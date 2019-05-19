package com.android.internal.telephony;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.SystemClock;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.Connection.PostDialState;
import com.android.internal.telephony.DriverCall.State;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.UiccCardApplication;

public class GsmCdmaConnection extends Connection {
    /* renamed from: -com-android-internal-telephony-DriverCall$StateSwitchesValues */
    private static final /* synthetic */ int[] f6-com-android-internal-telephony-DriverCall$StateSwitchesValues = null;
    private static final boolean DBG = true;
    static final int EVENT_DTMF_DELAY_DONE = 5;
    static final int EVENT_DTMF_DONE = 1;
    static final int EVENT_NEXT_POST_DIAL = 3;
    static final int EVENT_PAUSE_DONE = 2;
    static final int EVENT_WAKE_LOCK_TIMEOUT = 4;
    private static final String LOG_TAG = "GsmCdmaConnection";
    static final int PAUSE_DELAY_MILLIS_CDMA = 2000;
    static final int PAUSE_DELAY_MILLIS_GSM = 3000;
    private static final boolean VDBG = false;
    static final int WAKE_LOCK_TIMEOUT_MILLIS = 60000;
    boolean mAcceptCallPending = VDBG;
    long mDisconnectTime;
    boolean mDisconnected;
    private int mDtmfToneDelay = 0;
    Handler mHandler;
    boolean mHangupCallPending = VDBG;
    int mIndex;
    Connection mOrigConnection;
    GsmCdmaCallTracker mOwner;
    GsmCdmaCall mParent;
    private WakeLock mPartialWakeLock;
    int mPreciseCause = 0;
    private String[] mSpeechCodeState;
    UUSInfo mUusInfo;
    String mVendorCause;
    String redirectingNum;

    class MyHandler extends Handler {
        MyHandler(Looper l) {
            super(l);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    GsmCdmaConnection.this.mHandler.sendMessageDelayed(GsmCdmaConnection.this.mHandler.obtainMessage(5), (long) GsmCdmaConnection.this.mDtmfToneDelay);
                    return;
                case 2:
                case 3:
                case 5:
                    GsmCdmaConnection.this.processNextPostDialChar();
                    return;
                case 4:
                    GsmCdmaConnection.this.releaseWakeLock();
                    return;
                default:
                    return;
            }
        }
    }

    private static /* synthetic */ int[] -getcom-android-internal-telephony-DriverCall$StateSwitchesValues() {
        if (f6-com-android-internal-telephony-DriverCall$StateSwitchesValues != null) {
            return f6-com-android-internal-telephony-DriverCall$StateSwitchesValues;
        }
        int[] iArr = new int[State.values().length];
        try {
            iArr[State.ACTIVE.ordinal()] = 1;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[State.ALERTING.ordinal()] = 2;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[State.DIALING.ordinal()] = 3;
        } catch (NoSuchFieldError e3) {
        }
        try {
            iArr[State.HOLDING.ordinal()] = 4;
        } catch (NoSuchFieldError e4) {
        }
        try {
            iArr[State.INCOMING.ordinal()] = 5;
        } catch (NoSuchFieldError e5) {
        }
        try {
            iArr[State.WAITING.ordinal()] = 6;
        } catch (NoSuchFieldError e6) {
        }
        f6-com-android-internal-telephony-DriverCall$StateSwitchesValues = iArr;
        return iArr;
    }

    public GsmCdmaConnection(GsmCdmaPhone phone, DriverCall dc, GsmCdmaCallTracker ct, int index) {
        super(phone.getPhoneType());
        createWakeLock(phone.getContext());
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        if (!isPhoneTypeGsm() || !TelBrand.IS_DCM) {
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
            log("GsmCdmaConnection: address is " + this.mAddress);
            log("GsmCdmaConnection: redirectingNum is " + this.redirectingNum);
        }
        this.mIsIncoming = dc.isMT;
        this.mCreateTime = System.currentTimeMillis();
        this.mCnapName = dc.name;
        this.mCnapNamePresentation = dc.namePresentation;
        this.mNumberPresentation = dc.numberPresentation;
        this.mUusInfo = dc.uusInfo;
        if (isPhoneTypeGsm()) {
            logd("GsmCdmaConnection() mIsIncoming is %s", Boolean.valueOf(this.mIsIncoming));
            logd("GsmCdmaConnection() mNumberPresentation is %d", Integer.valueOf(this.mNumberPresentation));
            logd("GsmCdmaConnection() mAddress is %s", this.mAddress);
            if (this.mIsIncoming && this.mNumberPresentation == 1 && TextUtils.isEmpty(this.mAddress)) {
                logd("GsmCdmaConnection() sets PRESENTATION_UNKNOWN to mNumberPresentation");
                this.mNumberPresentation = 3;
            }
        }
        this.mIndex = index;
        this.mParent = parentFromDCState(dc.state);
        this.mParent.attach(this, dc);
        fetchDtmfToneDelay(phone);
        if (isPhoneTypeGsm() && TelBrand.IS_SBM) {
            logd("[SEQ]GsmCdmaConnection.GsmCdmaConnection(" + dc.number + ") IDLE->" + getState());
        }
    }

    public GsmCdmaConnection(GsmCdmaPhone phone, String dialString, GsmCdmaCallTracker ct, GsmCdmaCall parent) {
        super(phone.getPhoneType());
        createWakeLock(phone.getContext());
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        boolean showOrigDialString = VDBG;
        if (phone != null) {
            PersistableBundle pb = ((CarrierConfigManager) phone.getContext().getSystemService("carrier_config")).getConfigForSubId(phone.getSubId());
            if (pb != null) {
                showOrigDialString = pb.getBoolean("config_show_orig_dial_string_for_cdma");
            }
        }
        if (isPhoneTypeGsm() || showOrigDialString) {
            this.mDialString = dialString;
            logd("GsmCdmaConnection() dialString = (%s)", dialString);
            dialString = formatDialStringForGsm(dialString);
            logd("GsmCdmaConnection() formated dialString = (%s)", dialString);
        }
        if (!isPhoneTypeGsm()) {
            Rlog.d(LOG_TAG, "[GsmCdmaConn] GsmCdmaConnection: dialString=" + maskDialString(dialString));
            dialString = formatDialString(dialString);
            Rlog.d(LOG_TAG, "[GsmCdmaConn] GsmCdmaConnection:formated dialString=" + maskDialString(dialString));
        }
        this.mAddress = PhoneNumberUtils.extractNetworkPortionAlt(dialString);
        this.mPostDialString = PhoneNumberUtils.extractPostDialPortion(dialString);
        this.mIndex = -1;
        this.mIsIncoming = VDBG;
        this.mCnapName = null;
        this.mCnapNamePresentation = 1;
        this.mNumberPresentation = 1;
        this.mCreateTime = System.currentTimeMillis();
        if (parent != null) {
            this.mParent = parent;
            if (isPhoneTypeGsm()) {
                parent.attachFake(this, Call.State.DIALING);
            } else if (parent.mState == Call.State.ACTIVE) {
                parent.attachFake(this, Call.State.ACTIVE);
            } else {
                parent.attachFake(this, Call.State.DIALING);
            }
        }
        fetchDtmfToneDelay(phone);
        if (isPhoneTypeGsm() && TelBrand.IS_SBM) {
            logd("[SEQ]GsmCdmaConnection.GsmCdmaConnection(" + dialString + ") IDLE->DIALING");
        }
    }

    public GsmCdmaConnection(Context context, CdmaCallWaitingNotification cw, GsmCdmaCallTracker ct, GsmCdmaCall parent) {
        super(parent.getPhone().getPhoneType());
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mAddress = cw.number;
        this.mNumberPresentation = cw.numberPresentation;
        this.mCnapName = cw.name;
        this.mCnapNamePresentation = cw.namePresentation;
        this.mIndex = -1;
        this.mIsIncoming = true;
        this.mCreateTime = System.currentTimeMillis();
        this.mConnectTime = 0;
        this.mParent = parent;
        parent.attachFake(this, Call.State.WAITING);
    }

    public void dispose() {
        clearPostDialListeners();
        releaseAllWakeLocks();
    }

    static boolean equalsHandlesNulls(Object a, Object b) {
        if (a == null) {
            return b == null ? true : VDBG;
        } else {
            return a.equals(b);
        }
    }

    public static String formatDialString(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        int length = phoneNumber.length();
        StringBuilder ret = new StringBuilder();
        int currIndex = 0;
        while (currIndex < length) {
            char c = phoneNumber.charAt(currIndex);
            if (!isPause(c) && !isWait(c)) {
                ret.append(c);
            } else if (currIndex < length - 1) {
                int nextIndex = findNextPCharOrNonPOrNonWCharIndex(phoneNumber, currIndex);
                if (nextIndex < length) {
                    ret.append(findPOrWCharToAppend(phoneNumber, currIndex, nextIndex));
                    if (nextIndex > currIndex + 1) {
                        currIndex = nextIndex - 1;
                    }
                } else if (nextIndex == length) {
                    currIndex = length - 1;
                }
            }
            currIndex++;
        }
        return PhoneNumberUtils.cdmaCheckAndProcessPlusCode(ret.toString());
    }

    public static String formatDialStringForGsm(String phoneNumber) {
        logv("formatDialStringForGsm( String(%s) ) start", phoneNumber);
        if (phoneNumber == null) {
            loge("formatDialStringForGsm() parameter is invalid, return String(null)");
            return null;
        }
        int length = phoneNumber.length();
        StringBuilder ret = new StringBuilder();
        int currIndex = 0;
        while (currIndex < length) {
            char c = phoneNumber.charAt(currIndex);
            if (PhoneNumberUtils.isDialable(c)) {
                logd("formatDialStringForGsm() number(%c) of currIndex can be dial.", Character.valueOf(c));
                ret.append(c);
            } else if (isPauseForGsm(c) || isWaitForGsm(c)) {
                logd("formatDialStringForGsm() number(%c) of currIndex is PAUSE or WAIT or WAIT_EX", Character.valueOf(c));
                if (currIndex < length - 1) {
                    logd("formatDialStringForGsm() currIndex(%d) < length - 1(%d)", Integer.valueOf(currIndex), Integer.valueOf(length - 1));
                    int nextIndex = findNextPCharOrNonPOrNonWCharIndexForGsm(phoneNumber, currIndex);
                    if (nextIndex < length) {
                        logd("formatDialStringForGsm() nextIndex(%d) < length(%d)", Integer.valueOf(nextIndex), Integer.valueOf(length));
                        ret.append(findPOrWCharToAppendForGsm(phoneNumber, currIndex, nextIndex));
                        if (nextIndex > currIndex + 1) {
                            logd("formatDialStringForGsm() nextIndex(%d) > ( currIndex + 1 )(%d)", Integer.valueOf(nextIndex), Integer.valueOf(currIndex + 1));
                            currIndex = nextIndex - 1;
                        }
                    } else if (nextIndex == length) {
                        logd("formatDialStringForGsm() nextIndex(%d) == length(%d)", Integer.valueOf(nextIndex), Integer.valueOf(length));
                        currIndex = length - 1;
                    }
                }
            } else {
                logd("formatDialStringForGsm() number of currIndex is (%c)", Character.valueOf(c));
                ret.append(c);
            }
            currIndex++;
        }
        logv("formatDialStringForGsm() end, return String(%s)", ret.toString());
        return ret.toString();
    }

    /* Access modifiers changed, original: 0000 */
    public boolean compareTo(DriverCall c) {
        boolean z = VDBG;
        if (!(!this.mIsIncoming ? c.isMT : true)) {
            return true;
        }
        if (isPhoneTypeGsm() && this.mOrigConnection != null) {
            return true;
        }
        Object address_old = null;
        if (isPhoneTypeGsm() && TelBrand.IS_DCM) {
            address_old = this.mAddress;
            if (!TextUtils.isEmpty(this.redirectingNum)) {
                address_old = address_old + "&" + this.redirectingNum;
            }
        }
        String cAddress = PhoneNumberUtils.stringFromStringAndTOA(c.number, c.TOA);
        if (isPhoneTypeGsm() && TelBrand.IS_DCM) {
            if (this.mIsIncoming == c.isMT) {
                z = equalsHandlesNulls(address_old, cAddress);
            }
            return z;
        }
        if (this.mIsIncoming == c.isMT) {
            z = equalsHandlesNulls(this.mAddress, cAddress);
        }
        return z;
    }

    public String getOrigDialString() {
        return this.mDialString;
    }

    public GsmCdmaCall getCall() {
        return this.mParent;
    }

    public long getDisconnectTime() {
        return this.mDisconnectTime;
    }

    public long getHoldDurationMillis() {
        if (getState() != Call.State.HOLDING) {
            return 0;
        }
        return SystemClock.elapsedRealtime() - this.mHoldingStartTime;
    }

    public Call.State getState() {
        if (this.mDisconnected) {
            return Call.State.DISCONNECTED;
        }
        return super.getState();
    }

    public String[] getSpeechCodeState() {
        if (isPhoneTypeGsm()) {
            log("getSpeechCodeState() start");
            return this.mSpeechCodeState;
        }
        Rlog.e(LOG_TAG, "UNEXPECTED : getSpeechCodeState is not used by CDMA.");
        return null;
    }

    public void hangup() throws CallStateException {
        if (this.mDisconnected) {
            throw new CallStateException("disconnected");
        }
        this.mOwner.hangup(this);
    }

    public void separate() throws CallStateException {
        if (this.mDisconnected) {
            throw new CallStateException("disconnected");
        }
        this.mOwner.separate(this);
    }

    public void proceedAfterWaitChar() {
        if (!isPhoneTypeGsm() || TelBrand.IS_SBM) {
            if (this.mPostDialState != PostDialState.WAIT) {
                Rlog.w(LOG_TAG, "GsmCdmaConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WAIT but was " + this.mPostDialState);
                return;
            }
        } else if (!(this.mPostDialState == PostDialState.WAIT || this.mPostDialState == PostDialState.WAIT_EX)) {
            Rlog.w(LOG_TAG, "GsmCdmaConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WAIT but was " + this.mPostDialState);
            return;
        }
        setPostDialState(PostDialState.STARTED);
        processNextPostDialChar();
    }

    public void proceedAfterWildChar(String str) {
        if (this.mPostDialState != PostDialState.WILD) {
            Rlog.w(LOG_TAG, "GsmCdmaConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WILD but was " + this.mPostDialState);
            return;
        }
        setPostDialState(PostDialState.STARTED);
        StringBuilder buf = new StringBuilder(str);
        buf.append(this.mPostDialString.substring(this.mNextPostDialChar));
        this.mPostDialString = buf.toString();
        this.mNextPostDialChar = 0;
        log("proceedAfterWildChar: new postDialString is " + this.mPostDialString);
        processNextPostDialChar();
    }

    public void cancelPostDial() {
        setPostDialState(PostDialState.CANCELLED);
    }

    /* Access modifiers changed, original: 0000 */
    public void onHangupLocal() {
        this.mCause = 3;
        this.mPreciseCause = 0;
        this.mVendorCause = null;
    }

    /* Access modifiers changed, original: 0000 */
    public int disconnectCauseFromCode(int causeCode) {
        switch (causeCode) {
            case 1:
                return 25;
            case 3:
                return 52;
            case 6:
                return 58;
            case 8:
                return 53;
            case 17:
                return 4;
            case 18:
                return 54;
            case 19:
                return 55;
            case 21:
                return 59;
            case 22:
                return 60;
            case 25:
                return 61;
            case 26:
                return 99;
            case CallFailCause.CALL_FAIL_DESTINATION_OUT_OF_ORDER /*27*/:
                return 56;
            case CallFailCause.INVALID_NUMBER /*28*/:
                return 7;
            case CallFailCause.FACILITY_REJECTED /*29*/:
                return 62;
            case CallFailCause.STATUS_ENQUIRY /*30*/:
                return 63;
            case 31:
                return 64;
            case 34:
                return 51;
            case 38:
                return 65;
            case 41:
                return 66;
            case 42:
                return 67;
            case CallFailCause.ACCESS_INFORMATION_DISCARDED /*43*/:
                return 68;
            case CallFailCause.CHANNEL_NOT_AVAIL /*44*/:
                return 69;
            case 47:
                return 70;
            case CallFailCause.QOS_NOT_AVAIL /*49*/:
                return 71;
            case 50:
                return 72;
            case 55:
                return 73;
            case 57:
                return 57;
            case 58:
                return 74;
            case 63:
                return 75;
            case CallFailCause.BEARER_SERVICE_NOT_IMPLEMENTED /*65*/:
                return 76;
            case CallFailCause.ACM_LIMIT_EXCEEDED /*68*/:
                return 15;
            case CallFailCause.REQUESTED_FACILITY_NOT_IMPLEMENTED /*69*/:
                return 77;
            case CallFailCause.ONLY_DIGITAL_INFORMATION_BEARER_AVAILABLE /*70*/:
                return 78;
            case 79:
                return 79;
            case 81:
                return 80;
            case CallFailCause.USER_NOT_MEMBER_OF_CUG /*87*/:
                return 81;
            case CallFailCause.INCOMPATIBLE_DESTINATION /*88*/:
                return 82;
            case CallFailCause.INVALID_TRANSIT_NW_SELECTION /*91*/:
                return 83;
            case CallFailCause.SEMANTICALLY_INCORRECT_MESSAGE /*95*/:
                return 84;
            case 96:
                return 85;
            case CallFailCause.MESSAGE_TYPE_NON_IMPLEMENTED /*97*/:
                return 86;
            case CallFailCause.MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE /*98*/:
                return 87;
            case CallFailCause.INFORMATION_ELEMENT_NON_EXISTENT /*99*/:
                return 88;
            case 100:
                return 89;
            case CallFailCause.MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE /*101*/:
                return 90;
            case CallFailCause.RECOVERY_ON_TIMER_EXPIRED /*102*/:
                return 91;
            case CallFailCause.PROTOCOL_ERROR_UNSPECIFIED /*111*/:
                return 92;
            case CallFailCause.INTERWORKING_UNSPECIFIED /*127*/:
                return 93;
            case CallFailCause.CALL_BARRED /*240*/:
                return 20;
            case CallFailCause.FDN_BLOCKED /*241*/:
                return 21;
            case 244:
                return 46;
            case 245:
                return 47;
            case 246:
                return 48;
            case CallFailCause.EMERGENCY_TEMP_FAILURE /*325*/:
                return 96;
            case CallFailCause.EMERGENCY_PERM_FAILURE /*326*/:
                return 97;
            case 1000:
                return 26;
            case 1001:
                return 27;
            case 1002:
                return 28;
            case 1003:
                return 29;
            case 1004:
                return 30;
            case 1005:
                return 31;
            case 1006:
                return 32;
            case CallFailCause.CDMA_PREEMPTED /*1007*/:
                return 33;
            case CallFailCause.CDMA_NOT_EMERGENCY /*1008*/:
                return 34;
            case CallFailCause.CDMA_ACCESS_BLOCKED /*1009*/:
                return 35;
            default:
                AppState uiccAppState;
                GsmCdmaPhone phone = this.mOwner.getPhone();
                int serviceState = phone.getServiceState().getState();
                UiccCardApplication cardApp = phone.getUiccCardApplication();
                if (cardApp != null) {
                    uiccAppState = cardApp.getState();
                } else {
                    uiccAppState = AppState.APPSTATE_UNKNOWN;
                }
                if (serviceState == 3) {
                    return 17;
                }
                if (serviceState == 1 || serviceState == 2) {
                    return 18;
                }
                if (isPhoneTypeGsm()) {
                    if (uiccAppState != AppState.APPSTATE_READY) {
                        return 19;
                    }
                    if (causeCode != CallFailCause.ERROR_UNSPECIFIED) {
                        return causeCode == 16 ? 2 : 36;
                    } else {
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
                } else if (phone.mCdmaSubscriptionSource != 0 || uiccAppState == AppState.APPSTATE_READY) {
                    return causeCode == 16 ? 2 : 36;
                } else {
                    return 19;
                }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void onRemoteDisconnect(int causeCode, String vendorCause) {
        this.mPreciseCause = causeCode;
        this.mVendorCause = vendorCause;
        onDisconnect(disconnectCauseFromCode(causeCode));
    }

    public boolean onDisconnect(int cause) {
        boolean changed = VDBG;
        this.mCause = cause;
        if (isPhoneTypeGsm() && TelBrand.IS_DCM) {
            this.mHangupCallPending = VDBG;
            this.mAcceptCallPending = VDBG;
        }
        if (!this.mDisconnected) {
            doDisconnect();
            Rlog.d(LOG_TAG, "onDisconnect: cause=" + cause);
            this.mOwner.getPhone().notifyDisconnect(this);
            if (this.mParent != null) {
                changed = this.mParent.connectionDisconnected(this);
            }
            this.mOrigConnection = null;
        }
        clearPostDialListeners();
        releaseWakeLock();
        return changed;
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

    public boolean update(DriverCall dc) {
        boolean z = true;
        boolean changed = VDBG;
        boolean wasConnectingInOrOut = isConnectingInOrOut();
        boolean wasHolding = getState() == Call.State.HOLDING ? true : VDBG;
        GsmCdmaCall newParent = parentFromDCState(dc.state);
        log("parent= " + this.mParent + ", newParent= " + newParent);
        Object address_old = null;
        if (isPhoneTypeGsm() && TelBrand.IS_DCM) {
            address_old = this.mAddress;
            if (!TextUtils.isEmpty(this.redirectingNum)) {
                address_old = this.mAddress + "&" + this.redirectingNum;
            }
        }
        if (!isPhoneTypeGsm() || this.mOrigConnection == null) {
            log(" mNumberConverted " + this.mNumberConverted);
            if (isPhoneTypeGsm() && TelBrand.IS_DCM) {
                if (!(equalsHandlesNulls(address_old, dc.number) || (this.mNumberConverted && equalsHandlesNulls(this.mConvertedNumber, dc.number)))) {
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
                    changed = true;
                }
            } else if (!(equalsHandlesNulls(this.mAddress, dc.number) || (this.mNumberConverted && equalsHandlesNulls(this.mConvertedNumber, dc.number)))) {
                log("update: phone # changed!");
                this.mAddress = dc.number;
                changed = true;
            }
        } else {
            log("update: mOrigConnection is not null");
        }
        if (TextUtils.isEmpty(dc.name)) {
            if (!TextUtils.isEmpty(this.mCnapName)) {
                changed = true;
                this.mCnapName = "";
            }
        } else if (!dc.name.equals(this.mCnapName)) {
            changed = true;
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
            changed = !changed ? this.mParent.update(this, dc) : true;
        }
        StringBuilder append = new StringBuilder().append("update: parent=").append(this.mParent).append(", hasNewParent=");
        if (newParent == this.mParent) {
            z = VDBG;
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

    /* Access modifiers changed, original: 0000 */
    public void fakeHoldBeforeDial() {
        if (isPhoneTypeGsm() && TelBrand.IS_SBM) {
            logd("[SEQ]GsmCdmaConnection.fakeHoldBeforeDial(" + this.mAddress + ") " + getState() + "->HOLDING");
        }
        if (this.mParent != null) {
            this.mParent.detach(this);
        }
        this.mParent = this.mOwner.mBackgroundCall;
        this.mParent.attachFake(this, Call.State.HOLDING);
        onStartedHolding();
    }

    /* Access modifiers changed, original: 0000 */
    public int getGsmCdmaIndex() throws CallStateException {
        if (this.mIndex >= 0) {
            return this.mIndex + 1;
        }
        throw new CallStateException("GsmCdma index not yet assigned");
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

    private void doDisconnect() {
        this.mIndex = -1;
        this.mDisconnectTime = System.currentTimeMillis();
        this.mDuration = SystemClock.elapsedRealtime() - this.mConnectTimeReal;
        this.mDisconnected = true;
        clearPostDialListeners();
    }

    /* Access modifiers changed, original: 0000 */
    public void onStartedHolding() {
        this.mHoldingStartTime = SystemClock.elapsedRealtime();
    }

    private boolean processPostDialChar(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            this.mOwner.mCi.sendDtmf(c, this.mHandler.obtainMessage(1));
        } else if (isPause(c)) {
            if (!isPhoneTypeGsm()) {
                setPostDialState(PostDialState.PAUSE);
            }
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(2), (long) (isPhoneTypeGsm() ? PAUSE_DELAY_MILLIS_GSM : 2000));
            if (isPhoneTypeGsm() && TelBrand.IS_SBM) {
                setPostDialState(PostDialState.PAUSE);
            }
        } else if (isWait(c)) {
            setPostDialState(PostDialState.WAIT);
        } else if (isPhoneTypeGsm() && c == 'P') {
            setPostDialState(PostDialState.WAIT_EX);
        } else if (!isWild(c)) {
            return VDBG;
        } else {
            setPostDialState(PostDialState.WILD);
        }
        return true;
    }

    public String getRemainingPostDialString() {
        String subStr = super.getRemainingPostDialString();
        int wIndex;
        int pIndex;
        if (isPhoneTypeGsm() && !TelBrand.IS_DCM) {
            if (!TextUtils.isEmpty(subStr)) {
                wIndex = subStr.indexOf(59);
                pIndex = subStr.indexOf(44);
                int wexIndex = subStr.indexOf(80);
                if (wIndex > 0 && ((wIndex < pIndex || pIndex <= 0) && (wIndex < wexIndex || wexIndex <= 0))) {
                    subStr = subStr.substring(0, wIndex);
                } else if (wexIndex > 0 && ((wexIndex < pIndex || pIndex <= 0) && (wexIndex < wIndex || wIndex <= 0))) {
                    subStr = subStr.substring(0, wexIndex);
                } else if (pIndex > 0) {
                    subStr = subStr.substring(0, pIndex);
                }
                Rlog.d(LOG_TAG, "getRemainingPostDialString() wIndex(" + wIndex + "), pIndex(" + pIndex + "), wexIndex(" + wexIndex + ")");
            }
            Rlog.v(LOG_TAG, "getRemainingPostDialString() end, return String(" + subStr + ")");
            return subStr;
        } else if (isPhoneTypeGsm() || TextUtils.isEmpty(subStr)) {
            return subStr;
        } else {
            wIndex = subStr.indexOf(59);
            pIndex = subStr.indexOf(44);
            if (wIndex > 0 && (wIndex < pIndex || pIndex <= 0)) {
                return subStr.substring(0, wIndex);
            }
            if (pIndex > 0) {
                return subStr.substring(0, pIndex);
            }
            return subStr;
        }
    }

    public void updateParent(GsmCdmaCall oldParent, GsmCdmaCall newParent) {
        if (newParent != oldParent) {
            if (oldParent != null) {
                oldParent.detach(this);
            }
            newParent.attachFake(this, Call.State.ACTIVE);
            this.mParent = newParent;
        }
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        if (this.mPartialWakeLock.isHeld()) {
            Rlog.e(LOG_TAG, "[GsmCdmaConn] UNEXPECTED; mPartialWakeLock is held when finalizing.");
        }
        clearPostDialListeners();
        releaseWakeLock();
    }

    private void processNextPostDialChar() {
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
                Rlog.e(LOG_TAG, "processNextPostDialChar: c=" + c + " isn't valid!");
                return;
            }
        }
        notifyPostDialListenersNextChar(c);
        Registrant postDialHandler = this.mOwner.getPhone().getPostDialHandler();
        if (postDialHandler != null) {
            Message notifyMessage = postDialHandler.messageForRegistrant();
            if (notifyMessage != null) {
                PostDialState state = this.mPostDialState;
                AsyncResult ar = AsyncResult.forMessage(notifyMessage);
                ar.result = this;
                ar.userObj = state;
                notifyMessage.arg1 = c;
                notifyMessage.sendToTarget();
            }
        }
    }

    private boolean isConnectingInOrOut() {
        if (this.mParent == null || this.mParent == this.mOwner.mRingingCall || this.mParent.mState == Call.State.DIALING || this.mParent.mState == Call.State.ALERTING) {
            return true;
        }
        return VDBG;
    }

    private GsmCdmaCall parentFromDCState(State state) {
        switch (-getcom-android-internal-telephony-DriverCall$StateSwitchesValues()[state.ordinal()]) {
            case 1:
            case 2:
            case 3:
                return this.mOwner.mForegroundCall;
            case 4:
                return this.mOwner.mBackgroundCall;
            case 5:
            case 6:
                return this.mOwner.mRingingCall;
            default:
                throw new RuntimeException("illegal call state: " + state);
        }
    }

    private void setPostDialState(PostDialState s) {
        if (s == PostDialState.STARTED || s == PostDialState.PAUSE) {
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
        this.mPostDialState = s;
        notifyPostDialListeners();
    }

    public void setSpeechCodeState(String[] codec) {
        if (isPhoneTypeGsm()) {
            log("setSpeechCodeState start");
            this.mSpeechCodeState = codec;
            return;
        }
        Rlog.e(LOG_TAG, "UNEXPECTED : setSpeechCodeState is not used by CDMA.");
    }

    private void createWakeLock(Context context) {
        this.mPartialWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, LOG_TAG);
    }

    private void acquireWakeLock() {
        log("acquireWakeLock");
        this.mPartialWakeLock.acquire();
    }

    private void releaseWakeLock() {
        synchronized (this.mPartialWakeLock) {
            if (this.mPartialWakeLock.isHeld()) {
                log("releaseWakeLock");
                this.mPartialWakeLock.release();
            }
        }
    }

    private void releaseAllWakeLocks() {
        synchronized (this.mPartialWakeLock) {
            while (this.mPartialWakeLock.isHeld()) {
                this.mPartialWakeLock.release();
            }
        }
    }

    private static boolean isPause(char c) {
        return c == ',' ? true : VDBG;
    }

    private static boolean isWait(char c) {
        return c == ';' ? true : VDBG;
    }

    private static boolean isPauseForGsm(char c) {
        boolean z = true;
        String str;
        Object[] objArr;
        if (TelBrand.IS_SBM) {
            str = "isPauseForGsm( char(%c) ) return boolean(%s) ";
            objArr = new Object[2];
            objArr[0] = Character.valueOf(c);
            boolean z2 = (c == ',' || c == 'P') ? true : VDBG;
            objArr[1] = Boolean.valueOf(z2);
            logv(str, objArr);
            if (!(c == ',' || c == 'P')) {
                z = VDBG;
            }
            return z;
        }
        str = "isPauseForGsm( char(%c) ) return boolean(%s) ";
        objArr = new Object[2];
        objArr[0] = Character.valueOf(c);
        objArr[1] = Boolean.valueOf(c == ',' ? true : VDBG);
        logv(str, objArr);
        if (c != ',') {
            z = VDBG;
        }
        return z;
    }

    private static boolean isWaitForGsm(char c) {
        boolean z = true;
        String str;
        Object[] objArr;
        if (TelBrand.IS_SBM) {
            str = "isWaitForGsm( char(%c) ) return boolean(%s) ";
            objArr = new Object[2];
            objArr[0] = Character.valueOf(c);
            objArr[1] = Boolean.valueOf(c == ';' ? true : VDBG);
            logv(str, objArr);
            if (c != ';') {
                z = VDBG;
            }
            return z;
        }
        str = "isWaitForGsm( char(%c) ) return boolean(%s) ";
        objArr = new Object[2];
        objArr[0] = Character.valueOf(c);
        boolean z2 = (c == ';' || c == 'P') ? true : VDBG;
        objArr[1] = Boolean.valueOf(z2);
        logv(str, objArr);
        if (!(c == ';' || c == 'P')) {
            z = VDBG;
        }
        return z;
    }

    private static boolean isWild(char c) {
        return c == 'N' ? true : VDBG;
    }

    private static int findNextPCharOrNonPOrNonWCharIndex(String phoneNumber, int currIndex) {
        boolean wMatched = isWait(phoneNumber.charAt(currIndex));
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
        if (index >= length || index <= currIndex + 1 || wMatched || !isPause(phoneNumber.charAt(currIndex))) {
            return index;
        }
        return currIndex + 1;
    }

    private static int findNextPCharOrNonPOrNonWCharIndexForGsm(String phoneNumber, int currIndex) {
        logv("findNextPCharOrNonPOrNonWCharIndexForGsm( Strint(%s), int(%d) ) start", phoneNumber, Integer.valueOf(currIndex));
        boolean wMatched = VDBG;
        int index = currIndex + 1;
        int length = phoneNumber.length();
        while (index < length) {
            char cNext = phoneNumber.charAt(index);
            if (isWaitForGsm(cNext)) {
                wMatched = true;
            }
            if (!isWaitForGsm(cNext) && !isPauseForGsm(cNext)) {
                break;
            }
            index++;
        }
        logd("findNextPCharOrNonPOrNonWCharIndexForGsm() wMatched(%s), index(%d), length(%d)", Boolean.valueOf(wMatched), Integer.valueOf(index), Integer.valueOf(length));
        if (index >= length || index <= currIndex + 1 || wMatched || !isPause(phoneNumber.charAt(currIndex))) {
            logv("findNextPCharOrNonPOrNonWCharIndexForGsm() end, return int(%d)", Integer.valueOf(index));
            return index;
        }
        logd("findNextPCharOrNonPOrNonWCharIndexForGsm() PAUSE character(s) needs to be handled one by one, return int(%d)", Integer.valueOf(currIndex + 1));
        return currIndex + 1;
    }

    private static char findPOrWCharToAppend(String phoneNumber, int currPwIndex, int nextNonPwCharIndex) {
        char ret = isPause(phoneNumber.charAt(currPwIndex)) ? ',' : ';';
        if (nextNonPwCharIndex > currPwIndex + 1) {
            return ';';
        }
        return ret;
    }

    private static char findPOrWCharToAppendForGsm(String phoneNumber, int currPwIndex, int nextNonPwCharIndex) {
        logv("findPOrWCharToAppendForGsm( String(%s), int(%d), int(%d) ) start", phoneNumber, Integer.valueOf(currPwIndex), Integer.valueOf(nextNonPwCharIndex));
        char c = phoneNumber.charAt(currPwIndex);
        char ret = isPauseForGsm(c) ? ',' : ';';
        if (isPauseForGsm(c) && nextNonPwCharIndex > currPwIndex + 1) {
            ret = ';';
            logd("findPOrWCharToAppendForGsm() returns WAIT character to append, ret(%c)", Character.valueOf(';'));
        }
        logv("findPOrWCharToAppendForGsm() end, return char(%c)", Character.valueOf(ret));
        return ret;
    }

    private String maskDialString(String dialString) {
        return "<MASKED>";
    }

    private void fetchDtmfToneDelay(GsmCdmaPhone phone) {
        PersistableBundle b = ((CarrierConfigManager) phone.getContext().getSystemService("carrier_config")).getConfigForSubId(phone.getSubId());
        if (b != null) {
            this.mDtmfToneDelay = b.getInt(phone.getDtmfToneDelayKey());
        }
    }

    private boolean isPhoneTypeGsm() {
        return this.mOwner.getPhone().getPhoneType() == 1 ? true : VDBG;
    }

    private static void logd(String format, Object... args) {
        Rlog.d(LOG_TAG, "[GsmCdmaConn] " + String.format(format, args));
    }

    private static void logd(String msg) {
        Rlog.d(LOG_TAG, "[GsmCdmaConn] " + msg);
    }

    private static void logv(String format, Object... args) {
        Rlog.v(LOG_TAG, "[GsmCdmaConn] " + String.format(format, args));
    }

    private static void logv(String msg) {
        Rlog.v(LOG_TAG, "[GsmCdmaConn] " + msg);
    }

    private static void loge(String msg) {
        Rlog.e(LOG_TAG, "[GsmCdmaConn] " + msg);
    }

    private void log(String msg) {
        Rlog.d(LOG_TAG, "[GsmCdmaConn] " + msg);
    }

    public int getNumberPresentation() {
        return this.mNumberPresentation;
    }

    public UUSInfo getUUSInfo() {
        return this.mUusInfo;
    }

    public int getPreciseDisconnectCause() {
        return this.mPreciseCause;
    }

    public String getVendorDisconnectCause() {
        return this.mVendorCause;
    }

    public void migrateFrom(Connection c) {
        if (c != null) {
            super.migrateFrom(c);
            this.mUusInfo = c.getUUSInfo();
            setUserData(c.getUserData());
        }
    }

    public Connection getOrigConnection() {
        return this.mOrigConnection;
    }

    public boolean isMultiparty() {
        if (this.mOrigConnection != null) {
            return this.mOrigConnection.isMultiparty();
        }
        return VDBG;
    }

    public String getBeforeForwardingNumber() {
        if (!isPhoneTypeGsm()) {
            Rlog.e(LOG_TAG, "UNEXPECTED : getBeforeForwardingNumber is not used by CDMA.");
            return null;
        } else if (TelBrand.IS_DCM) {
            return this.redirectingNum;
        } else {
            return null;
        }
    }

    public boolean getHangUpCallPending() {
        if (!isPhoneTypeGsm()) {
            Rlog.e(LOG_TAG, "UNEXPECTED : getHangUpCallPending is not used by CDMA.");
            return VDBG;
        } else if (TelBrand.IS_DCM) {
            return this.mHangupCallPending;
        } else {
            return VDBG;
        }
    }

    public void setHangUpCallPending(boolean hangupCallPending) {
        if (isPhoneTypeGsm()) {
            if (TelBrand.IS_DCM) {
                this.mHangupCallPending = hangupCallPending;
            }
            return;
        }
        Rlog.e(LOG_TAG, "UNEXPECTED : setHangUpCallPending is not used by CDMA.");
    }

    public boolean getAcceptCallPending() {
        if (!isPhoneTypeGsm()) {
            Rlog.e(LOG_TAG, "UNEXPECTED : getAcceptCallPending is not used by CDMA.");
            return VDBG;
        } else if (TelBrand.IS_DCM) {
            return this.mAcceptCallPending;
        } else {
            return VDBG;
        }
    }

    public void setAcceptCallPending(boolean acceptCallPending) {
        if (isPhoneTypeGsm()) {
            if (TelBrand.IS_DCM) {
                this.mAcceptCallPending = acceptCallPending;
            }
            return;
        }
        Rlog.e(LOG_TAG, "UNEXPECTED : setAcceptCallPending is not used by CDMA.");
    }
}
