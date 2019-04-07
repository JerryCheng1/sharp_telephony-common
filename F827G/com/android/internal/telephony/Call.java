package com.android.internal.telephony;

import android.os.Bundle;
import android.telecom.ConferenceParticipant;
import android.telephony.Rlog;
import java.util.ArrayList;
import java.util.List;

public abstract class Call {
    protected final String LOG_TAG = "Call";
    public ArrayList<Connection> mConnections = new ArrayList();
    protected boolean mIsGeneric = false;
    public State mState = State.IDLE;

    public enum SrvccState {
        NONE,
        STARTED,
        COMPLETED,
        FAILED,
        CANCELED
    }

    public enum State {
        IDLE,
        ACTIVE,
        HOLDING,
        DIALING,
        ALERTING,
        INCOMING,
        WAITING,
        DISCONNECTED,
        DISCONNECTING;

        public boolean isAlive() {
            return (this == IDLE || this == DISCONNECTED || this == DISCONNECTING) ? false : true;
        }

        public boolean isDialing() {
            return this == DIALING || this == ALERTING;
        }

        public boolean isRinging() {
            return this == INCOMING || this == WAITING;
        }
    }

    public static State stateFromDCState(com.android.internal.telephony.DriverCall.State state) {
        switch (state) {
            case ACTIVE:
                return State.ACTIVE;
            case HOLDING:
                return State.HOLDING;
            case DIALING:
                return State.DIALING;
            case ALERTING:
                return State.ALERTING;
            case INCOMING:
                return State.INCOMING;
            case WAITING:
                return State.WAITING;
            default:
                throw new RuntimeException("illegal call state:" + state);
        }
    }

    public List<ConferenceParticipant> getConferenceParticipants() {
        return null;
    }

    public abstract List<Connection> getConnections();

    public long getEarliestConnectTime() {
        long j = Long.MAX_VALUE;
        List connections = getConnections();
        if (connections.size() == 0) {
            return 0;
        }
        int size = connections.size();
        int i = 0;
        while (i < size) {
            long connectTime = ((Connection) connections.get(i)).getConnectTime();
            if (connectTime >= j) {
                connectTime = j;
            }
            i++;
            j = connectTime;
        }
        return j;
    }

    public Connection getEarliestConnection() {
        Connection connection = null;
        long j = Long.MAX_VALUE;
        List connections = getConnections();
        if (connections.size() != 0) {
            int size = connections.size();
            int i = 0;
            while (i < size) {
                Connection connection2 = (Connection) connections.get(i);
                long createTime = connection2.getCreateTime();
                if (createTime >= j) {
                    createTime = j;
                    connection2 = connection;
                }
                i++;
                j = createTime;
                connection = connection2;
            }
        }
        return connection;
    }

    public long getEarliestCreateTime() {
        long j = Long.MAX_VALUE;
        List connections = getConnections();
        if (connections.size() == 0) {
            return 0;
        }
        int size = connections.size();
        int i = 0;
        while (i < size) {
            long createTime = ((Connection) connections.get(i)).getCreateTime();
            if (createTime >= j) {
                createTime = j;
            }
            i++;
            j = createTime;
        }
        return j;
    }

    public Bundle getExtras() {
        return null;
    }

    public Connection getLatestConnection() {
        Connection connection = null;
        List connections = getConnections();
        if (connections.size() != 0) {
            long j = 0;
            int size = connections.size();
            int i = 0;
            while (i < size) {
                Connection connection2 = (Connection) connections.get(i);
                long createTime = connection2.getCreateTime();
                if (createTime <= j) {
                    createTime = j;
                    connection2 = connection;
                }
                i++;
                j = createTime;
                connection = connection2;
            }
        }
        return connection;
    }

    public abstract Phone getPhone();

    public State getState() {
        return this.mState;
    }

    public abstract void hangup() throws CallStateException;

    public void hangupIfAlive() {
        if (getState().isAlive()) {
            try {
                hangup();
            } catch (CallStateException e) {
                Rlog.w("Call", " hangupIfActive: caught " + e);
            }
        }
    }

    public boolean hasConnection(Connection connection) {
        return connection.getCall() == this;
    }

    public boolean hasConnections() {
        List connections = getConnections();
        return connections != null && connections.size() > 0;
    }

    public boolean isDialingOrAlerting() {
        return getState().isDialing();
    }

    public boolean isGeneric() {
        return this.mIsGeneric;
    }

    public boolean isIdle() {
        return !getState().isAlive();
    }

    public abstract boolean isMultiparty();

    public boolean isRinging() {
        return getState().isRinging();
    }

    public void setGeneric(boolean z) {
        this.mIsGeneric = z;
    }
}
