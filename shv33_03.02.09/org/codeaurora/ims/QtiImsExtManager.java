package org.codeaurora.ims;

import android.os.RemoteException;
import android.os.ServiceManager;
import org.codeaurora.ims.internal.IQtiImsExt;
import org.codeaurora.ims.internal.IQtiImsExt.Stub;
import org.codeaurora.ims.internal.IQtiImsExtListener;

public class QtiImsExtManager {
    public static final String SERVICE_ID = "qti.ims.ext";
    private static QtiImsExtManager sInstance;
    private IQtiImsExt mQtiImsExt;

    private QtiImsExtManager() {
    }

    public static QtiImsExtManager getInstance() {
        if (sInstance == null) {
            sInstance = new QtiImsExtManager();
        }
        return sInstance;
    }

    public void setCallForwardUncondTimer(int startHour, int startMinute, int endHour, int endMinute, int action, int condition, int serviceClass, String number, IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        try {
            this.mQtiImsExt.setCallForwardUncondTimer(startHour, startMinute, endHour, endMinute, action, condition, serviceClass, number, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService setCallForwardUncondTimer : " + e);
        }
    }

    public void getCallForwardUncondTimer(int reason, int serviceClass, IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        try {
            this.mQtiImsExt.getCallForwardUncondTimer(reason, serviceClass, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService getCallForwardUncondTimer : " + e);
        }
    }

    public void getPacketCount(IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        try {
            this.mQtiImsExt.getPacketCount(listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService getPacketCount : " + e);
        }
    }

    public void getPacketErrorCount(IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        try {
            this.mQtiImsExt.getPacketErrorCount(listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService getPacketErrorCount : " + e);
        }
    }

    public void sendCallDeflectRequest(int phoneId, String deflectNumber, IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        try {
            this.mQtiImsExt.sendCallDeflectRequest(phoneId, deflectNumber, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService sendCallDeflectRequestCount : " + e);
        }
    }

    public void resumePendingCall(int videoState) throws QtiImsException {
        obtainBinder();
        try {
            this.mQtiImsExt.resumePendingCall(videoState);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService resumePendingCall : " + e);
        }
    }

    public void sendCallTransferRequest(int phoneId, int type, String number, IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        try {
            this.mQtiImsExt.sendCallTransferRequest(phoneId, type, number, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService sendCallTransferRequest : " + e);
        }
    }

    public void queryVopsStatus(IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        try {
            this.mQtiImsExt.queryVopsStatus(listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService queryVopsStatus : " + e);
        }
    }

    public void querySsacStatus(IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        try {
            this.mQtiImsExt.querySsacStatus(listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService querySsacStatus : " + e);
        }
    }

    public int getImsPhoneId() throws QtiImsException {
        obtainBinder();
        try {
            return this.mQtiImsExt.getImsPhoneId();
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService getImsPhoneId : " + e);
        }
    }

    public void registerForViceRefreshInfo(IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        try {
            this.mQtiImsExt.registerForViceRefreshInfo(listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService registerForViceRefreshInfo : " + e);
        }
    }

    public void registerForParticipantStatusInfo(IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        try {
            this.mQtiImsExt.registerForParticipantStatusInfo(listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService registerForParticipantStatusInfo : " + e);
        }
    }

    public void updateVoltePreference(int phoneId, int preference, IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        try {
            this.mQtiImsExt.updateVoltePreference(phoneId, preference, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService updateVoltePreference : " + e);
        }
    }

    public void queryVoltePreference(int phoneId, IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        try {
            this.mQtiImsExt.queryVoltePreference(phoneId, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService queryVoltePreference : " + e);
        }
    }

    public void getHandoverConfig(IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        try {
            this.mQtiImsExt.getHandoverConfig(listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService getHandoverConfig : " + e);
        }
    }

    public void setHandoverConfig(int hoConfig, IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        try {
            this.mQtiImsExt.setHandoverConfig(hoConfig, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService setHandoverConfig : " + e);
        }
    }

    private IQtiImsExt obtainBinder() throws QtiImsException {
        if (this.mQtiImsExt != null) {
            return this.mQtiImsExt;
        }
        this.mQtiImsExt = Stub.asInterface(ServiceManager.getService(SERVICE_ID));
        if (this.mQtiImsExt != null) {
            return this.mQtiImsExt;
        }
        throw new QtiImsException("ImsService is not running");
    }
}
