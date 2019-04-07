package com.android.internal.telephony.imsphone;

import android.os.Bundle;
import android.telecom.ConferenceParticipant;
import android.telephony.Rlog;
import com.android.ims.ImsCall;
import com.android.ims.ImsCallProfile;
import com.android.ims.ImsException;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import java.util.Iterator;
import java.util.List;

public class ImsPhoneCall extends Call {
    private static final boolean DBG = false;
    private static final String LOG_TAG = "ImsPhoneCall";
    ImsPhoneCallTracker mOwner;
    private boolean mRingbackTonePlayed = false;

    ImsPhoneCall() {
    }

    ImsPhoneCall(ImsPhoneCallTracker imsPhoneCallTracker) {
        this.mOwner = imsPhoneCallTracker;
    }

    static boolean isLocalTone(ImsCall imsCall) {
        return (imsCall == null || imsCall.getCallProfile() == null || imsCall.getCallProfile().mMediaProfile == null || imsCall.getCallProfile().mMediaProfile.mAudioDirection != 0) ? false : true;
    }

    private void takeOver(ImsPhoneCall imsPhoneCall) {
        this.mConnections = imsPhoneCall.mConnections;
        this.mState = imsPhoneCall.mState;
        Iterator it = this.mConnections.iterator();
        while (it.hasNext()) {
            ((ImsPhoneConnection) ((Connection) it.next())).changeParent(this);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void attach(Connection connection) {
        clearDisconnected();
        this.mConnections.add(connection);
    }

    /* Access modifiers changed, original: 0000 */
    public void attach(Connection connection, State state) {
        attach(connection);
        this.mState = state;
    }

    /* Access modifiers changed, original: 0000 */
    public void attachFake(Connection connection, State state) {
        attach(connection, state);
    }

    /* Access modifiers changed, original: 0000 */
    public void clearDisconnected() {
        for (int size = this.mConnections.size() - 1; size >= 0; size--) {
            if (((ImsPhoneConnection) this.mConnections.get(size)).getState() == State.DISCONNECTED) {
                this.mConnections.remove(size);
            }
        }
        if (this.mConnections.size() == 0) {
            this.mState = State.IDLE;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public boolean connectionDisconnected(ImsPhoneConnection imsPhoneConnection) {
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
    public void detach(ImsPhoneConnection imsPhoneConnection) {
        this.mConnections.remove(imsPhoneConnection);
        clearDisconnected();
    }

    public void dispose() {
        int i = 0;
        int size;
        try {
            this.mOwner.hangup(this);
            size = this.mConnections.size();
            while (i < size) {
                ((ImsPhoneConnection) this.mConnections.get(i)).onDisconnect(14);
                i++;
            }
        } catch (CallStateException e) {
            size = this.mConnections.size();
            while (i < size) {
                ((ImsPhoneConnection) this.mConnections.get(i)).onDisconnect(14);
                i++;
            }
        } catch (Throwable th) {
            Throwable th2 = th;
            int size2 = this.mConnections.size();
            while (i < size2) {
                ((ImsPhoneConnection) this.mConnections.get(i)).onDisconnect(14);
                i++;
            }
        }
    }

    public List<ConferenceParticipant> getConferenceParticipants() {
        ImsCall imsCall = getImsCall();
        return imsCall == null ? null : imsCall.getConferenceParticipants();
    }

    public List<Connection> getConnections() {
        return this.mConnections;
    }

    public Bundle getExtras() {
        Bundle bundle = null;
        ImsCall imsCall = getImsCall();
        if (imsCall != null) {
            ImsCallProfile callProfile = imsCall.getCallProfile();
            if (callProfile != null) {
                bundle = callProfile.mCallExtras;
            }
        }
        if (bundle == null) {
        }
        return bundle;
    }

    /* Access modifiers changed, original: 0000 */
    public ImsPhoneConnection getFirstConnection() {
        if (this.mConnections.size() != 0) {
            for (int size = this.mConnections.size() - 1; size >= 0; size--) {
                if (((ImsPhoneConnection) this.mConnections.get(size)).getState().isAlive()) {
                    return (ImsPhoneConnection) this.mConnections.get(size);
                }
            }
        }
        return null;
    }

    /* Access modifiers changed, original: 0000 */
    public ImsPhoneConnection getHandoverConnection() {
        return (ImsPhoneConnection) getEarliestConnection();
    }

    public ImsCall getImsCall() {
        return getFirstConnection() == null ? null : getFirstConnection().getImsCall();
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
        ImsCall imsCall = getImsCall();
        return imsCall == null ? false : imsCall.isMultiparty();
    }

    /* Access modifiers changed, original: 0000 */
    public void merge(ImsPhoneCall imsPhoneCall, State state, long j) {
        if (getFirstConnection() != null) {
            getFirstConnection().setConnectTime(j);
        }
        for (ImsPhoneConnection imsPhoneConnection : (ImsPhoneConnection[]) imsPhoneCall.mConnections.toArray(new ImsPhoneConnection[imsPhoneCall.mConnections.size()])) {
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void onHangupLocal() {
        int size = this.mConnections.size();
        for (int i = 0; i < size; i++) {
            ((ImsPhoneConnection) this.mConnections.get(i)).onHangupLocal();
        }
        this.mState = State.DISCONNECTING;
    }

    /* Access modifiers changed, original: 0000 */
    public void setMute(boolean z) {
        ImsCall imsCall = getFirstConnection() == null ? null : getFirstConnection().getImsCall();
        if (imsCall != null) {
            try {
                imsCall.setMute(z);
            } catch (ImsException e) {
                Rlog.e(LOG_TAG, "setMute failed : " + e.getMessage());
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void switchWith(ImsPhoneCall imsPhoneCall) {
        synchronized (ImsPhoneCall.class) {
            try {
                ImsPhoneCall imsPhoneCall2 = new ImsPhoneCall();
                imsPhoneCall2.takeOver(this);
                takeOver(imsPhoneCall);
                imsPhoneCall.takeOver(imsPhoneCall2);
            } catch (Throwable th) {
                Class cls = ImsPhoneCall.class;
            }
        }
    }

    public String toString() {
        return this.mState.toString();
    }

    /* Access modifiers changed, original: 0000 */
    public boolean update(ImsPhoneConnection imsPhoneConnection, ImsCall imsCall, State state) {
        if (state == State.ALERTING) {
            if (this.mRingbackTonePlayed && !isLocalTone(imsCall)) {
                this.mOwner.mPhone.stopRingbackTone();
                this.mRingbackTonePlayed = false;
            } else if (!this.mRingbackTonePlayed && isLocalTone(imsCall)) {
                this.mOwner.mPhone.startRingbackTone();
                this.mRingbackTonePlayed = true;
            }
        } else if (this.mRingbackTonePlayed) {
            this.mOwner.mPhone.stopRingbackTone();
            this.mRingbackTonePlayed = false;
        }
        if (state == this.mState || state == State.DISCONNECTED) {
            return state == State.DISCONNECTED;
        } else {
            this.mState = state;
            return true;
        }
    }
}
