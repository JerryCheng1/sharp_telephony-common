package com.google.android.mms.pdu;

import android.util.Log;
import com.android.internal.telephony.RadioNVItems;
import com.android.internal.telephony.cdma.SignalToneUtil;
import com.android.internal.telephony.gsm.CallFailCause;
import com.google.android.mms.ContentType;
import com.google.android.mms.InvalidHeaderValueException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_CDMA_NAM_Info;

public class PduParser {
    static final /* synthetic */ boolean $assertionsDisabled = (!PduParser.class.desiredAssertionStatus());
    private static final boolean DEBUG = false;
    private static final int END_STRING_FLAG = 0;
    private static final int LENGTH_QUOTE = 31;
    private static final boolean LOCAL_LOGV = false;
    private static final String LOG_TAG = "PduParser";
    private static final int LONG_INTEGER_LENGTH_MAX = 8;
    private static final int QUOTE = 127;
    private static final int QUOTED_STRING_FLAG = 34;
    private static final int SHORT_INTEGER_MAX = 127;
    private static final int SHORT_LENGTH_MAX = 30;
    private static final int TEXT_MAX = 127;
    private static final int TEXT_MIN = 32;
    private static final int THE_FIRST_PART = 0;
    private static final int THE_LAST_PART = 1;
    private static final int TYPE_QUOTED_STRING = 1;
    private static final int TYPE_TEXT_STRING = 0;
    private static final int TYPE_TOKEN_STRING = 2;
    private static byte[] mStartParam = null;
    private static byte[] mTypeParam = null;
    private PduBody mBody = null;
    private PduHeaders mHeaders = null;
    private final boolean mParseContentDisposition;
    private ByteArrayInputStream mPduDataStream = null;

    public PduParser(byte[] bArr, boolean z) {
        this.mPduDataStream = new ByteArrayInputStream(bArr);
        this.mParseContentDisposition = z;
    }

    protected static boolean checkMandatoryHeader(PduHeaders pduHeaders) {
        if (pduHeaders == null) {
            return false;
        }
        int octet = pduHeaders.getOctet(140);
        if (pduHeaders.getOctet(141) == 0) {
            return false;
        }
        switch (octet) {
            case 128:
                if (pduHeaders.getTextString(132) == null || pduHeaders.getEncodedStringValue(137) == null || pduHeaders.getTextString(152) == null) {
                    return false;
                }
            case 129:
                if (pduHeaders.getOctet(146) == 0 || pduHeaders.getTextString(152) == null) {
                    return false;
                }
            case 130:
                if (pduHeaders.getTextString(131) == null || -1 == pduHeaders.getLongInteger(136) || pduHeaders.getTextString(138) == null || -1 == pduHeaders.getLongInteger(142) || pduHeaders.getTextString(152) == null) {
                    return false;
                }
            case 131:
                if (pduHeaders.getOctet(149) == 0 || pduHeaders.getTextString(152) == null) {
                    return false;
                }
            case 132:
                if (pduHeaders.getTextString(132) == null || -1 == pduHeaders.getLongInteger(133)) {
                    return false;
                }
            case 133:
                if (pduHeaders.getTextString(152) == null) {
                    return false;
                }
                break;
            case 134:
                if (-1 == pduHeaders.getLongInteger(133) || pduHeaders.getTextString(139) == null || pduHeaders.getOctet(149) == 0 || pduHeaders.getEncodedStringValues(151) == null) {
                    return false;
                }
            case 135:
                if (pduHeaders.getEncodedStringValue(137) == null || pduHeaders.getTextString(139) == null || pduHeaders.getOctet(155) == 0 || pduHeaders.getEncodedStringValues(151) == null) {
                    return false;
                }
            case 136:
                if (-1 == pduHeaders.getLongInteger(133) || pduHeaders.getEncodedStringValue(137) == null || pduHeaders.getTextString(139) == null || pduHeaders.getOctet(155) == 0 || pduHeaders.getEncodedStringValues(151) == null) {
                    return false;
                }
            default:
                return false;
        }
        return true;
    }

    private static int checkPartPosition(PduPart pduPart) {
        if ($assertionsDisabled || pduPart != null) {
            if (!(mTypeParam == null && mStartParam == null)) {
                byte[] contentId;
                if (mStartParam != null) {
                    contentId = pduPart.getContentId();
                    if (contentId != null && true == Arrays.equals(mStartParam, contentId)) {
                        return 0;
                    }
                }
                if (mTypeParam != null) {
                    contentId = pduPart.getContentType();
                    if (contentId != null && true == Arrays.equals(mTypeParam, contentId)) {
                        return 0;
                    }
                }
            }
            return 1;
        }
        throw new AssertionError();
    }

    protected static int extractByteValue(ByteArrayInputStream byteArrayInputStream) {
        if ($assertionsDisabled || byteArrayInputStream != null) {
            int read = byteArrayInputStream.read();
            if ($assertionsDisabled || -1 != read) {
                return read & 255;
            }
            throw new AssertionError();
        }
        throw new AssertionError();
    }

    protected static byte[] getWapString(ByteArrayInputStream byteArrayInputStream, int i) {
        if ($assertionsDisabled || byteArrayInputStream != null) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int read = byteArrayInputStream.read();
            if ($assertionsDisabled || -1 != read) {
                while (-1 != read && read != 0) {
                    if (i == 2) {
                        if (isTokenCharacter(read)) {
                            byteArrayOutputStream.write(read);
                        }
                    } else if (isText(read)) {
                        byteArrayOutputStream.write(read);
                    }
                    read = byteArrayInputStream.read();
                    if (!$assertionsDisabled && -1 == read) {
                        throw new AssertionError();
                    }
                }
                return byteArrayOutputStream.size() > 0 ? byteArrayOutputStream.toByteArray() : null;
            } else {
                throw new AssertionError();
            }
        }
        throw new AssertionError();
    }

    protected static boolean isText(int i) {
        if ((i < 32 || i > OEM_RIL_CDMA_NAM_Info.SIZE) && (i < 128 || i > 255)) {
            switch (i) {
                case 9:
                case 10:
                case 13:
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    protected static boolean isTokenCharacter(int i) {
        if (i >= 33 && i <= OEM_RIL_CDMA_NAM_Info.SIZE) {
            switch (i) {
                case 34:
                case 40:
                case 41:
                case CallFailCause.CHANNEL_NOT_AVAIL /*44*/:
                case 47:
                case 58:
                case RadioNVItems.RIL_NV_CDMA_EHRPD_FORCED /*59*/:
                case 60:
                case 61:
                case 62:
                case SignalToneUtil.IS95_CONST_IR_SIG_TONE_NO_TONE /*63*/:
                case 64:
                case 91:
                case 92:
                case 93:
                case 123:
                case 125:
                    break;
                default:
                    return true;
            }
        }
        return false;
    }

    private static void log(String str) {
    }

    protected static byte[] parseContentType(ByteArrayInputStream byteArrayInputStream, HashMap<Integer, Object> hashMap) {
        if ($assertionsDisabled || byteArrayInputStream != null) {
            byteArrayInputStream.mark(1);
            int read = byteArrayInputStream.read();
            if ($assertionsDisabled || -1 != read) {
                byteArrayInputStream.reset();
                read &= 255;
                if (read >= 32) {
                    return read <= 127 ? parseWapString(byteArrayInputStream, 0) : PduContentTypes.contentTypes[parseShortInteger(byteArrayInputStream)].getBytes();
                } else {
                    int parseValueLength = parseValueLength(byteArrayInputStream);
                    int available = byteArrayInputStream.available();
                    byteArrayInputStream.mark(1);
                    read = byteArrayInputStream.read();
                    if ($assertionsDisabled || -1 != read) {
                        byte[] parseWapString;
                        byteArrayInputStream.reset();
                        read &= 255;
                        if (read >= 32 && read <= 127) {
                            parseWapString = parseWapString(byteArrayInputStream, 0);
                        } else if (read > 127) {
                            read = parseShortInteger(byteArrayInputStream);
                            if (read < PduContentTypes.contentTypes.length) {
                                parseWapString = PduContentTypes.contentTypes[read].getBytes();
                            } else {
                                byteArrayInputStream.reset();
                                parseWapString = parseWapString(byteArrayInputStream, 0);
                            }
                        } else {
                            Log.e(LOG_TAG, "Corrupt content-type");
                            return PduContentTypes.contentTypes[0].getBytes();
                        }
                        parseValueLength -= available - byteArrayInputStream.available();
                        if (parseValueLength > 0) {
                            parseContentTypeParams(byteArrayInputStream, hashMap, Integer.valueOf(parseValueLength));
                        }
                        if (parseValueLength >= 0) {
                            return parseWapString;
                        }
                        Log.e(LOG_TAG, "Corrupt MMS message");
                        return PduContentTypes.contentTypes[0].getBytes();
                    }
                    throw new AssertionError();
                }
            }
            throw new AssertionError();
        }
        throw new AssertionError();
    }

    protected static void parseContentTypeParams(ByteArrayInputStream byteArrayInputStream, HashMap<Integer, Object> hashMap, Integer num) {
        if (!$assertionsDisabled && byteArrayInputStream == null) {
            throw new AssertionError();
        } else if ($assertionsDisabled || num.intValue() > 0) {
            int available = byteArrayInputStream.available();
            int intValue = num.intValue();
            while (intValue > 0) {
                int read = byteArrayInputStream.read();
                if ($assertionsDisabled || -1 != read) {
                    intValue--;
                    byte[] parseWapString;
                    switch (read) {
                        case 129:
                            byteArrayInputStream.mark(1);
                            intValue = extractByteValue(byteArrayInputStream);
                            byteArrayInputStream.reset();
                            if ((intValue <= 32 || intValue >= 127) && intValue != 0) {
                                intValue = (int) parseIntegerValue(byteArrayInputStream);
                                if (hashMap != null) {
                                    hashMap.put(Integer.valueOf(129), Integer.valueOf(intValue));
                                }
                            } else {
                                byte[] parseWapString2 = parseWapString(byteArrayInputStream, 0);
                                try {
                                    hashMap.put(Integer.valueOf(129), Integer.valueOf(CharacterSets.getMibEnumValue(new String(parseWapString2))));
                                } catch (UnsupportedEncodingException e) {
                                    Log.e(LOG_TAG, Arrays.toString(parseWapString2), e);
                                    hashMap.put(Integer.valueOf(129), Integer.valueOf(0));
                                }
                            }
                            intValue = num.intValue() - (available - byteArrayInputStream.available());
                            break;
                        case 131:
                        case 137:
                            byteArrayInputStream.mark(1);
                            intValue = extractByteValue(byteArrayInputStream);
                            byteArrayInputStream.reset();
                            if (intValue > 127) {
                                intValue = parseShortInteger(byteArrayInputStream);
                                if (intValue < PduContentTypes.contentTypes.length) {
                                    hashMap.put(Integer.valueOf(131), PduContentTypes.contentTypes[intValue].getBytes());
                                }
                            } else {
                                parseWapString = parseWapString(byteArrayInputStream, 0);
                                if (!(parseWapString == null || hashMap == null)) {
                                    hashMap.put(Integer.valueOf(131), parseWapString);
                                }
                            }
                            intValue = num.intValue() - (available - byteArrayInputStream.available());
                            break;
                        case 133:
                        case 151:
                            parseWapString = parseWapString(byteArrayInputStream, 0);
                            if (!(parseWapString == null || hashMap == null)) {
                                hashMap.put(Integer.valueOf(151), parseWapString);
                            }
                            intValue = num.intValue() - (available - byteArrayInputStream.available());
                            break;
                        case 138:
                        case 153:
                            parseWapString = parseWapString(byteArrayInputStream, 0);
                            if (!(parseWapString == null || hashMap == null)) {
                                hashMap.put(Integer.valueOf(153), parseWapString);
                            }
                            intValue = num.intValue() - (available - byteArrayInputStream.available());
                            break;
                        default:
                            if (-1 != skipWapValue(byteArrayInputStream, intValue)) {
                                intValue = 0;
                                break;
                            } else {
                                Log.e(LOG_TAG, "Corrupt Content-Type");
                                break;
                            }
                    }
                }
                throw new AssertionError();
            }
            if (intValue != 0) {
                Log.e(LOG_TAG, "Corrupt Content-Type");
            }
        } else {
            throw new AssertionError();
        }
    }

    protected static EncodedStringValue parseEncodedStringValue(ByteArrayInputStream byteArrayInputStream) {
        if ($assertionsDisabled || byteArrayInputStream != null) {
            byteArrayInputStream.mark(1);
            int read = byteArrayInputStream.read();
            if ($assertionsDisabled || -1 != read) {
                read &= 255;
                if (read == 0) {
                    return new EncodedStringValue("");
                }
                byteArrayInputStream.reset();
                if (read < 32) {
                    parseValueLength(byteArrayInputStream);
                    read = parseShortInteger(byteArrayInputStream);
                } else {
                    read = 0;
                }
                byte[] parseWapString = parseWapString(byteArrayInputStream, 0);
                if (read == 0) {
                    return new EncodedStringValue(parseWapString);
                }
                try {
                    return new EncodedStringValue(read, parseWapString);
                } catch (Exception e) {
                    return null;
                }
            }
            throw new AssertionError();
        }
        throw new AssertionError();
    }

    protected static long parseIntegerValue(ByteArrayInputStream byteArrayInputStream) {
        if ($assertionsDisabled || byteArrayInputStream != null) {
            byteArrayInputStream.mark(1);
            int read = byteArrayInputStream.read();
            if ($assertionsDisabled || -1 != read) {
                byteArrayInputStream.reset();
                return read > 127 ? (long) parseShortInteger(byteArrayInputStream) : parseLongInteger(byteArrayInputStream);
            } else {
                throw new AssertionError();
            }
        }
        throw new AssertionError();
    }

    protected static long parseLongInteger(ByteArrayInputStream byteArrayInputStream) {
        if ($assertionsDisabled || byteArrayInputStream != null) {
            int read = byteArrayInputStream.read();
            if ($assertionsDisabled || -1 != read) {
                int i = read & 255;
                if (i > 8) {
                    throw new RuntimeException("Octet count greater than 8 and I can't represent that!");
                }
                long j = 0;
                int i2 = 0;
                while (i2 < i) {
                    int read2 = byteArrayInputStream.read();
                    if ($assertionsDisabled || -1 != read2) {
                        j = (j << 8) + ((long) (read2 & 255));
                        i2++;
                    } else {
                        throw new AssertionError();
                    }
                }
                return j;
            }
            throw new AssertionError();
        }
        throw new AssertionError();
    }

    protected static int parseShortInteger(ByteArrayInputStream byteArrayInputStream) {
        if ($assertionsDisabled || byteArrayInputStream != null) {
            int read = byteArrayInputStream.read();
            if ($assertionsDisabled || -1 != read) {
                return read & 127;
            }
            throw new AssertionError();
        }
        throw new AssertionError();
    }

    protected static int parseUnsignedInt(ByteArrayInputStream byteArrayInputStream) {
        if ($assertionsDisabled || byteArrayInputStream != null) {
            int i = 0;
            int read = byteArrayInputStream.read();
            if (read == -1) {
                return read;
            }
            while ((read & 128) != 0) {
                i = (i << 7) | (read & 127);
                read = byteArrayInputStream.read();
                if (read == -1) {
                    return read;
                }
            }
            return (read & 127) | (i << 7);
        }
        throw new AssertionError();
    }

    protected static int parseValueLength(ByteArrayInputStream byteArrayInputStream) {
        if ($assertionsDisabled || byteArrayInputStream != null) {
            int read = byteArrayInputStream.read();
            if ($assertionsDisabled || -1 != read) {
                read &= 255;
                if (read <= 30) {
                    return read;
                }
                if (read == 31) {
                    return parseUnsignedInt(byteArrayInputStream);
                }
                throw new RuntimeException("Value length > LENGTH_QUOTE!");
            }
            throw new AssertionError();
        }
        throw new AssertionError();
    }

    protected static byte[] parseWapString(ByteArrayInputStream byteArrayInputStream, int i) {
        if ($assertionsDisabled || byteArrayInputStream != null) {
            byteArrayInputStream.mark(1);
            int read = byteArrayInputStream.read();
            if ($assertionsDisabled || -1 != read) {
                if (1 == i && 34 == read) {
                    byteArrayInputStream.mark(1);
                } else if (i == 0 && 127 == read) {
                    byteArrayInputStream.mark(1);
                } else {
                    byteArrayInputStream.reset();
                }
                return getWapString(byteArrayInputStream, i);
            }
            throw new AssertionError();
        }
        throw new AssertionError();
    }

    protected static int skipWapValue(ByteArrayInputStream byteArrayInputStream, int i) {
        if ($assertionsDisabled || byteArrayInputStream != null) {
            int read = byteArrayInputStream.read(new byte[i], 0, i);
            return read < i ? -1 : read;
        } else {
            throw new AssertionError();
        }
    }

    public GenericPdu parse() {
        if (this.mPduDataStream == null) {
            return null;
        }
        this.mHeaders = parseHeaders(this.mPduDataStream);
        if (this.mHeaders == null) {
            return null;
        }
        int octet = this.mHeaders.getOctet(140);
        if (checkMandatoryHeader(this.mHeaders)) {
            if (128 == octet || 132 == octet) {
                this.mBody = parseParts(this.mPduDataStream);
                if (this.mBody == null) {
                    return null;
                }
            }
            switch (octet) {
                case 128:
                    return new SendReq(this.mHeaders, this.mBody);
                case 129:
                    return new SendConf(this.mHeaders);
                case 130:
                    return new NotificationInd(this.mHeaders);
                case 131:
                    return new NotifyRespInd(this.mHeaders);
                case 132:
                    GenericPdu retrieveConf = new RetrieveConf(this.mHeaders, this.mBody);
                    byte[] contentType = retrieveConf.getContentType();
                    if (contentType == null) {
                        return null;
                    }
                    String str = new String(contentType);
                    if (str.equals(ContentType.MULTIPART_MIXED) || str.equals(ContentType.MULTIPART_RELATED) || str.equals(ContentType.MULTIPART_ALTERNATIVE)) {
                        return retrieveConf;
                    }
                    if (!str.equals(ContentType.MULTIPART_ALTERNATIVE)) {
                        return null;
                    }
                    PduPart part = this.mBody.getPart(0);
                    this.mBody.removeAll();
                    this.mBody.addPart(0, part);
                    return retrieveConf;
                case 133:
                    return new AcknowledgeInd(this.mHeaders);
                case 134:
                    return new DeliveryInd(this.mHeaders);
                case 135:
                    return new ReadRecInd(this.mHeaders);
                case 136:
                    return new ReadOrigInd(this.mHeaders);
                default:
                    log("Parser doesn't support this message type in this version!");
                    return null;
            }
        }
        log("check mandatory headers failed!");
        return null;
    }

    /* Access modifiers changed, original: protected */
    public PduHeaders parseHeaders(ByteArrayInputStream byteArrayInputStream) {
        if (byteArrayInputStream == null) {
            return null;
        }
        PduHeaders pduHeaders = new PduHeaders();
        Object obj = 1;
        while (obj != null && byteArrayInputStream.available() > 0) {
            byteArrayInputStream.mark(1);
            int extractByteValue = extractByteValue(byteArrayInputStream);
            if (extractByteValue < 32 || extractByteValue > 127) {
                byte[] textString;
                int indexOf;
                byte[] parseWapString;
                int extractByteValue2;
                EncodedStringValue parseEncodedStringValue;
                switch (extractByteValue) {
                    case 129:
                    case 130:
                    case 151:
                        EncodedStringValue parseEncodedStringValue2 = parseEncodedStringValue(byteArrayInputStream);
                        if (parseEncodedStringValue2 == null) {
                            break;
                        }
                        textString = parseEncodedStringValue2.getTextString();
                        if (textString != null) {
                            String str = new String(textString);
                            indexOf = str.indexOf("/");
                            if (indexOf > 0) {
                                str = str.substring(0, indexOf);
                            }
                            try {
                                parseEncodedStringValue2.setTextString(str.getBytes());
                            } catch (NullPointerException e) {
                                log("null pointer error!");
                                return null;
                            }
                        }
                        try {
                            pduHeaders.appendEncodedStringValue(parseEncodedStringValue2, extractByteValue);
                            break;
                        } catch (NullPointerException e2) {
                            log("null pointer error!");
                            break;
                        } catch (RuntimeException e3) {
                            log(extractByteValue + "is not Encoded-String-Value header field!");
                            return null;
                        }
                    case 131:
                    case 139:
                    case 152:
                    case PduHeaders.REPLY_CHARGING_ID /*158*/:
                    case PduHeaders.APPLIC_ID /*183*/:
                    case PduHeaders.REPLY_APPLIC_ID /*184*/:
                    case PduHeaders.AUX_APPLIC_ID /*185*/:
                    case PduHeaders.REPLACE_ID /*189*/:
                    case PduHeaders.CANCEL_ID /*190*/:
                        parseWapString = parseWapString(byteArrayInputStream, 0);
                        if (parseWapString == null) {
                            break;
                        }
                        try {
                            pduHeaders.setTextString(parseWapString, extractByteValue);
                            break;
                        } catch (NullPointerException e4) {
                            log("null pointer error!");
                            break;
                        } catch (RuntimeException e5) {
                            log(extractByteValue + "is not Text-String header field!");
                            return null;
                        }
                    case 132:
                        HashMap hashMap = new HashMap();
                        parseWapString = parseContentType(byteArrayInputStream, hashMap);
                        if (parseWapString != null) {
                            try {
                                pduHeaders.setTextString(parseWapString, 132);
                            } catch (NullPointerException e6) {
                                log("null pointer error!");
                            } catch (RuntimeException e7) {
                                log(extractByteValue + "is not Text-String header field!");
                                return null;
                            }
                        }
                        mStartParam = (byte[]) hashMap.get(Integer.valueOf(153));
                        mTypeParam = (byte[]) hashMap.get(Integer.valueOf(131));
                        obj = null;
                        break;
                    case 133:
                    case 142:
                    case PduHeaders.REPLY_CHARGING_SIZE /*159*/:
                        try {
                            pduHeaders.setLongInteger(parseLongInteger(byteArrayInputStream), extractByteValue);
                            break;
                        } catch (RuntimeException e8) {
                            log(extractByteValue + "is not Long-Integer header field!");
                            return null;
                        }
                    case 134:
                    case 143:
                    case 144:
                    case 145:
                    case 146:
                    case 148:
                    case 149:
                    case 153:
                    case 155:
                    case 156:
                    case PduHeaders.STORE /*162*/:
                    case PduHeaders.MM_STATE /*163*/:
                    case PduHeaders.STORE_STATUS /*165*/:
                    case 167:
                    case PduHeaders.TOTALS /*169*/:
                    case PduHeaders.QUOTAS /*171*/:
                    case PduHeaders.DISTRIBUTION_INDICATOR /*177*/:
                    case PduHeaders.RECOMMENDED_RETRIEVAL_MODE /*180*/:
                    case PduHeaders.CONTENT_CLASS /*186*/:
                    case PduHeaders.DRM_CONTENT /*187*/:
                    case PduHeaders.ADAPTATION_ALLOWED /*188*/:
                    case PduHeaders.CANCEL_STATUS /*191*/:
                        extractByteValue2 = extractByteValue(byteArrayInputStream);
                        try {
                            pduHeaders.setOctet(extractByteValue2, extractByteValue);
                            break;
                        } catch (InvalidHeaderValueException e9) {
                            log("Set invalid Octet value: " + extractByteValue2 + " into the header filed: " + extractByteValue);
                            return null;
                        } catch (RuntimeException e10) {
                            log(extractByteValue + "is not Octet header field!");
                            return null;
                        }
                    case 135:
                    case 136:
                    case 157:
                        parseValueLength(byteArrayInputStream);
                        extractByteValue2 = extractByteValue(byteArrayInputStream);
                        try {
                            long parseLongInteger = parseLongInteger(byteArrayInputStream);
                            if (129 == extractByteValue2) {
                                parseLongInteger += System.currentTimeMillis() / 1000;
                            }
                            try {
                                pduHeaders.setLongInteger(parseLongInteger, extractByteValue);
                                break;
                            } catch (RuntimeException e11) {
                                log(extractByteValue + "is not Long-Integer header field!");
                                return null;
                            }
                        } catch (RuntimeException e12) {
                            log(extractByteValue + "is not Long-Integer header field!");
                            return null;
                        }
                    case 137:
                        parseValueLength(byteArrayInputStream);
                        if (128 == extractByteValue(byteArrayInputStream)) {
                            parseEncodedStringValue = parseEncodedStringValue(byteArrayInputStream);
                            if (parseEncodedStringValue != null) {
                                textString = parseEncodedStringValue.getTextString();
                                if (textString != null) {
                                    String str2 = new String(textString);
                                    indexOf = str2.indexOf("/");
                                    if (indexOf > 0) {
                                        str2 = str2.substring(0, indexOf);
                                    }
                                    try {
                                        parseEncodedStringValue.setTextString(str2.getBytes());
                                    } catch (NullPointerException e13) {
                                        log("null pointer error!");
                                        return null;
                                    }
                                }
                            }
                        }
                        try {
                            parseEncodedStringValue = new EncodedStringValue(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR.getBytes());
                        } catch (NullPointerException e14) {
                            log(extractByteValue + "is not Encoded-String-Value header field!");
                            return null;
                        }
                        try {
                            pduHeaders.setEncodedStringValue(parseEncodedStringValue, 137);
                            break;
                        } catch (NullPointerException e15) {
                            log("null pointer error!");
                            break;
                        } catch (RuntimeException e16) {
                            log(extractByteValue + "is not Encoded-String-Value header field!");
                            return null;
                        }
                    case 138:
                        byteArrayInputStream.mark(1);
                        extractByteValue2 = extractByteValue(byteArrayInputStream);
                        if (extractByteValue2 >= 128) {
                            if (128 != extractByteValue2) {
                                if (129 != extractByteValue2) {
                                    if (130 != extractByteValue2) {
                                        if (131 != extractByteValue2) {
                                            break;
                                        }
                                        pduHeaders.setTextString(PduHeaders.MESSAGE_CLASS_AUTO_STR.getBytes(), 138);
                                        break;
                                    }
                                    pduHeaders.setTextString(PduHeaders.MESSAGE_CLASS_INFORMATIONAL_STR.getBytes(), 138);
                                    break;
                                }
                                pduHeaders.setTextString(PduHeaders.MESSAGE_CLASS_ADVERTISEMENT_STR.getBytes(), 138);
                                break;
                            }
                            try {
                                pduHeaders.setTextString(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes(), 138);
                                break;
                            } catch (NullPointerException e17) {
                                log("null pointer error!");
                                break;
                            } catch (RuntimeException e18) {
                                log(extractByteValue + "is not Text-String header field!");
                                return null;
                            }
                        }
                        byteArrayInputStream.reset();
                        parseWapString = parseWapString(byteArrayInputStream, 0);
                        if (parseWapString == null) {
                            break;
                        }
                        try {
                            pduHeaders.setTextString(parseWapString, 138);
                            break;
                        } catch (NullPointerException e19) {
                            log("null pointer error!");
                            break;
                        } catch (RuntimeException e20) {
                            log(extractByteValue + "is not Text-String header field!");
                            return null;
                        }
                    case 140:
                        extractByteValue2 = extractByteValue(byteArrayInputStream);
                        switch (extractByteValue2) {
                            case 137:
                            case 138:
                            case 139:
                            case 140:
                            case 141:
                            case 142:
                            case 143:
                            case 144:
                            case 145:
                            case 146:
                            case 147:
                            case 148:
                            case 149:
                            case 150:
                            case 151:
                                return null;
                            default:
                                try {
                                    pduHeaders.setOctet(extractByteValue2, extractByteValue);
                                    break;
                                } catch (InvalidHeaderValueException e21) {
                                    log("Set invalid Octet value: " + extractByteValue2 + " into the header filed: " + extractByteValue);
                                    return null;
                                } catch (RuntimeException e22) {
                                    log(extractByteValue + "is not Octet header field!");
                                    return null;
                                }
                        }
                    case 141:
                        extractByteValue2 = parseShortInteger(byteArrayInputStream);
                        try {
                            pduHeaders.setOctet(extractByteValue2, 141);
                            break;
                        } catch (InvalidHeaderValueException e23) {
                            log("Set invalid Octet value: " + extractByteValue2 + " into the header filed: " + extractByteValue);
                            return null;
                        } catch (RuntimeException e24) {
                            log(extractByteValue + "is not Octet header field!");
                            return null;
                        }
                    case 147:
                    case 150:
                    case 154:
                    case PduHeaders.STORE_STATUS_TEXT /*166*/:
                    case PduHeaders.RECOMMENDED_RETRIEVAL_MODE_TEXT /*181*/:
                    case PduHeaders.STATUS_TEXT /*182*/:
                        parseEncodedStringValue = parseEncodedStringValue(byteArrayInputStream);
                        if (parseEncodedStringValue == null) {
                            break;
                        }
                        try {
                            pduHeaders.setEncodedStringValue(parseEncodedStringValue, extractByteValue);
                            break;
                        } catch (NullPointerException e25) {
                            log("null pointer error!");
                            break;
                        } catch (RuntimeException e26) {
                            log(extractByteValue + "is not Encoded-String-Value header field!");
                            return null;
                        }
                    case 160:
                        parseValueLength(byteArrayInputStream);
                        try {
                            parseIntegerValue(byteArrayInputStream);
                            parseEncodedStringValue = parseEncodedStringValue(byteArrayInputStream);
                            if (parseEncodedStringValue == null) {
                                break;
                            }
                            try {
                                pduHeaders.setEncodedStringValue(parseEncodedStringValue, 160);
                                break;
                            } catch (NullPointerException e27) {
                                log("null pointer error!");
                                break;
                            } catch (RuntimeException e28) {
                                log(extractByteValue + "is not Encoded-String-Value header field!");
                                return null;
                            }
                        } catch (RuntimeException e29) {
                            log(extractByteValue + " is not Integer-Value");
                            return null;
                        }
                    case PduHeaders.PREVIOUSLY_SENT_DATE /*161*/:
                        parseValueLength(byteArrayInputStream);
                        try {
                            parseIntegerValue(byteArrayInputStream);
                            try {
                                pduHeaders.setLongInteger(parseLongInteger(byteArrayInputStream), PduHeaders.PREVIOUSLY_SENT_DATE);
                                break;
                            } catch (RuntimeException e30) {
                                log(extractByteValue + "is not Long-Integer header field!");
                                return null;
                            }
                        } catch (RuntimeException e31) {
                            log(extractByteValue + " is not Integer-Value");
                            return null;
                        }
                    case PduHeaders.MM_FLAGS /*164*/:
                        parseValueLength(byteArrayInputStream);
                        extractByteValue(byteArrayInputStream);
                        parseEncodedStringValue(byteArrayInputStream);
                        break;
                    case PduHeaders.MBOX_TOTALS /*170*/:
                    case PduHeaders.MBOX_QUOTAS /*172*/:
                        parseValueLength(byteArrayInputStream);
                        extractByteValue(byteArrayInputStream);
                        try {
                            parseIntegerValue(byteArrayInputStream);
                            break;
                        } catch (RuntimeException e32) {
                            log(extractByteValue + " is not Integer-Value");
                            return null;
                        }
                    case PduHeaders.MESSAGE_COUNT /*173*/:
                    case PduHeaders.START /*175*/:
                    case PduHeaders.LIMIT /*179*/:
                        try {
                            pduHeaders.setLongInteger(parseIntegerValue(byteArrayInputStream), extractByteValue);
                            break;
                        } catch (RuntimeException e33) {
                            log(extractByteValue + "is not Long-Integer header field!");
                            return null;
                        }
                    case PduHeaders.ELEMENT_DESCRIPTOR /*178*/:
                        parseContentType(byteArrayInputStream, null);
                        break;
                    default:
                        log("Unknown header");
                        break;
                }
            }
            byteArrayInputStream.reset();
            parseWapString(byteArrayInputStream, 0);
        }
        return pduHeaders;
    }

    /* Access modifiers changed, original: protected */
    public boolean parsePartHeaders(ByteArrayInputStream byteArrayInputStream, PduPart pduPart, int i) {
        if (!$assertionsDisabled && byteArrayInputStream == null) {
            throw new AssertionError();
        } else if (!$assertionsDisabled && pduPart == null) {
            throw new AssertionError();
        } else if ($assertionsDisabled || i > 0) {
            int available = byteArrayInputStream.available();
            int i2 = i;
            while (i2 > 0) {
                int read = byteArrayInputStream.read();
                if ($assertionsDisabled || -1 != read) {
                    i2--;
                    byte[] parseWapString;
                    if (read > 127) {
                        switch (read) {
                            case 142:
                                parseWapString = parseWapString(byteArrayInputStream, 0);
                                if (parseWapString != null) {
                                    pduPart.setContentLocation(parseWapString);
                                }
                                i2 = i - (available - byteArrayInputStream.available());
                                break;
                            case 174:
                            case PduPart.P_CONTENT_DISPOSITION /*197*/:
                                if (!this.mParseContentDisposition) {
                                    break;
                                }
                                i2 = parseValueLength(byteArrayInputStream);
                                byteArrayInputStream.mark(1);
                                read = byteArrayInputStream.available();
                                int read2 = byteArrayInputStream.read();
                                if (read2 == 128) {
                                    pduPart.setContentDisposition(PduPart.DISPOSITION_FROM_DATA);
                                } else if (read2 == 129) {
                                    pduPart.setContentDisposition(PduPart.DISPOSITION_ATTACHMENT);
                                } else if (read2 == 130) {
                                    pduPart.setContentDisposition(PduPart.DISPOSITION_INLINE);
                                } else {
                                    byteArrayInputStream.reset();
                                    pduPart.setContentDisposition(parseWapString(byteArrayInputStream, 0));
                                }
                                if (read - byteArrayInputStream.available() < i2) {
                                    if (byteArrayInputStream.read() == 152) {
                                        pduPart.setFilename(parseWapString(byteArrayInputStream, 0));
                                    }
                                    read2 = byteArrayInputStream.available();
                                    if (read - read2 < i2) {
                                        i2 -= read - read2;
                                        byteArrayInputStream.read(new byte[i2], 0, i2);
                                    }
                                }
                                i2 = i - (available - byteArrayInputStream.available());
                                break;
                            case 192:
                                parseWapString = parseWapString(byteArrayInputStream, 1);
                                if (parseWapString != null) {
                                    pduPart.setContentId(parseWapString);
                                }
                                i2 = i - (available - byteArrayInputStream.available());
                                break;
                            default:
                                if (-1 != skipWapValue(byteArrayInputStream, i2)) {
                                    i2 = 0;
                                    break;
                                }
                                Log.e(LOG_TAG, "Corrupt Part headers");
                                return false;
                        }
                    } else if (read >= 32 && read <= 127) {
                        parseWapString = parseWapString(byteArrayInputStream, 0);
                        byte[] parseWapString2 = parseWapString(byteArrayInputStream, 0);
                        if (true == PduPart.CONTENT_TRANSFER_ENCODING.equalsIgnoreCase(new String(parseWapString))) {
                            pduPart.setContentTransferEncoding(parseWapString2);
                        }
                        i2 = i - (available - byteArrayInputStream.available());
                    } else if (-1 == skipWapValue(byteArrayInputStream, i2)) {
                        Log.e(LOG_TAG, "Corrupt Part headers");
                        return false;
                    } else {
                        i2 = 0;
                    }
                } else {
                    throw new AssertionError();
                }
            }
            if (i2 == 0) {
                return true;
            }
            Log.e(LOG_TAG, "Corrupt Part headers");
            return false;
        } else {
            throw new AssertionError();
        }
    }

    /* Access modifiers changed, original: protected */
    public PduBody parseParts(ByteArrayInputStream byteArrayInputStream) {
        PduBody pduBody;
        if (byteArrayInputStream == null) {
            pduBody = null;
        } else {
            int parseUnsignedInt = parseUnsignedInt(byteArrayInputStream);
            PduBody pduBody2 = new PduBody();
            for (int i = 0; i < parseUnsignedInt; i++) {
                int parseUnsignedInt2 = parseUnsignedInt(byteArrayInputStream);
                int parseUnsignedInt3 = parseUnsignedInt(byteArrayInputStream);
                PduPart pduPart = new PduPart();
                int available = byteArrayInputStream.available();
                if (available <= 0) {
                    return null;
                }
                PduPart part;
                HashMap hashMap = new HashMap();
                byte[] parseContentType = parseContentType(byteArrayInputStream, hashMap);
                if (parseContentType != null) {
                    pduPart.setContentType(parseContentType);
                } else {
                    pduPart.setContentType(PduContentTypes.contentTypes[0].getBytes());
                }
                parseContentType = (byte[]) hashMap.get(Integer.valueOf(151));
                if (parseContentType != null) {
                    pduPart.setName(parseContentType);
                }
                Integer num = (Integer) hashMap.get(Integer.valueOf(129));
                if (num != null) {
                    pduPart.setCharset(num.intValue());
                }
                int available2 = parseUnsignedInt2 - (available - byteArrayInputStream.available());
                if (available2 > 0) {
                    if (!parsePartHeaders(byteArrayInputStream, pduPart, available2)) {
                        return null;
                    }
                } else if (available2 < 0) {
                    return null;
                }
                if (pduPart.getContentLocation() == null && pduPart.getName() == null && pduPart.getFilename() == null && pduPart.getContentId() == null) {
                    pduPart.setContentLocation(Long.toOctalString(System.currentTimeMillis()).getBytes());
                }
                if (parseUnsignedInt3 > 0) {
                    parseContentType = new byte[parseUnsignedInt3];
                    String str = new String(pduPart.getContentType());
                    byteArrayInputStream.read(parseContentType, 0, parseUnsignedInt3);
                    if (str.equalsIgnoreCase(ContentType.MULTIPART_ALTERNATIVE)) {
                        part = parseParts(new ByteArrayInputStream(parseContentType)).getPart(0);
                    } else {
                        byte[] contentTransferEncoding = pduPart.getContentTransferEncoding();
                        if (contentTransferEncoding != null) {
                            String str2 = new String(contentTransferEncoding);
                            if (str2.equalsIgnoreCase(PduPart.P_BASE64)) {
                                parseContentType = Base64.decodeBase64(parseContentType);
                            } else if (str2.equalsIgnoreCase(PduPart.P_QUOTED_PRINTABLE)) {
                                parseContentType = QuotedPrintable.decodeQuotedPrintable(parseContentType);
                            }
                        }
                        if (parseContentType == null) {
                            log("Decode part data error!");
                            return null;
                        }
                        pduPart.setData(parseContentType);
                        part = pduPart;
                    }
                } else {
                    part = pduPart;
                }
                if (checkPartPosition(part) == 0) {
                    pduBody2.addPart(0, part);
                } else {
                    pduBody2.addPart(part);
                }
            }
            pduBody = pduBody2;
        }
        return pduBody;
    }
}
