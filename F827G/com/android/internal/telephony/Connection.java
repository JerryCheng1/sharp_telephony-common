package com.android.internal.telephony;

import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telecom.ConferenceParticipant;
import android.telecom.Connection;
import android.telephony.Rlog;
import com.android.internal.telephony.Call;
import com.google.android.mms.pdu.CharacterSets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public abstract class Connection {
    public static final int AUDIO_QUALITY_HIGH_DEFINITION = 2;
    public static final int AUDIO_QUALITY_STANDARD = 1;
    private static String LOG_TAG = "Connection";
    protected String mAddress;
    private int mAudioQuality;
    private int mCallSubstate;
    protected String mCnapName;
    protected long mConnectTime;
    protected long mConnectTimeReal;
    protected String mConvertedNumber;
    protected long mCreateTime;
    protected String mDialString;
    protected long mDuration;
    protected long mHoldingStartTime;
    protected boolean mIsIncoming;
    private boolean mLocalVideoCapable;
    protected Connection mOrigConnection;
    private boolean mRemoteVideoCapable;
    Object mUserData;
    private Connection.VideoProvider mVideoProvider;
    private int mVideoState;
    protected int mCnapNamePresentation = 1;
    protected int mNumberPresentation = 1;
    private List<PostDialListener> mPostDialListeners = new ArrayList();
    public Set<Listener> mListeners = new CopyOnWriteArraySet();
    protected boolean mNumberConverted = false;
    boolean manualReject = false;
    public Call.State mPreHandoverState = Call.State.IDLE;
    protected Call.State state_before_disconnect = null;

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public interface Listener {
        void onAudioQualityChanged(int i);

        void onCallSubstateChanged(int i);

        void onConferenceParticipantsChanged(List<ConferenceParticipant> list);

        void onLocalVideoCapabilityChanged(boolean z);

        void onRemoteVideoCapabilityChanged(boolean z);

        void onVideoProviderChanged(Connection.VideoProvider videoProvider);

        void onVideoStateChanged(int i);
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public interface PostDialListener {
        void onPostDialChar(char c);

        void onPostDialWait();
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum PostDialState {
        NOT_STARTED,
        STARTED,
        WAIT,
        WAIT_EX,
        WILD,
        COMPLETE,
        CANCELLED,
        PAUSE
    }

    public abstract void cancelPostDial();

    public abstract Call getCall();

    public abstract int getDisconnectCause();

    public abstract long getDisconnectTime();

    public abstract long getHoldDurationMillis();

    public abstract int getNumberPresentation();

    public abstract PostDialState getPostDialState();

    public abstract int getPreciseDisconnectCause();

    public abstract String getRemainingPostDialString();

    public abstract UUSInfo getUUSInfo();

    public abstract void hangup() throws CallStateException;

    public abstract boolean isMultiparty();

    public abstract void proceedAfterWaitChar();

    public abstract void proceedAfterWildChar(String str);

    public abstract void separate() throws CallStateException;

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static abstract class ListenerBase implements Listener {
        @Override // com.android.internal.telephony.Connection.Listener
        public void onVideoStateChanged(int videoState) {
        }

        @Override // com.android.internal.telephony.Connection.Listener
        public void onLocalVideoCapabilityChanged(boolean capable) {
        }

        @Override // com.android.internal.telephony.Connection.Listener
        public void onRemoteVideoCapabilityChanged(boolean capable) {
        }

        @Override // com.android.internal.telephony.Connection.Listener
        public void onVideoProviderChanged(Connection.VideoProvider videoProvider) {
        }

        @Override // com.android.internal.telephony.Connection.Listener
        public void onAudioQualityChanged(int audioQuality) {
        }

        @Override // com.android.internal.telephony.Connection.Listener
        public void onCallSubstateChanged(int callSubstate) {
        }

        @Override // com.android.internal.telephony.Connection.Listener
        public void onConferenceParticipantsChanged(List<ConferenceParticipant> participants) {
        }
    }

    public String getAddress() {
        int startPos2;
        if (!TelBrand.IS_SBM || !isIncoming() || this.mAddress == null || !this.mAddress.startsWith("010")) {
            if (TelBrand.IS_DCM && this.mDialString != null) {
                Rlog.d(LOG_TAG, "getAddress(): dialString is " + this.mDialString + ", address is " + this.mAddress);
                int startPos = this.mDialString.lastIndexOf(38);
                if (startPos > 0) {
                    if (this.mAddress == null || (startPos2 = this.mAddress.lastIndexOf(38)) <= 0) {
                        String subAddress = this.mDialString.substring(startPos + 1);
                        Rlog.d(LOG_TAG, " subAdress is " + subAddress + ")");
                        return this.mAddress + CharacterSets.MIMENAME_ANY_CHARSET + subAddress;
                    }
                    String address_tmp = this.mAddress.substring(0, startPos2);
                    String subAddress2 = this.mDialString.substring(startPos + 1);
                    Rlog.d(LOG_TAG, "address_tmp is " + address_tmp + ", subAdress is " + subAddress2);
                    return address_tmp + CharacterSets.MIMENAME_ANY_CHARSET + subAddress2;
                }
            } else if (TelBrand.IS_KDDI && isIncoming() && this.mAddress != null && this.mAddress.startsWith("+81") && SystemProperties.getBoolean("gsm.domesticinservice", false)) {
                return this.mAddress.replace("+81", "0");
            }
            return this.mAddress;
        }
        Call c = getCall();
        if (c == null || c.getPhone().getPhoneType() != 1) {
            return "+" + this.mAddress.substring(3);
        }
        return this.mAddress;
    }

    public String getCnapName() {
        return this.mCnapName;
    }

    public String getOrigDialString() {
        return null;
    }

    public int getCnapNamePresentation() {
        return this.mCnapNamePresentation;
    }

    public long getCreateTime() {
        return this.mCreateTime;
    }

    public long getConnectTime() {
        return this.mConnectTime;
    }

    public void setConnectTime(long oldConnectTime) {
        this.mConnectTime = oldConnectTime;
    }

    public long getConnectTimeReal() {
        return this.mConnectTimeReal;
    }

    public long getDurationMillis() {
        if (this.mConnectTimeReal == 0) {
            return 0L;
        }
        if (this.mDuration == 0) {
            return SystemClock.elapsedRealtime() - this.mConnectTimeReal;
        }
        return this.mDuration;
    }

    public long getHoldingStartTime() {
        return this.mHoldingStartTime;
    }

    public boolean isIncoming() {
        return this.mIsIncoming;
    }

    public Call.State getCallStateBeforeDisconnect() {
        return this.state_before_disconnect;
    }

    public Call.State getState() {
        Call c = getCall();
        return c == null ? Call.State.IDLE : c.getState();
    }

    public Call.State getStateBeforeHandover() {
        return this.mPreHandoverState;
    }

    public Bundle getExtras() {
        Call c = getCall();
        if (c == null) {
            return null;
        }
        return c.getExtras();
    }

    public List<ConferenceParticipant> getConferenceParticipants() {
        Call c = getCall();
        if (c == null) {
            return null;
        }
        return c.getConferenceParticipants();
    }

    public boolean isAlive() {
        return getState().isAlive();
    }

    public boolean isRinging() {
        return getState().isRinging();
    }

    public Object getUserData() {
        return this.mUserData;
    }

    public void setUserData(Object userdata) {
        this.mUserData = userdata;
    }

    public void clearUserData() {
        this.mUserData = null;
    }

    public final void addPostDialListener(PostDialListener listener) {
        if (!this.mPostDialListeners.contains(listener)) {
            this.mPostDialListeners.add(listener);
        }
    }

    protected final void clearPostDialListeners() {
        this.mPostDialListeners.clear();
    }

    protected final void notifyPostDialListeners() {
        if (getPostDialState() == PostDialState.WAIT || getPostDialState() == PostDialState.WAIT_EX || getPostDialState() == PostDialState.PAUSE) {
            Iterator i$ = new ArrayList(this.mPostDialListeners).iterator();
            while (i$.hasNext()) {
                ((PostDialListener) i$.next()).onPostDialWait();
            }
        }
    }

    protected final void notifyPostDialListenersNextChar(char c) {
        Iterator i$ = new ArrayList(this.mPostDialListeners).iterator();
        while (i$.hasNext()) {
            PostDialListener postDialListener = (PostDialListener) i$.next();
        }
    }

    public boolean isMergeAllowed() {
        return true;
    }

    public Connection getOrigConnection() {
        return this.mOrigConnection;
    }

    public void migrateFrom(Connection c) {
        if (c != null) {
            this.mListeners = c.mListeners;
            this.mDialString = c.getOrigDialString();
            this.mCreateTime = c.getCreateTime();
            this.mConnectTime = c.getConnectTime();
            this.mConnectTimeReal = c.getConnectTimeReal();
            this.mHoldingStartTime = c.getHoldingStartTime();
            this.mOrigConnection = c.getOrigConnection();
        }
    }

    public final void addListener(Listener listener) {
        this.mListeners.add(listener);
    }

    public final void removeListener(Listener listener) {
        this.mListeners.remove(listener);
    }

    public int getVideoState() {
        return this.mVideoState;
    }

    public boolean isLocalVideoCapable() {
        return this.mLocalVideoCapable;
    }

    public boolean isRemoteVideoCapable() {
        return this.mRemoteVideoCapable;
    }

    public Connection.VideoProvider getVideoProvider() {
        return this.mVideoProvider;
    }

    public int getAudioQuality() {
        return this.mAudioQuality;
    }

    public int getCallSubstate() {
        return this.mCallSubstate;
    }

    public void setVideoState(int videoState) {
        this.mVideoState = videoState;
        for (Listener l : this.mListeners) {
            l.onVideoStateChanged(this.mVideoState);
        }
    }

    public void setLocalVideoCapable(boolean capable) {
        this.mLocalVideoCapable = capable;
        for (Listener l : this.mListeners) {
            l.onLocalVideoCapabilityChanged(this.mLocalVideoCapable);
        }
    }

    public void setRemoteVideoCapable(boolean capable) {
        this.mRemoteVideoCapable = capable;
        for (Listener l : this.mListeners) {
            l.onRemoteVideoCapabilityChanged(this.mRemoteVideoCapable);
        }
    }

    public void setAudioQuality(int audioQuality) {
        this.mAudioQuality = audioQuality;
        for (Listener l : this.mListeners) {
            l.onAudioQualityChanged(this.mAudioQuality);
        }
    }

    public void setCallSubstate(int callSubstate) {
        this.mCallSubstate = callSubstate;
        for (Listener l : this.mListeners) {
            l.onCallSubstateChanged(this.mCallSubstate);
        }
    }

    public void setVideoProvider(Connection.VideoProvider videoProvider) {
        this.mVideoProvider = videoProvider;
        for (Listener l : this.mListeners) {
            l.onVideoProviderChanged(this.mVideoProvider);
        }
    }

    public void setConverted(String oriNumber) {
        this.mNumberConverted = true;
        this.mConvertedNumber = this.mAddress;
        this.mAddress = oriNumber;
        this.mDialString = oriNumber;
    }

    public void updateConferenceParticipants(List<ConferenceParticipant> conferenceParticipants) {
        for (Listener l : this.mListeners) {
            l.onConferenceParticipantsChanged(conferenceParticipants);
        }
    }

    public void onDisconnectConferenceParticipant(Uri endpoint) {
    }

    public String toString() {
        StringBuilder str = new StringBuilder(128);
        if (Rlog.isLoggable(LOG_TAG, 3)) {
            str.append("addr: " + getAddress()).append(" pres.: " + getNumberPresentation()).append(" dial: " + getOrigDialString()).append(" postdial: " + getRemainingPostDialString()).append(" cnap name: " + getCnapName()).append("(" + getCnapNamePresentation() + ")");
        }
        str.append(" incoming: " + isIncoming()).append(" state: " + getState()).append(" post dial state: " + getPostDialState());
        return str.toString();
    }

    public String getNumber() {
        if (TelBrand.IS_DCM || TelBrand.IS_KDDI) {
            return this.mAddress;
        }
        return null;
    }

    public String getBeforeFowardingNumber() {
        return null;
    }
}
