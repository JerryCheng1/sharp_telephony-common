package com.android.internal.telephony;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.AsyncResult;
import android.os.BaseBundle;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Telephony.BaseMmsColumns;
import android.provider.Telephony.CellBroadcasts;
import android.provider.Telephony.ThreadsColumns;
import android.telephony.CarrierConfigManager;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.Pair;
import android.util.TimeUtils;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import jp.co.sharp.android.internal.telephony.OemTelephonyIntents;

public class ServiceStateTracker extends Handler {
    /* renamed from: -com-android-internal-telephony-CommandsInterface$RadioStateSwitchesValues */
    private static final /* synthetic */ int[] f9xba025bb = null;
    private static final String ACTION_RADIO_OFF = "android.intent.action.ACTION_RADIO_OFF";
    public static final int CS_DISABLED = 1004;
    public static final int CS_EMERGENCY_ENABLED = 1006;
    public static final int CS_ENABLED = 1003;
    public static final int CS_NORMAL_ENABLED = 1005;
    public static final int CS_NOTIFICATION = 999;
    private static final boolean DBG = true;
    public static final int DEFAULT_GPRS_CHECK_PERIOD_MILLIS = 60000;
    public static final String DEFAULT_MNC = "00";
    private static final String DUN_PROFILE_INIT = "persist.radio.init_dun_profile";
    protected static final int EVENT_ALL_DATA_DISCONNECTED = 49;
    protected static final int EVENT_CDMA_PRL_VERSION_CHANGED = 40;
    protected static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 39;
    protected static final int EVENT_CHANGE_IMS_STATE = 45;
    protected static final int EVENT_CHECK_REPORT_GPRS = 22;
    protected static final int EVENT_ERI_FILE_LOADED = 36;
    protected static final int EVENT_GET_CELL_INFO_LIST = 43;
    protected static final int EVENT_GET_DATA_LOC_DONE = 10001;
    protected static final int EVENT_GET_LOC_DONE = 15;
    protected static final int EVENT_GET_PREFERRED_NETWORK_TYPE = 19;
    protected static final int EVENT_GET_SIGNAL_STRENGTH = 3;
    public static final int EVENT_ICC_CHANGED = 42;
    protected static final int EVENT_IMS_CAPABILITY_CHANGED = 48;
    protected static final int EVENT_IMS_STATE_CHANGED = 46;
    protected static final int EVENT_IMS_STATE_DONE = 47;
    protected static final int EVENT_LOCATION_UPDATES_ENABLED = 18;
    protected static final int EVENT_LTE_BAND_INFO = 10002;
    protected static final int EVENT_NETWORK_STATE_CHANGED = 2;
    protected static final int EVENT_NITZ_TIME = 11;
    protected static final int EVENT_NV_READY = 35;
    protected static final int EVENT_OEM_SIGNAL_STRENGTH_UPDATE = 10000;
    protected static final int EVENT_OTA_PROVISION_STATUS_CHANGE = 37;
    protected static final int EVENT_PHONE_TYPE_SWITCHED = 50;
    protected static final int EVENT_POLL_SIGNAL_STRENGTH = 10;
    protected static final int EVENT_POLL_STATE_CDMA_SUBSCRIPTION = 34;
    protected static final int EVENT_POLL_STATE_GPRS = 5;
    protected static final int EVENT_POLL_STATE_NETWORK_SELECTION_MODE = 14;
    protected static final int EVENT_POLL_STATE_OPERATOR = 6;
    protected static final int EVENT_POLL_STATE_REGISTRATION = 4;
    protected static final int EVENT_RADIO_AVAILABLE = 13;
    protected static final int EVENT_RADIO_ON = 41;
    protected static final int EVENT_RADIO_POWER_OFF_DONE = 51;
    protected static final int EVENT_RADIO_STATE_CHANGED = 1;
    protected static final int EVENT_REPORT_SMS_MEMORY_FULL = 61;
    protected static final int EVENT_RESET_PREFERRED_NETWORK_TYPE = 21;
    protected static final int EVENT_RESTRICTED_STATE_CHANGED = 23;
    protected static final int EVENT_RUIM_READY = 26;
    protected static final int EVENT_RUIM_RECORDS_LOADED = 27;
    protected static final int EVENT_SEND_SMS_MEMORY_FULL = 60;
    protected static final int EVENT_SET_PREFERRED_NETWORK_TYPE = 20;
    protected static final int EVENT_SET_RADIO_POWER_OFF = 38;
    protected static final int EVENT_SIGNAL_STRENGTH_UPDATE = 12;
    protected static final int EVENT_SIM_READY = 17;
    protected static final int EVENT_SIM_RECORDS_LOADED = 16;
    protected static final int EVENT_UNSOL_CELL_INFO_LIST = 44;
    protected static final String[] GMT_COUNTRY_CODES = new String[]{"bf", "ci", "eh", "fo", "gb", "gh", "gm", "gn", "gw", "ie", "is", "lr", "ma", "ml", "mr", "pt", "sl", "sn", BaseMmsColumns.STATUS, "tg"};
    public static final String INVALID_MCC = "000";
    private static final long LAST_CELL_INFO_LIST_MAX_AGE_MS = 2000;
    private static final String LOG_TAG = "SST";
    static final int LTE_BAND_DISTINGUISHABLE = SystemProperties.getInt("persist.radio.lteband.dist", 0);
    private static final int MAX_NITZ_YEAR = 2037;
    public static final int MS_PER_HOUR = 3600000;
    public static final int NITZ_UPDATE_DIFF_DEFAULT = 2000;
    public static final int NITZ_UPDATE_SPACING_DEFAULT = 600000;
    public static final int OTASP_NEEDED = 2;
    public static final int OTASP_NOT_NEEDED = 3;
    public static final int OTASP_SIM_UNPROVISIONED = 5;
    public static final int OTASP_UNINITIALIZED = 0;
    public static final int OTASP_UNKNOWN = 1;
    private static final int POLL_PERIOD_MILLIS = 20000;
    private static final String PROP_FORCE_ROAMING = "telephony.test.forceRoaming";
    public static final int PS_DISABLED = 1002;
    public static final int PS_ENABLED = 1001;
    public static final int PS_NOTIFICATION = 888;
    protected static final String REGISTRATION_DENIED_AUTH = "Authentication Failure";
    protected static final String REGISTRATION_DENIED_GEN = "General";
    protected static final String TIMEZONE_PROPERTY = "persist.sys.timezone";
    public static final String UNACTIVATED_MIN2_VALUE = "000000";
    public static final String UNACTIVATED_MIN_VALUE = "1111110111";
    private static final boolean VDBG = false;
    public static final String WAKELOCK_TAG = "ServiceStateTracker";
    private int locationUpdatingContext;
    private boolean mAlarmSwitch = VDBG;
    protected RegistrantList mAttachedRegistrants = new RegistrantList();
    private ContentObserver mAutoTimeObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange) {
            Rlog.i(ServiceStateTracker.LOG_TAG, "Auto time state changed");
            ServiceStateTracker.this.revertToNitzTime();
        }
    };
    private ContentObserver mAutoTimeZoneObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange) {
            Rlog.i(ServiceStateTracker.LOG_TAG, "Auto time zone state changed");
            ServiceStateTracker.this.revertToNitzTimeZone();
        }
    };
    private RegistrantList mCdmaForSubscriptionInfoReadyRegistrants = new RegistrantList();
    private CdmaSubscriptionSourceManager mCdmaSSM;
    public CellLocation mCellLoc;
    private CommandsInterface mCi;
    private int mCid = -1;
    private ContentResolver mCr;
    private String mCurDataSpn = null;
    private String mCurPlmn = null;
    private boolean mCurShowPlmn = VDBG;
    private boolean mCurShowSpn = VDBG;
    private String mCurSpn = null;
    private String mCurrentCarrier = null;
    private int mCurrentOtaspMode = 0;
    private int mDataCid = -1;
    private int mDataLAC = -1;
    private RegistrantList mDataRegStateOrRatChangedRegistrants = new RegistrantList();
    private boolean mDataRoaming = VDBG;
    private RegistrantList mDataRoamingOffRegistrants = new RegistrantList();
    private RegistrantList mDataRoamingOnRegistrants = new RegistrantList();
    private int mDefaultRoamingIndicator;
    private boolean mDesiredPowerState;
    protected RegistrantList mDetachedRegistrants = new RegistrantList();
    private boolean mDeviceShuttingDown = VDBG;
    private boolean mDontPollSignalStrength = VDBG;
    private boolean mEmergencyOnly = VDBG;
    private TelephonyEventLog mEventLog;
    private boolean mGotCountryCode = VDBG;
    private boolean mGsmRoaming = VDBG;
    private HbpcdUtils mHbpcdUtils = null;
    private int[] mHomeNetworkId = null;
    private int[] mHomeSystemId = null;
    private IccRecords mIccRecords = null;
    private boolean mImsRegistered = VDBG;
    private boolean mImsRegistrationOnOff = VDBG;
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (ServiceStateTracker.this.mPhone.isPhoneTypeGsm()) {
                if (intent.getAction().equals("android.intent.action.LOCALE_CHANGED")) {
                    ServiceStateTracker.this.updateSpnDisplay();
                } else if (intent.getAction().equals(ServiceStateTracker.ACTION_RADIO_OFF)) {
                    ServiceStateTracker.this.mAlarmSwitch = ServiceStateTracker.VDBG;
                    ServiceStateTracker.this.powerOffRadioSafely(ServiceStateTracker.this.mPhone.mDcTracker);
                }
                return;
            }
            ServiceStateTracker.this.loge("Ignoring intent " + intent + " received on CDMA phone");
        }
    };
    private boolean mIsEriTextLoaded = VDBG;
    private boolean mIsInPrl;
    private boolean mIsMinInfoReady = VDBG;
    private boolean mIsSmsMemRptSent = VDBG;
    private boolean mIsSubscriptionFromRuim = VDBG;
    private int mLAC = -1;
    private List<CellInfo> mLastCellInfoList = null;
    private long mLastCellInfoListTime;
    private SignalStrength mLastSignalStrength = null;
    int mLteActiveBand = Integer.MAX_VALUE;
    int mLteBand = -1;
    private int mMaxDataCalls = 1;
    private String mMdn;
    private String mMin;
    private boolean mNeedFixZoneAfterNitz = VDBG;
    private RegistrantList mNetworkAttachedRegistrants = new RegistrantList();
    private CellLocation mNewCellLoc;
    private int mNewMaxDataCalls = 1;
    private int mNewReasonDataDenied = -1;
    protected ServiceState mNewSS;
    private int mNitzUpdateDiff = SystemProperties.getInt("ro.nitz_update_diff", NITZ_UPDATE_DIFF_DEFAULT);
    private int mNitzUpdateSpacing = SystemProperties.getInt("ro.nitz_update_spacing", NITZ_UPDATE_SPACING_DEFAULT);
    private boolean mNitzUpdatedTime = VDBG;
    private Notification mNotification;
    private final SstSubscriptionsChangedListener mOnSubscriptionsChangedListener = new SstSubscriptionsChangedListener(this, null);
    private boolean mPendingRadioPowerOffAfterDataOff = VDBG;
    private int mPendingRadioPowerOffAfterDataOffTag = 0;
    protected GsmCdmaPhone mPhone;
    private int[] mPollingContext;
    private boolean mPowerOffDelayNeed = true;
    private int mPreferredNetworkType;
    private String mPrlVersion;
    private RegistrantList mPsRestrictDisabledRegistrants = new RegistrantList();
    private RegistrantList mPsRestrictEnabledRegistrants = new RegistrantList();
    private PendingIntent mRadioOffIntent = null;
    private int mReasonDataDenied = -1;
    private String mRegistrationDeniedReason;
    private int mRegistrationState = -1;
    private boolean mReportedGprsNoReg;
    public RestrictedState mRestrictedState;
    private int mRoamingIndicator;
    public ServiceState mSS;
    private long mSavedAtTime;
    private long mSavedTime;
    private String mSavedTimeZone;
    private SignalStrength mSignalStrength;
    private boolean mSpnUpdatePending = VDBG;
    private boolean mStartedGprsRegCheck;
    private int mSubId = -1;
    private SubscriptionController mSubscriptionController;
    private SubscriptionManager mSubscriptionManager;
    private UiccCardApplication mUiccApplcation = null;
    protected UiccController mUiccController = null;
    private boolean mVoiceCapable;
    private RegistrantList mVoiceRoamingOffRegistrants = new RegistrantList();
    private RegistrantList mVoiceRoamingOnRegistrants = new RegistrantList();
    private WakeLock mWakeLock;
    private boolean mWantContinuousLocationUpdates;
    private boolean mWantSingleLocationUpdate;
    private boolean mZoneDst;
    private int mZoneOffset;
    private long mZoneTime;

    private class CellInfoResult {
        List<CellInfo> list;
        Object lockObj;

        /* synthetic */ CellInfoResult(ServiceStateTracker this$0, CellInfoResult cellInfoResult) {
            this();
        }

        private CellInfoResult() {
            this.lockObj = new Object();
        }
    }

    private class SstSubscriptionsChangedListener extends OnSubscriptionsChangedListener {
        public final AtomicInteger mPreviousSubId;

        /* synthetic */ SstSubscriptionsChangedListener(ServiceStateTracker this$0, SstSubscriptionsChangedListener sstSubscriptionsChangedListener) {
            this();
        }

        private SstSubscriptionsChangedListener() {
            this.mPreviousSubId = new AtomicInteger(-1);
        }

        public void onSubscriptionsChanged() {
            ServiceStateTracker.this.log("SubscriptionListener.onSubscriptionInfoChanged");
            int subId = ServiceStateTracker.this.mPhone.getSubId();
            if (this.mPreviousSubId.getAndSet(subId) != subId) {
                if (ServiceStateTracker.this.mSubscriptionController.isActiveSubId(subId)) {
                    Context context = ServiceStateTracker.this.mPhone.getContext();
                    ServiceStateTracker.this.mPhone.notifyPhoneStateChanged();
                    ServiceStateTracker.this.mPhone.notifyCallForwardingIndicator();
                    ServiceStateTracker.this.mPhone.sendSubscriptionSettings(context.getResources().getBoolean(17956964) ? ServiceStateTracker.VDBG : true);
                    ServiceStateTracker.this.mPhone.setSystemProperty("gsm.network.type", ServiceState.rilRadioTechnologyToString(ServiceStateTracker.this.mSS.getRilDataRadioTechnology()));
                    if (ServiceStateTracker.this.mSpnUpdatePending) {
                        ServiceStateTracker.this.mSubscriptionController.setPlmnSpn(ServiceStateTracker.this.mPhone.getPhoneId(), ServiceStateTracker.this.mCurShowPlmn, ServiceStateTracker.this.mCurPlmn, ServiceStateTracker.this.mCurShowSpn, ServiceStateTracker.this.mCurSpn);
                        ServiceStateTracker.this.mSpnUpdatePending = ServiceStateTracker.VDBG;
                    }
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                    String oldNetworkSelection = sp.getString(Phone.NETWORK_SELECTION_KEY, "");
                    String oldNetworkSelectionName = sp.getString(Phone.NETWORK_SELECTION_NAME_KEY, "");
                    String oldNetworkSelectionShort = sp.getString(Phone.NETWORK_SELECTION_SHORT_KEY, "");
                    if (!(TextUtils.isEmpty(oldNetworkSelection) && TextUtils.isEmpty(oldNetworkSelectionName) && TextUtils.isEmpty(oldNetworkSelectionShort))) {
                        Editor editor = sp.edit();
                        editor.putString(Phone.NETWORK_SELECTION_KEY + subId, oldNetworkSelection);
                        editor.putString(Phone.NETWORK_SELECTION_NAME_KEY + subId, oldNetworkSelectionName);
                        editor.putString(Phone.NETWORK_SELECTION_SHORT_KEY + subId, oldNetworkSelectionShort);
                        editor.remove(Phone.NETWORK_SELECTION_KEY);
                        editor.remove(Phone.NETWORK_SELECTION_NAME_KEY);
                        editor.remove(Phone.NETWORK_SELECTION_SHORT_KEY);
                        editor.commit();
                    }
                    ServiceStateTracker.this.updateSpnDisplay();
                    ServiceStateTracker.this.log("SubscriptionListener.onSubscriptionInfoChanged: ServiceState => " + ServiceStateTracker.this.mSS);
                    ServiceStateTracker.this.mPhone.notifyServiceStateChanged(ServiceStateTracker.this.mSS);
                }
                ServiceStateTracker.this.mPhone.updateVoiceMail();
            }
        }
    }

    private static /* synthetic */ int[] -getcom-android-internal-telephony-CommandsInterface$RadioStateSwitchesValues() {
        if (f9xba025bb != null) {
            return f9xba025bb;
        }
        int[] iArr = new int[RadioState.values().length];
        try {
            iArr[RadioState.RADIO_OFF.ordinal()] = 1;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[RadioState.RADIO_ON.ordinal()] = 3;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[RadioState.RADIO_UNAVAILABLE.ordinal()] = 2;
        } catch (NoSuchFieldError e3) {
        }
        f9xba025bb = iArr;
        return iArr;
    }

    public ServiceStateTracker(GsmCdmaPhone phone, CommandsInterface ci) {
        initOnce(phone, ci);
        updatePhoneType();
    }

    private void initOnce(GsmCdmaPhone phone, CommandsInterface ci) {
        this.mPhone = phone;
        this.mCi = ci;
        this.mVoiceCapable = this.mPhone.getContext().getResources().getBoolean(17956957);
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 42, null);
        this.mCi.setOnSignalStrengthUpdate(this, 12, null);
        this.mCi.registerForCellInfoList(this, 44, null);
        this.mSubscriptionController = SubscriptionController.getInstance();
        this.mSubscriptionManager = SubscriptionManager.from(phone.getContext());
        this.mSubscriptionManager.addOnSubscriptionsChangedListener(this.mOnSubscriptionsChangedListener);
        this.mCi.registerForImsNetworkStateChanged(this, EVENT_IMS_STATE_CHANGED, null);
        this.mWakeLock = ((PowerManager) phone.getContext().getSystemService("power")).newWakeLock(1, WAKELOCK_TAG);
        this.mCi.registerForRadioStateChanged(this, 1, null);
        this.mCi.registerForVoiceNetworkStateChanged(this, 2, null);
        this.mCi.setOnNITZTime(this, 11, null);
        this.mCi.setOnLteBandInfo(this, EVENT_LTE_BAND_INFO, null);
        if (TelBrand.IS_SBM) {
            this.mCi.setOnOemSignalStrengthUpdate(this, EVENT_OEM_SIGNAL_STRENGTH_UPDATE, null);
        }
        this.mCr = phone.getContext().getContentResolver();
        boolean z = (Global.getInt(this.mCr, "enable_cellular_on_boot", 1) <= 0 || Global.getInt(this.mCr, "airplane_mode_on", 0) > 0) ? VDBG : true;
        this.mDesiredPowerState = z;
        this.mCr.registerContentObserver(Global.getUriFor("auto_time"), true, this.mAutoTimeObserver);
        this.mCr.registerContentObserver(Global.getUriFor("auto_time_zone"), true, this.mAutoTimeZoneObserver);
        setSignalStrengthDefaultValues();
        Context context = this.mPhone.getContext();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.LOCALE_CHANGED");
        context.registerReceiver(this.mIntentReceiver, filter);
        filter = new IntentFilter();
        filter.addAction(ACTION_RADIO_OFF);
        context.registerReceiver(this.mIntentReceiver, filter);
        this.mEventLog = new TelephonyEventLog(this.mPhone.getPhoneId());
        this.mPhone.notifyOtaspChanged(0);
    }

    public void updatePhoneType() {
        this.mSS = new ServiceState();
        this.mNewSS = new ServiceState();
        this.mLastCellInfoListTime = 0;
        this.mLastCellInfoList = null;
        this.mSignalStrength = new SignalStrength();
        this.mRestrictedState = new RestrictedState();
        this.mStartedGprsRegCheck = VDBG;
        this.mReportedGprsNoReg = VDBG;
        this.mMdn = null;
        this.mMin = null;
        this.mPrlVersion = null;
        this.mIsMinInfoReady = VDBG;
        this.mNitzUpdatedTime = VDBG;
        cancelPollState();
        if (this.mPhone.isPhoneTypeGsm()) {
            if (this.mCdmaSSM != null) {
                this.mCdmaSSM.dispose(this);
            }
            this.mCi.unregisterForCdmaPrlChanged(this);
            this.mPhone.unregisterForEriFileLoaded(this);
            this.mCi.unregisterForCdmaOtaProvision(this);
            this.mPhone.unregisterForSimRecordsLoaded(this);
            this.mCellLoc = new GsmCellLocation();
            this.mNewCellLoc = new GsmCellLocation();
            this.mCi.registerForAvailable(this, 13, null);
            this.mCi.setOnRestrictedStateChanged(this, 23, null);
        } else {
            this.mCi.unregisterForAvailable(this);
            this.mCi.unSetOnRestrictedStateChanged(this);
            this.mPhone.registerForSimRecordsLoaded(this, 16, null);
            this.mCellLoc = new CdmaCellLocation();
            this.mNewCellLoc = new CdmaCellLocation();
            this.mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(this.mPhone.getContext(), this.mCi, this, 39, null);
            this.mIsSubscriptionFromRuim = this.mCdmaSSM.getCdmaSubscriptionSource() == 0 ? true : VDBG;
            this.mCi.registerForCdmaPrlChanged(this, 40, null);
            this.mPhone.registerForEriFileLoaded(this, 36, null);
            this.mCi.registerForCdmaOtaProvision(this, 37, null);
            this.mHbpcdUtils = new HbpcdUtils(this.mPhone.getContext());
            updateOtaspState();
        }
        onUpdateIccAvailability();
        this.mPhone.setSystemProperty("gsm.network.type", ServiceState.rilRadioTechnologyToString(0));
        this.mCi.getSignalStrength(obtainMessage(3));
        sendMessage(obtainMessage(50));
    }

    public void requestShutdown() {
        if (!this.mDeviceShuttingDown) {
            this.mDeviceShuttingDown = true;
            this.mDesiredPowerState = VDBG;
            setPowerStateToDesired();
        }
    }

    public void dispose() {
        this.mCi.unSetOnSignalStrengthUpdate(this);
        this.mUiccController.unregisterForIccChanged(this);
        this.mCi.unregisterForCellInfoList(this);
        this.mSubscriptionManager.removeOnSubscriptionsChangedListener(this.mOnSubscriptionsChangedListener);
        this.mCi.unregisterForImsNetworkStateChanged(this);
        this.mCi.unSetOnLteBandInfo(this);
        if (TelBrand.IS_SBM) {
            this.mCi.unSetOnOemSignalStrengthUpdate(this);
        }
    }

    public boolean getDesiredPowerState() {
        return this.mDesiredPowerState;
    }

    /* Access modifiers changed, original: protected */
    public boolean notifySignalStrength() {
        if (this.mSignalStrength.equals(this.mLastSignalStrength)) {
            return VDBG;
        }
        try {
            this.mPhone.notifySignalStrength();
            return true;
        } catch (NullPointerException ex) {
            loge("updateSignalStrength() Phone already destroyed: " + ex + "SignalStrength not notified");
            return VDBG;
        }
    }

    /* Access modifiers changed, original: protected */
    public void notifyDataRegStateRilRadioTechnologyChanged() {
        int rat = this.mSS.getRilDataRadioTechnology();
        int drs = this.mSS.getDataRegState();
        log("notifyDataRegStateRilRadioTechnologyChanged: drs=" + drs + " rat=" + rat);
        this.mPhone.setSystemProperty("gsm.network.type", ServiceState.rilRadioTechnologyToString(rat));
        this.mDataRegStateOrRatChangedRegistrants.notifyResult(new Pair(Integer.valueOf(drs), Integer.valueOf(rat)));
    }

    /* Access modifiers changed, original: protected */
    public void useDataRegStateForDataOnlyDevices() {
        if (!this.mVoiceCapable) {
            log("useDataRegStateForDataOnlyDevice: VoiceRegState=" + this.mNewSS.getVoiceRegState() + " DataRegState=" + this.mNewSS.getDataRegState());
            this.mNewSS.setVoiceRegState(this.mNewSS.getDataRegState());
        }
    }

    /* Access modifiers changed, original: protected */
    public void updatePhoneObject() {
        boolean isRegistered = true;
        if (this.mPhone.getContext().getResources().getBoolean(17957020)) {
            if (!(this.mSS.getVoiceRegState() == 0 || this.mSS.getVoiceRegState() == 2)) {
                isRegistered = VDBG;
            }
            if (isRegistered) {
                this.mPhone.updatePhoneObject(this.mSS.getRilVoiceRadioTechnology());
            } else {
                log("updatePhoneObject: Ignore update");
            }
        }
    }

    public void registerForVoiceRoamingOn(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mVoiceRoamingOnRegistrants.add(r);
        if (this.mSS.getVoiceRoaming()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForVoiceRoamingOn(Handler h) {
        this.mVoiceRoamingOnRegistrants.remove(h);
    }

    public void registerForVoiceRoamingOff(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mVoiceRoamingOffRegistrants.add(r);
        if (!this.mSS.getVoiceRoaming()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForVoiceRoamingOff(Handler h) {
        this.mVoiceRoamingOffRegistrants.remove(h);
    }

    public void registerForDataRoamingOn(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mDataRoamingOnRegistrants.add(r);
        if (this.mSS.getDataRoaming()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForDataRoamingOn(Handler h) {
        this.mDataRoamingOnRegistrants.remove(h);
    }

    public void registerForDataRoamingOff(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mDataRoamingOffRegistrants.add(r);
        if (!this.mSS.getDataRoaming()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForDataRoamingOff(Handler h) {
        this.mDataRoamingOffRegistrants.remove(h);
    }

    public void reRegisterNetwork(Message onComplete) {
        this.mCi.getPreferredNetworkType(obtainMessage(19, onComplete));
    }

    public void setRadioPower(boolean power) {
        this.mDesiredPowerState = power;
        setPowerStateToDesired();
    }

    public void enableSingleLocationUpdate() {
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mWantSingleLocationUpdate = true;
            this.mCi.setLocationUpdates(true, obtainMessage(18));
        }
    }

    public void enableLocationUpdates() {
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mWantContinuousLocationUpdates = true;
            this.mCi.setLocationUpdates(true, obtainMessage(18));
        }
    }

    /* Access modifiers changed, original: protected */
    public void disableSingleLocationUpdate() {
        this.mWantSingleLocationUpdate = VDBG;
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mCi.setLocationUpdates(VDBG, null);
        }
    }

    public void disableLocationUpdates() {
        this.mWantContinuousLocationUpdates = VDBG;
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mCi.setLocationUpdates(VDBG, null);
        }
    }

    public void handleMessage(Message msg) {
        AsyncResult ar;
        switch (msg.what) {
            case 1:
            case 50:
                if (!this.mPhone.isPhoneTypeGsm() && this.mCi.getRadioState() == RadioState.RADIO_ON) {
                    handleCdmaSubscriptionSource(this.mCdmaSSM.getCdmaSubscriptionSource());
                    queueNextSignalStrengthPoll();
                }
                setPowerStateToDesired();
                modemTriggeredPollState();
                break;
            case 2:
                modemTriggeredPollState();
                break;
            case 3:
                if (this.mCi.getRadioState().isOn()) {
                    ar = (AsyncResult) msg.obj;
                    if (!TelBrand.IS_SBM) {
                        onSignalStrengthResult(ar);
                    }
                    queueNextSignalStrengthPoll();
                    break;
                }
                return;
            case 4:
            case 5:
            case 6:
                handlePollStateResult(msg.what, (AsyncResult) msg.obj);
                break;
            case 10:
                this.mCi.getSignalStrength(obtainMessage(3));
                break;
            case 11:
                ar = (AsyncResult) msg.obj;
                setTimeFromNITZString(((Object[]) ar.result)[0], ((Long) ((Object[]) ar.result)[1]).longValue());
                break;
            case 12:
                if (!TelBrand.IS_SBM) {
                    ar = (AsyncResult) msg.obj;
                    this.mDontPollSignalStrength = true;
                    onSignalStrengthResult(ar);
                    break;
                }
                break;
            case 13:
                break;
            case 14:
                log("EVENT_POLL_STATE_NETWORK_SELECTION_MODE");
                ar = (AsyncResult) msg.obj;
                if (!this.mPhone.isPhoneTypeGsm()) {
                    if (ar.exception == null && ar.result != null) {
                        if (((int[]) ar.result)[0] == 1) {
                            this.mPhone.setNetworkSelectionModeAutomatic(null);
                            break;
                        }
                    }
                    log("Unable to getNetworkSelectionMode");
                    break;
                }
                handlePollStateResult(msg.what, ar);
                break;
                break;
            case 15:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    String[] states = (String[]) ar.result;
                    if (this.mPhone.isPhoneTypeGsm()) {
                        int lac = -1;
                        int cid = -1;
                        if (states.length >= 3) {
                            try {
                                if (states[1] != null && states[1].length() > 0) {
                                    lac = Integer.parseInt(states[1], 16);
                                }
                                if (states[2] != null && states[2].length() > 0) {
                                    cid = Integer.parseInt(states[2], 16);
                                }
                            } catch (NumberFormatException ex) {
                                Rlog.w(LOG_TAG, "error parsing location: " + ex);
                            }
                        }
                        this.mLAC = lac;
                        this.mCid = cid;
                        ((GsmCellLocation) this.mCellLoc).setLacAndCid(getLacOrDataLac(), getCidOrDataCid());
                    } else {
                        int baseStationId = -1;
                        int baseStationLatitude = Integer.MAX_VALUE;
                        int baseStationLongitude = Integer.MAX_VALUE;
                        int systemId = -1;
                        int networkId = -1;
                        if (states.length > 9) {
                            try {
                                if (states[4] != null) {
                                    baseStationId = Integer.parseInt(states[4]);
                                }
                                if (states[5] != null) {
                                    baseStationLatitude = Integer.parseInt(states[5]);
                                }
                                if (states[6] != null) {
                                    baseStationLongitude = Integer.parseInt(states[6]);
                                }
                                if (baseStationLatitude == 0 && baseStationLongitude == 0) {
                                    baseStationLatitude = Integer.MAX_VALUE;
                                    baseStationLongitude = Integer.MAX_VALUE;
                                }
                                if (states[8] != null) {
                                    systemId = Integer.parseInt(states[8]);
                                }
                                if (states[9] != null) {
                                    networkId = Integer.parseInt(states[9]);
                                }
                            } catch (NumberFormatException ex2) {
                                loge("error parsing cell location data: " + ex2);
                            }
                        }
                        ((CdmaCellLocation) this.mCellLoc).setCellLocationData(baseStationId, baseStationLatitude, baseStationLongitude, systemId, networkId);
                    }
                }
                this.locationUpdatingContext--;
                if (this.locationUpdatingContext == 0) {
                    this.mPhone.notifyLocationChanged();
                    disableSingleLocationUpdate();
                    break;
                }
                break;
            case 16:
                log("EVENT_SIM_RECORDS_LOADED: what=" + msg.what);
                updatePhoneObject();
                updateOtaspState();
                if (this.mPhone.isPhoneTypeGsm()) {
                    updateSpnDisplay();
                    break;
                }
                break;
            case 17:
                this.mOnSubscriptionsChangedListener.mPreviousSubId.set(-1);
                pollState();
                queueNextSignalStrengthPoll();
                break;
            case 18:
                if (((AsyncResult) msg.obj).exception == null) {
                    this.locationUpdatingContext = 0;
                    this.locationUpdatingContext++;
                    this.mCi.getVoiceRegistrationState(obtainMessage(15, null));
                    this.locationUpdatingContext++;
                    this.mCi.getDataRegistrationState(obtainMessage(EVENT_GET_DATA_LOC_DONE, null));
                    break;
                }
                break;
            case 19:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    this.mPreferredNetworkType = ((int[]) ar.result)[0];
                } else {
                    this.mPreferredNetworkType = 7;
                }
                this.mCi.setPreferredNetworkType(7, obtainMessage(20, ar.userObj));
                break;
            case 20:
                this.mCi.setPreferredNetworkType(this.mPreferredNetworkType, obtainMessage(21, ((AsyncResult) msg.obj).userObj));
                break;
            case 21:
                ar = (AsyncResult) msg.obj;
                if (ar.userObj != null) {
                    AsyncResult.forMessage((Message) ar.userObj).exception = ar.exception;
                    ((Message) ar.userObj).sendToTarget();
                    break;
                }
                break;
            case 22:
                if (this.mPhone.isPhoneTypeGsm() && this.mSS != null) {
                    if (!isGprsConsistent(this.mSS.getDataRegState(), this.mSS.getVoiceRegState())) {
                        GsmCellLocation loc = (GsmCellLocation) this.mPhone.getCellLocation();
                        String[] strArr = new Object[2];
                        strArr[0] = this.mSS.getOperatorNumeric();
                        strArr[1] = Integer.valueOf(loc != null ? loc.getCid() : -1);
                        EventLog.writeEvent(EventLogTags.DATA_NETWORK_REGISTRATION_FAIL, strArr);
                        this.mReportedGprsNoReg = true;
                    }
                }
                this.mStartedGprsRegCheck = VDBG;
                break;
            case 23:
                if (this.mPhone.isPhoneTypeGsm()) {
                    log("EVENT_RESTRICTED_STATE_CHANGED");
                    onRestrictedStateChanged((AsyncResult) msg.obj);
                    break;
                }
                break;
            case 26:
                if (this.mPhone.getLteOnCdmaMode() == 1) {
                    log("Receive EVENT_RUIM_READY");
                    pollState();
                } else {
                    log("Receive EVENT_RUIM_READY and Send Request getCDMASubscription.");
                    getSubscriptionInfoAndStartPollingThreads();
                }
                this.mCi.getNetworkSelectionMode(obtainMessage(14));
                break;
            case 27:
                if (!this.mPhone.isPhoneTypeGsm()) {
                    log("EVENT_RUIM_RECORDS_LOADED: what=" + msg.what);
                    updatePhoneObject();
                    if (!this.mPhone.isPhoneTypeCdma()) {
                        RuimRecords ruim = (RuimRecords) this.mIccRecords;
                        if (ruim != null) {
                            if (ruim.isProvisioned()) {
                                this.mMdn = ruim.getMdn();
                                this.mMin = ruim.getMin();
                                parseSidNid(ruim.getSid(), ruim.getNid());
                                this.mPrlVersion = ruim.getPrlVersion();
                                this.mIsMinInfoReady = true;
                            }
                            updateOtaspState();
                            notifyCdmaSubscriptionInfoReady();
                        }
                        pollState();
                        break;
                    }
                    updateSpnDisplay();
                    break;
                }
                break;
            case 34:
                if (!this.mPhone.isPhoneTypeGsm()) {
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        String[] cdmaSubscription = ar.result;
                        if (cdmaSubscription != null && cdmaSubscription.length >= 5) {
                            this.mMdn = cdmaSubscription[0];
                            parseSidNid(cdmaSubscription[1], cdmaSubscription[2]);
                            this.mMin = cdmaSubscription[3];
                            this.mPrlVersion = cdmaSubscription[4];
                            log("GET_CDMA_SUBSCRIPTION: MDN=" + this.mMdn);
                            this.mIsMinInfoReady = true;
                            updateOtaspState();
                            notifyCdmaSubscriptionInfoReady();
                            if (!this.mIsSubscriptionFromRuim && this.mIccRecords != null) {
                                log("GET_CDMA_SUBSCRIPTION set imsi in mIccRecords");
                                this.mIccRecords.setImsi(getImsi());
                                break;
                            }
                            log("GET_CDMA_SUBSCRIPTION either mIccRecords is null or NV type device - not setting Imsi in mIccRecords");
                            break;
                        }
                        log("GET_CDMA_SUBSCRIPTION: error parsing cdmaSubscription params num=" + cdmaSubscription.length);
                        break;
                    }
                }
                break;
            case 35:
                updatePhoneObject();
                this.mCi.getNetworkSelectionMode(obtainMessage(14));
                getSubscriptionInfoAndStartPollingThreads();
                break;
            case 36:
                log("ERI file has been loaded, repolling.");
                pollState();
                break;
            case 37:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    int otaStatus = ((int[]) ar.result)[0];
                    if (otaStatus == 8 || otaStatus == 10) {
                        log("EVENT_OTA_PROVISION_STATUS_CHANGE: Complete, Reload MDN");
                        this.mCi.getCDMASubscription(obtainMessage(34));
                        break;
                    }
                }
                break;
            case 38:
                synchronized (this) {
                    if (!this.mPendingRadioPowerOffAfterDataOff || msg.arg1 != this.mPendingRadioPowerOffAfterDataOffTag) {
                        log("EVENT_SET_RADIO_OFF is stale arg1=" + msg.arg1 + "!= tag=" + this.mPendingRadioPowerOffAfterDataOffTag);
                        break;
                    }
                    log("EVENT_SET_RADIO_OFF, turn radio off now.");
                    hangupAndPowerOff();
                    this.mPendingRadioPowerOffAfterDataOffTag++;
                    this.mPendingRadioPowerOffAfterDataOff = VDBG;
                    break;
                }
                break;
            case 39:
                handleCdmaSubscriptionSource(this.mCdmaSSM.getCdmaSubscriptionSource());
                break;
            case 40:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    this.mPrlVersion = Integer.toString(((int[]) ar.result)[0]);
                    break;
                }
                break;
            case 42:
                onUpdateIccAvailability();
                break;
            case 43:
                ar = msg.obj;
                CellInfoResult result = ar.userObj;
                synchronized (result.lockObj) {
                    if (ar.exception != null) {
                        log("EVENT_GET_CELL_INFO_LIST: error ret null, e=" + ar.exception);
                        result.list = null;
                    } else {
                        result.list = (List) ar.result;
                    }
                    this.mLastCellInfoListTime = SystemClock.elapsedRealtime();
                    this.mLastCellInfoList = result.list;
                    result.lockObj.notify();
                }
            case 44:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    List<CellInfo> list = ar.result;
                    this.mLastCellInfoListTime = SystemClock.elapsedRealtime();
                    this.mLastCellInfoList = list;
                    this.mPhone.notifyCellInfo(list);
                    break;
                }
                log("EVENT_UNSOL_CELL_INFO_LIST: error ignoring, e=" + ar.exception);
                break;
            case EVENT_CHANGE_IMS_STATE /*45*/:
                log("EVENT_CHANGE_IMS_STATE:");
                setPowerStateToDesired();
                break;
            case EVENT_IMS_STATE_CHANGED /*46*/:
                this.mCi.getImsRegistrationState(obtainMessage(47));
                break;
            case 47:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    this.mImsRegistered = ((int[]) ar.result)[0] == 1 ? true : VDBG;
                    break;
                }
                break;
            case EVENT_IMS_CAPABILITY_CHANGED /*48*/:
                log("EVENT_IMS_CAPABILITY_CHANGED");
                updateSpnDisplay();
                updateRilImsRadioTechnology();
                break;
            case 49:
                ProxyController.getInstance().unregisterForAllDataDisconnected(SubscriptionManager.getDefaultDataSubscriptionId(), this);
                synchronized (this) {
                    if (!this.mPendingRadioPowerOffAfterDataOff) {
                        log("EVENT_ALL_DATA_DISCONNECTED is stale");
                        break;
                    }
                    log("EVENT_ALL_DATA_DISCONNECTED, turn radio off now.");
                    hangupAndPowerOff();
                    this.mPendingRadioPowerOffAfterDataOff = VDBG;
                    break;
                }
            case 51:
                log("EVENT_RADIO_POWER_OFF_DONE");
                if (this.mDeviceShuttingDown && this.mCi.getRadioState().isAvailable()) {
                    this.mCi.requestShutdown(null);
                    break;
                }
            case 60:
                if (TelBrand.IS_DCM) {
                    log("EVENT_SEND_SMS_MEMORY_FULL");
                    if (this.mSS.getVoiceRegState() == 0 || this.mSS.getDataRegState() == 0) {
                        log("Either is in service, now send EVENT_SEND_SMS_MEMORY_FULL");
                        setSendSmsMemoryFull();
                        break;
                    }
                }
                break;
            case EVENT_REPORT_SMS_MEMORY_FULL /*61*/:
                if (TelBrand.IS_DCM) {
                    log("EVENT_REPORT_SMS_MEMORY_FULL");
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        log("ReportSmsMemory success!");
                        this.mIsSmsMemRptSent = true;
                        break;
                    }
                    log("handleReportSmsMemoryFullDone, exception:" + ar.exception);
                    this.mIsSmsMemRptSent = VDBG;
                    break;
                }
                break;
            case EVENT_OEM_SIGNAL_STRENGTH_UPDATE /*10000*/:
                if (TelBrand.IS_SBM) {
                    ar = (AsyncResult) msg.obj;
                    this.mDontPollSignalStrength = true;
                    onOemSignalStrengthResult(ar);
                    break;
                }
                break;
            case EVENT_GET_DATA_LOC_DONE /*10001*/:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    String[] dataStates = ar.result;
                    if (this.mPhone.isPhoneTypeGsm()) {
                        int dataLac = -1;
                        int dataCid = -1;
                        if (dataStates.length >= 3) {
                            try {
                                if (dataStates[1] != null && dataStates[1].length() > 0) {
                                    dataLac = Integer.parseInt(dataStates[1], 16);
                                }
                                if (dataStates[2] != null && dataStates[2].length() > 0) {
                                    dataCid = Integer.parseInt(dataStates[2], 16);
                                }
                            } catch (NumberFormatException ex22) {
                                Rlog.w(LOG_TAG, "error parsing location: " + ex22);
                            }
                        }
                        this.mDataLAC = dataLac;
                        this.mDataCid = dataCid;
                        ((GsmCellLocation) this.mCellLoc).setLacAndCid(getLacOrDataLac(), getCidOrDataCid());
                    }
                }
                this.locationUpdatingContext--;
                if (this.locationUpdatingContext == 0) {
                    this.mPhone.notifyLocationChanged();
                    disableSingleLocationUpdate();
                    break;
                }
                break;
            case EVENT_LTE_BAND_INFO /*10002*/:
                onLteBandInfo((AsyncResult) msg.obj);
                break;
            default:
                log("Unhandled message with number: " + msg.what);
                break;
        }
    }

    /* Access modifiers changed, original: protected */
    public boolean isSidsAllZeros() {
        if (this.mHomeSystemId != null) {
            for (int i : this.mHomeSystemId) {
                if (i != 0) {
                    return VDBG;
                }
            }
        }
        return true;
    }

    private boolean isHomeSid(int sid) {
        if (this.mHomeSystemId != null) {
            for (int i : this.mHomeSystemId) {
                if (sid == i) {
                    return true;
                }
            }
        }
        return VDBG;
    }

    public String getMdnNumber() {
        return this.mMdn;
    }

    public String getCdmaMin() {
        return this.mMin;
    }

    public String getPrlVersion() {
        return this.mPrlVersion;
    }

    public String getImsi() {
        String operatorNumeric = ((TelephonyManager) this.mPhone.getContext().getSystemService("phone")).getSimOperatorNumericForPhone(this.mPhone.getPhoneId());
        if (TextUtils.isEmpty(operatorNumeric) || getCdmaMin() == null) {
            return null;
        }
        return operatorNumeric + getCdmaMin();
    }

    public boolean isMinInfoReady() {
        return this.mIsMinInfoReady;
    }

    public int getOtasp() {
        if (!this.mPhone.getIccRecordsLoaded()) {
            log("getOtasp: otasp uninitialized due to sim not loaded");
            return 0;
        } else if (this.mPhone.isPhoneTypeGsm()) {
            log("getOtasp: otasp not needed for GSM");
            return 3;
        } else if (this.mIsSubscriptionFromRuim && this.mMin == null) {
            return 2;
        } else {
            int provisioningState;
            if (this.mMin == null || this.mMin.length() < 6) {
                log("getOtasp: bad mMin='" + this.mMin + "'");
                provisioningState = 1;
            } else if (this.mMin.equals(UNACTIVATED_MIN_VALUE) || this.mMin.substring(0, 6).equals(UNACTIVATED_MIN2_VALUE) || SystemProperties.getBoolean("test_cdma_setup", VDBG)) {
                provisioningState = 2;
            } else {
                provisioningState = 3;
            }
            log("getOtasp: state=" + provisioningState);
            return provisioningState;
        }
    }

    /* Access modifiers changed, original: protected */
    public void parseSidNid(String sidStr, String nidStr) {
        int i;
        if (sidStr != null) {
            String[] sid = sidStr.split(",");
            this.mHomeSystemId = new int[sid.length];
            for (i = 0; i < sid.length; i++) {
                try {
                    this.mHomeSystemId[i] = Integer.parseInt(sid[i]);
                } catch (NumberFormatException ex) {
                    loge("error parsing system id: " + ex);
                }
            }
        }
        log("CDMA_SUBSCRIPTION: SID=" + sidStr);
        if (nidStr != null) {
            String[] nid = nidStr.split(",");
            this.mHomeNetworkId = new int[nid.length];
            for (i = 0; i < nid.length; i++) {
                try {
                    this.mHomeNetworkId[i] = Integer.parseInt(nid[i]);
                } catch (NumberFormatException ex2) {
                    loge("CDMA_SUBSCRIPTION: error parsing network id: " + ex2);
                }
            }
        }
        log("CDMA_SUBSCRIPTION: NID=" + nidStr);
    }

    /* Access modifiers changed, original: protected */
    public void updateOtaspState() {
        int otaspMode = getOtasp();
        int oldOtaspMode = this.mCurrentOtaspMode;
        this.mCurrentOtaspMode = otaspMode;
        if (oldOtaspMode != this.mCurrentOtaspMode) {
            log("updateOtaspState: call notifyOtaspChanged old otaspMode=" + oldOtaspMode + " new otaspMode=" + this.mCurrentOtaspMode);
            this.mPhone.notifyOtaspChanged(this.mCurrentOtaspMode);
        }
    }

    /* Access modifiers changed, original: protected */
    public Phone getPhone() {
        return this.mPhone;
    }

    /* Access modifiers changed, original: protected */
    public void handlePollStateResult(int what, AsyncResult ar) {
        if (ar.userObj == this.mPollingContext) {
            if (ar.exception != null) {
                Error err = null;
                if (ar.exception instanceof CommandException) {
                    err = ((CommandException) ar.exception).getCommandError();
                }
                if (err == Error.RADIO_NOT_AVAILABLE) {
                    cancelPollState();
                    return;
                } else if (err != Error.OP_NOT_ALLOWED_BEFORE_REG_NW) {
                    loge("RIL implementation has returned an error where it must succeed" + ar.exception);
                }
            } else {
                try {
                    handlePollStateResultMessage(what, ar);
                } catch (RuntimeException ex) {
                    loge("Exception while polling service state. Probably malformed RIL response." + ex);
                }
            }
            int[] iArr = this.mPollingContext;
            iArr[0] = iArr[0] - 1;
            if (this.mPollingContext[0] == 0) {
                if (this.mPhone.isPhoneTypeGsm()) {
                    updateRoamingState();
                    this.mNewSS.setEmergencyOnly(this.mEmergencyOnly);
                } else {
                    boolean namMatch = VDBG;
                    if (!isSidsAllZeros() && isHomeSid(this.mNewSS.getSystemId())) {
                        namMatch = true;
                    }
                    if (this.mIsSubscriptionFromRuim) {
                        this.mNewSS.setVoiceRoaming(isRoamingBetweenOperators(this.mNewSS.getVoiceRoaming(), this.mNewSS));
                    }
                    boolean isVoiceInService = this.mNewSS.getVoiceRegState() == 0 ? true : VDBG;
                    int dataRegType = this.mNewSS.getRilDataRadioTechnology();
                    if (isVoiceInService && ServiceState.isCdma(dataRegType)) {
                        this.mNewSS.setDataRoaming(this.mNewSS.getVoiceRoaming());
                    }
                    this.mNewSS.setCdmaDefaultRoamingIndicator(this.mDefaultRoamingIndicator);
                    this.mNewSS.setCdmaRoamingIndicator(this.mRoamingIndicator);
                    boolean isPrlLoaded = true;
                    if (TextUtils.isEmpty(this.mPrlVersion)) {
                        isPrlLoaded = VDBG;
                    }
                    if (!isPrlLoaded || this.mNewSS.getRilVoiceRadioTechnology() == 0) {
                        log("Turn off roaming indicator if !isPrlLoaded or voice RAT is unknown");
                        this.mNewSS.setCdmaRoamingIndicator(1);
                    } else if (!isSidsAllZeros()) {
                        if (!namMatch && !this.mIsInPrl) {
                            this.mNewSS.setCdmaRoamingIndicator(this.mDefaultRoamingIndicator);
                        } else if (!namMatch || this.mIsInPrl) {
                            if (!namMatch && this.mIsInPrl) {
                                this.mNewSS.setCdmaRoamingIndicator(this.mRoamingIndicator);
                            } else if (this.mRoamingIndicator <= 2) {
                                this.mNewSS.setCdmaRoamingIndicator(1);
                            } else {
                                this.mNewSS.setCdmaRoamingIndicator(this.mRoamingIndicator);
                            }
                        } else if (isRatLte(this.mNewSS.getRilVoiceRadioTechnology())) {
                            log("Turn off roaming indicator as voice is LTE");
                            this.mNewSS.setCdmaRoamingIndicator(1);
                        } else {
                            this.mNewSS.setCdmaRoamingIndicator(2);
                        }
                    }
                    int roamingIndicator = this.mNewSS.getCdmaRoamingIndicator();
                    this.mNewSS.setCdmaEriIconIndex(this.mPhone.mEriManager.getCdmaEriIconIndex(roamingIndicator, this.mDefaultRoamingIndicator));
                    this.mNewSS.setCdmaEriIconMode(this.mPhone.mEriManager.getCdmaEriIconMode(roamingIndicator, this.mDefaultRoamingIndicator));
                    log("Set CDMA Roaming Indicator to: " + this.mNewSS.getCdmaRoamingIndicator() + ". voiceRoaming = " + this.mNewSS.getVoiceRoaming() + ". dataRoaming = " + this.mNewSS.getDataRoaming() + ", isPrlLoaded = " + isPrlLoaded + ". namMatch = " + namMatch + " , mIsInPrl = " + this.mIsInPrl + ", mRoamingIndicator = " + this.mRoamingIndicator + ", mDefaultRoamingIndicator= " + this.mDefaultRoamingIndicator);
                }
                pollStateDone();
            }
        }
    }

    private boolean isRoamingBetweenOperators(boolean cdmaRoaming, ServiceState s) {
        return (!cdmaRoaming || isSameOperatorNameFromSimAndSS(s)) ? VDBG : true;
    }

    /* Access modifiers changed, original: protected */
    /* JADX WARNING: Removed duplicated region for block: B:113:0x033e  */
    /* JADX WARNING: Removed duplicated region for block: B:101:0x02a7  */
    /* JADX WARNING: Removed duplicated region for block: B:114:0x0341  */
    /* JADX WARNING: Removed duplicated region for block: B:104:0x02bd  */
    /* JADX WARNING: Removed duplicated region for block: B:238:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:107:0x02ce  */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void handlePollStateResultMessage(int what, AsyncResult ar) {
        String[] states;
        int type;
        int regState;
        switch (what) {
            case 4:
                int cssIndicator;
                if (this.mPhone.isPhoneTypeGsm()) {
                    states = (String[]) ar.result;
                    int lac = -1;
                    int cid = -1;
                    type = 0;
                    regState = 4;
                    int psc = -1;
                    cssIndicator = 0;
                    if (states.length > 0) {
                        try {
                            regState = Integer.parseInt(states[0]);
                            if (states.length >= 3) {
                                if (states[1] != null && states[1].length() > 0) {
                                    lac = Integer.parseInt(states[1], 16);
                                }
                                if (states[2] != null && states[2].length() > 0) {
                                    cid = Integer.parseInt(states[2], 16);
                                }
                                if (states.length >= 4 && states[3] != null) {
                                    type = Integer.parseInt(states[3]);
                                }
                            }
                            if (states.length >= 8 && states[7] != null) {
                                cssIndicator = Integer.parseInt(states[7]);
                            }
                            if (states.length > 14 && states[14] != null && states[14].length() > 0) {
                                psc = Integer.parseInt(states[14], 16);
                            }
                        } catch (NumberFormatException ex) {
                            loge("error parsing RegistrationState: " + ex);
                        }
                    }
                    this.mGsmRoaming = regCodeIsRoaming(regState);
                    this.mNewSS.setVoiceRegState(regCodeToServiceState(regState));
                    this.mNewSS.setRilVoiceRadioTechnology(type);
                    this.mNewSS.setCssIndicator(cssIndicator);
                    boolean isVoiceCapable = this.mPhone.getContext().getResources().getBoolean(17956957);
                    if ((regState == 13 || regState == 10 || regState == 12 || regState == 14) && isVoiceCapable) {
                        this.mEmergencyOnly = true;
                    } else {
                        this.mEmergencyOnly = VDBG;
                    }
                    this.mLAC = lac;
                    this.mCid = cid;
                    ((GsmCellLocation) this.mNewCellLoc).setLacAndCid(getLacOrDataLac(), getCidOrDataCid());
                    ((GsmCellLocation) this.mNewCellLoc).setPsc(psc);
                    return;
                }
                states = (String[]) ar.result;
                int registrationState = 4;
                int radioTechnology = -1;
                int baseStationId = -1;
                int baseStationLatitude = Integer.MAX_VALUE;
                int baseStationLongitude = Integer.MAX_VALUE;
                cssIndicator = 0;
                int systemId = 0;
                int networkId = 0;
                int roamingIndicator = -1;
                int systemIsInPrl = 0;
                int defaultRoamingIndicator = 0;
                int reasonForDenial = 0;
                if (states.length >= 14) {
                    boolean cdmaRoaming;
                    try {
                        if (states[0] != null) {
                            registrationState = Integer.parseInt(states[0]);
                        }
                        if (states[3] != null) {
                            radioTechnology = Integer.parseInt(states[3]);
                        }
                        if (states[4] != null) {
                            baseStationId = Integer.parseInt(states[4]);
                        }
                        if (states[5] != null) {
                            baseStationLatitude = Integer.parseInt(states[5]);
                        }
                        if (states[6] != null) {
                            baseStationLongitude = Integer.parseInt(states[6]);
                        }
                        if (baseStationLatitude == 0 && baseStationLongitude == 0) {
                            baseStationLatitude = Integer.MAX_VALUE;
                            baseStationLongitude = Integer.MAX_VALUE;
                        }
                        if (states[7] != null) {
                            cssIndicator = Integer.parseInt(states[7]);
                        }
                        if (states[8] != null) {
                            systemId = Integer.parseInt(states[8]);
                        }
                        if (states[9] != null) {
                            networkId = Integer.parseInt(states[9]);
                        }
                        if (states[10] != null) {
                            roamingIndicator = Integer.parseInt(states[10]);
                        }
                        if (states[11] != null) {
                            systemIsInPrl = Integer.parseInt(states[11]);
                        }
                        if (states[12] != null) {
                            defaultRoamingIndicator = Integer.parseInt(states[12]);
                        }
                        if (states[13] != null) {
                            reasonForDenial = Integer.parseInt(states[13]);
                        }
                    } catch (NumberFormatException ex2) {
                        loge("EVENT_POLL_STATE_REGISTRATION_CDMA: error parsing: " + ex2);
                    }
                    this.mRegistrationState = registrationState;
                    if (regCodeIsRoaming(registrationState)) {
                        if (!isRoamIndForHomeSystem(states[10])) {
                            cdmaRoaming = true;
                            this.mNewSS.setVoiceRoaming(cdmaRoaming);
                            this.mNewSS.setVoiceRegState(regCodeToServiceState(registrationState));
                            this.mNewSS.setRilVoiceRadioTechnology(radioTechnology);
                            this.mNewSS.setCssIndicator(cssIndicator);
                            this.mNewSS.setSystemAndNetworkId(systemId, networkId);
                            this.mRoamingIndicator = roamingIndicator;
                            this.mIsInPrl = systemIsInPrl != 0 ? VDBG : true;
                            this.mDefaultRoamingIndicator = defaultRoamingIndicator;
                            ((CdmaCellLocation) this.mNewCellLoc).setCellLocationData(baseStationId, baseStationLatitude, baseStationLongitude, systemId, networkId);
                            if (reasonForDenial != 0) {
                                this.mRegistrationDeniedReason = REGISTRATION_DENIED_GEN;
                            } else if (reasonForDenial == 1) {
                                this.mRegistrationDeniedReason = REGISTRATION_DENIED_AUTH;
                            } else {
                                this.mRegistrationDeniedReason = "";
                            }
                            if (this.mRegistrationState != 3) {
                                log("Registration denied, " + this.mRegistrationDeniedReason);
                                return;
                            }
                            return;
                        }
                    }
                    cdmaRoaming = VDBG;
                    this.mNewSS.setVoiceRoaming(cdmaRoaming);
                    this.mNewSS.setVoiceRegState(regCodeToServiceState(registrationState));
                    this.mNewSS.setRilVoiceRadioTechnology(radioTechnology);
                    this.mNewSS.setCssIndicator(cssIndicator);
                    this.mNewSS.setSystemAndNetworkId(systemId, networkId);
                    this.mRoamingIndicator = roamingIndicator;
                    if (systemIsInPrl != 0) {
                    }
                    this.mIsInPrl = systemIsInPrl != 0 ? VDBG : true;
                    this.mDefaultRoamingIndicator = defaultRoamingIndicator;
                    ((CdmaCellLocation) this.mNewCellLoc).setCellLocationData(baseStationId, baseStationLatitude, baseStationLongitude, systemId, networkId);
                    if (reasonForDenial != 0) {
                    }
                    if (this.mRegistrationState != 3) {
                    }
                } else {
                    throw new RuntimeException("Warning! Wrong number of parameters returned from RIL_REQUEST_REGISTRATION_STATE: expected 14 or more strings and got " + states.length + " strings");
                }
                break;
            case 5:
                int dataRegState;
                Object states2;
                if (this.mPhone.isPhoneTypeGsm()) {
                    states = (String[]) ar.result;
                    type = 0;
                    regState = 4;
                    int dataLac = -1;
                    int dataCid = -1;
                    this.mNewReasonDataDenied = -1;
                    this.mNewMaxDataCalls = 1;
                    if (states.length > 0) {
                        try {
                            regState = Integer.parseInt(states[0]);
                            if (states.length >= 4 && states[3] != null) {
                                type = Integer.parseInt(states[3]);
                            }
                            if (states.length >= 5 && regState == 3) {
                                this.mNewReasonDataDenied = Integer.parseInt(states[4]);
                            }
                            if (states.length >= 6) {
                                this.mNewMaxDataCalls = Integer.parseInt(states[5]);
                            }
                            if (states.length >= 2 && states[1] != null && states[1].length() > 0) {
                                dataLac = Integer.parseInt(states[1], 16);
                            }
                            if (states.length >= 3 && states[2] != null && states[2].length() > 0) {
                                dataCid = Integer.parseInt(states[2], 16);
                            }
                        } catch (NumberFormatException ex22) {
                            loge("error parsing GprsRegistrationState: " + ex22);
                        }
                    }
                    dataRegState = regCodeToServiceState(regState);
                    this.mNewSS.setDataRegState(dataRegState);
                    this.mDataRoaming = regCodeIsRoaming(regState);
                    this.mNewSS.setRilDataRadioTechnology(type);
                    log("handlPollStateResultMessage: GsmSST setDataRegState=" + dataRegState + " regState=" + regState + " dataRadioTechnology=" + type);
                    this.mDataLAC = dataLac;
                    this.mDataCid = dataCid;
                    ((GsmCellLocation) this.mNewCellLoc).setLacAndCid(getLacOrDataLac(), getCidOrDataCid());
                    return;
                } else if (this.mPhone.isPhoneTypeCdma()) {
                    states2 = (String[]) ar.result;
                    log("handlePollStateResultMessage: EVENT_POLL_STATE_GPRS states.length=" + states2.length + " states=" + states2);
                    regState = 4;
                    int dataRadioTechnology = 0;
                    if (states2.length > 0) {
                        try {
                            regState = Integer.parseInt(states2[0]);
                            if (states2.length >= 4 && states2[3] != null) {
                                dataRadioTechnology = Integer.parseInt(states2[3]);
                            }
                        } catch (NumberFormatException ex222) {
                            loge("handlePollStateResultMessage: error parsing GprsRegistrationState: " + ex222);
                        }
                    }
                    dataRegState = regCodeToServiceState(regState);
                    this.mNewSS.setDataRegState(dataRegState);
                    this.mNewSS.setRilDataRadioTechnology(dataRadioTechnology);
                    this.mNewSS.setDataRoaming(regCodeIsRoaming(regState));
                    log("handlPollStateResultMessage: cdma setDataRegState=" + dataRegState + " regState=" + regState + " dataRadioTechnology=" + dataRadioTechnology);
                    return;
                } else {
                    states2 = (String[]) ar.result;
                    log("handlePollStateResultMessage: EVENT_POLL_STATE_GPRS states.length=" + states2.length + " states=" + states2);
                    int newDataRAT = 0;
                    regState = -1;
                    if (states2.length > 0) {
                        try {
                            regState = Integer.parseInt(states2[0]);
                            if (states2.length >= 4 && states2[3] != null) {
                                newDataRAT = Integer.parseInt(states2[3]);
                            }
                        } catch (NumberFormatException ex2222) {
                            loge("handlePollStateResultMessage: error parsing GprsRegistrationState: " + ex2222);
                        }
                    }
                    int oldDataRAT = this.mSS.getRilDataRadioTechnology();
                    if ((oldDataRAT == 0 && newDataRAT != 0) || ((ServiceState.isCdma(oldDataRAT) && isRatLte(newDataRAT)) || (isRatLte(oldDataRAT) && ServiceState.isCdma(newDataRAT)))) {
                        this.mCi.getSignalStrength(obtainMessage(3));
                    }
                    this.mNewSS.setRilDataRadioTechnology(newDataRAT);
                    dataRegState = regCodeToServiceState(regState);
                    this.mNewSS.setDataRegState(dataRegState);
                    this.mNewSS.setDataRoaming(regCodeIsRoaming(regState));
                    log("handlPollStateResultMessage: CdmaLteSST setDataRegState=" + dataRegState + " regState=" + regState + " dataRadioTechnology=" + newDataRAT);
                    return;
                }
            case 6:
                String[] opNames;
                String brandOverride;
                if (this.mPhone.isPhoneTypeGsm()) {
                    opNames = (String[]) ar.result;
                    if (opNames != null && opNames.length >= 3) {
                        brandOverride = this.mUiccController.getUiccCard(getPhoneId()) != null ? this.mUiccController.getUiccCard(getPhoneId()).getOperatorBrandOverride() : null;
                        if (brandOverride != null) {
                            log("EVENT_POLL_STATE_OPERATOR: use brandOverride=" + brandOverride);
                            this.mNewSS.setOperatorName(brandOverride, brandOverride, opNames[2]);
                            return;
                        }
                        this.mNewSS.setOperatorName(opNames[0], opNames[1], opNames[2]);
                        return;
                    }
                    return;
                }
                opNames = (String[]) ar.result;
                if (opNames == null || opNames.length < 3) {
                    log("EVENT_POLL_STATE_OPERATOR_CDMA: error parsing opNames");
                    return;
                }
                if (opNames[2] == null || opNames[2].length() < 5 || "00000".equals(opNames[2])) {
                    opNames[2] = SystemProperties.get(GsmCdmaPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC, "00000");
                    log("RIL_REQUEST_OPERATOR.response[2], the numeric,  is bad. Using SystemProperties 'ro.cdma.home.operator.numeric'= " + opNames[2]);
                }
                if (this.mIsSubscriptionFromRuim) {
                    brandOverride = this.mUiccController.getUiccCard(getPhoneId()) != null ? this.mUiccController.getUiccCard(getPhoneId()).getOperatorBrandOverride() : null;
                    if (brandOverride != null) {
                        this.mNewSS.setOperatorName(brandOverride, brandOverride, opNames[2]);
                        return;
                    } else {
                        this.mNewSS.setOperatorName(opNames[0], opNames[1], opNames[2]);
                        return;
                    }
                }
                this.mNewSS.setOperatorName(opNames[0], opNames[1], opNames[2]);
                return;
            case 14:
                int[] ints = (int[]) ar.result;
                this.mNewSS.setIsManualSelection(ints[0] == 1 ? true : VDBG);
                if (ints[0] == 1 && !this.mPhone.isManualNetSelAllowed()) {
                    this.mPhone.setNetworkSelectionModeAutomatic(null);
                    log(" Forcing Automatic Network Selection, manual selection is not allowed");
                    return;
                }
                return;
            default:
                loge("handlePollStateResultMessage: Unexpected RIL response received: " + what);
                return;
        }
    }

    private boolean isRoamIndForHomeSystem(String roamInd) {
        String[] homeRoamIndicators = this.mPhone.getContext().getResources().getStringArray(17236036);
        if (homeRoamIndicators == null) {
            return VDBG;
        }
        for (String homeRoamInd : homeRoamIndicators) {
            if (homeRoamInd.equals(roamInd)) {
                return true;
            }
        }
        return VDBG;
    }

    /* Access modifiers changed, original: protected */
    public void updateRoamingState() {
        CarrierConfigManager configLoader;
        PersistableBundle b;
        if (this.mPhone.isPhoneTypeGsm()) {
            boolean roaming = !this.mGsmRoaming ? this.mDataRoaming : true;
            if (this.mGsmRoaming && !isOperatorConsideredRoaming(this.mNewSS) && (isSameNamedOperators(this.mNewSS) || isOperatorConsideredNonRoaming(this.mNewSS))) {
                roaming = VDBG;
            }
            this.mNewSS.setDataRoamingFromRegistration(roaming);
            configLoader = (CarrierConfigManager) this.mPhone.getContext().getSystemService("carrier_config");
            if (configLoader != null) {
                try {
                    b = configLoader.getConfigForSubId(this.mPhone.getSubId());
                    if (alwaysOnHomeNetwork(b)) {
                        log("updateRoamingState: carrier config override always on home network");
                        roaming = VDBG;
                    } else if (isNonRoamingInGsmNetwork(b, this.mNewSS.getOperatorNumeric())) {
                        log("updateRoamingState: carrier config override set non roaming:" + this.mNewSS.getOperatorNumeric());
                        roaming = VDBG;
                    } else if (isRoamingInGsmNetwork(b, this.mNewSS.getOperatorNumeric())) {
                        log("updateRoamingState: carrier config override set roaming:" + this.mNewSS.getOperatorNumeric());
                        roaming = true;
                    }
                } catch (Exception e) {
                    loge("updateRoamingState: unable to access carrier config service");
                }
            } else {
                log("updateRoamingState: no carrier config service available");
            }
            this.mNewSS.setVoiceRoaming(roaming);
            this.mNewSS.setDataRoaming(roaming);
            return;
        }
        this.mNewSS.setDataRoamingFromRegistration(this.mNewSS.getDataRoaming());
        configLoader = (CarrierConfigManager) this.mPhone.getContext().getSystemService("carrier_config");
        if (configLoader != null) {
            try {
                b = configLoader.getConfigForSubId(this.mPhone.getSubId());
                String systemId = Integer.toString(this.mNewSS.getSystemId());
                if (alwaysOnHomeNetwork(b)) {
                    log("updateRoamingState: carrier config override always on home network");
                    setRoamingOff();
                } else if (isNonRoamingInGsmNetwork(b, this.mNewSS.getOperatorNumeric()) || isNonRoamingInCdmaNetwork(b, systemId)) {
                    log("updateRoamingState: carrier config override set non-roaming:" + this.mNewSS.getOperatorNumeric() + ", " + systemId);
                    setRoamingOff();
                } else if (isRoamingInGsmNetwork(b, this.mNewSS.getOperatorNumeric()) || isRoamingInCdmaNetwork(b, systemId)) {
                    log("updateRoamingState: carrier config override set roaming:" + this.mNewSS.getOperatorNumeric() + ", " + systemId);
                    setRoamingOn();
                }
            } catch (Exception e2) {
                loge("updateRoamingState: unable to access carrier config service");
            }
        } else {
            log("updateRoamingState: no carrier config service available");
        }
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean(PROP_FORCE_ROAMING, VDBG)) {
            this.mNewSS.setVoiceRoaming(true);
            this.mNewSS.setDataRoaming(true);
        }
    }

    private void setRoamingOn() {
        this.mNewSS.setVoiceRoaming(true);
        this.mNewSS.setDataRoaming(true);
        this.mNewSS.setCdmaEriIconIndex(0);
        this.mNewSS.setCdmaEriIconMode(0);
    }

    private void setRoamingOff() {
        this.mNewSS.setVoiceRoaming(VDBG);
        this.mNewSS.setDataRoaming(VDBG);
        this.mNewSS.setCdmaEriIconIndex(1);
    }

    /* Access modifiers changed, original: protected */
    /* JADX WARNING: Missing block: B:75:0x02c3, code skipped:
            if (android.text.TextUtils.equals(r5, r30.mCurPlmn) != false) goto L_0x01d9;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void updateSpnDisplay() {
        boolean showPlmn;
        String plmn;
        int subId;
        int[] subIds;
        Intent intent;
        if (this.mPhone.isPhoneTypeGsm()) {
            IccRecords iccRecords = this.mIccRecords;
            int rule = iccRecords != null ? iccRecords.getDisplayRule(this.mSS.getOperatorNumeric()) : 0;
            int combinedRegState = getCombinedRegState();
            if (combinedRegState == 1 || combinedRegState == 2) {
                showPlmn = true;
                if (this.mEmergencyOnly) {
                    plmn = Resources.getSystem().getText(17040053).toString();
                } else {
                    plmn = Resources.getSystem().getText(17040029).toString();
                }
                log("updateSpnDisplay: radio is on but out of service, set plmn='" + plmn + "'");
            } else if (combinedRegState == 0) {
                plmn = this.mSS.getOperatorAlphaLong();
                showPlmn = !TextUtils.isEmpty(plmn) ? (rule & 2) == 2 ? true : VDBG : VDBG;
            } else {
                showPlmn = true;
                plmn = Resources.getSystem().getText(17040029).toString();
                log("updateSpnDisplay: radio is off w/ showPlmn=" + true + " plmn=" + plmn);
            }
            CharSequence spn = iccRecords != null ? iccRecords.getServiceProviderName() : "";
            String dataSpn = spn;
            boolean showSpn = !TextUtils.isEmpty(spn) ? (rule & 1) == 1 ? true : VDBG : VDBG;
            if (!TextUtils.isEmpty(spn) && this.mPhone.getImsPhone() != null && this.mPhone.getImsPhone().isWifiCallingEnabled()) {
                String[] wfcSpnFormats = this.mPhone.getContext().getResources().getStringArray(17236071);
                int voiceIdx = 0;
                int dataIdx = 0;
                CarrierConfigManager configLoader = (CarrierConfigManager) this.mPhone.getContext().getSystemService("carrier_config");
                if (configLoader != null) {
                    try {
                        PersistableBundle b = configLoader.getConfigForSubId(this.mPhone.getSubId());
                        if (b != null) {
                            voiceIdx = b.getInt("wfc_spn_format_idx_int");
                            dataIdx = b.getInt("wfc_data_spn_format_idx_int");
                        }
                    } catch (Exception e) {
                        loge("updateSpnDisplay: carrier config error: " + e);
                    }
                }
                String formatVoice = wfcSpnFormats[voiceIdx];
                String formatData = wfcSpnFormats[dataIdx];
                String originalSpn = spn.trim();
                spn = String.format(formatVoice, new Object[]{originalSpn});
                dataSpn = String.format(formatData, new Object[]{originalSpn});
                showSpn = true;
                showPlmn = VDBG;
            } else if (this.mSS.getVoiceRegState() == 3 || (showPlmn && TextUtils.equals(spn, plmn))) {
                spn = null;
                showSpn = VDBG;
            }
            subId = -1;
            subIds = SubscriptionManager.getSubId(this.mPhone.getPhoneId());
            if (subIds != null && subIds.length > 0) {
                subId = subIds[0];
            }
            if (this.mSubId == subId && showPlmn == this.mCurShowPlmn && showSpn == this.mCurShowSpn && TextUtils.equals(spn, this.mCurSpn)) {
                if (TextUtils.equals(dataSpn, this.mCurDataSpn)) {
                }
            }
            log(String.format("updateSpnDisplay: changed sending intent rule=" + rule + " showPlmn='%b' plmn='%s' showSpn='%b' spn='%s' dataSpn='%s' subId='%d'", new Object[]{Boolean.valueOf(showPlmn), plmn, Boolean.valueOf(showSpn), spn, dataSpn, Integer.valueOf(subId)}));
            intent = new Intent("android.provider.Telephony.SPN_STRINGS_UPDATED");
            intent.addFlags(536870912);
            intent.putExtra("showSpn", showSpn);
            intent.putExtra("spn", spn);
            intent.putExtra("spnData", dataSpn);
            intent.putExtra("showPlmn", showPlmn);
            intent.putExtra(CellBroadcasts.PLMN, plmn);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
            this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
            if (!this.mSubscriptionController.setPlmnSpn(this.mPhone.getPhoneId(), showPlmn, plmn, showSpn, spn)) {
                this.mSpnUpdatePending = true;
            }
            this.mSubId = subId;
            this.mCurShowSpn = showSpn;
            this.mCurShowPlmn = showPlmn;
            this.mCurSpn = spn;
            this.mCurDataSpn = dataSpn;
            this.mCurPlmn = plmn;
            return;
        }
        plmn = this.mSS.getOperatorAlphaLong();
        showPlmn = plmn != null ? true : VDBG;
        subId = -1;
        subIds = SubscriptionManager.getSubId(this.mPhone.getPhoneId());
        if (subIds != null && subIds.length > 0) {
            subId = subIds[0];
        }
        if (getCombinedRegState() == 1) {
            plmn = Resources.getSystem().getText(17040029).toString();
            log("updateSpnDisplay: radio is on but out of service, set plmn='" + plmn + "'");
        }
        if (!(this.mSubId == subId && TextUtils.equals(plmn, this.mCurPlmn))) {
            log(String.format("updateSpnDisplay: changed sending intent showPlmn='%b' plmn='%s' subId='%d'", new Object[]{Boolean.valueOf(showPlmn), plmn, Integer.valueOf(subId)}));
            intent = new Intent("android.provider.Telephony.SPN_STRINGS_UPDATED");
            intent.addFlags(536870912);
            intent.putExtra("showSpn", VDBG);
            intent.putExtra("spn", "");
            intent.putExtra("showPlmn", showPlmn);
            intent.putExtra(CellBroadcasts.PLMN, plmn);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
            this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
            if (!this.mSubscriptionController.setPlmnSpn(this.mPhone.getPhoneId(), showPlmn, plmn, VDBG, "")) {
                this.mSpnUpdatePending = true;
            }
        }
        this.mSubId = subId;
        this.mCurShowSpn = VDBG;
        this.mCurShowPlmn = showPlmn;
        this.mCurSpn = "";
        this.mCurPlmn = plmn;
    }

    /* Access modifiers changed, original: protected */
    public void setPowerStateToDesired() {
        log("mDeviceShuttingDown=" + this.mDeviceShuttingDown + ", mDesiredPowerState=" + this.mDesiredPowerState + ", getRadioState=" + this.mCi.getRadioState() + ", mPowerOffDelayNeed=" + this.mPowerOffDelayNeed + ", mAlarmSwitch=" + this.mAlarmSwitch);
        if (this.mPhone.isPhoneTypeGsm() && this.mAlarmSwitch) {
            log("mAlarmSwitch == true");
            ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.mRadioOffIntent);
            this.mAlarmSwitch = VDBG;
        }
        if (this.mDesiredPowerState && this.mCi.getRadioState() == RadioState.RADIO_OFF) {
            if (SystemProperties.getBoolean(DUN_PROFILE_INIT, VDBG)) {
                this.mPhone.resetDunProfiles();
                SystemProperties.set(DUN_PROFILE_INIT, "false");
            }
            this.mCi.setRadioPower(true, null);
        } else if (this.mDesiredPowerState || !this.mCi.getRadioState().isOn()) {
            if (this.mDeviceShuttingDown && this.mCi.getRadioState().isAvailable()) {
                this.mCi.requestShutdown(null);
            }
        } else if (!this.mPhone.isPhoneTypeGsm() || !this.mPowerOffDelayNeed) {
            powerOffRadioSafely(this.mPhone.mDcTracker);
        } else if (!this.mImsRegistrationOnOff || this.mAlarmSwitch) {
            powerOffRadioSafely(this.mPhone.mDcTracker);
        } else {
            log("mImsRegistrationOnOff == true");
            Context context = this.mPhone.getContext();
            AlarmManager am = (AlarmManager) context.getSystemService("alarm");
            this.mRadioOffIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_RADIO_OFF), 0);
            this.mAlarmSwitch = true;
            log("Alarm setting");
            am.set(2, SystemClock.elapsedRealtime() + 3000, this.mRadioOffIntent);
        }
    }

    /* Access modifiers changed, original: protected */
    public void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            UiccCardApplication newUiccApplication = getUiccCardApplication();
            if (this.mUiccApplcation != newUiccApplication) {
                if (this.mUiccApplcation != null) {
                    log("Removing stale icc objects.");
                    this.mUiccApplcation.unregisterForReady(this);
                    if (this.mIccRecords != null) {
                        this.mIccRecords.unregisterForRecordsLoaded(this);
                    }
                    this.mIccRecords = null;
                    this.mUiccApplcation = null;
                }
                if (newUiccApplication != null) {
                    log("New card found");
                    this.mUiccApplcation = newUiccApplication;
                    this.mIccRecords = this.mUiccApplcation.getIccRecords();
                    if (this.mPhone.isPhoneTypeGsm()) {
                        this.mUiccApplcation.registerForReady(this, 17, null);
                        if (this.mIccRecords != null) {
                            this.mIccRecords.registerForRecordsLoaded(this, 16, null);
                        }
                    } else if (this.mIsSubscriptionFromRuim) {
                        this.mUiccApplcation.registerForReady(this, 26, null);
                        if (this.mIccRecords != null) {
                            this.mIccRecords.registerForRecordsLoaded(this, 27, null);
                        }
                    }
                }
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void log(String s) {
        Rlog.d(LOG_TAG, s);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String s) {
        Rlog.e(LOG_TAG, s);
    }

    public int getCurrentDataConnectionState() {
        return this.mSS.getDataRegState();
    }

    public boolean isConcurrentVoiceAndDataAllowed() {
        boolean z = true;
        if (this.mPhone.isPhoneTypeGsm()) {
            if (this.mSS.getRilVoiceRadioTechnology() != 16) {
                return true;
            }
            if (this.mSS.getCssIndicator() != 1) {
                z = VDBG;
            }
            return z;
        } else if (this.mPhone.isPhoneTypeCdma()) {
            return VDBG;
        } else {
            if (this.mSS.getCssIndicator() != 1) {
                z = VDBG;
            }
            return z;
        }
    }

    public void setImsRegistrationState(boolean registered) {
        log("ImsRegistrationState - registered : " + registered);
        if (this.mImsRegistrationOnOff && !registered && this.mAlarmSwitch) {
            this.mImsRegistrationOnOff = registered;
            ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.mRadioOffIntent);
            this.mAlarmSwitch = VDBG;
            sendMessage(obtainMessage(EVENT_CHANGE_IMS_STATE));
            return;
        }
        this.mImsRegistrationOnOff = registered;
    }

    public void onImsCapabilityChanged() {
        if (this.mPhone.isPhoneTypeGsm()) {
            sendMessage(obtainMessage(EVENT_IMS_CAPABILITY_CHANGED));
        }
    }

    private void updateRilImsRadioTechnology() {
        int imsRadioTechnology;
        if (this.mPhone.getImsPhone() != null) {
            imsRadioTechnology = this.mPhone.getImsPhone().getServiceState().getRilImsRadioTechnology();
        } else {
            imsRadioTechnology = 0;
        }
        if (imsRadioTechnology != this.mSS.getRilImsRadioTechnology()) {
            Rlog.i(LOG_TAG, "updateRilImsRadioTechnology : Old ims RAT: " + this.mSS.getRilImsRadioTechnology() + " new ims RAT: " + imsRadioTechnology);
            this.mSS.setRilImsRadioTechnology(imsRadioTechnology);
            this.mPhone.notifyServiceStateChanged(this.mSS);
        }
    }

    public void pollState() {
        pollState(VDBG);
    }

    private void modemTriggeredPollState() {
        pollState(true);
    }

    public void pollState(boolean modemTriggered) {
        this.mPollingContext = new int[1];
        this.mPollingContext[0] = 0;
        switch (-getcom-android-internal-telephony-CommandsInterface$RadioStateSwitchesValues()[this.mCi.getRadioState().ordinal()]) {
            case 1:
                this.mNewSS.setStateOff();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = VDBG;
                this.mNitzUpdatedTime = VDBG;
                if (!(modemTriggered || 18 == this.mSS.getRilDataRadioTechnology())) {
                    pollStateDone();
                    return;
                }
            case 2:
                this.mNewSS.setStateOutOfService();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = VDBG;
                this.mNitzUpdatedTime = VDBG;
                pollStateDone();
                return;
        }
        int[] iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getOperator(obtainMessage(6, this.mPollingContext));
        iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getDataRegistrationState(obtainMessage(5, this.mPollingContext));
        iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getVoiceRegistrationState(obtainMessage(4, this.mPollingContext));
        if (this.mPhone.isPhoneTypeGsm()) {
            iArr = this.mPollingContext;
            iArr[0] = iArr[0] + 1;
            this.mCi.getNetworkSelectionMode(obtainMessage(14, this.mPollingContext));
        }
    }

    private void pollStateDone() {
        if (this.mPhone.isPhoneTypeGsm()) {
            pollStateDoneGsm();
        } else if (this.mPhone.isPhoneTypeCdma()) {
            pollStateDoneCdma();
        } else {
            pollStateDoneCdmaLte();
        }
    }

    private void pollStateDoneGsm() {
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean(PROP_FORCE_ROAMING, VDBG)) {
            this.mNewSS.setVoiceRoaming(true);
            this.mNewSS.setDataRoaming(true);
        }
        useDataRegStateForDataOnlyDevices();
        resetServiceStateInIwlanMode();
        log("Poll ServiceState done:  oldSS=[" + this.mSS + "] newSS=[" + this.mNewSS + "]" + " oldMaxDataCalls=" + this.mMaxDataCalls + " mNewMaxDataCalls=" + this.mNewMaxDataCalls + " oldReasonDataDenied=" + this.mReasonDataDenied + " mNewReasonDataDenied=" + this.mNewReasonDataDenied);
        boolean hasRegistered = this.mSS.getVoiceRegState() != 0 ? this.mNewSS.getVoiceRegState() == 0 ? true : VDBG : VDBG;
        if (this.mSS.getVoiceRegState() == 0) {
            if (this.mNewSS.getVoiceRegState() != 0) {
            }
        }
        boolean hasGprsAttached = this.mSS.getDataRegState() != 0 ? this.mNewSS.getDataRegState() == 0 ? true : VDBG : VDBG;
        boolean hasGprsDetached = this.mSS.getDataRegState() == 0 ? this.mNewSS.getDataRegState() != 0 ? true : VDBG : VDBG;
        boolean hasDataRegStateChanged = this.mSS.getDataRegState() != this.mNewSS.getDataRegState() ? true : VDBG;
        boolean hasVoiceRegStateChanged = this.mSS.getVoiceRegState() != this.mNewSS.getVoiceRegState() ? true : VDBG;
        boolean hasRilVoiceRadioTechnologyChanged = this.mSS.getRilVoiceRadioTechnology() != this.mNewSS.getRilVoiceRadioTechnology() ? true : VDBG;
        boolean hasRilDataRadioTechnologyChanged = this.mSS.getRilDataRadioTechnology() != this.mNewSS.getRilDataRadioTechnology() ? true : VDBG;
        boolean hasChanged = this.mNewSS.equals(this.mSS) ? VDBG : true;
        boolean hasVoiceRoamingOn = !this.mSS.getVoiceRoaming() ? this.mNewSS.getVoiceRoaming() : VDBG;
        boolean hasVoiceRoamingOff = (!this.mSS.getVoiceRoaming() || this.mNewSS.getVoiceRoaming()) ? VDBG : true;
        boolean hasDataRoamingOn = !this.mSS.getDataRoaming() ? this.mNewSS.getDataRoaming() : VDBG;
        boolean hasDataRoamingOff = (!this.mSS.getDataRoaming() || this.mNewSS.getDataRoaming()) ? VDBG : true;
        boolean hasLocationChanged = this.mNewCellLoc.equals(this.mCellLoc) ? VDBG : true;
        boolean hasCssIndicatorChanged = this.mSS.getCssIndicator() != this.mNewSS.getCssIndicator() ? true : VDBG;
        TelephonyManager tm = (TelephonyManager) this.mPhone.getContext().getSystemService("phone");
        if (TelBrand.IS_DCM && !this.mIsSmsMemRptSent && (hasRegistered || hasGprsAttached)) {
            log("Called setSendSmsMemoryFull while voice:" + hasRegistered + " or data:" + hasGprsAttached);
            if (SystemProperties.get("vold.decrypt", "0").equals("trigger_restart_min_framework")) {
                setSendSmsMemoryFull();
            } else {
                sendMessageDelayed(obtainMessage(60), 15000);
            }
        }
        if (hasVoiceRegStateChanged || hasDataRegStateChanged) {
            EventLog.writeEvent(EventLogTags.GSM_SERVICE_STATE_CHANGE, new Object[]{Integer.valueOf(this.mSS.getVoiceRegState()), Integer.valueOf(this.mSS.getDataRegState()), Integer.valueOf(this.mNewSS.getVoiceRegState()), Integer.valueOf(this.mNewSS.getDataRegState())});
        }
        if (hasRilVoiceRadioTechnologyChanged) {
            int cid = -1;
            GsmCellLocation loc = (GsmCellLocation) this.mNewCellLoc;
            if (loc != null) {
                cid = loc.getCid();
            }
            EventLog.writeEvent(EventLogTags.GSM_RAT_SWITCHED_NEW, new Object[]{Integer.valueOf(cid), Integer.valueOf(this.mSS.getRilVoiceRadioTechnology()), Integer.valueOf(this.mNewSS.getRilVoiceRadioTechnology())});
            log("RAT switched " + ServiceState.rilRadioTechnologyToString(this.mSS.getRilVoiceRadioTechnology()) + " -> " + ServiceState.rilRadioTechnologyToString(this.mNewSS.getRilVoiceRadioTechnology()) + " at cell " + cid);
        }
        this.mNewSS.setRilImsRadioTechnology(this.mSS.getRilImsRadioTechnology());
        ServiceState tss = this.mSS;
        this.mSS = this.mNewSS;
        this.mNewSS = tss;
        this.mNewSS.setStateOutOfService();
        CellLocation tcl = (GsmCellLocation) this.mCellLoc;
        this.mCellLoc = this.mNewCellLoc;
        this.mNewCellLoc = tcl;
        this.mReasonDataDenied = this.mNewReasonDataDenied;
        this.mMaxDataCalls = this.mNewMaxDataCalls;
        if (hasRilVoiceRadioTechnologyChanged) {
            updatePhoneObject();
        }
        if (hasRilDataRadioTechnologyChanged) {
            tm.setDataNetworkTypeForPhone(this.mPhone.getPhoneId(), this.mSS.getRilDataRadioTechnology());
            if (18 == this.mSS.getRilDataRadioTechnology()) {
                log("pollStateDone: IWLAN enabled");
            }
        }
        if (hasRegistered) {
            this.mNetworkAttachedRegistrants.notifyRegistrants();
            log("pollStateDone: registering current mNitzUpdatedTime=" + this.mNitzUpdatedTime + " changing to false");
            this.mNitzUpdatedTime = VDBG;
        }
        if (hasChanged) {
            updateSpnDisplay();
            tm.setNetworkOperatorNameForPhone(this.mPhone.getPhoneId(), this.mSS.getOperatorAlphaLong());
            String prevOperatorNumeric = tm.getNetworkOperatorForPhone(this.mPhone.getPhoneId());
            String operatorNumeric = this.mSS.getOperatorNumeric();
            tm.setNetworkOperatorNumericForPhone(this.mPhone.getPhoneId(), operatorNumeric);
            updateCarrierMccMncConfiguration(operatorNumeric, prevOperatorNumeric, this.mPhone.getContext());
            if (operatorNumeric == null) {
                log("operatorNumeric is null");
                tm.setNetworkCountryIsoForPhone(this.mPhone.getPhoneId(), "");
                this.mGotCountryCode = VDBG;
                this.mNitzUpdatedTime = VDBG;
            } else {
                String iso = "";
                String mcc = "";
                try {
                    mcc = operatorNumeric.substring(0, 3);
                    iso = MccTable.countryCodeForMcc(Integer.parseInt(mcc));
                } catch (NumberFormatException ex) {
                    loge("pollStateDone: countryCodeForMcc error" + ex);
                } catch (StringIndexOutOfBoundsException ex2) {
                    loge("pollStateDone: countryCodeForMcc error" + ex2);
                }
                tm.setNetworkCountryIsoForPhone(this.mPhone.getPhoneId(), iso);
                this.mGotCountryCode = true;
                if (!(this.mNitzUpdatedTime || mcc.equals(INVALID_MCC) || TextUtils.isEmpty(iso) || !getAutoTimeZone())) {
                    boolean testOneUniqueOffsetPath = SystemProperties.getBoolean("telephony.test.ignore.nitz", VDBG) ? (SystemClock.uptimeMillis() & 1) == 0 ? true : VDBG : VDBG;
                    ArrayList<TimeZone> uniqueZones = TimeUtils.getTimeZonesWithUniqueOffsets(iso);
                    if (uniqueZones.size() == 1 || testOneUniqueOffsetPath) {
                        TimeZone zone = (TimeZone) uniqueZones.get(0);
                        log("pollStateDone: no nitz but one TZ for iso-cc=" + iso + " with zone.getID=" + zone.getID() + " testOneUniqueOffsetPath=" + testOneUniqueOffsetPath);
                        setAndBroadcastNetworkSetTimeZone(zone.getID());
                    } else {
                        log("pollStateDone: there are " + uniqueZones.size() + " unique offsets for iso-cc='" + iso + " testOneUniqueOffsetPath=" + testOneUniqueOffsetPath + "', do nothing");
                    }
                }
                if (shouldFixTimeZoneNow(this.mPhone, operatorNumeric, prevOperatorNumeric, this.mNeedFixZoneAfterNitz)) {
                    fixTimeZone(iso);
                }
            }
            tm.setNetworkRoamingForPhone(this.mPhone.getPhoneId(), this.mSS.getVoiceRoaming());
            setRoamingType(this.mSS);
            log("Broadcasting ServiceState : " + this.mSS);
            this.mPhone.notifyServiceStateChanged(this.mSS);
            this.mEventLog.writeServiceStateChanged(this.mSS);
        }
        if (hasGprsAttached) {
            this.mAttachedRegistrants.notifyRegistrants();
        }
        if (hasGprsDetached) {
            this.mDetachedRegistrants.notifyRegistrants();
        }
        if (hasDataRegStateChanged || hasRilDataRadioTechnologyChanged) {
            notifyDataRegStateRilRadioTechnologyChanged();
            if (18 == this.mSS.getRilDataRadioTechnology()) {
                this.mPhone.notifyDataConnection(PhoneInternalInterface.REASON_IWLAN_AVAILABLE);
            } else {
                this.mPhone.notifyDataConnection(null);
            }
        }
        if (hasVoiceRoamingOn) {
            this.mVoiceRoamingOnRegistrants.notifyRegistrants();
        }
        if (hasVoiceRoamingOff) {
            this.mVoiceRoamingOffRegistrants.notifyRegistrants();
        }
        if (hasDataRoamingOn) {
            this.mDataRoamingOnRegistrants.notifyRegistrants();
        }
        if (hasDataRoamingOff) {
            this.mDataRoamingOffRegistrants.notifyRegistrants();
        }
        if (hasLocationChanged) {
            this.mPhone.notifyLocationChanged();
        }
        if (hasCssIndicatorChanged) {
            this.mPhone.notifyDataConnection(PhoneInternalInterface.REASON_CSS_INDICATOR_CHANGED);
        }
        if (isGprsConsistent(this.mSS.getDataRegState(), this.mSS.getVoiceRegState())) {
            this.mReportedGprsNoReg = VDBG;
        } else if (!this.mStartedGprsRegCheck && !this.mReportedGprsNoReg) {
            this.mStartedGprsRegCheck = true;
            sendMessageDelayed(obtainMessage(22), (long) Global.getInt(this.mPhone.getContext().getContentResolver(), "gprs_register_check_period_ms", DEFAULT_GPRS_CHECK_PERIOD_MILLIS));
        }
    }

    /* Access modifiers changed, original: protected */
    public void pollStateDoneCdma() {
        updateRoamingState();
        useDataRegStateForDataOnlyDevices();
        resetServiceStateInIwlanMode();
        log("pollStateDone: cdma oldSS=[" + this.mSS + "] newSS=[" + this.mNewSS + "]");
        boolean hasRegistered = this.mSS.getVoiceRegState() != 0 ? this.mNewSS.getVoiceRegState() == 0 ? true : VDBG : VDBG;
        boolean hasCdmaDataConnectionAttached = this.mSS.getDataRegState() != 0 ? this.mNewSS.getDataRegState() == 0 ? true : VDBG : VDBG;
        boolean hasCdmaDataConnectionDetached = this.mSS.getDataRegState() == 0 ? this.mNewSS.getDataRegState() != 0 ? true : VDBG : VDBG;
        boolean hasCdmaDataConnectionChanged = this.mSS.getDataRegState() != this.mNewSS.getDataRegState() ? true : VDBG;
        boolean hasRilVoiceRadioTechnologyChanged = this.mSS.getRilVoiceRadioTechnology() != this.mNewSS.getRilVoiceRadioTechnology() ? true : VDBG;
        boolean hasRilDataRadioTechnologyChanged = this.mSS.getRilDataRadioTechnology() != this.mNewSS.getRilDataRadioTechnology() ? true : VDBG;
        boolean hasChanged = this.mNewSS.equals(this.mSS) ? VDBG : true;
        boolean hasVoiceRoamingOn = !this.mSS.getVoiceRoaming() ? this.mNewSS.getVoiceRoaming() : VDBG;
        boolean hasVoiceRoamingOff = (!this.mSS.getVoiceRoaming() || this.mNewSS.getVoiceRoaming()) ? VDBG : true;
        boolean hasDataRoamingOn = !this.mSS.getDataRoaming() ? this.mNewSS.getDataRoaming() : VDBG;
        boolean hasDataRoamingOff = (!this.mSS.getDataRoaming() || this.mNewSS.getDataRoaming()) ? VDBG : true;
        boolean hasLocationChanged = this.mNewCellLoc.equals(this.mCellLoc) ? VDBG : true;
        TelephonyManager tm = (TelephonyManager) this.mPhone.getContext().getSystemService("phone");
        if (!(this.mSS.getVoiceRegState() == this.mNewSS.getVoiceRegState() && this.mSS.getDataRegState() == this.mNewSS.getDataRegState())) {
            EventLog.writeEvent(EventLogTags.CDMA_SERVICE_STATE_CHANGE, new Object[]{Integer.valueOf(this.mSS.getVoiceRegState()), Integer.valueOf(this.mSS.getDataRegState()), Integer.valueOf(this.mNewSS.getVoiceRegState()), Integer.valueOf(this.mNewSS.getDataRegState())});
        }
        this.mNewSS.setRilImsRadioTechnology(this.mSS.getRilImsRadioTechnology());
        ServiceState tss = this.mSS;
        this.mSS = this.mNewSS;
        this.mNewSS = tss;
        this.mNewSS.setStateOutOfService();
        CellLocation tcl = (CdmaCellLocation) this.mCellLoc;
        this.mCellLoc = this.mNewCellLoc;
        this.mNewCellLoc = tcl;
        if (hasRilVoiceRadioTechnologyChanged) {
            updatePhoneObject();
        }
        if (hasRilDataRadioTechnologyChanged) {
            tm.setDataNetworkTypeForPhone(this.mPhone.getPhoneId(), this.mSS.getRilDataRadioTechnology());
            if (18 == this.mSS.getRilDataRadioTechnology()) {
                log("pollStateDone: IWLAN enabled");
            }
        }
        if (hasRegistered) {
            this.mNetworkAttachedRegistrants.notifyRegistrants();
        }
        if (hasChanged) {
            if (this.mCi.getRadioState().isOn() && !this.mIsSubscriptionFromRuim) {
                String eriText;
                if (this.mSS.getVoiceRegState() == 0) {
                    eriText = this.mPhone.getCdmaEriText();
                } else {
                    eriText = this.mPhone.getContext().getText(17039606).toString();
                }
                this.mSS.setOperatorAlphaLong(eriText);
            }
            tm.setNetworkOperatorNameForPhone(this.mPhone.getPhoneId(), this.mSS.getOperatorAlphaLong());
            String prevOperatorNumeric = tm.getNetworkOperatorForPhone(this.mPhone.getPhoneId());
            String operatorNumeric = this.mSS.getOperatorNumeric();
            if (isInvalidOperatorNumeric(operatorNumeric)) {
                operatorNumeric = fixUnknownMcc(operatorNumeric, this.mSS.getSystemId());
            }
            tm.setNetworkOperatorNumericForPhone(this.mPhone.getPhoneId(), operatorNumeric);
            updateCarrierMccMncConfiguration(operatorNumeric, prevOperatorNumeric, this.mPhone.getContext());
            if (isInvalidOperatorNumeric(operatorNumeric)) {
                log("operatorNumeric " + operatorNumeric + "is invalid");
                tm.setNetworkCountryIsoForPhone(this.mPhone.getPhoneId(), "");
                this.mGotCountryCode = VDBG;
            } else {
                String isoCountryCode = "";
                String mcc = operatorNumeric.substring(0, 3);
                try {
                    isoCountryCode = MccTable.countryCodeForMcc(Integer.parseInt(operatorNumeric.substring(0, 3)));
                } catch (NumberFormatException ex) {
                    loge("pollStateDone: countryCodeForMcc error" + ex);
                } catch (StringIndexOutOfBoundsException ex2) {
                    loge("pollStateDone: countryCodeForMcc error" + ex2);
                }
                tm.setNetworkCountryIsoForPhone(this.mPhone.getPhoneId(), isoCountryCode);
                this.mGotCountryCode = true;
                setOperatorIdd(operatorNumeric);
                if (shouldFixTimeZoneNow(this.mPhone, operatorNumeric, prevOperatorNumeric, this.mNeedFixZoneAfterNitz)) {
                    fixTimeZone(isoCountryCode);
                }
            }
            tm.setNetworkRoamingForPhone(this.mPhone.getPhoneId(), !this.mSS.getVoiceRoaming() ? this.mSS.getDataRoaming() : true);
            updateSpnDisplay();
            setRoamingType(this.mSS);
            log("Broadcasting ServiceState : " + this.mSS);
            this.mPhone.notifyServiceStateChanged(this.mSS);
        }
        if (hasCdmaDataConnectionAttached) {
            this.mAttachedRegistrants.notifyRegistrants();
        }
        if (hasCdmaDataConnectionDetached) {
            this.mDetachedRegistrants.notifyRegistrants();
        }
        if (hasCdmaDataConnectionChanged || hasRilDataRadioTechnologyChanged) {
            notifyDataRegStateRilRadioTechnologyChanged();
            if (18 == this.mSS.getRilDataRadioTechnology()) {
                this.mPhone.notifyDataConnection(PhoneInternalInterface.REASON_IWLAN_AVAILABLE);
            } else {
                this.mPhone.notifyDataConnection(null);
            }
        }
        if (hasVoiceRoamingOn) {
            this.mVoiceRoamingOnRegistrants.notifyRegistrants();
        }
        if (hasVoiceRoamingOff) {
            this.mVoiceRoamingOffRegistrants.notifyRegistrants();
        }
        if (hasDataRoamingOn) {
            this.mDataRoamingOnRegistrants.notifyRegistrants();
        }
        if (hasDataRoamingOff) {
            this.mDataRoamingOffRegistrants.notifyRegistrants();
        }
        if (hasLocationChanged) {
            this.mPhone.notifyLocationChanged();
        }
    }

    /* Access modifiers changed, original: protected */
    public void pollStateDoneCdmaLte() {
        boolean has4gHandoff;
        updateRoamingState();
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean(PROP_FORCE_ROAMING, VDBG)) {
            this.mNewSS.setVoiceRoaming(true);
            this.mNewSS.setDataRoaming(true);
        }
        useDataRegStateForDataOnlyDevices();
        resetServiceStateInIwlanMode();
        log("pollStateDone: lte 1 ss=[" + this.mSS + "] newSS=[" + this.mNewSS + "]");
        boolean hasRegistered = this.mSS.getVoiceRegState() != 0 ? this.mNewSS.getVoiceRegState() == 0 ? true : VDBG : VDBG;
        boolean hasDeregistered = this.mSS.getVoiceRegState() == 0 ? this.mNewSS.getVoiceRegState() != 0 ? true : VDBG : VDBG;
        boolean hasCdmaDataConnectionAttached = this.mSS.getDataRegState() != 0 ? this.mNewSS.getDataRegState() == 0 ? true : VDBG : VDBG;
        boolean hasCdmaDataConnectionDetached = this.mSS.getDataRegState() == 0 ? this.mNewSS.getDataRegState() != 0 ? true : VDBG : VDBG;
        boolean hasCdmaDataConnectionChanged = this.mSS.getDataRegState() != this.mNewSS.getDataRegState() ? true : VDBG;
        boolean hasVoiceRadioTechnologyChanged = this.mSS.getRilVoiceRadioTechnology() != this.mNewSS.getRilVoiceRadioTechnology() ? true : VDBG;
        boolean hasDataRadioTechnologyChanged = this.mSS.getRilDataRadioTechnology() != this.mNewSS.getRilDataRadioTechnology() ? true : VDBG;
        boolean hasChanged = this.mNewSS.equals(this.mSS) ? VDBG : true;
        boolean hasVoiceRoamingOn = !this.mSS.getVoiceRoaming() ? this.mNewSS.getVoiceRoaming() : VDBG;
        boolean hasVoiceRoamingOff = (!this.mSS.getVoiceRoaming() || this.mNewSS.getVoiceRoaming()) ? VDBG : true;
        boolean hasDataRoamingOn = !this.mSS.getDataRoaming() ? this.mNewSS.getDataRoaming() : VDBG;
        boolean hasDataRoamingOff = (!this.mSS.getDataRoaming() || this.mNewSS.getDataRoaming()) ? VDBG : true;
        boolean hasLocationChanged = this.mNewCellLoc.equals(this.mCellLoc) ? VDBG : true;
        if (this.mNewSS.getDataRegState() != 0) {
            has4gHandoff = VDBG;
        } else if (isRatLte(this.mSS.getRilDataRadioTechnology()) && this.mNewSS.getRilDataRadioTechnology() == 13) {
            has4gHandoff = true;
        } else if (this.mSS.getRilDataRadioTechnology() == 13) {
            has4gHandoff = isRatLte(this.mNewSS.getRilDataRadioTechnology());
        } else {
            has4gHandoff = VDBG;
        }
        boolean hasMultiApnSupport = (isRatLte(this.mNewSS.getRilDataRadioTechnology()) || this.mNewSS.getRilDataRadioTechnology() == 13) ? !isRatLte(this.mSS.getRilDataRadioTechnology()) ? this.mSS.getRilDataRadioTechnology() != 13 ? true : VDBG : VDBG : VDBG;
        boolean hasLostMultiApnSupport = this.mNewSS.getRilDataRadioTechnology() >= 4 ? this.mNewSS.getRilDataRadioTechnology() <= 8 ? true : VDBG : VDBG;
        TelephonyManager tm = (TelephonyManager) this.mPhone.getContext().getSystemService("phone");
        log("pollStateDone: hasRegistered=" + hasRegistered + " hasDeegistered=" + hasDeregistered + " hasCdmaDataConnectionAttached=" + hasCdmaDataConnectionAttached + " hasCdmaDataConnectionDetached=" + hasCdmaDataConnectionDetached + " hasCdmaDataConnectionChanged=" + hasCdmaDataConnectionChanged + " hasVoiceRadioTechnologyChanged= " + hasVoiceRadioTechnologyChanged + " hasDataRadioTechnologyChanged=" + hasDataRadioTechnologyChanged + " hasChanged=" + hasChanged + " hasVoiceRoamingOn=" + hasVoiceRoamingOn + " hasVoiceRoamingOff=" + hasVoiceRoamingOff + " hasDataRoamingOn=" + hasDataRoamingOn + " hasDataRoamingOff=" + hasDataRoamingOff + " hasLocationChanged=" + hasLocationChanged + " has4gHandoff = " + has4gHandoff + " hasMultiApnSupport=" + hasMultiApnSupport + " hasLostMultiApnSupport=" + hasLostMultiApnSupport);
        if (!(this.mSS.getVoiceRegState() == this.mNewSS.getVoiceRegState() && this.mSS.getDataRegState() == this.mNewSS.getDataRegState())) {
            EventLog.writeEvent(EventLogTags.CDMA_SERVICE_STATE_CHANGE, new Object[]{Integer.valueOf(this.mSS.getVoiceRegState()), Integer.valueOf(this.mSS.getDataRegState()), Integer.valueOf(this.mNewSS.getVoiceRegState()), Integer.valueOf(this.mNewSS.getDataRegState())});
        }
        ServiceState tss = this.mSS;
        this.mSS = this.mNewSS;
        this.mNewSS = tss;
        this.mNewSS.setStateOutOfService();
        CellLocation tcl = (CdmaCellLocation) this.mCellLoc;
        this.mCellLoc = this.mNewCellLoc;
        this.mNewCellLoc = tcl;
        this.mNewSS.setStateOutOfService();
        if (hasVoiceRadioTechnologyChanged) {
            updatePhoneObject();
        }
        if (hasDataRadioTechnologyChanged) {
            tm.setDataNetworkTypeForPhone(this.mPhone.getPhoneId(), this.mSS.getRilDataRadioTechnology());
            if (18 == this.mSS.getRilDataRadioTechnology()) {
                log("pollStateDone: IWLAN enabled");
            }
        }
        if (hasRegistered) {
            this.mNetworkAttachedRegistrants.notifyRegistrants();
        }
        if (hasChanged) {
            boolean hasBrandOverride = this.mUiccController.getUiccCard(getPhoneId()) == null ? VDBG : this.mUiccController.getUiccCard(getPhoneId()).getOperatorBrandOverride() != null ? true : VDBG;
            if (!hasBrandOverride && this.mCi.getRadioState().isOn() && this.mPhone.isEriFileLoaded() && ((!isRatLte(this.mSS.getRilVoiceRadioTechnology()) || this.mPhone.getContext().getResources().getBoolean(17957026)) && !this.mIsSubscriptionFromRuim)) {
                String eriText = this.mSS.getOperatorAlphaLong();
                if (this.mSS.getVoiceRegState() == 0) {
                    eriText = this.mPhone.getCdmaEriText();
                } else if (this.mSS.getVoiceRegState() == 3) {
                    eriText = this.mIccRecords != null ? this.mIccRecords.getServiceProviderName() : null;
                    if (TextUtils.isEmpty(eriText)) {
                        eriText = SystemProperties.get("ro.cdma.home.operator.alpha");
                    }
                } else if (this.mSS.getDataRegState() != 0) {
                    eriText = this.mPhone.getContext().getText(17039606).toString();
                }
                this.mSS.setOperatorAlphaLong(eriText);
            }
            if (this.mUiccApplcation != null && this.mUiccApplcation.getState() == AppState.APPSTATE_READY && this.mIccRecords != null && ((this.mSS.getVoiceRegState() == 0 || this.mSS.getDataRegState() == 0) && !isRatLte(this.mSS.getRilVoiceRadioTechnology()))) {
                boolean showSpn = ((RuimRecords) this.mIccRecords).getCsimSpnDisplayCondition();
                int iconIndex = this.mSS.getCdmaEriIconIndex();
                if (showSpn && iconIndex == 1) {
                    if (isInHomeSidNid(this.mSS.getSystemId(), this.mSS.getNetworkId()) && this.mIccRecords != null) {
                        this.mSS.setOperatorAlphaLong(this.mIccRecords.getServiceProviderName());
                    }
                }
            }
            tm.setNetworkOperatorNameForPhone(this.mPhone.getPhoneId(), this.mSS.getOperatorAlphaLong());
            String prevOperatorNumeric = tm.getNetworkOperatorForPhone(this.mPhone.getPhoneId());
            String operatorNumeric = this.mSS.getOperatorNumeric();
            if (isInvalidOperatorNumeric(operatorNumeric)) {
                operatorNumeric = fixUnknownMcc(operatorNumeric, this.mSS.getSystemId());
            }
            tm.setNetworkOperatorNumericForPhone(this.mPhone.getPhoneId(), operatorNumeric);
            updateCarrierMccMncConfiguration(operatorNumeric, prevOperatorNumeric, this.mPhone.getContext());
            if (isInvalidOperatorNumeric(operatorNumeric)) {
                log("operatorNumeric is null");
                tm.setNetworkCountryIsoForPhone(this.mPhone.getPhoneId(), "");
                this.mGotCountryCode = VDBG;
            } else {
                String isoCountryCode = "";
                String mcc = operatorNumeric.substring(0, 3);
                try {
                    isoCountryCode = MccTable.countryCodeForMcc(Integer.parseInt(operatorNumeric.substring(0, 3)));
                } catch (NumberFormatException ex) {
                    loge("countryCodeForMcc error" + ex);
                } catch (StringIndexOutOfBoundsException ex2) {
                    loge("countryCodeForMcc error" + ex2);
                }
                tm.setNetworkCountryIsoForPhone(this.mPhone.getPhoneId(), isoCountryCode);
                this.mGotCountryCode = true;
                setOperatorIdd(operatorNumeric);
                if (shouldFixTimeZoneNow(this.mPhone, operatorNumeric, prevOperatorNumeric, this.mNeedFixZoneAfterNitz)) {
                    fixTimeZone(isoCountryCode);
                }
            }
            tm.setNetworkRoamingForPhone(this.mPhone.getPhoneId(), !this.mSS.getVoiceRoaming() ? this.mSS.getDataRoaming() : true);
            updateSpnDisplay();
            setRoamingType(this.mSS);
            log("Broadcasting ServiceState : " + this.mSS);
            this.mPhone.notifyServiceStateChanged(this.mSS);
        }
        if (hasCdmaDataConnectionAttached || has4gHandoff) {
            this.mAttachedRegistrants.notifyRegistrants();
        }
        if (hasCdmaDataConnectionDetached) {
            this.mDetachedRegistrants.notifyRegistrants();
        }
        if (hasCdmaDataConnectionChanged || hasDataRadioTechnologyChanged) {
            notifyDataRegStateRilRadioTechnologyChanged();
            if (18 == this.mSS.getRilDataRadioTechnology()) {
                this.mPhone.notifyDataConnection(PhoneInternalInterface.REASON_IWLAN_AVAILABLE);
            } else {
                this.mPhone.notifyDataConnection(null);
            }
        }
        if (hasVoiceRoamingOn) {
            this.mVoiceRoamingOnRegistrants.notifyRegistrants();
        }
        if (hasVoiceRoamingOff) {
            this.mVoiceRoamingOffRegistrants.notifyRegistrants();
        }
        if (hasDataRoamingOn) {
            this.mDataRoamingOnRegistrants.notifyRegistrants();
        }
        if (hasDataRoamingOff) {
            this.mDataRoamingOffRegistrants.notifyRegistrants();
        }
        if (hasLocationChanged) {
            this.mPhone.notifyLocationChanged();
        }
    }

    private boolean isInHomeSidNid(int sid, int nid) {
        if (isSidsAllZeros() || this.mHomeSystemId.length != this.mHomeNetworkId.length || sid == 0) {
            return true;
        }
        int i = 0;
        while (i < this.mHomeSystemId.length) {
            if (this.mHomeSystemId[i] == sid && (this.mHomeNetworkId[i] == 0 || this.mHomeNetworkId[i] == 65535 || nid == 0 || nid == CallFailCause.ERROR_UNSPECIFIED || this.mHomeNetworkId[i] == nid)) {
                return true;
            }
            i++;
        }
        return VDBG;
    }

    /* Access modifiers changed, original: protected */
    public void setOperatorIdd(String operatorNumeric) {
        String idd = this.mHbpcdUtils.getIddByMcc(Integer.parseInt(operatorNumeric.substring(0, 3)));
        if (idd == null || idd.isEmpty()) {
            this.mPhone.setSystemProperty("gsm.operator.idpstring", "+");
        } else {
            this.mPhone.setSystemProperty("gsm.operator.idpstring", idd);
        }
    }

    /* Access modifiers changed, original: protected */
    public boolean isInvalidOperatorNumeric(String operatorNumeric) {
        if (operatorNumeric == null || operatorNumeric.length() < 5) {
            return true;
        }
        return operatorNumeric.startsWith(INVALID_MCC);
    }

    /* Access modifiers changed, original: protected */
    public String fixUnknownMcc(String operatorNumeric, int sid) {
        int i = 0;
        if (sid <= 0) {
            return operatorNumeric;
        }
        boolean isNitzTimeZone = VDBG;
        int timeZone = 0;
        if (this.mSavedTimeZone != null) {
            timeZone = TimeZone.getTimeZone(this.mSavedTimeZone).getRawOffset() / MS_PER_HOUR;
            isNitzTimeZone = true;
        } else {
            TimeZone tzone = getNitzTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime);
            if (tzone != null) {
                timeZone = tzone.getRawOffset() / MS_PER_HOUR;
            }
        }
        HbpcdUtils hbpcdUtils = this.mHbpcdUtils;
        if (this.mZoneDst) {
            i = 1;
        }
        int mcc = hbpcdUtils.getMcc(sid, timeZone, i, isNitzTimeZone);
        if (mcc > 0) {
            operatorNumeric = Integer.toString(mcc) + DEFAULT_MNC;
        }
        return operatorNumeric;
    }

    /* Access modifiers changed, original: protected */
    public void fixTimeZone(String isoCountryCode) {
        TimeZone zone;
        String zoneName = SystemProperties.get(TIMEZONE_PROPERTY);
        log("fixTimeZone zoneName='" + zoneName + "' mZoneOffset=" + this.mZoneOffset + " mZoneDst=" + this.mZoneDst + " iso-cc='" + isoCountryCode + "' iso-cc-idx=" + Arrays.binarySearch(GMT_COUNTRY_CODES, isoCountryCode));
        if ("".equals(isoCountryCode) && this.mNeedFixZoneAfterNitz) {
            zone = getNitzTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime);
            log("pollStateDone: using NITZ TimeZone");
        } else if (this.mZoneOffset != 0 || this.mZoneDst || zoneName == null || zoneName.length() <= 0 || Arrays.binarySearch(GMT_COUNTRY_CODES, isoCountryCode) >= 0) {
            zone = TimeUtils.getTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime, isoCountryCode);
            log("fixTimeZone: using getTimeZone(off, dst, time, iso)");
        } else {
            zone = TimeZone.getDefault();
            if (this.mNeedFixZoneAfterNitz) {
                long ctm = System.currentTimeMillis();
                long tzOffset = (long) zone.getOffset(ctm);
                log("fixTimeZone: tzOffset=" + tzOffset + " ltod=" + TimeUtils.logTimeOfDay(ctm));
                if (getAutoTime()) {
                    long adj = ctm - tzOffset;
                    log("fixTimeZone: adj ltod=" + TimeUtils.logTimeOfDay(adj));
                    setAndBroadcastNetworkSetTime(adj);
                } else {
                    this.mSavedTime -= tzOffset;
                    log("fixTimeZone: adj mSavedTime=" + this.mSavedTime);
                }
            }
            log("fixTimeZone: using default TimeZone");
        }
        this.mNeedFixZoneAfterNitz = VDBG;
        if (zone != null) {
            log("fixTimeZone: zone != null zone.getID=" + zone.getID());
            if (getAutoTimeZone()) {
                setAndBroadcastNetworkSetTimeZone(zone.getID());
            } else {
                log("fixTimeZone: skip changing zone as getAutoTimeZone was false");
            }
            saveNitzTimeZone(zone.getID());
            return;
        }
        log("fixTimeZone: zone == null, do nothing for zone");
    }

    private boolean isGprsConsistent(int dataRegState, int voiceRegState) {
        return (voiceRegState != 0 || dataRegState == 0) ? true : VDBG;
    }

    private TimeZone getNitzTimeZone(int offset, boolean dst, long when) {
        TimeZone guess = findTimeZone(offset, dst, when);
        if (guess == null) {
            guess = findTimeZone(offset, dst ? VDBG : true, when);
        }
        log("getNitzTimeZone returning " + (guess == null ? guess : guess.getID()));
        return guess;
    }

    private TimeZone findTimeZone(int offset, boolean dst, long when) {
        int rawOffset = offset;
        if (dst) {
            rawOffset = offset - MS_PER_HOUR;
        }
        String[] zones = TimeZone.getAvailableIDs(rawOffset);
        Date d = new Date(when);
        for (String zone : zones) {
            TimeZone tz = TimeZone.getTimeZone(zone);
            if (tz.getOffset(when) == offset && tz.inDaylightTime(d) == dst) {
                return tz;
            }
        }
        return null;
    }

    private int regCodeToServiceState(int code) {
        switch (code) {
            case 0:
            case 2:
            case 3:
            case 4:
            case 10:
            case 12:
            case 13:
            case 14:
                return 1;
            case 1:
            case 5:
                return 0;
            default:
                loge("regCodeToServiceState: unexpected service state " + code);
                return 1;
        }
    }

    private boolean regCodeIsRoaming(int code) {
        return 5 == code ? true : VDBG;
    }

    private boolean isSameOperatorNameFromSimAndSS(ServiceState s) {
        String spn = ((TelephonyManager) this.mPhone.getContext().getSystemService("phone")).getSimOperatorNameForPhone(getPhoneId());
        return !(!TextUtils.isEmpty(spn) ? spn.equalsIgnoreCase(s.getOperatorAlphaLong()) : VDBG) ? !TextUtils.isEmpty(spn) ? spn.equalsIgnoreCase(s.getOperatorAlphaShort()) : VDBG : true;
    }

    private boolean isSameNamedOperators(ServiceState s) {
        return currentMccEqualsSimMcc(s) ? isSameOperatorNameFromSimAndSS(s) : VDBG;
    }

    private boolean currentMccEqualsSimMcc(ServiceState s) {
        boolean equalsMcc = true;
        try {
            return ((TelephonyManager) this.mPhone.getContext().getSystemService("phone")).getSimOperatorNumericForPhone(getPhoneId()).substring(0, 3).equals(s.getOperatorNumeric().substring(0, 3));
        } catch (Exception e) {
            return equalsMcc;
        }
    }

    private boolean isOperatorConsideredNonRoaming(ServiceState s) {
        String operatorNumeric = s.getOperatorNumeric();
        String[] numericArray = this.mPhone.getContext().getResources().getStringArray(17236030);
        if (numericArray.length == 0 || operatorNumeric == null) {
            return VDBG;
        }
        for (String numeric : numericArray) {
            if (operatorNumeric.startsWith(numeric)) {
                return true;
            }
        }
        return VDBG;
    }

    private boolean isOperatorConsideredRoaming(ServiceState s) {
        String operatorNumeric = s.getOperatorNumeric();
        String[] numericArray = this.mPhone.getContext().getResources().getStringArray(17236031);
        if (numericArray.length == 0 || operatorNumeric == null) {
            return VDBG;
        }
        for (String numeric : numericArray) {
            if (operatorNumeric.startsWith(numeric)) {
                return true;
            }
        }
        return VDBG;
    }

    private void onRestrictedStateChanged(AsyncResult ar) {
        boolean z = true;
        RestrictedState newRs = new RestrictedState();
        log("onRestrictedStateChanged: E rs " + this.mRestrictedState);
        if (ar.exception == null) {
            boolean z2;
            int state = ar.result[0];
            if ((state & 1) == 0) {
                z2 = (state & 4) != 0 ? true : VDBG;
            } else {
                z2 = true;
            }
            newRs.setCsEmergencyRestricted(z2);
            if (this.mUiccApplcation != null && this.mUiccApplcation.getState() == AppState.APPSTATE_READY) {
                if ((state & 2) == 0) {
                    z2 = (state & 4) != 0 ? true : VDBG;
                } else {
                    z2 = true;
                }
                newRs.setCsNormalRestricted(z2);
                if ((state & 16) == 0) {
                    z = VDBG;
                }
                newRs.setPsRestricted(z);
            }
            log("onRestrictedStateChanged: new rs " + newRs);
            if (!this.mRestrictedState.isPsRestricted() && newRs.isPsRestricted()) {
                this.mPsRestrictEnabledRegistrants.notifyRegistrants();
                setNotification(1001);
            } else if (this.mRestrictedState.isPsRestricted() && !newRs.isPsRestricted()) {
                this.mPsRestrictDisabledRegistrants.notifyRegistrants();
                setNotification(1002);
            }
            int csstat_new = 0;
            int csstat_prv = 0;
            if (this.mRestrictedState.isCsEmergencyRestricted()) {
                csstat_prv = 2;
            }
            if (this.mRestrictedState.isCsNormalRestricted()) {
                csstat_prv++;
            }
            if (newRs.isCsEmergencyRestricted()) {
                csstat_new = 2;
            }
            if (newRs.isCsNormalRestricted()) {
                csstat_new++;
            }
            if (csstat_prv != csstat_new) {
                switch (csstat_new) {
                    case 0:
                        setNotification(1004);
                        SystemProperties.set("gsm.cs_normal_restricted", "false");
                        break;
                    case 1:
                        setNotification(1005);
                        SystemProperties.set("gsm.cs_normal_restricted", "true");
                        break;
                    case 2:
                        setNotification(1006);
                        SystemProperties.set("gsm.cs_normal_restricted", "false");
                        break;
                    case 3:
                        setNotification(1003);
                        SystemProperties.set("gsm.cs_normal_restricted", "true");
                        break;
                }
            }
            this.mRestrictedState = newRs;
        }
        log("onRestrictedStateChanged: X rs " + this.mRestrictedState);
    }

    public CellLocation getCellLocation() {
        if (((GsmCellLocation) this.mCellLoc).getLac() < 0 || ((GsmCellLocation) this.mCellLoc).getCid() < 0) {
            List<CellInfo> result = getAllCellInfo();
            if (result != null) {
                GsmCellLocation cellLocOther = new GsmCellLocation();
                for (CellInfo ci : result) {
                    if (ci instanceof CellInfoGsm) {
                        CellIdentityGsm cellIdentityGsm = ((CellInfoGsm) ci).getCellIdentity();
                        cellLocOther.setLacAndCid(cellIdentityGsm.getLac(), cellIdentityGsm.getCid());
                        cellLocOther.setPsc(cellIdentityGsm.getPsc());
                        log("getCellLocation(): X ret GSM info=" + cellLocOther);
                        return cellLocOther;
                    } else if (ci instanceof CellInfoWcdma) {
                        CellIdentityWcdma cellIdentityWcdma = ((CellInfoWcdma) ci).getCellIdentity();
                        cellLocOther.setLacAndCid(cellIdentityWcdma.getLac(), cellIdentityWcdma.getCid());
                        cellLocOther.setPsc(cellIdentityWcdma.getPsc());
                        log("getCellLocation(): X ret WCDMA info=" + cellLocOther);
                        return cellLocOther;
                    } else if ((ci instanceof CellInfoLte) && (cellLocOther.getLac() < 0 || cellLocOther.getCid() < 0)) {
                        CellIdentityLte cellIdentityLte = ((CellInfoLte) ci).getCellIdentity();
                        if (!(cellIdentityLte.getTac() == Integer.MAX_VALUE || cellIdentityLte.getCi() == Integer.MAX_VALUE)) {
                            cellLocOther.setLacAndCid(cellIdentityLte.getTac(), cellIdentityLte.getCi());
                            cellLocOther.setPsc(0);
                            log("getCellLocation(): possible LTE cellLocOther=" + cellLocOther);
                        }
                    }
                }
                log("getCellLocation(): X ret best answer cellLocOther=" + cellLocOther);
                return cellLocOther;
            }
            log("getCellLocation(): X empty mCellLoc and CellInfo mCellLoc=" + this.mCellLoc);
            return this.mCellLoc;
        }
        log("getCellLocation(): X good mCellLoc=" + this.mCellLoc);
        return this.mCellLoc;
    }

    /* JADX WARNING: Removed duplicated region for block: B:65:0x02d6 A:{Catch:{ all -> 0x055a, RuntimeException -> 0x0593 }} */
    /* JADX WARNING: Removed duplicated region for block: B:36:0x0210 A:{Catch:{ all -> 0x055a, RuntimeException -> 0x0593 }} */
    /* JADX WARNING: Removed duplicated region for block: B:39:0x025a A:{Catch:{ all -> 0x055a, RuntimeException -> 0x0593 }} */
    /* JADX WARNING: Removed duplicated region for block: B:77:0x035d  */
    /* JADX WARNING: Removed duplicated region for block: B:73:0x0302 A:{Catch:{ all -> 0x055a, RuntimeException -> 0x0593 }} */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void setTimeFromNITZString(String nitz, long nitzReceiveTime) {
        long start = SystemClock.elapsedRealtime();
        log("NITZ: " + nitz + "," + nitzReceiveTime + " start=" + start + " delay=" + (start - nitzReceiveTime));
        long end;
        try {
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            c.clear();
            c.set(16, 0);
            String[] nitzSubs = nitz.split("[/:,+-]");
            int year = Integer.parseInt(nitzSubs[0]) + NITZ_UPDATE_DIFF_DEFAULT;
            if (year > MAX_NITZ_YEAR) {
                loge("NITZ year: " + year + " exceeds limit, skip NITZ time update");
                return;
            }
            String ignore;
            long millisSinceNitzReceived;
            c.set(1, year);
            c.set(2, Integer.parseInt(nitzSubs[1]) - 1);
            c.set(5, Integer.parseInt(nitzSubs[2]));
            c.set(10, Integer.parseInt(nitzSubs[3]));
            c.set(12, Integer.parseInt(nitzSubs[4]));
            c.set(13, Integer.parseInt(nitzSubs[5]));
            boolean sign = nitz.indexOf(EVENT_CHANGE_IMS_STATE) == -1 ? true : VDBG;
            int tzOffset = Integer.parseInt(nitzSubs[6]);
            int dst = nitzSubs.length >= 8 ? Integer.parseInt(nitzSubs[7]) : 0;
            tzOffset = ((((sign ? 1 : -1) * tzOffset) * 15) * 60) * 1000;
            TimeZone zone = null;
            if (nitzSubs.length >= 9) {
                zone = TimeZone.getTimeZone(nitzSubs[8].replace('!', '/'));
            }
            String iso = ((TelephonyManager) this.mPhone.getContext().getSystemService("phone")).getNetworkCountryIsoForPhone(this.mPhone.getPhoneId());
            if (zone == null && this.mGotCountryCode) {
                if (iso == null || iso.length() <= 0) {
                    zone = getNitzTimeZone(tzOffset, dst != 0 ? true : VDBG, c.getTimeInMillis());
                } else {
                    zone = TimeUtils.getTimeZone(tzOffset, dst != 0 ? true : VDBG, c.getTimeInMillis(), iso);
                }
            }
            if (zone != null && this.mZoneOffset == tzOffset) {
                if (this.mZoneDst != (dst != 0 ? true : VDBG)) {
                }
                log("NITZ: tzOffset=" + tzOffset + " dst=" + dst + " zone=" + (zone == null ? zone.getID() : "NULL") + " iso=" + iso + " mGotCountryCode=" + this.mGotCountryCode + " mNeedFixZoneAfterNitz=" + this.mNeedFixZoneAfterNitz);
                if (zone != null) {
                    if (getAutoTimeZone()) {
                        setAndBroadcastNetworkSetTimeZone(zone.getID());
                    }
                    saveNitzTimeZone(zone.getID());
                }
                ignore = SystemProperties.get("gsm.ignore-nitz");
                if (ignore == null && ignore.equals("yes")) {
                    log("NITZ: Not setting clock because gsm.ignore-nitz is set");
                    return;
                }
                this.mWakeLock.acquire();
                if (!this.mPhone.isPhoneTypeGsm() || getAutoTime()) {
                    millisSinceNitzReceived = SystemClock.elapsedRealtime() - nitzReceiveTime;
                    if (millisSinceNitzReceived >= 0) {
                        log("NITZ: not setting time, clock has rolled backwards since NITZ time was received, " + nitz);
                        end = SystemClock.elapsedRealtime();
                        log("NITZ: end=" + end + " dur=" + (end - start));
                        this.mWakeLock.release();
                        return;
                    } else if (millisSinceNitzReceived > 2147483647L) {
                        log("NITZ: not setting time, processing has taken " + (millisSinceNitzReceived / 86400000) + " days");
                        end = SystemClock.elapsedRealtime();
                        log("NITZ: end=" + end + " dur=" + (end - start));
                        this.mWakeLock.release();
                        return;
                    } else {
                        c.add(14, (int) millisSinceNitzReceived);
                        log("NITZ: Setting time of day to " + c.getTime() + " NITZ receive delay(ms): " + millisSinceNitzReceived + " gained(ms): " + (c.getTimeInMillis() - System.currentTimeMillis()) + " from " + nitz);
                        if (this.mPhone.isPhoneTypeGsm()) {
                            setAndBroadcastNetworkSetTime(c.getTimeInMillis());
                            Rlog.i(LOG_TAG, "NITZ: after Setting time of day");
                        } else if (getAutoTime()) {
                            long gained = c.getTimeInMillis() - System.currentTimeMillis();
                            long timeSinceLastUpdate = SystemClock.elapsedRealtime() - this.mSavedAtTime;
                            int nitzUpdateSpacing = Global.getInt(this.mCr, "nitz_update_spacing", this.mNitzUpdateSpacing);
                            int nitzUpdateDiff = Global.getInt(this.mCr, "nitz_update_diff", this.mNitzUpdateDiff);
                            if (this.mSavedAtTime != 0 && timeSinceLastUpdate <= ((long) nitzUpdateSpacing)) {
                                if (Math.abs(gained) <= ((long) nitzUpdateDiff)) {
                                    log("NITZ: ignore, a previous update was " + timeSinceLastUpdate + "ms ago and gained=" + gained + "ms");
                                    end = SystemClock.elapsedRealtime();
                                    log("NITZ: end=" + end + " dur=" + (end - start));
                                    this.mWakeLock.release();
                                    return;
                                }
                            }
                            log("NITZ: Auto updating time of day to " + c.getTime() + " NITZ receive delay=" + millisSinceNitzReceived + "ms gained=" + gained + "ms from " + nitz);
                            setAndBroadcastNetworkSetTime(c.getTimeInMillis());
                        }
                    }
                }
                SystemProperties.set("gsm.nitz.time", String.valueOf(c.getTimeInMillis()));
                saveNitzTime(c.getTimeInMillis());
                this.mNitzUpdatedTime = true;
                end = SystemClock.elapsedRealtime();
                log("NITZ: end=" + end + " dur=" + (end - start));
                this.mWakeLock.release();
                return;
            }
            this.mNeedFixZoneAfterNitz = true;
            this.mZoneOffset = tzOffset;
            this.mZoneDst = dst != 0 ? true : VDBG;
            this.mZoneTime = c.getTimeInMillis();
            if (zone == null) {
            }
            log("NITZ: tzOffset=" + tzOffset + " dst=" + dst + " zone=" + (zone == null ? zone.getID() : "NULL") + " iso=" + iso + " mGotCountryCode=" + this.mGotCountryCode + " mNeedFixZoneAfterNitz=" + this.mNeedFixZoneAfterNitz);
            if (zone != null) {
            }
            ignore = SystemProperties.get("gsm.ignore-nitz");
            if (ignore == null) {
            }
            this.mWakeLock.acquire();
            millisSinceNitzReceived = SystemClock.elapsedRealtime() - nitzReceiveTime;
            if (millisSinceNitzReceived >= 0) {
            }
        } catch (RuntimeException ex) {
            loge("NITZ: Parsing NITZ time " + nitz + " ex=" + ex);
        } catch (Throwable th) {
            end = SystemClock.elapsedRealtime();
            log("NITZ: end=" + end + " dur=" + (end - start));
            this.mWakeLock.release();
        }
    }

    private boolean getAutoTime() {
        boolean z = true;
        try {
            if (Global.getInt(this.mCr, "auto_time") <= 0) {
                z = VDBG;
            }
            return z;
        } catch (SettingNotFoundException e) {
            return true;
        }
    }

    private boolean getAutoTimeZone() {
        boolean z = true;
        try {
            if (Global.getInt(this.mCr, "auto_time_zone") <= 0) {
                z = VDBG;
            }
            return z;
        } catch (SettingNotFoundException e) {
            return true;
        }
    }

    private void saveNitzTimeZone(String zoneId) {
        this.mSavedTimeZone = zoneId;
    }

    private void saveNitzTime(long time) {
        this.mSavedTime = time;
        this.mSavedAtTime = SystemClock.elapsedRealtime();
    }

    private void setAndBroadcastNetworkSetTimeZone(String zoneId) {
        log("setAndBroadcastNetworkSetTimeZone: setTimeZone=" + zoneId);
        ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).setTimeZone(zoneId);
        Intent intent = new Intent("android.intent.action.NETWORK_SET_TIMEZONE");
        intent.addFlags(536870912);
        intent.putExtra("time-zone", zoneId);
        this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        log("setAndBroadcastNetworkSetTimeZone: call alarm.setTimeZone and broadcast zoneId=" + zoneId);
    }

    private void setAndBroadcastNetworkSetTime(long time) {
        log("setAndBroadcastNetworkSetTime: time=" + time + "ms");
        SystemClock.setCurrentTimeMillis(time);
        Intent intent = new Intent("android.intent.action.NETWORK_SET_TIME");
        intent.addFlags(536870912);
        intent.putExtra("time", time);
        this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void revertToNitzTime() {
        if (Global.getInt(this.mCr, "auto_time", 0) != 0) {
            log("Reverting to NITZ Time: mSavedTime=" + this.mSavedTime + " mSavedAtTime=" + this.mSavedAtTime);
            if (!(this.mSavedTime == 0 || this.mSavedAtTime == 0)) {
                setAndBroadcastNetworkSetTime(this.mSavedTime + (SystemClock.elapsedRealtime() - this.mSavedAtTime));
            }
        }
    }

    private void revertToNitzTimeZone() {
        if (Global.getInt(this.mCr, "auto_time_zone", 0) != 0) {
            log("Reverting to NITZ TimeZone: tz='" + this.mSavedTimeZone);
            if (this.mSavedTimeZone != null) {
                setAndBroadcastNetworkSetTimeZone(this.mSavedTimeZone);
            }
        }
    }

    private void setNotification(int notifyType) {
        log("setNotification: create notification " + notifyType);
        if (this.mPhone.getContext().getResources().getBoolean(17956959)) {
            Context context = this.mPhone.getContext();
            CharSequence details = "";
            CharSequence title = context.getText(17039572);
            int notificationId = CS_NOTIFICATION;
            switch (notifyType) {
                case 1001:
                    notificationId = PS_NOTIFICATION;
                    details = context.getText(17039573);
                    break;
                case 1002:
                    notificationId = PS_NOTIFICATION;
                    break;
                case 1003:
                    details = context.getText(17039576);
                    break;
                case 1005:
                    details = context.getText(17039575);
                    break;
                case 1006:
                    details = context.getText(17039574);
                    break;
            }
            log("setNotification: put notification " + title + " / " + details);
            this.mNotification = new Builder(context).setWhen(System.currentTimeMillis()).setAutoCancel(true).setSmallIcon(17301642).setTicker(title).setColor(context.getResources().getColor(17170521)).setContentTitle(title).setContentText(details).build();
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(ThreadsColumns.NOTIFICATION);
            if (notifyType == 1002 || notifyType == 1004) {
                notificationManager.cancel(notificationId);
            } else {
                notificationManager.notify(notificationId, this.mNotification);
            }
            return;
        }
        log("Ignore all the notifications");
    }

    private UiccCardApplication getUiccCardApplication() {
        if (this.mPhone.isPhoneTypeGsm()) {
            return this.mUiccController.getUiccCardApplication(this.mPhone.getPhoneId(), 1);
        }
        return this.mUiccController.getUiccCardApplication(this.mPhone.getPhoneId(), 2);
    }

    private void queueNextSignalStrengthPoll() {
        if (!this.mDontPollSignalStrength) {
            Message msg = obtainMessage();
            msg.what = 10;
            sendMessageDelayed(msg, 20000);
        }
    }

    private void notifyCdmaSubscriptionInfoReady() {
        if (this.mCdmaForSubscriptionInfoReadyRegistrants != null) {
            log("CDMA_SUBSCRIPTION: call notifyRegistrants()");
            this.mCdmaForSubscriptionInfoReadyRegistrants.notifyRegistrants();
        }
    }

    public void registerForDataConnectionAttached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mAttachedRegistrants.add(r);
        if (getCurrentDataConnectionState() == 0) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForDataConnectionAttached(Handler h) {
        this.mAttachedRegistrants.remove(h);
    }

    public void registerForDataConnectionDetached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mDetachedRegistrants.add(r);
        if (getCurrentDataConnectionState() != 0) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForDataConnectionDetached(Handler h) {
        this.mDetachedRegistrants.remove(h);
    }

    public void registerForDataRegStateOrRatChanged(Handler h, int what, Object obj) {
        this.mDataRegStateOrRatChangedRegistrants.add(new Registrant(h, what, obj));
        notifyDataRegStateRilRadioTechnologyChanged();
    }

    public void unregisterForDataRegStateOrRatChanged(Handler h) {
        this.mDataRegStateOrRatChangedRegistrants.remove(h);
    }

    public void registerForNetworkAttached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mNetworkAttachedRegistrants.add(r);
        if (this.mSS.getVoiceRegState() == 0) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForNetworkAttached(Handler h) {
        this.mNetworkAttachedRegistrants.remove(h);
    }

    public void registerForPsRestrictedEnabled(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mPsRestrictEnabledRegistrants.add(r);
        if (this.mRestrictedState.isPsRestricted()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForPsRestrictedEnabled(Handler h) {
        this.mPsRestrictEnabledRegistrants.remove(h);
    }

    public void registerForPsRestrictedDisabled(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mPsRestrictDisabledRegistrants.add(r);
        if (this.mRestrictedState.isPsRestricted()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForPsRestrictedDisabled(Handler h) {
        this.mPsRestrictDisabledRegistrants.remove(h);
    }

    /* JADX WARNING: Missing block: B:20:0x0051, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void powerOffRadioSafely(DcTracker dcTracker) {
        synchronized (this) {
            if (!this.mPendingRadioPowerOffAfterDataOff) {
                Message msg;
                int i;
                if (this.mPhone.isPhoneTypeGsm() || this.mPhone.isPhoneTypeCdma() || this.mPhone.isPhoneTypeCdmaLte()) {
                    int dds = SubscriptionManager.getDefaultDataSubscriptionId();
                    if (!dcTracker.isDisconnected() || (dds != this.mPhone.getSubId() && (dds == this.mPhone.getSubId() || !ProxyController.getInstance().isDataDisconnected(dds)))) {
                        if (this.mPhone.isPhoneTypeGsm() && this.mPhone.isInCall()) {
                            this.mPhone.mCT.mRingingCall.hangupIfAlive();
                            this.mPhone.mCT.mBackgroundCall.hangupIfAlive();
                            this.mPhone.mCT.mForegroundCall.hangupIfAlive();
                        }
                        dcTracker.cleanUpAllConnections(PhoneInternalInterface.REASON_RADIO_TURNED_OFF);
                        if (!(dds == this.mPhone.getSubId() || ProxyController.getInstance().isDataDisconnected(dds))) {
                            log("Data is active on DDS.  Wait for all data disconnect");
                            ProxyController.getInstance().registerForAllDataDisconnected(dds, this, 49, null);
                            this.mPendingRadioPowerOffAfterDataOff = true;
                        }
                        msg = Message.obtain(this);
                        msg.what = 38;
                        i = this.mPendingRadioPowerOffAfterDataOffTag + 1;
                        this.mPendingRadioPowerOffAfterDataOffTag = i;
                        msg.arg1 = i;
                        if (sendMessageDelayed(msg, 4000)) {
                            log("Wait upto 4s for data to disconnect, then turn off radio.");
                            this.mPendingRadioPowerOffAfterDataOff = true;
                        } else {
                            log("Cannot send delayed Msg, turn off radio right away.");
                            hangupAndPowerOff();
                            this.mPendingRadioPowerOffAfterDataOff = VDBG;
                        }
                    } else {
                        dcTracker.cleanUpAllConnections(PhoneInternalInterface.REASON_RADIO_TURNED_OFF);
                        log("Data disconnected, turn off radio right away.");
                        hangupAndPowerOff();
                    }
                } else {
                    String[] networkNotClearData = this.mPhone.getContext().getResources().getStringArray(17236041);
                    String currentNetwork = this.mSS.getOperatorNumeric();
                    if (!(networkNotClearData == null || currentNetwork == null)) {
                        for (Object equals : networkNotClearData) {
                            if (currentNetwork.equals(equals)) {
                                log("Not disconnecting data for " + currentNetwork);
                                hangupAndPowerOff();
                                return;
                            }
                        }
                    }
                    if (dcTracker.isDisconnected()) {
                        dcTracker.cleanUpAllConnections(PhoneInternalInterface.REASON_RADIO_TURNED_OFF);
                        log("Data disconnected, turn off radio right away.");
                        hangupAndPowerOff();
                    } else {
                        dcTracker.cleanUpAllConnections(PhoneInternalInterface.REASON_RADIO_TURNED_OFF);
                        msg = Message.obtain(this);
                        msg.what = 38;
                        i = this.mPendingRadioPowerOffAfterDataOffTag + 1;
                        this.mPendingRadioPowerOffAfterDataOffTag = i;
                        msg.arg1 = i;
                        if (sendMessageDelayed(msg, 30000)) {
                            log("Wait upto 30s for data to disconnect, then turn off radio.");
                            this.mPendingRadioPowerOffAfterDataOff = true;
                        } else {
                            log("Cannot send delayed Msg, turn off radio right away.");
                            hangupAndPowerOff();
                        }
                    }
                }
            }
        }
    }

    public boolean processPendingRadioPowerOffAfterDataOff() {
        synchronized (this) {
            if (this.mPendingRadioPowerOffAfterDataOff) {
                log("Process pending request to turn radio off.");
                this.mPendingRadioPowerOffAfterDataOffTag++;
                hangupAndPowerOff();
                this.mPendingRadioPowerOffAfterDataOff = VDBG;
                return true;
            }
            return VDBG;
        }
    }

    /* Access modifiers changed, original: protected */
    public boolean onSignalStrengthResult(AsyncResult ar) {
        boolean isGsm = VDBG;
        if (this.mPhone.isPhoneTypeGsm() || (this.mPhone.isPhoneTypeCdmaLte() && isRatLte(this.mSS.getRilDataRadioTechnology()))) {
            isGsm = true;
        }
        if (ar.exception != null || ar.result == null) {
            log("onSignalStrengthResult() Exception from RIL : " + ar.exception);
            this.mSignalStrength = new SignalStrength(isGsm);
        } else {
            this.mSignalStrength = (SignalStrength) ar.result;
            this.mSignalStrength.validateInput();
            this.mSignalStrength.setGsm(isGsm);
        }
        return notifySignalStrength();
    }

    /* Access modifiers changed, original: protected */
    public void hangupAndPowerOff() {
        if (!this.mPhone.isPhoneTypeGsm() || this.mPhone.isInCall()) {
            this.mPhone.mCT.mRingingCall.hangupIfAlive();
            this.mPhone.mCT.mBackgroundCall.hangupIfAlive();
            this.mPhone.mCT.mForegroundCall.hangupIfAlive();
        }
        this.mCi.setRadioPower(VDBG, obtainMessage(51));
    }

    /* Access modifiers changed, original: protected */
    public void cancelPollState() {
        this.mPollingContext = new int[1];
    }

    /* Access modifiers changed, original: protected */
    public boolean shouldFixTimeZoneNow(Phone phone, String operatorNumeric, String prevOperatorNumeric, boolean needToFixTimeZone) {
        try {
            int prevMcc;
            int mcc = Integer.parseInt(operatorNumeric.substring(0, 3));
            try {
                prevMcc = Integer.parseInt(prevOperatorNumeric.substring(0, 3));
            } catch (Exception e) {
                prevMcc = mcc + 1;
            }
            boolean iccCardExist = VDBG;
            if (this.mUiccApplcation != null) {
                iccCardExist = this.mUiccApplcation.getState() != AppState.APPSTATE_UNKNOWN ? true : VDBG;
            }
            boolean retVal = (!iccCardExist || mcc == prevMcc) ? needToFixTimeZone : true;
            log("shouldFixTimeZoneNow: retVal=" + retVal + " iccCardExist=" + iccCardExist + " operatorNumeric=" + operatorNumeric + " mcc=" + mcc + " prevOperatorNumeric=" + prevOperatorNumeric + " prevMcc=" + prevMcc + " needToFixTimeZone=" + needToFixTimeZone + " ltod=" + TimeUtils.logTimeOfDay(System.currentTimeMillis()));
            return retVal;
        } catch (Exception e2) {
            log("shouldFixTimeZoneNow: no mcc, operatorNumeric=" + operatorNumeric + " retVal=false");
            return VDBG;
        }
    }

    public String getSystemProperty(String property, String defValue) {
        return TelephonyManager.getTelephonyProperty(this.mPhone.getPhoneId(), property, defValue);
    }

    public List<CellInfo> getAllCellInfo() {
        CellInfoResult result = new CellInfoResult(this, null);
        if (this.mCi.getRilVersion() < 8) {
            log("SST.getAllCellInfo(): not implemented");
            result.list = null;
        } else if (!isCallerOnDifferentThread()) {
            log("SST.getAllCellInfo(): return last, same thread can't block");
            result.list = this.mLastCellInfoList;
        } else if (SystemClock.elapsedRealtime() - this.mLastCellInfoListTime > LAST_CELL_INFO_LIST_MAX_AGE_MS) {
            Message msg = obtainMessage(43, result);
            synchronized (result.lockObj) {
                result.list = null;
                this.mCi.getCellInfoList(msg);
                try {
                    result.lockObj.wait(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else {
            log("SST.getAllCellInfo(): return last, back to back calls");
            result.list = this.mLastCellInfoList;
        }
        synchronized (result.lockObj) {
            if (result.list != null) {
                List list = result.list;
                return list;
            }
            log("SST.getAllCellInfo(): X size=0 list=null");
            return null;
        }
    }

    public SignalStrength getSignalStrength() {
        return this.mSignalStrength;
    }

    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mCdmaForSubscriptionInfoReadyRegistrants.add(r);
        if (isMinInfoReady()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForSubscriptionInfoReady(Handler h) {
        this.mCdmaForSubscriptionInfoReadyRegistrants.remove(h);
    }

    private void saveCdmaSubscriptionSource(int source) {
        log("Storing cdma subscription source: " + source);
        Global.putInt(this.mPhone.getContext().getContentResolver(), "subscription_mode", source);
        log("Read from settings: " + Global.getInt(this.mPhone.getContext().getContentResolver(), "subscription_mode", -1));
    }

    private void getSubscriptionInfoAndStartPollingThreads() {
        this.mCi.getCDMASubscription(obtainMessage(34));
        pollState();
    }

    private void handleCdmaSubscriptionSource(int newSubscriptionSource) {
        boolean z = VDBG;
        log("Subscription Source : " + newSubscriptionSource);
        if (newSubscriptionSource == 0) {
            z = true;
        }
        this.mIsSubscriptionFromRuim = z;
        log("isFromRuim: " + this.mIsSubscriptionFromRuim);
        saveCdmaSubscriptionSource(newSubscriptionSource);
        if (!this.mIsSubscriptionFromRuim) {
            sendMessage(obtainMessage(35));
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("ServiceStateTracker:");
        pw.println(" mSubId=" + this.mSubId);
        pw.println(" mSS=" + this.mSS);
        pw.println(" mNewSS=" + this.mNewSS);
        pw.println(" mVoiceCapable=" + this.mVoiceCapable);
        pw.println(" mRestrictedState=" + this.mRestrictedState);
        pw.println(" mPollingContext=" + this.mPollingContext + " - " + (this.mPollingContext != null ? Integer.valueOf(this.mPollingContext[0]) : ""));
        pw.println(" mDesiredPowerState=" + this.mDesiredPowerState);
        pw.println(" mDontPollSignalStrength=" + this.mDontPollSignalStrength);
        pw.println(" mSignalStrength=" + this.mSignalStrength);
        pw.println(" mLastSignalStrength=" + this.mLastSignalStrength);
        pw.println(" mRestrictedState=" + this.mRestrictedState);
        pw.println(" mPendingRadioPowerOffAfterDataOff=" + this.mPendingRadioPowerOffAfterDataOff);
        pw.println(" mPendingRadioPowerOffAfterDataOffTag=" + this.mPendingRadioPowerOffAfterDataOffTag);
        pw.println(" mCellLoc=" + this.mCellLoc);
        pw.println(" mNewCellLoc=" + this.mNewCellLoc);
        pw.println(" mLastCellInfoListTime=" + this.mLastCellInfoListTime);
        pw.println(" mPreferredNetworkType=" + this.mPreferredNetworkType);
        pw.println(" mMaxDataCalls=" + this.mMaxDataCalls);
        pw.println(" mNewMaxDataCalls=" + this.mNewMaxDataCalls);
        pw.println(" mReasonDataDenied=" + this.mReasonDataDenied);
        pw.println(" mNewReasonDataDenied=" + this.mNewReasonDataDenied);
        pw.println(" mGsmRoaming=" + this.mGsmRoaming);
        pw.println(" mDataRoaming=" + this.mDataRoaming);
        pw.println(" mEmergencyOnly=" + this.mEmergencyOnly);
        pw.println(" mNeedFixZoneAfterNitz=" + this.mNeedFixZoneAfterNitz);
        pw.flush();
        pw.println(" mZoneOffset=" + this.mZoneOffset);
        pw.println(" mZoneDst=" + this.mZoneDst);
        pw.println(" mZoneTime=" + this.mZoneTime);
        pw.println(" mGotCountryCode=" + this.mGotCountryCode);
        pw.println(" mNitzUpdatedTime=" + this.mNitzUpdatedTime);
        pw.println(" mSavedTimeZone=" + this.mSavedTimeZone);
        pw.println(" mSavedTime=" + this.mSavedTime);
        pw.println(" mSavedAtTime=" + this.mSavedAtTime);
        pw.println(" mStartedGprsRegCheck=" + this.mStartedGprsRegCheck);
        pw.println(" mReportedGprsNoReg=" + this.mReportedGprsNoReg);
        pw.println(" mNotification=" + this.mNotification);
        pw.println(" mWakeLock=" + this.mWakeLock);
        pw.println(" mCurSpn=" + this.mCurSpn);
        pw.println(" mCurDataSpn=" + this.mCurDataSpn);
        pw.println(" mCurShowSpn=" + this.mCurShowSpn);
        pw.println(" mCurPlmn=" + this.mCurPlmn);
        pw.println(" mCurShowPlmn=" + this.mCurShowPlmn);
        pw.flush();
        pw.println(" mCurrentOtaspMode=" + this.mCurrentOtaspMode);
        pw.println(" mRoamingIndicator=" + this.mRoamingIndicator);
        pw.println(" mIsInPrl=" + this.mIsInPrl);
        pw.println(" mDefaultRoamingIndicator=" + this.mDefaultRoamingIndicator);
        pw.println(" mRegistrationState=" + this.mRegistrationState);
        pw.println(" mMdn=" + this.mMdn);
        pw.println(" mHomeSystemId=" + this.mHomeSystemId);
        pw.println(" mHomeNetworkId=" + this.mHomeNetworkId);
        pw.println(" mMin=" + this.mMin);
        pw.println(" mPrlVersion=" + this.mPrlVersion);
        pw.println(" mIsMinInfoReady=" + this.mIsMinInfoReady);
        pw.println(" mIsEriTextLoaded=" + this.mIsEriTextLoaded);
        pw.println(" mIsSubscriptionFromRuim=" + this.mIsSubscriptionFromRuim);
        pw.println(" mCdmaSSM=" + this.mCdmaSSM);
        pw.println(" mRegistrationDeniedReason=" + this.mRegistrationDeniedReason);
        pw.println(" mCurrentCarrier=" + this.mCurrentCarrier);
        pw.flush();
        pw.println(" mImsRegistered=" + this.mImsRegistered);
        pw.println(" mImsRegistrationOnOff=" + this.mImsRegistrationOnOff);
        pw.println(" mAlarmSwitch=" + this.mAlarmSwitch);
        pw.println(" mPowerOffDelayNeed=" + this.mPowerOffDelayNeed);
        pw.println(" mDeviceShuttingDown=" + this.mDeviceShuttingDown);
        pw.println(" mSpnUpdatePending=" + this.mSpnUpdatePending);
    }

    public boolean isImsRegistered() {
        return this.mImsRegistered;
    }

    /* Access modifiers changed, original: protected */
    public void checkCorrectThread() {
        if (Thread.currentThread() != getLooper().getThread()) {
            throw new RuntimeException("ServiceStateTracker must be used from within one thread");
        }
    }

    /* Access modifiers changed, original: protected */
    public boolean isCallerOnDifferentThread() {
        return Thread.currentThread() != getLooper().getThread() ? true : VDBG;
    }

    /* Access modifiers changed, original: protected */
    public void updateCarrierMccMncConfiguration(String newOp, String oldOp, Context context) {
        if ((newOp == null && !TextUtils.isEmpty(oldOp)) || (newOp != null && !newOp.equals(oldOp))) {
            log("update mccmnc=" + newOp + " fromServiceState=true");
            MccTable.updateMccMncConfiguration(context, newOp, true);
        }
    }

    /* Access modifiers changed, original: protected */
    public boolean inSameCountry(String operatorNumeric) {
        if (TextUtils.isEmpty(operatorNumeric) || operatorNumeric.length() < 5) {
            return VDBG;
        }
        String homeNumeric = getHomeOperatorNumeric();
        if (TextUtils.isEmpty(homeNumeric) || homeNumeric.length() < 5) {
            return VDBG;
        }
        String networkMCC = operatorNumeric.substring(0, 3);
        String homeMCC = homeNumeric.substring(0, 3);
        String networkCountry = MccTable.countryCodeForMcc(Integer.parseInt(networkMCC));
        String homeCountry = MccTable.countryCodeForMcc(Integer.parseInt(homeMCC));
        if (networkCountry.isEmpty() || homeCountry.isEmpty()) {
            return VDBG;
        }
        boolean inSameCountry = homeCountry.equals(networkCountry);
        if (inSameCountry) {
            return inSameCountry;
        }
        if ("us".equals(homeCountry) && "vi".equals(networkCountry)) {
            inSameCountry = true;
        } else if ("vi".equals(homeCountry) && "us".equals(networkCountry)) {
            inSameCountry = true;
        }
        return inSameCountry;
    }

    /* Access modifiers changed, original: protected */
    public void setRoamingType(ServiceState currentServiceState) {
        boolean isVoiceInService = currentServiceState.getVoiceRegState() == 0 ? true : VDBG;
        if (isVoiceInService) {
            if (!currentServiceState.getVoiceRoaming()) {
                currentServiceState.setVoiceRoamingType(0);
            } else if (!this.mPhone.isPhoneTypeGsm()) {
                int[] intRoamingIndicators = this.mPhone.getContext().getResources().getIntArray(17236044);
                if (intRoamingIndicators != null && intRoamingIndicators.length > 0) {
                    currentServiceState.setVoiceRoamingType(2);
                    int curRoamingIndicator = currentServiceState.getCdmaRoamingIndicator();
                    for (int i : intRoamingIndicators) {
                        if (curRoamingIndicator == i) {
                            currentServiceState.setVoiceRoamingType(3);
                            break;
                        }
                    }
                } else if (inSameCountry(currentServiceState.getVoiceOperatorNumeric())) {
                    currentServiceState.setVoiceRoamingType(2);
                } else {
                    currentServiceState.setVoiceRoamingType(3);
                }
            } else if (inSameCountry(currentServiceState.getVoiceOperatorNumeric())) {
                currentServiceState.setVoiceRoamingType(2);
            } else {
                currentServiceState.setVoiceRoamingType(3);
            }
        }
        boolean isDataInService = currentServiceState.getDataRegState() == 0 ? true : VDBG;
        int dataRegType = currentServiceState.getRilDataRadioTechnology();
        if (!isDataInService) {
            return;
        }
        if (!currentServiceState.getDataRoaming()) {
            currentServiceState.setDataRoamingType(0);
        } else if (this.mPhone.isPhoneTypeGsm()) {
            if (!ServiceState.isGsm(dataRegType)) {
                currentServiceState.setDataRoamingType(1);
            } else if (isVoiceInService) {
                currentServiceState.setDataRoamingType(currentServiceState.getVoiceRoamingType());
            } else {
                currentServiceState.setDataRoamingType(1);
            }
        } else if (ServiceState.isCdma(dataRegType)) {
            if (isVoiceInService) {
                currentServiceState.setDataRoamingType(currentServiceState.getVoiceRoamingType());
            } else {
                currentServiceState.setDataRoamingType(1);
            }
        } else if (inSameCountry(currentServiceState.getDataOperatorNumeric())) {
            currentServiceState.setDataRoamingType(2);
        } else {
            currentServiceState.setDataRoamingType(3);
        }
    }

    private void setSignalStrengthDefaultValues() {
        this.mSignalStrength = new SignalStrength(true);
    }

    /* Access modifiers changed, original: protected */
    public String getHomeOperatorNumeric() {
        String numeric = ((TelephonyManager) this.mPhone.getContext().getSystemService("phone")).getSimOperatorNumericForPhone(this.mPhone.getPhoneId());
        if (this.mPhone.isPhoneTypeGsm() || !TextUtils.isEmpty(numeric)) {
            return numeric;
        }
        return SystemProperties.get(GsmCdmaPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC, "");
    }

    /* Access modifiers changed, original: protected */
    public int getPhoneId() {
        return this.mPhone.getPhoneId();
    }

    /* Access modifiers changed, original: protected */
    public void resetServiceStateInIwlanMode() {
        if (this.mCi.getRadioState() == RadioState.RADIO_OFF) {
            boolean resetIwlanRatVal = VDBG;
            log("set service state as POWER_OFF");
            if (18 == this.mNewSS.getRilDataRadioTechnology()) {
                log("pollStateDone: mNewSS = " + this.mNewSS);
                log("pollStateDone: reset iwlan RAT value");
                resetIwlanRatVal = true;
            }
            this.mNewSS.setStateOff();
            if (resetIwlanRatVal) {
                this.mNewSS.setRilDataRadioTechnology(18);
                this.mNewSS.setDataRegState(0);
                log("pollStateDone: mNewSS = " + this.mNewSS);
            }
        }
    }

    /* Access modifiers changed, original: protected|final */
    public final boolean alwaysOnHomeNetwork(BaseBundle b) {
        return b.getBoolean("force_home_network_bool");
    }

    private boolean isInNetwork(BaseBundle b, String network, String key) {
        String[] networks = b.getStringArray(key);
        if (networks == null || !Arrays.asList(networks).contains(network)) {
            return VDBG;
        }
        return true;
    }

    /* Access modifiers changed, original: protected|final */
    public final boolean isRoamingInGsmNetwork(BaseBundle b, String network) {
        return isInNetwork(b, network, "gsm_roaming_networks_string_array");
    }

    /* Access modifiers changed, original: protected|final */
    public final boolean isNonRoamingInGsmNetwork(BaseBundle b, String network) {
        return isInNetwork(b, network, "gsm_nonroaming_networks_string_array");
    }

    /* Access modifiers changed, original: protected|final */
    public final boolean isRoamingInCdmaNetwork(BaseBundle b, String network) {
        return isInNetwork(b, network, "cdma_roaming_networks_string_array");
    }

    /* Access modifiers changed, original: protected|final */
    public final boolean isNonRoamingInCdmaNetwork(BaseBundle b, String network) {
        return isInNetwork(b, network, "cdma_nonroaming_networks_string_array");
    }

    /* Access modifiers changed, original: protected */
    public int getCombinedRegState() {
        int regState = this.mSS.getVoiceRegState();
        int dataRegState = this.mSS.getDataRegState();
        if (regState != 1 || dataRegState != 0) {
            return regState;
        }
        log("getCombinedRegState: return STATE_IN_SERVICE as Data is in service");
        return dataRegState;
    }

    public boolean isDeviceShuttingDown() {
        return this.mDeviceShuttingDown;
    }

    public boolean isRatLte(int rat) {
        if (rat == 14 || rat == 19) {
            return true;
        }
        return VDBG;
    }

    private void setSendSmsMemoryFull() {
        boolean z = VDBG;
        if (TelBrand.IS_DCM) {
            String decryptState = SystemProperties.get("vold.decrypt", "0");
            log("Get decryptState is: " + decryptState);
            if (decryptState.equals("trigger_restart_min_framework")) {
                log("report encrypt state to WMS");
                this.mCi.reportDecryptStatus(VDBG, obtainMessage(EVENT_REPORT_SMS_MEMORY_FULL));
            } else {
                boolean isAvailable = isStorageAvailable();
                StringBuilder append = new StringBuilder().append("is sms really memory full:");
                if (!isAvailable) {
                    z = true;
                }
                log(append.append(z).toString());
                if (isAvailable) {
                    log("report decrypt state to WMS");
                    this.mCi.reportDecryptStatus(true, obtainMessage(EVENT_REPORT_SMS_MEMORY_FULL));
                }
            }
            this.mIsSmsMemRptSent = true;
        }
    }

    private boolean isStorageAvailable() {
        if (!TelBrand.IS_DCM || this.mPhone.mSmsStorageMonitor == null) {
            return true;
        }
        return this.mPhone.mSmsStorageMonitor.isStorageAvailable();
    }

    private int getLacOrDataLac() {
        if (this.mLAC != -1) {
            return this.mLAC;
        }
        if (this.mDataLAC != -1) {
            return this.mDataLAC;
        }
        return -1;
    }

    private int getCidOrDataCid() {
        if (this.mCid != -1) {
            return this.mCid;
        }
        if (this.mDataCid != -1) {
            return this.mDataCid;
        }
        return -1;
    }

    private void onOemSignalStrengthResult(AsyncResult ar) {
        SignalStrength oldSignalStrength = this.mSignalStrength;
        if (ar.exception != null || ar.result == null) {
            log("onSignalStrengthResult() Exception from RIL : " + ar.exception);
            this.mSignalStrength = new SignalStrength(true);
        } else {
            int gwRssi = 99;
            int gwBer = -1;
            int gwEcIo = -1;
            int lteSignalStrength = 99;
            int lteRsrp = Integer.MAX_VALUE;
            int lteRsrq = Integer.MAX_VALUE;
            int lteRssnr = Integer.MAX_VALUE;
            int lteCqi = Integer.MAX_VALUE;
            int lteActiveBand = Integer.MAX_VALUE;
            int[] ints = (int[]) ar.result;
            if (ints.length > 15) {
                gwRssi = ints[0] >= 0 ? ints[0] : 99;
                gwBer = ints[1];
                gwEcIo = ints[2];
                lteSignalStrength = ints[8];
                lteRsrp = ints[9];
                lteRsrq = ints[10];
                lteRssnr = ints[11];
                lteCqi = ints[12];
                lteActiveBand = ints[14];
            }
            log("rssi=" + gwRssi + ", bitErrorRate=" + gwBer + ", ecio=" + gwEcIo);
            log("lteSignalStrength=" + lteSignalStrength + ", lteRsrp=" + lteRsrp + ", lteRsrq=" + lteRsrq + ", lteRssnr=" + lteRssnr + ", lteCqi=" + lteCqi + ", lteActiveBand=" + lteActiveBand);
            this.mSignalStrength = new SignalStrength(gwRssi, gwBer, -1, -1, -1, -1, -1, lteSignalStrength, lteRsrp, lteRsrq, lteRssnr, lteCqi, -1, true, gwEcIo, lteActiveBand);
            this.mSignalStrength.validateInput();
        }
        if (!this.mSignalStrength.equals(oldSignalStrength)) {
            try {
                this.mPhone.notifySignalStrength();
            } catch (NullPointerException ex) {
                log("onSignalStrengthResult() Phone already destroyed: " + ex + "SignalStrength not notified");
            }
        }
    }

    private void onLteBandInfo(AsyncResult ar) {
        if (ar.exception == null && ar.result != null) {
            int lteBand;
            this.mLteActiveBand = ar.result.intValue();
            switch (this.mLteActiveBand) {
                case 120:
                    lteBand = 1;
                    break;
                case 121:
                    lteBand = 2;
                    break;
                case 122:
                    lteBand = 3;
                    break;
                case 123:
                    lteBand = 4;
                    break;
                case CallFailCause.INTERWORKING_UNSPECIFIED /*127*/:
                    lteBand = 8;
                    break;
                case 149:
                    lteBand = 41;
                    break;
                default:
                    lteBand = -1;
                    break;
            }
            if (this.mLteBand != lteBand) {
                this.mLteBand = lteBand;
                if (LTE_BAND_DISTINGUISHABLE == 2) {
                    Intent intent = new Intent(OemTelephonyIntents.ACTION_LTE_BAND_CHANGED);
                    intent.addFlags(536870912);
                    intent.putExtra(OemTelephonyIntents.EXTRA_LTE_BAND, lteBand);
                    this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
                }
            }
        }
    }
}
