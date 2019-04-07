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
import com.android.internal.telephony.Call.SrvccState;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.Phone.SuppService;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.imsphone.ImsPhoneConnection;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class GsmCallTracker extends CallTracker {
    private static final boolean DBG_POLL = false;
    static final String LOG_TAG = "GsmCallTracker";
    static final int MAX_CONNECTIONS = 7;
    static final int MAX_CONNECTIONS_PER_CALL = 5;
    private static final boolean REPEAT_POLLING = false;
    static final int RETRY_ANSWER = 1;
    int[] SpeechCodecState;
    boolean acceptCallPending = false;
    int hangupCallOperations = 0;
    GsmCall mBackgroundCall = new GsmCall(this);
    GsmConnection[] mConnections = new GsmConnection[7];
    boolean mDesiredMute = false;
    ArrayList<GsmConnection> mDroppedDuringPoll = new ArrayList(7);
    GsmCall mForegroundCall = new GsmCall(this);
    boolean mHangupPendingMO;
    GsmConnection mPendingMO;
    GSMPhone mPhone;
    GsmCall mRingingCall = new GsmCall(this);
    SrvccState mSrvccState = SrvccState.NONE;
    State mState = State.IDLE;
    RegistrantList mVoiceCallEndedRegistrants = new RegistrantList();
    RegistrantList mVoiceCallStartedRegistrants = new RegistrantList();

    GsmCallTracker(GSMPhone gSMPhone) {
        this.mPhone = gSMPhone;
        this.mCi = gSMPhone.mCi;
        this.mCi.registerForCallStateChanged(this, 2, null);
        this.mCi.registerForOn(this, 9, null);
        this.mCi.registerForNotAvailable(this, 10, null);
    }

    private void dumpState() {
        int i;
        int i2 = 0;
        Rlog.i(LOG_TAG, "Phone State:" + this.mState);
        Rlog.i(LOG_TAG, "Ringing call: " + this.mRingingCall.toString());
        List connections = this.mRingingCall.getConnections();
        int size = connections.size();
        for (i = 0; i < size; i++) {
            Rlog.i(LOG_TAG, connections.get(i).toString());
        }
        Rlog.i(LOG_TAG, "Foreground call: " + this.mForegroundCall.toString());
        connections = this.mForegroundCall.getConnections();
        size = connections.size();
        for (i = 0; i < size; i++) {
            Rlog.i(LOG_TAG, connections.get(i).toString());
        }
        Rlog.i(LOG_TAG, "Background call: " + this.mBackgroundCall.toString());
        List connections2 = this.mBackgroundCall.getConnections();
        int size2 = connections2.size();
        while (i2 < size2) {
            Rlog.i(LOG_TAG, connections2.get(i2).toString());
            i2++;
        }
    }

    private void fakeHoldForegroundBeforeDial() {
        List list = (List) this.mForegroundCall.mConnections.clone();
        int size = list.size();
        for (int i = 0; i < size; i++) {
            ((GsmConnection) list.get(i)).fakeHoldBeforeDial();
        }
    }

    private SuppService getFailedService(int i) {
        switch (i) {
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

    private void handleRadioNotAvailable() {
        pollCallsWhenSafe();
    }

    private void internalClearDisconnected() {
        this.mRingingCall.clearDisconnected();
        this.mForegroundCall.clearDisconnected();
        this.mBackgroundCall.clearDisconnected();
    }

    private Message obtainCompleteMessage() {
        return obtainCompleteMessage(4);
    }

    private Message obtainCompleteMessage(int i) {
        this.mPendingOperations++;
        this.mLastRelevantPoll = null;
        this.mNeedsPoll = true;
        return obtainMessage(i);
    }

    private Message obtainCompleteMessage(int i, int i2, int i3) {
        this.mPendingOperations++;
        this.mLastRelevantPoll = null;
        this.mNeedsPoll = true;
        return obtainMessage(i, i2, i3);
    }

    private Message obtainCompleteMessage(int i, int i2, int i3, Object obj) {
        if (!TelBrand.IS_DCM) {
            return obtainMessage(0);
        }
        this.mPendingOperations++;
        this.mLastRelevantPoll = null;
        this.mNeedsPoll = true;
        return obtainMessage(i, i2, i3, obj);
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
        State state = this.mState;
        if (this.mRingingCall.isRinging()) {
            this.mState = State.RINGING;
        } else if (this.mPendingMO == null && this.mForegroundCall.isIdle() && this.mBackgroundCall.isIdle()) {
            this.mState = State.IDLE;
        } else {
            this.mState = State.OFFHOOK;
        }
        if (this.mState == State.IDLE && state != this.mState) {
            this.mVoiceCallEndedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
        } else if (state == State.IDLE && state != this.mState) {
            this.mVoiceCallStartedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
        }
        if (this.mState != state) {
            this.mPhone.notifyPhoneStateChanged();
        }
    }

    /* Access modifiers changed, original: 0000 */
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
            if (TelBrand.IS_SBM) {
                this.mCi.switchWaitingOrHoldingAndActive(obtainCompleteMessage(8, 1, 0));
            } else {
                switchWaitingOrHoldingAndActive();
            }
        } else if (!isAcceptCallPending()) {
            setAcceptCallPending();
        }
    }

    /* Access modifiers changed, original: 0000 */
    public boolean canConference() {
        return this.mForegroundCall.getState() == Call.State.ACTIVE && this.mBackgroundCall.getState() == Call.State.HOLDING && !this.mBackgroundCall.isFull() && !this.mForegroundCall.isFull();
    }

    /* Access modifiers changed, original: 0000 */
    public boolean canDial() {
        return (this.mPhone.getServiceState().getState() == 3 || this.mPendingMO != null || this.mRingingCall.isRinging() || SystemProperties.get("ro.telephony.disable-call", "false").equals("true") || (this.mForegroundCall.getState().isAlive() && this.mBackgroundCall.getState().isAlive())) ? false : true;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean canTransfer() {
        return (this.mForegroundCall.getState() == Call.State.ACTIVE || this.mForegroundCall.getState() == Call.State.ALERTING || this.mForegroundCall.getState() == Call.State.DIALING) && this.mBackgroundCall.getState() == Call.State.HOLDING;
    }

    public void clearAcceptCallPending() {
        if (TelBrand.IS_DCM) {
            this.acceptCallPending = false;
            if (this.mPendingOperations > 0) {
                this.mPendingOperations--;
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void clearDisconnected() {
        internalClearDisconnected();
        updatePhoneState();
        this.mPhone.notifyPreciseCallStateChanged();
    }

    /* Access modifiers changed, original: 0000 */
    public void conference() {
        this.mCi.conference(obtainCompleteMessage(11));
    }

    /* Access modifiers changed, original: 0000 */
    public Connection dial(String str) throws CallStateException {
        return dial(str, 0, null);
    }

    /* Access modifiers changed, original: 0000 */
    public Connection dial(String str, int i) throws CallStateException {
        return dial(str, i, null);
    }

    /* Access modifiers changed, original: 0000 */
    public Connection dial(String str, int i, UUSInfo uUSInfo) throws CallStateException {
        GsmConnection gsmConnection;
        synchronized (this) {
            clearDisconnected();
            if (canDial()) {
                String convertNumberIfNecessary = convertNumberIfNecessary(this.mPhone, str);
                if (this.mForegroundCall.getState() == Call.State.ACTIVE) {
                    switchWaitingOrHoldingAndActive();
                    fakeHoldForegroundBeforeDial();
                }
                if (this.mForegroundCall.getState() != Call.State.IDLE) {
                    throw new CallStateException("cannot dial in current state");
                }
                this.mPendingMO = new GsmConnection(this.mPhone.getContext(), checkForTestEmergencyNumber(convertNumberIfNecessary), this, this.mForegroundCall);
                this.mHangupPendingMO = false;
                if (TelBrand.IS_DCM) {
                    if (this.mPendingMO.getNumber() == null || this.mPendingMO.getNumber().length() == 0 || this.mPendingMO.getNumber().indexOf(78) >= 0) {
                        this.mPendingMO.mCause = 7;
                        pollCallsWhenSafe();
                    } else {
                        setMute(false);
                        this.mCi.dial(this.mPendingMO.getNumber(), i, uUSInfo, obtainCompleteMessage());
                    }
                } else if (this.mPendingMO.getAddress() == null || this.mPendingMO.getAddress().length() == 0 || this.mPendingMO.getAddress().indexOf(78) >= 0) {
                    this.mPendingMO.mCause = 7;
                    pollCallsWhenSafe();
                } else {
                    setMute(false);
                    this.mCi.dial(this.mPendingMO.getAddress(), i, uUSInfo, obtainCompleteMessage());
                }
                if (this.mNumberConverted) {
                    this.mPendingMO.setConverted(str);
                    this.mNumberConverted = false;
                }
                updatePhoneState();
                this.mPhone.notifyPreciseCallStateChanged();
                gsmConnection = this.mPendingMO;
            } else {
                throw new CallStateException("cannot dial in current state");
            }
        }
        return gsmConnection;
    }

    /* Access modifiers changed, original: 0000 */
    public Connection dial(String str, UUSInfo uUSInfo) throws CallStateException {
        return dial(str, 0, uUSInfo);
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

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        int i;
        printWriter.println("GsmCallTracker extends:");
        super.dump(fileDescriptor, printWriter, strArr);
        printWriter.println("mConnections: length=" + this.mConnections.length);
        for (i = 0; i < this.mConnections.length; i++) {
            printWriter.printf("  mConnections[%d]=%s\n", new Object[]{Integer.valueOf(i), this.mConnections[i]});
        }
        printWriter.println(" mVoiceCallEndedRegistrants=" + this.mVoiceCallEndedRegistrants);
        printWriter.println(" mVoiceCallStartedRegistrants=" + this.mVoiceCallStartedRegistrants);
        printWriter.println(" mDroppedDuringPoll: size=" + this.mDroppedDuringPoll.size());
        for (i = 0; i < this.mDroppedDuringPoll.size(); i++) {
            printWriter.printf("  mDroppedDuringPoll[%d]=%s\n", new Object[]{Integer.valueOf(i), this.mDroppedDuringPoll.get(i)});
        }
        printWriter.println(" mRingingCall=" + this.mRingingCall);
        printWriter.println(" mForegroundCall=" + this.mForegroundCall);
        printWriter.println(" mBackgroundCall=" + this.mBackgroundCall);
        printWriter.println(" mPendingMO=" + this.mPendingMO);
        printWriter.println(" mHangupPendingMO=" + this.mHangupPendingMO);
        printWriter.println(" mPhone=" + this.mPhone);
        printWriter.println(" mDesiredMute=" + this.mDesiredMute);
        printWriter.println(" mState=" + this.mState);
    }

    /* Access modifiers changed, original: 0000 */
    public void explicitCallTransfer() {
        this.mCi.explicitCallTransfer(obtainCompleteMessage(13));
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        Rlog.d(LOG_TAG, "GsmCallTracker finalized");
    }

    /* Access modifiers changed, original: 0000 */
    public GsmConnection getConnectionByIndex(GsmCall gsmCall, int i) throws CallStateException {
        int size = gsmCall.mConnections.size();
        for (int i2 = 0; i2 < size; i2++) {
            GsmConnection gsmConnection = (GsmConnection) gsmCall.mConnections.get(i2);
            if (gsmConnection.getGSMIndex() == i) {
                return gsmConnection;
            }
        }
        return null;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean getMute() {
        return this.mDesiredMute;
    }

    public String[] getSpeechCodeState(int i) {
        log("SPEECH_CODE_SET... id:" + (i + 1));
        if (this.SpeechCodecState == null || this.SpeechCodecState.length <= 0) {
            log("SPEECH_CODE_SET NULL...");
            return new String[]{"Codec=AMR_NB"};
        }
        log("SpeechCodecState state length:" + this.SpeechCodecState.length);
        int i2 = 0;
        while (i2 < this.SpeechCodecState.length / 2) {
            int i3 = i2 * 2;
            log("SpeechCodecState callId:" + this.SpeechCodecState[i3] + " SpeechCodecState:" + this.SpeechCodecState[i3 + 1]);
            if (i + 1 != this.SpeechCodecState[i3]) {
                i2++;
            } else if (7 == this.SpeechCodecState[i3 + 1]) {
                log("SPEECH_CODE_SET AMR_WB...");
                return new String[]{"Codec=AMR_WB"};
            } else {
                log("SPEECH_CODE_SET AMR_NB...");
                return new String[]{"Codec=AMR_NB"};
            }
        }
        return null;
    }

    public State getState() {
        return this.mState;
    }

    public void handleAcceptCallPending() {
        if (TelBrand.IS_DCM) {
            if (this.hangupCallOperations > 0) {
                this.hangupCallOperations--;
            }
            if (this.hangupCallOperations == 0 && isAcceptCallPending()) {
                try {
                    acceptCall();
                } catch (CallStateException e) {
                    Rlog.e(LOG_TAG, "acceptCall caught " + e);
                    operationComplete();
                }
                clearAcceptCallPending();
            }
        }
    }

    public void handleMessage(Message message) {
        if (this.mPhone.mIsTheCurrentActivePhone) {
            AsyncResult asyncResult;
            switch (message.what) {
                case 1:
                    asyncResult = (AsyncResult) message.obj;
                    if (message == this.mLastRelevantPoll) {
                        this.mNeedsPoll = false;
                        this.mLastRelevantPoll = null;
                        handlePollCalls((AsyncResult) message.obj);
                        return;
                    }
                    return;
                case 2:
                case 3:
                    pollCallsWhenSafe();
                    return;
                case 4:
                    asyncResult = (AsyncResult) message.obj;
                    operationComplete();
                    return;
                case 5:
                    int i;
                    asyncResult = (AsyncResult) message.obj;
                    operationComplete();
                    if (asyncResult.exception != null) {
                        Rlog.i(LOG_TAG, "Exception during getLastCallFailCause, assuming normal disconnect");
                        i = 16;
                    } else {
                        i = ((int[]) asyncResult.result)[0];
                    }
                    if (i == 34 || i == 41 || i == 42 || i == 44 || i == 49 || i == 58 || i == 65535) {
                        GsmCellLocation gsmCellLocation = (GsmCellLocation) this.mPhone.getCellLocation();
                        int cid = gsmCellLocation != null ? gsmCellLocation.getCid() : -1;
                        EventLog.writeEvent(EventLogTags.CALL_DROP, new Object[]{Integer.valueOf(i), Integer.valueOf(cid), Integer.valueOf(TelephonyManager.getDefault().getNetworkType())});
                    }
                    int size = this.mDroppedDuringPoll.size();
                    for (int i2 = 0; i2 < size; i2++) {
                        ((GsmConnection) this.mDroppedDuringPoll.get(i2)).onRemoteDisconnect(i);
                    }
                    updatePhoneState();
                    this.mPhone.notifyPreciseCallStateChanged();
                    this.mDroppedDuringPoll.clear();
                    return;
                case 8:
                    if (TelBrand.IS_DCM) {
                        if (((AsyncResult) message.obj).exception != null) {
                            if (this.mRingingCall != null) {
                                for (Connection connection : this.mRingingCall.getConnections()) {
                                    if (connection != null && ((GsmConnection) connection).isRinging()) {
                                        Rlog.d(LOG_TAG, "setAcceptCallPending(false), connection = " + connection);
                                        ((GsmConnection) connection).setAcceptCallPending(false);
                                    }
                                }
                            }
                            this.mPhone.notifySuppServiceFailed(getFailedService(message.what));
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
                        asyncResult = (AsyncResult) message.obj;
                        Rlog.d(LOG_TAG, "ringingCall.isRinging() is " + this.mRingingCall.isRinging());
                        if (asyncResult.exception != null) {
                            Rlog.i(LOG_TAG, "The object of exception is" + asyncResult.userObj);
                            if (this.mRingingCall.isRinging()) {
                                Rlog.i(LOG_TAG, "Exception during hangup active call and accept incoming call");
                                this.mPhone.notifySuppServiceFailed(SuppService.SWITCH);
                            }
                        } else if (asyncResult.userObj instanceof GsmCall) {
                            ((GsmCall) asyncResult.userObj).onHangupLocal();
                        } else if (asyncResult.userObj instanceof GsmConnection) {
                            ((GsmConnection) asyncResult.userObj).onHangupLocal();
                        }
                        operationComplete();
                        handleAcceptCallPending();
                        return;
                    }
                    return;
                default:
                    return;
            }
            if (((AsyncResult) message.obj).exception != null) {
                if (!TelBrand.IS_SBM) {
                    this.mPhone.notifySuppServiceFailed(getFailedService(message.what));
                } else if (message.what == 8 && message.arg1 == 1) {
                    this.mCi.acceptCall(obtainCompleteMessage());
                } else {
                    Rlog.i(LOG_TAG, "SuppServiceFail notify");
                    this.mPhone.notifySuppServiceFailed(getFailedService(message.what));
                }
            }
            operationComplete();
            return;
        }
        Rlog.e(LOG_TAG, "Received message " + message + "[" + message.what + "] while being destroyed. Ignoring.");
    }

    /* Access modifiers changed, original: protected */
    public void handlePollCalls(AsyncResult asyncResult) {
        synchronized (this) {
            List list;
            Connection connection;
            if (asyncResult.exception == null) {
                list = (List) asyncResult.result;
            } else if (isCommandExceptionRadioNotAvailable(asyncResult.exception)) {
                Object list2 = new ArrayList();
            } else {
                pollCallsAfterDelay();
            }
            Connection connection2 = null;
            ArrayList arrayList = new ArrayList();
            Object obj = null;
            Object obj2 = null;
            int i = 0;
            int size = list2.size();
            int i2 = 0;
            while (i2 < this.mConnections.length) {
                Object obj3 = this.mConnections[i2];
                DriverCall driverCall = null;
                if (i < size) {
                    driverCall = (DriverCall) list2.get(i);
                    if (driverCall.index == i2 + 1) {
                        i++;
                    } else {
                        driverCall = null;
                    }
                }
                Object obj4;
                if (obj3 == null && driverCall != null) {
                    if (this.mPendingMO == null || !this.mPendingMO.compareTo(driverCall)) {
                        this.mConnections[i2] = new GsmConnection(this.mPhone.getContext(), driverCall, this, i2);
                        Connection hoConnection = getHoConnection(driverCall);
                        if (hoConnection != null) {
                            this.mConnections[i2].migrateFrom(hoConnection);
                            this.mHandoverConnections.remove(hoConnection);
                            Iterator it = this.mHandoverConnections.iterator();
                            while (it.hasNext()) {
                                connection = (Connection) it.next();
                                Rlog.i(LOG_TAG, "HO Conn state is " + connection.mPreHandoverState);
                                if (connection.mPreHandoverState == this.mConnections[i2].getState()) {
                                    Rlog.i(LOG_TAG, "Removing HO conn " + hoConnection + connection.mPreHandoverState);
                                    it.remove();
                                }
                            }
                            this.mPhone.notifyHandoverStateChanged(this.mConnections[i2]);
                            obj4 = obj2;
                        } else if (this.mConnections[i2].getCall() == this.mRingingCall) {
                            connection2 = this.mConnections[i2];
                            obj4 = obj2;
                        } else {
                            Rlog.i(LOG_TAG, "Phantom call appeared " + driverCall);
                            if (!(driverCall.state == DriverCall.State.ALERTING || driverCall.state == DriverCall.State.DIALING)) {
                                this.mConnections[i2].onConnectedInOrOut();
                                if (driverCall.state == DriverCall.State.HOLDING) {
                                    this.mConnections[i2].onStartedHolding();
                                }
                            }
                            arrayList.add(this.mConnections[i2]);
                            obj4 = 1;
                        }
                    } else {
                        this.mConnections[i2] = this.mPendingMO;
                        this.mPendingMO.mIndex = i2;
                        this.mPendingMO.update(driverCall);
                        this.mPendingMO = null;
                        if (this.mHangupPendingMO) {
                            this.mHangupPendingMO = false;
                            try {
                                log("poll: hangupPendingMO, hangup conn " + i2);
                                hangup(this.mConnections[i2]);
                                break;
                            } catch (CallStateException e) {
                                Rlog.e(LOG_TAG, "unexpected error on hangup");
                            }
                        } else {
                            obj4 = obj2;
                        }
                    }
                    obj = 1;
                    obj2 = obj4;
                } else if (obj3 != null && driverCall == null) {
                    this.mDroppedDuringPoll.add(obj3);
                    this.mConnections[i2] = null;
                } else if (obj3 != null && driverCall != null && !obj3.compareTo(driverCall)) {
                    this.mDroppedDuringPoll.add(obj3);
                    this.mConnections[i2] = new GsmConnection(this.mPhone.getContext(), driverCall, this, i2);
                    if (this.mConnections[i2].getCall() == this.mRingingCall) {
                        connection2 = this.mConnections[i2];
                    }
                    obj = 1;
                } else if (!(obj3 == null || driverCall == null)) {
                    boolean update = obj3.update(driverCall);
                    if (TelBrand.IS_DCM && update && obj3.getCall().getState() == Call.State.ACTIVE && obj3.getHangUpCallPending() && obj3.getAcceptCallPending()) {
                        obj3.setHangUpCallPending(false);
                        obj3.setAcceptCallPending(false);
                        try {
                            hangup(obj3.getCall());
                        } catch (CallStateException e2) {
                            Rlog.e(LOG_TAG, "handlePollCalls caught " + e2);
                        }
                    } else {
                        obj4 = (obj != null || update) ? 1 : null;
                        obj = obj4;
                    }
                }
                if (TelBrand.IS_SBM && this.mConnections[i2] != null) {
                    this.mConnections[i2].setSpeechCodeState(getSpeechCodeState(i2));
                }
                i2++;
            }
            if (this.mPendingMO != null) {
                Rlog.d(LOG_TAG, "Pending MO dropped before poll fg state:" + this.mForegroundCall.getState());
                this.mDroppedDuringPoll.add(this.mPendingMO);
                this.mPendingMO = null;
                this.mHangupPendingMO = false;
            }
            if (connection2 != null) {
                this.mPhone.notifyNewRingingConnection(connection2);
            }
            int i3 = 0;
            for (i = this.mDroppedDuringPoll.size() - 1; i >= 0; i--) {
                int onDisconnect;
                GsmConnection gsmConnection = (GsmConnection) this.mDroppedDuringPoll.get(i);
                if (gsmConnection.isIncoming() && gsmConnection.getConnectTime() == 0) {
                    int i4 = gsmConnection.mCause == 3 ? 16 : 1;
                    log("missed/rejected call, conn.cause=" + gsmConnection.mCause);
                    log("setting cause to " + i4);
                    this.mDroppedDuringPoll.remove(i);
                    onDisconnect = gsmConnection.onDisconnect(i4) | i3;
                } else if (gsmConnection.mCause == 3 || gsmConnection.mCause == 7) {
                    this.mDroppedDuringPoll.remove(i);
                    onDisconnect = gsmConnection.onDisconnect(gsmConnection.mCause) | i3;
                } else {
                    onDisconnect = i3;
                }
                i3 = onDisconnect;
            }
            Iterator it2 = this.mHandoverConnections.iterator();
            while (it2.hasNext()) {
                connection = (Connection) it2.next();
                log("handlePollCalls - disconnect hoConn= " + connection);
                ((ImsPhoneConnection) connection).onDisconnect(-1);
                it2.remove();
            }
            if (this.mDroppedDuringPoll.size() > 0) {
                this.mCi.getLastCallFailCause(obtainNoPollCompleteMessage(5));
            }
            if (!TelBrand.IS_SBM) {
                if (!(connection2 == null && obj == null && i3 == 0)) {
                    internalClearDisconnected();
                }
                updatePhoneState();
            } else if (i3 != 0) {
                updatePhoneState();
                internalClearDisconnected();
            } else {
                if (!(connection2 == null && obj == null)) {
                    internalClearDisconnected();
                }
                updatePhoneState();
            }
            if (obj2 != null) {
                Iterator it3 = arrayList.iterator();
                while (it3.hasNext()) {
                    connection = (Connection) it3.next();
                    log("Notify unknown for " + connection);
                    this.mPhone.notifyUnknownConnection(connection);
                }
            }
            if (obj != null || connection2 != null || i3 != 0) {
                this.mPhone.notifyPreciseCallStateChanged();
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void hangup(GsmCall gsmCall) throws CallStateException {
        if (gsmCall.getConnections().size() == 0) {
            throw new CallStateException("no connections in call");
        }
        if (TelBrand.IS_DCM && gsmCall == this.mRingingCall) {
            for (int i = 0; i < gsmCall.getConnections().size(); i++) {
                GsmConnection gsmConnection = (GsmConnection) gsmCall.getConnections().get(i);
                if (gsmConnection.getAcceptCallPending()) {
                    gsmConnection.setHangUpCallPending(true);
                    return;
                }
            }
        }
        if (gsmCall == this.mRingingCall) {
            log("(ringing) hangup waiting or background");
            if (TelBrand.IS_DCM) {
                this.mCi.hangupWaitingOrBackground(obtainCompleteMessage(18, 0, 0, gsmCall));
            } else {
                this.mCi.hangupWaitingOrBackground(obtainCompleteMessage());
            }
        } else if (gsmCall == this.mForegroundCall) {
            if (gsmCall.isDialingOrAlerting()) {
                log("(foregnd) hangup dialing or alerting...");
                hangup((GsmConnection) gsmCall.getConnections().get(0));
            } else if (this.mRingingCall.isRinging()) {
                log("hangup all conns in active/background call, without affecting ringing call");
                hangupAllConnections(gsmCall);
            } else if (TelBrand.IS_DCM) {
                hangupForegroundResumeBackground(gsmCall);
            } else {
                hangupForegroundResumeBackground();
            }
        } else if (gsmCall != this.mBackgroundCall) {
            throw new RuntimeException("GsmCall " + gsmCall + "does not belong to GsmCallTracker " + this);
        } else if (this.mRingingCall.isRinging()) {
            log("hangup all conns in background call");
            hangupAllConnections(gsmCall);
        } else if (TelBrand.IS_DCM) {
            hangupWaitingOrBackground(gsmCall);
        } else {
            hangupWaitingOrBackground();
        }
        if (TelBrand.IS_DCM) {
            gsmCall.onDisconnecting();
        } else {
            gsmCall.onHangupLocal();
        }
        this.mPhone.notifyPreciseCallStateChanged();
    }

    /* Access modifiers changed, original: 0000 */
    public void hangup(GsmConnection gsmConnection) throws CallStateException {
        if (gsmConnection.mOwner != this) {
            throw new CallStateException("GsmConnection " + gsmConnection + "does not belong to GsmCallTracker " + this);
        }
        if (gsmConnection == this.mPendingMO) {
            log("hangup: set hangupPendingMO to true");
            this.mHangupPendingMO = true;
        } else {
            try {
                if (TelBrand.IS_DCM) {
                    this.mCi.hangupConnection(gsmConnection.getGSMIndex(), obtainCompleteMessage(18, 0, 0, gsmConnection));
                } else {
                    this.mCi.hangupConnection(gsmConnection.getGSMIndex(), obtainCompleteMessage());
                }
            } catch (CallStateException e) {
                Rlog.w(LOG_TAG, "GsmCallTracker WARN: hangup() on absent connection " + gsmConnection);
            }
        }
        gsmConnection.onHangupLocal();
    }

    /* Access modifiers changed, original: 0000 */
    public void hangupAllConnections(GsmCall gsmCall) {
        try {
            int size = gsmCall.mConnections.size();
            for (int i = 0; i < size; i++) {
                GsmConnection gsmConnection = (GsmConnection) gsmCall.mConnections.get(i);
                if (TelBrand.IS_DCM) {
                    this.mCi.hangupConnection(gsmConnection.getGSMIndex(), obtainCompleteMessage(18, 0, 0, gsmConnection));
                    this.hangupCallOperations++;
                } else {
                    this.mCi.hangupConnection(gsmConnection.getGSMIndex(), obtainCompleteMessage());
                }
            }
        } catch (CallStateException e) {
            Rlog.e(LOG_TAG, "hangupConnectionByIndex caught " + e);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void hangupConnectionByIndex(GsmCall gsmCall, int i) throws CallStateException {
        int size = gsmCall.mConnections.size();
        int i2 = 0;
        while (i2 < size) {
            GsmConnection gsmConnection = (GsmConnection) gsmCall.mConnections.get(i2);
            if (gsmConnection.getGSMIndex() != i) {
                i2++;
            } else if (TelBrand.IS_DCM) {
                this.mCi.hangupConnection(i, obtainCompleteMessage(18, 0, 0, gsmConnection));
                return;
            } else {
                this.mCi.hangupConnection(i, obtainCompleteMessage());
                return;
            }
        }
        throw new CallStateException("no gsm index found");
    }

    /* Access modifiers changed, original: 0000 */
    public void hangupForegroundResumeBackground() {
        log("hangupForegroundResumeBackground");
        this.mCi.hangupForegroundResumeBackground(obtainCompleteMessage());
    }

    /* Access modifiers changed, original: 0000 */
    public void hangupForegroundResumeBackground(GsmCall gsmCall) {
        if (TelBrand.IS_DCM) {
            log("hangupForegroundResumeBackground" + gsmCall);
            if (this.mRingingCall.isRinging()) {
                for (int i = 0; i < this.mRingingCall.getConnections().size(); i++) {
                    ((GsmConnection) this.mRingingCall.getConnections().get(i)).setAcceptCallPending(true);
                }
            }
            this.mCi.hangupForegroundResumeBackground(obtainCompleteMessage(18, 0, 0, gsmCall));
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void hangupWaitingOrBackground() {
        log("hangupWaitingOrBackground");
        this.mCi.hangupWaitingOrBackground(obtainCompleteMessage());
    }

    public void hangupWaitingOrBackground(GsmCall gsmCall) {
        if (TelBrand.IS_DCM) {
            log("hangupWaitingOrBackground" + gsmCall);
            this.mCi.hangupWaitingOrBackground(obtainCompleteMessage(18, 0, 0, gsmCall));
        }
    }

    public void hangup_UserReject() throws CallStateException {
        if (this.mRingingCall.getState() == Call.State.INCOMING || this.mRingingCall.getState() == Call.State.WAITING) {
            Rlog.d(LOG_TAG, "hangup_UserReject() (ringing) hangup user reject");
            byte[] bArr = new byte[((("QOEMHOOK".length() + 8) + 4) + 1)];
            ByteBuffer wrap = ByteBuffer.wrap(bArr);
            wrap.order(ByteOrder.nativeOrder());
            wrap.put("QOEMHOOK".getBytes());
            wrap.putInt(524308);
            wrap.putInt(4);
            wrap.put((byte) 0);
            if (TelBrand.IS_SBM) {
                this.mCi.invokeOemRilRequestRaw(bArr, null);
            } else {
                this.mCi.invokeOemRilRequestRaw(bArr, obtainCompleteMessage());
            }
            this.mRingingCall.onHangupLocal();
            this.mPhone.notifyPreciseCallStateChanged();
            Rlog.d(LOG_TAG, "hangup_UserReject() end");
            return;
        }
        Rlog.d(LOG_TAG, "hangup_UserReject() call isn't ringingCall, throw new RuntimeException");
        throw new RuntimeException("GsmCall " + this.mRingingCall + "does not belong to GsmCallTracker " + this);
    }

    public boolean isAcceptCallPending() {
        return TelBrand.IS_DCM ? this.acceptCallPending && this.mPendingOperations <= 1 : false;
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(LOG_TAG, "[GsmCallTracker] " + str);
    }

    public void registerForVoiceCallEnded(Handler handler, int i, Object obj) {
        this.mVoiceCallEndedRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForVoiceCallStarted(Handler handler, int i, Object obj) {
        this.mVoiceCallStartedRegistrants.add(new Registrant(handler, i, obj));
    }

    /* Access modifiers changed, original: 0000 */
    public void rejectCall() throws CallStateException {
        if (this.mRingingCall.getState().isRinging()) {
            this.mCi.rejectCall(obtainCompleteMessage());
            return;
        }
        throw new CallStateException("phone not ringing");
    }

    /* Access modifiers changed, original: 0000 */
    public void separate(GsmConnection gsmConnection) throws CallStateException {
        if (gsmConnection.mOwner != this) {
            throw new CallStateException("GsmConnection " + gsmConnection + "does not belong to GsmCallTracker " + this);
        }
        try {
            this.mCi.separateConnection(gsmConnection.getGSMIndex(), obtainCompleteMessage(12));
        } catch (CallStateException e) {
            Rlog.w(LOG_TAG, "GsmCallTracker WARN: separate() on absent connection " + gsmConnection);
        }
    }

    public void setAcceptCallPending() {
        if (TelBrand.IS_DCM) {
            this.mPendingOperations++;
            this.acceptCallPending = true;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void setMute(boolean z) {
        this.mDesiredMute = z;
        this.mCi.setMute(this.mDesiredMute, null);
    }

    /* Access modifiers changed, original: 0000 */
    public void switchWaitingOrHoldingAndActive() throws CallStateException {
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            throw new CallStateException("cannot be in the incoming state");
        }
        this.mCi.switchWaitingOrHoldingAndActive(obtainCompleteMessage(8));
    }

    public void unregisterForVoiceCallEnded(Handler handler) {
        this.mVoiceCallEndedRegistrants.remove(handler);
    }

    public void unregisterForVoiceCallStarted(Handler handler) {
        this.mVoiceCallStartedRegistrants.remove(handler);
    }

    public void updateSpeechCodec(int[] iArr) {
        int i = 0;
        this.SpeechCodecState = iArr;
        log("SPEECH_CODE_UPDATE...");
        int i2 = 0;
        while (true) {
            int i3 = i;
            if (i3 >= this.mConnections.length) {
                break;
            }
            if (this.mConnections[i3] != null) {
                this.mConnections[i3].setSpeechCodeState(getSpeechCodeState(i3));
                i2 = 1;
            }
            i = i3 + 1;
        }
        if (i2 != 0) {
            log("SPEECH_CODE CallStateChanged...");
            this.mPhone.notifyPreciseCallStateChanged();
            return;
        }
        log("SPEECH_CODE Connection null...");
        this.SpeechCodecState = null;
    }
}
