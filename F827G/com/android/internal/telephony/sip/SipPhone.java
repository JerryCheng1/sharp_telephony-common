package com.android.internal.telephony.sip;

import android.content.Context;
import android.media.AudioManager;
import android.net.LinkProperties;
import android.net.rtp.AudioGroup;
import android.net.sip.SipAudioCall;
import android.net.sip.SipErrorCode;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
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
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.SubscriptionInfoUpdater;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.uicc.IccFileHandler;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class SipPhone extends SipPhoneBase {
    private static final boolean DBG = true;
    private static final String LOG_TAG = "SipPhone";
    private static final int TIMEOUT_ANSWER_CALL = 8;
    private static final int TIMEOUT_HOLD_CALL = 15;
    private static final int TIMEOUT_MAKE_CALL = 15;
    private static final boolean VDBG = false;
    private SipCall mBackgroundCall;
    private SipCall mForegroundCall;
    private SipProfile mProfile;
    private SipCall mRingingCall;
    private SipManager mSipManager;

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void activateCellBroadcastSms(int x0, Message x1) {
        super.activateCellBroadcastSms(x0, x1);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase
    public /* bridge */ /* synthetic */ boolean canDial() {
        return super.canDial();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ Connection dial(String x0, UUSInfo x1, int x2) throws CallStateException {
        return super.dial(x0, x1, x2);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase
    public /* bridge */ /* synthetic */ boolean disableDataConnectivity() {
        return super.disableDataConnectivity();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void disableLocationUpdates() {
        super.disableLocationUpdates();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase
    public /* bridge */ /* synthetic */ boolean enableDataConnectivity() {
        return super.enableDataConnectivity();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void enableLocationUpdates() {
        super.enableLocationUpdates();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void getAvailableNetworks(Message x0) {
        super.getAvailableNetworks(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean getCallForwardingIndicator() {
        return super.getCallForwardingIndicator();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void getCallForwardingOption(int x0, Message x1) {
        super.getCallForwardingOption(x0, x1);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void getCellBroadcastSmsConfig(Message x0) {
        super.getCellBroadcastSmsConfig(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ CellLocation getCellLocation() {
        return super.getCellLocation();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase
    public /* bridge */ /* synthetic */ List getCurrentDataConnectionList() {
        return super.getCurrentDataConnectionList();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ Phone.DataActivityState getDataActivityState() {
        return super.getDataActivityState();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void getDataCallList(Message x0) {
        super.getDataCallList(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ PhoneConstants.DataState getDataConnectionState() {
        return super.getDataConnectionState();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ PhoneConstants.DataState getDataConnectionState(String x0) {
        return super.getDataConnectionState(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean getDataEnabled() {
        return super.getDataEnabled();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean getDataRoamingEnabled() {
        return super.getDataRoamingEnabled();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getDeviceId() {
        return super.getDeviceId();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getDeviceSvn() {
        return super.getDeviceSvn();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getEsn() {
        return super.getEsn();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getGroupIdLevel1() {
        return super.getGroupIdLevel1();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ IccCard getIccCard() {
        return super.getIccCard();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase
    public /* bridge */ /* synthetic */ IccFileHandler getIccFileHandler() {
        return super.getIccFileHandler();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return super.getIccPhoneBookInterfaceManager();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean getIccRecordsLoaded() {
        return super.getIccRecordsLoaded();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getIccSerialNumber() {
        return super.getIccSerialNumber();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getImei() {
        return super.getImei();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getLine1AlphaTag() {
        return super.getLine1AlphaTag();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getLine1Number() {
        return super.getLine1Number();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ LinkProperties getLinkProperties(String x0) {
        return super.getLinkProperties(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getMeid() {
        return super.getMeid();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean getMessageWaitingIndicator() {
        return super.getMessageWaitingIndicator();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void getNeighboringCids(Message x0) {
        super.getNeighboringCids(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ List getPendingMmiCodes() {
        return super.getPendingMmiCodes();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ PhoneSubInfo getPhoneSubInfo() {
        return super.getPhoneSubInfo();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ int getPhoneType() {
        return super.getPhoneType();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ SignalStrength getSignalStrength() {
        return super.getSignalStrength();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ PhoneConstants.State getState() {
        return super.getState();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getSubscriberId() {
        return super.getSubscriberId();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getVoiceMailAlphaTag() {
        return super.getVoiceMailAlphaTag();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ String getVoiceMailNumber() {
        return super.getVoiceMailNumber();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean handleInCallMmiCommands(String x0) {
        return super.handleInCallMmiCommands(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean handlePinMmi(String x0) {
        return super.handlePinMmi(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean isDataConnectivityPossible() {
        return super.isDataConnectivityPossible();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ boolean needsOtaServiceProvisioning() {
        return super.needsOtaServiceProvisioning();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase
    public /* bridge */ /* synthetic */ void notifyCallForwardingIndicator() {
        super.notifyCallForwardingIndicator();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void registerForRingbackTone(Handler x0, int x1, Object x2) {
        super.registerForRingbackTone(x0, x1, x2);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void registerForSuppServiceNotification(Handler x0, int x1, Object x2) {
        super.registerForSuppServiceNotification(x0, x1, x2);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase
    public /* bridge */ /* synthetic */ void saveClirSetting(int x0) {
        super.saveClirSetting(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void selectNetworkManually(OperatorInfo x0, Message x1) {
        super.selectNetworkManually(x0, x1);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void sendUssdResponse(String x0) {
        super.sendUssdResponse(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setCallForwardingOption(int x0, int x1, String x2, int x3, Message x4) {
        super.setCallForwardingOption(x0, x1, x2, x3, x4);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setCellBroadcastSmsConfig(int[] x0, Message x1) {
        super.setCellBroadcastSmsConfig(x0, x1);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setDataEnabled(boolean x0) {
        super.setDataEnabled(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setDataRoamingEnabled(boolean x0) {
        super.setDataRoamingEnabled(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setLine1Number(String x0, String x1, Message x2) {
        super.setLine1Number(x0, x1, x2);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setNetworkSelectionModeAutomatic(Message x0) {
        super.setNetworkSelectionModeAutomatic(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setOnPostDialCharacter(Handler x0, int x1, Object x2) {
        super.setOnPostDialCharacter(x0, x1, x2);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setRadioPower(boolean x0) {
        super.setRadioPower(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void setVoiceMailNumber(String x0, String x1, Message x2) {
        super.setVoiceMailNumber(x0, x1, x2);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void unregisterForRingbackTone(Handler x0) {
        super.unregisterForRingbackTone(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void unregisterForSuppServiceNotification(Handler x0) {
        super.unregisterForSuppServiceNotification(x0);
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public /* bridge */ /* synthetic */ void updateServiceLocation() {
        super.updateServiceLocation();
    }

    public SipPhone(Context context, PhoneNotifier notifier, SipProfile profile) {
        super("SIP:" + profile.getUriString(), context, notifier);
        this.mRingingCall = new SipCall();
        this.mForegroundCall = new SipCall();
        this.mBackgroundCall = new SipCall();
        log("new SipPhone: " + profile.getUriString());
        this.mRingingCall = new SipCall();
        this.mForegroundCall = new SipCall();
        this.mBackgroundCall = new SipCall();
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

    public Connection takeIncomingCall(Object incomingCall) {
        synchronized (SipPhone.class) {
            if (!(incomingCall instanceof SipAudioCall)) {
                log("takeIncomingCall: ret=null, not a SipAudioCall");
                return null;
            } else if (this.mRingingCall.getState().isAlive()) {
                log("takeIncomingCall: ret=null, ringingCall not alive");
                return null;
            } else if (!this.mForegroundCall.getState().isAlive() || !this.mBackgroundCall.getState().isAlive()) {
                try {
                    SipAudioCall sipAudioCall = (SipAudioCall) incomingCall;
                    log("takeIncomingCall: taking call from: " + sipAudioCall.getPeerProfile().getUriString());
                    if (sipAudioCall.getLocalProfile().getUriString().equals(this.mProfile.getUriString())) {
                        SipConnection connection = this.mRingingCall.initIncomingCall(sipAudioCall, this.mForegroundCall.getState().isAlive());
                        if (sipAudioCall.getState() != 3) {
                            log("    takeIncomingCall: call cancelled !!");
                            this.mRingingCall.reset();
                            connection = null;
                        }
                        return connection;
                    }
                } catch (Exception e) {
                    log("    takeIncomingCall: exception e=" + e);
                    this.mRingingCall.reset();
                }
                log("takeIncomingCall: NOT taking !!");
                return null;
            } else {
                log("takeIncomingCall: ret=null, foreground and background both alive");
                return null;
            }
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void acceptCall(int videoState) throws CallStateException {
        synchronized (SipPhone.class) {
            if (this.mRingingCall.getState() == Call.State.INCOMING || this.mRingingCall.getState() == Call.State.WAITING) {
                log("acceptCall: accepting");
                this.mRingingCall.setMute(false);
                this.mRingingCall.acceptCall();
            } else {
                log("acceptCall: throw CallStateException(\"phone not ringing\")");
                throw new CallStateException("phone not ringing");
            }
        }
    }

    @Override // com.android.internal.telephony.Phone
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

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState) throws CallStateException {
        Connection dialInternal;
        synchronized (SipPhone.class) {
            dialInternal = dialInternal(dialString, videoState);
        }
        return dialInternal;
    }

    private Connection dialInternal(String dialString, int videoState) throws CallStateException {
        log("dialInternal: dialString=xxxxxx");
        clearDisconnected();
        if (!canDial()) {
            throw new CallStateException("dialInternal: cannot dial in current state");
        }
        if (this.mForegroundCall.getState() == Call.State.ACTIVE) {
            switchHoldingAndActive();
        }
        if (this.mForegroundCall.getState() != Call.State.IDLE) {
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

    @Override // com.android.internal.telephony.Phone
    public void switchHoldingAndActive() throws CallStateException {
        log("dialInternal: switch fg and bg");
        synchronized (SipPhone.class) {
            this.mForegroundCall.switchWith(this.mBackgroundCall);
            if (this.mBackgroundCall.getState().isAlive()) {
                this.mBackgroundCall.hold();
            }
            if (this.mForegroundCall.getState().isAlive()) {
                this.mForegroundCall.unhold();
            }
        }
    }

    @Override // com.android.internal.telephony.Phone
    public boolean canConference() {
        log("canConference: ret=true");
        return true;
    }

    @Override // com.android.internal.telephony.Phone
    public void conference() throws CallStateException {
        synchronized (SipPhone.class) {
            if (this.mForegroundCall.getState() == Call.State.ACTIVE && this.mForegroundCall.getState() == Call.State.ACTIVE) {
                log("conference: merge fg & bg");
                this.mForegroundCall.merge(this.mBackgroundCall);
            } else {
                throw new CallStateException("wrong state to merge calls: fg=" + this.mForegroundCall.getState() + ", bg=" + this.mBackgroundCall.getState());
            }
        }
    }

    public void conference(Call that) throws CallStateException {
        synchronized (SipPhone.class) {
            if (!(that instanceof SipCall)) {
                throw new CallStateException("expect " + SipCall.class + ", cannot merge with " + that.getClass());
            }
            this.mForegroundCall.merge((SipCall) that);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public boolean canTransfer() {
        return false;
    }

    @Override // com.android.internal.telephony.Phone
    public void explicitCallTransfer() {
    }

    @Override // com.android.internal.telephony.Phone
    public void clearDisconnected() {
        synchronized (SipPhone.class) {
            this.mRingingCall.clearDisconnected();
            this.mForegroundCall.clearDisconnected();
            this.mBackgroundCall.clearDisconnected();
            updatePhoneState();
            notifyPreciseCallStateChanged();
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            loge("sendDtmf called with invalid character '" + c + "'");
        } else if (this.mForegroundCall.getState().isAlive()) {
            synchronized (SipPhone.class) {
                this.mForegroundCall.sendDtmf(c);
            }
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void startDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            loge("startDtmf called with invalid character '" + c + "'");
        } else {
            sendDtmf(c);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void stopDtmf() {
    }

    public void sendBurstDtmf(String dtmfString) {
        loge("sendBurstDtmf() is a CDMA method");
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallForwardingOption(int commandInterfaceCFReason, int serviceClass, Message onComplete) {
        loge("call forwarding not supported");
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, int serviceClass, String dialingNumber, int timerSeconds, Message onComplete) {
        loge("call forwarding not supported");
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        AsyncResult.forMessage(onComplete, (Object) null, (Throwable) null);
        onComplete.sendToTarget();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode, Message onComplete) {
        AsyncResult.forMessage(onComplete, (Object) null, (Throwable) null);
        onComplete.sendToTarget();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallWaiting(Message onComplete) {
        AsyncResult.forMessage(onComplete, (Object) null, (Throwable) null);
        onComplete.sendToTarget();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallWaiting(int serviceClass, Message onComplete) {
        this.mCi.queryCallWaiting(serviceClass, onComplete);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallWaiting(boolean enable, int serviceClass, Message onComplete) {
        this.mCi.setCallWaiting(enable, serviceClass, onComplete);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallBarring(String facility, boolean lockState, String password, int serviceClass, Message onComplete) {
        loge("call barring not supported");
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallBarring(String facility, String password, int serviceClass, Message onComplete) {
        loge("call barring not supported");
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallBarring(String facility, boolean lockState, String password, Message onComplete) {
        loge("call barring not supported");
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallBarring(String facility, String password, Message onComplete) {
        loge("call barring not supported");
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallBarring(String facility, Message onComplete) {
        loge("call barring not supported");
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallWaiting(boolean enable, Message onComplete) {
        loge("call waiting not supported");
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setEchoSuppressionEnabled() {
        synchronized (SipPhone.class) {
            if (((AudioManager) this.mContext.getSystemService("audio")).getParameters("ec_supported").contains("off")) {
                this.mForegroundCall.setAudioGroupMode();
            }
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void setMute(boolean muted) {
        synchronized (SipPhone.class) {
            this.mForegroundCall.setMute(muted);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getMute() {
        return this.mForegroundCall.getState().isAlive() ? this.mForegroundCall.getMute() : this.mBackgroundCall.getMute();
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public Call getForegroundCall() {
        return this.mForegroundCall;
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public Call getBackgroundCall() {
        return this.mBackgroundCall;
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public Call getRingingCall() {
        return this.mRingingCall;
    }

    @Override // com.android.internal.telephony.sip.SipPhoneBase, com.android.internal.telephony.Phone
    public ServiceState getServiceState() {
        return super.getServiceState();
    }

    public String getUriString(SipProfile p) {
        return p.getUserName() + "@" + getSipDomain(p);
    }

    private String getSipDomain(SipProfile p) {
        String domain = p.getSipDomain();
        if (domain.endsWith(":5060")) {
            return domain.substring(0, domain.length() - 5);
        }
        return domain;
    }

    public static Call.State getCallStateFrom(SipAudioCall sipAudioCall) {
        if (sipAudioCall.isOnHold()) {
            return Call.State.HOLDING;
        }
        int sessionState = sipAudioCall.getState();
        switch (sessionState) {
            case 0:
                return Call.State.IDLE;
            case 1:
            case 2:
            default:
                slog("illegal connection state: " + sessionState);
                return Call.State.DISCONNECTED;
            case 3:
            case 4:
                return Call.State.INCOMING;
            case 5:
                return Call.State.DIALING;
            case 6:
                return Call.State.ALERTING;
            case 7:
                return Call.State.DISCONNECTING;
            case 8:
                return Call.State.ACTIVE;
        }
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

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class SipCall extends SipCallBase {
        private static final boolean SC_DBG = true;
        private static final String SC_TAG = "SipCall";
        private static final boolean SC_VDBG = false;

        private SipCall() {
            SipPhone.this = r1;
        }

        void reset() {
            log("reset");
            this.mConnections.clear();
            setState(Call.State.IDLE);
        }

        void switchWith(SipCall that) {
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
            Iterator i$ = this.mConnections.iterator();
            while (i$.hasNext()) {
                ((SipConnection) ((Connection) i$.next())).changeOwner(this);
            }
        }

        @Override // com.android.internal.telephony.Call
        public Phone getPhone() {
            return SipPhone.this;
        }

        @Override // com.android.internal.telephony.sip.SipCallBase, com.android.internal.telephony.Call
        public List<Connection> getConnections() {
            ArrayList arrayList;
            synchronized (SipPhone.class) {
                arrayList = this.mConnections;
            }
            return arrayList;
        }

        Connection dial(String originalNumber) throws SipException {
            log("dial: num=xxx");
            String calleeSipUri = originalNumber;
            if (!calleeSipUri.contains("@")) {
                calleeSipUri = SipPhone.this.mProfile.getUriString().replaceFirst(Pattern.quote(SipPhone.this.mProfile.getUserName() + "@"), calleeSipUri + "@");
            }
            try {
                SipConnection c = new SipConnection(this, new SipProfile.Builder(calleeSipUri).build(), originalNumber);
                c.dial();
                this.mConnections.add(c);
                setState(Call.State.DIALING);
                return c;
            } catch (ParseException e) {
                throw new SipException("dial", e);
            }
        }

        @Override // com.android.internal.telephony.Call
        public void hangup() throws CallStateException {
            synchronized (SipPhone.class) {
                if (this.mState.isAlive()) {
                    log("hangup: call " + getState() + ": " + this + " on phone " + getPhone());
                    setState(Call.State.DISCONNECTING);
                    CallStateException excp = null;
                    Iterator i$ = this.mConnections.iterator();
                    while (i$.hasNext()) {
                        try {
                            ((Connection) i$.next()).hangup();
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

        SipConnection initIncomingCall(SipAudioCall sipAudioCall, boolean makeCallWait) {
            SipConnection c = new SipConnection(SipPhone.this, this, sipAudioCall.getPeerProfile());
            this.mConnections.add(c);
            Call.State newState = makeCallWait ? Call.State.WAITING : Call.State.INCOMING;
            c.initIncomingCall(sipAudioCall, newState);
            setState(newState);
            SipPhone.this.notifyNewRingingConnectionP(c);
            return c;
        }

        void rejectCall() throws CallStateException {
            log("rejectCall:");
            hangup();
        }

        void acceptCall() throws CallStateException {
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
            return Boolean.valueOf(((AudioManager) SipPhone.this.mContext.getSystemService("audio")).isSpeakerphoneOn()).booleanValue();
        }

        void setAudioGroupMode() {
            AudioGroup audioGroup = getAudioGroup();
            if (audioGroup == null) {
                log("setAudioGroupMode: audioGroup == null ignore");
                return;
            }
            int mode = audioGroup.getMode();
            if (this.mState == Call.State.HOLDING) {
                audioGroup.setMode(0);
            } else if (getMute()) {
                audioGroup.setMode(1);
            } else if (isSpeakerOn()) {
                audioGroup.setMode(3);
            } else {
                audioGroup.setMode(2);
            }
            log(String.format("setAudioGroupMode change: %d --> %d", Integer.valueOf(mode), Integer.valueOf(audioGroup.getMode())));
        }

        void hold() throws CallStateException {
            log("hold:");
            setState(Call.State.HOLDING);
            Iterator i$ = this.mConnections.iterator();
            while (i$.hasNext()) {
                ((SipConnection) ((Connection) i$.next())).hold();
            }
            setAudioGroupMode();
        }

        void unhold() throws CallStateException {
            log("unhold:");
            setState(Call.State.ACTIVE);
            AudioGroup audioGroup = new AudioGroup();
            Iterator i$ = this.mConnections.iterator();
            while (i$.hasNext()) {
                ((SipConnection) ((Connection) i$.next())).unhold(audioGroup);
            }
            setAudioGroupMode();
        }

        void setMute(boolean muted) {
            log("setMute: muted=" + muted);
            Iterator i$ = this.mConnections.iterator();
            while (i$.hasNext()) {
                ((SipConnection) ((Connection) i$.next())).setMute(muted);
            }
        }

        boolean getMute() {
            boolean ret = false;
            if (!this.mConnections.isEmpty()) {
                ret = ((SipConnection) this.mConnections.get(0)).getMute();
            }
            log("getMute: ret=" + ret);
            return ret;
        }

        void merge(SipCall that) throws CallStateException {
            log("merge:");
            AudioGroup audioGroup = getAudioGroup();
            for (Connection c : (Connection[]) that.mConnections.toArray(new Connection[that.mConnections.size()])) {
                SipConnection conn = (SipConnection) c;
                add(conn);
                if (conn.getState() == Call.State.HOLDING) {
                    conn.unhold(audioGroup);
                }
            }
            that.setState(Call.State.IDLE);
        }

        public void add(SipConnection conn) {
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

        void sendDtmf(char c) {
            AudioGroup audioGroup = getAudioGroup();
            if (audioGroup != null) {
                audioGroup.sendDtmf(convertDtmf(c));
            }
        }

        private int convertDtmf(char c) {
            int code = c - '0';
            if (code >= 0 && code <= 9) {
                return code;
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
                    throw new IllegalArgumentException("invalid DTMF char: " + ((int) c));
            }
        }

        @Override // com.android.internal.telephony.sip.SipCallBase
        protected void setState(Call.State newState) {
            if (this.mState != newState) {
                log("setState: cur state" + this.mState + " --> " + newState + ": " + this + ": on phone " + getPhone() + " " + this.mConnections.size());
                if (newState == Call.State.ALERTING) {
                    this.mState = newState;
                    SipPhone.this.startRingbackTone();
                } else if (this.mState == Call.State.ALERTING) {
                    SipPhone.this.stopRingbackTone();
                }
                this.mState = newState;
                SipPhone.this.updatePhoneState();
                SipPhone.this.notifyPreciseCallStateChanged();
            }
        }

        void onConnectionStateChanged(SipConnection conn) {
            log("onConnectionStateChanged: conn=" + conn);
            if (this.mState != Call.State.ACTIVE) {
                setState(conn.getState());
            }
        }

        void onConnectionEnded(SipConnection conn) {
            log("onConnectionEnded: conn=" + conn);
            if (this.mState != Call.State.DISCONNECTED) {
                boolean allConnectionsDisconnected = true;
                log("---check connections: " + this.mConnections.size());
                Iterator i$ = this.mConnections.iterator();
                while (true) {
                    if (!i$.hasNext()) {
                        break;
                    }
                    Connection c = (Connection) i$.next();
                    log("   state=" + c.getState() + ": " + c);
                    if (c.getState() != Call.State.DISCONNECTED) {
                        allConnectionsDisconnected = false;
                        break;
                    }
                }
                if (allConnectionsDisconnected) {
                    setState(Call.State.DISCONNECTED);
                }
            }
            SipPhone.this.notifyDisconnectP(conn);
        }

        public AudioGroup getAudioGroup() {
            if (this.mConnections.isEmpty()) {
                return null;
            }
            return ((SipConnection) this.mConnections.get(0)).getAudioGroup();
        }

        private void log(String s) {
            Rlog.d(SC_TAG, s);
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class SipConnection extends SipConnectionBase {
        private static final boolean SCN_DBG = true;
        private static final String SCN_TAG = "SipConnection";
        private SipAudioCallAdapter mAdapter;
        private boolean mIncoming;
        private String mOriginalNumber;
        private SipCall mOwner;
        private SipProfile mPeer;
        private SipAudioCall mSipAudioCall;
        private Call.State mState;

        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
        public SipConnection(SipCall owner, SipProfile callee, String originalNumber) {
            super(originalNumber);
            SipPhone.this = r2;
            this.mState = Call.State.IDLE;
            this.mIncoming = false;
            this.mAdapter = new SipAudioCallAdapter() { // from class: com.android.internal.telephony.sip.SipPhone.SipConnection.1
                {
                    SipConnection.this = this;
                    SipPhone sipPhone = SipPhone.this;
                }

                @Override // com.android.internal.telephony.sip.SipPhone.SipAudioCallAdapter
                protected void onCallEnded(int cause) {
                    if (SipConnection.this.getDisconnectCause() != 3) {
                        SipConnection.this.setDisconnectCause(cause);
                    }
                    synchronized (SipPhone.class) {
                        SipConnection.this.setState(Call.State.DISCONNECTED);
                        SipAudioCall sipAudioCall = SipConnection.this.mSipAudioCall;
                        SipConnection.this.mSipAudioCall = null;
                        SipConnection.this.log("[SipAudioCallAdapter] onCallEnded: " + SipConnection.this.mPeer.getUriString() + ": " + (sipAudioCall == null ? "" : sipAudioCall.getState() + ", ") + "cause: " + SipConnection.this.getDisconnectCause() + ", on phone " + SipConnection.this.getPhone());
                        if (sipAudioCall != null) {
                            sipAudioCall.setListener(null);
                            sipAudioCall.close();
                        }
                        SipConnection.this.mOwner.onConnectionEnded(SipConnection.this);
                    }
                }

                @Override // android.net.sip.SipAudioCall.Listener
                public void onCallEstablished(SipAudioCall call) {
                    onChanged(call);
                    if (SipConnection.this.mState == Call.State.ACTIVE) {
                        call.startAudio();
                    }
                }

                @Override // android.net.sip.SipAudioCall.Listener
                public void onCallHeld(SipAudioCall call) {
                    onChanged(call);
                    if (SipConnection.this.mState == Call.State.HOLDING) {
                        call.startAudio();
                    }
                }

                @Override // android.net.sip.SipAudioCall.Listener
                public void onChanged(SipAudioCall call) {
                    synchronized (SipPhone.class) {
                        Call.State newState = SipPhone.getCallStateFrom(call);
                        if (SipConnection.this.mState != newState) {
                            if (newState == Call.State.INCOMING) {
                                SipConnection.this.setState(SipConnection.this.mOwner.getState());
                            } else {
                                if (SipConnection.this.mOwner == SipPhone.this.mRingingCall) {
                                    if (SipPhone.this.mRingingCall.getState() == Call.State.WAITING) {
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
                }

                @Override // com.android.internal.telephony.sip.SipPhone.SipAudioCallAdapter
                protected void onError(int cause) {
                    SipConnection.this.log("onError: " + cause);
                    onCallEnded(cause);
                }
            };
            this.mOwner = owner;
            this.mPeer = callee;
            this.mOriginalNumber = originalNumber;
        }

        public SipConnection(SipPhone sipPhone, SipCall owner, SipProfile callee) {
            this(owner, callee, sipPhone.getUriString(callee));
        }

        @Override // com.android.internal.telephony.Connection
        public String getCnapName() {
            String displayName = this.mPeer.getDisplayName();
            if (TextUtils.isEmpty(displayName)) {
                return null;
            }
            return displayName;
        }

        @Override // com.android.internal.telephony.sip.SipConnectionBase, com.android.internal.telephony.Connection
        public int getNumberPresentation() {
            return 1;
        }

        void initIncomingCall(SipAudioCall sipAudioCall, Call.State newState) {
            setState(newState);
            this.mSipAudioCall = sipAudioCall;
            sipAudioCall.setListener(this.mAdapter);
            this.mIncoming = true;
        }

        void acceptCall() throws CallStateException {
            try {
                this.mSipAudioCall.answerCall(8);
            } catch (SipException e) {
                throw new CallStateException("acceptCall(): " + e);
            }
        }

        void changeOwner(SipCall owner) {
            this.mOwner = owner;
        }

        AudioGroup getAudioGroup() {
            if (this.mSipAudioCall == null) {
                return null;
            }
            return this.mSipAudioCall.getAudioGroup();
        }

        void dial() throws SipException {
            setState(Call.State.DIALING);
            this.mSipAudioCall = SipPhone.this.mSipManager.makeAudioCall(SipPhone.this.mProfile, this.mPeer, (SipAudioCall.Listener) null, 15);
            this.mSipAudioCall.setListener(this.mAdapter);
        }

        void hold() throws CallStateException {
            setState(Call.State.HOLDING);
            try {
                this.mSipAudioCall.holdCall(15);
            } catch (SipException e) {
                throw new CallStateException("hold(): " + e);
            }
        }

        void unhold(AudioGroup audioGroup) throws CallStateException {
            this.mSipAudioCall.setAudioGroup(audioGroup);
            setState(Call.State.ACTIVE);
            try {
                this.mSipAudioCall.continueCall(15);
            } catch (SipException e) {
                throw new CallStateException("unhold(): " + e);
            }
        }

        void setMute(boolean muted) {
            if (this.mSipAudioCall != null && muted != this.mSipAudioCall.isMuted()) {
                log("setState: prev muted=" + (!muted) + " new muted=" + muted);
                this.mSipAudioCall.toggleMute();
            }
        }

        boolean getMute() {
            if (this.mSipAudioCall == null) {
                return false;
            }
            return this.mSipAudioCall.isMuted();
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // com.android.internal.telephony.sip.SipConnectionBase
        public void setState(Call.State state) {
            if (state != this.mState) {
                super.setState(state);
                this.mState = state;
            }
        }

        @Override // com.android.internal.telephony.Connection
        public Call.State getState() {
            return this.mState;
        }

        @Override // com.android.internal.telephony.Connection
        public boolean isIncoming() {
            return this.mIncoming;
        }

        @Override // com.android.internal.telephony.Connection
        public String getAddress() {
            return this.mOriginalNumber;
        }

        @Override // com.android.internal.telephony.Connection
        public SipCall getCall() {
            return this.mOwner;
        }

        @Override // com.android.internal.telephony.sip.SipConnectionBase
        protected Phone getPhone() {
            return this.mOwner.getPhone();
        }

        @Override // com.android.internal.telephony.Connection
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
                        if (this.mState == Call.State.INCOMING || this.mState == Call.State.WAITING) {
                            i = 16;
                        }
                        sipAudioCallAdapter.onCallEnded(i);
                    } catch (SipException e) {
                        throw new CallStateException("hangup(): " + e);
                    }
                }
            }
        }

        @Override // com.android.internal.telephony.Connection
        public void separate() throws CallStateException {
            synchronized (SipPhone.class) {
                SipCall call = getPhone() == SipPhone.this ? (SipCall) SipPhone.this.getBackgroundCall() : (SipCall) SipPhone.this.getForegroundCall();
                if (call.getState() != Call.State.IDLE) {
                    throw new CallStateException("cannot put conn back to a call in non-idle state: " + call.getState());
                }
                log("separate: conn=" + this.mPeer.getUriString() + " from " + this.mOwner + " back to " + call);
                Phone originalPhone = getPhone();
                AudioGroup audioGroup = call.getAudioGroup();
                call.add(this);
                this.mSipAudioCall.setAudioGroup(audioGroup);
                originalPhone.switchHoldingAndActive();
                this.mSipAudioCall.startAudio();
                ((SipCall) SipPhone.this.getForegroundCall()).onConnectionStateChanged(this);
            }
        }

        public void log(String s) {
            Rlog.d(SCN_TAG, s);
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public abstract class SipAudioCallAdapter extends SipAudioCall.Listener {
        private static final boolean SACA_DBG = true;
        private static final String SACA_TAG = "SipAudioCallAdapter";

        protected abstract void onCallEnded(int i);

        protected abstract void onError(int i);

        private SipAudioCallAdapter() {
            SipPhone.this = r1;
        }

        @Override // android.net.sip.SipAudioCall.Listener
        public void onCallEnded(SipAudioCall call) {
            log("onCallEnded: call=" + call);
            onCallEnded(call.isInCall() ? 2 : 1);
        }

        @Override // android.net.sip.SipAudioCall.Listener
        public void onCallBusy(SipAudioCall call) {
            log("onCallBusy: call=" + call);
            onCallEnded(4);
        }

        @Override // android.net.sip.SipAudioCall.Listener
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
                case -9:
                case -4:
                default:
                    onError(36);
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
                case SubscriptionInfoUpdater.SIM_REPOSITION /* -3 */:
                    onError(13);
                    return;
                case -2:
                    onError(12);
                    return;
            }
        }

        private void log(String s) {
            Rlog.d(SACA_TAG, s);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState, Bundle extras) throws CallStateException {
        return null;
    }
}
