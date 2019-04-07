package com.android.internal.telephony.cdma;

import android.app.ActivityManagerNative;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.provider.Telephony.Carriers;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.cdma.CdmaCellLocation;
import android.text.TextUtils;
import android.util.Log;
import com.android.ims.ImsManager;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DctConstants.Activity;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone.DataActivityState;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.SubscriptionInfoUpdater;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.uicc.IccException;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data;

public class CDMAPhone extends PhoneBase {
    static final int CANCEL_ECM_TIMER = 1;
    private static final boolean DBG = true;
    private static final int DEFAULT_ECM_EXIT_TIMER_VALUE = 300000;
    private static final int INVALID_SYSTEM_SELECTION_CODE = -1;
    private static final String IS683A_FEATURE_CODE = "*228";
    private static final int IS683A_FEATURE_CODE_NUM_DIGITS = 4;
    private static final int IS683A_SYS_SEL_CODE_NUM_DIGITS = 2;
    private static final int IS683A_SYS_SEL_CODE_OFFSET = 4;
    private static final int IS683_CONST_1900MHZ_A_BLOCK = 2;
    private static final int IS683_CONST_1900MHZ_B_BLOCK = 3;
    private static final int IS683_CONST_1900MHZ_C_BLOCK = 4;
    private static final int IS683_CONST_1900MHZ_D_BLOCK = 5;
    private static final int IS683_CONST_1900MHZ_E_BLOCK = 6;
    private static final int IS683_CONST_1900MHZ_F_BLOCK = 7;
    private static final int IS683_CONST_800MHZ_A_BAND = 0;
    private static final int IS683_CONST_800MHZ_B_BAND = 1;
    static final String LOG_TAG = "CDMAPhone";
    public static final String PROPERTY_CDMA_HOME_OPERATOR_NUMERIC = "ro.cdma.home.operator.numeric";
    static final int RESTART_ECM_TIMER = 0;
    private static final boolean VDBG = false;
    private static final String VM_NUMBER_CDMA = "vm_number_key_cdma";
    private static Pattern pOtaSpNumSchema = Pattern.compile("[,\\s]+");
    CdmaCallTracker mCT;
    protected String mCarrierOtaSpNumSchema;
    CdmaSubscriptionSourceManager mCdmaSSM;
    int mCdmaSubscriptionSource = -1;
    private Registrant mEcmExitRespRegistrant;
    private final RegistrantList mEcmTimerResetRegistrants = new RegistrantList();
    private final RegistrantList mEriFileLoadedRegistrants = new RegistrantList();
    EriManager mEriManager;
    private String mEsn;
    private Runnable mExitEcmRunnable = new Runnable() {
        public void run() {
            CDMAPhone.this.exitEmergencyCallbackMode();
        }
    };
    protected String mImei;
    protected String mImeiSv;
    protected boolean mIsPhoneInEcmState;
    private String mMeid;
    ArrayList<CdmaMmiCode> mPendingMmis = new ArrayList();
    Registrant mPostDialHandler;
    RuimPhoneBookInterfaceManager mRuimPhoneBookInterfaceManager;
    CdmaServiceStateTracker mSST;
    PhoneSubInfo mSubInfo;
    private String mVmNumber = null;
    WakeLock mWakeLock;

    /* renamed from: com.android.internal.telephony.cdma.CDMAPhone$2 */
    static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$Activity = new int[Activity.values().length];
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.RETRYING.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.FAILED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.IDLE.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTED.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.DISCONNECTING.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.SCANNING.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DATAIN.ordinal()] = 1;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DATAOUT.ordinal()] = 2;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DATAINANDOUT.ordinal()] = 3;
            } catch (NoSuchFieldError e10) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DORMANT.ordinal()] = 4;
            } catch (NoSuchFieldError e11) {
            }
        }
    }

    public CDMAPhone(Context context, CommandsInterface commandsInterface, PhoneNotifier phoneNotifier, int i) {
        super("CDMA", phoneNotifier, context, commandsInterface, false, i);
        initSstIcc();
        init(context, phoneNotifier);
    }

    private static boolean checkOtaSpNumBasedOnSysSelCode(int i, String[] strArr) {
        try {
            int parseInt = Integer.parseInt(strArr[1]);
            int i2 = 0;
            while (i2 < parseInt) {
                if (!(TextUtils.isEmpty(strArr[i2 + 2]) || TextUtils.isEmpty(strArr[i2 + 3]))) {
                    int parseInt2 = Integer.parseInt(strArr[i2 + 2]);
                    int parseInt3 = Integer.parseInt(strArr[i2 + 3]);
                    if (i >= parseInt2 && i <= parseInt3) {
                        return true;
                    }
                }
                i2++;
            }
            return false;
        } catch (NumberFormatException e) {
            Rlog.e(LOG_TAG, "checkOtaSpNumBasedOnSysSelCode, error", e);
            return false;
        }
    }

    private static int extractSelCodeFromOtaSpNum(String str) {
        int length = str.length();
        int i = -1;
        if (str.regionMatches(0, IS683A_FEATURE_CODE, 0, 4) && length >= 6) {
            i = Integer.parseInt(str.substring(4, 6));
        }
        Rlog.d(LOG_TAG, "extractSelCodeFromOtaSpNum " + i);
        return i;
    }

    private int getStoredVoiceMessageCount() {
        return PreferenceManager.getDefaultSharedPreferences(this.mContext).getInt(PhoneBase.VM_COUNT + getSubId(), 0);
    }

    private void handleCdmaSubscriptionSource(int i) {
        if (i != this.mCdmaSubscriptionSource) {
            this.mCdmaSubscriptionSource = i;
            if (i == 1) {
                sendMessage(obtainMessage(23));
            }
        }
    }

    private void handleEnterEmergencyCallbackMode(Message message) {
        Rlog.d(LOG_TAG, "handleEnterEmergencyCallbackMode,mIsPhoneInEcmState= " + this.mIsPhoneInEcmState);
        if (!this.mIsPhoneInEcmState) {
            this.mIsPhoneInEcmState = true;
            sendEmergencyCallbackModeChange();
            super.setSystemProperty("ril.cdma.inecmmode", "true");
            postDelayed(this.mExitEcmRunnable, SystemProperties.getLong("ro.cdma.ecmexittimer", 300000));
            this.mWakeLock.acquire();
        }
    }

    private void handleExitEmergencyCallbackMode(Message message) {
        AsyncResult asyncResult = (AsyncResult) message.obj;
        Rlog.d(LOG_TAG, "handleExitEmergencyCallbackMode,ar.exception , mIsPhoneInEcmState " + asyncResult.exception + this.mIsPhoneInEcmState);
        removeCallbacks(this.mExitEcmRunnable);
        if (this.mEcmExitRespRegistrant != null) {
            this.mEcmExitRespRegistrant.notifyRegistrant(asyncResult);
        }
        if (asyncResult.exception == null) {
            if (this.mIsPhoneInEcmState) {
                this.mIsPhoneInEcmState = false;
                super.setSystemProperty("ril.cdma.inecmmode", "false");
            }
            sendEmergencyCallbackModeChange();
            this.mDcTracker.setInternalDataEnabled(true);
        }
    }

    private boolean isCarrierOtaSpNum(String str) {
        int extractSelCodeFromOtaSpNum = extractSelCodeFromOtaSpNum(str);
        if (extractSelCodeFromOtaSpNum == -1) {
            return false;
        }
        if (TextUtils.isEmpty(this.mCarrierOtaSpNumSchema)) {
            Rlog.d(LOG_TAG, "isCarrierOtaSpNum,ota schema pattern empty");
            return false;
        }
        Matcher matcher = pOtaSpNumSchema.matcher(this.mCarrierOtaSpNumSchema);
        Rlog.d(LOG_TAG, "isCarrierOtaSpNum,schema" + this.mCarrierOtaSpNumSchema);
        if (matcher.find()) {
            String[] split = pOtaSpNumSchema.split(this.mCarrierOtaSpNumSchema);
            if (TextUtils.isEmpty(split[0]) || !split[0].equals("SELC")) {
                if (TextUtils.isEmpty(split[0]) || !split[0].equals("FC")) {
                    Rlog.d(LOG_TAG, "isCarrierOtaSpNum,ota schema not supported" + split[0]);
                    return false;
                }
                if (str.regionMatches(0, split[2], 0, Integer.parseInt(split[1]))) {
                    return true;
                }
                Rlog.d(LOG_TAG, "isCarrierOtaSpNum,not otasp number");
                return false;
            } else if (extractSelCodeFromOtaSpNum != -1) {
                return checkOtaSpNumBasedOnSysSelCode(extractSelCodeFromOtaSpNum, split);
            } else {
                Rlog.d(LOG_TAG, "isCarrierOtaSpNum,sysSelCodeInt is invalid");
                return false;
            }
        }
        Rlog.d(LOG_TAG, "isCarrierOtaSpNum,ota schema pattern not right" + this.mCarrierOtaSpNumSchema);
        return false;
    }

    private static boolean isIs683OtaSpDialStr(String str) {
        if (str.length() == 4) {
            return str.equals(IS683A_FEATURE_CODE);
        } else {
            switch (extractSelCodeFromOtaSpNum(str)) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    return true;
                default:
                    return false;
            }
        }
    }

    private void storeVoiceMailNumber(String str) {
        Editor edit = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        edit.putString(VM_NUMBER_CDMA + getSubId(), str);
        edit.apply();
    }

    private void updateVoiceMail() {
        setVoiceMessageCount(getStoredVoiceMessageCount());
    }

    public void acceptCall(int i) throws CallStateException {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || !imsPhone.getRingingCall().isRinging()) {
            this.mCT.acceptCall();
        } else {
            imsPhone.acceptCall(i);
        }
    }

    public void activateCellBroadcastSms(int i, Message message) {
        Rlog.e(LOG_TAG, "[CDMAPhone] activateCellBroadcastSms() is obsolete; use SmsManager");
        message.sendToTarget();
    }

    public boolean canConference() {
        if (this.mImsPhone != null && this.mImsPhone.canConference()) {
            return true;
        }
        Rlog.e(LOG_TAG, "canConference: not possible in CDMA");
        return false;
    }

    public boolean canTransfer() {
        Rlog.e(LOG_TAG, "canTransfer: not possible in CDMA");
        return false;
    }

    public void clearDisconnected() {
        this.mCT.clearDisconnected();
    }

    public void conference() {
        if (this.mImsPhone == null || !this.mImsPhone.canConference()) {
            Rlog.e(LOG_TAG, "conference: not possible in CDMA");
            return;
        }
        log("conference() - delegated to IMS phone");
        this.mImsPhone.conference();
    }

    public void deflectCall(String str) throws CallStateException {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || !imsPhone.getRingingCall().isRinging()) {
            throw new CallStateException("Deflect call NOT supported in CDMA!");
        }
        imsPhone.deflectCall(str);
    }

    public Connection dial(String str, int i) throws CallStateException {
        return dial(str, i, null);
    }

    public Connection dial(String str, int i, Bundle bundle) throws CallStateException {
        CallStateException callStateException;
        boolean z = true;
        ImsPhone imsPhone = this.mImsPhone;
        boolean z2 = ImsManager.isVolteEnabledByPlatform(this.mContext) && ImsManager.isEnhanced4gLteModeSettingEnabledByUser(this.mContext) && ImsManager.isNonTtyOrTtyOnVolteEnabled(this.mContext) && imsPhone != null && imsPhone.isVolteEnabled() && imsPhone.getServiceState().getState() == 0;
        if (!(imsPhone != null && PhoneNumberUtils.isEmergencyNumber(str) && this.mContext.getResources().getBoolean(17956996) && ImsManager.isNonTtyOrTtyOnVolteEnabled(this.mContext) && imsPhone.getServiceState().getState() != 3)) {
            z = false;
        }
        Rlog.d(LOG_TAG, "imsUseEnabled = " + z2 + ", useImsForEmergency = " + z);
        if (z2 || z) {
            try {
                Rlog.d(LOG_TAG, "Trying IMS PS call");
                return imsPhone.dial(str, i, bundle);
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "IMS PS call exception " + e + "imsUseEnabled =" + z2 + ", imsPhone =" + imsPhone);
                if (!ImsPhone.CS_FALLBACK.equals(e.getMessage())) {
                    callStateException = new CallStateException(e.getMessage());
                    callStateException.setStackTrace(e.getStackTrace());
                    throw callStateException;
                }
            }
        }
        if (imsPhone != null && imsPhone.isUtEnabled() && str.endsWith("#")) {
            try {
                Rlog.d(LOG_TAG, "Trying IMS call with UT enabled");
                return imsPhone.dial(str, i, bundle);
            } catch (CallStateException e2) {
                Rlog.d(LOG_TAG, "IMS call UT enable exception " + e2);
                if (!ImsPhone.CS_FALLBACK.equals(e2.getMessage())) {
                    callStateException = new CallStateException(e2.getMessage());
                    callStateException.setStackTrace(e2.getStackTrace());
                    throw callStateException;
                }
            }
        }
        Rlog.d(LOG_TAG, "Trying (non-IMS) CS call");
        return dialInternal(str, null, i);
    }

    public Connection dial(String str, UUSInfo uUSInfo, int i) throws CallStateException {
        throw new CallStateException("Sending UUS information NOT supported in CDMA!");
    }

    /* Access modifiers changed, original: 0000 */
    public Connection dial(String str, UUSInfo uUSInfo, int i, Bundle bundle) throws CallStateException {
        return dial(str, uUSInfo, i, null);
    }

    /* Access modifiers changed, original: protected */
    public Connection dialInternal(String str, UUSInfo uUSInfo, int i) throws CallStateException {
        return this.mCT.dial(PhoneNumberUtils.stripSeparators(str));
    }

    public void disableLocationUpdates() {
        this.mSST.disableLocationUpdates();
    }

    public void dispose() {
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();
            log("dispose");
            unregisterForRuimRecordEvents();
            this.mCi.unregisterForAvailable(this);
            this.mCi.unregisterForOffOrNotAvailable(this);
            this.mCi.unregisterForOn(this);
            this.mSST.unregisterForNetworkAttached(this);
            this.mCi.unSetOnSuppServiceNotification(this);
            this.mCi.unregisterForExitEmergencyCallbackMode(this);
            removeCallbacks(this.mExitEcmRunnable);
            this.mPendingMmis.clear();
            this.mCT.dispose();
            this.mDcTracker.dispose();
            this.mSST.dispose();
            this.mCdmaSSM.dispose(this);
            this.mRuimPhoneBookInterfaceManager.dispose();
            this.mSubInfo.dispose();
            this.mEriManager.dispose();
        }
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("CDMAPhone extends:");
        super.dump(fileDescriptor, printWriter, strArr);
        printWriter.println(" mVmNumber=" + this.mVmNumber);
        printWriter.println(" mCT=" + this.mCT);
        printWriter.println(" mSST=" + this.mSST);
        printWriter.println(" mCdmaSSM=" + this.mCdmaSSM);
        printWriter.println(" mPendingMmis=" + this.mPendingMmis);
        printWriter.println(" mRuimPhoneBookInterfaceManager=" + this.mRuimPhoneBookInterfaceManager);
        printWriter.println(" mCdmaSubscriptionSource=" + this.mCdmaSubscriptionSource);
        printWriter.println(" mSubInfo=" + this.mSubInfo);
        printWriter.println(" mEriManager=" + this.mEriManager);
        printWriter.println(" mWakeLock=" + this.mWakeLock);
        printWriter.println(" mIsPhoneInEcmState=" + this.mIsPhoneInEcmState);
        printWriter.println(" mCarrierOtaSpNumSchema=" + this.mCarrierOtaSpNumSchema);
        printWriter.println(" getCdmaEriIconIndex()=" + getCdmaEriIconIndex());
        printWriter.println(" getCdmaEriIconMode()=" + getCdmaEriIconMode());
        printWriter.println(" getCdmaEriText()=" + getCdmaEriText());
        printWriter.println(" isMinInfoReady()=" + isMinInfoReady());
        printWriter.println(" isCspPlmnEnabled()=" + isCspPlmnEnabled());
    }

    public void enableEnhancedVoicePrivacy(boolean z, Message message) {
        this.mCi.setPreferredVoicePrivacy(z, message);
    }

    public void enableLocationUpdates() {
        this.mSST.enableLocationUpdates();
    }

    public void exitEmergencyCallbackMode() {
        if (this.mWakeLock.isHeld()) {
            this.mWakeLock.release();
        }
        this.mCi.exitEmergencyCallbackMode(obtainMessage(26));
    }

    public void explicitCallTransfer() {
        Rlog.e(LOG_TAG, "explicitCallTransfer: not possible in CDMA");
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        Rlog.d(LOG_TAG, "CDMAPhone finalized");
        if (this.mWakeLock.isHeld()) {
            Rlog.e(LOG_TAG, "UNEXPECTED; mWakeLock is held when finalizing.");
            this.mWakeLock.release();
        }
    }

    public void getAvailableNetworks(Message message) {
        Rlog.e(LOG_TAG, "getAvailableNetworks: not possible in CDMA");
        if (message != null) {
            AsyncResult.forMessage(message).exception = new CommandException(Error.REQUEST_NOT_SUPPORTED);
            message.sendToTarget();
        }
    }

    public CdmaCall getBackgroundCall() {
        return this.mCT.mBackgroundCall;
    }

    public ServiceState getBaseServiceState() {
        return this.mSST != null ? this.mSST.mSS : new ServiceState();
    }

    public void getCallBarring(String str, Message message) {
        Rlog.e(LOG_TAG, "getCallBarring: not possible in CDMA");
    }

    public void getCallBarring(String str, String str2, int i, Message message) {
        Rlog.e(LOG_TAG, "getCallBarring: not possible in CDMA");
    }

    public void getCallBarring(String str, String str2, Message message) {
        Rlog.e(LOG_TAG, "getCallBarring: not possible in CDMA");
    }

    public boolean getCallForwardingIndicator() {
        Rlog.e(LOG_TAG, "getCallForwardingIndicator: not possible in CDMA");
        return false;
    }

    public void getCallForwardingOption(int i, int i2, Message message) {
        Rlog.e(LOG_TAG, "getCallForwardingOption: not possible in CDMA");
    }

    public void getCallForwardingOption(int i, Message message) {
        Rlog.e(LOG_TAG, "getCallForwardingOption: not possible in CDMA");
    }

    public void getCallForwardingUncondTimerOption(int i, Message message) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null && (imsPhone.getServiceState().getState() == 0 || imsPhone.isUtEnabled())) {
            imsPhone.getCallForwardingOption(i, message);
        } else if (message != null) {
            AsyncResult.forMessage(message, null, new CommandException(Error.GENERIC_FAILURE));
            message.sendToTarget();
        }
    }

    public CallTracker getCallTracker() {
        return this.mCT;
    }

    public void getCallWaiting(int i, Message message) {
        this.mCi.queryCallWaiting(i, message);
    }

    public void getCallWaiting(Message message) {
        this.mCi.queryCallWaiting(1, message);
    }

    public int getCdmaEriIconIndex() {
        return getServiceState().getCdmaEriIconIndex();
    }

    public int getCdmaEriIconMode() {
        return getServiceState().getCdmaEriIconMode();
    }

    public String getCdmaEriText() {
        return this.mEriManager.getCdmaEriText(getServiceState().getCdmaRoamingIndicator(), getServiceState().getCdmaDefaultRoamingIndicator());
    }

    public String getCdmaMin() {
        return this.mSST.getCdmaMin();
    }

    public String getCdmaPrlVersion() {
        return this.mSST.getPrlVersion();
    }

    public void getCellBroadcastSmsConfig(Message message) {
        Rlog.e(LOG_TAG, "[CDMAPhone] getCellBroadcastSmsConfig() is obsolete; use SmsManager");
        message.sendToTarget();
    }

    public CellLocation getCellLocation() {
        CellLocation cellLocation = this.mSST.mCellLoc;
        if (Secure.getInt(getContext().getContentResolver(), "location_mode", 0) != 0) {
            return cellLocation;
        }
        CdmaCellLocation cdmaCellLocation = new CdmaCellLocation();
        cdmaCellLocation.setCellLocationData(cellLocation.getBaseStationId(), Integer.MAX_VALUE, Integer.MAX_VALUE, cellLocation.getSystemId(), cellLocation.getNetworkId());
        return cdmaCellLocation;
    }

    public DataActivityState getDataActivityState() {
        DataActivityState dataActivityState = DataActivityState.NONE;
        if (this.mSST.getCurrentDataConnectionState() != 0) {
            return dataActivityState;
        }
        switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$DctConstants$Activity[this.mDcTracker.getActivity().ordinal()]) {
            case 1:
                return DataActivityState.DATAIN;
            case 2:
                return DataActivityState.DATAOUT;
            case 3:
                return DataActivityState.DATAINANDOUT;
            case 4:
                return DataActivityState.DORMANT;
            default:
                return DataActivityState.NONE;
        }
    }

    public void getDataCallList(Message message) {
        this.mCi.getDataCallList(message);
    }

    public DataState getDataConnectionState(String str) {
        Object obj = DataState.DISCONNECTED;
        if (this.mSST == null) {
            obj = DataState.DISCONNECTED;
        } else if (this.mSST.getCurrentDataConnectionState() == 0 || !this.mOosIsDisconnect) {
            if (this.mDcTracker.isApnTypeEnabled(str) && this.mDcTracker.isApnTypeActive(str)) {
                switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$DctConstants$State[this.mDcTracker.getState(str).ordinal()]) {
                    case 1:
                    case 2:
                    case 3:
                        obj = DataState.DISCONNECTED;
                        break;
                    case 4:
                    case 5:
                        if (this.mCT.mState != PhoneConstants.State.IDLE && !this.mSST.isConcurrentVoiceAndDataAllowed()) {
                            obj = DataState.SUSPENDED;
                            break;
                        }
                        obj = DataState.CONNECTED;
                        break;
                    case 6:
                    case 7:
                        obj = DataState.CONNECTING;
                        break;
                }
            }
            obj = DataState.DISCONNECTED;
        } else {
            obj = DataState.DISCONNECTED;
            log("getDataConnectionState: Data is Out of Service. ret = " + obj);
        }
        log("getDataConnectionState apnType=" + str + " ret=" + obj);
        return obj;
    }

    public boolean getDataEnabled() {
        return this.mDcTracker.getDataEnabled();
    }

    public boolean getDataRoamingEnabled() {
        return this.mDcTracker.getDataOnRoamingEnabled();
    }

    public String getDeviceId() {
        String meid = getMeid();
        if (meid != null && !meid.matches("^0*$")) {
            return meid;
        }
        Rlog.d(LOG_TAG, "getDeviceId(): MEID is not initialized use ESN");
        return getEsn();
    }

    public String getDeviceSvn() {
        Rlog.d(LOG_TAG, "getDeviceSvn(): return 0");
        return "0";
    }

    public void getEnhancedVoicePrivacy(Message message) {
        this.mCi.getPreferredVoicePrivacy(message);
    }

    public String getEsn() {
        return this.mEsn;
    }

    public CdmaCall getForegroundCall() {
        return this.mCT.mForegroundCall;
    }

    public String getGroupIdLevel1() {
        Rlog.e(LOG_TAG, "GID1 is not available in CDMA");
        return null;
    }

    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return this.mRuimPhoneBookInterfaceManager;
    }

    public String getIccSerialNumber() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords == null) {
            iccRecords = this.mUiccController.getIccRecords(this.mPhoneId, 1);
        }
        return iccRecords != null ? iccRecords.getIccId() : null;
    }

    public String getImei() {
        Rlog.e(LOG_TAG, "getImei() called for CDMAPhone");
        return this.mImei;
    }

    public String getLine1AlphaTag() {
        Rlog.e(LOG_TAG, "getLine1AlphaTag: not possible in CDMA");
        return null;
    }

    public String getLine1Number() {
        return this.mSST.getMdnNumber();
    }

    public String getMeid() {
        return this.mMeid;
    }

    public boolean getMute() {
        return this.mCT.getMute();
    }

    public String getNai() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (Log.isLoggable(LOG_TAG, 2)) {
            Rlog.v(LOG_TAG, "IccRecords is " + iccRecords);
        }
        return iccRecords != null ? iccRecords.getNAI() : null;
    }

    public void getNeighboringCids(Message message) {
        if (message != null) {
            AsyncResult.forMessage(message).exception = new CommandException(Error.REQUEST_NOT_SUPPORTED);
            message.sendToTarget();
        }
    }

    public void getOutgoingCallerIdDisplay(Message message) {
        Rlog.e(LOG_TAG, "getOutgoingCallerIdDisplay: not possible in CDMA");
    }

    public List<? extends MmiCode> getPendingMmiCodes() {
        return this.mPendingMmis;
    }

    public PhoneSubInfo getPhoneSubInfo() {
        return this.mSubInfo;
    }

    public int getPhoneType() {
        return 2;
    }

    public Call getRingingCall() {
        ImsPhone imsPhone = this.mImsPhone;
        return (this.mCT.mRingingCall == null || !this.mCT.mRingingCall.isRinging()) ? imsPhone != null ? imsPhone.getRingingCall() : this.mCT.mRingingCall : this.mCT.mRingingCall;
    }

    public ServiceState getServiceState() {
        if ((this.mSST != null && this.mSST.mSS.getState() == 0) || this.mImsPhone == null) {
            return this.mSST != null ? this.mSST.mSS : new ServiceState();
        } else {
            return ServiceState.mergeServiceStates(this.mSST == null ? new ServiceState() : this.mSST.mSS, this.mImsPhone.getServiceState());
        }
    }

    public ServiceStateTracker getServiceStateTracker() {
        return this.mSST;
    }

    public PhoneConstants.State getState() {
        if (this.mImsPhone != null) {
            PhoneConstants.State state = this.mImsPhone.getState();
            if (state != PhoneConstants.State.IDLE) {
                return state;
            }
        }
        return this.mCT.mState;
    }

    public String getSubscriberId() {
        return this.mSST.getImsi();
    }

    public String getSystemProperty(String str, String str2) {
        return super.getSystemProperty(str, str2);
    }

    /* Access modifiers changed, original: protected */
    public UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(this.mPhoneId, 2);
    }

    public String getVoiceMailAlphaTag() {
        return ("" == null || "".length() == 0) ? this.mContext.getText(17039364).toString() : "";
    }

    public String getVoiceMailNumber() {
        CharSequence string = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(VM_NUMBER_CDMA + getSubId(), null);
        if (TextUtils.isEmpty(string)) {
            String[] stringArray = getContext().getResources().getStringArray(17236028);
            if (stringArray != null && stringArray.length > 0) {
                for (int i = 0; i < stringArray.length; i++) {
                    if (!TextUtils.isEmpty(stringArray[i])) {
                        String[] split = stringArray[i].split(";");
                        if (split != null && split.length > 0) {
                            if (split.length != 1) {
                                if (split.length == 2 && !TextUtils.isEmpty(split[1]) && split[1].equalsIgnoreCase(getGroupIdLevel1())) {
                                    string = split[0];
                                    break;
                                }
                            }
                            string = split[0];
                        }
                    }
                }
            }
        }
        return TextUtils.isEmpty(string) ? getContext().getResources().getBoolean(17956955) ? getLine1Number() : "*86" : string;
    }

    public boolean handleInCallMmiCommands(String str) {
        Rlog.e(LOG_TAG, "method handleInCallMmiCommands is NOT supported in CDMA!");
        return false;
    }

    public void handleMessage(Message message) {
        switch (message.what) {
            case 16:
            case 17:
                super.handleMessage(message);
                return;
            default:
                if (this.mIsTheCurrentActivePhone) {
                    AsyncResult asyncResult;
                    switch (message.what) {
                        case 1:
                            this.mCi.getBasebandVersion(obtainMessage(6));
                            this.mCi.getDeviceIdentity(obtainMessage(21));
                            return;
                        case 2:
                            Rlog.d(LOG_TAG, "Event EVENT_SSN Received");
                            return;
                        case 5:
                            Rlog.d(LOG_TAG, "Event EVENT_RADIO_ON Received");
                            handleCdmaSubscriptionSource(this.mCdmaSSM.getCdmaSubscriptionSource());
                            return;
                        case 6:
                            asyncResult = (AsyncResult) message.obj;
                            if (asyncResult.exception == null) {
                                Rlog.d(LOG_TAG, "Baseband version: " + asyncResult.result);
                                setSystemProperty("gsm.version.baseband", (String) asyncResult.result);
                                return;
                            }
                            return;
                        case 8:
                            Rlog.d(LOG_TAG, "Event EVENT_RADIO_OFF_OR_NOT_AVAILABLE Received");
                            return;
                        case 19:
                            Rlog.d(LOG_TAG, "Event EVENT_REGISTERED_TO_NETWORK Received");
                            return;
                        case 20:
                            asyncResult = (AsyncResult) message.obj;
                            if (IccException.class.isInstance(asyncResult.exception)) {
                                storeVoiceMailNumber(this.mVmNumber);
                                asyncResult.exception = null;
                            }
                            Message message2 = (Message) asyncResult.userObj;
                            if (message2 != null) {
                                AsyncResult.forMessage(message2, asyncResult.result, asyncResult.exception);
                                message2.sendToTarget();
                                return;
                            }
                            return;
                        case 21:
                            asyncResult = (AsyncResult) message.obj;
                            if (asyncResult.exception == null) {
                                String[] strArr = (String[]) asyncResult.result;
                                this.mImei = strArr[0];
                                this.mImeiSv = strArr[1];
                                this.mEsn = strArr[2];
                                this.mMeid = strArr[3];
                                return;
                            }
                            return;
                        case 22:
                            Rlog.d(LOG_TAG, "Event EVENT_RUIM_RECORDS_LOADED Received");
                            updateCurrentCarrierInProvider();
                            log("notifyMessageWaitingChanged");
                            this.mNotifier.notifyMessageWaitingChanged(this);
                            updateVoiceMail();
                            return;
                        case 23:
                            Rlog.d(LOG_TAG, "Event EVENT_NV_READY Received");
                            prepareEri();
                            log("notifyMessageWaitingChanged");
                            this.mNotifier.notifyMessageWaitingChanged(this);
                            updateVoiceMail();
                            SubscriptionInfoUpdater subscriptionInfoUpdater = PhoneFactory.getSubscriptionInfoUpdater();
                            if (subscriptionInfoUpdater != null) {
                                subscriptionInfoUpdater.updateSubIdForNV(this.mPhoneId);
                                return;
                            }
                            return;
                        case 25:
                            handleEnterEmergencyCallbackMode(message);
                            return;
                        case 26:
                            handleExitEmergencyCallbackMode(message);
                            return;
                        case OEM_RIL_RDE_Data.RDE_NV_OTKSL_I /*27*/:
                            Rlog.d(LOG_TAG, "EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED");
                            handleCdmaSubscriptionSource(this.mCdmaSSM.getCdmaSubscriptionSource());
                            return;
                        default:
                            super.handleMessage(message);
                            return;
                    }
                }
                Rlog.e(LOG_TAG, "Received message " + message + "[" + message.what + "] while being destroyed. Ignoring.");
                return;
        }
    }

    public boolean handlePinMmi(String str) {
        CdmaMmiCode newFromDialString = CdmaMmiCode.newFromDialString(str, this, (UiccCardApplication) this.mUiccApplication.get());
        if (newFromDialString == null) {
            Rlog.e(LOG_TAG, "Mmi is NULL!");
            return false;
        } else if (newFromDialString.isPinPukCommand()) {
            this.mPendingMmis.add(newFromDialString);
            this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, newFromDialString, null));
            newFromDialString.processCode();
            return true;
        } else {
            Rlog.e(LOG_TAG, "Unrecognized mmi!");
            return false;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void handleTimerInEmergencyCallbackMode(int i) {
        switch (i) {
            case 0:
                postDelayed(this.mExitEcmRunnable, SystemProperties.getLong("ro.cdma.ecmexittimer", 300000));
                this.mEcmTimerResetRegistrants.notifyResult(Boolean.FALSE);
                return;
            case 1:
                removeCallbacks(this.mExitEcmRunnable);
                this.mEcmTimerResetRegistrants.notifyResult(Boolean.TRUE);
                return;
            default:
                Rlog.e(LOG_TAG, "handleTimerInEmergencyCallbackMode, unsupported action " + i);
                return;
        }
    }

    /* Access modifiers changed, original: protected */
    public void init(Context context, PhoneNotifier phoneNotifier) {
        this.mCi.setPhoneType(2);
        this.mCT = new CdmaCallTracker(this);
        this.mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(context, this.mCi, this, 27, null);
        this.mDcTracker = new DcTracker(this);
        this.mRuimPhoneBookInterfaceManager = new RuimPhoneBookInterfaceManager(this);
        this.mSubInfo = new PhoneSubInfo(this);
        this.mEriManager = new EriManager(this, context, 0);
        this.mCi.registerForAvailable(this, 1, null);
        this.mCi.registerForOffOrNotAvailable(this, 8, null);
        this.mCi.registerForOn(this, 5, null);
        this.mCi.setOnSuppServiceNotification(this, 2, null);
        this.mSST.registerForNetworkAttached(this, 19, null);
        this.mCi.setEmergencyCallbackMode(this, 25, null);
        this.mCi.registerForExitEmergencyCallbackMode(this, 26, null);
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, LOG_TAG);
        SystemProperties.set("gsm.current.phone-type", Integer.toString(2));
        this.mIsPhoneInEcmState = SystemProperties.get("ril.cdma.inecmmode", "false").equals("true");
        if (this.mIsPhoneInEcmState) {
            this.mCi.exitEmergencyCallbackMode(obtainMessage(26));
        }
        this.mCarrierOtaSpNumSchema = SystemProperties.get("ro.cdma.otaspnumschema", "");
        String str = SystemProperties.get("ro.cdma.home.operator.alpha");
        String str2 = SystemProperties.get(PROPERTY_CDMA_HOME_OPERATOR_NUMERIC);
        log("init: operatorAlpha='" + str + "' operatorNumeric='" + str2 + "'");
        if (this.mUiccController.getUiccCardApplication(this.mPhoneId, 1) == null) {
            log("init: APP_FAM_3GPP == NULL");
            if (!TextUtils.isEmpty(str)) {
                log("init: set 'gsm.sim.operator.alpha' to operator='" + str + "'");
                setSystemProperty("gsm.sim.operator.alpha", str);
            }
            if (!TextUtils.isEmpty(str2)) {
                log("init: set 'gsm.sim.operator.numeric' to operator='" + str2 + "'");
                log("update icc_operator_numeric=" + str2);
                setSystemProperty("gsm.sim.operator.numeric", str2);
                SubscriptionController.getInstance().setMccMnc(str2, getSubId());
            }
            setIsoCountryProperty(str2);
        }
        updateCurrentCarrierInProvider(str2);
    }

    /* Access modifiers changed, original: protected */
    public void initSstIcc() {
        this.mSST = new CdmaServiceStateTracker(this);
    }

    public boolean isEriFileLoaded() {
        return this.mEriManager.isEriFileLoaded();
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isInCall() {
        return getForegroundCall().getState().isAlive() || getBackgroundCall().getState().isAlive() || getRingingCall().getState().isAlive();
    }

    public boolean isInEcm() {
        return this.mIsPhoneInEcmState;
    }

    public boolean isInEmergencyCall() {
        return this.mCT.isInEmergencyCall();
    }

    public boolean isMinInfoReady() {
        return this.mSST.isMinInfoReady();
    }

    public boolean isOtaSpNumber(String str) {
        boolean z = false;
        String extractNetworkPortionAlt = PhoneNumberUtils.extractNetworkPortionAlt(str);
        if (extractNetworkPortionAlt != null) {
            z = isIs683OtaSpDialStr(extractNetworkPortionAlt);
            if (!z) {
                z = isCarrierOtaSpNum(extractNetworkPortionAlt);
            }
        }
        Rlog.d(LOG_TAG, "isOtaSpNumber " + z);
        return z;
    }

    public boolean isUtEnabled() {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null) {
            return imsPhone.isUtEnabled();
        }
        Rlog.d(LOG_TAG, "isUtEnabled: called for CDMA");
        return false;
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str, Exception exception) {
        Rlog.e(LOG_TAG, str, exception);
    }

    public boolean needsOtaServiceProvisioning() {
        return this.mSST.getOtasp() != 3;
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyDisconnect(Connection connection) {
        this.mDisconnectRegistrants.notifyResult(connection);
        this.mNotifier.notifyDisconnectCause(connection.getDisconnectCause(), connection.getPreciseDisconnectCause());
    }

    public void notifyEcbmTimerReset(Boolean bool) {
        this.mEcmTimerResetRegistrants.notifyResult(bool);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyLocationChanged() {
        this.mNotifier.notifyCellLocation(this);
    }

    public void notifyNewRingingConnection(Connection connection) {
        super.notifyNewRingingConnectionP(connection);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyPhoneStateChanged() {
        this.mNotifier.notifyPhoneState(this);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyPreciseCallStateChanged() {
        super.notifyPreciseCallStateChangedP();
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyServiceStateChanged(ServiceState serviceState) {
        super.notifyServiceStateChangedP(serviceState);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyUnknownConnection(Connection connection) {
        super.notifyUnknownConnectionP(connection);
    }

    /* Access modifiers changed, original: 0000 */
    public void onMMIDone(CdmaMmiCode cdmaMmiCode) {
        if (this.mPendingMmis.remove(cdmaMmiCode)) {
            this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, cdmaMmiCode, null));
        }
    }

    /* Access modifiers changed, original: protected */
    public void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            UiccCardApplication uiccCardApplication;
            setCardInPhoneBook();
            UiccCardApplication uiccCardApplication2 = getUiccCardApplication();
            if (uiccCardApplication2 == null) {
                log("can't find 3GPP2 application; trying APP_FAM_3GPP");
                uiccCardApplication = this.mUiccController.getUiccCardApplication(this.mPhoneId, 1);
            } else {
                uiccCardApplication = uiccCardApplication2;
            }
            uiccCardApplication2 = (UiccCardApplication) this.mUiccApplication.get();
            if (uiccCardApplication2 != uiccCardApplication) {
                if (uiccCardApplication2 != null) {
                    log("Removing stale icc objects.");
                    if (this.mIccRecords.get() != null) {
                        unregisterForRuimRecordEvents();
                    }
                    this.mIccRecords.set(null);
                    this.mUiccApplication.set(null);
                }
                if (uiccCardApplication != null) {
                    log("New Uicc application found");
                    this.mUiccApplication.set(uiccCardApplication);
                    this.mIccRecords.set(uiccCardApplication.getIccRecords());
                    registerForRuimRecordEvents();
                }
            }
        }
    }

    public void prepareEri() {
        if (this.mEriManager == null) {
            Rlog.e(LOG_TAG, "PrepareEri: Trying to access stale objects");
            return;
        }
        this.mEriManager.loadEriFile();
        if (this.mEriManager.isEriFileLoaded()) {
            log("ERI read, notify registrants");
            this.mEriFileLoadedRegistrants.notifyRegistrants();
        }
    }

    public void registerForCallWaiting(Handler handler, int i, Object obj) {
        this.mCT.registerForCallWaiting(handler, i, obj);
    }

    public void registerForCdmaOtaStatusChange(Handler handler, int i, Object obj) {
        this.mCi.registerForCdmaOtaProvision(handler, i, obj);
    }

    public void registerForEcmTimerReset(Handler handler, int i, Object obj) {
        this.mEcmTimerResetRegistrants.addUnique(handler, i, obj);
    }

    public void registerForEriFileLoaded(Handler handler, int i, Object obj) {
        this.mEriFileLoadedRegistrants.add(new Registrant(handler, i, obj));
    }

    /* Access modifiers changed, original: protected */
    public void registerForRuimRecordEvents() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords != null) {
            iccRecords.registerForRecordsLoaded(this, 22, null);
        }
    }

    public void registerForSubscriptionInfoReady(Handler handler, int i, Object obj) {
        this.mSST.registerForSubscriptionInfoReady(handler, i, obj);
    }

    public void registerForSuppServiceNotification(Handler handler, int i, Object obj) {
        Rlog.e(LOG_TAG, "method registerForSuppServiceNotification is NOT supported in CDMA!");
    }

    public void rejectCall() throws CallStateException {
        this.mCT.rejectCall();
    }

    public void removeReferences() {
        log("removeReferences");
        this.mRuimPhoneBookInterfaceManager = null;
        this.mSubInfo = null;
        this.mCT = null;
        this.mSST = null;
        this.mEriManager = null;
        this.mExitEcmRunnable = null;
        super.removeReferences();
    }

    public void selectNetworkManually(OperatorInfo operatorInfo, Message message) {
        Rlog.e(LOG_TAG, "selectNetworkManually: not possible in CDMA");
        if (message != null) {
            AsyncResult.forMessage(message).exception = new CommandException(Error.REQUEST_NOT_SUPPORTED);
            message.sendToTarget();
        }
    }

    public void sendBurstDtmf(String str, int i, int i2, Message message) {
        Object obj = null;
        for (int i3 = 0; i3 < str.length(); i3++) {
            if (!PhoneNumberUtils.is12Key(str.charAt(i3))) {
                Rlog.e(LOG_TAG, "sendDtmf called with invalid character '" + str.charAt(i3) + "'");
                break;
            }
        }
        int obj2 = 1;
        if (this.mCT.mState == PhoneConstants.State.OFFHOOK && obj2 != null) {
            this.mCi.sendBurstDtmf(str, i, i2, message);
        }
    }

    public void sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG, "sendDtmf called with invalid character '" + c + "'");
        } else if (this.mCT.mState == PhoneConstants.State.OFFHOOK) {
            this.mCi.sendDtmf(c, null);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void sendEmergencyCallbackModeChange() {
        Intent intent = new Intent("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
        intent.putExtra("phoneinECMState", this.mIsPhoneInEcmState);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, getPhoneId());
        ActivityManagerNative.broadcastStickyIntent(intent, null, -1);
        Rlog.d(LOG_TAG, "sendEmergencyCallbackModeChange");
    }

    public void sendUssdResponse(String str) {
        Rlog.e(LOG_TAG, "sendUssdResponse: not possible in CDMA");
    }

    public void setCallBarring(String str, boolean z, String str2, int i, Message message) {
        Rlog.e(LOG_TAG, "setCallBarring: not possible in CDMA");
    }

    public void setCallBarring(String str, boolean z, String str2, Message message) {
        Rlog.e(LOG_TAG, "setCallBarring: not possible in CDMA");
    }

    public void setCallForwardingOption(int i, int i2, int i3, String str, int i4, Message message) {
        Rlog.e(LOG_TAG, "setCallForwardingOption: not possible in CDMA");
    }

    public void setCallForwardingOption(int i, int i2, String str, int i3, Message message) {
        Rlog.e(LOG_TAG, "setCallForwardingOption: not possible in CDMA");
    }

    public void setCallForwardingUncondTimerOption(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null && (imsPhone.getServiceState().getState() == 0 || imsPhone.isUtEnabled())) {
            imsPhone.setCallForwardingUncondTimerOption(i, i2, i3, i4, i5, i6, str, message);
        } else if (message != null) {
            AsyncResult.forMessage(message, null, new CommandException(Error.GENERIC_FAILURE));
            message.sendToTarget();
        }
    }

    public void setCallWaiting(boolean z, int i, Message message) {
        Rlog.e(LOG_TAG, "method setCallWaiting is NOT supported in CDMA!");
    }

    public void setCallWaiting(boolean z, Message message) {
        Rlog.e(LOG_TAG, "method setCallWaiting is NOT supported in CDMA!");
    }

    /* Access modifiers changed, original: protected */
    public void setCardInPhoneBook() {
        if (this.mUiccController != null) {
            this.mRuimPhoneBookInterfaceManager.setIccCard(this.mUiccController.getUiccCard(this.mPhoneId));
        }
    }

    public void setCellBroadcastSmsConfig(int[] iArr, Message message) {
        Rlog.e(LOG_TAG, "[CDMAPhone] setCellBroadcastSmsConfig() is obsolete; use SmsManager");
        message.sendToTarget();
    }

    public void setDataEnabled(boolean z) {
        this.mDcTracker.setDataEnabled(z);
    }

    public void setDataRoamingEnabled(boolean z) {
        this.mDcTracker.setDataOnRoamingEnabled(z);
    }

    /* Access modifiers changed, original: protected */
    public void setIsoCountryProperty(String str) {
        if (TextUtils.isEmpty(str)) {
            log("setIsoCountryProperty: clear 'gsm.sim.operator.iso-country'");
            setSystemProperty("gsm.sim.operator.iso-country", "");
            return;
        }
        String str2 = "";
        try {
            str2 = MccTable.countryCodeForMcc(Integer.parseInt(str.substring(0, 3)));
        } catch (NumberFormatException e) {
            loge("setIsoCountryProperty: countryCodeForMcc error", e);
        } catch (StringIndexOutOfBoundsException e2) {
            loge("setIsoCountryProperty: countryCodeForMcc error", e2);
        }
        log("setIsoCountryProperty: set 'gsm.sim.operator.iso-country' to iso=" + str2);
        setSystemProperty("gsm.sim.operator.iso-country", str2);
    }

    public void setLine1Number(String str, String str2, Message message) {
        Rlog.e(LOG_TAG, "setLine1Number: not possible in CDMA");
    }

    public void setMute(boolean z) {
        this.mCT.setMute(z);
    }

    public void setNetworkSelectionModeAutomatic(Message message) {
        Rlog.e(LOG_TAG, "method setNetworkSelectionModeAutomatic is NOT supported in CDMA!");
        if (message != null) {
            Rlog.e(LOG_TAG, "setNetworkSelectionModeAutomatic: not possible in CDMA- Posting exception");
            AsyncResult.forMessage(message).exception = new CommandException(Error.REQUEST_NOT_SUPPORTED);
            message.sendToTarget();
        }
    }

    public void setOnEcbModeExitResponse(Handler handler, int i, Object obj) {
        this.mEcmExitRespRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnPostDialCharacter(Handler handler, int i, Object obj) {
        this.mPostDialHandler = new Registrant(handler, i, obj);
    }

    public boolean setOperatorBrandOverride(String str) {
        boolean z = false;
        if (this.mUiccController != null) {
            UiccCard uiccCard = this.mUiccController.getUiccCard(getPhoneId());
            if (uiccCard != null) {
                boolean operatorBrandOverride = uiccCard.setOperatorBrandOverride(str);
                if (operatorBrandOverride) {
                    IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
                    if (iccRecords != null) {
                        SystemProperties.set("gsm.sim.operator.alpha", iccRecords.getServiceProviderName());
                    }
                    if (this.mSST != null) {
                        this.mSST.pollState();
                        return operatorBrandOverride;
                    }
                }
                z = operatorBrandOverride;
            }
        }
        return z;
    }

    public void setOutgoingCallerIdDisplay(int i, Message message) {
        Rlog.e(LOG_TAG, "setOutgoingCallerIdDisplay: not possible in CDMA");
    }

    public void setRadioPower(boolean z) {
        this.mSST.setRadioPower(z);
    }

    public void setSystemProperty(String str, String str2) {
        super.setSystemProperty(str, str2);
    }

    public void setUiTTYMode(int i, Message message) {
        if (this.mImsPhone != null) {
            this.mImsPhone.setUiTTYMode(i, message);
        }
    }

    public void setVoiceMailNumber(String str, String str2, Message message) {
        this.mVmNumber = str2;
        Message obtainMessage = obtainMessage(20, 0, 0, message);
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords != null) {
            iccRecords.setVoiceMailNumber(str, this.mVmNumber, obtainMessage);
        }
    }

    public void setVoiceMessageWaiting(int i, int i2) {
        setVoiceMessageCount(i2);
    }

    public void startDtmf(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            this.mCi.startDtmf(c, null);
        } else {
            Rlog.e(LOG_TAG, "startDtmf called with invalid character '" + c + "'");
        }
    }

    public void stopDtmf() {
        this.mCi.stopDtmf(null);
    }

    public void switchHoldingAndActive() throws CallStateException {
        this.mCT.switchWaitingOrHoldingAndActive();
    }

    public void unregisterForCallWaiting(Handler handler) {
        this.mCT.unregisterForCallWaiting(handler);
    }

    public void unregisterForCdmaOtaStatusChange(Handler handler) {
        this.mCi.unregisterForCdmaOtaProvision(handler);
    }

    public void unregisterForEcmTimerReset(Handler handler) {
        this.mEcmTimerResetRegistrants.remove(handler);
    }

    public void unregisterForEriFileLoaded(Handler handler) {
        this.mEriFileLoadedRegistrants.remove(handler);
    }

    /* Access modifiers changed, original: protected */
    public void unregisterForRuimRecordEvents() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords != null) {
            iccRecords.unregisterForRecordsLoaded(this);
        }
    }

    public void unregisterForSubscriptionInfoReady(Handler handler) {
        this.mSST.unregisterForSubscriptionInfoReady(handler);
    }

    public void unregisterForSuppServiceNotification(Handler handler) {
        Rlog.e(LOG_TAG, "method unregisterForSuppServiceNotification is NOT supported in CDMA!");
    }

    public void unsetOnEcbModeExitResponse(Handler handler) {
        this.mEcmExitRespRegistrant.clear();
    }

    /* Access modifiers changed, original: 0000 */
    public boolean updateCurrentCarrierInProvider() {
        return true;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean updateCurrentCarrierInProvider(String str) {
        log("CDMAPhone: updateCurrentCarrierInProvider called");
        if (TextUtils.isEmpty(str)) {
            return false;
        }
        try {
            Uri withAppendedPath = Uri.withAppendedPath(Carriers.CONTENT_URI, Carriers.CURRENT);
            ContentValues contentValues = new ContentValues();
            contentValues.put(Carriers.NUMERIC, str);
            log("updateCurrentCarrierInProvider from system: numeric=" + str);
            getContext().getContentResolver().insert(withAppendedPath, contentValues);
            log("update mccmnc=" + str);
            MccTable.updateMccMncConfiguration(this.mContext, str, false);
            return true;
        } catch (SQLException e) {
            Rlog.e(LOG_TAG, "Can't store current operator", e);
            return false;
        }
    }

    public void updateServiceLocation() {
        this.mSST.enableSingleLocationUpdate();
    }
}
