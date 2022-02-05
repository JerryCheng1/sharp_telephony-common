package android.telephony.gsm;

import android.app.PendingIntent;
import android.telephony.SmsMessage;
import android.util.SeempJavaFilter;
import android.util.SeempLog;
import java.util.ArrayList;

@Deprecated
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class SmsManager {
    @Deprecated
    public static final int RESULT_ERROR_GENERIC_FAILURE = 1;
    @Deprecated
    public static final int RESULT_ERROR_NO_SERVICE = 4;
    @Deprecated
    public static final int RESULT_ERROR_NULL_PDU = 3;
    @Deprecated
    public static final int RESULT_ERROR_RADIO_OFF = 2;
    @Deprecated
    public static final int STATUS_ON_SIM_FREE = 0;
    @Deprecated
    public static final int STATUS_ON_SIM_READ = 1;
    @Deprecated
    public static final int STATUS_ON_SIM_SENT = 5;
    @Deprecated
    public static final int STATUS_ON_SIM_UNREAD = 3;
    @Deprecated
    public static final int STATUS_ON_SIM_UNSENT = 7;
    private static SmsManager sInstance;
    private android.telephony.SmsManager mSmsMgrProxy = android.telephony.SmsManager.getDefault();

    @Deprecated
    public static final SmsManager getDefault() {
        if (sInstance == null) {
            sInstance = new SmsManager();
        }
        return sInstance;
    }

    @Deprecated
    private SmsManager() {
    }

    @Deprecated
    public final void sendTextMessage(String destinationAddress, String scAddress, String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (SeempJavaFilter.check("android.telephony.gsm.SmsManager", "sendTextMessage").booleanValue()) {
            SeempLog.record("android.telephony.gsm.SmsManager|sendTextMessage|destinationAddress," + (destinationAddress == null ? "null" : destinationAddress) + " scAddress," + (scAddress == null ? "null" : scAddress) + " text,null|--end");
        }
        this.mSmsMgrProxy.sendTextMessage(destinationAddress, scAddress, text, sentIntent, deliveryIntent);
    }

    @Deprecated
    public final ArrayList<String> divideMessage(String text) {
        return this.mSmsMgrProxy.divideMessage(text);
    }

    @Deprecated
    public final void sendMultipartTextMessage(String destinationAddress, String scAddress, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents) {
        if (SeempJavaFilter.check("android.telephony.SmsManager", "sendMultipartTextMessage").booleanValue()) {
            SeempLog.record("android.telephony.SmsManager|sendMultipartTextMessage|destinationAddress," + (destinationAddress == null ? "null" : destinationAddress) + " scAddress," + (scAddress == null ? "null" : scAddress) + "|--end");
        }
        this.mSmsMgrProxy.sendMultipartTextMessage(destinationAddress, scAddress, parts, sentIntents, deliveryIntents);
    }

    @Deprecated
    public final void sendDataMessage(String destinationAddress, String scAddress, short destinationPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (SeempJavaFilter.check("android.telephony.SmsManager", "sendDataMessage").booleanValue()) {
            SeempLog.record("android.telephony.SmsManager|sendDataMessage|destinationAddress," + (destinationAddress == null ? "null" : destinationAddress) + " scAddress," + (scAddress == null ? "null" : scAddress) + "|--end");
        }
        this.mSmsMgrProxy.sendDataMessage(destinationAddress, scAddress, destinationPort, data, sentIntent, deliveryIntent);
    }

    @Deprecated
    public final boolean copyMessageToSim(byte[] smsc, byte[] pdu, int status) {
        if (SeempJavaFilter.check("android.telephony.gsm.SmsManager", "copyMessageToSim").booleanValue()) {
            SeempLog.record("android.telephony.gsm.SmsManager|copyMessageToSim|--end");
        }
        return this.mSmsMgrProxy.copyMessageToIcc(smsc, pdu, status);
    }

    @Deprecated
    public final boolean deleteMessageFromSim(int messageIndex) {
        if (SeempJavaFilter.check("android.telephony.gsm.SmsManager", "deleteMessageFromSim").booleanValue()) {
            SeempLog.record("android.telephony.gsm.SmsManager|deleteMessageFromSim|--end");
        }
        return this.mSmsMgrProxy.deleteMessageFromIcc(messageIndex);
    }

    @Deprecated
    public final boolean updateMessageOnSim(int messageIndex, int newStatus, byte[] pdu) {
        if (SeempJavaFilter.check("android.telephony.gsm.SmsManager", "updateMessageOnSim").booleanValue()) {
            SeempLog.record("android.telephony.gsm.SmsManager|updateMessageOnSim|--end");
        }
        return this.mSmsMgrProxy.updateMessageOnIcc(messageIndex, newStatus, pdu);
    }

    @Deprecated
    public final ArrayList<SmsMessage> getAllMessagesFromSim() {
        if (SeempJavaFilter.check("android.telephony.gsm.SmsManager", "getAllMessagesFromSim").booleanValue()) {
            SeempLog.record("android.telephony.gsm.SmsManager|getAllMessagesFromSim|--end");
        }
        return android.telephony.SmsManager.getDefault().getAllMessagesFromIcc();
    }
}
