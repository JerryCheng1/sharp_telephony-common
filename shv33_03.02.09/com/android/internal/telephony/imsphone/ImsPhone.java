package com.android.internal.telephony.imsphone;

import android.app.ActivityManagerNative;
import android.app.Notification.BigTextStyle;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.LinkProperties;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.provider.Telephony.ThreadsColumns;
import android.telephony.CarrierConfigManager;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import com.android.ims.ImsCallForwardInfo;
import com.android.ims.ImsEcbmStateListener;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.ims.ImsMultiEndpoint;
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
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneInternalInterface.DataActivityState;
import com.android.internal.telephony.PhoneInternalInterface.SuppService;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.RadioNVItems;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.TelephonyComponentFactory;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ImsPhone extends ImsPhoneBase {
    static final int CANCEL_ECM_TIMER = 1;
    private static final boolean DBG = true;
    private static final int DEFAULT_ECM_EXIT_TIMER_VALUE = 300000;
    private static final int EVENT_DEFAULT_PHONE_DATA_STATE_CHANGED = 50;
    private static final int EVENT_GET_CALL_BARRING_DONE = 45;
    private static final int EVENT_GET_CALL_WAITING_DONE = 47;
    private static final int EVENT_GET_CLIR_DONE = 49;
    private static final int EVENT_SET_CALL_BARRING_DONE = 44;
    private static final int EVENT_SET_CALL_WAITING_DONE = 46;
    private static final int EVENT_SET_CLIR_DONE = 48;
    private static final String LOG_TAG = "ImsPhone";
    static final int RESTART_ECM_TIMER = 0;
    private static final boolean VDBG = false;
    ImsPhoneCallTracker mCT;
    Phone mDefaultPhone;
    private Registrant mEcmExitRespRegistrant;
    private Runnable mExitEcmRunnable;
    ImsExternalCallTracker mExternalCallTracker;
    private ImsEcbmStateListener mImsEcbmStateListener;
    ImsMultiEndpoint mImsMultiEndpoint;
    private boolean mImsRegistered;
    private boolean mIsPhoneInEcmState;
    private String mLastDialString;
    private ArrayList<ImsPhoneMmiCode> mPendingMMIs;
    private BroadcastReceiver mResultReceiver;
    private ServiceState mSS;
    private final RegistrantList mSilentRedialRegistrants;
    private RegistrantList mSsnRegistrants;
    private WakeLock mWakeLock;

    private static class Cf {
        final boolean mIsCfu;
        final Message mOnComplete;
        final String mSetCfNumber;

        Cf(String cfNumber, boolean isCfu, Message onComplete) {
            this.mSetCfNumber = cfNumber;
            this.mIsCfu = isCfu;
            this.mOnComplete = onComplete;
        }
    }

    public /* bridge */ /* synthetic */ void activateCellBroadcastSms(int activate, Message response) {
        super.activateCellBroadcastSms(activate, response);
    }

    public /* bridge */ /* synthetic */ boolean disableDataConnectivity() {
        return super.disableDataConnectivity();
    }

    public /* bridge */ /* synthetic */ void disableLocationUpdates() {
        super.disableLocationUpdates();
    }

    public /* bridge */ /* synthetic */ boolean enableDataConnectivity() {
        return super.enableDataConnectivity();
    }

    public /* bridge */ /* synthetic */ void enableLocationUpdates() {
        super.enableLocationUpdates();
    }

    public /* bridge */ /* synthetic */ List getAllCellInfo() {
        return super.getAllCellInfo();
    }

    public /* bridge */ /* synthetic */ void getAvailableNetworks(Message response) {
        super.getAvailableNetworks(response);
    }

    public /* bridge */ /* synthetic */ int getBrand() {
        return super.getBrand();
    }

    public /* bridge */ /* synthetic */ boolean getCallForwardingIndicator() {
        return super.getCallForwardingIndicator();
    }

    public /* bridge */ /* synthetic */ void getCellBroadcastSmsConfig(Message response) {
        super.getCellBroadcastSmsConfig(response);
    }

    public /* bridge */ /* synthetic */ CellLocation getCellLocation() {
        return super.getCellLocation();
    }

    public /* bridge */ /* synthetic */ List getCurrentDataConnectionList() {
        return super.getCurrentDataConnectionList();
    }

    public /* bridge */ /* synthetic */ DataActivityState getDataActivityState() {
        return super.getDataActivityState();
    }

    public /* bridge */ /* synthetic */ void getDataCallList(Message response) {
        super.getDataCallList(response);
    }

    public /* bridge */ /* synthetic */ DataState getDataConnectionState() {
        return super.getDataConnectionState();
    }

    public /* bridge */ /* synthetic */ DataState getDataConnectionState(String apnType) {
        return super.getDataConnectionState(apnType);
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

    public /* bridge */ /* synthetic */ String getGroupIdLevel1() {
        return super.getGroupIdLevel1();
    }

    public /* bridge */ /* synthetic */ String getGroupIdLevel2() {
        return super.getGroupIdLevel2();
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

    public /* bridge */ /* synthetic */ boolean getIccRecordsLoaded() {
        return super.getIccRecordsLoaded();
    }

    public /* bridge */ /* synthetic */ String getIccSerialNumber() {
        return super.getIccSerialNumber();
    }

    public /* bridge */ /* synthetic */ String getImei() {
        return super.getImei();
    }

    public /* bridge */ /* synthetic */ String getLine1AlphaTag() {
        return super.getLine1AlphaTag();
    }

    public /* bridge */ /* synthetic */ String getLine1Number() {
        return super.getLine1Number();
    }

    public /* bridge */ /* synthetic */ LinkProperties getLinkProperties(String apnType) {
        return super.getLinkProperties(apnType);
    }

    public /* bridge */ /* synthetic */ String getMccMncOnSimLock() {
        return super.getMccMncOnSimLock();
    }

    public /* bridge */ /* synthetic */ String getMeid() {
        return super.getMeid();
    }

    public /* bridge */ /* synthetic */ boolean getMessageWaitingIndicator() {
        return super.getMessageWaitingIndicator();
    }

    public /* bridge */ /* synthetic */ void getNeighboringCids(Message response) {
        super.getNeighboringCids(response);
    }

    public /* bridge */ /* synthetic */ int getPhoneType() {
        return super.getPhoneType();
    }

    public /* bridge */ /* synthetic */ SignalStrength getSignalStrength() {
        return super.getSignalStrength();
    }

    public /* bridge */ /* synthetic */ String getSubscriberId() {
        return super.getSubscriberId();
    }

    public /* bridge */ /* synthetic */ String getVoiceMailAlphaTag() {
        return super.getVoiceMailAlphaTag();
    }

    public /* bridge */ /* synthetic */ String getVoiceMailNumber() {
        return super.getVoiceMailNumber();
    }

    public /* bridge */ /* synthetic */ boolean handlePinMmi(String dialString) {
        return super.handlePinMmi(dialString);
    }

    public /* bridge */ /* synthetic */ boolean isDataConnectivityPossible() {
        return super.isDataConnectivityPossible();
    }

    public /* bridge */ /* synthetic */ void migrateFrom(Phone from) {
        super.migrateFrom(from);
    }

    public /* bridge */ /* synthetic */ boolean needsOtaServiceProvisioning() {
        return super.needsOtaServiceProvisioning();
    }

    public /* bridge */ /* synthetic */ void notifyDisconnect(Connection cn) {
        super.notifyDisconnect(cn);
    }

    public /* bridge */ /* synthetic */ void notifyPhoneStateChanged() {
        super.notifyPhoneStateChanged();
    }

    public /* bridge */ /* synthetic */ void notifyPreciseCallStateChanged() {
        super.notifyPreciseCallStateChanged();
    }

    public /* bridge */ /* synthetic */ void onTtyModeReceived(int mode) {
        super.onTtyModeReceived(mode);
    }

    public /* bridge */ /* synthetic */ void registerForOnHoldTone(Handler h, int what, Object obj) {
        super.registerForOnHoldTone(h, what, obj);
    }

    public /* bridge */ /* synthetic */ void registerForRingbackTone(Handler h, int what, Object obj) {
        super.registerForRingbackTone(h, what, obj);
    }

    public /* bridge */ /* synthetic */ void registerForTtyModeReceived(Handler h, int what, Object obj) {
        super.registerForTtyModeReceived(h, what, obj);
    }

    public /* bridge */ /* synthetic */ void saveClirSetting(int commandInterfaceCLIRMode) {
        super.saveClirSetting(commandInterfaceCLIRMode);
    }

    public /* bridge */ /* synthetic */ void selectNetworkManually(OperatorInfo network, boolean persistSelection, Message response) {
        super.selectNetworkManually(network, persistSelection, response);
    }

    public /* bridge */ /* synthetic */ void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        super.setCellBroadcastSmsConfig(configValuesArray, response);
    }

    public /* bridge */ /* synthetic */ void setDataEnabled(boolean enable) {
        super.setDataEnabled(enable);
    }

    public /* bridge */ /* synthetic */ void setDataRoamingEnabled(boolean enable) {
        super.setDataRoamingEnabled(enable);
    }

    public /* bridge */ /* synthetic */ boolean setLine1Number(String alphaTag, String number, Message onComplete) {
        return super.setLine1Number(alphaTag, number, onComplete);
    }

    public /* bridge */ /* synthetic */ void setNetworkSelectionModeAutomatic(Message response) {
        super.setNetworkSelectionModeAutomatic(response);
    }

    public /* bridge */ /* synthetic */ void setRadioPower(boolean power) {
        super.setRadioPower(power);
    }

    public /* bridge */ /* synthetic */ void setVoiceMailNumber(String alphaTag, String voiceMailNumber, Message onComplete) {
        super.setVoiceMailNumber(alphaTag, voiceMailNumber, onComplete);
    }

    public /* bridge */ /* synthetic */ void startRingbackTone() {
        super.startRingbackTone();
    }

    public /* bridge */ /* synthetic */ void stopRingbackTone() {
        super.stopRingbackTone();
    }

    public /* bridge */ /* synthetic */ void unregisterForOnHoldTone(Handler h) {
        super.unregisterForOnHoldTone(h);
    }

    public /* bridge */ /* synthetic */ void unregisterForRingbackTone(Handler h) {
        super.unregisterForRingbackTone(h);
    }

    public /* bridge */ /* synthetic */ void unregisterForTtyModeReceived(Handler h) {
        super.unregisterForTtyModeReceived(h);
    }

    public /* bridge */ /* synthetic */ void updateServiceLocation() {
        super.updateServiceLocation();
    }

    public ImsPhone(Context context, PhoneNotifier notifier, Phone defaultPhone) {
        this(context, notifier, defaultPhone, false);
    }

    public ImsPhone(Context context, PhoneNotifier notifier, Phone defaultPhone, boolean unitTestMode) {
        super(LOG_TAG, context, notifier, unitTestMode);
        this.mPendingMMIs = new ArrayList();
        this.mSS = new ServiceState();
        this.mSilentRedialRegistrants = new RegistrantList();
        this.mImsRegistered = false;
        this.mSsnRegistrants = new RegistrantList();
        this.mExitEcmRunnable = new Runnable() {
            public void run() {
                ImsPhone.this.exitEmergencyCallbackMode();
            }
        };
        this.mImsEcbmStateListener = new ImsEcbmStateListener() {
            public void onECBMEntered() {
                Rlog.d(ImsPhone.LOG_TAG, "onECBMEntered");
                ImsPhone.this.handleEnterEmergencyCallbackMode();
            }

            public void onECBMExited() {
                Rlog.d(ImsPhone.LOG_TAG, "onECBMExited");
                ImsPhone.this.handleExitEmergencyCallbackMode();
            }
        };
        this.mResultReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (getResultCode() == -1) {
                    CharSequence title = intent.getCharSequenceExtra(Phone.EXTRA_KEY_ALERT_TITLE);
                    CharSequence messageAlert = intent.getCharSequenceExtra(Phone.EXTRA_KEY_ALERT_MESSAGE);
                    CharSequence messageNotification = intent.getCharSequenceExtra("notificationMessage");
                    Intent resultIntent = new Intent("android.intent.action.MAIN");
                    resultIntent.setClassName("com.android.settings", "com.android.settings.Settings$WifiCallingSettingsActivity");
                    resultIntent.putExtra(Phone.EXTRA_KEY_ALERT_SHOW, true);
                    resultIntent.putExtra(Phone.EXTRA_KEY_ALERT_TITLE, title);
                    resultIntent.putExtra(Phone.EXTRA_KEY_ALERT_MESSAGE, messageAlert);
                    String notificationTag = "wifi_calling";
                    ((NotificationManager) ImsPhone.this.mContext.getSystemService(ThreadsColumns.NOTIFICATION)).notify("wifi_calling", 1, new Builder(ImsPhone.this.mContext).setSmallIcon(17301642).setContentTitle(title).setContentText(messageNotification).setAutoCancel(true).setContentIntent(PendingIntent.getActivity(ImsPhone.this.mContext, 0, resultIntent, 134217728)).setStyle(new BigTextStyle().bigText(messageNotification)).build());
                }
            }
        };
        this.mDefaultPhone = defaultPhone;
        this.mCT = TelephonyComponentFactory.getInstance().makeImsPhoneCallTracker(this);
        this.mExternalCallTracker = TelephonyComponentFactory.getInstance().makeImsExternalCallTracker(this, this.mCT);
        try {
            this.mImsMultiEndpoint = this.mCT.getMultiEndpointInterface();
            this.mImsMultiEndpoint.setExternalCallStateListener(this.mExternalCallTracker.getExternalCallStateListener());
        } catch (ImsException e) {
            Rlog.i(LOG_TAG, "ImsMultiEndpointInterface is not available.");
        }
        this.mSS.setStateOff();
        this.mPhoneId = this.mDefaultPhone.getPhoneId();
        this.mIsPhoneInEcmState = SystemProperties.getBoolean("ril.cdma.inecmmode", false);
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, LOG_TAG);
        this.mWakeLock.setReferenceCounted(false);
        if (this.mDefaultPhone.getServiceStateTracker() != null) {
            this.mDefaultPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(this, 50, null);
        }
        updateDataServiceState();
    }

    public void dispose() {
        Rlog.d(LOG_TAG, "dispose");
        this.mPendingMMIs.clear();
        this.mCT.dispose();
        if (this.mDefaultPhone != null && this.mDefaultPhone.getServiceStateTracker() != null) {
            this.mDefaultPhone.getServiceStateTracker().unregisterForDataRegStateOrRatChanged(this);
        }
    }

    public ServiceState getServiceState() {
        return this.mSS;
    }

    /* Access modifiers changed, original: 0000 */
    public void setServiceState(int state) {
        this.mSS.setVoiceRegState(state);
        updateDataServiceState();
    }

    public CallTracker getCallTracker() {
        return this.mCT;
    }

    public ImsExternalCallTracker getExternalCallTracker() {
        return this.mExternalCallTracker;
    }

    public List<? extends ImsPhoneMmiCode> getPendingMmiCodes() {
        return this.mPendingMMIs;
    }

    public void acceptCall(int videoState) throws CallStateException {
        this.mCT.acceptCall(videoState);
    }

    public void rejectCall() throws CallStateException {
        this.mCT.rejectCall();
    }

    public void switchHoldingAndActive() throws CallStateException {
        this.mCT.switchWaitingOrHoldingAndActive();
    }

    public boolean canConference() {
        return this.mCT.canConference();
    }

    public boolean canDial() {
        return this.mCT.canDial();
    }

    public void conference() {
        this.mCT.conference();
    }

    public void clearDisconnected() {
        this.mCT.clearDisconnected();
    }

    public boolean canTransfer() {
        return this.mCT.canTransfer();
    }

    public void explicitCallTransfer() {
        this.mCT.explicitCallTransfer();
    }

    public ImsPhoneCall getForegroundCall() {
        return this.mCT.mForegroundCall;
    }

    public ImsPhoneCall getBackgroundCall() {
        return this.mCT.mBackgroundCall;
    }

    public ImsPhoneCall getRingingCall() {
        return this.mCT.mRingingCall;
    }

    private boolean handleCallDeflectionIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }
        if (getRingingCall().getState() != State.IDLE) {
            Rlog.d(LOG_TAG, "MmiCode 0: rejectCall");
            try {
                this.mCT.rejectCall();
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "reject failed", e);
                notifySuppServiceFailed(SuppService.REJECT);
            }
        } else if (getBackgroundCall().getState() != State.IDLE) {
            Rlog.d(LOG_TAG, "MmiCode 0: hangupWaitingOrBackground");
            try {
                this.mCT.hangup(getBackgroundCall());
            } catch (CallStateException e2) {
                Rlog.d(LOG_TAG, "hangup failed", e2);
            }
        }
        return true;
    }

    private boolean handleCallWaitingIncallSupplementaryService(String dialString) {
        int len = dialString.length();
        if (len > 2) {
            return false;
        }
        ImsPhoneCall call = getForegroundCall();
        if (len > 1) {
            try {
                Rlog.d(LOG_TAG, "not support 1X SEND");
                notifySuppServiceFailed(SuppService.HANGUP);
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "hangup failed", e);
                notifySuppServiceFailed(SuppService.HANGUP);
            }
        } else if (call.getState() != State.IDLE) {
            Rlog.d(LOG_TAG, "MmiCode 1: hangup foreground");
            this.mCT.hangup(call);
        } else {
            Rlog.d(LOG_TAG, "MmiCode 1: switchWaitingOrHoldingAndActive");
            this.mCT.switchWaitingOrHoldingAndActive();
        }
        return true;
    }

    private boolean handleCallHoldIncallSupplementaryService(String dialString) {
        int len = dialString.length();
        if (len > 2) {
            return false;
        }
        if (len > 1) {
            Rlog.d(LOG_TAG, "separate not supported");
            notifySuppServiceFailed(SuppService.SEPARATE);
        } else {
            try {
                if (getRingingCall().getState() != State.IDLE) {
                    Rlog.d(LOG_TAG, "MmiCode 2: accept ringing call");
                    this.mCT.acceptCall(2);
                } else {
                    Rlog.d(LOG_TAG, "MmiCode 2: switchWaitingOrHoldingAndActive");
                    this.mCT.switchWaitingOrHoldingAndActive();
                }
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "switch failed", e);
                notifySuppServiceFailed(SuppService.SWITCH);
            }
        }
        return true;
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
        Rlog.d(LOG_TAG, "MmiCode 4: not support explicit call transfer");
        notifySuppServiceFailed(SuppService.TRANSFER);
        return true;
    }

    private boolean handleCcbsIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }
        Rlog.i(LOG_TAG, "MmiCode 5: CCBS not supported!");
        notifySuppServiceFailed(SuppService.UNKNOWN);
        return true;
    }

    public void notifySuppSvcNotification(SuppServiceNotification suppSvc) {
        Rlog.d(LOG_TAG, "notifySuppSvcNotification: suppSvc = " + suppSvc);
        this.mSsnRegistrants.notifyRegistrants(new AsyncResult(null, suppSvc, null));
    }

    public boolean handleInCallMmiCommands(String dialString) {
        if (!isInCall() || TextUtils.isEmpty(dialString)) {
            return false;
        }
        boolean result = false;
        switch (dialString.charAt(0)) {
            case EVENT_SET_CLIR_DONE /*48*/:
                result = handleCallDeflectionIncallSupplementaryService(dialString);
                break;
            case '1':
                result = handleCallWaitingIncallSupplementaryService(dialString);
                break;
            case '2':
                result = handleCallHoldIncallSupplementaryService(dialString);
                break;
            case RadioNVItems.RIL_NV_CDMA_PRL_VERSION /*51*/:
                result = handleMultipartyIncallSupplementaryService(dialString);
                break;
            case RadioNVItems.RIL_NV_CDMA_BC10 /*52*/:
                result = handleEctIncallSupplementaryService(dialString);
                break;
            case RadioNVItems.RIL_NV_CDMA_BC14 /*53*/:
                result = handleCcbsIncallSupplementaryService(dialString);
                break;
        }
        return result;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isInCall() {
        State foregroundCallState = getForegroundCall().getState();
        State backgroundCallState = getBackgroundCall().getState();
        State ringingCallState = getRingingCall().getState();
        if (foregroundCallState.isAlive() || backgroundCallState.isAlive()) {
            return true;
        }
        return ringingCallState.isAlive();
    }

    public void notifyNewRingingConnection(Connection c) {
        this.mDefaultPhone.notifyNewRingingConnectionP(c);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyUnknownConnection(Connection c) {
        this.mDefaultPhone.notifyUnknownConnectionP(c);
    }

    public void notifyForVideoCapabilityChanged(boolean isVideoCapable) {
        this.mIsVideoCapable = isVideoCapable;
        this.mDefaultPhone.notifyForVideoCapabilityChanged(isVideoCapable);
    }

    public Connection dial(String dialString, int videoState) throws CallStateException {
        return dialInternal(dialString, videoState, null);
    }

    public Connection dial(String dialString, UUSInfo uusInfo, int videoState, Bundle intentExtras) throws CallStateException {
        return dialInternal(dialString, videoState, intentExtras);
    }

    public Connection dial(String dialString, UUSInfo uusInfo, int videoState, Bundle intentExtras, int prefix) throws CallStateException {
        return dialInternal(dialString, videoState, intentExtras, prefix);
    }

    public Connection dial(String dialString, int videoState, int prefix) throws CallStateException {
        return dial(dialString, null, videoState, null, prefix);
    }

    private Connection dialInternal(String dialString, int videoState, Bundle intentExtras) throws CallStateException {
        if (TelBrand.IS_DCM) {
            return dialInternal(dialString, videoState, intentExtras, 0);
        }
        boolean isConferenceUri = false;
        boolean isSkipSchemaParsing = false;
        if (intentExtras != null) {
            isConferenceUri = intentExtras.getBoolean("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
            isSkipSchemaParsing = intentExtras.getBoolean("org.codeaurora.extra.SKIP_SCHEMA_PARSING", false);
        }
        String newDialString = dialString;
        if (!(isConferenceUri || isSkipSchemaParsing)) {
            newDialString = PhoneNumberUtils.stripSeparators(dialString);
        }
        if (handleInCallMmiCommands(newDialString)) {
            return null;
        }
        if (this.mDefaultPhone.getPhoneType() == 2) {
            return this.mCT.dial(dialString, videoState, intentExtras);
        }
        ImsPhoneMmiCode mmi = ImsPhoneMmiCode.newFromDialString(PhoneNumberUtils.extractNetworkPortionAlt(newDialString), this);
        Rlog.d(LOG_TAG, "dialing w/ mmi '" + mmi + "'...");
        if (mmi == null) {
            return this.mCT.dial(dialString, videoState, intentExtras);
        }
        if (mmi.isTemporaryModeCLIR()) {
            return this.mCT.dial(mmi.getDialingNumber(), mmi.getCLIRMode(), videoState, intentExtras);
        }
        if (mmi.isSupportedOverImsPhone()) {
            this.mPendingMMIs.add(mmi);
            this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();
            return null;
        }
        throw new CallStateException(Phone.CS_FALLBACK);
    }

    private Connection dialInternal(String dialString, int videoState, Bundle intentExtras, int prefix) throws CallStateException {
        boolean isConferenceUri = false;
        boolean isSkipSchemaParsing = false;
        if (intentExtras != null) {
            isConferenceUri = intentExtras.getBoolean("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
            isSkipSchemaParsing = intentExtras.getBoolean("org.codeaurora.extra.SKIP_SCHEMA_PARSING", false);
        }
        String newDialString = dialString;
        if (!(isConferenceUri || isSkipSchemaParsing)) {
            newDialString = PhoneNumberUtils.stripSeparators(dialString);
        }
        if (handleInCallMmiCommands(newDialString)) {
            return null;
        }
        if (this.mDefaultPhone.getPhoneType() == 2) {
            return this.mCT.dial((String) getDialNumberInfo(dialString, prefix).get(0), videoState, intentExtras);
        }
        ImsPhoneMmiCode mmi = ImsPhoneMmiCode.newFromDialString(PhoneNumberUtils.extractNetworkPortionAlt(newDialString), this);
        Rlog.d(LOG_TAG, "dialing w/ mmi '" + mmi + "'...");
        if (mmi == null) {
            return this.mCT.dial((String) getDialNumberInfo(dialString, prefix).get(0), videoState, intentExtras);
        } else if (mmi.isTemporaryModeCLIR()) {
            if (prefix >= 4) {
                prefix -= 4;
            }
            return this.mCT.dial((String) getDialNumberInfo(mmi.getDialingNumber(), prefix).get(0), mmi.getCLIRMode(), videoState, intentExtras);
        } else if (mmi.isSupportedOverImsPhone()) {
            this.mPendingMMIs.add(mmi);
            this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();
            return null;
        } else {
            throw new CallStateException(Phone.CS_FALLBACK);
        }
    }

    private ArrayList<String> getDialNumberInfo(String dialString, int prefix) {
        ArrayList<String> dialInfoList = new ArrayList();
        boolean isSubAddress = false;
        Context context = getContext();
        if (context != null) {
            isSubAddress = context.getSharedPreferences("network_service_settings_private", 0).getBoolean("sub_address_setting", true);
        }
        Rlog.d(LOG_TAG, "dialString is " + dialString + " isSubAddress is " + isSubAddress + " prefix is " + prefix);
        dialInfoList.add(dialString);
        dialInfoList.add(null);
        if (!isSubAddress) {
            return dialInfoList;
        }
        String subDialString = dialString.substring(prefix);
        int subDialStringLen = subDialString.length();
        if (subDialStringLen == 0) {
            return dialInfoList;
        }
        int i = 0;
        while (i < subDialStringLen && subDialString.charAt(i) == '*') {
            i++;
        }
        if (subDialString.substring(i).length() == 0) {
            return dialInfoList;
        }
        int startPos = subDialString.substring(i).indexOf(42);
        if (startPos > 0) {
            String dialAddress;
            int startIndexOfDialString = (prefix + i) + startPos;
            subDialString = dialString.substring(0, startIndexOfDialString);
            String subAddress = dialString.substring(startIndexOfDialString + 1);
            if (subAddress.equals("")) {
                dialAddress = subDialString;
            } else {
                dialAddress = subDialString + "&" + subAddress;
            }
            dialInfoList.set(0, dialAddress);
        }
        Rlog.d(LOG_TAG, "dialInfoList.get(0) is " + ((String) dialInfoList.get(0)) + ")");
        return dialInfoList;
    }

    public void addParticipant(String dialString) throws CallStateException {
        addParticipant(dialString, null);
    }

    public void addParticipant(String dialString, Message onComplete) throws CallStateException {
        this.mCT.addParticipant(dialString, onComplete);
    }

    public void sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG, "sendDtmf called with invalid character '" + c + "'");
        } else if (this.mCT.getState() == PhoneConstants.State.OFFHOOK) {
            this.mCT.sendDtmf(c, null);
        }
    }

    public void startDtmf(char c) {
        Object obj = 1;
        if (!PhoneNumberUtils.is12Key(c) && (c < 'A' || c > 'D')) {
            obj = null;
        }
        if (obj == null) {
            Rlog.e(LOG_TAG, "startDtmf called with invalid character '" + c + "'");
        } else {
            this.mCT.startDtmf(c);
        }
    }

    public void stopDtmf() {
        this.mCT.stopDtmf();
    }

    public void notifyIncomingRing() {
        Rlog.d(LOG_TAG, "notifyIncomingRing");
        sendMessage(obtainMessage(14, new AsyncResult(null, null, null)));
    }

    public void setVoiceCallForwardingFlag(int line, boolean enable, String number) {
        IccRecords r = getIccRecords();
        if (r != null) {
            setVoiceCallForwardingFlag(r, line, enable, number);
        }
    }

    public void setMute(boolean muted) {
        this.mCT.setMute(muted);
    }

    public void setUiTTYMode(int uiTtyMode, Message onComplete) {
        this.mCT.setUiTTYMode(uiTtyMode, onComplete);
    }

    public boolean getMute() {
        return this.mCT.getMute();
    }

    public PhoneConstants.State getState() {
        return this.mCT.getState();
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

    private boolean isValidCommandInterfaceCFAction(int commandInterfaceCFAction) {
        switch (commandInterfaceCFAction) {
            case 0:
            case 1:
            case 3:
            case 4:
                return true;
            default:
                return false;
        }
    }

    private boolean isCfEnable(int action) {
        return action == 1 || action == 3;
    }

    private int getConditionFromCFReason(int reason) {
        switch (reason) {
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

    private int getCFReasonFromCondition(int condition) {
        switch (condition) {
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
                return 3;
        }
    }

    private int getActionFromCFAction(int action) {
        switch (action) {
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

    public void getOutgoingCallerIdDisplay(Message onComplete) {
        Rlog.d(LOG_TAG, "getCLIR");
        try {
            this.mCT.getUtInterface().queryCLIR(obtainMessage(49, onComplete));
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    public void setOutgoingCallerIdDisplay(int clirMode, Message onComplete) {
        Rlog.d(LOG_TAG, "setCLIR action= " + clirMode);
        try {
            this.mCT.getUtInterface().updateCLIR(clirMode, obtainMessage(EVENT_SET_CLIR_DONE, clirMode, 0, onComplete));
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        getCallForwardingOption(commandInterfaceCFReason, 1, onComplete);
    }

    public void getCallForwardingOption(int commandInterfaceCFReason, int commandInterfaceServiceClass, Message onComplete) {
        Rlog.d(LOG_TAG, "getCallForwardingOption reason=" + commandInterfaceCFReason + "serviceclass =" + commandInterfaceServiceClass);
        if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            Rlog.d(LOG_TAG, "requesting call forwarding query.");
            try {
                this.mCT.getUtInterface().queryCallForward(getConditionFromCFReason(commandInterfaceCFReason), null, commandInterfaceServiceClass, obtainMessage(13, onComplete));
            } catch (ImsException e) {
                sendErrorResponse(onComplete, e);
            }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete);
        }
    }

    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int timerSeconds, Message onComplete) {
        setCallForwardingOption(commandInterfaceCFAction, commandInterfaceCFReason, dialingNumber, 1, timerSeconds, onComplete);
    }

    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int serviceClass, int timerSeconds, Message onComplete) {
        Rlog.d(LOG_TAG, "setCallForwardingOption action=" + commandInterfaceCFAction + ", reason=" + commandInterfaceCFReason + " serviceClass=" + serviceClass);
        if (isValidCommandInterfaceCFAction(commandInterfaceCFAction) && isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            Message resp = obtainMessage(12, isCfEnable(commandInterfaceCFAction) ? 1 : 0, 0, new Cf(dialingNumber, commandInterfaceCFReason == 0, onComplete));
            try {
                this.mCT.getUtInterface().updateCallForward(getActionFromCFAction(commandInterfaceCFAction), getConditionFromCFReason(commandInterfaceCFReason), dialingNumber, serviceClass, timerSeconds, onComplete);
            } catch (ImsException e) {
                sendErrorResponse(onComplete, e);
            }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete);
        }
    }

    public void getCallWaiting(Message onComplete) {
        Rlog.d(LOG_TAG, "getCallWaiting");
        try {
            this.mCT.getUtInterface().queryCallWaiting(obtainMessage(47, onComplete));
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    public void setCallWaiting(boolean enable, Message onComplete) {
        setCallWaiting(enable, 1, onComplete);
    }

    public void setCallWaiting(boolean enable, int serviceClass, Message onComplete) {
        Rlog.d(LOG_TAG, "setCallWaiting enable=" + enable);
        try {
            this.mCT.getUtInterface().updateCallWaiting(enable, serviceClass, obtainMessage(EVENT_SET_CALL_WAITING_DONE, onComplete));
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    private int getCBTypeFromFacility(String facility) {
        if (CommandsInterface.CB_FACILITY_BAOC.equals(facility)) {
            return 2;
        }
        if (CommandsInterface.CB_FACILITY_BAOIC.equals(facility)) {
            return 3;
        }
        if (CommandsInterface.CB_FACILITY_BAOICxH.equals(facility)) {
            return 4;
        }
        if (CommandsInterface.CB_FACILITY_BAIC.equals(facility)) {
            return 1;
        }
        if (CommandsInterface.CB_FACILITY_BAICr.equals(facility)) {
            return 5;
        }
        if (CommandsInterface.CB_FACILITY_BA_ALL.equals(facility)) {
            return 7;
        }
        if (CommandsInterface.CB_FACILITY_BA_MO.equals(facility)) {
            return 8;
        }
        if (CommandsInterface.CB_FACILITY_BA_MT.equals(facility)) {
            return 9;
        }
        return 0;
    }

    public void getCallBarring(String facility, Message onComplete) {
        Rlog.d(LOG_TAG, "getCallBarring facility=" + facility);
        try {
            this.mCT.getUtInterface().queryCallBarring(getCBTypeFromFacility(facility), obtainMessage(EVENT_GET_CALL_BARRING_DONE, onComplete));
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    public void setCallBarring(String facility, boolean lockState, String password, Message onComplete) {
        int action;
        Rlog.d(LOG_TAG, "setCallBarring facility=" + facility + ", lockState=" + lockState);
        Message resp = obtainMessage(44, onComplete);
        if (lockState) {
            action = 1;
        } else {
            action = 0;
        }
        try {
            this.mCT.getUtInterface().updateCallBarring(getCBTypeFromFacility(facility), action, resp, null);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    public void sendUssdResponse(String ussdMessge) {
        Rlog.d(LOG_TAG, "sendUssdResponse");
        ImsPhoneMmiCode mmi = ImsPhoneMmiCode.newFromUssdUserInput(ussdMessge, this);
        this.mPendingMMIs.add(mmi);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        mmi.sendUssd(ussdMessge);
    }

    public void sendUSSD(String ussdString, Message response) {
        this.mCT.sendUSSD(ussdString, response);
    }

    public void cancelUSSD() {
        this.mCT.cancelUSSD();
    }

    private void sendErrorResponse(Message onComplete) {
        Rlog.d(LOG_TAG, "sendErrorResponse");
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, null, new CommandException(Error.GENERIC_FAILURE));
            onComplete.sendToTarget();
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void sendErrorResponse(Message onComplete, Throwable e) {
        Rlog.d(LOG_TAG, "sendErrorResponse");
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, null, getCommandException(e));
            onComplete.sendToTarget();
        }
    }

    private CommandException getCommandException(int code, String errorString) {
        Rlog.d(LOG_TAG, "getCommandException code= " + code + ", errorString= " + errorString);
        Error error = Error.GENERIC_FAILURE;
        switch (code) {
            case 801:
                error = Error.REQUEST_NOT_SUPPORTED;
                break;
            case 802:
                error = Error.RADIO_NOT_AVAILABLE;
                break;
            case 821:
                error = Error.PASSWORD_INCORRECT;
                break;
        }
        return new CommandException(error, errorString);
    }

    private CommandException getCommandException(Throwable e) {
        if (e instanceof ImsException) {
            return getCommandException(((ImsException) e).getCode(), e.getMessage());
        }
        Rlog.d(LOG_TAG, "getCommandException generic failure");
        return new CommandException(Error.GENERIC_FAILURE);
    }

    private void onNetworkInitiatedUssd(ImsPhoneMmiCode mmi) {
        Rlog.d(LOG_TAG, "onNetworkInitiatedUssd");
        this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
    }

    /* Access modifiers changed, original: 0000 */
    public void onIncomingUSSD(int ussdMode, String ussdMessage) {
        Rlog.d(LOG_TAG, "onIncomingUSSD ussdMode=" + ussdMode);
        boolean isUssdRequest = ussdMode == 1;
        boolean isUssdError = ussdMode != 0 ? ussdMode != 1 : false;
        ImsPhoneMmiCode found = null;
        int s = this.mPendingMMIs.size();
        for (int i = 0; i < s; i++) {
            if (((ImsPhoneMmiCode) this.mPendingMMIs.get(i)).isPendingUSSD()) {
                found = (ImsPhoneMmiCode) this.mPendingMMIs.get(i);
                break;
            }
        }
        if (found != null) {
            if (isUssdError) {
                found.onUssdFinishedError();
            } else {
                found.onUssdFinished(ussdMessage, isUssdRequest);
            }
        } else if (!isUssdError && ussdMessage != null) {
            onNetworkInitiatedUssd(ImsPhoneMmiCode.newNetworkInitiatedUssd(ussdMessage, isUssdRequest, this));
        }
    }

    public void onMMIDone(ImsPhoneMmiCode mmi) {
        if (this.mPendingMMIs.remove(mmi) || mmi.isUssdRequest()) {
            this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        }
    }

    public ArrayList<Connection> getHandoverConnection() {
        ArrayList<Connection> connList = new ArrayList();
        connList.addAll(getForegroundCall().mConnections);
        connList.addAll(getBackgroundCall().mConnections);
        connList.addAll(getRingingCall().mConnections);
        if (connList.size() > 0) {
            return connList;
        }
        return null;
    }

    public void notifySrvccState(SrvccState state) {
        this.mCT.notifySrvccState(state);
    }

    /* Access modifiers changed, original: 0000 */
    public void initiateSilentRedial() {
        AsyncResult ar = new AsyncResult(null, this.mLastDialString, null);
        if (ar != null) {
            this.mSilentRedialRegistrants.notifyRegistrants(ar);
        }
    }

    public void registerForSilentRedial(Handler h, int what, Object obj) {
        this.mSilentRedialRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForSilentRedial(Handler h) {
        this.mSilentRedialRegistrants.remove(h);
    }

    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        this.mSsnRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForSuppServiceNotification(Handler h) {
        this.mSsnRegistrants.remove(h);
    }

    public int getSubId() {
        return this.mDefaultPhone.getSubId();
    }

    public int getPhoneId() {
        return this.mDefaultPhone.getPhoneId();
    }

    public IccRecords getIccRecords() {
        return this.mDefaultPhone.getIccRecords();
    }

    private CallForwardInfo getCallForwardInfo(ImsCallForwardInfo info) {
        CallForwardInfo cfInfo = new CallForwardInfo();
        cfInfo.status = info.mStatus;
        cfInfo.reason = getCFReasonFromCondition(info.mCondition);
        if (info.mServiceClass == 80) {
            cfInfo.serviceClass = info.mServiceClass;
        } else {
            cfInfo.serviceClass = 1;
        }
        cfInfo.toa = info.mToA;
        cfInfo.number = info.mNumber;
        cfInfo.timeSeconds = info.mTimeSeconds;
        return cfInfo;
    }

    private CallForwardInfo[] handleCfQueryResult(ImsCallForwardInfo[] infos) {
        CallForwardInfo[] cfInfos = null;
        if (!(infos == null || infos.length == 0)) {
            cfInfos = new CallForwardInfo[infos.length];
        }
        IccRecords r = this.mDefaultPhone.getIccRecords();
        if (infos != null && infos.length != 0) {
            int s = infos.length;
            for (int i = 0; i < s; i++) {
                if (infos[i].mCondition == 0) {
                    if (infos[i].mServiceClass == 80) {
                        boolean z;
                        if (infos[i].mStatus == 1) {
                            z = true;
                        } else {
                            z = false;
                        }
                        setVideoCallForwardingPreference(z);
                        notifyCallForwardingIndicator();
                    } else if (r != null) {
                        setVoiceCallForwardingFlag(r, 1, infos[i].mStatus == 1, infos[i].mNumber);
                    }
                }
                cfInfos[i] = getCallForwardInfo(infos[i]);
            }
        } else if (r != null) {
            setVoiceCallForwardingFlag(r, 1, false, null);
        }
        return cfInfos;
    }

    private int[] handleCbQueryResult(ImsSsInfo[] infos) {
        int[] cbInfos = new int[]{0};
        if (infos[0].mStatus == 1) {
            cbInfos[0] = 1;
        }
        return cbInfos;
    }

    private int[] handleCwQueryResult(ImsSsInfo[] infos) {
        int[] cwInfos = new int[2];
        cwInfos[0] = 0;
        if (infos[0].mStatus == 1) {
            cwInfos[0] = 1;
            cwInfos[1] = 1;
        }
        return cwInfos;
    }

    private void sendResponse(Message onComplete, Object result, Throwable e) {
        if (onComplete != null) {
            CommandException ex = null;
            if (e != null) {
                ex = getCommandException(e);
            }
            AsyncResult.forMessage(onComplete, result, ex);
            onComplete.sendToTarget();
        }
    }

    private void updateDataServiceState() {
        if (this.mSS != null && this.mDefaultPhone.getServiceStateTracker() != null && this.mDefaultPhone.getServiceStateTracker().mSS != null) {
            ServiceState ss = this.mDefaultPhone.getServiceStateTracker().mSS;
            this.mSS.setDataRegState(ss.getDataRegState());
            this.mSS.setRilDataRadioTechnology(ss.getRilDataRadioTechnology());
            Rlog.d(LOG_TAG, "updateDataServiceState: defSs = " + ss + " imsSs = " + this.mSS);
        }
    }

    public void handleMessage(Message msg) {
        AsyncResult ar = msg.obj;
        Rlog.d(LOG_TAG, "handleMessage what=" + msg.what);
        switch (msg.what) {
            case 12:
                IccRecords r = this.mDefaultPhone.getIccRecords();
                Cf cf = ar.userObj;
                if (cf.mIsCfu && ar.exception == null && r != null) {
                    setVoiceCallForwardingFlag(r, 1, msg.arg1 == 1, cf.mSetCfNumber);
                }
                sendResponse(cf.mOnComplete, null, ar.exception);
                return;
            case 13:
                Object cfInfos = null;
                if (ar.exception == null) {
                    cfInfos = handleCfQueryResult((ImsCallForwardInfo[]) ar.result);
                }
                sendResponse((Message) ar.userObj, cfInfos, ar.exception);
                return;
            case 44:
            case EVENT_SET_CALL_WAITING_DONE /*46*/:
                break;
            case EVENT_GET_CALL_BARRING_DONE /*45*/:
            case 47:
                Object ssInfos = null;
                if (ar.exception == null) {
                    if (msg.what == EVENT_GET_CALL_BARRING_DONE) {
                        ssInfos = handleCbQueryResult((ImsSsInfo[]) ar.result);
                    } else if (msg.what == 47) {
                        ssInfos = handleCwQueryResult((ImsSsInfo[]) ar.result);
                    }
                }
                sendResponse((Message) ar.userObj, ssInfos, ar.exception);
                return;
            case EVENT_SET_CLIR_DONE /*48*/:
                if (ar.exception == null) {
                    saveClirSetting(msg.arg1);
                    break;
                }
                break;
            case 49:
                Bundle ssInfo = ar.result;
                Object clirInfo = null;
                if (ssInfo != null) {
                    clirInfo = ssInfo.getIntArray(ImsPhoneMmiCode.UT_BUNDLE_KEY_CLIR);
                }
                sendResponse((Message) ar.userObj, clirInfo, ar.exception);
                return;
            case 50:
                Rlog.d(LOG_TAG, "EVENT_DEFAULT_PHONE_DATA_STATE_CHANGED");
                updateDataServiceState();
                return;
            default:
                super.handleMessage(msg);
                return;
        }
        sendResponse((Message) ar.userObj, null, ar.exception);
    }

    public ImsEcbmStateListener getImsEcbmStateListener() {
        return this.mImsEcbmStateListener;
    }

    public boolean isInEmergencyCall() {
        return this.mCT.isInEmergencyCall();
    }

    public boolean isInEcm() {
        return this.mIsPhoneInEcmState;
    }

    private void sendEmergencyCallbackModeChange() {
        Intent intent = new Intent("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
        intent.putExtra("phoneinECMState", this.mIsPhoneInEcmState);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, getPhoneId());
        ActivityManagerNative.broadcastStickyIntent(intent, null, -1);
        Rlog.d(LOG_TAG, "sendEmergencyCallbackModeChange");
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
        if (this.mWakeLock.isHeld()) {
            this.mWakeLock.release();
        }
        if (this.mIsPhoneInEcmState) {
            this.mIsPhoneInEcmState = false;
            setSystemProperty("ril.cdma.inecmmode", "false");
        }
        sendEmergencyCallbackModeChange();
    }

    /* Access modifiers changed, original: 0000 */
    public void handleTimerInEmergencyCallbackMode(int action) {
        switch (action) {
            case 0:
                postDelayed(this.mExitEcmRunnable, SystemProperties.getLong("ro.cdma.ecmexittimer", 300000));
                ((GsmCdmaPhone) this.mDefaultPhone).notifyEcbmTimerReset(Boolean.FALSE);
                return;
            case 1:
                removeCallbacks(this.mExitEcmRunnable);
                ((GsmCdmaPhone) this.mDefaultPhone).notifyEcbmTimerReset(Boolean.TRUE);
                return;
            default:
                Rlog.e(LOG_TAG, "handleTimerInEmergencyCallbackMode, unsupported action " + action);
                return;
        }
    }

    public void setOnEcbModeExitResponse(Handler h, int what, Object obj) {
        this.mEcmExitRespRegistrant = new Registrant(h, what, obj);
    }

    public void unsetOnEcbModeExitResponse(Handler h) {
        this.mEcmExitRespRegistrant.clear();
    }

    public void onFeatureCapabilityChanged() {
        this.mDefaultPhone.getServiceStateTracker().onImsCapabilityChanged();
    }

    public boolean isVolteEnabled() {
        return this.mCT.isVolteEnabled();
    }

    public boolean isWifiCallingEnabled() {
        return this.mCT.isVowifiEnabled();
    }

    public boolean isVideoEnabled() {
        return this.mCT.isVideoCallEnabled();
    }

    public boolean isVideoWifiCallingEnabled() {
        return this.mCT.isVideoWifiCallingEnabled();
    }

    public Phone getDefaultPhone() {
        return this.mDefaultPhone;
    }

    public boolean isImsRegistered() {
        return this.mImsRegistered;
    }

    public void setImsRegistered(boolean value) {
        this.mImsRegistered = value;
    }

    public void declineCall() throws CallStateException {
        this.mCT.declineCall();
    }

    public void callEndCleanupHandOverCallIfAny() {
        this.mCT.callEndCleanupHandOverCallIfAny();
    }

    public void processDisconnectReason(ImsReasonInfo imsReasonInfo) {
        if (imsReasonInfo.mCode == 1000 && imsReasonInfo.mExtraMessage != null) {
            CarrierConfigManager configManager = (CarrierConfigManager) this.mContext.getSystemService("carrier_config");
            if (configManager == null) {
                Rlog.e(LOG_TAG, "processDisconnectReason: CarrierConfigManager is not ready");
                return;
            }
            PersistableBundle pb = configManager.getConfigForSubId(getSubId());
            if (pb == null) {
                Rlog.e(LOG_TAG, "processDisconnectReason: no config for subId " + getSubId());
                return;
            }
            String[] wfcOperatorErrorCodes = pb.getStringArray("wfc_operator_error_codes_string_array");
            if (wfcOperatorErrorCodes != null) {
                String[] wfcOperatorErrorAlertMessages = this.mContext.getResources().getStringArray(17236069);
                String[] wfcOperatorErrorNotificationMessages = this.mContext.getResources().getStringArray(17236070);
                for (int i = 0; i < wfcOperatorErrorCodes.length; i++) {
                    String[] codes = wfcOperatorErrorCodes[i].split("\\|");
                    if (codes.length != 2) {
                        Rlog.e(LOG_TAG, "Invalid carrier config: " + wfcOperatorErrorCodes[i]);
                    } else if (imsReasonInfo.mExtraMessage.startsWith(codes[0])) {
                        int codeStringLength = codes[0].length();
                        if (!Character.isLetterOrDigit(codes[0].charAt(codeStringLength - 1)) || imsReasonInfo.mExtraMessage.length() <= codeStringLength || !Character.isLetterOrDigit(imsReasonInfo.mExtraMessage.charAt(codeStringLength))) {
                            CharSequence title = this.mContext.getText(17039607);
                            int idx = Integer.parseInt(codes[1]);
                            if (idx < 0 || idx >= wfcOperatorErrorAlertMessages.length || idx >= wfcOperatorErrorNotificationMessages.length) {
                                Rlog.e(LOG_TAG, "Invalid index: " + wfcOperatorErrorCodes[i]);
                            } else {
                                CharSequence messageAlert = imsReasonInfo.mExtraMessage;
                                CharSequence messageNotification = imsReasonInfo.mExtraMessage;
                                if (!wfcOperatorErrorAlertMessages[idx].isEmpty()) {
                                    messageAlert = wfcOperatorErrorAlertMessages[idx];
                                }
                                if (!wfcOperatorErrorNotificationMessages[idx].isEmpty()) {
                                    messageNotification = wfcOperatorErrorNotificationMessages[idx];
                                }
                                ImsManager.setWfcSetting(this.mContext, false);
                                Intent intent = new Intent("com.android.ims.REGISTRATION_ERROR");
                                intent.putExtra(Phone.EXTRA_KEY_ALERT_TITLE, title);
                                intent.putExtra(Phone.EXTRA_KEY_ALERT_MESSAGE, messageAlert);
                                intent.putExtra("notificationMessage", messageNotification);
                                this.mContext.sendOrderedBroadcast(intent, null, this.mResultReceiver, null, -1, null, null);
                            }
                        }
                    } else {
                        continue;
                    }
                }
            }
        }
    }

    public boolean isUtEnabled() {
        return this.mCT.isUtEnabled();
    }

    public void getIncomingAnonymousCallBarring(Message onComplete) {
        Rlog.d(LOG_TAG, "getIncomingAnonymousCallBarring: facility=157");
        try {
            this.mCT.getUtInterface().queryFacilityLockBAICa(ImsPhoneMmiCode.SC_BAICa, null, 0, onComplete);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    public void setIncomingAnonymousCallBarring(boolean lockState, Message onComplete) {
        Rlog.d(LOG_TAG, "setIncomingAnonymousCallBarring: facility=157 lockState=" + lockState);
        try {
            this.mCT.getUtInterface().setFacilityLock(ImsPhoneMmiCode.SC_BAICa, lockState, null, 1, onComplete);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    public void getIncomingSpecificDnCallBarring(Message onComplete) {
        Rlog.d(LOG_TAG, "getIncomingSpecificDnCallBarring: facility=156");
        try {
            this.mCT.getUtInterface().queryIncomingCallBarringSDn(ImsPhoneMmiCode.SC_BS_MT, 1, onComplete);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    public void setIncomingSpecificDnCallBarring(int operation, String[] icbNum, Message onComplete) {
        Rlog.d(LOG_TAG, "setIncomingSpecificDnCallBarring: facility=156 operation=" + operation);
        try {
            this.mCT.getUtInterface().setIncomingCallBarring(operation, ImsPhoneMmiCode.SC_BS_MT, icbNum, 1, onComplete);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    public void sendEmergencyCallStateChange(boolean callActive) {
        this.mDefaultPhone.sendEmergencyCallStateChange(callActive);
    }

    public void setBroadcastEmergencyCallStateChanges(boolean broadcast) {
        this.mDefaultPhone.setBroadcastEmergencyCallStateChanges(broadcast);
    }

    public void notifyCallForwardingIndicator() {
        this.mDefaultPhone.notifyCallForwardingIndicator();
    }

    public WakeLock getWakeLock() {
        return this.mWakeLock;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("ImsPhone extends:");
        super.dump(fd, pw, args);
        pw.flush();
        pw.println("ImsPhone:");
        pw.println("  mDefaultPhone = " + this.mDefaultPhone);
        pw.println("  mPendingMMIs = " + this.mPendingMMIs);
        pw.println("  mPostDialHandler = " + this.mPostDialHandler);
        pw.println("  mSS = " + this.mSS);
        pw.println("  mWakeLock = " + this.mWakeLock);
        pw.println("  mIsPhoneInEcmState = " + this.mIsPhoneInEcmState);
        pw.println("  mEcmExitRespRegistrant = " + this.mEcmExitRespRegistrant);
        pw.println("  mSilentRedialRegistrants = " + this.mSilentRedialRegistrants);
        pw.println("  mImsRegistered = " + this.mImsRegistered);
        pw.println("  mSsnRegistrants = " + this.mSsnRegistrants);
        pw.flush();
    }
}
