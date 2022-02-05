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
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import java.util.List;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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
    private static Phone[] mPhone;
    private static CommandsInterface[] sCi;
    private SubscriptionManager mSubscriptionManager;
    private static final int PROJECT_SIM_NUM = TelephonyManager.getDefault().getPhoneCount();
    private static Context mContext = null;
    private static IccCardStatus.CardState[] sCardState = new IccCardStatus.CardState[PROJECT_SIM_NUM];
    private static UiccController mUiccController = null;
    private static IccFileHandler[] mFh = new IccFileHandler[PROJECT_SIM_NUM];
    private static String[] mIccId = new String[PROJECT_SIM_NUM];
    private static int[] mInsertSimState = new int[PROJECT_SIM_NUM];
    private static TelephonyManager mTelephonyMgr = null;
    private static boolean mNeedUpdate = true;
    private boolean isNVSubAvailable = false;
    private final BroadcastReceiver sReceiver = new BroadcastReceiver() { // from class: com.android.internal.telephony.SubscriptionInfoUpdater.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            SubscriptionInfoUpdater.this.logd("[Receiver]+");
            String action = intent.getAction();
            SubscriptionInfoUpdater.this.logd("Action: " + action);
            if (action.equals("android.intent.action.SIM_STATE_CHANGED")) {
                int slotId = intent.getIntExtra("phone", -1);
                SubscriptionInfoUpdater.this.logd("slotId: " + slotId);
                if (slotId != -1) {
                    String simStatus = intent.getStringExtra("ss");
                    SubscriptionInfoUpdater.this.logd("simStatus: " + simStatus);
                    if (action.equals("android.intent.action.SIM_STATE_CHANGED")) {
                        int slotId2 = intent.getIntExtra("slot", -1);
                        SubscriptionInfoUpdater.this.logd("slotId: " + slotId2 + " simStatus: " + simStatus);
                        if (slotId2 == -1) {
                            return;
                        }
                        if ("READY".equals(simStatus) || "LOCKED".equals(simStatus)) {
                            if (((PhoneProxy) SubscriptionInfoUpdater.mPhone[slotId2]).getIccFileHandler() != null) {
                                SubscriptionInfoUpdater.this.sendMessage(SubscriptionInfoUpdater.this.obtainMessage(5, slotId2, -1));
                            }
                        } else if ("LOADED".equals(simStatus)) {
                            SubscriptionInfoUpdater.this.sendMessage(SubscriptionInfoUpdater.this.obtainMessage(3, slotId2, -1));
                        } else if ("ABSENT".equals(simStatus)) {
                            SubscriptionInfoUpdater.this.sendMessage(SubscriptionInfoUpdater.this.obtainMessage(4, slotId2, -1));
                        } else {
                            SubscriptionInfoUpdater.this.logd("Ignoring simStatus: " + simStatus);
                        }
                    }
                    SubscriptionInfoUpdater.this.logd("[Receiver]-");
                }
            }
        }
    };

    public SubscriptionInfoUpdater(Context context, Phone[] phoneProxy, CommandsInterface[] ci) {
        this.mSubscriptionManager = null;
        logd("Constructor invoked");
        mContext = context;
        mPhone = phoneProxy;
        this.mSubscriptionManager = SubscriptionManager.from(mContext);
        sCi = ci;
        SubscriptionHelper.init(context, ci);
        mUiccController = UiccController.getInstance();
        mUiccController.registerForIccChanged(this, 6, null);
        ModemStackController.getInstance().registerForStackReady(this, 7, null);
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            sCardState[i] = IccCardStatus.CardState.CARDSTATE_ABSENT;
        }
        mContext.registerReceiver(this.sReceiver, new IntentFilter("android.intent.action.SIM_STATE_CHANGED"));
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

    public void setDisplayNameForNewSub(String newSubName, int subId, int newNameSource) {
        SubscriptionInfo subInfo = this.mSubscriptionManager.getActiveSubscriptionInfo(subId);
        if (subInfo != null) {
            int oldNameSource = subInfo.getNameSource();
            CharSequence oldSubName = subInfo.getDisplayName();
            logd("[setDisplayNameForNewSub] subId = " + subInfo.getSubscriptionId() + ", oldSimName = " + ((Object) oldSubName) + ", oldNameSource = " + oldNameSource + ", newSubName = " + newSubName + ", newNameSource = " + newNameSource);
            if (oldSubName == null || ((oldNameSource == 0 && newSubName != null) || (oldNameSource == 1 && newSubName != null && !newSubName.equals(oldSubName)))) {
                this.mSubscriptionManager.setDisplayName(newSubName, subInfo.getSubscriptionId(), newNameSource);
                return;
            }
            return;
        }
        logd("SUB" + (subId + 1) + " SubInfo not created yet");
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        switch (msg.what) {
            case 1:
                Integer slotId = (Integer) ar.userObj;
                logd("handleMessage : <EVENT_QUERY_ICCID_DONE> SIM" + (slotId.intValue() + 1));
                if (ar.exception != null) {
                    mIccId[slotId.intValue()] = ICCID_STRING_FOR_NO_SIM;
                    logd("Query IccId fail: " + ar.exception);
                } else if (ar.result != null) {
                    byte[] data = (byte[]) ar.result;
                    mIccId[slotId.intValue()] = IccUtils.bcdToString(data, 0, data.length);
                } else {
                    logd("Null ar");
                    mIccId[slotId.intValue()] = ICCID_STRING_FOR_NO_SIM;
                }
                logd("mIccId[" + slotId + "] = " + mIccId[slotId.intValue()]);
                if (isAllIccIdQueryDone() && mNeedUpdate) {
                    updateSubscriptionInfoByIccId();
                    return;
                }
                return;
            case 2:
                Integer slotId2 = (Integer) ar.userObj;
                if (ar.exception != null || ar.result == null) {
                    logd("EVENT_GET_NETWORK_SELECTION_MODE_DONE: error getting network mode.");
                    return;
                } else if (((int[]) ar.result)[0] == 1) {
                    mPhone[slotId2.intValue()].setNetworkSelectionModeAutomatic(null);
                    return;
                } else {
                    return;
                }
            case 3:
                handleSimLoaded(msg.arg1);
                return;
            case 4:
                handleSimAbsent(msg.arg1);
                return;
            case 5:
                handleSimReadyOrLocked(msg.arg1);
                return;
            case 6:
                new Integer(0);
                if (ar.result != null) {
                    updateIccAvailability(((Integer) ar.result).intValue());
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
                logd("Unknown msg:" + msg.what);
                return;
        }
    }

    private void updateIccAvailability(int slotId) {
        if (mUiccController != null) {
            SubscriptionHelper subHelper = SubscriptionHelper.getInstance();
            logd("updateIccAvailability: Enter, slotId " + slotId);
            if (PROJECT_SIM_NUM <= 1 || subHelper.proceedToHandleIccEvent(slotId)) {
                IccCardStatus.CardState newState = IccCardStatus.CardState.CARDSTATE_ABSENT;
                UiccCard newCard = mUiccController.getUiccCard(slotId);
                if (newCard != null) {
                    newState = newCard.getCardState();
                    if (!newState.isCardPresent() && this.isNVSubAvailable) {
                        Rlog.i(LOG_TAG, "updateIccAvailability: Returning NV mode ");
                        return;
                    }
                }
                IccCardStatus.CardState oldState = sCardState[slotId];
                sCardState[slotId] = newState;
                logd("Slot[" + slotId + "]: New Card State = " + newState + " Old Card State = " + oldState);
                if (!newState.isCardPresent()) {
                    if (mIccId[slotId] != null && !mIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
                        logd("SIM" + (slotId + 1) + " hot plug out");
                        mNeedUpdate = true;
                    }
                    mFh[slotId] = null;
                    mIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
                    if (isAllIccIdQueryDone() && mNeedUpdate) {
                        updateSubscriptionInfoByIccId();
                    }
                } else if (!oldState.isCardPresent() && newState.isCardPresent()) {
                    if (mIccId[slotId] != null && mIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
                        logd("SIM" + (slotId + 1) + " hot plug in");
                        mIccId[slotId] = null;
                        mNeedUpdate = true;
                    }
                    queryIccId(slotId);
                } else if (oldState.isCardPresent() && newState.isCardPresent() && ((!subHelper.isApmSIMNotPwdn() && mIccId[slotId] == null) || (mIccId[slotId] != null && mIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)))) {
                    logd("SIM" + (slotId + 1) + " powered up from APM ");
                    mIccId[slotId] = null;
                    mFh[slotId] = null;
                    mNeedUpdate = true;
                    queryIccId(slotId);
                } else if (oldState.isCardPresent() && newState.isCardPresent() && subHelper.needSubActivationAfterRefresh(slotId)) {
                    logd("SIM" + (slotId + 1) + " refresh happened, need sub activation");
                    if (isAllIccIdQueryDone()) {
                        updateSubscriptionInfoByIccId();
                    }
                }
            } else {
                logd("updateIccAvailability: radio is OFF/unavailable, ignore ");
                if (!subHelper.isApmSIMNotPwdn()) {
                    mIccId[slotId] = null;
                }
            }
        }
    }

    private void handleSimReadyOrLocked(int slotId) {
        if (mIccId[slotId] != null && mIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
            logd("SIM" + (slotId + 1) + " hot plug in");
        }
    }

    private void handleSimLoaded(int slotId) {
        String nameToSet;
        logd("handleSimStateLoadedInternal: slotId: " + slotId);
        IccRecords records = mPhone[slotId].getIccCard().getIccRecords();
        if (records == null) {
            logd("onRecieve: IccRecords null");
        } else if (records.getIccId() == null) {
            logd("onRecieve: IccID null");
        } else {
            mIccId[slotId] = records.getIccId();
            if (mTelephonyMgr == null) {
                mTelephonyMgr = TelephonyManager.from(mContext);
            }
            int subId = Integer.MAX_VALUE;
            int[] subIds = SubscriptionController.getInstance().getSubId(slotId);
            if (subIds != null) {
                subId = subIds[0];
            }
            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                String operator = records.getOperatorNumeric();
                if (operator != null) {
                    if (subId == SubscriptionController.getInstance().getDefaultSubId()) {
                        MccTable.updateMccMncConfiguration(mContext, operator, false);
                    }
                    SubscriptionController.getInstance().setMccMnc(operator, subId);
                } else {
                    logd("EVENT_RECORDS_LOADED Operator name is null");
                }
                String msisdn = TelephonyManager.getDefault().getLine1NumberForSubscriber(subId);
                ContentResolver contentResolver = mContext.getContentResolver();
                if (msisdn != null) {
                    ContentValues number = new ContentValues(1);
                    number.put(IccProvider.STR_NUMBER, msisdn);
                    contentResolver.update(SubscriptionManager.CONTENT_URI, number, "_id=" + Integer.toString(subId), null);
                }
                SubscriptionInfo subInfo = this.mSubscriptionManager.getActiveSubscriptionInfo(subId);
                String simCarrierName = TelephonyManager.getDefault().getSimOperatorNameForSubscription(subId);
                ContentValues name = new ContentValues(1);
                if (subInfo != null && subInfo.getNameSource() != 2) {
                    if (!TextUtils.isEmpty(simCarrierName)) {
                        nameToSet = simCarrierName;
                    } else {
                        nameToSet = "CARD " + Integer.toString(slotId + 1);
                    }
                    name.put("display_name", nameToSet);
                    logd("sim name = " + nameToSet);
                    contentResolver.update(SubscriptionManager.CONTENT_URI, name, "_id=" + Integer.toString(subId), null);
                    return;
                }
                return;
            }
            logd("Invalid subId, could not update ContentResolver");
        }
    }

    private void handleSimAbsent(int slotId) {
        if (mIccId[slotId] != null && !mIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
            logd("SIM" + (slotId + 1) + " hot plug out");
        }
    }

    private void queryIccId(int slotId) {
        logd("queryIccId: slotid=" + slotId);
        if (mFh[slotId] == null) {
            logd("Getting IccFileHandler");
            UiccCardApplication validApp = null;
            UiccCard uiccCard = mUiccController.getUiccCard(slotId);
            int numApps = uiccCard.getNumApplications();
            int i = 0;
            while (true) {
                if (i < numApps) {
                    UiccCardApplication app = uiccCard.getApplicationIndex(i);
                    if (app != null && app.getType() != IccCardApplicationStatus.AppType.APPTYPE_UNKNOWN) {
                        validApp = app;
                        break;
                    }
                    i++;
                } else {
                    break;
                }
            }
            if (validApp != null) {
                mFh[slotId] = validApp.getIccFileHandler();
            }
        }
        if (mFh[slotId] != null) {
            String iccId = mIccId[slotId];
            if (iccId == null) {
                logd("Querying IccId");
                mFh[slotId].loadEFTransparent(IccConstants.EF_ICCID, obtainMessage(1, new Integer(slotId)));
                return;
            }
            logd("NOT Querying IccId its already set sIccid[" + slotId + "]=" + iccId);
            return;
        }
        sCardState[slotId] = IccCardStatus.CardState.CARDSTATE_ABSENT;
        logd("mFh[" + slotId + "] is null, SIM not inserted");
    }

    public void updateSubIdForNV(int slotId) {
        mIccId[slotId] = ICCID_STRING_FOR_NV;
        mNeedUpdate = true;
        logd("[updateSubIdForNV]+ Start");
        if (isAllIccIdQueryDone()) {
            logd("[updateSubIdForNV]+ updating");
            updateSubscriptionInfoByIccId();
            this.isNVSubAvailable = true;
        }
    }

    private synchronized void updateSubscriptionInfoByIccId() {
        logd("updateSubscriptionInfoByIccId:+ Start");
        mNeedUpdate = false;
        this.mSubscriptionManager.clearSubscriptionInfo();
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            mInsertSimState[i] = 0;
        }
        int insertedSimCount = PROJECT_SIM_NUM;
        for (int i2 = 0; i2 < PROJECT_SIM_NUM; i2++) {
            if (ICCID_STRING_FOR_NO_SIM.equals(mIccId[i2])) {
                insertedSimCount--;
                mInsertSimState[i2] = -99;
            }
        }
        logd("insertedSimCount = " + insertedSimCount);
        for (int i3 = 0; i3 < PROJECT_SIM_NUM; i3++) {
            if (mInsertSimState[i3] != -99) {
                int index = 2;
                for (int j = i3 + 1; j < PROJECT_SIM_NUM; j++) {
                    if (mInsertSimState[j] == 0 && mIccId[i3].equals(mIccId[j])) {
                        mInsertSimState[i3] = 1;
                        mInsertSimState[j] = index;
                        index++;
                    }
                }
            }
        }
        ContentResolver contentResolver = mContext.getContentResolver();
        String[] oldIccId = new String[PROJECT_SIM_NUM];
        for (int i4 = 0; i4 < PROJECT_SIM_NUM; i4++) {
            oldIccId[i4] = null;
            List<SubscriptionInfo> oldSubInfo = SubscriptionController.getInstance().getSubInfoUsingSlotIdWithCheck(i4, false);
            if (oldSubInfo != null) {
                oldIccId[i4] = oldSubInfo.get(0).getIccId();
                logd("updateSubscriptionInfoByIccId: oldSubId = " + oldSubInfo.get(0).getSubscriptionId());
                if (mInsertSimState[i4] == 0 && !mIccId[i4].equals(oldIccId[i4])) {
                    mInsertSimState[i4] = -1;
                }
                if (mInsertSimState[i4] != 0) {
                    ContentValues value = new ContentValues(1);
                    value.put("sim_id", (Integer) (-1));
                    contentResolver.update(SubscriptionManager.CONTENT_URI, value, "_id=" + Integer.toString(oldSubInfo.get(0).getSubscriptionId()), null);
                }
            } else {
                if (mInsertSimState[i4] == 0) {
                    mInsertSimState[i4] = -1;
                }
                oldIccId[i4] = ICCID_STRING_FOR_NO_SIM;
                logd("updateSubscriptionInfoByIccId: No SIM in slot " + i4 + " last time");
            }
        }
        for (int i5 = 0; i5 < PROJECT_SIM_NUM; i5++) {
            logd("updateSubscriptionInfoByIccId: oldIccId[" + i5 + "] = " + oldIccId[i5] + ", mIccId[" + i5 + "] = " + mIccId[i5]);
        }
        int nNewCardCount = 0;
        int nNewSimStatus = 0;
        for (int i6 = 0; i6 < PROJECT_SIM_NUM; i6++) {
            if (mInsertSimState[i6] == -99) {
                logd("No SIM inserted in slot " + i6 + " this time");
                if (PROJECT_SIM_NUM == 1) {
                    SubscriptionController.getInstance().updateUserPrefs(false);
                }
            } else {
                if (mInsertSimState[i6] > 0) {
                    this.mSubscriptionManager.addSubscriptionInfoRecord(mIccId[i6] + Integer.toString(mInsertSimState[i6]), i6);
                    logd("SUB" + (i6 + 1) + " has invalid IccId");
                } else {
                    this.mSubscriptionManager.addSubscriptionInfoRecord(mIccId[i6], i6);
                }
                if (isNewSim(mIccId[i6], oldIccId)) {
                    nNewCardCount++;
                    switch (i6) {
                        case 0:
                            nNewSimStatus |= 1;
                            break;
                        case 1:
                            nNewSimStatus |= 2;
                            break;
                        case 2:
                            nNewSimStatus |= 4;
                            break;
                    }
                    mInsertSimState[i6] = -2;
                }
            }
        }
        for (int i7 = 0; i7 < PROJECT_SIM_NUM; i7++) {
            if (mInsertSimState[i7] == -1) {
                mInsertSimState[i7] = -3;
            }
            logd("updateSubscriptionInfoByIccId: mInsertSimState[" + i7 + "] = " + mInsertSimState[i7]);
        }
        SubscriptionHelper.getInstance().updateNwMode();
        if (ModemStackController.getInstance().isStackReady() && PROJECT_SIM_NUM > 1) {
            SubscriptionHelper.getInstance().updateSubActivation(mInsertSimState, false);
        }
        List<SubscriptionInfo> subInfos = this.mSubscriptionManager.getActiveSubscriptionInfoList();
        int nSubCount = subInfos == null ? 0 : subInfos.size();
        logd("updateSubscriptionInfoByIccId: nSubCount = " + nSubCount);
        for (int i8 = 0; i8 < nSubCount; i8++) {
            SubscriptionInfo temp = subInfos.get(i8);
            String msisdn = TelephonyManager.getDefault().getLine1NumberForSubscriber(temp.getSubscriptionId());
            if (msisdn != null) {
                ContentValues value2 = new ContentValues(1);
                value2.put(IccProvider.STR_NUMBER, msisdn);
                contentResolver.update(SubscriptionManager.CONTENT_URI, value2, "_id=" + Integer.toString(temp.getSubscriptionId()), null);
            }
        }
        SubscriptionController.getInstance().notifySubscriptionInfoChanged();
        logd("updateSubscriptionInfoByIccId:- SsubscriptionInfo update complete");
    }

    private boolean isNewSim(String iccId, String[] oldIccId) {
        boolean newSim = true;
        int i = 0;
        while (true) {
            if (i >= PROJECT_SIM_NUM) {
                break;
            } else if (iccId.equals(oldIccId[i])) {
                newSim = false;
                break;
            } else {
                i++;
            }
        }
        logd("newSim = " + newSim);
        return newSim;
    }

    public void dispose() {
        logd("[dispose]");
        mContext.unregisterReceiver(this.sReceiver);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void logd(String message) {
        Rlog.d(LOG_TAG, message);
    }
}
