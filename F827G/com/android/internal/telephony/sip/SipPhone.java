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
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.Phone.DataActivityState;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.SubscriptionInfoUpdater;
import com.android.internal.telephony.uicc.IccFileHandler;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class SipPhone extends SipPhoneBase {
    private static final boolean DBG = true;
    private static final String LOG_TAG = "SipPhone";
    private static final int TIMEOUT_ANSWER_CALL = 8;
    private static final int TIMEOUT_HOLD_CALL = 15;
    private static final int TIMEOUT_MAKE_CALL = 15;
    private static final boolean VDBG = false;
    private SipCall mBackgroundCall = new SipCall();
    private SipCall mForegroundCall = new SipCall();
    private SipProfile mProfile;
    private SipCall mRingingCall = new SipCall();
    private SipManager mSipManager;

    private abstract class SipAudioCallAdapter extends Listener {
        private static final boolean SACA_DBG = true;
        private static final String SACA_TAG = "SipAudioCallAdapter";

        private SipAudioCallAdapter() {
        }

        private void log(String str) {
            Rlog.d(SACA_TAG, str);
        }

        public void onCallBusy(SipAudioCall sipAudioCall) {
            log("onCallBusy: call=" + sipAudioCall);
            onCallEnded(4);
        }

        public abstract void onCallEnded(int i);

        public void onCallEnded(SipAudioCall sipAudioCall) {
            log("onCallEnded: call=" + sipAudioCall);
            onCallEnded(sipAudioCall.isInCall() ? 2 : 1);
        }

        public abstract void onError(int i);

        public void onError(SipAudioCall sipAudioCall, int i, String str) {
            log("onError: call=" + sipAudioCall + " code=" + SipErrorCode.toString(i) + ": " + str);
            switch (i) {
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
    }

    private class SipCall extends SipCallBase {
        private static final boolean SC_DBG = true;
        private static final String SC_TAG = "SipCall";
        private static final boolean SC_VDBG = false;

        private SipCall() {
        }

        private void add(SipConnection sipConnection) {
            log("add:");
            SipCall call = sipConnection.getCall();
            if (call != this) {
                if (call != null) {
                    call.mConnections.remove(sipConnection);
                }
                this.mConnections.add(sipConnection);
                sipConnection.changeOwner(this);
            }
        }

        private int convertDtmf(char c) {
            int i = c - 48;
            if (i >= 0 && i <= 9) {
                return i;
            }
            switch (c) {
                case '#':
                    return 11;
                case '*':
                    return 10;
                case 'A':
                    return 12;
                case 'B':
                    return 13;
                case 'C':
                    return 14;
                case 'D':
                    return 15;
                default:
                    throw new IllegalArgumentException("invalid DTMF char: " + c);
            }
        }

        private AudioGroup getAudioGroup() {
            return this.mConnections.isEmpty() ? null : ((SipConnection) this.mConnections.get(0)).getAudioGroup();
        }

        private boolean isSpeakerOn() {
            return Boolean.valueOf(((AudioManager) SipPhone.this.mContext.getSystemService("audio")).isSpeakerphoneOn()).booleanValue();
        }

        private void log(String str) {
            Rlog.d(SC_TAG, str);
        }

        private void takeOver(SipCall sipCall) {
            log("takeOver");
            this.mConnections = sipCall.mConnections;
            this.mState = sipCall.mState;
            Iterator it = this.mConnections.iterator();
            while (it.hasNext()) {
                ((SipConnection) ((Connection) it.next())).changeOwner(this);
            }
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

        /* Access modifiers changed, original: 0000 */
        public Connection dial(String str) throws SipException {
            String str2;
            log("dial: num=" + "xxx");
            if (str.contains("@")) {
                str2 = str;
            } else {
                str2 = SipPhone.this.mProfile.getUriString().replaceFirst(Pattern.quote(SipPhone.this.mProfile.getUserName() + "@"), str + "@");
            }
            try {
                SipConnection sipConnection = new SipConnection(this, new Builder(str2).build(), str);
                sipConnection.dial();
                this.mConnections.add(sipConnection);
                setState(State.DIALING);
                return sipConnection;
            } catch (ParseException e) {
                throw new SipException("dial", e);
            }
        }

        public List<Connection> getConnections() {
            ArrayList arrayList;
            synchronized (SipPhone.class) {
                try {
                    arrayList = this.mConnections;
                } catch (Throwable th) {
                    Class cls = SipPhone.class;
                }
            }
            return arrayList;
        }

        /* Access modifiers changed, original: 0000 */
        public boolean getMute() {
            boolean z = false;
            if (!this.mConnections.isEmpty()) {
                z = ((SipConnection) this.mConnections.get(0)).getMute();
            }
            log("getMute: ret=" + z);
            return z;
        }

        public Phone getPhone() {
            return SipPhone.this;
        }

        public void hangup() throws CallStateException {
            synchronized (SipPhone.class) {
                try {
                    if (this.mState.isAlive()) {
                        log("hangup: call " + getState() + ": " + this + " on phone " + getPhone());
                        setState(State.DISCONNECTING);
                        Iterator it = this.mConnections.iterator();
                        CallStateException callStateException = null;
                        while (it.hasNext()) {
                            try {
                                ((Connection) it.next()).hangup();
                            } catch (CallStateException e) {
                                callStateException = e;
                            }
                        }
                        if (callStateException != null) {
                            throw callStateException;
                        }
                    }
                    log("hangup: dead call " + getState() + ": " + this + " on phone " + getPhone());
                } catch (Throwable th) {
                    Class cls = SipPhone.class;
                }
            }
        }

        /* Access modifiers changed, original: 0000 */
        public void hold() throws CallStateException {
            log("hold:");
            setState(State.HOLDING);
            Iterator it = this.mConnections.iterator();
            while (it.hasNext()) {
                ((SipConnection) ((Connection) it.next())).hold();
            }
            setAudioGroupMode();
        }

        /* Access modifiers changed, original: 0000 */
        public SipConnection initIncomingCall(SipAudioCall sipAudioCall, boolean z) {
            SipConnection sipConnection = new SipConnection(SipPhone.this, this, sipAudioCall.getPeerProfile());
            this.mConnections.add(sipConnection);
            State state = z ? State.WAITING : State.INCOMING;
            sipConnection.initIncomingCall(sipAudioCall, state);
            setState(state);
            SipPhone.this.notifyNewRingingConnectionP(sipConnection);
            return sipConnection;
        }

        /* Access modifiers changed, original: 0000 */
        public void merge(SipCall sipCall) throws CallStateException {
            log("merge:");
            AudioGroup audioGroup = getAudioGroup();
            for (Connection connection : (Connection[]) sipCall.mConnections.toArray(new Connection[sipCall.mConnections.size()])) {
                SipConnection sipConnection = (SipConnection) connection;
                add(sipConnection);
                if (sipConnection.getState() == State.HOLDING) {
                    sipConnection.unhold(audioGroup);
                }
            }
            sipCall.setState(State.IDLE);
        }

        /* Access modifiers changed, original: 0000 */
        public void onConnectionEnded(SipConnection sipConnection) {
            log("onConnectionEnded: conn=" + sipConnection);
            if (this.mState != State.DISCONNECTED) {
                Object obj;
                log("---check connections: " + this.mConnections.size());
                Iterator it = this.mConnections.iterator();
                while (it.hasNext()) {
                    Connection connection = (Connection) it.next();
                    log("   state=" + connection.getState() + ": " + connection);
                    if (connection.getState() != State.DISCONNECTED) {
                        obj = null;
                        break;
                    }
                }
                int obj2 = 1;
                if (obj2 != null) {
                    setState(State.DISCONNECTED);
                }
            }
            SipPhone.this.notifyDisconnectP(sipConnection);
        }

        /* Access modifiers changed, original: 0000 */
        public void onConnectionStateChanged(SipConnection sipConnection) {
            log("onConnectionStateChanged: conn=" + sipConnection);
            if (this.mState != State.ACTIVE) {
                setState(sipConnection.getState());
            }
        }

        /* Access modifiers changed, original: 0000 */
        public void rejectCall() throws CallStateException {
            log("rejectCall:");
            hangup();
        }

        /* Access modifiers changed, original: 0000 */
        public void reset() {
            log("reset");
            this.mConnections.clear();
            setState(State.IDLE);
        }

        /* Access modifiers changed, original: 0000 */
        public void sendDtmf(char c) {
            AudioGroup audioGroup = getAudioGroup();
            if (audioGroup != null) {
                audioGroup.sendDtmf(convertDtmf(c));
            }
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
        public void setMute(boolean z) {
            log("setMute: muted=" + z);
            Iterator it = this.mConnections.iterator();
            while (it.hasNext()) {
                ((SipConnection) ((Connection) it.next())).setMute(z);
            }
        }

        /* Access modifiers changed, original: protected */
        public void setState(State state) {
            if (this.mState != state) {
                log("setState: cur state" + this.mState + " --> " + state + ": " + this + ": on phone " + getPhone() + " " + this.mConnections.size());
                if (state == State.ALERTING) {
                    this.mState = state;
                    SipPhone.this.startRingbackTone();
                } else if (this.mState == State.ALERTING) {
                    SipPhone.this.stopRingbackTone();
                }
                this.mState = state;
                SipPhone.this.updatePhoneState();
                SipPhone.this.notifyPreciseCallStateChanged();
            }
        }

        /* Access modifiers changed, original: 0000 */
        public void switchWith(SipCall sipCall) {
            log("switchWith");
            synchronized (SipPhone.class) {
                try {
                    SipCall sipCall2 = new SipCall();
                    sipCall2.takeOver(this);
                    takeOver(sipCall);
                    sipCall.takeOver(sipCall2);
                } catch (Throwable th) {
                    Class cls = SipPhone.class;
                }
            }
        }

        /* Access modifiers changed, original: 0000 */
        public void unhold() throws CallStateException {
            log("unhold:");
            setState(State.ACTIVE);
            AudioGroup audioGroup = new AudioGroup();
            Iterator it = this.mConnections.iterator();
            while (it.hasNext()) {
                ((SipConnection) ((Connection) it.next())).unhold(audioGroup);
            }
            setAudioGroupMode();
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

        public SipConnection(SipPhone sipPhone, SipCall sipCall, SipProfile sipProfile) {
            this(sipCall, sipProfile, sipPhone.getUriString(sipProfile));
        }

        public SipConnection(SipCall sipCall, SipProfile sipProfile, String str) {
            super(str);
            this.mState = State.IDLE;
            this.mIncoming = false;
            this.mAdapter = new SipAudioCallAdapter() {
                {
                    SipPhone sipPhone = SipPhone.this;
                }

                /* Access modifiers changed, original: protected */
                public void onCallEnded(int i) {
                    if (SipConnection.this.getDisconnectCause() != 3) {
                        SipConnection.this.setDisconnectCause(i);
                    }
                    synchronized (SipPhone.class) {
                        try {
                            SipConnection.this.setState(State.DISCONNECTED);
                            SipAudioCall access$600 = SipConnection.this.mSipAudioCall;
                            SipConnection.this.mSipAudioCall = null;
                            SipConnection.this.log("[SipAudioCallAdapter] onCallEnded: " + SipConnection.this.mPeer.getUriString() + ": " + (access$600 == null ? "" : access$600.getState() + ", ") + "cause: " + SipConnection.this.getDisconnectCause() + ", on phone " + SipConnection.this.getPhone());
                            if (access$600 != null) {
                                access$600.setListener(null);
                                access$600.close();
                            }
                            SipConnection.this.mOwner.onConnectionEnded(SipConnection.this);
                        } catch (Throwable th) {
                            Class cls = SipPhone.class;
                        }
                    }
                }

                public void onCallEstablished(SipAudioCall sipAudioCall) {
                    onChanged(sipAudioCall);
                    if (SipConnection.this.mState == State.ACTIVE) {
                        sipAudioCall.startAudio();
                    }
                }

                public void onCallHeld(SipAudioCall sipAudioCall) {
                    onChanged(sipAudioCall);
                    if (SipConnection.this.mState == State.HOLDING) {
                        sipAudioCall.startAudio();
                    }
                }

                /* JADX WARNING: No exception handlers in catch block: Catch:{  } */
                public void onChanged(android.net.sip.SipAudioCall r4) {
                    /*
                    r3 = this;
                    r0 = com.android.internal.telephony.sip.SipPhone.class;
                    monitor-enter(r0);
                    r0 = com.android.internal.telephony.sip.SipPhone.getCallStateFrom(r4);	 Catch:{ all -> 0x0077 }
                    r1 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ all -> 0x0077 }
                    r1 = r1.mState;	 Catch:{ all -> 0x0077 }
                    if (r1 != r0) goto L_0x0013;
                L_0x000f:
                    r0 = com.android.internal.telephony.sip.SipPhone.class;
                    monitor-exit(r0);	 Catch:{ all -> 0x0077 }
                L_0x0012:
                    return;
                L_0x0013:
                    r1 = com.android.internal.telephony.Call.State.INCOMING;	 Catch:{ all -> 0x0077 }
                    if (r0 != r1) goto L_0x007c;
                L_0x0017:
                    r0 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ all -> 0x0077 }
                    r1 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ all -> 0x0077 }
                    r1 = r1.mOwner;	 Catch:{ all -> 0x0077 }
                    r1 = r1.getState();	 Catch:{ all -> 0x0077 }
                    r0.setState(r1);	 Catch:{ all -> 0x0077 }
                L_0x0026:
                    r0 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ all -> 0x0077 }
                    r0 = r0.mOwner;	 Catch:{ all -> 0x0077 }
                    r1 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ all -> 0x0077 }
                    r0.onConnectionStateChanged(r1);	 Catch:{ all -> 0x0077 }
                    r0 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ all -> 0x0077 }
                    r1 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0077 }
                    r1.<init>();	 Catch:{ all -> 0x0077 }
                    r2 = "onChanged: ";
                    r1 = r1.append(r2);	 Catch:{ all -> 0x0077 }
                    r2 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ all -> 0x0077 }
                    r2 = r2.mPeer;	 Catch:{ all -> 0x0077 }
                    r2 = r2.getUriString();	 Catch:{ all -> 0x0077 }
                    r1 = r1.append(r2);	 Catch:{ all -> 0x0077 }
                    r2 = ": ";
                    r1 = r1.append(r2);	 Catch:{ all -> 0x0077 }
                    r2 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ all -> 0x0077 }
                    r2 = r2.mState;	 Catch:{ all -> 0x0077 }
                    r1 = r1.append(r2);	 Catch:{ all -> 0x0077 }
                    r2 = " on phone ";
                    r1 = r1.append(r2);	 Catch:{ all -> 0x0077 }
                    r2 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ all -> 0x0077 }
                    r2 = r2.getPhone();	 Catch:{ all -> 0x0077 }
                    r1 = r1.append(r2);	 Catch:{ all -> 0x0077 }
                    r1 = r1.toString();	 Catch:{ all -> 0x0077 }
                    r0.log(r1);	 Catch:{ all -> 0x0077 }
                    r0 = com.android.internal.telephony.sip.SipPhone.class;
                    monitor-exit(r0);	 Catch:{ all -> 0x0077 }
                    goto L_0x0012;
                L_0x0077:
                    r0 = move-exception;
                    r1 = com.android.internal.telephony.sip.SipPhone.class;
                    monitor-exit(r1);	 Catch:{ all -> 0x0077 }
                    throw r0;
                L_0x007c:
                    r1 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ all -> 0x0077 }
                    r1 = r1.mOwner;	 Catch:{ all -> 0x0077 }
                    r2 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ all -> 0x0077 }
                    r2 = com.android.internal.telephony.sip.SipPhone.this;	 Catch:{ all -> 0x0077 }
                    r2 = r2.mRingingCall;	 Catch:{ all -> 0x0077 }
                    if (r1 != r2) goto L_0x00b6;
                L_0x008c:
                    r1 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ all -> 0x0077 }
                    r1 = com.android.internal.telephony.sip.SipPhone.this;	 Catch:{ all -> 0x0077 }
                    r1 = r1.mRingingCall;	 Catch:{ all -> 0x0077 }
                    r1 = r1.getState();	 Catch:{ all -> 0x0077 }
                    r2 = com.android.internal.telephony.Call.State.WAITING;	 Catch:{ all -> 0x0077 }
                    if (r1 != r2) goto L_0x00a3;
                L_0x009c:
                    r1 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ CallStateException -> 0x00bd }
                    r1 = com.android.internal.telephony.sip.SipPhone.this;	 Catch:{ CallStateException -> 0x00bd }
                    r1.switchHoldingAndActive();	 Catch:{ CallStateException -> 0x00bd }
                L_0x00a3:
                    r1 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ all -> 0x0077 }
                    r1 = com.android.internal.telephony.sip.SipPhone.this;	 Catch:{ all -> 0x0077 }
                    r1 = r1.mForegroundCall;	 Catch:{ all -> 0x0077 }
                    r2 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ all -> 0x0077 }
                    r2 = com.android.internal.telephony.sip.SipPhone.this;	 Catch:{ all -> 0x0077 }
                    r2 = r2.mRingingCall;	 Catch:{ all -> 0x0077 }
                    r1.switchWith(r2);	 Catch:{ all -> 0x0077 }
                L_0x00b6:
                    r1 = com.android.internal.telephony.sip.SipPhone.SipConnection.this;	 Catch:{ all -> 0x0077 }
                    r1.setState(r0);	 Catch:{ all -> 0x0077 }
                    goto L_0x0026;
                L_0x00bd:
                    r0 = move-exception;
                    r0 = 3;
                    r3.onCallEnded(r0);	 Catch:{ all -> 0x0077 }
                    r0 = com.android.internal.telephony.sip.SipPhone.class;
                    monitor-exit(r0);	 Catch:{ all -> 0x0077 }
                    goto L_0x0012;
                    */
                    throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.sip.SipPhone$SipConnection$AnonymousClass1.onChanged(android.net.sip.SipAudioCall):void");
                }

                /* Access modifiers changed, original: protected */
                public void onError(int i) {
                    SipConnection.this.log("onError: " + i);
                    onCallEnded(i);
                }
            };
            this.mOwner = sipCall;
            this.mPeer = sipProfile;
            this.mOriginalNumber = str;
        }

        private void log(String str) {
            Rlog.d(SCN_TAG, str);
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
        public void changeOwner(SipCall sipCall) {
            this.mOwner = sipCall;
        }

        /* Access modifiers changed, original: 0000 */
        public void dial() throws SipException {
            setState(State.DIALING);
            this.mSipAudioCall = SipPhone.this.mSipManager.makeAudioCall(SipPhone.this.mProfile, this.mPeer, null, 15);
            this.mSipAudioCall.setListener(this.mAdapter);
        }

        public String getAddress() {
            return this.mOriginalNumber;
        }

        /* Access modifiers changed, original: 0000 */
        public AudioGroup getAudioGroup() {
            return this.mSipAudioCall == null ? null : this.mSipAudioCall.getAudioGroup();
        }

        public SipCall getCall() {
            return this.mOwner;
        }

        public String getCnapName() {
            String displayName = this.mPeer.getDisplayName();
            return TextUtils.isEmpty(displayName) ? null : displayName;
        }

        /* Access modifiers changed, original: 0000 */
        public boolean getMute() {
            return this.mSipAudioCall == null ? false : this.mSipAudioCall.isMuted();
        }

        public int getNumberPresentation() {
            return 1;
        }

        /* Access modifiers changed, original: protected */
        public Phone getPhone() {
            return this.mOwner.getPhone();
        }

        public State getState() {
            return this.mState;
        }

        public void hangup() throws CallStateException {
            int i = 3;
            synchronized (SipPhone.class) {
                try {
                    log("hangup: conn=" + this.mPeer.getUriString() + ": " + this.mState + ": on phone " + getPhone().getPhoneName());
                    Object isAlive = this.mState.isAlive();
                    if (isAlive == null) {
                        return;
                    }
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
                } finally {
                    Class cls = SipPhone.class;
                }
            }
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
        public void initIncomingCall(SipAudioCall sipAudioCall, State state) {
            setState(state);
            this.mSipAudioCall = sipAudioCall;
            sipAudioCall.setListener(this.mAdapter);
            this.mIncoming = true;
        }

        public boolean isIncoming() {
            return this.mIncoming;
        }

        public void separate() throws CallStateException {
            synchronized (SipPhone.class) {
                try {
                    SipCall sipCall = getPhone() == SipPhone.this ? (SipCall) SipPhone.this.getBackgroundCall() : (SipCall) SipPhone.this.getForegroundCall();
                    if (sipCall.getState() != State.IDLE) {
                        throw new CallStateException("cannot put conn back to a call in non-idle state: " + sipCall.getState());
                    }
                    log("separate: conn=" + this.mPeer.getUriString() + " from " + this.mOwner + " back to " + sipCall);
                    Phone phone = getPhone();
                    AudioGroup access$1500 = sipCall.getAudioGroup();
                    sipCall.add(this);
                    this.mSipAudioCall.setAudioGroup(access$1500);
                    phone.switchHoldingAndActive();
                    sipCall = (SipCall) SipPhone.this.getForegroundCall();
                    this.mSipAudioCall.startAudio();
                    sipCall.onConnectionStateChanged(this);
                } finally {
                    Class cls = SipPhone.class;
                }
            }
        }

        /* Access modifiers changed, original: 0000 */
        public void setMute(boolean z) {
            if (this.mSipAudioCall != null && z != this.mSipAudioCall.isMuted()) {
                log("setState: prev muted=" + (!z) + " new muted=" + z);
                this.mSipAudioCall.toggleMute();
            }
        }

        /* Access modifiers changed, original: protected */
        public void setState(State state) {
            if (state != this.mState) {
                super.setState(state);
                this.mState = state;
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
    }

    SipPhone(Context context, PhoneNotifier phoneNotifier, SipProfile sipProfile) {
        super("SIP:" + sipProfile.getUriString(), context, phoneNotifier);
        log("new SipPhone: " + sipProfile.getUriString());
        this.mRingingCall = new SipCall();
        this.mForegroundCall = new SipCall();
        this.mBackgroundCall = new SipCall();
        this.mProfile = sipProfile;
        this.mSipManager = SipManager.newInstance(context);
    }

    private Connection dialInternal(String str, int i) throws CallStateException {
        log("dialInternal: dialString=" + "xxxxxx");
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
                return this.mForegroundCall.dial(str);
            } catch (SipException e) {
                loge("dialInternal: ", e);
                throw new CallStateException("dial error: " + e);
            }
        }
        throw new CallStateException("dialInternal: cannot dial in current state");
    }

    private static State getCallStateFrom(SipAudioCall sipAudioCall) {
        if (sipAudioCall.isOnHold()) {
            return State.HOLDING;
        }
        int state = sipAudioCall.getState();
        switch (state) {
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
                slog("illegal connection state: " + state);
                return State.DISCONNECTED;
        }
    }

    private String getSipDomain(SipProfile sipProfile) {
        String sipDomain = sipProfile.getSipDomain();
        return sipDomain.endsWith(":5060") ? sipDomain.substring(0, sipDomain.length() - 5) : sipDomain;
    }

    private String getUriString(SipProfile sipProfile) {
        return sipProfile.getUserName() + "@" + getSipDomain(sipProfile);
    }

    private void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    private void loge(String str) {
        Rlog.e(LOG_TAG, str);
    }

    private void loge(String str, Exception exception) {
        Rlog.e(LOG_TAG, str, exception);
    }

    private static void slog(String str) {
        Rlog.d(LOG_TAG, str);
    }

    public void acceptCall(int i) throws CallStateException {
        synchronized (SipPhone.class) {
            try {
                if (this.mRingingCall.getState() == State.INCOMING || this.mRingingCall.getState() == State.WAITING) {
                    log("acceptCall: accepting");
                    this.mRingingCall.setMute(false);
                    this.mRingingCall.acceptCall();
                } else {
                    log("acceptCall: throw CallStateException(\"phone not ringing\")");
                    throw new CallStateException("phone not ringing");
                }
            } catch (Throwable th) {
                Class cls = SipPhone.class;
            }
        }
    }

    public /* bridge */ /* synthetic */ void activateCellBroadcastSms(int i, Message message) {
        super.activateCellBroadcastSms(i, message);
    }

    public boolean canConference() {
        log("canConference: ret=true");
        return true;
    }

    public /* bridge */ /* synthetic */ boolean canDial() {
        return super.canDial();
    }

    public boolean canTransfer() {
        return false;
    }

    public void clearDisconnected() {
        synchronized (SipPhone.class) {
            try {
                this.mRingingCall.clearDisconnected();
                this.mForegroundCall.clearDisconnected();
                this.mBackgroundCall.clearDisconnected();
                updatePhoneState();
                notifyPreciseCallStateChanged();
            } catch (Throwable th) {
                Class cls = SipPhone.class;
            }
        }
    }

    public void conference() throws CallStateException {
        synchronized (SipPhone.class) {
            try {
                if (this.mForegroundCall.getState() == State.ACTIVE && this.mForegroundCall.getState() == State.ACTIVE) {
                    log("conference: merge fg & bg");
                    Object obj = this.mForegroundCall;
                    obj.merge(this.mBackgroundCall);
                    return;
                }
                throw new CallStateException("wrong state to merge calls: fg=" + this.mForegroundCall.getState() + ", bg=" + this.mBackgroundCall.getState());
            } finally {
                Class cls = SipPhone.class;
            }
        }
    }

    public void conference(Call call) throws CallStateException {
        synchronized (SipPhone.class) {
            try {
                if (call instanceof SipCall) {
                    Object obj = this.mForegroundCall;
                    obj.merge((SipCall) call);
                    return;
                }
                throw new CallStateException("expect " + SipCall.class + ", cannot merge with " + call.getClass());
            } finally {
                Class cls = SipPhone.class;
            }
        }
    }

    public Connection dial(String str, int i) throws CallStateException {
        Connection dialInternal;
        synchronized (SipPhone.class) {
            try {
                dialInternal = dialInternal(str, i);
            } catch (Throwable th) {
                Class cls = SipPhone.class;
            }
        }
        return dialInternal;
    }

    public Connection dial(String str, int i, Bundle bundle) throws CallStateException {
        return null;
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

    public boolean equals(SipPhone sipPhone) {
        return getSipUri().equals(sipPhone.getSipUri());
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof SipPhone)) {
            return false;
        }
        return this.mProfile.getUriString().equals(((SipPhone) obj).mProfile.getUriString());
    }

    public void explicitCallTransfer() {
    }

    public /* bridge */ /* synthetic */ void getAvailableNetworks(Message message) {
        super.getAvailableNetworks(message);
    }

    public Call getBackgroundCall() {
        return this.mBackgroundCall;
    }

    public void getCallBarring(String str, Message message) {
        loge("call barring not supported");
    }

    public void getCallBarring(String str, String str2, int i, Message message) {
        loge("call barring not supported");
    }

    public void getCallBarring(String str, String str2, Message message) {
        loge("call barring not supported");
    }

    public /* bridge */ /* synthetic */ boolean getCallForwardingIndicator() {
        return super.getCallForwardingIndicator();
    }

    public void getCallForwardingOption(int i, int i2, Message message) {
        loge("call forwarding not supported");
    }

    public /* bridge */ /* synthetic */ void getCallForwardingOption(int i, Message message) {
        super.getCallForwardingOption(i, message);
    }

    public void getCallWaiting(int i, Message message) {
        this.mCi.queryCallWaiting(i, message);
    }

    public void getCallWaiting(Message message) {
        AsyncResult.forMessage(message, null, null);
        message.sendToTarget();
    }

    public /* bridge */ /* synthetic */ void getCellBroadcastSmsConfig(Message message) {
        super.getCellBroadcastSmsConfig(message);
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

    public /* bridge */ /* synthetic */ void getDataCallList(Message message) {
        super.getDataCallList(message);
    }

    public /* bridge */ /* synthetic */ DataState getDataConnectionState() {
        return super.getDataConnectionState();
    }

    public /* bridge */ /* synthetic */ DataState getDataConnectionState(String str) {
        return super.getDataConnectionState(str);
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

    public Call getForegroundCall() {
        return this.mForegroundCall;
    }

    public /* bridge */ /* synthetic */ String getGroupIdLevel1() {
        return super.getGroupIdLevel1();
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

    public /* bridge */ /* synthetic */ LinkProperties getLinkProperties(String str) {
        return super.getLinkProperties(str);
    }

    public /* bridge */ /* synthetic */ String getMeid() {
        return super.getMeid();
    }

    public /* bridge */ /* synthetic */ boolean getMessageWaitingIndicator() {
        return super.getMessageWaitingIndicator();
    }

    public boolean getMute() {
        return this.mForegroundCall.getState().isAlive() ? this.mForegroundCall.getMute() : this.mBackgroundCall.getMute();
    }

    public /* bridge */ /* synthetic */ void getNeighboringCids(Message message) {
        super.getNeighboringCids(message);
    }

    public void getOutgoingCallerIdDisplay(Message message) {
        AsyncResult.forMessage(message, null, null);
        message.sendToTarget();
    }

    public /* bridge */ /* synthetic */ List getPendingMmiCodes() {
        return super.getPendingMmiCodes();
    }

    public /* bridge */ /* synthetic */ PhoneSubInfo getPhoneSubInfo() {
        return super.getPhoneSubInfo();
    }

    public /* bridge */ /* synthetic */ int getPhoneType() {
        return super.getPhoneType();
    }

    public Call getRingingCall() {
        return this.mRingingCall;
    }

    public ServiceState getServiceState() {
        return super.getServiceState();
    }

    public /* bridge */ /* synthetic */ SignalStrength getSignalStrength() {
        return super.getSignalStrength();
    }

    public String getSipUri() {
        return this.mProfile.getUriString();
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

    public /* bridge */ /* synthetic */ boolean handleInCallMmiCommands(String str) {
        return super.handleInCallMmiCommands(str);
    }

    public /* bridge */ /* synthetic */ boolean handlePinMmi(String str) {
        return super.handlePinMmi(str);
    }

    public /* bridge */ /* synthetic */ boolean isDataConnectivityPossible() {
        return super.isDataConnectivityPossible();
    }

    public /* bridge */ /* synthetic */ boolean needsOtaServiceProvisioning() {
        return super.needsOtaServiceProvisioning();
    }

    public /* bridge */ /* synthetic */ void notifyCallForwardingIndicator() {
        super.notifyCallForwardingIndicator();
    }

    public /* bridge */ /* synthetic */ void registerForRingbackTone(Handler handler, int i, Object obj) {
        super.registerForRingbackTone(handler, i, obj);
    }

    public /* bridge */ /* synthetic */ void registerForSuppServiceNotification(Handler handler, int i, Object obj) {
        super.registerForSuppServiceNotification(handler, i, obj);
    }

    public void rejectCall() throws CallStateException {
        synchronized (SipPhone.class) {
            try {
                if (this.mRingingCall.getState().isRinging()) {
                    log("rejectCall: rejecting");
                    Object obj = this.mRingingCall;
                    obj.rejectCall();
                    return;
                }
                log("rejectCall: throw CallStateException(\"phone not ringing\")");
                throw new CallStateException("phone not ringing");
            } finally {
                Class cls = SipPhone.class;
            }
        }
    }

    public /* bridge */ /* synthetic */ void saveClirSetting(int i) {
        super.saveClirSetting(i);
    }

    public /* bridge */ /* synthetic */ void selectNetworkManually(OperatorInfo operatorInfo, Message message) {
        super.selectNetworkManually(operatorInfo, message);
    }

    public void sendBurstDtmf(String str) {
        loge("sendBurstDtmf() is a CDMA method");
    }

    public void sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            loge("sendDtmf called with invalid character '" + c + "'");
        } else if (this.mForegroundCall.getState().isAlive()) {
            synchronized (SipPhone.class) {
                try {
                    this.mForegroundCall.sendDtmf(c);
                } catch (Throwable th) {
                    Class cls = SipPhone.class;
                }
            }
        }
    }

    public /* bridge */ /* synthetic */ void sendUssdResponse(String str) {
        super.sendUssdResponse(str);
    }

    public void setCallBarring(String str, boolean z, String str2, int i, Message message) {
        loge("call barring not supported");
    }

    public void setCallBarring(String str, boolean z, String str2, Message message) {
        loge("call barring not supported");
    }

    public void setCallForwardingOption(int i, int i2, int i3, String str, int i4, Message message) {
        loge("call forwarding not supported");
    }

    public /* bridge */ /* synthetic */ void setCallForwardingOption(int i, int i2, String str, int i3, Message message) {
        super.setCallForwardingOption(i, i2, str, i3, message);
    }

    public void setCallWaiting(boolean z, int i, Message message) {
        this.mCi.setCallWaiting(z, i, message);
    }

    public void setCallWaiting(boolean z, Message message) {
        loge("call waiting not supported");
    }

    public /* bridge */ /* synthetic */ void setCellBroadcastSmsConfig(int[] iArr, Message message) {
        super.setCellBroadcastSmsConfig(iArr, message);
    }

    public /* bridge */ /* synthetic */ void setDataEnabled(boolean z) {
        super.setDataEnabled(z);
    }

    public /* bridge */ /* synthetic */ void setDataRoamingEnabled(boolean z) {
        super.setDataRoamingEnabled(z);
    }

    public void setEchoSuppressionEnabled() {
        synchronized (SipPhone.class) {
            try {
                if (((AudioManager) this.mContext.getSystemService("audio")).getParameters("ec_supported").contains("off")) {
                    this.mForegroundCall.setAudioGroupMode();
                }
            } catch (Throwable th) {
                Class cls = SipPhone.class;
            }
        }
    }

    public /* bridge */ /* synthetic */ void setLine1Number(String str, String str2, Message message) {
        super.setLine1Number(str, str2, message);
    }

    public void setMute(boolean z) {
        synchronized (SipPhone.class) {
            try {
                this.mForegroundCall.setMute(z);
            } catch (Throwable th) {
                Class cls = SipPhone.class;
            }
        }
    }

    public /* bridge */ /* synthetic */ void setNetworkSelectionModeAutomatic(Message message) {
        super.setNetworkSelectionModeAutomatic(message);
    }

    public /* bridge */ /* synthetic */ void setOnPostDialCharacter(Handler handler, int i, Object obj) {
        super.setOnPostDialCharacter(handler, i, obj);
    }

    public void setOutgoingCallerIdDisplay(int i, Message message) {
        AsyncResult.forMessage(message, null, null);
        message.sendToTarget();
    }

    public /* bridge */ /* synthetic */ void setRadioPower(boolean z) {
        super.setRadioPower(z);
    }

    public /* bridge */ /* synthetic */ void setVoiceMailNumber(String str, String str2, Message message) {
        super.setVoiceMailNumber(str, str2, message);
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

    public void switchHoldingAndActive() throws CallStateException {
        log("dialInternal: switch fg and bg");
        synchronized (SipPhone.class) {
            try {
                this.mForegroundCall.switchWith(this.mBackgroundCall);
                if (this.mBackgroundCall.getState().isAlive()) {
                    this.mBackgroundCall.hold();
                }
                if (this.mForegroundCall.getState().isAlive()) {
                    this.mForegroundCall.unhold();
                }
            } catch (Throwable th) {
                Class cls = SipPhone.class;
            }
        }
    }

    public Connection takeIncomingCall(Object obj) {
        Connection connection = null;
        synchronized (SipPhone.class) {
            if (!(obj instanceof SipAudioCall)) {
                log("takeIncomingCall: ret=null, not a SipAudioCall");
            } else if (this.mRingingCall.getState().isAlive()) {
                log("takeIncomingCall: ret=null, ringingCall not alive");
            } else if (this.mForegroundCall.getState().isAlive() && this.mBackgroundCall.getState().isAlive()) {
                log("takeIncomingCall: ret=null, foreground and background both alive");
            } else {
                try {
                    SipAudioCall sipAudioCall = (SipAudioCall) obj;
                    log("takeIncomingCall: taking call from: " + sipAudioCall.getPeerProfile().getUriString());
                    if (sipAudioCall.getLocalProfile().getUriString().equals(this.mProfile.getUriString())) {
                        Connection initIncomingCall = this.mRingingCall.initIncomingCall(sipAudioCall, this.mForegroundCall.getState().isAlive());
                        if (sipAudioCall.getState() != 3) {
                            log("    takeIncomingCall: call cancelled !!");
                            this.mRingingCall.reset();
                        } else {
                            connection = initIncomingCall;
                        }
                    }
                } catch (Exception e) {
                    log("    takeIncomingCall: exception e=" + e);
                    this.mRingingCall.reset();
                } catch (Throwable th) {
                    Class cls = SipPhone.class;
                }
                log("takeIncomingCall: NOT taking !!");
            }
        }
        return connection;
    }

    public /* bridge */ /* synthetic */ void unregisterForRingbackTone(Handler handler) {
        super.unregisterForRingbackTone(handler);
    }

    public /* bridge */ /* synthetic */ void unregisterForSuppServiceNotification(Handler handler) {
        super.unregisterForSuppServiceNotification(handler);
    }

    public /* bridge */ /* synthetic */ void updateServiceLocation() {
        super.updateServiceLocation();
    }
}
