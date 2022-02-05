package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.NetworkRequest;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.Log;
import com.android.internal.telephony.ISub;
import com.android.internal.telephony.ITelephonyRegistry;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.dataconnection.DctController;
import com.android.internal.telephony.dataconnection.DdsScheduler;
import com.android.internal.telephony.dataconnection.DdsSchedulerAc;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class SubscriptionController extends ISub.Stub {
    static final boolean DBG = true;
    private static final int EVENT_SET_DEFAULT_DATA_DONE = 1;
    static final String LOG_TAG = "SubscriptionController";
    static final int MAX_LOCAL_LOG_LINES = 500;
    static final boolean VDBG = false;
    protected static PhoneProxy[] sProxyPhones;
    private int[] colorArr;
    protected Context mContext;
    private DataConnectionHandler mDataConnectionHandler;
    private DctController mDctController;
    private DdsScheduler mScheduler;
    private DdsSchedulerAc mSchedulerAc;
    protected TelephonyManager mTelephonyManager;
    private static SubscriptionController sInstance = null;
    private static HashMap<Integer, Integer> mSlotIdxToSubId = new HashMap<>();
    private static final int DUMMY_SUB_ID_BASE = 2147483643;
    private static int mDefaultFallbackSubId = DUMMY_SUB_ID_BASE;
    private static int mDefaultPhoneId = 0;
    private ScLocalLog mLocalLog = new ScLocalLog(MAX_LOCAL_LOG_LINES);
    protected final Object mLock = new Object();
    private HashMap<Integer, OnDemandDdsLockNotifier> mOnDemandDdsLockNotificationRegistrants = new HashMap<>();
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.internal.telephony.SubscriptionController.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            SubscriptionController.this.logd("onReceive " + intent);
            int subId = intent.getIntExtra("subscription", -1);
            if (!intent.getAction().equals("android.provider.Telephony.SPN_STRINGS_UPDATED")) {
                return;
            }
            if (intent.getBooleanExtra("showPlmn", false)) {
                String carrierText = intent.getStringExtra(Telephony.CellBroadcasts.PLMN);
                if (intent.getBooleanExtra("showSpn", false)) {
                    carrierText = carrierText + SubscriptionController.this.mContext.getString(17041030).toString() + intent.getStringExtra("spn");
                }
                SubscriptionController.this.setCarrierText(carrierText, subId);
            } else if (intent.getBooleanExtra("showSpn", false)) {
                SubscriptionController.this.setCarrierText(intent.getStringExtra(Telephony.CellBroadcasts.PLMN), subId);
            }
        }
    };
    protected CallManager mCM = CallManager.getInstance();

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public interface OnDemandDdsLockNotifier {
        void notifyOnDemandDdsLockGranted(NetworkRequest networkRequest);
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class ScLocalLog {
        private int mMaxLines;
        private LinkedList<String> mLog = new LinkedList<>();
        private Time mNow = new Time();

        public ScLocalLog(int maxLines) {
            this.mMaxLines = maxLines;
        }

        public synchronized void log(String msg) {
            if (this.mMaxLines > 0) {
                int pid = Process.myPid();
                int tid = Process.myTid();
                this.mNow.setToNow();
                this.mLog.add(this.mNow.format("%m-%d %H:%M:%S") + " pid=" + pid + " tid=" + tid + " " + msg);
                while (this.mLog.size() > this.mMaxLines) {
                    this.mLog.remove();
                }
            }
        }

        public synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            Iterator<String> itr = this.mLog.listIterator(0);
            int i = 0;
            while (itr.hasNext()) {
                i++;
                pw.println(Integer.toString(i) + ": " + itr.next());
                if (i % 10 == 0) {
                    pw.flush();
                }
            }
        }
    }

    public static SubscriptionController init(Phone phone) {
        SubscriptionController subscriptionController;
        synchronized (SubscriptionController.class) {
            if (sInstance == null) {
                sInstance = new SubscriptionController(phone);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            subscriptionController = sInstance;
        }
        return subscriptionController;
    }

    public static SubscriptionController init(Context c, CommandsInterface[] ci) {
        SubscriptionController subscriptionController;
        synchronized (SubscriptionController.class) {
            if (sInstance == null) {
                sInstance = new SubscriptionController(c);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            subscriptionController = sInstance;
        }
        return subscriptionController;
    }

    public static SubscriptionController getInstance() {
        if (sInstance == null) {
            Log.wtf(LOG_TAG, "getInstance null");
        }
        return sInstance;
    }

    /* JADX WARN: Multi-variable type inference failed */
    private SubscriptionController(Context c) {
        logd("SubscriptionController init by Context");
        this.mContext = c;
        this.mTelephonyManager = TelephonyManager.from(this.mContext);
        if (ServiceManager.getService("isub") == null) {
            ServiceManager.addService("isub", this);
        }
        registerReceiverIfNeeded();
        logdl("[SubscriptionController] init by Context");
        this.mDataConnectionHandler = new DataConnectionHandler();
        this.mScheduler = DdsScheduler.getInstance();
        this.mSchedulerAc = new DdsSchedulerAc();
        this.mSchedulerAc.connect(this.mContext, this.mDataConnectionHandler, this.mScheduler.getHandler());
    }

    public int getSubIdFromNetworkRequest(NetworkRequest n) {
        if (n == null) {
            return getDefaultDataSubId();
        }
        try {
            return Integer.parseInt(n.networkCapabilities.getNetworkSpecifier());
        } catch (NumberFormatException e) {
            loge("Exception e = " + e);
            return getDefaultDataSubId();
        }
    }

    public void startOnDemandDataSubscriptionRequest(NetworkRequest n) {
        logd("startOnDemandDataSubscriptionRequest = " + n);
        this.mSchedulerAc.allocateDds(n);
    }

    public void stopOnDemandDataSubscriptionRequest(NetworkRequest n) {
        logd("stopOnDemandDataSubscriptionRequest = " + n);
        this.mSchedulerAc.freeDds(n);
    }

    private boolean isSubInfoReady() {
        return mSlotIdxToSubId.size() > 0;
    }

    /* JADX WARN: Multi-variable type inference failed */
    private SubscriptionController(Phone phone) {
        this.mContext = phone.getContext();
        if (ServiceManager.getService("isub") == null) {
            ServiceManager.addService("isub", this);
        }
        registerReceiverIfNeeded();
        logdl("[SubscriptionController] init by Phone");
    }

    private void registerReceiverIfNeeded() {
        if (this.mContext.getPackageManager().resolveContentProvider(SubscriptionManager.CONTENT_URI.getAuthority(), 0) != null) {
            logd("registering SPN updated receiver");
            this.mContext.registerReceiver(this.mReceiver, new IntentFilter("android.provider.Telephony.SPN_STRINGS_UPDATED"));
        }
    }

    private void enforceSubscriptionPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.READ_PHONE_STATE", "Requires READ_PHONE_STATE");
    }

    private void broadcastSimInfoContentChanged() {
        this.mContext.sendBroadcast(new Intent("android.intent.action.ACTION_SUBINFO_CONTENT_CHANGE"));
        this.mContext.sendBroadcast(new Intent("android.intent.action.ACTION_SUBINFO_RECORD_UPDATED"));
    }

    private boolean checkNotifyPermission(String method) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE") == 0) {
            return true;
        }
        logd("checkNotifyPermission Permission Denial: " + method + " from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
        return false;
    }

    public void notifySubscriptionInfoChanged() {
        if (checkNotifyPermission("notifySubscriptionInfoChanged")) {
            ITelephonyRegistry tr = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService("telephony.registry"));
            try {
                logd("notifySubscriptionInfoChanged:");
                tr.notifySubscriptionInfoChanged();
            } catch (RemoteException e) {
            }
            broadcastSimInfoContentChanged();
        }
    }

    private SubscriptionInfo getSubInfoRecord(Cursor cursor) {
        int id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));
        String iccId = cursor.getString(cursor.getColumnIndexOrThrow("icc_id"));
        int simSlotIndex = cursor.getInt(cursor.getColumnIndexOrThrow("sim_id"));
        String displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name"));
        String carrierName = cursor.getString(cursor.getColumnIndexOrThrow("carrier_name"));
        int nameSource = cursor.getInt(cursor.getColumnIndexOrThrow("name_source"));
        int iconTint = cursor.getInt(cursor.getColumnIndexOrThrow("color"));
        String number = cursor.getString(cursor.getColumnIndexOrThrow(IccProvider.STR_NUMBER));
        int dataRoaming = cursor.getInt(cursor.getColumnIndexOrThrow("data_roaming"));
        Bitmap iconBitmap = BitmapFactory.decodeResource(this.mContext.getResources(), 17302605);
        int mcc = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MCC));
        int mnc = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MNC));
        String countryIso = getSubscriptionCountryIso(id);
        int status = cursor.getInt(cursor.getColumnIndexOrThrow("sub_state"));
        int nwMode = cursor.getInt(cursor.getColumnIndexOrThrow("network_mode"));
        logd("[getSubInfoRecord] id:" + id + " iccid:" + iccId + " simSlotIndex:" + simSlotIndex + " displayName:" + displayName + " nameSource:" + nameSource + " iconTint:" + iconTint + " dataRoaming:" + dataRoaming + " mcc:" + mcc + " mnc:" + mnc + " countIso:" + countryIso + " status:" + status + " nwMode:" + nwMode);
        String line1Number = this.mTelephonyManager.getLine1NumberForSubscriber(id);
        if (!TextUtils.isEmpty(line1Number) && !line1Number.equals(number)) {
            logd("Line1Number is different: " + line1Number);
            number = line1Number;
        }
        return new SubscriptionInfo(id, iccId, simSlotIndex, displayName, carrierName, nameSource, iconTint, number, dataRoaming, iconBitmap, mcc, mnc, countryIso, status, nwMode);
    }

    private String getSubscriptionCountryIso(int subId) {
        int phoneId = getPhoneId(subId);
        return phoneId < 0 ? "" : TelephonyManager.getTelephonyProperty(phoneId, "gsm.sim.operator.iso-country", "");
    }

    private List<SubscriptionInfo> getSubInfo(String selection, Object queryKey) {
        ArrayList<SubscriptionInfo> subList;
        logd("selection:" + selection + " " + queryKey);
        String[] selectionArgs = null;
        if (queryKey != null) {
            selectionArgs = new String[]{queryKey.toString()};
        }
        ArrayList<SubscriptionInfo> subList2 = null;
        Cursor cursor = this.mContext.getContentResolver().query(SubscriptionManager.CONTENT_URI, null, selection, selectionArgs, null);
        try {
            if (cursor != null) {
                ArrayList<SubscriptionInfo> subList3 = null;
                while (cursor.moveToNext()) {
                    try {
                        SubscriptionInfo subInfo = getSubInfoRecord(cursor);
                        if (subInfo != null) {
                            if (subList3 == null) {
                                subList = new ArrayList<>();
                            } else {
                                subList = subList3;
                            }
                            subList.add(subInfo);
                        } else {
                            subList = subList3;
                        }
                        subList3 = subList;
                    } catch (Throwable th) {
                        th = th;
                        if (cursor != null) {
                            cursor.close();
                        }
                        throw th;
                    }
                }
                subList2 = subList3;
            } else {
                logd("Query fail");
            }
            if (cursor != null) {
                cursor.close();
            }
            return subList2;
        } catch (Throwable th2) {
            th = th2;
        }
    }

    private int getUnusedColor() {
        List<SubscriptionInfo> availableSubInfos = getActiveSubscriptionInfoList();
        this.colorArr = this.mContext.getResources().getIntArray(17235978);
        int colorIdx = 0;
        if (availableSubInfos != null) {
            for (int i = 0; i < this.colorArr.length; i++) {
                int j = 0;
                while (j < availableSubInfos.size() && this.colorArr[i] != availableSubInfos.get(j).getIconTint()) {
                    j++;
                }
                if (j == availableSubInfos.size()) {
                    return this.colorArr[i];
                }
            }
            colorIdx = availableSubInfos.size() % this.colorArr.length;
        }
        return this.colorArr[colorIdx];
    }

    public SubscriptionInfo getActiveSubscriptionInfo(int subId) {
        enforceSubscriptionPermission();
        if (!SubscriptionManager.isValidSubscriptionId(subId) || !isSubInfoReady()) {
            logd("[getSubInfoUsingSubIdx]- invalid subId or not ready = " + subId);
            return null;
        }
        List<SubscriptionInfo> subList = getActiveSubscriptionInfoList();
        if (subList != null) {
            for (SubscriptionInfo si : subList) {
                if (si.getSubscriptionId() == subId) {
                    logd("[getActiveSubInfoForSubscriber]+ subId=" + subId + " subInfo=" + si);
                    return si;
                }
            }
        }
        logd("[getActiveSubInfoForSubscriber]- subId=" + subId + " subList=" + subList + " subInfo=null");
        return null;
    }

    public SubscriptionInfo getActiveSubscriptionInfoForIccId(String iccId) {
        enforceSubscriptionPermission();
        List<SubscriptionInfo> subList = getActiveSubscriptionInfoList();
        if (subList != null) {
            for (SubscriptionInfo si : subList) {
                if (si.getIccId() == iccId) {
                    logd("[getActiveSubInfoUsingIccId]+ iccId=" + iccId + " subInfo=" + si);
                    return si;
                }
            }
        }
        logd("[getActiveSubInfoUsingIccId]+ iccId=" + iccId + " subList=" + subList + " subInfo=null");
        return null;
    }

    public SubscriptionInfo getActiveSubscriptionInfoForSimSlotIndex(int slotIdx) {
        enforceSubscriptionPermission();
        List<SubscriptionInfo> subList = getActiveSubscriptionInfoList();
        if (subList != null) {
            for (SubscriptionInfo si : subList) {
                if (si.getSimSlotIndex() == slotIdx) {
                    logd("[getActiveSubscriptionInfoForSimSlotIndex]+ slotIdx=" + slotIdx + " subId=" + si);
                    return si;
                }
            }
            logd("[getActiveSubscriptionInfoForSimSlotIndex]+ slotIdx=" + slotIdx + " subId=null");
        } else {
            logd("[getActiveSubscriptionInfoForSimSlotIndex]+ subList=null");
        }
        return null;
    }

    public List<SubscriptionInfo> getAllSubInfoList() {
        logd("[getAllSubInfoList]+");
        enforceSubscriptionPermission();
        List<SubscriptionInfo> subList = getSubInfo(null, null);
        if (subList != null) {
            logd("[getAllSubInfoList]- " + subList.size() + " infos return");
        } else {
            logd("[getAllSubInfoList]- no info return");
        }
        return subList;
    }

    public List<SubscriptionInfo> getActiveSubscriptionInfoList() {
        enforceSubscriptionPermission();
        logdl("[getActiveSubInfoList]+");
        if (!isSubInfoReady()) {
            logdl("[getActiveSubInfoList] Sub Controller not ready");
            return null;
        }
        List<SubscriptionInfo> subList = getSubInfo("sim_id>=0", null);
        if (subList != null) {
            Collections.sort(subList, new Comparator<SubscriptionInfo>() { // from class: com.android.internal.telephony.SubscriptionController.2
                public int compare(SubscriptionInfo arg0, SubscriptionInfo arg1) {
                    int flag = arg0.getSimSlotIndex() - arg1.getSimSlotIndex();
                    if (flag == 0) {
                        return arg0.getSubscriptionId() - arg1.getSubscriptionId();
                    }
                    return flag;
                }
            });
            logdl("[getActiveSubInfoList]- " + subList.size() + " infos return");
        } else {
            logdl("[getActiveSubInfoList]- no info return");
        }
        return subList;
    }

    public int getActiveSubInfoCount() {
        logd("[getActiveSubInfoCount]+");
        List<SubscriptionInfo> records = getActiveSubscriptionInfoList();
        if (records == null) {
            logd("[getActiveSubInfoCount] records null");
            return 0;
        }
        logd("[getActiveSubInfoCount]- count: " + records.size());
        return records.size();
    }

    public int getAllSubInfoCount() {
        logd("[getAllSubInfoCount]+");
        enforceSubscriptionPermission();
        Cursor cursor = this.mContext.getContentResolver().query(SubscriptionManager.CONTENT_URI, null, null, null, null);
        if (cursor != null) {
            try {
                int count = cursor.getCount();
                logd("[getAllSubInfoCount]- " + count + " SUB(s) in DB");
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else {
            if (cursor != null) {
                cursor.close();
            }
            logd("[getAllSubInfoCount]- no SUB in DB");
            return 0;
        }
    }

    public int getActiveSubInfoCountMax() {
        return this.mTelephonyManager.getSimCount();
    }

    /* JADX WARN: Removed duplicated region for block: B:20:0x012e  */
    /* JADX WARN: Removed duplicated region for block: B:40:0x024a  */
    /* JADX WARN: Removed duplicated region for block: B:63:0x0147 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public int addSubInfoRecord(java.lang.String r23, int r24) {
        /*
            Method dump skipped, instructions count: 766
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.SubscriptionController.addSubInfoRecord(java.lang.String, int):int");
    }

    public boolean setPlmnSpn(int slotId, boolean showPlmn, String plmn, boolean showSpn, String spn) {
        int[] subIds = getSubId(slotId);
        if (this.mContext.getPackageManager().resolveContentProvider(SubscriptionManager.CONTENT_URI.getAuthority(), 0) == null || subIds == null || !SubscriptionManager.isValidSubscriptionId(subIds[0])) {
            logd("[setPlmnSpn] No valid subscription to store info");
            notifySubscriptionInfoChanged();
            return false;
        }
        String carrierText = "";
        if (showPlmn) {
            carrierText = plmn;
            if (showSpn) {
                carrierText = carrierText + this.mContext.getString(17041030).toString() + spn;
            }
        } else if (showSpn) {
            carrierText = spn;
        }
        for (int i : subIds) {
            setCarrierText(carrierText, i);
        }
        return true;
    }

    public int setCarrierText(String text, int subId) {
        logd("[setCarrierText]+ text:" + text + " subId:" + subId);
        enforceSubscriptionPermission();
        ContentValues value = new ContentValues(1);
        value.put("carrier_name", text);
        int result = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value, "_id=" + Integer.toString(subId), null);
        notifySubscriptionInfoChanged();
        return result;
    }

    public int setIconTint(int tint, int subId) {
        logd("[setIconTint]+ tint:" + tint + " subId:" + subId);
        enforceSubscriptionPermission();
        validateSubId(subId);
        ContentValues value = new ContentValues(1);
        value.put("color", Integer.valueOf(tint));
        logd("[setIconTint]- tint:" + tint + " set");
        int result = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value, "_id=" + Integer.toString(subId), null);
        notifySubscriptionInfoChanged();
        return result;
    }

    public int setDisplayName(String displayName, int subId) {
        return setDisplayNameUsingSrc(displayName, subId, -1L);
    }

    public int setDisplayNameUsingSrc(String displayName, int subId, long nameSource) {
        String nameToSet;
        logd("[setDisplayName]+  displayName:" + displayName + " subId:" + subId + " nameSource:" + nameSource);
        enforceSubscriptionPermission();
        validateSubId(subId);
        if (displayName == null) {
            nameToSet = this.mContext.getString(17039374);
        } else {
            nameToSet = displayName;
        }
        ContentValues value = new ContentValues(1);
        value.put("display_name", nameToSet);
        if (nameSource >= 0) {
            logd("Set nameSource=" + nameSource);
            value.put("name_source", Long.valueOf(nameSource));
        }
        logd("[setDisplayName]- mDisplayName:" + nameToSet + " set");
        int result = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value, "_id=" + Integer.toString(subId), null);
        notifySubscriptionInfoChanged();
        return result;
    }

    public int setDisplayNumber(String number, int subId) {
        logd("[setDisplayNumber]+ number:" + number + " subId:" + subId);
        enforceSubscriptionPermission();
        validateSubId(subId);
        int phoneId = getPhoneId(subId);
        if (number == null || phoneId < 0 || phoneId >= TelephonyManager.getDefault().getPhoneCount()) {
            logd("[setDispalyNumber]- fail");
            return -1;
        }
        ContentValues value = new ContentValues(1);
        value.put(IccProvider.STR_NUMBER, number);
        int result = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value, "_id=" + Integer.toString(subId), null);
        logd("[setDisplayNumber]- number: " + number + " update result :" + result);
        notifySubscriptionInfoChanged();
        return result;
    }

    public int setDataRoaming(int roaming, int subId) {
        logd("[setDataRoaming]+ roaming:" + roaming + " subId:" + subId);
        enforceSubscriptionPermission();
        validateSubId(subId);
        if (roaming < 0) {
            logd("[setDataRoaming]- fail");
            return -1;
        }
        ContentValues value = new ContentValues(1);
        value.put("data_roaming", Integer.valueOf(roaming));
        logd("[setDataRoaming]- roaming:" + roaming + " set");
        int update = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value, "_id=" + Integer.toString(subId), null);
        notifySubscriptionInfoChanged();
        return update;
    }

    public int setMccMnc(String mccMnc, int subId) {
        int mcc = 0;
        int mnc = 0;
        try {
            mcc = Integer.parseInt(mccMnc.substring(0, 3));
            mnc = Integer.parseInt(mccMnc.substring(3));
        } catch (NumberFormatException e) {
            loge("[setMccMnc] - couldn't parse mcc/mnc: " + mccMnc);
        }
        logd("[setMccMnc]+ mcc/mnc:" + mcc + "/" + mnc + " subId:" + subId);
        ContentValues value = new ContentValues(2);
        value.put(Telephony.Carriers.MCC, Integer.valueOf(mcc));
        value.put(Telephony.Carriers.MNC, Integer.valueOf(mnc));
        int result = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value, "_id=" + Integer.toString(subId), null);
        notifySubscriptionInfoChanged();
        return result;
    }

    public int getSlotId(int subId) {
        if (subId == Integer.MAX_VALUE) {
            subId = getDefaultSubId();
        }
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            logd("[getSlotId]- subId invalid");
            return -1;
        } else if (subId >= DUMMY_SUB_ID_BASE) {
            logd("getSlotId,  received dummy subId " + subId);
            return subId - DUMMY_SUB_ID_BASE;
        } else if (mSlotIdxToSubId.size() == 0) {
            logd("[getSlotId]- size == 0, return SIM_NOT_INSERTED instead");
            return -1;
        } else {
            for (Map.Entry<Integer, Integer> entry : mSlotIdxToSubId.entrySet()) {
                int intValue = entry.getKey().intValue();
                if (subId == entry.getValue().intValue()) {
                    return intValue;
                }
            }
            logd("[getSlotId]- return fail");
            return -1;
        }
    }

    @Deprecated
    public int[] getSubId(int slotIdx) {
        if (slotIdx == Integer.MAX_VALUE) {
            slotIdx = getSlotId(getDefaultSubId());
            logd("[getSubId] map default slotIdx=" + slotIdx);
        }
        if (!SubscriptionManager.isValidSlotId(slotIdx)) {
            logd("[getSubId]- invalid slotIdx=" + slotIdx);
            return null;
        } else if (mSlotIdxToSubId.size() == 0) {
            logd("[getSubId]- mSlotIdToSubIdMap.size == 0, return DummySubIds slotIdx=" + slotIdx);
            return getDummySubIds(slotIdx);
        } else {
            ArrayList<Integer> subIds = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : mSlotIdxToSubId.entrySet()) {
                int slot = entry.getKey().intValue();
                int sub = entry.getValue().intValue();
                if (slotIdx == slot) {
                    subIds.add(Integer.valueOf(sub));
                }
            }
            int numSubIds = subIds.size();
            if (numSubIds > 0) {
                int[] subIdArr = new int[numSubIds];
                for (int i = 0; i < numSubIds; i++) {
                    subIdArr[i] = subIds.get(i).intValue();
                }
                return subIdArr;
            }
            logd("[getSubId]- numSubIds == 0, return DummySubIds slotIdx=" + slotIdx);
            return getDummySubIds(slotIdx);
        }
    }

    public int getPhoneId(int subId) {
        if (subId == Integer.MAX_VALUE) {
            subId = getDefaultSubId();
            logdl("[getPhoneId] asked for default subId=" + subId);
        }
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            logdl("[getPhoneId]- invalid subId return=-1");
            return -1;
        } else if (subId >= DUMMY_SUB_ID_BASE) {
            logd("getPhoneId,  received dummy subId " + subId);
            return subId - DUMMY_SUB_ID_BASE;
        } else if (mSlotIdxToSubId.size() == 0) {
            int phoneId = mDefaultPhoneId;
            logdl("[getPhoneId]- no sims, returning default phoneId=" + phoneId);
            return phoneId;
        } else {
            for (Map.Entry<Integer, Integer> entry : mSlotIdxToSubId.entrySet()) {
                int sim = entry.getKey().intValue();
                if (subId == entry.getValue().intValue()) {
                    logd("[getPhoneId]- return =" + sim);
                    return sim;
                }
            }
            int phoneId2 = mDefaultPhoneId;
            logdl("[getPhoneId]- subId=" + subId + " not found return default phoneId=" + phoneId2);
            return phoneId2;
        }
    }

    private int[] getDummySubIds(int slotIdx) {
        int numSubs = getActiveSubInfoCountMax();
        if (numSubs <= 0) {
            return null;
        }
        int[] dummyValues = new int[numSubs];
        for (int i = 0; i < numSubs; i++) {
            dummyValues[i] = DUMMY_SUB_ID_BASE + slotIdx;
        }
        logd("getDummySubIds: slotIdx=" + slotIdx + " return " + numSubs + " DummySubIds with each subId=" + dummyValues[0]);
        return dummyValues;
    }

    public int clearSubInfo() {
        enforceSubscriptionPermission();
        logd("[clearSubInfo]+");
        int size = mSlotIdxToSubId.size();
        if (size == 0) {
            logdl("[clearSubInfo]- no simInfo size=" + size);
            return 0;
        }
        mSlotIdxToSubId.clear();
        logdl("[clearSubInfo]- clear size=" + size);
        return size;
    }

    private void logvl(String msg) {
        logv(msg);
        this.mLocalLog.log(msg);
    }

    private void logv(String msg) {
        Rlog.v(LOG_TAG, msg);
    }

    private void logdl(String msg) {
        logd(msg);
        this.mLocalLog.log(msg);
    }

    private static void slogd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    public void logd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    private void logel(String msg) {
        loge(msg);
        this.mLocalLog.log(msg);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    public int getDefaultSubId() {
        int subId;
        if (this.mContext.getResources().getBoolean(17956947)) {
            subId = getDefaultVoiceSubId();
        } else {
            subId = getDefaultDataSubId();
        }
        if (!isActiveSubId(subId)) {
            return mDefaultFallbackSubId;
        }
        return subId;
    }

    public void setDefaultSmsSubId(int subId) {
        if (subId == Integer.MAX_VALUE) {
            throw new RuntimeException("setDefaultSmsSubId called with DEFAULT_SUB_ID");
        }
        logdl("[setDefaultSmsSubId] subId=" + subId);
        Settings.Global.putInt(this.mContext.getContentResolver(), "multi_sim_sms", subId);
        broadcastDefaultSmsSubIdChanged(subId);
    }

    private void broadcastDefaultSmsSubIdChanged(int subId) {
        logdl("[broadcastDefaultSmsSubIdChanged] subId=" + subId);
        Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED");
        intent.addFlags(536870912);
        intent.putExtra("subscription", subId);
        this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    public int getDefaultSmsSubId() {
        return Settings.Global.getInt(this.mContext.getContentResolver(), "multi_sim_sms", -1);
    }

    public void setDefaultVoiceSubId(int subId) {
        if (subId == Integer.MAX_VALUE) {
            throw new RuntimeException("setDefaultVoiceSubId called with DEFAULT_SUB_ID");
        }
        logdl("[setDefaultVoiceSubId] subId=" + subId);
        Settings.Global.putInt(this.mContext.getContentResolver(), "multi_sim_voice_call", subId);
        broadcastDefaultVoiceSubIdChanged(subId);
    }

    private void broadcastDefaultVoiceSubIdChanged(int subId) {
        logdl("[broadcastDefaultVoiceSubIdChanged] subId=" + subId);
        Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED");
        intent.addFlags(536870912);
        intent.putExtra("subscription", subId);
        this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    public int getDefaultVoiceSubId() {
        return Settings.Global.getInt(this.mContext.getContentResolver(), "multi_sim_voice_call", -1);
    }

    public boolean isSMSPromptEnabled() {
        int value = 0;
        try {
            value = Settings.Global.getInt(this.mContext.getContentResolver(), "multi_sim_sms_prompt");
        } catch (Settings.SettingNotFoundException e) {
            loge("Settings Exception Reading Dual Sim SMS Prompt Values");
        }
        return value != 0;
    }

    public void setSMSPromptEnabled(boolean enabled) {
        Settings.Global.putInt(this.mContext.getContentResolver(), "multi_sim_sms_prompt", !enabled ? 0 : 1);
        logd("setSMSPromptOption to " + enabled);
    }

    public int getDefaultDataSubId() {
        return Settings.Global.getInt(this.mContext.getContentResolver(), "multi_sim_data_call", -1);
    }

    public int getCurrentDds() {
        return this.mScheduler.getCurrentDds();
    }

    public void updateDataSubId(AsyncResult ar) {
        Integer subId = (Integer) ar.result;
        logd(" updateDataSubId,  subId=" + subId + " exception " + ar.exception);
        if (ar.exception == null) {
            setDataSubId(subId.intValue());
            this.mScheduler.updateCurrentDds(null);
            broadcastDefaultDataSubIdChanged(subId.intValue());
            updateAllDataConnectionTrackers();
            return;
        }
        int defaultDds = getDefaultDataSubId();
        logd("DDS switch failed, enforce last dds = " + defaultDds);
        setDefaultDataSubId(defaultDds);
    }

    public void setDefaultDataSubId(int subId) {
        if (subId == Integer.MAX_VALUE) {
            throw new RuntimeException("setDefaultDataSubId called with DEFAULT_SUB_ID");
        }
        logdl("[setDefaultDataSubId] subId=" + subId);
        if (this.mDctController == null) {
            this.mDctController = DctController.getInstance();
            this.mDctController.registerForDefaultDataSwitchInfo(this.mDataConnectionHandler, 1, null);
        }
        this.mDctController.setDefaultDataSubId(subId);
    }

    public void setDataSubId(int subId) {
        Settings.Global.putInt(this.mContext.getContentResolver(), "multi_sim_data_call", subId);
    }

    private void updateAllDataConnectionTrackers() {
        int len = sProxyPhones.length;
        logdl("[updateAllDataConnectionTrackers] sProxyPhones.length=" + len);
        for (int phoneId = 0; phoneId < len; phoneId++) {
            logdl("[updateAllDataConnectionTrackers] phoneId=" + phoneId);
            sProxyPhones[phoneId].updateDataConnectionTracker();
        }
    }

    private void broadcastDefaultDataSubIdChanged(int subId) {
        logdl("[broadcastDefaultDataSubIdChanged] subId=" + subId);
        Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");
        intent.addFlags(536870912);
        intent.putExtra("subscription", getDefaultDataSubId());
        this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void setDefaultFallbackSubId(int subId) {
        if (subId == Integer.MAX_VALUE) {
            throw new RuntimeException("setDefaultSubId called with DEFAULT_SUB_ID");
        }
        logdl("[setDefaultFallbackSubId] subId=" + subId);
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            int phoneId = getPhoneId(subId);
            if (phoneId < 0 || (phoneId >= TelephonyManager.getDefault().getPhoneCount() && TelephonyManager.getDefault().getSimCount() != 1)) {
                logdl("[setDefaultFallbackSubId] not set invalid phoneId=" + phoneId + " subId=" + subId);
                return;
            }
            logdl("[setDefaultFallbackSubId] set mDefaultFallbackSubId=" + subId);
            mDefaultFallbackSubId = subId;
            MccTable.updateMccMncConfiguration(this.mContext, TelephonyManager.getDefault().getSimOperator(phoneId), false);
            Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_SUBSCRIPTION_CHANGED");
            intent.addFlags(536870912);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneId, subId);
            logdl("[setDefaultFallbackSubId] broadcast default subId changed phoneId=" + phoneId + " subId=" + subId);
            this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DataConnectionHandler extends Handler {
        private DataConnectionHandler() {
            SubscriptionController.this = r1;
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    SubscriptionController.this.logd("EVENT_SET_DEFAULT_DATA_DONE subId:" + ((Integer) ar.result));
                    SubscriptionController.this.updateDataSubId(ar);
                    return;
                default:
                    return;
            }
        }
    }

    public void clearDefaultsForInactiveSubIds() {
        List<SubscriptionInfo> records = getActiveSubscriptionInfoList();
        logdl("[clearDefaultsForInactiveSubIds] records: " + records);
        if (shouldDefaultBeCleared(records, getDefaultDataSubId())) {
            logd("[clearDefaultsForInactiveSubIds] clearing default data sub id");
            setDefaultDataSubId(-1);
        }
        if (shouldDefaultBeCleared(records, getDefaultSmsSubId())) {
            logdl("[clearDefaultsForInactiveSubIds] clearing default sms sub id");
            setDefaultSmsSubId(-1);
        }
        if (shouldDefaultBeCleared(records, getDefaultVoiceSubId())) {
            logdl("[clearDefaultsForInactiveSubIds] clearing default voice sub id");
            setDefaultVoiceSubId(-1);
        }
    }

    private boolean shouldDefaultBeCleared(List<SubscriptionInfo> records, int subId) {
        logdl("[shouldDefaultBeCleared: subId] " + subId);
        if (records == null) {
            logdl("[shouldDefaultBeCleared] return true no records subId=" + subId);
            return true;
        } else if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            logdl("[shouldDefaultBeCleared] return false only one subId, subId=" + subId);
            return false;
        } else {
            for (SubscriptionInfo record : records) {
                int id = record.getSubscriptionId();
                logdl("[shouldDefaultBeCleared] Record.id: " + id);
                if (id == subId) {
                    logdl("[shouldDefaultBeCleared] return false subId is active, subId=" + subId);
                    return false;
                }
            }
            logdl("[shouldDefaultBeCleared] return true not active subId=" + subId);
            return true;
        }
    }

    public int getSubIdUsingPhoneId(int phoneId) {
        int[] subIds = getSubId(phoneId);
        if (subIds == null || subIds.length == 0) {
            return -1;
        }
        return subIds[0];
    }

    public int[] getSubIdUsingSlotId(int slotId) {
        return getSubId(slotId);
    }

    public List<SubscriptionInfo> getSubInfoUsingSlotIdWithCheck(int slotId, boolean needCheck) {
        Throwable th;
        logd("[getSubInfoUsingSlotIdWithCheck]+ slotId:" + slotId);
        enforceSubscriptionPermission();
        if (slotId == Integer.MAX_VALUE) {
            slotId = getSlotId(getDefaultSubId());
        }
        if (!SubscriptionManager.isValidSlotId(slotId)) {
            logd("[getSubInfoUsingSlotIdWithCheck]- invalid slotId");
            return null;
        } else if (!needCheck || isSubInfoReady()) {
            Cursor cursor = this.mContext.getContentResolver().query(SubscriptionManager.CONTENT_URI, null, "sim_id=?", new String[]{String.valueOf(slotId)}, null);
            ArrayList<SubscriptionInfo> subList = null;
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    try {
                        SubscriptionInfo subInfo = getSubInfoRecord(cursor);
                        if (subInfo != null) {
                            if (subList == null) {
                                subList = new ArrayList<>();
                            } else {
                                subList = subList;
                            }
                            try {
                                subList.add(subInfo);
                            } catch (Throwable th2) {
                                th = th2;
                                if (cursor != null) {
                                    cursor.close();
                                }
                                throw th;
                            }
                        } else {
                            subList = subList;
                        }
                    } catch (Throwable th3) {
                        th = th3;
                    }
                }
                subList = subList;
            }
            if (cursor != null) {
                cursor.close();
            }
            logd("[getSubInfoUsingSlotId]- null info return");
            return subList;
        } else {
            logd("[getSubInfoUsingSlotIdWithCheck]- not ready");
            return null;
        }
    }

    private void validateSubId(int subId) {
        logd("validateSubId subId: " + subId);
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new RuntimeException("Invalid sub id passed as parameter");
        } else if (subId == Integer.MAX_VALUE) {
            throw new RuntimeException("Default sub id passed as parameter");
        }
    }

    public void updatePhonesAvailability(PhoneProxy[] phones) {
        sProxyPhones = phones;
    }

    public int[] getActiveSubIdList() {
        Set<Map.Entry<Integer, Integer>> simInfoSet = mSlotIdxToSubId.entrySet();
        logdl("[getActiveSubIdList] simInfoSet=" + simInfoSet);
        int[] subIdArr = new int[simInfoSet.size()];
        int i = 0;
        for (Map.Entry<Integer, Integer> entry : simInfoSet) {
            subIdArr[i] = entry.getValue().intValue();
            i++;
        }
        logdl("[getActiveSubIdList] X subIdArr.length=" + subIdArr.length);
        return subIdArr;
    }

    public void activateSubId(int subId) {
        if (getSubState(subId) == 1) {
            logd("activateSubId: subscription already active, subId = " + subId);
            return;
        }
        SubscriptionHelper.getInstance().setUiccSubscription(getSlotId(subId), 1);
    }

    public void deactivateSubId(int subId) {
        if (getSubState(subId) == 0) {
            logd("activateSubId: subscription already deactivated, subId = " + subId);
            return;
        }
        SubscriptionHelper.getInstance().setUiccSubscription(getSlotId(subId), 0);
    }

    public void setNwMode(int subId, int nwMode) {
        logd("setNwMode, nwMode: " + nwMode + " subId: " + subId);
        ContentValues value = new ContentValues(1);
        value.put("network_mode", Integer.valueOf(nwMode));
        this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value, "_id=" + Integer.toString(subId), null);
    }

    public int getNwMode(int subId) {
        SubscriptionInfo subInfo = getActiveSubscriptionInfo(subId);
        if (subInfo != null) {
            return subInfo.mNwMode;
        }
        loge("getSubState: invalid subId = " + subId);
        return -1;
    }

    public int setSubState(int subId, int subStatus) {
        int result = 0;
        logd("setSubState, subStatus: " + subStatus + " subId: " + subId);
        if (ModemStackController.getInstance().isStackReady()) {
            ContentValues value = new ContentValues(1);
            value.put("sub_state", Integer.valueOf(subStatus));
            result = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, value, "_id=" + Integer.toString(subId), null);
        }
        Intent intent = new Intent("android.intent.action.ACTION_SUBINFO_CONTENT_CHANGE");
        intent.putExtra("_id", subId);
        intent.putExtra("columnName", "sub_state");
        intent.putExtra("intContent", subStatus);
        intent.putExtra("stringContent", "None");
        this.mContext.sendBroadcast(intent);
        this.mContext.sendBroadcast(new Intent("android.intent.action.ACTION_SUBINFO_RECORD_UPDATED"));
        return result;
    }

    public int getSubState(int subId) {
        SubscriptionInfo subInfo = getActiveSubscriptionInfo(subId);
        if (subInfo == null || subInfo.getSimSlotIndex() < 0) {
            return 0;
        }
        return subInfo.mStatus;
    }

    public void updateUserPrefs(boolean setDds) {
        List<SubscriptionInfo> subInfoList = getActiveSubscriptionInfoList();
        int mActCount = 0;
        SubscriptionInfo mNextActivatedSub = null;
        if (subInfoList == null) {
            int[] dummySubId = getDummySubIds(mDefaultPhoneId);
            logd("updateUserPrefs: subscription are not avaiable dds = " + getDefaultDataSubId() + " voice = " + getDefaultVoiceSubId() + " sms = " + getDefaultSmsSubId() + " setDDs = " + setDds);
            setDefaultFallbackSubId(dummySubId[0]);
            setDefaultVoiceSubId(dummySubId[0]);
            setDefaultSmsSubId(dummySubId[0]);
            setDataSubId(dummySubId[0]);
            return;
        }
        for (SubscriptionInfo subInfo : subInfoList) {
            if (getSubState(subInfo.getSubscriptionId()) == 1) {
                mActCount++;
                if (mNextActivatedSub == null) {
                    mNextActivatedSub = subInfo;
                }
            }
        }
        logd("updateUserPrefs: active sub count = " + mActCount + " dds = " + getDefaultDataSubId() + " voice = " + getDefaultVoiceSubId() + " sms = " + getDefaultSmsSubId() + " setDDs = " + setDds);
        if (mActCount < 2) {
            setSMSPromptEnabled(false);
            setVoicePromptEnabled(false);
        }
        if (mNextActivatedSub != null) {
            if (getSubState(getDefaultSubId()) == 0) {
                setDefaultFallbackSubId(mNextActivatedSub.getSubscriptionId());
            }
            int ddsSubId = getDefaultDataSubId();
            int ddsSubState = getSubState(ddsSubId);
            if (setDds || ddsSubState == 0) {
                if (ddsSubState == 0) {
                    ddsSubId = mNextActivatedSub.getSubscriptionId();
                }
                setDefaultDataSubId(ddsSubId);
            }
            if (getSubState(getDefaultVoiceSubId()) == 0 && !isVoicePromptEnabled()) {
                setDefaultVoiceSubId(mNextActivatedSub.getSubscriptionId());
            }
            if (getSubState(getDefaultSmsSubId()) == 0 && !isSMSPromptEnabled()) {
                setDefaultSmsSubId(mNextActivatedSub.getSubscriptionId());
            }
            logd("updateUserPrefs: after currentDds = " + getDefaultDataSubId() + " voice = " + getDefaultVoiceSubId() + " sms = " + getDefaultSmsSubId() + " newDds = " + ddsSubId);
        }
    }

    public boolean isVoicePromptEnabled() {
        int value = 0;
        try {
            value = Settings.Global.getInt(this.mContext.getContentResolver(), "multi_sim_voice_prompt");
        } catch (Settings.SettingNotFoundException e) {
            loge("Settings Exception Reading Dual Sim Voice Prompt Values");
        }
        return value != 0;
    }

    public void setVoicePromptEnabled(boolean enabled) {
        Settings.Global.putInt(this.mContext.getContentResolver(), "multi_sim_voice_prompt", !enabled ? 0 : 1);
        logd("setVoicePromptOption to " + enabled);
    }

    public int getOnDemandDataSubId() {
        return getCurrentDds();
    }

    public void registerForOnDemandDdsLockNotification(int clientSubId, OnDemandDdsLockNotifier callback) {
        logd("registerForOnDemandDdsLockNotification for client=" + clientSubId);
        this.mOnDemandDdsLockNotificationRegistrants.put(Integer.valueOf(clientSubId), callback);
    }

    public void notifyOnDemandDataSubIdChanged(NetworkRequest n) {
        OnDemandDdsLockNotifier notifier = this.mOnDemandDdsLockNotificationRegistrants.get(Integer.valueOf(getSubIdFromNetworkRequest(n)));
        if (notifier != null) {
            notifier.notifyOnDemandDdsLockGranted(n);
        } else {
            logd("No registrants for OnDemandDdsLockGranted event");
        }
    }

    public void removeStaleSubPreferences(String prefKey) {
        List<SubscriptionInfo> subInfoList = getAllSubInfoList();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        for (SubscriptionInfo subInfo : subInfoList) {
            if (subInfo.getSimSlotIndex() == -1) {
                sp.edit().remove(prefKey + subInfo.getSubscriptionId()).commit();
            }
        }
    }

    private boolean isActiveSubId(int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return false;
        }
        for (Map.Entry<Integer, Integer> entry : mSlotIdxToSubId.entrySet()) {
            if (subId == entry.getValue().intValue()) {
                return true;
            }
        }
        return false;
    }

    public int getSimStateForSubscriber(int subId) {
        IccCardConstants.State simState;
        String err;
        int phoneIdx = getPhoneId(subId);
        if (phoneIdx < 0) {
            simState = IccCardConstants.State.UNKNOWN;
            err = "invalid PhoneIdx";
        } else {
            Phone phone = PhoneFactory.getPhone(phoneIdx);
            if (phone == null) {
                simState = IccCardConstants.State.UNKNOWN;
                err = "phone == null";
            } else {
                IccCard icc = phone.getIccCard();
                if (icc == null) {
                    simState = IccCardConstants.State.UNKNOWN;
                    err = "icc == null";
                } else {
                    simState = icc.getState();
                    err = "";
                }
            }
        }
        logd("getSimStateForSubscriber: " + err + " simState=" + simState + " ordinal=" + simState.ordinal());
        return simState.ordinal();
    }

    private static void printStackTrace(String msg) {
        RuntimeException re = new RuntimeException();
        slogd("StackTrace - " + msg);
        StackTraceElement[] st = re.getStackTrace();
        boolean first = true;
        for (StackTraceElement ste : st) {
            if (first) {
                first = false;
            } else {
                slogd(ste.toString());
            }
        }
    }

    public int[] getActivatedSubIdList() {
        Set<Map.Entry<Integer, Integer>> simInfoSet = mSlotIdxToSubId.entrySet();
        logd("getActivatedSubIdList: simInfoSet=" + simInfoSet);
        int[] subIdArr = new int[simInfoSet.size()];
        int i = 0;
        for (Map.Entry<Integer, Integer> entry : simInfoSet) {
            subIdArr[i] = entry.getValue().intValue();
            i++;
        }
        logd("getActivatedSubIdList: X subIdArr.length=" + subIdArr.length);
        return subIdArr;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.DUMP", "Requires DUMP");
        long token = Binder.clearCallingIdentity();
        try {
            pw.println("SubscriptionController:");
            pw.println(" defaultSubId=" + getDefaultSubId());
            pw.println(" defaultDataSubId=" + getDefaultDataSubId());
            pw.println(" defaultVoiceSubId=" + getDefaultVoiceSubId());
            pw.println(" defaultSmsSubId=" + getDefaultSmsSubId());
            pw.println(" defaultDataPhoneId=" + SubscriptionManager.from(this.mContext).getDefaultDataPhoneId());
            pw.println(" defaultVoicePhoneId=" + SubscriptionManager.getDefaultVoicePhoneId());
            pw.println(" defaultSmsPhoneId=" + SubscriptionManager.from(this.mContext).getDefaultSmsPhoneId());
            pw.flush();
            for (Map.Entry<Integer, Integer> entry : mSlotIdxToSubId.entrySet()) {
                pw.println(" mSlotIdToSubIdMap[" + entry.getKey() + "]: subId=" + entry.getValue());
            }
            pw.flush();
            pw.println("++++++++++++++++++++++++++++++++");
            List<SubscriptionInfo> sirl = getActiveSubscriptionInfoList();
            if (sirl != null) {
                pw.println(" ActiveSubInfoList:");
                Iterator i$ = sirl.iterator();
                while (i$.hasNext()) {
                    pw.println("  " + i$.next().toString());
                }
            } else {
                pw.println(" ActiveSubInfoList: is null");
            }
            pw.flush();
            pw.println("++++++++++++++++++++++++++++++++");
            List<SubscriptionInfo> sirl2 = getAllSubInfoList();
            if (sirl2 != null) {
                pw.println(" AllSubInfoList:");
                Iterator i$2 = sirl2.iterator();
                while (i$2.hasNext()) {
                    pw.println("  " + i$2.next().toString());
                }
            } else {
                pw.println(" AllSubInfoList: is null");
            }
            pw.flush();
            pw.println("++++++++++++++++++++++++++++++++");
            this.mLocalLog.dump(fd, pw, args);
            pw.flush();
            pw.println("++++++++++++++++++++++++++++++++");
            pw.flush();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }
}
