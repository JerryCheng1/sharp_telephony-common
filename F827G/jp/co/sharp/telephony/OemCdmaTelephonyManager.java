package jp.co.sharp.telephony;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.google.android.mms.pdu.PduHeaders;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedList;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class OemCdmaTelephonyManager {
    private static final int CDMA_START = 33554432;
    private static final int IMSI_CLASS_0_ADDR_NUM = 255;
    public static final int OEM_RIL_CDMA_SPC_LOCK_CODE_LENGTH = 6;
    public static final int OEM_RIL_REQUEST_CDMA_CHECK_SUBSIDY_LOCK_PASSWD = 33554442;
    public static final int OEM_RIL_REQUEST_CDMA_FACTORY_RESET = 33554614;
    public static final int OEM_RIL_REQUEST_CDMA_FIELD_TEST_GET_CALL_PROCESSING_DATA = 33554449;
    public static final int OEM_RIL_REQUEST_CDMA_FIELD_TEST_GET_EVDO_DATA = 33554450;
    public static final int OEM_RIL_REQUEST_CDMA_FIELD_TEST_GET_SERVICE_OPTION = 33554443;
    public static final int OEM_RIL_REQUEST_CDMA_FIELD_TEST_SET_SERVICE_OPTION = 33554444;
    public static final int OEM_RIL_REQUEST_CDMA_GET_BC0 = 33554656;
    public static final int OEM_RIL_REQUEST_CDMA_GET_BC1 = 33554658;
    public static final int OEM_RIL_REQUEST_CDMA_GET_BC10 = 33554608;
    public static final int OEM_RIL_REQUEST_CDMA_GET_BC14 = 33554610;
    public static final int OEM_RIL_REQUEST_CDMA_GET_CDMA_PRL_VERSION = 33554441;
    public static final int OEM_RIL_REQUEST_CDMA_GET_DDTM_DEFAULT_PREFERENCE = 33554615;
    public static final int OEM_RIL_REQUEST_CDMA_GET_MOB_P_REV = 33554439;
    public static final int OEM_RIL_REQUEST_CDMA_GET_NAM_INFO = 33554433;
    public static final int OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM = 33554454;
    public static final int OEM_RIL_REQUEST_CDMA_GET_SID_NID_PAIRS = 33554435;
    public static final int OEM_RIL_REQUEST_CDMA_SET_ACTIVE_PROF = 33554613;
    public static final int OEM_RIL_REQUEST_CDMA_SET_BC0 = 33554657;
    public static final int OEM_RIL_REQUEST_CDMA_SET_BC1 = 33554659;
    public static final int OEM_RIL_REQUEST_CDMA_SET_BC10 = 33554609;
    public static final int OEM_RIL_REQUEST_CDMA_SET_BC14 = 33554611;
    public static final int OEM_RIL_REQUEST_CDMA_SET_DDTM_DEFAULT_PREFERENCE = 33554616;
    public static final int OEM_RIL_REQUEST_CDMA_SET_MOB_P_REV = 33554440;
    public static final int OEM_RIL_REQUEST_CDMA_SET_NAM_INFO = 33554434;
    public static final int OEM_RIL_REQUEST_CDMA_SET_PRL = 33554612;
    public static final int OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM = 33554453;
    public static final int OEM_RIL_REQUEST_CDMA_SET_SID_NID_PAIRS = 33554436;
    public static final int OEM_RIL_UNSOL_RESP_MIP_ERROR = 33554864;
    static final int SIZE_OF_BYTE = 1;
    static final int SIZE_OF_CHAR = 2;
    static final int SIZE_OF_INT = 4;
    static final int SIZE_OF_LONG = 8;
    static final int SIZE_OF_NV_ITEM = 128;
    static final int SIZE_OF_SHORT = 2;
    private static final String TAG = "OemCdmaTelephonyManager";
    public static final String sDefaultSpcCode = "000000";
    private Message mCurrentMessage;
    private LinkedList<HookRequest> mPrlRequestList;
    private Handler mUserHandler;
    private static boolean DEBUG = false;
    private static OemCdmaTelephonyManager mInstance = null;
    private TelephonyMgrState mState = TelephonyMgrState.STATE_IDLE;
    private LinkedList<HookRequest> mRequestList = new LinkedList<>();
    private Handler mMsgHandler = new Handler() { // from class: jp.co.sharp.telephony.OemCdmaTelephonyManager.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            Object obj;
            if (msg.what != Watchdog.MSG_TIMEOUT) {
                boolean next = true;
                if (msg != OemCdmaTelephonyManager.this.mCurrentMessage) {
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "unexpected message received: " + msg.what);
                    }
                    next = false;
                }
                if (next) {
                    OemCdmaTelephonyManager.this.mDog.sleep();
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "calling user handler for msg=" + msg.what);
                }
                if (next && OemCdmaTelephonyManager.this.mUserHandler != null) {
                    OemCdmaTelephonyManager.this.mUserHandler.obtainMessage(msg.what, msg.obj).sendToTarget();
                }
                if (next) {
                    nextRequest();
                }
            } else if (((Message) msg.obj) == OemCdmaTelephonyManager.this.mCurrentMessage) {
                if (OemCdmaTelephonyManager.DEBUG) {
                    StringBuilder append = new StringBuilder().append("message timed out: ");
                    if (OemCdmaTelephonyManager.this.mCurrentMessage != null) {
                        obj = Integer.valueOf(OemCdmaTelephonyManager.this.mCurrentMessage.what);
                    } else {
                        obj = "null";
                    }
                    Log.d(OemCdmaTelephonyManager.TAG, append.append(obj).toString());
                }
                nextRequest();
            } else if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "Ignore timeout message, do nothing.");
            }
        }

        private void nextRequest() {
            OemCdmaTelephonyManager.this.mState = TelephonyMgrState.STATE_IDLE;
            OemCdmaTelephonyManager.this.mCurrentMessage = null;
            if (OemCdmaTelephonyManager.this.mRequestList.size() != 0) {
                HookRequest req = (HookRequest) OemCdmaTelephonyManager.this.mRequestList.removeFirst();
                OemCdmaTelephonyManager.this.invokeOemRilRequestRaw(req.data, req.msg, req.msgH);
            }
        }
    };
    private Phone mPhone = PhoneFactory.getDefaultPhone();
    private Watchdog mDog = new Watchdog(this.mMsgHandler);

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class OEM_RIL_CDMA_ACTIVE_PROF {
        public static final int SIZE = 4;
        public int profile;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class OEM_RIL_CDMA_BC {
        public static final int SIZE = 4;
        public int status;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class OEM_RIL_CDMA_CP_Status {
        public static final int OEM_RIL_CDMA_CP_ACTIVE_SET_PN_NUMBER = 6;
        public static final int OEM_RIL_CDMA_CP_ACTIVE_SET_STRENGTH_NUMBER = 6;
        public static final int OEM_RIL_CDMA_CP_NEIGHBOR_SET_PN_NUMBER = 40;
        public static final int SIZE = 304;
        public int PRevInUse;
        public int activePilotCount;
        public int[] activeSetPn;
        public int[] activeSetStrength;
        public int band;
        public int bestActivePilot;
        public int bestActiveStrength;
        public int bestNeighborPilot;
        public int bestNeighborStrength;
        public int bid;
        public int bsLat;
        public int bsLon;
        public int callCounter;
        public int candPilotCount;
        public int channel;
        public int cpState;
        public int droppedCallCounter;
        public int fer;
        public byte is2000System;
        public int lastCallIndicator;
        public int lnaStatus;
        public int neighborPilotCount;
        public int[] neighborSetPn;
        public int nid;
        public int rssi;
        public int sCallCounter;
        public int serviceOption;
        public int sid;
        public int txPower;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class OEM_RIL_CDMA_EVDO_Status {
        public static final int OEM_RIL_CDMA_EVDO_SECTOR_NUMBER = 16;
        public static final int SIZE = 144;
        public int anAuthStatus;
        public int atState;
        public int drcCover;
        public int dropCount;
        public int errPkts;
        public int hdrChanNum;
        public int hdrRssi;
        public int hybridMode;
        public int ip;
        public int macIndex;
        public int measPkts;
        public int pilotEnergy;
        public int pilotPn;
        public int rxPwrRx0Dbm;
        public int rxPwrRx1Dbm;
        public int[] sectorIds;
        public int sessionState;
        public int sinr;
        public int txUati;
        public int uatiColorCode;
        public int userCount;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class OEM_RIL_CDMA_HookHeader {
        public static final int OEM_RIL_CDMA_SPC_LOCK_CODE_LENGTH = 6;
        public static final int SIZE = 18;
        public OEM_RIL_CDMA_Errno error;
        public int msgId;
        public int msgLength;
        public byte[] spcLockCode;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class OEM_RIL_CDMA_NAM_Info {
        private static final int OEM_RIL_CDMA_IMSI_11_12_ALIGNED_LENGTH = 4;
        public static final int OEM_RIL_CDMA_IMSI_11_12_LENGTH = 2;
        private static final int OEM_RIL_CDMA_IMSI_MCC_ALIGNED_LENGTH = 4;
        public static final int OEM_RIL_CDMA_IMSI_MCC_LENGTH = 3;
        private static final int OEM_RIL_CDMA_IMSI_MCC_T_ALIGNED_LENGTH = 4;
        public static final int OEM_RIL_CDMA_IMSI_MCC_T_LENGTH = 3;
        public static final int OEM_RIL_CDMA_IMSI_M_S1_0_LENGTH = 4;
        public static final int OEM_RIL_CDMA_IMSI_M_S2_LENGTH = 4;
        private static final int OEM_RIL_CDMA_IMSI_T_ALIGNED_LENGTH = 16;
        public static final int OEM_RIL_CDMA_IMSI_T_LENGTH = 15;
        private static final int OEM_RIL_CDMA_MDN_ALIGNED_LENGTH = 16;
        public static final int OEM_RIL_CDMA_MDN_LENGTH = 10;
        public static final int OEM_RIL_CDMA_MIN1_LENGTH = 7;
        public static final int OEM_RIL_CDMA_MIN2_LENGTH = 3;
        private static final int OEM_RIL_CDMA_MIN_ALIGNED_LENGTH = 16;
        public static final int OEM_RIL_CDMA_MIN_LENGTH = 10;
        public static final int OEM_RIL_CDMA_SPC_LOCK_CODE_LENGTH = 6;
        public static final int SIZE = 126;
        public int accessOverloadClass;
        public int h_nid;
        public int h_sid;
        public byte[] imsi11_12;
        public int imsiMAddrNum;
        public byte[] imsiMS1_0;
        public byte[] imsiMS2;
        public byte[] imsiMcc;
        public byte[] imsiMccT;
        public byte[] imsiT;
        public byte[] mdn;
        public byte[] min;
        public int mob_term_home;
        public int mob_term_nid;
        public int mob_term_sid;
        public byte[] newSpcCode;
        public int priCdmaA;
        public int priCdmaB;
        public int scm;
        public int secCdmaA;
        public int secCdmaB;
        public int vocoderType;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class OEM_RIL_CDMA_NAM_PrlVersion {
        public static final int SIZE = 4;
        public int prlVerison;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class OEM_RIL_CDMA_RESET_TO_FACTORY {
        public static final byte RESET_CLEAR = 2;
        public static final byte RESET_DEFAULT = -1;
        public static final byte RESET_RTN = 1;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class OEM_RIL_CDMA_SID_NID_NAM_Pair {
        public static final int SIZE = 8;
        public int nid;
        public int sid;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class OEM_RIL_CDMA_SubsidyPassword {
        public static final int OEM_RIL_CDMA_SPC_LOCK_CODE_LENGTH = 6;
        public static final int SIZE = 6;
        public byte[] password;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum RdeRequestId {
        RDEREQ_SET_EHRPD_OPTION,
        RDEREQ_GET_EHRPD_OPTION,
        RDEREQ_SET_EHRPD_FORCED,
        RDEREQ_GET_EHRPD_FORCED,
        RDEREQ_SET_SO73_OPTION,
        RDEREQ_SET_SO73COP0_OPTION,
        RDEREQ_SET_1xADV_OPTION,
        RDEREQ_GET_SO73_OPTION,
        RDEREQ_GET_SO73COP0_OPTION,
        RDEREQ_GET_1xADV_OPTION,
        RDEREQ_GET_MIP_GEN_USER_PROF,
        RDEREQ_SET_MIP_GEN_USER_PROF,
        RDEREQ_GET_MIP_SS_USER_PROF,
        RDEREQ_SET_MIP_SS_USER_PROF,
        RDEREQ_GET_CDMA_SO68_ENABLED,
        RDEREQ_SET_CDMA_SO68_ENABLED,
        RDEREQ_GET_DDTM_DEFAULT_PREFERENCE_SETTINGS,
        RDEREQ_SET_DDTM_DEFAULT_PREFERENCE_SETTINGS,
        RDEREQ_GET_OTKSL,
        RDEREQ_GET_ESN,
        RDEREQ_GET_PREF_VOICE_SO,
        RDEREQ_SET_PREF_VOICE_SO,
        RDEREQ_GET_SCM_I,
        RDEREQ_GET_SLOT_CYCLE_INDEX,
        RDEREQ_SET_ROAMING_LIST,
        RDEREQ_GET_NAM_MDN,
        RDEREQ_SET_NAM_MDN,
        RDEREQ_GET_NAM_MIN1,
        RDEREQ_SET_NAM_MIN1,
        RDEREQ_GET_NAM_MIN2,
        RDEREQ_SET_NAM_MIN2,
        RDEREQ_GET_NAM_SCM,
        RDEREQ_GET_NAM_IMSI_11_12,
        RDEREQ_SET_NAM_IMSI_11_12,
        RDEREQ_GET_NAM_IMSI_MCC,
        RDEREQ_SET_NAM_IMSI_MCC,
        RDEREQ_GET_NAM_ACCOLC,
        RDEREQ_SET_NAM_ACCOLC,
        RDEREQ_GET_HOME_SID_REG,
        RDEREQ_GET_FOR_SID_REG,
        RDEREQ_GET_FOR_NID_REG,
        RDEREQ_GET_LOCK_CODE,
        RDEREQ_SET_LOCK_CODE,
        RDEREQ_GET_SLOT_CYCLE_MODE,
        RDEREQ_GET_MIP_ERROR_CODE_I,
        RDEREQ_GET_HDR_AN_AUTH_PASSWD_LONG,
        RDEREQ_SET_HOME_SID_REG,
        RDEREQ_SET_FOR_SID_REG,
        RDEREQ_SET_FOR_NID_REG,
        RDEREQ_GET_IMSI_M_ADDR_NUM,
        RDEREQ_SET_IMSI_M_ADDR_NUM,
        RDEREQ_GET_LTE_ENABLED,
        RDEREQ_SET_LTE_ENABLED,
        RDEREQ_GET_LTE_FORCED,
        RDEREQ_GET_BANDCLASS_SUPPORT,
        RDEREQ_SET_BANDCLASS_SUPPORT,
        RDEREQ_SET_LTE_FORCED,
        RDEREQ_GET_SIM_UNLOCKED,
        RDEREQ_SET_SIM_UNLOCKED,
        RDEREQ_GET_SIM_UICCID,
        RDEREQ_SET_SIM_UICCID
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum TelephonyMgrState {
        STATE_IDLE,
        STATE_WAITING_FOR_RESPONSE
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class HookRequest {
        public byte[] data;
        public Message msg;
        public Handler msgH;

        public HookRequest(byte[] data, Message msg, Handler msgH) {
            this.data = data;
            this.msg = msg;
            this.msgH = msgH;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class Watchdog extends Thread {
        public static int MSG_TIMEOUT = 256;
        private static int TIMEOUT = 5000;
        private Handler mHandler;
        private boolean mExit = false;
        private Message watchingMsg = null;

        public Watchdog(Handler h) {
            this.mHandler = h;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            while (!this.mExit) {
                synchronized (this) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
                try {
                    Thread.sleep(TIMEOUT);
                    Message m = new Message();
                    m.what = MSG_TIMEOUT;
                    m.obj = this.watchingMsg;
                    this.mHandler.sendMessage(m);
                } catch (InterruptedException e2) {
                }
            }
        }

        public void watch(Message msg) {
            synchronized (this) {
                this.watchingMsg = msg;
                notify();
            }
        }

        public void sleep() {
            synchronized (this) {
                interrupt();
            }
        }

        public void exit() {
            synchronized (this) {
                this.mExit = true;
                interrupt();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public Handler getMsgHandler() {
        return this.mMsgHandler;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public synchronized void invokeOemRilRequestRaw(byte[] data, Message msg, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "invokeOemRilRequestRaw(): msg.what = " + msg.what);
        }
        switch (this.mState) {
            case STATE_IDLE:
                this.mState = TelephonyMgrState.STATE_WAITING_FOR_RESPONSE;
                this.mCurrentMessage = msg;
                this.mUserHandler = msgH;
                if (DEBUG) {
                    Log.d(TAG, "sending request to RIL");
                }
                this.mPhone.invokeOemRilRequestRaw(data, msg);
                this.mDog.watch(msg);
                break;
            case STATE_WAITING_FOR_RESPONSE:
                if (DEBUG) {
                    Log.d(TAG, "OemCdmaTelephonyManager is busy. pushing request to the queue.");
                }
                this.mRequestList.add(new HookRequest(data, msg, msgH));
                break;
            default:
                if (DEBUG) {
                    Log.e(TAG, "wrong state in invokeOemRilRequestRaw(): " + this.mState);
                    break;
                }
                break;
        }
    }

    private OemCdmaTelephonyManager() {
        this.mDog.start();
    }

    public static synchronized OemCdmaTelephonyManager getInstance() {
        OemCdmaTelephonyManager oemCdmaTelephonyManager;
        synchronized (OemCdmaTelephonyManager.class) {
            if (mInstance == null) {
                mInstance = new OemCdmaTelephonyManager();
            }
            oemCdmaTelephonyManager = mInstance;
        }
        return oemCdmaTelephonyManager;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum OEM_RIL_CDMA_Errno {
        OEM_RIL_CDMA_SUCCESS(0),
        OEM_RIL_CDMA_RADIO_NOT_AVAILABLE(1),
        OEM_RIL_CDMA_NAM_READ_WRITE_FAILURE(2),
        OEM_RIL_CDMA_NAM_PASSWORD_INCORRECT(3),
        OEM_RIL_CDMA_NAM_ACCESS_COUNTER_EXCEEDED(4),
        OEM_RIL_CDMA_GENERIC_FAILURE(5);
        
        private final int id;

        OEM_RIL_CDMA_Errno(int id) {
            this.id = id;
        }

        public int toInt() {
            return this.id;
        }

        public static OEM_RIL_CDMA_Errno fromInt(int id) {
            OEM_RIL_CDMA_Errno[] arr$ = values();
            for (OEM_RIL_CDMA_Errno en : arr$) {
                if (en.id == id) {
                    return en;
                }
            }
            return OEM_RIL_CDMA_GENERIC_FAILURE;
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class OEM_RIL_CDMA_Result {
        public OEM_RIL_CDMA_Errno errno;
        public Object obj;

        OEM_RIL_CDMA_Result() {
            this.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
            this.obj = null;
        }

        OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno er) {
            this.errno = er;
            this.obj = null;
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum OEM_RIL_CDMA_ServiceOption {
        OEM_RIL_CDMA_SO_DEFAULT(0),
        OEM_RIL_CDMA_SO_MARKOV_8K(1),
        OEM_RIL_CDMA_SO_MARKOV_13K(2),
        OEM_RIL_CDMA_SO_LOOPBACK_8K(3),
        OEM_RIL_CDMA_SO_LOOPBACK_13K(4),
        OEM_RIL_CDMA_SO_EVRC(5),
        OEM_RIL_CDMA_SO_EVRC_B(6),
        INVALID_DATA(65535);
        
        public static final int SIZE = 4;
        private final int id;

        OEM_RIL_CDMA_ServiceOption(int id) {
            this.id = id;
        }

        public int toInt() {
            return this.id;
        }

        public static OEM_RIL_CDMA_ServiceOption fromInt(int id) {
            OEM_RIL_CDMA_ServiceOption[] arr$ = values();
            for (OEM_RIL_CDMA_ServiceOption en : arr$) {
                if (en.id == id) {
                    return en;
                }
            }
            return INVALID_DATA;
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class OEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs {
        public static final int OEM_RIL_CDMA_NUMBER_NID_SID_PAIRS = 20;
        public OEM_RIL_CDMA_SID_NID_NAM_Pair[] sidNid;

        public int SIZE() {
            return (this.sidNid.length * 8) + 4;
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class OEM_RIL_RDE_Data {
        public static final int RDE_EFS_1XADVANCED_CAPABILITY_STATUS_I = 10015;
        public static final int RDE_EFS_SO73_COP0_SUPPORTED_I = 10016;
        public static final int RDE_MIP_ERROR_CODE_I = 10019;
        public static final int RDE_NV_ACCOLC_I = 2;
        public static final int RDE_NV_CDMA_SO68_ENABLED_I = 48;
        public static final int RDE_NV_CDMA_SO73_ENABLED_I = 45;
        public static final int RDE_NV_DIAL_I = 6;
        public static final int RDE_NV_DIR_NUMBER_I = 7;
        public static final int RDE_NV_DS_MIP_GEN_USER_PROF_I = 9;
        public static final int RDE_NV_DS_MIP_SS_USER_PROF_I = 46;
        public static final int RDE_NV_D_KEY_I = 4;
        public static final int RDE_NV_EHRPD_ENABLED_I = 43;
        public static final int RDE_NV_ESN_I = 10;
        public static final int RDE_NV_IMSI_11_12_I = 16;
        public static final int RDE_NV_IMSI_ADDR_NUM_I = 49;
        public static final int RDE_NV_IMSI_MCC_I = 17;
        public static final int RDE_NV_LOCK_CODE_I = 23;
        public static final int RDE_NV_LTE_BC_CONFIG_I = 53;
        public static final int RDE_NV_LTE_CAPABILITY_I = 10017;
        public static final int RDE_NV_MIN1_I = 25;
        public static final int RDE_NV_MIN2_I = 26;
        public static final int RDE_NV_MOB_TERM_FOR_NID_I = 52;
        public static final int RDE_NV_MOB_TERM_FOR_SID_I = 51;
        public static final int RDE_NV_MOB_TERM_HOME_I = 50;
        public static final int RDE_NV_OTKSL_I = 27;
        public static final int RDE_NV_PREF_VOICE_SO_I = 30;
        public static final int RDE_NV_ROAMING_LIST_683_I = 108;
        public static final int RDE_NV_SCM_I = 36;
        public static final int RDE_NV_SIM_UICCID_I = 56;
        public static final int RDE_NV_SIM_UNLOCKED_I = 55;
        public static final int RDE_NV_SLOTTED_MODE_I = 10020;
        public static final int RDE_NV_SLOT_CYCLE_INDEX_I = 47;
        public static final int RDE_SHARP_NV_SPRINT_EHRPD_FORCE_ENABLE_I = 8025;
        public static final int RDE_SHARP_NV_SPRINT_LTE_FORCE_ENABLE_I = 8024;
        public int elementID = 0;
        public int recordNum = 0;
        public int offset = 0;
        public int length = 0;
        public Serializable dataObj = null;

        /* JADX INFO: Access modifiers changed from: private */
        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public interface Serializable {
            void serialize(ByteBuffer byteBuffer);

            int size();
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public enum DDTMDefaultPreferenceSettings {
            DDTMDefaultPreferenceOFF(0),
            DDTMDefaultPreferenceON(1),
            DDTMDefaultPreferenceAUTO(2);
            
            private final int id;

            DDTMDefaultPreferenceSettings(int id) {
                this.id = id;
            }

            public int toInt() {
                return this.id;
            }

            public static DDTMDefaultPreferenceSettings fromInt(int id) {
                DDTMDefaultPreferenceSettings[] arr$ = values();
                for (DDTMDefaultPreferenceSettings en : arr$) {
                    if (en.id == id) {
                        return en;
                    }
                }
                return DDTMDefaultPreferenceOFF;
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public enum EmergencyAddress {
            EMERGENCY_NUMBER_1(99),
            EMERGENCY_NUMBER_2(100),
            EMERGENCY_NUMBER_3(101),
            INVALID_DATA(65535);
            
            private int mId;

            EmergencyAddress(int id) {
                this.mId = id;
            }

            public static EmergencyAddress fromInt(int id) {
                EmergencyAddress[] arr$ = values();
                for (EmergencyAddress ad : arr$) {
                    if (ad.mId == id) {
                        return ad;
                    }
                }
                return INVALID_DATA;
            }

            public int id() {
                return this.mId;
            }
        }

        public int SIZE() {
            return (this.dataObj == null ? 1 : this.dataObj.size()) + 16;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("elementID=" + this.elementID + "\n");
            sb.append("recordNum=" + this.recordNum + "\n");
            sb.append("offset=" + this.offset + "\n");
            sb.append("object=\n" + this.dataObj);
            return sb.toString();
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public static class rde_obj_type implements Serializable {
            public byte[] data;

            public rde_obj_type() {
                this.data = null;
            }

            public rde_obj_type(byte b) {
                ByteBuffer buf = ByteBuffer.allocate(1);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                buf.put(b);
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "bype to bypeArray = " + ((int) b));
                }
                this.data = buf.array();
            }

            public rde_obj_type(boolean b) {
                ByteBuffer buf = ByteBuffer.allocate(4);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                if (b) {
                    buf.putInt(new Integer(1).intValue());
                } else {
                    buf.putInt(new Integer(0).intValue());
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "boolean to bypeArray = " + b);
                }
                this.data = buf.array();
            }

            public rde_obj_type(Integer i) {
                ByteBuffer buf = ByteBuffer.allocate(4);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                buf.putInt(i.intValue());
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "Integer to bypeArray = " + i);
                }
                this.data = buf.array();
            }

            public rde_obj_type(short i) {
                ByteBuffer buf = ByteBuffer.allocate(2);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                buf.putInt(i);
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "short to bypeArray = " + ((int) i));
                }
                this.data = buf.array();
            }

            public rde_obj_type(String s) {
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "String to bypeArray = " + s);
                }
                byte[] tempData = s.getBytes();
                this.data = new byte[127];
                System.arraycopy(tempData, 0, this.data, 0, s.length());
                Arrays.fill(this.data, s.length(), 127, (byte) 0);
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public void serialize(ByteBuffer buf) {
                buf.put(this.data);
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public int size() {
                if (this.data == null) {
                    return 0;
                }
                return this.data.length;
            }

            public static int rdeToInteger(rde_obj_type rde_obj) {
                int result = 0;
                for (int i = rde_obj.data.length - 1; i >= 0; i--) {
                    result = (result * 256) + (rde_obj.data[i] & OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT);
                }
                return result;
            }

            public static boolean rdeToBool(rde_obj_type rde_obj) {
                if ((rde_obj.data[0] & OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) != 0) {
                    return true;
                }
                return false;
            }

            public static String rdeToString(rde_obj_type rde_obj) {
                return new String(rde_obj.data);
            }

            public String toString() {
                StringBuffer sb = new StringBuffer();
                sb.append("data=0x" + OemCdmaTelephonyManager.byteArrayToHexString(this.data));
                return sb.toString();
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public static class rde_dial_type implements Serializable {
            public static final int NV_MAX_DIAL_DIGITS = 32;
            public static final int NV_MAX_LTRS = 12;
            private byte[] letters;
            public byte address = 0;
            public byte status = 0;
            private byte num_digits = 0;
            private byte[] digits = new byte[32];

            public rde_dial_type() {
                for (int i = 0; i < this.digits.length; i++) {
                    this.digits[i] = 0;
                }
                this.letters = new byte[12];
                for (int i2 = 0; i2 < this.letters.length; i2++) {
                    this.letters[i2] = 0;
                }
            }

            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append("address=" + ((int) this.address) + "\n");
                sb.append("status=" + ((int) this.status) + "\n");
                sb.append("num_digits=" + ((int) this.num_digits) + "\n");
                sb.append("digits=" + getNumber());
                return sb.toString();
            }

            public boolean setNumber(String number) {
                this.num_digits = (byte) 0;
                for (int i = 0; i < this.digits.length; i++) {
                    if (i < number.length()) {
                        this.digits[i] = (byte) number.charAt(i);
                    } else {
                        this.digits[i] = 0;
                    }
                }
                this.num_digits = (byte) (number.length() < this.digits.length ? number.length() : this.digits.length);
                return true;
            }

            public String getNumber() {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < this.num_digits; i++) {
                    sb.append((char) this.digits[i]);
                }
                return sb.toString();
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public void serialize(ByteBuffer buf) {
                buf.put(this.address);
                buf.put(this.status);
                buf.put(this.num_digits);
                buf.put(this.digits);
                buf.put(this.letters);
            }

            public static rde_dial_type deserialize(ByteBuffer buf) {
                rde_dial_type dt = new rde_dial_type();
                dt.address = buf.get();
                dt.status = buf.get();
                dt.num_digits = buf.get();
                if (dt.num_digits > 32) {
                    dt.num_digits = (byte) 32;
                }
                for (int i = 0; i < dt.digits.length; i++) {
                    dt.digits[i] = buf.get();
                }
                for (int i2 = 0; i2 < dt.letters.length; i2++) {
                    dt.letters[i2] = buf.get();
                }
                return dt;
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public int size() {
                return (this.digits.length + 3 + this.letters.length) * 1;
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public static class rde_gen_profile_type implements Serializable {
            public static final int NUM_BYTES_UINT32 = 4;
            public static final int NV_MAX_NAI_LENGTH = 72;
            public byte[] home_addr;
            public byte[] mn_aaa_spi;
            public byte[] mn_ha_spi;
            public byte[] primary_ha_addr;
            public byte[] secondary_ha_addr;
            public byte index = 0;
            public byte nai_length = 0;
            public byte mn_ha_spi_set = 0;
            public byte mn_aaa_spi_set = 0;
            public byte rev_tun_pref = 0;
            public byte[] nai = new byte[72];

            public rde_gen_profile_type() {
                for (int i = 0; i < this.nai.length; i++) {
                    this.nai[i] = 0;
                }
                this.mn_ha_spi = new byte[4];
                for (int i2 = 0; i2 < this.mn_ha_spi.length; i2++) {
                    this.mn_ha_spi[i2] = 0;
                }
                this.mn_aaa_spi = new byte[4];
                for (int i3 = 0; i3 < this.mn_aaa_spi.length; i3++) {
                    this.mn_aaa_spi[i3] = 0;
                }
                this.home_addr = new byte[4];
                for (int i4 = 0; i4 < this.home_addr.length; i4++) {
                    this.home_addr[i4] = 0;
                }
                this.primary_ha_addr = new byte[4];
                for (int i5 = 0; i5 < this.primary_ha_addr.length; i5++) {
                    this.primary_ha_addr[i5] = 0;
                }
                this.secondary_ha_addr = new byte[4];
                for (int i6 = 0; i6 < this.secondary_ha_addr.length; i6++) {
                    this.secondary_ha_addr[i6] = 0;
                }
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public void serialize(ByteBuffer buf) {
                buf.put(this.index);
                buf.put(this.nai_length);
                for (int i = 0; i < this.nai.length; i++) {
                    buf.put(this.nai[i]);
                }
                for (int i2 = 0; i2 < 72 - this.nai.length; i2++) {
                    buf.put((byte) 0);
                }
                buf.put(this.mn_ha_spi_set);
                for (int i3 = 0; i3 < this.mn_ha_spi.length; i3++) {
                    buf.put(this.mn_ha_spi[i3]);
                }
                for (int i4 = 0; i4 < 4 - this.mn_ha_spi.length; i4++) {
                    buf.put((byte) 0);
                }
                buf.put(this.mn_aaa_spi_set);
                for (int i5 = 0; i5 < this.mn_aaa_spi.length; i5++) {
                    buf.put(this.mn_aaa_spi[i5]);
                }
                for (int i6 = 0; i6 < 4 - this.mn_aaa_spi.length; i6++) {
                    buf.put((byte) 0);
                }
                buf.put(this.rev_tun_pref);
                for (int i7 = 0; i7 < this.home_addr.length; i7++) {
                    buf.put(this.home_addr[i7]);
                }
                for (int i8 = 0; i8 < this.primary_ha_addr.length; i8++) {
                    buf.put(this.primary_ha_addr[i8]);
                }
                for (int i9 = 0; i9 < this.secondary_ha_addr.length; i9++) {
                    buf.put(this.secondary_ha_addr[i9]);
                }
            }

            public static rde_gen_profile_type deserialize(ByteBuffer buf) {
                rde_gen_profile_type genProfType = new rde_gen_profile_type();
                genProfType.index = buf.get();
                genProfType.nai_length = buf.get();
                for (int i = 0; i < genProfType.nai.length; i++) {
                    genProfType.nai[i] = buf.get();
                }
                genProfType.mn_ha_spi_set = buf.get();
                for (int i2 = 0; i2 < genProfType.mn_ha_spi.length; i2++) {
                    genProfType.mn_ha_spi[i2] = buf.get();
                }
                genProfType.mn_aaa_spi_set = buf.get();
                for (int i3 = 0; i3 < genProfType.mn_aaa_spi.length; i3++) {
                    genProfType.mn_aaa_spi[i3] = buf.get();
                }
                genProfType.rev_tun_pref = buf.get();
                for (int i4 = 0; i4 < genProfType.home_addr.length; i4++) {
                    genProfType.home_addr[i4] = buf.get();
                }
                for (int i5 = 0; i5 < genProfType.primary_ha_addr.length; i5++) {
                    genProfType.primary_ha_addr[i5] = buf.get();
                }
                for (int i6 = 0; i6 < genProfType.secondary_ha_addr.length; i6++) {
                    genProfType.secondary_ha_addr[i6] = buf.get();
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.v(OemCdmaTelephonyManager.TAG, "rde_gen_profile_type deserialize() index=" + ((int) genProfType.index) + " nai=" + genProfType.nai + " mn_ha_spi_set=" + ((int) genProfType.mn_ha_spi_set) + " nam=" + genProfType.mn_ha_spi + "mn_aaa_spi_set" + ((int) genProfType.mn_aaa_spi_set) + "rev_tun_pref" + ((int) genProfType.rev_tun_pref));
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.v(OemCdmaTelephonyManager.TAG, "rde_gen_profile_type deserialize() home_addr=" + genProfType.home_addr + " primary_ha_addr=" + genProfType.primary_ha_addr + " secondary_ha_addr=" + genProfType.secondary_ha_addr);
                }
                return genProfType;
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public int size() {
                return 97;
            }

            public String toString() {
                StringBuffer sb = new StringBuffer();
                sb.append("index=0x" + OemCdmaTelephonyManager.byteToHexString(this.index));
                sb.append(", nai_length=0x" + OemCdmaTelephonyManager.byteToHexString(this.nai_length));
                sb.append(", nai=" + OemCdmaTelephonyManager.byteArrayToHexString(this.nai));
                sb.append(", mn_ha_spi_set=" + OemCdmaTelephonyManager.byteToHexString(this.mn_ha_spi_set));
                sb.append(", mn_ha_spi=" + OemCdmaTelephonyManager.byteArrayToHexString(this.mn_ha_spi));
                sb.append(", mn_aaa_spi_set=" + OemCdmaTelephonyManager.byteToHexString(this.mn_aaa_spi_set));
                sb.append(", mn_aaa_spi=" + OemCdmaTelephonyManager.byteArrayToHexString(this.mn_aaa_spi));
                sb.append(", rev_tun_pref=" + OemCdmaTelephonyManager.byteToHexString(this.rev_tun_pref));
                sb.append(", home_addr=" + OemCdmaTelephonyManager.byteArrayToHexString(this.home_addr));
                sb.append(", primary_ha_addr=" + OemCdmaTelephonyManager.byteArrayToHexString(this.primary_ha_addr));
                sb.append(", secondary_ha_addr=" + OemCdmaTelephonyManager.byteArrayToHexString(this.secondary_ha_addr));
                return sb.toString();
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public static class rde_ss_profile_type implements Serializable {
            public static final int NV_MAX_MN_AAA_SHARED_SECRET_LEN = 16;
            public static final int NV_MAX_MN_HA_SHARED_SECRET_LEN = 16;
            public byte[] mn_aaa_shared_secret;
            public byte index = 0;
            public byte mn_ha_shared_secret_length = 0;
            public byte mn_aaa_shared_secret_length = 0;
            public byte[] mn_ha_shared_secret = new byte[16];

            public rde_ss_profile_type() {
                for (int i = 0; i < this.mn_ha_shared_secret.length; i++) {
                    this.mn_ha_shared_secret[i] = 0;
                }
                this.mn_aaa_shared_secret = new byte[16];
                for (int i2 = 0; i2 < this.mn_aaa_shared_secret.length; i2++) {
                    this.mn_aaa_shared_secret[i2] = 0;
                }
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public void serialize(ByteBuffer buf) {
                buf.put(this.index);
                buf.put(this.mn_ha_shared_secret_length);
                for (int i = 0; i < this.mn_ha_shared_secret.length; i++) {
                    buf.put(this.mn_ha_shared_secret[i]);
                }
                for (int i2 = 0; i2 < 16 - this.mn_ha_shared_secret.length; i2++) {
                    buf.put((byte) 0);
                }
                buf.put(this.mn_aaa_shared_secret_length);
                for (int i3 = 0; i3 < this.mn_aaa_shared_secret.length; i3++) {
                    buf.put(this.mn_aaa_shared_secret[i3]);
                }
                for (int i4 = 0; i4 < 16 - this.mn_aaa_shared_secret.length; i4++) {
                    buf.put((byte) 0);
                }
            }

            public static rde_ss_profile_type deserialize(ByteBuffer buf) {
                rde_ss_profile_type ssProfType = new rde_ss_profile_type();
                ssProfType.index = buf.get();
                ssProfType.mn_ha_shared_secret_length = buf.get();
                for (int i = 0; i < ssProfType.mn_ha_shared_secret.length; i++) {
                    ssProfType.mn_ha_shared_secret[i] = buf.get();
                }
                ssProfType.mn_aaa_shared_secret_length = buf.get();
                for (int i2 = 0; i2 < ssProfType.mn_aaa_shared_secret.length; i2++) {
                    ssProfType.mn_aaa_shared_secret[i2] = buf.get();
                }
                return ssProfType;
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public int size() {
                return 35;
            }

            public String toString() {
                StringBuffer sb = new StringBuffer();
                sb.append("index=0x" + OemCdmaTelephonyManager.byteToHexString(this.index));
                sb.append(", mn_ha_shared_secret_length=0x" + OemCdmaTelephonyManager.byteToHexString(this.mn_ha_shared_secret_length));
                sb.append(", mn_ha_shared_secret=" + OemCdmaTelephonyManager.byteArrayToHexString(this.mn_ha_shared_secret));
                sb.append(", mn_aaa_shared_secret_length=0x" + OemCdmaTelephonyManager.byteToHexString(this.mn_aaa_shared_secret_length));
                sb.append(", mn_aaa_shared_secret=" + OemCdmaTelephonyManager.byteArrayToHexString(this.mn_aaa_shared_secret));
                return sb.toString();
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public static class rde_pref_voice_so_type implements Serializable {
            public byte nam = 0;
            public byte evrc_capability_enabled = 0;
            public short home_page_voice_so = 0;
            public short home_orig_voice_so = 0;
            public short roam_orig_voice_so = 0;

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public void serialize(ByteBuffer buf) {
                buf.put(this.nam);
                buf.put(this.evrc_capability_enabled);
                buf.putShort(this.home_page_voice_so);
                buf.putShort(this.home_orig_voice_so);
                buf.putShort(this.roam_orig_voice_so);
            }

            public static rde_pref_voice_so_type deserialize(ByteBuffer buf) {
                rde_pref_voice_so_type pt = new rde_pref_voice_so_type();
                pt.nam = buf.get();
                pt.evrc_capability_enabled = buf.get();
                pt.home_page_voice_so = buf.getShort();
                pt.home_orig_voice_so = buf.getShort();
                pt.roam_orig_voice_so = buf.getShort();
                return pt;
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public int size() {
                return 17;
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public static class rde_roaming_list_type implements Serializable {
            public static final int NV_SIZE_OF_RAM_ROAMING_LIST = 16390;
            public byte[] roaming_list;
            public int size = 0;

            public void setRoamingList(byte[] prl_list) {
                this.roaming_list = prl_list;
                this.size = this.roaming_list.length;
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public void serialize(ByteBuffer buf) {
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.v(OemCdmaTelephonyManager.TAG, "rde_roaming_list_type serialize()");
                }
                buf.putInt(this.size);
                buf.put(this.roaming_list);
            }

            public static rde_roaming_list_type deserialize(ByteBuffer buf) {
                rde_roaming_list_type pt = new rde_roaming_list_type();
                pt.size = buf.getInt();
                pt.roaming_list = new byte[pt.size];
                for (int i = 0; i < pt.size; i++) {
                    pt.roaming_list[i] = buf.get();
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.v(OemCdmaTelephonyManager.TAG, "rde_roaming_list_type deserialize()");
                }
                return pt;
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public int size() {
                return (this.roaming_list.length * 1) + 4;
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public static class rde_nam_mdn_type implements Serializable {
            public static final int OEM_RIL_CDMA_MDN_LENGTH = 10;
            public byte nam = 0;
            public byte[] mdn = new byte[10];

            public rde_nam_mdn_type() {
                for (int i = 0; i < 10; i++) {
                    this.mdn[i] = 0;
                }
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public void serialize(ByteBuffer buf) {
                buf.put(this.nam);
                for (int i = 0; i < 10; i++) {
                    buf.put(this.mdn[i]);
                }
            }

            public static rde_nam_mdn_type deserialize(ByteBuffer buf) {
                rde_nam_mdn_type mdnType = new rde_nam_mdn_type();
                mdnType.nam = buf.get();
                for (int i = 0; i < 10; i++) {
                    mdnType.mdn[i] = buf.get();
                }
                return mdnType;
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public int size() {
                return 11;
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public static class rde_nam_min1_type implements Serializable {
            public static final int OEM_RIL_CDMA_MAX_MINS = 2;
            public byte nam = 0;
            public int[] min1 = new int[2];

            public rde_nam_min1_type() {
                for (int i = 0; i < 2; i++) {
                    this.min1[i] = 0;
                }
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public void serialize(ByteBuffer buf) {
                buf.put(this.nam);
                for (int i = 0; i < 2; i++) {
                    buf.putInt(this.min1[i]);
                }
            }

            public static rde_nam_min1_type deserialize(ByteBuffer buf) {
                rde_nam_min1_type min1Type = new rde_nam_min1_type();
                min1Type.nam = buf.get();
                for (int i = 0; i < 2; i++) {
                    min1Type.min1[i] = buf.getInt();
                }
                return min1Type;
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public int size() {
                return 9;
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public static class rde_nam_min2_type implements Serializable {
            public static final int OEM_RIL_CDMA_MAX_MINS = 2;
            public byte nam = 0;
            public short[] min2 = new short[2];

            public rde_nam_min2_type() {
                for (int i = 0; i < 2; i++) {
                    this.min2[i] = 0;
                }
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public void serialize(ByteBuffer buf) {
                buf.put(this.nam);
                for (int i = 0; i < 2; i++) {
                    buf.putShort(this.min2[i]);
                }
            }

            public static rde_nam_min2_type deserialize(ByteBuffer buf) {
                rde_nam_min2_type min2Type = new rde_nam_min2_type();
                min2Type.nam = buf.get();
                for (int i = 0; i < 2; i++) {
                    min2Type.min2[i] = buf.getShort();
                }
                return min2Type;
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public int size() {
                return 5;
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public static class rde_nam_imsi_11_12_type implements Serializable {
            public byte nam = 0;
            public byte imsi_11_12 = 0;

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public void serialize(ByteBuffer buf) {
                buf.put(this.nam);
                buf.put(this.imsi_11_12);
            }

            public static rde_nam_imsi_11_12_type deserialize(ByteBuffer buf) {
                rde_nam_imsi_11_12_type imsi1112Type = new rde_nam_imsi_11_12_type();
                imsi1112Type.nam = buf.get();
                imsi1112Type.imsi_11_12 = buf.get();
                return imsi1112Type;
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public int size() {
                return 2;
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public static class rde_nam_imsi_mcc_type implements Serializable {
            public byte nam = 0;
            public short imsi_mcc = 0;

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public void serialize(ByteBuffer buf) {
                buf.put(this.nam);
                buf.putShort(this.imsi_mcc);
            }

            public static rde_nam_imsi_mcc_type deserialize(ByteBuffer buf) {
                rde_nam_imsi_mcc_type imsiMccType = new rde_nam_imsi_mcc_type();
                imsiMccType.nam = buf.get();
                imsiMccType.imsi_mcc = buf.getShort();
                return imsiMccType;
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public int size() {
                return 3;
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public static class rde_nam_accolc_type implements Serializable {
            public static final int OEM_RIL_CDMA_MAX_MINS = 2;
            public byte nam = 0;
            public byte[] ACCOLCpClass = new byte[2];

            public rde_nam_accolc_type() {
                for (int i = 0; i < 2; i++) {
                    this.ACCOLCpClass[i] = 0;
                }
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public void serialize(ByteBuffer buf) {
                buf.put(this.nam);
                for (int i = 0; i < 2; i++) {
                    buf.put(this.ACCOLCpClass[i]);
                }
            }

            public static rde_nam_accolc_type deserialize(ByteBuffer buf) {
                rde_nam_accolc_type accolcType = new rde_nam_accolc_type();
                accolcType.nam = buf.get();
                for (int i = 0; i < 2; i++) {
                    accolcType.ACCOLCpClass[i] = buf.get();
                }
                return accolcType;
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public int size() {
                return 3;
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public static class rde_mob_term_type implements Serializable {
            public static final int OEM_RIL_CDMA_MAX_MINS = 2;
            public byte nam = 0;
            public byte[] enabled = new byte[2];

            public rde_mob_term_type() {
                for (int i = 0; i < 2; i++) {
                    this.enabled[i] = 0;
                }
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public void serialize(ByteBuffer buf) {
                buf.put(this.nam);
                for (int i = 0; i < 2; i++) {
                    buf.put(this.enabled[i]);
                }
            }

            public static rde_mob_term_type deserialize(ByteBuffer buf) {
                rde_mob_term_type mobTermType = new rde_mob_term_type();
                mobTermType.nam = buf.get();
                for (int i = 0; i < 2; i++) {
                    mobTermType.enabled[i] = buf.get();
                }
                return mobTermType;
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public int size() {
                return 3;
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public static class rde_imsi_addr_num_type implements Serializable {
            public byte nam = 0;
            public byte num = -1;

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public void serialize(ByteBuffer buf) {
                buf.put(this.nam);
                if (this.num == 0) {
                    this.num = (byte) -1;
                }
                buf.put(this.num);
            }

            public static rde_imsi_addr_num_type deserialize(ByteBuffer buf) {
                rde_imsi_addr_num_type imsiAddrNumType = new rde_imsi_addr_num_type();
                imsiAddrNumType.nam = buf.get();
                imsiAddrNumType.num = buf.get();
                if (imsiAddrNumType.num == -1) {
                    imsiAddrNumType.num = (byte) 0;
                }
                return imsiAddrNumType;
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public int size() {
                return 2;
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public static class rde_lte_bc_config_type implements Serializable {
            public static final int NUM_BYTES_UINT64 = 8;
            public byte[] lte_bc_config;
            public byte[] lte_bc_config_ext;

            public rde_lte_bc_config_type() {
                this(0L, 0L);
            }

            public rde_lte_bc_config_type(long config, long config_ext) {
                this.lte_bc_config = longToLteBcConfig(config);
                this.lte_bc_config_ext = longToLteBcConfig(config_ext);
            }

            public byte[] longToLteBcConfig(long in) {
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.v(OemCdmaTelephonyManager.TAG, "enter longToLteBcConfig(), in =" + in);
                    Log.v(OemCdmaTelephonyManager.TAG, " longToLteBcConfig in to BinaryString =" + Long.toBinaryString(in));
                }
                byte[] out = {(byte) (((int) (in >>> 0)) & 255), (byte) (((int) (in >>> 8)) & 255), (byte) (((int) (in >>> 16)) & 255), (byte) (((int) (in >>> 24)) & 255), (byte) (((int) (in >>> 32)) & 255), (byte) (((int) (in >>> 40)) & 255), (byte) (((int) (in >>> 48)) & 255), (byte) (((int) (in >>> 56)) & 255)};
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.v(OemCdmaTelephonyManager.TAG, "longToLteBcConfig() print  out start");
                    int len$ = out.length;
                    for (int i$ = 0; i$ < len$; i$++) {
                        Log.v(OemCdmaTelephonyManager.TAG, "longToLteBcConfig() print  out= " + byte2bits(out[i$]));
                    }
                    Log.v(OemCdmaTelephonyManager.TAG, "longToLteBcConfig() print  out end");
                }
                return out;
            }

            public static long lteBcConfigToLong(byte[] in) {
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "enter lteBcConfigToLong()");
                    Log.v(OemCdmaTelephonyManager.TAG, "lteBcConfigToLong() print  in start");
                    int len$ = in.length;
                    for (int i$ = 0; i$ < len$; i$++) {
                        Log.v(OemCdmaTelephonyManager.TAG, "lteBcConfigToLong() print  in= " + byte2bits(in[i$]));
                    }
                    Log.v(OemCdmaTelephonyManager.TAG, "lteBcConfigToLong() print  in end");
                }
                long out = ((in[7] << 56) & (-72057594037927936L)) | ((in[6] << 48) & 71776119061217280L) | ((in[5] << 40) & 280375465082880L) | ((in[4] << 32) & 1095216660480L) | ((in[3] << 24) & 4278190080L) | ((in[2] << 16) & 16711680) | ((in[1] << 8) & 65280) | ((in[0] << 0) & 255);
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "lteBcConfigToLong, out =" + out);
                    Log.d(OemCdmaTelephonyManager.TAG, "lteBcConfigToLong out to BinaryString =" + Long.toBinaryString(out));
                }
                return out;
            }

            private static String byte2bits(byte b) {
                String str = Integer.toBinaryString(b | 256);
                int len = str.length();
                return str.substring(len - 8, len);
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public void serialize(ByteBuffer buf) {
                for (int i = 0; i < this.lte_bc_config.length; i++) {
                    buf.put(this.lte_bc_config[i]);
                }
                for (int i2 = 0; i2 < 8 - this.lte_bc_config.length; i2++) {
                    buf.put((byte) 0);
                }
                for (int i3 = 0; i3 < this.lte_bc_config_ext.length; i3++) {
                    buf.put(this.lte_bc_config_ext[i3]);
                }
                for (int i4 = 0; i4 < 8 - this.lte_bc_config_ext.length; i4++) {
                    buf.put((byte) 0);
                }
            }

            public static rde_lte_bc_config_type deserialize(ByteBuffer buf) {
                rde_lte_bc_config_type lteBcConfigType = new rde_lte_bc_config_type();
                for (int i = 0; i < lteBcConfigType.lte_bc_config.length; i++) {
                    lteBcConfigType.lte_bc_config[i] = buf.get();
                }
                for (int i2 = 0; i2 < lteBcConfigType.lte_bc_config_ext.length; i2++) {
                    lteBcConfigType.lte_bc_config_ext[i2] = buf.get();
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.v(OemCdmaTelephonyManager.TAG, "rde_lte_bc_config_type deserialize() lte_bc_config = " + lteBcConfigType.lte_bc_config + " lte_bc_config_ext = " + lteBcConfigType.lte_bc_config_ext);
                }
                return lteBcConfigType;
            }

            @Override // jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data.Serializable
            public int size() {
                return 16;
            }

            public String toString() {
                StringBuffer sb = new StringBuffer();
                sb.append(" lte_bc_config=" + OemCdmaTelephonyManager.byteArrayToHexString(this.lte_bc_config));
                sb.append(" lte_bc_config_ext=" + OemCdmaTelephonyManager.byteArrayToHexString(this.lte_bc_config_ext));
                return sb.toString();
            }
        }

        public static OEM_RIL_RDE_Data deserialize(ByteBuffer buf) {
            OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
            rde.elementID = buf.getInt();
            rde.recordNum = buf.getInt();
            rde.offset = buf.getInt();
            rde.length = buf.getInt();
            rde_obj_type rde_obj = new rde_obj_type();
            rde_obj.data = new byte[rde.length];
            switch (rde.elementID) {
                case 2:
                    rde.dataObj = rde_nam_accolc_type.deserialize(buf);
                    break;
                case 6:
                    rde.dataObj = rde_dial_type.deserialize(buf);
                    break;
                case 7:
                    rde.dataObj = rde_nam_mdn_type.deserialize(buf);
                    break;
                case 9:
                    rde.dataObj = rde_gen_profile_type.deserialize(buf);
                    break;
                case 10:
                case 23:
                case RDE_NV_OTKSL_I /* 27 */:
                case 36:
                case RDE_NV_EHRPD_ENABLED_I /* 43 */:
                case RDE_NV_CDMA_SO73_ENABLED_I /* 45 */:
                case 47:
                case RDE_NV_CDMA_SO68_ENABLED_I /* 48 */:
                case RDE_NV_MOB_TERM_HOME_I /* 50 */:
                case 51:
                case 52:
                case 55:
                case 56:
                case RDE_SHARP_NV_SPRINT_LTE_FORCE_ENABLE_I /* 8024 */:
                case RDE_SHARP_NV_SPRINT_EHRPD_FORCE_ENABLE_I /* 8025 */:
                case RDE_EFS_1XADVANCED_CAPABILITY_STATUS_I /* 10015 */:
                case RDE_EFS_SO73_COP0_SUPPORTED_I /* 10016 */:
                case RDE_NV_LTE_CAPABILITY_I /* 10017 */:
                case RDE_MIP_ERROR_CODE_I /* 10019 */:
                case RDE_NV_SLOTTED_MODE_I /* 10020 */:
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "Deserialize int length: " + rde.length);
                    }
                    for (int i = 0; i < rde.length; i++) {
                        rde_obj.data[i] = buf.get();
                    }
                    rde.dataObj = rde_obj;
                    break;
                case 16:
                    rde.dataObj = rde_nam_imsi_11_12_type.deserialize(buf);
                    break;
                case 17:
                    rde.dataObj = rde_nam_imsi_mcc_type.deserialize(buf);
                    break;
                case 25:
                    rde.dataObj = rde_nam_min1_type.deserialize(buf);
                    break;
                case 26:
                    rde.dataObj = rde_nam_min2_type.deserialize(buf);
                    break;
                case 30:
                    rde.dataObj = rde_pref_voice_so_type.deserialize(buf);
                    break;
                case RDE_NV_DS_MIP_SS_USER_PROF_I /* 46 */:
                    rde.dataObj = rde_ss_profile_type.deserialize(buf);
                    break;
                case 49:
                    rde.dataObj = rde_imsi_addr_num_type.deserialize(buf);
                    break;
                case 53:
                    rde.dataObj = rde_lte_bc_config_type.deserialize(buf);
                    break;
                case RDE_NV_ROAMING_LIST_683_I /* 108 */:
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "Deserialize roaming list");
                    }
                    rde.dataObj = rde_roaming_list_type.deserialize(buf);
                    break;
                default:
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "deserialize elementID (" + rde.elementID + ")");
                        break;
                    }
                    break;
            }
            return rde;
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum OEM_RIL_CDMA_MobilePRev {
        OEM_RIL_CDMA_P_REV_1(1),
        OEM_RIL_CDMA_P_REV_3(3),
        OEM_RIL_CDMA_P_REV_4(4),
        OEM_RIL_CDMA_P_REV_6(6),
        OEM_RIL_CDMA_P_REV_9(9),
        INVALID_DATA(65535);
        
        public static final int SIZE = 4;
        private final int id;

        OEM_RIL_CDMA_MobilePRev(int id) {
            this.id = id;
        }

        public int toInt() {
            return this.id;
        }

        public static OEM_RIL_CDMA_MobilePRev fromInt(int id) {
            OEM_RIL_CDMA_MobilePRev[] arr$ = values();
            for (OEM_RIL_CDMA_MobilePRev en : arr$) {
                if (en.id == id) {
                    return en;
                }
            }
            return INVALID_DATA;
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum OEM_RIL_CDMA_HybridModeState {
        OEM_RIL_CDMA_HYBRID_MODE_OFF(0),
        OEM_RIL_CDMA_HYBRID_MODE_ON(1),
        INVALID_DATA(65535);
        
        public static final int SIZE = 4;
        private final int id;

        OEM_RIL_CDMA_HybridModeState(int id) {
            this.id = id;
        }

        public int toInt() {
            return this.id;
        }

        public static OEM_RIL_CDMA_HybridModeState fromInt(int id) {
            OEM_RIL_CDMA_HybridModeState[] arr$ = values();
            for (OEM_RIL_CDMA_HybridModeState en : arr$) {
                if (en.id == id) {
                    return en;
                }
            }
            return INVALID_DATA;
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum OEM_RIL_CDMA_RevOption {
        OEM_RIL_CDMA_EVDO_REV_A(0),
        OEM_RIL_CDMA_EVDO_REV_0(1),
        INVALID_DATA(65535);
        
        public static final int SIZE = 4;
        private final int id;

        OEM_RIL_CDMA_RevOption(int id) {
            this.id = id;
        }

        public int toInt() {
            return this.id;
        }

        public static OEM_RIL_CDMA_RevOption fromInt(int id) {
            OEM_RIL_CDMA_RevOption[] arr$ = values();
            for (OEM_RIL_CDMA_RevOption en : arr$) {
                if (en.id == id) {
                    return en;
                }
            }
            return INVALID_DATA;
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum OEM_RIL_CDMA_DataRate {
        OEM_RIL_CDMA_DATA_RATE_153600_9600(0),
        OEM_RIL_CDMA_DATA_RATE_76800_38400(1),
        OEM_RIL_CDMA_DATA_RATE_38400_76800(2),
        OEM_RIL_CDMA_DATA_RATE_9600_76800(3),
        OEM_RIL_CDMA_DATA_RATE_64000_14400(4),
        OEM_RIL_CDMA_DATA_RATE_14400_14400(5),
        OEM_RIL_CDMA_DATA_RATE_153600_153600(6),
        INVALID_DATA(65535);
        
        public static final int SIZE = 4;
        private final int id;

        OEM_RIL_CDMA_DataRate(int id) {
            this.id = id;
        }

        public int toInt() {
            return this.id;
        }

        public static OEM_RIL_CDMA_DataRate fromInt(int id) {
            OEM_RIL_CDMA_DataRate[] arr$ = values();
            for (OEM_RIL_CDMA_DataRate en : arr$) {
                if (en.id == id) {
                    return en;
                }
            }
            return INVALID_DATA;
        }
    }

    public OEM_RIL_CDMA_Errno setServiceOption(OEM_RIL_CDMA_ServiceOption serviceOption, String spcLockCode, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setServiceOption()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.serviceOptionToByteArr(serviceOption, OEM_RIL_REQUEST_CDMA_FIELD_TEST_SET_SERVICE_OPTION, spcLockCode), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_FIELD_TEST_SET_SERVICE_OPTION), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getServiceOption(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getServiceOption()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_FIELD_TEST_GET_SERVICE_OPTION), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_FIELD_TEST_GET_SERVICE_OPTION), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getEvdoData(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getEvdoData()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_FIELD_TEST_GET_EVDO_DATA), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_FIELD_TEST_GET_EVDO_DATA), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getCallProcessingData(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getCallProcessingData()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_FIELD_TEST_GET_CALL_PROCESSING_DATA), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_FIELD_TEST_GET_CALL_PROCESSING_DATA), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamPrlVersion(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getNamPrlVersion()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_CDMA_PRL_VERSION), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_CDMA_PRL_VERSION), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getOtksl(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getOtksl()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_OTKSL);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 27;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getEsn(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getEsn()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_ESN);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 10;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getPrefVoiceSo(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getPrefVoiceSo()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_PREF_VOICE_SO);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 30;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setPrefVoiceSo(OEM_RIL_RDE_Data.rde_pref_voice_so_type option, String spcLockCode, Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "setPrefVoiceSo()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_PREF_VOICE_SO);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 30;
        rde.recordNum = 0;
        rde.offset = 0;
        rde.dataObj = option;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getSlotCycleIndex(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getSlotCycleIndex()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_SLOT_CYCLE_INDEX);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 47;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getSlotCycleMode(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getSlotCycleMode()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_SLOT_CYCLE_MODE);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = OEM_RIL_RDE_Data.RDE_NV_SLOTTED_MODE_I;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getMipErrorCode(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getMipErrorCode()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_MIP_ERROR_CODE_I);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = OEM_RIL_RDE_Data.RDE_MIP_ERROR_CODE_I;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamSidNidPairs(OEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs sidNidPairs, String spcLockCode, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setNamSidNidPairs()");
        }
        if (sidNidPairs.sidNid.length != 20) {
            return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.sidNidPairsToByteArr(sidNidPairs, OEM_RIL_REQUEST_CDMA_SET_SID_NID_PAIRS, spcLockCode), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_SID_NID_PAIRS), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamSidNidPairs(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getNamSidNidPairs()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_SID_NID_PAIRS), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_SID_NID_PAIRS), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamInfo(OEM_RIL_CDMA_NAM_Info namInfo, String spcLockCode, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setNamInfo()");
        }
        if (namInfo.mdn.length != 10 || namInfo.min.length != 10 || namInfo.imsi11_12.length != 2 || namInfo.imsiMcc.length != 3 || namInfo.imsiMccT.length != 3 || namInfo.imsiT.length != 15 || namInfo.newSpcCode.length != 6) {
            return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.namInfoToByteArr(namInfo, OEM_RIL_REQUEST_CDMA_SET_NAM_INFO, spcLockCode), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_NAM_INFO), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamInfo(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getNamInfo()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_NAM_INFO), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_NAM_INFO), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setMobilePRev(OEM_RIL_CDMA_MobilePRev rev, String spcLockCode, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setMobilePRev()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.mobilePRevToByteArr(rev, OEM_RIL_REQUEST_CDMA_SET_MOB_P_REV, spcLockCode), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_MOB_P_REV), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getMobilePRev(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getMobilePRev()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_MOB_P_REV), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_MOB_P_REV), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno checkSubsidyLockPasswd(OEM_RIL_CDMA_SubsidyPassword password, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "checkSubsidyLockPasswd()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.subsidyPasswdToByteArr(password), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_CHECK_SUBSIDY_LOCK_PASSWD), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getDDTMDefaultPreference(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getDDTMDefaultPreference()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_DDTM_DEFAULT_PREFERENCE), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_DDTM_DEFAULT_PREFERENCE), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setDDTMDefaultPreference(OEM_RIL_RDE_Data.DDTMDefaultPreferenceSettings defaultSettings, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setDDTMDefaultPreference()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_DDTM_DEFAULT_PREFERENCE);
        ByteBuffer buf = ByteBuffer.allocate(22);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        OemCdmaDataConverter.writeHookHeader(buf, OEM_RIL_REQUEST_CDMA_SET_DDTM_DEFAULT_PREFERENCE, 4, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS);
        buf.putInt(defaultSettings.toInt());
        invokeOemRilRequestRaw(buf.array(), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getMipGenProfile(int index, Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getMipGenProfile()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_MIP_GEN_USER_PROF);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 9;
        rde.recordNum = index;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setMipGenProfile(int index, OEM_RIL_RDE_Data.rde_gen_profile_type profile, Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "setMipGenProfile()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_MIP_GEN_USER_PROF);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 9;
        rde.recordNum = index;
        rde.offset = 0;
        rde.dataObj = profile;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getMipSsProfile(int index, Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getMipSsProfile()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_MIP_SS_USER_PROF);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 46;
        rde.recordNum = index;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setMipSsProfile(int index, OEM_RIL_RDE_Data.rde_ss_profile_type profile, Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "setMipSsProfile()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_MIP_SS_USER_PROF);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 46;
        rde.recordNum = index;
        rde.offset = 0;
        rde.dataObj = profile;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getSO68Enabled(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getSO68Enabled()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_CDMA_SO68_ENABLED);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 48;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setSO68Enabled(boolean enabled, Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "setSO68Enabled()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_CDMA_SO68_ENABLED);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 48;
        rde.dataObj = new OEM_RIL_RDE_Data.rde_obj_type(enabled ? (byte) 1 : (byte) 0);
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getHdrAnAuthPasswdLong(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getHdrAnAuthPasswdLong()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_HDR_AN_AUTH_PASSWD_LONG);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 4;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno factoryReset(Handler msgH) {
        return factoryReset(msgH, (byte) -1);
    }

    public OEM_RIL_CDMA_Errno factoryReset(Handler msgH, byte resetType) {
        if (DEBUG) {
            Log.v(TAG, "factoryReset type = " + ((int) resetType));
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_FACTORY_RESET);
        ByteBuffer buf = ByteBuffer.allocate(19);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        OemCdmaDataConverter.writeHookHeader(buf, OEM_RIL_REQUEST_CDMA_FACTORY_RESET, 1, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS);
        buf.put(resetType);
        invokeOemRilRequestRaw(buf.array(), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setPrl(byte[] prlData, Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "setPrl()");
        }
        if (prlData == null) {
            return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
        }
        OEM_RIL_RDE_Data.rde_roaming_list_type roamList = new OEM_RIL_RDE_Data.rde_roaming_list_type();
        roamList.setRoamingList(prlData);
        invokeOemRilRequestRaw(OemCdmaDataConverter.roamingListToByteArr(roamList, OEM_RIL_REQUEST_CDMA_SET_PRL, sDefaultSpcCode), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_PRL), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getBC0(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getBC0()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_BC0), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_BC0), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setBC0(int status, String spcLockCode, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setBC0()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_BC0);
        OEM_RIL_CDMA_BC bc = new OEM_RIL_CDMA_BC();
        bc.status = status;
        invokeOemRilRequestRaw(OemCdmaDataConverter.BCToByteArr(bc, OEM_RIL_REQUEST_CDMA_SET_BC0, spcLockCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getBC1(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getBC1()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_BC1), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_BC1), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setBC1(int status, String spcLockCode, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setBC1()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_BC1);
        OEM_RIL_CDMA_BC bc = new OEM_RIL_CDMA_BC();
        bc.status = status;
        invokeOemRilRequestRaw(OemCdmaDataConverter.BCToByteArr(bc, OEM_RIL_REQUEST_CDMA_SET_BC1, spcLockCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getBC10(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getBC10()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_BC10), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_BC10), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setBC10(int status, String spcLockCode, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setBC10()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_BC10);
        OEM_RIL_CDMA_BC bc = new OEM_RIL_CDMA_BC();
        bc.status = status;
        invokeOemRilRequestRaw(OemCdmaDataConverter.BCToByteArr(bc, OEM_RIL_REQUEST_CDMA_SET_BC10, spcLockCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getBC14(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getBC14()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_BC14), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_BC14), msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setBC14(int status, String spcLockCode, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setBC14()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_BC14);
        OEM_RIL_CDMA_BC bc = new OEM_RIL_CDMA_BC();
        bc.status = status;
        invokeOemRilRequestRaw(OemCdmaDataConverter.BCToByteArr(bc, OEM_RIL_REQUEST_CDMA_SET_BC14, spcLockCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamMdn(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getNamMdn()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_NAM_MDN);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 7;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamMdn(OEM_RIL_RDE_Data.rde_nam_mdn_type nvMdn, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setNamMdn()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_NAM_MDN);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 7;
        rde.recordNum = 0;
        rde.offset = 0;
        rde.dataObj = nvMdn;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamMin1(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getNamMin1()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_NAM_MIN1);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 25;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamMin1(OEM_RIL_RDE_Data.rde_nam_min1_type nvMin1, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setNamMin1()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_NAM_MIN1);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 25;
        rde.recordNum = 0;
        rde.offset = 0;
        rde.dataObj = nvMin1;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamMin2(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getNamMin2()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_NAM_MIN2);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 26;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamMin2(OEM_RIL_RDE_Data.rde_nam_min2_type nvMin2, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setNamMin2()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_NAM_MIN2);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 26;
        rde.recordNum = 0;
        rde.offset = 0;
        rde.dataObj = nvMin2;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamScm(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getNamScm()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_NAM_SCM);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 36;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamImsi1112(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getNamImsi1122()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_NAM_IMSI_11_12);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 16;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamImsi1112(OEM_RIL_RDE_Data.rde_nam_imsi_11_12_type nvMnc, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setNamImsi1112()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_NAM_IMSI_11_12);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 16;
        rde.recordNum = 0;
        rde.offset = 0;
        rde.dataObj = nvMnc;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamImsiMcc(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getNamImsiMcc()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_NAM_IMSI_MCC);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 17;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamImsiMcc(OEM_RIL_RDE_Data.rde_nam_imsi_mcc_type nvMcc, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setNamImsiMcc()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_NAM_IMSI_MCC);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 17;
        rde.recordNum = 0;
        rde.offset = 0;
        rde.dataObj = nvMcc;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamAccolc(Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "getNamImsiAccolc()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_NAM_ACCOLC);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 2;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamAccolc(OEM_RIL_RDE_Data.rde_nam_accolc_type nvAccolc, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setNamAccolc()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_NAM_ACCOLC);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 2;
        rde.recordNum = 0;
        rde.offset = 0;
        rde.dataObj = nvAccolc;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getHomeSIDReg(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getHomeSIDReg()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_HOME_SID_REG);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 50;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getForSIDReg(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getForSIDReg()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_FOR_SID_REG);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 51;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getForNIDReg(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getForNIDReg()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_FOR_NID_REG);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 52;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setHomeSIDReg(int enabled, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setHomeSIDReg()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_HOME_SID_REG);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 50;
        rde.recordNum = 0;
        rde.offset = 0;
        OEM_RIL_RDE_Data.rde_mob_term_type mob_term_home = new OEM_RIL_RDE_Data.rde_mob_term_type();
        mob_term_home.enabled[0] = (byte) enabled;
        mob_term_home.enabled[1] = (byte) enabled;
        rde.dataObj = mob_term_home;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setForSIDReg(int enabled, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setForSIDReg()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_FOR_SID_REG);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 51;
        rde.recordNum = 0;
        rde.offset = 0;
        OEM_RIL_RDE_Data.rde_mob_term_type mob_term_sid = new OEM_RIL_RDE_Data.rde_mob_term_type();
        mob_term_sid.enabled[0] = (byte) enabled;
        mob_term_sid.enabled[1] = (byte) enabled;
        rde.dataObj = mob_term_sid;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setForNIDReg(int enabled, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setForNIDReg()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_FOR_NID_REG);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 52;
        rde.recordNum = 0;
        rde.offset = 0;
        OEM_RIL_RDE_Data.rde_mob_term_type mob_term_nid = new OEM_RIL_RDE_Data.rde_mob_term_type();
        mob_term_nid.enabled[0] = (byte) enabled;
        mob_term_nid.enabled[1] = (byte) enabled;
        rde.dataObj = mob_term_nid;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getIMSIAddrNum(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getIMSIAddrNum()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_IMSI_M_ADDR_NUM);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 49;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setIMSIAddrNum(int number, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setIMSIAddrNum()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_IMSI_M_ADDR_NUM);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 49;
        rde.recordNum = 0;
        rde.offset = 0;
        OEM_RIL_RDE_Data.rde_imsi_addr_num_type imsiMAddrNum = new OEM_RIL_RDE_Data.rde_imsi_addr_num_type();
        imsiMAddrNum.num = (byte) number;
        rde.dataObj = imsiMAddrNum;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getLockCode(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getLockCode()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_LOCK_CODE);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 23;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setLockCode(String code, String spcLockCode, Handler msgH) {
        if (DEBUG) {
            Log.d(TAG, "setLockCode()");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_LOCK_CODE);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 23;
        rde.dataObj = new OEM_RIL_RDE_Data.rde_obj_type(code);
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno set1xAdvancedOption(boolean enabled, Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "set1xAdvancedOption - enabled: " + enabled);
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_1xADV_OPTION);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = OEM_RIL_RDE_Data.RDE_EFS_1XADVANCED_CAPABILITY_STATUS_I;
        rde.dataObj = new OEM_RIL_RDE_Data.rde_obj_type(enabled ? (byte) 1 : (byte) 0);
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno get1xAdvancedOption(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "get1xAdvancedOption");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_1xADV_OPTION);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = OEM_RIL_RDE_Data.RDE_EFS_1XADVANCED_CAPABILITY_STATUS_I;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setSo73Option(boolean enabled, Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "setSo73Option - enabled: " + enabled);
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_SO73_OPTION);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 45;
        rde.dataObj = new OEM_RIL_RDE_Data.rde_obj_type(enabled ? (byte) 1 : (byte) 0);
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getSo73Option(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getSo73Option");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_SO73_OPTION);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = 45;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setSo73Cop0Option(boolean enabled, Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "setSo73Cop0Option - enabled: " + enabled);
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_SO73COP0_OPTION);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = OEM_RIL_RDE_Data.RDE_EFS_SO73_COP0_SUPPORTED_I;
        rde.dataObj = new OEM_RIL_RDE_Data.rde_obj_type(enabled ? (byte) 1 : (byte) 0);
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getSo73Cop0Option(Handler msgH) {
        if (DEBUG) {
            Log.v(TAG, "getSo73Cop0Option");
        }
        Message msg = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_SO73COP0_OPTION);
        OEM_RIL_RDE_Data rde = new OEM_RIL_RDE_Data();
        rde.elementID = OEM_RIL_RDE_Data.RDE_EFS_SO73_COP0_SUPPORTED_I;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(rde, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), msg, msgH);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class OemCdmaDataConverter {
        public static String byteArrToHexString(byte[] arr) {
            if (arr == null) {
                return "null";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("hex:");
            for (int i = 0; i < arr.length; i++) {
                sb.append(String.format("%02X", Byte.valueOf(arr[i])));
            }
            return sb.toString();
        }

        public static String byteArrToString(byte[] arr) {
            int i = 0;
            while (true) {
                if (i >= arr.length) {
                    break;
                }
                int i2 = i + 1;
                if (arr[i] == 0) {
                    i = i2;
                    break;
                }
                i = i2;
            }
            return new String(arr, 0, i);
        }

        public static byte[] rdeDataToByteArr(OEM_RIL_RDE_Data rde, int msgId, String spcLockCode) {
            return rdeDataToByteArr(rde, msgId, spcLockCode, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS);
        }

        public static byte[] rdeDataToByteArr(OEM_RIL_RDE_Data rde, int msgId, String spcLockCode, OEM_RIL_CDMA_Errno err) {
            ByteBuffer buf = ByteBuffer.allocate((rde != null ? rde.SIZE() : 0) + 18);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(buf, msgId, rde != null ? rde.SIZE() : 0, err, spcLockCode);
            if (rde != null) {
                buf.putInt(rde.elementID);
                buf.putInt(rde.recordNum);
                buf.putInt(rde.offset);
                if (rde.dataObj != null) {
                    buf.putInt(rde.dataObj.size());
                    rde.dataObj.serialize(buf);
                } else {
                    buf.putInt(0);
                    buf.put((byte) 0);
                }
            }
            byte[] data = buf.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "rdeDataToByteArr: data = " + byteArrToHexString(data));
            }
            return data;
        }

        public static byte[] serviceOptionToByteArr(OEM_RIL_CDMA_ServiceOption serviceOption, int msgId, String spcLockCode) {
            ByteBuffer buf = ByteBuffer.allocate(22);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(buf, msgId, 4, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, spcLockCode);
            buf.putInt(serviceOption.toInt());
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "serviceOptionToByteArr: serviceOption = " + serviceOption.toString());
            }
            byte[] data = buf.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "serviceOptionToByteArr: data = " + byteArrToHexString(data));
            }
            return data;
        }

        public static byte[] subsidyPasswdToByteArr(OEM_RIL_CDMA_SubsidyPassword password) {
            ByteBuffer buf = ByteBuffer.allocate(24);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(buf, (int) OemCdmaTelephonyManager.OEM_RIL_REQUEST_CDMA_CHECK_SUBSIDY_LOCK_PASSWD, 6, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, password.password);
            for (int i = 0; i < 6; i++) {
                buf.put(password.password[i]);
            }
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "subsidyPasswdToByteArr: password.password = " + byteArrToHexString(password.password));
            }
            byte[] data = buf.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "subsidyPasswdToByteArr: data = " + byteArrToHexString(data));
            }
            return data;
        }

        public static byte[] sidNidPairsToByteArr(OEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs sidNidPairs, int msgId, String spcLockCode) {
            ByteBuffer buf = ByteBuffer.allocate(sidNidPairs.SIZE() + 18);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(buf, msgId, sidNidPairs.SIZE(), OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, spcLockCode);
            for (int i = 0; i < sidNidPairs.sidNid.length; i++) {
                buf.putInt(sidNidPairs.sidNid[i].sid);
                buf.putInt(sidNidPairs.sidNid[i].nid);
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "sidNidPairsToByteArr: sidNidPairs.sidNid[" + i + "].sid = " + sidNidPairs.sidNid[i].sid);
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "sidNidPairsToByteArr: sidNidPairs.sidNid[" + i + "].nid = " + sidNidPairs.sidNid[i].nid);
                }
            }
            byte[] data = buf.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "sidNidPairsToByteArr: data = " + byteArrToHexString(data));
            }
            return data;
        }

        public static byte[] roamingListToByteArr(OEM_RIL_RDE_Data.rde_roaming_list_type roaming_list, int msgId, String spcLockCode) {
            ByteBuffer buf = ByteBuffer.allocate(roaming_list.size() + 18);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(buf, msgId, roaming_list.size(), OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, spcLockCode);
            roaming_list.serialize(buf);
            byte[] data = buf.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "roamingListToByteArr: data = " + byteArrToString(data));
            }
            return data;
        }

        public static byte[] namInfoToByteArr(OEM_RIL_CDMA_NAM_Info namInfo, int msgId, String spcLockCode) {
            byte[] data = null;
            ByteBuffer buf = ByteBuffer.allocate(144);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(buf, msgId, (int) OEM_RIL_CDMA_NAM_Info.SIZE, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, spcLockCode);
            int len = namInfo.mdn.length;
            if (len == 10) {
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.mdn = " + byteArrToHexString(namInfo.mdn));
                }
                for (int i = 0; i < len; i++) {
                    buf.put(namInfo.mdn[i]);
                }
                for (int i2 = 10; i2 < 16; i2++) {
                    buf.put((byte) 0);
                }
                int len2 = namInfo.min.length;
                if (len2 == 10) {
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.min = " + byteArrToHexString(namInfo.min));
                    }
                    for (int i3 = 0; i3 < len2; i3++) {
                        buf.put(namInfo.min[i3]);
                    }
                    for (int i4 = 10; i4 < 16; i4++) {
                        buf.put((byte) 0);
                    }
                    buf.putInt(namInfo.h_sid);
                    buf.putInt(namInfo.h_nid);
                    buf.putInt(namInfo.scm);
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.h_sid = " + namInfo.h_sid);
                    }
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.h_nid = " + namInfo.h_nid);
                    }
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.scm = " + namInfo.scm);
                    }
                    int len3 = namInfo.imsi11_12.length;
                    if (len3 == 2) {
                        if (OemCdmaTelephonyManager.DEBUG) {
                            Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.imsi11_12 = " + byteArrToHexString(namInfo.imsi11_12));
                        }
                        for (int i5 = 0; i5 < len3; i5++) {
                            buf.put(namInfo.imsi11_12[i5]);
                        }
                        for (int i6 = 2; i6 < 4; i6++) {
                            buf.put((byte) 0);
                        }
                        int len4 = namInfo.imsiMcc.length;
                        if (len4 == 3) {
                            if (OemCdmaTelephonyManager.DEBUG) {
                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.imsiMcc = " + byteArrToHexString(namInfo.imsiMcc));
                            }
                            for (int i7 = 0; i7 < len4; i7++) {
                                buf.put(namInfo.imsiMcc[i7]);
                            }
                            for (int i8 = 3; i8 < 4; i8++) {
                                buf.put((byte) 0);
                            }
                            buf.putInt(namInfo.priCdmaA);
                            buf.putInt(namInfo.priCdmaB);
                            buf.putInt(namInfo.secCdmaA);
                            buf.putInt(namInfo.secCdmaB);
                            buf.putInt(namInfo.vocoderType);
                            if (OemCdmaTelephonyManager.DEBUG) {
                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.priCdmaA = " + namInfo.priCdmaA);
                            }
                            if (OemCdmaTelephonyManager.DEBUG) {
                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.priCdmaB = " + namInfo.priCdmaB);
                            }
                            if (OemCdmaTelephonyManager.DEBUG) {
                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.secCdmaA = " + namInfo.secCdmaA);
                            }
                            if (OemCdmaTelephonyManager.DEBUG) {
                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.secCdmaB = " + namInfo.secCdmaB);
                            }
                            if (OemCdmaTelephonyManager.DEBUG) {
                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.vocoderType = " + namInfo.vocoderType);
                            }
                            int len5 = namInfo.imsiMccT.length;
                            if (len5 == 3) {
                                if (OemCdmaTelephonyManager.DEBUG) {
                                    Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.imsiMccT = " + byteArrToHexString(namInfo.imsiMccT));
                                }
                                for (int i9 = 0; i9 < len5; i9++) {
                                    buf.put(namInfo.imsiMccT[i9]);
                                }
                                for (int i10 = 3; i10 < 4; i10++) {
                                    buf.put((byte) 0);
                                }
                                int len6 = namInfo.imsiT.length;
                                if (len6 == 15) {
                                    if (OemCdmaTelephonyManager.DEBUG) {
                                        Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.imsiT = " + byteArrToHexString(namInfo.imsiT));
                                    }
                                    for (int i11 = 0; i11 < len6; i11++) {
                                        buf.put(namInfo.imsiT[i11]);
                                    }
                                    for (int i12 = 15; i12 < 16; i12++) {
                                        buf.put((byte) 0);
                                    }
                                    buf.putInt(namInfo.accessOverloadClass);
                                    if (OemCdmaTelephonyManager.DEBUG) {
                                        Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.accessOverloadClass = " + namInfo.accessOverloadClass);
                                    }
                                    if (namInfo.imsiMAddrNum == 0) {
                                        namInfo.imsiMAddrNum = 255;
                                    }
                                    buf.putInt(namInfo.imsiMAddrNum);
                                    if (OemCdmaTelephonyManager.DEBUG) {
                                        Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.imsiMAddrNum = " + namInfo.imsiMAddrNum);
                                    }
                                    int len7 = namInfo.imsiMS1_0.length;
                                    if (len7 == 4) {
                                        if (OemCdmaTelephonyManager.DEBUG) {
                                            Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.imsiMS1_0 = " + byteArrToString(namInfo.imsiMS1_0));
                                        }
                                        for (int i13 = 0; i13 < len7; i13++) {
                                            buf.put(namInfo.imsiMS1_0[i13]);
                                        }
                                        int len8 = namInfo.imsiMS2.length;
                                        if (len8 == 4) {
                                            if (OemCdmaTelephonyManager.DEBUG) {
                                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.imsiMS2 = " + byteArrToString(namInfo.imsiMS2));
                                            }
                                            for (int i14 = 0; i14 < len8; i14++) {
                                                buf.put(namInfo.imsiMS2[i14]);
                                            }
                                            buf.putInt(namInfo.mob_term_home);
                                            if (OemCdmaTelephonyManager.DEBUG) {
                                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.mob_term_home = " + namInfo.mob_term_home);
                                            }
                                            buf.putInt(namInfo.mob_term_sid);
                                            if (OemCdmaTelephonyManager.DEBUG) {
                                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.mob_term_sid = " + namInfo.mob_term_sid);
                                            }
                                            buf.putInt(namInfo.mob_term_nid);
                                            if (OemCdmaTelephonyManager.DEBUG) {
                                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.mob_term_nid = " + namInfo.mob_term_nid);
                                            }
                                            int len9 = namInfo.newSpcCode.length;
                                            if (len9 == 6) {
                                                if (OemCdmaTelephonyManager.DEBUG) {
                                                    Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.newSpcCode = " + byteArrToHexString(namInfo.newSpcCode));
                                                }
                                                for (int i15 = 0; i15 < len9; i15++) {
                                                    buf.put(namInfo.newSpcCode[i15]);
                                                }
                                                data = buf.array();
                                                if (OemCdmaTelephonyManager.DEBUG) {
                                                    Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: data = " + byteArrToHexString(data));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return data;
        }

        public static byte[] mobilePRevToByteArr(OEM_RIL_CDMA_MobilePRev rev, int msgId, String spcLockCode) {
            ByteBuffer buf = ByteBuffer.allocate(22);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(buf, msgId, 4, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, spcLockCode);
            buf.putInt(rev.toInt());
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "mobilePRevToByteArr: rev = " + rev);
            }
            byte[] data = buf.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "mobilePRevToByteArr: data = " + byteArrToHexString(data));
            }
            return data;
        }

        public static byte[] namPrlVersionToByteArr(OEM_RIL_CDMA_NAM_PrlVersion prl, int msgId, String spcLockCode) {
            ByteBuffer buf = ByteBuffer.allocate(22);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(buf, msgId, 4, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, spcLockCode);
            buf.putInt(prl.prlVerison);
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "namPrlVersionToByteArr: prlVerison = " + prl.prlVerison);
            }
            byte[] data = buf.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "namPrlVersionToByteArr: data = " + byteArrToHexString(data));
            }
            return data;
        }

        public static byte[] cpStatusToByteArr(OEM_RIL_CDMA_CP_Status cp, int msgId, String spcLockCode) {
            ByteBuffer buf = ByteBuffer.allocate(322);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(buf, msgId, (int) OEM_RIL_CDMA_CP_Status.SIZE, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, spcLockCode);
            buf.putInt(cp.fer);
            buf.putInt(cp.activePilotCount);
            buf.putInt(cp.neighborPilotCount);
            buf.putInt(cp.candPilotCount);
            buf.putInt(cp.bestActivePilot);
            buf.putInt(cp.bestActiveStrength);
            buf.putInt(cp.bestNeighborPilot);
            buf.putInt(cp.bestNeighborStrength);
            buf.putInt(cp.sid);
            buf.putInt(cp.nid);
            buf.putInt(cp.channel);
            buf.putInt(cp.serviceOption);
            buf.putInt(cp.droppedCallCounter);
            buf.putInt(cp.callCounter);
            buf.putInt(cp.lastCallIndicator);
            buf.putInt(cp.PRevInUse);
            buf.putInt(cp.band);
            buf.putInt(cp.lnaStatus);
            buf.putInt(cp.cpState);
            buf.putInt(cp.txPower);
            buf.putInt(cp.rssi);
            buf.putInt(cp.bid);
            for (int i = 0; i < 6; i++) {
                buf.putInt(cp.activeSetPn[i]);
            }
            for (int i2 = 0; i2 < 6; i2++) {
                buf.putInt(cp.activeSetStrength[i2]);
            }
            for (int i3 = 0; i3 < 40; i3++) {
                buf.putInt(cp.neighborSetPn[i3]);
            }
            buf.putInt(cp.bsLat);
            buf.putInt(cp.bsLon);
            buf.put(cp.is2000System);
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.fer = " + cp.fer);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.bestActivePilot = " + cp.bestActivePilot);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.bestActiveStrength = " + cp.bestActiveStrength);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.bestNeighborPilot = " + cp.bestNeighborPilot);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.bestNeighborStrength = " + cp.bestNeighborStrength);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.sid = " + cp.sid);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.nid = " + cp.nid);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.channel = " + cp.channel);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.serviceOption = " + cp.serviceOption);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.droppedCallCounter = " + cp.droppedCallCounter);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.txPower = " + cp.txPower);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.band = " + cp.band);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.activePilotCount = " + cp.activePilotCount);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.neighborPilotCount = " + cp.neighborPilotCount);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.candPilotCount = " + cp.candPilotCount);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.cpState = " + cp.cpState);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.lastCallIndicator = " + cp.lastCallIndicator);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.lnaStatus = " + cp.lnaStatus);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.rssi = " + cp.rssi);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.callCounter = " + cp.callCounter);
            }
            byte[] data = buf.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: data = " + byteArrToHexString(data));
            }
            return data;
        }

        public static byte[] evdoDataToByteArr(OEM_RIL_CDMA_EVDO_Status status, int msgId, String spcLockCode) {
            ByteBuffer buf = ByteBuffer.allocate(PduHeaders.STORE);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(buf, msgId, 144, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, spcLockCode);
            buf.putInt(status.hdrChanNum);
            buf.putInt(status.uatiColorCode);
            buf.putInt(status.txUati);
            buf.putInt(status.pilotPn);
            buf.putInt(status.hdrRssi);
            buf.putInt(status.rxPwrRx0Dbm);
            buf.putInt(status.rxPwrRx1Dbm);
            buf.putInt(status.measPkts);
            buf.putInt(status.errPkts);
            buf.putInt(status.sessionState);
            buf.putInt(status.atState);
            buf.putInt(status.ip);
            buf.putInt(status.userCount);
            buf.putInt(status.dropCount);
            buf.putInt(status.hybridMode);
            buf.putInt(status.macIndex);
            for (int i = 0; i < 16; i++) {
                buf.putInt(status.sectorIds[i]);
            }
            buf.putInt(status.pilotEnergy);
            buf.putInt(status.drcCover);
            buf.putInt(status.sinr);
            buf.putInt(status.anAuthStatus);
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.hdrChanNum = " + status.hdrChanNum);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.uatiColorCode = " + status.uatiColorCode);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.txUati = " + status.txUati);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.pilotPn = " + status.pilotPn);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.hdrRssi = " + status.hdrRssi);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.rxPwrRx0Dbm = " + status.rxPwrRx0Dbm);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.rxPwrRx1Dbm = " + status.rxPwrRx1Dbm);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.measPkts = " + status.measPkts);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.errPkts = " + status.errPkts);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.sessionState = " + status.sessionState);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.atState = " + status.atState);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.ip = " + status.ip);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.userCount = " + status.userCount);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.hybridMode = " + status.hybridMode);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.macIndex = " + status.macIndex);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.drcCover = " + status.drcCover);
            }
            byte[] data = buf.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: data = " + byteArrToHexString(data));
            }
            return data;
        }

        public static byte[] BCToByteArr(OEM_RIL_CDMA_BC bc, int msgId, String spcLockCode) {
            ByteBuffer buf = ByteBuffer.allocate(22);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(buf, msgId, 4, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, spcLockCode);
            buf.putInt(bc.status);
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "BCToByteArr = " + bc.status);
            }
            return buf.array();
        }

        public static byte[] ActiveProfileToByteArr(OEM_RIL_CDMA_ACTIVE_PROF active_prof, int msgId, String spcLockCode) {
            ByteBuffer buf = ByteBuffer.allocate(22);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(buf, msgId, 4, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, spcLockCode);
            buf.putInt(active_prof.profile);
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "ActiveProfileToByteArr = " + active_prof.profile);
            }
            byte[] data = buf.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "ActiveProfileToByteArr: data = " + byteArrToString(data));
            }
            return data;
        }

        public static OEM_RIL_CDMA_Result byteArrToServiceOption(byte[] data) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToServiceOption: data = " + byteArrToHexString(data));
            }
            return data == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToServiceOption(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToServiceOption(ByteBuffer buf) {
            OEM_RIL_CDMA_Result result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader header = readHookHeader(buf);
                if (header.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    result.errno = header.error;
                } else {
                    OEM_RIL_CDMA_ServiceOption option = OEM_RIL_CDMA_ServiceOption.fromInt(buf.getInt());
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToServiceOption: option = " + option.toString());
                    }
                    result.obj = option;
                }
            } catch (BufferUnderflowException e) {
                result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return result;
        }

        public static OEM_RIL_CDMA_Result byteArrToMobilePRev(byte[] data) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToMobilePRev: data = " + byteArrToHexString(data));
            }
            return data == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToMobilePRev(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToMobilePRev(ByteBuffer buf) {
            OEM_RIL_CDMA_Result result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader header = readHookHeader(buf);
                if (header.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    result.errno = header.error;
                } else {
                    OEM_RIL_CDMA_MobilePRev rev = OEM_RIL_CDMA_MobilePRev.fromInt(buf.getInt());
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToMobilePRev: rev = " + rev.toString());
                    }
                    result.obj = rev;
                }
            } catch (BufferUnderflowException e) {
                result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return result;
        }

        public static OEM_RIL_CDMA_Result byteArrToCpStatus(byte[] data) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: data = " + byteArrToHexString(data));
            }
            return data == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToCpStatus(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToCpStatus(ByteBuffer buf) {
            OEM_RIL_CDMA_Result result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader header = readHookHeader(buf);
                if (header.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    result.errno = header.error;
                } else {
                    OEM_RIL_CDMA_CP_Status cp = new OEM_RIL_CDMA_CP_Status();
                    cp.fer = buf.getInt();
                    cp.activePilotCount = buf.getInt();
                    cp.neighborPilotCount = buf.getInt();
                    cp.candPilotCount = buf.getInt();
                    cp.bestActivePilot = buf.getInt();
                    cp.bestActiveStrength = buf.getInt();
                    cp.bestNeighborPilot = buf.getInt();
                    cp.bestNeighborStrength = buf.getInt();
                    cp.sid = buf.getInt();
                    cp.nid = buf.getInt();
                    cp.channel = buf.getInt();
                    cp.serviceOption = buf.getInt();
                    cp.droppedCallCounter = buf.getInt();
                    cp.callCounter = buf.getInt();
                    cp.lastCallIndicator = buf.getInt();
                    cp.PRevInUse = buf.getInt();
                    cp.band = buf.getInt();
                    cp.lnaStatus = buf.getInt();
                    cp.cpState = buf.getInt();
                    cp.txPower = buf.getInt();
                    cp.rssi = buf.getInt();
                    cp.bid = buf.getInt();
                    cp.activeSetPn = new int[6];
                    for (int i = 0; i < 6; i++) {
                        cp.activeSetPn[i] = buf.getInt();
                    }
                    cp.activeSetStrength = new int[6];
                    for (int i2 = 0; i2 < 6; i2++) {
                        cp.activeSetStrength[i2] = buf.getInt();
                    }
                    cp.neighborSetPn = new int[40];
                    for (int i3 = 0; i3 < 40; i3++) {
                        cp.neighborSetPn[i3] = buf.getInt();
                    }
                    cp.bsLat = buf.getInt();
                    cp.bsLon = buf.getInt();
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.fer = " + cp.fer);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.bestActivePilot = " + cp.bestActivePilot);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.bestActiveStrength = " + cp.bestActiveStrength);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.bestNeighborPilot = " + cp.bestNeighborPilot);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.bestNeighborStrength = " + cp.bestNeighborStrength);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.sid = " + cp.sid);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.nid = " + cp.nid);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.channel = " + cp.channel);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.serviceOption = " + cp.serviceOption);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.droppedCallCounter = " + cp.droppedCallCounter);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.txPower = " + cp.txPower);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.band = " + cp.band);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.activePilotCount = " + cp.activePilotCount);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.neighborPilotCount = " + cp.neighborPilotCount);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.candPilotCount = " + cp.candPilotCount);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.cpState = " + cp.cpState);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.lastCallIndicator = " + cp.lastCallIndicator);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.lnaStatus = " + cp.lnaStatus);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.rssi = " + cp.rssi);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.callCounter = " + cp.callCounter);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.bsLon = " + cp.bsLon);
                    }
                    result.obj = cp;
                }
            } catch (BufferUnderflowException e) {
                result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return result;
        }

        public static OEM_RIL_CDMA_Result byteArrToSubsidyPasswd(byte[] data) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToSubsidyPasswd: data = " + byteArrToHexString(data));
            }
            return data == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToSubsidyPasswd(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToSubsidyPasswd(ByteBuffer buf) {
            OEM_RIL_CDMA_Result result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader header = readHookHeader(buf);
                if (header.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    result.errno = header.error;
                } else {
                    OEM_RIL_CDMA_SubsidyPassword password = new OEM_RIL_CDMA_SubsidyPassword();
                    password.password = new byte[6];
                    for (int i = 0; i < password.password.length; i++) {
                        password.password[i] = buf.get();
                    }
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToSubsidyPasswd: password = " + byteArrToHexString(password.password));
                    }
                    result.obj = password;
                }
            } catch (BufferUnderflowException e) {
                result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return result;
        }

        public static OEM_RIL_CDMA_Result byteArrToEvdoData(byte[] data) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: data = " + byteArrToHexString(data));
            }
            return data == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToEvdoData(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToEvdoData(ByteBuffer buf) {
            OEM_RIL_CDMA_Result result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader header = readHookHeader(buf);
                if (header.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    result.errno = header.error;
                } else {
                    OEM_RIL_CDMA_EVDO_Status status = new OEM_RIL_CDMA_EVDO_Status();
                    status.hdrChanNum = buf.getInt();
                    status.uatiColorCode = buf.getInt();
                    status.txUati = buf.getInt();
                    status.pilotPn = buf.getInt();
                    status.hdrRssi = buf.getInt();
                    status.rxPwrRx0Dbm = buf.getInt();
                    status.rxPwrRx1Dbm = buf.getInt();
                    status.measPkts = buf.getInt();
                    status.errPkts = buf.getInt();
                    status.sessionState = buf.getInt();
                    status.atState = buf.getInt();
                    status.ip = buf.getInt();
                    status.userCount = buf.getInt();
                    status.dropCount = buf.getInt();
                    status.hybridMode = buf.getInt();
                    status.macIndex = buf.getInt();
                    status.sectorIds = new int[16];
                    for (int i = 0; i < 16; i++) {
                        status.sectorIds[i] = buf.getInt();
                    }
                    status.pilotEnergy = buf.getInt();
                    status.drcCover = buf.getInt();
                    status.sinr = buf.getInt();
                    status.anAuthStatus = buf.getInt();
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.hdrChanNum = " + status.hdrChanNum);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.uatiColorCode = " + status.uatiColorCode);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.txUati = " + status.txUati);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.pilotPn = " + status.pilotPn);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.hdrRssi = " + status.hdrRssi);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.rxPwrRx0Dbm = " + status.rxPwrRx0Dbm);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.rxPwrRx1Dbm = " + status.rxPwrRx1Dbm);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.measPkts = " + status.measPkts);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.errPkts = " + status.errPkts);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.sessionState = " + status.sessionState);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.atState = " + status.atState);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.ip = " + status.ip);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.userCount = " + status.userCount);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.hybridMode = " + status.hybridMode);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.macIndex = " + status.macIndex);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.drcCover = " + status.drcCover);
                    }
                    result.obj = status;
                }
            } catch (BufferUnderflowException e) {
                result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return result;
        }

        public static OEM_RIL_CDMA_Result byteArrToNamInfo(byte[] data) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: data = " + byteArrToHexString(data));
            }
            return data == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToNamInfo(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToNamInfo(ByteBuffer buf) {
            OEM_RIL_CDMA_Result result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader header = readHookHeader(buf);
                if (header.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    result.errno = header.error;
                } else {
                    OEM_RIL_CDMA_NAM_Info namInfo = new OEM_RIL_CDMA_NAM_Info();
                    namInfo.mdn = new byte[10];
                    for (int i = 0; i < namInfo.mdn.length; i++) {
                        namInfo.mdn[i] = buf.get();
                    }
                    for (int i2 = 10; i2 < 16; i2++) {
                        buf.get();
                    }
                    namInfo.min = new byte[10];
                    for (int i3 = 0; i3 < namInfo.min.length; i3++) {
                        namInfo.min[i3] = buf.get();
                    }
                    for (int i4 = 10; i4 < 16; i4++) {
                        buf.get();
                    }
                    namInfo.h_sid = buf.getInt();
                    namInfo.h_nid = buf.getInt();
                    namInfo.scm = buf.getInt();
                    namInfo.imsi11_12 = new byte[2];
                    for (int i5 = 0; i5 < namInfo.imsi11_12.length; i5++) {
                        namInfo.imsi11_12[i5] = buf.get();
                    }
                    for (int i6 = 2; i6 < 4; i6++) {
                        buf.get();
                    }
                    namInfo.imsiMcc = new byte[3];
                    for (int i7 = 0; i7 < namInfo.imsiMcc.length; i7++) {
                        namInfo.imsiMcc[i7] = buf.get();
                    }
                    for (int i8 = 3; i8 < 4; i8++) {
                        buf.get();
                    }
                    namInfo.priCdmaA = buf.getInt();
                    namInfo.priCdmaB = buf.getInt();
                    namInfo.secCdmaA = buf.getInt();
                    namInfo.secCdmaB = buf.getInt();
                    namInfo.vocoderType = buf.getInt();
                    namInfo.imsiMccT = new byte[3];
                    for (int i9 = 0; i9 < namInfo.imsiMccT.length; i9++) {
                        namInfo.imsiMccT[i9] = buf.get();
                    }
                    for (int i10 = 3; i10 < 4; i10++) {
                        buf.get();
                    }
                    namInfo.imsiT = new byte[15];
                    for (int i11 = 0; i11 < namInfo.imsiT.length; i11++) {
                        namInfo.imsiT[i11] = buf.get();
                    }
                    for (int i12 = 15; i12 < 16; i12++) {
                        buf.get();
                    }
                    namInfo.accessOverloadClass = buf.getInt();
                    namInfo.imsiMAddrNum = buf.getInt();
                    if (namInfo.imsiMAddrNum == 255) {
                        namInfo.imsiMAddrNum = 0;
                    }
                    namInfo.imsiMS1_0 = new byte[4];
                    for (int i13 = 0; i13 < namInfo.imsiMS1_0.length; i13++) {
                        namInfo.imsiMS1_0[i13] = buf.get();
                    }
                    namInfo.imsiMS2 = new byte[4];
                    for (int i14 = 0; i14 < namInfo.imsiMS2.length; i14++) {
                        namInfo.imsiMS2[i14] = buf.get();
                    }
                    namInfo.mob_term_home = buf.getInt();
                    namInfo.mob_term_sid = buf.getInt();
                    namInfo.mob_term_nid = buf.getInt();
                    namInfo.newSpcCode = new byte[6];
                    for (int i15 = 0; i15 < namInfo.newSpcCode.length; i15++) {
                        namInfo.newSpcCode[i15] = buf.get();
                    }
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.mdn = " + byteArrToHexString(namInfo.mdn));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.min = " + byteArrToHexString(namInfo.min));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.h_sid = " + namInfo.h_sid);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.h_nid = " + namInfo.h_nid);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.scm = " + namInfo.scm);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.imsi11_12 = " + byteArrToHexString(namInfo.imsi11_12));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.imsiMcc = " + byteArrToHexString(namInfo.imsiMcc));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.priCdmaA = " + namInfo.priCdmaA);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.priCdmaB = " + namInfo.priCdmaB);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.secCdmaA = " + namInfo.secCdmaA);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.secCdmaB = " + namInfo.secCdmaB);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.vocoderType = " + namInfo.vocoderType);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.imsiMccT = " + byteArrToHexString(namInfo.imsiMccT));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.imsiT = " + byteArrToHexString(namInfo.imsiT));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.accessOverloadClass = " + namInfo.accessOverloadClass);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.imsiMAddrNum = " + namInfo.imsiMAddrNum);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.imsiMS2 = " + byteArrToString(namInfo.imsiMS2));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.imsiMS1_0 = " + byteArrToString(namInfo.imsiMS1_0));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.mob_term_home = " + namInfo.mob_term_home);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.mob_term_sid = " + namInfo.mob_term_sid);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.mob_term_nid = " + namInfo.mob_term_nid);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.newSpcCode = " + byteArrToHexString(namInfo.newSpcCode));
                    }
                    result.obj = namInfo;
                }
            } catch (BufferUnderflowException e) {
                result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return result;
        }

        public static OEM_RIL_CDMA_Result byteArrToSidNidPairs(byte[] data) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToSidNidPairs: data = " + byteArrToHexString(data));
            }
            return data == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToSidNidPairs(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToSidNidPairs(ByteBuffer buf) {
            OEM_RIL_CDMA_Result result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader header = readHookHeader(buf);
                if (header.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    result.errno = header.error;
                } else {
                    OEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs pairs = new OEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs();
                    pairs.sidNid = new OEM_RIL_CDMA_SID_NID_NAM_Pair[20];
                    for (int i = 0; i < 20; i++) {
                        pairs.sidNid[i] = new OEM_RIL_CDMA_SID_NID_NAM_Pair();
                        pairs.sidNid[i].sid = buf.getInt();
                        pairs.sidNid[i].nid = buf.getInt();
                        if (OemCdmaTelephonyManager.DEBUG) {
                            Log.d(OemCdmaTelephonyManager.TAG, "byteArrToSidNidPairs: pairs.sidNid[" + i + "].sid = " + pairs.sidNid[i].sid);
                        }
                        if (OemCdmaTelephonyManager.DEBUG) {
                            Log.d(OemCdmaTelephonyManager.TAG, "byteArrToSidNidPairs: pairs.sidNid[" + i + "].nid = " + pairs.sidNid[i].nid);
                        }
                    }
                    result.obj = pairs;
                }
            } catch (BufferUnderflowException e) {
                result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return result;
        }

        public static OEM_RIL_CDMA_Result byteArrToNamPrlVersion(byte[] data) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamPrlVersion: data = " + byteArrToHexString(data));
            }
            return data == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToNamPrlVersion(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToNamPrlVersion(ByteBuffer buf) {
            OEM_RIL_CDMA_Result result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader header = readHookHeader(buf);
                if (header.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    result.errno = header.error;
                } else {
                    OEM_RIL_CDMA_NAM_PrlVersion ver = new OEM_RIL_CDMA_NAM_PrlVersion();
                    ver.prlVerison = buf.getInt();
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamPrlVersion: prlVerison = " + ver.prlVerison);
                    }
                    result.obj = ver;
                }
            } catch (BufferUnderflowException e) {
                result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return result;
        }

        public static OEM_RIL_CDMA_Result byteArrToRdeData(byte[] data) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToRdeData: data = " + byteArrToHexString(data));
            }
            return data == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToRdeData(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToRdeData(ByteBuffer buf) {
            OEM_RIL_CDMA_Result result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader header = readHookHeader(buf);
                if (header.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    result.errno = header.error;
                }
                result.obj = OEM_RIL_RDE_Data.deserialize(buf);
            } catch (BufferUnderflowException e) {
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.e(OemCdmaTelephonyManager.TAG, "byteArrToRdeData: buffer underflow");
                }
                result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return result;
        }

        public static OEM_RIL_CDMA_Result byteArrToBC(byte[] data) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToBC: data = " + byteArrToString(data));
            }
            return data == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToBC(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToBC(ByteBuffer buf) {
            OEM_RIL_CDMA_Result result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader header = readHookHeader(buf);
                if (header.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    result.errno = header.error;
                } else {
                    OEM_RIL_CDMA_BC bc = new OEM_RIL_CDMA_BC();
                    bc.status = buf.getInt();
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToBC: " + bc.status);
                    }
                    result.obj = bc;
                }
            } catch (BufferUnderflowException e) {
                result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return result;
        }

        public static OEM_RIL_CDMA_Result byteArrToActiveProfile(byte[] data) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToActiveProfile: data = " + byteArrToString(data));
            }
            return data == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToActiveProfile(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToActiveProfile(ByteBuffer buf) {
            OEM_RIL_CDMA_Result result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader header = readHookHeader(buf);
                if (header.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    result.errno = header.error;
                } else {
                    OEM_RIL_CDMA_ACTIVE_PROF active_prof = new OEM_RIL_CDMA_ACTIVE_PROF();
                    active_prof.profile = buf.getInt();
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToActiveProfile: " + active_prof.profile);
                    }
                    result.obj = active_prof;
                }
            } catch (BufferUnderflowException e) {
                result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return result;
        }

        public static OEM_RIL_CDMA_Result byteArrToGetSetDdtmDefaultPreferenceResp(byte[] data) {
            return data == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToGetSetDdtmDefaultPreferenceResp(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToGetSetDdtmDefaultPreferenceResp(ByteBuffer buf) {
            OEM_RIL_CDMA_Result result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader header = readHookHeader(buf);
                if (header.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    result.errno = header.error;
                } else {
                    result.obj = new Integer(buf.get() & OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT);
                }
            } catch (BufferUnderflowException e) {
                result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return result;
        }

        public static byte[] writeHookHeader(int msgId) {
            return writeHookHeader(msgId, 0, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS);
        }

        public static byte[] writeHookHeader(int msgId, int len, OEM_RIL_CDMA_Errno err) {
            ByteBuffer buf = ByteBuffer.allocate(18);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(buf, msgId, len, err);
            return buf.array();
        }

        public static void writeHookHeader(ByteBuffer buf, OEM_RIL_CDMA_HookHeader header) {
            writeHookHeader(buf, header.msgId, header.msgLength, header.error, header.spcLockCode);
        }

        public static void writeHookHeader(ByteBuffer buf, int msgId, int len, OEM_RIL_CDMA_Errno err) {
            writeHookHeader(buf, msgId, len, err, OemCdmaTelephonyManager.sDefaultSpcCode);
        }

        public static void writeHookHeader(ByteBuffer buf, int msgId, int len, OEM_RIL_CDMA_Errno err, String spcLockCode) {
            byte[] arrSpcLockCode = new byte[6];
            for (int i = 0; i < arrSpcLockCode.length; i++) {
                arrSpcLockCode[i] = (byte) spcLockCode.charAt(i);
            }
            writeHookHeader(buf, msgId, len, err, arrSpcLockCode);
        }

        public static void writeHookHeader(ByteBuffer buf, int msgId, int len, OEM_RIL_CDMA_Errno err, byte[] spcLockCode) {
            buf.putInt(msgId);
            buf.putInt(len);
            buf.putInt(err.toInt());
            for (int i = 0; i < 6; i++) {
                buf.put(spcLockCode[i]);
            }
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "writeHookHeader: msgId = " + msgId);
            }
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "writeHookHeader: msgLength = " + len);
            }
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "writeHookHeader: error = " + err);
            }
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "writeHookHeader: spcLockCode = " + byteArrToHexString(spcLockCode));
            }
        }

        public static final OEM_RIL_CDMA_HookHeader readHookHeader(byte[] bytes) {
            if (bytes == null) {
                return null;
            }
            return readHookHeader(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static final OEM_RIL_CDMA_HookHeader readHookHeader(ByteBuffer buf) {
            OEM_RIL_CDMA_HookHeader header = new OEM_RIL_CDMA_HookHeader();
            try {
                header.msgId = buf.getInt();
                header.msgLength = buf.getInt();
                header.error = OEM_RIL_CDMA_Errno.fromInt(buf.getInt());
                header.spcLockCode = new byte[6];
                for (int i = 0; i < header.spcLockCode.length; i++) {
                    header.spcLockCode[i] = buf.get();
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "readHookHeader: msgId = " + header.msgId);
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "readHookHeader: msgLength = " + header.msgLength);
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "readHookHeader: error = " + header.error.toString());
                }
                if (!OemCdmaTelephonyManager.DEBUG) {
                    return header;
                }
                Log.d(OemCdmaTelephonyManager.TAG, "readHookHeader: spcLockCode = " + byteArrToHexString(header.spcLockCode));
                return header;
            } catch (BufferUnderflowException e) {
                return null;
            }
        }
    }

    public static String byteArrayToHexString(byte[] b) {
        StringBuffer sb = new StringBuffer(b.length * 2);
        for (byte b2 : b) {
            int v = b2 & OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
            if (v < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString().toUpperCase();
    }

    public static String byteToHexString(byte b) {
        int v = b & OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
        return v < 16 ? "0" + Integer.toHexString(v) : Integer.toHexString(v);
    }
}
