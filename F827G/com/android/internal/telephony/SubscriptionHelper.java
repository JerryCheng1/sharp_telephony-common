package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.internal.telephony.uicc.IccRefreshResponse;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccController;

/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class SubscriptionHelper extends Handler {
    private static final String APM_SIM_NOT_PWDN_PROPERTY = "persist.radio.apm_sim_not_pwdn";
    private static final int EVENT_REFRESH = 2;
    private static final int EVENT_SET_UICC_SUBSCRIPTION_DONE = 1;
    private static final String LOG_TAG = "SubHelper";
    public static final int SUB_INIT_STATE = -1;
    public static final int SUB_SET_UICC_FAIL = -100;
    public static final int SUB_SET_UICC_SUCCESS = 1;
    public static final int SUB_SIM_NOT_INSERTED = -99;
    private static boolean mNwModeUpdated;
    private static final boolean sApmSIMNotPwdn;
    private static SubscriptionHelper sInstance;
    private static int sNumPhones;
    private static boolean sTriggerDds = false;
    private CommandsInterface[] mCi;
    private Context mContext;
    private final ContentObserver nwModeObserver = new ContentObserver(new Handler()) { // from class: com.android.internal.telephony.SubscriptionHelper.1
        @Override // android.database.ContentObserver
        public void onChange(boolean selfUpdate) {
            SubscriptionHelper.logd("NwMode Observer onChange hit !!!");
            if (SubscriptionHelper.mNwModeUpdated) {
                SubscriptionHelper.this.updateNwModesInSubIdTable(true);
            }
        }
    };
    private int[] mSubStatus = new int[sNumPhones];
    private SetUiccTransaction[] mSetUiccTransaction = new SetUiccTransaction[sNumPhones];

    static {
        boolean z = true;
        if (SystemProperties.getInt(APM_SIM_NOT_PWDN_PROPERTY, 0) != 1) {
            z = false;
        }
        sApmSIMNotPwdn = z;
        mNwModeUpdated = false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class SetUiccTransaction {
        int mApp3gpp2Result;
        int mApp3gppResult;
        int mRequestCount;

        public SetUiccTransaction() {
            resetToDefault();
        }

        void incrementReqCount() {
            this.mRequestCount++;
        }

        void updateAppResult(int appType, int result) {
            this.mRequestCount--;
            if (appType == 2 || appType == 1) {
                this.mApp3gppResult = result;
            } else if (appType == 4 || appType == 3) {
                this.mApp3gpp2Result = result;
            }
        }

        boolean isResponseReceivedForAllApps() {
            return this.mRequestCount == 0;
        }

        int getTransactionResult(int newSubState) {
            if (newSubState == 1 && this.mApp3gppResult == -100 && this.mApp3gpp2Result == -100) {
                return 1;
            }
            if (newSubState != 0) {
                return 0;
            }
            if (this.mApp3gppResult == -100 || this.mApp3gpp2Result == -100) {
                return 1;
            }
            return 0;
        }

        void resetToDefault() {
            this.mApp3gppResult = -1;
            this.mApp3gpp2Result = -1;
            this.mRequestCount = 0;
        }

        public String toString() {
            return "reqCount " + this.mRequestCount + " 3gppApp result " + this.mApp3gppResult + " 3gpp2 app result " + this.mApp3gpp2Result;
        }
    }

    public static SubscriptionHelper init(Context c, CommandsInterface[] ci) {
        SubscriptionHelper subscriptionHelper;
        synchronized (SubscriptionHelper.class) {
            if (sInstance == null) {
                sInstance = new SubscriptionHelper(c, ci);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            subscriptionHelper = sInstance;
        }
        return subscriptionHelper;
    }

    public static SubscriptionHelper getInstance() {
        if (sInstance == null) {
            Log.wtf(LOG_TAG, "getInstance null");
        }
        return sInstance;
    }

    private SubscriptionHelper(Context c, CommandsInterface[] ci) {
        this.mContext = c;
        this.mCi = ci;
        sNumPhones = TelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < sNumPhones; i++) {
            this.mSubStatus[i] = -1;
            this.mCi[i].registerForIccRefresh(this, 2, new Integer(i));
            this.mSetUiccTransaction[i] = new SetUiccTransaction();
        }
        this.mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor("preferred_network_mode"), false, this.nwModeObserver);
        logd("SubscriptionHelper init by Context, num phones = " + sNumPhones + " ApmSIMNotPwdn = " + sApmSIMNotPwdn);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateNwModesInSubIdTable(boolean override) {
        int nwModeInDb;
        SubscriptionController subCtrlr = SubscriptionController.getInstance();
        for (int i = 0; i < sNumPhones; i++) {
            int[] subIdList = subCtrlr.getSubId(i);
            if (subIdList != null && subIdList[0] > 0) {
                try {
                    nwModeInDb = TelephonyManager.getIntAtIndex(this.mContext.getContentResolver(), "preferred_network_mode", i);
                } catch (Settings.SettingNotFoundException e) {
                    loge("Settings Exception Reading Value At Index[" + i + "] Settings.Global.PREFERRED_NETWORK_MODE");
                    nwModeInDb = RILConstants.PREFERRED_NETWORK_MODE;
                }
                int nwModeinSubIdTable = subCtrlr.getNwMode(subIdList[0]);
                logd("updateNwModesInSubIdTable: nwModeinSubIdTable: " + nwModeinSubIdTable + ", nwModeInDb: " + nwModeInDb);
                if (override || nwModeinSubIdTable == -1) {
                    subCtrlr.setNwMode(subIdList[0], nwModeInDb);
                }
            }
        }
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                logd("EVENT_SET_UICC_SUBSCRIPTION_DONE");
                processSetUiccSubscriptionDone(msg);
                return;
            case 2:
                logd("EVENT_REFRESH");
                processSimRefresh((AsyncResult) msg.obj);
                return;
            default:
                return;
        }
    }

    public boolean needSubActivationAfterRefresh(int slotId) {
        return sNumPhones > 1 && this.mSubStatus[slotId] == -1;
    }

    public void updateSubActivation(int[] simStatus, boolean isStackReadyEvent) {
        boolean isPrimarySubFeatureEnable = SystemProperties.getBoolean("persist.radio.primarycard", false);
        SubscriptionController subCtrlr = SubscriptionController.getInstance();
        boolean setUiccSent = false;
        if (isStackReadyEvent && !isPrimarySubFeatureEnable) {
            sTriggerDds = true;
        }
        for (int slotId = 0; slotId < sNumPhones; slotId++) {
            if (simStatus[slotId] == -99) {
                this.mSubStatus[slotId] = simStatus[slotId];
                logd(" Sim not inserted in slot [" + slotId + "] simStatus= " + simStatus[slotId]);
            } else {
                int[] subId = subCtrlr.getSubId(slotId);
                int subState = subCtrlr.getSubState(subId[0]);
                logd("setUicc for [" + slotId + "] = " + subState + "subId = " + subId[0] + " prev subState = " + this.mSubStatus[slotId] + " stackReady " + isStackReadyEvent);
                if (this.mSubStatus[slotId] != subState || isStackReadyEvent) {
                    setUiccSubscription(slotId, subState);
                    setUiccSent = true;
                }
            }
        }
        if (isAllSubsAvailable() && !setUiccSent) {
            logd("Received all sim info, update user pref subs, triggerDds= " + sTriggerDds);
            subCtrlr.updateUserPrefs(sTriggerDds);
            sTriggerDds = false;
        }
    }

    public void updateNwMode() {
    }

    public void setUiccSubscription(int slotId, int subStatus) {
        boolean set3GPPDone = false;
        boolean set3GPP2Done = false;
        UiccCard uiccCard = UiccController.getInstance().getUiccCard(slotId);
        if (uiccCard == null) {
            logd("setUiccSubscription: slotId:" + slotId + " card info not available");
            return;
        }
        for (int i = 0; i < uiccCard.getNumApplications(); i++) {
            int appType = uiccCard.getApplicationIndex(i).getType().ordinal();
            if (!set3GPPDone && (appType == 2 || appType == 1)) {
                this.mSetUiccTransaction[slotId].incrementReqCount();
                this.mCi[slotId].setUiccSubscription(slotId, i, slotId, subStatus, Message.obtain(this, 1, slotId, subStatus, new Integer(appType)));
                set3GPPDone = true;
            } else if (!set3GPP2Done && (appType == 4 || appType == 3)) {
                this.mSetUiccTransaction[slotId].incrementReqCount();
                this.mCi[slotId].setUiccSubscription(slotId, i, slotId, subStatus, Message.obtain(this, 1, slotId, subStatus, new Integer(appType)));
                set3GPP2Done = true;
            }
            if (set3GPPDone && set3GPP2Done) {
                return;
            }
        }
    }

    private void processSetUiccSubscriptionDone(Message msg) {
        SubscriptionController subCtrlr = SubscriptionController.getInstance();
        AsyncResult ar = (AsyncResult) msg.obj;
        int slotId = msg.arg1;
        int newSubState = msg.arg2;
        int[] subId = subCtrlr.getSubIdUsingSlotId(slotId);
        this.mSetUiccTransaction[slotId].updateAppResult(((Integer) ar.userObj).intValue(), ar.exception != null ? -100 : 1);
        if (!this.mSetUiccTransaction[slotId].isResponseReceivedForAllApps()) {
            logi("Waiting for more responses " + this.mSetUiccTransaction[slotId] + " slotId " + slotId);
            return;
        }
        logd(" SubParams info " + this.mSetUiccTransaction[slotId] + " slotId " + slotId);
        if (this.mSetUiccTransaction[slotId].getTransactionResult(newSubState) == 1) {
            loge("Exception in SET_UICC_SUBSCRIPTION, slotId = " + slotId + " newSubState " + newSubState);
            this.mSubStatus[slotId] = -100;
            broadcastSetUiccResult(slotId, newSubState, 1);
            this.mSetUiccTransaction[slotId].resetToDefault();
            return;
        }
        this.mSetUiccTransaction[slotId].resetToDefault();
        if (newSubState != subCtrlr.getSubState(subId[0])) {
            subCtrlr.setSubState(subId[0], newSubState);
        }
        broadcastSetUiccResult(slotId, newSubState, 0);
        this.mSubStatus[slotId] = newSubState;
        if (isAllSubsAvailable()) {
            logd("Received all subs, now update user preferred subs, slotid = " + slotId + " newSubState = " + newSubState + " sTriggerDds = " + sTriggerDds);
            subCtrlr.updateUserPrefs(sTriggerDds);
            sTriggerDds = false;
        }
    }

    private void processSimRefresh(AsyncResult ar) {
        if (ar.exception != null || ar.result == null) {
            loge("processSimRefresh received without input");
            return;
        }
        new Integer(0);
        Integer index = (Integer) ar.userObj;
        IccRefreshResponse state = (IccRefreshResponse) ar.result;
        logi(" Received SIM refresh, reset sub state " + index + " old sub state " + this.mSubStatus[index.intValue()] + " refreshResult = " + state.refreshResult);
        if (state.refreshResult == 2) {
            this.mSubStatus[index.intValue()] = -1;
        }
    }

    private void broadcastSetUiccResult(int slotId, int newSubState, int result) {
        int[] subId = SubscriptionController.getInstance().getSubIdUsingSlotId(slotId);
        Intent intent = new Intent("org.codeaurora.intent.action.ACTION_SUBSCRIPTION_SET_UICC_RESULT");
        intent.addFlags(536870912);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, slotId, subId[0]);
        intent.putExtra("operationResult", result);
        intent.putExtra("newSubState", newSubState);
        this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private boolean isAllSubsAvailable() {
        boolean allSubsAvailable = true;
        for (int i = 0; i < sNumPhones; i++) {
            if (this.mSubStatus[i] == -1) {
                allSubsAvailable = false;
            }
        }
        return allSubsAvailable;
    }

    public boolean isRadioOn(int phoneId) {
        return this.mCi[phoneId].getRadioState().isOn();
    }

    public boolean isRadioAvailable(int phoneId) {
        return this.mCi[phoneId].getRadioState().isAvailable();
    }

    public boolean isApmSIMNotPwdn() {
        return sApmSIMNotPwdn;
    }

    public boolean proceedToHandleIccEvent(int slotId) {
        int apmState = Settings.Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0);
        if (!sApmSIMNotPwdn && (!isRadioOn(slotId) || apmState == 1)) {
            logi(" proceedToHandleIccEvent, radio off/unavailable, slotId = " + slotId);
            this.mSubStatus[slotId] = -1;
        }
        if (apmState == 1 && !sApmSIMNotPwdn) {
            logd(" proceedToHandleIccEvent, sApmSIMNotPwdn = " + sApmSIMNotPwdn);
            return false;
        } else if (isRadioAvailable(slotId)) {
            return true;
        } else {
            logi(" proceedToHandleIccEvent, radio not available, slotId = " + slotId);
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void logd(String message) {
        Rlog.d(LOG_TAG, message);
    }

    private void logi(String msg) {
        Rlog.i(LOG_TAG, msg);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }
}
