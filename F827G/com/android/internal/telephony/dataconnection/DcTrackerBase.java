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
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.EventLog;
import com.android.internal.telephony.DctConstants.Activity;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.AsyncChannel;
import com.google.android.mms.pdu.CharacterSets;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jp.co.sharp.android.internal.telephony.FastDormancy;

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
    static boolean mIsCleanupRequired = false;
    protected static int sEnableFailFastRefCounter = 0;
    protected static boolean sPolicyDataEnabled = true;
    protected String RADIO_RESET_PROPERTY = "gsm.radioreset";
    protected boolean SH_DBG = true;
    protected ApnSetting mActiveApn;
    protected Activity mActivity = Activity.NONE;
    AlarmManager mAlarmManager;
    protected ArrayList<ApnSetting> mAllApnSettings = new ArrayList();
    public ApnSetting mApnCarNavi = null;
    protected final ConcurrentHashMap<String, ApnContext> mApnContexts = new ConcurrentHashMap();
    protected HashMap<String, Integer> mApnToDataConnectionId = new HashMap();
    protected boolean mAutoAttachOnCreation = false;
    protected boolean mAutoAttachOnCreationConfig = false;
    protected int mCidActive;
    ConnectivityManager mCm;
    protected int mCnt = 0;
    protected boolean mConnectingApnCarNavi = false;
    protected HashMap<Integer, DcAsyncChannel> mDataConnectionAcHashMap = new HashMap();
    protected Handler mDataConnectionTracker = null;
    protected HashMap<Integer, DataConnection> mDataConnections = new HashMap();
    private boolean[] mDataEnabled = new boolean[10];
    protected Object mDataEnabledLock = new Object();
    private DataRoamingSettingObserver mDataRoamingSettingObserver;
    protected PendingIntent mDataStallAlarmIntent = null;
    protected int mDataStallAlarmTag = ((int) SystemClock.elapsedRealtime());
    protected volatile boolean mDataStallDetectionEnabled = true;
    protected TxRxSum mDataStallTxRxSum = new TxRxSum(0, 0);
    protected DcTesterFailBringUpAll mDcTesterFailBringUpAll;
    protected DcController mDcc;
    public boolean mDisConnectingApnCarNavi = false;
    public String[] mDnsAddressCarNavi = new String[2];
    public String[] mDnsesCarNavi = new String[2];
    protected ApnSetting mEmergencyApn = null;
    public boolean mEnableApnCarNavi = false;
    private int mEnabledCount = 0;
    protected volatile boolean mFailFast = false;
    protected AtomicReference<IccRecords> mIccRecords = new AtomicReference();
    protected boolean mInVoiceCall = false;
    protected BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
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
                boolean z2 = networkInfo != null && networkInfo.isConnected();
                dcTrackerBase.mIsWifiConnected = z2;
                DcTrackerBase.this.log("NETWORK_STATE_CHANGED_ACTION: mIsWifiConnected=" + DcTrackerBase.this.mIsWifiConnected);
                if (TelBrand.IS_DCM && !DcTrackerBase.this.mIsWifiConnected) {
                    DcTrackerBase.this.startNetStatPoll();
                }
            } else if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
                if (intent.getIntExtra("wifi_state", 4) != 3) {
                    z = false;
                }
                if (!z) {
                    DcTrackerBase.this.mIsWifiConnected = false;
                }
                DcTrackerBase.this.log("WIFI_STATE_CHANGED_ACTION: enabled=" + z + " mIsWifiConnected=" + DcTrackerBase.this.mIsWifiConnected);
            }
        }
    };
    protected boolean mInternalDataEnabled = true;
    protected boolean mIsDisposed = false;
    protected boolean mIsEpcPending = false;
    protected boolean mIsPhysicalLinkUp = false;
    protected boolean mIsProvisioning = false;
    protected boolean mIsPsRestricted = false;
    protected boolean mIsScreenOn = true;
    protected boolean mIsWifiConnected = false;
    public String mLocalAddressCarNavi;
    protected boolean mNetStatPollEnabled = false;
    protected int mNetStatPollPeriod;
    protected int mNoRecvPollCount = 0;
    protected ApnSetting mOemKddiDunApn = null;
    private final OnSubscriptionsChangedListener mOnSubscriptionsChangedListener = new OnSubscriptionsChangedListener() {
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
    protected String mPdpType = "IP";
    protected PhoneBase mPhone;
    private Runnable mPollNetStat = new Runnable() {
        public void run() {
            DcTrackerBase.this.updateDataActivity();
            if (DcTrackerBase.this.mIsScreenOn) {
                DcTrackerBase.this.mNetStatPollPeriod = Global.getInt(DcTrackerBase.this.mResolver, "pdp_watchdog_poll_interval_ms", 1000);
            } else {
                DcTrackerBase.this.mNetStatPollPeriod = Global.getInt(DcTrackerBase.this.mResolver, "pdp_watchdog_long_poll_interval_ms", DcTrackerBase.POLL_NETSTAT_SCREEN_OFF_MILLIS);
            }
            if (DcTrackerBase.this.mNetStatPollEnabled) {
                DcTrackerBase.this.mDataConnectionTracker.postDelayed(this, (long) DcTrackerBase.this.mNetStatPollPeriod);
            }
        }
    };
    protected ApnSetting mPreferredApn = null;
    protected final PriorityQueue<ApnContext> mPrioritySortedApnContexts = new PriorityQueue(5, new Comparator<ApnContext>() {
        public int compare(ApnContext apnContext, ApnContext apnContext2) {
            return apnContext2.priority - apnContext.priority;
        }
    });
    protected PendingIntent mProvisioningApnAlarmIntent = null;
    protected int mProvisioningApnAlarmTag = ((int) SystemClock.elapsedRealtime());
    protected String mProvisioningUrl = null;
    protected PendingIntent mReconnectIntent = null;
    protected AsyncChannel mReplyAc = new AsyncChannel();
    protected String mRequestedApnType = "default";
    protected ContentResolver mResolver;
    protected long mRxPkts;
    protected long mSentSinceLastRecv;
    protected AtomicReference<IccRecords> mSimRecords = new AtomicReference();
    protected State mState = State.IDLE;
    private SubscriptionManager mSubscriptionManager;
    protected long mTxPkts;
    protected UiccController mUiccController;
    protected AtomicInteger mUniqueIdGenerator = new AtomicInteger(0);
    protected boolean mUserDataEnabled = true;
    protected boolean mUserDataEnabledDun = true;

    /* renamed from: com.android.internal.telephony.dataconnection.DcTrackerBase$5 */
    static /* synthetic */ class AnonymousClass5 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.IDLE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.RETRYING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.SCANNING.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTED.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.DISCONNECTING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
        }
    }

    private class DataRoamingSettingObserver extends ContentObserver {
        public DataRoamingSettingObserver(Handler handler, Context context) {
            super(handler);
            DcTrackerBase.this.mResolver = context.getContentResolver();
        }

        public void onChange(boolean z) {
            DcTrackerBase.this.log(" handleDataOnRoamingChange: updateOemDataSettings call");
            DcTrackerBase.this.updateOemDataSettings();
            if (DcTrackerBase.this.mPhone.getServiceState().getDataRoaming()) {
                DcTrackerBase.this.sendMessage(DcTrackerBase.this.obtainMessage(270347));
            }
        }

        public void register() {
            String str = "data_roaming";
            if (TelephonyManager.getDefault().isMultiSimEnabled()) {
                str = "data_roaming" + DcTrackerBase.this.mPhone.getPhoneId();
            }
            DcTrackerBase.this.mResolver.registerContentObserver(Global.getUriFor(str), false, this);
        }

        public void unregister() {
            DcTrackerBase.this.mResolver.unregisterContentObserver(this);
        }
    }

    protected static class RecoveryAction {
        public static final int CLEANUP = 1;
        public static final int GET_DATA_CALL_LIST = 0;
        public static final int RADIO_RESTART = 3;
        public static final int RADIO_RESTART_WITH_PROP = 4;
        public static final int REREGISTER = 2;

        protected RecoveryAction() {
        }

        private static boolean isAggressiveRecovery(int i) {
            return i == 1 || i == 2 || i == 3 || i == 4;
        }
    }

    public class TxRxSum {
        public long rxPkts;
        public long txPkts;

        public TxRxSum() {
            reset();
        }

        public TxRxSum(long j, long j2) {
            this.txPkts = j;
            this.rxPkts = j2;
        }

        public TxRxSum(TxRxSum txRxSum) {
            this.txPkts = txRxSum.txPkts;
            this.rxPkts = txRxSum.rxPkts;
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

    protected DcTrackerBase(PhoneBase phoneBase) {
        this.mPhone = phoneBase;
        log("DCT.constructor");
        this.mResolver = this.mPhone.getContext().getContentResolver();
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 270369, null);
        this.mAlarmManager = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
        this.mCm = (ConnectivityManager) this.mPhone.getContext().getSystemService("connectivity");
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.SCREEN_ON");
        intentFilter.addAction("android.intent.action.SCREEN_OFF");
        intentFilter.addAction("android.net.wifi.STATE_CHANGE");
        intentFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        intentFilter.addAction(INTENT_DATA_STALL_ALARM);
        intentFilter.addAction(INTENT_PROVISIONING_APN_ALARM);
        this.mUserDataEnabled = getDataEnabled();
        this.mPhone.getContext().registerReceiver(this.mIntentReceiver, intentFilter, null, this.mPhone);
        this.mDataEnabled[0] = SystemProperties.getBoolean(DEFALUT_DATA_ON_BOOT_PROP, true);
        if (this.mDataEnabled[0]) {
            this.mEnabledCount++;
        }
        this.mAutoAttachOnCreation = PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext()).getBoolean(PhoneBase.DATA_DISABLED_ON_BOOT_KEY, false);
        this.mSubscriptionManager = SubscriptionManager.from(this.mPhone.getContext());
        this.mSubscriptionManager.addOnSubscriptionsChangedListener(this.mOnSubscriptionsChangedListener);
        HandlerThread handlerThread = new HandlerThread("DcHandlerThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        this.mDcc = DcController.makeDcc(this.mPhone, this, handler);
        this.mDcTesterFailBringUpAll = new DcTesterFailBringUpAll(this.mPhone, handler);
        FastDormancy fastDormancy = new FastDormancy(this.mPhone.mCi, this.mPhone.getContext());
    }

    /* JADX WARNING: Removed duplicated region for block: B:31:0x0092 A:{SYNTHETIC, Splitter:B:31:0x0092} */
    private java.lang.String getFixedParameter() {
        /*
        r10 = this;
        r1 = 0;
        r8 = 235; // 0xeb float:3.3E-43 double:1.16E-321;
        r6 = 170; // 0xaa float:2.38E-43 double:8.4E-322;
        r2 = 0;
        r0 = "";
        r3 = 13;
        r5 = new int[r3];
        r3 = 215; // 0xd7 float:3.01E-43 double:1.06E-321;
        r5[r2] = r3;
        r3 = 1;
        r4 = 67;
        r5[r3] = r4;
        r3 = 2;
        r4 = 46;
        r5[r3] = r4;
        r3 = 3;
        r4 = 69;
        r5[r3] = r4;
        r3 = 4;
        r4 = 21;
        r5[r3] = r4;
        r3 = 5;
        r4 = 74;
        r5[r3] = r4;
        r3 = 6;
        r4 = 36;
        r5[r3] = r4;
        r3 = 7;
        r4 = 236; // 0xec float:3.31E-43 double:1.166E-321;
        r5[r3] = r4;
        r3 = 8;
        r5[r3] = r6;
        r3 = 9;
        r4 = 247; // 0xf7 float:3.46E-43 double:1.22E-321;
        r5[r3] = r4;
        r3 = 10;
        r4 = 70;
        r5[r3] = r4;
        r3 = 11;
        r5[r3] = r6;
        r3 = 12;
        r4 = 85;
        r5[r3] = r4;
        r3 = new java.io.FileInputStream;	 Catch:{ IOException -> 0x009c, all -> 0x008e }
        r4 = "/etc/Tsx5UPfM";
        r3.<init>(r4);	 Catch:{ IOException -> 0x009c, all -> 0x008e }
        r1 = 235; // 0xeb float:3.3E-43 double:1.16E-321;
        r6 = new byte[r1];	 Catch:{ IOException -> 0x0080, all -> 0x009a }
        r7 = r3.read(r6);	 Catch:{ IOException -> 0x0080, all -> 0x009a }
        if (r7 != r8) goto L_0x007a;
    L_0x005e:
        r4 = r2;
        r1 = r2;
    L_0x0060:
        if (r4 >= r7) goto L_0x0073;
    L_0x0062:
        r8 = r6[r4];
        r9 = r5[r1];
        r8 = r8 ^ r9;
        r8 = (byte) r8;
        r6[r4] = r8;
        r1 = r1 + 1;
        r8 = r5.length;	 Catch:{ IOException -> 0x0080, all -> 0x009a }
        if (r1 < r8) goto L_0x0070;
    L_0x006f:
        r1 = r2;
    L_0x0070:
        r4 = r4 + 1;
        goto L_0x0060;
    L_0x0073:
        r1 = new java.lang.String;	 Catch:{ IOException -> 0x0080, all -> 0x009a }
        r2 = 0;
        r1.<init>(r6, r2, r7);	 Catch:{ IOException -> 0x0080, all -> 0x009a }
        r0 = r1;
    L_0x007a:
        if (r3 == 0) goto L_0x007f;
    L_0x007c:
        r3.close();	 Catch:{ IOException -> 0x0096 }
    L_0x007f:
        return r0;
    L_0x0080:
        r1 = move-exception;
        r1 = r3;
    L_0x0082:
        if (r1 == 0) goto L_0x007f;
    L_0x0084:
        r1.close();	 Catch:{ IOException -> 0x008a }
        r0 = "";
        goto L_0x007f;
    L_0x008a:
        r0 = move-exception;
        r0 = "";
        goto L_0x007f;
    L_0x008e:
        r0 = move-exception;
        r3 = r1;
    L_0x0090:
        if (r3 == 0) goto L_0x0095;
    L_0x0092:
        r3.close();	 Catch:{ IOException -> 0x0098 }
    L_0x0095:
        throw r0;
    L_0x0096:
        r1 = move-exception;
        goto L_0x007f;
    L_0x0098:
        r1 = move-exception;
        goto L_0x0095;
    L_0x009a:
        r0 = move-exception;
        goto L_0x0090;
    L_0x009c:
        r2 = move-exception;
        goto L_0x0082;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.dataconnection.DcTrackerBase.getFixedParameter():java.lang.String");
    }

    public static boolean isNetworkRoaming() {
        return "true".equals(SystemProperties.get("gsm.operator.isroaming"));
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

    private void notifyApnIdDisconnected(String str, int i) {
        this.mPhone.notifyDataConnection(str, apnIdToType(i), DataState.DISCONNECTED);
    }

    private void notifyApnIdUpToCurrent(String str, int i) {
        switch (AnonymousClass5.$SwitchMap$com$android$internal$telephony$DctConstants$State[this.mState.ordinal()]) {
            case 2:
            case 3:
            case 4:
                this.mPhone.notifyDataConnection(str, apnIdToType(i), DataState.CONNECTING);
                return;
            case 5:
            case 6:
                this.mPhone.notifyDataConnection(str, apnIdToType(i), DataState.CONNECTING);
                this.mPhone.notifyDataConnection(str, apnIdToType(i), DataState.CONNECTED);
                return;
            default:
                return;
        }
    }

    private void updateDataStallInfo() {
        TxRxSum txRxSum = new TxRxSum(this.mDataStallTxRxSum);
        this.mDataStallTxRxSum.updateTxRxSum();
        log("updateDataStallInfo: mDataStallTxRxSum=" + this.mDataStallTxRxSum + " preTxRxSum=" + txRxSum);
        long j = this.mDataStallTxRxSum.txPkts - txRxSum.txPkts;
        long j2 = this.mDataStallTxRxSum.rxPkts - txRxSum.rxPkts;
        if (j > 0 && j2 > 0) {
            log("updateDataStallInfo: IN/OUT");
            this.mSentSinceLastRecv = 0;
            putRecoveryAction(0);
        } else if (j > 0 && j2 == 0) {
            if (isPhoneStateIdle()) {
                this.mSentSinceLastRecv += j;
            } else {
                this.mSentSinceLastRecv = 0;
            }
            log("updateDataStallInfo: OUT sent=" + j + " mSentSinceLastRecv=" + this.mSentSinceLastRecv);
        } else if (j != 0 || j2 <= 0) {
            log("updateDataStallInfo: NONE");
        } else {
            log("updateDataStallInfo: IN");
            this.mSentSinceLastRecv = 0;
            putRecoveryAction(0);
        }
    }

    /* Access modifiers changed, original: protected */
    public String apnIdToType(int i) {
        switch (i) {
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
                log("Unknown id (" + i + ") in apnIdToType");
                return "default";
        }
    }

    /* Access modifiers changed, original: protected */
    public int apnTypeToId(String str) {
        return TextUtils.equals(str, "default") ? 0 : TextUtils.equals(str, "mms") ? 1 : TextUtils.equals(str, "supl") ? 2 : TextUtils.equals(str, "dun") ? 3 : TextUtils.equals(str, "hipri") ? 4 : TextUtils.equals(str, "ims") ? 5 : TextUtils.equals(str, "fota") ? 6 : TextUtils.equals(str, "cbs") ? 7 : TextUtils.equals(str, "ia") ? 8 : TextUtils.equals(str, "emergency") ? 9 : -1;
    }

    /* Access modifiers changed, original: protected */
    public void broadcastDisconnectDun() {
        this.mPhone.getContext().sendBroadcast(new Intent(ACTION_DISCONNECT_BT_DUN));
    }

    /* JADX WARNING: Missing block: B:32:0x01d2, code skipped:
            if (isApnTypeEnabled("hipri") != false) goto L_0x01d4;
     */
    public int changeMode(boolean r29, java.lang.String r30, java.lang.String r31, java.lang.String r32, int r33, java.lang.String r34, java.lang.String r35, java.lang.String r36, java.lang.String r37) {
        /*
        r28 = this;
        r2 = com.android.internal.telephony.TelBrand.IS_KDDI;
        if (r2 == 0) goto L_0x0278;
    L_0x0004:
        r0 = r28;
        r2 = r0.mState;
        r3 = com.android.internal.telephony.DctConstants.State.IDLE;
        if (r2 == r3) goto L_0x0014;
    L_0x000c:
        r0 = r28;
        r2 = r0.mState;
        r3 = com.android.internal.telephony.DctConstants.State.FAILED;
        if (r2 == r3) goto L_0x0014;
    L_0x0014:
        r2 = "default";
        r0 = r28;
        r3 = r0.apnTypeToId(r2);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "[CarNavi] mode[";
        r2 = r2.append(r4);
        r0 = r29;
        r2 = r2.append(r0);
        r4 = "]";
        r2 = r2.append(r4);
        r2 = r2.toString();
        r0 = r28;
        r0.log(r2);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "[CarNavi] apn[";
        r2 = r2.append(r4);
        r0 = r30;
        r2 = r2.append(r0);
        r4 = "]";
        r2 = r2.append(r4);
        r2 = r2.toString();
        r0 = r28;
        r0.log(r2);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "[CarNavi] userId[";
        r2 = r2.append(r4);
        r0 = r31;
        r2 = r2.append(r0);
        r4 = "]";
        r2 = r2.append(r4);
        r2 = r2.toString();
        r0 = r28;
        r0.log(r2);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "[CarNavi] password[";
        r2 = r2.append(r4);
        r0 = r32;
        r2 = r2.append(r0);
        r4 = "]";
        r2 = r2.append(r4);
        r2 = r2.toString();
        r0 = r28;
        r0.log(r2);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "[CarNavi] authType[";
        r2 = r2.append(r4);
        r0 = r33;
        r2 = r2.append(r0);
        r4 = "]";
        r2 = r2.append(r4);
        r2 = r2.toString();
        r0 = r28;
        r0.log(r2);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "[CarNavi] dns1[";
        r2 = r2.append(r4);
        r0 = r34;
        r2 = r2.append(r0);
        r4 = "]";
        r2 = r2.append(r4);
        r2 = r2.toString();
        r0 = r28;
        r0.log(r2);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "[CarNavi] dns2[";
        r2 = r2.append(r4);
        r0 = r35;
        r2 = r2.append(r0);
        r4 = "]";
        r2 = r2.append(r4);
        r2 = r2.toString();
        r0 = r28;
        r0.log(r2);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "[CarNavi] proxyHost[";
        r2 = r2.append(r4);
        r0 = r36;
        r2 = r2.append(r0);
        r4 = "]";
        r2 = r2.append(r4);
        r2 = r2.toString();
        r0 = r28;
        r0.log(r2);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "[CarNavi] proxyPort[";
        r2 = r2.append(r4);
        r0 = r37;
        r2 = r2.append(r0);
        r4 = "]";
        r2 = r2.append(r4);
        r2 = r2.toString();
        r0 = r28;
        r0.log(r2);
        r0 = r28;
        r2 = r0.mUserDataEnabled;
        if (r2 != 0) goto L_0x0160;
    L_0x0142:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "CpaManager.UNKNOWN_ERROR. mUserDataEnabled : ";
        r2 = r2.append(r3);
        r0 = r28;
        r3 = r0.mUserDataEnabled;
        r2 = r2.append(r3);
        r2 = r2.toString();
        r0 = r28;
        r0.log(r2);
        r2 = -4;
    L_0x015f:
        return r2;
    L_0x0160:
        r2 = isNetworkRoaming();
        if (r2 == 0) goto L_0x0171;
    L_0x0166:
        if (r29 == 0) goto L_0x0171;
    L_0x0168:
        r2 = "CpaManager.RADIO_NOT_AVAILABLE. isNetworkRoaming()";
        r0 = r28;
        r0.log(r2);
        r2 = -2;
        goto L_0x015f;
    L_0x0171:
        r0 = r28;
        r2 = r0.mPhone;
        r2 = r2.getServiceState();
        r2 = r2.getRadioTechnology();
        if (r2 != 0) goto L_0x0188;
    L_0x017f:
        r2 = "CpaManager.RADIO_NOT_AVAILABLE. RADIO_TECHNOLOGY_UNKNOWN";
        r0 = r28;
        r0.log(r2);
        r2 = -2;
        goto L_0x015f;
    L_0x0188:
        r0 = r28;
        r2 = r0.mEnableApnCarNavi;
        r0 = r29;
        if (r0 == r2) goto L_0x01e0;
    L_0x0190:
        if (r29 == 0) goto L_0x0262;
    L_0x0192:
        r0 = r28;
        r2 = r0.mEnableApnCarNavi;
        if (r2 != 0) goto L_0x0262;
    L_0x0198:
        r2 = "default";
        r0 = r28;
        r2 = r0.isApnTypeEnabled(r2);
        if (r2 != 0) goto L_0x01d4;
    L_0x01a2:
        r2 = "default";
        r0 = r28;
        r2 = r0.isApnTypeEnabled(r2);
        if (r2 != 0) goto L_0x0262;
    L_0x01ac:
        r2 = "mms";
        r0 = r28;
        r2 = r0.isApnTypeEnabled(r2);
        if (r2 != 0) goto L_0x01d4;
    L_0x01b6:
        r2 = "supl";
        r0 = r28;
        r2 = r0.isApnTypeEnabled(r2);
        if (r2 != 0) goto L_0x01d4;
    L_0x01c0:
        r2 = "dun";
        r0 = r28;
        r2 = r0.isApnTypeEnabled(r2);
        if (r2 != 0) goto L_0x01d4;
    L_0x01ca:
        r2 = "hipri";
        r0 = r28;
        r2 = r0.isApnTypeEnabled(r2);
        if (r2 == 0) goto L_0x0262;
    L_0x01d4:
        r2 = "mConnectingApnCarNavi = true";
        r0 = r28;
        r0.log(r2);
        r2 = 1;
        r0 = r28;
        r0.mConnectingApnCarNavi = r2;
    L_0x01e0:
        r0 = r28;
        r2 = r0.mPhone;
        r2 = r2.getServiceState();
        r19 = r2.getRadioTechnology();
        r2 = new com.android.internal.telephony.dataconnection.ApnSetting;
        r4 = "";
        r5 = "";
        r9 = "";
        r10 = "";
        r11 = "";
        r6 = 5;
        r15 = new java.lang.String[r6];
        r6 = 0;
        r7 = "default";
        r15[r6] = r7;
        r6 = 1;
        r7 = "mms";
        r15[r6] = r7;
        r6 = 2;
        r7 = "supl";
        r15[r6] = r7;
        r6 = 3;
        r7 = "hipri";
        r15[r6] = r7;
        r6 = 4;
        r7 = "dun";
        r15[r6] = r7;
        r16 = "";
        r17 = "";
        r18 = 1;
        r20 = 0;
        r21 = 0;
        r22 = 0;
        r23 = 0;
        r24 = 0;
        r25 = 0;
        r26 = "";
        r27 = "";
        r6 = r30;
        r7 = r36;
        r8 = r37;
        r12 = r31;
        r13 = r32;
        r14 = r33;
        r2.<init>(r3, r4, r5, r6, r7, r8, r9, r10, r11, r12, r13, r14, r15, r16, r17, r18, r19, r20, r21, r22, r23, r24, r25, r26, r27);
        r0 = r28;
        r0.mApnCarNavi = r2;
        r0 = r29;
        r1 = r28;
        r1.mEnableApnCarNavi = r0;
        r0 = r28;
        r2 = r0.mDnsesCarNavi;
        r3 = 0;
        r2[r3] = r34;
        r0 = r28;
        r2 = r0.mDnsesCarNavi;
        r3 = 1;
        r2[r3] = r35;
        r2 = 270355; // 0x42013 float:3.78848E-40 double:1.33573E-318;
        r0 = r28;
        r2 = r0.obtainMessage(r2);
        r0 = r28;
        r0.sendMessage(r2);
        r2 = 0;
        goto L_0x015f;
    L_0x0262:
        if (r29 != 0) goto L_0x01e0;
    L_0x0264:
        r0 = r28;
        r2 = r0.mEnableApnCarNavi;
        if (r2 == 0) goto L_0x01e0;
    L_0x026a:
        r2 = "mDisConnectingApnCarNavi = true";
        r0 = r28;
        r0.log(r2);
        r2 = 1;
        r0 = r28;
        r0.mDisConnectingApnCarNavi = r2;
        goto L_0x01e0;
    L_0x0278:
        r2 = -1;
        goto L_0x015f;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.dataconnection.DcTrackerBase.changeMode(boolean, java.lang.String, java.lang.String, java.lang.String, int, java.lang.String, java.lang.String, java.lang.String, java.lang.String):int");
    }

    public int changeOemKddiCpaMode(boolean z, String str, String str2, String str3, int i, String str4, String str5, String str6, String str7) {
        return changeMode(z, str, str2, str3, i, str4, str5, str6, str7);
    }

    public void cleanUpAllConnections(String str) {
        Message obtainMessage = obtainMessage(270365);
        obtainMessage.obj = str;
        sendMessage(obtainMessage);
    }

    public abstract void completeConnection(ApnContext apnContext);

    public void decApnRefCount(String str) {
    }

    public void dispose() {
        log("DCT.dispose");
        for (DcAsyncChannel disconnect : this.mDataConnectionAcHashMap.values()) {
            disconnect.disconnect();
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

    /* Access modifiers changed, original: protected */
    public void doRecovery() {
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
                    cleanUpAllConnections(Phone.REASON_PDP_RESET);
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

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        int i;
        Set<Entry> entrySet;
        printWriter.println("DcTrackerBase:");
        printWriter.println(" RADIO_TESTS=false");
        printWriter.println(" mInternalDataEnabled=" + this.mInternalDataEnabled);
        printWriter.println(" mUserDataEnabled=" + this.mUserDataEnabled);
        printWriter.println(" sPolicyDataEnabed=" + sPolicyDataEnabled);
        printWriter.println(" mDataEnabled:");
        for (i = 0; i < this.mDataEnabled.length; i++) {
            printWriter.printf("  mDataEnabled[%d]=%b\n", new Object[]{Integer.valueOf(i), Boolean.valueOf(this.mDataEnabled[i])});
        }
        printWriter.flush();
        printWriter.println(" mEnabledCount=" + this.mEnabledCount);
        printWriter.println(" mRequestedApnType=" + this.mRequestedApnType);
        printWriter.println(" mPhone=" + this.mPhone.getPhoneName());
        printWriter.println(" mPhoneId=" + this.mPhone.getPhoneId());
        printWriter.println(" mActivity=" + this.mActivity);
        printWriter.println(" mState=" + this.mState);
        printWriter.println(" mTxPkts=" + this.mTxPkts);
        printWriter.println(" mRxPkts=" + this.mRxPkts);
        printWriter.println(" mNetStatPollPeriod=" + this.mNetStatPollPeriod);
        printWriter.println(" mNetStatPollEnabled=" + this.mNetStatPollEnabled);
        printWriter.println(" mDataStallTxRxSum=" + this.mDataStallTxRxSum);
        printWriter.println(" mDataStallAlarmTag=" + this.mDataStallAlarmTag);
        printWriter.println(" mDataStallDetectionEanbled=" + this.mDataStallDetectionEnabled);
        printWriter.println(" mSentSinceLastRecv=" + this.mSentSinceLastRecv);
        printWriter.println(" mNoRecvPollCount=" + this.mNoRecvPollCount);
        printWriter.println(" mResolver=" + this.mResolver);
        printWriter.println(" mIsWifiConnected=" + this.mIsWifiConnected);
        printWriter.println(" mReconnectIntent=" + this.mReconnectIntent);
        printWriter.println(" mCidActive=" + this.mCidActive);
        printWriter.println(" mAutoAttachOnCreation=" + this.mAutoAttachOnCreation);
        printWriter.println(" mIsScreenOn=" + this.mIsScreenOn);
        printWriter.println(" mUniqueIdGenerator=" + this.mUniqueIdGenerator);
        printWriter.flush();
        printWriter.println(" ***************************************");
        DcController dcController = this.mDcc;
        if (dcController != null) {
            dcController.dump(fileDescriptor, printWriter, strArr);
        } else {
            printWriter.println(" mDcc=null");
        }
        printWriter.println(" ***************************************");
        if (this.mDataConnections != null) {
            entrySet = this.mDataConnections.entrySet();
            printWriter.println(" mDataConnections: count=" + entrySet.size());
            for (Entry key : entrySet) {
                printWriter.printf(" *** mDataConnection[%d] \n", new Object[]{key.getKey()});
                ((DataConnection) key.getValue()).dump(fileDescriptor, printWriter, strArr);
            }
        } else {
            printWriter.println("mDataConnections=null");
        }
        printWriter.println(" ***************************************");
        printWriter.flush();
        HashMap hashMap = this.mApnToDataConnectionId;
        if (hashMap != null) {
            entrySet = hashMap.entrySet();
            printWriter.println(" mApnToDataConnectonId size=" + entrySet.size());
            for (Entry key2 : entrySet) {
                printWriter.printf(" mApnToDataConnectonId[%s]=%d\n", new Object[]{key2.getKey(), key2.getValue()});
            }
        } else {
            printWriter.println("mApnToDataConnectionId=null");
        }
        printWriter.println(" ***************************************");
        printWriter.flush();
        ConcurrentHashMap concurrentHashMap = this.mApnContexts;
        if (concurrentHashMap != null) {
            entrySet = concurrentHashMap.entrySet();
            printWriter.println(" mApnContexts size=" + entrySet.size());
            for (Entry key22 : entrySet) {
                ((ApnContext) key22.getValue()).dump(fileDescriptor, printWriter, strArr);
            }
            printWriter.println(" ***************************************");
        } else {
            printWriter.println(" mApnContexts=null");
        }
        printWriter.flush();
        printWriter.println(" mActiveApn=" + this.mActiveApn);
        ArrayList arrayList = this.mAllApnSettings;
        if (arrayList != null) {
            printWriter.println(" mAllApnSettings size=" + arrayList.size());
            for (i = 0; i < arrayList.size(); i++) {
                printWriter.printf(" mAllApnSettings[%d]: %s\n", new Object[]{Integer.valueOf(i), arrayList.get(i)});
            }
            printWriter.flush();
        } else {
            printWriter.println(" mAllApnSettings=null");
        }
        printWriter.println(" mPreferredApn=" + this.mPreferredApn);
        printWriter.println(" mIsPsRestricted=" + this.mIsPsRestricted);
        printWriter.println(" mIsDisposed=" + this.mIsDisposed);
        printWriter.println(" mIntentReceiver=" + this.mIntentReceiver);
        printWriter.println(" mDataRoamingSettingObserver=" + this.mDataRoamingSettingObserver);
        printWriter.flush();
    }

    /* Access modifiers changed, original: protected */
    public ApnSetting fetchDunApn() {
        ApnSetting apnSetting;
        int i = 0;
        if (SystemProperties.getBoolean("net.tethering.noprovisioning", false)) {
            log("fetchDunApn: net.tethering.noprovisioning=true ret: null");
            apnSetting = null;
        } else if (TelBrand.IS_KDDI) {
            log("fetchDunApn() Start");
            return !isNetworkRoaming() ? this.mOemKddiDunApn : null;
        } else {
            Context context = this.mPhone.getContext();
            IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
            int i2 = -1;
            for (ApnSetting apnSetting2 : ApnSetting.arrayFromString(Global.getString(context.getContentResolver(), "tether_dun_apn"))) {
                Object operatorNumeric = iccRecords != null ? iccRecords.getOperatorNumeric() : "";
                if (apnSetting2.bearer != 0) {
                    if (i2 == -1) {
                        i2 = this.mPhone.getServiceState().getRilDataRadioTechnology();
                    }
                    if (apnSetting2.bearer != i2) {
                        continue;
                    }
                }
                if (!apnSetting2.numeric.equals(operatorNumeric)) {
                    continue;
                } else if (apnSetting2.hasMvnoParams()) {
                    if (iccRecords != null && mvnoMatches(iccRecords, apnSetting2.mvnoType, apnSetting2.mvnoMatchData)) {
                        return apnSetting2;
                    }
                }
            }
            String[] stringArray = context.getResources().getStringArray(17235993);
            if (stringArray == null) {
                return null;
            }
            int length = stringArray.length;
            int i3 = i2;
            ApnSetting apnSetting3 = null;
            while (i < length) {
                ApnSetting fromString = ApnSetting.fromString(stringArray[i]);
                if (fromString != null) {
                    if (fromString.bearer != 0) {
                        if (i3 == -1) {
                            i3 = this.mPhone.getServiceState().getRilDataRadioTechnology();
                        }
                        if (fromString.bearer != i3) {
                            continue;
                        }
                    }
                    if (!fromString.hasMvnoParams()) {
                        apnSetting3 = fromString;
                    } else if (iccRecords != null && mvnoMatches(iccRecords, fromString.mvnoType, fromString.mvnoMatchData)) {
                        return fromString;
                    }
                }
                i++;
            }
            return apnSetting3;
        }
        return apnSetting2;
    }

    public String getActiveApnString(String str) {
        return this.mActiveApn != null ? this.mActiveApn.apn : null;
    }

    public String[] getActiveApnTypes() {
        if (this.mActiveApn != null) {
            return this.mActiveApn.types;
        }
        return new String[]{"default"};
    }

    public Activity getActivity() {
        return this.mActivity;
    }

    public boolean getAnyDataEnabled() {
        boolean z;
        synchronized (this.mDataEnabledLock) {
            z = this.mInternalDataEnabled && this.mUserDataEnabled && sPolicyDataEnabled && this.mUserDataEnabledDun && this.mEnabledCount != 0;
        }
        if (!z) {
            log("getAnyDataEnabled " + z);
        }
        return z;
    }

    /* Access modifiers changed, original: protected */
    public boolean getAnyDataEnabledEx() {
        boolean z;
        synchronized (this.mDataEnabledLock) {
            z = this.mInternalDataEnabled && this.mUserDataEnabled && sPolicyDataEnabled && this.mUserDataEnabledDun && this.mEnabledCount != 0;
        }
        if (!z) {
            log("getAnyDataEnabledEx " + z);
        }
        return z;
    }

    public ApnSetting getApnParameter(boolean z, boolean z2, int i, int i2, String str, String str2) {
        String[] strArr;
        String[] strArr2;
        String str3 = null;
        String str4 = null;
        String str5 = null;
        int i3 = 2;
        String[] strArr3 = new String[]{"default", "mms", "supl", "hipri", "dun"};
        String str6 = "IP";
        if (!(z || isNetworkRoaming())) {
            strArr3 = z2 ? new String[]{"default", "mms", "supl", "hipri"} : new String[]{"dun"};
        }
        String[] split = getFixedParameter().split(",");
        if (i == 1002) {
            str3 = this.mApnCarNavi.apn;
            str4 = this.mApnCarNavi.user;
            str5 = this.mApnCarNavi.password;
            i3 = this.mApnCarNavi.authType;
            strArr = new String[]{CharacterSets.MIMENAME_ANY_CHARSET};
            strArr2 = this.mDnsesCarNavi;
        } else {
            if (i == 1001) {
                if (z) {
                    if (split.length >= 3) {
                        str4 = split[1];
                        str5 = split[2];
                        strArr = strArr3;
                        strArr2 = null;
                    }
                } else if (z2) {
                    if (split.length >= 6) {
                        str3 = split[3];
                        str4 = split[4];
                        str5 = split[5];
                        strArr = strArr3;
                        strArr2 = null;
                    }
                } else if (split.length >= 9) {
                    str3 = split[6];
                    str4 = split[7];
                    str5 = split[8];
                    strArr = strArr3;
                    strArr2 = null;
                }
            } else if (!z) {
                if (z2) {
                    if (split.length >= 12) {
                        str3 = split[9];
                        str4 = split[10];
                        str5 = split[11];
                        strArr = strArr3;
                        strArr2 = null;
                    }
                } else if (split.length >= 15) {
                    str3 = split[12];
                    str4 = split[13];
                    str5 = split[14];
                    strArr = strArr3;
                    strArr2 = null;
                }
            }
            strArr = strArr3;
            strArr2 = null;
        }
        if (!z) {
            str6 = "IPV4V6";
        }
        ApnSetting apnSetting = new ApnSetting(i2, str, str2, str3, "", "", "", "", "", str4, str5, i3, strArr, str6, "IP", true, 0, 0, false, 0, 0, 0, 0, "", "");
        apnSetting.oemDnses = strArr2;
        return apnSetting;
    }

    public int getApnPriority(String str) {
        return -1;
    }

    public boolean getDataEnabled() {
        int i = 0;
        try {
            return Global.getInt(this.mPhone.getContext().getContentResolver(), new StringBuilder().append("mobile_data").append(this.mPhone.getPhoneId()).toString()) != 0;
        } catch (SettingNotFoundException e) {
            try {
                ContentResolver contentResolver = this.mPhone.getContext().getContentResolver();
                boolean z = Global.getInt(contentResolver, "mobile_data") != 0;
                String str = "mobile_data" + this.mPhone.getPhoneId();
                if (z) {
                    i = 1;
                }
                Global.putInt(contentResolver, str, i);
                return z;
            } catch (SettingNotFoundException e2) {
                return true;
            }
        }
    }

    public boolean getDataOnRoamingEnabled() {
        int i = 1;
        ContentResolver contentResolver;
        try {
            contentResolver = this.mPhone.getContext().getContentResolver();
            String str = "data_roaming";
            if (TelephonyManager.getDefault().isMultiSimEnabled()) {
                str = "data_roaming" + this.mPhone.getPhoneId();
            }
            return Global.getInt(contentResolver, str) != 0;
        } catch (SettingNotFoundException e) {
            try {
                contentResolver = this.mPhone.getContext().getContentResolver();
                boolean z = Global.getInt(contentResolver, "data_roaming") != 0;
                String str2 = "data_roaming" + this.mPhone.getPhoneId();
                if (!z) {
                    i = 0;
                }
                Global.putInt(contentResolver, str2, i);
                return z;
            } catch (SettingNotFoundException e2) {
                return false;
            }
        }
    }

    public String getHigherPriorityApnType() {
        int i = 0;
        String str = "default";
        if (TelBrand.IS_KDDI) {
            ApnContext[] apnContextArr = (ApnContext[]) this.mPrioritySortedApnContexts.toArray(new ApnContext[0]);
            int length = apnContextArr.length;
            while (i < length) {
                ApnContext apnContext = apnContextArr[i];
                if (apnContext.isEnabled()) {
                    String apnType = apnContext.getApnType();
                    log("higherPrioApnType : " + apnType);
                    return apnType;
                }
                i++;
            }
        }
        return str;
    }

    /* Access modifiers changed, original: protected */
    public int getInitialMaxRetry() {
        if (this.mFailFast) {
            return 0;
        }
        return Global.getInt(this.mResolver, "mdc_initial_max_retry", SystemProperties.getInt("mdc_initial_max_retry", 1));
    }

    public LinkProperties getLinkProperties(String str) {
        return isApnIdEnabled(apnTypeToId(str)) ? ((DcAsyncChannel) this.mDataConnectionAcHashMap.get(Integer.valueOf(0))).getLinkPropertiesSync() : new LinkProperties();
    }

    public NetworkCapabilities getNetworkCapabilities(String str) {
        return isApnIdEnabled(apnTypeToId(str)) ? ((DcAsyncChannel) this.mDataConnectionAcHashMap.get(Integer.valueOf(0))).getNetworkCapabilitiesSync() : new NetworkCapabilities();
    }

    public String[] getOemKddiCpaConnInfo() {
        String[] strArr = new String[3];
        if (this.mEnableApnCarNavi) {
            strArr[0] = new String(this.mLocalAddressCarNavi);
            strArr[1] = new String(this.mDnsAddressCarNavi[0]);
            strArr[2] = new String(this.mDnsAddressCarNavi[1]);
            log("getAddress[0] : " + strArr[0] + " / " + this.mLocalAddressCarNavi);
            log("getAddress[1] : " + strArr[1] + " / " + this.mDnsAddressCarNavi[0]);
            log("getAddress[2] : " + strArr[2] + " / " + this.mDnsAddressCarNavi[1]);
        }
        return strArr;
    }

    public int getOemKddiCpaConnStatus() {
        String higherPriorityApnType = getHigherPriorityApnType();
        log("getConnStatus() higherPrioApnType : " + higherPriorityApnType);
        if ((!this.mEnableApnCarNavi && (this.mEnableApnCarNavi || !this.mDisConnectingApnCarNavi)) || !isApnTypeActive(higherPriorityApnType)) {
            return 6;
        }
        State state = getState(higherPriorityApnType);
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
        }
        return 6;
    }

    public abstract State getOverallState();

    public abstract String[] getPcscfAddress(String str);

    public int getRecoveryAction() {
        int i = System.getInt(this.mPhone.getContext().getContentResolver(), "radio.data.stall.recovery.action", 0);
        log("getRecoveryAction: " + i);
        return i;
    }

    /* Access modifiers changed, original: protected */
    public String getReryConfig(boolean z) {
        int networkType = this.mPhone.getServiceState().getNetworkType();
        return (networkType == 4 || networkType == 7 || networkType == 5 || networkType == 6 || networkType == 12 || networkType == 14) ? SystemProperties.get("ro.cdma.data_retry_config") : z ? SystemProperties.get("ro.gsm.data_retry_config") : SystemProperties.get("ro.gsm.2nd_data_retry_config");
    }

    public abstract State getState(String str);

    public long getSubId() {
        return (long) this.mPhone.getSubId();
    }

    public abstract void gotoIdleAndNotifyDataConnection(String str);

    public void handleMessage(Message message) {
        int i = 1;
        boolean z;
        Bundle data;
        boolean z2;
        switch (message.what) {
            case 69636:
                log("DISCONNECTED_CONNECTED: msg=" + message);
                DcAsyncChannel dcAsyncChannel = (DcAsyncChannel) message.obj;
                this.mDataConnectionAcHashMap.remove(Integer.valueOf(dcAsyncChannel.getDataConnectionIdSync()));
                dcAsyncChannel.disconnected();
                return;
            case 270336:
                this.mCidActive = message.arg1;
                onDataSetupComplete((AsyncResult) message.obj);
                return;
            case 270337:
                onRadioAvailable();
                return;
            case 270339:
                onTrySetupData(message.obj instanceof String ? (String) message.obj : null);
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
                onEnableApn(message.arg1, message.arg2);
                return;
            case 270351:
                log("DataConnectionTracker.handleMessage: EVENT_DISCONNECT_DONE msg=" + message);
                onDisconnectDone(message.arg1, (AsyncResult) message.obj);
                onUpdateOemDataSettingsAsync();
                return;
            case 270353:
                onDataStallAlarm(message.arg1);
                return;
            case 270360:
                onCleanUpConnection(message.arg1 != 0, message.arg2, (String) message.obj);
                return;
            case 270362:
                restartRadio();
                return;
            case 270363:
                if (message.arg1 != 1) {
                    z = false;
                }
                onSetInternalDataEnabled(z);
                return;
            case 270364:
                log("EVENT_RESET_DONE");
                onResetDone((AsyncResult) message.obj);
                return;
            case 270365:
                onCleanUpAllConnections((String) message.obj);
                return;
            case 270366:
                if (message.arg1 != 1) {
                    z = false;
                }
                log("CMD_SET_USER_DATA_ENABLE enabled=" + z);
                onSetUserDataEnabled(z);
                return;
            case 270367:
                if (message.arg1 != 1) {
                    z = false;
                }
                log("CMD_SET_DEPENDENCY_MET met=" + z);
                data = message.getData();
                if (data != null) {
                    String str = (String) data.get("apnType");
                    if (str != null) {
                        onSetDependencyMet(str, z);
                        return;
                    }
                    return;
                }
                return;
            case 270368:
                if (message.arg1 != 1) {
                    z = false;
                }
                onSetPolicyDataEnabled(z);
                return;
            case 270369:
                onUpdateIcc();
                return;
            case 270370:
                log("DataConnectionTracker.handleMessage: EVENT_DISCONNECT_DC_RETRYING msg=" + message);
                onDisconnectDcRetrying(message.arg1, (AsyncResult) message.obj);
                return;
            case 270371:
                onDataSetupCompleteError((AsyncResult) message.obj);
                return;
            case 270372:
                sEnableFailFastRefCounter = (message.arg1 == 1 ? 1 : -1) + sEnableFailFastRefCounter;
                log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA:  sEnableFailFastRefCounter=" + sEnableFailFastRefCounter);
                if (sEnableFailFastRefCounter < 0) {
                    loge("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: sEnableFailFastRefCounter:" + sEnableFailFastRefCounter + " < 0");
                    sEnableFailFastRefCounter = 0;
                }
                z2 = sEnableFailFastRefCounter > 0;
                log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: enabled=" + z2 + " sEnableFailFastRefCounter=" + sEnableFailFastRefCounter);
                if (this.mFailFast != z2) {
                    this.mFailFast = z2;
                    if (z2) {
                        z = false;
                    }
                    this.mDataStallDetectionEnabled = z;
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
                data = message.getData();
                if (data != null) {
                    try {
                        this.mProvisioningUrl = (String) data.get("provisioningUrl");
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
                try {
                    data = message.getData();
                    CharSequence charSequence = data != null ? (String) data.get("apnType") : null;
                    if (TextUtils.isEmpty(charSequence)) {
                        loge("CMD_IS_PROVISIONING_APN: apnType is empty");
                        z2 = false;
                    } else {
                        z2 = isProvisioningApn(charSequence);
                    }
                } catch (ClassCastException e2) {
                    loge("CMD_IS_PROVISIONING_APN: NO provisioning url ignoring");
                    z2 = false;
                }
                log("CMD_IS_PROVISIONING_APN: ret=" + z2);
                AsyncChannel asyncChannel = this.mReplyAc;
                if (!z2) {
                    i = 0;
                }
                asyncChannel.replyToMessage(message, 270374, i);
                return;
            case 270375:
                log("EVENT_PROVISIONING_APN_ALARM");
                ApnContext apnContext = (ApnContext) this.mApnContexts.get("default");
                if (!apnContext.isProvisioningApn() || !apnContext.isConnectedOrConnecting()) {
                    log("EVENT_PROVISIONING_APN_ALARM: Not connected ignore");
                    return;
                } else if (this.mProvisioningApnAlarmTag == message.arg1) {
                    log("EVENT_PROVISIONING_APN_ALARM: Disconnecting");
                    this.mIsProvisioning = false;
                    this.mProvisioningUrl = null;
                    stopProvisioningApnAlarm();
                    sendCleanUpConnection(true, apnContext);
                    return;
                } else {
                    log("EVENT_PROVISIONING_APN_ALARM: ignore stale tag, mProvisioningApnAlarmTag:" + this.mProvisioningApnAlarmTag + " != arg1:" + message.arg1);
                    return;
                }
            case 270376:
                if (message.arg1 == 1) {
                    handleStartNetStatPoll((Activity) message.obj);
                    return;
                } else if (message.arg1 == 0) {
                    handleStopNetStatPoll((Activity) message.obj);
                    return;
                } else {
                    return;
                }
            default:
                Rlog.e("DATA", "Unidentified event msg=" + message);
                return;
        }
    }

    /* Access modifiers changed, original: protected */
    public void handleStartNetStatPoll(Activity activity) {
        this.mIsPhysicalLinkUp = true;
        startNetStatPoll();
        startDataStallAlarm(false);
        setActivity(activity);
    }

    /* Access modifiers changed, original: protected */
    public void handleStopNetStatPoll(Activity activity) {
        this.mIsPhysicalLinkUp = false;
        stopNetStatPoll();
        stopDataStallAlarm();
        setActivity(activity);
    }

    public boolean hasMatchedTetherApnSetting() {
        ApnSetting fetchDunApn = fetchDunApn();
        log("hasMatchedTetherApnSetting: APN=" + fetchDunApn);
        return fetchDunApn != null;
    }

    public void incApnRefCount(String str) {
    }

    /* Access modifiers changed, original: protected */
    public boolean isApnIdEnabled(int i) {
        boolean z;
        synchronized (this) {
            z = i != -1 ? this.mDataEnabled[i] : false;
        }
        return z;
    }

    public boolean isApnSupported(String str) {
        return false;
    }

    public boolean isApnTypeActive(String str) {
        if ("dun".equals(str)) {
            ApnSetting fetchDunApn = fetchDunApn();
            if (fetchDunApn != null) {
                if (this.mActiveApn == null || !fetchDunApn.toString().equals(this.mActiveApn.toString())) {
                    return false;
                }
                return true;
            }
        }
        if (this.mActiveApn == null || !this.mActiveApn.canHandleType(str)) {
            return false;
        }
        return true;
    }

    public abstract boolean isApnTypeAvailable(String str);

    public boolean isApnTypeEnabled(String str) {
        return str == null ? false : isApnIdEnabled(apnTypeToId(str));
    }

    /* Access modifiers changed, original: protected */
    public boolean isConnected() {
        return false;
    }

    public abstract boolean isDataAllowed();

    public abstract boolean isDataPossible(String str);

    public abstract boolean isDisconnected();

    /* Access modifiers changed, original: protected */
    public boolean isEmergency() {
        boolean z;
        synchronized (this.mDataEnabledLock) {
            z = this.mPhone.isInEcm() || this.mPhone.isInEmergencyCall();
        }
        log("isEmergency: result=" + z);
        return z;
    }

    public abstract boolean isOnDemandDataPossible(String str);

    public abstract boolean isPermanentFail(DcFailCause dcFailCause);

    public abstract boolean isProvisioningApn(String str);

    public abstract void log(String str);

    public abstract void loge(String str);

    public abstract boolean mvnoMatches(IccRecords iccRecords, String str, String str2);

    /* Access modifiers changed, original: protected */
    public void notifyDataConnection(String str) {
        for (int i = 0; i < 10; i++) {
            if (this.mDataEnabled[i]) {
                this.mPhone.notifyDataConnection(str, apnIdToType(i));
            }
        }
        notifyOffApnsOfAvailability(str);
    }

    /* Access modifiers changed, original: protected */
    public void notifyDataConnectionCarNavi(State state) {
        int i = 4;
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
                if (state == State.RETRYING || state == State.CONNECTING || state == State.SCANNING) {
                    i = 1;
                    if (this.mConnectingApnCarNavi) {
                        log("mConnectingApnCarNavi = false");
                        this.mConnectingApnCarNavi = false;
                    }
                } else if (state == State.CONNECTED) {
                    i = 2;
                } else if (state == State.DISCONNECTING) {
                    i = 3;
                } else if (state == State.FAILED || state == State.IDLE) {
                }
                Intent intent = new Intent(CONNECTIVITY_ACTION_CARNAVI);
                intent.putExtra(EXTRA_CONNECTIVITY_STATUS_CARNAVI, i);
                intent.putExtra(EXTRA_ERRONO_CARNAVI, 0);
                this.mPhone.getContext().sendStickyBroadcast(intent);
                log("Sent intent CONNECTIVITY_ACTION for CarNavi : " + state + " / " + i);
            }
            if (this.mEnableApnCarNavi && this.mState == State.IDLE) {
                changeMode(false, "", "", "", 0, "", "", "", "");
                this.mEnableApnCarNavi = false;
                this.mDisConnectingApnCarNavi = false;
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void notifyOffApnsOfAvailability(String str) {
        log("notifyOffApnsOfAvailability - reason= " + str);
        for (int i = 0; i < 10; i++) {
            if (!isApnIdEnabled(i)) {
                notifyApnIdDisconnected(str, i);
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void onActionIntentDataStallAlarm(Intent intent) {
        log("onActionIntentDataStallAlarm: action=" + intent.getAction());
        Message obtainMessage = obtainMessage(270353, intent.getAction());
        obtainMessage.arg1 = intent.getIntExtra(DATA_STALL_ALARM_TAG_EXTRA, 0);
        sendMessage(obtainMessage);
    }

    /* Access modifiers changed, original: protected */
    public void onActionIntentProvisioningApnAlarm(Intent intent) {
        log("onActionIntentProvisioningApnAlarm: action=" + intent.getAction());
        Message obtainMessage = obtainMessage(270375, intent.getAction());
        obtainMessage.arg1 = intent.getIntExtra(PROVISIONING_APN_ALARM_TAG_EXTRA, 0);
        sendMessage(obtainMessage);
    }

    /* Access modifiers changed, original: protected */
    public void onActionIntentReconnectAlarm(Intent intent) {
        String stringExtra = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_REASON);
        String stringExtra2 = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_TYPE);
        log("onActionIntentReconnectAlarm: currSubId = " + intent.getIntExtra("subscription", -1) + " phoneSubId=" + this.mPhone.getSubId());
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(stringExtra2);
        log("onActionIntentReconnectAlarm: mState=" + this.mState + " reason=" + stringExtra + " apnType=" + stringExtra2 + " apnContext=" + apnContext + " mDataConnectionAsyncChannels=" + this.mDataConnectionAcHashMap);
        if (apnContext != null && apnContext.isEnabled()) {
            apnContext.setReason(stringExtra);
            State state = apnContext.getState();
            log("onActionIntentReconnectAlarm: apnContext state=" + state);
            if (state == State.FAILED || state == State.IDLE) {
                log("onActionIntentReconnectAlarm: state is FAILED|IDLE, disassociate");
                DcAsyncChannel dcAc = apnContext.getDcAc();
                if (dcAc != null) {
                    dcAc.tearDown(apnContext, "", null);
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

    /* Access modifiers changed, original: protected */
    public void onActionIntentRestartTrySetupAlarm(Intent intent) {
        String stringExtra = intent.getStringExtra(INTENT_RESTART_TRYSETUP_ALARM_EXTRA_TYPE);
        ApnContext apnContext = (ApnContext) this.mApnContexts.get(stringExtra);
        log("onActionIntentRestartTrySetupAlarm: mState=" + this.mState + " apnType=" + stringExtra + " apnContext=" + apnContext + " mDataConnectionAsyncChannels=" + this.mDataConnectionAcHashMap);
        sendMessage(obtainMessage(270339, apnContext));
    }

    public abstract void onCleanUpAllConnections(String str);

    public abstract void onCleanUpConnection(boolean z, int i, String str);

    public abstract void onDataSetupComplete(AsyncResult asyncResult);

    public abstract void onDataSetupCompleteError(AsyncResult asyncResult);

    /* Access modifiers changed, original: protected */
    public void onDataStallAlarm(int i) {
        if (this.mDataStallAlarmTag != i) {
            log("onDataStallAlarm: ignore, tag=" + i + " expecting " + this.mDataStallAlarmTag);
            return;
        }
        updateDataStallInfo();
        int i2 = Global.getInt(this.mResolver, "pdp_watchdog_trigger_packet_count", 10);
        boolean z = false;
        if (this.mSentSinceLastRecv >= ((long) i2)) {
            log("onDataStallAlarm: tag=" + i + " do recovery action=" + getRecoveryAction());
            z = true;
            sendMessage(obtainMessage(270354));
        } else {
            log("onDataStallAlarm: tag=" + i + " Sent " + String.valueOf(this.mSentSinceLastRecv) + " pkts since last received, < watchdogTrigger=" + i2);
        }
        startDataStallAlarm(z);
    }

    public abstract void onDisconnectDcRetrying(int i, AsyncResult asyncResult);

    public abstract void onDisconnectDone(int i, AsyncResult asyncResult);

    /* Access modifiers changed, original: protected */
    public void onEnableApn(int i, int i2) {
        log("EVENT_APN_ENABLE_REQUEST apnId=" + i + ", apnType=" + apnIdToType(i) + ", enabled=" + i2 + ", dataEnabled = " + this.mDataEnabled[i] + ", enabledCount = " + this.mEnabledCount + ", isApnTypeActive = " + isApnTypeActive(apnIdToType(i)));
        if (i2 == 1) {
            synchronized (this) {
                if (!this.mDataEnabled[i]) {
                    this.mDataEnabled[i] = true;
                    this.mEnabledCount++;
                }
            }
            String apnIdToType = apnIdToType(i);
            if (isApnTypeActive(apnIdToType)) {
                notifyApnIdUpToCurrent(Phone.REASON_APN_SWITCHED, i);
                return;
            }
            this.mRequestedApnType = apnIdToType;
            onEnableNewApn();
            return;
        }
        boolean z;
        synchronized (this) {
            if (this.mDataEnabled[i]) {
                this.mDataEnabled[i] = false;
                this.mEnabledCount--;
                z = true;
            } else {
                z = false;
            }
        }
        if (z) {
            if ((this.mEnabledCount == 0 || i == 3) && (!TelBrand.IS_KDDI || this.mEnabledCount == 0)) {
                this.mRequestedApnType = "default";
                onCleanUpConnection(true, i, Phone.REASON_DATA_DISABLED);
            }
            notifyApnIdDisconnected(Phone.REASON_DATA_DISABLED, i);
            if (this.mDataEnabled[0] && !isApnTypeActive("default")) {
                this.mRequestedApnType = "default";
                onEnableNewApn();
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void onEnableNewApn() {
    }

    public abstract void onRadioAvailable();

    public abstract void onRadioOffOrNotAvailable();

    /* Access modifiers changed, original: protected */
    public void onResetDone(AsyncResult asyncResult) {
        log("EVENT_RESET_DONE");
        String str = null;
        if (asyncResult.userObj instanceof String) {
            str = (String) asyncResult.userObj;
        }
        gotoIdleAndNotifyDataConnection(str);
    }

    public abstract void onRoamingOff();

    public abstract void onRoamingOn();

    /* Access modifiers changed, original: protected */
    public void onSetDependencyMet(String str, boolean z) {
    }

    /* Access modifiers changed, original: protected */
    public void onSetInternalDataEnabled(boolean z) {
        synchronized (this.mDataEnabledLock) {
            this.mInternalDataEnabled = z;
            if (z) {
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

    /* Access modifiers changed, original: protected */
    public void onSetPolicyDataEnabled(boolean z) {
        synchronized (this.mDataEnabledLock) {
            if (sPolicyDataEnabled != z) {
                sPolicyDataEnabled = z;
                updateOemDataSettings();
                if (z) {
                    onTrySetupData(Phone.REASON_DATA_ENABLED);
                } else {
                    onCleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED);
                }
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void onSetUserDataEnabled(boolean z) {
        boolean z2 = false;
        synchronized (this.mDataEnabledLock) {
            if (this.mUserDataEnabled != z) {
                this.mUserDataEnabled = z;
                Global.putInt(this.mPhone.getContext().getContentResolver(), "mobile_data" + this.mPhone.getPhoneId(), z ? 1 : 0);
                if (TelBrand.IS_DCM || TelBrand.IS_SBM) {
                    Global.putInt(this.mPhone.getContext().getContentResolver(), "mobile_data", z ? 1 : 0);
                }
                if (!getDataOnRoamingEnabled() && this.mPhone.getServiceState().getDataRoaming()) {
                    if (z) {
                        notifyOffApnsOfAvailability(Phone.REASON_ROAMING_ON);
                    } else {
                        notifyOffApnsOfAvailability(Phone.REASON_DATA_DISABLED);
                    }
                }
                if (TelBrand.IS_DCM || TelBrand.IS_SBM) {
                    if (!z) {
                        z2 = true;
                    }
                    updateOemDataSettingsAsync(z2);
                }
                if (TelBrand.IS_KDDI) {
                    updateOemDataSettings();
                }
                if (z) {
                    onTrySetupData(Phone.REASON_DATA_ENABLED);
                } else {
                    onCleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED);
                }
            }
        }
    }

    public abstract boolean onTrySetupData(String str);

    public abstract boolean onUpdateIcc();

    /* Access modifiers changed, original: protected */
    public void onUpdateOemDataSettingsAsync() {
        log("onUpdateOemDataSettingsAsync");
        if (this.mCnt == 0) {
            log("updateLetApn is not required");
        } else if (isDisconnected()) {
            updateOemDataSettings();
            this.mCnt = 0;
        }
    }

    public abstract void onVoiceCallEnded();

    public abstract void onVoiceCallStarted();

    public void putRecoveryAction(int i) {
        System.putInt(this.mPhone.getContext().getContentResolver(), "radio.data.stall.recovery.action", i);
        log("putRecoveryAction: " + i);
    }

    public void resetDunProfiles() {
    }

    /* Access modifiers changed, original: protected */
    public void resetPollStats() {
        this.mTxPkts = -1;
        this.mRxPkts = -1;
        this.mNetStatPollPeriod = 1000;
    }

    /* Access modifiers changed, original: protected */
    public void restartDataStallAlarm() {
        if (!isConnected()) {
            return;
        }
        if (RecoveryAction.isAggressiveRecovery(getRecoveryAction())) {
            log("restartDataStallAlarm: action is pending. not resetting the alarm.");
            return;
        }
        log("restartDataStallAlarm: stop then start.");
        stopDataStallAlarm();
        startDataStallAlarm(false);
    }

    public abstract void restartRadio();

    /* Access modifiers changed, original: 0000 */
    public void sendCleanUpConnection(boolean z, ApnContext apnContext) {
        log("sendCleanUpConnection: tearDown=" + z + " apnContext=" + apnContext);
        Message obtainMessage = obtainMessage(270360);
        obtainMessage.arg1 = z ? 1 : 0;
        obtainMessage.arg2 = 0;
        obtainMessage.obj = apnContext;
        sendMessage(obtainMessage);
    }

    /* Access modifiers changed, original: protected */
    public void sendOemKddiFailCauseBroadcast(DcFailCause dcFailCause, ApnContext apnContext) {
        if (this.mEnableApnCarNavi) {
            int i = dcFailCause == DcFailCause.USER_AUTHENTICATION ? -3 : (dcFailCause == DcFailCause.SIGNAL_LOST || dcFailCause == DcFailCause.RADIO_POWER_OFF) ? -2 : -4;
            Intent intent = new Intent(CONNECTIVITY_ACTION_CARNAVI);
            intent.putExtra(EXTRA_CONNECTIVITY_STATUS_CARNAVI, 4);
            intent.putExtra(EXTRA_ERRONO_CARNAVI, i);
            this.mPhone.getContext().sendBroadcast(intent);
            log("DataConnection Sent intent CONNECTIVITY_ACTION for CarNavi error : " + i);
        } else if (dcFailCause != DcFailCause.SIGNAL_LOST) {
            Intent intent2 = new Intent(INTENT_CDMA_FAILCAUSE);
            intent2.putExtra(INTENT_CDMA_FAILCAUSE_CAUSE, Integer.toString(dcFailCause.getErrorCode()));
            intent2.putExtra(INTENT_CDMA_FAILCAUSE_EXTRA, apnContext.getApnType());
            this.mPhone.getContext().sendBroadcast(intent2);
            log("sendOemKddiFailCauseBroadcast: send INTENT_CDMA_FAILCAUSE type= " + apnContext.getApnType() + " cause= " + Integer.toString(dcFailCause.getErrorCode()));
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void sendRestartRadio() {
        log("sendRestartRadio:");
        sendMessage(obtainMessage(270362));
    }

    public void sendStartNetStatPoll(Activity activity) {
        Message obtainMessage = obtainMessage(270376);
        obtainMessage.arg1 = 1;
        obtainMessage.obj = activity;
        sendMessage(obtainMessage);
    }

    public void sendStopNetStatPoll(Activity activity) {
        Message obtainMessage = obtainMessage(270376);
        obtainMessage.arg1 = 0;
        obtainMessage.obj = activity;
        sendMessage(obtainMessage);
    }

    /* Access modifiers changed, original: 0000 */
    public void setActivity(Activity activity) {
        log("setActivity = " + activity);
        this.mActivity = activity;
        this.mPhone.notifyDataActivity();
    }

    public abstract void setDataAllowed(boolean z, Message message);

    public void setDataEnabled(boolean z) {
        Message obtainMessage = obtainMessage(270366);
        obtainMessage.arg1 = z ? 1 : 0;
        sendMessage(obtainMessage);
    }

    public void setDataOnRoamingEnabled(boolean z) {
        int i = 1;
        if (getDataOnRoamingEnabled() != z) {
            ContentResolver contentResolver = this.mPhone.getContext().getContentResolver();
            if (!TelephonyManager.getDefault().isMultiSimEnabled()) {
                Global.putInt(contentResolver, "data_roaming", z ? 1 : 0);
            }
            String str = "data_roaming" + this.mPhone.getPhoneId();
            if (!z) {
                i = 0;
            }
            Global.putInt(contentResolver, str, i);
        }
    }

    /* Access modifiers changed, original: protected */
    public void setDataProfilesAsNeeded() {
        log("setDataProfilesAsNeeded");
        if (this.mAllApnSettings != null && !this.mAllApnSettings.isEmpty()) {
            ArrayList arrayList = new ArrayList();
            Iterator it = this.mAllApnSettings.iterator();
            while (it.hasNext()) {
                ApnSetting apnSetting = (ApnSetting) it.next();
                if (apnSetting.modemCognitive) {
                    int i;
                    DataProfile dataProfile = new DataProfile(apnSetting, this.mPhone.getServiceState().getDataRoaming());
                    Iterator it2 = arrayList.iterator();
                    while (it2.hasNext()) {
                        if (dataProfile.equals((DataProfile) it2.next())) {
                            i = 1;
                            break;
                        }
                    }
                    i = 0;
                    if (i == 0) {
                        arrayList.add(dataProfile);
                    }
                }
            }
            if (arrayList.size() > 0) {
                this.mPhone.mCi.setDataProfile((DataProfile[]) arrayList.toArray(new DataProfile[0]), null);
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void setEnabled(int i, boolean z) {
        log("setEnabled(" + i + ", " + z + ") with old state = " + this.mDataEnabled[i] + " and enabledCount = " + this.mEnabledCount);
        Message obtainMessage = obtainMessage(270349);
        obtainMessage.arg1 = i;
        obtainMessage.arg2 = z ? 1 : 0;
        sendMessage(obtainMessage);
    }

    public abstract void setImsRegistrationState(boolean z);

    /* Access modifiers changed, original: protected */
    public void setInitialAttachApn() {
        setInitialAttachApn(this.mAllApnSettings, (IccRecords) this.mIccRecords.get());
    }

    /* Access modifiers changed, original: protected */
    public void setInitialAttachApn(ArrayList<ApnSetting> arrayList, IccRecords iccRecords) {
        ApnSetting apnSetting;
        ApnSetting apnSetting2;
        Message message;
        Object obj;
        String operatorNumeric = iccRecords != null ? iccRecords.getOperatorNumeric() : "";
        log("setInitialApn: E mPreferredApn=" + this.mPreferredApn);
        if (arrayList == null || arrayList.isEmpty()) {
            apnSetting = null;
            apnSetting2 = null;
            message = null;
        } else {
            apnSetting2 = (ApnSetting) arrayList.get(0);
            log("setInitialApn: firstApnSetting=" + apnSetting2);
            Iterator it = arrayList.iterator();
            message = null;
            while (it.hasNext()) {
                apnSetting = (ApnSetting) it.next();
                if (ArrayUtils.contains(apnSetting.types, "ia") && apnSetting.carrierEnabled) {
                    log("setInitialApn: iaApnSetting=" + apnSetting);
                    break;
                } else if (message == null && apnSetting.canHandleType("default")) {
                    log("setInitialApn: defaultApnSetting=" + apnSetting);
                    message = apnSetting;
                }
            }
            apnSetting = null;
        }
        ApnSetting obj2;
        if (apnSetting != null) {
            log("setInitialAttachApn: using iaApnSetting");
            obj2 = apnSetting;
        } else if (this.mPreferredApn == null || !Objects.equals(this.mPreferredApn.numeric, operatorNumeric)) {
            if (message != null) {
                if (!TelBrand.IS_DCM) {
                    log("setInitialAttachApn: using defaultApnSetting");
                    Message obj22 = message;
                }
            } else if (!(apnSetting2 == null || TelBrand.IS_DCM)) {
                log("setInitialAttachApn: using firstApnSetting");
                obj22 = apnSetting2;
            }
            obj22 = null;
        } else {
            log("setInitialAttachApn: using mPreferredApn");
            obj22 = this.mPreferredApn;
        }
        if (obj22 == null) {
            log("setInitialAttachApn: X There in no available apn");
            if (!(operatorNumeric == null || operatorNumeric.equals(""))) {
                this.mPhone.mCi.setInitialAttachApn("", "", 0, "", "", null);
            }
        } else {
            log("setInitialAttachApn: X selected Apn=" + obj22);
            this.mPhone.mCi.setInitialAttachApn(obj22.apn, obj22.protocol, obj22.authType, obj22.user, obj22.password, null);
        }
        updateOemDataSettings();
    }

    public boolean setInternalDataEnabled(boolean z) {
        log("setInternalDataEnabled(" + z + ")");
        Message obtainMessage = obtainMessage(270363);
        obtainMessage.arg1 = z ? 1 : 0;
        sendMessage(obtainMessage);
        return true;
    }

    public void setMobileDataEnabledDun(boolean z) {
        boolean z2 = true;
        if (TelBrand.IS_SBM) {
            synchronized (this.mDataEnabledLock) {
                this.mUserDataEnabledDun = z;
                if (z) {
                    z2 = false;
                    onTrySetupData(Phone.REASON_DATA_ENABLED);
                } else {
                    onCleanUpAllConnections(Phone.REASON_DATA_DISABLED);
                }
                updateOemDataSettingsAsync(z2);
            }
        } else if (TelBrand.IS_DCM) {
            synchronized (this.mDataEnabledLock) {
                z2 = getAnyDataEnabledEx();
                State overallState = getOverallState();
                if (overallState == State.IDLE || overallState == State.FAILED) {
                }
                this.mUserDataEnabledDun = z;
                if (z2 != getAnyDataEnabledEx()) {
                    if (z2) {
                        updateDunProfileName(1, "IP", "dcmtrg.ne.jp");
                        onCleanUpAllConnections(Phone.REASON_DATA_DISABLED);
                    } else {
                        if (this.mPhone.getState() != PhoneConstants.State.IDLE) {
                            this.mIsEpcPending = true;
                        } else {
                            updateOemDataSettings();
                        }
                        onTrySetupData(Phone.REASON_DATA_ENABLED);
                    }
                }
            }
        }
    }

    public void setProfilePdpType(int i, String str) {
        if (TelBrand.IS_DCM) {
            this.mPdpType = str;
            log("setNotifyPdpType: current CID = " + i + ", and correspoding pdpType = " + this.mPdpType);
        }
    }

    public abstract void setState(State state);

    public void setStateCarNavi(ApnContext apnContext) {
        if (TelBrand.IS_KDDI) {
            if (apnContext.getApnType().equals(getHigherPriorityApnType())) {
                notifyDataConnectionCarNavi(apnContext.getState());
            }
        }
    }

    public abstract void setupDataAfterDdsSwitchIfPossible();

    /* Access modifiers changed, original: protected */
    public void startDataStallAlarm(boolean z) {
    }

    /* Access modifiers changed, original: 0000 */
    public void startNetStatPoll() {
        if (getOverallState() == State.CONNECTED && !this.mNetStatPollEnabled) {
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

    /* Access modifiers changed, original: protected */
    public void startProvisioningApnAlarm() {
        int i = Global.getInt(this.mResolver, "provisioning_apn_alarm_delay_in_ms", PROVISIONING_APN_ALARM_DELAY_IN_MS_DEFAULT);
        if (Build.IS_DEBUGGABLE) {
            try {
                i = Integer.parseInt(System.getProperty(DEBUG_PROV_APN_ALARM, Integer.toString(i)));
            } catch (NumberFormatException e) {
                loge("startProvisioningApnAlarm: e=" + e);
            }
        }
        this.mProvisioningApnAlarmTag++;
        log("startProvisioningApnAlarm: tag=" + this.mProvisioningApnAlarmTag + " delay=" + (i / 1000) + "s");
        Intent intent = new Intent(INTENT_PROVISIONING_APN_ALARM);
        intent.putExtra(PROVISIONING_APN_ALARM_TAG_EXTRA, this.mProvisioningApnAlarmTag);
        this.mProvisioningApnAlarmIntent = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
        this.mAlarmManager.set(2, SystemClock.elapsedRealtime() + ((long) i), this.mProvisioningApnAlarmIntent);
    }

    /* Access modifiers changed, original: protected */
    public void stopDataStallAlarm() {
    }

    /* Access modifiers changed, original: 0000 */
    public void stopNetStatPoll() {
        this.mNetStatPollEnabled = false;
        removeCallbacks(this.mPollNetStat);
        log("stopNetStatPoll");
        if (this.mPhone != null) {
            this.mPhone.notifyDataActivity();
        }
    }

    /* Access modifiers changed, original: protected */
    public void stopProvisioningApnAlarm() {
        log("stopProvisioningApnAlarm: current tag=" + this.mProvisioningApnAlarmTag + " mProvsioningApnAlarmIntent=" + this.mProvisioningApnAlarmIntent);
        this.mProvisioningApnAlarmTag++;
        if (this.mProvisioningApnAlarmIntent != null) {
            this.mAlarmManager.cancel(this.mProvisioningApnAlarmIntent);
            this.mProvisioningApnAlarmIntent = null;
        }
    }

    public void updateDataActivity() {
        TxRxSum txRxSum = new TxRxSum(this.mTxPkts, this.mRxPkts);
        TxRxSum txRxSum2 = new TxRxSum();
        txRxSum2.updateTxRxSum();
        this.mTxPkts = txRxSum2.txPkts;
        this.mRxPkts = txRxSum2.rxPkts;
        if (!this.mNetStatPollEnabled) {
            return;
        }
        if (txRxSum.txPkts > 0 || txRxSum.rxPkts > 0) {
            long j = this.mTxPkts - txRxSum.txPkts;
            long j2 = this.mRxPkts - txRxSum.rxPkts;
            Activity activity = (j <= 0 || j2 <= 0) ? (j <= 0 || j2 != 0) ? (j != 0 || j2 <= 0) ? this.mActivity == Activity.DORMANT ? this.mActivity : Activity.NONE : Activity.DATAIN : Activity.DATAOUT : Activity.DATAINANDOUT;
            if (this.mActivity != activity && this.mIsScreenOn) {
                this.mActivity = activity;
                this.mPhone.notifyDataActivity();
            }
        }
    }

    public void updateDunProfileName(int i, String str, String str2) {
    }

    /* Access modifiers changed, original: protected */
    public void updateOemDataSettings() {
        boolean z = true;
        boolean anyDataEnabledEx = getAnyDataEnabledEx();
        boolean dataOnRoamingEnabled = getDataOnRoamingEnabled();
        if (TelBrand.IS_DCM) {
            if (this.mUserDataEnabledDun) {
                this.mPdpType = "IP";
            } else if (this.mPdpType.equals("PPP")) {
                z = false;
                this.mPdpType = "IP";
            }
        }
        this.mPhone.mCi.updateOemDataSettings(anyDataEnabledEx, dataOnRoamingEnabled, z, null);
    }

    /* Access modifiers changed, original: 0000 */
    public void updateOemDataSettingsAsync(boolean z) {
        if (z) {
            this.mCnt++;
            log("updateOemDataSettingsAsync mCnt=" + this.mCnt);
            onUpdateOemDataSettingsAsync();
            return;
        }
        log("updateOemDataSettingsAsync sync");
        updateOemDataSettings();
        this.mCnt = 0;
    }
}
