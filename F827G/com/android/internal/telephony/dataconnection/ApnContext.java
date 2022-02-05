package com.android.internal.telephony.dataconnection;

import android.app.PendingIntent;
import android.content.Context;
import android.net.NetworkConfig;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelBrand;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class ApnContext {
    protected static final boolean DBG = false;
    public final String LOG_TAG;
    private ApnSetting mApnSetting;
    private final String mApnType;
    private final Context mContext;
    DcAsyncChannel mDcAc;
    private final DcTrackerBase mDcTracker;
    AtomicBoolean mDependencyMet;
    String mReason;
    PendingIntent mReconnectAlarmIntent;
    public final int priority;
    private ArrayList<ApnSetting> mWaitingApns = null;
    private final Object mRefCountLock = new Object();
    private int mRefCount = 0;
    private DctConstants.State mState = DctConstants.State.IDLE;
    AtomicBoolean mDataEnabled = new AtomicBoolean(false);
    private AtomicInteger mWaitingApnsPermanentFailureCountDown = new AtomicInteger(0);

    public ApnContext(Context context, String apnType, String logTag, NetworkConfig config, DcTrackerBase tracker) {
        this.mContext = context;
        this.mApnType = apnType;
        setReason(Phone.REASON_DATA_ENABLED);
        this.mDependencyMet = new AtomicBoolean(config.dependencyMet);
        this.priority = config.priority;
        this.LOG_TAG = logTag;
        this.mDcTracker = tracker;
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

    public synchronized PendingIntent getReconnectIntent() {
        return this.mReconnectAlarmIntent;
    }

    public synchronized void setReconnectIntent(PendingIntent intent) {
        this.mReconnectAlarmIntent = intent;
    }

    public synchronized ApnSetting getApnSetting() {
        log("getApnSetting: apnSetting=" + this.mApnSetting);
        return this.mApnSetting;
    }

    public synchronized void setApnSetting(ApnSetting apnSetting) {
        log("setApnSetting: apnSetting=" + apnSetting);
        this.mApnSetting = apnSetting;
    }

    public synchronized void setWaitingApns(ArrayList<ApnSetting> waitingApns) {
        this.mWaitingApns = waitingApns;
        this.mWaitingApnsPermanentFailureCountDown.set(this.mWaitingApns.size());
    }

    public int getWaitingApnsPermFailCount() {
        return this.mWaitingApnsPermanentFailureCountDown.get();
    }

    public void decWaitingApnsPermFailCount() {
        this.mWaitingApnsPermanentFailureCountDown.decrementAndGet();
    }

    public synchronized ApnSetting getNextWaitingApn() {
        ApnSetting apn;
        ArrayList<ApnSetting> list = this.mWaitingApns;
        apn = null;
        if (list != null && !list.isEmpty()) {
            apn = list.get(0);
        }
        return apn;
    }

    public synchronized void removeWaitingApn(ApnSetting apn) {
        if (this.mWaitingApns != null) {
            this.mWaitingApns.remove(apn);
        }
    }

    public synchronized ArrayList<ApnSetting> getWaitingApns() {
        return this.mWaitingApns;
    }

    public synchronized void setState(DctConstants.State s) {
        if (TelBrand.IS_KDDI) {
            DctConstants.State state = this.mState;
            this.mState = s;
            if (s != state) {
                this.mDcTracker.setStateCarNavi(this);
            }
        } else {
            this.mState = s;
        }
        if (this.mState == DctConstants.State.FAILED && this.mWaitingApns != null) {
            this.mWaitingApns.clear();
        }
    }

    public synchronized DctConstants.State getState() {
        return this.mState;
    }

    public boolean isDisconnected() {
        DctConstants.State currentState = getState();
        return currentState == DctConstants.State.IDLE || currentState == DctConstants.State.FAILED;
    }

    public synchronized void setReason(String reason) {
        this.mReason = reason;
    }

    public synchronized String getReason() {
        return this.mReason;
    }

    public boolean isReady() {
        return this.mDataEnabled.get() && this.mDependencyMet.get();
    }

    public boolean isConnectable() {
        return isReady() && (this.mState == DctConstants.State.IDLE || this.mState == DctConstants.State.SCANNING || this.mState == DctConstants.State.RETRYING || this.mState == DctConstants.State.FAILED);
    }

    public boolean isConnectedOrConnecting() {
        return isReady() && (this.mState == DctConstants.State.CONNECTED || this.mState == DctConstants.State.CONNECTING || this.mState == DctConstants.State.SCANNING || this.mState == DctConstants.State.RETRYING);
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
        String provisioningApn = this.mContext.getResources().getString(17039437);
        if (TextUtils.isEmpty(provisioningApn) || this.mApnSetting == null || this.mApnSetting.apn == null) {
            return false;
        }
        return this.mApnSetting.apn.equals(provisioningApn);
    }

    public void incRefCount() {
        synchronized (this.mRefCountLock) {
            int i = this.mRefCount;
            this.mRefCount = i + 1;
            if (i == 0) {
                this.mDcTracker.setEnabled(this.mDcTracker.apnTypeToId(this.mApnType), true);
            }
        }
    }

    public void decRefCount() {
        synchronized (this.mRefCountLock) {
            if (this.mRefCount > 0) {
                int i = this.mRefCount;
                this.mRefCount = i - 1;
                if (i == 1) {
                    this.mDcTracker.setEnabled(this.mDcTracker.apnTypeToId(this.mApnType), false);
                }
            }
            log("Ignoring defCount request as mRefCount is: " + this.mRefCount);
        }
    }

    public synchronized String toString() {
        return "{mApnType=" + this.mApnType + " mState=" + getState() + " mWaitingApns={" + this.mWaitingApns + "} mWaitingApnsPermanentFailureCountDown=" + this.mWaitingApnsPermanentFailureCountDown + " mApnSetting={" + this.mApnSetting + "} mReason=" + this.mReason + " mDataEnabled=" + this.mDataEnabled + " mDependencyMet=" + this.mDependencyMet + "}";
    }

    protected void log(String s) {
        Rlog.d(this.LOG_TAG, "[ApnContext:" + this.mApnType + "] " + s);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("ApnContext: " + toString());
    }
}
