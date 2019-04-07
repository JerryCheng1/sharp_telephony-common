package org.codeaurora.ims.csvt;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;

public interface ICsvtService extends IInterface {

    public static abstract class Stub extends Binder implements ICsvtService {
        private static final String DESCRIPTOR = "org.codeaurora.ims.csvt.ICsvtService";
        static final int TRANSACTION_acceptCall = 3;
        static final int TRANSACTION_dial = 1;
        static final int TRANSACTION_fallBack = 5;
        static final int TRANSACTION_getCallForwardingOption = 9;
        static final int TRANSACTION_getCallWaiting = 11;
        static final int TRANSACTION_hangup = 2;
        static final int TRANSACTION_isActive = 7;
        static final int TRANSACTION_isIdle = 6;
        static final int TRANSACTION_isNonCsvtIdle = 8;
        static final int TRANSACTION_registerListener = 13;
        static final int TRANSACTION_rejectCall = 4;
        static final int TRANSACTION_setCallForwardingOption = 10;
        static final int TRANSACTION_setCallWaiting = 12;
        static final int TRANSACTION_unregisterListener = 14;

        private static class Proxy implements ICsvtService {
            private IBinder mRemote;

            Proxy(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            public void acceptCall() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(3, obtain, obtain2, 0);
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public void dial(String str) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeString(str);
                    this.mRemote.transact(1, obtain, obtain2, 0);
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public void fallBack() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(5, obtain, obtain2, 0);
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public void getCallForwardingOption(int i, Message message) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (message != null) {
                        obtain.writeInt(1);
                        message.writeToParcel(obtain, 0);
                    } else {
                        obtain.writeInt(0);
                    }
                    this.mRemote.transact(9, obtain, obtain2, 0);
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public void getCallWaiting(Message message) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (message != null) {
                        obtain.writeInt(1);
                        message.writeToParcel(obtain, 0);
                    } else {
                        obtain.writeInt(0);
                    }
                    this.mRemote.transact(11, obtain, obtain2, 0);
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            public void hangup() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(2, obtain, obtain2, 0);
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public boolean isActive() throws RemoteException {
                boolean z = false;
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
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

            public boolean isIdle() throws RemoteException {
                boolean z = false;
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
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

            public boolean isNonCsvtIdle() throws RemoteException {
                boolean z = false;
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
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

            public void registerListener(ICsvtServiceListener iCsvtServiceListener) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeStrongBinder(iCsvtServiceListener != null ? iCsvtServiceListener.asBinder() : null);
                    this.mRemote.transact(13, obtain, obtain2, 0);
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public void rejectCall() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(4, obtain, obtain2, 0);
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public void setCallForwardingOption(int i, int i2, String str, int i3, Message message) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeInt(i2);
                    obtain.writeString(str);
                    obtain.writeInt(i3);
                    if (message != null) {
                        obtain.writeInt(1);
                        message.writeToParcel(obtain, 0);
                    } else {
                        obtain.writeInt(0);
                    }
                    this.mRemote.transact(10, obtain, obtain2, 0);
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public void setCallWaiting(boolean z, Message message) throws RemoteException {
                int i = 0;
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (z) {
                        i = 1;
                    }
                    obtain.writeInt(i);
                    if (message != null) {
                        obtain.writeInt(1);
                        message.writeToParcel(obtain, 0);
                    } else {
                        obtain.writeInt(0);
                    }
                    this.mRemote.transact(12, obtain, obtain2, 0);
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public void unregisterListener(ICsvtServiceListener iCsvtServiceListener) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeStrongBinder(iCsvtServiceListener != null ? iCsvtServiceListener.asBinder() : null);
                    this.mRemote.transact(14, obtain, obtain2, 0);
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ICsvtService asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            return (queryLocalInterface == null || !(queryLocalInterface instanceof ICsvtService)) ? new Proxy(iBinder) : (ICsvtService) queryLocalInterface;
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws RemoteException {
            Message message = null;
            int i3 = 0;
            boolean isIdle;
            switch (i) {
                case 1:
                    parcel.enforceInterface(DESCRIPTOR);
                    dial(parcel.readString());
                    parcel2.writeNoException();
                    return true;
                case 2:
                    parcel.enforceInterface(DESCRIPTOR);
                    hangup();
                    parcel2.writeNoException();
                    return true;
                case 3:
                    parcel.enforceInterface(DESCRIPTOR);
                    acceptCall();
                    parcel2.writeNoException();
                    return true;
                case 4:
                    parcel.enforceInterface(DESCRIPTOR);
                    rejectCall();
                    parcel2.writeNoException();
                    return true;
                case 5:
                    parcel.enforceInterface(DESCRIPTOR);
                    fallBack();
                    parcel2.writeNoException();
                    return true;
                case 6:
                    parcel.enforceInterface(DESCRIPTOR);
                    isIdle = isIdle();
                    parcel2.writeNoException();
                    if (isIdle) {
                        i3 = 1;
                    }
                    parcel2.writeInt(i3);
                    return true;
                case 7:
                    parcel.enforceInterface(DESCRIPTOR);
                    isIdle = isActive();
                    parcel2.writeNoException();
                    if (isIdle) {
                        i3 = 1;
                    }
                    parcel2.writeInt(i3);
                    return true;
                case 8:
                    parcel.enforceInterface(DESCRIPTOR);
                    isIdle = isNonCsvtIdle();
                    parcel2.writeNoException();
                    if (isIdle) {
                        i3 = 1;
                    }
                    parcel2.writeInt(i3);
                    return true;
                case 9:
                    parcel.enforceInterface(DESCRIPTOR);
                    getCallForwardingOption(parcel.readInt(), parcel.readInt() != 0 ? (Message) Message.CREATOR.createFromParcel(parcel) : null);
                    parcel2.writeNoException();
                    return true;
                case 10:
                    parcel.enforceInterface(DESCRIPTOR);
                    int readInt = parcel.readInt();
                    int readInt2 = parcel.readInt();
                    String readString = parcel.readString();
                    int readInt3 = parcel.readInt();
                    if (parcel.readInt() != 0) {
                        message = (Message) Message.CREATOR.createFromParcel(parcel);
                    }
                    setCallForwardingOption(readInt, readInt2, readString, readInt3, message);
                    parcel2.writeNoException();
                    return true;
                case 11:
                    parcel.enforceInterface(DESCRIPTOR);
                    getCallWaiting(parcel.readInt() != 0 ? (Message) Message.CREATOR.createFromParcel(parcel) : null);
                    parcel2.writeNoException();
                    return true;
                case 12:
                    parcel.enforceInterface(DESCRIPTOR);
                    setCallWaiting(parcel.readInt() != 0, parcel.readInt() != 0 ? (Message) Message.CREATOR.createFromParcel(parcel) : null);
                    parcel2.writeNoException();
                    return true;
                case 13:
                    parcel.enforceInterface(DESCRIPTOR);
                    registerListener(org.codeaurora.ims.csvt.ICsvtServiceListener.Stub.asInterface(parcel.readStrongBinder()));
                    parcel2.writeNoException();
                    return true;
                case 14:
                    parcel.enforceInterface(DESCRIPTOR);
                    unregisterListener(org.codeaurora.ims.csvt.ICsvtServiceListener.Stub.asInterface(parcel.readStrongBinder()));
                    parcel2.writeNoException();
                    return true;
                case 1598968902:
                    parcel2.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(i, parcel, parcel2, i2);
            }
        }
    }

    void acceptCall() throws RemoteException;

    void dial(String str) throws RemoteException;

    void fallBack() throws RemoteException;

    void getCallForwardingOption(int i, Message message) throws RemoteException;

    void getCallWaiting(Message message) throws RemoteException;

    void hangup() throws RemoteException;

    boolean isActive() throws RemoteException;

    boolean isIdle() throws RemoteException;

    boolean isNonCsvtIdle() throws RemoteException;

    void registerListener(ICsvtServiceListener iCsvtServiceListener) throws RemoteException;

    void rejectCall() throws RemoteException;

    void setCallForwardingOption(int i, int i2, String str, int i3, Message message) throws RemoteException;

    void setCallWaiting(boolean z, Message message) throws RemoteException;

    void unregisterListener(ICsvtServiceListener iCsvtServiceListener) throws RemoteException;
}
