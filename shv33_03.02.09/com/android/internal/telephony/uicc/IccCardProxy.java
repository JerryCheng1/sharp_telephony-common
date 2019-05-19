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
import android.text.TextUtils;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.TelephonyEventLog;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.PersoSubState;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccCardStatus.PinState;
import com.google.android.mms.pdu.PduPart;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class IccCardProxy extends Handler implements IccCard {
    /* renamed from: -com-android-internal-telephony-IccCardConstants$StateSwitchesValues */
    private static final /* synthetic */ int[] f22x8dbfd0b5 = null;
    /* renamed from: -com-android-internal-telephony-uicc-IccCardApplicationStatus$AppStateSwitchesValues */
    private static final /* synthetic */ int[] f23x3dee1264 = null;
    public static final String ACTION_INTERNAL_SIM_STATE_CHANGED = "android.intent.action.internal_sim_state_changed";
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
    private static final int EVENT_NETWORK_LOCKED = 9;
    private static final int EVENT_OEM_HOOK_RAW_RADIO_STATE = 24;
    private static final int EVENT_OPEN_CHANNEL_DONE_SHARP = 21;
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
    private RegistrantList mAbsentRegistrants = new RegistrantList();
    private CdmaSubscriptionSourceManager mCdmaSSM = null;
    private CommandsInterface mCi;
    private Context mContext;
    private int mCurrentAppType = 1;
    private State mExternalState = State.UNKNOWN;
    private IccRecords mIccRecords = null;
    private boolean mInitialized = false;
    private final Object mLock = new Object();
    private RegistrantList mNetworkLockedRegistrants = new RegistrantList();
    private PersoSubState mPersoSubState = PersoSubState.PERSOSUBSTATE_UNKNOWN;
    private Integer mPhoneId = null;
    private RegistrantList mPinLockedRegistrants = new RegistrantList();
    private boolean mQuietMode = false;
    private boolean mRadioOn = false;
    private int mSimLock = 0;
    private TelephonyManager mTelephonyManager;
    private UiccCardApplication mUiccApplication = null;
    private UiccCard mUiccCard = null;
    private UiccController mUiccController = null;

    private static /* synthetic */ int[] -getcom-android-internal-telephony-IccCardConstants$StateSwitchesValues() {
        if (f22x8dbfd0b5 != null) {
            return f22x8dbfd0b5;
        }
        int[] iArr = new int[State.values().length];
        try {
            iArr[State.ABSENT.ordinal()] = 1;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[State.CARD_IO_ERROR.ordinal()] = 2;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[State.NETWORK_LOCKED.ordinal()] = 3;
        } catch (NoSuchFieldError e3) {
        }
        try {
            iArr[State.NOT_READY.ordinal()] = 4;
        } catch (NoSuchFieldError e4) {
        }
        try {
            iArr[State.PERM_DISABLED.ordinal()] = 5;
        } catch (NoSuchFieldError e5) {
        }
        try {
            iArr[State.PIN_REQUIRED.ordinal()] = 6;
        } catch (NoSuchFieldError e6) {
        }
        try {
            iArr[State.PUK_REQUIRED.ordinal()] = 7;
        } catch (NoSuchFieldError e7) {
        }
        try {
            iArr[State.READY.ordinal()] = 8;
        } catch (NoSuchFieldError e8) {
        }
        try {
            iArr[State.UNKNOWN.ordinal()] = 15;
        } catch (NoSuchFieldError e9) {
        }
        f22x8dbfd0b5 = iArr;
        return iArr;
    }

    private static /* synthetic */ int[] -getcom-android-internal-telephony-uicc-IccCardApplicationStatus$AppStateSwitchesValues() {
        if (f23x3dee1264 != null) {
            return f23x3dee1264;
        }
        int[] iArr = new int[AppState.values().length];
        try {
            iArr[AppState.APPSTATE_DETECTED.ordinal()] = 1;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[AppState.APPSTATE_PIN.ordinal()] = 2;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[AppState.APPSTATE_PUK.ordinal()] = 3;
        } catch (NoSuchFieldError e3) {
        }
        try {
            iArr[AppState.APPSTATE_READY.ordinal()] = 4;
        } catch (NoSuchFieldError e4) {
        }
        try {
            iArr[AppState.APPSTATE_SUBSCRIPTION_PERSO.ordinal()] = 5;
        } catch (NoSuchFieldError e5) {
        }
        try {
            iArr[AppState.APPSTATE_UNKNOWN.ordinal()] = 6;
        } catch (NoSuchFieldError e6) {
        }
        f23x3dee1264 = iArr;
        return iArr;
    }

    public IccCardProxy(Context context, CommandsInterface ci, int phoneId) {
        log("ctor: ci=" + ci + " phoneId=" + phoneId);
        this.mContext = context;
        this.mCi = ci;
        this.mPhoneId = Integer.valueOf(phoneId);
        this.mTelephonyManager = (TelephonyManager) this.mContext.getSystemService("phone");
        this.mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(context, ci, this, 11, null);
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 3, null);
        ci.registerForOn(this, 2, null);
        ci.registerForOffOrNotAvailable(this, 1, null);
        resetProperties();
        setExternalState(State.NOT_READY, false);
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
        }
    }

    private void updateQuietMode() {
        synchronized (this.mLock) {
            boolean newQuietMode;
            int cdmaSource = -1;
            if (this.mCurrentAppType == 1) {
                newQuietMode = false;
                log("updateQuietMode: 3GPP subscription -> newQuietMode=" + false);
            } else {
                cdmaSource = this.mCdmaSSM != null ? this.mCdmaSSM.getCdmaSubscriptionSource() : -1;
                newQuietMode = cdmaSource == 1 ? this.mCurrentAppType == 2 : false;
            }
            if (!this.mQuietMode && newQuietMode) {
                log("Switching to QuietMode.");
                setExternalState(State.READY);
                this.mQuietMode = newQuietMode;
            } else if (!this.mQuietMode || newQuietMode) {
                log("updateQuietMode: no changes don't setExternalState");
            } else {
                log("updateQuietMode: Switching out from QuietMode. Force broadcast of current state=" + this.mExternalState);
                this.mQuietMode = newQuietMode;
                setExternalState(this.mExternalState, true);
            }
            log("updateQuietMode: QuietMode is " + this.mQuietMode + " (app_type=" + this.mCurrentAppType + " cdmaSource=" + cdmaSource + ")");
            this.mInitialized = true;
            sendMessage(obtainMessage(3));
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:80:0x0359  */
    /* JADX WARNING: Removed duplicated region for block: B:78:0x034a  */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                this.mRadioOn = false;
                if (RadioState.RADIO_UNAVAILABLE == this.mCi.getRadioState()) {
                    setExternalState(State.NOT_READY);
                    return;
                }
                return;
            case 2:
                this.mRadioOn = true;
                if (!this.mInitialized) {
                    updateQuietMode();
                    return;
                }
                return;
            case 3:
                if (this.mInitialized) {
                    updateIccAvailability();
                    return;
                }
                return;
            case 4:
                this.mAbsentRegistrants.notifyRegistrants();
                setExternalState(State.ABSENT);
                return;
            case 5:
                processLockedState();
                return;
            case 6:
                setExternalState(State.READY);
                return;
            case 7:
                if (this.mIccRecords != null) {
                    String operator = PhoneFactory.getPhone(this.mPhoneId.intValue()).getOperatorNumeric();
                    log("operator=" + operator + " mPhoneId=" + this.mPhoneId);
                    if (TextUtils.isEmpty(operator)) {
                        loge("EVENT_RECORDS_LOADED Operator name is null");
                    } else {
                        this.mTelephonyManager.setSimOperatorNumericForPhone(this.mPhoneId.intValue(), operator);
                        String countryCode = operator.substring(0, 3);
                        if (countryCode != null) {
                            this.mTelephonyManager.setSimCountryIsoForPhone(this.mPhoneId.intValue(), MccTable.countryCodeForMcc(Integer.parseInt(countryCode)));
                        } else {
                            loge("EVENT_RECORDS_LOADED Country code is null");
                        }
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
                this.mNetworkLockedRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                setExternalState(State.NETWORK_LOCKED);
                return;
            case 11:
                updateQuietMode();
                return;
            case 17:
                onGetPinPukRetryCountDone(msg.obj);
                notifyCurrentExternalState();
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
                        sendMessageDelayed(oemhookmsg, 1000);
                    }
                }
                return;
            case 25:
                log("EVENT_GET_SIM_LOCK_DONE");
                AsyncResult arSimLock = msg.obj;
                if (arSimLock.exception != null) {
                    loge("Error in get sim lock with exception: " + arSimLock.exception);
                    this.mSimLock = 1;
                    return;
                } else if (arSimLock.result != null) {
                    int simLockState = 3;
                    byte[] ret = (byte[]) arSimLock.result;
                    ByteBuffer bbRet = ByteBuffer.wrap(ret);
                    if (64 == ret.length) {
                        for (int index = 0; (index + 4) - 1 < ret.length; index += 8) {
                            if (bbRet.getInt(index) != 0) {
                                simLockState = 2;
                                this.mSimLock = simLockState;
                                if (2 != this.mSimLock) {
                                    log("mSimlock is ON");
                                    return;
                                } else {
                                    log("mSimlock is OFF");
                                    return;
                                }
                            }
                        }
                        this.mSimLock = simLockState;
                        if (2 != this.mSimLock) {
                        }
                        break;
                    }
                    this.mSimLock = 1;
                    loge("The response has unexpected size: " + ret.length);
                    return;
                } else {
                    this.mSimLock = 1;
                    loge("No result in get sim lock");
                    return;
                }
            case EVENT_ICC_RECORD_EVENTS /*500*/:
                if (this.mCurrentAppType == 1 && this.mIccRecords != null && ((Integer) msg.obj.result).intValue() == 2) {
                    this.mTelephonyManager.setSimOperatorNameForPhone(this.mPhoneId.intValue(), this.mIccRecords.getServiceProviderName());
                    return;
                }
                return;
            case EVENT_SUBSCRIPTION_ACTIVATED /*501*/:
                log("EVENT_SUBSCRIPTION_ACTIVATED");
                onSubscriptionActivated();
                return;
            case EVENT_SUBSCRIPTION_DEACTIVATED /*502*/:
                log("EVENT_SUBSCRIPTION_DEACTIVATED");
                onSubscriptionDeactivated();
                return;
            case EVENT_CARRIER_PRIVILIGES_LOADED /*503*/:
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
        broadcastInternalIccStateChangedIntent("LOADED", null);
    }

    /* JADX WARNING: Missing block: B:18:0x0046, code skipped:
            if (r7.mUiccCard != r1) goto L_0x002d;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void updateIccAvailability() {
        synchronized (this.mLock) {
            UiccCard newCard = this.mUiccController.getUiccCard(this.mPhoneId.intValue());
            CardState state = CardState.CARDSTATE_ABSENT;
            UiccCardApplication newApp = null;
            IccRecords newRecords = null;
            if (newCard != null) {
                state = newCard.getCardState();
                newApp = newCard.getApplication(this.mCurrentAppType);
                if (newApp != null) {
                    newRecords = newApp.getIccRecords();
                }
            }
            if (this.mIccRecords == newRecords && this.mUiccApplication == newApp) {
            }
            log("Icc changed. Reregestering.");
            unregisterUiccCardEvents();
            this.mUiccCard = newCard;
            this.mUiccApplication = newApp;
            this.mIccRecords = newRecords;
            registerUiccCardEvents();
            updateExternalState();
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void resetProperties() {
        if (this.mCurrentAppType == 1) {
            log("update icc_operator_numeric=");
            this.mTelephonyManager.setSimOperatorNumericForPhone(this.mPhoneId.intValue(), "");
            this.mTelephonyManager.setSimCountryIsoForPhone(this.mPhoneId.intValue(), "");
            this.mTelephonyManager.setSimOperatorNameForPhone(this.mPhoneId.intValue(), "");
        }
    }

    private void HandleDetectedState() {
    }

    private void updateExternalState() {
        if (this.mUiccCard == null) {
            setExternalState(State.NOT_READY);
        } else if (this.mUiccCard.getCardState() == CardState.CARDSTATE_ABSENT) {
            if (SystemProperties.getInt("persist.radio.apm_sim_not_pwdn", 0) != 0) {
                setExternalState(State.ABSENT);
                if (1 == SystemProperties.getInt(SIM_ABSENT_CHECK, 0)) {
                    broadcastIccStateChangedIntentAbsent();
                }
            } else if (this.mRadioOn) {
                setExternalState(State.ABSENT);
            } else {
                setExternalState(State.NOT_READY);
            }
        } else if (this.mUiccCard.getCardState() == CardState.CARDSTATE_ERROR) {
            if (TelBrand.IS_DCM) {
                setExternalState(State.ABSENT);
            } else {
                setExternalState(State.CARD_IO_ERROR);
            }
        } else if (this.mUiccApplication == null) {
            setExternalState(State.NOT_READY);
        } else {
            switch (-getcom-android-internal-telephony-uicc-IccCardApplicationStatus$AppStateSwitchesValues()[this.mUiccApplication.getState().ordinal()]) {
                case 1:
                    HandleDetectedState();
                    break;
                case 2:
                    setExternalState(State.PIN_REQUIRED);
                    break;
                case 3:
                    log("[UIM]updateExternalState app_state : APPSTATE_PUK");
                    PinState pin1State = this.mUiccApplication.getPin1State();
                    log("[UIM]updateExternalState pin1State : " + pin1State);
                    if (pin1State != PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
                        setExternalState(State.PUK_REQUIRED);
                        break;
                    } else {
                        setExternalState(State.PERM_DISABLED);
                        break;
                    }
                case 4:
                    setExternalState(State.READY);
                    break;
                case 5:
                    if (!this.mUiccApplication.isPersoLocked()) {
                        setExternalState(State.UNKNOWN);
                        break;
                    }
                    this.mPersoSubState = this.mUiccApplication.getPersoSubState();
                    setExternalState(State.NETWORK_LOCKED);
                    break;
                case 6:
                    setExternalState(State.UNKNOWN);
                    break;
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
            this.mUiccApplication.registerForNetworkLocked(this, 9, null);
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
            this.mUiccApplication.unregisterForNetworkLocked(this);
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
        this.mTelephonyManager.setSimStateForPhone(this.mPhoneId.intValue(), getState().toString());
    }

    private void broadcastIccStateChangedIntent(String value, String reason) {
        synchronized (this.mLock) {
            if (this.mPhoneId == null || !SubscriptionManager.isValidSlotId(this.mPhoneId.intValue())) {
                loge("broadcastIccStateChangedIntent: mPhoneId=" + this.mPhoneId + " is invalid; Return!!");
            } else if (this.mQuietMode) {
                log("broadcastIccStateChangedIntent: QuietMode NOT Broadcasting intent ACTION_SIM_STATE_CHANGED  value=" + value + " reason=" + reason);
            } else {
                if (TelBrand.IS_DCM && ENCRYPTED_STATE.equals(SystemProperties.get(PROP_RO_CRYPTO_STATE)) && MIN_FRAMEWORK_STATE.equals(SystemProperties.get(PROP_VOLD_DECRYPT)) && !"NOT_READY".equals(value)) {
                    if (!"ABSENT".equals(value)) {
                        log("Encrypted: NOT Broadcasting intent ACTION_SIM_STATE_CHANGED " + value + " reason " + reason);
                        return;
                    }
                }
                Intent intent = new Intent("android.intent.action.SIM_STATE_CHANGED");
                intent.addFlags(67108864);
                intent.putExtra("phoneName", "Phone");
                intent.putExtra("ss", value);
                intent.putExtra(TelephonyEventLog.DATA_KEY_DATA_DEACTIVATE_REASON, reason);
                SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhoneId.intValue());
                log("broadcastIccStateChangedIntent intent ACTION_SIM_STATE_CHANGED value=" + value + " reason=" + reason + " for mPhoneId=" + this.mPhoneId);
                ActivityManagerNative.broadcastStickyIntent(intent, "android.permission.READ_PHONE_STATE", -1);
            }
        }
    }

    private void broadcastInternalIccStateChangedIntent(String value, String reason) {
        synchronized (this.mLock) {
            if (this.mPhoneId == null) {
                loge("broadcastInternalIccStateChangedIntent: Card Index is not set; Return!!");
                return;
            }
            Intent intent = new Intent(ACTION_INTERNAL_SIM_STATE_CHANGED);
            intent.addFlags(603979776);
            intent.putExtra("phoneName", "Phone");
            intent.putExtra("ss", value);
            intent.putExtra(TelephonyEventLog.DATA_KEY_DATA_DEACTIVATE_REASON, reason);
            intent.putExtra("phone", this.mPhoneId);
            log("broadcastInternalIccStateChangedIntent intent ACTION_INTERNAL_SIM_STATE_CHANGED value=" + value + " reason=" + reason + " for mPhoneId=" + this.mPhoneId);
            ActivityManagerNative.broadcastStickyIntent(intent, null, -1);
        }
    }

    /* JADX WARNING: Missing block: B:53:0x00f4, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void setExternalState(State newState, boolean override) {
        synchronized (this.mLock) {
            if (this.mPhoneId == null || !SubscriptionManager.isValidSlotId(this.mPhoneId.intValue())) {
                loge("setExternalState: mPhoneId=" + this.mPhoneId + " is invalid; Return!!");
            } else if (override || newState != this.mExternalState) {
                this.mExternalState = newState;
                if (!(newState == State.PIN_REQUIRED || newState == State.PUK_REQUIRED)) {
                    if (!(newState == State.NETWORK_LOCKED || newState == State.READY || newState == State.PERM_DISABLED)) {
                        if (this.mUiccCard != null) {
                            this.mUiccCard.setPinPukRetryCount(0, -1);
                        }
                        if (!TelBrand.IS_DCM) {
                            loge("setExternalState: set mPhoneId=" + this.mPhoneId + " mExternalState=" + this.mExternalState);
                            this.mTelephonyManager.setSimStateForPhone(this.mPhoneId.intValue(), getState().toString());
                        } else if (!ENCRYPTED_STATE.equals(SystemProperties.get(PROP_RO_CRYPTO_STATE)) || !MIN_FRAMEWORK_STATE.equals(SystemProperties.get(PROP_VOLD_DECRYPT)) || "NOT_READY".equals(getIccStateIntentString(this.mExternalState)) || "ABSENT".equals(getIccStateIntentString(this.mExternalState))) {
                            setSystemProperty("gsm.sim.state", getState().toString());
                        }
                        if ("LOCKED".equals(getIccStateIntentString(this.mExternalState))) {
                            broadcastInternalIccStateChangedIntent(getIccStateIntentString(this.mExternalState), getIccStateReason(this.mExternalState));
                        } else {
                            broadcastIccStateChangedIntent(getIccStateIntentString(this.mExternalState), getIccStateReason(this.mExternalState));
                        }
                        if (State.ABSENT == this.mExternalState) {
                            this.mAbsentRegistrants.notifyRegistrants();
                        }
                    }
                }
                requestGetPinPukRetryCount();
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

    /* JADX WARNING: Missing block: B:17:0x002e, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void processLockedState() {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                if (this.mUiccApplication.getPin1State() != PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
                    switch (-getcom-android-internal-telephony-uicc-IccCardApplicationStatus$AppStateSwitchesValues()[this.mUiccApplication.getState().ordinal()]) {
                        case 2:
                            this.mPinLockedRegistrants.notifyRegistrants();
                            setExternalState(State.PIN_REQUIRED);
                            break;
                        case 3:
                            setExternalState(State.PUK_REQUIRED);
                            break;
                    }
                }
                setExternalState(State.PERM_DISABLED);
                return;
            }
        }
    }

    private void setExternalState(State newState) {
        setExternalState(newState, false);
    }

    public boolean getIccRecordsLoaded() {
        synchronized (this.mLock) {
            if (this.mIccRecords != null) {
                boolean recordsLoaded = this.mIccRecords.getRecordsLoaded();
                return recordsLoaded;
            }
            return false;
        }
    }

    private String getIccStateIntentString(State state) {
        switch (-getcom-android-internal-telephony-IccCardConstants$StateSwitchesValues()[state.ordinal()]) {
            case 1:
                return "ABSENT";
            case 2:
                return "CARD_IO_ERROR";
            case 3:
                return "LOCKED";
            case 4:
                return "NOT_READY";
            case 5:
                return "ABSENT";
            case 6:
                return "LOCKED";
            case 7:
                return "LOCKED";
            case 8:
                return "READY";
            default:
                return "UNKNOWN";
        }
    }

    private String getIccStateReason(State state) {
        switch (-getcom-android-internal-telephony-IccCardConstants$StateSwitchesValues()[state.ordinal()]) {
            case 2:
                return "CARD_IO_ERROR";
            case 3:
                return "NETWORK";
            case 5:
                return "PERM_DISABLED";
            case 6:
                return "PIN";
            case 7:
                return "PUK";
            default:
                return null;
        }
    }

    public State getState() {
        State state;
        synchronized (this.mLock) {
            state = this.mExternalState;
        }
        return state;
    }

    public IccRecords getIccRecords() {
        IccRecords iccRecords;
        synchronized (this.mLock) {
            iccRecords = this.mIccRecords;
        }
        return iccRecords;
    }

    public IccFileHandler getIccFileHandler() {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                IccFileHandler iccFileHandler = this.mUiccApplication.getIccFileHandler();
                return iccFileHandler;
            }
            return null;
        }
    }

    public int getSimLock() {
        return this.mSimLock;
    }

    public void registerForAbsent(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mAbsentRegistrants.add(r);
            if (getState() == State.ABSENT) {
                r.notifyRegistrant();
            }
        }
    }

    public void unregisterForAbsent(Handler h) {
        synchronized (this.mLock) {
            this.mAbsentRegistrants.remove(h);
        }
    }

    public void registerForNetworkLocked(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mNetworkLockedRegistrants.add(r);
            if (getState() == State.NETWORK_LOCKED) {
                r.notifyRegistrant(new AsyncResult(null, Integer.valueOf(this.mPersoSubState.ordinal()), null));
            }
        }
    }

    public void unregisterForNetworkLocked(Handler h) {
        synchronized (this.mLock) {
            this.mNetworkLockedRegistrants.remove(h);
        }
    }

    public void registerForLocked(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mPinLockedRegistrants.add(r);
            if (getState().isPinLocked()) {
                r.notifyRegistrant();
            }
        }
    }

    public void unregisterForLocked(Handler h) {
        synchronized (this.mLock) {
            this.mPinLockedRegistrants.remove(h);
        }
    }

    /* JADX WARNING: Missing block: B:7:0x000d, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
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

    /* JADX WARNING: Missing block: B:7:0x000d, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
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

    /* JADX WARNING: Missing block: B:7:0x000d, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
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

    /* JADX WARNING: Missing block: B:7:0x000d, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
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

    /* JADX WARNING: Missing block: B:7:0x000d, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void supplyNetworkDepersonalization(String pin, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.supplyNetworkDepersonalization(pin, onComplete);
            } else if (onComplete != null) {
                AsyncResult.forMessage(onComplete).exception = new RuntimeException("CommandsInterface is not set.");
                onComplete.sendToTarget();
            }
        }
    }

    public boolean getIccLockEnabled() {
        boolean booleanValue;
        synchronized (this.mLock) {
            booleanValue = Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccLockEnabled() : false).booleanValue();
        }
        return booleanValue;
    }

    public boolean getIccFdnEnabled() {
        boolean booleanValue;
        synchronized (this.mLock) {
            booleanValue = Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccFdnEnabled() : false).booleanValue();
        }
        return booleanValue;
    }

    public boolean getIccFdnAvailable() {
        return this.mUiccApplication != null ? this.mUiccApplication.getIccFdnAvailable() : false;
    }

    public boolean getIccPin2Blocked() {
        return Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccPin2Blocked() : false).booleanValue();
    }

    public boolean getIccPuk2Blocked() {
        return Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccPuk2Blocked() : false).booleanValue();
    }

    /* JADX WARNING: Missing block: B:7:0x000d, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
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

    /* JADX WARNING: Missing block: B:7:0x000d, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
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

    /* JADX WARNING: Missing block: B:7:0x000d, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
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

    /* JADX WARNING: Missing block: B:7:0x000d, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
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

    public String getServiceProviderName() {
        synchronized (this.mLock) {
            if (this.mIccRecords != null) {
                String serviceProviderName = this.mIccRecords.getServiceProviderName();
                return serviceProviderName;
            }
            return null;
        }
    }

    public boolean isApplicationOnIcc(AppType type) {
        boolean booleanValue;
        synchronized (this.mLock) {
            booleanValue = Boolean.valueOf(this.mUiccCard != null ? this.mUiccCard.isApplicationOnIcc(type) : false).booleanValue();
        }
        return booleanValue;
    }

    public boolean hasIccCard() {
        synchronized (this.mLock) {
            if (this.mUiccCard == null || this.mUiccCard.getCardState() == CardState.CARDSTATE_ABSENT) {
                return false;
            }
            return true;
        }
    }

    private void setSystemProperty(String property, String value) {
        TelephonyManager.setTelephonyProperty(this.mPhoneId.intValue(), property, value);
    }

    public IccRecords getIccRecord() {
        return this.mIccRecords;
    }

    private void log(String s) {
        Rlog.d(LOG_TAG, s);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    public int getIccPinPukRetryCountSc(int isPinPuk) {
        int retValue;
        synchronized (this.mLock) {
            retValue = this.mUiccCard != null ? this.mUiccCard.getIccPinPukRetryCountSc(isPinPuk) : -1;
        }
        return retValue;
    }

    private void requestGetPinPukRetryCount() {
        String mOemIdentifier = "QOEMHOOK";
        byte[] request = new byte[(mOemIdentifier.length() + 8)];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(mOemIdentifier.getBytes());
        reqBuffer.putInt(589926);
        this.mCi.invokeOemRilRequestRaw(request, obtainMessage(17));
    }

    /* JADX WARNING: Missing block: B:13:0x0034, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void onGetPinPukRetryCountDone(AsyncResult ar) {
        synchronized (this.mLock) {
            if (this.mUiccCard == null) {
                loge("Error in get pin puk retry count with mUiccCard is null");
            } else if (ar.exception != null) {
                loge("Error in get pin puk retry count with exception: " + ar.exception);
                this.mUiccCard.setPinPukRetryCount(0, -1);
            } else if (ar.result != null) {
                byte[] ret = ar.result;
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
            if ("LOCKED".equals(getIccStateIntentString(this.mExternalState)) || "ABSENT".equals(getIccStateIntentString(this.mExternalState))) {
                broadcastInternalIccStateChangedIntent(getIccStateIntentString(this.mExternalState), getIccStateReason(this.mExternalState));
            } else {
                broadcastIccStateChangedIntent(getIccStateIntentString(this.mExternalState), getIccStateReason(this.mExternalState));
            }
            if (State.ABSENT == this.mExternalState) {
                this.mAbsentRegistrants.notifyRegistrants();
            }
        }
    }

    private void requestUnsolRadioStateChanged() {
        String mOemIdentifier = "QOEMHOOK";
        int OEMHOOK_USIM_RADIO_STATE = (591824 + PduPart.P_CONTENT_TRANSFER_ENCODING) + 5;
        byte[] request = new byte[(mOemIdentifier.length() + 8)];
        ByteBuffer reqBuffer = ByteBuffer.wrap(request);
        reqBuffer.order(ByteOrder.nativeOrder());
        reqBuffer.put(mOemIdentifier.getBytes());
        reqBuffer.putInt(OEMHOOK_USIM_RADIO_STATE);
        this.mCi.invokeOemRilRequestRaw(request, null);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        int i;
        pw.println("IccCardProxy: " + this);
        pw.println(" mContext=" + this.mContext);
        pw.println(" mCi=" + this.mCi);
        pw.println(" mAbsentRegistrants: size=" + this.mAbsentRegistrants.size());
        for (i = 0; i < this.mAbsentRegistrants.size(); i++) {
            pw.println("  mAbsentRegistrants[" + i + "]=" + ((Registrant) this.mAbsentRegistrants.get(i)).getHandler());
        }
        pw.println(" mPinLockedRegistrants: size=" + this.mPinLockedRegistrants.size());
        for (i = 0; i < this.mPinLockedRegistrants.size(); i++) {
            pw.println("  mPinLockedRegistrants[" + i + "]=" + ((Registrant) this.mPinLockedRegistrants.get(i)).getHandler());
        }
        pw.println(" mNetworkLockedRegistrants: size=" + this.mNetworkLockedRegistrants.size());
        for (i = 0; i < this.mNetworkLockedRegistrants.size(); i++) {
            pw.println("  mNetworkLockedRegistrants[" + i + "]=" + ((Registrant) this.mNetworkLockedRegistrants.get(i)).getHandler());
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

    public void openLogicalChannel(String AID, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.openLogicalChannel(AID, onComplete);
            } else {
                log("UiccApplication is not exist.");
                AsyncResult.forMessage(onComplete).exception = new RuntimeException("ICC card is absent.");
                onComplete.sendToTarget();
            }
        }
    }

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

    public void getEfLock(Message onComplete) {
        if (TelBrand.IS_DCM) {
            synchronized (this.mLock) {
                IccFileHandler fh = getIccFileHandler();
                boolean isTypeUsim = this.mUiccApplication != null && this.mUiccApplication.getType() == AppType.APPTYPE_USIM;
                if (fh == null || !isTypeUsim) {
                    AsyncResult.forMessage(onComplete, null, null);
                    onComplete.sendToTarget();
                } else {
                    fh.loadEFTransparent(IccConstants.EF_LOCK, 8, obtainMessage(23, onComplete));
                }
            }
        }
    }

    public void setPinPukRetryCount(int isPinPuk, int RetryCount) {
        synchronized (this.mLock) {
            if (this.mUiccCard == null) {
                loge("Error in set pin puk retry count with mUiccCard is null");
                return;
            }
            this.mUiccCard.setPinPukRetryCount(isPinPuk, RetryCount);
        }
    }
}
