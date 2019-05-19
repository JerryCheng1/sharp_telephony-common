package com.android.internal.telephony.sip;

import android.content.Context;
import android.media.AudioManager;
import android.net.LinkProperties;
import android.net.rtp.AudioGroup;
import android.net.sip.SipAudioCall;
import android.net.sip.SipAudioCall.Listener;
import android.net.sip.SipErrorCode;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.net.sip.SipProfile.Builder;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.text.TextUtils;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.CallFailCause;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneInternalInterface.DataActivityState;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.SubscriptionInfoUpdater;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.uicc.IccFileHandler;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.codeaurora.ims.QtiViceInfo;

public class SipPhone extends SipPhoneBase {
    private static final boolean DBG = true;
    private static final String LOG_TAG = "SipPhone";
    private static final int TIMEOUT_ANSWER_CALL = 8;
    private static final int TIMEOUT_HOLD_CALL = 15;
    private static final long TIMEOUT_HOLD_PROCESSING = 1000;
    private static final int TIMEOUT_MAKE_CALL = 15;
    private static final boolean VDBG = false;
    private SipCall mBackgroundCall = new SipCall(this, null);
    private SipCall mForegroundCall = new SipCall(this, null);
    private SipProfile mProfile;
    private SipCall mRingingCall = new SipCall(this, null);
    private SipManager mSipManager;
    private long mTimeOfLastValidHoldRequest = System.currentTimeMillis();

    private abstract class SipAudioCallAdapter extends Listener {
        private static final boolean SACA_DBG = true;
        private static final String SACA_TAG = "SipAudioCallAdapter";

        /* synthetic */ SipAudioCallAdapter(SipPhone this$0, SipAudioCallAdapter sipAudioCallAdapter) {
            this();
        }

        public abstract void onCallEnded(int i);

        public abstract void onError(int i);

        private SipAudioCallAdapter() {
        }

        public void onCallEnded(SipAudioCall call) {
            int i;
            log("onCallEnded: call=" + call);
            if (call.isInCall()) {
                i = 2;
            } else {
                i = 1;
            }
            onCallEnded(i);
        }

        public void onCallBusy(SipAudioCall call) {
            log("onCallBusy: call=" + call);
            onCallEnded(4);
        }

        public void onError(SipAudioCall call, int errorCode, String errorMessage) {
            log("onError: call=" + call + " code=" + SipErrorCode.toString(errorCode) + ": " + errorMessage);
            switch (errorCode) {
                case -12:
                    onError(9);
                    return;
                case -11:
                    onError(11);
                    return;
                case -10:
                    onError(14);
                    return;
                case -8:
                    onError(10);
                    return;
                case -7:
                    onError(8);
                    return;
                case -6:
                    onError(7);
                    return;
                case -5:
                case SubscriptionInfoUpdater.SIM_REPOSITION /*-3*/:
                    onError(13);
                    return;
                case -2:
                    onError(12);
                    return;
                default:
                    onError(36);
                    return;
            }
        }

        private void log(String s) {
            Rlog.d(SACA_TAG, s);
        }
    }

    private class SipCall extends SipCallBase {
        private static final boolean SC_DBG = true;
        private static final String SC_TAG = "SipCall";
        private static final boolean SC_VDBG = false;

        /* synthetic */ SipCall(SipPhone this$0, SipCall sipCall) {
            this();
        }

        private SipCall() {
        }

        /* Access modifiers changed, original: 0000 */
        public void reset() {
            log("reset");
            this.mConnections.clear();
            setState(State.IDLE);
        }

        /* Access modifiers changed, original: 0000 */
        public void switchWith(SipCall that) {
            log("switchWith");
            synchronized (SipPhone.class) {
                SipCall tmp = new SipCall();
                tmp.takeOver(this);
                takeOver(that);
                that.takeOver(tmp);
            }
        }

        private void takeOver(SipCall that) {
            log("takeOver");
            this.mConnections = that.mConnections;
            this.mState = that.mState;
            for (Connection c : this.mConnections) {
                ((SipConnection) c).changeOwner(this);
            }
        }

        public Phone getPhone() {
            return SipPhone.this;
        }

        public List<Connection> getConnections() {
            ArrayList arrayList;
            synchronized (SipPhone.class) {
                arrayList = this.mConnections;
            }
            return arrayList;
        }

        /* Access modifiers changed, original: 0000 */
        public Connection dial(String originalNumber) throws SipException {
            log("dial: num=" + "xxx");
            String calleeSipUri = originalNumber;
            if (!originalNumber.contains("@")) {
                calleeSipUri = SipPhone.this.mProfile.getUriString().replaceFirst(Pattern.quote(SipPhone.this.mProfile.getUserName() + "@"), originalNumber + "@");
            }
            try {
                SipConnection c = new SipConnection(this, new Builder(calleeSipUri).build(), originalNumber);
                c.dial();
                this.mConnections.add(c);
                setState(State.DIALING);
                return c;
            } catch (ParseException e) {
                throw new SipException("dial", e);
            }
        }

        public void hangup() throws CallStateException {
            synchronized (SipPhone.class) {
                if (this.mState.isAlive()) {
                    log("hangup: call " + getState() + ": " + this + " on phone " + getPhone());
                    setState(State.DISCONNECTING);
                    CallStateException excp = null;
                    for (Connection c : this.mConnections) {
                        try {
                            c.hangup();
                        } catch (CallStateException e) {
                            excp = e;
                        }
                    }
                    if (excp != null) {
                        throw excp;
                    }
                } else {
                    log("hangup: dead call " + getState() + ": " + this + " on phone " + getPhone());
                }
            }
        }

        /* Access modifiers changed, original: 0000 */
        public SipConnection initIncomingCall(SipAudioCall sipAudioCall, boolean makeCallWait) {
            SipConnection c = new SipConnection(SipPhone.this, this, sipAudioCall.getPeerProfile());
            this.mConnections.add(c);
            State newState = makeCallWait ? State.WAITING : State.INCOMING;
            c.initIncomingCall(sipAudioCall, newState);
            setState(newState);
            SipPhone.this.notifyNewRingingConnectionP(c);
            return c;
        }

        /* Access modifiers changed, original: 0000 */
        public void rejectCall() throws CallStateException {
            log("rejectCall:");
            hangup();
        }

        /* Access modifiers changed, original: 0000 */
        public void acceptCall() throws CallStateException {
            log("acceptCall: accepting");
            if (this != SipPhone.this.mRingingCall) {
                throw new CallStateException("acceptCall() in a non-ringing call");
            } else if (this.mConnections.size() != 1) {
                throw new CallStateException("acceptCall() in a conf call");
            } else {
                ((SipConnection) this.mConnections.get(0)).acceptCall();
            }
        }

        private boolean isSpeakerOn() {
            return Boolean.valueOf(((AudioManager) SipPhone.this.mContext.getSystemService(QtiViceInfo.MEDIA_TYPE_AUDIO)).isSpeakerphoneOn()).booleanValue();
        }

        /* Access modifiers changed, original: 0000 */
        public void setAudioGroupMode() {
            AudioGroup audioGroup = getAudioGroup();
            if (audioGroup == null) {
                log("setAudioGroupMode: audioGroup == null ignore");
                return;
            }
            int mode = audioGroup.getMode();
            if (this.mState == State.HOLDING) {
                audioGroup.setMode(0);
            } else if (getMute()) {
                audioGroup.setMode(1);
            } else if (isSpeakerOn()) {
                audioGroup.setMode(3);
            } else {
                audioGroup.setMode(2);
            }
            log(String.format("setAudioGroupMode change: %d --> %d", new Object[]{Integer.valueOf(mode), Integer.valueOf(audioGroup.getMode())}));
        }

        /* Access modifiers changed, original: 0000 */
        public void hold() throws CallStateException {
            log("hold:");
            setState(State.HOLDING);
            for (Connection c : this.mConnections) {
                ((SipConnection) c).hold();
            }
            setAudioGroupMode();
        }

        /* Access modifiers changed, original: 0000 */
        public void unhold() throws CallStateException {
            log("unhold:");
            setState(State.ACTIVE);
            AudioGroup audioGroup = new AudioGroup();
            for (Connection c : this.mConnections) {
                ((SipConnection) c).unhold(audioGroup);
            }
            setAudioGroupMode();
        }

        /* Access modifiers changed, original: 0000 */
        public void setMute(boolean muted) {
            log("setMute: muted=" + muted);
            for (Connection c : this.mConnections) {
                ((SipConnection) c).setMute(muted);
            }
        }

        /* Access modifiers changed, original: 0000 */
        public boolean getMute() {
            boolean ret;
            if (this.mConnections.isEmpty()) {
                ret = false;
            } else {
                ret = ((SipConnection) this.mConnections.get(0)).getMute();
            }
            log("getMute: ret=" + ret);
            return ret;
        }

        /* Access modifiers changed, original: 0000 */
        public void merge(SipCall that) throws CallStateException {
            log("merge:");
            AudioGroup audioGroup = getAudioGroup();
            for (Connection c : (Connection[]) that.mConnections.toArray(new Connection[that.mConnections.size()])) {
                SipConnection conn = (SipConnection) c;
                add(conn);
                if (conn.getState() == State.HOLDING) {
                    conn.unhold(audioGroup);
                }
            }
            that.setState(State.IDLE);
        }

        private void add(SipConnection conn) {
            log("add:");
            SipCall call = conn.getCall();
            if (call != this) {
                if (call != null) {
                    call.mConnections.remove(conn);
                }
                this.mConnections.add(conn);
                conn.changeOwner(this);
            }
        }

        /* Access modifiers changed, original: 0000 */
        public void sendDtmf(char c) {
            AudioGroup audioGroup = getAudioGroup();
            if (audioGroup != null) {
                audioGroup.sendDtmf(convertDtmf(c));
            }
        }

        private int convertDtmf(char c) {
            int code = c - 48;
            if (code >= 0 && code <= 9) {
                return code;
            }
            switch (c) {
                case '#':
                    return 11;
                case '*':
                    return 10;
                case CallFailCause.BEARER_SERVICE_NOT_IMPLEMENTED /*65*/:
                    return 12;
                case 'B':
                    return 13;
                case 'C':
                    return 14;
                case CallFailCause.ACM_LIMIT_EXCEEDED /*68*/:
                    return 15;
                default:
                    throw new IllegalArgumentException("invalid DTMF char: " + c);
            }
        }

        /* Access modifiers changed, original: protected */
        public void setState(State newState) {
            if (this.mState != newState) {
                log("setState: cur state" + this.mState + " --> " + newState + ": " + this + ": on phone " + getPhone() + " " + this.mConnections.size());
                if (newState == State.ALERTING) {
                    this.mState = newState;
                    SipPhone.this.startRingbackTone();
                } else if (this.mState == State.ALERTING) {
                    SipPhone.this.stopRingbackTone();
                }
                this.mState = newState;
                SipPhone.this.updatePhoneState();
                SipPhone.this.notifyPreciseCallStateChanged();
            }
        }

        /* Access modifiers changed, original: 0000 */
        public void onConnectionStateChanged(SipConnection conn) {
            log("onConnectionStateChanged: conn=" + conn);
            if (this.mState != State.ACTIVE) {
                setState(conn.getState());
            }
        }

        /* Access modifiers changed, original: 0000 */
        public void onConnectionEnded(SipConnection conn) {
            log("onConnectionEnded: conn=" + conn);
            if (this.mState != State.DISCONNECTED) {
                boolean allConnectionsDisconnected = true;
                log("---check connections: " + this.mConnections.size());
                for (Connection c : this.mConnections) {
                    log("   state=" + c.getState() + ": " + c);
                    if (c.getState() != State.DISCONNECTED) {
                        allConnectionsDisconnected = false;
                        break;
                    }
                }
                if (allConnectionsDisconnected) {
                    setState(State.DISCONNECTED);
                }
            }
            SipPhone.this.notifyDisconnectP(conn);
        }

        private AudioGroup getAudioGroup() {
            if (this.mConnections.isEmpty()) {
                return null;
            }
            return ((SipConnection) this.mConnections.get(0)).getAudioGroup();
        }

        private void log(String s) {
            Rlog.d(SC_TAG, s);
        }
    }

    private class SipConnection extends SipConnectionBase {
        private static final boolean SCN_DBG = true;
        private static final String SCN_TAG = "SipConnection";
        private SipAudioCallAdapter mAdapter;
        private boolean mIncoming;
        private String mOriginalNumber;
        private SipCall mOwner;
        private SipProfile mPeer;
        private SipAudioCall mSipAudioCall;
        private State mState;

        public SipConnection(SipCall owner, SipProfile callee, String originalNumber) {
            super(originalNumber);
            this.mState = State.IDLE;
            this.mIncoming = false;
            this.mAdapter = new SipAudioCallAdapter(SipPhone.this) {
                /* Access modifiers changed, original: protected */
                public void onCallEnded(int cause) {
                    if (SipConnection.this.getDisconnectCause() != 3) {
                        SipConnection.this.setDisconnectCause(cause);
                    }
                    synchronized (SipPhone.class) {
                        String sessionState;
                        SipConnection.this.setState(State.DISCONNECTED);
                        SipAudioCall sipAudioCall = SipConnection.this.mSipAudioCall;
                        SipConnection.this.mSipAudioCall = null;
                        if (sipAudioCall == null) {
                            sessionState = "";
                        } else {
                            sessionState = sipAudioCall.getState() + ", ";
                        }
                        SipConnection.this.log("[SipAudioCallAdapter] onCallEnded: " + SipPhone.hidePii(SipConnection.this.mPeer.getUriString()) + ": " + sessionState + "cause: " + SipConnection.this.getDisconnectCause() + ", on phone " + SipConnection.this.getPhone());
                        if (sipAudioCall != null) {
                            sipAudioCall.setListener(null);
                            sipAudioCall.close();
                        }
                        SipConnection.this.mOwner.onConnectionEnded(SipConnection.this);
                    }
                }

                public void onCallEstablished(SipAudioCall call) {
                    onChanged(call);
                    if (SipConnection.this.mState == State.ACTIVE) {
                        call.startAudio();
                    }
                }

                public void onCallHeld(SipAudioCall call) {
                    onChanged(call);
                    if (SipConnection.this.mState == State.HOLDING) {
                        call.startAudio();
                    }
                }

                public void onChanged(SipAudioCall call) {
                    synchronized (SipPhone.class) {
                        State newState = SipPhone.getCallStateFrom(call);
                        if (SipConnection.this.mState == newState) {
                            return;
                        }
                        if (newState == State.INCOMING) {
                            SipConnection.this.setState(SipConnection.this.mOwner.getState());
                        } else {
                            if (SipConnection.this.mOwner == SipPhone.this.mRingingCall) {
                                if (SipPhone.this.mRingingCall.getState() == State.WAITING) {
                                    try {
                                        SipPhone.this.switchHoldingAndActive();
                                    } catch (CallStateException e) {
                                        onCallEnded(3);
                                        return;
                                    }
                                }
                                SipPhone.this.mForegroundCall.switchWith(SipPhone.this.mRingingCall);
                            }
                            SipConnection.this.setState(newState);
                        }
                        SipConnection.this.mOwner.onConnectionStateChanged(SipConnection.this);
                        SipConnection.this.log("onChanged: " + SipConnection.this.mPeer.getUriString() + ": " + SipConnection.this.mState + " on phone " + SipConnection.this.getPhone());
                    }
                }

                /* Access modifiers changed, original: protected */
                public void onError(int cause) {
                    SipConnection.this.log("onError: " + cause);
                    onCallEnded(cause);
                }
            };
            this.mOwner = owner;
            this.mPeer = callee;
            this.mOriginalNumber = originalNumber;
        }

        public SipConnection(SipPhone this$0, SipCall owner, SipProfile callee) {
            this(owner, callee, this$0.getUriString(callee));
        }

        public String getCnapName() {
            String displayName = this.mPeer.getDisplayName();
            return TextUtils.isEmpty(displayName) ? null : displayName;
        }

        public int getNumberPresentation() {
            return 1;
        }

        /* Access modifiers changed, original: 0000 */
        public void initIncomingCall(SipAudioCall sipAudioCall, State newState) {
            setState(newState);
            this.mSipAudioCall = sipAudioCall;
            sipAudioCall.setListener(this.mAdapter);
            this.mIncoming = true;
        }

        /* Access modifiers changed, original: 0000 */
        public void acceptCall() throws CallStateException {
            try {
                this.mSipAudioCall.answerCall(8);
            } catch (SipException e) {
                throw new CallStateException("acceptCall(): " + e);
            }
        }

        /* Access modifiers changed, original: 0000 */
        public void changeOwner(SipCall owner) {
            this.mOwner = owner;
        }

        /* Access modifiers changed, original: 0000 */
        public AudioGroup getAudioGroup() {
            if (this.mSipAudioCall == null) {
                return null;
            }
            return this.mSipAudioCall.getAudioGroup();
        }

        /* Access modifiers changed, original: 0000 */
        public void dial() throws SipException {
            setState(State.DIALING);
            this.mSipAudioCall = SipPhone.this.mSipManager.makeAudioCall(SipPhone.this.mProfile, this.mPeer, null, 15);
            this.mSipAudioCall.setListener(this.mAdapter);
        }

        /* Access modifiers changed, original: 0000 */
        public void hold() throws CallStateException {
            setState(State.HOLDING);
            try {
                this.mSipAudioCall.holdCall(15);
            } catch (SipException e) {
                throw new CallStateException("hold(): " + e);
            }
        }

        /* Access modifiers changed, original: 0000 */
        public void unhold(AudioGroup audioGroup) throws CallStateException {
            this.mSipAudioCall.setAudioGroup(audioGroup);
            setState(State.ACTIVE);
            try {
                this.mSipAudioCall.continueCall(15);
            } catch (SipException e) {
                throw new CallStateException("unhold(): " + e);
            }
        }

        /* Access modifiers changed, original: 0000 */
        public void setMute(boolean muted) {
            if (this.mSipAudioCall != null && muted != this.mSipAudioCall.isMuted()) {
                log("setState: prev muted=" + (!muted) + " new muted=" + muted);
                this.mSipAudioCall.toggleMute();
            }
        }

        /* Access modifiers changed, original: 0000 */
        public boolean getMute() {
            if (this.mSipAudioCall == null) {
                return false;
            }
            return this.mSipAudioCall.isMuted();
        }

        /* Access modifiers changed, original: protected */
        public void setState(State state) {
            if (state != this.mState) {
                super.setState(state);
                this.mState = state;
            }
        }

        public State getState() {
            return this.mState;
        }

        public boolean isIncoming() {
            return this.mIncoming;
        }

        public String getAddress() {
            return this.mOriginalNumber;
        }

        public SipCall getCall() {
            return this.mOwner;
        }

        /* Access modifiers changed, original: protected */
        public Phone getPhone() {
            return this.mOwner.getPhone();
        }

        public void hangup() throws CallStateException {
            int i = 3;
            synchronized (SipPhone.class) {
                log("hangup: conn=" + this.mPeer.getUriString() + ": " + this.mState + ": on phone " + getPhone().getPhoneName());
                if (this.mState.isAlive()) {
                    try {
                        SipAudioCall sipAudioCall = this.mSipAudioCall;
                        if (sipAudioCall != null) {
                            sipAudioCall.setListener(null);
                            sipAudioCall.endCall();
                        }
                        SipAudioCallAdapter sipAudioCallAdapter = this.mAdapter;
                        if (this.mState == State.INCOMING || this.mState == State.WAITING) {
                            i = 16;
                        }
                        sipAudioCallAdapter.onCallEnded(i);
                    } catch (SipException e) {
                        throw new CallStateException("hangup(): " + e);
                    } catch (Throwable th) {
                        SipAudioCallAdapter sipAudioCallAdapter2 = this.mAdapter;
                        if (this.mState == State.INCOMING || this.mState == State.WAITING) {
                            i = 16;
                        }
                        sipAudioCallAdapter2.onCallEnded(i);
                    }
                }
            }
        }

        public void separate() throws CallStateException {
            synchronized (SipPhone.class) {
                SipCall call;
                if (getPhone() == SipPhone.this) {
                    call = (SipCall) SipPhone.this.getBackgroundCall();
                } else {
                    call = (SipCall) SipPhone.this.getForegroundCall();
                }
                if (call.getState() != State.IDLE) {
                    throw new CallStateException("cannot put conn back to a call in non-idle state: " + call.getState());
                }
                log("separate: conn=" + this.mPeer.getUriString() + " from " + this.mOwner + " back to " + call);
                Phone originalPhone = getPhone();
                AudioGroup audioGroup = call.getAudioGroup();
                call.add(this);
                this.mSipAudioCall.setAudioGroup(audioGroup);
                originalPhone.switchHoldingAndActive();
                call = (SipCall) SipPhone.this.getForegroundCall();
                this.mSipAudioCall.startAudio();
                call.onConnectionStateChanged(this);
            }
        }

        private void log(String s) {
            Rlog.d(SCN_TAG, s);
        }
    }

    public /* bridge */ /* synthetic */ void activateCellBroadcastSms(int activate, Message response) {
        super.activateCellBroadcastSms(activate, response);
    }

    public /* bridge */ /* synthetic */ boolean canDial() {
        return super.canDial();
    }

    public /* bridge */ /* synthetic */ Connection dial(String dialString, UUSInfo uusInfo, int videoState, Bundle intentExtras) {
        return super.dial(dialString, uusInfo, videoState, intentExtras);
    }

    public /* bridge */ /* synthetic */ boolean disableDataConnectivity() {
        return super.disableDataConnectivity();
    }

    public /* bridge */ /* synthetic */ void disableLocationUpdates() {
        super.disableLocationUpdates();
    }

    public /* bridge */ /* synthetic */ boolean enableDataConnectivity() {
        return super.enableDataConnectivity();
    }

    public /* bridge */ /* synthetic */ void enableLocationUpdates() {
        super.enableLocationUpdates();
    }

    public /* bridge */ /* synthetic */ void getAvailableNetworks(Message response) {
        super.getAvailableNetworks(response);
    }

    public /* bridge */ /* synthetic */ int getBrand() {
        return super.getBrand();
    }

    public /* bridge */ /* synthetic */ boolean getCallForwardingIndicator() {
        return super.getCallForwardingIndicator();
    }

    public /* bridge */ /* synthetic */ void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        super.getCallForwardingOption(commandInterfaceCFReason, onComplete);
    }

    public /* bridge */ /* synthetic */ void getCellBroadcastSmsConfig(Message response) {
        super.getCellBroadcastSmsConfig(response);
    }

    public /* bridge */ /* synthetic */ CellLocation getCellLocation() {
        return super.getCellLocation();
    }

    public /* bridge */ /* synthetic */ List getCurrentDataConnectionList() {
        return super.getCurrentDataConnectionList();
    }

    public /* bridge */ /* synthetic */ DataActivityState getDataActivityState() {
        return super.getDataActivityState();
    }

    public /* bridge */ /* synthetic */ void getDataCallList(Message response) {
        super.getDataCallList(response);
    }

    public /* bridge */ /* synthetic */ DataState getDataConnectionState() {
        return super.getDataConnectionState();
    }

    public /* bridge */ /* synthetic */ DataState getDataConnectionState(String apnType) {
        return super.getDataConnectionState(apnType);
    }

    public /* bridge */ /* synthetic */ boolean getDataEnabled() {
        return super.getDataEnabled();
    }

    public /* bridge */ /* synthetic */ boolean getDataRoamingEnabled() {
        return super.getDataRoamingEnabled();
    }

    public /* bridge */ /* synthetic */ String getDeviceId() {
        return super.getDeviceId();
    }

    public /* bridge */ /* synthetic */ String getDeviceSvn() {
        return super.getDeviceSvn();
    }

    public /* bridge */ /* synthetic */ String getEsn() {
        return super.getEsn();
    }

    public /* bridge */ /* synthetic */ String getGroupIdLevel1() {
        return super.getGroupIdLevel1();
    }

    public /* bridge */ /* synthetic */ String getGroupIdLevel2() {
        return super.getGroupIdLevel2();
    }

    public /* bridge */ /* synthetic */ IccCard getIccCard() {
        return super.getIccCard();
    }

    public /* bridge */ /* synthetic */ IccFileHandler getIccFileHandler() {
        return super.getIccFileHandler();
    }

    public /* bridge */ /* synthetic */ IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return super.getIccPhoneBookInterfaceManager();
    }

    public /* bridge */ /* synthetic */ boolean getIccRecordsLoaded() {
        return super.getIccRecordsLoaded();
    }

    public /* bridge */ /* synthetic */ String getIccSerialNumber() {
        return super.getIccSerialNumber();
    }

    public /* bridge */ /* synthetic */ String getImei() {
        return super.getImei();
    }

    public /* bridge */ /* synthetic */ String getLine1AlphaTag() {
        return super.getLine1AlphaTag();
    }

    public /* bridge */ /* synthetic */ String getLine1Number() {
        return super.getLine1Number();
    }

    public /* bridge */ /* synthetic */ LinkProperties getLinkProperties(String apnType) {
        return super.getLinkProperties(apnType);
    }

    public /* bridge */ /* synthetic */ String getMccMncOnSimLock() {
        return super.getMccMncOnSimLock();
    }

    public /* bridge */ /* synthetic */ String getMeid() {
        return super.getMeid();
    }

    public /* bridge */ /* synthetic */ boolean getMessageWaitingIndicator() {
        return super.getMessageWaitingIndicator();
    }

    public /* bridge */ /* synthetic */ void getNeighboringCids(Message response) {
        super.getNeighboringCids(response);
    }

    public /* bridge */ /* synthetic */ List getPendingMmiCodes() {
        return super.getPendingMmiCodes();
    }

    public /* bridge */ /* synthetic */ int getPhoneType() {
        return super.getPhoneType();
    }

    public /* bridge */ /* synthetic */ SignalStrength getSignalStrength() {
        return super.getSignalStrength();
    }

    public /* bridge */ /* synthetic */ PhoneConstants.State getState() {
        return super.getState();
    }

    public /* bridge */ /* synthetic */ String getSubscriberId() {
        return super.getSubscriberId();
    }

    public /* bridge */ /* synthetic */ String getVoiceMailAlphaTag() {
        return super.getVoiceMailAlphaTag();
    }

    public /* bridge */ /* synthetic */ String getVoiceMailNumber() {
        return super.getVoiceMailNumber();
    }

    public /* bridge */ /* synthetic */ boolean handleInCallMmiCommands(String dialString) {
        return super.handleInCallMmiCommands(dialString);
    }

    public /* bridge */ /* synthetic */ boolean handlePinMmi(String dialString) {
        return super.handlePinMmi(dialString);
    }

    public /* bridge */ /* synthetic */ boolean isDataConnectivityPossible() {
        return super.isDataConnectivityPossible();
    }

    public /* bridge */ /* synthetic */ boolean isVideoEnabled() {
        return super.isVideoEnabled();
    }

    public /* bridge */ /* synthetic */ boolean needsOtaServiceProvisioning() {
        return super.needsOtaServiceProvisioning();
    }

    public /* bridge */ /* synthetic */ void notifyCallForwardingIndicator() {
        super.notifyCallForwardingIndicator();
    }

    public /* bridge */ /* synthetic */ void registerForRingbackTone(Handler h, int what, Object obj) {
        super.registerForRingbackTone(h, what, obj);
    }

    public /* bridge */ /* synthetic */ void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        super.registerForSuppServiceNotification(h, what, obj);
    }

    public /* bridge */ /* synthetic */ void saveClirSetting(int commandInterfaceCLIRMode) {
        super.saveClirSetting(commandInterfaceCLIRMode);
    }

    public /* bridge */ /* synthetic */ void selectNetworkManually(OperatorInfo network, boolean persistSelection, Message response) {
        super.selectNetworkManually(network, persistSelection, response);
    }

    public /* bridge */ /* synthetic */ void sendEmergencyCallStateChange(boolean callActive) {
        super.sendEmergencyCallStateChange(callActive);
    }

    public /* bridge */ /* synthetic */ void sendUssdResponse(String ussdMessge) {
        super.sendUssdResponse(ussdMessge);
    }

    public /* bridge */ /* synthetic */ void setBroadcastEmergencyCallStateChanges(boolean broadcast) {
        super.setBroadcastEmergencyCallStateChanges(broadcast);
    }

    public /* bridge */ /* synthetic */ void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int timerSeconds, Message onComplete) {
        super.setCallForwardingOption(commandInterfaceCFAction, commandInterfaceCFReason, dialingNumber, timerSeconds, onComplete);
    }

    public /* bridge */ /* synthetic */ void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        super.setCellBroadcastSmsConfig(configValuesArray, response);
    }

    public /* bridge */ /* synthetic */ void setDataEnabled(boolean enable) {
        super.setDataEnabled(enable);
    }

    public /* bridge */ /* synthetic */ void setDataRoamingEnabled(boolean enable) {
        super.setDataRoamingEnabled(enable);
    }

    public /* bridge */ /* synthetic */ boolean setLine1Number(String alphaTag, String number, Message onComplete) {
        return super.setLine1Number(alphaTag, number, onComplete);
    }

    public /* bridge */ /* synthetic */ void setNetworkSelectionModeAutomatic(Message response) {
        super.setNetworkSelectionModeAutomatic(response);
    }

    public /* bridge */ /* synthetic */ void setOnPostDialCharacter(Handler h, int what, Object obj) {
        super.setOnPostDialCharacter(h, what, obj);
    }

    public /* bridge */ /* synthetic */ void setRadioPower(boolean power) {
        super.setRadioPower(power);
    }

    public /* bridge */ /* synthetic */ void setVoiceMailNumber(String alphaTag, String voiceMailNumber, Message onComplete) {
        super.setVoiceMailNumber(alphaTag, voiceMailNumber, onComplete);
    }

    public /* bridge */ /* synthetic */ void startRingbackTone() {
        super.startRingbackTone();
    }

    public /* bridge */ /* synthetic */ void stopRingbackTone() {
        super.stopRingbackTone();
    }

    public /* bridge */ /* synthetic */ void unregisterForRingbackTone(Handler h) {
        super.unregisterForRingbackTone(h);
    }

    public /* bridge */ /* synthetic */ void unregisterForSuppServiceNotification(Handler h) {
        super.unregisterForSuppServiceNotification(h);
    }

    public /* bridge */ /* synthetic */ void updateServiceLocation() {
        super.updateServiceLocation();
    }

    SipPhone(Context context, PhoneNotifier notifier, SipProfile profile) {
        super("SIP:" + profile.getUriString(), context, notifier);
        log("new SipPhone: " + hidePii(profile.getUriString()));
        this.mRingingCall = new SipCall(this, null);
        this.mForegroundCall = new SipCall(this, null);
        this.mBackgroundCall = new SipCall(this, null);
        this.mProfile = profile;
        this.mSipManager = SipManager.newInstance(context);
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof SipPhone)) {
            return false;
        }
        return this.mProfile.getUriString().equals(((SipPhone) o).mProfile.getUriString());
    }

    public String getSipUri() {
        return this.mProfile.getUriString();
    }

    public boolean equals(SipPhone phone) {
        return getSipUri().equals(phone.getSipUri());
    }

    /* JADX WARNING: Missing block: B:30:0x009f, code skipped:
            return r1;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public Connection takeIncomingCall(Object incomingCall) {
        synchronized (SipPhone.class) {
            if (!(incomingCall instanceof SipAudioCall)) {
                log("takeIncomingCall: ret=null, not a SipAudioCall");
                return null;
            } else if (this.mRingingCall.getState().isAlive()) {
                log("takeIncomingCall: ret=null, ringingCall not alive");
                return null;
            } else if (this.mForegroundCall.getState().isAlive() && this.mBackgroundCall.getState().isAlive()) {
                log("takeIncomingCall: ret=null, foreground and background both alive");
                return null;
            } else {
                try {
                    SipAudioCall sipAudioCall = (SipAudioCall) incomingCall;
                    log("takeIncomingCall: taking call from: " + sipAudioCall.getPeerProfile().getUriString());
                    if (sipAudioCall.getLocalProfile().getUriString().equals(this.mProfile.getUriString())) {
                        Connection connection = this.mRingingCall.initIncomingCall(sipAudioCall, this.mForegroundCall.getState().isAlive());
                        if (sipAudioCall.getState() != 3) {
                            log("    takeIncomingCall: call cancelled !!");
                            this.mRingingCall.reset();
                            connection = null;
                        }
                    }
                } catch (Exception e) {
                    log("    takeIncomingCall: exception e=" + e);
                    this.mRingingCall.reset();
                }
                log("takeIncomingCall: NOT taking !!");
                return null;
            }
        }
    }

    public void acceptCall(int videoState) throws CallStateException {
        synchronized (SipPhone.class) {
            if (this.mRingingCall.getState() == State.INCOMING || this.mRingingCall.getState() == State.WAITING) {
                log("acceptCall: accepting");
                this.mRingingCall.setMute(false);
                this.mRingingCall.acceptCall();
            } else {
                log("acceptCall: throw CallStateException(\"phone not ringing\")");
                throw new CallStateException("phone not ringing");
            }
        }
    }

    public void rejectCall() throws CallStateException {
        synchronized (SipPhone.class) {
            if (this.mRingingCall.getState().isRinging()) {
                log("rejectCall: rejecting");
                this.mRingingCall.rejectCall();
            } else {
                log("rejectCall: throw CallStateException(\"phone not ringing\")");
                throw new CallStateException("phone not ringing");
            }
        }
    }

    public Connection dial(String dialString, int videoState) throws CallStateException {
        Connection dialInternal;
        synchronized (SipPhone.class) {
            dialInternal = dialInternal(dialString, videoState);
        }
        return dialInternal;
    }

    public Connection dial(String dialString, int videoState, int prefix) throws CallStateException {
        if (!TelBrand.IS_DCM) {
            return null;
        }
        throw new CallStateException("Dial with CallDetails is not supported in this phone " + this);
    }

    public Connection dial(String dialString, UUSInfo uusInfo, int videoState, Bundle intentExtras, int prefix) throws CallStateException {
        if (!TelBrand.IS_DCM) {
            return null;
        }
        throw new CallStateException("Dial with CallDetails is not supported in this phone " + this);
    }

    private Connection dialInternal(String dialString, int videoState) throws CallStateException {
        log("dialInternal: dialString=" + hidePii(dialString));
        clearDisconnected();
        if (canDial()) {
            if (this.mForegroundCall.getState() == State.ACTIVE) {
                switchHoldingAndActive();
            }
            if (this.mForegroundCall.getState() != State.IDLE) {
                throw new CallStateException("cannot dial in current state");
            }
            this.mForegroundCall.setMute(false);
            try {
                return this.mForegroundCall.dial(dialString);
            } catch (SipException e) {
                loge("dialInternal: ", e);
                throw new CallStateException("dial error: " + e);
            }
        }
        throw new CallStateException("dialInternal: cannot dial in current state");
    }

    public void switchHoldingAndActive() throws CallStateException {
        if (isHoldTimeoutExpired()) {
            log("switchHoldingAndActive: switch fg and bg");
            synchronized (SipPhone.class) {
                this.mForegroundCall.switchWith(this.mBackgroundCall);
                if (this.mBackgroundCall.getState().isAlive()) {
                    this.mBackgroundCall.hold();
                }
                if (this.mForegroundCall.getState().isAlive()) {
                    this.mForegroundCall.unhold();
                }
            }
            return;
        }
        log("switchHoldingAndActive: Disregarded! Under 1000 ms...");
    }

    public boolean canConference() {
        log("canConference: ret=true");
        return true;
    }

    public void conference() throws CallStateException {
        synchronized (SipPhone.class) {
            if (this.mForegroundCall.getState() == State.ACTIVE && this.mForegroundCall.getState() == State.ACTIVE) {
                log("conference: merge fg & bg");
                this.mForegroundCall.merge(this.mBackgroundCall);
            } else {
                throw new CallStateException("wrong state to merge calls: fg=" + this.mForegroundCall.getState() + ", bg=" + this.mBackgroundCall.getState());
            }
        }
    }

    public void conference(Call that) throws CallStateException {
        synchronized (SipPhone.class) {
            if (that instanceof SipCall) {
                this.mForegroundCall.merge((SipCall) that);
            } else {
                throw new CallStateException("expect " + SipCall.class + ", cannot merge with " + that.getClass());
            }
        }
    }

    public boolean canTransfer() {
        return false;
    }

    public void explicitCallTransfer() {
    }

    public void clearDisconnected() {
        synchronized (SipPhone.class) {
            this.mRingingCall.clearDisconnected();
            this.mForegroundCall.clearDisconnected();
            this.mBackgroundCall.clearDisconnected();
            updatePhoneState();
            notifyPreciseCallStateChanged();
        }
    }

    public void sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            loge("sendDtmf called with invalid character '" + c + "'");
        } else if (this.mForegroundCall.getState().isAlive()) {
            synchronized (SipPhone.class) {
                this.mForegroundCall.sendDtmf(c);
            }
        }
    }

    public void startDtmf(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            sendDtmf(c);
        } else {
            loge("startDtmf called with invalid character '" + c + "'");
        }
    }

    public void stopDtmf() {
    }

    public void sendBurstDtmf(String dtmfString) {
        loge("sendBurstDtmf() is a CDMA method");
    }

    public void getCallForwardingOption(int commandInterfaceCFReason, int serviceClass, Message onComplete) {
        loge("call forwarding not supported");
    }

    public void getOutgoingCallerIdDisplay(Message onComplete) {
        AsyncResult.forMessage(onComplete, null, null);
        onComplete.sendToTarget();
    }

    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode, Message onComplete) {
        AsyncResult.forMessage(onComplete, null, null);
        onComplete.sendToTarget();
    }

    public void getCallWaiting(Message onComplete) {
        AsyncResult.forMessage(onComplete, null, null);
        onComplete.sendToTarget();
    }

    public void setCallBarring(String facility, boolean lockState, String password, Message onComplete) {
        loge("call barring not supported");
    }

    public void getCallBarring(String facility, Message onComplete) {
        loge("call barring not supported");
    }

    public void setCallWaiting(boolean enable, Message onComplete) {
        loge("call waiting not supported");
    }

    public void setEchoSuppressionEnabled() {
        synchronized (SipPhone.class) {
            if (((AudioManager) this.mContext.getSystemService(QtiViceInfo.MEDIA_TYPE_AUDIO)).getParameters("ec_supported").contains("off")) {
                this.mForegroundCall.setAudioGroupMode();
            }
        }
    }

    public void setMute(boolean muted) {
        synchronized (SipPhone.class) {
            this.mForegroundCall.setMute(muted);
        }
    }

    public boolean getMute() {
        if (this.mForegroundCall.getState().isAlive()) {
            return this.mForegroundCall.getMute();
        }
        return this.mBackgroundCall.getMute();
    }

    public Call getForegroundCall() {
        return this.mForegroundCall;
    }

    public Call getBackgroundCall() {
        return this.mBackgroundCall;
    }

    public Call getRingingCall() {
        return this.mRingingCall;
    }

    public ServiceState getServiceState() {
        return super.getServiceState();
    }

    private String getUriString(SipProfile p) {
        return p.getUserName() + "@" + getSipDomain(p);
    }

    private String getSipDomain(SipProfile p) {
        String domain = p.getSipDomain();
        if (domain.endsWith(":5060")) {
            return domain.substring(0, domain.length() - 5);
        }
        return domain;
    }

    private static State getCallStateFrom(SipAudioCall sipAudioCall) {
        if (sipAudioCall.isOnHold()) {
            return State.HOLDING;
        }
        int sessionState = sipAudioCall.getState();
        switch (sessionState) {
            case 0:
                return State.IDLE;
            case 3:
            case 4:
                return State.INCOMING;
            case 5:
                return State.DIALING;
            case 6:
                return State.ALERTING;
            case 7:
                return State.DISCONNECTING;
            case 8:
                return State.ACTIVE;
            default:
                slog("illegal connection state: " + sessionState);
                return State.DISCONNECTED;
        }
    }

    private synchronized boolean isHoldTimeoutExpired() {
        long currTime = System.currentTimeMillis();
        if (currTime - this.mTimeOfLastValidHoldRequest <= TIMEOUT_HOLD_PROCESSING) {
            return false;
        }
        this.mTimeOfLastValidHoldRequest = currTime;
        return true;
    }

    private void log(String s) {
        Rlog.d(LOG_TAG, s);
    }

    private static void slog(String s) {
        Rlog.d(LOG_TAG, s);
    }

    private void loge(String s) {
        Rlog.e(LOG_TAG, s);
    }

    private void loge(String s, Exception e) {
        Rlog.e(LOG_TAG, s, e);
    }

    public static String hidePii(String s) {
        return "xxxxx";
    }
}
