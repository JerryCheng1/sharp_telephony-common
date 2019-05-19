package com.android.internal.telephony.gsm;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Message;
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
import com.android.internal.telephony.gsm.SmsMessage.SubmitPdu;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class GsmSMSDispatcher extends SMSDispatcher {
    private static final int EVENT_NEW_SMS_STATUS_REPORT = 100;
    private static boolean OUTPUT_DEBUG_LOG = false;
    private static final String TAG = "GsmSMSDispatcher";
    private static final boolean VDBG = false;
    private GsmInboundSmsHandler mGsmInboundSmsHandler;
    private AtomicReference<IccRecords> mIccRecords = new AtomicReference();
    private AtomicReference<UiccCardApplication> mUiccApplication = new AtomicReference();
    protected UiccController mUiccController = null;

    static {
        OUTPUT_DEBUG_LOG = true;
        if (Build.TYPE.equals(Carriers.USER)) {
            OUTPUT_DEBUG_LOG = false;
        }
    }

    public GsmSMSDispatcher(Phone phone, SmsUsageMonitor usageMonitor, ImsSMSDispatcher imsSMSDispatcher, GsmInboundSmsHandler gsmInboundSmsHandler) {
        super(phone, usageMonitor, imsSMSDispatcher);
        this.mCi.setOnSmsStatus(this, 100, null);
        this.mGsmInboundSmsHandler = gsmInboundSmsHandler;
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 15, null);
        Rlog.d(TAG, "GsmSMSDispatcher created");
    }

    public void dispose() {
        super.dispose();
        this.mCi.unSetOnSmsStatus(this);
        this.mUiccController.unregisterForIccChanged(this);
    }

    /* Access modifiers changed, original: protected */
    public String getFormat() {
        return SmsMessage.FORMAT_3GPP;
    }

    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 14:
                this.mGsmInboundSmsHandler.sendMessage(1, msg.obj);
                return;
            case 15:
                onUpdateIccAvailability();
                return;
            case 100:
                handleStatusReport((AsyncResult) msg.obj);
                return;
            default:
                super.handleMessage(msg);
                return;
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:22:0x0095  */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void handleStatusReport(AsyncResult ar) {
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "handleStatusReport Start");
        }
        String pduString = ar.result;
        SmsMessage sms = SmsMessage.newFromCDS(pduString);
        if (sms != null) {
            boolean isMatching = false;
            int tpStatus = sms.getStatus();
            int messageRef = sms.mMessageRef;
            int i = 0;
            int count = this.deliveryPendingList.size();
            while (i < count) {
                SmsTracker tracker = (SmsTracker) this.deliveryPendingList.get(i);
                if (tracker.mMessageRef == messageRef) {
                    if (tpStatus >= 64 || tpStatus < 32) {
                        this.deliveryPendingList.remove(i);
                        if (OUTPUT_DEBUG_LOG) {
                            Rlog.d(TAG, "GsmSMSDispathcer-handleStatusReport remove MessageRef is " + tracker.mMessageRef);
                        }
                        tracker.updateSentMessageStatus(this.mContext, tpStatus);
                    }
                    PendingIntent intent = tracker.mDeliveryIntent;
                    Intent fillIn = new Intent();
                    fillIn.putExtra("pdu", IccUtils.hexStringToBytes(pduString));
                    fillIn.putExtra(CellBroadcasts.MESSAGE_FORMAT, getFormat());
                    try {
                        intent.send(this.mContext, -1, fillIn);
                    } catch (CanceledException e) {
                    }
                    isMatching = true;
                    if (!isMatching) {
                        intent = gsmCheckDeliveryPendingList(sms);
                        if (intent != null) {
                            fillIn = new Intent();
                            fillIn.putExtra("pdu", IccUtils.hexStringToBytes(pduString));
                            fillIn.putExtra(CellBroadcasts.MESSAGE_FORMAT, SmsMessage.FORMAT_3GPP);
                            if (OUTPUT_DEBUG_LOG) {
                                Rlog.d(TAG, "send DeliveryIntent, messageRef : " + messageRef);
                            }
                            try {
                                intent.send(this.mContext, -1, fillIn);
                            } catch (CanceledException e2) {
                            }
                        } else {
                            deliveryPendingReportList.add(new ReportTracker(sms.mMessageRef, IccUtils.hexStringToBytes(pduString), SmsMessage.FORMAT_3GPP));
                            if (OUTPUT_DEBUG_LOG) {
                                Rlog.d(TAG, "GsmSMSDispathcer-handleStatusReport add MessageRef is " + sms.mMessageRef);
                            }
                        }
                    }
                } else {
                    i++;
                }
            }
            if (isMatching) {
            }
        }
        this.mCi.acknowledgeLastIncomingGsmSms(true, 1, null);
    }

    /* Access modifiers changed, original: protected */
    public PendingIntent isUpdateDeliveryPendingList(int messageRef) {
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "GSM isUpdateDeliveryPendingList Start! messageRef = " + messageRef);
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
    public PendingIntent gsmCheckDeliveryPendingList(SmsMessage sms) {
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "GSM checkDeliveryPendingList Start");
        }
        return this.mImsSMSDispatcher.checkDeliveryPendingList(sms);
    }

    /* Access modifiers changed, original: protected */
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
            sendRawPdu(tracker);
            return;
        }
        Rlog.e(TAG, "GsmSMSDispatcher.sendData(): getSubmitPdu() returned null");
    }

    public void sendText(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, Uri messageUri, String callingPkg, boolean persistMessage, int priority, boolean isExpectMore, int validityPeriod) {
        SubmitPduBase pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, text, deliveryIntent != null, validityPeriod);
        if (pdu != null) {
            SmsTracker tracker = getSmsTracker(getSmsTrackerMap(destAddr, scAddr, text, pdu), sentIntent, deliveryIntent, getFormat(), messageUri, false, text, true, validityPeriod, persistMessage);
            String carrierPackage = getCarrierAppPackageName();
            if (carrierPackage != null) {
                Rlog.d(TAG, "Found carrier package.");
                SmsSender textSmsSender = new TextSmsSender(tracker);
                textSmsSender.sendSmsByCarrierApp(carrierPackage, new SmsSenderCallback(textSmsSender));
                return;
            }
            Rlog.v(TAG, "No carrier package.");
            sendRawPdu(tracker);
            return;
        }
        Rlog.e(TAG, "GsmSMSDispatcher.sendText(): getSubmitPdu() returned null");
    }

    /* Access modifiers changed, original: protected */
    public void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        throw new IllegalStateException("This method must be called only on ImsSMSDispatcher");
    }

    /* Access modifiers changed, original: protected */
    public TextEncodingDetails calculateLength(CharSequence messageBody, boolean use7bitOnly) {
        return SmsMessage.calculateLength(messageBody, use7bitOnly);
    }

    /* Access modifiers changed, original: protected */
    public SmsTracker getNewSubmitPduTracker(String destinationAddress, String scAddress, String message, SmsHeader smsHeader, int encoding, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart, int priority, boolean isExpectMore, int validityPeriod, AtomicInteger unsentPartCount, AtomicBoolean anyPartFailed, Uri messageUri, String fullMessageText) {
        SubmitPduBase pdu = SmsMessage.getSubmitPdu(scAddress, destinationAddress, message, deliveryIntent != null, SmsHeader.toByteArray(smsHeader), encoding, smsHeader.languageTable, smsHeader.languageShiftTable, validityPeriod);
        if (pdu != null) {
            return getSmsTracker(getSmsTrackerMap(destinationAddress, scAddress, message, pdu), sentIntent, deliveryIntent, getFormat(), unsentPartCount, anyPartFailed, messageUri, smsHeader, lastPart ? isExpectMore : true, fullMessageText, true, validityPeriod, false);
        }
        Rlog.e(TAG, "GsmSMSDispatcher.sendNewSubmitPdu(): getSubmitPdu() returned null");
        return null;
    }

    /* Access modifiers changed, original: protected */
    public void sendSubmitPdu(SmsTracker tracker) {
        sendRawPdu(tracker);
    }

    /* Access modifiers changed, original: protected */
    public void sendSms(SmsTracker tracker) {
        byte[] pdu = (byte[]) tracker.getData().get("pdu");
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

    /* Access modifiers changed, original: protected */
    public void sendSmsByPstn(SmsTracker tracker) {
        int ss = this.mPhone.getServiceState().getState();
        if (isIms() || ss == 0) {
            HashMap<String, Object> map = tracker.getData();
            byte[] smsc = (byte[]) map.get("smsc");
            byte[] pdu = (byte[]) map.get("pdu");
            Message reply = obtainMessage(2, tracker);
            if (tracker.mImsRetry != 0 || isIms()) {
                this.mCi.sendImsGsmSms(IccUtils.bytesToHexString(smsc), IccUtils.bytesToHexString(pdu), tracker.mImsRetry, tracker.mMessageRef, reply);
                tracker.mImsRetry++;
            } else {
                if (tracker.mRetryCount > 0 && (pdu[0] & 1) == 1) {
                    pdu[0] = (byte) (pdu[0] | 4);
                    pdu[1] = (byte) tracker.mMessageRef;
                }
                if (tracker.mRetryCount == 0 && tracker.mExpectMore) {
                    this.mCi.sendSMSExpectMore(IccUtils.bytesToHexString(smsc), IccUtils.bytesToHexString(pdu), reply);
                } else {
                    this.mCi.sendSMS(IccUtils.bytesToHexString(smsc), IccUtils.bytesToHexString(pdu), reply);
                }
            }
            return;
        }
        tracker.onFailed(this.mContext, SMSDispatcher.getNotInServiceError(ss), 0);
    }

    /* Access modifiers changed, original: protected */
    public UiccCardApplication getUiccCardApplication() {
        Rlog.d(TAG, "GsmSMSDispatcher: subId = " + this.mPhone.getSubId() + " slotId = " + this.mPhone.getPhoneId());
        return this.mUiccController.getUiccCardApplication(this.mPhone.getPhoneId(), 1);
    }

    private void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            UiccCardApplication newUiccApplication = getUiccCardApplication();
            UiccCardApplication app = (UiccCardApplication) this.mUiccApplication.get();
            if (app != newUiccApplication) {
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
}
