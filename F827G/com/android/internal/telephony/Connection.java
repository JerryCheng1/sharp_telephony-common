package com.android.internal.telephony;

import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telecom.ConferenceParticipant;
import android.telecom.Connection.VideoProvider;
import android.telephony.Rlog;
import com.android.internal.telephony.Call.State;
import com.google.android.mms.pdu.CharacterSets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class Connection {
    public static final int AUDIO_QUALITY_HIGH_DEFINITION = 2;
    public static final int AUDIO_QUALITY_STANDARD = 1;
    private static String LOG_TAG = "Connection";
    protected String mAddress;
    private int mAudioQuality;
    private int mCallSubstate;
    protected String mCnapName;
    protected int mCnapNamePresentation = 1;
    protected long mConnectTime;
    protected long mConnectTimeReal;
    protected String mConvertedNumber;
    protected long mCreateTime;
    protected String mDialString;
    protected long mDuration;
    protected long mHoldingStartTime;
    protected boolean mIsIncoming;
    public Set<Listener> mListeners = new CopyOnWriteArraySet();
    private boolean mLocalVideoCapable;
    protected boolean mNumberConverted = false;
    protected int mNumberPresentation = 1;
    protected Connection mOrigConnection;
    private List<PostDialListener> mPostDialListeners = new ArrayList();
    public State mPreHandoverState = State.IDLE;
    private boolean mRemoteVideoCapable;
    Object mUserData;
    private VideoProvider mVideoProvider;
    private int mVideoState;
    boolean manualReject = false;
    protected State state_before_disconnect = null;

    public interface Listener {
        void onAudioQualityChanged(int i);

        void onCallSubstateChanged(int i);

        void onConferenceParticipantsChanged(List<ConferenceParticipant> list);

        void onLocalVideoCapabilityChanged(boolean z);

        void onRemoteVideoCapabilityChanged(boolean z);

        void onVideoProviderChanged(VideoProvider videoProvider);

        void onVideoStateChanged(int i);
    }

    public static abstract class ListenerBase implements Listener {
        public void onAudioQualityChanged(int i) {
        }

        public void onCallSubstateChanged(int i) {
        }

        public void onConferenceParticipantsChanged(List<ConferenceParticipant> list) {
        }

        public void onLocalVideoCapabilityChanged(boolean z) {
        }

        public void onRemoteVideoCapabilityChanged(boolean z) {
        }

        public void onVideoProviderChanged(VideoProvider videoProvider) {
        }

        public void onVideoStateChanged(int i) {
        }
    }

    public interface PostDialListener {
        void onPostDialChar(char c);

        void onPostDialWait();
    }

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

    public final void addListener(Listener listener) {
        this.mListeners.add(listener);
    }

    public final void addPostDialListener(PostDialListener postDialListener) {
        if (!this.mPostDialListeners.contains(postDialListener)) {
            this.mPostDialListeners.add(postDialListener);
        }
    }

    public abstract void cancelPostDial();

    /* Access modifiers changed, original: protected|final */
    public final void clearPostDialListeners() {
        this.mPostDialListeners.clear();
    }

    public void clearUserData() {
        this.mUserData = null;
    }

    public String getAddress() {
        if (TelBrand.IS_SBM && isIncoming() && this.mAddress != null && this.mAddress.startsWith("010")) {
            Call call = getCall();
            return (call == null || call.getPhone().getPhoneType() != 1) ? "+" + this.mAddress.substring(3) : this.mAddress;
        } else {
            if (TelBrand.IS_DCM && this.mDialString != null) {
                Rlog.d(LOG_TAG, "getAddress(): dialString is " + this.mDialString + ", address is " + this.mAddress);
                int lastIndexOf = this.mDialString.lastIndexOf(38);
                if (lastIndexOf > 0) {
                    String substring;
                    if (this.mAddress != null) {
                        int lastIndexOf2 = this.mAddress.lastIndexOf(38);
                        if (lastIndexOf2 > 0) {
                            String substring2 = this.mAddress.substring(0, lastIndexOf2);
                            substring = this.mDialString.substring(lastIndexOf + 1);
                            Rlog.d(LOG_TAG, "address_tmp is " + substring2 + ", subAdress is " + substring);
                            return substring2 + CharacterSets.MIMENAME_ANY_CHARSET + substring;
                        }
                    }
                    substring = this.mDialString.substring(lastIndexOf + 1);
                    Rlog.d(LOG_TAG, " subAdress is " + substring + ")");
                    return this.mAddress + CharacterSets.MIMENAME_ANY_CHARSET + substring;
                }
            } else if (TelBrand.IS_KDDI && isIncoming() && this.mAddress != null && this.mAddress.startsWith("+81") && SystemProperties.getBoolean("gsm.domesticinservice", false)) {
                return this.mAddress.replace("+81", "0");
            }
            return this.mAddress;
        }
    }

    public int getAudioQuality() {
        return this.mAudioQuality;
    }

    public String getBeforeFowardingNumber() {
        return null;
    }

    public abstract Call getCall();

    public State getCallStateBeforeDisconnect() {
        return this.state_before_disconnect;
    }

    public int getCallSubstate() {
        return this.mCallSubstate;
    }

    public String getCnapName() {
        return this.mCnapName;
    }

    public int getCnapNamePresentation() {
        return this.mCnapNamePresentation;
    }

    public List<ConferenceParticipant> getConferenceParticipants() {
        Call call = getCall();
        return call == null ? null : call.getConferenceParticipants();
    }

    public long getConnectTime() {
        return this.mConnectTime;
    }

    public long getConnectTimeReal() {
        return this.mConnectTimeReal;
    }

    public long getCreateTime() {
        return this.mCreateTime;
    }

    public abstract int getDisconnectCause();

    public abstract long getDisconnectTime();

    public long getDurationMillis() {
        return this.mConnectTimeReal == 0 ? 0 : this.mDuration == 0 ? SystemClock.elapsedRealtime() - this.mConnectTimeReal : this.mDuration;
    }

    public Bundle getExtras() {
        Call call = getCall();
        return call == null ? null : call.getExtras();
    }

    public abstract long getHoldDurationMillis();

    public long getHoldingStartTime() {
        return this.mHoldingStartTime;
    }

    public String getNumber() {
        return (TelBrand.IS_DCM || TelBrand.IS_KDDI) ? this.mAddress : null;
    }

    public abstract int getNumberPresentation();

    public Connection getOrigConnection() {
        return this.mOrigConnection;
    }

    public String getOrigDialString() {
        return null;
    }

    public abstract PostDialState getPostDialState();

    public abstract int getPreciseDisconnectCause();

    public abstract String getRemainingPostDialString();

    public State getState() {
        Call call = getCall();
        return call == null ? State.IDLE : call.getState();
    }

    public State getStateBeforeHandover() {
        return this.mPreHandoverState;
    }

    public abstract UUSInfo getUUSInfo();

    public Object getUserData() {
        return this.mUserData;
    }

    public VideoProvider getVideoProvider() {
        return this.mVideoProvider;
    }

    public int getVideoState() {
        return this.mVideoState;
    }

    public abstract void hangup() throws CallStateException;

    public boolean isAlive() {
        return getState().isAlive();
    }

    public boolean isIncoming() {
        return this.mIsIncoming;
    }

    public boolean isLocalVideoCapable() {
        return this.mLocalVideoCapable;
    }

    public boolean isMergeAllowed() {
        return true;
    }

    public abstract boolean isMultiparty();

    public boolean isRemoteVideoCapable() {
        return this.mRemoteVideoCapable;
    }

    public boolean isRinging() {
        return getState().isRinging();
    }

    public void migrateFrom(Connection connection) {
        if (connection != null) {
            this.mListeners = connection.mListeners;
            this.mDialString = connection.getOrigDialString();
            this.mCreateTime = connection.getCreateTime();
            this.mConnectTime = connection.getConnectTime();
            this.mConnectTimeReal = connection.getConnectTimeReal();
            this.mHoldingStartTime = connection.getHoldingStartTime();
            this.mOrigConnection = connection.getOrigConnection();
        }
    }

    /* Access modifiers changed, original: protected|final */
    public final void notifyPostDialListeners() {
        if (getPostDialState() == PostDialState.WAIT || getPostDialState() == PostDialState.WAIT_EX || getPostDialState() == PostDialState.PAUSE) {
            Iterator it = new ArrayList(this.mPostDialListeners).iterator();
            while (it.hasNext()) {
                ((PostDialListener) it.next()).onPostDialWait();
            }
        }
    }

    /* Access modifiers changed, original: protected|final */
    public final void notifyPostDialListenersNextChar(char c) {
        Iterator it = new ArrayList(this.mPostDialListeners).iterator();
        while (it.hasNext()) {
            PostDialListener postDialListener = (PostDialListener) it.next();
        }
    }

    public void onDisconnectConferenceParticipant(Uri uri) {
    }

    public abstract void proceedAfterWaitChar();

    public abstract void proceedAfterWildChar(String str);

    public final void removeListener(Listener listener) {
        this.mListeners.remove(listener);
    }

    public abstract void separate() throws CallStateException;

    public void setAudioQuality(int i) {
        this.mAudioQuality = i;
        for (Listener onAudioQualityChanged : this.mListeners) {
            onAudioQualityChanged.onAudioQualityChanged(this.mAudioQuality);
        }
    }

    public void setCallSubstate(int i) {
        this.mCallSubstate = i;
        for (Listener onCallSubstateChanged : this.mListeners) {
            onCallSubstateChanged.onCallSubstateChanged(this.mCallSubstate);
        }
    }

    public void setConnectTime(long j) {
        this.mConnectTime = j;
    }

    public void setConverted(String str) {
        this.mNumberConverted = true;
        this.mConvertedNumber = this.mAddress;
        this.mAddress = str;
        this.mDialString = str;
    }

    public void setLocalVideoCapable(boolean z) {
        this.mLocalVideoCapable = z;
        for (Listener onLocalVideoCapabilityChanged : this.mListeners) {
            onLocalVideoCapabilityChanged.onLocalVideoCapabilityChanged(this.mLocalVideoCapable);
        }
    }

    public void setRemoteVideoCapable(boolean z) {
        this.mRemoteVideoCapable = z;
        for (Listener onRemoteVideoCapabilityChanged : this.mListeners) {
            onRemoteVideoCapabilityChanged.onRemoteVideoCapabilityChanged(this.mRemoteVideoCapable);
        }
    }

    public void setUserData(Object obj) {
        this.mUserData = obj;
    }

    public void setVideoProvider(VideoProvider videoProvider) {
        this.mVideoProvider = videoProvider;
        for (Listener onVideoProviderChanged : this.mListeners) {
            onVideoProviderChanged.onVideoProviderChanged(this.mVideoProvider);
        }
    }

    public void setVideoState(int i) {
        this.mVideoState = i;
        for (Listener onVideoStateChanged : this.mListeners) {
            onVideoStateChanged.onVideoStateChanged(this.mVideoState);
        }
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder(128);
        if (Rlog.isLoggable(LOG_TAG, 3)) {
            stringBuilder.append("addr: " + getAddress()).append(" pres.: " + getNumberPresentation()).append(" dial: " + getOrigDialString()).append(" postdial: " + getRemainingPostDialString()).append(" cnap name: " + getCnapName()).append("(" + getCnapNamePresentation() + ")");
        }
        stringBuilder.append(" incoming: " + isIncoming()).append(" state: " + getState()).append(" post dial state: " + getPostDialState());
        return stringBuilder.toString();
    }

    public void updateConferenceParticipants(List<ConferenceParticipant> list) {
        for (Listener onConferenceParticipantsChanged : this.mListeners) {
            onConferenceParticipantsChanged.onConferenceParticipantsChanged(list);
        }
    }
}
