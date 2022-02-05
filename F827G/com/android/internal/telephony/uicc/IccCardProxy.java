package com.android.internal.telephony.uicc;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.google.android.mms.pdu.PduPart;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class IccCardProxy extends Handler implements IccCard {
    private static final boolean DBG = true;
    private static final String ENCRYPTED_STATE = "encrypted";
    private static final int EVENT_APP_READY = 6;
    private static final int EVENT_CARRIER_PRIVILIGES_LOADED = 503;
    private static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 11;
    private static final int EVENT_CLOSE_CHANNEL_DONE_SHARP = 22;
    private static final int EVENT_EXCHANGE_APDU_DONE_SHARP = 20;
    private static final int EVENT_GET_EF_LOCK_DONE = 23;
    private static final int EVENT_GET_PIN_PUK_RETRY_COUNT = 17;
    private static final int EVENT_GET_SIM_LOCK_DONE = 25;
    private static final int EVENT_ICC_ABSENT = 4;
    private static final int EVENT_ICC_CHANGED = 3;
    private static final int EVENT_ICC_LOCKED = 5;
    private static final int EVENT_ICC_RECORD_EVENTS = 500;
    private static final int EVENT_IMSI_READY = 8;
    private static final int EVENT_OEM_HOOK_RAW_RADIO_STATE = 24;
    private static final int EVENT_OPEN_CHANNEL_DONE_SHARP = 21;
    private static final int EVENT_PERSO_LOCKED = 9;
    private static final int EVENT_POLL_BROADCAST = 30;
    private static final int EVENT_RADIO_OFF_OR_UNAVAILABLE = 1;
    private static final int EVENT_RADIO_ON = 2;
    private static final int EVENT_RECORDS_LOADED = 7;
    private static final int EVENT_SUBSCRIPTION_ACTIVATED = 501;
    private static final int EVENT_SUBSCRIPTION_DEACTIVATED = 502;
    private static final int INITIAL_VALUE_RETRY_COUNT = -1;
    private static final String LOG_TAG = "IccCardProxy";
    private static final String MIN_FRAMEWORK_STATE = "trigger_restart_min_framework";
    protected static final int POLL_PERIOD_MILLIS = 1000;
    private static final String PROP_RO_CRYPTO_STATE = "ro.crypto.state";
    private static final String PROP_VOLD_DECRYPT = "vold.decrypt";
    private static final int RESPONSE_INDEX_PIN1 = 0;
    private static final int RESPONSE_INDEX_PIN2 = 2;
    private static final int RESPONSE_INDEX_PUK1 = 1;
    private static final int RESPONSE_INDEX_PUK2 = 3;
    private static final String SIM_ABSENT_CHECK = "ril.uim.absentcheck";
    private static final int SIM_ABSENT_NOT_RECEIVED = 0;
    private static final int SIM_ABSENT_RECEIVED = 1;
    private static final boolean SMARTCARD_DBG = false;
    private CdmaSubscriptionSourceManager mCdmaSSM;
    private CommandsInterface mCi;
    private Context mContext;
    private Integer mPhoneId;
    private UiccController mUiccController;
    private final Object mLock = new Object();
    private RegistrantList mAbsentRegistrants = new RegistrantList();
    private RegistrantList mPinLockedRegistrants = new RegistrantList();
    private RegistrantList mPersoLockedRegistrants = new RegistrantList();
    private int mCurrentAppType = 1;
    private UiccCard mUiccCard = null;
    private UiccCardApplication mUiccApplication = null;
    private IccRecords mIccRecords = null;
    private boolean mRadioOn = false;
    private boolean mQuietMode = false;
    private boolean mInitialized = false;
    protected IccCardConstants.State mExternalState = IccCardConstants.State.UNKNOWN;
    private boolean mIsCardStatusAvailable = false;
    private IccCardApplicationStatus.PersoSubState mPersoSubState = IccCardApplicationStatus.PersoSubState.PERSOSUBSTATE_UNKNOWN;
    protected IccCardConstants.State mExternalStateSharp = IccCardConstants.State.UNKNOWN;
    protected boolean dontPollBroadcastSharp = false;
    private int mSimLock = 0;
    private IccOpenLogicalChannel mIccOpenLogicalChannel = new IccOpenLogicalChannel();

    public IccCardProxy(Context context, CommandsInterface ci, int phoneId) {
        this.mPhoneId = null;
        this.mUiccController = null;
        this.mCdmaSSM = null;
        log("ctor: ci=" + ci + " phoneId=" + phoneId);
        this.mContext = context;
        this.mCi = ci;
        this.mPhoneId = Integer.valueOf(phoneId);
        this.mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(context, ci, this, 11, null);
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 3, null);
        ci.registerForOn(this, 2, null);
        ci.registerForOffOrNotAvailable(this, 1, null);
        resetProperties();
        setExternalState(IccCardConstants.State.NOT_READY, false);
        sendMessage(obtainMessage(24));
        if (TelBrand.IS_SBM) {
            ci.getSimLock(obtainMessage(25));
        }
    }

    public void dispose() {
        synchronized (this.mLock) {
            log("Disposing");
            this.mUiccController.unregisterForIccChanged(this);
            this.mUiccController = null;
            this.mCi.unregisterForOn(this);
            this.mCi.unregisterForOffOrNotAvailable(this);
            this.mCdmaSSM.dispose(this);
        }
    }

    public void setVoiceRadioTech(int radioTech) {
        synchronized (this.mLock) {
            log("Setting radio tech " + ServiceState.rilRadioTechnologyToString(radioTech));
            if (ServiceState.isGsm(radioTech)) {
                this.mCurrentAppType = 1;
            } else {
                this.mCurrentAppType = 2;
            }
            updateQuietMode();
            updateActiveRecord();
        }
    }

    private void updateActiveRecord() {
        log("updateActiveRecord app type = " + this.mCurrentAppType + "mIccRecords = " + this.mIccRecords);
        if (this.mIccRecords != null) {
            if (this.mCurrentAppType == 2) {
                if (this.mCdmaSSM.getCdmaSubscriptionSource() == 0) {
                    log("Setting Ruim Record as active");
                    this.mIccRecords.recordsRequired();
                }
            } else if (this.mCurrentAppType == 1) {
                log("Setting SIM Record as active");
                this.mIccRecords.recordsRequired();
            }
        }
    }

    private void updateQuietMode() {
        boolean newQuietMode;
        synchronized (this.mLock) {
            boolean z = this.mQuietMode;
            boolean isLteCapable = this.mContext.getResources().getBoolean(17957012);
            int cdmaSource = -1;
            if (this.mCurrentAppType == 1) {
                newQuietMode = false;
                log("updateQuietMode: 3GPP subscription -> newQuietMode=false");
            } else {
                cdmaSource = this.mCdmaSSM != null ? this.mCdmaSSM.getCdmaSubscriptionSource() : -1;
                if (isLteCapable) {
                    newQuietMode = false;
                } else if (cdmaSource == 1 && this.mCurrentAppType == 2) {
                    newQuietMode = true;
                } else {
                    newQuietMode = false;
                }
                log("updateQuietMode: cdmaSource=" + cdmaSource + " mCurrentAppType=" + this.mCurrentAppType + " newQuietMode=" + newQuietMode);
            }
            if (!this.mQuietMode && newQuietMode) {
                log("Switching to QuietMode.");
                setExternalState(IccCardConstants.State.READY);
                this.mQuietMode = newQuietMode;
            } else if (!this.mQuietMode || newQuietMode) {
                log("updateQuietMode: no changes don't setExternalState");
            } else {
                log("updateQuietMode: Switching out from QuietMode. Force broadcast of current state=" + this.mExternalState);
                this.mQuietMode = newQuietMode;
                setExternalState(this.mExternalState, true);
                if (TelBrand.IS_SBM) {
                    setExternalStateSharp(this.mExternalStateSharp, true);
                }
            }
            log("updateQuietMode: QuietMode is " + this.mQuietMode + " (app_type=" + this.mCurrentAppType + " cdmaSource=" + cdmaSource + ")");
            this.mInitialized = true;
            if (this.mIsCardStatusAvailable) {
                sendMessage(obtainMessage(3));
            }
        }
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                this.mRadioOn = false;
                if (CommandsInterface.RadioState.RADIO_UNAVAILABLE == this.mCi.getRadioState()) {
                    setExternalState(IccCardConstants.State.NOT_READY);
                    return;
                }
                return;
            case 2:
                this.mRadioOn = true;
                if (!this.mInitialized) {
                    updateQuietMode();
                }
                updateExternalState();
                return;
            case 3:
                this.mIsCardStatusAvailable = true;
                if (this.mInitialized) {
                    updateIccAvailability();
                    return;
                }
                return;
            case 4:
                this.mAbsentRegistrants.notifyRegistrants();
                setExternalState(IccCardConstants.State.ABSENT);
                return;
            case 5:
                processLockedState();
                return;
            case 6:
                setExternalState(IccCardConstants.State.READY);
                return;
            case 7:
                if (this.mIccRecords != null) {
                    String operator = this.mIccRecords.getOperatorNumeric();
                    log("operator=" + operator + " slotId=" + this.mPhoneId.intValue());
                    if (operator != null) {
                        log("update icc_operator_numeric=" + operator);
                        setSystemProperty("gsm.sim.operator.numeric", operator);
                        if (this.mCurrentAppType == 1) {
                            setSystemProperty("gsm.apn.sim.operator.numeric", operator);
                            log("update sim_operator_numeric=" + operator);
                        } else if (this.mCurrentAppType == 2) {
                            setSystemProperty("net.cdma.ruim.operator.numeric", operator);
                            log("update ruim_operator_numeric=" + operator);
                        }
                        String countryCode = operator.substring(0, 3);
                        if (countryCode != null) {
                            setSystemProperty("gsm.sim.operator.iso-country", MccTable.countryCodeForMcc(Integer.parseInt(countryCode)));
                        } else {
                            loge("EVENT_RECORDS_LOADED Country code is null");
                        }
                    } else {
                        loge("EVENT_RECORDS_LOADED Operator name is null");
                    }
                }
                if (this.mUiccCard == null || this.mUiccCard.areCarrierPriviligeRulesLoaded()) {
                    onRecordsLoaded();
                    return;
                } else {
                    this.mUiccCard.registerForCarrierPrivilegeRulesLoaded(this, EVENT_CARRIER_PRIVILIGES_LOADED, null);
                    return;
                }
            case 8:
                broadcastIccStateChangedIntent("IMSI", null);
                return;
            case 9:
                this.mPersoSubState = this.mUiccApplication.getPersoSubState();
                this.mPersoLockedRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                setExternalState(IccCardConstants.State.PERSO_LOCKED);
                return;
            case 11:
                updateQuietMode();
                updateActiveRecord();
                return;
            case 17:
                onGetPinPukRetryCountDone((AsyncResult) msg.obj);
                notifyCurrentExternalState();
                if (TelBrand.IS_SBM) {
                    notifyCurrentExternalStateSharp();
                    return;
                }
                return;
            case 20:
            case 21:
            case 22:
            case 23:
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    loge("Error in SIM access with exception" + ar.exception);
                }
                AsyncResult.forMessage((Message) ar.userObj, ar.result, ar.exception);
                ((Message) ar.userObj).sendToTarget();
                return;
            case 24:
                synchronized (this.mLock) {
                    if (this.mCi.isRunning()) {
                        requestUnsolRadioStateChanged();
                    } else {
                        Message oemhookmsg = obtainMessage();
                        oemhookmsg.what = 24;
                        sendMessageDelayed(oemhookmsg, 1000L);
                    }
                }
                return;
            case 25:
                log("EVENT_GET_SIM_LOCK_DONE");
                AsyncResult arSimLock = (AsyncResult) msg.obj;
                if (arSimLock.exception != null) {
                    loge("Error in get sim lock with exception: " + arSimLock.exception);
                    this.mSimLock = 1;
                    return;
                } else if (arSimLock.result != null) {
                    int simLockState = 3;
                    byte[] ret = (byte[]) arSimLock.result;
                    ByteBuffer bbRet = ByteBuffer.wrap(ret);
                    if (64 == ret.length) {
                        int index = 0;
                        while (true) {
                            if ((index + 4) - 1 < ret.length) {
                                if (bbRet.getInt(index) != 0) {
                                    simLockState = 2;
                                } else {
                                    index += 8;
                                }
                            }
                        }
                        this.mSimLock = simLockState;
                        if (2 == this.mSimLock) {
                            log("mSimlock is ON");
                            return;
                        } else {
                            log("mSimlock is OFF");
                            return;
                        }
                    } else {
                        this.mSimLock = 1;
                        loge("The response has unexpected size: " + ret.length);
                        return;
                    }
                } else {
                    this.mSimLock = 1;
                    loge("No result in get sim lock");
                    return;
                }
            case 30:
                synchronized (this.mLock) {
                    String[] strIntent = (String[]) msg.obj;
                    String value = strIntent[0];
                    String reason = strIntent[1];
                    if (!this.dontPollBroadcastSharp) {
                        broadcastIccStateChangedIntentSharp(value, reason);
                    }
                }
                return;
            case EVENT_ICC_RECORD_EVENTS /* 500 */:
                if (this.mCurrentAppType == 1 && this.mIccRecords != null && ((Integer) ((AsyncResult) msg.obj).result).intValue() == 2) {
                    setSystemProperty("gsm.sim.operator.alpha", this.mIccRecords.getServiceProviderName());
                    return;
                }
                return;
            case EVENT_SUBSCRIPTION_ACTIVATED /* 501 */:
                log("EVENT_SUBSCRIPTION_ACTIVATED");
                onSubscriptionActivated();
                return;
            case EVENT_SUBSCRIPTION_DEACTIVATED /* 502 */:
                log("EVENT_SUBSCRIPTION_DEACTIVATED");
                onSubscriptionDeactivated();
                return;
            case EVENT_CARRIER_PRIVILIGES_LOADED /* 503 */:
                log("EVENT_CARRIER_PRIVILEGES_LOADED");
                if (this.mUiccCard != null) {
                    this.mUiccCard.unregisterForCarrierPrivilegeRulesLoaded(this);
                }
                onRecordsLoaded();
                return;
            default:
                loge("Unhandled message with number: " + msg.what);
                return;
        }
    }

    private void onSubscriptionActivated() {
        updateIccAvailability();
        updateStateProperty();
    }

    private void onSubscriptionDeactivated() {
        resetProperties();
        updateIccAvailability();
        updateStateProperty();
    }

    private void onRecordsLoaded() {
        broadcastIccStateChangedIntent("LOADED", null);
    }

    private void updateIccAvailability() {
        synchronized (this.mLock) {
            UiccCard newCard = this.mUiccController.getUiccCard(this.mPhoneId.intValue());
            IccCardStatus.CardState cardState = IccCardStatus.CardState.CARDSTATE_ABSENT;
            UiccCardApplication newApp = null;
            IccRecords newRecords = null;
            if (newCard != null) {
                newCard.getCardState();
                newApp = newCard.getApplication(this.mCurrentAppType);
                if (newApp != null) {
                    newRecords = newApp.getIccRecords();
                }
            }
            if (!(this.mIccRecords == newRecords && this.mUiccApplication == newApp && this.mUiccCard == newCard)) {
                log("Icc changed. Reregestering.");
                unregisterUiccCardEvents();
                this.mUiccCard = newCard;
                this.mUiccApplication = newApp;
                this.mIccRecords = newRecords;
                registerUiccCardEvents();
                updateActiveRecord();
            }
            updateExternalState();
            if (TelBrand.IS_SBM) {
                updateExternalStateSharp();
            }
        }
    }

    void resetProperties() {
        if (this.mCurrentAppType == 1) {
            log("update icc_operator_numeric=");
            setSystemProperty("gsm.sim.operator.numeric", "");
            setSystemProperty("gsm.sim.operator.iso-country", "");
            setSystemProperty("gsm.sim.operator.alpha", "");
        }
    }

    private void updateExternalState() {
        if (this.mUiccCard == null) {
            setExternalState(IccCardConstants.State.NOT_READY);
        } else if (this.mUiccCard.getCardState() == IccCardStatus.CardState.CARDSTATE_ABSENT) {
            if (SystemProperties.getInt("persist.radio.apm_sim_not_pwdn", 0) != 0) {
                setExternalState(IccCardConstants.State.ABSENT);
                if (1 == SystemProperties.getInt(SIM_ABSENT_CHECK, 0)) {
                    broadcastIccStateChangedIntentAbsent();
                }
            } else if (this.mRadioOn) {
                setExternalState(IccCardConstants.State.ABSENT);
            } else {
                setExternalState(IccCardConstants.State.NOT_READY);
            }
        } else if (this.mUiccCard.getCardState() == IccCardStatus.CardState.CARDSTATE_ERROR) {
            if (TelBrand.IS_DCM) {
                setExternalState(IccCardConstants.State.ABSENT);
            } else {
                setExternalState(IccCardConstants.State.CARD_IO_ERROR);
            }
        } else if (this.mUiccApplication == null) {
            setExternalState(IccCardConstants.State.NOT_READY);
        } else {
            switch (this.mUiccApplication.getState()) {
                case APPSTATE_UNKNOWN:
                    setExternalState(IccCardConstants.State.UNKNOWN);
                    return;
                case APPSTATE_PIN:
                    setExternalState(IccCardConstants.State.PIN_REQUIRED);
                    return;
                case APPSTATE_PUK:
                    log("[UIM]updateExternalState app_state : APPSTATE_PUK");
                    IccCardStatus.PinState pin1State = this.mUiccApplication.getPin1State();
                    log("[UIM]updateExternalState pin1State : " + pin1State);
                    if (pin1State == IccCardStatus.PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
                        setExternalState(IccCardConstants.State.PERM_DISABLED);
                        return;
                    } else {
                        setExternalState(IccCardConstants.State.PUK_REQUIRED);
                        return;
                    }
                case APPSTATE_SUBSCRIPTION_PERSO:
                    if (this.mUiccApplication.isPersoLocked()) {
                        this.mPersoSubState = this.mUiccApplication.getPersoSubState();
                        setExternalState(IccCardConstants.State.PERSO_LOCKED);
                        return;
                    }
                    setExternalState(IccCardConstants.State.UNKNOWN);
                    return;
                case APPSTATE_READY:
                    setExternalState(IccCardConstants.State.READY);
                    return;
                default:
                    return;
            }
        }
    }

    private void registerUiccCardEvents() {
        if (this.mUiccCard != null) {
            this.mUiccCard.registerForAbsent(this, 4, null);
        }
        if (this.mUiccApplication != null) {
            this.mUiccApplication.registerForReady(this, 6, null);
            this.mUiccApplication.registerForLocked(this, 5, null);
            this.mUiccApplication.registerForPersoLocked(this, 9, null);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.registerForImsiReady(this, 8, null);
            this.mIccRecords.registerForRecordsLoaded(this, 7, null);
            this.mIccRecords.registerForRecordsEvents(this, EVENT_ICC_RECORD_EVENTS, null);
        }
    }

    private void unregisterUiccCardEvents() {
        if (this.mUiccCard != null) {
            this.mUiccCard.unregisterForAbsent(this);
        }
        if (this.mUiccApplication != null) {
            this.mUiccApplication.unregisterForReady(this);
        }
        if (this.mUiccApplication != null) {
            this.mUiccApplication.unregisterForLocked(this);
        }
        if (this.mUiccApplication != null) {
            this.mUiccApplication.unregisterForPersoLocked(this);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.unregisterForImsiReady(this);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.unregisterForRecordsLoaded(this);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.unregisterForRecordsEvents(this);
        }
    }

    private void updateStateProperty() {
        setSystemProperty("gsm.sim.state", getState().toString());
    }

    private void broadcastIccStateChangedIntent(String value, String reason) {
        synchronized (this.mLock) {
            if (this.mPhoneId == null || !SubscriptionManager.isValidSlotId(this.mPhoneId.intValue())) {
                loge("broadcastIccStateChangedIntent: mPhoneId=" + this.mPhoneId + " is invalid; Return!!");
            } else if (this.mQuietMode) {
                log("broadcastIccStateChangedIntent: QuietMode NOT Broadcasting intent ACTION_SIM_STATE_CHANGED  value=" + value + " reason=" + reason);
            } else if (!TelBrand.IS_DCM || !ENCRYPTED_STATE.equals(SystemProperties.get(PROP_RO_CRYPTO_STATE)) || !MIN_FRAMEWORK_STATE.equals(SystemProperties.get(PROP_VOLD_DECRYPT)) || "NOT_READY".equals(value) || "ABSENT".equals(value)) {
                Intent intent = new Intent("android.intent.action.SIM_STATE_CHANGED");
                intent.addFlags(67108864);
                intent.putExtra("phoneName", "Phone");
                intent.putExtra("ss", value);
                intent.putExtra("reason", reason);
                SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhoneId.intValue());
                log("broadcastIccStateChangedIntent intent ACTION_SIM_STATE_CHANGED value=" + value + " reason=" + reason + " for mPhoneId=" + this.mPhoneId);
                ActivityManagerNative.broadcastStickyIntent(intent, "android.permission.READ_PHONE_STATE", -1);
            } else {
                log("Encrypted: NOT Broadcasting intent ACTION_SIM_STATE_CHANGED " + value + " reason " + reason);
            }
        }
    }

    private void setExternalState(IccCardConstants.State newState, boolean override) {
        synchronized (this.mLock) {
            if (this.mPhoneId == null || !SubscriptionManager.isValidSlotId(this.mPhoneId.intValue())) {
                loge("setExternalState: mPhoneId=" + this.mPhoneId + " is invalid; Return!!");
            } else if (override || newState != this.mExternalState) {
                this.mExternalState = newState;
                if (newState == IccCardConstants.State.PIN_REQUIRED || newState == IccCardConstants.State.PUK_REQUIRED || newState == IccCardConstants.State.PERSO_LOCKED || newState == IccCardConstants.State.READY || newState == IccCardConstants.State.PERM_DISABLED) {
                    requestGetPinPukRetryCount();
                    return;
                }
                if (this.mUiccCard != null) {
                    this.mUiccCard.setPinPukRetryCount(0, -1);
                }
                if (!TelBrand.IS_DCM) {
                    loge("setExternalState: set mPhoneId=" + this.mPhoneId + " mExternalState=" + this.mExternalState);
                    setSystemProperty("gsm.sim.state", getState().toString());
                } else if (!ENCRYPTED_STATE.equals(SystemProperties.get(PROP_RO_CRYPTO_STATE)) || !MIN_FRAMEWORK_STATE.equals(SystemProperties.get(PROP_VOLD_DECRYPT)) || "NOT_READY".equals(getIccStateIntentString(this.mExternalState)) || "ABSENT".equals(getIccStateIntentString(this.mExternalState))) {
                    setSystemProperty("gsm.sim.state", getState().toString());
                }
                broadcastIccStateChangedIntent(getIccStateIntentString(this.mExternalState), getIccStateReason(this.mExternalState));
                if (IccCardConstants.State.ABSENT == this.mExternalState) {
                    this.mAbsentRegistrants.notifyRegistrants();
                }
            } else {
                loge("setExternalState: !override and newstate unchanged from " + newState);
            }
        }
    }

    public void broadcastIccStateChangedIntentAbsent() {
        synchronized (this.mLock) {
            Intent intent = new Intent("jp.co.sharp.android.telephony.action.SIM_STATE_CHANGED_ABSENT");
            intent.addFlags(536870912);
            log("Broadcasting intent ACTION_SIM_STATE_CHANGED_ABSENT");
            ActivityManagerNative.broadcastStickyIntent(intent, "android.permission.READ_PHONE_STATE", -1);
        }
    }

    private void processLockedState() {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                if (this.mUiccApplication.getPin1State() == IccCardStatus.PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
                    setExternalState(IccCardConstants.State.PERM_DISABLED);
                    return;
                }
                switch (this.mUiccApplication.getState()) {
                    case APPSTATE_PIN:
                        this.mPinLockedRegistrants.notifyRegistrants();
                        setExternalState(IccCardConstants.State.PIN_REQUIRED);
                        break;
                    case APPSTATE_PUK:
                        setExternalState(IccCardConstants.State.PUK_REQUIRED);
                        break;
                }
            }
        }
    }

    private void setExternalState(IccCardConstants.State newState) {
        setExternalState(newState, false);
    }

    public boolean getIccRecordsLoaded() {
        boolean recordsLoaded;
        synchronized (this.mLock) {
            recordsLoaded = this.mIccRecords != null ? this.mIccRecords.getRecordsLoaded() : false;
        }
        return recordsLoaded;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: com.android.internal.telephony.uicc.IccCardProxy$1  reason: invalid class name */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$IccCardConstants$State = new int[IccCardConstants.State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.ABSENT.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.PIN_REQUIRED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.PUK_REQUIRED.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.PERSO_LOCKED.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.READY.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.NOT_READY.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.PERM_DISABLED.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.CARD_IO_ERROR.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.SIM_NETWORK_SUBSET_LOCKED.ordinal()] = 9;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.FOREVER.ordinal()] = 10;
            } catch (NoSuchFieldError e10) {
            }
            $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState = new int[IccCardApplicationStatus.AppState.values().length];
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN.ordinal()] = 1;
            } catch (NoSuchFieldError e11) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[IccCardApplicationStatus.AppState.APPSTATE_PIN.ordinal()] = 2;
            } catch (NoSuchFieldError e12) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[IccCardApplicationStatus.AppState.APPSTATE_PUK.ordinal()] = 3;
            } catch (NoSuchFieldError e13) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[IccCardApplicationStatus.AppState.APPSTATE_SUBSCRIPTION_PERSO.ordinal()] = 4;
            } catch (NoSuchFieldError e14) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[IccCardApplicationStatus.AppState.APPSTATE_READY.ordinal()] = 5;
            } catch (NoSuchFieldError e15) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[IccCardApplicationStatus.AppState.APPSTATE_DETECTED.ordinal()] = 6;
            } catch (NoSuchFieldError e16) {
            }
        }
    }

    private String getIccStateIntentString(IccCardConstants.State state) {
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$IccCardConstants$State[state.ordinal()]) {
            case 1:
                return "ABSENT";
            case 2:
                return "LOCKED";
            case 3:
                return "LOCKED";
            case 4:
                return "LOCKED";
            case 5:
                return "READY";
            case 6:
                return "NOT_READY";
            case 7:
                return "ABSENT";
            case 8:
                return "CARD_IO_ERROR";
            default:
                return "UNKNOWN";
        }
    }

    private String getIccStateReason(IccCardConstants.State state) {
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$IccCardConstants$State[state.ordinal()]) {
            case 2:
                return "PIN";
            case 3:
                return "PUK";
            case 4:
                return "PERSO";
            case 5:
            case 6:
            default:
                return null;
            case 7:
                return "PERM_DISABLED";
            case 8:
                return "CARD_IO_ERROR";
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public IccCardConstants.State getState() {
        IccCardConstants.State state;
        synchronized (this.mLock) {
            state = this.mExternalState;
        }
        return state;
    }

    @Override // com.android.internal.telephony.IccCard
    public IccRecords getIccRecords() {
        IccRecords iccRecords;
        synchronized (this.mLock) {
            iccRecords = this.mIccRecords;
        }
        return iccRecords;
    }

    @Override // com.android.internal.telephony.IccCard
    public IccFileHandler getIccFileHandler() {
        IccFileHandler iccFileHandler;
        synchronized (this.mLock) {
            iccFileHandler = this.mUiccApplication != null ? this.mUiccApplication.getIccFileHandler() : null;
        }
        return iccFileHandler;
    }

    @Override // com.android.internal.telephony.IccCard
    public int getSimLock() {
        return this.mSimLock;
    }

    @Override // com.android.internal.telephony.IccCard
    public void registerForAbsent(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mAbsentRegistrants.add(r);
            if (getState() == IccCardConstants.State.ABSENT) {
                r.notifyRegistrant();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void unregisterForAbsent(Handler h) {
        synchronized (this.mLock) {
            this.mAbsentRegistrants.remove(h);
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void registerForPersoLocked(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mPersoLockedRegistrants.add(r);
            if (getState() == IccCardConstants.State.PERSO_LOCKED) {
                r.notifyRegistrant(new AsyncResult((Object) null, Integer.valueOf(this.mPersoSubState.ordinal()), (Throwable) null));
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void unregisterForPersoLocked(Handler h) {
        synchronized (this.mLock) {
            this.mPersoLockedRegistrants.remove(h);
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void registerForLocked(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mPinLockedRegistrants.add(r);
            if (getState().isPinLocked()) {
                r.notifyRegistrant();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void unregisterForLocked(Handler h) {
        synchronized (this.mLock) {
            this.mPinLockedRegistrants.remove(h);
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void supplyPin(String pin, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.supplyPin(pin, onComplete);
            } else if (onComplete != null) {
                AsyncResult.forMessage(onComplete).exception = new RuntimeException("ICC card is absent.");
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void supplyPuk(String puk, String newPin, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.supplyPuk(puk, newPin, onComplete);
            } else if (onComplete != null) {
                AsyncResult.forMessage(onComplete).exception = new RuntimeException("ICC card is absent.");
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void supplyPin2(String pin2, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.supplyPin2(pin2, onComplete);
            } else if (onComplete != null) {
                AsyncResult.forMessage(onComplete).exception = new RuntimeException("ICC card is absent.");
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void supplyPuk2(String puk2, String newPin2, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.supplyPuk2(puk2, newPin2, onComplete);
            } else if (onComplete != null) {
                AsyncResult.forMessage(onComplete).exception = new RuntimeException("ICC card is absent.");
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void supplyDepersonalization(String pin, String type, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.supplyDepersonalization(pin, type, onComplete);
            } else if (onComplete != null) {
                AsyncResult.forMessage(onComplete).exception = new RuntimeException("CommandsInterface is not set.");
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public boolean getIccLockEnabled() {
        boolean booleanValue;
        synchronized (this.mLock) {
            booleanValue = Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccLockEnabled() : false).booleanValue();
        }
        return booleanValue;
    }

    @Override // com.android.internal.telephony.IccCard
    public boolean getIccFdnEnabled() {
        boolean booleanValue;
        synchronized (this.mLock) {
            booleanValue = Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccFdnEnabled() : false).booleanValue();
        }
        return booleanValue;
    }

    @Override // com.android.internal.telephony.IccCard
    public boolean getIccFdnAvailable() {
        if (this.mUiccApplication != null) {
            return this.mUiccApplication.getIccFdnAvailable();
        }
        return false;
    }

    @Override // com.android.internal.telephony.IccCard
    public boolean getIccPin2Blocked() {
        return Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccPin2Blocked() : false).booleanValue();
    }

    @Override // com.android.internal.telephony.IccCard
    public boolean getIccPuk2Blocked() {
        return Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccPuk2Blocked() : false).booleanValue();
    }

    @Override // com.android.internal.telephony.IccCard
    public void setIccLockEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.setIccLockEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                AsyncResult.forMessage(onComplete).exception = new RuntimeException("ICC card is absent.");
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void setIccFdnEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.setIccFdnEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                AsyncResult.forMessage(onComplete).exception = new RuntimeException("ICC card is absent.");
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void changeIccLockPassword(String oldPassword, String newPassword, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.changeIccLockPassword(oldPassword, newPassword, onComplete);
            } else if (onComplete != null) {
                AsyncResult.forMessage(onComplete).exception = new RuntimeException("ICC card is absent.");
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void changeIccFdnPassword(String oldPassword, String newPassword, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.changeIccFdnPassword(oldPassword, newPassword, onComplete);
            } else if (onComplete != null) {
                AsyncResult.forMessage(onComplete).exception = new RuntimeException("ICC card is absent.");
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public String getServiceProviderName() {
        String serviceProviderName;
        synchronized (this.mLock) {
            serviceProviderName = this.mIccRecords != null ? this.mIccRecords.getServiceProviderName() : null;
        }
        return serviceProviderName;
    }

    @Override // com.android.internal.telephony.IccCard
    public boolean isApplicationOnIcc(IccCardApplicationStatus.AppType type) {
        boolean booleanValue;
        synchronized (this.mLock) {
            booleanValue = Boolean.valueOf(this.mUiccCard != null ? this.mUiccCard.isApplicationOnIcc(type) : false).booleanValue();
        }
        return booleanValue;
    }

    @Override // com.android.internal.telephony.IccCard
    public boolean hasIccCard() {
        boolean z;
        synchronized (this.mLock) {
            z = (this.mUiccCard == null || this.mUiccCard.getCardState() == IccCardStatus.CardState.CARDSTATE_ABSENT) ? false : true;
        }
        return z;
    }

    private void setSystemProperty(String property, String value) {
        TelephonyManager.setTelephonyProperty(this.mPhoneId.intValue(), property, value);
    }

    private void log(String s) {
        Rlog.d(LOG_TAG, s);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    protected void updateExternalStateSharp() {
        if (this.mUiccCard != null) {
            if (this.mUiccCard.getCardState() == IccCardStatus.CardState.CARDSTATE_ERROR) {
                setExternalStateSharp(IccCardConstants.State.CARD_IO_ERROR);
            } else if (this.mUiccCard.getCardState() == IccCardStatus.CardState.CARDSTATE_ABSENT) {
                if (1 == SystemProperties.getInt(SIM_ABSENT_CHECK, 0)) {
                    setExternalStateSharp(IccCardConstants.State.ABSENT);
                } else {
                    Rlog.d(LOG_TAG, "updateExternalStateSharp: waiting for the actual SIM state...");
                }
            } else if (this.mUiccApplication == null) {
                setExternalStateSharp(IccCardConstants.State.UNKNOWN);
            } else if (this.mUiccApplication.getPin1State() == IccCardStatus.PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
                setExternalStateSharp(IccCardConstants.State.FOREVER);
            } else {
                switch (this.mUiccApplication.getState()) {
                    case APPSTATE_UNKNOWN:
                    case APPSTATE_DETECTED:
                        setExternalStateSharp(IccCardConstants.State.UNKNOWN);
                        return;
                    case APPSTATE_PIN:
                        setExternalStateSharp(IccCardConstants.State.PIN_REQUIRED);
                        return;
                    case APPSTATE_PUK:
                        setExternalStateSharp(IccCardConstants.State.PUK_REQUIRED);
                        return;
                    case APPSTATE_SUBSCRIPTION_PERSO:
                        if (this.mUiccApplication.isPersoLocked()) {
                            setExternalStateSharp(IccCardConstants.State.SIM_NETWORK_SUBSET_LOCKED);
                            return;
                        } else {
                            setExternalStateSharp(IccCardConstants.State.UNKNOWN);
                            return;
                        }
                    case APPSTATE_READY:
                        setExternalStateSharp(IccCardConstants.State.READY);
                        return;
                    default:
                        return;
                }
            }
        }
    }

    protected void setExternalStateSharp(IccCardConstants.State newState, boolean override) {
        synchronized (this.mLock) {
            if (!override) {
                if (newState == this.mExternalStateSharp) {
                }
            }
            this.mExternalStateSharp = newState;
            if (newState != IccCardConstants.State.PIN_REQUIRED && newState != IccCardConstants.State.PUK_REQUIRED && newState != IccCardConstants.State.PERSO_LOCKED && newState != IccCardConstants.State.READY && newState != IccCardConstants.State.PERM_DISABLED) {
                broadcastIccStateChangedIntentSharp(getIccStateIntentStringSharp(this.mExternalStateSharp), getIccStateReasonSharp(this.mExternalStateSharp));
            }
        }
    }

    private void setExternalStateSharp(IccCardConstants.State newState) {
        setExternalStateSharp(newState, false);
    }

    private void notifyCurrentExternalStateSharp() {
        synchronized (this.mLock) {
            broadcastIccStateChangedIntentSharp(getIccStateIntentStringSharp(this.mExternalStateSharp), getIccStateReasonSharp(this.mExternalStateSharp));
        }
    }

    protected String getIccStateIntentStringSharp(IccCardConstants.State state) {
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$IccCardConstants$State[state.ordinal()]) {
            case 1:
                return "ABSENT";
            case 2:
                return "LOCKED";
            case 3:
                return "LOCKED";
            case 4:
                return "LOCKED";
            case 5:
                return "READY";
            case 6:
                return "NOT_READY";
            case 7:
                return "LOCKED";
            case 8:
                return "CARD_IO_ERROR";
            case 9:
                return "LOCKED";
            case 10:
                return "LOCKED";
            default:
                return "UNKNOWN";
        }
    }

    protected String getIccStateReasonSharp(IccCardConstants.State state) {
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$IccCardConstants$State[state.ordinal()]) {
            case 2:
                return "PIN";
            case 3:
                return "PUK";
            case 4:
                return "PERSO";
            case 5:
            case 6:
            default:
                return null;
            case 7:
                return "PERM_DISABLED";
            case 8:
                return "CARD_IO_ERROR";
            case 9:
                return IccCard.INTENT_VALUE_LOCKED_NETWORK_SUBSET;
            case 10:
                return IccCard.INTENT_VALUE_LOCKED_FOREVER;
        }
    }

    public void broadcastIccStateChangedIntentSharp(String value, String reason) {
        synchronized (this.mLock) {
            if (this.mQuietMode) {
                log("QuietMode: NOT Broadcasting intent ACTION_SIM_STATE_CHANGED_SHARP " + value + " reason " + reason);
                return;
            }
            Intent intent = new Intent("jp.co.sharp.android.uim.intent.action.SIM_STATE_CHANGED_SHARP");
            intent.addFlags(536870912);
            intent.putExtra("phoneName", "Phone");
            intent.putExtra(IccCard.INTENT_KEY_ICC_STATE_SHARP, value);
            intent.putExtra(IccCard.INTENT_KEY_LOCKED_REASON_SHARP, reason);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhoneId.intValue());
            log("Broadcasting intent ACTION_SIM_STATE_CHANGED_SHARP " + value + " reason " + reason + " for mPhoneId : " + this.mPhoneId);
            ActivityManagerNative.broadcastStickyIntent(intent, "android.permission.READ_PHONE_STATE", -1);
            queueNextBroadcastSharpPoll(value, reason);
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void responseBroadcastIntentSharp() {
        synchronized (this.mLock) {
            log("responseBroadcastIntentSharp");
            this.dontPollBroadcastSharp = true;
        }
    }

    private void queueNextBroadcastSharpPoll(String value, String reason) {
        if (!this.dontPollBroadcastSharp) {
            Message msg = obtainMessage();
            msg.what = 30;
            msg.obj = new String[]{value, reason};
            sendMessageDelayed(msg, 1000L);
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public int getIccPinPukRetryCountSc(int isPinPuk) {
        int retValue;
        synchronized (this.mLock) {
            retValue = this.mUiccCard != null ? this.mUiccCard.getIccPinPukRetryCountSc(isPinPuk) : -1;
        }
        return retValue;
    }

    private void requestGetPinPukRetryCount() {
        byte[] request = new byte["QOEMHOOK".length() + 8];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put("QOEMHOOK".getBytes());
        reqBuffer.putInt(589824 + 102);
        this.mCi.invokeOemRilRequestRaw(request, obtainMessage(17));
    }

    private void onGetPinPukRetryCountDone(AsyncResult ar) {
        synchronized (this.mLock) {
            if (this.mUiccCard == null) {
                loge("Error in get pin puk retry count with mUiccCard is null");
                return;
            }
            if (ar.exception != null) {
                loge("Error in get pin puk retry count with exception: " + ar.exception);
                this.mUiccCard.setPinPukRetryCount(0, -1);
            } else if (ar.result != null) {
                byte[] ret = (byte[]) ar.result;
                this.mUiccCard.setPinPukRetryCount(1, ret[0]);
                this.mUiccCard.setPinPukRetryCount(2, ret[1]);
                this.mUiccCard.setPinPukRetryCount(3, ret[2]);
                this.mUiccCard.setPinPukRetryCount(4, ret[3]);
            } else {
                loge("No result in get pin puk retry count");
                this.mUiccCard.setPinPukRetryCount(0, -1);
            }
        }
    }

    private void notifyCurrentExternalState() {
        synchronized (this.mLock) {
            if (!TelBrand.IS_DCM) {
                SystemProperties.set("gsm.sim.state", this.mExternalState.toString());
            } else if (!ENCRYPTED_STATE.equals(SystemProperties.get(PROP_RO_CRYPTO_STATE)) || !MIN_FRAMEWORK_STATE.equals(SystemProperties.get(PROP_VOLD_DECRYPT)) || "NOT_READY".equals(getIccStateIntentString(this.mExternalState)) || "ABSENT".equals(getIccStateIntentString(this.mExternalState))) {
                SystemProperties.set("gsm.sim.state", this.mExternalState.toString());
            }
            broadcastIccStateChangedIntent(getIccStateIntentString(this.mExternalState), getIccStateReason(this.mExternalState));
            if (IccCardConstants.State.ABSENT == this.mExternalState) {
                this.mAbsentRegistrants.notifyRegistrants();
            }
        }
    }

    private void requestUnsolRadioStateChanged() {
        byte[] request = new byte["QOEMHOOK".length() + 8];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put("QOEMHOOK".getBytes());
        reqBuffer.putInt(591824 + PduPart.P_CONTENT_TRANSFER_ENCODING + 5);
        this.mCi.invokeOemRilRequestRaw(request, null);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("IccCardProxy: " + this);
        pw.println(" mContext=" + this.mContext);
        pw.println(" mCi=" + this.mCi);
        pw.println(" mAbsentRegistrants: size=" + this.mAbsentRegistrants.size());
        for (int i = 0; i < this.mAbsentRegistrants.size(); i++) {
            pw.println("  mAbsentRegistrants[" + i + "]=" + ((Registrant) this.mAbsentRegistrants.get(i)).getHandler());
        }
        pw.println(" mPinLockedRegistrants: size=" + this.mPinLockedRegistrants.size());
        for (int i2 = 0; i2 < this.mPinLockedRegistrants.size(); i2++) {
            pw.println("  mPinLockedRegistrants[" + i2 + "]=" + ((Registrant) this.mPinLockedRegistrants.get(i2)).getHandler());
        }
        pw.println(" mPersoLockedRegistrants: size=" + this.mPersoLockedRegistrants.size());
        for (int i3 = 0; i3 < this.mPersoLockedRegistrants.size(); i3++) {
            pw.println("  mPersoLockedRegistrants[" + i3 + "]=" + ((Registrant) this.mPersoLockedRegistrants.get(i3)).getHandler());
        }
        pw.println(" mCurrentAppType=" + this.mCurrentAppType);
        pw.println(" mUiccController=" + this.mUiccController);
        pw.println(" mUiccCard=" + this.mUiccCard);
        pw.println(" mUiccApplication=" + this.mUiccApplication);
        pw.println(" mIccRecords=" + this.mIccRecords);
        pw.println(" mCdmaSSM=" + this.mCdmaSSM);
        pw.println(" mRadioOn=" + this.mRadioOn);
        pw.println(" mQuietMode=" + this.mQuietMode);
        pw.println(" mInitialized=" + this.mInitialized);
        pw.println(" mExternalState=" + this.mExternalState);
        pw.flush();
    }

    @Override // com.android.internal.telephony.IccCard
    public void exchangeAPDU(int cla, int command, int channel, int p1, int p2, int p3, String data, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.exchangeAPDU(cla, command, channel, p1, p2, p3, data, onComplete);
            } else {
                log("UiccApplication is not exist.");
                AsyncResult.forMessage(onComplete).exception = new RuntimeException("ICC card is absent.");
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void openLogicalChannel(String AID, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication == null) {
                log("UiccApplication is not exist.");
                AsyncResult.forMessage(onComplete).exception = new RuntimeException("ICC card is absent.");
                onComplete.sendToTarget();
            } else if (TelBrand.IS_DCM) {
                this.mIccOpenLogicalChannel.iccOpenChannel(AID, obtainMessage(21, onComplete));
            } else {
                this.mUiccApplication.openLogicalChannel(AID, onComplete);
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void closeLogicalChannel(int channel, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.closeLogicalChannel(channel, onComplete);
            } else {
                log("UiccApplication is not exist.");
                AsyncResult.forMessage(onComplete).exception = new RuntimeException("ICC card is absent.");
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void exchangeSimIO(int fileID, int command, int p1, int p2, int p3, String pathID, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.exchangeSimIO(fileID, command, p1, p2, p3, pathID, onComplete);
            } else {
                log("UiccApplication is not exist.");
                AsyncResult.forMessage(onComplete).exception = new RuntimeException("ICC card is absent.");
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void getEfLock(Message onComplete) {
        if (TelBrand.IS_DCM) {
            synchronized (this.mLock) {
                IccFileHandler fh = getIccFileHandler();
                boolean isTypeUsim = this.mUiccApplication != null && this.mUiccApplication.getType() == IccCardApplicationStatus.AppType.APPTYPE_USIM;
                if (fh == null || !isTypeUsim) {
                    AsyncResult.forMessage(onComplete, (Object) null, (Throwable) null);
                    onComplete.sendToTarget();
                } else {
                    fh.loadEFTransparent(IccConstants.EF_LOCK, 8, obtainMessage(23, onComplete));
                }
            }
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    private class IccOpenLogicalChannel extends Handler {
        private static final String CAS_AID = "F0000000010001FF81FF10FFFFFFFF02";
        public static final int DEFAULT_MAX_CHANNEL = 4;
        private static final int EVENT_GET_STATUS_DONE = 2;
        private static final int EVENT_GET_TMM_DONE = 1;
        private static final int EVENT_OPEN_CHANNEL_DONE = 3;
        private int mMaxChannels = 4;
        private int mRequestCount = 0;

        /* JADX INFO: Access modifiers changed from: private */
        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        public class LookForVacantChannel {
            public static final int TMM_NOT_OPEN_CHANNEL = 0;
            private String mAID;
            private int mMaxChannels;
            private Message mOnComplete;
            private int mCheckedNo = 0;
            private int mVacantCount = 0;
            private int mTMMChannel = 0;

            public LookForVacantChannel(String AID, Message onComplete, int max) {
                this.mMaxChannels = 4;
                this.mAID = null;
                this.mOnComplete = null;
                this.mAID = AID;
                this.mOnComplete = onComplete;
                this.mMaxChannels = max;
            }

            public boolean isCheckEnd() {
                return this.mCheckedNo >= this.mMaxChannels;
            }

            public boolean isOpenChannel() {
                return this.mTMMChannel > 0 ? this.mVacantCount > 0 : this.mVacantCount + (-1) > 0;
            }

            public int reqNextChannelWithUpdate() {
                this.mCheckedNo++;
                if (this.mTMMChannel == this.mCheckedNo) {
                    this.mCheckedNo++;
                }
                return this.mCheckedNo;
            }

            public void addVacant() {
                this.mVacantCount++;
            }

            public String getAID() {
                return this.mAID;
            }

            public Message getOnComplete() {
                return this.mOnComplete;
            }

            public void setTMMChannel(int channel) {
                this.mTMMChannel = channel;
            }
        }

        public IccOpenLogicalChannel() {
        }

        public void iccOpenChannel(String AID, Message onComplete) {
            getTMMChannel(CAS_AID, new LookForVacantChannel(AID, onComplete, this.mMaxChannels));
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            LookForVacantChannel result = (LookForVacantChannel) ar.userObj;
            if (ar.exception != null) {
                sendFailureMessage(result.getOnComplete(), ar.exception);
                return;
            }
            switch (msg.what) {
                case 1:
                    checkGetCardStatusResponse(result, (IccIoResult) ar.result);
                    break;
                case 2:
                    checkGetStatusResponse(result, (IccIoResult) ar.result);
                    break;
                case 3:
                    sendSuccessMessage(result.getOnComplete(), ar);
                    return;
            }
            int channel = result.reqNextChannelWithUpdate();
            if (!result.isCheckEnd()) {
                getChannelStatus(result, channel);
            } else if (result.isOpenChannel()) {
                getOpenLogicalChannel(result);
            } else {
                sendNonVacantChannelMessage(result.getOnComplete());
            }
        }

        private String eventString(int event) {
            switch (event) {
                case 1:
                    return "EVENT_GET_TMM_DONE";
                case 2:
                    return "EVENT_GET_STATUS_DONE";
                case 3:
                    return "EVENT_OPEN_CHANNEL_DONE";
                default:
                    return "Unknown event";
            }
        }

        private void checkGetCardStatusResponse(LookForVacantChannel result, IccIoResult responseApdu) {
            if (responseApdu.sw1 == 106 && responseApdu.sw2 == 130) {
                result.setTMMChannel(0);
            } else if (!responseApdu.success()) {
                result.setTMMChannel(0);
            } else {
                int len = responseApdu.payload.length;
                if (len > 1) {
                }
                if (len > 0) {
                    result.setTMMChannel(responseApdu.payload[0]);
                } else {
                    result.setTMMChannel(0);
                }
            }
        }

        private void checkGetStatusResponse(LookForVacantChannel result, IccIoResult responseApdu) {
            if (responseApdu.sw1 == 104 && responseApdu.sw2 == 129) {
                result.addVacant();
            }
        }

        private void getTMMChannel(String AID, LookForVacantChannel result) {
            IccCardProxy.this.mCi.iccExchangeAPDU(144, 242, 0, 0, 0, AID.length() / 2, AID, obtainMessage(1, result));
        }

        private void getChannelStatus(LookForVacantChannel result, int channel) {
            int cla;
            if (channel < 4) {
                cla = channel + 128;
            } else {
                cla = (channel + 192) - 4;
            }
            IccCardProxy.this.mCi.iccExchangeAPDU(cla, 242, 0, 0, 0, 0, null, obtainMessage(2, result));
        }

        private void getOpenLogicalChannel(LookForVacantChannel result) {
            IccCardProxy.this.mUiccApplication.openLogicalChannel(result.getAID(), obtainMessage(3, result));
        }

        private void sendSuccessMessage(Message message, AsyncResult ar) {
            ar.userObj = message.obj;
            message.obj = ar;
            message.sendToTarget();
        }

        private void sendNonVacantChannelMessage(Message message) {
            AsyncResult.forMessage(message, new int[]{0}, new CommandException(CommandException.Error.MISSING_RESOURCE));
            message.sendToTarget();
        }

        private void sendFailureMessage(Message message, Throwable throwable) {
            AsyncResult.forMessage(message, new int[]{0}, throwable);
            message.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void setPinPukRetryCount(int isPinPuk, int RetryCount) {
        synchronized (this.mLock) {
            if (this.mUiccCard == null) {
                loge("Error in set pin puk retry count with mUiccCard is null");
            } else {
                this.mUiccCard.setPinPukRetryCount(isPinPuk, RetryCount);
            }
        }
    }
}
