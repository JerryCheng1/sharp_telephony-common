package com.android.internal.telephony.gsm;

import android.content.Context;
import android.os.Message;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SmsConstants.MessageClass;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;

public class GsmInboundSmsHandler extends InboundSmsHandler {
    private final UsimDataDownloadHandler mDataDownloadHandler;

    private GsmInboundSmsHandler(Context context, SmsStorageMonitor smsStorageMonitor, PhoneBase phoneBase) {
        super("GsmInboundSmsHandler", context, smsStorageMonitor, phoneBase, GsmCellBroadcastHandler.makeGsmCellBroadcastHandler(context, phoneBase));
        phoneBase.mCi.setOnNewGsmSms(getHandler(), 1, null);
        this.mDataDownloadHandler = new UsimDataDownloadHandler(phoneBase.mCi);
    }

    public static GsmInboundSmsHandler makeInboundSmsHandler(Context context, SmsStorageMonitor smsStorageMonitor, PhoneBase phoneBase) {
        GsmInboundSmsHandler gsmInboundSmsHandler = new GsmInboundSmsHandler(context, smsStorageMonitor, phoneBase);
        gsmInboundSmsHandler.start();
        return gsmInboundSmsHandler;
    }

    private static int resultToCause(int i) {
        switch (i) {
            case -1:
            case 1:
                return 0;
            case 3:
                return 211;
            default:
                return 255;
        }
    }

    /* Access modifiers changed, original: protected */
    public void acknowledgeLastIncomingSms(boolean z, int i, Message message) {
        this.mPhone.mCi.acknowledgeLastIncomingGsmSms(z, resultToCause(i), message);
    }

    /* Access modifiers changed, original: protected */
    public int dispatchMessageRadioSpecific(SmsMessageBase smsMessageBase) {
        boolean z = false;
        SmsMessage smsMessage = (SmsMessage) smsMessageBase;
        if (smsMessage.isTypeZero()) {
            log("Received short message type 0, Don't display or store it. Send Ack");
            return 1;
        } else if (smsMessage.isUsimDataDownload()) {
            return this.mDataDownloadHandler.handleUsimDataDownload(this.mPhone.getUsimServiceTable(), smsMessage);
        } else {
            boolean isMwiDontStore;
            StringBuilder append;
            if (smsMessage.isMWISetMessage()) {
                updateMessageWaitingIndicator(smsMessage.getNumOfVoicemails());
                isMwiDontStore = smsMessage.isMwiDontStore();
                append = new StringBuilder().append("Received voice mail indicator set SMS shouldStore=");
                if (!isMwiDontStore) {
                    z = true;
                }
                log(append.append(z).toString());
                z = isMwiDontStore;
            } else if (smsMessage.isMWIClearMessage()) {
                updateMessageWaitingIndicator(0);
                isMwiDontStore = smsMessage.isMwiDontStore();
                append = new StringBuilder().append("Received voice mail indicator clear SMS shouldStore=");
                if (!isMwiDontStore) {
                    z = true;
                }
                log(append.append(z).toString());
                z = isMwiDontStore;
            }
            return !z ? (this.mStorageMonitor.isStorageAvailable() || smsMessage.getMessageClass() == MessageClass.CLASS_0) ? dispatchNormalMessage(smsMessageBase) : 3 : 1;
        }
    }

    /* Access modifiers changed, original: protected */
    public boolean is3gpp2() {
        return false;
    }

    /* Access modifiers changed, original: protected */
    public void onQuitting() {
        this.mPhone.mCi.unSetOnNewGsmSms(getHandler());
        this.mCellBroadcastHandler.dispose();
        log("unregistered for 3GPP SMS");
        super.onQuitting();
    }

    /* Access modifiers changed, original: protected */
    public void onUpdatePhoneObject(PhoneBase phoneBase) {
        super.onUpdatePhoneObject(phoneBase);
        log("onUpdatePhoneObject: dispose of old CellBroadcastHandler and make a new one");
        this.mCellBroadcastHandler.dispose();
        this.mCellBroadcastHandler = GsmCellBroadcastHandler.makeGsmCellBroadcastHandler(this.mContext, phoneBase);
    }

    /* Access modifiers changed, original: 0000 */
    public void updateMessageWaitingIndicator(int i) {
        if (i < 0) {
            i = -1;
        } else if (i > 255) {
            i = 255;
        }
        this.mPhone.setVoiceMessageCount(i);
        IccRecords iccRecords = UiccController.getInstance().getIccRecords(this.mPhone.getPhoneId(), 1);
        if (iccRecords != null) {
            log("updateMessageWaitingIndicator: updating SIM Records");
            iccRecords.setVoiceMessageWaiting(1, i);
        } else {
            log("updateMessageWaitingIndicator: SIM Records not found");
        }
        storeVoiceMailCount();
    }
}
