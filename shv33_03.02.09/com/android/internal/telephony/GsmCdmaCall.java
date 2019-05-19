package com.android.internal.telephony;

import android.telephony.Rlog;
import com.android.internal.telephony.Call.State;
import java.util.List;

public class GsmCdmaCall extends Call {
    protected static final String LOG_TAG = "GsmCdmaCall";
    GsmCdmaCallTracker mOwner;

    public GsmCdmaCall(GsmCdmaCallTracker owner) {
        this.mOwner = owner;
    }

    public List<Connection> getConnections() {
        return this.mConnections;
    }

    public Phone getPhone() {
        return this.mOwner.getPhone();
    }

    public boolean isMultiparty() {
        return this.mConnections.size() > 1;
    }

    public void hangup() throws CallStateException {
        this.mOwner.hangup(this);
    }

    public String toString() {
        return this.mState.toString();
    }

    public void attach(Connection conn, DriverCall dc) {
        this.mConnections.add(conn);
        this.mState = Call.stateFromDCState(dc.state);
    }

    public void attachFake(Connection conn, State state) {
        this.mConnections.add(conn);
        this.mState = state;
    }

    public boolean connectionDisconnected(GsmCdmaConnection conn) {
        if (this.mState != State.DISCONNECTED) {
            boolean hasOnlyDisconnectedConnections = true;
            int s = this.mConnections.size();
            for (int i = 0; i < s; i++) {
                if (((Connection) this.mConnections.get(i)).getState() != State.DISCONNECTED) {
                    hasOnlyDisconnectedConnections = false;
                    break;
                }
            }
            if (hasOnlyDisconnectedConnections) {
                this.mState = State.DISCONNECTED;
                return true;
            }
        }
        return false;
    }

    public void detach(GsmCdmaConnection conn) {
        this.mConnections.remove(conn);
        if (this.mConnections.size() == 0) {
            this.mState = State.IDLE;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public boolean update(GsmCdmaConnection conn, DriverCall dc) {
        State newState = Call.stateFromDCState(dc.state);
        if (newState == this.mState) {
            return false;
        }
        this.mState = newState;
        return true;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isFull() {
        return this.mConnections.size() == this.mOwner.getMaxConnectionsPerCall();
    }

    /* Access modifiers changed, original: 0000 */
    public void onHangupLocal() {
        int s = this.mConnections.size();
        for (int i = 0; i < s; i++) {
            ((GsmCdmaConnection) this.mConnections.get(i)).onHangupLocal();
        }
        this.mState = State.DISCONNECTING;
    }

    public void onDisconnecting() {
        if (isPhoneTypeGsm()) {
            if (TelBrand.IS_DCM) {
                this.mState = State.DISCONNECTING;
            }
            return;
        }
        Rlog.e(LOG_TAG, "UNEXPECTED : onDisconnecting is not used by CDMA.");
    }

    private boolean isPhoneTypeGsm() {
        return getPhone().getPhoneType() == 1;
    }

    public void clearDisconnected() {
        if (isPhoneTypeGsm()) {
            Rlog.d(LOG_TAG, "clearDisconnected() start...");
            for (int i = this.mConnections.size() - 1; i >= 0; i--) {
                if (((Connection) this.mConnections.get(i)).getState() == State.DISCONNECTED) {
                    synchronized (this.mConnections) {
                        this.mConnections.remove(i);
                    }
                }
            }
            if (this.mConnections.size() == 0) {
                setState(State.IDLE);
            }
            Rlog.d(LOG_TAG, "clearDisconnected() end...");
            return;
        }
        super.clearDisconnected();
    }
}
