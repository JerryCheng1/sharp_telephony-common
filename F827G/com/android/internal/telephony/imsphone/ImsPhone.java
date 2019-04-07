package com.android.internal.telephony.imsphone;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.net.LinkProperties;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import com.android.ims.ImsCallForwardInfo;
import com.android.ims.ImsConfig;
import com.android.ims.ImsEcbmStateListener;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.ims.ImsReasonInfo;
import com.android.ims.ImsSsInfo;
import com.android.internal.telephony.Call.SrvccState;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.Phone.DataActivityState;
import com.android.internal.telephony.Phone.SuppService;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import java.util.ArrayList;
import java.util.List;
import jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data;

public class ImsPhone extends ImsPhoneBase {
    static final int CANCEL_ECM_TIMER = 1;
    public static final String CS_FALLBACK = "cs_fallback";
    private static final boolean DBG = true;
    private static final int DEFAULT_ECM_EXIT_TIMER_VALUE = 300000;
    private static final int EVENT_DEFAULT_PHONE_DATA_STATE_CHANGED = 46;
    protected static final int EVENT_GET_CALL_BARRING_DONE = 41;
    protected static final int EVENT_GET_CALL_WAITING_DONE = 43;
    protected static final int EVENT_GET_CLIR_DONE = 45;
    protected static final int EVENT_GET_INCOMING_ANONYMOUS_CALL_BARRING_DONE = 46;
    protected static final int EVENT_GET_INCOMING_SPECIFICDN_CALL_BARRING_DONE = 48;
    protected static final int EVENT_SET_CALL_BARRING_DONE = 40;
    protected static final int EVENT_SET_CALL_WAITING_DONE = 42;
    protected static final int EVENT_SET_CLIR_DONE = 44;
    protected static final int EVENT_SET_INCOMING_ANONYMOUS_CALL_BARRING_DONE = 47;
    protected static final int EVENT_SET_INCOMING_SPECIFICDN_CALL_BARRING_DONE = 49;
    private static final String LOG_TAG = "ImsPhone";
    static final int RESTART_ECM_TIMER = 0;
    private static final boolean VDBG = false;
    ImsPhoneCallTracker mCT;
    PhoneBase mDefaultPhone;
    private Registrant mEcmExitRespRegistrant;
    private Runnable mExitEcmRunnable = new Runnable() {
        public void run() {
            ImsPhone.this.exitEmergencyCallbackMode();
        }
    };
    private ImsConfig mImsConfig;
    ImsEcbmStateListener mImsEcbmStateListener = new ImsEcbmStateListener() {
        public void onECBMEntered() {
            Rlog.d(ImsPhone.LOG_TAG, "onECBMEntered");
            ImsPhone.this.handleEnterEmergencyCallbackMode();
        }

        public void onECBMExited() {
            Rlog.d(ImsPhone.LOG_TAG, "onECBMExited");
            ImsPhone.this.handleExitEmergencyCallbackMode();
        }
    };
    private boolean mImsRegistered = false;
    protected boolean mIsPhoneInEcmState;
    private boolean mIsVideoCapable = false;
    private String mLastDialString;
    ArrayList<ImsPhoneMmiCode> mPendingMMIs = new ArrayList();
    Registrant mPostDialHandler;
    ServiceState mSS = new ServiceState();
    private final RegistrantList mSilentRedialRegistrants = new RegistrantList();
    RegistrantList mSsnRegistrants = new RegistrantList();
    WakeLock mWakeLock;

    private static class Cf {
        final boolean mIsCfu;
        final Message mOnComplete;
        final String mSetCfNumber;

        Cf(String str, boolean z, Message message) {
            this.mSetCfNumber = str;
            this.mIsCfu = z;
            this.mOnComplete = message;
        }
    }

    ImsPhone(Context context, PhoneNotifier phoneNotifier, Phone phone) {
        super(LOG_TAG, context, phoneNotifier);
        this.mDefaultPhone = (PhoneBase) phone;
        this.mCT = new ImsPhoneCallTracker(this);
        this.mSS.setStateOff();
        this.mPhoneId = this.mDefaultPhone.getPhoneId();
        this.mIsPhoneInEcmState = SystemProperties.getBoolean("ril.cdma.inecmmode", false);
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, LOG_TAG);
        this.mWakeLock.setReferenceCounted(false);
        if (TelBrand.IS_SBM) {
            try {
                this.mImsConfig = ImsManager.getInstance(context, SubscriptionManager.getDefaultVoiceSubId()).getConfigInterface();
            } catch (ImsException e) {
                this.mImsConfig = null;
                Rlog.e(LOG_TAG, "ImsService is not running");
            }
        }
        if (this.mDefaultPhone.getServiceStateTracker() != null) {
            this.mDefaultPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(this, 46, null);
        }
        updateDataServiceState();
    }

    private int getActionFromCFAction(int i) {
        switch (i) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 3:
                return 3;
            case 4:
                return 4;
            default:
                return -1;
        }
    }

    private int getCBTypeFromFacility(String str) {
        return CommandsInterface.CB_FACILITY_BAOC.equals(str) ? 2 : CommandsInterface.CB_FACILITY_BAOIC.equals(str) ? 3 : CommandsInterface.CB_FACILITY_BAOICxH.equals(str) ? 4 : CommandsInterface.CB_FACILITY_BAIC.equals(str) ? 1 : CommandsInterface.CB_FACILITY_BAICr.equals(str) ? 5 : CommandsInterface.CB_FACILITY_BA_ALL.equals(str) ? 7 : CommandsInterface.CB_FACILITY_BA_MO.equals(str) ? 8 : CommandsInterface.CB_FACILITY_BA_MT.equals(str) ? 9 : 0;
    }

    private int getCFReasonFromCondition(int i) {
        switch (i) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 2:
                return 2;
            case 4:
                return 4;
            case 5:
                return 5;
            default:
                return 3;
        }
    }

    private CallForwardInfo getCallForwardInfo(ImsCallForwardInfo imsCallForwardInfo) {
        CallForwardInfo callForwardInfo = new CallForwardInfo();
        callForwardInfo.status = imsCallForwardInfo.mStatus;
        callForwardInfo.reason = getCFReasonFromCondition(imsCallForwardInfo.mCondition);
        callForwardInfo.serviceClass = 1;
        callForwardInfo.toa = imsCallForwardInfo.mToA;
        callForwardInfo.number = imsCallForwardInfo.mNumber;
        callForwardInfo.timeSeconds = imsCallForwardInfo.mTimeSeconds;
        callForwardInfo.startHour = imsCallForwardInfo.mStartHour;
        callForwardInfo.startMinute = imsCallForwardInfo.mStartMinute;
        callForwardInfo.endHour = imsCallForwardInfo.mEndHour;
        callForwardInfo.endMinute = imsCallForwardInfo.mEndMinute;
        return callForwardInfo;
    }

    private int getConditionFromCFReason(int i) {
        switch (i) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            case 4:
                return 4;
            case 5:
                return 5;
            default:
                return -1;
        }
    }

    private ArrayList<String> getDialNumberInfo(String str, int i) {
        ArrayList<String> arrayList = new ArrayList();
        Context context = getContext();
        boolean z = context != null ? context.getSharedPreferences("network_service_settings_private", 0).getBoolean("sub_address_setting", true) : false;
        Rlog.d(LOG_TAG, "dialString is " + str + " isSubAddress is " + z + " prefix is " + i);
        arrayList.add(str);
        arrayList.add(null);
        if (z) {
            String substring = str.substring(i);
            int length = substring.length();
            if (length != 0) {
                int i2 = 0;
                while (i2 < length && substring.charAt(i2) == '*') {
                    i2++;
                }
                if (substring.substring(i2).length() != 0) {
                    int indexOf = substring.substring(i2).indexOf(42);
                    if (indexOf > 0) {
                        indexOf += i2 + i;
                        Object substring2 = str.substring(0, indexOf);
                        substring = str.substring(indexOf + 1);
                        if (!substring.equals("")) {
                            substring2 = substring2 + "&" + substring;
                        }
                        arrayList.set(0, substring2);
                    }
                    Rlog.d(LOG_TAG, "dialInfoList.get(0) is " + ((String) arrayList.get(0)) + ")");
                    return arrayList;
                }
            }
        }
        return arrayList;
    }

    private boolean handleCallDeflectionIncallSupplementaryService(String str) {
        if (str.length() > 1) {
            return false;
        }
        if (getRingingCall().getState() != State.IDLE) {
            Rlog.d(LOG_TAG, "MmiCode 0: rejectCall");
            try {
                this.mCT.rejectCall();
                return true;
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "reject failed", e);
                notifySuppServiceFailed(SuppService.REJECT);
                return true;
            }
        } else if (getBackgroundCall().getState() == State.IDLE) {
            return true;
        } else {
            Rlog.d(LOG_TAG, "MmiCode 0: hangupWaitingOrBackground");
            try {
                this.mCT.hangup(getBackgroundCall());
                return true;
            } catch (CallStateException e2) {
                Rlog.d(LOG_TAG, "hangup failed", e2);
                return true;
            }
        }
    }

    private boolean handleCallHoldIncallSupplementaryService(String str) {
        int length = str.length();
        if (length > 2) {
            return false;
        }
        getForegroundCall();
        if (length > 1) {
            Rlog.d(LOG_TAG, "separate not supported");
            notifySuppServiceFailed(SuppService.SEPARATE);
            return true;
        }
        try {
            if (getRingingCall().getState() != State.IDLE) {
                Rlog.d(LOG_TAG, "MmiCode 2: accept ringing call");
                this.mCT.acceptCall(2);
                return true;
            }
            Rlog.d(LOG_TAG, "MmiCode 2: switchWaitingOrHoldingAndActive");
            this.mCT.switchWaitingOrHoldingAndActive();
            return true;
        } catch (CallStateException e) {
            Rlog.d(LOG_TAG, "switch failed", e);
            notifySuppServiceFailed(SuppService.SWITCH);
            return true;
        }
    }

    private boolean handleCallWaitingIncallSupplementaryService(String str) {
        int length = str.length();
        if (length > 2) {
            return false;
        }
        ImsPhoneCall foregroundCall = getForegroundCall();
        if (length > 1) {
            try {
                Rlog.d(LOG_TAG, "not support 1X SEND");
                notifySuppServiceFailed(SuppService.HANGUP);
                return true;
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "hangup failed", e);
                notifySuppServiceFailed(SuppService.HANGUP);
                return true;
            }
        } else if (foregroundCall.getState() != State.IDLE) {
            Rlog.d(LOG_TAG, "MmiCode 1: hangup foreground");
            this.mCT.hangup(foregroundCall);
            return true;
        } else {
            Rlog.d(LOG_TAG, "MmiCode 1: switchWaitingOrHoldingAndActive");
            this.mCT.switchWaitingOrHoldingAndActive();
            return true;
        }
    }

    private int[] handleCbQueryResult(ImsSsInfo[] imsSsInfoArr) {
        int[] iArr = new int[]{0};
        if (imsSsInfoArr[0].mStatus == 1) {
            iArr[0] = 1;
        }
        return iArr;
    }

    private boolean handleCcbsIncallSupplementaryService(String str) {
        if (str.length() > 1) {
            return false;
        }
        Rlog.i(LOG_TAG, "MmiCode 5: CCBS not supported!");
        notifySuppServiceFailed(SuppService.UNKNOWN);
        return true;
    }

    private CallForwardInfo[] handleCfQueryResult(ImsCallForwardInfo[] imsCallForwardInfoArr) {
        CallForwardInfo[] callForwardInfoArr = (imsCallForwardInfoArr == null || imsCallForwardInfoArr.length == 0) ? null : new CallForwardInfo[imsCallForwardInfoArr.length];
        IccRecords iccRecords = getIccRecords();
        if (imsCallForwardInfoArr != null && imsCallForwardInfoArr.length != 0) {
            int length = imsCallForwardInfoArr.length;
            for (int i = 0; i < length; i++) {
                if (imsCallForwardInfoArr[i].mCondition == 0 && iccRecords != null) {
                    setCallForwardingPreference(imsCallForwardInfoArr[i].mStatus == 1);
                    iccRecords.setVoiceCallForwardingFlag(1, imsCallForwardInfoArr[i].mStatus == 1, imsCallForwardInfoArr[i].mNumber);
                }
                callForwardInfoArr[i] = getCallForwardInfo(imsCallForwardInfoArr[i]);
            }
        } else if (iccRecords != null) {
            iccRecords.setVoiceCallForwardingFlag(1, false, null);
        }
        return callForwardInfoArr;
    }

    private int[] handleCwQueryResult(ImsSsInfo[] imsSsInfoArr) {
        int[] iArr = new int[2];
        iArr[0] = 0;
        if (imsSsInfoArr[0].mStatus == 1) {
            iArr[0] = 1;
            iArr[1] = 1;
        }
        return iArr;
    }

    private boolean handleEctIncallSupplementaryService(String str) {
        if (str.length() != 1) {
            return false;
        }
        Rlog.d(LOG_TAG, "MmiCode 4: not support explicit call transfer");
        notifySuppServiceFailed(SuppService.TRANSFER);
        return true;
    }

    private void handleEnterEmergencyCallbackMode() {
        Rlog.d(LOG_TAG, "handleEnterEmergencyCallbackMode,mIsPhoneInEcmState= " + this.mIsPhoneInEcmState);
        if (!this.mIsPhoneInEcmState) {
            this.mIsPhoneInEcmState = true;
            sendEmergencyCallbackModeChange();
            setSystemProperty("ril.cdma.inecmmode", "true");
            postDelayed(this.mExitEcmRunnable, SystemProperties.getLong("ro.cdma.ecmexittimer", 300000));
            this.mWakeLock.acquire();
        }
    }

    private void handleExitEmergencyCallbackMode() {
        Rlog.d(LOG_TAG, "handleExitEmergencyCallbackMode: mIsPhoneInEcmState = " + this.mIsPhoneInEcmState);
        removeCallbacks(this.mExitEcmRunnable);
        if (this.mEcmExitRespRegistrant != null) {
            this.mEcmExitRespRegistrant.notifyResult(Boolean.TRUE);
        }
        if (this.mIsPhoneInEcmState) {
            this.mIsPhoneInEcmState = false;
            setSystemProperty("ril.cdma.inecmmode", "false");
        }
        sendEmergencyCallbackModeChange();
    }

    private boolean handleMultipartyIncallSupplementaryService(String str) {
        if (str.length() > 1) {
            return false;
        }
        Rlog.d(LOG_TAG, "MmiCode 3: merge calls");
        conference();
        return true;
    }

    private boolean isCfEnable(int i) {
        return i == 1 || i == 3;
    }

    private boolean isValidCommandInterfaceCFAction(int i) {
        switch (i) {
            case 0:
            case 1:
            case 3:
            case 4:
                return true;
            default:
                return false;
        }
    }

    private boolean isValidCommandInterfaceCFReason(int i) {
        switch (i) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return true;
            default:
                return false;
        }
    }

    private boolean isValidFacilityString(String str) {
        if (str.equals(CommandsInterface.CB_FACILITY_BAOC) || str.equals(CommandsInterface.CB_FACILITY_BAOIC) || str.equals(CommandsInterface.CB_FACILITY_BAOICxH) || str.equals(CommandsInterface.CB_FACILITY_BAIC) || str.equals(CommandsInterface.CB_FACILITY_BAICr) || str.equals(CommandsInterface.CB_FACILITY_BA_ALL) || str.equals(CommandsInterface.CB_FACILITY_BA_MO) || str.equals(CommandsInterface.CB_FACILITY_BA_MT)) {
            return true;
        }
        Rlog.e(LOG_TAG, "isValidFacilityString: invalid facilityString:" + str);
        return false;
    }

    private void onNetworkInitiatedUssd(ImsPhoneMmiCode imsPhoneMmiCode) {
        Rlog.d(LOG_TAG, "onNetworkInitiatedUssd");
        this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, imsPhoneMmiCode, null));
    }

    private void sendResponse(Message message, Object obj, Throwable th) {
        if (message != null) {
            if (th != null) {
                AsyncResult.forMessage(message, obj, getCommandException(th));
            } else {
                AsyncResult.forMessage(message, obj, null);
            }
            message.sendToTarget();
        }
    }

    private void updateDataServiceState() {
        if (this.mSS != null && this.mDefaultPhone.getServiceStateTracker() != null && this.mDefaultPhone.getServiceStateTracker().mSS != null) {
            ServiceState serviceState = this.mDefaultPhone.getServiceStateTracker().mSS;
            this.mSS.setDataRegState(serviceState.getDataRegState());
            this.mSS.setRilDataRadioTechnology(serviceState.getRilDataRadioTechnology());
            Rlog.d(LOG_TAG, "updateDataServiceState: defSs = " + serviceState + " imsSs = " + this.mSS);
        }
    }

    public void acceptCall(int i) throws CallStateException {
        this.mCT.acceptCall(i);
    }

    public /* bridge */ /* synthetic */ void activateCellBroadcastSms(int i, Message message) {
        super.activateCellBroadcastSms(i, message);
    }

    public void addParticipant(String str) throws CallStateException {
        this.mCT.addParticipant(str);
    }

    public boolean canConference() {
        return this.mCT.canConference();
    }

    public boolean canDial() {
        return this.mCT.canDial();
    }

    public boolean canTransfer() {
        return this.mCT.canTransfer();
    }

    /* Access modifiers changed, original: 0000 */
    public void cancelUSSD() {
        this.mCT.cancelUSSD();
    }

    public void clearDisconnected() {
        this.mCT.clearDisconnected();
    }

    public void conference() {
        this.mCT.conference();
    }

    public void declineCall() throws CallStateException {
        this.mCT.declineCall();
    }

    public void deflectCall(String str) throws CallStateException {
        this.mCT.deflectCall(str);
    }

    public Connection dial(String str, int i) throws CallStateException {
        return dialInternal(str, i, null);
    }

    public Connection dial(String str, int i, int i2) throws CallStateException {
        return dial(str, i, null, i2);
    }

    public Connection dial(String str, int i, Bundle bundle) throws CallStateException {
        return dialInternal(str, i, bundle);
    }

    public Connection dial(String str, int i, Bundle bundle, int i2) throws CallStateException {
        return dialInternal(str, i, bundle, i2);
    }

    /* Access modifiers changed, original: protected */
    public Connection dialInternal(String str, int i, Bundle bundle) throws CallStateException {
        if (TelBrand.IS_DCM) {
            return dialInternal(str, i, bundle, 0);
        }
        boolean z;
        boolean z2;
        if (bundle != null) {
            z = bundle.getBoolean("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
            z2 = bundle.getBoolean("org.codeaurora.extra.SKIP_SCHEMA_PARSING", false);
        } else {
            z2 = false;
            z = false;
        }
        String stripSeparators = (z || z2) ? str : PhoneNumberUtils.stripSeparators(str);
        if (handleInCallMmiCommands(stripSeparators)) {
            return null;
        }
        if (!ImsPhoneMmiCode.isScMatchesSuppServType(stripSeparators)) {
            stripSeparators = PhoneNumberUtils.extractNetworkPortionAlt(stripSeparators);
        }
        ImsPhoneMmiCode newFromDialString = ImsPhoneMmiCode.newFromDialString(stripSeparators, this);
        Rlog.d(LOG_TAG, "dialing w/ mmi '" + newFromDialString + "'...");
        if (newFromDialString == null) {
            return this.mCT.dial(str, i, bundle);
        }
        if (newFromDialString.isTemporaryModeCLIR()) {
            return this.mCT.dial(newFromDialString.getDialingNumber(), newFromDialString.getCLIRMode(), i, bundle);
        }
        if (newFromDialString.isSupportedOverImsPhone()) {
            this.mPendingMMIs.add(newFromDialString);
            this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, newFromDialString, null));
            newFromDialString.processCode();
            return null;
        }
        throw new CallStateException(CS_FALLBACK);
    }

    /* Access modifiers changed, original: protected */
    public Connection dialInternal(String str, int i, Bundle bundle, int i2) throws CallStateException {
        boolean z;
        boolean z2;
        if (bundle != null) {
            z = bundle.getBoolean("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
            z2 = bundle.getBoolean("org.codeaurora.extra.SKIP_SCHEMA_PARSING", false);
        } else {
            z2 = false;
            z = false;
        }
        String stripSeparators = (z || z2) ? str : PhoneNumberUtils.stripSeparators(str);
        if (handleInCallMmiCommands(stripSeparators)) {
            return null;
        }
        if (!ImsPhoneMmiCode.isScMatchesSuppServType(stripSeparators)) {
            stripSeparators = PhoneNumberUtils.extractNetworkPortionAlt(stripSeparators);
        }
        ImsPhoneMmiCode newFromDialString = ImsPhoneMmiCode.newFromDialString(stripSeparators, this);
        Rlog.d(LOG_TAG, "dialing w/ mmi '" + newFromDialString + "'...");
        if (newFromDialString == null) {
            return this.mCT.dial((String) getDialNumberInfo(str, i2).get(0), i, bundle);
        } else if (newFromDialString.isTemporaryModeCLIR()) {
            if (i2 >= 4) {
                i2 -= 4;
            }
            return this.mCT.dial((String) getDialNumberInfo(newFromDialString.getDialingNumber(), i2).get(0), newFromDialString.getCLIRMode(), i, bundle);
        } else if (newFromDialString.isSupportedOverImsPhone()) {
            this.mPendingMMIs.add(newFromDialString);
            this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, newFromDialString, null));
            newFromDialString.processCode();
            return null;
        } else {
            throw new CallStateException(CS_FALLBACK);
        }
    }

    public /* bridge */ /* synthetic */ boolean disableDataConnectivity() {
        return super.disableDataConnectivity();
    }

    public /* bridge */ /* synthetic */ void disableLocationUpdates() {
        super.disableLocationUpdates();
    }

    public void dispose() {
        Rlog.d(LOG_TAG, "dispose");
        this.mPendingMMIs.clear();
        this.mCT.dispose();
        if (this.mDefaultPhone != null && this.mDefaultPhone.getServiceStateTracker() != null) {
            this.mDefaultPhone.getServiceStateTracker().unregisterForDataRegStateOrRatChanged(this);
        }
    }

    public /* bridge */ /* synthetic */ boolean enableDataConnectivity() {
        return super.enableDataConnectivity();
    }

    public /* bridge */ /* synthetic */ void enableLocationUpdates() {
        super.enableLocationUpdates();
    }

    public void exitEmergencyCallbackMode() {
        if (this.mWakeLock.isHeld()) {
            this.mWakeLock.release();
        }
        Rlog.d(LOG_TAG, "exitEmergencyCallbackMode()");
        try {
            this.mCT.getEcbmInterface().exitEmergencyCallbackMode();
        } catch (ImsException e) {
            e.printStackTrace();
        }
    }

    public void explicitCallTransfer() {
        this.mCT.explicitCallTransfer();
    }

    public /* bridge */ /* synthetic */ List getAllCellInfo() {
        return super.getAllCellInfo();
    }

    public /* bridge */ /* synthetic */ void getAvailableNetworks(Message message) {
        super.getAvailableNetworks(message);
    }

    public ImsPhoneCall getBackgroundCall() {
        return this.mCT.mBackgroundCall;
    }

    public void getCallBarring(String str, Message message) {
        Rlog.d(LOG_TAG, "getCallBarring facility=" + str);
        try {
            this.mCT.getUtInterface().queryCallBarring(getCBTypeFromFacility(str), obtainMessage(41, message));
        } catch (ImsException e) {
            sendErrorResponse(message, e);
        }
    }

    public void getCallBarring(String str, String str2, Message message) {
        if (isValidFacilityString(str)) {
            Rlog.d(LOG_TAG, "getCallBarring: facility=" + str);
            try {
                if (this.mImsConfig != null) {
                    this.mImsConfig.queryFacilityLockSH(str, str2, 0, message);
                } else {
                    Rlog.e(LOG_TAG, "queryFacilityLockSH failed. mImsConfig is null");
                }
            } catch (ImsException e) {
                Rlog.e(LOG_TAG, "queryFacilityLockSH failed. Exception = " + e);
            }
        }
    }

    public boolean getCallForwardingIndicator() {
        IccRecords iccRecords = getIccRecords();
        return (iccRecords == null || !iccRecords.isCallForwardStatusStored()) ? getCallForwardingPreference() : iccRecords.getVoiceCallForwardingFlag();
    }

    public void getCallForwardingOption(int i, Message message) {
        Rlog.d(LOG_TAG, "getCallForwardingOption reason=" + i);
        if (isValidCommandInterfaceCFReason(i)) {
            Rlog.d(LOG_TAG, "requesting call forwarding query.");
            try {
                this.mCT.getUtInterface().queryCallForward(getConditionFromCFReason(i), null, obtainMessage(13, message));
            } catch (ImsException e) {
                sendErrorResponse(message, e);
            }
        } else if (message != null) {
            sendErrorResponse(message);
        }
    }

    public CallTracker getCallTracker() {
        return this.mCT;
    }

    public void getCallWaiting(Message message) {
        Rlog.d(LOG_TAG, "getCallWaiting");
        try {
            this.mCT.getUtInterface().queryCallWaiting(obtainMessage(43, message));
        } catch (ImsException e) {
            sendErrorResponse(message, e);
        }
    }

    public /* bridge */ /* synthetic */ void getCellBroadcastSmsConfig(Message message) {
        super.getCellBroadcastSmsConfig(message);
    }

    public /* bridge */ /* synthetic */ CellLocation getCellLocation() {
        return super.getCellLocation();
    }

    /* Access modifiers changed, original: 0000 */
    public CommandException getCommandException(int i) {
        return getCommandException(i, null);
    }

    /* Access modifiers changed, original: 0000 */
    public CommandException getCommandException(int i, String str) {
        Rlog.d(LOG_TAG, "getCommandException code= " + i + ", errorString= " + str);
        Error error = Error.GENERIC_FAILURE;
        switch (i) {
            case 801:
                error = Error.REQUEST_NOT_SUPPORTED;
                break;
            case 821:
                error = Error.PASSWORD_INCORRECT;
                break;
        }
        return new CommandException(error, str);
    }

    /* Access modifiers changed, original: 0000 */
    public CommandException getCommandException(Throwable th) {
        if (th instanceof ImsException) {
            return getCommandException(((ImsException) th).getCode(), th.getMessage());
        }
        Rlog.d(LOG_TAG, "getCommandException generic failure");
        return new CommandException(Error.GENERIC_FAILURE);
    }

    public /* bridge */ /* synthetic */ List getCurrentDataConnectionList() {
        return super.getCurrentDataConnectionList();
    }

    public /* bridge */ /* synthetic */ DataActivityState getDataActivityState() {
        return super.getDataActivityState();
    }

    public /* bridge */ /* synthetic */ void getDataCallList(Message message) {
        super.getDataCallList(message);
    }

    public /* bridge */ /* synthetic */ DataState getDataConnectionState() {
        return super.getDataConnectionState();
    }

    public /* bridge */ /* synthetic */ DataState getDataConnectionState(String str) {
        return super.getDataConnectionState(str);
    }

    public /* bridge */ /* synthetic */ boolean getDataEnabled() {
        return super.getDataEnabled();
    }

    public /* bridge */ /* synthetic */ boolean getDataRoamingEnabled() {
        return super.getDataRoamingEnabled();
    }

    public /* bridge */ /* synthetic */ String getDeviceId() {
        return super.getDeviceId();
    }

    public /* bridge */ /* synthetic */ String getDeviceSvn() {
        return super.getDeviceSvn();
    }

    public /* bridge */ /* synthetic */ String getEsn() {
        return super.getEsn();
    }

    public ImsPhoneCall getForegroundCall() {
        return this.mCT.mForegroundCall;
    }

    public /* bridge */ /* synthetic */ String getGroupIdLevel1() {
        return super.getGroupIdLevel1();
    }

    public ArrayList<Connection> getHandoverConnection() {
        ArrayList arrayList = new ArrayList();
        arrayList.addAll(getForegroundCall().mConnections);
        arrayList.addAll(getBackgroundCall().mConnections);
        arrayList.addAll(getRingingCall().mConnections);
        return arrayList.size() > 0 ? arrayList : null;
    }

    public /* bridge */ /* synthetic */ IccCard getIccCard() {
        return super.getIccCard();
    }

    public /* bridge */ /* synthetic */ IccFileHandler getIccFileHandler() {
        return super.getIccFileHandler();
    }

    public /* bridge */ /* synthetic */ IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return super.getIccPhoneBookInterfaceManager();
    }

    public IccRecords getIccRecords() {
        return (IccRecords) this.mDefaultPhone.mIccRecords.get();
    }

    public /* bridge */ /* synthetic */ boolean getIccRecordsLoaded() {
        return super.getIccRecordsLoaded();
    }

    public /* bridge */ /* synthetic */ String getIccSerialNumber() {
        return super.getIccSerialNumber();
    }

    public /* bridge */ /* synthetic */ String getImei() {
        return super.getImei();
    }

    public void getIncomingAnonymousCallBarring(Message message) {
        Rlog.d(LOG_TAG, "getIncomingAnonymousCallBarring: facility=157");
        obtainMessage(46, message);
        try {
            this.mCT.getUtInterface().queryFacilityLockBAICa(ImsPhoneMmiCode.SC_BAICa, null, 0, message);
        } catch (ImsException e) {
            sendErrorResponse(message, e);
        }
    }

    public void getIncomingSpecificDnCallBarring(Message message) {
        Rlog.d(LOG_TAG, "getIncomingSpecificDnCallBarring: facility=156");
        obtainMessage(48, message);
        try {
            this.mCT.getUtInterface().queryIncomingCallBarringSDn(ImsPhoneMmiCode.SC_BS_MT, 1, message);
        } catch (ImsException e) {
            sendErrorResponse(message, e);
        }
    }

    public /* bridge */ /* synthetic */ String getLine1AlphaTag() {
        return super.getLine1AlphaTag();
    }

    public /* bridge */ /* synthetic */ String getLine1Number() {
        return super.getLine1Number();
    }

    public /* bridge */ /* synthetic */ LinkProperties getLinkProperties(String str) {
        return super.getLinkProperties(str);
    }

    public /* bridge */ /* synthetic */ String getMeid() {
        return super.getMeid();
    }

    public /* bridge */ /* synthetic */ boolean getMessageWaitingIndicator() {
        return super.getMessageWaitingIndicator();
    }

    public boolean getMute() {
        return this.mCT.getMute();
    }

    public /* bridge */ /* synthetic */ void getNeighboringCids(Message message) {
        super.getNeighboringCids(message);
    }

    public void getOutgoingCallerIdDisplay(Message message) {
        Rlog.d(LOG_TAG, "getCLIR");
        try {
            this.mCT.getUtInterface().queryCLIR(obtainMessage(45, message));
        } catch (ImsException e) {
            sendErrorResponse(message, e);
        }
    }

    public List<? extends ImsPhoneMmiCode> getPendingMmiCodes() {
        return this.mPendingMMIs;
    }

    public int getPhoneId() {
        return this.mDefaultPhone.getPhoneId();
    }

    public /* bridge */ /* synthetic */ PhoneSubInfo getPhoneSubInfo() {
        return super.getPhoneSubInfo();
    }

    public /* bridge */ /* synthetic */ int getPhoneType() {
        return super.getPhoneType();
    }

    public ImsPhoneCall getRingingCall() {
        return this.mCT.mRingingCall;
    }

    public ServiceState getServiceState() {
        return this.mSS;
    }

    public /* bridge */ /* synthetic */ SignalStrength getSignalStrength() {
        return super.getSignalStrength();
    }

    public PhoneConstants.State getState() {
        return this.mCT.mState;
    }

    public int getSubId() {
        return this.mDefaultPhone.getSubId();
    }

    public String getSubscriberId() {
        IccRecords iccRecords = getIccRecords();
        return iccRecords != null ? iccRecords.getIMSI() : null;
    }

    public /* bridge */ /* synthetic */ String getVoiceMailAlphaTag() {
        return super.getVoiceMailAlphaTag();
    }

    public /* bridge */ /* synthetic */ String getVoiceMailNumber() {
        return super.getVoiceMailNumber();
    }

    public boolean handleInCallMmiCommands(String str) {
        if (!isInCall() || TextUtils.isEmpty(str)) {
            return false;
        }
        switch (str.charAt(0)) {
            case '0':
                return handleCallDeflectionIncallSupplementaryService(str);
            case '1':
                return handleCallWaitingIncallSupplementaryService(str);
            case OEM_RIL_RDE_Data.RDE_NV_MOB_TERM_HOME_I /*50*/:
                return handleCallHoldIncallSupplementaryService(str);
            case '3':
                return handleMultipartyIncallSupplementaryService(str);
            case '4':
                return handleEctIncallSupplementaryService(str);
            case '5':
                return handleCcbsIncallSupplementaryService(str);
            default:
                return false;
        }
    }

    public void handleMessage(Message message) {
        boolean z = false;
        Object obj = null;
        AsyncResult asyncResult = (AsyncResult) message.obj;
        Rlog.d(LOG_TAG, "handleMessage what=" + message.what);
        switch (message.what) {
            case 12:
                IccRecords iccRecords = getIccRecords();
                Cf cf = (Cf) asyncResult.userObj;
                if (cf.mIsCfu && asyncResult.exception == null && iccRecords != null) {
                    setCallForwardingPreference(message.arg1 == 1);
                    if (message.arg1 == 1) {
                        z = true;
                    }
                    iccRecords.setVoiceCallForwardingFlag(1, z, cf.mSetCfNumber);
                }
                sendResponse(cf.mOnComplete, null, asyncResult.exception);
                updateCallForwardStatus();
                return;
            case 13:
                sendResponse((Message) asyncResult.userObj, asyncResult.exception == null ? handleCfQueryResult((ImsCallForwardInfo[]) asyncResult.result) : null, asyncResult.exception);
                updateCallForwardStatus();
                return;
            case 37:
                sendResponse(((Cf) asyncResult.userObj).mOnComplete, null, asyncResult.exception);
                return;
            case 39:
                Rlog.d(LOG_TAG, "Callforwarding is " + getCallForwardingPreference());
                notifyCallForwardingIndicator();
                return;
            case 40:
            case 42:
            case 44:
                sendResponse((Message) asyncResult.userObj, null, asyncResult.exception);
                return;
            case 41:
            case 43:
                if (asyncResult.exception == null) {
                    if (message.what == 41) {
                        obj = handleCbQueryResult((ImsSsInfo[]) asyncResult.result);
                    } else if (message.what == 43) {
                        obj = handleCwQueryResult((ImsSsInfo[]) asyncResult.result);
                    }
                }
                sendResponse((Message) asyncResult.userObj, obj, asyncResult.exception);
                return;
            case 45:
                Bundle bundle = (Bundle) asyncResult.result;
                if (bundle != null) {
                    obj = bundle.getIntArray(ImsPhoneMmiCode.UT_BUNDLE_KEY_CLIR);
                }
                sendResponse((Message) asyncResult.userObj, obj, asyncResult.exception);
                return;
            case OEM_RIL_RDE_Data.RDE_NV_DS_MIP_SS_USER_PROF_I /*46*/:
                Rlog.d(LOG_TAG, "EVENT_DEFAULT_PHONE_DATA_STATE_CHANGED");
                updateDataServiceState();
                return;
            default:
                super.handleMessage(message);
                return;
        }
    }

    public /* bridge */ /* synthetic */ boolean handlePinMmi(String str) {
        return super.handlePinMmi(str);
    }

    /* Access modifiers changed, original: 0000 */
    public void handleTimerInEmergencyCallbackMode(int i) {
        switch (i) {
            case 0:
                postDelayed(this.mExitEcmRunnable, SystemProperties.getLong("ro.cdma.ecmexittimer", 300000));
                if (this.mDefaultPhone.getPhoneType() == 1) {
                    ((GSMPhone) this.mDefaultPhone).notifyEcbmTimerReset(Boolean.FALSE);
                    return;
                } else {
                    ((CDMAPhone) this.mDefaultPhone).notifyEcbmTimerReset(Boolean.FALSE);
                    return;
                }
            case 1:
                removeCallbacks(this.mExitEcmRunnable);
                if (this.mDefaultPhone.getPhoneType() == 1) {
                    ((GSMPhone) this.mDefaultPhone).notifyEcbmTimerReset(Boolean.TRUE);
                    return;
                } else {
                    ((CDMAPhone) this.mDefaultPhone).notifyEcbmTimerReset(Boolean.TRUE);
                    return;
                }
            default:
                Rlog.e(LOG_TAG, "handleTimerInEmergencyCallbackMode, unsupported action " + i);
                return;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void initiateSilentRedial() {
        AsyncResult asyncResult = new AsyncResult(null, this.mLastDialString, null);
        if (asyncResult != null) {
            this.mSilentRedialRegistrants.notifyRegistrants(asyncResult);
        }
    }

    public /* bridge */ /* synthetic */ boolean isDataConnectivityPossible() {
        return super.isDataConnectivityPossible();
    }

    public boolean isImsRegistered() {
        return this.mImsRegistered;
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

    public boolean isUtEnabled() {
        return this.mCT.isUtEnabled();
    }

    public boolean isVolteEnabled() {
        return this.mCT.isVolteEnabled();
    }

    public boolean isVtEnabled() {
        return this.mCT.isVtEnabled();
    }

    public /* bridge */ /* synthetic */ void migrateFrom(PhoneBase phoneBase) {
        super.migrateFrom(phoneBase);
    }

    public /* bridge */ /* synthetic */ boolean needsOtaServiceProvisioning() {
        return super.needsOtaServiceProvisioning();
    }

    public /* bridge */ /* synthetic */ void notifyCallForwardingIndicator() {
        super.notifyCallForwardingIndicator();
    }

    public void notifyForVideoCapabilityChanged(boolean z) {
        this.mIsVideoCapable = z;
        this.mDefaultPhone.notifyForVideoCapabilityChanged(z);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyIncomingRing() {
        Rlog.d(LOG_TAG, "notifyIncomingRing");
        sendMessage(obtainMessage(14, new AsyncResult(null, null, null)));
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyNewRingingConnection(Connection connection) {
        this.mDefaultPhone.notifyNewRingingConnectionP(connection);
    }

    public void notifySrvccState(SrvccState srvccState) {
        this.mCT.notifySrvccState(srvccState);
    }

    public void notifySuppSvcNotification(SuppServiceNotification suppServiceNotification) {
        Rlog.d(LOG_TAG, "notifySuppSvcNotification: suppSvc = " + suppServiceNotification);
        this.mSsnRegistrants.notifyRegistrants(new AsyncResult(null, suppServiceNotification, null));
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyUnknownConnection(Connection connection) {
        this.mDefaultPhone.notifyUnknownConnectionP(connection);
    }

    /* Access modifiers changed, original: 0000 */
    public void onIncomingUSSD(int i, String str) {
        ImsPhoneMmiCode imsPhoneMmiCode;
        int i2 = 0;
        Rlog.d(LOG_TAG, "onIncomingUSSD ussdMode=" + i);
        boolean z = i == 1;
        int i3 = (i == 0 || i == 1) ? 0 : 1;
        int size = this.mPendingMMIs.size();
        while (i2 < size) {
            if (((ImsPhoneMmiCode) this.mPendingMMIs.get(i2)).isPendingUSSD()) {
                imsPhoneMmiCode = (ImsPhoneMmiCode) this.mPendingMMIs.get(i2);
                break;
            }
            i2++;
        }
        imsPhoneMmiCode = null;
        if (imsPhoneMmiCode != null) {
            if (i3 != 0) {
                imsPhoneMmiCode.onUssdFinishedError();
            } else {
                imsPhoneMmiCode.onUssdFinished(str, z);
            }
        } else if (i3 == 0 && str != null) {
            onNetworkInitiatedUssd(ImsPhoneMmiCode.newNetworkInitiatedUssd(str, z, this));
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void onMMIDone(ImsPhoneMmiCode imsPhoneMmiCode) {
        if (this.mPendingMMIs.remove(imsPhoneMmiCode) || imsPhoneMmiCode.isUssdRequest()) {
            this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, imsPhoneMmiCode, null));
        }
    }

    public /* bridge */ /* synthetic */ void onTtyModeReceived(int i) {
        super.onTtyModeReceived(i);
    }

    public /* bridge */ /* synthetic */ void registerForOnHoldTone(Handler handler, int i, Object obj) {
        super.registerForOnHoldTone(handler, i, obj);
    }

    public /* bridge */ /* synthetic */ void registerForRingbackTone(Handler handler, int i, Object obj) {
        super.registerForRingbackTone(handler, i, obj);
    }

    public void registerForSilentRedial(Handler handler, int i, Object obj) {
        this.mSilentRedialRegistrants.addUnique(handler, i, obj);
    }

    public void registerForSuppServiceNotification(Handler handler, int i, Object obj) {
        this.mSsnRegistrants.addUnique(handler, i, obj);
    }

    public /* bridge */ /* synthetic */ void registerForTtyModeReceived(Handler handler, int i, Object obj) {
        super.registerForTtyModeReceived(handler, i, obj);
    }

    public void rejectCall() throws CallStateException {
        this.mCT.rejectCall();
    }

    public void removeReferences() {
        Rlog.d(LOG_TAG, "removeReferences");
        super.removeReferences();
        this.mCT = null;
        this.mSS = null;
    }

    public /* bridge */ /* synthetic */ void saveClirSetting(int i) {
        super.saveClirSetting(i);
    }

    public /* bridge */ /* synthetic */ void selectNetworkManually(OperatorInfo operatorInfo, Message message) {
        super.selectNetworkManually(operatorInfo, message);
    }

    public void sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG, "sendDtmf called with invalid character '" + c + "'");
        } else if (this.mCT.mState == PhoneConstants.State.OFFHOOK) {
            this.mCT.sendDtmf(c, null);
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

    /* Access modifiers changed, original: 0000 */
    public void sendErrorResponse(Message message) {
        Rlog.d(LOG_TAG, "sendErrorResponse");
        if (message != null) {
            AsyncResult.forMessage(message, null, new CommandException(Error.GENERIC_FAILURE));
            message.sendToTarget();
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void sendErrorResponse(Message message, ImsReasonInfo imsReasonInfo) {
        Rlog.d(LOG_TAG, "sendErrorResponse reasonCode=" + imsReasonInfo.getCode());
        if (message != null) {
            AsyncResult.forMessage(message, null, getCommandException(imsReasonInfo.getCode()));
            message.sendToTarget();
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void sendErrorResponse(Message message, Throwable th) {
        Rlog.d(LOG_TAG, "sendErrorResponse");
        if (message != null) {
            AsyncResult.forMessage(message, null, getCommandException(th));
            message.sendToTarget();
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void sendUSSD(String str, Message message) {
        this.mCT.sendUSSD(str, message);
    }

    public void sendUssdResponse(String str) {
        Rlog.d(LOG_TAG, "sendUssdResponse");
        ImsPhoneMmiCode newFromUssdUserInput = ImsPhoneMmiCode.newFromUssdUserInput(str, this);
        this.mPendingMMIs.add(newFromUssdUserInput);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, newFromUssdUserInput, null));
        newFromUssdUserInput.sendUssd(str);
    }

    public void setCallBarring(String str, boolean z, String str2, Message message) {
        Rlog.d(LOG_TAG, "setCallBarring facility=" + str + ", lockState=" + z);
        try {
            this.mCT.getUtInterface().updateCallBarring(getCBTypeFromFacility(str), z ? 1 : 0, obtainMessage(40, message), null);
        } catch (ImsException e) {
            sendErrorResponse(message, e);
        }
    }

    public void setCallBarringNew(String str, boolean z, String str2, Message message) {
        if (isValidFacilityString(str)) {
            Rlog.d(LOG_TAG, "setCallBarring: facility=" + str + " lockState=" + z);
            try {
                if (this.mImsConfig != null) {
                    this.mImsConfig.setFacilityLockSH(str, z, str2, 1, message);
                } else {
                    Rlog.e(LOG_TAG, "setFacilityLockSH failed. mImsConfig is null");
                }
            } catch (ImsException e) {
                Rlog.e(LOG_TAG, "setFacilityLockSH failed. Exception = " + e);
            }
        }
    }

    public void setCallForwardingOption(int i, int i2, String str, int i3, int i4, Message message) {
        int i5 = 1;
        Rlog.d(LOG_TAG, "setCallForwardingOption action=" + i + ", reason=" + i2 + " serviceClass=" + i3);
        if (isValidCommandInterfaceCFAction(i) && isValidCommandInterfaceCFReason(i2)) {
            Cf cf = new Cf(str, i2 == 0, message);
            if (!isCfEnable(i)) {
                i5 = 0;
            }
            obtainMessage(12, i5, 0, cf);
            try {
                this.mCT.getUtInterface().updateCallForward(getActionFromCFAction(i), getConditionFromCFReason(i2), str, i3, i4, message);
            } catch (ImsException e) {
                sendErrorResponse(message, e);
            }
        } else if (message != null) {
            sendErrorResponse(message);
        }
    }

    public void setCallForwardingOption(int i, int i2, String str, int i3, Message message) {
        setCallForwardingOption(i, i2, str, 1, i3, message);
    }

    public void setCallForwardingUncondTimerOption(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message) {
        Rlog.d(LOG_TAG, "setCallForwardingUncondTimerOption action=" + i5 + ", reason=" + i6 + ", startHour=" + i + ", startMinute=" + i2 + ", endHour=" + i3 + ", endMinute=" + i4);
        if (isValidCommandInterfaceCFAction(i5) && isValidCommandInterfaceCFReason(i6)) {
            try {
                this.mCT.getUtInterface().updateCallForwardUncondTimer(i, i2, i3, i4, getActionFromCFAction(i5), getConditionFromCFReason(i6), str, obtainMessage(37, isCfEnable(i5) ? 1 : 0, 0, new Cf(str, i6 == 0, message)));
            } catch (ImsException e) {
                sendErrorResponse(message, e);
            }
        } else if (message != null) {
            sendErrorResponse(message);
        }
    }

    public void setCallWaiting(boolean z, int i, Message message) {
        Rlog.d(LOG_TAG, "setCallWaiting enable=" + z);
        try {
            this.mCT.getUtInterface().updateCallWaiting(z, i, obtainMessage(42, message));
        } catch (ImsException e) {
            sendErrorResponse(message, e);
        }
    }

    public void setCallWaiting(boolean z, Message message) {
        setCallWaiting(z, 1, message);
    }

    public /* bridge */ /* synthetic */ void setCellBroadcastSmsConfig(int[] iArr, Message message) {
        super.setCellBroadcastSmsConfig(iArr, message);
    }

    public /* bridge */ /* synthetic */ void setDataEnabled(boolean z) {
        super.setDataEnabled(z);
    }

    public /* bridge */ /* synthetic */ void setDataRoamingEnabled(boolean z) {
        super.setDataRoamingEnabled(z);
    }

    public void setImsRegistered(boolean z) {
        this.mImsRegistered = z;
    }

    public void setIncomingAnonymousCallBarring(boolean z, Message message) {
        Rlog.d(LOG_TAG, "setIncomingAnonymousCallBarring: facility=157 lockState=" + z);
        obtainMessage(47, message);
        try {
            this.mCT.getUtInterface().setFacilityLock(ImsPhoneMmiCode.SC_BAICa, z, null, 1, message);
        } catch (ImsException e) {
            sendErrorResponse(message, e);
        }
    }

    public void setIncomingSpecificDnCallBarring(int i, String[] strArr, Message message) {
        Rlog.d(LOG_TAG, "setIncomingSpecificDnCallBarring: facility=156 operation=" + i);
        obtainMessage(49, message);
        try {
            this.mCT.getUtInterface().setIncomingCallBarring(i, ImsPhoneMmiCode.SC_BS_MT, strArr, 1, message);
        } catch (ImsException e) {
            sendErrorResponse(message, e);
        }
    }

    public /* bridge */ /* synthetic */ void setLine1Number(String str, String str2, Message message) {
        super.setLine1Number(str, str2, message);
    }

    public void setMute(boolean z) {
        this.mCT.setMute(z);
    }

    public /* bridge */ /* synthetic */ void setNetworkSelectionModeAutomatic(Message message) {
        super.setNetworkSelectionModeAutomatic(message);
    }

    public void setOnEcbModeExitResponse(Handler handler, int i, Object obj) {
        this.mEcmExitRespRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnPostDialCharacter(Handler handler, int i, Object obj) {
        this.mPostDialHandler = new Registrant(handler, i, obj);
    }

    public void setOutgoingCallerIdDisplay(int i, Message message) {
        Rlog.d(LOG_TAG, "setCLIR action= " + i);
        try {
            this.mCT.getUtInterface().updateCLIR(i, obtainMessage(44, message));
        } catch (ImsException e) {
            sendErrorResponse(message, e);
        }
    }

    public /* bridge */ /* synthetic */ void setRadioPower(boolean z) {
        super.setRadioPower(z);
    }

    /* Access modifiers changed, original: 0000 */
    public void setServiceState(int i) {
        this.mSS.setState(i);
        updateDataServiceState();
    }

    public void setUiTTYMode(int i, Message message) {
        this.mCT.setUiTTYMode(i, message);
    }

    public /* bridge */ /* synthetic */ void setVoiceMailNumber(String str, String str2, Message message) {
        super.setVoiceMailNumber(str, str2, message);
    }

    public void startDtmf(char c) {
        if (PhoneNumberUtils.is12Key(c) || (c >= 'A' && c <= 'D')) {
            this.mCT.startDtmf(c);
        } else {
            Rlog.e(LOG_TAG, "startDtmf called with invalid character '" + c + "'");
        }
    }

    public void stopDtmf() {
        this.mCT.stopDtmf();
    }

    public void switchHoldingAndActive() throws CallStateException {
        this.mCT.switchWaitingOrHoldingAndActive();
    }

    public /* bridge */ /* synthetic */ void unregisterForOnHoldTone(Handler handler) {
        super.unregisterForOnHoldTone(handler);
    }

    public /* bridge */ /* synthetic */ void unregisterForRingbackTone(Handler handler) {
        super.unregisterForRingbackTone(handler);
    }

    public void unregisterForSilentRedial(Handler handler) {
        this.mSilentRedialRegistrants.remove(handler);
    }

    public void unregisterForSuppServiceNotification(Handler handler) {
        this.mSsnRegistrants.remove(handler);
    }

    public /* bridge */ /* synthetic */ void unregisterForTtyModeReceived(Handler handler) {
        super.unregisterForTtyModeReceived(handler);
    }

    public void unsetOnEcbModeExitResponse(Handler handler) {
        this.mEcmExitRespRegistrant.clear();
    }

    public void updateCallForwardStatus() {
        Rlog.d(LOG_TAG, "updateCallForwardStatus");
        IccRecords iccRecords = getIccRecords();
        if (iccRecords == null || !iccRecords.isCallForwardStatusStored()) {
            sendMessage(obtainMessage(39));
            return;
        }
        Rlog.d(LOG_TAG, "Callforwarding info is present on sim");
        notifyCallForwardingIndicator();
    }

    public void updateParentPhone(PhoneBase phoneBase) {
        if (!(this.mDefaultPhone == null || this.mDefaultPhone.getServiceStateTracker() == null)) {
            this.mDefaultPhone.getServiceStateTracker().unregisterForDataRegStateOrRatChanged(this);
        }
        this.mDefaultPhone = phoneBase;
        this.mPhoneId = this.mDefaultPhone.getPhoneId();
        if (this.mDefaultPhone.getServiceStateTracker() != null) {
            this.mDefaultPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(this, 46, null);
        }
        updateDataServiceState();
        Rlog.d(LOG_TAG, "updateParentPhone - Notify video capability changed " + this.mIsVideoCapable);
        notifyForVideoCapabilityChanged(this.mIsVideoCapable);
    }

    public /* bridge */ /* synthetic */ void updateServiceLocation() {
        super.updateServiceLocation();
    }
}
