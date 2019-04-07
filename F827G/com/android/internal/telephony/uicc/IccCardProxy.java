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
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.PersoSubState;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccCardStatus.PinState;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
    protected boolean dontPollBroadcastSharp = false;
    private RegistrantList mAbsentRegistrants = new RegistrantList();
    private CdmaSubscriptionSourceManager mCdmaSSM = null;
    private CommandsInterface mCi;
    private Context mContext;
    private int mCurrentAppType = 1;
    protected State mExternalState = State.UNKNOWN;
    protected State mExternalStateSharp = State.UNKNOWN;
    private IccOpenLogicalChannel mIccOpenLogicalChannel = new IccOpenLogicalChannel();
    private IccRecords mIccRecords = null;
    private boolean mInitialized = false;
    private boolean mIsCardStatusAvailable = false;
    private final Object mLock = new Object();
    private RegistrantList mPersoLockedRegistrants = new RegistrantList();
    private PersoSubState mPersoSubState = PersoSubState.PERSOSUBSTATE_UNKNOWN;
    private Integer mPhoneId = null;
    private RegistrantList mPinLockedRegistrants = new RegistrantList();
    private boolean mQuietMode = false;
    private boolean mRadioOn = false;
    private int mSimLock = 0;
    private UiccCardApplication mUiccApplication = null;
    private UiccCard mUiccCard = null;
    private UiccController mUiccController = null;

    /* renamed from: com.android.internal.telephony.uicc.IccCardProxy$1 */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$IccCardConstants$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.ABSENT.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.PIN_REQUIRED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.PUK_REQUIRED.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.PERSO_LOCKED.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.READY.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.NOT_READY.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.PERM_DISABLED.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.CARD_IO_ERROR.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.SIM_NETWORK_SUBSET_LOCKED.ordinal()] = 9;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.FOREVER.ordinal()] = 10;
            } catch (NoSuchFieldError e10) {
            }
            $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState = new int[AppState.values().length];
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[AppState.APPSTATE_UNKNOWN.ordinal()] = 1;
            } catch (NoSuchFieldError e11) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[AppState.APPSTATE_PIN.ordinal()] = 2;
            } catch (NoSuchFieldError e12) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[AppState.APPSTATE_PUK.ordinal()] = 3;
            } catch (NoSuchFieldError e13) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[AppState.APPSTATE_SUBSCRIPTION_PERSO.ordinal()] = 4;
            } catch (NoSuchFieldError e14) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[AppState.APPSTATE_READY.ordinal()] = 5;
            } catch (NoSuchFieldError e15) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[AppState.APPSTATE_DETECTED.ordinal()] = 6;
            } catch (NoSuchFieldError e16) {
            }
        }
    }

    private class IccOpenLogicalChannel extends Handler {
        private static final String CAS_AID = "F0000000010001FF81FF10FFFFFFFF02";
        public static final int DEFAULT_MAX_CHANNEL = 4;
        private static final int EVENT_GET_STATUS_DONE = 2;
        private static final int EVENT_GET_TMM_DONE = 1;
        private static final int EVENT_OPEN_CHANNEL_DONE = 3;
        private int mMaxChannels = 4;
        private int mRequestCount = 0;

        private class LookForVacantChannel {
            public static final int TMM_NOT_OPEN_CHANNEL = 0;
            private String mAID = null;
            private int mCheckedNo = 0;
            private int mMaxChannels = 4;
            private Message mOnComplete = null;
            private int mTMMChannel = 0;
            private int mVacantCount = 0;

            public LookForVacantChannel(String str, Message message, int i) {
                this.mAID = str;
                this.mOnComplete = message;
                this.mMaxChannels = i;
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

            public boolean isCheckEnd() {
                return this.mCheckedNo >= this.mMaxChannels;
            }

            public boolean isOpenChannel() {
                if (this.mTMMChannel > 0) {
                    if (this.mVacantCount <= 0) {
                        return false;
                    }
                } else if (this.mVacantCount - 1 <= 0) {
                    return false;
                }
                return true;
            }

            public int reqNextChannelWithUpdate() {
                this.mCheckedNo++;
                if (this.mTMMChannel == this.mCheckedNo) {
                    this.mCheckedNo++;
                }
                return this.mCheckedNo;
            }

            public void setTMMChannel(int i) {
                this.mTMMChannel = i;
            }
        }

        private void checkGetCardStatusResponse(LookForVacantChannel lookForVacantChannel, IccIoResult iccIoResult) {
            if (iccIoResult.sw1 == 106 && iccIoResult.sw2 == 130) {
                lookForVacantChannel.setTMMChannel(0);
            } else if (iccIoResult.success()) {
                int length = iccIoResult.payload.length;
                if (length > 1) {
                }
                if (length > 0) {
                    lookForVacantChannel.setTMMChannel(iccIoResult.payload[0]);
                } else {
                    lookForVacantChannel.setTMMChannel(0);
                }
            } else {
                lookForVacantChannel.setTMMChannel(0);
            }
        }

        private void checkGetStatusResponse(LookForVacantChannel lookForVacantChannel, IccIoResult iccIoResult) {
            if (iccIoResult.sw1 == 104 && iccIoResult.sw2 == 129) {
                lookForVacantChannel.addVacant();
            }
        }

        private String eventString(int i) {
            switch (i) {
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

        private void getChannelStatus(LookForVacantChannel lookForVacantChannel, int i) {
            IccCardProxy.this.mCi.iccExchangeAPDU(i < 4 ? i + 128 : (i + 192) - 4, 242, 0, 0, 0, 0, null, obtainMessage(2, lookForVacantChannel));
        }

        private void getOpenLogicalChannel(LookForVacantChannel lookForVacantChannel) {
            IccCardProxy.this.mUiccApplication.openLogicalChannel(lookForVacantChannel.getAID(), obtainMessage(3, lookForVacantChannel));
        }

        private void getTMMChannel(String str, LookForVacantChannel lookForVacantChannel) {
            IccCardProxy.this.mCi.iccExchangeAPDU(144, 242, 0, 0, 0, str.length() / 2, str, obtainMessage(1, lookForVacantChannel));
        }

        private void sendFailureMessage(Message message, Throwable th) {
            AsyncResult.forMessage(message, new int[]{0}, th);
            message.sendToTarget();
        }

        private void sendNonVacantChannelMessage(Message message) {
            int[] iArr = new int[]{0};
            AsyncResult.forMessage(message, iArr, new CommandException(Error.MISSING_RESOURCE));
            message.sendToTarget();
        }

        private void sendSuccessMessage(Message message, AsyncResult asyncResult) {
            asyncResult.userObj = message.obj;
            message.obj = asyncResult;
            message.sendToTarget();
        }

        public void handleMessage(Message message) {
            AsyncResult asyncResult = (AsyncResult) message.obj;
            LookForVacantChannel lookForVacantChannel = (LookForVacantChannel) asyncResult.userObj;
            if (asyncResult.exception != null) {
                sendFailureMessage(lookForVacantChannel.getOnComplete(), asyncResult.exception);
                return;
            }
            switch (message.what) {
                case 1:
                    checkGetCardStatusResponse(lookForVacantChannel, (IccIoResult) asyncResult.result);
                    break;
                case 2:
                    checkGetStatusResponse(lookForVacantChannel, (IccIoResult) asyncResult.result);
                    break;
                case 3:
                    sendSuccessMessage(lookForVacantChannel.getOnComplete(), asyncResult);
                    return;
            }
            int reqNextChannelWithUpdate = lookForVacantChannel.reqNextChannelWithUpdate();
            if (!lookForVacantChannel.isCheckEnd()) {
                getChannelStatus(lookForVacantChannel, reqNextChannelWithUpdate);
            } else if (lookForVacantChannel.isOpenChannel()) {
                getOpenLogicalChannel(lookForVacantChannel);
            } else {
                sendNonVacantChannelMessage(lookForVacantChannel.getOnComplete());
            }
        }

        public void iccOpenChannel(String str, Message message) {
            getTMMChannel(CAS_AID, new LookForVacantChannel(str, message, this.mMaxChannels));
        }
    }

    public IccCardProxy(Context context, CommandsInterface commandsInterface, int i) {
        log("ctor: ci=" + commandsInterface + " phoneId=" + i);
        this.mContext = context;
        this.mCi = commandsInterface;
        this.mPhoneId = Integer.valueOf(i);
        this.mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(context, commandsInterface, this, 11, null);
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 3, null);
        commandsInterface.registerForOn(this, 2, null);
        commandsInterface.registerForOffOrNotAvailable(this, 1, null);
        resetProperties();
        setExternalState(State.NOT_READY, false);
        sendMessage(obtainMessage(24));
        if (TelBrand.IS_SBM) {
            commandsInterface.getSimLock(obtainMessage(25));
        }
    }

    private void broadcastIccStateChangedIntent(String str, String str2) {
        synchronized (this.mLock) {
            if (this.mPhoneId == null || !SubscriptionManager.isValidSlotId(this.mPhoneId.intValue())) {
                loge("broadcastIccStateChangedIntent: mPhoneId=" + this.mPhoneId + " is invalid; Return!!");
            } else if (this.mQuietMode) {
                log("broadcastIccStateChangedIntent: QuietMode NOT Broadcasting intent ACTION_SIM_STATE_CHANGED  value=" + str + " reason=" + str2);
            } else if (TelBrand.IS_DCM && ENCRYPTED_STATE.equals(SystemProperties.get(PROP_RO_CRYPTO_STATE)) && MIN_FRAMEWORK_STATE.equals(SystemProperties.get(PROP_VOLD_DECRYPT)) && !"NOT_READY".equals(str) && !"ABSENT".equals(str)) {
                log("Encrypted: NOT Broadcasting intent ACTION_SIM_STATE_CHANGED " + str + " reason " + str2);
            } else {
                Intent intent = new Intent("android.intent.action.SIM_STATE_CHANGED");
                intent.addFlags(67108864);
                intent.putExtra("phoneName", "Phone");
                intent.putExtra("ss", str);
                intent.putExtra("reason", str2);
                SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhoneId.intValue());
                log("broadcastIccStateChangedIntent intent ACTION_SIM_STATE_CHANGED value=" + str + " reason=" + str2 + " for mPhoneId=" + this.mPhoneId);
                ActivityManagerNative.broadcastStickyIntent(intent, "android.permission.READ_PHONE_STATE", -1);
            }
        }
    }

    private String getIccStateIntentString(State state) {
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

    private String getIccStateReason(State state) {
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$IccCardConstants$State[state.ordinal()]) {
            case 2:
                return "PIN";
            case 3:
                return "PUK";
            case 4:
                return "PERSO";
            case 7:
                return "PERM_DISABLED";
            case 8:
                return "CARD_IO_ERROR";
            default:
                return null;
        }
    }

    private void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    private void loge(String str) {
        Rlog.e(LOG_TAG, str);
    }

    private void notifyCurrentExternalState() {
        synchronized (this.mLock) {
            if (!TelBrand.IS_DCM) {
                SystemProperties.set("gsm.sim.state", this.mExternalState.toString());
            } else if (!ENCRYPTED_STATE.equals(SystemProperties.get(PROP_RO_CRYPTO_STATE)) || !MIN_FRAMEWORK_STATE.equals(SystemProperties.get(PROP_VOLD_DECRYPT)) || "NOT_READY".equals(getIccStateIntentString(this.mExternalState)) || "ABSENT".equals(getIccStateIntentString(this.mExternalState))) {
                SystemProperties.set("gsm.sim.state", this.mExternalState.toString());
            }
            broadcastIccStateChangedIntent(getIccStateIntentString(this.mExternalState), getIccStateReason(this.mExternalState));
            if (State.ABSENT == this.mExternalState) {
                this.mAbsentRegistrants.notifyRegistrants();
            }
        }
    }

    private void notifyCurrentExternalStateSharp() {
        synchronized (this.mLock) {
            broadcastIccStateChangedIntentSharp(getIccStateIntentStringSharp(this.mExternalStateSharp), getIccStateReasonSharp(this.mExternalStateSharp));
        }
    }

    /* JADX WARNING: Missing block: B:21:?, code skipped:
            return;
     */
    private void onGetPinPukRetryCountDone(android.os.AsyncResult r6) {
        /*
        r5 = this;
        r1 = r5.mLock;
        monitor-enter(r1);
        r0 = r5.mUiccCard;	 Catch:{ all -> 0x0033 }
        if (r0 != 0) goto L_0x000e;
    L_0x0007:
        r0 = "Error in get pin puk retry count with mUiccCard is null";
        r5.loge(r0);	 Catch:{ all -> 0x0033 }
        monitor-exit(r1);	 Catch:{ all -> 0x0033 }
    L_0x000d:
        return;
    L_0x000e:
        r0 = r6.exception;	 Catch:{ all -> 0x0033 }
        if (r0 == 0) goto L_0x0036;
    L_0x0012:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0033 }
        r0.<init>();	 Catch:{ all -> 0x0033 }
        r2 = "Error in get pin puk retry count with exception: ";
        r0 = r0.append(r2);	 Catch:{ all -> 0x0033 }
        r2 = r6.exception;	 Catch:{ all -> 0x0033 }
        r0 = r0.append(r2);	 Catch:{ all -> 0x0033 }
        r0 = r0.toString();	 Catch:{ all -> 0x0033 }
        r5.loge(r0);	 Catch:{ all -> 0x0033 }
        r0 = r5.mUiccCard;	 Catch:{ all -> 0x0033 }
        r2 = 0;
        r3 = -1;
        r0.setPinPukRetryCount(r2, r3);	 Catch:{ all -> 0x0033 }
    L_0x0031:
        monitor-exit(r1);	 Catch:{ all -> 0x0033 }
        goto L_0x000d;
    L_0x0033:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0033 }
        throw r0;
    L_0x0036:
        r0 = r6.result;	 Catch:{ all -> 0x0033 }
        if (r0 == 0) goto L_0x0065;
    L_0x003a:
        r0 = r6.result;	 Catch:{ all -> 0x0033 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x0033 }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x0033 }
        r2 = r5.mUiccCard;	 Catch:{ all -> 0x0033 }
        r3 = 1;
        r4 = 0;
        r4 = r0[r4];	 Catch:{ all -> 0x0033 }
        r2.setPinPukRetryCount(r3, r4);	 Catch:{ all -> 0x0033 }
        r2 = r5.mUiccCard;	 Catch:{ all -> 0x0033 }
        r3 = 2;
        r4 = 1;
        r4 = r0[r4];	 Catch:{ all -> 0x0033 }
        r2.setPinPukRetryCount(r3, r4);	 Catch:{ all -> 0x0033 }
        r2 = r5.mUiccCard;	 Catch:{ all -> 0x0033 }
        r3 = 3;
        r4 = 2;
        r4 = r0[r4];	 Catch:{ all -> 0x0033 }
        r2.setPinPukRetryCount(r3, r4);	 Catch:{ all -> 0x0033 }
        r2 = r5.mUiccCard;	 Catch:{ all -> 0x0033 }
        r3 = 4;
        r4 = 3;
        r0 = r0[r4];	 Catch:{ all -> 0x0033 }
        r2.setPinPukRetryCount(r3, r0);	 Catch:{ all -> 0x0033 }
        goto L_0x0031;
    L_0x0065:
        r0 = "No result in get pin puk retry count";
        r5.loge(r0);	 Catch:{ all -> 0x0033 }
        r0 = r5.mUiccCard;	 Catch:{ all -> 0x0033 }
        r2 = 0;
        r3 = -1;
        r0.setPinPukRetryCount(r2, r3);	 Catch:{ all -> 0x0033 }
        goto L_0x0031;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.onGetPinPukRetryCountDone(android.os.AsyncResult):void");
    }

    private void onRecordsLoaded() {
        broadcastIccStateChangedIntent("LOADED", null);
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

    /* JADX WARNING: Missing block: B:23:?, code skipped:
            return;
     */
    private void processLockedState() {
        /*
        r3 = this;
        r1 = r3.mLock;
        monitor-enter(r1);
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x001a }
        if (r0 != 0) goto L_0x0009;
    L_0x0007:
        monitor-exit(r1);	 Catch:{ all -> 0x001a }
    L_0x0008:
        return;
    L_0x0009:
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x001a }
        r0 = r0.getPin1State();	 Catch:{ all -> 0x001a }
        r2 = com.android.internal.telephony.uicc.IccCardStatus.PinState.PINSTATE_ENABLED_PERM_BLOCKED;	 Catch:{ all -> 0x001a }
        if (r0 != r2) goto L_0x001d;
    L_0x0013:
        r0 = com.android.internal.telephony.IccCardConstants.State.PERM_DISABLED;	 Catch:{ all -> 0x001a }
        r3.setExternalState(r0);	 Catch:{ all -> 0x001a }
        monitor-exit(r1);	 Catch:{ all -> 0x001a }
        goto L_0x0008;
    L_0x001a:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x001a }
        throw r0;
    L_0x001d:
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x001a }
        r0 = r0.getState();	 Catch:{ all -> 0x001a }
        r2 = com.android.internal.telephony.uicc.IccCardProxy.AnonymousClass1.$SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState;	 Catch:{ all -> 0x001a }
        r0 = r0.ordinal();	 Catch:{ all -> 0x001a }
        r0 = r2[r0];	 Catch:{ all -> 0x001a }
        switch(r0) {
            case 2: goto L_0x0030;
            case 3: goto L_0x003b;
            default: goto L_0x002e;
        };	 Catch:{ all -> 0x001a }
    L_0x002e:
        monitor-exit(r1);	 Catch:{ all -> 0x001a }
        goto L_0x0008;
    L_0x0030:
        r0 = r3.mPinLockedRegistrants;	 Catch:{ all -> 0x001a }
        r0.notifyRegistrants();	 Catch:{ all -> 0x001a }
        r0 = com.android.internal.telephony.IccCardConstants.State.PIN_REQUIRED;	 Catch:{ all -> 0x001a }
        r3.setExternalState(r0);	 Catch:{ all -> 0x001a }
        goto L_0x002e;
    L_0x003b:
        r0 = com.android.internal.telephony.IccCardConstants.State.PUK_REQUIRED;	 Catch:{ all -> 0x001a }
        r3.setExternalState(r0);	 Catch:{ all -> 0x001a }
        goto L_0x002e;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.processLockedState():void");
    }

    private void queueNextBroadcastSharpPoll(String str, String str2) {
        if (!this.dontPollBroadcastSharp) {
            Message obtainMessage = obtainMessage();
            obtainMessage.what = 30;
            obtainMessage.obj = new String[]{str, str2};
            sendMessageDelayed(obtainMessage, 1000);
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

    private void requestGetPinPukRetryCount() {
        byte[] bArr = new byte[("QOEMHOOK".length() + 8)];
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        wrap.order(ByteOrder.nativeOrder());
        wrap.put("QOEMHOOK".getBytes());
        wrap.putInt(589926);
        this.mCi.invokeOemRilRequestRaw(bArr, obtainMessage(17));
    }

    private void requestUnsolRadioStateChanged() {
        byte[] bArr = new byte[("QOEMHOOK".length() + 8)];
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        wrap.order(ByteOrder.nativeOrder());
        wrap.put("QOEMHOOK".getBytes());
        wrap.putInt(592029);
        this.mCi.invokeOemRilRequestRaw(bArr, null);
    }

    private void setExternalState(State state) {
        setExternalState(state, false);
    }

    /* JADX WARNING: Missing block: B:55:?, code skipped:
            return;
     */
    private void setExternalState(com.android.internal.telephony.IccCardConstants.State r5, boolean r6) {
        /*
        r4 = this;
        r1 = r4.mLock;
        monitor-enter(r1);
        r0 = r4.mPhoneId;	 Catch:{ all -> 0x0051 }
        if (r0 == 0) goto L_0x0013;
    L_0x0007:
        r0 = r4.mPhoneId;	 Catch:{ all -> 0x0051 }
        r0 = r0.intValue();	 Catch:{ all -> 0x0051 }
        r0 = android.telephony.SubscriptionManager.isValidSlotId(r0);	 Catch:{ all -> 0x0051 }
        if (r0 != 0) goto L_0x0033;
    L_0x0013:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0051 }
        r0.<init>();	 Catch:{ all -> 0x0051 }
        r2 = "setExternalState: mPhoneId=";
        r0 = r0.append(r2);	 Catch:{ all -> 0x0051 }
        r2 = r4.mPhoneId;	 Catch:{ all -> 0x0051 }
        r0 = r0.append(r2);	 Catch:{ all -> 0x0051 }
        r2 = " is invalid; Return!!";
        r0 = r0.append(r2);	 Catch:{ all -> 0x0051 }
        r0 = r0.toString();	 Catch:{ all -> 0x0051 }
        r4.loge(r0);	 Catch:{ all -> 0x0051 }
        monitor-exit(r1);	 Catch:{ all -> 0x0051 }
    L_0x0032:
        return;
    L_0x0033:
        if (r6 != 0) goto L_0x0054;
    L_0x0035:
        r0 = r4.mExternalState;	 Catch:{ all -> 0x0051 }
        if (r5 != r0) goto L_0x0054;
    L_0x0039:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0051 }
        r0.<init>();	 Catch:{ all -> 0x0051 }
        r2 = "setExternalState: !override and newstate unchanged from ";
        r0 = r0.append(r2);	 Catch:{ all -> 0x0051 }
        r0 = r0.append(r5);	 Catch:{ all -> 0x0051 }
        r0 = r0.toString();	 Catch:{ all -> 0x0051 }
        r4.loge(r0);	 Catch:{ all -> 0x0051 }
        monitor-exit(r1);	 Catch:{ all -> 0x0051 }
        goto L_0x0032;
    L_0x0051:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0051 }
        throw r0;
    L_0x0054:
        r4.mExternalState = r5;	 Catch:{ all -> 0x0051 }
        r0 = com.android.internal.telephony.IccCardConstants.State.PIN_REQUIRED;	 Catch:{ all -> 0x0051 }
        if (r5 == r0) goto L_0x006a;
    L_0x005a:
        r0 = com.android.internal.telephony.IccCardConstants.State.PUK_REQUIRED;	 Catch:{ all -> 0x0051 }
        if (r5 == r0) goto L_0x006a;
    L_0x005e:
        r0 = com.android.internal.telephony.IccCardConstants.State.PERSO_LOCKED;	 Catch:{ all -> 0x0051 }
        if (r5 == r0) goto L_0x006a;
    L_0x0062:
        r0 = com.android.internal.telephony.IccCardConstants.State.READY;	 Catch:{ all -> 0x0051 }
        if (r5 == r0) goto L_0x006a;
    L_0x0066:
        r0 = com.android.internal.telephony.IccCardConstants.State.PERM_DISABLED;	 Catch:{ all -> 0x0051 }
        if (r5 != r0) goto L_0x006f;
    L_0x006a:
        r4.requestGetPinPukRetryCount();	 Catch:{ all -> 0x0051 }
        monitor-exit(r1);	 Catch:{ all -> 0x0051 }
        goto L_0x0032;
    L_0x006f:
        r0 = r4.mUiccCard;	 Catch:{ all -> 0x0051 }
        if (r0 == 0) goto L_0x007a;
    L_0x0073:
        r0 = r4.mUiccCard;	 Catch:{ all -> 0x0051 }
        r2 = 0;
        r3 = -1;
        r0.setPinPukRetryCount(r2, r3);	 Catch:{ all -> 0x0051 }
    L_0x007a:
        r0 = com.android.internal.telephony.TelBrand.IS_DCM;	 Catch:{ all -> 0x0051 }
        if (r0 == 0) goto L_0x00e0;
    L_0x007e:
        r0 = "encrypted";
        r2 = "ro.crypto.state";
        r2 = android.os.SystemProperties.get(r2);	 Catch:{ all -> 0x0051 }
        r0 = r0.equals(r2);	 Catch:{ all -> 0x0051 }
        if (r0 == 0) goto L_0x00b6;
    L_0x008c:
        r0 = "trigger_restart_min_framework";
        r2 = "vold.decrypt";
        r2 = android.os.SystemProperties.get(r2);	 Catch:{ all -> 0x0051 }
        r0 = r0.equals(r2);	 Catch:{ all -> 0x0051 }
        if (r0 == 0) goto L_0x00b6;
    L_0x009a:
        r0 = "NOT_READY";
        r2 = r4.mExternalState;	 Catch:{ all -> 0x0051 }
        r2 = r4.getIccStateIntentString(r2);	 Catch:{ all -> 0x0051 }
        r0 = r0.equals(r2);	 Catch:{ all -> 0x0051 }
        if (r0 != 0) goto L_0x00b6;
    L_0x00a8:
        r0 = "ABSENT";
        r2 = r4.mExternalState;	 Catch:{ all -> 0x0051 }
        r2 = r4.getIccStateIntentString(r2);	 Catch:{ all -> 0x0051 }
        r0 = r0.equals(r2);	 Catch:{ all -> 0x0051 }
        if (r0 == 0) goto L_0x00c3;
    L_0x00b6:
        r0 = "gsm.sim.state";
        r2 = r4.getState();	 Catch:{ all -> 0x0051 }
        r2 = r2.toString();	 Catch:{ all -> 0x0051 }
        r4.setSystemProperty(r0, r2);	 Catch:{ all -> 0x0051 }
    L_0x00c3:
        r0 = r4.mExternalState;	 Catch:{ all -> 0x0051 }
        r0 = r4.getIccStateIntentString(r0);	 Catch:{ all -> 0x0051 }
        r2 = r4.mExternalState;	 Catch:{ all -> 0x0051 }
        r2 = r4.getIccStateReason(r2);	 Catch:{ all -> 0x0051 }
        r4.broadcastIccStateChangedIntent(r0, r2);	 Catch:{ all -> 0x0051 }
        r0 = com.android.internal.telephony.IccCardConstants.State.ABSENT;	 Catch:{ all -> 0x0051 }
        r2 = r4.mExternalState;	 Catch:{ all -> 0x0051 }
        if (r0 != r2) goto L_0x00dd;
    L_0x00d8:
        r0 = r4.mAbsentRegistrants;	 Catch:{ all -> 0x0051 }
        r0.notifyRegistrants();	 Catch:{ all -> 0x0051 }
    L_0x00dd:
        monitor-exit(r1);	 Catch:{ all -> 0x0051 }
        goto L_0x0032;
    L_0x00e0:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0051 }
        r0.<init>();	 Catch:{ all -> 0x0051 }
        r2 = "setExternalState: set mPhoneId=";
        r0 = r0.append(r2);	 Catch:{ all -> 0x0051 }
        r2 = r4.mPhoneId;	 Catch:{ all -> 0x0051 }
        r0 = r0.append(r2);	 Catch:{ all -> 0x0051 }
        r2 = " mExternalState=";
        r0 = r0.append(r2);	 Catch:{ all -> 0x0051 }
        r2 = r4.mExternalState;	 Catch:{ all -> 0x0051 }
        r0 = r0.append(r2);	 Catch:{ all -> 0x0051 }
        r0 = r0.toString();	 Catch:{ all -> 0x0051 }
        r4.loge(r0);	 Catch:{ all -> 0x0051 }
        r0 = "gsm.sim.state";
        r2 = r4.getState();	 Catch:{ all -> 0x0051 }
        r2 = r2.toString();	 Catch:{ all -> 0x0051 }
        r4.setSystemProperty(r0, r2);	 Catch:{ all -> 0x0051 }
        goto L_0x00c3;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.setExternalState(com.android.internal.telephony.IccCardConstants$State, boolean):void");
    }

    private void setExternalStateSharp(State state) {
        setExternalStateSharp(state, false);
    }

    private void setSystemProperty(String str, String str2) {
        TelephonyManager.setTelephonyProperty(this.mPhoneId.intValue(), str, str2);
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
            switch (this.mUiccApplication.getState()) {
                case APPSTATE_UNKNOWN:
                    setExternalState(State.UNKNOWN);
                    return;
                case APPSTATE_PIN:
                    setExternalState(State.PIN_REQUIRED);
                    return;
                case APPSTATE_PUK:
                    log("[UIM]updateExternalState app_state : APPSTATE_PUK");
                    PinState pin1State = this.mUiccApplication.getPin1State();
                    log("[UIM]updateExternalState pin1State : " + pin1State);
                    if (pin1State == PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
                        setExternalState(State.PERM_DISABLED);
                        return;
                    } else {
                        setExternalState(State.PUK_REQUIRED);
                        return;
                    }
                case APPSTATE_SUBSCRIPTION_PERSO:
                    if (this.mUiccApplication.isPersoLocked()) {
                        this.mPersoSubState = this.mUiccApplication.getPersoSubState();
                        setExternalState(State.PERSO_LOCKED);
                        return;
                    }
                    setExternalState(State.UNKNOWN);
                    return;
                case APPSTATE_READY:
                    setExternalState(State.READY);
                    return;
                default:
                    return;
            }
        }
    }

    private void updateIccAvailability() {
        synchronized (this.mLock) {
            UiccCardApplication application;
            IccRecords iccRecords;
            UiccCard uiccCard = this.mUiccController.getUiccCard(this.mPhoneId.intValue());
            CardState cardState = CardState.CARDSTATE_ABSENT;
            if (uiccCard != null) {
                uiccCard.getCardState();
                application = uiccCard.getApplication(this.mCurrentAppType);
                iccRecords = application != null ? application.getIccRecords() : null;
            } else {
                iccRecords = null;
                application = null;
            }
            if (!(this.mIccRecords == iccRecords && this.mUiccApplication == application && this.mUiccCard == uiccCard)) {
                log("Icc changed. Reregestering.");
                unregisterUiccCardEvents();
                this.mUiccCard = uiccCard;
                this.mUiccApplication = application;
                this.mIccRecords = iccRecords;
                registerUiccCardEvents();
                updateActiveRecord();
            }
            updateExternalState();
            if (TelBrand.IS_SBM) {
                updateExternalStateSharp();
            }
        }
    }

    private void updateQuietMode() {
        int i = -1;
        boolean z = false;
        synchronized (this.mLock) {
            boolean z2 = this.mQuietMode;
            z2 = this.mContext.getResources().getBoolean(17957012);
            if (this.mCurrentAppType == 1) {
                log("updateQuietMode: 3GPP subscription -> newQuietMode=" + false);
            } else {
                if (this.mCdmaSSM != null) {
                    i = this.mCdmaSSM.getCdmaSubscriptionSource();
                }
                if (!z2 && i == 1) {
                    if (this.mCurrentAppType == 2) {
                        z = true;
                    }
                }
                log("updateQuietMode: cdmaSource=" + i + " mCurrentAppType=" + this.mCurrentAppType + " newQuietMode=" + z);
            }
            if (!this.mQuietMode && z) {
                log("Switching to QuietMode.");
                setExternalState(State.READY);
                this.mQuietMode = z;
            } else if (!this.mQuietMode || z) {
                log("updateQuietMode: no changes don't setExternalState");
            } else {
                log("updateQuietMode: Switching out from QuietMode. Force broadcast of current state=" + this.mExternalState);
                this.mQuietMode = z;
                setExternalState(this.mExternalState, true);
                if (TelBrand.IS_SBM) {
                    setExternalStateSharp(this.mExternalStateSharp, true);
                }
            }
            log("updateQuietMode: QuietMode is " + this.mQuietMode + " (app_type=" + this.mCurrentAppType + " cdmaSource=" + i + ")");
            this.mInitialized = true;
            if (this.mIsCardStatusAvailable) {
                sendMessage(obtainMessage(3));
            }
        }
    }

    private void updateStateProperty() {
        setSystemProperty("gsm.sim.state", getState().toString());
    }

    public void broadcastIccStateChangedIntentAbsent() {
        synchronized (this.mLock) {
            Intent intent = new Intent("jp.co.sharp.android.telephony.action.SIM_STATE_CHANGED_ABSENT");
            intent.addFlags(536870912);
            log("Broadcasting intent ACTION_SIM_STATE_CHANGED_ABSENT");
            ActivityManagerNative.broadcastStickyIntent(intent, "android.permission.READ_PHONE_STATE", -1);
        }
    }

    public void broadcastIccStateChangedIntentSharp(String str, String str2) {
        synchronized (this.mLock) {
            if (this.mQuietMode) {
                log("QuietMode: NOT Broadcasting intent ACTION_SIM_STATE_CHANGED_SHARP " + str + " reason " + str2);
                return;
            }
            Intent intent = new Intent("jp.co.sharp.android.uim.intent.action.SIM_STATE_CHANGED_SHARP");
            intent.addFlags(536870912);
            intent.putExtra("phoneName", "Phone");
            intent.putExtra(IccCard.INTENT_KEY_ICC_STATE_SHARP, str);
            intent.putExtra(IccCard.INTENT_KEY_LOCKED_REASON_SHARP, str2);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhoneId.intValue());
            log("Broadcasting intent ACTION_SIM_STATE_CHANGED_SHARP " + str + " reason " + str2 + " for mPhoneId : " + this.mPhoneId);
            ActivityManagerNative.broadcastStickyIntent(intent, "android.permission.READ_PHONE_STATE", -1);
            queueNextBroadcastSharpPoll(str, str2);
        }
    }

    /* JADX WARNING: Missing block: B:14:?, code skipped:
            return;
     */
    public void changeIccFdnPassword(java.lang.String r4, java.lang.String r5, android.os.Message r6) {
        /*
        r3 = this;
        r1 = r3.mLock;
        monitor-enter(r1);
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r0 == 0) goto L_0x000e;
    L_0x0007:
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r0.changeIccFdnPassword(r4, r5, r6);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r6 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r2 = "ICC card is absent.";
        r0.<init>(r2);	 Catch:{ all -> 0x0022 }
        r2 = android.os.AsyncResult.forMessage(r6);	 Catch:{ all -> 0x0022 }
        r2.exception = r0;	 Catch:{ all -> 0x0022 }
        r6.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.changeIccFdnPassword(java.lang.String, java.lang.String, android.os.Message):void");
    }

    /* JADX WARNING: Missing block: B:14:?, code skipped:
            return;
     */
    public void changeIccLockPassword(java.lang.String r4, java.lang.String r5, android.os.Message r6) {
        /*
        r3 = this;
        r1 = r3.mLock;
        monitor-enter(r1);
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r0 == 0) goto L_0x000e;
    L_0x0007:
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r0.changeIccLockPassword(r4, r5, r6);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r6 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r2 = "ICC card is absent.";
        r0.<init>(r2);	 Catch:{ all -> 0x0022 }
        r2 = android.os.AsyncResult.forMessage(r6);	 Catch:{ all -> 0x0022 }
        r2.exception = r0;	 Catch:{ all -> 0x0022 }
        r6.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.changeIccLockPassword(java.lang.String, java.lang.String, android.os.Message):void");
    }

    public void closeLogicalChannel(int i, Message message) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.closeLogicalChannel(i, message);
            } else {
                log("UiccApplication is not exist.");
                AsyncResult.forMessage(message).exception = new RuntimeException("ICC card is absent.");
                message.sendToTarget();
            }
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

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        int i;
        int i2 = 0;
        printWriter.println("IccCardProxy: " + this);
        printWriter.println(" mContext=" + this.mContext);
        printWriter.println(" mCi=" + this.mCi);
        printWriter.println(" mAbsentRegistrants: size=" + this.mAbsentRegistrants.size());
        for (i = 0; i < this.mAbsentRegistrants.size(); i++) {
            printWriter.println("  mAbsentRegistrants[" + i + "]=" + ((Registrant) this.mAbsentRegistrants.get(i)).getHandler());
        }
        printWriter.println(" mPinLockedRegistrants: size=" + this.mPinLockedRegistrants.size());
        for (i = 0; i < this.mPinLockedRegistrants.size(); i++) {
            printWriter.println("  mPinLockedRegistrants[" + i + "]=" + ((Registrant) this.mPinLockedRegistrants.get(i)).getHandler());
        }
        printWriter.println(" mPersoLockedRegistrants: size=" + this.mPersoLockedRegistrants.size());
        while (i2 < this.mPersoLockedRegistrants.size()) {
            printWriter.println("  mPersoLockedRegistrants[" + i2 + "]=" + ((Registrant) this.mPersoLockedRegistrants.get(i2)).getHandler());
            i2++;
        }
        printWriter.println(" mCurrentAppType=" + this.mCurrentAppType);
        printWriter.println(" mUiccController=" + this.mUiccController);
        printWriter.println(" mUiccCard=" + this.mUiccCard);
        printWriter.println(" mUiccApplication=" + this.mUiccApplication);
        printWriter.println(" mIccRecords=" + this.mIccRecords);
        printWriter.println(" mCdmaSSM=" + this.mCdmaSSM);
        printWriter.println(" mRadioOn=" + this.mRadioOn);
        printWriter.println(" mQuietMode=" + this.mQuietMode);
        printWriter.println(" mInitialized=" + this.mInitialized);
        printWriter.println(" mExternalState=" + this.mExternalState);
        printWriter.flush();
    }

    public void exchangeAPDU(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.exchangeAPDU(i, i2, i3, i4, i5, i6, str, message);
            } else {
                log("UiccApplication is not exist.");
                AsyncResult.forMessage(message).exception = new RuntimeException("ICC card is absent.");
                message.sendToTarget();
            }
        }
    }

    public void exchangeSimIO(int i, int i2, int i3, int i4, int i5, String str, Message message) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.exchangeSimIO(i, i2, i3, i4, i5, str, message);
            } else {
                log("UiccApplication is not exist.");
                AsyncResult.forMessage(message).exception = new RuntimeException("ICC card is absent.");
                message.sendToTarget();
            }
        }
    }

    public void getEfLock(Message message) {
        if (TelBrand.IS_DCM) {
            synchronized (this.mLock) {
                IccFileHandler iccFileHandler = getIccFileHandler();
                Object obj = (this.mUiccApplication == null || this.mUiccApplication.getType() != AppType.APPTYPE_USIM) ? null : 1;
                if (iccFileHandler == null || obj == null) {
                    AsyncResult.forMessage(message, null, null);
                    message.sendToTarget();
                } else {
                    iccFileHandler.loadEFTransparent(IccConstants.EF_LOCK, 8, obtainMessage(23, message));
                }
            }
        }
    }

    public boolean getIccFdnAvailable() {
        return this.mUiccApplication != null ? this.mUiccApplication.getIccFdnAvailable() : false;
    }

    public boolean getIccFdnEnabled() {
        boolean booleanValue;
        synchronized (this.mLock) {
            booleanValue = Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccFdnEnabled() : false).booleanValue();
        }
        return booleanValue;
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

    public boolean getIccLockEnabled() {
        boolean booleanValue;
        synchronized (this.mLock) {
            booleanValue = Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccLockEnabled() : false).booleanValue();
        }
        return booleanValue;
    }

    public boolean getIccPin2Blocked() {
        return Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccPin2Blocked() : false).booleanValue();
    }

    public int getIccPinPukRetryCountSc(int i) {
        int iccPinPukRetryCountSc;
        synchronized (this.mLock) {
            iccPinPukRetryCountSc = this.mUiccCard != null ? this.mUiccCard.getIccPinPukRetryCountSc(i) : -1;
        }
        return iccPinPukRetryCountSc;
    }

    public boolean getIccPuk2Blocked() {
        return Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccPuk2Blocked() : false).booleanValue();
    }

    public IccRecords getIccRecords() {
        IccRecords iccRecords;
        synchronized (this.mLock) {
            iccRecords = this.mIccRecords;
        }
        return iccRecords;
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

    /* Access modifiers changed, original: protected */
    public String getIccStateIntentStringSharp(State state) {
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

    /* Access modifiers changed, original: protected */
    public String getIccStateReasonSharp(State state) {
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$IccCardConstants$State[state.ordinal()]) {
            case 2:
                return "PIN";
            case 3:
                return "PUK";
            case 4:
                return "PERSO";
            case 7:
                return "PERM_DISABLED";
            case 8:
                return "CARD_IO_ERROR";
            case 9:
                return IccCard.INTENT_VALUE_LOCKED_NETWORK_SUBSET;
            case 10:
                return IccCard.INTENT_VALUE_LOCKED_FOREVER;
            default:
                return null;
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

    public int getSimLock() {
        return this.mSimLock;
    }

    public State getState() {
        State state;
        synchronized (this.mLock) {
            state = this.mExternalState;
        }
        return state;
    }

    /* JADX WARNING: Removed duplicated region for block: B:99:0x0240  */
    /* JADX WARNING: Removed duplicated region for block: B:97:0x0236  */
    public void handleMessage(android.os.Message r10) {
        /*
        r9 = this;
        r8 = 0;
        r3 = 3;
        r2 = 2;
        r1 = 0;
        r7 = 1;
        r0 = r10.what;
        switch(r0) {
            case 1: goto L_0x0023;
            case 2: goto L_0x0035;
            case 3: goto L_0x0042;
            case 4: goto L_0x004c;
            case 5: goto L_0x0057;
            case 6: goto L_0x005b;
            case 7: goto L_0x0061;
            case 8: goto L_0x0126;
            case 9: goto L_0x012d;
            case 11: goto L_0x0145;
            case 17: goto L_0x01b9;
            case 20: goto L_0x026b;
            case 21: goto L_0x026b;
            case 22: goto L_0x026b;
            case 23: goto L_0x026b;
            case 24: goto L_0x014d;
            case 25: goto L_0x01e6;
            case 30: goto L_0x01cc;
            case 500: goto L_0x0183;
            case 501: goto L_0x016f;
            case 502: goto L_0x0179;
            case 503: goto L_0x01a6;
            default: goto L_0x000a;
        };
    L_0x000a:
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "Unhandled message with number: ";
        r0 = r0.append(r1);
        r1 = r10.what;
        r0 = r0.append(r1);
        r0 = r0.toString();
        r9.loge(r0);
    L_0x0022:
        return;
    L_0x0023:
        r9.mRadioOn = r1;
        r0 = com.android.internal.telephony.CommandsInterface.RadioState.RADIO_UNAVAILABLE;
        r1 = r9.mCi;
        r1 = r1.getRadioState();
        if (r0 != r1) goto L_0x0022;
    L_0x002f:
        r0 = com.android.internal.telephony.IccCardConstants.State.NOT_READY;
        r9.setExternalState(r0);
        goto L_0x0022;
    L_0x0035:
        r9.mRadioOn = r7;
        r0 = r9.mInitialized;
        if (r0 != 0) goto L_0x003e;
    L_0x003b:
        r9.updateQuietMode();
    L_0x003e:
        r9.updateExternalState();
        goto L_0x0022;
    L_0x0042:
        r9.mIsCardStatusAvailable = r7;
        r0 = r9.mInitialized;
        if (r0 == 0) goto L_0x0022;
    L_0x0048:
        r9.updateIccAvailability();
        goto L_0x0022;
    L_0x004c:
        r0 = r9.mAbsentRegistrants;
        r0.notifyRegistrants();
        r0 = com.android.internal.telephony.IccCardConstants.State.ABSENT;
        r9.setExternalState(r0);
        goto L_0x0022;
    L_0x0057:
        r9.processLockedState();
        goto L_0x0022;
    L_0x005b:
        r0 = com.android.internal.telephony.IccCardConstants.State.READY;
        r9.setExternalState(r0);
        goto L_0x0022;
    L_0x0061:
        r0 = r9.mIccRecords;
        if (r0 == 0) goto L_0x00e0;
    L_0x0065:
        r0 = r9.mIccRecords;
        r0 = r0.getOperatorNumeric();
        r4 = r9.mPhoneId;
        r4 = r4.intValue();
        r5 = new java.lang.StringBuilder;
        r5.<init>();
        r6 = "operator=";
        r5 = r5.append(r6);
        r5 = r5.append(r0);
        r6 = " slotId=";
        r5 = r5.append(r6);
        r4 = r5.append(r4);
        r4 = r4.toString();
        r9.log(r4);
        if (r0 == 0) goto L_0x011b;
    L_0x0093:
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "update icc_operator_numeric=";
        r4 = r4.append(r5);
        r4 = r4.append(r0);
        r4 = r4.toString();
        r9.log(r4);
        r4 = "gsm.sim.operator.numeric";
        r9.setSystemProperty(r4, r0);
        r4 = r9.mCurrentAppType;
        if (r4 != r7) goto L_0x00f5;
    L_0x00b2:
        r2 = "gsm.apn.sim.operator.numeric";
        r9.setSystemProperty(r2, r0);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "update sim_operator_numeric=";
        r2 = r2.append(r4);
        r2 = r2.append(r0);
        r2 = r2.toString();
        r9.log(r2);
    L_0x00cd:
        r0 = r0.substring(r1, r3);
        if (r0 == 0) goto L_0x0115;
    L_0x00d3:
        r1 = "gsm.sim.operator.iso-country";
        r0 = java.lang.Integer.parseInt(r0);
        r0 = com.android.internal.telephony.MccTable.countryCodeForMcc(r0);
        r9.setSystemProperty(r1, r0);
    L_0x00e0:
        r0 = r9.mUiccCard;
        if (r0 == 0) goto L_0x0121;
    L_0x00e4:
        r0 = r9.mUiccCard;
        r0 = r0.areCarrierPriviligeRulesLoaded();
        if (r0 != 0) goto L_0x0121;
    L_0x00ec:
        r0 = r9.mUiccCard;
        r1 = 503; // 0x1f7 float:7.05E-43 double:2.485E-321;
        r0.registerForCarrierPrivilegeRulesLoaded(r9, r1, r8);
        goto L_0x0022;
    L_0x00f5:
        r4 = r9.mCurrentAppType;
        if (r4 != r2) goto L_0x00cd;
    L_0x00f9:
        r2 = "net.cdma.ruim.operator.numeric";
        r9.setSystemProperty(r2, r0);
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "update ruim_operator_numeric=";
        r2 = r2.append(r4);
        r2 = r2.append(r0);
        r2 = r2.toString();
        r9.log(r2);
        goto L_0x00cd;
    L_0x0115:
        r0 = "EVENT_RECORDS_LOADED Country code is null";
        r9.loge(r0);
        goto L_0x00e0;
    L_0x011b:
        r0 = "EVENT_RECORDS_LOADED Operator name is null";
        r9.loge(r0);
        goto L_0x00e0;
    L_0x0121:
        r9.onRecordsLoaded();
        goto L_0x0022;
    L_0x0126:
        r0 = "IMSI";
        r9.broadcastIccStateChangedIntent(r0, r8);
        goto L_0x0022;
    L_0x012d:
        r0 = r9.mUiccApplication;
        r0 = r0.getPersoSubState();
        r9.mPersoSubState = r0;
        r1 = r9.mPersoLockedRegistrants;
        r0 = r10.obj;
        r0 = (android.os.AsyncResult) r0;
        r1.notifyRegistrants(r0);
        r0 = com.android.internal.telephony.IccCardConstants.State.PERSO_LOCKED;
        r9.setExternalState(r0);
        goto L_0x0022;
    L_0x0145:
        r9.updateQuietMode();
        r9.updateActiveRecord();
        goto L_0x0022;
    L_0x014d:
        r1 = r9.mLock;
        monitor-enter(r1);
        r0 = r9.mCi;	 Catch:{ all -> 0x015e }
        r0 = r0.isRunning();	 Catch:{ all -> 0x015e }
        if (r0 == 0) goto L_0x0161;
    L_0x0158:
        r9.requestUnsolRadioStateChanged();	 Catch:{ all -> 0x015e }
    L_0x015b:
        monitor-exit(r1);	 Catch:{ all -> 0x015e }
        goto L_0x0022;
    L_0x015e:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x015e }
        throw r0;
    L_0x0161:
        r0 = r9.obtainMessage();	 Catch:{ all -> 0x015e }
        r2 = 24;
        r0.what = r2;	 Catch:{ all -> 0x015e }
        r2 = 1000; // 0x3e8 float:1.401E-42 double:4.94E-321;
        r9.sendMessageDelayed(r0, r2);	 Catch:{ all -> 0x015e }
        goto L_0x015b;
    L_0x016f:
        r0 = "EVENT_SUBSCRIPTION_ACTIVATED";
        r9.log(r0);
        r9.onSubscriptionActivated();
        goto L_0x0022;
    L_0x0179:
        r0 = "EVENT_SUBSCRIPTION_DEACTIVATED";
        r9.log(r0);
        r9.onSubscriptionDeactivated();
        goto L_0x0022;
    L_0x0183:
        r0 = r9.mCurrentAppType;
        if (r0 != r7) goto L_0x0022;
    L_0x0187:
        r0 = r9.mIccRecords;
        if (r0 == 0) goto L_0x0022;
    L_0x018b:
        r0 = r10.obj;
        r0 = (android.os.AsyncResult) r0;
        r0 = r0.result;
        r0 = (java.lang.Integer) r0;
        r0 = r0.intValue();
        if (r0 != r2) goto L_0x0022;
    L_0x0199:
        r0 = "gsm.sim.operator.alpha";
        r1 = r9.mIccRecords;
        r1 = r1.getServiceProviderName();
        r9.setSystemProperty(r0, r1);
        goto L_0x0022;
    L_0x01a6:
        r0 = "EVENT_CARRIER_PRIVILEGES_LOADED";
        r9.log(r0);
        r0 = r9.mUiccCard;
        if (r0 == 0) goto L_0x01b4;
    L_0x01af:
        r0 = r9.mUiccCard;
        r0.unregisterForCarrierPrivilegeRulesLoaded(r9);
    L_0x01b4:
        r9.onRecordsLoaded();
        goto L_0x0022;
    L_0x01b9:
        r0 = r10.obj;
        r0 = (android.os.AsyncResult) r0;
        r9.onGetPinPukRetryCountDone(r0);
        r9.notifyCurrentExternalState();
        r0 = com.android.internal.telephony.TelBrand.IS_SBM;
        if (r0 == 0) goto L_0x0022;
    L_0x01c7:
        r9.notifyCurrentExternalStateSharp();
        goto L_0x0022;
    L_0x01cc:
        r2 = r9.mLock;
        monitor-enter(r2);
        r0 = r10.obj;	 Catch:{ all -> 0x01e3 }
        r0 = (java.lang.String[]) r0;	 Catch:{ all -> 0x01e3 }
        r0 = (java.lang.String[]) r0;	 Catch:{ all -> 0x01e3 }
        r1 = r0[r1];
        r0 = r0[r7];
        r3 = r9.dontPollBroadcastSharp;	 Catch:{ all -> 0x01e3 }
        if (r3 != 0) goto L_0x01e0;
    L_0x01dd:
        r9.broadcastIccStateChangedIntentSharp(r1, r0);	 Catch:{ all -> 0x01e3 }
    L_0x01e0:
        monitor-exit(r2);	 Catch:{ all -> 0x01e3 }
        goto L_0x0022;
    L_0x01e3:
        r0 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x01e3 }
        throw r0;
    L_0x01e6:
        r0 = "EVENT_GET_SIM_LOCK_DONE";
        r9.log(r0);
        r0 = r10.obj;
        r0 = (android.os.AsyncResult) r0;
        r4 = r0.exception;
        if (r4 == 0) goto L_0x020f;
    L_0x01f3:
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "Error in get sim lock with exception: ";
        r1 = r1.append(r2);
        r0 = r0.exception;
        r0 = r1.append(r0);
        r0 = r0.toString();
        r9.loge(r0);
        r9.mSimLock = r7;
        goto L_0x0022;
    L_0x020f:
        r4 = r0.result;
        if (r4 == 0) goto L_0x0262;
    L_0x0213:
        r0 = r0.result;
        r0 = (byte[]) r0;
        r0 = (byte[]) r0;
        r4 = java.nio.ByteBuffer.wrap(r0);
        r5 = 64;
        r6 = r0.length;
        if (r5 != r6) goto L_0x0247;
    L_0x0222:
        r5 = r1 + 4;
        r5 = r5 + -1;
        r6 = r0.length;
        if (r5 >= r6) goto L_0x029f;
    L_0x0229:
        r5 = r4.getInt(r1);
        if (r5 == 0) goto L_0x023d;
    L_0x022f:
        r0 = r2;
    L_0x0230:
        r9.mSimLock = r0;
        r0 = r9.mSimLock;
        if (r2 != r0) goto L_0x0240;
    L_0x0236:
        r0 = "mSimlock is ON";
        r9.log(r0);
        goto L_0x0022;
    L_0x023d:
        r1 = r1 + 8;
        goto L_0x0222;
    L_0x0240:
        r0 = "mSimlock is OFF";
        r9.log(r0);
        goto L_0x0022;
    L_0x0247:
        r9.mSimLock = r7;
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "The response has unexpected size: ";
        r1 = r1.append(r2);
        r0 = r0.length;
        r0 = r1.append(r0);
        r0 = r0.toString();
        r9.loge(r0);
        goto L_0x0022;
    L_0x0262:
        r9.mSimLock = r7;
        r0 = "No result in get sim lock";
        r9.loge(r0);
        goto L_0x0022;
    L_0x026b:
        r0 = r10.obj;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.exception;
        if (r1 == 0) goto L_0x028b;
    L_0x0273:
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "Error in SIM access with exception";
        r1 = r1.append(r2);
        r2 = r0.exception;
        r1 = r1.append(r2);
        r1 = r1.toString();
        r9.loge(r1);
    L_0x028b:
        r1 = r0.userObj;
        r1 = (android.os.Message) r1;
        r2 = r0.result;
        r3 = r0.exception;
        android.os.AsyncResult.forMessage(r1, r2, r3);
        r0 = r0.userObj;
        r0 = (android.os.Message) r0;
        r0.sendToTarget();
        goto L_0x0022;
    L_0x029f:
        r0 = r3;
        goto L_0x0230;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.handleMessage(android.os.Message):void");
    }

    /* JADX WARNING: Missing block: B:15:?, code skipped:
            return false;
     */
    public boolean hasIccCard() {
        /*
        r3 = this;
        r1 = r3.mLock;
        monitor-enter(r1);
        r0 = r3.mUiccCard;	 Catch:{ all -> 0x0017 }
        if (r0 == 0) goto L_0x0014;
    L_0x0007:
        r0 = r3.mUiccCard;	 Catch:{ all -> 0x0017 }
        r0 = r0.getCardState();	 Catch:{ all -> 0x0017 }
        r2 = com.android.internal.telephony.uicc.IccCardStatus.CardState.CARDSTATE_ABSENT;	 Catch:{ all -> 0x0017 }
        if (r0 == r2) goto L_0x0014;
    L_0x0011:
        monitor-exit(r1);	 Catch:{ all -> 0x0017 }
        r0 = 1;
    L_0x0013:
        return r0;
    L_0x0014:
        monitor-exit(r1);	 Catch:{ all -> 0x0017 }
        r0 = 0;
        goto L_0x0013;
    L_0x0017:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0017 }
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.hasIccCard():boolean");
    }

    public boolean isApplicationOnIcc(AppType appType) {
        boolean booleanValue;
        synchronized (this.mLock) {
            booleanValue = Boolean.valueOf(this.mUiccCard != null ? this.mUiccCard.isApplicationOnIcc(appType) : false).booleanValue();
        }
        return booleanValue;
    }

    public void openLogicalChannel(String str, Message message) {
        synchronized (this.mLock) {
            if (this.mUiccApplication == null) {
                log("UiccApplication is not exist.");
                AsyncResult.forMessage(message).exception = new RuntimeException("ICC card is absent.");
                message.sendToTarget();
            } else if (TelBrand.IS_DCM) {
                this.mIccOpenLogicalChannel.iccOpenChannel(str, obtainMessage(21, message));
            } else {
                this.mUiccApplication.openLogicalChannel(str, message);
            }
        }
    }

    public void registerForAbsent(Handler handler, int i, Object obj) {
        synchronized (this.mLock) {
            Registrant registrant = new Registrant(handler, i, obj);
            this.mAbsentRegistrants.add(registrant);
            if (getState() == State.ABSENT) {
                registrant.notifyRegistrant();
            }
        }
    }

    public void registerForLocked(Handler handler, int i, Object obj) {
        synchronized (this.mLock) {
            Registrant registrant = new Registrant(handler, i, obj);
            this.mPinLockedRegistrants.add(registrant);
            if (getState().isPinLocked()) {
                registrant.notifyRegistrant();
            }
        }
    }

    public void registerForPersoLocked(Handler handler, int i, Object obj) {
        synchronized (this.mLock) {
            Registrant registrant = new Registrant(handler, i, obj);
            this.mPersoLockedRegistrants.add(registrant);
            if (getState() == State.PERSO_LOCKED) {
                registrant.notifyRegistrant(new AsyncResult(null, Integer.valueOf(this.mPersoSubState.ordinal()), null));
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void resetProperties() {
        if (this.mCurrentAppType == 1) {
            log("update icc_operator_numeric=");
            setSystemProperty("gsm.sim.operator.numeric", "");
            setSystemProperty("gsm.sim.operator.iso-country", "");
            setSystemProperty("gsm.sim.operator.alpha", "");
        }
    }

    public void responseBroadcastIntentSharp() {
        synchronized (this.mLock) {
            log("responseBroadcastIntentSharp");
            this.dontPollBroadcastSharp = true;
        }
    }

    /* Access modifiers changed, original: protected */
    /* JADX WARNING: Missing block: B:26:?, code skipped:
            return;
     */
    public void setExternalStateSharp(com.android.internal.telephony.IccCardConstants.State r4, boolean r5) {
        /*
        r3 = this;
        r1 = r3.mLock;
        monitor-enter(r1);
        if (r5 != 0) goto L_0x000b;
    L_0x0005:
        r0 = r3.mExternalStateSharp;	 Catch:{ all -> 0x0023 }
        if (r4 != r0) goto L_0x000b;
    L_0x0009:
        monitor-exit(r1);	 Catch:{ all -> 0x0023 }
    L_0x000a:
        return;
    L_0x000b:
        r3.mExternalStateSharp = r4;	 Catch:{ all -> 0x0023 }
        r0 = com.android.internal.telephony.IccCardConstants.State.PIN_REQUIRED;	 Catch:{ all -> 0x0023 }
        if (r4 == r0) goto L_0x0021;
    L_0x0011:
        r0 = com.android.internal.telephony.IccCardConstants.State.PUK_REQUIRED;	 Catch:{ all -> 0x0023 }
        if (r4 == r0) goto L_0x0021;
    L_0x0015:
        r0 = com.android.internal.telephony.IccCardConstants.State.PERSO_LOCKED;	 Catch:{ all -> 0x0023 }
        if (r4 == r0) goto L_0x0021;
    L_0x0019:
        r0 = com.android.internal.telephony.IccCardConstants.State.READY;	 Catch:{ all -> 0x0023 }
        if (r4 == r0) goto L_0x0021;
    L_0x001d:
        r0 = com.android.internal.telephony.IccCardConstants.State.PERM_DISABLED;	 Catch:{ all -> 0x0023 }
        if (r4 != r0) goto L_0x0026;
    L_0x0021:
        monitor-exit(r1);	 Catch:{ all -> 0x0023 }
        goto L_0x000a;
    L_0x0023:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0023 }
        throw r0;
    L_0x0026:
        r0 = r3.mExternalStateSharp;	 Catch:{ all -> 0x0023 }
        r0 = r3.getIccStateIntentStringSharp(r0);	 Catch:{ all -> 0x0023 }
        r2 = r3.mExternalStateSharp;	 Catch:{ all -> 0x0023 }
        r2 = r3.getIccStateReasonSharp(r2);	 Catch:{ all -> 0x0023 }
        r3.broadcastIccStateChangedIntentSharp(r0, r2);	 Catch:{ all -> 0x0023 }
        monitor-exit(r1);	 Catch:{ all -> 0x0023 }
        goto L_0x000a;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.setExternalStateSharp(com.android.internal.telephony.IccCardConstants$State, boolean):void");
    }

    /* JADX WARNING: Missing block: B:14:?, code skipped:
            return;
     */
    public void setIccFdnEnabled(boolean r4, java.lang.String r5, android.os.Message r6) {
        /*
        r3 = this;
        r1 = r3.mLock;
        monitor-enter(r1);
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r0 == 0) goto L_0x000e;
    L_0x0007:
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r0.setIccFdnEnabled(r4, r5, r6);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r6 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r2 = "ICC card is absent.";
        r0.<init>(r2);	 Catch:{ all -> 0x0022 }
        r2 = android.os.AsyncResult.forMessage(r6);	 Catch:{ all -> 0x0022 }
        r2.exception = r0;	 Catch:{ all -> 0x0022 }
        r6.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.setIccFdnEnabled(boolean, java.lang.String, android.os.Message):void");
    }

    /* JADX WARNING: Missing block: B:14:?, code skipped:
            return;
     */
    public void setIccLockEnabled(boolean r4, java.lang.String r5, android.os.Message r6) {
        /*
        r3 = this;
        r1 = r3.mLock;
        monitor-enter(r1);
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r0 == 0) goto L_0x000e;
    L_0x0007:
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r0.setIccLockEnabled(r4, r5, r6);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r6 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r2 = "ICC card is absent.";
        r0.<init>(r2);	 Catch:{ all -> 0x0022 }
        r2 = android.os.AsyncResult.forMessage(r6);	 Catch:{ all -> 0x0022 }
        r2.exception = r0;	 Catch:{ all -> 0x0022 }
        r6.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.setIccLockEnabled(boolean, java.lang.String, android.os.Message):void");
    }

    public void setPinPukRetryCount(int i, int i2) {
        synchronized (this.mLock) {
            if (this.mUiccCard == null) {
                loge("Error in set pin puk retry count with mUiccCard is null");
                return;
            }
            this.mUiccCard.setPinPukRetryCount(i, i2);
        }
    }

    public void setVoiceRadioTech(int i) {
        synchronized (this.mLock) {
            log("Setting radio tech " + ServiceState.rilRadioTechnologyToString(i));
            if (ServiceState.isGsm(i)) {
                this.mCurrentAppType = 1;
            } else {
                this.mCurrentAppType = 2;
            }
            updateQuietMode();
            updateActiveRecord();
        }
    }

    /* JADX WARNING: Missing block: B:14:?, code skipped:
            return;
     */
    public void supplyDepersonalization(java.lang.String r4, java.lang.String r5, android.os.Message r6) {
        /*
        r3 = this;
        r1 = r3.mLock;
        monitor-enter(r1);
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r0 == 0) goto L_0x000e;
    L_0x0007:
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r0.supplyDepersonalization(r4, r5, r6);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r6 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r2 = "CommandsInterface is not set.";
        r0.<init>(r2);	 Catch:{ all -> 0x0022 }
        r2 = android.os.AsyncResult.forMessage(r6);	 Catch:{ all -> 0x0022 }
        r2.exception = r0;	 Catch:{ all -> 0x0022 }
        r6.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.supplyDepersonalization(java.lang.String, java.lang.String, android.os.Message):void");
    }

    /* JADX WARNING: Missing block: B:14:?, code skipped:
            return;
     */
    public void supplyPin(java.lang.String r4, android.os.Message r5) {
        /*
        r3 = this;
        r1 = r3.mLock;
        monitor-enter(r1);
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r0 == 0) goto L_0x000e;
    L_0x0007:
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r0.supplyPin(r4, r5);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r5 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r2 = "ICC card is absent.";
        r0.<init>(r2);	 Catch:{ all -> 0x0022 }
        r2 = android.os.AsyncResult.forMessage(r5);	 Catch:{ all -> 0x0022 }
        r2.exception = r0;	 Catch:{ all -> 0x0022 }
        r5.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.supplyPin(java.lang.String, android.os.Message):void");
    }

    /* JADX WARNING: Missing block: B:14:?, code skipped:
            return;
     */
    public void supplyPin2(java.lang.String r4, android.os.Message r5) {
        /*
        r3 = this;
        r1 = r3.mLock;
        monitor-enter(r1);
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r0 == 0) goto L_0x000e;
    L_0x0007:
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r0.supplyPin2(r4, r5);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r5 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r2 = "ICC card is absent.";
        r0.<init>(r2);	 Catch:{ all -> 0x0022 }
        r2 = android.os.AsyncResult.forMessage(r5);	 Catch:{ all -> 0x0022 }
        r2.exception = r0;	 Catch:{ all -> 0x0022 }
        r5.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.supplyPin2(java.lang.String, android.os.Message):void");
    }

    /* JADX WARNING: Missing block: B:14:?, code skipped:
            return;
     */
    public void supplyPuk(java.lang.String r4, java.lang.String r5, android.os.Message r6) {
        /*
        r3 = this;
        r1 = r3.mLock;
        monitor-enter(r1);
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r0 == 0) goto L_0x000e;
    L_0x0007:
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r0.supplyPuk(r4, r5, r6);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r6 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r2 = "ICC card is absent.";
        r0.<init>(r2);	 Catch:{ all -> 0x0022 }
        r2 = android.os.AsyncResult.forMessage(r6);	 Catch:{ all -> 0x0022 }
        r2.exception = r0;	 Catch:{ all -> 0x0022 }
        r6.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.supplyPuk(java.lang.String, java.lang.String, android.os.Message):void");
    }

    /* JADX WARNING: Missing block: B:14:?, code skipped:
            return;
     */
    public void supplyPuk2(java.lang.String r4, java.lang.String r5, android.os.Message r6) {
        /*
        r3 = this;
        r1 = r3.mLock;
        monitor-enter(r1);
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        if (r0 == 0) goto L_0x000e;
    L_0x0007:
        r0 = r3.mUiccApplication;	 Catch:{ all -> 0x0022 }
        r0.supplyPuk2(r4, r5, r6);	 Catch:{ all -> 0x0022 }
    L_0x000c:
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
    L_0x000d:
        return;
    L_0x000e:
        if (r6 == 0) goto L_0x000c;
    L_0x0010:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x0022 }
        r2 = "ICC card is absent.";
        r0.<init>(r2);	 Catch:{ all -> 0x0022 }
        r2 = android.os.AsyncResult.forMessage(r6);	 Catch:{ all -> 0x0022 }
        r2.exception = r0;	 Catch:{ all -> 0x0022 }
        r6.sendToTarget();	 Catch:{ all -> 0x0022 }
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        goto L_0x000d;
    L_0x0022:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0022 }
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.IccCardProxy.supplyPuk2(java.lang.String, java.lang.String, android.os.Message):void");
    }

    public void unregisterForAbsent(Handler handler) {
        synchronized (this.mLock) {
            this.mAbsentRegistrants.remove(handler);
        }
    }

    public void unregisterForLocked(Handler handler) {
        synchronized (this.mLock) {
            this.mPinLockedRegistrants.remove(handler);
        }
    }

    public void unregisterForPersoLocked(Handler handler) {
        synchronized (this.mLock) {
            this.mPersoLockedRegistrants.remove(handler);
        }
    }

    /* Access modifiers changed, original: protected */
    public void updateExternalStateSharp() {
        if (this.mUiccCard != null) {
            if (this.mUiccCard.getCardState() == CardState.CARDSTATE_ERROR) {
                setExternalStateSharp(State.CARD_IO_ERROR);
            } else if (this.mUiccCard.getCardState() == CardState.CARDSTATE_ABSENT) {
                if (1 == SystemProperties.getInt(SIM_ABSENT_CHECK, 0)) {
                    setExternalStateSharp(State.ABSENT);
                } else {
                    Rlog.d(LOG_TAG, "updateExternalStateSharp: waiting for the actual SIM state...");
                }
            } else if (this.mUiccApplication == null) {
                setExternalStateSharp(State.UNKNOWN);
            } else if (this.mUiccApplication.getPin1State() == PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
                setExternalStateSharp(State.FOREVER);
            } else {
                switch (this.mUiccApplication.getState()) {
                    case APPSTATE_UNKNOWN:
                    case APPSTATE_DETECTED:
                        setExternalStateSharp(State.UNKNOWN);
                        return;
                    case APPSTATE_PIN:
                        setExternalStateSharp(State.PIN_REQUIRED);
                        return;
                    case APPSTATE_PUK:
                        setExternalStateSharp(State.PUK_REQUIRED);
                        return;
                    case APPSTATE_SUBSCRIPTION_PERSO:
                        if (this.mUiccApplication.isPersoLocked()) {
                            setExternalStateSharp(State.SIM_NETWORK_SUBSET_LOCKED);
                            return;
                        } else {
                            setExternalStateSharp(State.UNKNOWN);
                            return;
                        }
                    case APPSTATE_READY:
                        setExternalStateSharp(State.READY);
                        return;
                    default:
                        return;
                }
            }
        }
    }
}
