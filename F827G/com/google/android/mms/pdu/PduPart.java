package com.google.android.mms.pdu;

import android.net.Uri;
import java.util.HashMap;
import java.util.Map;

public class PduPart {
    public static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
    static final byte[] DISPOSITION_ATTACHMENT = "attachment".getBytes();
    static final byte[] DISPOSITION_FROM_DATA = "from-data".getBytes();
    static final byte[] DISPOSITION_INLINE = "inline".getBytes();
    public static final String P_7BIT = "7bit";
    public static final String P_8BIT = "8bit";
    public static final String P_BASE64 = "base64";
    public static final String P_BINARY = "binary";
    public static final int P_CHARSET = 129;
    public static final int P_COMMENT = 155;
    public static final int P_CONTENT_DISPOSITION = 197;
    public static final int P_CONTENT_ID = 192;
    public static final int P_CONTENT_LOCATION = 142;
    public static final int P_CONTENT_TRANSFER_ENCODING = 200;
    public static final int P_CONTENT_TYPE = 145;
    public static final int P_CREATION_DATE = 147;
    public static final int P_CT_MR_TYPE = 137;
    public static final int P_DEP_COMMENT = 140;
    public static final int P_DEP_CONTENT_DISPOSITION = 174;
    public static final int P_DEP_DOMAIN = 141;
    public static final int P_DEP_FILENAME = 134;
    public static final int P_DEP_NAME = 133;
    public static final int P_DEP_PATH = 143;
    public static final int P_DEP_START = 138;
    public static final int P_DEP_START_INFO = 139;
    public static final int P_DIFFERENCES = 135;
    public static final int P_DISPOSITION_ATTACHMENT = 129;
    public static final int P_DISPOSITION_FROM_DATA = 128;
    public static final int P_DISPOSITION_INLINE = 130;
    public static final int P_DOMAIN = 156;
    public static final int P_FILENAME = 152;
    public static final int P_LEVEL = 130;
    public static final int P_MAC = 146;
    public static final int P_MAX_AGE = 142;
    public static final int P_MODIFICATION_DATE = 148;
    public static final int P_NAME = 151;
    public static final int P_PADDING = 136;
    public static final int P_PATH = 157;
    public static final int P_Q = 128;
    public static final String P_QUOTED_PRINTABLE = "quoted-printable";
    public static final int P_READ_DATE = 149;
    public static final int P_SEC = 145;
    public static final int P_SECURE = 144;
    public static final int P_SIZE = 150;
    public static final int P_START = 153;
    public static final int P_START_INFO = 154;
    public static final int P_TYPE = 131;
    private static final String TAG = "PduPart";
    private byte[] mPartData;
    private Map<Integer, Object> mPartHeader;
    private Uri mUri;

    public PduPart() {
        this.mPartHeader = null;
        this.mUri = null;
        this.mPartData = null;
        this.mPartHeader = new HashMap();
    }

    /* JADX WARNING: Removed duplicated region for block: B:7:0x0060  */
    /* JADX WARNING: Removed duplicated region for block: B:6:0x0037  */
    public java.lang.String generateLocation() {
        /*
        r3 = this;
        r0 = r3.mPartHeader;
        r1 = 151; // 0x97 float:2.12E-43 double:7.46E-322;
        r1 = java.lang.Integer.valueOf(r1);
        r0 = r0.get(r1);
        r0 = (byte[]) r0;
        r0 = (byte[]) r0;
        if (r0 != 0) goto L_0x0066;
    L_0x0012:
        r0 = r3.mPartHeader;
        r1 = 152; // 0x98 float:2.13E-43 double:7.5E-322;
        r1 = java.lang.Integer.valueOf(r1);
        r0 = r0.get(r1);
        r0 = (byte[]) r0;
        r0 = (byte[]) r0;
        if (r0 != 0) goto L_0x0066;
    L_0x0024:
        r0 = r3.mPartHeader;
        r1 = 142; // 0x8e float:1.99E-43 double:7.0E-322;
        r1 = java.lang.Integer.valueOf(r1);
        r0 = r0.get(r1);
        r0 = (byte[]) r0;
        r0 = (byte[]) r0;
        r1 = r0;
    L_0x0035:
        if (r1 != 0) goto L_0x0060;
    L_0x0037:
        r0 = r3.mPartHeader;
        r1 = 192; // 0xc0 float:2.69E-43 double:9.5E-322;
        r1 = java.lang.Integer.valueOf(r1);
        r0 = r0.get(r1);
        r0 = (byte[]) r0;
        r0 = (byte[]) r0;
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "cid:";
        r1 = r1.append(r2);
        r2 = new java.lang.String;
        r2.<init>(r0);
        r0 = r1.append(r2);
        r0 = r0.toString();
    L_0x005f:
        return r0;
    L_0x0060:
        r0 = new java.lang.String;
        r0.<init>(r1);
        goto L_0x005f;
    L_0x0066:
        r1 = r0;
        goto L_0x0035;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.android.mms.pdu.PduPart.generateLocation():java.lang.String");
    }

    public int getCharset() {
        Integer num = (Integer) this.mPartHeader.get(Integer.valueOf(129));
        return num == null ? 0 : num.intValue();
    }

    public byte[] getContentDisposition() {
        return (byte[]) this.mPartHeader.get(Integer.valueOf(P_CONTENT_DISPOSITION));
    }

    public byte[] getContentId() {
        return (byte[]) this.mPartHeader.get(Integer.valueOf(192));
    }

    public byte[] getContentLocation() {
        return (byte[]) this.mPartHeader.get(Integer.valueOf(142));
    }

    public byte[] getContentTransferEncoding() {
        return (byte[]) this.mPartHeader.get(Integer.valueOf(P_CONTENT_TRANSFER_ENCODING));
    }

    public byte[] getContentType() {
        return (byte[]) this.mPartHeader.get(Integer.valueOf(145));
    }

    public byte[] getData() {
        if (this.mPartData == null) {
            return null;
        }
        byte[] bArr = new byte[this.mPartData.length];
        System.arraycopy(this.mPartData, 0, bArr, 0, this.mPartData.length);
        return bArr;
    }

    public int getDataLength() {
        return this.mPartData != null ? this.mPartData.length : 0;
    }

    public Uri getDataUri() {
        return this.mUri;
    }

    public byte[] getFilename() {
        return (byte[]) this.mPartHeader.get(Integer.valueOf(152));
    }

    public byte[] getName() {
        return (byte[]) this.mPartHeader.get(Integer.valueOf(151));
    }

    public void setCharset(int i) {
        this.mPartHeader.put(Integer.valueOf(129), Integer.valueOf(i));
    }

    public void setContentDisposition(byte[] bArr) {
        if (bArr == null) {
            throw new NullPointerException("null content-disposition");
        }
        this.mPartHeader.put(Integer.valueOf(P_CONTENT_DISPOSITION), bArr);
    }

    public void setContentId(byte[] bArr) {
        if (bArr == null || bArr.length == 0) {
            throw new IllegalArgumentException("Content-Id may not be null or empty.");
        } else if (bArr.length > 1 && ((char) bArr[0]) == '<' && ((char) bArr[bArr.length - 1]) == '>') {
            this.mPartHeader.put(Integer.valueOf(192), bArr);
        } else {
            byte[] bArr2 = new byte[(bArr.length + 2)];
            bArr2[0] = (byte) 60;
            bArr2[bArr2.length - 1] = (byte) 62;
            System.arraycopy(bArr, 0, bArr2, 1, bArr.length);
            this.mPartHeader.put(Integer.valueOf(192), bArr2);
        }
    }

    public void setContentLocation(byte[] bArr) {
        if (bArr == null) {
            throw new NullPointerException("null content-location");
        }
        this.mPartHeader.put(Integer.valueOf(142), bArr);
    }

    public void setContentTransferEncoding(byte[] bArr) {
        if (bArr == null) {
            throw new NullPointerException("null content-transfer-encoding");
        }
        this.mPartHeader.put(Integer.valueOf(P_CONTENT_TRANSFER_ENCODING), bArr);
    }

    public void setContentType(byte[] bArr) {
        if (bArr == null) {
            throw new NullPointerException("null content-type");
        }
        this.mPartHeader.put(Integer.valueOf(145), bArr);
    }

    public void setData(byte[] bArr) {
        if (bArr != null) {
            this.mPartData = new byte[bArr.length];
            System.arraycopy(bArr, 0, this.mPartData, 0, bArr.length);
        }
    }

    public void setDataUri(Uri uri) {
        this.mUri = uri;
    }

    public void setFilename(byte[] bArr) {
        if (bArr == null) {
            throw new NullPointerException("null content-id");
        }
        this.mPartHeader.put(Integer.valueOf(152), bArr);
    }

    public void setName(byte[] bArr) {
        if (bArr == null) {
            throw new NullPointerException("null content-id");
        }
        this.mPartHeader.put(Integer.valueOf(151), bArr);
    }
}
