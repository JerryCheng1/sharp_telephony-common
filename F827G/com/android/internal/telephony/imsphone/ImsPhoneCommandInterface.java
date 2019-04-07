package com.android.internal.telephony.imsphone;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import com.android.internal.telephony.BaseCommands;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;

class ImsPhoneCommandInterface extends BaseCommands implements CommandsInterface {
    ImsPhoneCommandInterface(Context context) {
        super(context);
    }

    public void acceptCall(Message message) {
    }

    public void acknowledgeIncomingGsmSmsWithPdu(boolean z, String str, Message message) {
    }

    public void acknowledgeLastIncomingCdmaSms(boolean z, int i, Message message) {
    }

    public void acknowledgeLastIncomingGsmSms(boolean z, int i, Message message) {
    }

    public void cancelPendingUssd(Message message) {
    }

    public void changeBarringPassword(String str, String str2, String str3, Message message) {
    }

    public void changeIccPin(String str, String str2, Message message) {
    }

    public void changeIccPin2(String str, String str2, Message message) {
    }

    public void changeIccPin2ForApp(String str, String str2, String str3, Message message) {
    }

    public void changeIccPinForApp(String str, String str2, String str3, Message message) {
    }

    public void conference(Message message) {
    }

    public void deactivateDataCall(int i, int i2, Message message) {
    }

    public void deleteSmsOnRuim(int i, Message message) {
    }

    public void deleteSmsOnSim(int i, Message message) {
    }

    public void dial(String str, int i, Message message) {
    }

    public void dial(String str, int i, UUSInfo uUSInfo, Message message) {
    }

    public void exitEmergencyCallbackMode(Message message) {
    }

    public void explicitCallTransfer(Message message) {
    }

    public void getAvailableNetworks(Message message) {
    }

    public void getBasebandVersion(Message message) {
    }

    public void getCDMASubscription(Message message) {
    }

    public void getCLIR(Message message) {
    }

    public void getCdmaBroadcastConfig(Message message) {
    }

    public void getCdmaSubscriptionSource(Message message) {
    }

    public void getCellInfoList(Message message) {
    }

    public void getCurrentCalls(Message message) {
    }

    public void getDataCallList(Message message) {
    }

    public void getDataRegistrationState(Message message) {
    }

    public void getDeviceIdentity(Message message) {
    }

    public void getGsmBroadcastConfig(Message message) {
    }

    public void getHardwareConfig(Message message) {
    }

    public void getIMEI(Message message) {
    }

    public void getIMEISV(Message message) {
    }

    public void getIMSI(Message message) {
    }

    public void getIMSIForApp(String str, Message message) {
    }

    public void getIccCardStatus(Message message) {
    }

    public void getImsRegistrationState(Message message) {
    }

    public void getLastCallFailCause(Message message) {
    }

    public void getLastDataCallFailCause(Message message) {
    }

    @Deprecated
    public void getLastPdpFailCause(Message message) {
    }

    public void getMute(Message message) {
    }

    public void getNeighboringCids(Message message) {
    }

    public void getNetworkSelectionMode(Message message) {
    }

    public void getOperator(Message message) {
    }

    @Deprecated
    public void getPDPContextList(Message message) {
    }

    public void getPreferredNetworkType(Message message) {
    }

    public void getPreferredVoicePrivacy(Message message) {
    }

    public void getSignalStrength(Message message) {
    }

    public void getSmscAddress(Message message) {
    }

    public void getVoiceRadioTechnology(Message message) {
    }

    public void getVoiceRegistrationState(Message message) {
    }

    public void handleCallSetupRequestFromSim(boolean z, Message message) {
    }

    public void hangupConnection(int i, Message message) {
    }

    public void hangupForegroundResumeBackground(Message message) {
    }

    public void hangupWaitingOrBackground(Message message) {
    }

    public void iccIO(int i, int i2, String str, int i3, int i4, int i5, String str2, String str3, Message message) {
    }

    public void iccIOForApp(int i, int i2, String str, int i3, int i4, int i5, String str2, String str3, String str4, Message message) {
    }

    public void invokeOemRilRequestRaw(byte[] bArr, Message message) {
    }

    public void invokeOemRilRequestStrings(String[] strArr, Message message) {
    }

    public void nvReadItem(int i, Message message) {
    }

    public void nvResetConfig(int i, Message message) {
    }

    public void nvWriteCdmaPrl(byte[] bArr, Message message) {
    }

    public void nvWriteItem(int i, String str, Message message) {
    }

    public void queryAvailableBandMode(Message message) {
    }

    public void queryCLIP(Message message) {
    }

    public void queryCallForwardStatus(int i, int i2, String str, Message message) {
    }

    public void queryCallWaiting(int i, Message message) {
    }

    public void queryCdmaRoamingPreference(Message message) {
    }

    public void queryFacilityLock(String str, String str2, int i, Message message) {
    }

    public void queryFacilityLockForApp(String str, String str2, int i, String str3, Message message) {
    }

    public void queryTTYMode(Message message) {
    }

    public void rejectCall(Message message) {
    }

    public void reportSmsMemoryStatus(boolean z, Message message) {
    }

    public void reportStkServiceIsRunning(Message message) {
    }

    public void requestIccSimAuthentication(int i, String str, String str2, Message message) {
    }

    public void requestIsimAuthentication(String str, Message message) {
    }

    public void requestShutdown(Message message) {
    }

    public void resetRadio(Message message) {
    }

    public void sendBurstDtmf(String str, int i, int i2, Message message) {
    }

    public void sendCDMAFeatureCode(String str, Message message) {
    }

    public void sendCdmaSms(byte[] bArr, Message message) {
    }

    public void sendDtmf(char c, Message message) {
    }

    public void sendEnvelope(String str, Message message) {
    }

    public void sendEnvelopeWithStatus(String str, Message message) {
    }

    public void sendImsCdmaSms(byte[] bArr, int i, int i2, Message message) {
    }

    public void sendImsGsmSms(String str, String str2, int i, int i2, Message message) {
    }

    public void sendSMS(String str, String str2, Message message) {
    }

    public void sendSMSExpectMore(String str, String str2, Message message) {
    }

    public void sendTerminalResponse(String str, Message message) {
    }

    public void sendUSSD(String str, Message message) {
    }

    public void separateConnection(int i, Message message) {
    }

    public void setBandMode(int i, Message message) {
    }

    public void setCLIR(int i, Message message) {
    }

    public void setCallForward(int i, int i2, int i3, String str, int i4, Message message) {
    }

    public void setCallWaiting(boolean z, int i, Message message) {
    }

    public void setCdmaBroadcastActivation(boolean z, Message message) {
    }

    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] cdmaSmsBroadcastConfigInfoArr, Message message) {
    }

    public void setCdmaRoamingPreference(int i, Message message) {
    }

    public void setCdmaSubscriptionSource(int i, Message message) {
    }

    public void setCellInfoListRate(int i, Message message) {
    }

    public void setDataProfile(DataProfile[] dataProfileArr, Message message) {
    }

    public void setFacilityLock(String str, boolean z, String str2, int i, Message message) {
    }

    public void setFacilityLockForApp(String str, boolean z, String str2, int i, String str3, Message message) {
    }

    public void setGsmBroadcastActivation(boolean z, Message message) {
    }

    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] smsBroadcastConfigInfoArr, Message message) {
    }

    public void setInitialAttachApn(String str, String str2, int i, String str3, String str4, Message message) {
    }

    public void setLocationUpdates(boolean z, Message message) {
    }

    public void setMute(boolean z, Message message) {
    }

    public void setNetworkSelectionModeAutomatic(Message message) {
    }

    public void setNetworkSelectionModeManual(String str, Message message) {
    }

    public void setOnNITZTime(Handler handler, int i, Object obj) {
    }

    public void setPhoneType(int i) {
    }

    public void setPreferredNetworkType(int i, Message message) {
    }

    public void setPreferredVoicePrivacy(boolean z, Message message) {
    }

    public void setRadioPower(boolean z, Message message) {
    }

    public void setSmscAddress(String str, Message message) {
    }

    public void setSuppServiceNotifications(boolean z, Message message) {
    }

    public void setTTYMode(int i, Message message) {
    }

    public void setupDataCall(String str, String str2, String str3, String str4, String str5, String str6, String str7, Message message) {
    }

    public void startDtmf(char c, Message message) {
    }

    public void stopDtmf(Message message) {
    }

    public void supplyDepersonalization(String str, String str2, Message message) {
    }

    public void supplyIccPin(String str, Message message) {
    }

    public void supplyIccPin2(String str, Message message) {
    }

    public void supplyIccPin2ForApp(String str, String str2, Message message) {
    }

    public void supplyIccPinForApp(String str, String str2, Message message) {
    }

    public void supplyIccPuk(String str, String str2, Message message) {
    }

    public void supplyIccPuk2(String str, String str2, Message message) {
    }

    public void supplyIccPuk2ForApp(String str, String str2, String str3, Message message) {
    }

    public void supplyIccPukForApp(String str, String str2, String str3, Message message) {
    }

    public void switchWaitingOrHoldingAndActive(Message message) {
    }

    public void writeSmsToRuim(int i, String str, Message message) {
    }

    public void writeSmsToSim(int i, String str, String str2, Message message) {
    }
}
