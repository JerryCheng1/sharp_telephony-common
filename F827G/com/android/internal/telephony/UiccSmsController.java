package com.android.internal.telephony;

import android.app.ActivityThread;
import android.app.PendingIntent;
import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.ISms.Stub;
import java.util.List;

public class UiccSmsController extends Stub {
    static final String LOG_TAG = "RIL_UiccSmsController";
    protected Phone[] mPhone;

    protected UiccSmsController(Phone[] phoneArr) {
        this.mPhone = phoneArr;
        if (ServiceManager.getService("isms") == null) {
            ServiceManager.addService("isms", this);
        }
    }

    private int getDefaultSmsSubId() {
        return SubscriptionController.getInstance().getDefaultSmsSubId();
    }

    private IccSmsInterfaceManager getIccSmsInterfaceManager(int i) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(i);
        if (!SubscriptionManager.isValidPhoneId(phoneId) || phoneId == Integer.MAX_VALUE) {
            phoneId = 0;
        }
        try {
            return ((PhoneProxy) this.mPhone[phoneId]).getIccSmsInterfaceManager();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Exception is :" + e.toString() + " For subscription :" + i);
            e.printStackTrace();
            return null;
        } catch (ArrayIndexOutOfBoundsException e2) {
            Rlog.e(LOG_TAG, "Exception is :" + e2.toString() + " For subscription :" + i);
            e2.printStackTrace();
            return null;
        }
    }

    public boolean copyMessageToIccEf(String str, int i, byte[] bArr, byte[] bArr2) throws RemoteException {
        return copyMessageToIccEfForSubscriber(getDefaultSmsSubId(), str, i, bArr, bArr2);
    }

    public boolean copyMessageToIccEfForSubscriber(int i, String str, int i2, byte[] bArr, byte[] bArr2) throws RemoteException {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            return iccSmsInterfaceManager.copyMessageToIccEf(str, i2, bArr, bArr2);
        }
        Rlog.e(LOG_TAG, "copyMessageToIccEf iccSmsIntMgr is null for Subscription: " + i);
        return false;
    }

    public boolean disableCellBroadcast(int i, int i2) throws RemoteException {
        return disableCellBroadcastForSubscriber(getPreferredSmsSubscription(), i, i2);
    }

    public boolean disableCellBroadcastForSubscriber(int i, int i2, int i3) throws RemoteException {
        return disableCellBroadcastRangeForSubscriber(i, i2, i2, i3);
    }

    public boolean disableCellBroadcastRange(int i, int i2, int i3) throws RemoteException {
        return disableCellBroadcastRangeForSubscriber(getPreferredSmsSubscription(), i, i2, i3);
    }

    public boolean disableCellBroadcastRangeForSubscriber(int i, int i2, int i3, int i4) throws RemoteException {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            return iccSmsInterfaceManager.disableCellBroadcastRange(i2, i3, i4);
        }
        Rlog.e(LOG_TAG, "disableCellBroadcast iccSmsIntMgr is null for Subscription:" + i);
        return false;
    }

    public boolean enableCellBroadcast(int i, int i2) throws RemoteException {
        return enableCellBroadcastForSubscriber(getPreferredSmsSubscription(), i, i2);
    }

    public boolean enableCellBroadcastForSubscriber(int i, int i2, int i3) throws RemoteException {
        return enableCellBroadcastRangeForSubscriber(i, i2, i2, i3);
    }

    public boolean enableCellBroadcastRange(int i, int i2, int i3) throws RemoteException {
        return enableCellBroadcastRangeForSubscriber(getPreferredSmsSubscription(), i, i2, i3);
    }

    public boolean enableCellBroadcastRangeForSubscriber(int i, int i2, int i3, int i4) throws RemoteException {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            return iccSmsInterfaceManager.enableCellBroadcastRange(i2, i3, i4);
        }
        Rlog.e(LOG_TAG, "enableCellBroadcast iccSmsIntMgr is null for Subscription: " + i);
        return false;
    }

    public List<SmsRawData> getAllMessagesFromIccEf(String str) throws RemoteException {
        return getAllMessagesFromIccEfForSubscriber(getDefaultSmsSubId(), str);
    }

    public List<SmsRawData> getAllMessagesFromIccEfForSubscriber(int i, String str) throws RemoteException {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            return iccSmsInterfaceManager.getAllMessagesFromIccEf(str);
        }
        Rlog.e(LOG_TAG, "getAllMessagesFromIccEf iccSmsIntMgr is null for Subscription: " + i);
        return null;
    }

    public String getImsSmsFormat() {
        return getImsSmsFormatForSubscriber(getDefaultSmsSubId());
    }

    public String getImsSmsFormatForSubscriber(int i) {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            return iccSmsInterfaceManager.getImsSmsFormat();
        }
        Rlog.e(LOG_TAG, "getImsSmsFormat iccSmsIntMgr is null");
        return null;
    }

    public int getPreferredSmsSubscription() {
        return SubscriptionController.getInstance().getDefaultSmsSubId();
    }

    public int getPremiumSmsPermission(String str) {
        return getPremiumSmsPermissionForSubscriber(getDefaultSmsSubId(), str);
    }

    public int getPremiumSmsPermissionForSubscriber(int i, String str) {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            return iccSmsInterfaceManager.getPremiumSmsPermission(str);
        }
        Rlog.e(LOG_TAG, "getPremiumSmsPermission iccSmsIntMgr is null");
        return 0;
    }

    public int getSmsCapacityOnIccForSubscriber(int i) throws RemoteException {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            return iccSmsInterfaceManager.getSmsCapacityOnIcc();
        }
        Rlog.e(LOG_TAG, "iccSmsIntMgr is null for  subId: " + i);
        return -1;
    }

    public String getSmscAddressFromIccForSubscriber(int i) throws RemoteException {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            return iccSmsInterfaceManager.getSmscAddressFromIcc();
        }
        Rlog.e(LOG_TAG, "iccSmsIntMgr is null for  subId: " + i);
        return null;
    }

    public void injectSmsPdu(int i, byte[] bArr, String str, PendingIntent pendingIntent) {
        getIccSmsInterfaceManager(i).injectSmsPdu(bArr, str, pendingIntent);
    }

    public void injectSmsPdu(byte[] bArr, String str, PendingIntent pendingIntent) {
        injectSmsPdu(SubscriptionManager.getDefaultSmsSubId(), bArr, str, pendingIntent);
    }

    public boolean isImsSmsSupported() {
        return isImsSmsSupportedForSubscriber(getDefaultSmsSubId());
    }

    public boolean isImsSmsSupportedForSubscriber(int i) {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            return iccSmsInterfaceManager.isImsSmsSupported();
        }
        Rlog.e(LOG_TAG, "isImsSmsSupported iccSmsIntMgr is null");
        return false;
    }

    public boolean isSMSPromptEnabled() {
        return PhoneFactory.isSMSPromptEnabled();
    }

    public boolean isSmsSimPickActivityNeeded(int i) {
        Context applicationContext = ActivityThread.currentApplication().getApplicationContext();
        TelephonyManager telephonyManager = (TelephonyManager) applicationContext.getSystemService("phone");
        long clearCallingIdentity = Binder.clearCallingIdentity();
        try {
            List activeSubscriptionInfoList = SubscriptionManager.from(applicationContext).getActiveSubscriptionInfoList();
            if (activeSubscriptionInfoList != null) {
                int size = activeSubscriptionInfoList.size();
                for (int i2 = 0; i2 < size; i2++) {
                    SubscriptionInfo subscriptionInfo = (SubscriptionInfo) activeSubscriptionInfoList.get(i2);
                    if (subscriptionInfo != null && subscriptionInfo.getSubscriptionId() == i) {
                        break;
                    }
                }
                if (size > 0 && telephonyManager.getSimCount() > 1) {
                    return true;
                }
            }
            return false;
        } finally {
            Binder.restoreCallingIdentity(clearCallingIdentity);
        }
    }

    public void sendData(String str, String str2, String str3, int i, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        sendDataForSubscriber(getDefaultSmsSubId(), str, str2, str3, i, bArr, pendingIntent, pendingIntent2);
    }

    public void sendDataForSubscriber(int i, String str, String str2, String str3, int i2, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            iccSmsInterfaceManager.sendData(str, str2, str3, i2, bArr, pendingIntent, pendingIntent2);
        } else {
            Rlog.e(LOG_TAG, "sendText iccSmsIntMgr is null for Subscription: " + i);
        }
    }

    public void sendDataWithOrigPort(String str, String str2, String str3, int i, int i2, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        sendDataWithOrigPortUsingSubscriber(getDefaultSmsSubId(), str, str2, str3, i, i2, bArr, pendingIntent, pendingIntent2);
    }

    public void sendDataWithOrigPortUsingSubscriber(int i, String str, String str2, String str3, int i2, int i3, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            iccSmsInterfaceManager.sendDataWithOrigPort(str, str2, str3, i2, i3, bArr, pendingIntent, pendingIntent2);
        } else {
            Rlog.e(LOG_TAG, "sendTextWithOrigPort iccSmsIntMgr is null for Subscription: " + i);
        }
    }

    public void sendMultipartText(String str, String str2, String str3, List<String> list, List<PendingIntent> list2, List<PendingIntent> list3) throws RemoteException {
        sendMultipartTextForSubscriber(getDefaultSmsSubId(), str, str2, str3, list, list2, list3);
    }

    public void sendMultipartTextForSubscriber(int i, String str, String str2, String str3, List<String> list, List<PendingIntent> list2, List<PendingIntent> list3) throws RemoteException {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            iccSmsInterfaceManager.sendMultipartText(str, str2, str3, list, list2, list3);
        } else {
            Rlog.e(LOG_TAG, "sendMultipartText iccSmsIntMgr is null for Subscription: " + i);
        }
    }

    public void sendMultipartTextWithOptionsUsingSubscriber(int i, String str, String str2, String str3, List<String> list, List<PendingIntent> list2, List<PendingIntent> list3, int i2, boolean z, int i3) {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            iccSmsInterfaceManager.sendMultipartTextWithOptions(str, str2, str3, list, list2, list3, i2, z, i3);
        } else {
            Rlog.e(LOG_TAG, "sendMultipartTextWithOptions iccSmsIntMgr is null for Subscription: " + i);
        }
    }

    public void sendStoredMultipartText(int i, String str, Uri uri, String str2, List<PendingIntent> list, List<PendingIntent> list2) throws RemoteException {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            iccSmsInterfaceManager.sendStoredMultipartText(str, uri, str2, list, list2);
        } else {
            Rlog.e(LOG_TAG, "sendStoredMultipartText iccSmsIntMgr is null for subscription: " + i);
        }
    }

    public void sendStoredText(int i, String str, Uri uri, String str2, PendingIntent pendingIntent, PendingIntent pendingIntent2) throws RemoteException {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            iccSmsInterfaceManager.sendStoredText(str, uri, str2, pendingIntent, pendingIntent2);
        } else {
            Rlog.e(LOG_TAG, "sendStoredText iccSmsIntMgr is null for subscription: " + i);
        }
    }

    public void sendText(String str, String str2, String str3, String str4, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        sendTextForSubscriber(getDefaultSmsSubId(), str, str2, str3, str4, pendingIntent, pendingIntent2);
    }

    public void sendTextForSubscriber(int i, String str, String str2, String str3, String str4, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            iccSmsInterfaceManager.sendText(str, str2, str3, str4, pendingIntent, pendingIntent2);
        } else {
            Rlog.e(LOG_TAG, "sendText iccSmsIntMgr is null for Subscription: " + i);
        }
    }

    public void sendTextWithOptionsUsingSubscriber(int i, String str, String str2, String str3, String str4, PendingIntent pendingIntent, PendingIntent pendingIntent2, int i2, boolean z, int i3) {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            iccSmsInterfaceManager.sendTextWithOptions(str, str2, str3, str4, pendingIntent, pendingIntent2, i2, z, i3);
        } else {
            Rlog.e(LOG_TAG, "sendTextWithOptions iccSmsIntMgr is null for Subscription: " + i);
        }
    }

    public void setPremiumSmsPermission(String str, int i) {
        setPremiumSmsPermissionForSubscriber(getDefaultSmsSubId(), str, i);
    }

    public void setPremiumSmsPermissionForSubscriber(int i, String str, int i2) {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            iccSmsInterfaceManager.setPremiumSmsPermission(str, i2);
        } else {
            Rlog.e(LOG_TAG, "setPremiumSmsPermission iccSmsIntMgr is null");
        }
    }

    public boolean setSmscAddressToIccForSubscriber(int i, String str) throws RemoteException {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            return iccSmsInterfaceManager.setSmscAddressToIcc(str);
        }
        Rlog.e(LOG_TAG, "iccSmsIntMgr is null for  subId: " + i);
        return false;
    }

    public boolean updateMessageOnIccEf(String str, int i, int i2, byte[] bArr) throws RemoteException {
        return updateMessageOnIccEfForSubscriber(getDefaultSmsSubId(), str, i, i2, bArr);
    }

    public boolean updateMessageOnIccEfForSubscriber(int i, String str, int i2, int i3, byte[] bArr) throws RemoteException {
        IccSmsInterfaceManager iccSmsInterfaceManager = getIccSmsInterfaceManager(i);
        if (iccSmsInterfaceManager != null) {
            return iccSmsInterfaceManager.updateMessageOnIccEf(str, i2, i3, bArr);
        }
        Rlog.e(LOG_TAG, "updateMessageOnIccEf iccSmsIntMgr is null for Subscription: " + i);
        return false;
    }
}
