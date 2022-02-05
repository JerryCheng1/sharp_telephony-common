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
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
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
import java.util.Iterator;
import java.util.Map;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class PduPersister {
    static final /* synthetic */ boolean $assertionsDisabled = false;
    private static final int[] ADDRESS_FIELDS = null;
    private static final HashMap<Integer, Integer> CHARSET_COLUMN_INDEX_MAP = null;
    private static final HashMap<Integer, String> CHARSET_COLUMN_NAME_MAP = null;
    private static final boolean DEBUG = false;
    private static final long DUMMY_THREAD_ID = Long.MAX_VALUE;
    private static final HashMap<Integer, Integer> ENCODED_STRING_COLUMN_INDEX_MAP = null;
    private static final HashMap<Integer, String> ENCODED_STRING_COLUMN_NAME_MAP = null;
    private static final boolean LOCAL_LOGV = false;
    private static final HashMap<Integer, Integer> LONG_COLUMN_INDEX_MAP = null;
    private static final HashMap<Integer, String> LONG_COLUMN_NAME_MAP = null;
    private static final HashMap<Uri, Integer> MESSAGE_BOX_MAP = null;
    private static final HashMap<Integer, Integer> OCTET_COLUMN_INDEX_MAP = null;
    private static final HashMap<Integer, String> OCTET_COLUMN_NAME_MAP = null;
    private static final int PART_COLUMN_CHARSET = 1;
    private static final int PART_COLUMN_CONTENT_DISPOSITION = 2;
    private static final int PART_COLUMN_CONTENT_ID = 3;
    private static final int PART_COLUMN_CONTENT_LOCATION = 4;
    private static final int PART_COLUMN_CONTENT_TYPE = 5;
    private static final int PART_COLUMN_FILENAME = 6;
    private static final int PART_COLUMN_ID = 0;
    private static final int PART_COLUMN_NAME = 7;
    private static final int PART_COLUMN_TEXT = 8;
    private static final String[] PART_PROJECTION = null;
    private static final PduCache PDU_CACHE_INSTANCE = null;
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
    private static final String[] PDU_PROJECTION = null;
    public static final int PROC_STATUS_COMPLETED = 3;
    public static final int PROC_STATUS_PERMANENTLY_FAILURE = 2;
    public static final int PROC_STATUS_TRANSIENT_FAILURE = 1;
    private static final String TAG = "PduPersister";
    public static final String TEMPORARY_DRM_OBJECT_URI = "content://mms/9223372036854775807/part";
    private static final HashMap<Integer, Integer> TEXT_STRING_COLUMN_INDEX_MAP = null;
    private static final HashMap<Integer, String> TEXT_STRING_COLUMN_NAME_MAP = null;
    private static PduPersister sPersister;
    private final ContentResolver mContentResolver;
    private final Context mContext;
    private final DrmManagerClient mDrmManagerClient;
    private final TelephonyManager mTelephonyManager;

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
            headers.setEncodedStringValue(new EncodedStringValue(c.getInt(CHARSET_COLUMN_INDEX_MAP.get(Integer.valueOf(mapColumn)).intValue()), getBytes(s)), mapColumn);
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
        if (!c.isNull(columnIndex)) {
            return Integer.valueOf(c.getInt(columnIndex));
        }
        return null;
    }

    private byte[] getByteArrayFromPartColumn(Cursor c, int columnIndex) {
        if (!c.isNull(columnIndex)) {
            return getBytes(c.getString(columnIndex));
        }
        return null;
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
                            if (!ContentType.isImageType(type) && !ContentType.isAudioType(type) && !ContentType.isVideoType(type)) {
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                if (ContentType.TEXT_PLAIN.equals(type) || ContentType.APP_SMIL.equals(type) || ContentType.TEXT_HTML.equals(type)) {
                                    String text = c.getString(8);
                                    if (text == null) {
                                        text = "";
                                    }
                                    byte[] blob = new EncodedStringValue(text).getTextString();
                                    baos.write(blob, 0, blob.length);
                                } else {
                                    try {
                                        InputStream is = this.mContentResolver.openInputStream(partURI);
                                        byte[] buffer = new byte[256];
                                        for (int len = is.read(buffer); len >= 0; len = is.read(buffer)) {
                                            baos.write(buffer, 0, len);
                                        }
                                        if (is != null) {
                                            try {
                                                is.close();
                                            } catch (IOException e) {
                                                Log.e(TAG, "Failed to close stream", e);
                                            }
                                        }
                                    } catch (IOException e2) {
                                        Log.e(TAG, "Failed to load part data", e2);
                                        c.close();
                                        throw new MmsException(e2);
                                    }
                                }
                                part.setData(baos.toByteArray());
                            }
                            parts[partIdx] = part;
                            partIdx++;
                        } else {
                            throw new MmsException("Content-Type must be set.");
                        }
                    }
                    if (c != null) {
                        c.close();
                    }
                    return parts;
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }
    }

    private void loadAddress(long msgId, PduHeaders headers) {
        Cursor c = SqliteWrapper.query(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + msgId + "/addr"), new String[]{"address", Telephony.Mms.Addr.CHARSET, "type"}, null, null, null);
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
                                continue;
                            case 137:
                                headers.setEncodedStringValue(new EncodedStringValue(c.getInt(1), getBytes(addr)), addrType);
                                continue;
                            default:
                                Log.e(TAG, "Unknown address type: " + addrType);
                                continue;
                        }
                    }
                } finally {
                    c.close();
                }
            }
        }
    }

    /* JADX WARN: Code restructure failed: missing block: B:100:0x0247, code lost:
        if (com.google.android.mms.pdu.PduPersister.$assertionsDisabled != false) goto L_0x02ce;
     */
    /* JADX WARN: Code restructure failed: missing block: B:102:0x0251, code lost:
        if (com.google.android.mms.pdu.PduPersister.PDU_CACHE_INSTANCE.get(r33) == null) goto L_0x02ce;
     */
    /* JADX WARN: Code restructure failed: missing block: B:104:0x0258, code lost:
        throw new java.lang.AssertionError();
     */
    /* JADX WARN: Code restructure failed: missing block: B:105:0x025d, code lost:
        r26 = new com.google.android.mms.pdu.DeliveryInd(r17);
     */
    /* JADX WARN: Code restructure failed: missing block: B:106:0x0269, code lost:
        r26 = new com.google.android.mms.pdu.ReadOrigInd(r17);
     */
    /* JADX WARN: Code restructure failed: missing block: B:107:0x0275, code lost:
        r26 = new com.google.android.mms.pdu.RetrieveConf(r17, r11);
     */
    /* JADX WARN: Code restructure failed: missing block: B:108:0x0281, code lost:
        r26 = new com.google.android.mms.pdu.SendReq(r17, r11);
     */
    /* JADX WARN: Code restructure failed: missing block: B:109:0x028d, code lost:
        r26 = new com.google.android.mms.pdu.AcknowledgeInd(r17);
     */
    /* JADX WARN: Code restructure failed: missing block: B:110:0x0299, code lost:
        r26 = new com.google.android.mms.pdu.NotifyRespInd(r17);
     */
    /* JADX WARN: Code restructure failed: missing block: B:111:0x02a5, code lost:
        r26 = new com.google.android.mms.pdu.ReadRecInd(r17);
     */
    /* JADX WARN: Code restructure failed: missing block: B:113:0x02cd, code lost:
        throw new com.google.android.mms.MmsException("Unsupported PDU type: " + java.lang.Integer.toHexString(r21));
     */
    /* JADX WARN: Code restructure failed: missing block: B:115:0x02d9, code lost:
        com.google.android.mms.pdu.PduPersister.PDU_CACHE_INSTANCE.put(r33, new com.google.android.mms.util.PduCacheEntry(r26, r20, r30));
     */
    /* JADX WARN: Code restructure failed: missing block: B:116:0x02e0, code lost:
        com.google.android.mms.pdu.PduPersister.PDU_CACHE_INSTANCE.setUpdating(r33, false);
        com.google.android.mms.pdu.PduPersister.PDU_CACHE_INSTANCE.notifyAll();
     */
    /* JADX WARN: Code restructure failed: missing block: B:117:0x02ed, code lost:
        monitor-exit(r5);
     */
    /* JADX WARN: Code restructure failed: missing block: B:120:0x02fd, code lost:
        com.google.android.mms.pdu.PduPersister.PDU_CACHE_INSTANCE.put(r33, new com.google.android.mms.util.PduCacheEntry(null, 0, -1));
     */
    /* JADX WARN: Code restructure failed: missing block: B:121:0x0304, code lost:
        com.google.android.mms.pdu.PduPersister.PDU_CACHE_INSTANCE.setUpdating(r33, false);
        com.google.android.mms.pdu.PduPersister.PDU_CACHE_INSTANCE.notifyAll();
     */
    /* JADX WARN: Code restructure failed: missing block: B:123:0x0312, code lost:
        throw r4;
     */
    /* JADX WARN: Code restructure failed: missing block: B:150:?, code lost:
        return r26;
     */
    /* JADX WARN: Code restructure failed: missing block: B:32:0x0058, code lost:
        monitor-enter(com.google.android.mms.pdu.PduPersister.PDU_CACHE_INSTANCE);
     */
    /* JADX WARN: Code restructure failed: missing block: B:33:0x0059, code lost:
        if (0 != 0) goto L_0x005b;
     */
    /* JADX WARN: Code restructure failed: missing block: B:35:0x005d, code lost:
        if (com.google.android.mms.pdu.PduPersister.$assertionsDisabled != false) goto L_0x02f2;
     */
    /* JADX WARN: Code restructure failed: missing block: B:39:0x006e, code lost:
        throw new java.lang.AssertionError();
     */
    /* JADX WARN: Code restructure failed: missing block: B:49:0x009f, code lost:
        r12 = com.google.android.mms.util.SqliteWrapper.query(r32.mContext, r32.mContentResolver, r33, com.google.android.mms.pdu.PduPersister.PDU_PROJECTION, null, null, null);
        r17 = new com.google.android.mms.pdu.PduHeaders();
        r22 = android.content.ContentUris.parseId(r33);
     */
    /* JADX WARN: Code restructure failed: missing block: B:50:0x00bb, code lost:
        if (r12 == null) goto L_0x00ca;
     */
    /* JADX WARN: Code restructure failed: missing block: B:52:0x00c2, code lost:
        if (r12.getCount() != 1) goto L_0x00ca;
     */
    /* JADX WARN: Code restructure failed: missing block: B:54:0x00c8, code lost:
        if (r12.moveToFirst() != false) goto L_0x00ef;
     */
    /* JADX WARN: Code restructure failed: missing block: B:56:0x00e4, code lost:
        throw new com.google.android.mms.MmsException("Bad uri: " + r33);
     */
    /* JADX WARN: Code restructure failed: missing block: B:57:0x00ec, code lost:
        r4 = th;
     */
    /* JADX WARN: Code restructure failed: missing block: B:59:0x00f0, code lost:
        r20 = r12.getInt(1);
        r30 = r12.getLong(2);
        r19 = com.google.android.mms.pdu.PduPersister.ENCODED_STRING_COLUMN_INDEX_MAP.entrySet().iterator();
     */
    /* JADX WARN: Code restructure failed: missing block: B:61:0x0107, code lost:
        if (r19.hasNext() == false) goto L_0x012b;
     */
    /* JADX WARN: Code restructure failed: missing block: B:62:0x0109, code lost:
        r16 = r19.next();
        setEncodedStringValueToHeaders(r12, r16.getValue().intValue(), r17, r16.getKey().intValue());
     */
    /* JADX WARN: Code restructure failed: missing block: B:63:0x012b, code lost:
        r19 = com.google.android.mms.pdu.PduPersister.TEXT_STRING_COLUMN_INDEX_MAP.entrySet().iterator();
     */
    /* JADX WARN: Code restructure failed: missing block: B:65:0x0139, code lost:
        if (r19.hasNext() == false) goto L_0x015d;
     */
    /* JADX WARN: Code restructure failed: missing block: B:66:0x013b, code lost:
        r16 = r19.next();
        setTextStringToHeaders(r12, r16.getValue().intValue(), r17, r16.getKey().intValue());
     */
    /* JADX WARN: Code restructure failed: missing block: B:67:0x015d, code lost:
        r19 = com.google.android.mms.pdu.PduPersister.OCTET_COLUMN_INDEX_MAP.entrySet().iterator();
     */
    /* JADX WARN: Code restructure failed: missing block: B:69:0x016b, code lost:
        if (r19.hasNext() == false) goto L_0x018f;
     */
    /* JADX WARN: Code restructure failed: missing block: B:70:0x016d, code lost:
        r16 = r19.next();
        setOctetToHeaders(r12, r16.getValue().intValue(), r17, r16.getKey().intValue());
     */
    /* JADX WARN: Code restructure failed: missing block: B:71:0x018f, code lost:
        r19 = com.google.android.mms.pdu.PduPersister.LONG_COLUMN_INDEX_MAP.entrySet().iterator();
     */
    /* JADX WARN: Code restructure failed: missing block: B:73:0x019d, code lost:
        if (r19.hasNext() == false) goto L_0x01c1;
     */
    /* JADX WARN: Code restructure failed: missing block: B:74:0x019f, code lost:
        r16 = r19.next();
        setLongToHeaders(r12, r16.getValue().intValue(), r17, r16.getKey().intValue());
     */
    /* JADX WARN: Code restructure failed: missing block: B:75:0x01c1, code lost:
        if (r12 == null) goto L_0x01c6;
     */
    /* JADX WARN: Code restructure failed: missing block: B:76:0x01c3, code lost:
        r12.close();
     */
    /* JADX WARN: Code restructure failed: missing block: B:78:0x01ca, code lost:
        if (r22 != (-1)) goto L_0x01d4;
     */
    /* JADX WARN: Code restructure failed: missing block: B:80:0x01d3, code lost:
        throw new com.google.android.mms.MmsException("Error! ID of the message: -1.");
     */
    /* JADX WARN: Code restructure failed: missing block: B:81:0x01d4, code lost:
        loadAddress(r22, r17);
        r21 = r17.getOctet(140);
        r11 = new com.google.android.mms.pdu.PduBody();
     */
    /* JADX WARN: Code restructure failed: missing block: B:82:0x01ee, code lost:
        if (r21 == 132) goto L_0x01f6;
     */
    /* JADX WARN: Code restructure failed: missing block: B:84:0x01f4, code lost:
        if (r21 != 128) goto L_0x0215;
     */
    /* JADX WARN: Code restructure failed: missing block: B:85:0x01f6, code lost:
        r24 = loadParts(r22);
     */
    /* JADX WARN: Code restructure failed: missing block: B:86:0x01fe, code lost:
        if (r24 == null) goto L_0x0215;
     */
    /* JADX WARN: Code restructure failed: missing block: B:87:0x0200, code lost:
        r0 = r24.length;
        r18 = 0;
     */
    /* JADX WARN: Code restructure failed: missing block: B:89:0x020b, code lost:
        if (r18 >= r0) goto L_0x0215;
     */
    /* JADX WARN: Code restructure failed: missing block: B:90:0x020d, code lost:
        r11.addPart(r24[r18]);
        r18 = r18 + 1;
     */
    /* JADX WARN: Code restructure failed: missing block: B:91:0x0215, code lost:
        switch(r21) {
            case 128: goto L_0x0281;
            case 129: goto L_0x02b1;
            case 130: goto L_0x0235;
            case 131: goto L_0x0299;
            case 132: goto L_0x0275;
            case 133: goto L_0x028d;
            case 134: goto L_0x025d;
            case 135: goto L_0x02a5;
            case 136: goto L_0x0269;
            case 137: goto L_0x02b1;
            case 138: goto L_0x02b1;
            case 139: goto L_0x02b1;
            case 140: goto L_0x02b1;
            case 141: goto L_0x02b1;
            case 142: goto L_0x02b1;
            case 143: goto L_0x02b1;
            case 144: goto L_0x02b1;
            case 145: goto L_0x02b1;
            case 146: goto L_0x02b1;
            case 147: goto L_0x02b1;
            case 148: goto L_0x02b1;
            case 149: goto L_0x02b1;
            case 150: goto L_0x02b1;
            case 151: goto L_0x02b1;
            default: goto L_0x0218;
        };
     */
    /* JADX WARN: Code restructure failed: missing block: B:93:0x0234, code lost:
        throw new com.google.android.mms.MmsException("Unrecognized PDU type: " + java.lang.Integer.toHexString(r21));
     */
    /* JADX WARN: Code restructure failed: missing block: B:95:0x023e, code lost:
        r26 = new com.google.android.mms.pdu.NotificationInd(r17);
     */
    /* JADX WARN: Code restructure failed: missing block: B:96:0x0240, code lost:
        r5 = com.google.android.mms.pdu.PduPersister.PDU_CACHE_INSTANCE;
     */
    /* JADX WARN: Code restructure failed: missing block: B:97:0x0242, code lost:
        monitor-enter(r5);
     */
    /* JADX WARN: Code restructure failed: missing block: B:98:0x0243, code lost:
        if (r26 == null) goto L_0x0323;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public com.google.android.mms.pdu.GenericPdu load(android.net.Uri r33) throws com.google.android.mms.MmsException {
        /*
            Method dump skipped, instructions count: 858
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.android.mms.pdu.PduPersister.load(android.net.Uri):com.google.android.mms.pdu.GenericPdu");
    }

    private void persistAddress(long msgId, int type, EncodedStringValue[] array) {
        ContentValues values = new ContentValues(3);
        for (EncodedStringValue addr : array) {
            values.clear();
            values.put("address", toIsoString(addr.getTextString()));
            values.put(Telephony.Mms.Addr.CHARSET, Integer.valueOf(addr.getCharacterSet()));
            values.put("type", Integer.valueOf(type));
            SqliteWrapper.insert(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + msgId + "/addr"), values);
        }
    }

    private static String getPartContentType(PduPart part) {
        if (part.getContentType() == null) {
            return null;
        }
        return toIsoString(part.getContentType());
    }

    /* JADX INFO: Multiple debug info for r6v5 java.lang.String: [D('value' java.lang.String), D('value' java.lang.Object)] */
    public Uri persistPart(PduPart part, long msgId, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        Uri uri = Uri.parse("content://mms/" + msgId + "/part");
        ContentValues values = new ContentValues(8);
        int charset = part.getCharset();
        if (charset != 0) {
            values.put(Telephony.Mms.Part.CHARSET, Integer.valueOf(charset));
        }
        String contentType = getPartContentType(part);
        if (contentType != null) {
            if (ContentType.IMAGE_JPG.equals(contentType)) {
                contentType = ContentType.IMAGE_JPEG;
            }
            values.put(Telephony.Mms.Part.CONTENT_TYPE, contentType);
            if (ContentType.APP_SMIL.equals(contentType)) {
                values.put(Telephony.Mms.Part.SEQ, (Integer) (-1));
            }
            if (part.getFilename() != null) {
                values.put(Telephony.Mms.Part.FILENAME, new String(part.getFilename()));
            }
            if (part.getName() != null) {
                values.put("name", new String(part.getName()));
            }
            if (part.getContentDisposition() != null) {
                values.put(Telephony.Mms.Part.CONTENT_DISPOSITION, (String) toIsoString(part.getContentDisposition()));
            }
            if (part.getContentId() != null) {
                values.put("cid", toIsoString(part.getContentId()));
            }
            if (part.getContentLocation() != null) {
                values.put(Telephony.Mms.Part.CONTENT_LOCATION, toIsoString(part.getContentLocation()));
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
        OutputStream os;
        InputStream is;
        DrmConvertSession drmConvertSession;
        try {
            os = null;
            is = null;
            drmConvertSession = null;
            String path = null;
            try {
                try {
                    byte[] data = part.getData();
                    if (ContentType.TEXT_PLAIN.equals(contentType) || ContentType.APP_SMIL.equals(contentType) || ContentType.TEXT_HTML.equals(contentType)) {
                        ContentValues cv = new ContentValues();
                        if (data == null) {
                            data = new String("").getBytes("utf-8");
                        }
                        cv.put(Telephony.Mms.Part.TEXT, new EncodedStringValue(data).getString());
                        if (this.mContentResolver.update(uri, cv, null, null) != 1) {
                            throw new MmsException("unable to update " + uri.toString());
                        }
                    } else {
                        boolean isDrm = DownloadDrmHelper.isDrmConvertNeeded(contentType);
                        if (isDrm) {
                            if (uri != null) {
                                try {
                                    path = convertUriToPath(this.mContext, uri);
                                    if (new File(path).length() > 0) {
                                        if (0 != 0) {
                                            try {
                                                os.close();
                                            } catch (IOException e) {
                                                Log.e(TAG, "IOException while closing: " + ((Object) null), e);
                                            }
                                        }
                                        if (0 != 0) {
                                            try {
                                                is.close();
                                            } catch (IOException e2) {
                                                Log.e(TAG, "IOException while closing: " + ((Object) null), e2);
                                            }
                                        }
                                        if (0 != 0) {
                                            drmConvertSession.close(path);
                                            File f = new File(path);
                                            SqliteWrapper.update(this.mContext, this.mContentResolver, Uri.parse("content://mms/resetFilePerm/" + f.getName()), new ContentValues(0), null, null);
                                            return;
                                        }
                                        return;
                                    }
                                } catch (Exception e3) {
                                    Log.e(TAG, "Can't get file info for: " + part.getDataUri(), e3);
                                }
                            }
                            drmConvertSession = DrmConvertSession.open(this.mContext, contentType);
                            if (drmConvertSession == null) {
                                throw new MmsException("Mimetype " + contentType + " can not be converted.");
                            }
                        }
                        os = this.mContentResolver.openOutputStream(uri);
                        if (data == null) {
                            Uri dataUri = part.getDataUri();
                            if (dataUri != null && dataUri != uri) {
                                if (preOpenedFiles != null && preOpenedFiles.containsKey(dataUri)) {
                                    is = preOpenedFiles.get(dataUri);
                                }
                                if (is == null) {
                                    is = this.mContentResolver.openInputStream(dataUri);
                                }
                                byte[] buffer = new byte[SmsCbConstants.SERIAL_NUMBER_ETWS_EMERGENCY_USER_ALERT];
                                while (true) {
                                    int len = is.read(buffer);
                                    if (len == -1) {
                                        break;
                                    } else if (!isDrm) {
                                        os.write(buffer, 0, len);
                                    } else {
                                        byte[] convertedData = drmConvertSession.convert(buffer, len);
                                        if (convertedData != null) {
                                            os.write(convertedData, 0, convertedData.length);
                                        } else {
                                            throw new MmsException("Error converting drm data.");
                                        }
                                    }
                                }
                            } else {
                                Log.w(TAG, "Can't find data for this part.");
                                if (os != null) {
                                    try {
                                        os.close();
                                    } catch (IOException e4) {
                                        Log.e(TAG, "IOException while closing: " + os, e4);
                                    }
                                }
                                if (0 != 0) {
                                    try {
                                        is.close();
                                    } catch (IOException e5) {
                                        Log.e(TAG, "IOException while closing: " + ((Object) null), e5);
                                    }
                                }
                                if (drmConvertSession != null) {
                                    drmConvertSession.close(path);
                                    File f2 = new File(path);
                                    SqliteWrapper.update(this.mContext, this.mContentResolver, Uri.parse("content://mms/resetFilePerm/" + f2.getName()), new ContentValues(0), null, null);
                                    return;
                                }
                                return;
                            }
                        } else if (!isDrm) {
                            os.write(data);
                        } else {
                            byte[] convertedData2 = drmConvertSession.convert(data, data.length);
                            if (convertedData2 != null) {
                                os.write(convertedData2, 0, convertedData2.length);
                            } else {
                                throw new MmsException("Error converting drm data.");
                            }
                        }
                    }
                } catch (FileNotFoundException e6) {
                    Log.e(TAG, "Failed to open Input/Output stream.", e6);
                    throw new MmsException(e6);
                }
            } catch (IOException e7) {
                Log.e(TAG, "Failed to read/write data.", e7);
                throw new MmsException(e7);
            }
        } finally {
            if (0 != 0) {
                try {
                    os.close();
                } catch (IOException e8) {
                    Log.e(TAG, "IOException while closing: " + ((Object) null), e8);
                }
            }
            if (0 != 0) {
                try {
                    is.close();
                } catch (IOException e9) {
                    Log.e(TAG, "IOException while closing: " + ((Object) null), e9);
                }
            }
            if (0 != 0) {
                drmConvertSession.close(null);
                File f3 = new File((String) null);
                SqliteWrapper.update(this.mContext, this.mContentResolver, Uri.parse("content://mms/resetFilePerm/" + f3.getName()), new ContentValues(0), null, null);
            }
        }
    }

    public static String convertUriToPath(Context context, Uri uri) {
        Cursor cursor;
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
                cursor = null;
                try {
                    cursor = context.getContentResolver().query(uri, new String[]{Telephony.Mms.Part._DATA}, null, null, null);
                    if (cursor == null || cursor.getCount() == 0 || !cursor.moveToFirst()) {
                        throw new IllegalArgumentException("Given Uri could not be found in media store");
                    }
                    String path = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part._DATA));
                } catch (SQLiteException e) {
                    throw new IllegalArgumentException("Given Uri is not formatted in a way so that it can be found in media store.");
                }
            } finally {
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
            values.put(Telephony.BaseMmsColumns.CONTENT_TYPE, toIsoString(contentType));
        }
        long date = sendReq.getDate();
        if (date != -1) {
            values.put("date", Long.valueOf(date));
        }
        int deliveryReport = sendReq.getDeliveryReport();
        if (deliveryReport != 0) {
            values.put(Telephony.BaseMmsColumns.DELIVERY_REPORT, Integer.valueOf(deliveryReport));
        }
        long expiry = sendReq.getExpiry();
        if (expiry != -1) {
            values.put(Telephony.BaseMmsColumns.EXPIRY, Long.valueOf(expiry));
        }
        byte[] msgClass = sendReq.getMessageClass();
        if (msgClass != null) {
            values.put(Telephony.BaseMmsColumns.MESSAGE_CLASS, toIsoString(msgClass));
        }
        int priority = sendReq.getPriority();
        if (priority != 0) {
            values.put(Telephony.BaseMmsColumns.PRIORITY, Integer.valueOf(priority));
        }
        int readReport = sendReq.getReadReport();
        if (readReport != 0) {
            values.put(Telephony.BaseMmsColumns.READ_REPORT, Integer.valueOf(readReport));
        }
        byte[] transId = sendReq.getTransactionId();
        if (transId != null) {
            values.put(Telephony.BaseMmsColumns.TRANSACTION_ID, toIsoString(transId));
        }
        EncodedStringValue subject = sendReq.getSubject();
        if (subject != null) {
            values.put(Telephony.BaseMmsColumns.SUBJECT, toIsoString(subject.getTextString()));
            values.put(Telephony.BaseMmsColumns.SUBJECT_CHARSET, Integer.valueOf(subject.getCharacterSet()));
        } else {
            values.put(Telephony.BaseMmsColumns.SUBJECT, "");
        }
        long messageSize = sendReq.getMessageSize();
        if (messageSize > 0) {
            values.put(Telephony.BaseMmsColumns.MESSAGE_SIZE, Long.valueOf(messageSize));
        }
        PduHeaders headers = sendReq.getPduHeaders();
        HashSet<String> recipients = new HashSet<>();
        int[] arr$ = ADDRESS_FIELDS;
        for (int addrType : arr$) {
            EncodedStringValue[] array = null;
            if (addrType == 137) {
                EncodedStringValue v = headers.getEncodedStringValue(addrType);
                if (v != null) {
                    array = new EncodedStringValue[]{v};
                }
            } else {
                array = headers.getEncodedStringValues(addrType);
            }
            if (array != null) {
                updateAddress(ContentUris.parseId(uri), addrType, array);
                if (addrType == 151) {
                    for (EncodedStringValue v2 : array) {
                        if (v2 != null) {
                            recipients.add(v2.getString());
                        }
                    }
                }
            }
        }
        if (!recipients.isEmpty()) {
            values.put("thread_id", Long.valueOf(Telephony.Threads.getOrCreateThreadId(this.mContext, recipients)));
        }
        SqliteWrapper.update(this.mContext, this.mContentResolver, uri, values, null, null);
    }

    /* JADX INFO: Multiple debug info for r10v5 java.lang.String: [D('value' java.lang.String), D('value' java.lang.Object)] */
    private void updatePart(Uri uri, PduPart part, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        ContentValues values = new ContentValues(7);
        int charset = part.getCharset();
        if (charset != 0) {
            values.put(Telephony.Mms.Part.CHARSET, Integer.valueOf(charset));
        }
        if (part.getContentType() != null) {
            String contentType = toIsoString(part.getContentType());
            values.put(Telephony.Mms.Part.CONTENT_TYPE, contentType);
            if (part.getFilename() != null) {
                values.put(Telephony.Mms.Part.FILENAME, new String(part.getFilename()));
            }
            if (part.getName() != null) {
                values.put("name", new String(part.getName()));
            }
            if (part.getContentDisposition() != null) {
                values.put(Telephony.Mms.Part.CONTENT_DISPOSITION, (String) toIsoString(part.getContentDisposition()));
            }
            if (part.getContentId() != null) {
                values.put("cid", toIsoString(part.getContentId()));
            }
            if (part.getContentLocation() != null) {
                values.put(Telephony.Mms.Part.CONTENT_LOCATION, toIsoString(part.getContentLocation()));
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
        try {
            synchronized (PDU_CACHE_INSTANCE) {
                if (PDU_CACHE_INSTANCE.isUpdating(uri)) {
                    try {
                        PDU_CACHE_INSTANCE.wait();
                    } catch (InterruptedException e) {
                        Log.e(TAG, "updateParts: ", e);
                    }
                    PduCacheEntry cacheEntry = PDU_CACHE_INSTANCE.get(uri);
                    if (cacheEntry != null) {
                        ((MultimediaMessagePdu) cacheEntry.getPdu()).setBody(body);
                    }
                }
                PDU_CACHE_INSTANCE.setUpdating(uri, true);
            }
            ArrayList<PduPart> toBeCreated = new ArrayList<>();
            HashMap<Uri, PduPart> toBeUpdated = new HashMap<>();
            int partsNum = body.getPartsNum();
            StringBuilder filter = new StringBuilder().append('(');
            for (int i = 0; i < partsNum; i++) {
                PduPart part = body.getPart(i);
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
            SqliteWrapper.delete(this.mContext, this.mContentResolver, Uri.parse(Telephony.Mms.CONTENT_URI + "/" + msgId + "/part"), filter.length() > 2 ? filter.toString() : null, null);
            Iterator i$ = toBeCreated.iterator();
            while (i$.hasNext()) {
                persistPart(i$.next(), msgId, preOpenedFiles);
            }
            for (Map.Entry<Uri, PduPart> e2 : toBeUpdated.entrySet()) {
                updatePart(e2.getKey(), e2.getValue(), preOpenedFiles);
            }
            synchronized (PDU_CACHE_INSTANCE) {
                try {
                    PDU_CACHE_INSTANCE.setUpdating(uri, false);
                    PDU_CACHE_INSTANCE.notifyAll();
                } catch (Throwable th) {
                    throw th;
                }
            }
        } catch (Throwable th2) {
            synchronized (PDU_CACHE_INSTANCE) {
                try {
                    PDU_CACHE_INSTANCE.setUpdating(uri, false);
                    PDU_CACHE_INSTANCE.notifyAll();
                    throw th2;
                } catch (Throwable th3) {
                    throw th3;
                }
            }
        }
    }

    public Uri persist(GenericPdu pdu, Uri uri, boolean createThreadId, boolean groupMmsEnabled, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        Uri res;
        PduBody body;
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
            for (Map.Entry<Integer, String> e3 : ENCODED_STRING_COLUMN_NAME_MAP.entrySet()) {
                int field = e3.getKey().intValue();
                EncodedStringValue encodedString = header.getEncodedStringValue(field);
                if (encodedString != null) {
                    values.put(e3.getValue(), toIsoString(encodedString.getTextString()));
                    values.put(CHARSET_COLUMN_NAME_MAP.get(Integer.valueOf(field)), Integer.valueOf(encodedString.getCharacterSet()));
                }
            }
            for (Map.Entry<Integer, String> e4 : TEXT_STRING_COLUMN_NAME_MAP.entrySet()) {
                byte[] text = header.getTextString(e4.getKey().intValue());
                if (text != null) {
                    values.put(e4.getValue(), toIsoString(text));
                }
            }
            for (Map.Entry<Integer, String> e5 : OCTET_COLUMN_NAME_MAP.entrySet()) {
                int b = header.getOctet(e5.getKey().intValue());
                if (b != 0) {
                    values.put(e5.getValue(), Integer.valueOf(b));
                }
            }
            for (Map.Entry<Integer, String> e6 : LONG_COLUMN_NAME_MAP.entrySet()) {
                long l = header.getLongInteger(e6.getKey().intValue());
                if (l != -1) {
                    values.put(e6.getValue(), Long.valueOf(l));
                }
            }
            HashMap<Integer, EncodedStringValue[]> addressMap = new HashMap<>(ADDRESS_FIELDS.length);
            int[] arr$ = ADDRESS_FIELDS;
            for (int addrType : arr$) {
                EncodedStringValue[] array = null;
                if (addrType == 137) {
                    EncodedStringValue v = header.getEncodedStringValue(addrType);
                    if (v != null) {
                        array = new EncodedStringValue[]{v};
                    }
                } else {
                    array = header.getEncodedStringValues(addrType);
                }
                addressMap.put(Integer.valueOf(addrType), array);
            }
            HashSet<String> recipients = new HashSet<>();
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
                    threadId = Telephony.Threads.getOrCreateThreadId(this.mContext, recipients);
                }
                values.put("thread_id", Long.valueOf(threadId));
            }
            long dummyId = System.currentTimeMillis();
            boolean textOnly = true;
            int messageSize = 0;
            if ((pdu instanceof MultimediaMessagePdu) && (body = ((MultimediaMessagePdu) pdu).getBody()) != null) {
                int partsNum = body.getPartsNum();
                if (partsNum > 2) {
                    textOnly = false;
                }
                for (int i = 0; i < partsNum; i++) {
                    PduPart part = body.getPart(i);
                    messageSize += part.getDataLength();
                    persistPart(part, dummyId, preOpenedFiles);
                    String contentType = getPartContentType(part);
                    if (contentType != null && !ContentType.APP_SMIL.equals(contentType) && !ContentType.TEXT_PLAIN.equals(contentType)) {
                        textOnly = false;
                    }
                }
            }
            values.put(Telephony.BaseMmsColumns.TEXT_ONLY, Integer.valueOf(textOnly ? 1 : 0));
            if (values.getAsInteger(Telephony.BaseMmsColumns.MESSAGE_SIZE) == null) {
                values.put(Telephony.BaseMmsColumns.MESSAGE_SIZE, Integer.valueOf(messageSize));
            }
            if (existingUri) {
                res = uri;
                SqliteWrapper.update(this.mContext, this.mContentResolver, res, values, null, null);
            } else {
                res = SqliteWrapper.insert(this.mContext, this.mContentResolver, uri, values);
                if (res == null) {
                    throw new MmsException("persist() failed: return null.");
                }
                msgId = ContentUris.parseId(res);
            }
            ContentValues values2 = new ContentValues(1);
            values2.put(Telephony.Mms.Part.MSG_ID, Long.valueOf(msgId));
            SqliteWrapper.update(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + dummyId + "/part"), values2, null, null);
            if (!existingUri) {
                res = Uri.parse(uri + "/" + msgId);
            }
            int[] arr$2 = ADDRESS_FIELDS;
            for (int addrType2 : arr$2) {
                EncodedStringValue[] array2 = addressMap.get(Integer.valueOf(addrType2));
                if (array2 != null) {
                    persistAddress(msgId, addrType2, array2);
                }
            }
            return res;
        }
        throw new MmsException("Bad destination, must be one of content://mms/inbox, content://mms/sent, content://mms/drafts, content://mms/outbox, content://mms/temp.");
    }

    private void loadRecipients(int addressType, HashSet<String> recipients, HashMap<Integer, EncodedStringValue[]> addressMap, boolean excludeMyNumber) {
        EncodedStringValue[] array = addressMap.get(Integer.valueOf(addressType));
        if (array != null) {
            if (!excludeMyNumber || array.length != 1) {
                String myNumber = excludeMyNumber ? this.mTelephonyManager.getLine1Number() : null;
                for (EncodedStringValue v : array) {
                    if (v != null) {
                        String number = v.getString();
                        if ((myNumber == null || !PhoneNumberUtils.compare(number, myNumber)) && !recipients.contains(number)) {
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
        Integer msgBox = MESSAGE_BOX_MAP.get(to);
        if (msgBox == null) {
            throw new MmsException("Bad destination, must be one of content://mms/inbox, content://mms/sent, content://mms/drafts, content://mms/outbox, content://mms/temp.");
        }
        ContentValues values = new ContentValues(1);
        values.put(Telephony.BaseMmsColumns.MESSAGE_BOX, msgBox);
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
        Uri.Builder uriBuilder = Telephony.MmsSms.PendingMessages.CONTENT_URI.buildUpon();
        uriBuilder.appendQueryParameter("protocol", "mms");
        return SqliteWrapper.query(this.mContext, this.mContentResolver, uriBuilder.build(), null, "err_type < ? AND due_time <= ?", new String[]{String.valueOf(10), String.valueOf(dueTime)}, Telephony.MmsSms.PendingMessages.DUE_TIME);
    }
}
