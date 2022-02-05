package com.android.internal.telephony.gsm;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.EventLog;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.UUSInfo;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class GsmCallTracker extends CallTracker {
    private static final boolean DBG_POLL = false;
    static final String LOG_TAG = "GsmCallTracker";
    static final int MAX_CONNECTIONS = 7;
    static final int MAX_CONNECTIONS_PER_CALL = 5;
    private static final boolean REPEAT_POLLING = false;
    static final int RETRY_ANSWER = 1;
    int[] SpeechCodecState;
    boolean mHangupPendingMO;
    GsmConnection mPendingMO;
    GSMPhone mPhone;
    GsmConnection[] mConnections = new GsmConnection[7];
    RegistrantList mVoiceCallEndedRegistrants = new RegistrantList();
    RegistrantList mVoiceCallStartedRegistrants = new RegistrantList();
    ArrayList<GsmConnection> mDroppedDuringPoll = new ArrayList<>(7);
    GsmCall mRingingCall = new GsmCall(this);
    GsmCall mForegroundCall = new GsmCall(this);
    GsmCall mBackgroundCall = new GsmCall(this);
    boolean mDesiredMute = false;
    PhoneConstants.State mState = PhoneConstants.State.IDLE;
    Call.SrvccState mSrvccState = Call.SrvccState.NONE;
    int hangupCallOperations = 0;
    boolean acceptCallPending = false;

    public GsmCallTracker(GSMPhone phone) {
        this.mPhone = phone;
        this.mCi = phone.mCi;
        this.mCi.registerForCallStateChanged(this, 2, null);
        this.mCi.registerForOn(this, 9, null);
        this.mCi.registerForNotAvailable(this, 10, null);
    }

    public void dispose() {
        Rlog.d(LOG_TAG, "GsmCallTracker dispose");
        this.mCi.unregisterForCallStateChanged(this);
        this.mCi.unregisterForOn(this);
        this.mCi.unregisterForNotAvailable(this);
        if (TelBrand.IS_SBM) {
            this.SpeechCodecState = null;
        }
        clearDisconnected();
    }

    protected void finalize() {
        Rlog.d(LOG_TAG, "GsmCallTracker finalized");
    }

    @Override // com.android.internal.telephony.CallTracker
    public void registerForVoiceCallStarted(Handler h, int what, Object obj) {
        this.mVoiceCallStartedRegistrants.add(new Registrant(h, what, obj));
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

    private void fakeHoldForegroundBeforeDial() {
        List<Connection> connCopy = (List) this.mForegroundCall.mConnections.clone();
        int s = connCopy.size();
        for (int i = 0; i < s; i++) {
            ((GsmConnection) connCopy.get(i)).fakeHoldBeforeDial();
        }
    }

    public synchronized Connection dial(String dialString, int clirMode, UUSInfo uusInfo) throws CallStateException {
        clearDisconnected();
        if (!canDial()) {
            throw new CallStateException("cannot dial in current state");
        }
        String dialString2 = convertNumberIfNecessary(this.mPhone, dialString);
        if (this.mForegroundCall.getState() == Call.State.ACTIVE) {
            switchWaitingOrHoldingAndActive();
            fakeHoldForegroundBeforeDial();
        }
        if (this.mForegroundCall.getState() != Call.State.IDLE) {
            throw new CallStateException("cannot dial in current state");
        }
        this.mPendingMO = new GsmConnection(this.mPhone.getContext(), checkForTestEmergencyNumber(dialString2), this, this.mForegroundCall);
        this.mHangupPendingMO = false;
        if (TelBrand.IS_DCM) {
            if (this.mPendingMO.getNumber() == null || this.mPendingMO.getNumber().length() == 0 || this.mPendingMO.getNumber().indexOf(78) >= 0) {
                this.mPendingMO.mCause = 7;
                pollCallsWhenSafe();
            } else {
                setMute(false);
                this.mCi.dial(this.mPendingMO.getNumber(), clirMode, uusInfo, obtainCompleteMessage());
            }
        } else if (this.mPendingMO.getAddress() == null || this.mPendingMO.getAddress().length() == 0 || this.mPendingMO.getAddress().indexOf(78) >= 0) {
            this.mPendingMO.mCause = 7;
            pollCallsWhenSafe();
        } else {
            setMute(false);
            this.mCi.dial(this.mPendingMO.getAddress(), clirMode, uusInfo, obtainCompleteMessage());
        }
        if (this.mNumberConverted) {
            this.mPendingMO.setConverted(dialString);
            this.mNumberConverted = false;
        }
        updatePhoneState();
        this.mPhone.notifyPreciseCallStateChanged();
        return this.mPendingMO;
    }

    Connection dial(String dialString) throws CallStateException {
        return dial(dialString, 0, null);
    }

    public Connection dial(String dialString, UUSInfo uusInfo) throws CallStateException {
        return dial(dialString, 0, uusInfo);
    }

    Connection dial(String dialString, int clirMode) throws CallStateException {
        return dial(dialString, clirMode, null);
    }

    public void acceptCall() throws CallStateException {
        if (TelBrand.IS_DCM && this.mRingingCall.isRinging()) {
            for (int i = 0; i < this.mRingingCall.getConnections().size(); i++) {
                ((GsmConnection) this.mRingingCall.getConnections().get(i)).setAcceptCallPending(true);
            }
        }
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            Rlog.i("phone", "acceptCall: incoming...");
            setMute(false);
            this.mCi.acceptCall(obtainCompleteMessage());
        } else if (this.mRingingCall.getState() != Call.State.WAITING) {
            throw new CallStateException("phone not ringing");
        } else if (!TelBrand.IS_DCM || this.hangupCallOperations <= 0) {
            setMute(false);
            if (!TelBrand.IS_SBM) {
                switchWaitingOrHoldingAndActive();
            } else {
                this.mCi.switchWaitingOrHoldingAndActive(obtainCompleteMessage(8, 1, 0));
            }
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

    public void switchWaitingOrHoldingAndActive() throws CallStateException {
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            throw new CallStateException("cannot be in the incoming state");
        }
        this.mCi.switchWaitingOrHoldingAndActive(obtainCompleteMessage(8));
    }

    public void conference() {
        this.mCi.conference(obtainCompleteMessage(11));
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
        return this.mForegroundCall.getState() == Call.State.ACTIVE && this.mBackgroundCall.getState() == Call.State.HOLDING && !this.mBackgroundCall.isFull() && !this.mForegroundCall.isFull();
    }

    public boolean canDial() {
        return this.mPhone.getServiceState().getState() != 3 && this.mPendingMO == null && !this.mRingingCall.isRinging() && !SystemProperties.get("ro.telephony.disable-call", "false").equals("true") && (!this.mForegroundCall.getState().isAlive() || !this.mBackgroundCall.getState().isAlive());
    }

    public boolean canTransfer() {
        return (this.mForegroundCall.getState() == Call.State.ACTIVE || this.mForegroundCall.getState() == Call.State.ALERTING || this.mForegroundCall.getState() == Call.State.DIALING) && this.mBackgroundCall.getState() == Call.State.HOLDING;
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

    private Message obtainCompleteMessage(int what, int arg1, int arg2) {
        this.mPendingOperations++;
        this.mLastRelevantPoll = null;
        this.mNeedsPoll = true;
        return obtainMessage(what, arg1, arg2);
    }

    private Message obtainCompleteMessage(int what, int arg1, int arg2, Object obj) {
        if (!TelBrand.IS_DCM) {
            return obtainMessage(0);
        }
        this.mPendingOperations++;
        this.mLastRelevantPoll = null;
        this.mNeedsPoll = true;
        return obtainMessage(what, arg1, arg2, obj);
    }

    private void operationComplete() {
        this.mPendingOperations--;
        if (this.mPendingOperations == 0 && this.mNeedsPoll) {
            this.mLastRelevantPoll = obtainMessage(1);
            this.mCi.getCurrentCalls(this.mLastRelevantPoll);
        } else if (this.mPendingOperations < 0) {
            Rlog.e(LOG_TAG, "GsmCallTracker.pendingOperations < 0");
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
        if (this.mState != oldState) {
            this.mPhone.notifyPhoneStateChanged();
        }
    }

    /* JADX WARN: Code restructure failed: missing block: B:22:0x00a1, code lost:
        r28.mHangupPendingMO = false;
     */
    /* JADX WARN: Code restructure failed: missing block: B:23:0x00a9, code lost:
        log("poll: hangupPendingMO, hangup conn " + r15);
        hangup(r28.mConnections[r15]);
     */
    /* JADX WARN: Code restructure failed: missing block: B:34:0x00f8, code lost:
        android.telephony.Rlog.e(com.android.internal.telephony.gsm.GsmCallTracker.LOG_TAG, "unexpected error on hangup");
     */
    @Override // com.android.internal.telephony.CallTracker
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    protected synchronized void handlePollCalls(android.os.AsyncResult r29) {
        /*
            Method dump skipped, instructions count: 1319
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.gsm.GsmCallTracker.handlePollCalls(android.os.AsyncResult):void");
    }

    private void handleRadioNotAvailable() {
        pollCallsWhenSafe();
    }

    private void dumpState() {
        Rlog.i(LOG_TAG, "Phone State:" + this.mState);
        Rlog.i(LOG_TAG, "Ringing call: " + this.mRingingCall.toString());
        List l = this.mRingingCall.getConnections();
        int s = l.size();
        for (int i = 0; i < s; i++) {
            Rlog.i(LOG_TAG, l.get(i).toString());
        }
        Rlog.i(LOG_TAG, "Foreground call: " + this.mForegroundCall.toString());
        List l2 = this.mForegroundCall.getConnections();
        int s2 = l2.size();
        for (int i2 = 0; i2 < s2; i2++) {
            Rlog.i(LOG_TAG, l2.get(i2).toString());
        }
        Rlog.i(LOG_TAG, "Background call: " + this.mBackgroundCall.toString());
        List l3 = this.mBackgroundCall.getConnections();
        int s3 = l3.size();
        for (int i3 = 0; i3 < s3; i3++) {
            Rlog.i(LOG_TAG, l3.get(i3).toString());
        }
    }

    public void hangup(GsmConnection conn) throws CallStateException {
        if (conn.mOwner != this) {
            throw new CallStateException("GsmConnection " + conn + "does not belong to GsmCallTracker " + this);
        }
        if (conn == this.mPendingMO) {
            log("hangup: set hangupPendingMO to true");
            this.mHangupPendingMO = true;
        } else {
            try {
                if (TelBrand.IS_DCM) {
                    this.mCi.hangupConnection(conn.getGSMIndex(), obtainCompleteMessage(18, 0, 0, conn));
                } else {
                    this.mCi.hangupConnection(conn.getGSMIndex(), obtainCompleteMessage());
                }
            } catch (CallStateException e) {
                Rlog.w(LOG_TAG, "GsmCallTracker WARN: hangup() on absent connection " + conn);
            }
        }
        conn.onHangupLocal();
    }

    public void separate(GsmConnection conn) throws CallStateException {
        if (conn.mOwner != this) {
            throw new CallStateException("GsmConnection " + conn + "does not belong to GsmCallTracker " + this);
        }
        try {
            this.mCi.separateConnection(conn.getGSMIndex(), obtainCompleteMessage(12));
        } catch (CallStateException e) {
            Rlog.w(LOG_TAG, "GsmCallTracker WARN: separate() on absent connection " + conn);
        }
    }

    public void setMute(boolean mute) {
        this.mDesiredMute = mute;
        this.mCi.setMute(this.mDesiredMute, null);
    }

    public boolean getMute() {
        return this.mDesiredMute;
    }

    public void hangup(GsmCall call) throws CallStateException {
        if (call.getConnections().size() == 0) {
            throw new CallStateException("no connections in call");
        }
        if (TelBrand.IS_DCM && call == this.mRingingCall) {
            for (int i = 0; i < call.getConnections().size(); i++) {
                GsmConnection cn = (GsmConnection) call.getConnections().get(i);
                if (cn.getAcceptCallPending()) {
                    cn.setHangUpCallPending(true);
                    return;
                }
            }
        }
        if (call == this.mRingingCall) {
            log("(ringing) hangup waiting or background");
            if (TelBrand.IS_DCM) {
                this.mCi.hangupWaitingOrBackground(obtainCompleteMessage(18, 0, 0, call));
            } else {
                this.mCi.hangupWaitingOrBackground(obtainCompleteMessage());
            }
        } else if (call == this.mForegroundCall) {
            if (call.isDialingOrAlerting()) {
                log("(foregnd) hangup dialing or alerting...");
                hangup((GsmConnection) call.getConnections().get(0));
            } else if (this.mRingingCall.isRinging()) {
                log("hangup all conns in active/background call, without affecting ringing call");
                hangupAllConnections(call);
            } else if (TelBrand.IS_DCM) {
                hangupForegroundResumeBackground(call);
            } else {
                hangupForegroundResumeBackground();
            }
        } else if (call != this.mBackgroundCall) {
            throw new RuntimeException("GsmCall " + call + "does not belong to GsmCallTracker " + this);
        } else if (this.mRingingCall.isRinging()) {
            log("hangup all conns in background call");
            hangupAllConnections(call);
        } else if (TelBrand.IS_DCM) {
            hangupWaitingOrBackground(call);
        } else {
            hangupWaitingOrBackground();
        }
        if (TelBrand.IS_DCM) {
            call.onDisconnecting();
        } else {
            call.onHangupLocal();
        }
        this.mPhone.notifyPreciseCallStateChanged();
    }

    public void hangup_UserReject() throws CallStateException {
        if (this.mRingingCall.getState() == Call.State.INCOMING || this.mRingingCall.getState() == Call.State.WAITING) {
            Rlog.d(LOG_TAG, "hangup_UserReject() (ringing) hangup user reject");
            byte[] request = new byte["QOEMHOOK".length() + 8 + 4 + 1];
            ByteBuffer reqBuffer = ByteBuffer.wrap(request);
            reqBuffer.order(ByteOrder.nativeOrder());
            reqBuffer.put("QOEMHOOK".getBytes());
            reqBuffer.putInt(524288 + 20);
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
            return;
        }
        Rlog.d(LOG_TAG, "hangup_UserReject() call isn't ringingCall, throw new RuntimeException");
        throw new RuntimeException("GsmCall " + this.mRingingCall + "does not belong to GsmCallTracker " + this);
    }

    public void hangupWaitingOrBackground() {
        log("hangupWaitingOrBackground");
        this.mCi.hangupWaitingOrBackground(obtainCompleteMessage());
    }

    public void hangupWaitingOrBackground(GsmCall call) {
        if (TelBrand.IS_DCM) {
            log("hangupWaitingOrBackground" + call);
            this.mCi.hangupWaitingOrBackground(obtainCompleteMessage(18, 0, 0, call));
        }
    }

    void hangupForegroundResumeBackground() {
        log("hangupForegroundResumeBackground");
        this.mCi.hangupForegroundResumeBackground(obtainCompleteMessage());
    }

    void hangupForegroundResumeBackground(GsmCall call) {
        if (TelBrand.IS_DCM) {
            log("hangupForegroundResumeBackground" + call);
            if (this.mRingingCall.isRinging()) {
                for (int i = 0; i < this.mRingingCall.getConnections().size(); i++) {
                    ((GsmConnection) this.mRingingCall.getConnections().get(i)).setAcceptCallPending(true);
                }
            }
            this.mCi.hangupForegroundResumeBackground(obtainCompleteMessage(18, 0, 0, call));
        }
    }

    public void hangupConnectionByIndex(GsmCall call, int index) throws CallStateException {
        int count = call.mConnections.size();
        for (int i = 0; i < count; i++) {
            GsmConnection cn = (GsmConnection) call.mConnections.get(i);
            if (cn.getGSMIndex() == index) {
                if (TelBrand.IS_DCM) {
                    this.mCi.hangupConnection(index, obtainCompleteMessage(18, 0, 0, cn));
                    return;
                } else {
                    this.mCi.hangupConnection(index, obtainCompleteMessage());
                    return;
                }
            }
        }
        throw new CallStateException("no gsm index found");
    }

    void hangupAllConnections(GsmCall call) {
        try {
            int count = call.mConnections.size();
            for (int i = 0; i < count; i++) {
                GsmConnection cn = (GsmConnection) call.mConnections.get(i);
                if (TelBrand.IS_DCM) {
                    this.mCi.hangupConnection(cn.getGSMIndex(), obtainCompleteMessage(18, 0, 0, cn));
                    this.hangupCallOperations++;
                } else {
                    this.mCi.hangupConnection(cn.getGSMIndex(), obtainCompleteMessage());
                }
            }
        } catch (CallStateException ex) {
            Rlog.e(LOG_TAG, "hangupConnectionByIndex caught " + ex);
        }
    }

    public GsmConnection getConnectionByIndex(GsmCall call, int index) throws CallStateException {
        int count = call.mConnections.size();
        for (int i = 0; i < count; i++) {
            GsmConnection cn = (GsmConnection) call.mConnections.get(i);
            if (cn.getGSMIndex() == index) {
                return cn;
            }
        }
        return null;
    }

    private Phone.SuppService getFailedService(int what) {
        switch (what) {
            case 8:
                return Phone.SuppService.SWITCH;
            case 9:
            case 10:
            default:
                return Phone.SuppService.UNKNOWN;
            case 11:
                return Phone.SuppService.CONFERENCE;
            case 12:
                return Phone.SuppService.SEPARATE;
            case 13:
                return Phone.SuppService.TRANSFER;
        }
    }

    public String[] getSpeechCodeState(int id) {
        log("SPEECH_CODE_SET... id:" + (id + 1));
        if (this.SpeechCodecState == null || this.SpeechCodecState.length <= 0) {
            log("SPEECH_CODE_SET NULL...");
            return new String[]{"Codec=AMR_NB"};
        }
        log("SpeechCodecState state length:" + this.SpeechCodecState.length);
        for (int index = 0; index < this.SpeechCodecState.length / 2; index++) {
            int callIndex = index * 2;
            log("SpeechCodecState callId:" + this.SpeechCodecState[callIndex] + " SpeechCodecState:" + this.SpeechCodecState[callIndex + 1]);
            if (id + 1 == this.SpeechCodecState[callIndex]) {
                if (7 == this.SpeechCodecState[callIndex + 1]) {
                    log("SPEECH_CODE_SET AMR_WB...");
                    return new String[]{"Codec=AMR_WB"};
                } else {
                    log("SPEECH_CODE_SET AMR_NB...");
                    return new String[]{"Codec=AMR_NB"};
                }
            }
        }
        return null;
    }

    public void updateSpeechCodec(int[] codec) {
        this.SpeechCodecState = codec;
        log("SPEECH_CODE_UPDATE...");
        boolean hasNonHangupStateChanged = false;
        for (int i = 0; i < this.mConnections.length; i++) {
            if (this.mConnections[i] != null) {
                this.mConnections[i].setSpeechCodeState(getSpeechCodeState(i));
                hasNonHangupStateChanged = true;
            }
        }
        if (hasNonHangupStateChanged) {
            log("SPEECH_CODE CallStateChanged...");
            this.mPhone.notifyPreciseCallStateChanged();
            return;
        }
        log("SPEECH_CODE Connection null...");
        this.SpeechCodecState = null;
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    @Override // com.android.internal.telephony.CallTracker, android.os.Handler
    public void handleMessage(Message msg) {
        int causeCode;
        if (!this.mPhone.mIsTheCurrentActivePhone) {
            Rlog.e(LOG_TAG, "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
            return;
        }
        switch (msg.what) {
            case 1:
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
                AsyncResult asyncResult2 = (AsyncResult) msg.obj;
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
                if (causeCode == 34 || causeCode == 41 || causeCode == 42 || causeCode == 44 || causeCode == 49 || causeCode == 58 || causeCode == 65535) {
                    GsmCellLocation loc = (GsmCellLocation) this.mPhone.getCellLocation();
                    Object[] objArr = new Object[3];
                    objArr[0] = Integer.valueOf(causeCode);
                    objArr[1] = Integer.valueOf(loc != null ? loc.getCid() : -1);
                    objArr[2] = Integer.valueOf(TelephonyManager.getDefault().getNetworkType());
                    EventLog.writeEvent((int) EventLogTags.CALL_DROP, objArr);
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
            case 14:
            case 15:
            case 16:
            case 17:
            default:
                return;
            case 8:
                if (TelBrand.IS_DCM) {
                    if (((AsyncResult) msg.obj).exception != null) {
                        if (this.mRingingCall != null) {
                            for (Connection c : this.mRingingCall.getConnections()) {
                                if (c != null && ((GsmConnection) c).isRinging()) {
                                    Rlog.d(LOG_TAG, "setAcceptCallPending(false), connection = " + c);
                                    ((GsmConnection) c).setAcceptCallPending(false);
                                }
                            }
                        }
                        this.mPhone.notifySuppServiceFailed(getFailedService(msg.what));
                    }
                    operationComplete();
                    return;
                }
                break;
            case 9:
                handleRadioAvailable();
                return;
            case 10:
                handleRadioNotAvailable();
                return;
            case 11:
            case 12:
            case 13:
                break;
            case 18:
                if (TelBrand.IS_DCM) {
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    Rlog.d(LOG_TAG, "ringingCall.isRinging() is " + this.mRingingCall.isRinging());
                    if (ar2.exception != null) {
                        Rlog.i(LOG_TAG, "The object of exception is" + ar2.userObj);
                        if (this.mRingingCall.isRinging()) {
                            Rlog.i(LOG_TAG, "Exception during hangup active call and accept incoming call");
                            this.mPhone.notifySuppServiceFailed(Phone.SuppService.SWITCH);
                        }
                    } else if (ar2.userObj instanceof GsmCall) {
                        ((GsmCall) ar2.userObj).onHangupLocal();
                    } else if (ar2.userObj instanceof GsmConnection) {
                        ((GsmConnection) ar2.userObj).onHangupLocal();
                    }
                    operationComplete();
                    handleAcceptCallPending();
                    return;
                }
                return;
        }
        if (((AsyncResult) msg.obj).exception != null) {
            if (!TelBrand.IS_SBM) {
                this.mPhone.notifySuppServiceFailed(getFailedService(msg.what));
            } else if (msg.what == 8 && msg.arg1 == 1) {
                this.mCi.acceptCall(obtainCompleteMessage());
            } else {
                Rlog.i(LOG_TAG, "SuppServiceFail notify");
                this.mPhone.notifySuppServiceFailed(getFailedService(msg.what));
            }
        }
        operationComplete();
    }

    @Override // com.android.internal.telephony.CallTracker
    protected void log(String msg) {
        Rlog.d(LOG_TAG, "[GsmCallTracker] " + msg);
    }

    @Override // com.android.internal.telephony.CallTracker
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("GsmCallTracker extends:");
        super.dump(fd, pw, args);
        pw.println("mConnections: length=" + this.mConnections.length);
        for (int i = 0; i < this.mConnections.length; i++) {
            pw.printf("  mConnections[%d]=%s\n", Integer.valueOf(i), this.mConnections[i]);
        }
        pw.println(" mVoiceCallEndedRegistrants=" + this.mVoiceCallEndedRegistrants);
        pw.println(" mVoiceCallStartedRegistrants=" + this.mVoiceCallStartedRegistrants);
        pw.println(" mDroppedDuringPoll: size=" + this.mDroppedDuringPoll.size());
        for (int i2 = 0; i2 < this.mDroppedDuringPoll.size(); i2++) {
            pw.printf("  mDroppedDuringPoll[%d]=%s\n", Integer.valueOf(i2), this.mDroppedDuringPoll.get(i2));
        }
        pw.println(" mRingingCall=" + this.mRingingCall);
        pw.println(" mForegroundCall=" + this.mForegroundCall);
        pw.println(" mBackgroundCall=" + this.mBackgroundCall);
        pw.println(" mPendingMO=" + this.mPendingMO);
        pw.println(" mHangupPendingMO=" + this.mHangupPendingMO);
        pw.println(" mPhone=" + this.mPhone);
        pw.println(" mDesiredMute=" + this.mDesiredMute);
        pw.println(" mState=" + this.mState);
    }

    @Override // com.android.internal.telephony.CallTracker
    public PhoneConstants.State getState() {
        return this.mState;
    }

    public void setAcceptCallPending() {
        if (TelBrand.IS_DCM) {
            this.mPendingOperations++;
            this.acceptCallPending = true;
        }
    }

    public void clearAcceptCallPending() {
        if (TelBrand.IS_DCM) {
            this.acceptCallPending = false;
            if (this.mPendingOperations > 0) {
                this.mPendingOperations--;
            }
        }
    }

    public boolean isAcceptCallPending() {
        if (TelBrand.IS_DCM) {
            return this.acceptCallPending && this.mPendingOperations <= 1;
        }
        return false;
    }

    public void handleAcceptCallPending() {
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
    }
}
