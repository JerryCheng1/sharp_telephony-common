package com.android.internal.telephony.gsm;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.SystemClock;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.UiccCardApplication;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class GsmConnection extends Connection {
    private static final boolean DBG = true;
    static final int EVENT_DTMF_DONE = 1;
    static final int EVENT_NEXT_POST_DIAL = 3;
    static final int EVENT_PAUSE_DONE = 2;
    static final int EVENT_PAUSE_POST_DIAL = 5;
    static final int EVENT_WAKE_LOCK_TIMEOUT = 4;
    private static final String LOG_TAG = "GsmConnection";
    static final int PAUSE_DELAY_MILLIS = 3000;
    static final int PAUSE_DELAY_POST_MILLIS = 70;
    static final int WAKE_LOCK_TIMEOUT_MILLIS = 60000;
    boolean mAcceptCallPending;
    int mCause;
    long mDisconnectTime;
    boolean mDisconnected;
    Handler mHandler;
    boolean mHangupCallPending;
    int mIndex;
    int mNextPostDialChar;
    Connection mOrigConnection;
    GsmCallTracker mOwner;
    GsmCall mParent;
    private PowerManager.WakeLock mPartialWakeLock;
    Connection.PostDialState mPostDialState;
    String mPostDialString;
    int mPreciseCause;
    private String[] mSpeechCodeState;
    UUSInfo mUusInfo;
    String redirectingNum;

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    class MyHandler extends Handler {
        MyHandler(Looper l) {
            super(l);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            if (!TelBrand.IS_SBM) {
                switch (msg.what) {
                    case 1:
                    case 2:
                    case 3:
                        GsmConnection.this.processNextPostDialChar();
                        return;
                    case 4:
                        GsmConnection.this.releaseWakeLock();
                        return;
                    default:
                        return;
                }
            } else {
                GsmConnection.logd("GsmConnection() handleMessage is (%d)", Integer.valueOf(msg.what));
                switch (msg.what) {
                    case 1:
                        GsmConnection.this.mHandler.sendMessageDelayed(GsmConnection.this.mHandler.obtainMessage(5), 70L);
                        return;
                    case 2:
                    case 3:
                    case 5:
                        GsmConnection.this.processNextPostDialChar();
                        return;
                    case 4:
                        GsmConnection.this.releaseWakeLock();
                        return;
                    default:
                        return;
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public GsmConnection(Context context, DriverCall dc, GsmCallTracker ct, int index) {
        this.mCause = 0;
        this.mPostDialState = Connection.PostDialState.NOT_STARTED;
        this.mPreciseCause = 0;
        this.mHangupCallPending = false;
        this.mAcceptCallPending = false;
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        if (!TelBrand.IS_DCM) {
            this.mAddress = dc.number;
        } else if (TextUtils.isEmpty(dc.number)) {
            this.mAddress = dc.number;
            this.redirectingNum = null;
        } else if (dc.number.indexOf(38) == -1) {
            this.mAddress = dc.number;
            this.redirectingNum = null;
        } else {
            String[] a = dc.number.split("&");
            this.mAddress = a[0];
            this.redirectingNum = a[1];
            log("GsmConnection: address is " + this.mAddress);
            log("GsmConnection: redirectingNum is " + this.redirectingNum);
        }
        this.mIsIncoming = dc.isMT;
        this.mCreateTime = System.currentTimeMillis();
        this.mCnapName = dc.name;
        this.mCnapNamePresentation = dc.namePresentation;
        this.mNumberPresentation = dc.numberPresentation;
        this.mUusInfo = dc.uusInfo;
        logd("GsmConnection() mIsIncoming is %s", Boolean.valueOf(this.mIsIncoming));
        logd("GsmConnection() mNumberPresentation is %d", Integer.valueOf(this.mNumberPresentation));
        logd("GsmConnection() mAddress is %s", this.mAddress);
        if (this.mIsIncoming && this.mNumberPresentation == 1 && TextUtils.isEmpty(this.mAddress)) {
            logd("GsmConnection() sets PRESENTATION_UNKNOWN to mNumberPresentation");
            this.mNumberPresentation = 3;
        }
        this.mIndex = index;
        this.mParent = parentFromDCState(dc.state);
        this.mParent.attach(this, dc);
        if (TelBrand.IS_SBM) {
            logd("[SEQ]GsmConnection.GsmConnection(" + dc.number + ") IDLE->" + getState());
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public GsmConnection(Context context, String dialString, GsmCallTracker ct, GsmCall parent) {
        this.mCause = 0;
        this.mPostDialState = Connection.PostDialState.NOT_STARTED;
        this.mPreciseCause = 0;
        this.mHangupCallPending = false;
        this.mAcceptCallPending = false;
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mDialString = dialString;
        logd("GsmConnection() dialString = (%s)", dialString);
        String dialString2 = formatDialString(dialString);
        logd("GsmConnection() formated dialString = (%s)", dialString2);
        this.mAddress = PhoneNumberUtils.extractNetworkPortionAlt(dialString2);
        this.mPostDialString = PhoneNumberUtils.extractPostDialPortion(dialString2);
        this.mIndex = -1;
        this.mIsIncoming = false;
        this.mCnapName = null;
        this.mCnapNamePresentation = 1;
        this.mNumberPresentation = 1;
        this.mCreateTime = System.currentTimeMillis();
        this.mParent = parent;
        parent.attachFake(this, Call.State.DIALING);
        if (TelBrand.IS_SBM) {
            logd("[SEQ]GsmConnection.GsmConnection(" + dialString2 + ") IDLE->DIALING");
        }
    }

    public void dispose() {
    }

    static boolean equalsHandlesNulls(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean compareTo(DriverCall c) {
        if ((!this.mIsIncoming && !c.isMT) || this.mOrigConnection != null) {
            return true;
        }
        String address_old = null;
        if (TelBrand.IS_DCM) {
            address_old = this.mAddress;
            if (!TextUtils.isEmpty(this.redirectingNum)) {
                address_old = address_old + "&" + this.redirectingNum;
            }
        }
        String cAddress = PhoneNumberUtils.stringFromStringAndTOA(c.number, c.TOA);
        return TelBrand.IS_DCM ? this.mIsIncoming == c.isMT && equalsHandlesNulls(address_old, cAddress) : this.mIsIncoming == c.isMT && equalsHandlesNulls(this.mAddress, cAddress);
    }

    @Override // com.android.internal.telephony.Connection
    public GsmCall getCall() {
        return this.mParent;
    }

    @Override // com.android.internal.telephony.Connection
    public long getDisconnectTime() {
        return this.mDisconnectTime;
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

    @Override // com.android.internal.telephony.Connection
    public Call.State getState() {
        return this.mDisconnected ? Call.State.DISCONNECTED : super.getState();
    }

    public String[] getSpeechCodeState() {
        log("getSpeechCodeState() start");
        return this.mSpeechCodeState;
    }

    @Override // com.android.internal.telephony.Connection
    public void hangup() throws CallStateException {
        if (!this.mDisconnected) {
            this.mOwner.hangup(this);
            return;
        }
        throw new CallStateException("disconnected");
    }

    @Override // com.android.internal.telephony.Connection
    public void separate() throws CallStateException {
        if (!this.mDisconnected) {
            this.mOwner.separate(this);
            return;
        }
        throw new CallStateException("disconnected");
    }

    @Override // com.android.internal.telephony.Connection
    public Connection.PostDialState getPostDialState() {
        return this.mPostDialState;
    }

    @Override // com.android.internal.telephony.Connection
    public void proceedAfterWaitChar() {
        if (TelBrand.IS_SBM) {
            if (this.mPostDialState != Connection.PostDialState.WAIT) {
                Rlog.w(LOG_TAG, "GsmConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WAIT but was " + this.mPostDialState);
                return;
            }
        } else if (!(this.mPostDialState == Connection.PostDialState.WAIT || this.mPostDialState == Connection.PostDialState.WAIT_EX)) {
            Rlog.w(LOG_TAG, "GsmConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WAIT but was " + this.mPostDialState);
            return;
        }
        setPostDialState(Connection.PostDialState.STARTED);
        processNextPostDialChar();
    }

    @Override // com.android.internal.telephony.Connection
    public void proceedAfterWildChar(String str) {
        if (this.mPostDialState != Connection.PostDialState.WILD) {
            Rlog.w(LOG_TAG, "GsmConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WILD but was " + this.mPostDialState);
            return;
        }
        setPostDialState(Connection.PostDialState.STARTED);
        this.mPostDialString = str + this.mPostDialString.substring(this.mNextPostDialChar);
        this.mNextPostDialChar = 0;
        log(new StringBuilder().append("proceedAfterWildChar: new postDialString is ").append(this.mPostDialString).toString());
        processNextPostDialChar();
    }

    @Override // com.android.internal.telephony.Connection
    public void cancelPostDial() {
        setPostDialState(Connection.PostDialState.CANCELLED);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onHangupLocal() {
        this.mCause = 3;
        this.mPreciseCause = 0;
    }

    int disconnectCauseFromCode(int causeCode) {
        switch (causeCode) {
            case 1:
                return 25;
            case 17:
                return 4;
            case 34:
            case 41:
            case 42:
            case CallFailCause.CHANNEL_NOT_AVAIL /* 44 */:
            case 49:
            case 58:
                return 5;
            case 68:
                return 15;
            case 240:
                return 20;
            case 241:
                return 21;
            case 244:
                return 45;
            case 245:
                return 46;
            case 246:
                return 47;
            case 325:
                return 92;
            case 326:
                return 93;
            default:
                GSMPhone phone = this.mOwner.mPhone;
                int serviceState = phone.getServiceState().getState();
                UiccCardApplication cardApp = phone.getUiccCardApplication();
                IccCardApplicationStatus.AppState uiccAppState = cardApp != null ? cardApp.getState() : IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN;
                if (serviceState == 3) {
                    return 17;
                }
                if (serviceState == 1 || serviceState == 2) {
                    return 18;
                }
                if (uiccAppState != IccCardApplicationStatus.AppState.APPSTATE_READY) {
                    return 19;
                }
                if (causeCode != 65535) {
                    return causeCode == 16 ? 2 : 36;
                }
                if (phone.mSST.mRestrictedState.isCsRestricted()) {
                    return 22;
                }
                if (phone.mSST.mRestrictedState.isCsEmergencyRestricted()) {
                    return 24;
                }
                if (phone.mSST.mRestrictedState.isCsNormalRestricted()) {
                    return 23;
                }
                return 36;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onRemoteDisconnect(int causeCode) {
        this.mPreciseCause = causeCode;
        onDisconnect(disconnectCauseFromCode(causeCode));
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean onDisconnect(int cause) {
        boolean changed = false;
        this.mCause = cause;
        if (TelBrand.IS_DCM) {
            this.mHangupCallPending = false;
            this.mAcceptCallPending = false;
        }
        if (!this.mDisconnected) {
            this.mIndex = -1;
            this.mDisconnectTime = System.currentTimeMillis();
            this.mDuration = SystemClock.elapsedRealtime() - this.mConnectTimeReal;
            this.mDisconnected = true;
            Rlog.d(LOG_TAG, "onDisconnect: cause=" + cause);
            this.mOwner.mPhone.notifyDisconnect(this);
            if (this.mParent != null) {
                changed = this.mParent.connectionDisconnected(this);
            }
            this.mOrigConnection = null;
        }
        clearPostDialListeners();
        releaseWakeLock();
        return changed;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean update(DriverCall dc) {
        boolean changed;
        boolean z = true;
        boolean changed2 = false;
        boolean wasConnectingInOrOut = isConnectingInOrOut();
        boolean wasHolding = getState() == Call.State.HOLDING;
        GsmCall newParent = parentFromDCState(dc.state);
        String address_old = null;
        if (TelBrand.IS_DCM) {
            address_old = this.mAddress;
            if (!TextUtils.isEmpty(this.redirectingNum)) {
                address_old = this.mAddress + "&" + this.redirectingNum;
            }
        }
        if (this.mOrigConnection != null) {
            log("update: mOrigConnection is not null");
        } else {
            log(" mNumberConverted " + this.mNumberConverted);
            if (TelBrand.IS_DCM) {
                if (!equalsHandlesNulls(address_old, dc.number) && (!this.mNumberConverted || !equalsHandlesNulls(this.mConvertedNumber, dc.number))) {
                    log("update: phone # changed!");
                    if (TextUtils.isEmpty(dc.number)) {
                        this.mAddress = dc.number;
                        this.redirectingNum = null;
                    } else if (dc.number.indexOf(38) == -1) {
                        this.mAddress = dc.number;
                        this.redirectingNum = null;
                    } else {
                        String[] a = dc.number.split("&");
                        this.mAddress = a[0];
                        this.redirectingNum = a[1];
                        log("update: address is " + this.mAddress);
                        log("update: redirectingNum is " + this.redirectingNum);
                    }
                    changed2 = true;
                }
            } else if (!equalsHandlesNulls(this.mAddress, dc.number) && (!this.mNumberConverted || !equalsHandlesNulls(this.mConvertedNumber, dc.number))) {
                log("update: phone # changed!");
                this.mAddress = dc.number;
                changed2 = true;
            }
        }
        if (TextUtils.isEmpty(dc.name)) {
            if (!TextUtils.isEmpty(this.mCnapName)) {
                changed2 = true;
                this.mCnapName = "";
            }
        } else if (!dc.name.equals(this.mCnapName)) {
            changed2 = true;
            this.mCnapName = dc.name;
        }
        log("--dssds----" + this.mCnapName);
        this.mCnapNamePresentation = dc.namePresentation;
        this.mNumberPresentation = dc.numberPresentation;
        if (newParent != this.mParent) {
            if (this.mParent != null) {
                this.mParent.detach(this);
            }
            newParent.attach(this, dc);
            this.mParent = newParent;
            changed = true;
        } else {
            changed = changed2 || this.mParent.update(this, dc);
        }
        StringBuilder append = new StringBuilder().append("update: parent=").append(this.mParent).append(", hasNewParent=");
        if (newParent == this.mParent) {
            z = false;
        }
        log(append.append(z).append(", wasConnectingInOrOut=").append(wasConnectingInOrOut).append(", wasHolding=").append(wasHolding).append(", isConnectingInOrOut=").append(isConnectingInOrOut()).append(", changed=").append(changed).toString());
        if (wasConnectingInOrOut && !isConnectingInOrOut()) {
            onConnectedInOrOut();
        }
        if (changed && !wasHolding && getState() == Call.State.HOLDING) {
            onStartedHolding();
        }
        return changed;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void fakeHoldBeforeDial() {
        if (TelBrand.IS_SBM) {
            logd("[SEQ]GsmConnection.fakeHoldBeforeDial(" + this.mAddress + ") " + getState() + "->HOLDING");
        }
        if (this.mParent != null) {
            this.mParent.detach(this);
        }
        this.mParent = this.mOwner.mBackgroundCall;
        this.mParent.attachFake(this, Call.State.HOLDING);
        onStartedHolding();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getGSMIndex() throws CallStateException {
        if (this.mIndex >= 0) {
            return this.mIndex + 1;
        }
        throw new CallStateException("GSM index not yet assigned");
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onConnectedInOrOut() {
        this.mConnectTime = System.currentTimeMillis();
        this.mConnectTimeReal = SystemClock.elapsedRealtime();
        this.mDuration = 0L;
        log("onConnectedInOrOut: connectTime=" + this.mConnectTime);
        if (!this.mIsIncoming) {
            processNextPostDialChar();
        }
        releaseWakeLock();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onStartedHolding() {
        this.mHoldingStartTime = SystemClock.elapsedRealtime();
    }

    private boolean processPostDialChar(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            this.mOwner.mCi.sendDtmf(c, this.mHandler.obtainMessage(1));
            return true;
        } else if (c == ',') {
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(2), 3000L);
            if (!TelBrand.IS_SBM) {
                return true;
            }
            setPostDialState(Connection.PostDialState.PAUSE);
            return true;
        } else if (c == ';') {
            setPostDialState(Connection.PostDialState.WAIT);
            return true;
        } else if (c == 'P') {
            setPostDialState(Connection.PostDialState.WAIT_EX);
            return true;
        } else if (c != 'N') {
            return false;
        } else {
            setPostDialState(Connection.PostDialState.WILD);
            return true;
        }
    }

    @Override // com.android.internal.telephony.Connection
    public String getRemainingPostDialString() {
        if (this.mPostDialState == Connection.PostDialState.CANCELLED || this.mPostDialState == Connection.PostDialState.COMPLETE || this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
            return "";
        }
        String subStr = this.mPostDialString.substring(this.mNextPostDialChar);
        if (subStr != null) {
            int wIndex = subStr.indexOf(59);
            int pIndex = subStr.indexOf(44);
            int wexIndex = subStr.indexOf(80);
            if (wIndex > 0 && ((wIndex < pIndex || pIndex <= 0) && (wIndex < wexIndex || wexIndex <= 0))) {
                subStr = subStr.substring(0, wIndex);
            } else if (wexIndex > 0 && ((wexIndex < pIndex || pIndex <= 0) && (wexIndex < wIndex || wIndex <= 0))) {
                subStr = subStr.substring(0, wexIndex);
            } else if (pIndex > 0) {
                subStr = subStr.substring(0, pIndex);
            }
            logd("getRemainingPostDialString() wIndex(%d), pIndex(%d), wexIndex(%d)", Integer.valueOf(wIndex), Integer.valueOf(pIndex), Integer.valueOf(wexIndex));
        }
        logv("getRemainingPostDialString() end, return String(%s)", subStr);
        return subStr;
    }

    protected void finalize() {
        if (this.mPartialWakeLock.isHeld()) {
            Rlog.e(LOG_TAG, "[GSMConn] UNEXPECTED; mPartialWakeLock is held when finalizing.");
        }
        clearPostDialListeners();
        releaseWakeLock();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void processNextPostDialChar() {
        char c;
        Message notifyMessage;
        if (this.mPostDialState != Connection.PostDialState.CANCELLED) {
            if (this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
                setPostDialState(Connection.PostDialState.COMPLETE);
                c = 0;
            } else {
                setPostDialState(Connection.PostDialState.STARTED);
                String str = this.mPostDialString;
                int i = this.mNextPostDialChar;
                this.mNextPostDialChar = i + 1;
                c = str.charAt(i);
                if (!processPostDialChar(c)) {
                    this.mHandler.obtainMessage(3).sendToTarget();
                    Rlog.e("GSM", "processNextPostDialChar: c=" + c + " isn't valid!");
                    return;
                }
            }
            notifyPostDialListenersNextChar(c);
            Registrant postDialHandler = this.mOwner.mPhone.mPostDialHandler;
            if (postDialHandler != null && (notifyMessage = postDialHandler.messageForRegistrant()) != null) {
                Connection.PostDialState state = this.mPostDialState;
                AsyncResult ar = AsyncResult.forMessage(notifyMessage);
                ar.result = this;
                ar.userObj = state;
                notifyMessage.arg1 = c;
                notifyMessage.sendToTarget();
            }
        }
    }

    private boolean isConnectingInOrOut() {
        return this.mParent == null || this.mParent == this.mOwner.mRingingCall || this.mParent.mState == Call.State.DIALING || this.mParent.mState == Call.State.ALERTING;
    }

    private GsmCall parentFromDCState(DriverCall.State state) {
        switch (state) {
            case ACTIVE:
            case DIALING:
            case ALERTING:
                return this.mOwner.mForegroundCall;
            case HOLDING:
                return this.mOwner.mBackgroundCall;
            case INCOMING:
            case WAITING:
                return this.mOwner.mRingingCall;
            default:
                throw new RuntimeException("illegal call state: " + state);
        }
    }

    private void setPostDialState(Connection.PostDialState s) {
        if (this.mPostDialState != Connection.PostDialState.STARTED && s == Connection.PostDialState.STARTED) {
            acquireWakeLock();
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(4), 60000L);
        } else if (this.mPostDialState == Connection.PostDialState.STARTED && s != Connection.PostDialState.STARTED) {
            this.mHandler.removeMessages(4);
            releaseWakeLock();
        }
        this.mPostDialState = s;
        notifyPostDialListeners();
    }

    public void setSpeechCodeState(String[] codec) {
        log("setSpeechCodeState start");
        this.mSpeechCodeState = codec;
    }

    private void createWakeLock(Context context) {
        this.mPartialWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, LOG_TAG);
    }

    private void acquireWakeLock() {
        log("acquireWakeLock");
        this.mPartialWakeLock.acquire();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void releaseWakeLock() {
        synchronized (this.mPartialWakeLock) {
            if (this.mPartialWakeLock.isHeld()) {
                log("releaseWakeLock");
                this.mPartialWakeLock.release();
            }
        }
    }

    private static boolean isPause(char c) {
        boolean z = false;
        if (!TelBrand.IS_SBM) {
            Object[] objArr = new Object[2];
            objArr[0] = Character.valueOf(c);
            objArr[1] = Boolean.valueOf(c == ',');
            logv("isPause( char(%c) ) return boolean(%s) ", objArr);
            return c == ',';
        }
        Object[] objArr2 = new Object[2];
        objArr2[0] = Character.valueOf(c);
        objArr2[1] = Boolean.valueOf(c == ',' || c == 'P');
        logv("isPause( char(%c) ) return boolean(%s) ", objArr2);
        if (c == ',' || c == 'P') {
            z = true;
        }
        return z;
    }

    private static boolean isWait(char c) {
        boolean z = true;
        if (!TelBrand.IS_SBM) {
            Object[] objArr = new Object[2];
            objArr[0] = Character.valueOf(c);
            objArr[1] = Boolean.valueOf(c == ';' || c == 'P');
            logv("isWait( char(%c) ) return boolean(%s) ", objArr);
            return c == ';' || c == 'P';
        }
        Object[] objArr2 = new Object[2];
        objArr2[0] = Character.valueOf(c);
        objArr2[1] = Boolean.valueOf(c == ';');
        logv("isWait( char(%c) ) return boolean(%s) ", objArr2);
        if (c != ';') {
            z = false;
        }
        return z;
    }

    private static int findNextPCharOrNonPOrNonWCharIndex(String phoneNumber, int currIndex) {
        logv("findNextPCharOrNonPOrNonWCharIndex( Strint(%s), int(%d) ) start", phoneNumber, Integer.valueOf(currIndex));
        boolean wMatched = false;
        int index = currIndex + 1;
        int length = phoneNumber.length();
        while (index < length) {
            char cNext = phoneNumber.charAt(index);
            if (isWait(cNext)) {
                wMatched = true;
            }
            if (!isWait(cNext) && !isPause(cNext)) {
                break;
            }
            index++;
        }
        logd("findNextPCharOrNonPOrNonWCharIndex() wMatched(%s), index(%d), length(%d)", Boolean.valueOf(wMatched), Integer.valueOf(index), Integer.valueOf(length));
        if (index >= length || index <= currIndex + 1 || wMatched || !isPause(phoneNumber.charAt(currIndex))) {
            logv("findNextPCharOrNonPOrNonWCharIndex() end, return int(%d)", Integer.valueOf(index));
            return index;
        }
        logd("findNextPCharOrNonPOrNonWCharIndex() PAUSE character(s) needs to be handled one by one, return int(%d)", Integer.valueOf(currIndex + 1));
        return currIndex + 1;
    }

    private static char findPOrWCharToAppend(String phoneNumber, int currPwIndex, int nextNonPwCharIndex) {
        logv("findPOrWCharToAppend( String(%s), int(%d), int(%d) ) start", phoneNumber, Integer.valueOf(currPwIndex), Integer.valueOf(nextNonPwCharIndex));
        char c = phoneNumber.charAt(currPwIndex);
        char ret = isPause(c) ? ',' : ';';
        if (isPause(c) && nextNonPwCharIndex > currPwIndex + 1) {
            ret = ';';
            logd("findPOrWCharToAppend() returns WAIT character to append, ret(%c)", ';');
        }
        logv("findPOrWCharToAppend() end, return char(%c)", Character.valueOf(ret));
        return ret;
    }

    public static String formatDialString(String phoneNumber) {
        logv("formatDialString( String(%s) ) start", phoneNumber);
        if (phoneNumber == null) {
            loge("formatDialString() parameter is invalid, return String(null)");
            return null;
        }
        int length = phoneNumber.length();
        StringBuilder ret = new StringBuilder();
        int currIndex = 0;
        while (currIndex < length) {
            char c = phoneNumber.charAt(currIndex);
            if (PhoneNumberUtils.isDialable(c)) {
                logd("formatDialString() number(%c) of currIndex can be dial.", Character.valueOf(c));
                ret.append(c);
            } else if (isPause(c) || isWait(c)) {
                logd("formatDialString() number(%c) of currIndex is PAUSE or WAIT or WAIT_EX", Character.valueOf(c));
                if (currIndex < length - 1) {
                    logd("formatDialString() currIndex(%d) < length - 1(%d)", Integer.valueOf(currIndex), Integer.valueOf(length - 1));
                    int nextIndex = findNextPCharOrNonPOrNonWCharIndex(phoneNumber, currIndex);
                    if (nextIndex < length) {
                        logd("formatDialString() nextIndex(%d) < length(%d)", Integer.valueOf(nextIndex), Integer.valueOf(length));
                        ret.append(findPOrWCharToAppend(phoneNumber, currIndex, nextIndex));
                        if (nextIndex > currIndex + 1) {
                            logd("formatDialString() nextIndex(%d) > ( currIndex + 1 )(%d)", Integer.valueOf(nextIndex), Integer.valueOf(currIndex + 1));
                            currIndex = nextIndex - 1;
                        }
                    } else if (nextIndex == length) {
                        logd("formatDialString() nextIndex(%d) == length(%d)", Integer.valueOf(nextIndex), Integer.valueOf(length));
                        currIndex = length - 1;
                    }
                }
            } else {
                logd("formatDialString() number of currIndex is (%c)", Character.valueOf(c));
                ret.append(c);
            }
            currIndex++;
        }
        logv("formatDialString() end, return String(%s)", ret.toString());
        return ret.toString();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void logd(String format, Object... args) {
        Rlog.d(LOG_TAG, "[GSMConn] " + String.format(format, args));
    }

    private static void logd(String msg) {
        Rlog.d(LOG_TAG, "[GSMConn] " + msg);
    }

    private static void logv(String format, Object... args) {
        Rlog.v(LOG_TAG, "[GSMConn] " + String.format(format, args));
    }

    private static void logv(String msg) {
        Rlog.v(LOG_TAG, "[GSMConn] " + msg);
    }

    private static void loge(String msg) {
        Rlog.e(LOG_TAG, "[GSMConn] " + msg);
    }

    private void log(String msg) {
        Rlog.d(LOG_TAG, "[GSMConn] " + msg);
    }

    @Override // com.android.internal.telephony.Connection
    public int getNumberPresentation() {
        return this.mNumberPresentation;
    }

    @Override // com.android.internal.telephony.Connection
    public UUSInfo getUUSInfo() {
        return this.mUusInfo;
    }

    @Override // com.android.internal.telephony.Connection
    public int getPreciseDisconnectCause() {
        return this.mPreciseCause;
    }

    @Override // com.android.internal.telephony.Connection
    public void migrateFrom(Connection c) {
        if (c != null) {
            super.migrateFrom(c);
            this.mUusInfo = c.getUUSInfo();
            setUserData(c.getUserData());
        }
    }

    @Override // com.android.internal.telephony.Connection
    public Connection getOrigConnection() {
        return this.mOrigConnection;
    }

    @Override // com.android.internal.telephony.Connection
    public boolean isMultiparty() {
        if (this.mOrigConnection != null) {
            return this.mOrigConnection.isMultiparty();
        }
        return false;
    }

    @Override // com.android.internal.telephony.Connection
    public String getBeforeFowardingNumber() {
        if (TelBrand.IS_DCM) {
            return this.redirectingNum;
        }
        return null;
    }

    public boolean getHangUpCallPending() {
        if (TelBrand.IS_DCM) {
            return this.mHangupCallPending;
        }
        return false;
    }

    public void setHangUpCallPending(boolean hangupCallPending) {
        if (TelBrand.IS_DCM) {
            this.mHangupCallPending = hangupCallPending;
        }
    }

    public boolean getAcceptCallPending() {
        if (TelBrand.IS_DCM) {
            return this.mAcceptCallPending;
        }
        return false;
    }

    public void setAcceptCallPending(boolean acceptCallPending) {
        if (TelBrand.IS_DCM) {
            this.mAcceptCallPending = acceptCallPending;
        }
    }
}
