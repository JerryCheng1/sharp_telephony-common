package com.android.internal.telephony;

import android.content.ContentValues;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.Rlog;
import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.uicc.AdnRecord;
import java.util.List;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class UiccPhoneBookController extends IIccPhoneBook.Stub {
    private static final String TAG = "UiccPhoneBookController";
    private Phone[] mPhone;

    public UiccPhoneBookController(Phone[] phone) {
        if (ServiceManager.getService("simphonebook") == null) {
            ServiceManager.addService("simphonebook", this);
        }
        this.mPhone = phone;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public boolean updateAdnRecordsInEfBySearch(int efid, String oldTag, String oldPhoneNumber, String newTag, String newPhoneNumber, String pin2) throws RemoteException {
        return updateAdnRecordsInEfBySearchForSubscriber(getDefaultSubscription(), efid, oldTag, oldPhoneNumber, newTag, newPhoneNumber, pin2);
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public boolean updateAdnRecordsInEfBySearchForSubscriber(int subId, int efid, String oldTag, String oldPhoneNumber, String newTag, String newPhoneNumber, String pin2) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.updateAdnRecordsInEfBySearch(efid, oldTag, oldPhoneNumber, newTag, newPhoneNumber, pin2);
        }
        Rlog.e(TAG, "updateAdnRecordsInEfBySearch iccPbkIntMgrProxy is null for Subscription:" + subId);
        return false;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public boolean updateAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber, int index, String pin2) throws RemoteException {
        return updateAdnRecordsInEfByIndexForSubscriber(getDefaultSubscription(), efid, newTag, newPhoneNumber, index, pin2);
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public boolean updateAdnRecordsInEfByIndexForSubscriber(int subId, int efid, String newTag, String newPhoneNumber, int index, String pin2) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.updateAdnRecordsInEfByIndex(efid, newTag, newPhoneNumber, index, pin2);
        }
        Rlog.e(TAG, "updateAdnRecordsInEfByIndex iccPbkIntMgrProxy is null for Subscription:" + subId);
        return false;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int[] getAdnRecordsSize(int efid) throws RemoteException {
        return getAdnRecordsSizeForSubscriber(getDefaultSubscription(), efid);
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int[] getAdnRecordsSizeForSubscriber(int subId, int efid) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnRecordsSize(efid);
        }
        Rlog.e(TAG, "getAdnRecordsSize iccPbkIntMgrProxy is null for Subscription:" + subId);
        return null;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public List<AdnRecord> getAdnRecordsInEf(int efid) throws RemoteException {
        return getAdnRecordsInEfForSubscriber(getDefaultSubscription(), efid);
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public List<AdnRecord> getAdnRecordsInEfForSubscriber(int subId, int efid) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnRecordsInEf(efid);
        }
        Rlog.e(TAG, "getAdnRecordsInEf iccPbkIntMgrProxy isnull for Subscription:" + subId);
        return null;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public boolean updateAdnRecordsWithContentValuesInEfBySearch(int efid, ContentValues values, String pin2) throws RemoteException {
        return updateAdnRecordsWithContentValuesInEfBySearchUsingSubId(getDefaultSubscription(), efid, values, pin2);
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public boolean updateAdnRecordsWithContentValuesInEfBySearchUsingSubId(int subId, int efid, ContentValues values, String pin2) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.updateAdnRecordsWithContentValuesInEfBySearch(efid, values, pin2);
        }
        Rlog.e(TAG, "updateAdnRecordsWithContentValuesInEfBySearchUsingSubId iccPbkIntMgrProxy is null for Subscription:" + subId);
        return false;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getAdnCount() throws RemoteException {
        return getAdnCountUsingSubId(getDefaultSubscription());
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getAdnCountUsingSubId(int subId) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnCount();
        }
        Rlog.e(TAG, "getAdnCount iccPbkIntMgrProxy isnull for Subscription:" + subId);
        return 0;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getAnrCount() throws RemoteException {
        return getAnrCountUsingSubId(getDefaultSubscription());
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getAnrCountUsingSubId(int subId) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAnrCount();
        }
        Rlog.e(TAG, "getAnrCount iccPbkIntMgrProxy isnull for Subscription:" + subId);
        return 0;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getEmailCount() throws RemoteException {
        return getEmailCountUsingSubId(getDefaultSubscription());
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getEmailCountUsingSubId(int subId) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getEmailCount();
        }
        Rlog.e(TAG, "getEmailCount iccPbkIntMgrProxy isnull for Subscription:" + subId);
        return 0;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getSpareAnrCount() throws RemoteException {
        return getSpareAnrCountUsingSubId(getDefaultSubscription());
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getSpareAnrCountUsingSubId(int subId) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getSpareAnrCount();
        }
        Rlog.e(TAG, "getSpareAnrCount iccPbkIntMgrProxy isnull for Subscription:" + subId);
        return 0;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getSpareEmailCount() throws RemoteException {
        return getSpareEmailCountUsingSubId(getDefaultSubscription());
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getSpareEmailCountUsingSubId(int subId) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getSpareEmailCount();
        }
        Rlog.e(TAG, "getSpareEmailCount iccPbkIntMgrProxy isnull for Subscription:" + subId);
        return 0;
    }

    private IccPhoneBookInterfaceManagerProxy getIccPhoneBookInterfaceManagerProxy(int subId) {
        try {
            return ((PhoneProxy) this.mPhone[SubscriptionController.getInstance().getPhoneId(subId)]).getIccPhoneBookInterfaceManagerProxy();
        } catch (ArrayIndexOutOfBoundsException e) {
            Rlog.e(TAG, "Exception is :" + e.toString() + " For subscription :" + subId);
            e.printStackTrace();
            return null;
        } catch (NullPointerException e2) {
            Rlog.e(TAG, "Exception is :" + e2.toString() + " For subscription :" + subId);
            e2.printStackTrace();
            return null;
        }
    }

    private int getDefaultSubscription() {
        return SubscriptionController.getInstance().getDefaultSubId();
    }
}
