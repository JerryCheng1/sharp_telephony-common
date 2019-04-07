package com.android.internal.telephony.gsm;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Telephony.Carriers;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.ims.ImsManager;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DctConstants.Activity;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.Phone.DataActivityState;
import com.android.internal.telephony.Phone.SuppService;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccVmNotSupportedException;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.IsimUiccRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data;

public class GSMPhone extends PhoneBase {
    public static final String CIPHERING_KEY = "ciphering_key";
    private static final boolean LOCAL_DEBUG = true;
    static final String LOG_TAG = "GSMPhone";
    public static final int MAX_RECEIVED_ID_LIST = 32;
    public static final int MESSAGE_ID_CBS_EMERGENCY = 40960;
    public static final int MESSAGE_ID_CBS_TEST_MESSAGE = 40963;
    private static final boolean VDBG = false;
    public static final String VM_NUMBER = "vm_number_key";
    GsmCallTracker mCT;
    private final RegistrantList mEcmTimerResetRegistrants;
    private String mImei;
    private String mImeiSv;
    private IsimUiccRecords mIsimUiccRecords;
    ArrayList<GsmMmiCode> mPendingMMIs;
    Registrant mPostDialHandler;
    private ArrayList<AreaMailIds> mReceivedCbsList;
    private ArrayList<AreaMailIds> mReceivedEtwsList;
    GsmServiceStateTracker mSST;
    protected SimPhoneBookInterfaceManager mSimPhoneBookIntManager;
    RegistrantList mSsnRegistrants;
    PhoneSubInfo mSubInfo;
    private String mVmNumber;

    /* renamed from: com.android.internal.telephony.gsm.GSMPhone$1 */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$Activity = new int[Activity.values().length];
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DATAIN.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DATAOUT.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DATAINANDOUT.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[Activity.DORMANT.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.RETRYING.ordinal()] = 1;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.FAILED.ordinal()] = 2;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.IDLE.ordinal()] = 3;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTED.ordinal()] = 4;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.DISCONNECTING.ordinal()] = 5;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTING.ordinal()] = 6;
            } catch (NoSuchFieldError e10) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.SCANNING.ordinal()] = 7;
            } catch (NoSuchFieldError e11) {
            }
        }
    }

    private class AreaMailIds {
        int geographicalScope;
        int messageIdentifier;
        int serialNumber;

        public AreaMailIds(SmsCbHeader smsCbHeader) {
            if (TelBrand.IS_DCM) {
                this.geographicalScope = smsCbHeader.getGeographicalScope();
                this.serialNumber = smsCbHeader.getSerialNumber();
                this.messageIdentifier = smsCbHeader.getServiceCategory();
                if (!smsCbHeader.isEtwsPrimaryNotification() && 4352 <= this.messageIdentifier && this.messageIdentifier <= SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING) {
                    this.messageIdentifier = GSMPhone.MESSAGE_ID_CBS_EMERGENCY;
                }
            }
        }

        public int getSerialNo() {
            return TelBrand.IS_DCM ? this.serialNumber : -1;
        }

        public boolean isSame(AreaMailIds areaMailIds) {
            return TelBrand.IS_DCM && this.messageIdentifier == areaMailIds.messageIdentifier && this.geographicalScope == areaMailIds.geographicalScope && this.serialNumber == areaMailIds.serialNumber;
        }
    }

    private static class Cfu {
        final Message mOnComplete;
        final String mSetCfNumber;

        Cfu(String str, Message message) {
            this.mSetCfNumber = str;
            this.mOnComplete = message;
        }
    }

    public class DialData {
        public int clirMode;
        public String dialString;
        public boolean isTemporaryModeCLIR;
        public GsmMmiCode mmi;

        public DialData(String str, int i, boolean z, GsmMmiCode gsmMmiCode) {
            this.dialString = str;
            this.clirMode = i;
            this.isTemporaryModeCLIR = z;
            this.mmi = gsmMmiCode;
            Rlog.d(GSMPhone.LOG_TAG, "DialData() dialString=" + this.dialString + ", clirMode=" + this.clirMode + ", isClir=" + z + ", mmi=" + gsmMmiCode);
        }
    }

    public GSMPhone(Context context, CommandsInterface commandsInterface, PhoneNotifier phoneNotifier, int i) {
        this(context, commandsInterface, phoneNotifier, false, i);
    }

    public GSMPhone(Context context, CommandsInterface commandsInterface, PhoneNotifier phoneNotifier, boolean z) {
        super("GSM", phoneNotifier, context, commandsInterface, z);
        this.mPendingMMIs = new ArrayList();
        this.mSsnRegistrants = new RegistrantList();
        this.mEcmTimerResetRegistrants = new RegistrantList();
        this.mReceivedEtwsList = new ArrayList();
        this.mReceivedCbsList = new ArrayList();
        if (commandsInterface instanceof SimulatedRadioControl) {
            this.mSimulatedRadioControl = (SimulatedRadioControl) commandsInterface;
        }
        this.mUiccController.setPhone(this);
        this.mCi.setPhoneType(1);
        this.mCT = new GsmCallTracker(this);
        this.mSST = new GsmServiceStateTracker(this);
        this.mDcTracker = new DcTracker(this);
        if (!z) {
            this.mSimPhoneBookIntManager = new SimPhoneBookInterfaceManager(this);
            this.mSubInfo = new PhoneSubInfo(this);
        }
        this.mCi.registerForAvailable(this, 1, null);
        this.mCi.registerForOffOrNotAvailable(this, 8, null);
        this.mCi.registerForOn(this, 5, null);
        this.mCi.setOnUSSD(this, 7, null);
        this.mCi.setOnSuppServiceNotification(this, 2, null);
        this.mSST.registerForNetworkAttached(this, 19, null);
        this.mCi.setOnSs(this, 36, null);
        setProperties();
    }

    public GSMPhone(Context context, CommandsInterface commandsInterface, PhoneNotifier phoneNotifier, boolean z, int i) {
        super("GSM", phoneNotifier, context, commandsInterface, z, i);
        this.mPendingMMIs = new ArrayList();
        this.mSsnRegistrants = new RegistrantList();
        this.mEcmTimerResetRegistrants = new RegistrantList();
        this.mReceivedEtwsList = new ArrayList();
        this.mReceivedCbsList = new ArrayList();
        if (commandsInterface instanceof SimulatedRadioControl) {
            this.mSimulatedRadioControl = (SimulatedRadioControl) commandsInterface;
        }
        this.mUiccController.setPhone(this);
        this.mCi.setPhoneType(1);
        this.mCT = new GsmCallTracker(this);
        this.mSST = new GsmServiceStateTracker(this);
        this.mDcTracker = new DcTracker(this);
        if (!z) {
            this.mSimPhoneBookIntManager = new SimPhoneBookInterfaceManager(this);
            this.mSubInfo = new PhoneSubInfo(this);
        }
        this.mCi.registerForAvailable(this, 1, null);
        this.mCi.registerForOffOrNotAvailable(this, 8, null);
        this.mCi.registerForOn(this, 5, null);
        this.mCi.setOnUSSD(this, 7, null);
        this.mCi.setOnSuppServiceNotification(this, 2, null);
        this.mSST.registerForNetworkAttached(this, 19, null);
        this.mCi.setOnSs(this, 36, null);
        setProperties();
        log("GSMPhone: constructor: sub = " + this.mPhoneId);
        setProperties();
    }

    private ArrayList<String> getDialNumberInfo(String str, int i) {
        ArrayList<String> arrayList = new ArrayList();
        if (TelBrand.IS_DCM) {
            Context context = getContext();
            boolean z = context != null ? context.getSharedPreferences("network_service_settings_private", 0).getBoolean("sub_address_setting", true) : false;
            Rlog.d(LOG_TAG, "dialString is" + str + " isSubAddress is " + z + " prefix is " + i);
            arrayList.add(str);
            arrayList.add(null);
            if (z) {
                String substring = str.substring(i);
                int length = substring.length();
                if (length != 0) {
                    int i2 = 0;
                    while (i2 < length && substring.charAt(i2) == '*') {
                        i2++;
                    }
                    if (substring.substring(i2).length() != 0) {
                        int indexOf = substring.substring(i2).indexOf(42);
                        if (indexOf > 0) {
                            indexOf += i2 + i;
                            Object substring2 = str.substring(0, indexOf);
                            substring = str.substring(indexOf + 1);
                            if (!substring.equals("")) {
                                substring2 = substring2 + "&" + substring;
                            }
                            arrayList.set(0, substring2);
                        }
                        Rlog.d(LOG_TAG, " dialInfoList.get(0) is " + ((String) arrayList.get(0)) + ")");
                        return arrayList;
                    }
                }
            }
        }
        return arrayList;
    }

    private int getStoredVoiceMessageCount() {
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        String string = defaultSharedPreferences.getString(PhoneBase.VM_ID + getSubId(), null);
        String subscriberId = getSubscriberId();
        Rlog.d(LOG_TAG, "Voicemail count retrieval for Imsi = xxxxxx, current Imsi = xxxxxx");
        if (string == null || subscriberId == null || !subscriberId.equals(string)) {
            return 0;
        }
        int i = defaultSharedPreferences.getInt(PhoneBase.VM_COUNT + getSubId(), 0);
        Rlog.d(LOG_TAG, "Voice Mail Count from preference = " + i);
        return i;
    }

    private boolean handleCallDeflectionIncallSupplementaryService(String str) {
        if (str.length() > 1) {
            return false;
        }
        if (getRingingCall().getState() != Call.State.IDLE) {
            Rlog.d(LOG_TAG, "MmiCode 0: rejectCall");
            try {
                this.mCT.rejectCall();
                return true;
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "reject failed", e);
                notifySuppServiceFailed(SuppService.REJECT);
                return true;
            }
        } else if (getBackgroundCall().getState() == Call.State.IDLE) {
            return true;
        } else {
            Rlog.d(LOG_TAG, "MmiCode 0: hangupWaitingOrBackground");
            this.mCT.hangupWaitingOrBackground();
            return true;
        }
    }

    private boolean handleCallHoldIncallSupplementaryService(String str) {
        int length = str.length();
        if (length > 2) {
            return false;
        }
        GsmCall foregroundCall = getForegroundCall();
        if (length > 1) {
            try {
                length = str.charAt(1) - 48;
                GsmConnection connectionByIndex = this.mCT.getConnectionByIndex(foregroundCall, length);
                if (connectionByIndex == null || length < 1 || length > 7) {
                    Rlog.d(LOG_TAG, "separate: invalid call index " + length);
                    notifySuppServiceFailed(SuppService.SEPARATE);
                    return true;
                }
                Rlog.d(LOG_TAG, "MmiCode 2: separate call " + length);
                this.mCT.separate(connectionByIndex);
                return true;
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "separate failed", e);
                notifySuppServiceFailed(SuppService.SEPARATE);
                return true;
            }
        }
        try {
            if (getRingingCall().getState() != Call.State.IDLE) {
                Rlog.d(LOG_TAG, "MmiCode 2: accept ringing call");
                this.mCT.acceptCall();
                return true;
            }
            Rlog.d(LOG_TAG, "MmiCode 2: switchWaitingOrHoldingAndActive");
            this.mCT.switchWaitingOrHoldingAndActive();
            return true;
        } catch (CallStateException e2) {
            Rlog.d(LOG_TAG, "switch failed", e2);
            notifySuppServiceFailed(SuppService.SWITCH);
            return true;
        }
    }

    private boolean handleCallWaitingIncallSupplementaryService(String str) {
        int length = str.length();
        if (length > 2) {
            return false;
        }
        GsmCall foregroundCall = getForegroundCall();
        if (length > 1) {
            try {
                length = str.charAt(1) - 48;
                if (length < 1 || length > 7) {
                    return true;
                }
                Rlog.d(LOG_TAG, "MmiCode 1: hangupConnectionByIndex " + length);
                this.mCT.hangupConnectionByIndex(foregroundCall, length);
                return true;
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "hangup failed", e);
                notifySuppServiceFailed(SuppService.HANGUP);
                return true;
            }
        } else if (foregroundCall.getState() != Call.State.IDLE) {
            Rlog.d(LOG_TAG, "MmiCode 1: hangup foreground");
            this.mCT.hangup(foregroundCall);
            return true;
        } else {
            Rlog.d(LOG_TAG, "MmiCode 1: switchWaitingOrHoldingAndActive");
            this.mCT.switchWaitingOrHoldingAndActive();
            return true;
        }
    }

    private boolean handleCcbsIncallSupplementaryService(String str) {
        if (str.length() > 1) {
            return false;
        }
        Rlog.i(LOG_TAG, "MmiCode 5: CCBS not supported!");
        notifySuppServiceFailed(SuppService.UNKNOWN);
        return true;
    }

    private void handleCfuQueryResult(CallForwardInfo[] callForwardInfoArr) {
        boolean z = false;
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords == null) {
            return;
        }
        if (callForwardInfoArr == null || callForwardInfoArr.length == 0) {
            iccRecords.setVoiceCallForwardingFlag(1, false, null);
            return;
        }
        int length = callForwardInfoArr.length;
        for (int i = 0; i < length; i++) {
            if ((callForwardInfoArr[i].serviceClass & 1) != 0) {
                setCallForwardingPreference(callForwardInfoArr[i].status == 1);
                if (callForwardInfoArr[i].status == 1) {
                    z = true;
                }
                iccRecords.setVoiceCallForwardingFlag(1, z, callForwardInfoArr[i].number);
                return;
            }
        }
    }

    private boolean handleEctIncallSupplementaryService(String str) {
        if (str.length() != 1) {
            return false;
        }
        Rlog.d(LOG_TAG, "MmiCode 4: explicit call transfer");
        explicitCallTransfer();
        return true;
    }

    private boolean handleMultipartyIncallSupplementaryService(String str) {
        if (str.length() > 1) {
            return false;
        }
        Rlog.d(LOG_TAG, "MmiCode 3: merge calls");
        conference();
        return true;
    }

    private boolean isReceivedCbsMessage(AreaMailIds areaMailIds) {
        if (!TelBrand.IS_DCM) {
            return false;
        }
        int size = this.mReceivedCbsList.size();
        Rlog.d(LOG_TAG, "Received CBS List has " + size + " element(s).");
        for (int i = 0; i < size; i++) {
            if (true == areaMailIds.isSame((AreaMailIds) this.mReceivedCbsList.get(i))) {
                Rlog.d(LOG_TAG, "this CBS has been already received. matches received CBS: MessageID=" + areaMailIds.messageIdentifier + "(should be 40960), SerialNo=" + areaMailIds.getSerialNo());
                return true;
            }
        }
        return false;
    }

    private boolean isReceivedEmergencyMessage() {
        if (!TelBrand.IS_DCM) {
            return false;
        }
        int size = this.mReceivedCbsList.size();
        for (int i = 0; i < size; i++) {
            if (MESSAGE_ID_CBS_EMERGENCY == ((AreaMailIds) this.mReceivedCbsList.get(i)).messageIdentifier) {
                Rlog.d(LOG_TAG, "isReceivedCbsMessage() 0xA000 has been already received.");
                return true;
            }
        }
        return false;
    }

    private boolean isReceivedEtwsMessage(AreaMailIds areaMailIds) {
        if (!TelBrand.IS_DCM) {
            return false;
        }
        int size = this.mReceivedEtwsList.size();
        Rlog.d(LOG_TAG, "Received ETWS List has " + size + " element(s).");
        for (int i = 0; i < size; i++) {
            if (true == areaMailIds.isSame((AreaMailIds) this.mReceivedEtwsList.get(i))) {
                Rlog.d(LOG_TAG, "this ETWS has been already received. matches received ETWS: MessageID=" + areaMailIds.messageIdentifier + ", SerialNo=" + areaMailIds.getSerialNo());
                return true;
            }
        }
        return (4352 > areaMailIds.messageIdentifier || areaMailIds.messageIdentifier > SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING) ? isReceivedCbsMessage(areaMailIds) : isReceivedEmergencyMessage();
    }

    private boolean isValidCommandInterfaceCFAction(int i) {
        switch (i) {
            case 0:
            case 1:
            case 3:
            case 4:
                return true;
            default:
                return false;
        }
    }

    private boolean isValidCommandInterfaceCFReason(int i) {
        switch (i) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return true;
            default:
                return false;
        }
    }

    private boolean isValidFacilityString(String str) {
        if (str.equals(CommandsInterface.CB_FACILITY_BAOC) || str.equals(CommandsInterface.CB_FACILITY_BAOIC) || str.equals(CommandsInterface.CB_FACILITY_BAOICxH) || str.equals(CommandsInterface.CB_FACILITY_BAIC) || str.equals(CommandsInterface.CB_FACILITY_BAICr) || str.equals(CommandsInterface.CB_FACILITY_BA_ALL) || str.equals(CommandsInterface.CB_FACILITY_BA_MO) || str.equals(CommandsInterface.CB_FACILITY_BA_MT) || str.equals(CommandsInterface.CB_FACILITY_BA_SIM) || str.equals(CommandsInterface.CB_FACILITY_BA_FD)) {
            return true;
        }
        Rlog.e(LOG_TAG, " Invalid facility String : " + str);
        return false;
    }

    private void onIncomingUSSD(int i, String str) {
        GsmMmiCode gsmMmiCode;
        int i2 = 0;
        boolean z = i == 1;
        int i3 = (i == 0 || i == 1) ? 0 : 1;
        int i4 = i == 2 ? 1 : 0;
        int size = this.mPendingMMIs.size();
        while (i2 < size) {
            if (((GsmMmiCode) this.mPendingMMIs.get(i2)).isPendingUSSD()) {
                gsmMmiCode = (GsmMmiCode) this.mPendingMMIs.get(i2);
                break;
            }
            i2++;
        }
        gsmMmiCode = null;
        if (gsmMmiCode != null) {
            if (i4 != 0) {
                gsmMmiCode.onUssdRelease();
            } else if (i3 != 0) {
                gsmMmiCode.onUssdFinishedError();
            } else {
                gsmMmiCode.onUssdFinished(str, z);
            }
        } else if (i3 == 0 && str != null) {
            onNetworkInitiatedUssd(GsmMmiCode.newNetworkInitiatedUssd(str, z, this, (UiccCardApplication) this.mUiccApplication.get()));
        }
    }

    private void onNetworkInitiatedUssd(GsmMmiCode gsmMmiCode) {
        this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, gsmMmiCode, null));
    }

    private void processIccRecordEvents(int i) {
        switch (i) {
            case 1:
                notifyCallForwardingIndicator();
                return;
            default:
                return;
        }
    }

    private void registerForSimRecordEvents() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords != null) {
            iccRecords.registerForNetworkSelectionModeAutomatic(this, 28, null);
            iccRecords.registerForRecordsEvents(this, 29, null);
            iccRecords.registerForRecordsLoaded(this, 3, null);
        }
    }

    private void storeVoiceMailNumber(String str) {
        Editor edit = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        edit.putString(VM_NUMBER + getSubId(), str);
        edit.apply();
        setSimImsi(getSubscriberId());
    }

    private void unregisterForSimRecordEvents() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords != null) {
            iccRecords.unregisterForNetworkSelectionModeAutomatic(this);
            iccRecords.unregisterForRecordsEvents(this);
            iccRecords.unregisterForRecordsLoaded(this);
        }
    }

    private void updateCallForwardStatus() {
        Rlog.d(LOG_TAG, "updateCallForwardStatus got sim records");
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords == null || !iccRecords.isCallForwardStatusStored()) {
            sendMessage(obtainMessage(39));
            return;
        }
        Rlog.d(LOG_TAG, "Callforwarding info is present on sim");
        notifyCallForwardingIndicator();
    }

    private void updateVoiceMail() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        int voiceMessageCount = iccRecords != null ? iccRecords.getVoiceMessageCount() : 0;
        if (voiceMessageCount == -2) {
            voiceMessageCount = getStoredVoiceMessageCount();
        }
        Rlog.d(LOG_TAG, "updateVoiceMail countVoiceMessages = " + voiceMessageCount + " subId " + getSubId());
        setVoiceMessageCount(voiceMessageCount);
    }

    public void acceptCall(int i) throws CallStateException {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || !imsPhone.getRingingCall().isRinging()) {
            this.mCT.acceptCall();
        } else {
            imsPhone.acceptCall(i);
        }
    }

    public void activateCellBroadcastSms(int i, Message message) {
        Rlog.e(LOG_TAG, "[GSMPhone] activateCellBroadcastSms() is obsolete; use SmsManager");
        message.sendToTarget();
    }

    public void addParticipant(String str) throws CallStateException {
        ImsPhone imsPhone = this.mImsPhone;
        Object obj = (ImsManager.isVolteEnabledByPlatform(this.mContext) && ImsManager.isEnhanced4gLteModeSettingEnabledByUser(this.mContext)) ? 1 : null;
        if (obj == null) {
            Rlog.w(LOG_TAG, "IMS is disabled: forced to CS");
        }
        if (obj == null || imsPhone == null || !imsPhone.isVolteEnabled() || imsPhone.getServiceState().getState() != 0) {
            Rlog.e(LOG_TAG, "IMS is disabled so unable to add participant with IMS call");
            return;
        }
        try {
            Rlog.d(LOG_TAG, "Trying to add participant in IMS call");
            imsPhone.addParticipant(str);
        } catch (CallStateException e) {
            Rlog.d(LOG_TAG, "IMS PS call exception " + e);
        }
    }

    public boolean canConference() {
        return this.mCT.canConference() || (this.mImsPhone != null ? this.mImsPhone.canConference() : false);
    }

    public boolean canDial() {
        return this.mCT.canDial();
    }

    public boolean canTransfer() {
        return this.mCT.canTransfer();
    }

    public void clearDisconnected() {
        this.mCT.clearDisconnected();
    }

    public void conference() {
        if (this.mImsPhone == null || !this.mImsPhone.canConference()) {
            this.mCT.conference();
            return;
        }
        log("conference() - delegated to IMS phone");
        this.mImsPhone.conference();
    }

    public void deflectCall(String str) throws CallStateException {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || !imsPhone.getRingingCall().isRinging()) {
            throw new CallStateException("Deflect call NOT supported in GSM!");
        }
        imsPhone.deflectCall(str);
    }

    public Connection dial(String str, int i) throws CallStateException {
        return dial(str, null, i, null);
    }

    public Connection dial(String str, int i, int i2) throws CallStateException {
        return TelBrand.IS_DCM ? dial(str, null, i, null, i2) : null;
    }

    public Connection dial(String str, int i, Bundle bundle) throws CallStateException {
        return dial(str, null, i, bundle);
    }

    public Connection dial(String str, int i, Bundle bundle, int i2) throws CallStateException {
        return TelBrand.IS_DCM ? dial(str, null, i, bundle, i2) : null;
    }

    public Connection dial(String str, UUSInfo uUSInfo, int i) throws CallStateException {
        return dial(str, uUSInfo, i, null);
    }

    public Connection dial(String str, UUSInfo uUSInfo, int i, int i2) throws CallStateException {
        return TelBrand.IS_DCM ? dial(str, uUSInfo, i, null, i2) : null;
    }

    public Connection dial(String str, UUSInfo uUSInfo, int i, Bundle bundle) throws CallStateException {
        return dial(str, uUSInfo, i, bundle, 0);
    }

    public Connection dial(String str, UUSInfo uUSInfo, int i, Bundle bundle, int i2) throws CallStateException {
        CallStateException callStateException;
        boolean z = true;
        ImsPhone imsPhone = this.mImsPhone;
        boolean z2 = ImsManager.isVolteEnabledByPlatform(this.mContext) && ImsManager.isEnhanced4gLteModeSettingEnabledByUser(this.mContext) && ImsManager.isNonTtyOrTtyOnVolteEnabled(this.mContext) && imsPhone != null && imsPhone.isVolteEnabled() && imsPhone.getServiceState().getState() == 0;
        if (!(imsPhone != null && PhoneNumberUtils.isEmergencyNumber(str) && this.mContext.getResources().getBoolean(17956996) && ImsManager.isNonTtyOrTtyOnVolteEnabled(this.mContext) && imsPhone.getServiceState().getState() != 3)) {
            z = false;
        }
        Rlog.d(LOG_TAG, "imsUseEnabled = " + z2 + ", useImsForEmergency = " + z);
        if (z2 || z) {
            try {
                Rlog.d(LOG_TAG, "Trying IMS PS call");
                return TelBrand.IS_DCM ? imsPhone.dial(str, i, bundle, i2) : imsPhone.dial(str, i, bundle);
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "IMS PS call exception " + e + "imsUseEnabled =" + z2 + ", imsPhone =" + imsPhone);
                if (!ImsPhone.CS_FALLBACK.equals(e.getMessage())) {
                    callStateException = new CallStateException(e.getMessage());
                    callStateException.setStackTrace(e.getStackTrace());
                    throw callStateException;
                }
            }
        }
        if (imsPhone != null && imsPhone.isUtEnabled() && str.endsWith("#")) {
            try {
                Rlog.d(LOG_TAG, "Trying IMS call with UT enabled");
                return TelBrand.IS_DCM ? imsPhone.dial(str, i, bundle, i2) : imsPhone.dial(str, i, bundle);
            } catch (CallStateException e2) {
                Rlog.d(LOG_TAG, "IMS call UT enabled exception " + e2);
                if (!ImsPhone.CS_FALLBACK.equals(e2.getMessage())) {
                    callStateException = new CallStateException(e2.getMessage());
                    callStateException.setStackTrace(e2.getStackTrace());
                    throw callStateException;
                }
            }
        }
        Rlog.d(LOG_TAG, "Trying (non-IMS) CS call");
        return TelBrand.IS_DCM ? dialInternal(str, null, 0, i2) : dialInternal(str, null, 0);
    }

    /* Access modifiers changed, original: protected */
    public Connection dialInternal(String str, UUSInfo uUSInfo, int i) throws CallStateException {
        return dialInternal(str, uUSInfo, i, 0);
    }

    /* Access modifiers changed, original: protected */
    public Connection dialInternal(String str, UUSInfo uUSInfo, int i, int i2) throws CallStateException {
        if (TelBrand.IS_SBM) {
            DialData dialData = getDialData(str);
            if (dialData == null) {
                return null;
            }
            Rlog.d(LOG_TAG, "dialing w/ mmi '" + dialData.mmi + "'...");
            if (dialData.mmi == null) {
                return this.mCT.dial(dialData.dialString, uUSInfo);
            }
            if (dialData.isTemporaryModeCLIR) {
                return this.mCT.dial(dialData.dialString, dialData.clirMode, uUSInfo);
            }
            this.mPendingMMIs.add(dialData.mmi);
            this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, dialData.mmi, null));
            dialData.mmi.processCode();
            return null;
        }
        String stripSeparators = PhoneNumberUtils.stripSeparators(str);
        if (handleInCallMmiCommands(stripSeparators)) {
            return null;
        }
        GsmMmiCode newFromDialString = GsmMmiCode.newFromDialString(PhoneNumberUtils.extractNetworkPortionAlt(stripSeparators), this, (UiccCardApplication) this.mUiccApplication.get());
        Rlog.d(LOG_TAG, "dialing w/ mmi '" + newFromDialString + "'...");
        if (TelBrand.IS_DCM) {
            Rlog.d(LOG_TAG, "dialString is" + str + " prefix is " + i2);
        }
        if (newFromDialString == null) {
            if (!TelBrand.IS_DCM) {
                return this.mCT.dial(stripSeparators, uUSInfo);
            }
            return this.mCT.dial((String) getDialNumberInfo(stripSeparators, i2).get(0), uUSInfo);
        } else if (!newFromDialString.isTemporaryModeCLIR()) {
            this.mPendingMMIs.add(newFromDialString);
            this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, newFromDialString, null));
            newFromDialString.processCode();
            return null;
        } else if (!TelBrand.IS_DCM) {
            return this.mCT.dial(newFromDialString.mDialingNumber, newFromDialString.getCLIRMode(), uUSInfo);
        } else {
            return this.mCT.dial((String) getDialNumberInfo(newFromDialString.mDialingNumber, i2 > newFromDialString.mPoundString.length() ? i2 - newFromDialString.mPoundString.length() : 0).get(0), newFromDialString.getCLIRMode(), uUSInfo);
        }
    }

    public void disableLocationUpdates() {
        this.mSST.disableLocationUpdates();
    }

    public void dispose() {
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();
            this.mCi.unregisterForAvailable(this);
            unregisterForSimRecordEvents();
            this.mUiccController.setPhone(null);
            this.mCi.unregisterForOffOrNotAvailable(this);
            this.mCi.unregisterForOn(this);
            this.mSST.unregisterForNetworkAttached(this);
            this.mCi.unSetOnUSSD(this);
            this.mCi.unSetOnSuppServiceNotification(this);
            this.mCi.unSetOnSs(this);
            this.mPendingMMIs.clear();
            if (TelBrand.IS_DCM) {
                this.mReceivedEtwsList.clear();
                this.mReceivedCbsList.clear();
            }
            this.mCT.dispose();
            this.mDcTracker.dispose();
            this.mSST.dispose();
            this.mSimPhoneBookIntManager.dispose();
            this.mSubInfo.dispose();
        }
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("GSMPhone extends:");
        super.dump(fileDescriptor, printWriter, strArr);
        printWriter.println(" mCT=" + this.mCT);
        printWriter.println(" mSST=" + this.mSST);
        printWriter.println(" mPendingMMIs=" + this.mPendingMMIs);
        printWriter.println(" mSimPhoneBookIntManager=" + this.mSimPhoneBookIntManager);
        printWriter.println(" mSubInfo=" + this.mSubInfo);
        printWriter.println(" mVmNumber=" + this.mVmNumber);
    }

    public void enableLocationUpdates() {
        this.mSST.enableLocationUpdates();
    }

    public void exitEmergencyCallbackMode() {
        if (this.mImsPhone != null) {
            this.mImsPhone.exitEmergencyCallbackMode();
        }
    }

    public void explicitCallTransfer() {
        this.mCT.explicitCallTransfer();
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        Rlog.d(LOG_TAG, "GSMPhone finalized");
    }

    public void getAvailableNetworks(Message message) {
        this.mCi.getAvailableNetworks(message);
    }

    public GsmCall getBackgroundCall() {
        return this.mCT.mBackgroundCall;
    }

    public ServiceState getBaseServiceState() {
        return this.mSST != null ? this.mSST.mSS : new ServiceState();
    }

    public int getBrand() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        return iccRecords != null ? iccRecords.getBrand() : 0;
    }

    public void getCallBarring(String str, Message message) {
        Rlog.e(LOG_TAG, "getCallBarring: not possible in GSM");
    }

    public void getCallBarring(String str, String str2, int i, Message message) {
        this.mCi.queryFacilityLock(str, str2, i, message);
    }

    public void getCallBarring(String str, String str2, Message message) {
        this.mCi.queryFacilityLock(str, str2, 1, message);
    }

    public void getCallBarringOption(String str, String str2, Message message) {
        if (isValidFacilityString(str)) {
            this.mCi.queryFacilityLock(str, str2, 0, message);
        }
    }

    public boolean getCallForwardingIndicator() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        return (iccRecords == null || !iccRecords.isCallForwardStatusStored()) ? getCallForwardingPreference() : iccRecords.getVoiceCallForwardingFlag();
    }

    public void getCallForwardingOption(int i, int i2, Message message) {
        if (isValidCommandInterfaceCFReason(i)) {
            Rlog.d(LOG_TAG, "requesting call forwarding query.");
            if (i == 0) {
                message = obtainMessage(13, message);
            }
            this.mCi.queryCallForwardStatus(i, i2, null, message);
        }
    }

    public void getCallForwardingOption(int i, Message message) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null && (imsPhone.getServiceState().getState() == 0 || imsPhone.isUtEnabled())) {
            imsPhone.getCallForwardingOption(i, message);
        } else if (isValidCommandInterfaceCFReason(i)) {
            Rlog.d(LOG_TAG, "requesting call forwarding query.");
            if (i == 0) {
                message = obtainMessage(13, message);
            }
            this.mCi.queryCallForwardStatus(i, 0, null, message);
        }
    }

    public void getCallForwardingUncondTimerOption(int i, Message message) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null && (imsPhone.getServiceState().getState() == 0 || imsPhone.isUtEnabled())) {
            imsPhone.getCallForwardingOption(i, message);
        } else if (message != null) {
            AsyncResult.forMessage(message, null, new CommandException(Error.GENERIC_FAILURE));
            message.sendToTarget();
        }
    }

    public CallTracker getCallTracker() {
        return this.mCT;
    }

    public void getCallWaiting(int i, Message message) {
        this.mCi.queryCallWaiting(i, message);
    }

    public void getCallWaiting(Message message) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || !(imsPhone.getServiceState().getState() == 0 || imsPhone.isUtEnabled())) {
            this.mCi.queryCallWaiting(0, message);
        } else {
            imsPhone.getCallWaiting(message);
        }
    }

    public void getCellBroadcastSmsConfig(Message message) {
        Rlog.e(LOG_TAG, "[GSMPhone] getCellBroadcastSmsConfig() is obsolete; use SmsManager");
        message.sendToTarget();
    }

    public CellLocation getCellLocation() {
        return this.mSST.getCellLocation();
    }

    public DataActivityState getDataActivityState() {
        DataActivityState dataActivityState = DataActivityState.NONE;
        if (this.mSST.getCurrentDataConnectionState() != 0) {
            return dataActivityState;
        }
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$DctConstants$Activity[this.mDcTracker.getActivity().ordinal()]) {
            case 1:
                return DataActivityState.DATAIN;
            case 2:
                return DataActivityState.DATAOUT;
            case 3:
                return DataActivityState.DATAINANDOUT;
            case 4:
                return DataActivityState.DORMANT;
            default:
                return DataActivityState.NONE;
        }
    }

    public void getDataCallList(Message message) {
        this.mCi.getDataCallList(message);
    }

    public DataState getDataConnectionState(String str) {
        DataState dataState = DataState.DISCONNECTED;
        if (this.mSST == null) {
            return DataState.DISCONNECTED;
        }
        if (!str.equals("emergency") && this.mSST.getCurrentDataConnectionState() != 0 && this.mOosIsDisconnect) {
            dataState = DataState.DISCONNECTED;
            Rlog.d(LOG_TAG, "getDataConnectionState: Data is Out of Service. ret = " + dataState);
            return dataState;
        } else if (!this.mDcTracker.isApnTypeEnabled(str) || !this.mDcTracker.isApnTypeActive(str)) {
            return DataState.DISCONNECTED;
        } else {
            switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$DctConstants$State[this.mDcTracker.getState(str).ordinal()]) {
                case 1:
                case 2:
                case 3:
                    return DataState.DISCONNECTED;
                case 4:
                case 5:
                    return (this.mCT.mState == PhoneConstants.State.IDLE || this.mSST.isConcurrentVoiceAndDataAllowed()) ? DataState.CONNECTED : DataState.SUSPENDED;
                case 6:
                case 7:
                    return DataState.CONNECTING;
                default:
                    return dataState;
            }
        }
    }

    public boolean getDataEnabled() {
        return this.mDcTracker.getDataEnabled();
    }

    public boolean getDataRoamingEnabled() {
        return this.mDcTracker.getDataOnRoamingEnabled();
    }

    public String getDeviceId() {
        return this.mImei;
    }

    public String getDeviceSvn() {
        return this.mImeiSv;
    }

    public DialData getDialData(String str) throws CallStateException {
        String stripSeparators = PhoneNumberUtils.stripSeparators(str);
        if (handleInCallMmiCommands(stripSeparators)) {
            Rlog.d(LOG_TAG, "getDialData() return null, handleInCallMmiCommands() is false.");
            return null;
        }
        GsmMmiCode newFromDialString = GsmMmiCode.newFromDialString(PhoneNumberUtils.extractNetworkPortionAlt(stripSeparators), this, (UiccCardApplication) this.mUiccApplication.get());
        if (newFromDialString == null) {
            return new DialData(stripSeparators, 0, false, null);
        }
        if (!newFromDialString.isTemporaryModeCLIR()) {
            return new DialData(null, 0, false, newFromDialString);
        }
        return new DialData(newFromDialString.mDialingNumber, newFromDialString.getCLIRMode(), true, newFromDialString);
    }

    public String getEsn() {
        Rlog.e(LOG_TAG, "[GSMPhone] getEsn() is a CDMA method");
        return "0";
    }

    public GsmCall getForegroundCall() {
        return this.mCT.mForegroundCall;
    }

    public String getGroupIdLevel1() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        return iccRecords != null ? iccRecords.getGid1() : null;
    }

    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return this.mSimPhoneBookIntManager;
    }

    public String getImei() {
        return this.mImei;
    }

    public String getImsiOnSimLock() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        return iccRecords != null ? iccRecords.getImsiOnSimLock() : null;
    }

    public IsimRecords getIsimRecords() {
        return this.mIsimUiccRecords;
    }

    public String getLine1AlphaTag() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        return iccRecords != null ? iccRecords.getMsisdnAlphaTag() : null;
    }

    public String getLine1Number() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        return iccRecords != null ? iccRecords.getMsisdnNumber() : null;
    }

    public String getMccMncOnSimLock() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        return iccRecords != null ? iccRecords.getMccMncOnSimLock() : null;
    }

    public String getMeid() {
        Rlog.e(LOG_TAG, "[GSMPhone] getMeid() is a CDMA method");
        return "0";
    }

    public String getMsisdn() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        return iccRecords != null ? iccRecords.getMsisdnNumber() : null;
    }

    public boolean getMute() {
        return this.mCT.getMute();
    }

    public String getNai() {
        IccRecords iccRecords = this.mUiccController.getIccRecords(this.mPhoneId, 2);
        if (Log.isLoggable(LOG_TAG, 2)) {
            Rlog.v(LOG_TAG, "IccRecords is " + iccRecords);
        }
        return iccRecords != null ? iccRecords.getNAI() : null;
    }

    public void getNeighboringCids(Message message) {
        this.mCi.getNeighboringCids(message);
    }

    public String getOperatorNumeric() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        return iccRecords != null ? iccRecords.getOperatorNumeric() : null;
    }

    public void getOutgoingCallerIdDisplay(Message message) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || !(imsPhone.getServiceState().getState() == 0 || imsPhone.isUtEnabled())) {
            this.mCi.getCLIR(message);
        } else {
            imsPhone.getOutgoingCallerIdDisplay(message);
        }
    }

    public List<? extends MmiCode> getPendingMmiCodes() {
        return this.mPendingMMIs;
    }

    public PhoneSubInfo getPhoneSubInfo() {
        return this.mSubInfo;
    }

    public int getPhoneType() {
        return 1;
    }

    public Call getRingingCall() {
        ImsPhone imsPhone = this.mImsPhone;
        return (this.mCT.mRingingCall == null || !this.mCT.mRingingCall.isRinging()) ? imsPhone != null ? imsPhone.getRingingCall() : this.mCT.mRingingCall : this.mCT.mRingingCall;
    }

    public ServiceState getServiceState() {
        return this.mSST != null ? this.mSST.mSS : new ServiceState();
    }

    public ServiceStateTracker getServiceStateTracker() {
        return this.mSST;
    }

    public PhoneConstants.State getState() {
        if (this.mImsPhone != null) {
            PhoneConstants.State state = this.mImsPhone.getState();
            if (state != PhoneConstants.State.IDLE) {
                return state;
            }
        }
        return this.mCT.mState;
    }

    public String getSubscriberId() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        return iccRecords != null ? iccRecords.getIMSI() : null;
    }

    public String getSystemProperty(String str, String str2) {
        return getUnitTestMode() ? null : TelephonyManager.getTelephonyProperty(this.mPhoneId, str, str2);
    }

    /* Access modifiers changed, original: protected */
    public UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(this.mPhoneId, 1);
    }

    public String getVoiceMailAlphaTag() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        String voiceMailAlphaTag = iccRecords != null ? iccRecords.getVoiceMailAlphaTag() : "";
        return (voiceMailAlphaTag == null || voiceMailAlphaTag.length() == 0) ? this.mContext.getText(17039364).toString() : voiceMailAlphaTag;
    }

    public String getVoiceMailNumber() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        CharSequence voiceMailNumber = iccRecords != null ? iccRecords.getVoiceMailNumber() : "";
        if (TextUtils.isEmpty(voiceMailNumber)) {
            SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
            String string;
            Editor edit;
            if (TelephonyManager.getDefault().isMultiSimEnabled()) {
                if (!defaultSharedPreferences.contains(VM_NUMBER + getSubId()) && defaultSharedPreferences.contains(VM_NUMBER + this.mPhoneId)) {
                    string = defaultSharedPreferences.getString(VM_NUMBER + this.mPhoneId, null);
                    edit = defaultSharedPreferences.edit();
                    edit.remove(VM_NUMBER + this.mPhoneId);
                    edit.putString(VM_NUMBER + getSubId(), string);
                    edit.commit();
                }
            } else if (!defaultSharedPreferences.contains(VM_NUMBER + getSubId()) && defaultSharedPreferences.contains(VM_NUMBER)) {
                string = defaultSharedPreferences.getString(VM_NUMBER, null);
                edit = defaultSharedPreferences.edit();
                edit.remove(VM_NUMBER);
                edit.putString(VM_NUMBER + getSubId(), string);
                edit.commit();
            }
            voiceMailNumber = defaultSharedPreferences.getString(VM_NUMBER + getSubId(), null);
        }
        if (!TextUtils.isEmpty(voiceMailNumber)) {
            return voiceMailNumber;
        }
        String[] stringArray = getContext().getResources().getStringArray(17236028);
        if (stringArray == null || stringArray.length <= 0) {
            return voiceMailNumber;
        }
        CharSequence charSequence = voiceMailNumber;
        for (int i = 0; i < stringArray.length; i++) {
            if (!TextUtils.isEmpty(stringArray[i])) {
                String[] split = stringArray[i].split(";");
                if (split != null && split.length > 0) {
                    if (split.length == 1) {
                        charSequence = split[0];
                    } else if (split.length == 2 && !TextUtils.isEmpty(split[1]) && split[1].equalsIgnoreCase(getGroupIdLevel1())) {
                        return split[0];
                    }
                }
            }
        }
        return charSequence;
    }

    public boolean handleInCallMmiCommands(String str) throws CallStateException {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null && imsPhone.getServiceState().getState() == 0) {
            return imsPhone.handleInCallMmiCommands(str);
        }
        if (!isInCall() || TextUtils.isEmpty(str)) {
            return false;
        }
        switch (str.charAt(0)) {
            case OEM_RIL_RDE_Data.RDE_NV_CDMA_SO68_ENABLED_I /*48*/:
                return handleCallDeflectionIncallSupplementaryService(str);
            case '1':
                return handleCallWaitingIncallSupplementaryService(str);
            case OEM_RIL_RDE_Data.RDE_NV_MOB_TERM_HOME_I /*50*/:
                return handleCallHoldIncallSupplementaryService(str);
            case '3':
                if (!TelBrand.IS_DCM) {
                    return handleMultipartyIncallSupplementaryService(str);
                }
                Rlog.d(LOG_TAG, "Conference avoided!");
                return false;
            case '4':
                return handleEctIncallSupplementaryService(str);
            case '5':
                return handleCcbsIncallSupplementaryService(str);
            default:
                return false;
        }
    }

    public void handleMessage(Message message) {
        boolean z = true;
        switch (message.what) {
            case 16:
            case 17:
                super.handleMessage(message);
                return;
            default:
                if (this.mIsTheCurrentActivePhone) {
                    AsyncResult asyncResult;
                    Message message2;
                    switch (message.what) {
                        case 1:
                            this.mCi.getBasebandVersion(obtainMessage(6));
                            this.mCi.getIMEI(obtainMessage(9));
                            this.mCi.getIMEISV(obtainMessage(10));
                            return;
                        case 2:
                            asyncResult = (AsyncResult) message.obj;
                            SuppServiceNotification suppServiceNotification = (SuppServiceNotification) asyncResult.result;
                            this.mSsnRegistrants.notifyRegistrants(asyncResult);
                            return;
                        case 3:
                            updateCurrentCarrierInProvider();
                            String simImsi = getSimImsi();
                            String subscriberId = getSubscriberId();
                            if (!((TelBrand.IS_SBM || simImsi == null || subscriberId == null || subscriberId.equals(simImsi)) && (!TelBrand.IS_SBM || simImsi == null || subscriberId == null || subscriberId == simImsi))) {
                                storeVoiceMailNumber(null);
                                setCallForwardingPreference(false);
                                setSimImsi(null);
                                SubscriptionController instance = SubscriptionController.getInstance();
                                instance.removeStaleSubPreferences(VM_NUMBER);
                                instance.removeStaleSubPreferences(PhoneBase.SIM_IMSI);
                                instance.removeStaleSubPreferences(PhoneBase.CF_ENABLED);
                            }
                            this.mSimRecordsLoadedRegistrants.notifyRegistrants();
                            updateVoiceMail();
                            updateCallForwardStatus();
                            return;
                        case 5:
                            return;
                        case 6:
                            asyncResult = (AsyncResult) message.obj;
                            if (asyncResult.exception == null) {
                                Rlog.d(LOG_TAG, "Baseband version: " + asyncResult.result);
                                if (SubscriptionManager.isValidPhoneId(this.mPhoneId)) {
                                    SystemProperties.set("gsm.version.baseband" + (this.mPhoneId == 0 ? "" : Integer.toString(this.mPhoneId)), (String) asyncResult.result);
                                    return;
                                }
                                return;
                            }
                            return;
                        case 7:
                            String[] strArr = (String[]) ((AsyncResult) message.obj).result;
                            if (strArr.length > 1) {
                                try {
                                    onIncomingUSSD(Integer.parseInt(strArr[0]), strArr[1]);
                                    return;
                                } catch (NumberFormatException e) {
                                    Rlog.w(LOG_TAG, "error parsing USSD");
                                    return;
                                }
                            }
                            return;
                        case 8:
                            for (int size = this.mPendingMMIs.size() - 1; size >= 0; size--) {
                                if (((GsmMmiCode) this.mPendingMMIs.get(size)).isPendingUSSD()) {
                                    ((GsmMmiCode) this.mPendingMMIs.get(size)).onUssdFinishedError();
                                }
                            }
                            ImsPhone imsPhone = this.mImsPhone;
                            if (imsPhone != null) {
                                imsPhone.getServiceState().setStateOff();
                            }
                            this.mRadioOffOrNotAvailableRegistrants.notifyRegistrants();
                            return;
                        case 9:
                            asyncResult = (AsyncResult) message.obj;
                            if (asyncResult.exception == null) {
                                this.mImei = (String) asyncResult.result;
                                return;
                            }
                            return;
                        case 10:
                            asyncResult = (AsyncResult) message.obj;
                            if (asyncResult.exception == null) {
                                this.mImeiSv = (String) asyncResult.result;
                                return;
                            }
                            return;
                        case 12:
                            asyncResult = (AsyncResult) message.obj;
                            IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
                            Cfu cfu = (Cfu) asyncResult.userObj;
                            if (asyncResult.exception == null && iccRecords != null) {
                                iccRecords.setVoiceCallForwardingFlag(1, message.arg1 == 1, cfu.mSetCfNumber);
                                if (message.arg1 != 1) {
                                    z = false;
                                }
                                setCallForwardingPreference(z);
                            }
                            if (cfu.mOnComplete != null) {
                                AsyncResult.forMessage(cfu.mOnComplete, asyncResult.result, asyncResult.exception);
                                cfu.mOnComplete.sendToTarget();
                                return;
                            }
                            return;
                        case 13:
                            asyncResult = (AsyncResult) message.obj;
                            if (asyncResult.exception == null) {
                                handleCfuQueryResult((CallForwardInfo[]) asyncResult.result);
                            }
                            message2 = (Message) asyncResult.userObj;
                            if (message2 != null) {
                                AsyncResult.forMessage(message2, asyncResult.result, asyncResult.exception);
                                message2.sendToTarget();
                                return;
                            }
                            return;
                        case 18:
                            asyncResult = (AsyncResult) message.obj;
                            if (asyncResult.exception == null) {
                                saveClirSetting(message.arg1);
                            }
                            message2 = (Message) asyncResult.userObj;
                            if (message2 != null) {
                                AsyncResult.forMessage(message2, asyncResult.result, asyncResult.exception);
                                message2.sendToTarget();
                                return;
                            }
                            return;
                        case 19:
                            syncClirSetting();
                            return;
                        case 20:
                            asyncResult = (AsyncResult) message.obj;
                            if (IccVmNotSupportedException.class.isInstance(asyncResult.exception)) {
                                storeVoiceMailNumber(this.mVmNumber);
                                asyncResult.exception = null;
                            }
                            message2 = (Message) asyncResult.userObj;
                            if (message2 != null) {
                                AsyncResult.forMessage(message2, asyncResult.result, asyncResult.exception);
                                message2.sendToTarget();
                                return;
                            }
                            return;
                        case 28:
                            asyncResult = (AsyncResult) message.obj;
                            if (this.mSST.mSS.getIsManualSelection()) {
                                setNetworkSelectionModeAutomatic((Message) asyncResult.result);
                                Rlog.d(LOG_TAG, "SET_NETWORK_SELECTION_AUTOMATIC: set to automatic");
                                return;
                            }
                            Rlog.d(LOG_TAG, "SET_NETWORK_SELECTION_AUTOMATIC: already automatic, ignore");
                            return;
                        case IccRecords.EVENT_REFRESH_OEM /*29*/:
                            processIccRecordEvents(((Integer) ((AsyncResult) message.obj).result).intValue());
                            return;
                        case 36:
                            asyncResult = (AsyncResult) message.obj;
                            Rlog.d(LOG_TAG, "Event EVENT_SS received");
                            new GsmMmiCode(this, (UiccCardApplication) this.mUiccApplication.get()).processSsData(asyncResult);
                            break;
                        case 39:
                            break;
                        default:
                            super.handleMessage(message);
                            return;
                    }
                    boolean callForwardingPreference = getCallForwardingPreference();
                    Rlog.d(LOG_TAG, "Callforwarding is " + callForwardingPreference);
                    if (callForwardingPreference) {
                        notifyCallForwardingIndicator();
                        return;
                    }
                    return;
                }
                Rlog.e(LOG_TAG, "Received message " + message + "[" + message.what + "] while being destroyed. Ignoring.");
                return;
        }
    }

    public boolean handlePinMmi(String str) {
        GsmMmiCode newFromDialString = GsmMmiCode.newFromDialString(str, this, (UiccCardApplication) this.mUiccApplication.get());
        if (newFromDialString == null || !newFromDialString.isPinPukCommand()) {
            return false;
        }
        this.mPendingMMIs.add(newFromDialString);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, newFromDialString, null));
        newFromDialString.processCode();
        return true;
    }

    /* Access modifiers changed, original: protected */
    public boolean isCfEnable(int i) {
        return i == 1 || i == 3;
    }

    public boolean isCspPlmnEnabled() {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        return iccRecords != null ? iccRecords.isCspPlmnEnabled() : false;
    }

    public boolean isDunConnectionPossible() {
        return (!this.mSST.mSS.getRoaming() || this.mDcTracker.getDataOnRoamingEnabled()) && getState() == PhoneConstants.State.IDLE;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isInCall() {
        return getForegroundCall().getState().isAlive() || getBackgroundCall().getState().isAlive() || getRingingCall().getState().isAlive();
    }

    public boolean isManualNetSelAllowed() {
        int i = Phone.PREFERRED_NT_MODE;
        i = PhoneFactory.calculatePreferredNetworkType(this.mContext, this.mPhoneId);
        Rlog.d(LOG_TAG, "isManualNetSelAllowed in mode = " + i);
        if (!SystemProperties.getBoolean(PhoneBase.PROPERTY_MULTIMODE_CDMA, false) || (i != 10 && i != 7)) {
            return true;
        }
        Rlog.d(LOG_TAG, "Manual selection not supported in mode = " + i);
        return false;
    }

    public boolean isReceivedMessage(SmsCbHeader smsCbHeader) {
        if (!TelBrand.IS_DCM) {
            return false;
        }
        AreaMailIds areaMailIds = new AreaMailIds(smsCbHeader);
        if (smsCbHeader.isEtwsPrimaryNotification()) {
            if (SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE == smsCbHeader.getServiceCategory()) {
                Rlog.d(LOG_TAG, "discard message<0x1103>");
            } else if (true != isReceivedEtwsMessage(areaMailIds)) {
                if (32 == this.mReceivedEtwsList.size()) {
                    Rlog.d(LOG_TAG, "mReceivedEtwsList remove first elemet");
                    this.mReceivedEtwsList.remove(0);
                }
                this.mReceivedEtwsList.add(areaMailIds);
                Rlog.d(LOG_TAG, "add new ETWS: MessageID=" + areaMailIds.messageIdentifier + ", SerialNo=" + areaMailIds.getSerialNo());
                return false;
            }
        } else if (MESSAGE_ID_CBS_TEST_MESSAGE == smsCbHeader.getServiceCategory()) {
            Rlog.d(LOG_TAG, "discard message<0xA003>");
            return true;
        } else if (true != isReceivedCbsMessage(areaMailIds)) {
            if (32 == this.mReceivedCbsList.size()) {
                Rlog.d(LOG_TAG, "mReceivedCbsList remove first elemet");
                this.mReceivedCbsList.remove(0);
            }
            Rlog.d(LOG_TAG, "add new CBS: MessageID=" + areaMailIds.messageIdentifier + "(should be 40960), SerialNo=" + areaMailIds.getSerialNo());
            this.mReceivedCbsList.add(areaMailIds);
            return false;
        }
        return true;
    }

    public boolean isUtEnabled() {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null) {
            return imsPhone.isUtEnabled();
        }
        Rlog.d(LOG_TAG, "isUtEnabled: called for GSM");
        return false;
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(LOG_TAG, "[GSMPhone] " + str);
    }

    public void notifyCallForwardingIndicator() {
        this.mNotifier.notifyCallForwardingChanged(this);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyDisconnect(Connection connection) {
        this.mDisconnectRegistrants.notifyResult(connection);
        this.mNotifier.notifyDisconnectCause(connection.getDisconnectCause(), connection.getPreciseDisconnectCause());
    }

    public void notifyEcbmTimerReset(Boolean bool) {
        this.mEcmTimerResetRegistrants.notifyResult(bool);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyLocationChanged() {
        this.mNotifier.notifyCellLocation(this);
    }

    public void notifyNewRingingConnection(Connection connection) {
        super.notifyNewRingingConnectionP(connection);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyPhoneStateChanged() {
        this.mNotifier.notifyPhoneState(this);
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
    public void notifyUnknownConnection(Connection connection) {
        super.notifyUnknownConnectionP(connection);
    }

    /* Access modifiers changed, original: 0000 */
    public void onMMIDone(GsmMmiCode gsmMmiCode) {
        if (this.mPendingMMIs.remove(gsmMmiCode) || gsmMmiCode.isUssdRequest() || gsmMmiCode.isSsInfo()) {
            this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, gsmMmiCode, null));
        }
    }

    /* Access modifiers changed, original: protected */
    public void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            IsimUiccRecords isimUiccRecords;
            setCardInPhoneBook();
            UiccCardApplication uiccCardApplication = this.mUiccController.getUiccCardApplication(this.mPhoneId, 3);
            if (uiccCardApplication != null) {
                isimUiccRecords = (IsimUiccRecords) uiccCardApplication.getIccRecords();
                log("New ISIM application found");
            } else {
                isimUiccRecords = null;
            }
            this.mIsimUiccRecords = isimUiccRecords;
            UiccCardApplication uiccCardApplication2 = getUiccCardApplication();
            uiccCardApplication = (UiccCardApplication) this.mUiccApplication.get();
            if (uiccCardApplication != uiccCardApplication2) {
                if (uiccCardApplication != null) {
                    log("Removing stale icc objects.");
                    if (this.mIccRecords.get() != null) {
                        unregisterForSimRecordEvents();
                    }
                    this.mIccRecords.set(null);
                    this.mUiccApplication.set(null);
                }
                if (uiccCardApplication2 != null) {
                    log("New Uicc application found");
                    this.mUiccApplication.set(uiccCardApplication2);
                    this.mIccRecords.set(uiccCardApplication2.getIccRecords());
                    registerForSimRecordEvents();
                }
            }
        }
    }

    public void registerForAllDataDisconnected(Handler handler, int i, Object obj) {
        ((DcTracker) this.mDcTracker).registerForAllDataDisconnected(handler, i, obj);
    }

    public void registerForEcmTimerReset(Handler handler, int i, Object obj) {
        this.mEcmTimerResetRegistrants.addUnique(handler, i, obj);
    }

    public void registerForSimRecordsLoaded(Handler handler, int i, Object obj) {
        this.mSimRecordsLoadedRegistrants.addUnique(handler, i, obj);
    }

    public void registerForSuppServiceNotification(Handler handler, int i, Object obj) {
        this.mSsnRegistrants.addUnique(handler, i, obj);
        if (this.mSsnRegistrants.size() == 1) {
            this.mCi.setSuppServiceNotifications(true, null);
        }
    }

    public void rejectCall() throws CallStateException {
        this.mCT.rejectCall();
    }

    public void removeReferences() {
        Rlog.d(LOG_TAG, "removeReferences");
        this.mSimulatedRadioControl = null;
        this.mSimPhoneBookIntManager = null;
        this.mSubInfo = null;
        this.mCT = null;
        this.mSST = null;
        super.removeReferences();
    }

    public void requestChangeCbPsw(String str, String str2, String str3, Message message) {
        this.mCi.changeBarringPassword(str, str2, str3, message);
    }

    public void resetDunProfiles() {
        this.mDcTracker.resetDunProfiles();
    }

    public void saveClirSetting(int i) {
        Editor edit = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        edit.putInt(PhoneBase.CLIR_KEY + getPhoneId(), i);
        if (!edit.commit()) {
            Rlog.e(LOG_TAG, "failed to commit CLIR preference");
        }
    }

    public void sendBurstDtmf(String str) {
        Rlog.e(LOG_TAG, "[GSMPhone] sendBurstDtmf() is a CDMA method");
    }

    public void sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG, "sendDtmf called with invalid character '" + c + "'");
        } else if (this.mCT.mState == PhoneConstants.State.OFFHOOK) {
            this.mCi.sendDtmf(c, null);
        }
    }

    public void sendUssdResponse(String str) {
        GsmMmiCode newFromUssdUserInput = GsmMmiCode.newFromUssdUserInput(str, this, (UiccCardApplication) this.mUiccApplication.get());
        this.mPendingMMIs.add(newFromUssdUserInput);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, newFromUssdUserInput, null));
        newFromUssdUserInput.sendUssd(str);
    }

    public void setCallBarring(String str, boolean z, String str2, int i, Message message) {
        this.mCi.setFacilityLock(str, z, str2, i, message);
    }

    public void setCallBarring(String str, boolean z, String str2, Message message) {
        this.mCi.setFacilityLock(str, z, str2, 1, message);
    }

    public void setCallBarringOption(String str, boolean z, String str2, Message message) {
        if (isValidFacilityString(str)) {
            this.mCi.setFacilityLock(str, z, str2, 1, message);
        }
    }

    public void setCallForwardingOption(int i, int i2, int i3, String str, int i4, Message message) {
        if (isValidCommandInterfaceCFAction(i) && isValidCommandInterfaceCFReason(i2)) {
            Message obtainMessage;
            if (i2 == 0) {
                obtainMessage = obtainMessage(12, isCfEnable(i) ? 1 : 0, 0, new Cfu(str, message));
            } else {
                obtainMessage = message;
            }
            this.mCi.setCallForward(i, i2, i3, str, i4, obtainMessage);
        }
    }

    public void setCallForwardingOption(int i, int i2, String str, int i3, Message message) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null && (imsPhone.getServiceState().getState() == 0 || imsPhone.isUtEnabled())) {
            imsPhone.setCallForwardingOption(i, i2, str, i3, message);
        } else if (isValidCommandInterfaceCFAction(i) && isValidCommandInterfaceCFReason(i2)) {
            Message obtainMessage;
            if (i2 == 0) {
                obtainMessage = obtainMessage(12, isCfEnable(i) ? 1 : 0, 0, new Cfu(str, message));
            } else {
                obtainMessage = message;
            }
            this.mCi.setCallForward(i, i2, 1, str, i3, obtainMessage);
        }
    }

    public void setCallForwardingUncondTimerOption(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone != null && (imsPhone.getServiceState().getState() == 0 || imsPhone.isUtEnabled())) {
            imsPhone.setCallForwardingUncondTimerOption(i, i2, i3, i4, i5, i6, str, message);
        } else if (message != null) {
            AsyncResult.forMessage(message, null, new CommandException(Error.GENERIC_FAILURE));
            message.sendToTarget();
        }
    }

    public void setCallWaiting(boolean z, int i, Message message) {
        this.mCi.setCallWaiting(z, i, message);
    }

    public void setCallWaiting(boolean z, Message message) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || !(imsPhone.getServiceState().getState() == 0 || imsPhone.isUtEnabled())) {
            this.mCi.setCallWaiting(z, 1, message);
        } else {
            imsPhone.setCallWaiting(z, message);
        }
    }

    /* Access modifiers changed, original: protected */
    public void setCardInPhoneBook() {
        if (this.mUiccController != null) {
            this.mSimPhoneBookIntManager.setIccCard(this.mUiccController.getUiccCard(this.mPhoneId));
        }
    }

    public void setCellBroadcastSmsConfig(int[] iArr, Message message) {
        Rlog.e(LOG_TAG, "[GSMPhone] setCellBroadcastSmsConfig() is obsolete; use SmsManager");
        message.sendToTarget();
    }

    public void setDataEnabled(boolean z) {
        this.mDcTracker.setDataEnabled(z);
    }

    public void setDataRoamingEnabled(boolean z) {
        this.mDcTracker.setDataOnRoamingEnabled(z);
    }

    public void setInternalDataEnabled(boolean z, Message message) {
        ((DcTracker) this.mDcTracker).setInternalDataEnabled(z, message);
    }

    public boolean setInternalDataEnabledFlag(boolean z) {
        return ((DcTracker) this.mDcTracker).setInternalDataEnabledFlag(z);
    }

    public void setLine1Number(String str, String str2, Message message) {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords != null) {
            iccRecords.setMsisdnNumber(str, str2, message);
        }
    }

    public void setMobileDataEnabledDun(boolean z) {
        this.mDcTracker.setMobileDataEnabledDun(z);
    }

    public void setMute(boolean z) {
        this.mCT.setMute(z);
    }

    public void setOnPostDialCharacter(Handler handler, int i, Object obj) {
        this.mPostDialHandler = new Registrant(handler, i, obj);
    }

    public boolean setOperatorBrandOverride(String str) {
        boolean z = false;
        if (this.mUiccController != null) {
            UiccCard uiccCard = this.mUiccController.getUiccCard(getPhoneId());
            if (uiccCard != null) {
                boolean operatorBrandOverride = uiccCard.setOperatorBrandOverride(str);
                if (operatorBrandOverride) {
                    IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
                    if (iccRecords != null) {
                        SystemProperties.set("gsm.sim.operator.alpha", iccRecords.getServiceProviderName());
                    }
                    if (this.mSST != null) {
                        this.mSST.pollState();
                        return operatorBrandOverride;
                    }
                }
                z = operatorBrandOverride;
            }
        }
        return z;
    }

    public void setOutgoingCallerIdDisplay(int i, Message message) {
        ImsPhone imsPhone = this.mImsPhone;
        if (imsPhone == null || !(imsPhone.getServiceState().getState() == 0 || imsPhone.isUtEnabled())) {
            this.mCi.setCLIR(i, obtainMessage(18, i, 0, message));
        } else {
            imsPhone.setOutgoingCallerIdDisplay(i, obtainMessage(18, i, 0, message));
        }
    }

    public void setProfilePdpType(int i, String str) {
        this.mDcTracker.setProfilePdpType(i, str);
    }

    /* Access modifiers changed, original: protected */
    public void setProperties() {
        TelephonyManager.setTelephonyProperty(this.mPhoneId, "gsm.current.phone-type", new Integer(1).toString());
    }

    public void setRadioPower(boolean z) {
        this.mSST.setRadioPower(z);
    }

    public void setSystemProperty(String str, String str2) {
        TelephonyManager.setTelephonyProperty(this.mPhoneId, str, str2);
    }

    public void setUiTTYMode(int i, Message message) {
        if (this.mImsPhone != null) {
            this.mImsPhone.setUiTTYMode(i, message);
        }
    }

    public void setVoiceMailNumber(String str, String str2, Message message) {
        this.mVmNumber = str2;
        Message obtainMessage = obtainMessage(20, 0, 0, message);
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords != null) {
            iccRecords.setVoiceMailNumber(str, this.mVmNumber, obtainMessage);
        }
    }

    public void setVoiceMessageWaiting(int i, int i2) {
        IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
        if (iccRecords != null) {
            iccRecords.setVoiceMessageWaiting(i, i2);
        } else {
            log("SIM Records not found, MWI not updated");
        }
    }

    public void startDtmf(char c) {
        if (PhoneNumberUtils.is12Key(c) || (c >= 'A' && c <= 'D')) {
            this.mCi.startDtmf(c, null);
        } else {
            Rlog.e(LOG_TAG, "startDtmf called with invalid character '" + c + "'");
        }
    }

    public void stopDtmf() {
        this.mCi.stopDtmf(null);
    }

    public void switchHoldingAndActive() throws CallStateException {
        this.mCT.switchWaitingOrHoldingAndActive();
    }

    /* Access modifiers changed, original: protected */
    public void syncClirSetting() {
        int i = PreferenceManager.getDefaultSharedPreferences(getContext()).getInt(PhoneBase.CLIR_KEY + getPhoneId(), -1);
        if (i >= 0) {
            this.mCi.setCLIR(i, null);
        }
    }

    public void unregisterForAllDataDisconnected(Handler handler) {
        ((DcTracker) this.mDcTracker).unregisterForAllDataDisconnected(handler);
    }

    public void unregisterForEcmTimerReset(Handler handler) {
        this.mEcmTimerResetRegistrants.remove(handler);
    }

    public void unregisterForSimRecordsLoaded(Handler handler) {
        this.mSimRecordsLoadedRegistrants.remove(handler);
    }

    public void unregisterForSuppServiceNotification(Handler handler) {
        this.mSsnRegistrants.remove(handler);
        if (this.mSsnRegistrants.size() == 0) {
            this.mCi.setSuppServiceNotifications(false, null);
        }
    }

    public boolean updateCurrentCarrierInProvider() {
        int defaultDataSubId = SubscriptionManager.getDefaultDataSubId();
        String operatorNumeric = getOperatorNumeric();
        log("updateCurrentCarrierInProvider: mSubId = " + getSubId() + " currentDds = " + defaultDataSubId + " operatorNumeric = " + operatorNumeric);
        if (!TextUtils.isEmpty(operatorNumeric) && getSubId() == defaultDataSubId) {
            try {
                Uri withAppendedPath = Uri.withAppendedPath(Carriers.CONTENT_URI, Carriers.CURRENT);
                ContentValues contentValues = new ContentValues();
                contentValues.put(Carriers.NUMERIC, operatorNumeric);
                this.mContext.getContentResolver().insert(withAppendedPath, contentValues);
                return true;
            } catch (SQLException e) {
                Rlog.e(LOG_TAG, "Can't store current operator", e);
            }
        }
        return false;
    }

    public void updateDataConnectionTracker() {
        ((DcTracker) this.mDcTracker).update();
    }

    public void updateServiceLocation() {
        this.mSST.enableSingleLocationUpdate();
    }
}
