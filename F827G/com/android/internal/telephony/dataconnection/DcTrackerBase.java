package com.android.internal.telephony.dataconnection;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.EventLog;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.AsyncChannel;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.PduHeaders;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jp.co.sharp.android.internal.telephony.FastDormancy;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public abstract class DcTrackerBase extends Handler {
    protected static final String ACTION_DISCONNECT_BT_DUN = "jp.co.sharp.android.dun.action.ACTION_DISCONNECT_BT_DUN";
    protected static final int APN_DELAY_DEFAULT_MILLIS = 20000;
    protected static final int APN_FAIL_FAST_DELAY_DEFAULT_MILLIS = 3000;
    protected static final String APN_RESTORE_DELAY_PROP_NAME = "android.telephony.apn-restore";
    public static final String CONNECTIVITY_ACTION_CARNAVI = "com.kddi.android.cpa.CONNECTIVITY_CHANGE";
    protected static final int DATA_CONNECTION_ACTIVE_PH_LINK_DOWN = 1;
    protected static final int DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE = 0;
    protected static final int DATA_CONNECTION_ACTIVE_PH_LINK_UP = 2;
    protected static final int DATA_STALL_ALARM_AGGRESSIVE_DELAY_IN_MS_DEFAULT = 60000;
    protected static final int DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS_DEFAULT = 360000;
    protected static final String DATA_STALL_ALARM_TAG_EXTRA = "data.stall.alram.tag";
    protected static final boolean DATA_STALL_NOT_SUSPECTED = false;
    protected static final int DATA_STALL_NO_RECV_POLL_LIMIT = 1;
    protected static final boolean DATA_STALL_SUSPECTED = true;
    protected static final boolean DBG = true;
    protected static final String DEBUG_PROV_APN_ALARM = "persist.debug.prov_apn_alarm";
    protected static final String DEFALUT_DATA_ON_BOOT_PROP = "net.def_data_on_boot";
    protected static final String DEFAULT_DATA_RETRY_CONFIG = "default_randomization=2000,5000,10000,20000,40000,80000:5000,160000:5000,320000:5000,640000:5000,1280000:5000,1800000:5000";
    protected static final int DEFAULT_MAX_PDP_RESET_FAIL = 3;
    private static final int DEFAULT_MDC_INITIAL_RETRY = 1;
    public static final String EXTRA_CONNECTIVITY_STATUS_CARNAVI = "connStatus";
    public static final String EXTRA_ERRONO_CARNAVI = "errno";
    public static final String INTENT_CDMA_FAILCAUSE = "com.android.internal.telephony.cdma-failcause";
    public static final String INTENT_CDMA_FAILCAUSE_CAUSE = "cause";
    public static final String INTENT_CDMA_FAILCAUSE_EXTRA = "extra";
    protected static final String INTENT_DATA_STALL_ALARM = "com.android.internal.telephony.data-stall";
    protected static final String INTENT_PROVISIONING_APN_ALARM = "com.android.internal.telephony.provisioning_apn_alarm";
    protected static final String INTENT_RECONNECT_ALARM = "com.android.internal.telephony.data-reconnect";
    protected static final String INTENT_RECONNECT_ALARM_EXTRA_REASON = "reconnect_alarm_extra_reason";
    protected static final String INTENT_RECONNECT_ALARM_EXTRA_TYPE = "reconnect_alarm_extra_type";
    protected static final String INTENT_RESTART_TRYSETUP_ALARM = "com.android.internal.telephony.data-restart-trysetup";
    protected static final String INTENT_RESTART_TRYSETUP_ALARM_EXTRA_TYPE = "restart_trysetup_alarm_extra_type";
    protected static final int NO_RECV_POLL_LIMIT = 24;
    protected static final String NULL_IP = "0.0.0.0";
    protected static final int NUMBER_SENT_PACKETS_OF_HANG = 10;
    protected static final int OEM_KDDI_PROFILE_ID_ANDROID = 0;
    protected static final int OEM_KDDI_PROFILE_ID_CARNAVI = 1002;
    protected static final int OEM_KDDI_PROFILE_ID_LTE_NET = 1000;
    protected static final int OEM_KDDI_PROFILE_ID_LTE_NET_FOR_DATA = 1001;
    protected static final int POLL_LONGEST_RTT = 120000;
    protected static final int POLL_NETSTAT_MILLIS = 1000;
    protected static final int POLL_NETSTAT_SCREEN_OFF_MILLIS = 600000;
    protected static final int POLL_NETSTAT_SLOW_MILLIS = 5000;
    protected static final int PROVISIONING_APN_ALARM_DELAY_IN_MS_DEFAULT = 900000;
    protected static final String PROVISIONING_APN_ALARM_TAG_EXTRA = "provisioning.apn.alarm.tag";
    protected static final boolean RADIO_TESTS = false;
    protected static final String RECTRICTION_APN_CPA_CONNECTION = ".au-net.ne.jp";
    protected static final int RESTORE_DEFAULT_APN_DELAY = 60000;
    protected static final String SECONDARY_DATA_RETRY_CONFIG = "max_retries=3, 5000, 5000, 5000";
    protected static final boolean VDBG = false;
    protected static final boolean VDBG_STALL = true;
    protected ApnSetting mActiveApn;
    AlarmManager mAlarmManager;
    protected boolean mAutoAttachOnCreation;
    protected int mCidActive;
    ConnectivityManager mCm;
    private DataRoamingSettingObserver mDataRoamingSettingObserver;
    protected DcTesterFailBringUpAll mDcTesterFailBringUpAll;
    protected DcController mDcc;
    public String mLocalAddressCarNavi;
    protected int mNetStatPollPeriod;
    protected PhoneBase mPhone;
    protected ContentResolver mResolver;
    protected long mRxPkts;
    protected long mSentSinceLastRecv;
    private SubscriptionManager mSubscriptionManager;
    protected long mTxPkts;
    protected boolean mUserDataEnabled;
    static boolean mIsCleanupRequired = false;
    protected static boolean sPolicyDataEnabled = true;
    protected static int sEnableFailFastRefCounter = 0;
    protected Object mDataEnabledLock = new Object();
    protected boolean mInternalDataEnabled = true;
    protected boolean mUserDataEnabledDun = true;
    private boolean[] mDataEnabled = new boolean[10];
    private int mEnabledCount = 0;
    protected ApnSetting mOemKddiDunApn = null;
    protected boolean mConnectingApnCarNavi = false;
    public String[] mDnsesCarNavi = new String[2];
    public String[] mDnsAddressCarNavi = new String[2];
    public ApnSetting mApnCarNavi = null;
    public boolean mEnableApnCarNavi = false;
    public boolean mDisConnectingApnCarNavi = false;
    protected String mRequestedApnType = "default";
    protected String RADIO_RESET_PROPERTY = "gsm.radioreset";
    protected AtomicReference<IccRecords> mIccRecords = new AtomicReference<>();
    protected AtomicReference<IccRecords> mSimRecords = new AtomicReference<>();
    protected DctConstants.Activity mActivity = DctConstants.Activity.NONE;
    protected DctConstants.State mState = DctConstants.State.IDLE;
    protected Handler mDataConnectionTracker = null;
    protected boolean mNetStatPollEnabled = false;
    protected TxRxSum mDataStallTxRxSum = new TxRxSum(0, 0);
    protected int mDataStallAlarmTag = (int) SystemClock.elapsedRealtime();
    protected PendingIntent mDataStallAlarmIntent = null;
    protected int mNoRecvPollCount = 0;
    protected volatile boolean mDataStallDetectionEnabled = true;
    protected volatile boolean mFailFast = false;
    protected boolean mInVoiceCall = false;
    protected boolean mIsWifiConnected = false;
    protected PendingIntent mReconnectIntent = null;
    protected boolean mAutoAttachOnCreationConfig = false;
    protected boolean mIsScreenOn = true;
    protected AtomicInteger mUniqueIdGenerator = new AtomicInteger(0);
    protected HashMap<Integer, DataConnection> mDataConnections = new HashMap<>();
    protected HashMap<Integer, DcAsyncChannel> mDataConnectionAcHashMap = new HashMap<>();
    protected HashMap<String, Integer> mApnToDataConnectionId = new HashMap<>();
    protected final ConcurrentHashMap<String, ApnContext> mApnContexts = new ConcurrentHashMap<>();
    protected boolean mIsPhysicalLinkUp = false;
    protected final PriorityQueue<ApnContext> mPrioritySortedApnContexts = new PriorityQueue<>(5, new Comparator<ApnContext>() { // from class: com.android.internal.telephony.dataconnection.DcTrackerBase.1
        public int compare(ApnContext c1, ApnContext c2) {
            return c2.priority - c1.priority;
        }
    });
    protected ArrayList<ApnSetting> mAllApnSettings = new ArrayList<>();
    protected ApnSetting mPreferredApn = null;
    protected boolean mIsPsRestricted = false;
    protected ApnSetting mEmergencyApn = null;
    protected boolean mIsDisposed = false;
    protected boolean SH_DBG = true;
    protected boolean mIsProvisioning = false;
    protected String mProvisioningUrl = null;
    protected PendingIntent mProvisioningApnAlarmIntent = null;
    protected int mProvisioningApnAlarmTag = (int) SystemClock.elapsedRealtime();
    protected AsyncChannel mReplyAc = new AsyncChannel();
    protected String mPdpType = "IP";
    protected boolean mIsEpcPending = false;
    protected BroadcastReceiver mIntentReceiver = new BroadcastReceiver() { // from class: com.android.internal.telephony.dataconnection.DcTrackerBase.2
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            boolean enabled;
            boolean z = true;
            String action = intent.getAction();
            DcTrackerBase.this.log("onReceive: action=" + action);
            if (action.equals("android.intent.action.SCREEN_ON")) {
                DcTrackerBase.this.mIsScreenOn = true;
                DcTrackerBase.this.stopNetStatPoll();
                DcTrackerBase.this.startNetStatPoll();
                DcTrackerBase.this.restartDataStallAlarm();
            } else if (action.equals("android.intent.action.SCREEN_OFF")) {
                DcTrackerBase.this.mIsScreenOn = false;
                DcTrackerBase.this.stopNetStatPoll();
                DcTrackerBase.this.startNetStatPoll();
                DcTrackerBase.this.restartDataStallAlarm();
            } else if (action.startsWith(DcTrackerBase.INTENT_RECONNECT_ALARM)) {
                DcTrackerBase.this.log("Reconnect alarm. Previous state was " + DcTrackerBase.this.mState);
                DcTrackerBase.this.onActionIntentReconnectAlarm(intent);
            } else if (action.startsWith(DcTrackerBase.INTENT_RESTART_TRYSETUP_ALARM)) {
                DcTrackerBase.this.log("Restart trySetup alarm");
                DcTrackerBase.this.onActionIntentRestartTrySetupAlarm(intent);
            } else if (action.equals(DcTrackerBase.INTENT_DATA_STALL_ALARM)) {
                DcTrackerBase.this.onActionIntentDataStallAlarm(intent);
            } else if (action.equals(DcTrackerBase.INTENT_PROVISIONING_APN_ALARM)) {
                DcTrackerBase.this.onActionIntentProvisioningApnAlarm(intent);
            } else if (action.equals("android.net.wifi.STATE_CHANGE")) {
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                DcTrackerBase dcTrackerBase = DcTrackerBase.this;
                if (networkInfo == null || !networkInfo.isConnected()) {
                    z = false;
                }
                dcTrackerBase.mIsWifiConnected = z;
                DcTrackerBase.this.log("NETWORK_STATE_CHANGED_ACTION: mIsWifiConnected=" + DcTrackerBase.this.mIsWifiConnected);
                if (TelBrand.IS_DCM && !DcTrackerBase.this.mIsWifiConnected) {
                    DcTrackerBase.this.startNetStatPoll();
                }
            } else if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
                if (intent.getIntExtra("wifi_state", 4) == 3) {
                    enabled = true;
                } else {
                    enabled = false;
                }
                if (!enabled) {
                    DcTrackerBase.this.mIsWifiConnected = false;
                }
                DcTrackerBase.this.log("WIFI_STATE_CHANGED_ACTION: enabled=" + enabled + " mIsWifiConnected=" + DcTrackerBase.this.mIsWifiConnected);
            }
        }
    };
    private Runnable mPollNetStat = new Runnable() { // from class: com.android.internal.telephony.dataconnection.DcTrackerBase.3
        @Override // java.lang.Runnable
        public void run() {
            DcTrackerBase.this.updateDataActivity();
            if (DcTrackerBase.this.mIsScreenOn) {
                DcTrackerBase.this.mNetStatPollPeriod = Settings.Global.getInt(DcTrackerBase.this.mResolver, "pdp_watchdog_poll_interval_ms", 1000);
            } else {
                DcTrackerBase.this.mNetStatPollPeriod = Settings.Global.getInt(DcTrackerBase.this.mResolver, "pdp_watchdog_long_poll_interval_ms", DcTrackerBase.POLL_NETSTAT_SCREEN_OFF_MILLIS);
            }
            if (DcTrackerBase.this.mNetStatPollEnabled) {
                DcTrackerBase.this.mDataConnectionTracker.postDelayed(this, DcTrackerBase.this.mNetStatPollPeriod);
            }
        }
    };
    private final SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangedListener = new SubscriptionManager.OnSubscriptionsChangedListener() { // from class: com.android.internal.telephony.dataconnection.DcTrackerBase.4
        @Override // android.telephony.SubscriptionManager.OnSubscriptionsChangedListener
        public void onSubscriptionsChanged() {
            DcTrackerBase.this.log("SubscriptionListener.onSubscriptionInfoChanged");
            if (SubscriptionManager.isValidSubscriptionId(DcTrackerBase.this.mPhone.getSubId())) {
                if (DcTrackerBase.this.mDataRoamingSettingObserver != null) {
                    DcTrackerBase.this.mDataRoamingSettingObserver.unregister();
                }
                DcTrackerBase.this.mDataRoamingSettingObserver = new DataRoamingSettingObserver(DcTrackerBase.this.mPhone, DcTrackerBase.this.mPhone.getContext());
                DcTrackerBase.this.mDataRoamingSettingObserver.register();
            }
        }
    };
    protected int mCnt = 0;
    protected UiccController mUiccController = UiccController.getInstance();

    protected abstract void completeConnection(ApnContext apnContext);

    protected abstract DctConstants.State getOverallState();

    public abstract String[] getPcscfAddress(String str);

    public abstract DctConstants.State getState(String str);

    protected abstract void gotoIdleAndNotifyDataConnection(String str);

    protected abstract boolean isApnTypeAvailable(String str);

    protected abstract boolean isDataAllowed();

    public abstract boolean isDataPossible(String str);

    public abstract boolean isDisconnected();

    public abstract boolean isOnDemandDataPossible(String str);

    /* JADX INFO: Access modifiers changed from: protected */
    public abstract boolean isPermanentFail(DcFailCause dcFailCause);

    protected abstract boolean isProvisioningApn(String str);

    protected abstract void log(String str);

    protected abstract void loge(String str);

    protected abstract boolean mvnoMatches(IccRecords iccRecords, String str, String str2);

    protected abstract void onCleanUpAllConnections(String str);

    protected abstract void onCleanUpConnection(boolean z, int i, String str);

    protected abstract void onDataSetupComplete(AsyncResult asyncResult);

    protected abstract void onDataSetupCompleteError(AsyncResult asyncResult);

    protected abstract void onDisconnectDcRetrying(int i, AsyncResult asyncResult);

    protected abstract void onDisconnectDone(int i, AsyncResult asyncResult);

    protected abstract void onRadioAvailable();

    protected abstract void onRadioOffOrNotAvailable();

    protected abstract void onRoamingOff();

    protected abstract void onRoamingOn();

    protected abstract boolean onTrySetupData(String str);

    protected abstract boolean onUpdateIcc();

    protected abstract void onVoiceCallEnded();

    protected abstract void onVoiceCallStarted();

    protected abstract void restartRadio();

    public abstract void setDataAllowed(boolean z, Message message);

    public abstract void setImsRegistrationState(boolean z);

    protected abstract void setState(DctConstants.State state);

    /* JADX INFO: Access modifiers changed from: protected */
    public abstract void setupDataAfterDdsSwitchIfPossible();

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DataRoamingSettingObserver extends ContentObserver {
        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
        public DataRoamingSettingObserver(Handler handler, Context context) {
            super(handler);
            DcTrackerBase.this = r2;
            r2.mResolver = context.getContentResolver();
        }

        public void register() {
            String key = "data_roaming";
            if (TelephonyManager.getDefault().isMultiSimEnabled()) {
                key = key + DcTrackerBase.this.mPhone.getPhoneId();
            }
            DcTrackerBase.this.mResolver.registerContentObserver(Settings.Global.getUriFor(key), false, this);
        }

        public void unregister() {
            DcTrackerBase.this.mResolver.unregisterContentObserver(this);
        }

        @Override // android.database.ContentObserver
        public void onChange(boolean selfChange) {
            DcTrackerBase.this.log(" handleDataOnRoamingChange: updateOemDataSettings call");
            DcTrackerBase.this.updateOemDataSettings();
            if (DcTrackerBase.this.mPhone.getServiceState().getDataRoaming()) {
                DcTrackerBase.this.sendMessage(DcTrackerBase.this.obtainMessage(270347));
            }
        }
    }

    protected int getInitialMaxRetry() {
        if (this.mFailFast) {
            return 0;
        }
        return Settings.Global.getInt(this.mResolver, "mdc_initial_max_retry", SystemProperties.getInt("mdc_initial_max_retry", 1));
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class TxRxSum {
        public long rxPkts;
        public long txPkts;

        public TxRxSum() {
            DcTrackerBase.this = r1;
            reset();
        }

        public TxRxSum(long txPkts, long rxPkts) {
            DcTrackerBase.this = r1;
            this.txPkts = txPkts;
            this.rxPkts = rxPkts;
        }

        public TxRxSum(TxRxSum sum) {
            DcTrackerBase.this = r3;
            this.txPkts = sum.txPkts;
            this.rxPkts = sum.rxPkts;
        }

        public void reset() {
            this.txPkts = -1L;
            this.rxPkts = -1L;
        }

        public String toString() {
            return "{txSum=" + this.txPkts + " rxSum=" + this.rxPkts + "}";
        }

        public void updateTxRxSum() {
            this.txPkts = TrafficStats.getMobileTcpTxPackets();
            this.rxPkts = TrafficStats.getMobileTcpRxPackets();
        }
    }

    protected void onActionIntentReconnectAlarm(Intent intent) {
        String reason = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_REASON);
        String apnType = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_TYPE);
        log("onActionIntentReconnectAlarm: currSubId = " + intent.getIntExtra("subscription", -1) + " phoneSubId=" + this.mPhone.getSubId());
        ApnContext apnContext = this.mApnContexts.get(apnType);
        log("onActionIntentReconnectAlarm: mState=" + this.mState + " reason=" + reason + " apnType=" + apnType + " apnContext=" + apnContext + " mDataConnectionAsyncChannels=" + this.mDataConnectionAcHashMap);
        if (apnContext != null && apnContext.isEnabled()) {
            apnContext.setReason(reason);
            DctConstants.State apnContextState = apnContext.getState();
            log("onActionIntentReconnectAlarm: apnContext state=" + apnContextState);
            if (apnContextState == DctConstants.State.FAILED || apnContextState == DctConstants.State.IDLE) {
                log("onActionIntentReconnectAlarm: state is FAILED|IDLE, disassociate");
                DcAsyncChannel dcac = apnContext.getDcAc();
                if (dcac != null) {
                    dcac.tearDown(apnContext, "", null);
                }
                apnContext.setDataConnectionAc(null);
                apnContext.setState(DctConstants.State.IDLE);
            } else {
                log("onActionIntentReconnectAlarm: keep associated");
            }
            sendMessage(obtainMessage(270339, apnContext));
            apnContext.setReconnectIntent(null);
        }
    }

    protected void onActionIntentRestartTrySetupAlarm(Intent intent) {
        String apnType = intent.getStringExtra(INTENT_RESTART_TRYSETUP_ALARM_EXTRA_TYPE);
        ApnContext apnContext = this.mApnContexts.get(apnType);
        log("onActionIntentRestartTrySetupAlarm: mState=" + this.mState + " apnType=" + apnType + " apnContext=" + apnContext + " mDataConnectionAsyncChannels=" + this.mDataConnectionAcHashMap);
        sendMessage(obtainMessage(270339, apnContext));
    }

    protected void onActionIntentDataStallAlarm(Intent intent) {
        log("onActionIntentDataStallAlarm: action=" + intent.getAction());
        Message msg = obtainMessage(270353, intent.getAction());
        msg.arg1 = intent.getIntExtra(DATA_STALL_ALARM_TAG_EXTRA, 0);
        sendMessage(msg);
    }

    public DcTrackerBase(PhoneBase phone) {
        this.mUserDataEnabled = true;
        this.mAutoAttachOnCreation = false;
        this.mPhone = phone;
        log("DCT.constructor");
        this.mResolver = this.mPhone.getContext().getContentResolver();
        this.mUiccController.registerForIccChanged(this, 270369, null);
        this.mAlarmManager = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
        this.mCm = (ConnectivityManager) this.mPhone.getContext().getSystemService("connectivity");
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.SCREEN_ON");
        filter.addAction("android.intent.action.SCREEN_OFF");
        filter.addAction("android.net.wifi.STATE_CHANGE");
        filter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filter.addAction(INTENT_DATA_STALL_ALARM);
        filter.addAction(INTENT_PROVISIONING_APN_ALARM);
        this.mUserDataEnabled = getDataEnabled();
        this.mPhone.getContext().registerReceiver(this.mIntentReceiver, filter, null, this.mPhone);
        this.mDataEnabled[0] = SystemProperties.getBoolean(DEFALUT_DATA_ON_BOOT_PROP, true);
        if (this.mDataEnabled[0]) {
            this.mEnabledCount++;
        }
        this.mAutoAttachOnCreation = PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext()).getBoolean(PhoneBase.DATA_DISABLED_ON_BOOT_KEY, false);
        this.mSubscriptionManager = SubscriptionManager.from(this.mPhone.getContext());
        this.mSubscriptionManager.addOnSubscriptionsChangedListener(this.mOnSubscriptionsChangedListener);
        HandlerThread dcHandlerThread = new HandlerThread("DcHandlerThread");
        dcHandlerThread.start();
        Handler dcHandler = new Handler(dcHandlerThread.getLooper());
        this.mDcc = DcController.makeDcc(this.mPhone, this, dcHandler);
        this.mDcTesterFailBringUpAll = new DcTesterFailBringUpAll(this.mPhone, dcHandler);
        new FastDormancy(this.mPhone.mCi, this.mPhone.getContext());
    }

    public void dispose() {
        log("DCT.dispose");
        for (DcAsyncChannel dcac : this.mDataConnectionAcHashMap.values()) {
            dcac.disconnect();
        }
        this.mDataConnectionAcHashMap.clear();
        this.mIsDisposed = true;
        this.mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        this.mUiccController.unregisterForIccChanged(this);
        if (this.mDataRoamingSettingObserver != null) {
            this.mDataRoamingSettingObserver.unregister();
        }
        this.mSubscriptionManager.removeOnSubscriptionsChangedListener(this.mOnSubscriptionsChangedListener);
        this.mDcc.dispose();
        this.mDcTesterFailBringUpAll.dispose();
    }

    public long getSubId() {
        return this.mPhone.getSubId();
    }

    public DctConstants.Activity getActivity() {
        return this.mActivity;
    }

    void setActivity(DctConstants.Activity activity) {
        log("setActivity = " + activity);
        this.mActivity = activity;
        this.mPhone.notifyDataActivity();
    }

    public void incApnRefCount(String name) {
    }

    public void decApnRefCount(String name) {
    }

    public boolean isApnSupported(String name) {
        return false;
    }

    public int getApnPriority(String name) {
        return -1;
    }

    public boolean isApnTypeActive(String type) {
        ApnSetting dunApn;
        return (!"dun".equals(type) || (dunApn = fetchDunApn()) == null) ? this.mActiveApn != null && this.mActiveApn.canHandleType(type) : this.mActiveApn != null && dunApn.toString().equals(this.mActiveApn.toString());
    }

    public ApnSetting fetchDunApn() {
        if (SystemProperties.getBoolean("net.tethering.noprovisioning", false)) {
            log("fetchDunApn: net.tethering.noprovisioning=true ret: null");
            return null;
        } else if (TelBrand.IS_KDDI) {
            log("fetchDunApn() Start");
            if (isNetworkRoaming()) {
                return null;
            }
            return this.mOemKddiDunApn;
        } else {
            int bearer = -1;
            ApnSetting retDunSetting = null;
            Context c = this.mPhone.getContext();
            List<ApnSetting> dunSettings = ApnSetting.arrayFromString(Settings.Global.getString(c.getContentResolver(), "tether_dun_apn"));
            IccRecords r = this.mIccRecords.get();
            for (ApnSetting dunSetting : dunSettings) {
                String operator = r != null ? r.getOperatorNumeric() : "";
                if (dunSetting.bearer != 0) {
                    if (bearer == -1) {
                        bearer = this.mPhone.getServiceState().getRilDataRadioTechnology();
                    }
                    if (dunSetting.bearer != bearer) {
                        continue;
                    }
                }
                if (!dunSetting.numeric.equals(operator)) {
                    continue;
                } else if (!dunSetting.hasMvnoParams()) {
                    return dunSetting;
                } else {
                    if (r != null && mvnoMatches(r, dunSetting.mvnoType, dunSetting.mvnoMatchData)) {
                        return dunSetting;
                    }
                }
            }
            String[] apnArrayData = c.getResources().getStringArray(17235993);
            if (apnArrayData == null) {
                return null;
            }
            for (String apn : apnArrayData) {
                ApnSetting dunSetting2 = ApnSetting.fromString(apn);
                if (dunSetting2 != null) {
                    if (dunSetting2.bearer != 0) {
                        if (bearer == -1) {
                            bearer = this.mPhone.getServiceState().getRilDataRadioTechnology();
                        }
                        if (dunSetting2.bearer != bearer) {
                            continue;
                        }
                    }
                    if (!dunSetting2.hasMvnoParams()) {
                        retDunSetting = dunSetting2;
                    } else if (r != null && mvnoMatches(r, dunSetting2.mvnoType, dunSetting2.mvnoMatchData)) {
                        return dunSetting2;
                    }
                }
            }
            return retDunSetting;
        }
    }

    public boolean hasMatchedTetherApnSetting() {
        ApnSetting matched = fetchDunApn();
        log("hasMatchedTetherApnSetting: APN=" + matched);
        return matched != null;
    }

    public String[] getActiveApnTypes() {
        return this.mActiveApn != null ? this.mActiveApn.types : new String[]{"default"};
    }

    public String getActiveApnString(String apnType) {
        if (this.mActiveApn != null) {
            return this.mActiveApn.apn;
        }
        return null;
    }

    public void sendOemKddiFailCauseBroadcast(DcFailCause dcFailCause, ApnContext apnCtx) {
        int rilFailCause;
        if (this.mEnableApnCarNavi) {
            if (dcFailCause == DcFailCause.USER_AUTHENTICATION) {
                rilFailCause = -3;
            } else if (dcFailCause == DcFailCause.SIGNAL_LOST || dcFailCause == DcFailCause.RADIO_POWER_OFF) {
                rilFailCause = -2;
            } else {
                rilFailCause = -4;
            }
            Intent intent = new Intent(CONNECTIVITY_ACTION_CARNAVI);
            intent.putExtra(EXTRA_CONNECTIVITY_STATUS_CARNAVI, 4);
            intent.putExtra(EXTRA_ERRONO_CARNAVI, rilFailCause);
            this.mPhone.getContext().sendBroadcast(intent);
            log("DataConnection Sent intent CONNECTIVITY_ACTION for CarNavi error : " + rilFailCause);
        } else if (dcFailCause != DcFailCause.SIGNAL_LOST) {
            Intent intent2 = new Intent(INTENT_CDMA_FAILCAUSE);
            intent2.putExtra(INTENT_CDMA_FAILCAUSE_CAUSE, Integer.toString(dcFailCause.getErrorCode()));
            intent2.putExtra(INTENT_CDMA_FAILCAUSE_EXTRA, apnCtx.getApnType());
            this.mPhone.getContext().sendBroadcast(intent2);
            log("sendOemKddiFailCauseBroadcast: send INTENT_CDMA_FAILCAUSE type= " + apnCtx.getApnType() + " cause= " + Integer.toString(dcFailCause.getErrorCode()));
        }
    }

    public void setDataOnRoamingEnabled(boolean enabled) {
        int i;
        int i2 = 1;
        if (getDataOnRoamingEnabled() != enabled) {
            ContentResolver resolver = this.mPhone.getContext().getContentResolver();
            if (!TelephonyManager.getDefault().isMultiSimEnabled()) {
                if (enabled) {
                    i = 1;
                } else {
                    i = 0;
                }
                Settings.Global.putInt(resolver, "data_roaming", i);
            }
            String str = "data_roaming" + this.mPhone.getPhoneId();
            if (!enabled) {
                i2 = 0;
            }
            Settings.Global.putInt(resolver, str, i2);
        }
    }

    public boolean getDataOnRoamingEnabled() {
        boolean enabled;
        int i = 1;
        try {
            ContentResolver resolver = this.mPhone.getContext().getContentResolver();
            String key = "data_roaming";
            if (TelephonyManager.getDefault().isMultiSimEnabled()) {
                key = key + this.mPhone.getPhoneId();
            }
            return Settings.Global.getInt(resolver, key) != 0;
        } catch (Settings.SettingNotFoundException e) {
            try {
                ContentResolver resolver2 = this.mPhone.getContext().getContentResolver();
                if (Settings.Global.getInt(resolver2, "data_roaming") != 0) {
                    enabled = true;
                } else {
                    enabled = false;
                }
                String str = "data_roaming" + this.mPhone.getPhoneId();
                if (!enabled) {
                    i = 0;
                }
                Settings.Global.putInt(resolver2, str, i);
                return enabled;
            } catch (Settings.SettingNotFoundException e2) {
                return false;
            }
        }
    }

    public void setDataEnabled(boolean enable) {
        Message msg = obtainMessage(270366);
        msg.arg1 = enable ? 1 : 0;
        sendMessage(msg);
    }

    public boolean getDataEnabled() {
        int i = 0;
        try {
            return Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), new StringBuilder().append("mobile_data").append(this.mPhone.getPhoneId()).toString()) != 0;
        } catch (Settings.SettingNotFoundException e) {
            try {
                ContentResolver resolver = this.mPhone.getContext().getContentResolver();
                boolean enabled = Settings.Global.getInt(resolver, "mobile_data") != 0;
                String str = "mobile_data" + this.mPhone.getPhoneId();
                if (enabled) {
                    i = 1;
                }
                Settings.Global.putInt(resolver, str, i);
                return enabled;
            } catch (Settings.SettingNotFoundException e2) {
                return true;
            }
        }
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        boolean isProvApn;
        String apnType;
        switch (msg.what) {
            case 69636:
                log("DISCONNECTED_CONNECTED: msg=" + msg);
                DcAsyncChannel dcac = (DcAsyncChannel) msg.obj;
                this.mDataConnectionAcHashMap.remove(Integer.valueOf(dcac.getDataConnectionIdSync()));
                dcac.disconnected();
                return;
            case 270336:
                this.mCidActive = msg.arg1;
                onDataSetupComplete((AsyncResult) msg.obj);
                return;
            case 270337:
                onRadioAvailable();
                return;
            case 270339:
                String reason = null;
                if (msg.obj instanceof String) {
                    reason = (String) msg.obj;
                }
                onTrySetupData(reason);
                return;
            case 270342:
                onRadioOffOrNotAvailable();
                return;
            case 270343:
                onVoiceCallStarted();
                return;
            case 270344:
                onVoiceCallEnded();
                return;
            case 270347:
                onRoamingOn();
                return;
            case 270348:
                onRoamingOff();
                return;
            case 270349:
                onEnableApn(msg.arg1, msg.arg2);
                return;
            case 270351:
                log("DataConnectionTracker.handleMessage: EVENT_DISCONNECT_DONE msg=" + msg);
                onDisconnectDone(msg.arg1, (AsyncResult) msg.obj);
                onUpdateOemDataSettingsAsync();
                return;
            case 270353:
                onDataStallAlarm(msg.arg1);
                return;
            case 270360:
                onCleanUpConnection(msg.arg1 != 0, msg.arg2, (String) msg.obj);
                return;
            case 270362:
                restartRadio();
                return;
            case 270363:
                onSetInternalDataEnabled(msg.arg1 == 1);
                return;
            case 270364:
                log("EVENT_RESET_DONE");
                onResetDone((AsyncResult) msg.obj);
                return;
            case 270365:
                onCleanUpAllConnections((String) msg.obj);
                return;
            case 270366:
                boolean enabled = msg.arg1 == 1;
                log("CMD_SET_USER_DATA_ENABLE enabled=" + enabled);
                onSetUserDataEnabled(enabled);
                return;
            case 270367:
                boolean met = msg.arg1 == 1;
                log("CMD_SET_DEPENDENCY_MET met=" + met);
                Bundle bundle = msg.getData();
                if (bundle != null && (apnType = (String) bundle.get("apnType")) != null) {
                    onSetDependencyMet(apnType, met);
                    return;
                }
                return;
            case 270368:
                onSetPolicyDataEnabled(msg.arg1 == 1);
                return;
            case 270369:
                onUpdateIcc();
                return;
            case 270370:
                log("DataConnectionTracker.handleMessage: EVENT_DISCONNECT_DC_RETRYING msg=" + msg);
                onDisconnectDcRetrying(msg.arg1, (AsyncResult) msg.obj);
                return;
            case 270371:
                onDataSetupCompleteError((AsyncResult) msg.obj);
                return;
            case 270372:
                sEnableFailFastRefCounter = (msg.arg1 == 1 ? 1 : -1) + sEnableFailFastRefCounter;
                log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA:  sEnableFailFastRefCounter=" + sEnableFailFastRefCounter);
                if (sEnableFailFastRefCounter < 0) {
                    loge("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: sEnableFailFastRefCounter:" + sEnableFailFastRefCounter + " < 0");
                    sEnableFailFastRefCounter = 0;
                }
                boolean enabled2 = sEnableFailFastRefCounter > 0;
                log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: enabled=" + enabled2 + " sEnableFailFastRefCounter=" + sEnableFailFastRefCounter);
                if (this.mFailFast != enabled2) {
                    this.mFailFast = enabled2;
                    this.mDataStallDetectionEnabled = !enabled2;
                    if (!this.mDataStallDetectionEnabled || getOverallState() != DctConstants.State.CONNECTED || (this.mInVoiceCall && !this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed())) {
                        log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: stop data stall");
                        stopDataStallAlarm();
                        return;
                    }
                    log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: start data stall");
                    stopDataStallAlarm();
                    startDataStallAlarm(false);
                    return;
                }
                return;
            case 270373:
                Bundle bundle2 = msg.getData();
                if (bundle2 != null) {
                    try {
                        this.mProvisioningUrl = (String) bundle2.get("provisioningUrl");
                    } catch (ClassCastException e) {
                        loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioning url not a string" + e);
                        this.mProvisioningUrl = null;
                    }
                }
                if (TextUtils.isEmpty(this.mProvisioningUrl)) {
                    loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioning url is empty, ignoring");
                    this.mIsProvisioning = false;
                    this.mProvisioningUrl = null;
                    return;
                }
                loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioningUrl=" + this.mProvisioningUrl);
                this.mIsProvisioning = true;
                startProvisioningApnAlarm();
                return;
            case 270374:
                log("CMD_IS_PROVISIONING_APN");
                String apnType2 = null;
                try {
                    Bundle bundle3 = msg.getData();
                    if (bundle3 != null) {
                        apnType2 = (String) bundle3.get("apnType");
                    }
                    if (TextUtils.isEmpty(apnType2)) {
                        loge("CMD_IS_PROVISIONING_APN: apnType is empty");
                        isProvApn = false;
                    } else {
                        isProvApn = isProvisioningApn(apnType2);
                    }
                } catch (ClassCastException e2) {
                    loge("CMD_IS_PROVISIONING_APN: NO provisioning url ignoring");
                    isProvApn = false;
                }
                log("CMD_IS_PROVISIONING_APN: ret=" + isProvApn);
                this.mReplyAc.replyToMessage(msg, 270374, isProvApn ? 1 : 0);
                return;
            case 270375:
                log("EVENT_PROVISIONING_APN_ALARM");
                ApnContext apnCtx = this.mApnContexts.get("default");
                if (!apnCtx.isProvisioningApn() || !apnCtx.isConnectedOrConnecting()) {
                    log("EVENT_PROVISIONING_APN_ALARM: Not connected ignore");
                    return;
                } else if (this.mProvisioningApnAlarmTag == msg.arg1) {
                    log("EVENT_PROVISIONING_APN_ALARM: Disconnecting");
                    this.mIsProvisioning = false;
                    this.mProvisioningUrl = null;
                    stopProvisioningApnAlarm();
                    sendCleanUpConnection(true, apnCtx);
                    return;
                } else {
                    log("EVENT_PROVISIONING_APN_ALARM: ignore stale tag, mProvisioningApnAlarmTag:" + this.mProvisioningApnAlarmTag + " != arg1:" + msg.arg1);
                    return;
                }
            case 270376:
                if (msg.arg1 == 1) {
                    handleStartNetStatPoll((DctConstants.Activity) msg.obj);
                    return;
                } else if (msg.arg1 == 0) {
                    handleStopNetStatPoll((DctConstants.Activity) msg.obj);
                    return;
                } else {
                    return;
                }
            default:
                Rlog.e("DATA", "Unidentified event msg=" + msg);
                return;
        }
    }

    public boolean getAnyDataEnabled() {
        boolean result;
        synchronized (this.mDataEnabledLock) {
            result = this.mInternalDataEnabled && this.mUserDataEnabled && sPolicyDataEnabled && this.mUserDataEnabledDun && this.mEnabledCount != 0;
        }
        if (!result) {
            log("getAnyDataEnabled " + result);
        }
        return result;
    }

    protected boolean isEmergency() {
        boolean result;
        synchronized (this.mDataEnabledLock) {
            result = this.mPhone.isInEcm() || this.mPhone.isInEmergencyCall();
        }
        log("isEmergency: result=" + result);
        return result;
    }

    public int apnTypeToId(String type) {
        if (TextUtils.equals(type, "default")) {
            return 0;
        }
        if (TextUtils.equals(type, "mms")) {
            return 1;
        }
        if (TextUtils.equals(type, "supl")) {
            return 2;
        }
        if (TextUtils.equals(type, "dun")) {
            return 3;
        }
        if (TextUtils.equals(type, "hipri")) {
            return 4;
        }
        if (TextUtils.equals(type, "ims")) {
            return 5;
        }
        if (TextUtils.equals(type, "fota")) {
            return 6;
        }
        if (TextUtils.equals(type, "cbs")) {
            return 7;
        }
        if (TextUtils.equals(type, "ia")) {
            return 8;
        }
        if (TextUtils.equals(type, "emergency")) {
            return 9;
        }
        return -1;
    }

    protected String apnIdToType(int id) {
        switch (id) {
            case 0:
                return "default";
            case 1:
                return "mms";
            case 2:
                return "supl";
            case 3:
                return "dun";
            case 4:
                return "hipri";
            case 5:
                return "ims";
            case 6:
                return "fota";
            case 7:
                return "cbs";
            case 8:
                return "ia";
            case 9:
                return "emergency";
            default:
                log("Unknown id (" + id + ") in apnIdToType");
                return "default";
        }
    }

    public LinkProperties getLinkProperties(String apnType) {
        return isApnIdEnabled(apnTypeToId(apnType)) ? this.mDataConnectionAcHashMap.get(0).getLinkPropertiesSync() : new LinkProperties();
    }

    public NetworkCapabilities getNetworkCapabilities(String apnType) {
        return isApnIdEnabled(apnTypeToId(apnType)) ? this.mDataConnectionAcHashMap.get(0).getNetworkCapabilitiesSync() : new NetworkCapabilities();
    }

    protected void notifyDataConnection(String reason) {
        for (int id = 0; id < 10; id++) {
            if (this.mDataEnabled[id]) {
                this.mPhone.notifyDataConnection(reason, apnIdToType(id));
            }
        }
        notifyOffApnsOfAvailability(reason);
    }

    /* renamed from: com.android.internal.telephony.dataconnection.DcTrackerBase$5 */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static /* synthetic */ class AnonymousClass5 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[DctConstants.State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.IDLE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.RETRYING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.SCANNING.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTED.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.DISCONNECTING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
        }
    }

    private void notifyApnIdUpToCurrent(String reason, int apnId) {
        switch (AnonymousClass5.$SwitchMap$com$android$internal$telephony$DctConstants$State[this.mState.ordinal()]) {
            case 1:
            default:
                return;
            case 2:
            case 3:
            case 4:
                this.mPhone.notifyDataConnection(reason, apnIdToType(apnId), PhoneConstants.DataState.CONNECTING);
                return;
            case 5:
            case 6:
                this.mPhone.notifyDataConnection(reason, apnIdToType(apnId), PhoneConstants.DataState.CONNECTING);
                this.mPhone.notifyDataConnection(reason, apnIdToType(apnId), PhoneConstants.DataState.CONNECTED);
                return;
        }
    }

    private void notifyApnIdDisconnected(String reason, int apnId) {
        this.mPhone.notifyDataConnection(reason, apnIdToType(apnId), PhoneConstants.DataState.DISCONNECTED);
    }

    protected void notifyOffApnsOfAvailability(String reason) {
        log("notifyOffApnsOfAvailability - reason= " + reason);
        for (int id = 0; id < 10; id++) {
            if (!isApnIdEnabled(id)) {
                notifyApnIdDisconnected(reason, id);
            }
        }
    }

    public boolean isApnTypeEnabled(String apnType) {
        if (apnType == null) {
            return false;
        }
        return isApnIdEnabled(apnTypeToId(apnType));
    }

    protected synchronized boolean isApnIdEnabled(int id) {
        return id != -1 ? this.mDataEnabled[id] : false;
    }

    public void setEnabled(int id, boolean enable) {
        log("setEnabled(" + id + ", " + enable + ") with old state = " + this.mDataEnabled[id] + " and enabledCount = " + this.mEnabledCount);
        Message msg = obtainMessage(270349);
        msg.arg1 = id;
        msg.arg2 = enable ? 1 : 0;
        sendMessage(msg);
    }

    protected void onEnableApn(int apnId, int enabled) {
        log("EVENT_APN_ENABLE_REQUEST apnId=" + apnId + ", apnType=" + apnIdToType(apnId) + ", enabled=" + enabled + ", dataEnabled = " + this.mDataEnabled[apnId] + ", enabledCount = " + this.mEnabledCount + ", isApnTypeActive = " + isApnTypeActive(apnIdToType(apnId)));
        if (enabled == 1) {
            synchronized (this) {
                if (!this.mDataEnabled[apnId]) {
                    this.mDataEnabled[apnId] = true;
                    this.mEnabledCount++;
                }
            }
            String type = apnIdToType(apnId);
            if (!isApnTypeActive(type)) {
                this.mRequestedApnType = type;
                onEnableNewApn();
                return;
            }
            notifyApnIdUpToCurrent(Phone.REASON_APN_SWITCHED, apnId);
            return;
        }
        boolean didDisable = false;
        synchronized (this) {
            if (this.mDataEnabled[apnId]) {
                this.mDataEnabled[apnId] = false;
                this.mEnabledCount--;
                didDisable = true;
            }
        }
        if (didDisable) {
            if ((this.mEnabledCount == 0 || apnId == 3) && (!TelBrand.IS_KDDI || this.mEnabledCount == 0)) {
                this.mRequestedApnType = "default";
                onCleanUpConnection(true, apnId, Phone.REASON_DATA_DISABLED);
            }
            notifyApnIdDisconnected(Phone.REASON_DATA_DISABLED, apnId);
            if (this.mDataEnabled[0] && !isApnTypeActive("default")) {
                this.mRequestedApnType = "default";
                onEnableNewApn();
            }
        }
    }

    protected void onEnableNewApn() {
    }

    protected void onResetDone(AsyncResult ar) {
        log("EVENT_RESET_DONE");
        String reason = null;
        if (ar.userObj instanceof String) {
            reason = (String) ar.userObj;
        }
        gotoIdleAndNotifyDataConnection(reason);
    }

    public boolean setInternalDataEnabled(boolean enable) {
        int i;
        log("setInternalDataEnabled(" + enable + ")");
        Message msg = obtainMessage(270363);
        if (enable) {
            i = 1;
        } else {
            i = 0;
        }
        msg.arg1 = i;
        sendMessage(msg);
        return true;
    }

    protected void onSetInternalDataEnabled(boolean enabled) {
        synchronized (this.mDataEnabledLock) {
            this.mInternalDataEnabled = enabled;
            if (enabled) {
                log("onSetInternalDataEnabled: changed to enabled, try to setup data call");
                if (TelBrand.IS_KDDI) {
                    updateOemDataSettings();
                }
                onTrySetupData(Phone.REASON_DATA_ENABLED);
            } else {
                log("onSetInternalDataEnabled: changed to disabled, cleanUpAllConnections");
                cleanUpAllConnections(null);
            }
        }
    }

    public void cleanUpAllConnections(String cause) {
        Message msg = obtainMessage(270365);
        msg.obj = cause;
        sendMessage(msg);
    }

    protected void onSetUserDataEnabled(boolean enabled) {
        boolean z = true;
        synchronized (this.mDataEnabledLock) {
            if (this.mUserDataEnabled != enabled) {
                this.mUserDataEnabled = enabled;
                Settings.Global.putInt(this.mPhone.getContext().getContentResolver(), "mobile_data" + this.mPhone.getPhoneId(), enabled ? 1 : 0);
                if (TelBrand.IS_DCM || TelBrand.IS_SBM) {
                    Settings.Global.putInt(this.mPhone.getContext().getContentResolver(), "mobile_data", enabled ? 1 : 0);
                }
                if (!getDataOnRoamingEnabled() && this.mPhone.getServiceState().getDataRoaming()) {
                    if (enabled) {
                        notifyOffApnsOfAvailability(Phone.REASON_ROAMING_ON);
                    } else {
                        notifyOffApnsOfAvailability(Phone.REASON_DATA_DISABLED);
                    }
                }
                if (TelBrand.IS_DCM || TelBrand.IS_SBM) {
                    if (enabled) {
                        z = false;
                    }
                    updateOemDataSettingsAsync(z);
                }
                if (TelBrand.IS_KDDI) {
                    updateOemDataSettings();
                }
                if (enabled) {
                    onTrySetupData(Phone.REASON_DATA_ENABLED);
                } else {
                    onCleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED);
                }
            }
        }
    }

    void updateOemDataSettingsAsync(boolean async) {
        if (!async) {
            log("updateOemDataSettingsAsync sync");
            updateOemDataSettings();
            this.mCnt = 0;
            return;
        }
        this.mCnt++;
        log("updateOemDataSettingsAsync mCnt=" + this.mCnt);
        onUpdateOemDataSettingsAsync();
    }

    protected void onSetDependencyMet(String apnType, boolean met) {
    }

    protected void onSetPolicyDataEnabled(boolean enabled) {
        synchronized (this.mDataEnabledLock) {
            if (sPolicyDataEnabled != enabled) {
                sPolicyDataEnabled = enabled;
                updateOemDataSettings();
                if (enabled) {
                    onTrySetupData(Phone.REASON_DATA_ENABLED);
                } else {
                    onCleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED);
                }
            }
        }
    }

    protected String getReryConfig(boolean forDefault) {
        int nt = this.mPhone.getServiceState().getNetworkType();
        if (nt == 4 || nt == 7 || nt == 5 || nt == 6 || nt == 12 || nt == 14) {
            return SystemProperties.get("ro.cdma.data_retry_config");
        }
        if (forDefault) {
            return SystemProperties.get("ro.gsm.data_retry_config");
        }
        return SystemProperties.get("ro.gsm.2nd_data_retry_config");
    }

    protected void resetPollStats() {
        this.mTxPkts = -1L;
        this.mRxPkts = -1L;
        this.mNetStatPollPeriod = 1000;
    }

    public void startNetStatPoll() {
        if (getOverallState() == DctConstants.State.CONNECTED && !this.mNetStatPollEnabled) {
            log("startNetStatPoll");
            resetPollStats();
            this.mNetStatPollEnabled = true;
            this.mPollNetStat.run();
        } else if (TelBrand.IS_DCM && this.mIsPhysicalLinkUp && !this.mIsWifiConnected && !this.mNetStatPollEnabled) {
            log("OverallState is not CONNECTED, but mIsPhysicalLinkUp is " + this.mIsPhysicalLinkUp + ", startNetStatPoll");
            resetPollStats();
            this.mNetStatPollEnabled = true;
            this.mPollNetStat.run();
        }
        if (this.mPhone != null) {
            this.mPhone.notifyDataActivity();
        }
    }

    void stopNetStatPoll() {
        this.mNetStatPollEnabled = false;
        removeCallbacks(this.mPollNetStat);
        log("stopNetStatPoll");
        if (this.mPhone != null) {
            this.mPhone.notifyDataActivity();
        }
    }

    public void sendStartNetStatPoll(DctConstants.Activity activity) {
        Message msg = obtainMessage(270376);
        msg.arg1 = 1;
        msg.obj = activity;
        sendMessage(msg);
    }

    protected void handleStartNetStatPoll(DctConstants.Activity activity) {
        this.mIsPhysicalLinkUp = true;
        startNetStatPoll();
        startDataStallAlarm(false);
        setActivity(activity);
    }

    public void sendStopNetStatPoll(DctConstants.Activity activity) {
        Message msg = obtainMessage(270376);
        msg.arg1 = 0;
        msg.obj = activity;
        sendMessage(msg);
    }

    protected void handleStopNetStatPoll(DctConstants.Activity activity) {
        this.mIsPhysicalLinkUp = false;
        stopNetStatPoll();
        stopDataStallAlarm();
        setActivity(activity);
    }

    public void updateDataActivity() {
        DctConstants.Activity newActivity;
        TxRxSum preTxRxSum = new TxRxSum(this.mTxPkts, this.mRxPkts);
        TxRxSum curTxRxSum = new TxRxSum();
        curTxRxSum.updateTxRxSum();
        this.mTxPkts = curTxRxSum.txPkts;
        this.mRxPkts = curTxRxSum.rxPkts;
        if (!this.mNetStatPollEnabled) {
            return;
        }
        if (preTxRxSum.txPkts > 0 || preTxRxSum.rxPkts > 0) {
            long sent = this.mTxPkts - preTxRxSum.txPkts;
            long received = this.mRxPkts - preTxRxSum.rxPkts;
            if (sent > 0 && received > 0) {
                newActivity = DctConstants.Activity.DATAINANDOUT;
            } else if (sent > 0 && received == 0) {
                newActivity = DctConstants.Activity.DATAOUT;
            } else if (sent != 0 || received <= 0) {
                newActivity = this.mActivity == DctConstants.Activity.DORMANT ? this.mActivity : DctConstants.Activity.NONE;
            } else {
                newActivity = DctConstants.Activity.DATAIN;
            }
            if (this.mActivity != newActivity && this.mIsScreenOn) {
                this.mActivity = newActivity;
                this.mPhone.notifyDataActivity();
            }
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class RecoveryAction {
        public static final int CLEANUP = 1;
        public static final int GET_DATA_CALL_LIST = 0;
        public static final int RADIO_RESTART = 3;
        public static final int RADIO_RESTART_WITH_PROP = 4;
        public static final int REREGISTER = 2;

        protected RecoveryAction() {
        }

        public static boolean isAggressiveRecovery(int value) {
            return value == 1 || value == 2 || value == 3 || value == 4;
        }
    }

    public int getRecoveryAction() {
        int action = Settings.System.getInt(this.mPhone.getContext().getContentResolver(), "radio.data.stall.recovery.action", 0);
        log("getRecoveryAction: " + action);
        return action;
    }

    public void putRecoveryAction(int action) {
        Settings.System.putInt(this.mPhone.getContext().getContentResolver(), "radio.data.stall.recovery.action", action);
        log("putRecoveryAction: " + action);
    }

    protected boolean isConnected() {
        return false;
    }

    protected void doRecovery() {
        if (getOverallState() == DctConstants.State.CONNECTED) {
            int recoveryAction = getRecoveryAction();
            switch (recoveryAction) {
                case 0:
                    EventLog.writeEvent((int) EventLogTags.DATA_STALL_RECOVERY_GET_DATA_CALL_LIST, this.mSentSinceLastRecv);
                    log("doRecovery() get data call list");
                    this.mPhone.mCi.getDataCallList(obtainMessage(270340));
                    putRecoveryAction(1);
                    break;
                case 1:
                    EventLog.writeEvent((int) EventLogTags.DATA_STALL_RECOVERY_CLEANUP, this.mSentSinceLastRecv);
                    log("doRecovery() cleanup all connections");
                    cleanUpAllConnections(Phone.REASON_PDP_RESET);
                    putRecoveryAction(2);
                    break;
                case 2:
                    EventLog.writeEvent((int) EventLogTags.DATA_STALL_RECOVERY_REREGISTER, this.mSentSinceLastRecv);
                    log("doRecovery() re-register");
                    this.mPhone.getServiceStateTracker().reRegisterNetwork(null);
                    putRecoveryAction(3);
                    break;
                case 3:
                    EventLog.writeEvent((int) EventLogTags.DATA_STALL_RECOVERY_RADIO_RESTART, this.mSentSinceLastRecv);
                    log("restarting radio");
                    putRecoveryAction(4);
                    restartRadio();
                    break;
                case 4:
                    EventLog.writeEvent((int) EventLogTags.DATA_STALL_RECOVERY_RADIO_RESTART_WITH_PROP, -1);
                    log("restarting radio with gsm.radioreset to true");
                    SystemProperties.set(this.RADIO_RESET_PROPERTY, "true");
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                    }
                    restartRadio();
                    putRecoveryAction(0);
                    break;
                default:
                    throw new RuntimeException("doRecovery: Invalid recoveryAction=" + recoveryAction);
            }
            this.mSentSinceLastRecv = 0L;
        }
    }

    private void updateDataStallInfo() {
        TxRxSum preTxRxSum = new TxRxSum(this.mDataStallTxRxSum);
        this.mDataStallTxRxSum.updateTxRxSum();
        log("updateDataStallInfo: mDataStallTxRxSum=" + this.mDataStallTxRxSum + " preTxRxSum=" + preTxRxSum);
        long sent = this.mDataStallTxRxSum.txPkts - preTxRxSum.txPkts;
        long received = this.mDataStallTxRxSum.rxPkts - preTxRxSum.rxPkts;
        if (sent > 0 && received > 0) {
            log("updateDataStallInfo: IN/OUT");
            this.mSentSinceLastRecv = 0L;
            putRecoveryAction(0);
        } else if (sent > 0 && received == 0) {
            if (isPhoneStateIdle()) {
                this.mSentSinceLastRecv += sent;
            } else {
                this.mSentSinceLastRecv = 0L;
            }
            log("updateDataStallInfo: OUT sent=" + sent + " mSentSinceLastRecv=" + this.mSentSinceLastRecv);
        } else if (sent != 0 || received <= 0) {
            log("updateDataStallInfo: NONE");
        } else {
            log("updateDataStallInfo: IN");
            this.mSentSinceLastRecv = 0L;
            putRecoveryAction(0);
        }
    }

    private boolean isPhoneStateIdle() {
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            Phone phone = PhoneFactory.getPhone(i);
            if (phone != null && phone.getState() != PhoneConstants.State.IDLE) {
                log("isPhoneStateIdle: Voice call active on sub: " + i);
                return false;
            }
        }
        return true;
    }

    protected void onDataStallAlarm(int tag) {
        if (this.mDataStallAlarmTag != tag) {
            log("onDataStallAlarm: ignore, tag=" + tag + " expecting " + this.mDataStallAlarmTag);
            return;
        }
        updateDataStallInfo();
        int hangWatchdogTrigger = Settings.Global.getInt(this.mResolver, "pdp_watchdog_trigger_packet_count", 10);
        boolean suspectedStall = false;
        if (this.mSentSinceLastRecv >= hangWatchdogTrigger) {
            log("onDataStallAlarm: tag=" + tag + " do recovery action=" + getRecoveryAction());
            suspectedStall = true;
            sendMessage(obtainMessage(270354));
        } else {
            log("onDataStallAlarm: tag=" + tag + " Sent " + String.valueOf(this.mSentSinceLastRecv) + " pkts since last received, < watchdogTrigger=" + hangWatchdogTrigger);
        }
        startDataStallAlarm(suspectedStall);
    }

    protected void startDataStallAlarm(boolean suspectedStall) {
    }

    protected void stopDataStallAlarm() {
    }

    protected void restartDataStallAlarm() {
        if (isConnected()) {
            if (RecoveryAction.isAggressiveRecovery(getRecoveryAction())) {
                log("restartDataStallAlarm: action is pending. not resetting the alarm.");
                return;
            }
            log("restartDataStallAlarm: stop then start.");
            stopDataStallAlarm();
            startDataStallAlarm(false);
        }
    }

    protected void setInitialAttachApn(ArrayList<ApnSetting> apnList, IccRecords r) {
        ApnSetting iaApnSetting = null;
        ApnSetting defaultApnSetting = null;
        ApnSetting firstApnSetting = null;
        String operator = r != null ? r.getOperatorNumeric() : "";
        log("setInitialApn: E mPreferredApn=" + this.mPreferredApn);
        if (apnList != null && !apnList.isEmpty()) {
            firstApnSetting = apnList.get(0);
            log("setInitialApn: firstApnSetting=" + firstApnSetting);
            Iterator i$ = apnList.iterator();
            while (true) {
                if (!i$.hasNext()) {
                    break;
                }
                ApnSetting apn = i$.next();
                if (ArrayUtils.contains(apn.types, "ia") && apn.carrierEnabled) {
                    log("setInitialApn: iaApnSetting=" + apn);
                    iaApnSetting = apn;
                    break;
                } else if (defaultApnSetting == null && apn.canHandleType("default")) {
                    log("setInitialApn: defaultApnSetting=" + apn);
                    defaultApnSetting = apn;
                }
            }
        }
        ApnSetting initialAttachApnSetting = null;
        if (iaApnSetting != null) {
            log("setInitialAttachApn: using iaApnSetting");
            initialAttachApnSetting = iaApnSetting;
        } else if (this.mPreferredApn != null && Objects.equals(this.mPreferredApn.numeric, operator)) {
            log("setInitialAttachApn: using mPreferredApn");
            initialAttachApnSetting = this.mPreferredApn;
        } else if (defaultApnSetting != null) {
            if (!TelBrand.IS_DCM) {
                log("setInitialAttachApn: using defaultApnSetting");
                initialAttachApnSetting = defaultApnSetting;
            }
        } else if (firstApnSetting != null && !TelBrand.IS_DCM) {
            log("setInitialAttachApn: using firstApnSetting");
            initialAttachApnSetting = firstApnSetting;
        }
        if (initialAttachApnSetting == null) {
            log("setInitialAttachApn: X There in no available apn");
            if (operator != null && !operator.equals("")) {
                this.mPhone.mCi.setInitialAttachApn("", "", 0, "", "", null);
            }
        } else {
            log("setInitialAttachApn: X selected Apn=" + initialAttachApnSetting);
            this.mPhone.mCi.setInitialAttachApn(initialAttachApnSetting.apn, initialAttachApnSetting.protocol, initialAttachApnSetting.authType, initialAttachApnSetting.user, initialAttachApnSetting.password, null);
        }
        updateOemDataSettings();
    }

    protected void setInitialAttachApn() {
        setInitialAttachApn(this.mAllApnSettings, this.mIccRecords.get());
    }

    protected void setDataProfilesAsNeeded() {
        log("setDataProfilesAsNeeded");
        if (this.mAllApnSettings != null && !this.mAllApnSettings.isEmpty()) {
            ArrayList<DataProfile> dps = new ArrayList<>();
            Iterator<ApnSetting> it = this.mAllApnSettings.iterator();
            while (it.hasNext()) {
                ApnSetting apn = it.next();
                if (apn.modemCognitive) {
                    DataProfile dp = new DataProfile(apn, this.mPhone.getServiceState().getDataRoaming());
                    boolean isDup = false;
                    Iterator i$ = dps.iterator();
                    while (true) {
                        if (i$.hasNext()) {
                            if (dp.equals(i$.next())) {
                                isDup = true;
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                    if (!isDup) {
                        dps.add(dp);
                    }
                }
            }
            if (dps.size() > 0) {
                this.mPhone.mCi.setDataProfile((DataProfile[]) dps.toArray(new DataProfile[0]), null);
            }
        }
    }

    protected void onActionIntentProvisioningApnAlarm(Intent intent) {
        log("onActionIntentProvisioningApnAlarm: action=" + intent.getAction());
        Message msg = obtainMessage(270375, intent.getAction());
        msg.arg1 = intent.getIntExtra(PROVISIONING_APN_ALARM_TAG_EXTRA, 0);
        sendMessage(msg);
    }

    protected void startProvisioningApnAlarm() {
        int delayInMs = Settings.Global.getInt(this.mResolver, "provisioning_apn_alarm_delay_in_ms", PROVISIONING_APN_ALARM_DELAY_IN_MS_DEFAULT);
        if (Build.IS_DEBUGGABLE) {
            try {
                delayInMs = Integer.parseInt(System.getProperty(DEBUG_PROV_APN_ALARM, Integer.toString(delayInMs)));
            } catch (NumberFormatException e) {
                loge("startProvisioningApnAlarm: e=" + e);
            }
        }
        this.mProvisioningApnAlarmTag++;
        log("startProvisioningApnAlarm: tag=" + this.mProvisioningApnAlarmTag + " delay=" + (delayInMs / 1000) + "s");
        Intent intent = new Intent(INTENT_PROVISIONING_APN_ALARM);
        intent.putExtra(PROVISIONING_APN_ALARM_TAG_EXTRA, this.mProvisioningApnAlarmTag);
        this.mProvisioningApnAlarmIntent = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
        this.mAlarmManager.set(2, SystemClock.elapsedRealtime() + delayInMs, this.mProvisioningApnAlarmIntent);
    }

    protected void stopProvisioningApnAlarm() {
        log("stopProvisioningApnAlarm: current tag=" + this.mProvisioningApnAlarmTag + " mProvsioningApnAlarmIntent=" + this.mProvisioningApnAlarmIntent);
        this.mProvisioningApnAlarmTag++;
        if (this.mProvisioningApnAlarmIntent != null) {
            this.mAlarmManager.cancel(this.mProvisioningApnAlarmIntent);
            this.mProvisioningApnAlarmIntent = null;
        }
    }

    public void sendCleanUpConnection(boolean tearDown, ApnContext apnContext) {
        log("sendCleanUpConnection: tearDown=" + tearDown + " apnContext=" + apnContext);
        Message msg = obtainMessage(270360);
        msg.arg1 = tearDown ? 1 : 0;
        msg.arg2 = 0;
        msg.obj = apnContext;
        sendMessage(msg);
    }

    public void sendRestartRadio() {
        log("sendRestartRadio:");
        sendMessage(obtainMessage(270362));
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DcTrackerBase:");
        pw.println(" RADIO_TESTS=false");
        pw.println(" mInternalDataEnabled=" + this.mInternalDataEnabled);
        pw.println(" mUserDataEnabled=" + this.mUserDataEnabled);
        pw.println(" sPolicyDataEnabed=" + sPolicyDataEnabled);
        pw.println(" mDataEnabled:");
        for (int i = 0; i < this.mDataEnabled.length; i++) {
            pw.printf("  mDataEnabled[%d]=%b\n", Integer.valueOf(i), Boolean.valueOf(this.mDataEnabled[i]));
        }
        pw.flush();
        pw.println(" mEnabledCount=" + this.mEnabledCount);
        pw.println(" mRequestedApnType=" + this.mRequestedApnType);
        pw.println(" mPhone=" + this.mPhone.getPhoneName());
        pw.println(" mPhoneId=" + this.mPhone.getPhoneId());
        pw.println(" mActivity=" + this.mActivity);
        pw.println(" mState=" + this.mState);
        pw.println(" mTxPkts=" + this.mTxPkts);
        pw.println(" mRxPkts=" + this.mRxPkts);
        pw.println(" mNetStatPollPeriod=" + this.mNetStatPollPeriod);
        pw.println(" mNetStatPollEnabled=" + this.mNetStatPollEnabled);
        pw.println(" mDataStallTxRxSum=" + this.mDataStallTxRxSum);
        pw.println(" mDataStallAlarmTag=" + this.mDataStallAlarmTag);
        pw.println(" mDataStallDetectionEanbled=" + this.mDataStallDetectionEnabled);
        pw.println(" mSentSinceLastRecv=" + this.mSentSinceLastRecv);
        pw.println(" mNoRecvPollCount=" + this.mNoRecvPollCount);
        pw.println(" mResolver=" + this.mResolver);
        pw.println(" mIsWifiConnected=" + this.mIsWifiConnected);
        pw.println(" mReconnectIntent=" + this.mReconnectIntent);
        pw.println(" mCidActive=" + this.mCidActive);
        pw.println(" mAutoAttachOnCreation=" + this.mAutoAttachOnCreation);
        pw.println(" mIsScreenOn=" + this.mIsScreenOn);
        pw.println(" mUniqueIdGenerator=" + this.mUniqueIdGenerator);
        pw.flush();
        pw.println(" ***************************************");
        DcController dcc = this.mDcc;
        if (dcc != null) {
            dcc.dump(fd, pw, args);
        } else {
            pw.println(" mDcc=null");
        }
        pw.println(" ***************************************");
        if (this.mDataConnections != null) {
            Set<Map.Entry<Integer, DataConnection>> mDcSet = this.mDataConnections.entrySet();
            pw.println(" mDataConnections: count=" + mDcSet.size());
            for (Map.Entry<Integer, DataConnection> entry : mDcSet) {
                pw.printf(" *** mDataConnection[%d] \n", entry.getKey());
                entry.getValue().dump(fd, pw, args);
            }
        } else {
            pw.println("mDataConnections=null");
        }
        pw.println(" ***************************************");
        pw.flush();
        HashMap<String, Integer> apnToDcId = this.mApnToDataConnectionId;
        if (apnToDcId != null) {
            Set<Map.Entry<String, Integer>> apnToDcIdSet = apnToDcId.entrySet();
            pw.println(" mApnToDataConnectonId size=" + apnToDcIdSet.size());
            for (Map.Entry<String, Integer> entry2 : apnToDcIdSet) {
                pw.printf(" mApnToDataConnectonId[%s]=%d\n", entry2.getKey(), entry2.getValue());
            }
        } else {
            pw.println("mApnToDataConnectionId=null");
        }
        pw.println(" ***************************************");
        pw.flush();
        ConcurrentHashMap<String, ApnContext> apnCtxs = this.mApnContexts;
        if (apnCtxs != null) {
            Set<Map.Entry<String, ApnContext>> apnCtxsSet = apnCtxs.entrySet();
            pw.println(" mApnContexts size=" + apnCtxsSet.size());
            for (Map.Entry<String, ApnContext> entry3 : apnCtxsSet) {
                entry3.getValue().dump(fd, pw, args);
            }
            pw.println(" ***************************************");
        } else {
            pw.println(" mApnContexts=null");
        }
        pw.flush();
        pw.println(" mActiveApn=" + this.mActiveApn);
        ArrayList<ApnSetting> apnSettings = this.mAllApnSettings;
        if (apnSettings != null) {
            pw.println(" mAllApnSettings size=" + apnSettings.size());
            for (int i2 = 0; i2 < apnSettings.size(); i2++) {
                pw.printf(" mAllApnSettings[%d]: %s\n", Integer.valueOf(i2), apnSettings.get(i2));
            }
            pw.flush();
        } else {
            pw.println(" mAllApnSettings=null");
        }
        pw.println(" mPreferredApn=" + this.mPreferredApn);
        pw.println(" mIsPsRestricted=" + this.mIsPsRestricted);
        pw.println(" mIsDisposed=" + this.mIsDisposed);
        pw.println(" mIntentReceiver=" + this.mIntentReceiver);
        pw.println(" mDataRoamingSettingObserver=" + this.mDataRoamingSettingObserver);
        pw.flush();
    }

    protected void updateOemDataSettings() {
        boolean enabled = getAnyDataEnabledEx();
        boolean dataRoaming = getDataOnRoamingEnabled();
        boolean epcCapability = true;
        if (TelBrand.IS_DCM) {
            if (this.mUserDataEnabledDun) {
                epcCapability = true;
                this.mPdpType = "IP";
            } else if (this.mPdpType.equals("PPP")) {
                epcCapability = false;
                this.mPdpType = "IP";
            } else {
                epcCapability = true;
            }
        }
        this.mPhone.mCi.updateOemDataSettings(enabled, dataRoaming, epcCapability, null);
    }

    protected boolean getAnyDataEnabledEx() {
        boolean result;
        synchronized (this.mDataEnabledLock) {
            result = this.mInternalDataEnabled && this.mUserDataEnabled && sPolicyDataEnabled && this.mUserDataEnabledDun && this.mEnabledCount != 0;
        }
        if (!result) {
            log("getAnyDataEnabledEx " + result);
        }
        return result;
    }

    protected void onUpdateOemDataSettingsAsync() {
        log("onUpdateOemDataSettingsAsync");
        if (this.mCnt == 0) {
            log("updateLetApn is not required");
        } else if (isDisconnected()) {
            updateOemDataSettings();
            this.mCnt = 0;
        }
    }

    public void resetDunProfiles() {
    }

    public void setMobileDataEnabledDun(boolean enabled) {
        boolean async;
        if (TelBrand.IS_SBM) {
            synchronized (this.mDataEnabledLock) {
                this.mUserDataEnabledDun = enabled;
                if (enabled) {
                    async = false;
                    onTrySetupData(Phone.REASON_DATA_ENABLED);
                } else {
                    async = true;
                    onCleanUpAllConnections(Phone.REASON_DATA_DISABLED);
                }
                updateOemDataSettingsAsync(async);
            }
        } else if (TelBrand.IS_DCM) {
            synchronized (this.mDataEnabledLock) {
                boolean prevEnabled = getAnyDataEnabledEx();
                DctConstants.State overallState = getOverallState();
                if (!(overallState == DctConstants.State.IDLE || overallState == DctConstants.State.FAILED)) {
                }
                this.mUserDataEnabledDun = enabled;
                if (prevEnabled != getAnyDataEnabledEx()) {
                    if (!prevEnabled) {
                        if (this.mPhone.getState() != PhoneConstants.State.IDLE) {
                            this.mIsEpcPending = true;
                        } else {
                            updateOemDataSettings();
                        }
                        onTrySetupData(Phone.REASON_DATA_ENABLED);
                    } else {
                        updateDunProfileName(1, "IP", "dcmtrg.ne.jp");
                        onCleanUpAllConnections(Phone.REASON_DATA_DISABLED);
                    }
                }
            }
        }
    }

    protected void broadcastDisconnectDun() {
        this.mPhone.getContext().sendBroadcast(new Intent(ACTION_DISCONNECT_BT_DUN));
    }

    public void setProfilePdpType(int cid, String pdpType) {
        if (TelBrand.IS_DCM) {
            this.mPdpType = pdpType;
            log("setNotifyPdpType: current CID = " + cid + ", and correspoding pdpType = " + this.mPdpType);
        }
    }

    public void updateDunProfileName(int profileId, String dataProtocol, String apnName) {
    }

    public static boolean isNetworkRoaming() {
        return "true".equals(SystemProperties.get("gsm.operator.isroaming"));
    }

    private String getFixedParameter() {
        Throwable th;
        String fixedParam = "";
        FileInputStream fis = null;
        int[] pattern = {215, 67, 46, 69, 21, 74, 36, 236, PduHeaders.MBOX_TOTALS, BearerData.RELATIVE_TIME_MOBILE_INACTIVE, 70, PduHeaders.MBOX_TOTALS, 85};
        try {
            FileInputStream fis2 = new FileInputStream("/etc/Tsx5UPfM");
            try {
                byte[] buffer = new byte[PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_LACK_OF_PREPAID];
                int count = fis2.read(buffer);
                if (count == 235) {
                    int i = 0;
                    for (int j = 0; j < count; j++) {
                        buffer[j] = (byte) (pattern[i] ^ buffer[j]);
                        i++;
                        if (i >= pattern.length) {
                            i = 0;
                        }
                    }
                    fixedParam = new String(buffer, 0, count);
                }
                if (fis2 != null) {
                    try {
                        fis2.close();
                    } catch (IOException e) {
                    }
                }
            } catch (IOException e2) {
                fis = fis2;
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e3) {
                    }
                }
                return fixedParam;
            } catch (Throwable th2) {
                th = th2;
                fis = fis2;
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e4) {
                    }
                }
                throw th;
            }
        } catch (IOException e5) {
        } catch (Throwable th3) {
            th = th3;
        }
        return fixedParam;
    }

    public ApnSetting getApnParameter(boolean isCdma, boolean isInternet, int profileType, int id, String operator, String carrier) {
        String apn = null;
        String user = null;
        String pass = null;
        int authType = 2;
        String[] types = {"default", "mms", "supl", "hipri", "dun"};
        String protocol = "IP";
        String[] dnses = null;
        if (!isCdma && !isNetworkRoaming()) {
            types = isInternet ? new String[]{"default", "mms", "supl", "hipri"} : new String[]{"dun"};
        }
        String[] fixedParam = getFixedParameter().split(",");
        if (profileType == 1002) {
            apn = this.mApnCarNavi.apn;
            user = this.mApnCarNavi.user;
            pass = this.mApnCarNavi.password;
            authType = this.mApnCarNavi.authType;
            types = new String[]{CharacterSets.MIMENAME_ANY_CHARSET};
            dnses = this.mDnsesCarNavi;
        } else if (profileType == 1001) {
            if (isCdma) {
                if (fixedParam.length >= 3) {
                    user = fixedParam[1];
                    pass = fixedParam[2];
                }
            } else if (isInternet) {
                if (fixedParam.length >= 6) {
                    apn = fixedParam[3];
                    user = fixedParam[4];
                    pass = fixedParam[5];
                }
            } else if (fixedParam.length >= 9) {
                apn = fixedParam[6];
                user = fixedParam[7];
                pass = fixedParam[8];
            }
        } else if (!isCdma) {
            if (isInternet) {
                if (fixedParam.length >= 12) {
                    apn = fixedParam[9];
                    user = fixedParam[10];
                    pass = fixedParam[11];
                }
            } else if (fixedParam.length >= 15) {
                apn = fixedParam[12];
                user = fixedParam[13];
                pass = fixedParam[14];
            }
        }
        if (!isCdma) {
            protocol = "IPV4V6";
        }
        ApnSetting apnSetting = new ApnSetting(id, operator, carrier, apn, "", "", "", "", "", user, pass, authType, types, protocol, "IP", true, 0, 0, false, 0, 0, 0, 0, "", "");
        apnSetting.oemDnses = dnses;
        return apnSetting;
    }

    public int changeMode(boolean mode, String apn, String userId, String password, int authType, String dns1, String dns2, String proxyHost, String proxyPort) {
        if (!TelBrand.IS_KDDI) {
            return -1;
        }
        if (this.mState == DctConstants.State.IDLE || this.mState == DctConstants.State.FAILED) {
        }
        int apnId = apnTypeToId("default");
        log("[CarNavi] mode[" + mode + "]");
        log("[CarNavi] apn[" + apn + "]");
        log("[CarNavi] userId[" + userId + "]");
        log("[CarNavi] password[" + password + "]");
        log("[CarNavi] authType[" + authType + "]");
        log("[CarNavi] dns1[" + dns1 + "]");
        log("[CarNavi] dns2[" + dns2 + "]");
        log("[CarNavi] proxyHost[" + proxyHost + "]");
        log("[CarNavi] proxyPort[" + proxyPort + "]");
        if (!this.mUserDataEnabled) {
            log("CpaManager.UNKNOWN_ERROR. mUserDataEnabled : " + this.mUserDataEnabled);
            return -4;
        } else if (isNetworkRoaming() && mode) {
            log("CpaManager.RADIO_NOT_AVAILABLE. isNetworkRoaming()");
            return -2;
        } else if (this.mPhone.getServiceState().getRadioTechnology() == 0) {
            log("CpaManager.RADIO_NOT_AVAILABLE. RADIO_TECHNOLOGY_UNKNOWN");
            return -2;
        } else {
            if (mode != this.mEnableApnCarNavi) {
                if (mode && !this.mEnableApnCarNavi && (isApnTypeEnabled("default") || (!isApnTypeEnabled("default") && (isApnTypeEnabled("mms") || isApnTypeEnabled("supl") || isApnTypeEnabled("dun") || isApnTypeEnabled("hipri"))))) {
                    log("mConnectingApnCarNavi = true");
                    this.mConnectingApnCarNavi = true;
                } else if (!mode && this.mEnableApnCarNavi) {
                    log("mDisConnectingApnCarNavi = true");
                    this.mDisConnectingApnCarNavi = true;
                }
            }
            this.mApnCarNavi = new ApnSetting(apnId, "", "", apn, proxyHost, proxyPort, "", "", "", userId, password, authType, new String[]{"default", "mms", "supl", "hipri", "dun"}, "", "", true, this.mPhone.getServiceState().getRadioTechnology(), 0, false, 0, 0, 0, 0, "", "");
            this.mEnableApnCarNavi = mode;
            this.mDnsesCarNavi[0] = dns1;
            this.mDnsesCarNavi[1] = dns2;
            sendMessage(obtainMessage(270355));
            return 0;
        }
    }

    protected void notifyDataConnectionCarNavi(DctConstants.State state) {
        if (TelBrand.IS_KDDI) {
            log("notifyDataConnectionCarNavi() mEnableApnCarNavi :" + this.mEnableApnCarNavi + " mRequestedApnType : " + this.mRequestedApnType + " state : " + state);
            if (this.mEnableApnCarNavi && this.mConnectingApnCarNavi && state == DctConstants.State.CONNECTING) {
                log("notifyDataConnectionCarNavi() mConnectingApnCarNavi=true state change. [" + this.mState + "]-->[" + state + "]");
            } else if (this.mState == state) {
                log("notifyDataConnectionCarNavi() ignored same state");
                return;
            } else if (this.mEnableApnCarNavi && !((this.mState != DctConstants.State.RETRYING && this.mState != DctConstants.State.CONNECTING && state != DctConstants.State.SCANNING) || state == DctConstants.State.FAILED || state == DctConstants.State.CONNECTED)) {
                log("notifyDataConnectionCarNavi() ignored invalid state change. [" + this.mState + "]-->[" + state + "]");
                return;
            }
            this.mState = state;
            if (this.mEnableApnCarNavi || (!this.mEnableApnCarNavi && this.mDisConnectingApnCarNavi)) {
                if (this.mConnectingApnCarNavi) {
                    if (!(state == DctConstants.State.RETRYING || state == DctConstants.State.CONNECTING)) {
                        log("notifyDataConnectionCarNavi() ignored. mConnectingApnCarNavi : " + this.mConnectingApnCarNavi + " state : " + state);
                        return;
                    }
                } else if (!this.mEnableApnCarNavi && this.mDisConnectingApnCarNavi) {
                    if (state != DctConstants.State.IDLE && state != DctConstants.State.FAILED && state != DctConstants.State.DISCONNECTING) {
                        log("notifyDataConnectionCarNavi() ignored. mDisConnectingApnCarNavi : " + this.mDisConnectingApnCarNavi + " state : " + state);
                        return;
                    } else if (state == DctConstants.State.IDLE || state == DctConstants.State.FAILED) {
                        log("mDisConnectingApnCarNavi = false");
                        this.mDisConnectingApnCarNavi = false;
                    }
                }
                int stateCarNavi = 4;
                if (state == DctConstants.State.RETRYING || state == DctConstants.State.CONNECTING || state == DctConstants.State.SCANNING) {
                    stateCarNavi = 1;
                    if (this.mConnectingApnCarNavi) {
                        log("mConnectingApnCarNavi = false");
                        this.mConnectingApnCarNavi = false;
                    }
                } else if (state == DctConstants.State.CONNECTED) {
                    stateCarNavi = 2;
                } else if (state == DctConstants.State.DISCONNECTING) {
                    stateCarNavi = 3;
                } else if (state == DctConstants.State.FAILED || state == DctConstants.State.IDLE) {
                    stateCarNavi = 4;
                }
                Intent intent = new Intent(CONNECTIVITY_ACTION_CARNAVI);
                intent.putExtra(EXTRA_CONNECTIVITY_STATUS_CARNAVI, stateCarNavi);
                intent.putExtra(EXTRA_ERRONO_CARNAVI, 0);
                this.mPhone.getContext().sendStickyBroadcast(intent);
                log("Sent intent CONNECTIVITY_ACTION for CarNavi : " + state + " / " + stateCarNavi);
            }
            if (this.mEnableApnCarNavi && this.mState == DctConstants.State.IDLE) {
                changeMode(false, "", "", "", 0, "", "", "", "");
                this.mEnableApnCarNavi = false;
                this.mDisConnectingApnCarNavi = false;
            }
        }
    }

    public String getHigherPriorityApnType() {
        if (!TelBrand.IS_KDDI) {
            return "default";
        }
        ApnContext[] arr$ = (ApnContext[]) this.mPrioritySortedApnContexts.toArray(new ApnContext[0]);
        for (ApnContext apnContextEntry : arr$) {
            if (apnContextEntry.isEnabled()) {
                String higherPrioApnType = apnContextEntry.getApnType();
                log("higherPrioApnType : " + higherPrioApnType);
                return higherPrioApnType;
            }
        }
        return "default";
    }

    public void setStateCarNavi(ApnContext apnContext) {
        if (TelBrand.IS_KDDI) {
            if (apnContext.getApnType().equals(getHigherPriorityApnType())) {
                notifyDataConnectionCarNavi(apnContext.getState());
            }
        }
    }

    public int changeOemKddiCpaMode(boolean mode, String apn, String userId, String password, int authType, String dns1, String dns2, String proxyHost, String proxyPort) {
        return changeMode(mode, apn, userId, password, authType, dns1, dns2, proxyHost, proxyPort);
    }

    public int getOemKddiCpaConnStatus() {
        String higherPrioApnType = getHigherPriorityApnType();
        log("getConnStatus() higherPrioApnType : " + higherPrioApnType);
        if ((!this.mEnableApnCarNavi && (this.mEnableApnCarNavi || !this.mDisConnectingApnCarNavi)) || !isApnTypeActive(higherPrioApnType)) {
            return 6;
        }
        DctConstants.State state = getState(higherPrioApnType);
        if (state == DctConstants.State.IDLE) {
            return 0;
        }
        if (state == DctConstants.State.CONNECTING) {
            return 2;
        }
        if (state == DctConstants.State.SCANNING) {
            return 3;
        }
        if (state == DctConstants.State.CONNECTED) {
            return 4;
        }
        if (state == DctConstants.State.DISCONNECTING) {
            return 5;
        }
        if (state == DctConstants.State.FAILED || state == DctConstants.State.RETRYING) {
        }
        return 6;
    }

    public String[] getOemKddiCpaConnInfo() {
        String[] getAddress = new String[3];
        if (this.mEnableApnCarNavi) {
            getAddress[0] = new String(this.mLocalAddressCarNavi);
            getAddress[1] = new String(this.mDnsAddressCarNavi[0]);
            getAddress[2] = new String(this.mDnsAddressCarNavi[1]);
            log("getAddress[0] : " + getAddress[0] + " / " + this.mLocalAddressCarNavi);
            log("getAddress[1] : " + getAddress[1] + " / " + this.mDnsAddressCarNavi[0]);
            log("getAddress[2] : " + getAddress[2] + " / " + this.mDnsAddressCarNavi[1]);
        }
        return getAddress;
    }
}
