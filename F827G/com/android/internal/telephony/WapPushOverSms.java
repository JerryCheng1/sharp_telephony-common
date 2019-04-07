package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Telephony.Mms.Inbox;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Rlog;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.IWapPushManager.Stub;
import com.android.internal.telephony.uicc.IccUtils;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.PduParser;
import com.google.android.mms.pdu.PduPersister;

public class WapPushOverSms implements ServiceConnection {
    private static final boolean DBG = true;
    private static final String EMERGENCY_POWER_SAVING_CLASSNAME = "com.nttdocomo.android.epsmodecontrol.FotaDialogActivity";
    private static final String EMERGENCY_POWER_SAVING_PACKAGENAME = "com.nttdocomo.android.epsmodecontrol";
    private static final String LOCATION_SELECTION = "m_type=? AND ct_l =?";
    private static final String TAG = "WAP PUSH";
    private static final String THREAD_ID_SELECTION = "m_id=? AND m_type=?";
    private static final String[][] mEpsmodeNotificationList;
    private final Context mContext;
    private String mDeleteWhere;
    private String[] mDeleteWhereArgs;
    private volatile IWapPushManager mWapPushManager;

    static {
        String[][] strArr = new String[1][];
        strArr[0] = new String[]{"7", WspTypeDecoder.CONTENT_TYPE_B_PUSH_SYNCML_NOTI, "com.nttdocomo.android.osv", "com.nttdocomo.android.fota.SMSService"};
        mEpsmodeNotificationList = strArr;
    }

    public WapPushOverSms(Context context) {
        this.mContext = context;
        Intent intent = new Intent(IWapPushManager.class.getName());
        ComponentName resolveSystemService = intent.resolveSystemService(context.getPackageManager(), 0);
        intent.setComponent(resolveSystemService);
        if (resolveSystemService == null || !context.bindService(intent, this, 1)) {
            Rlog.e(TAG, "bindService() for wappush manager failed");
        } else {
            Rlog.v(TAG, "bindService() for wappush manager succeeded");
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:27:0x0099  */
    /* JADX WARNING: Removed duplicated region for block: B:27:0x0099  */
    private static long getDeliveryOrReadReportThreadId(android.content.Context r11, com.google.android.mms.pdu.GenericPdu r12) {
        /*
        r8 = -1;
        r7 = 0;
        r0 = r12 instanceof com.google.android.mms.pdu.DeliveryInd;
        if (r0 == 0) goto L_0x004e;
    L_0x0007:
        r0 = new java.lang.String;
        r12 = (com.google.android.mms.pdu.DeliveryInd) r12;
        r1 = r12.getMessageId();
        r0.<init>(r1);
    L_0x0012:
        r1 = r11.getContentResolver();	 Catch:{ SQLiteException -> 0x0087, all -> 0x0096 }
        r2 = android.provider.Telephony.Mms.CONTENT_URI;	 Catch:{ SQLiteException -> 0x0087, all -> 0x0096 }
        r0 = android.database.DatabaseUtils.sqlEscapeString(r0);	 Catch:{ SQLiteException -> 0x0087, all -> 0x0096 }
        r3 = 128; // 0x80 float:1.794E-43 double:6.32E-322;
        r6 = java.lang.Integer.toString(r3);	 Catch:{ SQLiteException -> 0x0087, all -> 0x0096 }
        r3 = 1;
        r3 = new java.lang.String[r3];	 Catch:{ SQLiteException -> 0x0087, all -> 0x0096 }
        r4 = 0;
        r5 = "thread_id";
        r3[r4] = r5;	 Catch:{ SQLiteException -> 0x0087, all -> 0x0096 }
        r4 = "m_id=? AND m_type=?";
        r5 = 2;
        r5 = new java.lang.String[r5];	 Catch:{ SQLiteException -> 0x0087, all -> 0x0096 }
        r10 = 0;
        r5[r10] = r0;	 Catch:{ SQLiteException -> 0x0087, all -> 0x0096 }
        r0 = 1;
        r5[r0] = r6;	 Catch:{ SQLiteException -> 0x0087, all -> 0x0096 }
        r6 = 0;
        r0 = r11;
        r2 = android.database.sqlite.SqliteWrapper.query(r0, r1, r2, r3, r4, r5, r6);	 Catch:{ SQLiteException -> 0x0087, all -> 0x0096 }
        if (r2 == 0) goto L_0x0080;
    L_0x003d:
        r0 = r2.moveToFirst();	 Catch:{ SQLiteException -> 0x00a3, all -> 0x009d }
        if (r0 == 0) goto L_0x0080;
    L_0x0043:
        r0 = 0;
        r0 = r2.getLong(r0);	 Catch:{ SQLiteException -> 0x00a3, all -> 0x009d }
        if (r2 == 0) goto L_0x004d;
    L_0x004a:
        r2.close();
    L_0x004d:
        return r0;
    L_0x004e:
        r0 = r12 instanceof com.google.android.mms.pdu.ReadOrigInd;
        if (r0 == 0) goto L_0x005e;
    L_0x0052:
        r0 = new java.lang.String;
        r12 = (com.google.android.mms.pdu.ReadOrigInd) r12;
        r1 = r12.getMessageId();
        r0.<init>(r1);
        goto L_0x0012;
    L_0x005e:
        r0 = "WAP PUSH";
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "WAP Push data is neither delivery or read report type: ";
        r1 = r1.append(r2);
        r2 = r12.getClass();
        r2 = r2.getCanonicalName();
        r1 = r1.append(r2);
        r1 = r1.toString();
        android.telephony.Rlog.e(r0, r1);
        r0 = r8;
        goto L_0x004d;
    L_0x0080:
        if (r2 == 0) goto L_0x0085;
    L_0x0082:
        r2.close();
    L_0x0085:
        r0 = r8;
        goto L_0x004d;
    L_0x0087:
        r0 = move-exception;
        r1 = r7;
    L_0x0089:
        r2 = "WAP PUSH";
        r3 = "Failed to query delivery or read report thread id";
        android.telephony.Rlog.e(r2, r3, r0);	 Catch:{ all -> 0x00a0 }
        if (r1 == 0) goto L_0x0085;
    L_0x0092:
        r1.close();
        goto L_0x0085;
    L_0x0096:
        r0 = move-exception;
    L_0x0097:
        if (r7 == 0) goto L_0x009c;
    L_0x0099:
        r7.close();
    L_0x009c:
        throw r0;
    L_0x009d:
        r0 = move-exception;
        r7 = r2;
        goto L_0x0097;
    L_0x00a0:
        r0 = move-exception;
        r7 = r1;
        goto L_0x0097;
    L_0x00a3:
        r0 = move-exception;
        r1 = r2;
        goto L_0x0089;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.WapPushOverSms.getDeliveryOrReadReportThreadId(android.content.Context, com.google.android.mms.pdu.GenericPdu):long");
    }

    /* JADX WARNING: Removed duplicated region for block: B:23:0x0060  */
    private static boolean isDuplicateNotification(android.content.Context r11, com.google.android.mms.pdu.NotificationInd r12) {
        /*
        r9 = 0;
        r7 = 1;
        r8 = 0;
        r0 = r12.getContentLocation();
        if (r0 == 0) goto L_0x004c;
    L_0x0009:
        r1 = new java.lang.String;
        r1.<init>(r0);
        r1 = r11.getContentResolver();	 Catch:{ SQLiteException -> 0x004e, all -> 0x005d }
        r2 = android.provider.Telephony.Mms.CONTENT_URI;	 Catch:{ SQLiteException -> 0x004e, all -> 0x005d }
        r3 = 130; // 0x82 float:1.82E-43 double:6.4E-322;
        r6 = java.lang.Integer.toString(r3);	 Catch:{ SQLiteException -> 0x004e, all -> 0x005d }
        r10 = new java.lang.String;	 Catch:{ SQLiteException -> 0x004e, all -> 0x005d }
        r10.<init>(r0);	 Catch:{ SQLiteException -> 0x004e, all -> 0x005d }
        r0 = 1;
        r3 = new java.lang.String[r0];	 Catch:{ SQLiteException -> 0x004e, all -> 0x005d }
        r0 = 0;
        r4 = "_id";
        r3[r0] = r4;	 Catch:{ SQLiteException -> 0x004e, all -> 0x005d }
        r4 = "m_type=? AND ct_l =?";
        r0 = 2;
        r5 = new java.lang.String[r0];	 Catch:{ SQLiteException -> 0x004e, all -> 0x005d }
        r0 = 0;
        r5[r0] = r6;	 Catch:{ SQLiteException -> 0x004e, all -> 0x005d }
        r0 = 1;
        r5[r0] = r10;	 Catch:{ SQLiteException -> 0x004e, all -> 0x005d }
        r6 = 0;
        r0 = r11;
        r1 = android.database.sqlite.SqliteWrapper.query(r0, r1, r2, r3, r4, r5, r6);	 Catch:{ SQLiteException -> 0x004e, all -> 0x005d }
        if (r1 == 0) goto L_0x0047;
    L_0x003a:
        r0 = r1.getCount();	 Catch:{ SQLiteException -> 0x0067 }
        if (r0 <= 0) goto L_0x0047;
    L_0x0040:
        if (r1 == 0) goto L_0x0045;
    L_0x0042:
        r1.close();
    L_0x0045:
        r0 = r7;
    L_0x0046:
        return r0;
    L_0x0047:
        if (r1 == 0) goto L_0x004c;
    L_0x0049:
        r1.close();
    L_0x004c:
        r0 = r8;
        goto L_0x0046;
    L_0x004e:
        r0 = move-exception;
        r1 = r9;
    L_0x0050:
        r2 = "WAP PUSH";
        r3 = "failed to query existing notification ind";
        android.telephony.Rlog.e(r2, r3, r0);	 Catch:{ all -> 0x0064 }
        if (r1 == 0) goto L_0x004c;
    L_0x0059:
        r1.close();
        goto L_0x004c;
    L_0x005d:
        r0 = move-exception;
    L_0x005e:
        if (r9 == 0) goto L_0x0063;
    L_0x0060:
        r9.close();
    L_0x0063:
        throw r0;
    L_0x0064:
        r0 = move-exception;
        r9 = r1;
        goto L_0x005e;
    L_0x0067:
        r0 = move-exception;
        goto L_0x0050;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.WapPushOverSms.isDuplicateNotification(android.content.Context, com.google.android.mms.pdu.NotificationInd):boolean");
    }

    private static boolean shouldParseContentDisposition(int i) {
        return SmsManager.getSmsManagerForSubscriptionId(i).getCarrierConfigValues().getBoolean(SmsManager.MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION, true);
    }

    private void writeInboxMessage(int i, byte[] bArr) {
        GenericPdu parse = new PduParser(bArr, shouldParseContentDisposition(i)).parse();
        if (parse == null) {
            Rlog.e(TAG, "Invalid PUSH PDU");
        }
        PduPersister pduPersister = PduPersister.getPduPersister(this.mContext);
        int messageType = parse.getMessageType();
        switch (messageType) {
            case 130:
                NotificationInd notificationInd = (NotificationInd) parse;
                Bundle carrierConfigValues = SmsManager.getSmsManagerForSubscriptionId(i).getCarrierConfigValues();
                if (carrierConfigValues != null && carrierConfigValues.getBoolean(SmsManager.MMS_CONFIG_APPEND_TRANSACTION_ID, false)) {
                    byte[] contentLocation = notificationInd.getContentLocation();
                    if ((byte) 61 == contentLocation[contentLocation.length - 1]) {
                        byte[] transactionId = notificationInd.getTransactionId();
                        byte[] bArr2 = new byte[(contentLocation.length + transactionId.length)];
                        System.arraycopy(contentLocation, 0, bArr2, 0, contentLocation.length);
                        System.arraycopy(transactionId, 0, bArr2, contentLocation.length, transactionId.length);
                        notificationInd.setContentLocation(bArr2);
                    }
                }
                if (isDuplicateNotification(this.mContext, notificationInd)) {
                    Rlog.d(TAG, "Skip storing duplicate MMS WAP push notification ind: " + new String(notificationInd.getContentLocation()));
                    return;
                } else if (pduPersister.persist(parse, Inbox.CONTENT_URI, true, true, null) == null) {
                    Rlog.e(TAG, "Failed to save MMS WAP push notification ind");
                    return;
                } else {
                    return;
                }
            case 134:
            case 136:
                long deliveryOrReadReportThreadId = getDeliveryOrReadReportThreadId(this.mContext, parse);
                if (deliveryOrReadReportThreadId == -1) {
                    Rlog.e(TAG, "Failed to find delivery or read report's thread id");
                    return;
                }
                Uri persist = pduPersister.persist(parse, Inbox.CONTENT_URI, true, true, null);
                if (persist == null) {
                    Rlog.e(TAG, "Failed to persist delivery or read report");
                    return;
                }
                ContentValues contentValues = new ContentValues(1);
                contentValues.put("thread_id", Long.valueOf(deliveryOrReadReportThreadId));
                if (SqliteWrapper.update(this.mContext, this.mContext.getContentResolver(), persist, contentValues, null, null) != 1) {
                    Rlog.e(TAG, "Failed to update delivery or read report thread id");
                    return;
                }
                return;
            default:
                try {
                    Log.e(TAG, "Received unrecognized WAP Push PDU.");
                    return;
                } catch (MmsException e) {
                    Log.e(TAG, "Failed to save MMS WAP push data: type=" + messageType, e);
                    return;
                } catch (RuntimeException e2) {
                    Log.e(TAG, "Unexpected RuntimeException in persisting MMS WAP push data", e2);
                    return;
                }
        }
    }

    public int dispatchWapPdu(byte[] bArr, BroadcastReceiver broadcastReceiver, InboundSmsHandler inboundSmsHandler, String str) {
        Rlog.d(TAG, "Rx: " + IccUtils.bytesToHexString(bArr));
        int i = bArr[0] & 255;
        int i2 = 2;
        int i3 = bArr[1] & 255;
        int phoneId = inboundSmsHandler.getPhone().getPhoneId();
        if (!(i3 == 6 || i3 == 7)) {
            i2 = this.mContext.getResources().getInteger(17694878);
            if (i2 != -1) {
                i3 = i2 + 1;
                i = bArr[i2] & 255;
                i2 = i3 + 1;
                i3 = bArr[i3] & 255;
                Rlog.d(TAG, "index = " + i2 + " PDU Type = " + i3 + " transactionID = " + i);
                if (!(i3 == 6 || i3 == 7)) {
                    Rlog.w(TAG, "Received non-PUSH WAP PDU. Type = " + i3);
                    return 1;
                }
            }
            Rlog.w(TAG, "Received non-PUSH WAP PDU. Type = " + i3);
            return 1;
        }
        int i4 = i;
        int i5 = i3;
        WspTypeDecoder wspTypeDecoder = new WspTypeDecoder(bArr);
        if (wspTypeDecoder.decodeUintvarInteger(i2)) {
            i = (int) wspTypeDecoder.getValue32();
            i2 += wspTypeDecoder.getDecodedDataLength();
            if (wspTypeDecoder.decodeContentType(i2)) {
                String l;
                String valueString = wspTypeDecoder.getValueString();
                long value32 = wspTypeDecoder.getValue32();
                int decodedDataLength = wspTypeDecoder.getDecodedDataLength() + i2;
                byte[] bArr2 = new byte[i];
                System.arraycopy(bArr, i2, bArr2, 0, bArr2.length);
                if (valueString == null || !valueString.equals(WspTypeDecoder.CONTENT_TYPE_B_PUSH_CO)) {
                    int i6 = i2 + i;
                    byte[] bArr3 = new byte[(bArr.length - i6)];
                    System.arraycopy(bArr, i6, bArr3, 0, bArr3.length);
                    bArr = bArr3;
                }
                if (SmsManager.getDefault().getAutoPersisting()) {
                    int[] subId = SubscriptionManager.getSubId(phoneId);
                    if (subId == null || subId.length <= 0) {
                        try {
                            i2 = SmsManager.getDefaultSmsSubscriptionId();
                        } catch (ArrayIndexOutOfBoundsException e) {
                            Rlog.e(TAG, "ignoring dispatchWapPdu() array index exception: " + e);
                            return 2;
                        }
                    }
                    i2 = subId[0];
                    writeInboxMessage(i2, bArr);
                }
                CharSequence charSequence = null;
                if (wspTypeDecoder.seekXWapApplicationId(decodedDataLength, (i + decodedDataLength) - 1)) {
                    wspTypeDecoder.decodeXWapApplicationId((int) wspTypeDecoder.getValue32());
                    charSequence = wspTypeDecoder.getValueString();
                    if (charSequence == null) {
                        charSequence = Integer.toString((int) wspTypeDecoder.getValue32());
                    }
                    l = valueString == null ? Long.toString(value32) : valueString;
                    Rlog.v(TAG, "appid found: " + charSequence + ":" + l);
                    if (SystemProperties.get("persist.sys.epsmodestate", "off").equals("on")) {
                        i = 0;
                        while (i < mEpsmodeNotificationList.length) {
                            if (charSequence.equals(mEpsmodeNotificationList[i][0]) && l.equals(mEpsmodeNotificationList[i][1])) {
                                try {
                                    Intent intent = new Intent();
                                    intent.setClassName(EMERGENCY_POWER_SAVING_PACKAGENAME, EMERGENCY_POWER_SAVING_CLASSNAME);
                                    intent.setFlags(268435456);
                                    this.mContext.startActivity(intent);
                                } catch (Exception e2) {
                                }
                            }
                            i++;
                        }
                    }
                    Object obj = 1;
                    try {
                        IWapPushManager iWapPushManager = this.mWapPushManager;
                        if (iWapPushManager == null) {
                            Rlog.w(TAG, "wap push manager not found!");
                        } else {
                            Intent intent2 = new Intent();
                            intent2.putExtra("transactionId", i4);
                            intent2.putExtra("pduType", i5);
                            intent2.putExtra("header", bArr2);
                            intent2.putExtra("data", bArr);
                            intent2.putExtra("contentTypeParameters", wspTypeDecoder.getContentParameters());
                            if (!TextUtils.isEmpty(str)) {
                                intent2.putExtra("address", str);
                            }
                            SubscriptionManager.putPhoneIdAndSubIdExtra(intent2, phoneId);
                            i3 = iWapPushManager.processMessage(charSequence, l, intent2);
                            Rlog.v(TAG, "procRet:" + i3);
                            if ((i3 & 1) > 0 && (i3 & WapPushManagerParams.FURTHER_PROCESSING) == 0) {
                                obj = null;
                            }
                        }
                        if (obj == null) {
                            inboundSmsHandler.deleteFromRawTable(this.mDeleteWhere, this.mDeleteWhereArgs);
                            return 1;
                        }
                    } catch (RemoteException e3) {
                        Rlog.w(TAG, "remote func failed...");
                    }
                }
                Rlog.v(TAG, "fall back to existing handler");
                if (valueString == null) {
                    Rlog.w(TAG, "Header Content-Type error.");
                    return 2;
                }
                int i7;
                if (valueString.equals("application/vnd.wap.mms-message")) {
                    l = "android.permission.RECEIVE_MMS";
                    i7 = 18;
                } else {
                    l = "android.permission.RECEIVE_WAP_PUSH";
                    i7 = 19;
                }
                Intent intent3 = new Intent(Intents.WAP_PUSH_DELIVER_ACTION);
                intent3.setType(valueString);
                intent3.putExtra("transactionId", i4);
                intent3.putExtra("pduType", i5);
                intent3.putExtra("header", bArr2);
                intent3.putExtra("data", bArr);
                intent3.putExtra("contentTypeParameters", wspTypeDecoder.getContentParameters());
                if (!TextUtils.isEmpty(str)) {
                    intent3.putExtra("address", str);
                }
                if (!TextUtils.isEmpty(charSequence)) {
                    try {
                        intent3.putExtra("applicationId", Long.valueOf(charSequence).longValue());
                    } catch (NumberFormatException e4) {
                    }
                }
                SubscriptionManager.putPhoneIdAndSubIdExtra(intent3, phoneId);
                ComponentName defaultMmsApplication = SmsApplication.getDefaultMmsApplication(this.mContext, true);
                if (defaultMmsApplication != null) {
                    intent3.setComponent(defaultMmsApplication);
                    Rlog.v(TAG, "Delivering MMS to: " + defaultMmsApplication.getPackageName() + " " + defaultMmsApplication.getClassName());
                }
                inboundSmsHandler.dispatchIntent(intent3, l, i7, broadcastReceiver, UserHandle.OWNER);
                return -1;
            }
            Rlog.w(TAG, "Received PDU. Header Content-Type error.");
            return 2;
        }
        Rlog.w(TAG, "Received PDU. Header Length error.");
        return 2;
    }

    /* Access modifiers changed, original: 0000 */
    public void dispose() {
        if (this.mWapPushManager != null) {
            Rlog.v(TAG, "dispose: unbind wappush manager");
            this.mContext.unbindService(this);
            return;
        }
        Rlog.e(TAG, "dispose: not bound to a wappush manager");
    }

    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        this.mWapPushManager = Stub.asInterface(iBinder);
        Rlog.v(TAG, "wappush manager connected to " + hashCode());
    }

    public void onServiceDisconnected(ComponentName componentName) {
        this.mWapPushManager = null;
        Rlog.v(TAG, "wappush manager disconnected.");
    }

    public void setDeleteWhere(String str) {
        this.mDeleteWhere = str;
    }

    public void setDeleteWhereArgs(String[] strArr) {
        this.mDeleteWhereArgs = strArr;
    }
}
