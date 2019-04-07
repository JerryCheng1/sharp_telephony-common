package com.google.android.mms.pdu;

import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

public class EncodedStringValue implements Cloneable {
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;
    private static final String TAG = "EncodedStringValue";
    private int mCharacterSet;
    private byte[] mData;

    public EncodedStringValue(int i, byte[] bArr) {
        if (bArr == null) {
            throw new NullPointerException("EncodedStringValue: Text-string is null.");
        }
        this.mCharacterSet = i;
        this.mData = new byte[bArr.length];
        System.arraycopy(bArr, 0, this.mData, 0, bArr.length);
    }

    public EncodedStringValue(String str) {
        try {
            this.mData = str.getBytes("utf-8");
            this.mCharacterSet = 106;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Default encoding must be supported.", e);
        }
    }

    public EncodedStringValue(byte[] bArr) {
        this(106, bArr);
    }

    public static String concat(EncodedStringValue[] encodedStringValueArr) {
        StringBuilder stringBuilder = new StringBuilder();
        int length = encodedStringValueArr.length - 1;
        for (int i = 0; i <= length; i++) {
            stringBuilder.append(encodedStringValueArr[i].getString());
            if (i < length) {
                stringBuilder.append(";");
            }
        }
        return stringBuilder.toString();
    }

    public static EncodedStringValue copy(EncodedStringValue encodedStringValue) {
        return encodedStringValue == null ? null : new EncodedStringValue(encodedStringValue.mCharacterSet, encodedStringValue.mData);
    }

    public static EncodedStringValue[] encodeStrings(String[] strArr) {
        int length = strArr.length;
        if (length <= 0) {
            return null;
        }
        EncodedStringValue[] encodedStringValueArr = new EncodedStringValue[length];
        for (int i = 0; i < length; i++) {
            encodedStringValueArr[i] = new EncodedStringValue(strArr[i]);
        }
        return encodedStringValueArr;
    }

    public static EncodedStringValue[] extract(String str) {
        int i;
        String[] split = str.split(";");
        ArrayList arrayList = new ArrayList();
        for (i = 0; i < split.length; i++) {
            if (split[i].length() > 0) {
                arrayList.add(new EncodedStringValue(split[i]));
            }
        }
        i = arrayList.size();
        return i > 0 ? (EncodedStringValue[]) arrayList.toArray(new EncodedStringValue[i]) : null;
    }

    public void appendTextString(byte[] bArr) {
        if (bArr == null) {
            throw new NullPointerException("Text-string is null.");
        } else if (this.mData == null) {
            this.mData = new byte[bArr.length];
            System.arraycopy(bArr, 0, this.mData, 0, bArr.length);
        } else {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try {
                byteArrayOutputStream.write(this.mData);
                byteArrayOutputStream.write(bArr);
                this.mData = byteArrayOutputStream.toByteArray();
            } catch (IOException e) {
                e.printStackTrace();
                throw new NullPointerException("appendTextString: failed when write a new Text-string");
            }
        }
    }

    public Object clone() throws CloneNotSupportedException {
        super.clone();
        int length = this.mData.length;
        byte[] bArr = new byte[length];
        System.arraycopy(this.mData, 0, bArr, 0, length);
        try {
            return new EncodedStringValue(this.mCharacterSet, bArr);
        } catch (Exception e) {
            Log.e(TAG, "failed to clone an EncodedStringValue: " + this);
            e.printStackTrace();
            throw new CloneNotSupportedException(e.getMessage());
        }
    }

    public int getCharacterSet() {
        return this.mCharacterSet;
    }

    public String getString() {
        if (this.mCharacterSet == 0) {
            return new String(this.mData);
        }
        try {
            return new String(this.mData, CharacterSets.getMimeName(this.mCharacterSet));
        } catch (UnsupportedEncodingException e) {
            try {
                return new String(this.mData, CharacterSets.MIMENAME_ISO_8859_1);
            } catch (UnsupportedEncodingException e2) {
                return new String(this.mData);
            }
        }
    }

    public byte[] getTextString() {
        byte[] bArr = new byte[this.mData.length];
        System.arraycopy(this.mData, 0, bArr, 0, this.mData.length);
        return bArr;
    }

    public void setCharacterSet(int i) {
        this.mCharacterSet = i;
    }

    public void setTextString(byte[] bArr) {
        if (bArr == null) {
            throw new NullPointerException("EncodedStringValue: Text-string is null.");
        }
        this.mData = new byte[bArr.length];
        System.arraycopy(bArr, 0, this.mData, 0, bArr.length);
    }

    public EncodedStringValue[] split(String str) {
        String[] split = getString().split(str);
        EncodedStringValue[] encodedStringValueArr = new EncodedStringValue[split.length];
        int i = 0;
        while (i < encodedStringValueArr.length) {
            try {
                encodedStringValueArr[i] = new EncodedStringValue(this.mCharacterSet, split[i].getBytes());
                i++;
            } catch (NullPointerException e) {
                return null;
            }
        }
        return encodedStringValueArr;
    }
}
