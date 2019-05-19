package org.codeaurora.ims;

import org.codeaurora.ims.internal.IQtiImsExt.Stub;
import org.codeaurora.ims.internal.IQtiImsExtListener;

public abstract class QtiImsExtBase {
    private QtiImsExtBinder mQtiImsExtBinder;

    public final class QtiImsExtBinder extends Stub {
        public void setCallForwardUncondTimer(int startHour, int startMinute, int endHour, int endMinute, int action, int condition, int serviceClass, String number, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onSetCallForwardUncondTimer(startHour, startMinute, endHour, endMinute, action, condition, serviceClass, number, listener);
        }

        public void getCallForwardUncondTimer(int reason, int serviceClass, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onGetCallForwardUncondTimer(reason, serviceClass, listener);
        }

        public void getPacketCount(IQtiImsExtListener listener) {
            QtiImsExtBase.this.onGetPacketCount(listener);
        }

        public void getPacketErrorCount(IQtiImsExtListener listener) {
            QtiImsExtBase.this.onGetPacketErrorCount(listener);
        }

        public void sendCallDeflectRequest(int phoneId, String deflectNumber, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onSendCallDeflectRequest(phoneId, deflectNumber, listener);
        }

        public void resumePendingCall(int videoState) {
            QtiImsExtBase.this.onResumePendingCall(videoState);
        }

        public void sendCallTransferRequest(int phoneId, int type, String number, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onSendCallTransferRequest(phoneId, type, number, listener);
        }

        public void queryVopsStatus(IQtiImsExtListener listener) {
            QtiImsExtBase.this.onQueryVopsStatus(listener);
        }

        public void querySsacStatus(IQtiImsExtListener listener) {
            QtiImsExtBase.this.onQuerySsacStatus(listener);
        }

        public int getImsPhoneId() {
            return QtiImsExtBase.this.onGetImsPhoneId();
        }

        public void registerForViceRefreshInfo(IQtiImsExtListener listener) {
            QtiImsExtBase.this.onRegisterForViceRefreshInfo(listener);
        }

        public void registerForParticipantStatusInfo(IQtiImsExtListener listener) {
            QtiImsExtBase.this.onRegisterForParticipantStatusInfo(listener);
        }

        public void updateVoltePreference(int phoneId, int preference, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onUpdateVoltePreference(phoneId, preference, listener);
        }

        public void queryVoltePreference(int phoneId, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onQueryVoltePreference(phoneId, listener);
        }

        public void getHandoverConfig(IQtiImsExtListener listener) {
            QtiImsExtBase.this.onGetHandoverConfig(listener);
        }

        public void setHandoverConfig(int hoConfig, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onSetHandoverConfig(hoConfig, listener);
        }
    }

    public QtiImsExtBinder getBinder() {
        if (this.mQtiImsExtBinder == null) {
            this.mQtiImsExtBinder = new QtiImsExtBinder();
        }
        return this.mQtiImsExtBinder;
    }

    /* Access modifiers changed, original: protected */
    public void onSetCallForwardUncondTimer(int startHour, int startMinute, int endHour, int endMinute, int action, int condition, int serviceClass, String number, IQtiImsExtListener listener) {
    }

    /* Access modifiers changed, original: protected */
    public void onGetCallForwardUncondTimer(int reason, int serviceClass, IQtiImsExtListener listener) {
    }

    /* Access modifiers changed, original: protected */
    public void onGetPacketCount(IQtiImsExtListener listener) {
    }

    /* Access modifiers changed, original: protected */
    public void onGetPacketErrorCount(IQtiImsExtListener listener) {
    }

    /* Access modifiers changed, original: protected */
    public void onSendCallDeflectRequest(int phoneId, String deflectNumber, IQtiImsExtListener listener) {
    }

    /* Access modifiers changed, original: protected */
    public void onResumePendingCall(int videoState) {
    }

    /* Access modifiers changed, original: protected */
    public void onSendCallTransferRequest(int phoneId, int type, String number, IQtiImsExtListener listener) {
    }

    /* Access modifiers changed, original: protected */
    public void onQueryVopsStatus(IQtiImsExtListener listener) {
    }

    /* Access modifiers changed, original: protected */
    public void onQuerySsacStatus(IQtiImsExtListener listener) {
    }

    /* Access modifiers changed, original: protected */
    public int onGetImsPhoneId() {
        return -1;
    }

    /* Access modifiers changed, original: protected */
    public void onRegisterForViceRefreshInfo(IQtiImsExtListener listener) {
    }

    /* Access modifiers changed, original: protected */
    public void onRegisterForParticipantStatusInfo(IQtiImsExtListener listener) {
    }

    /* Access modifiers changed, original: protected */
    public void onUpdateVoltePreference(int phoneId, int preference, IQtiImsExtListener listener) {
    }

    /* Access modifiers changed, original: protected */
    public void onQueryVoltePreference(int phoneId, IQtiImsExtListener listener) {
    }

    /* Access modifiers changed, original: protected */
    public void onGetHandoverConfig(IQtiImsExtListener listener) {
    }

    /* Access modifiers changed, original: protected */
    public void onSetHandoverConfig(int hoConfig, IQtiImsExtListener listener) {
    }
}
