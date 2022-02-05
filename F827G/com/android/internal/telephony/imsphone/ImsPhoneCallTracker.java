package com.android.internal.telephony.imsphone;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telecom.ConferenceParticipant;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import com.android.ims.ImsCall;
import com.android.ims.ImsCallProfile;
import com.android.ims.ImsConnectionStateListener;
import com.android.ims.ImsEcbm;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.ims.ImsReasonInfo;
import com.android.ims.ImsSuppServiceNotification;
import com.android.ims.ImsUtInterface;
import com.android.ims.internal.CallGroup;
import com.android.ims.internal.IImsVideoCallProvider;
import com.android.ims.internal.ImsCallSession;
import com.android.ims.internal.ImsVideoCallProviderWrapper;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccProvider;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class ImsPhoneCallTracker extends CallTracker {
    private static final boolean DBG = true;
    private static final int EVENT_DIAL_PENDINGMO = 20;
    private static final int EVENT_HANGUP_PENDINGMO = 18;
    private static final int EVENT_RESUME_BACKGROUND = 19;
    static final String LOG_TAG = "ImsPhoneCallTracker";
    static final int MAX_CONNECTIONS = 7;
    static final int MAX_CONNECTIONS_PER_CALL = 5;
    private static final int TIMEOUT_HANGUP_PENDINGMO = 500;
    private ImsManager mImsManager;
    private int mPendingCallVideoState;
    private ImsPhoneConnection mPendingMO;
    ImsPhone mPhone;
    private int pendingCallClirMode;
    private boolean mIsVolteEnabled = false;
    private boolean mIsVtEnabled = false;
    private boolean mIsUtEnabled = false;
    private boolean mIsSrvccCompleted = false;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.internal.telephony.imsphone.ImsPhoneCallTracker.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            ImsPhoneCall call;
            if (intent.getAction().equals("com.android.ims.IMS_INCOMING_CALL")) {
                ImsPhoneCallTracker.this.log("onReceive : incoming call intent");
                if (ImsPhoneCallTracker.this.mImsManager != null && ImsPhoneCallTracker.this.mServiceId >= 0 && !ImsPhoneCallTracker.this.mIsSrvccCompleted) {
                    try {
                        if (intent.getBooleanExtra("android:ussd", false)) {
                            ImsPhoneCallTracker.this.log("onReceive : USSD");
                            ImsPhoneCallTracker.this.mUssdSession = ImsPhoneCallTracker.this.mImsManager.takeCall(ImsPhoneCallTracker.this.mServiceId, intent, ImsPhoneCallTracker.this.mImsUssdListener);
                            if (ImsPhoneCallTracker.this.mUssdSession != null) {
                                ImsPhoneCallTracker.this.mUssdSession.accept(2);
                                return;
                            }
                            return;
                        }
                        boolean isUnknown = intent.getBooleanExtra("codeaurora:isUnknown", false);
                        int phantomState = intent.getIntExtra("codeaurora.unknownCallState", 0);
                        String address = intent.getStringExtra("codeaurora:unknownCallAddress");
                        ImsPhoneCallTracker.this.log("onReceive : isUnknown = " + isUnknown + " state = " + phantomState + " fg = " + ImsPhoneCallTracker.this.mForegroundCall.getState() + " bg = " + ImsPhoneCallTracker.this.mBackgroundCall.getState() + " address = " + address);
                        ImsCall imsCall = ImsPhoneCallTracker.this.mImsManager.takeCall(ImsPhoneCallTracker.this.mServiceId, intent, ImsPhoneCallTracker.this.mImsCallListener);
                        Call.State state = ImsPhoneCallTracker.this.convertIntToCallState(phantomState);
                        if (!isUnknown) {
                            call = ImsPhoneCallTracker.this.mRingingCall;
                        } else if (!isUnknown || state != Call.State.HOLDING) {
                            call = ImsPhoneCallTracker.this.mForegroundCall;
                        } else {
                            call = ImsPhoneCallTracker.this.mBackgroundCall;
                        }
                        ImsPhoneConnection conn = new ImsPhoneConnection(ImsPhoneCallTracker.this.mPhone.getContext(), imsCall, ImsPhoneCallTracker.this, call, isUnknown, state, address);
                        ImsPhoneCallTracker.this.addConnection(conn);
                        if (isUnknown && state == Call.State.HOLDING) {
                            imsCall.updateHoldValues();
                        }
                        ImsPhoneCallTracker.this.setVideoCallProvider(conn, imsCall);
                        if (isUnknown) {
                            ImsPhoneCallTracker.this.mPhone.notifyUnknownConnection(conn);
                        } else {
                            if (!(ImsPhoneCallTracker.this.mForegroundCall.getState() == Call.State.IDLE && ImsPhoneCallTracker.this.mBackgroundCall.getState() == Call.State.IDLE)) {
                                conn.update(imsCall, Call.State.WAITING);
                            }
                            ImsPhoneCallTracker.this.mPhone.notifyNewRingingConnection(conn);
                            ImsPhoneCallTracker.this.mPhone.notifyIncomingRing();
                        }
                        ImsPhoneCallTracker.this.updatePhoneState();
                        ImsPhoneCallTracker.this.mPhone.notifyPreciseCallStateChanged();
                    } catch (ImsException e) {
                        ImsPhoneCallTracker.this.loge("onReceive : exception " + e);
                    } catch (RemoteException e2) {
                    }
                }
            }
        }
    };
    private ArrayList<ImsPhoneConnection> mConnections = new ArrayList<>();
    private RegistrantList mVoiceCallEndedRegistrants = new RegistrantList();
    private RegistrantList mVoiceCallStartedRegistrants = new RegistrantList();
    ImsPhoneCall mRingingCall = new ImsPhoneCall(this);
    ImsPhoneCall mForegroundCall = new ImsPhoneCall(this);
    ImsPhoneCall mBackgroundCall = new ImsPhoneCall(this);
    ImsPhoneCall mHandoverCall = new ImsPhoneCall(this);
    private int mClirMode = 0;
    private Object mSyncHold = new Object();
    private ImsCall mUssdSession = null;
    private Message mPendingUssd = null;
    private boolean mDesiredMute = false;
    private boolean mOnHoldToneStarted = false;
    PhoneConstants.State mState = PhoneConstants.State.IDLE;
    private int mServiceId = -1;
    private Call.SrvccState mSrvccState = Call.SrvccState.NONE;
    private boolean mIsInEmergencyCall = false;
    private boolean pendingCallInEcm = false;
    private long mConferenceTime = 0;
    private ImsCall.Listener mImsCallListener = new ImsCall.Listener() { // from class: com.android.internal.telephony.imsphone.ImsPhoneCallTracker.3
        public void onCallProgressing(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallProgressing");
            ImsPhoneCallTracker.this.mPendingMO = null;
            ImsPhoneCallTracker.this.processCallStateChange(imsCall, Call.State.ALERTING, 0);
        }

        public void onCallStarted(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallStarted");
            ImsPhoneCallTracker.this.mPendingMO = null;
            ImsPhoneCallTracker.this.processCallStateChange(imsCall, Call.State.ACTIVE, 0);
        }

        public void onCallUpdated(ImsCall imsCall) {
            ImsPhoneConnection conn;
            ImsPhoneCallTracker.this.log("onCallUpdated");
            if (imsCall != null && (conn = ImsPhoneCallTracker.this.findConnection(imsCall)) != null) {
                if (ImsPhoneCallTracker.this.mPendingMO != null && conn.getCall().mState == Call.State.DIALING) {
                    ImsPhoneCallTracker.this.log("onCallUpdated(DIALING)...mPendingMO=null");
                    ImsPhoneCallTracker.this.mPendingMO = null;
                }
                ImsPhoneCallTracker.this.processCallStateChange(imsCall, conn.getCall().mState, 0, true);
            }
        }

        public void onCallStartFailed(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.log("onCallStartFailed reasonCode=" + reasonInfo.getCode());
            if (ImsPhoneCallTracker.this.mPendingMO == null) {
                return;
            }
            if (reasonInfo.getCode() == 146 && ImsPhoneCallTracker.this.mBackgroundCall.getState() == Call.State.IDLE && ImsPhoneCallTracker.this.mRingingCall.getState() == Call.State.IDLE) {
                ImsPhoneCallTracker.this.mForegroundCall.detach(ImsPhoneCallTracker.this.mPendingMO);
                ImsPhoneCallTracker.this.removeConnection(ImsPhoneCallTracker.this.mPendingMO);
                ImsPhoneCallTracker.this.mPendingMO.finalize();
                ImsPhoneCallTracker.this.mPendingMO = null;
                ImsPhoneCallTracker.this.mPhone.initiateSilentRedial();
                return;
            }
            ImsPhoneCallTracker.this.processCallStateChange(imsCall, Call.State.DISCONNECTED, ImsPhoneCallTracker.this.getDisconnectCauseFromReasonInfo(reasonInfo));
            ImsPhoneCallTracker.this.mPendingMO.setDisconnectCause(36);
            ImsPhoneCallTracker.this.sendEmptyMessageDelayed(18, 500L);
        }

        public void onCallTerminated(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.log("onCallTerminated reasonCode=" + reasonInfo.getCode());
            Call.State oldState = ImsPhoneCallTracker.this.mForegroundCall.getState();
            ImsPhoneCallTracker.this.log("onCallTerminated oldState=" + oldState);
            int cause = ImsPhoneCallTracker.this.getDisconnectCauseFromReasonInfo(reasonInfo);
            ImsPhoneConnection conn = ImsPhoneCallTracker.this.findConnection(imsCall);
            ImsPhoneCallTracker.this.log("cause = " + cause + " conn = " + conn);
            if (conn != null && conn.isIncoming() && conn.getConnectTime() == 0) {
                if (cause == 3) {
                    cause = 16;
                    conn.setDisconnectCause(16);
                } else if (cause == 16) {
                    conn.setDisconnectCause(cause);
                } else {
                    cause = 1;
                }
            }
            if (cause == 2 && conn != null && conn.getImsCall().isMerged()) {
                cause = 91;
            }
            if (ImsPhoneCallTracker.this.mPendingMO != null) {
                if (ImsPhoneCallTracker.this.mPendingMO.getImsCall() != null) {
                    if (ImsPhoneCallTracker.this.mPendingMO.getImsCall() == imsCall) {
                        ImsPhoneCallTracker.this.log("onCallTerminated() ImsCall is matching, mPendingMO=null");
                        ImsPhoneCallTracker.this.mPendingMO = null;
                    } else {
                        ImsPhoneCallTracker.this.log("onCallTerminated() ImsCall is not matching");
                    }
                } else if (ImsPhoneCallTracker.this.mForegroundCall.getState() != Call.State.ACTIVE) {
                    if (ImsPhoneCallTracker.this.mRingingCall.getState().isRinging()) {
                        ImsPhoneCallTracker.this.log("onCallTerminated() isRinging, mPendingMO=null");
                        ImsPhoneCallTracker.this.mPendingMO.update(null, Call.State.DISCONNECTED);
                        ImsPhoneCallTracker.this.mPendingMO.onDisconnect();
                        ImsPhoneCallTracker.this.removeConnection(ImsPhoneCallTracker.this.mPendingMO);
                        ImsPhoneCallTracker.this.mPendingMO = null;
                        ImsPhoneCallTracker.this.updatePhoneState();
                        ImsPhoneCallTracker.this.removeMessages(20);
                    } else {
                        ImsPhoneCallTracker.this.log("onCallTerminated() sendEmptyMessage(EVENT_DIAL_PENDINGMO)");
                        ImsPhoneCallTracker.this.sendEmptyMessage(20);
                    }
                }
            }
            ImsPhoneCallTracker.this.processCallStateChange(imsCall, Call.State.DISCONNECTED, cause);
            if (reasonInfo.getCode() == 501 && oldState == Call.State.DISCONNECTING && ImsPhoneCallTracker.this.mForegroundCall.getState() == Call.State.IDLE && ImsPhoneCallTracker.this.mBackgroundCall.getState() == Call.State.HOLDING) {
                ImsPhoneCallTracker.this.sendEmptyMessage(19);
            }
        }

        public void onCallHeld(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallHeld");
            synchronized (ImsPhoneCallTracker.this.mSyncHold) {
                Call.State oldState = ImsPhoneCallTracker.this.mBackgroundCall.getState();
                ImsPhoneCallTracker.this.processCallStateChange(imsCall, Call.State.HOLDING, 0);
                if (oldState == Call.State.ACTIVE) {
                    if (ImsPhoneCallTracker.this.mForegroundCall.getState() == Call.State.HOLDING || ImsPhoneCallTracker.this.mRingingCall.getState() == Call.State.WAITING) {
                        boolean isOwner = true;
                        CallGroup callGroup = imsCall.getCallGroup();
                        if (callGroup != null) {
                            isOwner = callGroup.isOwner(imsCall);
                        }
                        if (isOwner) {
                            ImsPhoneCallTracker.this.sendEmptyMessage(19);
                        }
                    } else if (ImsPhoneCallTracker.this.mPendingMO != null) {
                        ImsPhoneCallTracker.this.sendEmptyMessage(20);
                    }
                }
            }
        }

        public void onCallHoldFailed(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.log("onCallHoldFailed reasonCode=" + reasonInfo.getCode());
            synchronized (ImsPhoneCallTracker.this.mSyncHold) {
                Call.State bgState = ImsPhoneCallTracker.this.mBackgroundCall.getState();
                if (reasonInfo.getCode() == 148) {
                    if (ImsPhoneCallTracker.this.mPendingMO != null) {
                        ImsPhoneCallTracker.this.sendEmptyMessage(20);
                    }
                } else if (bgState == Call.State.ACTIVE) {
                    ImsPhoneCallTracker.this.mForegroundCall.switchWith(ImsPhoneCallTracker.this.mBackgroundCall);
                    if (ImsPhoneCallTracker.this.mPendingMO != null) {
                        ImsPhoneCallTracker.this.mPendingMO.setDisconnectCause(36);
                        ImsPhoneCallTracker.this.sendEmptyMessageDelayed(18, 500L);
                    }
                }
            }
            ImsPhoneCallTracker.this.mPhone.notifySuppServiceFailed(Phone.SuppService.SWITCH);
        }

        public void onCallResumed(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallResumed");
            ImsPhoneCallTracker.this.switchAfterConferenceSuccess();
            ImsPhoneCallTracker.this.processCallStateChange(imsCall, Call.State.ACTIVE, 0);
        }

        public void onCallResumeFailed(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.mPhone.notifySuppServiceFailed(Phone.SuppService.SWITCH);
        }

        public void onCallResumeReceived(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallResumeReceived");
            if (ImsPhoneCallTracker.this.mOnHoldToneStarted) {
                ImsPhoneCallTracker.this.mPhone.stopOnHoldTone();
                ImsPhoneCallTracker.this.mOnHoldToneStarted = false;
            }
        }

        public void onCallHoldReceived(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallHoldReceived");
            ImsPhoneConnection conn = ImsPhoneCallTracker.this.findConnection(imsCall);
            if (conn != null && conn.getState() == Call.State.ACTIVE && !ImsPhoneCallTracker.this.mOnHoldToneStarted && ImsPhoneCall.isLocalTone(imsCall)) {
                ImsPhoneCallTracker.this.mPhone.startOnHoldTone();
                ImsPhoneCallTracker.this.mOnHoldToneStarted = true;
            }
        }

        public void onCallSuppServiceReceived(ImsCall call, ImsSuppServiceNotification suppServiceInfo) {
            ImsPhoneCallTracker.this.log("onCallSuppServiceReceived: suppServiceInfo=" + suppServiceInfo);
            SuppServiceNotification supp = new SuppServiceNotification();
            supp.notificationType = suppServiceInfo.notificationType;
            supp.code = suppServiceInfo.code;
            supp.index = suppServiceInfo.index;
            supp.number = suppServiceInfo.number;
            supp.history = suppServiceInfo.history;
            ImsPhoneCallTracker.this.mPhone.notifySuppSvcNotification(supp);
        }

        public void onCallMerged(final ImsCall call) {
            ImsPhoneCallTracker.this.log("onCallMerged");
            ImsPhoneCallTracker.this.mForegroundCall.merge(ImsPhoneCallTracker.this.mBackgroundCall, ImsPhoneCallTracker.this.mForegroundCall.getState(), ImsPhoneCallTracker.this.mConferenceTime);
            ImsPhoneCallTracker.this.mConferenceTime = 0L;
            ImsPhoneCallTracker.this.post(new Runnable() { // from class: com.android.internal.telephony.imsphone.ImsPhoneCallTracker.3.1
                @Override // java.lang.Runnable
                public void run() {
                    try {
                        ImsPhoneConnection conn = ImsPhoneCallTracker.this.findConnection(call);
                        ImsPhoneCallTracker.this.log("onCallMerged: ImsPhoneConnection=" + conn);
                        ImsPhoneCallTracker.this.log("onCallMerged: mConferenceTime=" + ImsPhoneCallTracker.this.mConferenceTime);
                        ImsPhoneCallTracker.this.log("onCallMerged: CurrentVideoProvider=" + conn.getVideoProvider());
                        ImsPhoneCallTracker.this.setVideoCallProvider(conn, call);
                        ImsPhoneCallTracker.this.log("onCallMerged: CurrentVideoProvider=" + conn.getVideoProvider());
                    } catch (Exception e) {
                        ImsPhoneCallTracker.this.loge("onCallMerged: exception " + e);
                    }
                }
            });
            ImsPhoneCallTracker.this.processCallStateChange(call, Call.State.ACTIVE, 0, true);
        }

        public void onCallMergeFailed(ImsCall call, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.log("onCallMergeFailed reasonInfo=" + reasonInfo);
            ImsPhoneCallTracker.this.mPhone.notifySuppServiceFailed(Phone.SuppService.CONFERENCE);
        }

        public void onConferenceParticipantsStateChanged(ImsCall call, List<ConferenceParticipant> participants) {
            ImsPhoneCallTracker.this.log("onConferenceParticipantsStateChanged");
            ImsPhoneConnection conn = ImsPhoneCallTracker.this.findConnection(call);
            if (conn != null) {
                conn.updateConferenceParticipants(participants);
            }
        }

        public void onCallHandover(ImsCall imsCall, int srcAccessTech, int targetAccessTech, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.log("onCallHandover ::  srcAccessTech=" + srcAccessTech + ", targetAccessTech=" + targetAccessTech + ", reasonInfo=" + reasonInfo);
        }

        public void onCallHandoverFailed(ImsCall imsCall, int srcAccessTech, int targetAccessTech, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.log("onCallHandoverFailed :: srcAccessTech=" + srcAccessTech + ", targetAccessTech=" + targetAccessTech + ", reasonInfo=" + reasonInfo);
        }

        public void onCallSessionTtyModeReceived(ImsCall call, int mode) {
            ImsPhoneCallTracker.this.mPhone.onTtyModeReceived(mode);
        }

        public void onCallInviteParticipantsRequestFailed(ImsCall call, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.log("onCallInviteParticipantsRequestFailed ::  call=" + call.toString());
            ImsPhoneCallTracker.this.log("onCallInviteParticipantsRequestFailed ::  reasonInfo.mExtraMessage=" + reasonInfo.getExtraMessage());
            Intent intent = new Intent("jp.co.sharp.android.incalui.action.ADD_VP_MEMBER_FAILED");
            intent.putExtra(IccProvider.STR_NUMBER, reasonInfo.getExtraMessage());
            ImsPhoneCallTracker.this.mPhone.getContext().sendBroadcast(intent);
        }
    };
    private ImsCall.Listener mImsUssdListener = new ImsCall.Listener() { // from class: com.android.internal.telephony.imsphone.ImsPhoneCallTracker.4
        public void onCallStarted(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("mImsUssdListener onCallStarted");
            if (imsCall == ImsPhoneCallTracker.this.mUssdSession && ImsPhoneCallTracker.this.mPendingUssd != null) {
                AsyncResult.forMessage(ImsPhoneCallTracker.this.mPendingUssd);
                ImsPhoneCallTracker.this.mPendingUssd.sendToTarget();
                ImsPhoneCallTracker.this.mPendingUssd = null;
            }
        }

        public void onCallStartFailed(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.log("mImsUssdListener onCallStartFailed reasonCode=" + reasonInfo.getCode());
            onCallTerminated(imsCall, reasonInfo);
        }

        public void onCallTerminated(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            ImsPhoneCallTracker.this.log("mImsUssdListener onCallTerminated reasonCode=" + reasonInfo.getCode());
            if (imsCall == ImsPhoneCallTracker.this.mUssdSession) {
                ImsPhoneCallTracker.this.mUssdSession = null;
                if (ImsPhoneCallTracker.this.mPendingUssd != null) {
                    AsyncResult.forMessage(ImsPhoneCallTracker.this.mPendingUssd, (Object) null, new CommandException(CommandException.Error.GENERIC_FAILURE));
                    ImsPhoneCallTracker.this.mPendingUssd.sendToTarget();
                    ImsPhoneCallTracker.this.mPendingUssd = null;
                }
            }
            imsCall.close();
        }

        public void onCallUssdMessageReceived(ImsCall call, int mode, String ussdMessage) {
            ImsPhoneCallTracker.this.log("mImsUssdListener onCallUssdMessageReceived mode=" + mode);
            int ussdMode = -1;
            switch (mode) {
                case 0:
                    ussdMode = 0;
                    break;
                case 1:
                    ussdMode = 1;
                    break;
            }
            ImsPhoneCallTracker.this.mPhone.onIncomingUSSD(ussdMode, ussdMessage);
        }
    };
    private ImsConnectionStateListener mImsConnectionStateListener = new ImsConnectionStateListener() { // from class: com.android.internal.telephony.imsphone.ImsPhoneCallTracker.5
        public void onImsConnected() {
            ImsPhoneCallTracker.this.log("onImsConnected");
            ImsPhoneCallTracker.this.mPhone.setServiceState(0);
            ImsPhoneCallTracker.this.mPhone.setImsRegistered(true);
        }

        public void onImsDisconnected(ImsReasonInfo imsReasonInfo) {
            ImsPhoneCallTracker.this.log("onImsDisconnected imsReasonInfo=" + imsReasonInfo);
            ImsPhoneCallTracker.this.mPhone.setServiceState(1);
        }

        public void onImsProgressing() {
            ImsPhoneCallTracker.this.log("onImsProgressing");
            ImsPhoneCallTracker.this.mPhone.setServiceState(1);
            ImsPhoneCallTracker.this.mPhone.setImsRegistered(false);
        }

        public void onImsResumed() {
            ImsPhoneCallTracker.this.log("onImsResumed");
            ImsPhoneCallTracker.this.mPhone.setServiceState(0);
        }

        public void onImsSuspended() {
            ImsPhoneCallTracker.this.log("onImsSuspended");
            ImsPhoneCallTracker.this.mPhone.setServiceState(1);
        }

        public void onFeatureCapabilityChanged(int serviceClass, int[] enabledFeatures, int[] disabledFeatures) {
            if (serviceClass == 1) {
                boolean tmpIsVtEnabled = ImsPhoneCallTracker.this.mIsVtEnabled;
                if (disabledFeatures[0] == 0 || disabledFeatures[2] == 2) {
                    ImsPhoneCallTracker.this.mIsVolteEnabled = false;
                }
                if (disabledFeatures[1] == 1 || disabledFeatures[3] == 3) {
                    ImsPhoneCallTracker.this.mIsVtEnabled = false;
                }
                if (disabledFeatures[4] == 4 || disabledFeatures[5] == 5) {
                    ImsPhoneCallTracker.this.mIsUtEnabled = false;
                }
                if (enabledFeatures[0] == 0 || enabledFeatures[2] == 2) {
                    ImsPhoneCallTracker.this.mIsVolteEnabled = true;
                    ImsPhoneCallTracker.this.mIsSrvccCompleted = false;
                }
                if (enabledFeatures[1] == 1 || enabledFeatures[3] == 3) {
                    ImsPhoneCallTracker.this.mIsVtEnabled = true;
                    ImsPhoneCallTracker.this.mIsSrvccCompleted = false;
                }
                if (enabledFeatures[4] == 4 || enabledFeatures[5] == 5) {
                    ImsPhoneCallTracker.this.mIsUtEnabled = true;
                }
                if (tmpIsVtEnabled != ImsPhoneCallTracker.this.mIsVtEnabled) {
                    ImsPhoneCallTracker.this.mPhone.notifyForVideoCapabilityChanged(ImsPhoneCallTracker.this.mIsVtEnabled);
                }
            }
            ImsPhoneCallTracker.this.log("onFeatureCapabilityChanged, mIsVolteEnabled = " + ImsPhoneCallTracker.this.mIsVolteEnabled + " mIsVtEnabled = " + ImsPhoneCallTracker.this.mIsVtEnabled + "mIsUtEnabled = " + ImsPhoneCallTracker.this.mIsUtEnabled);
        }

        public void onVoiceMessageCountChanged(int count) {
            ImsPhoneCallTracker.this.log("onVoiceMessageCountChanged :: count=" + count);
            ImsPhoneCallTracker.this.mPhone.mDefaultPhone.setVoiceMessageCount(count);
        }
    };

    public Call.State convertIntToCallState(int state) {
        switch (state) {
            case 1:
                return Call.State.ACTIVE;
            case 2:
                return Call.State.HOLDING;
            case 3:
                return Call.State.DIALING;
            case 4:
                return Call.State.ALERTING;
            case 5:
                return Call.State.INCOMING;
            case 6:
                return Call.State.WAITING;
            default:
                log("convertIntToCallState: illegal call state:" + state);
                return Call.State.INCOMING;
        }
    }

    public ImsPhoneCallTracker(ImsPhone phone) {
        this.mPhone = phone;
        IntentFilter intentfilter = new IntentFilter();
        intentfilter.addAction("com.android.ims.IMS_INCOMING_CALL");
        this.mPhone.getContext().registerReceiver(this.mReceiver, intentfilter);
        new Thread() { // from class: com.android.internal.telephony.imsphone.ImsPhoneCallTracker.2
            @Override // java.lang.Thread, java.lang.Runnable
            public void run() {
                ImsPhoneCallTracker.this.getImsService();
            }
        }.start();
    }

    private PendingIntent createIncomingCallPendingIntent() {
        Intent intent = new Intent("com.android.ims.IMS_INCOMING_CALL");
        intent.addFlags(268435456);
        return PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
    }

    public void getImsService() {
        log("getImsService");
        this.mImsManager = ImsManager.getInstance(this.mPhone.getContext(), this.mPhone.getPhoneId());
        try {
            this.mServiceId = this.mImsManager.open(1, createIncomingCallPendingIntent(), this.mImsConnectionStateListener);
            getEcbmInterface().setEcbmStateListener(this.mPhone.mImsEcbmStateListener);
            if (this.mPhone.isInEcm()) {
                this.mPhone.exitEmergencyCallbackMode();
            }
            this.mImsManager.setUiTTYMode(this.mPhone.getContext(), this.mServiceId, Settings.Secure.getInt(this.mPhone.getContext().getContentResolver(), "preferred_tty_mode", 0), (Message) null);
        } catch (ImsException e) {
            loge("getImsService: " + e);
            this.mImsManager = null;
        }
    }

    public void dispose() {
        log("dispose");
        this.mRingingCall.dispose();
        this.mBackgroundCall.dispose();
        this.mForegroundCall.dispose();
        this.mHandoverCall.dispose();
        clearDisconnected();
        this.mPhone.getContext().unregisterReceiver(this.mReceiver);
    }

    protected void finalize() {
        log("ImsPhoneCallTracker finalized");
    }

    @Override // com.android.internal.telephony.CallTracker
    public void registerForVoiceCallStarted(Handler h, int what, Object obj) {
        this.mVoiceCallStartedRegistrants.add(new Registrant(h, what, obj));
    }

    @Override // com.android.internal.telephony.CallTracker
    public void unregisterForVoiceCallStarted(Handler h) {
        this.mVoiceCallStartedRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.CallTracker
    public void registerForVoiceCallEnded(Handler h, int what, Object obj) {
        this.mVoiceCallEndedRegistrants.add(new Registrant(h, what, obj));
    }

    @Override // com.android.internal.telephony.CallTracker
    public void unregisterForVoiceCallEnded(Handler h) {
        this.mVoiceCallEndedRegistrants.remove(h);
    }

    Connection dial(String dialString, int videoState) throws CallStateException {
        return dial(dialString, videoState, null);
    }

    public Connection dial(String dialString, int videoState, Bundle extras) throws CallStateException {
        return dial(dialString, PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext()).getInt(PhoneBase.CLIR_KEY, 0), videoState, extras);
    }

    public synchronized Connection dial(String dialString, int clirMode, int videoState, Bundle extras) throws CallStateException {
        boolean isPhoneInEcmMode = SystemProperties.getBoolean("ril.cdma.inecmmode", false);
        boolean isEmergencyNumber = PhoneNumberUtils.isEmergencyNumber(dialString);
        log("dial clirMode=" + clirMode);
        clearDisconnected();
        if (this.mImsManager == null) {
            throw new CallStateException("service not available");
        } else if (!canDial()) {
            throw new CallStateException("cannot dial in current state");
        } else {
            if (isPhoneInEcmMode && isEmergencyNumber) {
                handleEcmTimer(1);
            }
            boolean holdBeforeDial = false;
            if (this.mForegroundCall.getState() == Call.State.ACTIVE) {
                if (this.mBackgroundCall.getState() != Call.State.IDLE) {
                    throw new CallStateException("cannot dial in current state");
                }
                holdBeforeDial = true;
                this.mPendingCallVideoState = videoState;
                switchWaitingOrHoldingAndActive();
            }
            Call.State state = Call.State.IDLE;
            Call.State state2 = Call.State.IDLE;
            this.mClirMode = clirMode;
            synchronized (this.mSyncHold) {
                if (holdBeforeDial) {
                    Call.State fgState = this.mForegroundCall.getState();
                    Call.State bgState = this.mBackgroundCall.getState();
                    if (fgState == Call.State.ACTIVE) {
                        throw new CallStateException("cannot dial in current state");
                    } else if (bgState == Call.State.HOLDING) {
                        holdBeforeDial = false;
                    }
                }
                this.mPendingMO = new ImsPhoneConnection(this.mPhone.getContext(), checkForTestEmergencyNumber(dialString), this, this.mForegroundCall, extras);
            }
            addConnection(this.mPendingMO);
            if (!holdBeforeDial) {
                if (!isPhoneInEcmMode || (isPhoneInEcmMode && isEmergencyNumber)) {
                    if (TelBrand.IS_KDDI) {
                        String outgoingNumber = this.mPendingMO.getNumber();
                        if (!PhoneNumberUtils.isEmergencyNumber(outgoingNumber)) {
                            log("dial() add the special number");
                            try {
                                ITelephony telephony = ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
                                if (telephony != null) {
                                    outgoingNumber = telephony.addSpecialNumber(outgoingNumber);
                                } else {
                                    log("dial() ITelephony is null!");
                                }
                            } catch (RemoteException e) {
                                log("dial() ITelephony is exception(" + e + ")");
                            }
                        } else {
                            log("dial() not add the special number");
                            outgoingNumber = this.mPendingMO.getNumber();
                        }
                        this.mPendingMO.setConverted(outgoingNumber);
                    }
                    dialInternal(this.mPendingMO, clirMode, videoState, extras);
                } else {
                    try {
                        getEcbmInterface().exitEmergencyCallbackMode();
                        this.mPhone.setOnEcbModeExitResponse(this, 14, null);
                        this.pendingCallClirMode = clirMode;
                        this.mPendingCallVideoState = videoState;
                        this.pendingCallInEcm = true;
                    } catch (ImsException e2) {
                        e2.printStackTrace();
                        throw new CallStateException("service not available");
                    }
                }
            }
            updatePhoneState();
            this.mPhone.notifyPreciseCallStateChanged();
        }
        return this.mPendingMO;
    }

    public void addParticipant(String dialString) throws CallStateException {
        if (this.mForegroundCall != null) {
            ImsCall imsCall = this.mForegroundCall.getImsCall();
            if (imsCall == null) {
                loge("addParticipant : No foreground ims call");
                return;
            }
            ImsCallSession imsCallSession = imsCall.getCallSession();
            if (imsCallSession != null) {
                imsCallSession.inviteParticipants(new String[]{dialString});
            } else {
                loge("addParticipant : ImsCallSession does not exist");
            }
        } else {
            loge("addParticipant : Foreground call does not exist");
        }
    }

    private void handleEcmTimer(int action) {
        this.mPhone.handleTimerInEmergencyCallbackMode(action);
        switch (action) {
            case 0:
            case 1:
                return;
            default:
                log("handleEcmTimer, unsupported action " + action);
                return;
        }
    }

    private void dialInternal(ImsPhoneConnection conn, int clirMode, int videoState, Bundle extras) {
        if (conn != null) {
            boolean isConferenceUri = false;
            boolean isSkipSchemaParsing = false;
            if (extras != null) {
                isConferenceUri = extras.getBoolean("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
                isSkipSchemaParsing = extras.getBoolean("org.codeaurora.extra.SKIP_SCHEMA_PARSING", false);
            }
            if (isConferenceUri || isSkipSchemaParsing || !(conn.getAddress() == null || conn.getAddress().length() == 0 || conn.getAddress().indexOf(78) >= 0)) {
                setMute(false);
                int serviceType = PhoneNumberUtils.isEmergencyNumber(conn.getAddress()) ? 2 : 1;
                int callType = ImsCallProfile.getCallTypeFromVideoState(videoState);
                conn.setVideoState(videoState);
                try {
                    String[] callees = !TelBrand.IS_DCM ? new String[]{conn.getAddress()} : new String[]{conn.getNumber()};
                    ImsCallProfile profile = this.mImsManager.createCallProfile(this.mServiceId, serviceType, callType);
                    profile.setCallExtraInt("oir", clirMode);
                    profile.setCallExtraBoolean("isConferenceUri", isConferenceUri);
                    if (extras != null) {
                        profile.mCallExtras.putBundle("OemCallExtras", extras);
                        log("Packing OEM extras bundle in call profile.");
                    } else {
                        log("No dial extras packed in call profile.");
                    }
                    ImsCall imsCall = this.mImsManager.makeCall(this.mServiceId, profile, callees, this.mImsCallListener);
                    conn.setImsCall(imsCall);
                    setVideoCallProvider(conn, imsCall);
                } catch (RemoteException e) {
                } catch (ImsException e2) {
                    loge("dialInternal : " + e2);
                    conn.setDisconnectCause(36);
                    sendEmptyMessageDelayed(18, 500L);
                }
            } else {
                conn.setDisconnectCause(7);
                sendEmptyMessageDelayed(18, 500L);
            }
        }
    }

    public void acceptCall(int videoState) throws CallStateException {
        log("acceptCall");
        if (this.mForegroundCall.getState().isAlive() && this.mBackgroundCall.getState().isAlive()) {
            throw new CallStateException("cannot accept call");
        } else if (this.mRingingCall.getState() == Call.State.WAITING && this.mForegroundCall.getState().isAlive()) {
            setMute(false);
            this.mPendingCallVideoState = videoState;
            switchWaitingOrHoldingAndActive();
        } else if (this.mRingingCall.getState().isRinging()) {
            log("acceptCall: incoming...");
            setMute(false);
            try {
                ImsCall imsCall = this.mRingingCall.getImsCall();
                if (imsCall != null) {
                    imsCall.accept(ImsCallProfile.getCallTypeFromVideoState(videoState));
                    return;
                }
                throw new CallStateException("no valid ims call");
            } catch (ImsException e) {
                throw new CallStateException("cannot accept call");
            }
        } else {
            throw new CallStateException("phone not ringing");
        }
    }

    public void deflectCall(String number) throws CallStateException {
        log("deflectCall");
        if (this.mRingingCall.getState().isRinging()) {
            try {
                ImsCall imsCall = this.mRingingCall.getImsCall();
                if (imsCall != null) {
                    imsCall.deflect(number);
                    return;
                }
                throw new CallStateException("no valid ims call to deflect");
            } catch (ImsException e) {
                throw new CallStateException("cannot deflect call");
            }
        } else {
            throw new CallStateException("phone not ringing");
        }
    }

    public void rejectCall() throws CallStateException {
        log("rejectCall");
        if (this.mRingingCall.getState().isRinging()) {
            hangup(this.mRingingCall);
            return;
        }
        throw new CallStateException("phone not ringing");
    }

    public void switchWaitingOrHoldingAndActive() throws CallStateException {
        log("switchWaitingOrHoldingAndActive");
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            throw new CallStateException("cannot be in the incoming state");
        } else if (this.mForegroundCall.getState() == Call.State.ACTIVE) {
            ImsCall imsCall = this.mForegroundCall.getImsCall();
            if (imsCall == null) {
                throw new CallStateException("no ims call");
            }
            this.mForegroundCall.switchWith(this.mBackgroundCall);
            try {
                imsCall.hold();
            } catch (ImsException e) {
                this.mForegroundCall.switchWith(this.mBackgroundCall);
                throw new CallStateException(e.getMessage());
            }
        } else if (this.mBackgroundCall.getState() == Call.State.HOLDING) {
            resumeWaitingOrHolding();
        } else if (this.mForegroundCall.getState() == Call.State.HOLDING) {
            ImsCall imsErrCall = this.mForegroundCall.getImsCall();
            if (imsErrCall == null) {
                throw new CallStateException("no ims call");
            }
            try {
                imsErrCall.resume();
            } catch (ImsException e2) {
                throw new CallStateException(e2.getMessage());
            }
        }
    }

    public void conference() {
        log("conference");
        ImsCall fgImsCall = this.mForegroundCall.getImsCall();
        if (fgImsCall == null) {
            log("conference no foreground ims call");
            return;
        }
        ImsCall bgImsCall = this.mBackgroundCall.getImsCall();
        if (bgImsCall == null) {
            log("conference no background ims call");
            return;
        }
        this.mConferenceTime = Math.min(this.mBackgroundCall.getEarliestConnectTime(), this.mForegroundCall.getEarliestConnectTime());
        try {
            fgImsCall.merge(bgImsCall);
        } catch (ImsException e) {
            log("conference " + e.getMessage());
        }
    }

    public void explicitCallTransfer() {
    }

    public void clearDisconnected() {
        log("clearDisconnected");
        internalClearDisconnected();
        updatePhoneState();
        this.mPhone.notifyPreciseCallStateChanged();
    }

    public boolean canConference() {
        return this.mForegroundCall.getState() == Call.State.ACTIVE && this.mBackgroundCall.getState() == Call.State.HOLDING && !this.mBackgroundCall.isFull() && !this.mForegroundCall.isFull();
    }

    public boolean canDial() {
        boolean z;
        int serviceState = this.mPhone.getServiceState().getState();
        String disableCall = SystemProperties.get("ro.telephony.disable-call", "false");
        StringBuilder append = new StringBuilder().append("canDial(): \nserviceState = ").append(serviceState).append("\npendingMO == null::=");
        if (this.mPendingMO == null) {
            z = true;
        } else {
            z = false;
        }
        Rlog.d(LOG_TAG, append.append(String.valueOf(z)).append("\nringingCall: ").append(this.mRingingCall.getState()).append("\ndisableCall = ").append(disableCall).append("\nforegndCall: ").append(this.mForegroundCall.getState()).append("\nbackgndCall: ").append(this.mBackgroundCall.getState()).toString());
        return serviceState != 3 && this.mPendingMO == null && !this.mRingingCall.isRinging() && !disableCall.equals("true") && (!this.mForegroundCall.getState().isAlive() || !this.mBackgroundCall.getState().isAlive());
    }

    public boolean canTransfer() {
        return this.mForegroundCall.getState() == Call.State.ACTIVE && this.mBackgroundCall.getState() == Call.State.HOLDING;
    }

    private void internalClearDisconnected() {
        this.mRingingCall.clearDisconnected();
        this.mForegroundCall.clearDisconnected();
        this.mBackgroundCall.clearDisconnected();
        this.mHandoverCall.clearDisconnected();
    }

    public void updatePhoneState() {
        PhoneConstants.State oldState = this.mState;
        if (this.mRingingCall.isRinging()) {
            this.mState = PhoneConstants.State.RINGING;
        } else if (this.mPendingMO != null || !this.mForegroundCall.isIdle() || !this.mBackgroundCall.isIdle()) {
            this.mState = PhoneConstants.State.OFFHOOK;
        } else {
            this.mState = PhoneConstants.State.IDLE;
        }
        if (this.mState == PhoneConstants.State.IDLE && oldState != this.mState) {
            this.mVoiceCallEndedRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Object) null, (Throwable) null));
        } else if (oldState == PhoneConstants.State.IDLE && oldState != this.mState) {
            this.mVoiceCallStartedRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Object) null, (Throwable) null));
        }
        log("updatePhoneState oldState=" + oldState + ", newState=" + this.mState);
        if (this.mState != oldState) {
            this.mPhone.notifyPhoneStateChanged();
        }
    }

    private void handleRadioNotAvailable() {
        pollCallsWhenSafe();
    }

    private void dumpState() {
        log("Phone State:" + this.mState);
        log("Ringing call: " + this.mRingingCall.toString());
        List l = this.mRingingCall.getConnections();
        int s = l.size();
        for (int i = 0; i < s; i++) {
            log(l.get(i).toString());
        }
        log("Foreground call: " + this.mForegroundCall.toString());
        List l2 = this.mForegroundCall.getConnections();
        int s2 = l2.size();
        for (int i2 = 0; i2 < s2; i2++) {
            log(l2.get(i2).toString());
        }
        log("Background call: " + this.mBackgroundCall.toString());
        List l3 = this.mBackgroundCall.getConnections();
        int s3 = l3.size();
        for (int i3 = 0; i3 < s3; i3++) {
            log(l3.get(i3).toString());
        }
    }

    public void setUiTTYMode(int uiTtyMode, Message onComplete) {
        try {
            this.mImsManager.setUiTTYMode(this.mPhone.getContext(), this.mServiceId, uiTtyMode, onComplete);
        } catch (ImsException e) {
            loge("setTTYMode : " + e);
            this.mPhone.sendErrorResponse(onComplete, (Throwable) e);
        }
    }

    public void setMute(boolean mute) {
        this.mDesiredMute = mute;
        this.mForegroundCall.setMute(mute);
    }

    public boolean getMute() {
        return this.mDesiredMute;
    }

    void sendDtmf(char c) {
        log("sendDtmf");
        ImsCall imscall = this.mForegroundCall.getImsCall();
        if (imscall != null) {
            imscall.sendDtmf(c);
        } else {
            loge("sendDtmf : no foreground call");
        }
    }

    public void sendDtmf(char c, Message result) {
        log("sendDtmf");
        ImsCall imscall = this.mForegroundCall.getImsCall();
        if (imscall != null) {
            imscall.sendDtmf(c, result);
        }
    }

    public void startDtmf(char c) {
        log("startDtmf");
        ImsCall imscall = this.mForegroundCall.getImsCall();
        if (imscall != null) {
            imscall.startDtmf(c);
        } else {
            loge("startDtmf : no foreground call");
        }
    }

    public void stopDtmf() {
        log("stopDtmf");
        ImsCall imscall = this.mForegroundCall.getImsCall();
        if (imscall != null) {
            imscall.stopDtmf();
        } else {
            loge("stopDtmf : no foreground call");
        }
    }

    public void hangup(ImsPhoneConnection conn) throws CallStateException {
        log("hangup connection");
        if (conn.getOwner() != this) {
            throw new CallStateException("ImsPhoneConnection " + conn + "does not belong to ImsPhoneCallTracker " + this);
        }
        hangup(conn.getCall());
    }

    public void hangup(ImsPhoneCall call) throws CallStateException {
        log("hangup call");
        if (call.getConnections().size() == 0) {
            throw new CallStateException("no connections");
        }
        ImsCall imsCall = call.getImsCall();
        boolean rejectCall = false;
        if (call == this.mRingingCall) {
            log("(ringing) hangup incoming");
            rejectCall = true;
        } else if (call == this.mForegroundCall) {
            if (call.isDialingOrAlerting()) {
                log("(foregnd) hangup dialing or alerting...");
            } else {
                log("(foregnd) hangup foreground");
            }
        } else if (call == this.mBackgroundCall) {
            log("(backgnd) hangup waiting or background");
        } else {
            throw new CallStateException("ImsPhoneCall " + call + "does not belong to ImsPhoneCallTracker " + this);
        }
        call.onHangupLocal();
        try {
            if (imsCall != null) {
                if (!TelBrand.IS_SBM) {
                    if (rejectCall) {
                        imsCall.reject(504);
                    } else {
                        imsCall.terminate(501);
                    }
                } else if (rejectCall) {
                    imsCall.reject(1);
                } else {
                    imsCall.terminate(501);
                }
            } else if (this.mPendingMO != null && call == this.mForegroundCall) {
                this.mPendingMO.update(null, Call.State.DISCONNECTED);
                this.mPendingMO.onDisconnect();
                removeConnection(this.mPendingMO);
                this.mPendingMO = null;
                updatePhoneState();
                removeMessages(20);
            }
            this.mPhone.notifyPreciseCallStateChanged();
        } catch (ImsException e) {
            throw new CallStateException(e.getMessage());
        }
    }

    public void declineCall() throws CallStateException {
        if (this.mRingingCall.getState().isRinging()) {
            ImsCall imsCall = this.mRingingCall.getImsCall();
            if (imsCall != null) {
                log("declineCall() is called to decline(603) ringing-call.");
                try {
                    imsCall.reject(603);
                    this.mRingingCall.onHangupLocal();
                    this.mPhone.notifyPreciseCallStateChanged();
                } catch (ImsException e) {
                    throw new CallStateException(e.getMessage());
                }
            } else {
                Rlog.e(LOG_TAG, "[ImsCallTracker] declineCall() is called, but ImsCall of ringingCall is null.");
            }
        } else {
            Rlog.e(LOG_TAG, "[ImsCallTracker] declineCall() is called, but there is no ringing-call.");
            throw new CallStateException("phone not ringing");
        }
    }

    public void switchAfterConferenceSuccess() {
        log("switchAfterConferenceSuccess fg =" + this.mForegroundCall.getState() + ", bg = " + this.mBackgroundCall.getState());
        if (!this.mForegroundCall.getState().isAlive() && this.mBackgroundCall.getState() == Call.State.HOLDING) {
            this.mForegroundCall.switchWith(this.mBackgroundCall);
        }
    }

    void resumeWaitingOrHolding() throws CallStateException {
        log("resumeWaitingOrHolding");
        try {
            if (this.mForegroundCall.getState().isAlive()) {
                ImsCall imsCall = this.mForegroundCall.getImsCall();
                if (imsCall != null) {
                    imsCall.resume();
                }
            } else if (this.mRingingCall.getState() == Call.State.WAITING) {
                ImsCall imsCall2 = this.mRingingCall.getImsCall();
                if (imsCall2 != null) {
                    imsCall2.accept(ImsCallProfile.getCallTypeFromVideoState(this.mPendingCallVideoState));
                }
            } else {
                ImsCall imsCall3 = this.mBackgroundCall.getImsCall();
                if (imsCall3 != null) {
                    imsCall3.resume();
                }
            }
        } catch (ImsException e) {
            throw new CallStateException(e.getMessage());
        }
    }

    public void sendUSSD(String ussdString, Message response) {
        log("sendUSSD");
        try {
            if (this.mUssdSession != null) {
                this.mUssdSession.sendUssd(ussdString);
                AsyncResult.forMessage(response, (Object) null, (Throwable) null);
                response.sendToTarget();
            } else {
                ImsCallProfile profile = this.mImsManager.createCallProfile(this.mServiceId, 1, 2);
                profile.setCallExtraInt("dialstring", 2);
                this.mUssdSession = this.mImsManager.makeCall(this.mServiceId, profile, new String[]{ussdString}, this.mImsUssdListener);
            }
        } catch (ImsException e) {
            loge("sendUSSD : " + e);
            this.mPhone.sendErrorResponse(response, (Throwable) e);
        }
    }

    public void cancelUSSD() {
        if (this.mUssdSession != null) {
            try {
                this.mUssdSession.terminate(501);
            } catch (ImsException e) {
            }
        }
    }

    public synchronized ImsPhoneConnection findConnection(ImsCall imsCall) {
        ImsPhoneConnection conn;
        Iterator i$ = this.mConnections.iterator();
        while (true) {
            if (!i$.hasNext()) {
                conn = null;
                break;
            }
            conn = i$.next();
            if (conn.getImsCall() == imsCall) {
                break;
            }
        }
        return conn;
    }

    public synchronized void removeConnection(ImsPhoneConnection conn) {
        this.mConnections.remove(conn);
    }

    public synchronized void addConnection(ImsPhoneConnection conn) {
        this.mConnections.add(conn);
    }

    public void processCallStateChange(ImsCall imsCall, Call.State state, int cause) {
        processCallStateChange(imsCall, state, cause, false);
    }

    public void processCallStateChange(ImsCall imsCall, Call.State state, int cause, boolean ignoreState) {
        ImsPhoneConnection conn;
        boolean changed;
        log("processCallStateChange state=" + state + " cause=" + cause + " ignoreState=" + ignoreState);
        if (imsCall != null && (conn = findConnection(imsCall)) != null) {
            if (ignoreState) {
                changed = conn.update(imsCall);
            } else {
                changed = conn.update(imsCall, state);
                if (state == Call.State.DISCONNECTED) {
                    changed = conn.onDisconnect(cause) || changed;
                    conn.getCall().detach(conn);
                    removeConnection(conn);
                }
            }
            if (changed && conn.getCall() != this.mHandoverCall) {
                updatePhoneState();
                this.mPhone.notifyPreciseCallStateChanged();
            }
        }
    }

    public int getDisconnectCauseFromReasonInfo(ImsReasonInfo reasonInfo) {
        int code = reasonInfo.getCode();
        switch (code) {
            case 106:
            case 121:
            case 122:
            case 123:
            case 124:
            case 131:
            case 132:
            case 144:
                return 18;
            case 111:
            case 112:
                return 17;
            case 143:
                return 16;
            case 201:
            case 202:
            case 203:
            case 335:
                return 13;
            case 241:
                return 21;
            case 321:
            case 331:
            case 332:
            case 340:
            case 361:
            case 362:
                return 12;
            case 333:
            case 352:
            case 354:
                return 9;
            case 337:
            case 341:
                return 8;
            case 338:
                return 4;
            case 363:
                return 92;
            case 364:
                return 93;
            case 501:
                return 3;
            case 510:
                return 2;
            default:
                if (!TelBrand.IS_SBM) {
                    return 36;
                }
                int serviceState = this.mPhone.getServiceState().getState();
                log("getDisconnectCauseFromReasonInfo(" + code + "...ims service state=" + serviceState);
                if (serviceState == 3) {
                    return 17;
                }
                if (serviceState == 1 || serviceState == 2) {
                    return 18;
                }
                int serviceState2 = this.mPhone.mDefaultPhone.getServiceState().getState();
                log("getDisconnectCauseFromReasonInfo(" + code + "...gsm service state=" + serviceState2);
                if (serviceState2 == 3) {
                    return 17;
                }
                return (serviceState2 == 1 || serviceState2 == 2) ? 18 : 36;
        }
    }

    public ImsUtInterface getUtInterface() throws ImsException {
        if (this.mImsManager != null) {
            return this.mImsManager.getSupplementaryServiceConfiguration(this.mServiceId);
        }
        throw new ImsException("no ims manager", 0);
    }

    private void transferHandoverConnections(ImsPhoneCall call) {
        if (call.mConnections != null) {
            Iterator i$ = call.mConnections.iterator();
            while (i$.hasNext()) {
                Connection c = (Connection) i$.next();
                c.mPreHandoverState = call.mState;
                log("Connection state before handover is " + c.getStateBeforeHandover());
            }
        }
        if (this.mHandoverCall.mConnections == null) {
            this.mHandoverCall.mConnections = call.mConnections;
        } else {
            this.mHandoverCall.mConnections.addAll(call.mConnections);
        }
        if (this.mHandoverCall.mConnections != null) {
            if (call.getImsCall() != null) {
                call.getImsCall().close();
            }
            Iterator i$2 = this.mHandoverCall.mConnections.iterator();
            while (i$2.hasNext()) {
                Connection c2 = (Connection) i$2.next();
                ((ImsPhoneConnection) c2).changeParent(this.mHandoverCall);
                ((ImsPhoneConnection) c2).releaseWakeLock();
            }
        }
        if (call.getState().isAlive()) {
            log("Call is alive and state is " + call.mState);
            this.mHandoverCall.mState = call.mState;
        }
        call.mConnections.clear();
        call.mState = Call.State.IDLE;
    }

    public void notifySrvccState(Call.SrvccState state) {
        log("notifySrvccState state=" + state);
        this.mSrvccState = state;
        if (this.mSrvccState == Call.SrvccState.COMPLETED) {
            transferHandoverConnections(this.mForegroundCall);
            transferHandoverConnections(this.mBackgroundCall);
            transferHandoverConnections(this.mRingingCall);
            if (this.mPendingMO != null) {
                log("notifySrvccState()...mPendingMO=null");
                this.mPendingMO = null;
            }
            this.mIsSrvccCompleted = true;
            this.mState = PhoneConstants.State.IDLE;
        }
    }

    @Override // com.android.internal.telephony.CallTracker, android.os.Handler
    public void handleMessage(Message msg) {
        log("handleMessage what=" + msg.what);
        switch (msg.what) {
            case 14:
                if (this.pendingCallInEcm) {
                    dialInternal(this.mPendingMO, this.pendingCallClirMode, this.mPendingCallVideoState, this.mPendingMO.getCallExtras());
                    this.pendingCallInEcm = false;
                }
                this.mPhone.unsetOnEcbModeExitResponse(this);
                return;
            case 15:
            case 16:
            case 17:
            default:
                return;
            case 18:
                if (this.mPendingMO != null) {
                    this.mPendingMO = null;
                }
                updatePhoneState();
                this.mPhone.notifyPreciseCallStateChanged();
                return;
            case 19:
                try {
                    resumeWaitingOrHolding();
                    return;
                } catch (CallStateException e) {
                    loge("handleMessage EVENT_RESUME_BACKGROUND exception=" + e);
                    return;
                }
            case 20:
                dialInternal(this.mPendingMO, this.mClirMode, this.mPendingCallVideoState, this.mPendingMO.getCallExtras());
                return;
        }
    }

    @Override // com.android.internal.telephony.CallTracker
    protected void log(String msg) {
        Rlog.d(LOG_TAG, "[ImsPhoneCallTracker] " + msg);
    }

    protected void loge(String msg) {
        Rlog.e(LOG_TAG, "[ImsPhoneCallTracker] " + msg);
    }

    @Override // com.android.internal.telephony.CallTracker
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("ImsPhoneCallTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mVoiceCallEndedRegistrants=" + this.mVoiceCallEndedRegistrants);
        pw.println(" mVoiceCallStartedRegistrants=" + this.mVoiceCallStartedRegistrants);
        pw.println(" mRingingCall=" + this.mRingingCall);
        pw.println(" mForegroundCall=" + this.mForegroundCall);
        pw.println(" mBackgroundCall=" + this.mBackgroundCall);
        pw.println(" mHandoverCall=" + this.mHandoverCall);
        pw.println(" mPendingMO=" + this.mPendingMO);
        pw.println(" mPhone=" + this.mPhone);
        pw.println(" mDesiredMute=" + this.mDesiredMute);
        pw.println(" mState=" + this.mState);
    }

    @Override // com.android.internal.telephony.CallTracker
    protected void handlePollCalls(AsyncResult ar) {
    }

    public ImsEcbm getEcbmInterface() throws ImsException {
        if (this.mImsManager != null) {
            return this.mImsManager.getEcbmInterface(this.mServiceId);
        }
        throw new ImsException("no ims manager", 0);
    }

    public boolean isInEmergencyCall() {
        return this.mIsInEmergencyCall;
    }

    public boolean isVolteEnabled() {
        return this.mIsVolteEnabled;
    }

    public boolean isVtEnabled() {
        return this.mIsVtEnabled;
    }

    public void setVideoCallProvider(ImsPhoneConnection conn, ImsCall imsCall) throws RemoteException {
        IImsVideoCallProvider imsVideoCallProvider = imsCall.getCallSession().getVideoCallProvider();
        if (imsVideoCallProvider != null) {
            conn.setVideoProvider(new ImsVideoCallProviderWrapper(imsVideoCallProvider));
        }
    }

    public boolean isUtEnabled() {
        return this.mIsUtEnabled;
    }

    @Override // com.android.internal.telephony.CallTracker
    public PhoneConstants.State getState() {
        return this.mState;
    }
}
