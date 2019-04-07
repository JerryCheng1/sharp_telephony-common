package com.android.internal.telephony;

import android.content.ContentValues;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.android.internal.telephony.uicc.AdnRecord;
import java.util.List;

public interface IIccPhoneBook extends IInterface {

    public static abstract class Stub extends Binder implements IIccPhoneBook {
        private static final String DESCRIPTOR = "com.android.internal.telephony.IIccPhoneBook";
        static final int TRANSACTION_getAdnCount = 11;
        static final int TRANSACTION_getAdnCountUsingSubId = 12;
        static final int TRANSACTION_getAdnRecordsInEf = 1;
        static final int TRANSACTION_getAdnRecordsInEfForSubscriber = 2;
        static final int TRANSACTION_getAdnRecordsSize = 9;
        static final int TRANSACTION_getAdnRecordsSizeForSubscriber = 10;
        static final int TRANSACTION_getAnrCount = 13;
        static final int TRANSACTION_getAnrCountUsingSubId = 14;
        static final int TRANSACTION_getEmailCount = 15;
        static final int TRANSACTION_getEmailCountUsingSubId = 16;
        static final int TRANSACTION_getSpareAnrCount = 17;
        static final int TRANSACTION_getSpareAnrCountUsingSubId = 18;
        static final int TRANSACTION_getSpareEmailCount = 19;
        static final int TRANSACTION_getSpareEmailCountUsingSubId = 20;
        static final int TRANSACTION_updateAdnRecordsInEfByIndex = 7;
        static final int TRANSACTION_updateAdnRecordsInEfByIndexForSubscriber = 8;
        static final int TRANSACTION_updateAdnRecordsInEfBySearch = 3;
        static final int TRANSACTION_updateAdnRecordsInEfBySearchForSubscriber = 4;
        static final int TRANSACTION_updateAdnRecordsWithContentValuesInEfBySearch = 5;
        static final int TRANSACTION_updateAdnRecordsWithContentValuesInEfBySearchUsingSubId = 6;

        private static class Proxy implements IIccPhoneBook {
            private IBinder mRemote;

            Proxy(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public int getAdnCount() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(11, obtain, obtain2, 0);
                    obtain2.readException();
                    int readInt = obtain2.readInt();
                    return readInt;
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public int getAdnCountUsingSubId(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    this.mRemote.transact(12, obtain, obtain2, 0);
                    obtain2.readException();
                    int readInt = obtain2.readInt();
                    return readInt;
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public List<AdnRecord> getAdnRecordsInEf(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    this.mRemote.transact(1, obtain, obtain2, 0);
                    obtain2.readException();
                    List<AdnRecord> createTypedArrayList = obtain2.createTypedArrayList(AdnRecord.CREATOR);
                    return createTypedArrayList;
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public List<AdnRecord> getAdnRecordsInEfForSubscriber(int i, int i2) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeInt(i2);
                    this.mRemote.transact(2, obtain, obtain2, 0);
                    obtain2.readException();
                    List<AdnRecord> createTypedArrayList = obtain2.createTypedArrayList(AdnRecord.CREATOR);
                    return createTypedArrayList;
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public int[] getAdnRecordsSize(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    this.mRemote.transact(9, obtain, obtain2, 0);
                    obtain2.readException();
                    int[] createIntArray = obtain2.createIntArray();
                    return createIntArray;
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public int[] getAdnRecordsSizeForSubscriber(int i, int i2) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeInt(i2);
                    this.mRemote.transact(10, obtain, obtain2, 0);
                    obtain2.readException();
                    int[] createIntArray = obtain2.createIntArray();
                    return createIntArray;
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public int getAnrCount() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(13, obtain, obtain2, 0);
                    obtain2.readException();
                    int readInt = obtain2.readInt();
                    return readInt;
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public int getAnrCountUsingSubId(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    this.mRemote.transact(14, obtain, obtain2, 0);
                    obtain2.readException();
                    int readInt = obtain2.readInt();
                    return readInt;
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public int getEmailCount() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(15, obtain, obtain2, 0);
                    obtain2.readException();
                    int readInt = obtain2.readInt();
                    return readInt;
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public int getEmailCountUsingSubId(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    this.mRemote.transact(16, obtain, obtain2, 0);
                    obtain2.readException();
                    int readInt = obtain2.readInt();
                    return readInt;
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            public int getSpareAnrCount() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(17, obtain, obtain2, 0);
                    obtain2.readException();
                    int readInt = obtain2.readInt();
                    return readInt;
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public int getSpareAnrCountUsingSubId(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    this.mRemote.transact(18, obtain, obtain2, 0);
                    obtain2.readException();
                    int readInt = obtain2.readInt();
                    return readInt;
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public int getSpareEmailCount() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(19, obtain, obtain2, 0);
                    obtain2.readException();
                    int readInt = obtain2.readInt();
                    return readInt;
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public int getSpareEmailCountUsingSubId(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    this.mRemote.transact(20, obtain, obtain2, 0);
                    obtain2.readException();
                    int readInt = obtain2.readInt();
                    return readInt;
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public boolean updateAdnRecordsInEfByIndex(int i, String str, String str2, int i2, String str3) throws RemoteException {
                boolean z = false;
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeString(str);
                    obtain.writeString(str2);
                    obtain.writeInt(i2);
                    obtain.writeString(str3);
                    this.mRemote.transact(7, obtain, obtain2, 0);
                    obtain2.readException();
                    if (obtain2.readInt() != 0) {
                        z = true;
                    }
                    obtain2.recycle();
                    obtain.recycle();
                    return z;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public boolean updateAdnRecordsInEfByIndexForSubscriber(int i, int i2, String str, String str2, int i3, String str3) throws RemoteException {
                boolean z = false;
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeInt(i2);
                    obtain.writeString(str);
                    obtain.writeString(str2);
                    obtain.writeInt(i3);
                    obtain.writeString(str3);
                    this.mRemote.transact(8, obtain, obtain2, 0);
                    obtain2.readException();
                    if (obtain2.readInt() != 0) {
                        z = true;
                    }
                    obtain2.recycle();
                    obtain.recycle();
                    return z;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public boolean updateAdnRecordsInEfBySearch(int i, String str, String str2, String str3, String str4, String str5) throws RemoteException {
                boolean z = false;
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeString(str);
                    obtain.writeString(str2);
                    obtain.writeString(str3);
                    obtain.writeString(str4);
                    obtain.writeString(str5);
                    this.mRemote.transact(3, obtain, obtain2, 0);
                    obtain2.readException();
                    if (obtain2.readInt() != 0) {
                        z = true;
                    }
                    obtain2.recycle();
                    obtain.recycle();
                    return z;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public boolean updateAdnRecordsInEfBySearchForSubscriber(int i, int i2, String str, String str2, String str3, String str4, String str5) throws RemoteException {
                boolean z = false;
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeInt(i2);
                    obtain.writeString(str);
                    obtain.writeString(str2);
                    obtain.writeString(str3);
                    obtain.writeString(str4);
                    obtain.writeString(str5);
                    this.mRemote.transact(4, obtain, obtain2, 0);
                    obtain2.readException();
                    if (obtain2.readInt() != 0) {
                        z = true;
                    }
                    obtain2.recycle();
                    obtain.recycle();
                    return z;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public boolean updateAdnRecordsWithContentValuesInEfBySearch(int i, ContentValues contentValues, String str) throws RemoteException {
                boolean z = false;
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (contentValues != null) {
                        obtain.writeInt(1);
                        contentValues.writeToParcel(obtain, 0);
                    } else {
                        obtain.writeInt(0);
                    }
                    obtain.writeString(str);
                    this.mRemote.transact(5, obtain, obtain2, 0);
                    obtain2.readException();
                    if (obtain2.readInt() != 0) {
                        z = true;
                    }
                    obtain2.recycle();
                    obtain.recycle();
                    return z;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public boolean updateAdnRecordsWithContentValuesInEfBySearchUsingSubId(int i, int i2, ContentValues contentValues, String str) throws RemoteException {
                boolean z = false;
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeInt(i2);
                    if (contentValues != null) {
                        obtain.writeInt(1);
                        contentValues.writeToParcel(obtain, 0);
                    } else {
                        obtain.writeInt(0);
                    }
                    obtain.writeString(str);
                    this.mRemote.transact(6, obtain, obtain2, 0);
                    obtain2.readException();
                    if (obtain2.readInt() != 0) {
                        z = true;
                    }
                    obtain2.recycle();
                    obtain.recycle();
                    return z;
                } catch (Throwable th) {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IIccPhoneBook asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            return (queryLocalInterface == null || !(queryLocalInterface instanceof IIccPhoneBook)) ? new Proxy(iBinder) : (IIccPhoneBook) queryLocalInterface;
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws RemoteException {
            ContentValues contentValues = null;
            int i3 = 0;
            List adnRecordsInEf;
            boolean updateAdnRecordsInEfBySearch;
            int readInt;
            int[] adnRecordsSize;
            int adnCount;
            switch (i) {
                case 1:
                    parcel.enforceInterface(DESCRIPTOR);
                    adnRecordsInEf = getAdnRecordsInEf(parcel.readInt());
                    parcel2.writeNoException();
                    parcel2.writeTypedList(adnRecordsInEf);
                    return true;
                case 2:
                    parcel.enforceInterface(DESCRIPTOR);
                    adnRecordsInEf = getAdnRecordsInEfForSubscriber(parcel.readInt(), parcel.readInt());
                    parcel2.writeNoException();
                    parcel2.writeTypedList(adnRecordsInEf);
                    return true;
                case 3:
                    parcel.enforceInterface(DESCRIPTOR);
                    updateAdnRecordsInEfBySearch = updateAdnRecordsInEfBySearch(parcel.readInt(), parcel.readString(), parcel.readString(), parcel.readString(), parcel.readString(), parcel.readString());
                    parcel2.writeNoException();
                    parcel2.writeInt(updateAdnRecordsInEfBySearch ? 1 : 0);
                    return true;
                case 4:
                    parcel.enforceInterface(DESCRIPTOR);
                    updateAdnRecordsInEfBySearch = updateAdnRecordsInEfBySearchForSubscriber(parcel.readInt(), parcel.readInt(), parcel.readString(), parcel.readString(), parcel.readString(), parcel.readString(), parcel.readString());
                    parcel2.writeNoException();
                    if (updateAdnRecordsInEfBySearch) {
                        i3 = 1;
                    }
                    parcel2.writeInt(i3);
                    return true;
                case 5:
                    parcel.enforceInterface(DESCRIPTOR);
                    readInt = parcel.readInt();
                    if (parcel.readInt() != 0) {
                        contentValues = (ContentValues) ContentValues.CREATOR.createFromParcel(parcel);
                    }
                    updateAdnRecordsInEfBySearch = updateAdnRecordsWithContentValuesInEfBySearch(readInt, contentValues, parcel.readString());
                    parcel2.writeNoException();
                    if (updateAdnRecordsInEfBySearch) {
                        i3 = 1;
                    }
                    parcel2.writeInt(i3);
                    return true;
                case 6:
                    parcel.enforceInterface(DESCRIPTOR);
                    readInt = parcel.readInt();
                    int readInt2 = parcel.readInt();
                    if (parcel.readInt() != 0) {
                        contentValues = (ContentValues) ContentValues.CREATOR.createFromParcel(parcel);
                    }
                    updateAdnRecordsInEfBySearch = updateAdnRecordsWithContentValuesInEfBySearchUsingSubId(readInt, readInt2, contentValues, parcel.readString());
                    parcel2.writeNoException();
                    if (updateAdnRecordsInEfBySearch) {
                        i3 = 1;
                    }
                    parcel2.writeInt(i3);
                    return true;
                case 7:
                    parcel.enforceInterface(DESCRIPTOR);
                    updateAdnRecordsInEfBySearch = updateAdnRecordsInEfByIndex(parcel.readInt(), parcel.readString(), parcel.readString(), parcel.readInt(), parcel.readString());
                    parcel2.writeNoException();
                    if (updateAdnRecordsInEfBySearch) {
                        i3 = 1;
                    }
                    parcel2.writeInt(i3);
                    return true;
                case 8:
                    parcel.enforceInterface(DESCRIPTOR);
                    updateAdnRecordsInEfBySearch = updateAdnRecordsInEfByIndexForSubscriber(parcel.readInt(), parcel.readInt(), parcel.readString(), parcel.readString(), parcel.readInt(), parcel.readString());
                    parcel2.writeNoException();
                    if (updateAdnRecordsInEfBySearch) {
                        i3 = 1;
                    }
                    parcel2.writeInt(i3);
                    return true;
                case 9:
                    parcel.enforceInterface(DESCRIPTOR);
                    adnRecordsSize = getAdnRecordsSize(parcel.readInt());
                    parcel2.writeNoException();
                    parcel2.writeIntArray(adnRecordsSize);
                    return true;
                case 10:
                    parcel.enforceInterface(DESCRIPTOR);
                    adnRecordsSize = getAdnRecordsSizeForSubscriber(parcel.readInt(), parcel.readInt());
                    parcel2.writeNoException();
                    parcel2.writeIntArray(adnRecordsSize);
                    return true;
                case 11:
                    parcel.enforceInterface(DESCRIPTOR);
                    adnCount = getAdnCount();
                    parcel2.writeNoException();
                    parcel2.writeInt(adnCount);
                    return true;
                case 12:
                    parcel.enforceInterface(DESCRIPTOR);
                    adnCount = getAdnCountUsingSubId(parcel.readInt());
                    parcel2.writeNoException();
                    parcel2.writeInt(adnCount);
                    return true;
                case 13:
                    parcel.enforceInterface(DESCRIPTOR);
                    adnCount = getAnrCount();
                    parcel2.writeNoException();
                    parcel2.writeInt(adnCount);
                    return true;
                case 14:
                    parcel.enforceInterface(DESCRIPTOR);
                    adnCount = getAnrCountUsingSubId(parcel.readInt());
                    parcel2.writeNoException();
                    parcel2.writeInt(adnCount);
                    return true;
                case 15:
                    parcel.enforceInterface(DESCRIPTOR);
                    adnCount = getEmailCount();
                    parcel2.writeNoException();
                    parcel2.writeInt(adnCount);
                    return true;
                case 16:
                    parcel.enforceInterface(DESCRIPTOR);
                    adnCount = getEmailCountUsingSubId(parcel.readInt());
                    parcel2.writeNoException();
                    parcel2.writeInt(adnCount);
                    return true;
                case 17:
                    parcel.enforceInterface(DESCRIPTOR);
                    adnCount = getSpareAnrCount();
                    parcel2.writeNoException();
                    parcel2.writeInt(adnCount);
                    return true;
                case 18:
                    parcel.enforceInterface(DESCRIPTOR);
                    adnCount = getSpareAnrCountUsingSubId(parcel.readInt());
                    parcel2.writeNoException();
                    parcel2.writeInt(adnCount);
                    return true;
                case 19:
                    parcel.enforceInterface(DESCRIPTOR);
                    adnCount = getSpareEmailCount();
                    parcel2.writeNoException();
                    parcel2.writeInt(adnCount);
                    return true;
                case 20:
                    parcel.enforceInterface(DESCRIPTOR);
                    adnCount = getSpareEmailCountUsingSubId(parcel.readInt());
                    parcel2.writeNoException();
                    parcel2.writeInt(adnCount);
                    return true;
                case 1598968902:
                    parcel2.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(i, parcel, parcel2, i2);
            }
        }
    }

    int getAdnCount() throws RemoteException;

    int getAdnCountUsingSubId(int i) throws RemoteException;

    List<AdnRecord> getAdnRecordsInEf(int i) throws RemoteException;

    List<AdnRecord> getAdnRecordsInEfForSubscriber(int i, int i2) throws RemoteException;

    int[] getAdnRecordsSize(int i) throws RemoteException;

    int[] getAdnRecordsSizeForSubscriber(int i, int i2) throws RemoteException;

    int getAnrCount() throws RemoteException;

    int getAnrCountUsingSubId(int i) throws RemoteException;

    int getEmailCount() throws RemoteException;

    int getEmailCountUsingSubId(int i) throws RemoteException;

    int getSpareAnrCount() throws RemoteException;

    int getSpareAnrCountUsingSubId(int i) throws RemoteException;

    int getSpareEmailCount() throws RemoteException;

    int getSpareEmailCountUsingSubId(int i) throws RemoteException;

    boolean updateAdnRecordsInEfByIndex(int i, String str, String str2, int i2, String str3) throws RemoteException;

    boolean updateAdnRecordsInEfByIndexForSubscriber(int i, int i2, String str, String str2, int i3, String str3) throws RemoteException;

    boolean updateAdnRecordsInEfBySearch(int i, String str, String str2, String str3, String str4, String str5) throws RemoteException;

    boolean updateAdnRecordsInEfBySearchForSubscriber(int i, int i2, String str, String str2, String str3, String str4, String str5) throws RemoteException;

    boolean updateAdnRecordsWithContentValuesInEfBySearch(int i, ContentValues contentValues, String str) throws RemoteException;

    boolean updateAdnRecordsWithContentValuesInEfBySearchUsingSubId(int i, int i2, ContentValues contentValues, String str) throws RemoteException;
}
