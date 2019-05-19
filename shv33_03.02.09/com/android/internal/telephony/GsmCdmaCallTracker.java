package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import com.android.internal.telephony.ITelephony.Stub;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.PhoneInternalInterface.SuppService;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GsmCdmaCallTracker extends CallTracker {
    private static final boolean DBG_POLL = false;
    private static final String LOG_TAG = "GsmCdmaCallTracker";
    private static final int MAX_CONNECTIONS_CDMA = 8;
    public static final int MAX_CONNECTIONS_GSM = 19;
    private static final int MAX_CONNECTIONS_PER_CALL_CDMA = 1;
    private static final int MAX_CONNECTIONS_PER_CALL_GSM = 5;
    private static final boolean REPEAT_POLLING = false;
    private static final boolean VDBG = false;
    int[] SpeechCodecState;
    boolean acceptCallPending = DBG_POLL;
    int hangupCallOperations = 0;
    private int m3WayCallFlashDelay;
    public GsmCdmaCall mBackgroundCall = new GsmCdmaCall(this);
    private RegistrantList mCallWaitingRegistrants = new RegistrantList();
    private GsmCdmaConnection[] mConnections;
    private boolean mDesiredMute = DBG_POLL;
    private ArrayList<GsmCdmaConnection> mDroppedDuringPoll = new ArrayList(19);
    private BroadcastReceiver mEcmExitReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED")) {
                boolean isInEcm = intent.getBooleanExtra("phoneinECMState", GsmCdmaCallTracker.DBG_POLL);
                GsmCdmaCallTracker.this.log("Received ACTION_EMERGENCY_CALLBACK_MODE_CHANGED isInEcm = " + isInEcm);
                if (!isInEcm) {
                    List<Connection> toNotify = new ArrayList();
                    toNotify.addAll(GsmCdmaCallTracker.this.mRingingCall.getConnections());
                    toNotify.addAll(GsmCdmaCallTracker.this.mForegroundCall.getConnections());
                    toNotify.addAll(GsmCdmaCallTracker.this.mBackgroundCall.getConnections());
                    if (GsmCdmaCallTracker.this.mPendingMO != null) {
                        toNotify.add(GsmCdmaCallTracker.this.mPendingMO);
                    }
                    for (Connection connection : toNotify) {
                        if (connection != null) {
                            connection.onExitedEcmMode();
                        }
                    }
                }
            }
        }
    };
    private TelephonyEventLog mEventLog;
    public GsmCdmaCall mForegroundCall = new GsmCdmaCall(this);
    private boolean mHangupPendingMO;
    private boolean mIsEcmTimerCanceled;
    private boolean mIsInEmergencyCall;
    private int mPendingCallClirMode;
    private boolean mPendingCallInEcm;
    private GsmCdmaConnection mPendingMO;
    private GsmCdmaPhone mPhone;
    public GsmCdmaCall mRingingCall = new GsmCdmaCall(this);
    public State mState = State.IDLE;
    private RegistrantList mVoiceCallEndedRegistrants = new RegistrantList();
    private RegistrantList mVoiceCallStartedRegistrants = new RegistrantList();

    public GsmCdmaCallTracker(GsmCdmaPhone phone) {
        this.mPhone = phone;
        this.mCi = phone.mCi;
        this.mCi.registerForCallStateChanged(this, 2, null);
        this.mCi.registerForOn(this, 9, null);
        this.mCi.registerForNotAvailable(this, 10, null);
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
        this.mPhone.getContext().registerReceiver(this.mEcmExitReceiver, filter);
        updatePhoneType(true);
        this.mEventLog = new TelephonyEventLog(this.mPhone.getPhoneId());
        if (isPhoneTypeGsm() && TelBrand.IS_SBM) {
            this.mCi.setOnSpeechCodec(this, 10001, null);
        }
    }

    public void updatePhoneType() {
        updatePhoneType(DBG_POLL);
    }

    private void updatePhoneType(boolean duringInit) {
        if (!duringInit) {
            reset();
            pollCallsWhenSafe();
        }
        if (this.mPhone.isPhoneTypeGsm()) {
            this.mConnections = new GsmCdmaConnection[19];
            this.mCi.unregisterForCallWaitingInfo(this);
            return;
        }
        this.mConnections = new GsmCdmaConnection[8];
        this.mPendingCallInEcm = DBG_POLL;
        this.mIsInEmergencyCall = DBG_POLL;
        this.mPendingCallClirMode = 0;
        this.mIsEcmTimerCanceled = DBG_POLL;
        this.m3WayCallFlashDelay = 0;
        this.mCi.registerForCallWaitingInfo(this, 15, null);
    }

    private void reset() {
        Rlog.d(LOG_TAG, "reset");
        if (isPhoneTypeGsm() && TelBrand.IS_SBM) {
            this.SpeechCodecState = null;
            this.mCi.unSetOnSpeechCodec(this);
        }
        clearDisconnected();
        for (GsmCdmaConnection gsmCdmaConnection : this.mConnections) {
            if (gsmCdmaConnection != null) {
                gsmCdmaConnection.dispose();
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        Rlog.d(LOG_TAG, "GsmCdmaCallTracker finalized");
    }

    public void registerForVoiceCallStarted(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mVoiceCallStartedRegistrants.add(r);
        if (this.mState != State.IDLE) {
            r.notifyRegistrant(new AsyncResult(null, null, null));
        }
    }

    public void unregisterForVoiceCallStarted(Handler h) {
        this.mVoiceCallStartedRegistrants.remove(h);
    }

    public void registerForVoiceCallEnded(Handler h, int what, Object obj) {
        this.mVoiceCallEndedRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForVoiceCallEnded(Handler h) {
        this.mVoiceCallEndedRegistrants.remove(h);
    }

    public void registerForCallWaiting(Handler h, int what, Object obj) {
        this.mCallWaitingRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForCallWaiting(Handler h) {
        this.mCallWaitingRegistrants.remove(h);
    }

    private void fakeHoldForegroundBeforeDial() {
        List<Connection> connCopy = (List) this.mForegroundCall.mConnections.clone();
        int s = connCopy.size();
        for (int i = 0; i < s; i++) {
            ((GsmCdmaConnection) connCopy.get(i)).fakeHoldBeforeDial();
        }
    }

    public synchronized Connection dial(String dialString, int clirMode, UUSInfo uusInfo, Bundle intentExtras) throws CallStateException {
        clearDisconnected();
        if (canDial()) {
            String origNumber = dialString;
            dialString = convertNumberIfNecessary(this.mPhone, dialString);
            if (this.mForegroundCall.getState() == Call.State.ACTIVE) {
                switchWaitingOrHoldingAndActive();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                }
                fakeHoldForegroundBeforeDial();
            }
            if (this.mForegroundCall.getState() != Call.State.IDLE) {
                throw new CallStateException("cannot dial in current state");
            }
            this.mPendingMO = new GsmCdmaConnection(this.mPhone, checkForTestEmergencyNumber(dialString), this, this.mForegroundCall);
            this.mHangupPendingMO = DBG_POLL;
            if (isPhoneTypeGsm() && TelBrand.IS_DCM) {
                if (!(this.mPendingMO.getNumber() == null || this.mPendingMO.getNumber().length() == 0)) {
                    if (this.mPendingMO.getNumber().indexOf(78) < 0) {
                        setMute(DBG_POLL);
                        this.mCi.dial(this.mPendingMO.getNumber(), clirMode, uusInfo, obtainCompleteMessage());
                    }
                }
                this.mPendingMO.mCause = 7;
                pollCallsWhenSafe();
            } else if (this.mPendingMO.getAddress() == null || this.mPendingMO.getAddress().length() == 0 || this.mPendingMO.getAddress().indexOf(78) >= 0) {
                this.mPendingMO.mCause = 7;
                pollCallsWhenSafe();
            } else {
                setMute(DBG_POLL);
                this.mCi.dial(this.mPendingMO.getAddress(), clirMode, uusInfo, obtainCompleteMessage());
            }
            if (this.mNumberConverted) {
                this.mPendingMO.setConverted(origNumber);
                this.mNumberConverted = DBG_POLL;
            }
            updatePhoneState();
            this.mPhone.notifyPreciseCallStateChanged();
        } else {
            throw new CallStateException("cannot dial in current state");
        }
        return this.mPendingMO;
    }

    private void handleEcmTimer(int action) {
        this.mPhone.handleTimerInEmergencyCallbackMode(action);
        switch (action) {
            case 0:
                this.mIsEcmTimerCanceled = DBG_POLL;
                return;
            case 1:
                this.mIsEcmTimerCanceled = true;
                return;
            default:
                Rlog.e(LOG_TAG, "handleEcmTimer, unsupported action " + action);
                return;
        }
    }

    private void disableDataCallInEmergencyCall(String dialString) {
        if (PhoneNumberUtils.isLocalEmergencyNumber(this.mPhone.getContext(), dialString)) {
            log("disableDataCallInEmergencyCall");
            setIsInEmergencyCall();
        }
    }

    public void setIsInEmergencyCall() {
        this.mIsInEmergencyCall = true;
        this.mPhone.mDcTracker.setInternalDataEnabled(DBG_POLL);
        this.mPhone.notifyEmergencyCallRegistrants(true);
        this.mPhone.sendEmergencyCallStateChange(true);
    }

    private Connection dial(String dialString, int clirMode) throws CallStateException {
        clearDisconnected();
        if (canDial()) {
            boolean internationalRoaming;
            TelephonyManager tm = (TelephonyManager) this.mPhone.getContext().getSystemService("phone");
            String origNumber = dialString;
            String operatorIsoContry = tm.getNetworkCountryIsoForPhone(this.mPhone.getPhoneId());
            String simIsoContry = tm.getSimCountryIsoForPhone(this.mPhone.getPhoneId());
            if (TextUtils.isEmpty(operatorIsoContry) || TextUtils.isEmpty(simIsoContry)) {
                internationalRoaming = DBG_POLL;
            } else {
                internationalRoaming = simIsoContry.equals(operatorIsoContry) ? DBG_POLL : true;
            }
            if (internationalRoaming) {
                if ("us".equals(simIsoContry)) {
                    internationalRoaming = (!internationalRoaming || "vi".equals(operatorIsoContry)) ? DBG_POLL : true;
                } else if ("vi".equals(simIsoContry)) {
                    internationalRoaming = (!internationalRoaming || "us".equals(operatorIsoContry)) ? DBG_POLL : true;
                }
            }
            if (internationalRoaming) {
                dialString = convertNumberIfNecessary(this.mPhone, dialString);
            }
            boolean isPhoneInEcmMode = SystemProperties.get("ril.cdma.inecmmode", "false").equals("true");
            boolean isEmergencyCall = PhoneNumberUtils.isLocalEmergencyNumber(this.mPhone.getContext(), dialString);
            if (isPhoneInEcmMode && isEmergencyCall) {
                handleEcmTimer(1);
            }
            if (this.mForegroundCall.getState() == Call.State.ACTIVE) {
                return dialThreeWay(dialString);
            }
            this.mPendingMO = new GsmCdmaConnection(this.mPhone, checkForTestEmergencyNumber(dialString), this, this.mForegroundCall);
            this.mHangupPendingMO = DBG_POLL;
            if (this.mPendingMO.getAddress() == null || this.mPendingMO.getAddress().length() == 0 || this.mPendingMO.getAddress().indexOf(78) >= 0) {
                this.mPendingMO.mCause = 7;
                pollCallsWhenSafe();
            } else {
                setMute(DBG_POLL);
                disableDataCallInEmergencyCall(dialString);
                if (!isPhoneInEcmMode || (isPhoneInEcmMode && isEmergencyCall)) {
                    if (!isPhoneTypeGsm() && TelBrand.IS_KDDI) {
                        String outgoingNumber = this.mPendingMO.getNumber();
                        if (PhoneNumberUtils.isEmergencyNumber(this.mPendingMO.getNumber())) {
                            if (CallerInfo.sEnable) {
                                CallerInfo.TelLog(1, "dial() not add the special number", new Object[0]);
                            }
                            outgoingNumber = this.mPendingMO.getNumber();
                        } else {
                            if (CallerInfo.sEnable) {
                                CallerInfo.TelLog(1, "dial() add the special number", new Object[0]);
                            }
                            try {
                                ITelephony telephony = Stub.asInterface(ServiceManager.getService("phone"));
                                if (telephony != null) {
                                    outgoingNumber = telephony.addSpecialNumber(this.mPendingMO.getNumber());
                                } else if (CallerInfo.sEnable) {
                                    CallerInfo.TelLog(3, "dial() ITelephony is null!", new Object[0]);
                                }
                            } catch (RemoteException e) {
                                if (CallerInfo.sEnable) {
                                    CallerInfo.TelLog(3, "dial() ITelephony is exception(" + e + ")", new Object[0]);
                                }
                            }
                        }
                    }
                    this.mCi.dial(this.mPendingMO.getAddress(), clirMode, obtainCompleteMessage());
                } else {
                    this.mPhone.exitEmergencyCallbackMode();
                    this.mPhone.setOnEcbModeExitResponse(this, 14, null);
                    this.mPendingCallClirMode = clirMode;
                    this.mPendingCallInEcm = true;
                }
            }
            if (this.mNumberConverted) {
                this.mPendingMO.setConverted(origNumber);
                this.mNumberConverted = DBG_POLL;
            }
            updatePhoneState();
            this.mPhone.notifyPreciseCallStateChanged();
            return this.mPendingMO;
        }
        throw new CallStateException("cannot dial in current state");
    }

    private Connection dialThreeWay(String dialString) {
        if (this.mForegroundCall.isIdle()) {
            return null;
        }
        disableDataCallInEmergencyCall(dialString);
        this.mPendingMO = new GsmCdmaConnection(this.mPhone, checkForTestEmergencyNumber(dialString), this, this.mForegroundCall);
        this.m3WayCallFlashDelay = this.mPhone.getContext().getResources().getInteger(17694865);
        if (this.m3WayCallFlashDelay > 0) {
            this.mCi.sendCDMAFeatureCode("", obtainMessage(20));
        } else {
            this.mCi.sendCDMAFeatureCode(this.mPendingMO.getAddress(), obtainMessage(16));
        }
        return this.mPendingMO;
    }

    public Connection dial(String dialString) throws CallStateException {
        if (isPhoneTypeGsm()) {
            return dial(dialString, 0, null);
        }
        return dial(dialString, 0);
    }

    public Connection dial(String dialString, UUSInfo uusInfo, Bundle intentExtras) throws CallStateException {
        return dial(dialString, 0, uusInfo, intentExtras);
    }

    private Connection dial(String dialString, int clirMode, Bundle intentExtras) throws CallStateException {
        return dial(dialString, clirMode, null, intentExtras);
    }

    public void acceptCall() throws CallStateException {
        if (isPhoneTypeGsm() && TelBrand.IS_DCM && this.mRingingCall.isRinging()) {
            for (int i = 0; i < this.mRingingCall.getConnections().size(); i++) {
                ((GsmCdmaConnection) this.mRingingCall.getConnections().get(i)).setAcceptCallPending(true);
            }
        }
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            Rlog.i("phone", "acceptCall: incoming...");
            setMute(DBG_POLL);
            this.mCi.acceptCall(obtainCompleteMessage());
        } else if (this.mRingingCall.getState() != Call.State.WAITING) {
            throw new CallStateException("phone not ringing");
        } else if (!isPhoneTypeGsm() || !TelBrand.IS_DCM || this.hangupCallOperations <= 0) {
            if (isPhoneTypeGsm()) {
                setMute(DBG_POLL);
            } else {
                GsmCdmaConnection cwConn = (GsmCdmaConnection) this.mRingingCall.getLatestConnection();
                cwConn.updateParent(this.mRingingCall, this.mForegroundCall);
                cwConn.onConnectedInOrOut();
                updatePhoneState();
            }
            switchWaitingOrHoldingAndActive();
        } else if (!isAcceptCallPending()) {
            setAcceptCallPending();
        }
    }

    public void rejectCall() throws CallStateException {
        if (this.mRingingCall.getState().isRinging()) {
            this.mCi.rejectCall(obtainCompleteMessage());
            return;
        }
        throw new CallStateException("phone not ringing");
    }

    private void flashAndSetGenericTrue() {
        this.mCi.sendCDMAFeatureCode("", obtainMessage(8));
        this.mPhone.notifyPreciseCallStateChanged();
    }

    public void switchWaitingOrHoldingAndActive() throws CallStateException {
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            throw new CallStateException("cannot be in the incoming state");
        } else if (isPhoneTypeGsm()) {
            this.mCi.switchWaitingOrHoldingAndActive(obtainCompleteMessage(8));
        } else if (this.mForegroundCall.getConnections().size() > 1) {
            flashAndSetGenericTrue();
        } else {
            this.mCi.sendCDMAFeatureCode("", obtainMessage(8));
        }
    }

    public void conference() {
        if (isPhoneTypeGsm()) {
            this.mCi.conference(obtainCompleteMessage(11));
        } else {
            flashAndSetGenericTrue();
        }
    }

    public void explicitCallTransfer() {
        this.mCi.explicitCallTransfer(obtainCompleteMessage(13));
    }

    public void clearDisconnected() {
        internalClearDisconnected();
        updatePhoneState();
        this.mPhone.notifyPreciseCallStateChanged();
    }

    public boolean canConference() {
        if (this.mForegroundCall.getState() != Call.State.ACTIVE || this.mBackgroundCall.getState() != Call.State.HOLDING || this.mBackgroundCall.isFull() || this.mForegroundCall.isFull()) {
            return DBG_POLL;
        }
        return true;
    }

    private boolean canDial() {
        boolean ret;
        boolean z = DBG_POLL;
        int serviceState = this.mPhone.getServiceState().getState();
        String disableCall = SystemProperties.get("ro.telephony.disable-call", "false");
        if (serviceState == 3 || this.mPendingMO != null || this.mRingingCall.isRinging() || disableCall.equals("true")) {
            ret = DBG_POLL;
        } else {
            boolean z2;
            if (!this.mForegroundCall.getState().isAlive() || !this.mBackgroundCall.getState().isAlive()) {
                z2 = true;
            } else if (isPhoneTypeGsm()) {
                z2 = DBG_POLL;
            } else {
                z2 = this.mForegroundCall.getState() == Call.State.ACTIVE ? true : DBG_POLL;
            }
            ret = z2;
        }
        if (!ret) {
            String str = "canDial is false\n((serviceState=%d) != ServiceState.STATE_POWER_OFF)::=%s\n&& pendingMO == null::=%s\n&& !ringingCall.isRinging()::=%s\n&& !disableCall.equals(\"true\")::=%s\n&& (!foregroundCall.getState().isAlive()::=%s\n   || foregroundCall.getState() == GsmCdmaCall.State.ACTIVE::=%s\n   ||!backgroundCall.getState().isAlive())::=%s)";
            Object[] objArr = new Object[8];
            objArr[0] = Integer.valueOf(serviceState);
            objArr[1] = Boolean.valueOf(serviceState != 3 ? true : DBG_POLL);
            objArr[2] = Boolean.valueOf(this.mPendingMO == null ? true : DBG_POLL);
            objArr[3] = Boolean.valueOf(this.mRingingCall.isRinging() ? DBG_POLL : true);
            objArr[4] = Boolean.valueOf(disableCall.equals("true") ? DBG_POLL : true);
            objArr[5] = Boolean.valueOf(this.mForegroundCall.getState().isAlive() ? DBG_POLL : true);
            objArr[6] = Boolean.valueOf(this.mForegroundCall.getState() == Call.State.ACTIVE ? true : DBG_POLL);
            if (!this.mBackgroundCall.getState().isAlive()) {
                z = true;
            }
            objArr[7] = Boolean.valueOf(z);
            log(String.format(str, objArr));
        }
        return ret;
    }

    public boolean canTransfer() {
        boolean z = DBG_POLL;
        if (isPhoneTypeGsm()) {
            if ((this.mForegroundCall.getState() == Call.State.ACTIVE || this.mForegroundCall.getState() == Call.State.ALERTING || this.mForegroundCall.getState() == Call.State.DIALING) && this.mBackgroundCall.getState() == Call.State.HOLDING) {
                z = true;
            }
            return z;
        }
        Rlog.e(LOG_TAG, "canTransfer: not possible in CDMA");
        return DBG_POLL;
    }

    private void internalClearDisconnected() {
        this.mRingingCall.clearDisconnected();
        this.mForegroundCall.clearDisconnected();
        this.mBackgroundCall.clearDisconnected();
    }

    private Message obtainCompleteMessage() {
        return obtainCompleteMessage(4);
    }

    private Message obtainCompleteMessage(int what) {
        this.mPendingOperations++;
        this.mLastRelevantPoll = null;
        this.mNeedsPoll = true;
        return obtainMessage(what);
    }

    private Message obtainCompleteMessage(int what, int arg1, int arg2, Object obj) {
        if (!isPhoneTypeGsm()) {
            Rlog.e(LOG_TAG, "UNEXPECTED : obtainCompleteMessage is not used by CDMA.");
            return obtainMessage(0);
        } else if (!TelBrand.IS_DCM) {
            return obtainMessage(0);
        } else {
            this.mPendingOperations++;
            this.mLastRelevantPoll = null;
            this.mNeedsPoll = true;
            return obtainMessage(what, arg1, arg2, obj);
        }
    }

    private void operationComplete() {
        this.mPendingOperations--;
        if (this.mPendingOperations == 0 && this.mNeedsPoll) {
            this.mLastRelevantPoll = obtainMessage(1);
            this.mCi.getCurrentCalls(this.mLastRelevantPoll);
        } else if (this.mPendingOperations < 0) {
            Rlog.e(LOG_TAG, "GsmCdmaCallTracker.pendingOperations < 0");
            this.mPendingOperations = 0;
        }
    }

    private void updatePhoneState() {
        State oldState = this.mState;
        if (this.mRingingCall.isRinging()) {
            this.mState = State.RINGING;
        } else if (this.mPendingMO == null && this.mForegroundCall.isIdle() && this.mBackgroundCall.isIdle()) {
            Phone imsPhone = this.mPhone.getImsPhone();
            if (this.mState == State.OFFHOOK && imsPhone != null) {
                imsPhone.callEndCleanupHandOverCallIfAny();
            }
            this.mState = State.IDLE;
        } else {
            this.mState = State.OFFHOOK;
        }
        if (this.mState == State.IDLE && oldState != this.mState) {
            this.mVoiceCallEndedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
        } else if (oldState == State.IDLE && oldState != this.mState) {
            this.mVoiceCallStartedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
        }
        log("update phone state, old=" + oldState + " new=" + this.mState);
        if (this.mState != oldState) {
            this.mPhone.notifyPhoneStateChanged();
            this.mEventLog.writePhoneState(this.mState);
        }
    }

    /* Access modifiers changed, original: protected|declared_synchronized */
    /* JADX WARNING: Missing block: B:226:0x0767, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public synchronized void handlePollCalls(AsyncResult ar) {
        List polledCalls;
        Connection conn;
        Connection hoConnection;
        Iterator<Connection> it;
        Connection c;
        if (ar.exception == null) {
            polledCalls = ar.result;
        } else {
            if (isCommandExceptionRadioNotAvailable(ar.exception)) {
                polledCalls = new ArrayList();
            } else {
                pollCallsAfterDelay();
                return;
            }
        }
        Connection newRinging = null;
        ArrayList<Connection> newUnknownConnectionsGsm = new ArrayList();
        Connection newUnknownConnectionCdma = null;
        boolean hasNonHangupStateChanged = DBG_POLL;
        int hasAnyCallDisconnected = 0;
        boolean unknownConnectionAppeared = DBG_POLL;
        int handoverConnectionsSize = this.mHandoverConnections.size();
        boolean noConnectionExists = true;
        int i = 0;
        int curDC = 0;
        int dcSize = polledCalls.size();
        while (i < this.mConnections.length) {
            conn = this.mConnections[i];
            DriverCall dc = null;
            if (curDC < dcSize) {
                dc = (DriverCall) polledCalls.get(curDC);
                if (dc.index == i + 1) {
                    curDC++;
                } else {
                    dc = null;
                }
            }
            if (!(conn == null && dc == null)) {
                noConnectionExists = DBG_POLL;
            }
            if (conn == null && dc != null) {
                if (this.mPendingMO == null || !this.mPendingMO.compareTo(dc)) {
                    log("pendingMo=" + this.mPendingMO + ", dc=" + dc);
                    this.mConnections[i] = new GsmCdmaConnection(this.mPhone, dc, this, i);
                    hoConnection = getHoConnection(dc);
                    if (hoConnection != null) {
                        this.mConnections[i].migrateFrom(hoConnection);
                        if (!(hoConnection.mPreHandoverState == Call.State.ACTIVE || hoConnection.mPreHandoverState == Call.State.HOLDING || dc.state != DriverCall.State.ACTIVE)) {
                            this.mConnections[i].onConnectedInOrOut();
                        }
                        this.mHandoverConnections.remove(hoConnection);
                        if (isPhoneTypeGsm()) {
                            it = this.mHandoverConnections.iterator();
                            while (it.hasNext()) {
                                c = (Connection) it.next();
                                Rlog.i(LOG_TAG, "HO Conn state is " + c.mPreHandoverState);
                                if (c.mPreHandoverState == this.mConnections[i].getState()) {
                                    Rlog.i(LOG_TAG, "Removing HO conn " + hoConnection + c.mPreHandoverState);
                                    it.remove();
                                }
                            }
                        }
                        this.mPhone.notifyHandoverStateChanged(this.mConnections[i]);
                    } else {
                        newRinging = checkMtFindNewRinging(dc, i);
                        if (newRinging == null) {
                            unknownConnectionAppeared = true;
                            if (isPhoneTypeGsm()) {
                                newUnknownConnectionsGsm.add(this.mConnections[i]);
                            } else {
                                newUnknownConnectionCdma = this.mConnections[i];
                            }
                        }
                    }
                } else {
                    this.mConnections[i] = this.mPendingMO;
                    this.mPendingMO.mIndex = i;
                    this.mPendingMO.update(dc);
                    this.mPendingMO = null;
                    if (this.mHangupPendingMO) {
                        this.mHangupPendingMO = DBG_POLL;
                        if (!isPhoneTypeGsm() && this.mIsEcmTimerCanceled) {
                            handleEcmTimer(0);
                        }
                        try {
                            log("poll: hangupPendingMO, hangup conn " + i);
                            hangup(this.mConnections[i]);
                        } catch (CallStateException e) {
                            Rlog.e(LOG_TAG, "unexpected error on hangup");
                        }
                    }
                }
                hasNonHangupStateChanged = true;
            } else if (conn == null || dc != null) {
                if (conn != null && dc != null && !conn.compareTo(dc) && isPhoneTypeGsm()) {
                    this.mDroppedDuringPoll.add(conn);
                    this.mConnections[i] = new GsmCdmaConnection(this.mPhone, dc, this, i);
                    if (this.mConnections[i].getCall() == this.mRingingCall) {
                        newRinging = this.mConnections[i];
                    }
                    hasNonHangupStateChanged = true;
                } else if (!(conn == null || dc == null)) {
                    if (isPhoneTypeGsm() || conn.isIncoming() == dc.isMT) {
                        boolean changed = conn.update(dc);
                        if (isPhoneTypeGsm() && TelBrand.IS_DCM && changed && conn.getCall().getState() == Call.State.ACTIVE && conn.getHangUpCallPending() && conn.getAcceptCallPending()) {
                            conn.setHangUpCallPending(DBG_POLL);
                            conn.setAcceptCallPending(DBG_POLL);
                            try {
                                hangup(conn.getCall());
                            } catch (CallStateException ex) {
                                Rlog.e(LOG_TAG, "handlePollCalls caught " + ex);
                            }
                        } else {
                            hasNonHangupStateChanged = !hasNonHangupStateChanged ? changed : true;
                        }
                    } else if (dc.isMT) {
                        this.mDroppedDuringPoll.add(conn);
                        newRinging = checkMtFindNewRinging(dc, i);
                        if (newRinging == null) {
                            unknownConnectionAppeared = true;
                            newUnknownConnectionCdma = conn;
                        }
                        checkAndEnableDataCallAfterEmergencyCallDropped();
                    } else {
                        Rlog.e(LOG_TAG, "Error in RIL, Phantom call appeared " + dc);
                    }
                }
            } else if (isPhoneTypeGsm()) {
                this.mDroppedDuringPoll.add(conn);
                this.mConnections[i] = null;
            } else {
                int n;
                int count = this.mForegroundCall.mConnections.size();
                for (n = 0; n < count; n++) {
                    log("adding fgCall cn " + n + " to droppedDuringPoll");
                    this.mDroppedDuringPoll.add((GsmCdmaConnection) this.mForegroundCall.mConnections.get(n));
                }
                count = this.mRingingCall.mConnections.size();
                for (n = 0; n < count; n++) {
                    log("adding rgCall cn " + n + " to droppedDuringPoll");
                    this.mDroppedDuringPoll.add((GsmCdmaConnection) this.mRingingCall.mConnections.get(n));
                }
                if (this.mIsEcmTimerCanceled) {
                    handleEcmTimer(0);
                }
                checkAndEnableDataCallAfterEmergencyCallDropped();
                this.mConnections[i] = null;
            }
            if (isPhoneTypeGsm() && TelBrand.IS_SBM && this.mConnections[i] != null) {
                this.mConnections[i].setSpeechCodeState(getSpeechCodeState(i));
            }
            i++;
        }
        if (!isPhoneTypeGsm() && noConnectionExists) {
            checkAndEnableDataCallAfterEmergencyCallDropped();
        }
        if (this.mPendingMO != null) {
            Rlog.d(LOG_TAG, "Pending MO dropped before poll fg state:" + this.mForegroundCall.getState());
            this.mDroppedDuringPoll.add(this.mPendingMO);
            this.mPendingMO = null;
            this.mHangupPendingMO = DBG_POLL;
            if (!isPhoneTypeGsm()) {
                if (this.mPendingCallInEcm) {
                    this.mPendingCallInEcm = DBG_POLL;
                }
                checkAndEnableDataCallAfterEmergencyCallDropped();
            }
        }
        if (newRinging != null) {
            this.mPhone.notifyNewRingingConnection(newRinging);
        }
        for (i = this.mDroppedDuringPoll.size() - 1; i >= 0; i--) {
            conn = (GsmCdmaConnection) this.mDroppedDuringPoll.get(i);
            boolean wasDisconnected = DBG_POLL;
            if (conn.isIncoming() && conn.getConnectTime() == 0) {
                int cause;
                if (conn.mCause == 3) {
                    cause = 16;
                } else {
                    cause = 1;
                }
                log("missed/rejected call, conn.cause=" + conn.mCause);
                log("setting cause to " + cause);
                this.mDroppedDuringPoll.remove(i);
                hasAnyCallDisconnected |= conn.onDisconnect(cause);
                wasDisconnected = true;
            } else if (conn.mCause == 3 || conn.mCause == 7) {
                this.mDroppedDuringPoll.remove(i);
                hasAnyCallDisconnected |= conn.onDisconnect(conn.mCause);
                wasDisconnected = true;
            }
            if (!isPhoneTypeGsm() && wasDisconnected && unknownConnectionAppeared && conn == newUnknownConnectionCdma) {
                unknownConnectionAppeared = DBG_POLL;
                newUnknownConnectionCdma = null;
            }
        }
        it = this.mHandoverConnections.iterator();
        while (it.hasNext()) {
            hoConnection = (Connection) it.next();
            log("handlePollCalls - disconnect hoConn= " + hoConnection + " hoConn.State= " + hoConnection.getState());
            if (hoConnection.getState().isRinging()) {
                hoConnection.onDisconnect(1);
            } else {
                hoConnection.onDisconnect(-1);
            }
            it.remove();
        }
        if (this.mDroppedDuringPoll.size() > 0) {
            this.mCi.getLastCallFailCause(obtainNoPollCompleteMessage(5));
        }
        if (DBG_POLL) {
            pollCallsAfterDelay();
        }
        if (!(isPhoneTypeGsm() ? TelBrand.IS_SBM : DBG_POLL)) {
            if (!(newRinging == null && !hasNonHangupStateChanged && hasAnyCallDisconnected == 0)) {
                internalClearDisconnected();
            }
            updatePhoneState();
        } else if (hasAnyCallDisconnected != 0) {
            updatePhoneState();
            internalClearDisconnected();
        } else {
            if (newRinging != null || hasNonHangupStateChanged) {
                internalClearDisconnected();
            }
            updatePhoneState();
        }
        if (unknownConnectionAppeared) {
            if (isPhoneTypeGsm()) {
                for (Connection c2 : newUnknownConnectionsGsm) {
                    log("Notify unknown for " + c2);
                    this.mPhone.notifyUnknownConnection(c2);
                }
            } else {
                this.mPhone.notifyUnknownConnection(newUnknownConnectionCdma);
            }
        }
        if (!(!hasNonHangupStateChanged && newRinging == null && hasAnyCallDisconnected == 0)) {
            this.mPhone.notifyPreciseCallStateChanged();
        }
        if (handoverConnectionsSize > 0 && this.mHandoverConnections.size() == 0) {
            Phone imsPhone = this.mPhone.getImsPhone();
            if (imsPhone != null) {
                imsPhone.callEndCleanupHandOverCallIfAny();
            }
        }
    }

    private void handleRadioNotAvailable() {
        pollCallsWhenSafe();
    }

    private void dumpState() {
        int i;
        Rlog.i(LOG_TAG, "Phone State:" + this.mState);
        Rlog.i(LOG_TAG, "Ringing call: " + this.mRingingCall.toString());
        List l = this.mRingingCall.getConnections();
        int s = l.size();
        for (i = 0; i < s; i++) {
            Rlog.i(LOG_TAG, l.get(i).toString());
        }
        Rlog.i(LOG_TAG, "Foreground call: " + this.mForegroundCall.toString());
        l = this.mForegroundCall.getConnections();
        s = l.size();
        for (i = 0; i < s; i++) {
            Rlog.i(LOG_TAG, l.get(i).toString());
        }
        Rlog.i(LOG_TAG, "Background call: " + this.mBackgroundCall.toString());
        l = this.mBackgroundCall.getConnections();
        s = l.size();
        for (i = 0; i < s; i++) {
            Rlog.i(LOG_TAG, l.get(i).toString());
        }
    }

    public void hangup(GsmCdmaConnection conn) throws CallStateException {
        if (conn.mOwner != this) {
            throw new CallStateException("GsmCdmaConnection " + conn + "does not belong to GsmCdmaCallTracker " + this);
        }
        if (conn == this.mPendingMO) {
            if (this.mIsEcmTimerCanceled) {
                handleEcmTimer(0);
            }
            log("hangup conn with callId '-1' as there is no DIAL response yet ");
            this.mCi.hangupConnection(-1, obtainCompleteMessage());
        } else if (!isPhoneTypeGsm() && conn.getCall() == this.mRingingCall && this.mRingingCall.getState() == Call.State.WAITING) {
            conn.onLocalDisconnect();
            updatePhoneState();
            this.mPhone.notifyPreciseCallStateChanged();
            return;
        } else {
            try {
                if (isPhoneTypeGsm() && TelBrand.IS_DCM) {
                    this.mCi.hangupConnection(conn.getGsmCdmaIndex(), obtainCompleteMessage(30, 0, 0, conn));
                } else {
                    this.mCi.hangupConnection(conn.getGsmCdmaIndex(), obtainCompleteMessage());
                }
            } catch (CallStateException e) {
                Rlog.w(LOG_TAG, "GsmCdmaCallTracker WARN: hangup() on absent connection " + conn);
            }
        }
        conn.onHangupLocal();
    }

    public void separate(GsmCdmaConnection conn) throws CallStateException {
        if (conn.mOwner != this) {
            throw new CallStateException("GsmCdmaConnection " + conn + "does not belong to GsmCdmaCallTracker " + this);
        }
        try {
            this.mCi.separateConnection(conn.getGsmCdmaIndex(), obtainCompleteMessage(12));
        } catch (CallStateException e) {
            Rlog.w(LOG_TAG, "GsmCdmaCallTracker WARN: separate() on absent connection " + conn);
        }
    }

    public void setMute(boolean mute) {
        this.mDesiredMute = mute;
        this.mCi.setMute(this.mDesiredMute, null);
    }

    public boolean getMute() {
        return this.mDesiredMute;
    }

    public void hangup(GsmCdmaCall call) throws CallStateException {
        if (call.getConnections().size() == 0) {
            throw new CallStateException("no connections in call");
        }
        if (isPhoneTypeGsm() && TelBrand.IS_DCM && call == this.mRingingCall) {
            for (int i = 0; i < call.getConnections().size(); i++) {
                GsmCdmaConnection cn = (GsmCdmaConnection) call.getConnections().get(i);
                if (cn.getAcceptCallPending()) {
                    cn.setHangUpCallPending(true);
                    return;
                }
            }
        }
        if (call == this.mRingingCall) {
            log("(ringing) hangup waiting or background");
            if (isPhoneTypeGsm() && TelBrand.IS_DCM) {
                this.mCi.hangupWaitingOrBackground(obtainCompleteMessage(30, 0, 0, call));
            } else {
                this.mCi.hangupWaitingOrBackground(obtainCompleteMessage());
            }
        } else if (call == this.mForegroundCall) {
            if (call.isDialingOrAlerting()) {
                log("(foregnd) hangup dialing or alerting...");
                hangup((GsmCdmaConnection) call.getConnections().get(0));
            } else if (isPhoneTypeGsm() && this.mRingingCall.isRinging()) {
                log("hangup all conns in active/background call, without affecting ringing call");
                hangupAllConnections(call);
            } else if (isPhoneTypeGsm() && TelBrand.IS_DCM) {
                hangupForegroundResumeBackground(call);
            } else {
                hangupForegroundResumeBackground();
            }
        } else if (call != this.mBackgroundCall) {
            throw new RuntimeException("GsmCdmaCall " + call + "does not belong to GsmCdmaCallTracker " + this);
        } else if (this.mRingingCall.isRinging()) {
            log("hangup all conns in background call");
            hangupAllConnections(call);
        } else if (isPhoneTypeGsm() && TelBrand.IS_DCM) {
            hangupWaitingOrBackground(call);
        } else {
            hangupWaitingOrBackground();
        }
        if (isPhoneTypeGsm() && TelBrand.IS_DCM) {
            call.onDisconnecting();
        } else {
            call.onHangupLocal();
        }
        this.mPhone.notifyPreciseCallStateChanged();
    }

    public void hangup_UserReject() throws CallStateException {
        if (!isPhoneTypeGsm()) {
            Rlog.e(LOG_TAG, "UNEXPECTED : hangup_UserReject is not used by CDMA.");
        } else if (this.mRingingCall.getState() == Call.State.INCOMING || this.mRingingCall.getState() == Call.State.WAITING) {
            Rlog.d(LOG_TAG, "hangup_UserReject() (ringing) hangup user reject");
            String mOemIdentifier = "QOEMHOOK";
            byte[] request = new byte[(((mOemIdentifier.length() + 8) + 4) + 1)];
            ByteBuffer reqBuffer = ByteBuffer.wrap(request);
            reqBuffer.order(ByteOrder.nativeOrder());
            reqBuffer.put(mOemIdentifier.getBytes());
            reqBuffer.putInt(524308);
            reqBuffer.putInt(4);
            reqBuffer.put((byte) 0);
            if (TelBrand.IS_SBM) {
                this.mCi.invokeOemRilRequestRaw(request, null);
            } else {
                this.mCi.invokeOemRilRequestRaw(request, obtainCompleteMessage());
            }
            this.mRingingCall.onHangupLocal();
            this.mPhone.notifyPreciseCallStateChanged();
            Rlog.d(LOG_TAG, "hangup_UserReject() end");
        } else {
            Rlog.d(LOG_TAG, "hangup_UserReject() call isn't ringingCall, throw new RuntimeException");
            throw new RuntimeException("GsmCall " + this.mRingingCall + "does not belong to GsmCallTracker " + this);
        }
    }

    public void hangupWaitingOrBackground() {
        log("hangupWaitingOrBackground");
        this.mCi.hangupWaitingOrBackground(obtainCompleteMessage());
    }

    /* Access modifiers changed, original: 0000 */
    public void hangupWaitingOrBackground(GsmCdmaCall call) {
        if (isPhoneTypeGsm()) {
            if (TelBrand.IS_DCM) {
                log("hangupWaitingOrBackground" + call);
                this.mCi.hangupWaitingOrBackground(obtainCompleteMessage(30, 0, 0, call));
            }
            return;
        }
        Rlog.e(LOG_TAG, "UNEXPECTED : hangupWaitingOrBackground is not used by CDMA.");
    }

    public void hangupForegroundResumeBackground() {
        log("hangupForegroundResumeBackground");
        this.mCi.hangupForegroundResumeBackground(obtainCompleteMessage());
    }

    /* Access modifiers changed, original: 0000 */
    public void hangupForegroundResumeBackground(GsmCdmaCall call) {
        if (isPhoneTypeGsm()) {
            if (TelBrand.IS_DCM) {
                log("hangupForegroundResumeBackground" + call);
                if (this.mRingingCall.isRinging()) {
                    for (int i = 0; i < this.mRingingCall.getConnections().size(); i++) {
                        ((GsmCdmaConnection) this.mRingingCall.getConnections().get(i)).setAcceptCallPending(true);
                    }
                }
                this.mCi.hangupForegroundResumeBackground(obtainCompleteMessage(30, 0, 0, call));
            }
            return;
        }
        Rlog.e(LOG_TAG, "UNEXPECTED : hangupForegroundResumeBackground is not used by CDMA.");
    }

    public void hangupConnectionByIndex(GsmCdmaCall call, int index) throws CallStateException {
        int count = call.mConnections.size();
        int i = 0;
        while (i < count) {
            GsmCdmaConnection cn = (GsmCdmaConnection) call.mConnections.get(i);
            if (cn.getGsmCdmaIndex() != index) {
                i++;
            } else if (isPhoneTypeGsm() && TelBrand.IS_DCM) {
                this.mCi.hangupConnection(index, obtainCompleteMessage(30, 0, 0, cn));
                return;
            } else {
                this.mCi.hangupConnection(index, obtainCompleteMessage());
                return;
            }
        }
        throw new CallStateException("no GsmCdma index found");
    }

    public void hangupAllConnections(GsmCdmaCall call) {
        try {
            int count = call.mConnections.size();
            for (int i = 0; i < count; i++) {
                GsmCdmaConnection cn = (GsmCdmaConnection) call.mConnections.get(i);
                if (isPhoneTypeGsm() && TelBrand.IS_DCM) {
                    this.mCi.hangupConnection(cn.getGsmCdmaIndex(), obtainCompleteMessage(30, 0, 0, cn));
                    this.hangupCallOperations++;
                } else {
                    this.mCi.hangupConnection(cn.getGsmCdmaIndex(), obtainCompleteMessage());
                }
            }
        } catch (CallStateException ex) {
            Rlog.e(LOG_TAG, "hangupConnectionByIndex caught " + ex);
        }
    }

    public GsmCdmaConnection getConnectionByIndex(GsmCdmaCall call, int index) throws CallStateException {
        int count = call.mConnections.size();
        for (int i = 0; i < count; i++) {
            GsmCdmaConnection cn = (GsmCdmaConnection) call.mConnections.get(i);
            if (cn.getGsmCdmaIndex() == index) {
                return cn;
            }
        }
        return null;
    }

    private void notifyCallWaitingInfo(CdmaCallWaitingNotification obj) {
        if (this.mCallWaitingRegistrants != null) {
            this.mCallWaitingRegistrants.notifyRegistrants(new AsyncResult(null, obj, null));
        }
    }

    private void handleCallWaitingInfo(CdmaCallWaitingNotification cw) {
        GsmCdmaConnection gsmCdmaConnection = new GsmCdmaConnection(this.mPhone.getContext(), cw, this, this.mRingingCall);
        updatePhoneState();
        notifyCallWaitingInfo(cw);
    }

    private SuppService getFailedService(int what) {
        switch (what) {
            case 8:
                return SuppService.SWITCH;
            case 11:
                return SuppService.CONFERENCE;
            case 12:
                return SuppService.SEPARATE;
            case 13:
                return SuppService.TRANSFER;
            default:
                return SuppService.UNKNOWN;
        }
    }

    private String[] getSpeechCodeState(int id) {
        if (isPhoneTypeGsm()) {
            String[] amr = null;
            log("SPEECH_CODE_SET... id:" + (id + 1));
            if (this.SpeechCodecState == null || this.SpeechCodecState.length <= 0) {
                log("SPEECH_CODE_SET NULL...");
                amr = new String[]{"Codec=AMR_NB"};
            } else {
                log("SpeechCodecState state length:" + this.SpeechCodecState.length);
                int index = 0;
                while (index < this.SpeechCodecState.length / 2) {
                    int callIndex = index * 2;
                    log("SpeechCodecState callId:" + this.SpeechCodecState[callIndex] + " SpeechCodecState:" + this.SpeechCodecState[callIndex + 1]);
                    if (id + 1 != this.SpeechCodecState[callIndex]) {
                        index++;
                    } else if (7 == this.SpeechCodecState[callIndex + 1]) {
                        log("SPEECH_CODE_SET AMR_WB...");
                        amr = new String[]{"Codec=AMR_WB"};
                    } else {
                        log("SPEECH_CODE_SET AMR_NB...");
                        amr = new String[]{"Codec=AMR_NB"};
                    }
                }
            }
            return amr;
        }
        Rlog.e(LOG_TAG, "UNEXPECTED : getSpeechCodeState is not used by CDMA.");
        return null;
    }

    private void updateSpeechCodec(int[] codec) {
        if (isPhoneTypeGsm()) {
            this.SpeechCodecState = codec;
            log("SPEECH_CODE_UPDATE...");
            boolean hasNonHangupStateChanged = DBG_POLL;
            for (int i = 0; i < this.mConnections.length; i++) {
                if (this.mConnections[i] != null) {
                    this.mConnections[i].setSpeechCodeState(getSpeechCodeState(i));
                    hasNonHangupStateChanged = true;
                }
            }
            if (hasNonHangupStateChanged) {
                log("SPEECH_CODE CallStateChanged...");
                this.mPhone.notifyPreciseCallStateChanged();
            } else {
                log("SPEECH_CODE Connection null...");
                this.SpeechCodecState = null;
            }
            return;
        }
        Rlog.e(LOG_TAG, "UNEXPECTED : updateSpeechCodec is not used by CDMA.");
    }

    public void handleMessage(Message msg) {
        AsyncResult ar;
        switch (msg.what) {
            case 1:
                Rlog.d(LOG_TAG, "Event EVENT_POLL_CALLS_RESULT Received");
                if (msg == this.mLastRelevantPoll) {
                    this.mNeedsPoll = DBG_POLL;
                    this.mLastRelevantPoll = null;
                    handlePollCalls((AsyncResult) msg.obj);
                    return;
                }
                return;
            case 2:
            case 3:
                pollCallsWhenSafe();
                return;
            case 4:
                operationComplete();
                return;
            case 5:
                int causeCode;
                String vendorCause = null;
                ar = (AsyncResult) msg.obj;
                operationComplete();
                if (ar.exception != null) {
                    causeCode = 16;
                    Rlog.i(LOG_TAG, "Exception during getLastCallFailCause, assuming normal disconnect");
                } else {
                    LastCallFailCause failCause = ar.result;
                    causeCode = failCause.causeCode;
                    vendorCause = failCause.vendorCause;
                }
                if (causeCode == 34 || causeCode == 41 || causeCode == 42 || causeCode == 44 || causeCode == 49 || causeCode == 58 || causeCode == 65535) {
                    CellLocation loc = this.mPhone.getCellLocation();
                    int cid = -1;
                    if (loc != null) {
                        if (isPhoneTypeGsm()) {
                            cid = ((GsmCellLocation) loc).getCid();
                        } else {
                            cid = ((CdmaCellLocation) loc).getBaseStationId();
                        }
                    }
                    EventLog.writeEvent(EventLogTags.CALL_DROP, new Object[]{Integer.valueOf(causeCode), Integer.valueOf(cid), Integer.valueOf(TelephonyManager.getDefault().getNetworkType())});
                }
                int s = this.mDroppedDuringPoll.size();
                for (int i = 0; i < s; i++) {
                    ((GsmCdmaConnection) this.mDroppedDuringPoll.get(i)).onRemoteDisconnect(causeCode, vendorCause);
                }
                updatePhoneState();
                this.mPhone.notifyPreciseCallStateChanged();
                this.mDroppedDuringPoll.clear();
                return;
            case 8:
            case 12:
            case 13:
                break;
            case 9:
                handleRadioAvailable();
                return;
            case 10:
                handleRadioNotAvailable();
                return;
            case 11:
                if (isPhoneTypeGsm() && msg.obj.exception != null) {
                    Connection connection = this.mForegroundCall.getLatestConnection();
                    if (connection != null) {
                        connection.onConferenceMergeFailed();
                        break;
                    }
                }
                break;
            case 14:
                if (isPhoneTypeGsm()) {
                    throw new RuntimeException("unexpected event " + msg.what + " not handled by " + "phone type " + this.mPhone.getPhoneType());
                }
                if (this.mPendingCallInEcm) {
                    this.mCi.dial(this.mPendingMO.getAddress(), this.mPendingCallClirMode, obtainCompleteMessage());
                    this.mPendingCallInEcm = DBG_POLL;
                }
                this.mPhone.unsetOnEcbModeExitResponse(this);
                return;
            case 15:
                if (isPhoneTypeGsm()) {
                    throw new RuntimeException("unexpected event " + msg.what + " not handled by " + "phone type " + this.mPhone.getPhoneType());
                }
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    handleCallWaitingInfo((CdmaCallWaitingNotification) ar.result);
                    Rlog.d(LOG_TAG, "Event EVENT_CALL_WAITING_INFO_CDMA Received");
                    return;
                }
                return;
            case 16:
                if (isPhoneTypeGsm()) {
                    throw new RuntimeException("unexpected event " + msg.what + " not handled by " + "phone type " + this.mPhone.getPhoneType());
                } else if (((AsyncResult) msg.obj).exception == null) {
                    this.mPendingMO.onConnectedInOrOut();
                    this.mPendingMO = null;
                    return;
                } else {
                    return;
                }
            case 20:
                if (isPhoneTypeGsm()) {
                    throw new RuntimeException("unexpected event " + msg.what + " not handled by " + "phone type " + this.mPhone.getPhoneType());
                } else if (((AsyncResult) msg.obj).exception == null) {
                    postDelayed(new Runnable() {
                        public void run() {
                            if (GsmCdmaCallTracker.this.mPendingMO != null) {
                                GsmCdmaCallTracker.this.mCi.sendCDMAFeatureCode(GsmCdmaCallTracker.this.mPendingMO.getAddress(), GsmCdmaCallTracker.this.obtainMessage(16));
                            }
                        }
                    }, (long) this.m3WayCallFlashDelay);
                    return;
                } else {
                    this.mPendingMO = null;
                    Rlog.w(LOG_TAG, "exception happened on Blank Flash for 3-way call");
                    return;
                }
            case CallFailCause.STATUS_ENQUIRY /*30*/:
                if (!isPhoneTypeGsm()) {
                    Rlog.e(LOG_TAG, "UNEXPECTED : handleMessage [case EVENT_HANGUP_RESULT] is not used by CDMA.");
                    return;
                } else if (TelBrand.IS_DCM) {
                    ar = (AsyncResult) msg.obj;
                    Rlog.d(LOG_TAG, "ringingCall.isRinging() is " + this.mRingingCall.isRinging());
                    if (ar.exception != null) {
                        Rlog.i(LOG_TAG, "The object of exception is" + ar.userObj);
                        if (this.mRingingCall.isRinging()) {
                            Rlog.i(LOG_TAG, "Exception during hangup active call and accept incoming call");
                            this.mPhone.notifySuppServiceFailed(SuppService.SWITCH);
                        }
                    } else if (ar.userObj instanceof GsmCdmaCall) {
                        ((GsmCdmaCall) ar.userObj).onHangupLocal();
                    } else if (ar.userObj instanceof GsmCdmaConnection) {
                        ((GsmCdmaConnection) ar.userObj).onHangupLocal();
                    }
                    operationComplete();
                    handleAcceptCallPending();
                    return;
                } else {
                    return;
                }
            case 10001:
                if (!isPhoneTypeGsm()) {
                    Rlog.e(LOG_TAG, "UNEXPECTED : handleMessage [case EVENT_SPEECH_CODEC] is not used by CDMA.");
                    return;
                } else if (TelBrand.IS_SBM) {
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null && ar.result != null) {
                        updateSpeechCodec((int[]) ar.result);
                        return;
                    }
                    return;
                } else {
                    return;
                }
            default:
                throw new RuntimeException("unexpected event " + msg.what + " not handled by " + "phone type " + this.mPhone.getPhoneType());
        }
        if (isPhoneTypeGsm()) {
            if (((AsyncResult) msg.obj).exception != null) {
                if (TelBrand.IS_DCM && msg.what == 8 && this.mRingingCall != null) {
                    for (Connection c : this.mRingingCall.getConnections()) {
                        if (c != null && ((GsmCdmaConnection) c).isRinging()) {
                            Rlog.d(LOG_TAG, "setAcceptCallPending(false), connection = " + c);
                            ((GsmCdmaConnection) c).setAcceptCallPending(DBG_POLL);
                        }
                    }
                }
                this.mPhone.notifySuppServiceFailed(getFailedService(msg.what));
            }
            operationComplete();
        } else if (msg.what != 8) {
            throw new RuntimeException("unexpected event " + msg.what + " not handled by " + "phone type " + this.mPhone.getPhoneType());
        }
    }

    private void checkAndEnableDataCallAfterEmergencyCallDropped() {
        if (this.mIsInEmergencyCall) {
            this.mIsInEmergencyCall = DBG_POLL;
            String inEcm = SystemProperties.get("ril.cdma.inecmmode", "false");
            log("checkAndEnableDataCallAfterEmergencyCallDropped,inEcm=" + inEcm);
            if (inEcm.compareTo("false") == 0) {
                this.mPhone.mDcTracker.setInternalDataEnabled(true);
                this.mPhone.notifyEmergencyCallRegistrants(DBG_POLL);
            }
            this.mPhone.sendEmergencyCallStateChange(DBG_POLL);
        }
    }

    private Connection checkMtFindNewRinging(DriverCall dc, int i) {
        if (this.mConnections[i].getCall() == this.mRingingCall) {
            Connection newRinging = this.mConnections[i];
            log("Notify new ring " + dc);
            return newRinging;
        }
        Rlog.e(LOG_TAG, "Phantom call appeared " + dc);
        if (dc.state == DriverCall.State.ALERTING || dc.state == DriverCall.State.DIALING) {
            return null;
        }
        this.mConnections[i].onConnectedInOrOut();
        if (dc.state != DriverCall.State.HOLDING) {
            return null;
        }
        this.mConnections[i].onStartedHolding();
        return null;
    }

    public boolean isInEmergencyCall() {
        return this.mIsInEmergencyCall;
    }

    private boolean isPhoneTypeGsm() {
        return this.mPhone.getPhoneType() == 1 ? true : DBG_POLL;
    }

    public GsmCdmaPhone getPhone() {
        return this.mPhone;
    }

    /* Access modifiers changed, original: protected */
    public void log(String msg) {
        Rlog.d(LOG_TAG, "[GsmCdmaCallTracker] " + msg);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        int i;
        pw.println("GsmCdmaCallTracker extends:");
        super.dump(fd, pw, args);
        pw.println("mConnections: length=" + this.mConnections.length);
        for (i = 0; i < this.mConnections.length; i++) {
            pw.printf("  mConnections[%d]=%s\n", new Object[]{Integer.valueOf(i), this.mConnections[i]});
        }
        pw.println(" mVoiceCallEndedRegistrants=" + this.mVoiceCallEndedRegistrants);
        pw.println(" mVoiceCallStartedRegistrants=" + this.mVoiceCallStartedRegistrants);
        if (!isPhoneTypeGsm()) {
            pw.println(" mCallWaitingRegistrants=" + this.mCallWaitingRegistrants);
        }
        pw.println(" mDroppedDuringPoll: size=" + this.mDroppedDuringPoll.size());
        for (i = 0; i < this.mDroppedDuringPoll.size(); i++) {
            pw.printf("  mDroppedDuringPoll[%d]=%s\n", new Object[]{Integer.valueOf(i), this.mDroppedDuringPoll.get(i)});
        }
        pw.println(" mRingingCall=" + this.mRingingCall);
        pw.println(" mForegroundCall=" + this.mForegroundCall);
        pw.println(" mBackgroundCall=" + this.mBackgroundCall);
        pw.println(" mPendingMO=" + this.mPendingMO);
        pw.println(" mHangupPendingMO=" + this.mHangupPendingMO);
        pw.println(" mPhone=" + this.mPhone);
        pw.println(" mDesiredMute=" + this.mDesiredMute);
        pw.println(" mState=" + this.mState);
        if (!isPhoneTypeGsm()) {
            pw.println(" mPendingCallInEcm=" + this.mPendingCallInEcm);
            pw.println(" mIsInEmergencyCall=" + this.mIsInEmergencyCall);
            pw.println(" mPendingCallClirMode=" + this.mPendingCallClirMode);
            pw.println(" mIsEcmTimerCanceled=" + this.mIsEcmTimerCanceled);
        }
    }

    public State getState() {
        return this.mState;
    }

    public int getMaxConnectionsPerCall() {
        if (this.mPhone.isPhoneTypeGsm()) {
            return 5;
        }
        return 1;
    }

    public void setAcceptCallPending() {
        if (isPhoneTypeGsm()) {
            if (TelBrand.IS_DCM) {
                this.mPendingOperations++;
                this.acceptCallPending = true;
            }
            return;
        }
        Rlog.e(LOG_TAG, "UNEXPECTED : setAcceptCallPending is not used by CDMA.");
    }

    public void clearAcceptCallPending() {
        if (isPhoneTypeGsm()) {
            if (TelBrand.IS_DCM) {
                this.acceptCallPending = DBG_POLL;
                if (this.mPendingOperations > 0) {
                    this.mPendingOperations--;
                }
            }
            return;
        }
        Rlog.e(LOG_TAG, "UNEXPECTED : clearAcceptCallPending is not used by CDMA.");
    }

    public boolean isAcceptCallPending() {
        boolean z = true;
        if (!isPhoneTypeGsm()) {
            Rlog.e(LOG_TAG, "UNEXPECTED : isAcceptCallPending is not used by CDMA.");
            return DBG_POLL;
        } else if (!TelBrand.IS_DCM) {
            return DBG_POLL;
        } else {
            if (!this.acceptCallPending || this.mPendingOperations > 1) {
                z = DBG_POLL;
            }
            return z;
        }
    }

    public void handleAcceptCallPending() {
        if (isPhoneTypeGsm()) {
            if (TelBrand.IS_DCM) {
                if (this.hangupCallOperations > 0) {
                    this.hangupCallOperations--;
                }
                if (this.hangupCallOperations == 0 && isAcceptCallPending()) {
                    try {
                        acceptCall();
                    } catch (CallStateException ex) {
                        Rlog.e(LOG_TAG, "acceptCall caught " + ex);
                        operationComplete();
                    }
                    clearAcceptCallPending();
                }
            }
            return;
        }
        Rlog.e(LOG_TAG, "UNEXPECTED : handleAcceptCallPending is not used by CDMA.");
    }
}
