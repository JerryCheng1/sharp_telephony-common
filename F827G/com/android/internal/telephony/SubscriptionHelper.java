package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.internal.telephony.uicc.IccRefreshResponse;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccController;

class SubscriptionHelper extends Handler {
    private static final String APM_SIM_NOT_PWDN_PROPERTY = "persist.radio.apm_sim_not_pwdn";
    private static final int EVENT_REFRESH = 2;
    private static final int EVENT_SET_UICC_SUBSCRIPTION_DONE = 1;
    private static final String LOG_TAG = "SubHelper";
    public static final int SUB_INIT_STATE = -1;
    public static final int SUB_SET_UICC_FAIL = -100;
    public static final int SUB_SET_UICC_SUCCESS = 1;
    public static final int SUB_SIM_NOT_INSERTED = -99;
    private static boolean mNwModeUpdated = false;
    private static final boolean sApmSIMNotPwdn;
    private static SubscriptionHelper sInstance;
    private static int sNumPhones;
    private static boolean sTriggerDds = false;
    private CommandsInterface[] mCi;
    private Context mContext;
    private SetUiccTransaction[] mSetUiccTransaction;
    private int[] mSubStatus;
    private final ContentObserver nwModeObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean z) {
            SubscriptionHelper.logd("NwMode Observer onChange hit !!!");
            if (SubscriptionHelper.mNwModeUpdated) {
                SubscriptionHelper.this.updateNwModesInSubIdTable(true);
            }
        }
    };

    private class SetUiccTransaction {
        int mApp3gpp2Result;
        int mApp3gppResult;
        int mRequestCount;

        public SetUiccTransaction() {
            resetToDefault();
        }

        /* Access modifiers changed, original: 0000 */
        public int getTransactionResult(int i) {
            return (i == 1 && this.mApp3gppResult == -100 && this.mApp3gpp2Result == -100) ? 1 : (i == 0 && (this.mApp3gppResult == -100 || this.mApp3gpp2Result == -100)) ? 1 : 0;
        }

        /* Access modifiers changed, original: 0000 */
        public void incrementReqCount() {
            this.mRequestCount++;
        }

        /* Access modifiers changed, original: 0000 */
        public boolean isResponseReceivedForAllApps() {
            return this.mRequestCount == 0;
        }

        /* Access modifiers changed, original: 0000 */
        public void resetToDefault() {
            this.mApp3gppResult = -1;
            this.mApp3gpp2Result = -1;
            this.mRequestCount = 0;
        }

        public String toString() {
            return "reqCount " + this.mRequestCount + " 3gppApp result " + this.mApp3gppResult + " 3gpp2 app result " + this.mApp3gpp2Result;
        }

        /* Access modifiers changed, original: 0000 */
        public void updateAppResult(int i, int i2) {
            this.mRequestCount--;
            if (i == 2 || i == 1) {
                this.mApp3gppResult = i2;
            } else if (i == 4 || i == 3) {
                this.mApp3gpp2Result = i2;
            }
        }
    }

    static {
        boolean z = true;
        if (SystemProperties.getInt(APM_SIM_NOT_PWDN_PROPERTY, 0) != 1) {
            z = false;
        }
        sApmSIMNotPwdn = z;
    }

    private SubscriptionHelper(Context context, CommandsInterface[] commandsInterfaceArr) {
        this.mContext = context;
        this.mCi = commandsInterfaceArr;
        sNumPhones = TelephonyManager.getDefault().getPhoneCount();
        this.mSubStatus = new int[sNumPhones];
        this.mSetUiccTransaction = new SetUiccTransaction[sNumPhones];
        for (int i = 0; i < sNumPhones; i++) {
            this.mSubStatus[i] = -1;
            this.mCi[i].registerForIccRefresh(this, 2, new Integer(i));
            this.mSetUiccTransaction[i] = new SetUiccTransaction();
        }
        this.mContext.getContentResolver().registerContentObserver(Global.getUriFor("preferred_network_mode"), false, this.nwModeObserver);
        logd("SubscriptionHelper init by Context, num phones = " + sNumPhones + " ApmSIMNotPwdn = " + sApmSIMNotPwdn);
    }

    private void broadcastSetUiccResult(int i, int i2, int i3) {
        int[] subIdUsingSlotId = SubscriptionController.getInstance().getSubIdUsingSlotId(i);
        Intent intent = new Intent("org.codeaurora.intent.action.ACTION_SUBSCRIPTION_SET_UICC_RESULT");
        intent.addFlags(536870912);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, i, subIdUsingSlotId[0]);
        intent.putExtra("operationResult", i3);
        intent.putExtra("newSubState", i2);
        this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    public static SubscriptionHelper getInstance() {
        if (sInstance == null) {
            Log.wtf(LOG_TAG, "getInstance null");
        }
        return sInstance;
    }

    public static SubscriptionHelper init(Context context, CommandsInterface[] commandsInterfaceArr) {
        SubscriptionHelper subscriptionHelper;
        synchronized (SubscriptionHelper.class) {
            try {
                if (sInstance == null) {
                    sInstance = new SubscriptionHelper(context, commandsInterfaceArr);
                } else {
                    Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
                }
                subscriptionHelper = sInstance;
            } catch (Throwable th) {
                Class cls = SubscriptionHelper.class;
            }
        }
        return subscriptionHelper;
    }

    private boolean isAllSubsAvailable() {
        boolean z = true;
        for (int i = 0; i < sNumPhones; i++) {
            if (this.mSubStatus[i] == -1) {
                z = false;
            }
        }
        return z;
    }

    private static void logd(String str) {
        Rlog.d(LOG_TAG, str);
    }

    private void loge(String str) {
        Rlog.e(LOG_TAG, str);
    }

    private void logi(String str) {
        Rlog.i(LOG_TAG, str);
    }

    private void processSetUiccSubscriptionDone(Message message) {
        SubscriptionController instance = SubscriptionController.getInstance();
        AsyncResult asyncResult = (AsyncResult) message.obj;
        int i = message.arg1;
        int i2 = message.arg2;
        int[] subIdUsingSlotId = instance.getSubIdUsingSlotId(i);
        this.mSetUiccTransaction[i].updateAppResult(((Integer) asyncResult.userObj).intValue(), asyncResult.exception != null ? -100 : 1);
        if (this.mSetUiccTransaction[i].isResponseReceivedForAllApps()) {
            logd(" SubParams info " + this.mSetUiccTransaction[i] + " slotId " + i);
            if (this.mSetUiccTransaction[i].getTransactionResult(i2) == 1) {
                loge("Exception in SET_UICC_SUBSCRIPTION, slotId = " + i + " newSubState " + i2);
                this.mSubStatus[i] = -100;
                broadcastSetUiccResult(i, i2, 1);
                this.mSetUiccTransaction[i].resetToDefault();
                return;
            }
            this.mSetUiccTransaction[i].resetToDefault();
            if (i2 != instance.getSubState(subIdUsingSlotId[0])) {
                instance.setSubState(subIdUsingSlotId[0], i2);
            }
            broadcastSetUiccResult(i, i2, 0);
            this.mSubStatus[i] = i2;
            if (isAllSubsAvailable()) {
                logd("Received all subs, now update user preferred subs, slotid = " + i + " newSubState = " + i2 + " sTriggerDds = " + sTriggerDds);
                instance.updateUserPrefs(sTriggerDds);
                sTriggerDds = false;
                return;
            }
            return;
        }
        logi("Waiting for more responses " + this.mSetUiccTransaction[i] + " slotId " + i);
    }

    private void processSimRefresh(AsyncResult asyncResult) {
        if (asyncResult.exception != null || asyncResult.result == null) {
            loge("processSimRefresh received without input");
            return;
        }
        Integer num = new Integer(0);
        num = (Integer) asyncResult.userObj;
        IccRefreshResponse iccRefreshResponse = (IccRefreshResponse) asyncResult.result;
        logi(" Received SIM refresh, reset sub state " + num + " old sub state " + this.mSubStatus[num.intValue()] + " refreshResult = " + iccRefreshResponse.refreshResult);
        if (iccRefreshResponse.refreshResult == 2) {
            this.mSubStatus[num.intValue()] = -1;
        }
    }

    private void updateNwModesInSubIdTable(boolean z) {
        SubscriptionController instance = SubscriptionController.getInstance();
        for (int i = 0; i < sNumPhones; i++) {
            int[] subId = instance.getSubId(i);
            if (subId != null && subId[0] > 0) {
                int intAtIndex;
                try {
                    intAtIndex = TelephonyManager.getIntAtIndex(this.mContext.getContentResolver(), "preferred_network_mode", i);
                } catch (SettingNotFoundException e) {
                    loge("Settings Exception Reading Value At Index[" + i + "] Settings.Global.PREFERRED_NETWORK_MODE");
                    intAtIndex = RILConstants.PREFERRED_NETWORK_MODE;
                }
                int nwMode = instance.getNwMode(subId[0]);
                logd("updateNwModesInSubIdTable: nwModeinSubIdTable: " + nwMode + ", nwModeInDb: " + intAtIndex);
                if (z || nwMode == -1) {
                    instance.setNwMode(subId[0], intAtIndex);
                }
            }
        }
    }

    public void handleMessage(Message message) {
        switch (message.what) {
            case 1:
                logd("EVENT_SET_UICC_SUBSCRIPTION_DONE");
                processSetUiccSubscriptionDone(message);
                return;
            case 2:
                logd("EVENT_REFRESH");
                processSimRefresh((AsyncResult) message.obj);
                return;
            default:
                return;
        }
    }

    public boolean isApmSIMNotPwdn() {
        return sApmSIMNotPwdn;
    }

    public boolean isRadioAvailable(int i) {
        return this.mCi[i].getRadioState().isAvailable();
    }

    public boolean isRadioOn(int i) {
        return this.mCi[i].getRadioState().isOn();
    }

    public boolean needSubActivationAfterRefresh(int i) {
        return sNumPhones > 1 && this.mSubStatus[i] == -1;
    }

    public boolean proceedToHandleIccEvent(int i) {
        int i2 = Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0);
        if (!sApmSIMNotPwdn && (!isRadioOn(i) || i2 == 1)) {
            logi(" proceedToHandleIccEvent, radio off/unavailable, slotId = " + i);
            this.mSubStatus[i] = -1;
        }
        if (i2 == 1 && !sApmSIMNotPwdn) {
            logd(" proceedToHandleIccEvent, sApmSIMNotPwdn = " + sApmSIMNotPwdn);
            return false;
        } else if (isRadioAvailable(i)) {
            return true;
        } else {
            logi(" proceedToHandleIccEvent, radio not available, slotId = " + i);
            return false;
        }
    }

    public void setUiccSubscription(int i, int i2) {
        UiccCard uiccCard = UiccController.getInstance().getUiccCard(i);
        if (uiccCard == null) {
            logd("setUiccSubscription: slotId:" + i + " card info not available");
            return;
        }
        int i3 = 0;
        int i4 = 0;
        int i5 = 0;
        while (i4 < uiccCard.getNumApplications()) {
            int i6;
            int ordinal = uiccCard.getApplicationIndex(i4).getType().ordinal();
            if (i5 == 0 && (ordinal == 2 || ordinal == 1)) {
                this.mSetUiccTransaction[i].incrementReqCount();
                this.mCi[i].setUiccSubscription(i, i4, i, i2, Message.obtain(this, 1, i, i2, new Integer(ordinal)));
                i6 = 1;
                ordinal = i3;
            } else if (i3 == 0 && (ordinal == 4 || ordinal == 3)) {
                this.mSetUiccTransaction[i].incrementReqCount();
                this.mCi[i].setUiccSubscription(i, i4, i, i2, Message.obtain(this, 1, i, i2, new Integer(ordinal)));
                i6 = i5;
                ordinal = 1;
            } else {
                i6 = i5;
                ordinal = i3;
            }
            if (i6 == 0 || ordinal == 0) {
                i4++;
                i3 = ordinal;
                i5 = i6;
            } else {
                return;
            }
        }
    }

    public void updateNwMode() {
    }

    public void updateSubActivation(int[] iArr, boolean z) {
        boolean z2 = SystemProperties.getBoolean("persist.radio.primarycard", false);
        SubscriptionController instance = SubscriptionController.getInstance();
        if (z && !z2) {
            sTriggerDds = true;
        }
        z2 = false;
        for (int i = 0; i < sNumPhones; i++) {
            if (iArr[i] == -99) {
                this.mSubStatus[i] = iArr[i];
                logd(" Sim not inserted in slot [" + i + "] simStatus= " + iArr[i]);
            } else {
                int[] subId = instance.getSubId(i);
                int subState = instance.getSubState(subId[0]);
                logd("setUicc for [" + i + "] = " + subState + "subId = " + subId[0] + " prev subState = " + this.mSubStatus[i] + " stackReady " + z);
                if (this.mSubStatus[i] != subState || z) {
                    setUiccSubscription(i, subState);
                    z2 = true;
                }
            }
        }
        if (isAllSubsAvailable() && !r0) {
            logd("Received all sim info, update user pref subs, triggerDds= " + sTriggerDds);
            instance.updateUserPrefs(sTriggerDds);
            sTriggerDds = false;
        }
    }
}
