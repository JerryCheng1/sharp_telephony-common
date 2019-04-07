package com.android.internal.telephony;

import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Telephony.BaseMmsColumns;
import android.telephony.CellInfo;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TimeUtils;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

public abstract class ServiceStateTracker extends Handler {
    protected static final String ACTION_RADIO_OFF = "android.intent.action.ACTION_RADIO_OFF";
    protected static final boolean DBG = true;
    public static final int DEFAULT_GPRS_CHECK_PERIOD_MILLIS = 60000;
    protected static final int EVENT_CDMA_PRL_VERSION_CHANGED = 40;
    protected static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 39;
    protected static final int EVENT_CHANGE_IMS_STATE = 45;
    protected static final int EVENT_CHECK_REPORT_GPRS = 22;
    protected static final int EVENT_ERI_FILE_LOADED = 36;
    protected static final int EVENT_GET_CELL_INFO_LIST = 43;
    protected static final int EVENT_GET_DATA_LOC_DONE = 10001;
    protected static final int EVENT_GET_LOC_DONE = 15;
    protected static final int EVENT_GET_LOC_DONE_CDMA = 31;
    protected static final int EVENT_GET_PREFERRED_NETWORK_TYPE = 19;
    protected static final int EVENT_GET_SIGNAL_STRENGTH = 3;
    protected static final int EVENT_GET_SIGNAL_STRENGTH_CDMA = 29;
    public static final int EVENT_ICC_CHANGED = 42;
    protected static final int EVENT_IMS_STATE_CHANGED = 46;
    protected static final int EVENT_IMS_STATE_DONE = 47;
    protected static final int EVENT_LOCATION_UPDATES_ENABLED = 18;
    protected static final int EVENT_LTE_BAND_INFO = 10002;
    protected static final int EVENT_NETWORK_STATE_CHANGED = 2;
    protected static final int EVENT_NETWORK_STATE_CHANGED_CDMA = 30;
    protected static final int EVENT_NITZ_TIME = 11;
    protected static final int EVENT_NV_LOADED = 33;
    protected static final int EVENT_NV_READY = 35;
    protected static final int EVENT_OEM_SIGNAL_STRENGTH_UPDATE = 10000;
    protected static final int EVENT_OTA_PROVISION_STATUS_CHANGE = 37;
    protected static final int EVENT_POLL_SIGNAL_STRENGTH = 10;
    protected static final int EVENT_POLL_SIGNAL_STRENGTH_CDMA = 28;
    protected static final int EVENT_POLL_STATE_CDMA_SUBSCRIPTION = 34;
    protected static final int EVENT_POLL_STATE_GPRS = 5;
    protected static final int EVENT_POLL_STATE_NETWORK_SELECTION_MODE = 14;
    protected static final int EVENT_POLL_STATE_OPERATOR = 6;
    protected static final int EVENT_POLL_STATE_OPERATOR_CDMA = 25;
    protected static final int EVENT_POLL_STATE_REGISTRATION = 4;
    protected static final int EVENT_POLL_STATE_REGISTRATION_CDMA = 24;
    protected static final int EVENT_RADIO_AVAILABLE = 13;
    protected static final int EVENT_RADIO_ON = 41;
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
    protected static final int EVENT_SPEECH_CODEC = 10003;
    protected static final int EVENT_UNSOL_CELL_INFO_LIST = 44;
    protected static final String[] GMT_COUNTRY_CODES = new String[]{"bf", "ci", "eh", "fo", "gb", "gh", "gm", "gn", "gw", "ie", "is", "lr", "ma", "ml", "mr", "pt", "sl", "sn", BaseMmsColumns.STATUS, "tg"};
    private static final long LAST_CELL_INFO_LIST_MAX_AGE_MS = 2000;
    public static final int OTASP_NEEDED = 2;
    public static final int OTASP_NOT_NEEDED = 3;
    public static final int OTASP_UNINITIALIZED = 0;
    public static final int OTASP_UNKNOWN = 1;
    protected static final int POLL_PERIOD_MILLIS = 20000;
    protected static final String PROP_FORCE_ROAMING = "telephony.test.forceRoaming";
    protected static final String REGISTRATION_DENIED_AUTH = "Authentication Failure";
    protected static final String REGISTRATION_DENIED_GEN = "General";
    protected static final String TIMEZONE_PROPERTY = "persist.sys.timezone";
    protected static final boolean VDBG = false;
    protected boolean mAlarmSwitch = false;
    protected RegistrantList mAttachedRegistrants = new RegistrantList();
    protected final CellInfo mCellInfo;
    protected CommandsInterface mCi;
    protected RegistrantList mDataRegStateOrRatChangedRegistrants = new RegistrantList();
    protected RegistrantList mDataRoamingOffRegistrants = new RegistrantList();
    protected RegistrantList mDataRoamingOnRegistrants = new RegistrantList();
    protected boolean mDesiredPowerState;
    protected RegistrantList mDetachedRegistrants = new RegistrantList();
    protected boolean mDeviceShuttingDown = false;
    protected boolean mDontPollSignalStrength = false;
    protected IccRecords mIccRecords = null;
    private boolean mImsRegistered = false;
    protected boolean mImsRegistrationOnOff = false;
    protected IntentFilter mIntentFilter = null;
    protected boolean mIwlanRatAvailable = false;
    protected List<CellInfo> mLastCellInfoList = null;
    protected long mLastCellInfoListTime;
    private SignalStrength mLastSignalStrength = null;
    protected RegistrantList mNetworkAttachedRegistrants = new RegistrantList();
    protected ServiceState mNewSS = new ServiceState();
    protected final OnSubscriptionsChangedListener mOnSubscriptionsChangedListener = new OnSubscriptionsChangedListener() {
        private int previousSubId = -1;

        public void onSubscriptionsChanged() {
            ServiceStateTracker.this.log("SubscriptionListener.onSubscriptionInfoChanged");
            int subId = ServiceStateTracker.this.mPhoneBase.getSubId();
            if (ServiceStateTracker.this.mPhoneBase.getSubId() != subId && SubscriptionManager.isValidSubscriptionId(subId)) {
                ServiceStateTracker.this.mPhoneBase.notifyCallForwardingIndicator();
                SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(ServiceStateTracker.this.mPhoneBase.getContext());
                String string = defaultSharedPreferences.getString(PhoneBase.NETWORK_SELECTION_NAME_KEY, "");
                String string2 = defaultSharedPreferences.getString(PhoneBase.NETWORK_SELECTION_KEY, "");
                if (!TextUtils.isEmpty(string) || !TextUtils.isEmpty(string2)) {
                    Editor edit = defaultSharedPreferences.edit();
                    edit.putString(PhoneBase.NETWORK_SELECTION_NAME_KEY + subId, string);
                    edit.putString(PhoneBase.NETWORK_SELECTION_KEY + subId, string2);
                    edit.remove(PhoneBase.NETWORK_SELECTION_NAME_KEY);
                    edit.remove(PhoneBase.NETWORK_SELECTION_KEY);
                    edit.commit();
                }
            }
        }
    };
    protected boolean mPendingRadioPowerOffAfterDataOff = false;
    protected int mPendingRadioPowerOffAfterDataOffTag = 0;
    protected PhoneBase mPhoneBase;
    protected int[] mPollingContext;
    protected boolean mPowerOffDelayNeed = true;
    protected RegistrantList mPsRestrictDisabledRegistrants = new RegistrantList();
    protected RegistrantList mPsRestrictEnabledRegistrants = new RegistrantList();
    protected PendingIntent mRadioOffIntent = null;
    public RestrictedState mRestrictedState = new RestrictedState();
    public ServiceState mSS = new ServiceState();
    protected SignalStrength mSignalStrength = new SignalStrength();
    protected SubscriptionManager mSubscriptionManager;
    private TelephonyManager mTelephonyManager;
    protected UiccCardApplication mUiccApplcation = null;
    protected UiccController mUiccController = null;
    protected boolean mVoiceCapable;
    protected RegistrantList mVoiceRoamingOffRegistrants = new RegistrantList();
    protected RegistrantList mVoiceRoamingOnRegistrants = new RegistrantList();
    private boolean mWantContinuousLocationUpdates;
    private boolean mWantSingleLocationUpdate;

    private class CellInfoResult {
        List<CellInfo> list;
        Object lockObj;

        private CellInfoResult() {
            this.lockObj = new Object();
        }

        /* synthetic */ CellInfoResult(ServiceStateTracker serviceStateTracker, AnonymousClass1 anonymousClass1) {
            this();
        }
    }

    protected ServiceStateTracker(PhoneBase phoneBase, CommandsInterface commandsInterface, CellInfo cellInfo) {
        this.mPhoneBase = phoneBase;
        this.mCellInfo = cellInfo;
        this.mCi = commandsInterface;
        this.mVoiceCapable = this.mPhoneBase.getContext().getResources().getBoolean(17956947);
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 42, null);
        this.mCi.setOnSignalStrengthUpdate(this, 12, null);
        this.mCi.registerForCellInfoList(this, 44, null);
        this.mSubscriptionManager = SubscriptionManager.from(phoneBase.getContext());
        this.mSubscriptionManager.addOnSubscriptionsChangedListener(this.mOnSubscriptionsChangedListener);
        this.mTelephonyManager = (TelephonyManager) this.mPhoneBase.getContext().getSystemService("phone");
        this.mTelephonyManager.setDataNetworkTypeForPhone(this.mPhoneBase.getPhoneId(), 0);
        this.mCi.registerForImsNetworkStateChanged(this, 46, null);
    }

    /* Access modifiers changed, original: protected */
    public void cancelPollState() {
        this.mPollingContext = new int[1];
    }

    /* Access modifiers changed, original: protected */
    public void checkCorrectThread() {
        if (Thread.currentThread() != getLooper().getThread()) {
            throw new RuntimeException("ServiceStateTracker must be used from within one thread");
        }
    }

    public void disableLocationUpdates() {
        this.mWantContinuousLocationUpdates = false;
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mCi.setLocationUpdates(false, null);
        }
    }

    /* Access modifiers changed, original: protected */
    public void disableSingleLocationUpdate() {
        this.mWantSingleLocationUpdate = false;
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mCi.setLocationUpdates(false, null);
        }
    }

    public void dispose() {
        this.mCi.unSetOnSignalStrengthUpdate(this);
        this.mUiccController.unregisterForIccChanged(this);
        this.mCi.unregisterForCellInfoList(this);
        this.mSubscriptionManager.removeOnSubscriptionsChangedListener(this.mOnSubscriptionsChangedListener);
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("ServiceStateTracker:");
        printWriter.println(" mSS=" + this.mSS);
        printWriter.println(" mNewSS=" + this.mNewSS);
        printWriter.println(" mCellInfo=" + this.mCellInfo);
        printWriter.println(" mRestrictedState=" + this.mRestrictedState);
        printWriter.println(" mPollingContext=" + this.mPollingContext);
        printWriter.println(" mDesiredPowerState=" + this.mDesiredPowerState);
        printWriter.println(" mDontPollSignalStrength=" + this.mDontPollSignalStrength);
        printWriter.println(" mPendingRadioPowerOffAfterDataOff=" + this.mPendingRadioPowerOffAfterDataOff);
        printWriter.println(" mPendingRadioPowerOffAfterDataOffTag=" + this.mPendingRadioPowerOffAfterDataOffTag);
        printWriter.flush();
    }

    public void enableLocationUpdates() {
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mWantContinuousLocationUpdates = true;
            this.mCi.setLocationUpdates(true, obtainMessage(18));
        }
    }

    public void enableSingleLocationUpdate() {
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mWantSingleLocationUpdate = true;
            this.mCi.setLocationUpdates(true, obtainMessage(18));
        }
    }

    public List<CellInfo> getAllCellInfo() {
        List<CellInfo> list = null;
        CellInfoResult cellInfoResult = new CellInfoResult(this, null);
        if (this.mCi.getRilVersion() < 8) {
            log("SST.getAllCellInfo(): not implemented");
            cellInfoResult.list = null;
        } else if (!isCallerOnDifferentThread()) {
            log("SST.getAllCellInfo(): return last, same thread can't block");
            cellInfoResult.list = this.mLastCellInfoList;
        } else if (SystemClock.elapsedRealtime() - this.mLastCellInfoListTime > LAST_CELL_INFO_LIST_MAX_AGE_MS) {
            Message obtainMessage = obtainMessage(43, cellInfoResult);
            synchronized (cellInfoResult.lockObj) {
                cellInfoResult.list = null;
                this.mCi.getCellInfoList(obtainMessage);
                try {
                    cellInfoResult.lockObj.wait(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else {
            log("SST.getAllCellInfo(): return last, back to back calls");
            cellInfoResult.list = this.mLastCellInfoList;
        }
        synchronized (cellInfoResult.lockObj) {
            if (cellInfoResult.list != null) {
                log("SST.getAllCellInfo(): X size=" + cellInfoResult.list.size() + " list=" + cellInfoResult.list);
                list = cellInfoResult.list;
            } else {
                log("SST.getAllCellInfo(): X size=0 list=null");
            }
        }
        return list;
    }

    public abstract int getCurrentDataConnectionState();

    public boolean getDesiredPowerState() {
        return this.mDesiredPowerState;
    }

    /* Access modifiers changed, original: protected */
    public String getHomeOperatorNumeric() {
        return ((TelephonyManager) this.mPhoneBase.getContext().getSystemService("phone")).getSimOperatorNumericForPhone(this.mPhoneBase.getPhoneId());
    }

    public abstract Phone getPhone();

    /* Access modifiers changed, original: protected */
    public int getPhoneId() {
        return this.mPhoneBase.getPhoneId();
    }

    public SignalStrength getSignalStrength() {
        SignalStrength signalStrength;
        synchronized (this.mCellInfo) {
            signalStrength = this.mSignalStrength;
        }
        return signalStrength;
    }

    public String getSystemProperty(String str, String str2) {
        return TelephonyManager.getTelephonyProperty(this.mPhoneBase.getPhoneId(), str, str2);
    }

    public void handleMessage(Message message) {
        AsyncResult asyncResult;
        switch (message.what) {
            case 38:
                synchronized (this) {
                    if (this.mPendingRadioPowerOffAfterDataOff && message.arg1 == this.mPendingRadioPowerOffAfterDataOffTag) {
                        log("EVENT_SET_RADIO_OFF, turn radio off now.");
                        hangupAndPowerOff();
                        this.mPendingRadioPowerOffAfterDataOffTag++;
                        this.mPendingRadioPowerOffAfterDataOff = false;
                    } else {
                        log("EVENT_SET_RADIO_OFF is stale arg1=" + message.arg1 + "!= tag=" + this.mPendingRadioPowerOffAfterDataOffTag);
                    }
                }
                return;
            case 42:
                onUpdateIccAvailability();
                return;
            case 43:
                asyncResult = (AsyncResult) message.obj;
                CellInfoResult cellInfoResult = (CellInfoResult) asyncResult.userObj;
                synchronized (cellInfoResult.lockObj) {
                    if (asyncResult.exception != null) {
                        log("EVENT_GET_CELL_INFO_LIST: error ret null, e=" + asyncResult.exception);
                        cellInfoResult.list = null;
                    } else {
                        cellInfoResult.list = (List) asyncResult.result;
                    }
                    this.mLastCellInfoListTime = SystemClock.elapsedRealtime();
                    this.mLastCellInfoList = cellInfoResult.list;
                    cellInfoResult.lockObj.notify();
                }
                return;
            case 44:
                asyncResult = (AsyncResult) message.obj;
                if (asyncResult.exception != null) {
                    log("EVENT_UNSOL_CELL_INFO_LIST: error ignoring, e=" + asyncResult.exception);
                    return;
                }
                List list = (List) asyncResult.result;
                log("EVENT_UNSOL_CELL_INFO_LIST: size=" + list.size() + " list=" + list);
                this.mLastCellInfoListTime = SystemClock.elapsedRealtime();
                this.mLastCellInfoList = list;
                this.mPhoneBase.notifyCellInfo(list);
                return;
            case 46:
                this.mCi.getImsRegistrationState(obtainMessage(47));
                return;
            case 47:
                asyncResult = (AsyncResult) message.obj;
                if (asyncResult.exception == null) {
                    this.mImsRegistered = ((int[]) ((int[]) asyncResult.result))[0] == 1;
                    return;
                }
                return;
            default:
                log("Unhandled message with number: " + message.what);
                return;
        }
    }

    public abstract void handlePollStateResult(int i, AsyncResult asyncResult);

    public abstract void hangupAndPowerOff();

    /* Access modifiers changed, original: protected */
    public boolean inSameCountry(String str) {
        if (TextUtils.isEmpty(str) || str.length() < 5) {
            return false;
        }
        String homeOperatorNumeric = getHomeOperatorNumeric();
        if (TextUtils.isEmpty(homeOperatorNumeric) || homeOperatorNumeric.length() < 5) {
            return false;
        }
        String substring = str.substring(0, 3);
        homeOperatorNumeric = homeOperatorNumeric.substring(0, 3);
        substring = MccTable.countryCodeForMcc(Integer.parseInt(substring));
        homeOperatorNumeric = MccTable.countryCodeForMcc(Integer.parseInt(homeOperatorNumeric));
        if (substring.isEmpty() || homeOperatorNumeric.isEmpty()) {
            return false;
        }
        boolean equals = homeOperatorNumeric.equals(substring);
        return !equals ? ("us".equals(homeOperatorNumeric) && "vi".equals(substring)) ? true : ("vi".equals(homeOperatorNumeric) && "us".equals(substring)) ? true : equals : equals;
    }

    /* Access modifiers changed, original: protected */
    public boolean isCallerOnDifferentThread() {
        return Thread.currentThread() != getLooper().getThread();
    }

    public abstract boolean isConcurrentVoiceAndDataAllowed();

    public boolean isImsRegistered() {
        return this.mImsRegistered;
    }

    /* Access modifiers changed, original: protected */
    public boolean isIwlanFeatureAvailable() {
        boolean z = this.mPhoneBase.getContext().getResources().getBoolean(17957029);
        log("Iwlan feature available = " + z);
        return z;
    }

    public boolean isRatLte(int i) {
        return i == 14 || i == 19;
    }

    public abstract void log(String str);

    public abstract void loge(String str);

    /* Access modifiers changed, original: protected */
    public void notifyDataRegStateRilRadioTechnologyChanged() {
        int rilDataRadioTechnology = this.mSS.getRilDataRadioTechnology();
        int dataRegState = this.mSS.getDataRegState();
        log("notifyDataRegStateRilRadioTechnologyChanged: drs=" + dataRegState + " rat=" + rilDataRadioTechnology);
        this.mTelephonyManager.setDataNetworkTypeForPhone(this.mPhoneBase.getPhoneId(), rilDataRadioTechnology);
        this.mDataRegStateOrRatChangedRegistrants.notifyResult(new Pair(Integer.valueOf(dataRegState), Integer.valueOf(rilDataRadioTechnology)));
    }

    /* Access modifiers changed, original: protected */
    public boolean notifySignalStrength() {
        boolean z;
        synchronized (this.mCellInfo) {
            if (this.mSignalStrength.equals(this.mLastSignalStrength)) {
                z = false;
            } else {
                try {
                    this.mPhoneBase.notifySignalStrength();
                    z = true;
                } catch (NullPointerException e) {
                    loge("updateSignalStrength() Phone already destroyed: " + e + "SignalStrength not notified");
                    z = false;
                }
            }
        }
        return z;
    }

    /* Access modifiers changed, original: protected */
    public boolean onSignalStrengthResult(AsyncResult asyncResult, boolean z) {
        SignalStrength signalStrength = this.mSignalStrength;
        if (asyncResult.exception != null || asyncResult.result == null) {
            log("onSignalStrengthResult() Exception from RIL : " + asyncResult.exception);
            this.mSignalStrength = new SignalStrength(z);
        } else {
            this.mSignalStrength = (SignalStrength) asyncResult.result;
            this.mSignalStrength.validateInput();
            this.mSignalStrength.setGsm(z);
        }
        return notifySignalStrength();
    }

    public abstract void onUpdateIccAvailability();

    public abstract void pollState();

    /* JADX WARNING: Missing block: B:31:?, code skipped:
            return;
     */
    public void powerOffRadioSafely(com.android.internal.telephony.dataconnection.DcTrackerBase r5) {
        /*
        r4 = this;
        monitor-enter(r4);
        r0 = r4.mPendingRadioPowerOffAfterDataOff;	 Catch:{ all -> 0x005c }
        if (r0 != 0) goto L_0x005a;
    L_0x0005:
        r0 = r4.mPhoneBase;	 Catch:{ all -> 0x005c }
        r0 = r0.getContext();	 Catch:{ all -> 0x005c }
        r0 = r0.getResources();	 Catch:{ all -> 0x005c }
        r1 = 17236030; // 0x107003e float:2.4795758E-38 double:8.5157303E-317;
        r1 = r0.getStringArray(r1);	 Catch:{ all -> 0x005c }
        r0 = r4.mSS;	 Catch:{ all -> 0x005c }
        r2 = r0.getOperatorNumeric();	 Catch:{ all -> 0x005c }
        if (r1 == 0) goto L_0x0047;
    L_0x001e:
        if (r2 == 0) goto L_0x0047;
    L_0x0020:
        r0 = 0;
    L_0x0021:
        r3 = r1.length;	 Catch:{ all -> 0x005c }
        if (r0 >= r3) goto L_0x0047;
    L_0x0024:
        r3 = r1[r0];	 Catch:{ all -> 0x005c }
        r3 = r2.equals(r3);	 Catch:{ all -> 0x005c }
        if (r3 == 0) goto L_0x008e;
    L_0x002c:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x005c }
        r0.<init>();	 Catch:{ all -> 0x005c }
        r1 = "Not disconnecting data for ";
        r0 = r0.append(r1);	 Catch:{ all -> 0x005c }
        r0 = r0.append(r2);	 Catch:{ all -> 0x005c }
        r0 = r0.toString();	 Catch:{ all -> 0x005c }
        r4.log(r0);	 Catch:{ all -> 0x005c }
        r4.hangupAndPowerOff();	 Catch:{ all -> 0x005c }
        monitor-exit(r4);	 Catch:{ all -> 0x005c }
    L_0x0046:
        return;
    L_0x0047:
        r0 = r5.isDisconnected();	 Catch:{ all -> 0x005c }
        if (r0 == 0) goto L_0x005f;
    L_0x004d:
        r0 = "radioTurnedOff";
        r5.cleanUpAllConnections(r0);	 Catch:{ all -> 0x005c }
        r0 = "Data disconnected, turn off radio right away.";
        r4.log(r0);	 Catch:{ all -> 0x005c }
        r4.hangupAndPowerOff();	 Catch:{ all -> 0x005c }
    L_0x005a:
        monitor-exit(r4);	 Catch:{ all -> 0x005c }
        goto L_0x0046;
    L_0x005c:
        r0 = move-exception;
        monitor-exit(r4);	 Catch:{ all -> 0x005c }
        throw r0;
    L_0x005f:
        r0 = "radioTurnedOff";
        r5.cleanUpAllConnections(r0);	 Catch:{ all -> 0x005c }
        r0 = android.os.Message.obtain(r4);	 Catch:{ all -> 0x005c }
        r1 = 38;
        r0.what = r1;	 Catch:{ all -> 0x005c }
        r1 = r4.mPendingRadioPowerOffAfterDataOffTag;	 Catch:{ all -> 0x005c }
        r1 = r1 + 1;
        r4.mPendingRadioPowerOffAfterDataOffTag = r1;	 Catch:{ all -> 0x005c }
        r0.arg1 = r1;	 Catch:{ all -> 0x005c }
        r2 = 30000; // 0x7530 float:4.2039E-41 double:1.4822E-319;
        r0 = r4.sendMessageDelayed(r0, r2);	 Catch:{ all -> 0x005c }
        if (r0 == 0) goto L_0x0085;
    L_0x007c:
        r0 = "Wait upto 30s for data to disconnect, then turn off radio.";
        r4.log(r0);	 Catch:{ all -> 0x005c }
        r0 = 1;
        r4.mPendingRadioPowerOffAfterDataOff = r0;	 Catch:{ all -> 0x005c }
        goto L_0x005a;
    L_0x0085:
        r0 = "Cannot send delayed Msg, turn off radio right away.";
        r4.log(r0);	 Catch:{ all -> 0x005c }
        r4.hangupAndPowerOff();	 Catch:{ all -> 0x005c }
        goto L_0x005a;
    L_0x008e:
        r0 = r0 + 1;
        goto L_0x0021;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.ServiceStateTracker.powerOffRadioSafely(com.android.internal.telephony.dataconnection.DcTrackerBase):void");
    }

    /* Access modifiers changed, original: protected */
    public void processIwlanToWwanTransition(ServiceState serviceState) {
        if (isIwlanFeatureAvailable() && serviceState.getRilDataRadioTechnology() != 18 && serviceState.getRilDataRadioTechnology() != 0 && serviceState.getDataRegState() == 0 && this.mIwlanRatAvailable) {
            log("pollStateDone: Wifi connected and moved out of iwlan and wwan is attached.");
            this.mAttachedRegistrants.notifyRegistrants();
        }
    }

    public boolean processPendingRadioPowerOffAfterDataOff() {
        synchronized (this) {
            if (this.mPendingRadioPowerOffAfterDataOff) {
                log("Process pending request to turn radio off.");
                this.mPendingRadioPowerOffAfterDataOffTag++;
                hangupAndPowerOff();
                this.mPendingRadioPowerOffAfterDataOff = false;
                return true;
            }
            return false;
        }
    }

    public void reRegisterNetwork(Message message) {
        this.mCi.getPreferredNetworkType(obtainMessage(19, message));
    }

    public void registerForDataConnectionAttached(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        this.mAttachedRegistrants.add(registrant);
        if (getCurrentDataConnectionState() == 0) {
            registrant.notifyRegistrant();
        }
    }

    public void registerForDataConnectionDetached(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        this.mDetachedRegistrants.add(registrant);
        if (getCurrentDataConnectionState() != 0) {
            registrant.notifyRegistrant();
        }
    }

    public void registerForDataRegStateOrRatChanged(Handler handler, int i, Object obj) {
        this.mDataRegStateOrRatChangedRegistrants.add(new Registrant(handler, i, obj));
        notifyDataRegStateRilRadioTechnologyChanged();
    }

    public void registerForDataRoamingOff(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        this.mDataRoamingOffRegistrants.add(registrant);
        if (!this.mSS.getDataRoaming()) {
            registrant.notifyRegistrant();
        }
    }

    public void registerForDataRoamingOn(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        this.mDataRoamingOnRegistrants.add(registrant);
        if (this.mSS.getDataRoaming()) {
            registrant.notifyRegistrant();
        }
    }

    public void registerForNetworkAttached(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        this.mNetworkAttachedRegistrants.add(registrant);
        if (this.mSS.getVoiceRegState() == 0) {
            registrant.notifyRegistrant();
        }
    }

    public void registerForPsRestrictedDisabled(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        this.mPsRestrictDisabledRegistrants.add(registrant);
        if (this.mRestrictedState.isPsRestricted()) {
            registrant.notifyRegistrant();
        }
    }

    public void registerForPsRestrictedEnabled(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        this.mPsRestrictEnabledRegistrants.add(registrant);
        if (this.mRestrictedState.isPsRestricted()) {
            registrant.notifyRegistrant();
        }
    }

    public void registerForVoiceRoamingOff(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        this.mVoiceRoamingOffRegistrants.add(registrant);
        if (!this.mSS.getVoiceRoaming()) {
            registrant.notifyRegistrant();
        }
    }

    public void registerForVoiceRoamingOn(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        this.mVoiceRoamingOnRegistrants.add(registrant);
        if (this.mSS.getVoiceRoaming()) {
            registrant.notifyRegistrant();
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void requestShutdown() {
        if (!this.mDeviceShuttingDown) {
            this.mDeviceShuttingDown = true;
            this.mDesiredPowerState = false;
            setPowerStateToDesired();
        }
    }

    /* Access modifiers changed, original: protected */
    public void resetServiceStateInIwlanMode() {
        if (this.mCi.getRadioState() == RadioState.RADIO_OFF) {
            int i;
            log("set service state as POWER_OFF");
            if (isIwlanFeatureAvailable() && 18 == this.mNewSS.getRilDataRadioTechnology()) {
                log("pollStateDone: mNewSS = " + this.mNewSS);
                log("pollStateDone: reset iwlan RAT value");
                i = 1;
            } else {
                i = 0;
            }
            this.mNewSS.setStateOff();
            if (i != 0) {
                this.mNewSS.setRilDataRadioTechnology(18);
                this.mNewSS.setDataRegState(0);
                log("pollStateDone: mNewSS = " + this.mNewSS);
            }
        }
    }

    public abstract void setImsRegistrationState(boolean z);

    public abstract void setPowerStateToDesired();

    public void setRadioPower(boolean z) {
        this.mDesiredPowerState = z;
        setPowerStateToDesired();
    }

    public abstract void setRoamingType(ServiceState serviceState);

    /* Access modifiers changed, original: protected */
    public boolean shouldFixTimeZoneNow(PhoneBase phoneBase, String str, String str2, boolean z) {
        boolean z2 = true;
        try {
            int parseInt;
            int parseInt2 = Integer.parseInt(str.substring(0, 3));
            try {
                parseInt = Integer.parseInt(str2.substring(0, 3));
            } catch (Exception e) {
                parseInt = parseInt2 + 1;
            }
            boolean z3 = this.mUiccApplcation != null ? this.mUiccApplcation.getState() != AppState.APPSTATE_UNKNOWN : false;
            if ((!z3 || parseInt2 == parseInt) && !z) {
                z2 = false;
            }
            log("shouldFixTimeZoneNow: retVal=" + z2 + " iccCardExist=" + z3 + " operatorNumeric=" + str + " mcc=" + parseInt2 + " prevOperatorNumeric=" + str2 + " prevMcc=" + parseInt + " needToFixTimeZone=" + z + " ltod=" + TimeUtils.logTimeOfDay(System.currentTimeMillis()));
            return z2;
        } catch (Exception e2) {
            log("shouldFixTimeZoneNow: no mcc, operatorNumeric=" + str + " retVal=false");
            return false;
        }
    }

    public void unregisterForDataConnectionAttached(Handler handler) {
        this.mAttachedRegistrants.remove(handler);
    }

    public void unregisterForDataConnectionDetached(Handler handler) {
        this.mDetachedRegistrants.remove(handler);
    }

    public void unregisterForDataRegStateOrRatChanged(Handler handler) {
        this.mDataRegStateOrRatChangedRegistrants.remove(handler);
    }

    public void unregisterForDataRoamingOff(Handler handler) {
        this.mDataRoamingOffRegistrants.remove(handler);
    }

    public void unregisterForDataRoamingOn(Handler handler) {
        this.mDataRoamingOnRegistrants.remove(handler);
    }

    public void unregisterForNetworkAttached(Handler handler) {
        this.mNetworkAttachedRegistrants.remove(handler);
    }

    public void unregisterForPsRestrictedDisabled(Handler handler) {
        this.mPsRestrictDisabledRegistrants.remove(handler);
    }

    public void unregisterForPsRestrictedEnabled(Handler handler) {
        this.mPsRestrictEnabledRegistrants.remove(handler);
    }

    public void unregisterForVoiceRoamingOff(Handler handler) {
        this.mVoiceRoamingOffRegistrants.remove(handler);
    }

    public void unregisterForVoiceRoamingOn(Handler handler) {
        this.mVoiceRoamingOnRegistrants.remove(handler);
    }

    /* Access modifiers changed, original: protected */
    public void updateCarrierMccMncConfiguration(String str, String str2, Context context) {
        if ((str == null && !TextUtils.isEmpty(str2)) || (str != null && !str.equals(str2))) {
            log("update mccmnc=" + str + " fromServiceState=true");
            MccTable.updateMccMncConfiguration(context, str, true);
        }
    }

    /* Access modifiers changed, original: protected */
    public void updatePhoneObject() {
        if (this.mPhoneBase.getContext().getResources().getBoolean(17957009)) {
            this.mPhoneBase.updatePhoneObject(this.mSS.getRilVoiceRadioTechnology());
        }
    }

    public abstract void updateSpnDisplay();

    /* Access modifiers changed, original: protected */
    public void useDataRegStateForDataOnlyDevices() {
        if (!this.mVoiceCapable) {
            log("useDataRegStateForDataOnlyDevice: VoiceRegState=" + this.mNewSS.getVoiceRegState() + " DataRegState=" + this.mNewSS.getDataRegState());
            this.mNewSS.setVoiceRegState(this.mNewSS.getDataRegState());
        }
    }
}
