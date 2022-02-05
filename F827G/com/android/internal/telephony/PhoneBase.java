package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.provider.Settings;
import android.telecom.VideoProfile;
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
import com.android.internal.telephony.Call;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneConnection;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
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

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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

    @Override // com.android.internal.telephony.Phone
    public abstract int getPhoneType();

    @Override // com.android.internal.telephony.Phone
    public abstract PhoneConstants.State getState();

    protected abstract void onUpdateIccAvailability();

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class NetworkSelectMessage {
        public Message message;
        public String operatorAlphaLong;
        public String operatorNumeric;

        protected NetworkSelectMessage() {
        }
    }

    @Override // com.android.internal.telephony.Phone
    public String getPhoneName() {
        return this.mName;
    }

    @Override // com.android.internal.telephony.Phone
    public String getNai() {
        return null;
    }

    public String getActionDetached() {
        return this.mActionDetached;
    }

    public String getActionAttached() {
        return this.mActionAttached;
    }

    public void setSystemProperty(String property, String value) {
        if (!getUnitTestMode()) {
            SystemProperties.set(property, value);
        }
    }

    public String getSystemProperty(String property, String defValue) {
        if (getUnitTestMode()) {
            return null;
        }
        return SystemProperties.get(property, defValue);
    }

    protected PhoneBase(String name, PhoneNotifier notifier, Context context, CommandsInterface ci) {
        this(name, notifier, context, ci, false);
    }

    public PhoneBase(String name, PhoneNotifier notifier, Context context, CommandsInterface ci, boolean unitTestMode) {
        this(name, notifier, context, ci, unitTestMode, Integer.MAX_VALUE);
    }

    public PhoneBase(String name, PhoneNotifier notifier, Context context, CommandsInterface ci, boolean unitTestMode, int phoneId) {
        this.mImsIntentReceiver = new BroadcastReceiver() { // from class: com.android.internal.telephony.PhoneBase.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                Rlog.d(PhoneBase.LOG_TAG, "mImsIntentReceiver: action " + intent.getAction());
                if (intent.hasExtra("android:phone_id")) {
                    int extraPhoneId = intent.getIntExtra("android:phone_id", -1);
                    Rlog.d(PhoneBase.LOG_TAG, "mImsIntentReceiver: extraPhoneId = " + extraPhoneId);
                    if (extraPhoneId == -1 || extraPhoneId != PhoneBase.this.getPhoneId()) {
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
        this.mIccRecords = new AtomicReference<>();
        this.mUiccApplication = new AtomicReference<>();
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
        this.mPhoneId = phoneId;
        this.mName = name;
        this.mNotifier = notifier;
        this.mContext = context;
        this.mLooper = Looper.myLooper();
        this.mCi = ci;
        this.mActionDetached = getClass().getPackage().getName() + ".action_detached";
        this.mActionAttached = getClass().getPackage().getName() + ".action_attached";
        if (Build.IS_DEBUGGABLE) {
            this.mTelephonyTester = new TelephonyTester(this);
        }
        setUnitTestMode(unitTestMode);
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
                IntentFilter filter = new IntentFilter();
                filter.addAction("com.android.ims.IMS_SERVICE_UP");
                filter.addAction("com.android.ims.IMS_SERVICE_DOWN");
                this.mContext.registerReceiver(this.mImsIntentReceiver, filter);
                this.mCi.registerForSrvccStateChanged(this, 31, null);
            }
            this.mCi.setOnUnsolOemHookRaw(this, 34, null);
            Rlog.d(LOG_TAG, "mOosIsDisconnect=" + this.mOosIsDisconnect);
        }
    }

    @Override // com.android.internal.telephony.Phone
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

    @Override // com.android.internal.telephony.Phone
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

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 16:
            case 17:
                handleSetSelectNetwork((AsyncResult) msg.obj);
                return;
            default:
                if (!this.mIsTheCurrentActivePhone) {
                    Rlog.e(LOG_TAG, "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
                    return;
                }
                switch (msg.what) {
                    case 14:
                        Rlog.d(LOG_TAG, "Event EVENT_CALL_RING Received state=" + getState());
                        if (((AsyncResult) msg.obj).exception == null) {
                            PhoneConstants.State state = getState();
                            if (this.mDoesRilSendMultipleCallRing || !(state == PhoneConstants.State.RINGING || state == PhoneConstants.State.IDLE)) {
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
                        if (getState() == PhoneConstants.State.RINGING) {
                            sendIncomingCallRingNotification(msg.arg1);
                            return;
                        }
                        return;
                    case 30:
                        onUpdateIccAvailability();
                        return;
                    case 31:
                        AsyncResult ar = (AsyncResult) msg.obj;
                        if (ar.exception == null) {
                            handleSrvccStateChanged((int[]) ar.result);
                            return;
                        } else {
                            Rlog.e(LOG_TAG, "Srvcc exception: " + ar.exception);
                            return;
                        }
                    case 32:
                        Rlog.d(LOG_TAG, "Event EVENT_INITIATE_SILENT_REDIAL Received");
                        AsyncResult ar2 = (AsyncResult) msg.obj;
                        if (ar2.exception == null && ar2.result != null) {
                            String dialString = (String) ar2.result;
                            if (!TextUtils.isEmpty(dialString)) {
                                try {
                                    dialInternal(dialString, null, 0);
                                    return;
                                } catch (CallStateException e) {
                                    Rlog.e(LOG_TAG, "silent redial failed: " + e);
                                    return;
                                }
                            } else {
                                return;
                            }
                        } else {
                            return;
                        }
                    case 34:
                        AsyncResult ar3 = (AsyncResult) msg.obj;
                        if (ar3.exception == null) {
                            byte[] data = (byte[]) ar3.result;
                            Rlog.d(LOG_TAG, "EVENT_UNSOL_OEM_HOOK_RAW data=" + IccUtils.bytesToHexString(data));
                            this.mNotifier.notifyOemHookRawEventForSubscriber(getSubId(), data);
                            return;
                        }
                        Rlog.e(LOG_TAG, "OEM hook raw exception: " + ar3.exception);
                        return;
                    case EVENT_SET_RAT_MODE_OPTIMIZE_SETTING_COMPLETE /* 10000 */:
                        this.mCi.setPreferredNetworkType(msg.arg1, (Message) ((AsyncResult) msg.obj).userObj);
                        return;
                    default:
                        throw new RuntimeException("unexpected event not handled");
                }
        }
    }

    private void handleSrvccStateChanged(int[] ret) {
        Call.SrvccState srvccState;
        Rlog.d(LOG_TAG, "handleSrvccStateChanged");
        ArrayList<Connection> conn = null;
        ImsPhone imsPhone = this.mImsPhone;
        Call.SrvccState srvccState2 = Call.SrvccState.NONE;
        if (ret != null && ret.length != 0) {
            int state = ret[0];
            switch (state) {
                case 0:
                    srvccState = Call.SrvccState.STARTED;
                    if (imsPhone == null) {
                        Rlog.d(LOG_TAG, "HANDOVER_STARTED: mImsPhone null");
                        break;
                    } else {
                        conn = imsPhone.getHandoverConnection();
                        migrateFrom(imsPhone);
                        break;
                    }
                case 1:
                    srvccState = Call.SrvccState.COMPLETED;
                    if (imsPhone == null) {
                        Rlog.d(LOG_TAG, "HANDOVER_COMPLETED: mImsPhone null");
                        break;
                    } else {
                        imsPhone.notifySrvccState(srvccState);
                        break;
                    }
                case 2:
                case 3:
                    srvccState = Call.SrvccState.FAILED;
                    break;
                default:
                    return;
            }
            getCallTracker().notifySrvccState(srvccState, conn);
            notifyVoLteServiceStateChanged(new VoLteServiceState(state));
        }
    }

    @Override // com.android.internal.telephony.Phone
    public Context getContext() {
        return this.mContext;
    }

    protected void setCardInPhoneBook() {
    }

    @Override // com.android.internal.telephony.Phone
    public void disableDnsCheck(boolean b) {
        this.mDnsCheckDisabled = b;
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putBoolean(DNS_SERVER_CHECK_DISABLED_KEY, b);
        editor.apply();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isDnsCheckDisabled() {
        return this.mDnsCheckDisabled;
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForPreciseCallStateChanged(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mPreciseCallStateRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForPreciseCallStateChanged(Handler h) {
        this.mPreciseCallStateRegistrants.remove(h);
    }

    public void notifyPreciseCallStateChangedP() {
        this.mPreciseCallStateRegistrants.notifyRegistrants(new AsyncResult((Object) null, this, (Throwable) null));
        this.mNotifier.notifyPreciseCallState(this);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForHandoverStateChanged(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mHandoverRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForHandoverStateChanged(Handler h) {
        this.mHandoverRegistrants.remove(h);
    }

    public void notifyHandoverStateChanged(Connection cn) {
        this.mHandoverRegistrants.notifyRegistrants(new AsyncResult((Object) null, cn, (Throwable) null));
    }

    public void migrateFrom(PhoneBase from) {
        migrate(this.mHandoverRegistrants, from.mHandoverRegistrants);
        migrate(this.mPreciseCallStateRegistrants, from.mPreciseCallStateRegistrants);
        migrate(this.mNewRingingConnectionRegistrants, from.mNewRingingConnectionRegistrants);
        migrate(this.mIncomingRingRegistrants, from.mIncomingRingRegistrants);
        migrate(this.mDisconnectRegistrants, from.mDisconnectRegistrants);
        migrate(this.mServiceStateRegistrants, from.mServiceStateRegistrants);
        migrate(this.mMmiCompleteRegistrants, from.mMmiCompleteRegistrants);
        migrate(this.mMmiRegistrants, from.mMmiRegistrants);
        migrate(this.mUnknownConnectionRegistrants, from.mUnknownConnectionRegistrants);
        migrate(this.mSuppServiceFailedRegistrants, from.mSuppServiceFailedRegistrants);
    }

    public void migrate(RegistrantList to, RegistrantList from) {
        from.removeCleared();
        int n = from.size();
        for (int i = 0; i < n; i++) {
            Message msg = ((Registrant) from.get(i)).messageForRegistrant();
            if (msg == null) {
                Rlog.d(LOG_TAG, "msg is null");
            } else if (msg.obj != CallManager.getInstance().getRegistrantIdentifier()) {
                to.add((Registrant) from.get(i));
            }
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForUnknownConnection(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mUnknownConnectionRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForUnknownConnection(Handler h) {
        this.mUnknownConnectionRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForNewRingingConnection(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mNewRingingConnectionRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForNewRingingConnection(Handler h) {
        this.mNewRingingConnectionRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForVideoCapabilityChanged(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mVideoCapabilityChangedRegistrants.addUnique(h, what, obj);
        notifyForVideoCapabilityChanged(this.mIsVideoCapable);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForVideoCapabilityChanged(Handler h) {
        this.mVideoCapabilityChangedRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj) {
        this.mCi.registerForInCallVoicePrivacyOn(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForInCallVoicePrivacyOn(Handler h) {
        this.mCi.unregisterForInCallVoicePrivacyOn(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj) {
        this.mCi.registerForInCallVoicePrivacyOff(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForInCallVoicePrivacyOff(Handler h) {
        this.mCi.unregisterForInCallVoicePrivacyOff(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForIncomingRing(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mIncomingRingRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForIncomingRing(Handler h) {
        this.mIncomingRingRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForDisconnect(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mDisconnectRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForDisconnect(Handler h) {
        this.mDisconnectRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSuppServiceFailed(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mSuppServiceFailedRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSuppServiceFailed(Handler h) {
        this.mSuppServiceFailedRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForMmiInitiate(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mMmiRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForMmiInitiate(Handler h) {
        this.mMmiRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForMmiComplete(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mMmiCompleteRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForMmiComplete(Handler h) {
        checkCorrectThread(h);
        this.mMmiCompleteRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSimRecordsLoaded(Handler h, int what, Object obj) {
        logUnexpectedCdmaMethodCall("registerForSimRecordsLoaded");
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSimRecordsLoaded(Handler h) {
        logUnexpectedCdmaMethodCall("unregisterForSimRecordsLoaded");
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForTtyModeReceived(Handler h, int what, Object obj) {
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForTtyModeReceived(Handler h) {
    }

    @Override // com.android.internal.telephony.Phone
    public void setNetworkSelectionModeAutomatic(Message response) {
        NetworkSelectMessage nsm = new NetworkSelectMessage();
        nsm.message = response;
        nsm.operatorNumeric = "";
        nsm.operatorAlphaLong = "";
        this.mCi.setNetworkSelectionModeAutomatic(obtainMessage(17, nsm));
        updateSavedNetworkOperator(nsm);
    }

    @Override // com.android.internal.telephony.Phone
    public void getNetworkSelectionMode(Message message) {
        this.mCi.getNetworkSelectionMode(message);
    }

    @Override // com.android.internal.telephony.Phone
    public void selectNetworkManually(OperatorInfo network, Message response) {
        NetworkSelectMessage nsm = new NetworkSelectMessage();
        nsm.message = response;
        nsm.operatorNumeric = network.getOperatorNumeric();
        nsm.operatorAlphaLong = network.getOperatorAlphaLong();
        Message msg = obtainMessage(16, nsm);
        if (network.getRadioTech().equals("")) {
            this.mCi.setNetworkSelectionModeManual(network.getOperatorNumeric(), msg);
        } else {
            this.mCi.setNetworkSelectionModeManual(network.getOperatorNumeric() + "+" + network.getRadioTech(), msg);
        }
        updateSavedNetworkOperator(nsm);
    }

    private void updateSavedNetworkOperator(NetworkSelectMessage nsm) {
        int subId = getSubId();
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
            editor.putString(NETWORK_SELECTION_KEY + subId, nsm.operatorNumeric);
            editor.putString(NETWORK_SELECTION_NAME_KEY + subId, nsm.operatorAlphaLong);
            if (!editor.commit()) {
                Rlog.e(LOG_TAG, "failed to commit network selection preference");
                return;
            }
            return;
        }
        Rlog.e(LOG_TAG, "Cannot update network selection preference due to invalid subId " + subId);
    }

    private void handleSetSelectNetwork(AsyncResult ar) {
        if (!(ar.userObj instanceof NetworkSelectMessage)) {
            Rlog.e(LOG_TAG, "unexpected result from user object.");
            return;
        }
        NetworkSelectMessage nsm = (NetworkSelectMessage) ar.userObj;
        if (nsm.message != null) {
            AsyncResult.forMessage(nsm.message, ar.result, ar.exception);
            nsm.message.sendToTarget();
        }
    }

    private String getSavedNetworkSelection() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(NETWORK_SELECTION_KEY + getSubId(), "");
    }

    public void restoreSavedNetworkSelection(Message response) {
        String networkSelection = getSavedNetworkSelection();
        if (TextUtils.isEmpty(networkSelection)) {
            this.mCi.setNetworkSelectionModeAutomatic(response);
        } else {
            this.mCi.setNetworkSelectionModeManual(networkSelection, response);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void setUnitTestMode(boolean f) {
        this.mUnitTestMode = f;
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getUnitTestMode() {
        return this.mUnitTestMode;
    }

    public void notifyDisconnectP(Connection cn) {
        this.mDisconnectRegistrants.notifyRegistrants(new AsyncResult((Object) null, cn, (Throwable) null));
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForServiceStateChanged(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        this.mServiceStateRegistrants.add(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForServiceStateChanged(Handler h) {
        this.mServiceStateRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForRingbackTone(Handler h, int what, Object obj) {
        this.mCi.registerForRingbackTone(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForRingbackTone(Handler h) {
        this.mCi.unregisterForRingbackTone(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForOnHoldTone(Handler h, int what, Object obj) {
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForOnHoldTone(Handler h) {
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForResendIncallMute(Handler h, int what, Object obj) {
        this.mCi.registerForResendIncallMute(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForResendIncallMute(Handler h) {
        this.mCi.unregisterForResendIncallMute(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void setEchoSuppressionEnabled() {
    }

    public void notifyServiceStateChangedP(ServiceState ss) {
        this.mServiceStateRegistrants.notifyRegistrants(new AsyncResult((Object) null, ss, (Throwable) null));
        this.mNotifier.notifyServiceState(this);
    }

    @Override // com.android.internal.telephony.Phone
    public SimulatedRadioControl getSimulatedRadioControl() {
        return this.mSimulatedRadioControl;
    }

    private void checkCorrectThread(Handler h) {
        if (h.getLooper() != this.mLooper) {
            throw new RuntimeException("com.android.internal.telephony.Phone must be used from within one thread");
        }
    }

    private void setPropertiesByCarrier() {
        String carrier = SystemProperties.get("ro.carrier");
        if (!(carrier == null || carrier.length() == 0 || "unknown".equals(carrier))) {
            CharSequence[] carrierLocales = this.mContext.getResources().getTextArray(17236042);
            for (int i = 0; i < carrierLocales.length; i += 3) {
                if (carrier.equals(carrierLocales[i].toString())) {
                    Locale l = Locale.forLanguageTag(carrierLocales[i + 1].toString().replace('_', '-'));
                    String country = l.getCountry();
                    MccTable.setSystemLocale(this.mContext, l.getLanguage(), country);
                    if (!country.isEmpty()) {
                        try {
                            Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_country_code");
                            return;
                        } catch (Settings.SettingNotFoundException e) {
                            ((WifiManager) this.mContext.getSystemService("wifi")).setCountryCode(country, false);
                            return;
                        }
                    } else {
                        return;
                    }
                }
            }
        }
    }

    public IccFileHandler getIccFileHandler() {
        IccFileHandler fh;
        UiccCardApplication uiccApplication = this.mUiccApplication.get();
        if (uiccApplication == null) {
            Rlog.d(LOG_TAG, "getIccFileHandler: uiccApplication == null, return null");
            fh = null;
        } else {
            fh = uiccApplication.getIccFileHandler();
        }
        Rlog.d(LOG_TAG, "getIccFileHandler: fh=" + fh);
        return fh;
    }

    public IccRecords getIccRecords() {
        UiccCardApplication uiccApplication = this.mUiccApplication.get();
        if (uiccApplication == null) {
            return null;
        }
        return uiccApplication.getIccRecords();
    }

    public Handler getHandler() {
        return this;
    }

    @Override // com.android.internal.telephony.Phone
    public void updatePhoneObject(int voiceRadioTech) {
        Rlog.d(LOG_TAG, "updatePhoneObject: phoneid = " + this.mPhoneId + " rat = " + voiceRadioTech);
        Phone phoneProxy = PhoneFactory.getPhone(this.mPhoneId);
        if (phoneProxy != null) {
            phoneProxy.updatePhoneObject(voiceRadioTech);
        }
    }

    public ServiceStateTracker getServiceStateTracker() {
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public ServiceState getBaseServiceState() {
        return getServiceState();
    }

    public CallTracker getCallTracker() {
        return null;
    }

    public IccCardApplicationStatus.AppType getCurrentUiccAppType() {
        UiccCardApplication currentApp = this.mUiccApplication.get();
        return currentApp != null ? currentApp.getType() : IccCardApplicationStatus.AppType.APPTYPE_UNKNOWN;
    }

    @Override // com.android.internal.telephony.Phone
    public IccCard getIccCard() {
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public String getIccSerialNumber() {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            return r.getIccId();
        }
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getIccRecordsLoaded() {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            return r.getRecordsLoaded();
        }
        return false;
    }

    @Override // com.android.internal.telephony.Phone
    public List<CellInfo> getAllCellInfo() {
        return privatizeCellInfoList(getServiceStateTracker().getAllCellInfo());
    }

    private List<CellInfo> privatizeCellInfoList(List<CellInfo> cellInfoList) {
        if (Settings.Secure.getInt(getContext().getContentResolver(), "location_mode", 0) != 0) {
            return cellInfoList;
        }
        ArrayList<CellInfo> privateCellInfoList = new ArrayList<>(cellInfoList.size());
        for (CellInfo c : cellInfoList) {
            if (c instanceof CellInfoCdma) {
                CellInfoCdma cellInfoCdma = (CellInfoCdma) c;
                CellIdentityCdma cellIdentity = cellInfoCdma.getCellIdentity();
                CellIdentityCdma maskedCellIdentity = new CellIdentityCdma(cellIdentity.getNetworkId(), cellIdentity.getSystemId(), cellIdentity.getBasestationId(), Integer.MAX_VALUE, Integer.MAX_VALUE);
                CellInfoCdma privateCellInfoCdma = new CellInfoCdma(cellInfoCdma);
                privateCellInfoCdma.setCellIdentity(maskedCellIdentity);
                privateCellInfoList.add(privateCellInfoCdma);
            } else {
                privateCellInfoList.add(c);
            }
        }
        return privateCellInfoList;
    }

    @Override // com.android.internal.telephony.Phone
    public void setCellInfoListRate(int rateInMillis) {
        this.mCi.setCellInfoListRate(rateInMillis, null);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getMessageWaitingIndicator() {
        return this.mVmCount != 0;
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getCallForwardingIndicator() {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            return r.getVoiceCallForwardingFlag();
        }
        return false;
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallForwardingUncondTimerOption(int startHour, int startMinute, int endHour, int endMinute, int commandInterfaceCFReason, int commandInterfaceCFAction, String dialingNumber, Message onComplete) {
        Rlog.e(LOG_TAG, "setCallForwardingUncondTimerOption error ");
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallForwardingUncondTimerOption(int commandInterfaceCFReason, Message onComplete) {
        Rlog.e(LOG_TAG, "getCallForwardingUncondTimerOption error ");
    }

    public void setCallForwardingPreference(boolean enabled) {
        Rlog.d(LOG_TAG, "Set callforwarding info to perferences");
        SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
        edit.putBoolean(CF_ENABLED + getSubId(), enabled);
        edit.commit();
        setSimImsi(getSubscriberId());
    }

    public boolean getCallForwardingPreference() {
        Rlog.d(LOG_TAG, "Get callforwarding info from perferences");
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            if (!sp.contains(CF_ENABLED + getSubId()) && sp.contains(CF_ENABLED + this.mPhoneId)) {
                setCallForwardingPreference(sp.getBoolean(CF_ENABLED + this.mPhoneId, false));
                SharedPreferences.Editor edit = sp.edit();
                edit.remove(CF_ENABLED + this.mPhoneId);
                edit.commit();
            }
        } else if (!sp.contains(CF_ENABLED + getSubId()) && sp.contains(CF_ENABLED)) {
            setCallForwardingPreference(sp.getBoolean(CF_ENABLED, false));
            SharedPreferences.Editor edit2 = sp.edit();
            edit2.remove(CF_ENABLED);
            edit2.commit();
        }
        return sp.getBoolean(CF_ENABLED + getSubId(), false);
    }

    public String getSimImsi() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            if (!sp.contains(SIM_IMSI + getSubId()) && sp.contains(VM_SIM_IMSI + this.mPhoneId)) {
                setSimImsi(sp.getString(VM_SIM_IMSI + this.mPhoneId, null));
                SharedPreferences.Editor editor = sp.edit();
                editor.remove(VM_SIM_IMSI + this.mPhoneId);
                editor.commit();
            }
        } else if (!sp.contains(SIM_IMSI + getSubId()) && sp.contains(VM_SIM_IMSI)) {
            setSimImsi(sp.getString(VM_SIM_IMSI, null));
            SharedPreferences.Editor editor2 = sp.edit();
            editor2.remove(VM_SIM_IMSI);
            editor2.commit();
        }
        return sp.getString(SIM_IMSI + getSubId(), null);
    }

    public void setSimImsi(String imsi) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putString(SIM_IMSI + getSubId(), imsi);
        editor.apply();
    }

    @Override // com.android.internal.telephony.Phone
    public void queryCdmaRoamingPreference(Message response) {
        this.mCi.queryCdmaRoamingPreference(response);
    }

    @Override // com.android.internal.telephony.Phone
    public SignalStrength getSignalStrength() {
        ServiceStateTracker sst = getServiceStateTracker();
        return sst == null ? new SignalStrength() : sst.getSignalStrength();
    }

    @Override // com.android.internal.telephony.Phone
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        this.mCi.setCdmaRoamingPreference(cdmaRoamingType, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCdmaSubscription(int cdmaSubscriptionType, Message response) {
        this.mCi.setCdmaSubscriptionSource(cdmaSubscriptionType, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setPreferredNetworkType(int networkType, Message response) {
        if (TelephonyManager.getDefault().getPhoneCount() > 1) {
            ModemBindingPolicyHandler.getInstance().setPreferredNetworkType(networkType, getPhoneId(), response);
        } else {
            this.mCi.setPreferredNetworkType(networkType, response);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void getPreferredNetworkType(Message response) {
        this.mCi.getPreferredNetworkType(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void getSmscAddress(Message result) {
        this.mCi.getSmscAddress(result);
    }

    @Override // com.android.internal.telephony.Phone
    public void setSmscAddress(String address, Message result) {
        this.mCi.setSmscAddress(address, result);
    }

    @Override // com.android.internal.telephony.Phone
    public void setTTYMode(int ttyMode, Message onComplete) {
        this.mCi.setTTYMode(ttyMode, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setUiTTYMode(int uiTtyMode, Message onComplete) {
        Rlog.d(LOG_TAG, "unexpected setUiTTYMode method call");
    }

    @Override // com.android.internal.telephony.Phone
    public void queryTTYMode(Message onComplete) {
        this.mCi.queryTTYMode(onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void enableEnhancedVoicePrivacy(boolean enable, Message onComplete) {
        logUnexpectedCdmaMethodCall("enableEnhancedVoicePrivacy");
    }

    @Override // com.android.internal.telephony.Phone
    public void getEnhancedVoicePrivacy(Message onComplete) {
        logUnexpectedCdmaMethodCall("getEnhancedVoicePrivacy");
    }

    @Override // com.android.internal.telephony.Phone
    public void setBandMode(int bandMode, Message response) {
        this.mCi.setBandMode(bandMode, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void queryAvailableBandMode(Message response) {
        this.mCi.queryAvailableBandMode(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        this.mCi.invokeOemRilRequestRaw(data, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        this.mCi.invokeOemRilRequestStrings(strings, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void nvReadItem(int itemID, Message response) {
        this.mCi.nvReadItem(itemID, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void nvWriteItem(int itemID, String itemValue, Message response) {
        this.mCi.nvWriteItem(itemID, itemValue, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response) {
        this.mCi.nvWriteCdmaPrl(preferredRoamingList, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void nvResetConfig(int resetType, Message response) {
        this.mCi.nvResetConfig(resetType, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void notifyDataActivity() {
        this.mNotifier.notifyDataActivity(this);
    }

    public void notifyMessageWaitingIndicator() {
        if (this.mIsVoiceCapable) {
            this.mNotifier.notifyMessageWaitingChanged(this);
        }
    }

    public void notifyDataConnection(String reason, String apnType, PhoneConstants.DataState state) {
        this.mNotifier.notifyDataConnection(this, reason, apnType, state);
    }

    public void notifyDataConnection(String reason, String apnType) {
        this.mNotifier.notifyDataConnection(this, reason, apnType, getDataConnectionState(apnType));
    }

    public void notifyDataConnection(String reason) {
        String[] types = getActiveApnTypes();
        for (String apnType : types) {
            this.mNotifier.notifyDataConnection(this, reason, apnType, getDataConnectionState(apnType));
        }
    }

    public void notifyOtaspChanged(int otaspMode) {
        this.mNotifier.notifyOtaspChanged(this, otaspMode);
    }

    public void notifySignalStrength() {
        this.mNotifier.notifySignalStrength(this);
    }

    public void notifyCellInfo(List<CellInfo> cellInfo) {
        this.mNotifier.notifyCellInfo(this, privatizeCellInfoList(cellInfo));
    }

    public void notifyDataConnectionRealTimeInfo(DataConnectionRealTimeInfo dcRtInfo) {
        this.mNotifier.notifyDataConnectionRealTimeInfo(this, dcRtInfo);
    }

    public void notifyVoLteServiceStateChanged(VoLteServiceState lteState) {
        this.mNotifier.notifyVoLteServiceStateChanged(this, lteState);
    }

    public boolean isInEmergencyCall() {
        return false;
    }

    public boolean isInEcm() {
        return false;
    }

    public static int getVideoState(Call call) {
        ImsPhoneConnection conn = (ImsPhoneConnection) call.getEarliestConnection();
        if (conn != null) {
            return conn.getVideoState();
        }
        return 0;
    }

    private boolean isImsVideoCall(Call call) {
        return VideoProfile.VideoState.isVideo(getVideoState(call));
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isImsVtCallPresent() {
        boolean isVideoCallActive = false;
        if (this.mImsPhone != null) {
            isVideoCallActive = isImsVideoCall(this.mImsPhone.getForegroundCall()) || isImsVideoCall(this.mImsPhone.getBackgroundCall()) || isImsVideoCall(this.mImsPhone.getRingingCall());
        }
        Rlog.d(LOG_TAG, "isVideoCallActive: " + isVideoCallActive);
        return isVideoCallActive;
    }

    @Override // com.android.internal.telephony.Phone
    public int getVoiceMessageCount() {
        return this.mVmCount;
    }

    public void setVoiceMessageCount(int countWaiting) {
        this.mVmCount = countWaiting;
        notifyMessageWaitingIndicator();
    }

    @Override // com.android.internal.telephony.Phone
    public int getCdmaEriIconIndex() {
        logUnexpectedCdmaMethodCall("getCdmaEriIconIndex");
        return -1;
    }

    @Override // com.android.internal.telephony.Phone
    public int getCdmaEriIconMode() {
        logUnexpectedCdmaMethodCall("getCdmaEriIconMode");
        return -1;
    }

    @Override // com.android.internal.telephony.Phone
    public String getCdmaEriText() {
        logUnexpectedCdmaMethodCall("getCdmaEriText");
        return "GSM nw, no ERI";
    }

    @Override // com.android.internal.telephony.Phone
    public String getCdmaMin() {
        logUnexpectedCdmaMethodCall("getCdmaMin");
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isMinInfoReady() {
        logUnexpectedCdmaMethodCall("isMinInfoReady");
        return false;
    }

    @Override // com.android.internal.telephony.Phone
    public String getCdmaPrlVersion() {
        logUnexpectedCdmaMethodCall("getCdmaPrlVersion");
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public void sendBurstDtmf(String dtmfString, int on, int off, Message onComplete) {
        logUnexpectedCdmaMethodCall("sendBurstDtmf");
    }

    @Override // com.android.internal.telephony.Phone
    public void exitEmergencyCallbackMode() {
        logUnexpectedCdmaMethodCall("exitEmergencyCallbackMode");
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForCdmaOtaStatusChange(Handler h, int what, Object obj) {
        logUnexpectedCdmaMethodCall("registerForCdmaOtaStatusChange");
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForCdmaOtaStatusChange(Handler h) {
        logUnexpectedCdmaMethodCall("unregisterForCdmaOtaStatusChange");
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        logUnexpectedCdmaMethodCall("registerForSubscriptionInfoReady");
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSubscriptionInfoReady(Handler h) {
        logUnexpectedCdmaMethodCall("unregisterForSubscriptionInfoReady");
    }

    @Override // com.android.internal.telephony.Phone
    public boolean needsOtaServiceProvisioning() {
        return false;
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isOtaSpNumber(String dialStr) {
        return false;
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForCallWaiting(Handler h, int what, Object obj) {
        logUnexpectedCdmaMethodCall("registerForCallWaiting");
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForCallWaiting(Handler h) {
        logUnexpectedCdmaMethodCall("unregisterForCallWaiting");
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        logUnexpectedCdmaMethodCall("registerForEcmTimerReset");
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForEcmTimerReset(Handler h) {
        logUnexpectedCdmaMethodCall("unregisterForEcmTimerReset");
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSignalInfo(Handler h, int what, Object obj) {
        this.mCi.registerForSignalInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSignalInfo(Handler h) {
        this.mCi.unregisterForSignalInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForDisplayInfo(Handler h, int what, Object obj) {
        this.mCi.registerForDisplayInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForDisplayInfo(Handler h) {
        this.mCi.unregisterForDisplayInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForNumberInfo(Handler h, int what, Object obj) {
        this.mCi.registerForNumberInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForNumberInfo(Handler h) {
        this.mCi.unregisterForNumberInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForRedirectedNumberInfo(Handler h, int what, Object obj) {
        this.mCi.registerForRedirectedNumberInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForRedirectedNumberInfo(Handler h) {
        this.mCi.unregisterForRedirectedNumberInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForLineControlInfo(Handler h, int what, Object obj) {
        this.mCi.registerForLineControlInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForLineControlInfo(Handler h) {
        this.mCi.unregisterForLineControlInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerFoT53ClirlInfo(Handler h, int what, Object obj) {
        this.mCi.registerFoT53ClirlInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForT53ClirInfo(Handler h) {
        this.mCi.unregisterForT53ClirInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForT53AudioControlInfo(Handler h, int what, Object obj) {
        this.mCi.registerForT53AudioControlInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForT53AudioControlInfo(Handler h) {
        this.mCi.unregisterForT53AudioControlInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void setOnEcbModeExitResponse(Handler h, int what, Object obj) {
        logUnexpectedCdmaMethodCall("setOnEcbModeExitResponse");
    }

    @Override // com.android.internal.telephony.Phone
    public void unsetOnEcbModeExitResponse(Handler h) {
        logUnexpectedCdmaMethodCall("unsetOnEcbModeExitResponse");
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForRadioOffOrNotAvailable(Handler h, int what, Object obj) {
        this.mRadioOffOrNotAvailableRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForRadioOffOrNotAvailable(Handler h) {
        this.mRadioOffOrNotAvailableRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.Phone
    public String[] getActiveApnTypes() {
        return this.mDcTracker.getActiveApnTypes();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean hasMatchedTetherApnSetting() {
        return this.mDcTracker.hasMatchedTetherApnSetting();
    }

    @Override // com.android.internal.telephony.Phone
    public String getActiveApnHost(String apnType) {
        return this.mDcTracker.getActiveApnString(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public LinkProperties getLinkProperties(String apnType) {
        return this.mDcTracker.getLinkProperties(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public NetworkCapabilities getNetworkCapabilities(String apnType) {
        return this.mDcTracker.getNetworkCapabilities(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isDataConnectivityPossible() {
        return isDataConnectivityPossible("default");
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isOnDemandDataPossible(String apnType) {
        return this.mDcTracker != null && this.mDcTracker.isOnDemandDataPossible(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isDataConnectivityPossible(String apnType) {
        return this.mDcTracker != null && this.mDcTracker.isDataPossible(apnType);
    }

    public void notifyNewRingingConnectionP(Connection cn) {
        if (this.mIsVoiceCapable) {
            this.mNewRingingConnectionRegistrants.notifyRegistrants(new AsyncResult((Object) null, cn, (Throwable) null));
            if (hasMessages(15)) {
                return;
            }
            if ((getForegroundCall().isIdle() || getForegroundCall().isDialingOrAlerting()) && getBackgroundCall().isIdle()) {
                Rlog.d(LOG_TAG, "notifyNewRingingConnectionP(): send EVENT_CALL_RING_CONTINUE");
                sendMessageDelayed(obtainMessage(15, this.mCallRingContinueToken, 0), this.mCallRingDelay);
            }
        }
    }

    public void notifyUnknownConnectionP(Connection cn) {
        this.mUnknownConnectionRegistrants.notifyResult(cn);
    }

    public void notifyForVideoCapabilityChanged(boolean isVideoCallCapable) {
        this.mIsVideoCapable = isVideoCallCapable;
        this.mVideoCapabilityChangedRegistrants.notifyRegistrants(new AsyncResult((Object) null, Boolean.valueOf(isVideoCallCapable), (Throwable) null));
    }

    private void notifyIncomingRing() {
        if (this.mIsVoiceCapable) {
            this.mIncomingRingRegistrants.notifyRegistrants(new AsyncResult((Object) null, this, (Throwable) null));
        }
    }

    private void sendIncomingCallRingNotification(int token) {
        if (!this.mIsVoiceCapable || this.mDoesRilSendMultipleCallRing || token != this.mCallRingContinueToken) {
            Rlog.d(LOG_TAG, "Ignoring ring notification request, mDoesRilSendMultipleCallRing=" + this.mDoesRilSendMultipleCallRing + " token=" + token + " mCallRingContinueToken=" + this.mCallRingContinueToken + " mIsVoiceCapable=" + this.mIsVoiceCapable);
            return;
        }
        Rlog.d(LOG_TAG, "Sending notifyIncomingRing");
        notifyIncomingRing();
        sendMessageDelayed(obtainMessage(15, token, 0), this.mCallRingDelay);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isManualNetSelAllowed() {
        return false;
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isCspPlmnEnabled() {
        logUnexpectedGsmMethodCall("isCspPlmnEnabled");
        return false;
    }

    @Override // com.android.internal.telephony.Phone
    public IsimRecords getIsimRecords() {
        Rlog.e(LOG_TAG, "getIsimRecords() is only supported on LTE devices");
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public String getMsisdn() {
        logUnexpectedGsmMethodCall("getMsisdn");
        return null;
    }

    private static void logUnexpectedCdmaMethodCall(String name) {
        Rlog.e(LOG_TAG, "Error! " + name + "() in PhoneBase should not be called, CDMAPhone inactive.");
    }

    @Override // com.android.internal.telephony.Phone
    public PhoneConstants.DataState getDataConnectionState() {
        return getDataConnectionState("default");
    }

    private static void logUnexpectedGsmMethodCall(String name) {
        Rlog.e(LOG_TAG, "Error! " + name + "() in PhoneBase should not be called, GSMPhone inactive.");
    }

    public void notifyCallForwardingIndicator() {
        Rlog.e(LOG_TAG, "Error! This function should never be executed, inactive CDMAPhone.");
    }

    public void notifyDataConnectionFailed(String reason, String apnType) {
        this.mNotifier.notifyDataConnectionFailed(this, reason, apnType);
    }

    public void notifyPreciseDataConnectionFailed(String reason, String apnType, String apn, String failCause) {
        this.mNotifier.notifyPreciseDataConnectionFailed(this, reason, apnType, apn, failCause);
    }

    @Override // com.android.internal.telephony.Phone
    public int getLteOnCdmaMode() {
        return this.mCi.getLteOnCdmaMode();
    }

    @Override // com.android.internal.telephony.Phone
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        Rlog.e(LOG_TAG, "Error! This function should never be executed, inactive Phone.");
    }

    @Override // com.android.internal.telephony.Phone
    public UsimServiceTable getUsimServiceTable() {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            return r.getUsimServiceTable();
        }
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public UiccCard getUiccCard() {
        return this.mUiccController.getUiccCard(this.mPhoneId);
    }

    @Override // com.android.internal.telephony.Phone
    public String[] getPcscfAddress(String apnType) {
        return this.mDcTracker.getPcscfAddress(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public void setImsRegistrationState(boolean registered) {
        this.mDcTracker.setImsRegistrationState(registered);
    }

    @Override // com.android.internal.telephony.Phone
    public Phone getImsPhone() {
        return this.mImsPhone;
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isUtEnabled() {
        return false;
    }

    @Override // com.android.internal.telephony.Phone
    public ImsPhone relinquishOwnershipOfImsPhone() {
        ImsPhone imsPhone = null;
        synchronized (this.mImsLock) {
            if (this.mImsPhone != null) {
                imsPhone = this.mImsPhone;
                this.mImsPhone = null;
                CallManager.getInstance().unregisterPhone(imsPhone);
                imsPhone.unregisterForSilentRedial(this);
            }
        }
        return imsPhone;
    }

    @Override // com.android.internal.telephony.Phone
    public void acquireOwnershipOfImsPhone(ImsPhone imsPhone) {
        synchronized (this.mImsLock) {
            if (imsPhone != null) {
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
    }

    protected void updateImsPhone() {
        synchronized (this.mImsLock) {
            Rlog.d(LOG_TAG, "updateImsPhone mImsServiceReady=" + this.mImsServiceReady);
            if (this.mImsServiceReady && this.mImsPhone == null) {
                for (Phone phone : CallManager.getInstance().getAllPhones()) {
                    if (phone != null && phone.getPhoneType() == 5) {
                        Rlog.d(LOG_TAG, "Found IMSPhone = " + phone);
                        return;
                    }
                }
                Rlog.d(LOG_TAG, "Not found IMSPhone ~~~ make new");
                this.mImsPhone = PhoneFactory.makeImsPhone(this.mNotifier, this);
                CallManager.getInstance().registerPhone(this.mImsPhone);
                this.mImsPhone.registerForSilentRedial(this, 32, null);
            } else if (!this.mImsServiceReady && this.mImsPhone != null) {
                CallManager.getInstance().unregisterPhone(this.mImsPhone);
                this.mImsPhone.unregisterForSilentRedial(this);
                this.mImsPhone.dispose();
                this.mImsPhone = null;
            }
        }
    }

    protected Connection dialInternal(String dialString, UUSInfo uusInfo, int videoState) throws CallStateException {
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public int getSubId() {
        return SubscriptionController.getInstance().getSubIdUsingPhoneId(this.mPhoneId);
    }

    @Override // com.android.internal.telephony.Phone
    public int getPhoneId() {
        return this.mPhoneId;
    }

    @Override // com.android.internal.telephony.Phone
    public int getVoicePhoneServiceState() {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || imsPhone.getServiceState().getState() != 0) {
            return getServiceState().getState();
        }
        return 0;
    }

    @Override // com.android.internal.telephony.Phone
    public boolean setOperatorBrandOverride(String brand) {
        return false;
    }

    @Override // com.android.internal.telephony.Phone
    public boolean setRoamingOverride(List<String> gsmRoamingList, List<String> gsmNonRoamingList, List<String> cdmaRoamingList, List<String> cdmaNonRoamingList) {
        String iccId = getIccSerialNumber();
        if (TextUtils.isEmpty(iccId)) {
            return false;
        }
        setRoamingOverrideHelper(gsmRoamingList, GSM_ROAMING_LIST_OVERRIDE_PREFIX, iccId);
        setRoamingOverrideHelper(gsmNonRoamingList, GSM_NON_ROAMING_LIST_OVERRIDE_PREFIX, iccId);
        setRoamingOverrideHelper(cdmaRoamingList, CDMA_ROAMING_LIST_OVERRIDE_PREFIX, iccId);
        setRoamingOverrideHelper(cdmaNonRoamingList, CDMA_NON_ROAMING_LIST_OVERRIDE_PREFIX, iccId);
        ServiceStateTracker tracker = getServiceStateTracker();
        if (tracker != null) {
            tracker.pollState();
        }
        return true;
    }

    private void setRoamingOverrideHelper(List<String> list, String prefix, String iccId) {
        SharedPreferences.Editor spEditor = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
        String key = prefix + iccId;
        if (list == null || list.isEmpty()) {
            spEditor.remove(key).commit();
        } else {
            spEditor.putStringSet(key, new HashSet(list)).commit();
        }
    }

    public boolean isMccMncMarkedAsRoaming(String mccMnc) {
        return getRoamingOverrideHelper(GSM_ROAMING_LIST_OVERRIDE_PREFIX, mccMnc);
    }

    public boolean isMccMncMarkedAsNonRoaming(String mccMnc) {
        return getRoamingOverrideHelper(GSM_NON_ROAMING_LIST_OVERRIDE_PREFIX, mccMnc);
    }

    public boolean isSidMarkedAsRoaming(int SID) {
        return getRoamingOverrideHelper(CDMA_ROAMING_LIST_OVERRIDE_PREFIX, Integer.toString(SID));
    }

    public boolean isSidMarkedAsNonRoaming(int SID) {
        return getRoamingOverrideHelper(CDMA_NON_ROAMING_LIST_OVERRIDE_PREFIX, Integer.toString(SID));
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isImsRegistered() {
        ImsPhone imsPhone = this.mImsPhone;
        boolean isImsRegistered = false;
        if (imsPhone != null) {
            isImsRegistered = imsPhone.isImsRegistered();
        } else {
            ServiceStateTracker sst = getServiceStateTracker();
            if (sst != null) {
                isImsRegistered = sst.isImsRegistered();
            }
        }
        Rlog.d(LOG_TAG, "isImsRegistered =" + isImsRegistered);
        return isImsRegistered;
    }

    private boolean getRoamingOverrideHelper(String prefix, String key) {
        Set<String> value;
        String iccId = getIccSerialNumber();
        if (TextUtils.isEmpty(iccId) || TextUtils.isEmpty(key) || (value = PreferenceManager.getDefaultSharedPreferences(this.mContext).getStringSet(prefix + iccId, null)) == null) {
            return false;
        }
        return value.contains(key);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isRadioAvailable() {
        return this.mCi.getRadioState().isAvailable();
    }

    @Override // com.android.internal.telephony.Phone
    public void shutdownRadio() {
        getServiceStateTracker().requestShutdown();
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallBarringOption(String facility, String password, Message onComplete) {
        logUnexpectedCdmaMethodCall("getCallBarringOption");
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallBarringOption(String facility, boolean lockState, String password, Message onComplete) {
        logUnexpectedCdmaMethodCall("setCallBarringOption");
    }

    @Override // com.android.internal.telephony.Phone
    public void requestChangeCbPsw(String facility, String oldPwd, String newPwd, Message result) {
        logUnexpectedCdmaMethodCall("requestChangeCbPsw");
    }

    @Override // com.android.internal.telephony.Phone
    public void setLocalCallHold(int lchStatus) {
        this.mCi.setLocalCallHold(lchStatus);
    }

    @Override // com.android.internal.telephony.Phone
    public String getMccMncOnSimLock() {
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public void deflectCall(String number) throws CallStateException {
        throw new CallStateException("Unexpected deflectCall method call");
    }

    @Override // com.android.internal.telephony.Phone
    public void addParticipant(String dialString) throws CallStateException {
        throw new CallStateException("addParticipant is not supported in this phone " + this);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("PhoneBase: subId=" + getSubId());
        pw.println(" mPhoneId=" + this.mPhoneId);
        pw.println(" mCi=" + this.mCi);
        pw.println(" mDnsCheckDisabled=" + this.mDnsCheckDisabled);
        pw.println(" mDcTracker=" + this.mDcTracker);
        pw.println(" mDoesRilSendMultipleCallRing=" + this.mDoesRilSendMultipleCallRing);
        pw.println(" mCallRingContinueToken=" + this.mCallRingContinueToken);
        pw.println(" mCallRingDelay=" + this.mCallRingDelay);
        pw.println(" mIsTheCurrentActivePhone=" + this.mIsTheCurrentActivePhone);
        pw.println(" mIsVoiceCapable=" + this.mIsVoiceCapable);
        pw.println(" mIccRecords=" + this.mIccRecords.get());
        pw.println(" mUiccApplication=" + this.mUiccApplication.get());
        pw.println(" mSmsStorageMonitor=" + this.mSmsStorageMonitor);
        pw.println(" mSmsUsageMonitor=" + this.mSmsUsageMonitor);
        pw.flush();
        pw.println(" mLooper=" + this.mLooper);
        pw.println(" mContext=" + this.mContext);
        pw.println(" mNotifier=" + this.mNotifier);
        pw.println(" mSimulatedRadioControl=" + this.mSimulatedRadioControl);
        pw.println(" mUnitTestMode=" + this.mUnitTestMode);
        pw.println(" isDnsCheckDisabled()=" + isDnsCheckDisabled());
        pw.println(" getUnitTestMode()=" + getUnitTestMode());
        pw.println(" getState()=" + getState());
        pw.println(" getIccSerialNumber()=" + getIccSerialNumber());
        pw.println(" getIccRecordsLoaded()=" + getIccRecordsLoaded());
        pw.println(" getMessageWaitingIndicator()=" + getMessageWaitingIndicator());
        pw.println(" getCallForwardingIndicator()=" + getCallForwardingIndicator());
        pw.println(" isInEmergencyCall()=" + isInEmergencyCall());
        pw.flush();
        pw.println(" isInEcm()=" + isInEcm());
        pw.println(" getPhoneName()=" + getPhoneName());
        pw.println(" getPhoneType()=" + getPhoneType());
        pw.println(" getVoiceMessageCount()=" + getVoiceMessageCount());
        pw.println(" getActiveApnTypes()=" + getActiveApnTypes());
        pw.println(" isDataConnectivityPossible()=" + isDataConnectivityPossible());
        pw.println(" needsOtaServiceProvisioning=" + needsOtaServiceProvisioning());
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        try {
            this.mDcTracker.dump(fd, pw, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        try {
            getServiceStateTracker().dump(fd, pw, args);
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        try {
            getCallTracker().dump(fd, pw, args);
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        try {
            ((RIL) this.mCi).dump(fd, pw, args);
        } catch (Exception e4) {
            e4.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
    }

    @Override // com.android.internal.telephony.Phone
    public void setPreferredNetworkTypeWithOptimizeSetting(int networkType, boolean enable, Message response) {
        this.mCi.setRatModeOptimizeSetting(enable, obtainMessage(EVENT_SET_RAT_MODE_OPTIMIZE_SETTING_COMPLETE, networkType, 0, response));
    }

    @Override // com.android.internal.telephony.Phone
    public void getPreferredNetworkTypeWithOptimizeSetting(Message response) {
        this.mCi.getPreferredNetworkTypeWithOptimizeSetting(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setBandPref(long lteBand, int wcdmaBand, Message response) {
        this.mCi.setBandPref(lteBand, wcdmaBand, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void getBandPref(Message response) {
        this.mCi.getBandPref(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void resetDunProfiles() {
    }

    @Override // com.android.internal.telephony.Phone
    public void getIncomingAnonymousCallBarring(Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public void setIncomingAnonymousCallBarring(boolean lockState, Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public void getIncomingSpecificDnCallBarring(Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public void setIncomingSpecificDnCallBarring(int operation, String[] icbNum, Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public void setLimitationByChameleon(boolean isLimitation, Message response) {
        this.mCi.setLimitationByChameleon(isLimitation, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallForwardingOption(int commandInterfaceCFReason, int serviceClass, Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallForwardingOption(int commandInterfaceCFReason, int commandInterfaceCFAction, String dialingNumber, int timerSeconds, Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallForwardingOption(int commandInterfaceCFReason, int commandInterfaceCFAction, int serviceClass, String dialingNumber, int timerSeconds, Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallWaiting(Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallWaiting(int serviceClass, Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallWaiting(boolean enable, Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallWaiting(boolean enable, int serviceClass, Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallBarring(String facility, boolean lockState, String password, int serviceClass, Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallBarring(String facility, String password, int serviceClass, Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallBarring(String facility, boolean lockState, String password, Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallBarring(String facility, String password, Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallBarring(String facility, Message onComplete) {
    }

    @Override // com.android.internal.telephony.Phone
    public String getImsiOnSimLock() {
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public int getBrand() {
        return 0;
    }

    @Override // com.android.internal.telephony.Phone
    public void declineCall() throws CallStateException {
        throw new CallStateException("declineCall is not supported in this phone " + this);
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState, Bundle extras, int prefix) throws CallStateException {
        if (!TelBrand.IS_DCM) {
            return null;
        }
        throw new CallStateException("Dial with CallDetails is not supported in this phone " + this);
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState, int prefix) throws CallStateException {
        if (!TelBrand.IS_DCM) {
            return null;
        }
        throw new CallStateException("Dial with CallDetails is not supported in this phone " + this);
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, UUSInfo uusInfo, int videoState, int prefix) throws CallStateException {
        if (!TelBrand.IS_DCM) {
            return null;
        }
        throw new CallStateException("Dial with CallDetails is not supported in this phone " + this);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isDunConnectionPossible() {
        return false;
    }

    @Override // com.android.internal.telephony.Phone
    public void setMobileDataEnabledDun(boolean isEnable) {
    }

    @Override // com.android.internal.telephony.Phone
    public void setProfilePdpType(int cid, String pdpType) {
    }

    @Override // com.android.internal.telephony.Phone
    public int changeMode(boolean mode, String apn, String userId, String password, int authType, String dns1, String dns2, String proxyHost, String proxyPort) {
        return this.mDcTracker.changeOemKddiCpaMode(mode, apn, userId, password, authType, dns1, dns2, proxyHost, proxyPort);
    }

    @Override // com.android.internal.telephony.Phone
    public int getConnStatus() {
        return this.mDcTracker.getOemKddiCpaConnStatus();
    }

    @Override // com.android.internal.telephony.Phone
    public String[] getConnInfo() {
        return this.mDcTracker.getOemKddiCpaConnInfo();
    }

    @Override // com.android.internal.telephony.Phone
    public void setModemSettingsByChameleon(int pattern, Message response) {
        this.mCi.setModemSettingsByChameleon(pattern, response);
    }
}
