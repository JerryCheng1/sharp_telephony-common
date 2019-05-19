package com.android.internal.telephony.cdma;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Telephony.Carriers;
import android.provider.Telephony.CellBroadcasts;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SMSDispatcher.SmsTracker;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsMessageBase.SubmitPduBase;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.internal.telephony.cdma.SmsMessage.SubmitPdu;
import com.android.internal.telephony.cdma.sms.UserData;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CdmaSMSDispatcher extends SMSDispatcher {
    private static boolean OUTPUT_DEBUG_LOG = false;
    private static final String TAG = "CdmaSMSDispatcher";
    private static final boolean VDBG = false;

    static {
        OUTPUT_DEBUG_LOG = true;
        if (Build.TYPE.equals(Carriers.USER)) {
            OUTPUT_DEBUG_LOG = false;
        }
    }

    public CdmaSMSDispatcher(Phone phone, SmsUsageMonitor usageMonitor, ImsSMSDispatcher imsSMSDispatcher) {
        super(phone, usageMonitor, imsSMSDispatcher);
        Rlog.d(TAG, "CdmaSMSDispatcher created");
    }

    public String getFormat() {
        return SmsMessage.FORMAT_3GPP2;
    }

    public void sendStatusReportMessage(SmsMessage sms) {
        sendMessage(obtainMessage(10, sms));
    }

    /* Access modifiers changed, original: protected */
    public void handleStatusReport(Object o) {
        if (o instanceof SmsMessage) {
            handleCdmaStatusReport((SmsMessage) o);
        } else {
            Rlog.e(TAG, "handleStatusReport() called for object type " + o.getClass().getName());
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:31:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:15:0x0074  */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void handleCdmaStatusReport(SmsMessage sms) {
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "handleCdmaStatusReport Start");
        }
        boolean isMatching = false;
        int count = this.deliveryPendingList.size();
        for (int i = 0; i < count; i++) {
            SmsTracker tracker = (SmsTracker) this.deliveryPendingList.get(i);
            if (tracker.mMessageRef == sms.mMessageRef) {
                this.deliveryPendingList.remove(i);
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "CdmaSMSDispathcer-handleCdmaStatusReport remove MessageRef is " + tracker.mMessageRef);
                }
                tracker.updateSentMessageStatus(this.mContext, 0);
                PendingIntent intent = tracker.mDeliveryIntent;
                Intent fillIn = new Intent();
                fillIn.putExtra("pdu", sms.getPdu());
                fillIn.putExtra(CellBroadcasts.MESSAGE_FORMAT, getFormat());
                try {
                    intent.send(this.mContext, -1, fillIn);
                } catch (CanceledException e) {
                }
                isMatching = true;
                if (isMatching) {
                    intent = cdmaCheckDeliveryPendingList(sms);
                    if (intent != null) {
                        fillIn = new Intent();
                        fillIn.putExtra("pdu", sms.getPdu());
                        fillIn.putExtra(CellBroadcasts.MESSAGE_FORMAT, SmsMessage.FORMAT_3GPP2);
                        if (OUTPUT_DEBUG_LOG) {
                            Rlog.d(TAG, "send DeliveryIntent, mMessageRef : " + sms.mMessageRef);
                        }
                        try {
                            intent.send(this.mContext, -1, fillIn);
                            return;
                        } catch (CanceledException e2) {
                            return;
                        }
                    }
                    deliveryPendingReportList.add(new ReportTracker(sms.mMessageRef, sms.getPdu(), getFormat()));
                    if (OUTPUT_DEBUG_LOG) {
                        Rlog.d(TAG, "CdmaSMSDispathcer-handleCdmaStatusReport add MessageRef is " + sms.mMessageRef);
                        return;
                    }
                    return;
                }
                return;
            }
        }
        if (isMatching) {
        }
    }

    /* Access modifiers changed, original: protected */
    public PendingIntent isUpdateDeliveryPendingList(int messageRef) {
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "CDMA isUpdateDeliveryPendingList Start! messageRef = " + messageRef);
        }
        int count = this.deliveryPendingList.size();
        for (int i = 0; i < count; i++) {
            SmsTracker tracker = (SmsTracker) this.deliveryPendingList.get(i);
            if (tracker.mMessageRef == messageRef) {
                this.deliveryPendingList.remove(i);
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "isUpdateDeliveryPendingList is true! i = " + i);
                }
                return tracker.mDeliveryIntent;
            }
        }
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "isUpdateDeliveryPendingList is false!");
        }
        return null;
    }

    /* Access modifiers changed, original: protected */
    public PendingIntent checkDeliveryPendingList(SmsMessage sms) {
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "checkDeliveryPendingList should never be called from here!");
        }
        return null;
    }

    /* Access modifiers changed, original: protected */
    public PendingIntent cdmaCheckDeliveryPendingList(SmsMessage sms) {
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "CDMA checkDeliveryPendingList Start");
        }
        return this.mImsSMSDispatcher.checkDeliveryPendingList(sms);
    }

    public void sendData(String destAddr, String scAddr, int destPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, destPort, data, deliveryIntent != null);
        if (pdu != null) {
            SmsTracker tracker = getSmsTracker(getSmsTrackerMap(destAddr, scAddr, destPort, data, pdu), sentIntent, deliveryIntent, getFormat(), null, false, null, false, true);
            String carrierPackage = getCarrierAppPackageName();
            if (carrierPackage != null) {
                Rlog.d(TAG, "Found carrier package.");
                SmsSender dataSmsSender = new DataSmsSender(tracker);
                dataSmsSender.sendSmsByCarrierApp(carrierPackage, new SmsSenderCallback(dataSmsSender));
                return;
            }
            Rlog.v(TAG, "No carrier package.");
            sendSubmitPdu(tracker);
            return;
        }
        Rlog.e(TAG, "CdmaSMSDispatcher.sendData(): getSubmitPdu() returned null");
        if (sentIntent != null) {
            try {
                sentIntent.send(1);
            } catch (CanceledException e) {
                Rlog.e(TAG, "Intent has been canceled!");
            }
        }
    }

    public void sendText(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, Uri messageUri, String callingPkg, boolean persistMessage, int priority, boolean isExpectMore, int validityPeriod) {
        SubmitPduBase pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, text, deliveryIntent != null, null, priority);
        if (pdu != null) {
            SmsTracker tracker = getSmsTracker(getSmsTrackerMap(destAddr, scAddr, text, pdu), sentIntent, deliveryIntent, getFormat(), messageUri, isExpectMore, text, true, validityPeriod, persistMessage);
            String carrierPackage = getCarrierAppPackageName();
            if (carrierPackage != null) {
                Rlog.d(TAG, "Found carrier package.");
                SmsSender textSmsSender = new TextSmsSender(tracker);
                textSmsSender.sendSmsByCarrierApp(carrierPackage, new SmsSenderCallback(textSmsSender));
                return;
            }
            Rlog.v(TAG, "No carrier package.");
            sendSubmitPdu(tracker);
            return;
        }
        Rlog.e(TAG, "CdmaSMSDispatcher.sendText(): getSubmitPdu() returned null");
        if (sentIntent != null) {
            try {
                sentIntent.send(1);
            } catch (CanceledException e) {
                Rlog.e(TAG, "Intent has been canceled!");
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        throw new IllegalStateException("This method must be called only on ImsSMSDispatcher");
    }

    /* Access modifiers changed, original: protected */
    public TextEncodingDetails calculateLength(CharSequence messageBody, boolean use7bitOnly) {
        return SmsMessage.calculateLength(messageBody, use7bitOnly, false);
    }

    /* Access modifiers changed, original: protected */
    public SmsTracker getNewSubmitPduTracker(String destinationAddress, String scAddress, String message, SmsHeader smsHeader, int encoding, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart, int priority, boolean isExpectMore, int validityPeriod, AtomicInteger unsentPartCount, AtomicBoolean anyPartFailed, Uri messageUri, String fullMessageText) {
        UserData uData = new UserData();
        uData.payloadStr = message;
        uData.userDataHeader = smsHeader;
        if (encoding == 1) {
            uData.msgEncoding = SmsMessage.isAscii7bitSupportedForLongMessage() ? 2 : 9;
            Rlog.d(TAG, "Message ecoding for proper 7 bit: " + uData.msgEncoding);
        } else {
            uData.msgEncoding = 4;
        }
        uData.msgEncodingSet = true;
        return getSmsTracker(getSmsTrackerMap(destinationAddress, scAddress, message, SmsMessage.getSubmitPdu(destinationAddress, uData, deliveryIntent != null ? lastPart : false, priority)), sentIntent, deliveryIntent, getFormat(), unsentPartCount, anyPartFailed, messageUri, smsHeader, lastPart ? isExpectMore : true, fullMessageText, true, validityPeriod, true);
    }

    /* Access modifiers changed, original: protected */
    public void sendSubmitPdu(SmsTracker tracker) {
        if (SystemProperties.getBoolean("ril.cdma.inecmmode", false)) {
            tracker.onFailed(this.mContext, 4, 0);
        } else {
            sendRawPdu(tracker);
        }
    }

    public void sendSms(SmsTracker tracker) {
        Rlog.d(TAG, "sendSms:  isIms()=" + isIms() + " mRetryCount=" + tracker.mRetryCount + " mImsRetry=" + tracker.mImsRetry + " mMessageRef=" + tracker.mMessageRef + " SS=" + this.mPhone.getServiceState().getState());
        sendSmsByPstn(tracker);
    }

    /* Access modifiers changed, original: protected */
    public void sendSmsByPstn(SmsTracker tracker) {
        int ss = this.mPhone.getServiceState().getState();
        if (isIms() || ss == 0) {
            Message reply = obtainMessage(2, tracker);
            byte[] pdu = (byte[]) tracker.getData().get("pdu");
            if (tracker.mImsRetry == 0 && !isIms()) {
                this.mCi.sendCdmaSms(pdu, reply);
            } else if (this.mImsSMSDispatcher.isImsSmsEnabled()) {
                this.mCi.sendImsCdmaSms(pdu, tracker.mImsRetry, tracker.mMessageRef, reply);
                tracker.mImsRetry++;
            } else {
                this.mCi.sendCdmaSms(pdu, reply);
                this.mImsSMSDispatcher.enableSendSmsOverIms(true);
            }
            return;
        }
        tracker.onFailed(this.mContext, SMSDispatcher.getNotInServiceError(ss), 0);
    }
}
