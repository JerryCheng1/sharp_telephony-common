package com.android.internal.telephony;

import android.content.ContentValues;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.Rlog;
import com.android.internal.telephony.IIccPhoneBook.Stub;
import com.android.internal.telephony.uicc.AdnRecord;
import java.util.List;

public class UiccPhoneBookController extends Stub {
    private static final String TAG = "UiccPhoneBookController";
    private Phone[] mPhone;

    public UiccPhoneBookController(Phone[] phoneArr) {
        if (ServiceManager.getService("simphonebook") == null) {
            ServiceManager.addService("simphonebook", this);
        }
        this.mPhone = phoneArr;
    }

    private int getDefaultSubscription() {
        return SubscriptionController.getInstance().getDefaultSubId();
    }

    private IccPhoneBookInterfaceManagerProxy getIccPhoneBookInterfaceManagerProxy(int i) {
        try {
            return ((PhoneProxy) this.mPhone[SubscriptionController.getInstance().getPhoneId(i)]).getIccPhoneBookInterfaceManagerProxy();
        } catch (NullPointerException e) {
            Rlog.e(TAG, "Exception is :" + e.toString() + " For subscription :" + i);
            e.printStackTrace();
            return null;
        } catch (ArrayIndexOutOfBoundsException e2) {
            Rlog.e(TAG, "Exception is :" + e2.toString() + " For subscription :" + i);
            e2.printStackTrace();
            return null;
        }
    }

    public int getAdnCount() throws RemoteException {
        return getAdnCountUsingSubId(getDefaultSubscription());
    }

    public int getAdnCountUsingSubId(int i) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPhoneBookInterfaceManagerProxy = getIccPhoneBookInterfaceManagerProxy(i);
        if (iccPhoneBookInterfaceManagerProxy != null) {
            return iccPhoneBookInterfaceManagerProxy.getAdnCount();
        }
        Rlog.e(TAG, "getAdnCount iccPbkIntMgrProxy isnull for Subscription:" + i);
        return 0;
    }

    public List<AdnRecord> getAdnRecordsInEf(int i) throws RemoteException {
        return getAdnRecordsInEfForSubscriber(getDefaultSubscription(), i);
    }

    public List<AdnRecord> getAdnRecordsInEfForSubscriber(int i, int i2) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPhoneBookInterfaceManagerProxy = getIccPhoneBookInterfaceManagerProxy(i);
        if (iccPhoneBookInterfaceManagerProxy != null) {
            return iccPhoneBookInterfaceManagerProxy.getAdnRecordsInEf(i2);
        }
        Rlog.e(TAG, "getAdnRecordsInEf iccPbkIntMgrProxy isnull for Subscription:" + i);
        return null;
    }

    public int[] getAdnRecordsSize(int i) throws RemoteException {
        return getAdnRecordsSizeForSubscriber(getDefaultSubscription(), i);
    }

    public int[] getAdnRecordsSizeForSubscriber(int i, int i2) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPhoneBookInterfaceManagerProxy = getIccPhoneBookInterfaceManagerProxy(i);
        if (iccPhoneBookInterfaceManagerProxy != null) {
            return iccPhoneBookInterfaceManagerProxy.getAdnRecordsSize(i2);
        }
        Rlog.e(TAG, "getAdnRecordsSize iccPbkIntMgrProxy is null for Subscription:" + i);
        return null;
    }

    public int getAnrCount() throws RemoteException {
        return getAnrCountUsingSubId(getDefaultSubscription());
    }

    public int getAnrCountUsingSubId(int i) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPhoneBookInterfaceManagerProxy = getIccPhoneBookInterfaceManagerProxy(i);
        if (iccPhoneBookInterfaceManagerProxy != null) {
            return iccPhoneBookInterfaceManagerProxy.getAnrCount();
        }
        Rlog.e(TAG, "getAnrCount iccPbkIntMgrProxy isnull for Subscription:" + i);
        return 0;
    }

    public int getEmailCount() throws RemoteException {
        return getEmailCountUsingSubId(getDefaultSubscription());
    }

    public int getEmailCountUsingSubId(int i) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPhoneBookInterfaceManagerProxy = getIccPhoneBookInterfaceManagerProxy(i);
        if (iccPhoneBookInterfaceManagerProxy != null) {
            return iccPhoneBookInterfaceManagerProxy.getEmailCount();
        }
        Rlog.e(TAG, "getEmailCount iccPbkIntMgrProxy isnull for Subscription:" + i);
        return 0;
    }

    public int getSpareAnrCount() throws RemoteException {
        return getSpareAnrCountUsingSubId(getDefaultSubscription());
    }

    public int getSpareAnrCountUsingSubId(int i) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPhoneBookInterfaceManagerProxy = getIccPhoneBookInterfaceManagerProxy(i);
        if (iccPhoneBookInterfaceManagerProxy != null) {
            return iccPhoneBookInterfaceManagerProxy.getSpareAnrCount();
        }
        Rlog.e(TAG, "getSpareAnrCount iccPbkIntMgrProxy isnull for Subscription:" + i);
        return 0;
    }

    public int getSpareEmailCount() throws RemoteException {
        return getSpareEmailCountUsingSubId(getDefaultSubscription());
    }

    public int getSpareEmailCountUsingSubId(int i) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPhoneBookInterfaceManagerProxy = getIccPhoneBookInterfaceManagerProxy(i);
        if (iccPhoneBookInterfaceManagerProxy != null) {
            return iccPhoneBookInterfaceManagerProxy.getSpareEmailCount();
        }
        Rlog.e(TAG, "getSpareEmailCount iccPbkIntMgrProxy isnull for Subscription:" + i);
        return 0;
    }

    public boolean updateAdnRecordsInEfByIndex(int i, String str, String str2, int i2, String str3) throws RemoteException {
        return updateAdnRecordsInEfByIndexForSubscriber(getDefaultSubscription(), i, str, str2, i2, str3);
    }

    public boolean updateAdnRecordsInEfByIndexForSubscriber(int i, int i2, String str, String str2, int i3, String str3) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPhoneBookInterfaceManagerProxy = getIccPhoneBookInterfaceManagerProxy(i);
        if (iccPhoneBookInterfaceManagerProxy != null) {
            return iccPhoneBookInterfaceManagerProxy.updateAdnRecordsInEfByIndex(i2, str, str2, i3, str3);
        }
        Rlog.e(TAG, "updateAdnRecordsInEfByIndex iccPbkIntMgrProxy is null for Subscription:" + i);
        return false;
    }

    public boolean updateAdnRecordsInEfBySearch(int i, String str, String str2, String str3, String str4, String str5) throws RemoteException {
        return updateAdnRecordsInEfBySearchForSubscriber(getDefaultSubscription(), i, str, str2, str3, str4, str5);
    }

    public boolean updateAdnRecordsInEfBySearchForSubscriber(int i, int i2, String str, String str2, String str3, String str4, String str5) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPhoneBookInterfaceManagerProxy = getIccPhoneBookInterfaceManagerProxy(i);
        if (iccPhoneBookInterfaceManagerProxy != null) {
            return iccPhoneBookInterfaceManagerProxy.updateAdnRecordsInEfBySearch(i2, str, str2, str3, str4, str5);
        }
        Rlog.e(TAG, "updateAdnRecordsInEfBySearch iccPbkIntMgrProxy is null for Subscription:" + i);
        return false;
    }

    public boolean updateAdnRecordsWithContentValuesInEfBySearch(int i, ContentValues contentValues, String str) throws RemoteException {
        return updateAdnRecordsWithContentValuesInEfBySearchUsingSubId(getDefaultSubscription(), i, contentValues, str);
    }

    public boolean updateAdnRecordsWithContentValuesInEfBySearchUsingSubId(int i, int i2, ContentValues contentValues, String str) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPhoneBookInterfaceManagerProxy = getIccPhoneBookInterfaceManagerProxy(i);
        if (iccPhoneBookInterfaceManagerProxy != null) {
            return iccPhoneBookInterfaceManagerProxy.updateAdnRecordsWithContentValuesInEfBySearch(i2, contentValues, str);
        }
        Rlog.e(TAG, "updateAdnRecordsWithContentValuesInEfBySearchUsingSubId iccPbkIntMgrProxy is null for Subscription:" + i);
        return false;
    }
}
