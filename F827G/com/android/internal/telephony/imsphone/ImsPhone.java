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
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import java.util.ArrayList;
import java.util.List;
import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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
    PhoneBase mDefaultPhone;
    private Registrant mEcmExitRespRegistrant;
    private ImsConfig mImsConfig;
    private String mLastDialString;
    Registrant mPostDialHandler;
    PowerManager.WakeLock mWakeLock;
    ArrayList<ImsPhoneMmiCode> mPendingMMIs = new ArrayList<>();
    ServiceState mSS = new ServiceState();
    private final RegistrantList mSilentRedialRegistrants = new RegistrantList();
    RegistrantList mSsnRegistrants = new RegistrantList();
    private boolean mIsVideoCapable = false;
    private boolean mImsRegistered = false;
    private Runnable mExitEcmRunnable = new Runnable() { // from class: com.android.internal.telephony.imsphone.ImsPhone.1
        @Override // java.lang.Runnable
        public void run() {
            ImsPhone.this.exitEmergencyCallbackMode();
        }
    };
    ImsEcbmStateListener mImsEcbmStateListener = new ImsEcbmStateListener() { // from class: com.android.internal.telephony.imsphone.ImsPhone.2
        public void onECBMEntered() {
            Rlog.d(ImsPhone.LOG_TAG, "onECBMEntered");
            ImsPhone.this.handleEnterEmergencyCallbackMode();
        }

        public void onECBMExited() {
            Rlog.d(ImsPhone.LOG_TAG, "onECBMExited");
            ImsPhone.this.handleExitEmergencyCallbackMode();
        }
    };
    ImsPhoneCallTracker mCT = new ImsPhoneCallTracker(this);
    protected boolean mIsPhoneInEcmState = SystemProperties.getBoolean("ril.cdma.inecmmode", false);

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void activateCellBroadcastSms(int x0, Message x1) {
        super.activateCellBroadcastSms(x0, x1);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ Connection dial(String x0, UUSInfo x1, int x2) throws CallStateException {
        return super.dial(x0, x1, x2);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase
    public /* bridge */ /* synthetic */ boolean disableDataConnectivity() {
        return super.disableDataConnectivity();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void disableLocationUpdates() {
        super.disableLocationUpdates();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase
    public /* bridge */ /* synthetic */ boolean enableDataConnectivity() {
        return super.enableDataConnectivity();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void enableLocationUpdates() {
        super.enableLocationUpdates();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ List getAllCellInfo() {
        return super.getAllCellInfo();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void getAvailableNetworks(Message x0) {
        super.getAvailableNetworks(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void getCellBroadcastSmsConfig(Message x0) {
        super.getCellBroadcastSmsConfig(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ CellLocation getCellLocation() {
        return super.getCellLocation();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase
    public /* bridge */ /* synthetic */ List getCurrentDataConnectionList() {
        return super.getCurrentDataConnectionList();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ Phone.DataActivityState getDataActivityState() {
        return super.getDataActivityState();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void getDataCallList(Message x0) {
        super.getDataCallList(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ PhoneConstants.DataState getDataConnectionState() {
        return super.getDataConnectionState();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ PhoneConstants.DataState getDataConnectionState(String x0) {
        return super.getDataConnectionState(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean getDataEnabled() {
        return super.getDataEnabled();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean getDataRoamingEnabled() {
        return super.getDataRoamingEnabled();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getDeviceId() {
        return super.getDeviceId();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getDeviceSvn() {
        return super.getDeviceSvn();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getEsn() {
        return super.getEsn();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getGroupIdLevel1() {
        return super.getGroupIdLevel1();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ IccCard getIccCard() {
        return super.getIccCard();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase
    public /* bridge */ /* synthetic */ IccFileHandler getIccFileHandler() {
        return super.getIccFileHandler();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return super.getIccPhoneBookInterfaceManager();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean getIccRecordsLoaded() {
        return super.getIccRecordsLoaded();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getIccSerialNumber() {
        return super.getIccSerialNumber();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getImei() {
        return super.getImei();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getLine1AlphaTag() {
        return super.getLine1AlphaTag();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getLine1Number() {
        return super.getLine1Number();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ LinkProperties getLinkProperties(String x0) {
        return super.getLinkProperties(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getMeid() {
        return super.getMeid();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean getMessageWaitingIndicator() {
        return super.getMessageWaitingIndicator();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void getNeighboringCids(Message x0) {
        super.getNeighboringCids(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ PhoneSubInfo getPhoneSubInfo() {
        return super.getPhoneSubInfo();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ int getPhoneType() {
        return super.getPhoneType();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ SignalStrength getSignalStrength() {
        return super.getSignalStrength();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getVoiceMailAlphaTag() {
        return super.getVoiceMailAlphaTag();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getVoiceMailNumber() {
        return super.getVoiceMailNumber();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean handlePinMmi(String x0) {
        return super.handlePinMmi(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean isDataConnectivityPossible() {
        return super.isDataConnectivityPossible();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase
    public /* bridge */ /* synthetic */ void migrateFrom(PhoneBase x0) {
        super.migrateFrom(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean needsOtaServiceProvisioning() {
        return super.needsOtaServiceProvisioning();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase
    public /* bridge */ /* synthetic */ void notifyCallForwardingIndicator() {
        super.notifyCallForwardingIndicator();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase
    public /* bridge */ /* synthetic */ void onTtyModeReceived(int x0) {
        super.onTtyModeReceived(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void registerForOnHoldTone(Handler x0, int x1, Object x2) {
        super.registerForOnHoldTone(x0, x1, x2);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void registerForRingbackTone(Handler x0, int x1, Object x2) {
        super.registerForRingbackTone(x0, x1, x2);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void registerForTtyModeReceived(Handler x0, int x1, Object x2) {
        super.registerForTtyModeReceived(x0, x1, x2);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase
    public /* bridge */ /* synthetic */ void saveClirSetting(int x0) {
        super.saveClirSetting(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void selectNetworkManually(OperatorInfo x0, Message x1) {
        super.selectNetworkManually(x0, x1);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setCellBroadcastSmsConfig(int[] x0, Message x1) {
        super.setCellBroadcastSmsConfig(x0, x1);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setDataEnabled(boolean x0) {
        super.setDataEnabled(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setDataRoamingEnabled(boolean x0) {
        super.setDataRoamingEnabled(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setLine1Number(String x0, String x1, Message x2) {
        super.setLine1Number(x0, x1, x2);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setNetworkSelectionModeAutomatic(Message x0) {
        super.setNetworkSelectionModeAutomatic(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setRadioPower(boolean x0) {
        super.setRadioPower(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setVoiceMailNumber(String x0, String x1, Message x2) {
        super.setVoiceMailNumber(x0, x1, x2);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void unregisterForOnHoldTone(Handler x0) {
        super.unregisterForOnHoldTone(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void unregisterForRingbackTone(Handler x0) {
        super.unregisterForRingbackTone(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void unregisterForTtyModeReceived(Handler x0) {
        super.unregisterForTtyModeReceived(x0);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void updateServiceLocation() {
        super.updateServiceLocation();
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class Cf {
        final boolean mIsCfu;
        final Message mOnComplete;
        final String mSetCfNumber;

        Cf(String cfNumber, boolean isCfu, Message onComplete) {
            this.mSetCfNumber = cfNumber;
            this.mIsCfu = isCfu;
            this.mOnComplete = onComplete;
        }
    }

    public ImsPhone(Context context, PhoneNotifier notifier, Phone defaultPhone) {
        super(LOG_TAG, context, notifier);
        this.mDefaultPhone = (PhoneBase) defaultPhone;
        this.mSS.setStateOff();
        this.mPhoneId = this.mDefaultPhone.getPhoneId();
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

    public void updateParentPhone(PhoneBase parentPhone) {
        if (!(this.mDefaultPhone == null || this.mDefaultPhone.getServiceStateTracker() == null)) {
            this.mDefaultPhone.getServiceStateTracker().unregisterForDataRegStateOrRatChanged(this);
        }
        this.mDefaultPhone = parentPhone;
        this.mPhoneId = this.mDefaultPhone.getPhoneId();
        if (this.mDefaultPhone.getServiceStateTracker() != null) {
            this.mDefaultPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(this, 46, null);
        }
        updateDataServiceState();
        Rlog.d(LOG_TAG, "updateParentPhone - Notify video capability changed " + this.mIsVideoCapable);
        notifyForVideoCapabilityChanged(this.mIsVideoCapable);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void dispose() {
        Rlog.d(LOG_TAG, "dispose");
        this.mPendingMMIs.clear();
        this.mCT.dispose();
        if (this.mDefaultPhone != null && this.mDefaultPhone.getServiceStateTracker() != null) {
            this.mDefaultPhone.getServiceStateTracker().unregisterForDataRegStateOrRatChanged(this);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void removeReferences() {
        Rlog.d(LOG_TAG, "removeReferences");
        super.removeReferences();
        this.mCT = null;
        this.mSS = null;
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public ServiceState getServiceState() {
        return this.mSS;
    }

    public void setServiceState(int state) {
        this.mSS.setState(state);
        updateDataServiceState();
    }

    @Override // com.android.internal.telephony.PhoneBase
    public CallTracker getCallTracker() {
        return this.mCT;
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean getCallForwardingIndicator() {
        IccRecords r = getIccRecords();
        if (r == null || !r.isCallForwardStatusStored()) {
            return getCallForwardingPreference();
        }
        return r.getVoiceCallForwardingFlag();
    }

    public void updateCallForwardStatus() {
        Rlog.d(LOG_TAG, "updateCallForwardStatus");
        IccRecords r = getIccRecords();
        if (r == null || !r.isCallForwardStatusStored()) {
            sendMessage(obtainMessage(39));
            return;
        }
        Rlog.d(LOG_TAG, "Callforwarding info is present on sim");
        notifyCallForwardingIndicator();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public List<? extends ImsPhoneMmiCode> getPendingMmiCodes() {
        return this.mPendingMMIs;
    }

    @Override // com.android.internal.telephony.Phone
    public void acceptCall(int videoState) throws CallStateException {
        this.mCT.acceptCall(videoState);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void deflectCall(String number) throws CallStateException {
        this.mCT.deflectCall(number);
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
        return this.mCT.canConference();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase
    public boolean canDial() {
        return this.mCT.canDial();
    }

    @Override // com.android.internal.telephony.Phone
    public void conference() {
        this.mCT.conference();
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
    public ImsPhoneCall getForegroundCall() {
        return this.mCT.mForegroundCall;
    }

    @Override // com.android.internal.telephony.Phone
    public ImsPhoneCall getBackgroundCall() {
        return this.mCT.mBackgroundCall;
    }

    @Override // com.android.internal.telephony.Phone
    public ImsPhoneCall getRingingCall() {
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
            try {
                this.mCT.hangup(getBackgroundCall());
                return true;
            } catch (CallStateException e2) {
                Rlog.d(LOG_TAG, "hangup failed", e2);
                return true;
            }
        }
    }

    private boolean handleCallWaitingIncallSupplementaryService(String dialString) {
        int len = dialString.length();
        if (len > 2) {
            return false;
        }
        ImsPhoneCall call = getForegroundCall();
        try {
            if (len > 1) {
                Rlog.d(LOG_TAG, "not support 1X SEND");
                notifySuppServiceFailed(Phone.SuppService.HANGUP);
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
        getForegroundCall();
        if (len > 1) {
            Rlog.d(LOG_TAG, "separate not supported");
            notifySuppServiceFailed(Phone.SuppService.SEPARATE);
            return true;
        }
        try {
            if (getRingingCall().getState() != Call.State.IDLE) {
                Rlog.d(LOG_TAG, "MmiCode 2: accept ringing call");
                this.mCT.acceptCall(2);
            } else {
                Rlog.d(LOG_TAG, "MmiCode 2: switchWaitingOrHoldingAndActive");
                this.mCT.switchWaitingOrHoldingAndActive();
            }
            return true;
        } catch (CallStateException e) {
            Rlog.d(LOG_TAG, "switch failed", e);
            notifySuppServiceFailed(Phone.SuppService.SWITCH);
            return true;
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
        Rlog.d(LOG_TAG, "MmiCode 4: not support explicit call transfer");
        notifySuppServiceFailed(Phone.SuppService.TRANSFER);
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

    public void notifySuppSvcNotification(SuppServiceNotification suppSvc) {
        Rlog.d(LOG_TAG, "notifySuppSvcNotification: suppSvc = " + suppSvc);
        this.mSsnRegistrants.notifyRegistrants(new AsyncResult((Object) null, suppSvc, (Throwable) null));
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public boolean handleInCallMmiCommands(String dialString) {
        if (isInCall() && !TextUtils.isEmpty(dialString)) {
            switch (dialString.charAt(0)) {
                case '0':
                    return handleCallDeflectionIncallSupplementaryService(dialString);
                case '1':
                    return handleCallWaitingIncallSupplementaryService(dialString);
                case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_MOB_TERM_HOME_I /* 50 */:
                    return handleCallHoldIncallSupplementaryService(dialString);
                case '3':
                    return handleMultipartyIncallSupplementaryService(dialString);
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

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase
    public boolean isInCall() {
        return getForegroundCall().getState().isAlive() || getBackgroundCall().getState().isAlive() || getRingingCall().getState().isAlive();
    }

    public void notifyNewRingingConnection(Connection c) {
        this.mDefaultPhone.notifyNewRingingConnectionP(c);
    }

    public void notifyUnknownConnection(Connection c) {
        this.mDefaultPhone.notifyUnknownConnectionP(c);
    }

    @Override // com.android.internal.telephony.PhoneBase
    public void notifyForVideoCapabilityChanged(boolean isVideoCapable) {
        this.mIsVideoCapable = isVideoCapable;
        this.mDefaultPhone.notifyForVideoCapabilityChanged(isVideoCapable);
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState, Bundle extras) throws CallStateException {
        return dialInternal(dialString, videoState, extras);
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState) throws CallStateException {
        return dialInternal(dialString, videoState, (Bundle) null);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState, Bundle extras, int prefix) throws CallStateException {
        return dialInternal(dialString, videoState, extras, prefix);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState, int prefix) throws CallStateException {
        return dial(dialString, videoState, (Bundle) null, prefix);
    }

    protected Connection dialInternal(String dialString, int videoState, Bundle extras) throws CallStateException {
        if (TelBrand.IS_DCM) {
            return dialInternal(dialString, videoState, extras, 0);
        }
        boolean isConferenceUri = false;
        boolean isSkipSchemaParsing = false;
        if (extras != null) {
            isConferenceUri = extras.getBoolean("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
            isSkipSchemaParsing = extras.getBoolean("org.codeaurora.extra.SKIP_SCHEMA_PARSING", false);
        }
        String newDialString = dialString;
        if (!isConferenceUri && !isSkipSchemaParsing) {
            newDialString = PhoneNumberUtils.stripSeparators(dialString);
        }
        if (handleInCallMmiCommands(newDialString)) {
            return null;
        }
        String networkPortion = newDialString;
        if (!ImsPhoneMmiCode.isScMatchesSuppServType(newDialString)) {
            networkPortion = PhoneNumberUtils.extractNetworkPortionAlt(newDialString);
        }
        ImsPhoneMmiCode mmi = ImsPhoneMmiCode.newFromDialString(networkPortion, this);
        Rlog.d(LOG_TAG, "dialing w/ mmi '" + mmi + "'...");
        if (mmi == null) {
            return this.mCT.dial(dialString, videoState, extras);
        }
        if (mmi.isTemporaryModeCLIR()) {
            return this.mCT.dial(mmi.getDialingNumber(), mmi.getCLIRMode(), videoState, extras);
        }
        if (!mmi.isSupportedOverImsPhone()) {
            throw new CallStateException(CS_FALLBACK);
        }
        this.mPendingMMIs.add(mmi);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
        mmi.processCode();
        return null;
    }

    protected Connection dialInternal(String dialString, int videoState, Bundle extras, int prefix) throws CallStateException {
        boolean isConferenceUri = false;
        boolean isSkipSchemaParsing = false;
        if (extras != null) {
            isConferenceUri = extras.getBoolean("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
            isSkipSchemaParsing = extras.getBoolean("org.codeaurora.extra.SKIP_SCHEMA_PARSING", false);
        }
        String newDialString = dialString;
        if (!isConferenceUri && !isSkipSchemaParsing) {
            newDialString = PhoneNumberUtils.stripSeparators(dialString);
        }
        if (handleInCallMmiCommands(newDialString)) {
            return null;
        }
        String networkPortion = newDialString;
        if (!ImsPhoneMmiCode.isScMatchesSuppServType(newDialString)) {
            networkPortion = PhoneNumberUtils.extractNetworkPortionAlt(newDialString);
        }
        ImsPhoneMmiCode mmi = ImsPhoneMmiCode.newFromDialString(networkPortion, this);
        Rlog.d(LOG_TAG, "dialing w/ mmi '" + mmi + "'...");
        if (mmi == null) {
            return this.mCT.dial(getDialNumberInfo(dialString, prefix).get(0), videoState, extras);
        } else if (mmi.isTemporaryModeCLIR()) {
            if (prefix >= 4) {
                prefix -= 4;
            }
            return this.mCT.dial(getDialNumberInfo(mmi.getDialingNumber(), prefix).get(0), mmi.getCLIRMode(), videoState, extras);
        } else if (!mmi.isSupportedOverImsPhone()) {
            throw new CallStateException(CS_FALLBACK);
        } else {
            this.mPendingMMIs.add(mmi);
            this.mMmiRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
            mmi.processCode();
            return null;
        }
    }

    private ArrayList<String> getDialNumberInfo(String dialString, int prefix) {
        String subDialString;
        int subDialStringLen;
        String dialAddress;
        ArrayList<String> dialInfoList = new ArrayList<>();
        boolean isSubAddress = false;
        Context context = getContext();
        if (context != null) {
            isSubAddress = context.getSharedPreferences("network_service_settings_private", 0).getBoolean("sub_address_setting", true);
        }
        Rlog.d(LOG_TAG, "dialString is " + dialString + " isSubAddress is " + isSubAddress + " prefix is " + prefix);
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
                        dialAddress = subDialString2 + "&" + subAddress;
                    } else {
                        dialAddress = subDialString2;
                    }
                    dialInfoList.set(0, dialAddress);
                }
                Rlog.d(LOG_TAG, "dialInfoList.get(0) is " + dialInfoList.get(0) + ")");
            }
        }
        return dialInfoList;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void addParticipant(String dialString) throws CallStateException {
        this.mCT.addParticipant(dialString);
    }

    @Override // com.android.internal.telephony.Phone
    public void sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG, "sendDtmf called with invalid character '" + c + "'");
        } else if (this.mCT.mState == PhoneConstants.State.OFFHOOK) {
            this.mCT.sendDtmf(c, null);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void startDtmf(char c) {
        if (PhoneNumberUtils.is12Key(c) || (c >= 'A' && c <= 'D')) {
            this.mCT.startDtmf(c);
        } else {
            Rlog.e(LOG_TAG, "startDtmf called with invalid character '" + c + "'");
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void stopDtmf() {
        this.mCT.stopDtmf();
    }

    @Override // com.android.internal.telephony.Phone
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        this.mPostDialHandler = new Registrant(h, what, obj);
    }

    public void notifyIncomingRing() {
        Rlog.d(LOG_TAG, "notifyIncomingRing");
        sendMessage(obtainMessage(14, new AsyncResult((Object) null, (Object) null, (Throwable) null)));
    }

    @Override // com.android.internal.telephony.Phone
    public void setMute(boolean muted) {
        this.mCT.setMute(muted);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setUiTTYMode(int uiTtyMode, Message onComplete) {
        this.mCT.setUiTTYMode(uiTtyMode, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getMute() {
        return this.mCT.getMute();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public PhoneConstants.State getState() {
        return this.mCT.mState;
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
            case 2:
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
            default:
                return 3;
            case 4:
                return 4;
            case 5:
                return 5;
        }
    }

    private int getActionFromCFAction(int action) {
        switch (action) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 2:
            default:
                return -1;
            case 3:
                return 3;
            case 4:
                return 4;
        }
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        Rlog.d(LOG_TAG, "getCLIR");
        try {
            this.mCT.getUtInterface().queryCLIR(obtainMessage(45, onComplete));
        } catch (ImsException e) {
            sendErrorResponse(onComplete, (Throwable) e);
        }
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public void setOutgoingCallerIdDisplay(int clirMode, Message onComplete) {
        Rlog.d(LOG_TAG, "setCLIR action= " + clirMode);
        try {
            this.mCT.getUtInterface().updateCLIR(clirMode, obtainMessage(44, onComplete));
        } catch (ImsException e) {
            sendErrorResponse(onComplete, (Throwable) e);
        }
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        Rlog.d(LOG_TAG, "getCallForwardingOption reason=" + commandInterfaceCFReason);
        if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            Rlog.d(LOG_TAG, "requesting call forwarding query.");
            try {
                this.mCT.getUtInterface().queryCallForward(getConditionFromCFReason(commandInterfaceCFReason), (String) null, obtainMessage(13, onComplete));
            } catch (ImsException e) {
                sendErrorResponse(onComplete, (Throwable) e);
            }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete);
        }
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int timerSeconds, Message onComplete) {
        setCallForwardingOption(commandInterfaceCFAction, commandInterfaceCFReason, dialingNumber, 1, timerSeconds, onComplete);
    }

    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int serviceClass, int timerSeconds, Message onComplete) {
        Rlog.d(LOG_TAG, "setCallForwardingOption action=" + commandInterfaceCFAction + ", reason=" + commandInterfaceCFReason + " serviceClass=" + serviceClass);
        if (isValidCommandInterfaceCFAction(commandInterfaceCFAction) && isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            obtainMessage(12, isCfEnable(commandInterfaceCFAction) ? 1 : 0, 0, new Cf(dialingNumber, commandInterfaceCFReason == 0, onComplete));
            try {
                this.mCT.getUtInterface().updateCallForward(getActionFromCFAction(commandInterfaceCFAction), getConditionFromCFReason(commandInterfaceCFReason), dialingNumber, serviceClass, timerSeconds, onComplete);
            } catch (ImsException e) {
                sendErrorResponse(onComplete, (Throwable) e);
            }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete);
        }
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallForwardingUncondTimerOption(int startHour, int startMinute, int endHour, int endMinute, int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, Message onComplete) {
        Rlog.d(LOG_TAG, "setCallForwardingUncondTimerOption action=" + commandInterfaceCFAction + ", reason=" + commandInterfaceCFReason + ", startHour=" + startHour + ", startMinute=" + startMinute + ", endHour=" + endHour + ", endMinute=" + endMinute);
        if (isValidCommandInterfaceCFAction(commandInterfaceCFAction) && isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            try {
                this.mCT.getUtInterface().updateCallForwardUncondTimer(startHour, startMinute, endHour, endMinute, getActionFromCFAction(commandInterfaceCFAction), getConditionFromCFReason(commandInterfaceCFReason), dialingNumber, obtainMessage(37, isCfEnable(commandInterfaceCFAction) ? 1 : 0, 0, new Cf(dialingNumber, commandInterfaceCFReason == 0, onComplete)));
            } catch (ImsException e) {
                sendErrorResponse(onComplete, (Throwable) e);
            }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete);
        }
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallWaiting(Message onComplete) {
        Rlog.d(LOG_TAG, "getCallWaiting");
        try {
            this.mCT.getUtInterface().queryCallWaiting(obtainMessage(43, onComplete));
        } catch (ImsException e) {
            sendErrorResponse(onComplete, (Throwable) e);
        }
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallWaiting(boolean enable, Message onComplete) {
        setCallWaiting(enable, 1, onComplete);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallWaiting(boolean enable, int serviceClass, Message onComplete) {
        Rlog.d(LOG_TAG, "setCallWaiting enable=" + enable);
        try {
            this.mCT.getUtInterface().updateCallWaiting(enable, serviceClass, obtainMessage(42, onComplete));
        } catch (ImsException e) {
            sendErrorResponse(onComplete, (Throwable) e);
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

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallBarring(String facility, Message onComplete) {
        Rlog.d(LOG_TAG, "getCallBarring facility=" + facility);
        try {
            this.mCT.getUtInterface().queryCallBarring(getCBTypeFromFacility(facility), obtainMessage(41, onComplete));
        } catch (ImsException e) {
            sendErrorResponse(onComplete, (Throwable) e);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallBarring(String facility, boolean lockState, String password, Message onComplete) {
        int action;
        Rlog.d(LOG_TAG, "setCallBarring facility=" + facility + ", lockState=" + lockState);
        Message resp = obtainMessage(40, onComplete);
        if (lockState) {
            action = 1;
        } else {
            action = 0;
        }
        try {
            this.mCT.getUtInterface().updateCallBarring(getCBTypeFromFacility(facility), action, resp, (String[]) null);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, (Throwable) e);
        }
    }

    private boolean isValidFacilityString(String facility) {
        if (facility.equals(CommandsInterface.CB_FACILITY_BAOC) || facility.equals(CommandsInterface.CB_FACILITY_BAOIC) || facility.equals(CommandsInterface.CB_FACILITY_BAOICxH) || facility.equals(CommandsInterface.CB_FACILITY_BAIC) || facility.equals(CommandsInterface.CB_FACILITY_BAICr) || facility.equals(CommandsInterface.CB_FACILITY_BA_ALL) || facility.equals(CommandsInterface.CB_FACILITY_BA_MO) || facility.equals(CommandsInterface.CB_FACILITY_BA_MT)) {
            return true;
        }
        Rlog.e(LOG_TAG, "isValidFacilityString: invalid facilityString:" + facility);
        return false;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallBarring(String facility, String password, Message onComplete) {
        if (isValidFacilityString(facility)) {
            Rlog.d(LOG_TAG, "getCallBarring: facility=" + facility);
            try {
                if (this.mImsConfig != null) {
                    this.mImsConfig.queryFacilityLockSH(facility, password, 0, onComplete);
                } else {
                    Rlog.e(LOG_TAG, "queryFacilityLockSH failed. mImsConfig is null");
                }
            } catch (ImsException e) {
                Rlog.e(LOG_TAG, "queryFacilityLockSH failed. Exception = " + e);
            }
        }
    }

    public void setCallBarringNew(String facility, boolean lockState, String password, Message onComplete) {
        if (isValidFacilityString(facility)) {
            Rlog.d(LOG_TAG, "setCallBarring: facility=" + facility + " lockState=" + lockState);
            try {
                if (this.mImsConfig != null) {
                    this.mImsConfig.setFacilityLockSH(facility, lockState, password, 1, onComplete);
                } else {
                    Rlog.e(LOG_TAG, "setFacilityLockSH failed. mImsConfig is null");
                }
            } catch (ImsException e) {
                Rlog.e(LOG_TAG, "setFacilityLockSH failed. Exception = " + e);
            }
        }
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public void sendUssdResponse(String ussdMessge) {
        Rlog.d(LOG_TAG, "sendUssdResponse");
        ImsPhoneMmiCode mmi = ImsPhoneMmiCode.newFromUssdUserInput(ussdMessge, this);
        this.mPendingMMIs.add(mmi);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
        mmi.sendUssd(ussdMessge);
    }

    public void sendUSSD(String ussdString, Message response) {
        this.mCT.sendUSSD(ussdString, response);
    }

    public void cancelUSSD() {
        this.mCT.cancelUSSD();
    }

    void sendErrorResponse(Message onComplete) {
        Rlog.d(LOG_TAG, "sendErrorResponse");
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, (Object) null, new CommandException(CommandException.Error.GENERIC_FAILURE));
            onComplete.sendToTarget();
        }
    }

    public void sendErrorResponse(Message onComplete, Throwable e) {
        Rlog.d(LOG_TAG, "sendErrorResponse");
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, (Object) null, getCommandException(e));
            onComplete.sendToTarget();
        }
    }

    void sendErrorResponse(Message onComplete, ImsReasonInfo reasonInfo) {
        Rlog.d(LOG_TAG, "sendErrorResponse reasonCode=" + reasonInfo.getCode());
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, (Object) null, getCommandException(reasonInfo.getCode()));
            onComplete.sendToTarget();
        }
    }

    CommandException getCommandException(int code) {
        return getCommandException(code, null);
    }

    CommandException getCommandException(int code, String errorString) {
        Rlog.d(LOG_TAG, "getCommandException code= " + code + ", errorString= " + errorString);
        CommandException.Error error = CommandException.Error.GENERIC_FAILURE;
        switch (code) {
            case 801:
                error = CommandException.Error.REQUEST_NOT_SUPPORTED;
                break;
            case 821:
                error = CommandException.Error.PASSWORD_INCORRECT;
                break;
        }
        return new CommandException(error, errorString);
    }

    CommandException getCommandException(Throwable e) {
        if (e instanceof ImsException) {
            return getCommandException(((ImsException) e).getCode(), e.getMessage());
        }
        Rlog.d(LOG_TAG, "getCommandException generic failure");
        return new CommandException(CommandException.Error.GENERIC_FAILURE);
    }

    private void onNetworkInitiatedUssd(ImsPhoneMmiCode mmi) {
        Rlog.d(LOG_TAG, "onNetworkInitiatedUssd");
        this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
    }

    public void onIncomingUSSD(int ussdMode, String ussdMessage) {
        boolean isUssdError = true;
        Rlog.d(LOG_TAG, "onIncomingUSSD ussdMode=" + ussdMode);
        boolean isUssdRequest = ussdMode == 1;
        if (ussdMode == 0 || ussdMode == 1) {
            isUssdError = false;
        }
        ImsPhoneMmiCode found = null;
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
            this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
        }
    }

    public ArrayList<Connection> getHandoverConnection() {
        ArrayList<Connection> connList = new ArrayList<>();
        connList.addAll(getForegroundCall().mConnections);
        connList.addAll(getBackgroundCall().mConnections);
        connList.addAll(getRingingCall().mConnections);
        if (connList.size() > 0) {
            return connList;
        }
        return null;
    }

    public void notifySrvccState(Call.SrvccState state) {
        this.mCT.notifySrvccState(state);
    }

    public void initiateSilentRedial() {
        AsyncResult ar = new AsyncResult((Object) null, this.mLastDialString, (Throwable) null);
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

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        this.mSsnRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public void unregisterForSuppServiceNotification(Handler h) {
        this.mSsnRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public int getSubId() {
        return this.mDefaultPhone.getSubId();
    }

    @Override // com.android.internal.telephony.imsphone.ImsPhoneBase, com.android.internal.telephony.Phone
    public String getSubscriberId() {
        IccRecords r = getIccRecords();
        if (r != null) {
            return r.getIMSI();
        }
        return null;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public int getPhoneId() {
        return this.mDefaultPhone.getPhoneId();
    }

    @Override // com.android.internal.telephony.PhoneBase
    public IccRecords getIccRecords() {
        return this.mDefaultPhone.mIccRecords.get();
    }

    private CallForwardInfo getCallForwardInfo(ImsCallForwardInfo info) {
        CallForwardInfo cfInfo = new CallForwardInfo();
        cfInfo.status = info.mStatus;
        cfInfo.reason = getCFReasonFromCondition(info.mCondition);
        cfInfo.serviceClass = 1;
        cfInfo.toa = info.mToA;
        cfInfo.number = info.mNumber;
        cfInfo.timeSeconds = info.mTimeSeconds;
        cfInfo.startHour = info.mStartHour;
        cfInfo.startMinute = info.mStartMinute;
        cfInfo.endHour = info.mEndHour;
        cfInfo.endMinute = info.mEndMinute;
        return cfInfo;
    }

    private CallForwardInfo[] handleCfQueryResult(ImsCallForwardInfo[] infos) {
        CallForwardInfo[] cfInfos = null;
        if (!(infos == null || infos.length == 0)) {
            cfInfos = new CallForwardInfo[infos.length];
        }
        IccRecords r = getIccRecords();
        if (infos != null && infos.length != 0) {
            int s = infos.length;
            for (int i = 0; i < s; i++) {
                if (infos[i].mCondition == 0 && r != null) {
                    setCallForwardingPreference(infos[i].mStatus == 1);
                    r.setVoiceCallForwardingFlag(1, infos[i].mStatus == 1, infos[i].mNumber);
                }
                cfInfos[i] = getCallForwardInfo(infos[i]);
            }
        } else if (r != null) {
            r.setVoiceCallForwardingFlag(1, false, null);
        }
        return cfInfos;
    }

    private int[] handleCbQueryResult(ImsSsInfo[] infos) {
        int[] cbInfos = new int[1];
        cbInfos[0] = 0;
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
            if (e != null) {
                AsyncResult.forMessage(onComplete, result, getCommandException(e));
            } else {
                AsyncResult.forMessage(onComplete, result, (Throwable) null);
            }
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

    @Override // com.android.internal.telephony.PhoneBase, android.os.Handler
    public void handleMessage(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        Rlog.d(LOG_TAG, "handleMessage what=" + msg.what);
        switch (msg.what) {
            case 12:
                IccRecords r = getIccRecords();
                Cf cf = (Cf) ar.userObj;
                if (cf.mIsCfu && ar.exception == null && r != null) {
                    setCallForwardingPreference(msg.arg1 == 1);
                    r.setVoiceCallForwardingFlag(1, msg.arg1 == 1, cf.mSetCfNumber);
                }
                sendResponse(cf.mOnComplete, null, ar.exception);
                updateCallForwardStatus();
                return;
            case 13:
                CallForwardInfo[] cfInfos = null;
                if (ar.exception == null) {
                    cfInfos = handleCfQueryResult((ImsCallForwardInfo[]) ar.result);
                }
                sendResponse((Message) ar.userObj, cfInfos, ar.exception);
                updateCallForwardStatus();
                return;
            case 37:
                sendResponse(((Cf) ar.userObj).mOnComplete, null, ar.exception);
                return;
            case 39:
                Rlog.d(LOG_TAG, "Callforwarding is " + getCallForwardingPreference());
                notifyCallForwardingIndicator();
                return;
            case 40:
            case 42:
            case 44:
                sendResponse((Message) ar.userObj, null, ar.exception);
                return;
            case 41:
            case 43:
                int[] ssInfos = null;
                if (ar.exception == null) {
                    if (msg.what == 41) {
                        ssInfos = handleCbQueryResult((ImsSsInfo[]) ar.result);
                    } else if (msg.what == 43) {
                        ssInfos = handleCwQueryResult((ImsSsInfo[]) ar.result);
                    }
                }
                sendResponse((Message) ar.userObj, ssInfos, ar.exception);
                return;
            case 45:
                Bundle ssInfo = (Bundle) ar.result;
                int[] clirInfo = null;
                if (ssInfo != null) {
                    clirInfo = ssInfo.getIntArray(ImsPhoneMmiCode.UT_BUNDLE_KEY_CLIR);
                }
                sendResponse((Message) ar.userObj, clirInfo, ar.exception);
                return;
            case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_DS_MIP_SS_USER_PROF_I /* 46 */:
                Rlog.d(LOG_TAG, "EVENT_DEFAULT_PHONE_DATA_STATE_CHANGED");
                updateDataServiceState();
                return;
            default:
                super.handleMessage(msg);
                return;
        }
    }

    @Override // com.android.internal.telephony.PhoneBase
    public boolean isInEmergencyCall() {
        return this.mCT.isInEmergencyCall();
    }

    @Override // com.android.internal.telephony.PhoneBase
    public boolean isInEcm() {
        return this.mIsPhoneInEcmState;
    }

    void sendEmergencyCallbackModeChange() {
        Intent intent = new Intent("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
        intent.putExtra("phoneinECMState", this.mIsPhoneInEcmState);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, getPhoneId());
        ActivityManagerNative.broadcastStickyIntent(intent, (String) null, -1);
        Rlog.d(LOG_TAG, "sendEmergencyCallbackModeChange");
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
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

    public void handleEnterEmergencyCallbackMode() {
        Rlog.d(LOG_TAG, "handleEnterEmergencyCallbackMode,mIsPhoneInEcmState= " + this.mIsPhoneInEcmState);
        if (!this.mIsPhoneInEcmState) {
            this.mIsPhoneInEcmState = true;
            sendEmergencyCallbackModeChange();
            setSystemProperty("ril.cdma.inecmmode", "true");
            postDelayed(this.mExitEcmRunnable, SystemProperties.getLong("ro.cdma.ecmexittimer", 300000L));
            this.mWakeLock.acquire();
        }
    }

    public void handleExitEmergencyCallbackMode() {
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

    public void handleTimerInEmergencyCallbackMode(int action) {
        switch (action) {
            case 0:
                postDelayed(this.mExitEcmRunnable, SystemProperties.getLong("ro.cdma.ecmexittimer", 300000L));
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
                Rlog.e(LOG_TAG, "handleTimerInEmergencyCallbackMode, unsupported action " + action);
                return;
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setOnEcbModeExitResponse(Handler h, int what, Object obj) {
        this.mEcmExitRespRegistrant = new Registrant(h, what, obj);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void unsetOnEcbModeExitResponse(Handler h) {
        this.mEcmExitRespRegistrant.clear();
    }

    public boolean isVolteEnabled() {
        return this.mCT.isVolteEnabled();
    }

    public boolean isVtEnabled() {
        return this.mCT.isVtEnabled();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean isUtEnabled() {
        return this.mCT.isUtEnabled();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean isImsRegistered() {
        return this.mImsRegistered;
    }

    public void setImsRegistered(boolean value) {
        this.mImsRegistered = value;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void declineCall() throws CallStateException {
        this.mCT.declineCall();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getIncomingAnonymousCallBarring(Message onComplete) {
        Rlog.d(LOG_TAG, "getIncomingAnonymousCallBarring: facility=157");
        obtainMessage(46, onComplete);
        try {
            this.mCT.getUtInterface().queryFacilityLockBAICa(ImsPhoneMmiCode.SC_BAICa, (String) null, 0, onComplete);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, (Throwable) e);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setIncomingAnonymousCallBarring(boolean lockState, Message onComplete) {
        Rlog.d(LOG_TAG, "setIncomingAnonymousCallBarring: facility=157 lockState=" + lockState);
        obtainMessage(47, onComplete);
        try {
            this.mCT.getUtInterface().setFacilityLock(ImsPhoneMmiCode.SC_BAICa, lockState, (String) null, 1, onComplete);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, (Throwable) e);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getIncomingSpecificDnCallBarring(Message onComplete) {
        Rlog.d(LOG_TAG, "getIncomingSpecificDnCallBarring: facility=156");
        obtainMessage(48, onComplete);
        try {
            this.mCT.getUtInterface().queryIncomingCallBarringSDn(ImsPhoneMmiCode.SC_BS_MT, 1, onComplete);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, (Throwable) e);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setIncomingSpecificDnCallBarring(int operation, String[] icbNum, Message onComplete) {
        Rlog.d(LOG_TAG, "setIncomingSpecificDnCallBarring: facility=156 operation=" + operation);
        obtainMessage(49, onComplete);
        try {
            this.mCT.getUtInterface().setIncomingCallBarring(operation, ImsPhoneMmiCode.SC_BS_MT, icbNum, 1, onComplete);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, (Throwable) e);
        }
    }
}
