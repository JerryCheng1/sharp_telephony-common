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
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.gsm.SmsCbConstants;
import com.google.android.mms.ContentType;
import com.google.android.mms.InvalidHeaderValueException;
import com.google.android.mms.MmsException;
import com.google.android.mms.util.DownloadDrmHelper;
import com.google.android.mms.util.DrmConvertSession;
import com.google.android.mms.util.PduCache;
import com.google.android.mms.util.PduCacheEntry;
import com.google.android.mms.util.SqliteWrapper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

public class PduPersister {
    /* renamed from: -assertionsDisabled */
    static final /* synthetic */ boolean f31-assertionsDisabled;
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
        boolean z;
        if (PduPersister.class.desiredAssertionStatus()) {
            z = false;
        } else {
            z = true;
        }
        f31-assertionsDisabled = z;
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

    public static PduPersister getPduPersister(Context context) {
        if (sPersister == null) {
            sPersister = new PduPersister(context);
        } else if (!context.equals(sPersister.mContext)) {
            sPersister.release();
            sPersister = new PduPersister(context);
        }
        return sPersister;
    }

    private void setEncodedStringValueToHeaders(Cursor c, int columnIndex, PduHeaders headers, int mapColumn) {
        String s = c.getString(columnIndex);
        if (s != null && s.length() > 0) {
            headers.setEncodedStringValue(new EncodedStringValue(c.getInt(((Integer) CHARSET_COLUMN_INDEX_MAP.get(Integer.valueOf(mapColumn))).intValue()), getBytes(s)), mapColumn);
        }
    }

    private void setTextStringToHeaders(Cursor c, int columnIndex, PduHeaders headers, int mapColumn) {
        String s = c.getString(columnIndex);
        if (s != null) {
            headers.setTextString(getBytes(s), mapColumn);
        }
    }

    private void setOctetToHeaders(Cursor c, int columnIndex, PduHeaders headers, int mapColumn) throws InvalidHeaderValueException {
        if (!c.isNull(columnIndex)) {
            headers.setOctet(c.getInt(columnIndex), mapColumn);
        }
    }

    private void setLongToHeaders(Cursor c, int columnIndex, PduHeaders headers, int mapColumn) {
        if (!c.isNull(columnIndex)) {
            headers.setLongInteger(c.getLong(columnIndex), mapColumn);
        }
    }

    private Integer getIntegerFromPartColumn(Cursor c, int columnIndex) {
        if (c.isNull(columnIndex)) {
            return null;
        }
        return Integer.valueOf(c.getInt(columnIndex));
    }

    private byte[] getByteArrayFromPartColumn(Cursor c, int columnIndex) {
        if (c.isNull(columnIndex)) {
            return null;
        }
        return getBytes(c.getString(columnIndex));
    }

    private PduPart[] loadParts(long msgId) throws MmsException {
        Cursor c = SqliteWrapper.query(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + msgId + "/part"), PART_PROJECTION, null, null, null);
        if (c != null) {
            try {
                if (c.getCount() != 0) {
                    PduPart[] parts = new PduPart[c.getCount()];
                    int partIdx = 0;
                    while (c.moveToNext()) {
                        PduPart part = new PduPart();
                        Integer charset = getIntegerFromPartColumn(c, 1);
                        if (charset != null) {
                            part.setCharset(charset.intValue());
                        }
                        byte[] contentDisposition = getByteArrayFromPartColumn(c, 2);
                        if (contentDisposition != null) {
                            part.setContentDisposition(contentDisposition);
                        }
                        byte[] contentId = getByteArrayFromPartColumn(c, 3);
                        if (contentId != null) {
                            part.setContentId(contentId);
                        }
                        byte[] contentLocation = getByteArrayFromPartColumn(c, 4);
                        if (contentLocation != null) {
                            part.setContentLocation(contentLocation);
                        }
                        byte[] contentType = getByteArrayFromPartColumn(c, 5);
                        if (contentType != null) {
                            part.setContentType(contentType);
                            byte[] fileName = getByteArrayFromPartColumn(c, 6);
                            if (fileName != null) {
                                part.setFilename(fileName);
                            }
                            byte[] name = getByteArrayFromPartColumn(c, 7);
                            if (name != null) {
                                part.setName(name);
                            }
                            Uri partURI = Uri.parse("content://mms/part/" + c.getLong(0));
                            part.setDataUri(partURI);
                            String type = toIsoString(contentType);
                            if (!(ContentType.isImageType(type) || ContentType.isAudioType(type))) {
                                if (!ContentType.isVideoType(type)) {
                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    InputStream inputStream = null;
                                    if (ContentType.TEXT_PLAIN.equals(type) || ContentType.APP_SMIL.equals(type) || ContentType.TEXT_HTML.equals(type)) {
                                        String text = c.getString(8);
                                        if (text == null) {
                                            text = "";
                                        }
                                        byte[] blob = new EncodedStringValue(text).getTextString();
                                        baos.write(blob, 0, blob.length);
                                    } else {
                                        try {
                                            inputStream = this.mContentResolver.openInputStream(partURI);
                                            byte[] buffer = new byte[256];
                                            for (int len = inputStream.read(buffer); len >= 0; len = inputStream.read(buffer)) {
                                                baos.write(buffer, 0, len);
                                            }
                                            if (inputStream != null) {
                                                inputStream.close();
                                            }
                                        } catch (IOException e) {
                                            Log.e(TAG, "Failed to load part data", e);
                                            c.close();
                                            throw new MmsException(e);
                                        } catch (Throwable th) {
                                            if (inputStream != null) {
                                                try {
                                                    inputStream.close();
                                                } catch (IOException e2) {
                                                    Log.e(TAG, "Failed to close stream", e2);
                                                }
                                            }
                                        }
                                    }
                                    part.setData(baos.toByteArray());
                                }
                            }
                            int partIdx2 = partIdx + 1;
                            parts[partIdx] = part;
                            partIdx = partIdx2;
                        } else {
                            throw new MmsException("Content-Type must be set.");
                        }
                    }
                    if (c != null) {
                        c.close();
                    }
                    return parts;
                }
            } catch (IOException e22) {
                Log.e(TAG, "Failed to close stream", e22);
            } catch (Throwable th2) {
                if (c != null) {
                    c.close();
                }
            }
        }
        if (c != null) {
            c.close();
        }
        return null;
    }

    private void loadAddress(long msgId, PduHeaders headers) {
        Cursor c = SqliteWrapper.query(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + msgId + "/addr"), new String[]{"address", Addr.CHARSET, "type"}, null, null, null);
        if (c != null) {
            while (c.moveToNext()) {
                try {
                    String addr = c.getString(0);
                    if (!TextUtils.isEmpty(addr)) {
                        int addrType = c.getInt(2);
                        switch (addrType) {
                            case 129:
                            case 130:
                            case 151:
                                headers.appendEncodedStringValue(new EncodedStringValue(c.getInt(1), getBytes(addr)), addrType);
                                break;
                            case 137:
                                headers.setEncodedStringValue(new EncodedStringValue(c.getInt(1), getBytes(addr)), addrType);
                                break;
                            default:
                                Log.e(TAG, "Unknown address type: " + addrType);
                                break;
                        }
                    }
                } finally {
                    c.close();
                }
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:31:0x0050 A:{SYNTHETIC} */
    /* JADX WARNING: Missing block: B:43:0x006c, code skipped:
            r12 = com.google.android.mms.util.SqliteWrapper.query(r30.mContext, r30.mContentResolver, r31, PDU_PROJECTION, null, null, null);
            r18 = new com.google.android.mms.pdu.PduHeaders();
            r22 = android.content.ContentUris.parseId(r31);
     */
    /* JADX WARNING: Missing block: B:44:0x0088, code skipped:
            if (r12 == null) goto L_0x0091;
     */
    /* JADX WARNING: Missing block: B:47:0x008f, code skipped:
            if (r12.getCount() == 1) goto L_0x00b7;
     */
    /* JADX WARNING: Missing block: B:49:0x00ac, code skipped:
            throw new com.google.android.mms.MmsException("Bad uri: " + r31);
     */
    /* JADX WARNING: Missing block: B:59:0x00bb, code skipped:
            if (r12.moveToFirst() == false) goto L_0x0091;
     */
    /* JADX WARNING: Missing block: B:60:0x00bd, code skipped:
            r20 = r12.getInt(1);
            r28 = r12.getLong(2);
            r17 = ENCODED_STRING_COLUMN_INDEX_MAP.entrySet().iterator();
     */
    /* JADX WARNING: Missing block: B:62:0x00d5, code skipped:
            if (r17.hasNext() == false) goto L_0x00f9;
     */
    /* JADX WARNING: Missing block: B:63:0x00d7, code skipped:
            r16 = (java.util.Map.Entry) r17.next();
            setEncodedStringValueToHeaders(r12, ((java.lang.Integer) r16.getValue()).intValue(), r18, ((java.lang.Integer) r16.getKey()).intValue());
     */
    /* JADX WARNING: Missing block: B:64:0x00f9, code skipped:
            r17 = TEXT_STRING_COLUMN_INDEX_MAP.entrySet().iterator();
     */
    /* JADX WARNING: Missing block: B:66:0x0107, code skipped:
            if (r17.hasNext() == false) goto L_0x012b;
     */
    /* JADX WARNING: Missing block: B:67:0x0109, code skipped:
            r16 = (java.util.Map.Entry) r17.next();
            setTextStringToHeaders(r12, ((java.lang.Integer) r16.getValue()).intValue(), r18, ((java.lang.Integer) r16.getKey()).intValue());
     */
    /* JADX WARNING: Missing block: B:68:0x012b, code skipped:
            r17 = OCTET_COLUMN_INDEX_MAP.entrySet().iterator();
     */
    /* JADX WARNING: Missing block: B:70:0x0139, code skipped:
            if (r17.hasNext() == false) goto L_0x015d;
     */
    /* JADX WARNING: Missing block: B:71:0x013b, code skipped:
            r16 = (java.util.Map.Entry) r17.next();
            setOctetToHeaders(r12, ((java.lang.Integer) r16.getValue()).intValue(), r18, ((java.lang.Integer) r16.getKey()).intValue());
     */
    /* JADX WARNING: Missing block: B:72:0x015d, code skipped:
            r17 = LONG_COLUMN_INDEX_MAP.entrySet().iterator();
     */
    /* JADX WARNING: Missing block: B:74:0x016b, code skipped:
            if (r17.hasNext() == false) goto L_0x018f;
     */
    /* JADX WARNING: Missing block: B:75:0x016d, code skipped:
            r16 = (java.util.Map.Entry) r17.next();
            setLongToHeaders(r12, ((java.lang.Integer) r16.getValue()).intValue(), r18, ((java.lang.Integer) r16.getKey()).intValue());
     */
    /* JADX WARNING: Missing block: B:77:0x018f, code skipped:
            if (r12 == null) goto L_0x0194;
     */
    /* JADX WARNING: Missing block: B:79:?, code skipped:
            r12.close();
     */
    /* JADX WARNING: Missing block: B:81:0x0198, code skipped:
            if (r22 != -1) goto L_0x01a3;
     */
    /* JADX WARNING: Missing block: B:83:0x01a2, code skipped:
            throw new com.google.android.mms.MmsException("Error! ID of the message: -1.");
     */
    /* JADX WARNING: Missing block: B:84:0x01a3, code skipped:
            loadAddress(r22, r18);
            r21 = r18.getOctet(140);
            r11 = new com.google.android.mms.pdu.PduBody();
     */
    /* JADX WARNING: Missing block: B:85:0x01bd, code skipped:
            if (r21 == 132) goto L_0x01c5;
     */
    /* JADX WARNING: Missing block: B:87:0x01c3, code skipped:
            if (r21 != 128) goto L_0x01e4;
     */
    /* JADX WARNING: Missing block: B:88:0x01c5, code skipped:
            r24 = loadParts(r22);
     */
    /* JADX WARNING: Missing block: B:89:0x01cd, code skipped:
            if (r24 == null) goto L_0x01e4;
     */
    /* JADX WARNING: Missing block: B:90:0x01cf, code skipped:
            r25 = r24.length;
            r19 = 0;
     */
    /* JADX WARNING: Missing block: B:92:0x01da, code skipped:
            if (r19 >= r25) goto L_0x01e4;
     */
    /* JADX WARNING: Missing block: B:93:0x01dc, code skipped:
            r11.addPart(r24[r19]);
            r19 = r19 + 1;
     */
    /* JADX WARNING: Missing block: B:94:0x01e4, code skipped:
            switch(r21) {
                case 128: goto L_0x024c;
                case 129: goto L_0x0274;
                case 130: goto L_0x0205;
                case 131: goto L_0x0260;
                case 132: goto L_0x0242;
                case 133: goto L_0x0256;
                case 134: goto L_0x022e;
                case 135: goto L_0x026a;
                case 136: goto L_0x0238;
                case 137: goto L_0x0274;
                case 138: goto L_0x0274;
                case 139: goto L_0x0274;
                case 140: goto L_0x0274;
                case 141: goto L_0x0274;
                case 142: goto L_0x0274;
                case 143: goto L_0x0274;
                case 144: goto L_0x0274;
                case 145: goto L_0x0274;
                case 146: goto L_0x0274;
                case 147: goto L_0x0274;
                case 148: goto L_0x0274;
                case 149: goto L_0x0274;
                case 150: goto L_0x0274;
                case 151: goto L_0x0274;
                default: goto L_0x01e7;
            };
     */
    /* JADX WARNING: Missing block: B:96:0x0204, code skipped:
            throw new com.google.android.mms.MmsException("Unrecognized PDU type: " + java.lang.Integer.toHexString(r21));
     */
    /* JADX WARNING: Missing block: B:97:0x0205, code skipped:
            r0 = new com.google.android.mms.pdu.NotificationInd(r18);
     */
    /* JADX WARNING: Missing block: B:98:0x020e, code skipped:
            r5 = PDU_CACHE_INSTANCE;
     */
    /* JADX WARNING: Missing block: B:99:0x0210, code skipped:
            monitor-enter(r5);
     */
    /* JADX WARNING: Missing block: B:100:0x0211, code skipped:
            if (r26 == null) goto L_0x02bf;
     */
    /* JADX WARNING: Missing block: B:103:0x0215, code skipped:
            if (f31-assertionsDisabled != false) goto L_0x0294;
     */
    /* JADX WARNING: Missing block: B:105:0x021f, code skipped:
            if (PDU_CACHE_INSTANCE.get(r31) != null) goto L_0x0292;
     */
    /* JADX WARNING: Missing block: B:106:0x0221, code skipped:
            r4 = 1;
     */
    /* JADX WARNING: Missing block: B:107:0x0222, code skipped:
            if (r4 != null) goto L_0x0294;
     */
    /* JADX WARNING: Missing block: B:109:0x0229, code skipped:
            throw new java.lang.AssertionError();
     */
    /* JADX WARNING: Missing block: B:110:0x022a, code skipped:
            r4 = th;
     */
    /* JADX WARNING: Missing block: B:111:0x022b, code skipped:
            r13 = r14;
     */
    /* JADX WARNING: Missing block: B:112:0x022c, code skipped:
            monitor-exit(r5);
     */
    /* JADX WARNING: Missing block: B:113:0x022d, code skipped:
            throw r4;
     */
    /* JADX WARNING: Missing block: B:115:?, code skipped:
            r0 = new com.google.android.mms.pdu.DeliveryInd(r18);
     */
    /* JADX WARNING: Missing block: B:116:0x0238, code skipped:
            r0 = new com.google.android.mms.pdu.ReadOrigInd(r18);
     */
    /* JADX WARNING: Missing block: B:117:0x0242, code skipped:
            r0 = new com.google.android.mms.pdu.RetrieveConf(r18, r11);
     */
    /* JADX WARNING: Missing block: B:118:0x024c, code skipped:
            r0 = new com.google.android.mms.pdu.SendReq(r18, r11);
     */
    /* JADX WARNING: Missing block: B:119:0x0256, code skipped:
            r0 = new com.google.android.mms.pdu.AcknowledgeInd(r18);
     */
    /* JADX WARNING: Missing block: B:120:0x0260, code skipped:
            r0 = new com.google.android.mms.pdu.NotifyRespInd(r18);
     */
    /* JADX WARNING: Missing block: B:121:0x026a, code skipped:
            r0 = new com.google.android.mms.pdu.ReadRecInd(r18);
     */
    /* JADX WARNING: Missing block: B:123:0x0291, code skipped:
            throw new com.google.android.mms.MmsException("Unsupported PDU type: " + java.lang.Integer.toHexString(r21));
     */
    /* JADX WARNING: Missing block: B:124:0x0292, code skipped:
            r4 = null;
     */
    /* JADX WARNING: Missing block: B:128:?, code skipped:
            PDU_CACHE_INSTANCE.put(r31, new com.google.android.mms.util.PduCacheEntry(r26, r20, r28));
     */
    /* JADX WARNING: Missing block: B:129:0x02a6, code skipped:
            PDU_CACHE_INSTANCE.setUpdating(r31, false);
            PDU_CACHE_INSTANCE.notifyAll();
     */
    /* JADX WARNING: Missing block: B:130:0x02b3, code skipped:
            monitor-exit(r5);
     */
    /* JADX WARNING: Missing block: B:131:0x02b4, code skipped:
            return r26;
     */
    /* JADX WARNING: Missing block: B:135:0x02b8, code skipped:
            r4 = th;
     */
    /* JADX WARNING: Missing block: B:138:0x02bf, code skipped:
            r13 = r14;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public GenericPdu load(Uri uri) throws MmsException {
        Throwable th;
        PduCacheEntry cacheEntry = null;
        try {
            synchronized (PDU_CACHE_INSTANCE) {
                try {
                    if (PDU_CACHE_INSTANCE.isUpdating(uri)) {
                        PDU_CACHE_INSTANCE.wait();
                        cacheEntry = (PduCacheEntry) PDU_CACHE_INSTANCE.get(uri);
                        if (cacheEntry != null) {
                            GenericPdu pdu = cacheEntry.getPdu();
                            synchronized (PDU_CACHE_INSTANCE) {
                                PDU_CACHE_INSTANCE.setUpdating(uri, false);
                                PDU_CACHE_INSTANCE.notifyAll();
                            }
                            return pdu;
                        }
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "load: ", e);
                } catch (Throwable th2) {
                    th = th2;
                }
                PduCacheEntry cacheEntry2 = cacheEntry;
                try {
                    PDU_CACHE_INSTANCE.setUpdating(uri, true);
                } catch (Throwable th3) {
                    th = th3;
                    cacheEntry = cacheEntry2;
                    throw th;
                }
                try {
                } catch (Throwable th4) {
                    th = th4;
                    cacheEntry = cacheEntry2;
                    synchronized (PDU_CACHE_INSTANCE) {
                        PDU_CACHE_INSTANCE.setUpdating(uri, false);
                        PDU_CACHE_INSTANCE.notifyAll();
                    }
                    throw th;
                }
            }
        } catch (Throwable th5) {
            th = th5;
            synchronized (PDU_CACHE_INSTANCE) {
            }
            throw th;
        }
    }

    private void persistAddress(long msgId, int type, EncodedStringValue[] array) {
        ContentValues values = new ContentValues(3);
        for (EncodedStringValue addr : array) {
            values.clear();
            values.put("address", toIsoString(addr.getTextString()));
            values.put(Addr.CHARSET, Integer.valueOf(addr.getCharacterSet()));
            values.put("type", Integer.valueOf(type));
            SqliteWrapper.insert(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + msgId + "/addr"), values);
        }
    }

    private static String getPartContentType(PduPart part) {
        return part.getContentType() == null ? null : toIsoString(part.getContentType());
    }

    public Uri persistPart(PduPart part, long msgId, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        Uri uri = Uri.parse("content://mms/" + msgId + "/part");
        ContentValues values = new ContentValues(8);
        int charset = part.getCharset();
        if (charset != 0) {
            values.put(Part.CHARSET, Integer.valueOf(charset));
        }
        String contentType = getPartContentType(part);
        if (contentType != null) {
            if (ContentType.IMAGE_JPG.equals(contentType)) {
                contentType = ContentType.IMAGE_JPEG;
            }
            values.put(Part.CONTENT_TYPE, contentType);
            if (ContentType.APP_SMIL.equals(contentType)) {
                values.put(Part.SEQ, Integer.valueOf(-1));
            }
            if (part.getFilename() != null) {
                values.put(Part.FILENAME, new String(part.getFilename()));
            }
            if (part.getName() != null) {
                values.put("name", new String(part.getName()));
            }
            if (part.getContentDisposition() != null) {
                values.put(Part.CONTENT_DISPOSITION, toIsoString(part.getContentDisposition()));
            }
            if (part.getContentId() != null) {
                values.put("cid", toIsoString(part.getContentId()));
            }
            if (part.getContentLocation() != null) {
                values.put(Part.CONTENT_LOCATION, toIsoString(part.getContentLocation()));
            }
            Uri res = SqliteWrapper.insert(this.mContext, this.mContentResolver, uri, values);
            if (res == null) {
                throw new MmsException("Failed to persist part, return null.");
            }
            persistData(part, res, contentType, preOpenedFiles);
            part.setDataUri(res);
            return res;
        }
        throw new MmsException("MIME type of the part must be set.");
    }

    private void persistData(PduPart part, Uri uri, String contentType, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        OutputStream os = null;
        InputStream is = null;
        DrmConvertSession drmConvertSession = null;
        String str = null;
        File file;
        try {
            byte[] data = part.getData();
            if (ContentType.TEXT_PLAIN.equals(contentType) || ContentType.APP_SMIL.equals(contentType) || ContentType.TEXT_HTML.equals(contentType)) {
                ContentValues cv = new ContentValues();
                if (data == null) {
                    data = new String("").getBytes("utf-8");
                }
                cv.put(Part.TEXT, new EncodedStringValue(data).getString());
                if (this.mContentResolver.update(uri, cv, null, null) != 1) {
                    throw new MmsException("unable to update " + uri.toString());
                }
            }
            boolean isDrm = DownloadDrmHelper.isDrmConvertNeeded(contentType);
            if (isDrm) {
                if (uri != null) {
                    try {
                        str = convertUriToPath(this.mContext, uri);
                        if (new File(str).length() > 0) {
                            return;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Can't get file info for: " + part.getDataUri(), e);
                    }
                }
                drmConvertSession = DrmConvertSession.open(this.mContext, contentType);
                if (drmConvertSession == null) {
                    throw new MmsException("Mimetype " + contentType + " can not be converted.");
                }
            }
            os = this.mContentResolver.openOutputStream(uri);
            Uri dataUri;
            byte[] convertedData;
            if (data == null) {
                dataUri = part.getDataUri();
                if (dataUri != null && dataUri != uri) {
                    if (preOpenedFiles != null) {
                        if (preOpenedFiles.containsKey(dataUri)) {
                            is = (InputStream) preOpenedFiles.get(dataUri);
                        }
                    }
                    if (is == null) {
                        is = this.mContentResolver.openInputStream(dataUri);
                    }
                    byte[] buffer = new byte[SmsCbConstants.SERIAL_NUMBER_ETWS_EMERGENCY_USER_ALERT];
                    while (true) {
                        int len = is.read(buffer);
                        if (len == -1) {
                            break;
                        } else if (isDrm) {
                            convertedData = drmConvertSession.convert(buffer, len);
                            if (convertedData != null) {
                                os.write(convertedData, 0, convertedData.length);
                            } else {
                                throw new MmsException("Error converting drm data.");
                            }
                        } else {
                            os.write(buffer, 0, len);
                        }
                    }
                } else {
                    Log.w(TAG, "Can't find data for this part.");
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException e2) {
                            Log.e(TAG, "IOException while closing: " + os, e2);
                        }
                    }
                    if (drmConvertSession != null) {
                        drmConvertSession.close(str);
                        file = new File(str);
                        SqliteWrapper.update(this.mContext, this.mContentResolver, Uri.parse("content://mms/resetFilePerm/" + file.getName()), new ContentValues(0), null, null);
                    }
                    return;
                }
            } else if (isDrm) {
                dataUri = uri;
                convertedData = drmConvertSession.convert(data, data.length);
                if (convertedData != null) {
                    os.write(convertedData, 0, convertedData.length);
                } else {
                    throw new MmsException("Error converting drm data.");
                }
            } else {
                os.write(data);
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e22) {
                    Log.e(TAG, "IOException while closing: " + os, e22);
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e222) {
                    Log.e(TAG, "IOException while closing: " + is, e222);
                }
            }
            if (drmConvertSession != null) {
                drmConvertSession.close(str);
                file = new File(str);
                SqliteWrapper.update(this.mContext, this.mContentResolver, Uri.parse("content://mms/resetFilePerm/" + file.getName()), new ContentValues(0), null, null);
            }
        } catch (FileNotFoundException e3) {
            Log.e(TAG, "Failed to open Input/Output stream.", e3);
            throw new MmsException(e3);
        } catch (IOException e4) {
            Log.e(TAG, "Failed to read/write data.", e4);
            throw new MmsException(e4);
        } catch (Throwable th) {
            Throwable th2 = th;
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e2222) {
                    Log.e(TAG, "IOException while closing: " + os, e2222);
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e22222) {
                    Log.e(TAG, "IOException while closing: " + is, e22222);
                }
            }
            if (drmConvertSession != null) {
                drmConvertSession.close(str);
                file = new File(str);
                SqliteWrapper.update(this.mContext, this.mContentResolver, Uri.parse("content://mms/resetFilePerm/" + file.getName()), new ContentValues(0), null, null);
            }
        }
    }

    public static String convertUriToPath(Context context, Uri uri) {
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
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, new String[]{Part._DATA}, null, null, null);
                if (!(cursor == null || cursor.getCount() == 0)) {
                    if (cursor.moveToFirst()) {
                        String path = cursor.getString(cursor.getColumnIndexOrThrow(Part._DATA));
                        if (cursor == null) {
                            return path;
                        }
                        cursor.close();
                        return path;
                    }
                }
                throw new IllegalArgumentException("Given Uri could not be found in media store");
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

    private void updateAddress(long msgId, int type, EncodedStringValue[] array) {
        SqliteWrapper.delete(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + msgId + "/addr"), "type=" + type, null);
        persistAddress(msgId, type, array);
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
        ContentValues values = new ContentValues(10);
        byte[] contentType = sendReq.getContentType();
        if (contentType != null) {
            values.put(BaseMmsColumns.CONTENT_TYPE, toIsoString(contentType));
        }
        long date = sendReq.getDate();
        if (date != -1) {
            values.put("date", Long.valueOf(date));
        }
        int deliveryReport = sendReq.getDeliveryReport();
        if (deliveryReport != 0) {
            values.put(BaseMmsColumns.DELIVERY_REPORT, Integer.valueOf(deliveryReport));
        }
        long expiry = sendReq.getExpiry();
        if (expiry != -1) {
            values.put(BaseMmsColumns.EXPIRY, Long.valueOf(expiry));
        }
        byte[] msgClass = sendReq.getMessageClass();
        if (msgClass != null) {
            values.put(BaseMmsColumns.MESSAGE_CLASS, toIsoString(msgClass));
        }
        int priority = sendReq.getPriority();
        if (priority != 0) {
            values.put(BaseMmsColumns.PRIORITY, Integer.valueOf(priority));
        }
        int readReport = sendReq.getReadReport();
        if (readReport != 0) {
            values.put(BaseMmsColumns.READ_REPORT, Integer.valueOf(readReport));
        }
        byte[] transId = sendReq.getTransactionId();
        if (transId != null) {
            values.put(BaseMmsColumns.TRANSACTION_ID, toIsoString(transId));
        }
        EncodedStringValue subject = sendReq.getSubject();
        if (subject != null) {
            values.put(BaseMmsColumns.SUBJECT, toIsoString(subject.getTextString()));
            values.put(BaseMmsColumns.SUBJECT_CHARSET, Integer.valueOf(subject.getCharacterSet()));
        } else {
            values.put(BaseMmsColumns.SUBJECT, "");
        }
        long messageSize = sendReq.getMessageSize();
        if (messageSize > 0) {
            values.put(BaseMmsColumns.MESSAGE_SIZE, Long.valueOf(messageSize));
        }
        PduHeaders headers = sendReq.getPduHeaders();
        HashSet<String> recipients = new HashSet();
        for (int addrType : ADDRESS_FIELDS) {
            EncodedStringValue[] array = null;
            if (addrType == 137) {
                if (headers.getEncodedStringValue(addrType) != null) {
                    array = new EncodedStringValue[]{headers.getEncodedStringValue(addrType)};
                }
            } else {
                array = headers.getEncodedStringValues(addrType);
            }
            if (array != null) {
                updateAddress(ContentUris.parseId(uri), addrType, array);
                if (addrType == 151) {
                    for (EncodedStringValue v : array) {
                        if (v != null) {
                            recipients.add(v.getString());
                        }
                    }
                }
            }
        }
        if (!recipients.isEmpty()) {
            values.put("thread_id", Long.valueOf(Threads.getOrCreateThreadId(this.mContext, (Set) recipients)));
        }
        SqliteWrapper.update(this.mContext, this.mContentResolver, uri, values, null, null);
        return;
    }

    private void updatePart(Uri uri, PduPart part, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        ContentValues values = new ContentValues(7);
        int charset = part.getCharset();
        if (charset != 0) {
            values.put(Part.CHARSET, Integer.valueOf(charset));
        }
        if (part.getContentType() != null) {
            String contentType = toIsoString(part.getContentType());
            values.put(Part.CONTENT_TYPE, contentType);
            if (part.getFilename() != null) {
                values.put(Part.FILENAME, new String(part.getFilename()));
            }
            if (part.getName() != null) {
                values.put("name", new String(part.getName()));
            }
            if (part.getContentDisposition() != null) {
                values.put(Part.CONTENT_DISPOSITION, toIsoString(part.getContentDisposition()));
            }
            if (part.getContentId() != null) {
                values.put("cid", toIsoString(part.getContentId()));
            }
            if (part.getContentLocation() != null) {
                values.put(Part.CONTENT_LOCATION, toIsoString(part.getContentLocation()));
            }
            SqliteWrapper.update(this.mContext, this.mContentResolver, uri, values, null, null);
            if (part.getData() != null || uri != part.getDataUri()) {
                persistData(part, uri, contentType, preOpenedFiles);
                return;
            }
            return;
        }
        throw new MmsException("MIME type of the part must be set.");
    }

    public void updateParts(Uri uri, PduBody body, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        PduPart pduPart;
        try {
            PduPart part;
            synchronized (PDU_CACHE_INSTANCE) {
                if (PDU_CACHE_INSTANCE.isUpdating(uri)) {
                    try {
                        PDU_CACHE_INSTANCE.wait();
                    } catch (InterruptedException e) {
                        Log.e(TAG, "updateParts: ", e);
                    }
                    PduCacheEntry cacheEntry = (PduCacheEntry) PDU_CACHE_INSTANCE.get(uri);
                    if (cacheEntry != null) {
                        ((MultimediaMessagePdu) cacheEntry.getPdu()).setBody(body);
                    }
                }
                PDU_CACHE_INSTANCE.setUpdating(uri, true);
            }
            ArrayList<PduPart> toBeCreated = new ArrayList();
            HashMap<Uri, PduPart> toBeUpdated = new HashMap();
            int partsNum = body.getPartsNum();
            StringBuilder filter = new StringBuilder().append('(');
            for (int i = 0; i < partsNum; i++) {
                part = body.getPart(i);
                Uri partUri = part.getDataUri();
                if (partUri == null || TextUtils.isEmpty(partUri.getAuthority()) || !partUri.getAuthority().startsWith("mms")) {
                    toBeCreated.add(part);
                } else {
                    toBeUpdated.put(partUri, part);
                    if (filter.length() > 1) {
                        filter.append(" AND ");
                    }
                    filter.append("_id");
                    filter.append("!=");
                    DatabaseUtils.appendEscapedSQLString(filter, partUri.getLastPathSegment());
                }
            }
            filter.append(')');
            long msgId = ContentUris.parseId(uri);
            pduPart = this.mContext;
            SqliteWrapper.delete(pduPart, this.mContentResolver, Uri.parse(Mms.CONTENT_URI + "/" + msgId + "/part"), filter.length() > 2 ? filter.toString() : null, null);
            for (PduPart part2 : toBeCreated) {
                persistPart(part2, msgId, preOpenedFiles);
            }
            for (Entry<Uri, PduPart> e2 : toBeUpdated.entrySet()) {
                pduPart = (PduPart) e2.getValue();
                updatePart((Uri) e2.getKey(), pduPart, preOpenedFiles);
            }
            PDU_CACHE_INSTANCE.setUpdating(uri, false);
            PDU_CACHE_INSTANCE.notifyAll();
            return;
        } finally {
            pduPart = PDU_CACHE_INSTANCE;
            synchronized (pduPart) {
                PDU_CACHE_INSTANCE.setUpdating(uri, false);
                PDU_CACHE_INSTANCE.notifyAll();
            }
        }
    }

    public Uri persist(GenericPdu pdu, Uri uri, boolean createThreadId, boolean groupMmsEnabled, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        if (uri == null) {
            throw new MmsException("Uri may not be null.");
        }
        long msgId = -1;
        try {
            msgId = ContentUris.parseId(uri);
        } catch (NumberFormatException e) {
        }
        boolean existingUri = msgId != -1;
        if (existingUri || MESSAGE_BOX_MAP.get(uri) != null) {
            EncodedStringValue[] array;
            Uri res;
            synchronized (PDU_CACHE_INSTANCE) {
                if (PDU_CACHE_INSTANCE.isUpdating(uri)) {
                    try {
                        PDU_CACHE_INSTANCE.wait();
                    } catch (InterruptedException e2) {
                        Log.e(TAG, "persist1: ", e2);
                    }
                }
            }
            PDU_CACHE_INSTANCE.purge(uri);
            PduHeaders header = pdu.getPduHeaders();
            ContentValues values = new ContentValues();
            for (Entry<Integer, String> e3 : ENCODED_STRING_COLUMN_NAME_MAP.entrySet()) {
                int field = ((Integer) e3.getKey()).intValue();
                EncodedStringValue encodedString = header.getEncodedStringValue(field);
                if (encodedString != null) {
                    String charsetColumn = (String) CHARSET_COLUMN_NAME_MAP.get(Integer.valueOf(field));
                    values.put((String) e3.getValue(), toIsoString(encodedString.getTextString()));
                    values.put(charsetColumn, Integer.valueOf(encodedString.getCharacterSet()));
                }
            }
            for (Entry<Integer, String> e32 : TEXT_STRING_COLUMN_NAME_MAP.entrySet()) {
                byte[] text = header.getTextString(((Integer) e32.getKey()).intValue());
                if (text != null) {
                    values.put((String) e32.getValue(), toIsoString(text));
                }
            }
            for (Entry<Integer, String> e322 : OCTET_COLUMN_NAME_MAP.entrySet()) {
                int b = header.getOctet(((Integer) e322.getKey()).intValue());
                if (b != 0) {
                    values.put((String) e322.getValue(), Integer.valueOf(b));
                }
            }
            for (Entry<Integer, String> e3222 : LONG_COLUMN_NAME_MAP.entrySet()) {
                long l = header.getLongInteger(((Integer) e3222.getKey()).intValue());
                if (l != -1) {
                    values.put((String) e3222.getValue(), Long.valueOf(l));
                }
            }
            HashMap<Integer, EncodedStringValue[]> addressMap = new HashMap(ADDRESS_FIELDS.length);
            for (int addrType : ADDRESS_FIELDS) {
                array = null;
                if (addrType == 137) {
                    if (header.getEncodedStringValue(addrType) != null) {
                        array = new EncodedStringValue[]{header.getEncodedStringValue(addrType)};
                    }
                } else {
                    array = header.getEncodedStringValues(addrType);
                }
                addressMap.put(Integer.valueOf(addrType), array);
            }
            HashSet<String> recipients = new HashSet();
            int msgType = pdu.getMessageType();
            if (msgType == 130 || msgType == 132 || msgType == 128) {
                switch (msgType) {
                    case 128:
                        loadRecipients(151, recipients, addressMap, false);
                        break;
                    case 130:
                    case 132:
                        loadRecipients(137, recipients, addressMap, false);
                        if (groupMmsEnabled) {
                            loadRecipients(151, recipients, addressMap, true);
                            loadRecipients(130, recipients, addressMap, true);
                            break;
                        }
                        break;
                }
                long threadId = 0;
                if (createThreadId && !recipients.isEmpty()) {
                    threadId = Threads.getOrCreateThreadId(this.mContext, (Set) recipients);
                }
                values.put("thread_id", Long.valueOf(threadId));
            }
            long dummyId = System.currentTimeMillis();
            boolean textOnly = true;
            int messageSize = 0;
            if (pdu instanceof MultimediaMessagePdu) {
                PduBody body = ((MultimediaMessagePdu) pdu).getBody();
                if (body != null) {
                    int partsNum = body.getPartsNum();
                    if (partsNum > 2) {
                        textOnly = false;
                    }
                    for (int i = 0; i < partsNum; i++) {
                        PduPart part = body.getPart(i);
                        messageSize += part.getDataLength();
                        persistPart(part, dummyId, preOpenedFiles);
                        String contentType = getPartContentType(part);
                        if (!(contentType == null || ContentType.APP_SMIL.equals(contentType) || ContentType.TEXT_PLAIN.equals(contentType))) {
                            textOnly = false;
                        }
                    }
                }
            }
            values.put(BaseMmsColumns.TEXT_ONLY, Integer.valueOf(textOnly ? 1 : 0));
            if (values.getAsInteger(BaseMmsColumns.MESSAGE_SIZE) == null) {
                values.put(BaseMmsColumns.MESSAGE_SIZE, Integer.valueOf(messageSize));
            }
            if (existingUri) {
                res = uri;
                SqliteWrapper.update(this.mContext, this.mContentResolver, uri, values, null, null);
            } else {
                res = SqliteWrapper.insert(this.mContext, this.mContentResolver, uri, values);
                if (res == null) {
                    throw new MmsException("persist() failed: return null.");
                }
                msgId = ContentUris.parseId(res);
            }
            values = new ContentValues(1);
            values.put(Part.MSG_ID, Long.valueOf(msgId));
            SqliteWrapper.update(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + dummyId + "/part"), values, null, null);
            if (!existingUri) {
                res = Uri.parse(uri + "/" + msgId);
            }
            for (int addrType2 : ADDRESS_FIELDS) {
                array = (EncodedStringValue[]) addressMap.get(Integer.valueOf(addrType2));
                if (array != null) {
                    persistAddress(msgId, addrType2, array);
                }
            }
            return res;
        }
        throw new MmsException("Bad destination, must be one of content://mms/inbox, content://mms/sent, content://mms/drafts, content://mms/outbox, content://mms/temp.");
    }

    private void loadRecipients(int addressType, HashSet<String> recipients, HashMap<Integer, EncodedStringValue[]> addressMap, boolean excludeMyNumber) {
        EncodedStringValue[] array = (EncodedStringValue[]) addressMap.get(Integer.valueOf(addressType));
        if (array != null) {
            if (!excludeMyNumber || array.length != 1) {
                String myNumber;
                SubscriptionManager subscriptionManager = SubscriptionManager.from(this.mContext);
                Set<String> myPhoneNumbers = new HashSet();
                if (excludeMyNumber) {
                    for (int subid : subscriptionManager.getActiveSubscriptionIdList()) {
                        myNumber = this.mTelephonyManager.getLine1Number(subid);
                        if (myNumber != null) {
                            myPhoneNumbers.add(myNumber);
                        }
                    }
                }
                for (EncodedStringValue v : array) {
                    if (v != null) {
                        String number = v.getString();
                        if (excludeMyNumber) {
                            for (String myNumber2 : myPhoneNumbers) {
                                if (!PhoneNumberUtils.compare(number, myNumber2) && !recipients.contains(number)) {
                                    recipients.add(number);
                                    break;
                                }
                            }
                        } else if (!recipients.contains(number)) {
                            recipients.add(number);
                        }
                    }
                }
            }
        }
    }

    public Uri move(Uri from, Uri to) throws MmsException {
        long msgId = ContentUris.parseId(from);
        if (msgId == -1) {
            throw new MmsException("Error! ID of the message: -1.");
        }
        Integer msgBox = (Integer) MESSAGE_BOX_MAP.get(to);
        if (msgBox == null) {
            throw new MmsException("Bad destination, must be one of content://mms/inbox, content://mms/sent, content://mms/drafts, content://mms/outbox, content://mms/temp.");
        }
        ContentValues values = new ContentValues(1);
        values.put(BaseMmsColumns.MESSAGE_BOX, msgBox);
        SqliteWrapper.update(this.mContext, this.mContentResolver, from, values, null, null);
        return ContentUris.withAppendedId(to, msgId);
    }

    public static String toIsoString(byte[] bytes) {
        try {
            return new String(bytes, CharacterSets.MIMENAME_ISO_8859_1);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "ISO_8859_1 must be supported!", e);
            return "";
        }
    }

    public static byte[] getBytes(String data) {
        try {
            return data.getBytes(CharacterSets.MIMENAME_ISO_8859_1);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "ISO_8859_1 must be supported!", e);
            return new byte[0];
        }
    }

    public void release() {
        SqliteWrapper.delete(this.mContext, this.mContentResolver, Uri.parse(TEMPORARY_DRM_OBJECT_URI), null, null);
    }

    public Cursor getPendingMessages(long dueTime) {
        Builder uriBuilder = PendingMessages.CONTENT_URI.buildUpon();
        uriBuilder.appendQueryParameter("protocol", "mms");
        String[] selectionArgs = new String[]{String.valueOf(10), String.valueOf(dueTime)};
        return SqliteWrapper.query(this.mContext, this.mContentResolver, uriBuilder.build(), null, "err_type < ? AND due_time <= ?", selectionArgs, PendingMessages.DUE_TIME);
    }
}
