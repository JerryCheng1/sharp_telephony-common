package com.android.internal.telephony.sip;

import android.os.SystemClock;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.UUSInfo;

/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public abstract class SipConnectionBase extends Connection {
    private static final boolean DBG = true;
    private static final String LOG_TAG = "SipConnBase";
    private static final boolean VDBG = false;
    private long mConnectTime;
    private long mConnectTimeReal;
    private long mDisconnectTime;
    private long mHoldingStartTime;
    private int mNextPostDialChar;
    private String mPostDialString;
    private long mDuration = -1;
    private int mCause = 0;
    private Connection.PostDialState mPostDialState = Connection.PostDialState.NOT_STARTED;
    private long mCreateTime = System.currentTimeMillis();

    protected abstract Phone getPhone();

    /* JADX INFO: Access modifiers changed from: package-private */
    public SipConnectionBase(String dialString) {
        log("SipConnectionBase: ctor dialString=" + dialString);
        this.mPostDialString = PhoneNumberUtils.extractPostDialPortion(dialString);
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void setState(Call.State state) {
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

    @Override // com.android.internal.telephony.Connection
    public long getCreateTime() {
        return this.mCreateTime;
    }

    @Override // com.android.internal.telephony.Connection
    public long getConnectTime() {
        return this.mConnectTime;
    }

    @Override // com.android.internal.telephony.Connection
    public long getDisconnectTime() {
        return this.mDisconnectTime;
    }

    @Override // com.android.internal.telephony.Connection
    public long getDurationMillis() {
        if (this.mConnectTimeReal == 0) {
            return 0L;
        }
        if (this.mDuration < 0) {
            return SystemClock.elapsedRealtime() - this.mConnectTimeReal;
        }
        return this.mDuration;
    }

    @Override // com.android.internal.telephony.Connection
    public long getHoldDurationMillis() {
        if (getState() != Call.State.HOLDING) {
            return 0L;
        }
        return SystemClock.elapsedRealtime() - this.mHoldingStartTime;
    }

    @Override // com.android.internal.telephony.Connection
    public int getDisconnectCause() {
        return this.mCause;
    }

    void setDisconnectCause(int cause) {
        log("setDisconnectCause: prev=" + this.mCause + " new=" + cause);
        this.mCause = cause;
    }

    @Override // com.android.internal.telephony.Connection
    public Connection.PostDialState getPostDialState() {
        return this.mPostDialState;
    }

    @Override // com.android.internal.telephony.Connection
    public void proceedAfterWaitChar() {
        log("proceedAfterWaitChar: ignore");
    }

    @Override // com.android.internal.telephony.Connection
    public void proceedAfterWildChar(String str) {
        log("proceedAfterWildChar: ignore");
    }

    @Override // com.android.internal.telephony.Connection
    public void cancelPostDial() {
        log("cancelPostDial: ignore");
    }

    @Override // com.android.internal.telephony.Connection
    public String getRemainingPostDialString() {
        if (this.mPostDialState != Connection.PostDialState.CANCELLED && this.mPostDialState != Connection.PostDialState.COMPLETE && this.mPostDialString != null && this.mPostDialString.length() > this.mNextPostDialChar) {
            return this.mPostDialString.substring(this.mNextPostDialChar);
        }
        log("getRemaingPostDialString: ret empty string");
        return "";
    }

    private void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    @Override // com.android.internal.telephony.Connection
    public int getNumberPresentation() {
        return 1;
    }

    @Override // com.android.internal.telephony.Connection
    public UUSInfo getUUSInfo() {
        return null;
    }

    @Override // com.android.internal.telephony.Connection
    public int getPreciseDisconnectCause() {
        return 0;
    }

    @Override // com.android.internal.telephony.Connection
    public long getHoldingStartTime() {
        return this.mHoldingStartTime;
    }

    @Override // com.android.internal.telephony.Connection
    public long getConnectTimeReal() {
        return this.mConnectTimeReal;
    }

    @Override // com.android.internal.telephony.Connection
    public Connection getOrigConnection() {
        return null;
    }

    @Override // com.android.internal.telephony.Connection
    public boolean isMultiparty() {
        return false;
    }
}
