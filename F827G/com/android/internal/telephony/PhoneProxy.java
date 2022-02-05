package com.android.internal.telephony;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.dataconnection.DctController;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UsimServiceTable;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class PhoneProxy extends Handler implements Phone {
    private static final int EVENT_RADIO_ON = 2;
    private static final int EVENT_REQUEST_VOICE_RADIO_TECH_DONE = 3;
    private static final int EVENT_RIL_CONNECTED = 4;
    private static final int EVENT_SIM_RECORDS_LOADED = 6;
    private static final int EVENT_UPDATE_PHONE_OBJECT = 5;
    private static final int EVENT_VOICE_RADIO_TECH_CHANGED = 1;
    private static final String LOG_TAG = "PhoneProxy";
    public static final Object lockForRadioTechnologyChange = new Object();
    public Phone mActivePhone;
    private CommandsInterface mCommandsInterface;
    private IccCardProxy mIccCardProxy;
    private IccPhoneBookInterfaceManagerProxy mIccPhoneBookInterfaceManagerProxy;
    private IccSmsInterfaceManager mIccSmsInterfaceManager;
    private int mPhoneId;
    private PhoneSubInfoProxy mPhoneSubInfoProxy;
    private boolean mResetModemOnRadioTechnologyChange;
    private int mRilVersion;

    public PhoneProxy(PhoneBase phone) {
        this.mResetModemOnRadioTechnologyChange = false;
        this.mPhoneId = 0;
        this.mActivePhone = phone;
        this.mResetModemOnRadioTechnologyChange = SystemProperties.getBoolean("persist.radio.reset_on_switch", false);
        this.mIccPhoneBookInterfaceManagerProxy = new IccPhoneBookInterfaceManagerProxy(phone.getIccPhoneBookInterfaceManager());
        this.mPhoneSubInfoProxy = new PhoneSubInfoProxy(phone.getPhoneSubInfo());
        this.mCommandsInterface = ((PhoneBase) this.mActivePhone).mCi;
        this.mCommandsInterface.registerForRilConnected(this, 4, null);
        this.mCommandsInterface.registerForOn(this, 2, null);
        this.mCommandsInterface.registerForVoiceRadioTechChanged(this, 1, null);
        this.mPhoneId = phone.getPhoneId();
        this.mIccSmsInterfaceManager = new IccSmsInterfaceManager((PhoneBase) this.mActivePhone);
        this.mIccCardProxy = new IccCardProxy(this.mActivePhone.getContext(), this.mCommandsInterface, this.mActivePhone.getPhoneId());
        if (phone.getPhoneType() == 1) {
            this.mIccCardProxy.setVoiceRadioTech(3);
        } else if (phone.getPhoneType() == 2) {
            this.mIccCardProxy.setVoiceRadioTech(6);
        }
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        switch (msg.what) {
            case 1:
            case 3:
                String what = msg.what == 1 ? "EVENT_VOICE_RADIO_TECH_CHANGED" : "EVENT_REQUEST_VOICE_RADIO_TECH_DONE";
                if (ar.exception == null) {
                    if (ar.result != null && ((int[]) ar.result).length != 0) {
                        int newVoiceTech = ((int[]) ar.result)[0];
                        logd(what + ": newVoiceTech=" + newVoiceTech);
                        phoneObjectUpdater(newVoiceTech);
                        break;
                    } else {
                        loge(what + ": has no tech!");
                        break;
                    }
                } else {
                    loge(what + ": exception=" + ar.exception);
                    break;
                }
                break;
            case 2:
                this.mCommandsInterface.getVoiceRadioTechnology(obtainMessage(3));
                break;
            case 4:
                if (ar.exception == null && ar.result != null) {
                    this.mRilVersion = ((Integer) ar.result).intValue();
                    break;
                } else {
                    logd("Unexpected exception on EVENT_RIL_CONNECTED");
                    this.mRilVersion = -1;
                    break;
                }
                break;
            case 5:
                phoneObjectUpdater(msg.arg1);
                break;
            case 6:
                if (!this.mActivePhone.getContext().getResources().getBoolean(17957009)) {
                    this.mCommandsInterface.getVoiceRadioTechnology(obtainMessage(3));
                    break;
                }
                break;
            default:
                loge("Error! This handler was not registered for this message type. Message: " + msg.what);
                break;
        }
        super.handleMessage(msg);
    }

    private static void logd(String msg) {
        Rlog.d(LOG_TAG, "[PhoneProxy] " + msg);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, "[PhoneProxy] " + msg);
    }

    private void phoneObjectUpdater(int newVoiceRadioTech) {
        logd("phoneObjectUpdater: newVoiceRadioTech=" + newVoiceRadioTech);
        if (this.mActivePhone != null) {
            if (newVoiceRadioTech == 14 || newVoiceRadioTech == 0) {
                int volteReplacementRat = this.mActivePhone.getContext().getResources().getInteger(17694816);
                logd("phoneObjectUpdater: volteReplacementRat=" + volteReplacementRat);
                if (volteReplacementRat != 0) {
                    newVoiceRadioTech = volteReplacementRat;
                }
            }
            if (this.mRilVersion != 6 || getLteOnCdmaMode() != 1) {
                boolean matchCdma = ServiceState.isCdma(newVoiceRadioTech);
                boolean matchGsm = ServiceState.isGsm(newVoiceRadioTech);
                if ((matchCdma && this.mActivePhone.getPhoneType() == 2) || (matchGsm && this.mActivePhone.getPhoneType() == 1)) {
                    logd("phoneObjectUpdater: No change ignore, newVoiceRadioTech=" + newVoiceRadioTech + " mActivePhone=" + this.mActivePhone.getPhoneName());
                    return;
                } else if (!matchCdma && !matchGsm) {
                    loge("phoneObjectUpdater: newVoiceRadioTech=" + newVoiceRadioTech + " doesn't match either CDMA or GSM - error! No phone change");
                    return;
                }
            } else if (this.mActivePhone.getPhoneType() == 2) {
                logd("phoneObjectUpdater: LTE ON CDMA property is set. Use CDMA Phone newVoiceRadioTech=" + newVoiceRadioTech + " mActivePhone=" + this.mActivePhone.getPhoneName());
                return;
            } else {
                logd("phoneObjectUpdater: LTE ON CDMA property is set. Switch to CDMALTEPhone newVoiceRadioTech=" + newVoiceRadioTech + " mActivePhone=" + this.mActivePhone.getPhoneName());
                newVoiceRadioTech = 6;
            }
        }
        if (newVoiceRadioTech == 0) {
            logd("phoneObjectUpdater: Unknown rat ignore,  newVoiceRadioTech=Unknown. mActivePhone=" + this.mActivePhone.getPhoneName());
            return;
        }
        boolean oldPowerState = false;
        if (this.mResetModemOnRadioTechnologyChange && this.mCommandsInterface.getRadioState().isOn()) {
            oldPowerState = true;
            logd("phoneObjectUpdater: Setting Radio Power to Off");
            this.mCommandsInterface.setRadioPower(false, null);
        }
        deleteAndCreatePhone(newVoiceRadioTech);
        if (this.mResetModemOnRadioTechnologyChange && oldPowerState) {
            logd("phoneObjectUpdater: Resetting Radio");
            this.mCommandsInterface.setRadioPower(oldPowerState, null);
        }
        this.mIccSmsInterfaceManager.updatePhoneObject((PhoneBase) this.mActivePhone);
        this.mIccPhoneBookInterfaceManagerProxy.setmIccPhoneBookInterfaceManager(this.mActivePhone.getIccPhoneBookInterfaceManager());
        this.mPhoneSubInfoProxy.setmPhoneSubInfo(this.mActivePhone.getPhoneSubInfo());
        this.mCommandsInterface = ((PhoneBase) this.mActivePhone).mCi;
        this.mIccCardProxy.setVoiceRadioTech(newVoiceRadioTech);
        Intent intent = new Intent("android.intent.action.RADIO_TECHNOLOGY");
        intent.addFlags(536870912);
        intent.putExtra("phoneName", this.mActivePhone.getPhoneName());
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhoneId);
        ActivityManagerNative.broadcastStickyIntent(intent, (String) null, -1);
        DctController.getInstance().updatePhoneObject(this);
    }

    private void deleteAndCreatePhone(int newVoiceRadioTech) {
        String outgoingPhoneName = "Unknown";
        Phone oldPhone = this.mActivePhone;
        ImsPhone imsPhone = null;
        if (oldPhone != null) {
            outgoingPhoneName = ((PhoneBase) oldPhone).getPhoneName();
            oldPhone.unregisterForSimRecordsLoaded(this);
        }
        logd("Switching Voice Phone : " + outgoingPhoneName + " >>> " + (ServiceState.isGsm(newVoiceRadioTech) ? "GSM" : "CDMA"));
        if (ServiceState.isCdma(newVoiceRadioTech)) {
            this.mActivePhone = PhoneFactory.getCdmaPhone(this.mPhoneId);
        } else if (ServiceState.isGsm(newVoiceRadioTech)) {
            this.mActivePhone = PhoneFactory.getGsmPhone(this.mPhoneId);
        } else {
            loge("deleteAndCreatePhone: newVoiceRadioTech=" + newVoiceRadioTech + " is not CDMA or GSM (error) - aborting!");
            return;
        }
        if (oldPhone != null) {
            imsPhone = oldPhone.relinquishOwnershipOfImsPhone();
        }
        if (this.mActivePhone != null) {
            CallManager.getInstance().registerPhone(this.mActivePhone);
            if (imsPhone != null) {
                this.mActivePhone.acquireOwnershipOfImsPhone(imsPhone);
            }
            this.mActivePhone.registerForSimRecordsLoaded(this, 6, null);
        }
        if (oldPhone != null) {
            CallManager.getInstance().unregisterPhone(oldPhone);
            logd("Disposing old phone..");
            oldPhone.dispose();
        }
    }

    public IccSmsInterfaceManager getIccSmsInterfaceManager() {
        return this.mIccSmsInterfaceManager;
    }

    public PhoneSubInfoProxy getPhoneSubInfoProxy() {
        return this.mPhoneSubInfoProxy;
    }

    public IccPhoneBookInterfaceManagerProxy getIccPhoneBookInterfaceManagerProxy() {
        return this.mIccPhoneBookInterfaceManagerProxy;
    }

    public IccFileHandler getIccFileHandler() {
        return ((PhoneBase) this.mActivePhone).getIccFileHandler();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isImsVtCallPresent() {
        return this.mActivePhone.isImsVtCallPresent();
    }

    @Override // com.android.internal.telephony.Phone
    public void updatePhoneObject(int voiceRadioTech) {
        logd("updatePhoneObject: radioTechnology=" + voiceRadioTech);
        sendMessage(obtainMessage(5, voiceRadioTech, 0, null));
    }

    @Override // com.android.internal.telephony.Phone
    public ServiceState getServiceState() {
        return this.mActivePhone.getServiceState();
    }

    @Override // com.android.internal.telephony.Phone
    public ServiceState getBaseServiceState() {
        return this.mActivePhone.getBaseServiceState();
    }

    @Override // com.android.internal.telephony.Phone
    public CellLocation getCellLocation() {
        return this.mActivePhone.getCellLocation();
    }

    @Override // com.android.internal.telephony.Phone
    public List<CellInfo> getAllCellInfo() {
        return this.mActivePhone.getAllCellInfo();
    }

    @Override // com.android.internal.telephony.Phone
    public void setCellInfoListRate(int rateInMillis) {
        this.mActivePhone.setCellInfoListRate(rateInMillis);
    }

    @Override // com.android.internal.telephony.Phone
    public PhoneConstants.DataState getDataConnectionState() {
        return this.mActivePhone.getDataConnectionState("default");
    }

    @Override // com.android.internal.telephony.Phone
    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        return this.mActivePhone.getDataConnectionState(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public Phone.DataActivityState getDataActivityState() {
        return this.mActivePhone.getDataActivityState();
    }

    @Override // com.android.internal.telephony.Phone
    public Context getContext() {
        return this.mActivePhone.getContext();
    }

    @Override // com.android.internal.telephony.Phone
    public void disableDnsCheck(boolean b) {
        this.mActivePhone.disableDnsCheck(b);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isDnsCheckDisabled() {
        return this.mActivePhone.isDnsCheckDisabled();
    }

    @Override // com.android.internal.telephony.Phone
    public PhoneConstants.State getState() {
        return this.mActivePhone.getState();
    }

    @Override // com.android.internal.telephony.Phone
    public String getPhoneName() {
        return this.mActivePhone.getPhoneName();
    }

    @Override // com.android.internal.telephony.Phone
    public int getPhoneType() {
        return this.mActivePhone.getPhoneType();
    }

    @Override // com.android.internal.telephony.Phone
    public String[] getActiveApnTypes() {
        return this.mActivePhone.getActiveApnTypes();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean hasMatchedTetherApnSetting() {
        return this.mActivePhone.hasMatchedTetherApnSetting();
    }

    @Override // com.android.internal.telephony.Phone
    public String getActiveApnHost(String apnType) {
        return this.mActivePhone.getActiveApnHost(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public LinkProperties getLinkProperties(String apnType) {
        return this.mActivePhone.getLinkProperties(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public NetworkCapabilities getNetworkCapabilities(String apnType) {
        return this.mActivePhone.getNetworkCapabilities(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public SignalStrength getSignalStrength() {
        return this.mActivePhone.getSignalStrength();
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForUnknownConnection(Handler h, int what, Object obj) {
        this.mActivePhone.registerForUnknownConnection(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForUnknownConnection(Handler h) {
        this.mActivePhone.unregisterForUnknownConnection(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForHandoverStateChanged(Handler h, int what, Object obj) {
        this.mActivePhone.registerForHandoverStateChanged(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForHandoverStateChanged(Handler h) {
        this.mActivePhone.unregisterForHandoverStateChanged(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForPreciseCallStateChanged(Handler h, int what, Object obj) {
        this.mActivePhone.registerForPreciseCallStateChanged(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForPreciseCallStateChanged(Handler h) {
        this.mActivePhone.unregisterForPreciseCallStateChanged(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForNewRingingConnection(Handler h, int what, Object obj) {
        this.mActivePhone.registerForNewRingingConnection(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForNewRingingConnection(Handler h) {
        this.mActivePhone.unregisterForNewRingingConnection(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForVideoCapabilityChanged(Handler h, int what, Object obj) {
        this.mActivePhone.registerForVideoCapabilityChanged(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForVideoCapabilityChanged(Handler h) {
        this.mActivePhone.unregisterForVideoCapabilityChanged(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForIncomingRing(Handler h, int what, Object obj) {
        this.mActivePhone.registerForIncomingRing(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForIncomingRing(Handler h) {
        this.mActivePhone.unregisterForIncomingRing(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForDisconnect(Handler h, int what, Object obj) {
        this.mActivePhone.registerForDisconnect(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForDisconnect(Handler h) {
        this.mActivePhone.unregisterForDisconnect(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForMmiInitiate(Handler h, int what, Object obj) {
        this.mActivePhone.registerForMmiInitiate(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForMmiInitiate(Handler h) {
        this.mActivePhone.unregisterForMmiInitiate(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForMmiComplete(Handler h, int what, Object obj) {
        this.mActivePhone.registerForMmiComplete(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForMmiComplete(Handler h) {
        this.mActivePhone.unregisterForMmiComplete(h);
    }

    @Override // com.android.internal.telephony.Phone
    public List<? extends MmiCode> getPendingMmiCodes() {
        return this.mActivePhone.getPendingMmiCodes();
    }

    @Override // com.android.internal.telephony.Phone
    public void sendUssdResponse(String ussdMessge) {
        this.mActivePhone.sendUssdResponse(ussdMessge);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForServiceStateChanged(Handler h, int what, Object obj) {
        this.mActivePhone.registerForServiceStateChanged(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForServiceStateChanged(Handler h) {
        this.mActivePhone.unregisterForServiceStateChanged(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        this.mActivePhone.registerForSuppServiceNotification(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSuppServiceNotification(Handler h) {
        this.mActivePhone.unregisterForSuppServiceNotification(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSuppServiceFailed(Handler h, int what, Object obj) {
        this.mActivePhone.registerForSuppServiceFailed(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSuppServiceFailed(Handler h) {
        this.mActivePhone.unregisterForSuppServiceFailed(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj) {
        this.mActivePhone.registerForInCallVoicePrivacyOn(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForInCallVoicePrivacyOn(Handler h) {
        this.mActivePhone.unregisterForInCallVoicePrivacyOn(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj) {
        this.mActivePhone.registerForInCallVoicePrivacyOff(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForInCallVoicePrivacyOff(Handler h) {
        this.mActivePhone.unregisterForInCallVoicePrivacyOff(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForCdmaOtaStatusChange(Handler h, int what, Object obj) {
        this.mActivePhone.registerForCdmaOtaStatusChange(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForCdmaOtaStatusChange(Handler h) {
        this.mActivePhone.unregisterForCdmaOtaStatusChange(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        this.mActivePhone.registerForSubscriptionInfoReady(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSubscriptionInfoReady(Handler h) {
        this.mActivePhone.unregisterForSubscriptionInfoReady(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        this.mActivePhone.registerForEcmTimerReset(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForEcmTimerReset(Handler h) {
        this.mActivePhone.unregisterForEcmTimerReset(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForRingbackTone(Handler h, int what, Object obj) {
        this.mActivePhone.registerForRingbackTone(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForRingbackTone(Handler h) {
        this.mActivePhone.unregisterForRingbackTone(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForOnHoldTone(Handler h, int what, Object obj) {
        this.mActivePhone.registerForOnHoldTone(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForOnHoldTone(Handler h) {
        this.mActivePhone.unregisterForOnHoldTone(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForResendIncallMute(Handler h, int what, Object obj) {
        this.mActivePhone.registerForResendIncallMute(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForResendIncallMute(Handler h) {
        this.mActivePhone.unregisterForResendIncallMute(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSimRecordsLoaded(Handler h, int what, Object obj) {
        this.mActivePhone.registerForSimRecordsLoaded(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSimRecordsLoaded(Handler h) {
        this.mActivePhone.unregisterForSimRecordsLoaded(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForTtyModeReceived(Handler h, int what, Object obj) {
        this.mActivePhone.registerForTtyModeReceived(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForTtyModeReceived(Handler h) {
        this.mActivePhone.unregisterForTtyModeReceived(h);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getIccRecordsLoaded() {
        return this.mIccCardProxy.getIccRecordsLoaded();
    }

    @Override // com.android.internal.telephony.Phone
    public IccCard getIccCard() {
        return this.mIccCardProxy;
    }

    @Override // com.android.internal.telephony.Phone
    public void acceptCall(int videoState) throws CallStateException {
        this.mActivePhone.acceptCall(videoState);
    }

    @Override // com.android.internal.telephony.Phone
    public void deflectCall(String number) throws CallStateException {
        this.mActivePhone.deflectCall(number);
    }

    @Override // com.android.internal.telephony.Phone
    public void rejectCall() throws CallStateException {
        this.mActivePhone.rejectCall();
    }

    @Override // com.android.internal.telephony.Phone
    public void switchHoldingAndActive() throws CallStateException {
        this.mActivePhone.switchHoldingAndActive();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean canConference() {
        return this.mActivePhone.canConference();
    }

    @Override // com.android.internal.telephony.Phone
    public void conference() throws CallStateException {
        this.mActivePhone.conference();
    }

    @Override // com.android.internal.telephony.Phone
    public void enableEnhancedVoicePrivacy(boolean enable, Message onComplete) {
        this.mActivePhone.enableEnhancedVoicePrivacy(enable, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getEnhancedVoicePrivacy(Message onComplete) {
        this.mActivePhone.getEnhancedVoicePrivacy(onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean canTransfer() {
        return this.mActivePhone.canTransfer();
    }

    @Override // com.android.internal.telephony.Phone
    public void explicitCallTransfer() throws CallStateException {
        this.mActivePhone.explicitCallTransfer();
    }

    @Override // com.android.internal.telephony.Phone
    public void clearDisconnected() {
        this.mActivePhone.clearDisconnected();
    }

    @Override // com.android.internal.telephony.Phone
    public Call getForegroundCall() {
        return this.mActivePhone.getForegroundCall();
    }

    @Override // com.android.internal.telephony.Phone
    public Call getBackgroundCall() {
        return this.mActivePhone.getBackgroundCall();
    }

    @Override // com.android.internal.telephony.Phone
    public Call getRingingCall() {
        return this.mActivePhone.getRingingCall();
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState) throws CallStateException {
        return this.mActivePhone.dial(dialString, videoState);
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, UUSInfo uusInfo, int videoState) throws CallStateException {
        return this.mActivePhone.dial(dialString, uusInfo, videoState);
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState, int prefix) throws CallStateException {
        if (TelBrand.IS_DCM) {
            return this.mActivePhone.dial(dialString, videoState, prefix);
        }
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, UUSInfo uusInfo, int videoState, int prefix) throws CallStateException {
        if (TelBrand.IS_DCM) {
            return this.mActivePhone.dial(dialString, uusInfo, videoState, prefix);
        }
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState, Bundle extras, int prefix) throws CallStateException {
        if (TelBrand.IS_DCM) {
            return this.mActivePhone.dial(dialString, videoState, extras, prefix);
        }
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public void addParticipant(String dialString) throws CallStateException {
        this.mActivePhone.addParticipant(dialString);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean handlePinMmi(String dialString) {
        return this.mActivePhone.handlePinMmi(dialString);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean handleInCallMmiCommands(String command) throws CallStateException {
        return this.mActivePhone.handleInCallMmiCommands(command);
    }

    @Override // com.android.internal.telephony.Phone
    public void sendDtmf(char c) {
        this.mActivePhone.sendDtmf(c);
    }

    @Override // com.android.internal.telephony.Phone
    public void startDtmf(char c) {
        this.mActivePhone.startDtmf(c);
    }

    @Override // com.android.internal.telephony.Phone
    public void stopDtmf() {
        this.mActivePhone.stopDtmf();
    }

    @Override // com.android.internal.telephony.Phone
    public void setRadioPower(boolean power) {
        this.mActivePhone.setRadioPower(power);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getMessageWaitingIndicator() {
        return this.mActivePhone.getMessageWaitingIndicator();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getCallForwardingIndicator() {
        return this.mActivePhone.getCallForwardingIndicator();
    }

    @Override // com.android.internal.telephony.Phone
    public String getLine1Number() {
        return this.mActivePhone.getLine1Number();
    }

    @Override // com.android.internal.telephony.Phone
    public String getCdmaMin() {
        return this.mActivePhone.getCdmaMin();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isMinInfoReady() {
        return this.mActivePhone.isMinInfoReady();
    }

    @Override // com.android.internal.telephony.Phone
    public String getCdmaPrlVersion() {
        return this.mActivePhone.getCdmaPrlVersion();
    }

    @Override // com.android.internal.telephony.Phone
    public String getLine1AlphaTag() {
        return this.mActivePhone.getLine1AlphaTag();
    }

    @Override // com.android.internal.telephony.Phone
    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        this.mActivePhone.setLine1Number(alphaTag, number, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public String getVoiceMailNumber() {
        return this.mActivePhone.getVoiceMailNumber();
    }

    @Override // com.android.internal.telephony.Phone
    public int getVoiceMessageCount() {
        return this.mActivePhone.getVoiceMessageCount();
    }

    @Override // com.android.internal.telephony.Phone
    public String getVoiceMailAlphaTag() {
        return this.mActivePhone.getVoiceMailAlphaTag();
    }

    @Override // com.android.internal.telephony.Phone
    public void setVoiceMailNumber(String alphaTag, String voiceMailNumber, Message onComplete) {
        this.mActivePhone.setVoiceMailNumber(alphaTag, voiceMailNumber, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        this.mActivePhone.getCallForwardingOption(commandInterfaceCFReason, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallForwardingOption(int commandInterfaceCFReason, int serviceClass, Message onComplete) {
        this.mActivePhone.getCallForwardingOption(commandInterfaceCFReason, serviceClass, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallForwardingOption(int commandInterfaceCFReason, int commandInterfaceCFAction, int serviceClass, String dialingNumber, int timerSeconds, Message onComplete) {
        this.mActivePhone.setCallForwardingOption(commandInterfaceCFReason, commandInterfaceCFAction, serviceClass, dialingNumber, timerSeconds, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallForwardingUncondTimerOption(int commandInterfaceCFReason, Message onComplete) {
        this.mActivePhone.getCallForwardingUncondTimerOption(commandInterfaceCFReason, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallForwardingOption(int commandInterfaceCFReason, int commandInterfaceCFAction, String dialingNumber, int timerSeconds, Message onComplete) {
        this.mActivePhone.setCallForwardingOption(commandInterfaceCFReason, commandInterfaceCFAction, dialingNumber, timerSeconds, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallForwardingUncondTimerOption(int startHour, int startMinute, int endHour, int endMinute, int commandInterfaceCFReason, int commandInterfaceCFAction, String dialingNumber, Message onComplete) {
        this.mActivePhone.setCallForwardingUncondTimerOption(startHour, startMinute, endHour, endMinute, commandInterfaceCFReason, commandInterfaceCFAction, dialingNumber, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        this.mActivePhone.getOutgoingCallerIdDisplay(onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode, Message onComplete) {
        this.mActivePhone.setOutgoingCallerIdDisplay(commandInterfaceCLIRMode, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallWaiting(Message onComplete) {
        this.mActivePhone.getCallWaiting(onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallWaiting(int serviceClass, Message onComplete) {
        this.mActivePhone.getCallWaiting(serviceClass, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallWaiting(boolean enable, int serviceClass, Message onComplete) {
        this.mActivePhone.setCallWaiting(enable, serviceClass, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallBarring(String facility, boolean lockState, String password, int serviceClass, Message onComplete) {
        this.mActivePhone.setCallBarring(facility, lockState, password, serviceClass, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallBarring(String facility, String password, int serviceClass, Message onComplete) {
        this.mActivePhone.getCallBarring(facility, password, serviceClass, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallBarring(String facility, boolean lockState, String password, Message onComplete) {
        this.mActivePhone.setCallBarring(facility, lockState, password, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallBarring(String facility, String password, Message onComplete) {
        this.mActivePhone.getCallBarring(facility, password, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallBarring(String facility, Message onComplete) {
        this.mActivePhone.getCallBarring(facility, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallWaiting(boolean enable, Message onComplete) {
        this.mActivePhone.setCallWaiting(enable, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getAvailableNetworks(Message response) {
        this.mActivePhone.getAvailableNetworks(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setNetworkSelectionModeAutomatic(Message response) {
        this.mActivePhone.setNetworkSelectionModeAutomatic(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void getNetworkSelectionMode(Message response) {
        this.mActivePhone.getNetworkSelectionMode(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void selectNetworkManually(OperatorInfo network, Message response) {
        this.mActivePhone.selectNetworkManually(network, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setPreferredNetworkType(int networkType, Message response) {
        this.mActivePhone.setPreferredNetworkType(networkType, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void getPreferredNetworkType(Message response) {
        this.mActivePhone.getPreferredNetworkType(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void getNeighboringCids(Message response) {
        this.mActivePhone.getNeighboringCids(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        this.mActivePhone.setOnPostDialCharacter(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void setMute(boolean muted) {
        this.mActivePhone.setMute(muted);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getMute() {
        return this.mActivePhone.getMute();
    }

    @Override // com.android.internal.telephony.Phone
    public void setEchoSuppressionEnabled() {
        this.mActivePhone.setEchoSuppressionEnabled();
    }

    @Override // com.android.internal.telephony.Phone
    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        this.mActivePhone.invokeOemRilRequestRaw(data, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        this.mActivePhone.invokeOemRilRequestStrings(strings, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void getDataCallList(Message response) {
        this.mActivePhone.getDataCallList(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void updateServiceLocation() {
        this.mActivePhone.updateServiceLocation();
    }

    @Override // com.android.internal.telephony.Phone
    public void enableLocationUpdates() {
        this.mActivePhone.enableLocationUpdates();
    }

    @Override // com.android.internal.telephony.Phone
    public void disableLocationUpdates() {
        this.mActivePhone.disableLocationUpdates();
    }

    @Override // com.android.internal.telephony.Phone
    public void setUnitTestMode(boolean f) {
        this.mActivePhone.setUnitTestMode(f);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getUnitTestMode() {
        return this.mActivePhone.getUnitTestMode();
    }

    @Override // com.android.internal.telephony.Phone
    public void setBandMode(int bandMode, Message response) {
        this.mActivePhone.setBandMode(bandMode, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void queryAvailableBandMode(Message response) {
        this.mActivePhone.queryAvailableBandMode(response);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getDataRoamingEnabled() {
        return this.mActivePhone.getDataRoamingEnabled();
    }

    @Override // com.android.internal.telephony.Phone
    public void setDataRoamingEnabled(boolean enable) {
        this.mActivePhone.setDataRoamingEnabled(enable);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getDataEnabled() {
        return this.mActivePhone.getDataEnabled();
    }

    @Override // com.android.internal.telephony.Phone
    public void setDataEnabled(boolean enable) {
        this.mActivePhone.setDataEnabled(enable);
    }

    @Override // com.android.internal.telephony.Phone
    public void queryCdmaRoamingPreference(Message response) {
        this.mActivePhone.queryCdmaRoamingPreference(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        this.mActivePhone.setCdmaRoamingPreference(cdmaRoamingType, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCdmaSubscription(int cdmaSubscriptionType, Message response) {
        this.mActivePhone.setCdmaSubscription(cdmaSubscriptionType, response);
    }

    @Override // com.android.internal.telephony.Phone
    public SimulatedRadioControl getSimulatedRadioControl() {
        return this.mActivePhone.getSimulatedRadioControl();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isDataConnectivityPossible() {
        return this.mActivePhone.isDataConnectivityPossible("default");
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isOnDemandDataPossible(String apnType) {
        return this.mActivePhone.isOnDemandDataPossible(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isDataConnectivityPossible(String apnType) {
        return this.mActivePhone.isDataConnectivityPossible(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public String getDeviceId() {
        return this.mActivePhone.getDeviceId();
    }

    @Override // com.android.internal.telephony.Phone
    public String getDeviceSvn() {
        return this.mActivePhone.getDeviceSvn();
    }

    @Override // com.android.internal.telephony.Phone
    public String getSubscriberId() {
        return this.mActivePhone.getSubscriberId();
    }

    @Override // com.android.internal.telephony.Phone
    public String getMccMncOnSimLock() {
        return this.mActivePhone.getMccMncOnSimLock();
    }

    @Override // com.android.internal.telephony.Phone
    public String getImsiOnSimLock() {
        return this.mActivePhone.getImsiOnSimLock();
    }

    @Override // com.android.internal.telephony.Phone
    public int getBrand() {
        return this.mActivePhone.getBrand();
    }

    @Override // com.android.internal.telephony.Phone
    public String getGroupIdLevel1() {
        return this.mActivePhone.getGroupIdLevel1();
    }

    @Override // com.android.internal.telephony.Phone
    public String getIccSerialNumber() {
        return this.mActivePhone.getIccSerialNumber();
    }

    @Override // com.android.internal.telephony.Phone
    public String getEsn() {
        return this.mActivePhone.getEsn();
    }

    @Override // com.android.internal.telephony.Phone
    public String getMeid() {
        return this.mActivePhone.getMeid();
    }

    @Override // com.android.internal.telephony.Phone
    public String getMsisdn() {
        return this.mActivePhone.getMsisdn();
    }

    @Override // com.android.internal.telephony.Phone
    public String getImei() {
        return this.mActivePhone.getImei();
    }

    @Override // com.android.internal.telephony.Phone
    public String getNai() {
        return this.mActivePhone.getNai();
    }

    @Override // com.android.internal.telephony.Phone
    public PhoneSubInfo getPhoneSubInfo() {
        return this.mActivePhone.getPhoneSubInfo();
    }

    @Override // com.android.internal.telephony.Phone
    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return this.mActivePhone.getIccPhoneBookInterfaceManager();
    }

    @Override // com.android.internal.telephony.Phone
    public void setUiTTYMode(int uiTtyMode, Message onComplete) {
        this.mActivePhone.setUiTTYMode(uiTtyMode, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setTTYMode(int ttyMode, Message onComplete) {
        this.mActivePhone.setTTYMode(ttyMode, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void queryTTYMode(Message onComplete) {
        this.mActivePhone.queryTTYMode(onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void activateCellBroadcastSms(int activate, Message response) {
        this.mActivePhone.activateCellBroadcastSms(activate, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void getCellBroadcastSmsConfig(Message response) {
        this.mActivePhone.getCellBroadcastSmsConfig(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        this.mActivePhone.setCellBroadcastSmsConfig(configValuesArray, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void notifyDataActivity() {
        this.mActivePhone.notifyDataActivity();
    }

    @Override // com.android.internal.telephony.Phone
    public void getSmscAddress(Message result) {
        this.mActivePhone.getSmscAddress(result);
    }

    @Override // com.android.internal.telephony.Phone
    public void setSmscAddress(String address, Message result) {
        this.mActivePhone.setSmscAddress(address, result);
    }

    @Override // com.android.internal.telephony.Phone
    public int getCdmaEriIconIndex() {
        return this.mActivePhone.getCdmaEriIconIndex();
    }

    @Override // com.android.internal.telephony.Phone
    public String getCdmaEriText() {
        return this.mActivePhone.getCdmaEriText();
    }

    @Override // com.android.internal.telephony.Phone
    public int getCdmaEriIconMode() {
        return this.mActivePhone.getCdmaEriIconMode();
    }

    public Phone getActivePhone() {
        return this.mActivePhone;
    }

    @Override // com.android.internal.telephony.Phone
    public void sendBurstDtmf(String dtmfString, int on, int off, Message onComplete) {
        this.mActivePhone.sendBurstDtmf(dtmfString, on, off, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void exitEmergencyCallbackMode() {
        this.mActivePhone.exitEmergencyCallbackMode();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean needsOtaServiceProvisioning() {
        return this.mActivePhone.needsOtaServiceProvisioning();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isOtaSpNumber(String dialStr) {
        return this.mActivePhone.isOtaSpNumber(dialStr);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForCallWaiting(Handler h, int what, Object obj) {
        this.mActivePhone.registerForCallWaiting(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForCallWaiting(Handler h) {
        this.mActivePhone.unregisterForCallWaiting(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSignalInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForSignalInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSignalInfo(Handler h) {
        this.mActivePhone.unregisterForSignalInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForDisplayInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForDisplayInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForDisplayInfo(Handler h) {
        this.mActivePhone.unregisterForDisplayInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForNumberInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForNumberInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForNumberInfo(Handler h) {
        this.mActivePhone.unregisterForNumberInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForRedirectedNumberInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForRedirectedNumberInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForRedirectedNumberInfo(Handler h) {
        this.mActivePhone.unregisterForRedirectedNumberInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForLineControlInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForLineControlInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForLineControlInfo(Handler h) {
        this.mActivePhone.unregisterForLineControlInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerFoT53ClirlInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerFoT53ClirlInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForT53ClirInfo(Handler h) {
        this.mActivePhone.unregisterForT53ClirInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForT53AudioControlInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForT53AudioControlInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForT53AudioControlInfo(Handler h) {
        this.mActivePhone.unregisterForT53AudioControlInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForRadioOffOrNotAvailable(Handler h, int what, Object obj) {
        this.mActivePhone.registerForRadioOffOrNotAvailable(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForRadioOffOrNotAvailable(Handler h) {
        this.mActivePhone.unregisterForRadioOffOrNotAvailable(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void setOnEcbModeExitResponse(Handler h, int what, Object obj) {
        this.mActivePhone.setOnEcbModeExitResponse(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unsetOnEcbModeExitResponse(Handler h) {
        this.mActivePhone.unsetOnEcbModeExitResponse(h);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isManualNetSelAllowed() {
        return this.mActivePhone.isManualNetSelAllowed();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isCspPlmnEnabled() {
        return this.mActivePhone.isCspPlmnEnabled();
    }

    @Override // com.android.internal.telephony.Phone
    public IsimRecords getIsimRecords() {
        return this.mActivePhone.getIsimRecords();
    }

    @Override // com.android.internal.telephony.Phone
    public int getLteOnCdmaMode() {
        return this.mActivePhone.getLteOnCdmaMode();
    }

    @Override // com.android.internal.telephony.Phone
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        this.mActivePhone.setVoiceMessageWaiting(line, countWaiting);
    }

    @Override // com.android.internal.telephony.Phone
    public UsimServiceTable getUsimServiceTable() {
        return this.mActivePhone.getUsimServiceTable();
    }

    @Override // com.android.internal.telephony.Phone
    public UiccCard getUiccCard() {
        return this.mActivePhone.getUiccCard();
    }

    @Override // com.android.internal.telephony.Phone
    public void nvReadItem(int itemID, Message response) {
        this.mActivePhone.nvReadItem(itemID, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void nvWriteItem(int itemID, String itemValue, Message response) {
        this.mActivePhone.nvWriteItem(itemID, itemValue, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response) {
        this.mActivePhone.nvWriteCdmaPrl(preferredRoamingList, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void nvResetConfig(int resetType, Message response) {
        this.mActivePhone.nvResetConfig(resetType, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void dispose() {
        if (this.mActivePhone != null) {
            this.mActivePhone.unregisterForSimRecordsLoaded(this);
        }
        this.mCommandsInterface.unregisterForOn(this);
        this.mCommandsInterface.unregisterForVoiceRadioTechChanged(this);
        this.mCommandsInterface.unregisterForRilConnected(this);
    }

    @Override // com.android.internal.telephony.Phone
    public void removeReferences() {
        this.mActivePhone = null;
        this.mCommandsInterface = null;
    }

    public boolean updateCurrentCarrierInProvider() {
        if (this.mActivePhone instanceof CDMALTEPhone) {
            return ((CDMALTEPhone) this.mActivePhone).updateCurrentCarrierInProvider();
        }
        if (this.mActivePhone instanceof GSMPhone) {
            return ((GSMPhone) this.mActivePhone).updateCurrentCarrierInProvider();
        }
        loge("Phone object is not MultiSim. This should not hit!!!!");
        return false;
    }

    public void updateDataConnectionTracker() {
        logd("Updating Data Connection Tracker");
        if (this.mActivePhone instanceof CDMALTEPhone) {
            ((CDMALTEPhone) this.mActivePhone).updateDataConnectionTracker();
        } else if (this.mActivePhone instanceof GSMPhone) {
            ((GSMPhone) this.mActivePhone).updateDataConnectionTracker();
        } else {
            loge("Phone object is not MultiSim. This should not hit!!!!");
        }
    }

    public void setInternalDataEnabled(boolean enable) {
        setInternalDataEnabled(enable, null);
    }

    public boolean setInternalDataEnabledFlag(boolean enable) {
        if (this.mActivePhone instanceof CDMALTEPhone) {
            return ((CDMALTEPhone) this.mActivePhone).setInternalDataEnabledFlag(enable);
        }
        if (this.mActivePhone instanceof GSMPhone) {
            return ((GSMPhone) this.mActivePhone).setInternalDataEnabledFlag(enable);
        }
        loge("Phone object is not MultiSim. This should not hit!!!!");
        return false;
    }

    public void setInternalDataEnabled(boolean enable, Message onCompleteMsg) {
        if (this.mActivePhone instanceof CDMALTEPhone) {
            ((CDMALTEPhone) this.mActivePhone).setInternalDataEnabled(enable, onCompleteMsg);
        } else if (this.mActivePhone instanceof GSMPhone) {
            ((GSMPhone) this.mActivePhone).setInternalDataEnabled(enable, onCompleteMsg);
        } else {
            loge("Phone object is not MultiSim. This should not hit!!!!");
        }
    }

    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        if (this.mActivePhone instanceof CDMALTEPhone) {
            ((CDMALTEPhone) this.mActivePhone).registerForAllDataDisconnected(h, what, obj);
        } else if (this.mActivePhone instanceof GSMPhone) {
            ((GSMPhone) this.mActivePhone).registerForAllDataDisconnected(h, what, obj);
        } else {
            loge("Phone object is not MultiSim. This should not hit!!!!");
        }
    }

    public void unregisterForAllDataDisconnected(Handler h) {
        if (this.mActivePhone instanceof CDMALTEPhone) {
            ((CDMALTEPhone) this.mActivePhone).unregisterForAllDataDisconnected(h);
        } else if (this.mActivePhone instanceof GSMPhone) {
            ((GSMPhone) this.mActivePhone).unregisterForAllDataDisconnected(h);
        } else {
            loge("Phone object is not MultiSim. This should not hit!!!!");
        }
    }

    @Override // com.android.internal.telephony.Phone
    public int changeMode(boolean mode, String apn, String userId, String password, int authType, String dns1, String dns2, String proxyHost, String proxyPort) {
        if (TelBrand.IS_KDDI) {
            return this.mActivePhone.changeMode(mode, apn, userId, password, authType, dns1, dns2, proxyHost, proxyPort);
        }
        return -1;
    }

    @Override // com.android.internal.telephony.Phone
    public int getConnStatus() {
        if (TelBrand.IS_KDDI) {
            return this.mActivePhone.getConnStatus();
        }
        return 6;
    }

    @Override // com.android.internal.telephony.Phone
    public String[] getConnInfo() {
        if (TelBrand.IS_KDDI) {
            return this.mActivePhone.getConnInfo();
        }
        return new String[3];
    }

    @Override // com.android.internal.telephony.Phone
    public int getSubId() {
        return this.mActivePhone.getSubId();
    }

    @Override // com.android.internal.telephony.Phone
    public int getPhoneId() {
        return this.mActivePhone.getPhoneId();
    }

    @Override // com.android.internal.telephony.Phone
    public String[] getPcscfAddress(String apnType) {
        return this.mActivePhone.getPcscfAddress(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public void setImsRegistrationState(boolean registered) {
        logd("setImsRegistrationState - registered: " + registered);
        this.mActivePhone.setImsRegistrationState(registered);
        if (this.mActivePhone.getPhoneName().equals("GSM")) {
            ((GSMPhone) this.mActivePhone).getServiceStateTracker().setImsRegistrationState(registered);
        } else if (this.mActivePhone.getPhoneName().equals("CDMA")) {
            ((CDMAPhone) this.mActivePhone).getServiceStateTracker().setImsRegistrationState(registered);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public Phone getImsPhone() {
        return this.mActivePhone.getImsPhone();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isUtEnabled() {
        return this.mActivePhone.isUtEnabled();
    }

    @Override // com.android.internal.telephony.Phone
    public ImsPhone relinquishOwnershipOfImsPhone() {
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public void acquireOwnershipOfImsPhone(ImsPhone imsPhone) {
    }

    @Override // com.android.internal.telephony.Phone
    public int getVoicePhoneServiceState() {
        return this.mActivePhone.getVoicePhoneServiceState();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean setOperatorBrandOverride(String brand) {
        return this.mActivePhone.setOperatorBrandOverride(brand);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean setRoamingOverride(List<String> gsmRoamingList, List<String> gsmNonRoamingList, List<String> cdmaRoamingList, List<String> cdmaNonRoamingList) {
        return this.mActivePhone.setRoamingOverride(gsmRoamingList, gsmNonRoamingList, cdmaRoamingList, cdmaNonRoamingList);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isRadioAvailable() {
        return this.mCommandsInterface.getRadioState().isAvailable();
    }

    @Override // com.android.internal.telephony.Phone
    public void shutdownRadio() {
        this.mActivePhone.shutdownRadio();
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallBarringOption(String facility, String password, Message onComplete) {
        this.mActivePhone.getCallBarringOption(facility, password, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallBarringOption(String facility, boolean lockState, String password, Message onComplete) {
        this.mActivePhone.setCallBarringOption(facility, lockState, password, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void requestChangeCbPsw(String facility, String oldPwd, String newPwd, Message result) {
        this.mActivePhone.requestChangeCbPsw(facility, oldPwd, newPwd, result);
    }

    @Override // com.android.internal.telephony.Phone
    public void setLocalCallHold(int lchStatus) {
        this.mActivePhone.setLocalCallHold(lchStatus);
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, int videoState, Bundle extras) throws CallStateException {
        return this.mActivePhone.dial(dialString, videoState, extras);
    }

    @Override // com.android.internal.telephony.Phone
    public void getIncomingAnonymousCallBarring(Message onComplete) {
        this.mActivePhone.getIncomingAnonymousCallBarring(onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setIncomingAnonymousCallBarring(boolean lockState, Message onComplete) {
        this.mActivePhone.setIncomingAnonymousCallBarring(lockState, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getIncomingSpecificDnCallBarring(Message onComplete) {
        this.mActivePhone.getIncomingSpecificDnCallBarring(onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setIncomingSpecificDnCallBarring(int operation, String[] icbNum, Message onComplete) {
        this.mActivePhone.setIncomingSpecificDnCallBarring(operation, icbNum, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isImsRegistered() {
        return this.mActivePhone.isImsRegistered();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        try {
            ((PhoneBase) this.mActivePhone).dump(fd, pw, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        try {
            this.mPhoneSubInfoProxy.dump(fd, pw, args);
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        try {
            this.mIccCardProxy.dump(fd, pw, args);
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
    }

    @Override // com.android.internal.telephony.Phone
    public void setPreferredNetworkTypeWithOptimizeSetting(int networkType, boolean enable, Message response) {
        this.mActivePhone.setPreferredNetworkTypeWithOptimizeSetting(networkType, enable, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void getPreferredNetworkTypeWithOptimizeSetting(Message response) {
        this.mActivePhone.getPreferredNetworkTypeWithOptimizeSetting(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void resetDunProfiles() {
        this.mActivePhone.resetDunProfiles();
    }

    @Override // com.android.internal.telephony.Phone
    public void setLimitationByChameleon(boolean isLimitation, Message response) {
        this.mActivePhone.setLimitationByChameleon(isLimitation, response);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isDunConnectionPossible() {
        return this.mActivePhone.isDunConnectionPossible();
    }

    @Override // com.android.internal.telephony.Phone
    public void setMobileDataEnabledDun(boolean isEnable) {
        this.mActivePhone.setMobileDataEnabledDun(isEnable);
    }

    @Override // com.android.internal.telephony.Phone
    public void setProfilePdpType(int cid, String pdpType) {
        this.mActivePhone.setProfilePdpType(cid, pdpType);
    }

    @Override // com.android.internal.telephony.Phone
    public void declineCall() throws CallStateException {
        this.mActivePhone.declineCall();
    }

    @Override // com.android.internal.telephony.Phone
    public void setBandPref(long lteBand, int wcdmaBand, Message response) {
        this.mActivePhone.setBandPref(lteBand, wcdmaBand, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void getBandPref(Message response) {
        this.mActivePhone.getBandPref(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setModemSettingsByChameleon(int pattern, Message response) {
        this.mActivePhone.setModemSettingsByChameleon(pattern, response);
    }
}
