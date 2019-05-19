package org.codeaurora.ims.internal;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import org.codeaurora.ims.QtiViceInfo;

public interface IQtiImsExtListener extends IInterface {

    public static abstract class Stub extends Binder implements IQtiImsExtListener {
        private static final String DESCRIPTOR = "org.codeaurora.ims.internal.IQtiImsExtListener";
        static final int TRANSACTION_notifyParticipantStatusInfo = 11;
        static final int TRANSACTION_notifyRefreshViceInfo = 10;
        static final int TRANSACTION_notifySsacStatus = 9;
        static final int TRANSACTION_notifyVopsStatus = 8;
        static final int TRANSACTION_onGetCallForwardUncondTimer = 2;
        static final int TRANSACTION_onGetHandoverConfig = 15;
        static final int TRANSACTION_onGetPacketCount = 4;
        static final int TRANSACTION_onGetPacketErrorCount = 5;
        static final int TRANSACTION_onSetCallForwardUncondTimer = 1;
        static final int TRANSACTION_onSetHandoverConfig = 14;
        static final int TRANSACTION_onUTReqFailed = 3;
        static final int TRANSACTION_onVoltePreferenceQueried = 13;
        static final int TRANSACTION_onVoltePreferenceUpdated = 12;
        static final int TRANSACTION_receiveCallDeflectResponse = 6;
        static final int TRANSACTION_receiveCallTransferResponse = 7;

        private static class Proxy implements IQtiImsExtListener {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            public void onSetCallForwardUncondTimer(int status) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(status);
                    this.mRemote.transact(1, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            public void onGetCallForwardUncondTimer(int startHour, int endHour, int startMinute, int endMinute, int reason, int status, String number, int serviceClass) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(startHour);
                    _data.writeInt(endHour);
                    _data.writeInt(startMinute);
                    _data.writeInt(endMinute);
                    _data.writeInt(reason);
                    _data.writeInt(status);
                    _data.writeString(number);
                    _data.writeInt(serviceClass);
                    this.mRemote.transact(2, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            public void onUTReqFailed(int errCode, String errString) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(errCode);
                    _data.writeString(errString);
                    this.mRemote.transact(3, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            public void onGetPacketCount(int status, long packetCount) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(status);
                    _data.writeLong(packetCount);
                    this.mRemote.transact(4, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            public void onGetPacketErrorCount(int status, long packetErrorCount) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(status);
                    _data.writeLong(packetErrorCount);
                    this.mRemote.transact(5, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            public void receiveCallDeflectResponse(int result) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(result);
                    this.mRemote.transact(6, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            public void receiveCallTransferResponse(int result) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(result);
                    this.mRemote.transact(7, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            public void notifyVopsStatus(boolean vopsStatus) throws RemoteException {
                int i = 1;
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!vopsStatus) {
                        i = 0;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(8, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            public void notifySsacStatus(boolean ssacStatusResponse) throws RemoteException {
                int i = 1;
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!ssacStatusResponse) {
                        i = 0;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(9, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            public void notifyRefreshViceInfo(QtiViceInfo viceInfo) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (viceInfo != null) {
                        _data.writeInt(1);
                        viceInfo.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(10, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            public void notifyParticipantStatusInfo(int operation, int sipStatus, String participantUri, boolean isEct) throws RemoteException {
                int i = 1;
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(operation);
                    _data.writeInt(sipStatus);
                    _data.writeString(participantUri);
                    if (!isEct) {
                        i = 0;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(11, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            public void onVoltePreferenceUpdated(int result) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(result);
                    this.mRemote.transact(12, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            public void onVoltePreferenceQueried(int result, int mode) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(result);
                    _data.writeInt(mode);
                    this.mRemote.transact(13, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            public void onSetHandoverConfig(int status) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(status);
                    this.mRemote.transact(14, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            public void onGetHandoverConfig(int status, int hoConfig) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(status);
                    _data.writeInt(hoConfig);
                    this.mRemote.transact(15, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IQtiImsExtListener asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin == null || !(iin instanceof IQtiImsExtListener)) {
                return new Proxy(obj);
            }
            return (IQtiImsExtListener) iin;
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case 1:
                    data.enforceInterface(DESCRIPTOR);
                    onSetCallForwardUncondTimer(data.readInt());
                    return true;
                case 2:
                    data.enforceInterface(DESCRIPTOR);
                    onGetCallForwardUncondTimer(data.readInt(), data.readInt(), data.readInt(), data.readInt(), data.readInt(), data.readInt(), data.readString(), data.readInt());
                    return true;
                case 3:
                    data.enforceInterface(DESCRIPTOR);
                    onUTReqFailed(data.readInt(), data.readString());
                    return true;
                case 4:
                    data.enforceInterface(DESCRIPTOR);
                    onGetPacketCount(data.readInt(), data.readLong());
                    return true;
                case 5:
                    data.enforceInterface(DESCRIPTOR);
                    onGetPacketErrorCount(data.readInt(), data.readLong());
                    return true;
                case 6:
                    data.enforceInterface(DESCRIPTOR);
                    receiveCallDeflectResponse(data.readInt());
                    return true;
                case 7:
                    data.enforceInterface(DESCRIPTOR);
                    receiveCallTransferResponse(data.readInt());
                    return true;
                case 8:
                    data.enforceInterface(DESCRIPTOR);
                    notifyVopsStatus(data.readInt() != 0);
                    return true;
                case 9:
                    data.enforceInterface(DESCRIPTOR);
                    notifySsacStatus(data.readInt() != 0);
                    return true;
                case 10:
                    QtiViceInfo _arg0;
                    data.enforceInterface(DESCRIPTOR);
                    if (data.readInt() != 0) {
                        _arg0 = (QtiViceInfo) QtiViceInfo.CREATOR.createFromParcel(data);
                    } else {
                        _arg0 = null;
                    }
                    notifyRefreshViceInfo(_arg0);
                    return true;
                case 11:
                    data.enforceInterface(DESCRIPTOR);
                    notifyParticipantStatusInfo(data.readInt(), data.readInt(), data.readString(), data.readInt() != 0);
                    return true;
                case 12:
                    data.enforceInterface(DESCRIPTOR);
                    onVoltePreferenceUpdated(data.readInt());
                    return true;
                case 13:
                    data.enforceInterface(DESCRIPTOR);
                    onVoltePreferenceQueried(data.readInt(), data.readInt());
                    return true;
                case 14:
                    data.enforceInterface(DESCRIPTOR);
                    onSetHandoverConfig(data.readInt());
                    return true;
                case 15:
                    data.enforceInterface(DESCRIPTOR);
                    onGetHandoverConfig(data.readInt(), data.readInt());
                    return true;
                case 1598968902:
                    reply.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    void notifyParticipantStatusInfo(int i, int i2, String str, boolean z) throws RemoteException;

    void notifyRefreshViceInfo(QtiViceInfo qtiViceInfo) throws RemoteException;

    void notifySsacStatus(boolean z) throws RemoteException;

    void notifyVopsStatus(boolean z) throws RemoteException;

    void onGetCallForwardUncondTimer(int i, int i2, int i3, int i4, int i5, int i6, String str, int i7) throws RemoteException;

    void onGetHandoverConfig(int i, int i2) throws RemoteException;

    void onGetPacketCount(int i, long j) throws RemoteException;

    void onGetPacketErrorCount(int i, long j) throws RemoteException;

    void onSetCallForwardUncondTimer(int i) throws RemoteException;

    void onSetHandoverConfig(int i) throws RemoteException;

    void onUTReqFailed(int i, String str) throws RemoteException;

    void onVoltePreferenceQueried(int i, int i2) throws RemoteException;

    void onVoltePreferenceUpdated(int i) throws RemoteException;

    void receiveCallDeflectResponse(int i) throws RemoteException;

    void receiveCallTransferResponse(int i) throws RemoteException;
}
