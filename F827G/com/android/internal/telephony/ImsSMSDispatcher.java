package com.android.internal.telephony;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Message;
import android.provider.Telephony.Mms.Part;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.telephony.SmsMessage.MessageClass;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.SmsMessageBase.SubmitPduBase;
import com.android.internal.telephony.cdma.CdmaInboundSmsHandler;
import com.android.internal.telephony.cdma.CdmaSMSDispatcher;
import com.android.internal.telephony.gsm.GsmInboundSmsHandler;
import com.android.internal.telephony.gsm.GsmSMSDispatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ImsSMSDispatcher extends SMSDispatcher {
    private static final String TAG = "RIL_ImsSms";
    private SMSDispatcher mCdmaDispatcher;
    private CdmaInboundSmsHandler mCdmaInboundSmsHandler;
    private SMSDispatcher mGsmDispatcher;
    private GsmInboundSmsHandler mGsmInboundSmsHandler;
    private boolean mIms = false;
    private boolean mImsSmsEnabled = true;
    private String mImsSmsFormat = "unknown";

    public ImsSMSDispatcher(PhoneBase phoneBase, SmsStorageMonitor smsStorageMonitor, SmsUsageMonitor smsUsageMonitor) {
        super(phoneBase, smsUsageMonitor, null);
        Rlog.d(TAG, "ImsSMSDispatcher created");
        this.mCdmaDispatcher = new CdmaSMSDispatcher(phoneBase, smsUsageMonitor, this);
        this.mGsmInboundSmsHandler = GsmInboundSmsHandler.makeInboundSmsHandler(phoneBase.getContext(), smsStorageMonitor, phoneBase);
        this.mCdmaInboundSmsHandler = CdmaInboundSmsHandler.makeInboundSmsHandler(phoneBase.getContext(), smsStorageMonitor, phoneBase, (CdmaSMSDispatcher) this.mCdmaDispatcher);
        this.mGsmDispatcher = new GsmSMSDispatcher(phoneBase, smsUsageMonitor, this, this.mGsmInboundSmsHandler);
        new Thread(new SmsBroadcastUndelivered(phoneBase.getContext(), this.mGsmInboundSmsHandler, this.mCdmaInboundSmsHandler)).start();
        this.mCi.registerForOn(this, 11, null);
        this.mCi.registerForImsNetworkStateChanged(this, 12, null);
    }

    private boolean isCdmaFormat(String str) {
        return this.mCdmaDispatcher.getFormat().equals(str);
    }

    private boolean isCdmaMo() {
        return (isIms() && shouldSendSmsOverIms()) ? isCdmaFormat(this.mImsSmsFormat) : 2 == this.mPhone.getPhoneType();
    }

    private void setImsSmsFormat(int i) {
        switch (i) {
            case 1:
                this.mImsSmsFormat = SmsMessage.FORMAT_3GPP;
                return;
            case 2:
                this.mImsSmsFormat = SmsMessage.FORMAT_3GPP2;
                return;
            default:
                this.mImsSmsFormat = "unknown";
                return;
        }
    }

    private void updateImsInfo(AsyncResult asyncResult) {
        int[] iArr = (int[]) asyncResult.result;
        this.mIms = false;
        if (iArr[0] == 1) {
            Rlog.d(TAG, "IMS is registered!");
            this.mIms = true;
        } else {
            Rlog.d(TAG, "IMS is NOT registered!");
        }
        setImsSmsFormat(iArr[1]);
        if ("unknown".equals(this.mImsSmsFormat)) {
            Rlog.e(TAG, "IMS format was unknown!");
            this.mIms = false;
        }
    }

    /* Access modifiers changed, original: protected */
    public TextEncodingDetails calculateLength(CharSequence charSequence, boolean z) {
        Rlog.e(TAG, "Error! Not implemented for IMS.");
        return null;
    }

    public void dispose() {
        this.mCi.unregisterForOn(this);
        this.mCi.unregisterForImsNetworkStateChanged(this);
        this.mGsmDispatcher.dispose();
        this.mCdmaDispatcher.dispose();
        this.mGsmInboundSmsHandler.dispose();
        this.mCdmaInboundSmsHandler.dispose();
    }

    public void enableSendSmsOverIms(boolean z) {
        this.mImsSmsEnabled = z;
    }

    /* Access modifiers changed, original: protected */
    public String getFormat() {
        Rlog.e(TAG, "getFormat should never be called from here!");
        return "unknown";
    }

    public String getImsSmsFormat() {
        return this.mImsSmsFormat;
    }

    /* Access modifiers changed, original: protected */
    public SmsTracker getNewSubmitPduTracker(String str, String str2, String str3, SmsHeader smsHeader, int i, PendingIntent pendingIntent, PendingIntent pendingIntent2, boolean z, int i2, boolean z2, int i3, AtomicInteger atomicInteger, AtomicBoolean atomicBoolean, Uri uri, String str4) {
        Rlog.e(TAG, "Error! Not implemented for IMS.");
        return null;
    }

    public void handleMessage(Message message) {
        switch (message.what) {
            case 11:
            case 12:
                this.mCi.getImsRegistrationState(obtainMessage(13));
                return;
            case 13:
                AsyncResult asyncResult = (AsyncResult) message.obj;
                if (asyncResult.exception == null) {
                    updateImsInfo(asyncResult);
                    return;
                } else {
                    Rlog.e(TAG, "IMS State query failed with exp " + asyncResult.exception);
                    return;
                }
            default:
                super.handleMessage(message);
                return;
        }
    }

    /* Access modifiers changed, original: protected */
    public void injectSmsPdu(byte[] bArr, String str, PendingIntent pendingIntent) {
        Rlog.d(TAG, "ImsSMSDispatcher:injectSmsPdu");
        try {
            SmsMessage createFromPdu = SmsMessage.createFromPdu(bArr, str);
            if (createFromPdu.getMessageClass() == MessageClass.CLASS_1) {
                AsyncResult asyncResult = new AsyncResult(pendingIntent, createFromPdu, null);
                if (str.equals(SmsMessage.FORMAT_3GPP)) {
                    Rlog.i(TAG, "ImsSMSDispatcher:injectSmsText Sending msg=" + createFromPdu + ", format=" + str + "to mGsmInboundSmsHandler");
                    this.mGsmInboundSmsHandler.sendMessage(8, asyncResult);
                } else if (str.equals(SmsMessage.FORMAT_3GPP2)) {
                    Rlog.i(TAG, "ImsSMSDispatcher:injectSmsText Sending msg=" + createFromPdu + ", format=" + str + "to mCdmaInboundSmsHandler");
                    this.mCdmaInboundSmsHandler.sendMessage(8, asyncResult);
                } else {
                    Rlog.e(TAG, "Invalid pdu format: " + str);
                    if (pendingIntent != null) {
                        pendingIntent.send(2);
                    }
                }
            } else if (pendingIntent != null) {
                pendingIntent.send(2);
            }
        } catch (Exception e) {
            Rlog.e(TAG, "injectSmsPdu failed: ", e);
            if (pendingIntent != null) {
                try {
                    pendingIntent.send(2);
                } catch (CanceledException e2) {
                }
            }
        }
    }

    public boolean isIms() {
        return this.mIms;
    }

    public boolean isImsSmsEnabled() {
        return this.mImsSmsEnabled;
    }

    /* Access modifiers changed, original: protected */
    public void sendData(String str, String str2, int i, int i2, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendData(str, str2, i, i2, bArr, pendingIntent, pendingIntent2);
        } else {
            this.mGsmDispatcher.sendData(str, str2, i, i2, bArr, pendingIntent, pendingIntent2);
        }
    }

    /* Access modifiers changed, original: protected */
    public void sendMultipartText(String str, String str2, ArrayList<String> arrayList, ArrayList<PendingIntent> arrayList2, ArrayList<PendingIntent> arrayList3, Uri uri, String str3, int i, boolean z, int i2) {
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendMultipartText(str, str2, arrayList, arrayList2, arrayList3, uri, str3, i, z, i2);
        } else {
            this.mGsmDispatcher.sendMultipartText(str, str2, arrayList, arrayList2, arrayList3, uri, str3, i, z, i2);
        }
    }

    public void sendRetrySms(SmsTracker smsTracker) {
        Object format;
        boolean z = true;
        String str = smsTracker.mFormat;
        if (2 == this.mPhone.getPhoneType()) {
            format = this.mCdmaDispatcher.getFormat();
        } else {
            String format2 = this.mGsmDispatcher.getFormat();
        }
        if (!str.equals(format2)) {
            HashMap hashMap = smsTracker.mData;
            if (hashMap.containsKey("scAddr") && hashMap.containsKey("destAddr") && (hashMap.containsKey(Part.TEXT) || (hashMap.containsKey("data") && hashMap.containsKey("destPort")))) {
                SubmitPduBase submitPdu;
                String str2 = (String) hashMap.get("scAddr");
                str = (String) hashMap.get("destAddr");
                if (hashMap.containsKey(Part.TEXT)) {
                    Rlog.d(TAG, "sms failed was text");
                    String str3 = (String) hashMap.get(Part.TEXT);
                    if (isCdmaFormat(format2)) {
                        Rlog.d(TAG, "old format (gsm) ==> new format (cdma)");
                        submitPdu = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(str2, str, str3, smsTracker.mDeliveryIntent != null, null);
                        shouldSendSmsOverIms();
                    } else {
                        Rlog.d(TAG, "old format (cdma) ==> new format (gsm)");
                        if (smsTracker.mDeliveryIntent == null) {
                            z = false;
                        }
                        submitPdu = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(str2, str, str3, z, null);
                    }
                } else if (hashMap.containsKey("data")) {
                    Rlog.d(TAG, "sms failed was data");
                    byte[] bArr = (byte[]) hashMap.get("data");
                    Integer num = (Integer) hashMap.get("destPort");
                    int intValue;
                    if (isCdmaFormat(format2)) {
                        Rlog.d(TAG, "old format (gsm) ==> new format (cdma)");
                        intValue = num.intValue();
                        if (smsTracker.mDeliveryIntent == null) {
                            z = false;
                        }
                        submitPdu = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(str2, str, intValue, bArr, z);
                        shouldSendSmsOverIms();
                    } else {
                        Rlog.d(TAG, "old format (cdma) ==> new format (gsm)");
                        intValue = num.intValue();
                        if (smsTracker.mDeliveryIntent == null) {
                            z = false;
                        }
                        submitPdu = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(str2, str, intValue, bArr, z);
                    }
                } else {
                    submitPdu = null;
                }
                hashMap.put("smsc", submitPdu.encodedScAddress);
                hashMap.put("pdu", submitPdu.encodedMessage);
                SMSDispatcher sMSDispatcher = isCdmaFormat(format2) ? this.mCdmaDispatcher : this.mGsmDispatcher;
                smsTracker.mFormat = sMSDispatcher.getFormat();
                sMSDispatcher.sendSms(smsTracker);
                return;
            }
            Rlog.e(TAG, "sendRetrySms failed to re-encode per missing fields!");
            smsTracker.onFailed(this.mContext, 1, 0);
        } else if (isCdmaFormat(format2)) {
            Rlog.d(TAG, "old format matched new format (cdma)");
            shouldSendSmsOverIms();
            this.mCdmaDispatcher.sendSms(smsTracker);
        } else {
            Rlog.d(TAG, "old format matched new format (gsm)");
            this.mGsmDispatcher.sendSms(smsTracker);
        }
    }

    /* Access modifiers changed, original: protected */
    public void sendSms(SmsTracker smsTracker) {
        Rlog.e(TAG, "sendSms should never be called from here!");
    }

    /* Access modifiers changed, original: protected */
    public void sendSmsByPstn(SmsTracker smsTracker) {
        Rlog.e(TAG, "sendSmsByPstn should never be called from here!");
    }

    /* Access modifiers changed, original: protected */
    public void sendSubmitPdu(SmsTracker smsTracker) {
        sendRawPdu(smsTracker);
    }

    /* Access modifiers changed, original: protected */
    public void sendText(String str, String str2, String str3, PendingIntent pendingIntent, PendingIntent pendingIntent2, Uri uri, String str4, int i, boolean z, int i2) {
        Rlog.d(TAG, "sendText");
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendText(str, str2, str3, pendingIntent, pendingIntent2, uri, str4, i, z, i2);
        } else {
            this.mGsmDispatcher.sendText(str, str2, str3, pendingIntent, pendingIntent2, uri, str4, i, z, i2);
        }
    }

    public boolean shouldSendSmsOverIms() {
        boolean z = this.mContext.getResources().getBoolean(17956977);
        int callState = this.mTelephonyManager.getCallState();
        int voiceNetworkType = this.mTelephonyManager.getVoiceNetworkType();
        int dataNetworkType = this.mTelephonyManager.getDataNetworkType();
        Rlog.d(TAG, "data = " + dataNetworkType + " voice = " + voiceNetworkType + " call state = " + callState);
        if (z && dataNetworkType == 14 && voiceNetworkType == 7) {
            TelephonyManager telephonyManager = this.mTelephonyManager;
            if (callState != 0) {
                enableSendSmsOverIms(false);
                return false;
            }
        }
        return true;
    }

    /* Access modifiers changed, original: protected */
    public void updatePhoneObject(PhoneBase phoneBase) {
        Rlog.d(TAG, "In IMS updatePhoneObject ");
        super.updatePhoneObject(phoneBase);
        this.mCdmaDispatcher.updatePhoneObject(phoneBase);
        this.mGsmDispatcher.updatePhoneObject(phoneBase);
        this.mGsmInboundSmsHandler.updatePhoneObject(phoneBase);
        this.mCdmaInboundSmsHandler.updatePhoneObject(phoneBase);
    }
}
