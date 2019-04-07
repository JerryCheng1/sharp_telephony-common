package com.android.internal.telephony.gsm;

import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.util.Log;
import android.util.Pair;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.TelBrand;
import com.google.android.mms.pdu.CharacterSets;
import java.io.UnsupportedEncodingException;

public class GsmSmsCbMessage {
    private static final char CARRIAGE_RETURN = '\r';
    private static final String[] LANGUAGE_CODES_GROUP_0 = new String[]{"de", "en", "it", "fr", "es", "nl", "sv", "da", "pt", "fi", "no", "el", "tr", "hu", "pl", null};
    private static final String[] LANGUAGE_CODES_GROUP_2 = new String[]{"cs", "he", "ar", "ru", "is", null, null, null, null, null, null, null, null, null, null, null};
    private static final String LOG_TAG = "GsmSmsCbMessage";
    private static final int PDU_BODY_PAGE_LENGTH = 82;

    private GsmSmsCbMessage() {
    }

    public static SmsCbMessage createSmsCbMessage(SmsCbLocation smsCbLocation, byte[][] bArr) throws IllegalArgumentException {
        return createSmsCbMessage(new SmsCbHeader(bArr[0]), smsCbLocation, bArr);
    }

    static SmsCbMessage createSmsCbMessage(SmsCbHeader smsCbHeader, SmsCbLocation smsCbLocation, byte[][] bArr) throws IllegalArgumentException {
        String str = null;
        int i = 3;
        if (smsCbHeader.isEtwsPrimaryNotification()) {
            return new SmsCbMessage(1, smsCbHeader.getGeographicalScope(), smsCbHeader.getSerialNumber(), smsCbLocation, smsCbHeader.getServiceCategory(), null, "ETWS", 3, smsCbHeader.getEtwsInfo(), smsCbHeader.getCmasInfo());
        }
        StringBuilder stringBuilder = new StringBuilder();
        int length = bArr.length;
        int i2 = 0;
        while (i2 < length) {
            Pair parseBody = parseBody(smsCbHeader, bArr[i2]);
            String str2 = (String) parseBody.first;
            stringBuilder.append((String) parseBody.second);
            i2++;
            str = str2;
        }
        if (!smsCbHeader.isEmergencyMessage()) {
            i = 0;
        }
        return new SmsCbMessage(1, smsCbHeader.getGeographicalScope(), smsCbHeader.getSerialNumber(), smsCbLocation, smsCbHeader.getServiceCategory(), str, stringBuilder.toString(), i, smsCbHeader.getEtwsInfo(), smsCbHeader.getCmasInfo());
    }

    private static Pair<String, String> parseBody(SmsCbHeader smsCbHeader, byte[] bArr) {
        boolean z;
        int i = 3;
        String str = null;
        int dataCodingScheme = smsCbHeader.getDataCodingScheme();
        switch ((dataCodingScheme & 240) >> 4) {
            case 0:
                str = LANGUAGE_CODES_GROUP_0[dataCodingScheme & 15];
                z = false;
                i = 1;
                break;
            case 1:
                if ((dataCodingScheme & 15) != 1) {
                    z = true;
                    i = 1;
                    break;
                }
                z = true;
                break;
            case 2:
                str = LANGUAGE_CODES_GROUP_2[dataCodingScheme & 15];
                z = false;
                i = 1;
                break;
            case 3:
                z = false;
                i = 1;
                break;
            case 4:
            case 5:
                switch ((dataCodingScheme & 12) >> 2) {
                    case 1:
                        z = false;
                        i = 2;
                        break;
                    case 2:
                        z = false;
                        break;
                    default:
                        z = false;
                        i = 1;
                        break;
                }
            case 6:
            case 7:
            case 9:
            case 14:
                throw new IllegalArgumentException("Unsupported GSM dataCodingScheme " + dataCodingScheme);
            case 15:
                if (((dataCodingScheme & 4) >> 2) != 1) {
                    z = false;
                    i = 1;
                    break;
                }
                z = false;
                i = 2;
                break;
            default:
                z = false;
                i = 1;
                break;
        }
        if (smsCbHeader.isUmtsFormat()) {
            byte b = bArr[6];
            if (bArr.length < (b * 83) + 7) {
                throw new IllegalArgumentException("Pdu length " + bArr.length + " does not match " + b + " pages");
            }
            StringBuilder stringBuilder = new StringBuilder();
            byte b2 = (byte) 0;
            while (b2 < b) {
                int i2 = (b2 * 83) + 7;
                byte b3 = bArr[i2 + 82];
                if (b3 > (byte) 82) {
                    throw new IllegalArgumentException("Page length " + b3 + " exceeds maximum value " + 82);
                }
                Pair unpackBody = unpackBody(bArr, i, i2, b3, z, str);
                String str2 = (String) unpackBody.first;
                stringBuilder.append((String) unpackBody.second);
                b2++;
                str = str2;
            }
            return new Pair(str, stringBuilder.toString());
        }
        return unpackBody(bArr, i, 6, bArr.length - 6, z, str);
    }

    private static Pair<String, String> unpackBody(byte[] bArr, int i, int i2, int i3, boolean z, String str) {
        Object str2;
        int length;
        Object obj = null;
        switch (i) {
            case 1:
                obj = GsmAlphabet.gsm7BitPackedToString(bArr, i2, (i3 * 8) / 7);
                if (z && obj != null && obj.length() > 2) {
                    str2 = obj.substring(0, 2);
                    obj = obj.substring(3);
                    break;
                }
            case 3:
                int i4;
                if (TelBrand.IS_DCM) {
                    length = bArr.length - 1;
                    int i5 = 0;
                    while (length > i2 && ((bArr[length] == (byte) 0 && bArr[length - 1] == (byte) 0) || (bArr[length] == (byte) 13 && bArr[length - 1] == (byte) 13))) {
                        length -= 2;
                        i5 += 2;
                    }
                    Log.d(LOG_TAG, "16bit-encode. length: " + i3 + ", padding byte: " + i5 + ".");
                    i4 = i3 - i5;
                } else {
                    i4 = i3;
                }
                if (z && bArr.length >= i2 + 2) {
                    str2 = GsmAlphabet.gsm7BitPackedToString(bArr, i2, 2);
                    i2 += 2;
                    i4 -= 2;
                }
                try {
                    String obj2 = new String(bArr, i2, i4 & 65534, CharacterSets.MIMENAME_UTF_16);
                    break;
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalArgumentException("Error decoding UTF-16 message", e);
                }
                break;
        }
        if (obj2 != null) {
            length = obj2.length() - 1;
            while (length >= 0) {
                if (obj2.charAt(length) != CARRIAGE_RETURN) {
                    obj2 = obj2.substring(0, length + 1);
                } else {
                    length--;
                }
            }
        } else {
            obj2 = "";
        }
        return new Pair(str2, obj2);
    }
}
