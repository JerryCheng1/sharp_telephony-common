package com.android.internal.telephony.test;

import com.android.internal.telephony.ATParseEx;
import com.android.internal.telephony.DriverCall;

/* compiled from: SimulatedGsmCallState.java */
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
class CallInfo {
    boolean mIsMT;
    boolean mIsMpty;
    String mNumber;
    State mState;
    int mTOA;

    /* compiled from: SimulatedGsmCallState.java */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    enum State {
        ACTIVE(0),
        HOLDING(1),
        DIALING(2),
        ALERTING(3),
        INCOMING(4),
        WAITING(5);
        
        private final int mValue;

        State(int value) {
            this.mValue = value;
        }

        public int value() {
            return this.mValue;
        }
    }

    CallInfo(boolean isMT, State state, boolean isMpty, String number) {
        this.mIsMT = isMT;
        this.mState = state;
        this.mIsMpty = isMpty;
        this.mNumber = number;
        if (number.length() <= 0 || number.charAt(0) != '+') {
            this.mTOA = 129;
        } else {
            this.mTOA = 145;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static CallInfo createOutgoingCall(String number) {
        return new CallInfo(false, State.DIALING, false, number);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static CallInfo createIncomingCall(String number) {
        return new CallInfo(true, State.INCOMING, false, number);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public String toCLCCLine(int index) {
        return "+CLCC: " + index + "," + (this.mIsMT ? "1" : "0") + "," + this.mState.value() + ",0," + (this.mIsMpty ? "1" : "0") + ",\"" + this.mNumber + "\"," + this.mTOA;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public DriverCall toDriverCall(int index) {
        DriverCall ret = new DriverCall();
        ret.index = index;
        ret.isMT = this.mIsMT;
        try {
            ret.state = DriverCall.stateFromCLCC(this.mState.value());
            ret.isMpty = this.mIsMpty;
            ret.number = this.mNumber;
            ret.TOA = this.mTOA;
            ret.isVoice = true;
            ret.als = 0;
            return ret;
        } catch (ATParseEx ex) {
            throw new RuntimeException("should never happen", ex);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean isActiveOrHeld() {
        return this.mState == State.ACTIVE || this.mState == State.HOLDING;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean isConnecting() {
        return this.mState == State.DIALING || this.mState == State.ALERTING;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean isRinging() {
        return this.mState == State.INCOMING || this.mState == State.WAITING;
    }
}
