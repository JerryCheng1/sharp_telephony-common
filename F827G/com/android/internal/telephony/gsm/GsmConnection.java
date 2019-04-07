package com.android.internal.telephony.gsm;

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
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.UiccCardApplication;

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
    boolean mAcceptCallPending = false;
    int mCause = 0;
    long mDisconnectTime;
    boolean mDisconnected;
    Handler mHandler;
    boolean mHangupCallPending = false;
    int mIndex;
    int mNextPostDialChar;
    Connection mOrigConnection;
    GsmCallTracker mOwner;
    GsmCall mParent;
    private WakeLock mPartialWakeLock;
    PostDialState mPostDialState = PostDialState.NOT_STARTED;
    String mPostDialString;
    int mPreciseCause = 0;
    private String[] mSpeechCodeState;
    UUSInfo mUusInfo;
    String redirectingNum;

    class MyHandler extends Handler {
        MyHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message message) {
            if (TelBrand.IS_SBM) {
                GsmConnection.logd("GsmConnection() handleMessage is (%d)", Integer.valueOf(message.what));
                switch (message.what) {
                    case 1:
                        GsmConnection.this.mHandler.sendMessageDelayed(GsmConnection.this.mHandler.obtainMessage(5), 70);
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
            switch (message.what) {
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
        }
    }

    GsmConnection(Context context, DriverCall driverCall, GsmCallTracker gsmCallTracker, int i) {
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = gsmCallTracker;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        if (!TelBrand.IS_DCM) {
            this.mAddress = driverCall.number;
        } else if (TextUtils.isEmpty(driverCall.number)) {
            this.mAddress = driverCall.number;
            this.redirectingNum = null;
        } else if (driverCall.number.indexOf(38) == -1) {
            this.mAddress = driverCall.number;
            this.redirectingNum = null;
        } else {
            String[] split = driverCall.number.split("&");
            this.mAddress = split[0];
            this.redirectingNum = split[1];
            log("GsmConnection: address is " + this.mAddress);
            log("GsmConnection: redirectingNum is " + this.redirectingNum);
        }
        this.mIsIncoming = driverCall.isMT;
        this.mCreateTime = System.currentTimeMillis();
        this.mCnapName = driverCall.name;
        this.mCnapNamePresentation = driverCall.namePresentation;
        this.mNumberPresentation = driverCall.numberPresentation;
        this.mUusInfo = driverCall.uusInfo;
        logd("GsmConnection() mIsIncoming is %s", Boolean.valueOf(this.mIsIncoming));
        logd("GsmConnection() mNumberPresentation is %d", Integer.valueOf(this.mNumberPresentation));
        logd("GsmConnection() mAddress is %s", this.mAddress);
        if (this.mIsIncoming && this.mNumberPresentation == 1 && TextUtils.isEmpty(this.mAddress)) {
            logd("GsmConnection() sets PRESENTATION_UNKNOWN to mNumberPresentation");
            this.mNumberPresentation = 3;
        }
        this.mIndex = i;
        this.mParent = parentFromDCState(driverCall.state);
        this.mParent.attach(this, driverCall);
        if (TelBrand.IS_SBM) {
            logd("[SEQ]GsmConnection.GsmConnection(" + driverCall.number + ") IDLE->" + getState());
        }
    }

    GsmConnection(Context context, String str, GsmCallTracker gsmCallTracker, GsmCall gsmCall) {
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = gsmCallTracker;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mDialString = str;
        logd("GsmConnection() dialString = (%s)", str);
        String formatDialString = formatDialString(str);
        logd("GsmConnection() formated dialString = (%s)", formatDialString);
        this.mAddress = PhoneNumberUtils.extractNetworkPortionAlt(formatDialString);
        this.mPostDialString = PhoneNumberUtils.extractPostDialPortion(formatDialString);
        this.mIndex = -1;
        this.mIsIncoming = false;
        this.mCnapName = null;
        this.mCnapNamePresentation = 1;
        this.mNumberPresentation = 1;
        this.mCreateTime = System.currentTimeMillis();
        this.mParent = gsmCall;
        gsmCall.attachFake(this, State.DIALING);
        if (TelBrand.IS_SBM) {
            logd("[SEQ]GsmConnection.GsmConnection(" + formatDialString + ") IDLE->DIALING");
        }
    }

    private void acquireWakeLock() {
        log("acquireWakeLock");
        this.mPartialWakeLock.acquire();
    }

    private void createWakeLock(Context context) {
        this.mPartialWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, LOG_TAG);
    }

    static boolean equalsHandlesNulls(Object obj, Object obj2) {
        return obj == null ? obj2 == null : obj.equals(obj2);
    }

    private static int findNextPCharOrNonPOrNonWCharIndex(String str, int i) {
        logv("findNextPCharOrNonPOrNonWCharIndex( Strint(%s), int(%d) ) start", str, Integer.valueOf(i));
        int i2 = i + 1;
        int length = str.length();
        boolean z = false;
        while (i2 < length) {
            char charAt = str.charAt(i2);
            if (isWait(charAt)) {
                z = true;
            }
            if (!isWait(charAt) && !isPause(charAt)) {
                break;
            }
            i2++;
        }
        logd("findNextPCharOrNonPOrNonWCharIndex() wMatched(%s), index(%d), length(%d)", Boolean.valueOf(z), Integer.valueOf(i2), Integer.valueOf(length));
        if (i2 >= length || i2 <= i + 1 || z || !isPause(str.charAt(i))) {
            logv("findNextPCharOrNonPOrNonWCharIndex() end, return int(%d)", Integer.valueOf(i2));
            return i2;
        }
        logd("findNextPCharOrNonPOrNonWCharIndex() PAUSE character(s) needs to be handled one by one, return int(%d)", Integer.valueOf(i + 1));
        return i + 1;
    }

    private static char findPOrWCharToAppend(String str, int i, int i2) {
        logv("findPOrWCharToAppend( String(%s), int(%d), int(%d) ) start", str, Integer.valueOf(i), Integer.valueOf(i2));
        char charAt = str.charAt(i);
        char c = isPause(charAt) ? ',' : ';';
        if (isPause(charAt) && i2 > i + 1) {
            logd("findPOrWCharToAppend() returns WAIT character to append, ret(%c)", Character.valueOf(';'));
            c = ';';
        }
        logv("findPOrWCharToAppend() end, return char(%c)", Character.valueOf(c));
        return c;
    }

    public static String formatDialString(String str) {
        logv("formatDialString( String(%s) ) start", str);
        if (str == null) {
            loge("formatDialString() parameter is invalid, return String(null)");
            return null;
        }
        int length = str.length();
        StringBuilder stringBuilder = new StringBuilder();
        int i = 0;
        while (i < length) {
            char charAt = str.charAt(i);
            if (PhoneNumberUtils.isDialable(charAt)) {
                logd("formatDialString() number(%c) of currIndex can be dial.", Character.valueOf(charAt));
                stringBuilder.append(charAt);
            } else if (isPause(charAt) || isWait(charAt)) {
                logd("formatDialString() number(%c) of currIndex is PAUSE or WAIT or WAIT_EX", Character.valueOf(charAt));
                if (i < length - 1) {
                    logd("formatDialString() currIndex(%d) < length - 1(%d)", Integer.valueOf(i), Integer.valueOf(length - 1));
                    int findNextPCharOrNonPOrNonWCharIndex = findNextPCharOrNonPOrNonWCharIndex(str, i);
                    if (findNextPCharOrNonPOrNonWCharIndex < length) {
                        logd("formatDialString() nextIndex(%d) < length(%d)", Integer.valueOf(findNextPCharOrNonPOrNonWCharIndex), Integer.valueOf(length));
                        stringBuilder.append(findPOrWCharToAppend(str, i, findNextPCharOrNonPOrNonWCharIndex));
                        if (findNextPCharOrNonPOrNonWCharIndex > i + 1) {
                            logd("formatDialString() nextIndex(%d) > ( currIndex + 1 )(%d)", Integer.valueOf(findNextPCharOrNonPOrNonWCharIndex), Integer.valueOf(i + 1));
                            i = findNextPCharOrNonPOrNonWCharIndex - 1;
                        }
                    } else if (findNextPCharOrNonPOrNonWCharIndex == length) {
                        logd("formatDialString() nextIndex(%d) == length(%d)", Integer.valueOf(findNextPCharOrNonPOrNonWCharIndex), Integer.valueOf(length));
                        i = length - 1;
                    }
                }
            } else {
                logd("formatDialString() number of currIndex is (%c)", Character.valueOf(charAt));
                stringBuilder.append(charAt);
            }
            i++;
        }
        logv("formatDialString() end, return String(%s)", stringBuilder.toString());
        return stringBuilder.toString();
    }

    private boolean isConnectingInOrOut() {
        return this.mParent == null || this.mParent == this.mOwner.mRingingCall || this.mParent.mState == State.DIALING || this.mParent.mState == State.ALERTING;
    }

    private static boolean isPause(char c) {
        boolean z;
        if (TelBrand.IS_SBM) {
            z = c == ',' || c == 'P';
            logv("isPause( char(%c) ) return boolean(%s) ", Character.valueOf(c), Boolean.valueOf(z));
            return c == ',' || c == 'P';
        } else {
            z = c == ',';
            logv("isPause( char(%c) ) return boolean(%s) ", Character.valueOf(c), Boolean.valueOf(z));
            return c == ',';
        }
    }

    private static boolean isWait(char c) {
        boolean z = true;
        boolean z2;
        if (TelBrand.IS_SBM) {
            z2 = c == ';';
            logv("isWait( char(%c) ) return boolean(%s) ", Character.valueOf(c), Boolean.valueOf(z2));
            if (c != ';') {
                z = false;
            }
            return z;
        }
        z2 = c == ';' || c == 'P';
        logv("isWait( char(%c) ) return boolean(%s) ", Character.valueOf(c), Boolean.valueOf(z2));
        return c == ';' || c == 'P';
    }

    private void log(String str) {
        Rlog.d(LOG_TAG, "[GSMConn] " + str);
    }

    private static void logd(String str) {
        Rlog.d(LOG_TAG, "[GSMConn] " + str);
    }

    private static void logd(String str, Object... objArr) {
        Rlog.d(LOG_TAG, "[GSMConn] " + String.format(str, objArr));
    }

    private static void loge(String str) {
        Rlog.e(LOG_TAG, "[GSMConn] " + str);
    }

    private static void logv(String str) {
        Rlog.v(LOG_TAG, "[GSMConn] " + str);
    }

    private static void logv(String str, Object... objArr) {
        Rlog.v(LOG_TAG, "[GSMConn] " + String.format(str, objArr));
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

    private void processNextPostDialChar() {
        if (this.mPostDialState != PostDialState.CANCELLED) {
            char c;
            if (this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
                setPostDialState(PostDialState.COMPLETE);
                c = 0;
            } else {
                setPostDialState(PostDialState.STARTED);
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
    }

    private boolean processPostDialChar(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            this.mOwner.mCi.sendDtmf(c, this.mHandler.obtainMessage(1));
            return true;
        } else if (c == ',') {
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(2), 3000);
            if (!TelBrand.IS_SBM) {
                return true;
            }
            setPostDialState(PostDialState.PAUSE);
            return true;
        } else if (c == ';') {
            setPostDialState(PostDialState.WAIT);
            return true;
        } else if (c == 'P') {
            setPostDialState(PostDialState.WAIT_EX);
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
        if (this.mPostDialState != PostDialState.STARTED && postDialState == PostDialState.STARTED) {
            acquireWakeLock();
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(4), 60000);
        } else if (this.mPostDialState == PostDialState.STARTED && postDialState != PostDialState.STARTED) {
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
        if ((this.mIsIncoming || driverCall.isMT) && this.mOrigConnection == null) {
            Object obj = null;
            if (TelBrand.IS_DCM) {
                obj = this.mAddress;
                if (!TextUtils.isEmpty(this.redirectingNum)) {
                    obj = obj + "&" + this.redirectingNum;
                }
            }
            String stringFromStringAndTOA = PhoneNumberUtils.stringFromStringAndTOA(driverCall.number, driverCall.TOA);
            if (TelBrand.IS_DCM) {
                if (!(this.mIsIncoming == driverCall.isMT && equalsHandlesNulls(obj, stringFromStringAndTOA))) {
                    return false;
                }
            } else if (!(this.mIsIncoming == driverCall.isMT && equalsHandlesNulls(this.mAddress, stringFromStringAndTOA))) {
                return false;
            }
        }
        return true;
    }

    /* Access modifiers changed, original: 0000 */
    public int disconnectCauseFromCode(int i) {
        switch (i) {
            case 1:
                return 25;
            case 17:
                return 4;
            case 34:
            case 41:
            case 42:
            case CallFailCause.CHANNEL_NOT_AVAIL /*44*/:
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
                GSMPhone gSMPhone = this.mOwner.mPhone;
                int state = gSMPhone.getServiceState().getState();
                UiccCardApplication uiccCardApplication = gSMPhone.getUiccCardApplication();
                return state == 3 ? 17 : (state == 1 || state == 2) ? 18 : (uiccCardApplication != null ? uiccCardApplication.getState() : AppState.APPSTATE_UNKNOWN) != AppState.APPSTATE_READY ? 19 : i == 65535 ? gSMPhone.mSST.mRestrictedState.isCsRestricted() ? 22 : gSMPhone.mSST.mRestrictedState.isCsEmergencyRestricted() ? 24 : gSMPhone.mSST.mRestrictedState.isCsNormalRestricted() ? 23 : 36 : i == 16 ? 2 : 36;
        }
    }

    public void dispose() {
    }

    /* Access modifiers changed, original: 0000 */
    public void fakeHoldBeforeDial() {
        if (TelBrand.IS_SBM) {
            logd("[SEQ]GsmConnection.fakeHoldBeforeDial(" + this.mAddress + ") " + getState() + "->HOLDING");
        }
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
            Rlog.e(LOG_TAG, "[GSMConn] UNEXPECTED; mPartialWakeLock is held when finalizing.");
        }
        clearPostDialListeners();
        releaseWakeLock();
    }

    public boolean getAcceptCallPending() {
        return TelBrand.IS_DCM ? this.mAcceptCallPending : false;
    }

    public String getBeforeFowardingNumber() {
        return TelBrand.IS_DCM ? this.redirectingNum : null;
    }

    public GsmCall getCall() {
        return this.mParent;
    }

    public int getDisconnectCause() {
        return this.mCause;
    }

    public long getDisconnectTime() {
        return this.mDisconnectTime;
    }

    /* Access modifiers changed, original: 0000 */
    public int getGSMIndex() throws CallStateException {
        if (this.mIndex >= 0) {
            return this.mIndex + 1;
        }
        throw new CallStateException("GSM index not yet assigned");
    }

    public boolean getHangUpCallPending() {
        return TelBrand.IS_DCM ? this.mHangupCallPending : false;
    }

    public long getHoldDurationMillis() {
        return getState() != State.HOLDING ? 0 : SystemClock.elapsedRealtime() - this.mHoldingStartTime;
    }

    public int getNumberPresentation() {
        return this.mNumberPresentation;
    }

    public Connection getOrigConnection() {
        return this.mOrigConnection;
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
        if (substring != null) {
            int indexOf = substring.indexOf(59);
            int indexOf2 = substring.indexOf(44);
            int indexOf3 = substring.indexOf(80);
            if (indexOf > 0 && ((indexOf < indexOf2 || indexOf2 <= 0) && (indexOf < indexOf3 || indexOf3 <= 0))) {
                substring = substring.substring(0, indexOf);
            } else if (indexOf3 > 0 && ((indexOf3 < indexOf2 || indexOf2 <= 0) && (indexOf3 < indexOf || indexOf <= 0))) {
                substring = substring.substring(0, indexOf3);
            } else if (indexOf2 > 0) {
                substring = substring.substring(0, indexOf2);
            }
            logd("getRemainingPostDialString() wIndex(%d), pIndex(%d), wexIndex(%d)", Integer.valueOf(indexOf), Integer.valueOf(indexOf2), Integer.valueOf(indexOf3));
        }
        logv("getRemainingPostDialString() end, return String(%s)", substring);
        return substring;
    }

    public String[] getSpeechCodeState() {
        log("getSpeechCodeState() start");
        return this.mSpeechCodeState;
    }

    public State getState() {
        return this.mDisconnected ? State.DISCONNECTED : super.getState();
    }

    public UUSInfo getUUSInfo() {
        return this.mUusInfo;
    }

    public void hangup() throws CallStateException {
        if (this.mDisconnected) {
            throw new CallStateException("disconnected");
        }
        this.mOwner.hangup(this);
    }

    public boolean isMultiparty() {
        return this.mOrigConnection != null ? this.mOrigConnection.isMultiparty() : false;
    }

    public void migrateFrom(Connection connection) {
        if (connection != null) {
            super.migrateFrom(connection);
            this.mUusInfo = connection.getUUSInfo();
            setUserData(connection.getUserData());
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void onConnectedInOrOut() {
        this.mConnectTime = System.currentTimeMillis();
        this.mConnectTimeReal = SystemClock.elapsedRealtime();
        this.mDuration = 0;
        log("onConnectedInOrOut: connectTime=" + this.mConnectTime);
        if (!this.mIsIncoming) {
            processNextPostDialChar();
        }
        releaseWakeLock();
    }

    /* Access modifiers changed, original: 0000 */
    public boolean onDisconnect(int i) {
        boolean z = false;
        this.mCause = i;
        if (TelBrand.IS_DCM) {
            this.mHangupCallPending = false;
            this.mAcceptCallPending = false;
        }
        if (!this.mDisconnected) {
            this.mIndex = -1;
            this.mDisconnectTime = System.currentTimeMillis();
            this.mDuration = SystemClock.elapsedRealtime() - this.mConnectTimeReal;
            this.mDisconnected = true;
            Rlog.d(LOG_TAG, "onDisconnect: cause=" + i);
            this.mOwner.mPhone.notifyDisconnect(this);
            if (this.mParent != null) {
                z = this.mParent.connectionDisconnected(this);
            }
            this.mOrigConnection = null;
        }
        clearPostDialListeners();
        releaseWakeLock();
        return z;
    }

    /* Access modifiers changed, original: 0000 */
    public void onHangupLocal() {
        this.mCause = 3;
        this.mPreciseCause = 0;
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
        if (TelBrand.IS_SBM) {
            if (this.mPostDialState != PostDialState.WAIT) {
                Rlog.w(LOG_TAG, "GsmConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WAIT but was " + this.mPostDialState);
                return;
            }
        } else if (!(this.mPostDialState == PostDialState.WAIT || this.mPostDialState == PostDialState.WAIT_EX)) {
            Rlog.w(LOG_TAG, "GsmConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WAIT but was " + this.mPostDialState);
            return;
        }
        setPostDialState(PostDialState.STARTED);
        processNextPostDialChar();
    }

    public void proceedAfterWildChar(String str) {
        if (this.mPostDialState != PostDialState.WILD) {
            Rlog.w(LOG_TAG, "GsmConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WILD but was " + this.mPostDialState);
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

    public void separate() throws CallStateException {
        if (this.mDisconnected) {
            throw new CallStateException("disconnected");
        }
        this.mOwner.separate(this);
    }

    public void setAcceptCallPending(boolean z) {
        if (TelBrand.IS_DCM) {
            this.mAcceptCallPending = z;
        }
    }

    public void setHangUpCallPending(boolean z) {
        if (TelBrand.IS_DCM) {
            this.mHangupCallPending = z;
        }
    }

    public void setSpeechCodeState(String[] strArr) {
        log("setSpeechCodeState start");
        this.mSpeechCodeState = strArr;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean update(DriverCall driverCall) {
        Object obj;
        boolean z;
        boolean z2 = false;
        boolean isConnectingInOrOut = isConnectingInOrOut();
        boolean z3 = getState() == State.HOLDING;
        GsmCall parentFromDCState = parentFromDCState(driverCall.state);
        if (TelBrand.IS_DCM) {
            obj = this.mAddress;
            if (!TextUtils.isEmpty(this.redirectingNum)) {
                obj = this.mAddress + "&" + this.redirectingNum;
            }
        } else {
            obj = null;
        }
        if (this.mOrigConnection != null) {
            log("update: mOrigConnection is not null");
            z = false;
        } else {
            log(" mNumberConverted " + this.mNumberConverted);
            if (TelBrand.IS_DCM) {
                if (!(equalsHandlesNulls(obj, driverCall.number) || (this.mNumberConverted && equalsHandlesNulls(this.mConvertedNumber, driverCall.number)))) {
                    log("update: phone # changed!");
                    if (TextUtils.isEmpty(driverCall.number)) {
                        this.mAddress = driverCall.number;
                        this.redirectingNum = null;
                    } else if (driverCall.number.indexOf(38) == -1) {
                        this.mAddress = driverCall.number;
                        this.redirectingNum = null;
                    } else {
                        String[] split = driverCall.number.split("&");
                        this.mAddress = split[0];
                        this.redirectingNum = split[1];
                        log("update: address is " + this.mAddress);
                        log("update: redirectingNum is " + this.redirectingNum);
                    }
                    z = true;
                }
            } else if (!(equalsHandlesNulls(this.mAddress, driverCall.number) || (this.mNumberConverted && equalsHandlesNulls(this.mConvertedNumber, driverCall.number)))) {
                log("update: phone # changed!");
                this.mAddress = driverCall.number;
                z = true;
            }
            z = false;
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
            z = true;
        } else {
            z = z || this.mParent.update(this, driverCall);
        }
        StringBuilder append = new StringBuilder().append("update: parent=").append(this.mParent).append(", hasNewParent=");
        if (parentFromDCState != this.mParent) {
            z2 = true;
        }
        log(append.append(z2).append(", wasConnectingInOrOut=").append(isConnectingInOrOut).append(", wasHolding=").append(z3).append(", isConnectingInOrOut=").append(isConnectingInOrOut()).append(", changed=").append(z).toString());
        if (isConnectingInOrOut && !isConnectingInOrOut()) {
            onConnectedInOrOut();
        }
        if (z && !z3 && getState() == State.HOLDING) {
            onStartedHolding();
        }
        return z;
    }
}
