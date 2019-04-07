package com.android.internal.telephony.sip;

import android.os.SystemClock;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Connection.PostDialState;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.UUSInfo;

abstract class SipConnectionBase extends Connection {
    private static final boolean DBG = true;
    private static final String LOG_TAG = "SipConnBase";
    private static final boolean VDBG = false;
    private int mCause = 0;
    private long mConnectTime;
    private long mConnectTimeReal;
    private long mCreateTime;
    private long mDisconnectTime;
    private long mDuration = -1;
    private long mHoldingStartTime;
    private int mNextPostDialChar;
    private PostDialState mPostDialState = PostDialState.NOT_STARTED;
    private String mPostDialString;

    SipConnectionBase(String str) {
        log("SipConnectionBase: ctor dialString=" + str);
        this.mPostDialString = PhoneNumberUtils.extractPostDialPortion(str);
        this.mCreateTime = System.currentTimeMillis();
    }

    private void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    public void cancelPostDial() {
        log("cancelPostDial: ignore");
    }

    public long getConnectTime() {
        return this.mConnectTime;
    }

    public long getConnectTimeReal() {
        return this.mConnectTimeReal;
    }

    public long getCreateTime() {
        return this.mCreateTime;
    }

    public int getDisconnectCause() {
        return this.mCause;
    }

    public long getDisconnectTime() {
        return this.mDisconnectTime;
    }

    public long getDurationMillis() {
        return this.mConnectTimeReal == 0 ? 0 : this.mDuration < 0 ? SystemClock.elapsedRealtime() - this.mConnectTimeReal : this.mDuration;
    }

    public long getHoldDurationMillis() {
        return getState() != State.HOLDING ? 0 : SystemClock.elapsedRealtime() - this.mHoldingStartTime;
    }

    public long getHoldingStartTime() {
        return this.mHoldingStartTime;
    }

    public int getNumberPresentation() {
        return 1;
    }

    public Connection getOrigConnection() {
        return null;
    }

    public abstract Phone getPhone();

    public PostDialState getPostDialState() {
        return this.mPostDialState;
    }

    public int getPreciseDisconnectCause() {
        return 0;
    }

    public String getRemainingPostDialString() {
        if (this.mPostDialState != PostDialState.CANCELLED && this.mPostDialState != PostDialState.COMPLETE && this.mPostDialString != null && this.mPostDialString.length() > this.mNextPostDialChar) {
            return this.mPostDialString.substring(this.mNextPostDialChar);
        }
        log("getRemaingPostDialString: ret empty string");
        return "";
    }

    public UUSInfo getUUSInfo() {
        return null;
    }

    public boolean isMultiparty() {
        return false;
    }

    public void proceedAfterWaitChar() {
        log("proceedAfterWaitChar: ignore");
    }

    public void proceedAfterWildChar(String str) {
        log("proceedAfterWildChar: ignore");
    }

    /* Access modifiers changed, original: 0000 */
    public void setDisconnectCause(int i) {
        log("setDisconnectCause: prev=" + this.mCause + " new=" + i);
        this.mCause = i;
    }

    /* Access modifiers changed, original: protected */
    public void setState(State state) {
        log("setState: state=" + state);
        switch (state) {
            case ACTIVE:
                if (this.mConnectTime == 0) {
                    this.mConnectTimeReal = SystemClock.elapsedRealtime();
                    this.mConnectTime = System.currentTimeMillis();
                    return;
                }
                return;
            case DISCONNECTED:
                this.mDuration = getDurationMillis();
                this.mDisconnectTime = System.currentTimeMillis();
                return;
            case HOLDING:
                this.mHoldingStartTime = SystemClock.elapsedRealtime();
                return;
            default:
                return;
        }
    }
}
