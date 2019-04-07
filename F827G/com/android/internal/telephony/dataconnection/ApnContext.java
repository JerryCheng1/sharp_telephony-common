package com.android.internal.telephony.dataconnection;

import android.app.PendingIntent;
import android.content.Context;
import android.net.NetworkConfig;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelBrand;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ApnContext {
    protected static final boolean DBG = false;
    public final String LOG_TAG;
    private ApnSetting mApnSetting;
    private final String mApnType;
    private final Context mContext;
    AtomicBoolean mDataEnabled;
    DcAsyncChannel mDcAc;
    private final DcTrackerBase mDcTracker;
    AtomicBoolean mDependencyMet;
    String mReason;
    PendingIntent mReconnectAlarmIntent;
    private int mRefCount = 0;
    private final Object mRefCountLock = new Object();
    private State mState;
    private ArrayList<ApnSetting> mWaitingApns = null;
    private AtomicInteger mWaitingApnsPermanentFailureCountDown;
    public final int priority;

    public ApnContext(Context context, String str, String str2, NetworkConfig networkConfig, DcTrackerBase dcTrackerBase) {
        this.mContext = context;
        this.mApnType = str;
        this.mState = State.IDLE;
        setReason(Phone.REASON_DATA_ENABLED);
        this.mDataEnabled = new AtomicBoolean(false);
        this.mDependencyMet = new AtomicBoolean(networkConfig.dependencyMet);
        this.mWaitingApnsPermanentFailureCountDown = new AtomicInteger(0);
        this.priority = networkConfig.priority;
        this.LOG_TAG = str2;
        this.mDcTracker = dcTrackerBase;
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

    public void decWaitingApnsPermFailCount() {
        this.mWaitingApnsPermanentFailureCountDown.decrementAndGet();
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("ApnContext: " + toString());
    }

    public ApnSetting getApnSetting() {
        ApnSetting apnSetting;
        synchronized (this) {
            log("getApnSetting: apnSetting=" + this.mApnSetting);
            apnSetting = this.mApnSetting;
        }
        return apnSetting;
    }

    public String getApnType() {
        return this.mApnType;
    }

    public DcAsyncChannel getDcAc() {
        DcAsyncChannel dcAsyncChannel;
        synchronized (this) {
            dcAsyncChannel = this.mDcAc;
        }
        return dcAsyncChannel;
    }

    public boolean getDependencyMet() {
        return this.mDependencyMet.get();
    }

    public ApnSetting getNextWaitingApn() {
        ApnSetting apnSetting;
        synchronized (this) {
            ArrayList arrayList = this.mWaitingApns;
            apnSetting = null;
            if (!(arrayList == null || arrayList.isEmpty())) {
                apnSetting = (ApnSetting) arrayList.get(0);
            }
        }
        return apnSetting;
    }

    public String getReason() {
        String str;
        synchronized (this) {
            str = this.mReason;
        }
        return str;
    }

    public PendingIntent getReconnectIntent() {
        PendingIntent pendingIntent;
        synchronized (this) {
            pendingIntent = this.mReconnectAlarmIntent;
        }
        return pendingIntent;
    }

    public State getState() {
        State state;
        synchronized (this) {
            state = this.mState;
        }
        return state;
    }

    public ArrayList<ApnSetting> getWaitingApns() {
        ArrayList arrayList;
        synchronized (this) {
            arrayList = this.mWaitingApns;
        }
        return arrayList;
    }

    public int getWaitingApnsPermFailCount() {
        return this.mWaitingApnsPermanentFailureCountDown.get();
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

    public boolean isConnectable() {
        return isReady() && (this.mState == State.IDLE || this.mState == State.SCANNING || this.mState == State.RETRYING || this.mState == State.FAILED);
    }

    public boolean isConnectedOrConnecting() {
        return isReady() && (this.mState == State.CONNECTED || this.mState == State.CONNECTING || this.mState == State.SCANNING || this.mState == State.RETRYING);
    }

    public boolean isDisconnected() {
        State state = getState();
        return state == State.IDLE || state == State.FAILED;
    }

    public boolean isEnabled() {
        return this.mDataEnabled.get();
    }

    public boolean isProvisioningApn() {
        String string = this.mContext.getResources().getString(17039437);
        return (TextUtils.isEmpty(string) || this.mApnSetting == null || this.mApnSetting.apn == null) ? false : this.mApnSetting.apn.equals(string);
    }

    public boolean isReady() {
        return this.mDataEnabled.get() && this.mDependencyMet.get();
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(this.LOG_TAG, "[ApnContext:" + this.mApnType + "] " + str);
    }

    public void removeWaitingApn(ApnSetting apnSetting) {
        synchronized (this) {
            if (this.mWaitingApns != null) {
                this.mWaitingApns.remove(apnSetting);
            }
        }
    }

    public void setApnSetting(ApnSetting apnSetting) {
        synchronized (this) {
            log("setApnSetting: apnSetting=" + apnSetting);
            this.mApnSetting = apnSetting;
        }
    }

    public void setDataConnectionAc(DcAsyncChannel dcAsyncChannel) {
        synchronized (this) {
            this.mDcAc = dcAsyncChannel;
        }
    }

    public void setDependencyMet(boolean z) {
        this.mDependencyMet.set(z);
    }

    public void setEnabled(boolean z) {
        this.mDataEnabled.set(z);
    }

    public void setReason(String str) {
        synchronized (this) {
            this.mReason = str;
        }
    }

    public void setReconnectIntent(PendingIntent pendingIntent) {
        synchronized (this) {
            this.mReconnectAlarmIntent = pendingIntent;
        }
    }

    public void setState(State state) {
        synchronized (this) {
            if (TelBrand.IS_KDDI) {
                State state2 = this.mState;
                this.mState = state;
                if (state != state2) {
                    this.mDcTracker.setStateCarNavi(this);
                }
            } else {
                this.mState = state;
            }
            if (this.mState == State.FAILED && this.mWaitingApns != null) {
                this.mWaitingApns.clear();
            }
        }
    }

    public void setWaitingApns(ArrayList<ApnSetting> arrayList) {
        synchronized (this) {
            this.mWaitingApns = arrayList;
            this.mWaitingApnsPermanentFailureCountDown.set(this.mWaitingApns.size());
        }
    }

    public String toString() {
        String str;
        synchronized (this) {
            str = "{mApnType=" + this.mApnType + " mState=" + getState() + " mWaitingApns={" + this.mWaitingApns + "} mWaitingApnsPermanentFailureCountDown=" + this.mWaitingApnsPermanentFailureCountDown + " mApnSetting={" + this.mApnSetting + "} mReason=" + this.mReason + " mDataEnabled=" + this.mDataEnabled + " mDependencyMet=" + this.mDependencyMet + "}";
        }
        return str;
    }
}
