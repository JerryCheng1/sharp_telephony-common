package com.android.internal.telephony.gsm;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Message;
import android.provider.Telephony;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.internal.telephony.gsm.SmsMessage;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class GsmSMSDispatcher extends SMSDispatcher {
    private static final int EVENT_NEW_SMS_STATUS_REPORT = 100;
    private static final String TAG = "GsmSMSDispatcher";
    private static final boolean VDBG = false;
    private GsmInboundSmsHandler mGsmInboundSmsHandler;
    private AtomicReference<UiccCardApplication> mUiccApplication = new AtomicReference<>();
    protected UiccController mUiccController;

    public GsmSMSDispatcher(PhoneBase phone, SmsUsageMonitor usageMonitor, ImsSMSDispatcher imsSMSDispatcher, GsmInboundSmsHandler gsmInboundSmsHandler) {
        super(phone, usageMonitor, imsSMSDispatcher);
        this.mUiccController = null;
        this.mCi.setOnSmsStatus(this, 100, null);
        this.mCi.setOnSmsOnSim(this, 16, null);
        this.mGsmInboundSmsHandler = gsmInboundSmsHandler;
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 15, null);
        Rlog.d(TAG, "GsmSMSDispatcher created");
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public void dispose() {
        super.dispose();
        this.mCi.unSetOnSmsStatus(this);
        this.mUiccController.unregisterForIccChanged(this);
        this.mCi.unSetOnSmsOnSim(this);
        if (this.mIccRecords.get() != null) {
            ((IccRecords) this.mIccRecords.get()).unregisterForNewSms(this);
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.android.internal.telephony.SMSDispatcher
    public String getFormat() {
        return SmsMessage.FORMAT_3GPP;
    }

    @Override // com.android.internal.telephony.SMSDispatcher, android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 14:
                this.mGsmInboundSmsHandler.sendMessage(1, msg.obj);
                return;
            case 15:
                onUpdateIccAvailability();
                return;
            case 16:
                if (this.mIccRecords.get() != null) {
                    ((SIMRecords) this.mIccRecords.get()).handleSmsOnIcc((AsyncResult) msg.obj);
                    return;
                }
                return;
            case 100:
                handleStatusReport((AsyncResult) msg.obj);
                return;
            default:
                super.handleMessage(msg);
                return;
        }
    }

    private void handleStatusReport(AsyncResult ar) {
        String pduString = (String) ar.result;
        SmsMessage sms = SmsMessage.newFromCDS(pduString);
        if (sms != null) {
            int tpStatus = sms.getStatus();
            int messageRef = sms.mMessageRef;
            int i = 0;
            int count = this.deliveryPendingList.size();
            while (true) {
                if (i >= count) {
                    break;
                }
                SMSDispatcher.SmsTracker tracker = (SMSDispatcher.SmsTracker) this.deliveryPendingList.get(i);
                if (tracker.mMessageRef == messageRef) {
                    if (tpStatus >= 64 || tpStatus < 32) {
                        this.deliveryPendingList.remove(i);
                        tracker.updateSentMessageStatus(this.mContext, tpStatus);
                    }
                    PendingIntent intent = tracker.mDeliveryIntent;
                    Intent fillIn = new Intent();
                    fillIn.putExtra("pdu", IccUtils.hexStringToBytes(pduString));
                    fillIn.putExtra(Telephony.CellBroadcasts.MESSAGE_FORMAT, getFormat());
                    try {
                        intent.send(this.mContext, -1, fillIn);
                    } catch (PendingIntent.CanceledException e) {
                    }
                } else {
                    i++;
                }
            }
        }
        this.mCi.acknowledgeLastIncomingGsmSms(true, 1, null);
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.android.internal.telephony.SMSDispatcher
    public void sendData(String destAddr, String scAddr, int destPort, int origPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, destPort, origPort, data, deliveryIntent != null);
        if (pdu != null) {
            SMSDispatcher.SmsTracker tracker = getSmsTracker(getSmsTrackerMap(destAddr, scAddr, destPort, origPort, data, pdu), sentIntent, deliveryIntent, getFormat(), null, false, null, false);
            String carrierPackage = getCarrierAppPackageName();
            if (carrierPackage != null) {
                Rlog.d(TAG, "Found carrier package.");
                SMSDispatcher.DataSmsSender smsSender = new SMSDispatcher.DataSmsSender(tracker);
                smsSender.sendSmsByCarrierApp(carrierPackage, new SMSDispatcher.SmsSenderCallback(smsSender));
                return;
            }
            Rlog.v(TAG, "No carrier package.");
            sendRawPdu(tracker);
            return;
        }
        Rlog.e(TAG, "GsmSMSDispatcher.sendData(): getSubmitPdu() returned null");
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.android.internal.telephony.SMSDispatcher
    public void sendText(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, Uri messageUri, String callingPkg, int priority, boolean isExpectMore, int validityPeriod) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, text, deliveryIntent != null, validityPeriod);
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
            sendRawPdu(tracker);
            return;
        }
        Rlog.e(TAG, "GsmSMSDispatcher.sendText(): getSubmitPdu() returned null");
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
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddress, destinationAddress, message, deliveryIntent != null, SmsHeader.toByteArray(smsHeader), encoding, smsHeader.languageTable, smsHeader.languageShiftTable, validityPeriod);
        if (pdu != null) {
            SMSDispatcher.SmsTracker tracker = getSmsTracker(getSmsTrackerMap(destinationAddress, scAddress, message, pdu), sentIntent, deliveryIntent, getFormat(), unsentPartCount, anyPartFailed, messageUri, smsHeader, !lastPart || isExpectMore, fullMessageText, true, validityPeriod);
            SmsHeader.ConcatRef concatRef = smsHeader.concatRef;
            tracker.mFirstOfMultiPart = !lastPart && concatRef.seqNumber == 1;
            tracker.mMsgCount = concatRef.msgCount;
            tracker.mSendConfirmed = this.mSendConfirmed;
            return tracker;
        }
        Rlog.e(TAG, "GsmSMSDispatcher.sendNewSubmitPdu(): getSubmitPdu() returned null");
        return null;
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendSubmitPdu(SMSDispatcher.SmsTracker tracker) {
        sendRawPdu(tracker);
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.android.internal.telephony.SMSDispatcher
    public void sendSms(SMSDispatcher.SmsTracker tracker) {
        byte[] pdu = (byte[]) tracker.mData.get("pdu");
        if (tracker.mRetryCount > 0) {
            Rlog.d(TAG, "sendSms:  mRetryCount=" + tracker.mRetryCount + " mMessageRef=" + tracker.mMessageRef + " SS=" + this.mPhone.getServiceState().getState());
            if ((pdu[0] & 1) == 1) {
                pdu[0] = (byte) (pdu[0] | 4);
                pdu[1] = (byte) tracker.mMessageRef;
            }
        }
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
            HashMap<String, Object> map = tracker.mData;
            byte[] smsc = (byte[]) map.get("smsc");
            byte[] pdu = (byte[]) map.get("pdu");
            Message reply = obtainMessage(2, tracker);
            if (tracker.mImsRetry != 0 || isIms()) {
                this.mCi.sendImsGsmSms(IccUtils.bytesToHexString(smsc), IccUtils.bytesToHexString(pdu), tracker.mImsRetry, tracker.mMessageRef, reply);
                tracker.mImsRetry++;
                return;
            }
            if (tracker.mRetryCount > 0 && (pdu[0] & 1) == 1) {
                pdu[0] = (byte) (pdu[0] | 4);
                pdu[1] = (byte) tracker.mMessageRef;
            }
            if (tracker.mRetryCount != 0 || !tracker.mExpectMore) {
                this.mCi.sendSMS(IccUtils.bytesToHexString(smsc), IccUtils.bytesToHexString(pdu), reply);
            } else {
                this.mCi.sendSMSExpectMore(IccUtils.bytesToHexString(smsc), IccUtils.bytesToHexString(pdu), reply);
            }
        } else {
            tracker.onFailed(this.mContext, getNotInServiceError(ss), 0);
        }
    }

    protected UiccCardApplication getUiccCardApplication() {
        Rlog.d(TAG, "GsmSMSDispatcher: subId = " + this.mPhone.getSubId() + " slotId = " + this.mPhone.getPhoneId());
        return this.mUiccController.getUiccCardApplication(this.mPhone.getPhoneId(), 1);
    }

    private void onUpdateIccAvailability() {
        UiccCardApplication newUiccApplication;
        UiccCardApplication app;
        if (this.mUiccController != null && (app = this.mUiccApplication.get()) != (newUiccApplication = getUiccCardApplication())) {
            if (app != null) {
                Rlog.d(TAG, "Removing stale icc objects.");
                if (this.mIccRecords.get() != null) {
                    ((IccRecords) this.mIccRecords.get()).unregisterForNewSms(this);
                }
                this.mIccRecords.set(null);
                this.mUiccApplication.set(null);
            }
            if (newUiccApplication != null) {
                Rlog.d(TAG, "New Uicc application found");
                this.mUiccApplication.set(newUiccApplication);
                this.mIccRecords.set(newUiccApplication.getIccRecords());
                if (this.mIccRecords.get() != null) {
                    ((IccRecords) this.mIccRecords.get()).registerForNewSms(this, 14, null);
                }
            }
        }
    }
}
