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

public class OemCdmaTelephonyManager {
    private static final int CDMA_START = 33554432;
    private static boolean DEBUG = false;
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
    private static OemCdmaTelephonyManager mInstance = null;
    public static final String sDefaultSpcCode = "000000";
    private Message mCurrentMessage;
    private Watchdog mDog = new Watchdog(this.mMsgHandler);
    private Handler mMsgHandler = new Handler() {
        private void nextRequest() {
            OemCdmaTelephonyManager.this.mState = TelephonyMgrState.STATE_IDLE;
            OemCdmaTelephonyManager.this.mCurrentMessage = null;
            if (OemCdmaTelephonyManager.this.mRequestList.size() != 0) {
                HookRequest hookRequest = (HookRequest) OemCdmaTelephonyManager.this.mRequestList.removeFirst();
                OemCdmaTelephonyManager.this.invokeOemRilRequestRaw(hookRequest.data, hookRequest.msg, hookRequest.msgH);
            }
        }

        public void handleMessage(Message message) {
            if (message.what != Watchdog.MSG_TIMEOUT) {
                Object obj = 1;
                if (message != OemCdmaTelephonyManager.this.mCurrentMessage) {
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "unexpected message received: " + message.what);
                    }
                    obj = null;
                }
                if (obj != null) {
                    OemCdmaTelephonyManager.this.mDog.sleep();
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "calling user handler for msg=" + message.what);
                }
                if (!(obj == null || OemCdmaTelephonyManager.this.mUserHandler == null)) {
                    OemCdmaTelephonyManager.this.mUserHandler.obtainMessage(message.what, message.obj).sendToTarget();
                }
                if (obj != null) {
                    nextRequest();
                }
            } else if (((Message) message.obj) == OemCdmaTelephonyManager.this.mCurrentMessage) {
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "message timed out: " + (OemCdmaTelephonyManager.this.mCurrentMessage != null ? Integer.valueOf(OemCdmaTelephonyManager.this.mCurrentMessage.what) : "null"));
                }
                nextRequest();
            } else if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "Ignore timeout message, do nothing.");
            }
        }
    };
    private Phone mPhone = PhoneFactory.getDefaultPhone();
    private LinkedList<HookRequest> mPrlRequestList;
    private LinkedList<HookRequest> mRequestList = new LinkedList();
    private TelephonyMgrState mState = TelephonyMgrState.STATE_IDLE;
    private Handler mUserHandler;

    private static class HookRequest {
        public byte[] data;
        public Message msg;
        public Handler msgH;

        public HookRequest(byte[] bArr, Message message, Handler handler) {
            this.data = bArr;
            this.msg = message;
            this.msgH = handler;
        }
    }

    public static class OEM_RIL_CDMA_ACTIVE_PROF {
        public static final int SIZE = 4;
        public int profile;
    }

    public static class OEM_RIL_CDMA_BC {
        public static final int SIZE = 4;
        public int status;
    }

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

        private OEM_RIL_CDMA_DataRate(int i) {
            this.id = i;
        }

        public static OEM_RIL_CDMA_DataRate fromInt(int i) {
            for (OEM_RIL_CDMA_DataRate oEM_RIL_CDMA_DataRate : values()) {
                if (oEM_RIL_CDMA_DataRate.id == i) {
                    return oEM_RIL_CDMA_DataRate;
                }
            }
            return INVALID_DATA;
        }

        public int toInt() {
            return this.id;
        }
    }

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

    public enum OEM_RIL_CDMA_Errno {
        OEM_RIL_CDMA_SUCCESS(0),
        OEM_RIL_CDMA_RADIO_NOT_AVAILABLE(1),
        OEM_RIL_CDMA_NAM_READ_WRITE_FAILURE(2),
        OEM_RIL_CDMA_NAM_PASSWORD_INCORRECT(3),
        OEM_RIL_CDMA_NAM_ACCESS_COUNTER_EXCEEDED(4),
        OEM_RIL_CDMA_GENERIC_FAILURE(5);
        
        private final int id;

        private OEM_RIL_CDMA_Errno(int i) {
            this.id = i;
        }

        public static OEM_RIL_CDMA_Errno fromInt(int i) {
            for (OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno : values()) {
                if (oEM_RIL_CDMA_Errno.id == i) {
                    return oEM_RIL_CDMA_Errno;
                }
            }
            return OEM_RIL_CDMA_GENERIC_FAILURE;
        }

        public int toInt() {
            return this.id;
        }
    }

    public static class OEM_RIL_CDMA_HookHeader {
        public static final int OEM_RIL_CDMA_SPC_LOCK_CODE_LENGTH = 6;
        public static final int SIZE = 18;
        public OEM_RIL_CDMA_Errno error;
        public int msgId;
        public int msgLength;
        public byte[] spcLockCode;
    }

    public enum OEM_RIL_CDMA_HybridModeState {
        OEM_RIL_CDMA_HYBRID_MODE_OFF(0),
        OEM_RIL_CDMA_HYBRID_MODE_ON(1),
        INVALID_DATA(65535);
        
        public static final int SIZE = 4;
        private final int id;

        private OEM_RIL_CDMA_HybridModeState(int i) {
            this.id = i;
        }

        public static OEM_RIL_CDMA_HybridModeState fromInt(int i) {
            for (OEM_RIL_CDMA_HybridModeState oEM_RIL_CDMA_HybridModeState : values()) {
                if (oEM_RIL_CDMA_HybridModeState.id == i) {
                    return oEM_RIL_CDMA_HybridModeState;
                }
            }
            return INVALID_DATA;
        }

        public int toInt() {
            return this.id;
        }
    }

    public enum OEM_RIL_CDMA_MobilePRev {
        OEM_RIL_CDMA_P_REV_1(1),
        OEM_RIL_CDMA_P_REV_3(3),
        OEM_RIL_CDMA_P_REV_4(4),
        OEM_RIL_CDMA_P_REV_6(6),
        OEM_RIL_CDMA_P_REV_9(9),
        INVALID_DATA(65535);
        
        public static final int SIZE = 4;
        private final int id;

        private OEM_RIL_CDMA_MobilePRev(int i) {
            this.id = i;
        }

        public static OEM_RIL_CDMA_MobilePRev fromInt(int i) {
            for (OEM_RIL_CDMA_MobilePRev oEM_RIL_CDMA_MobilePRev : values()) {
                if (oEM_RIL_CDMA_MobilePRev.id == i) {
                    return oEM_RIL_CDMA_MobilePRev;
                }
            }
            return INVALID_DATA;
        }

        public int toInt() {
            return this.id;
        }
    }

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

    public static class OEM_RIL_CDMA_NAM_PrlVersion {
        public static final int SIZE = 4;
        public int prlVerison;
    }

    public static class OEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs {
        public static final int OEM_RIL_CDMA_NUMBER_NID_SID_PAIRS = 20;
        public OEM_RIL_CDMA_SID_NID_NAM_Pair[] sidNid;

        public int SIZE() {
            return (this.sidNid.length * 8) + 4;
        }
    }

    public static class OEM_RIL_CDMA_RESET_TO_FACTORY {
        public static final byte RESET_CLEAR = (byte) 2;
        public static final byte RESET_DEFAULT = (byte) -1;
        public static final byte RESET_RTN = (byte) 1;
    }

    public static class OEM_RIL_CDMA_Result {
        public OEM_RIL_CDMA_Errno errno;
        public Object obj;

        OEM_RIL_CDMA_Result() {
            this.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
            this.obj = null;
        }

        OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno) {
            this.errno = oEM_RIL_CDMA_Errno;
            this.obj = null;
        }
    }

    public enum OEM_RIL_CDMA_RevOption {
        OEM_RIL_CDMA_EVDO_REV_A(0),
        OEM_RIL_CDMA_EVDO_REV_0(1),
        INVALID_DATA(65535);
        
        public static final int SIZE = 4;
        private final int id;

        private OEM_RIL_CDMA_RevOption(int i) {
            this.id = i;
        }

        public static OEM_RIL_CDMA_RevOption fromInt(int i) {
            for (OEM_RIL_CDMA_RevOption oEM_RIL_CDMA_RevOption : values()) {
                if (oEM_RIL_CDMA_RevOption.id == i) {
                    return oEM_RIL_CDMA_RevOption;
                }
            }
            return INVALID_DATA;
        }

        public int toInt() {
            return this.id;
        }
    }

    public static class OEM_RIL_CDMA_SID_NID_NAM_Pair {
        public static final int SIZE = 8;
        public int nid;
        public int sid;
    }

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

        private OEM_RIL_CDMA_ServiceOption(int i) {
            this.id = i;
        }

        public static OEM_RIL_CDMA_ServiceOption fromInt(int i) {
            for (OEM_RIL_CDMA_ServiceOption oEM_RIL_CDMA_ServiceOption : values()) {
                if (oEM_RIL_CDMA_ServiceOption.id == i) {
                    return oEM_RIL_CDMA_ServiceOption;
                }
            }
            return INVALID_DATA;
        }

        public int toInt() {
            return this.id;
        }
    }

    public static class OEM_RIL_CDMA_SubsidyPassword {
        public static final int OEM_RIL_CDMA_SPC_LOCK_CODE_LENGTH = 6;
        public static final int SIZE = 6;
        public byte[] password;
    }

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
        public Serializable dataObj = null;
        public int elementID = 0;
        public int length = 0;
        public int offset = 0;
        public int recordNum = 0;

        public enum DDTMDefaultPreferenceSettings {
            DDTMDefaultPreferenceOFF(0),
            DDTMDefaultPreferenceON(1),
            DDTMDefaultPreferenceAUTO(2);
            
            private final int id;

            private DDTMDefaultPreferenceSettings(int i) {
                this.id = i;
            }

            public static DDTMDefaultPreferenceSettings fromInt(int i) {
                for (DDTMDefaultPreferenceSettings dDTMDefaultPreferenceSettings : values()) {
                    if (dDTMDefaultPreferenceSettings.id == i) {
                        return dDTMDefaultPreferenceSettings;
                    }
                }
                return DDTMDefaultPreferenceOFF;
            }

            public int toInt() {
                return this.id;
            }
        }

        public enum EmergencyAddress {
            EMERGENCY_NUMBER_1(99),
            EMERGENCY_NUMBER_2(100),
            EMERGENCY_NUMBER_3(101),
            INVALID_DATA(65535);
            
            private int mId;

            private EmergencyAddress(int i) {
                this.mId = i;
            }

            public static EmergencyAddress fromInt(int i) {
                for (EmergencyAddress emergencyAddress : values()) {
                    if (emergencyAddress.mId == i) {
                        return emergencyAddress;
                    }
                }
                return INVALID_DATA;
            }

            public int id() {
                return this.mId;
            }
        }

        private interface Serializable {
            void serialize(ByteBuffer byteBuffer);

            int size();
        }

        public static class rde_dial_type implements Serializable {
            public static final int NV_MAX_DIAL_DIGITS = 32;
            public static final int NV_MAX_LTRS = 12;
            public byte address = (byte) 0;
            private byte[] digits = new byte[32];
            private byte[] letters;
            private byte num_digits = (byte) 0;
            public byte status = (byte) 0;

            public rde_dial_type() {
                int i;
                for (i = 0; i < this.digits.length; i++) {
                    this.digits[i] = (byte) 0;
                }
                this.letters = new byte[12];
                for (i = 0; i < this.letters.length; i++) {
                    this.letters[i] = (byte) 0;
                }
            }

            public static rde_dial_type deserialize(ByteBuffer byteBuffer) {
                int i = 0;
                rde_dial_type rde_dial_type = new rde_dial_type();
                rde_dial_type.address = byteBuffer.get();
                rde_dial_type.status = byteBuffer.get();
                rde_dial_type.num_digits = byteBuffer.get();
                if (rde_dial_type.num_digits > (byte) 32) {
                    rde_dial_type.num_digits = (byte) 32;
                }
                for (int i2 = 0; i2 < rde_dial_type.digits.length; i2++) {
                    rde_dial_type.digits[i2] = byteBuffer.get();
                }
                while (i < rde_dial_type.letters.length) {
                    rde_dial_type.letters[i] = byteBuffer.get();
                    i++;
                }
                return rde_dial_type;
            }

            public String getNumber() {
                StringBuilder stringBuilder = new StringBuilder();
                for (byte b = (byte) 0; b < this.num_digits; b++) {
                    stringBuilder.append((char) this.digits[b]);
                }
                return stringBuilder.toString();
            }

            public void serialize(ByteBuffer byteBuffer) {
                byteBuffer.put(this.address);
                byteBuffer.put(this.status);
                byteBuffer.put(this.num_digits);
                byteBuffer.put(this.digits);
                byteBuffer.put(this.letters);
            }

            public boolean setNumber(String str) {
                this.num_digits = (byte) 0;
                for (int i = 0; i < this.digits.length; i++) {
                    if (i < str.length()) {
                        this.digits[i] = (byte) str.charAt(i);
                    } else {
                        this.digits[i] = (byte) 0;
                    }
                }
                this.num_digits = (byte) (str.length() < this.digits.length ? str.length() : this.digits.length);
                return true;
            }

            public int size() {
                return ((this.digits.length + 3) + this.letters.length) * 1;
            }

            public String toString() {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("address=" + this.address + "\n");
                stringBuilder.append("status=" + this.status + "\n");
                stringBuilder.append("num_digits=" + this.num_digits + "\n");
                stringBuilder.append("digits=" + getNumber());
                return stringBuilder.toString();
            }
        }

        public static class rde_gen_profile_type implements Serializable {
            public static final int NUM_BYTES_UINT32 = 4;
            public static final int NV_MAX_NAI_LENGTH = 72;
            public byte[] home_addr;
            public byte index = (byte) 0;
            public byte[] mn_aaa_spi;
            public byte mn_aaa_spi_set = (byte) 0;
            public byte[] mn_ha_spi;
            public byte mn_ha_spi_set = (byte) 0;
            public byte[] nai = new byte[72];
            public byte nai_length = (byte) 0;
            public byte[] primary_ha_addr;
            public byte rev_tun_pref = (byte) 0;
            public byte[] secondary_ha_addr;

            public rde_gen_profile_type() {
                int i;
                for (i = 0; i < this.nai.length; i++) {
                    this.nai[i] = (byte) 0;
                }
                this.mn_ha_spi = new byte[4];
                for (i = 0; i < this.mn_ha_spi.length; i++) {
                    this.mn_ha_spi[i] = (byte) 0;
                }
                this.mn_aaa_spi = new byte[4];
                for (i = 0; i < this.mn_aaa_spi.length; i++) {
                    this.mn_aaa_spi[i] = (byte) 0;
                }
                this.home_addr = new byte[4];
                for (i = 0; i < this.home_addr.length; i++) {
                    this.home_addr[i] = (byte) 0;
                }
                this.primary_ha_addr = new byte[4];
                for (i = 0; i < this.primary_ha_addr.length; i++) {
                    this.primary_ha_addr[i] = (byte) 0;
                }
                this.secondary_ha_addr = new byte[4];
                for (i = 0; i < this.secondary_ha_addr.length; i++) {
                    this.secondary_ha_addr[i] = (byte) 0;
                }
            }

            public static rde_gen_profile_type deserialize(ByteBuffer byteBuffer) {
                int i;
                int i2 = 0;
                rde_gen_profile_type rde_gen_profile_type = new rde_gen_profile_type();
                rde_gen_profile_type.index = byteBuffer.get();
                rde_gen_profile_type.nai_length = byteBuffer.get();
                for (i = 0; i < rde_gen_profile_type.nai.length; i++) {
                    rde_gen_profile_type.nai[i] = byteBuffer.get();
                }
                rde_gen_profile_type.mn_ha_spi_set = byteBuffer.get();
                for (i = 0; i < rde_gen_profile_type.mn_ha_spi.length; i++) {
                    rde_gen_profile_type.mn_ha_spi[i] = byteBuffer.get();
                }
                rde_gen_profile_type.mn_aaa_spi_set = byteBuffer.get();
                for (i = 0; i < rde_gen_profile_type.mn_aaa_spi.length; i++) {
                    rde_gen_profile_type.mn_aaa_spi[i] = byteBuffer.get();
                }
                rde_gen_profile_type.rev_tun_pref = byteBuffer.get();
                for (i = 0; i < rde_gen_profile_type.home_addr.length; i++) {
                    rde_gen_profile_type.home_addr[i] = byteBuffer.get();
                }
                for (i = 0; i < rde_gen_profile_type.primary_ha_addr.length; i++) {
                    rde_gen_profile_type.primary_ha_addr[i] = byteBuffer.get();
                }
                while (i2 < rde_gen_profile_type.secondary_ha_addr.length) {
                    rde_gen_profile_type.secondary_ha_addr[i2] = byteBuffer.get();
                    i2++;
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.v(OemCdmaTelephonyManager.TAG, "rde_gen_profile_type deserialize() index=" + rde_gen_profile_type.index + " nai=" + rde_gen_profile_type.nai + " mn_ha_spi_set=" + rde_gen_profile_type.mn_ha_spi_set + " nam=" + rde_gen_profile_type.mn_ha_spi + "mn_aaa_spi_set" + rde_gen_profile_type.mn_aaa_spi_set + "rev_tun_pref" + rde_gen_profile_type.rev_tun_pref);
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.v(OemCdmaTelephonyManager.TAG, "rde_gen_profile_type deserialize() home_addr=" + rde_gen_profile_type.home_addr + " primary_ha_addr=" + rde_gen_profile_type.primary_ha_addr + " secondary_ha_addr=" + rde_gen_profile_type.secondary_ha_addr);
                }
                return rde_gen_profile_type;
            }

            public void serialize(ByteBuffer byteBuffer) {
                int i;
                int i2 = 0;
                byteBuffer.put(this.index);
                byteBuffer.put(this.nai_length);
                for (byte put : this.nai) {
                    byteBuffer.put(put);
                }
                for (i = 0; i < 72 - this.nai.length; i++) {
                    byteBuffer.put((byte) 0);
                }
                byteBuffer.put(this.mn_ha_spi_set);
                for (byte put2 : this.mn_ha_spi) {
                    byteBuffer.put(put2);
                }
                for (i = 0; i < 4 - this.mn_ha_spi.length; i++) {
                    byteBuffer.put((byte) 0);
                }
                byteBuffer.put(this.mn_aaa_spi_set);
                for (byte put22 : this.mn_aaa_spi) {
                    byteBuffer.put(put22);
                }
                for (i = 0; i < 4 - this.mn_aaa_spi.length; i++) {
                    byteBuffer.put((byte) 0);
                }
                byteBuffer.put(this.rev_tun_pref);
                for (byte put222 : this.home_addr) {
                    byteBuffer.put(put222);
                }
                for (byte put2222 : this.primary_ha_addr) {
                    byteBuffer.put(put2222);
                }
                while (i2 < this.secondary_ha_addr.length) {
                    byteBuffer.put(this.secondary_ha_addr[i2]);
                    i2++;
                }
            }

            public int size() {
                return 97;
            }

            public String toString() {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("index=0x" + OemCdmaTelephonyManager.byteToHexString(this.index));
                stringBuffer.append(", nai_length=0x" + OemCdmaTelephonyManager.byteToHexString(this.nai_length));
                stringBuffer.append(", nai=" + OemCdmaTelephonyManager.byteArrayToHexString(this.nai));
                stringBuffer.append(", mn_ha_spi_set=" + OemCdmaTelephonyManager.byteToHexString(this.mn_ha_spi_set));
                stringBuffer.append(", mn_ha_spi=" + OemCdmaTelephonyManager.byteArrayToHexString(this.mn_ha_spi));
                stringBuffer.append(", mn_aaa_spi_set=" + OemCdmaTelephonyManager.byteToHexString(this.mn_aaa_spi_set));
                stringBuffer.append(", mn_aaa_spi=" + OemCdmaTelephonyManager.byteArrayToHexString(this.mn_aaa_spi));
                stringBuffer.append(", rev_tun_pref=" + OemCdmaTelephonyManager.byteToHexString(this.rev_tun_pref));
                stringBuffer.append(", home_addr=" + OemCdmaTelephonyManager.byteArrayToHexString(this.home_addr));
                stringBuffer.append(", primary_ha_addr=" + OemCdmaTelephonyManager.byteArrayToHexString(this.primary_ha_addr));
                stringBuffer.append(", secondary_ha_addr=" + OemCdmaTelephonyManager.byteArrayToHexString(this.secondary_ha_addr));
                return stringBuffer.toString();
            }
        }

        public static class rde_imsi_addr_num_type implements Serializable {
            public byte nam = (byte) 0;
            public byte num = (byte) -1;

            public static rde_imsi_addr_num_type deserialize(ByteBuffer byteBuffer) {
                rde_imsi_addr_num_type rde_imsi_addr_num_type = new rde_imsi_addr_num_type();
                rde_imsi_addr_num_type.nam = byteBuffer.get();
                rde_imsi_addr_num_type.num = byteBuffer.get();
                if (rde_imsi_addr_num_type.num == (byte) -1) {
                    rde_imsi_addr_num_type.num = (byte) 0;
                }
                return rde_imsi_addr_num_type;
            }

            public void serialize(ByteBuffer byteBuffer) {
                byteBuffer.put(this.nam);
                if (this.num == (byte) 0) {
                    this.num = (byte) -1;
                }
                byteBuffer.put(this.num);
            }

            public int size() {
                return 2;
            }
        }

        public static class rde_lte_bc_config_type implements Serializable {
            public static final int NUM_BYTES_UINT64 = 8;
            public byte[] lte_bc_config;
            public byte[] lte_bc_config_ext;

            public rde_lte_bc_config_type() {
                this(0, 0);
            }

            public rde_lte_bc_config_type(long j, long j2) {
                this.lte_bc_config = longToLteBcConfig(j);
                this.lte_bc_config_ext = longToLteBcConfig(j2);
            }

            private static String byte2bits(byte b) {
                String toBinaryString = Integer.toBinaryString(b | 256);
                int length = toBinaryString.length();
                return toBinaryString.substring(length - 8, length);
            }

            public static rde_lte_bc_config_type deserialize(ByteBuffer byteBuffer) {
                int i = 0;
                rde_lte_bc_config_type rde_lte_bc_config_type = new rde_lte_bc_config_type();
                for (int i2 = 0; i2 < rde_lte_bc_config_type.lte_bc_config.length; i2++) {
                    rde_lte_bc_config_type.lte_bc_config[i2] = byteBuffer.get();
                }
                while (i < rde_lte_bc_config_type.lte_bc_config_ext.length) {
                    rde_lte_bc_config_type.lte_bc_config_ext[i] = byteBuffer.get();
                    i++;
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.v(OemCdmaTelephonyManager.TAG, "rde_lte_bc_config_type deserialize() lte_bc_config = " + rde_lte_bc_config_type.lte_bc_config + " lte_bc_config_ext = " + rde_lte_bc_config_type.lte_bc_config_ext);
                }
                return rde_lte_bc_config_type;
            }

            public static long lteBcConfigToLong(byte[] bArr) {
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "enter lteBcConfigToLong()");
                    Log.v(OemCdmaTelephonyManager.TAG, "lteBcConfigToLong() print  in start");
                    for (byte byte2bits : bArr) {
                        Log.v(OemCdmaTelephonyManager.TAG, "lteBcConfigToLong() print  in= " + byte2bits(byte2bits));
                    }
                    Log.v(OemCdmaTelephonyManager.TAG, "lteBcConfigToLong() print  in end");
                }
                long j = ((((long) bArr[0]) << 0) & 255) | ((((((((((long) bArr[7]) << 56) & -72057594037927936L) | ((((long) bArr[6]) << 48) & 71776119061217280L)) | ((((long) bArr[5]) << 40) & 280375465082880L)) | ((((long) bArr[4]) << 32) & 1095216660480L)) | ((((long) bArr[3]) << 24) & 4278190080L)) | ((((long) bArr[2]) << 16) & 16711680)) | ((((long) bArr[1]) << 8) & 65280));
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "lteBcConfigToLong, out =" + j);
                    Log.d(OemCdmaTelephonyManager.TAG, "lteBcConfigToLong out to BinaryString =" + Long.toBinaryString(j));
                }
                return j;
            }

            public byte[] longToLteBcConfig(long j) {
                int i = 0;
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.v(OemCdmaTelephonyManager.TAG, "enter longToLteBcConfig(), in =" + j);
                    Log.v(OemCdmaTelephonyManager.TAG, " longToLteBcConfig in to BinaryString =" + Long.toBinaryString(j));
                }
                byte[] bArr = new byte[]{(byte) (((int) (j >>> 56)) & 255), (byte) (((int) (j >>> 48)) & 255), (byte) (((int) (j >>> 40)) & 255), (byte) (((int) (j >>> 32)) & 255), (byte) (((int) (j >>> 24)) & 255), (byte) (((int) (j >>> 16)) & 255), (byte) (((int) (j >>> 8)) & 255), (byte) (((int) (j >>> 0)) & 255)};
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.v(OemCdmaTelephonyManager.TAG, "longToLteBcConfig() print  out start");
                    int length = bArr.length;
                    while (i < length) {
                        Log.v(OemCdmaTelephonyManager.TAG, "longToLteBcConfig() print  out= " + byte2bits(bArr[i]));
                        i++;
                    }
                    Log.v(OemCdmaTelephonyManager.TAG, "longToLteBcConfig() print  out end");
                }
                return bArr;
            }

            public void serialize(ByteBuffer byteBuffer) {
                int i;
                for (byte put : this.lte_bc_config) {
                    byteBuffer.put(put);
                }
                for (i = 0; i < 8 - this.lte_bc_config.length; i++) {
                    byteBuffer.put((byte) 0);
                }
                for (byte put2 : this.lte_bc_config_ext) {
                    byteBuffer.put(put2);
                }
                for (i = 0; i < 8 - this.lte_bc_config_ext.length; i++) {
                    byteBuffer.put((byte) 0);
                }
            }

            public int size() {
                return 16;
            }

            public String toString() {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append(" lte_bc_config=" + OemCdmaTelephonyManager.byteArrayToHexString(this.lte_bc_config));
                stringBuffer.append(" lte_bc_config_ext=" + OemCdmaTelephonyManager.byteArrayToHexString(this.lte_bc_config_ext));
                return stringBuffer.toString();
            }
        }

        public static class rde_mob_term_type implements Serializable {
            public static final int OEM_RIL_CDMA_MAX_MINS = 2;
            public byte[] enabled = new byte[2];
            public byte nam = (byte) 0;

            public rde_mob_term_type() {
                for (int i = 0; i < 2; i++) {
                    this.enabled[i] = (byte) 0;
                }
            }

            public static rde_mob_term_type deserialize(ByteBuffer byteBuffer) {
                rde_mob_term_type rde_mob_term_type = new rde_mob_term_type();
                rde_mob_term_type.nam = byteBuffer.get();
                for (int i = 0; i < 2; i++) {
                    rde_mob_term_type.enabled[i] = byteBuffer.get();
                }
                return rde_mob_term_type;
            }

            public void serialize(ByteBuffer byteBuffer) {
                byteBuffer.put(this.nam);
                for (int i = 0; i < 2; i++) {
                    byteBuffer.put(this.enabled[i]);
                }
            }

            public int size() {
                return 3;
            }
        }

        public static class rde_nam_accolc_type implements Serializable {
            public static final int OEM_RIL_CDMA_MAX_MINS = 2;
            public byte[] ACCOLCpClass = new byte[2];
            public byte nam = (byte) 0;

            public rde_nam_accolc_type() {
                for (int i = 0; i < 2; i++) {
                    this.ACCOLCpClass[i] = (byte) 0;
                }
            }

            public static rde_nam_accolc_type deserialize(ByteBuffer byteBuffer) {
                rde_nam_accolc_type rde_nam_accolc_type = new rde_nam_accolc_type();
                rde_nam_accolc_type.nam = byteBuffer.get();
                for (int i = 0; i < 2; i++) {
                    rde_nam_accolc_type.ACCOLCpClass[i] = byteBuffer.get();
                }
                return rde_nam_accolc_type;
            }

            public void serialize(ByteBuffer byteBuffer) {
                byteBuffer.put(this.nam);
                for (int i = 0; i < 2; i++) {
                    byteBuffer.put(this.ACCOLCpClass[i]);
                }
            }

            public int size() {
                return 3;
            }
        }

        public static class rde_nam_imsi_11_12_type implements Serializable {
            public byte imsi_11_12 = (byte) 0;
            public byte nam = (byte) 0;

            public static rde_nam_imsi_11_12_type deserialize(ByteBuffer byteBuffer) {
                rde_nam_imsi_11_12_type rde_nam_imsi_11_12_type = new rde_nam_imsi_11_12_type();
                rde_nam_imsi_11_12_type.nam = byteBuffer.get();
                rde_nam_imsi_11_12_type.imsi_11_12 = byteBuffer.get();
                return rde_nam_imsi_11_12_type;
            }

            public void serialize(ByteBuffer byteBuffer) {
                byteBuffer.put(this.nam);
                byteBuffer.put(this.imsi_11_12);
            }

            public int size() {
                return 2;
            }
        }

        public static class rde_nam_imsi_mcc_type implements Serializable {
            public short imsi_mcc = (short) 0;
            public byte nam = (byte) 0;

            public static rde_nam_imsi_mcc_type deserialize(ByteBuffer byteBuffer) {
                rde_nam_imsi_mcc_type rde_nam_imsi_mcc_type = new rde_nam_imsi_mcc_type();
                rde_nam_imsi_mcc_type.nam = byteBuffer.get();
                rde_nam_imsi_mcc_type.imsi_mcc = byteBuffer.getShort();
                return rde_nam_imsi_mcc_type;
            }

            public void serialize(ByteBuffer byteBuffer) {
                byteBuffer.put(this.nam);
                byteBuffer.putShort(this.imsi_mcc);
            }

            public int size() {
                return 3;
            }
        }

        public static class rde_nam_mdn_type implements Serializable {
            public static final int OEM_RIL_CDMA_MDN_LENGTH = 10;
            public byte[] mdn = new byte[10];
            public byte nam = (byte) 0;

            public rde_nam_mdn_type() {
                for (int i = 0; i < 10; i++) {
                    this.mdn[i] = (byte) 0;
                }
            }

            public static rde_nam_mdn_type deserialize(ByteBuffer byteBuffer) {
                rde_nam_mdn_type rde_nam_mdn_type = new rde_nam_mdn_type();
                rde_nam_mdn_type.nam = byteBuffer.get();
                for (int i = 0; i < 10; i++) {
                    rde_nam_mdn_type.mdn[i] = byteBuffer.get();
                }
                return rde_nam_mdn_type;
            }

            public void serialize(ByteBuffer byteBuffer) {
                byteBuffer.put(this.nam);
                for (int i = 0; i < 10; i++) {
                    byteBuffer.put(this.mdn[i]);
                }
            }

            public int size() {
                return 11;
            }
        }

        public static class rde_nam_min1_type implements Serializable {
            public static final int OEM_RIL_CDMA_MAX_MINS = 2;
            public int[] min1 = new int[2];
            public byte nam = (byte) 0;

            public rde_nam_min1_type() {
                for (int i = 0; i < 2; i++) {
                    this.min1[i] = 0;
                }
            }

            public static rde_nam_min1_type deserialize(ByteBuffer byteBuffer) {
                rde_nam_min1_type rde_nam_min1_type = new rde_nam_min1_type();
                rde_nam_min1_type.nam = byteBuffer.get();
                for (int i = 0; i < 2; i++) {
                    rde_nam_min1_type.min1[i] = byteBuffer.getInt();
                }
                return rde_nam_min1_type;
            }

            public void serialize(ByteBuffer byteBuffer) {
                byteBuffer.put(this.nam);
                for (int i = 0; i < 2; i++) {
                    byteBuffer.putInt(this.min1[i]);
                }
            }

            public int size() {
                return 9;
            }
        }

        public static class rde_nam_min2_type implements Serializable {
            public static final int OEM_RIL_CDMA_MAX_MINS = 2;
            public short[] min2 = new short[2];
            public byte nam = (byte) 0;

            public rde_nam_min2_type() {
                for (int i = 0; i < 2; i++) {
                    this.min2[i] = (short) 0;
                }
            }

            public static rde_nam_min2_type deserialize(ByteBuffer byteBuffer) {
                rde_nam_min2_type rde_nam_min2_type = new rde_nam_min2_type();
                rde_nam_min2_type.nam = byteBuffer.get();
                for (int i = 0; i < 2; i++) {
                    rde_nam_min2_type.min2[i] = byteBuffer.getShort();
                }
                return rde_nam_min2_type;
            }

            public void serialize(ByteBuffer byteBuffer) {
                byteBuffer.put(this.nam);
                for (int i = 0; i < 2; i++) {
                    byteBuffer.putShort(this.min2[i]);
                }
            }

            public int size() {
                return 5;
            }
        }

        public static class rde_obj_type implements Serializable {
            public byte[] data;

            public rde_obj_type() {
                this.data = null;
            }

            public rde_obj_type(byte b) {
                ByteBuffer allocate = ByteBuffer.allocate(1);
                allocate.order(ByteOrder.LITTLE_ENDIAN);
                allocate.put(b);
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "bype to bypeArray = " + b);
                }
                this.data = allocate.array();
            }

            public rde_obj_type(Integer num) {
                ByteBuffer allocate = ByteBuffer.allocate(4);
                allocate.order(ByteOrder.LITTLE_ENDIAN);
                allocate.putInt(num.intValue());
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "Integer to bypeArray = " + num);
                }
                this.data = allocate.array();
            }

            public rde_obj_type(String str) {
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "String to bypeArray = " + str);
                }
                byte[] bytes = str.getBytes();
                this.data = new byte[127];
                System.arraycopy(bytes, 0, this.data, 0, str.length());
                Arrays.fill(this.data, str.length(), 127, (byte) 0);
            }

            public rde_obj_type(short s) {
                ByteBuffer allocate = ByteBuffer.allocate(2);
                allocate.order(ByteOrder.LITTLE_ENDIAN);
                allocate.putInt(s);
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "short to bypeArray = " + s);
                }
                this.data = allocate.array();
            }

            public rde_obj_type(boolean z) {
                ByteBuffer allocate = ByteBuffer.allocate(4);
                allocate.order(ByteOrder.LITTLE_ENDIAN);
                if (z) {
                    allocate.putInt(new Integer(1).intValue());
                } else {
                    allocate.putInt(new Integer(0).intValue());
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "boolean to bypeArray = " + z);
                }
                this.data = allocate.array();
            }

            public static boolean rdeToBool(rde_obj_type rde_obj_type) {
                return (rde_obj_type.data[0] & 255) != 0;
            }

            public static int rdeToInteger(rde_obj_type rde_obj_type) {
                int i = 0;
                for (int length = rde_obj_type.data.length - 1; length >= 0; length--) {
                    i = (i * 256) + (rde_obj_type.data[length] & 255);
                }
                return i;
            }

            public static String rdeToString(rde_obj_type rde_obj_type) {
                return new String(rde_obj_type.data);
            }

            public void serialize(ByteBuffer byteBuffer) {
                byteBuffer.put(this.data);
            }

            public int size() {
                return this.data == null ? 0 : this.data.length;
            }

            public String toString() {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("data=0x" + OemCdmaTelephonyManager.byteArrayToHexString(this.data));
                return stringBuffer.toString();
            }
        }

        public static class rde_pref_voice_so_type implements Serializable {
            public byte evrc_capability_enabled = (byte) 0;
            public short home_orig_voice_so = (short) 0;
            public short home_page_voice_so = (short) 0;
            public byte nam = (byte) 0;
            public short roam_orig_voice_so = (short) 0;

            public static rde_pref_voice_so_type deserialize(ByteBuffer byteBuffer) {
                rde_pref_voice_so_type rde_pref_voice_so_type = new rde_pref_voice_so_type();
                rde_pref_voice_so_type.nam = byteBuffer.get();
                rde_pref_voice_so_type.evrc_capability_enabled = byteBuffer.get();
                rde_pref_voice_so_type.home_page_voice_so = byteBuffer.getShort();
                rde_pref_voice_so_type.home_orig_voice_so = byteBuffer.getShort();
                rde_pref_voice_so_type.roam_orig_voice_so = byteBuffer.getShort();
                return rde_pref_voice_so_type;
            }

            public void serialize(ByteBuffer byteBuffer) {
                byteBuffer.put(this.nam);
                byteBuffer.put(this.evrc_capability_enabled);
                byteBuffer.putShort(this.home_page_voice_so);
                byteBuffer.putShort(this.home_orig_voice_so);
                byteBuffer.putShort(this.roam_orig_voice_so);
            }

            public int size() {
                return 17;
            }
        }

        public static class rde_roaming_list_type implements Serializable {
            public static final int NV_SIZE_OF_RAM_ROAMING_LIST = 16390;
            public byte[] roaming_list;
            public int size = 0;

            public static rde_roaming_list_type deserialize(ByteBuffer byteBuffer) {
                rde_roaming_list_type rde_roaming_list_type = new rde_roaming_list_type();
                rde_roaming_list_type.size = byteBuffer.getInt();
                rde_roaming_list_type.roaming_list = new byte[rde_roaming_list_type.size];
                for (int i = 0; i < rde_roaming_list_type.size; i++) {
                    rde_roaming_list_type.roaming_list[i] = byteBuffer.get();
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.v(OemCdmaTelephonyManager.TAG, "rde_roaming_list_type deserialize()");
                }
                return rde_roaming_list_type;
            }

            public void serialize(ByteBuffer byteBuffer) {
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.v(OemCdmaTelephonyManager.TAG, "rde_roaming_list_type serialize()");
                }
                byteBuffer.putInt(this.size);
                byteBuffer.put(this.roaming_list);
            }

            public void setRoamingList(byte[] bArr) {
                this.roaming_list = bArr;
                this.size = this.roaming_list.length;
            }

            public int size() {
                return (this.roaming_list.length * 1) + 4;
            }
        }

        public static class rde_ss_profile_type implements Serializable {
            public static final int NV_MAX_MN_AAA_SHARED_SECRET_LEN = 16;
            public static final int NV_MAX_MN_HA_SHARED_SECRET_LEN = 16;
            public byte index = (byte) 0;
            public byte[] mn_aaa_shared_secret;
            public byte mn_aaa_shared_secret_length = (byte) 0;
            public byte[] mn_ha_shared_secret = new byte[16];
            public byte mn_ha_shared_secret_length = (byte) 0;

            public rde_ss_profile_type() {
                int i;
                for (i = 0; i < this.mn_ha_shared_secret.length; i++) {
                    this.mn_ha_shared_secret[i] = (byte) 0;
                }
                this.mn_aaa_shared_secret = new byte[16];
                for (i = 0; i < this.mn_aaa_shared_secret.length; i++) {
                    this.mn_aaa_shared_secret[i] = (byte) 0;
                }
            }

            public static rde_ss_profile_type deserialize(ByteBuffer byteBuffer) {
                int i = 0;
                rde_ss_profile_type rde_ss_profile_type = new rde_ss_profile_type();
                rde_ss_profile_type.index = byteBuffer.get();
                rde_ss_profile_type.mn_ha_shared_secret_length = byteBuffer.get();
                for (int i2 = 0; i2 < rde_ss_profile_type.mn_ha_shared_secret.length; i2++) {
                    rde_ss_profile_type.mn_ha_shared_secret[i2] = byteBuffer.get();
                }
                rde_ss_profile_type.mn_aaa_shared_secret_length = byteBuffer.get();
                while (i < rde_ss_profile_type.mn_aaa_shared_secret.length) {
                    rde_ss_profile_type.mn_aaa_shared_secret[i] = byteBuffer.get();
                    i++;
                }
                return rde_ss_profile_type;
            }

            public void serialize(ByteBuffer byteBuffer) {
                int i;
                byteBuffer.put(this.index);
                byteBuffer.put(this.mn_ha_shared_secret_length);
                for (byte put : this.mn_ha_shared_secret) {
                    byteBuffer.put(put);
                }
                for (i = 0; i < 16 - this.mn_ha_shared_secret.length; i++) {
                    byteBuffer.put((byte) 0);
                }
                byteBuffer.put(this.mn_aaa_shared_secret_length);
                for (byte put2 : this.mn_aaa_shared_secret) {
                    byteBuffer.put(put2);
                }
                for (i = 0; i < 16 - this.mn_aaa_shared_secret.length; i++) {
                    byteBuffer.put((byte) 0);
                }
            }

            public int size() {
                return 35;
            }

            public String toString() {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("index=0x" + OemCdmaTelephonyManager.byteToHexString(this.index));
                stringBuffer.append(", mn_ha_shared_secret_length=0x" + OemCdmaTelephonyManager.byteToHexString(this.mn_ha_shared_secret_length));
                stringBuffer.append(", mn_ha_shared_secret=" + OemCdmaTelephonyManager.byteArrayToHexString(this.mn_ha_shared_secret));
                stringBuffer.append(", mn_aaa_shared_secret_length=0x" + OemCdmaTelephonyManager.byteToHexString(this.mn_aaa_shared_secret_length));
                stringBuffer.append(", mn_aaa_shared_secret=" + OemCdmaTelephonyManager.byteArrayToHexString(this.mn_aaa_shared_secret));
                return stringBuffer.toString();
            }
        }

        public static OEM_RIL_RDE_Data deserialize(ByteBuffer byteBuffer) {
            OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
            oEM_RIL_RDE_Data.elementID = byteBuffer.getInt();
            oEM_RIL_RDE_Data.recordNum = byteBuffer.getInt();
            oEM_RIL_RDE_Data.offset = byteBuffer.getInt();
            oEM_RIL_RDE_Data.length = byteBuffer.getInt();
            rde_obj_type rde_obj_type = new rde_obj_type();
            rde_obj_type.data = new byte[oEM_RIL_RDE_Data.length];
            switch (oEM_RIL_RDE_Data.elementID) {
                case 2:
                    oEM_RIL_RDE_Data.dataObj = rde_nam_accolc_type.deserialize(byteBuffer);
                    return oEM_RIL_RDE_Data;
                case 6:
                    oEM_RIL_RDE_Data.dataObj = rde_dial_type.deserialize(byteBuffer);
                    return oEM_RIL_RDE_Data;
                case 7:
                    oEM_RIL_RDE_Data.dataObj = rde_nam_mdn_type.deserialize(byteBuffer);
                    return oEM_RIL_RDE_Data;
                case 9:
                    oEM_RIL_RDE_Data.dataObj = rde_gen_profile_type.deserialize(byteBuffer);
                    return oEM_RIL_RDE_Data;
                case 10:
                case 23:
                case RDE_NV_OTKSL_I /*27*/:
                case 36:
                case RDE_NV_EHRPD_ENABLED_I /*43*/:
                case RDE_NV_CDMA_SO73_ENABLED_I /*45*/:
                case 47:
                case RDE_NV_CDMA_SO68_ENABLED_I /*48*/:
                case RDE_NV_MOB_TERM_HOME_I /*50*/:
                case 51:
                case 52:
                case 55:
                case 56:
                case RDE_SHARP_NV_SPRINT_LTE_FORCE_ENABLE_I /*8024*/:
                case RDE_SHARP_NV_SPRINT_EHRPD_FORCE_ENABLE_I /*8025*/:
                case RDE_EFS_1XADVANCED_CAPABILITY_STATUS_I /*10015*/:
                case RDE_EFS_SO73_COP0_SUPPORTED_I /*10016*/:
                case RDE_NV_LTE_CAPABILITY_I /*10017*/:
                case RDE_MIP_ERROR_CODE_I /*10019*/:
                case RDE_NV_SLOTTED_MODE_I /*10020*/:
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "Deserialize int length: " + oEM_RIL_RDE_Data.length);
                    }
                    for (int i = 0; i < oEM_RIL_RDE_Data.length; i++) {
                        rde_obj_type.data[i] = byteBuffer.get();
                    }
                    oEM_RIL_RDE_Data.dataObj = rde_obj_type;
                    return oEM_RIL_RDE_Data;
                case 16:
                    oEM_RIL_RDE_Data.dataObj = rde_nam_imsi_11_12_type.deserialize(byteBuffer);
                    return oEM_RIL_RDE_Data;
                case 17:
                    oEM_RIL_RDE_Data.dataObj = rde_nam_imsi_mcc_type.deserialize(byteBuffer);
                    return oEM_RIL_RDE_Data;
                case 25:
                    oEM_RIL_RDE_Data.dataObj = rde_nam_min1_type.deserialize(byteBuffer);
                    return oEM_RIL_RDE_Data;
                case 26:
                    oEM_RIL_RDE_Data.dataObj = rde_nam_min2_type.deserialize(byteBuffer);
                    return oEM_RIL_RDE_Data;
                case 30:
                    oEM_RIL_RDE_Data.dataObj = rde_pref_voice_so_type.deserialize(byteBuffer);
                    return oEM_RIL_RDE_Data;
                case RDE_NV_DS_MIP_SS_USER_PROF_I /*46*/:
                    oEM_RIL_RDE_Data.dataObj = rde_ss_profile_type.deserialize(byteBuffer);
                    return oEM_RIL_RDE_Data;
                case 49:
                    oEM_RIL_RDE_Data.dataObj = rde_imsi_addr_num_type.deserialize(byteBuffer);
                    return oEM_RIL_RDE_Data;
                case 53:
                    oEM_RIL_RDE_Data.dataObj = rde_lte_bc_config_type.deserialize(byteBuffer);
                    return oEM_RIL_RDE_Data;
                case RDE_NV_ROAMING_LIST_683_I /*108*/:
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "Deserialize roaming list");
                    }
                    oEM_RIL_RDE_Data.dataObj = rde_roaming_list_type.deserialize(byteBuffer);
                    return oEM_RIL_RDE_Data;
                default:
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "deserialize elementID (" + oEM_RIL_RDE_Data.elementID + ")");
                    }
                    return oEM_RIL_RDE_Data;
            }
        }

        public int SIZE() {
            return (this.dataObj == null ? 1 : this.dataObj.size()) + 16;
        }

        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("elementID=" + this.elementID + "\n");
            stringBuilder.append("recordNum=" + this.recordNum + "\n");
            stringBuilder.append("offset=" + this.offset + "\n");
            stringBuilder.append("object=\n" + this.dataObj);
            return stringBuilder.toString();
        }
    }

    public static class OemCdmaDataConverter {
        public static byte[] ActiveProfileToByteArr(OEM_RIL_CDMA_ACTIVE_PROF oem_ril_cdma_active_prof, int i, String str) {
            ByteBuffer allocate = ByteBuffer.allocate(22);
            allocate.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(allocate, i, 4, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, str);
            allocate.putInt(oem_ril_cdma_active_prof.profile);
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "ActiveProfileToByteArr = " + oem_ril_cdma_active_prof.profile);
            }
            byte[] array = allocate.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "ActiveProfileToByteArr: data = " + byteArrToString(array));
            }
            return array;
        }

        public static byte[] BCToByteArr(OEM_RIL_CDMA_BC oem_ril_cdma_bc, int i, String str) {
            ByteBuffer allocate = ByteBuffer.allocate(22);
            allocate.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(allocate, i, 4, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, str);
            allocate.putInt(oem_ril_cdma_bc.status);
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "BCToByteArr = " + oem_ril_cdma_bc.status);
            }
            return allocate.array();
        }

        public static OEM_RIL_CDMA_Result byteArrToActiveProfile(ByteBuffer byteBuffer) {
            OEM_RIL_CDMA_Result oEM_RIL_CDMA_Result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader readHookHeader = readHookHeader(byteBuffer);
                if (readHookHeader.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    oEM_RIL_CDMA_Result.errno = readHookHeader.error;
                } else {
                    OEM_RIL_CDMA_ACTIVE_PROF oem_ril_cdma_active_prof = new OEM_RIL_CDMA_ACTIVE_PROF();
                    oem_ril_cdma_active_prof.profile = byteBuffer.getInt();
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToActiveProfile: " + oem_ril_cdma_active_prof.profile);
                    }
                    oEM_RIL_CDMA_Result.obj = oem_ril_cdma_active_prof;
                }
            } catch (BufferUnderflowException e) {
                oEM_RIL_CDMA_Result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return oEM_RIL_CDMA_Result;
        }

        public static OEM_RIL_CDMA_Result byteArrToActiveProfile(byte[] bArr) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToActiveProfile: data = " + byteArrToString(bArr));
            }
            return bArr == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToActiveProfile(ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToBC(ByteBuffer byteBuffer) {
            OEM_RIL_CDMA_Result oEM_RIL_CDMA_Result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader readHookHeader = readHookHeader(byteBuffer);
                if (readHookHeader.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    oEM_RIL_CDMA_Result.errno = readHookHeader.error;
                } else {
                    OEM_RIL_CDMA_BC oem_ril_cdma_bc = new OEM_RIL_CDMA_BC();
                    oem_ril_cdma_bc.status = byteBuffer.getInt();
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToBC: " + oem_ril_cdma_bc.status);
                    }
                    oEM_RIL_CDMA_Result.obj = oem_ril_cdma_bc;
                }
            } catch (BufferUnderflowException e) {
                oEM_RIL_CDMA_Result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return oEM_RIL_CDMA_Result;
        }

        public static OEM_RIL_CDMA_Result byteArrToBC(byte[] bArr) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToBC: data = " + byteArrToString(bArr));
            }
            return bArr == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToBC(ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToCpStatus(ByteBuffer byteBuffer) {
            int i = 0;
            OEM_RIL_CDMA_Result oEM_RIL_CDMA_Result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader readHookHeader = readHookHeader(byteBuffer);
                if (readHookHeader.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    oEM_RIL_CDMA_Result.errno = readHookHeader.error;
                } else {
                    int i2;
                    OEM_RIL_CDMA_CP_Status oEM_RIL_CDMA_CP_Status = new OEM_RIL_CDMA_CP_Status();
                    oEM_RIL_CDMA_CP_Status.fer = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.activePilotCount = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.neighborPilotCount = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.candPilotCount = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.bestActivePilot = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.bestActiveStrength = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.bestNeighborPilot = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.bestNeighborStrength = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.sid = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.nid = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.channel = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.serviceOption = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.droppedCallCounter = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.callCounter = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.lastCallIndicator = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.PRevInUse = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.band = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.lnaStatus = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.cpState = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.txPower = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.rssi = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.bid = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.activeSetPn = new int[6];
                    for (i2 = 0; i2 < 6; i2++) {
                        oEM_RIL_CDMA_CP_Status.activeSetPn[i2] = byteBuffer.getInt();
                    }
                    oEM_RIL_CDMA_CP_Status.activeSetStrength = new int[6];
                    for (i2 = 0; i2 < 6; i2++) {
                        oEM_RIL_CDMA_CP_Status.activeSetStrength[i2] = byteBuffer.getInt();
                    }
                    oEM_RIL_CDMA_CP_Status.neighborSetPn = new int[40];
                    while (i < 40) {
                        oEM_RIL_CDMA_CP_Status.neighborSetPn[i] = byteBuffer.getInt();
                        i++;
                    }
                    oEM_RIL_CDMA_CP_Status.bsLat = byteBuffer.getInt();
                    oEM_RIL_CDMA_CP_Status.bsLon = byteBuffer.getInt();
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.fer = " + oEM_RIL_CDMA_CP_Status.fer);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.bestActivePilot = " + oEM_RIL_CDMA_CP_Status.bestActivePilot);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.bestActiveStrength = " + oEM_RIL_CDMA_CP_Status.bestActiveStrength);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.bestNeighborPilot = " + oEM_RIL_CDMA_CP_Status.bestNeighborPilot);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.bestNeighborStrength = " + oEM_RIL_CDMA_CP_Status.bestNeighborStrength);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.sid = " + oEM_RIL_CDMA_CP_Status.sid);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.nid = " + oEM_RIL_CDMA_CP_Status.nid);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.channel = " + oEM_RIL_CDMA_CP_Status.channel);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.serviceOption = " + oEM_RIL_CDMA_CP_Status.serviceOption);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.droppedCallCounter = " + oEM_RIL_CDMA_CP_Status.droppedCallCounter);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.txPower = " + oEM_RIL_CDMA_CP_Status.txPower);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.band = " + oEM_RIL_CDMA_CP_Status.band);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.activePilotCount = " + oEM_RIL_CDMA_CP_Status.activePilotCount);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.neighborPilotCount = " + oEM_RIL_CDMA_CP_Status.neighborPilotCount);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.candPilotCount = " + oEM_RIL_CDMA_CP_Status.candPilotCount);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.cpState = " + oEM_RIL_CDMA_CP_Status.cpState);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.lastCallIndicator = " + oEM_RIL_CDMA_CP_Status.lastCallIndicator);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.lnaStatus = " + oEM_RIL_CDMA_CP_Status.lnaStatus);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.rssi = " + oEM_RIL_CDMA_CP_Status.rssi);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.callCounter = " + oEM_RIL_CDMA_CP_Status.callCounter);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: cp.bsLon = " + oEM_RIL_CDMA_CP_Status.bsLon);
                    }
                    oEM_RIL_CDMA_Result.obj = oEM_RIL_CDMA_CP_Status;
                }
            } catch (BufferUnderflowException e) {
                oEM_RIL_CDMA_Result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return oEM_RIL_CDMA_Result;
        }

        public static OEM_RIL_CDMA_Result byteArrToCpStatus(byte[] bArr) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToCpStatus: data = " + byteArrToHexString(bArr));
            }
            return bArr == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToCpStatus(ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToEvdoData(ByteBuffer byteBuffer) {
            OEM_RIL_CDMA_Result oEM_RIL_CDMA_Result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader readHookHeader = readHookHeader(byteBuffer);
                if (readHookHeader.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    oEM_RIL_CDMA_Result.errno = readHookHeader.error;
                } else {
                    OEM_RIL_CDMA_EVDO_Status oEM_RIL_CDMA_EVDO_Status = new OEM_RIL_CDMA_EVDO_Status();
                    oEM_RIL_CDMA_EVDO_Status.hdrChanNum = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.uatiColorCode = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.txUati = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.pilotPn = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.hdrRssi = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.rxPwrRx0Dbm = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.rxPwrRx1Dbm = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.measPkts = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.errPkts = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.sessionState = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.atState = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.ip = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.userCount = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.dropCount = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.hybridMode = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.macIndex = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.sectorIds = new int[16];
                    for (int i = 0; i < 16; i++) {
                        oEM_RIL_CDMA_EVDO_Status.sectorIds[i] = byteBuffer.getInt();
                    }
                    oEM_RIL_CDMA_EVDO_Status.pilotEnergy = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.drcCover = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.sinr = byteBuffer.getInt();
                    oEM_RIL_CDMA_EVDO_Status.anAuthStatus = byteBuffer.getInt();
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.hdrChanNum = " + oEM_RIL_CDMA_EVDO_Status.hdrChanNum);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.uatiColorCode = " + oEM_RIL_CDMA_EVDO_Status.uatiColorCode);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.txUati = " + oEM_RIL_CDMA_EVDO_Status.txUati);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.pilotPn = " + oEM_RIL_CDMA_EVDO_Status.pilotPn);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.hdrRssi = " + oEM_RIL_CDMA_EVDO_Status.hdrRssi);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.rxPwrRx0Dbm = " + oEM_RIL_CDMA_EVDO_Status.rxPwrRx0Dbm);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.rxPwrRx1Dbm = " + oEM_RIL_CDMA_EVDO_Status.rxPwrRx1Dbm);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.measPkts = " + oEM_RIL_CDMA_EVDO_Status.measPkts);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.errPkts = " + oEM_RIL_CDMA_EVDO_Status.errPkts);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.sessionState = " + oEM_RIL_CDMA_EVDO_Status.sessionState);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.atState = " + oEM_RIL_CDMA_EVDO_Status.atState);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.ip = " + oEM_RIL_CDMA_EVDO_Status.ip);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.userCount = " + oEM_RIL_CDMA_EVDO_Status.userCount);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.hybridMode = " + oEM_RIL_CDMA_EVDO_Status.hybridMode);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.macIndex = " + oEM_RIL_CDMA_EVDO_Status.macIndex);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: status.drcCover = " + oEM_RIL_CDMA_EVDO_Status.drcCover);
                    }
                    oEM_RIL_CDMA_Result.obj = oEM_RIL_CDMA_EVDO_Status;
                }
            } catch (BufferUnderflowException e) {
                oEM_RIL_CDMA_Result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return oEM_RIL_CDMA_Result;
        }

        public static OEM_RIL_CDMA_Result byteArrToEvdoData(byte[] bArr) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToEvdoData: data = " + byteArrToHexString(bArr));
            }
            return bArr == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToEvdoData(ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToGetSetDdtmDefaultPreferenceResp(ByteBuffer byteBuffer) {
            OEM_RIL_CDMA_Result oEM_RIL_CDMA_Result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader readHookHeader = readHookHeader(byteBuffer);
                if (readHookHeader.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    oEM_RIL_CDMA_Result.errno = readHookHeader.error;
                } else {
                    oEM_RIL_CDMA_Result.obj = new Integer(byteBuffer.get() & 255);
                }
            } catch (BufferUnderflowException e) {
                oEM_RIL_CDMA_Result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return oEM_RIL_CDMA_Result;
        }

        public static OEM_RIL_CDMA_Result byteArrToGetSetDdtmDefaultPreferenceResp(byte[] bArr) {
            return bArr == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToGetSetDdtmDefaultPreferenceResp(ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static String byteArrToHexString(byte[] bArr) {
            if (bArr == null) {
                return "null";
            }
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("hex:");
            for (int i = 0; i < bArr.length; i++) {
                stringBuilder.append(String.format("%02X", new Object[]{Byte.valueOf(bArr[i])}));
            }
            return stringBuilder.toString();
        }

        public static OEM_RIL_CDMA_Result byteArrToMobilePRev(ByteBuffer byteBuffer) {
            OEM_RIL_CDMA_Result oEM_RIL_CDMA_Result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader readHookHeader = readHookHeader(byteBuffer);
                if (readHookHeader.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    oEM_RIL_CDMA_Result.errno = readHookHeader.error;
                } else {
                    OEM_RIL_CDMA_MobilePRev fromInt = OEM_RIL_CDMA_MobilePRev.fromInt(byteBuffer.getInt());
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToMobilePRev: rev = " + fromInt.toString());
                    }
                    oEM_RIL_CDMA_Result.obj = fromInt;
                }
            } catch (BufferUnderflowException e) {
                oEM_RIL_CDMA_Result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return oEM_RIL_CDMA_Result;
        }

        public static OEM_RIL_CDMA_Result byteArrToMobilePRev(byte[] bArr) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToMobilePRev: data = " + byteArrToHexString(bArr));
            }
            return bArr == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToMobilePRev(ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToNamInfo(ByteBuffer byteBuffer) {
            int i = 10;
            int i2 = 3;
            int i3 = 0;
            OEM_RIL_CDMA_Result oEM_RIL_CDMA_Result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader readHookHeader = readHookHeader(byteBuffer);
                if (readHookHeader.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    oEM_RIL_CDMA_Result.errno = readHookHeader.error;
                } else {
                    int i4;
                    OEM_RIL_CDMA_NAM_Info oEM_RIL_CDMA_NAM_Info = new OEM_RIL_CDMA_NAM_Info();
                    oEM_RIL_CDMA_NAM_Info.mdn = new byte[10];
                    for (i4 = 0; i4 < oEM_RIL_CDMA_NAM_Info.mdn.length; i4++) {
                        oEM_RIL_CDMA_NAM_Info.mdn[i4] = byteBuffer.get();
                    }
                    for (i4 = 10; i4 < 16; i4++) {
                        byteBuffer.get();
                    }
                    oEM_RIL_CDMA_NAM_Info.min = new byte[10];
                    for (i4 = 0; i4 < oEM_RIL_CDMA_NAM_Info.min.length; i4++) {
                        oEM_RIL_CDMA_NAM_Info.min[i4] = byteBuffer.get();
                    }
                    while (i < 16) {
                        byteBuffer.get();
                        i++;
                    }
                    oEM_RIL_CDMA_NAM_Info.h_sid = byteBuffer.getInt();
                    oEM_RIL_CDMA_NAM_Info.h_nid = byteBuffer.getInt();
                    oEM_RIL_CDMA_NAM_Info.scm = byteBuffer.getInt();
                    oEM_RIL_CDMA_NAM_Info.imsi11_12 = new byte[2];
                    for (i = 0; i < oEM_RIL_CDMA_NAM_Info.imsi11_12.length; i++) {
                        oEM_RIL_CDMA_NAM_Info.imsi11_12[i] = byteBuffer.get();
                    }
                    for (i = 2; i < 4; i++) {
                        byteBuffer.get();
                    }
                    oEM_RIL_CDMA_NAM_Info.imsiMcc = new byte[3];
                    for (i = 0; i < oEM_RIL_CDMA_NAM_Info.imsiMcc.length; i++) {
                        oEM_RIL_CDMA_NAM_Info.imsiMcc[i] = byteBuffer.get();
                    }
                    for (i = 3; i < 4; i++) {
                        byteBuffer.get();
                    }
                    oEM_RIL_CDMA_NAM_Info.priCdmaA = byteBuffer.getInt();
                    oEM_RIL_CDMA_NAM_Info.priCdmaB = byteBuffer.getInt();
                    oEM_RIL_CDMA_NAM_Info.secCdmaA = byteBuffer.getInt();
                    oEM_RIL_CDMA_NAM_Info.secCdmaB = byteBuffer.getInt();
                    oEM_RIL_CDMA_NAM_Info.vocoderType = byteBuffer.getInt();
                    oEM_RIL_CDMA_NAM_Info.imsiMccT = new byte[3];
                    for (i = 0; i < oEM_RIL_CDMA_NAM_Info.imsiMccT.length; i++) {
                        oEM_RIL_CDMA_NAM_Info.imsiMccT[i] = byteBuffer.get();
                    }
                    while (i2 < 4) {
                        byteBuffer.get();
                        i2++;
                    }
                    oEM_RIL_CDMA_NAM_Info.imsiT = new byte[15];
                    for (i2 = 0; i2 < oEM_RIL_CDMA_NAM_Info.imsiT.length; i2++) {
                        oEM_RIL_CDMA_NAM_Info.imsiT[i2] = byteBuffer.get();
                    }
                    for (i2 = 15; i2 < 16; i2++) {
                        byteBuffer.get();
                    }
                    oEM_RIL_CDMA_NAM_Info.accessOverloadClass = byteBuffer.getInt();
                    oEM_RIL_CDMA_NAM_Info.imsiMAddrNum = byteBuffer.getInt();
                    if (oEM_RIL_CDMA_NAM_Info.imsiMAddrNum == 255) {
                        oEM_RIL_CDMA_NAM_Info.imsiMAddrNum = 0;
                    }
                    oEM_RIL_CDMA_NAM_Info.imsiMS1_0 = new byte[4];
                    for (i2 = 0; i2 < oEM_RIL_CDMA_NAM_Info.imsiMS1_0.length; i2++) {
                        oEM_RIL_CDMA_NAM_Info.imsiMS1_0[i2] = byteBuffer.get();
                    }
                    oEM_RIL_CDMA_NAM_Info.imsiMS2 = new byte[4];
                    for (i2 = 0; i2 < oEM_RIL_CDMA_NAM_Info.imsiMS2.length; i2++) {
                        oEM_RIL_CDMA_NAM_Info.imsiMS2[i2] = byteBuffer.get();
                    }
                    oEM_RIL_CDMA_NAM_Info.mob_term_home = byteBuffer.getInt();
                    oEM_RIL_CDMA_NAM_Info.mob_term_sid = byteBuffer.getInt();
                    oEM_RIL_CDMA_NAM_Info.mob_term_nid = byteBuffer.getInt();
                    oEM_RIL_CDMA_NAM_Info.newSpcCode = new byte[6];
                    while (i3 < oEM_RIL_CDMA_NAM_Info.newSpcCode.length) {
                        oEM_RIL_CDMA_NAM_Info.newSpcCode[i3] = byteBuffer.get();
                        i3++;
                    }
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.mdn = " + byteArrToHexString(oEM_RIL_CDMA_NAM_Info.mdn));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.min = " + byteArrToHexString(oEM_RIL_CDMA_NAM_Info.min));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.h_sid = " + oEM_RIL_CDMA_NAM_Info.h_sid);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.h_nid = " + oEM_RIL_CDMA_NAM_Info.h_nid);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.scm = " + oEM_RIL_CDMA_NAM_Info.scm);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.imsi11_12 = " + byteArrToHexString(oEM_RIL_CDMA_NAM_Info.imsi11_12));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.imsiMcc = " + byteArrToHexString(oEM_RIL_CDMA_NAM_Info.imsiMcc));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.priCdmaA = " + oEM_RIL_CDMA_NAM_Info.priCdmaA);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.priCdmaB = " + oEM_RIL_CDMA_NAM_Info.priCdmaB);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.secCdmaA = " + oEM_RIL_CDMA_NAM_Info.secCdmaA);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.secCdmaB = " + oEM_RIL_CDMA_NAM_Info.secCdmaB);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.vocoderType = " + oEM_RIL_CDMA_NAM_Info.vocoderType);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.imsiMccT = " + byteArrToHexString(oEM_RIL_CDMA_NAM_Info.imsiMccT));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.imsiT = " + byteArrToHexString(oEM_RIL_CDMA_NAM_Info.imsiT));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.accessOverloadClass = " + oEM_RIL_CDMA_NAM_Info.accessOverloadClass);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.imsiMAddrNum = " + oEM_RIL_CDMA_NAM_Info.imsiMAddrNum);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.imsiMS2 = " + byteArrToString(oEM_RIL_CDMA_NAM_Info.imsiMS2));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.imsiMS1_0 = " + byteArrToString(oEM_RIL_CDMA_NAM_Info.imsiMS1_0));
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.mob_term_home = " + oEM_RIL_CDMA_NAM_Info.mob_term_home);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.mob_term_sid = " + oEM_RIL_CDMA_NAM_Info.mob_term_sid);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.mob_term_nid = " + oEM_RIL_CDMA_NAM_Info.mob_term_nid);
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: namInfo.newSpcCode = " + byteArrToHexString(oEM_RIL_CDMA_NAM_Info.newSpcCode));
                    }
                    oEM_RIL_CDMA_Result.obj = oEM_RIL_CDMA_NAM_Info;
                }
            } catch (BufferUnderflowException e) {
                oEM_RIL_CDMA_Result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return oEM_RIL_CDMA_Result;
        }

        public static OEM_RIL_CDMA_Result byteArrToNamInfo(byte[] bArr) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamInfo: data = " + byteArrToHexString(bArr));
            }
            return bArr == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToNamInfo(ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToNamPrlVersion(ByteBuffer byteBuffer) {
            OEM_RIL_CDMA_Result oEM_RIL_CDMA_Result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader readHookHeader = readHookHeader(byteBuffer);
                if (readHookHeader.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    oEM_RIL_CDMA_Result.errno = readHookHeader.error;
                } else {
                    OEM_RIL_CDMA_NAM_PrlVersion oEM_RIL_CDMA_NAM_PrlVersion = new OEM_RIL_CDMA_NAM_PrlVersion();
                    oEM_RIL_CDMA_NAM_PrlVersion.prlVerison = byteBuffer.getInt();
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamPrlVersion: prlVerison = " + oEM_RIL_CDMA_NAM_PrlVersion.prlVerison);
                    }
                    oEM_RIL_CDMA_Result.obj = oEM_RIL_CDMA_NAM_PrlVersion;
                }
            } catch (BufferUnderflowException e) {
                oEM_RIL_CDMA_Result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return oEM_RIL_CDMA_Result;
        }

        public static OEM_RIL_CDMA_Result byteArrToNamPrlVersion(byte[] bArr) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToNamPrlVersion: data = " + byteArrToHexString(bArr));
            }
            return bArr == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToNamPrlVersion(ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToRdeData(ByteBuffer byteBuffer) {
            OEM_RIL_CDMA_Result oEM_RIL_CDMA_Result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader readHookHeader = readHookHeader(byteBuffer);
                if (readHookHeader.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    oEM_RIL_CDMA_Result.errno = readHookHeader.error;
                }
                oEM_RIL_CDMA_Result.obj = OEM_RIL_RDE_Data.deserialize(byteBuffer);
            } catch (BufferUnderflowException e) {
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.e(OemCdmaTelephonyManager.TAG, "byteArrToRdeData: buffer underflow");
                }
                oEM_RIL_CDMA_Result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return oEM_RIL_CDMA_Result;
        }

        public static OEM_RIL_CDMA_Result byteArrToRdeData(byte[] bArr) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToRdeData: data = " + byteArrToHexString(bArr));
            }
            return bArr == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToRdeData(ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToServiceOption(ByteBuffer byteBuffer) {
            OEM_RIL_CDMA_Result oEM_RIL_CDMA_Result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader readHookHeader = readHookHeader(byteBuffer);
                if (readHookHeader.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    oEM_RIL_CDMA_Result.errno = readHookHeader.error;
                } else {
                    OEM_RIL_CDMA_ServiceOption fromInt = OEM_RIL_CDMA_ServiceOption.fromInt(byteBuffer.getInt());
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToServiceOption: option = " + fromInt.toString());
                    }
                    oEM_RIL_CDMA_Result.obj = fromInt;
                }
            } catch (BufferUnderflowException e) {
                oEM_RIL_CDMA_Result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return oEM_RIL_CDMA_Result;
        }

        public static OEM_RIL_CDMA_Result byteArrToServiceOption(byte[] bArr) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToServiceOption: data = " + byteArrToHexString(bArr));
            }
            return bArr == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToServiceOption(ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static OEM_RIL_CDMA_Result byteArrToSidNidPairs(ByteBuffer byteBuffer) {
            OEM_RIL_CDMA_Result oEM_RIL_CDMA_Result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader readHookHeader = readHookHeader(byteBuffer);
                if (readHookHeader.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    oEM_RIL_CDMA_Result.errno = readHookHeader.error;
                } else {
                    OEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs = new OEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs();
                    oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs.sidNid = new OEM_RIL_CDMA_SID_NID_NAM_Pair[20];
                    for (int i = 0; i < 20; i++) {
                        oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs.sidNid[i] = new OEM_RIL_CDMA_SID_NID_NAM_Pair();
                        oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs.sidNid[i].sid = byteBuffer.getInt();
                        oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs.sidNid[i].nid = byteBuffer.getInt();
                        if (OemCdmaTelephonyManager.DEBUG) {
                            Log.d(OemCdmaTelephonyManager.TAG, "byteArrToSidNidPairs: pairs.sidNid[" + i + "].sid = " + oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs.sidNid[i].sid);
                        }
                        if (OemCdmaTelephonyManager.DEBUG) {
                            Log.d(OemCdmaTelephonyManager.TAG, "byteArrToSidNidPairs: pairs.sidNid[" + i + "].nid = " + oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs.sidNid[i].nid);
                        }
                    }
                    oEM_RIL_CDMA_Result.obj = oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs;
                }
            } catch (BufferUnderflowException e) {
                oEM_RIL_CDMA_Result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return oEM_RIL_CDMA_Result;
        }

        public static OEM_RIL_CDMA_Result byteArrToSidNidPairs(byte[] bArr) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToSidNidPairs: data = " + byteArrToHexString(bArr));
            }
            return bArr == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToSidNidPairs(ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static String byteArrToString(byte[] bArr) {
            int i;
            int i2 = 0;
            while (i2 < bArr.length) {
                i = i2 + 1;
                if (bArr[i2] == (byte) 0) {
                    break;
                }
                i2 = i;
            }
            i = i2;
            return new String(bArr, 0, i);
        }

        public static OEM_RIL_CDMA_Result byteArrToSubsidyPasswd(ByteBuffer byteBuffer) {
            OEM_RIL_CDMA_Result oEM_RIL_CDMA_Result = new OEM_RIL_CDMA_Result();
            try {
                OEM_RIL_CDMA_HookHeader readHookHeader = readHookHeader(byteBuffer);
                if (readHookHeader.error != OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS) {
                    oEM_RIL_CDMA_Result.errno = readHookHeader.error;
                } else {
                    OEM_RIL_CDMA_SubsidyPassword oEM_RIL_CDMA_SubsidyPassword = new OEM_RIL_CDMA_SubsidyPassword();
                    oEM_RIL_CDMA_SubsidyPassword.password = new byte[6];
                    for (int i = 0; i < oEM_RIL_CDMA_SubsidyPassword.password.length; i++) {
                        oEM_RIL_CDMA_SubsidyPassword.password[i] = byteBuffer.get();
                    }
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "byteArrToSubsidyPasswd: password = " + byteArrToHexString(oEM_RIL_CDMA_SubsidyPassword.password));
                    }
                    oEM_RIL_CDMA_Result.obj = oEM_RIL_CDMA_SubsidyPassword;
                }
            } catch (BufferUnderflowException e) {
                oEM_RIL_CDMA_Result.errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
            }
            return oEM_RIL_CDMA_Result;
        }

        public static OEM_RIL_CDMA_Result byteArrToSubsidyPasswd(byte[] bArr) {
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "byteArrToSubsidyPasswd: data = " + byteArrToHexString(bArr));
            }
            return bArr == null ? new OEM_RIL_CDMA_Result(OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE) : byteArrToSubsidyPasswd(ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static byte[] cpStatusToByteArr(OEM_RIL_CDMA_CP_Status oEM_RIL_CDMA_CP_Status, int i, String str) {
            int i2;
            int i3 = 0;
            ByteBuffer allocate = ByteBuffer.allocate(322);
            allocate.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(allocate, i, (int) OEM_RIL_CDMA_CP_Status.SIZE, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, str);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.fer);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.activePilotCount);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.neighborPilotCount);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.candPilotCount);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.bestActivePilot);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.bestActiveStrength);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.bestNeighborPilot);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.bestNeighborStrength);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.sid);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.nid);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.channel);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.serviceOption);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.droppedCallCounter);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.callCounter);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.lastCallIndicator);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.PRevInUse);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.band);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.lnaStatus);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.cpState);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.txPower);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.rssi);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.bid);
            for (i2 = 0; i2 < 6; i2++) {
                allocate.putInt(oEM_RIL_CDMA_CP_Status.activeSetPn[i2]);
            }
            for (i2 = 0; i2 < 6; i2++) {
                allocate.putInt(oEM_RIL_CDMA_CP_Status.activeSetStrength[i2]);
            }
            while (i3 < 40) {
                allocate.putInt(oEM_RIL_CDMA_CP_Status.neighborSetPn[i3]);
                i3++;
            }
            allocate.putInt(oEM_RIL_CDMA_CP_Status.bsLat);
            allocate.putInt(oEM_RIL_CDMA_CP_Status.bsLon);
            allocate.put(oEM_RIL_CDMA_CP_Status.is2000System);
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.fer = " + oEM_RIL_CDMA_CP_Status.fer);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.bestActivePilot = " + oEM_RIL_CDMA_CP_Status.bestActivePilot);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.bestActiveStrength = " + oEM_RIL_CDMA_CP_Status.bestActiveStrength);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.bestNeighborPilot = " + oEM_RIL_CDMA_CP_Status.bestNeighborPilot);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.bestNeighborStrength = " + oEM_RIL_CDMA_CP_Status.bestNeighborStrength);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.sid = " + oEM_RIL_CDMA_CP_Status.sid);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.nid = " + oEM_RIL_CDMA_CP_Status.nid);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.channel = " + oEM_RIL_CDMA_CP_Status.channel);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.serviceOption = " + oEM_RIL_CDMA_CP_Status.serviceOption);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.droppedCallCounter = " + oEM_RIL_CDMA_CP_Status.droppedCallCounter);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.txPower = " + oEM_RIL_CDMA_CP_Status.txPower);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.band = " + oEM_RIL_CDMA_CP_Status.band);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.activePilotCount = " + oEM_RIL_CDMA_CP_Status.activePilotCount);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.neighborPilotCount = " + oEM_RIL_CDMA_CP_Status.neighborPilotCount);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.candPilotCount = " + oEM_RIL_CDMA_CP_Status.candPilotCount);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.cpState = " + oEM_RIL_CDMA_CP_Status.cpState);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.lastCallIndicator = " + oEM_RIL_CDMA_CP_Status.lastCallIndicator);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.lnaStatus = " + oEM_RIL_CDMA_CP_Status.lnaStatus);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.rssi = " + oEM_RIL_CDMA_CP_Status.rssi);
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: cp.callCounter = " + oEM_RIL_CDMA_CP_Status.callCounter);
            }
            byte[] array = allocate.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "cpStatusToByteArr: data = " + byteArrToHexString(array));
            }
            return array;
        }

        public static byte[] evdoDataToByteArr(OEM_RIL_CDMA_EVDO_Status oEM_RIL_CDMA_EVDO_Status, int i, String str) {
            ByteBuffer allocate = ByteBuffer.allocate(PduHeaders.STORE);
            allocate.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(allocate, i, 144, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, str);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.hdrChanNum);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.uatiColorCode);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.txUati);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.pilotPn);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.hdrRssi);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.rxPwrRx0Dbm);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.rxPwrRx1Dbm);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.measPkts);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.errPkts);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.sessionState);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.atState);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.ip);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.userCount);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.dropCount);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.hybridMode);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.macIndex);
            for (int i2 = 0; i2 < 16; i2++) {
                allocate.putInt(oEM_RIL_CDMA_EVDO_Status.sectorIds[i2]);
            }
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.pilotEnergy);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.drcCover);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.sinr);
            allocate.putInt(oEM_RIL_CDMA_EVDO_Status.anAuthStatus);
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.hdrChanNum = " + oEM_RIL_CDMA_EVDO_Status.hdrChanNum);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.uatiColorCode = " + oEM_RIL_CDMA_EVDO_Status.uatiColorCode);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.txUati = " + oEM_RIL_CDMA_EVDO_Status.txUati);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.pilotPn = " + oEM_RIL_CDMA_EVDO_Status.pilotPn);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.hdrRssi = " + oEM_RIL_CDMA_EVDO_Status.hdrRssi);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.rxPwrRx0Dbm = " + oEM_RIL_CDMA_EVDO_Status.rxPwrRx0Dbm);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.rxPwrRx1Dbm = " + oEM_RIL_CDMA_EVDO_Status.rxPwrRx1Dbm);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.measPkts = " + oEM_RIL_CDMA_EVDO_Status.measPkts);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.errPkts = " + oEM_RIL_CDMA_EVDO_Status.errPkts);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.sessionState = " + oEM_RIL_CDMA_EVDO_Status.sessionState);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.atState = " + oEM_RIL_CDMA_EVDO_Status.atState);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.ip = " + oEM_RIL_CDMA_EVDO_Status.ip);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.userCount = " + oEM_RIL_CDMA_EVDO_Status.userCount);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.hybridMode = " + oEM_RIL_CDMA_EVDO_Status.hybridMode);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.macIndex = " + oEM_RIL_CDMA_EVDO_Status.macIndex);
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: status.drcCover = " + oEM_RIL_CDMA_EVDO_Status.drcCover);
            }
            byte[] array = allocate.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "evdoDataToByteArr: data = " + byteArrToHexString(array));
            }
            return array;
        }

        public static byte[] mobilePRevToByteArr(OEM_RIL_CDMA_MobilePRev oEM_RIL_CDMA_MobilePRev, int i, String str) {
            ByteBuffer allocate = ByteBuffer.allocate(22);
            allocate.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(allocate, i, 4, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, str);
            allocate.putInt(oEM_RIL_CDMA_MobilePRev.toInt());
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "mobilePRevToByteArr: rev = " + oEM_RIL_CDMA_MobilePRev);
            }
            byte[] array = allocate.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "mobilePRevToByteArr: data = " + byteArrToHexString(array));
            }
            return array;
        }

        public static byte[] namInfoToByteArr(OEM_RIL_CDMA_NAM_Info oEM_RIL_CDMA_NAM_Info, int i, String str) {
            int i2 = 10;
            int i3 = 3;
            byte[] bArr = null;
            ByteBuffer allocate = ByteBuffer.allocate(144);
            allocate.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(allocate, i, (int) OEM_RIL_CDMA_NAM_Info.SIZE, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, str);
            if (r6 == 10) {
                int i4;
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.mdn = " + byteArrToHexString(oEM_RIL_CDMA_NAM_Info.mdn));
                }
                for (byte put : oEM_RIL_CDMA_NAM_Info.mdn) {
                    allocate.put(put);
                }
                for (i4 = 10; i4 < 16; i4++) {
                    allocate.put((byte) 0);
                }
                if (r6 == 10) {
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.min = " + byteArrToHexString(oEM_RIL_CDMA_NAM_Info.min));
                    }
                    for (byte put2 : oEM_RIL_CDMA_NAM_Info.min) {
                        allocate.put(put2);
                    }
                    while (i2 < 16) {
                        allocate.put((byte) 0);
                        i2++;
                    }
                    allocate.putInt(oEM_RIL_CDMA_NAM_Info.h_sid);
                    allocate.putInt(oEM_RIL_CDMA_NAM_Info.h_nid);
                    allocate.putInt(oEM_RIL_CDMA_NAM_Info.scm);
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.h_sid = " + oEM_RIL_CDMA_NAM_Info.h_sid);
                    }
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.h_nid = " + oEM_RIL_CDMA_NAM_Info.h_nid);
                    }
                    if (OemCdmaTelephonyManager.DEBUG) {
                        Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.scm = " + oEM_RIL_CDMA_NAM_Info.scm);
                    }
                    if (i4 == 2) {
                        if (OemCdmaTelephonyManager.DEBUG) {
                            Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.imsi11_12 = " + byteArrToHexString(oEM_RIL_CDMA_NAM_Info.imsi11_12));
                        }
                        for (byte put3 : oEM_RIL_CDMA_NAM_Info.imsi11_12) {
                            allocate.put(put3);
                        }
                        for (i2 = 2; i2 < 4; i2++) {
                            allocate.put((byte) 0);
                        }
                        if (i4 == 3) {
                            if (OemCdmaTelephonyManager.DEBUG) {
                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.imsiMcc = " + byteArrToHexString(oEM_RIL_CDMA_NAM_Info.imsiMcc));
                            }
                            for (byte put32 : oEM_RIL_CDMA_NAM_Info.imsiMcc) {
                                allocate.put(put32);
                            }
                            for (i2 = 3; i2 < 4; i2++) {
                                allocate.put((byte) 0);
                            }
                            allocate.putInt(oEM_RIL_CDMA_NAM_Info.priCdmaA);
                            allocate.putInt(oEM_RIL_CDMA_NAM_Info.priCdmaB);
                            allocate.putInt(oEM_RIL_CDMA_NAM_Info.secCdmaA);
                            allocate.putInt(oEM_RIL_CDMA_NAM_Info.secCdmaB);
                            allocate.putInt(oEM_RIL_CDMA_NAM_Info.vocoderType);
                            if (OemCdmaTelephonyManager.DEBUG) {
                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.priCdmaA = " + oEM_RIL_CDMA_NAM_Info.priCdmaA);
                            }
                            if (OemCdmaTelephonyManager.DEBUG) {
                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.priCdmaB = " + oEM_RIL_CDMA_NAM_Info.priCdmaB);
                            }
                            if (OemCdmaTelephonyManager.DEBUG) {
                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.secCdmaA = " + oEM_RIL_CDMA_NAM_Info.secCdmaA);
                            }
                            if (OemCdmaTelephonyManager.DEBUG) {
                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.secCdmaB = " + oEM_RIL_CDMA_NAM_Info.secCdmaB);
                            }
                            if (OemCdmaTelephonyManager.DEBUG) {
                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.vocoderType = " + oEM_RIL_CDMA_NAM_Info.vocoderType);
                            }
                            if (i4 == 3) {
                                if (OemCdmaTelephonyManager.DEBUG) {
                                    Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.imsiMccT = " + byteArrToHexString(oEM_RIL_CDMA_NAM_Info.imsiMccT));
                                }
                                for (byte put322 : oEM_RIL_CDMA_NAM_Info.imsiMccT) {
                                    allocate.put(put322);
                                }
                                while (i3 < 4) {
                                    allocate.put((byte) 0);
                                    i3++;
                                }
                                if (i2 == 15) {
                                    if (OemCdmaTelephonyManager.DEBUG) {
                                        Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.imsiT = " + byteArrToHexString(oEM_RIL_CDMA_NAM_Info.imsiT));
                                    }
                                    for (byte put4 : oEM_RIL_CDMA_NAM_Info.imsiT) {
                                        allocate.put(put4);
                                    }
                                    for (i3 = 15; i3 < 16; i3++) {
                                        allocate.put((byte) 0);
                                    }
                                    allocate.putInt(oEM_RIL_CDMA_NAM_Info.accessOverloadClass);
                                    if (OemCdmaTelephonyManager.DEBUG) {
                                        Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.accessOverloadClass = " + oEM_RIL_CDMA_NAM_Info.accessOverloadClass);
                                    }
                                    if (oEM_RIL_CDMA_NAM_Info.imsiMAddrNum == 0) {
                                        oEM_RIL_CDMA_NAM_Info.imsiMAddrNum = 255;
                                    }
                                    allocate.putInt(oEM_RIL_CDMA_NAM_Info.imsiMAddrNum);
                                    if (OemCdmaTelephonyManager.DEBUG) {
                                        Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.imsiMAddrNum = " + oEM_RIL_CDMA_NAM_Info.imsiMAddrNum);
                                    }
                                    if (i2 == 4) {
                                        if (OemCdmaTelephonyManager.DEBUG) {
                                            Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.imsiMS1_0 = " + byteArrToString(oEM_RIL_CDMA_NAM_Info.imsiMS1_0));
                                        }
                                        for (byte put42 : oEM_RIL_CDMA_NAM_Info.imsiMS1_0) {
                                            allocate.put(put42);
                                        }
                                        if (i2 == 4) {
                                            if (OemCdmaTelephonyManager.DEBUG) {
                                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.imsiMS2 = " + byteArrToString(oEM_RIL_CDMA_NAM_Info.imsiMS2));
                                            }
                                            for (byte put422 : oEM_RIL_CDMA_NAM_Info.imsiMS2) {
                                                allocate.put(put422);
                                            }
                                            allocate.putInt(oEM_RIL_CDMA_NAM_Info.mob_term_home);
                                            if (OemCdmaTelephonyManager.DEBUG) {
                                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.mob_term_home = " + oEM_RIL_CDMA_NAM_Info.mob_term_home);
                                            }
                                            allocate.putInt(oEM_RIL_CDMA_NAM_Info.mob_term_sid);
                                            if (OemCdmaTelephonyManager.DEBUG) {
                                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.mob_term_sid = " + oEM_RIL_CDMA_NAM_Info.mob_term_sid);
                                            }
                                            allocate.putInt(oEM_RIL_CDMA_NAM_Info.mob_term_nid);
                                            if (OemCdmaTelephonyManager.DEBUG) {
                                                Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.mob_term_nid = " + oEM_RIL_CDMA_NAM_Info.mob_term_nid);
                                            }
                                            if (i3 == 6) {
                                                if (OemCdmaTelephonyManager.DEBUG) {
                                                    Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: namInfo.newSpcCode = " + byteArrToHexString(oEM_RIL_CDMA_NAM_Info.newSpcCode));
                                                }
                                                for (byte put5 : oEM_RIL_CDMA_NAM_Info.newSpcCode) {
                                                    allocate.put(put5);
                                                }
                                                bArr = allocate.array();
                                                if (OemCdmaTelephonyManager.DEBUG) {
                                                    Log.d(OemCdmaTelephonyManager.TAG, "namInfoToByteArr: data = " + byteArrToHexString(bArr));
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
            return bArr;
        }

        public static byte[] namPrlVersionToByteArr(OEM_RIL_CDMA_NAM_PrlVersion oEM_RIL_CDMA_NAM_PrlVersion, int i, String str) {
            ByteBuffer allocate = ByteBuffer.allocate(22);
            allocate.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(allocate, i, 4, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, str);
            allocate.putInt(oEM_RIL_CDMA_NAM_PrlVersion.prlVerison);
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "namPrlVersionToByteArr: prlVerison = " + oEM_RIL_CDMA_NAM_PrlVersion.prlVerison);
            }
            byte[] array = allocate.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "namPrlVersionToByteArr: data = " + byteArrToHexString(array));
            }
            return array;
        }

        public static byte[] rdeDataToByteArr(OEM_RIL_RDE_Data oEM_RIL_RDE_Data, int i, String str) {
            return rdeDataToByteArr(oEM_RIL_RDE_Data, i, str, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS);
        }

        public static byte[] rdeDataToByteArr(OEM_RIL_RDE_Data oEM_RIL_RDE_Data, int i, String str, OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno) {
            ByteBuffer allocate = ByteBuffer.allocate((oEM_RIL_RDE_Data != null ? oEM_RIL_RDE_Data.SIZE() : 0) + 18);
            allocate.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(allocate, i, oEM_RIL_RDE_Data != null ? oEM_RIL_RDE_Data.SIZE() : 0, oEM_RIL_CDMA_Errno, str);
            if (oEM_RIL_RDE_Data != null) {
                allocate.putInt(oEM_RIL_RDE_Data.elementID);
                allocate.putInt(oEM_RIL_RDE_Data.recordNum);
                allocate.putInt(oEM_RIL_RDE_Data.offset);
                if (oEM_RIL_RDE_Data.dataObj != null) {
                    allocate.putInt(oEM_RIL_RDE_Data.dataObj.size());
                    oEM_RIL_RDE_Data.dataObj.serialize(allocate);
                } else {
                    allocate.putInt(0);
                    allocate.put((byte) 0);
                }
            }
            byte[] array = allocate.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "rdeDataToByteArr: data = " + byteArrToHexString(array));
            }
            return array;
        }

        public static final OEM_RIL_CDMA_HookHeader readHookHeader(ByteBuffer byteBuffer) {
            OEM_RIL_CDMA_HookHeader oEM_RIL_CDMA_HookHeader = new OEM_RIL_CDMA_HookHeader();
            try {
                oEM_RIL_CDMA_HookHeader.msgId = byteBuffer.getInt();
                oEM_RIL_CDMA_HookHeader.msgLength = byteBuffer.getInt();
                oEM_RIL_CDMA_HookHeader.error = OEM_RIL_CDMA_Errno.fromInt(byteBuffer.getInt());
                oEM_RIL_CDMA_HookHeader.spcLockCode = new byte[6];
                for (int i = 0; i < oEM_RIL_CDMA_HookHeader.spcLockCode.length; i++) {
                    oEM_RIL_CDMA_HookHeader.spcLockCode[i] = byteBuffer.get();
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "readHookHeader: msgId = " + oEM_RIL_CDMA_HookHeader.msgId);
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "readHookHeader: msgLength = " + oEM_RIL_CDMA_HookHeader.msgLength);
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "readHookHeader: error = " + oEM_RIL_CDMA_HookHeader.error.toString());
                }
                if (!OemCdmaTelephonyManager.DEBUG) {
                    return oEM_RIL_CDMA_HookHeader;
                }
                Log.d(OemCdmaTelephonyManager.TAG, "readHookHeader: spcLockCode = " + byteArrToHexString(oEM_RIL_CDMA_HookHeader.spcLockCode));
                return oEM_RIL_CDMA_HookHeader;
            } catch (BufferUnderflowException e) {
                return null;
            }
        }

        public static final OEM_RIL_CDMA_HookHeader readHookHeader(byte[] bArr) {
            return bArr == null ? null : readHookHeader(ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN));
        }

        public static byte[] roamingListToByteArr(rde_roaming_list_type rde_roaming_list_type, int i, String str) {
            ByteBuffer allocate = ByteBuffer.allocate(rde_roaming_list_type.size() + 18);
            allocate.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(allocate, i, rde_roaming_list_type.size(), OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, str);
            rde_roaming_list_type.serialize(allocate);
            byte[] array = allocate.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "roamingListToByteArr: data = " + byteArrToString(array));
            }
            return array;
        }

        public static byte[] serviceOptionToByteArr(OEM_RIL_CDMA_ServiceOption oEM_RIL_CDMA_ServiceOption, int i, String str) {
            ByteBuffer allocate = ByteBuffer.allocate(22);
            allocate.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(allocate, i, 4, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, str);
            allocate.putInt(oEM_RIL_CDMA_ServiceOption.toInt());
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "serviceOptionToByteArr: serviceOption = " + oEM_RIL_CDMA_ServiceOption.toString());
            }
            byte[] array = allocate.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "serviceOptionToByteArr: data = " + byteArrToHexString(array));
            }
            return array;
        }

        public static byte[] sidNidPairsToByteArr(OEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs, int i, String str) {
            ByteBuffer allocate = ByteBuffer.allocate(oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs.SIZE() + 18);
            allocate.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(allocate, i, oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs.SIZE(), OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, str);
            for (int i2 = 0; i2 < oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs.sidNid.length; i2++) {
                allocate.putInt(oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs.sidNid[i2].sid);
                allocate.putInt(oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs.sidNid[i2].nid);
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "sidNidPairsToByteArr: sidNidPairs.sidNid[" + i2 + "].sid = " + oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs.sidNid[i2].sid);
                }
                if (OemCdmaTelephonyManager.DEBUG) {
                    Log.d(OemCdmaTelephonyManager.TAG, "sidNidPairsToByteArr: sidNidPairs.sidNid[" + i2 + "].nid = " + oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs.sidNid[i2].nid);
                }
            }
            byte[] array = allocate.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "sidNidPairsToByteArr: data = " + byteArrToHexString(array));
            }
            return array;
        }

        public static byte[] subsidyPasswdToByteArr(OEM_RIL_CDMA_SubsidyPassword oEM_RIL_CDMA_SubsidyPassword) {
            ByteBuffer allocate = ByteBuffer.allocate(24);
            allocate.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(allocate, (int) OemCdmaTelephonyManager.OEM_RIL_REQUEST_CDMA_CHECK_SUBSIDY_LOCK_PASSWD, 6, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS, oEM_RIL_CDMA_SubsidyPassword.password);
            for (int i = 0; i < 6; i++) {
                allocate.put(oEM_RIL_CDMA_SubsidyPassword.password[i]);
            }
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "subsidyPasswdToByteArr: password.password = " + byteArrToHexString(oEM_RIL_CDMA_SubsidyPassword.password));
            }
            byte[] array = allocate.array();
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "subsidyPasswdToByteArr: data = " + byteArrToHexString(array));
            }
            return array;
        }

        public static void writeHookHeader(ByteBuffer byteBuffer, int i, int i2, OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno) {
            writeHookHeader(byteBuffer, i, i2, oEM_RIL_CDMA_Errno, OemCdmaTelephonyManager.sDefaultSpcCode);
        }

        public static void writeHookHeader(ByteBuffer byteBuffer, int i, int i2, OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno, String str) {
            byte[] bArr = new byte[6];
            for (int i3 = 0; i3 < bArr.length; i3++) {
                bArr[i3] = (byte) str.charAt(i3);
            }
            writeHookHeader(byteBuffer, i, i2, oEM_RIL_CDMA_Errno, bArr);
        }

        public static void writeHookHeader(ByteBuffer byteBuffer, int i, int i2, OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno, byte[] bArr) {
            byteBuffer.putInt(i);
            byteBuffer.putInt(i2);
            byteBuffer.putInt(oEM_RIL_CDMA_Errno.toInt());
            for (int i3 = 0; i3 < 6; i3++) {
                byteBuffer.put(bArr[i3]);
            }
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "writeHookHeader: msgId = " + i);
            }
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "writeHookHeader: msgLength = " + i2);
            }
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "writeHookHeader: error = " + oEM_RIL_CDMA_Errno);
            }
            if (OemCdmaTelephonyManager.DEBUG) {
                Log.d(OemCdmaTelephonyManager.TAG, "writeHookHeader: spcLockCode = " + byteArrToHexString(bArr));
            }
        }

        public static void writeHookHeader(ByteBuffer byteBuffer, OEM_RIL_CDMA_HookHeader oEM_RIL_CDMA_HookHeader) {
            writeHookHeader(byteBuffer, oEM_RIL_CDMA_HookHeader.msgId, oEM_RIL_CDMA_HookHeader.msgLength, oEM_RIL_CDMA_HookHeader.error, oEM_RIL_CDMA_HookHeader.spcLockCode);
        }

        public static byte[] writeHookHeader(int i) {
            return writeHookHeader(i, 0, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS);
        }

        public static byte[] writeHookHeader(int i, int i2, OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno) {
            ByteBuffer allocate = ByteBuffer.allocate(18);
            allocate.order(ByteOrder.LITTLE_ENDIAN);
            writeHookHeader(allocate, i, i2, oEM_RIL_CDMA_Errno);
            return allocate.array();
        }
    }

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

    private enum TelephonyMgrState {
        STATE_IDLE,
        STATE_WAITING_FOR_RESPONSE
    }

    private static class Watchdog extends Thread {
        public static int MSG_TIMEOUT = 256;
        private static int TIMEOUT = 5000;
        private boolean mExit = false;
        private Handler mHandler;
        private Message watchingMsg = null;

        public Watchdog(Handler handler) {
            this.mHandler = handler;
        }

        public void exit() {
            synchronized (this) {
                this.mExit = true;
                interrupt();
            }
        }

        public void run() {
            while (!this.mExit) {
                synchronized (this) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
                try {
                    Thread.sleep((long) TIMEOUT);
                    Message message = new Message();
                    message.what = MSG_TIMEOUT;
                    message.obj = this.watchingMsg;
                    this.mHandler.sendMessage(message);
                } catch (InterruptedException e2) {
                }
            }
        }

        public void sleep() {
            synchronized (this) {
                interrupt();
            }
        }

        public void watch(Message message) {
            synchronized (this) {
                this.watchingMsg = message;
                notify();
            }
        }
    }

    private OemCdmaTelephonyManager() {
        this.mDog.start();
    }

    public static String byteArrayToHexString(byte[] bArr) {
        StringBuffer stringBuffer = new StringBuffer(bArr.length * 2);
        for (byte b : bArr) {
            int i = b & 255;
            if (i < 16) {
                stringBuffer.append('0');
            }
            stringBuffer.append(Integer.toHexString(i));
        }
        return stringBuffer.toString().toUpperCase();
    }

    public static String byteToHexString(byte b) {
        int i = b & 255;
        return i < 16 ? "0" + Integer.toHexString(i) : Integer.toHexString(i);
    }

    public static OemCdmaTelephonyManager getInstance() {
        synchronized (OemCdmaTelephonyManager.class) {
            try {
                if (mInstance == null) {
                    mInstance = new OemCdmaTelephonyManager();
                }
                OemCdmaTelephonyManager oemCdmaTelephonyManager = mInstance;
                return oemCdmaTelephonyManager;
            } finally {
                Object obj = OemCdmaTelephonyManager.class;
            }
        }
    }

    public OEM_RIL_CDMA_Errno checkSubsidyLockPasswd(OEM_RIL_CDMA_SubsidyPassword oEM_RIL_CDMA_SubsidyPassword, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "checkSubsidyLockPasswd()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.subsidyPasswdToByteArr(oEM_RIL_CDMA_SubsidyPassword), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_CHECK_SUBSIDY_LOCK_PASSWD), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno factoryReset(Handler handler) {
        return factoryReset(handler, (byte) -1);
    }

    public OEM_RIL_CDMA_Errno factoryReset(Handler handler, byte b) {
        if (DEBUG) {
            Log.v(TAG, "factoryReset type = " + b);
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_FACTORY_RESET);
        ByteBuffer allocate = ByteBuffer.allocate(19);
        allocate.order(ByteOrder.LITTLE_ENDIAN);
        OemCdmaDataConverter.writeHookHeader(allocate, OEM_RIL_REQUEST_CDMA_FACTORY_RESET, 1, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS);
        allocate.put(b);
        invokeOemRilRequestRaw(allocate.array(), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno get1xAdvancedOption(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "get1xAdvancedOption");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_1xADV_OPTION);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = OEM_RIL_RDE_Data.RDE_EFS_1XADVANCED_CAPABILITY_STATUS_I;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getBC0(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getBC0()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_BC0), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_BC0), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getBC1(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getBC1()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_BC1), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_BC1), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getBC10(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getBC10()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_BC10), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_BC10), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getBC14(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getBC14()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_BC14), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_BC14), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getCallProcessingData(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getCallProcessingData()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_FIELD_TEST_GET_CALL_PROCESSING_DATA), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_FIELD_TEST_GET_CALL_PROCESSING_DATA), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getDDTMDefaultPreference(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getDDTMDefaultPreference()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_DDTM_DEFAULT_PREFERENCE), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_DDTM_DEFAULT_PREFERENCE), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getEsn(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getEsn()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_ESN);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 10;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getEvdoData(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getEvdoData()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_FIELD_TEST_GET_EVDO_DATA), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_FIELD_TEST_GET_EVDO_DATA), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getForNIDReg(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getForNIDReg()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_FOR_NID_REG);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 52;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getForSIDReg(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getForSIDReg()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_FOR_SID_REG);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 51;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getHdrAnAuthPasswdLong(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getHdrAnAuthPasswdLong()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_HDR_AN_AUTH_PASSWD_LONG);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 4;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getHomeSIDReg(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getHomeSIDReg()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_HOME_SID_REG);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 50;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getIMSIAddrNum(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getIMSIAddrNum()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_IMSI_M_ADDR_NUM);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 49;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getLockCode(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getLockCode()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_LOCK_CODE);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 23;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getMipErrorCode(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getMipErrorCode()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_MIP_ERROR_CODE_I);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = OEM_RIL_RDE_Data.RDE_MIP_ERROR_CODE_I;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getMipGenProfile(int i, Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getMipGenProfile()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_MIP_GEN_USER_PROF);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 9;
        oEM_RIL_RDE_Data.recordNum = i;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getMipSsProfile(int i, Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getMipSsProfile()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_MIP_SS_USER_PROF);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 46;
        oEM_RIL_RDE_Data.recordNum = i;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getMobilePRev(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getMobilePRev()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_MOB_P_REV), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_MOB_P_REV), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    /* Access modifiers changed, original: 0000 */
    public Handler getMsgHandler() {
        return this.mMsgHandler;
    }

    public OEM_RIL_CDMA_Errno getNamAccolc(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getNamImsiAccolc()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_NAM_ACCOLC);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 2;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamImsi1112(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getNamImsi1122()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_NAM_IMSI_11_12);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 16;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamImsiMcc(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getNamImsiMcc()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_NAM_IMSI_MCC);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 17;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamInfo(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getNamInfo()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_NAM_INFO), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_NAM_INFO), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamMdn(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getNamMdn()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_NAM_MDN);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 7;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamMin1(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getNamMin1()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_NAM_MIN1);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 25;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamMin2(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getNamMin2()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_NAM_MIN2);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 26;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamPrlVersion(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getNamPrlVersion()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_CDMA_PRL_VERSION), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_CDMA_PRL_VERSION), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamScm(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getNamScm()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_NAM_SCM);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 36;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getNamSidNidPairs(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getNamSidNidPairs()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_GET_SID_NID_PAIRS), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_SID_NID_PAIRS), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getOtksl(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getOtksl()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_OTKSL);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 27;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getPrefVoiceSo(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getPrefVoiceSo()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_PREF_VOICE_SO);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 30;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getSO68Enabled(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getSO68Enabled()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_CDMA_SO68_ENABLED);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 48;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getServiceOption(Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "getServiceOption()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(OEM_RIL_REQUEST_CDMA_FIELD_TEST_GET_SERVICE_OPTION), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_FIELD_TEST_GET_SERVICE_OPTION), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getSlotCycleIndex(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getSlotCycleIndex()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_SLOT_CYCLE_INDEX);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 47;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getSlotCycleMode(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getSlotCycleMode()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_SLOT_CYCLE_MODE);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = OEM_RIL_RDE_Data.RDE_NV_SLOTTED_MODE_I;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getSo73Cop0Option(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getSo73Cop0Option");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_SO73COP0_OPTION);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = OEM_RIL_RDE_Data.RDE_EFS_SO73_COP0_SUPPORTED_I;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno getSo73Option(Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "getSo73Option");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, RdeRequestId.RDEREQ_GET_SO73_OPTION);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 45;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    /* Access modifiers changed, original: 0000 */
    public void invokeOemRilRequestRaw(byte[] bArr, Message message, Handler handler) {
        synchronized (this) {
            if (DEBUG) {
                Log.d(TAG, "invokeOemRilRequestRaw(): msg.what = " + message.what);
            }
            switch (this.mState) {
                case STATE_IDLE:
                    this.mState = TelephonyMgrState.STATE_WAITING_FOR_RESPONSE;
                    this.mCurrentMessage = message;
                    this.mUserHandler = handler;
                    if (DEBUG) {
                        Log.d(TAG, "sending request to RIL");
                    }
                    this.mPhone.invokeOemRilRequestRaw(bArr, message);
                    this.mDog.watch(message);
                    break;
                case STATE_WAITING_FOR_RESPONSE:
                    if (DEBUG) {
                        Log.d(TAG, "OemCdmaTelephonyManager is busy. pushing request to the queue.");
                    }
                    this.mRequestList.add(new HookRequest(bArr, message, handler));
                    break;
                default:
                    if (DEBUG) {
                        Log.e(TAG, "wrong state in invokeOemRilRequestRaw(): " + this.mState);
                        break;
                    }
                    break;
            }
        }
    }

    public OEM_RIL_CDMA_Errno set1xAdvancedOption(boolean z, Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "set1xAdvancedOption - enabled: " + z);
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_1xADV_OPTION);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = OEM_RIL_RDE_Data.RDE_EFS_1XADVANCED_CAPABILITY_STATUS_I;
        oEM_RIL_RDE_Data.dataObj = new rde_obj_type(z ? (byte) 1 : (byte) 0);
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setBC0(int i, String str, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setBC0()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_BC0);
        OEM_RIL_CDMA_BC oem_ril_cdma_bc = new OEM_RIL_CDMA_BC();
        oem_ril_cdma_bc.status = i;
        invokeOemRilRequestRaw(OemCdmaDataConverter.BCToByteArr(oem_ril_cdma_bc, OEM_RIL_REQUEST_CDMA_SET_BC0, str), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setBC1(int i, String str, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setBC1()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_BC1);
        OEM_RIL_CDMA_BC oem_ril_cdma_bc = new OEM_RIL_CDMA_BC();
        oem_ril_cdma_bc.status = i;
        invokeOemRilRequestRaw(OemCdmaDataConverter.BCToByteArr(oem_ril_cdma_bc, OEM_RIL_REQUEST_CDMA_SET_BC1, str), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setBC10(int i, String str, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setBC10()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_BC10);
        OEM_RIL_CDMA_BC oem_ril_cdma_bc = new OEM_RIL_CDMA_BC();
        oem_ril_cdma_bc.status = i;
        invokeOemRilRequestRaw(OemCdmaDataConverter.BCToByteArr(oem_ril_cdma_bc, OEM_RIL_REQUEST_CDMA_SET_BC10, str), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setBC14(int i, String str, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setBC14()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_BC14);
        OEM_RIL_CDMA_BC oem_ril_cdma_bc = new OEM_RIL_CDMA_BC();
        oem_ril_cdma_bc.status = i;
        invokeOemRilRequestRaw(OemCdmaDataConverter.BCToByteArr(oem_ril_cdma_bc, OEM_RIL_REQUEST_CDMA_SET_BC14, str), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setDDTMDefaultPreference(DDTMDefaultPreferenceSettings dDTMDefaultPreferenceSettings, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setDDTMDefaultPreference()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_DDTM_DEFAULT_PREFERENCE);
        ByteBuffer allocate = ByteBuffer.allocate(22);
        allocate.order(ByteOrder.LITTLE_ENDIAN);
        OemCdmaDataConverter.writeHookHeader(allocate, OEM_RIL_REQUEST_CDMA_SET_DDTM_DEFAULT_PREFERENCE, 4, OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS);
        allocate.putInt(dDTMDefaultPreferenceSettings.toInt());
        invokeOemRilRequestRaw(allocate.array(), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setForNIDReg(int i, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setForNIDReg()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_FOR_NID_REG);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 52;
        oEM_RIL_RDE_Data.recordNum = 0;
        oEM_RIL_RDE_Data.offset = 0;
        rde_mob_term_type rde_mob_term_type = new rde_mob_term_type();
        rde_mob_term_type.enabled[0] = (byte) i;
        rde_mob_term_type.enabled[1] = (byte) i;
        oEM_RIL_RDE_Data.dataObj = rde_mob_term_type;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setForSIDReg(int i, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setForSIDReg()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_FOR_SID_REG);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 51;
        oEM_RIL_RDE_Data.recordNum = 0;
        oEM_RIL_RDE_Data.offset = 0;
        rde_mob_term_type rde_mob_term_type = new rde_mob_term_type();
        rde_mob_term_type.enabled[0] = (byte) i;
        rde_mob_term_type.enabled[1] = (byte) i;
        oEM_RIL_RDE_Data.dataObj = rde_mob_term_type;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setHomeSIDReg(int i, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setHomeSIDReg()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_HOME_SID_REG);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 50;
        oEM_RIL_RDE_Data.recordNum = 0;
        oEM_RIL_RDE_Data.offset = 0;
        rde_mob_term_type rde_mob_term_type = new rde_mob_term_type();
        rde_mob_term_type.enabled[0] = (byte) i;
        rde_mob_term_type.enabled[1] = (byte) i;
        oEM_RIL_RDE_Data.dataObj = rde_mob_term_type;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setIMSIAddrNum(int i, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setIMSIAddrNum()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_IMSI_M_ADDR_NUM);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 49;
        oEM_RIL_RDE_Data.recordNum = 0;
        oEM_RIL_RDE_Data.offset = 0;
        rde_imsi_addr_num_type rde_imsi_addr_num_type = new rde_imsi_addr_num_type();
        rde_imsi_addr_num_type.num = (byte) i;
        oEM_RIL_RDE_Data.dataObj = rde_imsi_addr_num_type;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setLockCode(String str, String str2, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setLockCode()");
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_LOCK_CODE);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 23;
        oEM_RIL_RDE_Data.dataObj = new rde_obj_type(str);
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setMipGenProfile(int i, rde_gen_profile_type rde_gen_profile_type, Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "setMipGenProfile()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_MIP_GEN_USER_PROF);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 9;
        oEM_RIL_RDE_Data.recordNum = i;
        oEM_RIL_RDE_Data.offset = 0;
        oEM_RIL_RDE_Data.dataObj = rde_gen_profile_type;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setMipSsProfile(int i, rde_ss_profile_type rde_ss_profile_type, Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "setMipSsProfile()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_MIP_SS_USER_PROF);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 46;
        oEM_RIL_RDE_Data.recordNum = i;
        oEM_RIL_RDE_Data.offset = 0;
        oEM_RIL_RDE_Data.dataObj = rde_ss_profile_type;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setMobilePRev(OEM_RIL_CDMA_MobilePRev oEM_RIL_CDMA_MobilePRev, String str, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setMobilePRev()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.mobilePRevToByteArr(oEM_RIL_CDMA_MobilePRev, OEM_RIL_REQUEST_CDMA_SET_MOB_P_REV, str), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_MOB_P_REV), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamAccolc(rde_nam_accolc_type rde_nam_accolc_type, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setNamAccolc()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_NAM_ACCOLC);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 2;
        oEM_RIL_RDE_Data.recordNum = 0;
        oEM_RIL_RDE_Data.offset = 0;
        oEM_RIL_RDE_Data.dataObj = rde_nam_accolc_type;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamImsi1112(rde_nam_imsi_11_12_type rde_nam_imsi_11_12_type, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setNamImsi1112()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_NAM_IMSI_11_12);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 16;
        oEM_RIL_RDE_Data.recordNum = 0;
        oEM_RIL_RDE_Data.offset = 0;
        oEM_RIL_RDE_Data.dataObj = rde_nam_imsi_11_12_type;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamImsiMcc(rde_nam_imsi_mcc_type rde_nam_imsi_mcc_type, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setNamImsiMcc()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_NAM_IMSI_MCC);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 17;
        oEM_RIL_RDE_Data.recordNum = 0;
        oEM_RIL_RDE_Data.offset = 0;
        oEM_RIL_RDE_Data.dataObj = rde_nam_imsi_mcc_type;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamInfo(OEM_RIL_CDMA_NAM_Info oEM_RIL_CDMA_NAM_Info, String str, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setNamInfo()");
        }
        if (oEM_RIL_CDMA_NAM_Info.mdn.length != 10 || oEM_RIL_CDMA_NAM_Info.min.length != 10 || oEM_RIL_CDMA_NAM_Info.imsi11_12.length != 2 || oEM_RIL_CDMA_NAM_Info.imsiMcc.length != 3 || oEM_RIL_CDMA_NAM_Info.imsiMccT.length != 3 || oEM_RIL_CDMA_NAM_Info.imsiT.length != 15 || oEM_RIL_CDMA_NAM_Info.newSpcCode.length != 6) {
            return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.namInfoToByteArr(oEM_RIL_CDMA_NAM_Info, OEM_RIL_REQUEST_CDMA_SET_NAM_INFO, str), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_NAM_INFO), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamMdn(rde_nam_mdn_type rde_nam_mdn_type, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setNamMdn()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_NAM_MDN);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 7;
        oEM_RIL_RDE_Data.recordNum = 0;
        oEM_RIL_RDE_Data.offset = 0;
        oEM_RIL_RDE_Data.dataObj = rde_nam_mdn_type;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamMin1(rde_nam_min1_type rde_nam_min1_type, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setNamMin1()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_NAM_MIN1);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 25;
        oEM_RIL_RDE_Data.recordNum = 0;
        oEM_RIL_RDE_Data.offset = 0;
        oEM_RIL_RDE_Data.dataObj = rde_nam_min1_type;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamMin2(rde_nam_min2_type rde_nam_min2_type, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setNamMin2()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_NAM_MIN2);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 26;
        oEM_RIL_RDE_Data.recordNum = 0;
        oEM_RIL_RDE_Data.offset = 0;
        oEM_RIL_RDE_Data.dataObj = rde_nam_min2_type;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setNamSidNidPairs(OEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs, String str, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setNamSidNidPairs()");
        }
        if (oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs.sidNid.length != 20) {
            return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.sidNidPairsToByteArr(oEM_RIL_CDMA_NAM_SID_NID_NAM_Pairs, OEM_RIL_REQUEST_CDMA_SET_SID_NID_PAIRS, str), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_SID_NID_PAIRS), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setPrefVoiceSo(rde_pref_voice_so_type rde_pref_voice_so_type, String str, Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "setPrefVoiceSo()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_PREF_VOICE_SO);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 30;
        oEM_RIL_RDE_Data.recordNum = 0;
        oEM_RIL_RDE_Data.offset = 0;
        oEM_RIL_RDE_Data.dataObj = rde_pref_voice_so_type;
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setPrl(byte[] bArr, Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "setPrl()");
        }
        if (bArr == null) {
            return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_GENERIC_FAILURE;
        }
        rde_roaming_list_type rde_roaming_list_type = new rde_roaming_list_type();
        rde_roaming_list_type.setRoamingList(bArr);
        invokeOemRilRequestRaw(OemCdmaDataConverter.roamingListToByteArr(rde_roaming_list_type, OEM_RIL_REQUEST_CDMA_SET_PRL, sDefaultSpcCode), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_PRL), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setSO68Enabled(boolean z, Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "setSO68Enabled()");
        }
        OEM_RIL_CDMA_Errno oEM_RIL_CDMA_Errno = OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_CDMA_SO68_ENABLED);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 48;
        oEM_RIL_RDE_Data.dataObj = new rde_obj_type(z ? (byte) 1 : (byte) 0);
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setServiceOption(OEM_RIL_CDMA_ServiceOption oEM_RIL_CDMA_ServiceOption, String str, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "setServiceOption()");
        }
        invokeOemRilRequestRaw(OemCdmaDataConverter.serviceOptionToByteArr(oEM_RIL_CDMA_ServiceOption, OEM_RIL_REQUEST_CDMA_FIELD_TEST_SET_SERVICE_OPTION, str), this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_FIELD_TEST_SET_SERVICE_OPTION), handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setSo73Cop0Option(boolean z, Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "setSo73Cop0Option - enabled: " + z);
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_SO73COP0_OPTION);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = OEM_RIL_RDE_Data.RDE_EFS_SO73_COP0_SUPPORTED_I;
        oEM_RIL_RDE_Data.dataObj = new rde_obj_type(z ? (byte) 1 : (byte) 0);
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setSo73Option(boolean z, Handler handler) {
        if (DEBUG) {
            Log.v(TAG, "setSo73Option - enabled: " + z);
        }
        Message obtainMessage = this.mMsgHandler.obtainMessage(OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, RdeRequestId.RDEREQ_SET_SO73_OPTION);
        OEM_RIL_RDE_Data oEM_RIL_RDE_Data = new OEM_RIL_RDE_Data();
        oEM_RIL_RDE_Data.elementID = 45;
        oEM_RIL_RDE_Data.dataObj = new rde_obj_type(z ? (byte) 1 : (byte) 0);
        invokeOemRilRequestRaw(OemCdmaDataConverter.rdeDataToByteArr(oEM_RIL_RDE_Data, OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, sDefaultSpcCode), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }
}
