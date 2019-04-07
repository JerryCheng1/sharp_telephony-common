package android.telephony.gsm;

import android.app.PendingIntent;
import android.telephony.SmsMessage;
import android.util.SeempJavaFilter;
import android.util.SeempLog;
import java.util.ArrayList;

@Deprecated
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
    private SmsManager() {
    }

    @Deprecated
    public static final SmsManager getDefault() {
        if (sInstance == null) {
            sInstance = new SmsManager();
        }
        return sInstance;
    }

    @Deprecated
    public final boolean copyMessageToSim(byte[] bArr, byte[] bArr2, int i) {
        if (SeempJavaFilter.check("android.telephony.gsm.SmsManager", "copyMessageToSim").booleanValue()) {
            SeempLog.record("android.telephony.gsm.SmsManager|copyMessageToSim|--end");
        }
        return this.mSmsMgrProxy.copyMessageToIcc(bArr, bArr2, i);
    }

    @Deprecated
    public final boolean deleteMessageFromSim(int i) {
        if (SeempJavaFilter.check("android.telephony.gsm.SmsManager", "deleteMessageFromSim").booleanValue()) {
            SeempLog.record("android.telephony.gsm.SmsManager|deleteMessageFromSim|--end");
        }
        return this.mSmsMgrProxy.deleteMessageFromIcc(i);
    }

    @Deprecated
    public final ArrayList<String> divideMessage(String str) {
        return this.mSmsMgrProxy.divideMessage(str);
    }

    @Deprecated
    public final ArrayList<SmsMessage> getAllMessagesFromSim() {
        if (SeempJavaFilter.check("android.telephony.gsm.SmsManager", "getAllMessagesFromSim").booleanValue()) {
            SeempLog.record("android.telephony.gsm.SmsManager|getAllMessagesFromSim|--end");
        }
        return android.telephony.SmsManager.getDefault().getAllMessagesFromIcc();
    }

    @Deprecated
    public final void sendDataMessage(String str, String str2, short s, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        if (SeempJavaFilter.check("android.telephony.SmsManager", "sendDataMessage").booleanValue()) {
            SeempLog.record("android.telephony.SmsManager|sendDataMessage|destinationAddress," + (str == null ? "null" : str) + " " + "scAddress," + (str2 == null ? "null" : str2) + "|--end");
        }
        this.mSmsMgrProxy.sendDataMessage(str, str2, s, bArr, pendingIntent, pendingIntent2);
    }

    @Deprecated
    public final void sendMultipartTextMessage(String str, String str2, ArrayList<String> arrayList, ArrayList<PendingIntent> arrayList2, ArrayList<PendingIntent> arrayList3) {
        if (SeempJavaFilter.check("android.telephony.SmsManager", "sendMultipartTextMessage").booleanValue()) {
            SeempLog.record("android.telephony.SmsManager|sendMultipartTextMessage|destinationAddress," + (str == null ? "null" : str) + " " + "scAddress," + (str2 == null ? "null" : str2) + "|--end");
        }
        this.mSmsMgrProxy.sendMultipartTextMessage(str, str2, arrayList, arrayList2, arrayList3);
    }

    @Deprecated
    public final void sendTextMessage(String str, String str2, String str3, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        if (SeempJavaFilter.check("android.telephony.gsm.SmsManager", "sendTextMessage").booleanValue()) {
            SeempLog.record("android.telephony.gsm.SmsManager|sendTextMessage|destinationAddress," + (str == null ? "null" : str) + " " + "scAddress," + (str2 == null ? "null" : str2) + " " + "text," + "null" + "|--end");
        }
        this.mSmsMgrProxy.sendTextMessage(str, str2, str3, pendingIntent, pendingIntent2);
    }

    @Deprecated
    public final boolean updateMessageOnSim(int i, int i2, byte[] bArr) {
        if (SeempJavaFilter.check("android.telephony.gsm.SmsManager", "updateMessageOnSim").booleanValue()) {
            SeempLog.record("android.telephony.gsm.SmsManager|updateMessageOnSim|--end");
        }
        return this.mSmsMgrProxy.updateMessageOnIcc(i, i2, bArr);
    }
}
