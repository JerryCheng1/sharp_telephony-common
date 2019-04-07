package android.telephony;

import android.app.ActivityThread;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SeempJavaFilter;
import android.util.SeempLog;
import com.android.internal.telephony.IMms;
import com.android.internal.telephony.ISms;
import com.android.internal.telephony.ISms.Stub;
import com.android.internal.telephony.SmsRawData;
import com.google.android.mms.pdu.PduHeaders;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class SmsManager {
    public static final int CELL_BROADCAST_RAN_TYPE_CDMA = 1;
    public static final int CELL_BROADCAST_RAN_TYPE_GSM = 0;
    private static final int DEFAULT_SUBSCRIPTION_ID = -1002;
    private static String DIALOG_TYPE_KEY = "dialog_type";
    public static final String EXTRA_MMS_DATA = "android.telephony.extra.MMS_DATA";
    public static final String EXTRA_MMS_HTTP_STATUS = "android.telephony.extra.MMS_HTTP_STATUS";
    public static final String MESSAGE_STATUS_READ = "read";
    public static final String MESSAGE_STATUS_SEEN = "seen";
    public static final String MMS_CONFIG_ALIAS_ENABLED = "aliasEnabled";
    public static final String MMS_CONFIG_ALIAS_MAX_CHARS = "aliasMaxChars";
    public static final String MMS_CONFIG_ALIAS_MIN_CHARS = "aliasMinChars";
    public static final String MMS_CONFIG_ALLOW_ATTACH_AUDIO = "allowAttachAudio";
    public static final String MMS_CONFIG_APPEND_TRANSACTION_ID = "enabledTransID";
    public static final String MMS_CONFIG_EMAIL_GATEWAY_NUMBER = "emailGatewayNumber";
    public static final String MMS_CONFIG_GROUP_MMS_ENABLED = "enableGroupMms";
    public static final String MMS_CONFIG_HTTP_PARAMS = "httpParams";
    public static final String MMS_CONFIG_HTTP_SOCKET_TIMEOUT = "httpSocketTimeout";
    public static final String MMS_CONFIG_MAX_IMAGE_HEIGHT = "maxImageHeight";
    public static final String MMS_CONFIG_MAX_IMAGE_WIDTH = "maxImageWidth";
    public static final String MMS_CONFIG_MAX_MESSAGE_SIZE = "maxMessageSize";
    public static final String MMS_CONFIG_MESSAGE_TEXT_MAX_SIZE = "maxMessageTextSize";
    public static final String MMS_CONFIG_MMS_DELIVERY_REPORT_ENABLED = "enableMMSDeliveryReports";
    public static final String MMS_CONFIG_MMS_ENABLED = "enabledMMS";
    public static final String MMS_CONFIG_MMS_READ_REPORT_ENABLED = "enableMMSReadReports";
    public static final String MMS_CONFIG_MULTIPART_SMS_ENABLED = "enableMultipartSMS";
    public static final String MMS_CONFIG_NAI_SUFFIX = "naiSuffix";
    public static final String MMS_CONFIG_NOTIFY_WAP_MMSC_ENABLED = "enabledNotifyWapMMSC";
    public static final String MMS_CONFIG_RECIPIENT_LIMIT = "recipientLimit";
    public static final String MMS_CONFIG_SEND_MULTIPART_SMS_AS_SEPARATE_MESSAGES = "sendMultipartSmsAsSeparateMessages";
    public static final String MMS_CONFIG_SHOW_CELL_BROADCAST_APP_LINKS = "config_cellBroadcastAppLinks";
    public static final String MMS_CONFIG_SMS_DELIVERY_REPORT_ENABLED = "enableSMSDeliveryReports";
    public static final String MMS_CONFIG_SMS_TO_MMS_TEXT_LENGTH_THRESHOLD = "smsToMmsTextLengthThreshold";
    public static final String MMS_CONFIG_SMS_TO_MMS_TEXT_THRESHOLD = "smsToMmsTextThreshold";
    public static final String MMS_CONFIG_SUBJECT_MAX_LENGTH = "maxSubjectLength";
    public static final String MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION = "supportMmsContentDisposition";
    public static final String MMS_CONFIG_UA_PROF_TAG_NAME = "uaProfTagName";
    public static final String MMS_CONFIG_UA_PROF_URL = "uaProfUrl";
    public static final String MMS_CONFIG_USER_AGENT = "userAgent";
    public static final int MMS_ERROR_CONFIGURATION_ERROR = 7;
    public static final int MMS_ERROR_HTTP_FAILURE = 4;
    public static final int MMS_ERROR_INVALID_APN = 2;
    public static final int MMS_ERROR_IO_ERROR = 5;
    public static final int MMS_ERROR_NO_DATA_NETWORK = 8;
    public static final int MMS_ERROR_RETRY = 6;
    public static final int MMS_ERROR_UNABLE_CONNECT_MMS = 3;
    public static final int MMS_ERROR_UNSPECIFIED = 1;
    private static final String PHONE_PACKAGE_NAME = "com.android.phone";
    public static final int RESULT_ERROR_FDN_CHECK_FAILURE = 6;
    public static final int RESULT_ERROR_GENERIC_FAILURE = 1;
    public static final int RESULT_ERROR_LIMIT_EXCEEDED = 5;
    public static final int RESULT_ERROR_NO_SERVICE = 4;
    public static final int RESULT_ERROR_NULL_PDU = 3;
    public static final int RESULT_ERROR_RADIO_OFF = 2;
    private static final int SMS_PICK = 2;
    public static final int SMS_TYPE_INCOMING = 0;
    public static final int SMS_TYPE_OUTGOING = 1;
    public static final int STATUS_ON_ICC_FREE = 0;
    public static final int STATUS_ON_ICC_READ = 1;
    public static final int STATUS_ON_ICC_SENT = 5;
    public static final int STATUS_ON_ICC_UNREAD = 3;
    public static final int STATUS_ON_ICC_UNSENT = 7;
    private static final String TAG = "SmsManager";
    private static final SmsManager sInstance = new SmsManager(DEFAULT_SUBSCRIPTION_ID);
    private static final Object sLockObject = new Object();
    private static final Map<Integer, SmsManager> sSubInstances = new ArrayMap();
    private int mSubId;

    private SmsManager(int i) {
        this.mSubId = i;
    }

    private ArrayList<SmsMessage> createMessageListFromRawRecords(List<SmsRawData> list) {
        ArrayList arrayList = new ArrayList();
        if (list != null) {
            int size = list.size();
            for (int i = 0; i < size; i++) {
                SmsRawData smsRawData = (SmsRawData) list.get(i);
                if (smsRawData != null) {
                    SmsMessage createFromEfRecord = SmsMessage.createFromEfRecord(i + 1, smsRawData.getBytes(), getSubscriptionId());
                    if (createFromEfRecord != null) {
                        arrayList.add(createFromEfRecord);
                    }
                }
            }
        }
        return arrayList;
    }

    public static SmsManager getDefault() {
        return sInstance;
    }

    public static int getDefaultSmsSubscriptionId() {
        int i = -1;
        try {
            return Stub.asInterface(ServiceManager.getService("isms")).getPreferredSmsSubscription();
        } catch (RemoteException | NullPointerException e) {
            return i;
        }
    }

    private static ISms getISmsService() {
        return Stub.asInterface(ServiceManager.getService("isms"));
    }

    private static ISms getISmsServiceOrThrow() {
        ISms iSmsService = getISmsService();
        if (iSmsService != null) {
            return iSmsService;
        }
        throw new UnsupportedOperationException("Sms is not supported");
    }

    public static SmsManager getSmsManagerForSubscriptionId(int i) {
        SmsManager smsManager;
        synchronized (sLockObject) {
            smsManager = (SmsManager) sSubInstances.get(Integer.valueOf(i));
            if (smsManager == null) {
                smsManager = new SmsManager(i);
                sSubInstances.put(Integer.valueOf(i), smsManager);
            }
        }
        return smsManager;
    }

    public Uri addMultimediaMessageDraft(Uri uri) {
        if (uri == null) {
            throw new IllegalArgumentException("Uri contentUri null");
        }
        try {
            IMms asInterface = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (asInterface != null) {
                return asInterface.addMultimediaMessageDraft(ActivityThread.currentPackageName(), uri);
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    public Uri addTextMessageDraft(String str, String str2) {
        try {
            IMms asInterface = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (asInterface != null) {
                return asInterface.addTextMessageDraft(ActivityThread.currentPackageName(), str, str2);
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    public boolean archiveStoredConversation(long j, boolean z) {
        try {
            IMms asInterface = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (asInterface != null) {
                return asInterface.archiveStoredConversation(ActivityThread.currentPackageName(), j, z);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    public boolean copyMessageToIcc(byte[] bArr, byte[] bArr2, int i) {
        if (SeempJavaFilter.check("android.telephony.SmsManager", "copyMessageToIcc").booleanValue()) {
            SeempLog.record("android.telephony.SmsManager|copyMessageToIcc|--end");
        }
        if (bArr2 == null) {
            throw new IllegalArgumentException("pdu is NULL");
        }
        try {
            ISms iSmsService = getISmsService();
            return iSmsService != null ? iSmsService.copyMessageToIccEfForSubscriber(getSubscriptionId(), ActivityThread.currentPackageName(), i, bArr2, bArr) : false;
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean deleteMessageFromIcc(int i) {
        if (SeempJavaFilter.check("android.telephony.SmsManager", "deleteMessageFromIcc").booleanValue()) {
            SeempLog.record("android.telephony.SmsManager|deleteMessageFromIcc|--end");
        }
        byte[] bArr = new byte[PduHeaders.START];
        Arrays.fill(bArr, (byte) -1);
        try {
            ISms iSmsService = getISmsService();
            return iSmsService != null ? iSmsService.updateMessageOnIccEfForSubscriber(getSubscriptionId(), ActivityThread.currentPackageName(), i, 0, bArr) : false;
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean deleteStoredConversation(long j) {
        try {
            IMms asInterface = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (asInterface != null) {
                return asInterface.deleteStoredConversation(ActivityThread.currentPackageName(), j);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    public boolean deleteStoredMessage(Uri uri) {
        if (uri == null) {
            throw new IllegalArgumentException("Empty message URI");
        }
        try {
            IMms asInterface = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (asInterface != null) {
                return asInterface.deleteStoredMessage(ActivityThread.currentPackageName(), uri);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    public boolean disableCellBroadcast(int i, int i2) {
        try {
            ISms iSmsService = getISmsService();
            return iSmsService != null ? iSmsService.disableCellBroadcastForSubscriber(getSubscriptionId(), i, i2) : false;
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean disableCellBroadcastRange(int i, int i2, int i3) {
        if (i2 < i) {
            throw new IllegalArgumentException("endMessageId < startMessageId");
        }
        try {
            ISms iSmsService = getISmsService();
            return iSmsService != null ? iSmsService.disableCellBroadcastRangeForSubscriber(getSubscriptionId(), i, i2, i3) : false;
        } catch (RemoteException e) {
            return false;
        }
    }

    public ArrayList<String> divideMessage(String str) {
        if (str != null) {
            return SmsMessage.fragmentText(str);
        }
        throw new IllegalArgumentException("text is null");
    }

    public void downloadMultimediaMessage(Context context, String str, Uri uri, Bundle bundle, PendingIntent pendingIntent) {
        if (TextUtils.isEmpty(str)) {
            throw new IllegalArgumentException("Empty MMS location URL");
        } else if (uri == null) {
            throw new IllegalArgumentException("Uri contentUri null");
        } else {
            try {
                IMms asInterface = IMms.Stub.asInterface(ServiceManager.getService("imms"));
                if (asInterface != null) {
                    asInterface.downloadMessage(getSubscriptionId(), ActivityThread.currentPackageName(), str, uri, bundle, pendingIntent);
                }
            } catch (RemoteException e) {
            }
        }
    }

    public boolean enableCellBroadcast(int i, int i2) {
        try {
            ISms iSmsService = getISmsService();
            return iSmsService != null ? iSmsService.enableCellBroadcastForSubscriber(getSubscriptionId(), i, i2) : false;
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean enableCellBroadcastRange(int i, int i2, int i3) {
        if (i2 < i) {
            throw new IllegalArgumentException("endMessageId < startMessageId");
        }
        try {
            ISms iSmsService = getISmsService();
            return iSmsService != null ? iSmsService.enableCellBroadcastRangeForSubscriber(getSubscriptionId(), i, i2, i3) : false;
        } catch (RemoteException e) {
            return false;
        }
    }

    public ArrayList<SmsMessage> getAllMessagesFromIcc() {
        List list = null;
        try {
            ISms iSmsService = getISmsService();
            if (iSmsService != null) {
                list = iSmsService.getAllMessagesFromIccEfForSubscriber(getSubscriptionId(), ActivityThread.currentPackageName());
            }
        } catch (RemoteException e) {
        }
        return createMessageListFromRawRecords(list);
    }

    public boolean getAutoPersisting() {
        try {
            IMms asInterface = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (asInterface != null) {
                return asInterface.getAutoPersisting();
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    public Bundle getCarrierConfigValues() {
        try {
            IMms asInterface = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (asInterface != null) {
                return asInterface.getCarrierConfigValues(getSubscriptionId());
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    public String getImsSmsFormat() {
        String str = "unknown";
        try {
            ISms iSmsService = getISmsService();
            return iSmsService != null ? iSmsService.getImsSmsFormatForSubscriber(getSubscriptionId()) : str;
        } catch (RemoteException e) {
            return "unknown";
        }
    }

    public int getSmsCapacityOnIcc() {
        try {
            ISms iSmsService = getISmsService();
            return iSmsService != null ? iSmsService.getSmsCapacityOnIccForSubscriber(getSubscriptionId()) : -1;
        } catch (RemoteException e) {
            return -1;
        }
    }

    public String getSmscAddressFromIcc() {
        try {
            ISms iSmsService = getISmsService();
            return iSmsService != null ? iSmsService.getSmscAddressFromIccForSubscriber(getSubscriptionId()) : null;
        } catch (RemoteException e) {
            return null;
        }
    }

    public int getSubscriptionId() {
        int defaultSmsSubscriptionId = this.mSubId == DEFAULT_SUBSCRIPTION_ID ? getDefaultSmsSubscriptionId() : this.mSubId;
        boolean z = false;
        Context applicationContext = ActivityThread.currentApplication().getApplicationContext();
        try {
            ISms iSmsService = getISmsService();
            if (iSmsService != null) {
                z = iSmsService.isSmsSimPickActivityNeeded(defaultSmsSubscriptionId);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Exception in getSubscriptionId");
        }
        if (z) {
            Log.d(TAG, "getSubscriptionId isSmsSimPickActivityNeeded is true");
            Intent intent = new Intent();
            intent.setClassName("com.android.settings", "com.android.settings.sim.SimDialogActivity");
            intent.addFlags(268435456);
            intent.putExtra(DIALOG_TYPE_KEY, 2);
            try {
                applicationContext.startActivity(intent);
            } catch (ActivityNotFoundException e2) {
                Log.e(TAG, "Unable to launch Settings application.");
            }
        }
        return defaultSmsSubscriptionId;
    }

    public Uri importMultimediaMessage(Uri uri, String str, long j, boolean z, boolean z2) {
        if (uri == null) {
            throw new IllegalArgumentException("Uri contentUri null");
        }
        try {
            IMms asInterface = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (asInterface != null) {
                return asInterface.importMultimediaMessage(ActivityThread.currentPackageName(), uri, str, j, z, z2);
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    public Uri importTextMessage(String str, int i, String str2, long j, boolean z, boolean z2) {
        try {
            IMms asInterface = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (asInterface != null) {
                return asInterface.importTextMessage(ActivityThread.currentPackageName(), str, i, str2, j, z, z2);
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    public void injectSmsPdu(byte[] bArr, String str, PendingIntent pendingIntent) {
        if (str.equals(SmsMessage.FORMAT_3GPP) || str.equals(SmsMessage.FORMAT_3GPP2)) {
            try {
                ISms asInterface = Stub.asInterface(ServiceManager.getService("isms"));
                if (asInterface != null) {
                    asInterface.injectSmsPdu(bArr, str, pendingIntent);
                    return;
                }
                return;
            } catch (RemoteException e) {
                return;
            }
        }
        throw new IllegalArgumentException("Invalid pdu format. format must be either 3gpp or 3gpp2");
    }

    public boolean isImsSmsSupported() {
        try {
            ISms iSmsService = getISmsService();
            return iSmsService != null ? iSmsService.isImsSmsSupportedForSubscriber(getSubscriptionId()) : false;
        } catch (RemoteException e) {
            return false;
        }
    }

    public void sendDataMessage(String str, String str2, short s, short s2, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        if (SeempJavaFilter.check("android.telephony.SmsManager", "sendDataMessage").booleanValue()) {
            SeempLog.record("android.telephony.SmsManager|sendDataMessage|destinationAddress," + (str == null ? "null" : str) + " " + "scAddress," + (str2 == null ? "null" : str2) + "|--end");
        }
        if (TextUtils.isEmpty(str)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (bArr == null || bArr.length == 0) {
            throw new IllegalArgumentException("Invalid message data");
        } else {
            try {
                getISmsServiceOrThrow().sendDataWithOrigPortUsingSubscriber(getSubscriptionId(), ActivityThread.currentPackageName(), str, str2, s & 65535, s2 & 65535, bArr, pendingIntent, pendingIntent2);
            } catch (RemoteException e) {
            }
        }
    }

    public void sendDataMessage(String str, String str2, short s, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        if (SeempJavaFilter.check("android.telephony.SmsManager", "sendDataMessage").booleanValue()) {
            SeempLog.record("android.telephony.SmsManager|sendDataMessage|destinationAddress," + (str == null ? "null" : str) + " " + "scAddress," + (str2 == null ? "null" : str2) + "|--end");
        }
        if (TextUtils.isEmpty(str)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (bArr == null || bArr.length == 0) {
            throw new IllegalArgumentException("Invalid message data");
        } else {
            try {
                getISmsServiceOrThrow().sendDataForSubscriber(getSubscriptionId(), ActivityThread.currentPackageName(), str, str2, s & 65535, bArr, pendingIntent, pendingIntent2);
            } catch (RemoteException e) {
            }
        }
    }

    public void sendMultimediaMessage(Context context, Uri uri, String str, Bundle bundle, PendingIntent pendingIntent) {
        if (uri == null) {
            throw new IllegalArgumentException("Uri contentUri null");
        }
        try {
            IMms asInterface = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (asInterface != null) {
                asInterface.sendMessage(getSubscriptionId(), ActivityThread.currentPackageName(), uri, str, bundle, pendingIntent);
            }
        } catch (RemoteException e) {
        }
    }

    public void sendMultipartTextMessage(String str, String str2, ArrayList<String> arrayList, ArrayList<PendingIntent> arrayList2, ArrayList<PendingIntent> arrayList3) {
        PendingIntent pendingIntent = null;
        if (SeempJavaFilter.check("android.telephony.SmsManager", "sendMultipartTextMessage").booleanValue()) {
            SeempLog.record("android.telephony.SmsManager|sendMultipartTextMessage|destinationAddress," + (str == null ? "null" : str) + " " + "scAddress," + (str2 == null ? "null" : str2) + "|--end");
        }
        if (TextUtils.isEmpty(str)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (arrayList == null || arrayList.size() < 1) {
            throw new IllegalArgumentException("Invalid message body");
        } else if (arrayList.size() > 1) {
            try {
                getISmsServiceOrThrow().sendMultipartTextForSubscriber(getSubscriptionId(), ActivityThread.currentPackageName(), str, str2, arrayList, arrayList2, arrayList3);
            } catch (RemoteException e) {
            }
        } else {
            PendingIntent pendingIntent2 = (arrayList2 == null || arrayList2.size() <= 0) ? null : (PendingIntent) arrayList2.get(0);
            if (arrayList3 != null && arrayList3.size() > 0) {
                pendingIntent = (PendingIntent) arrayList3.get(0);
            }
            sendTextMessage(str, str2, (String) arrayList.get(0), pendingIntent2, pendingIntent);
        }
    }

    public void sendMultipartTextMessage(String str, String str2, ArrayList<String> arrayList, ArrayList<PendingIntent> arrayList2, ArrayList<PendingIntent> arrayList3, int i, boolean z, int i2) {
        if (SeempJavaFilter.check("android.telephony.SmsManager", "sendMultipartTextMessage").booleanValue()) {
            SeempLog.record("android.telephony.SmsManager|sendMultipartTextMessage|destinationAddress," + (str == null ? "null" : str) + " " + "scAddress," + (str2 == null ? "null" : str2) + "|--end");
        }
        if (TextUtils.isEmpty(str)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (arrayList == null || arrayList.size() < 1) {
            throw new IllegalArgumentException("Invalid message body");
        } else if (arrayList.size() > 1) {
            try {
                ISms iSmsServiceOrThrow = getISmsServiceOrThrow();
                if (iSmsServiceOrThrow != null) {
                    iSmsServiceOrThrow.sendMultipartTextWithOptionsUsingSubscriber(getSubscriptionId(), ActivityThread.currentPackageName(), str, str2, arrayList, arrayList2, arrayList3, i, z, i2);
                }
            } catch (RemoteException e) {
            }
        } else {
            PendingIntent pendingIntent = null;
            PendingIntent pendingIntent2 = null;
            if (arrayList2 != null && arrayList2.size() > 0) {
                pendingIntent = (PendingIntent) arrayList2.get(0);
            }
            if (arrayList3 != null && arrayList3.size() > 0) {
                pendingIntent2 = (PendingIntent) arrayList3.get(0);
            }
            sendTextMessage(str, str2, (String) arrayList.get(0), pendingIntent, pendingIntent2, i, z, i2);
        }
    }

    public void sendStoredMultimediaMessage(Uri uri, Bundle bundle, PendingIntent pendingIntent) {
        if (uri == null) {
            throw new IllegalArgumentException("Empty message URI");
        }
        try {
            IMms asInterface = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (asInterface != null) {
                asInterface.sendStoredMessage(getSubscriptionId(), ActivityThread.currentPackageName(), uri, bundle, pendingIntent);
            }
        } catch (RemoteException e) {
        }
    }

    public void sendStoredMultipartTextMessage(Uri uri, String str, ArrayList<PendingIntent> arrayList, ArrayList<PendingIntent> arrayList2) {
        if (uri == null) {
            throw new IllegalArgumentException("Empty message URI");
        }
        try {
            getISmsServiceOrThrow().sendStoredMultipartText(getSubscriptionId(), ActivityThread.currentPackageName(), uri, str, arrayList, arrayList2);
        } catch (RemoteException e) {
        }
    }

    public void sendStoredTextMessage(Uri uri, String str, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        if (uri == null) {
            throw new IllegalArgumentException("Empty message URI");
        }
        try {
            getISmsServiceOrThrow().sendStoredText(getSubscriptionId(), ActivityThread.currentPackageName(), uri, str, pendingIntent, pendingIntent2);
        } catch (RemoteException e) {
        }
    }

    public void sendTextMessage(String str, String str2, String str3, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        if (SeempJavaFilter.check("android.telephony.gsm.SmsManager", "sendTextMessage").booleanValue()) {
            SeempLog.record("android.telephony.gsm.SmsManager|sendTextMessage|destinationAddress," + (str == null ? "null" : str) + " " + "scAddress," + (str2 == null ? "null" : str2) + " " + "text," + "null" + "|--end");
        }
        if (TextUtils.isEmpty(str)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (TextUtils.isEmpty(str3)) {
            throw new IllegalArgumentException("Invalid message body");
        } else {
            try {
                getISmsServiceOrThrow().sendTextForSubscriber(getSubscriptionId(), ActivityThread.currentPackageName(), str, str2, str3, pendingIntent, pendingIntent2);
            } catch (RemoteException e) {
            }
        }
    }

    public void sendTextMessage(String str, String str2, String str3, PendingIntent pendingIntent, PendingIntent pendingIntent2, int i, boolean z, int i2) {
        if (SeempJavaFilter.check("android.telephony.gsm.SmsManager", "sendTextMessage").booleanValue()) {
            SeempLog.record("android.telephony.gsm.SmsManager|sendTextMessage|destinationAddress," + (str == null ? "null" : str) + " " + "scAddress," + (str2 == null ? "null" : str2) + " " + "text," + "null" + "|--end");
        }
        if (TextUtils.isEmpty(str)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (TextUtils.isEmpty(str3)) {
            throw new IllegalArgumentException("Invalid message body");
        } else {
            try {
                getISmsServiceOrThrow().sendTextWithOptionsUsingSubscriber(getSubscriptionId(), ActivityThread.currentPackageName(), str, str2, str3, pendingIntent, pendingIntent2, i, z, i2);
            } catch (RemoteException e) {
            }
        }
    }

    public void setAutoPersisting(boolean z) {
        try {
            IMms asInterface = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (asInterface != null) {
                asInterface.setAutoPersisting(ActivityThread.currentPackageName(), z);
            }
        } catch (RemoteException e) {
        }
    }

    public boolean setSmscAddressToIcc(String str) {
        try {
            ISms iSmsService = getISmsService();
            return iSmsService != null ? iSmsService.setSmscAddressToIccForSubscriber(getSubscriptionId(), str) : false;
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean updateMessageOnIcc(int i, int i2, byte[] bArr) {
        if (SeempJavaFilter.check("android.telephony.SmsManager", "updateMessageOnIcc").booleanValue()) {
            SeempLog.record("android.telephony.SmsManager|updateMessageOnIcc|--end");
        }
        try {
            ISms iSmsService = getISmsService();
            return iSmsService != null ? iSmsService.updateMessageOnIccEfForSubscriber(getSubscriptionId(), ActivityThread.currentPackageName(), i, i2, bArr) : false;
        } catch (RemoteException e) {
            return false;
        }
    }

    public void updateMmsDownloadStatus(Context context, int i, int i2, Uri uri) {
    }

    public void updateMmsSendStatus(Context context, int i, byte[] bArr, int i2, Uri uri) {
    }

    public void updateSmsSendStatus(int i, boolean z) {
    }

    public boolean updateStoredMessageStatus(Uri uri, ContentValues contentValues) {
        if (uri == null) {
            throw new IllegalArgumentException("Empty message URI");
        }
        try {
            IMms asInterface = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (asInterface != null) {
                return asInterface.updateStoredMessageStatus(ActivityThread.currentPackageName(), uri, contentValues);
            }
        } catch (RemoteException e) {
        }
        return false;
    }
}
