package com.android.internal.telephony.cdma;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.Phone;
import java.util.List;

public final class CdmaCall extends Call {
    CdmaCallTracker mOwner;

    CdmaCall(CdmaCallTracker cdmaCallTracker) {
        this.mOwner = cdmaCallTracker;
    }

    /* Access modifiers changed, original: 0000 */
    public void attach(Connection connection, DriverCall driverCall) {
        this.mConnections.add(connection);
        this.mState = Call.stateFromDCState(driverCall.state);
    }

    /* Access modifiers changed, original: 0000 */
    public void attachFake(Connection connection, State state) {
        this.mConnections.add(connection);
        this.mState = state;
    }

    /* Access modifiers changed, original: 0000 */
    public void clearDisconnected() {
        for (int size = this.mConnections.size() - 1; size >= 0; size--) {
            if (((CdmaConnection) this.mConnections.get(size)).getState() == State.DISCONNECTED) {
                this.mConnections.remove(size);
            }
        }
        if (this.mConnections.size() == 0) {
            this.mState = State.IDLE;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public boolean connectionDisconnected(CdmaConnection cdmaConnection) {
        if (this.mState != State.DISCONNECTED) {
            Object obj;
            int size = this.mConnections.size();
            for (int i = 0; i < size; i++) {
                if (((Connection) this.mConnections.get(i)).getState() != State.DISCONNECTED) {
                    obj = null;
                    break;
                }
            }
            int obj2 = 1;
            if (obj2 != null) {
                this.mState = State.DISCONNECTED;
                return true;
            }
        }
        return false;
    }

    /* Access modifiers changed, original: 0000 */
    public void detach(CdmaConnection cdmaConnection) {
        this.mConnections.remove(cdmaConnection);
        if (this.mConnections.size() == 0) {
            this.mState = State.IDLE;
        }
    }

    public void dispose() {
    }

    public List<Connection> getConnections() {
        return this.mConnections;
    }

    public Phone getPhone() {
        return this.mOwner.mPhone;
    }

    public void hangup() throws CallStateException {
        this.mOwner.hangup(this);
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isFull() {
        return this.mConnections.size() == 1;
    }

    public boolean isMultiparty() {
        return this.mConnections.size() > 1;
    }

    /* Access modifiers changed, original: 0000 */
    public void onHangupLocal() {
        int size = this.mConnections.size();
        for (int i = 0; i < size; i++) {
            ((CdmaConnection) this.mConnections.get(i)).onHangupLocal();
        }
        this.mState = State.DISCONNECTING;
    }

    public String toString() {
        return this.mState.toString();
    }

    /* Access modifiers changed, original: 0000 */
    public boolean update(CdmaConnection cdmaConnection, DriverCall driverCall) {
        State stateFromDCState = Call.stateFromDCState(driverCall.state);
        if (stateFromDCState == this.mState) {
            return false;
        }
        this.mState = stateFromDCState;
        return true;
    }
}
