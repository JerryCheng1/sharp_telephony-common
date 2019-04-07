package com.google.android.mms.pdu;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteException;
import android.drm.DrmManagerClient;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.Telephony.BaseMmsColumns;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Mms.Addr;
import android.provider.Telephony.Mms.Draft;
import android.provider.Telephony.Mms.Inbox;
import android.provider.Telephony.Mms.Outbox;
import android.provider.Telephony.Mms.Part;
import android.provider.Telephony.Mms.Sent;
import android.provider.Telephony.MmsSms.PendingMessages;
import android.provider.Telephony.Threads;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.google.android.mms.ContentType;
import com.google.android.mms.InvalidHeaderValueException;
import com.google.android.mms.MmsException;
import com.google.android.mms.util.PduCache;
import com.google.android.mms.util.PduCacheEntry;
import com.google.android.mms.util.SqliteWrapper;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

public class PduPersister {
    static final /* synthetic */ boolean $assertionsDisabled = (!PduPersister.class.desiredAssertionStatus());
    private static final int[] ADDRESS_FIELDS = new int[]{129, 130, 137, 151};
    private static final HashMap<Integer, Integer> CHARSET_COLUMN_INDEX_MAP = new HashMap();
    private static final HashMap<Integer, String> CHARSET_COLUMN_NAME_MAP = new HashMap();
    private static final boolean DEBUG = false;
    private static final long DUMMY_THREAD_ID = Long.MAX_VALUE;
    private static final HashMap<Integer, Integer> ENCODED_STRING_COLUMN_INDEX_MAP = new HashMap();
    private static final HashMap<Integer, String> ENCODED_STRING_COLUMN_NAME_MAP = new HashMap();
    private static final boolean LOCAL_LOGV = false;
    private static final HashMap<Integer, Integer> LONG_COLUMN_INDEX_MAP = new HashMap();
    private static final HashMap<Integer, String> LONG_COLUMN_NAME_MAP = new HashMap();
    private static final HashMap<Uri, Integer> MESSAGE_BOX_MAP = new HashMap();
    private static final HashMap<Integer, Integer> OCTET_COLUMN_INDEX_MAP = new HashMap();
    private static final HashMap<Integer, String> OCTET_COLUMN_NAME_MAP = new HashMap();
    private static final int PART_COLUMN_CHARSET = 1;
    private static final int PART_COLUMN_CONTENT_DISPOSITION = 2;
    private static final int PART_COLUMN_CONTENT_ID = 3;
    private static final int PART_COLUMN_CONTENT_LOCATION = 4;
    private static final int PART_COLUMN_CONTENT_TYPE = 5;
    private static final int PART_COLUMN_FILENAME = 6;
    private static final int PART_COLUMN_ID = 0;
    private static final int PART_COLUMN_NAME = 7;
    private static final int PART_COLUMN_TEXT = 8;
    private static final String[] PART_PROJECTION = new String[]{"_id", Part.CHARSET, Part.CONTENT_DISPOSITION, "cid", Part.CONTENT_LOCATION, Part.CONTENT_TYPE, Part.FILENAME, "name", Part.TEXT};
    private static final PduCache PDU_CACHE_INSTANCE = PduCache.getInstance();
    private static final int PDU_COLUMN_CONTENT_CLASS = 11;
    private static final int PDU_COLUMN_CONTENT_LOCATION = 5;
    private static final int PDU_COLUMN_CONTENT_TYPE = 6;
    private static final int PDU_COLUMN_DATE = 21;
    private static final int PDU_COLUMN_DELIVERY_REPORT = 12;
    private static final int PDU_COLUMN_DELIVERY_TIME = 22;
    private static final int PDU_COLUMN_EXPIRY = 23;
    private static final int PDU_COLUMN_ID = 0;
    private static final int PDU_COLUMN_MESSAGE_BOX = 1;
    private static final int PDU_COLUMN_MESSAGE_CLASS = 7;
    private static final int PDU_COLUMN_MESSAGE_ID = 8;
    private static final int PDU_COLUMN_MESSAGE_SIZE = 24;
    private static final int PDU_COLUMN_MESSAGE_TYPE = 13;
    private static final int PDU_COLUMN_MMS_VERSION = 14;
    private static final int PDU_COLUMN_PRIORITY = 15;
    private static final int PDU_COLUMN_READ_REPORT = 16;
    private static final int PDU_COLUMN_READ_STATUS = 17;
    private static final int PDU_COLUMN_REPORT_ALLOWED = 18;
    private static final int PDU_COLUMN_RESPONSE_TEXT = 9;
    private static final int PDU_COLUMN_RETRIEVE_STATUS = 19;
    private static final int PDU_COLUMN_RETRIEVE_TEXT = 3;
    private static final int PDU_COLUMN_RETRIEVE_TEXT_CHARSET = 26;
    private static final int PDU_COLUMN_STATUS = 20;
    private static final int PDU_COLUMN_SUBJECT = 4;
    private static final int PDU_COLUMN_SUBJECT_CHARSET = 25;
    private static final int PDU_COLUMN_THREAD_ID = 2;
    private static final int PDU_COLUMN_TRANSACTION_ID = 10;
    private static final String[] PDU_PROJECTION = new String[]{"_id", BaseMmsColumns.MESSAGE_BOX, "thread_id", BaseMmsColumns.RETRIEVE_TEXT, BaseMmsColumns.SUBJECT, BaseMmsColumns.CONTENT_LOCATION, BaseMmsColumns.CONTENT_TYPE, BaseMmsColumns.MESSAGE_CLASS, BaseMmsColumns.MESSAGE_ID, BaseMmsColumns.RESPONSE_TEXT, BaseMmsColumns.TRANSACTION_ID, BaseMmsColumns.CONTENT_CLASS, BaseMmsColumns.DELIVERY_REPORT, BaseMmsColumns.MESSAGE_TYPE, BaseMmsColumns.MMS_VERSION, BaseMmsColumns.PRIORITY, BaseMmsColumns.READ_REPORT, BaseMmsColumns.READ_STATUS, BaseMmsColumns.REPORT_ALLOWED, BaseMmsColumns.RETRIEVE_STATUS, BaseMmsColumns.STATUS, "date", BaseMmsColumns.DELIVERY_TIME, BaseMmsColumns.EXPIRY, BaseMmsColumns.MESSAGE_SIZE, BaseMmsColumns.SUBJECT_CHARSET, BaseMmsColumns.RETRIEVE_TEXT_CHARSET};
    public static final int PROC_STATUS_COMPLETED = 3;
    public static final int PROC_STATUS_PERMANENTLY_FAILURE = 2;
    public static final int PROC_STATUS_TRANSIENT_FAILURE = 1;
    private static final String TAG = "PduPersister";
    public static final String TEMPORARY_DRM_OBJECT_URI = "content://mms/9223372036854775807/part";
    private static final HashMap<Integer, Integer> TEXT_STRING_COLUMN_INDEX_MAP = new HashMap();
    private static final HashMap<Integer, String> TEXT_STRING_COLUMN_NAME_MAP = new HashMap();
    private static PduPersister sPersister;
    private final ContentResolver mContentResolver;
    private final Context mContext;
    private final DrmManagerClient mDrmManagerClient;
    private final TelephonyManager mTelephonyManager;

    static {
        MESSAGE_BOX_MAP.put(Inbox.CONTENT_URI, Integer.valueOf(1));
        MESSAGE_BOX_MAP.put(Sent.CONTENT_URI, Integer.valueOf(2));
        MESSAGE_BOX_MAP.put(Draft.CONTENT_URI, Integer.valueOf(3));
        MESSAGE_BOX_MAP.put(Outbox.CONTENT_URI, Integer.valueOf(4));
        CHARSET_COLUMN_INDEX_MAP.put(Integer.valueOf(150), Integer.valueOf(25));
        CHARSET_COLUMN_INDEX_MAP.put(Integer.valueOf(154), Integer.valueOf(26));
        CHARSET_COLUMN_NAME_MAP.put(Integer.valueOf(150), BaseMmsColumns.SUBJECT_CHARSET);
        CHARSET_COLUMN_NAME_MAP.put(Integer.valueOf(154), BaseMmsColumns.RETRIEVE_TEXT_CHARSET);
        ENCODED_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(154), Integer.valueOf(3));
        ENCODED_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(150), Integer.valueOf(4));
        ENCODED_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(154), BaseMmsColumns.RETRIEVE_TEXT);
        ENCODED_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(150), BaseMmsColumns.SUBJECT);
        TEXT_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(131), Integer.valueOf(5));
        TEXT_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(132), Integer.valueOf(6));
        TEXT_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(138), Integer.valueOf(7));
        TEXT_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(139), Integer.valueOf(8));
        TEXT_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(147), Integer.valueOf(9));
        TEXT_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(152), Integer.valueOf(10));
        TEXT_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(131), BaseMmsColumns.CONTENT_LOCATION);
        TEXT_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(132), BaseMmsColumns.CONTENT_TYPE);
        TEXT_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(138), BaseMmsColumns.MESSAGE_CLASS);
        TEXT_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(139), BaseMmsColumns.MESSAGE_ID);
        TEXT_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(147), BaseMmsColumns.RESPONSE_TEXT);
        TEXT_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(152), BaseMmsColumns.TRANSACTION_ID);
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(PduHeaders.CONTENT_CLASS), Integer.valueOf(11));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(134), Integer.valueOf(12));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(140), Integer.valueOf(13));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(141), Integer.valueOf(14));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(143), Integer.valueOf(15));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(144), Integer.valueOf(16));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(155), Integer.valueOf(17));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(145), Integer.valueOf(18));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(153), Integer.valueOf(19));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(149), Integer.valueOf(20));
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(PduHeaders.CONTENT_CLASS), BaseMmsColumns.CONTENT_CLASS);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(134), BaseMmsColumns.DELIVERY_REPORT);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(140), BaseMmsColumns.MESSAGE_TYPE);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(141), BaseMmsColumns.MMS_VERSION);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(143), BaseMmsColumns.PRIORITY);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(144), BaseMmsColumns.READ_REPORT);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(155), BaseMmsColumns.READ_STATUS);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(145), BaseMmsColumns.REPORT_ALLOWED);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(153), BaseMmsColumns.RETRIEVE_STATUS);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(149), BaseMmsColumns.STATUS);
        LONG_COLUMN_INDEX_MAP.put(Integer.valueOf(133), Integer.valueOf(21));
        LONG_COLUMN_INDEX_MAP.put(Integer.valueOf(135), Integer.valueOf(22));
        LONG_COLUMN_INDEX_MAP.put(Integer.valueOf(136), Integer.valueOf(23));
        LONG_COLUMN_INDEX_MAP.put(Integer.valueOf(142), Integer.valueOf(24));
        LONG_COLUMN_NAME_MAP.put(Integer.valueOf(133), "date");
        LONG_COLUMN_NAME_MAP.put(Integer.valueOf(135), BaseMmsColumns.DELIVERY_TIME);
        LONG_COLUMN_NAME_MAP.put(Integer.valueOf(136), BaseMmsColumns.EXPIRY);
        LONG_COLUMN_NAME_MAP.put(Integer.valueOf(142), BaseMmsColumns.MESSAGE_SIZE);
    }

    private PduPersister(Context context) {
        this.mContext = context;
        this.mContentResolver = context.getContentResolver();
        this.mDrmManagerClient = new DrmManagerClient(context);
        this.mTelephonyManager = (TelephonyManager) context.getSystemService("phone");
    }

    public static String convertUriToPath(Context context, Uri uri) {
        Cursor cursor = null;
        if (uri == null) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null || scheme.equals("") || scheme.equals("file")) {
            return uri.getPath();
        }
        if (scheme.equals("http")) {
            return uri.toString();
        }
        if (scheme.equals("content")) {
            try {
                cursor = context.getContentResolver().query(uri, new String[]{Part._DATA}, null, null, null);
                if (cursor == null || cursor.getCount() == 0 || !cursor.moveToFirst()) {
                    throw new IllegalArgumentException("Given Uri could not be found in media store");
                }
                scheme = cursor.getString(cursor.getColumnIndexOrThrow(Part._DATA));
                if (cursor == null) {
                    return scheme;
                }
                cursor.close();
                return scheme;
            } catch (SQLiteException e) {
                throw new IllegalArgumentException("Given Uri is not formatted in a way so that it can be found in media store.");
            } catch (Throwable th) {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else {
            throw new IllegalArgumentException("Given Uri scheme is not supported");
        }
    }

    private byte[] getByteArrayFromPartColumn(Cursor cursor, int i) {
        return !cursor.isNull(i) ? getBytes(cursor.getString(i)) : null;
    }

    public static byte[] getBytes(String str) {
        try {
            return str.getBytes(CharacterSets.MIMENAME_ISO_8859_1);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "ISO_8859_1 must be supported!", e);
            return new byte[0];
        }
    }

    private Integer getIntegerFromPartColumn(Cursor cursor, int i) {
        return !cursor.isNull(i) ? Integer.valueOf(cursor.getInt(i)) : null;
    }

    private static String getPartContentType(PduPart pduPart) {
        return pduPart.getContentType() == null ? null : toIsoString(pduPart.getContentType());
    }

    public static PduPersister getPduPersister(Context context) {
        if (sPersister == null) {
            sPersister = new PduPersister(context);
        } else if (!context.equals(sPersister.mContext)) {
            sPersister.release();
            sPersister = new PduPersister(context);
        }
        return sPersister;
    }

    private void loadAddress(long j, PduHeaders pduHeaders) {
        Cursor query = SqliteWrapper.query(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + j + "/addr"), new String[]{"address", Addr.CHARSET, "type"}, null, null, null);
        if (query != null) {
            while (query.moveToNext()) {
                try {
                    String string = query.getString(0);
                    if (!TextUtils.isEmpty(string)) {
                        int i = query.getInt(2);
                        switch (i) {
                            case 129:
                            case 130:
                            case 151:
                                pduHeaders.appendEncodedStringValue(new EncodedStringValue(query.getInt(1), getBytes(string)), i);
                                break;
                            case 137:
                                pduHeaders.setEncodedStringValue(new EncodedStringValue(query.getInt(1), getBytes(string)), i);
                                break;
                            default:
                                Log.e(TAG, "Unknown address type: " + i);
                                break;
                        }
                    }
                } finally {
                    query.close();
                }
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:76:0x0159 A:{SYNTHETIC, Splitter:B:76:0x0159} */
    private com.google.android.mms.pdu.PduPart[] loadParts(long r12) throws com.google.android.mms.MmsException {
        /*
        r11 = this;
        r7 = 0;
        r4 = 0;
        r0 = r11.mContext;
        r1 = r11.mContentResolver;
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "content://mms/";
        r2 = r2.append(r3);
        r2 = r2.append(r12);
        r3 = "/part";
        r2 = r2.append(r3);
        r2 = r2.toString();
        r2 = android.net.Uri.parse(r2);
        r3 = PART_PROJECTION;
        r5 = r4;
        r6 = r4;
        r5 = com.google.android.mms.util.SqliteWrapper.query(r0, r1, r2, r3, r4, r5, r6);
        if (r5 == 0) goto L_0x0033;
    L_0x002d:
        r0 = r5.getCount();	 Catch:{ all -> 0x0113 }
        if (r0 != 0) goto L_0x0039;
    L_0x0033:
        if (r5 == 0) goto L_0x0038;
    L_0x0035:
        r5.close();
    L_0x0038:
        return r4;
    L_0x0039:
        r0 = r5.getCount();	 Catch:{ all -> 0x0113 }
        r0 = new com.google.android.mms.pdu.PduPart[r0];	 Catch:{ all -> 0x0113 }
        r3 = r7;
    L_0x0040:
        r1 = r5.moveToNext();	 Catch:{ all -> 0x0113 }
        if (r1 == 0) goto L_0x0166;
    L_0x0046:
        r6 = new com.google.android.mms.pdu.PduPart;	 Catch:{ all -> 0x0113 }
        r6.<init>();	 Catch:{ all -> 0x0113 }
        r1 = 1;
        r1 = r11.getIntegerFromPartColumn(r5, r1);	 Catch:{ all -> 0x0113 }
        if (r1 == 0) goto L_0x0059;
    L_0x0052:
        r1 = r1.intValue();	 Catch:{ all -> 0x0113 }
        r6.setCharset(r1);	 Catch:{ all -> 0x0113 }
    L_0x0059:
        r1 = 2;
        r1 = r11.getByteArrayFromPartColumn(r5, r1);	 Catch:{ all -> 0x0113 }
        if (r1 == 0) goto L_0x0063;
    L_0x0060:
        r6.setContentDisposition(r1);	 Catch:{ all -> 0x0113 }
    L_0x0063:
        r1 = 3;
        r1 = r11.getByteArrayFromPartColumn(r5, r1);	 Catch:{ all -> 0x0113 }
        if (r1 == 0) goto L_0x006d;
    L_0x006a:
        r6.setContentId(r1);	 Catch:{ all -> 0x0113 }
    L_0x006d:
        r1 = 4;
        r1 = r11.getByteArrayFromPartColumn(r5, r1);	 Catch:{ all -> 0x0113 }
        if (r1 == 0) goto L_0x0077;
    L_0x0074:
        r6.setContentLocation(r1);	 Catch:{ all -> 0x0113 }
    L_0x0077:
        r1 = 5;
        r1 = r11.getByteArrayFromPartColumn(r5, r1);	 Catch:{ all -> 0x0113 }
        if (r1 == 0) goto L_0x010b;
    L_0x007e:
        r6.setContentType(r1);	 Catch:{ all -> 0x0113 }
        r2 = 6;
        r2 = r11.getByteArrayFromPartColumn(r5, r2);	 Catch:{ all -> 0x0113 }
        if (r2 == 0) goto L_0x008b;
    L_0x0088:
        r6.setFilename(r2);	 Catch:{ all -> 0x0113 }
    L_0x008b:
        r2 = 7;
        r2 = r11.getByteArrayFromPartColumn(r5, r2);	 Catch:{ all -> 0x0113 }
        if (r2 == 0) goto L_0x0095;
    L_0x0092:
        r6.setName(r2);	 Catch:{ all -> 0x0113 }
    L_0x0095:
        r2 = 0;
        r8 = r5.getLong(r2);	 Catch:{ all -> 0x0113 }
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0113 }
        r2.<init>();	 Catch:{ all -> 0x0113 }
        r7 = "content://mms/part/";
        r2 = r2.append(r7);	 Catch:{ all -> 0x0113 }
        r2 = r2.append(r8);	 Catch:{ all -> 0x0113 }
        r2 = r2.toString();	 Catch:{ all -> 0x0113 }
        r2 = android.net.Uri.parse(r2);	 Catch:{ all -> 0x0113 }
        r6.setDataUri(r2);	 Catch:{ all -> 0x0113 }
        r1 = toIsoString(r1);	 Catch:{ all -> 0x0113 }
        r7 = com.google.android.mms.ContentType.isImageType(r1);	 Catch:{ all -> 0x0113 }
        if (r7 != 0) goto L_0x0104;
    L_0x00be:
        r7 = com.google.android.mms.ContentType.isAudioType(r1);	 Catch:{ all -> 0x0113 }
        if (r7 != 0) goto L_0x0104;
    L_0x00c4:
        r7 = com.google.android.mms.ContentType.isVideoType(r1);	 Catch:{ all -> 0x0113 }
        if (r7 != 0) goto L_0x0104;
    L_0x00ca:
        r7 = new java.io.ByteArrayOutputStream;	 Catch:{ all -> 0x0113 }
        r7.<init>();	 Catch:{ all -> 0x0113 }
        r8 = "text/plain";
        r8 = r8.equals(r1);	 Catch:{ all -> 0x0113 }
        if (r8 != 0) goto L_0x00e7;
    L_0x00d7:
        r8 = "application/smil";
        r8 = r8.equals(r1);	 Catch:{ all -> 0x0113 }
        if (r8 != 0) goto L_0x00e7;
    L_0x00df:
        r8 = "text/html";
        r1 = r8.equals(r1);	 Catch:{ all -> 0x0113 }
        if (r1 == 0) goto L_0x011d;
    L_0x00e7:
        r1 = 8;
        r1 = r5.getString(r1);	 Catch:{ all -> 0x0113 }
        if (r1 == 0) goto L_0x011a;
    L_0x00ef:
        r2 = new com.google.android.mms.pdu.EncodedStringValue;	 Catch:{ all -> 0x0113 }
        r2.<init>(r1);	 Catch:{ all -> 0x0113 }
        r1 = r2.getTextString();	 Catch:{ all -> 0x0113 }
        r2 = 0;
        r8 = r1.length;	 Catch:{ all -> 0x0113 }
        r7.write(r1, r2, r8);	 Catch:{ all -> 0x0113 }
    L_0x00fd:
        r1 = r7.toByteArray();	 Catch:{ all -> 0x0113 }
        r6.setData(r1);	 Catch:{ all -> 0x0113 }
    L_0x0104:
        r0[r3] = r6;
        r1 = r3 + 1;
        r3 = r1;
        goto L_0x0040;
    L_0x010b:
        r0 = new com.google.android.mms.MmsException;	 Catch:{ all -> 0x0113 }
        r1 = "Content-Type must be set.";
        r0.<init>(r1);	 Catch:{ all -> 0x0113 }
        throw r0;	 Catch:{ all -> 0x0113 }
    L_0x0113:
        r0 = move-exception;
        if (r5 == 0) goto L_0x0119;
    L_0x0116:
        r5.close();
    L_0x0119:
        throw r0;
    L_0x011a:
        r1 = "";
        goto L_0x00ef;
    L_0x011d:
        r1 = r11.mContentResolver;	 Catch:{ IOException -> 0x0145 }
        r1 = r1.openInputStream(r2);	 Catch:{ IOException -> 0x0145 }
        r2 = 256; // 0x100 float:3.59E-43 double:1.265E-321;
        r8 = new byte[r2];	 Catch:{ IOException -> 0x0171, all -> 0x016e }
        r2 = r1.read(r8);	 Catch:{ IOException -> 0x0171, all -> 0x016e }
    L_0x012b:
        if (r2 < 0) goto L_0x0136;
    L_0x012d:
        r9 = 0;
        r7.write(r8, r9, r2);	 Catch:{ IOException -> 0x0171, all -> 0x016e }
        r2 = r1.read(r8);	 Catch:{ IOException -> 0x0171, all -> 0x016e }
        goto L_0x012b;
    L_0x0136:
        if (r1 == 0) goto L_0x00fd;
    L_0x0138:
        r1.close();	 Catch:{ IOException -> 0x013c }
        goto L_0x00fd;
    L_0x013c:
        r1 = move-exception;
        r2 = "PduPersister";
        r8 = "Failed to close stream";
        android.util.Log.e(r2, r8, r1);	 Catch:{ all -> 0x0113 }
        goto L_0x00fd;
    L_0x0145:
        r0 = move-exception;
    L_0x0146:
        r1 = "PduPersister";
        r2 = "Failed to load part data";
        android.util.Log.e(r1, r2, r0);	 Catch:{ all -> 0x0156 }
        r5.close();	 Catch:{ all -> 0x0156 }
        r1 = new com.google.android.mms.MmsException;	 Catch:{ all -> 0x0156 }
        r1.<init>(r0);	 Catch:{ all -> 0x0156 }
        throw r1;	 Catch:{ all -> 0x0156 }
    L_0x0156:
        r0 = move-exception;
    L_0x0157:
        if (r4 == 0) goto L_0x015c;
    L_0x0159:
        r4.close();	 Catch:{ IOException -> 0x015d }
    L_0x015c:
        throw r0;	 Catch:{ all -> 0x0113 }
    L_0x015d:
        r1 = move-exception;
        r2 = "PduPersister";
        r3 = "Failed to close stream";
        android.util.Log.e(r2, r3, r1);	 Catch:{ all -> 0x0113 }
        goto L_0x015c;
    L_0x0166:
        if (r5 == 0) goto L_0x016b;
    L_0x0168:
        r5.close();
    L_0x016b:
        r4 = r0;
        goto L_0x0038;
    L_0x016e:
        r0 = move-exception;
        r4 = r1;
        goto L_0x0157;
    L_0x0171:
        r0 = move-exception;
        r4 = r1;
        goto L_0x0146;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.android.mms.pdu.PduPersister.loadParts(long):com.google.android.mms.pdu.PduPart[]");
    }

    private void loadRecipients(int i, HashSet<String> hashSet, HashMap<Integer, EncodedStringValue[]> hashMap, boolean z) {
        EncodedStringValue[] encodedStringValueArr = (EncodedStringValue[]) hashMap.get(Integer.valueOf(i));
        if (encodedStringValueArr != null) {
            if (!z || encodedStringValueArr.length != 1) {
                String line1Number = z ? this.mTelephonyManager.getLine1Number() : null;
                for (EncodedStringValue encodedStringValue : encodedStringValueArr) {
                    if (encodedStringValue != null) {
                        String string = encodedStringValue.getString();
                        if ((line1Number == null || !PhoneNumberUtils.compare(string, line1Number)) && !hashSet.contains(string)) {
                            hashSet.add(string);
                        }
                    }
                }
            }
        }
    }

    private void persistAddress(long j, int i, EncodedStringValue[] encodedStringValueArr) {
        ContentValues contentValues = new ContentValues(3);
        for (EncodedStringValue encodedStringValue : encodedStringValueArr) {
            contentValues.clear();
            contentValues.put("address", toIsoString(encodedStringValue.getTextString()));
            contentValues.put(Addr.CHARSET, Integer.valueOf(encodedStringValue.getCharacterSet()));
            contentValues.put("type", Integer.valueOf(i));
            SqliteWrapper.insert(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + j + "/addr"), contentValues);
        }
    }

    /* JADX WARNING: Unknown top exception splitter block from list: {B:54:0x0129=Splitter:B:54:0x0129, B:17:0x006e=Splitter:B:17:0x006e} */
    /* JADX WARNING: Removed duplicated region for block: B:79:0x01b2 A:{SYNTHETIC, Splitter:B:79:0x01b2} */
    /* JADX WARNING: Removed duplicated region for block: B:146:0x01f1 A:{SYNTHETIC, EDGE_INSN: B:146:0x01f1->B:101:0x01f1 ?: BREAK  , EDGE_INSN: B:146:0x01f1->B:101:0x01f1 ?: BREAK  } */
    /* JADX WARNING: Removed duplicated region for block: B:84:0x01c3 A:{Catch:{ FileNotFoundException -> 0x01ca, IOException -> 0x01da, all -> 0x01e6 }} */
    /* JADX WARNING: Removed duplicated region for block: B:23:0x007f A:{SYNTHETIC, Splitter:B:23:0x007f} */
    /* JADX WARNING: Removed duplicated region for block: B:26:0x0084 A:{SYNTHETIC, Splitter:B:26:0x0084} */
    /* JADX WARNING: Removed duplicated region for block: B:29:0x0089  */
    /* JADX WARNING: Removed duplicated region for block: B:23:0x007f A:{SYNTHETIC, Splitter:B:23:0x007f} */
    /* JADX WARNING: Removed duplicated region for block: B:26:0x0084 A:{SYNTHETIC, Splitter:B:26:0x0084} */
    /* JADX WARNING: Removed duplicated region for block: B:29:0x0089  */
    /* JADX WARNING: Removed duplicated region for block: B:23:0x007f A:{SYNTHETIC, Splitter:B:23:0x007f} */
    /* JADX WARNING: Removed duplicated region for block: B:26:0x0084 A:{SYNTHETIC, Splitter:B:26:0x0084} */
    /* JADX WARNING: Removed duplicated region for block: B:29:0x0089  */
    /* JADX WARNING: Removed duplicated region for block: B:23:0x007f A:{SYNTHETIC, Splitter:B:23:0x007f} */
    /* JADX WARNING: Removed duplicated region for block: B:26:0x0084 A:{SYNTHETIC, Splitter:B:26:0x0084} */
    /* JADX WARNING: Removed duplicated region for block: B:29:0x0089  */
    /* JADX WARNING: Removed duplicated region for block: B:23:0x007f A:{SYNTHETIC, Splitter:B:23:0x007f} */
    /* JADX WARNING: Removed duplicated region for block: B:26:0x0084 A:{SYNTHETIC, Splitter:B:26:0x0084} */
    /* JADX WARNING: Removed duplicated region for block: B:29:0x0089  */
    /* JADX WARNING: Removed duplicated region for block: B:47:0x00fe A:{SYNTHETIC, Splitter:B:47:0x00fe} */
    /* JADX WARNING: Removed duplicated region for block: B:97:0x01eb  */
    /* JADX WARNING: Removed duplicated region for block: B:61:0x0140 A:{SYNTHETIC, Splitter:B:61:0x0140} */
    private void persistData(com.google.android.mms.pdu.PduPart r12, android.net.Uri r13, java.lang.String r14, java.util.HashMap<android.net.Uri, java.io.InputStream> r15) throws com.google.android.mms.MmsException {
        /*
        r11 = this;
        r10 = 0;
        r4 = 0;
        r0 = r12.getData();	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r1 = "text/plain";
        r1 = r1.equals(r14);	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        if (r1 != 0) goto L_0x001e;
    L_0x000e:
        r1 = "application/smil";
        r1 = r1.equals(r14);	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        if (r1 != 0) goto L_0x001e;
    L_0x0016:
        r1 = "text/html";
        r1 = r1.equals(r14);	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        if (r1 == 0) goto L_0x00ba;
    L_0x001e:
        r1 = new android.content.ContentValues;	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r1.<init>();	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        if (r0 != 0) goto L_0x0032;
    L_0x0025:
        r0 = new java.lang.String;	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r2 = "";
        r0.<init>(r2);	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r2 = "utf-8";
        r0 = r0.getBytes(r2);	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
    L_0x0032:
        r2 = "text";
        r3 = new com.google.android.mms.pdu.EncodedStringValue;	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r3.<init>(r0);	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r0 = r3.getString();	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r1.put(r2, r0);	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r0 = r11.mContentResolver;	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r2 = 0;
        r3 = 0;
        r0 = r0.update(r13, r1, r2, r3);	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r1 = 1;
        if (r0 == r1) goto L_0x02f1;
    L_0x004b:
        r0 = new com.google.android.mms.MmsException;	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r1 = new java.lang.StringBuilder;	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r1.<init>();	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r2 = "unable to update ";
        r1 = r1.append(r2);	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r2 = r13.toString();	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r1 = r1.append(r2);	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r1 = r1.toString();	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        r0.<init>(r1);	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        throw r0;	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
    L_0x0068:
        r0 = move-exception;
        r3 = r0;
        r5 = r4;
        r2 = r4;
        r6 = r4;
        r1 = r4;
    L_0x006e:
        r0 = "PduPersister";
        r7 = "Failed to open Input/Output stream.";
        android.util.Log.e(r0, r7, r3);	 Catch:{ all -> 0x007b }
        r0 = new com.google.android.mms.MmsException;	 Catch:{ all -> 0x007b }
        r0.<init>(r3);	 Catch:{ all -> 0x007b }
        throw r0;	 Catch:{ all -> 0x007b }
    L_0x007b:
        r0 = move-exception;
        r7 = r0;
    L_0x007d:
        if (r5 == 0) goto L_0x0082;
    L_0x007f:
        r5.close();	 Catch:{ IOException -> 0x027f }
    L_0x0082:
        if (r6 == 0) goto L_0x0087;
    L_0x0084:
        r6.close();	 Catch:{ IOException -> 0x029a }
    L_0x0087:
        if (r2 == 0) goto L_0x00b9;
    L_0x0089:
        r2.close(r1);
        r2 = new java.io.File;
        r2.<init>(r1);
        r3 = new android.content.ContentValues;
        r3.<init>(r10);
        r0 = r11.mContext;
        r1 = r11.mContentResolver;
        r5 = new java.lang.StringBuilder;
        r5.<init>();
        r6 = "content://mms/resetFilePerm/";
        r5 = r5.append(r6);
        r2 = r2.getName();
        r2 = r5.append(r2);
        r2 = r2.toString();
        r2 = android.net.Uri.parse(r2);
        r5 = r4;
        com.google.android.mms.util.SqliteWrapper.update(r0, r1, r2, r3, r4, r5);
    L_0x00b9:
        throw r7;
    L_0x00ba:
        r3 = com.google.android.mms.util.DownloadDrmHelper.isDrmConvertNeeded(r14);	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0123, all -> 0x02ca }
        if (r3 == 0) goto L_0x0136;
    L_0x00c0:
        if (r13 == 0) goto L_0x02ee;
    L_0x00c2:
        r1 = r11.mContext;	 Catch:{ Exception -> 0x00d8 }
        r1 = convertUriToPath(r1, r13);	 Catch:{ Exception -> 0x00d8 }
        r2 = new java.io.File;	 Catch:{ Exception -> 0x02c7 }
        r2.<init>(r1);	 Catch:{ Exception -> 0x02c7 }
        r6 = r2.length();	 Catch:{ Exception -> 0x02c7 }
        r8 = 0;
        r2 = (r6 > r8 ? 1 : (r6 == r8 ? 0 : -1));
        if (r2 <= 0) goto L_0x00f6;
    L_0x00d7:
        return;
    L_0x00d8:
        r2 = move-exception;
        r1 = r4;
    L_0x00da:
        r5 = "PduPersister";
        r6 = new java.lang.StringBuilder;	 Catch:{ FileNotFoundException -> 0x02e4, IOException -> 0x02b5, all -> 0x02d2 }
        r6.<init>();	 Catch:{ FileNotFoundException -> 0x02e4, IOException -> 0x02b5, all -> 0x02d2 }
        r7 = "Can't get file info for: ";
        r6 = r6.append(r7);	 Catch:{ FileNotFoundException -> 0x02e4, IOException -> 0x02b5, all -> 0x02d2 }
        r7 = r12.getDataUri();	 Catch:{ FileNotFoundException -> 0x02e4, IOException -> 0x02b5, all -> 0x02d2 }
        r6 = r6.append(r7);	 Catch:{ FileNotFoundException -> 0x02e4, IOException -> 0x02b5, all -> 0x02d2 }
        r6 = r6.toString();	 Catch:{ FileNotFoundException -> 0x02e4, IOException -> 0x02b5, all -> 0x02d2 }
        android.util.Log.e(r5, r6, r2);	 Catch:{ FileNotFoundException -> 0x02e4, IOException -> 0x02b5, all -> 0x02d2 }
    L_0x00f6:
        r2 = r11.mContext;	 Catch:{ FileNotFoundException -> 0x02e4, IOException -> 0x02b5, all -> 0x02d2 }
        r2 = com.google.android.mms.util.DrmConvertSession.open(r2, r14);	 Catch:{ FileNotFoundException -> 0x02e4, IOException -> 0x02b5, all -> 0x02d2 }
        if (r2 != 0) goto L_0x0138;
    L_0x00fe:
        r0 = new com.google.android.mms.MmsException;	 Catch:{ FileNotFoundException -> 0x011d, IOException -> 0x02bc, all -> 0x02d9 }
        r3 = new java.lang.StringBuilder;	 Catch:{ FileNotFoundException -> 0x011d, IOException -> 0x02bc, all -> 0x02d9 }
        r3.<init>();	 Catch:{ FileNotFoundException -> 0x011d, IOException -> 0x02bc, all -> 0x02d9 }
        r5 = "Mimetype ";
        r3 = r3.append(r5);	 Catch:{ FileNotFoundException -> 0x011d, IOException -> 0x02bc, all -> 0x02d9 }
        r3 = r3.append(r14);	 Catch:{ FileNotFoundException -> 0x011d, IOException -> 0x02bc, all -> 0x02d9 }
        r5 = " can not be converted.";
        r3 = r3.append(r5);	 Catch:{ FileNotFoundException -> 0x011d, IOException -> 0x02bc, all -> 0x02d9 }
        r3 = r3.toString();	 Catch:{ FileNotFoundException -> 0x011d, IOException -> 0x02bc, all -> 0x02d9 }
        r0.<init>(r3);	 Catch:{ FileNotFoundException -> 0x011d, IOException -> 0x02bc, all -> 0x02d9 }
        throw r0;	 Catch:{ FileNotFoundException -> 0x011d, IOException -> 0x02bc, all -> 0x02d9 }
    L_0x011d:
        r0 = move-exception;
        r3 = r0;
        r5 = r4;
        r6 = r4;
        goto L_0x006e;
    L_0x0123:
        r0 = move-exception;
        r3 = r0;
        r5 = r4;
        r2 = r4;
        r6 = r4;
        r1 = r4;
    L_0x0129:
        r0 = "PduPersister";
        r7 = "Failed to read/write data.";
        android.util.Log.e(r0, r7, r3);	 Catch:{ all -> 0x007b }
        r0 = new com.google.android.mms.MmsException;	 Catch:{ all -> 0x007b }
        r0.<init>(r3);	 Catch:{ all -> 0x007b }
        throw r0;	 Catch:{ all -> 0x007b }
    L_0x0136:
        r1 = r4;
        r2 = r4;
    L_0x0138:
        r5 = r11.mContentResolver;	 Catch:{ FileNotFoundException -> 0x011d, IOException -> 0x02bc, all -> 0x02d9 }
        r5 = r5.openOutputStream(r13);	 Catch:{ FileNotFoundException -> 0x011d, IOException -> 0x02bc, all -> 0x02d9 }
        if (r0 != 0) goto L_0x01eb;
    L_0x0140:
        r6 = r12.getDataUri();	 Catch:{ FileNotFoundException -> 0x0245, IOException -> 0x02c2, all -> 0x02df }
        if (r6 == 0) goto L_0x0148;
    L_0x0146:
        if (r6 != r13) goto L_0x01a2;
    L_0x0148:
        r0 = "PduPersister";
        r3 = "Can't find data for this part.";
        android.util.Log.w(r0, r3);	 Catch:{ FileNotFoundException -> 0x0245, IOException -> 0x02c2, all -> 0x02df }
        if (r5 == 0) goto L_0x0154;
    L_0x0151:
        r5.close();	 Catch:{ IOException -> 0x0188 }
    L_0x0154:
        if (r2 == 0) goto L_0x00d7;
    L_0x0156:
        r2.close(r1);
        r2 = new java.io.File;
        r2.<init>(r1);
        r3 = new android.content.ContentValues;
        r3.<init>(r10);
        r0 = r11.mContext;
        r1 = r11.mContentResolver;
        r5 = new java.lang.StringBuilder;
        r5.<init>();
        r6 = "content://mms/resetFilePerm/";
        r5 = r5.append(r6);
        r2 = r2.getName();
        r2 = r5.append(r2);
        r2 = r2.toString();
        r2 = android.net.Uri.parse(r2);
        r5 = r4;
        com.google.android.mms.util.SqliteWrapper.update(r0, r1, r2, r3, r4, r5);
        goto L_0x00d7;
    L_0x0188:
        r0 = move-exception;
        r3 = "PduPersister";
        r6 = new java.lang.StringBuilder;
        r6.<init>();
        r7 = "IOException while closing: ";
        r6 = r6.append(r7);
        r5 = r6.append(r5);
        r5 = r5.toString();
        android.util.Log.e(r3, r5, r0);
        goto L_0x0154;
    L_0x01a2:
        if (r15 == 0) goto L_0x02eb;
    L_0x01a4:
        r0 = r15.containsKey(r6);	 Catch:{ FileNotFoundException -> 0x0245, IOException -> 0x02c2, all -> 0x02df }
        if (r0 == 0) goto L_0x02eb;
    L_0x01aa:
        r0 = r15.get(r6);	 Catch:{ FileNotFoundException -> 0x0245, IOException -> 0x02c2, all -> 0x02df }
        r0 = (java.io.InputStream) r0;	 Catch:{ FileNotFoundException -> 0x0245, IOException -> 0x02c2, all -> 0x02df }
    L_0x01b0:
        if (r0 != 0) goto L_0x01b8;
    L_0x01b2:
        r7 = r11.mContentResolver;	 Catch:{ FileNotFoundException -> 0x01ca, IOException -> 0x01da, all -> 0x01e6 }
        r0 = r7.openInputStream(r6);	 Catch:{ FileNotFoundException -> 0x01ca, IOException -> 0x01da, all -> 0x01e6 }
    L_0x01b8:
        r6 = 8192; // 0x2000 float:1.14794E-41 double:4.0474E-320;
        r6 = new byte[r6];	 Catch:{ FileNotFoundException -> 0x01ca, IOException -> 0x01da, all -> 0x01e6 }
    L_0x01bc:
        r7 = r0.read(r6);	 Catch:{ FileNotFoundException -> 0x01ca, IOException -> 0x01da, all -> 0x01e6 }
        r8 = -1;
        if (r7 == r8) goto L_0x01f1;
    L_0x01c3:
        if (r3 != 0) goto L_0x01ce;
    L_0x01c5:
        r8 = 0;
        r5.write(r6, r8, r7);	 Catch:{ FileNotFoundException -> 0x01ca, IOException -> 0x01da, all -> 0x01e6 }
        goto L_0x01bc;
    L_0x01ca:
        r3 = move-exception;
        r6 = r0;
        goto L_0x006e;
    L_0x01ce:
        r7 = r2.convert(r6, r7);	 Catch:{ FileNotFoundException -> 0x01ca, IOException -> 0x01da, all -> 0x01e6 }
        if (r7 == 0) goto L_0x01de;
    L_0x01d4:
        r8 = 0;
        r9 = r7.length;	 Catch:{ FileNotFoundException -> 0x01ca, IOException -> 0x01da, all -> 0x01e6 }
        r5.write(r7, r8, r9);	 Catch:{ FileNotFoundException -> 0x01ca, IOException -> 0x01da, all -> 0x01e6 }
        goto L_0x01bc;
    L_0x01da:
        r3 = move-exception;
        r6 = r0;
        goto L_0x0129;
    L_0x01de:
        r3 = new com.google.android.mms.MmsException;	 Catch:{ FileNotFoundException -> 0x01ca, IOException -> 0x01da, all -> 0x01e6 }
        r6 = "Error converting drm data.";
        r3.<init>(r6);	 Catch:{ FileNotFoundException -> 0x01ca, IOException -> 0x01da, all -> 0x01e6 }
        throw r3;	 Catch:{ FileNotFoundException -> 0x01ca, IOException -> 0x01da, all -> 0x01e6 }
    L_0x01e6:
        r3 = move-exception;
        r7 = r3;
        r6 = r0;
        goto L_0x007d;
    L_0x01eb:
        if (r3 != 0) goto L_0x022f;
    L_0x01ed:
        r5.write(r0);	 Catch:{ FileNotFoundException -> 0x0245, IOException -> 0x02c2, all -> 0x02df }
        r0 = r4;
    L_0x01f1:
        if (r5 == 0) goto L_0x01f6;
    L_0x01f3:
        r5.close();	 Catch:{ IOException -> 0x024a }
    L_0x01f6:
        if (r0 == 0) goto L_0x01fb;
    L_0x01f8:
        r0.close();	 Catch:{ IOException -> 0x0264 }
    L_0x01fb:
        if (r2 == 0) goto L_0x00d7;
    L_0x01fd:
        r2.close(r1);
        r2 = new java.io.File;
        r2.<init>(r1);
        r3 = new android.content.ContentValues;
        r3.<init>(r10);
        r0 = r11.mContext;
        r1 = r11.mContentResolver;
        r5 = new java.lang.StringBuilder;
        r5.<init>();
        r6 = "content://mms/resetFilePerm/";
        r5 = r5.append(r6);
        r2 = r2.getName();
        r2 = r5.append(r2);
        r2 = r2.toString();
        r2 = android.net.Uri.parse(r2);
        r5 = r4;
        com.google.android.mms.util.SqliteWrapper.update(r0, r1, r2, r3, r4, r5);
        goto L_0x00d7;
    L_0x022f:
        r3 = r0.length;	 Catch:{ FileNotFoundException -> 0x0245, IOException -> 0x02c2, all -> 0x02df }
        r0 = r2.convert(r0, r3);	 Catch:{ FileNotFoundException -> 0x0245, IOException -> 0x02c2, all -> 0x02df }
        if (r0 == 0) goto L_0x023d;
    L_0x0236:
        r3 = 0;
        r6 = r0.length;	 Catch:{ FileNotFoundException -> 0x0245, IOException -> 0x02c2, all -> 0x02df }
        r5.write(r0, r3, r6);	 Catch:{ FileNotFoundException -> 0x0245, IOException -> 0x02c2, all -> 0x02df }
        r0 = r4;
        goto L_0x01f1;
    L_0x023d:
        r0 = new com.google.android.mms.MmsException;	 Catch:{ FileNotFoundException -> 0x0245, IOException -> 0x02c2, all -> 0x02df }
        r3 = "Error converting drm data.";
        r0.<init>(r3);	 Catch:{ FileNotFoundException -> 0x0245, IOException -> 0x02c2, all -> 0x02df }
        throw r0;	 Catch:{ FileNotFoundException -> 0x0245, IOException -> 0x02c2, all -> 0x02df }
    L_0x0245:
        r0 = move-exception;
        r3 = r0;
        r6 = r4;
        goto L_0x006e;
    L_0x024a:
        r3 = move-exception;
        r6 = "PduPersister";
        r7 = new java.lang.StringBuilder;
        r7.<init>();
        r8 = "IOException while closing: ";
        r7 = r7.append(r8);
        r5 = r7.append(r5);
        r5 = r5.toString();
        android.util.Log.e(r6, r5, r3);
        goto L_0x01f6;
    L_0x0264:
        r3 = move-exception;
        r5 = "PduPersister";
        r6 = new java.lang.StringBuilder;
        r6.<init>();
        r7 = "IOException while closing: ";
        r6 = r6.append(r7);
        r0 = r6.append(r0);
        r0 = r0.toString();
        android.util.Log.e(r5, r0, r3);
        goto L_0x01fb;
    L_0x027f:
        r0 = move-exception;
        r3 = "PduPersister";
        r8 = new java.lang.StringBuilder;
        r8.<init>();
        r9 = "IOException while closing: ";
        r8 = r8.append(r9);
        r5 = r8.append(r5);
        r5 = r5.toString();
        android.util.Log.e(r3, r5, r0);
        goto L_0x0082;
    L_0x029a:
        r0 = move-exception;
        r3 = "PduPersister";
        r5 = new java.lang.StringBuilder;
        r5.<init>();
        r8 = "IOException while closing: ";
        r5 = r5.append(r8);
        r5 = r5.append(r6);
        r5 = r5.toString();
        android.util.Log.e(r3, r5, r0);
        goto L_0x0087;
    L_0x02b5:
        r0 = move-exception;
        r3 = r0;
        r5 = r4;
        r2 = r4;
        r6 = r4;
        goto L_0x0129;
    L_0x02bc:
        r0 = move-exception;
        r3 = r0;
        r5 = r4;
        r6 = r4;
        goto L_0x0129;
    L_0x02c2:
        r0 = move-exception;
        r3 = r0;
        r6 = r4;
        goto L_0x0129;
    L_0x02c7:
        r2 = move-exception;
        goto L_0x00da;
    L_0x02ca:
        r0 = move-exception;
        r7 = r0;
        r5 = r4;
        r2 = r4;
        r6 = r4;
        r1 = r4;
        goto L_0x007d;
    L_0x02d2:
        r0 = move-exception;
        r7 = r0;
        r5 = r4;
        r2 = r4;
        r6 = r4;
        goto L_0x007d;
    L_0x02d9:
        r0 = move-exception;
        r7 = r0;
        r5 = r4;
        r6 = r4;
        goto L_0x007d;
    L_0x02df:
        r0 = move-exception;
        r7 = r0;
        r6 = r4;
        goto L_0x007d;
    L_0x02e4:
        r0 = move-exception;
        r3 = r0;
        r5 = r4;
        r2 = r4;
        r6 = r4;
        goto L_0x006e;
    L_0x02eb:
        r0 = r4;
        goto L_0x01b0;
    L_0x02ee:
        r1 = r4;
        goto L_0x00f6;
    L_0x02f1:
        r5 = r4;
        r1 = r4;
        r0 = r4;
        r2 = r4;
        goto L_0x01f1;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.android.mms.pdu.PduPersister.persistData(com.google.android.mms.pdu.PduPart, android.net.Uri, java.lang.String, java.util.HashMap):void");
    }

    private void setEncodedStringValueToHeaders(Cursor cursor, int i, PduHeaders pduHeaders, int i2) {
        String string = cursor.getString(i);
        if (string != null && string.length() > 0) {
            pduHeaders.setEncodedStringValue(new EncodedStringValue(cursor.getInt(((Integer) CHARSET_COLUMN_INDEX_MAP.get(Integer.valueOf(i2))).intValue()), getBytes(string)), i2);
        }
    }

    private void setLongToHeaders(Cursor cursor, int i, PduHeaders pduHeaders, int i2) {
        if (!cursor.isNull(i)) {
            pduHeaders.setLongInteger(cursor.getLong(i), i2);
        }
    }

    private void setOctetToHeaders(Cursor cursor, int i, PduHeaders pduHeaders, int i2) throws InvalidHeaderValueException {
        if (!cursor.isNull(i)) {
            pduHeaders.setOctet(cursor.getInt(i), i2);
        }
    }

    private void setTextStringToHeaders(Cursor cursor, int i, PduHeaders pduHeaders, int i2) {
        String string = cursor.getString(i);
        if (string != null) {
            pduHeaders.setTextString(getBytes(string), i2);
        }
    }

    public static String toIsoString(byte[] bArr) {
        try {
            return new String(bArr, CharacterSets.MIMENAME_ISO_8859_1);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "ISO_8859_1 must be supported!", e);
            return "";
        }
    }

    private void updateAddress(long j, int i, EncodedStringValue[] encodedStringValueArr) {
        SqliteWrapper.delete(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + j + "/addr"), "type=" + i, null);
        persistAddress(j, i, encodedStringValueArr);
    }

    private void updatePart(Uri uri, PduPart pduPart, HashMap<Uri, InputStream> hashMap) throws MmsException {
        ContentValues contentValues = new ContentValues(7);
        int charset = pduPart.getCharset();
        if (charset != 0) {
            contentValues.put(Part.CHARSET, Integer.valueOf(charset));
        }
        if (pduPart.getContentType() != null) {
            String toIsoString = toIsoString(pduPart.getContentType());
            contentValues.put(Part.CONTENT_TYPE, toIsoString);
            if (pduPart.getFilename() != null) {
                contentValues.put(Part.FILENAME, new String(pduPart.getFilename()));
            }
            if (pduPart.getName() != null) {
                contentValues.put("name", new String(pduPart.getName()));
            }
            if (pduPart.getContentDisposition() != null) {
                contentValues.put(Part.CONTENT_DISPOSITION, toIsoString(pduPart.getContentDisposition()));
            }
            if (pduPart.getContentId() != null) {
                contentValues.put("cid", toIsoString(pduPart.getContentId()));
            }
            if (pduPart.getContentLocation() != null) {
                contentValues.put(Part.CONTENT_LOCATION, toIsoString(pduPart.getContentLocation()));
            }
            SqliteWrapper.update(this.mContext, this.mContentResolver, uri, contentValues, null, null);
            if (pduPart.getData() != null || uri != pduPart.getDataUri()) {
                persistData(pduPart, uri, toIsoString, hashMap);
                return;
            }
            return;
        }
        throw new MmsException("MIME type of the part must be set.");
    }

    public Cursor getPendingMessages(long j) {
        Builder buildUpon = PendingMessages.CONTENT_URI.buildUpon();
        buildUpon.appendQueryParameter("protocol", "mms");
        return SqliteWrapper.query(this.mContext, this.mContentResolver, buildUpon.build(), null, "err_type < ? AND due_time <= ?", new String[]{String.valueOf(10), String.valueOf(j)}, PendingMessages.DUE_TIME);
    }

    /* JADX WARNING: Can't wrap try/catch for region: R(4:109|110|111|112) */
    /* JADX WARNING: Missing block: B:14:0x0021, code skipped:
            r3 = PDU_CACHE_INSTANCE;
     */
    /* JADX WARNING: Missing block: B:15:0x0023, code skipped:
            monitor-enter(r3);
     */
    /* JADX WARNING: Missing block: B:17:?, code skipped:
            PDU_CACHE_INSTANCE.setUpdating(r15, false);
            PDU_CACHE_INSTANCE.notifyAll();
     */
    /* JADX WARNING: Missing block: B:18:0x002f, code skipped:
            monitor-exit(r3);
     */
    /* JADX WARNING: Missing block: B:19:0x0030, code skipped:
            return r2;
     */
    /* JADX WARNING: Missing block: B:45:?, code skipped:
            r4 = com.google.android.mms.util.SqliteWrapper.query(r14.mContext, r14.mContentResolver, r15, PDU_PROJECTION, null, null, null);
            r5 = new com.google.android.mms.pdu.PduHeaders();
            r6 = android.content.ContentUris.parseId(r15);
     */
    /* JADX WARNING: Missing block: B:46:0x0072, code skipped:
            if (r4 == null) goto L_0x0080;
     */
    /* JADX WARNING: Missing block: B:49:0x0078, code skipped:
            if (r4.getCount() != 1) goto L_0x0080;
     */
    /* JADX WARNING: Missing block: B:51:0x007e, code skipped:
            if (r4.moveToFirst() != false) goto L_0x00a2;
     */
    /* JADX WARNING: Missing block: B:53:0x0098, code skipped:
            throw new com.google.android.mms.MmsException("Bad uri: " + r15);
     */
    /* JADX WARNING: Missing block: B:55:0x009a, code skipped:
            if (r4 != null) goto L_0x009c;
     */
    /* JADX WARNING: Missing block: B:57:?, code skipped:
            r4.close();
     */
    /* JADX WARNING: Missing block: B:59:0x00a0, code skipped:
            r2 = th;
     */
    /* JADX WARNING: Missing block: B:62:?, code skipped:
            r8 = r4.getInt(1);
            r10 = r4.getLong(2);
            r12 = ENCODED_STRING_COLUMN_INDEX_MAP.entrySet().iterator();
     */
    /* JADX WARNING: Missing block: B:64:0x00ba, code skipped:
            if (r12.hasNext() == false) goto L_0x00dc;
     */
    /* JADX WARNING: Missing block: B:65:0x00bc, code skipped:
            r3 = (java.util.Map.Entry) r12.next();
            setEncodedStringValueToHeaders(r4, ((java.lang.Integer) r3.getValue()).intValue(), r5, ((java.lang.Integer) r3.getKey()).intValue());
     */
    /* JADX WARNING: Missing block: B:66:0x00dc, code skipped:
            r12 = TEXT_STRING_COLUMN_INDEX_MAP.entrySet().iterator();
     */
    /* JADX WARNING: Missing block: B:68:0x00ea, code skipped:
            if (r12.hasNext() == false) goto L_0x010a;
     */
    /* JADX WARNING: Missing block: B:69:0x00ec, code skipped:
            r2 = (java.util.Map.Entry) r12.next();
            setTextStringToHeaders(r4, ((java.lang.Integer) r2.getValue()).intValue(), r5, ((java.lang.Integer) r2.getKey()).intValue());
     */
    /* JADX WARNING: Missing block: B:70:0x010a, code skipped:
            r12 = OCTET_COLUMN_INDEX_MAP.entrySet().iterator();
     */
    /* JADX WARNING: Missing block: B:72:0x0118, code skipped:
            if (r12.hasNext() == false) goto L_0x0138;
     */
    /* JADX WARNING: Missing block: B:73:0x011a, code skipped:
            r2 = (java.util.Map.Entry) r12.next();
            setOctetToHeaders(r4, ((java.lang.Integer) r2.getValue()).intValue(), r5, ((java.lang.Integer) r2.getKey()).intValue());
     */
    /* JADX WARNING: Missing block: B:74:0x0138, code skipped:
            r12 = LONG_COLUMN_INDEX_MAP.entrySet().iterator();
     */
    /* JADX WARNING: Missing block: B:76:0x0146, code skipped:
            if (r12.hasNext() == false) goto L_0x0166;
     */
    /* JADX WARNING: Missing block: B:77:0x0148, code skipped:
            r2 = (java.util.Map.Entry) r12.next();
            setLongToHeaders(r4, ((java.lang.Integer) r2.getValue()).intValue(), r5, ((java.lang.Integer) r2.getKey()).intValue());
     */
    /* JADX WARNING: Missing block: B:79:0x0166, code skipped:
            if (r4 == null) goto L_0x016b;
     */
    /* JADX WARNING: Missing block: B:81:?, code skipped:
            r4.close();
     */
    /* JADX WARNING: Missing block: B:83:0x016f, code skipped:
            if (r6 != -1) goto L_0x0179;
     */
    /* JADX WARNING: Missing block: B:85:0x0178, code skipped:
            throw new com.google.android.mms.MmsException("Error! ID of the message: -1.");
     */
    /* JADX WARNING: Missing block: B:86:0x0179, code skipped:
            loadAddress(r6, r5);
            r3 = r5.getOctet(140);
            r4 = new com.google.android.mms.pdu.PduBody();
     */
    /* JADX WARNING: Missing block: B:87:0x0189, code skipped:
            if (r3 == 132) goto L_0x018f;
     */
    /* JADX WARNING: Missing block: B:89:0x018d, code skipped:
            if (r3 != 128) goto L_0x01a1;
     */
    /* JADX WARNING: Missing block: B:90:0x018f, code skipped:
            r6 = loadParts(r6);
     */
    /* JADX WARNING: Missing block: B:91:0x0193, code skipped:
            if (r6 == null) goto L_0x01a1;
     */
    /* JADX WARNING: Missing block: B:92:0x0195, code skipped:
            r7 = r6.length;
            r2 = 0;
     */
    /* JADX WARNING: Missing block: B:93:0x0197, code skipped:
            if (r2 >= r7) goto L_0x01a1;
     */
    /* JADX WARNING: Missing block: B:94:0x0199, code skipped:
            r4.addPart(r6[r2]);
            r2 = r2 + 1;
     */
    /* JADX WARNING: Missing block: B:95:0x01a1, code skipped:
            switch(r3) {
                case 128: goto L_0x01f2;
                case 129: goto L_0x020a;
                case 130: goto L_0x01c1;
                case 131: goto L_0x01fe;
                case 132: goto L_0x01ec;
                case 133: goto L_0x01f8;
                case 134: goto L_0x01e0;
                case 135: goto L_0x0204;
                case 136: goto L_0x01e6;
                case 137: goto L_0x020a;
                case 138: goto L_0x020a;
                case 139: goto L_0x020a;
                case 140: goto L_0x020a;
                case 141: goto L_0x020a;
                case 142: goto L_0x020a;
                case 143: goto L_0x020a;
                case 144: goto L_0x020a;
                case 145: goto L_0x020a;
                case 146: goto L_0x020a;
                case 147: goto L_0x020a;
                case 148: goto L_0x020a;
                case 149: goto L_0x020a;
                case 150: goto L_0x020a;
                case 151: goto L_0x020a;
                default: goto L_0x01a4;
            };
     */
    /* JADX WARNING: Missing block: B:97:0x01c0, code skipped:
            throw new com.google.android.mms.MmsException("Unrecognized PDU type: " + java.lang.Integer.toHexString(r3));
     */
    /* JADX WARNING: Missing block: B:98:0x01c1, code skipped:
            r2 = new com.google.android.mms.pdu.NotificationInd(r5);
     */
    /* JADX WARNING: Missing block: B:99:0x01c6, code skipped:
            r3 = PDU_CACHE_INSTANCE;
     */
    /* JADX WARNING: Missing block: B:100:0x01c8, code skipped:
            monitor-enter(r3);
     */
    /* JADX WARNING: Missing block: B:101:0x01c9, code skipped:
            if (r2 == null) goto L_0x0231;
     */
    /* JADX WARNING: Missing block: B:104:0x01cd, code skipped:
            if ($assertionsDisabled != false) goto L_0x0227;
     */
    /* JADX WARNING: Missing block: B:106:0x01d5, code skipped:
            if (PDU_CACHE_INSTANCE.get(r15) == null) goto L_0x0227;
     */
    /* JADX WARNING: Missing block: B:108:0x01dc, code skipped:
            throw new java.lang.AssertionError();
     */
    /* JADX WARNING: Missing block: B:109:0x01dd, code skipped:
            r2 = th;
     */
    /* JADX WARNING: Missing block: B:111:?, code skipped:
            monitor-exit(r3);
     */
    /* JADX WARNING: Missing block: B:112:0x01df, code skipped:
            throw r2;
     */
    /* JADX WARNING: Missing block: B:114:?, code skipped:
            r2 = new com.google.android.mms.pdu.DeliveryInd(r5);
     */
    /* JADX WARNING: Missing block: B:115:0x01e6, code skipped:
            r2 = new com.google.android.mms.pdu.ReadOrigInd(r5);
     */
    /* JADX WARNING: Missing block: B:116:0x01ec, code skipped:
            r2 = new com.google.android.mms.pdu.RetrieveConf(r5, r4);
     */
    /* JADX WARNING: Missing block: B:117:0x01f2, code skipped:
            r2 = new com.google.android.mms.pdu.SendReq(r5, r4);
     */
    /* JADX WARNING: Missing block: B:118:0x01f8, code skipped:
            r2 = new com.google.android.mms.pdu.AcknowledgeInd(r5);
     */
    /* JADX WARNING: Missing block: B:119:0x01fe, code skipped:
            r2 = new com.google.android.mms.pdu.NotifyRespInd(r5);
     */
    /* JADX WARNING: Missing block: B:120:0x0204, code skipped:
            r2 = new com.google.android.mms.pdu.ReadRecInd(r5);
     */
    /* JADX WARNING: Missing block: B:122:0x0226, code skipped:
            throw new com.google.android.mms.MmsException("Unsupported PDU type: " + java.lang.Integer.toHexString(r3));
     */
    /* JADX WARNING: Missing block: B:126:?, code skipped:
            PDU_CACHE_INSTANCE.put(r15, new com.google.android.mms.util.PduCacheEntry(r2, r8, r10));
     */
    /* JADX WARNING: Missing block: B:127:0x0231, code skipped:
            PDU_CACHE_INSTANCE.setUpdating(r15, false);
            PDU_CACHE_INSTANCE.notifyAll();
     */
    /* JADX WARNING: Missing block: B:128:0x023c, code skipped:
            monitor-exit(r3);
     */
    /* JADX WARNING: Missing block: B:129:0x023f, code skipped:
            r2 = th;
     */
    public com.google.android.mms.pdu.GenericPdu load(android.net.Uri r15) throws com.google.android.mms.MmsException {
        /*
        r14 = this;
        r10 = 1;
        r9 = 0;
        r3 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0040 }
        monitor-enter(r3);	 Catch:{ all -> 0x0040 }
        r2 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x003d }
        r2 = r2.isUpdating(r15);	 Catch:{ all -> 0x003d }
        if (r2 == 0) goto L_0x0054;
    L_0x000d:
        r2 = PDU_CACHE_INSTANCE;	 Catch:{ InterruptedException -> 0x0034 }
        r2.wait();	 Catch:{ InterruptedException -> 0x0034 }
    L_0x0012:
        r2 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x003d }
        r2 = r2.get(r15);	 Catch:{ all -> 0x003d }
        r2 = (com.google.android.mms.util.PduCacheEntry) r2;	 Catch:{ all -> 0x003d }
        if (r2 == 0) goto L_0x0054;
    L_0x001c:
        r2 = r2.getPdu();	 Catch:{ all -> 0x003d }
        monitor-exit(r3);	 Catch:{ all -> 0x003d }
        r3 = PDU_CACHE_INSTANCE;
        monitor-enter(r3);
        r4 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0031 }
        r5 = 0;
        r4.setUpdating(r15, r5);	 Catch:{ all -> 0x0031 }
        r4 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0031 }
        r4.notifyAll();	 Catch:{ all -> 0x0031 }
        monitor-exit(r3);	 Catch:{ all -> 0x0031 }
    L_0x0030:
        return r2;
    L_0x0031:
        r2 = move-exception;
        monitor-exit(r3);	 Catch:{ all -> 0x0031 }
        throw r2;
    L_0x0034:
        r2 = move-exception;
        r4 = "PduPersister";
        r5 = "load: ";
        android.util.Log.e(r4, r5, r2);	 Catch:{ all -> 0x003d }
        goto L_0x0012;
    L_0x003d:
        r2 = move-exception;
    L_0x003e:
        monitor-exit(r3);	 Catch:{ all -> 0x003d }
        throw r2;	 Catch:{ all -> 0x0040 }
    L_0x0040:
        r2 = move-exception;
    L_0x0041:
        r3 = PDU_CACHE_INSTANCE;
        monitor-enter(r3);
        r4 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0051 }
        r5 = 0;
        r4.setUpdating(r15, r5);	 Catch:{ all -> 0x0051 }
        r4 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0051 }
        r4.notifyAll();	 Catch:{ all -> 0x0051 }
        monitor-exit(r3);	 Catch:{ all -> 0x0051 }
        throw r2;
    L_0x0051:
        r2 = move-exception;
        monitor-exit(r3);	 Catch:{ all -> 0x0051 }
        throw r2;
    L_0x0054:
        r2 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0241 }
        r4 = 1;
        r2.setUpdating(r15, r4);	 Catch:{ all -> 0x0241 }
        monitor-exit(r3);	 Catch:{ all -> 0x0241 }
        r2 = r14.mContext;	 Catch:{ all -> 0x00a0 }
        r3 = r14.mContentResolver;	 Catch:{ all -> 0x00a0 }
        r5 = PDU_PROJECTION;	 Catch:{ all -> 0x00a0 }
        r6 = 0;
        r7 = 0;
        r8 = 0;
        r4 = r15;
        r4 = com.google.android.mms.util.SqliteWrapper.query(r2, r3, r4, r5, r6, r7, r8);	 Catch:{ all -> 0x00a0 }
        r5 = new com.google.android.mms.pdu.PduHeaders;	 Catch:{ all -> 0x00a0 }
        r5.<init>();	 Catch:{ all -> 0x00a0 }
        r6 = android.content.ContentUris.parseId(r15);	 Catch:{ all -> 0x00a0 }
        if (r4 == 0) goto L_0x0080;
    L_0x0074:
        r2 = r4.getCount();	 Catch:{ all -> 0x0099 }
        if (r2 != r10) goto L_0x0080;
    L_0x007a:
        r2 = r4.moveToFirst();	 Catch:{ all -> 0x0099 }
        if (r2 != 0) goto L_0x00a2;
    L_0x0080:
        r2 = new com.google.android.mms.MmsException;	 Catch:{ all -> 0x0099 }
        r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0099 }
        r3.<init>();	 Catch:{ all -> 0x0099 }
        r5 = "Bad uri: ";
        r3 = r3.append(r5);	 Catch:{ all -> 0x0099 }
        r3 = r3.append(r15);	 Catch:{ all -> 0x0099 }
        r3 = r3.toString();	 Catch:{ all -> 0x0099 }
        r2.<init>(r3);	 Catch:{ all -> 0x0099 }
        throw r2;	 Catch:{ all -> 0x0099 }
    L_0x0099:
        r2 = move-exception;
        if (r4 == 0) goto L_0x009f;
    L_0x009c:
        r4.close();	 Catch:{ all -> 0x00a0 }
    L_0x009f:
        throw r2;	 Catch:{ all -> 0x00a0 }
    L_0x00a0:
        r2 = move-exception;
        goto L_0x0041;
    L_0x00a2:
        r2 = 1;
        r8 = r4.getInt(r2);	 Catch:{ all -> 0x0099 }
        r2 = 2;
        r10 = r4.getLong(r2);	 Catch:{ all -> 0x0099 }
        r2 = ENCODED_STRING_COLUMN_INDEX_MAP;	 Catch:{ all -> 0x0099 }
        r2 = r2.entrySet();	 Catch:{ all -> 0x0099 }
        r12 = r2.iterator();	 Catch:{ all -> 0x0099 }
    L_0x00b6:
        r2 = r12.hasNext();	 Catch:{ all -> 0x0099 }
        if (r2 == 0) goto L_0x00dc;
    L_0x00bc:
        r2 = r12.next();	 Catch:{ all -> 0x0099 }
        r0 = r2;
        r0 = (java.util.Map.Entry) r0;	 Catch:{ all -> 0x0099 }
        r3 = r0;
        r2 = r3.getValue();	 Catch:{ all -> 0x0099 }
        r2 = (java.lang.Integer) r2;	 Catch:{ all -> 0x0099 }
        r13 = r2.intValue();	 Catch:{ all -> 0x0099 }
        r2 = r3.getKey();	 Catch:{ all -> 0x0099 }
        r2 = (java.lang.Integer) r2;	 Catch:{ all -> 0x0099 }
        r2 = r2.intValue();	 Catch:{ all -> 0x0099 }
        r14.setEncodedStringValueToHeaders(r4, r13, r5, r2);	 Catch:{ all -> 0x0099 }
        goto L_0x00b6;
    L_0x00dc:
        r2 = TEXT_STRING_COLUMN_INDEX_MAP;	 Catch:{ all -> 0x0099 }
        r2 = r2.entrySet();	 Catch:{ all -> 0x0099 }
        r12 = r2.iterator();	 Catch:{ all -> 0x0099 }
    L_0x00e6:
        r2 = r12.hasNext();	 Catch:{ all -> 0x0099 }
        if (r2 == 0) goto L_0x010a;
    L_0x00ec:
        r2 = r12.next();	 Catch:{ all -> 0x0099 }
        r2 = (java.util.Map.Entry) r2;	 Catch:{ all -> 0x0099 }
        r3 = r2.getValue();	 Catch:{ all -> 0x0099 }
        r3 = (java.lang.Integer) r3;	 Catch:{ all -> 0x0099 }
        r3 = r3.intValue();	 Catch:{ all -> 0x0099 }
        r2 = r2.getKey();	 Catch:{ all -> 0x0099 }
        r2 = (java.lang.Integer) r2;	 Catch:{ all -> 0x0099 }
        r2 = r2.intValue();	 Catch:{ all -> 0x0099 }
        r14.setTextStringToHeaders(r4, r3, r5, r2);	 Catch:{ all -> 0x0099 }
        goto L_0x00e6;
    L_0x010a:
        r2 = OCTET_COLUMN_INDEX_MAP;	 Catch:{ all -> 0x0099 }
        r2 = r2.entrySet();	 Catch:{ all -> 0x0099 }
        r12 = r2.iterator();	 Catch:{ all -> 0x0099 }
    L_0x0114:
        r2 = r12.hasNext();	 Catch:{ all -> 0x0099 }
        if (r2 == 0) goto L_0x0138;
    L_0x011a:
        r2 = r12.next();	 Catch:{ all -> 0x0099 }
        r2 = (java.util.Map.Entry) r2;	 Catch:{ all -> 0x0099 }
        r3 = r2.getValue();	 Catch:{ all -> 0x0099 }
        r3 = (java.lang.Integer) r3;	 Catch:{ all -> 0x0099 }
        r3 = r3.intValue();	 Catch:{ all -> 0x0099 }
        r2 = r2.getKey();	 Catch:{ all -> 0x0099 }
        r2 = (java.lang.Integer) r2;	 Catch:{ all -> 0x0099 }
        r2 = r2.intValue();	 Catch:{ all -> 0x0099 }
        r14.setOctetToHeaders(r4, r3, r5, r2);	 Catch:{ all -> 0x0099 }
        goto L_0x0114;
    L_0x0138:
        r2 = LONG_COLUMN_INDEX_MAP;	 Catch:{ all -> 0x0099 }
        r2 = r2.entrySet();	 Catch:{ all -> 0x0099 }
        r12 = r2.iterator();	 Catch:{ all -> 0x0099 }
    L_0x0142:
        r2 = r12.hasNext();	 Catch:{ all -> 0x0099 }
        if (r2 == 0) goto L_0x0166;
    L_0x0148:
        r2 = r12.next();	 Catch:{ all -> 0x0099 }
        r2 = (java.util.Map.Entry) r2;	 Catch:{ all -> 0x0099 }
        r3 = r2.getValue();	 Catch:{ all -> 0x0099 }
        r3 = (java.lang.Integer) r3;	 Catch:{ all -> 0x0099 }
        r3 = r3.intValue();	 Catch:{ all -> 0x0099 }
        r2 = r2.getKey();	 Catch:{ all -> 0x0099 }
        r2 = (java.lang.Integer) r2;	 Catch:{ all -> 0x0099 }
        r2 = r2.intValue();	 Catch:{ all -> 0x0099 }
        r14.setLongToHeaders(r4, r3, r5, r2);	 Catch:{ all -> 0x0099 }
        goto L_0x0142;
    L_0x0166:
        if (r4 == 0) goto L_0x016b;
    L_0x0168:
        r4.close();	 Catch:{ all -> 0x00a0 }
    L_0x016b:
        r2 = -1;
        r2 = (r6 > r2 ? 1 : (r6 == r2 ? 0 : -1));
        if (r2 != 0) goto L_0x0179;
    L_0x0171:
        r2 = new com.google.android.mms.MmsException;	 Catch:{ all -> 0x00a0 }
        r3 = "Error! ID of the message: -1.";
        r2.<init>(r3);	 Catch:{ all -> 0x00a0 }
        throw r2;	 Catch:{ all -> 0x00a0 }
    L_0x0179:
        r14.loadAddress(r6, r5);	 Catch:{ all -> 0x00a0 }
        r2 = 140; // 0x8c float:1.96E-43 double:6.9E-322;
        r3 = r5.getOctet(r2);	 Catch:{ all -> 0x00a0 }
        r4 = new com.google.android.mms.pdu.PduBody;	 Catch:{ all -> 0x00a0 }
        r4.<init>();	 Catch:{ all -> 0x00a0 }
        r2 = 132; // 0x84 float:1.85E-43 double:6.5E-322;
        if (r3 == r2) goto L_0x018f;
    L_0x018b:
        r2 = 128; // 0x80 float:1.794E-43 double:6.32E-322;
        if (r3 != r2) goto L_0x01a1;
    L_0x018f:
        r6 = r14.loadParts(r6);	 Catch:{ all -> 0x00a0 }
        if (r6 == 0) goto L_0x01a1;
    L_0x0195:
        r7 = r6.length;	 Catch:{ all -> 0x00a0 }
        r2 = r9;
    L_0x0197:
        if (r2 >= r7) goto L_0x01a1;
    L_0x0199:
        r9 = r6[r2];	 Catch:{ all -> 0x00a0 }
        r4.addPart(r9);	 Catch:{ all -> 0x00a0 }
        r2 = r2 + 1;
        goto L_0x0197;
    L_0x01a1:
        switch(r3) {
            case 128: goto L_0x01f2;
            case 129: goto L_0x020a;
            case 130: goto L_0x01c1;
            case 131: goto L_0x01fe;
            case 132: goto L_0x01ec;
            case 133: goto L_0x01f8;
            case 134: goto L_0x01e0;
            case 135: goto L_0x0204;
            case 136: goto L_0x01e6;
            case 137: goto L_0x020a;
            case 138: goto L_0x020a;
            case 139: goto L_0x020a;
            case 140: goto L_0x020a;
            case 141: goto L_0x020a;
            case 142: goto L_0x020a;
            case 143: goto L_0x020a;
            case 144: goto L_0x020a;
            case 145: goto L_0x020a;
            case 146: goto L_0x020a;
            case 147: goto L_0x020a;
            case 148: goto L_0x020a;
            case 149: goto L_0x020a;
            case 150: goto L_0x020a;
            case 151: goto L_0x020a;
            default: goto L_0x01a4;
        };	 Catch:{ all -> 0x00a0 }
    L_0x01a4:
        r2 = new com.google.android.mms.MmsException;	 Catch:{ all -> 0x00a0 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00a0 }
        r4.<init>();	 Catch:{ all -> 0x00a0 }
        r5 = "Unrecognized PDU type: ";
        r4 = r4.append(r5);	 Catch:{ all -> 0x00a0 }
        r3 = java.lang.Integer.toHexString(r3);	 Catch:{ all -> 0x00a0 }
        r3 = r4.append(r3);	 Catch:{ all -> 0x00a0 }
        r3 = r3.toString();	 Catch:{ all -> 0x00a0 }
        r2.<init>(r3);	 Catch:{ all -> 0x00a0 }
        throw r2;	 Catch:{ all -> 0x00a0 }
    L_0x01c1:
        r2 = new com.google.android.mms.pdu.NotificationInd;	 Catch:{ all -> 0x00a0 }
        r2.<init>(r5);	 Catch:{ all -> 0x00a0 }
    L_0x01c6:
        r3 = PDU_CACHE_INSTANCE;
        monitor-enter(r3);
        if (r2 == 0) goto L_0x0231;
    L_0x01cb:
        r4 = $assertionsDisabled;	 Catch:{ all -> 0x01dd }
        if (r4 != 0) goto L_0x0227;
    L_0x01cf:
        r4 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x01dd }
        r4 = r4.get(r15);	 Catch:{ all -> 0x01dd }
        if (r4 == 0) goto L_0x0227;
    L_0x01d7:
        r2 = new java.lang.AssertionError;	 Catch:{ all -> 0x01dd }
        r2.<init>();	 Catch:{ all -> 0x01dd }
        throw r2;	 Catch:{ all -> 0x01dd }
    L_0x01dd:
        r2 = move-exception;
    L_0x01de:
        monitor-exit(r3);	 Catch:{ all -> 0x023f }
        throw r2;
    L_0x01e0:
        r2 = new com.google.android.mms.pdu.DeliveryInd;	 Catch:{ all -> 0x00a0 }
        r2.<init>(r5);	 Catch:{ all -> 0x00a0 }
        goto L_0x01c6;
    L_0x01e6:
        r2 = new com.google.android.mms.pdu.ReadOrigInd;	 Catch:{ all -> 0x00a0 }
        r2.<init>(r5);	 Catch:{ all -> 0x00a0 }
        goto L_0x01c6;
    L_0x01ec:
        r2 = new com.google.android.mms.pdu.RetrieveConf;	 Catch:{ all -> 0x00a0 }
        r2.<init>(r5, r4);	 Catch:{ all -> 0x00a0 }
        goto L_0x01c6;
    L_0x01f2:
        r2 = new com.google.android.mms.pdu.SendReq;	 Catch:{ all -> 0x00a0 }
        r2.<init>(r5, r4);	 Catch:{ all -> 0x00a0 }
        goto L_0x01c6;
    L_0x01f8:
        r2 = new com.google.android.mms.pdu.AcknowledgeInd;	 Catch:{ all -> 0x00a0 }
        r2.<init>(r5);	 Catch:{ all -> 0x00a0 }
        goto L_0x01c6;
    L_0x01fe:
        r2 = new com.google.android.mms.pdu.NotifyRespInd;	 Catch:{ all -> 0x00a0 }
        r2.<init>(r5);	 Catch:{ all -> 0x00a0 }
        goto L_0x01c6;
    L_0x0204:
        r2 = new com.google.android.mms.pdu.ReadRecInd;	 Catch:{ all -> 0x00a0 }
        r2.<init>(r5);	 Catch:{ all -> 0x00a0 }
        goto L_0x01c6;
    L_0x020a:
        r2 = new com.google.android.mms.MmsException;	 Catch:{ all -> 0x00a0 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00a0 }
        r4.<init>();	 Catch:{ all -> 0x00a0 }
        r5 = "Unsupported PDU type: ";
        r4 = r4.append(r5);	 Catch:{ all -> 0x00a0 }
        r3 = java.lang.Integer.toHexString(r3);	 Catch:{ all -> 0x00a0 }
        r3 = r4.append(r3);	 Catch:{ all -> 0x00a0 }
        r3 = r3.toString();	 Catch:{ all -> 0x00a0 }
        r2.<init>(r3);	 Catch:{ all -> 0x00a0 }
        throw r2;	 Catch:{ all -> 0x00a0 }
    L_0x0227:
        r4 = new com.google.android.mms.util.PduCacheEntry;	 Catch:{ all -> 0x01dd }
        r4.<init>(r2, r8, r10);	 Catch:{ all -> 0x01dd }
        r5 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x023f }
        r5.put(r15, r4);	 Catch:{ all -> 0x023f }
    L_0x0231:
        r4 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x023f }
        r5 = 0;
        r4.setUpdating(r15, r5);	 Catch:{ all -> 0x023f }
        r4 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x023f }
        r4.notifyAll();	 Catch:{ all -> 0x023f }
        monitor-exit(r3);	 Catch:{ all -> 0x023f }
        goto L_0x0030;
    L_0x023f:
        r2 = move-exception;
        goto L_0x01de;
    L_0x0241:
        r2 = move-exception;
        goto L_0x003e;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.android.mms.pdu.PduPersister.load(android.net.Uri):com.google.android.mms.pdu.GenericPdu");
    }

    public Uri move(Uri uri, Uri uri2) throws MmsException {
        long parseId = ContentUris.parseId(uri);
        if (parseId == -1) {
            throw new MmsException("Error! ID of the message: -1.");
        }
        Integer num = (Integer) MESSAGE_BOX_MAP.get(uri2);
        if (num == null) {
            throw new MmsException("Bad destination, must be one of content://mms/inbox, content://mms/sent, content://mms/drafts, content://mms/outbox, content://mms/temp.");
        }
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(BaseMmsColumns.MESSAGE_BOX, num);
        SqliteWrapper.update(this.mContext, this.mContentResolver, uri, contentValues, null, null);
        return ContentUris.withAppendedId(uri2, parseId);
    }

    /* JADX WARNING: Removed duplicated region for block: B:114:0x02cb  */
    /* JADX WARNING: Removed duplicated region for block: B:99:0x0227  */
    /* JADX WARNING: Removed duplicated region for block: B:102:0x0239  */
    /* JADX WARNING: Removed duplicated region for block: B:115:0x02ce  */
    /* JADX WARNING: Removed duplicated region for block: B:104:0x0244  */
    /* JADX WARNING: Removed duplicated region for block: B:107:0x0290  */
    /* JADX WARNING: Removed duplicated region for block: B:110:0x02b4  */
    public android.net.Uri persist(com.google.android.mms.pdu.GenericPdu r17, android.net.Uri r18, boolean r19, boolean r20, java.util.HashMap<android.net.Uri, java.io.InputStream> r21) throws com.google.android.mms.MmsException {
        /*
        r16 = this;
        if (r18 != 0) goto L_0x000a;
    L_0x0002:
        r2 = new com.google.android.mms.MmsException;
        r3 = "Uri may not be null.";
        r2.<init>(r3);
        throw r2;
    L_0x000a:
        r8 = -1;
        r8 = android.content.ContentUris.parseId(r18);	 Catch:{ NumberFormatException -> 0x02ee }
    L_0x0010:
        r2 = -1;
        r2 = (r8 > r2 ? 1 : (r8 == r2 ? 0 : -1));
        if (r2 == 0) goto L_0x002c;
    L_0x0016:
        r2 = 1;
        r11 = r2;
    L_0x0018:
        if (r11 != 0) goto L_0x002f;
    L_0x001a:
        r2 = MESSAGE_BOX_MAP;
        r0 = r18;
        r2 = r2.get(r0);
        if (r2 != 0) goto L_0x002f;
    L_0x0024:
        r2 = new com.google.android.mms.MmsException;
        r3 = "Bad destination, must be one of content://mms/inbox, content://mms/sent, content://mms/drafts, content://mms/outbox, content://mms/temp.";
        r2.<init>(r3);
        throw r2;
    L_0x002c:
        r2 = 0;
        r11 = r2;
        goto L_0x0018;
    L_0x002f:
        r3 = PDU_CACHE_INSTANCE;
        monitor-enter(r3);
        r2 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x00aa }
        r0 = r18;
        r2 = r2.isUpdating(r0);	 Catch:{ all -> 0x00aa }
        if (r2 == 0) goto L_0x0041;
    L_0x003c:
        r2 = PDU_CACHE_INSTANCE;	 Catch:{ InterruptedException -> 0x00a1 }
        r2.wait();	 Catch:{ InterruptedException -> 0x00a1 }
    L_0x0041:
        monitor-exit(r3);	 Catch:{ all -> 0x00aa }
        r2 = PDU_CACHE_INSTANCE;
        r0 = r18;
        r2.purge(r0);
        r4 = r17.getPduHeaders();
        r5 = new android.content.ContentValues;
        r5.<init>();
        r2 = ENCODED_STRING_COLUMN_NAME_MAP;
        r2 = r2.entrySet();
        r6 = r2.iterator();
    L_0x005c:
        r2 = r6.hasNext();
        if (r2 == 0) goto L_0x00ad;
    L_0x0062:
        r2 = r6.next();
        r2 = (java.util.Map.Entry) r2;
        r3 = r2.getKey();
        r3 = (java.lang.Integer) r3;
        r3 = r3.intValue();
        r7 = r4.getEncodedStringValue(r3);
        if (r7 == 0) goto L_0x005c;
    L_0x0078:
        r10 = CHARSET_COLUMN_NAME_MAP;
        r3 = java.lang.Integer.valueOf(r3);
        r3 = r10.get(r3);
        r3 = (java.lang.String) r3;
        r2 = r2.getValue();
        r2 = (java.lang.String) r2;
        r10 = r7.getTextString();
        r10 = toIsoString(r10);
        r5.put(r2, r10);
        r2 = r7.getCharacterSet();
        r2 = java.lang.Integer.valueOf(r2);
        r5.put(r3, r2);
        goto L_0x005c;
    L_0x00a1:
        r2 = move-exception;
        r4 = "PduPersister";
        r5 = "persist1: ";
        android.util.Log.e(r4, r5, r2);	 Catch:{ all -> 0x00aa }
        goto L_0x0041;
    L_0x00aa:
        r2 = move-exception;
        monitor-exit(r3);	 Catch:{ all -> 0x00aa }
        throw r2;
    L_0x00ad:
        r2 = TEXT_STRING_COLUMN_NAME_MAP;
        r2 = r2.entrySet();
        r6 = r2.iterator();
    L_0x00b7:
        r2 = r6.hasNext();
        if (r2 == 0) goto L_0x00e1;
    L_0x00bd:
        r2 = r6.next();
        r2 = (java.util.Map.Entry) r2;
        r3 = r2.getKey();
        r3 = (java.lang.Integer) r3;
        r3 = r3.intValue();
        r3 = r4.getTextString(r3);
        if (r3 == 0) goto L_0x00b7;
    L_0x00d3:
        r2 = r2.getValue();
        r2 = (java.lang.String) r2;
        r3 = toIsoString(r3);
        r5.put(r2, r3);
        goto L_0x00b7;
    L_0x00e1:
        r2 = OCTET_COLUMN_NAME_MAP;
        r2 = r2.entrySet();
        r6 = r2.iterator();
    L_0x00eb:
        r2 = r6.hasNext();
        if (r2 == 0) goto L_0x0115;
    L_0x00f1:
        r2 = r6.next();
        r2 = (java.util.Map.Entry) r2;
        r3 = r2.getKey();
        r3 = (java.lang.Integer) r3;
        r3 = r3.intValue();
        r3 = r4.getOctet(r3);
        if (r3 == 0) goto L_0x00eb;
    L_0x0107:
        r2 = r2.getValue();
        r2 = (java.lang.String) r2;
        r3 = java.lang.Integer.valueOf(r3);
        r5.put(r2, r3);
        goto L_0x00eb;
    L_0x0115:
        r2 = LONG_COLUMN_NAME_MAP;
        r2 = r2.entrySet();
        r6 = r2.iterator();
    L_0x011f:
        r2 = r6.hasNext();
        if (r2 == 0) goto L_0x014d;
    L_0x0125:
        r2 = r6.next();
        r2 = (java.util.Map.Entry) r2;
        r3 = r2.getKey();
        r3 = (java.lang.Integer) r3;
        r3 = r3.intValue();
        r12 = r4.getLongInteger(r3);
        r14 = -1;
        r3 = (r12 > r14 ? 1 : (r12 == r14 ? 0 : -1));
        if (r3 == 0) goto L_0x011f;
    L_0x013f:
        r2 = r2.getValue();
        r2 = (java.lang.String) r2;
        r3 = java.lang.Long.valueOf(r12);
        r5.put(r2, r3);
        goto L_0x011f;
    L_0x014d:
        r12 = new java.util.HashMap;
        r2 = ADDRESS_FIELDS;
        r2 = r2.length;
        r12.<init>(r2);
        r6 = ADDRESS_FIELDS;
        r7 = r6.length;
        r2 = 0;
        r3 = r2;
    L_0x015a:
        if (r3 >= r7) goto L_0x017f;
    L_0x015c:
        r10 = r6[r3];
        r2 = 0;
        r13 = 137; // 0x89 float:1.92E-43 double:6.77E-322;
        if (r10 != r13) goto L_0x017a;
    L_0x0163:
        r13 = r4.getEncodedStringValue(r10);
        if (r13 == 0) goto L_0x016f;
    L_0x0169:
        r2 = 1;
        r2 = new com.google.android.mms.pdu.EncodedStringValue[r2];
        r14 = 0;
        r2[r14] = r13;
    L_0x016f:
        r10 = java.lang.Integer.valueOf(r10);
        r12.put(r10, r2);
        r2 = r3 + 1;
        r3 = r2;
        goto L_0x015a;
    L_0x017a:
        r2 = r4.getEncodedStringValues(r10);
        goto L_0x016f;
    L_0x017f:
        r4 = new java.util.HashSet;
        r4.<init>();
        r2 = r17.getMessageType();
        r3 = 130; // 0x82 float:1.82E-43 double:6.4E-322;
        if (r2 == r3) goto L_0x0194;
    L_0x018c:
        r3 = 132; // 0x84 float:1.85E-43 double:6.5E-322;
        if (r2 == r3) goto L_0x0194;
    L_0x0190:
        r3 = 128; // 0x80 float:1.794E-43 double:6.32E-322;
        if (r2 != r3) goto L_0x01b2;
    L_0x0194:
        switch(r2) {
            case 128: goto L_0x021a;
            case 129: goto L_0x0197;
            case 130: goto L_0x01fe;
            case 131: goto L_0x0197;
            case 132: goto L_0x01fe;
            default: goto L_0x0197;
        };
    L_0x0197:
        r2 = 0;
        if (r19 == 0) goto L_0x01a9;
    L_0x019b:
        r6 = r4.isEmpty();
        if (r6 != 0) goto L_0x01a9;
    L_0x01a1:
        r0 = r16;
        r2 = r0.mContext;
        r2 = android.provider.Telephony.Threads.getOrCreateThreadId(r2, r4);
    L_0x01a9:
        r4 = "thread_id";
        r2 = java.lang.Long.valueOf(r2);
        r5.put(r4, r2);
    L_0x01b2:
        r14 = java.lang.System.currentTimeMillis();
        r3 = 1;
        r2 = 1;
        r4 = 0;
        r6 = 0;
        r0 = r17;
        r7 = r0 instanceof com.google.android.mms.pdu.MultimediaMessagePdu;
        if (r7 == 0) goto L_0x02f2;
    L_0x01c0:
        r17 = (com.google.android.mms.pdu.MultimediaMessagePdu) r17;
        r7 = r17.getBody();
        if (r7 == 0) goto L_0x02f2;
    L_0x01c8:
        r10 = r7.getPartsNum();
        r3 = 2;
        if (r10 <= r3) goto L_0x01d0;
    L_0x01cf:
        r2 = 0;
    L_0x01d0:
        r4 = 0;
        r3 = r6;
    L_0x01d2:
        if (r4 >= r10) goto L_0x0224;
    L_0x01d4:
        r6 = r7.getPart(r4);
        r13 = r6.getDataLength();
        r3 = r3 + r13;
        r0 = r16;
        r1 = r21;
        r0.persistPart(r6, r14, r1);
        r6 = getPartContentType(r6);
        if (r6 == 0) goto L_0x01fb;
    L_0x01ea:
        r13 = "application/smil";
        r13 = r13.equals(r6);
        if (r13 != 0) goto L_0x01fb;
    L_0x01f2:
        r13 = "text/plain";
        r6 = r13.equals(r6);
        if (r6 != 0) goto L_0x01fb;
    L_0x01fa:
        r2 = 0;
    L_0x01fb:
        r4 = r4 + 1;
        goto L_0x01d2;
    L_0x01fe:
        r2 = 137; // 0x89 float:1.92E-43 double:6.77E-322;
        r3 = 0;
        r0 = r16;
        r0.loadRecipients(r2, r4, r12, r3);
        if (r20 == 0) goto L_0x0197;
    L_0x0208:
        r2 = 151; // 0x97 float:2.12E-43 double:7.46E-322;
        r3 = 1;
        r0 = r16;
        r0.loadRecipients(r2, r4, r12, r3);
        r2 = 130; // 0x82 float:1.82E-43 double:6.4E-322;
        r3 = 1;
        r0 = r16;
        r0.loadRecipients(r2, r4, r12, r3);
        goto L_0x0197;
    L_0x021a:
        r2 = 151; // 0x97 float:2.12E-43 double:7.46E-322;
        r3 = 0;
        r0 = r16;
        r0.loadRecipients(r2, r4, r12, r3);
        goto L_0x0197;
    L_0x0224:
        r4 = r3;
    L_0x0225:
        if (r2 == 0) goto L_0x02cb;
    L_0x0227:
        r2 = 1;
    L_0x0228:
        r3 = "text_only";
        r2 = java.lang.Integer.valueOf(r2);
        r5.put(r3, r2);
        r2 = "m_size";
        r2 = r5.getAsInteger(r2);
        if (r2 != 0) goto L_0x0242;
    L_0x0239:
        r2 = "m_size";
        r3 = java.lang.Integer.valueOf(r4);
        r5.put(r2, r3);
    L_0x0242:
        if (r11 == 0) goto L_0x02ce;
    L_0x0244:
        r0 = r16;
        r2 = r0.mContext;
        r0 = r16;
        r3 = r0.mContentResolver;
        r6 = 0;
        r7 = 0;
        r4 = r18;
        com.google.android.mms.util.SqliteWrapper.update(r2, r3, r4, r5, r6, r7);
        r10 = r18;
    L_0x0255:
        r5 = new android.content.ContentValues;
        r2 = 1;
        r5.<init>(r2);
        r2 = "mid";
        r3 = java.lang.Long.valueOf(r8);
        r5.put(r2, r3);
        r0 = r16;
        r2 = r0.mContext;
        r0 = r16;
        r3 = r0.mContentResolver;
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r6 = "content://mms/";
        r4 = r4.append(r6);
        r4 = r4.append(r14);
        r6 = "/part";
        r4 = r4.append(r6);
        r4 = r4.toString();
        r4 = android.net.Uri.parse(r4);
        r6 = 0;
        r7 = 0;
        com.google.android.mms.util.SqliteWrapper.update(r2, r3, r4, r5, r6, r7);
        if (r11 != 0) goto L_0x02ad;
    L_0x0290:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r0 = r18;
        r2 = r2.append(r0);
        r3 = "/";
        r2 = r2.append(r3);
        r2 = r2.append(r8);
        r2 = r2.toString();
        r10 = android.net.Uri.parse(r2);
    L_0x02ad:
        r4 = ADDRESS_FIELDS;
        r5 = r4.length;
        r2 = 0;
        r3 = r2;
    L_0x02b2:
        if (r3 >= r5) goto L_0x02f1;
    L_0x02b4:
        r6 = r4[r3];
        r2 = java.lang.Integer.valueOf(r6);
        r2 = r12.get(r2);
        r2 = (com.google.android.mms.pdu.EncodedStringValue[]) r2;
        if (r2 == 0) goto L_0x02c7;
    L_0x02c2:
        r0 = r16;
        r0.persistAddress(r8, r6, r2);
    L_0x02c7:
        r2 = r3 + 1;
        r3 = r2;
        goto L_0x02b2;
    L_0x02cb:
        r2 = 0;
        goto L_0x0228;
    L_0x02ce:
        r0 = r16;
        r2 = r0.mContext;
        r0 = r16;
        r3 = r0.mContentResolver;
        r0 = r18;
        r4 = com.google.android.mms.util.SqliteWrapper.insert(r2, r3, r0, r5);
        if (r4 != 0) goto L_0x02e6;
    L_0x02de:
        r2 = new com.google.android.mms.MmsException;
        r3 = "persist() failed: return null.";
        r2.<init>(r3);
        throw r2;
    L_0x02e6:
        r2 = android.content.ContentUris.parseId(r4);
        r8 = r2;
        r10 = r4;
        goto L_0x0255;
    L_0x02ee:
        r2 = move-exception;
        goto L_0x0010;
    L_0x02f1:
        return r10;
    L_0x02f2:
        r2 = r3;
        goto L_0x0225;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.android.mms.pdu.PduPersister.persist(com.google.android.mms.pdu.GenericPdu, android.net.Uri, boolean, boolean, java.util.HashMap):android.net.Uri");
    }

    public Uri persistPart(PduPart pduPart, long j, HashMap<Uri, InputStream> hashMap) throws MmsException {
        Uri parse = Uri.parse("content://mms/" + j + "/part");
        ContentValues contentValues = new ContentValues(8);
        int charset = pduPart.getCharset();
        if (charset != 0) {
            contentValues.put(Part.CHARSET, Integer.valueOf(charset));
        }
        String partContentType = getPartContentType(pduPart);
        if (partContentType != null) {
            String str = ContentType.IMAGE_JPG.equals(partContentType) ? ContentType.IMAGE_JPEG : partContentType;
            contentValues.put(Part.CONTENT_TYPE, str);
            if (ContentType.APP_SMIL.equals(str)) {
                contentValues.put(Part.SEQ, Integer.valueOf(-1));
            }
            if (pduPart.getFilename() != null) {
                contentValues.put(Part.FILENAME, new String(pduPart.getFilename()));
            }
            if (pduPart.getName() != null) {
                contentValues.put("name", new String(pduPart.getName()));
            }
            if (pduPart.getContentDisposition() != null) {
                contentValues.put(Part.CONTENT_DISPOSITION, toIsoString(pduPart.getContentDisposition()));
            }
            if (pduPart.getContentId() != null) {
                contentValues.put("cid", toIsoString(pduPart.getContentId()));
            }
            if (pduPart.getContentLocation() != null) {
                contentValues.put(Part.CONTENT_LOCATION, toIsoString(pduPart.getContentLocation()));
            }
            Uri insert = SqliteWrapper.insert(this.mContext, this.mContentResolver, parse, contentValues);
            if (insert == null) {
                throw new MmsException("Failed to persist part, return null.");
            }
            persistData(pduPart, insert, str, hashMap);
            pduPart.setDataUri(insert);
            return insert;
        }
        throw new MmsException("MIME type of the part must be set.");
    }

    public void release() {
        SqliteWrapper.delete(this.mContext, this.mContentResolver, Uri.parse(TEMPORARY_DRM_OBJECT_URI), null, null);
    }

    public void updateHeaders(Uri uri, SendReq sendReq) {
        synchronized (PDU_CACHE_INSTANCE) {
            if (PDU_CACHE_INSTANCE.isUpdating(uri)) {
                try {
                    PDU_CACHE_INSTANCE.wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "updateHeaders: ", e);
                }
            }
        }
        PDU_CACHE_INSTANCE.purge(uri);
        ContentValues contentValues = new ContentValues(10);
        byte[] contentType = sendReq.getContentType();
        if (contentType != null) {
            contentValues.put(BaseMmsColumns.CONTENT_TYPE, toIsoString(contentType));
        }
        long date = sendReq.getDate();
        if (date != -1) {
            contentValues.put("date", Long.valueOf(date));
        }
        int deliveryReport = sendReq.getDeliveryReport();
        if (deliveryReport != 0) {
            contentValues.put(BaseMmsColumns.DELIVERY_REPORT, Integer.valueOf(deliveryReport));
        }
        date = sendReq.getExpiry();
        if (date != -1) {
            contentValues.put(BaseMmsColumns.EXPIRY, Long.valueOf(date));
        }
        contentType = sendReq.getMessageClass();
        if (contentType != null) {
            contentValues.put(BaseMmsColumns.MESSAGE_CLASS, toIsoString(contentType));
        }
        deliveryReport = sendReq.getPriority();
        if (deliveryReport != 0) {
            contentValues.put(BaseMmsColumns.PRIORITY, Integer.valueOf(deliveryReport));
        }
        deliveryReport = sendReq.getReadReport();
        if (deliveryReport != 0) {
            contentValues.put(BaseMmsColumns.READ_REPORT, Integer.valueOf(deliveryReport));
        }
        contentType = sendReq.getTransactionId();
        if (contentType != null) {
            contentValues.put(BaseMmsColumns.TRANSACTION_ID, toIsoString(contentType));
        }
        EncodedStringValue subject = sendReq.getSubject();
        if (subject != null) {
            contentValues.put(BaseMmsColumns.SUBJECT, toIsoString(subject.getTextString()));
            contentValues.put(BaseMmsColumns.SUBJECT_CHARSET, Integer.valueOf(subject.getCharacterSet()));
        } else {
            contentValues.put(BaseMmsColumns.SUBJECT, "");
        }
        date = sendReq.getMessageSize();
        if (date > 0) {
            contentValues.put(BaseMmsColumns.MESSAGE_SIZE, Long.valueOf(date));
        }
        PduHeaders pduHeaders = sendReq.getPduHeaders();
        Set hashSet = new HashSet();
        for (int i : ADDRESS_FIELDS) {
            EncodedStringValue[] encodedStringValueArr;
            if (i == 137) {
                if (pduHeaders.getEncodedStringValue(i) != null) {
                    encodedStringValueArr = new EncodedStringValue[]{pduHeaders.getEncodedStringValue(i)};
                } else {
                    encodedStringValueArr = null;
                }
            } else {
                encodedStringValueArr = pduHeaders.getEncodedStringValues(i);
            }
            if (encodedStringValueArr != null) {
                updateAddress(ContentUris.parseId(uri), i, encodedStringValueArr);
                if (i == 151) {
                    for (EncodedStringValue encodedStringValue : encodedStringValueArr) {
                        if (encodedStringValue != null) {
                            hashSet.add(encodedStringValue.getString());
                        }
                    }
                }
            }
        }
        if (!hashSet.isEmpty()) {
            contentValues.put("thread_id", Long.valueOf(Threads.getOrCreateThreadId(this.mContext, hashSet)));
        }
        SqliteWrapper.update(this.mContext, this.mContentResolver, uri, contentValues, null, null);
    }

    public void updateParts(Uri uri, PduBody pduBody, HashMap<Uri, InputStream> hashMap) throws MmsException {
        Iterator it;
        try {
            synchronized (PDU_CACHE_INSTANCE) {
                if (PDU_CACHE_INSTANCE.isUpdating(uri)) {
                    try {
                        PDU_CACHE_INSTANCE.wait();
                    } catch (InterruptedException e) {
                        Log.e(TAG, "updateParts: ", e);
                    }
                    PduCacheEntry pduCacheEntry = (PduCacheEntry) PDU_CACHE_INSTANCE.get(uri);
                    if (pduCacheEntry != null) {
                        ((MultimediaMessagePdu) pduCacheEntry.getPdu()).setBody(pduBody);
                    }
                }
                PDU_CACHE_INSTANCE.setUpdating(uri, true);
            }
            ArrayList arrayList = new ArrayList();
            HashMap hashMap2 = new HashMap();
            int partsNum = pduBody.getPartsNum();
            StringBuilder append = new StringBuilder().append('(');
            for (int i = 0; i < partsNum; i++) {
                PduPart part = pduBody.getPart(i);
                Uri dataUri = part.getDataUri();
                if (dataUri == null || TextUtils.isEmpty(dataUri.getAuthority()) || !dataUri.getAuthority().startsWith("mms")) {
                    arrayList.add(part);
                } else {
                    hashMap2.put(dataUri, part);
                    if (append.length() > 1) {
                        append.append(" AND ");
                    }
                    append.append("_id");
                    append.append("!=");
                    DatabaseUtils.appendEscapedSQLString(append, dataUri.getLastPathSegment());
                }
            }
            append.append(')');
            long parseId = ContentUris.parseId(uri);
            SqliteWrapper.delete(this.mContext, this.mContentResolver, Uri.parse(Mms.CONTENT_URI + "/" + parseId + "/part"), append.length() > 2 ? append.toString() : null, null);
            it = arrayList.iterator();
            while (it.hasNext()) {
                persistPart((PduPart) it.next(), parseId, hashMap);
            }
            for (Entry entry : hashMap2.entrySet()) {
                Uri uri2 = (Uri) entry.getKey();
                PduPart it2 = (PduPart) entry.getValue();
                updatePart(uri2, it2, hashMap);
            }
            PDU_CACHE_INSTANCE.setUpdating(uri, false);
            PDU_CACHE_INSTANCE.notifyAll();
        } finally {
            it2 = PDU_CACHE_INSTANCE;
            synchronized (it2) {
                PDU_CACHE_INSTANCE.setUpdating(uri, false);
                PDU_CACHE_INSTANCE.notifyAll();
            }
        }
    }
}
