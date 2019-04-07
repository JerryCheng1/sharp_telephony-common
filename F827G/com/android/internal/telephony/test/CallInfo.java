package com.android.internal.telephony.test;

import com.android.internal.telephony.ATParseEx;
import com.android.internal.telephony.DriverCall;

class CallInfo {
    boolean mIsMT;
    boolean mIsMpty;
    String mNumber;
    State mState;
    int mTOA;

    enum State {
        ACTIVE(0),
        HOLDING(1),
        DIALING(2),
        ALERTING(3),
        INCOMING(4),
        WAITING(5);
        
        private final int mValue;

        private State(int i) {
            this.mValue = i;
        }

        public int value() {
            return this.mValue;
        }
    }

    CallInfo(boolean z, State state, boolean z2, String str) {
        this.mIsMT = z;
        this.mState = state;
        this.mIsMpty = z2;
        this.mNumber = str;
        if (str.length() <= 0 || str.charAt(0) != '+') {
            this.mTOA = 129;
        } else {
            this.mTOA = 145;
        }
    }

    static CallInfo createIncomingCall(String str) {
        return new CallInfo(true, State.INCOMING, false, str);
    }

    static CallInfo createOutgoingCall(String str) {
        return new CallInfo(false, State.DIALING, false, str);
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isActiveOrHeld() {
        return this.mState == State.ACTIVE || this.mState == State.HOLDING;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isConnecting() {
        return this.mState == State.DIALING || this.mState == State.ALERTING;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isRinging() {
        return this.mState == State.INCOMING || this.mState == State.WAITING;
    }

    /* Access modifiers changed, original: 0000 */
    public String toCLCCLine(int i) {
        return "+CLCC: " + i + "," + (this.mIsMT ? "1" : "0") + "," + this.mState.value() + ",0," + (this.mIsMpty ? "1" : "0") + ",\"" + this.mNumber + "\"," + this.mTOA;
    }

    /* Access modifiers changed, original: 0000 */
    public DriverCall toDriverCall(int i) {
        DriverCall driverCall = new DriverCall();
        driverCall.index = i;
        driverCall.isMT = this.mIsMT;
        try {
            driverCall.state = DriverCall.stateFromCLCC(this.mState.value());
            driverCall.isMpty = this.mIsMpty;
            driverCall.number = this.mNumber;
            driverCall.TOA = this.mTOA;
            driverCall.isVoice = true;
            driverCall.als = 0;
            return driverCall;
        } catch (ATParseEx e) {
            throw new RuntimeException("should never happen", e);
        }
    }
}
