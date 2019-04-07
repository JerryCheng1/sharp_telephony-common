package com.android.internal.telephony.sip;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.Connection;
import java.util.Iterator;
import java.util.List;

abstract class SipCallBase extends Call {
    SipCallBase() {
    }

    /* Access modifiers changed, original: 0000 */
    public void clearDisconnected() {
        Iterator it = this.mConnections.iterator();
        while (it.hasNext()) {
            if (((Connection) it.next()).getState() == State.DISCONNECTED) {
                it.remove();
            }
        }
        if (this.mConnections.isEmpty()) {
            setState(State.IDLE);
        }
    }

    public List<Connection> getConnections() {
        return this.mConnections;
    }

    public boolean isMultiparty() {
        return this.mConnections.size() > 1;
    }

    public abstract void setState(State state);

    public String toString() {
        return this.mState.toString() + ":" + super.toString();
    }
}
