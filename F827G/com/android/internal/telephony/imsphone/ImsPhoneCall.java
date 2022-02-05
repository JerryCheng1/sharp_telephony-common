package com.android.internal.telephony.imsphone;

import android.os.Bundle;
import android.telecom.ConferenceParticipant;
import android.telephony.Rlog;
import com.android.ims.ImsCall;
import com.android.ims.ImsCallProfile;
import com.android.ims.ImsException;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import java.util.Iterator;
import java.util.List;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class ImsPhoneCall extends Call {
    private static final boolean DBG = false;
    private static final String LOG_TAG = "ImsPhoneCall";
    ImsPhoneCallTracker mOwner;
    private boolean mRingbackTonePlayed = false;

    ImsPhoneCall() {
    }

    public ImsPhoneCall(ImsPhoneCallTracker owner) {
        this.mOwner = owner;
    }

    public void dispose() {
        try {
            this.mOwner.hangup(this);
            int s = this.mConnections.size();
            for (int i = 0; i < s; i++) {
                ((ImsPhoneConnection) this.mConnections.get(i)).onDisconnect(14);
            }
        } catch (CallStateException e) {
            int s2 = this.mConnections.size();
            for (int i2 = 0; i2 < s2; i2++) {
                ((ImsPhoneConnection) this.mConnections.get(i2)).onDisconnect(14);
            }
        } catch (Throwable th) {
            int s3 = this.mConnections.size();
            for (int i3 = 0; i3 < s3; i3++) {
                ((ImsPhoneConnection) this.mConnections.get(i3)).onDisconnect(14);
            }
            throw th;
        }
    }

    @Override // com.android.internal.telephony.Call
    public List<Connection> getConnections() {
        return this.mConnections;
    }

    @Override // com.android.internal.telephony.Call
    public Phone getPhone() {
        return this.mOwner.mPhone;
    }

    @Override // com.android.internal.telephony.Call
    public boolean isMultiparty() {
        ImsCall imsCall = getImsCall();
        if (imsCall == null) {
            return false;
        }
        return imsCall.isMultiparty();
    }

    @Override // com.android.internal.telephony.Call
    public void hangup() throws CallStateException {
        this.mOwner.hangup(this);
    }

    public String toString() {
        return this.mState.toString();
    }

    @Override // com.android.internal.telephony.Call
    public Bundle getExtras() {
        ImsCallProfile callProfile;
        Bundle imsCallExtras = null;
        ImsCall call = getImsCall();
        if (!(call == null || (callProfile = call.getCallProfile()) == null)) {
            imsCallExtras = callProfile.mCallExtras;
        }
        if (imsCallExtras == null) {
        }
        return imsCallExtras;
    }

    @Override // com.android.internal.telephony.Call
    public List<ConferenceParticipant> getConferenceParticipants() {
        ImsCall call = getImsCall();
        if (call == null) {
            return null;
        }
        return call.getConferenceParticipants();
    }

    public void attach(Connection conn) {
        clearDisconnected();
        this.mConnections.add(conn);
    }

    public void attach(Connection conn, Call.State state) {
        attach(conn);
        this.mState = state;
    }

    public void attachFake(Connection conn, Call.State state) {
        attach(conn, state);
    }

    public boolean connectionDisconnected(ImsPhoneConnection conn) {
        if (this.mState != Call.State.DISCONNECTED) {
            boolean hasOnlyDisconnectedConnections = true;
            int i = 0;
            int s = this.mConnections.size();
            while (true) {
                if (i >= s) {
                    break;
                } else if (((Connection) this.mConnections.get(i)).getState() != Call.State.DISCONNECTED) {
                    hasOnlyDisconnectedConnections = false;
                    break;
                } else {
                    i++;
                }
            }
            if (hasOnlyDisconnectedConnections) {
                this.mState = Call.State.DISCONNECTED;
                return true;
            }
        }
        return false;
    }

    public void detach(ImsPhoneConnection conn) {
        this.mConnections.remove(conn);
        clearDisconnected();
    }

    public boolean isFull() {
        return this.mConnections.size() == 5;
    }

    public void onHangupLocal() {
        int s = this.mConnections.size();
        for (int i = 0; i < s; i++) {
            ((ImsPhoneConnection) this.mConnections.get(i)).onHangupLocal();
        }
        this.mState = Call.State.DISCONNECTING;
    }

    public void clearDisconnected() {
        for (int i = this.mConnections.size() - 1; i >= 0; i--) {
            if (((ImsPhoneConnection) this.mConnections.get(i)).getState() == Call.State.DISCONNECTED) {
                this.mConnections.remove(i);
            }
        }
        if (this.mConnections.size() == 0) {
            this.mState = Call.State.IDLE;
        }
    }

    ImsPhoneConnection getFirstConnection() {
        if (this.mConnections.size() == 0) {
            return null;
        }
        for (int i = this.mConnections.size() - 1; i >= 0; i--) {
            if (((ImsPhoneConnection) this.mConnections.get(i)).getState().isAlive()) {
                return (ImsPhoneConnection) this.mConnections.get(i);
            }
        }
        return null;
    }

    public void setMute(boolean mute) {
        ImsCall imsCall = getFirstConnection() == null ? null : getFirstConnection().getImsCall();
        if (imsCall != null) {
            try {
                imsCall.setMute(mute);
            } catch (ImsException e) {
                Rlog.e(LOG_TAG, "setMute failed : " + e.getMessage());
            }
        }
    }

    public void merge(ImsPhoneCall that, Call.State state, long oldConnectTime) {
        if (getFirstConnection() != null) {
            getFirstConnection().setConnectTime(oldConnectTime);
        }
        ImsPhoneConnection[] cc = (ImsPhoneConnection[]) that.mConnections.toArray(new ImsPhoneConnection[that.mConnections.size()]);
        for (ImsPhoneConnection imsPhoneConnection : cc) {
        }
    }

    public ImsCall getImsCall() {
        if (getFirstConnection() == null) {
            return null;
        }
        return getFirstConnection().getImsCall();
    }

    public static boolean isLocalTone(ImsCall imsCall) {
        return (imsCall == null || imsCall.getCallProfile() == null || imsCall.getCallProfile().mMediaProfile == null || imsCall.getCallProfile().mMediaProfile.mAudioDirection != 0) ? false : true;
    }

    public boolean update(ImsPhoneConnection conn, ImsCall imsCall, Call.State state) {
        if (state == Call.State.ALERTING) {
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
        if (state != this.mState && state != Call.State.DISCONNECTED) {
            this.mState = state;
            return true;
        } else if (state == Call.State.DISCONNECTED) {
            return true;
        } else {
            return false;
        }
    }

    ImsPhoneConnection getHandoverConnection() {
        return (ImsPhoneConnection) getEarliestConnection();
    }

    public void switchWith(ImsPhoneCall that) {
        synchronized (ImsPhoneCall.class) {
            ImsPhoneCall tmp = new ImsPhoneCall();
            tmp.takeOver(this);
            takeOver(that);
            that.takeOver(tmp);
        }
    }

    private void takeOver(ImsPhoneCall that) {
        this.mConnections = that.mConnections;
        this.mState = that.mState;
        Iterator i$ = this.mConnections.iterator();
        while (i$.hasNext()) {
            ((ImsPhoneConnection) ((Connection) i$.next())).changeParent(this);
        }
    }
}
