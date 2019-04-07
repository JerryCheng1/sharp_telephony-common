package com.android.internal.telephony;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.sip.SipPhone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public final class CallManager {
    private static final boolean DBG = true;
    private static final int EVENT_CALL_WAITING = 108;
    private static final int EVENT_CDMA_OTA_STATUS_CHANGE = 111;
    private static final int EVENT_DISCONNECT = 100;
    private static final int EVENT_DISPLAY_INFO = 109;
    private static final int EVENT_ECM_TIMER_RESET = 115;
    private static final int EVENT_INCOMING_RING = 104;
    private static final int EVENT_IN_CALL_VOICE_PRIVACY_OFF = 107;
    private static final int EVENT_IN_CALL_VOICE_PRIVACY_ON = 106;
    private static final int EVENT_MMI_COMPLETE = 114;
    private static final int EVENT_MMI_INITIATE = 113;
    private static final int EVENT_NEW_RINGING_CONNECTION = 102;
    private static final int EVENT_ONHOLD_TONE = 120;
    private static final int EVENT_POST_DIAL_CHARACTER = 119;
    private static final int EVENT_PRECISE_CALL_STATE_CHANGED = 101;
    private static final int EVENT_RESEND_INCALL_MUTE = 112;
    private static final int EVENT_RINGBACK_TONE = 105;
    private static final int EVENT_SERVICE_STATE_CHANGED = 118;
    private static final int EVENT_SIGNAL_INFO = 110;
    private static final int EVENT_SUBSCRIPTION_INFO_READY = 116;
    private static final int EVENT_SUPP_SERVICE_FAILED = 117;
    private static final int EVENT_SUPP_SERVICE_NOTIFY = 121;
    private static final int EVENT_TTY_MODE_RECEIVED = 122;
    private static final int EVENT_UNKNOWN_CONNECTION = 103;
    private static final CallManager INSTANCE = new CallManager();
    private static final String LOG_TAG = "CallManager";
    private static final boolean VDBG = false;
    private static int mActiveSub = -1;
    protected final RegistrantList mActiveSubChangeRegistrants = new RegistrantList();
    private final ArrayList<Call> mBackgroundCalls = new ArrayList();
    protected final RegistrantList mCallWaitingRegistrants = new RegistrantList();
    protected final RegistrantList mCdmaOtaStatusChangeRegistrants = new RegistrantList();
    private Phone mDefaultPhone = null;
    protected final RegistrantList mDisconnectRegistrants = new RegistrantList();
    protected final RegistrantList mDisplayInfoRegistrants = new RegistrantList();
    protected final RegistrantList mEcmTimerResetRegistrants = new RegistrantList();
    private final ArrayList<Connection> mEmptyConnections = new ArrayList();
    private final ArrayList<Call> mForegroundCalls = new ArrayList();
    private final HashMap<Phone, CallManagerHandler> mHandlerMap = new HashMap();
    protected final RegistrantList mInCallVoicePrivacyOffRegistrants = new RegistrantList();
    protected final RegistrantList mInCallVoicePrivacyOnRegistrants = new RegistrantList();
    protected final RegistrantList mIncomingRingRegistrants = new RegistrantList();
    protected final RegistrantList mMmiCompleteRegistrants = new RegistrantList();
    protected final RegistrantList mMmiInitiateRegistrants = new RegistrantList();
    protected final RegistrantList mMmiRegistrants = new RegistrantList();
    protected final RegistrantList mNewRingingConnectionRegistrants = new RegistrantList();
    protected final RegistrantList mOnHoldToneRegistrants = new RegistrantList();
    private final ArrayList<Phone> mPhones = new ArrayList();
    protected final RegistrantList mPostDialCharacterRegistrants = new RegistrantList();
    protected final RegistrantList mPreciseCallStateRegistrants = new RegistrantList();
    private Object mRegistrantidentifier = new Object();
    protected final RegistrantList mResendIncallMuteRegistrants = new RegistrantList();
    protected final RegistrantList mRingbackToneRegistrants = new RegistrantList();
    private final ArrayList<Call> mRingingCalls = new ArrayList();
    protected final RegistrantList mServiceStateChangedRegistrants = new RegistrantList();
    protected final RegistrantList mSignalInfoRegistrants = new RegistrantList();
    private boolean mSpeedUpAudioForMtCall = false;
    protected final RegistrantList mSubscriptionInfoReadyRegistrants = new RegistrantList();
    protected final RegistrantList mSuppServiceFailedRegistrants = new RegistrantList();
    protected final RegistrantList mSuppServiceNotificationRegistrants = new RegistrantList();
    protected final RegistrantList mTtyModeReceivedRegistrants = new RegistrantList();
    protected final RegistrantList mUnknownConnectionRegistrants = new RegistrantList();

    private class CallManagerHandler extends Handler {
        private CallManagerHandler() {
        }

        public void handleMessage(Message message) {
            int subId;
            switch (message.what) {
                case 100:
                    CallManager.this.mDisconnectRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_PRECISE_CALL_STATE_CHANGED /*101*/:
                    CallManager.this.mPreciseCallStateRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_NEW_RINGING_CONNECTION /*102*/:
                    Connection connection = (Connection) ((AsyncResult) message.obj).result;
                    subId = connection.getCall().getPhone().getSubId();
                    if (CallManager.this.getActiveFgCallState(subId).isDialing() || CallManager.this.hasMoreThanOneRingingCall(subId)) {
                        try {
                            Rlog.d(CallManager.LOG_TAG, "silently drop incoming call: " + connection.getCall());
                            connection.getCall().hangup();
                            return;
                        } catch (CallStateException e) {
                            Rlog.w(CallManager.LOG_TAG, "new ringing connection", e);
                            return;
                        }
                    }
                    CallManager.this.mNewRingingConnectionRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_UNKNOWN_CONNECTION /*103*/:
                    CallManager.this.mUnknownConnectionRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_INCOMING_RING /*104*/:
                    if (!CallManager.this.hasActiveFgCall()) {
                        CallManager.this.mIncomingRingRegistrants.notifyRegistrants((AsyncResult) message.obj);
                        return;
                    }
                    return;
                case CallManager.EVENT_RINGBACK_TONE /*105*/:
                    CallManager.this.mRingbackToneRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case 106:
                    CallManager.this.mInCallVoicePrivacyOnRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_IN_CALL_VOICE_PRIVACY_OFF /*107*/:
                    CallManager.this.mInCallVoicePrivacyOffRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case 108:
                    CallManager.this.mCallWaitingRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_DISPLAY_INFO /*109*/:
                    CallManager.this.mDisplayInfoRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_SIGNAL_INFO /*110*/:
                    CallManager.this.mSignalInfoRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_CDMA_OTA_STATUS_CHANGE /*111*/:
                    CallManager.this.mCdmaOtaStatusChangeRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_RESEND_INCALL_MUTE /*112*/:
                    CallManager.this.mResendIncallMuteRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_MMI_INITIATE /*113*/:
                    CallManager.this.mMmiInitiateRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_MMI_COMPLETE /*114*/:
                    CallManager.this.mMmiCompleteRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_ECM_TIMER_RESET /*115*/:
                    CallManager.this.mEcmTimerResetRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_SUBSCRIPTION_INFO_READY /*116*/:
                    CallManager.this.mSubscriptionInfoReadyRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_SUPP_SERVICE_FAILED /*117*/:
                    CallManager.this.mSuppServiceFailedRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_SERVICE_STATE_CHANGED /*118*/:
                    CallManager.this.mServiceStateChangedRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_POST_DIAL_CHARACTER /*119*/:
                    int i = 0;
                    while (true) {
                        subId = i;
                        if (subId < CallManager.this.mPostDialCharacterRegistrants.size()) {
                            Message messageForRegistrant = ((Registrant) CallManager.this.mPostDialCharacterRegistrants.get(subId)).messageForRegistrant();
                            messageForRegistrant.obj = message.obj;
                            messageForRegistrant.arg1 = message.arg1;
                            messageForRegistrant.sendToTarget();
                            i = subId + 1;
                        } else {
                            return;
                        }
                    }
                case CallManager.EVENT_ONHOLD_TONE /*120*/:
                    CallManager.this.mOnHoldToneRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                case CallManager.EVENT_SUPP_SERVICE_NOTIFY /*121*/:
                    CallManager.this.mSuppServiceNotificationRegistrants.notifyRegistrants(new AsyncResult(null, message.obj, null));
                    return;
                case CallManager.EVENT_TTY_MODE_RECEIVED /*122*/:
                    CallManager.this.mTtyModeReceivedRegistrants.notifyRegistrants((AsyncResult) message.obj);
                    return;
                default:
                    return;
            }
        }
    }

    private CallManager() {
    }

    private boolean canDial(Phone phone) {
        int state = phone.getServiceState().getState();
        int subId = phone.getSubId();
        boolean hasActiveRingingCall = hasActiveRingingCall();
        State activeFgCallState = getActiveFgCallState(subId);
        boolean z = (state == 3 || hasActiveRingingCall || (activeFgCallState != State.ACTIVE && activeFgCallState != State.IDLE && activeFgCallState != State.DISCONNECTED && activeFgCallState != State.ALERTING)) ? false : true;
        if (!z) {
            Rlog.d(LOG_TAG, "canDial serviceState=" + state + " hasRingingCall=" + hasActiveRingingCall + " fgCallState=" + activeFgCallState);
        }
        return z;
    }

    private Context getContext() {
        Phone defaultPhone = getDefaultPhone();
        return defaultPhone == null ? null : defaultPhone.getContext();
    }

    private Call getFirstActiveCall(ArrayList<Call> arrayList) {
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            Call call = (Call) it.next();
            if (!call.isIdle()) {
                return call;
            }
        }
        return null;
    }

    private Call getFirstActiveCall(ArrayList<Call> arrayList, int i) {
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            Call call = (Call) it.next();
            if (!call.isIdle() && (call.getPhone().getSubId() == i || (call.getPhone() instanceof SipPhone))) {
                return call;
            }
        }
        return null;
    }

    private Call getFirstCallOfState(ArrayList<Call> arrayList, State state) {
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            Call call = (Call) it.next();
            if (call.getState() == state) {
                return call;
            }
        }
        return null;
    }

    private Call getFirstCallOfState(ArrayList<Call> arrayList, State state, int i) {
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            Call call = (Call) it.next();
            if (call.getState() == state || call.getPhone().getSubId() == i) {
                return call;
            }
            if (call.getPhone() instanceof SipPhone) {
                return call;
            }
        }
        return null;
    }

    private Call getFirstNonIdleCall(List<Call> list) {
        Call call = null;
        for (Call call2 : list) {
            if (!call2.isIdle()) {
                return call2;
            }
            if (call2.getState() != State.IDLE && call == null) {
                call = call2;
            }
        }
        return call;
    }

    private Call getFirstNonIdleCall(List<Call> list, int i) {
        Call call = null;
        for (Call call2 : list) {
            if (call2.getPhone().getSubId() == i || (call2.getPhone() instanceof SipPhone)) {
                if (!call2.isIdle()) {
                    return call2;
                }
                if (call2.getState() != State.IDLE && call == null) {
                    call = call2;
                }
            }
        }
        return call;
    }

    public static CallManager getInstance() {
        return INSTANCE;
    }

    private Phone getPhone(int i) {
        Iterator it = this.mPhones.iterator();
        while (it.hasNext()) {
            Phone phone = (Phone) it.next();
            if (phone.getSubId() == i && !(phone instanceof ImsPhone)) {
                return phone;
            }
        }
        return null;
    }

    private static Phone getPhoneBase(Phone phone) {
        return phone instanceof PhoneProxy ? phone.getForegroundCall().getPhone() : phone;
    }

    private boolean hasMoreThanOneHoldingCall(int i) {
        Iterator it = this.mBackgroundCalls.iterator();
        int i2 = 0;
        while (it.hasNext()) {
            Call call = (Call) it.next();
            if (call.getState() == State.HOLDING && (call.getPhone().getSubId() == i || (call.getPhone() instanceof SipPhone))) {
                int i3 = i2 + 1;
                if (i3 > 1) {
                    return true;
                }
                i2 = i3;
            }
        }
        return false;
    }

    private boolean hasMoreThanOneRingingCall() {
        Iterator it = this.mRingingCalls.iterator();
        int i = 0;
        while (it.hasNext()) {
            if (((Call) it.next()).getState().isRinging()) {
                int i2 = i + 1;
                if (i2 > 1) {
                    return true;
                }
                i = i2;
            }
        }
        return false;
    }

    private boolean hasMoreThanOneRingingCall(int i) {
        Iterator it = this.mRingingCalls.iterator();
        int i2 = 0;
        while (it.hasNext()) {
            Call call = (Call) it.next();
            if (call.getState().isRinging() && (call.getPhone().getSubId() == i || (call.getPhone() instanceof SipPhone))) {
                int i3 = i2 + 1;
                if (i3 > 1) {
                    return true;
                }
                i2 = i3;
            }
        }
        return false;
    }

    public static boolean isSamePhone(Phone phone, Phone phone2) {
        return getPhoneBase(phone) == getPhoneBase(phone2);
    }

    private void registerForPhoneStates(Phone phone) {
        int phoneId = phone.getPhoneId();
        if (((CallManagerHandler) this.mHandlerMap.get(phone)) != null) {
            Rlog.d(LOG_TAG, "This phone has already been registered.");
            return;
        }
        CallManagerHandler callManagerHandler = new CallManagerHandler();
        this.mHandlerMap.put(phone, callManagerHandler);
        phone.registerForPreciseCallStateChanged(callManagerHandler, EVENT_PRECISE_CALL_STATE_CHANGED, this.mRegistrantidentifier);
        phone.registerForDisconnect(callManagerHandler, 100, this.mRegistrantidentifier);
        phone.registerForNewRingingConnection(callManagerHandler, EVENT_NEW_RINGING_CONNECTION, this.mRegistrantidentifier);
        phone.registerForUnknownConnection(callManagerHandler, EVENT_UNKNOWN_CONNECTION, this.mRegistrantidentifier);
        phone.registerForIncomingRing(callManagerHandler, EVENT_INCOMING_RING, this.mRegistrantidentifier);
        phone.registerForRingbackTone(callManagerHandler, EVENT_RINGBACK_TONE, this.mRegistrantidentifier);
        phone.registerForInCallVoicePrivacyOn(callManagerHandler, 106, this.mRegistrantidentifier);
        phone.registerForInCallVoicePrivacyOff(callManagerHandler, EVENT_IN_CALL_VOICE_PRIVACY_OFF, this.mRegistrantidentifier);
        phone.registerForDisplayInfo(callManagerHandler, EVENT_DISPLAY_INFO, this.mRegistrantidentifier);
        phone.registerForSignalInfo(callManagerHandler, EVENT_SIGNAL_INFO, this.mRegistrantidentifier);
        phone.registerForResendIncallMute(callManagerHandler, EVENT_RESEND_INCALL_MUTE, this.mRegistrantidentifier);
        phone.registerForMmiInitiate(callManagerHandler, EVENT_MMI_INITIATE, this.mRegistrantidentifier);
        phone.registerForMmiComplete(callManagerHandler, EVENT_MMI_COMPLETE, this.mRegistrantidentifier);
        phone.registerForSuppServiceFailed(callManagerHandler, EVENT_SUPP_SERVICE_FAILED, this.mRegistrantidentifier);
        phone.registerForServiceStateChanged(callManagerHandler, EVENT_SERVICE_STATE_CHANGED, this.mRegistrantidentifier);
        if (phone.getPhoneType() == 1 || phone.getPhoneType() == 5) {
            phone.registerForSuppServiceNotification(callManagerHandler, EVENT_SUPP_SERVICE_NOTIFY, Integer.valueOf(phoneId));
        }
        if (phone.getPhoneType() == 1 || phone.getPhoneType() == 2 || phone.getPhoneType() == 5) {
            phone.setOnPostDialCharacter(callManagerHandler, EVENT_POST_DIAL_CHARACTER, null);
        }
        if (phone.getPhoneType() == 2) {
            phone.registerForCdmaOtaStatusChange(callManagerHandler, EVENT_CDMA_OTA_STATUS_CHANGE, null);
            phone.registerForSubscriptionInfoReady(callManagerHandler, EVENT_SUBSCRIPTION_INFO_READY, null);
            phone.registerForCallWaiting(callManagerHandler, 108, null);
            phone.registerForEcmTimerReset(callManagerHandler, EVENT_ECM_TIMER_RESET, null);
        }
        if (phone.getPhoneType() == 5) {
            phone.registerForOnHoldTone(callManagerHandler, EVENT_ONHOLD_TONE, null);
            phone.registerForSuppServiceFailed(callManagerHandler, EVENT_SUPP_SERVICE_FAILED, null);
            phone.registerForTtyModeReceived(callManagerHandler, EVENT_TTY_MODE_RECEIVED, null);
        }
    }

    private void unregisterForPhoneStates(Phone phone) {
        CallManagerHandler callManagerHandler = (CallManagerHandler) this.mHandlerMap.get(phone);
        if (callManagerHandler == null) {
            Rlog.e(LOG_TAG, "Could not find Phone handler for unregistration");
            return;
        }
        this.mHandlerMap.remove(phone);
        phone.unregisterForPreciseCallStateChanged(callManagerHandler);
        phone.unregisterForDisconnect(callManagerHandler);
        phone.unregisterForNewRingingConnection(callManagerHandler);
        phone.unregisterForUnknownConnection(callManagerHandler);
        phone.unregisterForIncomingRing(callManagerHandler);
        phone.unregisterForRingbackTone(callManagerHandler);
        phone.unregisterForInCallVoicePrivacyOn(callManagerHandler);
        phone.unregisterForInCallVoicePrivacyOff(callManagerHandler);
        phone.unregisterForDisplayInfo(callManagerHandler);
        phone.unregisterForSignalInfo(callManagerHandler);
        phone.unregisterForResendIncallMute(callManagerHandler);
        phone.unregisterForMmiInitiate(callManagerHandler);
        phone.unregisterForMmiComplete(callManagerHandler);
        phone.unregisterForSuppServiceFailed(callManagerHandler);
        phone.unregisterForServiceStateChanged(callManagerHandler);
        phone.unregisterForTtyModeReceived(callManagerHandler);
        if (phone.getPhoneType() == 1 || phone.getPhoneType() == 5) {
            phone.unregisterForSuppServiceNotification(callManagerHandler);
        }
        if (phone.getPhoneType() == 1 || phone.getPhoneType() == 2 || phone.getPhoneType() == 5) {
            phone.setOnPostDialCharacter(null, EVENT_POST_DIAL_CHARACTER, null);
        }
        if (phone.getPhoneType() == 2) {
            phone.unregisterForCdmaOtaStatusChange(callManagerHandler);
            phone.unregisterForSubscriptionInfoReady(callManagerHandler);
            phone.unregisterForCallWaiting(callManagerHandler);
            phone.unregisterForEcmTimerReset(callManagerHandler);
        }
        if (phone.getPhoneType() == 5) {
            phone.unregisterForOnHoldTone(callManagerHandler);
            phone.unregisterForSuppServiceFailed(callManagerHandler);
        }
    }

    public void acceptCall(Call call) throws CallStateException {
        int i = 1;
        Phone phone = call.getPhone();
        if (hasActiveFgCall()) {
            Phone phone2 = getActiveFgCall().getPhone();
            int i2 = !phone2.getBackgroundCall().isIdle() ? 1 : 0;
            if (phone2 != phone) {
                i = 0;
            }
            if (i != 0 && i2 != 0) {
                getActiveFgCall().hangup();
            } else if (i == 0 && i2 == 0) {
                phone2.switchHoldingAndActive();
            } else if (i == 0 && i2 != 0) {
                getActiveFgCall().hangup();
            }
        }
        phone.acceptCall(0);
    }

    public boolean canConference(Call call) {
        Object obj = null;
        Object phone = hasActiveFgCall() ? getActiveFgCall().getPhone() : null;
        if (call != null) {
            obj = call.getPhone();
        }
        return obj.getClass().equals(phone.getClass());
    }

    public boolean canConference(Call call, int i) {
        Object obj = null;
        Object phone = hasActiveFgCall(i) ? getActiveFgCall(i).getPhone() : null;
        if (call != null) {
            obj = call.getPhone();
        }
        return obj.getClass().equals(phone.getClass());
    }

    public boolean canTransfer(Call call) {
        Phone phone = null;
        Phone phone2 = hasActiveFgCall() ? getActiveFgCall().getPhone() : null;
        if (call != null) {
            phone = call.getPhone();
        }
        return phone == phone2 && phone2.canTransfer();
    }

    public boolean canTransfer(Call call, int i) {
        Phone phone = null;
        Phone phone2 = hasActiveFgCall(i) ? getActiveFgCall(i).getPhone() : null;
        if (call != null) {
            phone = call.getPhone();
        }
        return phone == phone2 && phone2.canTransfer();
    }

    public void clearDisconnected() {
        Iterator it = this.mPhones.iterator();
        while (it.hasNext()) {
            ((Phone) it.next()).clearDisconnected();
        }
    }

    public void clearDisconnected(int i) {
        Iterator it = this.mPhones.iterator();
        while (it.hasNext()) {
            Phone phone = (Phone) it.next();
            if (phone.getSubId() == i) {
                phone.clearDisconnected();
            }
        }
    }

    public void conference(Call call) throws CallStateException {
        Phone fgPhone = getFgPhone(call.getPhone().getSubId());
        if (fgPhone == null) {
            Rlog.d(LOG_TAG, "conference: fgPhone=null");
        } else if (fgPhone instanceof SipPhone) {
            ((SipPhone) fgPhone).conference(call);
        } else if (canConference(call)) {
            fgPhone.conference();
        } else {
            throw new CallStateException("Can't conference foreground and selected background call");
        }
    }

    public Connection dial(Phone phone, String str, int i) throws CallStateException {
        return dial(phone, str, i, 0);
    }

    public Connection dial(Phone phone, String str, int i, int i2) throws CallStateException {
        boolean z = true;
        Phone phoneBase = getPhoneBase(phone);
        int subId = phone.getSubId();
        if (canDial(phone)) {
            if (hasActiveFgCall(subId)) {
                Phone phone2 = getActiveFgCall(subId).getPhone();
                boolean z2 = !phone2.getBackgroundCall().isIdle();
                StringBuilder append = new StringBuilder().append("hasBgCall: ").append(z2).append(" sameChannel:");
                if (phone2 != phoneBase) {
                    z = false;
                }
                Rlog.d(LOG_TAG, append.append(z).toString());
                Phone imsPhone = phoneBase.getImsPhone();
                if (phone2 != phoneBase && (imsPhone == null || imsPhone != phone2)) {
                    if (z2) {
                        Rlog.d(LOG_TAG, "Hangup");
                        getActiveFgCall(subId).hangup();
                    } else {
                        Rlog.d(LOG_TAG, "Switch");
                        phone2.switchHoldingAndActive();
                    }
                }
            }
            return TelBrand.IS_DCM ? phoneBase.dial(str, i, i2) : phoneBase.dial(str, i);
        } else if (phoneBase.handleInCallMmiCommands(PhoneNumberUtils.stripSeparators(str))) {
            return null;
        } else {
            throw new CallStateException("cannot dial in current state");
        }
    }

    public Connection dial(Phone phone, String str, UUSInfo uUSInfo, int i) throws CallStateException {
        return dial(phone, str, uUSInfo, i, 0);
    }

    public Connection dial(Phone phone, String str, UUSInfo uUSInfo, int i, int i2) throws CallStateException {
        return TelBrand.IS_DCM ? phone.dial(str, uUSInfo, i, i2) : phone.dial(str, uUSInfo, i);
    }

    public void explicitCallTransfer(Call call) throws CallStateException {
        if (canTransfer(call)) {
            call.getPhone().explicitCallTransfer();
        }
    }

    public Call getActiveFgCall() {
        Call firstNonIdleCall = getFirstNonIdleCall(this.mForegroundCalls);
        return firstNonIdleCall == null ? this.mDefaultPhone == null ? null : this.mDefaultPhone.getForegroundCall() : firstNonIdleCall;
    }

    public Call getActiveFgCall(int i) {
        Call firstNonIdleCall = getFirstNonIdleCall(this.mForegroundCalls, i);
        if (firstNonIdleCall != null) {
            return firstNonIdleCall;
        }
        Phone phone = getPhone(i);
        return phone == null ? null : phone.getForegroundCall();
    }

    public State getActiveFgCallState() {
        Call activeFgCall = getActiveFgCall();
        return activeFgCall != null ? activeFgCall.getState() : State.IDLE;
    }

    public State getActiveFgCallState(int i) {
        Call activeFgCall = getActiveFgCall(i);
        return activeFgCall != null ? activeFgCall.getState() : State.IDLE;
    }

    public List<Phone> getAllPhones() {
        return Collections.unmodifiableList(this.mPhones);
    }

    public List<Call> getBackgroundCalls() {
        return Collections.unmodifiableList(this.mBackgroundCalls);
    }

    public List<Connection> getBgCallConnections() {
        Call firstActiveBgCall = getFirstActiveBgCall();
        return firstActiveBgCall != null ? firstActiveBgCall.getConnections() : this.mEmptyConnections;
    }

    public List<Connection> getBgCallConnections(int i) {
        Call firstActiveBgCall = getFirstActiveBgCall(i);
        return firstActiveBgCall != null ? firstActiveBgCall.getConnections() : this.mEmptyConnections;
    }

    public Phone getBgPhone() {
        return getFirstActiveBgCall().getPhone();
    }

    public Phone getBgPhone(int i) {
        return getFirstActiveBgCall(i).getPhone();
    }

    public Phone getDefaultPhone() {
        return this.mDefaultPhone;
    }

    public List<Connection> getFgCallConnections() {
        Call activeFgCall = getActiveFgCall();
        return activeFgCall != null ? activeFgCall.getConnections() : this.mEmptyConnections;
    }

    public List<Connection> getFgCallConnections(int i) {
        Call activeFgCall = getActiveFgCall(i);
        return activeFgCall != null ? activeFgCall.getConnections() : this.mEmptyConnections;
    }

    public Connection getFgCallLatestConnection() {
        Call activeFgCall = getActiveFgCall();
        return activeFgCall != null ? activeFgCall.getLatestConnection() : null;
    }

    public Connection getFgCallLatestConnection(int i) {
        Call activeFgCall = getActiveFgCall(i);
        return activeFgCall != null ? activeFgCall.getLatestConnection() : null;
    }

    public Phone getFgPhone() {
        return getActiveFgCall().getPhone();
    }

    public Phone getFgPhone(int i) {
        return getActiveFgCall(i).getPhone();
    }

    public Call getFirstActiveBgCall() {
        Call firstNonIdleCall = getFirstNonIdleCall(this.mBackgroundCalls);
        return firstNonIdleCall == null ? this.mDefaultPhone == null ? null : this.mDefaultPhone.getBackgroundCall() : firstNonIdleCall;
    }

    public Call getFirstActiveBgCall(int i) {
        Phone phone = getPhone(i);
        if (hasMoreThanOneHoldingCall(i)) {
            return phone.getBackgroundCall();
        }
        Call firstNonIdleCall = getFirstNonIdleCall(this.mBackgroundCalls, i);
        return firstNonIdleCall == null ? phone == null ? null : phone.getBackgroundCall() : firstNonIdleCall;
    }

    public Call getFirstActiveRingingCall() {
        Call firstNonIdleCall = getFirstNonIdleCall(this.mRingingCalls);
        return firstNonIdleCall == null ? this.mDefaultPhone == null ? null : this.mDefaultPhone.getRingingCall() : firstNonIdleCall;
    }

    public Call getFirstActiveRingingCall(int i) {
        Phone phone = getPhone(i);
        Call firstNonIdleCall = getFirstNonIdleCall(this.mRingingCalls, i);
        return firstNonIdleCall == null ? phone == null ? null : phone.getRingingCall() : firstNonIdleCall;
    }

    public List<Call> getForegroundCalls() {
        return Collections.unmodifiableList(this.mForegroundCalls);
    }

    public boolean getMute() {
        return hasActiveFgCall() ? getActiveFgCall().getPhone().getMute() : hasActiveBgCall() ? getFirstActiveBgCall().getPhone().getMute() : false;
    }

    public List<? extends MmiCode> getPendingMmiCodes(Phone phone) {
        Rlog.e(LOG_TAG, "getPendingMmiCodes not implemented");
        return null;
    }

    public Phone getPhoneInCall() {
        return !getFirstActiveRingingCall().isIdle() ? getFirstActiveRingingCall().getPhone() : !getActiveFgCall().isIdle() ? getActiveFgCall().getPhone() : getFirstActiveBgCall().getPhone();
    }

    public Phone getPhoneInCall(int i) {
        return !getFirstActiveRingingCall(i).isIdle() ? getFirstActiveRingingCall(i).getPhone() : !getActiveFgCall(i).isIdle() ? getActiveFgCall(i).getPhone() : getFirstActiveBgCall(i).getPhone();
    }

    public Object getRegistrantIdentifier() {
        return this.mRegistrantidentifier;
    }

    public List<Call> getRingingCalls() {
        return Collections.unmodifiableList(this.mRingingCalls);
    }

    public Phone getRingingPhone() {
        return getFirstActiveRingingCall().getPhone();
    }

    public Phone getRingingPhone(int i) {
        return getFirstActiveRingingCall(i).getPhone();
    }

    public int getServiceState() {
        Iterator it = this.mPhones.iterator();
        while (it.hasNext()) {
            int state = ((Phone) it.next()).getServiceState().getState();
            if (state == 0) {
                return state;
            }
            if (state != 1 && state == 2) {
            }
        }
        return 1;
    }

    public int getServiceState(int i) {
        Iterator it = this.mPhones.iterator();
        while (it.hasNext()) {
            Phone phone = (Phone) it.next();
            if (phone.getSubId() == i) {
                int state = phone.getServiceState().getState();
                if (state == 0) {
                    return state;
                }
                if (state != 1 && state == 2) {
                }
            }
        }
        return 1;
    }

    public PhoneConstants.State getState() {
        PhoneConstants.State state = PhoneConstants.State.IDLE;
        Iterator it = this.mPhones.iterator();
        PhoneConstants.State state2 = state;
        while (it.hasNext()) {
            Phone phone = (Phone) it.next();
            if (phone.getState() == PhoneConstants.State.RINGING) {
                state2 = PhoneConstants.State.RINGING;
            } else if (phone.getState() == PhoneConstants.State.OFFHOOK && state2 == PhoneConstants.State.IDLE) {
                state2 = PhoneConstants.State.OFFHOOK;
            }
        }
        return state2;
    }

    public PhoneConstants.State getState(int i) {
        PhoneConstants.State state = PhoneConstants.State.IDLE;
        Iterator it = this.mPhones.iterator();
        PhoneConstants.State state2 = state;
        while (it.hasNext()) {
            Phone phone = (Phone) it.next();
            if (phone.getSubId() == i) {
                if (phone.getState() == PhoneConstants.State.RINGING) {
                    state2 = PhoneConstants.State.RINGING;
                } else if (phone.getState() == PhoneConstants.State.OFFHOOK && state2 == PhoneConstants.State.IDLE) {
                    state2 = PhoneConstants.State.OFFHOOK;
                }
            }
        }
        return state2;
    }

    public void hangupForegroundResumeBackground(Call call) throws CallStateException {
        if (hasActiveFgCall()) {
            Phone fgPhone = getFgPhone();
            if (call == null) {
                return;
            }
            if (fgPhone == call.getPhone()) {
                getActiveFgCall().hangup();
                return;
            }
            getActiveFgCall().hangup();
            switchHoldingAndActive(call);
        }
    }

    public boolean hasActiveBgCall() {
        return getFirstActiveCall(this.mBackgroundCalls) != null;
    }

    public boolean hasActiveBgCall(int i) {
        return getFirstActiveCall(this.mBackgroundCalls, i) != null;
    }

    public boolean hasActiveFgCall() {
        return getFirstActiveCall(this.mForegroundCalls) != null;
    }

    public boolean hasActiveFgCall(int i) {
        return getFirstActiveCall(this.mForegroundCalls, i) != null;
    }

    public boolean hasActiveRingingCall() {
        return getFirstActiveCall(this.mRingingCalls) != null;
    }

    public boolean hasActiveRingingCall(int i) {
        return getFirstActiveCall(this.mRingingCalls, i) != null;
    }

    public boolean hasDisconnectedBgCall() {
        return getFirstCallOfState(this.mBackgroundCalls, State.DISCONNECTED) != null;
    }

    public boolean hasDisconnectedBgCall(int i) {
        return getFirstCallOfState(this.mBackgroundCalls, State.DISCONNECTED, i) != null;
    }

    public boolean hasDisconnectedFgCall() {
        return getFirstCallOfState(this.mForegroundCalls, State.DISCONNECTED) != null;
    }

    public boolean hasDisconnectedFgCall(int i) {
        return getFirstCallOfState(this.mForegroundCalls, State.DISCONNECTED, i) != null;
    }

    public void registerForCallWaiting(Handler handler, int i, Object obj) {
        this.mCallWaitingRegistrants.addUnique(handler, i, obj);
    }

    public void registerForCdmaOtaStatusChange(Handler handler, int i, Object obj) {
        this.mCdmaOtaStatusChangeRegistrants.addUnique(handler, i, obj);
    }

    public void registerForDisconnect(Handler handler, int i, Object obj) {
        this.mDisconnectRegistrants.addUnique(handler, i, obj);
    }

    public void registerForDisplayInfo(Handler handler, int i, Object obj) {
        this.mDisplayInfoRegistrants.addUnique(handler, i, obj);
    }

    public void registerForEcmTimerReset(Handler handler, int i, Object obj) {
        this.mEcmTimerResetRegistrants.addUnique(handler, i, obj);
    }

    public void registerForInCallVoicePrivacyOff(Handler handler, int i, Object obj) {
        this.mInCallVoicePrivacyOffRegistrants.addUnique(handler, i, obj);
    }

    public void registerForInCallVoicePrivacyOn(Handler handler, int i, Object obj) {
        this.mInCallVoicePrivacyOnRegistrants.addUnique(handler, i, obj);
    }

    public void registerForIncomingRing(Handler handler, int i, Object obj) {
        this.mIncomingRingRegistrants.addUnique(handler, i, obj);
    }

    public void registerForMmiComplete(Handler handler, int i, Object obj) {
        this.mMmiCompleteRegistrants.addUnique(handler, i, obj);
    }

    public void registerForMmiInitiate(Handler handler, int i, Object obj) {
        this.mMmiInitiateRegistrants.addUnique(handler, i, obj);
    }

    public void registerForNewRingingConnection(Handler handler, int i, Object obj) {
        this.mNewRingingConnectionRegistrants.addUnique(handler, i, obj);
    }

    public void registerForOnHoldTone(Handler handler, int i, Object obj) {
        this.mOnHoldToneRegistrants.addUnique(handler, i, obj);
    }

    public void registerForPostDialCharacter(Handler handler, int i, Object obj) {
        this.mPostDialCharacterRegistrants.addUnique(handler, i, obj);
    }

    public void registerForPreciseCallStateChanged(Handler handler, int i, Object obj) {
        this.mPreciseCallStateRegistrants.addUnique(handler, i, obj);
    }

    public void registerForResendIncallMute(Handler handler, int i, Object obj) {
        this.mResendIncallMuteRegistrants.addUnique(handler, i, obj);
    }

    public void registerForRingbackTone(Handler handler, int i, Object obj) {
        this.mRingbackToneRegistrants.addUnique(handler, i, obj);
    }

    public void registerForServiceStateChanged(Handler handler, int i, Object obj) {
        this.mServiceStateChangedRegistrants.addUnique(handler, i, obj);
    }

    public void registerForSignalInfo(Handler handler, int i, Object obj) {
        this.mSignalInfoRegistrants.addUnique(handler, i, obj);
    }

    public void registerForSubscriptionChange(Handler handler, int i, Object obj) {
        this.mActiveSubChangeRegistrants.addUnique(handler, i, obj);
    }

    public void registerForSubscriptionInfoReady(Handler handler, int i, Object obj) {
        this.mSubscriptionInfoReadyRegistrants.addUnique(handler, i, obj);
    }

    public void registerForSuppServiceFailed(Handler handler, int i, Object obj) {
        this.mSuppServiceFailedRegistrants.addUnique(handler, i, obj);
    }

    public void registerForSuppServiceNotification(Handler handler, int i, Object obj) {
        this.mSuppServiceNotificationRegistrants.addUnique(handler, i, obj);
    }

    public void registerForTtyModeReceived(Handler handler, int i, Object obj) {
        this.mTtyModeReceivedRegistrants.addUnique(handler, i, obj);
    }

    public void registerForUnknownConnection(Handler handler, int i, Object obj) {
        this.mUnknownConnectionRegistrants.addUnique(handler, i, obj);
    }

    public boolean registerPhone(Phone phone) {
        Phone phoneBase = getPhoneBase(phone);
        if ((TelBrand.IS_SBM || TelBrand.IS_KDDI) && (phoneBase instanceof SipPhone)) {
            unregisterPhone(phoneBase);
        }
        if (phoneBase == null || this.mPhones.contains(phoneBase)) {
            return false;
        }
        Rlog.d(LOG_TAG, "registerPhone(" + phone.getPhoneName() + " " + phone + ")");
        if (this.mPhones.isEmpty()) {
            this.mDefaultPhone = phoneBase;
        }
        this.mPhones.add(phoneBase);
        this.mRingingCalls.add(phoneBase.getRingingCall());
        this.mBackgroundCalls.add(phoneBase.getBackgroundCall());
        this.mForegroundCalls.add(phoneBase.getForegroundCall());
        registerForPhoneStates(phoneBase);
        return true;
    }

    public void rejectCall(Call call) throws CallStateException {
        call.getPhone().rejectCall();
    }

    public boolean sendBurstDtmf(String str, int i, int i2, Message message) {
        if (!hasActiveFgCall()) {
            return false;
        }
        getActiveFgCall().getPhone().sendBurstDtmf(str, i, i2, message);
        return true;
    }

    public boolean sendDtmf(char c) {
        if (!hasActiveFgCall()) {
            return false;
        }
        getActiveFgCall().getPhone().sendDtmf(c);
        return true;
    }

    public boolean sendUssdResponse(Phone phone, String str) {
        Rlog.e(LOG_TAG, "sendUssdResponse not implemented");
        return false;
    }

    public void setActiveSubscription(int i) {
        Rlog.d(LOG_TAG, "setActiveSubscription existing:" + mActiveSub + "new = " + i);
        mActiveSub = i;
        this.mActiveSubChangeRegistrants.notifyRegistrants(new AsyncResult(null, Integer.valueOf(mActiveSub), null));
    }

    public void setEchoSuppressionEnabled() {
        if (hasActiveFgCall()) {
            getActiveFgCall().getPhone().setEchoSuppressionEnabled();
        }
    }

    public void setMute(boolean z) {
        if (hasActiveFgCall()) {
            getActiveFgCall().getPhone().setMute(z);
        }
    }

    public boolean startDtmf(char c) {
        if (!hasActiveFgCall()) {
            return false;
        }
        getActiveFgCall().getPhone().startDtmf(c);
        return true;
    }

    public void stopDtmf() {
        if (hasActiveFgCall()) {
            getFgPhone().stopDtmf();
        }
    }

    public void switchHoldingAndActive(Call call) throws CallStateException {
        Phone phone = null;
        Phone phone2 = hasActiveFgCall() ? getActiveFgCall().getPhone() : null;
        if (call != null) {
            phone = call.getPhone();
        }
        if (phone2 != null) {
            phone2.switchHoldingAndActive();
        }
        if (phone != null && phone != phone2) {
            phone.switchHoldingAndActive();
        }
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            stringBuilder.append("CallManager {");
            stringBuilder.append("\nstate = " + getState(i));
            Call activeFgCall = getActiveFgCall(i);
            if (activeFgCall != null) {
                stringBuilder.append("\n- Foreground: " + getActiveFgCallState(i));
                stringBuilder.append(" from " + activeFgCall.getPhone());
                stringBuilder.append("\n  Conn: ").append(getFgCallConnections(i));
            }
            activeFgCall = getFirstActiveBgCall(i);
            if (activeFgCall != null) {
                stringBuilder.append("\n- Background: " + activeFgCall.getState());
                stringBuilder.append(" from " + activeFgCall.getPhone());
                stringBuilder.append("\n  Conn: ").append(getBgCallConnections(i));
            }
            activeFgCall = getFirstActiveRingingCall(i);
            if (activeFgCall != null) {
                stringBuilder.append("\n- Ringing: " + activeFgCall.getState());
                stringBuilder.append(" from " + activeFgCall.getPhone());
            }
        }
        for (Phone phone : getAllPhones()) {
            if (phone != null) {
                stringBuilder.append("\nPhone: " + phone + ", name = " + phone.getPhoneName() + ", state = " + phone.getState());
                Call foregroundCall = phone.getForegroundCall();
                if (foregroundCall != null) {
                    stringBuilder.append("\n- Foreground: ").append(foregroundCall);
                }
                foregroundCall = phone.getBackgroundCall();
                if (foregroundCall != null) {
                    stringBuilder.append(" Background: ").append(foregroundCall);
                }
                Call ringingCall = phone.getRingingCall();
                if (ringingCall != null) {
                    stringBuilder.append(" Ringing: ").append(ringingCall);
                }
            }
        }
        stringBuilder.append("\n}");
        return stringBuilder.toString();
    }

    public void unregisterForCallWaiting(Handler handler) {
        this.mCallWaitingRegistrants.remove(handler);
    }

    public void unregisterForCdmaOtaStatusChange(Handler handler) {
        this.mCdmaOtaStatusChangeRegistrants.remove(handler);
    }

    public void unregisterForDisconnect(Handler handler) {
        this.mDisconnectRegistrants.remove(handler);
    }

    public void unregisterForDisplayInfo(Handler handler) {
        this.mDisplayInfoRegistrants.remove(handler);
    }

    public void unregisterForEcmTimerReset(Handler handler) {
        this.mEcmTimerResetRegistrants.remove(handler);
    }

    public void unregisterForInCallVoicePrivacyOff(Handler handler) {
        this.mInCallVoicePrivacyOffRegistrants.remove(handler);
    }

    public void unregisterForInCallVoicePrivacyOn(Handler handler) {
        this.mInCallVoicePrivacyOnRegistrants.remove(handler);
    }

    public void unregisterForIncomingRing(Handler handler) {
        this.mIncomingRingRegistrants.remove(handler);
    }

    public void unregisterForMmiComplete(Handler handler) {
        this.mMmiCompleteRegistrants.remove(handler);
    }

    public void unregisterForMmiInitiate(Handler handler) {
        this.mMmiInitiateRegistrants.remove(handler);
    }

    public void unregisterForNewRingingConnection(Handler handler) {
        this.mNewRingingConnectionRegistrants.remove(handler);
    }

    public void unregisterForOnHoldTone(Handler handler) {
        this.mOnHoldToneRegistrants.remove(handler);
    }

    public void unregisterForPostDialCharacter(Handler handler) {
        this.mPostDialCharacterRegistrants.remove(handler);
    }

    public void unregisterForPreciseCallStateChanged(Handler handler) {
        this.mPreciseCallStateRegistrants.remove(handler);
    }

    public void unregisterForResendIncallMute(Handler handler) {
        this.mResendIncallMuteRegistrants.remove(handler);
    }

    public void unregisterForRingbackTone(Handler handler) {
        this.mRingbackToneRegistrants.remove(handler);
    }

    public void unregisterForServiceStateChanged(Handler handler) {
        this.mServiceStateChangedRegistrants.remove(handler);
    }

    public void unregisterForSignalInfo(Handler handler) {
        this.mSignalInfoRegistrants.remove(handler);
    }

    public void unregisterForSubscriptionChange(Handler handler) {
        this.mActiveSubChangeRegistrants.remove(handler);
    }

    public void unregisterForSubscriptionInfoReady(Handler handler) {
        this.mSubscriptionInfoReadyRegistrants.remove(handler);
    }

    public void unregisterForSuppServiceFailed(Handler handler) {
        this.mSuppServiceFailedRegistrants.remove(handler);
    }

    public void unregisterForSuppServiceNotification(Handler handler) {
        this.mSuppServiceNotificationRegistrants.remove(handler);
    }

    public void unregisterForTtyModeReceived(Handler handler) {
        this.mTtyModeReceivedRegistrants.remove(handler);
    }

    public void unregisterForUnknownConnection(Handler handler) {
        this.mUnknownConnectionRegistrants.remove(handler);
    }

    public void unregisterPhone(Phone phone) {
        Phone phoneBase = getPhoneBase(phone);
        if (phoneBase != null && this.mPhones.contains(phoneBase)) {
            Rlog.d(LOG_TAG, "unregisterPhone(" + phone.getPhoneName() + " " + phone + ")");
            Phone imsPhone = phoneBase.getImsPhone();
            if (imsPhone != null) {
                unregisterPhone(imsPhone);
            }
            this.mPhones.remove(phoneBase);
            this.mRingingCalls.remove(phoneBase.getRingingCall());
            this.mBackgroundCalls.remove(phoneBase.getBackgroundCall());
            this.mForegroundCalls.remove(phoneBase.getForegroundCall());
            unregisterForPhoneStates(phoneBase);
            if (phoneBase != this.mDefaultPhone) {
                return;
            }
            if (this.mPhones.isEmpty()) {
                this.mDefaultPhone = null;
            } else {
                this.mDefaultPhone = (Phone) this.mPhones.get(0);
            }
        }
    }
}
