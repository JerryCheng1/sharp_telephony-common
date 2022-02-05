package com.android.internal.telephony.cdma;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.imsphone.ImsPhoneConnection;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class CdmaCallTracker extends CallTracker {
    private static final boolean DBG_POLL = false;
    static final String LOG_TAG = "CdmaCallTracker";
    static final int MAX_CONNECTIONS = 8;
    static final int MAX_CONNECTIONS_PER_CALL = 1;
    private static final boolean REPEAT_POLLING = false;
    boolean mHangupPendingMO;
    int mPendingCallClirMode;
    CdmaConnection mPendingMO;
    CDMAPhone mPhone;
    CdmaConnection[] mConnections = new CdmaConnection[8];
    RegistrantList mVoiceCallEndedRegistrants = new RegistrantList();
    RegistrantList mVoiceCallStartedRegistrants = new RegistrantList();
    RegistrantList mCallWaitingRegistrants = new RegistrantList();
    ArrayList<CdmaConnection> mDroppedDuringPoll = new ArrayList<>(8);
    CdmaCall mRingingCall = new CdmaCall(this);
    CdmaCall mForegroundCall = new CdmaCall(this);
    CdmaCall mBackgroundCall = new CdmaCall(this);
    boolean mPendingCallInEcm = false;
    boolean mIsInEmergencyCall = false;
    boolean mDesiredMute = false;
    PhoneConstants.State mState = PhoneConstants.State.IDLE;
    private boolean mIsEcmTimerCanceled = false;
    private int m3WayCallFlashDelay = 0;

    public CdmaCallTracker(CDMAPhone phone) {
        this.mPhone = phone;
        this.mCi = phone.mCi;
        this.mCi.registerForCallStateChanged(this, 2, null);
        this.mCi.registerForOn(this, 9, null);
        this.mCi.registerForNotAvailable(this, 10, null);
        this.mCi.registerForCallWaitingInfo(this, 15, null);
        this.mForegroundCall.setGeneric(false);
    }

    public void dispose() {
        Rlog.d(LOG_TAG, "CdmaCallTracker dispose");
        this.mCi.unregisterForLineControlInfo(this);
        this.mCi.unregisterForCallStateChanged(this);
        this.mCi.unregisterForOn(this);
        this.mCi.unregisterForNotAvailable(this);
        this.mCi.unregisterForCallWaitingInfo(this);
        clearDisconnected();
    }

    protected void finalize() {
        Rlog.d(LOG_TAG, "CdmaCallTracker finalized");
    }

    @Override // com.android.internal.telephony.CallTracker
    public void registerForVoiceCallStarted(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mVoiceCallStartedRegistrants.add(r);
        if (this.mState != PhoneConstants.State.IDLE) {
            r.notifyRegistrant(new AsyncResult((Object) null, (Object) null, (Throwable) null));
        }
    }

    @Override // com.android.internal.telephony.CallTracker
    public void unregisterForVoiceCallStarted(Handler h) {
        this.mVoiceCallStartedRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.CallTracker
    public void registerForVoiceCallEnded(Handler h, int what, Object obj) {
        this.mVoiceCallEndedRegistrants.add(new Registrant(h, what, obj));
    }

    @Override // com.android.internal.telephony.CallTracker
    public void unregisterForVoiceCallEnded(Handler h) {
        this.mVoiceCallEndedRegistrants.remove(h);
    }

    public void registerForCallWaiting(Handler h, int what, Object obj) {
        this.mCallWaitingRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForCallWaiting(Handler h) {
        this.mCallWaitingRegistrants.remove(h);
    }

    Connection dial(String dialString, int clirMode) throws CallStateException {
        clearDisconnected();
        if (!canDial()) {
            throw new CallStateException("cannot dial in current state");
        }
        String operatorIsoContry = this.mPhone.getSystemProperty("gsm.operator.iso-country", "");
        String simIsoContry = this.mPhone.getSystemProperty("gsm.sim.operator.iso-country", "");
        boolean internationalRoaming = !TextUtils.isEmpty(operatorIsoContry) && !TextUtils.isEmpty(simIsoContry) && !simIsoContry.equals(operatorIsoContry);
        if (internationalRoaming) {
            if ("us".equals(simIsoContry)) {
                internationalRoaming = internationalRoaming && !"vi".equals(operatorIsoContry);
            } else if ("vi".equals(simIsoContry)) {
                internationalRoaming = internationalRoaming && !"us".equals(operatorIsoContry);
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
        this.mForegroundCall.setGeneric(false);
        if (this.mForegroundCall.getState() == Call.State.ACTIVE) {
            return dialThreeWay(dialString);
        }
        this.mPendingMO = new CdmaConnection(this.mPhone.getContext(), checkForTestEmergencyNumber(dialString), this, this.mForegroundCall);
        this.mHangupPendingMO = false;
        if (this.mPendingMO.getAddress() == null || this.mPendingMO.getAddress().length() == 0 || this.mPendingMO.getAddress().indexOf(78) >= 0) {
            this.mPendingMO.mCause = 7;
            pollCallsWhenSafe();
        } else {
            setMute(false);
            disableDataCallInEmergencyCall(dialString);
            if (!isPhoneInEcmMode || (isPhoneInEcmMode && isEmergencyCall)) {
                if (TelBrand.IS_KDDI) {
                    this.mPendingMO.getNumber();
                    if (!PhoneNumberUtils.isEmergencyNumber(this.mPendingMO.getNumber())) {
                        if (CallerInfo.sEnable) {
                            CallerInfo.TelLog(1, "dial() add the special number", new Object[0]);
                        }
                        try {
                            ITelephony telephony = ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
                            if (telephony != null) {
                                telephony.addSpecialNumber(this.mPendingMO.getNumber());
                            } else if (CallerInfo.sEnable) {
                                CallerInfo.TelLog(3, "dial() ITelephony is null!", new Object[0]);
                            }
                        } catch (RemoteException e) {
                            if (CallerInfo.sEnable) {
                                CallerInfo.TelLog(3, "dial() ITelephony is exception(" + e + ")", new Object[0]);
                            }
                        }
                    } else {
                        if (CallerInfo.sEnable) {
                            CallerInfo.TelLog(1, "dial() not add the special number", new Object[0]);
                        }
                        this.mPendingMO.getNumber();
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
            this.mPendingMO.setConverted(dialString);
            this.mNumberConverted = false;
        }
        updatePhoneState();
        this.mPhone.notifyPreciseCallStateChanged();
        return this.mPendingMO;
    }

    public Connection dial(String dialString) throws CallStateException {
        return dial(dialString, 0);
    }

    private Connection dialThreeWay(String dialString) {
        if (this.mForegroundCall.isIdle()) {
            return null;
        }
        disableDataCallInEmergencyCall(dialString);
        this.mPendingMO = new CdmaConnection(this.mPhone.getContext(), checkForTestEmergencyNumber(dialString), this, this.mForegroundCall);
        this.m3WayCallFlashDelay = this.mPhone.getContext().getResources().getInteger(17694880);
        if (this.m3WayCallFlashDelay > 0) {
            this.mCi.sendCDMAFeatureCode("", obtainMessage(20));
        } else {
            this.mCi.sendCDMAFeatureCode(this.mPendingMO.getAddress(), obtainMessage(16));
        }
        return this.mPendingMO;
    }

    public void acceptCall() throws CallStateException {
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            Rlog.i("phone", "acceptCall: incoming...");
            setMute(false);
            this.mCi.acceptCall(obtainCompleteMessage());
        } else if (this.mRingingCall.getState() == Call.State.WAITING) {
            CdmaConnection cwConn = (CdmaConnection) this.mRingingCall.getLatestConnection();
            cwConn.updateParent(this.mRingingCall, this.mForegroundCall);
            cwConn.onConnectedInOrOut();
            updatePhoneState();
            switchWaitingOrHoldingAndActive();
        } else {
            throw new CallStateException("phone not ringing");
        }
    }

    public void rejectCall() throws CallStateException {
        if (this.mRingingCall.getState().isRinging()) {
            this.mCi.rejectCall(obtainCompleteMessage());
            return;
        }
        throw new CallStateException("phone not ringing");
    }

    public void switchWaitingOrHoldingAndActive() throws CallStateException {
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            throw new CallStateException("cannot be in the incoming state");
        } else if (this.mForegroundCall.getConnections().size() > 1) {
            flashAndSetGenericTrue();
        } else {
            this.mCi.sendCDMAFeatureCode("", obtainMessage(8));
        }
    }

    void conference() {
        flashAndSetGenericTrue();
    }

    void explicitCallTransfer() {
        this.mCi.explicitCallTransfer(obtainCompleteMessage(13));
    }

    public void clearDisconnected() {
        internalClearDisconnected();
        updatePhoneState();
        this.mPhone.notifyPreciseCallStateChanged();
    }

    boolean canConference() {
        return this.mForegroundCall.getState() == Call.State.ACTIVE && this.mBackgroundCall.getState() == Call.State.HOLDING && !this.mBackgroundCall.isFull() && !this.mForegroundCall.isFull();
    }

    boolean canDial() {
        boolean z = true;
        int serviceState = this.mPhone.getServiceState().getState();
        String disableCall = SystemProperties.get("ro.telephony.disable-call", "false");
        boolean ret = serviceState != 3 && this.mPendingMO == null && !this.mRingingCall.isRinging() && !disableCall.equals("true") && (!this.mForegroundCall.getState().isAlive() || this.mForegroundCall.getState() == Call.State.ACTIVE || !this.mBackgroundCall.getState().isAlive());
        if (!ret) {
            Object[] objArr = new Object[8];
            objArr[0] = Integer.valueOf(serviceState);
            objArr[1] = Boolean.valueOf(serviceState != 3);
            objArr[2] = Boolean.valueOf(this.mPendingMO == null);
            objArr[3] = Boolean.valueOf(!this.mRingingCall.isRinging());
            objArr[4] = Boolean.valueOf(!disableCall.equals("true"));
            objArr[5] = Boolean.valueOf(!this.mForegroundCall.getState().isAlive());
            objArr[6] = Boolean.valueOf(this.mForegroundCall.getState() == Call.State.ACTIVE);
            if (this.mBackgroundCall.getState().isAlive()) {
                z = false;
            }
            objArr[7] = Boolean.valueOf(z);
            log(String.format("canDial is false\n((serviceState=%d) != ServiceState.STATE_POWER_OFF)::=%s\n&& pendingMO == null::=%s\n&& !ringingCall.isRinging()::=%s\n&& !disableCall.equals(\"true\")::=%s\n&& (!foregroundCall.getState().isAlive()::=%s\n   || foregroundCall.getState() == CdmaCall.State.ACTIVE::=%s\n   ||!backgroundCall.getState().isAlive())::=%s)", objArr));
        }
        return ret;
    }

    boolean canTransfer() {
        Rlog.e(LOG_TAG, "canTransfer: not possible in CDMA");
        return false;
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

    private void operationComplete() {
        this.mPendingOperations--;
        if (this.mPendingOperations == 0 && this.mNeedsPoll) {
            this.mLastRelevantPoll = obtainMessage(1);
            this.mCi.getCurrentCalls(this.mLastRelevantPoll);
        } else if (this.mPendingOperations < 0) {
            Rlog.e(LOG_TAG, "CdmaCallTracker.pendingOperations < 0");
            this.mPendingOperations = 0;
        }
    }

    private void updatePhoneState() {
        PhoneConstants.State oldState = this.mState;
        if (this.mRingingCall.isRinging()) {
            this.mState = PhoneConstants.State.RINGING;
        } else if (this.mPendingMO != null || !this.mForegroundCall.isIdle() || !this.mBackgroundCall.isIdle()) {
            this.mState = PhoneConstants.State.OFFHOOK;
        } else {
            this.mState = PhoneConstants.State.IDLE;
        }
        if (this.mState == PhoneConstants.State.IDLE && oldState != this.mState) {
            this.mVoiceCallEndedRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Object) null, (Throwable) null));
        } else if (oldState == PhoneConstants.State.IDLE && oldState != this.mState) {
            this.mVoiceCallStartedRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Object) null, (Throwable) null));
        }
        log("update phone state, old=" + oldState + " new=" + this.mState);
        if (this.mState != oldState) {
            this.mPhone.notifyPhoneStateChanged();
        }
    }

    @Override // com.android.internal.telephony.CallTracker
    protected void handlePollCalls(AsyncResult ar) {
        List polledCalls;
        int cause;
        if (ar.exception == null) {
            polledCalls = (List) ar.result;
        } else if (isCommandExceptionRadioNotAvailable(ar.exception)) {
            polledCalls = new ArrayList();
        } else {
            pollCallsAfterDelay();
            return;
        }
        Connection newRinging = null;
        Connection newUnknown = null;
        boolean hasNonHangupStateChanged = false;
        boolean hasAnyCallDisconnected = false;
        boolean unknownConnectionAppeared = false;
        int curDC = 0;
        int dcSize = polledCalls.size();
        for (int i = 0; i < this.mConnections.length; i++) {
            CdmaConnection conn = this.mConnections[i];
            DriverCall dc = null;
            if (curDC < dcSize) {
                dc = (DriverCall) polledCalls.get(curDC);
                if (dc.index == i + 1) {
                    curDC++;
                } else {
                    dc = null;
                }
            }
            if (conn == null && dc != null) {
                if (this.mPendingMO == null || !this.mPendingMO.compareTo(dc)) {
                    log("pendingMo=" + this.mPendingMO + ", dc=" + dc);
                    this.mConnections[i] = new CdmaConnection(this.mPhone.getContext(), dc, this, i);
                    Connection hoConnection = getHoConnection(dc);
                    if (hoConnection != null) {
                        this.mConnections[i].migrateFrom(hoConnection);
                        this.mHandoverConnections.remove(hoConnection);
                        this.mPhone.notifyHandoverStateChanged(this.mConnections[i]);
                    } else {
                        newRinging = checkMtFindNewRinging(dc, i);
                        if (newRinging == null) {
                            unknownConnectionAppeared = true;
                            newUnknown = this.mConnections[i];
                        }
                    }
                    checkAndEnableDataCallAfterEmergencyCallDropped();
                } else {
                    this.mConnections[i] = this.mPendingMO;
                    this.mPendingMO.mIndex = i;
                    this.mPendingMO.update(dc);
                    this.mPendingMO = null;
                    if (this.mHangupPendingMO) {
                        this.mHangupPendingMO = false;
                        if (this.mIsEcmTimerCanceled) {
                            handleEcmTimer(0);
                        }
                        try {
                            log("poll: hangupPendingMO, hangup conn " + i);
                            hangup(this.mConnections[i]);
                            return;
                        } catch (CallStateException e) {
                            Rlog.e(LOG_TAG, "unexpected error on hangup");
                            return;
                        }
                    }
                }
                hasNonHangupStateChanged = true;
            } else if (conn != null && dc == null) {
                int count = this.mForegroundCall.mConnections.size();
                for (int n = 0; n < count; n++) {
                    log("adding fgCall cn " + n + " to droppedDuringPoll");
                    this.mDroppedDuringPoll.add((CdmaConnection) this.mForegroundCall.mConnections.get(n));
                }
                int count2 = this.mRingingCall.mConnections.size();
                for (int n2 = 0; n2 < count2; n2++) {
                    log("adding rgCall cn " + n2 + " to droppedDuringPoll");
                    this.mDroppedDuringPoll.add((CdmaConnection) this.mRingingCall.mConnections.get(n2));
                }
                this.mForegroundCall.setGeneric(false);
                this.mRingingCall.setGeneric(false);
                if (this.mIsEcmTimerCanceled) {
                    handleEcmTimer(0);
                }
                checkAndEnableDataCallAfterEmergencyCallDropped();
                this.mConnections[i] = null;
            } else if (!(conn == null || dc == null)) {
                if (conn.isIncoming() == dc.isMT) {
                    hasNonHangupStateChanged = hasNonHangupStateChanged || conn.update(dc);
                } else if (dc.isMT) {
                    this.mDroppedDuringPoll.add(conn);
                    newRinging = checkMtFindNewRinging(dc, i);
                    if (newRinging == null) {
                        unknownConnectionAppeared = true;
                        newUnknown = conn;
                    }
                    checkAndEnableDataCallAfterEmergencyCallDropped();
                } else {
                    Rlog.e(LOG_TAG, "Error in RIL, Phantom call appeared " + dc);
                }
            }
        }
        if (this.mPendingMO != null) {
            Rlog.d(LOG_TAG, "Pending MO dropped before poll fg state:" + this.mForegroundCall.getState());
            this.mDroppedDuringPoll.add(this.mPendingMO);
            this.mPendingMO = null;
            this.mHangupPendingMO = false;
            if (this.mPendingCallInEcm) {
                this.mPendingCallInEcm = false;
            }
            checkAndEnableDataCallAfterEmergencyCallDropped();
        }
        if (newRinging != null) {
            this.mPhone.notifyNewRingingConnection(newRinging);
        }
        for (int i2 = this.mDroppedDuringPoll.size() - 1; i2 >= 0; i2--) {
            CdmaConnection conn2 = this.mDroppedDuringPoll.get(i2);
            if (conn2.isIncoming() && conn2.getConnectTime() == 0) {
                if (conn2.mCause == 3) {
                    cause = 16;
                } else {
                    cause = 1;
                }
                log("missed/rejected call, conn.cause=" + conn2.mCause);
                log("setting cause to " + cause);
                this.mDroppedDuringPoll.remove(i2);
                hasAnyCallDisconnected |= conn2.onDisconnect(cause);
            } else if (conn2.mCause == 3 || conn2.mCause == 7) {
                this.mDroppedDuringPoll.remove(i2);
                hasAnyCallDisconnected |= conn2.onDisconnect(conn2.mCause);
            }
        }
        Iterator<Connection> it = this.mHandoverConnections.iterator();
        while (it.hasNext()) {
            Connection hoConnection2 = it.next();
            log("handlePollCalls - disconnect hoConn= " + hoConnection2);
            ((ImsPhoneConnection) hoConnection2).onDisconnect(-1);
            it.remove();
        }
        if (this.mDroppedDuringPoll.size() > 0) {
            this.mCi.getLastCallFailCause(obtainNoPollCompleteMessage(5));
        }
        if (0 != 0) {
            pollCallsAfterDelay();
        }
        if (newRinging != null || hasNonHangupStateChanged || hasAnyCallDisconnected) {
            internalClearDisconnected();
        }
        updatePhoneState();
        if (unknownConnectionAppeared) {
            this.mPhone.notifyUnknownConnection(newUnknown);
        }
        if (hasNonHangupStateChanged || newRinging != null || hasAnyCallDisconnected) {
            this.mPhone.notifyPreciseCallStateChanged();
        }
    }

    public void hangup(CdmaConnection conn) throws CallStateException {
        if (conn.mOwner != this) {
            throw new CallStateException("CdmaConnection " + conn + "does not belong to CdmaCallTracker " + this);
        }
        if (conn == this.mPendingMO) {
            log("hangup: set hangupPendingMO to true");
            this.mHangupPendingMO = true;
        } else if (conn.getCall() == this.mRingingCall && this.mRingingCall.getState() == Call.State.WAITING) {
            conn.onLocalDisconnect();
            updatePhoneState();
            this.mPhone.notifyPreciseCallStateChanged();
            return;
        } else {
            try {
                this.mCi.hangupConnection(conn.getCDMAIndex(), obtainCompleteMessage());
            } catch (CallStateException e) {
                Rlog.w(LOG_TAG, "CdmaCallTracker WARN: hangup() on absent connection " + conn);
            }
        }
        conn.onHangupLocal();
    }

    public void separate(CdmaConnection conn) throws CallStateException {
        if (conn.mOwner != this) {
            throw new CallStateException("CdmaConnection " + conn + "does not belong to CdmaCallTracker " + this);
        }
        try {
            this.mCi.separateConnection(conn.getCDMAIndex(), obtainCompleteMessage(12));
        } catch (CallStateException e) {
            Rlog.w(LOG_TAG, "CdmaCallTracker WARN: separate() on absent connection " + conn);
        }
    }

    public void setMute(boolean mute) {
        this.mDesiredMute = mute;
        this.mCi.setMute(this.mDesiredMute, null);
    }

    public boolean getMute() {
        return this.mDesiredMute;
    }

    public void hangup(CdmaCall call) throws CallStateException {
        if (call.getConnections().size() == 0) {
            throw new CallStateException("no connections in call");
        }
        if (call == this.mRingingCall) {
            log("(ringing) hangup waiting or background");
            this.mCi.hangupWaitingOrBackground(obtainCompleteMessage());
        } else if (call == this.mForegroundCall) {
            if (call.isDialingOrAlerting()) {
                log("(foregnd) hangup dialing or alerting...");
                hangup((CdmaConnection) call.getConnections().get(0));
            } else {
                hangupForegroundResumeBackground();
            }
        } else if (call != this.mBackgroundCall) {
            throw new RuntimeException("CdmaCall " + call + "does not belong to CdmaCallTracker " + this);
        } else if (this.mRingingCall.isRinging()) {
            log("hangup all conns in background call");
            hangupAllConnections(call);
        } else {
            hangupWaitingOrBackground();
        }
        call.onHangupLocal();
        this.mPhone.notifyPreciseCallStateChanged();
    }

    void hangupWaitingOrBackground() {
        log("hangupWaitingOrBackground");
        this.mCi.hangupWaitingOrBackground(obtainCompleteMessage());
    }

    void hangupForegroundResumeBackground() {
        log("hangupForegroundResumeBackground");
        this.mCi.hangupForegroundResumeBackground(obtainCompleteMessage());
    }

    void hangupConnectionByIndex(CdmaCall call, int index) throws CallStateException {
        int count = call.mConnections.size();
        for (int i = 0; i < count; i++) {
            if (((CdmaConnection) call.mConnections.get(i)).getCDMAIndex() == index) {
                this.mCi.hangupConnection(index, obtainCompleteMessage());
                return;
            }
        }
        throw new CallStateException("no gsm index found");
    }

    void hangupAllConnections(CdmaCall call) {
        try {
            int count = call.mConnections.size();
            for (int i = 0; i < count; i++) {
                this.mCi.hangupConnection(((CdmaConnection) call.mConnections.get(i)).getCDMAIndex(), obtainCompleteMessage());
            }
        } catch (CallStateException ex) {
            Rlog.e(LOG_TAG, "hangupConnectionByIndex caught " + ex);
        }
    }

    CdmaConnection getConnectionByIndex(CdmaCall call, int index) throws CallStateException {
        int count = call.mConnections.size();
        for (int i = 0; i < count; i++) {
            CdmaConnection cn = (CdmaConnection) call.mConnections.get(i);
            if (cn.getCDMAIndex() == index) {
                return cn;
            }
        }
        return null;
    }

    private void flashAndSetGenericTrue() {
        this.mCi.sendCDMAFeatureCode("", obtainMessage(8));
        this.mForegroundCall.setGeneric(true);
        this.mPhone.notifyPreciseCallStateChanged();
    }

    private void handleRadioNotAvailable() {
        pollCallsWhenSafe();
    }

    private void notifyCallWaitingInfo(CdmaCallWaitingNotification obj) {
        if (this.mCallWaitingRegistrants != null) {
            this.mCallWaitingRegistrants.notifyRegistrants(new AsyncResult((Object) null, obj, (Throwable) null));
        }
    }

    private void handleCallWaitingInfo(CdmaCallWaitingNotification cw) {
        if (this.mForegroundCall.mConnections.size() > 1) {
            this.mForegroundCall.setGeneric(true);
        }
        this.mRingingCall.setGeneric(false);
        new CdmaConnection(this.mPhone.getContext(), cw, this, this.mRingingCall);
        updatePhoneState();
        notifyCallWaitingInfo(cw);
    }

    @Override // com.android.internal.telephony.CallTracker, android.os.Handler
    public void handleMessage(Message msg) {
        int causeCode;
        if (!this.mPhone.mIsTheCurrentActivePhone) {
            Rlog.w(LOG_TAG, "Ignoring events received on inactive CdmaPhone");
            return;
        }
        switch (msg.what) {
            case 1:
                Rlog.d(LOG_TAG, "Event EVENT_POLL_CALLS_RESULT Received");
                AsyncResult asyncResult = (AsyncResult) msg.obj;
                if (msg == this.mLastRelevantPoll) {
                    this.mNeedsPoll = false;
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
                AsyncResult ar = (AsyncResult) msg.obj;
                operationComplete();
                if (ar.exception != null) {
                    causeCode = 16;
                    Rlog.i(LOG_TAG, "Exception during getLastCallFailCause, assuming normal disconnect");
                } else {
                    causeCode = ((int[]) ar.result)[0];
                }
                int s = this.mDroppedDuringPoll.size();
                for (int i = 0; i < s; i++) {
                    this.mDroppedDuringPoll.get(i).onRemoteDisconnect(causeCode);
                }
                updatePhoneState();
                this.mPhone.notifyPreciseCallStateChanged();
                this.mDroppedDuringPoll.clear();
                return;
            case 6:
            case 7:
            case 11:
            case 12:
            case 13:
            case 17:
            case 18:
            case 19:
            default:
                throw new RuntimeException("unexpected event not handled");
            case 8:
                return;
            case 9:
                handleRadioAvailable();
                return;
            case 10:
                handleRadioNotAvailable();
                return;
            case 14:
                if (this.mPendingCallInEcm) {
                    this.mCi.dial(this.mPendingMO.getAddress(), this.mPendingCallClirMode, obtainCompleteMessage());
                    this.mPendingCallInEcm = false;
                }
                this.mPhone.unsetOnEcbModeExitResponse(this);
                return;
            case 15:
                AsyncResult ar2 = (AsyncResult) msg.obj;
                if (ar2.exception == null) {
                    handleCallWaitingInfo((CdmaCallWaitingNotification) ar2.result);
                    Rlog.d(LOG_TAG, "Event EVENT_CALL_WAITING_INFO_CDMA Received");
                    return;
                }
                return;
            case 16:
                if (((AsyncResult) msg.obj).exception == null) {
                    this.mPendingMO.onConnectedInOrOut();
                    this.mPendingMO = null;
                    return;
                }
                return;
            case 20:
                if (((AsyncResult) msg.obj).exception == null) {
                    postDelayed(new Runnable() { // from class: com.android.internal.telephony.cdma.CdmaCallTracker.1
                        @Override // java.lang.Runnable
                        public void run() {
                            if (CdmaCallTracker.this.mPendingMO != null) {
                                CdmaCallTracker.this.mCi.sendCDMAFeatureCode(CdmaCallTracker.this.mPendingMO.getAddress(), CdmaCallTracker.this.obtainMessage(16));
                            }
                        }
                    }, this.m3WayCallFlashDelay);
                    return;
                }
                this.mPendingMO = null;
                Rlog.w(LOG_TAG, "exception happened on Blank Flash for 3-way call");
                return;
        }
    }

    private void handleEcmTimer(int action) {
        this.mPhone.handleTimerInEmergencyCallbackMode(action);
        switch (action) {
            case 0:
                this.mIsEcmTimerCanceled = false;
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
            this.mIsInEmergencyCall = true;
            this.mPhone.mDcTracker.setInternalDataEnabled(false);
        }
    }

    private void checkAndEnableDataCallAfterEmergencyCallDropped() {
        if (this.mIsInEmergencyCall) {
            this.mIsInEmergencyCall = false;
            String inEcm = SystemProperties.get("ril.cdma.inecmmode", "false");
            log("checkAndEnableDataCallAfterEmergencyCallDropped,inEcm=" + inEcm);
            if (inEcm.compareTo("false") == 0) {
                this.mPhone.mDcTracker.setInternalDataEnabled(true);
            }
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

    @Override // com.android.internal.telephony.CallTracker
    protected void log(String msg) {
        Rlog.d(LOG_TAG, "[CdmaCallTracker] " + msg);
    }

    @Override // com.android.internal.telephony.CallTracker
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("GsmCallTracker extends:");
        super.dump(fd, pw, args);
        pw.println("droppedDuringPoll: length=" + this.mConnections.length);
        for (int i = 0; i < this.mConnections.length; i++) {
            pw.printf(" mConnections[%d]=%s\n", Integer.valueOf(i), this.mConnections[i]);
        }
        pw.println(" mVoiceCallEndedRegistrants=" + this.mVoiceCallEndedRegistrants);
        pw.println(" mVoiceCallStartedRegistrants=" + this.mVoiceCallStartedRegistrants);
        pw.println(" mCallWaitingRegistrants=" + this.mCallWaitingRegistrants);
        pw.println("droppedDuringPoll: size=" + this.mDroppedDuringPoll.size());
        for (int i2 = 0; i2 < this.mDroppedDuringPoll.size(); i2++) {
            pw.printf(" mDroppedDuringPoll[%d]=%s\n", Integer.valueOf(i2), this.mDroppedDuringPoll.get(i2));
        }
        pw.println(" mRingingCall=" + this.mRingingCall);
        pw.println(" mForegroundCall=" + this.mForegroundCall);
        pw.println(" mBackgroundCall=" + this.mBackgroundCall);
        pw.println(" mPendingMO=" + this.mPendingMO);
        pw.println(" mHangupPendingMO=" + this.mHangupPendingMO);
        pw.println(" mPendingCallInEcm=" + this.mPendingCallInEcm);
        pw.println(" mIsInEmergencyCall=" + this.mIsInEmergencyCall);
        pw.println(" mPhone=" + this.mPhone);
        pw.println(" mDesiredMute=" + this.mDesiredMute);
        pw.println(" mPendingCallClirMode=" + this.mPendingCallClirMode);
        pw.println(" mState=" + this.mState);
        pw.println(" mIsEcmTimerCanceled=" + this.mIsEcmTimerCanceled);
    }

    @Override // com.android.internal.telephony.CallTracker
    public PhoneConstants.State getState() {
        return this.mState;
    }
}
