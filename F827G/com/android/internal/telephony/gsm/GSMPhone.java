package com.android.internal.telephony.gsm;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.ims.ImsManager;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.RadioNVItems;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccVmNotSupportedException;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.IsimUiccRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class GSMPhone extends PhoneBase {
    public static final String CIPHERING_KEY = "ciphering_key";
    private static final boolean LOCAL_DEBUG = true;
    static final String LOG_TAG = "GSMPhone";
    public static final int MAX_RECEIVED_ID_LIST = 32;
    public static final int MESSAGE_ID_CBS_EMERGENCY = 40960;
    public static final int MESSAGE_ID_CBS_TEST_MESSAGE = 40963;
    private static final boolean VDBG = false;
    public static final String VM_NUMBER = "vm_number_key";
    GsmCallTracker mCT;
    private final RegistrantList mEcmTimerResetRegistrants;
    private String mImei;
    private String mImeiSv;
    private IsimUiccRecords mIsimUiccRecords;
    ArrayList<GsmMmiCode> mPendingMMIs;
    Registrant mPostDialHandler;
    private ArrayList<AreaMailIds> mReceivedCbsList;
    private ArrayList<AreaMailIds> mReceivedEtwsList;
    GsmServiceStateTracker mSST;
    protected SimPhoneBookInterfaceManager mSimPhoneBookIntManager;
    RegistrantList mSsnRegistrants;
    PhoneSubInfo mSubInfo;
    private String mVmNumber;

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    private static class Cfu {
        final Message mOnComplete;
        final String mSetCfNumber;

        Cfu(String cfNumber, Message onComplete) {
            this.mSetCfNumber = cfNumber;
            this.mOnComplete = onComplete;
        }
    }

    public GSMPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode) {
        super("GSM", notifier, context, ci, unitTestMode);
        this.mPendingMMIs = new ArrayList<>();
        this.mSsnRegistrants = new RegistrantList();
        this.mEcmTimerResetRegistrants = new RegistrantList();
        this.mReceivedEtwsList = new ArrayList<>();
        this.mReceivedCbsList = new ArrayList<>();
        if (ci instanceof SimulatedRadioControl) {
            this.mSimulatedRadioControl = (SimulatedRadioControl) ci;
        }
        this.mUiccController.setPhone(this);
        this.mCi.setPhoneType(1);
        this.mCT = new GsmCallTracker(this);
        this.mSST = new GsmServiceStateTracker(this);
        this.mDcTracker = new DcTracker(this);
        if (!unitTestMode) {
            this.mSimPhoneBookIntManager = new SimPhoneBookInterfaceManager(this);
            this.mSubInfo = new PhoneSubInfo(this);
        }
        this.mCi.registerForAvailable(this, 1, null);
        this.mCi.registerForOffOrNotAvailable(this, 8, null);
        this.mCi.registerForOn(this, 5, null);
        this.mCi.setOnUSSD(this, 7, null);
        this.mCi.setOnSuppServiceNotification(this, 2, null);
        this.mSST.registerForNetworkAttached(this, 19, null);
        this.mCi.setOnSs(this, 36, null);
        setProperties();
    }

    public GSMPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, int phoneId) {
        this(context, ci, notifier, false, phoneId);
    }

    public GSMPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode, int phoneId) {
        super("GSM", notifier, context, ci, unitTestMode, phoneId);
        this.mPendingMMIs = new ArrayList<>();
        this.mSsnRegistrants = new RegistrantList();
        this.mEcmTimerResetRegistrants = new RegistrantList();
        this.mReceivedEtwsList = new ArrayList<>();
        this.mReceivedCbsList = new ArrayList<>();
        if (ci instanceof SimulatedRadioControl) {
            this.mSimulatedRadioControl = (SimulatedRadioControl) ci;
        }
        this.mUiccController.setPhone(this);
        this.mCi.setPhoneType(1);
        this.mCT = new GsmCallTracker(this);
        this.mSST = new GsmServiceStateTracker(this);
        this.mDcTracker = new DcTracker(this);
        if (!unitTestMode) {
            this.mSimPhoneBookIntManager = new SimPhoneBookInterfaceManager(this);
            this.mSubInfo = new PhoneSubInfo(this);
        }
        this.mCi.registerForAvailable(this, 1, null);
        this.mCi.registerForOffOrNotAvailable(this, 8, null);
        this.mCi.registerForOn(this, 5, null);
        this.mCi.setOnUSSD(this, 7, null);
        this.mCi.setOnSuppServiceNotification(this, 2, null);
        this.mSST.registerForNetworkAttached(this, 19, null);
        this.mCi.setOnSs(this, 36, null);
        setProperties();
        log("GSMPhone: constructor: sub = " + this.mPhoneId);
        setProperties();
    }

    protected void setProperties() {
        TelephonyManager.setTelephonyProperty(this.mPhoneId, "gsm.current.phone-type", new Integer(1).toString());
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void dispose() {
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();
            this.mCi.unregisterForAvailable(this);
            unregisterForSimRecordEvents();
            this.mUiccController.setPhone(null);
            this.mCi.unregisterForOffOrNotAvailable(this);
            this.mCi.unregisterForOn(this);
            this.mSST.unregisterForNetworkAttached(this);
            this.mCi.unSetOnUSSD(this);
            this.mCi.unSetOnSuppServiceNotification(this);
            this.mCi.unSetOnSs(this);
            this.mPendingMMIs.clear();
            if (TelBrand.IS_DCM) {
                this.mReceivedEtwsList.clear();
                this.mReceivedCbsList.clear();
            }
            this.mCT.dispose();
            this.mDcTracker.dispose();
            this.mSST.dispose();
            this.mSimPhoneBookIntManager.dispose();
            this.mSubInfo.dispose();
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void removeReferences() {
        Rlog.d(LOG_TAG, "removeReferences");
        this.mSimulatedRadioControl = null;
        this.mSimPhoneBookIntManager = null;
        this.mSubInfo = null;
        this.mCT = null;
        this.mSST = null;
        super.removeReferences();
    }

    protected void finalize() {
        Rlog.d(LOG_TAG, "GSMPhone finalized");
    }

    @Override // com.android.internal.telephony.Phone
    public ServiceState getServiceState() {
        return this.mSST != null ? this.mSST.mSS : new ServiceState();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public ServiceState getBaseServiceState() {
        return this.mSST != null ? this.mSST.mSS : new ServiceState();
    }

    @Override // com.android.internal.telephony.Phone
    public CellLocation getCellLocation() {
        return this.mSST.getCellLocation();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public PhoneConstants.State getState() {
        PhoneConstants.State imsState;
        return (this.mImsPhone == null || (imsState = this.mImsPhone.getState()) == PhoneConstants.State.IDLE) ? this.mCT.mState : imsState;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public int getPhoneType() {
        return 1;
    }

    @Override // com.android.internal.telephony.PhoneBase
    public ServiceStateTracker getServiceStateTracker() {
        return this.mSST;
    }

    @Override // com.android.internal.telephony.PhoneBase
    public CallTracker getCallTracker() {
        return this.mCT;
    }

    private void updateVoiceMail() {
        int countVoiceMessages = 0;
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            countVoiceMessages = r.getVoiceMessageCount();
        }
        if (countVoiceMessages == -2) {
            countVoiceMessages = getStoredVoiceMessageCount();
        }
        Rlog.d(LOG_TAG, "updateVoiceMail countVoiceMessages = " + countVoiceMessages + " subId " + getSubId());
        setVoiceMessageCount(countVoiceMessages);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean getCallForwardingIndicator() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r == null || !r.isCallForwardStatusStored()) {
            return getCallForwardingPreference();
        }
        return r.getVoiceCallForwardingFlag();
    }

    @Override // com.android.internal.telephony.Phone
    public List<? extends MmiCode> getPendingMmiCodes() {
        return this.mPendingMMIs;
    }

    @Override // com.android.internal.telephony.Phone
    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        PhoneConstants.DataState ret = PhoneConstants.DataState.DISCONNECTED;
        if (this.mSST == null) {
            return PhoneConstants.DataState.DISCONNECTED;
        }
        if (!apnType.equals("emergency") && this.mSST.getCurrentDataConnectionState() != 0 && this.mOosIsDisconnect) {
            PhoneConstants.DataState ret2 = PhoneConstants.DataState.DISCONNECTED;
            Rlog.d(LOG_TAG, "getDataConnectionState: Data is Out of Service. ret = " + ret2);
            return ret2;
        } else if (!this.mDcTracker.isApnTypeEnabled(apnType) || !this.mDcTracker.isApnTypeActive(apnType)) {
            return PhoneConstants.DataState.DISCONNECTED;
        } else {
            switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$DctConstants$State[this.mDcTracker.getState(apnType).ordinal()]) {
                case 1:
                case 2:
                case 3:
                    return PhoneConstants.DataState.DISCONNECTED;
                case 4:
                case 5:
                    if (this.mCT.mState == PhoneConstants.State.IDLE || this.mSST.isConcurrentVoiceAndDataAllowed()) {
                        return PhoneConstants.DataState.CONNECTED;
                    }
                    return PhoneConstants.DataState.SUSPENDED;
                case 6:
                case 7:
                    return PhoneConstants.DataState.CONNECTING;
                default:
                    return ret;
            }
        }
    }

    @Override // com.android.internal.telephony.Phone
    public Phone.DataActivityState getDataActivityState() {
        Phone.DataActivityState ret = Phone.DataActivityState.NONE;
        if (this.mSST.getCurrentDataConnectionState() != 0) {
            return ret;
        }
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$DctConstants$Activity[this.mDcTracker.getActivity().ordinal()]) {
            case 1:
                return Phone.DataActivityState.DATAIN;
            case 2:
                return Phone.DataActivityState.DATAOUT;
            case 3:
                return Phone.DataActivityState.DATAINANDOUT;
            case 4:
                return Phone.DataActivityState.DORMANT;
            default:
                return Phone.DataActivityState.NONE;
        }
    }

    /* renamed from: com.android.internal.telephony.gsm.GSMPhone$1 */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$Activity = new int[DctConstants.Activity.values().length];
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State;

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[DctConstants.Activity.DATAIN.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[DctConstants.Activity.DATAOUT.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[DctConstants.Activity.DATAINANDOUT.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[DctConstants.Activity.DORMANT.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[DctConstants.State.values().length];
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.RETRYING.ordinal()] = 1;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.FAILED.ordinal()] = 2;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.IDLE.ordinal()] = 3;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTED.ordinal()] = 4;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.DISCONNECTING.ordinal()] = 5;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTING.ordinal()] = 6;
            } catch (NoSuchFieldError e10) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.SCANNING.ordinal()] = 7;
            } catch (NoSuchFieldError e11) {
            }
        }
    }

    public void notifyPhoneStateChanged() {
        this.mNotifier.notifyPhoneState(this);
    }

    public void notifyPreciseCallStateChanged() {
        super.notifyPreciseCallStateChangedP();
    }

    public void notifyNewRingingConnection(Connection c) {
        super.notifyNewRingingConnectionP(c);
    }

    public void notifyDisconnect(Connection cn) {
        this.mDisconnectRegistrants.notifyResult(cn);
        this.mNotifier.notifyDisconnectCause(cn.getDisconnectCause(), cn.getPreciseDisconnectCause());
    }

    public void notifyUnknownConnection(Connection cn) {
        super.notifyUnknownConnectionP(cn);
    }

    public void notifySuppServiceFailed(Phone.SuppService code) {
        this.mSuppServiceFailedRegistrants.notifyResult(code);
    }

    public void notifyServiceStateChanged(ServiceState ss) {
        super.notifyServiceStateChangedP(ss);
    }

    public void notifyLocationChanged() {
        this.mNotifier.notifyCellLocation(this);
    }

    @Override // com.android.internal.telephony.PhoneBase
    public void notifyCallForwardingIndicator() {
        this.mNotifier.notifyCallForwardingChanged(this);
    }

    @Override // com.android.internal.telephony.PhoneBase
    public void setSystemProperty(String property, String value) {
        TelephonyManager.setTelephonyProperty(this.mPhoneId, property, value);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        this.mSsnRegistrants.addUnique(h, what, obj);
        if (this.mSsnRegistrants.size() == 1) {
            this.mCi.setSuppServiceNotifications(true, null);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSuppServiceNotification(Handler h) {
        this.mSsnRegistrants.remove(h);
        if (this.mSsnRegistrants.size() == 0) {
            this.mCi.setSuppServiceNotifications(false, null);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void registerForSimRecordsLoaded(Handler h, int what, Object obj) {
        this.mSimRecordsLoadedRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void unregisterForSimRecordsLoaded(Handler h) {
        this.mSimRecordsLoadedRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void acceptCall(int videoState) throws CallStateException {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || !imsPhone.getRingingCall().isRinging()) {
            this.mCT.acceptCall();
        } else {
            imsPhone.acceptCall(videoState);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void deflectCall(String number) throws CallStateException {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || !imsPhone.getRingingCall().isRinging()) {
            throw new CallStateException("Deflect call NOT supported in GSM!");
        }
        imsPhone.deflectCall(number);
    }

    @Override // com.android.internal.telephony.Phone
    public void rejectCall() throws CallStateException {
        this.mCT.rejectCall();
    }

    @Override // com.android.internal.telephony.Phone
    public void switchHoldingAndActive() throws CallStateException {
        this.mCT.switchWaitingOrHoldingAndActive();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean canConference() {
        boolean canImsConference = false;
        if (this.mImsPhone != null) {
            canImsConference = this.mImsPhone.canConference();
        }
        return this.mCT.canConference() || canImsConference;
    }

    public boolean canDial() {
        return this.mCT.canDial();
    }

    @Override // com.android.internal.telephony.Phone
    public void conference() {
        if (this.mImsPhone == null || !this.mImsPhone.canConference()) {
            this.mCT.conference();
            return;
        }
        log("conference() - delegated to IMS phone");
        this.mImsPhone.conference();
    }

    @Override // com.android.internal.telephony.Phone
    public void clearDisconnected() {
        this.mCT.clearDisconnected();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean canTransfer() {
        return this.mCT.canTransfer();
    }

    @Override // com.android.internal.telephony.Phone
    public void explicitCallTransfer() {
        this.mCT.explicitCallTransfer();
    }

    @Override // com.android.internal.telephony.Phone
    public GsmCall getForegroundCall() {
        return this.mCT.mForegroundCall;
    }

    @Override // com.android.internal.telephony.Phone
    public GsmCall getBackgroundCall() {
        return this.mCT.mBackgroundCall;
    }

    @Override // com.android.internal.telephony.Phone
    public Call getRingingCall() {
        ImsPhone imsPhone = this.mImsPhone;
        if (this.mCT.mRingingCall != null && this.mCT.mRingingCall.isRinging()) {
            return this.mCT.mRingingCall;
        }
        if (imsPhone != null) {
            return imsPhone.getRingingCall();
        }
        return this.mCT.mRingingCall;
    }

    private boolean handleCallDeflectionIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }
        if (getRingingCall().getState() != Call.State.IDLE) {
            Rlog.d(LOG_TAG, "MmiCode 0: rejectCall");
            try {
                this.mCT.rejectCall();
                return true;
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "reject failed", e);
                notifySuppServiceFailed(Phone.SuppService.REJECT);
                return true;
            }
        } else if (getBackgroundCall().getState() == Call.State.IDLE) {
            return true;
        } else {
            Rlog.d(LOG_TAG, "MmiCode 0: hangupWaitingOrBackground");
            this.mCT.hangupWaitingOrBackground();
            return true;
        }
    }

    private boolean handleCallWaitingIncallSupplementaryService(String dialString) {
        int len = dialString.length();
        if (len > 2) {
            return false;
        }
        GsmCall call = getForegroundCall();
        try {
            if (len > 1) {
                int callIndex = dialString.charAt(1) - '0';
                if (callIndex >= 1 && callIndex <= 7) {
                    Rlog.d(LOG_TAG, "MmiCode 1: hangupConnectionByIndex " + callIndex);
                    this.mCT.hangupConnectionByIndex(call, callIndex);
                }
            } else if (call.getState() != Call.State.IDLE) {
                Rlog.d(LOG_TAG, "MmiCode 1: hangup foreground");
                this.mCT.hangup(call);
            } else {
                Rlog.d(LOG_TAG, "MmiCode 1: switchWaitingOrHoldingAndActive");
                this.mCT.switchWaitingOrHoldingAndActive();
            }
            return true;
        } catch (CallStateException e) {
            Rlog.d(LOG_TAG, "hangup failed", e);
            notifySuppServiceFailed(Phone.SuppService.HANGUP);
            return true;
        }
    }

    private boolean handleCallHoldIncallSupplementaryService(String dialString) {
        int len = dialString.length();
        if (len > 2) {
            return false;
        }
        GsmCall call = getForegroundCall();
        if (len > 1) {
            try {
                int callIndex = dialString.charAt(1) - '0';
                GsmConnection conn = this.mCT.getConnectionByIndex(call, callIndex);
                if (conn == null || callIndex < 1 || callIndex > 7) {
                    Rlog.d(LOG_TAG, "separate: invalid call index " + callIndex);
                    notifySuppServiceFailed(Phone.SuppService.SEPARATE);
                } else {
                    Rlog.d(LOG_TAG, "MmiCode 2: separate call " + callIndex);
                    this.mCT.separate(conn);
                }
                return true;
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "separate failed", e);
                notifySuppServiceFailed(Phone.SuppService.SEPARATE);
                return true;
            }
        } else {
            try {
                if (getRingingCall().getState() != Call.State.IDLE) {
                    Rlog.d(LOG_TAG, "MmiCode 2: accept ringing call");
                    this.mCT.acceptCall();
                } else {
                    Rlog.d(LOG_TAG, "MmiCode 2: switchWaitingOrHoldingAndActive");
                    this.mCT.switchWaitingOrHoldingAndActive();
                }
                return true;
            } catch (CallStateException e2) {
                Rlog.d(LOG_TAG, "switch failed", e2);
                notifySuppServiceFailed(Phone.SuppService.SWITCH);
                return true;
            }
        }
    }

    private boolean handleMultipartyIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }
        Rlog.d(LOG_TAG, "MmiCode 3: merge calls");
        conference();
        return true;
    }

    private boolean handleEctIncallSupplementaryService(String dialString) {
        if (dialString.length() != 1) {
            return false;
        }
        Rlog.d(LOG_TAG, "MmiCode 4: explicit call transfer");
        explicitCallTransfer();
        return true;
    }

    private boolean handleCcbsIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }
        Rlog.i(LOG_TAG, "MmiCode 5: CCBS not supported!");
        notifySuppServiceFailed(Phone.SuppService.UNKNOWN);
        return true;
    }

    @Override // com.android.internal.telephony.Phone
    public boolean handleInCallMmiCommands(String dialString) throws CallStateException {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null && imsPhone.getServiceState().getState() == 0) {
            return imsPhone.handleInCallMmiCommands(dialString);
        }
        if (isInCall() && !TextUtils.isEmpty(dialString)) {
            switch (dialString.charAt(0)) {
                case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_CDMA_SO68_ENABLED_I /* 48 */:
                    return handleCallDeflectionIncallSupplementaryService(dialString);
                case '1':
                    return handleCallWaitingIncallSupplementaryService(dialString);
                case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_MOB_TERM_HOME_I /* 50 */:
                    return handleCallHoldIncallSupplementaryService(dialString);
                case '3':
                    if (!TelBrand.IS_DCM) {
                        return handleMultipartyIncallSupplementaryService(dialString);
                    }
                    Rlog.d(LOG_TAG, "Conference avoided!");
                    return false;
                case '4':
                    return handleEctIncallSupplementaryService(dialString);
                case '5':
                    return handleCcbsIncallSupplementaryService(dialString);
                default:
                    return false;
            }
        }
        return false;
    }

    public boolean isInCall() {
        return getForegroundCall().getState().isAlive() || getBackgroundCall().getState().isAlive() || getRingingCall().getState().isAlive();
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState, Bundle extras) throws CallStateException {
        return dial(dialString, (UUSInfo) null, videoState, extras);
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState) throws CallStateException {
        return dial(dialString, (UUSInfo) null, videoState, (Bundle) null);
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, UUSInfo uusInfo, int videoState) throws CallStateException {
        return dial(dialString, uusInfo, videoState, (Bundle) null);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState, Bundle extras, int prefix) throws CallStateException {
        if (TelBrand.IS_DCM) {
            return dial(dialString, null, videoState, extras, prefix);
        }
        return null;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState, int prefix) throws CallStateException {
        if (TelBrand.IS_DCM) {
            return dial(dialString, null, videoState, null, prefix);
        }
        return null;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public Connection dial(String dialString, UUSInfo uusInfo, int videoState, int prefix) throws CallStateException {
        if (TelBrand.IS_DCM) {
            return dial(dialString, uusInfo, videoState, null, prefix);
        }
        return null;
    }

    public Connection dial(String dialString, UUSInfo uusInfo, int videoState, Bundle extras) throws CallStateException {
        return dial(dialString, uusInfo, videoState, extras, 0);
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r5v11 */
    /* JADX WARN: Type inference failed for: r5v14, types: [com.android.internal.telephony.Connection] */
    /* JADX WARN: Type inference failed for: r5v17 */
    /* JADX WARN: Type inference failed for: r5v19 */
    /* JADX WARN: Type inference failed for: r5v20 */
    /* JADX WARN: Type inference failed for: r5v21 */
    /* JADX WARN: Type inference failed for: r5v7 */
    public Connection dial(String dialString, UUSInfo uusInfo, int videoState, Bundle extras, int prefix) throws CallStateException {
        boolean useImsForEmergency = true;
        Connection connection = null;
        int i = 0;
        ImsPhone imsPhone = this.mImsPhone;
        boolean imsUseEnabled = ImsManager.isVolteEnabledByPlatform(this.mContext) && ImsManager.isEnhanced4gLteModeSettingEnabledByUser(this.mContext) && ImsManager.isNonTtyOrTtyOnVolteEnabled(this.mContext) && imsPhone != null && imsPhone.isVolteEnabled() && imsPhone.getServiceState().getState() == 0;
        if (imsPhone == null || !PhoneNumberUtils.isEmergencyNumber(dialString) || !this.mContext.getResources().getBoolean(17956996) || !ImsManager.isNonTtyOrTtyOnVolteEnabled(this.mContext) || imsPhone.getServiceState().getState() == 3) {
            useImsForEmergency = false;
        }
        Rlog.d(LOG_TAG, "imsUseEnabled = " + imsUseEnabled + ", useImsForEmergency = " + useImsForEmergency);
        if (imsUseEnabled || useImsForEmergency) {
            try {
                Rlog.d(LOG_TAG, "Trying IMS PS call");
                if (TelBrand.IS_DCM) {
                    connection = imsPhone.dial(dialString, videoState, extras, prefix);
                } else {
                    connection = imsPhone.dial(dialString, videoState, extras);
                }
                return connection;
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "IMS PS call exception " + e + "imsUseEnabled =" + imsUseEnabled + ", imsPhone =" + imsPhone);
                i = connection;
                if (!ImsPhone.CS_FALLBACK.equals(e.getMessage())) {
                    CallStateException ce = new CallStateException(e.getMessage());
                    ce.setStackTrace(e.getStackTrace());
                    throw ce;
                }
            }
        }
        if (imsPhone != null && imsPhone.isUtEnabled() && dialString.endsWith("#")) {
            try {
                Rlog.d(LOG_TAG, "Trying IMS call with UT enabled");
                if (TelBrand.IS_DCM) {
                    i = imsPhone.dial(dialString, videoState, extras, prefix);
                } else {
                    i = imsPhone.dial(dialString, videoState, extras);
                }
                return i;
            } catch (CallStateException e2) {
                Rlog.d(LOG_TAG, "IMS call UT enabled exception " + e2);
                if (!ImsPhone.CS_FALLBACK.equals(e2.getMessage())) {
                    CallStateException ce2 = new CallStateException(e2.getMessage());
                    ce2.setStackTrace(e2.getStackTrace());
                    throw ce2;
                }
            }
        }
        Rlog.d(LOG_TAG, "Trying (non-IMS) CS call");
        if (TelBrand.IS_DCM) {
            return dialInternal(dialString, null, i, prefix);
        }
        return dialInternal(dialString, null, i == true ? 1 : 0);
    }

    @Override // com.android.internal.telephony.PhoneBase
    protected Connection dialInternal(String dialString, UUSInfo uusInfo, int videoState) throws CallStateException {
        return dialInternal(dialString, uusInfo, videoState, 0);
    }

    protected Connection dialInternal(String dialString, UUSInfo uusInfo, int videoState, int prefix) throws CallStateException {
        if (!TelBrand.IS_SBM) {
            String newDialString = PhoneNumberUtils.stripSeparators(dialString);
            if (handleInCallMmiCommands(newDialString)) {
                return null;
            }
            GsmMmiCode mmi = GsmMmiCode.newFromDialString(PhoneNumberUtils.extractNetworkPortionAlt(newDialString), this, (UiccCardApplication) this.mUiccApplication.get());
            Rlog.d(LOG_TAG, "dialing w/ mmi '" + mmi + "'...");
            if (TelBrand.IS_DCM) {
                Rlog.d(LOG_TAG, "dialString is" + dialString + " prefix is " + prefix);
            }
            if (mmi == null) {
                if (!TelBrand.IS_DCM) {
                    return this.mCT.dial(newDialString, uusInfo);
                }
                return this.mCT.dial(getDialNumberInfo(newDialString, prefix).get(0), uusInfo);
            } else if (!mmi.isTemporaryModeCLIR()) {
                this.mPendingMMIs.add(mmi);
                this.mMmiRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
                mmi.processCode();
                return null;
            } else if (!TelBrand.IS_DCM) {
                return this.mCT.dial(mmi.mDialingNumber, mmi.getCLIRMode(), uusInfo);
            } else {
                return this.mCT.dial(getDialNumberInfo(mmi.mDialingNumber, prefix > mmi.mPoundString.length() ? prefix - mmi.mPoundString.length() : 0).get(0), mmi.getCLIRMode(), uusInfo);
            }
        } else {
            DialData dd = getDialData(dialString);
            if (dd == null) {
                return null;
            }
            Rlog.d(LOG_TAG, "dialing w/ mmi '" + dd.mmi + "'...");
            if (dd.mmi == null) {
                return this.mCT.dial(dd.dialString, uusInfo);
            }
            if (dd.isTemporaryModeCLIR) {
                return this.mCT.dial(dd.dialString, dd.clirMode, uusInfo);
            }
            this.mPendingMMIs.add(dd.mmi);
            this.mMmiRegistrants.notifyRegistrants(new AsyncResult((Object) null, dd.mmi, (Throwable) null));
            dd.mmi.processCode();
            return null;
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DialData {
        public int clirMode;
        public String dialString;
        public boolean isTemporaryModeCLIR;
        public GsmMmiCode mmi;

        public DialData(String s, int i, boolean b, GsmMmiCode g) {
            GSMPhone.this = r4;
            this.dialString = s;
            this.clirMode = i;
            this.isTemporaryModeCLIR = b;
            this.mmi = g;
            Rlog.d(GSMPhone.LOG_TAG, "DialData() dialString=" + this.dialString + ", clirMode=" + this.clirMode + ", isClir=" + b + ", mmi=" + g);
        }
    }

    public DialData getDialData(String dialString) throws CallStateException {
        String newDialString = PhoneNumberUtils.stripSeparators(dialString);
        if (handleInCallMmiCommands(newDialString)) {
            Rlog.d(LOG_TAG, "getDialData() return null, handleInCallMmiCommands() is false.");
            return null;
        }
        GsmMmiCode mmi = GsmMmiCode.newFromDialString(PhoneNumberUtils.extractNetworkPortionAlt(newDialString), this, (UiccCardApplication) this.mUiccApplication.get());
        if (mmi == null) {
            return new DialData(newDialString, 0, false, null);
        }
        if (mmi.isTemporaryModeCLIR()) {
            return new DialData(mmi.mDialingNumber, mmi.getCLIRMode(), true, mmi);
        }
        return new DialData(null, 0, false, mmi);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void addParticipant(String dialString) throws CallStateException {
        ImsPhone imsPhone = this.mImsPhone;
        boolean imsUseEnabled = ImsManager.isVolteEnabledByPlatform(this.mContext) && ImsManager.isEnhanced4gLteModeSettingEnabledByUser(this.mContext);
        if (!imsUseEnabled) {
            Rlog.w(LOG_TAG, "IMS is disabled: forced to CS");
        }
        if (!imsUseEnabled || imsPhone == null || !imsPhone.isVolteEnabled() || imsPhone.getServiceState().getState() != 0) {
            Rlog.e(LOG_TAG, "IMS is disabled so unable to add participant with IMS call");
            return;
        }
        try {
            Rlog.d(LOG_TAG, "Trying to add participant in IMS call");
            imsPhone.addParticipant(dialString);
        } catch (CallStateException e) {
            Rlog.d(LOG_TAG, "IMS PS call exception " + e);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public boolean handlePinMmi(String dialString) {
        GsmMmiCode mmi = GsmMmiCode.newFromDialString(dialString, this, (UiccCardApplication) this.mUiccApplication.get());
        if (mmi == null || !mmi.isPinPukCommand()) {
            return false;
        }
        this.mPendingMMIs.add(mmi);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
        mmi.processCode();
        return true;
    }

    @Override // com.android.internal.telephony.Phone
    public void sendUssdResponse(String ussdMessge) {
        GsmMmiCode mmi = GsmMmiCode.newFromUssdUserInput(ussdMessge, this, (UiccCardApplication) this.mUiccApplication.get());
        this.mPendingMMIs.add(mmi);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
        mmi.sendUssd(ussdMessge);
    }

    @Override // com.android.internal.telephony.Phone
    public void sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG, "sendDtmf called with invalid character '" + c + "'");
        } else if (this.mCT.mState == PhoneConstants.State.OFFHOOK) {
            this.mCi.sendDtmf(c, null);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void startDtmf(char c) {
        if (PhoneNumberUtils.is12Key(c) || (c >= 'A' && c <= 'D')) {
            this.mCi.startDtmf(c, null);
        } else {
            Rlog.e(LOG_TAG, "startDtmf called with invalid character '" + c + "'");
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void stopDtmf() {
        this.mCi.stopDtmf(null);
    }

    public void sendBurstDtmf(String dtmfString) {
        Rlog.e(LOG_TAG, "[GSMPhone] sendBurstDtmf() is a CDMA method");
    }

    @Override // com.android.internal.telephony.Phone
    public void setRadioPower(boolean power) {
        this.mSST.setRadioPower(power);
    }

    private void storeVoiceMailNumber(String number) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putString(VM_NUMBER + getSubId(), number);
        editor.apply();
        setSimImsi(getSubscriberId());
    }

    @Override // com.android.internal.telephony.Phone
    public String getVoiceMailNumber() {
        String[] listArray;
        String[] defaultVMNumberArray;
        IccRecords r = (IccRecords) this.mIccRecords.get();
        String number = r != null ? r.getVoiceMailNumber() : "";
        if (TextUtils.isEmpty(number)) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
            if (TelephonyManager.getDefault().isMultiSimEnabled()) {
                if (!sp.contains(VM_NUMBER + getSubId()) && sp.contains(VM_NUMBER + this.mPhoneId)) {
                    String number2 = sp.getString(VM_NUMBER + this.mPhoneId, null);
                    SharedPreferences.Editor editor = sp.edit();
                    editor.remove(VM_NUMBER + this.mPhoneId);
                    editor.putString(VM_NUMBER + getSubId(), number2);
                    editor.commit();
                }
            } else if (!sp.contains(VM_NUMBER + getSubId()) && sp.contains(VM_NUMBER)) {
                String number3 = sp.getString(VM_NUMBER, null);
                SharedPreferences.Editor editor2 = sp.edit();
                editor2.remove(VM_NUMBER);
                editor2.putString(VM_NUMBER + getSubId(), number3);
                editor2.commit();
            }
            number = sp.getString(VM_NUMBER + getSubId(), null);
        }
        if (!TextUtils.isEmpty(number) || (listArray = getContext().getResources().getStringArray(17236028)) == null || listArray.length <= 0) {
            return number;
        }
        for (int i = 0; i < listArray.length; i++) {
            if (!TextUtils.isEmpty(listArray[i]) && (defaultVMNumberArray = listArray[i].split(";")) != null && defaultVMNumberArray.length > 0) {
                if (defaultVMNumberArray.length == 1) {
                    number = defaultVMNumberArray[0];
                } else if (defaultVMNumberArray.length == 2 && !TextUtils.isEmpty(defaultVMNumberArray[1]) && defaultVMNumberArray[1].equalsIgnoreCase(getGroupIdLevel1())) {
                    return defaultVMNumberArray[0];
                }
            }
        }
        return number;
    }

    @Override // com.android.internal.telephony.Phone
    public String getVoiceMailAlphaTag() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        String ret = r != null ? r.getVoiceMailAlphaTag() : "";
        if (ret == null || ret.length() == 0) {
            return this.mContext.getText(17039364).toString();
        }
        return ret;
    }

    @Override // com.android.internal.telephony.Phone
    public String getDeviceId() {
        return this.mImei;
    }

    @Override // com.android.internal.telephony.Phone
    public String getDeviceSvn() {
        return this.mImeiSv;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public IsimRecords getIsimRecords() {
        return this.mIsimUiccRecords;
    }

    @Override // com.android.internal.telephony.Phone
    public String getImei() {
        return this.mImei;
    }

    @Override // com.android.internal.telephony.Phone
    public String getEsn() {
        Rlog.e(LOG_TAG, "[GSMPhone] getEsn() is a CDMA method");
        return "0";
    }

    @Override // com.android.internal.telephony.Phone
    public String getMeid() {
        Rlog.e(LOG_TAG, "[GSMPhone] getMeid() is a CDMA method");
        return "0";
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public String getNai() {
        IccRecords r = this.mUiccController.getIccRecords(this.mPhoneId, 2);
        if (Log.isLoggable(LOG_TAG, 2)) {
            Rlog.v(LOG_TAG, "IccRecords is " + r);
        }
        if (r != null) {
            return r.getNAI();
        }
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public String getSubscriberId() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            return r.getIMSI();
        }
        return null;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public String getMccMncOnSimLock() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            return r.getMccMncOnSimLock();
        }
        return null;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public String getImsiOnSimLock() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            return r.getImsiOnSimLock();
        }
        return null;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public int getBrand() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            return r.getBrand();
        }
        return 0;
    }

    @Override // com.android.internal.telephony.Phone
    public String getGroupIdLevel1() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            return r.getGid1();
        }
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public String getLine1Number() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            return r.getMsisdnNumber();
        }
        return null;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public String getMsisdn() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            return r.getMsisdnNumber();
        }
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public String getLine1AlphaTag() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            return r.getMsisdnAlphaTag();
        }
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.setMsisdnNumber(alphaTag, number, onComplete);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void setVoiceMailNumber(String alphaTag, String voiceMailNumber, Message onComplete) {
        this.mVmNumber = voiceMailNumber;
        Message resp = obtainMessage(20, 0, 0, onComplete);
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.setVoiceMailNumber(alphaTag, this.mVmNumber, resp);
        }
    }

    private boolean isValidCommandInterfaceCFReason(int commandInterfaceCFReason) {
        switch (commandInterfaceCFReason) {
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

    @Override // com.android.internal.telephony.PhoneBase
    public String getSystemProperty(String property, String defValue) {
        if (getUnitTestMode()) {
            return null;
        }
        return TelephonyManager.getTelephonyProperty(this.mPhoneId, property, defValue);
    }

    private boolean isValidCommandInterfaceCFAction(int commandInterfaceCFAction) {
        switch (commandInterfaceCFAction) {
            case 0:
            case 1:
            case 3:
            case 4:
                return true;
            case 2:
            default:
                return false;
        }
    }

    public void updateDataConnectionTracker() {
        ((DcTracker) this.mDcTracker).update();
    }

    protected boolean isCfEnable(int action) {
        return action == 1 || action == 3;
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        Message resp;
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null && (imsPhone.getServiceState().getState() == 0 || imsPhone.isUtEnabled())) {
            imsPhone.getCallForwardingOption(commandInterfaceCFReason, onComplete);
        } else if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            Rlog.d(LOG_TAG, "requesting call forwarding query.");
            if (commandInterfaceCFReason == 0) {
                resp = obtainMessage(13, onComplete);
            } else {
                resp = onComplete;
            }
            this.mCi.queryCallForwardStatus(commandInterfaceCFReason, 0, null, resp);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallForwardingOption(int commandInterfaceCFReason, int serviceClass, Message onComplete) {
        Message resp;
        if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            Rlog.d(LOG_TAG, "requesting call forwarding query.");
            if (commandInterfaceCFReason == 0) {
                resp = obtainMessage(13, onComplete);
            } else {
                resp = onComplete;
            }
            this.mCi.queryCallForwardStatus(commandInterfaceCFReason, serviceClass, null, resp);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int timerSeconds, Message onComplete) {
        Message resp;
        int i;
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null && (imsPhone.getServiceState().getState() == 0 || imsPhone.isUtEnabled())) {
            imsPhone.setCallForwardingOption(commandInterfaceCFAction, commandInterfaceCFReason, dialingNumber, timerSeconds, onComplete);
        } else if (isValidCommandInterfaceCFAction(commandInterfaceCFAction) && isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            if (commandInterfaceCFReason == 0) {
                Cfu cfu = new Cfu(dialingNumber, onComplete);
                if (isCfEnable(commandInterfaceCFAction)) {
                    i = 1;
                } else {
                    i = 0;
                }
                resp = obtainMessage(12, i, 0, cfu);
            } else {
                resp = onComplete;
            }
            this.mCi.setCallForward(commandInterfaceCFAction, commandInterfaceCFReason, 1, dialingNumber, timerSeconds, resp);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, int serviceClass, String dialingNumber, int timerSeconds, Message onComplete) {
        Message resp;
        if (isValidCommandInterfaceCFAction(commandInterfaceCFAction) && isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            if (commandInterfaceCFReason == 0) {
                resp = obtainMessage(12, isCfEnable(commandInterfaceCFAction) ? 1 : 0, 0, new Cfu(dialingNumber, onComplete));
            } else {
                resp = onComplete;
            }
            this.mCi.setCallForward(commandInterfaceCFAction, commandInterfaceCFReason, serviceClass, dialingNumber, timerSeconds, resp);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallForwardingUncondTimerOption(int startHour, int startMinute, int endHour, int endMinute, int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, Message onComplete) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null && (imsPhone.getServiceState().getState() == 0 || imsPhone.isUtEnabled())) {
            imsPhone.setCallForwardingUncondTimerOption(startHour, startMinute, endHour, endMinute, commandInterfaceCFAction, commandInterfaceCFReason, dialingNumber, onComplete);
        } else if (onComplete != null) {
            AsyncResult.forMessage(onComplete, (Object) null, new CommandException(CommandException.Error.GENERIC_FAILURE));
            onComplete.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallForwardingUncondTimerOption(int commandInterfaceCFReason, Message onComplete) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null && (imsPhone.getServiceState().getState() == 0 || imsPhone.isUtEnabled())) {
            imsPhone.getCallForwardingOption(commandInterfaceCFReason, onComplete);
        } else if (onComplete != null) {
            AsyncResult.forMessage(onComplete, (Object) null, new CommandException(CommandException.Error.GENERIC_FAILURE));
            onComplete.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || (imsPhone.getServiceState().getState() != 0 && !imsPhone.isUtEnabled())) {
            this.mCi.getCLIR(onComplete);
        } else {
            imsPhone.getOutgoingCallerIdDisplay(onComplete);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode, Message onComplete) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || (imsPhone.getServiceState().getState() != 0 && !imsPhone.isUtEnabled())) {
            this.mCi.setCLIR(commandInterfaceCLIRMode, obtainMessage(18, commandInterfaceCLIRMode, 0, onComplete));
        } else {
            imsPhone.setOutgoingCallerIdDisplay(commandInterfaceCLIRMode, obtainMessage(18, commandInterfaceCLIRMode, 0, onComplete));
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallWaiting(Message onComplete) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || (imsPhone.getServiceState().getState() != 0 && !imsPhone.isUtEnabled())) {
            this.mCi.queryCallWaiting(0, onComplete);
        } else {
            imsPhone.getCallWaiting(onComplete);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallWaiting(int serviceClass, Message onComplete) {
        this.mCi.queryCallWaiting(serviceClass, onComplete);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallWaiting(boolean enable, Message onComplete) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || (imsPhone.getServiceState().getState() != 0 && !imsPhone.isUtEnabled())) {
            this.mCi.setCallWaiting(enable, 1, onComplete);
        } else {
            imsPhone.setCallWaiting(enable, onComplete);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallWaiting(boolean enable, int serviceClass, Message onComplete) {
        this.mCi.setCallWaiting(enable, serviceClass, onComplete);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallBarring(String facility, boolean lockState, String password, int serviceClass, Message onComplete) {
        this.mCi.setFacilityLock(facility, lockState, password, serviceClass, onComplete);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallBarring(String facility, String password, int serviceClass, Message onComplete) {
        this.mCi.queryFacilityLock(facility, password, serviceClass, onComplete);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallBarring(String facility, boolean lockState, String password, Message onComplete) {
        this.mCi.setFacilityLock(facility, lockState, password, 1, onComplete);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallBarring(String facility, String password, Message onComplete) {
        this.mCi.queryFacilityLock(facility, password, 1, onComplete);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallBarring(String facility, Message onComplete) {
        Rlog.e(LOG_TAG, "getCallBarring: not possible in GSM");
    }

    @Override // com.android.internal.telephony.Phone
    public void getAvailableNetworks(Message response) {
        this.mCi.getAvailableNetworks(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void getNeighboringCids(Message response) {
        this.mCi.getNeighboringCids(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        this.mPostDialHandler = new Registrant(h, what, obj);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setUiTTYMode(int uiTtyMode, Message onComplete) {
        if (this.mImsPhone != null) {
            this.mImsPhone.setUiTTYMode(uiTtyMode, onComplete);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void setMute(boolean muted) {
        this.mCT.setMute(muted);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getMute() {
        return this.mCT.getMute();
    }

    @Override // com.android.internal.telephony.Phone
    public void getDataCallList(Message response) {
        this.mCi.getDataCallList(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void updateServiceLocation() {
        this.mSST.enableSingleLocationUpdate();
    }

    @Override // com.android.internal.telephony.Phone
    public void enableLocationUpdates() {
        this.mSST.enableLocationUpdates();
    }

    @Override // com.android.internal.telephony.Phone
    public void disableLocationUpdates() {
        this.mSST.disableLocationUpdates();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getDataRoamingEnabled() {
        return this.mDcTracker.getDataOnRoamingEnabled();
    }

    @Override // com.android.internal.telephony.Phone
    public void setDataRoamingEnabled(boolean enable) {
        this.mDcTracker.setDataOnRoamingEnabled(enable);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getDataEnabled() {
        return this.mDcTracker.getDataEnabled();
    }

    @Override // com.android.internal.telephony.Phone
    public void setDataEnabled(boolean enable) {
        this.mDcTracker.setDataEnabled(enable);
    }

    public void onMMIDone(GsmMmiCode mmi) {
        if (this.mPendingMMIs.remove(mmi) || mmi.isUssdRequest() || mmi.isSsInfo()) {
            this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
        }
    }

    private void updateCallForwardStatus() {
        Rlog.d(LOG_TAG, "updateCallForwardStatus got sim records");
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r == null || !r.isCallForwardStatusStored()) {
            sendMessage(obtainMessage(39));
            return;
        }
        Rlog.d(LOG_TAG, "Callforwarding info is present on sim");
        notifyCallForwardingIndicator();
    }

    private void onNetworkInitiatedUssd(GsmMmiCode mmi) {
        this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
    }

    private void onIncomingUSSD(int ussdMode, String ussdMessage) {
        boolean isUssdRelease = true;
        boolean isUssdRequest = ussdMode == 1;
        boolean isUssdError = (ussdMode == 0 || ussdMode == 1) ? false : true;
        if (ussdMode != 2) {
            isUssdRelease = false;
        }
        GsmMmiCode found = null;
        int i = 0;
        int s = this.mPendingMMIs.size();
        while (true) {
            if (i >= s) {
                break;
            } else if (this.mPendingMMIs.get(i).isPendingUSSD()) {
                found = this.mPendingMMIs.get(i);
                break;
            } else {
                i++;
            }
        }
        if (found != null) {
            if (isUssdRelease) {
                found.onUssdRelease();
            } else if (isUssdError) {
                found.onUssdFinishedError();
            } else {
                found.onUssdFinished(ussdMessage, isUssdRequest);
            }
        } else if (!isUssdError && ussdMessage != null) {
            onNetworkInitiatedUssd(GsmMmiCode.newNetworkInitiatedUssd(ussdMessage, isUssdRequest, this, (UiccCardApplication) this.mUiccApplication.get()));
        }
    }

    protected void syncClirSetting() {
        int clirSetting = PreferenceManager.getDefaultSharedPreferences(getContext()).getInt(PhoneBase.CLIR_KEY + getPhoneId(), -1);
        if (clirSetting >= 0) {
            this.mCi.setCLIR(clirSetting, null);
        }
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    @Override // com.android.internal.telephony.PhoneBase, android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 16:
            case 17:
                super.handleMessage(msg);
                return;
            default:
                if (!this.mIsTheCurrentActivePhone) {
                    Rlog.e(LOG_TAG, "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
                    return;
                }
                switch (msg.what) {
                    case 1:
                        this.mCi.getBasebandVersion(obtainMessage(6));
                        this.mCi.getIMEI(obtainMessage(9));
                        this.mCi.getIMEISV(obtainMessage(10));
                        return;
                    case 2:
                        AsyncResult ar = (AsyncResult) msg.obj;
                        SuppServiceNotification suppServiceNotification = (SuppServiceNotification) ar.result;
                        this.mSsnRegistrants.notifyRegistrants(ar);
                        return;
                    case 3:
                        updateCurrentCarrierInProvider();
                        String imsi = getSimImsi();
                        String imsiFromSIM = getSubscriberId();
                        if ((!TelBrand.IS_SBM && imsi != null && imsiFromSIM != null && !imsiFromSIM.equals(imsi)) || !(!TelBrand.IS_SBM || imsi == null || imsiFromSIM == null || imsiFromSIM == imsi)) {
                            storeVoiceMailNumber(null);
                            setCallForwardingPreference(false);
                            setSimImsi(null);
                            SubscriptionController controller = SubscriptionController.getInstance();
                            controller.removeStaleSubPreferences(VM_NUMBER);
                            controller.removeStaleSubPreferences(PhoneBase.SIM_IMSI);
                            controller.removeStaleSubPreferences(PhoneBase.CF_ENABLED);
                        }
                        this.mSimRecordsLoadedRegistrants.notifyRegistrants();
                        updateVoiceMail();
                        updateCallForwardStatus();
                        return;
                    case 4:
                    case 11:
                    case 14:
                    case 15:
                    case 16:
                    case 17:
                    case 21:
                    case 22:
                    case 23:
                    case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /* 24 */:
                    case 25:
                    case 26:
                    case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_OTKSL_I /* 27 */:
                    case 30:
                    case 31:
                    case 32:
                    case 33:
                    case 34:
                    case 35:
                    case 37:
                    case RadioNVItems.RIL_NV_MIP_PROFILE_HA_SPI /* 38 */:
                    default:
                        super.handleMessage(msg);
                        return;
                    case 5:
                        return;
                    case 6:
                        AsyncResult ar2 = (AsyncResult) msg.obj;
                        if (ar2.exception == null) {
                            Rlog.d(LOG_TAG, "Baseband version: " + ar2.result);
                            if (SubscriptionManager.isValidPhoneId(this.mPhoneId)) {
                                SystemProperties.set("gsm.version.baseband" + (this.mPhoneId == 0 ? "" : Integer.toString(this.mPhoneId)), (String) ar2.result);
                                return;
                            }
                            return;
                        }
                        return;
                    case 7:
                        String[] ussdResult = (String[]) ((AsyncResult) msg.obj).result;
                        if (ussdResult.length > 1) {
                            try {
                                onIncomingUSSD(Integer.parseInt(ussdResult[0]), ussdResult[1]);
                                return;
                            } catch (NumberFormatException e) {
                                Rlog.w(LOG_TAG, "error parsing USSD");
                                return;
                            }
                        } else {
                            return;
                        }
                    case 8:
                        for (int i = this.mPendingMMIs.size() - 1; i >= 0; i--) {
                            if (this.mPendingMMIs.get(i).isPendingUSSD()) {
                                this.mPendingMMIs.get(i).onUssdFinishedError();
                            }
                        }
                        ImsPhone imsPhone = this.mImsPhone;
                        if (imsPhone != null) {
                            imsPhone.getServiceState().setStateOff();
                        }
                        this.mRadioOffOrNotAvailableRegistrants.notifyRegistrants();
                        return;
                    case 9:
                        AsyncResult ar3 = (AsyncResult) msg.obj;
                        if (ar3.exception == null) {
                            this.mImei = (String) ar3.result;
                            return;
                        }
                        return;
                    case 10:
                        AsyncResult ar4 = (AsyncResult) msg.obj;
                        if (ar4.exception == null) {
                            this.mImeiSv = (String) ar4.result;
                            return;
                        }
                        return;
                    case 12:
                        AsyncResult ar5 = (AsyncResult) msg.obj;
                        IccRecords r = (IccRecords) this.mIccRecords.get();
                        Cfu cfu = (Cfu) ar5.userObj;
                        if (ar5.exception == null && r != null) {
                            r.setVoiceCallForwardingFlag(1, msg.arg1 == 1, cfu.mSetCfNumber);
                            setCallForwardingPreference(msg.arg1 == 1);
                        }
                        if (cfu.mOnComplete != null) {
                            AsyncResult.forMessage(cfu.mOnComplete, ar5.result, ar5.exception);
                            cfu.mOnComplete.sendToTarget();
                            return;
                        }
                        return;
                    case 13:
                        AsyncResult ar6 = (AsyncResult) msg.obj;
                        if (ar6.exception == null) {
                            handleCfuQueryResult((CallForwardInfo[]) ar6.result);
                        }
                        Message onComplete = (Message) ar6.userObj;
                        if (onComplete != null) {
                            AsyncResult.forMessage(onComplete, ar6.result, ar6.exception);
                            onComplete.sendToTarget();
                            return;
                        }
                        return;
                    case 18:
                        AsyncResult ar7 = (AsyncResult) msg.obj;
                        if (ar7.exception == null) {
                            saveClirSetting(msg.arg1);
                        }
                        Message onComplete2 = (Message) ar7.userObj;
                        if (onComplete2 != null) {
                            AsyncResult.forMessage(onComplete2, ar7.result, ar7.exception);
                            onComplete2.sendToTarget();
                            return;
                        }
                        return;
                    case 19:
                        syncClirSetting();
                        return;
                    case 20:
                        AsyncResult ar8 = (AsyncResult) msg.obj;
                        if (IccVmNotSupportedException.class.isInstance(ar8.exception)) {
                            storeVoiceMailNumber(this.mVmNumber);
                            ar8.exception = null;
                        }
                        Message onComplete3 = (Message) ar8.userObj;
                        if (onComplete3 != null) {
                            AsyncResult.forMessage(onComplete3, ar8.result, ar8.exception);
                            onComplete3.sendToTarget();
                            return;
                        }
                        return;
                    case 28:
                        AsyncResult ar9 = (AsyncResult) msg.obj;
                        if (this.mSST.mSS.getIsManualSelection()) {
                            setNetworkSelectionModeAutomatic((Message) ar9.result);
                            Rlog.d(LOG_TAG, "SET_NETWORK_SELECTION_AUTOMATIC: set to automatic");
                            return;
                        }
                        Rlog.d(LOG_TAG, "SET_NETWORK_SELECTION_AUTOMATIC: already automatic, ignore");
                        return;
                    case IccRecords.EVENT_REFRESH_OEM /* 29 */:
                        processIccRecordEvents(((Integer) ((AsyncResult) msg.obj).result).intValue());
                        return;
                    case 36:
                        Rlog.d(LOG_TAG, "Event EVENT_SS received");
                        new GsmMmiCode(this, (UiccCardApplication) this.mUiccApplication.get()).processSsData((AsyncResult) msg.obj);
                        break;
                    case 39:
                        break;
                }
                boolean cfEnabled = getCallForwardingPreference();
                Rlog.d(LOG_TAG, "Callforwarding is " + cfEnabled);
                if (cfEnabled) {
                    notifyCallForwardingIndicator();
                    return;
                }
                return;
        }
    }

    public UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(this.mPhoneId, 1);
    }

    @Override // com.android.internal.telephony.PhoneBase
    protected void setCardInPhoneBook() {
        if (this.mUiccController != null) {
            this.mSimPhoneBookIntManager.setIccCard(this.mUiccController.getUiccCard(this.mPhoneId));
        }
    }

    @Override // com.android.internal.telephony.PhoneBase
    protected void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            setCardInPhoneBook();
            UiccCardApplication newUiccApplication = this.mUiccController.getUiccCardApplication(this.mPhoneId, 3);
            IsimUiccRecords newIsimUiccRecords = null;
            if (newUiccApplication != null) {
                newIsimUiccRecords = (IsimUiccRecords) newUiccApplication.getIccRecords();
                log("New ISIM application found");
            }
            this.mIsimUiccRecords = newIsimUiccRecords;
            UiccCardApplication newUiccApplication2 = getUiccCardApplication();
            UiccCardApplication app = (UiccCardApplication) this.mUiccApplication.get();
            if (app != newUiccApplication2) {
                if (app != null) {
                    log("Removing stale icc objects.");
                    if (this.mIccRecords.get() != null) {
                        unregisterForSimRecordEvents();
                    }
                    this.mIccRecords.set(null);
                    this.mUiccApplication.set(null);
                }
                if (newUiccApplication2 != null) {
                    log("New Uicc application found");
                    this.mUiccApplication.set(newUiccApplication2);
                    this.mIccRecords.set(newUiccApplication2.getIccRecords());
                    registerForSimRecordEvents();
                }
            }
        }
    }

    private void processIccRecordEvents(int eventCode) {
        switch (eventCode) {
            case 1:
                notifyCallForwardingIndicator();
                return;
            default:
                return;
        }
    }

    public boolean updateCurrentCarrierInProvider() {
        int currentDds = SubscriptionManager.getDefaultDataSubId();
        String operatorNumeric = getOperatorNumeric();
        log("updateCurrentCarrierInProvider: mSubId = " + getSubId() + " currentDds = " + currentDds + " operatorNumeric = " + operatorNumeric);
        if (!TextUtils.isEmpty(operatorNumeric) && getSubId() == currentDds) {
            try {
                Uri uri = Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, Telephony.Carriers.CURRENT);
                ContentValues map = new ContentValues();
                map.put(Telephony.Carriers.NUMERIC, operatorNumeric);
                this.mContext.getContentResolver().insert(uri, map);
                return true;
            } catch (SQLException e) {
                Rlog.e(LOG_TAG, "Can't store current operator", e);
            }
        }
        return false;
    }

    public void saveClirSetting(int commandInterfaceCLIRMode) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putInt(PhoneBase.CLIR_KEY + getPhoneId(), commandInterfaceCLIRMode);
        if (!editor.commit()) {
            Rlog.e(LOG_TAG, "failed to commit CLIR preference");
        }
    }

    private void handleCfuQueryResult(CallForwardInfo[] infos) {
        boolean z = false;
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r == null) {
            return;
        }
        if (infos == null || infos.length == 0) {
            r.setVoiceCallForwardingFlag(1, false, null);
            return;
        }
        int s = infos.length;
        for (int i = 0; i < s; i++) {
            if ((infos[i].serviceClass & 1) != 0) {
                setCallForwardingPreference(infos[i].status == 1);
                if (infos[i].status == 1) {
                    z = true;
                }
                r.setVoiceCallForwardingFlag(1, z, infos[i].number);
                return;
            }
        }
    }

    @Override // com.android.internal.telephony.Phone
    public PhoneSubInfo getPhoneSubInfo() {
        return this.mSubInfo;
    }

    @Override // com.android.internal.telephony.Phone
    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return this.mSimPhoneBookIntManager;
    }

    @Override // com.android.internal.telephony.Phone
    public void activateCellBroadcastSms(int activate, Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] activateCellBroadcastSms() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    @Override // com.android.internal.telephony.Phone
    public void getCellBroadcastSmsConfig(Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] getCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    @Override // com.android.internal.telephony.Phone
    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] setCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean isCspPlmnEnabled() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            return r.isCspPlmnEnabled();
        }
        return false;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean isManualNetSelAllowed() {
        int i = Phone.PREFERRED_NT_MODE;
        int nwMode = PhoneFactory.calculatePreferredNetworkType(this.mContext, this.mPhoneId);
        Rlog.d(LOG_TAG, "isManualNetSelAllowed in mode = " + nwMode);
        if (!SystemProperties.getBoolean(PhoneBase.PROPERTY_MULTIMODE_CDMA, false) || (nwMode != 10 && nwMode != 7)) {
            return true;
        }
        Rlog.d(LOG_TAG, "Manual selection not supported in mode = " + nwMode);
        return false;
    }

    private void registerForSimRecordEvents() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.registerForNetworkSelectionModeAutomatic(this, 28, null);
            r.registerForRecordsEvents(this, 29, null);
            r.registerForRecordsLoaded(this, 3, null);
        }
    }

    private void unregisterForSimRecordEvents() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.unregisterForNetworkSelectionModeAutomatic(this);
            r.unregisterForRecordsEvents(this);
            r.unregisterForRecordsLoaded(this);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void exitEmergencyCallbackMode() {
        if (this.mImsPhone != null) {
            this.mImsPhone.exitEmergencyCallbackMode();
        }
    }

    @Override // com.android.internal.telephony.PhoneBase
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("GSMPhone extends:");
        super.dump(fd, pw, args);
        pw.println(" mCT=" + this.mCT);
        pw.println(" mSST=" + this.mSST);
        pw.println(" mPendingMMIs=" + this.mPendingMMIs);
        pw.println(" mSimPhoneBookIntManager=" + this.mSimPhoneBookIntManager);
        pw.println(" mSubInfo=" + this.mSubInfo);
        pw.println(" mVmNumber=" + this.mVmNumber);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean setOperatorBrandOverride(String brand) {
        UiccCard card;
        boolean status = false;
        if (!(this.mUiccController == null || (card = this.mUiccController.getUiccCard(getPhoneId())) == null || !(status = card.setOperatorBrandOverride(brand)))) {
            IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
            if (iccRecords != null) {
                SystemProperties.set("gsm.sim.operator.alpha", iccRecords.getServiceProviderName());
            }
            if (this.mSST != null) {
                this.mSST.pollState();
            }
        }
        return status;
    }

    public String getOperatorNumeric() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            return r.getOperatorNumeric();
        }
        return null;
    }

    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        ((DcTracker) this.mDcTracker).registerForAllDataDisconnected(h, what, obj);
    }

    public void unregisterForAllDataDisconnected(Handler h) {
        ((DcTracker) this.mDcTracker).unregisterForAllDataDisconnected(h);
    }

    public void setInternalDataEnabled(boolean enable, Message onCompleteMsg) {
        ((DcTracker) this.mDcTracker).setInternalDataEnabled(enable, onCompleteMsg);
    }

    public boolean setInternalDataEnabledFlag(boolean enable) {
        return ((DcTracker) this.mDcTracker).setInternalDataEnabledFlag(enable);
    }

    public void notifyEcbmTimerReset(Boolean flag) {
        this.mEcmTimerResetRegistrants.notifyResult(flag);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        this.mEcmTimerResetRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void unregisterForEcmTimerReset(Handler h) {
        this.mEcmTimerResetRegistrants.remove(h);
    }

    private int getStoredVoiceMessageCount() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        String imsi = sp.getString(PhoneBase.VM_ID + getSubId(), null);
        String currentImsi = getSubscriberId();
        Rlog.d(LOG_TAG, "Voicemail count retrieval for Imsi = xxxxxx, current Imsi = xxxxxx");
        if (imsi == null || currentImsi == null || !currentImsi.equals(imsi)) {
            return 0;
        }
        int countVoiceMessages = sp.getInt(PhoneBase.VM_COUNT + getSubId(), 0);
        Rlog.d(LOG_TAG, "Voice Mail Count from preference = " + countVoiceMessages);
        return countVoiceMessages;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.setVoiceMessageWaiting(line, countWaiting);
        } else {
            log("SIM Records not found, MWI not updated");
        }
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, "[GSMPhone] " + s);
    }

    private boolean isValidFacilityString(String facility) {
        if (facility.equals(CommandsInterface.CB_FACILITY_BAOC) || facility.equals(CommandsInterface.CB_FACILITY_BAOIC) || facility.equals(CommandsInterface.CB_FACILITY_BAOICxH) || facility.equals(CommandsInterface.CB_FACILITY_BAIC) || facility.equals(CommandsInterface.CB_FACILITY_BAICr) || facility.equals(CommandsInterface.CB_FACILITY_BA_ALL) || facility.equals(CommandsInterface.CB_FACILITY_BA_MO) || facility.equals(CommandsInterface.CB_FACILITY_BA_MT) || facility.equals(CommandsInterface.CB_FACILITY_BA_SIM) || facility.equals(CommandsInterface.CB_FACILITY_BA_FD)) {
            return true;
        }
        Rlog.e(LOG_TAG, " Invalid facility String : " + facility);
        return false;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallBarringOption(String facility, String password, Message onComplete) {
        if (isValidFacilityString(facility)) {
            this.mCi.queryFacilityLock(facility, password, 0, onComplete);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallBarringOption(String facility, boolean lockState, String password, Message onComplete) {
        if (isValidFacilityString(facility)) {
            this.mCi.setFacilityLock(facility, lockState, password, 1, onComplete);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void requestChangeCbPsw(String facility, String oldPwd, String newPwd, Message result) {
        this.mCi.changeBarringPassword(facility, oldPwd, newPwd, result);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void resetDunProfiles() {
        this.mDcTracker.resetDunProfiles();
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class AreaMailIds {
        int geographicalScope;
        int messageIdentifier;
        int serialNumber;

        public AreaMailIds(SmsCbHeader header) {
            GSMPhone.this = r3;
            if (TelBrand.IS_DCM) {
                this.geographicalScope = header.getGeographicalScope();
                this.serialNumber = header.getSerialNumber();
                this.messageIdentifier = header.getServiceCategory();
                if (!header.isEtwsPrimaryNotification() && 4352 <= this.messageIdentifier && this.messageIdentifier <= 4354) {
                    this.messageIdentifier = GSMPhone.MESSAGE_ID_CBS_EMERGENCY;
                }
            }
        }

        public boolean isSame(AreaMailIds areaMailIds) {
            if (TelBrand.IS_DCM && this.messageIdentifier == areaMailIds.messageIdentifier && this.geographicalScope == areaMailIds.geographicalScope && this.serialNumber == areaMailIds.serialNumber) {
                return true;
            }
            return false;
        }

        public int getSerialNo() {
            if (TelBrand.IS_DCM) {
                return this.serialNumber;
            }
            return -1;
        }
    }

    public boolean isReceivedMessage(SmsCbHeader header) {
        if (!TelBrand.IS_DCM) {
            return false;
        }
        AreaMailIds checkedIds = new AreaMailIds(header);
        if (header.isEtwsPrimaryNotification()) {
            if (4355 == header.getServiceCategory()) {
                Rlog.d(LOG_TAG, "discard message<0x1103>");
                return true;
            } else if (true == isReceivedEtwsMessage(checkedIds)) {
                return true;
            } else {
                if (32 == this.mReceivedEtwsList.size()) {
                    Rlog.d(LOG_TAG, "mReceivedEtwsList remove first elemet");
                    this.mReceivedEtwsList.remove(0);
                }
                this.mReceivedEtwsList.add(checkedIds);
                Rlog.d(LOG_TAG, "add new ETWS: MessageID=" + checkedIds.messageIdentifier + ", SerialNo=" + checkedIds.getSerialNo());
                return false;
            }
        } else if (40963 == header.getServiceCategory()) {
            Rlog.d(LOG_TAG, "discard message<0xA003>");
            return true;
        } else if (true == isReceivedCbsMessage(checkedIds)) {
            return true;
        } else {
            if (32 == this.mReceivedCbsList.size()) {
                Rlog.d(LOG_TAG, "mReceivedCbsList remove first elemet");
                this.mReceivedCbsList.remove(0);
            }
            Rlog.d(LOG_TAG, "add new CBS: MessageID=" + checkedIds.messageIdentifier + "(should be 40960), SerialNo=" + checkedIds.getSerialNo());
            this.mReceivedCbsList.add(checkedIds);
            return false;
        }
    }

    private boolean isReceivedEtwsMessage(AreaMailIds checkedIds) {
        if (!TelBrand.IS_DCM) {
            return false;
        }
        int numOfReceivedEtws = this.mReceivedEtwsList.size();
        Rlog.d(LOG_TAG, "Received ETWS List has " + numOfReceivedEtws + " element(s).");
        for (int i = 0; i < numOfReceivedEtws; i++) {
            if (true == checkedIds.isSame(this.mReceivedEtwsList.get(i))) {
                Rlog.d(LOG_TAG, "this ETWS has been already received. matches received ETWS: MessageID=" + checkedIds.messageIdentifier + ", SerialNo=" + checkedIds.getSerialNo());
                return true;
            }
        }
        if (4352 > checkedIds.messageIdentifier || checkedIds.messageIdentifier > 4354) {
            return isReceivedCbsMessage(checkedIds);
        }
        return isReceivedEmergencyMessage();
    }

    private boolean isReceivedEmergencyMessage() {
        if (!TelBrand.IS_DCM) {
            return false;
        }
        int numOfReceivedCbs = this.mReceivedCbsList.size();
        for (int i = 0; i < numOfReceivedCbs; i++) {
            if (40960 == this.mReceivedCbsList.get(i).messageIdentifier) {
                Rlog.d(LOG_TAG, "isReceivedCbsMessage() 0xA000 has been already received.");
                return true;
            }
        }
        return false;
    }

    private boolean isReceivedCbsMessage(AreaMailIds checkedIds) {
        if (!TelBrand.IS_DCM) {
            return false;
        }
        int numOfReceivedCbs = this.mReceivedCbsList.size();
        Rlog.d(LOG_TAG, "Received CBS List has " + numOfReceivedCbs + " element(s).");
        for (int i = 0; i < numOfReceivedCbs; i++) {
            if (true == checkedIds.isSame(this.mReceivedCbsList.get(i))) {
                Rlog.d(LOG_TAG, "this CBS has been already received. matches received CBS: MessageID=" + checkedIds.messageIdentifier + "(should be 40960), SerialNo=" + checkedIds.getSerialNo());
                return true;
            }
        }
        return false;
    }

    private ArrayList<String> getDialNumberInfo(String dialString, int prefix) {
        String subDialString;
        int subDialStringLen;
        String DialAddress;
        ArrayList<String> dialInfoList = new ArrayList<>();
        if (TelBrand.IS_DCM) {
            boolean isSubAddress = false;
            Context context = getContext();
            if (context != null) {
                isSubAddress = context.getSharedPreferences("network_service_settings_private", 0).getBoolean("sub_address_setting", true);
            }
            Rlog.d(LOG_TAG, "dialString is" + dialString + " isSubAddress is " + isSubAddress + " prefix is " + prefix);
            dialInfoList.add(dialString);
            dialInfoList.add(null);
            if (isSubAddress && (subDialStringLen = (subDialString = dialString.substring(prefix)).length()) != 0) {
                int i = 0;
                while (i < subDialStringLen && subDialString.charAt(i) == '*') {
                    i++;
                }
                if (subDialString.substring(i).length() != 0) {
                    int startPos = subDialString.substring(i).indexOf(42);
                    if (startPos > 0) {
                        int startIndexOfDialString = prefix + i + startPos;
                        String subDialString2 = dialString.substring(0, startIndexOfDialString);
                        String subAddress = dialString.substring(startIndexOfDialString + 1);
                        if (!subAddress.equals("")) {
                            DialAddress = subDialString2 + "&" + subAddress;
                        } else {
                            DialAddress = subDialString2;
                        }
                        dialInfoList.set(0, DialAddress);
                    }
                    Rlog.d(LOG_TAG, " dialInfoList.get(0) is " + dialInfoList.get(0) + ")");
                }
            }
        }
        return dialInfoList;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean isDunConnectionPossible() {
        return (!this.mSST.mSS.getRoaming() || this.mDcTracker.getDataOnRoamingEnabled()) && getState() == PhoneConstants.State.IDLE;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setMobileDataEnabledDun(boolean isEnable) {
        this.mDcTracker.setMobileDataEnabledDun(isEnable);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setProfilePdpType(int cid, String pdpType) {
        this.mDcTracker.setProfilePdpType(cid, pdpType);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean isUtEnabled() {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null) {
            return imsPhone.isUtEnabled();
        }
        Rlog.d(LOG_TAG, "isUtEnabled: called for GSM");
        return false;
    }
}
