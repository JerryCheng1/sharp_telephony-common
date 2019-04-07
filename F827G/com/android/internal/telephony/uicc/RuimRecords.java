package com.android.internal.telephony.uicc;

import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded;
import com.android.internal.util.BitwiseInputStream;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;

public final class RuimRecords extends IccRecords {
    private static final int CSIM_IMSI_MNC_LENGTH = 2;
    static final int EF_MODEL_FILE_SIZE = 126;
    private static final int EVENT_GET_ALL_SMS_DONE = 18;
    private static final int EVENT_GET_CDMA_SUBSCRIPTION_DONE = 10;
    private static final int EVENT_GET_DEVICE_IDENTITY_DONE = 4;
    private static final int EVENT_GET_ICCID_DONE = 5;
    private static final int EVENT_GET_RUIM_CST_DONE = 8;
    private static final int EVENT_GET_SMS_DONE = 22;
    private static final int EVENT_GET_SST_DONE = 17;
    private static final int EVENT_MARK_SMS_READ_DONE = 19;
    private static final int EVENT_SET_MODEL_DONE = 15;
    private static final int EVENT_SMS_ON_RUIM = 21;
    private static final int EVENT_UPDATE_DONE = 14;
    static final int LANGUAGE_INDICATOR_ENGLISH = 1;
    static final String LOG_TAG = "RuimRecords";
    static final int MANUFACTURER_NAME_SIZE = 32;
    static final int MODEL_INFORMATION_SIZE = 32;
    private static final int NUM_BYTES_RUIM_ID = 8;
    static final int SOFTWARE_VERSION_INFORMATION_SIZE = 60;
    boolean mCsimSpnDisplayCondition;
    private byte[] mEFli;
    private byte[] mEFpl;
    private String mHomeNetworkId;
    private String mHomeSystemId;
    private String mMdn;
    private String mMin;
    private String mMin2Min1;
    private String mMyMobileNumber;
    private String mNai;
    private boolean mOtaCommited;
    private String mPrlVersion;
    private boolean mRecordsRequired;

    private class EfCsimCdmaHomeLoaded implements IccRecordLoaded {
        private EfCsimCdmaHomeLoaded() {
        }

        public String getEfName() {
            return "EF_CSIM_CDMAHOME";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            ArrayList arrayList = (ArrayList) asyncResult.result;
            RuimRecords.this.log("CSIM_CDMAHOME data size=" + arrayList.size());
            if (!arrayList.isEmpty()) {
                StringBuilder stringBuilder = new StringBuilder();
                StringBuilder stringBuilder2 = new StringBuilder();
                Iterator it = arrayList.iterator();
                while (it.hasNext()) {
                    byte[] bArr = (byte[]) it.next();
                    if (bArr.length == 5) {
                        byte b = bArr[1];
                        byte b2 = bArr[0];
                        byte b3 = bArr[3];
                        byte b4 = bArr[2];
                        stringBuilder.append(((b & 255) << 8) | (b2 & 255)).append(',');
                        stringBuilder2.append((b4 & 255) | ((b3 & 255) << 8)).append(',');
                    }
                }
                stringBuilder.setLength(stringBuilder.length() - 1);
                stringBuilder2.setLength(stringBuilder2.length() - 1);
                RuimRecords.this.mHomeSystemId = stringBuilder.toString();
                RuimRecords.this.mHomeNetworkId = stringBuilder2.toString();
            }
        }
    }

    private class EfCsimEprlLoaded implements IccRecordLoaded {
        private EfCsimEprlLoaded() {
        }

        public String getEfName() {
            return "EF_CSIM_EPRL";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            RuimRecords.this.onGetCSimEprlDone(asyncResult);
        }
    }

    private class EfCsimImsimLoaded implements IccRecordLoaded {
        private EfCsimImsimLoaded() {
        }

        public String getEfName() {
            return "EF_CSIM_IMSIM";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            byte[] bArr = (byte[]) asyncResult.result;
            if (bArr == null || bArr.length < 10) {
                RuimRecords.this.log("Invalid IMSI from EF_CSIM_IMSIM " + IccUtils.bytesToHexString(bArr));
                RuimRecords.this.mImsi = null;
                RuimRecords.this.mMin = null;
                return;
            }
            RuimRecords.this.log("CSIM_IMSIM=" + IccUtils.bytesToHexString(bArr));
            if (((bArr[7] & 128) == 128 ? 1 : 0) != 0) {
                RuimRecords.this.mImsi = RuimRecords.this.decodeImsi(bArr);
                if (RuimRecords.this.mImsi != null) {
                    RuimRecords.this.mMin = RuimRecords.this.mImsi.substring(5, 15);
                }
                RuimRecords.this.log("IMSI: " + RuimRecords.this.mImsi.substring(0, 5) + "xxxxxxxxx");
            } else {
                RuimRecords.this.log("IMSI not provisioned in card");
            }
            String operatorNumeric = RuimRecords.this.getOperatorNumeric();
            if (operatorNumeric != null && operatorNumeric.length() <= 6) {
                MccTable.updateMccMncConfiguration(RuimRecords.this.mContext, operatorNumeric, false);
            }
            if (RuimRecords.this.isAppStateReady()) {
                RuimRecords.this.mImsiReadyRegistrants.notifyRegistrants();
            } else {
                RuimRecords.this.log("onRecordLoaded: AppState is not ready; not notifying the imsi readyregistrants");
            }
        }
    }

    private class EfCsimLiLoaded implements IccRecordLoaded {
        private EfCsimLiLoaded() {
        }

        public String getEfName() {
            return "EF_CSIM_LI";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            RuimRecords.this.mEFli = (byte[]) asyncResult.result;
            for (int i = 0; i < RuimRecords.this.mEFli.length; i += 2) {
                switch (RuimRecords.this.mEFli[i + 1]) {
                    case (byte) 1:
                        RuimRecords.this.mEFli[i] = (byte) 101;
                        RuimRecords.this.mEFli[i + 1] = (byte) 110;
                        break;
                    case (byte) 2:
                        RuimRecords.this.mEFli[i] = (byte) 102;
                        RuimRecords.this.mEFli[i + 1] = (byte) 114;
                        break;
                    case (byte) 3:
                        RuimRecords.this.mEFli[i] = (byte) 101;
                        RuimRecords.this.mEFli[i + 1] = (byte) 115;
                        break;
                    case (byte) 4:
                        RuimRecords.this.mEFli[i] = (byte) 106;
                        RuimRecords.this.mEFli[i + 1] = (byte) 97;
                        break;
                    case (byte) 5:
                        RuimRecords.this.mEFli[i] = (byte) 107;
                        RuimRecords.this.mEFli[i + 1] = (byte) 111;
                        break;
                    case (byte) 6:
                        RuimRecords.this.mEFli[i] = (byte) 122;
                        RuimRecords.this.mEFli[i + 1] = (byte) 104;
                        break;
                    case (byte) 7:
                        RuimRecords.this.mEFli[i] = (byte) 104;
                        RuimRecords.this.mEFli[i + 1] = (byte) 101;
                        break;
                    default:
                        RuimRecords.this.mEFli[i] = (byte) 32;
                        RuimRecords.this.mEFli[i + 1] = (byte) 32;
                        break;
                }
            }
            RuimRecords.this.log("EF_LI=" + IccUtils.bytesToHexString(RuimRecords.this.mEFli));
        }
    }

    private class EfCsimMdnLoaded implements IccRecordLoaded {
        private EfCsimMdnLoaded() {
        }

        public String getEfName() {
            return "EF_CSIM_MDN";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            byte[] bArr = (byte[]) asyncResult.result;
            RuimRecords.this.log("CSIM_MDN=" + IccUtils.bytesToHexString(bArr));
            RuimRecords.this.mMdn = IccUtils.cdmaBcdToString(bArr, 1, bArr[0] & 15);
            RuimRecords.this.log("CSIM MDN=" + RuimRecords.this.mMdn);
        }
    }

    private class EfCsimMipUppLoaded implements IccRecordLoaded {
        private EfCsimMipUppLoaded() {
        }

        /* Access modifiers changed, original: 0000 */
        public boolean checkLengthLegal(int i, int i2) {
            if (i >= i2) {
                return true;
            }
            Log.e(RuimRecords.LOG_TAG, "CSIM MIPUPP format error, length = " + i + "expected length at least =" + i2);
            return false;
        }

        public String getEfName() {
            return "EF_CSIM_MIPUPP";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            byte[] bArr = (byte[]) asyncResult.result;
            if (bArr.length < 1) {
                Log.e(RuimRecords.LOG_TAG, "MIPUPP read error");
                return;
            }
            BitwiseInputStream bitwiseInputStream = new BitwiseInputStream(bArr);
            try {
                int read = bitwiseInputStream.read(8) << 3;
                if (checkLengthLegal(read, 1)) {
                    read--;
                    if (bitwiseInputStream.read(1) == 1) {
                        if (checkLengthLegal(read, 11)) {
                            bitwiseInputStream.skip(11);
                            read -= 11;
                        } else {
                            return;
                        }
                    }
                    if (checkLengthLegal(read, 4)) {
                        int read2 = bitwiseInputStream.read(4);
                        read -= 4;
                        int i = 0;
                        while (i < read2 && checkLengthLegal(read, 4)) {
                            int read3 = bitwiseInputStream.read(4);
                            read -= 4;
                            if (checkLengthLegal(read, 8)) {
                                int read4 = bitwiseInputStream.read(8);
                                read -= 8;
                                if (read3 == 0) {
                                    if (checkLengthLegal(read, read4 << 3)) {
                                        char[] cArr = new char[read4];
                                        for (read = 0; read < read4; read++) {
                                            cArr[read] = (char) (bitwiseInputStream.read(8) & 255);
                                        }
                                        RuimRecords.this.mNai = new String(cArr);
                                        if (Log.isLoggable(RuimRecords.LOG_TAG, 2)) {
                                            Log.v(RuimRecords.LOG_TAG, "MIPUPP Nai = " + RuimRecords.this.mNai);
                                            return;
                                        }
                                        return;
                                    }
                                    return;
                                } else if (checkLengthLegal(read, (read4 << 3) + 102)) {
                                    bitwiseInputStream.skip((read4 << 3) + 101);
                                    read -= (read4 << 3) + 102;
                                    if (bitwiseInputStream.read(1) == 1) {
                                        if (checkLengthLegal(read, 32)) {
                                            bitwiseInputStream.skip(32);
                                            read -= 32;
                                        } else {
                                            return;
                                        }
                                    }
                                    if (checkLengthLegal(read, 5)) {
                                        bitwiseInputStream.skip(4);
                                        read = (read - 4) - 1;
                                        if (bitwiseInputStream.read(1) == 1) {
                                            if (checkLengthLegal(read, 32)) {
                                                bitwiseInputStream.skip(32);
                                                read -= 32;
                                            } else {
                                                return;
                                            }
                                        }
                                        i++;
                                    } else {
                                        return;
                                    }
                                } else {
                                    return;
                                }
                            }
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(RuimRecords.LOG_TAG, "MIPUPP read Exception error!");
            }
        }
    }

    private class EfCsimModelLoaded implements IccRecordLoaded {
        private EfCsimModelLoaded() {
        }

        public String getEfName() {
            return "EF_CSIM_MODEL";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            RuimRecords.this.log("EF_CSIM_MODEL=" + IccUtils.bytesToHexString((byte[]) asyncResult.result));
        }
    }

    private class EfCsimSpnLoaded implements IccRecordLoaded {
        private EfCsimSpnLoaded() {
        }

        public String getEfName() {
            return "EF_CSIM_SPN";
        }

        public void onRecordLoaded(android.os.AsyncResult r8) {
            /*
            r7 = this;
            r2 = 1;
            r4 = 32;
            r3 = 0;
            r0 = r8.result;
            r0 = (byte[]) r0;
            r0 = (byte[]) r0;
            r1 = com.android.internal.telephony.uicc.RuimRecords.this;
            r5 = new java.lang.StringBuilder;
            r5.<init>();
            r6 = "CSIM_SPN=";
            r5 = r5.append(r6);
            r6 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r0);
            r5 = r5.append(r6);
            r5 = r5.toString();
            r1.log(r5);
            r5 = com.android.internal.telephony.uicc.RuimRecords.this;
            r1 = r0[r3];
            r1 = r1 & 1;
            if (r1 == 0) goto L_0x0059;
        L_0x002e:
            r1 = r2;
        L_0x002f:
            r5.mCsimSpnDisplayCondition = r1;
            r2 = r0[r2];
            r1 = 2;
            r1 = r0[r1];
            r5 = new byte[r4];
            r1 = r0.length;
            r1 = r1 + -3;
            if (r1 >= r4) goto L_0x0138;
        L_0x003d:
            r1 = r0.length;
            r1 = r1 + -3;
        L_0x0040:
            r4 = 3;
            java.lang.System.arraycopy(r0, r4, r5, r3, r1);
        L_0x0044:
            r0 = r5.length;
            if (r3 >= r0) goto L_0x004f;
        L_0x0047:
            r0 = r5[r3];
            r0 = r0 & 255;
            r1 = 255; // 0xff float:3.57E-43 double:1.26E-321;
            if (r0 != r1) goto L_0x005b;
        L_0x004f:
            if (r3 != 0) goto L_0x005e;
        L_0x0051:
            r0 = com.android.internal.telephony.uicc.RuimRecords.this;
            r1 = "";
            r0.setServiceProviderName(r1);
        L_0x0058:
            return;
        L_0x0059:
            r1 = r3;
            goto L_0x002f;
        L_0x005b:
            r3 = r3 + 1;
            goto L_0x0044;
        L_0x005e:
            switch(r2) {
                case 0: goto L_0x00ae;
                case 1: goto L_0x0061;
                case 2: goto L_0x00e5;
                case 3: goto L_0x00d6;
                case 4: goto L_0x0129;
                case 5: goto L_0x0061;
                case 6: goto L_0x0061;
                case 7: goto L_0x0061;
                case 8: goto L_0x00ae;
                case 9: goto L_0x00d6;
                default: goto L_0x0061;
            };
        L_0x0061:
            r0 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00bc }
            r1 = "SPN encoding not supported";
            r0.log(r1);	 Catch:{ Exception -> 0x00bc }
        L_0x0068:
            r0 = com.android.internal.telephony.uicc.RuimRecords.this;
            r1 = new java.lang.StringBuilder;
            r1.<init>();
            r2 = "spn=";
            r1 = r1.append(r2);
            r2 = com.android.internal.telephony.uicc.RuimRecords.this;
            r2 = r2.getServiceProviderName();
            r1 = r1.append(r2);
            r1 = r1.toString();
            r0.log(r1);
            r0 = com.android.internal.telephony.uicc.RuimRecords.this;
            r1 = new java.lang.StringBuilder;
            r1.<init>();
            r2 = "spnCondition=";
            r1 = r1.append(r2);
            r2 = com.android.internal.telephony.uicc.RuimRecords.this;
            r2 = r2.mCsimSpnDisplayCondition;
            r1 = r1.append(r2);
            r1 = r1.toString();
            r0.log(r1);
            r0 = "gsm.sim.operator.alpha";
            r1 = com.android.internal.telephony.uicc.RuimRecords.this;
            r1 = r1.getServiceProviderName();
            android.os.SystemProperties.set(r0, r1);
            goto L_0x0058;
        L_0x00ae:
            r0 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00bc }
            r1 = new java.lang.String;	 Catch:{ Exception -> 0x00bc }
            r2 = 0;
            r4 = "ISO-8859-1";
            r1.<init>(r5, r2, r3, r4);	 Catch:{ Exception -> 0x00bc }
            r0.setServiceProviderName(r1);	 Catch:{ Exception -> 0x00bc }
            goto L_0x0068;
        L_0x00bc:
            r0 = move-exception;
            r1 = com.android.internal.telephony.uicc.RuimRecords.this;
            r2 = new java.lang.StringBuilder;
            r2.<init>();
            r3 = "spn decode error: ";
            r2 = r2.append(r3);
            r0 = r2.append(r0);
            r0 = r0.toString();
            r1.log(r0);
            goto L_0x0068;
        L_0x00d6:
            r0 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00bc }
            r1 = 0;
            r2 = r3 * 8;
            r2 = r2 / 7;
            r1 = com.android.internal.telephony.GsmAlphabet.gsm7BitPackedToString(r5, r1, r2);	 Catch:{ Exception -> 0x00bc }
            r0.setServiceProviderName(r1);	 Catch:{ Exception -> 0x00bc }
            goto L_0x0068;
        L_0x00e5:
            r0 = new java.lang.String;	 Catch:{ Exception -> 0x00bc }
            r1 = 0;
            r2 = "US-ASCII";
            r0.<init>(r5, r1, r3, r2);	 Catch:{ Exception -> 0x00bc }
            r1 = android.text.TextUtils.isPrintableAsciiOnly(r0);	 Catch:{ Exception -> 0x00bc }
            if (r1 == 0) goto L_0x00fa;
        L_0x00f3:
            r1 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00bc }
            r1.setServiceProviderName(r0);	 Catch:{ Exception -> 0x00bc }
            goto L_0x0068;
        L_0x00fa:
            r1 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00bc }
            r2 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x00bc }
            r2.<init>();	 Catch:{ Exception -> 0x00bc }
            r4 = "Some corruption in SPN decoding = ";
            r2 = r2.append(r4);	 Catch:{ Exception -> 0x00bc }
            r0 = r2.append(r0);	 Catch:{ Exception -> 0x00bc }
            r0 = r0.toString();	 Catch:{ Exception -> 0x00bc }
            r1.log(r0);	 Catch:{ Exception -> 0x00bc }
            r0 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00bc }
            r1 = "Using ENCODING_GSM_7BIT_ALPHABET scheme...";
            r0.log(r1);	 Catch:{ Exception -> 0x00bc }
            r0 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00bc }
            r1 = 0;
            r2 = r3 * 8;
            r2 = r2 / 7;
            r1 = com.android.internal.telephony.GsmAlphabet.gsm7BitPackedToString(r5, r1, r2);	 Catch:{ Exception -> 0x00bc }
            r0.setServiceProviderName(r1);	 Catch:{ Exception -> 0x00bc }
            goto L_0x0068;
        L_0x0129:
            r0 = com.android.internal.telephony.uicc.RuimRecords.this;	 Catch:{ Exception -> 0x00bc }
            r1 = new java.lang.String;	 Catch:{ Exception -> 0x00bc }
            r2 = 0;
            r4 = "utf-16";
            r1.<init>(r5, r2, r3, r4);	 Catch:{ Exception -> 0x00bc }
            r0.setServiceProviderName(r1);	 Catch:{ Exception -> 0x00bc }
            goto L_0x0068;
        L_0x0138:
            r1 = r4;
            goto L_0x0040;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.RuimRecords$EfCsimSpnLoaded.onRecordLoaded(android.os.AsyncResult):void");
        }
    }

    private class EfPlLoaded implements IccRecordLoaded {
        private EfPlLoaded() {
        }

        public String getEfName() {
            return "EF_PL";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            RuimRecords.this.mEFpl = (byte[]) asyncResult.result;
            RuimRecords.this.log("EF_PL=" + IccUtils.bytesToHexString(RuimRecords.this.mEFpl));
        }
    }

    private class EfRuimIdLoaded implements IccRecordLoaded {
        private EfRuimIdLoaded() {
        }

        public String getEfName() {
            return "EF_RUIM_ID";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            byte b = (byte) 0;
            byte[] bArr = (byte[]) asyncResult.result;
            RuimRecords.this.log("RuimId Data=" + IccUtils.bytesToHexString(bArr));
            if (bArr != null) {
                byte b2 = bArr[0];
                if (b2 < (byte) 8) {
                    byte[] bArr2 = new byte[b2];
                    while (b < b2) {
                        bArr2[b] = bArr[b2 - b];
                        b++;
                    }
                    RuimRecords.this.log("RUIM_ID=" + IccUtils.bytesToHexString(bArr2));
                }
            }
        }
    }

    private class EfRuimModelLoaded implements IccRecordLoaded {
        private EfRuimModelLoaded() {
        }

        public String getEfName() {
            return "EF_RUIM_MODEL";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            RuimRecords.this.log("EF_RUIM_MODEL=" + IccUtils.bytesToHexString((byte[]) asyncResult.result));
        }
    }

    public RuimRecords(UiccCardApplication uiccCardApplication, Context context, CommandsInterface commandsInterface) {
        super(uiccCardApplication, context, commandsInterface);
        this.mOtaCommited = false;
        this.mRecordsRequired = false;
        this.mEFpl = null;
        this.mEFli = null;
        this.mCsimSpnDisplayCondition = false;
        this.mAdnCache = new AdnRecordCache(this.mFh);
        this.mRecordsRequested = false;
        this.mRecordsToLoad = 0;
        resetRecords();
        this.mParentApp.registerForReady(this, 1, null);
        log("RuimRecords X ctor this=" + this);
    }

    private String decodeImsi(byte[] bArr) {
        int decodeImsiDigits = decodeImsiDigits(((bArr[9] & 3) << 8) | (bArr[8] & 255), 3);
        int decodeImsiDigits2 = decodeImsiDigits(bArr[6] & 127, 2);
        byte b = bArr[2];
        byte b2 = bArr[1];
        byte b3 = bArr[5];
        byte b4 = bArr[4];
        int i = (bArr[4] >> 2) & 15;
        if (i > 9) {
            i = 0;
        }
        byte b5 = bArr[4];
        byte b6 = bArr[3];
        int decodeImsiDigits3 = decodeImsiDigits(((b & 3) << 8) + (b2 & 255), 3);
        int decodeImsiDigits4 = decodeImsiDigits((((b3 & 255) << 8) | (b4 & 255)) >> 6, 3);
        int decodeImsiDigits5 = decodeImsiDigits(((b5 & 3) << 8) | (b6 & 255), 3);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format(Locale.US, "%03d", new Object[]{Integer.valueOf(decodeImsiDigits)}));
        stringBuilder.append(String.format(Locale.US, "%02d", new Object[]{Integer.valueOf(decodeImsiDigits2)}));
        stringBuilder.append(String.format(Locale.US, "%03d", new Object[]{Integer.valueOf(decodeImsiDigits3)}));
        stringBuilder.append(String.format(Locale.US, "%03d", new Object[]{Integer.valueOf(decodeImsiDigits4)}));
        stringBuilder.append(String.format(Locale.US, "%d", new Object[]{Integer.valueOf(i)}));
        stringBuilder.append(String.format(Locale.US, "%03d", new Object[]{Integer.valueOf(decodeImsiDigits5)}));
        return stringBuilder.toString();
    }

    private int decodeImsiDigits(int i, int i2) {
        int i3;
        int i4 = 0;
        int i5 = 0;
        for (i3 = 0; i3 < i2; i3++) {
            i5 = (i5 * 10) + 1;
        }
        i5 += i;
        i3 = 1;
        while (i4 < i2) {
            if ((i5 / i3) % 10 == 0) {
                i5 -= i3 * 10;
            }
            i3 *= 10;
            i4++;
        }
        return i5;
    }

    private void fetchOMHCardRecords(boolean z) {
        if (z) {
            setModel();
        }
    }

    private void fetchRuimRecords() {
        boolean z = this.mContext.getResources().getBoolean(17957026);
        if (this.mRecordsRequested || !((z || this.mRecordsRequired) && AppState.APPSTATE_READY == this.mParentApp.getState())) {
            log("fetchRuimRecords: Abort fetching records rRecordsRequested = " + this.mRecordsRequested + " state = " + this.mParentApp.getState() + " required = " + this.mRecordsRequired);
            return;
        }
        this.mRecordsRequested = true;
        log("fetchRuimRecords " + this.mRecordsToLoad);
        this.mFh.loadEFTransparent(IccConstants.EF_ICCID, obtainMessage(5));
        this.mRecordsToLoad++;
        if (Resources.getSystem().getBoolean(17957041)) {
            this.mFh.loadEFTransparent(IccConstants.EF_PL, obtainMessage(100, new EfPlLoaded()));
            this.mRecordsToLoad++;
            this.mFh.loadEFTransparent(28474, obtainMessage(100, new EfCsimLiLoaded()));
            this.mRecordsToLoad++;
        }
        this.mFh.loadEFTransparent(28481, obtainMessage(100, new EfCsimSpnLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixed(IccConstants.EF_CSIM_MDN, 1, obtainMessage(100, new EfCsimMdnLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_CSIM_IMSIM, obtainMessage(100, new EfCsimImsimLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixedAll(IccConstants.EF_CSIM_CDMAHOME, obtainMessage(100, new EfCsimCdmaHomeLoaded()));
        this.mRecordsToLoad++;
        if (z) {
            this.mFh.loadEFTransparent(IccConstants.EF_CSIM_MODEL, obtainMessage(100, new EfCsimModelLoaded()));
            this.mRecordsToLoad++;
            this.mFh.loadEFTransparent(IccConstants.EF_MODEL, obtainMessage(100, new EfRuimModelLoaded()));
            this.mRecordsToLoad++;
            this.mFh.loadEFTransparent(IccConstants.EF_CST, obtainMessage(8));
            this.mRecordsToLoad++;
            this.mFh.loadEFTransparent(IccConstants.EF_RUIM_ID, obtainMessage(100, new EfRuimIdLoaded()));
            this.mRecordsToLoad++;
        }
        this.mFh.loadEFTransparent(IccConstants.EF_CSIM_EPRL, 4, obtainMessage(100, new EfCsimEprlLoaded()));
        this.mRecordsToLoad++;
        this.mFh.getEFLinearRecordSize(IccConstants.EF_SMS, obtainMessage(28));
        this.mFh.loadEFTransparent(IccConstants.EF_CSIM_MIPUPP, obtainMessage(100, new EfCsimMipUppLoaded()));
        this.mRecordsToLoad++;
        log("fetchRuimRecords " + this.mRecordsToLoad + " requested: " + this.mRecordsRequested);
    }

    private String findBestLanguage(byte[] bArr) {
        String str;
        String[] assetLanguages = getAssetLanguages(this.mContext);
        if (bArr == null || assetLanguages == null) {
            str = null;
        } else {
            for (int i = 0; i + 1 < bArr.length; i += 2) {
                try {
                    String str2 = new String(bArr, i, 2, "ISO-8859-1");
                    int i2 = 0;
                    while (i2 < assetLanguages.length) {
                        if (assetLanguages[i2].equals(str2)) {
                            str = str2;
                        } else {
                            i2++;
                        }
                    }
                    continue;
                } catch (UnsupportedEncodingException e) {
                    log("Failed to parse SIM language records");
                }
            }
            return null;
        }
        return str;
    }

    private static String[] getAssetLanguages(Context context) {
        String[] locales = context.getAssets().getLocales();
        String[] strArr = new String[locales.length];
        for (int i = 0; i < locales.length; i++) {
            String str = locales[i];
            int indexOf = str.indexOf(45);
            if (indexOf < 0) {
                strArr[i] = str;
            } else {
                strArr[i] = str.substring(0, indexOf);
            }
        }
        return strArr;
    }

    private void onGetCSimEprlDone(AsyncResult asyncResult) {
        byte[] bArr = (byte[]) asyncResult.result;
        log("CSIM_EPRL=" + IccUtils.bytesToHexString(bArr));
        if (bArr.length > 3) {
            this.mPrlVersion = Integer.toString((bArr[3] & 255) | ((bArr[2] & 255) << 8));
        }
        log("CSIM PRL version=" + this.mPrlVersion);
    }

    private void setLocaleFromCsim() {
        String findBestLanguage = findBestLanguage(this.mEFli);
        String findBestLanguage2 = findBestLanguage == null ? findBestLanguage(this.mEFpl) : findBestLanguage;
        if (findBestLanguage2 != null) {
            String imsi = getIMSI();
            findBestLanguage = null;
            if (imsi != null) {
                findBestLanguage = MccTable.countryCodeForMcc(Integer.parseInt(imsi.substring(0, 3)));
            }
            log("Setting locale to " + findBestLanguage2 + "_" + findBestLanguage);
            MccTable.setSystemLocale(this.mContext, findBestLanguage2, findBestLanguage);
            return;
        }
        log("No suitable CSIM selected locale");
    }

    /* JADX WARNING: Removed duplicated region for block: B:26:0x00ee  */
    /* JADX WARNING: Removed duplicated region for block: B:12:0x003a  */
    /* JADX WARNING: Removed duplicated region for block: B:27:0x00f1  */
    /* JADX WARNING: Removed duplicated region for block: B:28:0x00f4  */
    /* JADX WARNING: Removed duplicated region for block: B:17:0x004c  */
    private void setModel() {
        /*
        r11 = this;
        r10 = 15;
        r9 = 1;
        r2 = 0;
        r5 = 32;
        r1 = 0;
        r0 = 126; // 0x7e float:1.77E-43 double:6.23E-322;
        r6 = new byte[r0];
        r0 = r1;
    L_0x000c:
        r3 = r6.length;
        if (r0 >= r3) goto L_0x0015;
    L_0x000f:
        r3 = -1;
        r6[r0] = r3;
        r0 = r0 + 1;
        goto L_0x000c;
    L_0x0015:
        r0 = android.os.Build.MODEL;	 Catch:{ UnsupportedEncodingException -> 0x00d2 }
        r3 = "UTF-8";
        r3 = r0.getBytes(r3);	 Catch:{ UnsupportedEncodingException -> 0x00d2 }
        r0 = android.os.Build.MANUFACTURER;	 Catch:{ UnsupportedEncodingException -> 0x0109 }
        r4 = "UTF-8";
        r4 = r0.getBytes(r4);	 Catch:{ UnsupportedEncodingException -> 0x0109 }
        r0 = "persist.product.sw.version";
        r7 = android.os.Build.DISPLAY;	 Catch:{ UnsupportedEncodingException -> 0x010c }
        r0 = android.os.SystemProperties.get(r0, r7);	 Catch:{ UnsupportedEncodingException -> 0x010c }
        r7 = "UTF-8";
        r0 = r0.getBytes(r7);	 Catch:{ UnsupportedEncodingException -> 0x010c }
    L_0x0033:
        r6[r1] = r1;
        r6[r9] = r9;
        r2 = r3.length;
        if (r2 <= r5) goto L_0x00ee;
    L_0x003a:
        r2 = r5;
    L_0x003b:
        r7 = 2;
        java.lang.System.arraycopy(r3, r1, r6, r7, r2);
        r2 = 34;
        r7 = r4.length;
        if (r7 <= r5) goto L_0x00f1;
    L_0x0044:
        java.lang.System.arraycopy(r4, r1, r6, r2, r5);
        r2 = r0.length;
        r5 = 60;
        if (r2 <= r5) goto L_0x00f4;
    L_0x004c:
        r2 = 60;
    L_0x004e:
        r5 = 66;
        java.lang.System.arraycopy(r0, r1, r6, r5, r2);
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r5 = "model: ";
        r1 = r1.append(r5);
        r3 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r3);
        r1 = r1.append(r3);
        r3 = "manufacturer: ";
        r1 = r1.append(r3);
        r3 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r4);
        r1 = r1.append(r3);
        r3 = "softwareVersion: ";
        r1 = r1.append(r3);
        r0 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r0);
        r0 = r1.append(r0);
        r0 = r0.toString();
        r11.log(r0);
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "EF model write data : ";
        r0 = r0.append(r1);
        r1 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r6);
        r0 = r0.append(r1);
        r1 = " version length=";
        r0 = r0.append(r1);
        r0 = r0.append(r2);
        r0 = r0.toString();
        r11.log(r0);
        r0 = r11.mParentApp;
        if (r0 == 0) goto L_0x00f7;
    L_0x00b1:
        r0 = r11.mParentApp;
        r0 = r0.getType();
        r1 = com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType.APPTYPE_CSIM;
        if (r0 != r1) goto L_0x00f7;
    L_0x00bb:
        r0 = "CSIM card type, set csim model";
        r11.log(r0);
        r0 = r11.mFh;
        r1 = 28545; // 0x6f81 float:4.0E-41 double:1.4103E-319;
        r2 = 28545; // 0x6f81 float:4.0E-41 double:1.4103E-319;
        r2 = java.lang.Integer.valueOf(r2);
        r2 = r11.obtainMessage(r10, r2);
        r0.updateEFTransparent(r1, r6, r2);
    L_0x00d1:
        return;
    L_0x00d2:
        r0 = move-exception;
        r3 = r2;
        r4 = r2;
    L_0x00d5:
        r7 = new java.lang.StringBuilder;
        r7.<init>();
        r8 = "BearerData encode failed: ";
        r7 = r7.append(r8);
        r0 = r7.append(r0);
        r0 = r0.toString();
        r11.loge(r0);
        r0 = r2;
        goto L_0x0033;
    L_0x00ee:
        r2 = r3.length;
        goto L_0x003b;
    L_0x00f1:
        r5 = r4.length;
        goto L_0x0044;
    L_0x00f4:
        r2 = r0.length;
        goto L_0x004e;
    L_0x00f7:
        r0 = r11.mFh;
        r1 = 28560; // 0x6f90 float:4.0021E-41 double:1.41105E-319;
        r2 = 28560; // 0x6f90 float:4.0021E-41 double:1.41105E-319;
        r2 = java.lang.Integer.valueOf(r2);
        r2 = r11.obtainMessage(r10, r2);
        r0.updateEFTransparent(r1, r6, r2);
        goto L_0x00d1;
    L_0x0109:
        r0 = move-exception;
        r4 = r2;
        goto L_0x00d5;
    L_0x010c:
        r0 = move-exception;
        goto L_0x00d5;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.RuimRecords.setModel():void");
    }

    public void dispose() {
        log("Disposing RuimRecords " + this);
        this.mParentApp.unregisterForReady(this);
        resetRecords();
        super.dispose();
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("RuimRecords: " + this);
        printWriter.println(" extends:");
        super.dump(fileDescriptor, printWriter, strArr);
        printWriter.println(" mOtaCommited=" + this.mOtaCommited);
        printWriter.println(" mMyMobileNumber=" + this.mMyMobileNumber);
        printWriter.println(" mMin2Min1=" + this.mMin2Min1);
        printWriter.println(" mPrlVersion=" + this.mPrlVersion);
        printWriter.println(" mEFpl[]=" + Arrays.toString(this.mEFpl));
        printWriter.println(" mEFli[]=" + Arrays.toString(this.mEFli));
        printWriter.println(" mCsimSpnDisplayCondition=" + this.mCsimSpnDisplayCondition);
        printWriter.println(" mMdn=" + this.mMdn);
        printWriter.println(" mMin=" + this.mMin);
        printWriter.println(" mHomeSystemId=" + this.mHomeSystemId);
        printWriter.println(" mHomeNetworkId=" + this.mHomeNetworkId);
        printWriter.flush();
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        log("RuimRecords finalized");
    }

    public String getCdmaMin() {
        return this.mMin2Min1;
    }

    public boolean getCsimSpnDisplayCondition() {
        return this.mCsimSpnDisplayCondition;
    }

    public int getDisplayRule(String str) {
        return 0;
    }

    public String getIMSI() {
        return this.mImsi;
    }

    public String getMdn() {
        return this.mMdn;
    }

    public String getMdnNumber() {
        return this.mMyMobileNumber;
    }

    public String getMin() {
        return this.mMin;
    }

    public String getNAI() {
        return this.mNai;
    }

    public String getNid() {
        return this.mHomeNetworkId;
    }

    public String getOperatorNumeric() {
        if (this.mImsi == null) {
            return null;
        }
        if (this.mMncLength != -1 && this.mMncLength != 0) {
            return this.mImsi.substring(0, this.mMncLength + 3);
        }
        Integer.parseInt(this.mImsi.substring(0, 3));
        return this.mImsi.substring(0, 5);
    }

    public String getPrlVersion() {
        return this.mPrlVersion;
    }

    public String getSid() {
        return this.mHomeSystemId;
    }

    public int getVoiceMessageCount() {
        log("RuimRecords:getVoiceMessageCount - NOP for CDMA");
        return 0;
    }

    /* Access modifiers changed, original: protected */
    public void handleFileUpdate(int i) {
        switch (i) {
            case 28474:
                log("SIM Refresh for EF_ADN");
                this.mAdnCache.reset();
                return;
            default:
                this.mAdnCache.reset();
                fetchRuimRecords();
                return;
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:22:0x0062  */
    /* JADX WARNING: Missing block: B:7:0x003f, code skipped:
            if (r2 == false) goto L_?;
     */
    /* JADX WARNING: Missing block: B:8:0x0041, code skipped:
            onRecordLoaded();
     */
    /* JADX WARNING: Missing block: B:95:0x0237, code skipped:
            r2 = true;
     */
    /* JADX WARNING: Missing block: B:97:?, code skipped:
            return;
     */
    /* JADX WARNING: Missing block: B:98:?, code skipped:
            return;
     */
    public void handleMessage(android.os.Message r9) {
        /*
        r8 = this;
        r7 = 10;
        r6 = 3;
        r5 = 4;
        r4 = 1;
        r2 = 0;
        r0 = r8.mDestroyed;
        r0 = r0.get();
        if (r0 == 0) goto L_0x0037;
    L_0x000e:
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "Received message ";
        r0 = r0.append(r1);
        r0 = r0.append(r9);
        r1 = "[";
        r0 = r0.append(r1);
        r1 = r9.what;
        r0 = r0.append(r1);
        r1 = "] while being destroyed. Ignoring.";
        r0 = r0.append(r1);
        r0 = r0.toString();
        r8.loge(r0);
    L_0x0036:
        return;
    L_0x0037:
        r0 = r9.what;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        switch(r0) {
            case 1: goto L_0x0045;
            case 2: goto L_0x003c;
            case 3: goto L_0x003c;
            case 4: goto L_0x0058;
            case 5: goto L_0x00a8;
            case 6: goto L_0x003c;
            case 7: goto L_0x003c;
            case 8: goto L_0x0136;
            case 9: goto L_0x003c;
            case 10: goto L_0x0066;
            case 11: goto L_0x003c;
            case 12: goto L_0x003c;
            case 13: goto L_0x003c;
            case 14: goto L_0x00d9;
            case 15: goto L_0x010f;
            case 16: goto L_0x003c;
            case 17: goto L_0x0108;
            case 18: goto L_0x00ec;
            case 19: goto L_0x00ec;
            case 20: goto L_0x003c;
            case 21: goto L_0x00ec;
            case 22: goto L_0x00ec;
            default: goto L_0x003c;
        };	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
    L_0x003c:
        super.handleMessage(r9);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
    L_0x003f:
        if (r2 == 0) goto L_0x0036;
    L_0x0041:
        r8.onRecordLoaded();
        goto L_0x0036;
    L_0x0045:
        r8.onReady();	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        goto L_0x003f;
    L_0x0049:
        r0 = move-exception;
        r4 = r2;
    L_0x004b:
        r1 = "RuimRecords";
        r2 = "Exception parsing RUIM record";
        android.telephony.Rlog.w(r1, r2, r0);	 Catch:{ all -> 0x022d }
        if (r4 == 0) goto L_0x0036;
    L_0x0054:
        r8.onRecordLoaded();
        goto L_0x0036;
    L_0x0058:
        r0 = "Event EVENT_GET_DEVICE_IDENTITY_DONE Received";
        r8.log(r0);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        goto L_0x003f;
    L_0x005e:
        r0 = move-exception;
        r4 = r2;
    L_0x0060:
        if (r4 == 0) goto L_0x0065;
    L_0x0062:
        r8.onRecordLoaded();
    L_0x0065:
        throw r0;
    L_0x0066:
        r0 = r9.obj;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r1 = (java.lang.String[]) r1;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r1 = (java.lang.String[]) r1;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        if (r0 != 0) goto L_0x003f;
    L_0x0074:
        r0 = 0;
        r0 = r1[r0];	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r8.mMyMobileNumber = r0;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r0 = 3;
        r0 = r1[r0];	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r8.mMin2Min1 = r0;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r0 = 4;
        r0 = r1[r0];	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r8.mPrlVersion = r0;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r0.<init>();	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r1 = "MDN: ";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r1 = r8.mMyMobileNumber;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r1 = " MIN: ";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r1 = r8.mMin2Min1;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r8.log(r0);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        goto L_0x003f;
    L_0x00a8:
        r0 = r9.obj;	 Catch:{ RuntimeException -> 0x0230 }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x0230 }
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x0230 }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x0230 }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x0230 }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x0230 }
        if (r0 != 0) goto L_0x0237;
    L_0x00b6:
        r0 = 0;
        r2 = r1.length;	 Catch:{ RuntimeException -> 0x0230 }
        r0 = com.android.internal.telephony.uicc.IccUtils.bcdToString(r1, r0, r2);	 Catch:{ RuntimeException -> 0x0230 }
        r8.mIccId = r0;	 Catch:{ RuntimeException -> 0x0230 }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0230 }
        r0.<init>();	 Catch:{ RuntimeException -> 0x0230 }
        r1 = "iccid: ";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0230 }
        r1 = r8.mIccId;	 Catch:{ RuntimeException -> 0x0230 }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0230 }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x0230 }
        r8.log(r0);	 Catch:{ RuntimeException -> 0x0230 }
        r2 = r4;
        goto L_0x003f;
    L_0x00d9:
        r0 = r9.obj;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r1 = r0.exception;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        if (r1 == 0) goto L_0x003f;
    L_0x00e1:
        r1 = "RuimRecords";
        r3 = "RuimRecords update failed";
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        android.telephony.Rlog.i(r1, r3, r0);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        goto L_0x003f;
    L_0x00ec:
        r0 = "RuimRecords";
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r1.<init>();	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r3 = "Event not supported: ";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r3 = r9.what;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        android.telephony.Rlog.w(r0, r1);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        goto L_0x003f;
    L_0x0108:
        r0 = "Event EVENT_GET_SST_DONE Received";
        r8.log(r0);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        goto L_0x003f;
    L_0x010f:
        r0 = r9.obj;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r1 = r0.exception;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        if (r1 == 0) goto L_0x012f;
    L_0x0117:
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r1.<init>();	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r3 = "Set EF Model failed";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r0 = r1.append(r0);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        r8.loge(r0);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
    L_0x012f:
        r0 = "EVENT_SET_MODEL_DONE";
        r8.log(r0);	 Catch:{ RuntimeException -> 0x0049, all -> 0x005e }
        goto L_0x003f;
    L_0x0136:
        r0 = r9.obj;	 Catch:{ RuntimeException -> 0x0230 }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x0230 }
        if (r0 == 0) goto L_0x01af;
    L_0x013c:
        r1 = r0.exception;	 Catch:{ RuntimeException -> 0x0230 }
        if (r1 != 0) goto L_0x01af;
    L_0x0140:
        r0 = r0.result;	 Catch:{ RuntimeException -> 0x0230 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0230 }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0230 }
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0230 }
        r1.<init>();	 Catch:{ RuntimeException -> 0x0230 }
        r3 = "EF CST data: ";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x0230 }
        r3 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r0);	 Catch:{ RuntimeException -> 0x0230 }
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x0230 }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x0230 }
        r8.log(r1);	 Catch:{ RuntimeException -> 0x0230 }
        if (r0 == 0) goto L_0x0237;
    L_0x0162:
        r1 = r8.mParentApp;	 Catch:{ RuntimeException -> 0x0230 }
        if (r1 == 0) goto L_0x01cf;
    L_0x0166:
        r1 = r8.mParentApp;	 Catch:{ RuntimeException -> 0x0230 }
        r1 = r1.getType();	 Catch:{ RuntimeException -> 0x0230 }
        r3 = com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType.APPTYPE_CSIM;	 Catch:{ RuntimeException -> 0x0230 }
        if (r1 != r3) goto L_0x01cf;
    L_0x0170:
        r1 = r0.length;	 Catch:{ RuntimeException -> 0x0230 }
        if (r1 <= r5) goto L_0x01b5;
    L_0x0173:
        r1 = r0[r5];
        r1 = r1 & 4;
        if (r5 != r1) goto L_0x0224;
    L_0x0179:
        r1 = r4;
    L_0x017a:
        if (r1 == 0) goto L_0x0233;
    L_0x017c:
        r3 = 2;
        r0 = r0[r3];
        r0 = r0 & 4;
        if (r5 != r0) goto L_0x0227;
    L_0x0183:
        r0 = r1;
        r3 = r4;
    L_0x0185:
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0230 }
        r1.<init>();	 Catch:{ RuntimeException -> 0x0230 }
        r2 = "mms icp enabled =";
        r1 = r1.append(r2);	 Catch:{ RuntimeException -> 0x0230 }
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x0230 }
        r2 = " omhEnabled ";
        r1 = r1.append(r2);	 Catch:{ RuntimeException -> 0x0230 }
        r1 = r1.append(r0);	 Catch:{ RuntimeException -> 0x0230 }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x0230 }
        r8.log(r1);	 Catch:{ RuntimeException -> 0x0230 }
        if (r0 == 0) goto L_0x0221;
    L_0x01a7:
        r1 = "true";
    L_0x01a9:
        r2 = "ril.cdma.omhcard";
        android.os.SystemProperties.set(r2, r1);	 Catch:{ RuntimeException -> 0x0230 }
        r2 = r0;
    L_0x01af:
        r8.fetchOMHCardRecords(r2);	 Catch:{ RuntimeException -> 0x0230 }
        r2 = r4;
        goto L_0x003f;
    L_0x01b5:
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0230 }
        r1.<init>();	 Catch:{ RuntimeException -> 0x0230 }
        r3 = "CSIM EF CST data length = ";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x0230 }
        r0 = r0.length;	 Catch:{ RuntimeException -> 0x0230 }
        r0 = r1.append(r0);	 Catch:{ RuntimeException -> 0x0230 }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x0230 }
        r8.loge(r0);	 Catch:{ RuntimeException -> 0x0230 }
        r0 = r2;
        r3 = r2;
        goto L_0x0185;
    L_0x01cf:
        r1 = r0.length;	 Catch:{ RuntimeException -> 0x0230 }
        if (r1 <= r6) goto L_0x0206;
    L_0x01d2:
        r1 = 48;
        r3 = r0[r6];
        r3 = r3 & 48;
        if (r1 != r3) goto L_0x022b;
    L_0x01da:
        r1 = r4;
    L_0x01db:
        if (r1 == 0) goto L_0x01ec;
    L_0x01dd:
        r3 = r0.length;	 Catch:{ RuntimeException -> 0x0230 }
        if (r3 <= r7) goto L_0x01ec;
    L_0x01e0:
        r3 = 12;
        r0 = r0[r7];
        r0 = r0 & 12;
        if (r3 != r0) goto L_0x01e9;
    L_0x01e8:
        r2 = r4;
    L_0x01e9:
        r0 = r1;
        r3 = r2;
        goto L_0x0185;
    L_0x01ec:
        r3 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0230 }
        r3.<init>();	 Catch:{ RuntimeException -> 0x0230 }
        r5 = "OMH EF CST data length = ";
        r3 = r3.append(r5);	 Catch:{ RuntimeException -> 0x0230 }
        r0 = r0.length;	 Catch:{ RuntimeException -> 0x0230 }
        r0 = r3.append(r0);	 Catch:{ RuntimeException -> 0x0230 }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x0230 }
        r8.loge(r0);	 Catch:{ RuntimeException -> 0x0230 }
        r0 = r1;
        r3 = r2;
        goto L_0x0185;
    L_0x0206:
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0230 }
        r1.<init>();	 Catch:{ RuntimeException -> 0x0230 }
        r3 = "OMH EF CST data length = ";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x0230 }
        r0 = r0.length;	 Catch:{ RuntimeException -> 0x0230 }
        r0 = r1.append(r0);	 Catch:{ RuntimeException -> 0x0230 }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x0230 }
        r8.loge(r0);	 Catch:{ RuntimeException -> 0x0230 }
        r0 = r2;
        r3 = r2;
        goto L_0x0185;
    L_0x0221:
        r1 = "false";
        goto L_0x01a9;
    L_0x0224:
        r1 = r2;
        goto L_0x017a;
    L_0x0227:
        r0 = r1;
        r3 = r2;
        goto L_0x0185;
    L_0x022b:
        r1 = r2;
        goto L_0x01db;
    L_0x022d:
        r0 = move-exception;
        goto L_0x0060;
    L_0x0230:
        r0 = move-exception;
        goto L_0x004b;
    L_0x0233:
        r0 = r1;
        r3 = r2;
        goto L_0x0185;
    L_0x0237:
        r2 = r4;
        goto L_0x003f;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.RuimRecords.handleMessage(android.os.Message):void");
    }

    public boolean isProvisioned() {
        if (!SystemProperties.getBoolean("persist.radio.test-csim", false)) {
            if (this.mParentApp == null) {
                return false;
            }
            if (this.mParentApp.getType() == AppType.APPTYPE_CSIM && (this.mMdn == null || this.mMin == null)) {
                return false;
            }
        }
        return true;
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(LOG_TAG, "[RuimRecords] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str) {
        Rlog.e(LOG_TAG, "[RuimRecords] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void onAllRecordsLoaded() {
        log("record load complete");
        String operatorNumeric = getOperatorNumeric();
        if (TextUtils.isEmpty(operatorNumeric)) {
            log("onAllRecordsLoaded empty 'gsm.sim.operator.numeric' skipping");
        } else {
            log("onAllRecordsLoaded set 'gsm.sim.operator.numeric' to operator='" + operatorNumeric + "'");
            setSystemProperty("gsm.sim.operator.numeric", operatorNumeric);
            setSystemProperty("net.cdma.ruim.operator.numeric", operatorNumeric);
        }
        if (TextUtils.isEmpty(this.mImsi)) {
            log("onAllRecordsLoaded empty imsi skipping setting mcc");
        } else {
            log("onAllRecordsLoaded set mcc imsi=" + this.mImsi);
            setSystemProperty("gsm.sim.operator.iso-country", MccTable.countryCodeForMcc(Integer.parseInt(this.mImsi.substring(0, 3))));
        }
        setLocaleFromCsim();
        if (isAppStateReady()) {
            this.mRecordsLoadedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
        } else {
            log("onAllRecordsLoaded: AppState is not ready; not notifying the registrants");
        }
        if (!TextUtils.isEmpty(this.mMdn)) {
            int[] subId = SubscriptionController.getInstance().getSubId(this.mParentApp.getUiccCard().getPhoneId());
            if (subId != null) {
                log("Calling setDisplayNumber for subId and number " + subId[0] + " and " + this.mMdn);
                SubscriptionManager.from(this.mContext).setDisplayNumber(this.mMdn, subId[0]);
                return;
            }
            log("Cannot call setDisplayNumber: invalid subId");
        }
    }

    public void onReady() {
        fetchRuimRecords();
        this.mCi.getCDMASubscription(obtainMessage(10));
    }

    /* Access modifiers changed, original: protected */
    public void onRecordLoaded() {
        this.mRecordsToLoad--;
        log("onRecordLoaded " + this.mRecordsToLoad + " requested: " + this.mRecordsRequested);
        if (this.mRecordsToLoad == 0 && this.mRecordsRequested) {
            onAllRecordsLoaded();
        } else if (this.mRecordsToLoad < 0) {
            loge("recordsToLoad <0, programmer error suspected");
            this.mRecordsToLoad = 0;
        }
    }

    public void onRefresh(boolean z, int[] iArr) {
        if (z) {
            fetchRuimRecords();
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void recordsRequired() {
        log("recordsRequired");
        this.mRecordsRequired = true;
        fetchRuimRecords();
    }

    /* Access modifiers changed, original: protected */
    public void resetRecords() {
        this.mMncLength = -1;
        log("setting0 mMncLength" + this.mMncLength);
        this.mIccId = null;
        this.mAdnCache.reset();
        setSystemProperty("net.cdma.ruim.operator.numeric", "");
        this.mRecordsRequested = false;
    }

    public void setVoiceMailNumber(String str, String str2, Message message) {
        AsyncResult.forMessage(message).exception = new IccException("setVoiceMailNumber not implemented");
        message.sendToTarget();
        loge("method setVoiceMailNumber is not implemented");
    }

    public void setVoiceMessageWaiting(int i, int i2) {
        log("RuimRecords:setVoiceMessageWaiting - NOP for CDMA");
    }

    public String toString() {
        return "RuimRecords: " + super.toString() + " m_ota_commited" + this.mOtaCommited + " mMyMobileNumber=" + "xxxx" + " mMin2Min1=" + this.mMin2Min1 + " mPrlVersion=" + this.mPrlVersion + " mEFpl=" + this.mEFpl + " mEFli=" + this.mEFli + " mCsimSpnDisplayCondition=" + this.mCsimSpnDisplayCondition + " mMdn=" + this.mMdn + " mMin=" + this.mMin + " mHomeSystemId=" + this.mHomeSystemId + " mHomeNetworkId=" + this.mHomeNetworkId;
    }
}
