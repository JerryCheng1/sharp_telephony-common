package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.RIL.UnsolOemHookBuffer;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccController;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class ModemStackController extends Handler {
    private static final int BIND_TO_STACK = 1;
    private static final int CMD_DEACTIVATE_ALL_SUBS = 1;
    private static final int CMD_TRIGGER_BIND = 5;
    private static final int CMD_TRIGGER_UNBIND = 3;
    private static final int DEFAULT_MAX_DATA_ALLOWED = 1;
    private static final int EVENT_BIND_DONE = 6;
    private static final int EVENT_GET_MODEM_CAPS_DONE = 2;
    private static final int EVENT_MODEM_CAPABILITY_CHANGED = 10;
    private static final int EVENT_RADIO_AVAILABLE = 9;
    private static final int EVENT_RADIO_NOT_AVAILABLE = 11;
    private static final int EVENT_SET_PREF_MODE_DONE = 7;
    private static final int EVENT_SUB_DEACTIVATED = 8;
    private static final int EVENT_UNBIND_DONE = 4;
    private static final int FAILURE = 0;
    private static final int GET_MODEM_CAPS_BUFFER_LEN = 7;
    static final String LOG_TAG = "ModemStackController";
    private static final int PRIMARY_STACK_ID = 0;
    private static final int STATE_BIND = 5;
    private static final int STATE_GOT_MODEM_CAPS = 2;
    private static final int STATE_SET_PREF_MODE = 7;
    private static final int STATE_SUB_ACT = 6;
    private static final int STATE_SUB_DEACT = 3;
    private static final int STATE_UNBIND = 4;
    private static final int STATE_UNKNOWN = 1;
    private static final int SUCCESS = 1;
    private static final int UNBIND_TO_STACK = 0;
    private static ModemStackController sModemStackController;
    private int mActiveSubCount = 0;
    private CommandsInterface[] mCi;
    private boolean[] mCmdFailed = new boolean[this.mNumPhones];
    private Context mContext;
    private int[] mCurrentStackId = new int[this.mNumPhones];
    private boolean mDeactivationInProgress = false;
    private int mDeactivedSubCount = 0;
    private boolean mIsPhoneInEcbmMode = false;
    private boolean mIsRecoveryInProgress = false;
    private boolean mIsStackReady = false;
    private ModemCapabilityInfo[] mModemCapInfo = null;
    private RegistrantList mModemDataCapsAvailableRegistrants = new RegistrantList();
    private boolean mModemRatCapabilitiesAvailable = false;
    private RegistrantList mModemRatCapsAvailableRegistrants = new RegistrantList();
    private int mNumPhones = TelephonyManager.getDefault().getPhoneCount();
    private int[] mPrefNwMode = new int[this.mNumPhones];
    private int[] mPreferredStackId = new int[this.mNumPhones];
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            int intExtra;
            int intExtra2;
            int phoneId;
            Message obtainMessage;
            if ("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED".equals(intent.getAction())) {
                if (intent.getBooleanExtra("phoneinECMState", false)) {
                    ModemStackController.this.logd("Device is in ECBM Mode");
                    ModemStackController.this.mIsPhoneInEcbmMode = true;
                    return;
                }
                ModemStackController.this.logd("Device is out of ECBM Mode");
                ModemStackController.this.mIsPhoneInEcbmMode = false;
            } else if ("android.intent.action.ACTION_SUBINFO_CONTENT_CHANGE".equals(intent.getAction())) {
                intExtra = intent.getIntExtra("_id", -1);
                String stringExtra = intent.getStringExtra("columnName");
                intExtra2 = intent.getIntExtra("intContent", 0);
                ModemStackController.this.logd("Received ACTION_SUBINFO_CONTENT_CHANGE on subId: " + intExtra + "for " + stringExtra + " intValue: " + intExtra2);
                if (ModemStackController.this.mDeactivationInProgress && stringExtra != null && stringExtra.equals("sub_state")) {
                    phoneId = SubscriptionController.getInstance().getPhoneId(intExtra);
                    if (intExtra2 == 0 && ((Integer) ModemStackController.this.mSubcriptionStatus.get(Integer.valueOf(phoneId))).intValue() == 1) {
                        obtainMessage = ModemStackController.this.obtainMessage(8, new Integer(phoneId));
                        AsyncResult.forMessage(obtainMessage, SubscriptionStatus.SUB_DEACTIVATED, null);
                        ModemStackController.this.sendMessage(obtainMessage);
                    }
                }
            } else if ("org.codeaurora.intent.action.ACTION_SUBSCRIPTION_SET_UICC_RESULT".equals(intent.getAction())) {
                intExtra = intent.getIntExtra("subscription", -1);
                phoneId = intent.getIntExtra("phone", 0);
                intExtra2 = intent.getIntExtra("operationResult", 1);
                ModemStackController.this.logd("Received ACTION_SUBSCRIPTION_SET_UICC_RESULT on subId: " + intExtra + "phoneId " + phoneId + " status: " + intExtra2);
                if (ModemStackController.this.mDeactivationInProgress && intExtra2 == 1) {
                    obtainMessage = ModemStackController.this.obtainMessage(8, new Integer(phoneId));
                    AsyncResult.forMessage(obtainMessage, SubscriptionStatus.SUB_ACTIVATED, null);
                    ModemStackController.this.sendMessage(obtainMessage);
                }
            }
        }
    };
    private RegistrantList mStackReadyRegistrants = new RegistrantList();
    private int[] mSubState = new int[this.mNumPhones];
    private HashMap<Integer, Integer> mSubcriptionStatus = new HashMap();
    private Message mUpdateStackMsg;

    public class ModemCapabilityInfo {
        private int mMaxDataCap;
        private int mStackId;
        private int mSupportedRatBitMask;
        private int mVoiceDataCap;

        public ModemCapabilityInfo(int i, int i2, int i3, int i4) {
            this.mStackId = i;
            this.mSupportedRatBitMask = i2;
            this.mVoiceDataCap = i3;
            this.mMaxDataCap = i4;
        }

        public int getMaxDataCap() {
            return this.mMaxDataCap;
        }

        public int getStackId() {
            return this.mStackId;
        }

        public int getSupportedRatBitMask() {
            return this.mSupportedRatBitMask;
        }

        public String toString() {
            return "[stack = " + this.mStackId + ", SuppRatBitMask = " + this.mSupportedRatBitMask + ", voiceDataCap = " + this.mVoiceDataCap + ", maxDataCap = " + this.mMaxDataCap + "]";
        }
    }

    public enum SubscriptionStatus {
        SUB_DEACTIVATE,
        SUB_ACTIVATE,
        SUB_ACTIVATED,
        SUB_DEACTIVATED,
        SUB_INVALID
    }

    private ModemStackController(Context context, UiccController uiccController, CommandsInterface[] commandsInterfaceArr) {
        int i;
        logd("Constructor - Enter");
        this.mCi = commandsInterfaceArr;
        this.mContext = context;
        this.mModemCapInfo = new ModemCapabilityInfo[this.mNumPhones];
        for (i = 0; i < this.mCi.length; i++) {
            this.mCi[i].registerForAvailable(this, 9, new Integer(i));
            this.mCi[i].registerForModemCapEvent(this, 10, null);
            this.mCi[i].registerForNotAvailable(this, 11, new Integer(i));
        }
        for (i = 0; i < this.mNumPhones; i++) {
            this.mPreferredStackId[i] = i;
            this.mCurrentStackId[i] = i;
            this.mSubState[i] = 1;
            this.mCmdFailed[i] = false;
        }
        if (this.mNumPhones == 1) {
            this.mIsStackReady = true;
        }
        IntentFilter intentFilter = new IntentFilter("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
        intentFilter.addAction("android.intent.action.ACTION_SUBINFO_CONTENT_CHANGE");
        intentFilter.addAction("org.codeaurora.intent.action.ACTION_SUBSCRIPTION_SET_UICC_RESULT");
        this.mContext.registerReceiver(this.mReceiver, intentFilter);
        logd("Constructor - Exit");
    }

    private boolean areAllModemCapInfoReceived() {
        for (int i = 0; i < this.mNumPhones; i++) {
            if (this.mModemCapInfo[this.mCurrentStackId[i]] == null) {
                return false;
            }
        }
        return true;
    }

    private boolean areAllSubsinSameState(int i) {
        for (int i2 : this.mSubState) {
            logd("areAllSubsinSameState state= " + i + " substate=" + i2);
            if (i2 != i) {
                return false;
            }
        }
        return true;
    }

    private void bindStackOnSub(int i) {
        logd("bindStack " + this.mPreferredStackId[i] + " On phoneId[" + i + "]");
        this.mCi[i].updateStackBinding(this.mPreferredStackId[i], 1, Message.obtain(this, 6, new Integer(i)));
    }

    private void deactivateAllSubscriptions() {
        SubscriptionController instance = SubscriptionController.getInstance();
        List<SubscriptionInfo> activeSubscriptionInfoList = instance.getActiveSubscriptionInfoList();
        this.mActiveSubCount = 0;
        if (activeSubscriptionInfoList == null) {
            if (this.mUpdateStackMsg != null) {
                sendResponseToTarget(this.mUpdateStackMsg, 2);
                this.mUpdateStackMsg = null;
            }
            notifyStackReady(false);
            return;
        }
        for (SubscriptionInfo subscriptionInfo : activeSubscriptionInfoList) {
            int subState = instance.getSubState(subscriptionInfo.getSubscriptionId());
            if (subState == 1) {
                this.mActiveSubCount++;
                instance.deactivateSubId(subscriptionInfo.getSubscriptionId());
            }
            this.mSubcriptionStatus.put(Integer.valueOf(subscriptionInfo.getSimSlotIndex()), Integer.valueOf(subState));
        }
        if (this.mActiveSubCount > 0) {
            this.mDeactivedSubCount = 0;
            this.mDeactivationInProgress = true;
            return;
        }
        this.mDeactivationInProgress = false;
        triggerUnBindingOnAllSubs();
    }

    public static ModemStackController getInstance() {
        if (sModemStackController != null) {
            return sModemStackController;
        }
        throw new RuntimeException("ModemStackController.getInstance called before make()");
    }

    private boolean isAnyCallsInProgress() {
        for (int i = 0; i < this.mNumPhones; i++) {
            if (TelephonyManager.getDefault().getCallState(SubscriptionController.getInstance().getSubIdUsingPhoneId(i)) != 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isAnyCmdFailed() {
        int i = 0;
        boolean z = false;
        while (true) {
            int i2 = i;
            if (i2 >= this.mNumPhones) {
                return z;
            }
            if (this.mCmdFailed[i2]) {
                z = true;
            }
            i = i2 + 1;
        }
    }

    private void logd(String str) {
        Rlog.d(LOG_TAG, str);
    }

    private void loge(String str) {
        Rlog.e(LOG_TAG, str);
    }

    public static ModemStackController make(Context context, UiccController uiccController, CommandsInterface[] commandsInterfaceArr) {
        Rlog.d(LOG_TAG, "getInstance");
        if (sModemStackController == null) {
            sModemStackController = new ModemStackController(context, uiccController, commandsInterfaceArr);
            return sModemStackController;
        }
        throw new RuntimeException("ModemStackController.make() should only be called once");
    }

    private void notifyModemDataCapabilitiesAvailable() {
        logd("notifyGetDataCapabilitiesDone");
        this.mModemDataCapsAvailableRegistrants.notifyRegistrants();
    }

    private void notifyModemRatCapabilitiesAvailable() {
        logd("notifyGetRatCapabilitiesDone: Got RAT capabilities for all Stacks!!!");
        this.mModemRatCapabilitiesAvailable = true;
        this.mModemRatCapsAvailableRegistrants.notifyRegistrants();
    }

    private void notifyStackReady(boolean z) {
        int i = 0;
        logd("notifyStackReady: Stack is READY!!!");
        this.mIsRecoveryInProgress = false;
        this.mIsStackReady = true;
        resetSubStates();
        if (z) {
            while (i < this.mNumPhones) {
                this.mCurrentStackId[i] = this.mPreferredStackId[i];
                i++;
            }
        }
        this.mStackReadyRegistrants.notifyRegistrants();
    }

    private void onBindComplete(AsyncResult asyncResult, int i) {
        if (asyncResult.exception instanceof CommandException) {
            this.mCmdFailed[i] = true;
            loge("onBindComplete(" + i + "): got Exception =" + asyncResult.exception);
        }
        this.mSubState[i] = 5;
        if (!areAllSubsinSameState(5)) {
            return;
        }
        if (isAnyCmdFailed()) {
            recoverToPrevState();
        } else {
            setPrefNwTypeOnAllSubs();
        }
    }

    private void onGetModemCapabilityDone(AsyncResult asyncResult, byte[] bArr, int i) {
        if (bArr == null && (asyncResult.exception instanceof CommandException)) {
            loge("onGetModemCapabilityDone: EXIT!, result null or Exception =" + asyncResult.exception);
            notifyStackReady(false);
            return;
        }
        logd("onGetModemCapabilityDone on phoneId[" + i + "] result = " + bArr);
        if (i < 0 || i >= this.mNumPhones) {
            loge("Invalid Index!!!");
            return;
        }
        this.mSubState[i] = 2;
        parseGetModemCapabilityResponse(bArr, i);
        if (areAllModemCapInfoReceived()) {
            notifyModemRatCapabilitiesAvailable();
        }
    }

    private void onSetPrefNwModeDone(AsyncResult asyncResult, int i) {
        if (asyncResult.exception instanceof CommandException) {
            this.mCmdFailed[i] = true;
            loge("onSetPrefNwModeDone(SUB:" + i + "): got Exception =" + asyncResult.exception);
        }
        this.mSubState[i] = 7;
        if (!areAllSubsinSameState(7)) {
            return;
        }
        if (isAnyCmdFailed()) {
            recoverToPrevState();
            return;
        }
        if (this.mUpdateStackMsg != null) {
            sendResponseToTarget(this.mUpdateStackMsg, 0);
            this.mUpdateStackMsg = null;
        }
        updateNetworkSelectionMode();
        notifyStackReady(true);
    }

    private void onSubDeactivated(AsyncResult asyncResult, int i) {
        SubscriptionStatus subscriptionStatus = (SubscriptionStatus) asyncResult.result;
        if (subscriptionStatus == null || !(subscriptionStatus == null || SubscriptionStatus.SUB_DEACTIVATED == subscriptionStatus)) {
            loge("onSubDeactivated on phoneId[" + i + "] Failed!!!");
            this.mCmdFailed[i] = true;
        }
        logd("onSubDeactivated on phoneId[" + i + "] subStatus = " + subscriptionStatus);
        if (this.mSubState[i] != 3) {
            this.mSubState[i] = 3;
            this.mDeactivedSubCount++;
            if (this.mDeactivedSubCount != this.mActiveSubCount) {
                return;
            }
            if (isAnyCmdFailed()) {
                if (this.mUpdateStackMsg != null) {
                    sendResponseToTarget(this.mUpdateStackMsg, 2);
                    this.mUpdateStackMsg = null;
                }
                notifyStackReady(false);
                return;
            }
            this.mDeactivationInProgress = false;
            triggerUnBindingOnAllSubs();
        }
    }

    private void onUnbindComplete(AsyncResult asyncResult, int i) {
        if (asyncResult.exception instanceof CommandException) {
            this.mCmdFailed[i] = true;
            loge("onUnbindComplete(" + i + "): got Exception =" + asyncResult.exception);
        }
        this.mSubState[i] = 4;
        if (!areAllSubsinSameState(4)) {
            return;
        }
        if (isAnyCmdFailed()) {
            recoverToPrevState();
        } else {
            triggerBindingOnAllSubs();
        }
    }

    private void onUnsolModemCapabilityChanged(AsyncResult asyncResult) {
        logd("onUnsolModemCapabilityChanged");
        UnsolOemHookBuffer unsolOemHookBuffer = (UnsolOemHookBuffer) asyncResult.result;
        if (unsolOemHookBuffer == null && (asyncResult.exception instanceof CommandException)) {
            loge("onUnsolModemCapabilityChanged: EXIT!, result null or Exception =" + asyncResult.exception);
            return;
        }
        byte[] unsolOemHookBuffer2 = unsolOemHookBuffer.getUnsolOemHookBuffer();
        int rilInstance = unsolOemHookBuffer.getRilInstance();
        logd("onUnsolModemCapabilityChanged on phoneId = " + rilInstance);
        parseGetModemCapabilityResponse(unsolOemHookBuffer2, rilInstance);
        notifyModemDataCapabilitiesAvailable();
    }

    private void parseGetModemCapabilityResponse(byte[] bArr, int i) {
        if (bArr.length != 7) {
            loge("parseGetModemCapabilityResponse: EXIT!, result length(" + bArr.length + ") and Expected length(" + 7 + ") not matching.");
            return;
        }
        logd("parseGetModemCapabilityResponse: buffer = " + IccUtils.bytesToHexString(bArr));
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        wrap.order(ByteOrder.nativeOrder());
        byte b = wrap.get();
        if (b < (byte) 0 || b >= this.mNumPhones) {
            loge("Invalid Index!!!");
            return;
        }
        updateModemCapInfo(i, b, wrap.getInt(), wrap.get(), wrap.get());
    }

    private void processRadioAvailable(AsyncResult asyncResult, int i) {
        logd("processRadioAvailable on phoneId = " + i);
        if (i < 0 || i >= this.mNumPhones) {
            loge("Invalid Index!!!");
            return;
        }
        this.mCi[i].getModemCapability(Message.obtain(this, 2, new Integer(i)));
    }

    private void processRadioNotAvailable(AsyncResult asyncResult, int i) {
        logd("processRadioNotAvailable on phoneId = " + i);
        if (i < 0 || i >= this.mNumPhones) {
            loge("Invalid Index!!!");
        } else {
            this.mModemCapInfo[this.mCurrentStackId[i]] = null;
        }
    }

    private void recoverToPrevState() {
        int i = 0;
        if (this.mIsRecoveryInProgress) {
            if (this.mUpdateStackMsg != null) {
                sendResponseToTarget(this.mUpdateStackMsg, 2);
                this.mUpdateStackMsg = null;
            }
            this.mIsRecoveryInProgress = false;
            if (7 == this.mSubState[0]) {
                notifyStackReady(true);
                return;
            }
            return;
        }
        this.mIsRecoveryInProgress = true;
        while (i < this.mNumPhones) {
            this.mPreferredStackId[i] = this.mCurrentStackId[i];
            i++;
        }
        triggerUnBindingOnAllSubs();
    }

    private void resetSubStates() {
        for (int i = 0; i < this.mNumPhones; i++) {
            this.mSubState[i] = 1;
            this.mCmdFailed[i] = false;
        }
    }

    private void sendResponseToTarget(Message message, int i) {
        AsyncResult.forMessage(message, null, CommandException.fromRilErrno(i));
        message.sendToTarget();
    }

    private void setPrefNwTypeOnAllSubs() {
        resetSubStates();
        for (int i = 0; i < this.mNumPhones; i++) {
            this.mCi[i].setPreferredNetworkType(this.mPrefNwMode[i], obtainMessage(7, new Integer(i)));
        }
    }

    private void syncPreferredNwModeFromDB() {
        for (int i = 0; i < this.mNumPhones; i++) {
            try {
                this.mPrefNwMode[i] = TelephonyManager.getIntAtIndex(this.mContext.getContentResolver(), "preferred_network_mode", i);
            } catch (SettingNotFoundException e) {
                loge("getPreferredNetworkMode: Could not find PREFERRED_NETWORK_MODE!!!");
                this.mPrefNwMode[i] = Phone.PREFERRED_NT_MODE;
            }
        }
    }

    private void triggerBindingOnAllSubs() {
        resetSubStates();
        for (int i = 0; i < this.mNumPhones; i++) {
            sendMessage(obtainMessage(5, new Integer(i)));
        }
    }

    private void triggerDeactivationOnAllSubs() {
        resetSubStates();
        sendMessage(obtainMessage(1));
    }

    private void triggerUnBindingOnAllSubs() {
        resetSubStates();
        for (int i = 0; i < this.mNumPhones; i++) {
            sendMessage(obtainMessage(3, new Integer(i)));
        }
    }

    private void unbindStackOnSub(int i) {
        logd("unbindStack " + this.mCurrentStackId[i] + " On phoneId[" + i + "]");
        this.mCi[i].updateStackBinding(this.mCurrentStackId[i], 0, Message.obtain(this, 4, new Integer(i)));
    }

    private void updateModemCapInfo(int i, int i2, int i3, int i4, int i5) {
        this.mCurrentStackId[i] = i2;
        this.mModemCapInfo[this.mCurrentStackId[i]] = new ModemCapabilityInfo(this.mCurrentStackId[i], i3, i4, i5);
        logd("updateModemCapInfo: ModemCaps[" + i + "]" + this.mModemCapInfo[this.mCurrentStackId[i]]);
    }

    private void updateNetworkSelectionMode() {
        for (int i = 0; i < this.mNumPhones; i++) {
            this.mCi[i].setNetworkSelectionModeAutomatic(null);
        }
    }

    public int getCurrentStackIdForPhoneId(int i) {
        return this.mCurrentStackId[i];
    }

    public int getMaxDataAllowed() {
        int i;
        logd("getMaxDataAllowed");
        ArrayList arrayList = new ArrayList();
        for (i = 0; i < this.mNumPhones; i++) {
            if (this.mModemCapInfo[i] != null) {
                arrayList.add(Integer.valueOf(this.mModemCapInfo[i].getMaxDataCap()));
            }
        }
        Collections.sort(arrayList);
        i = arrayList.size();
        return i > 0 ? ((Integer) arrayList.get(i - 1)).intValue() : 1;
    }

    public ModemCapabilityInfo getModemRatCapsForPhoneId(int i) {
        return this.mModemCapInfo[this.mCurrentStackId[i]];
    }

    public int getPrimarySub() {
        for (int i = 0; i < this.mNumPhones; i++) {
            if (getCurrentStackIdForPhoneId(i) == 0) {
                return i;
            }
        }
        return 0;
    }

    public void handleMessage(Message message) {
        AsyncResult asyncResult;
        Integer num;
        Integer num2;
        switch (message.what) {
            case 1:
                logd("CMD_DEACTIVATE_ALL_SUBS");
                deactivateAllSubscriptions();
                return;
            case 2:
                asyncResult = (AsyncResult) message.obj;
                num = (Integer) asyncResult.userObj;
                logd("EVENT_GET_MODEM_CAPS_DONE");
                onGetModemCapabilityDone(asyncResult, (byte[]) asyncResult.result, num.intValue());
                return;
            case 3:
                num2 = (Integer) message.obj;
                logd("CMD_TRIGGER_UNBIND");
                unbindStackOnSub(num2.intValue());
                return;
            case 4:
                asyncResult = (AsyncResult) message.obj;
                num = (Integer) asyncResult.userObj;
                logd("EVENT_UNBIND_DONE");
                onUnbindComplete(asyncResult, num.intValue());
                return;
            case 5:
                num2 = (Integer) message.obj;
                logd("CMD_TRIGGER_BIND");
                bindStackOnSub(num2.intValue());
                return;
            case 6:
                asyncResult = (AsyncResult) message.obj;
                num = (Integer) asyncResult.userObj;
                logd("EVENT_BIND_DONE");
                onBindComplete(asyncResult, num.intValue());
                return;
            case 7:
                asyncResult = (AsyncResult) message.obj;
                num = (Integer) asyncResult.userObj;
                logd("EVENT_SET_PREF_MODE_DONE");
                onSetPrefNwModeDone(asyncResult, num.intValue());
                return;
            case 8:
                asyncResult = (AsyncResult) message.obj;
                num = (Integer) asyncResult.userObj;
                logd("EVENT_SUB_DEACTIVATED");
                onSubDeactivated(asyncResult, num.intValue());
                return;
            case 9:
                asyncResult = (AsyncResult) message.obj;
                num = (Integer) asyncResult.userObj;
                logd("EVENT_RADIO_AVAILABLE");
                processRadioAvailable(asyncResult, num.intValue());
                return;
            case 10:
                asyncResult = (AsyncResult) message.obj;
                logd("EVENT_MODEM_CAPABILITY_CHANGED ar =" + asyncResult);
                onUnsolModemCapabilityChanged(asyncResult);
                return;
            case 11:
                asyncResult = (AsyncResult) message.obj;
                num = (Integer) asyncResult.userObj;
                logd("EVENT_RADIO_NOT_AVAILABLE, phoneId = " + num);
                processRadioNotAvailable(asyncResult, num.intValue());
                return;
            default:
                return;
        }
    }

    public boolean isStackReady() {
        return this.mIsStackReady;
    }

    public void registerForModemDataCapsUpdate(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        synchronized (this.mModemDataCapsAvailableRegistrants) {
            this.mModemDataCapsAvailableRegistrants.add(registrant);
        }
    }

    public void registerForModemRatCapsAvailable(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        if (this.mModemRatCapabilitiesAvailable) {
            registrant.notifyRegistrant();
        }
        synchronized (this.mModemRatCapsAvailableRegistrants) {
            this.mModemRatCapsAvailableRegistrants.add(registrant);
        }
    }

    public void registerForStackReady(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        if (this.mIsStackReady) {
            registrant.notifyRegistrant();
        }
        synchronized (this.mStackReadyRegistrants) {
            this.mStackReadyRegistrants.add(registrant);
        }
    }

    public int updateStackBinding(int[] iArr, boolean z, Message message) {
        boolean isAnyCallsInProgress = isAnyCallsInProgress();
        if (this.mNumPhones == 1) {
            loge("No need to update Stack Binding in case of Single Sim.");
            return 0;
        }
        boolean z2 = SystemProperties.getInt("persist.radio.disable_flexmap", 0) == 1;
        if (isAnyCallsInProgress || this.mIsPhoneInEcbmMode || !(this.mIsStackReady || z)) {
            loge("updateStackBinding: Calls is progress = " + isAnyCallsInProgress + ", mIsPhoneInEcbmMode = " + this.mIsPhoneInEcbmMode + ", mIsStackReady = " + this.mIsStackReady + ". So EXITING!!!");
            return 0;
        }
        int i;
        for (i = 0; i < this.mNumPhones; i++) {
            this.mPreferredStackId[i] = iArr[i];
        }
        for (i = 0; i < this.mNumPhones; i++) {
            if (this.mPreferredStackId[i] != this.mCurrentStackId[i]) {
                i = 1;
                break;
            }
        }
        i = 0;
        if (z2 || r0 == 0) {
            loge("updateStackBinding: FlexMap Disabled : " + z2);
            if (!z) {
                return 0;
            }
            notifyStackReady(false);
            return 0;
        }
        this.mIsStackReady = false;
        this.mUpdateStackMsg = message;
        syncPreferredNwModeFromDB();
        if (z) {
            triggerUnBindingOnAllSubs();
        } else {
            triggerDeactivationOnAllSubs();
        }
        return 1;
    }
}
