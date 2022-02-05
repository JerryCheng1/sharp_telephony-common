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
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.RIL;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccController;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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
    private CommandsInterface[] mCi;
    private Context mContext;
    private boolean mIsStackReady;
    private ModemCapabilityInfo[] mModemCapInfo;
    private Message mUpdateStackMsg;
    private int mNumPhones = TelephonyManager.getDefault().getPhoneCount();
    private int mActiveSubCount = 0;
    private int mDeactivedSubCount = 0;
    private int[] mPreferredStackId = new int[this.mNumPhones];
    private int[] mCurrentStackId = new int[this.mNumPhones];
    private int[] mPrefNwMode = new int[this.mNumPhones];
    private int[] mSubState = new int[this.mNumPhones];
    private boolean mIsRecoveryInProgress = false;
    private boolean mIsPhoneInEcbmMode = false;
    private boolean mModemRatCapabilitiesAvailable = false;
    private boolean mDeactivationInProgress = false;
    private boolean[] mCmdFailed = new boolean[this.mNumPhones];
    private RegistrantList mStackReadyRegistrants = new RegistrantList();
    private RegistrantList mModemRatCapsAvailableRegistrants = new RegistrantList();
    private RegistrantList mModemDataCapsAvailableRegistrants = new RegistrantList();
    private HashMap<Integer, Integer> mSubcriptionStatus = new HashMap<>();
    private BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.internal.telephony.ModemStackController.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if ("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED".equals(intent.getAction())) {
                if (intent.getBooleanExtra("phoneinECMState", false)) {
                    ModemStackController.this.logd("Device is in ECBM Mode");
                    ModemStackController.this.mIsPhoneInEcbmMode = true;
                    return;
                }
                ModemStackController.this.logd("Device is out of ECBM Mode");
                ModemStackController.this.mIsPhoneInEcbmMode = false;
            } else if ("android.intent.action.ACTION_SUBINFO_CONTENT_CHANGE".equals(intent.getAction())) {
                int subId = intent.getIntExtra("_id", -1);
                String column = intent.getStringExtra("columnName");
                int intValue = intent.getIntExtra("intContent", 0);
                ModemStackController.this.logd("Received ACTION_SUBINFO_CONTENT_CHANGE on subId: " + subId + "for " + column + " intValue: " + intValue);
                if (ModemStackController.this.mDeactivationInProgress && column != null && column.equals("sub_state")) {
                    int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
                    if (intValue == 0 && ((Integer) ModemStackController.this.mSubcriptionStatus.get(Integer.valueOf(phoneId))).intValue() == 1) {
                        Message msg = ModemStackController.this.obtainMessage(8, new Integer(phoneId));
                        AsyncResult.forMessage(msg, SubscriptionStatus.SUB_DEACTIVATED, (Throwable) null);
                        ModemStackController.this.sendMessage(msg);
                    }
                }
            } else if ("org.codeaurora.intent.action.ACTION_SUBSCRIPTION_SET_UICC_RESULT".equals(intent.getAction())) {
                int subId2 = intent.getIntExtra("subscription", -1);
                int phoneId2 = intent.getIntExtra("phone", 0);
                int status = intent.getIntExtra("operationResult", 1);
                ModemStackController.this.logd("Received ACTION_SUBSCRIPTION_SET_UICC_RESULT on subId: " + subId2 + "phoneId " + phoneId2 + " status: " + status);
                if (ModemStackController.this.mDeactivationInProgress && status == 1) {
                    Message msg2 = ModemStackController.this.obtainMessage(8, new Integer(phoneId2));
                    AsyncResult.forMessage(msg2, SubscriptionStatus.SUB_ACTIVATED, (Throwable) null);
                    ModemStackController.this.sendMessage(msg2);
                }
            }
        }
    };

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum SubscriptionStatus {
        SUB_DEACTIVATE,
        SUB_ACTIVATE,
        SUB_ACTIVATED,
        SUB_DEACTIVATED,
        SUB_INVALID
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class ModemCapabilityInfo {
        private int mMaxDataCap;
        private int mStackId;
        private int mSupportedRatBitMask;
        private int mVoiceDataCap;

        public ModemCapabilityInfo(int stackId, int supportedRatBitMask, int voiceCap, int dataCap) {
            ModemStackController.this = r1;
            this.mStackId = stackId;
            this.mSupportedRatBitMask = supportedRatBitMask;
            this.mVoiceDataCap = voiceCap;
            this.mMaxDataCap = dataCap;
        }

        public int getSupportedRatBitMask() {
            return this.mSupportedRatBitMask;
        }

        public int getStackId() {
            return this.mStackId;
        }

        public int getMaxDataCap() {
            return this.mMaxDataCap;
        }

        public String toString() {
            return "[stack = " + this.mStackId + ", SuppRatBitMask = " + this.mSupportedRatBitMask + ", voiceDataCap = " + this.mVoiceDataCap + ", maxDataCap = " + this.mMaxDataCap + "]";
        }
    }

    public static ModemStackController make(Context context, UiccController uiccMgr, CommandsInterface[] ci) {
        Rlog.d(LOG_TAG, "getInstance");
        if (sModemStackController == null) {
            sModemStackController = new ModemStackController(context, uiccMgr, ci);
            return sModemStackController;
        }
        throw new RuntimeException("ModemStackController.make() should only be called once");
    }

    public static ModemStackController getInstance() {
        if (sModemStackController != null) {
            return sModemStackController;
        }
        throw new RuntimeException("ModemStackController.getInstance called before make()");
    }

    private ModemStackController(Context context, UiccController uiccManager, CommandsInterface[] ci) {
        this.mIsStackReady = false;
        this.mModemCapInfo = null;
        logd("Constructor - Enter");
        this.mCi = ci;
        this.mContext = context;
        this.mModemCapInfo = new ModemCapabilityInfo[this.mNumPhones];
        for (int i = 0; i < this.mCi.length; i++) {
            this.mCi[i].registerForAvailable(this, 9, new Integer(i));
            this.mCi[i].registerForModemCapEvent(this, 10, null);
            this.mCi[i].registerForNotAvailable(this, 11, new Integer(i));
        }
        for (int i2 = 0; i2 < this.mNumPhones; i2++) {
            this.mPreferredStackId[i2] = i2;
            this.mCurrentStackId[i2] = i2;
            this.mSubState[i2] = 1;
            this.mCmdFailed[i2] = false;
        }
        if (this.mNumPhones == 1) {
            this.mIsStackReady = true;
        }
        IntentFilter filter = new IntentFilter("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
        filter.addAction("android.intent.action.ACTION_SUBINFO_CONTENT_CHANGE");
        filter.addAction("org.codeaurora.intent.action.ACTION_SUBSCRIPTION_SET_UICC_RESULT");
        this.mContext.registerReceiver(this.mReceiver, filter);
        logd("Constructor - Exit");
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                logd("CMD_DEACTIVATE_ALL_SUBS");
                deactivateAllSubscriptions();
                return;
            case 2:
                AsyncResult ar = (AsyncResult) msg.obj;
                logd("EVENT_GET_MODEM_CAPS_DONE");
                onGetModemCapabilityDone(ar, (byte[]) ar.result, ((Integer) ar.userObj).intValue());
                return;
            case 3:
                logd("CMD_TRIGGER_UNBIND");
                unbindStackOnSub(((Integer) msg.obj).intValue());
                return;
            case 4:
                AsyncResult ar2 = (AsyncResult) msg.obj;
                logd("EVENT_UNBIND_DONE");
                onUnbindComplete(ar2, ((Integer) ar2.userObj).intValue());
                return;
            case 5:
                logd("CMD_TRIGGER_BIND");
                bindStackOnSub(((Integer) msg.obj).intValue());
                return;
            case 6:
                AsyncResult ar3 = (AsyncResult) msg.obj;
                logd("EVENT_BIND_DONE");
                onBindComplete(ar3, ((Integer) ar3.userObj).intValue());
                return;
            case 7:
                AsyncResult ar4 = (AsyncResult) msg.obj;
                logd("EVENT_SET_PREF_MODE_DONE");
                onSetPrefNwModeDone(ar4, ((Integer) ar4.userObj).intValue());
                return;
            case 8:
                AsyncResult ar5 = (AsyncResult) msg.obj;
                logd("EVENT_SUB_DEACTIVATED");
                onSubDeactivated(ar5, ((Integer) ar5.userObj).intValue());
                return;
            case 9:
                AsyncResult ar6 = (AsyncResult) msg.obj;
                logd("EVENT_RADIO_AVAILABLE");
                processRadioAvailable(ar6, ((Integer) ar6.userObj).intValue());
                return;
            case 10:
                AsyncResult ar7 = (AsyncResult) msg.obj;
                logd("EVENT_MODEM_CAPABILITY_CHANGED ar =" + ar7);
                onUnsolModemCapabilityChanged(ar7);
                return;
            case 11:
                AsyncResult ar8 = (AsyncResult) msg.obj;
                Integer phoneId = (Integer) ar8.userObj;
                logd("EVENT_RADIO_NOT_AVAILABLE, phoneId = " + phoneId);
                processRadioNotAvailable(ar8, phoneId.intValue());
                return;
            default:
                return;
        }
    }

    private void processRadioAvailable(AsyncResult ar, int phoneId) {
        logd("processRadioAvailable on phoneId = " + phoneId);
        if (phoneId < 0 || phoneId >= this.mNumPhones) {
            loge("Invalid Index!!!");
            return;
        }
        this.mCi[phoneId].getModemCapability(Message.obtain(this, 2, new Integer(phoneId)));
    }

    private void processRadioNotAvailable(AsyncResult ar, int phoneId) {
        logd("processRadioNotAvailable on phoneId = " + phoneId);
        if (phoneId < 0 || phoneId >= this.mNumPhones) {
            loge("Invalid Index!!!");
        } else {
            this.mModemCapInfo[this.mCurrentStackId[phoneId]] = null;
        }
    }

    private void onGetModemCapabilityDone(AsyncResult ar, byte[] result, int phoneId) {
        if (result != null || !(ar.exception instanceof CommandException)) {
            logd("onGetModemCapabilityDone on phoneId[" + phoneId + "] result = " + result);
            if (phoneId < 0 || phoneId >= this.mNumPhones) {
                loge("Invalid Index!!!");
                return;
            }
            this.mSubState[phoneId] = 2;
            parseGetModemCapabilityResponse(result, phoneId);
            if (areAllModemCapInfoReceived()) {
                notifyModemRatCapabilitiesAvailable();
                return;
            }
            return;
        }
        loge("onGetModemCapabilityDone: EXIT!, result null or Exception =" + ar.exception);
        notifyStackReady(false);
    }

    private void onUnsolModemCapabilityChanged(AsyncResult ar) {
        logd("onUnsolModemCapabilityChanged");
        RIL.UnsolOemHookBuffer unsolOemHookBuffer = (RIL.UnsolOemHookBuffer) ar.result;
        if (unsolOemHookBuffer != null || !(ar.exception instanceof CommandException)) {
            int phoneId = unsolOemHookBuffer.getRilInstance();
            logd("onUnsolModemCapabilityChanged on phoneId = " + phoneId);
            parseGetModemCapabilityResponse(unsolOemHookBuffer.getUnsolOemHookBuffer(), phoneId);
            notifyModemDataCapabilitiesAvailable();
            return;
        }
        loge("onUnsolModemCapabilityChanged: EXIT!, result null or Exception =" + ar.exception);
    }

    private void onSubDeactivated(AsyncResult ar, int phoneId) {
        SubscriptionStatus subStatus = (SubscriptionStatus) ar.result;
        if (subStatus == null || !(subStatus == null || SubscriptionStatus.SUB_DEACTIVATED == subStatus)) {
            loge("onSubDeactivated on phoneId[" + phoneId + "] Failed!!!");
            this.mCmdFailed[phoneId] = true;
        }
        logd("onSubDeactivated on phoneId[" + phoneId + "] subStatus = " + subStatus);
        if (this.mSubState[phoneId] != 3) {
            this.mSubState[phoneId] = 3;
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

    private void bindStackOnSub(int phoneId) {
        logd("bindStack " + this.mPreferredStackId[phoneId] + " On phoneId[" + phoneId + "]");
        this.mCi[phoneId].updateStackBinding(this.mPreferredStackId[phoneId], 1, Message.obtain(this, 6, new Integer(phoneId)));
    }

    private void unbindStackOnSub(int phoneId) {
        logd("unbindStack " + this.mCurrentStackId[phoneId] + " On phoneId[" + phoneId + "]");
        this.mCi[phoneId].updateStackBinding(this.mCurrentStackId[phoneId], 0, Message.obtain(this, 4, new Integer(phoneId)));
    }

    private void onUnbindComplete(AsyncResult ar, int phoneId) {
        if (ar.exception instanceof CommandException) {
            this.mCmdFailed[phoneId] = true;
            loge("onUnbindComplete(" + phoneId + "): got Exception =" + ar.exception);
        }
        this.mSubState[phoneId] = 4;
        if (!areAllSubsinSameState(4)) {
            return;
        }
        if (isAnyCmdFailed()) {
            recoverToPrevState();
        } else {
            triggerBindingOnAllSubs();
        }
    }

    private void onBindComplete(AsyncResult ar, int phoneId) {
        if (ar.exception instanceof CommandException) {
            this.mCmdFailed[phoneId] = true;
            loge("onBindComplete(" + phoneId + "): got Exception =" + ar.exception);
        }
        this.mSubState[phoneId] = 5;
        if (!areAllSubsinSameState(5)) {
            return;
        }
        if (isAnyCmdFailed()) {
            recoverToPrevState();
        } else {
            setPrefNwTypeOnAllSubs();
        }
    }

    private void onSetPrefNwModeDone(AsyncResult ar, int phoneId) {
        if (ar.exception instanceof CommandException) {
            this.mCmdFailed[phoneId] = true;
            loge("onSetPrefNwModeDone(SUB:" + phoneId + "): got Exception =" + ar.exception);
        }
        this.mSubState[phoneId] = 7;
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

    private void updateNetworkSelectionMode() {
        for (int i = 0; i < this.mNumPhones; i++) {
            this.mCi[i].setNetworkSelectionModeAutomatic(null);
        }
    }

    private void triggerUnBindingOnAllSubs() {
        resetSubStates();
        for (int i = 0; i < this.mNumPhones; i++) {
            sendMessage(obtainMessage(3, new Integer(i)));
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

    private void setPrefNwTypeOnAllSubs() {
        resetSubStates();
        for (int i = 0; i < this.mNumPhones; i++) {
            this.mCi[i].setPreferredNetworkType(this.mPrefNwMode[i], obtainMessage(7, new Integer(i)));
        }
    }

    private boolean areAllSubsinSameState(int state) {
        int[] arr$ = this.mSubState;
        for (int subState : arr$) {
            logd("areAllSubsinSameState state= " + state + " substate=" + subState);
            if (subState != state) {
                return false;
            }
        }
        return true;
    }

    private boolean areAllModemCapInfoReceived() {
        for (int i = 0; i < this.mNumPhones; i++) {
            if (this.mModemCapInfo[this.mCurrentStackId[i]] == null) {
                return false;
            }
        }
        return true;
    }

    private void resetSubStates() {
        for (int i = 0; i < this.mNumPhones; i++) {
            this.mSubState[i] = 1;
            this.mCmdFailed[i] = false;
        }
    }

    private boolean isAnyCmdFailed() {
        boolean result = false;
        for (int i = 0; i < this.mNumPhones; i++) {
            if (this.mCmdFailed[i]) {
                result = true;
            }
        }
        return result;
    }

    private void updateModemCapInfo(int phoneId, int stackId, int supportedRatBitMask, int voiceDataCap, int maxDataCap) {
        this.mCurrentStackId[phoneId] = stackId;
        this.mModemCapInfo[this.mCurrentStackId[phoneId]] = new ModemCapabilityInfo(this.mCurrentStackId[phoneId], supportedRatBitMask, voiceDataCap, maxDataCap);
        logd("updateModemCapInfo: ModemCaps[" + phoneId + "]" + this.mModemCapInfo[this.mCurrentStackId[phoneId]]);
    }

    private void parseGetModemCapabilityResponse(byte[] result, int phoneId) {
        if (result.length != 7) {
            loge("parseGetModemCapabilityResponse: EXIT!, result length(" + result.length + ") and Expected length(7) not matching.");
            return;
        }
        logd("parseGetModemCapabilityResponse: buffer = " + IccUtils.bytesToHexString(result));
        ByteBuffer respBuffer = ByteBuffer.wrap(result);
        respBuffer.order(ByteOrder.nativeOrder());
        int stackId = respBuffer.get();
        if (stackId < 0 || stackId >= this.mNumPhones) {
            loge("Invalid Index!!!");
        } else {
            updateModemCapInfo(phoneId, stackId, respBuffer.getInt(), respBuffer.get(), respBuffer.get());
        }
    }

    private void syncPreferredNwModeFromDB() {
        for (int i = 0; i < this.mNumPhones; i++) {
            try {
                this.mPrefNwMode[i] = TelephonyManager.getIntAtIndex(this.mContext.getContentResolver(), "preferred_network_mode", i);
            } catch (Settings.SettingNotFoundException e) {
                loge("getPreferredNetworkMode: Could not find PREFERRED_NETWORK_MODE!!!");
                this.mPrefNwMode[i] = Phone.PREFERRED_NT_MODE;
            }
        }
    }

    private boolean isAnyCallsInProgress() {
        for (int i = 0; i < this.mNumPhones; i++) {
            if (TelephonyManager.getDefault().getCallState(SubscriptionController.getInstance().getSubIdUsingPhoneId(i)) != 0) {
                return true;
            }
        }
        return false;
    }

    public boolean isStackReady() {
        return this.mIsStackReady;
    }

    public int getMaxDataAllowed() {
        logd("getMaxDataAllowed");
        List<Integer> unsortedList = new ArrayList<>();
        for (int i = 0; i < this.mNumPhones; i++) {
            if (this.mModemCapInfo[i] != null) {
                unsortedList.add(Integer.valueOf(this.mModemCapInfo[i].getMaxDataCap()));
            }
        }
        Collections.sort(unsortedList);
        int listSize = unsortedList.size();
        if (listSize > 0) {
            return unsortedList.get(listSize - 1).intValue();
        }
        return 1;
    }

    public int getCurrentStackIdForPhoneId(int phoneId) {
        return this.mCurrentStackId[phoneId];
    }

    public int getPrimarySub() {
        for (int i = 0; i < this.mNumPhones; i++) {
            if (getCurrentStackIdForPhoneId(i) == 0) {
                return i;
            }
        }
        return 0;
    }

    public ModemCapabilityInfo getModemRatCapsForPhoneId(int phoneId) {
        return this.mModemCapInfo[this.mCurrentStackId[phoneId]];
    }

    public int updateStackBinding(int[] prefStackIds, boolean isBootUp, Message msg) {
        boolean isFlexmapDisabled;
        boolean isUpdateRequired = false;
        boolean callInProgress = isAnyCallsInProgress();
        if (this.mNumPhones == 1) {
            loge("No need to update Stack Binding in case of Single Sim.");
            return 0;
        }
        if (SystemProperties.getInt("persist.radio.disable_flexmap", 0) == 1) {
            isFlexmapDisabled = true;
        } else {
            isFlexmapDisabled = false;
        }
        if (callInProgress || this.mIsPhoneInEcbmMode || (!this.mIsStackReady && !isBootUp)) {
            loge("updateStackBinding: Calls is progress = " + callInProgress + ", mIsPhoneInEcbmMode = " + this.mIsPhoneInEcbmMode + ", mIsStackReady = " + this.mIsStackReady + ". So EXITING!!!");
            return 0;
        }
        for (int i = 0; i < this.mNumPhones; i++) {
            this.mPreferredStackId[i] = prefStackIds[i];
        }
        int i2 = 0;
        while (true) {
            if (i2 >= this.mNumPhones) {
                break;
            } else if (this.mPreferredStackId[i2] != this.mCurrentStackId[i2]) {
                isUpdateRequired = true;
                break;
            } else {
                i2++;
            }
        }
        if (isFlexmapDisabled || !isUpdateRequired) {
            loge("updateStackBinding: FlexMap Disabled : " + isFlexmapDisabled);
            if (!isBootUp) {
                return 0;
            }
            notifyStackReady(false);
            return 0;
        }
        this.mIsStackReady = false;
        this.mUpdateStackMsg = msg;
        syncPreferredNwModeFromDB();
        if (isBootUp) {
            triggerUnBindingOnAllSubs();
        } else {
            triggerDeactivationOnAllSubs();
        }
        return 1;
    }

    private void deactivateAllSubscriptions() {
        SubscriptionController subCtrlr = SubscriptionController.getInstance();
        List<SubscriptionInfo> subInfoList = subCtrlr.getActiveSubscriptionInfoList();
        this.mActiveSubCount = 0;
        if (subInfoList == null) {
            if (this.mUpdateStackMsg != null) {
                sendResponseToTarget(this.mUpdateStackMsg, 2);
                this.mUpdateStackMsg = null;
            }
            notifyStackReady(false);
            return;
        }
        for (SubscriptionInfo subInfo : subInfoList) {
            int subStatus = subCtrlr.getSubState(subInfo.getSubscriptionId());
            if (subStatus == 1) {
                this.mActiveSubCount++;
                subCtrlr.deactivateSubId(subInfo.getSubscriptionId());
            }
            this.mSubcriptionStatus.put(Integer.valueOf(subInfo.getSimSlotIndex()), Integer.valueOf(subStatus));
        }
        if (this.mActiveSubCount > 0) {
            this.mDeactivedSubCount = 0;
            this.mDeactivationInProgress = true;
            return;
        }
        this.mDeactivationInProgress = false;
        triggerUnBindingOnAllSubs();
    }

    private void notifyStackReady(boolean isCrossMapDone) {
        logd("notifyStackReady: Stack is READY!!!");
        this.mIsRecoveryInProgress = false;
        this.mIsStackReady = true;
        resetSubStates();
        if (isCrossMapDone) {
            for (int i = 0; i < this.mNumPhones; i++) {
                this.mCurrentStackId[i] = this.mPreferredStackId[i];
            }
        }
        this.mStackReadyRegistrants.notifyRegistrants();
    }

    public void registerForStackReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        if (this.mIsStackReady) {
            r.notifyRegistrant();
        }
        synchronized (this.mStackReadyRegistrants) {
            this.mStackReadyRegistrants.add(r);
        }
    }

    private void notifyModemRatCapabilitiesAvailable() {
        logd("notifyGetRatCapabilitiesDone: Got RAT capabilities for all Stacks!!!");
        this.mModemRatCapabilitiesAvailable = true;
        this.mModemRatCapsAvailableRegistrants.notifyRegistrants();
    }

    private void notifyModemDataCapabilitiesAvailable() {
        logd("notifyGetDataCapabilitiesDone");
        this.mModemDataCapsAvailableRegistrants.notifyRegistrants();
    }

    public void registerForModemRatCapsAvailable(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        if (this.mModemRatCapabilitiesAvailable) {
            r.notifyRegistrant();
        }
        synchronized (this.mModemRatCapsAvailableRegistrants) {
            this.mModemRatCapsAvailableRegistrants.add(r);
        }
    }

    public void registerForModemDataCapsUpdate(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        synchronized (this.mModemDataCapsAvailableRegistrants) {
            this.mModemDataCapsAvailableRegistrants.add(r);
        }
    }

    private void recoverToPrevState() {
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
        for (int i = 0; i < this.mNumPhones; i++) {
            this.mPreferredStackId[i] = this.mCurrentStackId[i];
        }
        triggerUnBindingOnAllSubs();
    }

    private void sendResponseToTarget(Message response, int responseCode) {
        AsyncResult.forMessage(response, (Object) null, CommandException.fromRilErrno(responseCode));
        response.sendToTarget();
    }

    public void logd(String string) {
        Rlog.d(LOG_TAG, string);
    }

    private void loge(String string) {
        Rlog.e(LOG_TAG, string);
    }
}
