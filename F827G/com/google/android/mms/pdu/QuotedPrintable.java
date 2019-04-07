package com.google.android.mms.pdu;

import java.io.ByteArrayOutputStream;

public class QuotedPrintable {
    private static byte ESCAPE_CHAR = (byte) 61;

    public static final byte[] decodeQuotedPrintable(byte[] bArr) {
        if (bArr != null) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int i = 0;
            while (i < bArr.length) {
                byte b = bArr[i];
                if (b != ESCAPE_CHAR) {
                    byteArrayOutputStream.write(b);
                } else if (13 == ((char) bArr[i + 1]) && 10 == ((char) bArr[i + 2])) {
                    i += 2;
                } else {
                    i++;
                    try {
                        int digit = Character.digit((char) bArr[i], 16);
                        i++;
                        int digit2 = Character.digit((char) bArr[i], 16);
                        if (!(digit == -1 || digit2 == -1)) {
                            byteArrayOutputStream.write((char) ((digit << 4) + digit2));
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        return null;
                    }
                }
                i++;
            }
            return byteArrayOutputStream.toByteArray();
        }
        return null;
    }
}
