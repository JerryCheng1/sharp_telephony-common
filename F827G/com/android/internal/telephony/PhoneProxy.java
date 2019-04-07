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
import com.android.internal.telephony.Phone.DataActivityState;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneConstants.State;
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
    private int mPhoneId = 0;
    private PhoneSubInfoProxy mPhoneSubInfoProxy;
    private boolean mResetModemOnRadioTechnologyChange = false;
    private int mRilVersion;

    public PhoneProxy(PhoneBase phoneBase) {
        this.mActivePhone = phoneBase;
        this.mResetModemOnRadioTechnologyChange = SystemProperties.getBoolean("persist.radio.reset_on_switch", false);
        this.mIccPhoneBookInterfaceManagerProxy = new IccPhoneBookInterfaceManagerProxy(phoneBase.getIccPhoneBookInterfaceManager());
        this.mPhoneSubInfoProxy = new PhoneSubInfoProxy(phoneBase.getPhoneSubInfo());
        this.mCommandsInterface = ((PhoneBase) this.mActivePhone).mCi;
        this.mCommandsInterface.registerForRilConnected(this, 4, null);
        this.mCommandsInterface.registerForOn(this, 2, null);
        this.mCommandsInterface.registerForVoiceRadioTechChanged(this, 1, null);
        this.mPhoneId = phoneBase.getPhoneId();
        this.mIccSmsInterfaceManager = new IccSmsInterfaceManager((PhoneBase) this.mActivePhone);
        this.mIccCardProxy = new IccCardProxy(this.mActivePhone.getContext(), this.mCommandsInterface, this.mActivePhone.getPhoneId());
        if (phoneBase.getPhoneType() == 1) {
            this.mIccCardProxy.setVoiceRadioTech(3);
        } else if (phoneBase.getPhoneType() == 2) {
            this.mIccCardProxy.setVoiceRadioTech(6);
        }
    }

    private void deleteAndCreatePhone(int i) {
        String str = "Unknown";
        Phone phone = this.mActivePhone;
        if (phone != null) {
            str = ((PhoneBase) phone).getPhoneName();
            phone.unregisterForSimRecordsLoaded(this);
        }
        logd("Switching Voice Phone : " + str + " >>> " + (ServiceState.isGsm(i) ? "GSM" : "CDMA"));
        if (ServiceState.isCdma(i)) {
            this.mActivePhone = PhoneFactory.getCdmaPhone(this.mPhoneId);
        } else if (ServiceState.isGsm(i)) {
            this.mActivePhone = PhoneFactory.getGsmPhone(this.mPhoneId);
        } else {
            loge("deleteAndCreatePhone: newVoiceRadioTech=" + i + " is not CDMA or GSM (error) - aborting!");
            return;
        }
        ImsPhone relinquishOwnershipOfImsPhone = phone != null ? phone.relinquishOwnershipOfImsPhone() : null;
        if (this.mActivePhone != null) {
            CallManager.getInstance().registerPhone(this.mActivePhone);
            if (relinquishOwnershipOfImsPhone != null) {
                this.mActivePhone.acquireOwnershipOfImsPhone(relinquishOwnershipOfImsPhone);
            }
            this.mActivePhone.registerForSimRecordsLoaded(this, 6, null);
        }
        if (phone != null) {
            CallManager.getInstance().unregisterPhone(phone);
            logd("Disposing old phone..");
            phone.dispose();
        }
    }

    private static void logd(String str) {
        Rlog.d(LOG_TAG, "[PhoneProxy] " + str);
    }

    private void loge(String str) {
        Rlog.e(LOG_TAG, "[PhoneProxy] " + str);
    }

    private void phoneObjectUpdater(int i) {
        boolean isCdma;
        logd("phoneObjectUpdater: newVoiceRadioTech=" + i);
        if (this.mActivePhone != null) {
            if (i == 14 || i == 0) {
                int integer = this.mActivePhone.getContext().getResources().getInteger(17694816);
                logd("phoneObjectUpdater: volteReplacementRat=" + integer);
                if (integer != 0) {
                    i = integer;
                }
            }
            if (this.mRilVersion != 6 || getLteOnCdmaMode() != 1) {
                isCdma = ServiceState.isCdma(i);
                boolean isGsm = ServiceState.isGsm(i);
                if ((isCdma && this.mActivePhone.getPhoneType() == 2) || (isGsm && this.mActivePhone.getPhoneType() == 1)) {
                    logd("phoneObjectUpdater: No change ignore, newVoiceRadioTech=" + i + " mActivePhone=" + this.mActivePhone.getPhoneName());
                    return;
                } else if (!(isCdma || isGsm)) {
                    loge("phoneObjectUpdater: newVoiceRadioTech=" + i + " doesn't match either CDMA or GSM - error! No phone change");
                    return;
                }
            } else if (this.mActivePhone.getPhoneType() == 2) {
                logd("phoneObjectUpdater: LTE ON CDMA property is set. Use CDMA Phone newVoiceRadioTech=" + i + " mActivePhone=" + this.mActivePhone.getPhoneName());
                return;
            } else {
                logd("phoneObjectUpdater: LTE ON CDMA property is set. Switch to CDMALTEPhone newVoiceRadioTech=" + i + " mActivePhone=" + this.mActivePhone.getPhoneName());
                i = 6;
            }
        }
        if (i == 0) {
            logd("phoneObjectUpdater: Unknown rat ignore,  newVoiceRadioTech=Unknown. mActivePhone=" + this.mActivePhone.getPhoneName());
            return;
        }
        if (this.mResetModemOnRadioTechnologyChange && this.mCommandsInterface.getRadioState().isOn()) {
            logd("phoneObjectUpdater: Setting Radio Power to Off");
            this.mCommandsInterface.setRadioPower(false, null);
            isCdma = true;
        } else {
            isCdma = false;
        }
        deleteAndCreatePhone(i);
        if (this.mResetModemOnRadioTechnologyChange && isCdma) {
            logd("phoneObjectUpdater: Resetting Radio");
            this.mCommandsInterface.setRadioPower(isCdma, null);
        }
        this.mIccSmsInterfaceManager.updatePhoneObject((PhoneBase) this.mActivePhone);
        this.mIccPhoneBookInterfaceManagerProxy.setmIccPhoneBookInterfaceManager(this.mActivePhone.getIccPhoneBookInterfaceManager());
        this.mPhoneSubInfoProxy.setmPhoneSubInfo(this.mActivePhone.getPhoneSubInfo());
        this.mCommandsInterface = ((PhoneBase) this.mActivePhone).mCi;
        this.mIccCardProxy.setVoiceRadioTech(i);
        Intent intent = new Intent("android.intent.action.RADIO_TECHNOLOGY");
        intent.addFlags(536870912);
        intent.putExtra("phoneName", this.mActivePhone.getPhoneName());
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhoneId);
        ActivityManagerNative.broadcastStickyIntent(intent, null, -1);
        DctController.getInstance().updatePhoneObject(this);
    }

    public void acceptCall(int i) throws CallStateException {
        this.mActivePhone.acceptCall(i);
    }

    public void acquireOwnershipOfImsPhone(ImsPhone imsPhone) {
    }

    public void activateCellBroadcastSms(int i, Message message) {
        this.mActivePhone.activateCellBroadcastSms(i, message);
    }

    public void addParticipant(String str) throws CallStateException {
        this.mActivePhone.addParticipant(str);
    }

    public boolean canConference() {
        return this.mActivePhone.canConference();
    }

    public boolean canTransfer() {
        return this.mActivePhone.canTransfer();
    }

    public int changeMode(boolean z, String str, String str2, String str3, int i, String str4, String str5, String str6, String str7) {
        return TelBrand.IS_KDDI ? this.mActivePhone.changeMode(z, str, str2, str3, i, str4, str5, str6, str7) : -1;
    }

    public void clearDisconnected() {
        this.mActivePhone.clearDisconnected();
    }

    public void conference() throws CallStateException {
        this.mActivePhone.conference();
    }

    public void declineCall() throws CallStateException {
        this.mActivePhone.declineCall();
    }

    public void deflectCall(String str) throws CallStateException {
        this.mActivePhone.deflectCall(str);
    }

    public Connection dial(String str, int i) throws CallStateException {
        return this.mActivePhone.dial(str, i);
    }

    public Connection dial(String str, int i, int i2) throws CallStateException {
        return TelBrand.IS_DCM ? this.mActivePhone.dial(str, i, i2) : null;
    }

    public Connection dial(String str, int i, Bundle bundle) throws CallStateException {
        return this.mActivePhone.dial(str, i, bundle);
    }

    public Connection dial(String str, int i, Bundle bundle, int i2) throws CallStateException {
        return TelBrand.IS_DCM ? this.mActivePhone.dial(str, i, bundle, i2) : null;
    }

    public Connection dial(String str, UUSInfo uUSInfo, int i) throws CallStateException {
        return this.mActivePhone.dial(str, uUSInfo, i);
    }

    public Connection dial(String str, UUSInfo uUSInfo, int i, int i2) throws CallStateException {
        return TelBrand.IS_DCM ? this.mActivePhone.dial(str, uUSInfo, i, i2) : null;
    }

    public void disableDnsCheck(boolean z) {
        this.mActivePhone.disableDnsCheck(z);
    }

    public void disableLocationUpdates() {
        this.mActivePhone.disableLocationUpdates();
    }

    public void dispose() {
        if (this.mActivePhone != null) {
            this.mActivePhone.unregisterForSimRecordsLoaded(this);
        }
        this.mCommandsInterface.unregisterForOn(this);
        this.mCommandsInterface.unregisterForVoiceRadioTechChanged(this);
        this.mCommandsInterface.unregisterForRilConnected(this);
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        try {
            ((PhoneBase) this.mActivePhone).dump(fileDescriptor, printWriter, strArr);
        } catch (Exception e) {
            e.printStackTrace();
        }
        printWriter.flush();
        printWriter.println("++++++++++++++++++++++++++++++++");
        try {
            this.mPhoneSubInfoProxy.dump(fileDescriptor, printWriter, strArr);
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        printWriter.flush();
        printWriter.println("++++++++++++++++++++++++++++++++");
        try {
            this.mIccCardProxy.dump(fileDescriptor, printWriter, strArr);
        } catch (Exception e22) {
            e22.printStackTrace();
        }
        printWriter.flush();
        printWriter.println("++++++++++++++++++++++++++++++++");
    }

    public void enableEnhancedVoicePrivacy(boolean z, Message message) {
        this.mActivePhone.enableEnhancedVoicePrivacy(z, message);
    }

    public void enableLocationUpdates() {
        this.mActivePhone.enableLocationUpdates();
    }

    public void exitEmergencyCallbackMode() {
        this.mActivePhone.exitEmergencyCallbackMode();
    }

    public void explicitCallTransfer() throws CallStateException {
        this.mActivePhone.explicitCallTransfer();
    }

    public String getActiveApnHost(String str) {
        return this.mActivePhone.getActiveApnHost(str);
    }

    public String[] getActiveApnTypes() {
        return this.mActivePhone.getActiveApnTypes();
    }

    public Phone getActivePhone() {
        return this.mActivePhone;
    }

    public List<CellInfo> getAllCellInfo() {
        return this.mActivePhone.getAllCellInfo();
    }

    public void getAvailableNetworks(Message message) {
        this.mActivePhone.getAvailableNetworks(message);
    }

    public Call getBackgroundCall() {
        return this.mActivePhone.getBackgroundCall();
    }

    public void getBandPref(Message message) {
        this.mActivePhone.getBandPref(message);
    }

    public ServiceState getBaseServiceState() {
        return this.mActivePhone.getBaseServiceState();
    }

    public int getBrand() {
        return this.mActivePhone.getBrand();
    }

    public void getCallBarring(String str, Message message) {
        this.mActivePhone.getCallBarring(str, message);
    }

    public void getCallBarring(String str, String str2, int i, Message message) {
        this.mActivePhone.getCallBarring(str, str2, i, message);
    }

    public void getCallBarring(String str, String str2, Message message) {
        this.mActivePhone.getCallBarring(str, str2, message);
    }

    public void getCallBarringOption(String str, String str2, Message message) {
        this.mActivePhone.getCallBarringOption(str, str2, message);
    }

    public boolean getCallForwardingIndicator() {
        return this.mActivePhone.getCallForwardingIndicator();
    }

    public void getCallForwardingOption(int i, int i2, Message message) {
        this.mActivePhone.getCallForwardingOption(i, i2, message);
    }

    public void getCallForwardingOption(int i, Message message) {
        this.mActivePhone.getCallForwardingOption(i, message);
    }

    public void getCallForwardingUncondTimerOption(int i, Message message) {
        this.mActivePhone.getCallForwardingUncondTimerOption(i, message);
    }

    public void getCallWaiting(int i, Message message) {
        this.mActivePhone.getCallWaiting(i, message);
    }

    public void getCallWaiting(Message message) {
        this.mActivePhone.getCallWaiting(message);
    }

    public int getCdmaEriIconIndex() {
        return this.mActivePhone.getCdmaEriIconIndex();
    }

    public int getCdmaEriIconMode() {
        return this.mActivePhone.getCdmaEriIconMode();
    }

    public String getCdmaEriText() {
        return this.mActivePhone.getCdmaEriText();
    }

    public String getCdmaMin() {
        return this.mActivePhone.getCdmaMin();
    }

    public String getCdmaPrlVersion() {
        return this.mActivePhone.getCdmaPrlVersion();
    }

    public void getCellBroadcastSmsConfig(Message message) {
        this.mActivePhone.getCellBroadcastSmsConfig(message);
    }

    public CellLocation getCellLocation() {
        return this.mActivePhone.getCellLocation();
    }

    public String[] getConnInfo() {
        return TelBrand.IS_KDDI ? this.mActivePhone.getConnInfo() : new String[3];
    }

    public int getConnStatus() {
        return TelBrand.IS_KDDI ? this.mActivePhone.getConnStatus() : 6;
    }

    public Context getContext() {
        return this.mActivePhone.getContext();
    }

    public DataActivityState getDataActivityState() {
        return this.mActivePhone.getDataActivityState();
    }

    public void getDataCallList(Message message) {
        this.mActivePhone.getDataCallList(message);
    }

    public DataState getDataConnectionState() {
        return this.mActivePhone.getDataConnectionState("default");
    }

    public DataState getDataConnectionState(String str) {
        return this.mActivePhone.getDataConnectionState(str);
    }

    public boolean getDataEnabled() {
        return this.mActivePhone.getDataEnabled();
    }

    public boolean getDataRoamingEnabled() {
        return this.mActivePhone.getDataRoamingEnabled();
    }

    public String getDeviceId() {
        return this.mActivePhone.getDeviceId();
    }

    public String getDeviceSvn() {
        return this.mActivePhone.getDeviceSvn();
    }

    public void getEnhancedVoicePrivacy(Message message) {
        this.mActivePhone.getEnhancedVoicePrivacy(message);
    }

    public String getEsn() {
        return this.mActivePhone.getEsn();
    }

    public Call getForegroundCall() {
        return this.mActivePhone.getForegroundCall();
    }

    public String getGroupIdLevel1() {
        return this.mActivePhone.getGroupIdLevel1();
    }

    public IccCard getIccCard() {
        return this.mIccCardProxy;
    }

    public IccFileHandler getIccFileHandler() {
        return ((PhoneBase) this.mActivePhone).getIccFileHandler();
    }

    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return this.mActivePhone.getIccPhoneBookInterfaceManager();
    }

    public IccPhoneBookInterfaceManagerProxy getIccPhoneBookInterfaceManagerProxy() {
        return this.mIccPhoneBookInterfaceManagerProxy;
    }

    public boolean getIccRecordsLoaded() {
        return this.mIccCardProxy.getIccRecordsLoaded();
    }

    public String getIccSerialNumber() {
        return this.mActivePhone.getIccSerialNumber();
    }

    public IccSmsInterfaceManager getIccSmsInterfaceManager() {
        return this.mIccSmsInterfaceManager;
    }

    public String getImei() {
        return this.mActivePhone.getImei();
    }

    public Phone getImsPhone() {
        return this.mActivePhone.getImsPhone();
    }

    public String getImsiOnSimLock() {
        return this.mActivePhone.getImsiOnSimLock();
    }

    public void getIncomingAnonymousCallBarring(Message message) {
        this.mActivePhone.getIncomingAnonymousCallBarring(message);
    }

    public void getIncomingSpecificDnCallBarring(Message message) {
        this.mActivePhone.getIncomingSpecificDnCallBarring(message);
    }

    public IsimRecords getIsimRecords() {
        return this.mActivePhone.getIsimRecords();
    }

    public String getLine1AlphaTag() {
        return this.mActivePhone.getLine1AlphaTag();
    }

    public String getLine1Number() {
        return this.mActivePhone.getLine1Number();
    }

    public LinkProperties getLinkProperties(String str) {
        return this.mActivePhone.getLinkProperties(str);
    }

    public int getLteOnCdmaMode() {
        return this.mActivePhone.getLteOnCdmaMode();
    }

    public String getMccMncOnSimLock() {
        return this.mActivePhone.getMccMncOnSimLock();
    }

    public String getMeid() {
        return this.mActivePhone.getMeid();
    }

    public boolean getMessageWaitingIndicator() {
        return this.mActivePhone.getMessageWaitingIndicator();
    }

    public String getMsisdn() {
        return this.mActivePhone.getMsisdn();
    }

    public boolean getMute() {
        return this.mActivePhone.getMute();
    }

    public String getNai() {
        return this.mActivePhone.getNai();
    }

    public void getNeighboringCids(Message message) {
        this.mActivePhone.getNeighboringCids(message);
    }

    public NetworkCapabilities getNetworkCapabilities(String str) {
        return this.mActivePhone.getNetworkCapabilities(str);
    }

    public void getNetworkSelectionMode(Message message) {
        this.mActivePhone.getNetworkSelectionMode(message);
    }

    public void getOutgoingCallerIdDisplay(Message message) {
        this.mActivePhone.getOutgoingCallerIdDisplay(message);
    }

    public String[] getPcscfAddress(String str) {
        return this.mActivePhone.getPcscfAddress(str);
    }

    public List<? extends MmiCode> getPendingMmiCodes() {
        return this.mActivePhone.getPendingMmiCodes();
    }

    public int getPhoneId() {
        return this.mActivePhone.getPhoneId();
    }

    public String getPhoneName() {
        return this.mActivePhone.getPhoneName();
    }

    public PhoneSubInfo getPhoneSubInfo() {
        return this.mActivePhone.getPhoneSubInfo();
    }

    public PhoneSubInfoProxy getPhoneSubInfoProxy() {
        return this.mPhoneSubInfoProxy;
    }

    public int getPhoneType() {
        return this.mActivePhone.getPhoneType();
    }

    public void getPreferredNetworkType(Message message) {
        this.mActivePhone.getPreferredNetworkType(message);
    }

    public void getPreferredNetworkTypeWithOptimizeSetting(Message message) {
        this.mActivePhone.getPreferredNetworkTypeWithOptimizeSetting(message);
    }

    public Call getRingingCall() {
        return this.mActivePhone.getRingingCall();
    }

    public ServiceState getServiceState() {
        return this.mActivePhone.getServiceState();
    }

    public SignalStrength getSignalStrength() {
        return this.mActivePhone.getSignalStrength();
    }

    public SimulatedRadioControl getSimulatedRadioControl() {
        return this.mActivePhone.getSimulatedRadioControl();
    }

    public void getSmscAddress(Message message) {
        this.mActivePhone.getSmscAddress(message);
    }

    public State getState() {
        return this.mActivePhone.getState();
    }

    public int getSubId() {
        return this.mActivePhone.getSubId();
    }

    public String getSubscriberId() {
        return this.mActivePhone.getSubscriberId();
    }

    public UiccCard getUiccCard() {
        return this.mActivePhone.getUiccCard();
    }

    public boolean getUnitTestMode() {
        return this.mActivePhone.getUnitTestMode();
    }

    public UsimServiceTable getUsimServiceTable() {
        return this.mActivePhone.getUsimServiceTable();
    }

    public String getVoiceMailAlphaTag() {
        return this.mActivePhone.getVoiceMailAlphaTag();
    }

    public String getVoiceMailNumber() {
        return this.mActivePhone.getVoiceMailNumber();
    }

    public int getVoiceMessageCount() {
        return this.mActivePhone.getVoiceMessageCount();
    }

    public int getVoicePhoneServiceState() {
        return this.mActivePhone.getVoicePhoneServiceState();
    }

    public boolean handleInCallMmiCommands(String str) throws CallStateException {
        return this.mActivePhone.handleInCallMmiCommands(str);
    }

    public void handleMessage(Message message) {
        AsyncResult asyncResult = (AsyncResult) message.obj;
        switch (message.what) {
            case 1:
            case 3:
                String str = message.what == 1 ? "EVENT_VOICE_RADIO_TECH_CHANGED" : "EVENT_REQUEST_VOICE_RADIO_TECH_DONE";
                if (asyncResult.exception == null) {
                    if (asyncResult.result != null && ((int[]) asyncResult.result).length != 0) {
                        int i = ((int[]) asyncResult.result)[0];
                        logd(str + ": newVoiceTech=" + i);
                        phoneObjectUpdater(i);
                        break;
                    }
                    loge(str + ": has no tech!");
                    break;
                }
                loge(str + ": exception=" + asyncResult.exception);
                break;
            case 2:
                this.mCommandsInterface.getVoiceRadioTechnology(obtainMessage(3));
                break;
            case 4:
                if (asyncResult.exception == null && asyncResult.result != null) {
                    this.mRilVersion = ((Integer) asyncResult.result).intValue();
                    break;
                }
                logd("Unexpected exception on EVENT_RIL_CONNECTED");
                this.mRilVersion = -1;
                break;
                break;
            case 5:
                phoneObjectUpdater(message.arg1);
                break;
            case 6:
                if (!this.mActivePhone.getContext().getResources().getBoolean(17957009)) {
                    this.mCommandsInterface.getVoiceRadioTechnology(obtainMessage(3));
                    break;
                }
                break;
            default:
                loge("Error! This handler was not registered for this message type. Message: " + message.what);
                break;
        }
        super.handleMessage(message);
    }

    public boolean handlePinMmi(String str) {
        return this.mActivePhone.handlePinMmi(str);
    }

    public boolean hasMatchedTetherApnSetting() {
        return this.mActivePhone.hasMatchedTetherApnSetting();
    }

    public void invokeOemRilRequestRaw(byte[] bArr, Message message) {
        this.mActivePhone.invokeOemRilRequestRaw(bArr, message);
    }

    public void invokeOemRilRequestStrings(String[] strArr, Message message) {
        this.mActivePhone.invokeOemRilRequestStrings(strArr, message);
    }

    public boolean isCspPlmnEnabled() {
        return this.mActivePhone.isCspPlmnEnabled();
    }

    public boolean isDataConnectivityPossible() {
        return this.mActivePhone.isDataConnectivityPossible("default");
    }

    public boolean isDataConnectivityPossible(String str) {
        return this.mActivePhone.isDataConnectivityPossible(str);
    }

    public boolean isDnsCheckDisabled() {
        return this.mActivePhone.isDnsCheckDisabled();
    }

    public boolean isDunConnectionPossible() {
        return this.mActivePhone.isDunConnectionPossible();
    }

    public boolean isImsRegistered() {
        return this.mActivePhone.isImsRegistered();
    }

    public boolean isImsVtCallPresent() {
        return this.mActivePhone.isImsVtCallPresent();
    }

    public boolean isManualNetSelAllowed() {
        return this.mActivePhone.isManualNetSelAllowed();
    }

    public boolean isMinInfoReady() {
        return this.mActivePhone.isMinInfoReady();
    }

    public boolean isOnDemandDataPossible(String str) {
        return this.mActivePhone.isOnDemandDataPossible(str);
    }

    public boolean isOtaSpNumber(String str) {
        return this.mActivePhone.isOtaSpNumber(str);
    }

    public boolean isRadioAvailable() {
        return this.mCommandsInterface.getRadioState().isAvailable();
    }

    public boolean isUtEnabled() {
        return this.mActivePhone.isUtEnabled();
    }

    public boolean needsOtaServiceProvisioning() {
        return this.mActivePhone.needsOtaServiceProvisioning();
    }

    public void notifyDataActivity() {
        this.mActivePhone.notifyDataActivity();
    }

    public void nvReadItem(int i, Message message) {
        this.mActivePhone.nvReadItem(i, message);
    }

    public void nvResetConfig(int i, Message message) {
        this.mActivePhone.nvResetConfig(i, message);
    }

    public void nvWriteCdmaPrl(byte[] bArr, Message message) {
        this.mActivePhone.nvWriteCdmaPrl(bArr, message);
    }

    public void nvWriteItem(int i, String str, Message message) {
        this.mActivePhone.nvWriteItem(i, str, message);
    }

    public void queryAvailableBandMode(Message message) {
        this.mActivePhone.queryAvailableBandMode(message);
    }

    public void queryCdmaRoamingPreference(Message message) {
        this.mActivePhone.queryCdmaRoamingPreference(message);
    }

    public void queryTTYMode(Message message) {
        this.mActivePhone.queryTTYMode(message);
    }

    public void registerFoT53ClirlInfo(Handler handler, int i, Object obj) {
        this.mActivePhone.registerFoT53ClirlInfo(handler, i, obj);
    }

    public void registerForAllDataDisconnected(Handler handler, int i, Object obj) {
        if (this.mActivePhone instanceof CDMALTEPhone) {
            ((CDMALTEPhone) this.mActivePhone).registerForAllDataDisconnected(handler, i, obj);
        } else if (this.mActivePhone instanceof GSMPhone) {
            ((GSMPhone) this.mActivePhone).registerForAllDataDisconnected(handler, i, obj);
        } else {
            loge("Phone object is not MultiSim. This should not hit!!!!");
        }
    }

    public void registerForCallWaiting(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForCallWaiting(handler, i, obj);
    }

    public void registerForCdmaOtaStatusChange(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForCdmaOtaStatusChange(handler, i, obj);
    }

    public void registerForDisconnect(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForDisconnect(handler, i, obj);
    }

    public void registerForDisplayInfo(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForDisplayInfo(handler, i, obj);
    }

    public void registerForEcmTimerReset(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForEcmTimerReset(handler, i, obj);
    }

    public void registerForHandoverStateChanged(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForHandoverStateChanged(handler, i, obj);
    }

    public void registerForInCallVoicePrivacyOff(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForInCallVoicePrivacyOff(handler, i, obj);
    }

    public void registerForInCallVoicePrivacyOn(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForInCallVoicePrivacyOn(handler, i, obj);
    }

    public void registerForIncomingRing(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForIncomingRing(handler, i, obj);
    }

    public void registerForLineControlInfo(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForLineControlInfo(handler, i, obj);
    }

    public void registerForMmiComplete(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForMmiComplete(handler, i, obj);
    }

    public void registerForMmiInitiate(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForMmiInitiate(handler, i, obj);
    }

    public void registerForNewRingingConnection(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForNewRingingConnection(handler, i, obj);
    }

    public void registerForNumberInfo(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForNumberInfo(handler, i, obj);
    }

    public void registerForOnHoldTone(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForOnHoldTone(handler, i, obj);
    }

    public void registerForPreciseCallStateChanged(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForPreciseCallStateChanged(handler, i, obj);
    }

    public void registerForRadioOffOrNotAvailable(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForRadioOffOrNotAvailable(handler, i, obj);
    }

    public void registerForRedirectedNumberInfo(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForRedirectedNumberInfo(handler, i, obj);
    }

    public void registerForResendIncallMute(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForResendIncallMute(handler, i, obj);
    }

    public void registerForRingbackTone(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForRingbackTone(handler, i, obj);
    }

    public void registerForServiceStateChanged(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForServiceStateChanged(handler, i, obj);
    }

    public void registerForSignalInfo(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForSignalInfo(handler, i, obj);
    }

    public void registerForSimRecordsLoaded(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForSimRecordsLoaded(handler, i, obj);
    }

    public void registerForSubscriptionInfoReady(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForSubscriptionInfoReady(handler, i, obj);
    }

    public void registerForSuppServiceFailed(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForSuppServiceFailed(handler, i, obj);
    }

    public void registerForSuppServiceNotification(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForSuppServiceNotification(handler, i, obj);
    }

    public void registerForT53AudioControlInfo(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForT53AudioControlInfo(handler, i, obj);
    }

    public void registerForTtyModeReceived(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForTtyModeReceived(handler, i, obj);
    }

    public void registerForUnknownConnection(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForUnknownConnection(handler, i, obj);
    }

    public void registerForVideoCapabilityChanged(Handler handler, int i, Object obj) {
        this.mActivePhone.registerForVideoCapabilityChanged(handler, i, obj);
    }

    public void rejectCall() throws CallStateException {
        this.mActivePhone.rejectCall();
    }

    public ImsPhone relinquishOwnershipOfImsPhone() {
        return null;
    }

    public void removeReferences() {
        this.mActivePhone = null;
        this.mCommandsInterface = null;
    }

    public void requestChangeCbPsw(String str, String str2, String str3, Message message) {
        this.mActivePhone.requestChangeCbPsw(str, str2, str3, message);
    }

    public void resetDunProfiles() {
        this.mActivePhone.resetDunProfiles();
    }

    public void selectNetworkManually(OperatorInfo operatorInfo, Message message) {
        this.mActivePhone.selectNetworkManually(operatorInfo, message);
    }

    public void sendBurstDtmf(String str, int i, int i2, Message message) {
        this.mActivePhone.sendBurstDtmf(str, i, i2, message);
    }

    public void sendDtmf(char c) {
        this.mActivePhone.sendDtmf(c);
    }

    public void sendUssdResponse(String str) {
        this.mActivePhone.sendUssdResponse(str);
    }

    public void setBandMode(int i, Message message) {
        this.mActivePhone.setBandMode(i, message);
    }

    public void setBandPref(long j, int i, Message message) {
        this.mActivePhone.setBandPref(j, i, message);
    }

    public void setCallBarring(String str, boolean z, String str2, int i, Message message) {
        this.mActivePhone.setCallBarring(str, z, str2, i, message);
    }

    public void setCallBarring(String str, boolean z, String str2, Message message) {
        this.mActivePhone.setCallBarring(str, z, str2, message);
    }

    public void setCallBarringOption(String str, boolean z, String str2, Message message) {
        this.mActivePhone.setCallBarringOption(str, z, str2, message);
    }

    public void setCallForwardingOption(int i, int i2, int i3, String str, int i4, Message message) {
        this.mActivePhone.setCallForwardingOption(i, i2, i3, str, i4, message);
    }

    public void setCallForwardingOption(int i, int i2, String str, int i3, Message message) {
        this.mActivePhone.setCallForwardingOption(i, i2, str, i3, message);
    }

    public void setCallForwardingUncondTimerOption(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message) {
        this.mActivePhone.setCallForwardingUncondTimerOption(i, i2, i3, i4, i5, i6, str, message);
    }

    public void setCallWaiting(boolean z, int i, Message message) {
        this.mActivePhone.setCallWaiting(z, i, message);
    }

    public void setCallWaiting(boolean z, Message message) {
        this.mActivePhone.setCallWaiting(z, message);
    }

    public void setCdmaRoamingPreference(int i, Message message) {
        this.mActivePhone.setCdmaRoamingPreference(i, message);
    }

    public void setCdmaSubscription(int i, Message message) {
        this.mActivePhone.setCdmaSubscription(i, message);
    }

    public void setCellBroadcastSmsConfig(int[] iArr, Message message) {
        this.mActivePhone.setCellBroadcastSmsConfig(iArr, message);
    }

    public void setCellInfoListRate(int i) {
        this.mActivePhone.setCellInfoListRate(i);
    }

    public void setDataEnabled(boolean z) {
        this.mActivePhone.setDataEnabled(z);
    }

    public void setDataRoamingEnabled(boolean z) {
        this.mActivePhone.setDataRoamingEnabled(z);
    }

    public void setEchoSuppressionEnabled() {
        this.mActivePhone.setEchoSuppressionEnabled();
    }

    public void setImsRegistrationState(boolean z) {
        logd("setImsRegistrationState - registered: " + z);
        this.mActivePhone.setImsRegistrationState(z);
        if (this.mActivePhone.getPhoneName().equals("GSM")) {
            ((GSMPhone) this.mActivePhone).getServiceStateTracker().setImsRegistrationState(z);
        } else if (this.mActivePhone.getPhoneName().equals("CDMA")) {
            ((CDMAPhone) this.mActivePhone).getServiceStateTracker().setImsRegistrationState(z);
        }
    }

    public void setIncomingAnonymousCallBarring(boolean z, Message message) {
        this.mActivePhone.setIncomingAnonymousCallBarring(z, message);
    }

    public void setIncomingSpecificDnCallBarring(int i, String[] strArr, Message message) {
        this.mActivePhone.setIncomingSpecificDnCallBarring(i, strArr, message);
    }

    public void setInternalDataEnabled(boolean z) {
        setInternalDataEnabled(z, null);
    }

    public void setInternalDataEnabled(boolean z, Message message) {
        if (this.mActivePhone instanceof CDMALTEPhone) {
            ((CDMALTEPhone) this.mActivePhone).setInternalDataEnabled(z, message);
        } else if (this.mActivePhone instanceof GSMPhone) {
            ((GSMPhone) this.mActivePhone).setInternalDataEnabled(z, message);
        } else {
            loge("Phone object is not MultiSim. This should not hit!!!!");
        }
    }

    public boolean setInternalDataEnabledFlag(boolean z) {
        if (this.mActivePhone instanceof CDMALTEPhone) {
            return ((CDMALTEPhone) this.mActivePhone).setInternalDataEnabledFlag(z);
        }
        if (this.mActivePhone instanceof GSMPhone) {
            return ((GSMPhone) this.mActivePhone).setInternalDataEnabledFlag(z);
        }
        loge("Phone object is not MultiSim. This should not hit!!!!");
        return false;
    }

    public void setLimitationByChameleon(boolean z, Message message) {
        this.mActivePhone.setLimitationByChameleon(z, message);
    }

    public void setLine1Number(String str, String str2, Message message) {
        this.mActivePhone.setLine1Number(str, str2, message);
    }

    public void setLocalCallHold(int i) {
        this.mActivePhone.setLocalCallHold(i);
    }

    public void setMobileDataEnabledDun(boolean z) {
        this.mActivePhone.setMobileDataEnabledDun(z);
    }

    public void setModemSettingsByChameleon(int i, Message message) {
        this.mActivePhone.setModemSettingsByChameleon(i, message);
    }

    public void setMute(boolean z) {
        this.mActivePhone.setMute(z);
    }

    public void setNetworkSelectionModeAutomatic(Message message) {
        this.mActivePhone.setNetworkSelectionModeAutomatic(message);
    }

    public void setOnEcbModeExitResponse(Handler handler, int i, Object obj) {
        this.mActivePhone.setOnEcbModeExitResponse(handler, i, obj);
    }

    public void setOnPostDialCharacter(Handler handler, int i, Object obj) {
        this.mActivePhone.setOnPostDialCharacter(handler, i, obj);
    }

    public boolean setOperatorBrandOverride(String str) {
        return this.mActivePhone.setOperatorBrandOverride(str);
    }

    public void setOutgoingCallerIdDisplay(int i, Message message) {
        this.mActivePhone.setOutgoingCallerIdDisplay(i, message);
    }

    public void setPreferredNetworkType(int i, Message message) {
        this.mActivePhone.setPreferredNetworkType(i, message);
    }

    public void setPreferredNetworkTypeWithOptimizeSetting(int i, boolean z, Message message) {
        this.mActivePhone.setPreferredNetworkTypeWithOptimizeSetting(i, z, message);
    }

    public void setProfilePdpType(int i, String str) {
        this.mActivePhone.setProfilePdpType(i, str);
    }

    public void setRadioPower(boolean z) {
        this.mActivePhone.setRadioPower(z);
    }

    public boolean setRoamingOverride(List<String> list, List<String> list2, List<String> list3, List<String> list4) {
        return this.mActivePhone.setRoamingOverride(list, list2, list3, list4);
    }

    public void setSmscAddress(String str, Message message) {
        this.mActivePhone.setSmscAddress(str, message);
    }

    public void setTTYMode(int i, Message message) {
        this.mActivePhone.setTTYMode(i, message);
    }

    public void setUiTTYMode(int i, Message message) {
        this.mActivePhone.setUiTTYMode(i, message);
    }

    public void setUnitTestMode(boolean z) {
        this.mActivePhone.setUnitTestMode(z);
    }

    public void setVoiceMailNumber(String str, String str2, Message message) {
        this.mActivePhone.setVoiceMailNumber(str, str2, message);
    }

    public void setVoiceMessageWaiting(int i, int i2) {
        this.mActivePhone.setVoiceMessageWaiting(i, i2);
    }

    public void shutdownRadio() {
        this.mActivePhone.shutdownRadio();
    }

    public void startDtmf(char c) {
        this.mActivePhone.startDtmf(c);
    }

    public void stopDtmf() {
        this.mActivePhone.stopDtmf();
    }

    public void switchHoldingAndActive() throws CallStateException {
        this.mActivePhone.switchHoldingAndActive();
    }

    public void unregisterForAllDataDisconnected(Handler handler) {
        if (this.mActivePhone instanceof CDMALTEPhone) {
            ((CDMALTEPhone) this.mActivePhone).unregisterForAllDataDisconnected(handler);
        } else if (this.mActivePhone instanceof GSMPhone) {
            ((GSMPhone) this.mActivePhone).unregisterForAllDataDisconnected(handler);
        } else {
            loge("Phone object is not MultiSim. This should not hit!!!!");
        }
    }

    public void unregisterForCallWaiting(Handler handler) {
        this.mActivePhone.unregisterForCallWaiting(handler);
    }

    public void unregisterForCdmaOtaStatusChange(Handler handler) {
        this.mActivePhone.unregisterForCdmaOtaStatusChange(handler);
    }

    public void unregisterForDisconnect(Handler handler) {
        this.mActivePhone.unregisterForDisconnect(handler);
    }

    public void unregisterForDisplayInfo(Handler handler) {
        this.mActivePhone.unregisterForDisplayInfo(handler);
    }

    public void unregisterForEcmTimerReset(Handler handler) {
        this.mActivePhone.unregisterForEcmTimerReset(handler);
    }

    public void unregisterForHandoverStateChanged(Handler handler) {
        this.mActivePhone.unregisterForHandoverStateChanged(handler);
    }

    public void unregisterForInCallVoicePrivacyOff(Handler handler) {
        this.mActivePhone.unregisterForInCallVoicePrivacyOff(handler);
    }

    public void unregisterForInCallVoicePrivacyOn(Handler handler) {
        this.mActivePhone.unregisterForInCallVoicePrivacyOn(handler);
    }

    public void unregisterForIncomingRing(Handler handler) {
        this.mActivePhone.unregisterForIncomingRing(handler);
    }

    public void unregisterForLineControlInfo(Handler handler) {
        this.mActivePhone.unregisterForLineControlInfo(handler);
    }

    public void unregisterForMmiComplete(Handler handler) {
        this.mActivePhone.unregisterForMmiComplete(handler);
    }

    public void unregisterForMmiInitiate(Handler handler) {
        this.mActivePhone.unregisterForMmiInitiate(handler);
    }

    public void unregisterForNewRingingConnection(Handler handler) {
        this.mActivePhone.unregisterForNewRingingConnection(handler);
    }

    public void unregisterForNumberInfo(Handler handler) {
        this.mActivePhone.unregisterForNumberInfo(handler);
    }

    public void unregisterForOnHoldTone(Handler handler) {
        this.mActivePhone.unregisterForOnHoldTone(handler);
    }

    public void unregisterForPreciseCallStateChanged(Handler handler) {
        this.mActivePhone.unregisterForPreciseCallStateChanged(handler);
    }

    public void unregisterForRadioOffOrNotAvailable(Handler handler) {
        this.mActivePhone.unregisterForRadioOffOrNotAvailable(handler);
    }

    public void unregisterForRedirectedNumberInfo(Handler handler) {
        this.mActivePhone.unregisterForRedirectedNumberInfo(handler);
    }

    public void unregisterForResendIncallMute(Handler handler) {
        this.mActivePhone.unregisterForResendIncallMute(handler);
    }

    public void unregisterForRingbackTone(Handler handler) {
        this.mActivePhone.unregisterForRingbackTone(handler);
    }

    public void unregisterForServiceStateChanged(Handler handler) {
        this.mActivePhone.unregisterForServiceStateChanged(handler);
    }

    public void unregisterForSignalInfo(Handler handler) {
        this.mActivePhone.unregisterForSignalInfo(handler);
    }

    public void unregisterForSimRecordsLoaded(Handler handler) {
        this.mActivePhone.unregisterForSimRecordsLoaded(handler);
    }

    public void unregisterForSubscriptionInfoReady(Handler handler) {
        this.mActivePhone.unregisterForSubscriptionInfoReady(handler);
    }

    public void unregisterForSuppServiceFailed(Handler handler) {
        this.mActivePhone.unregisterForSuppServiceFailed(handler);
    }

    public void unregisterForSuppServiceNotification(Handler handler) {
        this.mActivePhone.unregisterForSuppServiceNotification(handler);
    }

    public void unregisterForT53AudioControlInfo(Handler handler) {
        this.mActivePhone.unregisterForT53AudioControlInfo(handler);
    }

    public void unregisterForT53ClirInfo(Handler handler) {
        this.mActivePhone.unregisterForT53ClirInfo(handler);
    }

    public void unregisterForTtyModeReceived(Handler handler) {
        this.mActivePhone.unregisterForTtyModeReceived(handler);
    }

    public void unregisterForUnknownConnection(Handler handler) {
        this.mActivePhone.unregisterForUnknownConnection(handler);
    }

    public void unregisterForVideoCapabilityChanged(Handler handler) {
        this.mActivePhone.unregisterForVideoCapabilityChanged(handler);
    }

    public void unsetOnEcbModeExitResponse(Handler handler) {
        this.mActivePhone.unsetOnEcbModeExitResponse(handler);
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

    public void updatePhoneObject(int i) {
        logd("updatePhoneObject: radioTechnology=" + i);
        sendMessage(obtainMessage(5, i, 0, null));
    }

    public void updateServiceLocation() {
        this.mActivePhone.updateServiceLocation();
    }
}
