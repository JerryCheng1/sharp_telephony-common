package org.codeaurora.ims.csvt;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.List;

public interface ICsvtServiceListener extends IInterface {

    public static abstract class Stub extends Binder implements ICsvtServiceListener {
        private static final String DESCRIPTOR = "org.codeaurora.ims.csvt.ICsvtServiceListener";
        static final int TRANSACTION_onCallForwardingOptions = 4;
        static final int TRANSACTION_onCallStatus = 2;
        static final int TRANSACTION_onCallWaiting = 3;
        static final int TRANSACTION_onPhoneStateChanged = 1;
        static final int TRANSACTION_onRingbackTone = 5;

        private static class Proxy implements ICsvtServiceListener {
            private IBinder mRemote;

            Proxy(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            public void onCallForwardingOptions(List<CallForwardInfoP> list) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeTypedList(list);
                    this.mRemote.transact(4, obtain, null, 1);
                } finally {
                    obtain.recycle();
                }
            }

            public void onCallStatus(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    this.mRemote.transact(2, obtain, null, 1);
                } finally {
                    obtain.recycle();
                }
            }

            public void onCallWaiting(boolean z) throws RemoteException {
                int i = 1;
                Parcel obtain = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!z) {
                        i = 0;
                    }
                    obtain.writeInt(i);
                    this.mRemote.transact(3, obtain, null, 1);
                } finally {
                    obtain.recycle();
                }
            }

            public void onPhoneStateChanged(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    this.mRemote.transact(1, obtain, null, 1);
                } finally {
                    obtain.recycle();
                }
            }

            public void onRingbackTone(boolean z) throws RemoteException {
                int i = 1;
                Parcel obtain = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!z) {
                        i = 0;
                    }
                    obtain.writeInt(i);
                    this.mRemote.transact(5, obtain, null, 1);
                } finally {
                    obtain.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ICsvtServiceListener asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            return (queryLocalInterface == null || !(queryLocalInterface instanceof ICsvtServiceListener)) ? new Proxy(iBinder) : (ICsvtServiceListener) queryLocalInterface;
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws RemoteException {
            boolean z = false;
            switch (i) {
                case 1:
                    parcel.enforceInterface(DESCRIPTOR);
                    onPhoneStateChanged(parcel.readInt());
                    return true;
                case 2:
                    parcel.enforceInterface(DESCRIPTOR);
                    onCallStatus(parcel.readInt());
                    return true;
                case 3:
                    parcel.enforceInterface(DESCRIPTOR);
                    if (parcel.readInt() != 0) {
                        z = true;
                    }
                    onCallWaiting(z);
                    return true;
                case 4:
                    parcel.enforceInterface(DESCRIPTOR);
                    onCallForwardingOptions(parcel.createTypedArrayList(CallForwardInfoP.CREATOR));
                    return true;
                case 5:
                    parcel.enforceInterface(DESCRIPTOR);
                    if (parcel.readInt() != 0) {
                        z = true;
                    }
                    onRingbackTone(z);
                    return true;
                case 1598968902:
                    parcel2.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(i, parcel, parcel2, i2);
            }
        }
    }

    void onCallForwardingOptions(List<CallForwardInfoP> list) throws RemoteException;

    void onCallStatus(int i) throws RemoteException;

    void onCallWaiting(boolean z) throws RemoteException;

    void onPhoneStateChanged(int i) throws RemoteException;

    void onRingbackTone(boolean z) throws RemoteException;
}
