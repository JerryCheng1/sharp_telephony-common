package com.android.internal.telephony;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
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

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class PhoneFactory {
    static final String LOG_TAG = "PhoneFactory";
    static final int SOCKET_OPEN_MAX_RETRY = 3;
    static final int SOCKET_OPEN_RETRY_MILLIS = 2000;
    private static ProxyController mProxyController;
    private static UiccController mUiccController;
    private static Context sContext;
    private static ModemBindingPolicyHandler sModemBindingPolicyHandler;
    private static ModemStackController sModemStackController;
    private static PhoneNotifier sPhoneNotifier;
    static final Object sLockProxyPhones = new Object();
    private static PhoneProxy[] sProxyPhones = null;
    private static PhoneProxy sProxyPhone = null;
    private static CommandsInterface[] sCommandsInterfaces = null;
    private static CommandsInterface sCommandsInterface = null;
    private static SubscriptionInfoUpdater sSubInfoRecordUpdater = null;
    private static boolean sMadeDefaults = false;

    public static void makeDefaultPhones(Context context) {
        makeDefaultPhone(context);
    }

    /* JADX WARN: Code restructure failed: missing block: B:18:0x0093, code lost:
        com.android.internal.telephony.PhoneFactory.sPhoneNotifier = new com.android.internal.telephony.DefaultPhoneNotifier();
        r18 = com.android.internal.telephony.RILConstants.PREFERRED_NETWORK_MODE;
     */
    /* JADX WARN: Code restructure failed: missing block: B:19:0x00a6, code lost:
        if (android.telephony.TelephonyManager.getLteOnCdmaModeStatic() != 1) goto L_0x00aa;
     */
    /* JADX WARN: Code restructure failed: missing block: B:20:0x00a8, code lost:
        r18 = 7;
     */
    /* JADX WARN: Code restructure failed: missing block: B:21:0x00aa, code lost:
        r4 = com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager.getDefault(r27);
        android.telephony.Rlog.i(com.android.internal.telephony.PhoneFactory.LOG_TAG, "Cdma Subscription set to " + r4);
        r14 = android.telephony.TelephonyManager.getDefault().getPhoneCount();
        r13 = new int[r14];
        com.android.internal.telephony.PhoneFactory.sProxyPhones = new com.android.internal.telephony.PhoneProxy[r14];
        com.android.internal.telephony.PhoneFactory.sCommandsInterfaces = new com.android.internal.telephony.RIL[r14];
     */
    /* JADX WARN: Code restructure failed: missing block: B:22:0x00e2, code lost:
        r10 = 0;
     */
    /* JADX WARN: Code restructure failed: missing block: B:23:0x00e3, code lost:
        if (r10 >= r14) goto L_0x01be;
     */
    /* JADX WARN: Code restructure failed: missing block: B:24:0x00e5, code lost:
        r13[r10] = android.telephony.TelephonyManager.getIntAtIndex(r27.getContentResolver(), "preferred_network_mode", r10);
     */
    /* JADX WARN: Code restructure failed: missing block: B:47:0x01af, code lost:
        android.telephony.Rlog.e(com.android.internal.telephony.PhoneFactory.LOG_TAG, "Settings Exception Reading Value At Index for Settings.Global.PREFERRED_NETWORK_MODE");
        r13[r10] = r18;
     */
    /* JADX WARN: Code restructure failed: missing block: B:48:0x01be, code lost:
        android.telephony.Rlog.i(com.android.internal.telephony.PhoneFactory.LOG_TAG, "Creating SubscriptionController");
        com.android.internal.telephony.SubscriptionController.init(r27, com.android.internal.telephony.PhoneFactory.sCommandsInterfaces);
        com.android.internal.telephony.PhoneFactory.mUiccController = com.android.internal.telephony.uicc.UiccController.make(r27, com.android.internal.telephony.PhoneFactory.sCommandsInterfaces);
        com.android.internal.telephony.PhoneFactory.sModemStackController = com.android.internal.telephony.ModemStackController.make(r27, com.android.internal.telephony.PhoneFactory.mUiccController, com.android.internal.telephony.PhoneFactory.sCommandsInterfaces);
        com.android.internal.telephony.PhoneFactory.sModemBindingPolicyHandler = com.android.internal.telephony.ModemBindingPolicyHandler.make(r27, com.android.internal.telephony.PhoneFactory.mUiccController, com.android.internal.telephony.PhoneFactory.sCommandsInterfaces);
        r10 = 0;
     */
    /* JADX WARN: Code restructure failed: missing block: B:49:0x01ff, code lost:
        if (r10 >= r14) goto L_0x027c;
     */
    /* JADX WARN: Code restructure failed: missing block: B:50:0x0201, code lost:
        r16 = null;
        r17 = android.telephony.TelephonyManager.getPhoneType(r13[r10]);
     */
    /* JADX WARN: Code restructure failed: missing block: B:51:0x020f, code lost:
        if (r17 != 1) goto L_0x0260;
     */
    /* JADX WARN: Code restructure failed: missing block: B:52:0x0211, code lost:
        r16 = new com.android.internal.telephony.gsm.GSMPhone(r27, com.android.internal.telephony.PhoneFactory.sCommandsInterfaces[r10], com.android.internal.telephony.PhoneFactory.sPhoneNotifier, r10);
     */
    /* JADX WARN: Code restructure failed: missing block: B:53:0x0224, code lost:
        android.telephony.Rlog.i(com.android.internal.telephony.PhoneFactory.LOG_TAG, "Creating Phone with type = " + r17 + " sub = " + r10);
        com.android.internal.telephony.PhoneFactory.sProxyPhones[r10] = new com.android.internal.telephony.PhoneProxy(r16);
        r10 = r10 + 1;
     */
    /* JADX WARN: Code restructure failed: missing block: B:55:0x0266, code lost:
        if (r17 != 2) goto L_0x0224;
     */
    /* JADX WARN: Code restructure failed: missing block: B:56:0x0268, code lost:
        r16 = new com.android.internal.telephony.cdma.CDMALTEPhone(r27, com.android.internal.telephony.PhoneFactory.sCommandsInterfaces[r10], com.android.internal.telephony.PhoneFactory.sPhoneNotifier, r10);
     */
    /* JADX WARN: Code restructure failed: missing block: B:57:0x027c, code lost:
        com.android.internal.telephony.PhoneFactory.mProxyController = com.android.internal.telephony.ProxyController.getInstance(r27, com.android.internal.telephony.PhoneFactory.sProxyPhones, com.android.internal.telephony.PhoneFactory.mUiccController, com.android.internal.telephony.PhoneFactory.sCommandsInterfaces);
        com.android.internal.telephony.PhoneFactory.sProxyPhone = com.android.internal.telephony.PhoneFactory.sProxyPhones[0];
        com.android.internal.telephony.PhoneFactory.sCommandsInterface = com.android.internal.telephony.PhoneFactory.sCommandsInterfaces[0];
        r5 = com.android.internal.telephony.SmsApplication.getDefaultSmsApplication(r27, true);
        r15 = "NONE";
     */
    /* JADX WARN: Code restructure failed: missing block: B:58:0x02ac, code lost:
        if (r5 == null) goto L_0x02b2;
     */
    /* JADX WARN: Code restructure failed: missing block: B:59:0x02ae, code lost:
        r15 = r5.getPackageName();
     */
    /* JADX WARN: Code restructure failed: missing block: B:60:0x02b2, code lost:
        android.telephony.Rlog.i(com.android.internal.telephony.PhoneFactory.LOG_TAG, "defaultSmsApplication: " + r15);
        com.android.internal.telephony.SmsApplication.initSmsPackageMonitor(r27);
        com.android.internal.telephony.PhoneFactory.sMadeDefaults = true;
        android.telephony.Rlog.i(com.android.internal.telephony.PhoneFactory.LOG_TAG, "Creating SubInfoRecordUpdater ");
        com.android.internal.telephony.PhoneFactory.sSubInfoRecordUpdater = new com.android.internal.telephony.SubscriptionInfoUpdater(r27, com.android.internal.telephony.PhoneFactory.sProxyPhones, com.android.internal.telephony.PhoneFactory.sCommandsInterfaces);
        com.android.internal.telephony.SubscriptionController.getInstance().updatePhonesAvailability(com.android.internal.telephony.PhoneFactory.sProxyPhones);
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public static void makeDefaultPhone(android.content.Context r27) {
        /*
            Method dump skipped, instructions count: 772
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.PhoneFactory.makeDefaultPhone(android.content.Context):void");
    }

    public static Phone getCdmaPhone(int phoneId) {
        Phone phone;
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            phone = new CDMALTEPhone(sContext, sCommandsInterfaces[phoneId], sPhoneNotifier, phoneId);
        }
        return phone;
    }

    public static Phone getGsmPhone(int phoneId) {
        Phone phone;
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            phone = new GSMPhone(sContext, sCommandsInterfaces[phoneId], sPhoneNotifier, phoneId);
        }
        return phone;
    }

    public static Phone getDefaultPhone() {
        PhoneProxy phoneProxy;
        synchronized (sLockProxyPhones) {
            if (!sMadeDefaults) {
                throw new IllegalStateException("Default phones haven't been made yet!");
            }
            phoneProxy = sProxyPhone;
        }
        return phoneProxy;
    }

    public static Phone getPhone(int phoneId) {
        Phone phone;
        synchronized (sLockProxyPhones) {
            if (!sMadeDefaults) {
                throw new IllegalStateException("Default phones haven't been made yet!");
            }
            if (phoneId == Integer.MAX_VALUE) {
                Rlog.d(LOG_TAG, "getPhone: phoneId == DEFAULT_PHONE_ID");
                phone = sProxyPhone;
            } else {
                Rlog.d(LOG_TAG, "getPhone: phoneId != DEFAULT_PHONE_ID");
                phone = (phoneId < 0 || phoneId >= TelephonyManager.getDefault().getPhoneCount()) ? null : sProxyPhones[phoneId];
            }
            Rlog.d(LOG_TAG, "getPhone:- phone=" + phone);
        }
        return phone;
    }

    public static Phone[] getPhones() {
        PhoneProxy[] phoneProxyArr;
        synchronized (sLockProxyPhones) {
            if (!sMadeDefaults) {
                throw new IllegalStateException("Default phones haven't been made yet!");
            }
            phoneProxyArr = sProxyPhones;
        }
        return phoneProxyArr;
    }

    public static Context getContext() {
        return sContext;
    }

    public static SipPhone makeSipPhone(String sipUri) {
        return SipPhoneFactory.makePhone(sipUri, sContext, sPhoneNotifier);
    }

    public static void setDefaultSubscription(int subId) {
        SystemProperties.set("persist.radio.default.sub", Integer.toString(subId));
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        synchronized (sLockProxyPhones) {
            if (phoneId >= 0) {
                if (phoneId < sProxyPhones.length) {
                    sProxyPhone = sProxyPhones[phoneId];
                    sCommandsInterface = sCommandsInterfaces[phoneId];
                    sMadeDefaults = true;
                }
            }
        }
        String defaultMccMnc = TelephonyManager.getDefault().getSimOperator(phoneId);
        Rlog.d(LOG_TAG, "update mccmnc=" + defaultMccMnc);
        MccTable.updateMccMncConfiguration(sContext, defaultMccMnc, false);
        Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_SUBSCRIPTION_CHANGED");
        intent.addFlags(536870912);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneId);
        Rlog.d(LOG_TAG, "setDefaultSubscription : " + subId + " Broadcasting Default Subscription Changed...");
        sContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    public static int calculatePreferredNetworkType(Context context) {
        return calculatePreferredNetworkType(context, getDefaultPhoneId());
    }

    public static int calculatePreferredNetworkType(Context context, int phoneId) {
        int preferredNetworkType = RILConstants.PREFERRED_NETWORK_MODE;
        if (TelephonyManager.getLteOnCdmaModeStatic(phoneId) == 1) {
            preferredNetworkType = 7;
        }
        int networkType = preferredNetworkType;
        try {
            networkType = TelephonyManager.getIntAtIndex(context.getContentResolver(), "preferred_network_mode", phoneId);
        } catch (Settings.SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Value At Index for Settings.Global.PREFERRED_NETWORK_MODE");
        }
        Rlog.d(LOG_TAG, "calculatePreferredNetworkType: phoneId = " + phoneId);
        return networkType;
    }

    public static int getDefaultSubscription() {
        return SubscriptionController.getInstance().getDefaultSubId();
    }

    public static int getVoiceSubscription() {
        int subId = -1;
        try {
            subId = Settings.Global.getInt(sContext.getContentResolver(), "multi_sim_voice_call");
        } catch (Settings.SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim Voice Call Values");
        }
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            return subId;
        }
        Rlog.i(LOG_TAG, "Subscription is invalid..." + subId + " Set to 0");
        setVoiceSubscription(0);
        return 0;
    }

    public static boolean isPromptEnabled() {
        int value = 0;
        try {
            value = Settings.Global.getInt(sContext.getContentResolver(), "multi_sim_voice_prompt");
        } catch (Settings.SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim Voice Prompt Values");
        }
        boolean prompt = value != 0;
        Rlog.d(LOG_TAG, "Prompt option:" + prompt);
        return prompt;
    }

    public static void setPromptEnabled(boolean enabled) {
        Settings.Global.putInt(sContext.getContentResolver(), "multi_sim_voice_prompt", !enabled ? 0 : 1);
        Rlog.d(LOG_TAG, "setVoicePromptOption to " + enabled);
    }

    public static boolean isSMSPromptEnabled() {
        int value = 0;
        try {
            value = Settings.Global.getInt(sContext.getContentResolver(), "multi_sim_sms_prompt");
        } catch (Settings.SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim SMS Prompt Values");
        }
        boolean prompt = value != 0;
        Rlog.d(LOG_TAG, "SMS Prompt option:" + prompt);
        return prompt;
    }

    public static void setSMSPromptEnabled(boolean enabled) {
        Settings.Global.putInt(sContext.getContentResolver(), "multi_sim_sms_prompt", !enabled ? 0 : 1);
        Rlog.d(LOG_TAG, "setSMSPromptOption to " + enabled);
    }

    public static int getDataSubscription() {
        int subId = -1;
        try {
            subId = Settings.Global.getInt(sContext.getContentResolver(), "multi_sim_data_call");
        } catch (Settings.SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim Data Call Values");
        }
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            return subId;
        }
        Rlog.i(LOG_TAG, "Subscription is invalid...0 Set to 0");
        setDataSubscription(0);
        return 0;
    }

    public static int getSMSSubscription() {
        int subId = -1;
        try {
            subId = Settings.Global.getInt(sContext.getContentResolver(), "multi_sim_sms");
        } catch (Settings.SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim SMS Values");
        }
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            return subId;
        }
        Rlog.i(LOG_TAG, "Subscription is invalid..." + subId + " Set to 0");
        setSMSSubscription(0);
        return 0;
    }

    public static void setVoiceSubscription(int subId) {
        Settings.Global.putInt(sContext.getContentResolver(), "multi_sim_voice_call", subId);
        Rlog.d(LOG_TAG, "setVoiceSubscription : " + subId);
    }

    public static void setDataSubscription(int subId) {
        boolean enabled;
        int i;
        int i2 = 1;
        Settings.Global.putInt(sContext.getContentResolver(), "multi_sim_data_call", subId);
        Rlog.d(LOG_TAG, "setDataSubscription: " + subId);
        if (Settings.Global.getInt(sContext.getContentResolver(), "mobile_data" + subId, 0) != 0) {
            enabled = true;
        } else {
            enabled = false;
        }
        ContentResolver contentResolver = sContext.getContentResolver();
        if (enabled) {
            i = 1;
        } else {
            i = 0;
        }
        Settings.Global.putInt(contentResolver, "mobile_data", i);
        Rlog.d(LOG_TAG, "set mobile_data: " + enabled);
        boolean enabled2 = Settings.Global.getInt(sContext.getContentResolver(), new StringBuilder().append("data_roaming").append(subId).toString(), 0) != 0;
        ContentResolver contentResolver2 = sContext.getContentResolver();
        if (!enabled2) {
            i2 = 0;
        }
        Settings.Global.putInt(contentResolver2, "data_roaming", i2);
        Rlog.d(LOG_TAG, "set data_roaming: " + enabled2);
    }

    public static void setSMSSubscription(int subId) {
        Settings.Global.putInt(sContext.getContentResolver(), "multi_sim_sms", subId);
        sContext.sendBroadcast(new Intent("com.android.mms.transaction.SEND_MESSAGE"));
        Rlog.d(LOG_TAG, "setSMSSubscription : " + subId);
    }

    public static ImsPhone makeImsPhone(PhoneNotifier phoneNotifier, Phone defaultPhone) {
        return ImsPhoneFactory.makePhone(sContext, phoneNotifier, defaultPhone);
    }

    private static boolean isValidphoneId(int phoneId) {
        return phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount();
    }

    private static int getDefaultPhoneId() {
        int phoneId = SubscriptionController.getInstance().getPhoneId(getDefaultSubscription());
        if (!isValidphoneId(phoneId)) {
            return 0;
        }
        return phoneId;
    }

    public static SubscriptionInfoUpdater getSubscriptionInfoUpdater() {
        return sSubInfoRecordUpdater;
    }

    public static void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("PhoneFactory:");
        PhoneProxy[] phones = (PhoneProxy[]) getPhones();
        int i = -1;
        for (PhoneProxy phoneProxy : phones) {
            i++;
            try {
                ((PhoneBase) phoneProxy.getActivePhone()).dump(fd, pw, args);
                pw.flush();
                pw.println("++++++++++++++++++++++++++++++++");
                try {
                    ((IccCardProxy) phoneProxy.getIccCard()).dump(fd, pw, args);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                pw.flush();
                pw.println("++++++++++++++++++++++++++++++++");
            } catch (Exception e2) {
                pw.println("Telephony DebugService: Could not get Phone[" + i + "] e=" + e2);
            }
        }
        try {
            mUiccController.dump(fd, pw, args);
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        try {
            SubscriptionController.getInstance().dump(fd, pw, args);
        } catch (Exception e4) {
            e4.printStackTrace();
        }
        pw.flush();
    }
}
