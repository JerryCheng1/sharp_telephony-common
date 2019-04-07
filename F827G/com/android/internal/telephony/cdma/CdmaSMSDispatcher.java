package com.android.internal.telephony.cdma;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Intent;
import android.net.Uri;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Telephony.CellBroadcasts;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.internal.telephony.cdma.SmsMessage.SubmitPdu;
import com.android.internal.telephony.cdma.sms.UserData;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CdmaSMSDispatcher extends SMSDispatcher {
    private static final String TAG = "CdmaSMSDispatcher";
    private static final boolean VDBG = false;

    public CdmaSMSDispatcher(PhoneBase phoneBase, SmsUsageMonitor smsUsageMonitor, ImsSMSDispatcher imsSMSDispatcher) {
        super(phoneBase, smsUsageMonitor, imsSMSDispatcher);
        Rlog.d(TAG, "CdmaSMSDispatcher created");
    }

    /* Access modifiers changed, original: protected */
    public TextEncodingDetails calculateLength(CharSequence charSequence, boolean z) {
        return SmsMessage.calculateLength(charSequence, z);
    }

    /* Access modifiers changed, original: protected */
    public String getFormat() {
        return SmsMessage.FORMAT_3GPP2;
    }

    /* Access modifiers changed, original: protected */
    public SmsTracker getNewSubmitPduTracker(String str, String str2, String str3, SmsHeader smsHeader, int i, PendingIntent pendingIntent, PendingIntent pendingIntent2, boolean z, int i2, boolean z2, int i3, AtomicInteger atomicInteger, AtomicBoolean atomicBoolean, Uri uri, String str4) {
        boolean z3;
        UserData userData = new UserData();
        userData.payloadStr = str3;
        userData.userDataHeader = smsHeader;
        if (i == 1) {
            userData.msgEncoding = 9;
            z3 = this.mPhone.getContext().getResources().getBoolean(17957011);
            if (z3) {
                Rlog.d(TAG, "ascii7bitForLongMsg = " + z3);
                userData.msgEncoding = 2;
            }
        } else {
            userData.msgEncoding = 4;
        }
        userData.msgEncodingSet = true;
        z3 = pendingIntent2 != null && z;
        HashMap smsTrackerMap = getSmsTrackerMap(str, str2, str3, SmsMessage.getSubmitPdu(str, userData, z3, i2));
        String format = getFormat();
        boolean z4 = !z || z2;
        return getSmsTracker(smsTrackerMap, pendingIntent, pendingIntent2, format, atomicInteger, atomicBoolean, uri, smsHeader, z4, str4, true, i3);
    }

    /* Access modifiers changed, original: 0000 */
    public void handleCdmaStatusReport(SmsMessage smsMessage) {
        int size = this.deliveryPendingList.size();
        for (int i = 0; i < size; i++) {
            SmsTracker smsTracker = (SmsTracker) this.deliveryPendingList.get(i);
            if (smsTracker.mMessageRef == smsMessage.mMessageRef) {
                this.deliveryPendingList.remove(i);
                smsTracker.updateSentMessageStatus(this.mContext, 0);
                PendingIntent pendingIntent = smsTracker.mDeliveryIntent;
                Intent intent = new Intent();
                intent.putExtra("pdu", smsMessage.getPdu());
                intent.putExtra(CellBroadcasts.MESSAGE_FORMAT, getFormat());
                try {
                    pendingIntent.send(this.mContext, -1, intent);
                    return;
                } catch (CanceledException e) {
                    return;
                }
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void handleStatusReport(Object obj) {
        if (obj instanceof SmsMessage) {
            handleCdmaStatusReport((SmsMessage) obj);
        } else {
            Rlog.e(TAG, "handleStatusReport() called for object type " + obj.getClass().getName());
        }
    }

    /* Access modifiers changed, original: protected */
    public void injectSmsPdu(byte[] bArr, String str, PendingIntent pendingIntent) {
        throw new IllegalStateException("This method must be called only on ImsSMSDispatcher");
    }

    /* Access modifiers changed, original: protected */
    public void sendData(String str, String str2, int i, int i2, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        SmsTracker smsTracker = getSmsTracker(getSmsTrackerMap(str, str2, i, i2, bArr, SmsMessage.getSubmitPdu(str2, str, i, i2, bArr, pendingIntent2 != null)), pendingIntent, pendingIntent2, getFormat(), null, false, null, false);
        String carrierAppPackageName = getCarrierAppPackageName();
        if (carrierAppPackageName != null) {
            Rlog.d(TAG, "Found carrier package.");
            DataSmsSender dataSmsSender = new DataSmsSender(smsTracker);
            dataSmsSender.sendSmsByCarrierApp(carrierAppPackageName, new SmsSenderCallback(dataSmsSender));
            return;
        }
        Rlog.v(TAG, "No carrier package.");
        sendSubmitPdu(smsTracker);
    }

    /* Access modifiers changed, original: protected */
    public void sendSms(SmsTracker smsTracker) {
        byte[] bArr = (byte[]) smsTracker.mData.get("pdu");
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
            Message obtainMessage = obtainMessage(2, smsTracker);
            byte[] bArr = (byte[]) smsTracker.mData.get("pdu");
            voiceRegState = this.mPhone.getServiceState().getDataNetworkType();
            if ((voiceRegState == 14 || (voiceRegState == 13 && !this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed())) && this.mPhone.getServiceState().getVoiceNetworkType() == 7 && ((CDMAPhone) this.mPhone).mCT.mState != State.IDLE) {
            }
            if (smsTracker.mImsRetry == 0 && !isIms()) {
                this.mCi.sendCdmaSms(bArr, obtainMessage);
                return;
            } else if (this.mImsSMSDispatcher.isImsSmsEnabled()) {
                this.mCi.sendImsCdmaSms(bArr, smsTracker.mImsRetry, smsTracker.mMessageRef, obtainMessage);
                smsTracker.mImsRetry++;
                return;
            } else {
                this.mCi.sendCdmaSms(bArr, obtainMessage);
                this.mImsSMSDispatcher.enableSendSmsOverIms(true);
                return;
            }
        }
        smsTracker.onFailed(this.mContext, SMSDispatcher.getNotInServiceError(state), 0);
    }

    /* Access modifiers changed, original: 0000 */
    public void sendStatusReportMessage(SmsMessage smsMessage) {
        sendMessage(obtainMessage(10, smsMessage));
    }

    /* Access modifiers changed, original: protected */
    public void sendSubmitPdu(SmsTracker smsTracker) {
        if (SystemProperties.getBoolean("ril.cdma.inecmmode", false)) {
            smsTracker.onFailed(this.mContext, 4, 0);
        } else {
            sendRawPdu(smsTracker);
        }
    }

    /* Access modifiers changed, original: protected */
    public void sendText(String str, String str2, String str3, PendingIntent pendingIntent, PendingIntent pendingIntent2, Uri uri, String str4, int i, boolean z, int i2) {
        SubmitPdu submitPdu = SmsMessage.getSubmitPdu(str2, str, str3, pendingIntent2 != null, null, i);
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
            sendSubmitPdu(smsTracker);
            return;
        }
        Rlog.e(TAG, "CdmaSMSDispatcher.sendText(): getSubmitPdu() returned null");
    }
}
