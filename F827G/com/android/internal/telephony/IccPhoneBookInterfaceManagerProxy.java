package com.android.internal.telephony;

import android.content.ContentValues;
import android.os.RemoteException;
import com.android.internal.telephony.uicc.AdnRecord;
import java.util.List;

public class IccPhoneBookInterfaceManagerProxy {
    private IccPhoneBookInterfaceManager mIccPhoneBookInterfaceManager;

    public IccPhoneBookInterfaceManagerProxy(IccPhoneBookInterfaceManager iccPhoneBookInterfaceManager) {
        this.mIccPhoneBookInterfaceManager = iccPhoneBookInterfaceManager;
    }

    public int getAdnCount() {
        return this.mIccPhoneBookInterfaceManager.getAdnCount();
    }

    public List<AdnRecord> getAdnRecordsInEf(int i) {
        return this.mIccPhoneBookInterfaceManager.getAdnRecordsInEf(i);
    }

    public int[] getAdnRecordsSize(int i) {
        return this.mIccPhoneBookInterfaceManager.getAdnRecordsSize(i);
    }

    public int getAnrCount() {
        return this.mIccPhoneBookInterfaceManager.getAnrCount();
    }

    public int getEmailCount() {
        return this.mIccPhoneBookInterfaceManager.getEmailCount();
    }

    public int getSpareAnrCount() {
        return this.mIccPhoneBookInterfaceManager.getSpareAnrCount();
    }

    public int getSpareEmailCount() {
        return this.mIccPhoneBookInterfaceManager.getSpareEmailCount();
    }

    public void setmIccPhoneBookInterfaceManager(IccPhoneBookInterfaceManager iccPhoneBookInterfaceManager) {
        this.mIccPhoneBookInterfaceManager = iccPhoneBookInterfaceManager;
    }

    public boolean updateAdnRecordsInEfByIndex(int i, String str, String str2, int i2, String str3) {
        return this.mIccPhoneBookInterfaceManager.updateAdnRecordsInEfByIndex(i, str, str2, i2, str3);
    }

    public boolean updateAdnRecordsInEfBySearch(int i, String str, String str2, String str3, String str4, String str5) {
        return this.mIccPhoneBookInterfaceManager.updateAdnRecordsInEfBySearch(i, str, str2, str3, str4, str5);
    }

    public boolean updateAdnRecordsWithContentValuesInEfBySearch(int i, ContentValues contentValues, String str) throws RemoteException {
        return this.mIccPhoneBookInterfaceManager.updateAdnRecordsWithContentValuesInEfBySearch(i, contentValues, str);
    }
}
