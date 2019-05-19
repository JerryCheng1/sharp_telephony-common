package com.android.internal.telephony.dataconnection;

import android.app.PendingIntent;
import android.net.NetworkCapabilities;
import android.net.NetworkConfig;
import android.net.NetworkRequest;
import android.telephony.Rlog;
import android.text.TextUtils;
import android.util.LocalLog;
import android.util.SparseIntArray;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneInternalInterface;
import com.android.internal.telephony.RetryManager;
import com.android.internal.telephony.TelBrand;
import com.android.internal.util.IndentingPrintWriter;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ApnContext {
    protected static final boolean DBG = false;
    private static final int MAX_HISTORY_LOG_COUNT = 4;
    private static final String SLOG_TAG = "ApnContext";
    public final String LOG_TAG;
    private ApnSetting mApnSetting;
    private final String mApnType;
    private boolean mConcurrentVoiceAndDataAllowed;
    private final AtomicInteger mConnectionGeneration = new AtomicInteger(0);
    AtomicBoolean mDataEnabled;
    DcAsyncChannel mDcAc;
    private final DcTracker mDcTracker;
    AtomicBoolean mDependencyMet;
    private final ArrayDeque<LocalLog> mHistoryLogs = new ArrayDeque();
    private final ArrayList<LocalLog> mLocalLogs = new ArrayList();
    private final Phone mPhone;
    String mReason;
    PendingIntent mReconnectAlarmIntent;
    private int mRefCount = 0;
    private final Object mRefCountLock = new Object();
    private final SparseIntArray mRetriesLeftPerErrorCode = new SparseIntArray();
    private final RetryManager mRetryManager;
    private State mState;
    public final int priority;

    public ApnContext(Phone phone, String apnType, String logTag, NetworkConfig config, DcTracker tracker) {
        this.mPhone = phone;
        this.mApnType = apnType;
        this.mState = State.IDLE;
        setReason(PhoneInternalInterface.REASON_DATA_ENABLED);
        this.mDataEnabled = new AtomicBoolean(DBG);
        this.mDependencyMet = new AtomicBoolean(config.dependencyMet);
        this.priority = config.priority;
        this.LOG_TAG = logTag;
        this.mDcTracker = tracker;
        this.mRetryManager = new RetryManager(phone, apnType);
    }

    public String getApnType() {
        return this.mApnType;
    }

    public synchronized DcAsyncChannel getDcAc() {
        return this.mDcAc;
    }

    public synchronized void setDataConnectionAc(DcAsyncChannel dcac) {
        this.mDcAc = dcac;
    }

    public synchronized void releaseDataConnection(String reason) {
        if (this.mDcAc != null) {
            this.mDcAc.tearDown(this, reason, null);
            this.mDcAc = null;
        }
        setState(State.IDLE);
    }

    public synchronized PendingIntent getReconnectIntent() {
        return this.mReconnectAlarmIntent;
    }

    public synchronized void setReconnectIntent(PendingIntent intent) {
        this.mReconnectAlarmIntent = intent;
    }

    public synchronized ApnSetting getApnSetting() {
        return this.mApnSetting;
    }

    public synchronized void setApnSetting(ApnSetting apnSetting) {
        this.mApnSetting = apnSetting;
    }

    public synchronized void setWaitingApns(ArrayList<ApnSetting> waitingApns) {
        this.mRetryManager.setWaitingApns(waitingApns);
    }

    public ApnSetting getNextApnSetting() {
        return this.mRetryManager.getNextApnSetting();
    }

    public void setModemSuggestedDelay(long delay) {
        this.mRetryManager.setModemSuggestedDelay(delay);
    }

    public long getDelayForNextApn(boolean failFastEnabled) {
        if (!TelBrand.IS_DCM) {
            return this.mRetryManager.getDelayForNextApn(failFastEnabled);
        }
        return this.mRetryManager.getDelayForNextApn(!failFastEnabled ? isFastRetryReason() : true);
    }

    public void markApnPermanentFailed(ApnSetting apn) {
        this.mRetryManager.markApnPermanentFailed(apn);
    }

    public ArrayList<ApnSetting> getWaitingApns() {
        return this.mRetryManager.getWaitingApns();
    }

    public synchronized void setConcurrentVoiceAndDataAllowed(boolean allowed) {
        this.mConcurrentVoiceAndDataAllowed = allowed;
    }

    public synchronized boolean isConcurrentVoiceAndDataAllowed() {
        return this.mConcurrentVoiceAndDataAllowed;
    }

    public synchronized void setState(State s) {
        if (TelBrand.IS_KDDI) {
            State state = this.mState;
            this.mState = s;
            if (s != state) {
                this.mDcTracker.setStateCarNavi(this);
            }
        } else {
            this.mState = s;
        }
        if (this.mState == State.FAILED && this.mRetryManager.getWaitingApns() != null) {
            this.mRetryManager.getWaitingApns().clear();
        }
    }

    public synchronized State getState() {
        return this.mState;
    }

    public boolean isDisconnected() {
        State currentState = getState();
        if (currentState == State.IDLE || currentState == State.FAILED) {
            return true;
        }
        return DBG;
    }

    public synchronized void setReason(String reason) {
        this.mReason = reason;
    }

    public synchronized String getReason() {
        return this.mReason;
    }

    public boolean isReady() {
        return this.mDataEnabled.get() ? this.mDependencyMet.get() : DBG;
    }

    public boolean isConnectable() {
        return isReady() ? (this.mState == State.IDLE || this.mState == State.SCANNING || this.mState == State.RETRYING || this.mState == State.FAILED) ? true : DBG : DBG;
    }

    private boolean isFastRetryReason() {
        if (PhoneInternalInterface.REASON_NW_TYPE_CHANGED.equals(this.mReason)) {
            return true;
        }
        return PhoneInternalInterface.REASON_APN_CHANGED.equals(this.mReason);
    }

    public boolean isConnectedOrConnecting() {
        return isReady() ? (this.mState == State.CONNECTED || this.mState == State.CONNECTING || this.mState == State.SCANNING || this.mState == State.RETRYING) ? true : DBG : DBG;
    }

    public void setEnabled(boolean enabled) {
        this.mDataEnabled.set(enabled);
    }

    public boolean isEnabled() {
        return this.mDataEnabled.get();
    }

    public void setDependencyMet(boolean met) {
        this.mDependencyMet.set(met);
    }

    public boolean getDependencyMet() {
        return this.mDependencyMet.get();
    }

    public boolean isProvisioningApn() {
        String provisioningApn = this.mPhone.getContext().getResources().getString(17039440);
        if (TextUtils.isEmpty(provisioningApn) || this.mApnSetting == null || this.mApnSetting.apn == null) {
            return DBG;
        }
        return this.mApnSetting.apn.equals(provisioningApn);
    }

    public void requestLog(String str) {
        synchronized (this.mRefCountLock) {
            for (LocalLog l : this.mLocalLogs) {
                l.log(str);
            }
        }
    }

    public void incRefCount(LocalLog log) {
        synchronized (this.mRefCountLock) {
            if (this.mLocalLogs.contains(log)) {
                log.log("ApnContext.incRefCount has duplicate add - " + this.mRefCount);
            } else {
                this.mLocalLogs.add(log);
                log.log("ApnContext.incRefCount - " + this.mRefCount);
            }
            int i = this.mRefCount;
            this.mRefCount = i + 1;
            if (i == 0) {
                this.mDcTracker.setEnabled(apnIdForApnName(this.mApnType), true);
            }
        }
    }

    public void decRefCount(LocalLog log) {
        synchronized (this.mRefCountLock) {
            if (this.mLocalLogs.remove(log)) {
                log.log("ApnContext.decRefCount - " + this.mRefCount);
                this.mHistoryLogs.addFirst(log);
                while (this.mHistoryLogs.size() > 4) {
                    this.mHistoryLogs.removeLast();
                }
            } else {
                log.log("ApnContext.decRefCount didn't find log - " + this.mRefCount);
            }
            int i = this.mRefCount;
            this.mRefCount = i - 1;
            if (i == 1) {
                this.mDcTracker.setEnabled(apnIdForApnName(this.mApnType), DBG);
            }
            if (this.mRefCount < 0) {
                log.log("ApnContext.decRefCount went to " + this.mRefCount);
                this.mRefCount = 0;
            }
        }
    }

    public void resetErrorCodeRetries() {
        requestLog("ApnContext.resetErrorCodeRetries");
        String[] config = this.mPhone.getContext().getResources().getStringArray(17236045);
        synchronized (this.mRetriesLeftPerErrorCode) {
            this.mRetriesLeftPerErrorCode.clear();
            for (String c : config) {
                String[] errorValue = c.split(",");
                if (errorValue == null || errorValue.length != 2) {
                    log("Exception parsing config_retries_per_error_code: " + c);
                } else {
                    try {
                        int errorCode = Integer.parseInt(errorValue[0]);
                        int count = Integer.parseInt(errorValue[1]);
                        if (count > 0 && errorCode > 0) {
                            this.mRetriesLeftPerErrorCode.put(errorCode, count);
                        }
                    } catch (NumberFormatException e) {
                        log("Exception parsing config_retries_per_error_code: " + e);
                    }
                }
            }
        }
    }

    public boolean restartOnError(int errorCode) {
        int retriesLeft;
        boolean result = DBG;
        synchronized (this.mRetriesLeftPerErrorCode) {
            retriesLeft = this.mRetriesLeftPerErrorCode.get(errorCode);
            switch (retriesLeft) {
                case 0:
                    break;
                case 1:
                    resetErrorCodeRetries();
                    result = true;
                    break;
                default:
                    this.mRetriesLeftPerErrorCode.put(errorCode, retriesLeft - 1);
                    result = DBG;
                    break;
            }
        }
        requestLog("ApnContext.restartOnError(" + errorCode + ") found " + retriesLeft + " and returned " + result);
        return result;
    }

    public int incAndGetConnectionGeneration() {
        return this.mConnectionGeneration.incrementAndGet();
    }

    public int getConnectionGeneration() {
        return this.mConnectionGeneration.get();
    }

    public long getInterApnDelay(boolean failFastEnabled) {
        if (!TelBrand.IS_DCM) {
            return this.mRetryManager.getInterApnDelay(failFastEnabled);
        }
        return this.mRetryManager.getInterApnDelay(!failFastEnabled ? isFastRetryReason() : true);
    }

    public static int apnIdForType(int networkType) {
        switch (networkType) {
            case 0:
                return 0;
            case 2:
                return 1;
            case 3:
                return 2;
            case 4:
                return 3;
            case 10:
                return 6;
            case 11:
                return 5;
            case 12:
                return 7;
            case 14:
                return 8;
            case 15:
                return 9;
            default:
                return -1;
        }
    }

    public static int apnIdForNetworkRequest(NetworkRequest nr) {
        NetworkCapabilities nc = nr.networkCapabilities;
        if (nc.getTransportTypes().length > 0 && !nc.hasTransport(0)) {
            return -1;
        }
        int apnId = -1;
        boolean error = DBG;
        if (nc.hasCapability(12)) {
            apnId = 0;
        }
        if (nc.hasCapability(0)) {
            if (apnId != -1) {
                error = true;
            }
            apnId = 1;
        }
        if (nc.hasCapability(1)) {
            if (apnId != -1) {
                error = true;
            }
            apnId = 2;
        }
        if (nc.hasCapability(2)) {
            if (apnId != -1) {
                error = true;
            }
            apnId = 3;
        }
        if (nc.hasCapability(3)) {
            if (apnId != -1) {
                error = true;
            }
            apnId = 6;
        }
        if (nc.hasCapability(4)) {
            if (apnId != -1) {
                error = true;
            }
            apnId = 5;
        }
        if (nc.hasCapability(5)) {
            if (apnId != -1) {
                error = true;
            }
            apnId = 7;
        }
        if (nc.hasCapability(7)) {
            if (apnId != -1) {
                error = true;
            }
            apnId = 8;
        }
        if (nc.hasCapability(8)) {
            if (apnId != -1) {
                error = true;
            }
            Rlog.d(SLOG_TAG, "RCS APN type not yet supported");
        }
        if (nc.hasCapability(9)) {
            if (apnId != -1) {
                error = true;
            }
            Rlog.d(SLOG_TAG, "XCAP APN type not yet supported");
        }
        if (nc.hasCapability(10)) {
            if (apnId != -1) {
                error = true;
            }
            apnId = 9;
        }
        if (error) {
            Rlog.d(SLOG_TAG, "Multiple apn types specified in request - result is unspecified!");
        }
        if (apnId == -1) {
            Rlog.d(SLOG_TAG, "Unsupported NetworkRequest in Telephony: nr=" + nr);
        }
        return apnId;
    }

    public static int apnIdForApnName(String type) {
        if (type.equals("default")) {
            return 0;
        }
        if (type.equals("mms")) {
            return 1;
        }
        if (type.equals("supl")) {
            return 2;
        }
        if (type.equals("dun")) {
            return 3;
        }
        if (type.equals("hipri")) {
            return 4;
        }
        if (type.equals("ims")) {
            return 5;
        }
        if (type.equals("fota")) {
            return 6;
        }
        if (type.equals("cbs")) {
            return 7;
        }
        if (type.equals("ia")) {
            return 8;
        }
        if (type.equals("emergency")) {
            return 9;
        }
        return -1;
    }

    private static String apnNameForApnId(int id) {
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
                Rlog.d(SLOG_TAG, "Unknown id (" + id + ") in apnIdToType");
                return "default";
        }
    }

    public synchronized String toString() {
        return "{mApnType=" + this.mApnType + " mState=" + getState() + " mWaitingApns={" + this.mRetryManager.getWaitingApns() + "}" + " mApnSetting={" + this.mApnSetting + "} mReason=" + this.mReason + " mDataEnabled=" + this.mDataEnabled + " mDependencyMet=" + this.mDependencyMet + "}";
    }

    private void log(String s) {
        Rlog.d(this.LOG_TAG, "[ApnContext:" + this.mApnType + "] " + s);
    }

    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        synchronized (this.mRefCountLock) {
            pw.println(toString());
            pw.increaseIndent();
            for (LocalLog l : this.mLocalLogs) {
                l.dump(fd, pw, args);
            }
            if (this.mHistoryLogs.size() > 0) {
                pw.println("Historical Logs:");
            }
            for (LocalLog l2 : this.mHistoryLogs) {
                l2.dump(fd, pw, args);
            }
            pw.decreaseIndent();
            pw.println("mRetryManager={" + this.mRetryManager.toString() + "}");
        }
    }
}
