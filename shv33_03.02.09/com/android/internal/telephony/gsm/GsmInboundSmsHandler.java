package com.android.internal.telephony.gsm;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Message;
import android.provider.Telephony.Carriers;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Rlog;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.InboundSmsTracker;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SmsConstants.MessageClass;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;
import jp.co.sharp.android.internal.telephony.SmsDuplicate;
import jp.co.sharp.android.internal.telephony.SmsDuplicate.ResultJudgeDuplicate;
import jp.co.sharp.android.internal.telephony.SmsDuplicate.SmsAccessory;

public class GsmInboundSmsHandler extends InboundSmsHandler {
    private static boolean OUTPUT_DEBUG_LOG = false;
    protected static final String TAG = "GsmInboundSmsHandler";
    private final UsimDataDownloadHandler mDataDownloadHandler;

    static {
        OUTPUT_DEBUG_LOG = true;
        if (Build.TYPE.equals(Carriers.USER)) {
            OUTPUT_DEBUG_LOG = false;
        }
    }

    private GsmInboundSmsHandler(Context context, SmsStorageMonitor storageMonitor, Phone phone) {
        super(TAG, context, storageMonitor, phone, GsmCellBroadcastHandler.makeGsmCellBroadcastHandler(context, phone));
        phone.mCi.setOnNewGsmSms(getHandler(), 1, null);
        this.mDataDownloadHandler = new UsimDataDownloadHandler(phone.mCi);
    }

    /* Access modifiers changed, original: protected */
    public void onQuitting() {
        this.mPhone.mCi.unSetOnNewGsmSms(getHandler());
        this.mCellBroadcastHandler.dispose();
        log("unregistered for 3GPP SMS");
        super.onQuitting();
    }

    public static GsmInboundSmsHandler makeInboundSmsHandler(Context context, SmsStorageMonitor storageMonitor, Phone phone) {
        GsmInboundSmsHandler handler = new GsmInboundSmsHandler(context, storageMonitor, phone);
        handler.start();
        return handler;
    }

    /* Access modifiers changed, original: protected */
    public boolean is3gpp2() {
        return false;
    }

    /* Access modifiers changed, original: protected */
    public int dispatchMessageRadioSpecific(SmsMessageBase smsb) {
        boolean z = false;
        SmsMessage sms = (SmsMessage) smsb;
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "GSM:dispatchMessageRadioSpecific() call!");
        }
        if (sms.isTypeZero()) {
            log("Received short message type 0, Don't display or store it. Send Ack");
            return 1;
        } else if (sms.isUsimDataDownload()) {
            return this.mDataDownloadHandler.handleUsimDataDownload(this.mPhone.getUsimServiceTable(), sms);
        } else {
            boolean handled = false;
            StringBuilder append;
            if (sms.isMWISetMessage()) {
                updateMessageWaitingIndicator(sms.getNumOfVoicemails());
                handled = sms.isMwiDontStore();
                append = new StringBuilder().append("Received voice mail indicator set SMS shouldStore=");
                if (!handled) {
                    z = true;
                }
                log(append.append(z).toString());
            } else if (sms.isMWIClearMessage()) {
                updateMessageWaitingIndicator(0);
                handled = sms.isMwiDontStore();
                append = new StringBuilder().append("Received voice mail indicator clear SMS shouldStore=");
                if (!handled) {
                    z = true;
                }
                log(append.append(z).toString());
            }
            if (handled) {
                return 1;
            }
            if (this.mStorageMonitor.isStorageAvailable() || sms.getMessageClass() == MessageClass.CLASS_0) {
                return dispatchNormalMessage(smsb);
            }
            return 3;
        }
    }

    private void updateMessageWaitingIndicator(int voicemailCount) {
        if (voicemailCount < 0) {
            voicemailCount = -1;
        } else if (voicemailCount > 255) {
            voicemailCount = 255;
        }
        this.mPhone.setVoiceMessageCount(voicemailCount);
        IccRecords records = UiccController.getInstance().getIccRecords(this.mPhone.getPhoneId(), 1);
        if (records != null) {
            log("updateMessageWaitingIndicator: updating SIM Records");
            records.setVoiceMessageWaiting(1, voicemailCount);
            return;
        }
        log("updateMessageWaitingIndicator: SIM Records not found");
    }

    /* Access modifiers changed, original: protected */
    public void acknowledgeLastIncomingSms(boolean success, int result, Message response) {
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "GSM:acknowledgeLastIncomingSms call!");
            Rlog.d(TAG, "success = " + success + ", result = " + result);
        }
        if (!success) {
            result = 3;
        }
        this.mPhone.mCi.acknowledgeLastIncomingGsmSms(success, resultToCause(result), response);
    }

    /* Access modifiers changed, original: protected */
    public void onUpdatePhoneObject(Phone phone) {
        super.onUpdatePhoneObject(phone);
        log("onUpdatePhoneObject: dispose of old CellBroadcastHandler and make a new one");
        this.mCellBroadcastHandler.dispose();
        this.mCellBroadcastHandler = GsmCellBroadcastHandler.makeGsmCellBroadcastHandler(this.mContext, phone);
    }

    private static int resultToCause(int rc) {
        switch (rc) {
            case -1:
            case 1:
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "resultToCause case is RESULT_SMS_HANDLED!");
                }
                return 0;
            case 3:
                return 211;
            default:
                return 255;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void handleNewSms(AsyncResult ar) {
        boolean handled = true;
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "GSM:handleNewSms() call!");
        }
        if (ar.exception != null) {
            loge("Exception processing incoming SMS: " + ar.exception);
            return;
        }
        int result;
        try {
            result = dispatchMessage(ar.result.mWrappedSmsMessage);
        } catch (RuntimeException ex) {
            loge("Exception dispatching message", ex);
            result = 2;
        }
        if (result != -1) {
            if (result != 1) {
                handled = false;
            }
            acknowledgeLastIncomingSms(handled, result, null);
        }
    }

    /* Access modifiers changed, original: protected */
    public boolean kddiDispatchPdus(InboundSmsTracker tracker) {
        long millis = tracker.getTimestamp();
        byte[] OriginatingAddress = tracker.getAddress().getBytes();
        byte[] MsgCenterTimeStamp = new byte[]{(byte) ((int) ((millis >>> 56) & 255)), (byte) ((int) ((millis >>> 48) & 255)), (byte) ((int) ((millis >>> 40) & 255)), (byte) ((int) ((millis >>> 32) & 255)), (byte) ((int) ((millis >>> 24) & 255)), (byte) ((int) ((millis >>> 16) & 255)), (byte) ((int) ((millis >>> 8) & 255)), (byte) ((int) ((millis >>> null) & 255))};
        byte[] messageData = new byte[(MsgCenterTimeStamp.length + OriginatingAddress.length)];
        System.arraycopy(MsgCenterTimeStamp, 0, messageData, 0, MsgCenterTimeStamp.length);
        System.arraycopy(OriginatingAddress, 0, messageData, MsgCenterTimeStamp.length, OriginatingAddress.length);
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "kddiDispatchPdus() start");
        }
        try {
            if (this.mSmsDuplicate == null) {
                this.mSmsDuplicate = new SmsDuplicate(this.mContext, 1, true);
            }
            ResultJudgeDuplicate info = this.mSmsDuplicate.checkSmsDuplicate(0, messageData);
            if (info == null || !info.mIsSame) {
                SmsAccessory accessory = this.mSmsDuplicate.SmsAccessory(Intents.SMS_RECEIVED_ACTION, null, 0);
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "action = " + accessory.getAction() + " ,response = " + accessory.getResponse());
                    Rlog.d(TAG, "kddiDispatchPdus():This sms is Cmail(GSM).");
                }
                this.mSmsDuplicate.updateSmsDuplicate(0, messageData, accessory);
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "kddiDispatchPdus() end");
                }
                return true;
            }
            if (info.mIsReply && OUTPUT_DEBUG_LOG) {
                Rlog.d(TAG, "kddiDispatchPdus():This sms is Duplicate!(GSM).");
            }
            return false;
        } catch (NullPointerException e) {
            Rlog.e(TAG, "kddiDispatchPdus() failed to create SmsAccessory ");
        }
    }
}
