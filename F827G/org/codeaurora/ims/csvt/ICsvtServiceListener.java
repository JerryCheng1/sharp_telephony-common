package org.codeaurora.ims.csvt;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.List;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public interface ICsvtServiceListener extends IInterface {
    void onCallForwardingOptions(List<CallForwardInfoP> list) throws RemoteException;

    void onCallStatus(int i) throws RemoteException;

    void onCallWaiting(boolean z) throws RemoteException;

    void onPhoneStateChanged(int i) throws RemoteException;

    void onRingbackTone(boolean z) throws RemoteException;

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static abstract class Stub extends Binder implements ICsvtServiceListener {
        private static final String DESCRIPTOR = "org.codeaurora.ims.csvt.ICsvtServiceListener";
        static final int TRANSACTION_onCallForwardingOptions = 4;
        static final int TRANSACTION_onCallStatus = 2;
        static final int TRANSACTION_onCallWaiting = 3;
        static final int TRANSACTION_onPhoneStateChanged = 1;
        static final int TRANSACTION_onRingbackTone = 5;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ICsvtServiceListener asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin == null || !(iin instanceof ICsvtServiceListener)) {
                return new Proxy(obj);
            }
            return (ICsvtServiceListener) iin;
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            boolean _arg0 = false;
            switch (code) {
                case 1:
                    data.enforceInterface(DESCRIPTOR);
                    onPhoneStateChanged(data.readInt());
                    return true;
                case 2:
                    data.enforceInterface(DESCRIPTOR);
                    onCallStatus(data.readInt());
                    return true;
                case 3:
                    data.enforceInterface(DESCRIPTOR);
                    if (data.readInt() != 0) {
                        _arg0 = true;
                    }
                    onCallWaiting(_arg0);
                    return true;
                case 4:
                    data.enforceInterface(DESCRIPTOR);
                    onCallForwardingOptions(data.createTypedArrayList(CallForwardInfoP.CREATOR));
                    return true;
                case 5:
                    data.enforceInterface(DESCRIPTOR);
                    if (data.readInt() != 0) {
                        _arg0 = true;
                    }
                    onRingbackTone(_arg0);
                    return true;
                case 1598968902:
                    reply.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
        private static class Proxy implements ICsvtServiceListener {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            @Override // org.codeaurora.ims.csvt.ICsvtServiceListener
            public void onPhoneStateChanged(int state) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(state);
                    this.mRemote.transact(1, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtServiceListener
            public void onCallStatus(int status) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(status);
                    this.mRemote.transact(2, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtServiceListener
            public void onCallWaiting(boolean enabled) throws RemoteException {
                int i = 1;
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!enabled) {
                        i = 0;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(3, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtServiceListener
            public void onCallForwardingOptions(List<CallForwardInfoP> fi) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeTypedList(fi);
                    this.mRemote.transact(4, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtServiceListener
            public void onRingbackTone(boolean playTone) throws RemoteException {
                int i = 1;
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!playTone) {
                        i = 0;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(5, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }
        }
    }
}
