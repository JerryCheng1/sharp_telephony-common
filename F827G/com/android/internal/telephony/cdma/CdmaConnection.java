package com.android.internal.telephony.cdma;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.SystemClock;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Connection.PostDialState;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;

public class CdmaConnection extends Connection {
    static final int EVENT_DTMF_DONE = 1;
    static final int EVENT_NEXT_POST_DIAL = 3;
    static final int EVENT_PAUSE_DONE = 2;
    static final int EVENT_WAKE_LOCK_TIMEOUT = 4;
    static final String LOG_TAG = "CdmaConnection";
    static final int PAUSE_DELAY_MILLIS = 2000;
    private static final boolean VDBG = false;
    static final int WAKE_LOCK_TIMEOUT_MILLIS = 60000;
    int mCause = 0;
    long mDisconnectTime;
    boolean mDisconnected;
    Handler mHandler;
    int mIndex;
    int mNextPostDialChar;
    CdmaCallTracker mOwner;
    CdmaCall mParent;
    private WakeLock mPartialWakeLock;
    PostDialState mPostDialState = PostDialState.NOT_STARTED;
    String mPostDialString;
    int mPreciseCause = 0;

    class MyHandler extends Handler {
        MyHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message message) {
            switch (message.what) {
                case 1:
                case 2:
                case 3:
                    CdmaConnection.this.processNextPostDialChar();
                    return;
                case 4:
                    CdmaConnection.this.releaseWakeLock();
                    return;
                default:
                    return;
            }
        }
    }

    CdmaConnection(Context context, DriverCall driverCall, CdmaCallTracker cdmaCallTracker, int i) {
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = cdmaCallTracker;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mAddress = driverCall.number;
        this.mIsIncoming = driverCall.isMT;
        this.mCreateTime = System.currentTimeMillis();
        this.mCnapName = driverCall.name;
        this.mCnapNamePresentation = driverCall.namePresentation;
        this.mNumberPresentation = driverCall.numberPresentation;
        this.mIndex = i;
        this.mParent = parentFromDCState(driverCall.state);
        this.mParent.attach(this, driverCall);
    }

    CdmaConnection(Context context, CdmaCallWaitingNotification cdmaCallWaitingNotification, CdmaCallTracker cdmaCallTracker, CdmaCall cdmaCall) {
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = cdmaCallTracker;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mAddress = cdmaCallWaitingNotification.number;
        this.mNumberPresentation = cdmaCallWaitingNotification.numberPresentation;
        this.mCnapName = cdmaCallWaitingNotification.name;
        this.mCnapNamePresentation = cdmaCallWaitingNotification.namePresentation;
        this.mIndex = -1;
        this.mIsIncoming = true;
        this.mCreateTime = System.currentTimeMillis();
        this.mConnectTime = 0;
        this.mParent = cdmaCall;
        cdmaCall.attachFake(this, State.WAITING);
    }

    CdmaConnection(Context context, String str, CdmaCallTracker cdmaCallTracker, CdmaCall cdmaCall) {
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = cdmaCallTracker;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mDialString = str;
        Rlog.d(LOG_TAG, "[CDMAConn] CdmaConnection: dialString=" + str);
        String formatDialString = formatDialString(str);
        Rlog.d(LOG_TAG, "[CDMAConn] CdmaConnection:formated dialString=" + formatDialString);
        this.mAddress = PhoneNumberUtils.extractNetworkPortionAlt(formatDialString);
        this.mPostDialString = PhoneNumberUtils.extractPostDialPortion(formatDialString);
        this.mIndex = -1;
        this.mIsIncoming = false;
        this.mCnapName = null;
        this.mCnapNamePresentation = 1;
        this.mNumberPresentation = 1;
        this.mCreateTime = System.currentTimeMillis();
        if (cdmaCall != null) {
            this.mParent = cdmaCall;
            if (cdmaCall.mState == State.ACTIVE) {
                cdmaCall.attachFake(this, State.ACTIVE);
            } else {
                cdmaCall.attachFake(this, State.DIALING);
            }
        }
    }

    private void acquireWakeLock() {
        log("acquireWakeLock");
        this.mPartialWakeLock.acquire();
    }

    private void createWakeLock(Context context) {
        this.mPartialWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, LOG_TAG);
    }

    private void doDisconnect() {
        this.mIndex = -1;
        this.mDisconnectTime = System.currentTimeMillis();
        this.mDuration = SystemClock.elapsedRealtime() - this.mConnectTimeReal;
        this.mDisconnected = true;
        clearPostDialListeners();
    }

    static boolean equalsHandlesNulls(Object obj, Object obj2) {
        return obj == null ? obj2 == null : obj.equals(obj2);
    }

    private static int findNextPCharOrNonPOrNonWCharIndex(String str, int i) {
        boolean isWait = isWait(str.charAt(i));
        int i2 = i + 1;
        int length = str.length();
        while (i2 < length) {
            char charAt = str.charAt(i2);
            if (isWait(charAt)) {
                isWait = true;
            }
            if (!isWait(charAt) && !isPause(charAt)) {
                break;
            }
            i2++;
        }
        return (i2 >= length || i2 <= i + 1 || isWait || !isPause(str.charAt(i))) ? i2 : i + 1;
    }

    private static char findPOrWCharToAppend(String str, int i, int i2) {
        return i2 > i + 1 ? ';' : isPause(str.charAt(i)) ? ',' : ';';
    }

    public static String formatDialString(String str) {
        if (str == null) {
            return null;
        }
        int length = str.length();
        StringBuilder stringBuilder = new StringBuilder();
        int i = 0;
        while (i < length) {
            char charAt = str.charAt(i);
            if (!isPause(charAt) && !isWait(charAt)) {
                stringBuilder.append(charAt);
            } else if (i < length - 1) {
                int findNextPCharOrNonPOrNonWCharIndex = findNextPCharOrNonPOrNonWCharIndex(str, i);
                if (findNextPCharOrNonPOrNonWCharIndex < length) {
                    stringBuilder.append(findPOrWCharToAppend(str, i, findNextPCharOrNonPOrNonWCharIndex));
                    if (findNextPCharOrNonPOrNonWCharIndex > i + 1) {
                        i = findNextPCharOrNonPOrNonWCharIndex - 1;
                    }
                } else if (findNextPCharOrNonPOrNonWCharIndex == length) {
                    i = length - 1;
                }
            }
            i++;
        }
        return PhoneNumberUtils.cdmaCheckAndProcessPlusCode(stringBuilder.toString());
    }

    private boolean isConnectingInOrOut() {
        return this.mParent == null || this.mParent == this.mOwner.mRingingCall || this.mParent.mState == State.DIALING || this.mParent.mState == State.ALERTING;
    }

    private static boolean isPause(char c) {
        return c == ',';
    }

    private static boolean isWait(char c) {
        return c == ';';
    }

    private void log(String str) {
        Rlog.d(LOG_TAG, "[CDMAConn] " + str);
    }

    private CdmaCall parentFromDCState(DriverCall.State state) {
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

    private boolean processPostDialChar(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            this.mOwner.mCi.sendDtmf(c, this.mHandler.obtainMessage(1));
            return true;
        } else if (c == ',') {
            setPostDialState(PostDialState.PAUSE);
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(2), 2000);
            return true;
        } else if (c == ';') {
            setPostDialState(PostDialState.WAIT);
            return true;
        } else if (c != 'N') {
            return false;
        } else {
            setPostDialState(PostDialState.WILD);
            return true;
        }
    }

    private void releaseWakeLock() {
        synchronized (this.mPartialWakeLock) {
            if (this.mPartialWakeLock.isHeld()) {
                log("releaseWakeLock");
                this.mPartialWakeLock.release();
            }
        }
    }

    private void setPostDialState(PostDialState postDialState) {
        if (postDialState == PostDialState.STARTED || postDialState == PostDialState.PAUSE) {
            synchronized (this.mPartialWakeLock) {
                if (this.mPartialWakeLock.isHeld()) {
                    this.mHandler.removeMessages(4);
                } else {
                    acquireWakeLock();
                }
                this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(4), 60000);
            }
        } else {
            this.mHandler.removeMessages(4);
            releaseWakeLock();
        }
        this.mPostDialState = postDialState;
        notifyPostDialListeners();
    }

    public void cancelPostDial() {
        setPostDialState(PostDialState.CANCELLED);
    }

    /* Access modifiers changed, original: 0000 */
    public boolean compareTo(DriverCall driverCall) {
        if (this.mIsIncoming || driverCall.isMT) {
            String stringFromStringAndTOA = PhoneNumberUtils.stringFromStringAndTOA(driverCall.number, driverCall.TOA);
            if (!(this.mIsIncoming == driverCall.isMT && equalsHandlesNulls(this.mAddress, stringFromStringAndTOA))) {
                return false;
            }
        }
        return true;
    }

    /* Access modifiers changed, original: 0000 */
    public int disconnectCauseFromCode(int i) {
        switch (i) {
            case 17:
                return 4;
            case 34:
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
            case 1000:
                return 26;
            case CallFailCause.CDMA_DROP /*1001*/:
                return 27;
            case CallFailCause.CDMA_INTERCEPT /*1002*/:
                return 28;
            case CallFailCause.CDMA_REORDER /*1003*/:
                return 29;
            case CallFailCause.CDMA_SO_REJECT /*1004*/:
                return 30;
            case CallFailCause.CDMA_RETRY_ORDER /*1005*/:
                return 31;
            case CallFailCause.CDMA_ACCESS_FAILURE /*1006*/:
                return 32;
            case CallFailCause.CDMA_PREEMPTED /*1007*/:
                return 33;
            case CallFailCause.CDMA_NOT_EMERGENCY /*1008*/:
                return 34;
            case CallFailCause.CDMA_ACCESS_BLOCKED /*1009*/:
                return 35;
            default:
                CDMAPhone cDMAPhone = this.mOwner.mPhone;
                int state = cDMAPhone.getServiceState().getState();
                UiccCardApplication uiccCardApplication = UiccController.getInstance().getUiccCardApplication(cDMAPhone.getPhoneId(), 2);
                return state == 3 ? 17 : (state == 1 || state == 2) ? 18 : (cDMAPhone.mCdmaSubscriptionSource != 0 || (uiccCardApplication != null ? uiccCardApplication.getState() : AppState.APPSTATE_UNKNOWN) == AppState.APPSTATE_READY) ? i != 16 ? 36 : 2 : 19;
        }
    }

    public void dispose() {
    }

    /* Access modifiers changed, original: 0000 */
    public void fakeHoldBeforeDial() {
        if (this.mParent != null) {
            this.mParent.detach(this);
        }
        this.mParent = this.mOwner.mBackgroundCall;
        this.mParent.attachFake(this, State.HOLDING);
        onStartedHolding();
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        if (this.mPartialWakeLock.isHeld()) {
            Rlog.e(LOG_TAG, "[CdmaConn] UNEXPECTED; mPartialWakeLock is held when finalizing.");
        }
        releaseWakeLock();
    }

    /* Access modifiers changed, original: 0000 */
    public int getCDMAIndex() throws CallStateException {
        if (this.mIndex >= 0) {
            return this.mIndex + 1;
        }
        throw new CallStateException("CDMA connection index not assigned");
    }

    public CdmaCall getCall() {
        return this.mParent;
    }

    public int getDisconnectCause() {
        return this.mCause;
    }

    public long getDisconnectTime() {
        return this.mDisconnectTime;
    }

    public long getHoldDurationMillis() {
        return getState() != State.HOLDING ? 0 : SystemClock.elapsedRealtime() - this.mHoldingStartTime;
    }

    public int getNumberPresentation() {
        return this.mNumberPresentation;
    }

    public Connection getOrigConnection() {
        return null;
    }

    public String getOrigDialString() {
        return this.mDialString;
    }

    public PostDialState getPostDialState() {
        return this.mPostDialState;
    }

    public int getPreciseDisconnectCause() {
        return this.mPreciseCause;
    }

    public String getRemainingPostDialString() {
        if (this.mPostDialState == PostDialState.CANCELLED || this.mPostDialState == PostDialState.COMPLETE || this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
            return "";
        }
        String substring = this.mPostDialString.substring(this.mNextPostDialChar);
        if (substring == null) {
            return substring;
        }
        int indexOf = substring.indexOf(59);
        int indexOf2 = substring.indexOf(44);
        return (indexOf <= 0 || (indexOf >= indexOf2 && indexOf2 > 0)) ? indexOf2 > 0 ? substring.substring(0, indexOf2) : substring : substring.substring(0, indexOf);
    }

    public State getState() {
        return this.mDisconnected ? State.DISCONNECTED : super.getState();
    }

    public UUSInfo getUUSInfo() {
        return null;
    }

    public void hangup() throws CallStateException {
        if (this.mDisconnected) {
            throw new CallStateException("disconnected");
        }
        this.mOwner.hangup(this);
    }

    public boolean isMultiparty() {
        return false;
    }

    /* Access modifiers changed, original: 0000 */
    public void onConnectedInOrOut() {
        this.mConnectTime = System.currentTimeMillis();
        this.mConnectTimeReal = SystemClock.elapsedRealtime();
        this.mDuration = 0;
        log("onConnectedInOrOut: connectTime=" + this.mConnectTime);
        if (this.mIsIncoming) {
            releaseWakeLock();
        } else {
            processNextPostDialChar();
        }
    }

    /* Access modifiers changed, original: 0000 */
    public boolean onDisconnect(int i) {
        boolean z = false;
        this.mCause = i;
        if (!this.mDisconnected) {
            doDisconnect();
            this.mOwner.mPhone.notifyDisconnect(this);
            if (this.mParent != null) {
                z = this.mParent.connectionDisconnected(this);
            }
        }
        releaseWakeLock();
        return z;
    }

    /* Access modifiers changed, original: 0000 */
    public void onHangupLocal() {
        this.mCause = 3;
        this.mPreciseCause = 0;
    }

    /* Access modifiers changed, original: 0000 */
    public void onLocalDisconnect() {
        if (!this.mDisconnected) {
            doDisconnect();
            if (this.mParent != null) {
                this.mParent.detach(this);
            }
        }
        releaseWakeLock();
    }

    /* Access modifiers changed, original: 0000 */
    public void onRemoteDisconnect(int i) {
        this.mPreciseCause = i;
        onDisconnect(disconnectCauseFromCode(i));
    }

    /* Access modifiers changed, original: 0000 */
    public void onStartedHolding() {
        this.mHoldingStartTime = SystemClock.elapsedRealtime();
    }

    public void proceedAfterWaitChar() {
        if (this.mPostDialState != PostDialState.WAIT) {
            Rlog.w(LOG_TAG, "CdmaConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WAIT but was " + this.mPostDialState);
            return;
        }
        setPostDialState(PostDialState.STARTED);
        processNextPostDialChar();
    }

    public void proceedAfterWildChar(String str) {
        if (this.mPostDialState != PostDialState.WILD) {
            Rlog.w(LOG_TAG, "CdmaConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WILD but was " + this.mPostDialState);
            return;
        }
        setPostDialState(PostDialState.STARTED);
        StringBuilder stringBuilder = new StringBuilder(str);
        stringBuilder.append(this.mPostDialString.substring(this.mNextPostDialChar));
        this.mPostDialString = stringBuilder.toString();
        this.mNextPostDialChar = 0;
        log("proceedAfterWildChar: new postDialString is " + this.mPostDialString);
        processNextPostDialChar();
    }

    /* Access modifiers changed, original: 0000 */
    public void processNextPostDialChar() {
        if (this.mPostDialState == PostDialState.CANCELLED) {
            releaseWakeLock();
            return;
        }
        char c;
        if (this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
            setPostDialState(PostDialState.COMPLETE);
            releaseWakeLock();
            c = 0;
        } else {
            setPostDialState(PostDialState.STARTED);
            String str = this.mPostDialString;
            int i = this.mNextPostDialChar;
            this.mNextPostDialChar = i + 1;
            c = str.charAt(i);
            if (!processPostDialChar(c)) {
                this.mHandler.obtainMessage(3).sendToTarget();
                Rlog.e("CDMA", "processNextPostDialChar: c=" + c + " isn't valid!");
                return;
            }
        }
        notifyPostDialListenersNextChar(c);
        Registrant registrant = this.mOwner.mPhone.mPostDialHandler;
        if (registrant != null) {
            Message messageForRegistrant = registrant.messageForRegistrant();
            if (messageForRegistrant != null) {
                PostDialState postDialState = this.mPostDialState;
                AsyncResult forMessage = AsyncResult.forMessage(messageForRegistrant);
                forMessage.result = this;
                forMessage.userObj = postDialState;
                messageForRegistrant.arg1 = c;
                messageForRegistrant.sendToTarget();
            }
        }
    }

    public void separate() throws CallStateException {
        if (this.mDisconnected) {
            throw new CallStateException("disconnected");
        }
        this.mOwner.separate(this);
    }

    /* Access modifiers changed, original: 0000 */
    public boolean update(DriverCall driverCall) {
        boolean z;
        boolean z2 = true;
        boolean isConnectingInOrOut = isConnectingInOrOut();
        boolean z3 = getState() == State.HOLDING;
        CdmaCall parentFromDCState = parentFromDCState(driverCall.state);
        log("parent= " + this.mParent + ", newParent= " + parentFromDCState);
        log(" mNumberConverted " + this.mNumberConverted);
        if (equalsHandlesNulls(this.mAddress, driverCall.number) || (this.mNumberConverted && equalsHandlesNulls(this.mConvertedNumber, driverCall.number))) {
            z = false;
        } else {
            log("update: phone # changed!");
            this.mAddress = driverCall.number;
            z = true;
        }
        if (TextUtils.isEmpty(driverCall.name)) {
            if (!TextUtils.isEmpty(this.mCnapName)) {
                this.mCnapName = "";
                z = true;
            }
        } else if (!driverCall.name.equals(this.mCnapName)) {
            this.mCnapName = driverCall.name;
            z = true;
        }
        log("--dssds----" + this.mCnapName);
        this.mCnapNamePresentation = driverCall.namePresentation;
        this.mNumberPresentation = driverCall.numberPresentation;
        if (parentFromDCState != this.mParent) {
            if (this.mParent != null) {
                this.mParent.detach(this);
            }
            parentFromDCState.attach(this, driverCall);
            this.mParent = parentFromDCState;
        } else {
            boolean update = this.mParent.update(this, driverCall);
            if (!(z || update)) {
                z2 = false;
            }
        }
        log("Update, wasConnectingInOrOut=" + isConnectingInOrOut + ", wasHolding=" + z3 + ", isConnectingInOrOut=" + isConnectingInOrOut() + ", changed=" + z2);
        if (isConnectingInOrOut && !isConnectingInOrOut()) {
            onConnectedInOrOut();
        }
        if (z2 && !z3 && getState() == State.HOLDING) {
            onStartedHolding();
        }
        return z2;
    }

    public void updateParent(CdmaCall cdmaCall, CdmaCall cdmaCall2) {
        if (cdmaCall2 != cdmaCall) {
            if (cdmaCall != null) {
                cdmaCall.detach(this);
            }
            cdmaCall2.attachFake(this, State.ACTIVE);
            this.mParent = cdmaCall2;
        }
    }
}
