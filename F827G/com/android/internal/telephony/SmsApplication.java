package com.android.internal.telephony;

import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings.Secure;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Rlog;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import com.android.internal.content.PackageMonitor;
import com.android.internal.telephony.gsm.SmsCbConstants;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public final class SmsApplication {
    private static final String BLUETOOTH_PACKAGE_NAME = "com.android.bluetooth";
    private static final boolean DEBUG_MULTIUSER = false;
    static final String LOG_TAG = "SmsApplication";
    private static final String MMS_SERVICE_PACKAGE_NAME = "com.android.mms.service";
    private static final String PHONE_PACKAGE_NAME = "com.android.phone";
    private static final String SCHEME_MMS = "mms";
    private static final String SCHEME_MMSTO = "mmsto";
    private static final String SCHEME_SMS = "sms";
    private static final String SCHEME_SMSTO = "smsto";
    private static final String SMS_BACKUP_PACKAGE_NAME = "jp.co.sharp.android.smsbackup";
    private static SmsPackageMonitor sSmsPackageMonitor = null;

    public static class SmsApplicationData {
        public String mApplicationName;
        public String mMmsReceiverClass;
        public String mPackageName;
        public String mRespondViaMessageClass;
        public String mSendToClass;
        public String mSmsReceiverClass;
        public int mUid;

        public SmsApplicationData(String str, String str2, int i) {
            this.mApplicationName = str;
            this.mPackageName = str2;
            this.mUid = i;
        }

        public boolean isComplete() {
            return (this.mSmsReceiverClass == null || this.mMmsReceiverClass == null || this.mRespondViaMessageClass == null || this.mSendToClass == null) ? false : true;
        }
    }

    private static final class SmsPackageMonitor extends PackageMonitor {
        final Context mContext;

        public SmsPackageMonitor(Context context) {
            this.mContext = context;
        }

        private void onPackageChanged(String str) {
            PackageManager packageManager = this.mContext.getPackageManager();
            Context context = this.mContext;
            int sendingUserId = getSendingUserId();
            if (sendingUserId != 0) {
                try {
                    context = this.mContext.createPackageContextAsUser(this.mContext.getPackageName(), 0, new UserHandle(sendingUserId));
                } catch (NameNotFoundException e) {
                }
            }
            ComponentName defaultSendToApplication = SmsApplication.getDefaultSendToApplication(context, true);
            if (defaultSendToApplication != null) {
                SmsApplication.configurePreferredActivity(packageManager, defaultSendToApplication, sendingUserId);
            }
        }

        public void onPackageAppeared(String str, int i) {
            onPackageChanged(str);
        }

        public void onPackageDisappeared(String str, int i) {
            onPackageChanged(str);
        }

        public void onPackageModified(String str) {
            onPackageChanged(str);
        }
    }

    private static void configurePreferredActivity(PackageManager packageManager, ComponentName componentName, int i) {
        replacePreferredActivity(packageManager, componentName, i, SCHEME_SMS);
        replacePreferredActivity(packageManager, componentName, i, SCHEME_SMSTO);
        replacePreferredActivity(packageManager, componentName, i, SCHEME_MMS);
        replacePreferredActivity(packageManager, componentName, i, SCHEME_MMSTO);
    }

    private static SmsApplicationData getApplication(Context context, boolean z, int i) {
        SmsApplicationData smsApplicationData;
        SmsApplicationData smsApplicationData2 = null;
        if (((TelephonyManager) context.getSystemService("phone")).isSmsCapable()) {
            Collection applicationCollectionInternal = getApplicationCollectionInternal(context, i);
            String stringForUser = Secure.getStringForUser(context.getContentResolver(), "sms_default_application", i);
            SmsApplicationData applicationForPackage = stringForUser != null ? getApplicationForPackage(applicationCollectionInternal, stringForUser) : null;
            if (z && applicationForPackage == null) {
                applicationForPackage = getApplicationForPackage(applicationCollectionInternal, context.getResources().getString(17039435));
                if (applicationForPackage == null && applicationCollectionInternal.size() != 0) {
                    applicationForPackage = (SmsApplicationData) applicationCollectionInternal.toArray()[0];
                }
                if (applicationForPackage != null) {
                    setDefaultApplicationInternal(applicationForPackage.mPackageName, context, i);
                }
            }
            if (applicationForPackage != null) {
                AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService("appops");
                if ((z || applicationForPackage.mUid == Process.myUid()) && appOpsManager.checkOp(15, applicationForPackage.mUid, applicationForPackage.mPackageName) != 0) {
                    Rlog.e(LOG_TAG, applicationForPackage.mPackageName + " lost OP_WRITE_SMS: " + (z ? " (fixing)" : " (no permission to fix)"));
                    if (z) {
                        appOpsManager.setMode(15, applicationForPackage.mUid, applicationForPackage.mPackageName, 0);
                    } else {
                        applicationForPackage = null;
                    }
                }
                if (z) {
                    PackageInfo packageInfo;
                    PackageManager packageManager = context.getPackageManager();
                    configurePreferredActivity(packageManager, new ComponentName(applicationForPackage.mPackageName, applicationForPackage.mSendToClass), i);
                    try {
                        packageInfo = packageManager.getPackageInfo(PHONE_PACKAGE_NAME, 0);
                        if (appOpsManager.checkOp(15, packageInfo.applicationInfo.uid, PHONE_PACKAGE_NAME) != 0) {
                            Rlog.e(LOG_TAG, "com.android.phone lost OP_WRITE_SMS:  (fixing)");
                            appOpsManager.setMode(15, packageInfo.applicationInfo.uid, PHONE_PACKAGE_NAME, 0);
                        }
                    } catch (NameNotFoundException e) {
                        Rlog.e(LOG_TAG, "Phone package not found: com.android.phone");
                        applicationForPackage = null;
                    }
                    try {
                        packageInfo = packageManager.getPackageInfo(BLUETOOTH_PACKAGE_NAME, 0);
                        if (appOpsManager.checkOp(15, packageInfo.applicationInfo.uid, BLUETOOTH_PACKAGE_NAME) != 0) {
                            Rlog.e(LOG_TAG, "com.android.bluetooth lost OP_WRITE_SMS:  (fixing)");
                            appOpsManager.setMode(15, packageInfo.applicationInfo.uid, BLUETOOTH_PACKAGE_NAME, 0);
                        }
                    } catch (NameNotFoundException e2) {
                        Rlog.e(LOG_TAG, "Bluetooth package not found: com.android.bluetooth");
                    }
                    try {
                        packageInfo = packageManager.getPackageInfo(MMS_SERVICE_PACKAGE_NAME, 0);
                        if (appOpsManager.checkOp(15, packageInfo.applicationInfo.uid, MMS_SERVICE_PACKAGE_NAME) != 0) {
                            Rlog.e(LOG_TAG, "com.android.mms.service lost OP_WRITE_SMS:  (fixing)");
                            appOpsManager.setMode(15, packageInfo.applicationInfo.uid, MMS_SERVICE_PACKAGE_NAME, 0);
                            smsApplicationData2 = applicationForPackage;
                        } else {
                            smsApplicationData2 = applicationForPackage;
                        }
                    } catch (NameNotFoundException e3) {
                        Rlog.e(LOG_TAG, "MmsService package not found: com.android.mms.service");
                    }
                    try {
                        PackageInfo packageInfo2 = packageManager.getPackageInfo(SMS_BACKUP_PACKAGE_NAME, 0);
                        if (appOpsManager.checkOp(15, packageInfo2.applicationInfo.uid, SMS_BACKUP_PACKAGE_NAME) != 0) {
                            Rlog.e(LOG_TAG, "jp.co.sharp.android.smsbackup lost OP_WRITE_SMS:  (fixing)");
                            appOpsManager.setMode(15, packageInfo2.applicationInfo.uid, SMS_BACKUP_PACKAGE_NAME, 0);
                            return smsApplicationData2;
                        }
                        smsApplicationData = smsApplicationData2;
                    } catch (NameNotFoundException e4) {
                        Rlog.e(LOG_TAG, "SMSBackup package not found: jp.co.sharp.android.smsbackup");
                        return smsApplicationData2;
                    }
                }
            }
            smsApplicationData = applicationForPackage;
        } else {
            smsApplicationData = null;
        }
        return smsApplicationData;
    }

    public static Collection<SmsApplicationData> getApplicationCollection(Context context) {
        int incomingUserId = getIncomingUserId(context);
        long clearCallingIdentity = Binder.clearCallingIdentity();
        try {
            Collection<SmsApplicationData> applicationCollectionInternal = getApplicationCollectionInternal(context, incomingUserId);
            return applicationCollectionInternal;
        } finally {
            Binder.restoreCallingIdentity(clearCallingIdentity);
        }
    }

    private static Collection<SmsApplicationData> getApplicationCollectionInternal(Context context, int i) {
        ActivityInfo activityInfo;
        SmsApplicationData smsApplicationData;
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> queryBroadcastReceivers = packageManager.queryBroadcastReceivers(new Intent(Intents.SMS_DELIVER_ACTION), 0, i);
        HashMap hashMap = new HashMap();
        for (ResolveInfo resolveInfo : queryBroadcastReceivers) {
            activityInfo = resolveInfo.activityInfo;
            if (activityInfo != null && "android.permission.BROADCAST_SMS".equals(activityInfo.permission)) {
                String str = activityInfo.packageName;
                if (!hashMap.containsKey(str)) {
                    SmsApplicationData smsApplicationData2 = new SmsApplicationData(resolveInfo.loadLabel(packageManager).toString(), str, activityInfo.applicationInfo.uid);
                    smsApplicationData2.mSmsReceiverClass = activityInfo.name;
                    hashMap.put(str, smsApplicationData2);
                }
            }
        }
        Intent intent = new Intent(Intents.WAP_PUSH_DELIVER_ACTION);
        intent.setDataAndType(null, "application/vnd.wap.mms-message");
        for (ResolveInfo resolveInfo2 : packageManager.queryBroadcastReceivers(intent, 0, i)) {
            activityInfo = resolveInfo2.activityInfo;
            if (activityInfo != null && "android.permission.BROADCAST_WAP_PUSH".equals(activityInfo.permission)) {
                smsApplicationData = (SmsApplicationData) hashMap.get(activityInfo.packageName);
                if (smsApplicationData != null) {
                    smsApplicationData.mMmsReceiverClass = activityInfo.name;
                }
            }
        }
        for (ResolveInfo resolveInfo22 : packageManager.queryIntentServicesAsUser(new Intent("android.intent.action.RESPOND_VIA_MESSAGE", Uri.fromParts(SCHEME_SMSTO, "", null)), 0, i)) {
            ServiceInfo serviceInfo = resolveInfo22.serviceInfo;
            if (serviceInfo != null && "android.permission.SEND_RESPOND_VIA_MESSAGE".equals(serviceInfo.permission)) {
                smsApplicationData = (SmsApplicationData) hashMap.get(serviceInfo.packageName);
                if (smsApplicationData != null) {
                    smsApplicationData.mRespondViaMessageClass = serviceInfo.name;
                }
            }
        }
        for (ResolveInfo resolveInfo222 : packageManager.queryIntentActivitiesAsUser(new Intent("android.intent.action.SENDTO", Uri.fromParts(SCHEME_SMSTO, "", null)), 0, i)) {
            ActivityInfo activityInfo2 = resolveInfo222.activityInfo;
            if (activityInfo2 != null) {
                smsApplicationData = (SmsApplicationData) hashMap.get(activityInfo2.packageName);
                if (smsApplicationData != null) {
                    smsApplicationData.mSendToClass = activityInfo2.name;
                }
            }
        }
        for (ResolveInfo resolveInfo2222 : queryBroadcastReceivers) {
            ActivityInfo activityInfo3 = resolveInfo2222.activityInfo;
            if (activityInfo3 != null) {
                String str2 = activityInfo3.packageName;
                smsApplicationData = (SmsApplicationData) hashMap.get(str2);
                if (!(smsApplicationData == null || smsApplicationData.isComplete())) {
                    hashMap.remove(str2);
                }
            }
        }
        return hashMap.values();
    }

    private static SmsApplicationData getApplicationForPackage(Collection<SmsApplicationData> collection, String str) {
        if (str == null) {
            return null;
        }
        for (SmsApplicationData smsApplicationData : collection) {
            if (smsApplicationData.mPackageName.contentEquals(str)) {
                return smsApplicationData;
            }
        }
        return null;
    }

    public static ComponentName getDefaultMmsApplication(Context context, boolean z) {
        int incomingUserId = getIncomingUserId(context);
        long clearCallingIdentity = Binder.clearCallingIdentity();
        ComponentName componentName = null;
        try {
            SmsApplicationData application = getApplication(context, z, incomingUserId);
            if (application != null) {
                componentName = new ComponentName(application.mPackageName, application.mMmsReceiverClass);
            }
            Binder.restoreCallingIdentity(clearCallingIdentity);
            return componentName;
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(clearCallingIdentity);
        }
    }

    public static ComponentName getDefaultRespondViaMessageApplication(Context context, boolean z) {
        int incomingUserId = getIncomingUserId(context);
        long clearCallingIdentity = Binder.clearCallingIdentity();
        ComponentName componentName = null;
        try {
            SmsApplicationData application = getApplication(context, z, incomingUserId);
            if (application != null) {
                componentName = new ComponentName(application.mPackageName, application.mRespondViaMessageClass);
            }
            Binder.restoreCallingIdentity(clearCallingIdentity);
            return componentName;
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(clearCallingIdentity);
        }
    }

    public static ComponentName getDefaultSendToApplication(Context context, boolean z) {
        int incomingUserId = getIncomingUserId(context);
        long clearCallingIdentity = Binder.clearCallingIdentity();
        ComponentName componentName = null;
        try {
            SmsApplicationData application = getApplication(context, z, incomingUserId);
            if (application != null) {
                componentName = new ComponentName(application.mPackageName, application.mSendToClass);
            }
            Binder.restoreCallingIdentity(clearCallingIdentity);
            return componentName;
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(clearCallingIdentity);
        }
    }

    public static ComponentName getDefaultSmsApplication(Context context, boolean z) {
        int incomingUserId = getIncomingUserId(context);
        long clearCallingIdentity = Binder.clearCallingIdentity();
        ComponentName componentName = null;
        try {
            SmsApplicationData application = getApplication(context, z, incomingUserId);
            if (application != null) {
                componentName = new ComponentName(application.mPackageName, application.mSmsReceiverClass);
            }
            Binder.restoreCallingIdentity(clearCallingIdentity);
            return componentName;
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(clearCallingIdentity);
        }
    }

    private static int getIncomingUserId(Context context) {
        int userId = context.getUserId();
        int callingUid = Binder.getCallingUid();
        return UserHandle.getAppId(callingUid) < 10000 ? userId : UserHandle.getUserId(callingUid);
    }

    public static SmsApplicationData getSmsApplicationData(String str, Context context) {
        return getApplicationForPackage(getApplicationCollection(context), str);
    }

    public static void initSmsPackageMonitor(Context context) {
        sSmsPackageMonitor = new SmsPackageMonitor(context);
        sSmsPackageMonitor.register(context, context.getMainLooper(), UserHandle.ALL, false);
    }

    private static void replacePreferredActivity(PackageManager packageManager, ComponentName componentName, int i, String str) {
        List queryIntentActivitiesAsUser = packageManager.queryIntentActivitiesAsUser(new Intent("android.intent.action.SENDTO", Uri.fromParts(str, "", null)), 65600, i);
        int size = queryIntentActivitiesAsUser.size();
        ComponentName[] componentNameArr = new ComponentName[size];
        for (int i2 = 0; i2 < size; i2++) {
            ResolveInfo resolveInfo = (ResolveInfo) queryIntentActivitiesAsUser.get(i2);
            componentNameArr[i2] = new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.SENDTO");
        intentFilter.addCategory("android.intent.category.DEFAULT");
        intentFilter.addDataScheme(str);
        packageManager.replacePreferredActivityAsUser(intentFilter, 2129920, componentNameArr, componentName, i);
    }

    public static void setDefaultApplication(String str, Context context) {
        if (((TelephonyManager) context.getSystemService("phone")).isSmsCapable()) {
            int incomingUserId = getIncomingUserId(context);
            long clearCallingIdentity = Binder.clearCallingIdentity();
            try {
                setDefaultApplicationInternal(str, context, incomingUserId);
            } finally {
                Binder.restoreCallingIdentity(clearCallingIdentity);
            }
        }
    }

    private static void setDefaultApplicationInternal(String str, Context context, int i) {
        String stringForUser = Secure.getStringForUser(context.getContentResolver(), "sms_default_application", i);
        if (str == null || stringForUser == null || !str.equals(stringForUser)) {
            PackageManager packageManager = context.getPackageManager();
            SmsApplicationData applicationForPackage = getApplicationForPackage(getApplicationCollection(context), str);
            if (applicationForPackage != null) {
                AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService("appops");
                if (stringForUser != null) {
                    try {
                        appOpsManager.setMode(15, packageManager.getPackageInfo(stringForUser, SmsCbConstants.SERIAL_NUMBER_ETWS_EMERGENCY_USER_ALERT).applicationInfo.uid, stringForUser, 1);
                    } catch (NameNotFoundException e) {
                        Rlog.w(LOG_TAG, "Old SMS package not found: " + stringForUser);
                    }
                }
                Secure.putStringForUser(context.getContentResolver(), "sms_default_application", applicationForPackage.mPackageName, i);
                configurePreferredActivity(packageManager, new ComponentName(applicationForPackage.mPackageName, applicationForPackage.mSendToClass), i);
                appOpsManager.setMode(15, applicationForPackage.mUid, applicationForPackage.mPackageName, 0);
                try {
                    appOpsManager.setMode(15, packageManager.getPackageInfo(PHONE_PACKAGE_NAME, 0).applicationInfo.uid, PHONE_PACKAGE_NAME, 0);
                } catch (NameNotFoundException e2) {
                    Rlog.e(LOG_TAG, "Phone package not found: com.android.phone");
                }
                try {
                    appOpsManager.setMode(15, packageManager.getPackageInfo(BLUETOOTH_PACKAGE_NAME, 0).applicationInfo.uid, BLUETOOTH_PACKAGE_NAME, 0);
                } catch (NameNotFoundException e3) {
                    Rlog.e(LOG_TAG, "Bluetooth package not found: com.android.bluetooth");
                }
                try {
                    appOpsManager.setMode(15, packageManager.getPackageInfo(MMS_SERVICE_PACKAGE_NAME, 0).applicationInfo.uid, MMS_SERVICE_PACKAGE_NAME, 0);
                } catch (NameNotFoundException e4) {
                    Rlog.e(LOG_TAG, "MmsService package not found: com.android.mms.service");
                }
                try {
                    appOpsManager.setMode(15, packageManager.getPackageInfo(SMS_BACKUP_PACKAGE_NAME, 0).applicationInfo.uid, SMS_BACKUP_PACKAGE_NAME, 0);
                } catch (NameNotFoundException e5) {
                    Rlog.e(LOG_TAG, "SMSBackup package not found: jp.co.sharp.android.smsbackup");
                }
            }
        }
    }

    public static boolean shouldWriteMessageForPackage(String str, Context context) {
        if (!(str == null || SmsManager.getDefault().getAutoPersisting())) {
            String str2 = null;
            ComponentName defaultSmsApplication = getDefaultSmsApplication(context, false);
            if (defaultSmsApplication != null) {
                str2 = defaultSmsApplication.getPackageName();
            }
            if ((str2 != null && str2.equals(str)) || str.equals(BLUETOOTH_PACKAGE_NAME)) {
                return false;
            }
        }
        return true;
    }
}
