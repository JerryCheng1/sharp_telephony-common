package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import java.util.List;

public class SubscriptionInfoUpdater extends Handler {
    private static final int EVENT_GET_NETWORK_SELECTION_MODE_DONE = 2;
    private static final int EVENT_ICC_CHANGED = 6;
    private static final int EVENT_QUERY_ICCID_DONE = 1;
    private static final int EVENT_SIM_ABSENT = 4;
    private static final int EVENT_SIM_LOADED = 3;
    private static final int EVENT_SIM_READY_OR_LOCKED = 5;
    private static final int EVENT_STACK_READY = 7;
    private static final String ICCID_STRING_FOR_NO_SIM = "";
    private static final String ICCID_STRING_FOR_NV = "DUMMY_NV_ID";
    private static final String LOG_TAG = "SubscriptionInfoUpdater";
    private static final int PROJECT_SIM_NUM = TelephonyManager.getDefault().getPhoneCount();
    public static final int SIM_CHANGED = -1;
    public static final int SIM_NEW = -2;
    public static final int SIM_NOT_CHANGE = 0;
    public static final int SIM_NOT_INSERT = -99;
    public static final int SIM_REPOSITION = -3;
    public static final int STATUS_NO_SIM_INSERTED = 0;
    public static final int STATUS_SIM1_INSERTED = 1;
    public static final int STATUS_SIM2_INSERTED = 2;
    public static final int STATUS_SIM3_INSERTED = 4;
    public static final int STATUS_SIM4_INSERTED = 8;
    private static Context mContext = null;
    private static IccFileHandler[] mFh = new IccFileHandler[PROJECT_SIM_NUM];
    private static String[] mIccId = new String[PROJECT_SIM_NUM];
    private static int[] mInsertSimState = new int[PROJECT_SIM_NUM];
    private static boolean mNeedUpdate = true;
    private static Phone[] mPhone;
    private static TelephonyManager mTelephonyMgr = null;
    private static UiccController mUiccController = null;
    private static CardState[] sCardState = new CardState[PROJECT_SIM_NUM];
    private static CommandsInterface[] sCi;
    private boolean isNVSubAvailable = false;
    private SubscriptionManager mSubscriptionManager = null;
    private final BroadcastReceiver sReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            SubscriptionInfoUpdater.this.logd("[Receiver]+");
            String action = intent.getAction();
            SubscriptionInfoUpdater.this.logd("Action: " + action);
            if (action.equals("android.intent.action.SIM_STATE_CHANGED")) {
                int intExtra = intent.getIntExtra("phone", -1);
                SubscriptionInfoUpdater.this.logd("slotId: " + intExtra);
                if (intExtra != -1) {
                    String stringExtra = intent.getStringExtra("ss");
                    SubscriptionInfoUpdater.this.logd("simStatus: " + stringExtra);
                    if (action.equals("android.intent.action.SIM_STATE_CHANGED")) {
                        int intExtra2 = intent.getIntExtra("slot", -1);
                        SubscriptionInfoUpdater.this.logd("slotId: " + intExtra2 + " simStatus: " + stringExtra);
                        if (intExtra2 == -1) {
                            return;
                        }
                        if ("READY".equals(stringExtra) || "LOCKED".equals(stringExtra)) {
                            if (((PhoneProxy) SubscriptionInfoUpdater.mPhone[intExtra2]).getIccFileHandler() != null) {
                                SubscriptionInfoUpdater.this.sendMessage(SubscriptionInfoUpdater.this.obtainMessage(5, intExtra2, -1));
                            }
                        } else if ("LOADED".equals(stringExtra)) {
                            SubscriptionInfoUpdater.this.sendMessage(SubscriptionInfoUpdater.this.obtainMessage(3, intExtra2, -1));
                        } else if ("ABSENT".equals(stringExtra)) {
                            SubscriptionInfoUpdater.this.sendMessage(SubscriptionInfoUpdater.this.obtainMessage(4, intExtra2, -1));
                        } else {
                            SubscriptionInfoUpdater.this.logd("Ignoring simStatus: " + stringExtra);
                        }
                    }
                    SubscriptionInfoUpdater.this.logd("[Receiver]-");
                }
            }
        }
    };

    public SubscriptionInfoUpdater(Context context, Phone[] phoneArr, CommandsInterface[] commandsInterfaceArr) {
        int i = 0;
        logd("Constructor invoked");
        mContext = context;
        mPhone = phoneArr;
        this.mSubscriptionManager = SubscriptionManager.from(mContext);
        sCi = commandsInterfaceArr;
        SubscriptionHelper.init(context, commandsInterfaceArr);
        mUiccController = UiccController.getInstance();
        mUiccController.registerForIccChanged(this, 6, null);
        ModemStackController.getInstance().registerForStackReady(this, 7, null);
        while (i < PROJECT_SIM_NUM) {
            sCardState[i] = CardState.CARDSTATE_ABSENT;
            i++;
        }
        mContext.registerReceiver(this.sReceiver, new IntentFilter("android.intent.action.SIM_STATE_CHANGED"));
    }

    private void handleSimAbsent(int i) {
        if (mIccId[i] != null && !mIccId[i].equals(ICCID_STRING_FOR_NO_SIM)) {
            logd("SIM" + (i + 1) + " hot plug out");
        }
    }

    private void handleSimLoaded(int i) {
        logd("handleSimStateLoadedInternal: slotId: " + i);
        IccRecords iccRecords = mPhone[i].getIccCard().getIccRecords();
        if (iccRecords == null) {
            logd("onRecieve: IccRecords null");
        } else if (iccRecords.getIccId() == null) {
            logd("onRecieve: IccID null");
        } else {
            mIccId[i] = iccRecords.getIccId();
            if (mTelephonyMgr == null) {
                mTelephonyMgr = TelephonyManager.from(mContext);
            }
            int i2 = Integer.MAX_VALUE;
            int[] subId = SubscriptionController.getInstance().getSubId(i);
            if (subId != null) {
                i2 = subId[0];
            }
            if (SubscriptionManager.isValidSubscriptionId(i2)) {
                String operatorNumeric = iccRecords.getOperatorNumeric();
                if (operatorNumeric != null) {
                    if (i2 == SubscriptionController.getInstance().getDefaultSubId()) {
                        MccTable.updateMccMncConfiguration(mContext, operatorNumeric, false);
                    }
                    SubscriptionController.getInstance().setMccMnc(operatorNumeric, i2);
                } else {
                    logd("EVENT_RECORDS_LOADED Operator name is null");
                }
                operatorNumeric = TelephonyManager.getDefault().getLine1NumberForSubscriber(i2);
                ContentResolver contentResolver = mContext.getContentResolver();
                if (operatorNumeric != null) {
                    ContentValues contentValues = new ContentValues(1);
                    contentValues.put(IccProvider.STR_NUMBER, operatorNumeric);
                    contentResolver.update(SubscriptionManager.CONTENT_URI, contentValues, "_id=" + Integer.toString(i2), null);
                }
                SubscriptionInfo activeSubscriptionInfo = this.mSubscriptionManager.getActiveSubscriptionInfo(i2);
                operatorNumeric = TelephonyManager.getDefault().getSimOperatorNameForSubscription(i2);
                ContentValues contentValues2 = new ContentValues(1);
                if (activeSubscriptionInfo != null && activeSubscriptionInfo.getNameSource() != 2) {
                    if (TextUtils.isEmpty(operatorNumeric)) {
                        operatorNumeric = "CARD " + Integer.toString(i + 1);
                    }
                    contentValues2.put("display_name", operatorNumeric);
                    logd("sim name = " + operatorNumeric);
                    contentResolver.update(SubscriptionManager.CONTENT_URI, contentValues2, "_id=" + Integer.toString(i2), null);
                    return;
                }
                return;
            }
            logd("Invalid subId, could not update ContentResolver");
        }
    }

    private void handleSimReadyOrLocked(int i) {
        if (mIccId[i] != null && mIccId[i].equals(ICCID_STRING_FOR_NO_SIM)) {
            logd("SIM" + (i + 1) + " hot plug in");
        }
    }

    private boolean isAllIccIdQueryDone() {
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (mIccId[i] == null) {
                logd("Wait for SIM" + (i + 1) + " IccId");
                return false;
            }
        }
        logd("All IccIds query complete");
        return true;
    }

    private boolean isNewSim(String str, String[] strArr) {
        boolean z = false;
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (str.equals(strArr[i])) {
                break;
            }
        }
        z = true;
        logd("newSim = " + z);
        return z;
    }

    private void logd(String str) {
        Rlog.d(LOG_TAG, str);
    }

    private void queryIccId(int i) {
        logd("queryIccId: slotid=" + i);
        if (mFh[i] == null) {
            UiccCardApplication applicationIndex;
            logd("Getting IccFileHandler");
            UiccCard uiccCard = mUiccController.getUiccCard(i);
            int numApplications = uiccCard.getNumApplications();
            for (int i2 = 0; i2 < numApplications; i2++) {
                applicationIndex = uiccCard.getApplicationIndex(i2);
                if (applicationIndex != null && applicationIndex.getType() != AppType.APPTYPE_UNKNOWN) {
                    break;
                }
            }
            applicationIndex = null;
            if (applicationIndex != null) {
                mFh[i] = applicationIndex.getIccFileHandler();
            }
        }
        if (mFh[i] != null) {
            String str = mIccId[i];
            if (str == null) {
                logd("Querying IccId");
                mFh[i].loadEFTransparent(IccConstants.EF_ICCID, obtainMessage(1, new Integer(i)));
                return;
            }
            logd("NOT Querying IccId its already set sIccid[" + i + "]=" + str);
            return;
        }
        sCardState[i] = CardState.CARDSTATE_ABSENT;
        logd("mFh[" + i + "] is null, SIM not inserted");
    }

    private void updateIccAvailability(int i) {
        if (mUiccController != null) {
            SubscriptionHelper instance = SubscriptionHelper.getInstance();
            logd("updateIccAvailability: Enter, slotId " + i);
            if (PROJECT_SIM_NUM <= 1 || instance.proceedToHandleIccEvent(i)) {
                Object obj = CardState.CARDSTATE_ABSENT;
                UiccCard uiccCard = mUiccController.getUiccCard(i);
                if (uiccCard != null) {
                    obj = uiccCard.getCardState();
                    if (!obj.isCardPresent() && this.isNVSubAvailable) {
                        Rlog.i(LOG_TAG, "updateIccAvailability: Returning NV mode ");
                        return;
                    }
                }
                Object obj2 = sCardState[i];
                sCardState[i] = obj;
                logd("Slot[" + i + "]: New Card State = " + obj + " " + "Old Card State = " + obj2);
                if (!obj.isCardPresent()) {
                    if (!(mIccId[i] == null || mIccId[i].equals(ICCID_STRING_FOR_NO_SIM))) {
                        logd("SIM" + (i + 1) + " hot plug out");
                        mNeedUpdate = true;
                    }
                    mFh[i] = null;
                    mIccId[i] = ICCID_STRING_FOR_NO_SIM;
                    if (isAllIccIdQueryDone() && mNeedUpdate) {
                        updateSubscriptionInfoByIccId();
                        return;
                    }
                    return;
                } else if (!obj2.isCardPresent() && obj.isCardPresent()) {
                    if (mIccId[i] != null && mIccId[i].equals(ICCID_STRING_FOR_NO_SIM)) {
                        logd("SIM" + (i + 1) + " hot plug in");
                        mIccId[i] = null;
                        mNeedUpdate = true;
                    }
                    queryIccId(i);
                    return;
                } else if (obj2.isCardPresent() && obj.isCardPresent() && ((!instance.isApmSIMNotPwdn() && mIccId[i] == null) || (mIccId[i] != null && mIccId[i].equals(ICCID_STRING_FOR_NO_SIM)))) {
                    logd("SIM" + (i + 1) + " powered up from APM ");
                    mIccId[i] = null;
                    mFh[i] = null;
                    mNeedUpdate = true;
                    queryIccId(i);
                    return;
                } else if (obj2.isCardPresent() && obj.isCardPresent() && instance.needSubActivationAfterRefresh(i)) {
                    logd("SIM" + (i + 1) + " refresh happened, need sub activation");
                    if (isAllIccIdQueryDone()) {
                        updateSubscriptionInfoByIccId();
                        return;
                    }
                    return;
                } else {
                    return;
                }
            }
            logd("updateIccAvailability: radio is OFF/unavailable, ignore ");
            if (!instance.isApmSIMNotPwdn()) {
                mIccId[i] = null;
            }
        }
    }

    private void updateSubscriptionInfoByIccId() {
        synchronized (this) {
            int i;
            int i2;
            ContentValues contentValues;
            logd("updateSubscriptionInfoByIccId:+ Start");
            mNeedUpdate = false;
            this.mSubscriptionManager.clearSubscriptionInfo();
            for (i = 0; i < PROJECT_SIM_NUM; i++) {
                mInsertSimState[i] = 0;
            }
            i = PROJECT_SIM_NUM;
            for (i2 = 0; i2 < PROJECT_SIM_NUM; i2++) {
                if (ICCID_STRING_FOR_NO_SIM.equals(mIccId[i2])) {
                    i--;
                    mInsertSimState[i2] = -99;
                }
            }
            logd("insertedSimCount = " + i);
            int i3 = 0;
            while (i3 < PROJECT_SIM_NUM) {
                if (mInsertSimState[i3] != -99) {
                    i = 2;
                    i2 = i3 + 1;
                    while (i2 < PROJECT_SIM_NUM) {
                        if (mInsertSimState[i2] == 0 && mIccId[i3].equals(mIccId[i2])) {
                            mInsertSimState[i3] = 1;
                            mInsertSimState[i2] = i;
                            i++;
                        }
                        i2++;
                    }
                }
                i3++;
            }
            ContentResolver contentResolver = mContext.getContentResolver();
            String[] strArr = new String[PROJECT_SIM_NUM];
            i2 = 0;
            while (i2 < PROJECT_SIM_NUM) {
                strArr[i2] = null;
                List subInfoUsingSlotIdWithCheck = SubscriptionController.getInstance().getSubInfoUsingSlotIdWithCheck(i2, false);
                if (subInfoUsingSlotIdWithCheck != null) {
                    strArr[i2] = ((SubscriptionInfo) subInfoUsingSlotIdWithCheck.get(0)).getIccId();
                    logd("updateSubscriptionInfoByIccId: oldSubId = " + ((SubscriptionInfo) subInfoUsingSlotIdWithCheck.get(0)).getSubscriptionId());
                    if (mInsertSimState[i2] == 0 && !mIccId[i2].equals(strArr[i2])) {
                        mInsertSimState[i2] = -1;
                    }
                    if (mInsertSimState[i2] != 0) {
                        contentValues = new ContentValues(1);
                        contentValues.put("sim_id", Integer.valueOf(-1));
                        contentResolver.update(SubscriptionManager.CONTENT_URI, contentValues, "_id=" + Integer.toString(((SubscriptionInfo) subInfoUsingSlotIdWithCheck.get(0)).getSubscriptionId()), null);
                    }
                } else {
                    if (mInsertSimState[i2] == 0) {
                        mInsertSimState[i2] = -1;
                    }
                    strArr[i2] = ICCID_STRING_FOR_NO_SIM;
                    logd("updateSubscriptionInfoByIccId: No SIM in slot " + i2 + " last time");
                }
                i2++;
            }
            for (i = 0; i < PROJECT_SIM_NUM; i++) {
                logd("updateSubscriptionInfoByIccId: oldIccId[" + i + "] = " + strArr[i] + ", mIccId[" + i + "] = " + mIccId[i]);
            }
            i2 = 0;
            i = 0;
            for (i3 = 0; i3 < PROJECT_SIM_NUM; i3++) {
                if (mInsertSimState[i3] == -99) {
                    logd("No SIM inserted in slot " + i3 + " this time");
                    if (PROJECT_SIM_NUM == 1) {
                        SubscriptionController.getInstance().updateUserPrefs(false);
                    }
                } else {
                    if (mInsertSimState[i3] > 0) {
                        this.mSubscriptionManager.addSubscriptionInfoRecord(mIccId[i3] + Integer.toString(mInsertSimState[i3]), i3);
                        logd("SUB" + (i3 + 1) + " has invalid IccId");
                    } else {
                        this.mSubscriptionManager.addSubscriptionInfoRecord(mIccId[i3], i3);
                    }
                    if (isNewSim(mIccId[i3], strArr)) {
                        i2++;
                        switch (i3) {
                            case 0:
                                i |= 1;
                                break;
                            case 1:
                                i |= 2;
                                break;
                            case 2:
                                i |= 4;
                                break;
                        }
                        mInsertSimState[i3] = -2;
                    }
                }
            }
            for (i = 0; i < PROJECT_SIM_NUM; i++) {
                if (mInsertSimState[i] == -1) {
                    mInsertSimState[i] = -3;
                }
                logd("updateSubscriptionInfoByIccId: mInsertSimState[" + i + "] = " + mInsertSimState[i]);
            }
            SubscriptionHelper.getInstance().updateNwMode();
            if (ModemStackController.getInstance().isStackReady() && PROJECT_SIM_NUM > 1) {
                SubscriptionHelper.getInstance().updateSubActivation(mInsertSimState, false);
            }
            List activeSubscriptionInfoList = this.mSubscriptionManager.getActiveSubscriptionInfoList();
            i3 = activeSubscriptionInfoList == null ? 0 : activeSubscriptionInfoList.size();
            logd("updateSubscriptionInfoByIccId: nSubCount = " + i3);
            for (i2 = 0; i2 < i3; i2++) {
                SubscriptionInfo subscriptionInfo = (SubscriptionInfo) activeSubscriptionInfoList.get(i2);
                String line1NumberForSubscriber = TelephonyManager.getDefault().getLine1NumberForSubscriber(subscriptionInfo.getSubscriptionId());
                if (line1NumberForSubscriber != null) {
                    contentValues = new ContentValues(1);
                    contentValues.put(IccProvider.STR_NUMBER, line1NumberForSubscriber);
                    contentResolver.update(SubscriptionManager.CONTENT_URI, contentValues, "_id=" + Integer.toString(subscriptionInfo.getSubscriptionId()), null);
                }
            }
            SubscriptionController.getInstance().notifySubscriptionInfoChanged();
            logd("updateSubscriptionInfoByIccId:- SsubscriptionInfo update complete");
        }
    }

    public void dispose() {
        logd("[dispose]");
        mContext.unregisterReceiver(this.sReceiver);
    }

    public void handleMessage(Message message) {
        AsyncResult asyncResult = (AsyncResult) message.obj;
        Integer num;
        switch (message.what) {
            case 1:
                num = (Integer) asyncResult.userObj;
                logd("handleMessage : <EVENT_QUERY_ICCID_DONE> SIM" + (num.intValue() + 1));
                if (asyncResult.exception != null) {
                    mIccId[num.intValue()] = ICCID_STRING_FOR_NO_SIM;
                    logd("Query IccId fail: " + asyncResult.exception);
                } else if (asyncResult.result != null) {
                    byte[] bArr = (byte[]) asyncResult.result;
                    mIccId[num.intValue()] = IccUtils.bcdToString(bArr, 0, bArr.length);
                } else {
                    logd("Null ar");
                    mIccId[num.intValue()] = ICCID_STRING_FOR_NO_SIM;
                }
                logd("mIccId[" + num + "] = " + mIccId[num.intValue()]);
                if (isAllIccIdQueryDone() && mNeedUpdate) {
                    updateSubscriptionInfoByIccId();
                    return;
                }
                return;
            case 2:
                num = (Integer) asyncResult.userObj;
                if (asyncResult.exception != null || asyncResult.result == null) {
                    logd("EVENT_GET_NETWORK_SELECTION_MODE_DONE: error getting network mode.");
                    return;
                } else if (((int[]) asyncResult.result)[0] == 1) {
                    mPhone[num.intValue()].setNetworkSelectionModeAutomatic(null);
                    return;
                } else {
                    return;
                }
            case 3:
                handleSimLoaded(message.arg1);
                return;
            case 4:
                handleSimAbsent(message.arg1);
                return;
            case 5:
                handleSimReadyOrLocked(message.arg1);
                return;
            case 6:
                num = new Integer(0);
                if (asyncResult.result != null) {
                    updateIccAvailability(((Integer) asyncResult.result).intValue());
                    return;
                } else {
                    Rlog.e(LOG_TAG, "Error: Invalid card index EVENT_ICC_CHANGED ");
                    return;
                }
            case 7:
                logd("EVENT_STACK_READY");
                if (isAllIccIdQueryDone() && PROJECT_SIM_NUM > 1) {
                    SubscriptionHelper.getInstance().updateSubActivation(mInsertSimState, true);
                    return;
                }
                return;
            default:
                logd("Unknown msg:" + message.what);
                return;
        }
    }

    public void setDisplayNameForNewSub(String str, int i, int i2) {
        SubscriptionInfo activeSubscriptionInfo = this.mSubscriptionManager.getActiveSubscriptionInfo(i);
        if (activeSubscriptionInfo != null) {
            int nameSource = activeSubscriptionInfo.getNameSource();
            CharSequence displayName = activeSubscriptionInfo.getDisplayName();
            logd("[setDisplayNameForNewSub] subId = " + activeSubscriptionInfo.getSubscriptionId() + ", oldSimName = " + displayName + ", oldNameSource = " + nameSource + ", newSubName = " + str + ", newNameSource = " + i2);
            if (displayName == null || ((nameSource == 0 && str != null) || !(nameSource != 1 || str == null || str.equals(displayName)))) {
                this.mSubscriptionManager.setDisplayName(str, activeSubscriptionInfo.getSubscriptionId(), (long) i2);
                return;
            }
            return;
        }
        logd("SUB" + (i + 1) + " SubInfo not created yet");
    }

    public void updateSubIdForNV(int i) {
        mIccId[i] = ICCID_STRING_FOR_NV;
        mNeedUpdate = true;
        logd("[updateSubIdForNV]+ Start");
        if (isAllIccIdQueryDone()) {
            logd("[updateSubIdForNV]+ updating");
            updateSubscriptionInfoByIccId();
            this.isNVSubAvailable = true;
        }
    }
}
