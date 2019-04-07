package com.android.internal.telephony.imsphone;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
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
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Connection.PostDialState;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.UUSInfo;

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
    private Bundle mCallExtras = null;
    private int mCause = 0;
    private Context mContext;
    private long mDisconnectTime;
    private boolean mDisconnected;
    private Handler mHandler;
    private ImsCall mImsCall;
    private boolean mIsConferenceUri = false;
    private boolean mMptyState = false;
    private int mNextPostDialChar;
    private ImsPhoneCallTracker mOwner;
    private ImsPhoneCall mParent;
    private WakeLock mPartialWakeLock;
    private PostDialState mPostDialState = PostDialState.NOT_STARTED;
    private String mPostDialString;
    private UUSInfo mUusInfo;
    String redirectingNum;

    class MyHandler extends Handler {
        MyHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message message) {
            switch (message.what) {
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

    ImsPhoneConnection(Context context, ImsCall imsCall, ImsPhoneCallTracker imsPhoneCallTracker, ImsPhoneCall imsPhoneCall, boolean z, State state, String str) {
        boolean z2 = false;
        createWakeLock(context);
        acquireWakeLock();
        this.mContext = context;
        this.mOwner = imsPhoneCallTracker;
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
                    String[] split = this.mAddress.split("&");
                    this.mAddress = split[0];
                    this.redirectingNum = split[1];
                    Rlog.d(LOG_TAG, "ImsConnection: address is " + this.mAddress);
                    Rlog.d(LOG_TAG, "ImsConnection: redirectingNum is " + this.redirectingNum);
                }
            }
            this.mCnapName = imsCall.getCallProfile().getCallExtra("cna");
            this.mNumberPresentation = ImsCallProfile.OIRToPresentation(imsCall.getCallProfile().getCallExtraInt("oir"));
            this.mCnapNamePresentation = ImsCallProfile.OIRToPresentation(imsCall.getCallProfile().getCallExtraInt("cnap"));
            ImsCallProfile callProfile = imsCall.getCallProfile();
            if (callProfile != null) {
                setVideoState(ImsCallProfile.getVideoStateFromImsCallProfile(callProfile));
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
        this.mParent = imsPhoneCall;
        if (!z) {
            z2 = true;
        }
        this.mIsIncoming = z2;
        if (z) {
            this.mParent.attach(this, state);
            this.mAddress = str;
            this.mCnapName = str;
            this.mCnapNamePresentation = 1;
            this.mNumberPresentation = 1;
            return;
        }
        this.mParent.attach(this, this.mIsIncoming ? State.INCOMING : State.DIALING);
    }

    ImsPhoneConnection(Context context, String str, ImsPhoneCallTracker imsPhoneCallTracker, ImsPhoneCall imsPhoneCall, Bundle bundle) {
        boolean z;
        createWakeLock(context);
        acquireWakeLock();
        boolean z2;
        if (bundle != null) {
            z = bundle.getBoolean("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
            z2 = bundle.getBoolean("org.codeaurora.extra.SKIP_SCHEMA_PARSING", false);
        } else {
            z2 = false;
            z = false;
        }
        this.mContext = context;
        this.mOwner = imsPhoneCallTracker;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mIsConferenceUri = z;
        this.mDialString = str;
        if (z || z2) {
            this.mAddress = str;
            this.mPostDialString = "";
        } else {
            this.mAddress = PhoneNumberUtils.extractNetworkPortionAlt(str);
            this.mPostDialString = PhoneNumberUtils.extractPostDialPortion(str);
        }
        this.mIsIncoming = false;
        this.mCnapName = null;
        this.mCnapNamePresentation = 1;
        this.mNumberPresentation = 1;
        this.mCreateTime = System.currentTimeMillis();
        if (bundle != null) {
            this.mCallExtras = bundle;
        }
        this.mParent = imsPhoneCall;
        imsPhoneCall.attachFake(this, State.DIALING);
    }

    private void acquireWakeLock() {
        Rlog.d(LOG_TAG, "acquireWakeLock");
        this.mPartialWakeLock.acquire();
    }

    private void createWakeLock(Context context) {
        this.mPartialWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, LOG_TAG);
    }

    static boolean equalsHandlesNulls(Object obj, Object obj2) {
        return obj == null ? obj2 == null : obj.equals(obj2);
    }

    private int getAudioQualityFromCallProfile(ImsCallProfile imsCallProfile, ImsCallProfile imsCallProfile2) {
        int i = 2;
        if (imsCallProfile == null || imsCallProfile2 == null || imsCallProfile.mMediaProfile == null) {
            i = 1;
        } else {
            int i2 = ((imsCallProfile.mMediaProfile.mAudioQuality == 2 || imsCallProfile.mMediaProfile.mAudioQuality == 6) && imsCallProfile2.mRestrictCause == 0) ? 1 : 0;
            if (i2 == 0) {
                return 1;
            }
        }
        return i;
    }

    private int getAudioQualityFromMediaProfile(ImsStreamMediaProfile imsStreamMediaProfile) {
        return imsStreamMediaProfile.mAudioQuality == 2 ? 2 : 1;
    }

    private void processNextPostDialChar() {
        if (this.mPostDialState != PostDialState.CANCELLED) {
            char c;
            if (this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
                setPostDialState(PostDialState.COMPLETE);
                c = 0;
            } else {
                setPostDialState(PostDialState.STARTED);
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
            Registrant registrant = this.mOwner.mPhone.mPostDialHandler;
            if (registrant != null) {
                Message messageForRegistrant = registrant.messageForRegistrant();
                if (messageForRegistrant != null) {
                    PostDialState postDialState = this.mPostDialState;
                    AsyncResult forMessage = AsyncResult.forMessage(messageForRegistrant);
                    forMessage.result = this;
                    forMessage.userObj = postDialState;
                    messageForRegistrant.arg1 = c;
                    messageForRegistrant.sendToTarget();
                }
            }
        }
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
                setPostDialState(PostDialState.PAUSE);
            }
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(2), 3000);
            return true;
        } else if (c == ';') {
            setPostDialState(PostDialState.WAIT);
            return true;
        } else if (c == 'P') {
            setPostDialState(PostDialState.WAIT_EX);
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(2), 3000);
            return true;
        } else if (c != 'N') {
            return false;
        } else {
            setPostDialState(PostDialState.WILD);
            return true;
        }
    }

    private boolean restrictedMergeFeatureEnabled() {
        return SystemProperties.get(PROPERTY_ENABLE_RESTRICT_NON_OWNER_MERGE, "false").equals("true") ? true : this.mContext.getResources().getBoolean(17957013);
    }

    private void setPostDialState(PostDialState postDialState) {
        if (this.mPostDialState != PostDialState.STARTED && postDialState == PostDialState.STARTED) {
            acquireWakeLock();
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(4), 60000);
        } else if (this.mPostDialState == PostDialState.STARTED && postDialState != PostDialState.STARTED) {
            this.mHandler.removeMessages(4);
            releaseWakeLock();
        }
        this.mPostDialState = postDialState;
        notifyPostDialListeners();
    }

    private boolean updateMediaCapabilities(ImsCall imsCall) {
        boolean z = false;
        if (imsCall != null) {
            try {
                ImsCallProfile localCallProfile = imsCall.getLocalCallProfile();
                ImsCallProfile remoteCallProfile = imsCall.getRemoteCallProfile();
                if (localCallProfile != null) {
                    boolean z2 = localCallProfile.mCallType == 4;
                    if (isLocalVideoCapable() != z2) {
                        setLocalVideoCapable(z2);
                        z = true;
                    }
                }
                int audioQualityFromCallProfile = getAudioQualityFromCallProfile(localCallProfile, remoteCallProfile);
                if (getAudioQuality() != audioQualityFromCallProfile) {
                    setAudioQuality(audioQualityFromCallProfile);
                    return true;
                }
            } catch (ImsException e) {
                return false;
            }
        }
        return z;
    }

    public void cancelPostDial() {
        setPostDialState(PostDialState.CANCELLED);
    }

    /* Access modifiers changed, original: 0000 */
    public void changeParent(ImsPhoneCall imsPhoneCall) {
        this.mParent = imsPhoneCall;
    }

    public void dispose() {
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        clearPostDialListeners();
        releaseWakeLock();
    }

    public String getBeforeFowardingNumber() {
        return this.redirectingNum;
    }

    public ImsPhoneCall getCall() {
        return this.mParent;
    }

    public Bundle getCallExtras() {
        return this.mCallExtras;
    }

    public int getDisconnectCause() {
        return this.mCause;
    }

    public long getDisconnectTime() {
        return this.mDisconnectTime;
    }

    public long getHoldDurationMillis() {
        return getState() != State.HOLDING ? 0 : SystemClock.elapsedRealtime() - this.mHoldingStartTime;
    }

    public long getHoldingStartTime() {
        return this.mHoldingStartTime;
    }

    /* Access modifiers changed, original: 0000 */
    public ImsCall getImsCall() {
        return this.mImsCall;
    }

    public int getNumberPresentation() {
        return this.mNumberPresentation;
    }

    public Connection getOrigConnection() {
        return null;
    }

    public String getOrigDialString() {
        return this.mDialString;
    }

    public ImsPhoneCallTracker getOwner() {
        return this.mOwner;
    }

    public PostDialState getPostDialState() {
        return this.mPostDialState;
    }

    public int getPreciseDisconnectCause() {
        return 0;
    }

    public String getRemainingPostDialString() {
        if (this.mPostDialState == PostDialState.CANCELLED || this.mPostDialState == PostDialState.COMPLETE || this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
            return "";
        }
        String substring = this.mPostDialString.substring(this.mNextPostDialChar);
        if (substring != null) {
            int indexOf = substring.indexOf(59);
            int indexOf2 = substring.indexOf(44);
            int indexOf3 = substring.indexOf(80);
            if (indexOf > 0 && ((indexOf < indexOf2 || indexOf2 <= 0) && (indexOf < indexOf3 || indexOf3 <= 0))) {
                substring = substring.substring(0, indexOf);
            } else if (indexOf3 > 0 && ((indexOf3 < indexOf2 || indexOf2 <= 0) && (indexOf3 < indexOf || indexOf <= 0))) {
                substring = substring.substring(0, indexOf3);
            } else if (indexOf2 > 0) {
                substring = substring.substring(0, indexOf2);
            }
            Rlog.w(LOG_TAG, "getRemainingPostDialString() wIndex(" + indexOf + "), pIndex(" + indexOf2 + "), wexIndex(" + indexOf3 + ")");
        }
        Rlog.w(LOG_TAG, "getRemainingPostDialString() end, return String(" + substring + ")");
        return substring;
    }

    public State getState() {
        return this.mDisconnected ? State.DISCONNECTED : super.getState();
    }

    public UUSInfo getUUSInfo() {
        return this.mUusInfo;
    }

    public void hangup() throws CallStateException {
        if (this.mDisconnected) {
            throw new CallStateException("disconnected");
        }
        this.mOwner.hangup(this);
    }

    public boolean isMergeAllowed() {
        return (restrictedMergeFeatureEnabled() && this.mImsCall != null && this.mImsCall.isMultiparty() && this.mImsCall.getCallGroup() == null && !this.mIsConferenceUri) ? false : true;
    }

    public boolean isMultiparty() {
        return this.mImsCall != null && this.mImsCall.isMultiparty();
    }

    /* Access modifiers changed, original: 0000 */
    public void onConnectedInOrOut() {
        this.mConnectTime = System.currentTimeMillis();
        this.mConnectTimeReal = SystemClock.elapsedRealtime();
        this.mDuration = 0;
        Rlog.d(LOG_TAG, "onConnectedInOrOut: connectTime=" + this.mConnectTime);
        if (!this.mIsIncoming) {
            processNextPostDialChar();
        }
        releaseWakeLock();
    }

    /* Access modifiers changed, original: 0000 */
    public boolean onDisconnect() {
        boolean z = false;
        if (!this.mDisconnected) {
            this.mDisconnectTime = System.currentTimeMillis();
            this.mDuration = SystemClock.elapsedRealtime() - this.mConnectTimeReal;
            this.mDisconnected = true;
            this.mOwner.mPhone.notifyDisconnect(this);
            if (this.mParent != null) {
                z = this.mParent.connectionDisconnected(this);
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
        return z;
    }

    public boolean onDisconnect(int i) {
        Rlog.d(LOG_TAG, "onDisconnect: cause=" + i);
        if (this.mCause != 3) {
            this.mCause = i;
        }
        return onDisconnect();
    }

    public void onDisconnectConferenceParticipant(Uri uri) {
        ImsCall imsCall = getImsCall();
        if (imsCall != null) {
            try {
                imsCall.removeParticipants(new String[]{uri.toString()});
            } catch (ImsException e) {
                Rlog.e(LOG_TAG, "onDisconnectConferenceParticipant: no session in place. Failed to disconnect endpoint = " + uri);
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void onHangupLocal() {
        this.mCause = 3;
    }

    /* Access modifiers changed, original: 0000 */
    public void onStartedHolding() {
        this.mHoldingStartTime = SystemClock.elapsedRealtime();
    }

    public void proceedAfterWaitChar() {
        if (this.mPostDialState == PostDialState.WAIT || this.mPostDialState == PostDialState.WAIT_EX) {
            setPostDialState(PostDialState.STARTED);
            processNextPostDialChar();
            return;
        }
        Rlog.w(LOG_TAG, "ImsPhoneConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WAIT but was " + this.mPostDialState);
    }

    public void proceedAfterWildChar(String str) {
        if (this.mPostDialState != PostDialState.WILD) {
            Rlog.w(LOG_TAG, "ImsPhoneConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WILD but was " + this.mPostDialState);
            return;
        }
        setPostDialState(PostDialState.STARTED);
        StringBuilder stringBuilder = new StringBuilder(str);
        stringBuilder.append(this.mPostDialString.substring(this.mNextPostDialChar));
        this.mPostDialString = stringBuilder.toString();
        this.mNextPostDialChar = 0;
        Rlog.d(LOG_TAG, "proceedAfterWildChar: new postDialString is " + this.mPostDialString);
        processNextPostDialChar();
    }

    /* Access modifiers changed, original: 0000 */
    public void releaseWakeLock() {
        synchronized (this.mPartialWakeLock) {
            if (this.mPartialWakeLock.isHeld()) {
                Rlog.d(LOG_TAG, "releaseWakeLock");
                this.mPartialWakeLock.release();
            }
        }
    }

    public void separate() throws CallStateException {
        throw new CallStateException("not supported");
    }

    public void setDisconnectCause(int i) {
        this.mCause = i;
    }

    /* Access modifiers changed, original: 0000 */
    public void setImsCall(ImsCall imsCall) {
        this.mImsCall = imsCall;
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[ImsPhoneConnection objId: ");
        stringBuilder.append(System.identityHashCode(this));
        stringBuilder.append(" address:");
        stringBuilder.append(Log.pii(getAddress()));
        stringBuilder.append(" ImsCall:");
        if (this.mImsCall == null) {
            stringBuilder.append("null");
        } else {
            stringBuilder.append(this.mImsCall);
        }
        stringBuilder.append("]");
        return stringBuilder.toString();
    }

    /* Access modifiers changed, original: 0000 */
    /* JADX WARNING: Removed duplicated region for block: B:15:0x006d A:{Catch:{ ImsException -> 0x01cf }} */
    /* JADX WARNING: Removed duplicated region for block: B:23:0x0086 A:{Catch:{ ImsException -> 0x01cf }} */
    /* JADX WARNING: Removed duplicated region for block: B:81:0x01db  */
    /* JADX WARNING: Removed duplicated region for block: B:27:0x0090  */
    /* JADX WARNING: Removed duplicated region for block: B:62:0x0150  */
    public boolean update(com.android.ims.ImsCall r13) {
        /*
        r12 = this;
        r11 = 0;
        r7 = 4;
        r2 = 0;
        r1 = 1;
        if (r13 == 0) goto L_0x01d5;
    L_0x0006:
        r0 = r13.getLocalCallProfile();	 Catch:{ ImsException -> 0x01cb }
        r3 = "ImsPhoneConnection";
        r4 = new java.lang.StringBuilder;	 Catch:{ ImsException -> 0x01cb }
        r4.<init>();	 Catch:{ ImsException -> 0x01cb }
        r5 = " update localCallProfile=";
        r4 = r4.append(r5);	 Catch:{ ImsException -> 0x01cb }
        r4 = r4.append(r0);	 Catch:{ ImsException -> 0x01cb }
        r5 = "isLocalVideoCapable()= ";
        r4 = r4.append(r5);	 Catch:{ ImsException -> 0x01cb }
        r5 = r12.isLocalVideoCapable();	 Catch:{ ImsException -> 0x01cb }
        r4 = r4.append(r5);	 Catch:{ ImsException -> 0x01cb }
        r4 = r4.toString();	 Catch:{ ImsException -> 0x01cb }
        android.telephony.Rlog.d(r3, r4);	 Catch:{ ImsException -> 0x01cb }
        if (r0 == 0) goto L_0x01de;
    L_0x0032:
        r0 = r0.mCallType;	 Catch:{ ImsException -> 0x01cb }
        if (r0 != r7) goto L_0x0153;
    L_0x0036:
        r0 = r1;
    L_0x0037:
        r3 = r12.isLocalVideoCapable();	 Catch:{ ImsException -> 0x01cb }
        if (r3 == r0) goto L_0x01de;
    L_0x003d:
        r12.setLocalVideoCapable(r0);	 Catch:{ ImsException -> 0x01cb }
        r0 = r1;
    L_0x0041:
        r3 = r13.getRemoteCallProfile();	 Catch:{ ImsException -> 0x01cf }
        r4 = "ImsPhoneConnection";
        r5 = new java.lang.StringBuilder;	 Catch:{ ImsException -> 0x01cf }
        r5.<init>();	 Catch:{ ImsException -> 0x01cf }
        r6 = " update remoteCallProfile=";
        r5 = r5.append(r6);	 Catch:{ ImsException -> 0x01cf }
        r5 = r5.append(r3);	 Catch:{ ImsException -> 0x01cf }
        r6 = "isRemoteVideoCapable()= ";
        r5 = r5.append(r6);	 Catch:{ ImsException -> 0x01cf }
        r6 = r12.isRemoteVideoCapable();	 Catch:{ ImsException -> 0x01cf }
        r5 = r5.append(r6);	 Catch:{ ImsException -> 0x01cf }
        r5 = r5.toString();	 Catch:{ ImsException -> 0x01cf }
        android.telephony.Rlog.d(r4, r5);	 Catch:{ ImsException -> 0x01cf }
        if (r3 == 0) goto L_0x007c;
    L_0x006d:
        r3 = r3.mCallType;	 Catch:{ ImsException -> 0x01cf }
        if (r3 != r7) goto L_0x0156;
    L_0x0071:
        r3 = r1;
    L_0x0072:
        r4 = r12.isRemoteVideoCapable();	 Catch:{ ImsException -> 0x01cf }
        if (r4 == r3) goto L_0x007c;
    L_0x0078:
        r12.setRemoteVideoCapable(r3);	 Catch:{ ImsException -> 0x01cf }
        r0 = r1;
    L_0x007c:
        r3 = r12.getCallSubstate();	 Catch:{ ImsException -> 0x01cf }
        r4 = r13.getCallSubstate();	 Catch:{ ImsException -> 0x01cf }
        if (r3 == r4) goto L_0x008a;
    L_0x0086:
        r12.setCallSubstate(r4);	 Catch:{ ImsException -> 0x01cf }
        r0 = r1;
    L_0x008a:
        r3 = r13.getCallProfile();
        if (r3 == 0) goto L_0x01db;
    L_0x0090:
        r4 = "oi";
        r4 = r3.getCallExtra(r4);
        r5 = "cna";
        r5 = r3.getCallExtra(r5);
        r6 = "oir";
        r6 = r3.getCallExtraInt(r6);
        r6 = com.android.ims.ImsCallProfile.OIRToPresentation(r6);
        r7 = "cnap";
        r7 = r3.getCallExtraInt(r7);
        r7 = com.android.ims.ImsCallProfile.OIRToPresentation(r7);
        r8 = "ImsPhoneConnection";
        r9 = new java.lang.StringBuilder;
        r9.<init>();
        r10 = "address = ";
        r9 = r9.append(r10);
        r9 = r9.append(r4);
        r10 = " name = ";
        r9 = r9.append(r10);
        r9 = r9.append(r5);
        r10 = " nump = ";
        r9 = r9.append(r10);
        r9 = r9.append(r6);
        r10 = " namep = ";
        r9 = r9.append(r10);
        r9 = r9.append(r7);
        r9 = r9.toString();
        android.telephony.Rlog.d(r8, r9);
        r8 = r12.mAddress;
        if (r8 != 0) goto L_0x00ec;
    L_0x00ea:
        if (r4 != 0) goto L_0x00f8;
    L_0x00ec:
        r8 = r12.mAddress;
        if (r8 == 0) goto L_0x00ff;
    L_0x00f0:
        r8 = r12.mAddress;
        r8 = r8.equals(r4);
        if (r8 != 0) goto L_0x00ff;
    L_0x00f8:
        r0 = com.android.internal.telephony.TelBrand.IS_DCM;
        if (r0 != 0) goto L_0x0159;
    L_0x00fc:
        r12.mAddress = r4;
    L_0x00fe:
        r0 = r1;
    L_0x00ff:
        r2 = android.text.TextUtils.isEmpty(r5);
        if (r2 == 0) goto L_0x01be;
    L_0x0105:
        r2 = r12.mCnapName;
        r2 = android.text.TextUtils.isEmpty(r2);
        if (r2 != 0) goto L_0x0112;
    L_0x010d:
        r0 = "";
        r12.mCnapName = r0;
        r0 = r1;
    L_0x0112:
        r2 = r12.mNumberPresentation;
        if (r2 == r6) goto L_0x0119;
    L_0x0116:
        r12.mNumberPresentation = r6;
        r0 = r1;
    L_0x0119:
        r2 = r12.mCnapNamePresentation;
        if (r2 == r7) goto L_0x0120;
    L_0x011d:
        r12.mCnapNamePresentation = r7;
        r0 = r1;
    L_0x0120:
        r2 = r12.getVideoState();
        r3 = com.android.ims.ImsCallProfile.getVideoStateFromImsCallProfile(r3);
        if (r2 == r3) goto L_0x01d8;
    L_0x012a:
        r12.setVideoState(r3);
        r2 = r1;
    L_0x012e:
        r0 = r13.getLocalCallProfile();	 Catch:{ ImsException -> 0x01d2 }
        r3 = r13.getRemoteCallProfile();	 Catch:{ ImsException -> 0x01d2 }
        if (r0 == 0) goto L_0x0148;
    L_0x0138:
        if (r3 == 0) goto L_0x0148;
    L_0x013a:
        r4 = r12.getAudioQuality();	 Catch:{ ImsException -> 0x01d2 }
        r0 = r12.getAudioQualityFromCallProfile(r0, r3);	 Catch:{ ImsException -> 0x01d2 }
        if (r4 == r0) goto L_0x0148;
    L_0x0144:
        r12.setAudioQuality(r0);	 Catch:{ ImsException -> 0x01d2 }
        r2 = r1;
    L_0x0148:
        r0 = r12.isMultiparty();
        r3 = r12.mMptyState;
        if (r0 == r3) goto L_0x01d5;
    L_0x0150:
        r12.mMptyState = r0;
    L_0x0152:
        return r1;
    L_0x0153:
        r0 = r2;
        goto L_0x0037;
    L_0x0156:
        r3 = r2;
        goto L_0x0072;
    L_0x0159:
        r0 = "ImsPhoneConnection";
        r8 = "update: phone # changed!";
        android.telephony.Rlog.d(r0, r8);
        r0 = android.text.TextUtils.isEmpty(r4);
        if (r0 != 0) goto L_0x01b8;
    L_0x0166:
        r0 = 38;
        r0 = r4.indexOf(r0);
        r8 = -1;
        if (r0 != r8) goto L_0x0174;
    L_0x016f:
        r12.mAddress = r4;
        r12.redirectingNum = r11;
        goto L_0x00fe;
    L_0x0174:
        r0 = "&";
        r0 = r4.split(r0);
        r2 = r0[r2];
        r12.mAddress = r2;
        r0 = r0[r1];
        r12.redirectingNum = r0;
        r0 = "ImsPhoneConnection";
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "update: address is ";
        r2 = r2.append(r4);
        r4 = r12.mAddress;
        r2 = r2.append(r4);
        r2 = r2.toString();
        android.telephony.Rlog.d(r0, r2);
        r0 = "ImsPhoneConnection";
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "update: redirectingNum is ";
        r2 = r2.append(r4);
        r4 = r12.redirectingNum;
        r2 = r2.append(r4);
        r2 = r2.toString();
        android.telephony.Rlog.d(r0, r2);
        goto L_0x00fe;
    L_0x01b8:
        r12.mAddress = r4;
        r12.redirectingNum = r11;
        goto L_0x00fe;
    L_0x01be:
        r2 = r12.mCnapName;
        r2 = r5.equals(r2);
        if (r2 != 0) goto L_0x0112;
    L_0x01c6:
        r12.mCnapName = r5;
        r0 = r1;
        goto L_0x0112;
    L_0x01cb:
        r0 = move-exception;
        r0 = r2;
        goto L_0x008a;
    L_0x01cf:
        r3 = move-exception;
        goto L_0x008a;
    L_0x01d2:
        r0 = move-exception;
        goto L_0x0148;
    L_0x01d5:
        r1 = r2;
        goto L_0x0152;
    L_0x01d8:
        r2 = r0;
        goto L_0x012e;
    L_0x01db:
        r2 = r0;
        goto L_0x0148;
    L_0x01de:
        r0 = r2;
        goto L_0x0041;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.imsphone.ImsPhoneConnection.update(com.android.ims.ImsCall):boolean");
    }

    /* Access modifiers changed, original: 0000 */
    public boolean update(ImsCall imsCall, State state) {
        if (state == State.ACTIVE) {
            if (TelBrand.IS_DCM) {
                if (this.mParent.getState().isRinging() || this.mParent.getState().isDialing() || (this.mParent == this.mOwner.mRingingCall && this.mParent.getState() == State.DISCONNECTING)) {
                    onConnectedInOrOut();
                }
            } else if (this.mParent.getState().isRinging() || this.mParent.getState().isDialing()) {
                onConnectedInOrOut();
            }
            if (TelBrand.IS_DCM) {
                if (this.mParent.getState().isRinging() || this.mParent == this.mOwner.mBackgroundCall || (this.mParent == this.mOwner.mRingingCall && this.mParent.getState() == State.DISCONNECTING)) {
                    this.mParent.detach(this);
                    this.mParent = this.mOwner.mForegroundCall;
                    this.mParent.attach(this);
                }
            } else if (this.mParent.getState().isRinging() || this.mParent == this.mOwner.mBackgroundCall) {
                this.mParent.detach(this);
                this.mParent = this.mOwner.mForegroundCall;
                this.mParent.attach(this);
            }
        } else if (state == State.HOLDING) {
            onStartedHolding();
        }
        return update(imsCall) || this.mParent.update(this, imsCall, state);
    }
}
