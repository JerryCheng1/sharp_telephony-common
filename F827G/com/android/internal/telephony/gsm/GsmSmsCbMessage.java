package com.android.internal.telephony.gsm;

import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.util.Log;
import android.util.Pair;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.TelBrand;
import com.google.android.mms.pdu.CharacterSets;
import java.io.UnsupportedEncodingException;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class GsmSmsCbMessage {
    private static final char CARRIAGE_RETURN = '\r';
    private static final String[] LANGUAGE_CODES_GROUP_0 = {"de", "en", "it", "fr", "es", "nl", "sv", "da", "pt", "fi", "no", "el", "tr", "hu", "pl", null};
    private static final String[] LANGUAGE_CODES_GROUP_2 = {"cs", "he", "ar", "ru", "is", null, null, null, null, null, null, null, null, null, null, null};
    private static final String LOG_TAG = "GsmSmsCbMessage";
    private static final int PDU_BODY_PAGE_LENGTH = 82;

    private GsmSmsCbMessage() {
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static SmsCbMessage createSmsCbMessage(SmsCbHeader header, SmsCbLocation location, byte[][] pdus) throws IllegalArgumentException {
        if (header.isEtwsPrimaryNotification()) {
            return new SmsCbMessage(1, header.getGeographicalScope(), header.getSerialNumber(), location, header.getServiceCategory(), null, "ETWS", 3, header.getEtwsInfo(), header.getCmasInfo());
        }
        String language = null;
        StringBuilder sb = new StringBuilder();
        for (byte[] pdu : pdus) {
            Pair<String, String> p = parseBody(header, pdu);
            language = (String) p.first;
            sb.append((String) p.second);
        }
        return new SmsCbMessage(1, header.getGeographicalScope(), header.getSerialNumber(), location, header.getServiceCategory(), language, sb.toString(), header.isEmergencyMessage() ? 3 : 0, header.getEtwsInfo(), header.getCmasInfo());
    }

    public static SmsCbMessage createSmsCbMessage(SmsCbLocation location, byte[][] pdus) throws IllegalArgumentException {
        return createSmsCbMessage(new SmsCbHeader(pdus[0]), location, pdus);
    }

    private static Pair<String, String> parseBody(SmsCbHeader header, byte[] pdu) {
        int encoding;
        String language = null;
        boolean hasLanguageIndicator = false;
        int dataCodingScheme = header.getDataCodingScheme();
        switch ((dataCodingScheme & 240) >> 4) {
            case 0:
                encoding = 1;
                language = LANGUAGE_CODES_GROUP_0[dataCodingScheme & 15];
                break;
            case 1:
                hasLanguageIndicator = true;
                if ((dataCodingScheme & 15) == 1) {
                    encoding = 3;
                    break;
                } else {
                    encoding = 1;
                    break;
                }
            case 2:
                encoding = 1;
                language = LANGUAGE_CODES_GROUP_2[dataCodingScheme & 15];
                break;
            case 3:
                encoding = 1;
                break;
            case 4:
            case 5:
                switch ((dataCodingScheme & 12) >> 2) {
                    case 1:
                        encoding = 2;
                        break;
                    case 2:
                        encoding = 3;
                        break;
                    default:
                        encoding = 1;
                        break;
                }
            case 6:
            case 7:
            case 9:
            case 14:
                throw new IllegalArgumentException("Unsupported GSM dataCodingScheme " + dataCodingScheme);
            case 8:
            case 10:
            case 11:
            case 12:
            case 13:
            default:
                encoding = 1;
                break;
            case 15:
                if (((dataCodingScheme & 4) >> 2) == 1) {
                    encoding = 2;
                    break;
                } else {
                    encoding = 1;
                    break;
                }
        }
        if (!header.isUmtsFormat()) {
            return unpackBody(pdu, encoding, 6, pdu.length - 6, hasLanguageIndicator, language);
        }
        byte b = pdu[6];
        if (pdu.length < (b * 83) + 7) {
            throw new IllegalArgumentException("Pdu length " + pdu.length + " does not match " + ((int) b) + " pages");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b; i++) {
            int offset = (i * 83) + 7;
            byte b2 = pdu[offset + 82];
            if (b2 > 82) {
                throw new IllegalArgumentException("Page length " + ((int) b2) + " exceeds maximum value 82");
            }
            Pair<String, String> p = unpackBody(pdu, encoding, offset, b2, hasLanguageIndicator, language);
            language = (String) p.first;
            sb.append((String) p.second);
        }
        return new Pair<>(language, sb.toString());
    }

    private static Pair<String, String> unpackBody(byte[] pdu, int encoding, int offset, int length, boolean hasLanguageIndicator, String language) {
        String body = null;
        switch (encoding) {
            case 1:
                body = GsmAlphabet.gsm7BitPackedToString(pdu, offset, (length * 8) / 7);
                if (hasLanguageIndicator && body != null && body.length() > 2) {
                    language = body.substring(0, 2);
                    body = body.substring(3);
                    break;
                }
                break;
            case 3:
                if (TelBrand.IS_DCM) {
                    int paddingByte = 0;
                    for (int i = pdu.length - 1; i > offset && ((pdu[i] == 0 && pdu[i - 1] == 0) || (pdu[i] == 13 && pdu[i - 1] == 13)); i -= 2) {
                        paddingByte += 2;
                    }
                    Log.d(LOG_TAG, "16bit-encode. length: " + length + ", padding byte: " + paddingByte + ".");
                    length -= paddingByte;
                }
                if (hasLanguageIndicator && pdu.length >= offset + 2) {
                    language = GsmAlphabet.gsm7BitPackedToString(pdu, offset, 2);
                    offset += 2;
                    length -= 2;
                }
                try {
                    body = new String(pdu, offset, 65534 & length, CharacterSets.MIMENAME_UTF_16);
                    break;
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalArgumentException("Error decoding UTF-16 message", e);
                }
                break;
        }
        if (body != null) {
            int i2 = body.length() - 1;
            while (true) {
                if (i2 >= 0) {
                    if (body.charAt(i2) != '\r') {
                        body = body.substring(0, i2 + 1);
                    } else {
                        i2--;
                    }
                }
            }
        } else {
            body = "";
        }
        return new Pair<>(language, body);
    }
}
