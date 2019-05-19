package com.android.internal.telephony;

import android.app.ActivityManagerNative;
import android.app.IUserSwitchObserver;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.IPackageManager;
import android.content.pm.IPackageManager.Stub;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IRemoteCallback;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Telephony.Carriers;
import android.provider.Telephony.Sms.Intents;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class SubscriptionInfoUpdater extends Handler {
    public static final String CURR_SUBID = "curr_subid";
    private static final int EVENT_GET_NETWORK_SELECTION_MODE_DONE = 2;
    private static final int EVENT_SIM_ABSENT = 4;
    private static final int EVENT_SIM_IO_ERROR = 6;
    private static final int EVENT_SIM_LOADED = 3;
    private static final int EVENT_SIM_LOCKED = 5;
    protected static final int EVENT_SIM_LOCKED_QUERY_ICCID_DONE = 1;
    private static final int EVENT_SIM_UNKNOWN = 7;
    private static final String ICCID_STRING_FOR_NO_SIM = "";
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
    protected static String[] mIccId = new String[PROJECT_SIM_NUM];
    private static int[] mInsertSimState = new int[PROJECT_SIM_NUM];
    private static Phone[] mPhone;
    private CarrierServiceBindHelper mCarrierServiceBindHelper;
    private int mCurrentlyActiveUserId;
    private IPackageManager mPackageManager;
    private SubscriptionManager mSubscriptionManager = null;
    private UserManager mUserManager;
    private Map<Integer, Intent> rebroadcastIntentsOnUnlock = new HashMap();
    private final BroadcastReceiver sReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            SubscriptionInfoUpdater.this.logd("[Receiver]+");
            String action = intent.getAction();
            SubscriptionInfoUpdater.this.logd("Action: " + action);
            if (action.equals("android.intent.action.USER_UNLOCKED")) {
                Iterator iterator = SubscriptionInfoUpdater.this.rebroadcastIntentsOnUnlock.entrySet().iterator();
                while (iterator.hasNext()) {
                    Entry pair = (Entry) iterator.next();
                    Intent i = (Intent) pair.getValue();
                    iterator.remove();
                    SubscriptionInfoUpdater.this.logd("Broadcasting intent ACTION_SIM_STATE_CHANGED for mCardIndex: " + pair.getKey());
                    ActivityManagerNative.broadcastStickyIntent(i, "android.permission.READ_PHONE_STATE", -1);
                }
                SubscriptionInfoUpdater.this.rebroadcastIntentsOnUnlock = null;
                SubscriptionInfoUpdater.this.logd("[Receiver]-");
            } else if (action.equals("android.intent.action.SIM_STATE_CHANGED") || action.equals(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED)) {
                int slotId = intent.getIntExtra("phone", -1);
                SubscriptionInfoUpdater.this.logd("slotId: " + slotId);
                if (slotId != -1) {
                    String simStatus = intent.getStringExtra("ss");
                    SubscriptionInfoUpdater.this.logd("simStatus: " + simStatus);
                    if (action.equals("android.intent.action.SIM_STATE_CHANGED")) {
                        if ("ABSENT".equals(simStatus)) {
                            if (!"PERM_DISABLED".equals(intent.getStringExtra(TelephonyEventLog.DATA_KEY_DATA_DEACTIVATE_REASON))) {
                                SubscriptionInfoUpdater.this.sendMessage(SubscriptionInfoUpdater.this.obtainMessage(4, slotId, -1));
                            }
                        } else if ("UNKNOWN".equals(simStatus)) {
                            SubscriptionInfoUpdater.this.sendMessage(SubscriptionInfoUpdater.this.obtainMessage(7, slotId, -1));
                        } else if ("CARD_IO_ERROR".equals(simStatus)) {
                            SubscriptionInfoUpdater.this.sendMessage(SubscriptionInfoUpdater.this.obtainMessage(6, slotId, -1));
                        } else {
                            SubscriptionInfoUpdater.this.logd("Ignoring simStatus: " + simStatus);
                        }
                    } else if (action.equals(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED)) {
                        if ("LOCKED".equals(simStatus) || "ABSENT".equals(simStatus)) {
                            SubscriptionInfoUpdater.this.sendMessage(SubscriptionInfoUpdater.this.obtainMessage(5, slotId, -1, intent.getStringExtra(TelephonyEventLog.DATA_KEY_DATA_DEACTIVATE_REASON)));
                        } else if ("LOADED".equals(simStatus)) {
                            SubscriptionInfoUpdater.this.sendMessage(SubscriptionInfoUpdater.this.obtainMessage(3, slotId, -1));
                        } else {
                            SubscriptionInfoUpdater.this.logd("Ignoring simStatus: " + simStatus);
                        }
                    }
                    SubscriptionInfoUpdater.this.logd("[Receiver]-");
                }
            }
        }
    };

    protected static class QueryIccIdUserObj {
        public String reason;
        public int slotId;

        QueryIccIdUserObj(String reason, int slotId) {
            this.reason = reason;
            this.slotId = slotId;
        }
    }

    public SubscriptionInfoUpdater(Context context, Phone[] phone, CommandsInterface[] ci) {
        logd("Constructor invoked");
        mContext = context;
        mPhone = phone;
        this.mSubscriptionManager = SubscriptionManager.from(mContext);
        this.mPackageManager = Stub.asInterface(ServiceManager.getService(Intents.EXTRA_PACKAGE_NAME));
        this.mUserManager = (UserManager) mContext.getSystemService(Carriers.USER);
        IntentFilter intentFilter = new IntentFilter("android.intent.action.SIM_STATE_CHANGED");
        intentFilter.addAction(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED);
        intentFilter.addAction("android.intent.action.USER_UNLOCKED");
        mContext.registerReceiver(this.sReceiver, intentFilter);
        this.mCarrierServiceBindHelper = new CarrierServiceBindHelper(mContext);
        initializeCarrierApps();
    }

    private void initializeCarrierApps() {
        this.mCurrentlyActiveUserId = 0;
        try {
            ActivityManagerNative.getDefault().registerUserSwitchObserver(new IUserSwitchObserver.Stub() {
                public void onUserSwitching(int newUserId, IRemoteCallback reply) throws RemoteException {
                    SubscriptionInfoUpdater.this.mCurrentlyActiveUserId = newUserId;
                    CarrierAppUtils.disableCarrierAppsUntilPrivileged(SubscriptionInfoUpdater.mContext.getOpPackageName(), SubscriptionInfoUpdater.this.mPackageManager, TelephonyManager.getDefault(), SubscriptionInfoUpdater.this.mCurrentlyActiveUserId);
                    if (reply != null) {
                        try {
                            reply.sendResult(null);
                        } catch (RemoteException e) {
                        }
                    }
                }

                public void onUserSwitchComplete(int newUserId) {
                }

                public void onForegroundProfileSwitch(int newProfileId) throws RemoteException {
                }
            });
            this.mCurrentlyActiveUserId = ActivityManagerNative.getDefault().getCurrentUser().id;
        } catch (RemoteException e) {
            logd("Couldn't get current user ID; guessing it's 0: " + e.getMessage());
        }
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(mContext.getOpPackageName(), this.mPackageManager, TelephonyManager.getDefault(), this.mCurrentlyActiveUserId);
    }

    /* Access modifiers changed, original: protected */
    public boolean isAllIccIdQueryDone() {
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
            logd("[setDisplayNameForNewSub] subId = " + subInfo.getSubscriptionId() + ", oldSimName = " + oldSubName + ", oldNameSource = " + oldNameSource + ", newSubName = " + newSubName + ", newNameSource = " + newNameSource);
            if (oldSubName == null || ((oldNameSource == 0 && newSubName != null) || !(oldNameSource != 1 || newSubName == null || newSubName.equals(oldSubName)))) {
                this.mSubscriptionManager.setDisplayName(newSubName, subInfo.getSubscriptionId(), (long) newNameSource);
                return;
            }
            return;
        }
        logd("SUB" + (subId + 1) + " SubInfo not created yet");
    }

    public void handleMessage(Message msg) {
        AsyncResult ar;
        switch (msg.what) {
            case 1:
                ar = msg.obj;
                QueryIccIdUserObj uObj = ar.userObj;
                int slotId = uObj.slotId;
                logd("handleMessage : <EVENT_SIM_LOCKED_QUERY_ICCID_DONE> SIM" + (slotId + 1));
                if (ar.exception != null) {
                    mIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
                    logd("Query IccId fail: " + ar.exception);
                } else if (ar.result != null) {
                    byte[] data = ar.result;
                    mIccId[slotId] = IccUtils.bcdToString(data, 0, data.length);
                } else {
                    logd("Null ar");
                    mIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
                }
                logd("sIccId[" + slotId + "] = " + mIccId[slotId]);
                if (isAllIccIdQueryDone()) {
                    updateSubscriptionInfoByIccId();
                }
                if ("PERM_DISABLED".equals(uObj.reason)) {
                    broadcastSimStateChanged(slotId, "ABSENT", uObj.reason);
                } else {
                    broadcastSimStateChanged(slotId, "LOCKED", uObj.reason);
                }
                if (!ICCID_STRING_FOR_NO_SIM.equals(mIccId[slotId])) {
                    updateCarrierServices(slotId, "LOCKED");
                    return;
                }
                return;
            case 2:
                ar = (AsyncResult) msg.obj;
                Integer slotId2 = ar.userObj;
                if (ar.exception != null || ar.result == null) {
                    logd("EVENT_GET_NETWORK_SELECTION_MODE_DONE: error getting network mode.");
                    return;
                } else if (ar.result[0] == 1) {
                    mPhone[slotId2.intValue()].setNetworkSelectionModeAutomatic(null);
                    return;
                } else {
                    return;
                }
            case 3:
                handleSimLoaded(msg.arg1);
                return;
            case 4:
                handleSimAbsentOrError(msg.arg1, "ABSENT");
                return;
            case 5:
                handleSimLocked(msg.arg1, (String) msg.obj);
                return;
            case 6:
                handleSimAbsentOrError(msg.arg1, "CARD_IO_ERROR");
                return;
            case 7:
                updateCarrierServices(msg.arg1, "UNKNOWN");
                return;
            default:
                logd("Unknown msg:" + msg.what);
                return;
        }
    }

    /* Access modifiers changed, original: protected */
    public void handleSimLocked(int slotId, String reason) {
        IccFileHandler fileHandler = null;
        if (mIccId[slotId] != null && mIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
            logd("SIM" + (slotId + 1) + " hot plug in");
            mIccId[slotId] = null;
        }
        if (mPhone[slotId].getIccCard() != null) {
            fileHandler = mPhone[slotId].getIccCard().getIccFileHandler();
        }
        if (fileHandler != null) {
            String iccId = mIccId[slotId];
            if (iccId == null) {
                logd("Querying IccId");
                fileHandler.loadEFTransparent(IccConstants.EF_ICCID, obtainMessage(1, new QueryIccIdUserObj(reason, slotId)));
                return;
            }
            logd("NOT Querying IccId its already set sIccid[" + slotId + "]=" + iccId);
            updateCarrierServices(slotId, "LOCKED");
            if ("PERM_DISABLED".equals(reason)) {
                broadcastSimStateChanged(slotId, "ABSENT", reason);
                return;
            } else {
                broadcastSimStateChanged(slotId, "LOCKED", reason);
                return;
            }
        }
        logd("sFh[" + slotId + "] is null, ignore");
    }

    /* Access modifiers changed, original: protected */
    public void handleSimLoaded(int slotId) {
        logd("handleSimStateLoadedInternal: slotId: " + slotId);
        IccRecords records = mPhone[slotId].getIccCard().getIccRecords();
        if (records == null) {
            logd("onRecieve: IccRecords null");
        } else if (records.getIccId() == null) {
            logd("onRecieve: IccID null");
        } else {
            mIccId[slotId] = records.getIccId();
            if (isAllIccIdQueryDone()) {
                updateSubscriptionInfoByIccId();
            }
            int subId = Integer.MAX_VALUE;
            int[] subIds = SubscriptionController.getInstance().getSubId(slotId);
            if (subIds != null) {
                subId = subIds[0];
            }
            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                String operator = mPhone[slotId].getOperatorNumeric();
                if (operator == null || TextUtils.isEmpty(operator)) {
                    logd("EVENT_RECORDS_LOADED Operator name is null");
                } else {
                    if (subId == SubscriptionController.getInstance().getDefaultSubId()) {
                        MccTable.updateMccMncConfiguration(mContext, operator, false);
                    }
                    SubscriptionController.getInstance().setMccMnc(operator, subId);
                }
                TelephonyManager tm = TelephonyManager.getDefault();
                String msisdn = tm.getLine1Number(subId);
                ContentResolver contentResolver = mContext.getContentResolver();
                if (msisdn != null) {
                    SubscriptionController.getInstance().setDisplayNumber(msisdn, subId);
                }
                SubscriptionInfo subInfo = this.mSubscriptionManager.getActiveSubscriptionInfo(subId);
                String simCarrierName = tm.getSimOperatorName(subId);
                if (!(subInfo == null || subInfo.getNameSource() == 2)) {
                    String nameToSet;
                    if (TextUtils.isEmpty(simCarrierName)) {
                        nameToSet = "CARD " + Integer.toString(slotId + 1);
                    } else {
                        nameToSet = simCarrierName;
                    }
                    logd("sim name = " + nameToSet);
                    SubscriptionController.getInstance().setDisplayName(nameToSet, subId);
                }
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
                if (sp.getInt(CURR_SUBID + slotId, -1) != subId) {
                    int networkType = RILConstants.PREFERRED_NETWORK_MODE;
                    try {
                        networkType = TelephonyManager.getIntAtIndex(mContext.getContentResolver(), "preferred_network_mode", slotId);
                    } catch (SettingNotFoundException e) {
                        Rlog.e(LOG_TAG, "Settings Exception Reading Value At Index for Settings.Global.PREFERRED_NETWORK_MODE");
                    }
                    Global.putInt(mPhone[slotId].getContext().getContentResolver(), "preferred_network_mode" + subId, networkType);
                    Editor editor = sp.edit();
                    editor.putInt(CURR_SUBID + slotId, subId);
                    editor.apply();
                }
            } else {
                logd("Invalid subId, could not update ContentResolver");
            }
            CarrierAppUtils.disableCarrierAppsUntilPrivileged(mContext.getOpPackageName(), this.mPackageManager, TelephonyManager.getDefault(), this.mCurrentlyActiveUserId);
            broadcastSimStateChanged(slotId, "LOADED", null);
            updateCarrierServices(slotId, "LOADED");
        }
    }

    private void updateCarrierServices(int slotId, String simState) {
        ((CarrierConfigManager) mContext.getSystemService("carrier_config")).updateConfigForPhoneId(slotId, simState);
        this.mCarrierServiceBindHelper.updateForPhoneId(slotId, simState);
    }

    /* Access modifiers changed, original: protected */
    public void handleSimAbsentOrError(int slotId, String simState) {
        if (!(mIccId[slotId] == null || mIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM))) {
            logd("SIM" + (slotId + 1) + " hot plug out or error");
        }
        mIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
        if (isAllIccIdQueryDone()) {
            updateSubscriptionInfoByIccId();
        }
        updateCarrierServices(slotId, simState);
    }

    /* Access modifiers changed, original: protected|declared_synchronized */
    public synchronized void updateSubscriptionInfoByIccId() {
        int i;
        ContentValues value;
        logd("updateSubscriptionInfoByIccId:+ Start");
        this.mSubscriptionManager.clearSubscriptionInfo();
        for (i = 0; i < PROJECT_SIM_NUM; i++) {
            mInsertSimState[i] = 0;
        }
        int insertedSimCount = PROJECT_SIM_NUM;
        for (i = 0; i < PROJECT_SIM_NUM; i++) {
            if (ICCID_STRING_FOR_NO_SIM.equals(mIccId[i])) {
                insertedSimCount--;
                mInsertSimState[i] = -99;
            }
        }
        logd("insertedSimCount = " + insertedSimCount);
        i = 0;
        while (i < PROJECT_SIM_NUM) {
            if (mInsertSimState[i] != -99) {
                int index = 2;
                int j = i + 1;
                while (j < PROJECT_SIM_NUM) {
                    if (mInsertSimState[j] == 0 && mIccId[i].equals(mIccId[j])) {
                        mInsertSimState[i] = 1;
                        mInsertSimState[j] = index;
                        index++;
                    }
                    j++;
                }
            }
            i++;
        }
        ContentResolver contentResolver = mContext.getContentResolver();
        String[] oldIccId = new String[PROJECT_SIM_NUM];
        i = 0;
        while (i < PROJECT_SIM_NUM) {
            oldIccId[i] = null;
            List<SubscriptionInfo> oldSubInfo = SubscriptionController.getInstance().getSubInfoUsingSlotIdWithCheck(i, false, mContext.getOpPackageName());
            if (oldSubInfo != null) {
                oldIccId[i] = ((SubscriptionInfo) oldSubInfo.get(0)).getIccId();
                logd("updateSubscriptionInfoByIccId: oldSubId = " + ((SubscriptionInfo) oldSubInfo.get(0)).getSubscriptionId());
                if (mInsertSimState[i] == 0 && !mIccId[i].equals(oldIccId[i])) {
                    mInsertSimState[i] = -1;
                }
                if (mInsertSimState[i] != 0) {
                    value = new ContentValues(1);
                    value.put("sim_id", Integer.valueOf(-1));
                    contentResolver.update(SubscriptionManager.CONTENT_URI, value, "_id=" + Integer.toString(((SubscriptionInfo) oldSubInfo.get(0)).getSubscriptionId()), null);
                }
            } else {
                if (mInsertSimState[i] == 0) {
                    mInsertSimState[i] = -1;
                }
                oldIccId[i] = ICCID_STRING_FOR_NO_SIM;
                logd("updateSubscriptionInfoByIccId: No SIM in slot " + i + " last time");
            }
            i++;
        }
        for (i = 0; i < PROJECT_SIM_NUM; i++) {
            logd("updateSubscriptionInfoByIccId: oldIccId[" + i + "] = " + oldIccId[i] + ", sIccId[" + i + "] = " + mIccId[i]);
        }
        int nNewCardCount = 0;
        int nNewSimStatus = 0;
        for (i = 0; i < PROJECT_SIM_NUM; i++) {
            if (mInsertSimState[i] == -99) {
                logd("updateSubscriptionInfoByIccId: No SIM inserted in slot " + i + " this time");
            } else {
                if (mInsertSimState[i] > 0) {
                    this.mSubscriptionManager.addSubscriptionInfoRecord(mIccId[i] + Integer.toString(mInsertSimState[i]), i);
                    logd("SUB" + (i + 1) + " has invalid IccId");
                } else {
                    this.mSubscriptionManager.addSubscriptionInfoRecord(mIccId[i], i);
                }
                if (isNewSim(mIccId[i], oldIccId)) {
                    nNewCardCount++;
                    switch (i) {
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
                    mInsertSimState[i] = -2;
                }
            }
        }
        for (i = 0; i < PROJECT_SIM_NUM; i++) {
            if (mInsertSimState[i] == -1) {
                mInsertSimState[i] = -3;
            }
            logd("updateSubscriptionInfoByIccId: sInsertSimState[" + i + "] = " + mInsertSimState[i]);
        }
        List<SubscriptionInfo> subInfos = this.mSubscriptionManager.getActiveSubscriptionInfoList();
        int nSubCount = subInfos == null ? 0 : subInfos.size();
        logd("updateSubscriptionInfoByIccId: nSubCount = " + nSubCount);
        for (i = 0; i < nSubCount; i++) {
            SubscriptionInfo temp = (SubscriptionInfo) subInfos.get(i);
            String msisdn = TelephonyManager.getDefault().getLine1Number(temp.getSubscriptionId());
            if (msisdn != null) {
                value = new ContentValues(1);
                value.put("number", msisdn);
                contentResolver.update(SubscriptionManager.CONTENT_URI, value, "_id=" + Integer.toString(temp.getSubscriptionId()), null);
            }
        }
        SubscriptionManager subscriptionManager = this.mSubscriptionManager;
        SubscriptionManager subscriptionManager2 = this.mSubscriptionManager;
        subscriptionManager.setDefaultDataSubId(SubscriptionManager.getDefaultDataSubscriptionId());
        SubscriptionController.getInstance().notifySubscriptionInfoChanged();
        logd("updateSubscriptionInfoByIccId:- SsubscriptionInfo update complete");
    }

    private boolean isNewSim(String iccId, String[] oldIccId) {
        boolean newSim = true;
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (iccId.equals(oldIccId[i])) {
                newSim = false;
                break;
            }
        }
        logd("newSim = " + newSim);
        return newSim;
    }

    private void broadcastSimStateChanged(int slotId, String state, String reason) {
        Intent i = new Intent("android.intent.action.SIM_STATE_CHANGED");
        i.addFlags(67108864);
        i.putExtra("phoneName", "Phone");
        i.putExtra("ss", state);
        i.putExtra(TelephonyEventLog.DATA_KEY_DATA_DEACTIVATE_REASON, reason);
        SubscriptionManager.putPhoneIdAndSubIdExtra(i, slotId);
        logd("Broadcasting intent ACTION_SIM_STATE_CHANGED " + state + " reason " + reason + " for mCardIndex: " + slotId);
        ActivityManagerNative.broadcastStickyIntent(i, "android.permission.READ_PHONE_STATE", -1);
        if (!this.mUserManager.isUserUnlocked()) {
            this.rebroadcastIntentsOnUnlock.put(Integer.valueOf(slotId), i);
        }
    }

    public void dispose() {
        logd("[dispose]");
        mContext.unregisterReceiver(this.sReceiver);
    }

    private void logd(String message) {
        Rlog.d(LOG_TAG, message);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("SubscriptionInfoUpdater:");
        this.mCarrierServiceBindHelper.dump(fd, pw, args);
    }
}
