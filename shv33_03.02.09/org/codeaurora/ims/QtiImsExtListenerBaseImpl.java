package org.codeaurora.ims;

import org.codeaurora.ims.internal.IQtiImsExtListener.Stub;

public class QtiImsExtListenerBaseImpl extends Stub {
    public void onSetCallForwardUncondTimer(int status) {
    }

    public void onGetCallForwardUncondTimer(int startHour, int endHour, int startMinute, int endMinute, int reason, int status, String number, int service) {
    }

    public void onUTReqFailed(int errCode, String errString) {
    }

    public void onGetPacketCount(int status, long packetCount) {
    }

    public void onGetPacketErrorCount(int status, long packetErrorCount) {
    }

    public void receiveCallDeflectResponse(int result) {
    }

    public void receiveCallTransferResponse(int result) {
    }

    public void notifyVopsStatus(boolean vopsStatus) {
    }

    public void notifySsacStatus(boolean ssacStatusResponse) {
    }

    public void notifyRefreshViceInfo(QtiViceInfo viceInfo) {
    }

    public void notifyParticipantStatusInfo(int operation, int sipStatus, String participantUri, boolean isEct) {
    }

    public void onVoltePreferenceUpdated(int result) {
    }

    public void onVoltePreferenceQueried(int result, int mode) {
    }

    public void onSetHandoverConfig(int result) {
    }

    public void onGetHandoverConfig(int result, int hoConfig) {
    }
}
