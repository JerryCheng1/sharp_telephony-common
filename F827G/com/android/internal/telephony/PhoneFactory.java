package com.android.internal.telephony;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneFactory;
import com.android.internal.telephony.sip.SipPhone;
import com.android.internal.telephony.sip.SipPhoneFactory;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.UiccController;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public class PhoneFactory {
    static final String LOG_TAG = "PhoneFactory";
    static final int SOCKET_OPEN_MAX_RETRY = 3;
    static final int SOCKET_OPEN_RETRY_MILLIS = 2000;
    private static ProxyController mProxyController;
    private static UiccController mUiccController;
    private static CommandsInterface sCommandsInterface = null;
    private static CommandsInterface[] sCommandsInterfaces = null;
    private static Context sContext;
    static final Object sLockProxyPhones = new Object();
    private static boolean sMadeDefaults = false;
    private static ModemBindingPolicyHandler sModemBindingPolicyHandler;
    private static ModemStackController sModemStackController;
    private static PhoneNotifier sPhoneNotifier;
    private static PhoneProxy sProxyPhone = null;
    private static PhoneProxy[] sProxyPhones = null;
    private static SubscriptionInfoUpdater sSubInfoRecordUpdater = null;

    public static int calculatePreferredNetworkType(Context context) {
        return calculatePreferredNetworkType(context, getDefaultPhoneId());
    }

    public static int calculatePreferredNetworkType(Context context, int i) {
        int i2 = RILConstants.PREFERRED_NETWORK_MODE;
        if (TelephonyManager.getLteOnCdmaModeStatic(i) == 1) {
            i2 = 7;
        }
        try {
            i2 = TelephonyManager.getIntAtIndex(context.getContentResolver(), "preferred_network_mode", i);
        } catch (SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Value At Index for Settings.Global.PREFERRED_NETWORK_MODE");
        }
        Rlog.d(LOG_TAG, "calculatePreferredNetworkType: phoneId = " + i);
        return i2;
    }

    public static void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("PhoneFactory:");
        PhoneProxy[] phoneProxyArr = (PhoneProxy[]) getPhones();
        int i = -1;
        int length = phoneProxyArr.length;
        int i2 = 0;
        while (i2 < length) {
            PhoneProxy phoneProxy = phoneProxyArr[i2];
            int i3 = i + 1;
            try {
                ((PhoneBase) phoneProxy.getActivePhone()).dump(fileDescriptor, printWriter, strArr);
                printWriter.flush();
                printWriter.println("++++++++++++++++++++++++++++++++");
                try {
                    ((IccCardProxy) phoneProxy.getIccCard()).dump(fileDescriptor, printWriter, strArr);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                printWriter.flush();
                printWriter.println("++++++++++++++++++++++++++++++++");
            } catch (Exception e2) {
                printWriter.println("Telephony DebugService: Could not get Phone[" + i3 + "] e=" + e2);
            }
            i2++;
            i = i3;
        }
        try {
            mUiccController.dump(fileDescriptor, printWriter, strArr);
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        printWriter.flush();
        printWriter.println("++++++++++++++++++++++++++++++++");
        try {
            SubscriptionController.getInstance().dump(fileDescriptor, printWriter, strArr);
        } catch (Exception e32) {
            e32.printStackTrace();
        }
        printWriter.flush();
    }

    public static Phone getCdmaPhone(int i) {
        CDMALTEPhone cDMALTEPhone;
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            cDMALTEPhone = new CDMALTEPhone(sContext, sCommandsInterfaces[i], sPhoneNotifier, i);
        }
        return cDMALTEPhone;
    }

    public static Context getContext() {
        return sContext;
    }

    public static int getDataSubscription() {
        int i = -1;
        try {
            i = Global.getInt(sContext.getContentResolver(), "multi_sim_data_call");
        } catch (SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim Data Call Values");
        }
        int phoneId = SubscriptionController.getInstance().getPhoneId(i);
        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            return i;
        }
        Rlog.i(LOG_TAG, "Subscription is invalid..." + 0 + " Set to 0");
        setDataSubscription(0);
        return 0;
    }

    public static Phone getDefaultPhone() {
        PhoneProxy phoneProxy;
        synchronized (sLockProxyPhones) {
            if (sMadeDefaults) {
                phoneProxy = sProxyPhone;
            } else {
                throw new IllegalStateException("Default phones haven't been made yet!");
            }
        }
        return phoneProxy;
    }

    private static int getDefaultPhoneId() {
        int phoneId = SubscriptionController.getInstance().getPhoneId(getDefaultSubscription());
        return !isValidphoneId(phoneId) ? 0 : phoneId;
    }

    public static int getDefaultSubscription() {
        return SubscriptionController.getInstance().getDefaultSubId();
    }

    public static Phone getGsmPhone(int i) {
        GSMPhone gSMPhone;
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            gSMPhone = new GSMPhone(sContext, sCommandsInterfaces[i], sPhoneNotifier, i);
        }
        return gSMPhone;
    }

    public static Phone getPhone(int i) {
        Object obj;
        synchronized (sLockProxyPhones) {
            if (sMadeDefaults) {
                if (i == Integer.MAX_VALUE) {
                    Rlog.d(LOG_TAG, "getPhone: phoneId == DEFAULT_PHONE_ID");
                    obj = sProxyPhone;
                } else {
                    Rlog.d(LOG_TAG, "getPhone: phoneId != DEFAULT_PHONE_ID");
                    obj = (i < 0 || i >= TelephonyManager.getDefault().getPhoneCount()) ? null : sProxyPhones[i];
                }
                Rlog.d(LOG_TAG, "getPhone:- phone=" + obj);
            } else {
                throw new IllegalStateException("Default phones haven't been made yet!");
            }
        }
        return obj;
    }

    public static Phone[] getPhones() {
        PhoneProxy[] phoneProxyArr;
        synchronized (sLockProxyPhones) {
            if (sMadeDefaults) {
                phoneProxyArr = sProxyPhones;
            } else {
                throw new IllegalStateException("Default phones haven't been made yet!");
            }
        }
        return phoneProxyArr;
    }

    public static int getSMSSubscription() {
        int i = -1;
        try {
            i = Global.getInt(sContext.getContentResolver(), "multi_sim_sms");
        } catch (SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim SMS Values");
        }
        int phoneId = SubscriptionController.getInstance().getPhoneId(i);
        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            return i;
        }
        Rlog.i(LOG_TAG, "Subscription is invalid..." + i + " Set to 0");
        setSMSSubscription(0);
        return 0;
    }

    public static SubscriptionInfoUpdater getSubscriptionInfoUpdater() {
        return sSubInfoRecordUpdater;
    }

    public static int getVoiceSubscription() {
        int i = -1;
        try {
            i = Global.getInt(sContext.getContentResolver(), "multi_sim_voice_call");
        } catch (SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim Voice Call Values");
        }
        int phoneId = SubscriptionController.getInstance().getPhoneId(i);
        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            return i;
        }
        Rlog.i(LOG_TAG, "Subscription is invalid..." + i + " Set to 0");
        setVoiceSubscription(0);
        return 0;
    }

    public static boolean isPromptEnabled() {
        int i;
        boolean z = false;
        try {
            i = Global.getInt(sContext.getContentResolver(), "multi_sim_voice_prompt");
        } catch (SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim Voice Prompt Values");
            i = 0;
        }
        if (i != 0) {
            z = true;
        }
        Rlog.d(LOG_TAG, "Prompt option:" + z);
        return z;
    }

    public static boolean isSMSPromptEnabled() {
        int i;
        boolean z = false;
        try {
            i = Global.getInt(sContext.getContentResolver(), "multi_sim_sms_prompt");
        } catch (SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim SMS Prompt Values");
            i = 0;
        }
        if (i != 0) {
            z = true;
        }
        Rlog.d(LOG_TAG, "SMS Prompt option:" + z);
        return z;
    }

    private static boolean isValidphoneId(int i) {
        return i >= 0 && i < TelephonyManager.getDefault().getPhoneCount();
    }

    /* JADX WARNING: Missing block: B:22:?, code skipped:
            sPhoneNotifier = new com.android.internal.telephony.DefaultPhoneNotifier();
            r0 = com.android.internal.telephony.RILConstants.PREFERRED_NETWORK_MODE;
     */
    /* JADX WARNING: Missing block: B:23:0x0087, code skipped:
            if (android.telephony.TelephonyManager.getLteOnCdmaModeStatic() != 1) goto L_0x0241;
     */
    /* JADX WARNING: Missing block: B:24:0x0089, code skipped:
            r2 = 7;
     */
    /* JADX WARNING: Missing block: B:25:0x008b, code skipped:
            r5 = com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager.getDefault(r12);
            android.telephony.Rlog.i(LOG_TAG, "Cdma Subscription set to " + r5);
            r6 = android.telephony.TelephonyManager.getDefault().getPhoneCount();
            r7 = new int[r6];
            sProxyPhones = new com.android.internal.telephony.PhoneProxy[r6];
            sCommandsInterfaces = new com.android.internal.telephony.RIL[r6];
     */
    /* JADX WARNING: Missing block: B:26:0x00b9, code skipped:
            r0 = 0;
     */
    /* JADX WARNING: Missing block: B:27:0x00ba, code skipped:
            if (r0 >= r6) goto L_0x0164;
     */
    /* JADX WARNING: Missing block: B:29:?, code skipped:
            r7[r0] = android.telephony.TelephonyManager.getIntAtIndex(r12.getContentResolver(), "preferred_network_mode", r0);
     */
    /* JADX WARNING: Missing block: B:62:?, code skipped:
            android.telephony.Rlog.e(LOG_TAG, "Settings Exception Reading Value At Index for Settings.Global.PREFERRED_NETWORK_MODE");
     */
    /* JADX WARNING: Missing block: B:63:0x0160, code skipped:
            r7[r0] = r2;
     */
    /* JADX WARNING: Missing block: B:65:?, code skipped:
            android.telephony.Rlog.i(LOG_TAG, "Creating SubscriptionController");
            com.android.internal.telephony.SubscriptionController.init(r12, sCommandsInterfaces);
            mUiccController = com.android.internal.telephony.uicc.UiccController.make(r12, sCommandsInterfaces);
            sModemStackController = com.android.internal.telephony.ModemStackController.make(r12, mUiccController, sCommandsInterfaces);
            sModemBindingPolicyHandler = com.android.internal.telephony.ModemBindingPolicyHandler.make(r12, mUiccController, sCommandsInterfaces);
     */
    /* JADX WARNING: Missing block: B:66:0x018c, code skipped:
            if (r1 >= r6) goto L_0x01df;
     */
    /* JADX WARNING: Missing block: B:67:0x018e, code skipped:
            r0 = null;
            r2 = android.telephony.TelephonyManager.getPhoneType(r7[r1]);
     */
    /* JADX WARNING: Missing block: B:68:0x0195, code skipped:
            if (r2 != 1) goto L_0x01d0;
     */
    /* JADX WARNING: Missing block: B:69:0x0197, code skipped:
            r0 = new com.android.internal.telephony.gsm.GSMPhone(r12, sCommandsInterfaces[r1], sPhoneNotifier, r1);
     */
    /* JADX WARNING: Missing block: B:70:0x01a2, code skipped:
            android.telephony.Rlog.i(LOG_TAG, "Creating Phone with type = " + r2 + " sub = " + r1);
            sProxyPhones[r1] = new com.android.internal.telephony.PhoneProxy(r0);
            r1 = r1 + 1;
     */
    /* JADX WARNING: Missing block: B:72:0x01d1, code skipped:
            if (r2 != 2) goto L_0x01a2;
     */
    /* JADX WARNING: Missing block: B:73:0x01d3, code skipped:
            r0 = new com.android.internal.telephony.cdma.CDMALTEPhone(r12, sCommandsInterfaces[r1], sPhoneNotifier, r1);
     */
    /* JADX WARNING: Missing block: B:74:0x01df, code skipped:
            mProxyController = com.android.internal.telephony.ProxyController.getInstance(r12, sProxyPhones, mUiccController, sCommandsInterfaces);
            sProxyPhone = sProxyPhones[0];
            sCommandsInterface = sCommandsInterfaces[0];
            r1 = com.android.internal.telephony.SmsApplication.getDefaultSmsApplication(r12, true);
     */
    /* JADX WARNING: Missing block: B:75:0x01fd, code skipped:
            r0 = "NONE";
     */
    /* JADX WARNING: Missing block: B:76:0x0200, code skipped:
            if (r1 == null) goto L_0x0206;
     */
    /* JADX WARNING: Missing block: B:78:?, code skipped:
            r0 = r1.getPackageName();
     */
    /* JADX WARNING: Missing block: B:79:0x0206, code skipped:
            android.telephony.Rlog.i(LOG_TAG, "defaultSmsApplication: " + r0);
            com.android.internal.telephony.SmsApplication.initSmsPackageMonitor(r12);
            sMadeDefaults = true;
            android.telephony.Rlog.i(LOG_TAG, "Creating SubInfoRecordUpdater ");
            sSubInfoRecordUpdater = new com.android.internal.telephony.SubscriptionInfoUpdater(r12, sProxyPhones, sCommandsInterfaces);
            com.android.internal.telephony.SubscriptionController.getInstance().updatePhonesAvailability(sProxyPhones);
     */
    /* JADX WARNING: Missing block: B:82:0x0241, code skipped:
            r2 = r0;
     */
    public static void makeDefaultPhone(android.content.Context r12) {
        /*
        r3 = 1;
        r1 = 0;
        r4 = sLockProxyPhones;
        monitor-enter(r4);
        r0 = sMadeDefaults;	 Catch:{ all -> 0x013c }
        if (r0 != 0) goto L_0x023f;
    L_0x0009:
        r0 = com.android.internal.telephony.TelBrand.IS_DCM;	 Catch:{ all -> 0x013c }
        if (r0 == 0) goto L_0x0068;
    L_0x000d:
        r0 = "vold.decrypt";
        r2 = "0";
        r0 = android.os.SystemProperties.get(r0, r2);	 Catch:{ all -> 0x013c }
        r2 = "PhoneFactory";
        r5 = new java.lang.StringBuilder;	 Catch:{ all -> 0x013c }
        r5.<init>();	 Catch:{ all -> 0x013c }
        r6 = "decryptState = ";
        r5 = r5.append(r6);	 Catch:{ all -> 0x013c }
        r5 = r5.append(r0);	 Catch:{ all -> 0x013c }
        r5 = r5.toString();	 Catch:{ all -> 0x013c }
        android.telephony.Rlog.d(r2, r5);	 Catch:{ all -> 0x013c }
        r2 = "trigger_restart_min_framework";
        r0 = r0.equals(r2);	 Catch:{ all -> 0x013c }
        if (r0 == 0) goto L_0x0068;
    L_0x0035:
        r0 = "mount";
        r0 = android.os.ServiceManager.getService(r0);	 Catch:{ Exception -> 0x0121 }
        r0 = android.os.storage.IMountService.Stub.asInterface(r0);	 Catch:{ Exception -> 0x0121 }
        r2 = "persist.radio.sh.imss_reg_state";
        r0 = r0.getField(r2);	 Catch:{ Exception -> 0x0121 }
        r2 = android.text.TextUtils.isEmpty(r0);	 Catch:{ Exception -> 0x0121 }
        if (r2 != 0) goto L_0x0118;
    L_0x004b:
        r2 = "PhoneFactory";
        r5 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x0121 }
        r5.<init>();	 Catch:{ Exception -> 0x0121 }
        r6 = "isImsOn = ";
        r5 = r5.append(r6);	 Catch:{ Exception -> 0x0121 }
        r5 = r5.append(r0);	 Catch:{ Exception -> 0x0121 }
        r5 = r5.toString();	 Catch:{ Exception -> 0x0121 }
        android.telephony.Rlog.d(r2, r5);	 Catch:{ Exception -> 0x0121 }
        r2 = "persist.radio.sh.imss_reg_state";
        android.os.SystemProperties.set(r2, r0);	 Catch:{ Exception -> 0x0121 }
    L_0x0068:
        sContext = r12;	 Catch:{ all -> 0x013c }
        com.android.internal.telephony.TelephonyDevController.create();	 Catch:{ all -> 0x013c }
        r0 = r1;
    L_0x006e:
        r0 = r0 + 1;
        r2 = new android.net.LocalServerSocket;	 Catch:{ IOException -> 0x013f }
        r5 = "com.android.internal.telephony";
        r2.<init>(r5);	 Catch:{ IOException -> 0x013f }
        r2 = r1;
    L_0x0078:
        if (r2 != 0) goto L_0x0143;
    L_0x007a:
        r0 = new com.android.internal.telephony.DefaultPhoneNotifier;	 Catch:{ all -> 0x013c }
        r0.<init>();	 Catch:{ all -> 0x013c }
        sPhoneNotifier = r0;	 Catch:{ all -> 0x013c }
        r0 = com.android.internal.telephony.RILConstants.PREFERRED_NETWORK_MODE;	 Catch:{ all -> 0x013c }
        r2 = android.telephony.TelephonyManager.getLteOnCdmaModeStatic();	 Catch:{ all -> 0x013c }
        if (r2 != r3) goto L_0x0241;
    L_0x0089:
        r0 = 7;
        r2 = r0;
    L_0x008b:
        r5 = com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager.getDefault(r12);	 Catch:{ all -> 0x013c }
        r0 = "PhoneFactory";
        r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x013c }
        r6.<init>();	 Catch:{ all -> 0x013c }
        r7 = "Cdma Subscription set to ";
        r6 = r6.append(r7);	 Catch:{ all -> 0x013c }
        r6 = r6.append(r5);	 Catch:{ all -> 0x013c }
        r6 = r6.toString();	 Catch:{ all -> 0x013c }
        android.telephony.Rlog.i(r0, r6);	 Catch:{ all -> 0x013c }
        r0 = android.telephony.TelephonyManager.getDefault();	 Catch:{ all -> 0x013c }
        r6 = r0.getPhoneCount();	 Catch:{ all -> 0x013c }
        r7 = new int[r6];	 Catch:{ all -> 0x013c }
        r0 = new com.android.internal.telephony.PhoneProxy[r6];	 Catch:{ all -> 0x013c }
        sProxyPhones = r0;	 Catch:{ all -> 0x013c }
        r0 = new com.android.internal.telephony.RIL[r6];	 Catch:{ all -> 0x013c }
        sCommandsInterfaces = r0;	 Catch:{ all -> 0x013c }
        r0 = r1;
    L_0x00ba:
        if (r0 >= r6) goto L_0x0164;
    L_0x00bc:
        r8 = r12.getContentResolver();	 Catch:{ SettingNotFoundException -> 0x0158 }
        r9 = "preferred_network_mode";
        r8 = android.telephony.TelephonyManager.getIntAtIndex(r8, r9, r0);	 Catch:{ SettingNotFoundException -> 0x0158 }
        r7[r0] = r8;	 Catch:{ SettingNotFoundException -> 0x0158 }
    L_0x00c8:
        r8 = sContext;	 Catch:{ all -> 0x013c }
        r8 = r8.getResources();	 Catch:{ all -> 0x013c }
        r9 = 17957014; // 0x1120096 float:2.6816385E-38 double:8.8719437E-317;
        r8 = r8.getBoolean(r9);	 Catch:{ all -> 0x013c }
        if (r8 == 0) goto L_0x00e8;
    L_0x00d7:
        if (r0 != 0) goto L_0x00e8;
    L_0x00d9:
        r8 = 10;
        r7[r0] = r8;
        r8 = r12.getContentResolver();	 Catch:{ all -> 0x013c }
        r9 = "preferred_network_mode";
        r10 = r7[r0];	 Catch:{ all -> 0x013c }
        android.telephony.TelephonyManager.putIntAtIndex(r8, r9, r0, r10);	 Catch:{ all -> 0x013c }
    L_0x00e8:
        r8 = "PhoneFactory";
        r9 = new java.lang.StringBuilder;	 Catch:{ all -> 0x013c }
        r9.<init>();	 Catch:{ all -> 0x013c }
        r10 = "Network Mode set to ";
        r9 = r9.append(r10);	 Catch:{ all -> 0x013c }
        r10 = r7[r0];	 Catch:{ all -> 0x013c }
        r10 = java.lang.Integer.toString(r10);	 Catch:{ all -> 0x013c }
        r9 = r9.append(r10);	 Catch:{ all -> 0x013c }
        r9 = r9.toString();	 Catch:{ all -> 0x013c }
        android.telephony.Rlog.i(r8, r9);	 Catch:{ all -> 0x013c }
        r8 = sCommandsInterfaces;	 Catch:{ all -> 0x013c }
        r9 = new com.android.internal.telephony.RIL;	 Catch:{ all -> 0x013c }
        r10 = r7[r0];	 Catch:{ all -> 0x013c }
        r11 = java.lang.Integer.valueOf(r0);	 Catch:{ all -> 0x013c }
        r9.<init>(r12, r10, r5, r11);	 Catch:{ all -> 0x013c }
        r8[r0] = r9;	 Catch:{ all -> 0x013c }
        r0 = r0 + 1;
        goto L_0x00ba;
    L_0x0118:
        r0 = "PhoneFactory";
        r2 = "isImsOn is empty";
        android.telephony.Rlog.d(r0, r2);	 Catch:{ Exception -> 0x0121 }
        goto L_0x0068;
    L_0x0121:
        r0 = move-exception;
        r2 = "PhoneFactory";
        r5 = new java.lang.StringBuilder;	 Catch:{ all -> 0x013c }
        r5.<init>();	 Catch:{ all -> 0x013c }
        r6 = "Exception: ";
        r5 = r5.append(r6);	 Catch:{ all -> 0x013c }
        r0 = r5.append(r0);	 Catch:{ all -> 0x013c }
        r0 = r0.toString();	 Catch:{ all -> 0x013c }
        android.telephony.Rlog.d(r2, r0);	 Catch:{ all -> 0x013c }
        goto L_0x0068;
    L_0x013c:
        r0 = move-exception;
        monitor-exit(r4);	 Catch:{ all -> 0x013c }
        throw r0;
    L_0x013f:
        r2 = move-exception;
        r2 = r3;
        goto L_0x0078;
    L_0x0143:
        r2 = 3;
        if (r0 <= r2) goto L_0x014e;
    L_0x0146:
        r0 = new java.lang.RuntimeException;	 Catch:{ all -> 0x013c }
        r1 = "PhoneFactory probably already running";
        r0.<init>(r1);	 Catch:{ all -> 0x013c }
        throw r0;	 Catch:{ all -> 0x013c }
    L_0x014e:
        r6 = 2000; // 0x7d0 float:2.803E-42 double:9.88E-321;
        java.lang.Thread.sleep(r6);	 Catch:{ InterruptedException -> 0x0155 }
        goto L_0x006e;
    L_0x0155:
        r2 = move-exception;
        goto L_0x006e;
    L_0x0158:
        r8 = move-exception;
        r8 = "PhoneFactory";
        r9 = "Settings Exception Reading Value At Index for Settings.Global.PREFERRED_NETWORK_MODE";
        android.telephony.Rlog.e(r8, r9);	 Catch:{ all -> 0x013c }
        r7[r0] = r2;
        goto L_0x00c8;
    L_0x0164:
        r0 = "PhoneFactory";
        r2 = "Creating SubscriptionController";
        android.telephony.Rlog.i(r0, r2);	 Catch:{ all -> 0x013c }
        r0 = sCommandsInterfaces;	 Catch:{ all -> 0x013c }
        com.android.internal.telephony.SubscriptionController.init(r12, r0);	 Catch:{ all -> 0x013c }
        r0 = sCommandsInterfaces;	 Catch:{ all -> 0x013c }
        r0 = com.android.internal.telephony.uicc.UiccController.make(r12, r0);	 Catch:{ all -> 0x013c }
        mUiccController = r0;	 Catch:{ all -> 0x013c }
        r0 = mUiccController;	 Catch:{ all -> 0x013c }
        r2 = sCommandsInterfaces;	 Catch:{ all -> 0x013c }
        r0 = com.android.internal.telephony.ModemStackController.make(r12, r0, r2);	 Catch:{ all -> 0x013c }
        sModemStackController = r0;	 Catch:{ all -> 0x013c }
        r0 = mUiccController;	 Catch:{ all -> 0x013c }
        r2 = sCommandsInterfaces;	 Catch:{ all -> 0x013c }
        r0 = com.android.internal.telephony.ModemBindingPolicyHandler.make(r12, r0, r2);	 Catch:{ all -> 0x013c }
        sModemBindingPolicyHandler = r0;	 Catch:{ all -> 0x013c }
    L_0x018c:
        if (r1 >= r6) goto L_0x01df;
    L_0x018e:
        r0 = 0;
        r2 = r7[r1];	 Catch:{ all -> 0x013c }
        r2 = android.telephony.TelephonyManager.getPhoneType(r2);	 Catch:{ all -> 0x013c }
        if (r2 != r3) goto L_0x01d0;
    L_0x0197:
        r0 = new com.android.internal.telephony.gsm.GSMPhone;	 Catch:{ all -> 0x013c }
        r5 = sCommandsInterfaces;	 Catch:{ all -> 0x013c }
        r5 = r5[r1];	 Catch:{ all -> 0x013c }
        r8 = sPhoneNotifier;	 Catch:{ all -> 0x013c }
        r0.<init>(r12, r5, r8, r1);	 Catch:{ all -> 0x013c }
    L_0x01a2:
        r5 = "PhoneFactory";
        r8 = new java.lang.StringBuilder;	 Catch:{ all -> 0x013c }
        r8.<init>();	 Catch:{ all -> 0x013c }
        r9 = "Creating Phone with type = ";
        r8 = r8.append(r9);	 Catch:{ all -> 0x013c }
        r2 = r8.append(r2);	 Catch:{ all -> 0x013c }
        r8 = " sub = ";
        r2 = r2.append(r8);	 Catch:{ all -> 0x013c }
        r2 = r2.append(r1);	 Catch:{ all -> 0x013c }
        r2 = r2.toString();	 Catch:{ all -> 0x013c }
        android.telephony.Rlog.i(r5, r2);	 Catch:{ all -> 0x013c }
        r2 = sProxyPhones;	 Catch:{ all -> 0x013c }
        r5 = new com.android.internal.telephony.PhoneProxy;	 Catch:{ all -> 0x013c }
        r5.<init>(r0);	 Catch:{ all -> 0x013c }
        r2[r1] = r5;	 Catch:{ all -> 0x013c }
        r1 = r1 + 1;
        goto L_0x018c;
    L_0x01d0:
        r5 = 2;
        if (r2 != r5) goto L_0x01a2;
    L_0x01d3:
        r0 = new com.android.internal.telephony.cdma.CDMALTEPhone;	 Catch:{ all -> 0x013c }
        r5 = sCommandsInterfaces;	 Catch:{ all -> 0x013c }
        r5 = r5[r1];	 Catch:{ all -> 0x013c }
        r8 = sPhoneNotifier;	 Catch:{ all -> 0x013c }
        r0.<init>(r12, r5, r8, r1);	 Catch:{ all -> 0x013c }
        goto L_0x01a2;
    L_0x01df:
        r0 = sProxyPhones;	 Catch:{ all -> 0x013c }
        r1 = mUiccController;	 Catch:{ all -> 0x013c }
        r2 = sCommandsInterfaces;	 Catch:{ all -> 0x013c }
        r0 = com.android.internal.telephony.ProxyController.getInstance(r12, r0, r1, r2);	 Catch:{ all -> 0x013c }
        mProxyController = r0;	 Catch:{ all -> 0x013c }
        r0 = sProxyPhones;	 Catch:{ all -> 0x013c }
        r1 = 0;
        r0 = r0[r1];	 Catch:{ all -> 0x013c }
        sProxyPhone = r0;	 Catch:{ all -> 0x013c }
        r0 = sCommandsInterfaces;	 Catch:{ all -> 0x013c }
        r1 = 0;
        r0 = r0[r1];	 Catch:{ all -> 0x013c }
        sCommandsInterface = r0;	 Catch:{ all -> 0x013c }
        r0 = 1;
        r1 = com.android.internal.telephony.SmsApplication.getDefaultSmsApplication(r12, r0);	 Catch:{ all -> 0x013c }
        r0 = "NONE";
        if (r1 == 0) goto L_0x0206;
    L_0x0202:
        r0 = r1.getPackageName();	 Catch:{ all -> 0x013c }
    L_0x0206:
        r1 = "PhoneFactory";
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x013c }
        r2.<init>();	 Catch:{ all -> 0x013c }
        r3 = "defaultSmsApplication: ";
        r2 = r2.append(r3);	 Catch:{ all -> 0x013c }
        r0 = r2.append(r0);	 Catch:{ all -> 0x013c }
        r0 = r0.toString();	 Catch:{ all -> 0x013c }
        android.telephony.Rlog.i(r1, r0);	 Catch:{ all -> 0x013c }
        com.android.internal.telephony.SmsApplication.initSmsPackageMonitor(r12);	 Catch:{ all -> 0x013c }
        r0 = 1;
        sMadeDefaults = r0;	 Catch:{ all -> 0x013c }
        r0 = "PhoneFactory";
        r1 = "Creating SubInfoRecordUpdater ";
        android.telephony.Rlog.i(r0, r1);	 Catch:{ all -> 0x013c }
        r0 = new com.android.internal.telephony.SubscriptionInfoUpdater;	 Catch:{ all -> 0x013c }
        r1 = sProxyPhones;	 Catch:{ all -> 0x013c }
        r2 = sCommandsInterfaces;	 Catch:{ all -> 0x013c }
        r0.<init>(r12, r1, r2);	 Catch:{ all -> 0x013c }
        sSubInfoRecordUpdater = r0;	 Catch:{ all -> 0x013c }
        r0 = com.android.internal.telephony.SubscriptionController.getInstance();	 Catch:{ all -> 0x013c }
        r1 = sProxyPhones;	 Catch:{ all -> 0x013c }
        r0.updatePhonesAvailability(r1);	 Catch:{ all -> 0x013c }
    L_0x023f:
        monitor-exit(r4);	 Catch:{ all -> 0x013c }
        return;
    L_0x0241:
        r2 = r0;
        goto L_0x008b;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.PhoneFactory.makeDefaultPhone(android.content.Context):void");
    }

    public static void makeDefaultPhones(Context context) {
        makeDefaultPhone(context);
    }

    public static ImsPhone makeImsPhone(PhoneNotifier phoneNotifier, Phone phone) {
        return ImsPhoneFactory.makePhone(sContext, phoneNotifier, phone);
    }

    public static SipPhone makeSipPhone(String str) {
        return SipPhoneFactory.makePhone(str, sContext, sPhoneNotifier);
    }

    public static void setDataSubscription(int i) {
        int i2 = 1;
        Global.putInt(sContext.getContentResolver(), "multi_sim_data_call", i);
        Rlog.d(LOG_TAG, "setDataSubscription: " + i);
        boolean z = Global.getInt(sContext.getContentResolver(), new StringBuilder().append("mobile_data").append(i).toString(), 0) != 0;
        Global.putInt(sContext.getContentResolver(), "mobile_data", z ? 1 : 0);
        Rlog.d(LOG_TAG, "set mobile_data: " + z);
        z = Global.getInt(sContext.getContentResolver(), new StringBuilder().append("data_roaming").append(i).toString(), 0) != 0;
        ContentResolver contentResolver = sContext.getContentResolver();
        if (!z) {
            i2 = 0;
        }
        Global.putInt(contentResolver, "data_roaming", i2);
        Rlog.d(LOG_TAG, "set data_roaming: " + z);
    }

    public static void setDefaultSubscription(int i) {
        SystemProperties.set("persist.radio.default.sub", Integer.toString(i));
        int phoneId = SubscriptionController.getInstance().getPhoneId(i);
        synchronized (sLockProxyPhones) {
            if (phoneId >= 0) {
                if (phoneId < sProxyPhones.length) {
                    sProxyPhone = sProxyPhones[phoneId];
                    sCommandsInterface = sCommandsInterfaces[phoneId];
                    sMadeDefaults = true;
                }
            }
        }
        String simOperator = TelephonyManager.getDefault().getSimOperator(phoneId);
        Rlog.d(LOG_TAG, "update mccmnc=" + simOperator);
        MccTable.updateMccMncConfiguration(sContext, simOperator, false);
        Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_SUBSCRIPTION_CHANGED");
        intent.addFlags(536870912);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneId);
        Rlog.d(LOG_TAG, "setDefaultSubscription : " + i + " Broadcasting Default Subscription Changed...");
        sContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    public static void setPromptEnabled(boolean z) {
        Global.putInt(sContext.getContentResolver(), "multi_sim_voice_prompt", !z ? 0 : 1);
        Rlog.d(LOG_TAG, "setVoicePromptOption to " + z);
    }

    public static void setSMSPromptEnabled(boolean z) {
        Global.putInt(sContext.getContentResolver(), "multi_sim_sms_prompt", !z ? 0 : 1);
        Rlog.d(LOG_TAG, "setSMSPromptOption to " + z);
    }

    public static void setSMSSubscription(int i) {
        Global.putInt(sContext.getContentResolver(), "multi_sim_sms", i);
        sContext.sendBroadcast(new Intent("com.android.mms.transaction.SEND_MESSAGE"));
        Rlog.d(LOG_TAG, "setSMSSubscription : " + i);
    }

    public static void setVoiceSubscription(int i) {
        Global.putInt(sContext.getContentResolver(), "multi_sim_voice_call", i);
        Rlog.d(LOG_TAG, "setVoiceSubscription : " + i);
    }
}
