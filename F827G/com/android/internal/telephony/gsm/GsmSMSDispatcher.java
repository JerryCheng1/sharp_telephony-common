package com.android.internal.telephony.gsm;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Message;
import android.provider.Telephony.CellBroadcasts;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsHeader.ConcatRef;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.internal.telephony.gsm.SmsMessage.SubmitPdu;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class GsmSMSDispatcher extends SMSDispatcher {
    private static final int EVENT_NEW_SMS_STATUS_REPORT = 100;
    private static final String TAG = "GsmSMSDispatcher";
    private static final boolean VDBG = false;
    private GsmInboundSmsHandler mGsmInboundSmsHandler;
    private AtomicReference<UiccCardApplication> mUiccApplication = new AtomicReference();
    protected UiccController mUiccController = null;

    public GsmSMSDispatcher(PhoneBase phoneBase, SmsUsageMonitor smsUsageMonitor, ImsSMSDispatcher imsSMSDispatcher, GsmInboundSmsHandler gsmInboundSmsHandler) {
        super(phoneBase, smsUsageMonitor, imsSMSDispatcher);
        this.mCi.setOnSmsStatus(this, 100, null);
        this.mCi.setOnSmsOnSim(this, 16, null);
        this.mGsmInboundSmsHandler = gsmInboundSmsHandler;
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 15, null);
        Rlog.d(TAG, "GsmSMSDispatcher created");
    }

    private void handleStatusReport(AsyncResult asyncResult) {
        String str = (String) asyncResult.result;
        SmsMessage newFromCDS = SmsMessage.newFromCDS(str);
        if (newFromCDS != null) {
            int status = newFromCDS.getStatus();
            int i = newFromCDS.mMessageRef;
            int size = this.deliveryPendingList.size();
            int i2 = 0;
            while (i2 < size) {
                SmsTracker smsTracker = (SmsTracker) this.deliveryPendingList.get(i2);
                if (smsTracker.mMessageRef == i) {
                    if (status >= 64 || status < 32) {
                        this.deliveryPendingList.remove(i2);
                        smsTracker.updateSentMessageStatus(this.mContext, status);
                    }
                    PendingIntent pendingIntent = smsTracker.mDeliveryIntent;
                    Intent intent = new Intent();
                    intent.putExtra("pdu", IccUtils.hexStringToBytes(str));
                    intent.putExtra(CellBroadcasts.MESSAGE_FORMAT, getFormat());
                    try {
                        pendingIntent.send(this.mContext, -1, intent);
                    } catch (CanceledException e) {
                    }
                } else {
                    i2++;
                }
            }
        }
        this.mCi.acknowledgeLastIncomingGsmSms(true, 1, null);
    }

    private void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            UiccCardApplication uiccCardApplication = getUiccCardApplication();
            UiccCardApplication uiccCardApplication2 = (UiccCardApplication) this.mUiccApplication.get();
            if (uiccCardApplication2 != uiccCardApplication) {
                if (uiccCardApplication2 != null) {
                    Rlog.d(TAG, "Removing stale icc objects.");
                    if (this.mIccRecords.get() != null) {
                        ((IccRecords) this.mIccRecords.get()).unregisterForNewSms(this);
                    }
                    this.mIccRecords.set(null);
                    this.mUiccApplication.set(null);
                }
                if (uiccCardApplication != null) {
                    Rlog.d(TAG, "New Uicc application found");
                    this.mUiccApplication.set(uiccCardApplication);
                    this.mIccRecords.set(uiccCardApplication.getIccRecords());
                    if (this.mIccRecords.get() != null) {
                        ((IccRecords) this.mIccRecords.get()).registerForNewSms(this, 14, null);
                    }
                }
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public TextEncodingDetails calculateLength(CharSequence charSequence, boolean z) {
        return SmsMessage.calculateLength(charSequence, z);
    }

    public void dispose() {
        super.dispose();
        this.mCi.unSetOnSmsStatus(this);
        this.mUiccController.unregisterForIccChanged(this);
        this.mCi.unSetOnSmsOnSim(this);
        if (this.mIccRecords.get() != null) {
            ((IccRecords) this.mIccRecords.get()).unregisterForNewSms(this);
        }
    }

    /* Access modifiers changed, original: protected */
    public String getFormat() {
        return SmsMessage.FORMAT_3GPP;
    }

    /* Access modifiers changed, original: protected */
    public SmsTracker getNewSubmitPduTracker(String str, String str2, String str3, SmsHeader smsHeader, int i, PendingIntent pendingIntent, PendingIntent pendingIntent2, boolean z, int i2, boolean z2, int i3, AtomicInteger atomicInteger, AtomicBoolean atomicBoolean, Uri uri, String str4) {
        SubmitPdu submitPdu = SmsMessage.getSubmitPdu(str2, str, str3, pendingIntent2 != null, SmsHeader.toByteArray(smsHeader), i, smsHeader.languageTable, smsHeader.languageShiftTable, i3);
        if (submitPdu != null) {
            HashMap smsTrackerMap = getSmsTrackerMap(str, str2, str3, submitPdu);
            String format = getFormat();
            boolean z3 = !z || z2;
            SmsTracker smsTracker = getSmsTracker(smsTrackerMap, pendingIntent, pendingIntent2, format, atomicInteger, atomicBoolean, uri, smsHeader, z3, str4, true, i3);
            ConcatRef concatRef = smsHeader.concatRef;
            boolean z4 = !z && concatRef.seqNumber == 1;
            smsTracker.mFirstOfMultiPart = z4;
            smsTracker.mMsgCount = concatRef.msgCount;
            smsTracker.mSendConfirmed = this.mSendConfirmed;
            return smsTracker;
        }
        Rlog.e(TAG, "GsmSMSDispatcher.sendNewSubmitPdu(): getSubmitPdu() returned null");
        return null;
    }

    /* Access modifiers changed, original: protected */
    public UiccCardApplication getUiccCardApplication() {
        Rlog.d(TAG, "GsmSMSDispatcher: subId = " + this.mPhone.getSubId() + " slotId = " + this.mPhone.getPhoneId());
        return this.mUiccController.getUiccCardApplication(this.mPhone.getPhoneId(), 1);
    }

    public void handleMessage(Message message) {
        switch (message.what) {
            case 14:
                this.mGsmInboundSmsHandler.sendMessage(1, message.obj);
                return;
            case 15:
                onUpdateIccAvailability();
                return;
            case 16:
                if (this.mIccRecords.get() != null) {
                    ((SIMRecords) this.mIccRecords.get()).handleSmsOnIcc((AsyncResult) message.obj);
                    return;
                }
                return;
            case 100:
                handleStatusReport((AsyncResult) message.obj);
                return;
            default:
                super.handleMessage(message);
                return;
        }
    }

    /* Access modifiers changed, original: protected */
    public void injectSmsPdu(byte[] bArr, String str, PendingIntent pendingIntent) {
        throw new IllegalStateException("This method must be called only on ImsSMSDispatcher");
    }

    /* Access modifiers changed, original: protected */
    public void sendData(String str, String str2, int i, int i2, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        SubmitPdu submitPdu = SmsMessage.getSubmitPdu(str2, str, i, i2, bArr, pendingIntent2 != null);
        if (submitPdu != null) {
            SmsTracker smsTracker = getSmsTracker(getSmsTrackerMap(str, str2, i, i2, bArr, submitPdu), pendingIntent, pendingIntent2, getFormat(), null, false, null, false);
            String carrierAppPackageName = getCarrierAppPackageName();
            if (carrierAppPackageName != null) {
                Rlog.d(TAG, "Found carrier package.");
                DataSmsSender dataSmsSender = new DataSmsSender(smsTracker);
                dataSmsSender.sendSmsByCarrierApp(carrierAppPackageName, new SmsSenderCallback(dataSmsSender));
                return;
            }
            Rlog.v(TAG, "No carrier package.");
            sendRawPdu(smsTracker);
            return;
        }
        Rlog.e(TAG, "GsmSMSDispatcher.sendData(): getSubmitPdu() returned null");
    }

    /* Access modifiers changed, original: protected */
    public void sendSms(SmsTracker smsTracker) {
        byte[] bArr = (byte[]) smsTracker.mData.get("pdu");
        if (smsTracker.mRetryCount > 0) {
            Rlog.d(TAG, "sendSms:  mRetryCount=" + smsTracker.mRetryCount + " mMessageRef=" + smsTracker.mMessageRef + " SS=" + this.mPhone.getServiceState().getState());
            if ((bArr[0] & 1) == 1) {
                bArr[0] = (byte) (bArr[0] | 4);
                bArr[1] = (byte) smsTracker.mMessageRef;
            }
        }
        Rlog.d(TAG, "sendSms:  isIms()=" + isIms() + " mRetryCount=" + smsTracker.mRetryCount + " mImsRetry=" + smsTracker.mImsRetry + " mMessageRef=" + smsTracker.mMessageRef + " SS=" + this.mPhone.getServiceState().getState());
        sendSmsByPstn(smsTracker);
    }

    /* Access modifiers changed, original: protected */
    public void sendSmsByPstn(SmsTracker smsTracker) {
        int state = this.mPhone.getServiceState().getState();
        int voiceRegState = this.mPhone.getServiceState().getVoiceRegState();
        int dataRegState = this.mPhone.getServiceState().getDataRegState();
        Rlog.d(TAG, "isIms = " + isIms() + ", ssVoice = " + voiceRegState + ", ssData = " + dataRegState);
        if (isIms() || voiceRegState == 0 || dataRegState == 0) {
            HashMap hashMap = smsTracker.mData;
            byte[] bArr = (byte[]) hashMap.get("smsc");
            byte[] bArr2 = (byte[]) hashMap.get("pdu");
            Message obtainMessage = obtainMessage(2, smsTracker);
            if (smsTracker.mImsRetry != 0 || isIms()) {
                this.mCi.sendImsGsmSms(IccUtils.bytesToHexString(bArr), IccUtils.bytesToHexString(bArr2), smsTracker.mImsRetry, smsTracker.mMessageRef, obtainMessage);
                smsTracker.mImsRetry++;
                return;
            }
            if (smsTracker.mRetryCount > 0 && (bArr2[0] & 1) == 1) {
                bArr2[0] = (byte) (bArr2[0] | 4);
                bArr2[1] = (byte) smsTracker.mMessageRef;
            }
            if (smsTracker.mRetryCount == 0 && smsTracker.mExpectMore) {
                this.mCi.sendSMSExpectMore(IccUtils.bytesToHexString(bArr), IccUtils.bytesToHexString(bArr2), obtainMessage);
                return;
            } else {
                this.mCi.sendSMS(IccUtils.bytesToHexString(bArr), IccUtils.bytesToHexString(bArr2), obtainMessage);
                return;
            }
        }
        smsTracker.onFailed(this.mContext, SMSDispatcher.getNotInServiceError(state), 0);
    }

    /* Access modifiers changed, original: protected */
    public void sendSubmitPdu(SmsTracker smsTracker) {
        sendRawPdu(smsTracker);
    }

    /* Access modifiers changed, original: protected */
    public void sendText(String str, String str2, String str3, PendingIntent pendingIntent, PendingIntent pendingIntent2, Uri uri, String str4, int i, boolean z, int i2) {
        SubmitPdu submitPdu = SmsMessage.getSubmitPdu(str2, str, str3, pendingIntent2 != null, i2);
        if (submitPdu != null) {
            SmsTracker smsTracker = getSmsTracker(getSmsTrackerMap(str, str2, str3, submitPdu), pendingIntent, pendingIntent2, getFormat(), uri, z, str3, true, i2);
            String carrierAppPackageName = getCarrierAppPackageName();
            if (carrierAppPackageName != null) {
                Rlog.d(TAG, "Found carrier package.");
                TextSmsSender textSmsSender = new TextSmsSender(smsTracker);
                textSmsSender.sendSmsByCarrierApp(carrierAppPackageName, new SmsSenderCallback(textSmsSender));
                return;
            }
            Rlog.v(TAG, "No carrier package.");
            sendRawPdu(smsTracker);
            return;
        }
        Rlog.e(TAG, "GsmSMSDispatcher.sendText(): getSubmitPdu() returned null");
    }
}
