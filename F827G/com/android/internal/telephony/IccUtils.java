package com.android.internal.telephony;

import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.telephony.Rlog;
import com.google.android.mms.pdu.CharacterSets;
import java.io.UnsupportedEncodingException;

public class IccUtils {
    static final String LOG_TAG = "IccUtils";

    public static String adnStringFieldToString(byte[] bArr, int i, int i2) {
        int i3 = 1;
        if (i2 == 0) {
            return "";
        }
        int length;
        int i4;
        int i5;
        int i6;
        if (i2 >= 1 && bArr[i] == Byte.MIN_VALUE) {
            String str = null;
            try {
                str = new String(bArr, i + 1, ((i2 - 1) / 2) * 2, "utf-16be");
            } catch (UnsupportedEncodingException e) {
                Rlog.e(LOG_TAG, "implausible UnsupportedEncodingException", e);
            }
            if (str != null) {
                length = str.length();
                while (length > 0 && str.charAt(length - 1) == 65535) {
                    length--;
                }
                return str.substring(0, length);
            }
        }
        if (i2 >= 3 && bArr[i] == (byte) -127) {
            length = bArr[i + 1] & 255;
            if (length > i2 - 3) {
                length = i2 - 3;
            }
            i4 = (char) ((bArr[i + 2] & 255) << 7);
            i5 = i + 3;
            i6 = length;
        } else if (i2 < 4 || bArr[i] != (byte) -126) {
            i3 = 0;
            i4 = 0;
            i5 = i;
            i6 = 0;
        } else {
            length = bArr[i + 1] & 255;
            if (length > i2 - 4) {
                length = i2 - 4;
            }
            i4 = (char) (((bArr[i + 2] & 255) << 8) | (bArr[i + 3] & 255));
            i5 = i + 4;
            i6 = length;
        }
        if (i3 != 0) {
            StringBuilder stringBuilder = new StringBuilder();
            length = i5;
            while (i6 > 0) {
                if (bArr[length] < (byte) 0) {
                    stringBuilder.append((char) ((bArr[length] & 127) + i4));
                    length++;
                    i6--;
                }
                i5 = 0;
                while (i5 < i6 && bArr[length + i5] >= (byte) 0) {
                    i5++;
                }
                stringBuilder.append(GsmAlphabet.gsm8BitUnpackedToString(bArr, length, i5));
                length += i5;
                i6 -= i5;
            }
            return stringBuilder.toString();
        }
        String str2 = "";
        try {
            str2 = Resources.getSystem().getString(17039440);
        } catch (NotFoundException e2) {
        }
        return GsmAlphabet.gsm8BitUnpackedToString(bArr, i5, i2, str2.trim());
    }

    public static String bcdToString(byte[] bArr, int i, int i2) {
        StringBuilder stringBuilder = new StringBuilder(i2 * 2);
        for (int i3 = i; i3 < i + i2; i3++) {
            int i4 = bArr[i3] & 15;
            if (i4 > 9) {
                break;
            }
            stringBuilder.append((char) (i4 + 48));
            i4 = (bArr[i3] >> 4) & 15;
            if (i4 != 15) {
                if (i4 > 9) {
                    break;
                }
                stringBuilder.append((char) (i4 + 48));
            }
        }
        return stringBuilder.toString();
    }

    private static int bitToRGB(int i) {
        return i == 1 ? -1 : -16777216;
    }

    public static String bytesToHexString(byte[] bArr) {
        if (bArr == null) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder(bArr.length * 2);
        for (int i = 0; i < bArr.length; i++) {
            stringBuilder.append("0123456789abcdef".charAt((bArr[i] >> 4) & 15));
            stringBuilder.append("0123456789abcdef".charAt(bArr[i] & 15));
        }
        return stringBuilder.toString();
    }

    public static int cdmaBcdByteToInt(byte b) {
        int i = 0;
        if ((b & 240) <= 144) {
            i = ((b >> 4) & 15) * 10;
        }
        return (b & 15) <= 9 ? i + (b & 15) : i;
    }

    public static String cdmaBcdToString(byte[] bArr, int i, int i2) {
        StringBuilder stringBuilder = new StringBuilder(i2);
        int i3 = 0;
        while (i3 < i2) {
            int i4 = bArr[i] & 15;
            if (i4 > 9) {
                i4 = 0;
            }
            stringBuilder.append((char) (i4 + 48));
            i3++;
            if (i3 == i2) {
                break;
            }
            i4 = (bArr[i] >> 4) & 15;
            if (i4 > 9) {
                i4 = 0;
            }
            stringBuilder.append((char) (i4 + 48));
            i++;
            i3++;
        }
        return stringBuilder.toString();
    }

    private static int[] getCLUT(byte[] bArr, int i, int i2) {
        if (bArr == null) {
            return null;
        }
        int[] iArr = new int[i2];
        int i3 = 0;
        int i4 = i;
        while (true) {
            int i5 = i4 + 1;
            byte b = bArr[i4];
            int i6 = i5 + 1;
            i4 = i6 + 1;
            iArr[i3] = (((bArr[i5] & 255) << 8) | (((b & 255) << 16) | -16777216)) | (bArr[i6] & 255);
            if (i4 >= (i2 * 3) + i) {
                return iArr;
            }
            i3++;
        }
    }

    public static int gsmBcdByteToInt(byte b) {
        int i = 0;
        if ((b & 240) <= 144) {
            i = (b >> 4) & 15;
        }
        return (b & 15) <= 9 ? i + ((b & 15) * 10) : i;
    }

    static int hexCharToInt(char c) {
        if (c >= '0' && c <= '9') {
            return c - 48;
        }
        if (c >= 'A' && c <= 'F') {
            return (c - 65) + 10;
        }
        if (c >= 'a' && c <= 'f') {
            return (c - 97) + 10;
        }
        throw new RuntimeException("invalid hex char '" + c + "'");
    }

    public static byte[] hexStringToBytes(String str) {
        if (str == null) {
            return null;
        }
        int length = str.length();
        byte[] bArr = new byte[(length / 2)];
        for (int i = 0; i < length; i += 2) {
            bArr[i / 2] = (byte) ((hexCharToInt(str.charAt(i)) << 4) | hexCharToInt(str.charAt(i + 1)));
        }
        return bArr;
    }

    private static int[] mapTo2OrderBitColor(byte[] bArr, int i, int i2, int[] iArr, int i3) {
        int i4 = 1;
        if (8 % i3 != 0) {
            Rlog.e(LOG_TAG, "not event number of color");
            return mapToNon2OrderBitColor(bArr, i, i2, iArr, i3);
        }
        switch (i3) {
            case 2:
                i4 = 3;
                break;
            case 4:
                i4 = 15;
                break;
            case 8:
                i4 = 255;
                break;
        }
        int[] iArr2 = new int[i2];
        int i5 = 8 / i3;
        int i6 = 0;
        while (i6 < i2) {
            byte b = bArr[i];
            int i7 = 0;
            while (i7 < i5) {
                iArr2[i6] = iArr[(b >> (((i5 - i7) - 1) * i3)) & i4];
                i7++;
                i6++;
            }
            i++;
        }
        return iArr2;
    }

    private static int[] mapToNon2OrderBitColor(byte[] bArr, int i, int i2, int[] iArr, int i3) {
        if (8 % i3 != 0) {
            return new int[i2];
        }
        Rlog.e(LOG_TAG, "not odd number of color");
        return mapTo2OrderBitColor(bArr, i, i2, iArr, i3);
    }

    public static String networkNameToString(byte[] bArr, int i, int i2) {
        if ((bArr[i] & 128) != 128 || i2 < 1) {
            return "";
        }
        String gsm7BitPackedToString;
        switch ((bArr[i] >>> 4) & 7) {
            case 0:
                gsm7BitPackedToString = GsmAlphabet.gsm7BitPackedToString(bArr, i + 1, (((i2 - 1) * 8) - (bArr[i] & 7)) / 7);
                break;
            case 1:
                try {
                    gsm7BitPackedToString = new String(bArr, i + 1, i2 - 1, CharacterSets.MIMENAME_UTF_16);
                    break;
                } catch (UnsupportedEncodingException e) {
                    Throwable th = e;
                    gsm7BitPackedToString = "";
                    Rlog.e(LOG_TAG, "implausible UnsupportedEncodingException", th);
                    break;
                }
            default:
                gsm7BitPackedToString = "";
                break;
        }
        if ((bArr[i] & 64) != 0) {
        }
        return gsm7BitPackedToString;
    }

    public static Bitmap parseToBnW(byte[] bArr, int i) {
        int i2 = bArr[0] & 255;
        int i3 = bArr[1] & 255;
        int i4 = i2 * i3;
        int[] iArr = new int[i4];
        int i5 = 2;
        int i6 = 0;
        int i7 = 0;
        int i8 = 7;
        while (i7 < i4) {
            int i9;
            if (i7 % 8 == 0) {
                i9 = i5 + 1;
                i6 = bArr[i5];
                i8 = 7;
            } else {
                i9 = i5;
            }
            iArr[i7] = bitToRGB((i6 >> i8) & 1);
            i8--;
            i7++;
            i5 = i9;
        }
        if (i7 != i4) {
            Rlog.e(LOG_TAG, "parse end and size error");
        }
        return Bitmap.createBitmap(iArr, i2, i3, Config.ARGB_8888);
    }

    public static Bitmap parseToRGB(byte[] bArr, int i, boolean z) {
        int i2 = bArr[0] & 255;
        int i3 = bArr[1] & 255;
        int i4 = bArr[2] & 255;
        int i5 = bArr[3] & 255;
        int[] clut = getCLUT(bArr, ((bArr[4] & 255) << 8) | (bArr[5] & 255), i5);
        if (true == z) {
            clut[i5 - 1] = 0;
        }
        return Bitmap.createBitmap(8 % i4 == 0 ? mapTo2OrderBitColor(bArr, 6, i2 * i3, clut, i4) : mapToNon2OrderBitColor(bArr, 6, i2 * i3, clut, i4), i2, i3, Config.RGB_565);
    }
}
