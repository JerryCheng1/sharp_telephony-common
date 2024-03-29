package com.android.internal.telephony;

import android.os.Handler;
import android.os.Message;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public interface CommandsInterface {
    public static final String CB_FACILITY_BAIC = "AI";
    public static final String CB_FACILITY_BAICr = "IR";
    public static final String CB_FACILITY_BAOC = "AO";
    public static final String CB_FACILITY_BAOIC = "OI";
    public static final String CB_FACILITY_BAOICxH = "OX";
    public static final String CB_FACILITY_BA_ALL = "AB";
    public static final String CB_FACILITY_BA_FD = "FD";
    public static final String CB_FACILITY_BA_MO = "AG";
    public static final String CB_FACILITY_BA_MT = "AC";
    public static final String CB_FACILITY_BA_SIM = "SC";
    public static final int CDMA_SMS_FAIL_CAUSE_ENCODING_PROBLEM = 96;
    public static final int CDMA_SMS_FAIL_CAUSE_INVALID_TELESERVICE_ID = 4;
    public static final int CDMA_SMS_FAIL_CAUSE_OTHER_TERMINAL_PROBLEM = 39;
    public static final int CDMA_SMS_FAIL_CAUSE_RESOURCE_SHORTAGE = 35;
    public static final int CF_ACTION_DISABLE = 0;
    public static final int CF_ACTION_ENABLE = 1;
    public static final int CF_ACTION_ERASURE = 4;
    public static final int CF_ACTION_REGISTRATION = 3;
    public static final int CF_REASON_ALL = 4;
    public static final int CF_REASON_ALL_CONDITIONAL = 5;
    public static final int CF_REASON_BUSY = 1;
    public static final int CF_REASON_NOT_REACHABLE = 3;
    public static final int CF_REASON_NO_REPLY = 2;
    public static final int CF_REASON_UNCONDITIONAL = 0;
    public static final int CLIR_DEFAULT = 0;
    public static final int CLIR_INVOCATION = 1;
    public static final int CLIR_SUPPRESSION = 2;
    public static final int GSM_SMS_FAIL_CAUSE_MEMORY_CAPACITY_EXCEEDED = 211;
    public static final int GSM_SMS_FAIL_CAUSE_UNSPECIFIED_ERROR = 255;
    public static final int GSM_SMS_FAIL_CAUSE_USIM_APP_TOOLKIT_BUSY = 212;
    public static final int GSM_SMS_FAIL_CAUSE_USIM_DATA_DOWNLOAD_ERROR = 213;
    public static final int SERVICE_CLASS_DATA = 2;
    public static final int SERVICE_CLASS_DATA_ASYNC = 32;
    public static final int SERVICE_CLASS_DATA_SYNC = 16;
    public static final int SERVICE_CLASS_FAX = 4;
    public static final int SERVICE_CLASS_MAX = 128;
    public static final int SERVICE_CLASS_NONE = 0;
    public static final int SERVICE_CLASS_PACKET = 64;
    public static final int SERVICE_CLASS_PAD = 128;
    public static final int SERVICE_CLASS_SMS = 8;
    public static final int SERVICE_CLASS_VOICE = 1;
    public static final int USSD_MODE_LOCAL_CLIENT = 3;
    public static final int USSD_MODE_NOTIFY = 0;
    public static final int USSD_MODE_NOT_SUPPORTED = 4;
    public static final int USSD_MODE_NW_RELEASE = 2;
    public static final int USSD_MODE_NW_TIMEOUT = 5;
    public static final int USSD_MODE_REQUEST = 1;

    void acceptCall(Message message);

    void acknowledgeIncomingGsmSmsWithPdu(boolean z, String str, Message message);

    void acknowledgeLastIncomingCdmaSms(boolean z, int i, Message message);

    void acknowledgeLastIncomingGsmSms(boolean z, int i, Message message);

    void cancelPendingUssd(Message message);

    void changeBarringPassword(String str, String str2, String str3, Message message);

    void changeIccPin(String str, String str2, Message message);

    void changeIccPin2(String str, String str2, Message message);

    void changeIccPin2ForApp(String str, String str2, String str3, Message message);

    void changeIccPinForApp(String str, String str2, String str3, Message message);

    void conference(Message message);

    void deactivateDataCall(int i, int i2, Message message);

    void deleteSmsOnRuim(int i, Message message);

    void deleteSmsOnSim(int i, Message message);

    void dial(String str, int i, Message message);

    void dial(String str, int i, UUSInfo uUSInfo, Message message);

    void exitEmergencyCallbackMode(Message message);

    void explicitCallTransfer(Message message);

    void getAtr(Message message);

    void getAvailableNetworks(Message message);

    void getBandPref(Message message);

    void getBasebandVersion(Message message);

    void getCDMASubscription(Message message);

    void getCLIR(Message message);

    void getCdmaBroadcastConfig(Message message);

    void getCdmaSubscriptionSource(Message message);

    void getCellInfoList(Message message);

    void getCurrentCalls(Message message);

    void getDataCallList(Message message);

    void getDataCallProfile(int i, Message message);

    void getDataRegistrationState(Message message);

    void getDeviceIdentity(Message message);

    void getGsmBroadcastConfig(Message message);

    void getHardwareConfig(Message message);

    void getIMEI(Message message);

    void getIMEISV(Message message);

    void getIMSI(Message message);

    void getIMSIForApp(String str, Message message);

    void getIccCardStatus(Message message);

    void getImsRegistrationState(Message message);

    void getLastCallFailCause(Message message);

    void getLastDataCallFailCause(Message message);

    @Deprecated
    void getLastPdpFailCause(Message message);

    int getLteOnCdmaMode();

    void getModemCapability(Message message);

    void getMute(Message message);

    void getNeighboringCids(Message message);

    void getNetworkSelectionMode(Message message);

    void getOperator(Message message);

    @Deprecated
    void getPDPContextList(Message message);

    void getPreferredNetworkType(Message message);

    void getPreferredNetworkTypeWithOptimizeSetting(Message message);

    void getPreferredVoicePrivacy(Message message);

    RadioState getRadioState();

    int getRilVersion();

    void getSignalStrength(Message message);

    void getSimLock(Message message);

    void getSmscAddress(Message message);

    void getVoiceRadioTechnology(Message message);

    void getVoiceRegistrationState(Message message);

    void handleCallSetupRequestFromSim(boolean z, Message message);

    void hangupConnection(int i, Message message);

    void hangupForegroundResumeBackground(Message message);

    void hangupWaitingOrBackground(Message message);

    void iccCloseChannel(int i, Message message);

    void iccCloseLogicalChannel(int i, Message message);

    void iccExchangeAPDU(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message);

    void iccIO(int i, int i2, String str, int i3, int i4, int i5, String str2, String str3, Message message);

    void iccIOForApp(int i, int i2, String str, int i3, int i4, int i5, String str2, String str3, String str4, Message message);

    void iccOpenChannel(String str, Message message);

    void iccOpenLogicalChannel(String str, Message message);

    void iccTransmitApduBasicChannel(int i, int i2, int i3, int i4, int i5, String str, Message message);

    void iccTransmitApduLogicalChannel(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message);

    void invokeOemRilRequestRaw(byte[] bArr, Message message);

    void invokeOemRilRequestStrings(String[] strArr, Message message);

    boolean isRunning();

    void nvReadItem(int i, Message message);

    void nvResetConfig(int i, Message message);

    void nvWriteCdmaPrl(byte[] bArr, Message message);

    void nvWriteItem(int i, String str, Message message);

    void queryAvailableBandMode(Message message);

    void queryCLIP(Message message);

    void queryCallForwardStatus(int i, int i2, String str, Message message);

    void queryCallWaiting(int i, Message message);

    void queryCdmaRoamingPreference(Message message);

    void queryFacilityLock(String str, String str2, int i, Message message);

    void queryFacilityLockForApp(String str, String str2, int i, String str3, Message message);

    void queryTTYMode(Message message);

    void registerFoT53ClirlInfo(Handler handler, int i, Object obj);

    void registerForAvailable(Handler handler, int i, Object obj);

    void registerForCallStateChanged(Handler handler, int i, Object obj);

    void registerForCallWaitingInfo(Handler handler, int i, Object obj);

    void registerForCdmaOtaProvision(Handler handler, int i, Object obj);

    void registerForCdmaPrlChanged(Handler handler, int i, Object obj);

    void registerForCdmaSubscriptionChanged(Handler handler, int i, Object obj);

    void registerForCellInfoList(Handler handler, int i, Object obj);

    void registerForDataNetworkStateChanged(Handler handler, int i, Object obj);

    void registerForDisplayInfo(Handler handler, int i, Object obj);

    void registerForExitEmergencyCallbackMode(Handler handler, int i, Object obj);

    void registerForHardwareConfigChanged(Handler handler, int i, Object obj);

    void registerForIccRefresh(Handler handler, int i, Object obj);

    void registerForIccStatusChanged(Handler handler, int i, Object obj);

    void registerForImsNetworkStateChanged(Handler handler, int i, Object obj);

    void registerForInCallVoicePrivacyOff(Handler handler, int i, Object obj);

    void registerForInCallVoicePrivacyOn(Handler handler, int i, Object obj);

    void registerForLineControlInfo(Handler handler, int i, Object obj);

    void registerForModemCapEvent(Handler handler, int i, Object obj);

    void registerForNotAvailable(Handler handler, int i, Object obj);

    void registerForNumberInfo(Handler handler, int i, Object obj);

    void registerForOffOrNotAvailable(Handler handler, int i, Object obj);

    void registerForOn(Handler handler, int i, Object obj);

    void registerForRadioStateChanged(Handler handler, int i, Object obj);

    void registerForRedirectedNumberInfo(Handler handler, int i, Object obj);

    void registerForResendIncallMute(Handler handler, int i, Object obj);

    void registerForRilConnected(Handler handler, int i, Object obj);

    void registerForRingbackTone(Handler handler, int i, Object obj);

    void registerForSignalInfo(Handler handler, int i, Object obj);

    void registerForSimRefreshEvent(Handler handler, int i, Object obj);

    void registerForSrvccStateChanged(Handler handler, int i, Object obj);

    void registerForSubscriptionStatusChanged(Handler handler, int i, Object obj);

    void registerForT53AudioControlInfo(Handler handler, int i, Object obj);

    void registerForVoiceNetworkStateChanged(Handler handler, int i, Object obj);

    void registerForVoiceRadioTechChanged(Handler handler, int i, Object obj);

    void registerForWwanIwlanCoexistence(Handler handler, int i, Object obj);

    void rejectCall(Message message);

    void reportDecryptStatus(boolean z, Message message);

    void reportSmsMemoryStatus(boolean z, Message message);

    void reportStkServiceIsRunning(Message message);

    void requestIccSimAuthentication(int i, String str, String str2, Message message);

    void requestIsimAuthentication(String str, Message message);

    void requestShutdown(Message message);

    void resetRadio(Message message);

    void sendBurstDtmf(String str, int i, int i2, Message message);

    void sendCDMAFeatureCode(String str, Message message);

    void sendCdmaSms(byte[] bArr, Message message);

    void sendDtmf(char c, Message message);

    void sendEnvelope(String str, Message message);

    void sendEnvelopeWithStatus(String str, Message message);

    void sendImsCdmaSms(byte[] bArr, int i, int i2, Message message);

    void sendImsGsmSms(String str, String str2, int i, int i2, Message message);

    void sendSMS(String str, String str2, Message message);

    void sendSMSExpectMore(String str, String str2, Message message);

    void sendTerminalResponse(String str, Message message);

    void sendUSSD(String str, Message message);

    void separateConnection(int i, Message message);

    void setBandMode(int i, Message message);

    void setBandPref(long j, int i, Message message);

    void setCLIR(int i, Message message);

    void setCallForward(int i, int i2, int i3, String str, int i4, Message message);

    void setCallWaiting(boolean z, int i, Message message);

    void setCdmaBroadcastActivation(boolean z, Message message);

    void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] cdmaSmsBroadcastConfigInfoArr, Message message);

    void setCdmaRoamingPreference(int i, Message message);

    void setCdmaSubscriptionSource(int i, Message message);

    void setCellInfoListRate(int i, Message message);

    void setDataAllowed(boolean z, Message message);

    void setDataProfile(DataProfile[] dataProfileArr, Message message);

    void setEmergencyCallbackMode(Handler handler, int i, Object obj);

    void setFacilityLock(String str, boolean z, String str2, int i, Message message);

    void setFacilityLockForApp(String str, boolean z, String str2, int i, String str3, Message message);

    void setGsmBroadcastActivation(boolean z, Message message);

    void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] smsBroadcastConfigInfoArr, Message message);

    void setInitialAttachApn(String str, String str2, int i, String str3, String str4, Message message);

    void setLimitationByChameleon(boolean z, Message message);

    void setLocalCallHold(int i);

    void setLocationUpdates(boolean z, Message message);

    void setModemSettingsByChameleon(int i, Message message);

    void setMute(boolean z, Message message);

    void setNetworkSelectionModeAutomatic(Message message);

    void setNetworkSelectionModeManual(String str, Message message);

    void setOnCallRing(Handler handler, int i, Object obj);

    void setOnCatCallSetUp(Handler handler, int i, Object obj);

    void setOnCatCcAlphaNotify(Handler handler, int i, Object obj);

    void setOnCatEvent(Handler handler, int i, Object obj);

    void setOnCatProactiveCmd(Handler handler, int i, Object obj);

    void setOnCatSessionEnd(Handler handler, int i, Object obj);

    void setOnIccRefresh(Handler handler, int i, Object obj);

    void setOnIccSmsFull(Handler handler, int i, Object obj);

    void setOnLteBandInfo(Handler handler, int i, Object obj);

    void setOnNITZTime(Handler handler, int i, Object obj);

    void setOnNewCdmaSms(Handler handler, int i, Object obj);

    void setOnNewGsmBroadcastSms(Handler handler, int i, Object obj);

    void setOnNewGsmSms(Handler handler, int i, Object obj);

    void setOnOemSignalStrengthUpdate(Handler handler, int i, Object obj);

    void setOnRestrictedStateChanged(Handler handler, int i, Object obj);

    void setOnSignalStrengthUpdate(Handler handler, int i, Object obj);

    void setOnSmsOnSim(Handler handler, int i, Object obj);

    void setOnSmsStatus(Handler handler, int i, Object obj);

    void setOnSpeechCodec(Handler handler, int i, Object obj);

    void setOnSs(Handler handler, int i, Object obj);

    void setOnSuppServiceNotification(Handler handler, int i, Object obj);

    void setOnUSSD(Handler handler, int i, Object obj);

    void setOnUnsolOemHookRaw(Handler handler, int i, Object obj);

    void setPhoneType(int i);

    void setPreferredNetworkType(int i, Message message);

    void setPreferredVoicePrivacy(boolean z, Message message);

    void setRadioPower(boolean z, Message message);

    void setRatModeOptimizeSetting(boolean z, Message message);

    void setSmscAddress(String str, Message message);

    void setSuppServiceNotifications(boolean z, Message message);

    void setTTYMode(int i, Message message);

    void setUiccSubscription(int i, int i2, int i3, int i4, Message message);

    void setupDataCall(String str, String str2, String str3, String str4, String str5, String str6, String str7, Message message);

    void startDtmf(char c, Message message);

    void stopDtmf(Message message);

    void supplyDepersonalization(String str, String str2, Message message);

    void supplyIccPin(String str, Message message);

    void supplyIccPin2(String str, Message message);

    void supplyIccPin2ForApp(String str, String str2, Message message);

    void supplyIccPinForApp(String str, String str2, Message message);

    void supplyIccPuk(String str, String str2, Message message);

    void supplyIccPuk2(String str, String str2, Message message);

    void supplyIccPuk2ForApp(String str, String str2, String str3, Message message);

    void supplyIccPukForApp(String str, String str2, String str3, Message message);

    void switchWaitingOrHoldingAndActive(Message message);

    void testingEmergencyCall();

    void unSetOnCallRing(Handler handler);

    void unSetOnCatCallSetUp(Handler handler);

    void unSetOnCatCcAlphaNotify(Handler handler);

    void unSetOnCatEvent(Handler handler);

    void unSetOnCatProactiveCmd(Handler handler);

    void unSetOnCatSessionEnd(Handler handler);

    void unSetOnIccSmsFull(Handler handler);

    void unSetOnLteBandInfo(Handler handler);

    void unSetOnNITZTime(Handler handler);

    void unSetOnNewCdmaSms(Handler handler);

    void unSetOnNewGsmBroadcastSms(Handler handler);

    void unSetOnNewGsmSms(Handler handler);

    void unSetOnOemSignalStrengthUpdate(Handler handler);

    void unSetOnRestrictedStateChanged(Handler handler);

    void unSetOnSignalStrengthUpdate(Handler handler);

    void unSetOnSmsOnSim(Handler handler);

    void unSetOnSmsStatus(Handler handler);

    void unSetOnSpeechCodec(Handler handler);

    void unSetOnSs(Handler handler);

    void unSetOnSuppServiceNotification(Handler handler);

    void unSetOnUSSD(Handler handler);

    void unSetOnUnsolOemHookRaw(Handler handler);

    void unregisterForAvailable(Handler handler);

    void unregisterForCallStateChanged(Handler handler);

    void unregisterForCallWaitingInfo(Handler handler);

    void unregisterForCdmaOtaProvision(Handler handler);

    void unregisterForCdmaPrlChanged(Handler handler);

    void unregisterForCdmaSubscriptionChanged(Handler handler);

    void unregisterForCellInfoList(Handler handler);

    void unregisterForDataNetworkStateChanged(Handler handler);

    void unregisterForDisplayInfo(Handler handler);

    void unregisterForExitEmergencyCallbackMode(Handler handler);

    void unregisterForHardwareConfigChanged(Handler handler);

    void unregisterForIccRefresh(Handler handler);

    void unregisterForIccStatusChanged(Handler handler);

    void unregisterForImsNetworkStateChanged(Handler handler);

    void unregisterForInCallVoicePrivacyOff(Handler handler);

    void unregisterForInCallVoicePrivacyOn(Handler handler);

    void unregisterForLineControlInfo(Handler handler);

    void unregisterForModemCapEvent(Handler handler);

    void unregisterForNotAvailable(Handler handler);

    void unregisterForNumberInfo(Handler handler);

    void unregisterForOffOrNotAvailable(Handler handler);

    void unregisterForOn(Handler handler);

    void unregisterForRadioStateChanged(Handler handler);

    void unregisterForRedirectedNumberInfo(Handler handler);

    void unregisterForResendIncallMute(Handler handler);

    void unregisterForRilConnected(Handler handler);

    void unregisterForRingbackTone(Handler handler);

    void unregisterForSignalInfo(Handler handler);

    void unregisterForSimRefreshEvent(Handler handler);

    void unregisterForSrvccStateChanged(Handler handler);

    void unregisterForSubscriptionStatusChanged(Handler handler);

    void unregisterForT53AudioControlInfo(Handler handler);

    void unregisterForT53ClirInfo(Handler handler);

    void unregisterForVoiceNetworkStateChanged(Handler handler);

    void unregisterForVoiceRadioTechChanged(Handler handler);

    void unregisterForWwanIwlanCoexistence(Handler handler);

    void unsetOnIccRefresh(Handler handler);

    void updateOemDataSettings(boolean z, boolean z2, boolean z3, Message message);

    void updateStackBinding(int i, int i2, Message message);

    void writeSmsToRuim(int i, String str, Message message);

    void writeSmsToSim(int i, String str, String str2, Message message);

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum RadioState {
        RADIO_OFF,
        RADIO_UNAVAILABLE,
        RADIO_ON;

        public boolean isOn() {
            return this == RADIO_ON;
        }

        public boolean isAvailable() {
            return this != RADIO_UNAVAILABLE;
        }
    }
}
