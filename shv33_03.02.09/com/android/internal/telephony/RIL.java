package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.net.ConnectivityManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.LocalSocketAddress.Namespace;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings.Global;
import android.telephony.CellInfo;
import android.telephony.ModemActivityInfo;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneNumberUtils;
import android.telephony.RadioAccessFamily;
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
import com.android.internal.telephony.dataconnection.DataCallResponse;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.dataconnection.DcFailCause;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SmsCbConstants;
import com.android.internal.telephony.gsm.SsData;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccRefreshResponse;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.SimPhoneBookAdnRecord;
import com.google.android.mms.pdu.CharacterSets;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import org.codeaurora.ims.QtiCallConstants;

public final class RIL extends BaseCommands implements CommandsInterface {
    private static final int CDMA_BROADCAST_SMS_NO_OF_SERVICE_CATEGORIES = 31;
    private static final int CDMA_BSI_NO_OF_INTS_STRUCT = 3;
    private static final int DEFAULT_ACK_WAKE_LOCK_TIMEOUT_MS = 200;
    private static final int DEFAULT_BLOCKING_MESSAGE_RESPONSE_TIMEOUT_MS = 2000;
    private static final int DEFAULT_WAKE_LOCK_TIMEOUT_MS = 60000;
    static final int EVENT_ACK_WAKE_LOCK_TIMEOUT = 4;
    static final int EVENT_BLOCKING_RESPONSE_TIMEOUT = 5;
    static final int EVENT_SEARCHING_NETWORK_DELAY = 7;
    static final int EVENT_SEND = 1;
    static final int EVENT_SEND_ACK = 3;
    static final int EVENT_WAKE_LOCK_TIMEOUT = 2;
    public static final int FOR_ACK_WAKELOCK = 1;
    public static final int FOR_WAKELOCK = 0;
    private static final int INT_SIZE = 4;
    public static final int INVALID_WAKELOCK = -1;
    static final boolean LOG_NS = LOG_NW;
    static final boolean LOG_NW = SystemProperties.getBoolean("persist.sharp.telephony.nvlognw", false);
    static final boolean LOG_SD = SystemProperties.getBoolean("persist.sharp.telephony.nvlogsd", false);
    private static final String OEM_IDENTIFIER = "QOEMHOOK";
    static final int RADIO_SCREEN_OFF = 0;
    static final int RADIO_SCREEN_ON = 1;
    static final int RADIO_SCREEN_UNSET = -1;
    static final int RESPONSE_SOLICITED = 0;
    static final int RESPONSE_SOLICITED_ACK = 2;
    static final int RESPONSE_SOLICITED_ACK_EXP = 3;
    static final int RESPONSE_UNSOLICITED = 1;
    static final int RESPONSE_UNSOLICITED_ACK_EXP = 4;
    static final String RILJ_ACK_WAKELOCK_NAME = "RILJ_ACK_WL";
    static final boolean RILJ_LOGD = true;
    static final boolean RILJ_LOGV = false;
    static final String RILJ_LOG_TAG = "RILJ";
    static final int RIL_MAX_COMMAND_BYTES = 8192;
    static final long SEARCH_NETWORK_DELAY_TIME = 3000;
    private static final boolean SMARTCARD_DBG = false;
    static final String[] SOCKET_NAME_RIL = new String[]{"rild", "rild2", "rild3"};
    static final int SOCKET_OPEN_RETRY_MILLIS = 4000;
    final WakeLock mAckWakeLock;
    final int mAckWakeLockTimeout;
    volatile int mAckWlSequenceNum;
    private final BroadcastReceiver mBatteryStateListener;
    Display mDefaultDisplay;
    int mDefaultDisplayState;
    private final DisplayListener mDisplayListener;
    private TelephonyEventLog mEventLog;
    int mHeaderSize;
    private Integer mInstanceId;
    BroadcastReceiver mIntentReceiver;
    boolean mIsDevicePlugged;
    Object[] mLastNITZTimeInfo;
    private boolean mRILConnected_SetInitialAttachApn_OnceSkip;
    private boolean mRILConnected_UpdateOemDataSettings_OnceSkip;
    int mRadioScreenState;
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
    LocalSocket mSocket;
    AtomicBoolean mTestingEmergencyCall;
    private boolean mUpdateOemDataSettings_DataRoaming;
    private boolean mUpdateOemDataSettings_MobileData;
    final WakeLock mWakeLock;
    int mWakeLockCount;
    final int mWakeLockTimeout;
    volatile int mWlSequenceNum;

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

        public RILRadioLogWriter(int rectype, int buftype) {
            if (TelBrand.IS_DCM) {
                if (rectype == 0) {
                    this.mRecType = 0;
                    this.mWriteSize = 8192;
                } else if (rectype == 2) {
                    this.mRecType = 2;
                    this.mWriteSize = 8192;
                } else {
                    this.mRecType = 1;
                    this.mWriteSize = RADIO_LOG_REC_SIZE_TYPE2;
                }
                if (buftype == 0) {
                    this.mBufType = 0;
                } else if (buftype == 2) {
                    this.mBufType = 2;
                } else {
                    this.mBufType = 1;
                }
            }
        }

        public void setStackTrace() {
            if (TelBrand.IS_DCM) {
                this.mStackTrace = Log.getStackTraceString(new Throwable());
            }
        }

        public void setLog(String log) {
            if (TelBrand.IS_DCM) {
                this.mAppendString = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS").format(new Date()) + " : " + log + "\n";
            }
        }

        /* JADX WARNING: Unknown top exception splitter block from list: {B:41:0x0159=Splitter:B:41:0x0159, B:49:0x01ba=Splitter:B:49:0x01ba} */
        /* JADX WARNING: Unknown top exception splitter block from list: {B:174:0x063a=Splitter:B:174:0x063a, B:182:0x067d=Splitter:B:182:0x067d} */
        /* JADX WARNING: Unknown top exception splitter block from list: {B:67:0x026a=Splitter:B:67:0x026a, B:81:0x02ef=Splitter:B:81:0x02ef} */
        /* JADX WARNING: Removed duplicated region for block: B:27:0x00dd  */
        /* JADX WARNING: Removed duplicated region for block: B:34:0x00f7 A:{SYNTHETIC, Splitter:B:34:0x00f7} */
        /* JADX WARNING: Removed duplicated region for block: B:27:0x00dd  */
        /* JADX WARNING: Removed duplicated region for block: B:34:0x00f7 A:{SYNTHETIC, Splitter:B:34:0x00f7} */
        /* JADX WARNING: Removed duplicated region for block: B:44:0x0186 A:{SYNTHETIC, Splitter:B:44:0x0186} */
        /* JADX WARNING: Removed duplicated region for block: B:27:0x00dd  */
        /* JADX WARNING: Removed duplicated region for block: B:34:0x00f7 A:{SYNTHETIC, Splitter:B:34:0x00f7} */
        /* JADX WARNING: Removed duplicated region for block: B:84:0x031c A:{SYNTHETIC, Splitter:B:84:0x031c} */
        /* JADX WARNING: Removed duplicated region for block: B:95:0x0382 A:{SYNTHETIC, Splitter:B:95:0x0382} */
        /* JADX WARNING: Removed duplicated region for block: B:124:0x04cf A:{SYNTHETIC, Splitter:B:124:0x04cf} */
        /* JADX WARNING: Removed duplicated region for block: B:133:0x050a A:{SYNTHETIC, Splitter:B:133:0x050a} */
        /* JADX WARNING: Removed duplicated region for block: B:163:0x05f7 A:{SYNTHETIC, Splitter:B:163:0x05f7} */
        /* JADX WARNING: Removed duplicated region for block: B:166:0x05fc  */
        /* JADX WARNING: Removed duplicated region for block: B:185:0x069b A:{SYNTHETIC, Splitter:B:185:0x069b} */
        /* JADX WARNING: Removed duplicated region for block: B:191:0x06c2 A:{SYNTHETIC, Splitter:B:191:0x06c2} */
        /* JADX WARNING: Removed duplicated region for block: B:58:0x021d A:{SYNTHETIC, Splitter:B:58:0x021d} */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void run() {
            FileNotFoundException e;
            IOException e2;
            FileOutputStream fileOutputStream;
            FileOutputStream fileOutputStream2;
            Throwable th;
            FileOutputStream writer;
            String s;
            OutputStream writer2;
            OutputStream writer3;
            if (TelBrand.IS_DCM) {
                long freeMemory = Runtime.getRuntime().freeMemory();
                if (freeMemory <= 524288) {
                    Rlog.e(RIL.RILJ_LOG_TAG, "Deficient freeMemory : " + freeMemory);
                    return;
                }
                String RADIO_LOG_FILEPATH = "/durable/tmp/";
                String RADIO_LOG_FILEFORMAT = "0000";
                String RADIO_LOG_SEPARATOR_LINE = "--- Stack Trace Information ---\n";
                byte[] buf = new byte[262144];
                int readCnt;
                if (buf == null) {
                    Rlog.e(RIL.RILJ_LOG_TAG, "logcat buffer alloc err");
                } else if (this.mAppendString != null) {
                    String fname = getPrefix(this.mRecType, this.mBufType);
                    byte[] appendLog = this.mAppendString.getBytes();
                    int appendLogSize = appendLog.length;
                    FileInputStream fis = null;
                    readCnt = 0;
                    try {
                        FileInputStream fileInputStream = new FileInputStream("/durable/tmp/" + fname);
                        if (fileInputStream != null) {
                            try {
                                readCnt = fileInputStream.read(buf, 0, 262144);
                            } catch (FileNotFoundException e3) {
                                e = e3;
                                fis = fileInputStream;
                            } catch (IOException e4) {
                                e2 = e4;
                                fis = fileInputStream;
                                try {
                                    Rlog.e(RIL.RILJ_LOG_TAG, "/durable/tmp/" + fname + " read err:" + e2);
                                    if (fis != null) {
                                    }
                                    fileOutputStream = null;
                                    fileOutputStream2 = new FileOutputStream("/durable/tmp/" + fname);
                                    if (fileOutputStream2 != null) {
                                    }
                                    if (fileOutputStream2 != null) {
                                    }
                                } catch (Throwable th2) {
                                    th = th2;
                                    if (fis != null) {
                                    }
                                    throw th;
                                }
                            } catch (Throwable th3) {
                                th = th3;
                                fis = fileInputStream;
                                if (fis != null) {
                                    try {
                                        fis.close();
                                    } catch (IOException e22) {
                                        Rlog.e(RIL.RILJ_LOG_TAG, "/durable/tmp/" + fname + " close err:" + e22);
                                    }
                                }
                                throw th;
                            }
                        }
                        if (fileInputStream != null) {
                            try {
                                fileInputStream.close();
                            } catch (IOException e222) {
                                Rlog.e(RIL.RILJ_LOG_TAG, "/durable/tmp/" + fname + " close err:" + e222);
                            }
                        }
                        fis = fileInputStream;
                    } catch (FileNotFoundException e5) {
                        e = e5;
                        Rlog.w(RIL.RILJ_LOG_TAG, "/durable/tmp/" + fname + " open err:" + e);
                        if (fis != null) {
                            try {
                                fis.close();
                            } catch (IOException e2222) {
                                Rlog.e(RIL.RILJ_LOG_TAG, "/durable/tmp/" + fname + " close err:" + e2222);
                            }
                        }
                        fileOutputStream = null;
                        fileOutputStream2 = new FileOutputStream("/durable/tmp/" + fname);
                        if (fileOutputStream2 != null) {
                        }
                        if (fileOutputStream2 != null) {
                        }
                    } catch (IOException e6) {
                        e2222 = e6;
                        Rlog.e(RIL.RILJ_LOG_TAG, "/durable/tmp/" + fname + " read err:" + e2222);
                        if (fis != null) {
                            try {
                                fis.close();
                            } catch (IOException e22222) {
                                Rlog.e(RIL.RILJ_LOG_TAG, "/durable/tmp/" + fname + " close err:" + e22222);
                            }
                        }
                        fileOutputStream = null;
                        fileOutputStream2 = new FileOutputStream("/durable/tmp/" + fname);
                        if (fileOutputStream2 != null) {
                        }
                        if (fileOutputStream2 != null) {
                        }
                    }
                    fileOutputStream = null;
                    try {
                        fileOutputStream2 = new FileOutputStream("/durable/tmp/" + fname);
                        if (fileOutputStream2 != null) {
                            if (readCnt <= 0) {
                                try {
                                    if (appendLogSize <= this.mWriteSize) {
                                        fileOutputStream2.write(appendLog, 0, appendLogSize);
                                    } else {
                                        fileOutputStream2.write(appendLog, appendLogSize - this.mWriteSize, this.mWriteSize);
                                    }
                                } catch (FileNotFoundException e7) {
                                    e = e7;
                                    fileOutputStream = fileOutputStream2;
                                } catch (IOException e8) {
                                    e22222 = e8;
                                    fileOutputStream = fileOutputStream2;
                                    Rlog.e(RIL.RILJ_LOG_TAG, "/durable/tmp/" + fname + " radiolog write err:" + e22222);
                                    if (fileOutputStream != null) {
                                    }
                                } catch (Throwable th4) {
                                    th = th4;
                                    fileOutputStream = fileOutputStream2;
                                    if (fileOutputStream != null) {
                                    }
                                    throw th;
                                }
                            }
                            int fileSize = readCnt + appendLogSize;
                            if (fileSize <= this.mWriteSize) {
                                fileOutputStream2.write(buf, 0, readCnt);
                                fileOutputStream2.write(appendLog, 0, appendLogSize);
                            } else if (appendLogSize < this.mWriteSize) {
                                fileOutputStream2.write(buf, fileSize - this.mWriteSize, this.mWriteSize - appendLogSize);
                                fileOutputStream2.write(appendLog, 0, appendLogSize);
                            } else {
                                fileOutputStream2.write(appendLog, appendLogSize - this.mWriteSize, this.mWriteSize);
                            }
                            fileOutputStream2.flush();
                        }
                        if (fileOutputStream2 != null) {
                            try {
                                fileOutputStream2.close();
                            } catch (IOException e222222) {
                                Rlog.e(RIL.RILJ_LOG_TAG, "/durable/tmp/" + fname + " radiolog close err:" + e222222);
                            }
                        }
                    } catch (FileNotFoundException e9) {
                        e = e9;
                        try {
                            Rlog.e(RIL.RILJ_LOG_TAG, "/durable/tmp/" + fname + " radiolog open err:" + e);
                            if (fileOutputStream != null) {
                                try {
                                    fileOutputStream.close();
                                } catch (IOException e2222222) {
                                    Rlog.e(RIL.RILJ_LOG_TAG, "/durable/tmp/" + fname + " radiolog close err:" + e2222222);
                                }
                            }
                        } catch (Throwable th5) {
                            th = th5;
                            if (fileOutputStream != null) {
                                try {
                                    fileOutputStream.close();
                                } catch (IOException e22222222) {
                                    Rlog.e(RIL.RILJ_LOG_TAG, "/durable/tmp/" + fname + " radiolog close err:" + e22222222);
                                }
                            }
                            throw th;
                        }
                    } catch (IOException e10) {
                        e22222222 = e10;
                        Rlog.e(RIL.RILJ_LOG_TAG, "/durable/tmp/" + fname + " radiolog write err:" + e22222222);
                        if (fileOutputStream != null) {
                            try {
                                fileOutputStream.close();
                            } catch (IOException e222222222) {
                                Rlog.e(RIL.RILJ_LOG_TAG, "/durable/tmp/" + fname + " radiolog close err:" + e222222222);
                            }
                        }
                    }
                } else {
                    BufferedInputStream reader = null;
                    Process process = null;
                    readCnt = 0;
                    try {
                        if (this.mBufType == 0) {
                            process = Runtime.getRuntime().exec(new String[]{"logcat", "-v", "time", "-b", "main"});
                        } else {
                            process = Runtime.getRuntime().exec(new String[]{"logcat", "-v", "time", "-b", "radio"});
                        }
                        BufferedInputStream bufferedInputStream = new BufferedInputStream(process.getInputStream(), 262144);
                        if (bufferedInputStream != null) {
                            int retryCnt = 0;
                            try {
                                SystemClock.sleep(60);
                                while (bufferedInputStream.available() > 0) {
                                    int len = bufferedInputStream.read(buf, readCnt, 262144 - readCnt);
                                    if (len <= 0) {
                                        break;
                                    }
                                    readCnt += len;
                                    retryCnt++;
                                    SystemClock.sleep(30);
                                    if (retryCnt > 9 || readCnt >= 262144) {
                                        break;
                                    }
                                }
                            } catch (IOException e11) {
                                e222222222 = e11;
                                reader = bufferedInputStream;
                                try {
                                    Rlog.e(RIL.RILJ_LOG_TAG, "logcat read err:" + e222222222);
                                    if (reader != null) {
                                        try {
                                            reader.close();
                                        } catch (IOException e2222222222) {
                                            Rlog.e(RIL.RILJ_LOG_TAG, "logcat close err:" + e2222222222);
                                        }
                                    }
                                    if (process != null) {
                                        process.destroy();
                                    }
                                    writer3 = null;
                                    s = "0000" + Integer.toString(getRadioLogIndex("/durable/tmp/", this.mRecType, this.mBufType));
                                    writer2 = new FileOutputStream("/durable/tmp/" + (getPrefix(this.mRecType, this.mBufType) + s.substring(s.length() - "0000".length(), s.length())));
                                    if (writer2 != null) {
                                    }
                                    if (writer2 != null) {
                                    }
                                } catch (Throwable th6) {
                                    th = th6;
                                    if (reader != null) {
                                        try {
                                            reader.close();
                                        } catch (IOException e22222222222) {
                                            Rlog.e(RIL.RILJ_LOG_TAG, "logcat close err:" + e22222222222);
                                        }
                                    }
                                    if (process != null) {
                                        process.destroy();
                                    }
                                    throw th;
                                }
                            } catch (Throwable th7) {
                                th = th7;
                                reader = bufferedInputStream;
                                if (reader != null) {
                                }
                                if (process != null) {
                                }
                                throw th;
                            }
                        }
                        if (bufferedInputStream != null) {
                            try {
                                bufferedInputStream.close();
                            } catch (IOException e222222222222) {
                                Rlog.e(RIL.RILJ_LOG_TAG, "logcat close err:" + e222222222222);
                            }
                        }
                        if (process != null) {
                            process.destroy();
                        }
                        reader = bufferedInputStream;
                    } catch (IOException e12) {
                        e222222222222 = e12;
                    }
                    writer3 = null;
                    try {
                        s = "0000" + Integer.toString(getRadioLogIndex("/durable/tmp/", this.mRecType, this.mBufType));
                        writer2 = new FileOutputStream("/durable/tmp/" + (getPrefix(this.mRecType, this.mBufType) + s.substring(s.length() - "0000".length(), s.length())));
                        if (writer2 != null) {
                            try {
                                if (readCnt <= this.mWriteSize) {
                                    writer2.write(buf, 0, readCnt);
                                } else {
                                    writer2.write(buf, readCnt - this.mWriteSize, this.mWriteSize);
                                }
                                if (this.mStackTrace != null) {
                                    writer2.write("--- Stack Trace Information ---\n".getBytes());
                                    writer2.write(this.mStackTrace.getBytes());
                                }
                                writer2.flush();
                            } catch (FileNotFoundException e13) {
                                e = e13;
                                writer3 = writer2;
                            } catch (IOException e14) {
                                e222222222222 = e14;
                                writer3 = writer2;
                                Rlog.e(RIL.RILJ_LOG_TAG, "radiolog write err:" + e222222222222);
                                if (writer3 != null) {
                                }
                            } catch (Throwable th8) {
                                th = th8;
                                writer3 = writer2;
                                if (writer3 != null) {
                                }
                                throw th;
                            }
                        }
                        if (writer2 != null) {
                            try {
                                writer2.close();
                            } catch (IOException e2222222222222) {
                                Rlog.e(RIL.RILJ_LOG_TAG, "radiolog close err:" + e2222222222222);
                            }
                        }
                    } catch (FileNotFoundException e15) {
                        e = e15;
                        try {
                            Rlog.e(RIL.RILJ_LOG_TAG, "radiolog write err:" + e);
                            if (writer3 != null) {
                                try {
                                    writer3.close();
                                } catch (IOException e22222222222222) {
                                    Rlog.e(RIL.RILJ_LOG_TAG, "radiolog close err:" + e22222222222222);
                                }
                            }
                        } catch (Throwable th9) {
                            th = th9;
                            if (writer3 != null) {
                                try {
                                    writer3.close();
                                } catch (IOException e222222222222222) {
                                    Rlog.e(RIL.RILJ_LOG_TAG, "radiolog close err:" + e222222222222222);
                                }
                            }
                            throw th;
                        }
                    } catch (IOException e16) {
                        e222222222222222 = e16;
                        Rlog.e(RIL.RILJ_LOG_TAG, "radiolog write err:" + e222222222222222);
                        if (writer3 != null) {
                            try {
                                writer3.close();
                            } catch (IOException e2222222222222222) {
                                Rlog.e(RIL.RILJ_LOG_TAG, "radiolog close err:" + e2222222222222222);
                            }
                        }
                    }
                }
            }
        }

        private String getPrefix(int rectype, int buftype) {
            String ret = "";
            if (!TelBrand.IS_DCM) {
                return ret;
            }
            if (rectype == 0) {
                ret = "N";
            } else if (rectype == 2) {
                ret = "NS";
            } else {
                ret = "S";
            }
            if (buftype == 0) {
                return ret + "M";
            }
            if (buftype == 2) {
                return ret + "RCI";
            }
            return ret + "R";
        }

        /* JADX WARNING: Unknown top exception splitter block from list: {B:91:0x01ba=Splitter:B:91:0x01ba, B:72:0x013d=Splitter:B:72:0x013d} */
        /* JADX WARNING: Removed duplicated region for block: B:82:0x018a A:{SYNTHETIC, Splitter:B:82:0x018a} */
        /* JADX WARNING: Removed duplicated region for block: B:89:0x01b7 A:{SYNTHETIC, Splitter:B:89:0x01b7} */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        private synchronized int getRadioLogIndex(String path, int rectype, int buftype) {
            IOException e;
            Throwable th;
            OutputStream out;
            String RADIO_LOG_FILENAME_INDEX;
            try {
                if (!TelBrand.IS_DCM) {
                    return -1;
                }
                byte[] bytes;
                String CHARSET_ASCII = "US-ASCII";
                FileInputStream fileInputStream = null;
                int RADIO_LOG_RINGSIZE = 10;
                int idx = 9;
                RADIO_LOG_FILENAME_INDEX = path + getPrefix(rectype, buftype) + "1000";
                if (rectype == 0) {
                    RADIO_LOG_RINGSIZE = 30;
                    idx = 29;
                } else if (rectype == 2) {
                    RADIO_LOG_RINGSIZE = 30;
                    idx = 29;
                }
                fileInputStream = new FileInputStream(RADIO_LOG_FILENAME_INDEX);
                if (fileInputStream != null) {
                    try {
                        if (fileInputStream.available() > 0) {
                            bytes = new byte[fileInputStream.available()];
                            if (bytes != null) {
                                fileInputStream.read(bytes);
                                String str = new String(bytes, "US-ASCII");
                                if (str != null) {
                                    idx = Integer.valueOf(str).intValue();
                                    if (idx < 0 || idx >= RADIO_LOG_RINGSIZE) {
                                        idx = RADIO_LOG_RINGSIZE - 1;
                                    }
                                }
                            }
                        }
                        try {
                            fileInputStream.close();
                        } catch (IOException e2) {
                        }
                    } catch (NumberFormatException nfe) {
                        Rlog.e(RIL.RILJ_LOG_TAG, RADIO_LOG_FILENAME_INDEX + " read err:" + nfe);
                        try {
                            fileInputStream.close();
                        } catch (IOException e3) {
                        }
                    } catch (IOException e4) {
                        Rlog.e(RIL.RILJ_LOG_TAG, RADIO_LOG_FILENAME_INDEX + " read err:" + e4);
                        try {
                            fileInputStream.close();
                        } catch (IOException e5) {
                        }
                    } catch (Throwable th2) {
                        try {
                            fileInputStream.close();
                        } catch (IOException e6) {
                        }
                        throw th2;
                    }
                }
                idx = (idx + 1) % RADIO_LOG_RINGSIZE;
                FileOutputStream out2 = null;
                try {
                    OutputStream fileOutputStream = new FileOutputStream(RADIO_LOG_FILENAME_INDEX);
                    if (fileOutputStream != null) {
                        try {
                            String str2 = String.valueOf(idx);
                            if (str2 != null) {
                                bytes = str2.getBytes("US-ASCII");
                                if (bytes != null) {
                                    fileOutputStream.write(bytes);
                                    fileOutputStream.flush();
                                }
                            }
                        } catch (IOException e7) {
                            e4 = e7;
                            out2 = fileOutputStream;
                            try {
                                Rlog.e(RIL.RILJ_LOG_TAG, RADIO_LOG_FILENAME_INDEX + " write err:" + e4);
                                if (out2 != null) {
                                }
                                return idx;
                            } catch (Throwable th3) {
                                th2 = th3;
                                if (out2 != null) {
                                    try {
                                        out2.close();
                                    } catch (IOException e42) {
                                        Rlog.e(RIL.RILJ_LOG_TAG, RADIO_LOG_FILENAME_INDEX + " close err:" + e42);
                                    }
                                }
                                throw th2;
                            }
                        } catch (Throwable th4) {
                            th2 = th4;
                            out2 = fileOutputStream;
                            if (out2 != null) {
                            }
                            throw th2;
                        }
                    }
                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.close();
                        } catch (IOException e422) {
                            Rlog.e(RIL.RILJ_LOG_TAG, RADIO_LOG_FILENAME_INDEX + " close err:" + e422);
                        }
                    }
                    out2 = fileOutputStream;
                } catch (IOException e8) {
                    e422 = e8;
                    Rlog.e(RIL.RILJ_LOG_TAG, RADIO_LOG_FILENAME_INDEX + " write err:" + e422);
                    if (out2 != null) {
                        try {
                            out2.close();
                        } catch (IOException e4222) {
                            Rlog.e(RIL.RILJ_LOG_TAG, RADIO_LOG_FILENAME_INDEX + " close err:" + e4222);
                        }
                    }
                    return idx;
                }
            } catch (FileNotFoundException e9) {
                Rlog.w(RIL.RILJ_LOG_TAG, RADIO_LOG_FILENAME_INDEX + " open err:" + e9);
            } catch (Throwable th22) {
                throw th22;
            }
        }
    }

    class RILReceiver implements Runnable {
        byte[] buffer = new byte[8192];

        RILReceiver() {
        }

        /* JADX WARNING: Removed duplicated region for block: B:31:0x00da A:{SYNTHETIC, Splitter:B:31:0x00da} */
        /* JADX WARNING: Removed duplicated region for block: B:42:0x0118 A:{SKIP} */
        /* JADX WARNING: Removed duplicated region for block: B:35:0x00e1 A:{SYNTHETIC, Splitter:B:35:0x00e1} */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void run() {
            Throwable tr;
            int retryCount = 0;
            String rilSocket = "rild";
            while (true) {
                LocalSocket localSocket = null;
                try {
                    if (RIL.this.mInstanceId == null || RIL.this.mInstanceId.intValue() == 0) {
                        rilSocket = RIL.SOCKET_NAME_RIL[0];
                    } else {
                        rilSocket = RIL.SOCKET_NAME_RIL[RIL.this.mInstanceId.intValue()];
                    }
                    try {
                        LocalSocket s = new LocalSocket();
                        try {
                            s.connect(new LocalSocketAddress(rilSocket, Namespace.RESERVED));
                            retryCount = 0;
                            RIL.this.mSocket = s;
                            Rlog.i(RIL.RILJ_LOG_TAG, "(" + RIL.this.mInstanceId + ") Connected to '" + rilSocket + "' socket");
                            int length = 0;
                            try {
                                InputStream is = RIL.this.mSocket.getInputStream();
                                while (true) {
                                    length = RIL.readRilMessage(is, this.buffer);
                                    if (length >= 0) {
                                        Parcel p = Parcel.obtain();
                                        p.unmarshall(this.buffer, 0, length);
                                        p.setDataPosition(0);
                                        RIL.this.processResponse(p);
                                        p.recycle();
                                    }
                                    break;
                                }
                            } catch (IOException ex) {
                                Rlog.i(RIL.RILJ_LOG_TAG, "'" + rilSocket + "' socket closed", ex);
                            } catch (Throwable th) {
                                tr = th;
                                Rlog.e(RIL.RILJ_LOG_TAG, "Uncaught exception", tr);
                                RIL.this.notifyRegistrantsRilConnectionChanged(-1);
                                return;
                            }
                            Rlog.i(RIL.RILJ_LOG_TAG, "(" + RIL.this.mInstanceId + ") Disconnected from '" + rilSocket + "' socket");
                            RIL.this.setRadioState(RadioState.RADIO_UNAVAILABLE);
                            try {
                                RIL.this.mSocket.close();
                            } catch (IOException e) {
                            }
                            RIL.this.mSocket = null;
                            RILRequest.resetSerial();
                            RIL.this.clearRequestList(1, false);
                        } catch (IOException e2) {
                            localSocket = s;
                            if (localSocket != null) {
                            }
                            if (retryCount != 8) {
                            }
                            try {
                                Thread.sleep(4000);
                            } catch (InterruptedException e3) {
                            }
                            retryCount++;
                        }
                    } catch (IOException e4) {
                        if (localSocket != null) {
                            try {
                                localSocket.close();
                            } catch (IOException e5) {
                            }
                        }
                        if (retryCount != 8) {
                            Rlog.e(RIL.RILJ_LOG_TAG, "Couldn't find '" + rilSocket + "' socket after " + retryCount + " times, continuing to retry silently");
                        } else if (retryCount >= 0 && retryCount < 8) {
                            Rlog.i(RIL.RILJ_LOG_TAG, "Couldn't find '" + rilSocket + "' socket; retrying after timeout");
                        }
                        Thread.sleep(4000);
                        retryCount++;
                    }
                } catch (Throwable th2) {
                    tr = th2;
                }
            }
        }
    }

    class RILSender extends Handler implements Runnable {
        byte[] dataLength = new byte[4];

        public RILSender(Looper looper) {
            super(looper);
        }

        public void run() {
        }

        public void handleMessage(Message msg) {
            RILRequest rr = msg.obj;
            switch (msg.what) {
                case 1:
                case 3:
                    try {
                        LocalSocket s = RIL.this.mSocket;
                        if (s == null) {
                            rr.onError(1, null);
                            RIL.this.decrementWakeLock(rr);
                            rr.release();
                            return;
                        }
                        if (msg.what != 3) {
                            synchronized (RIL.this.mRequestList) {
                                RIL.this.mRequestList.append(rr.mSerial, rr);
                            }
                        }
                        byte[] data = rr.mParcel.marshall();
                        rr.mParcel.recycle();
                        rr.mParcel = null;
                        if (data.length > 8192) {
                            throw new RuntimeException("Parcel larger than max bytes allowed! " + data.length);
                        }
                        byte[] bArr = this.dataLength;
                        this.dataLength[1] = (byte) 0;
                        bArr[0] = (byte) 0;
                        this.dataLength[2] = (byte) ((data.length >> 8) & 255);
                        this.dataLength[3] = (byte) (data.length & 255);
                        s.getOutputStream().write(this.dataLength);
                        s.getOutputStream().write(data);
                        if (msg.what == 3) {
                            rr.release();
                            return;
                        }
                    } catch (IOException ex) {
                        Rlog.e(RIL.RILJ_LOG_TAG, "IOException", ex);
                        if (RIL.this.findAndRemoveRequestFromList(rr.mSerial) != null) {
                            rr.onError(1, null);
                            RIL.this.decrementWakeLock(rr);
                            rr.release();
                            return;
                        }
                    } catch (RuntimeException exc) {
                        Rlog.e(RIL.RILJ_LOG_TAG, "Uncaught exception ", exc);
                        if (RIL.this.findAndRemoveRequestFromList(rr.mSerial) != null) {
                            rr.onError(2, null);
                            RIL.this.decrementWakeLock(rr);
                            rr.release();
                            return;
                        }
                    }
                    break;
                case 2:
                    synchronized (RIL.this.mRequestList) {
                        if (msg.arg1 == RIL.this.mWlSequenceNum && RIL.this.clearWakeLock(0)) {
                            int count = RIL.this.mRequestList.size();
                            Rlog.d(RIL.RILJ_LOG_TAG, "WAKE_LOCK_TIMEOUT  mRequestList=" + count);
                            for (int i = 0; i < count; i++) {
                                rr = (RILRequest) RIL.this.mRequestList.valueAt(i);
                                Rlog.d(RIL.RILJ_LOG_TAG, i + ": [" + rr.mSerial + "] " + RIL.requestToString(rr.mRequest));
                            }
                        }
                    }
                case 4:
                    if (msg.arg1 == RIL.this.mAckWlSequenceNum && RIL.this.clearWakeLock(1)) {
                        Rlog.d(RIL.RILJ_LOG_TAG, "ACK_WAKE_LOCK_TIMEOUT");
                        break;
                    }
                case 5:
                    rr = RIL.this.findAndRemoveRequestFromList(msg.arg1);
                    if (rr != null) {
                        if (rr.mResult != null) {
                            AsyncResult.forMessage(rr.mResult, RIL.getResponseForTimedOutRILRequest(rr), null);
                            rr.mResult.sendToTarget();
                            RIL.this.mEventLog.writeOnRilTimeoutResponse(rr.mSerial, rr.mRequest);
                        }
                        RIL.this.decrementWakeLock(rr);
                        rr.release();
                        break;
                    }
                    break;
                case 7:
                    if (TelBrand.IS_DCM) {
                        RIL.this.send(rr);
                        break;
                    }
                    break;
            }
        }
    }

    private static Object getResponseForTimedOutRILRequest(RILRequest rr) {
        if (rr == null) {
            return null;
        }
        Object timeoutResponse = null;
        switch (rr.mRequest) {
            case 135:
                timeoutResponse = new ModemActivityInfo(0, 0, 0, new int[5], 0, 0);
                break;
        }
        return timeoutResponse;
    }

    private static int readRilMessage(InputStream is, byte[] buffer) throws IOException {
        int countRead;
        int offset = 0;
        int remaining = 4;
        do {
            countRead = is.read(buffer, offset, remaining);
            if (countRead < 0) {
                Rlog.e(RILJ_LOG_TAG, "Hit EOS reading message length");
                return -1;
            }
            offset += countRead;
            remaining -= countRead;
        } while (remaining > 0);
        int messageLength = ((((buffer[0] & 255) << 24) | ((buffer[1] & 255) << 16)) | ((buffer[2] & 255) << 8)) | (buffer[3] & 255);
        offset = 0;
        remaining = messageLength;
        do {
            countRead = is.read(buffer, offset, remaining);
            if (countRead < 0) {
                Rlog.e(RILJ_LOG_TAG, "Hit EOS reading message.  messageLength=" + messageLength + " remaining=" + remaining);
                return -1;
            }
            offset += countRead;
            remaining -= countRead;
        } while (remaining > 0);
        return messageLength;
    }

    public RIL(Context context, int preferredNetworkType, int cdmaSubscription) {
        this(context, preferredNetworkType, cdmaSubscription, null);
    }

    public RIL(Context context, int preferredNetworkType, int cdmaSubscription, Integer instanceId) {
        super(context);
        this.mHeaderSize = OEM_IDENTIFIER.length() + 8;
        this.mDefaultDisplayState = 0;
        this.mRadioScreenState = -1;
        this.mIsDevicePlugged = false;
        this.mWlSequenceNum = 0;
        this.mAckWlSequenceNum = 0;
        this.mRequestList = new SparseArray();
        this.mTestingEmergencyCall = new AtomicBoolean(false);
        this.mRILConnected_UpdateOemDataSettings_OnceSkip = true;
        this.mUpdateOemDataSettings_MobileData = false;
        this.mUpdateOemDataSettings_DataRoaming = false;
        this.mRILConnected_SetInitialAttachApn_OnceSkip = true;
        this.mSetInitialAttachApn_Apn = null;
        this.mSetInitialAttachApn_Protocol = null;
        this.mSetInitialAttachApn_AuthType = -1;
        this.mSetInitialAttachApn_Username = null;
        this.mSetInitialAttachApn_Password = null;
        this.mIntentReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("android.intent.action.SERVICE_STATE")) {
                    RIL.this.upDomesticInService(context, intent);
                } else {
                    Rlog.w(RIL.RILJ_LOG_TAG, "RIL received unexpected Intent: " + intent.getAction());
                }
            }
        };
        this.mDisplayListener = new DisplayListener() {
            public void onDisplayAdded(int displayId) {
            }

            public void onDisplayRemoved(int displayId) {
            }

            public void onDisplayChanged(int displayId) {
                if (displayId == 0) {
                    int oldState = RIL.this.mDefaultDisplayState;
                    RIL.this.mDefaultDisplayState = RIL.this.mDefaultDisplay.getState();
                    if (RIL.this.mDefaultDisplayState != oldState) {
                        RIL.this.updateScreenState();
                    }
                }
            }
        };
        this.mBatteryStateListener = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                boolean z = true;
                if (TelBrand.IS_DCM && intent.getAction().equals("android.intent.action.ACTION_SHUTDOWN")) {
                    Thread thread = new Thread(new RILRadioLogWriter(1, 1));
                    if (thread != null) {
                        thread.start();
                        return;
                    }
                    return;
                }
                boolean oldState = RIL.this.mIsDevicePlugged;
                RIL ril = RIL.this;
                if (intent.getIntExtra("plugged", 0) == 0) {
                    z = false;
                }
                ril.mIsDevicePlugged = z;
                if (RIL.this.mIsDevicePlugged != oldState) {
                    RIL.this.updateScreenState();
                }
            }
        };
        riljLog("RIL(context, preferredNetworkType=" + preferredNetworkType + " cdmaSubscription=" + cdmaSubscription + ")");
        this.mContext = context;
        this.mCdmaSubscription = cdmaSubscription;
        this.mPreferredNetworkType = preferredNetworkType;
        this.mPhoneType = 0;
        this.mInstanceId = instanceId;
        this.mEventLog = new TelephonyEventLog(this.mInstanceId.intValue());
        PowerManager pm = (PowerManager) context.getSystemService("power");
        this.mWakeLock = pm.newWakeLock(1, RILJ_LOG_TAG);
        this.mWakeLock.setReferenceCounted(false);
        this.mAckWakeLock = pm.newWakeLock(1, RILJ_ACK_WAKELOCK_NAME);
        this.mAckWakeLock.setReferenceCounted(false);
        this.mWakeLockTimeout = SystemProperties.getInt("ro.ril.wake_lock_timeout", 60000);
        this.mAckWakeLockTimeout = SystemProperties.getInt("ro.ril.wake_lock_timeout", 200);
        this.mWakeLockCount = 0;
        this.mSenderThread = new HandlerThread("RILSender" + this.mInstanceId);
        this.mSenderThread.start();
        this.mSender = new RILSender(this.mSenderThread.getLooper());
        if (((ConnectivityManager) context.getSystemService("connectivity")).isNetworkSupported(0)) {
            riljLog("Starting RILReceiver" + this.mInstanceId);
            this.mReceiver = new RILReceiver();
            this.mReceiverThread = new Thread(this.mReceiver, "RILReceiver" + this.mInstanceId);
            this.mReceiverThread.start();
            DisplayManager dm = (DisplayManager) context.getSystemService("display");
            this.mDefaultDisplay = dm.getDisplay(0);
            dm.registerDisplayListener(this.mDisplayListener, null);
            this.mDefaultDisplayState = this.mDefaultDisplay.getState();
            IntentFilter filter = new IntentFilter("android.intent.action.BATTERY_CHANGED");
            if (TelBrand.IS_DCM && LOG_SD) {
                filter.addAction("android.intent.action.ACTION_SHUTDOWN");
            }
            Intent batteryStatus = context.registerReceiver(this.mBatteryStateListener, filter);
            if (batteryStatus != null) {
                this.mIsDevicePlugged = batteryStatus.getIntExtra("plugged", 0) != 0;
            }
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.intent.action.SERVICE_STATE");
            context.registerReceiver(this.mIntentReceiver, intentFilter);
        } else {
            riljLog("Not starting RILReceiver: wifi-only");
        }
        TelephonyDevController tdc = TelephonyDevController.getInstance();
        TelephonyDevController.registerRIL(this);
    }

    public void getVoiceRadioTechnology(Message result) {
        RILRequest rr = RILRequest.obtain(108, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getImsRegistrationState(Message result) {
        RILRequest rr = RILRequest.obtain(QtiCallConstants.CALL_FAIL_EXTRA_CODE_LOCAL_LOW_BATTERY, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setOnNITZTime(Handler h, int what, Object obj) {
        super.setOnNITZTime(h, what, obj);
        if (this.mLastNITZTimeInfo != null) {
            this.mNITZTimeRegistrant.notifyRegistrant(new AsyncResult(null, this.mLastNITZTimeInfo, null));
        }
    }

    public void getIccCardStatus(Message result) {
        RILRequest rr = RILRequest.obtain(1, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setUiccSubscription(int slotId, int appIndex, int subId, int subStatus, Message result) {
        RILRequest rr = RILRequest.obtain(122, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " slot: " + slotId + " appIndex: " + appIndex + " subId: " + subId + " subStatus: " + subStatus);
        rr.mParcel.writeInt(slotId);
        rr.mParcel.writeInt(appIndex);
        rr.mParcel.writeInt(subId);
        rr.mParcel.writeInt(subStatus);
        send(rr);
    }

    public void setDataAllowed(boolean allowed, Message result) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(123, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " allowed: " + allowed);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!allowed) {
            i = 0;
        }
        parcel.writeInt(i);
        send(rr);
    }

    public void supplyIccPin(String pin, Message result) {
        supplyIccPinForApp(pin, null, result);
    }

    public void supplyIccPinForApp(String pin, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(2, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(pin);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    public void supplyIccPuk(String puk, String newPin, Message result) {
        supplyIccPukForApp(puk, newPin, null, result);
    }

    public void supplyIccPukForApp(String puk, String newPin, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(3, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(puk);
        rr.mParcel.writeString(newPin);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    public void supplyIccPin2(String pin, Message result) {
        supplyIccPin2ForApp(pin, null, result);
    }

    public void supplyIccPin2ForApp(String pin, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(4, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(pin);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    public void supplyIccPuk2(String puk2, String newPin2, Message result) {
        supplyIccPuk2ForApp(puk2, newPin2, null, result);
    }

    public void supplyIccPuk2ForApp(String puk, String newPin2, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(5, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(puk);
        rr.mParcel.writeString(newPin2);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    public void changeIccPin(String oldPin, String newPin, Message result) {
        changeIccPinForApp(oldPin, newPin, null, result);
    }

    public void changeIccPinForApp(String oldPin, String newPin, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(6, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(oldPin);
        rr.mParcel.writeString(newPin);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    public void changeIccPin2(String oldPin2, String newPin2, Message result) {
        changeIccPin2ForApp(oldPin2, newPin2, null, result);
    }

    public void changeIccPin2ForApp(String oldPin2, String newPin2, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(7, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(oldPin2);
        rr.mParcel.writeString(newPin2);
        rr.mParcel.writeString(aid);
        send(rr);
    }

    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message result) {
        RILRequest rr = RILRequest.obtain(44, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(facility);
        rr.mParcel.writeString(oldPwd);
        rr.mParcel.writeString(newPwd);
        send(rr);
    }

    public void supplyNetworkDepersonalization(String netpin, Message result) {
        RILRequest rr = RILRequest.obtain(8, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(1);
        rr.mParcel.writeString(netpin);
        send(rr);
    }

    public void getCurrentCalls(Message result) {
        RILRequest rr = RILRequest.obtain(9, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Deprecated
    public void getPDPContextList(Message result) {
        getDataCallList(result);
    }

    public void getDataCallList(Message result) {
        RILRequest rr = RILRequest.obtain(57, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void dial(String address, int clirMode, Message result) {
        dial(address, clirMode, null, result);
    }

    public void dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        RILRequest rr = RILRequest.obtain(10, result);
        rr.mParcel.writeString(address);
        rr.mParcel.writeInt(clirMode);
        if (uusInfo == null) {
            rr.mParcel.writeInt(0);
        } else {
            rr.mParcel.writeInt(1);
            rr.mParcel.writeInt(uusInfo.getType());
            rr.mParcel.writeInt(uusInfo.getDcs());
            rr.mParcel.writeByteArray(uusInfo.getUserData());
        }
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        this.mEventLog.writeRilDial(rr.mSerial, clirMode, uusInfo);
        send(rr);
    }

    public void getIMSI(Message result) {
        getIMSIForApp(null, result);
    }

    public void getIMSIForApp(String aid, Message result) {
        RILRequest rr = RILRequest.obtain(11, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeString(aid);
        riljLog(rr.serialString() + "> getIMSI: " + requestToString(rr.mRequest) + " aid: " + aid);
        send(rr);
    }

    public void getIMEI(Message result) {
        RILRequest rr = RILRequest.obtain(38, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getIMEISV(Message result) {
        RILRequest rr = RILRequest.obtain(39, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void hangupConnection(int gsmIndex, Message result) {
        riljLog("hangupConnection: gsmIndex=" + gsmIndex);
        RILRequest rr = RILRequest.obtain(12, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + gsmIndex);
        this.mEventLog.writeRilHangup(rr.mSerial, 12, gsmIndex);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(gsmIndex);
        send(rr);
    }

    public void hangupWaitingOrBackground(Message result) {
        RILRequest rr = RILRequest.obtain(13, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        this.mEventLog.writeRilHangup(rr.mSerial, 13, -1);
        send(rr);
    }

    public void hangupForegroundResumeBackground(Message result) {
        RILRequest rr = RILRequest.obtain(14, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        this.mEventLog.writeRilHangup(rr.mSerial, 14, -1);
        send(rr);
    }

    public void switchWaitingOrHoldingAndActive(Message result) {
        RILRequest rr = RILRequest.obtain(15, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void conference(Message result) {
        RILRequest rr = RILRequest.obtain(16, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setPreferredVoicePrivacy(boolean enable, Message result) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(82, result);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!enable) {
            i = 0;
        }
        parcel.writeInt(i);
        send(rr);
    }

    public void getPreferredVoicePrivacy(Message result) {
        send(RILRequest.obtain(83, result));
    }

    public void separateConnection(int gsmIndex, Message result) {
        RILRequest rr = RILRequest.obtain(52, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + gsmIndex);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(gsmIndex);
        send(rr);
    }

    public void acceptCall(Message result) {
        RILRequest rr = RILRequest.obtain(40, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        this.mEventLog.writeRilAnswer(rr.mSerial);
        send(rr);
    }

    public void rejectCall(Message result) {
        RILRequest rr = RILRequest.obtain(17, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void explicitCallTransfer(Message result) {
        RILRequest rr = RILRequest.obtain(72, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getLastCallFailCause(Message result) {
        RILRequest rr = RILRequest.obtain(18, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Deprecated
    public void getLastPdpFailCause(Message result) {
        getLastDataCallFailCause(result);
    }

    public void getLastDataCallFailCause(Message result) {
        RILRequest rr = RILRequest.obtain(56, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setMute(boolean enableMute, Message response) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(53, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + enableMute);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!enableMute) {
            i = 0;
        }
        parcel.writeInt(i);
        send(rr);
    }

    public void getMute(Message response) {
        RILRequest rr = RILRequest.obtain(54, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getSignalStrength(Message result) {
        RILRequest rr = RILRequest.obtain(19, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getVoiceRegistrationState(Message result) {
        RILRequest rr = RILRequest.obtain(20, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getDataRegistrationState(Message result) {
        RILRequest rr = RILRequest.obtain(21, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getOperator(Message result) {
        RILRequest rr = RILRequest.obtain(22, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getHardwareConfig(Message result) {
        RILRequest rr = RILRequest.obtain(124, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void sendDtmf(char c, Message result) {
        RILRequest rr = RILRequest.obtain(24, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeString(Character.toString(c));
        send(rr);
    }

    public void startDtmf(char c, Message result) {
        RILRequest rr = RILRequest.obtain(49, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeString(Character.toString(c));
        send(rr);
    }

    public void stopDtmf(Message result) {
        RILRequest rr = RILRequest.obtain(50, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void sendBurstDtmf(String dtmfString, int on, int off, Message result) {
        RILRequest rr = RILRequest.obtain(85, result);
        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(dtmfString);
        rr.mParcel.writeString(Integer.toString(on));
        rr.mParcel.writeString(Integer.toString(off));
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + dtmfString);
        send(rr);
    }

    private void constructGsmSendSmsRilRequest(RILRequest rr, String smscPDU, String pdu) {
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(smscPDU);
        rr.mParcel.writeString(pdu);
    }

    public void sendSMS(String smscPDU, String pdu, Message result) {
        RILRequest rr = RILRequest.obtain(25, result);
        constructGsmSendSmsRilRequest(rr, smscPDU, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        this.mEventLog.writeRilSendSms(rr.mSerial, rr.mRequest);
        send(rr);
    }

    public void sendSMSExpectMore(String smscPDU, String pdu, Message result) {
        RILRequest rr = RILRequest.obtain(26, result);
        constructGsmSendSmsRilRequest(rr, smscPDU, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        this.mEventLog.writeRilSendSms(rr.mSerial, rr.mRequest);
        send(rr);
    }

    private void constructCdmaSendSmsRilRequest(RILRequest rr, byte[] pdu) {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(pdu));
        try {
            int i;
            rr.mParcel.writeInt(dis.readInt());
            rr.mParcel.writeByte((byte) dis.readInt());
            rr.mParcel.writeInt(dis.readInt());
            rr.mParcel.writeInt(dis.read());
            rr.mParcel.writeInt(dis.read());
            rr.mParcel.writeInt(dis.read());
            rr.mParcel.writeInt(dis.read());
            int address_nbr_of_digits = (byte) dis.read();
            rr.mParcel.writeByte((byte) address_nbr_of_digits);
            for (i = 0; i < address_nbr_of_digits; i++) {
                rr.mParcel.writeByte(dis.readByte());
            }
            rr.mParcel.writeInt(dis.read());
            rr.mParcel.writeByte((byte) dis.read());
            int subaddr_nbr_of_digits = (byte) dis.read();
            rr.mParcel.writeByte((byte) subaddr_nbr_of_digits);
            for (i = 0; i < subaddr_nbr_of_digits; i++) {
                rr.mParcel.writeByte(dis.readByte());
            }
            int bearerDataLength = dis.read();
            rr.mParcel.writeInt(bearerDataLength);
            for (i = 0; i < bearerDataLength; i++) {
                rr.mParcel.writeByte(dis.readByte());
            }
        } catch (IOException ex) {
            riljLog("sendSmsCdma: conversion from input stream to object failed: " + ex);
        }
    }

    public void sendCdmaSms(byte[] pdu, Message result) {
        RILRequest rr = RILRequest.obtain(87, result);
        constructCdmaSendSmsRilRequest(rr, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        this.mEventLog.writeRilSendSms(rr.mSerial, rr.mRequest);
        send(rr);
    }

    public void sendImsGsmSms(String smscPDU, String pdu, int retry, int messageRef, Message result) {
        RILRequest rr = RILRequest.obtain(113, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeByte((byte) retry);
        rr.mParcel.writeInt(messageRef);
        constructGsmSendSmsRilRequest(rr, smscPDU, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        this.mEventLog.writeRilSendSms(rr.mSerial, rr.mRequest);
        send(rr);
    }

    public void sendImsCdmaSms(byte[] pdu, int retry, int messageRef, Message result) {
        RILRequest rr = RILRequest.obtain(113, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeByte((byte) retry);
        rr.mParcel.writeInt(messageRef);
        constructCdmaSendSmsRilRequest(rr, pdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        this.mEventLog.writeRilSendSms(rr.mSerial, rr.mRequest);
        send(rr);
    }

    public void deleteSmsOnSim(int index, Message response) {
        RILRequest rr = RILRequest.obtain(64, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(index);
        send(rr);
    }

    public void deleteSmsOnRuim(int index, Message response) {
        RILRequest rr = RILRequest.obtain(97, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(index);
        send(rr);
    }

    public void writeSmsToSim(int status, String smsc, String pdu, Message response) {
        status = translateStatus(status);
        RILRequest rr = RILRequest.obtain(63, response);
        rr.mParcel.writeInt(status);
        rr.mParcel.writeString(pdu);
        rr.mParcel.writeString(smsc);
        send(rr);
    }

    public void writeSmsToRuim(int status, String pdu, Message response) {
        status = translateStatus(status);
        RILRequest rr = RILRequest.obtain(96, response);
        rr.mParcel.writeInt(status);
        constructCdmaWriteSmsRilRequest(rr, IccUtils.hexStringToBytes(pdu));
        send(rr);
    }

    private void constructCdmaWriteSmsRilRequest(RILRequest rr, byte[] pdu) {
        ByteArrayInputStream bais = new ByteArrayInputStream(pdu);
        DataInputStream dis = new DataInputStream(bais);
        try {
            int i;
            int teleServiceId = dis.readInt();
            rr.mParcel.writeInt(teleServiceId);
            byte servicePresent = (byte) dis.readInt();
            rr.mParcel.writeByte(servicePresent);
            int serviceCategory = dis.readInt();
            rr.mParcel.writeInt(serviceCategory);
            int address_digit_mode = dis.readByte();
            rr.mParcel.writeInt(address_digit_mode);
            int address_nbr_mode = dis.readByte();
            rr.mParcel.writeInt(address_nbr_mode);
            int address_ton = dis.readByte();
            rr.mParcel.writeInt(address_ton);
            int address_nbr_plan = dis.readByte();
            rr.mParcel.writeInt(address_nbr_plan);
            int address_nbr_of_digits = dis.readByte();
            rr.mParcel.writeByte((byte) address_nbr_of_digits);
            for (i = 0; i < address_nbr_of_digits; i++) {
                rr.mParcel.writeByte(dis.readByte());
            }
            int subaddressType = dis.readByte();
            rr.mParcel.writeInt(subaddressType);
            byte subaddr_odd = dis.readByte();
            rr.mParcel.writeByte(subaddr_odd);
            int subaddr_nbr_of_digits = dis.readByte();
            rr.mParcel.writeByte((byte) subaddr_nbr_of_digits);
            for (i = 0; i < subaddr_nbr_of_digits; i++) {
                rr.mParcel.writeByte(dis.readByte());
            }
            int bearerDataLength = dis.readByte() & 255;
            rr.mParcel.writeInt(bearerDataLength);
            for (i = 0; i < bearerDataLength; i++) {
                rr.mParcel.writeByte(dis.readByte());
            }
            riljLog(" teleServiceId=" + teleServiceId + " servicePresent=" + servicePresent + " serviceCategory=" + serviceCategory + " address_digit_mode=" + address_digit_mode + " address_nbr_mode=" + address_nbr_mode + " address_ton=" + address_ton + " address_nbr_plan=" + address_nbr_plan + " address_nbr_of_digits=" + address_nbr_of_digits + " subaddressType=" + subaddressType + " subaddr_odd= " + subaddr_odd + " subaddr_nbr_of_digits=" + subaddr_nbr_of_digits + " bearerDataLength=" + bearerDataLength);
            if (bais != null) {
                try {
                    bais.close();
                } catch (IOException e) {
                    riljLog("sendSmsCdma: close input stream exception" + e);
                    return;
                }
            }
            if (dis != null) {
                dis.close();
            }
        } catch (IOException ex) {
            riljLog("sendSmsCdma: conversion from input stream to object failed: " + ex);
            if (bais != null) {
                try {
                    bais.close();
                } catch (IOException e2) {
                    riljLog("sendSmsCdma: close input stream exception" + e2);
                    return;
                }
            }
            if (dis != null) {
                dis.close();
            }
        } catch (Throwable th) {
            if (bais != null) {
                try {
                    bais.close();
                } catch (IOException e22) {
                    riljLog("sendSmsCdma: close input stream exception" + e22);
                }
            }
            if (dis != null) {
                dis.close();
            }
        }
    }

    private int translateStatus(int status) {
        switch (status & 7) {
            case 1:
                return 1;
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

    public void setupDataCall(int radioTechnology, int profile, String apn, String user, String password, int authType, String protocol, Message result) {
        RILRequest rr = RILRequest.obtain(27, result);
        rr.mParcel.writeInt(7);
        rr.mParcel.writeString(Integer.toString(radioTechnology + 2));
        rr.mParcel.writeString(Integer.toString(profile));
        rr.mParcel.writeString(apn);
        rr.mParcel.writeString(user);
        rr.mParcel.writeString(password);
        rr.mParcel.writeString(Integer.toString(authType));
        rr.mParcel.writeString(protocol);
        String log_apn = apn;
        String log_user = user;
        String log_password = password;
        if (!"eng".equals(Build.TYPE)) {
            if (apn != null) {
                log_apn = "*****";
            }
            if (user != null) {
                log_user = "*****";
            }
            if (password != null) {
                log_password = "*****";
            }
        }
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + radioTechnology + " " + profile + " " + log_apn + " " + log_user + " " + log_password + " " + authType + " " + protocol);
        this.mEventLog.writeRilSetupDataCall(rr.mSerial, radioTechnology, profile, apn, user, password, authType, protocol);
        send(rr);
    }

    public void deactivateDataCall(int cid, int reason, Message result) {
        RILRequest rr = RILRequest.obtain(41, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(Integer.toString(cid));
        rr.mParcel.writeString(Integer.toString(reason));
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + cid + " " + reason);
        this.mEventLog.writeRilDeactivateDataCall(rr.mSerial, cid, reason);
        send(rr);
    }

    public void setRadioPower(boolean on, Message result) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(23, result);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!on) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + (on ? " on" : " off"));
        send(rr);
    }

    public void requestShutdown(Message result) {
        RILRequest rr = RILRequest.obtain(129, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setSuppServiceNotifications(boolean enable, Message result) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(62, result);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!enable) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void acknowledgeLastIncomingGsmSms(boolean success, int cause, Message result) {
        RILRequest rr = RILRequest.obtain(37, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(success ? 1 : 0);
        rr.mParcel.writeInt(cause);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + success + " " + cause);
        send(rr);
    }

    public void acknowledgeLastIncomingCdmaSms(boolean success, int cause, Message result) {
        RILRequest rr = RILRequest.obtain(88, result);
        rr.mParcel.writeInt(success ? 0 : 1);
        rr.mParcel.writeInt(cause);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + success + " " + cause);
        send(rr);
    }

    public void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu, Message result) {
        RILRequest rr = RILRequest.obtain(106, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(success ? "1" : "0");
        rr.mParcel.writeString(ackPdu);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ' ' + success + " [" + ackPdu + ']');
        send(rr);
    }

    public void iccIO(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, Message result) {
        iccIOForApp(command, fileid, path, p1, p2, p3, data, pin2, null, result);
    }

    public void iccIOForApp(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, String aid, Message result) {
        RILRequest rr = RILRequest.obtain(28, result);
        rr.mParcel.writeInt(command);
        rr.mParcel.writeInt(fileid);
        rr.mParcel.writeString(path);
        rr.mParcel.writeInt(p1);
        rr.mParcel.writeInt(p2);
        rr.mParcel.writeInt(p3);
        rr.mParcel.writeString(data);
        rr.mParcel.writeString(pin2);
        rr.mParcel.writeString(aid);
        if (!TelBrand.IS_KDDI) {
            riljLog(rr.serialString() + "> iccIO: " + requestToString(rr.mRequest) + " 0x" + Integer.toHexString(command) + " 0x" + Integer.toHexString(fileid) + " " + " path: " + path + "," + p1 + "," + p2 + "," + p3 + " aid: " + aid);
        }
        send(rr);
    }

    public void getCLIR(Message result) {
        RILRequest rr = RILRequest.obtain(31, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setCLIR(int clirMode, Message result) {
        RILRequest rr = RILRequest.obtain(32, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(clirMode);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + clirMode);
        send(rr);
    }

    public void queryCallWaiting(int serviceClass, Message response) {
        RILRequest rr = RILRequest.obtain(35, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(serviceClass);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + serviceClass);
        send(rr);
    }

    public void setCallWaiting(boolean enable, int serviceClass, Message response) {
        RILRequest rr = RILRequest.obtain(36, response);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(enable ? 1 : 0);
        rr.mParcel.writeInt(serviceClass);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + enable + ", " + serviceClass);
        send(rr);
    }

    public void setNetworkSelectionModeAutomatic(Message response) {
        RILRequest rr = RILRequest.obtain(46, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        if (TelBrand.IS_DCM && LOG_NS) {
            RILRadioLogWriter mainLogWriter = new RILRadioLogWriter(2, 0);
            mainLogWriter.setStackTrace();
            Thread mainLogThread = new Thread(mainLogWriter);
            if (mainLogThread != null) {
                mainLogThread.start();
            }
            RILRadioLogWriter radioLogWriter = new RILRadioLogWriter(2, 2);
            radioLogWriter.setLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            Thread radioLogThread = new Thread(radioLogWriter);
            if (radioLogThread != null) {
                radioLogThread.start();
            }
        }
        send(rr);
    }

    public void setNetworkSelectionModeManual(String operatorNumeric, Message response) {
        RILRequest rr = RILRequest.obtain(47, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + operatorNumeric);
        rr.mParcel.writeString(operatorNumeric);
        if (TelBrand.IS_DCM && LOG_NS) {
            RILRadioLogWriter mainLogWriter = new RILRadioLogWriter(2, 0);
            mainLogWriter.setStackTrace();
            Thread mainLogThread = new Thread(mainLogWriter);
            if (mainLogThread != null) {
                mainLogThread.start();
            }
            RILRadioLogWriter radioLogWriter = new RILRadioLogWriter(2, 2);
            radioLogWriter.setLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + operatorNumeric);
            Thread radioLogThread = new Thread(radioLogWriter);
            if (radioLogThread != null) {
                radioLogThread.start();
            }
        }
        send(rr);
    }

    public void getNetworkSelectionMode(Message response) {
        RILRequest rr = RILRequest.obtain(45, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getAvailableNetworks(Message response) {
        RILRequest rr = RILRequest.obtain(48, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        if (TelBrand.IS_DCM) {
            this.mSender.sendMessageDelayed(this.mSender.obtainMessage(7, rr), SEARCH_NETWORK_DELAY_TIME);
            return;
        }
        send(rr);
    }

    public void setCallForward(int action, int cfReason, int serviceClass, String number, int timeSeconds, Message response) {
        RILRequest rr = RILRequest.obtain(34, response);
        rr.mParcel.writeInt(action);
        rr.mParcel.writeInt(cfReason);
        rr.mParcel.writeInt(serviceClass);
        rr.mParcel.writeInt(PhoneNumberUtils.toaFromString(number));
        rr.mParcel.writeString(number);
        rr.mParcel.writeInt(timeSeconds);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + action + " " + cfReason + " " + serviceClass + timeSeconds);
        send(rr);
    }

    public void queryCallForwardStatus(int cfReason, int serviceClass, String number, Message response) {
        RILRequest rr = RILRequest.obtain(33, response);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(cfReason);
        rr.mParcel.writeInt(serviceClass);
        rr.mParcel.writeInt(PhoneNumberUtils.toaFromString(number));
        rr.mParcel.writeString(number);
        rr.mParcel.writeInt(0);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + cfReason + " " + serviceClass);
        send(rr);
    }

    public void queryCLIP(Message response) {
        RILRequest rr = RILRequest.obtain(55, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getBasebandVersion(Message response) {
        RILRequest rr = RILRequest.obtain(51, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void queryFacilityLock(String facility, String password, int serviceClass, Message response) {
        queryFacilityLockForApp(facility, password, serviceClass, null, response);
    }

    public void queryFacilityLockForApp(String facility, String password, int serviceClass, String appId, Message response) {
        RILRequest rr = RILRequest.obtain(42, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " [" + facility + " " + serviceClass + " " + appId + "]");
        rr.mParcel.writeInt(4);
        rr.mParcel.writeString(facility);
        rr.mParcel.writeString(password);
        rr.mParcel.writeString(Integer.toString(serviceClass));
        rr.mParcel.writeString(appId);
        send(rr);
    }

    public void setFacilityLock(String facility, boolean lockState, String password, int serviceClass, Message response) {
        setFacilityLockForApp(facility, lockState, password, serviceClass, null, response);
    }

    public void setFacilityLockForApp(String facility, boolean lockState, String password, int serviceClass, String appId, Message response) {
        RILRequest rr = RILRequest.obtain(43, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " [" + facility + " " + lockState + " " + serviceClass + " " + appId + "]");
        rr.mParcel.writeInt(5);
        rr.mParcel.writeString(facility);
        rr.mParcel.writeString(lockState ? "1" : "0");
        rr.mParcel.writeString(password);
        rr.mParcel.writeString(Integer.toString(serviceClass));
        rr.mParcel.writeString(appId);
        send(rr);
    }

    public void sendUSSD(String ussdString, Message response) {
        RILRequest rr = RILRequest.obtain(29, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + "*******");
        rr.mParcel.writeString(ussdString);
        send(rr);
    }

    public void cancelPendingUssd(Message response) {
        RILRequest rr = RILRequest.obtain(30, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void resetRadio(Message result) {
        RILRequest rr = RILRequest.obtain(58, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        RILRequest rr = RILRequest.obtain(59, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + "[" + IccUtils.bytesToHexString(data) + "]");
        rr.mParcel.writeByteArray(data);
        send(rr);
    }

    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        RILRequest rr = RILRequest.obtain(60, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeStringArray(strings);
        send(rr);
    }

    public void setBandMode(int bandMode, Message response) {
        RILRequest rr = RILRequest.obtain(65, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(bandMode);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + bandMode);
        send(rr);
    }

    public void queryAvailableBandMode(Message response) {
        RILRequest rr = RILRequest.obtain(66, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void sendTerminalResponse(String contents, Message response) {
        RILRequest rr = RILRequest.obtain(70, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeString(contents);
        send(rr);
    }

    public void sendEnvelope(String contents, Message response) {
        RILRequest rr = RILRequest.obtain(69, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeString(contents);
        send(rr);
    }

    public void sendEnvelopeWithStatus(String contents, Message response) {
        RILRequest rr = RILRequest.obtain(107, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + '[' + contents + ']');
        rr.mParcel.writeString(contents);
        send(rr);
    }

    public void handleCallSetupRequestFromSim(boolean accept, Message response) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(71, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        int[] param = new int[1];
        if (!accept) {
            i = 0;
        }
        param[0] = i;
        rr.mParcel.writeIntArray(param);
        send(rr);
    }

    public void setPreferredNetworkType(int networkType, Message response) {
        RILRequest rr = RILRequest.obtain(73, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(networkType);
        this.mPreferredNetworkType = networkType;
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + networkType);
        this.mEventLog.writeSetPreferredNetworkType(networkType);
        if (TelBrand.IS_DCM && LOG_NW) {
            RILRadioLogWriter r = new RILRadioLogWriter(0, 0);
            r.setStackTrace();
            Thread thread = new Thread(r);
            if (thread != null) {
                thread.start();
            }
        }
        send(rr);
    }

    public void getPreferredNetworkType(Message response) {
        RILRequest rr = RILRequest.obtain(74, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getNeighboringCids(Message response) {
        RILRequest rr = RILRequest.obtain(75, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setLocationUpdates(boolean enable, Message response) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(76, response);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!enable) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + enable);
        send(rr);
    }

    public void getSmscAddress(Message result) {
        RILRequest rr = RILRequest.obtain(100, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setSmscAddress(String address, Message result) {
        RILRequest rr = RILRequest.obtain(CallFailCause.MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE, result);
        rr.mParcel.writeString(address);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + address);
        send(rr);
    }

    public void reportSmsMemoryStatus(boolean available, Message result) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(CallFailCause.RECOVERY_ON_TIMER_EXPIRED, result);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!available) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + available);
        send(rr);
    }

    public void reportStkServiceIsRunning(Message result) {
        RILRequest rr = RILRequest.obtain(103, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getGsmBroadcastConfig(Message response) {
        RILRequest rr = RILRequest.obtain(89, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message response) {
        int i;
        RILRequest rr = RILRequest.obtain(90, response);
        rr.mParcel.writeInt(numOfConfig);
        for (i = 0; i < numOfConfig; i++) {
            int i2;
            rr.mParcel.writeInt(config[i].getFromServiceId());
            rr.mParcel.writeInt(config[i].getToServiceId());
            rr.mParcel.writeInt(config[i].getFromCodeScheme());
            rr.mParcel.writeInt(config[i].getToCodeScheme());
            Parcel parcel = rr.mParcel;
            if (config[i].isSelected()) {
                i2 = 1;
            } else {
                i2 = 0;
            }
            parcel.writeInt(i2);
        }
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " with " + numOfConfig + " configs : ");
        for (SmsBroadcastConfigInfo smsBroadcastConfigInfo : config) {
            riljLog(smsBroadcastConfigInfo.toString());
        }
        send(rr);
    }

    public void setGsmBroadcastActivation(boolean activate, Message response) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(91, response);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (activate) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    private void updateScreenState() {
        int i;
        boolean z = true;
        int oldState = this.mRadioScreenState;
        if (this.mDefaultDisplayState == 2 || this.mIsDevicePlugged) {
            i = 1;
        } else {
            i = 0;
        }
        this.mRadioScreenState = i;
        if (this.mRadioScreenState != oldState) {
            if (this.mRadioScreenState != 1) {
                z = false;
            }
            sendScreenState(z);
        }
    }

    private void sendScreenState(boolean on) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(61, null);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (!on) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + on);
        send(rr);
    }

    /* Access modifiers changed, original: protected */
    public void onRadioAvailable() {
        updateScreenState();
    }

    private RadioState getRadioStateFromInt(int stateInt) {
        switch (stateInt) {
            case 0:
                return RadioState.RADIO_OFF;
            case 1:
                return RadioState.RADIO_UNAVAILABLE;
            case 10:
                return RadioState.RADIO_ON;
            default:
                throw new RuntimeException("Unrecognized RIL_RadioState: " + stateInt);
        }
    }

    private void switchToRadioState(RadioState newState) {
        setRadioState(newState);
    }

    private void acquireWakeLock(RILRequest rr, int wakeLockType) {
        synchronized (rr) {
            if (rr.mWakeLockType != -1) {
                Rlog.d(RILJ_LOG_TAG, "Failed to aquire wakelock for " + rr.serialString());
                return;
            }
            Message msg;
            switch (wakeLockType) {
                case 0:
                    synchronized (this.mWakeLock) {
                        this.mWakeLock.acquire();
                        this.mWakeLockCount++;
                        this.mWlSequenceNum++;
                        msg = this.mSender.obtainMessage(2);
                        msg.arg1 = this.mWlSequenceNum;
                        this.mSender.sendMessageDelayed(msg, (long) this.mWakeLockTimeout);
                    }
                case 1:
                    synchronized (this.mAckWakeLock) {
                        this.mAckWakeLock.acquire();
                        this.mAckWlSequenceNum++;
                        msg = this.mSender.obtainMessage(4);
                        msg.arg1 = this.mAckWlSequenceNum;
                        this.mSender.sendMessageDelayed(msg, (long) this.mAckWakeLockTimeout);
                    }
                    break;
                default:
                    Rlog.w(RILJ_LOG_TAG, "Acquiring Invalid Wakelock type " + wakeLockType);
                    return;
            }
            rr.mWakeLockType = wakeLockType;
        }
    }

    private void decrementWakeLock(RILRequest rr) {
        synchronized (rr) {
            switch (rr.mWakeLockType) {
                case -1:
                case 1:
                    break;
                case 0:
                    synchronized (this.mWakeLock) {
                        if (this.mWakeLockCount > 1) {
                            this.mWakeLockCount--;
                        } else {
                            this.mWakeLockCount = 0;
                            this.mWakeLock.release();
                        }
                    }
                default:
                    Rlog.w(RILJ_LOG_TAG, "Decrementing Invalid Wakelock type " + rr.mWakeLockType);
                    break;
            }
            rr.mWakeLockType = -1;
        }
    }

    private boolean clearWakeLock(int wakeLockType) {
        if (wakeLockType == 0) {
            synchronized (this.mWakeLock) {
                if (this.mWakeLockCount != 0 || this.mWakeLock.isHeld()) {
                    Rlog.d(RILJ_LOG_TAG, "NOTE: mWakeLockCount is " + this.mWakeLockCount + "at time of clearing");
                    this.mWakeLockCount = 0;
                    this.mWakeLock.release();
                    return true;
                }
                return false;
            }
        }
        synchronized (this.mAckWakeLock) {
            if (this.mAckWakeLock.isHeld()) {
                this.mAckWakeLock.release();
                return true;
            }
            return false;
        }
    }

    private void send(RILRequest rr) {
        if (this.mSocket == null) {
            rr.onError(1, null);
            rr.release();
            return;
        }
        Message msg = this.mSender.obtainMessage(1, rr);
        acquireWakeLock(rr, 0);
        msg.sendToTarget();
    }

    private void processResponse(Parcel p) {
        int type = p.readInt();
        RILRequest rr;
        if (type == 1 || type == 4) {
            processUnsolicited(p, type);
        } else if (type == 0 || type == 3) {
            rr = processSolicited(p, type);
            if (rr != null) {
                if (type == 0) {
                    decrementWakeLock(rr);
                }
                rr.release();
            }
        } else if (type == 2) {
            int serial = p.readInt();
            synchronized (this.mRequestList) {
                rr = (RILRequest) this.mRequestList.get(serial);
            }
            if (rr == null) {
                Rlog.w(RILJ_LOG_TAG, "Unexpected solicited ack response! sn: " + serial);
            } else {
                decrementWakeLock(rr);
                riljLog(rr.serialString() + " Ack < " + requestToString(rr.mRequest));
            }
        }
    }

    private void clearRequestList(int error, boolean loggable) {
        synchronized (this.mRequestList) {
            int count = this.mRequestList.size();
            if (loggable) {
                Rlog.d(RILJ_LOG_TAG, "clearRequestList  mWakeLockCount=" + this.mWakeLockCount + " mRequestList=" + count);
            }
            for (int i = 0; i < count; i++) {
                RILRequest rr = (RILRequest) this.mRequestList.valueAt(i);
                if (loggable) {
                    Rlog.d(RILJ_LOG_TAG, i + ": [" + rr.mSerial + "] " + requestToString(rr.mRequest));
                }
                rr.onError(error, null);
                decrementWakeLock(rr);
                rr.release();
            }
            this.mRequestList.clear();
        }
    }

    private RILRequest findAndRemoveRequestFromList(int serial) {
        RILRequest rr;
        synchronized (this.mRequestList) {
            rr = (RILRequest) this.mRequestList.get(serial);
            if (rr != null) {
                this.mRequestList.remove(serial);
            }
        }
        return rr;
    }

    private RILRequest processSolicited(Parcel p, int type) {
        int serial = p.readInt();
        int error = p.readInt();
        RILRequest rr = findAndRemoveRequestFromList(serial);
        if (rr == null) {
            Rlog.w(RILJ_LOG_TAG, "Unexpected solicited response! sn: " + serial + " error: " + error);
            return null;
        }
        RILRadioLogWriter radioLogWriter;
        Thread thread;
        if (getRilVersion() >= 13 && type == 3) {
            Message msg = this.mSender.obtainMessage(3, RILRequest.obtain(800, null));
            acquireWakeLock(rr, 1);
            msg.sendToTarget();
            riljLog("Response received for " + rr.serialString() + " " + requestToString(rr.mRequest) + " Sending ack to ril.cpp");
        }
        Object ret = null;
        if (error == 0 || p.dataAvail() > 0) {
            try {
                switch (rr.mRequest) {
                    case 1:
                        ret = responseIccCardStatus(p);
                        break;
                    case 2:
                        ret = responseInts(p);
                        break;
                    case 3:
                        ret = responseInts(p);
                        break;
                    case 4:
                        ret = responseInts(p);
                        break;
                    case 5:
                        ret = responseInts(p);
                        break;
                    case 6:
                        ret = responseInts(p);
                        break;
                    case 7:
                        ret = responseInts(p);
                        break;
                    case 8:
                        ret = responseInts(p);
                        break;
                    case 9:
                        ret = responseCallList(p);
                        break;
                    case 10:
                        ret = responseVoid(p);
                        break;
                    case 11:
                        ret = responseString(p);
                        break;
                    case 12:
                        ret = responseVoid(p);
                        break;
                    case 13:
                        ret = responseVoid(p);
                        break;
                    case 14:
                        if (this.mTestingEmergencyCall.getAndSet(false) && this.mEmergencyCallbackModeRegistrant != null) {
                            riljLog("testing emergency call, notify ECM Registrants");
                            this.mEmergencyCallbackModeRegistrant.notifyRegistrant();
                        }
                        ret = responseVoid(p);
                        break;
                    case 15:
                        ret = responseVoid(p);
                        break;
                    case 16:
                        ret = responseVoid(p);
                        break;
                    case 17:
                        ret = responseVoid(p);
                        break;
                    case 18:
                        ret = responseFailCause(p);
                        break;
                    case 19:
                        ret = responseSignalStrength(p);
                        break;
                    case 20:
                        ret = responseStrings(p);
                        break;
                    case 21:
                        ret = responseStrings(p);
                        break;
                    case 22:
                        ret = responseStrings(p);
                        break;
                    case SmsHeader.ELT_ID_OBJECT_DISTR_INDICATOR /*23*/:
                        ret = responseVoid(p);
                        break;
                    case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /*24*/:
                        ret = responseVoid(p);
                        break;
                    case 25:
                        ret = responseSMS(p);
                        break;
                    case 26:
                        ret = responseSMS(p);
                        break;
                    case CallFailCause.CALL_FAIL_DESTINATION_OUT_OF_ORDER /*27*/:
                        ret = responseSetupDataCall(p);
                        break;
                    case CallFailCause.INVALID_NUMBER /*28*/:
                        ret = responseICC_IO(p);
                        break;
                    case CallFailCause.FACILITY_REJECTED /*29*/:
                        ret = responseVoid(p);
                        break;
                    case CallFailCause.STATUS_ENQUIRY /*30*/:
                        ret = responseVoid(p);
                        break;
                    case 31:
                        ret = responseInts(p);
                        break;
                    case 32:
                        ret = responseVoid(p);
                        break;
                    case 33:
                        ret = responseCallForward(p);
                        break;
                    case 34:
                        ret = responseVoid(p);
                        break;
                    case 35:
                        ret = responseInts(p);
                        break;
                    case 36:
                        ret = responseVoid(p);
                        break;
                    case 37:
                        ret = responseVoid(p);
                        break;
                    case 38:
                        ret = responseString(p);
                        break;
                    case 39:
                        ret = responseString(p);
                        break;
                    case RadioNVItems.RIL_NV_MIP_PROFILE_MN_HA_SS /*40*/:
                        ret = responseVoid(p);
                        break;
                    case 41:
                        ret = responseVoid(p);
                        break;
                    case 42:
                        ret = responseInts(p);
                        break;
                    case CallFailCause.ACCESS_INFORMATION_DISCARDED /*43*/:
                        ret = responseInts(p);
                        break;
                    case CallFailCause.CHANNEL_NOT_AVAIL /*44*/:
                        ret = responseVoid(p);
                        break;
                    case 45:
                        ret = responseInts(p);
                        break;
                    case 46:
                        ret = responseVoid(p);
                        break;
                    case 47:
                        ret = responseVoid(p);
                        break;
                    case 48:
                        ret = responseOperatorInfos(p);
                        break;
                    case CallFailCause.QOS_NOT_AVAIL /*49*/:
                        ret = responseVoid(p);
                        break;
                    case 50:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_CDMA_PRL_VERSION /*51*/:
                        ret = responseString(p);
                        break;
                    case RadioNVItems.RIL_NV_CDMA_BC10 /*52*/:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_CDMA_BC14 /*53*/:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_CDMA_SO68 /*54*/:
                        ret = responseInts(p);
                        break;
                    case 55:
                        ret = responseInts(p);
                        break;
                    case RadioNVItems.RIL_NV_CDMA_SO73_COP1TO7 /*56*/:
                        ret = responseInts(p);
                        break;
                    case 57:
                        ret = responseDataCallList(p);
                        break;
                    case 58:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_CDMA_EHRPD_FORCED /*59*/:
                        ret = responseRaw(p);
                        break;
                    case SmsCbConstants.MESSAGE_ID_GSMA_ALLOCATED_CHANNEL_60 /*60*/:
                        ret = responseStrings(p);
                        break;
                    case 61:
                        ret = responseVoid(p);
                        break;
                    case 62:
                        ret = responseVoid(p);
                        break;
                    case 63:
                        ret = responseInts(p);
                        break;
                    case 64:
                        ret = responseVoid(p);
                        break;
                    case CallFailCause.BEARER_SERVICE_NOT_IMPLEMENTED /*65*/:
                        ret = responseVoid(p);
                        break;
                    case 66:
                        ret = responseInts(p);
                        break;
                    case 67:
                        ret = responseString(p);
                        break;
                    case CallFailCause.ACM_LIMIT_EXCEEDED /*68*/:
                        ret = responseVoid(p);
                        break;
                    case CallFailCause.REQUESTED_FACILITY_NOT_IMPLEMENTED /*69*/:
                        ret = responseString(p);
                        break;
                    case CallFailCause.ONLY_DIGITAL_INFORMATION_BEARER_AVAILABLE /*70*/:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_25 /*71*/:
                        ret = responseInts(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_26 /*72*/:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_41 /*73*/:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_25 /*74*/:
                        ret = responseGetPreferredNetworkType(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_26 /*75*/:
                        ret = responseCellList(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_SCAN_PRIORITY_41 /*76*/:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_25 /*77*/:
                        ret = responseVoid(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_HIDDEN_BAND_PRIORITY_26 /*78*/:
                        ret = responseVoid(p);
                        break;
                    case 79:
                        ret = responseInts(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_NEXT_SCAN /*80*/:
                        ret = responseVoid(p);
                        break;
                    case 81:
                        ret = responseInts(p);
                        break;
                    case RadioNVItems.RIL_NV_LTE_BSR_MAX_TIME /*82*/:
                        ret = responseVoid(p);
                        break;
                    case 83:
                        ret = responseInts(p);
                        break;
                    case 84:
                        ret = responseVoid(p);
                        break;
                    case 85:
                        ret = responseVoid(p);
                        break;
                    case 86:
                        ret = responseVoid(p);
                        break;
                    case CallFailCause.USER_NOT_MEMBER_OF_CUG /*87*/:
                        ret = responseSMS(p);
                        break;
                    case CallFailCause.INCOMPATIBLE_DESTINATION /*88*/:
                        ret = responseVoid(p);
                        break;
                    case 89:
                        ret = responseGmsBroadcastConfig(p);
                        break;
                    case 90:
                        ret = responseVoid(p);
                        break;
                    case CallFailCause.INVALID_TRANSIT_NW_SELECTION /*91*/:
                        ret = responseVoid(p);
                        break;
                    case 92:
                        ret = responseCdmaBroadcastConfig(p);
                        break;
                    case 93:
                        ret = responseVoid(p);
                        break;
                    case 94:
                        ret = responseVoid(p);
                        break;
                    case CallFailCause.SEMANTICALLY_INCORRECT_MESSAGE /*95*/:
                        ret = responseStrings(p);
                        break;
                    case 96:
                        ret = responseInts(p);
                        break;
                    case CallFailCause.MESSAGE_TYPE_NON_IMPLEMENTED /*97*/:
                        ret = responseVoid(p);
                        break;
                    case CallFailCause.MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE /*98*/:
                        ret = responseStrings(p);
                        break;
                    case CallFailCause.INFORMATION_ELEMENT_NON_EXISTENT /*99*/:
                        ret = responseVoid(p);
                        break;
                    case 100:
                        ret = responseString(p);
                        break;
                    case CallFailCause.MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE /*101*/:
                        ret = responseVoid(p);
                        break;
                    case CallFailCause.RECOVERY_ON_TIMER_EXPIRED /*102*/:
                        ret = responseVoid(p);
                        break;
                    case 103:
                        ret = responseVoid(p);
                        break;
                    case 104:
                        ret = responseInts(p);
                        break;
                    case 105:
                        ret = responseString(p);
                        break;
                    case 106:
                        ret = responseVoid(p);
                        break;
                    case 107:
                        ret = responseICC_IO(p);
                        break;
                    case 108:
                        ret = responseInts(p);
                        break;
                    case 109:
                        ret = responseCellInfoList(p);
                        break;
                    case 110:
                        ret = responseVoid(p);
                        break;
                    case CallFailCause.PROTOCOL_ERROR_UNSPECIFIED /*111*/:
                        ret = responseVoid(p);
                        break;
                    case QtiCallConstants.CALL_FAIL_EXTRA_CODE_LOCAL_LOW_BATTERY /*112*/:
                        ret = responseInts(p);
                        break;
                    case 113:
                        ret = responseSMS(p);
                        break;
                    case 114:
                        ret = responseICC_IO(p);
                        break;
                    case 115:
                        ret = responseInts(p);
                        break;
                    case 116:
                        ret = responseVoid(p);
                        break;
                    case 117:
                        ret = responseICC_IO(p);
                        break;
                    case 118:
                        ret = responseString(p);
                        break;
                    case 119:
                        ret = responseVoid(p);
                        break;
                    case 120:
                        ret = responseVoid(p);
                        break;
                    case 121:
                        ret = responseVoid(p);
                        break;
                    case 122:
                        ret = responseVoid(p);
                        break;
                    case 123:
                        ret = responseVoid(p);
                        break;
                    case 124:
                        ret = responseHardwareConfig(p);
                        break;
                    case 125:
                        ret = responseICC_IOBase64(p);
                        break;
                    case 128:
                        ret = responseVoid(p);
                        break;
                    case 129:
                        ret = responseVoid(p);
                        break;
                    case 130:
                        ret = responseRadioCapability(p);
                        break;
                    case 131:
                        ret = responseRadioCapability(p);
                        break;
                    case 132:
                        ret = responseLceStatus(p);
                        break;
                    case 133:
                        ret = responseLceStatus(p);
                        break;
                    case 134:
                        ret = responseLceData(p);
                        break;
                    case 135:
                        ret = responseActivityData(p);
                        break;
                    case 136:
                        ret = responseString(p);
                        break;
                    case 137:
                        ret = responseInts(p);
                        break;
                    case 138:
                        ret = responseInts(p);
                        break;
                    case 139:
                        ret = responseInts(p);
                        break;
                    case 140:
                        ret = responseICC_IO(p);
                        break;
                    case 141:
                        ret = responseInts(p);
                        break;
                    case 142:
                        ret = responseVoid(p);
                        break;
                    case 143:
                        ret = responseICC_IO(p);
                        break;
                    default:
                        throw new RuntimeException("Unrecognized solicited response: " + rr.mRequest);
                }
            } catch (Throwable tr) {
                Rlog.w(RILJ_LOG_TAG, rr.serialString() + "< " + requestToString(rr.mRequest) + " exception, possible invalid RIL response", tr);
                if (rr.mResult != null) {
                    AsyncResult.forMessage(rr.mResult, null, tr);
                    rr.mResult.sendToTarget();
                }
                return rr;
            }
        }
        if (rr.mRequest == 129) {
            riljLog("Response to RIL_REQUEST_SHUTDOWN received. Error is " + error + " Setting Radio State to Unavailable regardless of error.");
            setRadioState(RadioState.RADIO_UNAVAILABLE);
        }
        switch (rr.mRequest) {
            case 3:
            case 5:
                if (this.mIccStatusChangedRegistrants != null) {
                    riljLog("ON enter sim puk fakeSimStatusChanged: reg count=" + this.mIccStatusChangedRegistrants.size());
                    this.mIccStatusChangedRegistrants.notifyRegistrants();
                    break;
                }
                break;
            case 46:
            case 47:
                if (TelBrand.IS_DCM && LOG_NS) {
                    radioLogWriter = new RILRadioLogWriter(2, 2);
                    radioLogWriter.setLog(rr.serialString() + "< " + requestToString(rr.mRequest) + " error: " + CommandException.fromRilErrno(error));
                    thread = new Thread(radioLogWriter);
                    if (thread != null) {
                        thread.start();
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
        if (error != 0) {
            switch (rr.mRequest) {
                case 2:
                case 4:
                case 6:
                case 7:
                case CallFailCause.ACCESS_INFORMATION_DISCARDED /*43*/:
                    if (this.mIccStatusChangedRegistrants != null) {
                        riljLog("ON some errors fakeSimStatusChanged: reg count=" + this.mIccStatusChangedRegistrants.size());
                        this.mIccStatusChangedRegistrants.notifyRegistrants();
                        break;
                    }
                    break;
                case 46:
                case 47:
                    if (TelBrand.IS_DCM && LOG_NS) {
                        radioLogWriter = new RILRadioLogWriter(2, 2);
                        radioLogWriter.setLog(rr.serialString() + "< " + requestToString(rr.mRequest) + " error: " + CommandException.fromRilErrno(error));
                        thread = new Thread(radioLogWriter);
                        if (thread != null) {
                            thread.start();
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
                case 130:
                    if (6 == error || 2 == error) {
                        ret = makeStaticRadioCapability();
                        error = 0;
                        break;
                    }
                case 135:
                    ret = new ModemActivityInfo(0, 0, 0, new int[5], 0, 0);
                    error = 0;
                    break;
            }
            if (error != 0) {
                rr.onError(error, ret);
            }
        }
        if (error == 0) {
            riljLog(rr.serialString() + "< " + requestToString(rr.mRequest) + " " + retToString(rr.mRequest, ret));
            if (rr.mResult != null) {
                AsyncResult.forMessage(rr.mResult, ret, null);
                rr.mResult.sendToTarget();
            }
        }
        this.mEventLog.writeOnRilSolicitedResponse(rr.mSerial, error, rr.mRequest, ret);
        return rr;
    }

    private RadioCapability makeStaticRadioCapability() {
        int raf = 1;
        String rafString = this.mContext.getResources().getString(17039472);
        if (!TextUtils.isEmpty(rafString)) {
            raf = RadioAccessFamily.rafTypeFromString(rafString);
        }
        RadioCapability rc = new RadioCapability(this.mInstanceId.intValue(), 0, 0, raf, "", 1);
        riljLog("Faking RIL_REQUEST_GET_RADIO_CAPABILITY response using " + raf);
        return rc;
    }

    static String retToString(int req, Object ret) {
        if (ret == null) {
            return "";
        }
        switch (req) {
            case 11:
            case 38:
            case 39:
            case 115:
            case 117:
            case 137:
                return "";
            default:
                String s;
                int length;
                StringBuilder stringBuilder;
                int i;
                int i2;
                if (ret instanceof int[]) {
                    int[] intArray = (int[]) ret;
                    length = intArray.length;
                    stringBuilder = new StringBuilder("{");
                    if (length > 0) {
                        stringBuilder.append(intArray[0]);
                        i = 1;
                        while (i < length) {
                            i2 = i + 1;
                            stringBuilder.append(", ").append(intArray[i]);
                            i = i2;
                        }
                    }
                    stringBuilder.append("}");
                    s = stringBuilder.toString();
                } else if (ret instanceof String[]) {
                    String[] strings = (String[]) ret;
                    length = strings.length;
                    stringBuilder = new StringBuilder("{");
                    if (length > 0) {
                        stringBuilder.append(strings[0]);
                        i = 1;
                        while (i < length) {
                            i2 = i + 1;
                            stringBuilder.append(", ").append(strings[i]);
                            i = i2;
                        }
                    }
                    stringBuilder.append("}");
                    s = stringBuilder.toString();
                } else if (req == 9) {
                    ArrayList<DriverCall> calls = (ArrayList) ret;
                    stringBuilder = new StringBuilder("{");
                    for (DriverCall dc : calls) {
                        stringBuilder.append("[").append(dc).append("] ");
                    }
                    stringBuilder.append("}");
                    s = stringBuilder.toString();
                } else if (req == 75) {
                    ArrayList<NeighboringCellInfo> cells = (ArrayList) ret;
                    stringBuilder = new StringBuilder("{");
                    for (NeighboringCellInfo cell : cells) {
                        stringBuilder.append("[").append(cell).append("] ");
                    }
                    stringBuilder.append("}");
                    s = stringBuilder.toString();
                } else if (req == 33) {
                    stringBuilder = new StringBuilder("{");
                    for (Object append : (CallForwardInfo[]) ret) {
                        stringBuilder.append("[").append(append).append("] ");
                    }
                    stringBuilder.append("}");
                    s = stringBuilder.toString();
                } else if (req == 124) {
                    ArrayList<HardwareConfig> hwcfgs = (ArrayList) ret;
                    stringBuilder = new StringBuilder(" ");
                    for (HardwareConfig hwcfg : hwcfgs) {
                        stringBuilder.append("[").append(hwcfg).append("] ");
                    }
                    s = stringBuilder.toString();
                } else {
                    s = ret.toString();
                }
                return s;
        }
    }

    private void processUnsolicited(Parcel p, int type) {
        Object ret;
        int response = p.readInt();
        if (getRilVersion() >= 13 && type == 4) {
            RILRequest rr = RILRequest.obtain(800, null);
            Message msg = this.mSender.obtainMessage(3, rr);
            acquireWakeLock(rr, 1);
            msg.sendToTarget();
            riljLog("Unsol response received for " + responseToString(response) + " Sending ack to ril.cpp");
        }
        switch (response) {
            case 1000:
                ret = responseVoid(p);
                break;
            case 1001:
                ret = responseVoid(p);
                break;
            case 1002:
                ret = responseVoid(p);
                break;
            case 1003:
                ret = responseString(p);
                break;
            case 1004:
                ret = responseString(p);
                break;
            case 1005:
                ret = responseInts(p);
                break;
            case 1006:
                ret = responseStrings(p);
                break;
            case CallFailCause.CDMA_NOT_EMERGENCY /*1008*/:
                ret = responseString(p);
                break;
            case CallFailCause.CDMA_ACCESS_BLOCKED /*1009*/:
                ret = responseSignalStrength(p);
                break;
            case 1010:
                ret = responseDataCallList(p);
                break;
            case 1011:
                ret = responseSuppServiceNotification(p);
                break;
            case 1012:
                ret = responseVoid(p);
                break;
            case 1013:
                ret = responseString(p);
                break;
            case 1014:
                ret = responseString(p);
                break;
            case CharacterSets.UTF_16 /*1015*/:
                ret = responseInts(p);
                break;
            case 1016:
                ret = responseVoid(p);
                break;
            case 1017:
                ret = responseSimRefresh(p);
                break;
            case 1018:
                ret = responseCallRing(p);
                break;
            case 1019:
                ret = responseVoid(p);
                break;
            case 1020:
                ret = responseCdmaSms(p);
                break;
            case 1021:
                ret = responseRaw(p);
                break;
            case 1022:
                ret = responseVoid(p);
                break;
            case 1023:
                ret = responseInts(p);
                break;
            case 1024:
                ret = responseVoid(p);
                break;
            case 1025:
                ret = responseCdmaCallWaiting(p);
                break;
            case 1026:
                ret = responseInts(p);
                break;
            case 1027:
                ret = responseCdmaInformationRecord(p);
                break;
            case 1028:
                ret = responseRaw(p);
                break;
            case 1029:
                ret = responseInts(p);
                break;
            case 1030:
                ret = responseVoid(p);
                break;
            case 1031:
                ret = responseInts(p);
                break;
            case 1032:
                ret = responseInts(p);
                break;
            case 1033:
                ret = responseVoid(p);
                break;
            case 1034:
                ret = responseInts(p);
                break;
            case 1035:
                ret = responseInts(p);
                break;
            case 1036:
                ret = responseCellInfoList(p);
                break;
            case 1037:
                ret = responseVoid(p);
                break;
            case 1038:
                ret = responseInts(p);
                break;
            case 1039:
                ret = responseInts(p);
                break;
            case 1040:
                ret = responseHardwareConfig(p);
                break;
            case 1042:
                ret = responseRadioCapability(p);
                break;
            case 1043:
                ret = responseSsData(p);
                break;
            case 1044:
                ret = responseString(p);
                break;
            case 1045:
                ret = responseLceData(p);
                break;
            case 1046:
                ret = responseVoid(p);
                break;
            case 1047:
                ret = responseAdnRecords(p);
                break;
            default:
                try {
                    throw new RuntimeException("Unrecognized unsol response: " + response);
                } catch (Throwable tr) {
                    Rlog.e(RILJ_LOG_TAG, "Exception processing unsol response: " + response + "Exception:" + tr.toString());
                    return;
                }
        }
        SmsMessage sms;
        switch (response) {
            case 1000:
                RadioState newState = getRadioStateFromInt(p.readInt());
                unsljLogMore(response, newState.toString());
                switchToRadioState(newState);
                break;
            case 1001:
                unsljLog(response);
                this.mCallStateRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
                break;
            case 1002:
                unsljLog(response);
                this.mVoiceNetworkStateRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
                break;
            case 1003:
                unsljLog(response);
                this.mEventLog.writeRilNewSms(response);
                String[] a = new String[2];
                a[1] = (String) ret;
                sms = SmsMessage.newFromCMT(a);
                if (this.mGsmSmsRegistrant != null) {
                    this.mGsmSmsRegistrant.notifyRegistrant(new AsyncResult(null, sms, null));
                    break;
                }
                break;
            case 1004:
                unsljLogRet(response, ret);
                if (this.mSmsStatusRegistrant != null) {
                    this.mSmsStatusRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1005:
                unsljLogRet(response, ret);
                Object smsIndex = (int[]) ret;
                if (smsIndex.length == 1) {
                    if (this.mSmsOnSimRegistrant != null) {
                        this.mSmsOnSimRegistrant.notifyRegistrant(new AsyncResult(null, smsIndex, null));
                        break;
                    }
                }
                riljLog(" NEW_SMS_ON_SIM ERROR with wrong length " + smsIndex.length);
                break;
                break;
            case 1006:
                Object resp = (String[]) ret;
                if (resp.length < 2) {
                    resp = new String[]{((String[]) ret)[0], null};
                }
                unsljLogMore(response, resp[0]);
                if (this.mUSSDRegistrant != null) {
                    this.mUSSDRegistrant.notifyRegistrant(new AsyncResult(null, resp, null));
                    break;
                }
                break;
            case CallFailCause.CDMA_NOT_EMERGENCY /*1008*/:
                unsljLogRet(response, ret);
                long nitzReceiveTime = p.readLong();
                Object result = new Object[]{ret, Long.valueOf(nitzReceiveTime)};
                if (!SystemProperties.getBoolean("telephony.test.ignore.nitz", false)) {
                    if (this.mNITZTimeRegistrant != null) {
                        this.mNITZTimeRegistrant.notifyRegistrant(new AsyncResult(null, result, null));
                    }
                    this.mLastNITZTimeInfo = result;
                    break;
                }
                riljLog("ignoring UNSOL_NITZ_TIME_RECEIVED");
                break;
            case CallFailCause.CDMA_ACCESS_BLOCKED /*1009*/:
                if (this.mSignalStrengthRegistrant != null) {
                    this.mSignalStrengthRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1010:
                unsljLogRet(response, ret);
                this.mDataNetworkStateRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                break;
            case 1011:
                unsljLogRet(response, ret);
                if (this.mSsnRegistrant != null) {
                    this.mSsnRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1012:
                unsljLog(response);
                if (this.mCatSessionEndRegistrant != null) {
                    this.mCatSessionEndRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1013:
                unsljLog(response);
                if (this.mCatProCmdRegistrant != null) {
                    this.mCatProCmdRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1014:
                unsljLog(response);
                if (this.mCatEventRegistrant != null) {
                    this.mCatEventRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case CharacterSets.UTF_16 /*1015*/:
                unsljLogRet(response, ret);
                if (this.mCatCallSetUpRegistrant != null) {
                    this.mCatCallSetUpRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1016:
                unsljLog(response);
                if (this.mIccSmsFullRegistrant != null) {
                    this.mIccSmsFullRegistrant.notifyRegistrant();
                    break;
                }
                break;
            case 1017:
                unsljLogRet(response, ret);
                if (this.mIccRefreshRegistrants != null) {
                    this.mIccRefreshRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1018:
                unsljLogRet(response, ret);
                if (this.mRingRegistrant != null) {
                    this.mRingRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1019:
                unsljLog(response);
                if (this.mIccStatusChangedRegistrants != null) {
                    this.mIccStatusChangedRegistrants.notifyRegistrants();
                    break;
                }
                break;
            case 1020:
                unsljLog(response);
                this.mEventLog.writeRilNewSms(response);
                sms = (SmsMessage) ret;
                if (this.mCdmaSmsRegistrant != null) {
                    this.mCdmaSmsRegistrant.notifyRegistrant(new AsyncResult(null, sms, null));
                    break;
                }
                break;
            case 1021:
                unsljLogvRet(response, IccUtils.bytesToHexString((byte[]) ret));
                if (this.mGsmBroadcastSmsRegistrant != null) {
                    this.mGsmBroadcastSmsRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1022:
                unsljLog(response);
                if (this.mIccSmsFullRegistrant != null) {
                    this.mIccSmsFullRegistrant.notifyRegistrant();
                    break;
                }
                break;
            case 1023:
                unsljLogvRet(response, ret);
                if (this.mRestrictedStateRegistrant != null) {
                    this.mRestrictedStateRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1024:
                unsljLog(response);
                if (this.mEmergencyCallbackModeRegistrant != null) {
                    this.mEmergencyCallbackModeRegistrant.notifyRegistrant();
                    break;
                }
                break;
            case 1025:
                unsljLogRet(response, ret);
                if (this.mCallWaitingInfoRegistrants != null) {
                    this.mCallWaitingInfoRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1026:
                unsljLogRet(response, ret);
                if (this.mOtaProvisionRegistrants != null) {
                    this.mOtaProvisionRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1027:
                try {
                    for (CdmaInformationRecords rec : (ArrayList) ret) {
                        unsljLogRet(response, rec);
                        notifyRegistrantsCdmaInfoRec(rec);
                    }
                    break;
                } catch (ClassCastException e) {
                    Rlog.e(RILJ_LOG_TAG, "Unexpected exception casting to listInfoRecs", e);
                    break;
                }
            case 1028:
                unsljLogvRet(response, IccUtils.bytesToHexString((byte[]) ret));
                ByteBuffer oemHookResponse = ByteBuffer.wrap((byte[]) ret);
                oemHookResponse.order(ByteOrder.nativeOrder());
                if (!TelBrand.IS_SBM) {
                    if (this.mUnsolOemHookRawRegistrant != null) {
                        this.mUnsolOemHookRawRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                        break;
                    }
                } else if (!isQcUnsolOemHookResp(oemHookResponse)) {
                    if (this.mUnsolOemHookRawRegistrant != null) {
                        Rlog.d(RILJ_LOG_TAG, "External OEM message, to be notified");
                        this.mUnsolOemHookRawRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                        break;
                    }
                } else {
                    Rlog.d(RILJ_LOG_TAG, "OEM ID check Passed");
                    processUnsolOemhookResponse(oemHookResponse);
                    break;
                }
                break;
            case 1029:
                unsljLogvRet(response, ret);
                if (this.mRingbackToneRegistrants != null) {
                    this.mRingbackToneRegistrants.notifyRegistrants(new AsyncResult(null, Boolean.valueOf(((int[]) ret)[0] == 1), null));
                    break;
                }
                break;
            case 1030:
                unsljLogRet(response, ret);
                if (this.mResendIncallMuteRegistrants != null) {
                    this.mResendIncallMuteRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1031:
                unsljLogRet(response, ret);
                if (this.mCdmaSubscriptionChangedRegistrants != null) {
                    this.mCdmaSubscriptionChangedRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1032:
                unsljLogRet(response, ret);
                if (this.mCdmaPrlChangedRegistrants != null) {
                    this.mCdmaPrlChangedRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1033:
                unsljLogRet(response, ret);
                if (this.mExitEmergencyCallbackModeRegistrants != null) {
                    this.mExitEmergencyCallbackModeRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
                    break;
                }
                break;
            case 1034:
                unsljLogRet(response, ret);
                setRadioPower(false, null);
                setCdmaSubscriptionSource(this.mCdmaSubscription, null);
                setCellInfoListRate(Integer.MAX_VALUE, null);
                notifyRegistrantsRilConnectionChanged(((int[]) ret)[0]);
                if (!this.mRILConnected_SetInitialAttachApn_OnceSkip) {
                    riljLog("RILD crash, call setInitialAttachApn");
                    setInitialAttachApn(this.mSetInitialAttachApn_Apn, this.mSetInitialAttachApn_Protocol, this.mSetInitialAttachApn_AuthType, this.mSetInitialAttachApn_Username, this.mSetInitialAttachApn_Password, null);
                }
                if (!this.mRILConnected_UpdateOemDataSettings_OnceSkip) {
                    riljLog("RILD crash, call updateOemDataSettings");
                    updateOemDataSettings(this.mUpdateOemDataSettings_MobileData, this.mUpdateOemDataSettings_DataRoaming, true, null);
                    break;
                }
                break;
            case 1035:
                unsljLogRet(response, ret);
                if (this.mVoiceRadioTechChangedRegistrants != null) {
                    this.mVoiceRadioTechChangedRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1036:
                unsljLogRet(response, ret);
                if (this.mRilCellInfoListRegistrants != null) {
                    this.mRilCellInfoListRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1037:
                unsljLog(response);
                this.mImsNetworkStateChangedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
                break;
            case 1038:
                unsljLogRet(response, ret);
                if (this.mSubscriptionStatusRegistrants != null) {
                    this.mSubscriptionStatusRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1039:
                unsljLogRet(response, ret);
                this.mEventLog.writeRilSrvcc(((int[]) ret)[0]);
                if (this.mSrvccStateRegistrants != null) {
                    this.mSrvccStateRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1040:
                unsljLogRet(response, ret);
                if (this.mHardwareConfigChangeRegistrants != null) {
                    this.mHardwareConfigChangeRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1042:
                unsljLogRet(response, ret);
                if (this.mPhoneRadioCapabilityChangedRegistrants != null) {
                    this.mPhoneRadioCapabilityChangedRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1043:
                unsljLogRet(response, ret);
                if (this.mSsRegistrant != null) {
                    this.mSsRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1044:
                unsljLogRet(response, ret);
                if (this.mCatCcAlphaRegistrant != null) {
                    this.mCatCcAlphaRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1045:
                unsljLogRet(response, ret);
                if (this.mLceInfoRegistrant != null) {
                    this.mLceInfoRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1046:
                unsljLog(response);
                if (this.mAdnInitDoneRegistrants != null) {
                    this.mAdnInitDoneRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                    break;
                }
                break;
            case 1047:
                unsljLog(response);
                if (this.mAdnRecordsInfoRegistrants != null) {
                    this.mAdnRecordsInfoRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                    break;
                }
                break;
        }
    }

    private void notifyRegistrantsRilConnectionChanged(int rilVer) {
        this.mRilVersion = rilVer;
        if (this.mRilConnectedRegistrants != null) {
            this.mRilConnectedRegistrants.notifyRegistrants(new AsyncResult(null, new Integer(rilVer), null));
        }
    }

    private Object responseInts(Parcel p) {
        int numInts = p.readInt();
        int[] response = new int[numInts];
        for (int i = 0; i < numInts; i++) {
            response[i] = p.readInt();
        }
        return response;
    }

    private Object responseFailCause(Parcel p) {
        LastCallFailCause failCause = new LastCallFailCause();
        failCause.causeCode = p.readInt();
        if (p.dataAvail() > 0) {
            failCause.vendorCause = p.readString();
        }
        return failCause;
    }

    private Object responseVoid(Parcel p) {
        return null;
    }

    private Object responseCallForward(Parcel p) {
        int numInfos = p.readInt();
        CallForwardInfo[] infos = new CallForwardInfo[numInfos];
        for (int i = 0; i < numInfos; i++) {
            infos[i] = new CallForwardInfo();
            infos[i].status = p.readInt();
            infos[i].reason = p.readInt();
            infos[i].serviceClass = p.readInt();
            infos[i].toa = p.readInt();
            infos[i].number = p.readString();
            infos[i].timeSeconds = p.readInt();
        }
        return infos;
    }

    private Object responseSuppServiceNotification(Parcel p) {
        SuppServiceNotification notification = new SuppServiceNotification();
        notification.notificationType = p.readInt();
        notification.code = p.readInt();
        notification.index = p.readInt();
        notification.type = p.readInt();
        notification.number = p.readString();
        return notification;
    }

    private Object responseCdmaSms(Parcel p) {
        return SmsMessage.newFromParcel(p);
    }

    private Object responseString(Parcel p) {
        return p.readString();
    }

    private Object responseStrings(Parcel p) {
        return p.readStringArray();
    }

    private Object responseRaw(Parcel p) {
        return p.createByteArray();
    }

    private Object responseSMS(Parcel p) {
        return new SmsResponse(p.readInt(), p.readString(), p.readInt());
    }

    private Object responseICC_IO(Parcel p) {
        return new IccIoResult(p.readInt(), p.readInt(), p.readString());
    }

    private Object responseICC_IOBase64(Parcel p) {
        int sw1 = p.readInt();
        int sw2 = p.readInt();
        String s = p.readString();
        return new IccIoResult(sw1, sw2, s != null ? Base64.decode(s, 0) : (byte[]) null);
    }

    private Object responseIccCardStatus(Parcel p) {
        IccCardStatus cardStatus = new IccCardStatus();
        cardStatus.setCardState(p.readInt());
        cardStatus.setUniversalPinState(p.readInt());
        cardStatus.mGsmUmtsSubscriptionAppIndex = p.readInt();
        cardStatus.mCdmaSubscriptionAppIndex = p.readInt();
        cardStatus.mImsSubscriptionAppIndex = p.readInt();
        int numApplications = p.readInt();
        if (numApplications > 8) {
            numApplications = 8;
        }
        cardStatus.mApplications = new IccCardApplicationStatus[numApplications];
        for (int i = 0; i < numApplications; i++) {
            IccCardApplicationStatus appStatus = new IccCardApplicationStatus();
            appStatus.app_type = appStatus.AppTypeFromRILInt(p.readInt());
            appStatus.app_state = appStatus.AppStateFromRILInt(p.readInt());
            appStatus.perso_substate = appStatus.PersoSubstateFromRILInt(p.readInt());
            appStatus.aid = p.readString();
            appStatus.app_label = p.readString();
            appStatus.pin1_replaced = p.readInt();
            appStatus.pin1 = appStatus.PinStateFromRILInt(p.readInt());
            appStatus.pin2 = appStatus.PinStateFromRILInt(p.readInt());
            cardStatus.mApplications[i] = appStatus;
        }
        return cardStatus;
    }

    private Object responseSimRefresh(Parcel p) {
        IccRefreshResponse response = new IccRefreshResponse();
        response.refreshResult = p.readInt();
        response.efId = p.readInt();
        response.aid = p.readString();
        return response;
    }

    private Object responseCallList(Parcel p) {
        int num = p.readInt();
        ArrayList<DriverCall> response = new ArrayList(num);
        for (int i = 0; i < num; i++) {
            boolean z;
            DriverCall dc = new DriverCall();
            dc.state = DriverCall.stateFromCLCC(p.readInt());
            dc.index = p.readInt();
            dc.TOA = p.readInt();
            if (p.readInt() != 0) {
                z = true;
            } else {
                z = false;
            }
            dc.isMpty = z;
            if (p.readInt() != 0) {
                z = true;
            } else {
                z = false;
            }
            dc.isMT = z;
            dc.als = p.readInt();
            if (p.readInt() == 0) {
                z = false;
            } else {
                z = true;
            }
            dc.isVoice = z;
            if (p.readInt() != 0) {
                z = true;
            } else {
                z = false;
            }
            dc.isVoicePrivacy = z;
            dc.number = p.readString();
            dc.numberPresentation = DriverCall.presentationFromCLIP(p.readInt());
            dc.name = p.readString();
            dc.namePresentation = DriverCall.presentationFromCLIP(p.readInt());
            if (p.readInt() == 1) {
                dc.uusInfo = new UUSInfo();
                dc.uusInfo.setType(p.readInt());
                dc.uusInfo.setDcs(p.readInt());
                dc.uusInfo.setUserData(p.createByteArray());
                riljLogv(String.format("Incoming UUS : type=%d, dcs=%d, length=%d", new Object[]{Integer.valueOf(dc.uusInfo.getType()), Integer.valueOf(dc.uusInfo.getDcs()), Integer.valueOf(dc.uusInfo.getUserData().length)}));
                riljLogv("Incoming UUS : data (string)=" + new String(dc.uusInfo.getUserData()));
                riljLogv("Incoming UUS : data (hex): " + IccUtils.bytesToHexString(dc.uusInfo.getUserData()));
            } else {
                riljLogv("Incoming UUS : NOT present!");
            }
            dc.number = PhoneNumberUtils.stringFromStringAndTOA(dc.number, dc.TOA);
            response.add(dc);
            if (dc.isVoicePrivacy) {
                this.mVoicePrivacyOnRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is enabled");
            } else {
                this.mVoicePrivacyOffRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is disabled");
            }
        }
        Collections.sort(response);
        if (num == 0 && this.mTestingEmergencyCall.getAndSet(false) && this.mEmergencyCallbackModeRegistrant != null) {
            riljLog("responseCallList: call ended, testing emergency call, notify ECM Registrants");
            this.mEmergencyCallbackModeRegistrant.notifyRegistrant();
        }
        return response;
    }

    private DataCallResponse getDataCallResponse(Parcel p, int version) {
        DataCallResponse dataCall = new DataCallResponse();
        dataCall.version = version;
        String addresses;
        if (version < 5) {
            dataCall.cid = p.readInt();
            dataCall.active = p.readInt();
            dataCall.type = p.readString();
            addresses = p.readString();
            if (!TextUtils.isEmpty(addresses)) {
                dataCall.addresses = addresses.split(" ");
            }
        } else {
            dataCall.status = p.readInt();
            dataCall.suggestedRetryTime = p.readInt();
            dataCall.cid = p.readInt();
            dataCall.active = p.readInt();
            dataCall.type = p.readString();
            dataCall.ifname = p.readString();
            if (dataCall.status == DcFailCause.NONE.getErrorCode() && TextUtils.isEmpty(dataCall.ifname)) {
                throw new RuntimeException("getDataCallResponse, no ifname");
            }
            addresses = p.readString();
            if (!TextUtils.isEmpty(addresses)) {
                dataCall.addresses = addresses.split(" ");
            }
            String dnses = p.readString();
            if (!TextUtils.isEmpty(dnses)) {
                dataCall.dnses = dnses.split(" ");
            }
            String gateways = p.readString();
            if (!TextUtils.isEmpty(gateways)) {
                dataCall.gateways = gateways.split(" ");
            }
            if (version >= 10) {
                String pcscf = p.readString();
                if (!TextUtils.isEmpty(pcscf)) {
                    dataCall.pcscf = pcscf.split(" ");
                }
            }
            if (version >= 11) {
                dataCall.mtu = p.readInt();
            }
        }
        return dataCall;
    }

    private Object responseDataCallList(Parcel p) {
        int ver = p.readInt();
        int num = p.readInt();
        riljLog("responseDataCallList ver=" + ver + " num=" + num);
        ArrayList<DataCallResponse> response = new ArrayList(num);
        for (int i = 0; i < num; i++) {
            response.add(getDataCallResponse(p, ver));
        }
        this.mEventLog.writeRilDataCallList(response);
        return response;
    }

    private Object responseSetupDataCall(Parcel p) {
        int ver = p.readInt();
        int num = p.readInt();
        if (ver < 5) {
            DataCallResponse dataCall = new DataCallResponse();
            dataCall.version = ver;
            dataCall.cid = Integer.parseInt(p.readString());
            dataCall.ifname = p.readString();
            if (TextUtils.isEmpty(dataCall.ifname)) {
                throw new RuntimeException("RIL_REQUEST_SETUP_DATA_CALL response, no ifname");
            }
            String addresses = p.readString();
            if (!TextUtils.isEmpty(addresses)) {
                dataCall.addresses = addresses.split(" ");
            }
            if (num >= 4) {
                String dnses = p.readString();
                riljLog("responseSetupDataCall got dnses=" + dnses);
                if (!TextUtils.isEmpty(dnses)) {
                    dataCall.dnses = dnses.split(" ");
                }
            }
            if (num >= 5) {
                String gateways = p.readString();
                riljLog("responseSetupDataCall got gateways=" + gateways);
                if (!TextUtils.isEmpty(gateways)) {
                    dataCall.gateways = gateways.split(" ");
                }
            }
            if (num < 6) {
                return dataCall;
            }
            String pcscf = p.readString();
            riljLog("responseSetupDataCall got pcscf=" + pcscf);
            if (TextUtils.isEmpty(pcscf)) {
                return dataCall;
            }
            dataCall.pcscf = pcscf.split(" ");
            return dataCall;
        } else if (num == 1) {
            return getDataCallResponse(p, ver);
        } else {
            throw new RuntimeException("RIL_REQUEST_SETUP_DATA_CALL response expecting 1 RIL_Data_Call_response_v5 got " + num);
        }
    }

    private Object responseOperatorInfos(Parcel p) {
        String[] strings = (String[]) responseStrings(p);
        if (strings.length % 4 != 0) {
            throw new RuntimeException("RIL_REQUEST_QUERY_AVAILABLE_NETWORKS: invalid response. Got " + strings.length + " strings, expected multible of 4");
        }
        ArrayList<OperatorInfo> ret = new ArrayList(strings.length / 4);
        for (int i = 0; i < strings.length; i += 4) {
            ret.add(new OperatorInfo(strings[i + 0], strings[i + 1], strings[i + 2], strings[i + 3]));
        }
        return ret;
    }

    private Object responseCellList(Parcel p) {
        int num = p.readInt();
        ArrayList<NeighboringCellInfo> response = new ArrayList();
        int radioType = ((TelephonyManager) this.mContext.getSystemService("phone")).getDataNetworkType(SubscriptionManager.getSubId(this.mInstanceId.intValue())[0]);
        if (radioType != 0) {
            for (int i = 0; i < num; i++) {
                response.add(new NeighboringCellInfo(p.readInt(), p.readString(), radioType));
            }
        }
        return response;
    }

    private Object responseGetPreferredNetworkType(Parcel p) {
        int[] response = (int[]) responseInts(p);
        if (response.length >= 1) {
            this.mPreferredNetworkType = response[0];
        }
        return response;
    }

    private Object responseGmsBroadcastConfig(Parcel p) {
        int num = p.readInt();
        ArrayList<SmsBroadcastConfigInfo> response = new ArrayList(num);
        for (int i = 0; i < num; i++) {
            response.add(new SmsBroadcastConfigInfo(p.readInt(), p.readInt(), p.readInt(), p.readInt(), p.readInt() == 1));
        }
        return response;
    }

    private Object responseCdmaBroadcastConfig(Parcel p) {
        int[] response;
        int numServiceCategories = p.readInt();
        int i;
        if (numServiceCategories == 0) {
            response = new int[94];
            response[0] = 31;
            for (i = 1; i < 94; i += 3) {
                response[i + 0] = i / 3;
                response[i + 1] = 1;
                response[i + 2] = 0;
            }
        } else {
            int numInts = (numServiceCategories * 3) + 1;
            response = new int[numInts];
            response[0] = numServiceCategories;
            for (i = 1; i < numInts; i++) {
                response[i] = p.readInt();
            }
        }
        return response;
    }

    private Object responseSignalStrength(Parcel p) {
        return SignalStrength.makeSignalStrengthFromRilParcel(p);
    }

    private ArrayList<CdmaInformationRecords> responseCdmaInformationRecord(Parcel p) {
        int numberOfInfoRecs = p.readInt();
        ArrayList<CdmaInformationRecords> response = new ArrayList(numberOfInfoRecs);
        for (int i = 0; i < numberOfInfoRecs; i++) {
            response.add(new CdmaInformationRecords(p));
        }
        return response;
    }

    private Object responseCdmaCallWaiting(Parcel p) {
        CdmaCallWaitingNotification notification = new CdmaCallWaitingNotification();
        notification.number = p.readString();
        notification.numberPresentation = CdmaCallWaitingNotification.presentationFromCLIP(p.readInt());
        notification.name = p.readString();
        notification.namePresentation = notification.numberPresentation;
        notification.isPresent = p.readInt();
        notification.signalType = p.readInt();
        notification.alertPitch = p.readInt();
        notification.signal = p.readInt();
        notification.numberType = p.readInt();
        notification.numberPlan = p.readInt();
        return notification;
    }

    private Object responseCallRing(Parcel p) {
        char[] response = new char[]{(char) p.readInt(), (char) p.readInt(), (char) p.readInt(), (char) p.readInt()};
        this.mEventLog.writeRilCallRing(response);
        return response;
    }

    private void notifyRegistrantsCdmaInfoRec(CdmaInformationRecords infoRec) {
        if (infoRec.record instanceof CdmaDisplayInfoRec) {
            if (this.mDisplayInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mDisplayInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaSignalInfoRec) {
            if (this.mSignalInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mSignalInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaNumberInfoRec) {
            if (this.mNumberInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mNumberInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaRedirectingNumberInfoRec) {
            if (this.mRedirNumInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mRedirNumInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaLineControlInfoRec) {
            if (this.mLineControlInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mLineControlInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaT53ClirInfoRec) {
            if (this.mT53ClirInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mT53ClirInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if ((infoRec.record instanceof CdmaT53AudioControlInfoRec) && this.mT53AudCntrlInfoRegistrants != null) {
            unsljLogRet(1027, infoRec.record);
            this.mT53AudCntrlInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
        }
    }

    private ArrayList<CellInfo> responseCellInfoList(Parcel p) {
        int numberOfInfoRecs = p.readInt();
        ArrayList<CellInfo> response = new ArrayList(numberOfInfoRecs);
        for (int i = 0; i < numberOfInfoRecs; i++) {
            response.add((CellInfo) CellInfo.CREATOR.createFromParcel(p));
        }
        return response;
    }

    private Object responseHardwareConfig(Parcel p) {
        int num = p.readInt();
        ArrayList<HardwareConfig> response = new ArrayList(num);
        for (int i = 0; i < num; i++) {
            HardwareConfig hw;
            int type = p.readInt();
            switch (type) {
                case 0:
                    hw = new HardwareConfig(type);
                    hw.assignModem(p.readString(), p.readInt(), p.readInt(), p.readInt(), p.readInt(), p.readInt(), p.readInt());
                    break;
                case 1:
                    hw = new HardwareConfig(type);
                    hw.assignSim(p.readString(), p.readInt(), p.readString());
                    break;
                default:
                    throw new RuntimeException("RIL_REQUEST_GET_HARDWARE_CONFIG invalid hardward type:" + type);
            }
            response.add(hw);
        }
        return response;
    }

    private Object responseRadioCapability(Parcel p) {
        int version = p.readInt();
        int session = p.readInt();
        int phase = p.readInt();
        int rat = p.readInt();
        String logicModemUuid = p.readString();
        int status = p.readInt();
        riljLog("responseRadioCapability: version= " + version + ", session=" + session + ", phase=" + phase + ", rat=" + rat + ", logicModemUuid=" + logicModemUuid + ", status=" + status);
        return new RadioCapability(this.mInstanceId.intValue(), session, phase, rat, logicModemUuid, status);
    }

    private Object responseLceData(Parcel p) {
        ArrayList<Integer> capacityResponse = new ArrayList();
        int capacityDownKbps = p.readInt();
        int confidenceLevel = p.readByte();
        int lceSuspended = p.readByte();
        riljLog("LCE capacity information received: capacity=" + capacityDownKbps + " confidence=" + confidenceLevel + " lceSuspended=" + lceSuspended);
        capacityResponse.add(Integer.valueOf(capacityDownKbps));
        capacityResponse.add(Integer.valueOf(confidenceLevel));
        capacityResponse.add(Integer.valueOf(lceSuspended));
        return capacityResponse;
    }

    private Object responseLceStatus(Parcel p) {
        ArrayList<Integer> statusResponse = new ArrayList();
        int lceStatus = p.readByte();
        int actualInterval = p.readInt();
        riljLog("LCE status information received: lceStatus=" + lceStatus + " actualInterval=" + actualInterval);
        statusResponse.add(Integer.valueOf(lceStatus));
        statusResponse.add(Integer.valueOf(actualInterval));
        return statusResponse;
    }

    private Object responseActivityData(Parcel p) {
        int sleepModeTimeMs = p.readInt();
        int idleModeTimeMs = p.readInt();
        int[] txModeTimeMs = new int[5];
        for (int i = 0; i < 5; i++) {
            txModeTimeMs[i] = p.readInt();
        }
        int rxModeTimeMs = p.readInt();
        riljLog("Modem activity info received: sleepModeTimeMs=" + sleepModeTimeMs + " idleModeTimeMs=" + idleModeTimeMs + " txModeTimeMs[]=" + Arrays.toString(txModeTimeMs) + " rxModeTimeMs=" + rxModeTimeMs);
        return new ModemActivityInfo(SystemClock.elapsedRealtime(), sleepModeTimeMs, idleModeTimeMs, txModeTimeMs, rxModeTimeMs, 0);
    }

    private Object responseAdnRecords(Parcel p) {
        int numRecords = p.readInt();
        SimPhoneBookAdnRecord[] AdnRecordsInfoGroup = new SimPhoneBookAdnRecord[numRecords];
        for (int i = 0; i < numRecords; i++) {
            AdnRecordsInfoGroup[i] = new SimPhoneBookAdnRecord();
            AdnRecordsInfoGroup[i].mRecordIndex = p.readInt();
            AdnRecordsInfoGroup[i].mAlphaTag = p.readString();
            AdnRecordsInfoGroup[i].mNumber = SimPhoneBookAdnRecord.ConvertToPhoneNumber(p.readString());
            int numEmails = p.readInt();
            if (numEmails > 0) {
                AdnRecordsInfoGroup[i].mEmailCount = numEmails;
                AdnRecordsInfoGroup[i].mEmails = new String[numEmails];
                for (int j = 0; j < numEmails; j++) {
                    AdnRecordsInfoGroup[i].mEmails[j] = p.readString();
                }
            }
            int numAnrs = p.readInt();
            if (numAnrs > 0) {
                AdnRecordsInfoGroup[i].mAdNumCount = numAnrs;
                AdnRecordsInfoGroup[i].mAdNumbers = new String[numAnrs];
                for (int k = 0; k < numAnrs; k++) {
                    AdnRecordsInfoGroup[i].mAdNumbers[k] = SimPhoneBookAdnRecord.ConvertToPhoneNumber(p.readString());
                }
            }
        }
        riljLog(Arrays.toString(AdnRecordsInfoGroup));
        return AdnRecordsInfoGroup;
    }

    static String requestToString(int request) {
        switch (request) {
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
                return "ENTER_NETWORK_DEPERSONALIZATION";
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
            case SmsHeader.ELT_ID_OBJECT_DISTR_INDICATOR /*23*/:
                return "RADIO_POWER";
            case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /*24*/:
                return "DTMF";
            case 25:
                return "SEND_SMS";
            case 26:
                return "SEND_SMS_EXPECT_MORE";
            case CallFailCause.CALL_FAIL_DESTINATION_OUT_OF_ORDER /*27*/:
                return "SETUP_DATA_CALL";
            case CallFailCause.INVALID_NUMBER /*28*/:
                return "SIM_IO";
            case CallFailCause.FACILITY_REJECTED /*29*/:
                return "SEND_USSD";
            case CallFailCause.STATUS_ENQUIRY /*30*/:
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
            case 38:
                return "GET_IMEI";
            case 39:
                return "GET_IMEISV";
            case RadioNVItems.RIL_NV_MIP_PROFILE_MN_HA_SS /*40*/:
                return "ANSWER";
            case 41:
                return "DEACTIVATE_DATA_CALL";
            case 42:
                return "QUERY_FACILITY_LOCK";
            case CallFailCause.ACCESS_INFORMATION_DISCARDED /*43*/:
                return "SET_FACILITY_LOCK";
            case CallFailCause.CHANNEL_NOT_AVAIL /*44*/:
                return "CHANGE_BARRING_PASSWORD";
            case 45:
                return "QUERY_NETWORK_SELECTION_MODE";
            case 46:
                return "SET_NETWORK_SELECTION_AUTOMATIC";
            case 47:
                return "SET_NETWORK_SELECTION_MANUAL";
            case 48:
                return "QUERY_AVAILABLE_NETWORKS ";
            case CallFailCause.QOS_NOT_AVAIL /*49*/:
                return "DTMF_START";
            case 50:
                return "DTMF_STOP";
            case RadioNVItems.RIL_NV_CDMA_PRL_VERSION /*51*/:
                return "BASEBAND_VERSION";
            case RadioNVItems.RIL_NV_CDMA_BC10 /*52*/:
                return "SEPARATE_CONNECTION";
            case RadioNVItems.RIL_NV_CDMA_BC14 /*53*/:
                return "SET_MUTE";
            case RadioNVItems.RIL_NV_CDMA_SO68 /*54*/:
                return "GET_MUTE";
            case 55:
                return "QUERY_CLIP";
            case RadioNVItems.RIL_NV_CDMA_SO73_COP1TO7 /*56*/:
                return "LAST_DATA_CALL_FAIL_CAUSE";
            case 57:
                return "DATA_CALL_LIST";
            case 58:
                return "RESET_RADIO";
            case RadioNVItems.RIL_NV_CDMA_EHRPD_FORCED /*59*/:
                return "OEM_HOOK_RAW";
            case SmsCbConstants.MESSAGE_ID_GSMA_ALLOCATED_CHANNEL_60 /*60*/:
                return "OEM_HOOK_STRINGS";
            case 61:
                return "SCREEN_STATE";
            case 62:
                return "SET_SUPP_SVC_NOTIFICATION";
            case 63:
                return "WRITE_SMS_TO_SIM";
            case 64:
                return "DELETE_SMS_ON_SIM";
            case CallFailCause.BEARER_SERVICE_NOT_IMPLEMENTED /*65*/:
                return "SET_BAND_MODE";
            case 66:
                return "QUERY_AVAILABLE_BAND_MODE";
            case 67:
                return "REQUEST_STK_GET_PROFILE";
            case CallFailCause.ACM_LIMIT_EXCEEDED /*68*/:
                return "REQUEST_STK_SET_PROFILE";
            case CallFailCause.REQUESTED_FACILITY_NOT_IMPLEMENTED /*69*/:
                return "REQUEST_STK_SEND_ENVELOPE_COMMAND";
            case CallFailCause.ONLY_DIGITAL_INFORMATION_BEARER_AVAILABLE /*70*/:
                return "REQUEST_STK_SEND_TERMINAL_RESPONSE";
            case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_25 /*71*/:
                return "REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM";
            case RadioNVItems.RIL_NV_LTE_BAND_ENABLE_26 /*72*/:
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
            case 79:
                return "RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE";
            case RadioNVItems.RIL_NV_LTE_NEXT_SCAN /*80*/:
                return "RIL_REQUEST_SET_TTY_MODE";
            case 81:
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
            case CallFailCause.USER_NOT_MEMBER_OF_CUG /*87*/:
                return "RIL_REQUEST_CDMA_SEND_SMS";
            case CallFailCause.INCOMPATIBLE_DESTINATION /*88*/:
                return "RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE";
            case 89:
                return "RIL_REQUEST_GSM_GET_BROADCAST_CONFIG";
            case 90:
                return "RIL_REQUEST_GSM_SET_BROADCAST_CONFIG";
            case CallFailCause.INVALID_TRANSIT_NW_SELECTION /*91*/:
                return "RIL_REQUEST_GSM_BROADCAST_ACTIVATION";
            case 92:
                return "RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG";
            case 93:
                return "RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG";
            case 94:
                return "RIL_REQUEST_CDMA_BROADCAST_ACTIVATION";
            case CallFailCause.SEMANTICALLY_INCORRECT_MESSAGE /*95*/:
                return "RIL_REQUEST_CDMA_SUBSCRIPTION";
            case 96:
                return "RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM";
            case CallFailCause.MESSAGE_TYPE_NON_IMPLEMENTED /*97*/:
                return "RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM";
            case CallFailCause.MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE /*98*/:
                return "RIL_REQUEST_DEVICE_IDENTITY";
            case CallFailCause.INFORMATION_ELEMENT_NON_EXISTENT /*99*/:
                return "REQUEST_EXIT_EMERGENCY_CALLBACK_MODE";
            case 100:
                return "RIL_REQUEST_GET_SMSC_ADDRESS";
            case CallFailCause.MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE /*101*/:
                return "RIL_REQUEST_SET_SMSC_ADDRESS";
            case CallFailCause.RECOVERY_ON_TIMER_EXPIRED /*102*/:
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
            case 108:
                return "RIL_REQUEST_VOICE_RADIO_TECH";
            case 109:
                return "RIL_REQUEST_GET_CELL_INFO_LIST";
            case 110:
                return "RIL_REQUEST_SET_CELL_INFO_LIST_RATE";
            case CallFailCause.PROTOCOL_ERROR_UNSPECIFIED /*111*/:
                return "RIL_REQUEST_SET_INITIAL_ATTACH_APN";
            case QtiCallConstants.CALL_FAIL_EXTRA_CODE_LOCAL_LOW_BATTERY /*112*/:
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
            case 130:
                return "RIL_REQUEST_GET_RADIO_CAPABILITY";
            case 131:
                return "RIL_REQUEST_SET_RADIO_CAPABILITY";
            case 132:
                return "RIL_REQUEST_START_LCE";
            case 133:
                return "RIL_REQUEST_STOP_LCE";
            case 134:
                return "RIL_REQUEST_PULL_LCEDATA";
            case 135:
                return "RIL_REQUEST_GET_ACTIVITY_INFO";
            case 136:
                return "RIL_REQUEST_SIM_GET_ATR";
            case 137:
                return "RIL_REQUEST_CAF_SIM_OPEN_CHANNEL_WITH_P2";
            case 138:
                return "RIL_REQUEST_GET_ADN_RECORD";
            case 139:
                return "RIL_REQUEST_UPDATE_ADN_RECORD";
            case 140:
                return "SIM_TRANSMIT_BASIC_SHARP";
            case 141:
                return "SIM_OPEN_CHANNEL_SHARP";
            case 142:
                return "SIM_CLOSE_CHANNEL_SHARP";
            case 143:
                return "SIM_TRANSMIT_CHANNEL_SHARP";
            case 800:
                return "RIL_RESPONSE_ACKNOWLEDGEMENT";
            default:
                return "<unknown request>";
        }
    }

    static String responseToString(int request) {
        switch (request) {
            case 1000:
                return "UNSOL_RESPONSE_RADIO_STATE_CHANGED";
            case 1001:
                return "UNSOL_RESPONSE_CALL_STATE_CHANGED";
            case 1002:
                return "UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED";
            case 1003:
                return "UNSOL_RESPONSE_NEW_SMS";
            case 1004:
                return "UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT";
            case 1005:
                return "UNSOL_RESPONSE_NEW_SMS_ON_SIM";
            case 1006:
                return "UNSOL_ON_USSD";
            case CallFailCause.CDMA_PREEMPTED /*1007*/:
                return "UNSOL_ON_USSD_REQUEST";
            case CallFailCause.CDMA_NOT_EMERGENCY /*1008*/:
                return "UNSOL_NITZ_TIME_RECEIVED";
            case CallFailCause.CDMA_ACCESS_BLOCKED /*1009*/:
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
            case 1042:
                return "RIL_UNSOL_RADIO_CAPABILITY";
            case 1043:
                return "UNSOL_ON_SS";
            case 1044:
                return "UNSOL_STK_CC_ALPHA_NOTIFY";
            case 1045:
                return "UNSOL_LCE_INFO_RECV";
            case 1046:
                return "RIL_UNSOL_RESPONSE_ADN_INIT_DONE";
            case 1047:
                return "RIL_UNSOL_RESPONSE_ADN_RECORDS";
            default:
                return "<unknown response>";
        }
    }

    private void riljLog(String msg) {
        Rlog.d(RILJ_LOG_TAG, msg + (this.mInstanceId != null ? " [SUB" + this.mInstanceId + "]" : ""));
    }

    private void riljLogv(String msg) {
        Rlog.v(RILJ_LOG_TAG, msg + (this.mInstanceId != null ? " [SUB" + this.mInstanceId + "]" : ""));
    }

    private void unsljLog(int response) {
        riljLog("[UNSL]< " + responseToString(response));
    }

    private void unsljLogMore(int response, String more) {
        riljLog("[UNSL]< " + responseToString(response) + " " + more);
    }

    private void unsljLogRet(int response, Object ret) {
        riljLog("[UNSL]< " + responseToString(response) + " " + retToString(response, ret));
    }

    private void unsljLogvRet(int response, Object ret) {
        riljLogv("[UNSL]< " + responseToString(response) + " " + retToString(response, ret));
    }

    private Object responseSsData(Parcel p) {
        SsData ssData = new SsData();
        ssData.serviceType = ssData.ServiceTypeFromRILInt(p.readInt());
        ssData.requestType = ssData.RequestTypeFromRILInt(p.readInt());
        ssData.teleserviceType = ssData.TeleserviceTypeFromRILInt(p.readInt());
        ssData.serviceClass = p.readInt();
        ssData.result = p.readInt();
        int num = p.readInt();
        int i;
        if (ssData.serviceType.isTypeCF() && ssData.requestType.isTypeInterrogation()) {
            ssData.cfInfo = new CallForwardInfo[num];
            for (i = 0; i < num; i++) {
                ssData.cfInfo[i] = new CallForwardInfo();
                ssData.cfInfo[i].status = p.readInt();
                ssData.cfInfo[i].reason = p.readInt();
                ssData.cfInfo[i].serviceClass = p.readInt();
                ssData.cfInfo[i].toa = p.readInt();
                ssData.cfInfo[i].number = p.readString();
                ssData.cfInfo[i].timeSeconds = p.readInt();
                riljLog("[SS Data] CF Info " + i + " : " + ssData.cfInfo[i]);
            }
        } else {
            ssData.ssInfo = new int[num];
            for (i = 0; i < num; i++) {
                ssData.ssInfo[i] = p.readInt();
                riljLog("[SS Data] SS Info " + i + " : " + ssData.ssInfo[i]);
            }
        }
        return ssData;
    }

    public void getDeviceIdentity(Message response) {
        RILRequest rr = RILRequest.obtain(98, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getCDMASubscription(Message response) {
        RILRequest rr = RILRequest.obtain(95, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setPhoneType(int phoneType) {
        riljLog("setPhoneType=" + phoneType + " old value=" + this.mPhoneType);
        this.mPhoneType = phoneType;
    }

    public void queryCdmaRoamingPreference(Message response) {
        RILRequest rr = RILRequest.obtain(79, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        RILRequest rr = RILRequest.obtain(78, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(cdmaRoamingType);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + cdmaRoamingType);
        send(rr);
    }

    public void setCdmaSubscriptionSource(int cdmaSubscription, Message response) {
        RILRequest rr = RILRequest.obtain(77, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(cdmaSubscription);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + cdmaSubscription);
        send(rr);
    }

    public void getCdmaSubscriptionSource(Message response) {
        RILRequest rr = RILRequest.obtain(104, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void queryTTYMode(Message response) {
        RILRequest rr = RILRequest.obtain(81, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setTTYMode(int ttyMode, Message response) {
        RILRequest rr = RILRequest.obtain(80, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(ttyMode);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + ttyMode);
        send(rr);
    }

    public void sendCDMAFeatureCode(String FeatureCode, Message response) {
        RILRequest rr = RILRequest.obtain(84, response);
        rr.mParcel.writeString(FeatureCode);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + FeatureCode);
        send(rr);
    }

    public void getCdmaBroadcastConfig(Message response) {
        send(RILRequest.obtain(92, response));
    }

    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message response) {
        int i;
        int i2;
        RILRequest rr = RILRequest.obtain(93, response);
        ArrayList<CdmaSmsBroadcastConfigInfo> processedConfigs = new ArrayList();
        for (CdmaSmsBroadcastConfigInfo config : configs) {
            for (i2 = config.getFromServiceCategory(); i2 <= config.getToServiceCategory(); i2++) {
                processedConfigs.add(new CdmaSmsBroadcastConfigInfo(i2, i2, config.getLanguage(), config.isSelected()));
            }
        }
        CdmaSmsBroadcastConfigInfo[] rilConfigs = (CdmaSmsBroadcastConfigInfo[]) processedConfigs.toArray(configs);
        rr.mParcel.writeInt(rilConfigs.length);
        for (i2 = 0; i2 < rilConfigs.length; i2++) {
            rr.mParcel.writeInt(rilConfigs[i2].getFromServiceCategory());
            rr.mParcel.writeInt(rilConfigs[i2].getLanguage());
            Parcel parcel = rr.mParcel;
            if (rilConfigs[i2].isSelected()) {
                i = 1;
            } else {
                i = 0;
            }
            parcel.writeInt(i);
        }
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " with " + rilConfigs.length + " configs : ");
        for (CdmaSmsBroadcastConfigInfo cdmaSmsBroadcastConfigInfo : rilConfigs) {
            riljLog(cdmaSmsBroadcastConfigInfo.toString());
        }
        send(rr);
    }

    public void setCdmaBroadcastActivation(boolean activate, Message response) {
        int i = 1;
        RILRequest rr = RILRequest.obtain(94, response);
        rr.mParcel.writeInt(1);
        Parcel parcel = rr.mParcel;
        if (activate) {
            i = 0;
        }
        parcel.writeInt(i);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void exitEmergencyCallbackMode(Message response) {
        RILRequest rr = RILRequest.obtain(99, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void requestIsimAuthentication(String nonce, Message response) {
        RILRequest rr = RILRequest.obtain(105, response);
        rr.mParcel.writeString(nonce);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void requestIccSimAuthentication(int authContext, String data, String aid, Message response) {
        RILRequest rr = RILRequest.obtain(125, response);
        rr.mParcel.writeInt(authContext);
        rr.mParcel.writeString(data);
        rr.mParcel.writeString(aid);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getCellInfoList(Message result) {
        RILRequest rr = RILRequest.obtain(109, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setCellInfoListRate(int rateInMillis, Message response) {
        riljLog("setCellInfoListRate: " + rateInMillis);
        RILRequest rr = RILRequest.obtain(110, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(rateInMillis);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setInitialAttachApn(String apn, String protocol, int authType, String username, String password, Message result) {
        RILRequest rr = RILRequest.obtain(CallFailCause.PROTOCOL_ERROR_UNSPECIFIED, result);
        riljLog("Set RIL_REQUEST_SET_INITIAL_ATTACH_APN");
        if (authType == -1) {
            authType = TextUtils.isEmpty(username) ? 0 : 3;
        }
        this.mSetInitialAttachApn_Apn = apn;
        this.mSetInitialAttachApn_Protocol = protocol;
        this.mSetInitialAttachApn_AuthType = authType;
        this.mSetInitialAttachApn_Username = username;
        this.mSetInitialAttachApn_Password = password;
        this.mRILConnected_SetInitialAttachApn_OnceSkip = false;
        rr.mParcel.writeString(apn);
        rr.mParcel.writeString(protocol);
        rr.mParcel.writeInt(authType);
        rr.mParcel.writeString(username);
        rr.mParcel.writeString(password);
        String log_apn = apn;
        String log_user = username;
        String log_password = password;
        if (!"eng".equals(Build.TYPE)) {
            if (apn != null) {
                log_apn = "*****";
            }
            if (username != null) {
                log_user = "*****";
            }
            if (password != null) {
                log_password = "*****";
            }
        }
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ", apn:" + log_apn + ", protocol:" + protocol + ", authType:" + authType + ", username:" + log_user + ", password:" + log_password);
        send(rr);
    }

    public void setDataProfile(DataProfile[] dps, Message result) {
        riljLog("Set RIL_REQUEST_SET_DATA_PROFILE");
        RILRequest rr = RILRequest.obtain(128, null);
        DataProfile.toParcel(rr.mParcel, dps);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " with " + dps + " Data Profiles : ");
        for (DataProfile dataProfile : dps) {
            riljLog(dataProfile.toString());
        }
        send(rr);
    }

    public void testingEmergencyCall() {
        riljLog("testingEmergencyCall");
        this.mTestingEmergencyCall.set(true);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("RIL: " + this);
        pw.println(" mSocket=" + this.mSocket);
        pw.println(" mSenderThread=" + this.mSenderThread);
        pw.println(" mSender=" + this.mSender);
        pw.println(" mReceiverThread=" + this.mReceiverThread);
        pw.println(" mReceiver=" + this.mReceiver);
        pw.println(" mWakeLock=" + this.mWakeLock);
        pw.println(" mWakeLockTimeout=" + this.mWakeLockTimeout);
        synchronized (this.mRequestList) {
            synchronized (this.mWakeLock) {
                pw.println(" mWakeLockCount=" + this.mWakeLockCount);
            }
            int count = this.mRequestList.size();
            pw.println(" mRequestList count=" + count);
            for (int i = 0; i < count; i++) {
                RILRequest rr = (RILRequest) this.mRequestList.valueAt(i);
                pw.println("  [" + rr.mSerial + "] " + requestToString(rr.mRequest));
            }
        }
        pw.println(" mLastNITZTimeInfo=" + Arrays.toString(this.mLastNITZTimeInfo));
        pw.println(" mTestingEmergencyCall=" + this.mTestingEmergencyCall.get());
    }

    public void iccOpenLogicalChannel(String AID, Message response) {
        RILRequest rr = RILRequest.obtain(115, response);
        rr.mParcel.writeString(AID);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void iccOpenLogicalChannel(String AID, byte p2, Message response) {
        RILRequest rr = RILRequest.obtain(137, response);
        rr.mParcel.writeByte(p2);
        rr.mParcel.writeString(AID);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void iccCloseLogicalChannel(int channel, Message response) {
        RILRequest rr = RILRequest.obtain(116, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(channel);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void iccTransmitApduLogicalChannel(int channel, int cla, int instruction, int p1, int p2, int p3, String data, Message response) {
        if (channel <= 0) {
            throw new RuntimeException("Invalid channel in iccTransmitApduLogicalChannel: " + channel);
        }
        iccTransmitApduHelper(117, channel, cla, instruction, p1, p2, p3, data, response);
    }

    public void iccTransmitApduBasicChannel(int cla, int instruction, int p1, int p2, int p3, String data, Message response) {
        iccTransmitApduHelper(114, 0, cla, instruction, p1, p2, p3, data, response);
    }

    public void getAtr(Message response) {
        RILRequest rr = RILRequest.obtain(136, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(0);
        riljLog(rr.serialString() + "> iccGetAtr: " + requestToString(rr.mRequest) + " " + 0);
        send(rr);
    }

    private void iccTransmitApduHelper(int rilCommand, int channel, int cla, int instruction, int p1, int p2, int p3, String data, Message response) {
        RILRequest rr = RILRequest.obtain(rilCommand, response);
        rr.mParcel.writeInt(channel);
        rr.mParcel.writeInt(cla);
        rr.mParcel.writeInt(instruction);
        rr.mParcel.writeInt(p1);
        rr.mParcel.writeInt(p2);
        rr.mParcel.writeInt(p3);
        rr.mParcel.writeString(data);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void nvReadItem(int itemID, Message response) {
        RILRequest rr = RILRequest.obtain(118, response);
        rr.mParcel.writeInt(itemID);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ' ' + itemID);
        send(rr);
    }

    public void nvWriteItem(int itemID, String itemValue, Message response) {
        RILRequest rr = RILRequest.obtain(119, response);
        rr.mParcel.writeInt(itemID);
        rr.mParcel.writeString(itemValue);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ' ' + itemID + ": " + itemValue);
        send(rr);
    }

    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response) {
        RILRequest rr = RILRequest.obtain(120, response);
        rr.mParcel.writeByteArray(preferredRoamingList);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " (" + preferredRoamingList.length + " bytes)");
        send(rr);
    }

    public void nvResetConfig(int resetType, Message response) {
        RILRequest rr = RILRequest.obtain(121, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(resetType);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ' ' + resetType);
        send(rr);
    }

    public void setRadioCapability(RadioCapability rc, Message response) {
        RILRequest rr = RILRequest.obtain(131, response);
        rr.mParcel.writeInt(rc.getVersion());
        rr.mParcel.writeInt(rc.getSession());
        rr.mParcel.writeInt(rc.getPhase());
        rr.mParcel.writeInt(rc.getRadioAccessFamily());
        rr.mParcel.writeString(rc.getLogicalModemUuid());
        rr.mParcel.writeInt(rc.getStatus());
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + rc.toString());
        send(rr);
    }

    public void getRadioCapability(Message response) {
        RILRequest rr = RILRequest.obtain(130, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void startLceService(int reportIntervalMs, boolean pullMode, Message response) {
        RILRequest rr = RILRequest.obtain(132, response);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(reportIntervalMs);
        rr.mParcel.writeInt(pullMode ? 1 : 0);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void stopLceService(Message response) {
        RILRequest rr = RILRequest.obtain(133, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void pullLceData(Message response) {
        RILRequest rr = RILRequest.obtain(134, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getModemActivityInfo(Message response) {
        RILRequest rr = RILRequest.obtain(135, response);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
        Message msg = this.mSender.obtainMessage(5);
        msg.obj = null;
        msg.arg1 = rr.mSerial;
        this.mSender.sendMessageDelayed(msg, 2000);
    }

    public void getAdnRecord(Message result) {
        RILRequest rr = RILRequest.obtain(138, result);
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void updateAdnRecord(SimPhoneBookAdnRecord adnRecordInfo, Message result) {
        RILRequest rr = RILRequest.obtain(139, result);
        rr.mParcel.writeInt(adnRecordInfo.getRecordIndex());
        rr.mParcel.writeString(adnRecordInfo.getAlphaTag());
        rr.mParcel.writeString(SimPhoneBookAdnRecord.ConvertToRecordNumber(adnRecordInfo.getNumber()));
        int numEmails = adnRecordInfo.getNumEmails();
        rr.mParcel.writeInt(numEmails);
        for (int i = 0; i < numEmails; i++) {
            rr.mParcel.writeString(adnRecordInfo.getEmails()[i]);
        }
        int numAdNumbers = adnRecordInfo.getNumAdNumbers();
        rr.mParcel.writeInt(numAdNumbers);
        for (int j = 0; j < numAdNumbers; j++) {
            rr.mParcel.writeString(SimPhoneBookAdnRecord.ConvertToRecordNumber(adnRecordInfo.getAdNumbers()[j]));
        }
        riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " with " + adnRecordInfo.toString());
        send(rr);
    }

    public void iccExchangeAPDU(int cla, int command, int channel, int p1, int p2, int p3, String data, Message result) {
        if (channel < 0) {
            throw new RuntimeException("Invalid channel in iccTransmitApduLogicalChannel: " + channel);
        }
        RILRequest rr;
        if (channel == 0) {
            rr = RILRequest.obtain(140, result);
        } else {
            rr = RILRequest.obtain(143, result);
        }
        rr.mParcel.writeInt(channel);
        rr.mParcel.writeInt(cla);
        rr.mParcel.writeInt(command);
        rr.mParcel.writeInt(p1);
        rr.mParcel.writeInt(p2);
        rr.mParcel.writeInt(p3);
        rr.mParcel.writeString(data);
        send(rr);
    }

    public void iccOpenChannel(String AID, Message result) {
        RILRequest rr = RILRequest.obtain(141, result);
        rr.mParcel.writeString(AID);
        send(rr);
    }

    public void iccCloseChannel(int channel, Message result) {
        RILRequest rr = RILRequest.obtain(142, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(channel);
        send(rr);
    }

    private void upDomesticInService(Context context, Intent intent) {
        riljLog("upDomesticInService start ");
        ServiceState ss = ServiceState.newFromBundle(intent.getExtras());
        if (ss != null) {
            riljLog("upDomesticInService gsm.domesticinservice before: " + String.valueOf(isDomesticInService()));
            int state = ss.getState();
            int airplaneMode = Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0);
            riljLog("upDomesticInService state : " + Integer.toString(state) + ", airplaneMode : " + Integer.toString(airplaneMode));
            if (state == 0 && airplaneMode != 1) {
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

    public static boolean isDomesticInService() {
        return SystemProperties.getBoolean("gsm.domesticinservice", false);
    }

    public void updateOemDataSettings(boolean mobileData, boolean dataRoaming, boolean epcCapability, Message response) {
        riljLog("RIL_REQUEST_OEM_HOOK_RAW_UPDATE_DATA_SETTINGS > " + mobileData + " " + dataRoaming);
        this.mRILConnected_UpdateOemDataSettings_OnceSkip = false;
        this.mUpdateOemDataSettings_MobileData = mobileData;
        this.mUpdateOemDataSettings_DataRoaming = dataRoaming;
        byte[] request = new byte[(this.mHeaderSize + 3)];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(OEM_IDENTIFIER.getBytes());
        reqBuffer.putInt(591828);
        reqBuffer.putInt(3);
        if (mobileData) {
            reqBuffer.put((byte) 1);
        } else {
            reqBuffer.put((byte) 0);
        }
        if (dataRoaming) {
            reqBuffer.put((byte) 1);
        } else {
            reqBuffer.put((byte) 0);
        }
        if (epcCapability) {
            reqBuffer.put((byte) 1);
        } else {
            reqBuffer.put((byte) 0);
        }
        invokeOemRilRequestRaw(request, response);
        riljLog("updateOemDataSettings is called.");
    }

    public void reportDecryptStatus(boolean decrypt, Message result) {
        if (TelBrand.IS_DCM) {
            RILRequest rr = RILRequest.obtain(CallFailCause.RECOVERY_ON_TIMER_EXPIRED, result);
            rr.mParcel.writeInt(1);
            rr.mParcel.writeInt(decrypt ? 3 : 2);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + decrypt);
            send(rr);
        }
    }

    private void processUnsolOemhookResponse(ByteBuffer oemHookResponse) {
        int responseId = oemHookResponse.getInt();
        Rlog.d(RILJ_LOG_TAG, "Response ID in RIL_UNSOL_OEM_HOOK_RAW is " + responseId);
        int responseSize = oemHookResponse.getInt();
        if (responseSize < 0) {
            Rlog.e(RILJ_LOG_TAG, "Response Size is Invalid " + responseSize);
            return;
        }
        byte[] responseData = new byte[responseSize];
        if (oemHookResponse.remaining() == responseSize) {
            oemHookResponse.get(responseData, 0, responseSize);
            switch (responseId) {
                case 592825:
                    if (TelBrand.IS_SBM) {
                        notifyOemSignalStrength(responseData);
                        break;
                    }
                    break;
                case 592826:
                    notifyLteBandInfo(responseData);
                    break;
                case 592827:
                    if (TelBrand.IS_SBM) {
                        notifySpeechCodec(responseData);
                        break;
                    }
                    break;
                default:
                    Rlog.d(RILJ_LOG_TAG, "Response ID " + responseId + " is not served in this process.");
                    break;
            }
            return;
        }
        Rlog.e(RILJ_LOG_TAG, "Response Size(" + responseSize + ") doesnot match remaining bytes(" + oemHookResponse.remaining() + ") in the buffer. So, don't process further");
    }

    private Object toIntArrayFromByteArray(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.nativeOrder());
        int numInts = buf.capacity() / 4;
        int[] response = new int[numInts];
        for (int i = 0; i < numInts; i++) {
            response[i] = buf.getInt();
        }
        return response;
    }

    /* Access modifiers changed, original: protected */
    public void notifySpeechCodec(byte[] data) {
        if (TelBrand.IS_SBM && this.mSpeechCodecRegistrant != null) {
            this.mSpeechCodecRegistrant.notifyRegistrant(new AsyncResult(null, toIntArrayFromByteArray(data), null));
        }
    }

    private boolean isQcUnsolOemHookResp(ByteBuffer oemHookResponse) {
        if (oemHookResponse.capacity() < this.mHeaderSize) {
            Rlog.d(RILJ_LOG_TAG, "RIL_UNSOL_OEM_HOOK_RAW data size is " + oemHookResponse.capacity());
            return false;
        }
        byte[] oemIdBytes = new byte[OEM_IDENTIFIER.length()];
        oemHookResponse.get(oemIdBytes);
        String oemIdString = new String(oemIdBytes);
        Rlog.d(RILJ_LOG_TAG, "Oem ID in RIL_UNSOL_OEM_HOOK_RAW is " + oemIdString);
        if (oemIdString.equals(OEM_IDENTIFIER)) {
            return true;
        }
        return false;
    }

    /* Access modifiers changed, original: protected */
    public void notifyOemSignalStrength(byte[] data) {
        if (this.mOemSignalStrengthRegistrant != null) {
            this.mOemSignalStrengthRegistrant.notifyRegistrant(new AsyncResult(null, toIntArrayFromByteArray(data), null));
        }
    }

    /* Access modifiers changed, original: protected */
    public void notifyLteBandInfo(byte[] data) {
        if (this.mLteBandInfoRegistrant != null) {
            ByteBuffer lteBandInfo = ByteBuffer.wrap(data);
            lteBandInfo.order(ByteOrder.nativeOrder());
            this.mLteBandInfoRegistrant.notifyRegistrant(new AsyncResult(null, new Integer(lteBandInfo.getInt()), null));
        }
    }

    public boolean isRunning() {
        boolean transitionedSender = false;
        boolean transitionedReceiver = false;
        if (this.mSenderThread != null && this.mSenderThread.isAlive()) {
            transitionedSender = true;
        }
        if (this.mReceiverThread != null && this.mReceiverThread.isAlive()) {
            transitionedReceiver = true;
        }
        return transitionedSender ? transitionedReceiver : false;
    }

    public void getSimLock(Message response) {
        byte[] request = new byte[(this.mHeaderSize + 0)];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(OEM_IDENTIFIER.getBytes());
        reqBuffer.putInt(525315);
        reqBuffer.putInt(0);
        invokeOemRilRequestRaw(request, response);
        riljLog("[EXTDBG] getSimLock");
    }

    public void setVoLTERoaming(int isEnabled, Message response) {
        byte[] request = new byte[(this.mHeaderSize + 4)];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(OEM_IDENTIFIER.getBytes());
        reqBuffer.putInt(593824);
        reqBuffer.putInt(4);
        reqBuffer.putInt(isEnabled);
        invokeOemRilRequestRaw(request, response);
        riljLog("isEnabled: " + isEnabled);
    }

    public void getVoLTERoaming(Message response) {
        byte[] request = new byte[(this.mHeaderSize + 0)];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(OEM_IDENTIFIER.getBytes());
        reqBuffer.putInt(593825);
        reqBuffer.putInt(0);
        invokeOemRilRequestRaw(request, response);
        riljLog("getVoLTERoaming is called.");
    }
}
