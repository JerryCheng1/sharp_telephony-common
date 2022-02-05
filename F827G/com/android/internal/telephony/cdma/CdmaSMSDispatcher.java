package com.android.internal.telephony.cdma;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Telephony;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.internal.telephony.cdma.SmsMessage;
import com.android.internal.telephony.cdma.sms.UserData;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class CdmaSMSDispatcher extends SMSDispatcher {
    private static final String TAG = "CdmaSMSDispatcher";
    private static final boolean VDBG = false;

    public CdmaSMSDispatcher(PhoneBase phone, SmsUsageMonitor usageMonitor, ImsSMSDispatcher imsSMSDispatcher) {
        super(phone, usageMonitor, imsSMSDispatcher);
        Rlog.d(TAG, "CdmaSMSDispatcher created");
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.android.internal.telephony.SMSDispatcher
    public String getFormat() {
        return SmsMessage.FORMAT_3GPP2;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void sendStatusReportMessage(SmsMessage sms) {
        sendMessage(obtainMessage(10, sms));
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void handleStatusReport(Object o) {
        if (o instanceof SmsMessage) {
            handleCdmaStatusReport((SmsMessage) o);
        } else {
            Rlog.e(TAG, "handleStatusReport() called for object type " + o.getClass().getName());
        }
    }

    void handleCdmaStatusReport(SmsMessage sms) {
        int count = this.deliveryPendingList.size();
        for (int i = 0; i < count; i++) {
            SMSDispatcher.SmsTracker tracker = (SMSDispatcher.SmsTracker) this.deliveryPendingList.get(i);
            if (tracker.mMessageRef == sms.mMessageRef) {
                this.deliveryPendingList.remove(i);
                tracker.updateSentMessageStatus(this.mContext, 0);
                PendingIntent intent = tracker.mDeliveryIntent;
                Intent fillIn = new Intent();
                fillIn.putExtra("pdu", sms.getPdu());
                fillIn.putExtra(Telephony.CellBroadcasts.MESSAGE_FORMAT, getFormat());
                try {
                    intent.send(this.mContext, -1, fillIn);
                    return;
                } catch (PendingIntent.CanceledException e) {
                    return;
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.android.internal.telephony.SMSDispatcher
    public void sendData(String destAddr, String scAddr, int destPort, int origPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        SMSDispatcher.SmsTracker tracker = getSmsTracker(getSmsTrackerMap(destAddr, scAddr, destPort, origPort, data, SmsMessage.getSubmitPdu(scAddr, destAddr, destPort, origPort, data, deliveryIntent != null)), sentIntent, deliveryIntent, getFormat(), null, false, null, false);
        String carrierPackage = getCarrierAppPackageName();
        if (carrierPackage != null) {
            Rlog.d(TAG, "Found carrier package.");
            SMSDispatcher.DataSmsSender smsSender = new SMSDispatcher.DataSmsSender(tracker);
            smsSender.sendSmsByCarrierApp(carrierPackage, new SMSDispatcher.SmsSenderCallback(smsSender));
            return;
        }
        Rlog.v(TAG, "No carrier package.");
        sendSubmitPdu(tracker);
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.android.internal.telephony.SMSDispatcher
    public void sendText(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, Uri messageUri, String callingPkg, int priority, boolean isExpectMore, int validityPeriod) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, text, deliveryIntent != null, (SmsHeader) null, priority);
        if (pdu != null) {
            SMSDispatcher.SmsTracker tracker = getSmsTracker(getSmsTrackerMap(destAddr, scAddr, text, pdu), sentIntent, deliveryIntent, getFormat(), messageUri, isExpectMore, text, true, validityPeriod);
            String carrierPackage = getCarrierAppPackageName();
            if (carrierPackage != null) {
                Rlog.d(TAG, "Found carrier package.");
                SMSDispatcher.TextSmsSender smsSender = new SMSDispatcher.TextSmsSender(tracker);
                smsSender.sendSmsByCarrierApp(carrierPackage, new SMSDispatcher.SmsSenderCallback(smsSender));
                return;
            }
            Rlog.v(TAG, "No carrier package.");
            sendSubmitPdu(tracker);
            return;
        }
        Rlog.e(TAG, "CdmaSMSDispatcher.sendText(): getSubmitPdu() returned null");
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.android.internal.telephony.SMSDispatcher
    public void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        throw new IllegalStateException("This method must be called only on ImsSMSDispatcher");
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected GsmAlphabet.TextEncodingDetails calculateLength(CharSequence messageBody, boolean use7bitOnly) {
        return SmsMessage.calculateLength(messageBody, use7bitOnly);
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected SMSDispatcher.SmsTracker getNewSubmitPduTracker(String destinationAddress, String scAddress, String message, SmsHeader smsHeader, int encoding, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart, int priority, boolean isExpectMore, int validityPeriod, AtomicInteger unsentPartCount, AtomicBoolean anyPartFailed, Uri messageUri, String fullMessageText) {
        UserData uData = new UserData();
        uData.payloadStr = message;
        uData.userDataHeader = smsHeader;
        if (encoding == 1) {
            uData.msgEncoding = 9;
            boolean ascii7bitForLongMsg = this.mPhone.getContext().getResources().getBoolean(17957011);
            if (ascii7bitForLongMsg) {
                Rlog.d(TAG, "ascii7bitForLongMsg = " + ascii7bitForLongMsg);
                uData.msgEncoding = 2;
            }
        } else {
            uData.msgEncoding = 4;
        }
        uData.msgEncodingSet = true;
        return getSmsTracker(getSmsTrackerMap(destinationAddress, scAddress, message, SmsMessage.getSubmitPdu(destinationAddress, uData, deliveryIntent != null && lastPart, priority)), sentIntent, deliveryIntent, getFormat(), unsentPartCount, anyPartFailed, messageUri, smsHeader, !lastPart || isExpectMore, fullMessageText, true, validityPeriod);
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendSubmitPdu(SMSDispatcher.SmsTracker tracker) {
        if (SystemProperties.getBoolean("ril.cdma.inecmmode", false)) {
            tracker.onFailed(this.mContext, 4, 0);
        } else {
            sendRawPdu(tracker);
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.android.internal.telephony.SMSDispatcher
    public void sendSms(SMSDispatcher.SmsTracker tracker) {
        byte[] bArr = (byte[]) tracker.mData.get("pdu");
        Rlog.d(TAG, "sendSms:  isIms()=" + isIms() + " mRetryCount=" + tracker.mRetryCount + " mImsRetry=" + tracker.mImsRetry + " mMessageRef=" + tracker.mMessageRef + " SS=" + this.mPhone.getServiceState().getState());
        sendSmsByPstn(tracker);
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendSmsByPstn(SMSDispatcher.SmsTracker tracker) {
        int ss = this.mPhone.getServiceState().getState();
        int ssVoice = this.mPhone.getServiceState().getVoiceRegState();
        int ssData = this.mPhone.getServiceState().getDataRegState();
        Rlog.d(TAG, "isIms = " + isIms() + ", ssVoice = " + ssVoice + ", ssData = " + ssData);
        if (isIms() || ssVoice == 0 || ssData == 0) {
            Message reply = obtainMessage(2, tracker);
            byte[] pdu = (byte[]) tracker.mData.get("pdu");
            int currentDataNetwork = this.mPhone.getServiceState().getDataNetworkType();
            if ((currentDataNetwork == 14 || (currentDataNetwork == 13 && !this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed())) && this.mPhone.getServiceState().getVoiceNetworkType() == 7 && ((CDMAPhone) this.mPhone).mCT.mState != PhoneConstants.State.IDLE) {
            }
            if (tracker.mImsRetry == 0 && !isIms()) {
                this.mCi.sendCdmaSms(pdu, reply);
            } else if (!this.mImsSMSDispatcher.isImsSmsEnabled()) {
                this.mCi.sendCdmaSms(pdu, reply);
                this.mImsSMSDispatcher.enableSendSmsOverIms(true);
            } else {
                this.mCi.sendImsCdmaSms(pdu, tracker.mImsRetry, tracker.mMessageRef, reply);
                tracker.mImsRetry++;
            }
        } else {
            tracker.onFailed(this.mContext, getNotInServiceError(ss), 0);
        }
    }
}
