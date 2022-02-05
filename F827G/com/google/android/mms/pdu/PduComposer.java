package com.google.android.mms.pdu;

import android.content.ContentResolver;
import android.content.Context;
import android.text.TextUtils;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class PduComposer {
    static final /* synthetic */ boolean $assertionsDisabled;
    private static final int END_STRING_FLAG = 0;
    private static final int LENGTH_QUOTE = 31;
    private static final int LONG_INTEGER_LENGTH_MAX = 8;
    private static final int PDU_COMPOSER_BLOCK_SIZE = 1024;
    private static final int PDU_COMPOSE_CONTENT_ERROR = 1;
    private static final int PDU_COMPOSE_FIELD_NOT_SET = 2;
    private static final int PDU_COMPOSE_FIELD_NOT_SUPPORTED = 3;
    private static final int PDU_COMPOSE_SUCCESS = 0;
    private static final int PDU_EMAIL_ADDRESS_TYPE = 2;
    private static final int PDU_IPV4_ADDRESS_TYPE = 3;
    private static final int PDU_IPV6_ADDRESS_TYPE = 4;
    private static final int PDU_PHONE_NUMBER_ADDRESS_TYPE = 1;
    private static final int PDU_UNKNOWN_ADDRESS_TYPE = 5;
    private static final int QUOTED_STRING_FLAG = 34;
    static final String REGEXP_EMAIL_ADDRESS_TYPE = "[a-zA-Z| ]*\\<{0,1}[a-zA-Z| ]+@{1}[a-zA-Z| ]+\\.{1}[a-zA-Z| ]+\\>{0,1}";
    static final String REGEXP_IPV4_ADDRESS_TYPE = "[0-9]{1,3}\\.{1}[0-9]{1,3}\\.{1}[0-9]{1,3}\\.{1}[0-9]{1,3}";
    static final String REGEXP_IPV6_ADDRESS_TYPE = "[a-fA-F]{4}\\:{1}[a-fA-F0-9]{4}\\:{1}[a-fA-F0-9]{4}\\:{1}[a-fA-F0-9]{4}\\:{1}[a-fA-F0-9]{4}\\:{1}[a-fA-F0-9]{4}\\:{1}[a-fA-F0-9]{4}\\:{1}[a-fA-F0-9]{4}";
    static final String REGEXP_PHONE_NUMBER_ADDRESS_TYPE = "\\+?[0-9|\\.|\\-]+";
    private static final int SHORT_INTEGER_MAX = 127;
    static final String STRING_IPV4_ADDRESS_TYPE = "/TYPE=IPV4";
    static final String STRING_IPV6_ADDRESS_TYPE = "/TYPE=IPV6";
    static final String STRING_PHONE_NUMBER_ADDRESS_TYPE = "/TYPE=PLMN";
    private static final int TEXT_MAX = 127;
    private static HashMap<String, Integer> mContentTypeMap;
    protected ByteArrayOutputStream mMessage;
    private GenericPdu mPdu;
    private PduHeaders mPduHeader;
    protected int mPosition;
    private final ContentResolver mResolver;
    private BufferStack mStack;

    static {
        $assertionsDisabled = !PduComposer.class.desiredAssertionStatus();
        mContentTypeMap = null;
        mContentTypeMap = new HashMap<>();
        for (int i = 0; i < PduContentTypes.contentTypes.length; i++) {
            mContentTypeMap.put(PduContentTypes.contentTypes[i], Integer.valueOf(i));
        }
    }

    public PduComposer(Context context, GenericPdu pdu) {
        this.mMessage = null;
        this.mPdu = null;
        this.mPosition = 0;
        this.mStack = null;
        this.mPduHeader = null;
        this.mPdu = pdu;
        this.mResolver = context.getContentResolver();
        this.mPduHeader = pdu.getPduHeaders();
        this.mStack = new BufferStack();
        this.mMessage = new ByteArrayOutputStream();
        this.mPosition = 0;
    }

    public byte[] make() {
        int type = this.mPdu.getMessageType();
        switch (type) {
            case 128:
            case 132:
                if (makeSendRetrievePdu(type) != 0) {
                    return null;
                }
                break;
            case 129:
            case 130:
            case 134:
            default:
                return null;
            case 131:
                if (makeNotifyResp() != 0) {
                    return null;
                }
                break;
            case 133:
                if (makeAckInd() != 0) {
                    return null;
                }
                break;
            case 135:
                if (makeReadRecInd() != 0) {
                    return null;
                }
                break;
        }
        return this.mMessage.toByteArray();
    }

    protected void arraycopy(byte[] buf, int pos, int length) {
        this.mMessage.write(buf, pos, length);
        this.mPosition += length;
    }

    protected void append(int value) {
        this.mMessage.write(value);
        this.mPosition++;
    }

    protected void appendShortInteger(int value) {
        append((value | 128) & 255);
    }

    protected void appendOctet(int number) {
        append(number);
    }

    protected void appendShortLength(int value) {
        append(value);
    }

    protected void appendLongInteger(long longInt) {
        long temp = longInt;
        int size = 0;
        while (temp != 0 && size < 8) {
            temp >>>= 8;
            size++;
        }
        appendShortLength(size);
        int shift = (size - 1) * 8;
        for (int i = 0; i < size; i++) {
            append((int) ((longInt >>> shift) & 255));
            shift -= 8;
        }
    }

    protected void appendTextString(byte[] text) {
        if ((text[0] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) > 127) {
            append(127);
        }
        arraycopy(text, 0, text.length);
        append(0);
    }

    protected void appendTextString(String str) {
        appendTextString(str.getBytes());
    }

    protected void appendEncodedString(EncodedStringValue enStr) {
        if ($assertionsDisabled || enStr != null) {
            int charset = enStr.getCharacterSet();
            byte[] textString = enStr.getTextString();
            if (textString != null) {
                this.mStack.newbuf();
                PositionMarker start = this.mStack.mark();
                appendShortInteger(charset);
                appendTextString(textString);
                int len = start.getLength();
                this.mStack.pop();
                appendValueLength(len);
                this.mStack.copy();
                return;
            }
            return;
        }
        throw new AssertionError();
    }

    protected void appendUintvarInteger(long value) {
        long max = 127;
        int i = 0;
        while (i < 5 && value >= max) {
            max = (max << 7) | 127;
            i++;
        }
        while (i > 0) {
            append((int) ((128 | ((value >>> (i * 7)) & 127)) & 255));
            i--;
        }
        append((int) (value & 127));
    }

    protected void appendDateValue(long date) {
        appendLongInteger(date);
    }

    protected void appendValueLength(long value) {
        if (value < 31) {
            appendShortLength((int) value);
            return;
        }
        append(31);
        appendUintvarInteger(value);
    }

    protected void appendQuotedString(byte[] text) {
        append(34);
        arraycopy(text, 0, text.length);
        append(0);
    }

    protected void appendQuotedString(String str) {
        appendQuotedString(str.getBytes());
    }

    private EncodedStringValue appendAddressType(EncodedStringValue address) {
        try {
            int addressType = checkAddressType(address.getString());
            EncodedStringValue temp = EncodedStringValue.copy(address);
            if (1 == addressType) {
                temp.appendTextString(STRING_PHONE_NUMBER_ADDRESS_TYPE.getBytes());
            } else if (3 == addressType) {
                temp.appendTextString(STRING_IPV4_ADDRESS_TYPE.getBytes());
            } else if (4 == addressType) {
                temp.appendTextString(STRING_IPV6_ADDRESS_TYPE.getBytes());
            }
            return temp;
        } catch (NullPointerException e) {
            return null;
        }
    }

    private int appendHeader(int field) {
        switch (field) {
            case 129:
            case 130:
            case 151:
                EncodedStringValue[] addr = this.mPduHeader.getEncodedStringValues(field);
                if (addr != null) {
                    for (EncodedStringValue encodedStringValue : addr) {
                        EncodedStringValue temp = appendAddressType(encodedStringValue);
                        if (temp == null) {
                            return 1;
                        }
                        appendOctet(field);
                        appendEncodedString(temp);
                    }
                    break;
                } else {
                    return 2;
                }
            case 131:
            case 132:
            case 135:
            case 140:
            case 142:
            case 146:
            case 147:
            case 148:
            default:
                return 3;
            case 133:
                long date = this.mPduHeader.getLongInteger(field);
                if (-1 != date) {
                    appendOctet(field);
                    appendDateValue(date);
                    break;
                } else {
                    return 2;
                }
            case 134:
            case 143:
            case 144:
            case 145:
            case 149:
            case 153:
            case 155:
                int octet = this.mPduHeader.getOctet(field);
                if (octet != 0) {
                    appendOctet(field);
                    appendOctet(octet);
                    break;
                } else {
                    return 2;
                }
            case 136:
                long expiry = this.mPduHeader.getLongInteger(field);
                if (-1 != expiry) {
                    appendOctet(field);
                    this.mStack.newbuf();
                    PositionMarker expiryStart = this.mStack.mark();
                    append(129);
                    appendLongInteger(expiry);
                    int expiryLength = expiryStart.getLength();
                    this.mStack.pop();
                    appendValueLength(expiryLength);
                    this.mStack.copy();
                    break;
                } else {
                    return 2;
                }
            case 137:
                appendOctet(field);
                EncodedStringValue from = this.mPduHeader.getEncodedStringValue(field);
                if (from == null || TextUtils.isEmpty(from.getString()) || new String(from.getTextString()).equals(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR)) {
                    append(1);
                    append(129);
                    break;
                } else {
                    this.mStack.newbuf();
                    PositionMarker fstart = this.mStack.mark();
                    append(128);
                    EncodedStringValue temp2 = appendAddressType(from);
                    if (temp2 != null) {
                        appendEncodedString(temp2);
                        int flen = fstart.getLength();
                        this.mStack.pop();
                        appendValueLength(flen);
                        this.mStack.copy();
                        break;
                    } else {
                        return 1;
                    }
                }
                break;
            case 138:
                byte[] messageClass = this.mPduHeader.getTextString(field);
                if (messageClass != null) {
                    appendOctet(field);
                    if (!Arrays.equals(messageClass, PduHeaders.MESSAGE_CLASS_ADVERTISEMENT_STR.getBytes())) {
                        if (!Arrays.equals(messageClass, PduHeaders.MESSAGE_CLASS_AUTO_STR.getBytes())) {
                            if (!Arrays.equals(messageClass, PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes())) {
                                if (Arrays.equals(messageClass, PduHeaders.MESSAGE_CLASS_INFORMATIONAL_STR.getBytes())) {
                                    appendOctet(130);
                                    break;
                                } else {
                                    appendTextString(messageClass);
                                    break;
                                }
                            } else {
                                appendOctet(128);
                                break;
                            }
                        } else {
                            appendOctet(131);
                            break;
                        }
                    } else {
                        appendOctet(129);
                        break;
                    }
                } else {
                    return 2;
                }
            case 139:
            case 152:
                byte[] textString = this.mPduHeader.getTextString(field);
                if (textString != null) {
                    appendOctet(field);
                    appendTextString(textString);
                    break;
                } else {
                    return 2;
                }
            case 141:
                appendOctet(field);
                int version = this.mPduHeader.getOctet(field);
                if (version == 0) {
                    appendShortInteger(18);
                    break;
                } else {
                    appendShortInteger(version);
                    break;
                }
            case 150:
            case 154:
                EncodedStringValue enString = this.mPduHeader.getEncodedStringValue(field);
                if (enString != null) {
                    appendOctet(field);
                    appendEncodedString(enString);
                    break;
                } else {
                    return 2;
                }
        }
        return 0;
    }

    private int makeReadRecInd() {
        if (this.mMessage == null) {
            this.mMessage = new ByteArrayOutputStream();
            this.mPosition = 0;
        }
        appendOctet(140);
        appendOctet(135);
        if (appendHeader(141) != 0 || appendHeader(139) != 0 || appendHeader(151) != 0 || appendHeader(137) != 0) {
            return 1;
        }
        appendHeader(133);
        return appendHeader(155) == 0 ? 0 : 1;
    }

    private int makeNotifyResp() {
        if (this.mMessage == null) {
            this.mMessage = new ByteArrayOutputStream();
            this.mPosition = 0;
        }
        appendOctet(140);
        appendOctet(131);
        return (appendHeader(152) == 0 && appendHeader(141) == 0 && appendHeader(149) == 0) ? 0 : 1;
    }

    private int makeAckInd() {
        if (this.mMessage == null) {
            this.mMessage = new ByteArrayOutputStream();
            this.mPosition = 0;
        }
        appendOctet(140);
        appendOctet(133);
        if (appendHeader(152) != 0 || appendHeader(141) != 0) {
            return 1;
        }
        appendHeader(145);
        return 0;
    }

    private int makeSendRetrievePdu(int type) {
        if (this.mMessage == null) {
            this.mMessage = new ByteArrayOutputStream();
            this.mPosition = 0;
        }
        appendOctet(140);
        appendOctet(type);
        appendOctet(152);
        byte[] trid = this.mPduHeader.getTextString(152);
        if (trid == null) {
            throw new IllegalArgumentException("Transaction-ID is null.");
        }
        appendTextString(trid);
        if (appendHeader(141) != 0) {
            return 1;
        }
        appendHeader(133);
        if (appendHeader(137) != 0) {
            return 1;
        }
        boolean recipient = false;
        if (appendHeader(151) != 1) {
            recipient = true;
        }
        if (appendHeader(130) != 1) {
            recipient = true;
        }
        if (appendHeader(129) != 1) {
            recipient = true;
        }
        if (!recipient) {
            return 1;
        }
        appendHeader(150);
        appendHeader(138);
        appendHeader(136);
        appendHeader(143);
        appendHeader(134);
        appendHeader(144);
        if (type == 132) {
            appendHeader(153);
            appendHeader(154);
        }
        appendOctet(132);
        return makeMessageBody(type);
    }

    private int makeMessageBody(int type) {
        PduBody body;
        this.mStack.newbuf();
        PositionMarker ctStart = this.mStack.mark();
        Integer contentTypeIdentifier = mContentTypeMap.get(new String(this.mPduHeader.getTextString(132)));
        if (contentTypeIdentifier == null) {
            return 1;
        }
        appendShortInteger(contentTypeIdentifier.intValue());
        if (type == 132) {
            body = ((RetrieveConf) this.mPdu).getBody();
        } else {
            body = ((SendReq) this.mPdu).getBody();
        }
        if (body == null || body.getPartsNum() == 0) {
            appendUintvarInteger(0L);
            this.mStack.pop();
            this.mStack.copy();
            return 0;
        }
        try {
            PduPart part = body.getPart(0);
            byte[] start = part.getContentId();
            if (start != null) {
                appendOctet(138);
                if (60 == start[0] && 62 == start[start.length - 1]) {
                    appendTextString(start);
                } else {
                    appendTextString("<" + new String(start) + ">");
                }
            }
            appendOctet(137);
            appendTextString(part.getContentType());
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        int ctLength = ctStart.getLength();
        this.mStack.pop();
        appendValueLength(ctLength);
        this.mStack.copy();
        int partNum = body.getPartsNum();
        appendUintvarInteger(partNum);
        for (int i = 0; i < partNum; i++) {
            PduPart part2 = body.getPart(i);
            this.mStack.newbuf();
            PositionMarker attachment = this.mStack.mark();
            this.mStack.newbuf();
            PositionMarker contentTypeBegin = this.mStack.mark();
            byte[] partContentType = part2.getContentType();
            if (partContentType == null) {
                return 1;
            }
            Integer partContentTypeIdentifier = mContentTypeMap.get(new String(partContentType));
            if (partContentTypeIdentifier == null) {
                appendTextString(partContentType);
            } else {
                appendShortInteger(partContentTypeIdentifier.intValue());
            }
            byte[] name = part2.getName();
            if (name == null && (name = part2.getFilename()) == null && (name = part2.getContentLocation()) == null && (name = part2.getContentId()) == null) {
                return 1;
            }
            appendOctet(133);
            appendTextString(name);
            int charset = part2.getCharset();
            if (charset != 0) {
                appendOctet(129);
                appendShortInteger(charset);
            }
            int contentTypeLength = contentTypeBegin.getLength();
            this.mStack.pop();
            appendValueLength(contentTypeLength);
            this.mStack.copy();
            byte[] contentId = part2.getContentId();
            if (contentId != null) {
                appendOctet(192);
                if (60 == contentId[0] && 62 == contentId[contentId.length - 1]) {
                    appendQuotedString(contentId);
                } else {
                    appendQuotedString("<" + new String(contentId) + ">");
                }
            }
            byte[] contentLocation = part2.getContentLocation();
            if (contentLocation != null) {
                appendOctet(142);
                appendTextString(contentLocation);
            }
            int headerLength = attachment.getLength();
            int dataLength = 0;
            byte[] partData = part2.getData();
            if (partData != null) {
                arraycopy(partData, 0, partData.length);
                dataLength = partData.length;
            } else {
                InputStream cr = null;
                try {
                    byte[] buffer = new byte[PDU_COMPOSER_BLOCK_SIZE];
                    cr = this.mResolver.openInputStream(part2.getDataUri());
                    while (true) {
                        int len = cr.read(buffer);
                        if (len == -1) {
                            break;
                        }
                        this.mMessage.write(buffer, 0, len);
                        this.mPosition += len;
                        dataLength += len;
                    }
                    if (cr != null) {
                        try {
                            cr.close();
                        } catch (IOException e2) {
                        }
                    }
                } catch (FileNotFoundException e3) {
                    if (cr == null) {
                        return 1;
                    }
                    try {
                        cr.close();
                        return 1;
                    } catch (IOException e4) {
                        return 1;
                    }
                } catch (IOException e5) {
                    if (cr == null) {
                        return 1;
                    }
                    try {
                        cr.close();
                        return 1;
                    } catch (IOException e6) {
                        return 1;
                    }
                } catch (RuntimeException e7) {
                    if (cr == null) {
                        return 1;
                    }
                    try {
                        cr.close();
                        return 1;
                    } catch (IOException e8) {
                        return 1;
                    }
                } catch (Throwable th) {
                    if (cr != null) {
                        try {
                            cr.close();
                        } catch (IOException e9) {
                        }
                    }
                    throw th;
                }
            }
            if (dataLength != attachment.getLength() - headerLength) {
                throw new RuntimeException("BUG: Length sanity check failed");
            }
            this.mStack.pop();
            appendUintvarInteger(headerLength);
            appendUintvarInteger(dataLength);
            this.mStack.copy();
        }
        return 0;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class LengthRecordNode {
        ByteArrayOutputStream currentMessage;
        public int currentPosition;
        public LengthRecordNode next;

        private LengthRecordNode() {
            this.currentMessage = null;
            this.currentPosition = 0;
            this.next = null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class PositionMarker {
        private int c_pos;
        private int currentStackSize;

        private PositionMarker() {
        }

        int getLength() {
            if (this.currentStackSize == PduComposer.this.mStack.stackSize) {
                return PduComposer.this.mPosition - this.c_pos;
            }
            throw new RuntimeException("BUG: Invalid call to getLength()");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class BufferStack {
        private LengthRecordNode stack;
        int stackSize;
        private LengthRecordNode toCopy;

        private BufferStack() {
            this.stack = null;
            this.toCopy = null;
            this.stackSize = 0;
        }

        void newbuf() {
            if (this.toCopy != null) {
                throw new RuntimeException("BUG: Invalid newbuf() before copy()");
            }
            LengthRecordNode temp = new LengthRecordNode();
            temp.currentMessage = PduComposer.this.mMessage;
            temp.currentPosition = PduComposer.this.mPosition;
            temp.next = this.stack;
            this.stack = temp;
            this.stackSize++;
            PduComposer.this.mMessage = new ByteArrayOutputStream();
            PduComposer.this.mPosition = 0;
        }

        void pop() {
            ByteArrayOutputStream currentMessage = PduComposer.this.mMessage;
            int currentPosition = PduComposer.this.mPosition;
            PduComposer.this.mMessage = this.stack.currentMessage;
            PduComposer.this.mPosition = this.stack.currentPosition;
            this.toCopy = this.stack;
            this.stack = this.stack.next;
            this.stackSize--;
            this.toCopy.currentMessage = currentMessage;
            this.toCopy.currentPosition = currentPosition;
        }

        void copy() {
            PduComposer.this.arraycopy(this.toCopy.currentMessage.toByteArray(), 0, this.toCopy.currentPosition);
            this.toCopy = null;
        }

        PositionMarker mark() {
            PositionMarker m = new PositionMarker();
            m.c_pos = PduComposer.this.mPosition;
            m.currentStackSize = this.stackSize;
            return m;
        }
    }

    protected static int checkAddressType(String address) {
        if (address == null) {
            return 5;
        }
        if (address.matches(REGEXP_IPV4_ADDRESS_TYPE)) {
            return 3;
        }
        if (address.matches(REGEXP_PHONE_NUMBER_ADDRESS_TYPE)) {
            return 1;
        }
        if (address.matches(REGEXP_EMAIL_ADDRESS_TYPE)) {
            return 2;
        }
        if (address.matches(REGEXP_IPV6_ADDRESS_TYPE)) {
            return 4;
        }
        return 5;
    }
}
