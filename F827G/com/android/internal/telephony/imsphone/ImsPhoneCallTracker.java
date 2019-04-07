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
import android.provider.Settings.Secure;
import android.telecom.ConferenceParticipant;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import com.android.ims.ImsCall;
import com.android.ims.ImsCall.Listener;
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
import com.android.internal.telephony.Call.SrvccState;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.ITelephony.Stub;
import com.android.internal.telephony.IccProvider;
import com.android.internal.telephony.Phone.SuppService;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ImsPhoneCallTracker extends CallTracker {
    private static final boolean DBG = true;
    private static final int EVENT_DIAL_PENDINGMO = 20;
    private static final int EVENT_HANGUP_PENDINGMO = 18;
    private static final int EVENT_RESUME_BACKGROUND = 19;
    static final String LOG_TAG = "ImsPhoneCallTracker";
    static final int MAX_CONNECTIONS = 7;
    static final int MAX_CONNECTIONS_PER_CALL = 5;
    private static final int TIMEOUT_HANGUP_PENDINGMO = 500;
    ImsPhoneCall mBackgroundCall = new ImsPhoneCall(this);
    private int mClirMode = 0;
    private long mConferenceTime = 0;
    private ArrayList<ImsPhoneConnection> mConnections = new ArrayList();
    private boolean mDesiredMute = false;
    ImsPhoneCall mForegroundCall = new ImsPhoneCall(this);
    ImsPhoneCall mHandoverCall = new ImsPhoneCall(this);
    private Listener mImsCallListener = new Listener() {
        public void onCallHandover(ImsCall imsCall, int i, int i2, ImsReasonInfo imsReasonInfo) {
            ImsPhoneCallTracker.this.log("onCallHandover ::  srcAccessTech=" + i + ", targetAccessTech=" + i2 + ", reasonInfo=" + imsReasonInfo);
        }

        public void onCallHandoverFailed(ImsCall imsCall, int i, int i2, ImsReasonInfo imsReasonInfo) {
            ImsPhoneCallTracker.this.log("onCallHandoverFailed :: srcAccessTech=" + i + ", targetAccessTech=" + i2 + ", reasonInfo=" + imsReasonInfo);
        }

        public void onCallHeld(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallHeld");
            synchronized (ImsPhoneCallTracker.this.mSyncHold) {
                State state = ImsPhoneCallTracker.this.mBackgroundCall.getState();
                ImsPhoneCallTracker.this.processCallStateChange(imsCall, State.HOLDING, 0);
                if (state == State.ACTIVE) {
                    if (ImsPhoneCallTracker.this.mForegroundCall.getState() == State.HOLDING || ImsPhoneCallTracker.this.mRingingCall.getState() == State.WAITING) {
                        boolean z = true;
                        CallGroup callGroup = imsCall.getCallGroup();
                        if (callGroup != null) {
                            z = callGroup.isOwner(imsCall);
                        }
                        if (z) {
                            ImsPhoneCallTracker.this.sendEmptyMessage(19);
                        }
                    } else if (ImsPhoneCallTracker.this.mPendingMO != null) {
                        ImsPhoneCallTracker.this.sendEmptyMessage(20);
                    }
                }
            }
        }

        public void onCallHoldFailed(ImsCall imsCall, ImsReasonInfo imsReasonInfo) {
            ImsPhoneCallTracker.this.log("onCallHoldFailed reasonCode=" + imsReasonInfo.getCode());
            synchronized (ImsPhoneCallTracker.this.mSyncHold) {
                State state = ImsPhoneCallTracker.this.mBackgroundCall.getState();
                if (imsReasonInfo.getCode() == 148) {
                    if (ImsPhoneCallTracker.this.mPendingMO != null) {
                        ImsPhoneCallTracker.this.sendEmptyMessage(20);
                    }
                } else if (state == State.ACTIVE) {
                    ImsPhoneCallTracker.this.mForegroundCall.switchWith(ImsPhoneCallTracker.this.mBackgroundCall);
                    if (ImsPhoneCallTracker.this.mPendingMO != null) {
                        ImsPhoneCallTracker.this.mPendingMO.setDisconnectCause(36);
                        ImsPhoneCallTracker.this.sendEmptyMessageDelayed(18, 500);
                    }
                }
            }
            ImsPhoneCallTracker.this.mPhone.notifySuppServiceFailed(SuppService.SWITCH);
        }

        public void onCallHoldReceived(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallHoldReceived");
            ImsPhoneConnection access$1300 = ImsPhoneCallTracker.this.findConnection(imsCall);
            if (access$1300 != null && access$1300.getState() == State.ACTIVE && !ImsPhoneCallTracker.this.mOnHoldToneStarted && ImsPhoneCall.isLocalTone(imsCall)) {
                ImsPhoneCallTracker.this.mPhone.startOnHoldTone();
                ImsPhoneCallTracker.this.mOnHoldToneStarted = true;
            }
        }

        public void onCallInviteParticipantsRequestFailed(ImsCall imsCall, ImsReasonInfo imsReasonInfo) {
            ImsPhoneCallTracker.this.log("onCallInviteParticipantsRequestFailed ::  call=" + imsCall.toString());
            ImsPhoneCallTracker.this.log("onCallInviteParticipantsRequestFailed ::  reasonInfo.mExtraMessage=" + imsReasonInfo.getExtraMessage());
            Intent intent = new Intent("jp.co.sharp.android.incalui.action.ADD_VP_MEMBER_FAILED");
            intent.putExtra(IccProvider.STR_NUMBER, imsReasonInfo.getExtraMessage());
            ImsPhoneCallTracker.this.mPhone.getContext().sendBroadcast(intent);
        }

        public void onCallMergeFailed(ImsCall imsCall, ImsReasonInfo imsReasonInfo) {
            ImsPhoneCallTracker.this.log("onCallMergeFailed reasonInfo=" + imsReasonInfo);
            ImsPhoneCallTracker.this.mPhone.notifySuppServiceFailed(SuppService.CONFERENCE);
        }

        public void onCallMerged(final ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallMerged");
            ImsPhoneCallTracker.this.mForegroundCall.merge(ImsPhoneCallTracker.this.mBackgroundCall, ImsPhoneCallTracker.this.mForegroundCall.getState(), ImsPhoneCallTracker.this.mConferenceTime);
            ImsPhoneCallTracker.this.mConferenceTime = 0;
            ImsPhoneCallTracker.this.post(new Runnable() {
                public void run() {
                    try {
                        ImsPhoneConnection access$1300 = ImsPhoneCallTracker.this.findConnection(imsCall);
                        ImsPhoneCallTracker.this.log("onCallMerged: ImsPhoneConnection=" + access$1300);
                        ImsPhoneCallTracker.this.log("onCallMerged: mConferenceTime=" + ImsPhoneCallTracker.this.mConferenceTime);
                        ImsPhoneCallTracker.this.log("onCallMerged: CurrentVideoProvider=" + access$1300.getVideoProvider());
                        ImsPhoneCallTracker.this.setVideoCallProvider(access$1300, imsCall);
                        ImsPhoneCallTracker.this.log("onCallMerged: CurrentVideoProvider=" + access$1300.getVideoProvider());
                    } catch (Exception e) {
                        ImsPhoneCallTracker.this.loge("onCallMerged: exception " + e);
                    }
                }
            });
            ImsPhoneCallTracker.this.processCallStateChange(imsCall, State.ACTIVE, 0, true);
        }

        public void onCallProgressing(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallProgressing");
            ImsPhoneCallTracker.this.mPendingMO = null;
            ImsPhoneCallTracker.this.processCallStateChange(imsCall, State.ALERTING, 0);
        }

        public void onCallResumeFailed(ImsCall imsCall, ImsReasonInfo imsReasonInfo) {
            ImsPhoneCallTracker.this.mPhone.notifySuppServiceFailed(SuppService.SWITCH);
        }

        public void onCallResumeReceived(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallResumeReceived");
            if (ImsPhoneCallTracker.this.mOnHoldToneStarted) {
                ImsPhoneCallTracker.this.mPhone.stopOnHoldTone();
                ImsPhoneCallTracker.this.mOnHoldToneStarted = false;
            }
        }

        public void onCallResumed(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallResumed");
            ImsPhoneCallTracker.this.switchAfterConferenceSuccess();
            ImsPhoneCallTracker.this.processCallStateChange(imsCall, State.ACTIVE, 0);
        }

        public void onCallSessionTtyModeReceived(ImsCall imsCall, int i) {
            ImsPhoneCallTracker.this.mPhone.onTtyModeReceived(i);
        }

        public void onCallStartFailed(ImsCall imsCall, ImsReasonInfo imsReasonInfo) {
            ImsPhoneCallTracker.this.log("onCallStartFailed reasonCode=" + imsReasonInfo.getCode());
            if (ImsPhoneCallTracker.this.mPendingMO == null) {
                return;
            }
            if (imsReasonInfo.getCode() == 146 && ImsPhoneCallTracker.this.mBackgroundCall.getState() == State.IDLE && ImsPhoneCallTracker.this.mRingingCall.getState() == State.IDLE) {
                ImsPhoneCallTracker.this.mForegroundCall.detach(ImsPhoneCallTracker.this.mPendingMO);
                ImsPhoneCallTracker.this.removeConnection(ImsPhoneCallTracker.this.mPendingMO);
                ImsPhoneCallTracker.this.mPendingMO.finalize();
                ImsPhoneCallTracker.this.mPendingMO = null;
                ImsPhoneCallTracker.this.mPhone.initiateSilentRedial();
                return;
            }
            ImsPhoneCallTracker.this.processCallStateChange(imsCall, State.DISCONNECTED, ImsPhoneCallTracker.this.getDisconnectCauseFromReasonInfo(imsReasonInfo));
            ImsPhoneCallTracker.this.mPendingMO.setDisconnectCause(36);
            ImsPhoneCallTracker.this.sendEmptyMessageDelayed(18, 500);
        }

        public void onCallStarted(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallStarted");
            ImsPhoneCallTracker.this.mPendingMO = null;
            ImsPhoneCallTracker.this.processCallStateChange(imsCall, State.ACTIVE, 0);
        }

        public void onCallSuppServiceReceived(ImsCall imsCall, ImsSuppServiceNotification imsSuppServiceNotification) {
            ImsPhoneCallTracker.this.log("onCallSuppServiceReceived: suppServiceInfo=" + imsSuppServiceNotification);
            SuppServiceNotification suppServiceNotification = new SuppServiceNotification();
            suppServiceNotification.notificationType = imsSuppServiceNotification.notificationType;
            suppServiceNotification.code = imsSuppServiceNotification.code;
            suppServiceNotification.index = imsSuppServiceNotification.index;
            suppServiceNotification.number = imsSuppServiceNotification.number;
            suppServiceNotification.history = imsSuppServiceNotification.history;
            ImsPhoneCallTracker.this.mPhone.notifySuppSvcNotification(suppServiceNotification);
        }

        public void onCallTerminated(ImsCall imsCall, ImsReasonInfo imsReasonInfo) {
            int i = 16;
            ImsPhoneCallTracker.this.log("onCallTerminated reasonCode=" + imsReasonInfo.getCode());
            State state = ImsPhoneCallTracker.this.mForegroundCall.getState();
            ImsPhoneCallTracker.this.log("onCallTerminated oldState=" + state);
            int access$1600 = ImsPhoneCallTracker.this.getDisconnectCauseFromReasonInfo(imsReasonInfo);
            ImsPhoneConnection access$1300 = ImsPhoneCallTracker.this.findConnection(imsCall);
            ImsPhoneCallTracker.this.log("cause = " + access$1600 + " conn = " + access$1300);
            if (access$1300 == null || !access$1300.isIncoming() || access$1300.getConnectTime() != 0) {
                i = access$1600;
            } else if (access$1600 == 3) {
                access$1300.setDisconnectCause(16);
            } else if (access$1600 == 16) {
                access$1300.setDisconnectCause(access$1600);
                i = access$1600;
            } else {
                i = 1;
            }
            if (i == 2 && access$1300 != null && access$1300.getImsCall().isMerged()) {
                i = 91;
            }
            if (ImsPhoneCallTracker.this.mPendingMO != null) {
                if (ImsPhoneCallTracker.this.mPendingMO.getImsCall() != null) {
                    if (ImsPhoneCallTracker.this.mPendingMO.getImsCall() == imsCall) {
                        ImsPhoneCallTracker.this.log("onCallTerminated() ImsCall is matching, mPendingMO=null");
                        ImsPhoneCallTracker.this.mPendingMO = null;
                    } else {
                        ImsPhoneCallTracker.this.log("onCallTerminated() ImsCall is not matching");
                    }
                } else if (ImsPhoneCallTracker.this.mForegroundCall.getState() != State.ACTIVE) {
                    if (ImsPhoneCallTracker.this.mRingingCall.getState().isRinging()) {
                        ImsPhoneCallTracker.this.log("onCallTerminated() isRinging, mPendingMO=null");
                        ImsPhoneCallTracker.this.mPendingMO.update(null, State.DISCONNECTED);
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
            ImsPhoneCallTracker.this.processCallStateChange(imsCall, State.DISCONNECTED, i);
            if (imsReasonInfo.getCode() == 501 && state == State.DISCONNECTING && ImsPhoneCallTracker.this.mForegroundCall.getState() == State.IDLE && ImsPhoneCallTracker.this.mBackgroundCall.getState() == State.HOLDING) {
                ImsPhoneCallTracker.this.sendEmptyMessage(19);
            }
        }

        public void onCallUpdated(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("onCallUpdated");
            if (imsCall != null) {
                ImsPhoneConnection access$1300 = ImsPhoneCallTracker.this.findConnection(imsCall);
                if (access$1300 != null) {
                    if (ImsPhoneCallTracker.this.mPendingMO != null && access$1300.getCall().mState == State.DIALING) {
                        ImsPhoneCallTracker.this.log("onCallUpdated(DIALING)...mPendingMO=null");
                        ImsPhoneCallTracker.this.mPendingMO = null;
                    }
                    ImsPhoneCallTracker.this.processCallStateChange(imsCall, access$1300.getCall().mState, 0, true);
                }
            }
        }

        public void onConferenceParticipantsStateChanged(ImsCall imsCall, List<ConferenceParticipant> list) {
            ImsPhoneCallTracker.this.log("onConferenceParticipantsStateChanged");
            ImsPhoneConnection access$1300 = ImsPhoneCallTracker.this.findConnection(imsCall);
            if (access$1300 != null) {
                access$1300.updateConferenceParticipants(list);
            }
        }
    };
    private ImsConnectionStateListener mImsConnectionStateListener = new ImsConnectionStateListener() {
        public void onFeatureCapabilityChanged(int i, int[] iArr, int[] iArr2) {
            if (i == 1) {
                boolean access$2200 = ImsPhoneCallTracker.this.mIsVtEnabled;
                if (iArr2[0] == 0 || iArr2[2] == 2) {
                    ImsPhoneCallTracker.this.mIsVolteEnabled = false;
                }
                if (iArr2[1] == 1 || iArr2[3] == 3) {
                    ImsPhoneCallTracker.this.mIsVtEnabled = false;
                }
                if (iArr2[4] == 4 || iArr2[5] == 5) {
                    ImsPhoneCallTracker.this.mIsUtEnabled = false;
                }
                if (iArr[0] == 0 || iArr[2] == 2) {
                    ImsPhoneCallTracker.this.mIsVolteEnabled = true;
                    ImsPhoneCallTracker.this.mIsSrvccCompleted = false;
                }
                if (iArr[1] == 1 || iArr[3] == 3) {
                    ImsPhoneCallTracker.this.mIsVtEnabled = true;
                    ImsPhoneCallTracker.this.mIsSrvccCompleted = false;
                }
                if (iArr[4] == 4 || iArr[5] == 5) {
                    ImsPhoneCallTracker.this.mIsUtEnabled = true;
                }
                if (access$2200 != ImsPhoneCallTracker.this.mIsVtEnabled) {
                    ImsPhoneCallTracker.this.mPhone.notifyForVideoCapabilityChanged(ImsPhoneCallTracker.this.mIsVtEnabled);
                }
            }
            ImsPhoneCallTracker.this.log("onFeatureCapabilityChanged, mIsVolteEnabled = " + ImsPhoneCallTracker.this.mIsVolteEnabled + " mIsVtEnabled = " + ImsPhoneCallTracker.this.mIsVtEnabled + "mIsUtEnabled = " + ImsPhoneCallTracker.this.mIsUtEnabled);
        }

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

        public void onVoiceMessageCountChanged(int i) {
            ImsPhoneCallTracker.this.log("onVoiceMessageCountChanged :: count=" + i);
            ImsPhoneCallTracker.this.mPhone.mDefaultPhone.setVoiceMessageCount(i);
        }
    };
    private ImsManager mImsManager;
    private Listener mImsUssdListener = new Listener() {
        public void onCallStartFailed(ImsCall imsCall, ImsReasonInfo imsReasonInfo) {
            ImsPhoneCallTracker.this.log("mImsUssdListener onCallStartFailed reasonCode=" + imsReasonInfo.getCode());
            onCallTerminated(imsCall, imsReasonInfo);
        }

        public void onCallStarted(ImsCall imsCall) {
            ImsPhoneCallTracker.this.log("mImsUssdListener onCallStarted");
            if (imsCall == ImsPhoneCallTracker.this.mUssdSession && ImsPhoneCallTracker.this.mPendingUssd != null) {
                AsyncResult.forMessage(ImsPhoneCallTracker.this.mPendingUssd);
                ImsPhoneCallTracker.this.mPendingUssd.sendToTarget();
                ImsPhoneCallTracker.this.mPendingUssd = null;
            }
        }

        public void onCallTerminated(ImsCall imsCall, ImsReasonInfo imsReasonInfo) {
            ImsPhoneCallTracker.this.log("mImsUssdListener onCallTerminated reasonCode=" + imsReasonInfo.getCode());
            if (imsCall == ImsPhoneCallTracker.this.mUssdSession) {
                ImsPhoneCallTracker.this.mUssdSession = null;
                if (ImsPhoneCallTracker.this.mPendingUssd != null) {
                    AsyncResult.forMessage(ImsPhoneCallTracker.this.mPendingUssd, null, new CommandException(Error.GENERIC_FAILURE));
                    ImsPhoneCallTracker.this.mPendingUssd.sendToTarget();
                    ImsPhoneCallTracker.this.mPendingUssd = null;
                }
            }
            imsCall.close();
        }

        public void onCallUssdMessageReceived(ImsCall imsCall, int i, String str) {
            ImsPhoneCallTracker.this.log("mImsUssdListener onCallUssdMessageReceived mode=" + i);
            int i2 = -1;
            switch (i) {
                case 0:
                    i2 = 0;
                    break;
                case 1:
                    i2 = 1;
                    break;
            }
            ImsPhoneCallTracker.this.mPhone.onIncomingUSSD(i2, str);
        }
    };
    private boolean mIsInEmergencyCall = false;
    private boolean mIsSrvccCompleted = false;
    private boolean mIsUtEnabled = false;
    private boolean mIsVolteEnabled = false;
    private boolean mIsVtEnabled = false;
    private boolean mOnHoldToneStarted = false;
    private int mPendingCallVideoState;
    private ImsPhoneConnection mPendingMO;
    private Message mPendingUssd = null;
    ImsPhone mPhone;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
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
                        boolean booleanExtra = intent.getBooleanExtra("codeaurora:isUnknown", false);
                        int intExtra = intent.getIntExtra("codeaurora.unknownCallState", 0);
                        String stringExtra = intent.getStringExtra("codeaurora:unknownCallAddress");
                        ImsPhoneCallTracker.this.log("onReceive : isUnknown = " + booleanExtra + " state = " + intExtra + " fg = " + ImsPhoneCallTracker.this.mForegroundCall.getState() + " bg = " + ImsPhoneCallTracker.this.mBackgroundCall.getState() + " address = " + stringExtra);
                        ImsCall takeCall = ImsPhoneCallTracker.this.mImsManager.takeCall(ImsPhoneCallTracker.this.mServiceId, intent, ImsPhoneCallTracker.this.mImsCallListener);
                        State access$600 = ImsPhoneCallTracker.this.convertIntToCallState(intExtra);
                        ImsPhoneCall imsPhoneCall = !booleanExtra ? ImsPhoneCallTracker.this.mRingingCall : (booleanExtra && access$600 == State.HOLDING) ? ImsPhoneCallTracker.this.mBackgroundCall : ImsPhoneCallTracker.this.mForegroundCall;
                        ImsPhoneConnection imsPhoneConnection = new ImsPhoneConnection(ImsPhoneCallTracker.this.mPhone.getContext(), takeCall, ImsPhoneCallTracker.this, imsPhoneCall, booleanExtra, access$600, stringExtra);
                        ImsPhoneCallTracker.this.addConnection(imsPhoneConnection);
                        if (booleanExtra && access$600 == State.HOLDING) {
                            takeCall.updateHoldValues();
                        }
                        ImsPhoneCallTracker.this.setVideoCallProvider(imsPhoneConnection, takeCall);
                        if (booleanExtra) {
                            ImsPhoneCallTracker.this.mPhone.notifyUnknownConnection(imsPhoneConnection);
                        } else {
                            if (!(ImsPhoneCallTracker.this.mForegroundCall.getState() == State.IDLE && ImsPhoneCallTracker.this.mBackgroundCall.getState() == State.IDLE)) {
                                imsPhoneConnection.update(takeCall, State.WAITING);
                            }
                            ImsPhoneCallTracker.this.mPhone.notifyNewRingingConnection(imsPhoneConnection);
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
    ImsPhoneCall mRingingCall = new ImsPhoneCall(this);
    private int mServiceId = -1;
    private SrvccState mSrvccState = SrvccState.NONE;
    PhoneConstants.State mState = PhoneConstants.State.IDLE;
    private Object mSyncHold = new Object();
    private ImsCall mUssdSession = null;
    private RegistrantList mVoiceCallEndedRegistrants = new RegistrantList();
    private RegistrantList mVoiceCallStartedRegistrants = new RegistrantList();
    private int pendingCallClirMode;
    private boolean pendingCallInEcm = false;

    ImsPhoneCallTracker(ImsPhone imsPhone) {
        this.mPhone = imsPhone;
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.android.ims.IMS_INCOMING_CALL");
        this.mPhone.getContext().registerReceiver(this.mReceiver, intentFilter);
        new Thread() {
            public void run() {
                ImsPhoneCallTracker.this.getImsService();
            }
        }.start();
    }

    private void addConnection(ImsPhoneConnection imsPhoneConnection) {
        synchronized (this) {
            this.mConnections.add(imsPhoneConnection);
        }
    }

    private State convertIntToCallState(int i) {
        switch (i) {
            case 1:
                return State.ACTIVE;
            case 2:
                return State.HOLDING;
            case 3:
                return State.DIALING;
            case 4:
                return State.ALERTING;
            case 5:
                return State.INCOMING;
            case 6:
                return State.WAITING;
            default:
                log("convertIntToCallState: illegal call state:" + i);
                return State.INCOMING;
        }
    }

    private PendingIntent createIncomingCallPendingIntent() {
        Intent intent = new Intent("com.android.ims.IMS_INCOMING_CALL");
        intent.addFlags(268435456);
        return PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
    }

    private void dialInternal(ImsPhoneConnection imsPhoneConnection, int i, int i2, Bundle bundle) {
        int i3 = 1;
        if (imsPhoneConnection != null) {
            boolean z;
            boolean z2;
            if (bundle != null) {
                z = bundle.getBoolean("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
                z2 = bundle.getBoolean("org.codeaurora.extra.SKIP_SCHEMA_PARSING", false);
            } else {
                z2 = false;
                z = false;
            }
            if (z || z2 || !(imsPhoneConnection.getAddress() == null || imsPhoneConnection.getAddress().length() == 0 || imsPhoneConnection.getAddress().indexOf(78) >= 0)) {
                setMute(false);
                if (PhoneNumberUtils.isEmergencyNumber(imsPhoneConnection.getAddress())) {
                    i3 = 2;
                }
                int callTypeFromVideoState = ImsCallProfile.getCallTypeFromVideoState(i2);
                imsPhoneConnection.setVideoState(i2);
                try {
                    String[] strArr = !TelBrand.IS_DCM ? new String[]{imsPhoneConnection.getAddress()} : new String[]{imsPhoneConnection.getNumber()};
                    ImsCallProfile createCallProfile = this.mImsManager.createCallProfile(this.mServiceId, i3, callTypeFromVideoState);
                    createCallProfile.setCallExtraInt("oir", i);
                    createCallProfile.setCallExtraBoolean("isConferenceUri", z);
                    if (bundle != null) {
                        createCallProfile.mCallExtras.putBundle("OemCallExtras", bundle);
                        log("Packing OEM extras bundle in call profile.");
                    } else {
                        log("No dial extras packed in call profile.");
                    }
                    ImsCall makeCall = this.mImsManager.makeCall(this.mServiceId, createCallProfile, strArr, this.mImsCallListener);
                    imsPhoneConnection.setImsCall(makeCall);
                    setVideoCallProvider(imsPhoneConnection, makeCall);
                    return;
                } catch (ImsException e) {
                    loge("dialInternal : " + e);
                    imsPhoneConnection.setDisconnectCause(36);
                    sendEmptyMessageDelayed(18, 500);
                    return;
                } catch (RemoteException e2) {
                    return;
                }
            }
            imsPhoneConnection.setDisconnectCause(7);
            sendEmptyMessageDelayed(18, 500);
        }
    }

    private void dumpState() {
        int i;
        int i2 = 0;
        log("Phone State:" + this.mState);
        log("Ringing call: " + this.mRingingCall.toString());
        List connections = this.mRingingCall.getConnections();
        int size = connections.size();
        for (i = 0; i < size; i++) {
            log(connections.get(i).toString());
        }
        log("Foreground call: " + this.mForegroundCall.toString());
        connections = this.mForegroundCall.getConnections();
        size = connections.size();
        for (i = 0; i < size; i++) {
            log(connections.get(i).toString());
        }
        log("Background call: " + this.mBackgroundCall.toString());
        List connections2 = this.mBackgroundCall.getConnections();
        int size2 = connections2.size();
        while (i2 < size2) {
            log(connections2.get(i2).toString());
            i2++;
        }
    }

    private ImsPhoneConnection findConnection(ImsCall imsCall) {
        ImsPhoneConnection imsPhoneConnection;
        synchronized (this) {
            Iterator it = this.mConnections.iterator();
            while (it.hasNext()) {
                imsPhoneConnection = (ImsPhoneConnection) it.next();
                if (imsPhoneConnection.getImsCall() == imsCall) {
                    break;
                }
            }
            imsPhoneConnection = null;
        }
        return imsPhoneConnection;
    }

    private int getDisconnectCauseFromReasonInfo(ImsReasonInfo imsReasonInfo) {
        int code = imsReasonInfo.getCode();
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
                if (TelBrand.IS_SBM) {
                    int state = this.mPhone.getServiceState().getState();
                    log("getDisconnectCauseFromReasonInfo(" + code + "...ims service state=" + state);
                    if (state == 3) {
                        return 17;
                    }
                    if (state == 1 || state == 2) {
                        return 18;
                    }
                    state = this.mPhone.mDefaultPhone.getServiceState().getState();
                    log("getDisconnectCauseFromReasonInfo(" + code + "...gsm service state=" + state);
                    if (state == 3) {
                        return 17;
                    }
                    if (state == 1 || state == 2) {
                        return 18;
                    }
                }
                return 36;
        }
    }

    private void getImsService() {
        log("getImsService");
        this.mImsManager = ImsManager.getInstance(this.mPhone.getContext(), this.mPhone.getPhoneId());
        try {
            this.mServiceId = this.mImsManager.open(1, createIncomingCallPendingIntent(), this.mImsConnectionStateListener);
            getEcbmInterface().setEcbmStateListener(this.mPhone.mImsEcbmStateListener);
            if (this.mPhone.isInEcm()) {
                this.mPhone.exitEmergencyCallbackMode();
            }
            this.mImsManager.setUiTTYMode(this.mPhone.getContext(), this.mServiceId, Secure.getInt(this.mPhone.getContext().getContentResolver(), "preferred_tty_mode", 0), null);
        } catch (ImsException e) {
            loge("getImsService: " + e);
            this.mImsManager = null;
        }
    }

    private void handleEcmTimer(int i) {
        this.mPhone.handleTimerInEmergencyCallbackMode(i);
        switch (i) {
            case 0:
            case 1:
                return;
            default:
                log("handleEcmTimer, unsupported action " + i);
                return;
        }
    }

    private void handleRadioNotAvailable() {
        pollCallsWhenSafe();
    }

    private void internalClearDisconnected() {
        this.mRingingCall.clearDisconnected();
        this.mForegroundCall.clearDisconnected();
        this.mBackgroundCall.clearDisconnected();
        this.mHandoverCall.clearDisconnected();
    }

    private void processCallStateChange(ImsCall imsCall, State state, int i) {
        processCallStateChange(imsCall, state, i, false);
    }

    private void processCallStateChange(ImsCall imsCall, State state, int i, boolean z) {
        log("processCallStateChange state=" + state + " cause=" + i + " ignoreState=" + z);
        if (imsCall != null) {
            ImsPhoneConnection findConnection = findConnection(imsCall);
            if (findConnection != null) {
                boolean update;
                if (z) {
                    update = findConnection.update(imsCall);
                } else {
                    update = findConnection.update(imsCall, state);
                    if (state == State.DISCONNECTED) {
                        update = findConnection.onDisconnect(i) || update;
                        findConnection.getCall().detach(findConnection);
                        removeConnection(findConnection);
                    }
                }
                if (update && findConnection.getCall() != this.mHandoverCall) {
                    updatePhoneState();
                    this.mPhone.notifyPreciseCallStateChanged();
                }
            }
        }
    }

    private void removeConnection(ImsPhoneConnection imsPhoneConnection) {
        synchronized (this) {
            this.mConnections.remove(imsPhoneConnection);
        }
    }

    private void setVideoCallProvider(ImsPhoneConnection imsPhoneConnection, ImsCall imsCall) throws RemoteException {
        IImsVideoCallProvider videoCallProvider = imsCall.getCallSession().getVideoCallProvider();
        if (videoCallProvider != null) {
            imsPhoneConnection.setVideoProvider(new ImsVideoCallProviderWrapper(videoCallProvider));
        }
    }

    private void switchAfterConferenceSuccess() {
        log("switchAfterConferenceSuccess fg =" + this.mForegroundCall.getState() + ", bg = " + this.mBackgroundCall.getState());
        if (!this.mForegroundCall.getState().isAlive() && this.mBackgroundCall.getState() == State.HOLDING) {
            this.mForegroundCall.switchWith(this.mBackgroundCall);
        }
    }

    private void transferHandoverConnections(ImsPhoneCall imsPhoneCall) {
        Connection connection;
        if (imsPhoneCall.mConnections != null) {
            Iterator it = imsPhoneCall.mConnections.iterator();
            while (it.hasNext()) {
                connection = (Connection) it.next();
                connection.mPreHandoverState = imsPhoneCall.mState;
                log("Connection state before handover is " + connection.getStateBeforeHandover());
            }
        }
        if (this.mHandoverCall.mConnections == null) {
            this.mHandoverCall.mConnections = imsPhoneCall.mConnections;
        } else {
            this.mHandoverCall.mConnections.addAll(imsPhoneCall.mConnections);
        }
        if (this.mHandoverCall.mConnections != null) {
            if (imsPhoneCall.getImsCall() != null) {
                imsPhoneCall.getImsCall().close();
            }
            Iterator it2 = this.mHandoverCall.mConnections.iterator();
            while (it2.hasNext()) {
                connection = (Connection) it2.next();
                ((ImsPhoneConnection) connection).changeParent(this.mHandoverCall);
                ((ImsPhoneConnection) connection).releaseWakeLock();
            }
        }
        if (imsPhoneCall.getState().isAlive()) {
            log("Call is alive and state is " + imsPhoneCall.mState);
            this.mHandoverCall.mState = imsPhoneCall.mState;
        }
        imsPhoneCall.mConnections.clear();
        imsPhoneCall.mState = State.IDLE;
    }

    private void updatePhoneState() {
        PhoneConstants.State state = this.mState;
        if (this.mRingingCall.isRinging()) {
            this.mState = PhoneConstants.State.RINGING;
        } else if (this.mPendingMO == null && this.mForegroundCall.isIdle() && this.mBackgroundCall.isIdle()) {
            this.mState = PhoneConstants.State.IDLE;
        } else {
            this.mState = PhoneConstants.State.OFFHOOK;
        }
        if (this.mState == PhoneConstants.State.IDLE && state != this.mState) {
            this.mVoiceCallEndedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
        } else if (state == PhoneConstants.State.IDLE && state != this.mState) {
            this.mVoiceCallStartedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
        }
        log("updatePhoneState oldState=" + state + ", newState=" + this.mState);
        if (this.mState != state) {
            this.mPhone.notifyPhoneStateChanged();
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void acceptCall(int i) throws CallStateException {
        log("acceptCall");
        if (this.mForegroundCall.getState().isAlive() && this.mBackgroundCall.getState().isAlive()) {
            throw new CallStateException("cannot accept call");
        } else if (this.mRingingCall.getState() == State.WAITING && this.mForegroundCall.getState().isAlive()) {
            setMute(false);
            this.mPendingCallVideoState = i;
            switchWaitingOrHoldingAndActive();
        } else if (this.mRingingCall.getState().isRinging()) {
            log("acceptCall: incoming...");
            setMute(false);
            try {
                ImsCall imsCall = this.mRingingCall.getImsCall();
                if (imsCall != null) {
                    imsCall.accept(ImsCallProfile.getCallTypeFromVideoState(i));
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

    public void addParticipant(String str) throws CallStateException {
        if (this.mForegroundCall != null) {
            ImsCall imsCall = this.mForegroundCall.getImsCall();
            if (imsCall == null) {
                loge("addParticipant : No foreground ims call");
                return;
            }
            ImsCallSession callSession = imsCall.getCallSession();
            if (callSession != null) {
                callSession.inviteParticipants(new String[]{str});
                return;
            }
            loge("addParticipant : ImsCallSession does not exist");
            return;
        }
        loge("addParticipant : Foreground call does not exist");
    }

    /* Access modifiers changed, original: 0000 */
    public boolean canConference() {
        return this.mForegroundCall.getState() == State.ACTIVE && this.mBackgroundCall.getState() == State.HOLDING && !this.mBackgroundCall.isFull() && !this.mForegroundCall.isFull();
    }

    /* Access modifiers changed, original: 0000 */
    public boolean canDial() {
        int state = this.mPhone.getServiceState().getState();
        String str = SystemProperties.get("ro.telephony.disable-call", "false");
        Rlog.d(LOG_TAG, "canDial(): \nserviceState = " + state + "\npendingMO == null::=" + String.valueOf(this.mPendingMO == null) + "\nringingCall: " + this.mRingingCall.getState() + "\ndisableCall = " + str + "\nforegndCall: " + this.mForegroundCall.getState() + "\nbackgndCall: " + this.mBackgroundCall.getState());
        return (state == 3 || this.mPendingMO != null || this.mRingingCall.isRinging() || str.equals("true") || (this.mForegroundCall.getState().isAlive() && this.mBackgroundCall.getState().isAlive())) ? false : true;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean canTransfer() {
        return this.mForegroundCall.getState() == State.ACTIVE && this.mBackgroundCall.getState() == State.HOLDING;
    }

    /* Access modifiers changed, original: 0000 */
    public void cancelUSSD() {
        if (this.mUssdSession != null) {
            try {
                this.mUssdSession.terminate(501);
            } catch (ImsException e) {
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void clearDisconnected() {
        log("clearDisconnected");
        internalClearDisconnected();
        updatePhoneState();
        this.mPhone.notifyPreciseCallStateChanged();
    }

    /* Access modifiers changed, original: 0000 */
    public void conference() {
        log("conference");
        ImsCall imsCall = this.mForegroundCall.getImsCall();
        if (imsCall == null) {
            log("conference no foreground ims call");
            return;
        }
        ImsCall imsCall2 = this.mBackgroundCall.getImsCall();
        if (imsCall2 == null) {
            log("conference no background ims call");
            return;
        }
        this.mConferenceTime = Math.min(this.mBackgroundCall.getEarliestConnectTime(), this.mForegroundCall.getEarliestConnectTime());
        try {
            imsCall.merge(imsCall2);
        } catch (ImsException e) {
            log("conference " + e.getMessage());
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
                    return;
                } catch (ImsException e) {
                    throw new CallStateException(e.getMessage());
                }
            }
            Rlog.e(LOG_TAG, "[ImsCallTracker] declineCall() is called, but ImsCall of ringingCall is null.");
            return;
        }
        Rlog.e(LOG_TAG, "[ImsCallTracker] declineCall() is called, but there is no ringing-call.");
        throw new CallStateException("phone not ringing");
    }

    /* Access modifiers changed, original: 0000 */
    public void deflectCall(String str) throws CallStateException {
        log("deflectCall");
        if (this.mRingingCall.getState().isRinging()) {
            try {
                ImsCall imsCall = this.mRingingCall.getImsCall();
                if (imsCall != null) {
                    imsCall.deflect(str);
                    return;
                }
                throw new CallStateException("no valid ims call to deflect");
            } catch (ImsException e) {
                throw new CallStateException("cannot deflect call");
            }
        }
        throw new CallStateException("phone not ringing");
    }

    /* Access modifiers changed, original: 0000 */
    public Connection dial(String str, int i) throws CallStateException {
        return dial(str, i, null);
    }

    /* Access modifiers changed, original: 0000 */
    public Connection dial(String str, int i, int i2, Bundle bundle) throws CallStateException {
        ImsPhoneConnection imsPhoneConnection;
        Object obj = 1;
        synchronized (this) {
            boolean z = SystemProperties.getBoolean("ril.cdma.inecmmode", false);
            boolean isEmergencyNumber = PhoneNumberUtils.isEmergencyNumber(str);
            log("dial clirMode=" + i);
            clearDisconnected();
            if (this.mImsManager == null) {
                throw new CallStateException("service not available");
            } else if (canDial()) {
                Object obj2;
                if (z && isEmergencyNumber) {
                    handleEcmTimer(1);
                }
                if (this.mForegroundCall.getState() != State.ACTIVE) {
                    obj = null;
                } else if (this.mBackgroundCall.getState() != State.IDLE) {
                    throw new CallStateException("cannot dial in current state");
                } else {
                    this.mPendingCallVideoState = i2;
                    switchWaitingOrHoldingAndActive();
                }
                State state = State.IDLE;
                state = State.IDLE;
                this.mClirMode = i;
                synchronized (this.mSyncHold) {
                    if (obj != null) {
                        state = this.mForegroundCall.getState();
                        State state2 = this.mBackgroundCall.getState();
                        if (state == State.ACTIVE) {
                            throw new CallStateException("cannot dial in current state");
                        } else if (state2 == State.HOLDING) {
                            obj2 = null;
                            this.mPendingMO = new ImsPhoneConnection(this.mPhone.getContext(), checkForTestEmergencyNumber(str), this, this.mForegroundCall, bundle);
                        }
                    }
                    obj2 = obj;
                    this.mPendingMO = new ImsPhoneConnection(this.mPhone.getContext(), checkForTestEmergencyNumber(str), this, this.mForegroundCall, bundle);
                }
                addConnection(this.mPendingMO);
                if (obj2 == null) {
                    if (!z || (z && isEmergencyNumber)) {
                        if (TelBrand.IS_KDDI) {
                            String number;
                            String number2 = this.mPendingMO.getNumber();
                            if (PhoneNumberUtils.isEmergencyNumber(number2)) {
                                log("dial() not add the special number");
                                number = this.mPendingMO.getNumber();
                            } else {
                                log("dial() add the special number");
                                try {
                                    ITelephony asInterface = Stub.asInterface(ServiceManager.getService("phone"));
                                    if (asInterface != null) {
                                        number = asInterface.addSpecialNumber(number2);
                                    } else {
                                        log("dial() ITelephony is null!");
                                        number = number2;
                                    }
                                } catch (RemoteException e) {
                                    log("dial() ITelephony is exception(" + e + ")");
                                    number = number2;
                                }
                            }
                            this.mPendingMO.setConverted(number);
                        }
                        dialInternal(this.mPendingMO, i, i2, bundle);
                    } else {
                        try {
                            getEcbmInterface().exitEmergencyCallbackMode();
                            this.mPhone.setOnEcbModeExitResponse(this, 14, null);
                            this.pendingCallClirMode = i;
                            this.mPendingCallVideoState = i2;
                            this.pendingCallInEcm = true;
                        } catch (ImsException e2) {
                            e2.printStackTrace();
                            throw new CallStateException("service not available");
                        }
                    }
                }
                updatePhoneState();
                this.mPhone.notifyPreciseCallStateChanged();
                imsPhoneConnection = this.mPendingMO;
            } else {
                throw new CallStateException("cannot dial in current state");
            }
        }
        return imsPhoneConnection;
    }

    /* Access modifiers changed, original: 0000 */
    public Connection dial(String str, int i, Bundle bundle) throws CallStateException {
        return dial(str, PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext()).getInt(PhoneBase.CLIR_KEY, 0), i, bundle);
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

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("ImsPhoneCallTracker extends:");
        super.dump(fileDescriptor, printWriter, strArr);
        printWriter.println(" mVoiceCallEndedRegistrants=" + this.mVoiceCallEndedRegistrants);
        printWriter.println(" mVoiceCallStartedRegistrants=" + this.mVoiceCallStartedRegistrants);
        printWriter.println(" mRingingCall=" + this.mRingingCall);
        printWriter.println(" mForegroundCall=" + this.mForegroundCall);
        printWriter.println(" mBackgroundCall=" + this.mBackgroundCall);
        printWriter.println(" mHandoverCall=" + this.mHandoverCall);
        printWriter.println(" mPendingMO=" + this.mPendingMO);
        printWriter.println(" mPhone=" + this.mPhone);
        printWriter.println(" mDesiredMute=" + this.mDesiredMute);
        printWriter.println(" mState=" + this.mState);
    }

    /* Access modifiers changed, original: 0000 */
    public void explicitCallTransfer() {
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        log("ImsPhoneCallTracker finalized");
    }

    /* Access modifiers changed, original: 0000 */
    public ImsEcbm getEcbmInterface() throws ImsException {
        if (this.mImsManager != null) {
            return this.mImsManager.getEcbmInterface(this.mServiceId);
        }
        throw new ImsException("no ims manager", 0);
    }

    /* Access modifiers changed, original: 0000 */
    public boolean getMute() {
        return this.mDesiredMute;
    }

    public PhoneConstants.State getState() {
        return this.mState;
    }

    /* Access modifiers changed, original: 0000 */
    public ImsUtInterface getUtInterface() throws ImsException {
        if (this.mImsManager != null) {
            return this.mImsManager.getSupplementaryServiceConfiguration(this.mServiceId);
        }
        throw new ImsException("no ims manager", 0);
    }

    public void handleMessage(Message message) {
        log("handleMessage what=" + message.what);
        switch (message.what) {
            case 14:
                if (this.pendingCallInEcm) {
                    dialInternal(this.mPendingMO, this.pendingCallClirMode, this.mPendingCallVideoState, this.mPendingMO.getCallExtras());
                    this.pendingCallInEcm = false;
                }
                this.mPhone.unsetOnEcbModeExitResponse(this);
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
            default:
                return;
        }
    }

    /* Access modifiers changed, original: protected */
    public void handlePollCalls(AsyncResult asyncResult) {
    }

    /* Access modifiers changed, original: 0000 */
    public void hangup(ImsPhoneCall imsPhoneCall) throws CallStateException {
        Object obj = 1;
        log("hangup call");
        if (imsPhoneCall.getConnections().size() == 0) {
            throw new CallStateException("no connections");
        }
        ImsCall imsCall = imsPhoneCall.getImsCall();
        if (imsPhoneCall == this.mRingingCall) {
            log("(ringing) hangup incoming");
        } else if (imsPhoneCall == this.mForegroundCall) {
            if (imsPhoneCall.isDialingOrAlerting()) {
                log("(foregnd) hangup dialing or alerting...");
                obj = null;
            } else {
                log("(foregnd) hangup foreground");
                obj = null;
            }
        } else if (imsPhoneCall == this.mBackgroundCall) {
            log("(backgnd) hangup waiting or background");
            obj = null;
        } else {
            throw new CallStateException("ImsPhoneCall " + imsPhoneCall + "does not belong to ImsPhoneCallTracker " + this);
        }
        imsPhoneCall.onHangupLocal();
        if (imsCall != null) {
            try {
                if (TelBrand.IS_SBM) {
                    if (obj != null) {
                        imsCall.reject(1);
                    } else {
                        imsCall.terminate(501);
                    }
                } else if (obj != null) {
                    imsCall.reject(504);
                } else {
                    imsCall.terminate(501);
                }
            } catch (ImsException e) {
                throw new CallStateException(e.getMessage());
            }
        } else if (this.mPendingMO != null && imsPhoneCall == this.mForegroundCall) {
            this.mPendingMO.update(null, State.DISCONNECTED);
            this.mPendingMO.onDisconnect();
            removeConnection(this.mPendingMO);
            this.mPendingMO = null;
            updatePhoneState();
            removeMessages(20);
        }
        this.mPhone.notifyPreciseCallStateChanged();
    }

    /* Access modifiers changed, original: 0000 */
    public void hangup(ImsPhoneConnection imsPhoneConnection) throws CallStateException {
        log("hangup connection");
        if (imsPhoneConnection.getOwner() != this) {
            throw new CallStateException("ImsPhoneConnection " + imsPhoneConnection + "does not belong to ImsPhoneCallTracker " + this);
        }
        hangup(imsPhoneConnection.getCall());
    }

    public boolean isInEmergencyCall() {
        return this.mIsInEmergencyCall;
    }

    public boolean isUtEnabled() {
        return this.mIsUtEnabled;
    }

    public boolean isVolteEnabled() {
        return this.mIsVolteEnabled;
    }

    public boolean isVtEnabled() {
        return this.mIsVtEnabled;
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(LOG_TAG, "[ImsPhoneCallTracker] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str) {
        Rlog.e(LOG_TAG, "[ImsPhoneCallTracker] " + str);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifySrvccState(SrvccState srvccState) {
        log("notifySrvccState state=" + srvccState);
        this.mSrvccState = srvccState;
        if (this.mSrvccState == SrvccState.COMPLETED) {
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

    public void registerForVoiceCallEnded(Handler handler, int i, Object obj) {
        this.mVoiceCallEndedRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForVoiceCallStarted(Handler handler, int i, Object obj) {
        this.mVoiceCallStartedRegistrants.add(new Registrant(handler, i, obj));
    }

    /* Access modifiers changed, original: 0000 */
    public void rejectCall() throws CallStateException {
        log("rejectCall");
        if (this.mRingingCall.getState().isRinging()) {
            hangup(this.mRingingCall);
            return;
        }
        throw new CallStateException("phone not ringing");
    }

    /* Access modifiers changed, original: 0000 */
    public void resumeWaitingOrHolding() throws CallStateException {
        log("resumeWaitingOrHolding");
        try {
            ImsCall imsCall;
            if (this.mForegroundCall.getState().isAlive()) {
                imsCall = this.mForegroundCall.getImsCall();
                if (imsCall != null) {
                    imsCall.resume();
                }
            } else if (this.mRingingCall.getState() == State.WAITING) {
                imsCall = this.mRingingCall.getImsCall();
                if (imsCall != null) {
                    imsCall.accept(ImsCallProfile.getCallTypeFromVideoState(this.mPendingCallVideoState));
                }
            } else {
                imsCall = this.mBackgroundCall.getImsCall();
                if (imsCall != null) {
                    imsCall.resume();
                }
            }
        } catch (ImsException e) {
            throw new CallStateException(e.getMessage());
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void sendDtmf(char c) {
        log("sendDtmf");
        ImsCall imsCall = this.mForegroundCall.getImsCall();
        if (imsCall != null) {
            imsCall.sendDtmf(c);
        } else {
            loge("sendDtmf : no foreground call");
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void sendDtmf(char c, Message message) {
        log("sendDtmf");
        ImsCall imsCall = this.mForegroundCall.getImsCall();
        if (imsCall != null) {
            imsCall.sendDtmf(c, message);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void sendUSSD(String str, Message message) {
        log("sendUSSD");
        try {
            if (this.mUssdSession != null) {
                this.mUssdSession.sendUssd(str);
                AsyncResult.forMessage(message, null, null);
                message.sendToTarget();
                return;
            }
            ImsCallProfile createCallProfile = this.mImsManager.createCallProfile(this.mServiceId, 1, 2);
            createCallProfile.setCallExtraInt("dialstring", 2);
            String[] strArr = new String[]{str};
            this.mUssdSession = this.mImsManager.makeCall(this.mServiceId, createCallProfile, strArr, this.mImsUssdListener);
        } catch (ImsException e) {
            loge("sendUSSD : " + e);
            this.mPhone.sendErrorResponse(message, e);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void setMute(boolean z) {
        this.mDesiredMute = z;
        this.mForegroundCall.setMute(z);
    }

    /* Access modifiers changed, original: 0000 */
    public void setUiTTYMode(int i, Message message) {
        try {
            this.mImsManager.setUiTTYMode(this.mPhone.getContext(), this.mServiceId, i, message);
        } catch (ImsException e) {
            loge("setTTYMode : " + e);
            this.mPhone.sendErrorResponse(message, e);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void startDtmf(char c) {
        log("startDtmf");
        ImsCall imsCall = this.mForegroundCall.getImsCall();
        if (imsCall != null) {
            imsCall.startDtmf(c);
        } else {
            loge("startDtmf : no foreground call");
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void stopDtmf() {
        log("stopDtmf");
        ImsCall imsCall = this.mForegroundCall.getImsCall();
        if (imsCall != null) {
            imsCall.stopDtmf();
        } else {
            loge("stopDtmf : no foreground call");
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void switchWaitingOrHoldingAndActive() throws CallStateException {
        log("switchWaitingOrHoldingAndActive");
        ImsCall imsCall;
        if (this.mRingingCall.getState() == State.INCOMING) {
            throw new CallStateException("cannot be in the incoming state");
        } else if (this.mForegroundCall.getState() == State.ACTIVE) {
            imsCall = this.mForegroundCall.getImsCall();
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
        } else if (this.mBackgroundCall.getState() == State.HOLDING) {
            resumeWaitingOrHolding();
        } else if (this.mForegroundCall.getState() == State.HOLDING) {
            imsCall = this.mForegroundCall.getImsCall();
            if (imsCall == null) {
                throw new CallStateException("no ims call");
            }
            try {
                imsCall.resume();
            } catch (ImsException e2) {
                throw new CallStateException(e2.getMessage());
            }
        }
    }

    public void unregisterForVoiceCallEnded(Handler handler) {
        this.mVoiceCallEndedRegistrants.remove(handler);
    }

    public void unregisterForVoiceCallStarted(Handler handler) {
        this.mVoiceCallStartedRegistrants.remove(handler);
    }
}
