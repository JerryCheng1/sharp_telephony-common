package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Telephony;
import android.telephony.Rlog;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.IWapPushManager;
import com.android.internal.telephony.uicc.IccUtils;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.DeliveryInd;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.PduParser;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.ReadOrigInd;
import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class WapPushOverSms implements ServiceConnection {
    private static final boolean DBG = true;
    private static final String EMERGENCY_POWER_SAVING_CLASSNAME = "com.nttdocomo.android.epsmodecontrol.FotaDialogActivity";
    private static final String EMERGENCY_POWER_SAVING_PACKAGENAME = "com.nttdocomo.android.epsmodecontrol";
    private static final String LOCATION_SELECTION = "m_type=? AND ct_l =?";
    private static final String TAG = "WAP PUSH";
    private static final String THREAD_ID_SELECTION = "m_id=? AND m_type=?";
    private static final String[][] mEpsmodeNotificationList = {new String[]{"7", WspTypeDecoder.CONTENT_TYPE_B_PUSH_SYNCML_NOTI, "com.nttdocomo.android.osv", "com.nttdocomo.android.fota.SMSService"}};
    private final Context mContext;
    private String mDeleteWhere;
    private String[] mDeleteWhereArgs;
    private volatile IWapPushManager mWapPushManager;

    public void setDeleteWhere(String deleteWhere) {
        this.mDeleteWhere = deleteWhere;
    }

    public void setDeleteWhereArgs(String[] deleteWhereArgs) {
        this.mDeleteWhereArgs = deleteWhereArgs;
    }

    @Override // android.content.ServiceConnection
    public void onServiceConnected(ComponentName name, IBinder service) {
        this.mWapPushManager = IWapPushManager.Stub.asInterface(service);
        Rlog.v(TAG, "wappush manager connected to " + hashCode());
    }

    @Override // android.content.ServiceConnection
    public void onServiceDisconnected(ComponentName name) {
        this.mWapPushManager = null;
        Rlog.v(TAG, "wappush manager disconnected.");
    }

    public WapPushOverSms(Context context) {
        this.mContext = context;
        Intent intent = new Intent(IWapPushManager.class.getName());
        ComponentName comp = intent.resolveSystemService(context.getPackageManager(), 0);
        intent.setComponent(comp);
        if (comp == null || !context.bindService(intent, this, 1)) {
            Rlog.e(TAG, "bindService() for wappush manager failed");
        } else {
            Rlog.v(TAG, "bindService() for wappush manager succeeded");
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void dispose() {
        if (this.mWapPushManager != null) {
            Rlog.v(TAG, "dispose: unbind wappush manager");
            this.mContext.unbindService(this);
            return;
        }
        Rlog.e(TAG, "dispose: not bound to a wappush manager");
    }

    public int dispatchWapPdu(byte[] pdu, BroadcastReceiver receiver, InboundSmsHandler handler, String address) {
        byte[] intentData;
        String permission;
        int appOp;
        Rlog.d(TAG, "Rx: " + IccUtils.bytesToHexString(pdu));
        int index = 0 + 1;
        try {
            int transactionId = pdu[0] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
            int index2 = index + 1;
            try {
                int pduType = pdu[index] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
                int phoneId = handler.getPhone().getPhoneId();
                if (!(pduType == 6 || pduType == 7)) {
                    int index3 = this.mContext.getResources().getInteger(17694878);
                    if (index3 != -1) {
                        index = index3 + 1;
                        transactionId = pdu[index3] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
                        index2 = index + 1;
                        pduType = pdu[index] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
                        Rlog.d(TAG, "index = " + index2 + " PDU Type = " + pduType + " transactionID = " + transactionId);
                        if (!(pduType == 6 || pduType == 7)) {
                            Rlog.w(TAG, "Received non-PUSH WAP PDU. Type = " + pduType);
                            return 1;
                        }
                    } else {
                        Rlog.w(TAG, "Received non-PUSH WAP PDU. Type = " + pduType);
                        return 1;
                    }
                }
                WspTypeDecoder pduDecoder = new WspTypeDecoder(pdu);
                if (!pduDecoder.decodeUintvarInteger(index2)) {
                    Rlog.w(TAG, "Received PDU. Header Length error.");
                    return 2;
                }
                int headerLength = (int) pduDecoder.getValue32();
                int index4 = index2 + pduDecoder.getDecodedDataLength();
                if (!pduDecoder.decodeContentType(index4)) {
                    Rlog.w(TAG, "Received PDU. Header Content-Type error.");
                    return 2;
                }
                String mimeType = pduDecoder.getValueString();
                long binaryContentType = pduDecoder.getValue32();
                int index5 = index4 + pduDecoder.getDecodedDataLength();
                byte[] header = new byte[headerLength];
                System.arraycopy(pdu, index4, header, 0, header.length);
                if (mimeType == null || !mimeType.equals(WspTypeDecoder.CONTENT_TYPE_B_PUSH_CO)) {
                    int dataIndex = index4 + headerLength;
                    intentData = new byte[pdu.length - dataIndex];
                    System.arraycopy(pdu, dataIndex, intentData, 0, intentData.length);
                } else {
                    intentData = pdu;
                }
                if (SmsManager.getDefault().getAutoPersisting()) {
                    int[] subIds = SubscriptionManager.getSubId(phoneId);
                    writeInboxMessage((subIds == null || subIds.length <= 0) ? SmsManager.getDefaultSmsSubscriptionId() : subIds[0], intentData);
                }
                String wapAppId = null;
                if (pduDecoder.seekXWapApplicationId(index5, (index5 + headerLength) - 1)) {
                    pduDecoder.decodeXWapApplicationId((int) pduDecoder.getValue32());
                    wapAppId = pduDecoder.getValueString();
                    if (wapAppId == null) {
                        wapAppId = Integer.toString((int) pduDecoder.getValue32());
                    }
                    String contentType = mimeType == null ? Long.toString(binaryContentType) : mimeType;
                    Rlog.v(TAG, "appid found: " + wapAppId + ":" + contentType);
                    if (SystemProperties.get("persist.sys.epsmodestate", "off").equals("on")) {
                        for (int i = 0; i < mEpsmodeNotificationList.length; i++) {
                            if (wapAppId.equals(mEpsmodeNotificationList[i][0]) && contentType.equals(mEpsmodeNotificationList[i][1])) {
                                try {
                                    Intent epsintent = new Intent();
                                    epsintent.setClassName(EMERGENCY_POWER_SAVING_PACKAGENAME, EMERGENCY_POWER_SAVING_CLASSNAME);
                                    epsintent.setFlags(268435456);
                                    this.mContext.startActivity(epsintent);
                                } catch (Exception e) {
                                }
                            }
                        }
                    }
                    boolean processFurther = true;
                    try {
                        IWapPushManager wapPushMan = this.mWapPushManager;
                        if (wapPushMan == null) {
                            Rlog.w(TAG, "wap push manager not found!");
                        } else {
                            Intent intent = new Intent();
                            intent.putExtra("transactionId", transactionId);
                            intent.putExtra("pduType", pduType);
                            intent.putExtra("header", header);
                            intent.putExtra("data", intentData);
                            intent.putExtra("contentTypeParameters", pduDecoder.getContentParameters());
                            if (!TextUtils.isEmpty(address)) {
                                intent.putExtra("address", address);
                            }
                            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneId);
                            int procRet = wapPushMan.processMessage(wapAppId, contentType, intent);
                            Rlog.v(TAG, "procRet:" + procRet);
                            if ((procRet & 1) > 0 && (32768 & procRet) == 0) {
                                processFurther = false;
                            }
                        }
                        if (!processFurther) {
                            handler.deleteFromRawTable(this.mDeleteWhere, this.mDeleteWhereArgs);
                            return 1;
                        }
                    } catch (RemoteException e2) {
                        Rlog.w(TAG, "remote func failed...");
                    }
                }
                Rlog.v(TAG, "fall back to existing handler");
                if (mimeType == null) {
                    Rlog.w(TAG, "Header Content-Type error.");
                    return 2;
                }
                if (mimeType.equals("application/vnd.wap.mms-message")) {
                    permission = "android.permission.RECEIVE_MMS";
                    appOp = 18;
                } else {
                    permission = "android.permission.RECEIVE_WAP_PUSH";
                    appOp = 19;
                }
                Intent intent2 = new Intent(Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION);
                intent2.setType(mimeType);
                intent2.putExtra("transactionId", transactionId);
                intent2.putExtra("pduType", pduType);
                intent2.putExtra("header", header);
                intent2.putExtra("data", intentData);
                intent2.putExtra("contentTypeParameters", pduDecoder.getContentParameters());
                if (!TextUtils.isEmpty(address)) {
                    intent2.putExtra("address", address);
                }
                if (!TextUtils.isEmpty(wapAppId)) {
                    try {
                        intent2.putExtra("applicationId", Long.valueOf(wapAppId).longValue());
                    } catch (NumberFormatException e3) {
                    }
                }
                SubscriptionManager.putPhoneIdAndSubIdExtra(intent2, phoneId);
                ComponentName componentName = SmsApplication.getDefaultMmsApplication(this.mContext, true);
                if (componentName != null) {
                    intent2.setComponent(componentName);
                    Rlog.v(TAG, "Delivering MMS to: " + componentName.getPackageName() + " " + componentName.getClassName());
                }
                handler.dispatchIntent(intent2, permission, appOp, receiver, UserHandle.OWNER);
                return -1;
            } catch (ArrayIndexOutOfBoundsException e4) {
                aie = e4;
                Rlog.e(TAG, "ignoring dispatchWapPdu() array index exception: " + aie);
                return 2;
            }
        } catch (ArrayIndexOutOfBoundsException e5) {
            aie = e5;
        }
    }

    private static boolean shouldParseContentDisposition(int subId) {
        return SmsManager.getSmsManagerForSubscriptionId(subId).getCarrierConfigValues().getBoolean(SmsManager.MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION, true);
    }

    private void writeInboxMessage(int subId, byte[] pushData) {
        GenericPdu pdu = new PduParser(pushData, shouldParseContentDisposition(subId)).parse();
        if (pdu == null) {
            Rlog.e(TAG, "Invalid PUSH PDU");
        }
        PduPersister persister = PduPersister.getPduPersister(this.mContext);
        int type = pdu.getMessageType();
        try {
            switch (type) {
                case 130:
                    NotificationInd nInd = (NotificationInd) pdu;
                    Bundle configs = SmsManager.getSmsManagerForSubscriptionId(subId).getCarrierConfigValues();
                    if (configs != null && configs.getBoolean(SmsManager.MMS_CONFIG_APPEND_TRANSACTION_ID, false)) {
                        byte[] contentLocation = nInd.getContentLocation();
                        if (61 == contentLocation[contentLocation.length - 1]) {
                            byte[] transactionId = nInd.getTransactionId();
                            byte[] contentLocationWithId = new byte[contentLocation.length + transactionId.length];
                            System.arraycopy(contentLocation, 0, contentLocationWithId, 0, contentLocation.length);
                            System.arraycopy(transactionId, 0, contentLocationWithId, contentLocation.length, transactionId.length);
                            nInd.setContentLocation(contentLocationWithId);
                        }
                    }
                    if (isDuplicateNotification(this.mContext, nInd)) {
                        Rlog.d(TAG, "Skip storing duplicate MMS WAP push notification ind: " + new String(nInd.getContentLocation()));
                        return;
                    } else if (persister.persist(pdu, Telephony.Mms.Inbox.CONTENT_URI, true, true, null) == null) {
                        Rlog.e(TAG, "Failed to save MMS WAP push notification ind");
                        return;
                    } else {
                        return;
                    }
                case 134:
                case 136:
                    long threadId = getDeliveryOrReadReportThreadId(this.mContext, pdu);
                    if (threadId == -1) {
                        Rlog.e(TAG, "Failed to find delivery or read report's thread id");
                        return;
                    }
                    Uri uri = persister.persist(pdu, Telephony.Mms.Inbox.CONTENT_URI, true, true, null);
                    if (uri == null) {
                        Rlog.e(TAG, "Failed to persist delivery or read report");
                        return;
                    }
                    ContentValues values = new ContentValues(1);
                    values.put("thread_id", Long.valueOf(threadId));
                    if (SqliteWrapper.update(this.mContext, this.mContext.getContentResolver(), uri, values, (String) null, (String[]) null) != 1) {
                        Rlog.e(TAG, "Failed to update delivery or read report thread id");
                        return;
                    }
                    return;
                default:
                    Log.e(TAG, "Received unrecognized WAP Push PDU.");
                    return;
            }
        } catch (MmsException e) {
            Log.e(TAG, "Failed to save MMS WAP push data: type=" + type, e);
        } catch (RuntimeException e2) {
            Log.e(TAG, "Unexpected RuntimeException in persisting MMS WAP push data", e2);
        }
    }

    private static long getDeliveryOrReadReportThreadId(Context context, GenericPdu pdu) {
        String messageId;
        if (pdu instanceof DeliveryInd) {
            messageId = new String(((DeliveryInd) pdu).getMessageId());
        } else if (pdu instanceof ReadOrigInd) {
            messageId = new String(((ReadOrigInd) pdu).getMessageId());
        } else {
            Rlog.e(TAG, "WAP Push data is neither delivery or read report type: " + pdu.getClass().getCanonicalName());
            return -1L;
        }
        Cursor cursor = null;
        try {
            try {
                cursor = SqliteWrapper.query(context, context.getContentResolver(), Telephony.Mms.CONTENT_URI, new String[]{"thread_id"}, THREAD_ID_SELECTION, new String[]{DatabaseUtils.sqlEscapeString(messageId), Integer.toString(128)}, (String) null);
            } catch (SQLiteException e) {
                Rlog.e(TAG, "Failed to query delivery or read report thread id", e);
                if (cursor != null) {
                    cursor.close();
                }
            }
            if (cursor == null || !cursor.moveToFirst()) {
                if (cursor != null) {
                    cursor.close();
                }
                return -1L;
            }
            long j = cursor.getLong(0);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static boolean isDuplicateNotification(Context context, NotificationInd nInd) {
        Cursor cursor;
        byte[] rawLocation = nInd.getContentLocation();
        if (rawLocation != null) {
            try {
                String[] strArr = {new String(rawLocation)};
                cursor = null;
                try {
                    cursor = SqliteWrapper.query(context, context.getContentResolver(), Telephony.Mms.CONTENT_URI, new String[]{"_id"}, LOCATION_SELECTION, new String[]{Integer.toString(130), new String(rawLocation)}, (String) null);
                    if (cursor != null) {
                        if (cursor.getCount() > 0) {
                            return true;
                        }
                    }
                    if (cursor != null) {
                        cursor.close();
                    }
                } catch (SQLiteException e) {
                    Rlog.e(TAG, "failed to query existing notification ind", e);
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return false;
    }
}
