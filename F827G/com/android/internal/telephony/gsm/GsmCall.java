package com.android.internal.telephony.gsm;

import android.telephony.Rlog;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelBrand;
import java.util.List;

class GsmCall extends Call {
    protected static final String LOG_TAG = "GsmCall";
    GsmCallTracker mOwner;

    GsmCall(GsmCallTracker gsmCallTracker) {
        this.mOwner = gsmCallTracker;
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
        Rlog.d(LOG_TAG, "clearDisconnected() start...");
        for (int size = this.mConnections.size() - 1; size >= 0; size--) {
            if (((GsmConnection) this.mConnections.get(size)).getState() == State.DISCONNECTED) {
                synchronized (this.mConnections) {
                    this.mConnections.remove(size);
                }
            }
        }
        if (this.mConnections.size() == 0) {
            this.mState = State.IDLE;
        }
        Rlog.d(LOG_TAG, "clearDisconnected() end...");
    }

    /* Access modifiers changed, original: 0000 */
    public boolean connectionDisconnected(GsmConnection gsmConnection) {
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
    public void detach(GsmConnection gsmConnection) {
        this.mConnections.remove(gsmConnection);
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
        return this.mConnections.size() == 5;
    }

    public boolean isMultiparty() {
        return this.mConnections.size() > 1;
    }

    public void onDisconnecting() {
        if (TelBrand.IS_DCM) {
            this.mState = State.DISCONNECTING;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void onHangupLocal() {
        int size = this.mConnections.size();
        for (int i = 0; i < size; i++) {
            ((GsmConnection) this.mConnections.get(i)).onHangupLocal();
        }
        this.mState = State.DISCONNECTING;
    }

    public String toString() {
        return this.mState.toString();
    }

    /* Access modifiers changed, original: 0000 */
    public boolean update(GsmConnection gsmConnection, DriverCall driverCall) {
        State stateFromDCState = Call.stateFromDCState(driverCall.state);
        if (stateFromDCState == this.mState) {
            return false;
        }
        this.mState = stateFromDCState;
        return true;
    }
}
