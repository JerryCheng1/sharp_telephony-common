package com.android.internal.telephony.sip;

import android.content.Context;
import android.net.LinkProperties;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone.DataActivityState;
import com.android.internal.telephony.Phone.SuppService;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.dataconnection.DataConnection;
import com.android.internal.telephony.uicc.IccFileHandler;
import java.util.ArrayList;
import java.util.List;

abstract class SipPhoneBase extends PhoneBase {
    private static final String LOG_TAG = "SipPhoneBase";
    private RegistrantList mRingbackRegistrants = new RegistrantList();
    private State mState = State.IDLE;

    public SipPhoneBase(String str, Context context, PhoneNotifier phoneNotifier) {
        super(str, phoneNotifier, context, new SipCommandInterface(context), false);
    }

    public void activateCellBroadcastSms(int i, Message message) {
        Rlog.e(LOG_TAG, "Error! This functionality is not implemented for SIP.");
    }

    public boolean canDial() {
        int state = getServiceState().getState();
        Rlog.v(LOG_TAG, "canDial(): serviceState = " + state);
        if (state != 3) {
            String str = SystemProperties.get("ro.telephony.disable-call", "false");
            Rlog.v(LOG_TAG, "canDial(): disableCall = " + str);
            if (!str.equals("true")) {
                Rlog.v(LOG_TAG, "canDial(): ringingCall: " + getRingingCall().getState());
                Rlog.v(LOG_TAG, "canDial(): foregndCall: " + getForegroundCall().getState());
                Rlog.v(LOG_TAG, "canDial(): backgndCall: " + getBackgroundCall().getState());
                if (!(getRingingCall().isRinging() || (getForegroundCall().getState().isAlive() && getBackgroundCall().getState().isAlive()))) {
                    return true;
                }
            }
        }
        return false;
    }

    public Connection dial(String str, UUSInfo uUSInfo, int i) throws CallStateException {
        return dial(str, i);
    }

    public boolean disableDataConnectivity() {
        return false;
    }

    public void disableLocationUpdates() {
    }

    public boolean enableDataConnectivity() {
        return false;
    }

    public void enableLocationUpdates() {
    }

    public void getAvailableNetworks(Message message) {
    }

    public abstract Call getBackgroundCall();

    public boolean getCallForwardingIndicator() {
        return false;
    }

    public void getCallForwardingOption(int i, Message message) {
    }

    public void getCallWaiting(Message message) {
        AsyncResult.forMessage(message, null, null);
        message.sendToTarget();
    }

    public void getCellBroadcastSmsConfig(Message message) {
        Rlog.e(LOG_TAG, "Error! This functionality is not implemented for SIP.");
    }

    public CellLocation getCellLocation() {
        return null;
    }

    public List<DataConnection> getCurrentDataConnectionList() {
        return null;
    }

    public DataActivityState getDataActivityState() {
        return DataActivityState.NONE;
    }

    public void getDataCallList(Message message) {
    }

    public DataState getDataConnectionState() {
        return DataState.DISCONNECTED;
    }

    public DataState getDataConnectionState(String str) {
        return DataState.DISCONNECTED;
    }

    public boolean getDataEnabled() {
        return false;
    }

    public boolean getDataRoamingEnabled() {
        return false;
    }

    public String getDeviceId() {
        return null;
    }

    public String getDeviceSvn() {
        return null;
    }

    public String getEsn() {
        Rlog.e(LOG_TAG, "[SipPhone] getEsn() is a CDMA method");
        return "0";
    }

    public abstract Call getForegroundCall();

    public String getGroupIdLevel1() {
        return null;
    }

    public IccCard getIccCard() {
        return null;
    }

    public IccFileHandler getIccFileHandler() {
        return null;
    }

    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return null;
    }

    public boolean getIccRecordsLoaded() {
        return false;
    }

    public String getIccSerialNumber() {
        return null;
    }

    public String getImei() {
        return null;
    }

    public String getLine1AlphaTag() {
        return null;
    }

    public String getLine1Number() {
        return null;
    }

    public LinkProperties getLinkProperties(String str) {
        return null;
    }

    public String getMeid() {
        Rlog.e(LOG_TAG, "[SipPhone] getMeid() is a CDMA method");
        return "0";
    }

    public boolean getMessageWaitingIndicator() {
        return false;
    }

    public void getNeighboringCids(Message message) {
    }

    public void getOutgoingCallerIdDisplay(Message message) {
        AsyncResult.forMessage(message, null, null);
        message.sendToTarget();
    }

    public List<? extends MmiCode> getPendingMmiCodes() {
        return new ArrayList(0);
    }

    public PhoneSubInfo getPhoneSubInfo() {
        return null;
    }

    public int getPhoneType() {
        return 3;
    }

    public abstract Call getRingingCall();

    public ServiceState getServiceState() {
        ServiceState serviceState = new ServiceState();
        serviceState.setState(0);
        return serviceState;
    }

    public SignalStrength getSignalStrength() {
        return new SignalStrength();
    }

    public State getState() {
        return this.mState;
    }

    public String getSubscriberId() {
        return null;
    }

    public String getVoiceMailAlphaTag() {
        return null;
    }

    public String getVoiceMailNumber() {
        return null;
    }

    public boolean handleInCallMmiCommands(String str) {
        return false;
    }

    public boolean handlePinMmi(String str) {
        return false;
    }

    public boolean isDataConnectivityPossible() {
        return false;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isInCall() {
        return getForegroundCall().getState().isAlive() || getBackgroundCall().getState().isAlive() || getRingingCall().getState().isAlive();
    }

    /* Access modifiers changed, original: 0000 */
    public void migrateFrom(SipPhoneBase sipPhoneBase) {
        super.migrateFrom(sipPhoneBase);
        migrate(this.mRingbackRegistrants, sipPhoneBase.mRingbackRegistrants);
    }

    public boolean needsOtaServiceProvisioning() {
        return false;
    }

    public void notifyCallForwardingIndicator() {
        this.mNotifier.notifyCallForwardingChanged(this);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyDisconnect(Connection connection) {
        this.mDisconnectRegistrants.notifyResult(connection);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyNewRingingConnection(Connection connection) {
        super.notifyNewRingingConnectionP(connection);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyPhoneStateChanged() {
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyPreciseCallStateChanged() {
        super.notifyPreciseCallStateChangedP();
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyServiceStateChanged(ServiceState serviceState) {
        super.notifyServiceStateChangedP(serviceState);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifySuppServiceFailed(SuppService suppService) {
        this.mSuppServiceFailedRegistrants.notifyResult(suppService);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyUnknownConnection() {
        this.mUnknownConnectionRegistrants.notifyResult(this);
    }

    /* Access modifiers changed, original: protected */
    public void onUpdateIccAvailability() {
    }

    public void registerForRingbackTone(Handler handler, int i, Object obj) {
        this.mRingbackRegistrants.addUnique(handler, i, obj);
    }

    public void registerForSuppServiceNotification(Handler handler, int i, Object obj) {
    }

    public void saveClirSetting(int i) {
    }

    public void selectNetworkManually(OperatorInfo operatorInfo, Message message) {
    }

    public void sendUssdResponse(String str) {
    }

    public void setCallForwardingOption(int i, int i2, String str, int i3, Message message) {
    }

    public void setCallWaiting(boolean z, Message message) {
        Rlog.e(LOG_TAG, "call waiting not supported");
    }

    public void setCellBroadcastSmsConfig(int[] iArr, Message message) {
        Rlog.e(LOG_TAG, "Error! This functionality is not implemented for SIP.");
    }

    public void setDataEnabled(boolean z) {
    }

    public void setDataRoamingEnabled(boolean z) {
    }

    public void setLine1Number(String str, String str2, Message message) {
        AsyncResult.forMessage(message, null, null);
        message.sendToTarget();
    }

    public void setNetworkSelectionModeAutomatic(Message message) {
    }

    public void setOnPostDialCharacter(Handler handler, int i, Object obj) {
    }

    public void setOutgoingCallerIdDisplay(int i, Message message) {
        AsyncResult.forMessage(message, null, null);
        message.sendToTarget();
    }

    public void setRadioPower(boolean z) {
    }

    public void setVoiceMailNumber(String str, String str2, Message message) {
        AsyncResult.forMessage(message, null, null);
        message.sendToTarget();
    }

    /* Access modifiers changed, original: protected */
    public void startRingbackTone() {
        this.mRingbackRegistrants.notifyRegistrants(new AsyncResult(null, Boolean.TRUE, null));
    }

    /* Access modifiers changed, original: protected */
    public void stopRingbackTone() {
        this.mRingbackRegistrants.notifyRegistrants(new AsyncResult(null, Boolean.FALSE, null));
    }

    public void unregisterForRingbackTone(Handler handler) {
        this.mRingbackRegistrants.remove(handler);
    }

    public void unregisterForSuppServiceNotification(Handler handler) {
    }

    /* Access modifiers changed, original: 0000 */
    public boolean updateCurrentCarrierInProvider() {
        return false;
    }

    /* Access modifiers changed, original: 0000 */
    public void updatePhoneState() {
        State state = this.mState;
        if (getRingingCall().isRinging()) {
            this.mState = State.RINGING;
        } else if (getForegroundCall().isIdle() && getBackgroundCall().isIdle()) {
            this.mState = State.IDLE;
        } else {
            this.mState = State.OFFHOOK;
        }
        if (this.mState != state) {
            Rlog.d(LOG_TAG, " ^^^ new phone state: " + this.mState);
            notifyPhoneStateChanged();
        }
    }

    public void updateServiceLocation() {
    }
}
