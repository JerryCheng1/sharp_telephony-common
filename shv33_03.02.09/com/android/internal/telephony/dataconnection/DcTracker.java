package com.android.internal.telephony.dataconnection;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources.NotFoundException;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkConfig;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.NetworkUtils;
import android.net.ProxyInfo;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RegistrantList;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.provider.Telephony.Carriers;
import android.telephony.CarrierConfigManager;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.LocalLog;
import android.util.Pair;
import android.util.SparseArray;
import com.android.internal.telephony.DctConstants.Activity;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.ITelephony.Stub;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneInternalInterface;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.TelephonyEventLog;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.dataconnection.DataConnection.ConnectionParams;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jp.co.sharp.android.internal.telephony.FastDormancy;

public class DcTracker extends Handler {
    /* renamed from: -com-android-internal-telephony-DctConstants$StateSwitchesValues */
    private static final /* synthetic */ int[] f18-com-android-internal-telephony-DctConstants$StateSwitchesValues = null;
    protected static final String ACTION_DISCONNECT_BT_DUN = "jp.co.sharp.android.dun.action.ACTION_DISCONNECT_BT_DUN";
    protected static final String APN_DCM_INTERNET_DEFAULT;
    protected static final String APN_DCM_TEST = "test.net";
    static final String APN_ID = "apn_id";
    private static final String APN_TYPE_KEY = "apnType";
    protected static final String COLUMN_APN_ID = "apn_id";
    public static final String CONNECTIVITY_ACTION_CARNAVI = "com.kddi.android.cpa.CONNECTIVITY_CHANGE";
    private static final int DATA_STALL_ALARM_AGGRESSIVE_DELAY_IN_MS_DEFAULT = 60000;
    private static final int DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS_DEFAULT = 360000;
    private static final String DATA_STALL_ALARM_TAG_EXTRA = "data.stall.alram.tag";
    private static final boolean DATA_STALL_NOT_SUSPECTED = false;
    private static final boolean DATA_STALL_SUSPECTED = true;
    protected static final boolean DBG = true;
    private static final String DEBUG_PROV_APN_ALARM = "persist.debug.prov_apn_alarm";
    private static final String ERROR_CODE_KEY = "errorCode";
    public static final String EXTRA_CONNECTIVITY_STATUS_CARNAVI = "connStatus";
    public static final String EXTRA_ERRONO_CARNAVI = "errno";
    public static final String INTENT_CDMA_FAILCAUSE = "com.android.internal.telephony.cdma-failcause";
    public static final String INTENT_CDMA_FAILCAUSE_CAUSE = "cause";
    public static final String INTENT_CDMA_FAILCAUSE_EXTRA = "extra";
    private static final String INTENT_DATA_STALL_ALARM = "com.android.internal.telephony.data-stall";
    private static final String INTENT_PROVISIONING_APN_ALARM = "com.android.internal.telephony.provisioning_apn_alarm";
    private static final String INTENT_RECONNECT_ALARM = "com.android.internal.telephony.data-reconnect";
    private static final String INTENT_RECONNECT_ALARM_EXTRA_REASON = "reconnect_alarm_extra_reason";
    private static final String INTENT_RECONNECT_ALARM_EXTRA_TYPE = "reconnect_alarm_extra_type";
    protected static final int INVALID_APN_ID = -1;
    private static final int NUMBER_SENT_PACKETS_OF_HANG = 10;
    private static final String OEM_DNS_PRIMARY = "oem_dns_primary";
    private static final String OEM_DNS_SECOUNDARY = "oem_dns_secoundary";
    protected static final int OEM_KDDI_PROFILE_ID_ANDROID = 0;
    protected static final int OEM_KDDI_PROFILE_ID_CARNAVI = 1002;
    protected static final int OEM_KDDI_PROFILE_ID_LTE_NET = 1000;
    protected static final int OEM_KDDI_PROFILE_ID_LTE_NET_FOR_DATA = 1001;
    private static final int POLL_NETSTAT_MILLIS = 1000;
    private static final int POLL_NETSTAT_SCREEN_OFF_MILLIS = 600000;
    private static final int POLL_PDP_MILLIS = 5000;
    static final Uri PREFERAPN_NO_UPDATE_URI_USING_SUBID = Uri.parse("content://telephony/carriers/preferapn_no_update/subId/");
    protected static final String PREF_FILE = "preferred-apn";
    private static final int PROVISIONING_APN_ALARM_DELAY_IN_MS_DEFAULT = 900000;
    private static final String PROVISIONING_APN_ALARM_TAG_EXTRA = "provisioning.apn.alarm.tag";
    private static final int PROVISIONING_SPINNER_TIMEOUT_MILLIS = 120000;
    private static final String PUPPET_MASTER_RADIO_STRESS_TEST = "gsm.defaultpdpcontext.active";
    private static final boolean RADIO_TESTS = false;
    protected static final String RECTRICTION_APN_CPA_CONNECTION = ".au-net.ne.jp";
    private static final String REDIRECTION_URL_KEY = "redirectionUrl";
    protected static final String TELEPHONY_PROVIDER_PACKAGE = "com.android.providers.telephony";
    private static final boolean VDBG = false;
    private static final boolean VDBG_STALL = false;
    private static int sEnableFailFastRefCounter = 0;
    private static boolean sPolicyDataEnabled = true;
    protected String LOG_TAG;
    private String RADIO_RESET_PROPERTY;
    private int RetryEnableApn;
    public AtomicBoolean isCleanupRequired;
    private Activity mActivity;
    private final AlarmManager mAlarmManager;
    protected ArrayList<ApnSetting> mAllApnSettings;
    private RegistrantList mAllDataDisconnectedRegistrants;
    public ApnSetting mApnCarNavi;
    private final ConcurrentHashMap<String, ApnContext> mApnContexts;
    private final SparseArray<ApnContext> mApnContextsById;
    private ApnChangeObserver mApnObserver;
    private HashMap<String, Integer> mApnToDataConnectionId;
    private AtomicBoolean mAttached;
    protected AtomicBoolean mAutoAttachOnCreation;
    protected boolean mAutoAttachOnCreationConfig;
    private boolean mCanSetPreferApn;
    protected int mCid;
    private final ConnectivityManager mCm;
    private boolean mColdSimDetected;
    protected boolean mConnectingApnCarNavi;
    private HashMap<Integer, DcAsyncChannel> mDataConnectionAcHashMap;
    private final Handler mDataConnectionTracker;
    private HashMap<Integer, DataConnection> mDataConnections;
    private Object mDataEnabledLock;
    private PendingIntent mDataStallAlarmIntent;
    private int mDataStallAlarmTag;
    private volatile boolean mDataStallDetectionEnabled;
    private TxRxSum mDataStallTxRxSum;
    private DcTesterFailBringUpAll mDcTesterFailBringUpAll;
    private DcController mDcc;
    public boolean mDisConnectingApnCarNavi;
    private ArrayList<Message> mDisconnectAllCompleteMsgList;
    private int mDisconnectPendingCount;
    public String[] mDnsAddressCarNavi;
    public String[] mDnsesCarNavi;
    private ApnSetting mEmergencyApn;
    public boolean mEnableApnCarNavi;
    private volatile boolean mFailFast;
    protected final AtomicReference<IccRecords> mIccRecords;
    public boolean mImsRegistrationState;
    private boolean mInVoiceCall;
    private final BroadcastReceiver mIntentReceiver;
    private boolean mInternalDataEnabled;
    protected boolean mIsDisposed;
    protected boolean mIsPhysicalLinkUp;
    private boolean mIsProvisioning;
    private boolean mIsPsRestricted;
    private boolean mIsScreenOn;
    private boolean mIsWifiConnected;
    public String mLocalAddressCarNavi;
    protected boolean mMvnoMatched;
    private boolean mNetStatPollEnabled;
    private int mNetStatPollPeriod;
    private int mNoRecvPollCount;
    protected ApnSetting mOemKddiDunApn;
    private final OnSubscriptionsChangedListener mOnSubscriptionsChangedListener;
    private boolean mOutOfCreditSimDetected;
    protected String mPdpType;
    protected final Phone mPhone;
    private final Runnable mPollNetStat;
    protected ApnSetting mPreferredApn;
    private final PriorityQueue<ApnContext> mPrioritySortedApnContexts;
    private final String mProvisionActionName;
    private BroadcastReceiver mProvisionBroadcastReceiver;
    private PendingIntent mProvisioningApnAlarmIntent;
    private int mProvisioningApnAlarmTag;
    private ProgressDialog mProvisioningSpinner;
    private String mProvisioningUrl;
    private PendingIntent mReconnectIntent;
    private String mRedirectUrl;
    private AsyncChannel mReplyAc;
    private String mRequestedApnType;
    private boolean mReregisterOnReconnectFailure;
    private ContentResolver mResolver;
    private long mRxPkts;
    private long mSentSinceLastRecv;
    private final SettingsObserver mSettingsObserver;
    protected State mState;
    private SubscriptionManager mSubscriptionManager;
    private long mTxPkts;
    private final UiccController mUiccController;
    private AtomicInteger mUniqueIdGenerator;
    private boolean mUserDataEnabled;
    protected boolean mUserDataEnabledDun;
    private HashSet<ApnContext> redirectApnContextSet;

    private class ApnChangeObserver extends ContentObserver {
        public ApnChangeObserver() {
            super(DcTracker.this.mDataConnectionTracker);
        }

        public void onChange(boolean selfChange) {
            DcTracker.this.sendMessage(DcTracker.this.obtainMessage(270355));
        }
    }

    public static class DataAllowFailReason {
        private HashSet<DataAllowFailReasonType> mDataAllowFailReasonSet = new HashSet();

        public void addDataAllowFailReason(DataAllowFailReasonType type) {
            this.mDataAllowFailReasonSet.add(type);
        }

        public String getDataAllowFailReason() {
            StringBuilder failureReason = new StringBuilder();
            failureReason.append("isDataAllowed: No");
            for (DataAllowFailReasonType reason : this.mDataAllowFailReasonSet) {
                failureReason.append(reason.mFailReasonStr);
            }
            return failureReason.toString();
        }

        public boolean isFailForSingleReason(DataAllowFailReasonType failReasonType) {
            if (this.mDataAllowFailReasonSet.size() == 1) {
                return this.mDataAllowFailReasonSet.contains(failReasonType);
            }
            return false;
        }

        public void clearAllReasons() {
            this.mDataAllowFailReasonSet.clear();
        }

        public boolean isFailed() {
            return this.mDataAllowFailReasonSet.size() > 0;
        }
    }

    public enum DataAllowFailReasonType {
        NOT_ATTACHED(" - Not attached"),
        RECORD_NOT_LOADED(" - SIM not loaded"),
        ROAMING_DISABLED(" - Roaming and data roaming not enabled"),
        INVALID_PHONE_STATE(" - PhoneState is not idle"),
        CONCURRENT_VOICE_DATA_NOT_ALLOWED(" - Concurrent voice and data not allowed"),
        PS_RESTRICTED(" - mIsPsRestricted= true"),
        UNDESIRED_POWER_STATE(" - desiredPowerState= false"),
        INTERNAL_DATA_DISABLED(" - mInternalDataEnabled= false"),
        DEFAULT_DATA_UNSELECTED(" - defaultDataSelected= false");
        
        public String mFailReasonStr;

        private DataAllowFailReasonType(String reason) {
            this.mFailReasonStr = reason;
        }
    }

    private class ProvisionNotificationBroadcastReceiver extends BroadcastReceiver {
        private final String mNetworkOperator;
        private final String mProvisionUrl;

        public ProvisionNotificationBroadcastReceiver(String provisionUrl, String networkOperator) {
            this.mNetworkOperator = networkOperator;
            this.mProvisionUrl = provisionUrl;
        }

        private void setEnableFailFastMobileData(int enabled) {
            DcTracker.this.sendMessage(DcTracker.this.obtainMessage(270372, enabled, 0));
        }

        private void enableMobileProvisioning() {
            Message msg = DcTracker.this.obtainMessage(270373);
            msg.setData(Bundle.forPair("provisioningUrl", this.mProvisionUrl));
            DcTracker.this.sendMessage(msg);
        }

        public void onReceive(Context context, Intent intent) {
            DcTracker.this.mProvisioningSpinner = new ProgressDialog(context);
            DcTracker.this.mProvisioningSpinner.setTitle(this.mNetworkOperator);
            DcTracker.this.mProvisioningSpinner.setMessage(context.getText(17040653));
            DcTracker.this.mProvisioningSpinner.setIndeterminate(true);
            DcTracker.this.mProvisioningSpinner.setCancelable(true);
            DcTracker.this.mProvisioningSpinner.getWindow().setType(TelephonyEventLog.TAG_IMS_CALL_MERGE);
            DcTracker.this.mProvisioningSpinner.show();
            DcTracker.this.sendMessageDelayed(DcTracker.this.obtainMessage(270378, DcTracker.this.mProvisioningSpinner), 120000);
            DcTracker.this.setRadio(true);
            setEnableFailFastMobileData(1);
            enableMobileProvisioning();
        }
    }

    private static class RecoveryAction {
        public static final int CLEANUP = 1;
        public static final int GET_DATA_CALL_LIST = 0;
        public static final int RADIO_RESTART = 3;
        public static final int RADIO_RESTART_WITH_PROP = 4;
        public static final int REREGISTER = 2;

        private RecoveryAction() {
        }

        private static boolean isAggressiveRecovery(int value) {
            if (value == 1 || value == 2 || value == 3 || value == 4) {
                return true;
            }
            return false;
        }
    }

    private enum RetryFailures {
        ALWAYS,
        ONLY_ON_CHANGE
    }

    private static class SettingsObserver extends ContentObserver {
        private static final String TAG = "DcTracker.SettingsObserver";
        private final Context mContext;
        private final Handler mHandler;
        private final HashMap<Uri, Integer> mUriEventMap = new HashMap();

        SettingsObserver(Context context, Handler handler) {
            super(null);
            this.mContext = context;
            this.mHandler = handler;
        }

        /* Access modifiers changed, original: 0000 */
        public void observe(Uri uri, int what) {
            this.mUriEventMap.put(uri, Integer.valueOf(what));
            this.mContext.getContentResolver().registerContentObserver(uri, false, this);
        }

        /* Access modifiers changed, original: 0000 */
        public void unobserve() {
            this.mContext.getContentResolver().unregisterContentObserver(this);
        }

        public void onChange(boolean selfChange) {
            Rlog.e(TAG, "Should never be reached.");
        }

        public void onChange(boolean selfChange, Uri uri) {
            Integer what = (Integer) this.mUriEventMap.get(uri);
            if (what != null) {
                this.mHandler.obtainMessage(what.intValue()).sendToTarget();
            } else {
                Rlog.e(TAG, "No matching event to send for URI=" + uri);
            }
        }
    }

    public static class TxRxSum {
        public long rxPkts;
        public long txPkts;

        public TxRxSum() {
            reset();
        }

        public TxRxSum(long txPkts, long rxPkts) {
            this.txPkts = txPkts;
            this.rxPkts = rxPkts;
        }

        public TxRxSum(TxRxSum sum) {
            this.txPkts = sum.txPkts;
            this.rxPkts = sum.rxPkts;
        }

        public void reset() {
            this.txPkts = -1;
            this.rxPkts = -1;
        }

        public String toString() {
            return "{txSum=" + this.txPkts + " rxSum=" + this.rxPkts + "}";
        }

        public void updateTxRxSum() {
            this.txPkts = TrafficStats.getMobileTcpTxPackets();
            this.rxPkts = TrafficStats.getMobileTcpRxPackets();
        }
    }

    private static /* synthetic */ int[] -getcom-android-internal-telephony-DctConstants$StateSwitchesValues() {
        if (f18-com-android-internal-telephony-DctConstants$StateSwitchesValues != null) {
            return f18-com-android-internal-telephony-DctConstants$StateSwitchesValues;
        }
        int[] iArr = new int[State.values().length];
        try {
            iArr[State.CONNECTED.ordinal()] = 1;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[State.CONNECTING.ordinal()] = 2;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[State.DISCONNECTING.ordinal()] = 3;
        } catch (NoSuchFieldError e3) {
        }
        try {
            iArr[State.FAILED.ordinal()] = 4;
        } catch (NoSuchFieldError e4) {
        }
        try {
            iArr[State.IDLE.ordinal()] = 5;
        } catch (NoSuchFieldError e5) {
        }
        try {
            iArr[State.RETRYING.ordinal()] = 6;
        } catch (NoSuchFieldError e6) {
        }
        try {
            iArr[State.SCANNING.ordinal()] = 7;
        } catch (NoSuchFieldError e7) {
        }
        f18-com-android-internal-telephony-DctConstants$StateSwitchesValues = iArr;
        return iArr;
    }

    static {
        String str;
        if (TelBrand.IS_SHARP) {
            str = "";
        } else {
            str = "spmode.ne.jp";
        }
        APN_DCM_INTERNET_DEFAULT = str;
    }

    private void registerSettingsObserver() {
        this.mSettingsObserver.unobserve();
        String simSuffix = "";
        if (TelephonyManager.getDefault().getSimCount() > 1) {
            simSuffix = Integer.toString(this.mPhone.getSubId());
        }
        this.mSettingsObserver.observe(Global.getUriFor("data_roaming" + simSuffix), 270347);
        this.mSettingsObserver.observe(Global.getUriFor("device_provisioned"), 270379);
        this.mSettingsObserver.observe(Global.getUriFor("device_provisioning_mobile_data"), 270379);
    }

    private void onActionIntentReconnectAlarm(Intent intent) {
        String reason = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_REASON);
        String apnType = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_TYPE);
        log("onActionIntentReconnectAlarm: currSubId = " + intent.getIntExtra("subscription", -1) + " phoneSubId=" + this.mPhone.getSubId());
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        log("onActionIntentReconnectAlarm: mState=" + this.mState + " reason=" + reason + " apnType=" + apnType + " apnContext=" + apnContext + " mDataConnectionAsyncChannels=" + this.mDataConnectionAcHashMap);
        if (apnContext != null && apnContext.isEnabled()) {
            apnContext.setReason(reason);
            State apnContextState = apnContext.getState();
            log("onActionIntentReconnectAlarm: apnContext state=" + apnContextState);
            if (apnContextState == State.FAILED || apnContextState == State.IDLE) {
                log("onActionIntentReconnectAlarm: state is FAILED|IDLE, disassociate");
                DcAsyncChannel dcac = apnContext.getDcAc();
                if (dcac != null) {
                    log("onActionIntentReconnectAlarm: tearDown apnContext=" + apnContext);
                    dcac.tearDown(apnContext, "", null);
                }
                apnContext.setDataConnectionAc(null);
                apnContext.setState(State.IDLE);
            } else {
                log("onActionIntentReconnectAlarm: keep associated");
            }
            sendMessage(obtainMessage(270339, apnContext));
            apnContext.setReconnectIntent(null);
        }
    }

    private void onActionIntentDataStallAlarm(Intent intent) {
        Message msg = obtainMessage(270353, intent.getAction());
        msg.arg1 = intent.getIntExtra(DATA_STALL_ALARM_TAG_EXTRA, 0);
        sendMessage(msg);
    }

    public DcTracker(Phone phone) {
        this.LOG_TAG = "DCT";
        this.isCleanupRequired = new AtomicBoolean(false);
        this.mDataEnabledLock = new Object();
        this.mInternalDataEnabled = true;
        this.mUserDataEnabled = true;
        this.mUserDataEnabledDun = true;
        this.mPdpType = "IP";
        this.mOemKddiDunApn = null;
        this.mConnectingApnCarNavi = false;
        this.mDnsesCarNavi = new String[2];
        this.mDnsAddressCarNavi = new String[2];
        this.mApnCarNavi = null;
        this.mEnableApnCarNavi = false;
        this.mDisConnectingApnCarNavi = false;
        this.mRequestedApnType = "default";
        this.RADIO_RESET_PROPERTY = "gsm.radioreset";
        this.mPrioritySortedApnContexts = new PriorityQueue(5, new Comparator<ApnContext>() {
            public int compare(ApnContext c1, ApnContext c2) {
                return c2.priority - c1.priority;
            }
        });
        this.mAllApnSettings = null;
        this.mPreferredApn = null;
        this.mIsPsRestricted = false;
        this.mEmergencyApn = null;
        this.mIsDisposed = false;
        this.mIsProvisioning = false;
        this.mProvisioningUrl = null;
        this.mProvisioningApnAlarmIntent = null;
        this.mProvisioningApnAlarmTag = (int) SystemClock.elapsedRealtime();
        this.mReplyAc = new AsyncChannel();
        this.mIntentReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                boolean enabled = true;
                boolean z = false;
                String action = intent.getAction();
                if (action.equals("android.intent.action.SCREEN_ON")) {
                    DcTracker.this.log("screen on");
                    DcTracker.this.mIsScreenOn = true;
                    DcTracker.this.stopNetStatPoll();
                    DcTracker.this.startNetStatPoll();
                    DcTracker.this.restartDataStallAlarm();
                } else if (action.equals("android.intent.action.SCREEN_OFF")) {
                    DcTracker.this.log("screen off");
                    DcTracker.this.mIsScreenOn = false;
                    DcTracker.this.stopNetStatPoll();
                    DcTracker.this.startNetStatPoll();
                    DcTracker.this.restartDataStallAlarm();
                } else if (action.startsWith(DcTracker.INTENT_RECONNECT_ALARM)) {
                    DcTracker.this.log("Reconnect alarm. Previous state was " + DcTracker.this.mState);
                    DcTracker.this.onActionIntentReconnectAlarm(intent);
                } else if (action.equals(DcTracker.INTENT_DATA_STALL_ALARM)) {
                    DcTracker.this.log("Data stall alarm");
                    DcTracker.this.onActionIntentDataStallAlarm(intent);
                } else if (action.equals(DcTracker.INTENT_PROVISIONING_APN_ALARM)) {
                    DcTracker.this.log("Provisioning apn alarm");
                    DcTracker.this.onActionIntentProvisioningApnAlarm(intent);
                } else if (action.equals("android.net.wifi.STATE_CHANGE")) {
                    NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                    DcTracker dcTracker = DcTracker.this;
                    if (networkInfo != null) {
                        z = networkInfo.isConnected();
                    }
                    dcTracker.mIsWifiConnected = z;
                    DcTracker.this.log("NETWORK_STATE_CHANGED_ACTION: mIsWifiConnected=" + DcTracker.this.mIsWifiConnected);
                } else if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
                    DcTracker.this.log("Wifi state changed");
                    if (intent.getIntExtra("wifi_state", 4) != 3) {
                        enabled = false;
                    }
                    if (!enabled) {
                        DcTracker.this.mIsWifiConnected = false;
                    }
                    DcTracker.this.log("WIFI_STATE_CHANGED_ACTION: enabled=" + enabled + " mIsWifiConnected=" + DcTracker.this.mIsWifiConnected);
                } else {
                    DcTracker.this.log("onReceive: Unknown action=" + action);
                }
            }
        };
        this.mPollNetStat = new Runnable() {
            public void run() {
                DcTracker.this.updateDataActivity();
                if (DcTracker.this.mIsScreenOn) {
                    DcTracker.this.mNetStatPollPeriod = Global.getInt(DcTracker.this.mResolver, "pdp_watchdog_poll_interval_ms", 1000);
                } else {
                    DcTracker.this.mNetStatPollPeriod = Global.getInt(DcTracker.this.mResolver, "pdp_watchdog_long_poll_interval_ms", 600000);
                }
                if (DcTracker.this.mNetStatPollEnabled) {
                    DcTracker.this.mDataConnectionTracker.postDelayed(this, (long) DcTracker.this.mNetStatPollPeriod);
                }
            }
        };
        this.mOnSubscriptionsChangedListener = new OnSubscriptionsChangedListener() {
            public final AtomicInteger mPreviousSubId = new AtomicInteger(-1);

            public void onSubscriptionsChanged() {
                DcTracker.this.log("SubscriptionListener.onSubscriptionInfoChanged");
                int subId = DcTracker.this.mPhone.getSubId();
                if (DcTracker.this.mSubscriptionManager.isActiveSubId(subId)) {
                    DcTracker.this.registerSettingsObserver();
                    DcTracker.this.applyUnProvisionedSimDetected();
                }
                if (this.mPreviousSubId.getAndSet(subId) != subId && DcTracker.this.mSubscriptionManager.isActiveSubId(subId)) {
                    DcTracker.this.onRecordsLoadedOrSubIdChanged();
                }
            }
        };
        this.mDisconnectAllCompleteMsgList = new ArrayList();
        this.mAllDataDisconnectedRegistrants = new RegistrantList();
        this.mIccRecords = new AtomicReference();
        this.mActivity = Activity.NONE;
        this.mState = State.IDLE;
        this.mNetStatPollEnabled = false;
        this.mDataStallTxRxSum = new TxRxSum(0, 0);
        this.mDataStallAlarmTag = (int) SystemClock.elapsedRealtime();
        this.mDataStallAlarmIntent = null;
        this.mNoRecvPollCount = 0;
        this.mDataStallDetectionEnabled = true;
        this.mFailFast = false;
        this.mInVoiceCall = false;
        this.mIsWifiConnected = false;
        this.mReconnectIntent = null;
        this.mAutoAttachOnCreationConfig = false;
        this.mAutoAttachOnCreation = new AtomicBoolean(false);
        this.mIsScreenOn = true;
        this.mMvnoMatched = false;
        this.mUniqueIdGenerator = new AtomicInteger(0);
        this.mDataConnections = new HashMap();
        this.mDataConnectionAcHashMap = new HashMap();
        this.mApnToDataConnectionId = new HashMap();
        this.mApnContexts = new ConcurrentHashMap();
        this.mIsPhysicalLinkUp = false;
        this.mApnContextsById = new SparseArray();
        this.mDisconnectPendingCount = 0;
        this.mRedirectUrl = null;
        this.mColdSimDetected = false;
        this.mOutOfCreditSimDetected = false;
        this.redirectApnContextSet = new HashSet();
        this.mReregisterOnReconnectFailure = false;
        this.RetryEnableApn = 0;
        this.mCanSetPreferApn = false;
        this.mAttached = new AtomicBoolean(false);
        this.mImsRegistrationState = false;
        this.mPhone = phone;
        log("DCT.constructor");
        this.mResolver = this.mPhone.getContext().getContentResolver();
        this.mUiccController = UiccController.getInstance();
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
        this.mAutoAttachOnCreation.set(PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext()).getBoolean(Phone.DATA_DISABLED_ON_BOOT_KEY, false));
        this.mSubscriptionManager = SubscriptionManager.from(this.mPhone.getContext());
        this.mSubscriptionManager.addOnSubscriptionsChangedListener(this.mOnSubscriptionsChangedListener);
        HandlerThread dcHandlerThread = new HandlerThread("DcHandlerThread");
        dcHandlerThread.start();
        Handler dcHandler = new Handler(dcHandlerThread.getLooper());
        this.mDcc = DcController.makeDcc(this.mPhone, this, dcHandler);
        this.mDcTesterFailBringUpAll = new DcTesterFailBringUpAll(this.mPhone, dcHandler);
        this.mDataConnectionTracker = this;
        registerForAllEvents();
        update();
        this.mApnObserver = new ApnChangeObserver();
        phone.getContext().getContentResolver().registerContentObserver(Carriers.CONTENT_URI, true, this.mApnObserver);
        initApnContexts();
        for (ApnContext apnContext : this.mApnContexts.values()) {
            filter = new IntentFilter();
            filter.addAction("com.android.internal.telephony.data-reconnect." + apnContext.getApnType());
            this.mPhone.getContext().registerReceiver(this.mIntentReceiver, filter, null, this.mPhone);
        }
        initEmergencyApnSetting();
        addEmergencyApnSetting();
        this.mProvisionActionName = "com.android.internal.telephony.PROVISION" + phone.getPhoneId();
        this.mSettingsObserver = new SettingsObserver(this.mPhone.getContext(), this);
        registerSettingsObserver();
        FastDormancy fastDormancy = new FastDormancy(this.mPhone.mCi, this.mPhone.getContext());
    }

    public DcTracker() {
        this.LOG_TAG = "DCT";
        this.isCleanupRequired = new AtomicBoolean(false);
        this.mDataEnabledLock = new Object();
        this.mInternalDataEnabled = true;
        this.mUserDataEnabled = true;
        this.mUserDataEnabledDun = true;
        this.mPdpType = "IP";
        this.mOemKddiDunApn = null;
        this.mConnectingApnCarNavi = false;
        this.mDnsesCarNavi = new String[2];
        this.mDnsAddressCarNavi = new String[2];
        this.mApnCarNavi = null;
        this.mEnableApnCarNavi = false;
        this.mDisConnectingApnCarNavi = false;
        this.mRequestedApnType = "default";
        this.RADIO_RESET_PROPERTY = "gsm.radioreset";
        this.mPrioritySortedApnContexts = new PriorityQueue(5, /* anonymous class already generated */);
        this.mAllApnSettings = null;
        this.mPreferredApn = null;
        this.mIsPsRestricted = false;
        this.mEmergencyApn = null;
        this.mIsDisposed = false;
        this.mIsProvisioning = false;
        this.mProvisioningUrl = null;
        this.mProvisioningApnAlarmIntent = null;
        this.mProvisioningApnAlarmTag = (int) SystemClock.elapsedRealtime();
        this.mReplyAc = new AsyncChannel();
        this.mIntentReceiver = /* anonymous class already generated */;
        this.mPollNetStat = /* anonymous class already generated */;
        this.mOnSubscriptionsChangedListener = /* anonymous class already generated */;
        this.mDisconnectAllCompleteMsgList = new ArrayList();
        this.mAllDataDisconnectedRegistrants = new RegistrantList();
        this.mIccRecords = new AtomicReference();
        this.mActivity = Activity.NONE;
        this.mState = State.IDLE;
        this.mNetStatPollEnabled = false;
        this.mDataStallTxRxSum = new TxRxSum(0, 0);
        this.mDataStallAlarmTag = (int) SystemClock.elapsedRealtime();
        this.mDataStallAlarmIntent = null;
        this.mNoRecvPollCount = 0;
        this.mDataStallDetectionEnabled = true;
        this.mFailFast = false;
        this.mInVoiceCall = false;
        this.mIsWifiConnected = false;
        this.mReconnectIntent = null;
        this.mAutoAttachOnCreationConfig = false;
        this.mAutoAttachOnCreation = new AtomicBoolean(false);
        this.mIsScreenOn = true;
        this.mMvnoMatched = false;
        this.mUniqueIdGenerator = new AtomicInteger(0);
        this.mDataConnections = new HashMap();
        this.mDataConnectionAcHashMap = new HashMap();
        this.mApnToDataConnectionId = new HashMap();
        this.mApnContexts = new ConcurrentHashMap();
        this.mIsPhysicalLinkUp = false;
        this.mApnContextsById = new SparseArray();
        this.mDisconnectPendingCount = 0;
        this.mRedirectUrl = null;
        this.mColdSimDetected = false;
        this.mOutOfCreditSimDetected = false;
        this.redirectApnContextSet = new HashSet();
        this.mReregisterOnReconnectFailure = false;
        this.RetryEnableApn = 0;
        this.mCanSetPreferApn = false;
        this.mAttached = new AtomicBoolean(false);
        this.mImsRegistrationState = false;
        this.mAlarmManager = null;
        this.mCm = null;
        this.mPhone = null;
        this.mUiccController = null;
        this.mDataConnectionTracker = null;
        this.mProvisionActionName = null;
        this.mSettingsObserver = new SettingsObserver(null, this);
    }

    public void registerServiceStateTrackerEvents() {
        this.mPhone.getServiceStateTracker().registerForDataConnectionAttached(this, 270352, null);
        this.mPhone.getServiceStateTracker().registerForDataConnectionDetached(this, 270345, null);
        this.mPhone.getServiceStateTracker().registerForDataRoamingOn(this, 270347, null);
        this.mPhone.getServiceStateTracker().registerForDataRoamingOff(this, 270348, null);
        this.mPhone.getServiceStateTracker().registerForPsRestrictedEnabled(this, 270358, null);
        this.mPhone.getServiceStateTracker().registerForPsRestrictedDisabled(this, 270359, null);
        this.mPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(this, 270377, null);
    }

    public void unregisterServiceStateTrackerEvents() {
        this.mPhone.getServiceStateTracker().unregisterForDataConnectionAttached(this);
        this.mPhone.getServiceStateTracker().unregisterForDataConnectionDetached(this);
        this.mPhone.getServiceStateTracker().unregisterForDataRoamingOn(this);
        this.mPhone.getServiceStateTracker().unregisterForDataRoamingOff(this);
        this.mPhone.getServiceStateTracker().unregisterForPsRestrictedEnabled(this);
        this.mPhone.getServiceStateTracker().unregisterForPsRestrictedDisabled(this);
        this.mPhone.getServiceStateTracker().unregisterForDataRegStateOrRatChanged(this);
    }

    private void registerForAllEvents() {
        this.mPhone.mCi.registerForAvailable(this, 270337, null);
        this.mPhone.mCi.registerForOffOrNotAvailable(this, 270342, null);
        this.mPhone.mCi.registerForDataNetworkStateChanged(this, 270340, null);
        this.mPhone.getCallTracker().registerForVoiceCallEnded(this, 270344, null);
        this.mPhone.getCallTracker().registerForVoiceCallStarted(this, 270343, null);
        registerServiceStateTrackerEvents();
    }

    public void dispose() {
        log("DCT.dispose");
        if (this.mProvisionBroadcastReceiver != null) {
            this.mPhone.getContext().unregisterReceiver(this.mProvisionBroadcastReceiver);
            this.mProvisionBroadcastReceiver = null;
        }
        if (this.mProvisioningSpinner != null) {
            this.mProvisioningSpinner.dismiss();
            this.mProvisioningSpinner = null;
        }
        cleanUpAllConnections(true, null);
        for (DcAsyncChannel dcac : this.mDataConnectionAcHashMap.values()) {
            dcac.disconnect();
        }
        this.mDataConnectionAcHashMap.clear();
        this.mIsDisposed = true;
        this.mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        this.mUiccController.unregisterForIccChanged(this);
        this.mSettingsObserver.unobserve();
        this.mSubscriptionManager.removeOnSubscriptionsChangedListener(this.mOnSubscriptionsChangedListener);
        this.mDcc.dispose();
        this.mDcTesterFailBringUpAll.dispose();
        this.mPhone.getContext().getContentResolver().unregisterContentObserver(this.mApnObserver);
        this.mApnContexts.clear();
        this.mApnContextsById.clear();
        this.mPrioritySortedApnContexts.clear();
        unregisterForAllEvents();
        destroyDataConnections();
    }

    private void unregisterForAllEvents() {
        this.mPhone.mCi.unregisterForAvailable(this);
        this.mPhone.mCi.unregisterForOffOrNotAvailable(this);
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.unregisterForRecordsLoaded(this);
            this.mIccRecords.set(null);
        }
        this.mPhone.mCi.unregisterForDataNetworkStateChanged(this);
        this.mPhone.getCallTracker().unregisterForVoiceCallEnded(this);
        this.mPhone.getCallTracker().unregisterForVoiceCallStarted(this);
        unregisterServiceStateTrackerEvents();
    }

    private void onResetDone(AsyncResult ar) {
        log("EVENT_RESET_DONE");
        String str = null;
        if (ar.userObj instanceof String) {
            str = ar.userObj;
        }
        gotoIdleAndNotifyDataConnection(str);
    }

    public void setDataEnabled(boolean enable) {
        Message msg = obtainMessage(270366);
        msg.arg1 = enable ? 1 : 0;
        log("setDataEnabled: sendMessage: enable=" + enable);
        sendMessage(msg);
    }

    private void onSetUserDataEnabled(boolean enabled) {
        int i = 1;
        synchronized (this.mDataEnabledLock) {
            if (this.mUserDataEnabled != enabled) {
                this.mUserDataEnabled = enabled;
                ContentResolver contentResolver;
                String str;
                if (TelephonyManager.getDefault().getSimCount() == 1) {
                    contentResolver = this.mResolver;
                    str = "mobile_data";
                    if (!enabled) {
                        i = 0;
                    }
                    Global.putInt(contentResolver, str, i);
                } else {
                    int phoneSubId = this.mPhone.getSubId();
                    contentResolver = this.mResolver;
                    str = "mobile_data" + phoneSubId;
                    if (!enabled) {
                        i = 0;
                    }
                    Global.putInt(contentResolver, str, i);
                }
                if (!getDataOnRoamingEnabled() && this.mPhone.getServiceState().getDataRoaming()) {
                    if (enabled) {
                        notifyOffApnsOfAvailability(PhoneInternalInterface.REASON_ROAMING_ON);
                    } else {
                        notifyOffApnsOfAvailability(PhoneInternalInterface.REASON_DATA_DISABLED);
                    }
                }
                updateOemDataSettings();
                if (enabled) {
                    onTrySetupData(PhoneInternalInterface.REASON_DATA_ENABLED);
                } else {
                    onCleanUpAllConnections(PhoneInternalInterface.REASON_DATA_SPECIFIC_DISABLED);
                }
            }
        }
    }

    private void onDeviceProvisionedChange() {
        if (getDataEnabled()) {
            this.mUserDataEnabled = true;
            onTrySetupData(PhoneInternalInterface.REASON_DATA_ENABLED);
            return;
        }
        this.mUserDataEnabled = false;
        onCleanUpAllConnections(PhoneInternalInterface.REASON_DATA_SPECIFIC_DISABLED);
    }

    public long getSubId() {
        return (long) this.mPhone.getSubId();
    }

    public Activity getActivity() {
        return this.mActivity;
    }

    private void setActivity(Activity activity) {
        log("setActivity = " + activity);
        this.mActivity = activity;
        this.mPhone.notifyDataActivity();
    }

    public void requestNetwork(NetworkRequest networkRequest, LocalLog log) {
        ApnContext apnContext = (ApnContext) this.mApnContextsById.get(ApnContext.apnIdForNetworkRequest(networkRequest));
        log.log("DcTracker.requestNetwork for " + networkRequest + " found " + apnContext);
        if (apnContext != null) {
            apnContext.incRefCount(log);
        }
    }

    public void releaseNetwork(NetworkRequest networkRequest, LocalLog log) {
        ApnContext apnContext = (ApnContext) this.mApnContextsById.get(ApnContext.apnIdForNetworkRequest(networkRequest));
        log.log("DcTracker.releaseNetwork for " + networkRequest + " found " + apnContext);
        if (apnContext != null) {
            apnContext.decRefCount(log);
        }
    }

    public boolean isApnSupported(String name) {
        if (name == null) {
            loge("isApnSupported: name=null");
            return false;
        } else if (((ApnContext) this.mApnContexts.get(name)) != null) {
            return true;
        } else {
            loge("Request for unsupported mobile name: " + name);
            return false;
        }
    }

    private boolean isColdSimDetected() {
        int subId = this.mPhone.getSubId();
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            SubscriptionInfo subInfo = this.mSubscriptionManager.getActiveSubscriptionInfo(subId);
            if (subInfo != null && subInfo.getSimProvisioningStatus() == 1) {
                log("Cold Sim Detected on SubId: " + subId);
                return true;
            }
        }
        return false;
    }

    private boolean isOutOfCreditSimDetected() {
        int subId = this.mPhone.getSubId();
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            SubscriptionInfo subInfo = this.mSubscriptionManager.getActiveSubscriptionInfo(subId);
            if (subInfo != null && subInfo.getSimProvisioningStatus() == 2) {
                log("Out Of Credit Sim Detected on SubId: " + subId);
                return true;
            }
        }
        return false;
    }

    public int getApnPriority(String name) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(name);
        if (apnContext == null) {
            loge("Request for unsupported mobile name: " + name);
        }
        return apnContext.priority;
    }

    private void setRadio(boolean on) {
        try {
            Stub.asInterface(ServiceManager.checkService("phone")).setRadio(on);
        } catch (Exception e) {
        }
    }

    public boolean isDataPossible(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext == null) {
            return false;
        }
        boolean apnTypePossible = (apnContext.isEnabled() && apnContext.getState() == State.FAILED) ? false : true;
        boolean possible = !apnContext.getApnType().equals("emergency") ? isDataAllowed(null) : true ? apnTypePossible : false;
        if ((apnContext.getApnType().equals("default") || apnContext.getApnType().equals("ia")) && this.mPhone.getServiceState().getRilDataRadioTechnology() == 18) {
            log("Default data call activation not possible in iwlan.");
            possible = false;
        }
        return possible;
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        log("finalize");
    }

    private ApnContext addApnContext(String type, NetworkConfig networkConfig) {
        ApnContext apnContext = new ApnContext(this.mPhone, type, this.LOG_TAG, networkConfig, this);
        this.mApnContexts.put(type, apnContext);
        this.mApnContextsById.put(ApnContext.apnIdForApnName(type), apnContext);
        this.mPrioritySortedApnContexts.add(apnContext);
        return apnContext;
    }

    private void initApnContexts() {
        log("initApnContexts: E");
        for (String networkConfigString : this.mPhone.getContext().getResources().getStringArray(17235987)) {
            ApnContext apnContext;
            NetworkConfig networkConfig = new NetworkConfig(networkConfigString);
            switch (networkConfig.type) {
                case 0:
                    apnContext = addApnContext("default", networkConfig);
                    break;
                case 2:
                    apnContext = addApnContext("mms", networkConfig);
                    break;
                case 3:
                    apnContext = addApnContext("supl", networkConfig);
                    break;
                case 4:
                    apnContext = addApnContext("dun", networkConfig);
                    break;
                case 5:
                    apnContext = addApnContext("hipri", networkConfig);
                    break;
                case 10:
                    apnContext = addApnContext("fota", networkConfig);
                    break;
                case 11:
                    apnContext = addApnContext("ims", networkConfig);
                    break;
                case 12:
                    apnContext = addApnContext("cbs", networkConfig);
                    break;
                case 14:
                    apnContext = addApnContext("ia", networkConfig);
                    break;
                case 15:
                    apnContext = addApnContext("emergency", networkConfig);
                    break;
                default:
                    log("initApnContexts: skipping unknown type=" + networkConfig.type);
                    continue;
            }
            log("initApnContexts: apnContext=" + apnContext);
        }
    }

    public LinkProperties getLinkProperties(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext != null) {
            DcAsyncChannel dcac = apnContext.getDcAc();
            if (dcac != null) {
                log("return link properites for " + apnType);
                return dcac.getLinkPropertiesSync();
            }
        }
        log("return new LinkProperties");
        return new LinkProperties();
    }

    public NetworkCapabilities getNetworkCapabilities(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext != null) {
            DcAsyncChannel dataConnectionAc = apnContext.getDcAc();
            if (dataConnectionAc != null) {
                log("get active pdp is not null, return NetworkCapabilities for " + apnType);
                return dataConnectionAc.getNetworkCapabilitiesSync();
            }
        }
        log("return new NetworkCapabilities");
        return new NetworkCapabilities();
    }

    public String[] getActiveApnTypes() {
        log("get all active apn types");
        ArrayList<String> result = new ArrayList();
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (this.mAttached.get() && apnContext.isReady()) {
                result.add(apnContext.getApnType());
            }
        }
        return (String[]) result.toArray(new String[0]);
    }

    public String getActiveApnString(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext != null) {
            ApnSetting apnSetting = apnContext.getApnSetting();
            if (apnSetting != null) {
                return apnSetting.apn;
            }
        }
        return null;
    }

    public State getState(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext != null) {
            return apnContext.getState();
        }
        return State.FAILED;
    }

    private boolean isProvisioningApn(String apnType) {
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
        if (apnContext != null) {
            return apnContext.isProvisioningApn();
        }
        return false;
    }

    public State getOverallState() {
        boolean isConnecting = false;
        boolean isFailed = true;
        boolean isAnyEnabled = false;
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (apnContext.isEnabled() && !(TelBrand.IS_DCM && apnContext.getApnType().equals("ims"))) {
                isAnyEnabled = true;
                switch (-getcom-android-internal-telephony-DctConstants$StateSwitchesValues()[apnContext.getState().ordinal()]) {
                    case 1:
                    case 3:
                        return State.CONNECTED;
                    case 2:
                    case 6:
                        isConnecting = true;
                        isFailed = false;
                        break;
                    case 5:
                    case 7:
                        isFailed = false;
                        break;
                    default:
                        isAnyEnabled = true;
                        break;
                }
            }
        }
        if (!isAnyEnabled) {
            return State.IDLE;
        }
        if (isConnecting) {
            return State.CONNECTING;
        }
        if (isFailed) {
            return State.FAILED;
        }
        return State.IDLE;
    }

    public boolean getAnyDataEnabled() {
        if (!isDataEnabled(true)) {
            return false;
        }
        DataAllowFailReason failureReason = new DataAllowFailReason();
        if (isDataAllowed(failureReason)) {
            for (ApnContext apnContext : this.mApnContexts.values()) {
                if (isDataAllowedForApn(apnContext)) {
                    return true;
                }
            }
            return false;
        }
        log(failureReason.getDataAllowFailReason());
        return false;
    }

    public boolean getAnyDataEnabled(boolean checkUserDataEnabled) {
        if (!isDataEnabled(checkUserDataEnabled)) {
            return false;
        }
        DataAllowFailReason failureReason = new DataAllowFailReason();
        if (isDataAllowed(failureReason)) {
            for (ApnContext apnContext : this.mApnContexts.values()) {
                if (isDataAllowedForApn(apnContext)) {
                    return true;
                }
            }
            return false;
        }
        log(failureReason.getDataAllowFailReason());
        return false;
    }

    private boolean isDataEnabled(boolean checkUserDataEnabled) {
        synchronized (this.mDataEnabledLock) {
            boolean z = (!this.mInternalDataEnabled || ((checkUserDataEnabled && !this.mUserDataEnabled) || !this.mUserDataEnabledDun)) ? false : checkUserDataEnabled ? sPolicyDataEnabled : true;
            if (z) {
                return true;
            }
            return false;
        }
    }

    private boolean isDataAllowedForApn(ApnContext apnContext) {
        if ((!apnContext.getApnType().equals("default") && !apnContext.getApnType().equals("ia")) || this.mPhone.getServiceState().getRilDataRadioTechnology() != 18) {
            return apnContext.isReady();
        }
        log("Default data call activation not allowed in iwlan.");
        return false;
    }

    private void onDataConnectionDetached() {
        log("onDataConnectionDetached: stop polling and notify detached");
        stopNetStatPoll();
        stopDataStallAlarm();
        notifyDataConnection(PhoneInternalInterface.REASON_DATA_DETACHED);
        this.mAttached.set(false);
    }

    private void onDataConnectionAttached() {
        log("onDataConnectionAttached");
        this.mAttached.set(true);
        if (getOverallState() == State.CONNECTED) {
            log("onDataConnectionAttached: start polling notify attached");
            startNetStatPoll();
            startDataStallAlarm(false);
            notifyDataConnection(PhoneInternalInterface.REASON_DATA_ATTACHED);
        } else {
            notifyOffApnsOfAvailability(PhoneInternalInterface.REASON_DATA_ATTACHED);
        }
        if (this.mAutoAttachOnCreationConfig) {
            this.mAutoAttachOnCreation.set(true);
        }
        setupDataOnConnectableApns(PhoneInternalInterface.REASON_DATA_ATTACHED);
    }

    /* Access modifiers changed, original: protected */
    public boolean isNvSubscription() {
        return false;
    }

    private boolean isDataAllowed(DataAllowFailReason failureReason) {
        boolean internalDataEnabled;
        boolean z;
        boolean z2 = true;
        synchronized (this.mDataEnabledLock) {
            internalDataEnabled = this.mInternalDataEnabled;
        }
        boolean attachedState = this.mAttached.get();
        boolean desiredPowerState = this.mPhone.getServiceStateTracker().getDesiredPowerState();
        if (this.mPhone.getServiceState().getRilDataRadioTechnology() == 18) {
            desiredPowerState = true;
        }
        IccRecords r = (IccRecords) this.mIccRecords.get();
        boolean recordsLoaded = false;
        boolean subscriptionFromNv = isNvSubscription();
        if (r != null) {
            recordsLoaded = r.getRecordsLoaded();
            if (!recordsLoaded) {
                log("isDataAllowed getRecordsLoaded=" + recordsLoaded);
            }
        }
        boolean defaultDataSelected = SubscriptionManager.isValidSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId());
        PhoneConstants.State state = PhoneConstants.State.IDLE;
        if (this.mPhone.getCallTracker() != null) {
            state = this.mPhone.getCallTracker().getState();
        }
        if (failureReason != null) {
            failureReason.clearAllReasons();
        }
        if (attachedState) {
            z = true;
        } else {
            z = this.mAutoAttachOnCreation.get();
        }
        if (!z) {
            if (failureReason == null) {
                return false;
            }
            failureReason.addDataAllowFailReason(DataAllowFailReasonType.NOT_ATTACHED);
        }
        if (recordsLoaded) {
            subscriptionFromNv = true;
        }
        if (!subscriptionFromNv) {
            if (failureReason == null) {
                return false;
            }
            failureReason.addDataAllowFailReason(DataAllowFailReasonType.RECORD_NOT_LOADED);
        }
        if (!(state == PhoneConstants.State.IDLE || this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed())) {
            if (failureReason == null) {
                return false;
            }
            failureReason.addDataAllowFailReason(DataAllowFailReasonType.INVALID_PHONE_STATE);
            failureReason.addDataAllowFailReason(DataAllowFailReasonType.CONCURRENT_VOICE_DATA_NOT_ALLOWED);
        }
        if (!internalDataEnabled) {
            if (failureReason == null) {
                return false;
            }
            failureReason.addDataAllowFailReason(DataAllowFailReasonType.INTERNAL_DATA_DISABLED);
        }
        if (!defaultDataSelected) {
            if (failureReason == null) {
                return false;
            }
            failureReason.addDataAllowFailReason(DataAllowFailReasonType.DEFAULT_DATA_UNSELECTED);
        }
        if (this.mPhone.getServiceState().getDataRoaming() && !getDataOnRoamingEnabled()) {
            if (failureReason == null) {
                return false;
            }
            failureReason.addDataAllowFailReason(DataAllowFailReasonType.ROAMING_DISABLED);
        }
        if (this.mIsPsRestricted) {
            if (failureReason == null) {
                return false;
            }
            failureReason.addDataAllowFailReason(DataAllowFailReasonType.PS_RESTRICTED);
        }
        if (!desiredPowerState) {
            if (failureReason == null) {
                return false;
            }
            failureReason.addDataAllowFailReason(DataAllowFailReasonType.UNDESIRED_POWER_STATE);
        }
        if (failureReason != null && failureReason.isFailed()) {
            z2 = false;
        }
        return z2;
    }

    /* Access modifiers changed, original: protected */
    public void setupDataOnConnectableApns(String reason) {
        setupDataOnConnectableApns(reason, RetryFailures.ALWAYS);
    }

    private void setupDataOnConnectableApns(String reason, RetryFailures retryFailures) {
        StringBuilder sb = new StringBuilder(120);
        for (ApnContext apnContext : this.mPrioritySortedApnContexts) {
            sb.append(apnContext.getApnType());
            sb.append(":[state=");
            sb.append(apnContext.getState());
            sb.append(",enabled=");
            sb.append(apnContext.isEnabled());
            sb.append("] ");
        }
        log("setupDataOnConnectableApns: " + reason + " " + sb);
        for (ApnContext apnContext2 : this.mPrioritySortedApnContexts) {
            ArrayList waitingApns = null;
            if (apnContext2.getState() == State.FAILED || apnContext2.getState() == State.SCANNING) {
                if (!TelBrand.IS_DCM || apnContext2.getState() != State.FAILED || !PhoneInternalInterface.REASON_VOICE_CALL_ENDED.equals(reason)) {
                    if (retryFailures == RetryFailures.ALWAYS) {
                        apnContext2.releaseDataConnection(reason);
                    } else if (apnContext2.isConcurrentVoiceAndDataAllowed() || !this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                        int radioTech = this.mPhone.getServiceState().getRilDataRadioTechnology();
                        ArrayList<ApnSetting> originalApns = apnContext2.getWaitingApns();
                        if (!(originalApns == null || originalApns.isEmpty())) {
                            waitingApns = buildWaitingApns(apnContext2.getApnType(), radioTech);
                            if (originalApns.size() != waitingApns.size() || !originalApns.containsAll(waitingApns)) {
                                apnContext2.releaseDataConnection(reason);
                            }
                        }
                    } else {
                        apnContext2.releaseDataConnection(reason);
                    }
                }
            }
            if (apnContext2.isConnectable()) {
                log("isConnectable() call trySetupData");
                apnContext2.setReason(reason);
                trySetupData(apnContext2, waitingApns);
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isEmergency() {
        boolean result;
        synchronized (this.mDataEnabledLock) {
            result = !this.mPhone.isInEcm() ? this.mPhone.isInEmergencyCall() : true;
        }
        log("isEmergency: result=" + result);
        return result;
    }

    private boolean trySetupData(ApnContext apnContext) {
        return trySetupData(apnContext, null);
    }

    private boolean trySetupData(ApnContext apnContext, ArrayList<ApnSetting> waitingApns) {
        log("trySetupData for type:" + apnContext.getApnType() + " due to " + apnContext.getReason() + ", mIsPsRestricted=" + this.mIsPsRestricted);
        apnContext.requestLog("trySetupData due to " + apnContext.getReason());
        if (TelBrand.IS_DCM && !apnContext.getApnType().equals("ims")) {
            String decryptState = SystemProperties.get("vold.decrypt", "0");
            log("Get decryptState is: " + decryptState);
            if (decryptState.equals("trigger_restart_min_framework")) {
                log("Stop trySetupData while phone in encrypt view !!!");
                return false;
            }
        }
        if (this.mPhone.getSimulatedRadioControl() != null) {
            apnContext.setState(State.CONNECTED);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            log("trySetupData: X We're on the simulator; assuming connected retValue=true");
            return true;
        }
        boolean isDataAllowed;
        boolean isEmergencyApn = apnContext.getApnType().equals("emergency");
        ServiceStateTracker sst = this.mPhone.getServiceStateTracker();
        boolean checkUserDataEnabled = ApnSetting.isMeteredApnType(apnContext.getApnType(), this.mPhone.getContext(), this.mPhone.getSubId(), this.mPhone.getServiceState().getDataRoaming());
        DataAllowFailReason failureReason = new DataAllowFailReason();
        if (isDataAllowed(failureReason)) {
            isDataAllowed = true;
        } else {
            isDataAllowed = failureReason.isFailForSingleReason(DataAllowFailReasonType.ROAMING_DISABLED) ? !ApnSetting.isMeteredApnType(apnContext.getApnType(), this.mPhone.getContext(), this.mPhone.getSubId(), this.mPhone.getServiceState().getDataRoaming()) : false;
        }
        if (apnContext.getApnType().equals("mms")) {
            PersistableBundle pb = ((CarrierConfigManager) this.mPhone.getContext().getSystemService("carrier_config")).getConfigForSubId(this.mPhone.getSubId());
            if (pb != null) {
                if (checkUserDataEnabled) {
                    checkUserDataEnabled = !pb.getBoolean("config_enable_mms_with_mobile_data_off");
                } else {
                    checkUserDataEnabled = false;
                }
            }
        }
        if (!apnContext.isConnectable() || (!(isEmergencyApn || (isDataAllowed && isDataAllowedForApn(apnContext) && isDataEnabled(checkUserDataEnabled) && !isEmergency())) || this.mColdSimDetected)) {
            if (TelBrand.IS_KDDI && (apnContext.getState() == State.IDLE || apnContext.getState() == State.SCANNING)) {
                if (this.mEnableApnCarNavi && this.mPhone.getServiceState().getRadioTechnology() == 0) {
                    sendOemKddiFailCauseBroadcast(DcFailCause.SIGNAL_LOST, apnContext);
                    changeMode(false, "", "", "", 0, "", "", "", "");
                    this.mEnableApnCarNavi = false;
                } else if (this.mState != State.DISCONNECTING) {
                    sendOemKddiFailCauseBroadcast(DcFailCause.CUST_NOT_READY_FOR_DATA, apnContext);
                }
            }
            if (!apnContext.getApnType().equals("default") && apnContext.isConnectable()) {
                this.mPhone.notifyDataConnectionFailed(apnContext.getReason(), apnContext.getApnType());
            }
            notifyOffApnsOfAvailability(apnContext.getReason());
            StringBuilder str = new StringBuilder();
            str.append("trySetupData failed. apnContext = [type=").append(apnContext.getApnType()).append(", mState=").append(apnContext.getState()).append(", mDataEnabled=").append(apnContext.isEnabled()).append(", mDependencyMet=").append(apnContext.getDependencyMet()).append("] ");
            if (!apnContext.isConnectable()) {
                str.append("isConnectable = false. ");
            }
            if (!isDataAllowed) {
                str.append("data not allowed: ").append(failureReason.getDataAllowFailReason()).append(". ");
            }
            if (!isDataAllowedForApn(apnContext)) {
                str.append("isDataAllowedForApn = false. RAT = ").append(this.mPhone.getServiceState().getRilDataRadioTechnology());
            }
            if (!isDataEnabled(checkUserDataEnabled)) {
                str.append("isDataEnabled(").append(checkUserDataEnabled).append(") = false. ").append("mInternalDataEnabled = ").append(this.mInternalDataEnabled).append(" , mUserDataEnabled = ").append(this.mUserDataEnabled).append(", sPolicyDataEnabled = ").append(sPolicyDataEnabled).append(" ");
            }
            if (isEmergency()) {
                str.append("emergency = true");
            }
            if (this.mColdSimDetected) {
                str.append("coldSimDetected = true");
            }
            log(str.toString());
            apnContext.requestLog(str.toString());
            return false;
        }
        String str2;
        if (apnContext.getState() == State.FAILED) {
            str2 = "trySetupData: make a FAILED ApnContext IDLE so its reusable";
            log(str2);
            apnContext.requestLog(str2);
            apnContext.setState(State.IDLE);
        }
        int radioTech = this.mPhone.getServiceState().getRilDataRadioTechnology();
        apnContext.setConcurrentVoiceAndDataAllowed(sst.isConcurrentVoiceAndDataAllowed());
        if (apnContext.getState() == State.IDLE) {
            if (waitingApns == null) {
                waitingApns = buildWaitingApns(apnContext.getApnType(), radioTech);
            }
            if (waitingApns.isEmpty()) {
                notifyNoData(DcFailCause.MISSING_UNKNOWN_APN, apnContext);
                notifyOffApnsOfAvailability(apnContext.getReason());
                str2 = "trySetupData: X No APN found retValue=false";
                log(str2);
                apnContext.requestLog(str2);
                return false;
            }
            apnContext.setWaitingApns(waitingApns);
            log("trySetupData: Create from mAllApnSettings : " + apnListToString(this.mAllApnSettings));
        }
        boolean retValue = setupData(apnContext, radioTech);
        notifyOffApnsOfAvailability(apnContext.getReason());
        log("trySetupData: X retValue=" + retValue);
        return retValue;
    }

    /* Access modifiers changed, original: protected */
    public void notifyOffApnsOfAvailability(String reason) {
        DataAllowFailReason failureReason = new DataAllowFailReason();
        if (!isDataAllowed(failureReason)) {
            log(failureReason.getDataAllowFailReason());
        }
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (!this.mAttached.get() || !apnContext.isReady()) {
                String str;
                Phone phone = this.mPhone;
                if (reason != null) {
                    str = reason;
                } else {
                    str = apnContext.getReason();
                }
                phone.notifyDataConnection(str, apnContext.getApnType(), DataState.DISCONNECTED);
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public boolean cleanUpAllConnections(boolean tearDown, String reason) {
        log("cleanUpAllConnections: tearDown=" + tearDown + " reason=" + reason);
        boolean didDisconnect = false;
        int specificDisable = 0;
        if (!TextUtils.isEmpty(reason)) {
            if (reason.equals(PhoneInternalInterface.REASON_DATA_SPECIFIC_DISABLED) || reason.equals(PhoneInternalInterface.REASON_ROAMING_ON) || reason.equals(PhoneInternalInterface.REASON_SINGLE_PDN_ARBITRATION)) {
                specificDisable = 1;
            } else {
                specificDisable = reason.equals(PhoneInternalInterface.REASON_PDP_RESET);
            }
            if (TelBrand.IS_DCM) {
                specificDisable |= reason.equals(PhoneInternalInterface.REASON_APN_CHANGED);
            }
        }
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (specificDisable == 0) {
                if (!apnContext.isDisconnected()) {
                    didDisconnect = true;
                }
                apnContext.setReason(reason);
                cleanUpConnection(tearDown, apnContext);
            } else if (!apnContext.getApnType().equals("ims")) {
                ApnSetting apnSetting = apnContext.getApnSetting();
                if (apnSetting != null && apnSetting.isMetered(this.mPhone.getContext(), this.mPhone.getSubId(), this.mPhone.getServiceState().getDataRoaming())) {
                    if (!apnContext.isDisconnected()) {
                        didDisconnect = true;
                    }
                    log("clean up metered ApnContext Type: " + apnContext.getApnType());
                    apnContext.setReason(reason);
                    cleanUpConnection(tearDown, apnContext);
                }
            }
        }
        stopDataStallAlarm();
        this.mRequestedApnType = "default";
        log("cleanUpConnection: mDisconnectPendingCount = " + this.mDisconnectPendingCount);
        if (tearDown && this.mDisconnectPendingCount == 0) {
            notifyDataDisconnectComplete();
            notifyAllDataDisconnected();
        }
        return didDisconnect;
    }

    private void onCleanUpAllConnections(String cause) {
        cleanUpAllConnections(true, cause);
    }

    /* Access modifiers changed, original: 0000 */
    public void sendCleanUpConnection(boolean tearDown, ApnContext apnContext) {
        int i;
        log("sendCleanUpConnection: tearDown=" + tearDown + " apnContext=" + apnContext);
        Message msg = obtainMessage(270360);
        if (tearDown) {
            i = 1;
        } else {
            i = 0;
        }
        msg.arg1 = i;
        msg.arg2 = 0;
        msg.obj = apnContext;
        sendMessage(msg);
    }

    /* Access modifiers changed, original: protected */
    public void cleanUpConnection(boolean tearDown, ApnContext apnContext) {
        if (apnContext == null) {
            log("cleanUpConnection: apn context is null");
            return;
        }
        String str;
        DcAsyncChannel dcac = apnContext.getDcAc();
        apnContext.requestLog("cleanUpConnection: tearDown=" + tearDown + " reason=" + apnContext.getReason());
        if (!tearDown) {
            if (dcac != null) {
                dcac.reqReset();
            }
            apnContext.setState(State.IDLE);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            apnContext.setDataConnectionAc(null);
        } else if (apnContext.isDisconnected()) {
            apnContext.setState(State.IDLE);
            if (!apnContext.isReady()) {
                if (dcac != null) {
                    str = "cleanUpConnection: teardown, disconnected, !ready";
                    log(str + " apnContext=" + apnContext);
                    apnContext.requestLog(str);
                    dcac.tearDown(apnContext, "", null);
                }
                apnContext.setDataConnectionAc(null);
            }
        } else if (dcac == null) {
            apnContext.setState(State.IDLE);
            apnContext.requestLog("cleanUpConnection: connected, bug no DCAC");
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        } else if (apnContext.getState() != State.DISCONNECTING) {
            boolean disconnectAll = false;
            if ("dun".equals(apnContext.getApnType()) && teardownForDun()) {
                log("cleanUpConnection: disconnectAll DUN connection");
                disconnectAll = true;
            }
            int generation = apnContext.getConnectionGeneration();
            str = "cleanUpConnection: tearing down" + (disconnectAll ? " all" : "") + " using gen#" + generation;
            log(str + "apnContext=" + apnContext);
            apnContext.requestLog(str);
            Message msg = obtainMessage(270351, new Pair(apnContext, Integer.valueOf(generation)));
            if (disconnectAll) {
                apnContext.getDcAc().tearDownAll(apnContext.getReason(), msg);
            } else {
                apnContext.getDcAc().tearDown(apnContext, apnContext.getReason(), msg);
            }
            apnContext.setState(State.DISCONNECTING);
            this.mDisconnectPendingCount++;
        }
        if (dcac != null) {
            cancelReconnectAlarm(apnContext);
        }
        setupDataForSinglePdnArbitration(apnContext.getReason());
        str = "cleanUpConnection: X tearDown=" + tearDown + " reason=" + apnContext.getReason();
        log(str + " apnContext=" + apnContext + " dcac=" + apnContext.getDcAc());
        apnContext.requestLog(str);
    }

    /* Access modifiers changed, original: 0000 */
    public ApnSetting fetchDunApn() {
        int i = 0;
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
            ApnSetting dunSetting;
            int bearer = this.mPhone.getServiceState().getRilDataRadioTechnology();
            ApnSetting retDunSetting = null;
            IccRecords r = (IccRecords) this.mIccRecords.get();
            for (ApnSetting dunSetting2 : ApnSetting.arrayFromString(Global.getString(this.mResolver, "tether_dun_apn"))) {
                String operator = this.mPhone.getOperatorNumeric();
                if (ServiceState.bitmaskHasTech(dunSetting2.bearerBitmask, bearer) && dunSetting2.numeric.equals(operator)) {
                    if (dunSetting2.hasMvnoParams()) {
                        if (r != null && ApnSetting.mvnoMatches(r, dunSetting2.mvnoType, dunSetting2.mvnoMatchData)) {
                            return dunSetting2;
                        }
                    } else if (!this.mMvnoMatched) {
                        return dunSetting2;
                    }
                }
            }
            String[] apnArrayData = this.mPhone.getContext().getResources().getStringArray(17235998);
            int length = apnArrayData.length;
            while (i < length) {
                dunSetting2 = ApnSetting.fromString(apnArrayData[i]);
                if (dunSetting2 != null && ServiceState.bitmaskHasTech(dunSetting2.bearerBitmask, bearer)) {
                    if (dunSetting2.hasMvnoParams()) {
                        if (r != null && ApnSetting.mvnoMatches(r, dunSetting2.mvnoType, dunSetting2.mvnoMatchData)) {
                            return dunSetting2;
                        }
                    } else if (!this.mMvnoMatched) {
                        retDunSetting = dunSetting2;
                    }
                }
                i++;
            }
            return retDunSetting;
        }
    }

    public boolean hasMatchedTetherApnSetting() {
        ApnSetting matched = fetchDunApn();
        log("hasMatchedTetherApnSetting: APN=" + matched);
        return matched != null;
    }

    /* Access modifiers changed, original: protected */
    public void setupDataForSinglePdnArbitration(String reason) {
        log("setupDataForSinglePdn: reason = " + reason + " isDisconnected = " + isDisconnected());
        if (isOnlySingleDcAllowed(this.mPhone.getServiceState().getRilDataRadioTechnology()) && isDisconnected() && !PhoneInternalInterface.REASON_SINGLE_PDN_ARBITRATION.equals(reason) && !PhoneInternalInterface.REASON_RADIO_TURNED_OFF.equals(reason)) {
            sendMessage(obtainMessage(270339, PhoneInternalInterface.REASON_SINGLE_PDN_ARBITRATION));
        }
    }

    private boolean teardownForDun() {
        boolean z = true;
        if (ServiceState.isCdma(this.mPhone.getServiceState().getRilDataRadioTechnology())) {
            return true;
        }
        if (fetchDunApn() == null) {
            z = false;
        }
        return z;
    }

    private void cancelReconnectAlarm(ApnContext apnContext) {
        if (apnContext != null) {
            PendingIntent intent = apnContext.getReconnectIntent();
            if (intent != null) {
                ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(intent);
                apnContext.setReconnectIntent(null);
            }
        }
    }

    private String[] parseTypes(String types) {
        if (types != null && !types.equals("")) {
            return types.split(",");
        }
        return new String[]{CharacterSets.MIMENAME_ANY_CHARSET};
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isPermanentFail(DcFailCause dcFailCause) {
        if (dcFailCause.isPermanentFail(this.mPhone.getContext(), this.mPhone.getSubId())) {
            return (this.mAttached.get() && dcFailCause == DcFailCause.SIGNAL_LOST) ? false : true;
        } else {
            return false;
        }
    }

    private ApnSetting makeApnSetting(Cursor cursor) {
        ApnSetting apn = new ApnSetting(cursor.getInt(cursor.getColumnIndexOrThrow("_id")), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.NUMERIC)), cursor.getString(cursor.getColumnIndexOrThrow("name")), cursor.getString(cursor.getColumnIndexOrThrow("apn")), NetworkUtils.trimV4AddrZeros(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.PROXY))), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.PORT)), NetworkUtils.trimV4AddrZeros(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.MMSC))), NetworkUtils.trimV4AddrZeros(cursor.getString(cursor.getColumnIndexOrThrow(Carriers.MMSPROXY))), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.MMSPORT)), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.USER)), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.PASSWORD)), cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.AUTH_TYPE)), parseTypes(cursor.getString(cursor.getColumnIndexOrThrow("type"))), cursor.getString(cursor.getColumnIndexOrThrow("protocol")), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.ROAMING_PROTOCOL)), cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.CARRIER_ENABLED)) == 1, cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.BEARER)), cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.BEARER_BITMASK)), cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.PROFILE_ID)), cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.MODEM_COGNITIVE)) == 1, cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.MAX_CONNS)), cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.WAIT_TIME)), cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.MAX_CONNS_TIME)), cursor.getInt(cursor.getColumnIndexOrThrow("mtu")), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.MVNO_TYPE)), cursor.getString(cursor.getColumnIndexOrThrow(Carriers.MVNO_MATCH_DATA)));
        if (TelBrand.IS_KDDI) {
            String[] dnses = new String[2];
            try {
                dnses[0] = cursor.getString(cursor.getColumnIndexOrThrow("oem_dns_primary"));
                dnses[1] = cursor.getString(cursor.getColumnIndexOrThrow("oem_dns_secoundary"));
            } catch (Exception e) {
                dnses[0] = "";
                dnses[1] = "";
            }
            apn.oemDnses = dnses;
        }
        return apn;
    }

    /* Access modifiers changed, original: protected */
    public ArrayList<ApnSetting> createApnList(Cursor cursor) {
        ArrayList<ApnSetting> result;
        ArrayList<ApnSetting> mnoApns = new ArrayList();
        ArrayList<ApnSetting> mvnoApns = new ArrayList();
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (cursor.moveToFirst()) {
            do {
                ApnSetting apn = makeApnSetting(cursor);
                if (apn != null) {
                    if (!apn.hasMvnoParams()) {
                        mnoApns.add(apn);
                    } else if (r != null && ApnSetting.mvnoMatches(r, apn.mvnoType, apn.mvnoMatchData)) {
                        mvnoApns.add(apn);
                    }
                }
            } while (cursor.moveToNext());
        }
        if (mvnoApns.isEmpty()) {
            result = mnoApns;
            this.mMvnoMatched = false;
        } else {
            result = mvnoApns;
            this.mMvnoMatched = true;
        }
        log("createApnList: X result=" + result);
        return result;
    }

    private boolean dataConnectionNotInUse(DcAsyncChannel dcac) {
        log("dataConnectionNotInUse: check if dcac is inuse dcac=" + dcac);
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (apnContext.getDcAc() == dcac) {
                log("dataConnectionNotInUse: in use by apnContext=" + apnContext);
                return false;
            }
        }
        log("dataConnectionNotInUse: tearDownAll");
        dcac.tearDownAll("No connection", null);
        log("dataConnectionNotInUse: not in use return true");
        return true;
    }

    private DcAsyncChannel findFreeDataConnection() {
        for (DcAsyncChannel dcac : this.mDataConnectionAcHashMap.values()) {
            if (dcac.isInactiveSync() && dataConnectionNotInUse(dcac)) {
                log("findFreeDataConnection: found free DataConnection= dcac=" + dcac);
                return dcac;
            }
        }
        log("findFreeDataConnection: NO free DataConnection");
        return null;
    }

    private boolean setupData(ApnContext apnContext, int radioTech) {
        log("setupData: apnContext=" + apnContext);
        apnContext.requestLog("setupData");
        Object dcac = null;
        ApnSetting apnSetting = apnContext.getNextApnSetting();
        if (apnSetting == null) {
            log("setupData: return for no apn found!");
            return false;
        }
        int profileId = apnSetting.profileId;
        if (profileId == 0) {
            profileId = getApnProfileID(apnContext.getApnType());
        }
        if (!(apnContext.getApnType() == "dun" && teardownForDun())) {
            dcac = checkForCompatibleConnectedApnContext(apnContext);
            if (dcac != null) {
                ApnSetting dcacApnSetting = dcac.getApnSettingSync();
                if (dcacApnSetting != null) {
                    apnSetting = dcacApnSetting;
                }
            }
        }
        if (dcac == null) {
            if (isOnlySingleDcAllowed(radioTech)) {
                if (isHigherPriorityApnContextActive(apnContext)) {
                    log("setupData: Higher priority ApnContext active.  Ignoring call");
                    return false;
                }
                if (!apnContext.getApnType().equals("ims")) {
                    for (ApnContext otherApnContext : this.mApnContexts.values()) {
                        if (!(otherApnContext.getApnType().equals("ims") || otherApnContext.getApnType().equals(apnContext.getApnType()))) {
                            if (otherApnContext.isDisconnected()) {
                                ApnSetting currentApnSetting = otherApnContext.getApnSetting();
                                if (currentApnSetting != null && currentApnSetting.isMetered(this.mPhone.getContext(), this.mPhone.getSubId(), this.mPhone.getServiceState().getDataRoaming())) {
                                    otherApnContext.setReason(PhoneInternalInterface.REASON_SINGLE_PDN_ARBITRATION);
                                    cleanUpConnection(true, otherApnContext);
                                }
                            } else if (cleanUpAllConnections(true, PhoneInternalInterface.REASON_SINGLE_PDN_ARBITRATION)) {
                                log("setupData: Some calls are disconnecting first. Wait and retry");
                                return false;
                            }
                        }
                    }
                }
                log("setupData: Single pdp. Continue setting up data call.");
            }
            dcac = findFreeDataConnection();
            if (dcac == null) {
                dcac = createDataConnection();
            }
            if (dcac == null) {
                log("setupData: No free DataConnection and couldn't create one, WEIRD");
                return false;
            }
        }
        int generation = apnContext.incAndGetConnectionGeneration();
        log("setupData: dcac=" + dcac + " apnSetting=" + apnSetting + " gen#=" + generation);
        apnContext.setDataConnectionAc(dcac);
        apnContext.setApnSetting(apnSetting);
        apnContext.setState(State.CONNECTING);
        this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        Message msg = obtainMessage();
        msg.what = 270336;
        msg.obj = new Pair(apnContext, Integer.valueOf(generation));
        dcac.bringUp(apnContext, profileId, radioTech, msg, generation);
        log("setupData: initing!");
        return true;
    }

    /* Access modifiers changed, original: protected */
    public void setInitialAttachApn() {
        ApnSetting iaApnSetting = null;
        ApnSetting defaultApnSetting = null;
        ApnSetting firstApnSetting = null;
        log("setInitialApn: E mPreferredApn=" + this.mPreferredApn);
        if (this.mAllApnSettings != null && !this.mAllApnSettings.isEmpty()) {
            firstApnSetting = (ApnSetting) this.mAllApnSettings.get(0);
            log("setInitialApn: firstApnSetting=" + firstApnSetting);
            for (ApnSetting apn : this.mAllApnSettings) {
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
        if (iaApnSetting == null && defaultApnSetting == null && !allowInitialAttachForOperator()) {
            log("Abort Initial attach");
            return;
        }
        Object initialAttachApnSetting = null;
        ApnSetting initialAttachApnSetting2;
        if (iaApnSetting != null) {
            log("setInitialAttachApn: using iaApnSetting");
            initialAttachApnSetting2 = iaApnSetting;
        } else if (this.mPreferredApn != null) {
            log("setInitialAttachApn: using mPreferredApn");
            initialAttachApnSetting2 = this.mPreferredApn;
        } else if (defaultApnSetting != null) {
            if (!TelBrand.IS_DCM || TelBrand.IS_SHARP) {
                log("setInitialAttachApn: using defaultApnSetting");
                initialAttachApnSetting2 = defaultApnSetting;
            }
        } else if (firstApnSetting != null && (!TelBrand.IS_DCM || TelBrand.IS_SHARP)) {
            log("setInitialAttachApn: using firstApnSetting");
            initialAttachApnSetting2 = firstApnSetting;
        }
        if (initialAttachApnSetting2 == null) {
            log("setInitialAttachApn: X There in no available apn");
            IccRecords r = (IccRecords) this.mIccRecords.get();
            String operator = r != null ? r.getOperatorNumeric() : "";
            if (!(operator == null || operator.equals(""))) {
                this.mPhone.mCi.setInitialAttachApn("", "", 0, "", "", null);
            }
        } else {
            log("setInitialAttachApn: X selected Apn=" + initialAttachApnSetting2);
            this.mPhone.mCi.setInitialAttachApn(initialAttachApnSetting2.apn, initialAttachApnSetting2.protocol, initialAttachApnSetting2.authType, initialAttachApnSetting2.user, initialAttachApnSetting2.password, null);
        }
        updateOemDataSettings();
    }

    /* Access modifiers changed, original: protected */
    public boolean allowInitialAttachForOperator() {
        return true;
    }

    private void onApnChanged() {
        boolean z;
        State overallState = getOverallState();
        boolean isDisconnected = overallState != State.IDLE ? overallState == State.FAILED : true;
        if (this.mPhone instanceof GsmCdmaPhone) {
            ((GsmCdmaPhone) this.mPhone).updateCurrentCarrierInProvider();
        }
        log("onApnChanged: createAllApnList and cleanUpAllConnections");
        createAllApnList();
        if (TelBrand.IS_KDDI && this.mEnableApnCarNavi && !this.mConnectingApnCarNavi) {
            log("DcTracker mConnectingApnCarNavi = true");
            this.mConnectingApnCarNavi = true;
        }
        setInitialAttachApn();
        if (TelBrand.IS_DCM) {
            updateDunProfileNameCid1();
        }
        if (isDisconnected) {
            z = false;
        } else {
            z = true;
        }
        cleanUpConnectionsOnUpdatedApns(z);
        if (this.mPhone.getSubId() == SubscriptionManager.getDefaultDataSubscriptionId()) {
            setupDataOnConnectableApns(PhoneInternalInterface.REASON_APN_CHANGED);
        }
    }

    private DcAsyncChannel findDataConnectionAcByCid(int cid) {
        for (DcAsyncChannel dcac : this.mDataConnectionAcHashMap.values()) {
            if (dcac.getCidSync() == cid) {
                return dcac;
            }
        }
        return null;
    }

    private void gotoIdleAndNotifyDataConnection(String reason) {
        log("gotoIdleAndNotifyDataConnection: reason=" + reason);
        notifyDataConnection(reason);
    }

    private boolean isHigherPriorityApnContextActive(ApnContext apnContext) {
        if (apnContext.getApnType().equals("ims")) {
            return false;
        }
        for (ApnContext otherContext : this.mPrioritySortedApnContexts) {
            if (!otherContext.getApnType().equals("ims")) {
                if (apnContext.getApnType().equalsIgnoreCase(otherContext.getApnType())) {
                    return false;
                }
                if (otherContext.isEnabled() && otherContext.getState() != State.FAILED) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isOnlySingleDcAllowed(int rilRadioTech) {
        if (TelBrand.IS_DCM) {
            return true;
        }
        int[] singleDcRats = this.mPhone.getContext().getResources().getIntArray(17236024);
        boolean onlySingleDcAllowed = false;
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean("persist.telephony.test.singleDc", false)) {
            onlySingleDcAllowed = true;
        }
        if (singleDcRats != null) {
            for (int i = 0; i < singleDcRats.length && !onlySingleDcAllowed; i++) {
                if (rilRadioTech == singleDcRats[i]) {
                    onlySingleDcAllowed = true;
                }
            }
        }
        log("isOnlySingleDcAllowed(" + rilRadioTech + "): " + onlySingleDcAllowed);
        return onlySingleDcAllowed;
    }

    /* Access modifiers changed, original: 0000 */
    public void sendRestartRadio() {
        log("sendRestartRadio:");
        sendMessage(obtainMessage(270362));
    }

    private void restartRadio() {
        log("restartRadio: ************TURN OFF RADIO**************");
        cleanUpAllConnections(true, PhoneInternalInterface.REASON_RADIO_TURNED_OFF);
        this.mPhone.getServiceStateTracker().powerOffRadioSafely(this);
        SystemProperties.set("net.ppp.reset-by-timeout", String.valueOf(Integer.parseInt(SystemProperties.get("net.ppp.reset-by-timeout", "0")) + 1));
    }

    private boolean retryAfterDisconnected(ApnContext apnContext) {
        if (PhoneInternalInterface.REASON_RADIO_TURNED_OFF.equals(apnContext.getReason()) || (isOnlySingleDcAllowed(this.mPhone.getServiceState().getRilDataRadioTechnology()) && isHigherPriorityApnContextActive(apnContext))) {
            return false;
        }
        return true;
    }

    private void startAlarmForReconnect(long delay, ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        Intent intent = new Intent("com.android.internal.telephony.data-reconnect." + apnType);
        intent.addFlags(268435456);
        intent.putExtra(INTENT_RECONNECT_ALARM_EXTRA_REASON, apnContext.getReason());
        intent.putExtra(INTENT_RECONNECT_ALARM_EXTRA_TYPE, apnType);
        intent.addFlags(268435456);
        intent.putExtra("subscription", this.mPhone.getSubId());
        log("startAlarmForReconnect: delay=" + delay + " action=" + intent.getAction() + " apn=" + apnContext);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
        apnContext.setReconnectIntent(alarmIntent);
        this.mAlarmManager.setExact(2, SystemClock.elapsedRealtime() + delay, alarmIntent);
    }

    private void notifyNoData(DcFailCause lastFailCauseCode, ApnContext apnContext) {
        log("notifyNoData: type=" + apnContext.getApnType());
        if (isPermanentFail(lastFailCauseCode) && !apnContext.getApnType().equals("default")) {
            this.mPhone.notifyDataConnectionFailed(apnContext.getReason(), apnContext.getApnType());
        }
    }

    public boolean getAutoAttachOnCreation() {
        return this.mAutoAttachOnCreation.get();
    }

    /* Access modifiers changed, original: protected */
    public void onRecordsLoadedOrSubIdChanged() {
        log("onRecordsLoadedOrSubIdChanged: createAllApnList");
        this.mAutoAttachOnCreationConfig = this.mPhone.getContext().getResources().getBoolean(17957017);
        createAllApnList();
        setInitialAttachApn();
        if (this.mPhone.mCi.getRadioState().isOn()) {
            log("onRecordsLoadedOrSubIdChanged: notifying data availability");
            notifyOffApnsOfAvailability(PhoneInternalInterface.REASON_SIM_LOADED);
        }
        setupDataOnConnectableApns(PhoneInternalInterface.REASON_SIM_LOADED);
    }

    private void applyUnProvisionedSimDetected() {
        if (isColdSimDetected()) {
            if (!this.mColdSimDetected) {
                log("onColdSimDetected: cleanUpAllDataConnections");
                cleanUpAllConnections(null);
                this.mPhone.notifyOtaspChanged(5);
                this.mColdSimDetected = true;
            }
        } else if (!isOutOfCreditSimDetected()) {
            log("Provisioned Sim Detected on subId: " + this.mPhone.getSubId());
            this.mColdSimDetected = false;
            this.mOutOfCreditSimDetected = false;
        } else if (!this.mOutOfCreditSimDetected) {
            log("onOutOfCreditSimDetected on subId: re-establish data connection");
            for (ApnContext context : this.redirectApnContextSet) {
                onTrySetupData(context);
                this.redirectApnContextSet.remove(context);
            }
            this.mOutOfCreditSimDetected = true;
        }
    }

    private void onSimNotReady() {
        log("onSimNotReady");
        cleanUpAllConnections(true, PhoneInternalInterface.REASON_SIM_NOT_READY);
        this.mAllApnSettings = null;
        this.mAutoAttachOnCreationConfig = false;
    }

    private void onSetDependencyMet(String apnType, boolean met) {
        if (!"hipri".equals(apnType)) {
            ApnContext apnContext = (ApnContext) this.mApnContexts.get(apnType);
            if (apnContext == null) {
                loge("onSetDependencyMet: ApnContext not found in onSetDependencyMet(" + apnType + ", " + met + ")");
                return;
            }
            applyNewState(apnContext, apnContext.isEnabled(), met);
            if ("default".equals(apnType)) {
                apnContext = (ApnContext) this.mApnContexts.get("hipri");
                if (apnContext != null) {
                    applyNewState(apnContext, apnContext.isEnabled(), met);
                }
            }
        }
    }

    private void onSetPolicyDataEnabled(boolean enabled) {
        synchronized (this.mDataEnabledLock) {
            boolean prevEnabled = getAnyDataEnabled();
            if (sPolicyDataEnabled != enabled) {
                sPolicyDataEnabled = enabled;
                updateOemDataSettings();
                if (prevEnabled != getAnyDataEnabled()) {
                    if (prevEnabled) {
                        onCleanUpAllConnections(PhoneInternalInterface.REASON_DATA_SPECIFIC_DISABLED);
                    } else {
                        onTrySetupData(PhoneInternalInterface.REASON_DATA_ENABLED);
                    }
                }
            }
        }
    }

    private void applyNewState(ApnContext apnContext, boolean enabled, boolean met) {
        boolean cleanup = false;
        boolean trySetup = false;
        String str = "applyNewState(" + apnContext.getApnType() + ", " + enabled + "(" + apnContext.isEnabled() + "), " + met + "(" + apnContext.getDependencyMet() + "))";
        log(str);
        apnContext.requestLog(str);
        if (apnContext.isReady()) {
            cleanup = true;
            if (enabled && met) {
                State state = apnContext.getState();
                switch (-getcom-android-internal-telephony-DctConstants$StateSwitchesValues()[state.ordinal()]) {
                    case 1:
                    case 2:
                    case 3:
                    case 7:
                        log("applyNewState: 'ready' so return");
                        apnContext.requestLog("applyNewState state=" + state + ", so return");
                        return;
                    case 4:
                    case 5:
                    case 6:
                        trySetup = true;
                        apnContext.setReason(PhoneInternalInterface.REASON_DATA_ENABLED);
                        break;
                }
            } else if (met) {
                apnContext.setReason(PhoneInternalInterface.REASON_DATA_DISABLED);
                cleanup = (apnContext.getApnType() == "dun" && teardownForDun()) || apnContext.getState() != State.CONNECTED;
            } else {
                apnContext.setReason(PhoneInternalInterface.REASON_DATA_DEPENDENCY_UNMET);
            }
        } else if (enabled && met) {
            if (apnContext.isEnabled()) {
                apnContext.setReason(PhoneInternalInterface.REASON_DATA_DEPENDENCY_MET);
            } else {
                apnContext.setReason(PhoneInternalInterface.REASON_DATA_ENABLED);
            }
            if (apnContext.getState() == State.FAILED) {
                apnContext.setState(State.IDLE);
            }
            trySetup = true;
        }
        apnContext.setEnabled(enabled);
        apnContext.setDependencyMet(met);
        if (cleanup) {
            cleanUpConnection(true, apnContext);
        }
        if (trySetup) {
            apnContext.resetErrorCodeRetries();
            trySetupData(apnContext);
        }
        if (TelBrand.IS_DCM && enabled) {
            startNetStatPoll();
        }
        if (apnContext.getState() == State.CONNECTED) {
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        }
    }

    private DcAsyncChannel checkForCompatibleConnectedApnContext(ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        ApnSetting dunSetting = null;
        if ("dun".equals(apnType)) {
            dunSetting = fetchDunApn();
        }
        log("checkForCompatibleConnectedApnContext: apnContext=" + apnContext);
        Object potentialDcac = null;
        Object potentialApnCtx = null;
        for (ApnContext curApnCtx : this.mApnContexts.values()) {
            DcAsyncChannel curDcac = curApnCtx.getDcAc();
            if (curDcac != null) {
                ApnSetting apnSetting = curApnCtx.getApnSetting();
                log("apnSetting: " + apnSetting);
                if (dunSetting == null) {
                    if (apnSetting != null && apnSetting.canHandleType(apnType)) {
                        switch (-getcom-android-internal-telephony-DctConstants$StateSwitchesValues()[curApnCtx.getState().ordinal()]) {
                            case 1:
                                log("checkForCompatibleConnectedApnContext: found canHandle conn=" + curDcac + " curApnCtx=" + curApnCtx);
                                return curDcac;
                            case 2:
                            case 6:
                                potentialDcac = curDcac;
                                potentialApnCtx = curApnCtx;
                                break;
                            default:
                                break;
                        }
                    }
                } else if (dunSetting.equals(apnSetting)) {
                    switch (-getcom-android-internal-telephony-DctConstants$StateSwitchesValues()[curApnCtx.getState().ordinal()]) {
                        case 1:
                            log("checkForCompatibleConnectedApnContext: found dun conn=" + curDcac + " curApnCtx=" + curApnCtx);
                            return curDcac;
                        case 2:
                        case 6:
                            potentialDcac = curDcac;
                            potentialApnCtx = curApnCtx;
                            break;
                        default:
                            break;
                    }
                } else {
                    continue;
                }
            }
        }
        if (potentialDcac != null) {
            log("checkForCompatibleConnectedApnContext: found potential conn=" + potentialDcac + " curApnCtx=" + potentialApnCtx);
            return potentialDcac;
        }
        log("checkForCompatibleConnectedApnContext: NO conn apnContext=" + apnContext);
        return null;
    }

    public void setEnabled(int id, boolean enable) {
        Message msg = obtainMessage(270349);
        msg.arg1 = id;
        msg.arg2 = enable ? 1 : 0;
        sendMessage(msg);
    }

    private void onEnableApn(int apnId, int enabled) {
        boolean z = true;
        ApnContext apnContext = (ApnContext) this.mApnContextsById.get(apnId);
        if (TelBrand.IS_KDDI) {
            if (enabled == 1 && this.RetryEnableApn < 100) {
                for (ApnContext apnContextCheck : this.mApnContexts.values()) {
                    if (apnContextCheck.isEnabled() && apnContextCheck.getState() == State.DISCONNECTING) {
                        log("onEnableApn : " + apnContextCheck.getApnType() + " is Disconnecting!!!");
                        Message msg = obtainMessage(270349);
                        msg.arg1 = apnId;
                        msg.arg2 = enabled;
                        sendMessageDelayed(msg, 200);
                        this.RetryEnableApn++;
                        return;
                    }
                }
            }
            this.RetryEnableApn = 0;
        }
        if (apnContext == null) {
            loge("onEnableApn(" + apnId + ", " + enabled + "): NO ApnContext");
            return;
        }
        log("onEnableApn: apnContext=" + apnContext + " call applyNewState");
        if (enabled != 1) {
            z = false;
        }
        applyNewState(apnContext, z, apnContext.getDependencyMet());
    }

    private boolean onTrySetupData(String reason) {
        log("onTrySetupData: reason=" + reason);
        setupDataOnConnectableApns(reason);
        return true;
    }

    private boolean onTrySetupData(ApnContext apnContext) {
        log("onTrySetupData: apnContext=" + apnContext);
        return trySetupData(apnContext);
    }

    public boolean getDataEnabled() {
        int i = 1;
        int device_provisioned = Global.getInt(this.mResolver, "device_provisioned", 0);
        boolean retVal = "true".equalsIgnoreCase(SystemProperties.get("ro.com.android.mobiledata", "true"));
        if (TelephonyManager.getDefault().getSimCount() == 1) {
            int i2;
            ContentResolver contentResolver = this.mResolver;
            String str = "mobile_data";
            if (retVal) {
                i2 = 1;
            } else {
                i2 = 0;
            }
            retVal = Global.getInt(contentResolver, str, i2) != 0;
        } else {
            try {
                retVal = TelephonyManager.getIntWithSubId(this.mResolver, "mobile_data", this.mPhone.getSubId()) != 0;
            } catch (SettingNotFoundException e) {
            }
        }
        if (device_provisioned == 0) {
            String prov_property = SystemProperties.get("ro.com.android.prov_mobiledata", retVal ? "true" : "false");
            retVal = "true".equalsIgnoreCase(prov_property);
            ContentResolver contentResolver2 = this.mResolver;
            String str2 = "device_provisioning_mobile_data";
            if (!retVal) {
                i = 0;
            }
            int prov_mobile_data = Global.getInt(contentResolver2, str2, i);
            retVal = prov_mobile_data != 0;
            log("getDataEnabled during provisioning retVal=" + retVal + " - (" + prov_property + ", " + prov_mobile_data + ")");
        }
        return retVal;
    }

    /* Access modifiers changed, original: protected */
    public void sendOemKddiFailCauseBroadcast(DcFailCause dcFailCause, ApnContext apnCtx) {
        Intent intent;
        if (this.mEnableApnCarNavi) {
            int rilFailCause;
            if (dcFailCause == DcFailCause.USER_AUTHENTICATION) {
                rilFailCause = -3;
            } else if (dcFailCause == DcFailCause.SIGNAL_LOST || dcFailCause == DcFailCause.RADIO_POWER_OFF) {
                rilFailCause = -2;
            } else {
                rilFailCause = -4;
            }
            intent = new Intent(CONNECTIVITY_ACTION_CARNAVI);
            intent.putExtra(EXTRA_CONNECTIVITY_STATUS_CARNAVI, 4);
            intent.putExtra(EXTRA_ERRONO_CARNAVI, rilFailCause);
            this.mPhone.getContext().sendBroadcast(intent);
            log("DataConnection Sent intent CONNECTIVITY_ACTION for CarNavi error : " + rilFailCause);
        } else if (dcFailCause != DcFailCause.SIGNAL_LOST) {
            intent = new Intent(INTENT_CDMA_FAILCAUSE);
            intent.putExtra(INTENT_CDMA_FAILCAUSE_CAUSE, Integer.toString(dcFailCause.getErrorCode()));
            intent.putExtra(INTENT_CDMA_FAILCAUSE_EXTRA, apnCtx.getApnType());
            this.mPhone.getContext().sendBroadcast(intent);
            log("sendOemKddiFailCauseBroadcast: send INTENT_CDMA_FAILCAUSE type= " + apnCtx.getApnType() + " cause= " + Integer.toString(dcFailCause.getErrorCode()));
        }
    }

    public void setDataOnRoamingEnabled(boolean enabled) {
        int phoneSubId = this.mPhone.getSubId();
        if (getDataOnRoamingEnabled() != enabled) {
            int roaming = enabled ? 1 : 0;
            if (TelephonyManager.getDefault().getSimCount() == 1) {
                Global.putInt(this.mResolver, "data_roaming", roaming);
            } else {
                Global.putInt(this.mResolver, "data_roaming" + phoneSubId, roaming);
            }
            log(" handleDataOnRoamingChange: updateOemDataSettings call");
            updateOemDataSettings();
            this.mSubscriptionManager.setDataRoaming(roaming, phoneSubId);
            log("setDataOnRoamingEnabled: set phoneSubId=" + phoneSubId + " isRoaming=" + enabled);
            return;
        }
        log("setDataOnRoamingEnabled: unchanged phoneSubId=" + phoneSubId + " isRoaming=" + enabled);
    }

    public boolean getDataOnRoamingEnabled() {
        int i = 1;
        boolean isDataRoamingEnabled = "true".equalsIgnoreCase(SystemProperties.get("ro.com.android.dataroaming", "false"));
        int phoneSubId = this.mPhone.getSubId();
        try {
            if (TelephonyManager.getDefault().getSimCount() != 1) {
                return TelephonyManager.getIntWithSubId(this.mResolver, "data_roaming", phoneSubId) != 0;
            } else {
                ContentResolver contentResolver = this.mResolver;
                String str = "data_roaming";
                if (!isDataRoamingEnabled) {
                    i = 0;
                }
                if (Global.getInt(contentResolver, str, i) != 0) {
                    return true;
                }
                return false;
            }
        } catch (SettingNotFoundException snfe) {
            log("getDataOnRoamingEnabled: SettingNofFoundException snfe=" + snfe);
            return isDataRoamingEnabled;
        }
    }

    private void onRoamingOff() {
        log("onRoamingOff");
        if (TelBrand.IS_KDDI) {
            createAllApnList();
        }
        if (this.mUserDataEnabled) {
            if (getDataOnRoamingEnabled()) {
                notifyDataConnection(PhoneInternalInterface.REASON_ROAMING_OFF);
            } else {
                notifyOffApnsOfAvailability(PhoneInternalInterface.REASON_ROAMING_OFF);
                setupDataOnConnectableApns(PhoneInternalInterface.REASON_ROAMING_OFF);
            }
        }
    }

    private void onRoamingOn() {
        log("onRoamingOn");
        if (TelBrand.IS_KDDI) {
            createAllApnList();
        }
        if (!this.mUserDataEnabled) {
            log("data not enabled by user");
        } else if (this.mPhone.getServiceState().getDataRoaming()) {
            if (getDataOnRoamingEnabled()) {
                log("onRoamingOn: setup data on roaming");
                setupDataOnConnectableApns(PhoneInternalInterface.REASON_ROAMING_ON);
                notifyDataConnection(PhoneInternalInterface.REASON_ROAMING_ON);
            } else {
                log("onRoamingOn: Tear down data connection on roaming.");
                cleanUpAllConnections(true, PhoneInternalInterface.REASON_ROAMING_ON);
                notifyOffApnsOfAvailability(PhoneInternalInterface.REASON_ROAMING_ON);
                broadcastDisconnectDun();
            }
        } else {
            log("device is not roaming. ignored the request.");
        }
    }

    private void onRadioAvailable() {
        log("onRadioAvailable");
        if (this.mPhone.getSimulatedRadioControl() != null) {
            notifyDataConnection(null);
            log("onRadioAvailable: We're on the simulator; assuming data is connected");
        }
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null && r.getRecordsLoaded()) {
            notifyOffApnsOfAvailability(null);
        }
        if (getOverallState() != State.IDLE) {
            cleanUpConnection(true, null);
        }
    }

    private void onRadioOffOrNotAvailable() {
        this.mReregisterOnReconnectFailure = false;
        if (this.mPhone.getSimulatedRadioControl() != null) {
            log("We're on the simulator; assuming radio off is meaningless");
        } else {
            log("onRadioOffOrNotAvailable: is off and clean up all connections");
            cleanUpAllConnections(false, PhoneInternalInterface.REASON_RADIO_TURNED_OFF);
        }
        notifyOffApnsOfAvailability(null);
    }

    private void completeConnection(ApnContext apnContext) {
        log("completeConnection: successful, notify the world apnContext=" + apnContext);
        if (this.mIsProvisioning && !TextUtils.isEmpty(this.mProvisioningUrl)) {
            log("completeConnection: MOBILE_PROVISIONING_ACTION url=" + this.mProvisioningUrl);
            Intent newIntent = Intent.makeMainSelectorActivity("android.intent.action.MAIN", "android.intent.category.APP_BROWSER");
            newIntent.setData(Uri.parse(this.mProvisioningUrl));
            newIntent.setFlags(272629760);
            try {
                this.mPhone.getContext().startActivity(newIntent);
            } catch (ActivityNotFoundException e) {
                loge("completeConnection: startActivityAsUser failed" + e);
            }
        }
        this.mIsProvisioning = false;
        this.mProvisioningUrl = null;
        if (this.mProvisioningSpinner != null) {
            sendMessage(obtainMessage(270378, this.mProvisioningSpinner));
        }
        this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        startNetStatPoll();
        startDataStallAlarm(false);
    }

    private void onDataSetupComplete(AsyncResult ar) {
        DcFailCause cause = DcFailCause.UNKNOWN;
        boolean handleError = false;
        ApnContext apnContext = getValidApnContext(ar, "onDataSetupComplete");
        if (apnContext != null) {
            ApnSetting apn;
            if (ar.exception == null) {
                DcAsyncChannel dcac = apnContext.getDcAc();
                if (dcac == null) {
                    log("onDataSetupComplete: no connection to DC, handle as error");
                    cause = DcFailCause.CONNECTION_TO_DATACONNECTIONAC_BROKEN;
                    handleError = true;
                } else {
                    apn = apnContext.getApnSetting();
                    log("onDataSetupComplete: success apn=" + (apn == null ? "unknown" : apn.apn));
                    if (!(apn == null || apn.proxy == null || apn.proxy.length() == 0)) {
                        try {
                            String port = apn.port;
                            if (TextUtils.isEmpty(port)) {
                                port = "8080";
                            }
                            dcac.setLinkPropertiesHttpProxySync(new ProxyInfo(apn.proxy, Integer.parseInt(port), null));
                        } catch (NumberFormatException e) {
                            loge("onDataSetupComplete: NumberFormatException making ProxyProperties (" + apn.port + "): " + e);
                        }
                    }
                    if (TextUtils.equals(apnContext.getApnType(), "default")) {
                        try {
                            SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "true");
                        } catch (RuntimeException e2) {
                            log("Failed to set PUPPET_MASTER_RADIO_STRESS_TEST to true");
                        }
                        if (this.mCanSetPreferApn && this.mPreferredApn == null) {
                            log("onDataSetupComplete: PREFERRED APN is null");
                            this.mPreferredApn = apn;
                            if (this.mPreferredApn != null) {
                                setPreferredApn(this.mPreferredApn.id);
                                if (TelBrand.IS_DCM) {
                                    updateDunProfileNameCid1();
                                }
                            }
                        }
                    } else {
                        try {
                            SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
                        } catch (RuntimeException e3) {
                            log("Failed to set PUPPET_MASTER_RADIO_STRESS_TEST to false");
                        }
                    }
                    if (TelBrand.IS_KDDI && this.mEnableApnCarNavi && apnContext.getState() == State.DISCONNECTING) {
                        log("onDataSetupComplete: type=" + apnContext.getApnType() + " Do not change CONNECTED state during DISCONNECTING state when CarNavi");
                    } else {
                        apnContext.setState(State.CONNECTED);
                    }
                    boolean isProvApn = apnContext.isProvisioningApn();
                    ConnectivityManager cm = ConnectivityManager.from(this.mPhone.getContext());
                    if (this.mProvisionBroadcastReceiver != null) {
                        this.mPhone.getContext().unregisterReceiver(this.mProvisionBroadcastReceiver);
                        this.mProvisionBroadcastReceiver = null;
                    }
                    if (!isProvApn || this.mIsProvisioning) {
                        cm.setProvisioningNotificationVisible(false, 0, this.mProvisionActionName);
                        completeConnection(apnContext);
                    } else {
                        log("onDataSetupComplete: successful, BUT send connected to prov apn as mIsProvisioning:" + this.mIsProvisioning + " == false" + " && (isProvisioningApn:" + isProvApn + " == true");
                        this.mProvisionBroadcastReceiver = new ProvisionNotificationBroadcastReceiver(cm.getMobileProvisioningUrl(), TelephonyManager.getDefault().getNetworkOperatorName());
                        this.mPhone.getContext().registerReceiver(this.mProvisionBroadcastReceiver, new IntentFilter(this.mProvisionActionName));
                        cm.setProvisioningNotificationVisible(true, 0, this.mProvisionActionName);
                        setRadio(false);
                    }
                    log("onDataSetupComplete: SETUP complete type=" + apnContext.getApnType() + ", reason:" + apnContext.getReason());
                }
            } else {
                cause = ar.result;
                apn = apnContext.getApnSetting();
                String str = "onDataSetupComplete: error apn=%s cause=%s";
                Object[] objArr = new Object[2];
                objArr[0] = apn == null ? "unknown" : apn.apn;
                objArr[1] = cause;
                log(String.format(str, objArr));
                if (cause.isEventLoggable()) {
                    int cid = getCellLocationId();
                    EventLog.writeEvent(EventLogTags.PDP_SETUP_FAIL, new Object[]{Integer.valueOf(cause.ordinal()), Integer.valueOf(cid), Integer.valueOf(TelephonyManager.getDefault().getNetworkType())});
                }
                apn = apnContext.getApnSetting();
                this.mPhone.notifyPreciseDataConnectionFailed(apnContext.getReason(), apnContext.getApnType(), apn != null ? apn.apn : "unknown", cause.toString());
                Intent intent = new Intent("android.intent.action.REQUEST_NETWORK_FAILED");
                intent.putExtra("errorCode", cause.getErrorCode());
                intent.putExtra(APN_TYPE_KEY, apnContext.getApnType());
                notifyCarrierAppWithIntent(intent);
                if (cause.isRestartRadioFail() || apnContext.restartOnError(cause.getErrorCode())) {
                    log("Modem restarted.");
                    sendRestartRadio();
                }
                if (isPermanentFail(cause)) {
                    log("cause = " + cause + ", mark apn as permanent failed. apn = " + apn);
                    apnContext.markApnPermanentFailed(apn);
                }
                handleError = true;
            }
            if (handleError) {
                onDataSetupCompleteError(ar);
            }
            if (!this.mInternalDataEnabled) {
                cleanUpAllConnections(null);
            }
        }
    }

    private ApnContext getValidApnContext(AsyncResult ar, String logString) {
        if (ar != null && (ar.userObj instanceof Pair)) {
            Pair<ApnContext, Integer> pair = ar.userObj;
            ApnContext apnContext = pair.first;
            if (apnContext != null) {
                int generation = apnContext.getConnectionGeneration();
                log("getValidApnContext (" + logString + ") on " + apnContext + " got " + generation + " vs " + pair.second);
                if (generation == ((Integer) pair.second).intValue()) {
                    return apnContext;
                }
                log("ignoring obsolete " + logString);
                return null;
            }
        }
        throw new RuntimeException(logString + ": No apnContext");
    }

    private void onDataSetupCompleteError(AsyncResult ar) {
        ApnContext apnContext = getValidApnContext(ar, "onDataSetupCompleteError");
        if (apnContext != null) {
            long delay = apnContext.getDelayForNextApn(this.mFailFast);
            if (delay >= 0) {
                log("onDataSetupCompleteError: Try next APN. delay = " + delay);
                apnContext.setState(State.SCANNING);
                startAlarmForReconnect(delay, apnContext);
            } else {
                apnContext.setState(State.FAILED);
                this.mPhone.notifyDataConnection(PhoneInternalInterface.REASON_APN_FAILED, apnContext.getApnType());
                apnContext.setDataConnectionAc(null);
                log("onDataSetupCompleteError: Stop retrying APNs.");
            }
        }
    }

    private String[] getActivationAppName() {
        CarrierConfigManager configManager = (CarrierConfigManager) this.mPhone.getContext().getSystemService("carrier_config");
        PersistableBundle b = null;
        if (configManager != null) {
            b = configManager.getConfig();
        }
        if (b != null) {
            return b.getStringArray("sim_state_detection_carrier_app_string_array");
        }
        return CarrierConfigManager.getDefaultConfig().getStringArray("sim_state_detection_carrier_app_string_array");
    }

    private void onDataConnectionRedirected(String redirectUrl, HashMap<ApnContext, ConnectionParams> apnContextMap) {
        if (!TextUtils.isEmpty(redirectUrl)) {
            this.mRedirectUrl = redirectUrl;
            Intent intent = new Intent("android.intent.action.REDIRECTION_DETECTED");
            intent.putExtra(REDIRECTION_URL_KEY, redirectUrl);
            if (!isColdSimDetected() && !isOutOfCreditSimDetected() && checkCarrierAppAvailable(intent)) {
                log("Starting Activation Carrier app with redirectUrl : " + redirectUrl);
                for (ApnContext context : apnContextMap.keySet()) {
                    cleanUpConnection(true, context);
                    this.redirectApnContextSet.add(context);
                }
            }
        }
    }

    private void onDisconnectDone(AsyncResult ar) {
        ApnContext apnContext = getValidApnContext(ar, "onDisconnectDone");
        if (apnContext != null) {
            log("onDisconnectDone: EVENT_DISCONNECT_DONE apnContext=" + apnContext);
            apnContext.setState(State.IDLE);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            if (!isConnected()) {
                this.mIsPhysicalLinkUp = false;
                stopNetStatPoll();
            }
            if (isDisconnected() && this.mPhone.getServiceStateTracker().processPendingRadioPowerOffAfterDataOff()) {
                log("onDisconnectDone: radio will be turned off, no retries");
                apnContext.setApnSetting(null);
                apnContext.setDataConnectionAc(null);
                if (this.mDisconnectPendingCount > 0) {
                    this.mDisconnectPendingCount--;
                }
                if (this.mDisconnectPendingCount == 0) {
                    notifyDataDisconnectComplete();
                    notifyAllDataDisconnected();
                    notifyCarrierAppForRedirection();
                }
                return;
            }
            if (this.mAttached.get() && apnContext.isReady() && retryAfterDisconnected(apnContext)) {
                try {
                    SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
                } catch (RuntimeException e) {
                    log("Failed to set PUPPET_MASTER_RADIO_STRESS_TEST to false");
                }
                log("onDisconnectDone: attached, ready and retry after disconnect");
                if (TelBrand.IS_KDDI && this.mEnableApnCarNavi) {
                    startAlarmForReconnect(10000, apnContext);
                } else {
                    long delay = apnContext.getInterApnDelay(this.mFailFast);
                    if (TelBrand.IS_KDDI && DcFailCause.LOST_CONNECTION.toString().equals(apnContext.getReason())) {
                        delay = 1000;
                    }
                    if (delay > 0) {
                        startAlarmForReconnect(delay, apnContext);
                    }
                }
            } else {
                boolean restartRadioAfterProvisioning = this.mPhone.getContext().getResources().getBoolean(17956994);
                if (apnContext.isProvisioningApn() && restartRadioAfterProvisioning) {
                    log("onDisconnectDone: restartRadio after provisioning");
                    restartRadio();
                }
                apnContext.setApnSetting(null);
                apnContext.setDataConnectionAc(null);
                if (!isOnlySingleDcAllowed(this.mPhone.getServiceState().getRilDataRadioTechnology()) || PhoneInternalInterface.REASON_RADIO_TURNED_OFF.equals(apnContext.getReason())) {
                    log("onDisconnectDone: not retrying");
                } else {
                    log("onDisconnectDone: isOnlySigneDcAllowed true so setup single apn");
                    sendMessage(obtainMessage(270339, PhoneInternalInterface.REASON_SINGLE_PDN_ARBITRATION));
                }
            }
            if (this.mDisconnectPendingCount > 0) {
                this.mDisconnectPendingCount--;
            }
            if (this.mDisconnectPendingCount == 0) {
                apnContext.setConcurrentVoiceAndDataAllowed(this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed());
                notifyDataDisconnectComplete();
                notifyAllDataDisconnected();
                notifyCarrierAppForRedirection();
            }
        }
    }

    private void onDisconnectDcRetrying(AsyncResult ar) {
        ApnContext apnContext = getValidApnContext(ar, "onDisconnectDcRetrying");
        if (apnContext != null) {
            apnContext.setState(State.RETRYING);
            log("onDisconnectDcRetrying: apnContext=" + apnContext);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        }
    }

    private void onVoiceCallStarted() {
        log("onVoiceCallStarted");
        this.mInVoiceCall = true;
        if (isConnected() && !this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
            log("onVoiceCallStarted stop polling");
            stopNetStatPoll();
            stopDataStallAlarm();
            notifyDataConnection(PhoneInternalInterface.REASON_VOICE_CALL_STARTED);
        }
    }

    private void onVoiceCallEnded() {
        log("onVoiceCallEnded");
        this.mInVoiceCall = false;
        if (isConnected()) {
            if (this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                resetPollStats();
            } else {
                startNetStatPoll();
                startDataStallAlarm(false);
                notifyDataConnection(PhoneInternalInterface.REASON_VOICE_CALL_ENDED);
            }
        }
        setupDataOnConnectableApns(PhoneInternalInterface.REASON_VOICE_CALL_ENDED);
    }

    private void onCleanUpConnection(boolean tearDown, int apnId, String reason) {
        log("onCleanUpConnection");
        ApnContext apnContext = (ApnContext) this.mApnContextsById.get(apnId);
        if (apnContext != null) {
            apnContext.setReason(reason);
            cleanUpConnection(tearDown, apnContext);
        }
    }

    private boolean isConnected() {
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (apnContext.getState() == State.CONNECTED) {
                return true;
            }
        }
        return false;
    }

    public boolean isDisconnected() {
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (TelBrand.IS_DCM && apnContext.getApnType().equals("ims")) {
                log("isDisconnected: ignore ims");
            } else if (!apnContext.isDisconnected()) {
                return false;
            }
        }
        return true;
    }

    private void notifyDataConnection(String reason) {
        log("notifyDataConnection: reason=" + reason);
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (this.mAttached.get() && apnContext.isReady()) {
                log("notifyDataConnection: type:" + apnContext.getApnType());
                this.mPhone.notifyDataConnection(reason != null ? reason : apnContext.getReason(), apnContext.getApnType());
            }
        }
        notifyOffApnsOfAvailability(reason);
    }

    /* Access modifiers changed, original: protected */
    public void setDataProfilesAsNeeded() {
        log("setDataProfilesAsNeeded");
        if (this.mAllApnSettings != null && !this.mAllApnSettings.isEmpty()) {
            ArrayList<DataProfile> dps = new ArrayList();
            for (ApnSetting apn : this.mAllApnSettings) {
                if (apn.modemCognitive) {
                    DataProfile dp = new DataProfile(apn, this.mPhone.getServiceState().getDataRoaming());
                    boolean isDup = false;
                    for (DataProfile dpIn : dps) {
                        if (dp.equals(dpIn)) {
                            isDup = true;
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

    /* Access modifiers changed, original: protected */
    public void createAllApnList() {
        this.mMvnoMatched = false;
        this.mAllApnSettings = new ArrayList();
        IccRecords r = (IccRecords) this.mIccRecords.get();
        String operator = this.mPhone.getOperatorNumeric();
        if (operator != null) {
            String selection = "numeric = '" + operator + "'";
            log("createAllApnList: selection=" + selection);
            Cursor cursor = this.mPhone.getContext().getContentResolver().query(Carriers.CONTENT_URI, null, selection, null, "_id");
            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    this.mAllApnSettings = createApnList(cursor);
                }
                cursor.close();
            }
        }
        addEmergencyApnSetting();
        dedupeApnSettings();
        if (this.mAllApnSettings.isEmpty()) {
            log("createAllApnList: No APN found for carrier: " + operator);
            this.mPreferredApn = null;
        } else {
            this.mPreferredApn = getPreferredApn();
            if (!(this.mPreferredApn == null || this.mPreferredApn.numeric.equals(operator))) {
                this.mPreferredApn = null;
                setPreferredApn(-1);
            }
            log("createAllApnList: mPreferredApn=" + this.mPreferredApn);
        }
        log("createAllApnList: X mAllApnSettings=" + this.mAllApnSettings);
        setDataProfilesAsNeeded();
    }

    /* Access modifiers changed, original: protected */
    public void dedupeApnSettings() {
        ArrayList<ApnSetting> resultApns = new ArrayList();
        for (int i = 0; i < this.mAllApnSettings.size() - 1; i++) {
            ApnSetting first = (ApnSetting) this.mAllApnSettings.get(i);
            int j = i + 1;
            while (j < this.mAllApnSettings.size()) {
                ApnSetting second = (ApnSetting) this.mAllApnSettings.get(j);
                if (apnsSimilar(first, second)) {
                    ApnSetting newApn = mergeApns(first, second);
                    this.mAllApnSettings.set(i, newApn);
                    first = newApn;
                    this.mAllApnSettings.remove(j);
                } else {
                    j++;
                }
            }
        }
    }

    private boolean apnTypeSameAny(ApnSetting first, ApnSetting second) {
        int index1 = 0;
        while (index1 < first.types.length) {
            int index2 = 0;
            while (index2 < second.types.length) {
                if (first.types[index1].equals(CharacterSets.MIMENAME_ANY_CHARSET) || second.types[index2].equals(CharacterSets.MIMENAME_ANY_CHARSET) || first.types[index1].equals(second.types[index2])) {
                    return true;
                }
                index2++;
            }
            index1++;
        }
        return false;
    }

    private boolean apnsSimilar(ApnSetting first, ApnSetting second) {
        if (!first.canHandleType("dun") && !second.canHandleType("dun") && Objects.equals(first.apn, second.apn) && !apnTypeSameAny(first, second) && xorEquals(first.proxy, second.proxy) && xorEquals(first.port, second.port) && xorEquals(first.protocol, second.protocol) && xorEquals(first.roamingProtocol, second.roamingProtocol) && first.carrierEnabled == second.carrierEnabled && first.bearerBitmask == second.bearerBitmask && first.profileId == second.profileId && Objects.equals(first.mvnoType, second.mvnoType) && Objects.equals(first.mvnoMatchData, second.mvnoMatchData) && xorEquals(first.mmsc, second.mmsc) && xorEquals(first.mmsProxy, second.mmsProxy)) {
            return xorEquals(first.mmsPort, second.mmsPort);
        }
        return false;
    }

    private boolean xorEquals(String first, String second) {
        if (Objects.equals(first, second) || TextUtils.isEmpty(first)) {
            return true;
        }
        return TextUtils.isEmpty(second);
    }

    private ApnSetting mergeApns(ApnSetting dest, ApnSetting src) {
        String roamingProtocol;
        int id = dest.id;
        ArrayList<String> resultTypes = new ArrayList();
        resultTypes.addAll(Arrays.asList(dest.types));
        for (String srcType : src.types) {
            if (!resultTypes.contains(srcType)) {
                resultTypes.add(srcType);
            }
            if (srcType.equals("default")) {
                id = src.id;
            }
        }
        String mmsc = TextUtils.isEmpty(dest.mmsc) ? src.mmsc : dest.mmsc;
        String mmsProxy = TextUtils.isEmpty(dest.mmsProxy) ? src.mmsProxy : dest.mmsProxy;
        String mmsPort = TextUtils.isEmpty(dest.mmsPort) ? src.mmsPort : dest.mmsPort;
        String proxy = TextUtils.isEmpty(dest.proxy) ? src.proxy : dest.proxy;
        String port = TextUtils.isEmpty(dest.port) ? src.port : dest.port;
        String protocol = src.protocol.equals("IPV4V6") ? src.protocol : dest.protocol;
        if (src.roamingProtocol.equals("IPV4V6")) {
            roamingProtocol = src.roamingProtocol;
        } else {
            roamingProtocol = dest.roamingProtocol;
        }
        int bearerBitmask = (dest.bearerBitmask == 0 || src.bearerBitmask == 0) ? 0 : dest.bearerBitmask | src.bearerBitmask;
        return new ApnSetting(id, dest.numeric, dest.carrier, dest.apn, proxy, port, mmsc, mmsProxy, mmsPort, dest.user, dest.password, dest.authType, (String[]) resultTypes.toArray(new String[0]), protocol, roamingProtocol, dest.carrierEnabled, 0, bearerBitmask, dest.profileId, !dest.modemCognitive ? src.modemCognitive : true, dest.maxConns, dest.waitTime, dest.maxConnsTime, dest.mtu, dest.mvnoType, dest.mvnoMatchData);
    }

    private DcAsyncChannel createDataConnection() {
        log("createDataConnection E");
        int id = this.mUniqueIdGenerator.getAndIncrement();
        DataConnection conn = DataConnection.makeDataConnection(this.mPhone, id, this, this.mDcTesterFailBringUpAll, this.mDcc);
        this.mDataConnections.put(Integer.valueOf(id), conn);
        DcAsyncChannel dcac = new DcAsyncChannel(conn, this.LOG_TAG);
        int status = dcac.fullyConnectSync(this.mPhone.getContext(), this, conn.getHandler());
        if (status == 0) {
            this.mDataConnectionAcHashMap.put(Integer.valueOf(dcac.getDataConnectionIdSync()), dcac);
        } else {
            loge("createDataConnection: Could not connect to dcac=" + dcac + " status=" + status);
        }
        log("createDataConnection() X id=" + id + " dc=" + conn);
        return dcac;
    }

    private void destroyDataConnections() {
        if (this.mDataConnections != null) {
            log("destroyDataConnections: clear mDataConnectionList");
            this.mDataConnections.clear();
            return;
        }
        log("destroyDataConnections: mDataConnecitonList is empty, ignore");
    }

    private ArrayList<ApnSetting> buildWaitingApns(String requestedApnType, int radioTech) {
        log("buildWaitingApns: E requestedApnType=" + requestedApnType);
        ArrayList<ApnSetting> apnList = new ArrayList();
        if (requestedApnType.equals("dun")) {
            ApnSetting dun = fetchDunApn();
            if (dun != null) {
                apnList.add(dun);
                log("buildWaitingApns: X added APN_TYPE_DUN apnList=" + apnList);
                return apnList;
            }
        }
        if (TelBrand.IS_DCM && requestedApnType.equals("ims") && this.mAllApnSettings != null && !this.mAllApnSettings.isEmpty()) {
            for (ApnSetting imsApn : this.mAllApnSettings) {
                if (imsApn.apn.equals("ims") && imsApn.canHandleType(requestedApnType)) {
                    apnList.add(imsApn);
                    log("buildWaitingApns: X added APN_TYPE_IMS apnList=" + apnList);
                    return apnList;
                }
            }
        }
        String operator = this.mPhone.getOperatorNumeric();
        if (TelBrand.IS_DCM && !TelBrand.IS_SHARP) {
            if (this.mCanSetPreferApn && this.mPreferredApn != null) {
                log("buildWaitingApns: Preferred APN:" + operator + ":" + this.mPreferredApn.numeric + ":" + this.mPreferredApn);
                if (this.mPreferredApn.numeric != null && this.mPreferredApn.numeric.equals(operator) && ServiceState.bitmaskHasTech(this.mPreferredApn.bearerBitmask, radioTech) && this.mPreferredApn.canHandleType(requestedApnType)) {
                    apnList.add(this.mPreferredApn);
                    log("buildWaitingApns: X added preferred apnList=" + apnList);
                    return apnList;
                }
            }
            log("buildWaitingApns: no preferred APN");
            return apnList;
        } else if (TelBrand.IS_KDDI && this.mCanSetPreferApn && this.mPreferredApn != null && this.mPreferredApn.canHandleType(requestedApnType)) {
            log("buildWaitingApns: Preferred APN:" + operator + ":" + this.mPreferredApn.numeric + ":" + this.mPreferredApn);
            if (this.mPreferredApn.numeric != null && this.mPreferredApn.numeric.equals(operator) && ServiceState.bitmaskHasTech(this.mPreferredApn.bearerBitmask, radioTech)) {
                apnList.add(this.mPreferredApn);
                log("buildWaitingApns: X added preferred apnList=" + apnList);
                return apnList;
            }
            log("buildWaitingApns: no preferred APN");
            return apnList;
        } else {
            boolean usePreferred;
            try {
                usePreferred = !this.mPhone.getContext().getResources().getBoolean(17956993);
            } catch (NotFoundException e) {
                log("buildWaitingApns: usePreferred NotFoundException set to true");
                usePreferred = true;
            }
            if (usePreferred) {
                this.mPreferredApn = getPreferredApn();
            }
            log("buildWaitingApns: usePreferred=" + usePreferred + " canSetPreferApn=" + this.mCanSetPreferApn + " mPreferredApn=" + this.mPreferredApn + " operator=" + operator + " radioTech=" + radioTech + " IccRecords r=" + this.mIccRecords);
            if (usePreferred && this.mCanSetPreferApn && this.mPreferredApn != null && this.mPreferredApn.canHandleType(requestedApnType)) {
                log("buildWaitingApns: Preferred APN:" + operator + ":" + this.mPreferredApn.numeric + ":" + this.mPreferredApn);
                if (!this.mPreferredApn.numeric.equals(operator)) {
                    log("buildWaitingApns: no preferred APN");
                    setPreferredApn(-1);
                    this.mPreferredApn = null;
                } else if (ServiceState.bitmaskHasTech(this.mPreferredApn.bearerBitmask, radioTech)) {
                    apnList.add(this.mPreferredApn);
                    log("buildWaitingApns: X added preferred apnList=" + apnList);
                    return apnList;
                } else {
                    log("buildWaitingApns: no preferred APN");
                    setPreferredApn(-1);
                    this.mPreferredApn = null;
                }
            }
            if (this.mAllApnSettings != null) {
                log("buildWaitingApns: mAllApnSettings=" + this.mAllApnSettings);
                for (ApnSetting apn : this.mAllApnSettings) {
                    if (!apn.canHandleType(requestedApnType)) {
                        log("buildWaitingApns: couldn't handle requested ApnType=" + requestedApnType);
                    } else if (ServiceState.bitmaskHasTech(apn.bearerBitmask, radioTech)) {
                        log("buildWaitingApns: adding apn=" + apn);
                        apnList.add(apn);
                    } else {
                        log("buildWaitingApns: bearerBitmask:" + apn.bearerBitmask + " does " + "not include radioTech:" + radioTech);
                    }
                }
            } else {
                loge("mAllApnSettings is null!");
            }
            log("buildWaitingApns: " + apnList.size() + " APNs in the list: " + apnList);
            return apnList;
        }
    }

    private String apnListToString(ArrayList<ApnSetting> apns) {
        StringBuilder result = new StringBuilder();
        int size = apns.size();
        for (int i = 0; i < size; i++) {
            result.append('[').append(((ApnSetting) apns.get(i)).toString()).append(']');
        }
        return result.toString();
    }

    /* Access modifiers changed, original: protected */
    public void setPreferredApn(int pos) {
        if (this.mCanSetPreferApn) {
            Uri uri = Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, Long.toString((long) this.mPhone.getSubId()));
            log("setPreferredApn: delete");
            ContentResolver resolver = this.mPhone.getContext().getContentResolver();
            resolver.delete(uri, null, null);
            if (pos >= 0) {
                log("setPreferredApn: insert");
                ContentValues values = new ContentValues();
                values.put("apn_id", Integer.valueOf(pos));
                resolver.insert(uri, values);
            }
            return;
        }
        log("setPreferredApn: X !canSEtPreferApn");
    }

    /* Access modifiers changed, original: protected */
    public ApnSetting getPreferredApn() {
        if (this.mAllApnSettings == null || this.mAllApnSettings.isEmpty()) {
            log("getPreferredApn: mAllApnSettings is " + (this.mAllApnSettings == null ? "null" : "empty"));
            return null;
        }
        int count;
        Cursor cursor = this.mPhone.getContext().getContentResolver().query(Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, Long.toString((long) this.mPhone.getSubId())), new String[]{"_id", "name", "apn"}, null, null, Carriers.DEFAULT_SORT_ORDER);
        if (cursor != null) {
            this.mCanSetPreferApn = true;
        } else {
            this.mCanSetPreferApn = false;
        }
        StringBuilder append = new StringBuilder().append("getPreferredApn: mRequestedApnType=").append(this.mRequestedApnType).append(" cursor=").append(cursor).append(" cursor.count=");
        if (cursor != null) {
            count = cursor.getCount();
        } else {
            count = 0;
        }
        log(append.append(count).toString());
        if (this.mCanSetPreferApn && cursor.getCount() > 0) {
            cursor.moveToFirst();
            int pos = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));
            for (ApnSetting p : this.mAllApnSettings) {
                log("getPreferredApn: apnSetting=" + p);
                if (p.id == pos && p.canHandleType(this.mRequestedApnType)) {
                    log("getPreferredApn: X found apnSetting" + p);
                    cursor.close();
                    return p;
                }
            }
        }
        if (cursor != null) {
            cursor.close();
        }
        log("getPreferredApn: X not found");
        return null;
    }

    public void handleMessage(Message msg) {
        boolean enabled;
        Bundle bundle;
        switch (msg.what) {
            case 69636:
                log("DISCONNECTED_CONNECTED: msg=" + msg);
                DcAsyncChannel dcac = msg.obj;
                this.mDataConnectionAcHashMap.remove(Integer.valueOf(dcac.getDataConnectionIdSync()));
                dcac.disconnected();
                return;
            case 270336:
                onDataSetupComplete((AsyncResult) msg.obj);
                return;
            case 270337:
                break;
            case 270338:
                int subId = this.mPhone.getSubId();
                if (this.mSubscriptionManager.isActiveSubId(subId)) {
                    onRecordsLoadedOrSubIdChanged();
                    return;
                } else {
                    log("Ignoring EVENT_RECORDS_LOADED as subId is not valid: " + subId);
                    return;
                }
            case 270339:
                if (msg.obj instanceof ApnContext) {
                    onTrySetupData((ApnContext) msg.obj);
                    return;
                } else if (msg.obj instanceof String) {
                    onTrySetupData((String) msg.obj);
                    return;
                } else {
                    loge("EVENT_TRY_SETUP request w/o apnContext or String");
                    return;
                }
            case 270340:
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
            case 270345:
                onDataConnectionDetached();
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
                onDisconnectDone((AsyncResult) msg.obj);
                return;
            case 270352:
                onDataConnectionAttached();
                return;
            case 270353:
                onDataStallAlarm(msg.arg1);
                return;
            case 270354:
                doRecovery();
                return;
            case 270355:
                onApnChanged();
                return;
            case 270358:
                log("EVENT_PS_RESTRICT_ENABLED " + this.mIsPsRestricted);
                stopNetStatPoll();
                stopDataStallAlarm();
                this.mIsPsRestricted = true;
                return;
            case 270359:
                log("EVENT_PS_RESTRICT_DISABLED " + this.mIsPsRestricted);
                this.mIsPsRestricted = false;
                if (isConnected()) {
                    startNetStatPoll();
                    startDataStallAlarm(false);
                    return;
                }
                if (this.mState == State.FAILED) {
                    cleanUpAllConnections(false, PhoneInternalInterface.REASON_PS_RESTRICT_ENABLED);
                    this.mReregisterOnReconnectFailure = false;
                }
                ApnContext apnContext = (ApnContext) this.mApnContextsById.get(0);
                if (apnContext != null) {
                    apnContext.setReason(PhoneInternalInterface.REASON_PS_RESTRICT_ENABLED);
                    trySetupData(apnContext);
                    return;
                }
                loge("**** Default ApnContext not found ****");
                if (Build.IS_DEBUGGABLE) {
                    throw new RuntimeException("Default ApnContext not found");
                }
                return;
            case 270360:
                boolean tearDown = msg.arg1 != 0;
                log("EVENT_CLEAN_UP_CONNECTION tearDown=" + tearDown);
                if (msg.obj instanceof ApnContext) {
                    cleanUpConnection(tearDown, (ApnContext) msg.obj);
                    return;
                } else {
                    onCleanUpConnection(tearDown, msg.arg2, (String) msg.obj);
                    return;
                }
            case 270362:
                restartRadio();
                return;
            case 270363:
                onSetInternalDataEnabled(msg.arg1 == 1, (Message) msg.obj);
                return;
            case 270364:
                log("EVENT_RESET_DONE");
                onResetDone((AsyncResult) msg.obj);
                return;
            case 270365:
                if (!(msg.obj == null || (msg.obj instanceof String))) {
                    msg.obj = null;
                }
                onCleanUpAllConnections((String) msg.obj);
                return;
            case 270366:
                enabled = msg.arg1 == 1;
                log("CMD_SET_USER_DATA_ENABLE enabled=" + enabled);
                onSetUserDataEnabled(enabled);
                return;
            case 270367:
                boolean met = msg.arg1 == 1;
                log("CMD_SET_DEPENDENCY_MET met=" + met);
                bundle = msg.getData();
                if (bundle != null) {
                    String apnType = (String) bundle.get(APN_TYPE_KEY);
                    if (apnType != null) {
                        onSetDependencyMet(apnType, met);
                        return;
                    }
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
                onDisconnectDcRetrying((AsyncResult) msg.obj);
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
                enabled = sEnableFailFastRefCounter > 0;
                log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: enabled=" + enabled + " sEnableFailFastRefCounter=" + sEnableFailFastRefCounter);
                if (this.mFailFast != enabled) {
                    this.mFailFast = enabled;
                    this.mDataStallDetectionEnabled = !enabled;
                    if (this.mDataStallDetectionEnabled && getOverallState() == State.CONNECTED && (!this.mInVoiceCall || this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed())) {
                        log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: start data stall");
                        stopDataStallAlarm();
                        startDataStallAlarm(false);
                        return;
                    }
                    log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: stop data stall");
                    stopDataStallAlarm();
                    return;
                }
                return;
            case 270373:
                bundle = msg.getData();
                if (bundle != null) {
                    try {
                        this.mProvisioningUrl = (String) bundle.get("provisioningUrl");
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
                boolean isProvApn;
                log("CMD_IS_PROVISIONING_APN");
                CharSequence apnType2 = null;
                try {
                    bundle = msg.getData();
                    if (bundle != null) {
                        apnType2 = (String) bundle.get(APN_TYPE_KEY);
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
                ApnContext apnCtx = (ApnContext) this.mApnContextsById.get(0);
                if (apnCtx.isProvisioningApn() && apnCtx.isConnectedOrConnecting()) {
                    if (this.mProvisioningApnAlarmTag == msg.arg1) {
                        log("EVENT_PROVISIONING_APN_ALARM: Disconnecting");
                        this.mIsProvisioning = false;
                        this.mProvisioningUrl = null;
                        stopProvisioningApnAlarm();
                        sendCleanUpConnection(true, apnCtx);
                        return;
                    }
                    log("EVENT_PROVISIONING_APN_ALARM: ignore stale tag, mProvisioningApnAlarmTag:" + this.mProvisioningApnAlarmTag + " != arg1:" + msg.arg1);
                    return;
                }
                log("EVENT_PROVISIONING_APN_ALARM: Not connected ignore");
                return;
            case 270376:
                if (msg.arg1 == 1) {
                    handleStartNetStatPoll((Activity) msg.obj);
                    return;
                } else if (msg.arg1 == 0) {
                    handleStopNetStatPoll((Activity) msg.obj);
                    return;
                } else {
                    return;
                }
            case 270377:
                setupDataOnConnectableApns(PhoneInternalInterface.REASON_NW_TYPE_CHANGED, RetryFailures.ONLY_ON_CHANGE);
                return;
            case 270378:
                if (this.mProvisioningSpinner == msg.obj) {
                    this.mProvisioningSpinner.dismiss();
                    this.mProvisioningSpinner = null;
                    return;
                }
                return;
            case 270379:
                onDeviceProvisionedChange();
                return;
            case 270380:
                AsyncResult ar = msg.obj;
                String url = ar.userObj;
                log("dataConnectionTracker.handleMessage: EVENT_REDIRECTION_DETECTED=" + url);
                onDataConnectionRedirected(url, (HashMap) ar.result);
                break;
            default:
                Rlog.e("DcTracker", "Unhandled event=" + msg);
                return;
        }
        onRadioAvailable();
    }

    private int getApnProfileID(String apnType) {
        if (TextUtils.equals(apnType, "ims")) {
            return 2;
        }
        if (TextUtils.equals(apnType, "fota")) {
            return 3;
        }
        if (TextUtils.equals(apnType, "cbs")) {
            return 4;
        }
        if (!TextUtils.equals(apnType, "ia") && TextUtils.equals(apnType, "dun")) {
            return 1;
        }
        return 0;
    }

    private int getCellLocationId() {
        CellLocation loc = this.mPhone.getCellLocation();
        if (loc == null) {
            return -1;
        }
        if (loc instanceof GsmCellLocation) {
            return ((GsmCellLocation) loc).getCid();
        }
        if (loc instanceof CdmaCellLocation) {
            return ((CdmaCellLocation) loc).getBaseStationId();
        }
        return -1;
    }

    private IccRecords getUiccRecords(int appFamily) {
        return this.mUiccController.getIccRecords(this.mPhone.getPhoneId(), appFamily);
    }

    private void onUpdateIcc() {
        if (this.mUiccController != null) {
            IccRecords newIccRecords = this.mPhone.getIccRecords();
            IccRecords r = (IccRecords) this.mIccRecords.get();
            if (r != newIccRecords) {
                if (r != null) {
                    log("Removing stale icc objects.");
                    r.unregisterForRecordsLoaded(this);
                    this.mIccRecords.set(null);
                }
                if (newIccRecords == null) {
                    onSimNotReady();
                } else if (this.mSubscriptionManager.isActiveSubId(this.mPhone.getSubId())) {
                    log("New records found.");
                    this.mIccRecords.set(newIccRecords);
                    newIccRecords.registerForRecordsLoaded(this, 270338, null);
                    SubscriptionController.getInstance().setSimProvisioningStatus(0, this.mPhone.getSubId());
                }
            }
        }
    }

    public void update() {
        log("update sub = " + this.mPhone.getSubId());
        log("update(): Active DDS, register for all events now!");
        onUpdateIcc();
        this.mUserDataEnabled = getDataEnabled();
        this.mAutoAttachOnCreation.set(false);
        ((GsmCdmaPhone) this.mPhone).updateCurrentCarrierInProvider();
    }

    public void cleanUpAllConnections(String cause) {
        cleanUpAllConnections(cause, null);
    }

    public void updateRecords() {
        onUpdateIcc();
    }

    public void cleanUpAllConnections(String cause, Message disconnectAllCompleteMsg) {
        log("cleanUpAllConnections");
        if (disconnectAllCompleteMsg != null) {
            this.mDisconnectAllCompleteMsgList.add(disconnectAllCompleteMsg);
        }
        Message msg = obtainMessage(270365);
        msg.obj = cause;
        sendMessage(msg);
    }

    private boolean checkCarrierAppAvailable(Intent intent) {
        String[] activationApp = getActivationAppName();
        if (activationApp == null || activationApp.length != 2) {
            return false;
        }
        intent.setClassName(activationApp[0], activationApp[1]);
        if (!this.mPhone.getContext().getPackageManager().queryBroadcastReceivers(intent, 65536).isEmpty()) {
            return true;
        }
        loge("Activation Carrier app is configured, but not available: " + activationApp[0] + "." + activationApp[1]);
        return false;
    }

    private boolean notifyCarrierAppWithIntent(Intent intent) {
        if (this.mDisconnectPendingCount != 0) {
            loge("Wait for pending disconnect requests done");
            return false;
        } else if (checkCarrierAppAvailable(intent)) {
            intent.putExtra("subscription", this.mPhone.getSubId());
            intent.addFlags(268435456);
            try {
                this.mPhone.getContext().sendBroadcast(intent);
                log("send Intent to Carrier app with action: " + intent.getAction());
                return true;
            } catch (ActivityNotFoundException e) {
                loge("sendBroadcast failed: " + e);
                return false;
            }
        } else {
            loge("Carrier app is unavailable");
            return false;
        }
    }

    private void notifyCarrierAppForRedirection() {
        if (!isColdSimDetected() && !isOutOfCreditSimDetected() && this.mRedirectUrl != null) {
            Intent intent = new Intent("android.intent.action.REDIRECTION_DETECTED");
            intent.putExtra(REDIRECTION_URL_KEY, this.mRedirectUrl);
            if (notifyCarrierAppWithIntent(intent)) {
                this.mRedirectUrl = null;
            }
        }
    }

    private void notifyDataDisconnectComplete() {
        log("notifyDataDisconnectComplete");
        for (Message m : this.mDisconnectAllCompleteMsgList) {
            m.sendToTarget();
        }
        this.mDisconnectAllCompleteMsgList.clear();
    }

    private void notifyAllDataDisconnected() {
        sEnableFailFastRefCounter = 0;
        this.mFailFast = false;
        this.mAllDataDisconnectedRegistrants.notifyRegistrants();
    }

    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        this.mAllDataDisconnectedRegistrants.addUnique(h, what, obj);
        if (isDisconnected()) {
            log("notify All Data Disconnected");
            notifyAllDataDisconnected();
        }
    }

    public void unregisterForAllDataDisconnected(Handler h) {
        this.mAllDataDisconnectedRegistrants.remove(h);
    }

    private void onSetInternalDataEnabled(boolean enabled, Message onCompleteMsg) {
        log("onSetInternalDataEnabled: enabled=" + enabled);
        boolean sendOnComplete = true;
        synchronized (this.mDataEnabledLock) {
            this.mInternalDataEnabled = enabled;
            if (enabled) {
                log("onSetInternalDataEnabled: changed to enabled, try to setup data call");
                onTrySetupData(PhoneInternalInterface.REASON_DATA_ENABLED);
            } else {
                sendOnComplete = false;
                log("onSetInternalDataEnabled: changed to disabled, cleanUpAllConnections");
                cleanUpAllConnections(null, onCompleteMsg);
            }
        }
        if (sendOnComplete && onCompleteMsg != null) {
            onCompleteMsg.sendToTarget();
        }
    }

    public boolean setInternalDataEnabledFlag(boolean enable) {
        log("setInternalDataEnabledFlag(" + enable + ")");
        if (this.mInternalDataEnabled != enable) {
            this.mInternalDataEnabled = enable;
        }
        return true;
    }

    public boolean setInternalDataEnabled(boolean enable) {
        return setInternalDataEnabled(enable, null);
    }

    public boolean setInternalDataEnabled(boolean enable, Message onCompleteMsg) {
        log("setInternalDataEnabled(" + enable + ")");
        Message msg = obtainMessage(270363, onCompleteMsg);
        msg.arg1 = enable ? 1 : 0;
        sendMessage(msg);
        return true;
    }

    public void setDataAllowed(boolean enable, Message response) {
        log("setDataAllowed: enable=" + enable);
        this.isCleanupRequired.set(!enable);
        this.mPhone.mCi.setDataAllowed(enable, response);
        this.mInternalDataEnabled = enable;
    }

    /* Access modifiers changed, original: protected */
    public void log(String s) {
        Rlog.d(this.LOG_TAG, "[" + this.mPhone.getPhoneId() + "]" + s);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String s) {
        Rlog.e(this.LOG_TAG, "[" + this.mPhone.getPhoneId() + "]" + s);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DcTracker:");
        pw.println(" RADIO_TESTS=false");
        pw.println(" mInternalDataEnabled=" + this.mInternalDataEnabled);
        pw.println(" mUserDataEnabled=" + this.mUserDataEnabled);
        pw.println(" sPolicyDataEnabed=" + sPolicyDataEnabled);
        pw.flush();
        pw.println(" mRequestedApnType=" + this.mRequestedApnType);
        pw.println(" mPhone=" + this.mPhone.getPhoneName());
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
        pw.println(" mAutoAttachOnCreation=" + this.mAutoAttachOnCreation.get());
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
            Set<Entry<Integer, DataConnection>> mDcSet = this.mDataConnections.entrySet();
            pw.println(" mDataConnections: count=" + mDcSet.size());
            for (Entry<Integer, DataConnection> entry : mDcSet) {
                pw.printf(" *** mDataConnection[%d] \n", new Object[]{entry.getKey()});
                ((DataConnection) entry.getValue()).dump(fd, pw, args);
            }
        } else {
            pw.println("mDataConnections=null");
        }
        pw.println(" ***************************************");
        pw.flush();
        HashMap<String, Integer> apnToDcId = this.mApnToDataConnectionId;
        if (apnToDcId != null) {
            Set<Entry<String, Integer>> apnToDcIdSet = apnToDcId.entrySet();
            pw.println(" mApnToDataConnectonId size=" + apnToDcIdSet.size());
            for (Entry<String, Integer> entry2 : apnToDcIdSet) {
                pw.printf(" mApnToDataConnectonId[%s]=%d\n", new Object[]{entry2.getKey(), entry2.getValue()});
            }
        } else {
            pw.println("mApnToDataConnectionId=null");
        }
        pw.println(" ***************************************");
        pw.flush();
        ConcurrentHashMap<String, ApnContext> apnCtxs = this.mApnContexts;
        if (apnCtxs != null) {
            Set<Entry<String, ApnContext>> apnCtxsSet = apnCtxs.entrySet();
            pw.println(" mApnContexts size=" + apnCtxsSet.size());
            for (Entry<String, ApnContext> entry3 : apnCtxsSet) {
                ((ApnContext) entry3.getValue()).dump(fd, pw, args);
            }
            pw.println(" ***************************************");
        } else {
            pw.println(" mApnContexts=null");
        }
        pw.flush();
        ArrayList<ApnSetting> apnSettings = this.mAllApnSettings;
        if (apnSettings != null) {
            pw.println(" mAllApnSettings size=" + apnSettings.size());
            for (int i = 0; i < apnSettings.size(); i++) {
                pw.printf(" mAllApnSettings[%d]: %s\n", new Object[]{Integer.valueOf(i), apnSettings.get(i)});
            }
            pw.flush();
        } else {
            pw.println(" mAllApnSettings=null");
        }
        pw.println(" mPreferredApn=" + this.mPreferredApn);
        pw.println(" mIsPsRestricted=" + this.mIsPsRestricted);
        pw.println(" mIsDisposed=" + this.mIsDisposed);
        pw.println(" mIntentReceiver=" + this.mIntentReceiver);
        pw.println(" mReregisterOnReconnectFailure=" + this.mReregisterOnReconnectFailure);
        pw.println(" canSetPreferApn=" + this.mCanSetPreferApn);
        pw.println(" mApnObserver=" + this.mApnObserver);
        pw.println(" getOverallState=" + getOverallState());
        pw.println(" mDataConnectionAsyncChannels=%s\n" + this.mDataConnectionAcHashMap);
        pw.println(" mAttached=" + this.mAttached.get());
        pw.flush();
    }

    public String[] getPcscfAddress(String apnType) {
        log("getPcscfAddress()");
        if (apnType == null) {
            log("apnType is null, return null");
            return null;
        }
        ApnContext apnContext;
        if (TextUtils.equals(apnType, "emergency")) {
            apnContext = (ApnContext) this.mApnContextsById.get(9);
        } else if (TextUtils.equals(apnType, "ims")) {
            apnContext = (ApnContext) this.mApnContextsById.get(5);
        } else {
            log("apnType is invalid, return null");
            return null;
        }
        if (apnContext == null) {
            log("apnContext is null, return null");
            return null;
        }
        DcAsyncChannel dcac = apnContext.getDcAc();
        if (dcac == null) {
            return null;
        }
        String[] result = dcac.getPcscfAddr();
        for (int i = 0; i < result.length; i++) {
            log("Pcscf[" + i + "]: " + result[i]);
        }
        return result;
    }

    private void initEmergencyApnSetting() {
        Cursor cursor = this.mPhone.getContext().getContentResolver().query(Carriers.CONTENT_URI, null, "type=\"emergency\"", null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0 && cursor.moveToFirst()) {
                this.mEmergencyApn = makeApnSetting(cursor);
            }
            cursor.close();
        }
    }

    /* Access modifiers changed, original: protected */
    public void addEmergencyApnSetting() {
        if (this.mEmergencyApn == null) {
            return;
        }
        if (this.mAllApnSettings == null) {
            this.mAllApnSettings = new ArrayList();
            return;
        }
        boolean hasEmergencyApn = false;
        for (ApnSetting apn : this.mAllApnSettings) {
            if (ArrayUtils.contains(apn.types, "emergency")) {
                hasEmergencyApn = true;
                break;
            }
        }
        if (hasEmergencyApn) {
            log("addEmergencyApnSetting - E-APN setting is already present");
        } else {
            this.mAllApnSettings.add(this.mEmergencyApn);
        }
    }

    /* Access modifiers changed, original: protected */
    public void cleanUpConnectionsOnUpdatedApns(boolean tearDown) {
        log("cleanUpConnectionsOnUpdatedApns: tearDown=" + tearDown);
        if (this.mAllApnSettings.isEmpty()) {
            cleanUpAllConnections(tearDown, PhoneInternalInterface.REASON_APN_CHANGED);
        } else {
            for (ApnContext apnContext : this.mApnContexts.values()) {
                boolean cleanUpApn = true;
                ArrayList<ApnSetting> currentWaitingApns = apnContext.getWaitingApns();
                if (currentWaitingApns != null && !apnContext.isDisconnected()) {
                    ArrayList<ApnSetting> waitingApns = buildWaitingApns(apnContext.getApnType(), this.mPhone.getServiceState().getRilDataRadioTechnology());
                    if (waitingApns.size() == currentWaitingApns.size()) {
                        cleanUpApn = false;
                        for (int i = 0; i < waitingApns.size(); i++) {
                            if (!((ApnSetting) currentWaitingApns.get(i)).equals(waitingApns.get(i))) {
                                cleanUpApn = true;
                                apnContext.setWaitingApns(waitingApns);
                                break;
                            }
                        }
                    }
                }
                if (cleanUpApn) {
                    apnContext.setReason(PhoneInternalInterface.REASON_APN_CHANGED);
                    cleanUpConnection(true, apnContext);
                }
            }
        }
        if (!isConnected()) {
            stopNetStatPoll();
            stopDataStallAlarm();
        }
        this.mRequestedApnType = "default";
        log("mDisconnectPendingCount = " + this.mDisconnectPendingCount);
        if (tearDown && this.mDisconnectPendingCount == 0) {
            notifyDataDisconnectComplete();
            notifyAllDataDisconnected();
        }
    }

    private void resetPollStats() {
        this.mTxPkts = -1;
        this.mRxPkts = -1;
        this.mNetStatPollPeriod = 1000;
    }

    private void startNetStatPoll() {
        if (this.mIsPhysicalLinkUp && !this.mIsPsRestricted && this.mPhone.getServiceStateTracker().getCurrentDataConnectionState() == 0) {
            if (getOverallState() == State.CONNECTED && !this.mNetStatPollEnabled) {
                log("startNetStatPoll");
                resetPollStats();
                this.mNetStatPollEnabled = true;
                this.mPollNetStat.run();
            }
            if (this.mPhone != null) {
                this.mPhone.notifyDataActivity();
            }
        }
    }

    private void stopNetStatPoll() {
        this.mNetStatPollEnabled = false;
        removeCallbacks(this.mPollNetStat);
        log("stopNetStatPoll");
        if (this.mPhone == null) {
            return;
        }
        if (TelBrand.IS_DCM && getOverallState() == State.IDLE) {
            setActivity(Activity.NONE);
        } else {
            this.mPhone.notifyDataActivity();
        }
    }

    public void sendStartNetStatPoll(Activity activity) {
        Message msg = obtainMessage(270376);
        msg.arg1 = 1;
        msg.obj = activity;
        sendMessage(msg);
    }

    private void handleStartNetStatPoll(Activity activity) {
        this.mIsPhysicalLinkUp = true;
        startNetStatPoll();
        startDataStallAlarm(false);
        setActivity(activity);
    }

    public void sendStopNetStatPoll(Activity activity) {
        Message msg = obtainMessage(270376);
        msg.arg1 = 0;
        msg.obj = activity;
        sendMessage(msg);
    }

    private void handleStopNetStatPoll(Activity activity) {
        this.mIsPhysicalLinkUp = false;
        stopNetStatPoll();
        stopDataStallAlarm();
        setActivity(activity);
    }

    private void updateDataActivity() {
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
            Activity newActivity = (sent <= 0 || received <= 0) ? (sent <= 0 || received != 0) ? (sent != 0 || received <= 0) ? this.mActivity == Activity.DORMANT ? this.mActivity : Activity.NONE : Activity.DATAIN : Activity.DATAOUT : Activity.DATAINANDOUT;
            if (this.mActivity != newActivity && this.mIsScreenOn) {
                this.mActivity = newActivity;
                this.mPhone.notifyDataActivity();
            }
        }
    }

    private int getRecoveryAction() {
        return System.getInt(this.mResolver, "radio.data.stall.recovery.action", 0);
    }

    private void putRecoveryAction(int action) {
        System.putInt(this.mResolver, "radio.data.stall.recovery.action", action);
    }

    private void doRecovery() {
        if (getOverallState() == State.CONNECTED) {
            int recoveryAction = getRecoveryAction();
            switch (recoveryAction) {
                case 0:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_GET_DATA_CALL_LIST, this.mSentSinceLastRecv);
                    log("doRecovery() get data call list");
                    this.mPhone.mCi.getDataCallList(obtainMessage(270340));
                    putRecoveryAction(1);
                    break;
                case 1:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_CLEANUP, this.mSentSinceLastRecv);
                    log("doRecovery() cleanup all connections");
                    cleanUpAllConnections(PhoneInternalInterface.REASON_PDP_RESET);
                    putRecoveryAction(2);
                    break;
                case 2:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_REREGISTER, this.mSentSinceLastRecv);
                    log("doRecovery() re-register");
                    this.mPhone.getServiceStateTracker().reRegisterNetwork(null);
                    putRecoveryAction(3);
                    break;
                case 3:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_RADIO_RESTART, this.mSentSinceLastRecv);
                    log("restarting radio");
                    putRecoveryAction(4);
                    restartRadio();
                    break;
                case 4:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_RADIO_RESTART_WITH_PROP, -1);
                    log("restarting radio with gsm.radioreset to true");
                    SystemProperties.set(this.RADIO_RESET_PROPERTY, "true");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                    restartRadio();
                    putRecoveryAction(0);
                    break;
                default:
                    throw new RuntimeException("doRecovery: Invalid recoveryAction=" + recoveryAction);
            }
            this.mSentSinceLastRecv = 0;
        }
    }

    private void updateDataStallInfo() {
        TxRxSum preTxRxSum = new TxRxSum(this.mDataStallTxRxSum);
        this.mDataStallTxRxSum.updateTxRxSum();
        long sent = this.mDataStallTxRxSum.txPkts - preTxRxSum.txPkts;
        long received = this.mDataStallTxRxSum.rxPkts - preTxRxSum.rxPkts;
        if (sent > 0 && received > 0) {
            this.mSentSinceLastRecv = 0;
            putRecoveryAction(0);
        } else if (sent > 0 && received == 0) {
            if (isPhoneStateIdle()) {
                this.mSentSinceLastRecv += sent;
            } else {
                this.mSentSinceLastRecv = 0;
            }
            log("updateDataStallInfo: OUT sent=" + sent + " mSentSinceLastRecv=" + this.mSentSinceLastRecv);
        } else if (sent == 0 && received > 0) {
            this.mSentSinceLastRecv = 0;
            putRecoveryAction(0);
        }
    }

    private boolean isPhoneStateIdle() {
        int i = 0;
        while (i < TelephonyManager.getDefault().getPhoneCount()) {
            Phone phone = PhoneFactory.getPhone(i);
            if (phone == null || phone.getState() == PhoneConstants.State.IDLE) {
                i++;
            } else {
                log("isPhoneStateIdle: Voice call active on sub: " + i);
                return false;
            }
        }
        return true;
    }

    private void onDataStallAlarm(int tag) {
        if (this.mDataStallAlarmTag != tag) {
            log("onDataStallAlarm: ignore, tag=" + tag + " expecting " + this.mDataStallAlarmTag);
            return;
        }
        updateDataStallInfo();
        boolean suspectedStall = false;
        if (this.mSentSinceLastRecv >= ((long) Global.getInt(this.mResolver, "pdp_watchdog_trigger_packet_count", 10))) {
            log("onDataStallAlarm: tag=" + tag + " do recovery action=" + getRecoveryAction());
            suspectedStall = true;
            sendMessage(obtainMessage(270354));
        }
        startDataStallAlarm(suspectedStall);
    }

    private void startDataStallAlarm(boolean suspectedStall) {
    }

    private void stopDataStallAlarm() {
    }

    private void restartDataStallAlarm() {
        if (!isConnected()) {
            return;
        }
        if (RecoveryAction.isAggressiveRecovery(getRecoveryAction())) {
            log("restartDataStallAlarm: action is pending. not resetting the alarm.");
            return;
        }
        stopDataStallAlarm();
        startDataStallAlarm(false);
    }

    private void onActionIntentProvisioningApnAlarm(Intent intent) {
        log("onActionIntentProvisioningApnAlarm: action=" + intent.getAction());
        Message msg = obtainMessage(270375, intent.getAction());
        msg.arg1 = intent.getIntExtra(PROVISIONING_APN_ALARM_TAG_EXTRA, 0);
        sendMessage(msg);
    }

    private void startProvisioningApnAlarm() {
        int delayInMs = Global.getInt(this.mResolver, "provisioning_apn_alarm_delay_in_ms", PROVISIONING_APN_ALARM_DELAY_IN_MS_DEFAULT);
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
        this.mAlarmManager.set(2, SystemClock.elapsedRealtime() + ((long) delayInMs), this.mProvisioningApnAlarmIntent);
    }

    private void stopProvisioningApnAlarm() {
        log("stopProvisioningApnAlarm: current tag=" + this.mProvisioningApnAlarmTag + " mProvsioningApnAlarmIntent=" + this.mProvisioningApnAlarmIntent);
        this.mProvisioningApnAlarmTag++;
        if (this.mProvisioningApnAlarmIntent != null) {
            this.mAlarmManager.cancel(this.mProvisioningApnAlarmIntent);
            this.mProvisioningApnAlarmIntent = null;
        }
    }

    /* Access modifiers changed, original: protected */
    public void updateOemDataSettings() {
        boolean epcCapability;
        boolean enabled = isDataEnabled(true);
        boolean dataRoaming = getDataOnRoamingEnabled();
        if (this.mUserDataEnabledDun) {
            epcCapability = true;
        } else {
            if (this.mPdpType.equals("PPP") || TelBrand.IS_SBM) {
                epcCapability = false;
            } else {
                epcCapability = true;
            }
            if (TelBrand.IS_DCM && this.mCid == 1 && this.mPreferredApn != null && (APN_DCM_INTERNET_DEFAULT.equals("") || !this.mPreferredApn.apn.equalsIgnoreCase(APN_DCM_INTERNET_DEFAULT))) {
                enabled = true;
            }
        }
        if (TelBrand.IS_DCM && SystemProperties.get("vold.decrypt", "0").equals("trigger_restart_min_framework")) {
            log("updateOemDataSettings: Forcibly set data FALSE because of encrypt");
            enabled = false;
        }
        this.mPhone.mCi.updateOemDataSettings(enabled, dataRoaming, epcCapability, null);
    }

    public void updateDunProfileName(int profileId, String dataProtocol, String apnName) {
        String mOemIdentifier = "QOEMHOOK";
        int headerSize = mOemIdentifier.length() + 8;
        String radioTechnology = Integer.toString(1);
        String profile = Integer.toString(profileId + 500);
        if (apnName == null) {
            apnName = "";
        }
        String user = "";
        String password = "";
        String authType = Integer.toString(0);
        if (dataProtocol == null) {
            dataProtocol = "IP";
        }
        int requestSize = ((((((((((((radioTechnology.getBytes().length + 1) + profile.getBytes().length) + 1) + apnName.getBytes().length) + 1) + user.getBytes().length) + 1) + password.getBytes().length) + 1) + authType.getBytes().length) + 1) + dataProtocol.getBytes().length) + 1;
        byte[] request = new byte[(headerSize + requestSize)];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(mOemIdentifier.getBytes());
        reqBuffer.putInt(591832);
        reqBuffer.putInt(requestSize);
        reqBuffer.put(radioTechnology.getBytes());
        reqBuffer.put((byte) 0);
        reqBuffer.put(profile.getBytes());
        reqBuffer.put((byte) 0);
        reqBuffer.put(apnName.getBytes());
        reqBuffer.put((byte) 0);
        reqBuffer.put(user.getBytes());
        reqBuffer.put((byte) 0);
        reqBuffer.put(password.getBytes());
        reqBuffer.put((byte) 0);
        reqBuffer.put(authType.getBytes());
        reqBuffer.put((byte) 0);
        reqBuffer.put(dataProtocol.getBytes());
        reqBuffer.put((byte) 0);
        StringBuilder append = new StringBuilder().append("updateDunProfileName: profileId = ").append(profileId).append(", dataProtocol = ").append(dataProtocol).append(", apnName = ");
        if (!"eng".equals(Build.TYPE)) {
            apnName = "*****";
        }
        log(append.append(apnName).toString());
        this.mPhone.mCi.invokeOemRilRequestRaw(request, null);
    }

    public void resetDunProfiles() {
        int i;
        if (TelBrand.IS_DCM) {
            updateDunProfileNameCid1();
            for (i = 2; i <= 10; i++) {
                updateDunProfileName(i, "IP", "LKvkWgIeq.o8s");
            }
            return;
        }
        for (i = 1; i <= 10; i++) {
            updateDunProfileName(i, "IP", "LKvkWgIeq.o8s");
        }
    }

    public void setMobileDataEnabledDun(boolean enabled) {
        synchronized (this.mDataEnabledLock) {
            boolean prevEnabled = isDataEnabled(true);
            this.mUserDataEnabledDun = enabled;
            if (prevEnabled != isDataEnabled(true)) {
                updateOemDataSettings();
                if (prevEnabled) {
                    onCleanUpAllConnections(PhoneInternalInterface.REASON_DATA_SPECIFIC_DISABLED);
                } else {
                    onTrySetupData(PhoneInternalInterface.REASON_DATA_ENABLED);
                }
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void broadcastDisconnectDun() {
        this.mPhone.getContext().sendBroadcast(new Intent(ACTION_DISCONNECT_BT_DUN));
    }

    public void setProfilePdpType(int cid, String pdpType) {
        if (TelBrand.IS_DCM) {
            this.mCid = cid;
            this.mPdpType = pdpType;
            log("setNotifyPdpType: current CID = " + cid + ", and corresponding pdpType = " + this.mPdpType);
        }
    }

    public void updateDunProfileNameCid1() {
        String dunDefaultApn = "dummy";
        if (this.mPreferredApn != null) {
            dunDefaultApn = this.mPreferredApn.apn.equalsIgnoreCase(APN_DCM_INTERNET_DEFAULT) ? RILConstants.APN_DCM_DUN_DEFAULT : this.mPreferredApn.apn;
        }
        updateDunProfileName(1, "IP", dunDefaultApn);
    }

    public boolean isNetworkRoaming() {
        return this.mPhone.getServiceState().getDataRoaming();
    }

    /* JADX WARNING: Removed duplicated region for block: B:23:0x0048 A:{SYNTHETIC, Splitter:B:23:0x0048} */
    /* JADX WARNING: Removed duplicated region for block: B:28:0x0051 A:{SYNTHETIC, Splitter:B:28:0x0051} */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private String getFixedParameter() {
        Throwable th;
        String fixedParam = "";
        FileInputStream fis = null;
        int[] pattern = new int[]{215, 67, 46, 69, 21, 74, 36, 236, PduHeaders.MBOX_TOTALS, BearerData.RELATIVE_TIME_MOBILE_INACTIVE, 70, PduHeaders.MBOX_TOTALS, 85};
        try {
            FileInputStream fis2 = new FileInputStream("/etc/Tsx5UPfM");
            try {
                byte[] buffer = new byte[PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_LACK_OF_PREPAID];
                int count = fis2.read(buffer);
                if (count == PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_LACK_OF_PREPAID) {
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
                fis = fis2;
            } catch (IOException e2) {
                fis = fis2;
                if (fis != null) {
                }
                return fixedParam;
            } catch (Throwable th2) {
                th = th2;
                fis = fis2;
                if (fis != null) {
                }
                throw th;
            }
        } catch (IOException e3) {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e4) {
                }
            }
            return fixedParam;
        } catch (Throwable th3) {
            th = th3;
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e5) {
                }
            }
            throw th;
        }
        return fixedParam;
    }

    public ApnSetting getApnParameter(boolean isCdma, boolean isInternet, int profileType, int id, String operator, String carrier) {
        String apn = null;
        String user = null;
        String pass = null;
        int authType = 2;
        String[] types = new String[]{"default", "mms", "supl", "hipri", "dun"};
        String protocol = "IP";
        String roamingProtocol = "IP";
        String[] dnses = null;
        if (!(isCdma || isNetworkRoaming())) {
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
        ApnSetting apnSetting = new ApnSetting(id, operator, carrier, apn, "", "", "", "", "", user, pass, authType, types, protocol, roamingProtocol, true, 0, 0, 0, false, 0, 0, 0, 0, "", "");
        apnSetting.oemDnses = dnses;
        return apnSetting;
    }

    public int changeMode(boolean mode, String apn, String userId, String password, int authType, String dns1, String dns2, String proxyHost, String proxyPort) {
        if (!TelBrand.IS_KDDI) {
            return -1;
        }
        if (this.mState == State.IDLE || this.mState == State.FAILED) {
        }
        int apnId = ApnContext.apnIdForApnName("default");
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
                if (mode) {
                    log("mConnectingApnCarNavi = true");
                    this.mConnectingApnCarNavi = true;
                } else {
                    log("mDisConnectingApnCarNavi = true");
                    this.mDisConnectingApnCarNavi = true;
                }
            }
            String str = apn;
            String str2 = proxyHost;
            String str3 = proxyPort;
            String str4 = userId;
            String str5 = password;
            int i = authType;
            this.mApnCarNavi = new ApnSetting(apnId, "", "", str, str2, str3, "", "", "", str4, str5, i, new String[]{"default", "mms", "supl", "hipri", "dun"}, "", "", true, this.mPhone.getServiceState().getRadioTechnology(), 0, 0, false, 0, 0, 0, 0, "", "");
            this.mEnableApnCarNavi = mode;
            this.mDnsesCarNavi[0] = dns1;
            this.mDnsesCarNavi[1] = dns2;
            sendMessage(obtainMessage(270355));
            return 0;
        }
    }

    /* Access modifiers changed, original: protected */
    public void notifyDataConnectionCarNavi(State state) {
        if (TelBrand.IS_KDDI) {
            log("notifyDataConnectionCarNavi() mEnableApnCarNavi :" + this.mEnableApnCarNavi + " mRequestedApnType : " + this.mRequestedApnType + " state : " + state);
            if (this.mEnableApnCarNavi && this.mConnectingApnCarNavi && state == State.CONNECTING) {
                log("notifyDataConnectionCarNavi() mConnectingApnCarNavi=true state change. [" + this.mState + "]-->[" + state + "]");
            } else if (this.mState == state) {
                log("notifyDataConnectionCarNavi() ignored same state");
                return;
            } else if (this.mEnableApnCarNavi && !((this.mState != State.RETRYING && this.mState != State.CONNECTING && state != State.SCANNING) || state == State.FAILED || state == State.CONNECTED)) {
                log("notifyDataConnectionCarNavi() ignored invalid state change. [" + this.mState + "]-->[" + state + "]");
                return;
            }
            this.mState = state;
            if (this.mEnableApnCarNavi || (!this.mEnableApnCarNavi && this.mDisConnectingApnCarNavi)) {
                if (this.mConnectingApnCarNavi) {
                    if (!(state == State.RETRYING || state == State.CONNECTING)) {
                        log("notifyDataConnectionCarNavi() ignored. mConnectingApnCarNavi : " + this.mConnectingApnCarNavi + " state : " + state);
                        return;
                    }
                } else if (!this.mEnableApnCarNavi && this.mDisConnectingApnCarNavi) {
                    if (state != State.IDLE && state != State.FAILED && state != State.DISCONNECTING) {
                        log("notifyDataConnectionCarNavi() ignored. mDisConnectingApnCarNavi : " + this.mDisConnectingApnCarNavi + " state : " + state);
                        return;
                    } else if (state == State.IDLE || state == State.FAILED) {
                        log("mDisConnectingApnCarNavi = false");
                        this.mDisConnectingApnCarNavi = false;
                    }
                }
                int stateCarNavi = 4;
                if (state == State.RETRYING || state == State.CONNECTING || state == State.SCANNING) {
                    stateCarNavi = 1;
                    if (this.mConnectingApnCarNavi) {
                        log("mConnectingApnCarNavi = false");
                        this.mConnectingApnCarNavi = false;
                    }
                } else if (state == State.CONNECTED) {
                    stateCarNavi = 2;
                } else if (state == State.DISCONNECTING) {
                    stateCarNavi = 3;
                } else if (state == State.FAILED || state == State.IDLE) {
                    stateCarNavi = 4;
                }
                Intent intent = new Intent(CONNECTIVITY_ACTION_CARNAVI);
                intent.putExtra(EXTRA_CONNECTIVITY_STATUS_CARNAVI, stateCarNavi);
                intent.putExtra(EXTRA_ERRONO_CARNAVI, 0);
                this.mPhone.getContext().sendStickyBroadcast(intent);
                log("Sent intent CONNECTIVITY_ACTION for CarNavi : " + state + " / " + stateCarNavi);
            }
            if (this.mEnableApnCarNavi && this.mState == State.IDLE) {
                changeMode(false, "", "", "", 0, "", "", "", "");
                this.mEnableApnCarNavi = false;
                this.mDisConnectingApnCarNavi = false;
            }
        }
    }

    public String getHigherPriorityApnType() {
        int i = 0;
        String higherPrioApnType = "default";
        if (!TelBrand.IS_KDDI) {
            return higherPrioApnType;
        }
        ApnContext[] apnContextArr = (ApnContext[]) this.mPrioritySortedApnContexts.toArray(new ApnContext[0]);
        int length = apnContextArr.length;
        while (i < length) {
            ApnContext apnContextEntry = apnContextArr[i];
            if (apnContextEntry.isEnabled()) {
                higherPrioApnType = apnContextEntry.getApnType();
                log("higherPrioApnType : " + higherPrioApnType);
                return higherPrioApnType;
            }
            i++;
        }
        return higherPrioApnType;
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
        if (this.mEnableApnCarNavi || this.mDisConnectingApnCarNavi) {
            State state = getState(higherPrioApnType);
            if (state == State.IDLE) {
                return 0;
            }
            if (state == State.CONNECTING) {
                return 2;
            }
            if (state == State.SCANNING) {
                return 3;
            }
            if (state == State.CONNECTED) {
                return 4;
            }
            if (state == State.DISCONNECTING) {
                return 5;
            }
            if (state == State.FAILED || state == State.RETRYING) {
                return 6;
            }
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
