package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.net.ConnectivityManager;
import android.net.LocalSocket;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;
import android.telephony.CellInfo;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaDisplayInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaLineControlInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaNumberInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaRedirectingNumberInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaSignalInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaT53AudioControlInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaT53ClirInfoRec;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.cdma.SignalToneUtil;
import com.android.internal.telephony.dataconnection.ApnProfileOmh;
import com.android.internal.telephony.dataconnection.ApnSetting;
import com.android.internal.telephony.dataconnection.DataCallResponse;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.dataconnection.DcFailCause;
import com.android.internal.telephony.gsm.CallFailCause;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SsData;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccRefreshResponse;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.SpnOverride;
import com.google.android.mms.pdu.CharacterSets;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import jp.co.sharp.telephony.OemCdmaTelephonyManager;
import jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data;

public final class RIL extends BaseCommands implements CommandsInterface {
    private static final int BYTE_SIZE = 1;
    private static final int CDMA_BROADCAST_SMS_NO_OF_SERVICE_CATEGORIES = 31;
    private static final int CDMA_BSI_NO_OF_INTS_STRUCT = 3;
    public static final String COLUM_TABLE_MCN_SETTING_DURATION_TIME = "COLUM_TABLE_MCN_SETTING_DURATION_TIME";
    public static final String COLUM_TABLE_MCN_SETTING_DURATION_TYPE = "COLUM_TABLE_MCN_SETTING_DURATION_TYPE";
    private static final int DEFAULT_WAKE_LOCK_TIMEOUT = 60000;
    static final int EVENT_SEARCHING_NETWORK_DELAY = 4;
    static final int EVENT_SEND = 1;
    static final int EVENT_WAKE_LOCK_TIMEOUT = 2;
    private static final int INT_SIZE = 4;
    static final boolean LOG_NS = LOG_NW;
    static final boolean LOG_NW = SystemProperties.getBoolean("persist.sharp.telephony.nvlognw", false);
    static final boolean LOG_SD = SystemProperties.getBoolean("persist.sharp.telephony.nvlogsd", false);
    private static final int OEMHOOK_BASE = 524288;
    private static final int OEMHOOK_EVT_HOOK_GET_MODEM_CAPABILITY = 524323;
    private static final int OEMHOOK_EVT_HOOK_SET_LOCAL_CALL_HOLD = 524301;
    private static final int OEMHOOK_EVT_HOOK_UPDATE_SUB_BINDING = 524324;
    private static final String OEM_IDENTIFIER = "QOEMHOOK";
    static final int RESPONSE_SOLICITED = 0;
    static final int RESPONSE_UNSOLICITED = 1;
    static final boolean RILJ_LOGD = true;
    static final boolean RILJ_LOGV = false;
    static final String RILJ_LOG_TAG = "RILJ";
    static final int RIL_MAX_COMMAND_BYTES = 8192;
    static final long SEARCH_NETWORK_DELAY_TIME = 3000;
    private static final boolean SMARTCARD_DBG = false;
    static final String[] SOCKET_NAME_RIL = new String[]{"rild", "rild2", "rild3"};
    static final int SOCKET_OPEN_RETRY_MILLIS = 4000;
    final int OEMHOOK_UNSOL_SIM_REFRESH;
    final int OEMHOOK_UNSOL_WWAN_IWLAN_COEXIST;
    final int QCRIL_EVT_HOOK_UNSOL_MODEM_CAPABILITY;
    Display mDefaultDisplay;
    int mDefaultDisplayState;
    private final DisplayListener mDisplayListener;
    int mHeaderSize;
    private Integer mInstanceId;
    BroadcastReceiver mIntentReceiver;
    Object mLastNITZTimeInfo;
    private boolean mLimitationByChameleon;
    private boolean mLimitedService;
    private boolean mRILConnected_SetInitialAttachApn_OnceSkip;
    private boolean mRILConnected_UpdateOemDataSettings_OnceSkip;
    RILReceiver mReceiver;
    Thread mReceiverThread;
    SparseArray<RILRequest> mRequestList;
    RILSender mSender;
    HandlerThread mSenderThread;
    private String mSetInitialAttachApn_Apn;
    private int mSetInitialAttachApn_AuthType;
    private String mSetInitialAttachApn_Password;
    private String mSetInitialAttachApn_Protocol;
    private String mSetInitialAttachApn_Username;
    private boolean mSetLimitationByChameleon;
    LocalSocket mSocket;
    AtomicBoolean mTestingEmergencyCall;
    private boolean mUpdateOemDataSettings_DataRoaming;
    private boolean mUpdateOemDataSettings_MobileData;
    WakeLock mWakeLock;
    int mWakeLockCount;
    final int mWakeLockTimeout;

    class RILRadioLogWriter implements Runnable {
        public static final int LOGBUF_MAIN = 0;
        public static final int LOGBUF_RADIO = 1;
        public static final int LOGBUF_RIL_CMD_INFO = 2;
        private static final int RADIO_LOG_BUFFERSIZE = 262144;
        private static final int RADIO_LOG_REC_SIZE_TYPE1 = 8192;
        private static final int RADIO_LOG_REC_SIZE_TYPE2 = 204800;
        public static final int RECTYPE_NETWORK_SELECTION = 2;
        public static final int RECTYPE_SETNETWORK = 0;
        public static final int RECTYPE_SHUTDOWN = 1;
        private String mAppendString = null;
        private int mBufType;
        private int mRecType;
        private String mStackTrace = null;
        private int mWriteSize;

        public RILRadioLogWriter(int i, int i2) {
            if (TelBrand.IS_DCM) {
                if (i == 0) {
                    this.mRecType = 0;
                    this.mWriteSize = 8192;
                } else if (i == 2) {
                    this.mRecType = 2;
                    this.mWriteSize = 8192;
                } else {
                    this.mRecType = 1;
                    this.mWriteSize = RADIO_LOG_REC_SIZE_TYPE2;
                }
                if (i2 == 0) {
                    this.mBufType = 0;
                } else if (i2 == 2) {
                    this.mBufType = 2;
                } else {
                    this.mBufType = 1;
                }
            }
        }

        private String getPrefix(int i, int i2) {
            String str = "";
            if (!TelBrand.IS_DCM) {
                return str;
            }
            str = i == 0 ? "N" : i == 2 ? "NS" : "S";
            return i2 == 0 ? str + "M" : i2 == 2 ? str + "RCI" : str + "R";
        }

        /* JADX WARNING: Unknown top exception splitter block from list: {B:87:0x0166=Splitter:B:87:0x0166, B:71:0x011b=Splitter:B:71:0x011b} */
        /* JADX WARNING: Removed duplicated region for block: B:78:0x013c A:{SYNTHETIC, Splitter:B:78:0x013c} */
        /* JADX WARNING: Removed duplicated region for block: B:85:0x0163 A:{SYNTHETIC, Splitter:B:85:0x0163} */
        /* JADX WARNING: Removed duplicated region for block: B:85:0x0163 A:{SYNTHETIC, Splitter:B:85:0x0163} */
        private int getRadioLogIndex(java.lang.String r10, int r11, int r12) {
            /*
            r9 = this;
            r1 = 30;
            r0 = 29;
            r3 = 0;
            monitor-enter(r9);
            r2 = com.android.internal.telephony.TelBrand.IS_DCM;	 Catch:{ all -> 0x00a5 }
            if (r2 != 0) goto L_0x000d;
        L_0x000a:
            r0 = -1;
        L_0x000b:
            monitor-exit(r9);
            return r0;
        L_0x000d:
            r2 = 10;
            r5 = 9;
            r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00a5 }
            r4.<init>();	 Catch:{ all -> 0x00a5 }
            r4 = r4.append(r10);	 Catch:{ all -> 0x00a5 }
            r6 = r9.getPrefix(r11, r12);	 Catch:{ all -> 0x00a5 }
            r4 = r4.append(r6);	 Catch:{ all -> 0x00a5 }
            r6 = "1000";
            r4 = r4.append(r6);	 Catch:{ all -> 0x00a5 }
            r6 = r4.toString();	 Catch:{ all -> 0x00a5 }
            if (r11 != 0) goto L_0x00a8;
        L_0x002e:
            r4 = r1;
        L_0x002f:
            r1 = new java.io.FileInputStream;	 Catch:{ FileNotFoundException -> 0x00ad }
            r1.<init>(r6);	 Catch:{ FileNotFoundException -> 0x00ad }
            r2 = r1;
        L_0x0035:
            if (r2 == 0) goto L_0x0062;
        L_0x0037:
            r1 = r2.available();	 Catch:{ NumberFormatException -> 0x00cd, IOException -> 0x00f2 }
            if (r1 <= 0) goto L_0x005f;
        L_0x003d:
            r1 = r2.available();	 Catch:{ NumberFormatException -> 0x00cd, IOException -> 0x00f2 }
            r1 = new byte[r1];	 Catch:{ NumberFormatException -> 0x00cd, IOException -> 0x00f2 }
            if (r1 == 0) goto L_0x005f;
        L_0x0045:
            r2.read(r1);	 Catch:{ NumberFormatException -> 0x00cd, IOException -> 0x00f2 }
            r5 = new java.lang.String;	 Catch:{ NumberFormatException -> 0x00cd, IOException -> 0x00f2 }
            r7 = "US-ASCII";
            r5.<init>(r1, r7);	 Catch:{ NumberFormatException -> 0x00cd, IOException -> 0x00f2 }
            if (r5 == 0) goto L_0x005f;
        L_0x0051:
            r1 = java.lang.Integer.valueOf(r5);	 Catch:{ NumberFormatException -> 0x00cd, IOException -> 0x00f2 }
            r0 = r1.intValue();	 Catch:{ NumberFormatException -> 0x00cd, IOException -> 0x00f2 }
            if (r0 < 0) goto L_0x005d;
        L_0x005b:
            if (r0 < r4) goto L_0x005f;
        L_0x005d:
            r0 = r4 + -1;
        L_0x005f:
            r2.close();	 Catch:{ IOException -> 0x0185 }
        L_0x0062:
            r0 = r0 + 1;
            r0 = r0 % r4;
            r2 = new java.io.FileOutputStream;	 Catch:{ IOException -> 0x011c, all -> 0x018c }
            r2.<init>(r6);	 Catch:{ IOException -> 0x011c, all -> 0x018c }
            if (r2 == 0) goto L_0x0080;
        L_0x006c:
            r1 = java.lang.String.valueOf(r0);	 Catch:{ IOException -> 0x018a, all -> 0x0160 }
            if (r1 == 0) goto L_0x0080;
        L_0x0072:
            r3 = "US-ASCII";
            r1 = r1.getBytes(r3);	 Catch:{ IOException -> 0x018a, all -> 0x0160 }
            if (r1 == 0) goto L_0x0080;
        L_0x007a:
            r2.write(r1);	 Catch:{ IOException -> 0x018a, all -> 0x0160 }
            r2.flush();	 Catch:{ IOException -> 0x018a, all -> 0x0160 }
        L_0x0080:
            if (r2 == 0) goto L_0x000b;
        L_0x0082:
            r2.close();	 Catch:{ IOException -> 0x0086 }
            goto L_0x000b;
        L_0x0086:
            r1 = move-exception;
            r2 = "RILJ";
            r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00a5 }
            r3.<init>();	 Catch:{ all -> 0x00a5 }
            r3 = r3.append(r6);	 Catch:{ all -> 0x00a5 }
            r4 = " close err:";
            r3 = r3.append(r4);	 Catch:{ all -> 0x00a5 }
            r1 = r3.append(r1);	 Catch:{ all -> 0x00a5 }
            r1 = r1.toString();	 Catch:{ all -> 0x00a5 }
            android.telephony.Rlog.e(r2, r1);	 Catch:{ all -> 0x00a5 }
            goto L_0x000b;
        L_0x00a5:
            r0 = move-exception;
            monitor-exit(r9);
            throw r0;
        L_0x00a8:
            r4 = 2;
            if (r11 != r4) goto L_0x0191;
        L_0x00ab:
            r4 = r1;
            goto L_0x002f;
        L_0x00ad:
            r1 = move-exception;
            r2 = "RILJ";
            r5 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00a5 }
            r5.<init>();	 Catch:{ all -> 0x00a5 }
            r5 = r5.append(r6);	 Catch:{ all -> 0x00a5 }
            r7 = " open err:";
            r5 = r5.append(r7);	 Catch:{ all -> 0x00a5 }
            r1 = r5.append(r1);	 Catch:{ all -> 0x00a5 }
            r1 = r1.toString();	 Catch:{ all -> 0x00a5 }
            android.telephony.Rlog.w(r2, r1);	 Catch:{ all -> 0x00a5 }
            r2 = r3;
            goto L_0x0035;
        L_0x00cd:
            r1 = move-exception;
            r5 = "RILJ";
            r7 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0117 }
            r7.<init>();	 Catch:{ all -> 0x0117 }
            r7 = r7.append(r6);	 Catch:{ all -> 0x0117 }
            r8 = " read err:";
            r7 = r7.append(r8);	 Catch:{ all -> 0x0117 }
            r1 = r7.append(r1);	 Catch:{ all -> 0x0117 }
            r1 = r1.toString();	 Catch:{ all -> 0x0117 }
            android.telephony.Rlog.e(r5, r1);	 Catch:{ all -> 0x0117 }
            r2.close();	 Catch:{ IOException -> 0x00ef }
            goto L_0x0062;
        L_0x00ef:
            r1 = move-exception;
            goto L_0x0062;
        L_0x00f2:
            r1 = move-exception;
            r5 = "RILJ";
            r7 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0117 }
            r7.<init>();	 Catch:{ all -> 0x0117 }
            r7 = r7.append(r6);	 Catch:{ all -> 0x0117 }
            r8 = " read err:";
            r7 = r7.append(r8);	 Catch:{ all -> 0x0117 }
            r1 = r7.append(r1);	 Catch:{ all -> 0x0117 }
            r1 = r1.toString();	 Catch:{ all -> 0x0117 }
            android.telephony.Rlog.e(r5, r1);	 Catch:{ all -> 0x0117 }
            r2.close();	 Catch:{ IOException -> 0x0114 }
            goto L_0x0062;
        L_0x0114:
            r1 = move-exception;
            goto L_0x0062;
        L_0x0117:
            r0 = move-exception;
            r2.close();	 Catch:{ IOException -> 0x0188 }
        L_0x011b:
            throw r0;	 Catch:{ all -> 0x00a5 }
        L_0x011c:
            r1 = move-exception;
            r2 = r3;
        L_0x011e:
            r3 = "RILJ";
            r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x018f }
            r4.<init>();	 Catch:{ all -> 0x018f }
            r4 = r4.append(r6);	 Catch:{ all -> 0x018f }
            r5 = " write err:";
            r4 = r4.append(r5);	 Catch:{ all -> 0x018f }
            r1 = r4.append(r1);	 Catch:{ all -> 0x018f }
            r1 = r1.toString();	 Catch:{ all -> 0x018f }
            android.telephony.Rlog.e(r3, r1);	 Catch:{ all -> 0x018f }
            if (r2 == 0) goto L_0x000b;
        L_0x013c:
            r2.close();	 Catch:{ IOException -> 0x0141 }
            goto L_0x000b;
        L_0x0141:
            r1 = move-exception;
            r2 = "RILJ";
            r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00a5 }
            r3.<init>();	 Catch:{ all -> 0x00a5 }
            r3 = r3.append(r6);	 Catch:{ all -> 0x00a5 }
            r4 = " close err:";
            r3 = r3.append(r4);	 Catch:{ all -> 0x00a5 }
            r1 = r3.append(r1);	 Catch:{ all -> 0x00a5 }
            r1 = r1.toString();	 Catch:{ all -> 0x00a5 }
            android.telephony.Rlog.e(r2, r1);	 Catch:{ all -> 0x00a5 }
            goto L_0x000b;
        L_0x0160:
            r0 = move-exception;
        L_0x0161:
            if (r2 == 0) goto L_0x0166;
        L_0x0163:
            r2.close();	 Catch:{ IOException -> 0x0167 }
        L_0x0166:
            throw r0;	 Catch:{ all -> 0x00a5 }
        L_0x0167:
            r1 = move-exception;
            r2 = "RILJ";
            r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00a5 }
            r3.<init>();	 Catch:{ all -> 0x00a5 }
            r3 = r3.append(r6);	 Catch:{ all -> 0x00a5 }
            r4 = " close err:";
            r3 = r3.append(r4);	 Catch:{ all -> 0x00a5 }
            r1 = r3.append(r1);	 Catch:{ all -> 0x00a5 }
            r1 = r1.toString();	 Catch:{ all -> 0x00a5 }
            android.telephony.Rlog.e(r2, r1);	 Catch:{ all -> 0x00a5 }
            goto L_0x0166;
        L_0x0185:
            r1 = move-exception;
            goto L_0x0062;
        L_0x0188:
            r1 = move-exception;
            goto L_0x011b;
        L_0x018a:
            r1 = move-exception;
            goto L_0x011e;
        L_0x018c:
            r0 = move-exception;
            r2 = r3;
            goto L_0x0161;
        L_0x018f:
            r0 = move-exception;
            goto L_0x0161;
        L_0x0191:
            r4 = r2;
            r0 = r5;
            goto L_0x002f;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.RIL$RILRadioLogWriter.getRadioLogIndex(java.lang.String, int, int):int");
        }

        /* JADX WARNING: Unknown top exception splitter block from list: {B:77:0x0221=Splitter:B:77:0x0221, B:64:0x01c2=Splitter:B:64:0x01c2} */
        /* JADX WARNING: Unknown top exception splitter block from list: {B:38:0x00ed=Splitter:B:38:0x00ed, B:47:0x013d=Splitter:B:47:0x013d} */
        /* JADX WARNING: Removed duplicated region for block: B:164:0x0452 A:{SYNTHETIC, Splitter:B:164:0x0452} */
        /* JADX WARNING: Removed duplicated region for block: B:167:0x0457  */
        /* JADX WARNING: Removed duplicated region for block: B:193:0x04fb A:{SYNTHETIC, Splitter:B:193:0x04fb} */
        /* JADX WARNING: Removed duplicated region for block: B:90:0x0285 A:{SYNTHETIC, Splitter:B:90:0x0285} */
        /* JADX WARNING: Removed duplicated region for block: B:23:0x008f  */
        /* JADX WARNING: Removed duplicated region for block: B:235:? A:{SYNTHETIC, RETURN} */
        /* JADX WARNING: Removed duplicated region for block: B:30:0x009e A:{SYNTHETIC, Splitter:B:30:0x009e} */
        /* JADX WARNING: Removed duplicated region for block: B:23:0x008f  */
        /* JADX WARNING: Removed duplicated region for block: B:30:0x009e A:{SYNTHETIC, Splitter:B:30:0x009e} */
        /* JADX WARNING: Removed duplicated region for block: B:235:? A:{SYNTHETIC, RETURN} */
        /* JADX WARNING: Removed duplicated region for block: B:120:0x036c A:{SYNTHETIC, Splitter:B:120:0x036c} */
        /* JADX WARNING: Removed duplicated region for block: B:241:? A:{SYNTHETIC, RETURN} */
        /* JADX WARNING: Removed duplicated region for block: B:129:0x038f A:{SYNTHETIC, Splitter:B:129:0x038f} */
        /* JADX WARNING: Removed duplicated region for block: B:164:0x0452 A:{SYNTHETIC, Splitter:B:164:0x0452} */
        /* JADX WARNING: Removed duplicated region for block: B:167:0x0457  */
        /* JADX WARNING: Removed duplicated region for block: B:164:0x0452 A:{SYNTHETIC, Splitter:B:164:0x0452} */
        /* JADX WARNING: Removed duplicated region for block: B:167:0x0457  */
        /* JADX WARNING: Removed duplicated region for block: B:50:0x0161 A:{SYNTHETIC, Splitter:B:50:0x0161} */
        /* JADX WARNING: Removed duplicated region for block: B:23:0x008f  */
        /* JADX WARNING: Removed duplicated region for block: B:235:? A:{SYNTHETIC, RETURN} */
        /* JADX WARNING: Removed duplicated region for block: B:30:0x009e A:{SYNTHETIC, Splitter:B:30:0x009e} */
        /* JADX WARNING: Removed duplicated region for block: B:56:0x018e A:{SYNTHETIC, Splitter:B:56:0x018e} */
        /* JADX WARNING: Removed duplicated region for block: B:193:0x04fb A:{SYNTHETIC, Splitter:B:193:0x04fb} */
        /* JADX WARNING: Removed duplicated region for block: B:200:0x051d A:{ExcHandler: all (th java.lang.Throwable), Splitter:B:105:0x02ef} */
        /* JADX WARNING: Removed duplicated region for block: B:56:0x018e A:{SYNTHETIC, Splitter:B:56:0x018e} */
        /* JADX WARNING: Removed duplicated region for block: B:239:? A:{SYNTHETIC, RETURN} */
        /* JADX WARNING: Removed duplicated region for block: B:80:0x0245 A:{SYNTHETIC, Splitter:B:80:0x0245} */
        /* JADX WARNING: Removed duplicated region for block: B:90:0x0285 A:{SYNTHETIC, Splitter:B:90:0x0285} */
        /* JADX WARNING: Removed duplicated region for block: B:245:? A:{SYNTHETIC, RETURN} */
        /* JADX WARNING: Removed duplicated region for block: B:186:0x04d7 A:{SYNTHETIC, Splitter:B:186:0x04d7} */
        /* JADX WARNING: Removed duplicated region for block: B:154:0x0426 A:{SYNTHETIC, Splitter:B:154:0x0426} */
        /* JADX WARNING: Removed duplicated region for block: B:157:0x042b  */
        /* JADX WARNING: Removed duplicated region for block: B:120:0x036c A:{SYNTHETIC, Splitter:B:120:0x036c} */
        /* JADX WARNING: Removed duplicated region for block: B:129:0x038f A:{SYNTHETIC, Splitter:B:129:0x038f} */
        /* JADX WARNING: Removed duplicated region for block: B:241:? A:{SYNTHETIC, RETURN} */
        /* JADX WARNING: Failed to process nested try/catch */
        /* JADX WARNING: Missing block: B:200:0x051d, code skipped:
            r1 = th;
     */
        /* JADX WARNING: Missing block: B:201:0x051e, code skipped:
            r4 = r5;
     */
        /* JADX WARNING: Missing block: B:202:0x0521, code skipped:
            r6 = e;
     */
        public void run() {
            /*
            r11 = this;
            r6 = 262144; // 0x40000 float:3.67342E-40 double:1.295163E-318;
            r2 = 0;
            r3 = 0;
            r0 = com.android.internal.telephony.TelBrand.IS_DCM;
            if (r0 == 0) goto L_0x002f;
        L_0x0008:
            r0 = java.lang.Runtime.getRuntime();
            r0 = r0.freeMemory();
            r4 = 524288; // 0x80000 float:7.34684E-40 double:2.590327E-318;
            r4 = (r0 > r4 ? 1 : (r0 == r4 ? 0 : -1));
            if (r4 > 0) goto L_0x0030;
        L_0x0017:
            r2 = "RILJ";
            r3 = new java.lang.StringBuilder;
            r3.<init>();
            r4 = "Deficient freeMemory : ";
            r3 = r3.append(r4);
            r0 = r3.append(r0);
            r0 = r0.toString();
            android.telephony.Rlog.e(r2, r0);
        L_0x002f:
            return;
        L_0x0030:
            r7 = new byte[r6];
            if (r7 != 0) goto L_0x003c;
        L_0x0034:
            r0 = "RILJ";
            r1 = "logcat buffer alloc err";
            android.telephony.Rlog.e(r0, r1);
            goto L_0x002f;
        L_0x003c:
            r0 = r11.mAppendString;
            if (r0 == 0) goto L_0x02b8;
        L_0x0040:
            r0 = r11.mRecType;
            r1 = r11.mBufType;
            r4 = r11.getPrefix(r0, r1);
            r0 = r11.mAppendString;
            r5 = r0.getBytes();
            r6 = r5.length;
            r1 = new java.io.FileInputStream;	 Catch:{ FileNotFoundException -> 0x00eb, IOException -> 0x013b, all -> 0x0555 }
            r0 = new java.lang.StringBuilder;	 Catch:{ FileNotFoundException -> 0x00eb, IOException -> 0x013b, all -> 0x0555 }
            r0.<init>();	 Catch:{ FileNotFoundException -> 0x00eb, IOException -> 0x013b, all -> 0x0555 }
            r8 = "/durable/tmp/";
            r0 = r0.append(r8);	 Catch:{ FileNotFoundException -> 0x00eb, IOException -> 0x013b, all -> 0x0555 }
            r0 = r0.append(r4);	 Catch:{ FileNotFoundException -> 0x00eb, IOException -> 0x013b, all -> 0x0555 }
            r0 = r0.toString();	 Catch:{ FileNotFoundException -> 0x00eb, IOException -> 0x013b, all -> 0x0555 }
            r1.<init>(r0);	 Catch:{ FileNotFoundException -> 0x00eb, IOException -> 0x013b, all -> 0x0555 }
            if (r1 == 0) goto L_0x0070;
        L_0x0069:
            r0 = 0;
            r8 = 262144; // 0x40000 float:3.67342E-40 double:1.295163E-318;
            r3 = r1.read(r7, r0, r8);	 Catch:{ FileNotFoundException -> 0x052e, IOException -> 0x0531, all -> 0x018b }
        L_0x0070:
            if (r1 == 0) goto L_0x0075;
        L_0x0072:
            r1.close();	 Catch:{ IOException -> 0x00c7 }
        L_0x0075:
            r1 = new java.io.FileOutputStream;	 Catch:{ FileNotFoundException -> 0x052a, IOException -> 0x0526, all -> 0x0534 }
            r0 = new java.lang.StringBuilder;	 Catch:{ FileNotFoundException -> 0x052a, IOException -> 0x0526, all -> 0x0534 }
            r0.<init>();	 Catch:{ FileNotFoundException -> 0x052a, IOException -> 0x0526, all -> 0x0534 }
            r8 = "/durable/tmp/";
            r0 = r0.append(r8);	 Catch:{ FileNotFoundException -> 0x052a, IOException -> 0x0526, all -> 0x0534 }
            r0 = r0.append(r4);	 Catch:{ FileNotFoundException -> 0x052a, IOException -> 0x0526, all -> 0x0534 }
            r0 = r0.toString();	 Catch:{ FileNotFoundException -> 0x052a, IOException -> 0x0526, all -> 0x0534 }
            r1.<init>(r0);	 Catch:{ FileNotFoundException -> 0x052a, IOException -> 0x0526, all -> 0x0534 }
            if (r1 == 0) goto L_0x009c;
        L_0x008f:
            if (r3 > 0) goto L_0x0210;
        L_0x0091:
            r0 = r11.mWriteSize;	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
            if (r6 > r0) goto L_0x01b6;
        L_0x0095:
            r0 = 0;
            r1.write(r5, r0, r6);	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
        L_0x0099:
            r1.flush();	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
        L_0x009c:
            if (r1 == 0) goto L_0x002f;
        L_0x009e:
            r1.close();	 Catch:{ IOException -> 0x00a2 }
            goto L_0x002f;
        L_0x00a2:
            r0 = move-exception;
            r1 = "RILJ";
            r2 = new java.lang.StringBuilder;
            r2.<init>();
            r3 = "/durable/tmp/";
            r2 = r2.append(r3);
            r2 = r2.append(r4);
            r3 = " radiolog close err:";
            r2 = r2.append(r3);
            r0 = r2.append(r0);
            r0 = r0.toString();
            android.telephony.Rlog.e(r1, r0);
            goto L_0x002f;
        L_0x00c7:
            r0 = move-exception;
            r1 = "RILJ";
            r8 = new java.lang.StringBuilder;
            r8.<init>();
            r9 = "/durable/tmp/";
            r8 = r8.append(r9);
            r8 = r8.append(r4);
            r9 = " close err:";
            r8 = r8.append(r9);
            r0 = r8.append(r0);
            r0 = r0.toString();
            android.telephony.Rlog.e(r1, r0);
            goto L_0x0075;
        L_0x00eb:
            r0 = move-exception;
            r1 = r2;
        L_0x00ed:
            r8 = "RILJ";
            r9 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0559 }
            r9.<init>();	 Catch:{ all -> 0x0559 }
            r10 = "/durable/tmp/";
            r9 = r9.append(r10);	 Catch:{ all -> 0x0559 }
            r9 = r9.append(r4);	 Catch:{ all -> 0x0559 }
            r10 = " open err:";
            r9 = r9.append(r10);	 Catch:{ all -> 0x0559 }
            r0 = r9.append(r0);	 Catch:{ all -> 0x0559 }
            r0 = r0.toString();	 Catch:{ all -> 0x0559 }
            android.telephony.Rlog.w(r8, r0);	 Catch:{ all -> 0x0559 }
            if (r1 == 0) goto L_0x0075;
        L_0x0111:
            r1.close();	 Catch:{ IOException -> 0x0116 }
            goto L_0x0075;
        L_0x0116:
            r0 = move-exception;
            r1 = "RILJ";
            r8 = new java.lang.StringBuilder;
            r8.<init>();
            r9 = "/durable/tmp/";
            r8 = r8.append(r9);
            r8 = r8.append(r4);
            r9 = " close err:";
            r8 = r8.append(r9);
            r0 = r8.append(r0);
            r0 = r0.toString();
            android.telephony.Rlog.e(r1, r0);
            goto L_0x0075;
        L_0x013b:
            r0 = move-exception;
            r1 = r2;
        L_0x013d:
            r8 = "RILJ";
            r9 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0559 }
            r9.<init>();	 Catch:{ all -> 0x0559 }
            r10 = "/durable/tmp/";
            r9 = r9.append(r10);	 Catch:{ all -> 0x0559 }
            r9 = r9.append(r4);	 Catch:{ all -> 0x0559 }
            r10 = " read err:";
            r9 = r9.append(r10);	 Catch:{ all -> 0x0559 }
            r0 = r9.append(r0);	 Catch:{ all -> 0x0559 }
            r0 = r0.toString();	 Catch:{ all -> 0x0559 }
            android.telephony.Rlog.e(r8, r0);	 Catch:{ all -> 0x0559 }
            if (r1 == 0) goto L_0x0075;
        L_0x0161:
            r1.close();	 Catch:{ IOException -> 0x0166 }
            goto L_0x0075;
        L_0x0166:
            r0 = move-exception;
            r1 = "RILJ";
            r8 = new java.lang.StringBuilder;
            r8.<init>();
            r9 = "/durable/tmp/";
            r8 = r8.append(r9);
            r8 = r8.append(r4);
            r9 = " close err:";
            r8 = r8.append(r9);
            r0 = r8.append(r0);
            r0 = r0.toString();
            android.telephony.Rlog.e(r1, r0);
            goto L_0x0075;
        L_0x018b:
            r0 = move-exception;
        L_0x018c:
            if (r1 == 0) goto L_0x0191;
        L_0x018e:
            r1.close();	 Catch:{ IOException -> 0x0192 }
        L_0x0191:
            throw r0;
        L_0x0192:
            r1 = move-exception;
            r2 = "RILJ";
            r3 = new java.lang.StringBuilder;
            r3.<init>();
            r5 = "/durable/tmp/";
            r3 = r3.append(r5);
            r3 = r3.append(r4);
            r4 = " close err:";
            r3 = r3.append(r4);
            r1 = r3.append(r1);
            r1 = r1.toString();
            android.telephony.Rlog.e(r2, r1);
            goto L_0x0191;
        L_0x01b6:
            r0 = r11.mWriteSize;	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
            r0 = r6 - r0;
            r2 = r11.mWriteSize;	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
            r1.write(r5, r0, r2);	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
            goto L_0x0099;
        L_0x01c1:
            r0 = move-exception;
        L_0x01c2:
            r2 = "RILJ";
            r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0538 }
            r3.<init>();	 Catch:{ all -> 0x0538 }
            r5 = "/durable/tmp/";
            r3 = r3.append(r5);	 Catch:{ all -> 0x0538 }
            r3 = r3.append(r4);	 Catch:{ all -> 0x0538 }
            r5 = " radiolog open err:";
            r3 = r3.append(r5);	 Catch:{ all -> 0x0538 }
            r0 = r3.append(r0);	 Catch:{ all -> 0x0538 }
            r0 = r0.toString();	 Catch:{ all -> 0x0538 }
            android.telephony.Rlog.e(r2, r0);	 Catch:{ all -> 0x0538 }
            if (r1 == 0) goto L_0x002f;
        L_0x01e6:
            r1.close();	 Catch:{ IOException -> 0x01eb }
            goto L_0x002f;
        L_0x01eb:
            r0 = move-exception;
            r1 = "RILJ";
            r2 = new java.lang.StringBuilder;
            r2.<init>();
            r3 = "/durable/tmp/";
            r2 = r2.append(r3);
            r2 = r2.append(r4);
            r3 = " radiolog close err:";
            r2 = r2.append(r3);
            r0 = r2.append(r0);
            r0 = r0.toString();
            android.telephony.Rlog.e(r1, r0);
            goto L_0x002f;
        L_0x0210:
            r0 = r3 + r6;
            r2 = r11.mWriteSize;	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
            if (r0 > r2) goto L_0x026f;
        L_0x0216:
            r0 = 0;
            r1.write(r7, r0, r3);	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
            r0 = 0;
            r1.write(r5, r0, r6);	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
            goto L_0x0099;
        L_0x0220:
            r0 = move-exception;
        L_0x0221:
            r2 = "RILJ";
            r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0538 }
            r3.<init>();	 Catch:{ all -> 0x0538 }
            r5 = "/durable/tmp/";
            r3 = r3.append(r5);	 Catch:{ all -> 0x0538 }
            r3 = r3.append(r4);	 Catch:{ all -> 0x0538 }
            r5 = " radiolog write err:";
            r3 = r3.append(r5);	 Catch:{ all -> 0x0538 }
            r0 = r3.append(r0);	 Catch:{ all -> 0x0538 }
            r0 = r0.toString();	 Catch:{ all -> 0x0538 }
            android.telephony.Rlog.e(r2, r0);	 Catch:{ all -> 0x0538 }
            if (r1 == 0) goto L_0x002f;
        L_0x0245:
            r1.close();	 Catch:{ IOException -> 0x024a }
            goto L_0x002f;
        L_0x024a:
            r0 = move-exception;
            r1 = "RILJ";
            r2 = new java.lang.StringBuilder;
            r2.<init>();
            r3 = "/durable/tmp/";
            r2 = r2.append(r3);
            r2 = r2.append(r4);
            r3 = " radiolog close err:";
            r2 = r2.append(r3);
            r0 = r2.append(r0);
            r0 = r0.toString();
            android.telephony.Rlog.e(r1, r0);
            goto L_0x002f;
        L_0x026f:
            r2 = r11.mWriteSize;	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
            if (r6 >= r2) goto L_0x0289;
        L_0x0273:
            r2 = r11.mWriteSize;	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
            r0 = r0 - r2;
            r2 = r11.mWriteSize;	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
            r2 = r2 - r6;
            r1.write(r7, r0, r2);	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
            r0 = 0;
            r1.write(r5, r0, r6);	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
            goto L_0x0099;
        L_0x0282:
            r0 = move-exception;
        L_0x0283:
            if (r1 == 0) goto L_0x0288;
        L_0x0285:
            r1.close();	 Catch:{ IOException -> 0x0294 }
        L_0x0288:
            throw r0;
        L_0x0289:
            r0 = r11.mWriteSize;	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
            r0 = r6 - r0;
            r2 = r11.mWriteSize;	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
            r1.write(r5, r0, r2);	 Catch:{ FileNotFoundException -> 0x01c1, IOException -> 0x0220, all -> 0x0282 }
            goto L_0x0099;
        L_0x0294:
            r1 = move-exception;
            r2 = "RILJ";
            r3 = new java.lang.StringBuilder;
            r3.<init>();
            r5 = "/durable/tmp/";
            r3 = r3.append(r5);
            r3 = r3.append(r4);
            r4 = " radiolog close err:";
            r3 = r3.append(r4);
            r1 = r3.append(r1);
            r1 = r1.toString();
            android.telephony.Rlog.e(r2, r1);
            goto L_0x0288;
        L_0x02b8:
            r0 = r11.mBufType;	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            if (r0 != 0) goto L_0x03af;
        L_0x02bc:
            r0 = java.lang.Runtime.getRuntime();	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            r1 = 5;
            r1 = new java.lang.String[r1];	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            r4 = 0;
            r5 = "logcat";
            r1[r4] = r5;	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            r4 = 1;
            r5 = "-v";
            r1[r4] = r5;	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            r4 = 2;
            r5 = "time";
            r1[r4] = r5;	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            r4 = 3;
            r5 = "-b";
            r1[r4] = r5;	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            r4 = 4;
            r5 = "main";
            r1[r4] = r5;	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            r0 = r0.exec(r1);	 Catch:{ IOException -> 0x0409, all -> 0x044a }
        L_0x02e0:
            r5 = new java.io.BufferedInputStream;	 Catch:{ IOException -> 0x0552, all -> 0x0546 }
            r1 = r0.getInputStream();	 Catch:{ IOException -> 0x0552, all -> 0x0546 }
            r4 = 262144; // 0x40000 float:3.67342E-40 double:1.295163E-318;
            r5.<init>(r1, r4);	 Catch:{ IOException -> 0x0552, all -> 0x0546 }
            if (r5 == 0) goto L_0x02fb;
        L_0x02ed:
            r8 = 60;
            android.os.SystemClock.sleep(r8);	 Catch:{ IOException -> 0x0521, all -> 0x051d }
            r1 = r3;
            r4 = r3;
        L_0x02f4:
            r3 = r5.available();	 Catch:{ IOException -> 0x053b, all -> 0x051d }
            if (r3 > 0) goto L_0x03d5;
        L_0x02fa:
            r3 = r4;
        L_0x02fb:
            if (r5 == 0) goto L_0x0300;
        L_0x02fd:
            r5.close();	 Catch:{ IOException -> 0x03ee }
        L_0x0300:
            if (r0 == 0) goto L_0x0305;
        L_0x0302:
            r0.destroy();
        L_0x0305:
            r0 = new java.lang.StringBuilder;	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r0.<init>();	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r1 = "0000";
            r0 = r0.append(r1);	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r1 = "/durable/tmp/";
            r4 = r11.mRecType;	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r5 = r11.mBufType;	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r1 = r11.getRadioLogIndex(r1, r4, r5);	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r1 = java.lang.Integer.toString(r1);	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r0 = r0.append(r1);	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r0 = r0.toString();	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r1 = new java.lang.StringBuilder;	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r1.<init>();	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r4 = r11.mRecType;	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r5 = r11.mBufType;	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r4 = r11.getPrefix(r4, r5);	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r1 = r1.append(r4);	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r4 = r0.length();	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r5 = "0000";
            r5 = r5.length();	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r4 = r4 - r5;
            r5 = r0.length();	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r0 = r0.substring(r4, r5);	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r0 = r1.append(r0);	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r0 = r0.toString();	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r1 = new java.io.FileOutputStream;	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r4 = new java.lang.StringBuilder;	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r4.<init>();	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r5 = "/durable/tmp/";
            r4 = r4.append(r5);	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r0 = r4.append(r0);	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r0 = r0.toString();	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            r1.<init>(r0);	 Catch:{ FileNotFoundException -> 0x0519, IOException -> 0x0543 }
            if (r1 == 0) goto L_0x038d;
        L_0x036c:
            r0 = r11.mWriteSize;	 Catch:{ FileNotFoundException -> 0x0480, IOException -> 0x04bb, all -> 0x053e }
            if (r3 > r0) goto L_0x0475;
        L_0x0370:
            r0 = 0;
            r1.write(r7, r0, r3);	 Catch:{ FileNotFoundException -> 0x0480, IOException -> 0x04bb, all -> 0x053e }
        L_0x0374:
            r0 = r11.mStackTrace;	 Catch:{ FileNotFoundException -> 0x0480, IOException -> 0x04bb, all -> 0x053e }
            if (r0 == 0) goto L_0x038a;
        L_0x0378:
            r0 = "--- Stack Trace Information ---\n";
            r0 = r0.getBytes();	 Catch:{ FileNotFoundException -> 0x0480, IOException -> 0x04bb, all -> 0x053e }
            r1.write(r0);	 Catch:{ FileNotFoundException -> 0x0480, IOException -> 0x04bb, all -> 0x053e }
            r0 = r11.mStackTrace;	 Catch:{ FileNotFoundException -> 0x0480, IOException -> 0x04bb, all -> 0x053e }
            r0 = r0.getBytes();	 Catch:{ FileNotFoundException -> 0x0480, IOException -> 0x04bb, all -> 0x053e }
            r1.write(r0);	 Catch:{ FileNotFoundException -> 0x0480, IOException -> 0x04bb, all -> 0x053e }
        L_0x038a:
            r1.flush();	 Catch:{ FileNotFoundException -> 0x0480, IOException -> 0x04bb, all -> 0x053e }
        L_0x038d:
            if (r1 == 0) goto L_0x002f;
        L_0x038f:
            r1.close();	 Catch:{ IOException -> 0x0394 }
            goto L_0x002f;
        L_0x0394:
            r0 = move-exception;
            r1 = "RILJ";
            r2 = new java.lang.StringBuilder;
            r2.<init>();
            r3 = "radiolog close err:";
            r2 = r2.append(r3);
            r0 = r2.append(r0);
            r0 = r0.toString();
            android.telephony.Rlog.e(r1, r0);
            goto L_0x002f;
        L_0x03af:
            r0 = java.lang.Runtime.getRuntime();	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            r1 = 5;
            r1 = new java.lang.String[r1];	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            r4 = 0;
            r5 = "logcat";
            r1[r4] = r5;	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            r4 = 1;
            r5 = "-v";
            r1[r4] = r5;	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            r4 = 2;
            r5 = "time";
            r1[r4] = r5;	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            r4 = 3;
            r5 = "-b";
            r1[r4] = r5;	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            r4 = 4;
            r5 = "radio";
            r1[r4] = r5;	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            r0 = r0.exec(r1);	 Catch:{ IOException -> 0x0409, all -> 0x044a }
            goto L_0x02e0;
        L_0x03d5:
            r3 = r6 - r4;
            r3 = r5.read(r7, r4, r3);	 Catch:{ IOException -> 0x053b, all -> 0x051d }
            if (r3 <= 0) goto L_0x055c;
        L_0x03dd:
            r3 = r3 + r4;
            r1 = r1 + 1;
            r8 = 30;
            android.os.SystemClock.sleep(r8);	 Catch:{ IOException -> 0x0521, all -> 0x051d }
            r4 = 9;
            if (r1 > r4) goto L_0x02fb;
        L_0x03e9:
            if (r3 >= r6) goto L_0x02fb;
        L_0x03eb:
            r4 = r3;
            goto L_0x02f4;
        L_0x03ee:
            r1 = move-exception;
            r4 = "RILJ";
            r5 = new java.lang.StringBuilder;
            r5.<init>();
            r6 = "logcat close err:";
            r5 = r5.append(r6);
            r1 = r5.append(r1);
            r1 = r1.toString();
            android.telephony.Rlog.e(r4, r1);
            goto L_0x0300;
        L_0x0409:
            r4 = move-exception;
            r0 = r2;
        L_0x040b:
            r1 = r2;
        L_0x040c:
            r5 = "RILJ";
            r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x054c }
            r6.<init>();	 Catch:{ all -> 0x054c }
            r8 = "logcat read err:";
            r6 = r6.append(r8);	 Catch:{ all -> 0x054c }
            r4 = r6.append(r4);	 Catch:{ all -> 0x054c }
            r4 = r4.toString();	 Catch:{ all -> 0x054c }
            android.telephony.Rlog.e(r5, r4);	 Catch:{ all -> 0x054c }
            if (r1 == 0) goto L_0x0429;
        L_0x0426:
            r1.close();	 Catch:{ IOException -> 0x0430 }
        L_0x0429:
            if (r0 == 0) goto L_0x0305;
        L_0x042b:
            r0.destroy();
            goto L_0x0305;
        L_0x0430:
            r1 = move-exception;
            r4 = "RILJ";
            r5 = new java.lang.StringBuilder;
            r5.<init>();
            r6 = "logcat close err:";
            r5 = r5.append(r6);
            r1 = r5.append(r1);
            r1 = r1.toString();
            android.telephony.Rlog.e(r4, r1);
            goto L_0x0429;
        L_0x044a:
            r0 = move-exception;
            r3 = r0;
            r4 = r2;
            r5 = r2;
        L_0x044e:
            r1 = r3;
            r0 = r5;
        L_0x0450:
            if (r4 == 0) goto L_0x0455;
        L_0x0452:
            r4.close();	 Catch:{ IOException -> 0x045b }
        L_0x0455:
            if (r0 == 0) goto L_0x045a;
        L_0x0457:
            r0.destroy();
        L_0x045a:
            throw r1;
        L_0x045b:
            r2 = move-exception;
            r3 = "RILJ";
            r4 = new java.lang.StringBuilder;
            r4.<init>();
            r5 = "logcat close err:";
            r4 = r4.append(r5);
            r2 = r4.append(r2);
            r2 = r2.toString();
            android.telephony.Rlog.e(r3, r2);
            goto L_0x0455;
        L_0x0475:
            r0 = r11.mWriteSize;	 Catch:{ FileNotFoundException -> 0x0480, IOException -> 0x04bb, all -> 0x053e }
            r0 = r3 - r0;
            r2 = r11.mWriteSize;	 Catch:{ FileNotFoundException -> 0x0480, IOException -> 0x04bb, all -> 0x053e }
            r1.write(r7, r0, r2);	 Catch:{ FileNotFoundException -> 0x0480, IOException -> 0x04bb, all -> 0x053e }
            goto L_0x0374;
        L_0x0480:
            r0 = move-exception;
        L_0x0481:
            r2 = "RILJ";
            r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0540 }
            r3.<init>();	 Catch:{ all -> 0x0540 }
            r4 = "radiolog write err:";
            r3 = r3.append(r4);	 Catch:{ all -> 0x0540 }
            r0 = r3.append(r0);	 Catch:{ all -> 0x0540 }
            r0 = r0.toString();	 Catch:{ all -> 0x0540 }
            android.telephony.Rlog.e(r2, r0);	 Catch:{ all -> 0x0540 }
            if (r1 == 0) goto L_0x002f;
        L_0x049b:
            r1.close();	 Catch:{ IOException -> 0x04a0 }
            goto L_0x002f;
        L_0x04a0:
            r0 = move-exception;
            r1 = "RILJ";
            r2 = new java.lang.StringBuilder;
            r2.<init>();
            r3 = "radiolog close err:";
            r2 = r2.append(r3);
            r0 = r2.append(r0);
            r0 = r0.toString();
            android.telephony.Rlog.e(r1, r0);
            goto L_0x002f;
        L_0x04bb:
            r0 = move-exception;
            r2 = r1;
        L_0x04bd:
            r1 = "RILJ";
            r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x04f7 }
            r3.<init>();	 Catch:{ all -> 0x04f7 }
            r4 = "radiolog write err:";
            r3 = r3.append(r4);	 Catch:{ all -> 0x04f7 }
            r0 = r3.append(r0);	 Catch:{ all -> 0x04f7 }
            r0 = r0.toString();	 Catch:{ all -> 0x04f7 }
            android.telephony.Rlog.e(r1, r0);	 Catch:{ all -> 0x04f7 }
            if (r2 == 0) goto L_0x002f;
        L_0x04d7:
            r2.close();	 Catch:{ IOException -> 0x04dc }
            goto L_0x002f;
        L_0x04dc:
            r0 = move-exception;
            r1 = "RILJ";
            r2 = new java.lang.StringBuilder;
            r2.<init>();
            r3 = "radiolog close err:";
            r2 = r2.append(r3);
            r0 = r2.append(r0);
            r0 = r0.toString();
            android.telephony.Rlog.e(r1, r0);
            goto L_0x002f;
        L_0x04f7:
            r0 = move-exception;
        L_0x04f8:
            r1 = r2;
        L_0x04f9:
            if (r1 == 0) goto L_0x04fe;
        L_0x04fb:
            r1.close();	 Catch:{ IOException -> 0x04ff }
        L_0x04fe:
            throw r0;
        L_0x04ff:
            r1 = move-exception;
            r2 = "RILJ";
            r3 = new java.lang.StringBuilder;
            r3.<init>();
            r4 = "radiolog close err:";
            r3 = r3.append(r4);
            r1 = r3.append(r1);
            r1 = r1.toString();
            android.telephony.Rlog.e(r2, r1);
            goto L_0x04fe;
        L_0x0519:
            r0 = move-exception;
            r1 = r2;
            goto L_0x0481;
        L_0x051d:
            r1 = move-exception;
            r4 = r5;
            goto L_0x0450;
        L_0x0521:
            r6 = move-exception;
        L_0x0522:
            r1 = r5;
            r4 = r6;
            goto L_0x040c;
        L_0x0526:
            r0 = move-exception;
            r1 = r2;
            goto L_0x0221;
        L_0x052a:
            r0 = move-exception;
            r1 = r2;
            goto L_0x01c2;
        L_0x052e:
            r0 = move-exception;
            goto L_0x00ed;
        L_0x0531:
            r0 = move-exception;
            goto L_0x013d;
        L_0x0534:
            r0 = move-exception;
            r1 = r2;
            goto L_0x0283;
        L_0x0538:
            r0 = move-exception;
            goto L_0x0283;
        L_0x053b:
            r6 = move-exception;
            r3 = r4;
            goto L_0x0522;
        L_0x053e:
            r0 = move-exception;
            goto L_0x04f9;
        L_0x0540:
            r0 = move-exception;
            r2 = r1;
            goto L_0x04f8;
        L_0x0543:
            r0 = move-exception;
            goto L_0x04bd;
        L_0x0546:
            r1 = move-exception;
            r3 = r1;
            r4 = r2;
            r5 = r0;
            goto L_0x044e;
        L_0x054c:
            r2 = move-exception;
            r3 = r2;
            r4 = r1;
            r5 = r0;
            goto L_0x044e;
        L_0x0552:
            r4 = move-exception;
            goto L_0x040b;
        L_0x0555:
            r0 = move-exception;
            r1 = r2;
            goto L_0x018c;
        L_0x0559:
            r0 = move-exception;
            goto L_0x018c;
        L_0x055c:
            r3 = r4;
            goto L_0x02fb;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.RIL$RILRadioLogWriter.run():void");
        }

        public void setLog(String str) {
            if (TelBrand.IS_DCM) {
                this.mAppendString = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS").format(new Date()) + " : " + str + "\n";
            }
        }

        public void setStackTrace() {
            if (TelBrand.IS_DCM) {
                this.mStackTrace = Log.getStackTraceString(new Throwable());
            }
        }
    }

    class RILReceiver implements Runnable {
        byte[] buffer = new byte[8192];

        RILReceiver() {
        }

        /* JADX WARNING: Removed duplicated region for block: B:52:0x0154 A:{ExcHandler: IOException (r0_16 'e' java.io.IOException), Splitter:B:14:0x0060} */
        /* JADX WARNING: Removed duplicated region for block: B:36:0x00e3 A:{SYNTHETIC, Splitter:B:36:0x00e3} */
        /* JADX WARNING: Removed duplicated region for block: B:45:0x0119 A:{SKIP} */
        /* JADX WARNING: Removed duplicated region for block: B:39:0x00e8 A:{SYNTHETIC, Splitter:B:39:0x00e8} */
        /* JADX WARNING: Failed to process nested try/catch */
        /* JADX WARNING: Missing block: B:52:0x0154, code skipped:
            r0 = move-exception;
     */
        /* JADX WARNING: Missing block: B:54:?, code skipped:
            android.telephony.Rlog.i(com.android.internal.telephony.RIL.RILJ_LOG_TAG, "'" + r4 + "' socket closed", r0);
     */
        /* JADX WARNING: Missing block: B:57:0x0178, code skipped:
            r0 = th;
     */
        /* JADX WARNING: Missing block: B:58:0x0179, code skipped:
            r2 = 0;
     */
        public void run() {
            /*
            r9 = this;
            r3 = 0;
            r8 = 8;
            r1 = 0;
            r0 = r1;
        L_0x0005:
            r2 = com.android.internal.telephony.RIL.this;	 Catch:{ Throwable -> 0x00bf }
            r2 = r2.mInstanceId;	 Catch:{ Throwable -> 0x00bf }
            if (r2 == 0) goto L_0x0019;
        L_0x000d:
            r2 = com.android.internal.telephony.RIL.this;	 Catch:{ Throwable -> 0x00bf }
            r2 = r2.mInstanceId;	 Catch:{ Throwable -> 0x00bf }
            r2 = r2.intValue();	 Catch:{ Throwable -> 0x00bf }
            if (r2 != 0) goto L_0x00ce;
        L_0x0019:
            r2 = com.android.internal.telephony.RIL.SOCKET_NAME_RIL;	 Catch:{ Throwable -> 0x00bf }
            r4 = 0;
            r2 = r2[r4];	 Catch:{ Throwable -> 0x00bf }
            r4 = r2;
        L_0x001f:
            r2 = new android.net.LocalSocket;	 Catch:{ IOException -> 0x00df }
            r2.<init>();	 Catch:{ IOException -> 0x00df }
            r5 = new android.net.LocalSocketAddress;	 Catch:{ IOException -> 0x01a8 }
            r6 = android.net.LocalSocketAddress.Namespace.RESERVED;	 Catch:{ IOException -> 0x01a8 }
            r5.<init>(r4, r6);	 Catch:{ IOException -> 0x01a8 }
            r2.connect(r5);	 Catch:{ IOException -> 0x01a8 }
            r0 = com.android.internal.telephony.RIL.this;	 Catch:{ Throwable -> 0x0175 }
            r0.mSocket = r2;	 Catch:{ Throwable -> 0x0175 }
            r0 = "RILJ";
            r2 = new java.lang.StringBuilder;	 Catch:{ Throwable -> 0x0175 }
            r2.<init>();	 Catch:{ Throwable -> 0x0175 }
            r5 = "(";
            r2 = r2.append(r5);	 Catch:{ Throwable -> 0x0175 }
            r5 = com.android.internal.telephony.RIL.this;	 Catch:{ Throwable -> 0x0175 }
            r5 = r5.mInstanceId;	 Catch:{ Throwable -> 0x0175 }
            r2 = r2.append(r5);	 Catch:{ Throwable -> 0x0175 }
            r5 = ") Connected to '";
            r2 = r2.append(r5);	 Catch:{ Throwable -> 0x0175 }
            r2 = r2.append(r4);	 Catch:{ Throwable -> 0x0175 }
            r5 = "' socket";
            r2 = r2.append(r5);	 Catch:{ Throwable -> 0x0175 }
            r2 = r2.toString();	 Catch:{ Throwable -> 0x0175 }
            android.telephony.Rlog.i(r0, r2);	 Catch:{ Throwable -> 0x0175 }
            r0 = com.android.internal.telephony.RIL.this;	 Catch:{ IOException -> 0x0154, Throwable -> 0x0178 }
            r0 = r0.mSocket;	 Catch:{ IOException -> 0x0154, Throwable -> 0x0178 }
            r0 = r0.getInputStream();	 Catch:{ IOException -> 0x0154, Throwable -> 0x0178 }
            r2 = r1;
        L_0x0069:
            r5 = r9.buffer;	 Catch:{ IOException -> 0x0154, Throwable -> 0x01ae }
            r2 = com.android.internal.telephony.RIL.readRilMessage(r0, r5);	 Catch:{ IOException -> 0x0154, Throwable -> 0x01ae }
            if (r2 >= 0) goto L_0x013c;
        L_0x0071:
            r0 = "RILJ";
            r2 = new java.lang.StringBuilder;	 Catch:{ Throwable -> 0x0175 }
            r2.<init>();	 Catch:{ Throwable -> 0x0175 }
            r5 = "(";
            r2 = r2.append(r5);	 Catch:{ Throwable -> 0x0175 }
            r5 = com.android.internal.telephony.RIL.this;	 Catch:{ Throwable -> 0x0175 }
            r5 = r5.mInstanceId;	 Catch:{ Throwable -> 0x0175 }
            r2 = r2.append(r5);	 Catch:{ Throwable -> 0x0175 }
            r5 = ") Disconnected from '";
            r2 = r2.append(r5);	 Catch:{ Throwable -> 0x0175 }
            r2 = r2.append(r4);	 Catch:{ Throwable -> 0x0175 }
            r4 = "' socket";
            r2 = r2.append(r4);	 Catch:{ Throwable -> 0x0175 }
            r2 = r2.toString();	 Catch:{ Throwable -> 0x0175 }
            android.telephony.Rlog.i(r0, r2);	 Catch:{ Throwable -> 0x0175 }
            r0 = com.android.internal.telephony.RIL.this;	 Catch:{ Throwable -> 0x0175 }
            r2 = com.android.internal.telephony.CommandsInterface.RadioState.RADIO_UNAVAILABLE;	 Catch:{ Throwable -> 0x0175 }
            r0.setRadioState(r2);	 Catch:{ Throwable -> 0x0175 }
            r0 = com.android.internal.telephony.RIL.this;	 Catch:{ IOException -> 0x01ab }
            r0 = r0.mSocket;	 Catch:{ IOException -> 0x01ab }
            r0.close();	 Catch:{ IOException -> 0x01ab }
        L_0x00ad:
            r0 = com.android.internal.telephony.RIL.this;	 Catch:{ Throwable -> 0x0175 }
            r2 = 0;
            r0.mSocket = r2;	 Catch:{ Throwable -> 0x0175 }
            com.android.internal.telephony.RILRequest.resetSerial();	 Catch:{ Throwable -> 0x0175 }
            r0 = com.android.internal.telephony.RIL.this;	 Catch:{ Throwable -> 0x0175 }
            r2 = 1;
            r4 = 0;
            r0.clearRequestList(r2, r4);	 Catch:{ Throwable -> 0x0175 }
            r0 = r1;
            goto L_0x0005;
        L_0x00bf:
            r0 = move-exception;
        L_0x00c0:
            r1 = "RILJ";
            r2 = "Uncaught exception";
            android.telephony.Rlog.e(r1, r2, r0);
            r0 = com.android.internal.telephony.RIL.this;
            r1 = -1;
            r0.notifyRegistrantsRilConnectionChanged(r1);
            return;
        L_0x00ce:
            r2 = com.android.internal.telephony.RIL.SOCKET_NAME_RIL;	 Catch:{ Throwable -> 0x00bf }
            r4 = com.android.internal.telephony.RIL.this;	 Catch:{ Throwable -> 0x00bf }
            r4 = r4.mInstanceId;	 Catch:{ Throwable -> 0x00bf }
            r4 = r4.intValue();	 Catch:{ Throwable -> 0x00bf }
            r2 = r2[r4];	 Catch:{ Throwable -> 0x00bf }
            r4 = r2;
            goto L_0x001f;
        L_0x00df:
            r2 = move-exception;
            r2 = r3;
        L_0x00e1:
            if (r2 == 0) goto L_0x00e6;
        L_0x00e3:
            r2.close();	 Catch:{ IOException -> 0x01a2 }
        L_0x00e6:
            if (r0 != r8) goto L_0x0119;
        L_0x00e8:
            r2 = "RILJ";
            r5 = new java.lang.StringBuilder;	 Catch:{ Throwable -> 0x00bf }
            r5.<init>();	 Catch:{ Throwable -> 0x00bf }
            r6 = "Couldn't find '";
            r5 = r5.append(r6);	 Catch:{ Throwable -> 0x00bf }
            r4 = r5.append(r4);	 Catch:{ Throwable -> 0x00bf }
            r5 = "' socket after ";
            r4 = r4.append(r5);	 Catch:{ Throwable -> 0x00bf }
            r4 = r4.append(r0);	 Catch:{ Throwable -> 0x00bf }
            r5 = " times, continuing to retry silently";
            r4 = r4.append(r5);	 Catch:{ Throwable -> 0x00bf }
            r4 = r4.toString();	 Catch:{ Throwable -> 0x00bf }
            android.telephony.Rlog.e(r2, r4);	 Catch:{ Throwable -> 0x00bf }
        L_0x0110:
            r4 = 4000; // 0xfa0 float:5.605E-42 double:1.9763E-320;
            java.lang.Thread.sleep(r4);	 Catch:{ InterruptedException -> 0x01a5 }
        L_0x0115:
            r0 = r0 + 1;
            goto L_0x0005;
        L_0x0119:
            if (r0 < 0) goto L_0x0110;
        L_0x011b:
            if (r0 >= r8) goto L_0x0110;
        L_0x011d:
            r2 = "RILJ";
            r5 = new java.lang.StringBuilder;	 Catch:{ Throwable -> 0x00bf }
            r5.<init>();	 Catch:{ Throwable -> 0x00bf }
            r6 = "Couldn't find '";
            r5 = r5.append(r6);	 Catch:{ Throwable -> 0x00bf }
            r4 = r5.append(r4);	 Catch:{ Throwable -> 0x00bf }
            r5 = "' socket; retrying after timeout";
            r4 = r4.append(r5);	 Catch:{ Throwable -> 0x00bf }
            r4 = r4.toString();	 Catch:{ Throwable -> 0x00bf }
            android.telephony.Rlog.i(r2, r4);	 Catch:{ Throwable -> 0x00bf }
            goto L_0x0110;
        L_0x013c:
            r5 = android.os.Parcel.obtain();	 Catch:{ IOException -> 0x0154, Throwable -> 0x01ae }
            r6 = r9.buffer;	 Catch:{ IOException -> 0x0154, Throwable -> 0x01ae }
            r7 = 0;
            r5.unmarshall(r6, r7, r2);	 Catch:{ IOException -> 0x0154, Throwable -> 0x01ae }
            r6 = 0;
            r5.setDataPosition(r6);	 Catch:{ IOException -> 0x0154, Throwable -> 0x01ae }
            r6 = com.android.internal.telephony.RIL.this;	 Catch:{ IOException -> 0x0154, Throwable -> 0x01ae }
            r6.processResponse(r5);	 Catch:{ IOException -> 0x0154, Throwable -> 0x01ae }
            r5.recycle();	 Catch:{ IOException -> 0x0154, Throwable -> 0x01ae }
            goto L_0x0069;
        L_0x0154:
            r0 = move-exception;
            r2 = "RILJ";
            r5 = new java.lang.StringBuilder;	 Catch:{ Throwable -> 0x0175 }
            r5.<init>();	 Catch:{ Throwable -> 0x0175 }
            r6 = "'";
            r5 = r5.append(r6);	 Catch:{ Throwable -> 0x0175 }
            r5 = r5.append(r4);	 Catch:{ Throwable -> 0x0175 }
            r6 = "' socket closed";
            r5 = r5.append(r6);	 Catch:{ Throwable -> 0x0175 }
            r5 = r5.toString();	 Catch:{ Throwable -> 0x0175 }
            android.telephony.Rlog.i(r2, r5, r0);	 Catch:{ Throwable -> 0x0175 }
            goto L_0x0071;
        L_0x0175:
            r0 = move-exception;
            goto L_0x00c0;
        L_0x0178:
            r0 = move-exception;
            r2 = r1;
        L_0x017a:
            r5 = "RILJ";
            r6 = new java.lang.StringBuilder;	 Catch:{ Throwable -> 0x0175 }
            r6.<init>();	 Catch:{ Throwable -> 0x0175 }
            r7 = "Uncaught exception read length=";
            r6 = r6.append(r7);	 Catch:{ Throwable -> 0x0175 }
            r2 = r6.append(r2);	 Catch:{ Throwable -> 0x0175 }
            r6 = "Exception:";
            r2 = r2.append(r6);	 Catch:{ Throwable -> 0x0175 }
            r0 = r0.toString();	 Catch:{ Throwable -> 0x0175 }
            r0 = r2.append(r0);	 Catch:{ Throwable -> 0x0175 }
            r0 = r0.toString();	 Catch:{ Throwable -> 0x0175 }
            android.telephony.Rlog.e(r5, r0);	 Catch:{ Throwable -> 0x0175 }
            goto L_0x0071;
        L_0x01a2:
            r2 = move-exception;
            goto L_0x00e6;
        L_0x01a5:
            r2 = move-exception;
            goto L_0x0115;
        L_0x01a8:
            r5 = move-exception;
            goto L_0x00e1;
        L_0x01ab:
            r0 = move-exception;
            goto L_0x00ad;
        L_0x01ae:
            r0 = move-exception;
            goto L_0x017a;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.RIL$RILReceiver.run():void");
        }
    }

    class RILSender extends Handler implements Runnable {
        byte[] dataLength = new byte[4];

        public RILSender(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message message) {
            int i = 0;
            RILRequest rILRequest = (RILRequest) message.obj;
            switch (message.what) {
                case 1:
                    try {
                        LocalSocket localSocket = RIL.this.mSocket;
                        if (localSocket == null) {
                            rILRequest.onError(1, null);
                            rILRequest.release();
                            RIL.this.decrementWakeLock();
                            return;
                        }
                        synchronized (RIL.this.mRequestList) {
                            RIL.this.mRequestList.append(rILRequest.mSerial, rILRequest);
                        }
                        byte[] marshall = rILRequest.mParcel.marshall();
                        rILRequest.mParcel.recycle();
                        rILRequest.mParcel = null;
                        if (marshall.length > 8192) {
                            throw new RuntimeException("Parcel larger than max bytes allowed! " + marshall.length);
                        }
                        byte[] bArr = this.dataLength;
                        this.dataLength[1] = (byte) 0;
                        bArr[0] = (byte) 0;
                        this.dataLength[2] = (byte) ((marshall.length >> 8) & 255);
                        this.dataLength[3] = (byte) (marshall.length & 255);
                        localSocket.getOutputStream().write(this.dataLength);
                        localSocket.getOutputStream().write(marshall);
                        return;
                    } catch (IOException e) {
                        Rlog.e(RIL.RILJ_LOG_TAG, "IOException", e);
                        if (RIL.this.findAndRemoveRequestFromList(rILRequest.mSerial) != null) {
                            rILRequest.onError(1, null);
                            rILRequest.release();
                            RIL.this.decrementWakeLock();
                            return;
                        }
                        return;
                    } catch (RuntimeException e2) {
                        Rlog.e(RIL.RILJ_LOG_TAG, "Uncaught exception ", e2);
                        if (RIL.this.findAndRemoveRequestFromList(rILRequest.mSerial) != null) {
                            rILRequest.onError(2, null);
                            rILRequest.release();
                            RIL.this.decrementWakeLock();
                            return;
                        }
                        return;
                    }
                case 2:
                    synchronized (RIL.this.mRequestList) {
                        if (RIL.this.clearWakeLock()) {
                            int size = RIL.this.mRequestList.size();
                            Rlog.d(RIL.RILJ_LOG_TAG, "WAKE_LOCK_TIMEOUT  mRequestList=" + size);
                            while (i < size) {
                                rILRequest = (RILRequest) RIL.this.mRequestList.valueAt(i);
                                Rlog.d(RIL.RILJ_LOG_TAG, i + ": [" + rILRequest.mSerial + "] " + RIL.requestToString(rILRequest.mRequest));
                                i++;
                            }
                        }
                    }
                    return;
                case 4:
                    if (TelBrand.IS_DCM) {
                        RIL.this.send(rILRequest);
                        return;
                    }
                    return;
                default:
                    return;
            }
        }

        public void run() {
        }
    }

    public final class UnsolOemHookBuffer {
        private byte[] mData;
        private int mRilInstance;

        public UnsolOemHookBuffer(int i, byte[] bArr) {
            this.mRilInstance = i;
            this.mData = bArr;
        }

        public int getRilInstance() {
            return this.mRilInstance;
        }

        public byte[] getUnsolOemHookBuffer() {
            return this.mData;
        }
    }

    public RIL(Context context, int i, int i2) {
        this(context, i, i2, null);
    }

    public RIL(Context context, int i, int i2, Integer num) {
        super(context);
        this.OEMHOOK_UNSOL_SIM_REFRESH = 525304;
        this.OEMHOOK_UNSOL_WWAN_IWLAN_COEXIST = 525306;
        this.mHeaderSize = OEM_IDENTIFIER.length() + 8;
        this.QCRIL_EVT_HOOK_UNSOL_MODEM_CAPABILITY = 525308;
        this.mDefaultDisplayState = 0;
        this.mRequestList = new SparseArray();
        this.mTestingEmergencyCall = new AtomicBoolean(false);
        this.mLimitedService = false;
        this.mRILConnected_UpdateOemDataSettings_OnceSkip = true;
        this.mUpdateOemDataSettings_MobileData = false;
        this.mUpdateOemDataSettings_DataRoaming = false;
        this.mRILConnected_SetInitialAttachApn_OnceSkip = true;
        this.mSetInitialAttachApn_Apn = null;
        this.mSetInitialAttachApn_Protocol = null;
        this.mSetInitialAttachApn_AuthType = -1;
        this.mSetInitialAttachApn_Username = null;
        this.mSetInitialAttachApn_Password = null;
        this.mSetLimitationByChameleon = false;
        this.mLimitationByChameleon = true;
        this.mIntentReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("android.intent.action.SERVICE_STATE")) {
                    RIL.this.upDomesticInService(context, intent);
                } else if (TelBrand.IS_DCM && intent.getAction().equals("android.intent.action.ACTION_SHUTDOWN")) {
                    Thread thread = new Thread(new RILRadioLogWriter(1, 1));
                    if (thread != null) {
                        thread.start();
                    }
                } else {
                    Rlog.w(RIL.RILJ_LOG_TAG, "RIL received unexpected Intent: " + intent.getAction());
                }
            }
        };
        this.mDisplayListener = new DisplayListener() {
            public void onDisplayAdded(int i) {
            }

            public void onDisplayChanged(int i) {
                if (i == 0) {
                    RIL.this.updateScreenState();
                }
            }

            public void onDisplayRemoved(int i) {
            }
        };
        riljLog("RIL(context, preferredNetworkType=" + i + " cdmaSubscription=" + i2 + ")");
        this.mContext = context;
        this.mCdmaSubscription = i2;
        this.mPreferredNetworkType = i;
        this.mPhoneType = 0;
        this.mInstanceId = num;
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, RILJ_LOG_TAG);
        this.mWakeLock.setReferenceCounted(false);
        this.mWakeLockTimeout = SystemProperties.getInt("ro.ril.wake_lock_timeout", 60000);
        this.mWakeLockCount = 0;
        this.mSenderThread = new HandlerThread("RILSender" + this.mInstanceId);
        this.mSenderThread.start();
        this.mSender = new RILSender(this.mSenderThread.getLooper());
        if (((ConnectivityManager) context.getSystemService("connectivity")).isNetworkSupported(0)) {
            riljLog("Starting RILReceiver" + this.mInstanceId);
            this.mReceiver = new RILReceiver();
            this.mReceiverThread = new Thread(this.mReceiver, "RILReceiver" + this.mInstanceId);
            this.mReceiverThread.start();
            DisplayManager displayManager = (DisplayManager) context.getSystemService("display");
            this.mDefaultDisplay = displayManager.getDisplay(0);
            displayManager.registerDisplayListener(this.mDisplayListener, null);
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.intent.action.SERVICE_STATE");
            if (TelBrand.IS_DCM && LOG_SD) {
                intentFilter.addAction("android.intent.action.ACTION_SHUTDOWN");
            }
            context.registerReceiver(this.mIntentReceiver, intentFilter);
        } else {
            riljLog("Not starting RILReceiver: wifi-only");
        }
        TelephonyDevController.getInstance();
        TelephonyDevController.registerRIL(this);
        this.mLimitedService = this.mLimitationByChameleon;
    }

    private void acquireWakeLock() {
        synchronized (this.mWakeLock) {
            this.mWakeLock.acquire();
            this.mWakeLockCount++;
            this.mSender.removeMessages(2);
            this.mSender.sendMessageDelayed(this.mSender.obtainMessage(2), (long) this.mWakeLockTimeout);
        }
    }

    private void clearRequestList(int i, boolean z) {
        synchronized (this.mRequestList) {
            int size = this.mRequestList.size();
            if (z) {
                Rlog.d(RILJ_LOG_TAG, "clearRequestList  mWakeLockCount=" + this.mWakeLockCount + " mRequestList=" + size);
            }
            for (int i2 = 0; i2 < size; i2++) {
                RILRequest rILRequest = (RILRequest) this.mRequestList.valueAt(i2);
                if (z) {
                    Rlog.d(RILJ_LOG_TAG, i2 + ": [" + rILRequest.mSerial + "] " + requestToString(rILRequest.mRequest));
                }
                rILRequest.onError(i, null);
                rILRequest.release();
                decrementWakeLock();
            }
            this.mRequestList.clear();
        }
    }

    private boolean clearWakeLock() {
        synchronized (this.mWakeLock) {
            if (this.mWakeLockCount != 0 || this.mWakeLock.isHeld()) {
                Rlog.d(RILJ_LOG_TAG, "NOTE: mWakeLockCount is " + this.mWakeLockCount + "at time of clearing");
                this.mWakeLockCount = 0;
                this.mWakeLock.release();
                this.mSender.removeMessages(2);
                return true;
            }
            return false;
        }
    }

    private void constructCdmaSendSmsRilRequest(RILRequest rILRequest, byte[] bArr) {
        int i = 0;
        DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(bArr));
        try {
            byte b;
            rILRequest.mParcel.writeInt(dataInputStream.readInt());
            rILRequest.mParcel.writeByte((byte) dataInputStream.readInt());
            rILRequest.mParcel.writeInt(dataInputStream.readInt());
            rILRequest.mParcel.writeInt(dataInputStream.read());
            rILRequest.mParcel.writeInt(dataInputStream.read());
            rILRequest.mParcel.writeInt(dataInputStream.read());
            rILRequest.mParcel.writeInt(dataInputStream.read());
            byte read = (byte) dataInputStream.read();
            rILRequest.mParcel.writeByte((byte) read);
            for (b = (byte) 0; b < read; b++) {
                rILRequest.mParcel.writeByte(dataInputStream.readByte());
            }
            rILRequest.mParcel.writeInt(dataInputStream.read());
            rILRequest.mParcel.writeByte((byte) dataInputStream.read());
            read = (byte) dataInputStream.read();
            rILRequest.mParcel.writeByte((byte) read);
            for (b = (byte) 0; b < read; b++) {
                rILRequest.mParcel.writeByte(dataInputStream.readByte());
            }
            int read2 = dataInputStream.read();
            rILRequest.mParcel.writeInt(read2);
            while (i < read2) {
                rILRequest.mParcel.writeByte(dataInputStream.readByte());
                i++;
            }
        } catch (IOException e) {
            riljLog("sendSmsCdma: conversion from input stream to object failed: " + e);
        }
    }

    private void constructCdmaWriteSmsRilRequest(RILRequest rILRequest, byte[] bArr) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bArr);
        DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);
        try {
            byte b;
            int readInt = dataInputStream.readInt();
            rILRequest.mParcel.writeInt(readInt);
            byte readInt2 = (byte) dataInputStream.readInt();
            rILRequest.mParcel.writeByte(readInt2);
            int readInt3 = dataInputStream.readInt();
            rILRequest.mParcel.writeInt(readInt3);
            byte readByte = dataInputStream.readByte();
            rILRequest.mParcel.writeInt(readByte);
            byte readByte2 = dataInputStream.readByte();
            rILRequest.mParcel.writeInt(readByte2);
            byte readByte3 = dataInputStream.readByte();
            rILRequest.mParcel.writeInt(readByte3);
            byte readByte4 = dataInputStream.readByte();
            rILRequest.mParcel.writeInt(readByte4);
            byte readByte5 = dataInputStream.readByte();
            rILRequest.mParcel.writeByte((byte) readByte5);
            for (b = (byte) 0; b < readByte5; b++) {
                rILRequest.mParcel.writeByte(dataInputStream.readByte());
            }
            byte readByte6 = dataInputStream.readByte();
            rILRequest.mParcel.writeInt(readByte6);
            byte readByte7 = dataInputStream.readByte();
            rILRequest.mParcel.writeByte(readByte7);
            byte readByte8 = dataInputStream.readByte();
            rILRequest.mParcel.writeByte((byte) readByte8);
            for (b = (byte) 0; b < readByte8; b++) {
                rILRequest.mParcel.writeByte(dataInputStream.readByte());
            }
            int readByte9 = dataInputStream.readByte() & 255;
            rILRequest.mParcel.writeInt(readByte9);
            for (int i = 0; i < readByte9; i++) {
                rILRequest.mParcel.writeByte(dataInputStream.readByte());
            }
            riljLog(" teleServiceId=" + readInt + " servicePresent=" + readInt2 + " serviceCategory=" + readInt3 + " address_digit_mode=" + readByte + " address_nbr_mode=" + readByte2 + " address_ton=" + readByte3 + " address_nbr_plan=" + readByte4 + " address_nbr_of_digits=" + readByte5 + " subaddressType=" + readByte6 + " subaddr_odd= " + readByte7 + " subaddr_nbr_of_digits=" + readByte8 + " bearerDataLength=" + readByte9);
            if (byteArrayInputStream != null) {
                try {
                    byteArrayInputStream.close();
                } catch (IOException e) {
                    riljLog("sendSmsCdma: close input stream exception" + e);
                    return;
                }
            }
            if (dataInputStream != null) {
                dataInputStream.close();
            }
        } catch (IOException e2) {
            riljLog("sendSmsCdma: conversion from input stream to object failed: " + e2);
            if (byteArrayInputStream != null) {
                try {
                    byteArrayInputStream.close();
                } catch (IOException e22) {
                    riljLog("sendSmsCdma: close input stream exception" + e22);
                    return;
                }
            }
            if (dataInputStream != null) {
                dataInputStream.close();
            }
        } catch (Throwable th) {
            if (byteArrayInputStream != null) {
                try {
                    byteArrayInputStream.close();
                } catch (IOException e3) {
                    riljLog("sendSmsCdma: close input stream exception" + e3);
                }
            }
            if (dataInputStream != null) {
                dataInputStream.close();
            }
        }
    }

    private void constructGsmSendSmsRilRequest(RILRequest rILRequest, String str, String str2) {
        rILRequest.mParcel.writeInt(2);
        rILRequest.mParcel.writeString(str);
        rILRequest.mParcel.writeString(str2);
    }

    private void decrementWakeLock() {
        synchronized (this.mWakeLock) {
            if (this.mWakeLockCount > 1) {
                this.mWakeLockCount--;
            } else {
                this.mWakeLockCount = 0;
                this.mWakeLock.release();
                this.mSender.removeMessages(2);
            }
        }
    }

    private RILRequest findAndRemoveRequestFromList(int i) {
        RILRequest rILRequest;
        synchronized (this.mRequestList) {
            rILRequest = (RILRequest) this.mRequestList.get(i);
            if (rILRequest != null) {
                this.mRequestList.remove(i);
            }
        }
        return rILRequest;
    }

    private DataCallResponse getDataCallResponse(Parcel parcel, int i) {
        DataCallResponse dataCallResponse = new DataCallResponse();
        dataCallResponse.version = i;
        String readString;
        if (i < 5) {
            dataCallResponse.cid = parcel.readInt();
            dataCallResponse.active = parcel.readInt();
            dataCallResponse.type = parcel.readString();
            readString = parcel.readString();
            if (!TextUtils.isEmpty(readString)) {
                dataCallResponse.addresses = readString.split(" ");
            }
        } else {
            dataCallResponse.status = parcel.readInt();
            dataCallResponse.suggestedRetryTime = parcel.readInt();
            dataCallResponse.cid = parcel.readInt();
            dataCallResponse.active = parcel.readInt();
            dataCallResponse.type = parcel.readString();
            dataCallResponse.ifname = parcel.readString();
            if (dataCallResponse.status == DcFailCause.NONE.getErrorCode() && TextUtils.isEmpty(dataCallResponse.ifname)) {
                throw new RuntimeException("getDataCallResponse, no ifname");
            }
            readString = parcel.readString();
            if (!TextUtils.isEmpty(readString)) {
                dataCallResponse.addresses = readString.split(" ");
            }
            readString = parcel.readString();
            if (!TextUtils.isEmpty(readString)) {
                dataCallResponse.dnses = readString.split(" ");
            }
            readString = parcel.readString();
            if (!TextUtils.isEmpty(readString)) {
                dataCallResponse.gateways = readString.split(" ");
            }
            if (i >= 10) {
                readString = parcel.readString();
                if (!TextUtils.isEmpty(readString)) {
                    dataCallResponse.pcscf = readString.split(" ");
                }
                dataCallResponse.mtu = parcel.readInt();
            }
        }
        return dataCallResponse;
    }

    private RadioState getRadioStateFromInt(int i) {
        switch (i) {
            case 0:
                return RadioState.RADIO_OFF;
            case 1:
                return RadioState.RADIO_UNAVAILABLE;
            case 10:
                return RadioState.RADIO_ON;
            default:
                throw new RuntimeException("Unrecognized RIL_RadioState: " + i);
        }
    }

    private void iccTransmitApduHelper(int i, int i2, int i3, int i4, int i5, int i6, int i7, String str, Message message) {
        RILRequest obtain = RILRequest.obtain(i, message);
        obtain.mParcel.writeInt(i2);
        obtain.mParcel.writeInt(i3);
        obtain.mParcel.writeInt(i4);
        obtain.mParcel.writeInt(i5);
        obtain.mParcel.writeInt(i6);
        obtain.mParcel.writeInt(i7);
        obtain.mParcel.writeString(str);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public static boolean isDomesticInService() {
        return SystemProperties.getBoolean("gsm.domesticinservice", false);
    }

    private boolean isQcUnsolOemHookResp(ByteBuffer byteBuffer) {
        if (byteBuffer.capacity() < this.mHeaderSize) {
            Rlog.d(RILJ_LOG_TAG, "RIL_UNSOL_OEM_HOOK_RAW data size is " + byteBuffer.capacity());
        } else {
            byte[] bArr = new byte[OEM_IDENTIFIER.length()];
            byteBuffer.get(bArr);
            String str = new String(bArr);
            Rlog.d(RILJ_LOG_TAG, "Oem ID in RIL_UNSOL_OEM_HOOK_RAW is " + str);
            if (str.equals(OEM_IDENTIFIER)) {
                return true;
            }
        }
        return false;
    }

    private void notifyRegistrantsCdmaInfoRec(CdmaInformationRecords cdmaInformationRecords) {
        if (cdmaInformationRecords.record instanceof CdmaDisplayInfoRec) {
            if (this.mDisplayInfoRegistrants != null) {
                unsljLogRet(1027, cdmaInformationRecords.record);
                this.mDisplayInfoRegistrants.notifyRegistrants(new AsyncResult(null, cdmaInformationRecords.record, null));
            }
        } else if (cdmaInformationRecords.record instanceof CdmaSignalInfoRec) {
            if (this.mSignalInfoRegistrants != null) {
                unsljLogRet(1027, cdmaInformationRecords.record);
                this.mSignalInfoRegistrants.notifyRegistrants(new AsyncResult(null, cdmaInformationRecords.record, null));
            }
        } else if (cdmaInformationRecords.record instanceof CdmaNumberInfoRec) {
            if (this.mNumberInfoRegistrants != null) {
                unsljLogRet(1027, cdmaInformationRecords.record);
                this.mNumberInfoRegistrants.notifyRegistrants(new AsyncResult(null, cdmaInformationRecords.record, null));
            }
        } else if (cdmaInformationRecords.record instanceof CdmaRedirectingNumberInfoRec) {
            if (this.mRedirNumInfoRegistrants != null) {
                unsljLogRet(1027, cdmaInformationRecords.record);
                this.mRedirNumInfoRegistrants.notifyRegistrants(new AsyncResult(null, cdmaInformationRecords.record, null));
            }
        } else if (cdmaInformationRecords.record instanceof CdmaLineControlInfoRec) {
            if (this.mLineControlInfoRegistrants != null) {
                unsljLogRet(1027, cdmaInformationRecords.record);
                this.mLineControlInfoRegistrants.notifyRegistrants(new AsyncResult(null, cdmaInformationRecords.record, null));
            }
        } else if (cdmaInformationRecords.record instanceof CdmaT53ClirInfoRec) {
            if (this.mT53ClirInfoRegistrants != null) {
                unsljLogRet(1027, cdmaInformationRecords.record);
                this.mT53ClirInfoRegistrants.notifyRegistrants(new AsyncResult(null, cdmaInformationRecords.record, null));
            }
        } else if ((cdmaInformationRecords.record instanceof CdmaT53AudioControlInfoRec) && this.mT53AudCntrlInfoRegistrants != null) {
            unsljLogRet(1027, cdmaInformationRecords.record);
            this.mT53AudCntrlInfoRegistrants.notifyRegistrants(new AsyncResult(null, cdmaInformationRecords.record, null));
        }
    }

    private void notifyRegistrantsRilConnectionChanged(int i) {
        this.mRilVersion = i;
        if (this.mRilConnectedRegistrants != null) {
            this.mRilConnectedRegistrants.notifyRegistrants(new AsyncResult(null, new Integer(i), null));
        }
    }

    private void processResponse(Parcel parcel) {
        int readInt = parcel.readInt();
        if (readInt == 1) {
            processUnsolicited(parcel);
        } else if (readInt == 0) {
            RILRequest processSolicited = processSolicited(parcel);
            if (processSolicited != null) {
                processSolicited.release();
                decrementWakeLock();
            }
        }
    }

    private RILRequest processSolicited(Parcel parcel) {
        RILRequest rILRequest = null;
        int readInt = parcel.readInt();
        int readInt2 = parcel.readInt();
        RILRequest findAndRemoveRequestFromList = findAndRemoveRequestFromList(readInt);
        if (findAndRemoveRequestFromList == null) {
            Rlog.w(RILJ_LOG_TAG, "Unexpected solicited response! sn: " + readInt + " error: " + readInt2);
        } else {
            Object responseIccCardStatus;
            Thread thread;
            if (readInt2 == 0 || parcel.dataAvail() > 0) {
                try {
                    switch (findAndRemoveRequestFromList.mRequest) {
                        case 1:
                            responseIccCardStatus = responseIccCardStatus(parcel);
                            break;
                        case 2:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 3:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 4:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 5:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 6:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 7:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 8:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 9:
                            responseIccCardStatus = responseCallList(parcel);
                            break;
                        case 10:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 11:
                            responseIccCardStatus = responseString(parcel);
                            break;
                        case 12:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 13:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 14:
                            if (this.mTestingEmergencyCall.getAndSet(false) && this.mEmergencyCallbackModeRegistrant != null) {
                                riljLog("testing emergency call, notify ECM Registrants");
                                this.mEmergencyCallbackModeRegistrant.notifyRegistrant();
                            }
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 15:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 16:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 17:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 18:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 19:
                            responseIccCardStatus = responseSignalStrength(parcel);
                            break;
                        case 20:
                            responseIccCardStatus = responseStrings(parcel);
                            break;
                        case 21:
                            responseIccCardStatus = responseStrings(parcel);
                            break;
                        case 22:
                            responseIccCardStatus = responseStrings(parcel);
                            break;
                        case 23:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /*24*/:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 25:
                            responseIccCardStatus = responseSMS(parcel);
                            break;
                        case 26:
                            responseIccCardStatus = responseSMS(parcel);
                            break;
                        case OEM_RIL_RDE_Data.RDE_NV_OTKSL_I /*27*/:
                            responseIccCardStatus = responseSetupDataCall(parcel);
                            break;
                        case 28:
                            responseIccCardStatus = responseICC_IO(parcel);
                            break;
                        case IccRecords.EVENT_REFRESH_OEM /*29*/:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 30:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 31:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 32:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 33:
                            responseIccCardStatus = responseCallForward(parcel);
                            break;
                        case 34:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 35:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 36:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 37:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case RadioNVItems.RIL_NV_MIP_PROFILE_HA_SPI /*38*/:
                            responseIccCardStatus = responseString(parcel);
                            break;
                        case 39:
                            responseIccCardStatus = responseString(parcel);
                            break;
                        case 40:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 41:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 42:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case OEM_RIL_RDE_Data.RDE_NV_EHRPD_ENABLED_I /*43*/:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case CallFailCause.CHANNEL_NOT_AVAIL /*44*/:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case OEM_RIL_RDE_Data.RDE_NV_CDMA_SO73_ENABLED_I /*45*/:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case OEM_RIL_RDE_Data.RDE_NV_DS_MIP_SS_USER_PROF_I /*46*/:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 47:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case OEM_RIL_RDE_Data.RDE_NV_CDMA_SO68_ENABLED_I /*48*/:
                            responseIccCardStatus = responseOperatorInfos(parcel);
                            break;
                        case 49:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case OEM_RIL_RDE_Data.RDE_NV_MOB_TERM_HOME_I /*50*/:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 51:
                            responseIccCardStatus = responseString(parcel);
                            break;
                        case 52:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 53:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case RadioNVItems.RIL_NV_CDMA_SO68 /*54*/:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 55:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 56:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case RadioNVItems.RIL_NV_CDMA_1X_ADVANCED_ENABLED /*57*/:
                            responseIccCardStatus = responseDataCallList(parcel);
                            break;
                        case 58:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case RadioNVItems.RIL_NV_CDMA_EHRPD_FORCED /*59*/:
                            responseIccCardStatus = responseRaw(parcel);
                            break;
                        case 60:
                            responseIccCardStatus = responseStrings(parcel);
                            break;
                        case 61:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 62:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case SignalToneUtil.IS95_CONST_IR_SIG_TONE_NO_TONE /*63*/:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 64:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 65:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 66:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 67:
                            responseIccCardStatus = responseString(parcel);
                            break;
                        case 68:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 69:
                            responseIccCardStatus = responseString(parcel);
                            break;
                        case 70:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_25 /*71*/:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 72:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_41 /*73*/:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_25 /*74*/:
                            responseIccCardStatus = responseGetPreferredNetworkType(parcel);
                            break;
                        case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_26 /*75*/:
                            responseIccCardStatus = responseCellList(parcel);
                            break;
                        case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_41 /*76*/:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_25 /*77*/:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_26 /*78*/:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_41 /*79*/:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case RadioNVItems.RIL_NV_LTE_NEXT_SCAN /*80*/:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case RadioNVItems.RIL_NV_LTE_BSR_TIMER /*81*/:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case RadioNVItems.RIL_NV_LTE_BSR_MAX_TIME /*82*/:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 83:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 84:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 85:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 86:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 87:
                            responseIccCardStatus = responseSMS(parcel);
                            break;
                        case 88:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 89:
                            responseIccCardStatus = responseGmsBroadcastConfig(parcel);
                            break;
                        case 90:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 91:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 92:
                            responseIccCardStatus = responseCdmaBroadcastConfig(parcel);
                            break;
                        case 93:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 94:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 95:
                            responseIccCardStatus = responseStrings(parcel);
                            break;
                        case CommandsInterface.CDMA_SMS_FAIL_CAUSE_ENCODING_PROBLEM /*96*/:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 97:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 98:
                            responseIccCardStatus = responseStrings(parcel);
                            break;
                        case 99:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case IccRecords.EVENT_GET_ICC_RECORD_DONE /*100*/:
                            responseIccCardStatus = responseString(parcel);
                            break;
                        case 101:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 102:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 103:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 104:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 105:
                            responseIccCardStatus = responseString(parcel);
                            break;
                        case 106:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 107:
                            responseIccCardStatus = responseICC_IO(parcel);
                            break;
                        case OEM_RIL_RDE_Data.RDE_NV_ROAMING_LIST_683_I /*108*/:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 109:
                            responseIccCardStatus = responseCellInfoList(parcel);
                            break;
                        case 110:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 111:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 112:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 113:
                            responseIccCardStatus = responseSMS(parcel);
                            break;
                        case 114:
                            responseIccCardStatus = responseICC_IO(parcel);
                            break;
                        case 115:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 116:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 117:
                            responseIccCardStatus = responseICC_IO(parcel);
                            break;
                        case 118:
                            responseIccCardStatus = responseString(parcel);
                            break;
                        case 119:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 120:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 121:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 122:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 123:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 124:
                            responseIccCardStatus = responseHardwareConfig(parcel);
                            break;
                        case 125:
                            responseIccCardStatus = responseICC_IOBase64(parcel);
                            break;
                        case 128:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 129:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 132:
                            responseIccCardStatus = responseGetDataCallProfile(parcel);
                            break;
                        case 133:
                            responseIccCardStatus = responseString(parcel);
                            break;
                        case 134:
                            responseIccCardStatus = responseICC_IO(parcel);
                            break;
                        case 135:
                            responseIccCardStatus = responseInts(parcel);
                            break;
                        case 136:
                            responseIccCardStatus = responseVoid(parcel);
                            break;
                        case 137:
                            responseIccCardStatus = responseICC_IO(parcel);
                            break;
                        default:
                            throw new RuntimeException("Unrecognized solicited response: " + findAndRemoveRequestFromList.mRequest);
                    }
                } catch (Throwable th) {
                    Rlog.w(RILJ_LOG_TAG, findAndRemoveRequestFromList.serialString() + "< " + requestToString(findAndRemoveRequestFromList.mRequest) + " exception, possible invalid RIL response", th);
                    if (findAndRemoveRequestFromList.mResult != null) {
                        AsyncResult.forMessage(findAndRemoveRequestFromList.mResult, null, th);
                        findAndRemoveRequestFromList.mResult.sendToTarget();
                        return findAndRemoveRequestFromList;
                    }
                }
            }
            responseIccCardStatus = null;
            if (findAndRemoveRequestFromList.mRequest == 129) {
                riljLog("Response to RIL_REQUEST_SHUTDOWN received. Error is " + readInt2 + " Setting Radio State to Unavailable regardless of error.");
                setRadioState(RadioState.RADIO_UNAVAILABLE);
            }
            switch (findAndRemoveRequestFromList.mRequest) {
                case 3:
                case 5:
                    if (this.mIccStatusChangedRegistrants != null) {
                        riljLog("ON enter sim puk fakeSimStatusChanged: reg count=" + this.mIccStatusChangedRegistrants.size());
                        this.mIccStatusChangedRegistrants.notifyRegistrants();
                        break;
                    }
                    break;
                case OEM_RIL_RDE_Data.RDE_NV_DS_MIP_SS_USER_PROF_I /*46*/:
                case 47:
                    if (TelBrand.IS_DCM && LOG_NS) {
                        RILRadioLogWriter rILRadioLogWriter = new RILRadioLogWriter(2, 2);
                        rILRadioLogWriter.setLog(findAndRemoveRequestFromList.serialString() + "< " + requestToString(findAndRemoveRequestFromList.mRequest) + " error: " + CommandException.fromRilErrno(readInt2));
                        Thread thread2 = new Thread(rILRadioLogWriter);
                        if (thread2 != null) {
                            thread2.start();
                            break;
                        }
                    }
                    break;
                case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_41 /*73*/:
                    if (TelBrand.IS_DCM && LOG_NW) {
                        thread = new Thread(new RILRadioLogWriter(0, 1));
                        if (thread != null) {
                            thread.start();
                            break;
                        }
                    }
                    break;
            }
            if (readInt2 != 0) {
                switch (findAndRemoveRequestFromList.mRequest) {
                    case 2:
                    case 4:
                    case 6:
                    case 7:
                    case OEM_RIL_RDE_Data.RDE_NV_EHRPD_ENABLED_I /*43*/:
                        if (this.mIccStatusChangedRegistrants != null) {
                            riljLog("ON some errors fakeSimStatusChanged: reg count=" + this.mIccStatusChangedRegistrants.size());
                            this.mIccStatusChangedRegistrants.notifyRegistrants();
                            break;
                        }
                        break;
                    case OEM_RIL_RDE_Data.RDE_NV_DS_MIP_SS_USER_PROF_I /*46*/:
                    case 47:
                        if (TelBrand.IS_DCM && LOG_NS) {
                            RILRadioLogWriter rILRadioLogWriter2 = new RILRadioLogWriter(2, 2);
                            rILRadioLogWriter2.setLog(findAndRemoveRequestFromList.serialString() + "< " + requestToString(findAndRemoveRequestFromList.mRequest) + " error: " + CommandException.fromRilErrno(readInt2));
                            thread = new Thread(rILRadioLogWriter2);
                            if (thread != null) {
                                thread.start();
                                break;
                            }
                        }
                        break;
                    case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_41 /*73*/:
                        if (TelBrand.IS_DCM && LOG_NW) {
                            Thread thread3 = new Thread(new RILRadioLogWriter(0, 1));
                            if (thread3 != null) {
                                thread3.start();
                                break;
                            }
                        }
                        break;
                }
                findAndRemoveRequestFromList.onError(readInt2, responseIccCardStatus);
                return findAndRemoveRequestFromList;
            }
            riljLog(findAndRemoveRequestFromList.serialString() + "< " + requestToString(findAndRemoveRequestFromList.mRequest) + " " + retToString(findAndRemoveRequestFromList.mRequest, responseIccCardStatus));
            if (findAndRemoveRequestFromList.mResult != null) {
                AsyncResult.forMessage(findAndRemoveRequestFromList.mResult, responseIccCardStatus, null);
                findAndRemoveRequestFromList.mResult.sendToTarget();
                return findAndRemoveRequestFromList;
            }
            rILRequest = findAndRemoveRequestFromList;
        }
        return rILRequest;
    }

    private void processUnsolOemhookResponse(ByteBuffer byteBuffer) {
        int i = byteBuffer.getInt();
        Rlog.d(RILJ_LOG_TAG, "Response ID in RIL_UNSOL_OEM_HOOK_RAW is " + i);
        int i2 = byteBuffer.getInt();
        if (i2 < 0) {
            Rlog.e(RILJ_LOG_TAG, "Response Size is Invalid " + i2);
            return;
        }
        byte[] bArr = new byte[i2];
        if (byteBuffer.remaining() == i2) {
            byteBuffer.get(bArr, 0, i2);
            switch (i) {
                case 525304:
                    notifySimRefresh(bArr);
                    return;
                case 525306:
                    notifyWwanIwlanCoexist(bArr);
                    return;
                case 525308:
                    Rlog.d(RILJ_LOG_TAG, "QCRIL_EVT_HOOK_UNSOL_MODEM_CAPABILITY = mInstanceId" + this.mInstanceId);
                    notifyModemCap(bArr, this.mInstanceId);
                    return;
                case 592825:
                    if (TelBrand.IS_SBM) {
                        notifyOemSignalStrength(bArr);
                        return;
                    }
                    return;
                case 592826:
                    notifyLteBandInfo(bArr);
                    return;
                case 592827:
                    if (TelBrand.IS_SBM) {
                        notifySpeechCodec(bArr);
                        return;
                    }
                    return;
                default:
                    Rlog.d(RILJ_LOG_TAG, "Response ID " + i + " is not served in this process.");
                    return;
            }
        }
        Rlog.e(RILJ_LOG_TAG, "Response Size(" + i2 + ") doesnot match remaining bytes(" + byteBuffer.remaining() + ") in the buffer. So, don't process further");
    }

    private void processUnsolicited(Parcel parcel) {
        Object responseVoid;
        int readInt = parcel.readInt();
        switch (readInt) {
            case 1000:
                responseVoid = responseVoid(parcel);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_DROP /*1001*/:
                responseVoid = responseVoid(parcel);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_INTERCEPT /*1002*/:
                responseVoid = responseVoid(parcel);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_REORDER /*1003*/:
                responseVoid = responseString(parcel);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_SO_REJECT /*1004*/:
                responseVoid = responseString(parcel);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_RETRY_ORDER /*1005*/:
                responseVoid = responseInts(parcel);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_FAILURE /*1006*/:
                responseVoid = responseStrings(parcel);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_NOT_EMERGENCY /*1008*/:
                responseVoid = responseString(parcel);
                break;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_BLOCKED /*1009*/:
                responseVoid = responseSignalStrength(parcel);
                break;
            case 1010:
                responseVoid = responseDataCallList(parcel);
                break;
            case 1011:
                responseVoid = responseSuppServiceNotification(parcel);
                break;
            case 1012:
                responseVoid = responseVoid(parcel);
                break;
            case 1013:
                responseVoid = responseString(parcel);
                break;
            case 1014:
                responseVoid = responseString(parcel);
                break;
            case CharacterSets.UTF_16 /*1015*/:
                responseVoid = responseInts(parcel);
                break;
            case 1016:
                responseVoid = responseVoid(parcel);
                break;
            case 1017:
                responseVoid = responseSimRefresh(parcel);
                break;
            case 1018:
                responseVoid = responseCallRing(parcel);
                break;
            case 1019:
                responseVoid = responseVoid(parcel);
                break;
            case 1020:
                responseVoid = responseCdmaSms(parcel);
                break;
            case 1021:
                responseVoid = responseRaw(parcel);
                break;
            case 1022:
                responseVoid = responseVoid(parcel);
                break;
            case 1023:
                responseVoid = responseInts(parcel);
                break;
            case 1024:
                responseVoid = responseVoid(parcel);
                break;
            case 1025:
                responseVoid = responseCdmaCallWaiting(parcel);
                break;
            case 1026:
                responseVoid = responseInts(parcel);
                break;
            case 1027:
                responseVoid = responseCdmaInformationRecord(parcel);
                break;
            case 1028:
                responseVoid = responseRaw(parcel);
                break;
            case 1029:
                responseVoid = responseInts(parcel);
                break;
            case 1030:
                responseVoid = responseVoid(parcel);
                break;
            case 1031:
                responseVoid = responseInts(parcel);
                break;
            case 1032:
                responseVoid = responseInts(parcel);
                break;
            case 1033:
                responseVoid = responseVoid(parcel);
                break;
            case 1034:
                responseVoid = responseInts(parcel);
                break;
            case 1035:
                responseVoid = responseInts(parcel);
                break;
            case 1036:
                responseVoid = responseCellInfoList(parcel);
                break;
            case 1037:
                responseVoid = responseVoid(parcel);
                break;
            case 1038:
                responseVoid = responseInts(parcel);
                break;
            case 1039:
                responseVoid = responseInts(parcel);
                break;
            case 1040:
                responseVoid = responseHardwareConfig(parcel);
                break;
            case 1043:
                responseVoid = responseSsData(parcel);
                break;
            case 1044:
                responseVoid = responseString(parcel);
                break;
            default:
                try {
                    throw new RuntimeException("Unrecognized unsol response: " + readInt);
                } catch (Throwable th) {
                    Rlog.e(RILJ_LOG_TAG, "Exception processing unsol response: " + readInt + "Exception:" + th.toString());
                    return;
                }
        }
        String[] strArr;
        SmsMessage newFromCMT;
        switch (readInt) {
            case 1000:
                RadioState radioStateFromInt = getRadioStateFromInt(parcel.readInt());
                unsljLogMore(readInt, radioStateFromInt.toString());
                switchToRadioState(radioStateFromInt);
                return;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_DROP /*1001*/:
                unsljLog(readInt);
                this.mCallStateRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
                return;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_INTERCEPT /*1002*/:
                unsljLog(readInt);
                this.mVoiceNetworkStateRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
                return;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_REORDER /*1003*/:
                unsljLog(readInt);
                strArr = new String[2];
                strArr[1] = (String) responseVoid;
                newFromCMT = SmsMessage.newFromCMT(strArr);
                if (this.mGsmSmsRegistrant != null) {
                    this.mGsmSmsRegistrant.notifyRegistrant(new AsyncResult(null, newFromCMT, null));
                    return;
                }
                return;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_SO_REJECT /*1004*/:
                unsljLogRet(readInt, responseVoid);
                if (this.mSmsStatusRegistrant != null) {
                    this.mSmsStatusRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_RETRY_ORDER /*1005*/:
                unsljLogRet(readInt, responseVoid);
                int[] iArr = (int[]) responseVoid;
                if (iArr.length != 1) {
                    riljLog(" NEW_SMS_ON_SIM ERROR with wrong length " + iArr.length);
                    return;
                } else if (this.mSmsOnSimRegistrant != null) {
                    this.mSmsOnSimRegistrant.notifyRegistrant(new AsyncResult(null, iArr, null));
                    return;
                } else {
                    return;
                }
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_FAILURE /*1006*/:
                strArr = (String[]) responseVoid;
                if (strArr.length < 2) {
                    strArr = new String[]{((String[]) responseVoid)[0], null};
                }
                unsljLogMore(readInt, strArr[0]);
                if (this.mUSSDRegistrant != null) {
                    this.mUSSDRegistrant.notifyRegistrant(new AsyncResult(null, strArr, null));
                    return;
                }
                return;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_NOT_EMERGENCY /*1008*/:
                unsljLogRet(readInt, responseVoid);
                long readLong = parcel.readLong();
                Object[] objArr = new Object[]{responseVoid, Long.valueOf(readLong)};
                if (SystemProperties.getBoolean("telephony.test.ignore.nitz", false)) {
                    riljLog("ignoring UNSOL_NITZ_TIME_RECEIVED");
                    return;
                }
                if (this.mNITZTimeRegistrant != null) {
                    this.mNITZTimeRegistrant.notifyRegistrant(new AsyncResult(null, objArr, null));
                }
                this.mLastNITZTimeInfo = objArr;
                return;
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_BLOCKED /*1009*/:
                if (this.mSignalStrengthRegistrant != null) {
                    this.mSignalStrengthRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1010:
                unsljLogRet(readInt, responseVoid);
                this.mDataNetworkStateRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                return;
            case 1011:
                unsljLogRet(readInt, responseVoid);
                if (this.mSsnRegistrant != null) {
                    this.mSsnRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1012:
                unsljLog(readInt);
                if (this.mCatSessionEndRegistrant != null) {
                    this.mCatSessionEndRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1013:
                unsljLog(readInt);
                if (this.mCatProCmdRegistrant != null) {
                    this.mCatProCmdRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1014:
                unsljLog(readInt);
                if (this.mCatEventRegistrant != null) {
                    this.mCatEventRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case CharacterSets.UTF_16 /*1015*/:
                unsljLogRet(readInt, responseVoid);
                if (this.mCatCallSetUpRegistrant != null) {
                    this.mCatCallSetUpRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1016:
                unsljLog(readInt);
                if (this.mIccSmsFullRegistrant != null) {
                    this.mIccSmsFullRegistrant.notifyRegistrant();
                    return;
                }
                return;
            case 1017:
                unsljLogRet(readInt, responseVoid);
                if (this.mIccRefreshRegistrants != null) {
                    this.mIccRefreshRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1018:
                unsljLogRet(readInt, responseVoid);
                if (this.mRingRegistrant != null) {
                    this.mRingRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1019:
                unsljLog(readInt);
                if (this.mIccStatusChangedRegistrants != null) {
                    this.mIccStatusChangedRegistrants.notifyRegistrants();
                    return;
                }
                return;
            case 1020:
                unsljLog(readInt);
                newFromCMT = (SmsMessage) responseVoid;
                if (this.mCdmaSmsRegistrant != null) {
                    this.mCdmaSmsRegistrant.notifyRegistrant(new AsyncResult(null, newFromCMT, null));
                    return;
                }
                return;
            case 1021:
                unsljLogvRet(readInt, IccUtils.bytesToHexString((byte[]) responseVoid));
                if (this.mGsmBroadcastSmsRegistrant != null) {
                    this.mGsmBroadcastSmsRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1022:
                unsljLog(readInt);
                if (this.mIccSmsFullRegistrant != null) {
                    this.mIccSmsFullRegistrant.notifyRegistrant();
                    return;
                }
                return;
            case 1023:
                unsljLogvRet(readInt, responseVoid);
                if (this.mRestrictedStateRegistrant != null) {
                    this.mRestrictedStateRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1024:
                unsljLog(readInt);
                if (this.mEmergencyCallbackModeRegistrant != null) {
                    this.mEmergencyCallbackModeRegistrant.notifyRegistrant();
                    return;
                }
                return;
            case 1025:
                unsljLogRet(readInt, responseVoid);
                if (this.mCallWaitingInfoRegistrants != null) {
                    this.mCallWaitingInfoRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1026:
                unsljLogRet(readInt, responseVoid);
                if (this.mOtaProvisionRegistrants != null) {
                    this.mOtaProvisionRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1027:
                try {
                    Iterator it = ((ArrayList) responseVoid).iterator();
                    while (it.hasNext()) {
                        CdmaInformationRecords cdmaInformationRecords = (CdmaInformationRecords) it.next();
                        unsljLogRet(readInt, cdmaInformationRecords);
                        notifyRegistrantsCdmaInfoRec(cdmaInformationRecords);
                    }
                    return;
                } catch (ClassCastException e) {
                    Rlog.e(RILJ_LOG_TAG, "Unexpected exception casting to listInfoRecs", e);
                    return;
                }
            case 1028:
                unsljLogvRet(readInt, IccUtils.bytesToHexString((byte[]) responseVoid));
                ByteBuffer wrap = ByteBuffer.wrap((byte[]) responseVoid);
                wrap.order(ByteOrder.nativeOrder());
                if (isQcUnsolOemHookResp(wrap)) {
                    Rlog.d(RILJ_LOG_TAG, "OEM ID check Passed");
                    processUnsolOemhookResponse(wrap);
                    return;
                } else if (this.mUnsolOemHookRawRegistrant != null) {
                    Rlog.d(RILJ_LOG_TAG, "External OEM message, to be notified");
                    this.mUnsolOemHookRawRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                } else {
                    return;
                }
            case 1029:
                unsljLogvRet(readInt, responseVoid);
                if (this.mRingbackToneRegistrants != null) {
                    this.mRingbackToneRegistrants.notifyRegistrants(new AsyncResult(null, Boolean.valueOf(((int[]) ((int[]) responseVoid))[0] == 1), null));
                    return;
                }
                return;
            case 1030:
                unsljLogRet(readInt, responseVoid);
                if (this.mResendIncallMuteRegistrants != null) {
                    this.mResendIncallMuteRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1031:
                unsljLogRet(readInt, responseVoid);
                if (this.mCdmaSubscriptionChangedRegistrants != null) {
                    this.mCdmaSubscriptionChangedRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1032:
                unsljLogRet(readInt, responseVoid);
                if (this.mCdmaPrlChangedRegistrants != null) {
                    this.mCdmaPrlChangedRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1033:
                unsljLogRet(readInt, responseVoid);
                if (this.mExitEmergencyCallbackModeRegistrants != null) {
                    this.mExitEmergencyCallbackModeRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
                    return;
                }
                return;
            case 1034:
                unsljLogRet(readInt, responseVoid);
                setRadioPower(false, null);
                setCdmaSubscriptionSource(this.mCdmaSubscription, null);
                setCellInfoListRate(Integer.MAX_VALUE, null);
                notifyRegistrantsRilConnectionChanged(((int[]) responseVoid)[0]);
                if (!this.mRILConnected_SetInitialAttachApn_OnceSkip) {
                    riljLog("[EXTDBG] RILD crash, call setInitialAttachApn");
                    setInitialAttachApn(this.mSetInitialAttachApn_Apn, this.mSetInitialAttachApn_Protocol, this.mSetInitialAttachApn_AuthType, this.mSetInitialAttachApn_Username, this.mSetInitialAttachApn_Password, null);
                }
                if (!this.mRILConnected_UpdateOemDataSettings_OnceSkip) {
                    riljLog("[EXTDBG] RILD crash, call updateOemDataSettings");
                    updateOemDataSettings(this.mUpdateOemDataSettings_MobileData, this.mUpdateOemDataSettings_DataRoaming, true, null);
                }
                if (this.mSetLimitationByChameleon) {
                    setLimitationByChameleon(this.mLimitationByChameleon, null);
                    return;
                }
                return;
            case 1035:
                unsljLogRet(readInt, responseVoid);
                if (this.mVoiceRadioTechChangedRegistrants != null) {
                    this.mVoiceRadioTechChangedRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1036:
                unsljLogRet(readInt, responseVoid);
                if (this.mRilCellInfoListRegistrants != null) {
                    this.mRilCellInfoListRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1037:
                unsljLog(readInt);
                this.mImsNetworkStateChangedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
                return;
            case 1038:
                unsljLogRet(readInt, responseVoid);
                if (this.mSubscriptionStatusRegistrants != null) {
                    this.mSubscriptionStatusRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1039:
                unsljLogRet(readInt, responseVoid);
                if (this.mSrvccStateRegistrants != null) {
                    this.mSrvccStateRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1040:
                unsljLogRet(readInt, responseVoid);
                if (this.mHardwareConfigChangeRegistrants != null) {
                    this.mHardwareConfigChangeRegistrants.notifyRegistrants(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1043:
                unsljLogRet(readInt, responseVoid);
                if (this.mSsRegistrant != null) {
                    this.mSsRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            case 1044:
                unsljLogRet(readInt, responseVoid);
                if (this.mCatCcAlphaRegistrant != null) {
                    this.mCatCcAlphaRegistrant.notifyRegistrant(new AsyncResult(null, responseVoid, null));
                    return;
                }
                return;
            default:
                return;
        }
    }

    private static int readRilMessage(InputStream inputStream, byte[] bArr) throws IOException {
        int read;
        int i = 0;
        int i2 = 4;
        int i3 = 0;
        do {
            read = inputStream.read(bArr, i3, i2);
            if (read < 0) {
                Rlog.e(RILJ_LOG_TAG, "Hit EOS reading message length");
                return -1;
            }
            i3 += read;
            i2 -= read;
        } while (i2 > 0);
        i2 = (bArr[3] & 255) | ((((bArr[0] & 255) << 24) | ((bArr[1] & 255) << 16)) | ((bArr[2] & 255) << 8));
        i3 = i2;
        do {
            read = inputStream.read(bArr, i, i3);
            if (read < 0) {
                Rlog.e(RILJ_LOG_TAG, "Hit EOS reading message.  messageLength=" + i2 + " remaining=" + i3);
                return -1;
            }
            i += read;
            i3 -= read;
        } while (i3 > 0);
        return i2;
    }

    static String requestToString(int i) {
        switch (i) {
            case 1:
                return "GET_SIM_STATUS";
            case 2:
                return "ENTER_SIM_PIN";
            case 3:
                return "ENTER_SIM_PUK";
            case 4:
                return "ENTER_SIM_PIN2";
            case 5:
                return "ENTER_SIM_PUK2";
            case 6:
                return "CHANGE_SIM_PIN";
            case 7:
                return "CHANGE_SIM_PIN2";
            case 8:
                return "ENTER_DEPERSONALIZATION_CODE";
            case 9:
                return "GET_CURRENT_CALLS";
            case 10:
                return "DIAL";
            case 11:
                return "GET_IMSI";
            case 12:
                return "HANGUP";
            case 13:
                return "HANGUP_WAITING_OR_BACKGROUND";
            case 14:
                return "HANGUP_FOREGROUND_RESUME_BACKGROUND";
            case 15:
                return "REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE";
            case 16:
                return "CONFERENCE";
            case 17:
                return "UDUB";
            case 18:
                return "LAST_CALL_FAIL_CAUSE";
            case 19:
                return "SIGNAL_STRENGTH";
            case 20:
                return "VOICE_REGISTRATION_STATE";
            case 21:
                return "DATA_REGISTRATION_STATE";
            case 22:
                return "OPERATOR";
            case 23:
                return "RADIO_POWER";
            case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /*24*/:
                return "DTMF";
            case 25:
                return "SEND_SMS";
            case 26:
                return "SEND_SMS_EXPECT_MORE";
            case OEM_RIL_RDE_Data.RDE_NV_OTKSL_I /*27*/:
                return "SETUP_DATA_CALL";
            case 28:
                return "SIM_IO";
            case IccRecords.EVENT_REFRESH_OEM /*29*/:
                return "SEND_USSD";
            case 30:
                return "CANCEL_USSD";
            case 31:
                return "GET_CLIR";
            case 32:
                return "SET_CLIR";
            case 33:
                return "QUERY_CALL_FORWARD_STATUS";
            case 34:
                return "SET_CALL_FORWARD";
            case 35:
                return "QUERY_CALL_WAITING";
            case 36:
                return "SET_CALL_WAITING";
            case 37:
                return "SMS_ACKNOWLEDGE";
            case RadioNVItems.RIL_NV_MIP_PROFILE_HA_SPI /*38*/:
                return "GET_IMEI";
            case 39:
                return "GET_IMEISV";
            case 40:
                return "ANSWER";
            case 41:
                return "DEACTIVATE_DATA_CALL";
            case 42:
                return "QUERY_FACILITY_LOCK";
            case OEM_RIL_RDE_Data.RDE_NV_EHRPD_ENABLED_I /*43*/:
                return "SET_FACILITY_LOCK";
            case CallFailCause.CHANNEL_NOT_AVAIL /*44*/:
                return "CHANGE_BARRING_PASSWORD";
            case OEM_RIL_RDE_Data.RDE_NV_CDMA_SO73_ENABLED_I /*45*/:
                return "QUERY_NETWORK_SELECTION_MODE";
            case OEM_RIL_RDE_Data.RDE_NV_DS_MIP_SS_USER_PROF_I /*46*/:
                return "SET_NETWORK_SELECTION_AUTOMATIC";
            case 47:
                return "SET_NETWORK_SELECTION_MANUAL";
            case OEM_RIL_RDE_Data.RDE_NV_CDMA_SO68_ENABLED_I /*48*/:
                return "QUERY_AVAILABLE_NETWORKS ";
            case 49:
                return "DTMF_START";
            case OEM_RIL_RDE_Data.RDE_NV_MOB_TERM_HOME_I /*50*/:
                return "DTMF_STOP";
            case 51:
                return "BASEBAND_VERSION";
            case 52:
                return "SEPARATE_CONNECTION";
            case 53:
                return "SET_MUTE";
            case RadioNVItems.RIL_NV_CDMA_SO68 /*54*/:
                return "GET_MUTE";
            case 55:
                return "QUERY_CLIP";
            case 56:
                return "LAST_DATA_CALL_FAIL_CAUSE";
            case RadioNVItems.RIL_NV_CDMA_1X_ADVANCED_ENABLED /*57*/:
                return "DATA_CALL_LIST";
            case 58:
                return "RESET_RADIO";
            case RadioNVItems.RIL_NV_CDMA_EHRPD_FORCED /*59*/:
                return "OEM_HOOK_RAW";
            case 60:
                return "OEM_HOOK_STRINGS";
            case 61:
                return "SCREEN_STATE";
            case 62:
                return "SET_SUPP_SVC_NOTIFICATION";
            case SignalToneUtil.IS95_CONST_IR_SIG_TONE_NO_TONE /*63*/:
                return "WRITE_SMS_TO_SIM";
            case 64:
                return "DELETE_SMS_ON_SIM";
            case 65:
                return "SET_BAND_MODE";
            case 66:
                return "QUERY_AVAILABLE_BAND_MODE";
            case 67:
                return "REQUEST_STK_GET_PROFILE";
            case 68:
                return "REQUEST_STK_SET_PROFILE";
            case 69:
                return "REQUEST_STK_SEND_ENVELOPE_COMMAND";
            case 70:
                return "REQUEST_STK_SEND_TERMINAL_RESPONSE";
            case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_25 /*71*/:
                return "REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM";
            case 72:
                return "REQUEST_EXPLICIT_CALL_TRANSFER";
            case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_41 /*73*/:
                return "REQUEST_SET_PREFERRED_NETWORK_TYPE";
            case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_25 /*74*/:
                return "REQUEST_GET_PREFERRED_NETWORK_TYPE";
            case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_26 /*75*/:
                return "REQUEST_GET_NEIGHBORING_CELL_IDS";
            case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_41 /*76*/:
                return "REQUEST_SET_LOCATION_UPDATES";
            case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_25 /*77*/:
                return "RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE";
            case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_26 /*78*/:
                return "RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE";
            case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_41 /*79*/:
                return "RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE";
            case RadioNVItems.RIL_NV_LTE_NEXT_SCAN /*80*/:
                return "RIL_REQUEST_SET_TTY_MODE";
            case RadioNVItems.RIL_NV_LTE_BSR_TIMER /*81*/:
                return "RIL_REQUEST_QUERY_TTY_MODE";
            case RadioNVItems.RIL_NV_LTE_BSR_MAX_TIME /*82*/:
                return "RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE";
            case 83:
                return "RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE";
            case 84:
                return "RIL_REQUEST_CDMA_FLASH";
            case 85:
                return "RIL_REQUEST_CDMA_BURST_DTMF";
            case 86:
                return "RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY";
            case 87:
                return "RIL_REQUEST_CDMA_SEND_SMS";
            case 88:
                return "RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE";
            case 89:
                return "RIL_REQUEST_GSM_GET_BROADCAST_CONFIG";
            case 90:
                return "RIL_REQUEST_GSM_SET_BROADCAST_CONFIG";
            case 91:
                return "RIL_REQUEST_GSM_BROADCAST_ACTIVATION";
            case 92:
                return "RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG";
            case 93:
                return "RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG";
            case 94:
                return "RIL_REQUEST_CDMA_BROADCAST_ACTIVATION";
            case 95:
                return "RIL_REQUEST_CDMA_SUBSCRIPTION";
            case CommandsInterface.CDMA_SMS_FAIL_CAUSE_ENCODING_PROBLEM /*96*/:
                return "RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM";
            case 97:
                return "RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM";
            case 98:
                return "RIL_REQUEST_DEVICE_IDENTITY";
            case 99:
                return "REQUEST_EXIT_EMERGENCY_CALLBACK_MODE";
            case IccRecords.EVENT_GET_ICC_RECORD_DONE /*100*/:
                return "RIL_REQUEST_GET_SMSC_ADDRESS";
            case 101:
                return "RIL_REQUEST_SET_SMSC_ADDRESS";
            case 102:
                return "RIL_REQUEST_REPORT_SMS_MEMORY_STATUS";
            case 103:
                return "RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING";
            case 104:
                return "RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE";
            case 105:
                return "RIL_REQUEST_ISIM_AUTHENTICATION";
            case 106:
                return "RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU";
            case 107:
                return "RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS";
            case OEM_RIL_RDE_Data.RDE_NV_ROAMING_LIST_683_I /*108*/:
                return "RIL_REQUEST_VOICE_RADIO_TECH";
            case 109:
                return "RIL_REQUEST_GET_CELL_INFO_LIST";
            case 110:
                return "RIL_REQUEST_SET_CELL_INFO_LIST_RATE";
            case 111:
                return "RIL_REQUEST_SET_INITIAL_ATTACH_APN";
            case 112:
                return "RIL_REQUEST_IMS_REGISTRATION_STATE";
            case 113:
                return "RIL_REQUEST_IMS_SEND_SMS";
            case 114:
                return "RIL_REQUEST_SIM_TRANSMIT_APDU_BASIC";
            case 115:
                return "RIL_REQUEST_SIM_OPEN_CHANNEL";
            case 116:
                return "RIL_REQUEST_SIM_CLOSE_CHANNEL";
            case 117:
                return "RIL_REQUEST_SIM_TRANSMIT_APDU_CHANNEL";
            case 118:
                return "RIL_REQUEST_NV_READ_ITEM";
            case 119:
                return "RIL_REQUEST_NV_WRITE_ITEM";
            case 120:
                return "RIL_REQUEST_NV_WRITE_CDMA_PRL";
            case 121:
                return "RIL_REQUEST_NV_RESET_CONFIG";
            case 122:
                return "RIL_REQUEST_SET_UICC_SUBSCRIPTION";
            case 123:
                return "RIL_REQUEST_ALLOW_DATA";
            case 124:
                return "GET_HARDWARE_CONFIG";
            case 125:
                return "RIL_REQUEST_SIM_AUTHENTICATION";
            case 128:
                return "RIL_REQUEST_SET_DATA_PROFILE";
            case 129:
                return "RIL_REQUEST_SHUTDOWN";
            case 132:
                return "RIL_REQUEST_GET_DATA_CALL_PROFILE";
            case 133:
                return "SIM_GET_ATR";
            case 134:
                return "SIM_TRANSMIT_BASIC_SHARP";
            case 135:
                return "SIM_OPEN_CHANNEL_SHARP";
            case 136:
                return "SIM_CLOSE_CHANNEL_SHARP";
            case 137:
                return "SIM_TRANSMIT_CHANNEL_SHARP";
            default:
                return "<unknown request>";
        }
    }

    private Object responseCallForward(Parcel parcel) {
        int readInt = parcel.readInt();
        CallForwardInfo[] callForwardInfoArr = new CallForwardInfo[readInt];
        for (int i = 0; i < readInt; i++) {
            callForwardInfoArr[i] = new CallForwardInfo();
            callForwardInfoArr[i].status = parcel.readInt();
            callForwardInfoArr[i].reason = parcel.readInt();
            callForwardInfoArr[i].serviceClass = parcel.readInt();
            callForwardInfoArr[i].toa = parcel.readInt();
            callForwardInfoArr[i].number = parcel.readString();
            callForwardInfoArr[i].timeSeconds = parcel.readInt();
        }
        return callForwardInfoArr;
    }

    private Object responseCallList(Parcel parcel) {
        int readInt = parcel.readInt();
        ArrayList arrayList = new ArrayList(readInt);
        for (int i = 0; i < readInt; i++) {
            DriverCall driverCall = new DriverCall();
            driverCall.state = DriverCall.stateFromCLCC(parcel.readInt());
            driverCall.index = parcel.readInt();
            driverCall.TOA = parcel.readInt();
            driverCall.isMpty = parcel.readInt() != 0;
            driverCall.isMT = parcel.readInt() != 0;
            driverCall.als = parcel.readInt();
            driverCall.isVoice = parcel.readInt() != 0;
            driverCall.isVoicePrivacy = parcel.readInt() != 0;
            driverCall.number = parcel.readString();
            driverCall.numberPresentation = DriverCall.presentationFromCLIP(parcel.readInt());
            driverCall.name = parcel.readString();
            driverCall.namePresentation = DriverCall.presentationFromCLIP(parcel.readInt());
            if (parcel.readInt() == 1) {
                driverCall.uusInfo = new UUSInfo();
                driverCall.uusInfo.setType(parcel.readInt());
                driverCall.uusInfo.setDcs(parcel.readInt());
                driverCall.uusInfo.setUserData(parcel.createByteArray());
                riljLogv(String.format("Incoming UUS : type=%d, dcs=%d, length=%d", new Object[]{Integer.valueOf(driverCall.uusInfo.getType()), Integer.valueOf(driverCall.uusInfo.getDcs()), Integer.valueOf(driverCall.uusInfo.getUserData().length)}));
                riljLogv("Incoming UUS : data (string)=" + new String(driverCall.uusInfo.getUserData()));
                riljLogv("Incoming UUS : data (hex): " + IccUtils.bytesToHexString(driverCall.uusInfo.getUserData()));
            } else {
                riljLogv("Incoming UUS : NOT present!");
            }
            driverCall.number = PhoneNumberUtils.stringFromStringAndTOA(driverCall.number, driverCall.TOA);
            arrayList.add(driverCall);
            if (driverCall.isVoicePrivacy) {
                this.mVoicePrivacyOnRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is enabled");
            } else {
                this.mVoicePrivacyOffRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is disabled");
            }
        }
        Collections.sort(arrayList);
        if (readInt == 0 && this.mTestingEmergencyCall.getAndSet(false) && this.mEmergencyCallbackModeRegistrant != null) {
            riljLog("responseCallList: call ended, testing emergency call, notify ECM Registrants");
            this.mEmergencyCallbackModeRegistrant.notifyRegistrant();
        }
        return arrayList;
    }

    private Object responseCallRing(Parcel parcel) {
        return new char[]{(char) parcel.readInt(), (char) parcel.readInt(), (char) parcel.readInt(), (char) parcel.readInt()};
    }

    private Object responseCdmaBroadcastConfig(Parcel parcel) {
        Object obj;
        int i = 1;
        int readInt = parcel.readInt();
        if (readInt == 0) {
            obj = new int[94];
            obj[0] = 31;
            for (readInt = 1; readInt < 94; readInt += 3) {
                obj[readInt + 0] = readInt / 3;
                obj[readInt + 1] = 1;
                obj[readInt + 2] = null;
            }
        } else {
            int i2 = (readInt * 3) + 1;
            obj = new int[i2];
            obj[0] = readInt;
            while (i < i2) {
                obj[i] = parcel.readInt();
                i++;
            }
        }
        return obj;
    }

    private Object responseCdmaCallWaiting(Parcel parcel) {
        CdmaCallWaitingNotification cdmaCallWaitingNotification = new CdmaCallWaitingNotification();
        cdmaCallWaitingNotification.number = parcel.readString();
        cdmaCallWaitingNotification.numberPresentation = CdmaCallWaitingNotification.presentationFromCLIP(parcel.readInt());
        cdmaCallWaitingNotification.name = parcel.readString();
        cdmaCallWaitingNotification.namePresentation = cdmaCallWaitingNotification.numberPresentation;
        cdmaCallWaitingNotification.isPresent = parcel.readInt();
        cdmaCallWaitingNotification.signalType = parcel.readInt();
        cdmaCallWaitingNotification.alertPitch = parcel.readInt();
        cdmaCallWaitingNotification.signal = parcel.readInt();
        cdmaCallWaitingNotification.numberType = parcel.readInt();
        cdmaCallWaitingNotification.numberPlan = parcel.readInt();
        return cdmaCallWaitingNotification;
    }

    private ArrayList<CdmaInformationRecords> responseCdmaInformationRecord(Parcel parcel) {
        int readInt = parcel.readInt();
        ArrayList arrayList = new ArrayList(readInt);
        for (int i = 0; i < readInt; i++) {
            arrayList.add(new CdmaInformationRecords(parcel));
        }
        return arrayList;
    }

    private Object responseCdmaSms(Parcel parcel) {
        return SmsMessage.newFromParcel(parcel);
    }

    private ArrayList<CellInfo> responseCellInfoList(Parcel parcel) {
        int readInt = parcel.readInt();
        ArrayList arrayList = new ArrayList(readInt);
        for (int i = 0; i < readInt; i++) {
            arrayList.add((CellInfo) CellInfo.CREATOR.createFromParcel(parcel));
        }
        return arrayList;
    }

    private Object responseCellList(Parcel parcel) {
        int readInt = parcel.readInt();
        ArrayList arrayList = new ArrayList();
        int dataNetworkType = ((TelephonyManager) this.mContext.getSystemService("phone")).getDataNetworkType(SubscriptionManager.getSubId(this.mInstanceId.intValue())[0]);
        if (dataNetworkType != 0) {
            for (int i = 0; i < readInt; i++) {
                arrayList.add(new NeighboringCellInfo(parcel.readInt(), parcel.readString(), dataNetworkType));
            }
        }
        return arrayList;
    }

    private Object responseDataCallList(Parcel parcel) {
        int readInt = parcel.readInt();
        int readInt2 = parcel.readInt();
        riljLog("responseDataCallList ver=" + readInt + " num=" + readInt2);
        ArrayList arrayList = new ArrayList(readInt2);
        for (int i = 0; i < readInt2; i++) {
            arrayList.add(getDataCallResponse(parcel, readInt));
        }
        return arrayList;
    }

    private ArrayList<ApnSetting> responseGetDataCallProfile(Parcel parcel) {
        int readInt = parcel.readInt();
        riljLog("# data call profiles:" + readInt);
        ArrayList arrayList = new ArrayList(readInt);
        for (int i = 0; i < readInt; i++) {
            ApnProfileOmh apnProfileOmh = new ApnProfileOmh(parcel.readInt(), parcel.readInt());
            riljLog("responseGetDataCallProfile()" + apnProfileOmh.getProfileId() + ":" + apnProfileOmh.getPriority());
            arrayList.add(apnProfileOmh);
        }
        return arrayList;
    }

    private Object responseGetPreferredNetworkType(Parcel parcel) {
        int[] iArr = (int[]) responseInts(parcel);
        if (iArr.length >= 1) {
            this.mPreferredNetworkType = iArr[0];
        }
        return iArr;
    }

    private Object responseGmsBroadcastConfig(Parcel parcel) {
        int readInt = parcel.readInt();
        ArrayList arrayList = new ArrayList(readInt);
        for (int i = 0; i < readInt; i++) {
            arrayList.add(new SmsBroadcastConfigInfo(parcel.readInt(), parcel.readInt(), parcel.readInt(), parcel.readInt(), parcel.readInt() == 1));
        }
        return arrayList;
    }

    private Object responseHardwareConfig(Parcel parcel) {
        int readInt = parcel.readInt();
        ArrayList arrayList = new ArrayList(readInt);
        for (int i = 0; i < readInt; i++) {
            Object hardwareConfig;
            int readInt2 = parcel.readInt();
            switch (readInt2) {
                case 0:
                    hardwareConfig = new HardwareConfig(readInt2);
                    hardwareConfig.assignModem(parcel.readString(), parcel.readInt(), parcel.readInt(), parcel.readInt(), parcel.readInt(), parcel.readInt(), parcel.readInt());
                    break;
                case 1:
                    hardwareConfig = new HardwareConfig(readInt2);
                    hardwareConfig.assignSim(parcel.readString(), parcel.readInt(), parcel.readString());
                    break;
                default:
                    throw new RuntimeException("RIL_REQUEST_GET_HARDWARE_CONFIG invalid hardward type:" + readInt2);
            }
            arrayList.add(hardwareConfig);
        }
        return arrayList;
    }

    private Object responseICC_IO(Parcel parcel) {
        return new IccIoResult(parcel.readInt(), parcel.readInt(), parcel.readString());
    }

    private Object responseICC_IOBase64(Parcel parcel) {
        return new IccIoResult(parcel.readInt(), parcel.readInt(), Base64.decode(parcel.readString(), 0));
    }

    private Object responseIccCardStatus(Parcel parcel) {
        int i = 8;
        IccCardStatus iccCardStatus = new IccCardStatus();
        iccCardStatus.setCardState(parcel.readInt());
        iccCardStatus.setUniversalPinState(parcel.readInt());
        iccCardStatus.mGsmUmtsSubscriptionAppIndex = parcel.readInt();
        iccCardStatus.mCdmaSubscriptionAppIndex = parcel.readInt();
        iccCardStatus.mImsSubscriptionAppIndex = parcel.readInt();
        int readInt = parcel.readInt();
        if (readInt <= 8) {
            i = readInt;
        }
        iccCardStatus.mApplications = new IccCardApplicationStatus[i];
        for (readInt = 0; readInt < i; readInt++) {
            IccCardApplicationStatus iccCardApplicationStatus = new IccCardApplicationStatus();
            iccCardApplicationStatus.app_type = iccCardApplicationStatus.AppTypeFromRILInt(parcel.readInt());
            iccCardApplicationStatus.app_state = iccCardApplicationStatus.AppStateFromRILInt(parcel.readInt());
            iccCardApplicationStatus.perso_substate = iccCardApplicationStatus.PersoSubstateFromRILInt(parcel.readInt());
            iccCardApplicationStatus.aid = parcel.readString();
            iccCardApplicationStatus.app_label = parcel.readString();
            iccCardApplicationStatus.pin1_replaced = parcel.readInt();
            iccCardApplicationStatus.pin1 = iccCardApplicationStatus.PinStateFromRILInt(parcel.readInt());
            iccCardApplicationStatus.pin2 = iccCardApplicationStatus.PinStateFromRILInt(parcel.readInt());
            iccCardStatus.mApplications[readInt] = iccCardApplicationStatus;
        }
        return iccCardStatus;
    }

    private Object responseInts(Parcel parcel) {
        int readInt = parcel.readInt();
        int[] iArr = new int[readInt];
        for (int i = 0; i < readInt; i++) {
            iArr[i] = parcel.readInt();
        }
        return iArr;
    }

    private Object responseOperatorInfos(Parcel parcel) {
        String[] strArr = (String[]) responseStrings(parcel);
        SpnOverride spnOverride = new SpnOverride();
        if (strArr.length % 4 != 0) {
            throw new RuntimeException("RIL_REQUEST_QUERY_AVAILABLE_NETWORKS: invalid response. Got " + strArr.length + " strings, expected multible of 4");
        }
        ArrayList arrayList = new ArrayList(strArr.length / 4);
        int i = 0;
        while (true) {
            int i2 = i;
            if (i2 >= strArr.length) {
                return arrayList;
            }
            arrayList.add(new OperatorInfo(spnOverride.containsCarrier(strArr[i2 + 2]) ? spnOverride.getSpn(strArr[i2 + 2]) : strArr[i2 + 0], strArr[i2 + 1], strArr[i2 + 2], strArr[i2 + 3]));
            i = i2 + 4;
        }
    }

    private Object responseRaw(Parcel parcel) {
        return parcel.createByteArray();
    }

    private Object responseSMS(Parcel parcel) {
        return new SmsResponse(parcel.readInt(), parcel.readString(), parcel.readInt());
    }

    private Object responseSetupDataCall(Parcel parcel) {
        int readInt = parcel.readInt();
        int readInt2 = parcel.readInt();
        if (readInt < 5) {
            DataCallResponse dataCallResponse = new DataCallResponse();
            dataCallResponse.version = readInt;
            dataCallResponse.cid = Integer.parseInt(parcel.readString());
            dataCallResponse.ifname = parcel.readString();
            if (TextUtils.isEmpty(dataCallResponse.ifname)) {
                throw new RuntimeException("RIL_REQUEST_SETUP_DATA_CALL response, no ifname");
            }
            String readString = parcel.readString();
            if (!TextUtils.isEmpty(readString)) {
                dataCallResponse.addresses = readString.split(" ");
            }
            if (readInt2 >= 4) {
                readString = parcel.readString();
                riljLog("responseSetupDataCall got dnses=" + readString);
                if (!TextUtils.isEmpty(readString)) {
                    dataCallResponse.dnses = readString.split(" ");
                }
            }
            if (readInt2 >= 5) {
                readString = parcel.readString();
                riljLog("responseSetupDataCall got gateways=" + readString);
                if (!TextUtils.isEmpty(readString)) {
                    dataCallResponse.gateways = readString.split(" ");
                }
            }
            if (readInt2 >= 6) {
                readString = parcel.readString();
                riljLog("responseSetupDataCall got pcscf=" + readString);
                if (!TextUtils.isEmpty(readString)) {
                    dataCallResponse.pcscf = readString.split(" ");
                }
            }
            if (readInt2 < 7) {
                return dataCallResponse;
            }
            dataCallResponse.mtu = Integer.parseInt(parcel.readString());
            riljLog("responseSetupDataCall got mtu=" + dataCallResponse.mtu);
            return dataCallResponse;
        } else if (readInt2 == 1) {
            return getDataCallResponse(parcel, readInt);
        } else {
            throw new RuntimeException("RIL_REQUEST_SETUP_DATA_CALL response expecting 1 RIL_Data_Call_response_v5 got " + readInt2);
        }
    }

    private Object responseSignalStrength(Parcel parcel) {
        return SignalStrength.makeSignalStrengthFromRilParcel(parcel);
    }

    private Object responseSimRefresh(Parcel parcel) {
        IccRefreshResponse iccRefreshResponse = new IccRefreshResponse();
        iccRefreshResponse.refreshResult = parcel.readInt();
        iccRefreshResponse.efId = parcel.readInt();
        iccRefreshResponse.aid = parcel.readString();
        return iccRefreshResponse;
    }

    private Object responseSsData(Parcel parcel) {
        int i = 0;
        SsData ssData = new SsData();
        ssData.serviceType = ssData.ServiceTypeFromRILInt(parcel.readInt());
        ssData.requestType = ssData.RequestTypeFromRILInt(parcel.readInt());
        ssData.teleserviceType = ssData.TeleserviceTypeFromRILInt(parcel.readInt());
        ssData.serviceClass = parcel.readInt();
        ssData.result = parcel.readInt();
        int readInt = parcel.readInt();
        if (ssData.serviceType.isTypeCF() && ssData.requestType.isTypeInterrogation()) {
            ssData.cfInfo = new CallForwardInfo[readInt];
            while (i < readInt) {
                ssData.cfInfo[i] = new CallForwardInfo();
                ssData.cfInfo[i].status = parcel.readInt();
                ssData.cfInfo[i].reason = parcel.readInt();
                ssData.cfInfo[i].serviceClass = parcel.readInt();
                ssData.cfInfo[i].toa = parcel.readInt();
                ssData.cfInfo[i].number = parcel.readString();
                ssData.cfInfo[i].timeSeconds = parcel.readInt();
                riljLog("[SS Data] CF Info " + i + " : " + ssData.cfInfo[i]);
                i++;
            }
        } else {
            ssData.ssInfo = new int[readInt];
            while (i < readInt) {
                ssData.ssInfo[i] = parcel.readInt();
                riljLog("[SS Data] SS Info " + i + " : " + ssData.ssInfo[i]);
                i++;
            }
        }
        return ssData;
    }

    private Object responseString(Parcel parcel) {
        return parcel.readString();
    }

    private Object responseStrings(Parcel parcel) {
        return parcel.readStringArray();
    }

    private Object responseSuppServiceNotification(Parcel parcel) {
        SuppServiceNotification suppServiceNotification = new SuppServiceNotification();
        suppServiceNotification.notificationType = parcel.readInt();
        suppServiceNotification.code = parcel.readInt();
        suppServiceNotification.index = parcel.readInt();
        suppServiceNotification.type = parcel.readInt();
        suppServiceNotification.number = parcel.readString();
        return suppServiceNotification;
    }

    static String responseToString(int i) {
        switch (i) {
            case 1000:
                return "UNSOL_RESPONSE_RADIO_STATE_CHANGED";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_DROP /*1001*/:
                return "UNSOL_RESPONSE_CALL_STATE_CHANGED";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_INTERCEPT /*1002*/:
                return "UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_REORDER /*1003*/:
                return "UNSOL_RESPONSE_NEW_SMS";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_SO_REJECT /*1004*/:
                return "UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_RETRY_ORDER /*1005*/:
                return "UNSOL_RESPONSE_NEW_SMS_ON_SIM";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_FAILURE /*1006*/:
                return "UNSOL_ON_USSD";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_PREEMPTED /*1007*/:
                return "UNSOL_ON_USSD_REQUEST";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_NOT_EMERGENCY /*1008*/:
                return "UNSOL_NITZ_TIME_RECEIVED";
            case com.android.internal.telephony.cdma.CallFailCause.CDMA_ACCESS_BLOCKED /*1009*/:
                return "UNSOL_SIGNAL_STRENGTH";
            case 1010:
                return "UNSOL_DATA_CALL_LIST_CHANGED";
            case 1011:
                return "UNSOL_SUPP_SVC_NOTIFICATION";
            case 1012:
                return "UNSOL_STK_SESSION_END";
            case 1013:
                return "UNSOL_STK_PROACTIVE_COMMAND";
            case 1014:
                return "UNSOL_STK_EVENT_NOTIFY";
            case CharacterSets.UTF_16 /*1015*/:
                return "UNSOL_STK_CALL_SETUP";
            case 1016:
                return "UNSOL_SIM_SMS_STORAGE_FULL";
            case 1017:
                return "UNSOL_SIM_REFRESH";
            case 1018:
                return "UNSOL_CALL_RING";
            case 1019:
                return "UNSOL_RESPONSE_SIM_STATUS_CHANGED";
            case 1020:
                return "UNSOL_RESPONSE_CDMA_NEW_SMS";
            case 1021:
                return "UNSOL_RESPONSE_NEW_BROADCAST_SMS";
            case 1022:
                return "UNSOL_CDMA_RUIM_SMS_STORAGE_FULL";
            case 1023:
                return "UNSOL_RESTRICTED_STATE_CHANGED";
            case 1024:
                return "UNSOL_ENTER_EMERGENCY_CALLBACK_MODE";
            case 1025:
                return "UNSOL_CDMA_CALL_WAITING";
            case 1026:
                return "UNSOL_CDMA_OTA_PROVISION_STATUS";
            case 1027:
                return "UNSOL_CDMA_INFO_REC";
            case 1028:
                return "UNSOL_OEM_HOOK_RAW";
            case 1029:
                return "UNSOL_RINGBACK_TONE";
            case 1030:
                return "UNSOL_RESEND_INCALL_MUTE";
            case 1031:
                return "CDMA_SUBSCRIPTION_SOURCE_CHANGED";
            case 1032:
                return "UNSOL_CDMA_PRL_CHANGED";
            case 1033:
                return "UNSOL_EXIT_EMERGENCY_CALLBACK_MODE";
            case 1034:
                return "UNSOL_RIL_CONNECTED";
            case 1035:
                return "UNSOL_VOICE_RADIO_TECH_CHANGED";
            case 1036:
                return "UNSOL_CELL_INFO_LIST";
            case 1037:
                return "UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED";
            case 1038:
                return "RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED";
            case 1039:
                return "UNSOL_SRVCC_STATE_NOTIFY";
            case 1040:
                return "RIL_UNSOL_HARDWARE_CONFIG_CHANGED";
            case 1043:
                return "UNSOL_ON_SS";
            case 1044:
                return "UNSOL_STK_CC_ALPHA_NOTIFY";
            default:
                return "<unknown response>";
        }
    }

    private Object responseVoid(Parcel parcel) {
        return null;
    }

    static String retToString(int i, Object obj) {
        int i2 = 1;
        if (obj == null) {
            return "";
        }
        switch (i) {
            case 11:
            case RadioNVItems.RIL_NV_MIP_PROFILE_HA_SPI /*38*/:
            case 39:
            case 115:
            case 117:
                return "";
            default:
                int length;
                StringBuilder stringBuilder;
                ArrayList arrayList;
                StringBuilder stringBuilder2;
                Iterator it;
                if (obj instanceof int[]) {
                    int[] iArr = (int[]) obj;
                    length = iArr.length;
                    stringBuilder = new StringBuilder("{");
                    if (length > 0) {
                        stringBuilder.append(iArr[0]);
                        while (i2 < length) {
                            stringBuilder.append(", ").append(iArr[i2]);
                            i2++;
                        }
                    }
                    stringBuilder.append("}");
                    return stringBuilder.toString();
                } else if (obj instanceof String[]) {
                    String[] strArr = (String[]) obj;
                    length = strArr.length;
                    stringBuilder = new StringBuilder("{");
                    if (length > 0) {
                        stringBuilder.append(strArr[0]);
                        while (i2 < length) {
                            stringBuilder.append(", ").append(strArr[i2]);
                            i2++;
                        }
                    }
                    stringBuilder.append("}");
                    return stringBuilder.toString();
                } else if (i == 9) {
                    arrayList = (ArrayList) obj;
                    stringBuilder2 = new StringBuilder(" ");
                    it = arrayList.iterator();
                    while (it.hasNext()) {
                        stringBuilder2.append("[").append((DriverCall) it.next()).append("] ");
                    }
                    return stringBuilder2.toString();
                } else if (i == 75) {
                    arrayList = (ArrayList) obj;
                    stringBuilder2 = new StringBuilder(" ");
                    it = arrayList.iterator();
                    while (it.hasNext()) {
                        stringBuilder2.append((NeighboringCellInfo) it.next()).append(" ");
                    }
                    return stringBuilder2.toString();
                } else if (i != 124) {
                    return obj.toString();
                } else {
                    arrayList = (ArrayList) obj;
                    stringBuilder2 = new StringBuilder(" ");
                    it = arrayList.iterator();
                    while (it.hasNext()) {
                        stringBuilder2.append("[").append((HardwareConfig) it.next()).append("] ");
                    }
                    return stringBuilder2.toString();
                }
        }
    }

    private void riljLog(String str) {
        Rlog.d(RILJ_LOG_TAG, str + (this.mInstanceId != null ? " [SUB" + this.mInstanceId + "]" : ""));
    }

    private void riljLogv(String str) {
        Rlog.v(RILJ_LOG_TAG, str + (this.mInstanceId != null ? " [SUB" + this.mInstanceId + "]" : ""));
    }

    private void send(RILRequest rILRequest) {
        if (this.mSocket == null) {
            rILRequest.onError(1, null);
            rILRequest.release();
            return;
        }
        Message obtainMessage = this.mSender.obtainMessage(1, rILRequest);
        acquireWakeLock();
        obtainMessage.sendToTarget();
    }

    private void sendOemRilRequestRaw(int i, int i2, byte[] bArr, Message message) {
        byte[] bArr2 = new byte[(this.mHeaderSize + (i2 * 1))];
        ByteBuffer wrap = ByteBuffer.wrap(bArr2);
        wrap.order(ByteOrder.nativeOrder());
        wrap.put(OEM_IDENTIFIER.getBytes());
        wrap.putInt(i);
        if (i2 > 0 && bArr != null) {
            wrap.putInt(i2 * 1);
            for (byte put : bArr) {
                wrap.put(put);
            }
        }
        invokeOemRilRequestRaw(bArr2, message);
    }

    private void sendScreenState(boolean z) {
        int i = 1;
        RILRequest obtain = RILRequest.obtain(61, null);
        obtain.mParcel.writeInt(1);
        Parcel parcel = obtain.mParcel;
        if (!z) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + ": " + z);
        send(obtain);
    }

    private void setLimitedService(Message message) {
        byte[] bArr = new byte[(this.mHeaderSize + 4)];
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        wrap.order(ByteOrder.nativeOrder());
        wrap.put(OEM_IDENTIFIER.getBytes());
        wrap.putInt(591829);
        wrap.putInt(4);
        wrap.putInt(this.mLimitedService ? 1 : 0);
        invokeOemRilRequestRaw(bArr, message);
        riljLog("[EXTDBG] setLimitedService: " + this.mLimitedService);
    }

    private void switchToRadioState(RadioState radioState) {
        setRadioState(radioState);
    }

    private Object toIntArrayFromByteArray(byte[] bArr) {
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        wrap.order(ByteOrder.nativeOrder());
        int capacity = wrap.capacity() / 4;
        int[] iArr = new int[capacity];
        for (int i = 0; i < capacity; i++) {
            iArr[i] = wrap.getInt();
        }
        return iArr;
    }

    private int translateStatus(int i) {
        switch (i & 7) {
            case 3:
                return 0;
            case 5:
                return 3;
            case 7:
                return 2;
            default:
                return 1;
        }
    }

    private void unsljLog(int i) {
        riljLog("[UNSL]< " + responseToString(i));
    }

    private void unsljLogMore(int i, String str) {
        riljLog("[UNSL]< " + responseToString(i) + " " + str);
    }

    private void unsljLogRet(int i, Object obj) {
        riljLog("[UNSL]< " + responseToString(i) + " " + retToString(i, obj));
    }

    private void unsljLogvRet(int i, Object obj) {
        riljLogv("[UNSL]< " + responseToString(i) + " " + retToString(i, obj));
    }

    private void upDomesticInService(Context context, Intent intent) {
        riljLog("upDomesticInService start ");
        ServiceState newFromBundle = ServiceState.newFromBundle(intent.getExtras());
        if (newFromBundle != null) {
            riljLog("upDomesticInService gsm.domesticinservice before: " + String.valueOf(isDomesticInService()));
            int state = newFromBundle.getState();
            riljLog("upDomesticInService state : " + Integer.toString(state));
            if (state == 0) {
                switch (this.mPhoneType) {
                    case 1:
                        riljLog("upDomesticInService PhoneType : GSM_PHONE");
                        SystemProperties.set("gsm.domesticinservice", String.valueOf(new TelephonyManager(context).getNetworkCountryIso().equals("jp")));
                        break;
                    case 2:
                        riljLog("upDomesticInService PhoneType : CDMA_PHONE");
                        SystemProperties.set("gsm.domesticinservice", String.valueOf(!new TelephonyManager(context).isNetworkRoaming()));
                        break;
                    default:
                        riljLog("upDomesticInService PhoneType : Outside the scope PhoneType: " + Integer.toString(this.mPhoneType));
                        break;
                }
                riljLog("upDomesticInService gsm.domesticinservice after: " + String.valueOf(isDomesticInService()));
            }
        }
        riljLog("upDomesticInService end ");
    }

    private void updateScreenState() {
        int i = this.mDefaultDisplayState;
        this.mDefaultDisplayState = this.mDefaultDisplay.getState();
        if (this.mDefaultDisplayState == i) {
            return;
        }
        if (i != 2 && this.mDefaultDisplayState == 2) {
            sendScreenState(true);
        } else if ((i == 2 || i == 0) && this.mDefaultDisplayState != 2) {
            sendScreenState(false);
        }
    }

    private void validateMsl(byte[] bArr, Message message) {
        byte[] bArr2 = new byte[(this.mHeaderSize + 6)];
        ByteBuffer wrap = ByteBuffer.wrap(bArr2);
        wrap.order(ByteOrder.nativeOrder());
        wrap.put(OEM_IDENTIFIER.getBytes());
        wrap.putInt(591837);
        wrap.putInt(6);
        for (int i = 0; i < 6; i++) {
            wrap.put(bArr[i]);
        }
        invokeOemRilRequestRaw(bArr2, message);
        riljLog("[EXTDBG] validateMsl: " + IccUtils.bytesToHexString(bArr));
    }

    public void acceptCall(Message message) {
        RILRequest obtain = RILRequest.obtain(40, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void acknowledgeIncomingGsmSmsWithPdu(boolean z, String str, Message message) {
        RILRequest obtain = RILRequest.obtain(106, message);
        obtain.mParcel.writeInt(2);
        obtain.mParcel.writeString(z ? "1" : "0");
        obtain.mParcel.writeString(str);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + ' ' + z + " [" + str + ']');
        send(obtain);
    }

    public void acknowledgeLastIncomingCdmaSms(boolean z, int i, Message message) {
        RILRequest obtain = RILRequest.obtain(88, message);
        obtain.mParcel.writeInt(z ? 0 : 1);
        obtain.mParcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + z + " " + i);
        send(obtain);
    }

    public void acknowledgeLastIncomingGsmSms(boolean z, int i, Message message) {
        RILRequest obtain = RILRequest.obtain(37, message);
        obtain.mParcel.writeInt(2);
        obtain.mParcel.writeInt(z ? 1 : 0);
        obtain.mParcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + z + " " + i);
        send(obtain);
    }

    public void cancelPendingUssd(Message message) {
        RILRequest obtain = RILRequest.obtain(30, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void changeBarringPassword(String str, String str2, String str3, Message message) {
        RILRequest obtain = RILRequest.obtain(44, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        obtain.mParcel.writeInt(3);
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeString(str2);
        obtain.mParcel.writeString(str3);
        send(obtain);
    }

    public void changeIccPin(String str, String str2, Message message) {
        changeIccPinForApp(str, str2, null, message);
    }

    public void changeIccPin2(String str, String str2, Message message) {
        changeIccPin2ForApp(str, str2, null, message);
    }

    public void changeIccPin2ForApp(String str, String str2, String str3, Message message) {
        RILRequest obtain = RILRequest.obtain(7, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        obtain.mParcel.writeInt(3);
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeString(str2);
        obtain.mParcel.writeString(str3);
        send(obtain);
    }

    public void changeIccPinForApp(String str, String str2, String str3, Message message) {
        RILRequest obtain = RILRequest.obtain(6, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        obtain.mParcel.writeInt(3);
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeString(str2);
        obtain.mParcel.writeString(str3);
        send(obtain);
    }

    public void conference(Message message) {
        RILRequest obtain = RILRequest.obtain(16, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void deactivateDataCall(int i, int i2, Message message) {
        RILRequest obtain = RILRequest.obtain(41, message);
        obtain.mParcel.writeInt(2);
        obtain.mParcel.writeString(Integer.toString(i));
        obtain.mParcel.writeString(Integer.toString(i2));
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + i + " " + i2);
        send(obtain);
    }

    public void deleteSmsOnRuim(int i, Message message) {
        RILRequest obtain = RILRequest.obtain(97, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        send(obtain);
    }

    public void deleteSmsOnSim(int i, Message message) {
        RILRequest obtain = RILRequest.obtain(64, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        send(obtain);
    }

    public void dial(String str, int i, Message message) {
        dial(str, i, null, message);
    }

    public void dial(String str, int i, UUSInfo uUSInfo, Message message) {
        RILRequest obtain = RILRequest.obtain(10, message);
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeInt(i);
        if (uUSInfo == null) {
            obtain.mParcel.writeInt(0);
        } else {
            obtain.mParcel.writeInt(1);
            obtain.mParcel.writeInt(uUSInfo.getType());
            obtain.mParcel.writeInt(uUSInfo.getDcs());
            obtain.mParcel.writeByteArray(uUSInfo.getUserData());
        }
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("RIL: " + this);
        printWriter.println(" mSocket=" + this.mSocket);
        printWriter.println(" mSenderThread=" + this.mSenderThread);
        printWriter.println(" mSender=" + this.mSender);
        printWriter.println(" mReceiverThread=" + this.mReceiverThread);
        printWriter.println(" mReceiver=" + this.mReceiver);
        printWriter.println(" mWakeLock=" + this.mWakeLock);
        printWriter.println(" mWakeLockTimeout=" + this.mWakeLockTimeout);
        synchronized (this.mRequestList) {
            synchronized (this.mWakeLock) {
                printWriter.println(" mWakeLockCount=" + this.mWakeLockCount);
            }
            int size = this.mRequestList.size();
            printWriter.println(" mRequestList count=" + size);
            for (int i = 0; i < size; i++) {
                RILRequest rILRequest = (RILRequest) this.mRequestList.valueAt(i);
                printWriter.println("  [" + rILRequest.mSerial + "] " + requestToString(rILRequest.mRequest));
            }
        }
        printWriter.println(" mLastNITZTimeInfo=" + this.mLastNITZTimeInfo);
        printWriter.println(" mTestingEmergencyCall=" + this.mTestingEmergencyCall.get());
    }

    public void exitEmergencyCallbackMode(Message message) {
        RILRequest obtain = RILRequest.obtain(99, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void explicitCallTransfer(Message message) {
        RILRequest obtain = RILRequest.obtain(72, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getAtr(Message message) {
        RILRequest obtain = RILRequest.obtain(133, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(0);
        riljLog(obtain.serialString() + "> iccGetAtr: " + requestToString(obtain.mRequest) + " " + 0);
        send(obtain);
    }

    public void getAvailableNetworks(Message message) {
        RILRequest obtain = RILRequest.obtain(48, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        if (TelBrand.IS_DCM) {
            this.mSender.sendMessageDelayed(this.mSender.obtainMessage(4, obtain), SEARCH_NETWORK_DELAY_TIME);
            return;
        }
        send(obtain);
    }

    public void getBandPref(Message message) {
        byte[] bArr = new byte[(this.mHeaderSize + 0)];
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        wrap.order(ByteOrder.nativeOrder());
        wrap.put(OEM_IDENTIFIER.getBytes());
        wrap.putInt(591831);
        wrap.putInt(0);
        invokeOemRilRequestRaw(bArr, message);
        riljLog("[EXTDBG] getBandPref is called.");
    }

    public void getBasebandVersion(Message message) {
        RILRequest obtain = RILRequest.obtain(51, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getCDMASubscription(Message message) {
        RILRequest obtain = RILRequest.obtain(95, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getCLIR(Message message) {
        RILRequest obtain = RILRequest.obtain(31, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getCdmaBroadcastConfig(Message message) {
        send(RILRequest.obtain(92, message));
    }

    public void getCdmaSubscriptionSource(Message message) {
        RILRequest obtain = RILRequest.obtain(104, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getCellInfoList(Message message) {
        RILRequest obtain = RILRequest.obtain(109, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getCurrentCalls(Message message) {
        RILRequest obtain = RILRequest.obtain(9, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getDataCallList(Message message) {
        RILRequest obtain = RILRequest.obtain(57, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getDataCallProfile(int i, Message message) {
        RILRequest obtain = RILRequest.obtain(132, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " : " + i);
        send(obtain);
    }

    public void getDataRegistrationState(Message message) {
        RILRequest obtain = RILRequest.obtain(21, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getDeviceIdentity(Message message) {
        RILRequest obtain = RILRequest.obtain(98, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getGsmBroadcastConfig(Message message) {
        RILRequest obtain = RILRequest.obtain(89, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getHardwareConfig(Message message) {
        RILRequest obtain = RILRequest.obtain(124, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getIMEI(Message message) {
        RILRequest obtain = RILRequest.obtain(38, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getIMEISV(Message message) {
        RILRequest obtain = RILRequest.obtain(39, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getIMSI(Message message) {
        getIMSIForApp(null, message);
    }

    public void getIMSIForApp(String str, Message message) {
        RILRequest obtain = RILRequest.obtain(11, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeString(str);
        riljLog(obtain.serialString() + "> getIMSI: " + requestToString(obtain.mRequest) + " aid: " + str);
        send(obtain);
    }

    public void getIccCardStatus(Message message) {
        RILRequest obtain = RILRequest.obtain(1, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getImsRegistrationState(Message message) {
        RILRequest obtain = RILRequest.obtain(112, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getLastCallFailCause(Message message) {
        RILRequest obtain = RILRequest.obtain(18, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getLastDataCallFailCause(Message message) {
        RILRequest obtain = RILRequest.obtain(56, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    @Deprecated
    public void getLastPdpFailCause(Message message) {
        getLastDataCallFailCause(message);
    }

    public void getModemCapability(Message message) {
        Rlog.d(RILJ_LOG_TAG, "GetModemCapability");
        sendOemRilRequestRaw(OEMHOOK_EVT_HOOK_GET_MODEM_CAPABILITY, 0, null, message);
    }

    public void getMute(Message message) {
        RILRequest obtain = RILRequest.obtain(54, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getNeighboringCids(Message message) {
        RILRequest obtain = RILRequest.obtain(75, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getNetworkSelectionMode(Message message) {
        RILRequest obtain = RILRequest.obtain(45, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getOperator(Message message) {
        RILRequest obtain = RILRequest.obtain(22, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    @Deprecated
    public void getPDPContextList(Message message) {
        getDataCallList(message);
    }

    public void getPreferredNetworkType(Message message) {
        RILRequest obtain = RILRequest.obtain(74, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getPreferredNetworkTypeWithOptimizeSetting(Message message) {
        byte[] bArr = new byte[(this.mHeaderSize + 0)];
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        wrap.order(ByteOrder.nativeOrder());
        wrap.put(OEM_IDENTIFIER.getBytes());
        wrap.putInt(591826);
        wrap.putInt(0);
        invokeOemRilRequestRaw(bArr, message);
        riljLog("[EXTDBG] getPreferredNetworkTypeWithOptimizeSetting is called.");
    }

    public void getPreferredVoicePrivacy(Message message) {
        send(RILRequest.obtain(83, message));
    }

    public void getSignalStrength(Message message) {
        RILRequest obtain = RILRequest.obtain(19, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getSimLock(Message message) {
        byte[] bArr = new byte[(this.mHeaderSize + 0)];
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        wrap.order(ByteOrder.nativeOrder());
        wrap.put(OEM_IDENTIFIER.getBytes());
        wrap.putInt(592030);
        wrap.putInt(0);
        invokeOemRilRequestRaw(bArr, message);
        riljLog("[EXTDBG] getSimLock");
    }

    public void getSmscAddress(Message message) {
        RILRequest obtain = RILRequest.obtain(100, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getVoiceRadioTechnology(Message message) {
        RILRequest obtain = RILRequest.obtain(OEM_RIL_RDE_Data.RDE_NV_ROAMING_LIST_683_I, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void getVoiceRegistrationState(Message message) {
        RILRequest obtain = RILRequest.obtain(20, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void handleCallSetupRequestFromSim(boolean z, Message message) {
        RILRequest obtain = RILRequest.obtain(71, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        int i = z ? 1 : 0;
        obtain.mParcel.writeIntArray(new int[]{i});
        send(obtain);
    }

    public void hangupConnection(int i, Message message) {
        riljLog("hangupConnection: gsmIndex=" + i);
        RILRequest obtain = RILRequest.obtain(12, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + i);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        send(obtain);
    }

    public void hangupForegroundResumeBackground(Message message) {
        RILRequest obtain = RILRequest.obtain(14, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void hangupWaitingOrBackground(Message message) {
        RILRequest obtain = RILRequest.obtain(13, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void iccCloseChannel(int i, Message message) {
        RILRequest obtain = RILRequest.obtain(136, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        send(obtain);
    }

    public void iccCloseLogicalChannel(int i, Message message) {
        RILRequest obtain = RILRequest.obtain(116, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void iccExchangeAPDU(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message) {
        if (i3 < 0) {
            throw new RuntimeException("Invalid channel in iccTransmitApduLogicalChannel: " + i3);
        }
        RILRequest obtain = i3 == 0 ? RILRequest.obtain(134, message) : RILRequest.obtain(137, message);
        obtain.mParcel.writeInt(i3);
        obtain.mParcel.writeInt(i);
        obtain.mParcel.writeInt(i2);
        obtain.mParcel.writeInt(i4);
        obtain.mParcel.writeInt(i5);
        obtain.mParcel.writeInt(i6);
        obtain.mParcel.writeString(str);
        send(obtain);
    }

    public void iccIO(int i, int i2, String str, int i3, int i4, int i5, String str2, String str3, Message message) {
        iccIOForApp(i, i2, str, i3, i4, i5, str2, str3, null, message);
    }

    public void iccIOForApp(int i, int i2, String str, int i3, int i4, int i5, String str2, String str3, String str4, Message message) {
        RILRequest obtain = RILRequest.obtain(28, message);
        obtain.mParcel.writeInt(i);
        obtain.mParcel.writeInt(i2);
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeInt(i3);
        obtain.mParcel.writeInt(i4);
        obtain.mParcel.writeInt(i5);
        obtain.mParcel.writeString(str2);
        obtain.mParcel.writeString(str3);
        obtain.mParcel.writeString(str4);
        riljLog(obtain.serialString() + "> iccIO: " + requestToString(obtain.mRequest) + " 0x" + Integer.toHexString(i) + " 0x" + Integer.toHexString(i2) + " " + " path: " + str + "," + i3 + "," + i4 + "," + i5 + " aid: " + str4);
        send(obtain);
    }

    public void iccOpenChannel(String str, Message message) {
        RILRequest obtain = RILRequest.obtain(135, message);
        obtain.mParcel.writeString(str);
        send(obtain);
    }

    public void iccOpenLogicalChannel(String str, Message message) {
        RILRequest obtain = RILRequest.obtain(115, message);
        obtain.mParcel.writeString(str);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void iccTransmitApduBasicChannel(int i, int i2, int i3, int i4, int i5, String str, Message message) {
        iccTransmitApduHelper(114, 0, i, i2, i3, i4, i5, str, message);
    }

    public void iccTransmitApduLogicalChannel(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message) {
        if (i <= 0) {
            throw new RuntimeException("Invalid channel in iccTransmitApduLogicalChannel: " + i);
        }
        iccTransmitApduHelper(117, i, i2, i3, i4, i5, i6, str, message);
    }

    public void invokeOemRilRequestRaw(byte[] bArr, Message message) {
        if (!TelBrand.IS_SBM || !replaceInvokeOemRilRequestRaw(bArr, message)) {
            RILRequest obtain = RILRequest.obtain(59, message);
            riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + "[" + IccUtils.bytesToHexString(bArr) + "]");
            obtain.mParcel.writeByteArray(bArr);
            send(obtain);
        }
    }

    public void invokeOemRilRequestStrings(String[] strArr, Message message) {
        RILRequest obtain = RILRequest.obtain(60, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        obtain.mParcel.writeStringArray(strArr);
        send(obtain);
    }

    public boolean isRunning() {
        boolean z = this.mSenderThread != null && this.mSenderThread.isAlive();
        boolean z2 = this.mReceiverThread != null && this.mReceiverThread.isAlive();
        return z && z2;
    }

    /* Access modifiers changed, original: protected */
    public void notifyLteBandInfo(byte[] bArr) {
        if (this.mLteBandInfoRegistrant != null) {
            ByteBuffer wrap = ByteBuffer.wrap(bArr);
            wrap.order(ByteOrder.nativeOrder());
            this.mLteBandInfoRegistrant.notifyRegistrant(new AsyncResult(null, new Integer(wrap.getInt()), null));
        }
    }

    /* Access modifiers changed, original: protected */
    public void notifyModemCap(byte[] bArr, Integer num) {
        this.mModemCapRegistrants.notifyRegistrants(new AsyncResult(null, new UnsolOemHookBuffer(num.intValue(), bArr), null));
        Rlog.d(RILJ_LOG_TAG, "MODEM_CAPABILITY on phone=" + num + " notified to registrants");
    }

    /* Access modifiers changed, original: protected */
    public void notifyOemSignalStrength(byte[] bArr) {
        if (this.mOemSignalStrengthRegistrant != null) {
            this.mOemSignalStrengthRegistrant.notifyRegistrant(new AsyncResult(null, toIntArrayFromByteArray(bArr), null));
        }
    }

    /* Access modifiers changed, original: protected */
    public void notifySimRefresh(byte[] bArr) {
        byte b = (byte) 0;
        int length = bArr.length;
        byte[] bArr2 = new byte[(length + 1)];
        System.arraycopy(bArr, 0, bArr2, 0, length);
        if (this.mInstanceId != null) {
            b = (byte) (this.mInstanceId.intValue() & 255);
        }
        bArr2[length] = b;
        this.mSimRefreshRegistrants.notifyRegistrants(new AsyncResult(null, bArr2, null));
        Rlog.d(RILJ_LOG_TAG, "SIM_REFRESH notified to registrants");
    }

    /* Access modifiers changed, original: protected */
    public void notifySpeechCodec(byte[] bArr) {
        if (TelBrand.IS_SBM && this.mSpeechCodecRegistrant != null) {
            this.mSpeechCodecRegistrant.notifyRegistrant(new AsyncResult(null, toIntArrayFromByteArray(bArr), null));
        }
    }

    /* Access modifiers changed, original: protected */
    public void notifyWwanIwlanCoexist(byte[] bArr) {
        this.mWwanIwlanCoexistenceRegistrants.notifyRegistrants(new AsyncResult(null, bArr, null));
        Rlog.d(RILJ_LOG_TAG, "WWAN, IWLAN coexistence notified to registrants");
    }

    public void nvReadItem(int i, Message message) {
        RILRequest obtain = RILRequest.obtain(118, message);
        obtain.mParcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + ' ' + i);
        send(obtain);
    }

    public void nvResetConfig(int i, Message message) {
        RILRequest obtain = RILRequest.obtain(121, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + ' ' + i);
        send(obtain);
    }

    public void nvWriteCdmaPrl(byte[] bArr, Message message) {
        RILRequest obtain = RILRequest.obtain(120, message);
        obtain.mParcel.writeByteArray(bArr);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " (" + bArr.length + " bytes)");
        send(obtain);
    }

    public void nvWriteItem(int i, String str, Message message) {
        RILRequest obtain = RILRequest.obtain(119, message);
        obtain.mParcel.writeInt(i);
        obtain.mParcel.writeString(str);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + ' ' + i + ": " + str);
        send(obtain);
    }

    /* Access modifiers changed, original: protected */
    public void onRadioAvailable() {
        updateScreenState();
    }

    public void queryAvailableBandMode(Message message) {
        RILRequest obtain = RILRequest.obtain(66, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void queryCLIP(Message message) {
        RILRequest obtain = RILRequest.obtain(55, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void queryCallForwardStatus(int i, int i2, String str, Message message) {
        RILRequest obtain = RILRequest.obtain(33, message);
        obtain.mParcel.writeInt(2);
        obtain.mParcel.writeInt(i);
        obtain.mParcel.writeInt(i2);
        obtain.mParcel.writeInt(PhoneNumberUtils.toaFromString(str));
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeInt(0);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + i + " " + i2);
        send(obtain);
    }

    public void queryCallWaiting(int i, Message message) {
        RILRequest obtain = RILRequest.obtain(35, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + i);
        send(obtain);
    }

    public void queryCdmaRoamingPreference(Message message) {
        RILRequest obtain = RILRequest.obtain(79, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void queryFacilityLock(String str, String str2, int i, Message message) {
        queryFacilityLockForApp(str, str2, i, null, message);
    }

    public void queryFacilityLockForApp(String str, String str2, int i, String str3, Message message) {
        RILRequest obtain = RILRequest.obtain(42, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " [" + str + " " + i + " " + str3 + "]");
        obtain.mParcel.writeInt(4);
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeString(str2);
        obtain.mParcel.writeString(Integer.toString(i));
        obtain.mParcel.writeString(str3);
        send(obtain);
    }

    public void queryTTYMode(Message message) {
        RILRequest obtain = RILRequest.obtain(81, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void rejectCall(Message message) {
        RILRequest obtain = RILRequest.obtain(17, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public boolean replaceInvokeOemRilRequestRaw(byte[] bArr, Message message) {
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        wrap.order(ByteOrder.nativeOrder());
        byte[] bArr2 = new byte[OEM_IDENTIFIER.length()];
        wrap.get(bArr2);
        if (new String(bArr2).equals(OEM_IDENTIFIER)) {
            return false;
        }
        switch (wrap.getInt(0)) {
            case OemCdmaTelephonyManager.OEM_RIL_REQUEST_CDMA_CHECK_SUBSIDY_LOCK_PASSWD /*33554442*/:
                validateMsl(Arrays.copyOfRange(bArr, 18, 24), message);
                return true;
            default:
                return false;
        }
    }

    public void reportDecryptStatus(boolean z, Message message) {
        if (TelBrand.IS_DCM) {
            RILRequest obtain = RILRequest.obtain(102, message);
            obtain.mParcel.writeInt(1);
            obtain.mParcel.writeInt(z ? 3 : 2);
            riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + ": " + z);
            send(obtain);
        }
    }

    public void reportSmsMemoryStatus(boolean z, Message message) {
        int i = 1;
        RILRequest obtain = RILRequest.obtain(102, message);
        obtain.mParcel.writeInt(1);
        Parcel parcel = obtain.mParcel;
        if (!z) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + ": " + z);
        send(obtain);
    }

    public void reportStkServiceIsRunning(Message message) {
        RILRequest obtain = RILRequest.obtain(103, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void requestIccSimAuthentication(int i, String str, String str2, Message message) {
        RILRequest obtain = RILRequest.obtain(125, message);
        obtain.mParcel.writeInt(i);
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeString(str2);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void requestIsimAuthentication(String str, Message message) {
        RILRequest obtain = RILRequest.obtain(105, message);
        obtain.mParcel.writeString(str);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void requestShutdown(Message message) {
        RILRequest obtain = RILRequest.obtain(129, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void resetRadio(Message message) {
        RILRequest obtain = RILRequest.obtain(58, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void sendBurstDtmf(String str, int i, int i2, Message message) {
        RILRequest obtain = RILRequest.obtain(85, message);
        obtain.mParcel.writeInt(3);
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeString(Integer.toString(i));
        obtain.mParcel.writeString(Integer.toString(i2));
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " : " + str);
        send(obtain);
    }

    public void sendCDMAFeatureCode(String str, Message message) {
        RILRequest obtain = RILRequest.obtain(84, message);
        obtain.mParcel.writeString(str);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " : " + str);
        send(obtain);
    }

    public void sendCdmaSms(byte[] bArr, Message message) {
        RILRequest obtain = RILRequest.obtain(87, message);
        constructCdmaSendSmsRilRequest(obtain, bArr);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void sendDtmf(char c, Message message) {
        RILRequest obtain = RILRequest.obtain(24, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        obtain.mParcel.writeString(Character.toString(c));
        send(obtain);
    }

    public void sendEnvelope(String str, Message message) {
        RILRequest obtain = RILRequest.obtain(69, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        obtain.mParcel.writeString(str);
        send(obtain);
    }

    public void sendEnvelopeWithStatus(String str, Message message) {
        RILRequest obtain = RILRequest.obtain(107, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + '[' + str + ']');
        obtain.mParcel.writeString(str);
        send(obtain);
    }

    public void sendImsCdmaSms(byte[] bArr, int i, int i2, Message message) {
        RILRequest obtain = RILRequest.obtain(113, message);
        obtain.mParcel.writeInt(2);
        obtain.mParcel.writeByte((byte) i);
        obtain.mParcel.writeInt(i2);
        constructCdmaSendSmsRilRequest(obtain, bArr);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void sendImsGsmSms(String str, String str2, int i, int i2, Message message) {
        RILRequest obtain = RILRequest.obtain(113, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeByte((byte) i);
        obtain.mParcel.writeInt(i2);
        constructGsmSendSmsRilRequest(obtain, str, str2);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void sendSMS(String str, String str2, Message message) {
        RILRequest obtain = RILRequest.obtain(25, message);
        constructGsmSendSmsRilRequest(obtain, str, str2);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void sendSMSExpectMore(String str, String str2, Message message) {
        RILRequest obtain = RILRequest.obtain(26, message);
        constructGsmSendSmsRilRequest(obtain, str, str2);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void sendTerminalResponse(String str, Message message) {
        RILRequest obtain = RILRequest.obtain(70, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        obtain.mParcel.writeString(str);
        send(obtain);
    }

    public void sendUSSD(String str, Message message) {
        RILRequest obtain = RILRequest.obtain(29, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + "*******");
        obtain.mParcel.writeString(str);
        send(obtain);
    }

    public void separateConnection(int i, Message message) {
        RILRequest obtain = RILRequest.obtain(52, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + i);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        send(obtain);
    }

    public void setBandMode(int i, Message message) {
        RILRequest obtain = RILRequest.obtain(65, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + i);
        send(obtain);
    }

    public void setBandPref(long j, int i, Message message) {
        byte[] bArr = new byte[(this.mHeaderSize + 12)];
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        wrap.order(ByteOrder.nativeOrder());
        wrap.put(OEM_IDENTIFIER.getBytes());
        wrap.putInt(591830);
        wrap.putInt(12);
        wrap.putLong(j);
        wrap.putInt(i);
        invokeOemRilRequestRaw(bArr, message);
        riljLog("[EXTDBG] setBandPref lteBand: " + j + " wcdmaBand: " + i);
    }

    public void setCLIR(int i, Message message) {
        RILRequest obtain = RILRequest.obtain(32, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + i);
        send(obtain);
    }

    public void setCallForward(int i, int i2, int i3, String str, int i4, Message message) {
        RILRequest obtain = RILRequest.obtain(34, message);
        obtain.mParcel.writeInt(i);
        obtain.mParcel.writeInt(i2);
        obtain.mParcel.writeInt(i3);
        obtain.mParcel.writeInt(PhoneNumberUtils.toaFromString(str));
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeInt(i4);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + i + " " + i2 + " " + i3 + i4);
        send(obtain);
    }

    public void setCallWaiting(boolean z, int i, Message message) {
        RILRequest obtain = RILRequest.obtain(36, message);
        obtain.mParcel.writeInt(2);
        obtain.mParcel.writeInt(z ? 1 : 0);
        obtain.mParcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + z + ", " + i);
        send(obtain);
    }

    public void setCdmaBroadcastActivation(boolean z, Message message) {
        int i = 1;
        RILRequest obtain = RILRequest.obtain(94, message);
        obtain.mParcel.writeInt(1);
        Parcel parcel = obtain.mParcel;
        if (z) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] cdmaSmsBroadcastConfigInfoArr, Message message) {
        int i = 0;
        RILRequest obtain = RILRequest.obtain(93, message);
        ArrayList arrayList = new ArrayList();
        for (CdmaSmsBroadcastConfigInfo cdmaSmsBroadcastConfigInfo : cdmaSmsBroadcastConfigInfoArr) {
            for (int fromServiceCategory = cdmaSmsBroadcastConfigInfo.getFromServiceCategory(); fromServiceCategory <= cdmaSmsBroadcastConfigInfo.getToServiceCategory(); fromServiceCategory++) {
                arrayList.add(new CdmaSmsBroadcastConfigInfo(fromServiceCategory, fromServiceCategory, cdmaSmsBroadcastConfigInfo.getLanguage(), cdmaSmsBroadcastConfigInfo.isSelected()));
            }
        }
        CdmaSmsBroadcastConfigInfo[] cdmaSmsBroadcastConfigInfoArr2 = (CdmaSmsBroadcastConfigInfo[]) arrayList.toArray(cdmaSmsBroadcastConfigInfoArr);
        obtain.mParcel.writeInt(cdmaSmsBroadcastConfigInfoArr2.length);
        for (int i2 = 0; i2 < cdmaSmsBroadcastConfigInfoArr2.length; i2++) {
            obtain.mParcel.writeInt(cdmaSmsBroadcastConfigInfoArr2[i2].getFromServiceCategory());
            obtain.mParcel.writeInt(cdmaSmsBroadcastConfigInfoArr2[i2].getLanguage());
            obtain.mParcel.writeInt(cdmaSmsBroadcastConfigInfoArr2[i2].isSelected() ? 1 : 0);
        }
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " with " + cdmaSmsBroadcastConfigInfoArr2.length + " configs : ");
        while (i < cdmaSmsBroadcastConfigInfoArr2.length) {
            riljLog(cdmaSmsBroadcastConfigInfoArr2[i].toString());
            i++;
        }
        send(obtain);
    }

    public void setCdmaRoamingPreference(int i, Message message) {
        RILRequest obtain = RILRequest.obtain(78, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " : " + i);
        send(obtain);
    }

    public void setCdmaSubscriptionSource(int i, Message message) {
        RILRequest obtain = RILRequest.obtain(77, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " : " + i);
        send(obtain);
    }

    public void setCellInfoListRate(int i, Message message) {
        riljLog("setCellInfoListRate: " + i);
        RILRequest obtain = RILRequest.obtain(110, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void setDataAllowed(boolean z, Message message) {
        int i = 1;
        RILRequest obtain = RILRequest.obtain(123, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + z);
        obtain.mParcel.writeInt(1);
        Parcel parcel = obtain.mParcel;
        if (!z) {
            i = 0;
        }
        parcel.writeInt(i);
        send(obtain);
    }

    public void setDataProfile(DataProfile[] dataProfileArr, Message message) {
        riljLog("Set RIL_REQUEST_SET_DATA_PROFILE");
        RILRequest obtain = RILRequest.obtain(128, null);
        DataProfile.toParcel(obtain.mParcel, dataProfileArr);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " with " + dataProfileArr + " Data Profiles : ");
        for (DataProfile dataProfile : dataProfileArr) {
            riljLog(dataProfile.toString());
        }
        send(obtain);
    }

    public void setFacilityLock(String str, boolean z, String str2, int i, Message message) {
        setFacilityLockForApp(str, z, str2, i, null, message);
    }

    public void setFacilityLockForApp(String str, boolean z, String str2, int i, String str3, Message message) {
        RILRequest obtain = RILRequest.obtain(43, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " [" + str + " " + z + " " + i + " " + str3 + "]");
        obtain.mParcel.writeInt(5);
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeString(z ? "1" : "0");
        obtain.mParcel.writeString(str2);
        obtain.mParcel.writeString(Integer.toString(i));
        obtain.mParcel.writeString(str3);
        send(obtain);
    }

    public void setGsmBroadcastActivation(boolean z, Message message) {
        int i = 1;
        RILRequest obtain = RILRequest.obtain(91, message);
        obtain.mParcel.writeInt(1);
        Parcel parcel = obtain.mParcel;
        if (z) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] smsBroadcastConfigInfoArr, Message message) {
        int i = 0;
        RILRequest obtain = RILRequest.obtain(90, message);
        int length = smsBroadcastConfigInfoArr.length;
        obtain.mParcel.writeInt(length);
        for (int i2 = 0; i2 < length; i2++) {
            obtain.mParcel.writeInt(smsBroadcastConfigInfoArr[i2].getFromServiceId());
            obtain.mParcel.writeInt(smsBroadcastConfigInfoArr[i2].getToServiceId());
            obtain.mParcel.writeInt(smsBroadcastConfigInfoArr[i2].getFromCodeScheme());
            obtain.mParcel.writeInt(smsBroadcastConfigInfoArr[i2].getToCodeScheme());
            obtain.mParcel.writeInt(smsBroadcastConfigInfoArr[i2].isSelected() ? 1 : 0);
        }
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " with " + length + " configs : ");
        while (i < length) {
            riljLog(smsBroadcastConfigInfoArr[i].toString());
            i++;
        }
        send(obtain);
    }

    public void setInitialAttachApn(String str, String str2, int i, String str3, String str4, Message message) {
        RILRequest obtain = RILRequest.obtain(111, message);
        riljLog("Set RIL_REQUEST_SET_INITIAL_ATTACH_APN");
        if (i == -1) {
            i = TextUtils.isEmpty(str3) ? 0 : 3;
        }
        this.mSetInitialAttachApn_Apn = str;
        this.mSetInitialAttachApn_Protocol = str2;
        this.mSetInitialAttachApn_AuthType = i;
        this.mSetInitialAttachApn_Username = str3;
        this.mSetInitialAttachApn_Password = str4;
        this.mRILConnected_SetInitialAttachApn_OnceSkip = false;
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeString(str2);
        obtain.mParcel.writeInt(i);
        obtain.mParcel.writeString(str3);
        obtain.mParcel.writeString(str4);
        if (!"eng".equals(Build.TYPE)) {
            if (str != null) {
                str = "*****";
            }
            if (str3 != null) {
                str3 = "*****";
            }
            if (str4 != null) {
                str4 = "*****";
            }
        }
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + ", apn:" + str + ", protocol:" + str2 + ", authType:" + i + ", username:" + str3 + ", password:" + str4);
        send(obtain);
    }

    public void setLimitationByChameleon(boolean z, Message message) {
        this.mSetLimitationByChameleon = true;
        this.mLimitationByChameleon = z;
        this.mLimitedService = this.mLimitationByChameleon;
        setLimitedService(message);
        riljLog("[EXTDBG] setLimitationByChameleon: " + this.mLimitationByChameleon);
    }

    public void setLocalCallHold(int i) {
        byte b = (byte) (i & 127);
        Rlog.d(RILJ_LOG_TAG, "setLocalCallHold: lchStatus is " + i);
        sendOemRilRequestRaw(OEMHOOK_EVT_HOOK_SET_LOCAL_CALL_HOLD, 1, new byte[]{b}, null);
    }

    public void setLocationUpdates(boolean z, Message message) {
        int i = 1;
        RILRequest obtain = RILRequest.obtain(76, message);
        obtain.mParcel.writeInt(1);
        Parcel parcel = obtain.mParcel;
        if (!z) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + ": " + z);
        send(obtain);
    }

    public void setModemSettingsByChameleon(int i, Message message) {
        byte[] bArr = new byte[(this.mHeaderSize + 4)];
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        wrap.order(ByteOrder.nativeOrder());
        wrap.put(OEM_IDENTIFIER.getBytes());
        wrap.putInt(591838);
        wrap.putInt(4);
        wrap.putInt(i);
        invokeOemRilRequestRaw(bArr, message);
        riljLog("[EXTDBG] pattern: " + i);
    }

    public void setMute(boolean z, Message message) {
        int i = 1;
        RILRequest obtain = RILRequest.obtain(53, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + z);
        obtain.mParcel.writeInt(1);
        Parcel parcel = obtain.mParcel;
        if (!z) {
            i = 0;
        }
        parcel.writeInt(i);
        send(obtain);
    }

    public void setNetworkSelectionModeAutomatic(Message message) {
        RILRequest obtain = RILRequest.obtain(46, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        if (TelBrand.IS_DCM && LOG_NS) {
            RILRadioLogWriter rILRadioLogWriter = new RILRadioLogWriter(2, 0);
            rILRadioLogWriter.setStackTrace();
            Thread thread = new Thread(rILRadioLogWriter);
            if (thread != null) {
                thread.start();
            }
            rILRadioLogWriter = new RILRadioLogWriter(2, 2);
            rILRadioLogWriter.setLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
            thread = new Thread(rILRadioLogWriter);
            if (thread != null) {
                thread.start();
            }
        }
        send(obtain);
    }

    public void setNetworkSelectionModeManual(String str, Message message) {
        RILRequest obtain = RILRequest.obtain(47, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + str);
        obtain.mParcel.writeString(str);
        if (TelBrand.IS_DCM && LOG_NS) {
            RILRadioLogWriter rILRadioLogWriter = new RILRadioLogWriter(2, 0);
            rILRadioLogWriter.setStackTrace();
            Thread thread = new Thread(rILRadioLogWriter);
            if (thread != null) {
                thread.start();
            }
            rILRadioLogWriter = new RILRadioLogWriter(2, 2);
            rILRadioLogWriter.setLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + str);
            thread = new Thread(rILRadioLogWriter);
            if (thread != null) {
                thread.start();
            }
        }
        send(obtain);
    }

    public void setOnNITZTime(Handler handler, int i, Object obj) {
        super.setOnNITZTime(handler, i, obj);
        if (this.mLastNITZTimeInfo != null) {
            this.mNITZTimeRegistrant.notifyRegistrant(new AsyncResult(null, this.mLastNITZTimeInfo, null));
        }
    }

    public void setPhoneType(int i) {
        riljLog("setPhoneType=" + i + " old value=" + this.mPhoneType);
        this.mPhoneType = i;
    }

    public void setPreferredNetworkType(int i, Message message) {
        RILRequest obtain = RILRequest.obtain(73, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        this.mPreferredNetworkType = i;
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " : " + i);
        if (TelBrand.IS_DCM && LOG_NW) {
            RILRadioLogWriter rILRadioLogWriter = new RILRadioLogWriter(0, 0);
            rILRadioLogWriter.setStackTrace();
            Thread thread = new Thread(rILRadioLogWriter);
            if (thread != null) {
                thread.start();
            }
        }
        send(obtain);
    }

    public void setPreferredVoicePrivacy(boolean z, Message message) {
        int i = 1;
        RILRequest obtain = RILRequest.obtain(82, message);
        obtain.mParcel.writeInt(1);
        Parcel parcel = obtain.mParcel;
        if (!z) {
            i = 0;
        }
        parcel.writeInt(i);
        send(obtain);
    }

    public void setRadioPower(boolean z, Message message) {
        int i = 1;
        RILRequest obtain = RILRequest.obtain(23, message);
        obtain.mParcel.writeInt(1);
        Parcel parcel = obtain.mParcel;
        if (!z) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + (z ? " on" : " off"));
        send(obtain);
    }

    public void setRatModeOptimizeSetting(boolean z, Message message) {
        byte[] bArr = new byte[(this.mHeaderSize + 4)];
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        wrap.order(ByteOrder.nativeOrder());
        wrap.put(OEM_IDENTIFIER.getBytes());
        wrap.putInt(591827);
        wrap.putInt(4);
        wrap.putInt(z ? 1 : 0);
        invokeOemRilRequestRaw(bArr, message);
        riljLog("[EXTDBG] setRatModeOptimizeSetting: " + z);
    }

    public void setSmscAddress(String str, Message message) {
        RILRequest obtain = RILRequest.obtain(101, message);
        obtain.mParcel.writeString(str);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " : " + str);
        send(obtain);
    }

    public void setSuppServiceNotifications(boolean z, Message message) {
        int i = 1;
        RILRequest obtain = RILRequest.obtain(62, message);
        obtain.mParcel.writeInt(1);
        Parcel parcel = obtain.mParcel;
        if (!z) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void setTTYMode(int i, Message message) {
        RILRequest obtain = RILRequest.obtain(80, message);
        obtain.mParcel.writeInt(1);
        obtain.mParcel.writeInt(i);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " : " + i);
        send(obtain);
    }

    public void setUiccSubscription(int i, int i2, int i3, int i4, Message message) {
        RILRequest obtain = RILRequest.obtain(122, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " slot: " + i + " appIndex: " + i2 + " subId: " + i3 + " subStatus: " + i4);
        obtain.mParcel.writeInt(i);
        obtain.mParcel.writeInt(i2);
        obtain.mParcel.writeInt(i3);
        obtain.mParcel.writeInt(i4);
        send(obtain);
    }

    public void setupDataCall(String str, String str2, String str3, String str4, String str5, String str6, String str7, Message message) {
        RILRequest obtain = RILRequest.obtain(27, message);
        obtain.mParcel.writeInt(7);
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeString(str2);
        obtain.mParcel.writeString(str3);
        obtain.mParcel.writeString(str4);
        obtain.mParcel.writeString(str5);
        obtain.mParcel.writeString(str6);
        obtain.mParcel.writeString(str7);
        if (!"eng".equals(Build.TYPE)) {
            if (str3 != null) {
                str3 = "*****";
            }
            if (str4 != null) {
                str4 = "*****";
            }
            if (str5 != null) {
                str5 = "*****";
            }
        }
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " " + str + " " + str2 + " " + str3 + " " + str4 + " " + str5 + " " + str6 + " " + str7);
        send(obtain);
    }

    public void startDtmf(char c, Message message) {
        RILRequest obtain = RILRequest.obtain(49, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        obtain.mParcel.writeString(Character.toString(c));
        send(obtain);
    }

    public void stopDtmf(Message message) {
        RILRequest obtain = RILRequest.obtain(50, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void supplyDepersonalization(String str, String str2, Message message) {
        RILRequest obtain = RILRequest.obtain(8, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + " Type:" + str2);
        obtain.mParcel.writeInt(2);
        obtain.mParcel.writeString(str2);
        obtain.mParcel.writeString(str);
        send(obtain);
    }

    public void supplyIccPin(String str, Message message) {
        supplyIccPinForApp(str, null, message);
    }

    public void supplyIccPin2(String str, Message message) {
        supplyIccPin2ForApp(str, null, message);
    }

    public void supplyIccPin2ForApp(String str, String str2, Message message) {
        RILRequest obtain = RILRequest.obtain(4, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        obtain.mParcel.writeInt(2);
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeString(str2);
        send(obtain);
    }

    public void supplyIccPinForApp(String str, String str2, Message message) {
        RILRequest obtain = RILRequest.obtain(2, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        obtain.mParcel.writeInt(2);
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeString(str2);
        send(obtain);
    }

    public void supplyIccPuk(String str, String str2, Message message) {
        supplyIccPukForApp(str, str2, null, message);
    }

    public void supplyIccPuk2(String str, String str2, Message message) {
        supplyIccPuk2ForApp(str, str2, null, message);
    }

    public void supplyIccPuk2ForApp(String str, String str2, String str3, Message message) {
        RILRequest obtain = RILRequest.obtain(5, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        obtain.mParcel.writeInt(3);
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeString(str2);
        obtain.mParcel.writeString(str3);
        send(obtain);
    }

    public void supplyIccPukForApp(String str, String str2, String str3, Message message) {
        RILRequest obtain = RILRequest.obtain(3, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        obtain.mParcel.writeInt(3);
        obtain.mParcel.writeString(str);
        obtain.mParcel.writeString(str2);
        obtain.mParcel.writeString(str3);
        send(obtain);
    }

    public void switchWaitingOrHoldingAndActive(Message message) {
        RILRequest obtain = RILRequest.obtain(15, message);
        riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest));
        send(obtain);
    }

    public void testingEmergencyCall() {
        riljLog("testingEmergencyCall");
        this.mTestingEmergencyCall.set(true);
    }

    public void updateOemDataSettings(boolean z, boolean z2, boolean z3, Message message) {
        riljLog("[EXTDBG] RIL_REQUEST_OEM_HOOK_RAW_UPDATE_LTE_APN > " + z + " " + z2);
        this.mRILConnected_UpdateOemDataSettings_OnceSkip = false;
        this.mUpdateOemDataSettings_MobileData = z;
        this.mUpdateOemDataSettings_DataRoaming = z2;
        byte[] bArr = new byte[(this.mHeaderSize + 3)];
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        wrap.order(ByteOrder.nativeOrder());
        wrap.put(OEM_IDENTIFIER.getBytes());
        wrap.putInt(591828);
        wrap.putInt(3);
        if (z) {
            wrap.put((byte) 1);
        } else {
            wrap.put((byte) 0);
        }
        if (z2) {
            wrap.put((byte) 1);
        } else {
            wrap.put((byte) 0);
        }
        if (z3) {
            wrap.put((byte) 1);
        } else {
            wrap.put((byte) 0);
        }
        invokeOemRilRequestRaw(bArr, message);
        riljLog("[EXTDBG] updateOemDataSettings is called.");
    }

    public void updateStackBinding(int i, int i2, Message message) {
        byte b = (byte) i;
        byte b2 = (byte) i2;
        Rlog.d(RILJ_LOG_TAG, "UpdateStackBinding: on Stack: " + i + ", enable/disable: " + i2);
        sendOemRilRequestRaw(OEMHOOK_EVT_HOOK_UPDATE_SUB_BINDING, 2, new byte[]{b, b2}, message);
    }

    public void writeSmsToRuim(int i, String str, Message message) {
        int translateStatus = translateStatus(i);
        RILRequest obtain = RILRequest.obtain(96, message);
        obtain.mParcel.writeInt(translateStatus);
        constructCdmaWriteSmsRilRequest(obtain, IccUtils.hexStringToBytes(str));
        send(obtain);
    }

    public void writeSmsToSim(int i, String str, String str2, Message message) {
        int translateStatus = translateStatus(i);
        RILRequest obtain = RILRequest.obtain(63, message);
        obtain.mParcel.writeInt(translateStatus);
        obtain.mParcel.writeString(str2);
        obtain.mParcel.writeString(str);
        send(obtain);
    }
}
