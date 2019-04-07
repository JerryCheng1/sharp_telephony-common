package com.google.android.mms.pdu;

import android.content.ContentResolver;
import android.content.Context;
import android.text.TextUtils;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;

public class PduComposer {
    static final /* synthetic */ boolean $assertionsDisabled = (!PduComposer.class.desiredAssertionStatus());
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
    protected ByteArrayOutputStream mMessage = null;
    private GenericPdu mPdu = null;
    private PduHeaders mPduHeader = null;
    protected int mPosition = 0;
    private final ContentResolver mResolver;
    private BufferStack mStack = null;

    private class BufferStack {
        private LengthRecordNode stack;
        int stackSize;
        private LengthRecordNode toCopy;

        private BufferStack() {
            this.stack = null;
            this.toCopy = null;
            this.stackSize = 0;
        }

        /* Access modifiers changed, original: 0000 */
        public void copy() {
            PduComposer.this.arraycopy(this.toCopy.currentMessage.toByteArray(), 0, this.toCopy.currentPosition);
            this.toCopy = null;
        }

        /* Access modifiers changed, original: 0000 */
        public PositionMarker mark() {
            PositionMarker positionMarker = new PositionMarker();
            positionMarker.c_pos = PduComposer.this.mPosition;
            positionMarker.currentStackSize = this.stackSize;
            return positionMarker;
        }

        /* Access modifiers changed, original: 0000 */
        public void newbuf() {
            if (this.toCopy != null) {
                throw new RuntimeException("BUG: Invalid newbuf() before copy()");
            }
            LengthRecordNode lengthRecordNode = new LengthRecordNode();
            lengthRecordNode.currentMessage = PduComposer.this.mMessage;
            lengthRecordNode.currentPosition = PduComposer.this.mPosition;
            lengthRecordNode.next = this.stack;
            this.stack = lengthRecordNode;
            this.stackSize++;
            PduComposer.this.mMessage = new ByteArrayOutputStream();
            PduComposer.this.mPosition = 0;
        }

        /* Access modifiers changed, original: 0000 */
        public void pop() {
            ByteArrayOutputStream byteArrayOutputStream = PduComposer.this.mMessage;
            int i = PduComposer.this.mPosition;
            PduComposer.this.mMessage = this.stack.currentMessage;
            PduComposer.this.mPosition = this.stack.currentPosition;
            this.toCopy = this.stack;
            this.stack = this.stack.next;
            this.stackSize--;
            this.toCopy.currentMessage = byteArrayOutputStream;
            this.toCopy.currentPosition = i;
        }
    }

    private static class LengthRecordNode {
        ByteArrayOutputStream currentMessage;
        public int currentPosition;
        public LengthRecordNode next;

        private LengthRecordNode() {
            this.currentMessage = null;
            this.currentPosition = 0;
            this.next = null;
        }
    }

    private class PositionMarker {
        private int c_pos;
        private int currentStackSize;

        private PositionMarker() {
        }

        /* Access modifiers changed, original: 0000 */
        public int getLength() {
            if (this.currentStackSize == PduComposer.this.mStack.stackSize) {
                return PduComposer.this.mPosition - this.c_pos;
            }
            throw new RuntimeException("BUG: Invalid call to getLength()");
        }
    }

    static {
        int i = 0;
        mContentTypeMap = null;
        mContentTypeMap = new HashMap();
        while (i < PduContentTypes.contentTypes.length) {
            mContentTypeMap.put(PduContentTypes.contentTypes[i], Integer.valueOf(i));
            i++;
        }
    }

    public PduComposer(Context context, GenericPdu genericPdu) {
        this.mPdu = genericPdu;
        this.mResolver = context.getContentResolver();
        this.mPduHeader = genericPdu.getPduHeaders();
        this.mStack = new BufferStack();
        this.mMessage = new ByteArrayOutputStream();
        this.mPosition = 0;
    }

    private EncodedStringValue appendAddressType(EncodedStringValue encodedStringValue) {
        try {
            int checkAddressType = checkAddressType(encodedStringValue.getString());
            EncodedStringValue copy = EncodedStringValue.copy(encodedStringValue);
            if (1 == checkAddressType) {
                copy.appendTextString(STRING_PHONE_NUMBER_ADDRESS_TYPE.getBytes());
                return copy;
            } else if (3 == checkAddressType) {
                copy.appendTextString(STRING_IPV4_ADDRESS_TYPE.getBytes());
                return copy;
            } else if (4 != checkAddressType) {
                return copy;
            } else {
                copy.appendTextString(STRING_IPV6_ADDRESS_TYPE.getBytes());
                return copy;
            }
        } catch (NullPointerException e) {
            return null;
        }
    }

    private int appendHeader(int i) {
        int length;
        long longInteger;
        byte[] textString;
        switch (i) {
            case 129:
            case 130:
            case 151:
                EncodedStringValue[] encodedStringValues = this.mPduHeader.getEncodedStringValues(i);
                if (encodedStringValues == null) {
                    return 2;
                }
                for (EncodedStringValue appendAddressType : encodedStringValues) {
                    EncodedStringValue appendAddressType2 = appendAddressType(appendAddressType2);
                    if (appendAddressType2 == null) {
                        return 1;
                    }
                    appendOctet(i);
                    appendEncodedString(appendAddressType2);
                }
                return 0;
            case 133:
                longInteger = this.mPduHeader.getLongInteger(i);
                if (-1 == longInteger) {
                    return 2;
                }
                appendOctet(i);
                appendDateValue(longInteger);
                return 0;
            case 134:
            case 143:
            case 144:
            case 145:
            case 149:
            case 153:
            case 155:
                int octet = this.mPduHeader.getOctet(i);
                if (octet == 0) {
                    return 2;
                }
                appendOctet(i);
                appendOctet(octet);
                return 0;
            case 136:
                longInteger = this.mPduHeader.getLongInteger(i);
                if (-1 == longInteger) {
                    return 2;
                }
                appendOctet(i);
                this.mStack.newbuf();
                PositionMarker mark = this.mStack.mark();
                append(129);
                appendLongInteger(longInteger);
                length = mark.getLength();
                this.mStack.pop();
                appendValueLength((long) length);
                this.mStack.copy();
                return 0;
            case 137:
                appendOctet(i);
                EncodedStringValue encodedStringValue = this.mPduHeader.getEncodedStringValue(i);
                if (encodedStringValue == null || TextUtils.isEmpty(encodedStringValue.getString()) || new String(encodedStringValue.getTextString()).equals(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR)) {
                    append(1);
                    append(129);
                    return 0;
                }
                this.mStack.newbuf();
                PositionMarker mark2 = this.mStack.mark();
                append(128);
                encodedStringValue = appendAddressType(encodedStringValue);
                if (encodedStringValue == null) {
                    return 1;
                }
                appendEncodedString(encodedStringValue);
                length = mark2.getLength();
                this.mStack.pop();
                appendValueLength((long) length);
                this.mStack.copy();
                return 0;
            case 138:
                textString = this.mPduHeader.getTextString(i);
                if (textString == null) {
                    return 2;
                }
                appendOctet(i);
                if (Arrays.equals(textString, PduHeaders.MESSAGE_CLASS_ADVERTISEMENT_STR.getBytes())) {
                    appendOctet(129);
                    return 0;
                } else if (Arrays.equals(textString, PduHeaders.MESSAGE_CLASS_AUTO_STR.getBytes())) {
                    appendOctet(131);
                    return 0;
                } else if (Arrays.equals(textString, PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes())) {
                    appendOctet(128);
                    return 0;
                } else if (Arrays.equals(textString, PduHeaders.MESSAGE_CLASS_INFORMATIONAL_STR.getBytes())) {
                    appendOctet(130);
                    return 0;
                } else {
                    appendTextString(textString);
                    return 0;
                }
            case 139:
            case 152:
                textString = this.mPduHeader.getTextString(i);
                if (textString == null) {
                    return 2;
                }
                appendOctet(i);
                appendTextString(textString);
                return 0;
            case 141:
                appendOctet(i);
                length = this.mPduHeader.getOctet(i);
                if (length == 0) {
                    appendShortInteger(18);
                    return 0;
                }
                appendShortInteger(length);
                return 0;
            case 150:
            case 154:
                EncodedStringValue encodedStringValue2 = this.mPduHeader.getEncodedStringValue(i);
                if (encodedStringValue2 == null) {
                    return 2;
                }
                appendOctet(i);
                appendEncodedString(encodedStringValue2);
                return 0;
            default:
                return 3;
        }
    }

    protected static int checkAddressType(String str) {
        if (str != null) {
            if (str.matches(REGEXP_IPV4_ADDRESS_TYPE)) {
                return 3;
            }
            if (str.matches(REGEXP_PHONE_NUMBER_ADDRESS_TYPE)) {
                return 1;
            }
            if (str.matches(REGEXP_EMAIL_ADDRESS_TYPE)) {
                return 2;
            }
            if (str.matches(REGEXP_IPV6_ADDRESS_TYPE)) {
                return 4;
            }
        }
        return 5;
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

    /* JADX WARNING: Removed duplicated region for block: B:90:0x01fd A:{SYNTHETIC, Splitter:B:90:0x01fd} */
    /* JADX WARNING: Removed duplicated region for block: B:98:0x020b A:{SYNTHETIC, Splitter:B:98:0x020b} */
    /* JADX WARNING: Removed duplicated region for block: B:106:0x0219 A:{SYNTHETIC, Splitter:B:106:0x0219} */
    private int makeMessageBody(int r14) {
        /*
        r13 = this;
        r0 = r13.mStack;
        r0.newbuf();
        r0 = r13.mStack;
        r1 = r0.mark();
        r0 = new java.lang.String;
        r2 = r13.mPduHeader;
        r3 = 132; // 0x84 float:1.85E-43 double:6.5E-322;
        r2 = r2.getTextString(r3);
        r0.<init>(r2);
        r2 = mContentTypeMap;
        r0 = r2.get(r0);
        r0 = (java.lang.Integer) r0;
        if (r0 != 0) goto L_0x0024;
    L_0x0022:
        r0 = 1;
    L_0x0023:
        return r0;
    L_0x0024:
        r0 = r0.intValue();
        r13.appendShortInteger(r0);
        r0 = 132; // 0x84 float:1.85E-43 double:6.5E-322;
        if (r14 != r0) goto L_0x0051;
    L_0x002f:
        r0 = r13.mPdu;
        r0 = (com.google.android.mms.pdu.RetrieveConf) r0;
        r0 = r0.getBody();
        r6 = r0;
    L_0x0038:
        if (r6 == 0) goto L_0x0040;
    L_0x003a:
        r0 = r6.getPartsNum();
        if (r0 != 0) goto L_0x005b;
    L_0x0040:
        r0 = 0;
        r13.appendUintvarInteger(r0);
        r0 = r13.mStack;
        r0.pop();
        r0 = r13.mStack;
        r0.copy();
        r0 = 0;
        goto L_0x0023;
    L_0x0051:
        r0 = r13.mPdu;
        r0 = (com.google.android.mms.pdu.SendReq) r0;
        r0 = r0.getBody();
        r6 = r0;
        goto L_0x0038;
    L_0x005b:
        r0 = 0;
        r0 = r6.getPart(r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        r2 = r0.getContentId();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        if (r2 == 0) goto L_0x007e;
    L_0x0066:
        r3 = 138; // 0x8a float:1.93E-43 double:6.8E-322;
        r13.appendOctet(r3);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        r3 = 60;
        r4 = 0;
        r4 = r2[r4];
        if (r3 != r4) goto L_0x00cb;
    L_0x0072:
        r3 = 62;
        r4 = r2.length;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        r4 = r4 + -1;
        r4 = r2[r4];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        if (r3 != r4) goto L_0x00cb;
    L_0x007b:
        r13.appendTextString(r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
    L_0x007e:
        r2 = 137; // 0x89 float:1.92E-43 double:6.77E-322;
        r13.appendOctet(r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        r0 = r0.getContentType();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        r13.appendTextString(r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
    L_0x008a:
        r0 = r1.getLength();
        r1 = r13.mStack;
        r1.pop();
        r0 = (long) r0;
        r13.appendValueLength(r0);
        r0 = r13.mStack;
        r0.copy();
        r7 = r6.getPartsNum();
        r0 = (long) r7;
        r13.appendUintvarInteger(r0);
        r0 = 0;
        r5 = r0;
    L_0x00a6:
        if (r5 >= r7) goto L_0x0234;
    L_0x00a8:
        r8 = r6.getPart(r5);
        r0 = r13.mStack;
        r0.newbuf();
        r0 = r13.mStack;
        r9 = r0.mark();
        r0 = r13.mStack;
        r0.newbuf();
        r0 = r13.mStack;
        r1 = r0.mark();
        r2 = r8.getContentType();
        if (r2 != 0) goto L_0x00f2;
    L_0x00c8:
        r0 = 1;
        goto L_0x0023;
    L_0x00cb:
        r3 = new java.lang.StringBuilder;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        r3.<init>();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        r4 = "<";
        r3 = r3.append(r4);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        r4 = new java.lang.String;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        r4.<init>(r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        r2 = r3.append(r4);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        r3 = ">";
        r2 = r2.append(r3);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        r2 = r2.toString();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        r13.appendTextString(r2);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x00ed }
        goto L_0x007e;
    L_0x00ed:
        r0 = move-exception;
        r0.printStackTrace();
        goto L_0x008a;
    L_0x00f2:
        r0 = mContentTypeMap;
        r3 = new java.lang.String;
        r3.<init>(r2);
        r0 = r0.get(r3);
        r0 = (java.lang.Integer) r0;
        if (r0 != 0) goto L_0x011f;
    L_0x0101:
        r13.appendTextString(r2);
    L_0x0104:
        r0 = r8.getName();
        if (r0 != 0) goto L_0x0127;
    L_0x010a:
        r0 = r8.getFilename();
        if (r0 != 0) goto L_0x0127;
    L_0x0110:
        r0 = r8.getContentLocation();
        if (r0 != 0) goto L_0x0127;
    L_0x0116:
        r0 = r8.getContentId();
        if (r0 != 0) goto L_0x0127;
    L_0x011c:
        r0 = 1;
        goto L_0x0023;
    L_0x011f:
        r0 = r0.intValue();
        r13.appendShortInteger(r0);
        goto L_0x0104;
    L_0x0127:
        r2 = 133; // 0x85 float:1.86E-43 double:6.57E-322;
        r13.appendOctet(r2);
        r13.appendTextString(r0);
        r0 = r8.getCharset();
        if (r0 == 0) goto L_0x013d;
    L_0x0135:
        r2 = 129; // 0x81 float:1.81E-43 double:6.37E-322;
        r13.appendOctet(r2);
        r13.appendShortInteger(r0);
    L_0x013d:
        r0 = r1.getLength();
        r1 = r13.mStack;
        r1.pop();
        r0 = (long) r0;
        r13.appendValueLength(r0);
        r0 = r13.mStack;
        r0.copy();
        r0 = r8.getContentId();
        if (r0 == 0) goto L_0x016d;
    L_0x0155:
        r1 = 192; // 0xc0 float:2.69E-43 double:9.5E-322;
        r13.appendOctet(r1);
        r1 = 60;
        r2 = 0;
        r2 = r0[r2];
        if (r1 != r2) goto L_0x019b;
    L_0x0161:
        r1 = 62;
        r2 = r0.length;
        r2 = r2 + -1;
        r2 = r0[r2];
        if (r1 != r2) goto L_0x019b;
    L_0x016a:
        r13.appendQuotedString(r0);
    L_0x016d:
        r0 = r8.getContentLocation();
        if (r0 == 0) goto L_0x017b;
    L_0x0173:
        r1 = 142; // 0x8e float:1.99E-43 double:7.0E-322;
        r13.appendOctet(r1);
        r13.appendTextString(r0);
    L_0x017b:
        r10 = r9.getLength();
        r0 = 0;
        r1 = r8.getData();
        if (r1 == 0) goto L_0x01bd;
    L_0x0186:
        r0 = 0;
        r2 = r1.length;
        r13.arraycopy(r1, r0, r2);
        r0 = r1.length;
    L_0x018c:
        r1 = r9.getLength();
        r1 = r1 - r10;
        if (r0 == r1) goto L_0x021d;
    L_0x0193:
        r0 = new java.lang.RuntimeException;
        r1 = "BUG: Length sanity check failed";
        r0.<init>(r1);
        throw r0;
    L_0x019b:
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "<";
        r1 = r1.append(r2);
        r2 = new java.lang.String;
        r2.<init>(r0);
        r0 = r1.append(r2);
        r1 = ">";
        r0 = r0.append(r1);
        r0 = r0.toString();
        r13.appendQuotedString(r0);
        goto L_0x016d;
    L_0x01bd:
        r2 = 0;
        r3 = 0;
        r4 = 0;
        r1 = 0;
        r11 = 1024; // 0x400 float:1.435E-42 double:5.06E-321;
        r11 = new byte[r11];	 Catch:{ FileNotFoundException -> 0x01eb, IOException -> 0x01f9, RuntimeException -> 0x0207, all -> 0x0215 }
        r12 = r13.mResolver;	 Catch:{ FileNotFoundException -> 0x01eb, IOException -> 0x01f9, RuntimeException -> 0x0207, all -> 0x0215 }
        r8 = r8.getDataUri();	 Catch:{ FileNotFoundException -> 0x01eb, IOException -> 0x01f9, RuntimeException -> 0x0207, all -> 0x0215 }
        r1 = r12.openInputStream(r8);	 Catch:{ FileNotFoundException -> 0x01eb, IOException -> 0x01f9, RuntimeException -> 0x0207, all -> 0x0215 }
    L_0x01cf:
        r2 = r1.read(r11);	 Catch:{ FileNotFoundException -> 0x023f, IOException -> 0x023d, RuntimeException -> 0x023b, all -> 0x0239 }
        r3 = -1;
        if (r2 == r3) goto L_0x01e3;
    L_0x01d6:
        r3 = r13.mMessage;	 Catch:{ FileNotFoundException -> 0x023f, IOException -> 0x023d, RuntimeException -> 0x023b, all -> 0x0239 }
        r4 = 0;
        r3.write(r11, r4, r2);	 Catch:{ FileNotFoundException -> 0x023f, IOException -> 0x023d, RuntimeException -> 0x023b, all -> 0x0239 }
        r3 = r13.mPosition;	 Catch:{ FileNotFoundException -> 0x023f, IOException -> 0x023d, RuntimeException -> 0x023b, all -> 0x0239 }
        r3 = r3 + r2;
        r13.mPosition = r3;	 Catch:{ FileNotFoundException -> 0x023f, IOException -> 0x023d, RuntimeException -> 0x023b, all -> 0x0239 }
        r0 = r0 + r2;
        goto L_0x01cf;
    L_0x01e3:
        if (r1 == 0) goto L_0x018c;
    L_0x01e5:
        r1.close();	 Catch:{ IOException -> 0x01e9 }
        goto L_0x018c;
    L_0x01e9:
        r1 = move-exception;
        goto L_0x018c;
    L_0x01eb:
        r0 = move-exception;
        r0 = r1;
    L_0x01ed:
        if (r0 == 0) goto L_0x0022;
    L_0x01ef:
        r0.close();	 Catch:{ IOException -> 0x01f5 }
        r0 = 1;
        goto L_0x0023;
    L_0x01f5:
        r0 = move-exception;
        r0 = 1;
        goto L_0x0023;
    L_0x01f9:
        r0 = move-exception;
        r1 = r2;
    L_0x01fb:
        if (r1 == 0) goto L_0x0022;
    L_0x01fd:
        r1.close();	 Catch:{ IOException -> 0x0203 }
        r0 = 1;
        goto L_0x0023;
    L_0x0203:
        r0 = move-exception;
        r0 = 1;
        goto L_0x0023;
    L_0x0207:
        r0 = move-exception;
        r1 = r3;
    L_0x0209:
        if (r1 == 0) goto L_0x0022;
    L_0x020b:
        r1.close();	 Catch:{ IOException -> 0x0211 }
        r0 = 1;
        goto L_0x0023;
    L_0x0211:
        r0 = move-exception;
        r0 = 1;
        goto L_0x0023;
    L_0x0215:
        r0 = move-exception;
        r1 = r4;
    L_0x0217:
        if (r1 == 0) goto L_0x021c;
    L_0x0219:
        r1.close();	 Catch:{ IOException -> 0x0237 }
    L_0x021c:
        throw r0;
    L_0x021d:
        r1 = r13.mStack;
        r1.pop();
        r2 = (long) r10;
        r13.appendUintvarInteger(r2);
        r0 = (long) r0;
        r13.appendUintvarInteger(r0);
        r0 = r13.mStack;
        r0.copy();
        r0 = r5 + 1;
        r5 = r0;
        goto L_0x00a6;
    L_0x0234:
        r0 = 0;
        goto L_0x0023;
    L_0x0237:
        r1 = move-exception;
        goto L_0x021c;
    L_0x0239:
        r0 = move-exception;
        goto L_0x0217;
    L_0x023b:
        r0 = move-exception;
        goto L_0x0209;
    L_0x023d:
        r0 = move-exception;
        goto L_0x01fb;
    L_0x023f:
        r0 = move-exception;
        r0 = r1;
        goto L_0x01ed;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.android.mms.pdu.PduComposer.makeMessageBody(int):int");
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

    private int makeReadRecInd() {
        if (this.mMessage == null) {
            this.mMessage = new ByteArrayOutputStream();
            this.mPosition = 0;
        }
        appendOctet(140);
        appendOctet(135);
        if (appendHeader(141) == 0 && appendHeader(139) == 0 && appendHeader(151) == 0 && appendHeader(137) == 0) {
            appendHeader(133);
            if (appendHeader(155) == 0) {
                return 0;
            }
        }
        return 1;
    }

    private int makeSendRetrievePdu(int i) {
        int i2 = 0;
        if (this.mMessage == null) {
            this.mMessage = new ByteArrayOutputStream();
            this.mPosition = 0;
        }
        appendOctet(140);
        appendOctet(i);
        appendOctet(152);
        byte[] textString = this.mPduHeader.getTextString(152);
        if (textString == null) {
            throw new IllegalArgumentException("Transaction-ID is null.");
        }
        appendTextString(textString);
        if (appendHeader(141) != 0) {
            return 1;
        }
        appendHeader(133);
        if (appendHeader(137) != 0) {
            return 1;
        }
        if (appendHeader(151) != 1) {
            i2 = 1;
        }
        if (appendHeader(130) != 1) {
            i2 = 1;
        }
        if (appendHeader(129) != 1) {
            i2 = 1;
        }
        if (i2 == 0) {
            return 1;
        }
        appendHeader(150);
        appendHeader(138);
        appendHeader(136);
        appendHeader(143);
        appendHeader(134);
        appendHeader(144);
        if (i == 132) {
            appendHeader(153);
            appendHeader(154);
        }
        appendOctet(132);
        return makeMessageBody(i);
    }

    /* Access modifiers changed, original: protected */
    public void append(int i) {
        this.mMessage.write(i);
        this.mPosition++;
    }

    /* Access modifiers changed, original: protected */
    public void appendDateValue(long j) {
        appendLongInteger(j);
    }

    /* Access modifiers changed, original: protected */
    public void appendEncodedString(EncodedStringValue encodedStringValue) {
        if ($assertionsDisabled || encodedStringValue != null) {
            int characterSet = encodedStringValue.getCharacterSet();
            byte[] textString = encodedStringValue.getTextString();
            if (textString != null) {
                this.mStack.newbuf();
                PositionMarker mark = this.mStack.mark();
                appendShortInteger(characterSet);
                appendTextString(textString);
                characterSet = mark.getLength();
                this.mStack.pop();
                appendValueLength((long) characterSet);
                this.mStack.copy();
                return;
            }
            return;
        }
        throw new AssertionError();
    }

    /* Access modifiers changed, original: protected */
    public void appendLongInteger(long j) {
        int i = 0;
        long j2 = j;
        int i2 = 0;
        while (j2 != 0 && i2 < 8) {
            j2 >>>= 8;
            i2++;
        }
        appendShortLength(i2);
        long j3 = (i2 - 1) * 8;
        while (i < i2) {
            append((int) ((j >>> j3) & 255));
            j3 -= 8;
            i++;
        }
    }

    /* Access modifiers changed, original: protected */
    public void appendOctet(int i) {
        append(i);
    }

    /* Access modifiers changed, original: protected */
    public void appendQuotedString(String str) {
        appendQuotedString(str.getBytes());
    }

    /* Access modifiers changed, original: protected */
    public void appendQuotedString(byte[] bArr) {
        append(34);
        arraycopy(bArr, 0, bArr.length);
        append(0);
    }

    /* Access modifiers changed, original: protected */
    public void appendShortInteger(int i) {
        append((i | 128) & 255);
    }

    /* Access modifiers changed, original: protected */
    public void appendShortLength(int i) {
        append(i);
    }

    /* Access modifiers changed, original: protected */
    public void appendTextString(String str) {
        appendTextString(str.getBytes());
    }

    /* Access modifiers changed, original: protected */
    public void appendTextString(byte[] bArr) {
        if ((bArr[0] & 255) > 127) {
            append(127);
        }
        arraycopy(bArr, 0, bArr.length);
        append(0);
    }

    /* Access modifiers changed, original: protected */
    public void appendUintvarInteger(long j) {
        int i = 0;
        long j2 = 127;
        while (i < 5 && j >= j2) {
            j2 = (j2 << 7) | 127;
            i++;
        }
        while (i > 0) {
            append((int) ((128 | ((j >>> (i * 7)) & 127)) & 255));
            i--;
        }
        append((int) (j & 127));
    }

    /* Access modifiers changed, original: protected */
    public void appendValueLength(long j) {
        if (j < 31) {
            appendShortLength((int) j);
            return;
        }
        append(31);
        appendUintvarInteger(j);
    }

    /* Access modifiers changed, original: protected */
    public void arraycopy(byte[] bArr, int i, int i2) {
        this.mMessage.write(bArr, i, i2);
        this.mPosition += i2;
    }

    public byte[] make() {
        int messageType = this.mPdu.getMessageType();
        switch (messageType) {
            case 128:
            case 132:
                if (makeSendRetrievePdu(messageType) != 0) {
                    return null;
                }
                break;
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
            default:
                return null;
        }
        return this.mMessage.toByteArray();
    }
}
