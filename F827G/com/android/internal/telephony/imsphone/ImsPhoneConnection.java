package com.android.internal.telephony.imsphone;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telecom.Log;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.ims.ImsCall;
import com.android.ims.ImsCallProfile;
import com.android.ims.ImsException;
import com.android.ims.ImsStreamMediaProfile;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.UUSInfo;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class ImsPhoneConnection extends Connection {
    private static final boolean DBG = true;
    private static final int EVENT_DTMF_DONE = 1;
    private static final int EVENT_NEXT_POST_DIAL = 3;
    private static final int EVENT_PAUSE_DONE = 2;
    private static final int EVENT_WAKE_LOCK_TIMEOUT = 4;
    private static final String LOG_TAG = "ImsPhoneConnection";
    private static final int PAUSE_DELAY_MILLIS = 3000;
    private static final String PROPERTY_ENABLE_RESTRICT_NON_OWNER_MERGE = "persist.radio.restrict_merge";
    private static final int WAKE_LOCK_TIMEOUT_MILLIS = 60000;
    private Bundle mCallExtras;
    private int mCause;
    private Context mContext;
    private long mDisconnectTime;
    private boolean mDisconnected;
    private Handler mHandler;
    private ImsCall mImsCall;
    private boolean mIsConferenceUri;
    private boolean mMptyState;
    private int mNextPostDialChar;
    private ImsPhoneCallTracker mOwner;
    private ImsPhoneCall mParent;
    private PowerManager.WakeLock mPartialWakeLock;
    private Connection.PostDialState mPostDialState;
    private String mPostDialString;
    private UUSInfo mUusInfo;
    String redirectingNum;

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class MyHandler extends Handler {
        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
        MyHandler(Looper l) {
            super(l);
            ImsPhoneConnection.this = r1;
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                case 2:
                case 3:
                    ImsPhoneConnection.this.processNextPostDialChar();
                    return;
                case 4:
                    ImsPhoneConnection.this.releaseWakeLock();
                    return;
                default:
                    return;
            }
        }
    }

    public ImsPhoneConnection(Context context, ImsCall imsCall, ImsPhoneCallTracker ct, ImsPhoneCall parent, boolean isUnknown, Call.State state, String address) {
        this.mCallExtras = null;
        this.mMptyState = false;
        this.mIsConferenceUri = false;
        this.mCause = 0;
        this.mPostDialState = Connection.PostDialState.NOT_STARTED;
        createWakeLock(context);
        acquireWakeLock();
        this.mContext = context;
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mImsCall = imsCall;
        if (imsCall == null || imsCall.getCallProfile() == null) {
            this.mNumberPresentation = 3;
            this.mCnapNamePresentation = 3;
        } else {
            this.mAddress = imsCall.getCallProfile().getCallExtra("oi");
            if (TelBrand.IS_DCM) {
                if (TextUtils.isEmpty(this.mAddress)) {
                    this.redirectingNum = null;
                } else if (this.mAddress.indexOf(38) == -1) {
                    this.redirectingNum = null;
                } else {
                    String[] a = this.mAddress.split("&");
                    this.mAddress = a[0];
                    this.redirectingNum = a[1];
                    Rlog.d(LOG_TAG, "ImsConnection: address is " + this.mAddress);
                    Rlog.d(LOG_TAG, "ImsConnection: redirectingNum is " + this.redirectingNum);
                }
            }
            this.mCnapName = imsCall.getCallProfile().getCallExtra("cna");
            this.mNumberPresentation = ImsCallProfile.OIRToPresentation(imsCall.getCallProfile().getCallExtraInt("oir"));
            this.mCnapNamePresentation = ImsCallProfile.OIRToPresentation(imsCall.getCallProfile().getCallExtraInt("cnap"));
            ImsCallProfile imsCallProfile = imsCall.getCallProfile();
            if (imsCallProfile != null) {
                setVideoState(ImsCallProfile.getVideoStateFromImsCallProfile(imsCallProfile));
            }
            try {
                ImsCallProfile localCallProfile = imsCall.getLocalCallProfile();
                if (localCallProfile != null) {
                    setLocalVideoCapable(localCallProfile.mCallType == 4);
                }
                ImsCallProfile remoteCallProfile = imsCall.getRemoteCallProfile();
                if (remoteCallProfile != null) {
                    setRemoteVideoCapable(remoteCallProfile.mCallType == 4);
                }
                if (!(localCallProfile == null || remoteCallProfile == null)) {
                    setAudioQuality(getAudioQualityFromCallProfile(localCallProfile, remoteCallProfile));
                }
            } catch (ImsException e) {
            }
        }
        this.mCreateTime = System.currentTimeMillis();
        this.mUusInfo = null;
        this.mParent = parent;
        this.mIsIncoming = !isUnknown;
        if (isUnknown) {
            this.mParent.attach(this, state);
            this.mAddress = address;
            this.mCnapName = address;
            this.mCnapNamePresentation = 1;
            this.mNumberPresentation = 1;
            return;
        }
        this.mParent.attach(this, this.mIsIncoming ? Call.State.INCOMING : Call.State.DIALING);
    }

    public ImsPhoneConnection(Context context, String dialString, ImsPhoneCallTracker ct, ImsPhoneCall parent, Bundle extras) {
        this.mCallExtras = null;
        this.mMptyState = false;
        this.mIsConferenceUri = false;
        this.mCause = 0;
        this.mPostDialState = Connection.PostDialState.NOT_STARTED;
        createWakeLock(context);
        acquireWakeLock();
        boolean isConferenceUri = false;
        boolean isSkipSchemaParsing = false;
        if (extras != null) {
            isConferenceUri = extras.getBoolean("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
            isSkipSchemaParsing = extras.getBoolean("org.codeaurora.extra.SKIP_SCHEMA_PARSING", false);
        }
        this.mContext = context;
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mIsConferenceUri = isConferenceUri;
        this.mDialString = dialString;
        if (isConferenceUri || isSkipSchemaParsing) {
            this.mAddress = dialString;
            this.mPostDialString = "";
        } else {
            this.mAddress = PhoneNumberUtils.extractNetworkPortionAlt(dialString);
            this.mPostDialString = PhoneNumberUtils.extractPostDialPortion(dialString);
        }
        this.mIsIncoming = false;
        this.mCnapName = null;
        this.mCnapNamePresentation = 1;
        this.mNumberPresentation = 1;
        this.mCreateTime = System.currentTimeMillis();
        if (extras != null) {
            this.mCallExtras = extras;
        }
        this.mParent = parent;
        parent.attachFake(this, Call.State.DIALING);
    }

    public void dispose() {
    }

    static boolean equalsHandlesNulls(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private int getAudioQualityFromMediaProfile(ImsStreamMediaProfile mediaProfile) {
        if (mediaProfile.mAudioQuality == 2) {
            return 2;
        }
        return 1;
    }

    @Override // com.android.internal.telephony.Connection
    public String getOrigDialString() {
        return this.mDialString;
    }

    @Override // com.android.internal.telephony.Connection
    public ImsPhoneCall getCall() {
        return this.mParent;
    }

    @Override // com.android.internal.telephony.Connection
    public long getDisconnectTime() {
        return this.mDisconnectTime;
    }

    @Override // com.android.internal.telephony.Connection
    public long getHoldingStartTime() {
        return this.mHoldingStartTime;
    }

    @Override // com.android.internal.telephony.Connection
    public long getHoldDurationMillis() {
        if (getState() != Call.State.HOLDING) {
            return 0L;
        }
        return SystemClock.elapsedRealtime() - this.mHoldingStartTime;
    }

    @Override // com.android.internal.telephony.Connection
    public int getDisconnectCause() {
        return this.mCause;
    }

    public void setDisconnectCause(int cause) {
        this.mCause = cause;
    }

    public ImsPhoneCallTracker getOwner() {
        return this.mOwner;
    }

    @Override // com.android.internal.telephony.Connection
    public Call.State getState() {
        return this.mDisconnected ? Call.State.DISCONNECTED : super.getState();
    }

    @Override // com.android.internal.telephony.Connection
    public void hangup() throws CallStateException {
        if (!this.mDisconnected) {
            this.mOwner.hangup(this);
            return;
        }
        throw new CallStateException("disconnected");
    }

    @Override // com.android.internal.telephony.Connection
    public void separate() throws CallStateException {
        throw new CallStateException("not supported");
    }

    @Override // com.android.internal.telephony.Connection
    public Connection.PostDialState getPostDialState() {
        return this.mPostDialState;
    }

    @Override // com.android.internal.telephony.Connection
    public void proceedAfterWaitChar() {
        if (this.mPostDialState == Connection.PostDialState.WAIT || this.mPostDialState == Connection.PostDialState.WAIT_EX) {
            setPostDialState(Connection.PostDialState.STARTED);
            processNextPostDialChar();
            return;
        }
        Rlog.w(LOG_TAG, "ImsPhoneConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WAIT but was " + this.mPostDialState);
    }

    @Override // com.android.internal.telephony.Connection
    public void proceedAfterWildChar(String str) {
        if (this.mPostDialState != Connection.PostDialState.WILD) {
            Rlog.w(LOG_TAG, "ImsPhoneConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WILD but was " + this.mPostDialState);
            return;
        }
        setPostDialState(Connection.PostDialState.STARTED);
        this.mPostDialString = str + this.mPostDialString.substring(this.mNextPostDialChar);
        this.mNextPostDialChar = 0;
        Rlog.d(LOG_TAG, new StringBuilder().append("proceedAfterWildChar: new postDialString is ").append(this.mPostDialString).toString());
        processNextPostDialChar();
    }

    @Override // com.android.internal.telephony.Connection
    public void cancelPostDial() {
        setPostDialState(Connection.PostDialState.CANCELLED);
    }

    public void onHangupLocal() {
        this.mCause = 3;
    }

    public boolean onDisconnect(int cause) {
        Rlog.d(LOG_TAG, "onDisconnect: cause=" + cause);
        if (this.mCause != 3) {
            this.mCause = cause;
        }
        return onDisconnect();
    }

    public boolean onDisconnect() {
        boolean changed = false;
        if (!this.mDisconnected) {
            this.mDisconnectTime = System.currentTimeMillis();
            this.mDuration = SystemClock.elapsedRealtime() - this.mConnectTimeReal;
            this.mDisconnected = true;
            this.mOwner.mPhone.notifyDisconnect(this);
            if (this.mParent != null) {
                changed = this.mParent.connectionDisconnected(this);
            } else {
                Rlog.d(LOG_TAG, "onDisconnect: no parent");
            }
            if (this.mImsCall != null) {
                this.mImsCall.close();
            }
            this.mImsCall = null;
        }
        clearPostDialListeners();
        releaseWakeLock();
        return changed;
    }

    void onConnectedInOrOut() {
        this.mConnectTime = System.currentTimeMillis();
        this.mConnectTimeReal = SystemClock.elapsedRealtime();
        this.mDuration = 0L;
        Rlog.d(LOG_TAG, "onConnectedInOrOut: connectTime=" + this.mConnectTime);
        if (!this.mIsIncoming) {
            processNextPostDialChar();
        }
        releaseWakeLock();
    }

    void onStartedHolding() {
        this.mHoldingStartTime = SystemClock.elapsedRealtime();
    }

    private boolean processPostDialChar(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            if (this.mOwner == null) {
                return true;
            }
            this.mOwner.sendDtmf(c, this.mHandler.obtainMessage(1));
            return true;
        } else if (c == ',') {
            if (!TelBrand.IS_DCM) {
                setPostDialState(Connection.PostDialState.PAUSE);
            }
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(2), 3000L);
            return true;
        } else if (c == ';') {
            setPostDialState(Connection.PostDialState.WAIT);
            return true;
        } else if (c == 'P') {
            setPostDialState(Connection.PostDialState.WAIT_EX);
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(2), 3000L);
            return true;
        } else if (c != 'N') {
            return false;
        } else {
            setPostDialState(Connection.PostDialState.WILD);
            return true;
        }
    }

    @Override // com.android.internal.telephony.Connection
    public String getRemainingPostDialString() {
        if (this.mPostDialState == Connection.PostDialState.CANCELLED || this.mPostDialState == Connection.PostDialState.COMPLETE || this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
            return "";
        }
        String subStr = this.mPostDialString.substring(this.mNextPostDialChar);
        if (subStr != null) {
            int wIndex = subStr.indexOf(59);
            int pIndex = subStr.indexOf(44);
            int wexIndex = subStr.indexOf(80);
            if (wIndex > 0 && ((wIndex < pIndex || pIndex <= 0) && (wIndex < wexIndex || wexIndex <= 0))) {
                subStr = subStr.substring(0, wIndex);
            } else if (wexIndex > 0 && ((wexIndex < pIndex || pIndex <= 0) && (wexIndex < wIndex || wIndex <= 0))) {
                subStr = subStr.substring(0, wexIndex);
            } else if (pIndex > 0) {
                subStr = subStr.substring(0, pIndex);
            }
            Rlog.w(LOG_TAG, "getRemainingPostDialString() wIndex(" + wIndex + "), pIndex(" + pIndex + "), wexIndex(" + wexIndex + ")");
        }
        Rlog.w(LOG_TAG, "getRemainingPostDialString() end, return String(" + subStr + ")");
        return subStr;
    }

    public void finalize() {
        clearPostDialListeners();
        releaseWakeLock();
    }

    public void processNextPostDialChar() {
        char c;
        Message notifyMessage;
        if (this.mPostDialState != Connection.PostDialState.CANCELLED) {
            if (this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
                setPostDialState(Connection.PostDialState.COMPLETE);
                c = 0;
            } else {
                setPostDialState(Connection.PostDialState.STARTED);
                String str = this.mPostDialString;
                int i = this.mNextPostDialChar;
                this.mNextPostDialChar = i + 1;
                c = str.charAt(i);
                if (!processPostDialChar(c)) {
                    this.mHandler.obtainMessage(3).sendToTarget();
                    Rlog.e(LOG_TAG, "processNextPostDialChar: c=" + c + " isn't valid!");
                    return;
                }
            }
            notifyPostDialListenersNextChar(c);
            Registrant postDialHandler = this.mOwner.mPhone.mPostDialHandler;
            if (postDialHandler != null && (notifyMessage = postDialHandler.messageForRegistrant()) != null) {
                Connection.PostDialState state = this.mPostDialState;
                AsyncResult ar = AsyncResult.forMessage(notifyMessage);
                ar.result = this;
                ar.userObj = state;
                notifyMessage.arg1 = c;
                notifyMessage.sendToTarget();
            }
        }
    }

    private void setPostDialState(Connection.PostDialState s) {
        if (this.mPostDialState != Connection.PostDialState.STARTED && s == Connection.PostDialState.STARTED) {
            acquireWakeLock();
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(4), 60000L);
        } else if (this.mPostDialState == Connection.PostDialState.STARTED && s != Connection.PostDialState.STARTED) {
            this.mHandler.removeMessages(4);
            releaseWakeLock();
        }
        this.mPostDialState = s;
        notifyPostDialListeners();
    }

    private void createWakeLock(Context context) {
        this.mPartialWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, LOG_TAG);
    }

    private void acquireWakeLock() {
        Rlog.d(LOG_TAG, "acquireWakeLock");
        this.mPartialWakeLock.acquire();
    }

    public void releaseWakeLock() {
        synchronized (this.mPartialWakeLock) {
            if (this.mPartialWakeLock.isHeld()) {
                Rlog.d(LOG_TAG, "releaseWakeLock");
                this.mPartialWakeLock.release();
            }
        }
    }

    @Override // com.android.internal.telephony.Connection
    public int getNumberPresentation() {
        return this.mNumberPresentation;
    }

    @Override // com.android.internal.telephony.Connection
    public UUSInfo getUUSInfo() {
        return this.mUusInfo;
    }

    @Override // com.android.internal.telephony.Connection
    public Connection getOrigConnection() {
        return null;
    }

    @Override // com.android.internal.telephony.Connection
    public boolean isMultiparty() {
        return this.mImsCall != null && this.mImsCall.isMultiparty();
    }

    @Override // com.android.internal.telephony.Connection
    public boolean isMergeAllowed() {
        if (restrictedMergeFeatureEnabled() && this.mImsCall != null && this.mImsCall.isMultiparty() && this.mImsCall.getCallGroup() == null && !this.mIsConferenceUri) {
            return false;
        }
        return true;
    }

    public ImsCall getImsCall() {
        return this.mImsCall;
    }

    public void setImsCall(ImsCall imsCall) {
        this.mImsCall = imsCall;
    }

    public void changeParent(ImsPhoneCall parent) {
        this.mParent = parent;
    }

    public boolean update(ImsCall imsCall, Call.State state) {
        if (state == Call.State.ACTIVE) {
            if (!TelBrand.IS_DCM) {
                if (this.mParent.getState().isRinging() || this.mParent.getState().isDialing()) {
                    onConnectedInOrOut();
                }
            } else if (this.mParent.getState().isRinging() || this.mParent.getState().isDialing() || (this.mParent == this.mOwner.mRingingCall && this.mParent.getState() == Call.State.DISCONNECTING)) {
                onConnectedInOrOut();
            }
            if (!TelBrand.IS_DCM) {
                if (this.mParent.getState().isRinging() || this.mParent == this.mOwner.mBackgroundCall) {
                    this.mParent.detach(this);
                    this.mParent = this.mOwner.mForegroundCall;
                    this.mParent.attach(this);
                }
            } else if (this.mParent.getState().isRinging() || this.mParent == this.mOwner.mBackgroundCall || (this.mParent == this.mOwner.mRingingCall && this.mParent.getState() == Call.State.DISCONNECTING)) {
                this.mParent.detach(this);
                this.mParent = this.mOwner.mForegroundCall;
                this.mParent.attach(this);
            }
        } else if (state == Call.State.HOLDING) {
            onStartedHolding();
        }
        return update(imsCall) || this.mParent.update(this, imsCall, state);
    }

    public boolean update(ImsCall imsCall) {
        int newAudioQuality;
        boolean changed = false;
        if (imsCall == null) {
            return false;
        }
        try {
            ImsCallProfile localCallProfile = imsCall.getLocalCallProfile();
            Rlog.d(LOG_TAG, " update localCallProfile=" + localCallProfile + "isLocalVideoCapable()= " + isLocalVideoCapable());
            if (localCallProfile != null) {
                boolean newLocalVideoCapable = localCallProfile.mCallType == 4;
                if (isLocalVideoCapable() != newLocalVideoCapable) {
                    setLocalVideoCapable(newLocalVideoCapable);
                    changed = true;
                }
            }
            ImsCallProfile remoteCallProfile = imsCall.getRemoteCallProfile();
            Rlog.d(LOG_TAG, " update remoteCallProfile=" + remoteCallProfile + "isRemoteVideoCapable()= " + isRemoteVideoCapable());
            if (remoteCallProfile != null) {
                boolean newRemoteVideoCapable = remoteCallProfile.mCallType == 4;
                if (isRemoteVideoCapable() != newRemoteVideoCapable) {
                    setRemoteVideoCapable(newRemoteVideoCapable);
                    changed = true;
                }
            }
            int callSubstate = getCallSubstate();
            int newCallSubstate = imsCall.getCallSubstate();
            if (callSubstate != newCallSubstate) {
                setCallSubstate(newCallSubstate);
                changed = true;
            }
        } catch (ImsException e) {
        }
        ImsCallProfile callProfile = imsCall.getCallProfile();
        if (callProfile != null) {
            String address = callProfile.getCallExtra("oi");
            String name = callProfile.getCallExtra("cna");
            int nump = ImsCallProfile.OIRToPresentation(callProfile.getCallExtraInt("oir"));
            int namep = ImsCallProfile.OIRToPresentation(callProfile.getCallExtraInt("cnap"));
            Rlog.d(LOG_TAG, "address = " + address + " name = " + name + " nump = " + nump + " namep = " + namep);
            if ((this.mAddress == null && address != null) || (this.mAddress != null && !this.mAddress.equals(address))) {
                if (!TelBrand.IS_DCM) {
                    this.mAddress = address;
                } else {
                    Rlog.d(LOG_TAG, "update: phone # changed!");
                    if (TextUtils.isEmpty(address)) {
                        this.mAddress = address;
                        this.redirectingNum = null;
                    } else if (address.indexOf(38) == -1) {
                        this.mAddress = address;
                        this.redirectingNum = null;
                    } else {
                        String[] a = address.split("&");
                        this.mAddress = a[0];
                        this.redirectingNum = a[1];
                        Rlog.d(LOG_TAG, "update: address is " + this.mAddress);
                        Rlog.d(LOG_TAG, "update: redirectingNum is " + this.redirectingNum);
                    }
                }
                changed = true;
            }
            if (TextUtils.isEmpty(name)) {
                if (!TextUtils.isEmpty(this.mCnapName)) {
                    this.mCnapName = "";
                    changed = true;
                }
            } else if (!name.equals(this.mCnapName)) {
                this.mCnapName = name;
                changed = true;
            }
            if (this.mNumberPresentation != nump) {
                this.mNumberPresentation = nump;
                changed = true;
            }
            if (this.mCnapNamePresentation != namep) {
                this.mCnapNamePresentation = namep;
                changed = true;
            }
            int oldVideoState = getVideoState();
            int newVideoState = ImsCallProfile.getVideoStateFromImsCallProfile(callProfile);
            if (oldVideoState != newVideoState) {
                setVideoState(newVideoState);
                changed = true;
            }
            try {
                ImsCallProfile localCallProfile2 = imsCall.getLocalCallProfile();
                ImsCallProfile remoteCallProfile2 = imsCall.getRemoteCallProfile();
                if (!(localCallProfile2 == null || remoteCallProfile2 == null || getAudioQuality() == (newAudioQuality = getAudioQualityFromCallProfile(localCallProfile2, remoteCallProfile2)))) {
                    setAudioQuality(newAudioQuality);
                    changed = true;
                }
            } catch (ImsException e2) {
            }
        }
        boolean mptyState = isMultiparty();
        if (mptyState == this.mMptyState) {
            return changed;
        }
        this.mMptyState = mptyState;
        return true;
    }

    @Override // com.android.internal.telephony.Connection
    public int getPreciseDisconnectCause() {
        return 0;
    }

    public Bundle getCallExtras() {
        return this.mCallExtras;
    }

    private boolean restrictedMergeFeatureEnabled() {
        if (SystemProperties.get(PROPERTY_ENABLE_RESTRICT_NON_OWNER_MERGE, "false").equals("true")) {
            return true;
        }
        return this.mContext.getResources().getBoolean(17957013);
    }

    @Override // com.android.internal.telephony.Connection
    public void onDisconnectConferenceParticipant(Uri endpoint) {
        ImsCall imsCall = getImsCall();
        if (imsCall != null) {
            try {
                imsCall.removeParticipants(new String[]{endpoint.toString()});
            } catch (ImsException e) {
                Rlog.e(LOG_TAG, "onDisconnectConferenceParticipant: no session in place. Failed to disconnect endpoint = " + endpoint);
            }
        }
    }

    private boolean updateMediaCapabilities(ImsCall imsCall) {
        if (imsCall == null) {
            return false;
        }
        boolean changed = false;
        try {
            ImsCallProfile localCallProfile = imsCall.getLocalCallProfile();
            ImsCallProfile remoteCallProfile = imsCall.getRemoteCallProfile();
            if (localCallProfile != null) {
                boolean newLocalVideoCapable = localCallProfile.mCallType == 4;
                if (isLocalVideoCapable() != newLocalVideoCapable) {
                    setLocalVideoCapable(newLocalVideoCapable);
                    changed = true;
                }
            }
            int newAudioQuality = getAudioQualityFromCallProfile(localCallProfile, remoteCallProfile);
            if (getAudioQuality() == newAudioQuality) {
                return changed;
            }
            setAudioQuality(newAudioQuality);
            return true;
        } catch (ImsException e) {
            return changed;
        }
    }

    private int getAudioQualityFromCallProfile(ImsCallProfile localCallProfile, ImsCallProfile remoteCallProfile) {
        if (localCallProfile == null || remoteCallProfile == null || localCallProfile.mMediaProfile == null) {
            return 1;
        }
        return !((localCallProfile.mMediaProfile.mAudioQuality == 2 || localCallProfile.mMediaProfile.mAudioQuality == 6) && remoteCallProfile.mRestrictCause == 0) ? 1 : 2;
    }

    @Override // com.android.internal.telephony.Connection
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ImsPhoneConnection objId: ");
        sb.append(System.identityHashCode(this));
        sb.append(" address:");
        sb.append(Log.pii(getAddress()));
        sb.append(" ImsCall:");
        if (this.mImsCall == null) {
            sb.append("null");
        } else {
            sb.append(this.mImsCall);
        }
        sb.append("]");
        return sb.toString();
    }

    @Override // com.android.internal.telephony.Connection
    public String getBeforeFowardingNumber() {
        return this.redirectingNum;
    }
}
