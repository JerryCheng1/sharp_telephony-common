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

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class PduParser {
    static final /* synthetic */ boolean $assertionsDisabled;
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
    private static byte[] mStartParam;
    private static byte[] mTypeParam;
    private final boolean mParseContentDisposition;
    private ByteArrayInputStream mPduDataStream;
    private PduHeaders mHeaders = null;
    private PduBody mBody = null;

    static {
        $assertionsDisabled = !PduParser.class.desiredAssertionStatus();
        mTypeParam = null;
        mStartParam = null;
    }

    public PduParser(byte[] pduDataStream, boolean parseContentDisposition) {
        this.mPduDataStream = null;
        this.mPduDataStream = new ByteArrayInputStream(pduDataStream);
        this.mParseContentDisposition = parseContentDisposition;
    }

    public GenericPdu parse() {
        if (this.mPduDataStream == null) {
            return null;
        }
        this.mHeaders = parseHeaders(this.mPduDataStream);
        if (this.mHeaders == null) {
            return null;
        }
        int messageType = this.mHeaders.getOctet(140);
        if (!checkMandatoryHeader(this.mHeaders)) {
            log("check mandatory headers failed!");
            return null;
        }
        if (128 == messageType || 132 == messageType) {
            this.mBody = parseParts(this.mPduDataStream);
            if (this.mBody == null) {
                return null;
            }
        }
        switch (messageType) {
            case 128:
                return new SendReq(this.mHeaders, this.mBody);
            case 129:
                return new SendConf(this.mHeaders);
            case 130:
                return new NotificationInd(this.mHeaders);
            case 131:
                return new NotifyRespInd(this.mHeaders);
            case 132:
                RetrieveConf retrieveConf = new RetrieveConf(this.mHeaders, this.mBody);
                byte[] contentType = retrieveConf.getContentType();
                if (contentType == null) {
                    return null;
                }
                String ctTypeStr = new String(contentType);
                if (ctTypeStr.equals(ContentType.MULTIPART_MIXED) || ctTypeStr.equals(ContentType.MULTIPART_RELATED) || ctTypeStr.equals(ContentType.MULTIPART_ALTERNATIVE)) {
                    return retrieveConf;
                }
                if (!ctTypeStr.equals(ContentType.MULTIPART_ALTERNATIVE)) {
                    return null;
                }
                PduPart firstPart = this.mBody.getPart(0);
                this.mBody.removeAll();
                this.mBody.addPart(0, firstPart);
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

    protected PduHeaders parseHeaders(ByteArrayInputStream pduDataStream) {
        EncodedStringValue from;
        byte[] address;
        if (pduDataStream == null) {
            return null;
        }
        boolean keepParsing = true;
        PduHeaders headers = new PduHeaders();
        while (keepParsing && pduDataStream.available() > 0) {
            pduDataStream.mark(1);
            int headerField = extractByteValue(pduDataStream);
            if (headerField < 32 || headerField > 127) {
                switch (headerField) {
                    case 129:
                    case 130:
                    case 151:
                        EncodedStringValue value = parseEncodedStringValue(pduDataStream);
                        if (value != null) {
                            byte[] address2 = value.getTextString();
                            if (address2 != null) {
                                String str = new String(address2);
                                int endIndex = str.indexOf("/");
                                if (endIndex > 0) {
                                    str = str.substring(0, endIndex);
                                }
                                try {
                                    value.setTextString(str.getBytes());
                                } catch (NullPointerException e) {
                                    log("null pointer error!");
                                    return null;
                                }
                            }
                            try {
                                headers.appendEncodedStringValue(value, headerField);
                                break;
                            } catch (NullPointerException e2) {
                                log("null pointer error!");
                                break;
                            } catch (RuntimeException e3) {
                                log(headerField + "is not Encoded-String-Value header field!");
                                return null;
                            }
                        } else {
                            continue;
                        }
                    case 131:
                    case 139:
                    case 152:
                    case PduHeaders.REPLY_CHARGING_ID /* 158 */:
                    case PduHeaders.APPLIC_ID /* 183 */:
                    case PduHeaders.REPLY_APPLIC_ID /* 184 */:
                    case PduHeaders.AUX_APPLIC_ID /* 185 */:
                    case PduHeaders.REPLACE_ID /* 189 */:
                    case PduHeaders.CANCEL_ID /* 190 */:
                        byte[] value2 = parseWapString(pduDataStream, 0);
                        if (value2 != null) {
                            try {
                                headers.setTextString(value2, headerField);
                                break;
                            } catch (NullPointerException e4) {
                                log("null pointer error!");
                                break;
                            } catch (RuntimeException e5) {
                                log(headerField + "is not Text-String header field!");
                                return null;
                            }
                        } else {
                            continue;
                        }
                    case 132:
                        HashMap<Integer, Object> map = new HashMap<>();
                        byte[] contentType = parseContentType(pduDataStream, map);
                        if (contentType != null) {
                            try {
                                headers.setTextString(contentType, 132);
                            } catch (NullPointerException e6) {
                                log("null pointer error!");
                            } catch (RuntimeException e7) {
                                log(headerField + "is not Text-String header field!");
                                return null;
                            }
                        }
                        mStartParam = (byte[]) map.get(153);
                        mTypeParam = (byte[]) map.get(131);
                        keepParsing = false;
                        continue;
                    case 133:
                    case 142:
                    case PduHeaders.REPLY_CHARGING_SIZE /* 159 */:
                        try {
                            headers.setLongInteger(parseLongInteger(pduDataStream), headerField);
                            continue;
                        } catch (RuntimeException e8) {
                            log(headerField + "is not Long-Integer header field!");
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
                    case PduHeaders.STORE /* 162 */:
                    case PduHeaders.MM_STATE /* 163 */:
                    case PduHeaders.STORE_STATUS /* 165 */:
                    case 167:
                    case PduHeaders.TOTALS /* 169 */:
                    case PduHeaders.QUOTAS /* 171 */:
                    case PduHeaders.DISTRIBUTION_INDICATOR /* 177 */:
                    case PduHeaders.RECOMMENDED_RETRIEVAL_MODE /* 180 */:
                    case PduHeaders.CONTENT_CLASS /* 186 */:
                    case PduHeaders.DRM_CONTENT /* 187 */:
                    case PduHeaders.ADAPTATION_ALLOWED /* 188 */:
                    case PduHeaders.CANCEL_STATUS /* 191 */:
                        int value3 = extractByteValue(pduDataStream);
                        try {
                            headers.setOctet(value3, headerField);
                            continue;
                        } catch (InvalidHeaderValueException e9) {
                            log("Set invalid Octet value: " + value3 + " into the header filed: " + headerField);
                            return null;
                        } catch (RuntimeException e10) {
                            log(headerField + "is not Octet header field!");
                            return null;
                        }
                    case 135:
                    case 136:
                    case 157:
                        parseValueLength(pduDataStream);
                        int token = extractByteValue(pduDataStream);
                        try {
                            long timeValue = parseLongInteger(pduDataStream);
                            if (129 == token) {
                                timeValue += System.currentTimeMillis() / 1000;
                            }
                            try {
                                headers.setLongInteger(timeValue, headerField);
                                continue;
                            } catch (RuntimeException e11) {
                                log(headerField + "is not Long-Integer header field!");
                                return null;
                            }
                        } catch (RuntimeException e12) {
                            log(headerField + "is not Long-Integer header field!");
                            return null;
                        }
                    case 137:
                        parseValueLength(pduDataStream);
                        if (128 == extractByteValue(pduDataStream)) {
                            from = parseEncodedStringValue(pduDataStream);
                            if (!(from == null || (address = from.getTextString()) == null)) {
                                String str2 = new String(address);
                                int endIndex2 = str2.indexOf("/");
                                if (endIndex2 > 0) {
                                    str2 = str2.substring(0, endIndex2);
                                }
                                try {
                                    from.setTextString(str2.getBytes());
                                } catch (NullPointerException e13) {
                                    log("null pointer error!");
                                    return null;
                                }
                            }
                        } else {
                            try {
                                from = new EncodedStringValue(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR.getBytes());
                            } catch (NullPointerException e14) {
                                log(headerField + "is not Encoded-String-Value header field!");
                                return null;
                            }
                        }
                        try {
                            headers.setEncodedStringValue(from, 137);
                            continue;
                        } catch (NullPointerException e15) {
                            log("null pointer error!");
                            break;
                        } catch (RuntimeException e16) {
                            log(headerField + "is not Encoded-String-Value header field!");
                            return null;
                        }
                    case 138:
                        pduDataStream.mark(1);
                        int messageClass = extractByteValue(pduDataStream);
                        if (messageClass >= 128) {
                            if (128 != messageClass) {
                                if (129 != messageClass) {
                                    if (130 != messageClass) {
                                        if (131 != messageClass) {
                                            break;
                                        } else {
                                            headers.setTextString(PduHeaders.MESSAGE_CLASS_AUTO_STR.getBytes(), 138);
                                            break;
                                        }
                                    } else {
                                        headers.setTextString(PduHeaders.MESSAGE_CLASS_INFORMATIONAL_STR.getBytes(), 138);
                                        break;
                                    }
                                } else {
                                    headers.setTextString(PduHeaders.MESSAGE_CLASS_ADVERTISEMENT_STR.getBytes(), 138);
                                    break;
                                }
                            } else {
                                try {
                                    headers.setTextString(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes(), 138);
                                    continue;
                                } catch (NullPointerException e17) {
                                    log("null pointer error!");
                                    break;
                                } catch (RuntimeException e18) {
                                    log(headerField + "is not Text-String header field!");
                                    return null;
                                }
                            }
                        } else {
                            pduDataStream.reset();
                            byte[] messageClassString = parseWapString(pduDataStream, 0);
                            if (messageClassString == null) {
                                break;
                            } else {
                                try {
                                    headers.setTextString(messageClassString, 138);
                                    break;
                                } catch (NullPointerException e19) {
                                    log("null pointer error!");
                                    break;
                                } catch (RuntimeException e20) {
                                    log(headerField + "is not Text-String header field!");
                                    return null;
                                }
                            }
                        }
                    case 140:
                        int messageType = extractByteValue(pduDataStream);
                        switch (messageType) {
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
                                    headers.setOctet(messageType, headerField);
                                    continue;
                                } catch (InvalidHeaderValueException e21) {
                                    log("Set invalid Octet value: " + messageType + " into the header filed: " + headerField);
                                    return null;
                                } catch (RuntimeException e22) {
                                    log(headerField + "is not Octet header field!");
                                    return null;
                                }
                        }
                    case 141:
                        int version = parseShortInteger(pduDataStream);
                        try {
                            headers.setOctet(version, 141);
                            continue;
                        } catch (InvalidHeaderValueException e23) {
                            log("Set invalid Octet value: " + version + " into the header filed: " + headerField);
                            return null;
                        } catch (RuntimeException e24) {
                            log(headerField + "is not Octet header field!");
                            return null;
                        }
                    case 147:
                    case 150:
                    case 154:
                    case PduHeaders.STORE_STATUS_TEXT /* 166 */:
                    case PduHeaders.RECOMMENDED_RETRIEVAL_MODE_TEXT /* 181 */:
                    case PduHeaders.STATUS_TEXT /* 182 */:
                        EncodedStringValue value4 = parseEncodedStringValue(pduDataStream);
                        if (value4 != null) {
                            try {
                                headers.setEncodedStringValue(value4, headerField);
                                break;
                            } catch (NullPointerException e25) {
                                log("null pointer error!");
                                break;
                            } catch (RuntimeException e26) {
                                log(headerField + "is not Encoded-String-Value header field!");
                                return null;
                            }
                        } else {
                            continue;
                        }
                    case 160:
                        parseValueLength(pduDataStream);
                        try {
                            parseIntegerValue(pduDataStream);
                            EncodedStringValue previouslySentBy = parseEncodedStringValue(pduDataStream);
                            if (previouslySentBy != null) {
                                try {
                                    headers.setEncodedStringValue(previouslySentBy, 160);
                                    break;
                                } catch (NullPointerException e27) {
                                    log("null pointer error!");
                                    break;
                                } catch (RuntimeException e28) {
                                    log(headerField + "is not Encoded-String-Value header field!");
                                    return null;
                                }
                            } else {
                                continue;
                            }
                        } catch (RuntimeException e29) {
                            log(headerField + " is not Integer-Value");
                            return null;
                        }
                    case PduHeaders.PREVIOUSLY_SENT_DATE /* 161 */:
                        parseValueLength(pduDataStream);
                        try {
                            parseIntegerValue(pduDataStream);
                            try {
                                headers.setLongInteger(parseLongInteger(pduDataStream), PduHeaders.PREVIOUSLY_SENT_DATE);
                                continue;
                            } catch (RuntimeException e30) {
                                log(headerField + "is not Long-Integer header field!");
                                return null;
                            }
                        } catch (RuntimeException e31) {
                            log(headerField + " is not Integer-Value");
                            return null;
                        }
                    case PduHeaders.MM_FLAGS /* 164 */:
                        parseValueLength(pduDataStream);
                        extractByteValue(pduDataStream);
                        parseEncodedStringValue(pduDataStream);
                        continue;
                    case PduHeaders.ATTRIBUTES /* 168 */:
                    case 174:
                    case 176:
                    default:
                        log("Unknown header");
                        continue;
                    case PduHeaders.MBOX_TOTALS /* 170 */:
                    case PduHeaders.MBOX_QUOTAS /* 172 */:
                        parseValueLength(pduDataStream);
                        extractByteValue(pduDataStream);
                        try {
                            parseIntegerValue(pduDataStream);
                            continue;
                        } catch (RuntimeException e32) {
                            log(headerField + " is not Integer-Value");
                            return null;
                        }
                    case PduHeaders.MESSAGE_COUNT /* 173 */:
                    case PduHeaders.START /* 175 */:
                    case PduHeaders.LIMIT /* 179 */:
                        try {
                            headers.setLongInteger(parseIntegerValue(pduDataStream), headerField);
                            continue;
                        } catch (RuntimeException e33) {
                            log(headerField + "is not Long-Integer header field!");
                            return null;
                        }
                    case PduHeaders.ELEMENT_DESCRIPTOR /* 178 */:
                        parseContentType(pduDataStream, null);
                        continue;
                }
            } else {
                pduDataStream.reset();
                parseWapString(pduDataStream, 0);
            }
        }
        return headers;
    }

    protected PduBody parseParts(ByteArrayInputStream pduDataStream) {
        if (pduDataStream == null) {
            return null;
        }
        int count = parseUnsignedInt(pduDataStream);
        PduBody body = new PduBody();
        for (int i = 0; i < count; i++) {
            int headerLength = parseUnsignedInt(pduDataStream);
            int dataLength = parseUnsignedInt(pduDataStream);
            PduPart part = new PduPart();
            int startPos = pduDataStream.available();
            if (startPos <= 0) {
                return null;
            }
            HashMap<Integer, Object> map = new HashMap<>();
            byte[] contentType = parseContentType(pduDataStream, map);
            if (contentType != null) {
                part.setContentType(contentType);
            } else {
                part.setContentType(PduContentTypes.contentTypes[0].getBytes());
            }
            byte[] name = (byte[]) map.get(151);
            if (name != null) {
                part.setName(name);
            }
            Integer charset = (Integer) map.get(129);
            if (charset != null) {
                part.setCharset(charset.intValue());
            }
            int partHeaderLen = headerLength - (startPos - pduDataStream.available());
            if (partHeaderLen > 0) {
                if (!parsePartHeaders(pduDataStream, part, partHeaderLen)) {
                    return null;
                }
            } else if (partHeaderLen < 0) {
                return null;
            }
            if (part.getContentLocation() == null && part.getName() == null && part.getFilename() == null && part.getContentId() == null) {
                part.setContentLocation(Long.toOctalString(System.currentTimeMillis()).getBytes());
            }
            if (dataLength > 0) {
                byte[] partData = new byte[dataLength];
                String partContentType = new String(part.getContentType());
                pduDataStream.read(partData, 0, dataLength);
                if (partContentType.equalsIgnoreCase(ContentType.MULTIPART_ALTERNATIVE)) {
                    part = parseParts(new ByteArrayInputStream(partData)).getPart(0);
                } else {
                    byte[] partDataEncoding = part.getContentTransferEncoding();
                    if (partDataEncoding != null) {
                        String encoding = new String(partDataEncoding);
                        if (encoding.equalsIgnoreCase(PduPart.P_BASE64)) {
                            partData = Base64.decodeBase64(partData);
                        } else if (encoding.equalsIgnoreCase(PduPart.P_QUOTED_PRINTABLE)) {
                            partData = QuotedPrintable.decodeQuotedPrintable(partData);
                        }
                    }
                    if (partData == null) {
                        log("Decode part data error!");
                        return null;
                    }
                    part.setData(partData);
                }
            }
            if (checkPartPosition(part) == 0) {
                body.addPart(0, part);
            } else {
                body.addPart(part);
            }
        }
        return body;
    }

    private static void log(String text) {
    }

    protected static int parseUnsignedInt(ByteArrayInputStream pduDataStream) {
        if ($assertionsDisabled || pduDataStream != null) {
            int result = 0;
            int temp = pduDataStream.read();
            if (temp == -1) {
                return temp;
            }
            while ((temp & 128) != 0) {
                result = (result << 7) | (temp & 127);
                temp = pduDataStream.read();
                if (temp == -1) {
                    return temp;
                }
            }
            return (result << 7) | (temp & 127);
        }
        throw new AssertionError();
    }

    protected static int parseValueLength(ByteArrayInputStream pduDataStream) {
        if ($assertionsDisabled || pduDataStream != null) {
            int temp = pduDataStream.read();
            if ($assertionsDisabled || -1 != temp) {
                int first = temp & 255;
                if (first <= 30) {
                    return first;
                }
                if (first == 31) {
                    return parseUnsignedInt(pduDataStream);
                }
                throw new RuntimeException("Value length > LENGTH_QUOTE!");
            }
            throw new AssertionError();
        }
        throw new AssertionError();
    }

    protected static EncodedStringValue parseEncodedStringValue(ByteArrayInputStream pduDataStream) {
        EncodedStringValue returnValue;
        if ($assertionsDisabled || pduDataStream != null) {
            pduDataStream.mark(1);
            int charset = 0;
            int temp = pduDataStream.read();
            if ($assertionsDisabled || -1 != temp) {
                int first = temp & 255;
                if (first == 0) {
                    return new EncodedStringValue("");
                }
                pduDataStream.reset();
                if (first < 32) {
                    parseValueLength(pduDataStream);
                    charset = parseShortInteger(pduDataStream);
                }
                byte[] textString = parseWapString(pduDataStream, 0);
                try {
                    if (charset != 0) {
                        returnValue = new EncodedStringValue(charset, textString);
                    } else {
                        returnValue = new EncodedStringValue(textString);
                    }
                    return returnValue;
                } catch (Exception e) {
                    return null;
                }
            } else {
                throw new AssertionError();
            }
        } else {
            throw new AssertionError();
        }
    }

    protected static byte[] parseWapString(ByteArrayInputStream pduDataStream, int stringType) {
        if ($assertionsDisabled || pduDataStream != null) {
            pduDataStream.mark(1);
            int temp = pduDataStream.read();
            if ($assertionsDisabled || -1 != temp) {
                if (1 == stringType && 34 == temp) {
                    pduDataStream.mark(1);
                } else if (stringType == 0 && 127 == temp) {
                    pduDataStream.mark(1);
                } else {
                    pduDataStream.reset();
                }
                return getWapString(pduDataStream, stringType);
            }
            throw new AssertionError();
        }
        throw new AssertionError();
    }

    protected static boolean isTokenCharacter(int ch) {
        if (ch < 33 || ch > 126) {
            return false;
        }
        switch (ch) {
            case 34:
            case 40:
            case 41:
            case CallFailCause.CHANNEL_NOT_AVAIL /* 44 */:
            case 47:
            case 58:
            case RadioNVItems.RIL_NV_CDMA_EHRPD_FORCED /* 59 */:
            case 60:
            case 61:
            case 62:
            case SignalToneUtil.IS95_CONST_IR_SIG_TONE_NO_TONE /* 63 */:
            case 64:
            case 91:
            case 92:
            case 93:
            case 123:
            case 125:
                return false;
            default:
                return true;
        }
    }

    protected static boolean isText(int ch) {
        if (ch >= 32 && ch <= 126) {
            return true;
        }
        if (ch >= 128 && ch <= 255) {
            return true;
        }
        switch (ch) {
            case 9:
            case 10:
            case 13:
                return true;
            case 11:
            case 12:
            default:
                return false;
        }
    }

    protected static byte[] getWapString(ByteArrayInputStream pduDataStream, int stringType) {
        if ($assertionsDisabled || pduDataStream != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int temp = pduDataStream.read();
            if ($assertionsDisabled || -1 != temp) {
                while (-1 != temp && temp != 0) {
                    if (stringType == 2) {
                        if (isTokenCharacter(temp)) {
                            out.write(temp);
                        }
                    } else if (isText(temp)) {
                        out.write(temp);
                    }
                    temp = pduDataStream.read();
                    if (!$assertionsDisabled && -1 == temp) {
                        throw new AssertionError();
                    }
                }
                if (out.size() > 0) {
                    return out.toByteArray();
                }
                return null;
            }
            throw new AssertionError();
        }
        throw new AssertionError();
    }

    protected static int extractByteValue(ByteArrayInputStream pduDataStream) {
        if ($assertionsDisabled || pduDataStream != null) {
            int temp = pduDataStream.read();
            if ($assertionsDisabled || -1 != temp) {
                return temp & 255;
            }
            throw new AssertionError();
        }
        throw new AssertionError();
    }

    protected static int parseShortInteger(ByteArrayInputStream pduDataStream) {
        if ($assertionsDisabled || pduDataStream != null) {
            int temp = pduDataStream.read();
            if ($assertionsDisabled || -1 != temp) {
                return temp & 127;
            }
            throw new AssertionError();
        }
        throw new AssertionError();
    }

    protected static long parseLongInteger(ByteArrayInputStream pduDataStream) {
        if ($assertionsDisabled || pduDataStream != null) {
            int temp = pduDataStream.read();
            if ($assertionsDisabled || -1 != temp) {
                int count = temp & 255;
                if (count > 8) {
                    throw new RuntimeException("Octet count greater than 8 and I can't represent that!");
                }
                long result = 0;
                for (int i = 0; i < count; i++) {
                    int temp2 = pduDataStream.read();
                    if ($assertionsDisabled || -1 != temp2) {
                        result = (result << 8) + (temp2 & 255);
                    } else {
                        throw new AssertionError();
                    }
                }
                return result;
            }
            throw new AssertionError();
        }
        throw new AssertionError();
    }

    protected static long parseIntegerValue(ByteArrayInputStream pduDataStream) {
        if ($assertionsDisabled || pduDataStream != null) {
            pduDataStream.mark(1);
            int temp = pduDataStream.read();
            if ($assertionsDisabled || -1 != temp) {
                pduDataStream.reset();
                return temp > 127 ? parseShortInteger(pduDataStream) : parseLongInteger(pduDataStream);
            }
            throw new AssertionError();
        }
        throw new AssertionError();
    }

    protected static int skipWapValue(ByteArrayInputStream pduDataStream, int length) {
        if ($assertionsDisabled || pduDataStream != null) {
            int readLen = pduDataStream.read(new byte[length], 0, length);
            if (readLen < length) {
                return -1;
            }
            return readLen;
        }
        throw new AssertionError();
    }

    protected static void parseContentTypeParams(ByteArrayInputStream pduDataStream, HashMap<Integer, Object> map, Integer length) {
        if (!$assertionsDisabled && pduDataStream == null) {
            throw new AssertionError();
        } else if ($assertionsDisabled || length.intValue() > 0) {
            int startPos = pduDataStream.available();
            int lastLen = length.intValue();
            while (lastLen > 0) {
                int param = pduDataStream.read();
                if ($assertionsDisabled || -1 != param) {
                    lastLen--;
                    switch (param) {
                        case 129:
                            pduDataStream.mark(1);
                            int firstValue = extractByteValue(pduDataStream);
                            pduDataStream.reset();
                            if ((firstValue <= 32 || firstValue >= 127) && firstValue != 0) {
                                int charset = (int) parseIntegerValue(pduDataStream);
                                if (map != null) {
                                    map.put(129, Integer.valueOf(charset));
                                }
                            } else {
                                byte[] charsetStr = parseWapString(pduDataStream, 0);
                                try {
                                    map.put(129, Integer.valueOf(CharacterSets.getMibEnumValue(new String(charsetStr))));
                                } catch (UnsupportedEncodingException e) {
                                    Log.e(LOG_TAG, Arrays.toString(charsetStr), e);
                                    map.put(129, 0);
                                }
                            }
                            lastLen = length.intValue() - (startPos - pduDataStream.available());
                            break;
                        case 131:
                        case 137:
                            pduDataStream.mark(1);
                            int first = extractByteValue(pduDataStream);
                            pduDataStream.reset();
                            if (first > 127) {
                                int index = parseShortInteger(pduDataStream);
                                if (index < PduContentTypes.contentTypes.length) {
                                    map.put(131, PduContentTypes.contentTypes[index].getBytes());
                                }
                            } else {
                                byte[] type = parseWapString(pduDataStream, 0);
                                if (!(type == null || map == null)) {
                                    map.put(131, type);
                                }
                            }
                            lastLen = length.intValue() - (startPos - pduDataStream.available());
                            break;
                        case 133:
                        case 151:
                            byte[] name = parseWapString(pduDataStream, 0);
                            if (!(name == null || map == null)) {
                                map.put(151, name);
                            }
                            lastLen = length.intValue() - (startPos - pduDataStream.available());
                            break;
                        case 138:
                        case 153:
                            byte[] start = parseWapString(pduDataStream, 0);
                            if (!(start == null || map == null)) {
                                map.put(153, start);
                            }
                            lastLen = length.intValue() - (startPos - pduDataStream.available());
                            break;
                        default:
                            if (-1 != skipWapValue(pduDataStream, lastLen)) {
                                lastLen = 0;
                                break;
                            } else {
                                Log.e(LOG_TAG, "Corrupt Content-Type");
                                break;
                            }
                    }
                } else {
                    throw new AssertionError();
                }
            }
            if (lastLen != 0) {
                Log.e(LOG_TAG, "Corrupt Content-Type");
            }
        } else {
            throw new AssertionError();
        }
    }

    protected static byte[] parseContentType(ByteArrayInputStream pduDataStream, HashMap<Integer, Object> map) {
        byte[] contentType;
        if ($assertionsDisabled || pduDataStream != null) {
            pduDataStream.mark(1);
            int temp = pduDataStream.read();
            if ($assertionsDisabled || -1 != temp) {
                pduDataStream.reset();
                int cur = temp & 255;
                if (cur < 32) {
                    int length = parseValueLength(pduDataStream);
                    int startPos = pduDataStream.available();
                    pduDataStream.mark(1);
                    int temp2 = pduDataStream.read();
                    if ($assertionsDisabled || -1 != temp2) {
                        pduDataStream.reset();
                        int first = temp2 & 255;
                        if (first >= 32 && first <= 127) {
                            contentType = parseWapString(pduDataStream, 0);
                        } else if (first > 127) {
                            int index = parseShortInteger(pduDataStream);
                            if (index < PduContentTypes.contentTypes.length) {
                                contentType = PduContentTypes.contentTypes[index].getBytes();
                            } else {
                                pduDataStream.reset();
                                contentType = parseWapString(pduDataStream, 0);
                            }
                        } else {
                            Log.e(LOG_TAG, "Corrupt content-type");
                            return PduContentTypes.contentTypes[0].getBytes();
                        }
                        int parameterLen = length - (startPos - pduDataStream.available());
                        if (parameterLen > 0) {
                            parseContentTypeParams(pduDataStream, map, Integer.valueOf(parameterLen));
                        }
                        if (parameterLen < 0) {
                            Log.e(LOG_TAG, "Corrupt MMS message");
                            return PduContentTypes.contentTypes[0].getBytes();
                        }
                    } else {
                        throw new AssertionError();
                    }
                } else if (cur <= 127) {
                    contentType = parseWapString(pduDataStream, 0);
                } else {
                    contentType = PduContentTypes.contentTypes[parseShortInteger(pduDataStream)].getBytes();
                }
                return contentType;
            }
            throw new AssertionError();
        }
        throw new AssertionError();
    }

    protected boolean parsePartHeaders(ByteArrayInputStream pduDataStream, PduPart part, int length) {
        if (!$assertionsDisabled && pduDataStream == null) {
            throw new AssertionError();
        } else if (!$assertionsDisabled && part == null) {
            throw new AssertionError();
        } else if ($assertionsDisabled || length > 0) {
            int startPos = pduDataStream.available();
            int lastLen = length;
            while (lastLen > 0) {
                int header = pduDataStream.read();
                if ($assertionsDisabled || -1 != header) {
                    lastLen--;
                    if (header > 127) {
                        switch (header) {
                            case 142:
                                byte[] contentLocation = parseWapString(pduDataStream, 0);
                                if (contentLocation != null) {
                                    part.setContentLocation(contentLocation);
                                }
                                lastLen = length - (startPos - pduDataStream.available());
                                continue;
                            case 174:
                            case PduPart.P_CONTENT_DISPOSITION /* 197 */:
                                if (this.mParseContentDisposition) {
                                    int len = parseValueLength(pduDataStream);
                                    pduDataStream.mark(1);
                                    int thisStartPos = pduDataStream.available();
                                    int value = pduDataStream.read();
                                    if (value == 128) {
                                        part.setContentDisposition(PduPart.DISPOSITION_FROM_DATA);
                                    } else if (value == 129) {
                                        part.setContentDisposition(PduPart.DISPOSITION_ATTACHMENT);
                                    } else if (value == 130) {
                                        part.setContentDisposition(PduPart.DISPOSITION_INLINE);
                                    } else {
                                        pduDataStream.reset();
                                        part.setContentDisposition(parseWapString(pduDataStream, 0));
                                    }
                                    if (thisStartPos - pduDataStream.available() < len) {
                                        if (pduDataStream.read() == 152) {
                                            part.setFilename(parseWapString(pduDataStream, 0));
                                        }
                                        int thisEndPos = pduDataStream.available();
                                        if (thisStartPos - thisEndPos < len) {
                                            int last = len - (thisStartPos - thisEndPos);
                                            pduDataStream.read(new byte[last], 0, last);
                                        }
                                    }
                                    lastLen = length - (startPos - pduDataStream.available());
                                    break;
                                } else {
                                    continue;
                                }
                            case 192:
                                byte[] contentId = parseWapString(pduDataStream, 1);
                                if (contentId != null) {
                                    part.setContentId(contentId);
                                }
                                lastLen = length - (startPos - pduDataStream.available());
                                continue;
                            default:
                                if (-1 == skipWapValue(pduDataStream, lastLen)) {
                                    Log.e(LOG_TAG, "Corrupt Part headers");
                                    return false;
                                }
                                lastLen = 0;
                                continue;
                        }
                    } else if (header >= 32 && header <= 127) {
                        byte[] tempHeader = parseWapString(pduDataStream, 0);
                        byte[] tempValue = parseWapString(pduDataStream, 0);
                        if (true == PduPart.CONTENT_TRANSFER_ENCODING.equalsIgnoreCase(new String(tempHeader))) {
                            part.setContentTransferEncoding(tempValue);
                        }
                        lastLen = length - (startPos - pduDataStream.available());
                    } else if (-1 == skipWapValue(pduDataStream, lastLen)) {
                        Log.e(LOG_TAG, "Corrupt Part headers");
                        return false;
                    } else {
                        lastLen = 0;
                    }
                } else {
                    throw new AssertionError();
                }
            }
            if (lastLen == 0) {
                return true;
            }
            Log.e(LOG_TAG, "Corrupt Part headers");
            return false;
        } else {
            throw new AssertionError();
        }
    }

    private static int checkPartPosition(PduPart part) {
        byte[] contentType;
        byte[] contentId;
        if (!$assertionsDisabled && part == null) {
            throw new AssertionError();
        } else if (mTypeParam == null && mStartParam == null) {
            return 1;
        } else {
            if (mStartParam == null || (contentId = part.getContentId()) == null || true != Arrays.equals(mStartParam, contentId)) {
                return (mTypeParam == null || (contentType = part.getContentType()) == null || true != Arrays.equals(mTypeParam, contentType)) ? 1 : 0;
            }
            return 0;
        }
    }

    protected static boolean checkMandatoryHeader(PduHeaders headers) {
        if (headers == null) {
            return false;
        }
        int messageType = headers.getOctet(140);
        if (headers.getOctet(141) == 0) {
            return false;
        }
        switch (messageType) {
            case 128:
                if (headers.getTextString(132) == null || headers.getEncodedStringValue(137) == null || headers.getTextString(152) == null) {
                    return false;
                }
                break;
            case 129:
                if (headers.getOctet(146) == 0 || headers.getTextString(152) == null) {
                    return false;
                }
                break;
            case 130:
                if (headers.getTextString(131) == null || -1 == headers.getLongInteger(136) || headers.getTextString(138) == null || -1 == headers.getLongInteger(142) || headers.getTextString(152) == null) {
                    return false;
                }
                break;
            case 131:
                if (headers.getOctet(149) == 0 || headers.getTextString(152) == null) {
                    return false;
                }
                break;
            case 132:
                if (headers.getTextString(132) == null || -1 == headers.getLongInteger(133)) {
                    return false;
                }
                break;
            case 133:
                if (headers.getTextString(152) == null) {
                    return false;
                }
                break;
            case 134:
                if (-1 == headers.getLongInteger(133) || headers.getTextString(139) == null || headers.getOctet(149) == 0 || headers.getEncodedStringValues(151) == null) {
                    return false;
                }
                break;
            case 135:
                if (headers.getEncodedStringValue(137) == null || headers.getTextString(139) == null || headers.getOctet(155) == 0 || headers.getEncodedStringValues(151) == null) {
                    return false;
                }
                break;
            case 136:
                if (-1 == headers.getLongInteger(133) || headers.getEncodedStringValue(137) == null || headers.getTextString(139) == null || headers.getOctet(155) == 0 || headers.getEncodedStringValues(151) == null) {
                    return false;
                }
                break;
            default:
                return false;
        }
        return true;
    }
}
