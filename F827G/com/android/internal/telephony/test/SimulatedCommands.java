package com.android.internal.telephony.test;

import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.BaseCommands;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import java.util.ArrayList;

public final class SimulatedCommands extends BaseCommands implements CommandsInterface, SimulatedRadioControl {
    private static final String DEFAULT_SIM_PIN2_CODE = "5678";
    private static final String DEFAULT_SIM_PIN_CODE = "1234";
    private static final SimFdnState INITIAL_FDN_STATE = SimFdnState.NONE;
    private static final SimLockState INITIAL_LOCK_STATE = SimLockState.NONE;
    private static final String LOG_TAG = "SimulatedCommands";
    private static final String SIM_PUK2_CODE = "87654321";
    private static final String SIM_PUK_CODE = "12345678";
    HandlerThread mHandlerThread = new HandlerThread(LOG_TAG);
    int mNetworkType;
    int mNextCallFailCause = 16;
    int mPausedResponseCount;
    ArrayList<Message> mPausedResponses = new ArrayList();
    String mPin2Code;
    int mPin2UnlockAttempts;
    String mPinCode;
    int mPinUnlockAttempts;
    int mPuk2UnlockAttempts;
    int mPukUnlockAttempts;
    boolean mRatModeOptimizeSetting;
    boolean mSimFdnEnabled;
    SimFdnState mSimFdnEnabledState;
    boolean mSimLockEnabled;
    SimLockState mSimLockedState;
    boolean mSsnNotifyOn = false;
    SimulatedGsmCallState simulatedCallState;

    private enum SimFdnState {
        NONE,
        REQUIRE_PIN2,
        REQUIRE_PUK2,
        SIM_PERM_LOCKED
    }

    private enum SimLockState {
        NONE,
        REQUIRE_PIN,
        REQUIRE_PUK,
        SIM_PERM_LOCKED
    }

    public SimulatedCommands() {
        boolean z = true;
        super(null);
        this.mHandlerThread.start();
        this.simulatedCallState = new SimulatedGsmCallState(this.mHandlerThread.getLooper());
        setRadioState(RadioState.RADIO_OFF);
        this.mSimLockedState = INITIAL_LOCK_STATE;
        this.mSimLockEnabled = this.mSimLockedState != SimLockState.NONE;
        this.mPinCode = DEFAULT_SIM_PIN_CODE;
        this.mSimFdnEnabledState = INITIAL_FDN_STATE;
        if (this.mSimFdnEnabledState == SimFdnState.NONE) {
            z = false;
        }
        this.mSimFdnEnabled = z;
        this.mPin2Code = DEFAULT_SIM_PIN2_CODE;
    }

    private boolean isSimLocked() {
        return this.mSimLockedState != SimLockState.NONE;
    }

    private void resultFail(Message message, Throwable th) {
        if (message != null) {
            AsyncResult.forMessage(message).exception = th;
            if (this.mPausedResponseCount > 0) {
                this.mPausedResponses.add(message);
            } else {
                message.sendToTarget();
            }
        }
    }

    private void resultSuccess(Message message, Object obj) {
        if (message != null) {
            AsyncResult.forMessage(message).result = obj;
            if (this.mPausedResponseCount > 0) {
                this.mPausedResponses.add(message);
            } else {
                message.sendToTarget();
            }
        }
    }

    private void unimplemented(Message message) {
        if (message != null) {
            AsyncResult.forMessage(message).exception = new RuntimeException("Unimplemented");
            if (this.mPausedResponseCount > 0) {
                this.mPausedResponses.add(message);
            } else {
                message.sendToTarget();
            }
        }
    }

    public void acceptCall(Message message) {
        if (this.simulatedCallState.onAnswer()) {
            resultSuccess(message, null);
        } else {
            resultFail(message, new RuntimeException("Hangup Error"));
        }
    }

    public void acknowledgeIncomingGsmSmsWithPdu(boolean z, String str, Message message) {
        unimplemented(message);
    }

    public void acknowledgeLastIncomingCdmaSms(boolean z, int i, Message message) {
        unimplemented(message);
    }

    public void acknowledgeLastIncomingGsmSms(boolean z, int i, Message message) {
        unimplemented(message);
    }

    public void cancelPendingUssd(Message message) {
        resultSuccess(message, null);
    }

    public void changeBarringPassword(String str, String str2, String str3, Message message) {
        unimplemented(message);
    }

    public void changeIccPin(String str, String str2, Message message) {
        if (str != null && str.equals(this.mPinCode)) {
            this.mPinCode = str2;
            if (message != null) {
                AsyncResult.forMessage(message, null, null);
                message.sendToTarget();
            }
        } else if (message != null) {
            Rlog.i(LOG_TAG, "[SimCmd] changeIccPin: pin failed!");
            AsyncResult.forMessage(message, null, new CommandException(Error.PASSWORD_INCORRECT));
            message.sendToTarget();
        }
    }

    public void changeIccPin2(String str, String str2, Message message) {
        if (str != null && str.equals(this.mPin2Code)) {
            this.mPin2Code = str2;
            if (message != null) {
                AsyncResult.forMessage(message, null, null);
                message.sendToTarget();
            }
        } else if (message != null) {
            Rlog.i(LOG_TAG, "[SimCmd] changeIccPin2: pin2 failed!");
            AsyncResult.forMessage(message, null, new CommandException(Error.PASSWORD_INCORRECT));
            message.sendToTarget();
        }
    }

    public void changeIccPin2ForApp(String str, String str2, String str3, Message message) {
        unimplemented(message);
    }

    public void changeIccPinForApp(String str, String str2, String str3, Message message) {
        unimplemented(message);
    }

    public void conference(Message message) {
        if (this.simulatedCallState.onChld('3', 0)) {
            resultSuccess(message, null);
        } else {
            resultFail(message, new RuntimeException("Hangup Error"));
        }
    }

    public void deactivateDataCall(int i, int i2, Message message) {
        unimplemented(message);
    }

    public void deleteSmsOnRuim(int i, Message message) {
        Rlog.d(LOG_TAG, "Delete RUIM message at index " + i);
        unimplemented(message);
    }

    public void deleteSmsOnSim(int i, Message message) {
        Rlog.d(LOG_TAG, "Delete message at index " + i);
        unimplemented(message);
    }

    public void dial(String str, int i, Message message) {
        this.simulatedCallState.onDial(str);
        resultSuccess(message, null);
    }

    public void dial(String str, int i, UUSInfo uUSInfo, Message message) {
        this.simulatedCallState.onDial(str);
        resultSuccess(message, null);
    }

    public void exitEmergencyCallbackMode(Message message) {
        unimplemented(message);
    }

    public void explicitCallTransfer(Message message) {
        if (this.simulatedCallState.onChld('4', 0)) {
            resultSuccess(message, null);
        } else {
            resultFail(message, new RuntimeException("Hangup Error"));
        }
    }

    public void forceDataDormancy(Message message) {
        unimplemented(message);
    }

    public void getAtr(Message message) {
        unimplemented(message);
    }

    public void getAvailableNetworks(Message message) {
        unimplemented(message);
    }

    public void getBasebandVersion(Message message) {
        resultSuccess(message, LOG_TAG);
    }

    public void getCDMASubscription(Message message) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(message);
    }

    public void getCLIR(Message message) {
        unimplemented(message);
    }

    public void getCdmaBroadcastConfig(Message message) {
        unimplemented(message);
    }

    public void getCdmaSubscriptionSource(Message message) {
        unimplemented(message);
    }

    public void getCellInfoList(Message message) {
        unimplemented(message);
    }

    public void getCurrentCalls(Message message) {
        if (this.mState != RadioState.RADIO_ON || isSimLocked()) {
            resultFail(message, new CommandException(Error.RADIO_NOT_AVAILABLE));
        } else {
            resultSuccess(message, this.simulatedCallState.getDriverCalls());
        }
    }

    public void getDataCallList(Message message) {
        resultSuccess(message, new ArrayList(0));
    }

    public void getDataRegistrationState(Message message) {
        resultSuccess(message, new String[]{"5", null, null, "2"});
    }

    public void getDeviceIdentity(Message message) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(message);
    }

    public void getGsmBroadcastConfig(Message message) {
        unimplemented(message);
    }

    public void getHardwareConfig(Message message) {
        unimplemented(message);
    }

    public void getIMEI(Message message) {
        resultSuccess(message, "012345678901234");
    }

    public void getIMEISV(Message message) {
        resultSuccess(message, "99");
    }

    public void getIMSI(Message message) {
        getIMSIForApp(null, message);
    }

    public void getIMSIForApp(String str, Message message) {
        resultSuccess(message, "012345678901234");
    }

    public void getIccCardStatus(Message message) {
        unimplemented(message);
    }

    public void getImsRegistrationState(Message message) {
        unimplemented(message);
    }

    public void getLastCallFailCause(Message message) {
        resultSuccess(message, new int[]{this.mNextCallFailCause});
    }

    public void getLastDataCallFailCause(Message message) {
        unimplemented(message);
    }

    @Deprecated
    public void getLastPdpFailCause(Message message) {
        unimplemented(message);
    }

    public void getMute(Message message) {
        unimplemented(message);
    }

    public void getNeighboringCids(Message message) {
        int[] iArr = new int[7];
        iArr[0] = 6;
        for (int i = 1; i < 7; i++) {
            iArr[i] = i;
        }
        resultSuccess(message, iArr);
    }

    public void getNetworkSelectionMode(Message message) {
        resultSuccess(message, new int[]{0});
    }

    public void getOperator(Message message) {
        resultSuccess(message, new String[]{"El Telco Loco", "Telco Loco", "001001"});
    }

    @Deprecated
    public void getPDPContextList(Message message) {
        getDataCallList(message);
    }

    public void getPreferredNetworkType(Message message) {
        resultSuccess(message, new int[]{this.mNetworkType});
    }

    public void getPreferredNetworkTypeWithOptimizeSetting(Message message) {
        byte b = (byte) this.mNetworkType;
        int i = this.mRatModeOptimizeSetting ? 1 : 0;
        resultSuccess(message, new byte[]{b, (byte) i});
    }

    public void getPreferredVoicePrivacy(Message message) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(message);
    }

    public void getSignalStrength(Message message) {
        resultSuccess(message, new int[]{23, 0});
    }

    public void getSmscAddress(Message message) {
        unimplemented(message);
    }

    public void getVoiceRadioTechnology(Message message) {
        unimplemented(message);
    }

    public void getVoiceRegistrationState(Message message) {
        resultSuccess(message, new String[]{"5", null, null, null, null, null, null, null, null, null, null, null, null, null});
    }

    public void handleCallSetupRequestFromSim(boolean z, Message message) {
        resultSuccess(message, null);
    }

    public void hangupConnection(int i, Message message) {
        if (this.simulatedCallState.onChld('1', (char) (i + 48))) {
            Rlog.i("GSM", "[SimCmd] hangupConnection: resultSuccess");
            resultSuccess(message, null);
            return;
        }
        Rlog.i("GSM", "[SimCmd] hangupConnection: resultFail");
        resultFail(message, new RuntimeException("Hangup Error"));
    }

    public void hangupForegroundResumeBackground(Message message) {
        if (this.simulatedCallState.onChld('1', 0)) {
            resultSuccess(message, null);
        } else {
            resultFail(message, new RuntimeException("Hangup Error"));
        }
    }

    public void hangupWaitingOrBackground(Message message) {
        if (this.simulatedCallState.onChld('0', 0)) {
            resultSuccess(message, null);
        } else {
            resultFail(message, new RuntimeException("Hangup Error"));
        }
    }

    public void iccCloseLogicalChannel(int i, Message message) {
        unimplemented(message);
    }

    public void iccIO(int i, int i2, String str, int i3, int i4, int i5, String str2, String str3, Message message) {
        iccIOForApp(i, i2, str, i3, i4, i5, str2, str3, null, message);
    }

    public void iccIOForApp(int i, int i2, String str, int i3, int i4, int i5, String str2, String str3, String str4, Message message) {
        unimplemented(message);
    }

    public void iccOpenLogicalChannel(String str, Message message) {
        unimplemented(message);
    }

    public void iccTransmitApduBasicChannel(int i, int i2, int i3, int i4, int i5, String str, Message message) {
        unimplemented(message);
    }

    public void iccTransmitApduLogicalChannel(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message) {
        unimplemented(message);
    }

    public void invokeOemRilRequestRaw(byte[] bArr, Message message) {
        if (message != null) {
            AsyncResult.forMessage(message).result = bArr;
            message.sendToTarget();
        }
    }

    public void invokeOemRilRequestStrings(String[] strArr, Message message) {
        if (message != null) {
            AsyncResult.forMessage(message).result = strArr;
            message.sendToTarget();
        }
    }

    public void nvReadItem(int i, Message message) {
        unimplemented(message);
    }

    public void nvResetConfig(int i, Message message) {
        unimplemented(message);
    }

    public void nvWriteCdmaPrl(byte[] bArr, Message message) {
        unimplemented(message);
    }

    public void nvWriteItem(int i, String str, Message message) {
        unimplemented(message);
    }

    public void pauseResponses() {
        this.mPausedResponseCount++;
    }

    public void progressConnectingCallState() {
        this.simulatedCallState.progressConnectingCallState();
        this.mCallStateRegistrants.notifyRegistrants();
    }

    public void progressConnectingToActive() {
        this.simulatedCallState.progressConnectingToActive();
        this.mCallStateRegistrants.notifyRegistrants();
    }

    public void queryAvailableBandMode(Message message) {
        resultSuccess(message, new int[]{4, 2, 3, 4});
    }

    public void queryCLIP(Message message) {
        unimplemented(message);
    }

    public void queryCallForwardStatus(int i, int i2, String str, Message message) {
        unimplemented(message);
    }

    public void queryCallWaiting(int i, Message message) {
        unimplemented(message);
    }

    public void queryCdmaRoamingPreference(Message message) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(message);
    }

    public void queryFacilityLock(String str, String str2, int i, Message message) {
        queryFacilityLockForApp(str, str2, i, null, message);
    }

    public void queryFacilityLockForApp(String str, String str2, int i, String str3, Message message) {
        int i2 = 1;
        int[] iArr;
        if (str == null || !str.equals(CommandsInterface.CB_FACILITY_BA_SIM)) {
            if (str == null || !str.equals(CommandsInterface.CB_FACILITY_BA_FD)) {
                unimplemented(message);
            } else if (message != null) {
                iArr = new int[1];
                if (!this.mSimFdnEnabled) {
                    i2 = 0;
                }
                iArr[0] = i2;
                Rlog.i(LOG_TAG, "[SimCmd] queryFacilityLock: FDN is " + (iArr[0] == 0 ? "disabled" : "enabled"));
                AsyncResult.forMessage(message, iArr, null);
                message.sendToTarget();
            }
        } else if (message != null) {
            iArr = new int[1];
            if (!this.mSimLockEnabled) {
                i2 = 0;
            }
            iArr[0] = i2;
            Rlog.i(LOG_TAG, "[SimCmd] queryFacilityLock: SIM is " + (iArr[0] == 0 ? "unlocked" : "locked"));
            AsyncResult.forMessage(message, iArr, null);
            message.sendToTarget();
        }
    }

    public void queryTTYMode(Message message) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(message);
    }

    public void rejectCall(Message message) {
        if (this.simulatedCallState.onChld('0', 0)) {
            resultSuccess(message, null);
        } else {
            resultFail(message, new RuntimeException("Hangup Error"));
        }
    }

    public void reportSmsMemoryStatus(boolean z, Message message) {
        unimplemented(message);
    }

    public void reportStkServiceIsRunning(Message message) {
        resultSuccess(message, null);
    }

    public void requestIccSimAuthentication(int i, String str, String str2, Message message) {
        unimplemented(message);
    }

    public void requestIsimAuthentication(String str, Message message) {
        unimplemented(message);
    }

    public void requestShutdown(Message message) {
        setRadioState(RadioState.RADIO_UNAVAILABLE);
    }

    public void resetRadio(Message message) {
        unimplemented(message);
    }

    public void resumeResponses() {
        this.mPausedResponseCount--;
        if (this.mPausedResponseCount == 0) {
            int size = this.mPausedResponses.size();
            for (int i = 0; i < size; i++) {
                ((Message) this.mPausedResponses.get(i)).sendToTarget();
            }
            this.mPausedResponses.clear();
            return;
        }
        Rlog.e("GSM", "SimulatedCommands.resumeResponses < 0");
    }

    public void sendBurstDtmf(String str, int i, int i2, Message message) {
        resultSuccess(message, null);
    }

    public void sendCDMAFeatureCode(String str, Message message) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(message);
    }

    public void sendCdmaSms(byte[] bArr, Message message) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
    }

    public void sendDtmf(char c, Message message) {
        resultSuccess(message, null);
    }

    public void sendEnvelope(String str, Message message) {
        resultSuccess(message, null);
    }

    public void sendEnvelopeWithStatus(String str, Message message) {
        resultSuccess(message, null);
    }

    public void sendImsCdmaSms(byte[] bArr, int i, int i2, Message message) {
        unimplemented(message);
    }

    public void sendImsGsmSms(String str, String str2, int i, int i2, Message message) {
        unimplemented(message);
    }

    public void sendSMS(String str, String str2, Message message) {
        unimplemented(message);
    }

    public void sendSMSExpectMore(String str, String str2, Message message) {
        unimplemented(message);
    }

    public void sendStkCcAplha(String str) {
        triggerIncomingStkCcAlpha(str);
    }

    public void sendTerminalResponse(String str, Message message) {
        resultSuccess(message, null);
    }

    public void sendUSSD(String str, Message message) {
        if (str.equals("#646#")) {
            resultSuccess(message, null);
            triggerIncomingUssd("0", "You have NNN minutes remaining.");
            return;
        }
        resultSuccess(message, null);
        triggerIncomingUssd("0", "All Done");
    }

    public void separateConnection(int i, Message message) {
        if (this.simulatedCallState.onChld('2', (char) (i + 48))) {
            resultSuccess(message, null);
        } else {
            resultFail(message, new RuntimeException("Hangup Error"));
        }
    }

    public void setAutoProgressConnectingCall(boolean z) {
        this.simulatedCallState.setAutoProgressConnectingCall(z);
    }

    public void setBandMode(int i, Message message) {
        resultSuccess(message, null);
    }

    public void setCLIR(int i, Message message) {
        unimplemented(message);
    }

    public void setCallForward(int i, int i2, int i3, String str, int i4, Message message) {
        unimplemented(message);
    }

    public void setCallWaiting(boolean z, int i, Message message) {
        unimplemented(message);
    }

    public void setCdmaBroadcastActivation(boolean z, Message message) {
        unimplemented(message);
    }

    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] cdmaSmsBroadcastConfigInfoArr, Message message) {
        unimplemented(message);
    }

    public void setCdmaRoamingPreference(int i, Message message) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(message);
    }

    public void setCdmaSubscriptionSource(int i, Message message) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(message);
    }

    public void setCellInfoListRate(int i, Message message) {
        unimplemented(message);
    }

    public void setDataProfile(DataProfile[] dataProfileArr, Message message) {
    }

    public void setFacilityLock(String str, boolean z, String str2, int i, Message message) {
        setFacilityLockForApp(str, z, str2, i, null, message);
    }

    public void setFacilityLockForApp(String str, boolean z, String str2, int i, String str3, Message message) {
        if (str == null || !str.equals(CommandsInterface.CB_FACILITY_BA_SIM)) {
            if (str == null || !str.equals(CommandsInterface.CB_FACILITY_BA_FD)) {
                unimplemented(message);
            } else if (str2 != null && str2.equals(this.mPin2Code)) {
                Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin2 is valid");
                this.mSimFdnEnabled = z;
                if (message != null) {
                    AsyncResult.forMessage(message, null, null);
                    message.sendToTarget();
                }
            } else if (message != null) {
                Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin2 failed!");
                AsyncResult.forMessage(message, null, new CommandException(Error.GENERIC_FAILURE));
                message.sendToTarget();
            }
        } else if (str2 != null && str2.equals(this.mPinCode)) {
            Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin is valid");
            this.mSimLockEnabled = z;
            if (message != null) {
                AsyncResult.forMessage(message, null, null);
                message.sendToTarget();
            }
        } else if (message != null) {
            Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin failed!");
            AsyncResult.forMessage(message, null, new CommandException(Error.GENERIC_FAILURE));
            message.sendToTarget();
        }
    }

    public void setGsmBroadcastActivation(boolean z, Message message) {
        unimplemented(message);
    }

    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] smsBroadcastConfigInfoArr, Message message) {
        unimplemented(message);
    }

    public void setInitialAttachApn(String str, String str2, int i, String str3, String str4, Message message) {
    }

    public void setLocationUpdates(boolean z, Message message) {
        unimplemented(message);
    }

    public void setMute(boolean z, Message message) {
        unimplemented(message);
    }

    public void setNetworkSelectionModeAutomatic(Message message) {
        unimplemented(message);
    }

    public void setNetworkSelectionModeManual(String str, Message message) {
        unimplemented(message);
    }

    public void setNextCallFailCause(int i) {
        this.mNextCallFailCause = i;
    }

    public void setNextDialFailImmediately(boolean z) {
        this.simulatedCallState.setNextDialFailImmediately(z);
    }

    public void setPhoneType(int i) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
    }

    public void setPreferredNetworkType(int i, Message message) {
        this.mNetworkType = i;
        resultSuccess(message, null);
    }

    public void setPreferredVoicePrivacy(boolean z, Message message) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(message);
    }

    public void setRadioPower(boolean z, Message message) {
        if (z) {
            setRadioState(RadioState.RADIO_ON);
        } else {
            setRadioState(RadioState.RADIO_OFF);
        }
    }

    public void setRatModeOptimizeSetting(boolean z, Message message) {
        this.mRatModeOptimizeSetting = z;
        resultSuccess(message, null);
    }

    public void setSmscAddress(String str, Message message) {
        unimplemented(message);
    }

    public void setSuppServiceNotifications(boolean z, Message message) {
        resultSuccess(message, null);
        if (z && this.mSsnNotifyOn) {
            Rlog.w(LOG_TAG, "Supp Service Notifications already enabled!");
        }
        this.mSsnNotifyOn = z;
    }

    public void setTTYMode(int i, Message message) {
        Rlog.w(LOG_TAG, "Not implemented in SimulatedCommands");
        unimplemented(message);
    }

    public void setupDataCall(String str, String str2, String str3, String str4, String str5, String str6, String str7, Message message) {
        unimplemented(message);
    }

    public void shutdown() {
        setRadioState(RadioState.RADIO_UNAVAILABLE);
        Looper looper = this.mHandlerThread.getLooper();
        if (looper != null) {
            looper.quit();
        }
    }

    public void startDtmf(char c, Message message) {
        resultSuccess(message, null);
    }

    public void stopDtmf(Message message) {
        resultSuccess(message, null);
    }

    public void supplyDepersonalization(String str, String str2, Message message) {
        unimplemented(message);
    }

    public void supplyIccPin(String str, Message message) {
        if (this.mSimLockedState != SimLockState.REQUIRE_PIN) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: wrong state, state=" + this.mSimLockedState);
            AsyncResult.forMessage(message, null, new CommandException(Error.PASSWORD_INCORRECT));
            message.sendToTarget();
        } else if (str != null && str.equals(this.mPinCode)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: success!");
            this.mPinUnlockAttempts = 0;
            this.mSimLockedState = SimLockState.NONE;
            this.mIccStatusChangedRegistrants.notifyRegistrants();
            if (message != null) {
                AsyncResult.forMessage(message, null, null);
                message.sendToTarget();
            }
        } else if (message != null) {
            this.mPinUnlockAttempts++;
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: failed! attempt=" + this.mPinUnlockAttempts);
            if (this.mPinUnlockAttempts >= 3) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: set state to REQUIRE_PUK");
                this.mSimLockedState = SimLockState.REQUIRE_PUK;
            }
            AsyncResult.forMessage(message, null, new CommandException(Error.PASSWORD_INCORRECT));
            message.sendToTarget();
        }
    }

    public void supplyIccPin2(String str, Message message) {
        if (this.mSimFdnEnabledState != SimFdnState.REQUIRE_PIN2) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: wrong state, state=" + this.mSimFdnEnabledState);
            AsyncResult.forMessage(message, null, new CommandException(Error.PASSWORD_INCORRECT));
            message.sendToTarget();
        } else if (str != null && str.equals(this.mPin2Code)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: success!");
            this.mPin2UnlockAttempts = 0;
            this.mSimFdnEnabledState = SimFdnState.NONE;
            if (message != null) {
                AsyncResult.forMessage(message, null, null);
                message.sendToTarget();
            }
        } else if (message != null) {
            this.mPin2UnlockAttempts++;
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: failed! attempt=" + this.mPin2UnlockAttempts);
            if (this.mPin2UnlockAttempts >= 3) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: set state to REQUIRE_PUK2");
                this.mSimFdnEnabledState = SimFdnState.REQUIRE_PUK2;
            }
            AsyncResult.forMessage(message, null, new CommandException(Error.PASSWORD_INCORRECT));
            message.sendToTarget();
        }
    }

    public void supplyIccPin2ForApp(String str, String str2, Message message) {
        unimplemented(message);
    }

    public void supplyIccPinForApp(String str, String str2, Message message) {
        unimplemented(message);
    }

    public void supplyIccPuk(String str, String str2, Message message) {
        if (this.mSimLockedState != SimLockState.REQUIRE_PUK) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: wrong state, state=" + this.mSimLockedState);
            AsyncResult.forMessage(message, null, new CommandException(Error.PASSWORD_INCORRECT));
            message.sendToTarget();
        } else if (str != null && str.equals(SIM_PUK_CODE)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: success!");
            this.mSimLockedState = SimLockState.NONE;
            this.mPukUnlockAttempts = 0;
            this.mIccStatusChangedRegistrants.notifyRegistrants();
            if (message != null) {
                AsyncResult.forMessage(message, null, null);
                message.sendToTarget();
            }
        } else if (message != null) {
            this.mPukUnlockAttempts++;
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: failed! attempt=" + this.mPukUnlockAttempts);
            if (this.mPukUnlockAttempts >= 10) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: set state to SIM_PERM_LOCKED");
                this.mSimLockedState = SimLockState.SIM_PERM_LOCKED;
            }
            AsyncResult.forMessage(message, null, new CommandException(Error.PASSWORD_INCORRECT));
            message.sendToTarget();
        }
    }

    public void supplyIccPuk2(String str, String str2, Message message) {
        if (this.mSimFdnEnabledState != SimFdnState.REQUIRE_PUK2) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: wrong state, state=" + this.mSimLockedState);
            AsyncResult.forMessage(message, null, new CommandException(Error.PASSWORD_INCORRECT));
            message.sendToTarget();
        } else if (str != null && str.equals(SIM_PUK2_CODE)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: success!");
            this.mSimFdnEnabledState = SimFdnState.NONE;
            this.mPuk2UnlockAttempts = 0;
            if (message != null) {
                AsyncResult.forMessage(message, null, null);
                message.sendToTarget();
            }
        } else if (message != null) {
            this.mPuk2UnlockAttempts++;
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: failed! attempt=" + this.mPuk2UnlockAttempts);
            if (this.mPuk2UnlockAttempts >= 10) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: set state to SIM_PERM_LOCKED");
                this.mSimFdnEnabledState = SimFdnState.SIM_PERM_LOCKED;
            }
            AsyncResult.forMessage(message, null, new CommandException(Error.PASSWORD_INCORRECT));
            message.sendToTarget();
        }
    }

    public void supplyIccPuk2ForApp(String str, String str2, String str3, Message message) {
        unimplemented(message);
    }

    public void supplyIccPukForApp(String str, String str2, String str3, Message message) {
        unimplemented(message);
    }

    public void switchWaitingOrHoldingAndActive(Message message) {
        if (this.simulatedCallState.onChld('2', 0)) {
            resultSuccess(message, null);
        } else {
            resultFail(message, new RuntimeException("Hangup Error"));
        }
    }

    public void triggerHangupAll() {
        this.simulatedCallState.triggerHangupAll();
        this.mCallStateRegistrants.notifyRegistrants();
    }

    public void triggerHangupBackground() {
        this.simulatedCallState.triggerHangupBackground();
        this.mCallStateRegistrants.notifyRegistrants();
    }

    public void triggerHangupForeground() {
        this.simulatedCallState.triggerHangupForeground();
        this.mCallStateRegistrants.notifyRegistrants();
    }

    public void triggerIncomingSMS(String str) {
    }

    public void triggerIncomingStkCcAlpha(String str) {
        if (this.mCatCcAlphaRegistrant != null) {
            this.mCatCcAlphaRegistrant.notifyResult(str);
        }
    }

    public void triggerIncomingUssd(String str, String str2) {
        if (this.mUSSDRegistrant != null) {
            this.mUSSDRegistrant.notifyResult(new String[]{str, str2});
        }
    }

    public void triggerRing(String str) {
        this.simulatedCallState.triggerRing(str);
        this.mCallStateRegistrants.notifyRegistrants();
    }

    public void triggerSsn(int i, int i2) {
        SuppServiceNotification suppServiceNotification = new SuppServiceNotification();
        suppServiceNotification.notificationType = i;
        suppServiceNotification.code = i2;
        this.mSsnRegistrant.notifyRegistrant(new AsyncResult(null, suppServiceNotification, null));
    }

    public void writeSmsToRuim(int i, String str, Message message) {
        Rlog.d(LOG_TAG, "Write SMS to RUIM with status " + i);
        unimplemented(message);
    }

    public void writeSmsToSim(int i, String str, String str2, Message message) {
        Rlog.d(LOG_TAG, "Write SMS to SIM with status " + i);
        unimplemented(message);
    }
}
