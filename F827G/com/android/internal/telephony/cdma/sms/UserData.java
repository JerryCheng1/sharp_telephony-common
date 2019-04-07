package com.android.internal.telephony.cdma.sms;

import android.util.SparseIntArray;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.util.HexDump;

public class UserData {
    public static final int ASCII_CR_INDEX = 13;
    public static final char[] ASCII_MAP = new char[]{' ', '!', '\"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':', ';', '<', '=', '>', '?', '@', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '[', '\\', ']', '^', '_', '`', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '{', '|', '}', '~'};
    public static final int ASCII_MAP_BASE_INDEX = 32;
    public static final int ASCII_MAP_MAX_INDEX = ((ASCII_MAP.length + 32) - 1);
    public static final int ASCII_NL_INDEX = 10;
    public static final int ENCODING_7BIT_ASCII = 2;
    public static final int ENCODING_GSM_7BIT_ALPHABET = 9;
    public static final int ENCODING_GSM_DCS = 10;
    public static final int ENCODING_GSM_DCS_16BIT = 2;
    public static final int ENCODING_GSM_DCS_7BIT = 0;
    public static final int ENCODING_GSM_DCS_8BIT = 1;
    public static final int ENCODING_IA5 = 3;
    public static final int ENCODING_IS91_EXTENDED_PROTOCOL = 1;
    public static final int ENCODING_KOREAN = 6;
    public static final int ENCODING_LATIN = 8;
    public static final int ENCODING_LATIN_HEBREW = 7;
    public static final int ENCODING_OCTET = 0;
    public static final int ENCODING_SHIFT_JIS = 5;
    public static final int ENCODING_UNICODE_16 = 4;
    public static final int IS91_MSG_TYPE_CLI = 132;
    public static final int IS91_MSG_TYPE_SHORT_MESSAGE = 133;
    public static final int IS91_MSG_TYPE_SHORT_MESSAGE_FULL = 131;
    public static final int IS91_MSG_TYPE_VOICEMAIL_STATUS = 130;
    public static final int PRINTABLE_ASCII_MIN_INDEX = 32;
    static final byte UNENCODABLE_7_BIT_CHAR = (byte) 32;
    public static final SparseIntArray charToAscii = new SparseIntArray();
    public int msgEncoding;
    public boolean msgEncodingSet = false;
    public int msgType;
    public int numFields;
    public int paddingBits;
    public byte[] payload;
    public String payloadStr;
    public SmsHeader userDataHeader;

    static {
        for (int i = 0; i < ASCII_MAP.length; i++) {
            charToAscii.put(ASCII_MAP[i], i + 32);
        }
        charToAscii.put(10, 10);
        charToAscii.put(13, 13);
    }

    public static byte[] stringToAscii(String str) {
        int length = str.length();
        byte[] bArr = new byte[length];
        for (int i = 0; i < length; i++) {
            int i2 = charToAscii.get(str.charAt(i), -1);
            if (i2 == -1) {
                return null;
            }
            bArr[i] = (byte) i2;
        }
        return bArr;
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("UserData ");
        stringBuilder.append("{ msgEncoding=" + (this.msgEncodingSet ? Integer.valueOf(this.msgEncoding) : "unset"));
        stringBuilder.append(", msgType=" + this.msgType);
        stringBuilder.append(", paddingBits=" + this.paddingBits);
        stringBuilder.append(", numFields=" + this.numFields);
        stringBuilder.append(", userDataHeader=" + this.userDataHeader);
        stringBuilder.append(", payload='" + HexDump.toHexString(this.payload) + "'");
        stringBuilder.append(", payloadStr='" + this.payloadStr + "'");
        stringBuilder.append(" }");
        return stringBuilder.toString();
    }
}
