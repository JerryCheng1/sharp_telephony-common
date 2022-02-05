package com.android.internal.telephony.uicc;

import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.util.BitwiseInputStream;
import com.google.android.mms.pdu.CharacterSets;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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
    private String mHomeNetworkId;
    private String mHomeSystemId;
    private String mMdn;
    private String mMin;
    private String mMin2Min1;
    private String mMyMobileNumber;
    private String mNai;
    private String mPrlVersion;
    private boolean mOtaCommited = false;
    private boolean mRecordsRequired = false;
    private byte[] mEFpl = null;
    private byte[] mEFli = null;
    boolean mCsimSpnDisplayCondition = false;

    @Override // com.android.internal.telephony.uicc.IccRecords, android.os.Handler
    public String toString() {
        return "RuimRecords: " + super.toString() + " m_ota_commited" + this.mOtaCommited + " mMyMobileNumber=xxxx mMin2Min1=" + this.mMin2Min1 + " mPrlVersion=" + this.mPrlVersion + " mEFpl=" + this.mEFpl + " mEFli=" + this.mEFli + " mCsimSpnDisplayCondition=" + this.mCsimSpnDisplayCondition + " mMdn=" + this.mMdn + " mMin=" + this.mMin + " mHomeSystemId=" + this.mHomeSystemId + " mHomeNetworkId=" + this.mHomeNetworkId;
    }

    public RuimRecords(UiccCardApplication app, Context c, CommandsInterface ci) {
        super(app, c, ci);
        this.mAdnCache = new AdnRecordCache(this.mFh);
        this.mRecordsRequested = false;
        this.mRecordsToLoad = 0;
        resetRecords();
        this.mParentApp.registerForReady(this, 1, null);
        log("RuimRecords X ctor this=" + this);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void dispose() {
        log("Disposing RuimRecords " + this);
        this.mParentApp.unregisterForReady(this);
        resetRecords();
        super.dispose();
    }

    protected void finalize() {
        log("RuimRecords finalized");
    }

    protected void resetRecords() {
        this.mMncLength = -1;
        log("setting0 mMncLength" + this.mMncLength);
        this.mIccId = null;
        this.mAdnCache.reset();
        setSystemProperty("net.cdma.ruim.operator.numeric", "");
        this.mRecordsRequested = false;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getIMSI() {
        return this.mImsi;
    }

    public String getMdnNumber() {
        return this.mMyMobileNumber;
    }

    public String getCdmaMin() {
        return this.mMin2Min1;
    }

    public String getPrlVersion() {
        return this.mPrlVersion;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getNAI() {
        return this.mNai;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void setVoiceMailNumber(String alphaTag, String voiceNumber, Message onComplete) {
        AsyncResult.forMessage(onComplete).exception = new IccException("setVoiceMailNumber not implemented");
        onComplete.sendToTarget();
        loge("method setVoiceMailNumber is not implemented");
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void onRefresh(boolean fileChanged, int[] fileList) {
        if (fileChanged) {
            fetchRuimRecords();
        }
    }

    private int decodeImsiDigits(int digits, int length) {
        int constant = 0;
        for (int i = 0; i < length; i++) {
            constant = (constant * 10) + 1;
        }
        int digits2 = digits + constant;
        int denominator = 1;
        for (int i2 = 0; i2 < length; i2++) {
            if ((digits2 / denominator) % 10 == 0) {
                digits2 -= denominator * 10;
            }
            denominator *= 10;
        }
        return digits2;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String decodeImsi(byte[] data) {
        int mcc = decodeImsiDigits(((data[9] & 3) << 8) | (data[8] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT), 3);
        int digits_11_12 = decodeImsiDigits(data[6] & Byte.MAX_VALUE, 2);
        int first3digits = ((data[2] & 3) << 8) + (data[1] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT);
        int second3digits = (((data[5] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) << 8) | (data[4] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT)) >> 6;
        int digit7 = (data[4] >> 2) & 15;
        if (digit7 > 9) {
            digit7 = 0;
        }
        int first3digits2 = decodeImsiDigits(first3digits, 3);
        int second3digits2 = decodeImsiDigits(second3digits, 3);
        int last3digits = decodeImsiDigits(((data[4] & 3) << 8) | (data[3] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT), 3);
        return String.format(Locale.US, "%03d", Integer.valueOf(mcc)) + String.format(Locale.US, "%02d", Integer.valueOf(digits_11_12)) + String.format(Locale.US, "%03d", Integer.valueOf(first3digits2)) + String.format(Locale.US, "%03d", Integer.valueOf(second3digits2)) + String.format(Locale.US, "%d", Integer.valueOf(digit7)) + String.format(Locale.US, "%03d", Integer.valueOf(last3digits));
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
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

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class EfPlLoaded implements IccRecords.IccRecordLoaded {
        private EfPlLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_PL";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            RuimRecords.this.mEFpl = (byte[]) ar.result;
            RuimRecords.this.log("EF_PL=" + IccUtils.bytesToHexString(RuimRecords.this.mEFpl));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class EfCsimLiLoaded implements IccRecords.IccRecordLoaded {
        private EfCsimLiLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_CSIM_LI";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            RuimRecords.this.mEFli = (byte[]) ar.result;
            for (int i = 0; i < RuimRecords.this.mEFli.length; i += 2) {
                switch (RuimRecords.this.mEFli[i + 1]) {
                    case 1:
                        RuimRecords.this.mEFli[i] = 101;
                        RuimRecords.this.mEFli[i + 1] = 110;
                        break;
                    case 2:
                        RuimRecords.this.mEFli[i] = 102;
                        RuimRecords.this.mEFli[i + 1] = 114;
                        break;
                    case 3:
                        RuimRecords.this.mEFli[i] = 101;
                        RuimRecords.this.mEFli[i + 1] = 115;
                        break;
                    case 4:
                        RuimRecords.this.mEFli[i] = 106;
                        RuimRecords.this.mEFli[i + 1] = 97;
                        break;
                    case 5:
                        RuimRecords.this.mEFli[i] = 107;
                        RuimRecords.this.mEFli[i + 1] = 111;
                        break;
                    case 6:
                        RuimRecords.this.mEFli[i] = 122;
                        RuimRecords.this.mEFli[i + 1] = 104;
                        break;
                    case 7:
                        RuimRecords.this.mEFli[i] = 104;
                        RuimRecords.this.mEFli[i + 1] = 101;
                        break;
                    default:
                        RuimRecords.this.mEFli[i] = 32;
                        RuimRecords.this.mEFli[i + 1] = 32;
                        break;
                }
            }
            RuimRecords.this.log("EF_LI=" + IccUtils.bytesToHexString(RuimRecords.this.mEFli));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class EfCsimSpnLoaded implements IccRecords.IccRecordLoaded {
        private EfCsimSpnLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_CSIM_SPN";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            boolean z;
            int len = 32;
            byte[] data = (byte[]) ar.result;
            RuimRecords.this.log("CSIM_SPN=" + IccUtils.bytesToHexString(data));
            RuimRecords ruimRecords = RuimRecords.this;
            if ((data[0] & 1) != 0) {
                z = true;
            } else {
                z = false;
            }
            ruimRecords.mCsimSpnDisplayCondition = z;
            byte b = data[1];
            byte b2 = data[2];
            byte[] spnData = new byte[32];
            if (data.length - 3 < 32) {
                len = data.length - 3;
            }
            System.arraycopy(data, 3, spnData, 0, len);
            int numBytes = 0;
            while (numBytes < spnData.length && (spnData[numBytes] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) != 255) {
                numBytes++;
            }
            if (numBytes == 0) {
                RuimRecords.this.setServiceProviderName("");
                return;
            }
            try {
                switch (b) {
                    case 0:
                    case 8:
                        RuimRecords.this.setServiceProviderName(new String(spnData, 0, numBytes, "ISO-8859-1"));
                        break;
                    case 1:
                    case 5:
                    case 6:
                    case 7:
                    default:
                        RuimRecords.this.log("SPN encoding not supported");
                        break;
                    case 2:
                        String spn = new String(spnData, 0, numBytes, "US-ASCII");
                        if (!TextUtils.isPrintableAsciiOnly(spn)) {
                            RuimRecords.this.log("Some corruption in SPN decoding = " + spn);
                            RuimRecords.this.log("Using ENCODING_GSM_7BIT_ALPHABET scheme...");
                            RuimRecords.this.setServiceProviderName(GsmAlphabet.gsm7BitPackedToString(spnData, 0, (numBytes * 8) / 7));
                            break;
                        } else {
                            RuimRecords.this.setServiceProviderName(spn);
                            break;
                        }
                    case 3:
                    case 9:
                        RuimRecords.this.setServiceProviderName(GsmAlphabet.gsm7BitPackedToString(spnData, 0, (numBytes * 8) / 7));
                        break;
                    case 4:
                        RuimRecords.this.setServiceProviderName(new String(spnData, 0, numBytes, CharacterSets.MIMENAME_UTF_16));
                        break;
                }
            } catch (Exception e) {
                RuimRecords.this.log("spn decode error: " + e);
            }
            RuimRecords.this.log("spn=" + RuimRecords.this.getServiceProviderName());
            RuimRecords.this.log("spnCondition=" + RuimRecords.this.mCsimSpnDisplayCondition);
            SystemProperties.set("gsm.sim.operator.alpha", RuimRecords.this.getServiceProviderName());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class EfCsimMdnLoaded implements IccRecords.IccRecordLoaded {
        private EfCsimMdnLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_CSIM_MDN";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            byte[] data = (byte[]) ar.result;
            RuimRecords.this.log("CSIM_MDN=" + IccUtils.bytesToHexString(data));
            int mdnDigitsNum = data[0] & 15;
            RuimRecords.this.mMdn = IccUtils.cdmaBcdToString(data, 1, mdnDigitsNum);
            RuimRecords.this.log("CSIM MDN=" + RuimRecords.this.mMdn);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class EfCsimImsimLoaded implements IccRecords.IccRecordLoaded {
        private EfCsimImsimLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_CSIM_IMSIM";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            byte[] data = (byte[]) ar.result;
            if (data == null || data.length < 10) {
                RuimRecords.this.log("Invalid IMSI from EF_CSIM_IMSIM " + IccUtils.bytesToHexString(data));
                RuimRecords.this.mImsi = null;
                RuimRecords.this.mMin = null;
                return;
            }
            RuimRecords.this.log("CSIM_IMSIM=" + IccUtils.bytesToHexString(data));
            if ((data[7] & 128) == 128) {
                RuimRecords.this.mImsi = RuimRecords.this.decodeImsi(data);
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

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class EfCsimCdmaHomeLoaded implements IccRecords.IccRecordLoaded {
        private EfCsimCdmaHomeLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_CSIM_CDMAHOME";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            ArrayList<byte[]> dataList = (ArrayList) ar.result;
            RuimRecords.this.log("CSIM_CDMAHOME data size=" + dataList.size());
            if (!dataList.isEmpty()) {
                StringBuilder sidBuf = new StringBuilder();
                StringBuilder nidBuf = new StringBuilder();
                Iterator i$ = dataList.iterator();
                while (i$.hasNext()) {
                    byte[] data = i$.next();
                    if (data.length == 5) {
                        int sid = ((data[1] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) << 8) | (data[0] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT);
                        int nid = ((data[3] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) << 8) | (data[2] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT);
                        sidBuf.append(sid).append(',');
                        nidBuf.append(nid).append(',');
                    }
                }
                sidBuf.setLength(sidBuf.length() - 1);
                nidBuf.setLength(nidBuf.length() - 1);
                RuimRecords.this.mHomeSystemId = sidBuf.toString();
                RuimRecords.this.mHomeNetworkId = nidBuf.toString();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class EfCsimEprlLoaded implements IccRecords.IccRecordLoaded {
        private EfCsimEprlLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_CSIM_EPRL";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            RuimRecords.this.onGetCSimEprlDone(ar);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onGetCSimEprlDone(AsyncResult ar) {
        byte[] data = (byte[]) ar.result;
        log("CSIM_EPRL=" + IccUtils.bytesToHexString(data));
        if (data.length > 3) {
            this.mPrlVersion = Integer.toString(((data[2] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) << 8) | (data[3] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT));
        }
        log("CSIM PRL version=" + this.mPrlVersion);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class EfCsimMipUppLoaded implements IccRecords.IccRecordLoaded {
        private EfCsimMipUppLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_CSIM_MIPUPP";
        }

        boolean checkLengthLegal(int length, int expectLength) {
            if (length >= expectLength) {
                return true;
            }
            Log.e(RuimRecords.LOG_TAG, "CSIM MIPUPP format error, length = " + length + "expected length at least =" + expectLength);
            return false;
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            byte[] data = (byte[]) ar.result;
            if (data.length < 1) {
                Log.e(RuimRecords.LOG_TAG, "MIPUPP read error");
                return;
            }
            BitwiseInputStream bitStream = new BitwiseInputStream(data);
            try {
                int mipUppLength = bitStream.read(8) << 3;
                if (checkLengthLegal(mipUppLength, 1)) {
                    int mipUppLength2 = mipUppLength - 1;
                    if (bitStream.read(1) == 1) {
                        if (checkLengthLegal(mipUppLength2, 11)) {
                            bitStream.skip(11);
                            mipUppLength2 -= 11;
                        } else {
                            return;
                        }
                    }
                    if (checkLengthLegal(mipUppLength2, 4)) {
                        int numNai = bitStream.read(4);
                        int mipUppLength3 = mipUppLength2 - 4;
                        for (int index = 0; index < numNai && checkLengthLegal(mipUppLength3, 4); index++) {
                            int naiEntryIndex = bitStream.read(4);
                            int mipUppLength4 = mipUppLength3 - 4;
                            if (checkLengthLegal(mipUppLength4, 8)) {
                                int naiLength = bitStream.read(8);
                                int mipUppLength5 = mipUppLength4 - 8;
                                if (naiEntryIndex == 0) {
                                    if (checkLengthLegal(mipUppLength5, naiLength << 3)) {
                                        char[] naiCharArray = new char[naiLength];
                                        for (int index1 = 0; index1 < naiLength; index1++) {
                                            naiCharArray[index1] = (char) (bitStream.read(8) & 255);
                                        }
                                        RuimRecords.this.mNai = new String(naiCharArray);
                                        if (Log.isLoggable(RuimRecords.LOG_TAG, 2)) {
                                            Log.v(RuimRecords.LOG_TAG, "MIPUPP Nai = " + RuimRecords.this.mNai);
                                            return;
                                        }
                                        return;
                                    }
                                    return;
                                } else if (checkLengthLegal(mipUppLength5, (naiLength << 3) + 102)) {
                                    bitStream.skip((naiLength << 3) + 101);
                                    int mipUppLength6 = mipUppLength5 - ((naiLength << 3) + 102);
                                    if (bitStream.read(1) == 1) {
                                        if (checkLengthLegal(mipUppLength6, 32)) {
                                            bitStream.skip(32);
                                            mipUppLength6 -= 32;
                                        } else {
                                            return;
                                        }
                                    }
                                    if (checkLengthLegal(mipUppLength6, 5)) {
                                        bitStream.skip(4);
                                        mipUppLength3 = (mipUppLength6 - 4) - 1;
                                        if (bitStream.read(1) == 1) {
                                            if (checkLengthLegal(mipUppLength3, 32)) {
                                                bitStream.skip(32);
                                                mipUppLength3 -= 32;
                                            } else {
                                                return;
                                            }
                                        }
                                    } else {
                                        return;
                                    }
                                } else {
                                    return;
                                }
                            } else {
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(RuimRecords.LOG_TAG, "MIPUPP read Exception error!");
            }
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords, android.os.Handler
    public void handleMessage(Message msg) {
        boolean isRecordLoadResponse = false;
        try {
            if (this.mDestroyed.get()) {
                loge("Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
                return;
            }
            try {
                switch (msg.what) {
                    case 1:
                        onReady();
                        break;
                    case 2:
                    case 3:
                    case 6:
                    case 7:
                    case 9:
                    case 11:
                    case 12:
                    case 13:
                    case 16:
                    case 20:
                    default:
                        super.handleMessage(msg);
                        break;
                    case 4:
                        log("Event EVENT_GET_DEVICE_IDENTITY_DONE Received");
                        break;
                    case 5:
                        isRecordLoadResponse = true;
                        AsyncResult ar = (AsyncResult) msg.obj;
                        byte[] data = (byte[]) ar.result;
                        if (ar.exception == null) {
                            this.mIccId = IccUtils.bcdToString(data, 0, data.length);
                            log("iccid: " + this.mIccId);
                            break;
                        }
                        break;
                    case 8:
                        boolean omhEnabled = false;
                        boolean mmsicpEnabled = false;
                        isRecordLoadResponse = true;
                        AsyncResult ar2 = (AsyncResult) msg.obj;
                        if (ar2 != null && ar2.exception == null) {
                            byte[] data2 = (byte[]) ar2.result;
                            log("EF CST data: " + IccUtils.bytesToHexString(data2));
                            if (data2 != null) {
                                if (this.mParentApp == null || this.mParentApp.getType() != IccCardApplicationStatus.AppType.APPTYPE_CSIM) {
                                    if (data2.length > 3) {
                                        omhEnabled = 48 == (data2[3] & 48);
                                        if (!omhEnabled || data2.length <= 10) {
                                            loge("OMH EF CST data length = " + data2.length);
                                        } else {
                                            mmsicpEnabled = 12 == (data2[10] & 12);
                                        }
                                    } else {
                                        loge("OMH EF CST data length = " + data2.length);
                                    }
                                } else if (data2.length > 4) {
                                    omhEnabled = 4 == (data2[4] & 4);
                                    if (omhEnabled) {
                                        mmsicpEnabled = 4 == (data2[2] & 4);
                                    }
                                } else {
                                    loge("CSIM EF CST data length = " + data2.length);
                                }
                                log("mms icp enabled =" + mmsicpEnabled + " omhEnabled " + omhEnabled);
                                SystemProperties.set("ril.cdma.omhcard", omhEnabled ? "true" : "false");
                            }
                        }
                        fetchOMHCardRecords(omhEnabled);
                        break;
                    case 10:
                        AsyncResult ar3 = (AsyncResult) msg.obj;
                        String[] localTemp = (String[]) ar3.result;
                        if (ar3.exception == null) {
                            this.mMyMobileNumber = localTemp[0];
                            this.mMin2Min1 = localTemp[3];
                            this.mPrlVersion = localTemp[4];
                            log("MDN: " + this.mMyMobileNumber + " MIN: " + this.mMin2Min1);
                            break;
                        }
                        break;
                    case 14:
                        AsyncResult ar4 = (AsyncResult) msg.obj;
                        if (ar4.exception != null) {
                            Rlog.i(LOG_TAG, "RuimRecords update failed", ar4.exception);
                            break;
                        }
                        break;
                    case 15:
                        AsyncResult ar5 = (AsyncResult) msg.obj;
                        if (ar5.exception != null) {
                            loge("Set EF Model failed" + ar5.exception);
                        }
                        log("EVENT_SET_MODEL_DONE");
                        break;
                    case 17:
                        log("Event EVENT_GET_SST_DONE Received");
                        break;
                    case 18:
                    case 19:
                    case 21:
                    case 22:
                        Rlog.w(LOG_TAG, "Event not supported: " + msg.what);
                        break;
                }
                if (isRecordLoadResponse) {
                    onRecordLoaded();
                }
            } catch (RuntimeException exc) {
                Rlog.w(LOG_TAG, "Exception parsing RUIM record", exc);
                if (0 != 0) {
                    onRecordLoaded();
                }
            }
        } catch (Throwable th) {
            if (0 != 0) {
                onRecordLoaded();
            }
            throw th;
        }
    }

    private void fetchOMHCardRecords(boolean isOMHCard) {
        if (isOMHCard) {
            setModel();
        }
    }

    private static String[] getAssetLanguages(Context ctx) {
        String[] locales = ctx.getAssets().getLocales();
        String[] localeLangs = new String[locales.length];
        for (int i = 0; i < locales.length; i++) {
            String localeStr = locales[i];
            int separator = localeStr.indexOf(45);
            if (separator < 0) {
                localeLangs[i] = localeStr;
            } else {
                localeLangs[i] = localeStr.substring(0, separator);
            }
        }
        return localeLangs;
    }

    private String findBestLanguage(byte[] languages) {
        String[] assetLanguages = getAssetLanguages(this.mContext);
        if (languages == null || assetLanguages == null) {
            return null;
        }
        for (int i = 0; i + 1 < languages.length; i += 2) {
            try {
                String lang = new String(languages, i, 2, "ISO-8859-1");
                for (String str : assetLanguages) {
                    if (str.equals(lang)) {
                        return lang;
                    }
                }
                continue;
            } catch (UnsupportedEncodingException e) {
                log("Failed to parse SIM language records");
            }
        }
        return null;
    }

    private void setLocaleFromCsim() {
        String prefLang = findBestLanguage(this.mEFli);
        if (prefLang == null) {
            prefLang = findBestLanguage(this.mEFpl);
        }
        if (prefLang != null) {
            String imsi = getIMSI();
            String country = null;
            if (imsi != null) {
                country = MccTable.countryCodeForMcc(Integer.parseInt(imsi.substring(0, 3)));
            }
            log("Setting locale to " + prefLang + "_" + country);
            MccTable.setSystemLocale(this.mContext, prefLang, country);
            return;
        }
        log("No suitable CSIM selected locale");
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void onRecordLoaded() {
        this.mRecordsToLoad--;
        log("onRecordLoaded " + this.mRecordsToLoad + " requested: " + this.mRecordsRequested);
        if (this.mRecordsToLoad == 0 && this.mRecordsRequested) {
            onAllRecordsLoaded();
        } else if (this.mRecordsToLoad < 0) {
            loge("recordsToLoad <0, programmer error suspected");
            this.mRecordsToLoad = 0;
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void onAllRecordsLoaded() {
        log("record load complete");
        String operator = getOperatorNumeric();
        if (!TextUtils.isEmpty(operator)) {
            log("onAllRecordsLoaded set 'gsm.sim.operator.numeric' to operator='" + operator + "'");
            setSystemProperty("gsm.sim.operator.numeric", operator);
            setSystemProperty("net.cdma.ruim.operator.numeric", operator);
        } else {
            log("onAllRecordsLoaded empty 'gsm.sim.operator.numeric' skipping");
        }
        if (!TextUtils.isEmpty(this.mImsi)) {
            log("onAllRecordsLoaded set mcc imsi=" + this.mImsi);
            setSystemProperty("gsm.sim.operator.iso-country", MccTable.countryCodeForMcc(Integer.parseInt(this.mImsi.substring(0, 3))));
        } else {
            log("onAllRecordsLoaded empty imsi skipping setting mcc");
        }
        setLocaleFromCsim();
        if (isAppStateReady()) {
            this.mRecordsLoadedRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Object) null, (Throwable) null));
        } else {
            log("onAllRecordsLoaded: AppState is not ready; not notifying the registrants");
        }
        if (!TextUtils.isEmpty(this.mMdn)) {
            int[] subIds = SubscriptionController.getInstance().getSubId(this.mParentApp.getUiccCard().getPhoneId());
            if (subIds != null) {
                log("Calling setDisplayNumber for subId and number " + subIds[0] + " and " + this.mMdn);
                SubscriptionManager.from(this.mContext).setDisplayNumber(this.mMdn, subIds[0]);
                return;
            }
            log("Cannot call setDisplayNumber: invalid subId");
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void onReady() {
        fetchRuimRecords();
        this.mCi.getCDMASubscription(obtainMessage(10));
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.android.internal.telephony.uicc.IccRecords
    public void recordsRequired() {
        log("recordsRequired");
        this.mRecordsRequired = true;
        fetchRuimRecords();
    }

    private void fetchRuimRecords() {
        boolean mESNTrackerEnabled = this.mContext.getResources().getBoolean(17957026);
        if (this.mRecordsRequested || ((!mESNTrackerEnabled && !this.mRecordsRequired) || IccCardApplicationStatus.AppState.APPSTATE_READY != this.mParentApp.getState())) {
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
        if (mESNTrackerEnabled) {
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

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class EfCsimModelLoaded implements IccRecords.IccRecordLoaded {
        private EfCsimModelLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_CSIM_MODEL";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            RuimRecords.this.log("EF_CSIM_MODEL=" + IccUtils.bytesToHexString((byte[]) ar.result));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class EfRuimModelLoaded implements IccRecords.IccRecordLoaded {
        private EfRuimModelLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_RUIM_MODEL";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            RuimRecords.this.log("EF_RUIM_MODEL=" + IccUtils.bytesToHexString((byte[]) ar.result));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class EfRuimIdLoaded implements IccRecords.IccRecordLoaded {
        private EfRuimIdLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_RUIM_ID";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            byte b;
            byte[] data = (byte[]) ar.result;
            RuimRecords.this.log("RuimId Data=" + IccUtils.bytesToHexString(data));
            if (data != null && (b = data[0]) < 8) {
                byte[] decodeData = new byte[b];
                for (int i = 0; i < b; i++) {
                    decodeData[i] = data[b - i];
                }
                RuimRecords.this.log("RUIM_ID=" + IccUtils.bytesToHexString(decodeData));
            }
        }
    }

    private void setModel() {
        byte[] data = new byte[126];
        byte[] model = null;
        byte[] manufacturer = null;
        byte[] softwareVersion = null;
        for (int i = 0; i < data.length; i++) {
            data[i] = -1;
        }
        try {
            model = Build.MODEL.getBytes("UTF-8");
            manufacturer = Build.MANUFACTURER.getBytes("UTF-8");
            softwareVersion = SystemProperties.get("persist.product.sw.version", Build.DISPLAY).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            loge("BearerData encode failed: " + e);
        }
        data[0] = 0;
        data[1] = 1;
        System.arraycopy(model, 0, data, 2, model.length > 32 ? 32 : model.length);
        int offset = 2 + 32;
        System.arraycopy(manufacturer, 0, data, offset, manufacturer.length > 32 ? 32 : manufacturer.length);
        int offset2 = offset + 32;
        int versionLength = softwareVersion.length > SOFTWARE_VERSION_INFORMATION_SIZE ? SOFTWARE_VERSION_INFORMATION_SIZE : softwareVersion.length;
        System.arraycopy(softwareVersion, 0, data, offset2, versionLength);
        log("model: " + IccUtils.bytesToHexString(model) + "manufacturer: " + IccUtils.bytesToHexString(manufacturer) + "softwareVersion: " + IccUtils.bytesToHexString(softwareVersion));
        log("EF model write data : " + IccUtils.bytesToHexString(data) + " version length=" + versionLength);
        if (this.mParentApp == null || this.mParentApp.getType() != IccCardApplicationStatus.AppType.APPTYPE_CSIM) {
            this.mFh.updateEFTransparent(IccConstants.EF_MODEL, data, obtainMessage(15, Integer.valueOf((int) IccConstants.EF_MODEL)));
            return;
        }
        log("CSIM card type, set csim model");
        this.mFh.updateEFTransparent(IccConstants.EF_CSIM_MODEL, data, obtainMessage(15, Integer.valueOf((int) IccConstants.EF_CSIM_MODEL)));
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public int getDisplayRule(String plmn) {
        return 0;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public boolean isProvisioned() {
        if (SystemProperties.getBoolean("persist.radio.test-csim", false)) {
            return true;
        }
        if (this.mParentApp == null) {
            return false;
        }
        if (this.mParentApp.getType() == IccCardApplicationStatus.AppType.APPTYPE_CSIM) {
            return (this.mMdn == null || this.mMin == null) ? false : true;
        }
        return true;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        log("RuimRecords:setVoiceMessageWaiting - NOP for CDMA");
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public int getVoiceMessageCount() {
        log("RuimRecords:getVoiceMessageCount - NOP for CDMA");
        return 0;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void handleFileUpdate(int efid) {
        switch (efid) {
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

    public String getMdn() {
        return this.mMdn;
    }

    public String getMin() {
        return this.mMin;
    }

    public String getSid() {
        return this.mHomeSystemId;
    }

    public String getNid() {
        return this.mHomeNetworkId;
    }

    public boolean getCsimSpnDisplayCondition() {
        return this.mCsimSpnDisplayCondition;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[RuimRecords] " + s);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[RuimRecords] " + s);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("RuimRecords: " + this);
        pw.println(" extends:");
        super.dump(fd, pw, args);
        pw.println(" mOtaCommited=" + this.mOtaCommited);
        pw.println(" mMyMobileNumber=" + this.mMyMobileNumber);
        pw.println(" mMin2Min1=" + this.mMin2Min1);
        pw.println(" mPrlVersion=" + this.mPrlVersion);
        pw.println(" mEFpl[]=" + Arrays.toString(this.mEFpl));
        pw.println(" mEFli[]=" + Arrays.toString(this.mEFli));
        pw.println(" mCsimSpnDisplayCondition=" + this.mCsimSpnDisplayCondition);
        pw.println(" mMdn=" + this.mMdn);
        pw.println(" mMin=" + this.mMin);
        pw.println(" mHomeSystemId=" + this.mHomeSystemId);
        pw.println(" mHomeNetworkId=" + this.mHomeNetworkId);
        pw.flush();
    }
}
