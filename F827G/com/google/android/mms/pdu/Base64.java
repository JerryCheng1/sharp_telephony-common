package com.google.android.mms.pdu;

public class Base64 {
    static final int BASELENGTH = 255;
    static final int FOURBYTE = 4;
    static final byte PAD = (byte) 61;
    private static byte[] base64Alphabet = new byte[255];

    static {
        int i;
        for (i = 0; i < 255; i++) {
            base64Alphabet[i] = (byte) -1;
        }
        for (i = 90; i >= 65; i--) {
            base64Alphabet[i] = (byte) (i - 65);
        }
        for (i = 122; i >= 97; i--) {
            base64Alphabet[i] = (byte) ((i - 97) + 26);
        }
        for (i = 57; i >= 48; i--) {
            base64Alphabet[i] = (byte) ((i - 48) + 52);
        }
        base64Alphabet[43] = (byte) 62;
        base64Alphabet[47] = (byte) 63;
    }

    public static byte[] decodeBase64(byte[] bArr) {
        byte[] discardNonBase64 = discardNonBase64(bArr);
        if (discardNonBase64.length == 0) {
            return new byte[0];
        }
        int length = discardNonBase64.length / 4;
        int length2 = discardNonBase64.length;
        while (discardNonBase64[length2 - 1] == PAD) {
            length2--;
            if (length2 == 0) {
                return new byte[0];
            }
        }
        byte[] bArr2 = new byte[(length2 - length)];
        int i = 0;
        for (int i2 = 0; i2 < length; i2++) {
            int i3 = i2 * 4;
            byte b = discardNonBase64[i3 + 2];
            byte b2 = discardNonBase64[i3 + 3];
            byte b3 = base64Alphabet[discardNonBase64[i3]];
            byte b4 = base64Alphabet[discardNonBase64[i3 + 1]];
            if (b != PAD && b2 != PAD) {
                b = base64Alphabet[b];
                b2 = base64Alphabet[b2];
                bArr2[i] = (byte) ((b3 << 2) | (b4 >> 4));
                bArr2[i + 1] = (byte) (((b4 & 15) << 4) | ((b >> 2) & 15));
                bArr2[i + 2] = (byte) ((b << 6) | b2);
            } else if (b == PAD) {
                bArr2[i] = (byte) ((b4 >> 4) | (b3 << 2));
            } else if (b2 == PAD) {
                b = base64Alphabet[b];
                bArr2[i] = (byte) ((b3 << 2) | (b4 >> 4));
                bArr2[i + 1] = (byte) (((b4 & 15) << 4) | ((b >> 2) & 15));
            }
            i += 3;
        }
        return bArr2;
    }

    static byte[] discardNonBase64(byte[] bArr) {
        byte[] bArr2 = new byte[bArr.length];
        int i = 0;
        for (int i2 = 0; i2 < bArr.length; i2++) {
            if (isBase64(bArr[i2])) {
                bArr2[i] = bArr[i2];
                i++;
            }
        }
        byte[] bArr3 = new byte[i];
        System.arraycopy(bArr2, 0, bArr3, 0, i);
        return bArr3;
    }

    private static boolean isBase64(byte b) {
        return b == PAD || base64Alphabet[b] != (byte) -1;
    }
}
