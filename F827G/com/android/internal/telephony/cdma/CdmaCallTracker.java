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
import com.android.internal.telephony.ITelephony.Stub;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.imsphone.ImsPhoneConnection;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class CdmaCallTracker extends CallTracker {
    private static final boolean DBG_POLL = false;
    static final String LOG_TAG = "CdmaCallTracker";
    static final int MAX_CONNECTIONS = 8;
    static final int MAX_CONNECTIONS_PER_CALL = 1;
    private static final boolean REPEAT_POLLING = false;
    private int m3WayCallFlashDelay = 0;
    CdmaCall mBackgroundCall = new CdmaCall(this);
    RegistrantList mCallWaitingRegistrants = new RegistrantList();
    CdmaConnection[] mConnections = new CdmaConnection[8];
    boolean mDesiredMute = false;
    ArrayList<CdmaConnection> mDroppedDuringPoll = new ArrayList(8);
    CdmaCall mForegroundCall = new CdmaCall(this);
    boolean mHangupPendingMO;
    private boolean mIsEcmTimerCanceled = false;
    boolean mIsInEmergencyCall = false;
    int mPendingCallClirMode;
    boolean mPendingCallInEcm = false;
    CdmaConnection mPendingMO;
    CDMAPhone mPhone;
    CdmaCall mRingingCall = new CdmaCall(this);
    State mState = State.IDLE;
    RegistrantList mVoiceCallEndedRegistrants = new RegistrantList();
    RegistrantList mVoiceCallStartedRegistrants = new RegistrantList();

    CdmaCallTracker(CDMAPhone cDMAPhone) {
        this.mPhone = cDMAPhone;
        this.mCi = cDMAPhone.mCi;
        this.mCi.registerForCallStateChanged(this, 2, null);
        this.mCi.registerForOn(this, 9, null);
        this.mCi.registerForNotAvailable(this, 10, null);
        this.mCi.registerForCallWaitingInfo(this, 15, null);
        this.mForegroundCall.setGeneric(false);
    }

    private void checkAndEnableDataCallAfterEmergencyCallDropped() {
        if (this.mIsInEmergencyCall) {
            this.mIsInEmergencyCall = false;
            String str = SystemProperties.get("ril.cdma.inecmmode", "false");
            log("checkAndEnableDataCallAfterEmergencyCallDropped,inEcm=" + str);
            if (str.compareTo("false") == 0) {
                this.mPhone.mDcTracker.setInternalDataEnabled(true);
            }
        }
    }

    private Connection checkMtFindNewRinging(DriverCall driverCall, int i) {
        if (this.mConnections[i].getCall() == this.mRingingCall) {
            Connection connection = this.mConnections[i];
            log("Notify new ring " + driverCall);
            return connection;
        }
        Rlog.e(LOG_TAG, "Phantom call appeared " + driverCall);
        if (driverCall.state == DriverCall.State.ALERTING || driverCall.state == DriverCall.State.DIALING) {
            return null;
        }
        this.mConnections[i].onConnectedInOrOut();
        if (driverCall.state != DriverCall.State.HOLDING) {
            return null;
        }
        this.mConnections[i].onStartedHolding();
        return null;
    }

    private Connection dialThreeWay(String str) {
        if (this.mForegroundCall.isIdle()) {
            return null;
        }
        disableDataCallInEmergencyCall(str);
        this.mPendingMO = new CdmaConnection(this.mPhone.getContext(), checkForTestEmergencyNumber(str), this, this.mForegroundCall);
        this.m3WayCallFlashDelay = this.mPhone.getContext().getResources().getInteger(17694880);
        if (this.m3WayCallFlashDelay > 0) {
            this.mCi.sendCDMAFeatureCode("", obtainMessage(20));
        } else {
            this.mCi.sendCDMAFeatureCode(this.mPendingMO.getAddress(), obtainMessage(16));
        }
        return this.mPendingMO;
    }

    private void disableDataCallInEmergencyCall(String str) {
        if (PhoneNumberUtils.isLocalEmergencyNumber(this.mPhone.getContext(), str)) {
            log("disableDataCallInEmergencyCall");
            this.mIsInEmergencyCall = true;
            this.mPhone.mDcTracker.setInternalDataEnabled(false);
        }
    }

    private void flashAndSetGenericTrue() {
        this.mCi.sendCDMAFeatureCode("", obtainMessage(8));
        this.mForegroundCall.setGeneric(true);
        this.mPhone.notifyPreciseCallStateChanged();
    }

    private void handleCallWaitingInfo(CdmaCallWaitingNotification cdmaCallWaitingNotification) {
        if (this.mForegroundCall.mConnections.size() > 1) {
            this.mForegroundCall.setGeneric(true);
        }
        this.mRingingCall.setGeneric(false);
        CdmaConnection cdmaConnection = new CdmaConnection(this.mPhone.getContext(), cdmaCallWaitingNotification, this, this.mRingingCall);
        updatePhoneState();
        notifyCallWaitingInfo(cdmaCallWaitingNotification);
    }

    private void handleEcmTimer(int i) {
        this.mPhone.handleTimerInEmergencyCallbackMode(i);
        switch (i) {
            case 0:
                this.mIsEcmTimerCanceled = false;
                return;
            case 1:
                this.mIsEcmTimerCanceled = true;
                return;
            default:
                Rlog.e(LOG_TAG, "handleEcmTimer, unsupported action " + i);
                return;
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

    private void notifyCallWaitingInfo(CdmaCallWaitingNotification cdmaCallWaitingNotification) {
        if (this.mCallWaitingRegistrants != null) {
            this.mCallWaitingRegistrants.notifyRegistrants(new AsyncResult(null, cdmaCallWaitingNotification, null));
        }
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
        log("update phone state, old=" + state + " new=" + this.mState);
        if (this.mState != state) {
            this.mPhone.notifyPhoneStateChanged();
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void acceptCall() throws CallStateException {
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            Rlog.i("phone", "acceptCall: incoming...");
            setMute(false);
            this.mCi.acceptCall(obtainCompleteMessage());
        } else if (this.mRingingCall.getState() == Call.State.WAITING) {
            CdmaConnection cdmaConnection = (CdmaConnection) this.mRingingCall.getLatestConnection();
            cdmaConnection.updateParent(this.mRingingCall, this.mForegroundCall);
            cdmaConnection.onConnectedInOrOut();
            updatePhoneState();
            switchWaitingOrHoldingAndActive();
        } else {
            throw new CallStateException("phone not ringing");
        }
    }

    /* Access modifiers changed, original: 0000 */
    public boolean canConference() {
        return this.mForegroundCall.getState() == Call.State.ACTIVE && this.mBackgroundCall.getState() == Call.State.HOLDING && !this.mBackgroundCall.isFull() && !this.mForegroundCall.isFull();
    }

    /* Access modifiers changed, original: 0000 */
    public boolean canDial() {
        int state = this.mPhone.getServiceState().getState();
        String str = SystemProperties.get("ro.telephony.disable-call", "false");
        boolean z = (state == 3 || this.mPendingMO != null || this.mRingingCall.isRinging() || str.equals("true") || (this.mForegroundCall.getState().isAlive() && this.mForegroundCall.getState() != Call.State.ACTIVE && this.mBackgroundCall.getState().isAlive())) ? false : true;
        if (!z) {
            boolean z2 = state != 3;
            boolean z3 = this.mPendingMO == null;
            boolean z4 = !this.mRingingCall.isRinging();
            boolean z5 = !str.equals("true");
            boolean z6 = !this.mForegroundCall.getState().isAlive();
            boolean z7 = this.mForegroundCall.getState() == Call.State.ACTIVE;
            boolean z8 = !this.mBackgroundCall.getState().isAlive();
            log(String.format("canDial is false\n((serviceState=%d) != ServiceState.STATE_POWER_OFF)::=%s\n&& pendingMO == null::=%s\n&& !ringingCall.isRinging()::=%s\n&& !disableCall.equals(\"true\")::=%s\n&& (!foregroundCall.getState().isAlive()::=%s\n   || foregroundCall.getState() == CdmaCall.State.ACTIVE::=%s\n   ||!backgroundCall.getState().isAlive())::=%s)", new Object[]{Integer.valueOf(state), Boolean.valueOf(z2), Boolean.valueOf(z3), Boolean.valueOf(z4), Boolean.valueOf(z5), Boolean.valueOf(z6), Boolean.valueOf(z7), Boolean.valueOf(z8)}));
        }
        return z;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean canTransfer() {
        Rlog.e(LOG_TAG, "canTransfer: not possible in CDMA");
        return false;
    }

    /* Access modifiers changed, original: 0000 */
    public void clearDisconnected() {
        internalClearDisconnected();
        updatePhoneState();
        this.mPhone.notifyPreciseCallStateChanged();
    }

    /* Access modifiers changed, original: 0000 */
    public void conference() {
        flashAndSetGenericTrue();
    }

    /* Access modifiers changed, original: 0000 */
    public Connection dial(String str) throws CallStateException {
        return dial(str, 0);
    }

    /* Access modifiers changed, original: 0000 */
    public Connection dial(String str, int i) throws CallStateException {
        clearDisconnected();
        if (canDial()) {
            boolean z;
            int z2;
            String systemProperty = this.mPhone.getSystemProperty("gsm.operator.iso-country", "");
            String systemProperty2 = this.mPhone.getSystemProperty("gsm.sim.operator.iso-country", "");
            if (TextUtils.isEmpty(systemProperty) || TextUtils.isEmpty(systemProperty2) || systemProperty2.equals(systemProperty)) {
                z2 = false;
            } else {
                z2 = 1;
            }
            if (z2 != 0) {
                if ("us".equals(systemProperty2)) {
                    if (z2 == 0 || "vi".equals(systemProperty)) {
                        z2 = false;
                    } else {
                        z2 = 1;
                    }
                } else if ("vi".equals(systemProperty2)) {
                    if (z2 == 0 || "us".equals(systemProperty)) {
                        z2 = false;
                    } else {
                        z2 = 1;
                    }
                }
            }
            String convertNumberIfNecessary = z2 != 0 ? convertNumberIfNecessary(this.mPhone, str) : str;
            boolean equals = SystemProperties.get("ril.cdma.inecmmode", "false").equals("true");
            boolean isLocalEmergencyNumber = PhoneNumberUtils.isLocalEmergencyNumber(this.mPhone.getContext(), convertNumberIfNecessary);
            if (equals && isLocalEmergencyNumber) {
                handleEcmTimer(1);
            }
            this.mForegroundCall.setGeneric(false);
            if (this.mForegroundCall.getState() == Call.State.ACTIVE) {
                return dialThreeWay(convertNumberIfNecessary);
            }
            this.mPendingMO = new CdmaConnection(this.mPhone.getContext(), checkForTestEmergencyNumber(convertNumberIfNecessary), this, this.mForegroundCall);
            this.mHangupPendingMO = false;
            if (this.mPendingMO.getAddress() == null || this.mPendingMO.getAddress().length() == 0 || this.mPendingMO.getAddress().indexOf(78) >= 0) {
                this.mPendingMO.mCause = 7;
                pollCallsWhenSafe();
            } else {
                setMute(false);
                disableDataCallInEmergencyCall(convertNumberIfNecessary);
                if (!equals || (equals && isLocalEmergencyNumber)) {
                    if (TelBrand.IS_KDDI) {
                        this.mPendingMO.getNumber();
                        if (PhoneNumberUtils.isEmergencyNumber(this.mPendingMO.getNumber())) {
                            if (CallerInfo.sEnable) {
                                CallerInfo.TelLog(1, "dial() not add the special number", new Object[0]);
                            }
                            this.mPendingMO.getNumber();
                        } else {
                            if (CallerInfo.sEnable) {
                                CallerInfo.TelLog(1, "dial() add the special number", new Object[0]);
                            }
                            try {
                                ITelephony asInterface = Stub.asInterface(ServiceManager.getService("phone"));
                                if (asInterface != null) {
                                    asInterface.addSpecialNumber(this.mPendingMO.getNumber());
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
                    this.mCi.dial(this.mPendingMO.getAddress(), i, obtainCompleteMessage());
                } else {
                    this.mPhone.exitEmergencyCallbackMode();
                    this.mPhone.setOnEcbModeExitResponse(this, 14, null);
                    this.mPendingCallClirMode = i;
                    this.mPendingCallInEcm = true;
                }
            }
            if (this.mNumberConverted) {
                this.mPendingMO.setConverted(str);
                this.mNumberConverted = false;
            }
            updatePhoneState();
            this.mPhone.notifyPreciseCallStateChanged();
            return this.mPendingMO;
        }
        throw new CallStateException("cannot dial in current state");
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

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        int i;
        printWriter.println("GsmCallTracker extends:");
        super.dump(fileDescriptor, printWriter, strArr);
        printWriter.println("droppedDuringPoll: length=" + this.mConnections.length);
        for (i = 0; i < this.mConnections.length; i++) {
            printWriter.printf(" mConnections[%d]=%s\n", new Object[]{Integer.valueOf(i), this.mConnections[i]});
        }
        printWriter.println(" mVoiceCallEndedRegistrants=" + this.mVoiceCallEndedRegistrants);
        printWriter.println(" mVoiceCallStartedRegistrants=" + this.mVoiceCallStartedRegistrants);
        printWriter.println(" mCallWaitingRegistrants=" + this.mCallWaitingRegistrants);
        printWriter.println("droppedDuringPoll: size=" + this.mDroppedDuringPoll.size());
        for (i = 0; i < this.mDroppedDuringPoll.size(); i++) {
            printWriter.printf(" mDroppedDuringPoll[%d]=%s\n", new Object[]{Integer.valueOf(i), this.mDroppedDuringPoll.get(i)});
        }
        printWriter.println(" mRingingCall=" + this.mRingingCall);
        printWriter.println(" mForegroundCall=" + this.mForegroundCall);
        printWriter.println(" mBackgroundCall=" + this.mBackgroundCall);
        printWriter.println(" mPendingMO=" + this.mPendingMO);
        printWriter.println(" mHangupPendingMO=" + this.mHangupPendingMO);
        printWriter.println(" mPendingCallInEcm=" + this.mPendingCallInEcm);
        printWriter.println(" mIsInEmergencyCall=" + this.mIsInEmergencyCall);
        printWriter.println(" mPhone=" + this.mPhone);
        printWriter.println(" mDesiredMute=" + this.mDesiredMute);
        printWriter.println(" mPendingCallClirMode=" + this.mPendingCallClirMode);
        printWriter.println(" mState=" + this.mState);
        printWriter.println(" mIsEcmTimerCanceled=" + this.mIsEcmTimerCanceled);
    }

    /* Access modifiers changed, original: 0000 */
    public void explicitCallTransfer() {
        this.mCi.explicitCallTransfer(obtainCompleteMessage(13));
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        Rlog.d(LOG_TAG, "CdmaCallTracker finalized");
    }

    /* Access modifiers changed, original: 0000 */
    public CdmaConnection getConnectionByIndex(CdmaCall cdmaCall, int i) throws CallStateException {
        int size = cdmaCall.mConnections.size();
        for (int i2 = 0; i2 < size; i2++) {
            CdmaConnection cdmaConnection = (CdmaConnection) cdmaCall.mConnections.get(i2);
            if (cdmaConnection.getCDMAIndex() == i) {
                return cdmaConnection;
            }
        }
        return null;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean getMute() {
        return this.mDesiredMute;
    }

    public State getState() {
        return this.mState;
    }

    public void handleMessage(Message message) {
        int i = 0;
        if (this.mPhone.mIsTheCurrentActivePhone) {
            AsyncResult asyncResult;
            switch (message.what) {
                case 1:
                    Rlog.d(LOG_TAG, "Event EVENT_POLL_CALLS_RESULT Received");
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
                    operationComplete();
                    return;
                case 5:
                    int i2;
                    asyncResult = (AsyncResult) message.obj;
                    operationComplete();
                    if (asyncResult.exception != null) {
                        Rlog.i(LOG_TAG, "Exception during getLastCallFailCause, assuming normal disconnect");
                        i2 = 16;
                    } else {
                        i2 = ((int[]) asyncResult.result)[0];
                    }
                    int size = this.mDroppedDuringPoll.size();
                    while (i < size) {
                        ((CdmaConnection) this.mDroppedDuringPoll.get(i)).onRemoteDisconnect(i2);
                        i++;
                    }
                    updatePhoneState();
                    this.mPhone.notifyPreciseCallStateChanged();
                    this.mDroppedDuringPoll.clear();
                    return;
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
                    asyncResult = (AsyncResult) message.obj;
                    if (asyncResult.exception == null) {
                        handleCallWaitingInfo((CdmaCallWaitingNotification) asyncResult.result);
                        Rlog.d(LOG_TAG, "Event EVENT_CALL_WAITING_INFO_CDMA Received");
                        return;
                    }
                    return;
                case 16:
                    if (((AsyncResult) message.obj).exception == null) {
                        this.mPendingMO.onConnectedInOrOut();
                        this.mPendingMO = null;
                        return;
                    }
                    return;
                case 20:
                    if (((AsyncResult) message.obj).exception == null) {
                        postDelayed(new Runnable() {
                            public void run() {
                                if (CdmaCallTracker.this.mPendingMO != null) {
                                    CdmaCallTracker.this.mCi.sendCDMAFeatureCode(CdmaCallTracker.this.mPendingMO.getAddress(), CdmaCallTracker.this.obtainMessage(16));
                                }
                            }
                        }, (long) this.m3WayCallFlashDelay);
                        return;
                    }
                    this.mPendingMO = null;
                    Rlog.w(LOG_TAG, "exception happened on Blank Flash for 3-way call");
                    return;
                default:
                    throw new RuntimeException("unexpected event not handled");
            }
        }
        Rlog.w(LOG_TAG, "Ignoring events received on inactive CdmaPhone");
    }

    /* Access modifiers changed, original: protected */
    public void handlePollCalls(AsyncResult asyncResult) {
        List list;
        if (asyncResult.exception == null) {
            list = (List) asyncResult.result;
        } else if (isCommandExceptionRadioNotAvailable(asyncResult.exception)) {
            Object list2 = new ArrayList();
        } else {
            pollCallsAfterDelay();
            return;
        }
        Connection connection = null;
        Connection connection2 = null;
        Object obj = null;
        Object obj2 = null;
        int i = 0;
        int size = list2.size();
        int i2 = 0;
        while (i < this.mConnections.length) {
            Object obj3;
            Connection connection3 = this.mConnections[i];
            DriverCall driverCall = null;
            if (i2 < size) {
                driverCall = (DriverCall) list2.get(i2);
                if (driverCall.index == i + 1) {
                    i2++;
                } else {
                    driverCall = null;
                }
            }
            if (connection3 == null && driverCall != null) {
                if (this.mPendingMO == null || !this.mPendingMO.compareTo(driverCall)) {
                    log("pendingMo=" + this.mPendingMO + ", dc=" + driverCall);
                    this.mConnections[i] = new CdmaConnection(this.mPhone.getContext(), driverCall, this, i);
                    Connection hoConnection = getHoConnection(driverCall);
                    if (hoConnection != null) {
                        this.mConnections[i].migrateFrom(hoConnection);
                        this.mHandoverConnections.remove(hoConnection);
                        this.mPhone.notifyHandoverStateChanged(this.mConnections[i]);
                    } else {
                        connection = checkMtFindNewRinging(driverCall, i);
                        if (connection == null) {
                            obj3 = 1;
                            connection2 = this.mConnections[i];
                            checkAndEnableDataCallAfterEmergencyCallDropped();
                            obj2 = obj3;
                        }
                    }
                    obj3 = obj2;
                    checkAndEnableDataCallAfterEmergencyCallDropped();
                    obj2 = obj3;
                } else {
                    this.mConnections[i] = this.mPendingMO;
                    this.mPendingMO.mIndex = i;
                    this.mPendingMO.update(driverCall);
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
                obj3 = 1;
            } else if (connection3 != null && driverCall == null) {
                int i3;
                int size2 = this.mForegroundCall.mConnections.size();
                for (i3 = 0; i3 < size2; i3++) {
                    log("adding fgCall cn " + i3 + " to droppedDuringPoll");
                    this.mDroppedDuringPoll.add((CdmaConnection) this.mForegroundCall.mConnections.get(i3));
                }
                size2 = this.mRingingCall.mConnections.size();
                for (i3 = 0; i3 < size2; i3++) {
                    log("adding rgCall cn " + i3 + " to droppedDuringPoll");
                    this.mDroppedDuringPoll.add((CdmaConnection) this.mRingingCall.mConnections.get(i3));
                }
                this.mForegroundCall.setGeneric(false);
                this.mRingingCall.setGeneric(false);
                if (this.mIsEcmTimerCanceled) {
                    handleEcmTimer(0);
                }
                checkAndEnableDataCallAfterEmergencyCallDropped();
                this.mConnections[i] = null;
                obj3 = obj;
            } else if (connection3 == null || driverCall == null) {
                obj3 = obj;
            } else if (connection3.isIncoming() == driverCall.isMT) {
                obj3 = (obj != null || connection3.update(driverCall)) ? 1 : null;
            } else if (driverCall.isMT) {
                this.mDroppedDuringPoll.add(connection3);
                connection = checkMtFindNewRinging(driverCall, i);
                if (connection == null) {
                    obj2 = 1;
                    connection2 = connection3;
                }
                checkAndEnableDataCallAfterEmergencyCallDropped();
                obj3 = obj;
            } else {
                Rlog.e(LOG_TAG, "Error in RIL, Phantom call appeared " + driverCall);
                obj3 = obj;
            }
            i++;
            obj = obj3;
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
        if (connection != null) {
            this.mPhone.notifyNewRingingConnection(connection);
        }
        i2 = 0;
        for (i = this.mDroppedDuringPoll.size() - 1; i >= 0; i--) {
            int onDisconnect;
            CdmaConnection cdmaConnection = (CdmaConnection) this.mDroppedDuringPoll.get(i);
            if (cdmaConnection.isIncoming() && cdmaConnection.getConnectTime() == 0) {
                int i4 = cdmaConnection.mCause == 3 ? 16 : 1;
                log("missed/rejected call, conn.cause=" + cdmaConnection.mCause);
                log("setting cause to " + i4);
                this.mDroppedDuringPoll.remove(i);
                onDisconnect = cdmaConnection.onDisconnect(i4) | i2;
            } else if (cdmaConnection.mCause == 3 || cdmaConnection.mCause == 7) {
                this.mDroppedDuringPoll.remove(i);
                onDisconnect = cdmaConnection.onDisconnect(cdmaConnection.mCause) | i2;
            } else {
                onDisconnect = i2;
            }
            i2 = onDisconnect;
        }
        Iterator it = this.mHandoverConnections.iterator();
        while (it.hasNext()) {
            Connection connection4 = (Connection) it.next();
            log("handlePollCalls - disconnect hoConn= " + connection4);
            ((ImsPhoneConnection) connection4).onDisconnect(-1);
            it.remove();
        }
        if (this.mDroppedDuringPoll.size() > 0) {
            this.mCi.getLastCallFailCause(obtainNoPollCompleteMessage(5));
        }
        if (!(connection == null && obj == null && i2 == 0)) {
            internalClearDisconnected();
        }
        updatePhoneState();
        if (obj2 != null) {
            this.mPhone.notifyUnknownConnection(connection2);
        }
        if (obj != null || connection != null || i2 != 0) {
            this.mPhone.notifyPreciseCallStateChanged();
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void hangup(CdmaCall cdmaCall) throws CallStateException {
        if (cdmaCall.getConnections().size() == 0) {
            throw new CallStateException("no connections in call");
        }
        if (cdmaCall == this.mRingingCall) {
            log("(ringing) hangup waiting or background");
            this.mCi.hangupWaitingOrBackground(obtainCompleteMessage());
        } else if (cdmaCall == this.mForegroundCall) {
            if (cdmaCall.isDialingOrAlerting()) {
                log("(foregnd) hangup dialing or alerting...");
                hangup((CdmaConnection) cdmaCall.getConnections().get(0));
            } else {
                hangupForegroundResumeBackground();
            }
        } else if (cdmaCall != this.mBackgroundCall) {
            throw new RuntimeException("CdmaCall " + cdmaCall + "does not belong to CdmaCallTracker " + this);
        } else if (this.mRingingCall.isRinging()) {
            log("hangup all conns in background call");
            hangupAllConnections(cdmaCall);
        } else {
            hangupWaitingOrBackground();
        }
        cdmaCall.onHangupLocal();
        this.mPhone.notifyPreciseCallStateChanged();
    }

    /* Access modifiers changed, original: 0000 */
    public void hangup(CdmaConnection cdmaConnection) throws CallStateException {
        if (cdmaConnection.mOwner != this) {
            throw new CallStateException("CdmaConnection " + cdmaConnection + "does not belong to CdmaCallTracker " + this);
        }
        if (cdmaConnection == this.mPendingMO) {
            log("hangup: set hangupPendingMO to true");
            this.mHangupPendingMO = true;
        } else if (cdmaConnection.getCall() == this.mRingingCall && this.mRingingCall.getState() == Call.State.WAITING) {
            cdmaConnection.onLocalDisconnect();
            updatePhoneState();
            this.mPhone.notifyPreciseCallStateChanged();
            return;
        } else {
            try {
                this.mCi.hangupConnection(cdmaConnection.getCDMAIndex(), obtainCompleteMessage());
            } catch (CallStateException e) {
                Rlog.w(LOG_TAG, "CdmaCallTracker WARN: hangup() on absent connection " + cdmaConnection);
            }
        }
        cdmaConnection.onHangupLocal();
    }

    /* Access modifiers changed, original: 0000 */
    public void hangupAllConnections(CdmaCall cdmaCall) {
        try {
            int size = cdmaCall.mConnections.size();
            for (int i = 0; i < size; i++) {
                this.mCi.hangupConnection(((CdmaConnection) cdmaCall.mConnections.get(i)).getCDMAIndex(), obtainCompleteMessage());
            }
        } catch (CallStateException e) {
            Rlog.e(LOG_TAG, "hangupConnectionByIndex caught " + e);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void hangupConnectionByIndex(CdmaCall cdmaCall, int i) throws CallStateException {
        int size = cdmaCall.mConnections.size();
        for (int i2 = 0; i2 < size; i2++) {
            if (((CdmaConnection) cdmaCall.mConnections.get(i2)).getCDMAIndex() == i) {
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
    public void hangupWaitingOrBackground() {
        log("hangupWaitingOrBackground");
        this.mCi.hangupWaitingOrBackground(obtainCompleteMessage());
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isInEmergencyCall() {
        return this.mIsInEmergencyCall;
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(LOG_TAG, "[CdmaCallTracker] " + str);
    }

    public void registerForCallWaiting(Handler handler, int i, Object obj) {
        this.mCallWaitingRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForVoiceCallEnded(Handler handler, int i, Object obj) {
        this.mVoiceCallEndedRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForVoiceCallStarted(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        this.mVoiceCallStartedRegistrants.add(registrant);
        if (this.mState != State.IDLE) {
            registrant.notifyRegistrant(new AsyncResult(null, null, null));
        }
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
    public void separate(CdmaConnection cdmaConnection) throws CallStateException {
        if (cdmaConnection.mOwner != this) {
            throw new CallStateException("CdmaConnection " + cdmaConnection + "does not belong to CdmaCallTracker " + this);
        }
        try {
            this.mCi.separateConnection(cdmaConnection.getCDMAIndex(), obtainCompleteMessage(12));
        } catch (CallStateException e) {
            Rlog.w(LOG_TAG, "CdmaCallTracker WARN: separate() on absent connection " + cdmaConnection);
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
        } else if (this.mForegroundCall.getConnections().size() > 1) {
            flashAndSetGenericTrue();
        } else {
            this.mCi.sendCDMAFeatureCode("", obtainMessage(8));
        }
    }

    public void unregisterForCallWaiting(Handler handler) {
        this.mCallWaitingRegistrants.remove(handler);
    }

    public void unregisterForVoiceCallEnded(Handler handler) {
        this.mVoiceCallEndedRegistrants.remove(handler);
    }

    public void unregisterForVoiceCallStarted(Handler handler) {
        this.mVoiceCallStartedRegistrants.remove(handler);
    }
}
