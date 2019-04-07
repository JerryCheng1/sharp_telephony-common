package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Settings.SettingNotFoundException;
import android.telecom.VideoProfile.VideoState;
import android.telephony.CellIdentityCdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.DataConnectionRealTimeInfo;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.VoLteServiceState;
import android.text.TextUtils;
import com.android.internal.telephony.Call.SrvccState;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneConnection;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UsimServiceTable;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public abstract class PhoneBase extends Handler implements Phone {
    private static final String CDMA_NON_ROAMING_LIST_OVERRIDE_PREFIX = "cdma_non_roaming_list_";
    private static final String CDMA_ROAMING_LIST_OVERRIDE_PREFIX = "cdma_roaming_list_";
    public static final String CF_ENABLED = "cf_enabled_key";
    public static final String CLIR_KEY = "clir_key";
    public static final String DATA_DISABLED_ON_BOOT_KEY = "disabled_on_boot_key";
    public static final String DNS_SERVER_CHECK_DISABLED_KEY = "dns_server_check_disabled_key";
    protected static final int EVENT_CALL_RING = 14;
    protected static final int EVENT_CALL_RING_CONTINUE = 15;
    protected static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 27;
    protected static final int EVENT_EMERGENCY_CALLBACK_MODE_ENTER = 25;
    protected static final int EVENT_EXIT_EMERGENCY_CALLBACK_RESPONSE = 26;
    protected static final int EVENT_GET_BASEBAND_VERSION_DONE = 6;
    protected static final int EVENT_GET_CALLFORWARDING_STATUS = 39;
    protected static final int EVENT_GET_CALL_FORWARD_DONE = 13;
    protected static final int EVENT_GET_CALL_FORWARD_TIMER_DONE = 38;
    protected static final int EVENT_GET_DEVICE_IDENTITY_DONE = 21;
    protected static final int EVENT_GET_IMEISV_DONE = 10;
    protected static final int EVENT_GET_IMEI_DONE = 9;
    protected static final int EVENT_GET_RADIO_CAPABILITY = 35;
    protected static final int EVENT_GET_SIM_STATUS_DONE = 11;
    protected static final int EVENT_ICC_CHANGED = 30;
    protected static final int EVENT_ICC_RECORD_EVENTS = 29;
    protected static final int EVENT_INITIATE_SILENT_REDIAL = 32;
    protected static final int EVENT_LAST = 39;
    protected static final int EVENT_MMI_DONE = 4;
    protected static final int EVENT_NV_READY = 23;
    protected static final int EVENT_RADIO_AVAILABLE = 1;
    protected static final int EVENT_RADIO_NOT_AVAILABLE = 33;
    protected static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE = 8;
    protected static final int EVENT_RADIO_ON = 5;
    protected static final int EVENT_REGISTERED_TO_NETWORK = 19;
    protected static final int EVENT_RUIM_RECORDS_LOADED = 22;
    protected static final int EVENT_SET_CALL_FORWARD_DONE = 12;
    protected static final int EVENT_SET_CALL_FORWARD_TIMER_DONE = 37;
    protected static final int EVENT_SET_CLIR_COMPLETE = 18;
    protected static final int EVENT_SET_ENHANCED_VP = 24;
    protected static final int EVENT_SET_NETWORK_AUTOMATIC = 28;
    protected static final int EVENT_SET_NETWORK_AUTOMATIC_COMPLETE = 17;
    protected static final int EVENT_SET_NETWORK_MANUAL_COMPLETE = 16;
    protected static final int EVENT_SET_RAT_MODE_OPTIMIZE_SETTING_COMPLETE = 10000;
    protected static final int EVENT_SET_VM_NUMBER_DONE = 20;
    protected static final int EVENT_SIM_RECORDS_LOADED = 3;
    protected static final int EVENT_SRVCC_STATE_CHANGED = 31;
    protected static final int EVENT_SS = 36;
    protected static final int EVENT_SSN = 2;
    protected static final int EVENT_UNSOL_OEM_HOOK_RAW = 34;
    protected static final int EVENT_USSD = 7;
    private static final String GSM_NON_ROAMING_LIST_OVERRIDE_PREFIX = "gsm_non_roaming_list_";
    private static final String GSM_ROAMING_LIST_OVERRIDE_PREFIX = "gsm_roaming_list_";
    private static final String LOG_TAG = "PhoneBase";
    public static final String NETWORK_SELECTION_KEY = "network_selection_key";
    public static final String NETWORK_SELECTION_NAME_KEY = "network_selection_name_key";
    public static final String PROPERTY_MULTIMODE_CDMA = "ro.config.multimode_cdma";
    public static final String PROPERTY_OOS_IS_DISCONNECT = "persist.telephony.oosisdc";
    public static final String SIM_IMSI = "sim_imsi_key";
    public static final String VM_COUNT = "vm_count_key";
    public static final String VM_ID = "vm_id_key";
    public static final String VM_SIM_IMSI = "vm_sim_imsi_key";
    private final String mActionAttached;
    private final String mActionDetached;
    int mCallRingContinueToken;
    int mCallRingDelay;
    public CommandsInterface mCi;
    protected final Context mContext;
    public DcTrackerBase mDcTracker;
    protected final RegistrantList mDisconnectRegistrants;
    boolean mDnsCheckDisabled;
    boolean mDoesRilSendMultipleCallRing;
    protected final RegistrantList mHandoverRegistrants;
    public AtomicReference<IccRecords> mIccRecords;
    private BroadcastReceiver mImsIntentReceiver;
    private final Object mImsLock;
    protected ImsPhone mImsPhone;
    private boolean mImsServiceReady;
    protected final RegistrantList mIncomingRingRegistrants;
    public boolean mIsTheCurrentActivePhone;
    private boolean mIsVideoCapable;
    boolean mIsVoiceCapable;
    protected Looper mLooper;
    protected final RegistrantList mMmiCompleteRegistrants;
    protected final RegistrantList mMmiRegistrants;
    private final String mName;
    protected final RegistrantList mNewRingingConnectionRegistrants;
    protected PhoneNotifier mNotifier;
    protected boolean mOosIsDisconnect;
    protected int mPhoneId;
    protected final RegistrantList mPreciseCallStateRegistrants;
    protected final RegistrantList mRadioOffOrNotAvailableRegistrants;
    protected final RegistrantList mServiceStateRegistrants;
    protected final RegistrantList mSimRecordsLoadedRegistrants;
    protected SimulatedRadioControl mSimulatedRadioControl;
    public SmsStorageMonitor mSmsStorageMonitor;
    public SmsUsageMonitor mSmsUsageMonitor;
    protected final RegistrantList mSuppServiceFailedRegistrants;
    private TelephonyTester mTelephonyTester;
    protected AtomicReference<UiccCardApplication> mUiccApplication;
    protected UiccController mUiccController;
    boolean mUnitTestMode;
    protected final RegistrantList mUnknownConnectionRegistrants;
    protected final RegistrantList mVideoCapabilityChangedRegistrants;
    private int mVmCount;

    protected static class NetworkSelectMessage {
        public Message message;
        public String operatorAlphaLong;
        public String operatorNumeric;

        protected NetworkSelectMessage() {
        }
    }

    protected PhoneBase(String str, PhoneNotifier phoneNotifier, Context context, CommandsInterface commandsInterface) {
        this(str, phoneNotifier, context, commandsInterface, false);
    }

    protected PhoneBase(String str, PhoneNotifier phoneNotifier, Context context, CommandsInterface commandsInterface, boolean z) {
        this(str, phoneNotifier, context, commandsInterface, z, Integer.MAX_VALUE);
    }

    protected PhoneBase(String str, PhoneNotifier phoneNotifier, Context context, CommandsInterface commandsInterface, boolean z, int i) {
        this.mImsIntentReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                Rlog.d(PhoneBase.LOG_TAG, "mImsIntentReceiver: action " + intent.getAction());
                if (intent.hasExtra("android:phone_id")) {
                    int intExtra = intent.getIntExtra("android:phone_id", -1);
                    Rlog.d(PhoneBase.LOG_TAG, "mImsIntentReceiver: extraPhoneId = " + intExtra);
                    if (intExtra == -1 || intExtra != PhoneBase.this.getPhoneId()) {
                        return;
                    }
                }
                if (intent.getAction().equals("com.android.ims.IMS_SERVICE_UP")) {
                    PhoneBase.this.mImsServiceReady = true;
                    PhoneBase.this.updateImsPhone();
                } else if (intent.getAction().equals("com.android.ims.IMS_SERVICE_DOWN")) {
                    PhoneBase.this.mImsServiceReady = false;
                    PhoneBase.this.updateImsPhone();
                }
            }
        };
        this.mVmCount = 0;
        this.mIsTheCurrentActivePhone = true;
        this.mIsVoiceCapable = true;
        this.mIsVideoCapable = false;
        this.mUiccController = null;
        this.mIccRecords = new AtomicReference();
        this.mUiccApplication = new AtomicReference();
        this.mImsLock = new Object();
        this.mImsServiceReady = false;
        this.mImsPhone = null;
        this.mOosIsDisconnect = SystemProperties.getBoolean(PROPERTY_OOS_IS_DISCONNECT, true);
        this.mPreciseCallStateRegistrants = new RegistrantList();
        this.mHandoverRegistrants = new RegistrantList();
        this.mNewRingingConnectionRegistrants = new RegistrantList();
        this.mIncomingRingRegistrants = new RegistrantList();
        this.mDisconnectRegistrants = new RegistrantList();
        this.mServiceStateRegistrants = new RegistrantList();
        this.mMmiCompleteRegistrants = new RegistrantList();
        this.mMmiRegistrants = new RegistrantList();
        this.mUnknownConnectionRegistrants = new RegistrantList();
        this.mSuppServiceFailedRegistrants = new RegistrantList();
        this.mRadioOffOrNotAvailableRegistrants = new RegistrantList();
        this.mSimRecordsLoadedRegistrants = new RegistrantList();
        this.mVideoCapabilityChangedRegistrants = new RegistrantList();
        this.mPhoneId = i;
        this.mName = str;
        this.mNotifier = phoneNotifier;
        this.mContext = context;
        this.mLooper = Looper.myLooper();
        this.mCi = commandsInterface;
        this.mActionDetached = getClass().getPackage().getName() + ".action_detached";
        this.mActionAttached = getClass().getPackage().getName() + ".action_attached";
        if (Build.IS_DEBUGGABLE) {
            this.mTelephonyTester = new TelephonyTester(this);
        }
        setUnitTestMode(z);
        this.mDnsCheckDisabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DNS_SERVER_CHECK_DISABLED_KEY, false);
        this.mCi.setOnCallRing(this, 14, null);
        this.mIsVoiceCapable = this.mContext.getResources().getBoolean(17956947);
        this.mDoesRilSendMultipleCallRing = SystemProperties.getBoolean("ro.telephony.call_ring.multiple", false);
        Rlog.d(LOG_TAG, "mDoesRilSendMultipleCallRing=" + this.mDoesRilSendMultipleCallRing);
        this.mCallRingDelay = SystemProperties.getInt("ro.telephony.call_ring.delay", 3000);
        Rlog.d(LOG_TAG, "mCallRingDelay=" + this.mCallRingDelay);
        if (getPhoneType() != 5) {
            setPropertiesByCarrier();
            this.mSmsStorageMonitor = new SmsStorageMonitor(this);
            this.mSmsUsageMonitor = new SmsUsageMonitor(context);
            this.mUiccController = UiccController.getInstance();
            this.mUiccController.registerForIccChanged(this, 30, null);
            if (getPhoneType() != 3) {
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction("com.android.ims.IMS_SERVICE_UP");
                intentFilter.addAction("com.android.ims.IMS_SERVICE_DOWN");
                this.mContext.registerReceiver(this.mImsIntentReceiver, intentFilter);
                this.mCi.registerForSrvccStateChanged(this, 31, null);
            }
            this.mCi.setOnUnsolOemHookRaw(this, 34, null);
            Rlog.d(LOG_TAG, "mOosIsDisconnect=" + this.mOosIsDisconnect);
        }
    }

    private void checkCorrectThread(Handler handler) {
        if (handler.getLooper() != this.mLooper) {
            throw new RuntimeException("com.android.internal.telephony.Phone must be used from within one thread");
        }
    }

    private boolean getRoamingOverrideHelper(String str, String str2) {
        String iccSerialNumber = getIccSerialNumber();
        if (!(TextUtils.isEmpty(iccSerialNumber) || TextUtils.isEmpty(str2))) {
            Set stringSet = PreferenceManager.getDefaultSharedPreferences(this.mContext).getStringSet(str + iccSerialNumber, null);
            if (stringSet != null) {
                return stringSet.contains(str2);
            }
        }
        return false;
    }

    private String getSavedNetworkSelection() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(NETWORK_SELECTION_KEY + getSubId(), "");
    }

    public static int getVideoState(Call call) {
        ImsPhoneConnection imsPhoneConnection = (ImsPhoneConnection) call.getEarliestConnection();
        return imsPhoneConnection != null ? imsPhoneConnection.getVideoState() : 0;
    }

    private void handleSetSelectNetwork(AsyncResult asyncResult) {
        if (asyncResult.userObj instanceof NetworkSelectMessage) {
            NetworkSelectMessage networkSelectMessage = (NetworkSelectMessage) asyncResult.userObj;
            if (networkSelectMessage.message != null) {
                AsyncResult.forMessage(networkSelectMessage.message, asyncResult.result, asyncResult.exception);
                networkSelectMessage.message.sendToTarget();
                return;
            }
            return;
        }
        Rlog.e(LOG_TAG, "unexpected result from user object.");
    }

    private void handleSrvccStateChanged(int[] iArr) {
        Rlog.d(LOG_TAG, "handleSrvccStateChanged");
        ArrayList arrayList = null;
        ImsPhone imsPhone = this.mImsPhone;
        SrvccState srvccState = SrvccState.NONE;
        if (iArr != null && iArr.length != 0) {
            int i = iArr[0];
            switch (i) {
                case 0:
                    srvccState = SrvccState.STARTED;
                    if (imsPhone == null) {
                        Rlog.d(LOG_TAG, "HANDOVER_STARTED: mImsPhone null");
                        break;
                    }
                    arrayList = imsPhone.getHandoverConnection();
                    migrateFrom(imsPhone);
                    break;
                case 1:
                    srvccState = SrvccState.COMPLETED;
                    if (imsPhone == null) {
                        Rlog.d(LOG_TAG, "HANDOVER_COMPLETED: mImsPhone null");
                        break;
                    } else {
                        imsPhone.notifySrvccState(srvccState);
                        break;
                    }
                case 2:
                case 3:
                    srvccState = SrvccState.FAILED;
                    break;
                default:
                    return;
            }
            getCallTracker().notifySrvccState(srvccState, arrayList);
            notifyVoLteServiceStateChanged(new VoLteServiceState(i));
        }
    }

    private boolean isImsVideoCall(Call call) {
        return VideoState.isVideo(getVideoState(call));
    }

    private static void logUnexpectedCdmaMethodCall(String str) {
        Rlog.e(LOG_TAG, "Error! " + str + "() in PhoneBase should not be " + "called, CDMAPhone inactive.");
    }

    private static void logUnexpectedGsmMethodCall(String str) {
        Rlog.e(LOG_TAG, "Error! " + str + "() in PhoneBase should not be " + "called, GSMPhone inactive.");
    }

    private void notifyIncomingRing() {
        if (this.mIsVoiceCapable) {
            this.mIncomingRingRegistrants.notifyRegistrants(new AsyncResult(null, this, null));
        }
    }

    private List<CellInfo> privatizeCellInfoList(List<CellInfo> list) {
        if (Secure.getInt(getContext().getContentResolver(), "location_mode", 0) != 0) {
            return list;
        }
        ArrayList arrayList = new ArrayList(list.size());
        for (CellInfo cellInfo : list) {
            if (cellInfo instanceof CellInfoCdma) {
                CellInfoCdma cellInfoCdma = (CellInfoCdma) cellInfo;
                CellIdentityCdma cellIdentity = cellInfoCdma.getCellIdentity();
                CellIdentityCdma cellIdentityCdma = new CellIdentityCdma(cellIdentity.getNetworkId(), cellIdentity.getSystemId(), cellIdentity.getBasestationId(), Integer.MAX_VALUE, Integer.MAX_VALUE);
                CellInfoCdma cellInfoCdma2 = new CellInfoCdma(cellInfoCdma);
                cellInfoCdma2.setCellIdentity(cellIdentityCdma);
                arrayList.add(cellInfoCdma2);
            } else {
                arrayList.add(cellInfo);
            }
        }
        return arrayList;
    }

    private void sendIncomingCallRingNotification(int i) {
        if (this.mIsVoiceCapable && !this.mDoesRilSendMultipleCallRing && i == this.mCallRingContinueToken) {
            Rlog.d(LOG_TAG, "Sending notifyIncomingRing");
            notifyIncomingRing();
            sendMessageDelayed(obtainMessage(15, i, 0), (long) this.mCallRingDelay);
            return;
        }
        Rlog.d(LOG_TAG, "Ignoring ring notification request, mDoesRilSendMultipleCallRing=" + this.mDoesRilSendMultipleCallRing + " token=" + i + " mCallRingContinueToken=" + this.mCallRingContinueToken + " mIsVoiceCapable=" + this.mIsVoiceCapable);
    }

    private void setPropertiesByCarrier() {
        String str = SystemProperties.get("ro.carrier");
        if (str != null && str.length() != 0 && !"unknown".equals(str)) {
            CharSequence[] textArray = this.mContext.getResources().getTextArray(17236042);
            for (int i = 0; i < textArray.length; i += 3) {
                if (str.equals(textArray[i].toString())) {
                    Locale forLanguageTag = Locale.forLanguageTag(textArray[i + 1].toString().replace('_', '-'));
                    str = forLanguageTag.getCountry();
                    MccTable.setSystemLocale(this.mContext, forLanguageTag.getLanguage(), str);
                    if (!str.isEmpty()) {
                        try {
                            Global.getInt(this.mContext.getContentResolver(), "wifi_country_code");
                            return;
                        } catch (SettingNotFoundException e) {
                            ((WifiManager) this.mContext.getSystemService("wifi")).setCountryCode(str, false);
                            return;
                        }
                    }
                    return;
                }
            }
        }
    }

    private void setRoamingOverrideHelper(List<String> list, String str, String str2) {
        Editor edit = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
        String str3 = str + str2;
        if (list == null || list.isEmpty()) {
            edit.remove(str3).commit();
        } else {
            edit.putStringSet(str3, new HashSet(list)).commit();
        }
    }

    private void updateSavedNetworkOperator(NetworkSelectMessage networkSelectMessage) {
        int subId = getSubId();
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            Editor edit = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
            edit.putString(NETWORK_SELECTION_KEY + subId, networkSelectMessage.operatorNumeric);
            edit.putString(NETWORK_SELECTION_NAME_KEY + subId, networkSelectMessage.operatorAlphaLong);
            if (!edit.commit()) {
                Rlog.e(LOG_TAG, "failed to commit network selection preference");
                return;
            }
            return;
        }
        Rlog.e(LOG_TAG, "Cannot update network selection preference due to invalid subId " + subId);
    }

    public void acquireOwnershipOfImsPhone(ImsPhone imsPhone) {
        synchronized (this.mImsLock) {
            if (imsPhone == null) {
                return;
            }
            if (this.mImsPhone != null) {
                Rlog.e(LOG_TAG, "acquireOwnershipOfImsPhone: non-null mImsPhone. Shouldn't happen - but disposing");
                this.mImsPhone.dispose();
            }
            this.mImsPhone = imsPhone;
            this.mImsServiceReady = true;
            this.mImsPhone.updateParentPhone(this);
            CallManager.getInstance().registerPhone(this.mImsPhone);
            this.mImsPhone.registerForSilentRedial(this, 32, null);
        }
    }

    public void addParticipant(String str) throws CallStateException {
        throw new CallStateException("addParticipant is not supported in this phone " + this);
    }

    public int changeMode(boolean z, String str, String str2, String str3, int i, String str4, String str5, String str6, String str7) {
        return this.mDcTracker.changeOemKddiCpaMode(z, str, str2, str3, i, str4, str5, str6, str7);
    }

    public void declineCall() throws CallStateException {
        throw new CallStateException("declineCall is not supported in this phone " + this);
    }

    public void deflectCall(String str) throws CallStateException {
        throw new CallStateException("Unexpected deflectCall method call");
    }

    public Connection dial(String str, int i, int i2) throws CallStateException {
        if (!TelBrand.IS_DCM) {
            return null;
        }
        throw new CallStateException("Dial with CallDetails is not supported in this phone " + this);
    }

    public Connection dial(String str, int i, Bundle bundle, int i2) throws CallStateException {
        if (!TelBrand.IS_DCM) {
            return null;
        }
        throw new CallStateException("Dial with CallDetails is not supported in this phone " + this);
    }

    public Connection dial(String str, UUSInfo uUSInfo, int i, int i2) throws CallStateException {
        if (!TelBrand.IS_DCM) {
            return null;
        }
        throw new CallStateException("Dial with CallDetails is not supported in this phone " + this);
    }

    /* Access modifiers changed, original: protected */
    public Connection dialInternal(String str, UUSInfo uUSInfo, int i) throws CallStateException {
        return null;
    }

    public void disableDnsCheck(boolean z) {
        this.mDnsCheckDisabled = z;
        Editor edit = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        edit.putBoolean(DNS_SERVER_CHECK_DISABLED_KEY, z);
        edit.apply();
    }

    public void dispose() {
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            this.mContext.unregisterReceiver(this.mImsIntentReceiver);
            this.mCi.unSetOnCallRing(this);
            this.mDcTracker.cleanUpAllConnections(null);
            this.mIsTheCurrentActivePhone = false;
            this.mSmsStorageMonitor.dispose();
            this.mSmsUsageMonitor.dispose();
            this.mUiccController.unregisterForIccChanged(this);
            this.mCi.unregisterForSrvccStateChanged(this);
            this.mCi.unSetOnUnsolOemHookRaw(this);
            if (this.mTelephonyTester != null) {
                this.mTelephonyTester.dispose();
            }
            ImsPhone imsPhone = this.mImsPhone;
            if (imsPhone != null) {
                imsPhone.unregisterForSilentRedial(this);
                imsPhone.dispose();
            }
        }
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("PhoneBase: subId=" + getSubId());
        printWriter.println(" mPhoneId=" + this.mPhoneId);
        printWriter.println(" mCi=" + this.mCi);
        printWriter.println(" mDnsCheckDisabled=" + this.mDnsCheckDisabled);
        printWriter.println(" mDcTracker=" + this.mDcTracker);
        printWriter.println(" mDoesRilSendMultipleCallRing=" + this.mDoesRilSendMultipleCallRing);
        printWriter.println(" mCallRingContinueToken=" + this.mCallRingContinueToken);
        printWriter.println(" mCallRingDelay=" + this.mCallRingDelay);
        printWriter.println(" mIsTheCurrentActivePhone=" + this.mIsTheCurrentActivePhone);
        printWriter.println(" mIsVoiceCapable=" + this.mIsVoiceCapable);
        printWriter.println(" mIccRecords=" + this.mIccRecords.get());
        printWriter.println(" mUiccApplication=" + this.mUiccApplication.get());
        printWriter.println(" mSmsStorageMonitor=" + this.mSmsStorageMonitor);
        printWriter.println(" mSmsUsageMonitor=" + this.mSmsUsageMonitor);
        printWriter.flush();
        printWriter.println(" mLooper=" + this.mLooper);
        printWriter.println(" mContext=" + this.mContext);
        printWriter.println(" mNotifier=" + this.mNotifier);
        printWriter.println(" mSimulatedRadioControl=" + this.mSimulatedRadioControl);
        printWriter.println(" mUnitTestMode=" + this.mUnitTestMode);
        printWriter.println(" isDnsCheckDisabled()=" + isDnsCheckDisabled());
        printWriter.println(" getUnitTestMode()=" + getUnitTestMode());
        printWriter.println(" getState()=" + getState());
        printWriter.println(" getIccSerialNumber()=" + getIccSerialNumber());
        printWriter.println(" getIccRecordsLoaded()=" + getIccRecordsLoaded());
        printWriter.println(" getMessageWaitingIndicator()=" + getMessageWaitingIndicator());
        printWriter.println(" getCallForwardingIndicator()=" + getCallForwardingIndicator());
        printWriter.println(" isInEmergencyCall()=" + isInEmergencyCall());
        printWriter.flush();
        printWriter.println(" isInEcm()=" + isInEcm());
        printWriter.println(" getPhoneName()=" + getPhoneName());
        printWriter.println(" getPhoneType()=" + getPhoneType());
        printWriter.println(" getVoiceMessageCount()=" + getVoiceMessageCount());
        printWriter.println(" getActiveApnTypes()=" + getActiveApnTypes());
        printWriter.println(" isDataConnectivityPossible()=" + isDataConnectivityPossible());
        printWriter.println(" needsOtaServiceProvisioning=" + needsOtaServiceProvisioning());
        printWriter.flush();
        printWriter.println("++++++++++++++++++++++++++++++++");
        try {
            this.mDcTracker.dump(fileDescriptor, printWriter, strArr);
        } catch (Exception e) {
            e.printStackTrace();
        }
        printWriter.flush();
        printWriter.println("++++++++++++++++++++++++++++++++");
        try {
            getServiceStateTracker().dump(fileDescriptor, printWriter, strArr);
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        printWriter.flush();
        printWriter.println("++++++++++++++++++++++++++++++++");
        try {
            getCallTracker().dump(fileDescriptor, printWriter, strArr);
        } catch (Exception e22) {
            e22.printStackTrace();
        }
        printWriter.flush();
        printWriter.println("++++++++++++++++++++++++++++++++");
        try {
            ((RIL) this.mCi).dump(fileDescriptor, printWriter, strArr);
        } catch (Exception e222) {
            e222.printStackTrace();
        }
        printWriter.flush();
        printWriter.println("++++++++++++++++++++++++++++++++");
    }

    public void enableEnhancedVoicePrivacy(boolean z, Message message) {
        logUnexpectedCdmaMethodCall("enableEnhancedVoicePrivacy");
    }

    public void exitEmergencyCallbackMode() {
        logUnexpectedCdmaMethodCall("exitEmergencyCallbackMode");
    }

    public String getActionAttached() {
        return this.mActionAttached;
    }

    public String getActionDetached() {
        return this.mActionDetached;
    }

    public String getActiveApnHost(String str) {
        return this.mDcTracker.getActiveApnString(str);
    }

    public String[] getActiveApnTypes() {
        return this.mDcTracker.getActiveApnTypes();
    }

    public List<CellInfo> getAllCellInfo() {
        return privatizeCellInfoList(getServiceStateTracker().getAllCellInfo());
    }

    public void getBandPref(Message message) {
        this.mCi.getBandPref(message);
    }

    public ServiceState getBaseServiceState() {
        return getServiceState();
    }

    public int getBrand() {
        return 0;
    }

    public void getCallBarring(String str, Message message) {
    }

    public void getCallBarring(String str, String str2, int i, Message message) {
    }

    public void getCallBarring(String str, String str2, Message message) {
    }

    public void getCallBarringOption(String str, String str2, Message message) {
        logUnexpectedCdmaMethodCall("getCallBarringOption");
    }

    public boolean getCallForwardingIndicator() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        return iccRecords != null ? iccRecords.getVoiceCallForwardingFlag() : false;
    }

    public void getCallForwardingOption(int i, int i2, Message message) {
    }

    public boolean getCallForwardingPreference() {
        Rlog.d(LOG_TAG, "Get callforwarding info from perferences");
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        Editor edit;
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            if (!defaultSharedPreferences.contains(CF_ENABLED + getSubId()) && defaultSharedPreferences.contains(CF_ENABLED + this.mPhoneId)) {
                setCallForwardingPreference(defaultSharedPreferences.getBoolean(CF_ENABLED + this.mPhoneId, false));
                edit = defaultSharedPreferences.edit();
                edit.remove(CF_ENABLED + this.mPhoneId);
                edit.commit();
            }
        } else if (!defaultSharedPreferences.contains(CF_ENABLED + getSubId()) && defaultSharedPreferences.contains(CF_ENABLED)) {
            setCallForwardingPreference(defaultSharedPreferences.getBoolean(CF_ENABLED, false));
            edit = defaultSharedPreferences.edit();
            edit.remove(CF_ENABLED);
            edit.commit();
        }
        return defaultSharedPreferences.getBoolean(CF_ENABLED + getSubId(), false);
    }

    public void getCallForwardingUncondTimerOption(int i, Message message) {
        Rlog.e(LOG_TAG, "getCallForwardingUncondTimerOption error ");
    }

    public CallTracker getCallTracker() {
        return null;
    }

    public void getCallWaiting(int i, Message message) {
    }

    public void getCallWaiting(Message message) {
    }

    public int getCdmaEriIconIndex() {
        logUnexpectedCdmaMethodCall("getCdmaEriIconIndex");
        return -1;
    }

    public int getCdmaEriIconMode() {
        logUnexpectedCdmaMethodCall("getCdmaEriIconMode");
        return -1;
    }

    public String getCdmaEriText() {
        logUnexpectedCdmaMethodCall("getCdmaEriText");
        return "GSM nw, no ERI";
    }

    public String getCdmaMin() {
        logUnexpectedCdmaMethodCall("getCdmaMin");
        return null;
    }

    public String getCdmaPrlVersion() {
        logUnexpectedCdmaMethodCall("getCdmaPrlVersion");
        return null;
    }

    public String[] getConnInfo() {
        return this.mDcTracker.getOemKddiCpaConnInfo();
    }

    public int getConnStatus() {
        return this.mDcTracker.getOemKddiCpaConnStatus();
    }

    public Context getContext() {
        return this.mContext;
    }

    public AppType getCurrentUiccAppType() {
        UiccCardApplication uiccCardApplication = (UiccCardApplication) this.mUiccApplication.get();
        return uiccCardApplication != null ? uiccCardApplication.getType() : AppType.APPTYPE_UNKNOWN;
    }

    public DataState getDataConnectionState() {
        return getDataConnectionState("default");
    }

    public void getEnhancedVoicePrivacy(Message message) {
        logUnexpectedCdmaMethodCall("getEnhancedVoicePrivacy");
    }

    public Handler getHandler() {
        return this;
    }

    public IccCard getIccCard() {
        return null;
    }

    public IccFileHandler getIccFileHandler() {
        Object obj;
        UiccCardApplication uiccCardApplication = (UiccCardApplication) this.mUiccApplication.get();
        if (uiccCardApplication == null) {
            Rlog.d(LOG_TAG, "getIccFileHandler: uiccApplication == null, return null");
            obj = null;
        } else {
            obj = uiccCardApplication.getIccFileHandler();
        }
        Rlog.d(LOG_TAG, "getIccFileHandler: fh=" + obj);
        return obj;
    }

    public IccRecords getIccRecords() {
        UiccCardApplication uiccCardApplication = (UiccCardApplication) this.mUiccApplication.get();
        return uiccCardApplication == null ? null : uiccCardApplication.getIccRecords();
    }

    public boolean getIccRecordsLoaded() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        return iccRecords != null ? iccRecords.getRecordsLoaded() : false;
    }

    public String getIccSerialNumber() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        return iccRecords != null ? iccRecords.getIccId() : null;
    }

    public Phone getImsPhone() {
        return this.mImsPhone;
    }

    public String getImsiOnSimLock() {
        return null;
    }

    public void getIncomingAnonymousCallBarring(Message message) {
    }

    public void getIncomingSpecificDnCallBarring(Message message) {
    }

    public IsimRecords getIsimRecords() {
        Rlog.e(LOG_TAG, "getIsimRecords() is only supported on LTE devices");
        return null;
    }

    public LinkProperties getLinkProperties(String str) {
        return this.mDcTracker.getLinkProperties(str);
    }

    public int getLteOnCdmaMode() {
        return this.mCi.getLteOnCdmaMode();
    }

    public String getMccMncOnSimLock() {
        return null;
    }

    public boolean getMessageWaitingIndicator() {
        return this.mVmCount != 0;
    }

    public String getMsisdn() {
        logUnexpectedGsmMethodCall("getMsisdn");
        return null;
    }

    public String getNai() {
        return null;
    }

    public NetworkCapabilities getNetworkCapabilities(String str) {
        return this.mDcTracker.getNetworkCapabilities(str);
    }

    public void getNetworkSelectionMode(Message message) {
        this.mCi.getNetworkSelectionMode(message);
    }

    public String[] getPcscfAddress(String str) {
        return this.mDcTracker.getPcscfAddress(str);
    }

    public int getPhoneId() {
        return this.mPhoneId;
    }

    public String getPhoneName() {
        return this.mName;
    }

    public abstract int getPhoneType();

    public void getPreferredNetworkType(Message message) {
        this.mCi.getPreferredNetworkType(message);
    }

    public void getPreferredNetworkTypeWithOptimizeSetting(Message message) {
        this.mCi.getPreferredNetworkTypeWithOptimizeSetting(message);
    }

    public ServiceStateTracker getServiceStateTracker() {
        return null;
    }

    public SignalStrength getSignalStrength() {
        ServiceStateTracker serviceStateTracker = getServiceStateTracker();
        return serviceStateTracker == null ? new SignalStrength() : serviceStateTracker.getSignalStrength();
    }

    public String getSimImsi() {
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        Editor edit;
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            if (!defaultSharedPreferences.contains(SIM_IMSI + getSubId()) && defaultSharedPreferences.contains(VM_SIM_IMSI + this.mPhoneId)) {
                setSimImsi(defaultSharedPreferences.getString(VM_SIM_IMSI + this.mPhoneId, null));
                edit = defaultSharedPreferences.edit();
                edit.remove(VM_SIM_IMSI + this.mPhoneId);
                edit.commit();
            }
        } else if (!defaultSharedPreferences.contains(SIM_IMSI + getSubId()) && defaultSharedPreferences.contains(VM_SIM_IMSI)) {
            setSimImsi(defaultSharedPreferences.getString(VM_SIM_IMSI, null));
            edit = defaultSharedPreferences.edit();
            edit.remove(VM_SIM_IMSI);
            edit.commit();
        }
        return defaultSharedPreferences.getString(SIM_IMSI + getSubId(), null);
    }

    public SimulatedRadioControl getSimulatedRadioControl() {
        return this.mSimulatedRadioControl;
    }

    public void getSmscAddress(Message message) {
        this.mCi.getSmscAddress(message);
    }

    public abstract State getState();

    public int getSubId() {
        return SubscriptionController.getInstance().getSubIdUsingPhoneId(this.mPhoneId);
    }

    public String getSystemProperty(String str, String str2) {
        return getUnitTestMode() ? null : SystemProperties.get(str, str2);
    }

    public UiccCard getUiccCard() {
        return this.mUiccController.getUiccCard(this.mPhoneId);
    }

    public boolean getUnitTestMode() {
        return this.mUnitTestMode;
    }

    public UsimServiceTable getUsimServiceTable() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        return iccRecords != null ? iccRecords.getUsimServiceTable() : null;
    }

    public int getVoiceMessageCount() {
        return this.mVmCount;
    }

    public int getVoicePhoneServiceState() {
        ImsPhone imsPhone = this.mImsPhone;
        return (imsPhone == null || imsPhone.getServiceState().getState() != 0) ? getServiceState().getState() : 0;
    }

    public void handleMessage(Message message) {
        switch (message.what) {
            case 16:
            case 17:
                handleSetSelectNetwork((AsyncResult) message.obj);
                return;
            default:
                if (this.mIsTheCurrentActivePhone) {
                    AsyncResult asyncResult;
                    switch (message.what) {
                        case 14:
                            Rlog.d(LOG_TAG, "Event EVENT_CALL_RING Received state=" + getState());
                            if (((AsyncResult) message.obj).exception == null) {
                                State state = getState();
                                if (this.mDoesRilSendMultipleCallRing || !(state == State.RINGING || state == State.IDLE)) {
                                    notifyIncomingRing();
                                    return;
                                }
                                this.mCallRingContinueToken++;
                                sendIncomingCallRingNotification(this.mCallRingContinueToken);
                                return;
                            }
                            return;
                        case 15:
                            Rlog.d(LOG_TAG, "Event EVENT_CALL_RING_CONTINUE Received stat=" + getState());
                            if (getState() == State.RINGING) {
                                sendIncomingCallRingNotification(message.arg1);
                                return;
                            }
                            return;
                        case 30:
                            onUpdateIccAvailability();
                            return;
                        case 31:
                            asyncResult = (AsyncResult) message.obj;
                            if (asyncResult.exception == null) {
                                handleSrvccStateChanged((int[]) asyncResult.result);
                                return;
                            } else {
                                Rlog.e(LOG_TAG, "Srvcc exception: " + asyncResult.exception);
                                return;
                            }
                        case 32:
                            Rlog.d(LOG_TAG, "Event EVENT_INITIATE_SILENT_REDIAL Received");
                            asyncResult = (AsyncResult) message.obj;
                            if (asyncResult.exception == null && asyncResult.result != null) {
                                String str = (String) asyncResult.result;
                                if (!TextUtils.isEmpty(str)) {
                                    try {
                                        dialInternal(str, null, 0);
                                        return;
                                    } catch (CallStateException e) {
                                        Rlog.e(LOG_TAG, "silent redial failed: " + e);
                                        return;
                                    }
                                }
                                return;
                            }
                            return;
                        case 34:
                            asyncResult = (AsyncResult) message.obj;
                            if (asyncResult.exception == null) {
                                byte[] bArr = (byte[]) asyncResult.result;
                                Rlog.d(LOG_TAG, "EVENT_UNSOL_OEM_HOOK_RAW data=" + IccUtils.bytesToHexString(bArr));
                                this.mNotifier.notifyOemHookRawEventForSubscriber(getSubId(), bArr);
                                return;
                            }
                            Rlog.e(LOG_TAG, "OEM hook raw exception: " + asyncResult.exception);
                            return;
                        case EVENT_SET_RAT_MODE_OPTIMIZE_SETTING_COMPLETE /*10000*/:
                            this.mCi.setPreferredNetworkType(message.arg1, (Message) ((AsyncResult) message.obj).userObj);
                            return;
                        default:
                            throw new RuntimeException("unexpected event not handled");
                    }
                }
                Rlog.e(LOG_TAG, "Received message " + message + "[" + message.what + "] while being destroyed. Ignoring.");
                return;
        }
    }

    public boolean hasMatchedTetherApnSetting() {
        return this.mDcTracker.hasMatchedTetherApnSetting();
    }

    public void invokeOemRilRequestRaw(byte[] bArr, Message message) {
        this.mCi.invokeOemRilRequestRaw(bArr, message);
    }

    public void invokeOemRilRequestStrings(String[] strArr, Message message) {
        this.mCi.invokeOemRilRequestStrings(strArr, message);
    }

    public boolean isCspPlmnEnabled() {
        logUnexpectedGsmMethodCall("isCspPlmnEnabled");
        return false;
    }

    public boolean isDataConnectivityPossible() {
        return isDataConnectivityPossible("default");
    }

    public boolean isDataConnectivityPossible(String str) {
        return this.mDcTracker != null && this.mDcTracker.isDataPossible(str);
    }

    public boolean isDnsCheckDisabled() {
        return this.mDnsCheckDisabled;
    }

    public boolean isDunConnectionPossible() {
        return false;
    }

    public boolean isImsRegistered() {
        ImsPhone imsPhone = this.mImsPhone;
        boolean z = false;
        if (imsPhone != null) {
            z = imsPhone.isImsRegistered();
        } else {
            ServiceStateTracker serviceStateTracker = getServiceStateTracker();
            if (serviceStateTracker != null) {
                z = serviceStateTracker.isImsRegistered();
            }
        }
        Rlog.d(LOG_TAG, "isImsRegistered =" + z);
        return z;
    }

    public boolean isImsVtCallPresent() {
        boolean z = false;
        if (this.mImsPhone != null && (isImsVideoCall(this.mImsPhone.getForegroundCall()) || isImsVideoCall(this.mImsPhone.getBackgroundCall()) || isImsVideoCall(this.mImsPhone.getRingingCall()))) {
            z = true;
        }
        Rlog.d(LOG_TAG, "isVideoCallActive: " + z);
        return z;
    }

    public boolean isInEcm() {
        return false;
    }

    public boolean isInEmergencyCall() {
        return false;
    }

    public boolean isManualNetSelAllowed() {
        return false;
    }

    public boolean isMccMncMarkedAsNonRoaming(String str) {
        return getRoamingOverrideHelper(GSM_NON_ROAMING_LIST_OVERRIDE_PREFIX, str);
    }

    public boolean isMccMncMarkedAsRoaming(String str) {
        return getRoamingOverrideHelper(GSM_ROAMING_LIST_OVERRIDE_PREFIX, str);
    }

    public boolean isMinInfoReady() {
        logUnexpectedCdmaMethodCall("isMinInfoReady");
        return false;
    }

    public boolean isOnDemandDataPossible(String str) {
        return this.mDcTracker != null && this.mDcTracker.isOnDemandDataPossible(str);
    }

    public boolean isOtaSpNumber(String str) {
        return false;
    }

    public boolean isRadioAvailable() {
        return this.mCi.getRadioState().isAvailable();
    }

    public boolean isSidMarkedAsNonRoaming(int i) {
        return getRoamingOverrideHelper(CDMA_NON_ROAMING_LIST_OVERRIDE_PREFIX, Integer.toString(i));
    }

    public boolean isSidMarkedAsRoaming(int i) {
        return getRoamingOverrideHelper(CDMA_ROAMING_LIST_OVERRIDE_PREFIX, Integer.toString(i));
    }

    public boolean isUtEnabled() {
        return false;
    }

    public void migrate(RegistrantList registrantList, RegistrantList registrantList2) {
        registrantList2.removeCleared();
        int size = registrantList2.size();
        for (int i = 0; i < size; i++) {
            Message messageForRegistrant = ((Registrant) registrantList2.get(i)).messageForRegistrant();
            if (messageForRegistrant == null) {
                Rlog.d(LOG_TAG, "msg is null");
            } else if (messageForRegistrant.obj != CallManager.getInstance().getRegistrantIdentifier()) {
                registrantList.add((Registrant) registrantList2.get(i));
            }
        }
    }

    public void migrateFrom(PhoneBase phoneBase) {
        migrate(this.mHandoverRegistrants, phoneBase.mHandoverRegistrants);
        migrate(this.mPreciseCallStateRegistrants, phoneBase.mPreciseCallStateRegistrants);
        migrate(this.mNewRingingConnectionRegistrants, phoneBase.mNewRingingConnectionRegistrants);
        migrate(this.mIncomingRingRegistrants, phoneBase.mIncomingRingRegistrants);
        migrate(this.mDisconnectRegistrants, phoneBase.mDisconnectRegistrants);
        migrate(this.mServiceStateRegistrants, phoneBase.mServiceStateRegistrants);
        migrate(this.mMmiCompleteRegistrants, phoneBase.mMmiCompleteRegistrants);
        migrate(this.mMmiRegistrants, phoneBase.mMmiRegistrants);
        migrate(this.mUnknownConnectionRegistrants, phoneBase.mUnknownConnectionRegistrants);
        migrate(this.mSuppServiceFailedRegistrants, phoneBase.mSuppServiceFailedRegistrants);
    }

    public boolean needsOtaServiceProvisioning() {
        return false;
    }

    public void notifyCallForwardingIndicator() {
        Rlog.e(LOG_TAG, "Error! This function should never be executed, inactive CDMAPhone.");
    }

    public void notifyCellInfo(List<CellInfo> list) {
        this.mNotifier.notifyCellInfo(this, privatizeCellInfoList(list));
    }

    public void notifyDataActivity() {
        this.mNotifier.notifyDataActivity(this);
    }

    public void notifyDataConnection(String str) {
        for (String str2 : getActiveApnTypes()) {
            this.mNotifier.notifyDataConnection(this, str, str2, getDataConnectionState(str2));
        }
    }

    public void notifyDataConnection(String str, String str2) {
        this.mNotifier.notifyDataConnection(this, str, str2, getDataConnectionState(str2));
    }

    public void notifyDataConnection(String str, String str2, DataState dataState) {
        this.mNotifier.notifyDataConnection(this, str, str2, dataState);
    }

    public void notifyDataConnectionFailed(String str, String str2) {
        this.mNotifier.notifyDataConnectionFailed(this, str, str2);
    }

    public void notifyDataConnectionRealTimeInfo(DataConnectionRealTimeInfo dataConnectionRealTimeInfo) {
        this.mNotifier.notifyDataConnectionRealTimeInfo(this, dataConnectionRealTimeInfo);
    }

    /* Access modifiers changed, original: protected */
    public void notifyDisconnectP(Connection connection) {
        this.mDisconnectRegistrants.notifyRegistrants(new AsyncResult(null, connection, null));
    }

    public void notifyForVideoCapabilityChanged(boolean z) {
        this.mIsVideoCapable = z;
        this.mVideoCapabilityChangedRegistrants.notifyRegistrants(new AsyncResult(null, Boolean.valueOf(z), null));
    }

    public void notifyHandoverStateChanged(Connection connection) {
        this.mHandoverRegistrants.notifyRegistrants(new AsyncResult(null, connection, null));
    }

    public void notifyMessageWaitingIndicator() {
        if (this.mIsVoiceCapable) {
            this.mNotifier.notifyMessageWaitingChanged(this);
        }
    }

    public void notifyNewRingingConnectionP(Connection connection) {
        if (this.mIsVoiceCapable) {
            this.mNewRingingConnectionRegistrants.notifyRegistrants(new AsyncResult(null, connection, null));
            if (!hasMessages(15)) {
                if ((getForegroundCall().isIdle() || getForegroundCall().isDialingOrAlerting()) && getBackgroundCall().isIdle()) {
                    Rlog.d(LOG_TAG, "notifyNewRingingConnectionP(): send EVENT_CALL_RING_CONTINUE");
                    sendMessageDelayed(obtainMessage(15, this.mCallRingContinueToken, 0), (long) this.mCallRingDelay);
                }
            }
        }
    }

    public void notifyOtaspChanged(int i) {
        this.mNotifier.notifyOtaspChanged(this, i);
    }

    /* Access modifiers changed, original: protected */
    public void notifyPreciseCallStateChangedP() {
        this.mPreciseCallStateRegistrants.notifyRegistrants(new AsyncResult(null, this, null));
        this.mNotifier.notifyPreciseCallState(this);
    }

    public void notifyPreciseDataConnectionFailed(String str, String str2, String str3, String str4) {
        this.mNotifier.notifyPreciseDataConnectionFailed(this, str, str2, str3, str4);
    }

    /* Access modifiers changed, original: protected */
    public void notifyServiceStateChangedP(ServiceState serviceState) {
        this.mServiceStateRegistrants.notifyRegistrants(new AsyncResult(null, serviceState, null));
        this.mNotifier.notifyServiceState(this);
    }

    public void notifySignalStrength() {
        this.mNotifier.notifySignalStrength(this);
    }

    public void notifyUnknownConnectionP(Connection connection) {
        this.mUnknownConnectionRegistrants.notifyResult(connection);
    }

    public void notifyVoLteServiceStateChanged(VoLteServiceState voLteServiceState) {
        this.mNotifier.notifyVoLteServiceStateChanged(this, voLteServiceState);
    }

    public void nvReadItem(int i, Message message) {
        this.mCi.nvReadItem(i, message);
    }

    public void nvResetConfig(int i, Message message) {
        this.mCi.nvResetConfig(i, message);
    }

    public void nvWriteCdmaPrl(byte[] bArr, Message message) {
        this.mCi.nvWriteCdmaPrl(bArr, message);
    }

    public void nvWriteItem(int i, String str, Message message) {
        this.mCi.nvWriteItem(i, str, message);
    }

    public abstract void onUpdateIccAvailability();

    public void queryAvailableBandMode(Message message) {
        this.mCi.queryAvailableBandMode(message);
    }

    public void queryCdmaRoamingPreference(Message message) {
        this.mCi.queryCdmaRoamingPreference(message);
    }

    public void queryTTYMode(Message message) {
        this.mCi.queryTTYMode(message);
    }

    public void registerFoT53ClirlInfo(Handler handler, int i, Object obj) {
        this.mCi.registerFoT53ClirlInfo(handler, i, obj);
    }

    public void registerForCallWaiting(Handler handler, int i, Object obj) {
        logUnexpectedCdmaMethodCall("registerForCallWaiting");
    }

    public void registerForCdmaOtaStatusChange(Handler handler, int i, Object obj) {
        logUnexpectedCdmaMethodCall("registerForCdmaOtaStatusChange");
    }

    public void registerForDisconnect(Handler handler, int i, Object obj) {
        checkCorrectThread(handler);
        this.mDisconnectRegistrants.addUnique(handler, i, obj);
    }

    public void registerForDisplayInfo(Handler handler, int i, Object obj) {
        this.mCi.registerForDisplayInfo(handler, i, obj);
    }

    public void registerForEcmTimerReset(Handler handler, int i, Object obj) {
        logUnexpectedCdmaMethodCall("registerForEcmTimerReset");
    }

    public void registerForHandoverStateChanged(Handler handler, int i, Object obj) {
        checkCorrectThread(handler);
        this.mHandoverRegistrants.addUnique(handler, i, obj);
    }

    public void registerForInCallVoicePrivacyOff(Handler handler, int i, Object obj) {
        this.mCi.registerForInCallVoicePrivacyOff(handler, i, obj);
    }

    public void registerForInCallVoicePrivacyOn(Handler handler, int i, Object obj) {
        this.mCi.registerForInCallVoicePrivacyOn(handler, i, obj);
    }

    public void registerForIncomingRing(Handler handler, int i, Object obj) {
        checkCorrectThread(handler);
        this.mIncomingRingRegistrants.addUnique(handler, i, obj);
    }

    public void registerForLineControlInfo(Handler handler, int i, Object obj) {
        this.mCi.registerForLineControlInfo(handler, i, obj);
    }

    public void registerForMmiComplete(Handler handler, int i, Object obj) {
        checkCorrectThread(handler);
        this.mMmiCompleteRegistrants.addUnique(handler, i, obj);
    }

    public void registerForMmiInitiate(Handler handler, int i, Object obj) {
        checkCorrectThread(handler);
        this.mMmiRegistrants.addUnique(handler, i, obj);
    }

    public void registerForNewRingingConnection(Handler handler, int i, Object obj) {
        checkCorrectThread(handler);
        this.mNewRingingConnectionRegistrants.addUnique(handler, i, obj);
    }

    public void registerForNumberInfo(Handler handler, int i, Object obj) {
        this.mCi.registerForNumberInfo(handler, i, obj);
    }

    public void registerForOnHoldTone(Handler handler, int i, Object obj) {
    }

    public void registerForPreciseCallStateChanged(Handler handler, int i, Object obj) {
        checkCorrectThread(handler);
        this.mPreciseCallStateRegistrants.addUnique(handler, i, obj);
    }

    public void registerForRadioOffOrNotAvailable(Handler handler, int i, Object obj) {
        this.mRadioOffOrNotAvailableRegistrants.addUnique(handler, i, obj);
    }

    public void registerForRedirectedNumberInfo(Handler handler, int i, Object obj) {
        this.mCi.registerForRedirectedNumberInfo(handler, i, obj);
    }

    public void registerForResendIncallMute(Handler handler, int i, Object obj) {
        this.mCi.registerForResendIncallMute(handler, i, obj);
    }

    public void registerForRingbackTone(Handler handler, int i, Object obj) {
        this.mCi.registerForRingbackTone(handler, i, obj);
    }

    public void registerForServiceStateChanged(Handler handler, int i, Object obj) {
        checkCorrectThread(handler);
        this.mServiceStateRegistrants.add(handler, i, obj);
    }

    public void registerForSignalInfo(Handler handler, int i, Object obj) {
        this.mCi.registerForSignalInfo(handler, i, obj);
    }

    public void registerForSimRecordsLoaded(Handler handler, int i, Object obj) {
        logUnexpectedCdmaMethodCall("registerForSimRecordsLoaded");
    }

    public void registerForSubscriptionInfoReady(Handler handler, int i, Object obj) {
        logUnexpectedCdmaMethodCall("registerForSubscriptionInfoReady");
    }

    public void registerForSuppServiceFailed(Handler handler, int i, Object obj) {
        checkCorrectThread(handler);
        this.mSuppServiceFailedRegistrants.addUnique(handler, i, obj);
    }

    public void registerForT53AudioControlInfo(Handler handler, int i, Object obj) {
        this.mCi.registerForT53AudioControlInfo(handler, i, obj);
    }

    public void registerForTtyModeReceived(Handler handler, int i, Object obj) {
    }

    public void registerForUnknownConnection(Handler handler, int i, Object obj) {
        checkCorrectThread(handler);
        this.mUnknownConnectionRegistrants.addUnique(handler, i, obj);
    }

    public void registerForVideoCapabilityChanged(Handler handler, int i, Object obj) {
        checkCorrectThread(handler);
        this.mVideoCapabilityChangedRegistrants.addUnique(handler, i, obj);
        notifyForVideoCapabilityChanged(this.mIsVideoCapable);
    }

    public ImsPhone relinquishOwnershipOfImsPhone() {
        ImsPhone imsPhone = null;
        synchronized (this.mImsLock) {
            if (this.mImsPhone == null) {
            } else {
                imsPhone = this.mImsPhone;
                this.mImsPhone = null;
                CallManager.getInstance().unregisterPhone(imsPhone);
                imsPhone.unregisterForSilentRedial(this);
            }
        }
        return imsPhone;
    }

    public void removeReferences() {
        this.mSmsStorageMonitor = null;
        this.mSmsUsageMonitor = null;
        this.mIccRecords.set(null);
        this.mUiccApplication.set(null);
        this.mDcTracker = null;
        this.mUiccController = null;
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null) {
            imsPhone.removeReferences();
            this.mImsPhone = null;
        }
    }

    public void requestChangeCbPsw(String str, String str2, String str3, Message message) {
        logUnexpectedCdmaMethodCall("requestChangeCbPsw");
    }

    public void resetDunProfiles() {
    }

    public void restoreSavedNetworkSelection(Message message) {
        String savedNetworkSelection = getSavedNetworkSelection();
        if (TextUtils.isEmpty(savedNetworkSelection)) {
            this.mCi.setNetworkSelectionModeAutomatic(message);
        } else {
            this.mCi.setNetworkSelectionModeManual(savedNetworkSelection, message);
        }
    }

    public void selectNetworkManually(OperatorInfo operatorInfo, Message message) {
        NetworkSelectMessage networkSelectMessage = new NetworkSelectMessage();
        networkSelectMessage.message = message;
        networkSelectMessage.operatorNumeric = operatorInfo.getOperatorNumeric();
        networkSelectMessage.operatorAlphaLong = operatorInfo.getOperatorAlphaLong();
        Message obtainMessage = obtainMessage(16, networkSelectMessage);
        if (operatorInfo.getRadioTech().equals("")) {
            this.mCi.setNetworkSelectionModeManual(operatorInfo.getOperatorNumeric(), obtainMessage);
        } else {
            this.mCi.setNetworkSelectionModeManual(operatorInfo.getOperatorNumeric() + "+" + operatorInfo.getRadioTech(), obtainMessage);
        }
        updateSavedNetworkOperator(networkSelectMessage);
    }

    public void sendBurstDtmf(String str, int i, int i2, Message message) {
        logUnexpectedCdmaMethodCall("sendBurstDtmf");
    }

    public void setBandMode(int i, Message message) {
        this.mCi.setBandMode(i, message);
    }

    public void setBandPref(long j, int i, Message message) {
        this.mCi.setBandPref(j, i, message);
    }

    public void setCallBarring(String str, boolean z, String str2, int i, Message message) {
    }

    public void setCallBarring(String str, boolean z, String str2, Message message) {
    }

    public void setCallBarringOption(String str, boolean z, String str2, Message message) {
        logUnexpectedCdmaMethodCall("setCallBarringOption");
    }

    public void setCallForwardingOption(int i, int i2, int i3, String str, int i4, Message message) {
    }

    public void setCallForwardingOption(int i, int i2, String str, int i3, Message message) {
    }

    public void setCallForwardingPreference(boolean z) {
        Rlog.d(LOG_TAG, "Set callforwarding info to perferences");
        Editor edit = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
        edit.putBoolean(CF_ENABLED + getSubId(), z);
        edit.commit();
        setSimImsi(getSubscriberId());
    }

    public void setCallForwardingUncondTimerOption(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message) {
        Rlog.e(LOG_TAG, "setCallForwardingUncondTimerOption error ");
    }

    public void setCallWaiting(boolean z, int i, Message message) {
    }

    public void setCallWaiting(boolean z, Message message) {
    }

    /* Access modifiers changed, original: protected */
    public void setCardInPhoneBook() {
    }

    public void setCdmaRoamingPreference(int i, Message message) {
        this.mCi.setCdmaRoamingPreference(i, message);
    }

    public void setCdmaSubscription(int i, Message message) {
        this.mCi.setCdmaSubscriptionSource(i, message);
    }

    public void setCellInfoListRate(int i) {
        this.mCi.setCellInfoListRate(i, null);
    }

    public void setEchoSuppressionEnabled() {
    }

    public void setImsRegistrationState(boolean z) {
        this.mDcTracker.setImsRegistrationState(z);
    }

    public void setIncomingAnonymousCallBarring(boolean z, Message message) {
    }

    public void setIncomingSpecificDnCallBarring(int i, String[] strArr, Message message) {
    }

    public void setLimitationByChameleon(boolean z, Message message) {
        this.mCi.setLimitationByChameleon(z, message);
    }

    public void setLocalCallHold(int i) {
        this.mCi.setLocalCallHold(i);
    }

    public void setMobileDataEnabledDun(boolean z) {
    }

    public void setModemSettingsByChameleon(int i, Message message) {
        this.mCi.setModemSettingsByChameleon(i, message);
    }

    public void setNetworkSelectionModeAutomatic(Message message) {
        NetworkSelectMessage networkSelectMessage = new NetworkSelectMessage();
        networkSelectMessage.message = message;
        networkSelectMessage.operatorNumeric = "";
        networkSelectMessage.operatorAlphaLong = "";
        this.mCi.setNetworkSelectionModeAutomatic(obtainMessage(17, networkSelectMessage));
        updateSavedNetworkOperator(networkSelectMessage);
    }

    public void setOnEcbModeExitResponse(Handler handler, int i, Object obj) {
        logUnexpectedCdmaMethodCall("setOnEcbModeExitResponse");
    }

    public boolean setOperatorBrandOverride(String str) {
        return false;
    }

    public void setPreferredNetworkType(int i, Message message) {
        if (TelephonyManager.getDefault().getPhoneCount() > 1) {
            ModemBindingPolicyHandler.getInstance().setPreferredNetworkType(i, getPhoneId(), message);
        } else {
            this.mCi.setPreferredNetworkType(i, message);
        }
    }

    public void setPreferredNetworkTypeWithOptimizeSetting(int i, boolean z, Message message) {
        this.mCi.setRatModeOptimizeSetting(z, obtainMessage(EVENT_SET_RAT_MODE_OPTIMIZE_SETTING_COMPLETE, i, 0, message));
    }

    public void setProfilePdpType(int i, String str) {
    }

    public boolean setRoamingOverride(List<String> list, List<String> list2, List<String> list3, List<String> list4) {
        String iccSerialNumber = getIccSerialNumber();
        if (TextUtils.isEmpty(iccSerialNumber)) {
            return false;
        }
        setRoamingOverrideHelper(list, GSM_ROAMING_LIST_OVERRIDE_PREFIX, iccSerialNumber);
        setRoamingOverrideHelper(list2, GSM_NON_ROAMING_LIST_OVERRIDE_PREFIX, iccSerialNumber);
        setRoamingOverrideHelper(list3, CDMA_ROAMING_LIST_OVERRIDE_PREFIX, iccSerialNumber);
        setRoamingOverrideHelper(list4, CDMA_NON_ROAMING_LIST_OVERRIDE_PREFIX, iccSerialNumber);
        ServiceStateTracker serviceStateTracker = getServiceStateTracker();
        if (serviceStateTracker != null) {
            serviceStateTracker.pollState();
        }
        return true;
    }

    public void setSimImsi(String str) {
        Editor edit = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        edit.putString(SIM_IMSI + getSubId(), str);
        edit.apply();
    }

    public void setSmscAddress(String str, Message message) {
        this.mCi.setSmscAddress(str, message);
    }

    public void setSystemProperty(String str, String str2) {
        if (!getUnitTestMode()) {
            SystemProperties.set(str, str2);
        }
    }

    public void setTTYMode(int i, Message message) {
        this.mCi.setTTYMode(i, message);
    }

    public void setUiTTYMode(int i, Message message) {
        Rlog.d(LOG_TAG, "unexpected setUiTTYMode method call");
    }

    public void setUnitTestMode(boolean z) {
        this.mUnitTestMode = z;
    }

    public void setVoiceMessageCount(int i) {
        this.mVmCount = i;
        notifyMessageWaitingIndicator();
    }

    public void setVoiceMessageWaiting(int i, int i2) {
        Rlog.e(LOG_TAG, "Error! This function should never be executed, inactive Phone.");
    }

    public void shutdownRadio() {
        getServiceStateTracker().requestShutdown();
    }

    public void unregisterForCallWaiting(Handler handler) {
        logUnexpectedCdmaMethodCall("unregisterForCallWaiting");
    }

    public void unregisterForCdmaOtaStatusChange(Handler handler) {
        logUnexpectedCdmaMethodCall("unregisterForCdmaOtaStatusChange");
    }

    public void unregisterForDisconnect(Handler handler) {
        this.mDisconnectRegistrants.remove(handler);
    }

    public void unregisterForDisplayInfo(Handler handler) {
        this.mCi.unregisterForDisplayInfo(handler);
    }

    public void unregisterForEcmTimerReset(Handler handler) {
        logUnexpectedCdmaMethodCall("unregisterForEcmTimerReset");
    }

    public void unregisterForHandoverStateChanged(Handler handler) {
        this.mHandoverRegistrants.remove(handler);
    }

    public void unregisterForInCallVoicePrivacyOff(Handler handler) {
        this.mCi.unregisterForInCallVoicePrivacyOff(handler);
    }

    public void unregisterForInCallVoicePrivacyOn(Handler handler) {
        this.mCi.unregisterForInCallVoicePrivacyOn(handler);
    }

    public void unregisterForIncomingRing(Handler handler) {
        this.mIncomingRingRegistrants.remove(handler);
    }

    public void unregisterForLineControlInfo(Handler handler) {
        this.mCi.unregisterForLineControlInfo(handler);
    }

    public void unregisterForMmiComplete(Handler handler) {
        checkCorrectThread(handler);
        this.mMmiCompleteRegistrants.remove(handler);
    }

    public void unregisterForMmiInitiate(Handler handler) {
        this.mMmiRegistrants.remove(handler);
    }

    public void unregisterForNewRingingConnection(Handler handler) {
        this.mNewRingingConnectionRegistrants.remove(handler);
    }

    public void unregisterForNumberInfo(Handler handler) {
        this.mCi.unregisterForNumberInfo(handler);
    }

    public void unregisterForOnHoldTone(Handler handler) {
    }

    public void unregisterForPreciseCallStateChanged(Handler handler) {
        this.mPreciseCallStateRegistrants.remove(handler);
    }

    public void unregisterForRadioOffOrNotAvailable(Handler handler) {
        this.mRadioOffOrNotAvailableRegistrants.remove(handler);
    }

    public void unregisterForRedirectedNumberInfo(Handler handler) {
        this.mCi.unregisterForRedirectedNumberInfo(handler);
    }

    public void unregisterForResendIncallMute(Handler handler) {
        this.mCi.unregisterForResendIncallMute(handler);
    }

    public void unregisterForRingbackTone(Handler handler) {
        this.mCi.unregisterForRingbackTone(handler);
    }

    public void unregisterForServiceStateChanged(Handler handler) {
        this.mServiceStateRegistrants.remove(handler);
    }

    public void unregisterForSignalInfo(Handler handler) {
        this.mCi.unregisterForSignalInfo(handler);
    }

    public void unregisterForSimRecordsLoaded(Handler handler) {
        logUnexpectedCdmaMethodCall("unregisterForSimRecordsLoaded");
    }

    public void unregisterForSubscriptionInfoReady(Handler handler) {
        logUnexpectedCdmaMethodCall("unregisterForSubscriptionInfoReady");
    }

    public void unregisterForSuppServiceFailed(Handler handler) {
        this.mSuppServiceFailedRegistrants.remove(handler);
    }

    public void unregisterForT53AudioControlInfo(Handler handler) {
        this.mCi.unregisterForT53AudioControlInfo(handler);
    }

    public void unregisterForT53ClirInfo(Handler handler) {
        this.mCi.unregisterForT53ClirInfo(handler);
    }

    public void unregisterForTtyModeReceived(Handler handler) {
    }

    public void unregisterForUnknownConnection(Handler handler) {
        this.mUnknownConnectionRegistrants.remove(handler);
    }

    public void unregisterForVideoCapabilityChanged(Handler handler) {
        this.mVideoCapabilityChangedRegistrants.remove(handler);
    }

    public void unsetOnEcbModeExitResponse(Handler handler) {
        logUnexpectedCdmaMethodCall("unsetOnEcbModeExitResponse");
    }

    /* Access modifiers changed, original: protected */
    /* JADX WARNING: Missing block: B:34:?, code skipped:
            return;
     */
    public void updateImsPhone() {
        /*
        r5 = this;
        r1 = r5.mImsLock;
        monitor-enter(r1);
        r0 = "PhoneBase";
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0082 }
        r2.<init>();	 Catch:{ all -> 0x0082 }
        r3 = "updateImsPhone mImsServiceReady=";
        r2 = r2.append(r3);	 Catch:{ all -> 0x0082 }
        r3 = r5.mImsServiceReady;	 Catch:{ all -> 0x0082 }
        r2 = r2.append(r3);	 Catch:{ all -> 0x0082 }
        r2 = r2.toString();	 Catch:{ all -> 0x0082 }
        android.telephony.Rlog.d(r0, r2);	 Catch:{ all -> 0x0082 }
        r0 = r5.mImsServiceReady;	 Catch:{ all -> 0x0082 }
        if (r0 == 0) goto L_0x0085;
    L_0x0021:
        r0 = r5.mImsPhone;	 Catch:{ all -> 0x0082 }
        if (r0 != 0) goto L_0x0085;
    L_0x0025:
        r0 = com.android.internal.telephony.CallManager.getInstance();	 Catch:{ all -> 0x0082 }
        r0 = r0.getAllPhones();	 Catch:{ all -> 0x0082 }
        r2 = r0.iterator();	 Catch:{ all -> 0x0082 }
    L_0x0031:
        r0 = r2.hasNext();	 Catch:{ all -> 0x0082 }
        if (r0 == 0) goto L_0x0060;
    L_0x0037:
        r0 = r2.next();	 Catch:{ all -> 0x0082 }
        r0 = (com.android.internal.telephony.Phone) r0;	 Catch:{ all -> 0x0082 }
        if (r0 == 0) goto L_0x0031;
    L_0x003f:
        r3 = r0.getPhoneType();	 Catch:{ all -> 0x0082 }
        r4 = 5;
        if (r3 != r4) goto L_0x0031;
    L_0x0046:
        r2 = "PhoneBase";
        r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0082 }
        r3.<init>();	 Catch:{ all -> 0x0082 }
        r4 = "Found IMSPhone = ";
        r3 = r3.append(r4);	 Catch:{ all -> 0x0082 }
        r0 = r3.append(r0);	 Catch:{ all -> 0x0082 }
        r0 = r0.toString();	 Catch:{ all -> 0x0082 }
        android.telephony.Rlog.d(r2, r0);	 Catch:{ all -> 0x0082 }
        monitor-exit(r1);	 Catch:{ all -> 0x0082 }
    L_0x005f:
        return;
    L_0x0060:
        r0 = "PhoneBase";
        r2 = "Not found IMSPhone ~~~ make new";
        android.telephony.Rlog.d(r0, r2);	 Catch:{ all -> 0x0082 }
        r0 = r5.mNotifier;	 Catch:{ all -> 0x0082 }
        r0 = com.android.internal.telephony.PhoneFactory.makeImsPhone(r0, r5);	 Catch:{ all -> 0x0082 }
        r5.mImsPhone = r0;	 Catch:{ all -> 0x0082 }
        r0 = com.android.internal.telephony.CallManager.getInstance();	 Catch:{ all -> 0x0082 }
        r2 = r5.mImsPhone;	 Catch:{ all -> 0x0082 }
        r0.registerPhone(r2);	 Catch:{ all -> 0x0082 }
        r0 = r5.mImsPhone;	 Catch:{ all -> 0x0082 }
        r2 = 32;
        r3 = 0;
        r0.registerForSilentRedial(r5, r2, r3);	 Catch:{ all -> 0x0082 }
    L_0x0080:
        monitor-exit(r1);	 Catch:{ all -> 0x0082 }
        goto L_0x005f;
    L_0x0082:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0082 }
        throw r0;
    L_0x0085:
        r0 = r5.mImsServiceReady;	 Catch:{ all -> 0x0082 }
        if (r0 != 0) goto L_0x0080;
    L_0x0089:
        r0 = r5.mImsPhone;	 Catch:{ all -> 0x0082 }
        if (r0 == 0) goto L_0x0080;
    L_0x008d:
        r0 = com.android.internal.telephony.CallManager.getInstance();	 Catch:{ all -> 0x0082 }
        r2 = r5.mImsPhone;	 Catch:{ all -> 0x0082 }
        r0.unregisterPhone(r2);	 Catch:{ all -> 0x0082 }
        r0 = r5.mImsPhone;	 Catch:{ all -> 0x0082 }
        r0.unregisterForSilentRedial(r5);	 Catch:{ all -> 0x0082 }
        r0 = r5.mImsPhone;	 Catch:{ all -> 0x0082 }
        r0.dispose();	 Catch:{ all -> 0x0082 }
        r0 = 0;
        r5.mImsPhone = r0;	 Catch:{ all -> 0x0082 }
        goto L_0x0080;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.PhoneBase.updateImsPhone():void");
    }

    public void updatePhoneObject(int i) {
        Rlog.d(LOG_TAG, "updatePhoneObject: phoneid = " + this.mPhoneId + " rat = " + i);
        Phone phone = PhoneFactory.getPhone(this.mPhoneId);
        if (phone != null) {
            phone.updatePhoneObject(i);
        }
    }
}
