package com.android.internal.telephony;

import android.app.PendingIntent;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Message;
import android.provider.Telephony;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.cdma.CdmaInboundSmsHandler;
import com.android.internal.telephony.cdma.CdmaSMSDispatcher;
import com.android.internal.telephony.gsm.GsmInboundSmsHandler;
import com.android.internal.telephony.gsm.GsmSMSDispatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class ImsSMSDispatcher extends SMSDispatcher {
    private static final String TAG = "RIL_ImsSms";
    private SMSDispatcher mCdmaDispatcher;
    private CdmaInboundSmsHandler mCdmaInboundSmsHandler;
    private SMSDispatcher mGsmDispatcher;
    private GsmInboundSmsHandler mGsmInboundSmsHandler;
    private boolean mIms = false;
    private String mImsSmsFormat = "unknown";
    private boolean mImsSmsEnabled = true;

    public ImsSMSDispatcher(PhoneBase phone, SmsStorageMonitor storageMonitor, SmsUsageMonitor usageMonitor) {
        super(phone, usageMonitor, null);
        Rlog.d(TAG, "ImsSMSDispatcher created");
        this.mCdmaDispatcher = new CdmaSMSDispatcher(phone, usageMonitor, this);
        this.mGsmInboundSmsHandler = GsmInboundSmsHandler.makeInboundSmsHandler(phone.getContext(), storageMonitor, phone);
        this.mCdmaInboundSmsHandler = CdmaInboundSmsHandler.makeInboundSmsHandler(phone.getContext(), storageMonitor, phone, (CdmaSMSDispatcher) this.mCdmaDispatcher);
        this.mGsmDispatcher = new GsmSMSDispatcher(phone, usageMonitor, this, this.mGsmInboundSmsHandler);
        new Thread(new SmsBroadcastUndelivered(phone.getContext(), this.mGsmInboundSmsHandler, this.mCdmaInboundSmsHandler)).start();
        this.mCi.registerForOn(this, 11, null);
        this.mCi.registerForImsNetworkStateChanged(this, 12, null);
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public void updatePhoneObject(PhoneBase phone) {
        Rlog.d(TAG, "In IMS updatePhoneObject ");
        super.updatePhoneObject(phone);
        this.mCdmaDispatcher.updatePhoneObject(phone);
        this.mGsmDispatcher.updatePhoneObject(phone);
        this.mGsmInboundSmsHandler.updatePhoneObject(phone);
        this.mCdmaInboundSmsHandler.updatePhoneObject(phone);
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public void dispose() {
        this.mCi.unregisterForOn(this);
        this.mCi.unregisterForImsNetworkStateChanged(this);
        this.mGsmDispatcher.dispose();
        this.mCdmaDispatcher.dispose();
        this.mGsmInboundSmsHandler.dispose();
        this.mCdmaInboundSmsHandler.dispose();
    }

    @Override // com.android.internal.telephony.SMSDispatcher, android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 11:
            case 12:
                this.mCi.getImsRegistrationState(obtainMessage(13));
                return;
            case 13:
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    updateImsInfo(ar);
                    return;
                } else {
                    Rlog.e(TAG, "IMS State query failed with exp " + ar.exception);
                    return;
                }
            default:
                super.handleMessage(msg);
                return;
        }
    }

    private void setImsSmsFormat(int format) {
        switch (format) {
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

    private void updateImsInfo(AsyncResult ar) {
        int[] responseArray = (int[]) ar.result;
        this.mIms = false;
        if (responseArray[0] == 1) {
            Rlog.d(TAG, "IMS is registered!");
            this.mIms = true;
        } else {
            Rlog.d(TAG, "IMS is NOT registered!");
        }
        setImsSmsFormat(responseArray[1]);
        if ("unknown".equals(this.mImsSmsFormat)) {
            Rlog.e(TAG, "IMS format was unknown!");
            this.mIms = false;
        }
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public void sendData(String destAddr, String scAddr, int destPort, int origPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendData(destAddr, scAddr, destPort, origPort, data, sentIntent, deliveryIntent);
        } else {
            this.mGsmDispatcher.sendData(destAddr, scAddr, destPort, origPort, data, sentIntent, deliveryIntent);
        }
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public void sendMultipartText(String destAddr, String scAddr, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, Uri messageUri, String callingPkg, int priority, boolean isExpectMore, int validityPeriod) {
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendMultipartText(destAddr, scAddr, parts, sentIntents, deliveryIntents, messageUri, callingPkg, priority, isExpectMore, validityPeriod);
        } else {
            this.mGsmDispatcher.sendMultipartText(destAddr, scAddr, parts, sentIntents, deliveryIntents, messageUri, callingPkg, priority, isExpectMore, validityPeriod);
        }
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public void sendSms(SMSDispatcher.SmsTracker tracker) {
        Rlog.e(TAG, "sendSms should never be called from here!");
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendSmsByPstn(SMSDispatcher.SmsTracker tracker) {
        Rlog.e(TAG, "sendSmsByPstn should never be called from here!");
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public void sendText(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, Uri messageUri, String callingPkg, int priority, boolean isExpectMore, int validityPeriod) {
        Rlog.d(TAG, "sendText");
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendText(destAddr, scAddr, text, sentIntent, deliveryIntent, messageUri, callingPkg, priority, isExpectMore, validityPeriod);
        } else {
            this.mGsmDispatcher.sendText(destAddr, scAddr, text, sentIntent, deliveryIntent, messageUri, callingPkg, priority, isExpectMore, validityPeriod);
        }
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        Rlog.d(TAG, "ImsSMSDispatcher:injectSmsPdu");
        try {
            SmsMessage msg = SmsMessage.createFromPdu(pdu, format);
            if (msg.getMessageClass() == SmsMessage.MessageClass.CLASS_1) {
                AsyncResult ar = new AsyncResult(receivedIntent, msg, (Throwable) null);
                if (format.equals(SmsMessage.FORMAT_3GPP)) {
                    Rlog.i(TAG, "ImsSMSDispatcher:injectSmsText Sending msg=" + msg + ", format=" + format + "to mGsmInboundSmsHandler");
                    this.mGsmInboundSmsHandler.sendMessage(8, ar);
                } else if (format.equals(SmsMessage.FORMAT_3GPP2)) {
                    Rlog.i(TAG, "ImsSMSDispatcher:injectSmsText Sending msg=" + msg + ", format=" + format + "to mCdmaInboundSmsHandler");
                    this.mCdmaInboundSmsHandler.sendMessage(8, ar);
                } else {
                    Rlog.e(TAG, "Invalid pdu format: " + format);
                    if (receivedIntent != null) {
                        receivedIntent.send(2);
                    }
                }
            } else if (receivedIntent != null) {
                receivedIntent.send(2);
            }
        } catch (Exception e) {
            Rlog.e(TAG, "injectSmsPdu failed: ", e);
            if (receivedIntent != null) {
                try {
                    receivedIntent.send(2);
                } catch (PendingIntent.CanceledException e2) {
                }
            }
        }
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public void sendRetrySms(SMSDispatcher.SmsTracker tracker) {
        String oldFormat = tracker.mFormat;
        String newFormat = 2 == this.mPhone.getPhoneType() ? this.mCdmaDispatcher.getFormat() : this.mGsmDispatcher.getFormat();
        if (!oldFormat.equals(newFormat)) {
            HashMap map = tracker.mData;
            if (!map.containsKey("scAddr") || !map.containsKey("destAddr") || (!map.containsKey(Telephony.Mms.Part.TEXT) && (!map.containsKey("data") || !map.containsKey("destPort")))) {
                Rlog.e(TAG, "sendRetrySms failed to re-encode per missing fields!");
                tracker.onFailed(this.mContext, 1, 0);
                return;
            }
            String scAddr = (String) map.get("scAddr");
            String destAddr = (String) map.get("destAddr");
            SmsMessageBase.SubmitPduBase pdu = null;
            if (map.containsKey(Telephony.Mms.Part.TEXT)) {
                Rlog.d(TAG, "sms failed was text");
                String text = (String) map.get(Telephony.Mms.Part.TEXT);
                if (isCdmaFormat(newFormat)) {
                    Rlog.d(TAG, "old format (gsm) ==> new format (cdma)");
                    pdu = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(scAddr, destAddr, text, tracker.mDeliveryIntent != null, (SmsHeader) null);
                    shouldSendSmsOverIms();
                } else {
                    Rlog.d(TAG, "old format (cdma) ==> new format (gsm)");
                    pdu = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(scAddr, destAddr, text, tracker.mDeliveryIntent != null, (byte[]) null);
                }
            } else if (map.containsKey("data")) {
                Rlog.d(TAG, "sms failed was data");
                byte[] data = (byte[]) map.get("data");
                Integer destPort = (Integer) map.get("destPort");
                if (isCdmaFormat(newFormat)) {
                    Rlog.d(TAG, "old format (gsm) ==> new format (cdma)");
                    pdu = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(scAddr, destAddr, destPort.intValue(), data, tracker.mDeliveryIntent != null);
                    shouldSendSmsOverIms();
                } else {
                    Rlog.d(TAG, "old format (cdma) ==> new format (gsm)");
                    pdu = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(scAddr, destAddr, destPort.intValue(), data, tracker.mDeliveryIntent != null);
                }
            }
            map.put("smsc", pdu.encodedScAddress);
            map.put("pdu", pdu.encodedMessage);
            SMSDispatcher dispatcher = isCdmaFormat(newFormat) ? this.mCdmaDispatcher : this.mGsmDispatcher;
            tracker.mFormat = dispatcher.getFormat();
            dispatcher.sendSms(tracker);
        } else if (isCdmaFormat(newFormat)) {
            Rlog.d(TAG, "old format matched new format (cdma)");
            shouldSendSmsOverIms();
            this.mCdmaDispatcher.sendSms(tracker);
        } else {
            Rlog.d(TAG, "old format matched new format (gsm)");
            this.mGsmDispatcher.sendSms(tracker);
        }
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendSubmitPdu(SMSDispatcher.SmsTracker tracker) {
        sendRawPdu(tracker);
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public String getFormat() {
        Rlog.e(TAG, "getFormat should never be called from here!");
        return "unknown";
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected GsmAlphabet.TextEncodingDetails calculateLength(CharSequence messageBody, boolean use7bitOnly) {
        Rlog.e(TAG, "Error! Not implemented for IMS.");
        return null;
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected SMSDispatcher.SmsTracker getNewSubmitPduTracker(String destinationAddress, String scAddress, String message, SmsHeader smsHeader, int format, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart, int priority, boolean isExpectMore, int validityPeriod, AtomicInteger unsentPartCount, AtomicBoolean anyPartFailed, Uri messageUri, String fullMessageText) {
        Rlog.e(TAG, "Error! Not implemented for IMS.");
        return null;
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public boolean isIms() {
        return this.mIms;
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public String getImsSmsFormat() {
        return this.mImsSmsFormat;
    }

    private boolean isCdmaMo() {
        if (!isIms() || !shouldSendSmsOverIms()) {
            return 2 == this.mPhone.getPhoneType();
        }
        return isCdmaFormat(this.mImsSmsFormat);
    }

    private boolean isCdmaFormat(String format) {
        return this.mCdmaDispatcher.getFormat().equals(format);
    }

    public void enableSendSmsOverIms(boolean enable) {
        this.mImsSmsEnabled = enable;
    }

    public boolean isImsSmsEnabled() {
        return this.mImsSmsEnabled;
    }

    public boolean shouldSendSmsOverIms() {
        boolean sendSmsOn1x = this.mContext.getResources().getBoolean(17956977);
        int currentCallState = this.mTelephonyManager.getCallState();
        int currentVoiceNetwork = this.mTelephonyManager.getVoiceNetworkType();
        int currentDataNetwork = this.mTelephonyManager.getDataNetworkType();
        Rlog.d(TAG, "data = " + currentDataNetwork + " voice = " + currentVoiceNetwork + " call state = " + currentCallState);
        if (sendSmsOn1x && currentDataNetwork == 14 && currentVoiceNetwork == 7) {
            TelephonyManager telephonyManager = this.mTelephonyManager;
            if (currentCallState != 0) {
                enableSendSmsOverIms(false);
                return false;
            }
        }
        return true;
    }
}
