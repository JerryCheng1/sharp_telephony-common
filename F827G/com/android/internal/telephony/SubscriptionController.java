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
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Telephony.Carriers;
import android.provider.Telephony.CellBroadcasts;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.Log;
import com.android.internal.telephony.ISub.Stub;
import com.android.internal.telephony.IccCardConstants.State;
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
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Set;

public class SubscriptionController extends Stub {
    static final boolean DBG = true;
    private static final int DUMMY_SUB_ID_BASE = 2147483643;
    private static final int EVENT_SET_DEFAULT_DATA_DONE = 1;
    static final String LOG_TAG = "SubscriptionController";
    static final int MAX_LOCAL_LOG_LINES = 500;
    static final boolean VDBG = false;
    private static int mDefaultFallbackSubId = DUMMY_SUB_ID_BASE;
    private static int mDefaultPhoneId = 0;
    private static HashMap<Integer, Integer> mSlotIdxToSubId = new HashMap();
    private static SubscriptionController sInstance = null;
    protected static PhoneProxy[] sProxyPhones;
    private int[] colorArr;
    protected CallManager mCM;
    protected Context mContext;
    private DataConnectionHandler mDataConnectionHandler;
    private DctController mDctController;
    private ScLocalLog mLocalLog = new ScLocalLog(MAX_LOCAL_LOG_LINES);
    protected final Object mLock = new Object();
    private HashMap<Integer, OnDemandDdsLockNotifier> mOnDemandDdsLockNotificationRegistrants = new HashMap();
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            SubscriptionController.this.logd("onReceive " + intent);
            int intExtra = intent.getIntExtra("subscription", -1);
            if (!intent.getAction().equals("android.provider.Telephony.SPN_STRINGS_UPDATED")) {
                return;
            }
            if (intent.getBooleanExtra("showPlmn", false)) {
                String stringExtra = intent.getStringExtra(CellBroadcasts.PLMN);
                if (intent.getBooleanExtra("showSpn", false)) {
                    stringExtra = stringExtra + SubscriptionController.this.mContext.getString(17041030).toString() + intent.getStringExtra("spn");
                }
                SubscriptionController.this.setCarrierText(stringExtra, intExtra);
            } else if (intent.getBooleanExtra("showSpn", false)) {
                SubscriptionController.this.setCarrierText(intent.getStringExtra(CellBroadcasts.PLMN), intExtra);
            }
        }
    };
    private DdsScheduler mScheduler;
    private DdsSchedulerAc mSchedulerAc;
    protected TelephonyManager mTelephonyManager;

    private class DataConnectionHandler extends Handler {
        private DataConnectionHandler() {
        }

        /* synthetic */ DataConnectionHandler(SubscriptionController subscriptionController, AnonymousClass1 anonymousClass1) {
            this();
        }

        public void handleMessage(Message message) {
            switch (message.what) {
                case 1:
                    AsyncResult asyncResult = (AsyncResult) message.obj;
                    SubscriptionController.this.logd("EVENT_SET_DEFAULT_DATA_DONE subId:" + ((Integer) asyncResult.result));
                    SubscriptionController.this.updateDataSubId(asyncResult);
                    return;
                default:
                    return;
            }
        }
    }

    public interface OnDemandDdsLockNotifier {
        void notifyOnDemandDdsLockGranted(NetworkRequest networkRequest);
    }

    static class ScLocalLog {
        private LinkedList<String> mLog = new LinkedList();
        private int mMaxLines;
        private Time mNow;

        public ScLocalLog(int i) {
            this.mMaxLines = i;
            this.mNow = new Time();
        }

        public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
            int i = 0;
            synchronized (this) {
                ListIterator listIterator = this.mLog.listIterator(0);
                while (listIterator.hasNext()) {
                    int i2 = i + 1;
                    printWriter.println(Integer.toString(i) + ": " + ((String) listIterator.next()));
                    if (i2 % 10 == 0) {
                        printWriter.flush();
                        i = i2;
                    } else {
                        i = i2;
                    }
                }
            }
        }

        public void log(String str) {
            synchronized (this) {
                if (this.mMaxLines > 0) {
                    int myPid = Process.myPid();
                    int myTid = Process.myTid();
                    this.mNow.setToNow();
                    this.mLog.add(this.mNow.format("%m-%d %H:%M:%S") + " pid=" + myPid + " tid=" + myTid + " " + str);
                    while (this.mLog.size() > this.mMaxLines) {
                        this.mLog.remove();
                    }
                }
            }
        }
    }

    private SubscriptionController(Context context) {
        logd("SubscriptionController init by Context");
        this.mContext = context;
        this.mCM = CallManager.getInstance();
        this.mTelephonyManager = TelephonyManager.from(this.mContext);
        if (ServiceManager.getService("isub") == null) {
            ServiceManager.addService("isub", this);
        }
        registerReceiverIfNeeded();
        logdl("[SubscriptionController] init by Context");
        this.mDataConnectionHandler = new DataConnectionHandler(this, null);
        this.mScheduler = DdsScheduler.getInstance();
        this.mSchedulerAc = new DdsSchedulerAc();
        this.mSchedulerAc.connect(this.mContext, this.mDataConnectionHandler, this.mScheduler.getHandler());
    }

    private SubscriptionController(Phone phone) {
        this.mContext = phone.getContext();
        this.mCM = CallManager.getInstance();
        if (ServiceManager.getService("isub") == null) {
            ServiceManager.addService("isub", this);
        }
        registerReceiverIfNeeded();
        logdl("[SubscriptionController] init by Phone");
    }

    private void broadcastDefaultDataSubIdChanged(int i) {
        logdl("[broadcastDefaultDataSubIdChanged] subId=" + i);
        Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");
        intent.addFlags(536870912);
        intent.putExtra("subscription", getDefaultDataSubId());
        this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void broadcastDefaultSmsSubIdChanged(int i) {
        logdl("[broadcastDefaultSmsSubIdChanged] subId=" + i);
        Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED");
        intent.addFlags(536870912);
        intent.putExtra("subscription", i);
        this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void broadcastDefaultVoiceSubIdChanged(int i) {
        logdl("[broadcastDefaultVoiceSubIdChanged] subId=" + i);
        Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED");
        intent.addFlags(536870912);
        intent.putExtra("subscription", i);
        this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void broadcastSimInfoContentChanged() {
        this.mContext.sendBroadcast(new Intent("android.intent.action.ACTION_SUBINFO_CONTENT_CHANGE"));
        this.mContext.sendBroadcast(new Intent("android.intent.action.ACTION_SUBINFO_RECORD_UPDATED"));
    }

    private boolean checkNotifyPermission(String str) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE") == 0) {
            return true;
        }
        logd("checkNotifyPermission Permission Denial: " + str + " from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
        return false;
    }

    private void enforceSubscriptionPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.READ_PHONE_STATE", "Requires READ_PHONE_STATE");
    }

    private int[] getDummySubIds(int i) {
        int activeSubInfoCountMax = getActiveSubInfoCountMax();
        if (activeSubInfoCountMax <= 0) {
            return null;
        }
        int[] iArr = new int[activeSubInfoCountMax];
        for (int i2 = 0; i2 < activeSubInfoCountMax; i2++) {
            iArr[i2] = DUMMY_SUB_ID_BASE + i;
        }
        logd("getDummySubIds: slotIdx=" + i + " return " + activeSubInfoCountMax + " DummySubIds with each subId=" + iArr[0]);
        return iArr;
    }

    public static SubscriptionController getInstance() {
        if (sInstance == null) {
            Log.wtf(LOG_TAG, "getInstance null");
        }
        return sInstance;
    }

    /* JADX WARNING: Removed duplicated region for block: B:18:0x0058  */
    private java.util.List<android.telephony.SubscriptionInfo> getSubInfo(java.lang.String r7, java.lang.Object r8) {
        /*
        r6 = this;
        r2 = 0;
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "selection:";
        r0 = r0.append(r1);
        r0 = r0.append(r7);
        r1 = " ";
        r0 = r0.append(r1);
        r0 = r0.append(r8);
        r0 = r0.toString();
        r6.logd(r0);
        if (r8 == 0) goto L_0x006a;
    L_0x0023:
        r0 = 1;
        r4 = new java.lang.String[r0];
        r0 = 0;
        r1 = r8.toString();
        r4[r0] = r1;
    L_0x002d:
        r0 = r6.mContext;
        r0 = r0.getContentResolver();
        r1 = android.telephony.SubscriptionManager.CONTENT_URI;
        r3 = r7;
        r5 = r2;
        r1 = r0.query(r1, r2, r3, r4, r5);
        if (r1 == 0) goto L_0x005c;
    L_0x003d:
        r0 = r2;
    L_0x003e:
        r2 = r1.moveToNext();	 Catch:{ all -> 0x0068 }
        if (r2 == 0) goto L_0x0062;
    L_0x0044:
        r2 = r6.getSubInfoRecord(r1);	 Catch:{ all -> 0x0068 }
        if (r2 == 0) goto L_0x003e;
    L_0x004a:
        if (r0 != 0) goto L_0x0051;
    L_0x004c:
        r0 = new java.util.ArrayList;	 Catch:{ all -> 0x0068 }
        r0.<init>();	 Catch:{ all -> 0x0068 }
    L_0x0051:
        r0.add(r2);	 Catch:{ all -> 0x0055 }
        goto L_0x003e;
    L_0x0055:
        r0 = move-exception;
    L_0x0056:
        if (r1 == 0) goto L_0x005b;
    L_0x0058:
        r1.close();
    L_0x005b:
        throw r0;
    L_0x005c:
        r0 = "Query fail";
        r6.logd(r0);	 Catch:{ all -> 0x0055 }
        r0 = r2;
    L_0x0062:
        if (r1 == 0) goto L_0x0067;
    L_0x0064:
        r1.close();
    L_0x0067:
        return r0;
    L_0x0068:
        r0 = move-exception;
        goto L_0x0056;
    L_0x006a:
        r4 = r2;
        goto L_0x002d;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.SubscriptionController.getSubInfo(java.lang.String, java.lang.Object):java.util.List");
    }

    private SubscriptionInfo getSubInfoRecord(Cursor cursor) {
        int i = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));
        String string = cursor.getString(cursor.getColumnIndexOrThrow("icc_id"));
        int i2 = cursor.getInt(cursor.getColumnIndexOrThrow("sim_id"));
        String string2 = cursor.getString(cursor.getColumnIndexOrThrow("display_name"));
        String string3 = cursor.getString(cursor.getColumnIndexOrThrow("carrier_name"));
        int i3 = cursor.getInt(cursor.getColumnIndexOrThrow("name_source"));
        int i4 = cursor.getInt(cursor.getColumnIndexOrThrow("color"));
        String string4 = cursor.getString(cursor.getColumnIndexOrThrow(IccProvider.STR_NUMBER));
        int i5 = cursor.getInt(cursor.getColumnIndexOrThrow("data_roaming"));
        Bitmap decodeResource = BitmapFactory.decodeResource(this.mContext.getResources(), 17302605);
        int i6 = cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.MCC));
        int i7 = cursor.getInt(cursor.getColumnIndexOrThrow(Carriers.MNC));
        String subscriptionCountryIso = getSubscriptionCountryIso(i);
        int i8 = cursor.getInt(cursor.getColumnIndexOrThrow("sub_state"));
        int i9 = cursor.getInt(cursor.getColumnIndexOrThrow("network_mode"));
        logd("[getSubInfoRecord] id:" + i + " iccid:" + string + " simSlotIndex:" + i2 + " displayName:" + string2 + " nameSource:" + i3 + " iconTint:" + i4 + " dataRoaming:" + i5 + " mcc:" + i6 + " mnc:" + i7 + " countIso:" + subscriptionCountryIso + " status:" + i8 + " nwMode:" + i9);
        String line1NumberForSubscriber = this.mTelephonyManager.getLine1NumberForSubscriber(i);
        if (TextUtils.isEmpty(line1NumberForSubscriber) || line1NumberForSubscriber.equals(string4)) {
            line1NumberForSubscriber = string4;
        } else {
            logd("Line1Number is different: " + line1NumberForSubscriber);
        }
        return new SubscriptionInfo(i, string, i2, string2, string3, i3, i4, line1NumberForSubscriber, i5, decodeResource, i6, i7, subscriptionCountryIso, i8, i9);
    }

    private String getSubscriptionCountryIso(int i) {
        int phoneId = getPhoneId(i);
        return phoneId < 0 ? "" : TelephonyManager.getTelephonyProperty(phoneId, "gsm.sim.operator.iso-country", "");
    }

    private int getUnusedColor() {
        int i = 0;
        List activeSubscriptionInfoList = getActiveSubscriptionInfoList();
        this.colorArr = this.mContext.getResources().getIntArray(17235978);
        if (activeSubscriptionInfoList != null) {
            int i2 = 0;
            while (i2 < this.colorArr.length) {
                int i3 = 0;
                while (i3 < activeSubscriptionInfoList.size() && this.colorArr[i2] != ((SubscriptionInfo) activeSubscriptionInfoList.get(i3)).getIconTint()) {
                    i3++;
                }
                if (i3 == activeSubscriptionInfoList.size()) {
                    return this.colorArr[i2];
                }
                i2++;
            }
            i = activeSubscriptionInfoList.size() % this.colorArr.length;
        }
        return this.colorArr[i];
    }

    public static SubscriptionController init(Context context, CommandsInterface[] commandsInterfaceArr) {
        SubscriptionController subscriptionController;
        synchronized (SubscriptionController.class) {
            try {
                if (sInstance == null) {
                    sInstance = new SubscriptionController(context);
                } else {
                    Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
                }
                subscriptionController = sInstance;
            } catch (Throwable th) {
                Class cls = SubscriptionController.class;
            }
        }
        return subscriptionController;
    }

    public static SubscriptionController init(Phone phone) {
        SubscriptionController subscriptionController;
        synchronized (SubscriptionController.class) {
            try {
                if (sInstance == null) {
                    sInstance = new SubscriptionController(phone);
                } else {
                    Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
                }
                subscriptionController = sInstance;
            } catch (Throwable th) {
                Class cls = SubscriptionController.class;
            }
        }
        return subscriptionController;
    }

    private boolean isActiveSubId(int i) {
        if (SubscriptionManager.isValidSubscriptionId(i)) {
            for (Entry value : mSlotIdxToSubId.entrySet()) {
                if (i == ((Integer) value.getValue()).intValue()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSubInfoReady() {
        return mSlotIdxToSubId.size() > 0;
    }

    private void logd(String str) {
        Rlog.d(LOG_TAG, str);
    }

    private void logdl(String str) {
        logd(str);
        this.mLocalLog.log(str);
    }

    private void loge(String str) {
        Rlog.e(LOG_TAG, str);
    }

    private void logel(String str) {
        loge(str);
        this.mLocalLog.log(str);
    }

    private void logv(String str) {
        Rlog.v(LOG_TAG, str);
    }

    private void logvl(String str) {
        logv(str);
        this.mLocalLog.log(str);
    }

    private static void printStackTrace(String str) {
        RuntimeException runtimeException = new RuntimeException();
        slogd("StackTrace - " + str);
        StackTraceElement[] stackTrace = runtimeException.getStackTrace();
        Object obj = 1;
        for (StackTraceElement stackTraceElement : stackTrace) {
            if (obj != null) {
                obj = null;
            } else {
                slogd(stackTraceElement.toString());
            }
        }
    }

    private void registerReceiverIfNeeded() {
        if (this.mContext.getPackageManager().resolveContentProvider(SubscriptionManager.CONTENT_URI.getAuthority(), 0) != null) {
            logd("registering SPN updated receiver");
            this.mContext.registerReceiver(this.mReceiver, new IntentFilter("android.provider.Telephony.SPN_STRINGS_UPDATED"));
        }
    }

    private int setCarrierText(String str, int i) {
        logd("[setCarrierText]+ text:" + str + " subId:" + i);
        enforceSubscriptionPermission();
        ContentValues contentValues = new ContentValues(1);
        contentValues.put("carrier_name", str);
        int update = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, contentValues, "_id=" + Integer.toString(i), null);
        notifySubscriptionInfoChanged();
        return update;
    }

    private void setDefaultFallbackSubId(int i) {
        if (i == Integer.MAX_VALUE) {
            throw new RuntimeException("setDefaultSubId called with DEFAULT_SUB_ID");
        }
        logdl("[setDefaultFallbackSubId] subId=" + i);
        if (SubscriptionManager.isValidSubscriptionId(i)) {
            int phoneId = getPhoneId(i);
            if (phoneId < 0 || (phoneId >= TelephonyManager.getDefault().getPhoneCount() && TelephonyManager.getDefault().getSimCount() != 1)) {
                logdl("[setDefaultFallbackSubId] not set invalid phoneId=" + phoneId + " subId=" + i);
                return;
            }
            logdl("[setDefaultFallbackSubId] set mDefaultFallbackSubId=" + i);
            mDefaultFallbackSubId = i;
            MccTable.updateMccMncConfiguration(this.mContext, TelephonyManager.getDefault().getSimOperator(phoneId), false);
            Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_SUBSCRIPTION_CHANGED");
            intent.addFlags(536870912);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneId, i);
            logdl("[setDefaultFallbackSubId] broadcast default subId changed phoneId=" + phoneId + " subId=" + i);
            this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    private boolean shouldDefaultBeCleared(List<SubscriptionInfo> list, int i) {
        logdl("[shouldDefaultBeCleared: subId] " + i);
        if (list == null) {
            logdl("[shouldDefaultBeCleared] return true no records subId=" + i);
            return true;
        } else if (SubscriptionManager.isValidSubscriptionId(i)) {
            for (SubscriptionInfo subscriptionId : list) {
                int subscriptionId2 = subscriptionId.getSubscriptionId();
                logdl("[shouldDefaultBeCleared] Record.id: " + subscriptionId2);
                if (subscriptionId2 == i) {
                    logdl("[shouldDefaultBeCleared] return false subId is active, subId=" + i);
                    return false;
                }
            }
            logdl("[shouldDefaultBeCleared] return true not active subId=" + i);
            return true;
        } else {
            logdl("[shouldDefaultBeCleared] return false only one subId, subId=" + i);
            return false;
        }
    }

    private static void slogd(String str) {
        Rlog.d(LOG_TAG, str);
    }

    private void updateAllDataConnectionTrackers() {
        int length = sProxyPhones.length;
        logdl("[updateAllDataConnectionTrackers] sProxyPhones.length=" + length);
        for (int i = 0; i < length; i++) {
            logdl("[updateAllDataConnectionTrackers] phoneId=" + i);
            sProxyPhones[i].updateDataConnectionTracker();
        }
    }

    private void updateDataSubId(AsyncResult asyncResult) {
        Integer num = (Integer) asyncResult.result;
        logd(" updateDataSubId,  subId=" + num + " exception " + asyncResult.exception);
        if (asyncResult.exception == null) {
            setDataSubId(num.intValue());
            this.mScheduler.updateCurrentDds(null);
            broadcastDefaultDataSubIdChanged(num.intValue());
            updateAllDataConnectionTrackers();
            return;
        }
        int defaultDataSubId = getDefaultDataSubId();
        logd("DDS switch failed, enforce last dds = " + defaultDataSubId);
        setDefaultDataSubId(defaultDataSubId);
    }

    private void validateSubId(int i) {
        logd("validateSubId subId: " + i);
        if (!SubscriptionManager.isValidSubscriptionId(i)) {
            throw new RuntimeException("Invalid sub id passed as parameter");
        } else if (i == Integer.MAX_VALUE) {
            throw new RuntimeException("Default sub id passed as parameter");
        }
    }

    public void activateSubId(int i) {
        if (getSubState(i) == 1) {
            logd("activateSubId: subscription already active, subId = " + i);
            return;
        }
        SubscriptionHelper.getInstance().setUiccSubscription(getSlotId(i), 1);
    }

    /* JADX WARNING: Removed duplicated region for block: B:18:0x00fe  */
    /* JADX WARNING: Removed duplicated region for block: B:21:0x0114 A:{SYNTHETIC, Splitter:B:21:0x0114} */
    /* JADX WARNING: Removed duplicated region for block: B:37:0x01ef  */
    public int addSubInfoRecord(java.lang.String r13, int r14) {
        /*
        r12 = this;
        r0 = -1;
        r11 = 2;
        r5 = 0;
        r10 = 1;
        r7 = 0;
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "[addSubInfoRecord]+ iccId:";
        r1 = r1.append(r2);
        r1 = r1.append(r13);
        r2 = " slotId:";
        r1 = r1.append(r2);
        r1 = r1.append(r14);
        r1 = r1.toString();
        r12.logdl(r1);
        r12.enforceSubscriptionPermission();
        if (r13 != 0) goto L_0x0030;
    L_0x002a:
        r1 = "[addSubInfoRecord]- null iccId";
        r12.logdl(r1);
    L_0x002f:
        return r0;
    L_0x0030:
        r1 = r12.getSubId(r14);
        if (r1 == 0) goto L_0x0039;
    L_0x0036:
        r2 = r1.length;
        if (r2 != 0) goto L_0x0050;
    L_0x0039:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "[addSubInfoRecord]- getSubId failed subIds == null || length == 0 subIds=";
        r2 = r2.append(r3);
        r1 = r2.append(r1);
        r1 = r1.toString();
        r12.logdl(r1);
        goto L_0x002f;
    L_0x0050:
        r0 = r12.mTelephonyManager;
        r1 = r1[r7];
        r0 = r0.getSimOperatorNameForSubscription(r1);
        r1 = android.text.TextUtils.isEmpty(r0);
        if (r1 != 0) goto L_0x0214;
    L_0x005e:
        r6 = r0;
    L_0x005f:
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "[addSubInfoRecord] sim name = ";
        r1 = r1.append(r2);
        r1 = r1.append(r6);
        r1 = r1.toString();
        r12.logdl(r1);
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "[addSubInfoRecord] carrier name = ";
        r1 = r1.append(r2);
        r0 = r1.append(r0);
        r0 = r0.toString();
        r12.logdl(r0);
        r0 = r12.mContext;
        r0 = r0.getContentResolver();
        r1 = android.telephony.SubscriptionManager.CONTENT_URI;
        r2 = 3;
        r2 = new java.lang.String[r2];
        r3 = "_id";
        r2[r7] = r3;
        r3 = "sim_id";
        r2[r10] = r3;
        r3 = "name_source";
        r2[r11] = r3;
        r3 = "icc_id=?";
        r4 = new java.lang.String[r10];
        r4[r7] = r13;
        r1 = r0.query(r1, r2, r3, r4, r5);
        r2 = r12.getUnusedColor();
        if (r1 == 0) goto L_0x00b8;
    L_0x00b2:
        r3 = r1.moveToFirst();	 Catch:{ all -> 0x0280 }
        if (r3 != 0) goto L_0x0230;
    L_0x00b8:
        r3 = new android.content.ContentValues;	 Catch:{ all -> 0x0280 }
        r3.<init>();	 Catch:{ all -> 0x0280 }
        r4 = "icc_id";
        r3.put(r4, r13);	 Catch:{ all -> 0x0280 }
        r4 = "color";
        r2 = java.lang.Integer.valueOf(r2);	 Catch:{ all -> 0x0280 }
        r3.put(r4, r2);	 Catch:{ all -> 0x0280 }
        r2 = "sim_id";
        r4 = java.lang.Integer.valueOf(r14);	 Catch:{ all -> 0x0280 }
        r3.put(r2, r4);	 Catch:{ all -> 0x0280 }
        r2 = "display_name";
        r3.put(r2, r6);	 Catch:{ all -> 0x0280 }
        r2 = "carrier_name";
        r4 = "";
        r3.put(r2, r4);	 Catch:{ all -> 0x0280 }
        r2 = android.telephony.SubscriptionManager.CONTENT_URI;	 Catch:{ all -> 0x0280 }
        r2 = r0.insert(r2, r3);	 Catch:{ all -> 0x0280 }
        r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0280 }
        r3.<init>();	 Catch:{ all -> 0x0280 }
        r4 = "[addSubInfoRecord] New record created: ";
        r3 = r3.append(r4);	 Catch:{ all -> 0x0280 }
        r2 = r3.append(r2);	 Catch:{ all -> 0x0280 }
        r2 = r2.toString();	 Catch:{ all -> 0x0280 }
        r12.logdl(r2);	 Catch:{ all -> 0x0280 }
    L_0x00fc:
        if (r1 == 0) goto L_0x0101;
    L_0x00fe:
        r1.close();
    L_0x0101:
        r1 = android.telephony.SubscriptionManager.CONTENT_URI;
        r3 = "sim_id=?";
        r4 = new java.lang.String[r10];
        r2 = java.lang.String.valueOf(r14);
        r4[r7] = r2;
        r2 = r5;
        r1 = r0.query(r1, r2, r3, r4, r5);
        if (r1 == 0) goto L_0x01ed;
    L_0x0114:
        r0 = r1.moveToFirst();	 Catch:{ all -> 0x028e }
        if (r0 == 0) goto L_0x01ed;
    L_0x011a:
        r0 = "_id";
        r0 = r1.getColumnIndexOrThrow(r0);	 Catch:{ all -> 0x028e }
        r2 = r1.getInt(r0);	 Catch:{ all -> 0x028e }
        r0 = mSlotIdxToSubId;	 Catch:{ all -> 0x028e }
        r3 = java.lang.Integer.valueOf(r14);	 Catch:{ all -> 0x028e }
        r0 = r0.get(r3);	 Catch:{ all -> 0x028e }
        r0 = (java.lang.Integer) r0;	 Catch:{ all -> 0x028e }
        if (r0 == 0) goto L_0x013c;
    L_0x0132:
        r0 = r0.intValue();	 Catch:{ all -> 0x028e }
        r0 = android.telephony.SubscriptionManager.isValidSubscriptionId(r0);	 Catch:{ all -> 0x028e }
        if (r0 != 0) goto L_0x0287;
    L_0x013c:
        r0 = mSlotIdxToSubId;	 Catch:{ all -> 0x028e }
        r3 = java.lang.Integer.valueOf(r14);	 Catch:{ all -> 0x028e }
        r4 = java.lang.Integer.valueOf(r2);	 Catch:{ all -> 0x028e }
        r0.put(r3, r4);	 Catch:{ all -> 0x028e }
        r0 = r12.getActiveSubInfoCountMax();	 Catch:{ all -> 0x028e }
        r3 = r12.getDefaultSubId();	 Catch:{ all -> 0x028e }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x028e }
        r4.<init>();	 Catch:{ all -> 0x028e }
        r5 = "[addSubInfoRecord] mSlotIdxToSubId.size=";
        r4 = r4.append(r5);	 Catch:{ all -> 0x028e }
        r5 = mSlotIdxToSubId;	 Catch:{ all -> 0x028e }
        r5 = r5.size();	 Catch:{ all -> 0x028e }
        r4 = r4.append(r5);	 Catch:{ all -> 0x028e }
        r5 = " slotId=";
        r4 = r4.append(r5);	 Catch:{ all -> 0x028e }
        r4 = r4.append(r14);	 Catch:{ all -> 0x028e }
        r5 = " subId=";
        r4 = r4.append(r5);	 Catch:{ all -> 0x028e }
        r4 = r4.append(r2);	 Catch:{ all -> 0x028e }
        r5 = " defaultSubId=";
        r4 = r4.append(r5);	 Catch:{ all -> 0x028e }
        r4 = r4.append(r3);	 Catch:{ all -> 0x028e }
        r5 = " simCount=";
        r4 = r4.append(r5);	 Catch:{ all -> 0x028e }
        r4 = r4.append(r0);	 Catch:{ all -> 0x028e }
        r4 = r4.toString();	 Catch:{ all -> 0x028e }
        r12.logdl(r4);	 Catch:{ all -> 0x028e }
        r3 = android.telephony.SubscriptionManager.isValidSubscriptionId(r3);	 Catch:{ all -> 0x028e }
        if (r3 == 0) goto L_0x019d;
    L_0x019b:
        if (r0 != r10) goto L_0x01a0;
    L_0x019d:
        r12.setDefaultFallbackSubId(r2);	 Catch:{ all -> 0x028e }
    L_0x01a0:
        if (r0 != r10) goto L_0x01c1;
    L_0x01a2:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x028e }
        r0.<init>();	 Catch:{ all -> 0x028e }
        r3 = "[addSubInfoRecord] one sim set defaults to subId=";
        r0 = r0.append(r3);	 Catch:{ all -> 0x028e }
        r0 = r0.append(r2);	 Catch:{ all -> 0x028e }
        r0 = r0.toString();	 Catch:{ all -> 0x028e }
        r12.logdl(r0);	 Catch:{ all -> 0x028e }
        r12.setDataSubId(r2);	 Catch:{ all -> 0x028e }
        r12.setDefaultSmsSubId(r2);	 Catch:{ all -> 0x028e }
        r12.setDefaultVoiceSubId(r2);	 Catch:{ all -> 0x028e }
    L_0x01c1:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x028e }
        r0.<init>();	 Catch:{ all -> 0x028e }
        r3 = "[addSubInfoRecord] hashmap(";
        r0 = r0.append(r3);	 Catch:{ all -> 0x028e }
        r0 = r0.append(r14);	 Catch:{ all -> 0x028e }
        r3 = ",";
        r0 = r0.append(r3);	 Catch:{ all -> 0x028e }
        r0 = r0.append(r2);	 Catch:{ all -> 0x028e }
        r2 = ")";
        r0 = r0.append(r2);	 Catch:{ all -> 0x028e }
        r0 = r0.toString();	 Catch:{ all -> 0x028e }
        r12.logdl(r0);	 Catch:{ all -> 0x028e }
        r0 = r1.moveToNext();	 Catch:{ all -> 0x028e }
        if (r0 != 0) goto L_0x011a;
    L_0x01ed:
        if (r1 == 0) goto L_0x01f2;
    L_0x01ef:
        r1.close();
    L_0x01f2:
        r12.updateAllDataConnectionTrackers();
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "[addSubInfoRecord]- info size=";
        r0 = r0.append(r1);
        r1 = mSlotIdxToSubId;
        r1 = r1.size();
        r0 = r0.append(r1);
        r0 = r0.toString();
        r12.logdl(r0);
        r0 = r7;
        goto L_0x002f;
    L_0x0214:
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "CARD ";
        r1 = r1.append(r2);
        r2 = r14 + 1;
        r2 = java.lang.Integer.toString(r2);
        r1 = r1.append(r2);
        r1 = r1.toString();
        r6 = r1;
        goto L_0x005f;
    L_0x0230:
        r2 = 0;
        r2 = r1.getInt(r2);	 Catch:{ all -> 0x0280 }
        r3 = 1;
        r3 = r1.getInt(r3);	 Catch:{ all -> 0x0280 }
        r4 = 2;
        r4 = r1.getInt(r4);	 Catch:{ all -> 0x0280 }
        r8 = new android.content.ContentValues;	 Catch:{ all -> 0x0280 }
        r8.<init>();	 Catch:{ all -> 0x0280 }
        if (r14 == r3) goto L_0x024f;
    L_0x0246:
        r3 = "sim_id";
        r9 = java.lang.Integer.valueOf(r14);	 Catch:{ all -> 0x0280 }
        r8.put(r3, r9);	 Catch:{ all -> 0x0280 }
    L_0x024f:
        if (r4 == r11) goto L_0x0256;
    L_0x0251:
        r3 = "display_name";
        r8.put(r3, r6);	 Catch:{ all -> 0x0280 }
    L_0x0256:
        r3 = r8.size();	 Catch:{ all -> 0x0280 }
        if (r3 <= 0) goto L_0x0279;
    L_0x025c:
        r3 = android.telephony.SubscriptionManager.CONTENT_URI;	 Catch:{ all -> 0x0280 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0280 }
        r4.<init>();	 Catch:{ all -> 0x0280 }
        r6 = "_id=";
        r4 = r4.append(r6);	 Catch:{ all -> 0x0280 }
        r2 = java.lang.Integer.toString(r2);	 Catch:{ all -> 0x0280 }
        r2 = r4.append(r2);	 Catch:{ all -> 0x0280 }
        r2 = r2.toString();	 Catch:{ all -> 0x0280 }
        r4 = 0;
        r0.update(r3, r8, r2, r4);	 Catch:{ all -> 0x0280 }
    L_0x0279:
        r2 = "[addSubInfoRecord] Record already exists";
        r12.logdl(r2);	 Catch:{ all -> 0x0280 }
        goto L_0x00fc;
    L_0x0280:
        r0 = move-exception;
        if (r1 == 0) goto L_0x0286;
    L_0x0283:
        r1.close();
    L_0x0286:
        throw r0;
    L_0x0287:
        r0 = "[addSubInfoRecord] currentSubId != null && currentSubId is valid, IGNORE";
        r12.logdl(r0);	 Catch:{ all -> 0x028e }
        goto L_0x01c1;
    L_0x028e:
        r0 = move-exception;
        if (r1 == 0) goto L_0x0294;
    L_0x0291:
        r1.close();
    L_0x0294:
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.SubscriptionController.addSubInfoRecord(java.lang.String, int):int");
    }

    public void clearDefaultsForInactiveSubIds() {
        List activeSubscriptionInfoList = getActiveSubscriptionInfoList();
        logdl("[clearDefaultsForInactiveSubIds] records: " + activeSubscriptionInfoList);
        if (shouldDefaultBeCleared(activeSubscriptionInfoList, getDefaultDataSubId())) {
            logd("[clearDefaultsForInactiveSubIds] clearing default data sub id");
            setDefaultDataSubId(-1);
        }
        if (shouldDefaultBeCleared(activeSubscriptionInfoList, getDefaultSmsSubId())) {
            logdl("[clearDefaultsForInactiveSubIds] clearing default sms sub id");
            setDefaultSmsSubId(-1);
        }
        if (shouldDefaultBeCleared(activeSubscriptionInfoList, getDefaultVoiceSubId())) {
            logdl("[clearDefaultsForInactiveSubIds] clearing default voice sub id");
            setDefaultVoiceSubId(-1);
        }
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

    public void deactivateSubId(int i) {
        if (getSubState(i) == 0) {
            logd("activateSubId: subscription already deactivated, subId = " + i);
            return;
        }
        SubscriptionHelper.getInstance().setUiccSubscription(getSlotId(i), 0);
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.DUMP", "Requires DUMP");
        long clearCallingIdentity = Binder.clearCallingIdentity();
        try {
            printWriter.println("SubscriptionController:");
            printWriter.println(" defaultSubId=" + getDefaultSubId());
            printWriter.println(" defaultDataSubId=" + getDefaultDataSubId());
            printWriter.println(" defaultVoiceSubId=" + getDefaultVoiceSubId());
            printWriter.println(" defaultSmsSubId=" + getDefaultSmsSubId());
            printWriter.println(" defaultDataPhoneId=" + SubscriptionManager.from(this.mContext).getDefaultDataPhoneId());
            printWriter.println(" defaultVoicePhoneId=" + SubscriptionManager.getDefaultVoicePhoneId());
            printWriter.println(" defaultSmsPhoneId=" + SubscriptionManager.from(this.mContext).getDefaultSmsPhoneId());
            printWriter.flush();
            for (Entry entry : mSlotIdxToSubId.entrySet()) {
                printWriter.println(" mSlotIdToSubIdMap[" + entry.getKey() + "]: subId=" + entry.getValue());
            }
            printWriter.flush();
            printWriter.println("++++++++++++++++++++++++++++++++");
            List<SubscriptionInfo> activeSubscriptionInfoList = getActiveSubscriptionInfoList();
            if (activeSubscriptionInfoList != null) {
                printWriter.println(" ActiveSubInfoList:");
                for (SubscriptionInfo subscriptionInfo : activeSubscriptionInfoList) {
                    printWriter.println("  " + subscriptionInfo.toString());
                }
            } else {
                printWriter.println(" ActiveSubInfoList: is null");
            }
            printWriter.flush();
            printWriter.println("++++++++++++++++++++++++++++++++");
            activeSubscriptionInfoList = getAllSubInfoList();
            if (activeSubscriptionInfoList != null) {
                printWriter.println(" AllSubInfoList:");
                for (SubscriptionInfo subscriptionInfo2 : activeSubscriptionInfoList) {
                    printWriter.println("  " + subscriptionInfo2.toString());
                }
            } else {
                printWriter.println(" AllSubInfoList: is null");
            }
            printWriter.flush();
            printWriter.println("++++++++++++++++++++++++++++++++");
            this.mLocalLog.dump(fileDescriptor, printWriter, strArr);
            printWriter.flush();
            printWriter.println("++++++++++++++++++++++++++++++++");
            printWriter.flush();
        } finally {
            Binder.restoreCallingIdentity(clearCallingIdentity);
        }
    }

    public int[] getActivatedSubIdList() {
        Set entrySet = mSlotIdxToSubId.entrySet();
        logd("getActivatedSubIdList: simInfoSet=" + entrySet);
        int[] iArr = new int[entrySet.size()];
        int i = 0;
        Iterator it = entrySet.iterator();
        while (true) {
            int i2 = i;
            if (it.hasNext()) {
                iArr[i2] = ((Integer) ((Entry) it.next()).getValue()).intValue();
                i = i2 + 1;
            } else {
                logd("getActivatedSubIdList: X subIdArr.length=" + iArr.length);
                return iArr;
            }
        }
    }

    public int[] getActiveSubIdList() {
        Set entrySet = mSlotIdxToSubId.entrySet();
        logdl("[getActiveSubIdList] simInfoSet=" + entrySet);
        int[] iArr = new int[entrySet.size()];
        int i = 0;
        Iterator it = entrySet.iterator();
        while (true) {
            int i2 = i;
            if (it.hasNext()) {
                iArr[i2] = ((Integer) ((Entry) it.next()).getValue()).intValue();
                i = i2 + 1;
            } else {
                logdl("[getActiveSubIdList] X subIdArr.length=" + iArr.length);
                return iArr;
            }
        }
    }

    public int getActiveSubInfoCount() {
        logd("[getActiveSubInfoCount]+");
        List activeSubscriptionInfoList = getActiveSubscriptionInfoList();
        if (activeSubscriptionInfoList == null) {
            logd("[getActiveSubInfoCount] records null");
            return 0;
        }
        logd("[getActiveSubInfoCount]- count: " + activeSubscriptionInfoList.size());
        return activeSubscriptionInfoList.size();
    }

    public int getActiveSubInfoCountMax() {
        return this.mTelephonyManager.getSimCount();
    }

    public SubscriptionInfo getActiveSubscriptionInfo(int i) {
        enforceSubscriptionPermission();
        if (SubscriptionManager.isValidSubscriptionId(i) && isSubInfoReady()) {
            List<SubscriptionInfo> activeSubscriptionInfoList = getActiveSubscriptionInfoList();
            if (activeSubscriptionInfoList != null) {
                for (SubscriptionInfo subscriptionInfo : activeSubscriptionInfoList) {
                    if (subscriptionInfo.getSubscriptionId() == i) {
                        logd("[getActiveSubInfoForSubscriber]+ subId=" + i + " subInfo=" + subscriptionInfo);
                        return subscriptionInfo;
                    }
                }
            }
            logd("[getActiveSubInfoForSubscriber]- subId=" + i + " subList=" + activeSubscriptionInfoList + " subInfo=null");
            return null;
        }
        logd("[getSubInfoUsingSubIdx]- invalid subId or not ready = " + i);
        return null;
    }

    public SubscriptionInfo getActiveSubscriptionInfoForIccId(String str) {
        enforceSubscriptionPermission();
        List<SubscriptionInfo> activeSubscriptionInfoList = getActiveSubscriptionInfoList();
        if (activeSubscriptionInfoList != null) {
            for (SubscriptionInfo subscriptionInfo : activeSubscriptionInfoList) {
                if (subscriptionInfo.getIccId() == str) {
                    logd("[getActiveSubInfoUsingIccId]+ iccId=" + str + " subInfo=" + subscriptionInfo);
                    return subscriptionInfo;
                }
            }
        }
        logd("[getActiveSubInfoUsingIccId]+ iccId=" + str + " subList=" + activeSubscriptionInfoList + " subInfo=null");
        return null;
    }

    public SubscriptionInfo getActiveSubscriptionInfoForSimSlotIndex(int i) {
        enforceSubscriptionPermission();
        List<SubscriptionInfo> activeSubscriptionInfoList = getActiveSubscriptionInfoList();
        if (activeSubscriptionInfoList != null) {
            for (SubscriptionInfo subscriptionInfo : activeSubscriptionInfoList) {
                if (subscriptionInfo.getSimSlotIndex() == i) {
                    logd("[getActiveSubscriptionInfoForSimSlotIndex]+ slotIdx=" + i + " subId=" + subscriptionInfo);
                    return subscriptionInfo;
                }
            }
            logd("[getActiveSubscriptionInfoForSimSlotIndex]+ slotIdx=" + i + " subId=null");
        } else {
            logd("[getActiveSubscriptionInfoForSimSlotIndex]+ subList=null");
        }
        return null;
    }

    public List<SubscriptionInfo> getActiveSubscriptionInfoList() {
        List<SubscriptionInfo> list = null;
        enforceSubscriptionPermission();
        logdl("[getActiveSubInfoList]+");
        if (isSubInfoReady()) {
            list = getSubInfo("sim_id>=0", null);
            if (list != null) {
                Collections.sort(list, new Comparator<SubscriptionInfo>() {
                    public int compare(SubscriptionInfo subscriptionInfo, SubscriptionInfo subscriptionInfo2) {
                        int simSlotIndex = subscriptionInfo.getSimSlotIndex() - subscriptionInfo2.getSimSlotIndex();
                        return simSlotIndex == 0 ? subscriptionInfo.getSubscriptionId() - subscriptionInfo2.getSubscriptionId() : simSlotIndex;
                    }
                });
                logdl("[getActiveSubInfoList]- " + list.size() + " infos return");
            } else {
                logdl("[getActiveSubInfoList]- no info return");
            }
        } else {
            logdl("[getActiveSubInfoList] Sub Controller not ready");
        }
        return list;
    }

    public int getAllSubInfoCount() {
        logd("[getAllSubInfoCount]+");
        enforceSubscriptionPermission();
        Cursor query = this.mContext.getContentResolver().query(SubscriptionManager.CONTENT_URI, null, null, null, null);
        if (query != null) {
            try {
                int count = query.getCount();
                logd("[getAllSubInfoCount]- " + count + " SUB(s) in DB");
                return count;
            } finally {
                if (query != null) {
                    query.close();
                }
            }
        } else {
            if (query != null) {
                query.close();
            }
            logd("[getAllSubInfoCount]- no SUB in DB");
            return 0;
        }
    }

    public List<SubscriptionInfo> getAllSubInfoList() {
        logd("[getAllSubInfoList]+");
        enforceSubscriptionPermission();
        List subInfo = getSubInfo(null, null);
        if (subInfo != null) {
            logd("[getAllSubInfoList]- " + subInfo.size() + " infos return");
        } else {
            logd("[getAllSubInfoList]- no info return");
        }
        return subInfo;
    }

    public int getCurrentDds() {
        return this.mScheduler.getCurrentDds();
    }

    public int getDefaultDataSubId() {
        return Global.getInt(this.mContext.getContentResolver(), "multi_sim_data_call", -1);
    }

    public int getDefaultSmsSubId() {
        return Global.getInt(this.mContext.getContentResolver(), "multi_sim_sms", -1);
    }

    public int getDefaultSubId() {
        int defaultVoiceSubId = this.mContext.getResources().getBoolean(17956947) ? getDefaultVoiceSubId() : getDefaultDataSubId();
        return !isActiveSubId(defaultVoiceSubId) ? mDefaultFallbackSubId : defaultVoiceSubId;
    }

    public int getDefaultVoiceSubId() {
        return Global.getInt(this.mContext.getContentResolver(), "multi_sim_voice_call", -1);
    }

    public int getNwMode(int i) {
        SubscriptionInfo activeSubscriptionInfo = getActiveSubscriptionInfo(i);
        if (activeSubscriptionInfo != null) {
            return activeSubscriptionInfo.mNwMode;
        }
        loge("getSubState: invalid subId = " + i);
        return -1;
    }

    public int getOnDemandDataSubId() {
        return getCurrentDds();
    }

    public int getPhoneId(int i) {
        if (i == Integer.MAX_VALUE) {
            i = getDefaultSubId();
            logdl("[getPhoneId] asked for default subId=" + i);
        }
        int i2;
        if (!SubscriptionManager.isValidSubscriptionId(i)) {
            logdl("[getPhoneId]- invalid subId return=-1");
            return -1;
        } else if (i >= DUMMY_SUB_ID_BASE) {
            logd("getPhoneId,  received dummy subId " + i);
            return i - DUMMY_SUB_ID_BASE;
        } else if (mSlotIdxToSubId.size() == 0) {
            i2 = mDefaultPhoneId;
            logdl("[getPhoneId]- no sims, returning default phoneId=" + i2);
            return i2;
        } else {
            for (Entry entry : mSlotIdxToSubId.entrySet()) {
                int intValue = ((Integer) entry.getKey()).intValue();
                if (i == ((Integer) entry.getValue()).intValue()) {
                    logd("[getPhoneId]- return =" + intValue);
                    return intValue;
                }
            }
            i2 = mDefaultPhoneId;
            logdl("[getPhoneId]- subId=" + i + " not found return default phoneId=" + i2);
            return i2;
        }
    }

    public int getSimStateForSubscriber(int i) {
        Object obj;
        String str;
        int phoneId = getPhoneId(i);
        if (phoneId < 0) {
            obj = State.UNKNOWN;
            str = "invalid PhoneIdx";
        } else {
            Phone phone = PhoneFactory.getPhone(phoneId);
            if (phone == null) {
                obj = State.UNKNOWN;
                str = "phone == null";
            } else {
                IccCard iccCard = phone.getIccCard();
                if (iccCard == null) {
                    obj = State.UNKNOWN;
                    str = "icc == null";
                } else {
                    obj = iccCard.getState();
                    str = "";
                }
            }
        }
        logd("getSimStateForSubscriber: " + str + " simState=" + obj + " ordinal=" + obj.ordinal());
        return obj.ordinal();
    }

    public int getSlotId(int i) {
        if (i == Integer.MAX_VALUE) {
            i = getDefaultSubId();
        }
        if (!SubscriptionManager.isValidSubscriptionId(i)) {
            logd("[getSlotId]- subId invalid");
            return -1;
        } else if (i >= DUMMY_SUB_ID_BASE) {
            logd("getSlotId,  received dummy subId " + i);
            return i - DUMMY_SUB_ID_BASE;
        } else if (mSlotIdxToSubId.size() == 0) {
            logd("[getSlotId]- size == 0, return SIM_NOT_INSERTED instead");
            return -1;
        } else {
            for (Entry entry : mSlotIdxToSubId.entrySet()) {
                int intValue = ((Integer) entry.getKey()).intValue();
                if (i == ((Integer) entry.getValue()).intValue()) {
                    return intValue;
                }
            }
            logd("[getSlotId]- return fail");
            return -1;
        }
    }

    @Deprecated
    public int[] getSubId(int i) {
        if (i == Integer.MAX_VALUE) {
            i = getSlotId(getDefaultSubId());
            logd("[getSubId] map default slotIdx=" + i);
        }
        if (!SubscriptionManager.isValidSlotId(i)) {
            logd("[getSubId]- invalid slotIdx=" + i);
            return null;
        } else if (mSlotIdxToSubId.size() == 0) {
            logd("[getSubId]- mSlotIdToSubIdMap.size == 0, return DummySubIds slotIdx=" + i);
            return getDummySubIds(i);
        } else {
            ArrayList arrayList = new ArrayList();
            for (Entry entry : mSlotIdxToSubId.entrySet()) {
                int intValue = ((Integer) entry.getKey()).intValue();
                int intValue2 = ((Integer) entry.getValue()).intValue();
                if (i == intValue) {
                    arrayList.add(Integer.valueOf(intValue2));
                }
            }
            int size = arrayList.size();
            if (size > 0) {
                int[] iArr = new int[size];
                for (int i2 = 0; i2 < size; i2++) {
                    iArr[i2] = ((Integer) arrayList.get(i2)).intValue();
                }
                return iArr;
            }
            logd("[getSubId]- numSubIds == 0, return DummySubIds slotIdx=" + i);
            return getDummySubIds(i);
        }
    }

    public int getSubIdFromNetworkRequest(NetworkRequest networkRequest) {
        if (networkRequest == null) {
            return getDefaultDataSubId();
        }
        try {
            return Integer.parseInt(networkRequest.networkCapabilities.getNetworkSpecifier());
        } catch (NumberFormatException e) {
            loge("Exception e = " + e);
            return getDefaultDataSubId();
        }
    }

    public int getSubIdUsingPhoneId(int i) {
        int[] subId = getSubId(i);
        return (subId == null || subId.length == 0) ? -1 : subId[0];
    }

    public int[] getSubIdUsingSlotId(int i) {
        return getSubId(i);
    }

    /* JADX WARNING: Removed duplicated region for block: B:26:0x0077  */
    public java.util.List<android.telephony.SubscriptionInfo> getSubInfoUsingSlotIdWithCheck(int r8, boolean r9) {
        /*
        r7 = this;
        r2 = 0;
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "[getSubInfoUsingSlotIdWithCheck]+ slotId:";
        r0 = r0.append(r1);
        r0 = r0.append(r8);
        r0 = r0.toString();
        r7.logd(r0);
        r7.enforceSubscriptionPermission();
        r0 = 2147483647; // 0x7fffffff float:NaN double:1.060997895E-314;
        if (r8 != r0) goto L_0x0027;
    L_0x001f:
        r0 = r7.getDefaultSubId();
        r8 = r7.getSlotId(r0);
    L_0x0027:
        r0 = android.telephony.SubscriptionManager.isValidSlotId(r8);
        if (r0 != 0) goto L_0x0033;
    L_0x002d:
        r0 = "[getSubInfoUsingSlotIdWithCheck]- invalid slotId";
        r7.logd(r0);
    L_0x0032:
        return r2;
    L_0x0033:
        if (r9 == 0) goto L_0x0041;
    L_0x0035:
        r0 = r7.isSubInfoReady();
        if (r0 != 0) goto L_0x0041;
    L_0x003b:
        r0 = "[getSubInfoUsingSlotIdWithCheck]- not ready";
        r7.logd(r0);
        goto L_0x0032;
    L_0x0041:
        r0 = r7.mContext;
        r0 = r0.getContentResolver();
        r1 = android.telephony.SubscriptionManager.CONTENT_URI;
        r3 = "sim_id=?";
        r4 = 1;
        r4 = new java.lang.String[r4];
        r5 = 0;
        r6 = java.lang.String.valueOf(r8);
        r4[r5] = r6;
        r5 = r2;
        r1 = r0.query(r1, r2, r3, r4, r5);
        if (r1 == 0) goto L_0x007c;
    L_0x005c:
        r0 = r2;
    L_0x005d:
        r2 = r1.moveToNext();	 Catch:{ all -> 0x0087 }
        if (r2 == 0) goto L_0x007b;
    L_0x0063:
        r2 = r7.getSubInfoRecord(r1);	 Catch:{ all -> 0x0087 }
        if (r2 == 0) goto L_0x005d;
    L_0x0069:
        if (r0 != 0) goto L_0x0070;
    L_0x006b:
        r0 = new java.util.ArrayList;	 Catch:{ all -> 0x0087 }
        r0.<init>();	 Catch:{ all -> 0x0087 }
    L_0x0070:
        r0.add(r2);	 Catch:{ all -> 0x0074 }
        goto L_0x005d;
    L_0x0074:
        r0 = move-exception;
    L_0x0075:
        if (r1 == 0) goto L_0x007a;
    L_0x0077:
        r1.close();
    L_0x007a:
        throw r0;
    L_0x007b:
        r2 = r0;
    L_0x007c:
        if (r1 == 0) goto L_0x0081;
    L_0x007e:
        r1.close();
    L_0x0081:
        r0 = "[getSubInfoUsingSlotId]- null info return";
        r7.logd(r0);
        goto L_0x0032;
    L_0x0087:
        r0 = move-exception;
        goto L_0x0075;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.SubscriptionController.getSubInfoUsingSlotIdWithCheck(int, boolean):java.util.List");
    }

    public int getSubState(int i) {
        SubscriptionInfo activeSubscriptionInfo = getActiveSubscriptionInfo(i);
        return (activeSubscriptionInfo == null || activeSubscriptionInfo.getSimSlotIndex() < 0) ? 0 : activeSubscriptionInfo.mStatus;
    }

    public boolean isSMSPromptEnabled() {
        int i;
        try {
            i = Global.getInt(this.mContext.getContentResolver(), "multi_sim_sms_prompt");
        } catch (SettingNotFoundException e) {
            loge("Settings Exception Reading Dual Sim SMS Prompt Values");
            i = 0;
        }
        return i != 0;
    }

    public boolean isVoicePromptEnabled() {
        int i;
        try {
            i = Global.getInt(this.mContext.getContentResolver(), "multi_sim_voice_prompt");
        } catch (SettingNotFoundException e) {
            loge("Settings Exception Reading Dual Sim Voice Prompt Values");
            i = 0;
        }
        return i != 0;
    }

    public void notifyOnDemandDataSubIdChanged(NetworkRequest networkRequest) {
        OnDemandDdsLockNotifier onDemandDdsLockNotifier = (OnDemandDdsLockNotifier) this.mOnDemandDdsLockNotificationRegistrants.get(Integer.valueOf(getSubIdFromNetworkRequest(networkRequest)));
        if (onDemandDdsLockNotifier != null) {
            onDemandDdsLockNotifier.notifyOnDemandDdsLockGranted(networkRequest);
        } else {
            logd("No registrants for OnDemandDdsLockGranted event");
        }
    }

    public void notifySubscriptionInfoChanged() {
        if (checkNotifyPermission("notifySubscriptionInfoChanged")) {
            ITelephonyRegistry asInterface = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService("telephony.registry"));
            try {
                logd("notifySubscriptionInfoChanged:");
                asInterface.notifySubscriptionInfoChanged();
            } catch (RemoteException e) {
            }
            broadcastSimInfoContentChanged();
        }
    }

    public void registerForOnDemandDdsLockNotification(int i, OnDemandDdsLockNotifier onDemandDdsLockNotifier) {
        logd("registerForOnDemandDdsLockNotification for client=" + i);
        this.mOnDemandDdsLockNotificationRegistrants.put(Integer.valueOf(i), onDemandDdsLockNotifier);
    }

    public void removeStaleSubPreferences(String str) {
        List<SubscriptionInfo> allSubInfoList = getAllSubInfoList();
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        for (SubscriptionInfo subscriptionInfo : allSubInfoList) {
            if (subscriptionInfo.getSimSlotIndex() == -1) {
                defaultSharedPreferences.edit().remove(str + subscriptionInfo.getSubscriptionId()).commit();
            }
        }
    }

    public int setDataRoaming(int i, int i2) {
        logd("[setDataRoaming]+ roaming:" + i + " subId:" + i2);
        enforceSubscriptionPermission();
        validateSubId(i2);
        if (i < 0) {
            logd("[setDataRoaming]- fail");
            return -1;
        }
        ContentValues contentValues = new ContentValues(1);
        contentValues.put("data_roaming", Integer.valueOf(i));
        logd("[setDataRoaming]- roaming:" + i + " set");
        int update = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, contentValues, "_id=" + Integer.toString(i2), null);
        notifySubscriptionInfoChanged();
        return update;
    }

    public void setDataSubId(int i) {
        Global.putInt(this.mContext.getContentResolver(), "multi_sim_data_call", i);
    }

    public void setDefaultDataSubId(int i) {
        if (i == Integer.MAX_VALUE) {
            throw new RuntimeException("setDefaultDataSubId called with DEFAULT_SUB_ID");
        }
        logdl("[setDefaultDataSubId] subId=" + i);
        if (this.mDctController == null) {
            this.mDctController = DctController.getInstance();
            this.mDctController.registerForDefaultDataSwitchInfo(this.mDataConnectionHandler, 1, null);
        }
        this.mDctController.setDefaultDataSubId(i);
    }

    public void setDefaultSmsSubId(int i) {
        if (i == Integer.MAX_VALUE) {
            throw new RuntimeException("setDefaultSmsSubId called with DEFAULT_SUB_ID");
        }
        logdl("[setDefaultSmsSubId] subId=" + i);
        Global.putInt(this.mContext.getContentResolver(), "multi_sim_sms", i);
        broadcastDefaultSmsSubIdChanged(i);
    }

    public void setDefaultVoiceSubId(int i) {
        if (i == Integer.MAX_VALUE) {
            throw new RuntimeException("setDefaultVoiceSubId called with DEFAULT_SUB_ID");
        }
        logdl("[setDefaultVoiceSubId] subId=" + i);
        Global.putInt(this.mContext.getContentResolver(), "multi_sim_voice_call", i);
        broadcastDefaultVoiceSubIdChanged(i);
    }

    public int setDisplayName(String str, int i) {
        return setDisplayNameUsingSrc(str, i, -1);
    }

    public int setDisplayNameUsingSrc(String str, int i, long j) {
        logd("[setDisplayName]+  displayName:" + str + " subId:" + i + " nameSource:" + j);
        enforceSubscriptionPermission();
        validateSubId(i);
        if (str == null) {
            str = this.mContext.getString(17039374);
        }
        ContentValues contentValues = new ContentValues(1);
        contentValues.put("display_name", str);
        if (j >= 0) {
            logd("Set nameSource=" + j);
            contentValues.put("name_source", Long.valueOf(j));
        }
        logd("[setDisplayName]- mDisplayName:" + str + " set");
        int update = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, contentValues, "_id=" + Integer.toString(i), null);
        notifySubscriptionInfoChanged();
        return update;
    }

    public int setDisplayNumber(String str, int i) {
        logd("[setDisplayNumber]+ number:" + str + " subId:" + i);
        enforceSubscriptionPermission();
        validateSubId(i);
        int phoneId = getPhoneId(i);
        if (str == null || phoneId < 0 || phoneId >= TelephonyManager.getDefault().getPhoneCount()) {
            logd("[setDispalyNumber]- fail");
            return -1;
        }
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(IccProvider.STR_NUMBER, str);
        phoneId = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, contentValues, "_id=" + Integer.toString(i), null);
        logd("[setDisplayNumber]- number: " + str + " update result :" + phoneId);
        notifySubscriptionInfoChanged();
        return phoneId;
    }

    public int setIconTint(int i, int i2) {
        logd("[setIconTint]+ tint:" + i + " subId:" + i2);
        enforceSubscriptionPermission();
        validateSubId(i2);
        ContentValues contentValues = new ContentValues(1);
        contentValues.put("color", Integer.valueOf(i));
        logd("[setIconTint]- tint:" + i + " set");
        int update = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, contentValues, "_id=" + Integer.toString(i2), null);
        notifySubscriptionInfoChanged();
        return update;
    }

    public int setMccMnc(String str, int i) {
        int parseInt;
        ContentValues contentValues;
        int i2 = 0;
        try {
            parseInt = Integer.parseInt(str.substring(0, 3));
            try {
                i2 = Integer.parseInt(str.substring(3));
            } catch (NumberFormatException e) {
            }
        } catch (NumberFormatException e2) {
            parseInt = i2;
            loge("[setMccMnc] - couldn't parse mcc/mnc: " + str);
            logd("[setMccMnc]+ mcc/mnc:" + parseInt + "/" + i2 + " subId:" + i);
            contentValues = new ContentValues(2);
            contentValues.put(Carriers.MCC, Integer.valueOf(parseInt));
            contentValues.put(Carriers.MNC, Integer.valueOf(i2));
            i2 = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, contentValues, "_id=" + Integer.toString(i), null);
            notifySubscriptionInfoChanged();
            return i2;
        }
        logd("[setMccMnc]+ mcc/mnc:" + parseInt + "/" + i2 + " subId:" + i);
        contentValues = new ContentValues(2);
        contentValues.put(Carriers.MCC, Integer.valueOf(parseInt));
        contentValues.put(Carriers.MNC, Integer.valueOf(i2));
        i2 = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, contentValues, "_id=" + Integer.toString(i), null);
        notifySubscriptionInfoChanged();
        return i2;
    }

    public void setNwMode(int i, int i2) {
        logd("setNwMode, nwMode: " + i2 + " subId: " + i);
        ContentValues contentValues = new ContentValues(1);
        contentValues.put("network_mode", Integer.valueOf(i2));
        this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, contentValues, "_id=" + Integer.toString(i), null);
    }

    public boolean setPlmnSpn(int i, boolean z, String str, boolean z2, String str2) {
        int[] subId = getSubId(i);
        if (this.mContext.getPackageManager().resolveContentProvider(SubscriptionManager.CONTENT_URI.getAuthority(), 0) == null || subId == null || !SubscriptionManager.isValidSubscriptionId(subId[0])) {
            logd("[setPlmnSpn] No valid subscription to store info");
            notifySubscriptionInfoChanged();
            return false;
        }
        String str3 = "";
        if (!z) {
            str = z2 ? str2 : str3;
        } else if (z2) {
            str = str + this.mContext.getString(17041030).toString() + str2;
        }
        for (int carrierText : subId) {
            setCarrierText(str, carrierText);
        }
        return true;
    }

    public void setSMSPromptEnabled(boolean z) {
        Global.putInt(this.mContext.getContentResolver(), "multi_sim_sms_prompt", !z ? 0 : 1);
        logd("setSMSPromptOption to " + z);
    }

    public int setSubState(int i, int i2) {
        int i3 = 0;
        logd("setSubState, subStatus: " + i2 + " subId: " + i);
        if (ModemStackController.getInstance().isStackReady()) {
            ContentValues contentValues = new ContentValues(1);
            contentValues.put("sub_state", Integer.valueOf(i2));
            i3 = this.mContext.getContentResolver().update(SubscriptionManager.CONTENT_URI, contentValues, "_id=" + Integer.toString(i), null);
        }
        Intent intent = new Intent("android.intent.action.ACTION_SUBINFO_CONTENT_CHANGE");
        intent.putExtra("_id", i);
        intent.putExtra("columnName", "sub_state");
        intent.putExtra("intContent", i2);
        intent.putExtra("stringContent", "None");
        this.mContext.sendBroadcast(intent);
        this.mContext.sendBroadcast(new Intent("android.intent.action.ACTION_SUBINFO_RECORD_UPDATED"));
        return i3;
    }

    public void setVoicePromptEnabled(boolean z) {
        Global.putInt(this.mContext.getContentResolver(), "multi_sim_voice_prompt", !z ? 0 : 1);
        logd("setVoicePromptOption to " + z);
    }

    public void startOnDemandDataSubscriptionRequest(NetworkRequest networkRequest) {
        logd("startOnDemandDataSubscriptionRequest = " + networkRequest);
        this.mSchedulerAc.allocateDds(networkRequest);
    }

    public void stopOnDemandDataSubscriptionRequest(NetworkRequest networkRequest) {
        logd("stopOnDemandDataSubscriptionRequest = " + networkRequest);
        this.mSchedulerAc.freeDds(networkRequest);
    }

    public void updatePhonesAvailability(PhoneProxy[] phoneProxyArr) {
        sProxyPhones = phoneProxyArr;
    }

    public void updateUserPrefs(boolean z) {
        List<SubscriptionInfo> activeSubscriptionInfoList = getActiveSubscriptionInfoList();
        if (activeSubscriptionInfoList == null) {
            int[] dummySubIds = getDummySubIds(mDefaultPhoneId);
            logd("updateUserPrefs: subscription are not avaiable dds = " + getDefaultDataSubId() + " voice = " + getDefaultVoiceSubId() + " sms = " + getDefaultSmsSubId() + " setDDs = " + z);
            setDefaultFallbackSubId(dummySubIds[0]);
            setDefaultVoiceSubId(dummySubIds[0]);
            setDefaultSmsSubId(dummySubIds[0]);
            setDataSubId(dummySubIds[0]);
            return;
        }
        int i = 0;
        SubscriptionInfo subscriptionInfo = null;
        for (SubscriptionInfo subscriptionInfo2 : activeSubscriptionInfoList) {
            if (getSubState(subscriptionInfo2.getSubscriptionId()) == 1) {
                i++;
                if (subscriptionInfo == null) {
                    subscriptionInfo = subscriptionInfo2;
                }
            }
        }
        logd("updateUserPrefs: active sub count = " + i + " dds = " + getDefaultDataSubId() + " voice = " + getDefaultVoiceSubId() + " sms = " + getDefaultSmsSubId() + " setDDs = " + z);
        if (i < 2) {
            setSMSPromptEnabled(false);
            setVoicePromptEnabled(false);
        }
        if (subscriptionInfo != null) {
            if (getSubState(getDefaultSubId()) == 0) {
                setDefaultFallbackSubId(subscriptionInfo.getSubscriptionId());
            }
            int defaultDataSubId = getDefaultDataSubId();
            i = getSubState(defaultDataSubId);
            if (z || i == 0) {
                if (i == 0) {
                    defaultDataSubId = subscriptionInfo.getSubscriptionId();
                }
                setDefaultDataSubId(defaultDataSubId);
            }
            if (getSubState(getDefaultVoiceSubId()) == 0 && !isVoicePromptEnabled()) {
                setDefaultVoiceSubId(subscriptionInfo.getSubscriptionId());
            }
            if (getSubState(getDefaultSmsSubId()) == 0 && !isSMSPromptEnabled()) {
                setDefaultSmsSubId(subscriptionInfo.getSubscriptionId());
            }
            logd("updateUserPrefs: after currentDds = " + getDefaultDataSubId() + " voice = " + getDefaultVoiceSubId() + " sms = " + getDefaultSmsSubId() + " newDds = " + defaultDataSubId);
        }
    }
}
