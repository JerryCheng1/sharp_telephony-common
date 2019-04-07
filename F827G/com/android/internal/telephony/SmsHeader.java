package com.android.internal.telephony;

import com.android.internal.util.HexDump;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;

public class SmsHeader {
    public static final int ELT_ID_APPLICATION_PORT_ADDRESSING_16_BIT = 5;
    public static final int ELT_ID_APPLICATION_PORT_ADDRESSING_8_BIT = 4;
    public static final int ELT_ID_CHARACTER_SIZE_WVG_OBJECT = 25;
    public static final int ELT_ID_COMPRESSION_CONTROL = 22;
    public static final int ELT_ID_CONCATENATED_16_BIT_REFERENCE = 8;
    public static final int ELT_ID_CONCATENATED_8_BIT_REFERENCE = 0;
    public static final int ELT_ID_ENHANCED_VOICE_MAIL_INFORMATION = 35;
    public static final int ELT_ID_EXTENDED_OBJECT = 20;
    public static final int ELT_ID_EXTENDED_OBJECT_DATA_REQUEST_CMD = 26;
    public static final int ELT_ID_HYPERLINK_FORMAT_ELEMENT = 33;
    public static final int ELT_ID_LARGE_ANIMATION = 14;
    public static final int ELT_ID_LARGE_PICTURE = 16;
    public static final int ELT_ID_NATIONAL_LANGUAGE_LOCKING_SHIFT = 37;
    public static final int ELT_ID_NATIONAL_LANGUAGE_SINGLE_SHIFT = 36;
    public static final int ELT_ID_OBJECT_DISTR_INDICATOR = 23;
    public static final int ELT_ID_PREDEFINED_ANIMATION = 13;
    public static final int ELT_ID_PREDEFINED_SOUND = 11;
    public static final int ELT_ID_REPLY_ADDRESS_ELEMENT = 34;
    public static final int ELT_ID_REUSED_EXTENDED_OBJECT = 21;
    public static final int ELT_ID_RFC_822_EMAIL_HEADER = 32;
    public static final int ELT_ID_SMALL_ANIMATION = 15;
    public static final int ELT_ID_SMALL_PICTURE = 17;
    public static final int ELT_ID_SMSC_CONTROL_PARAMS = 6;
    public static final int ELT_ID_SPECIAL_SMS_MESSAGE_INDICATION = 1;
    public static final int ELT_ID_STANDARD_WVG_OBJECT = 24;
    public static final int ELT_ID_TEXT_FORMATTING = 10;
    public static final int ELT_ID_UDH_SOURCE_INDICATION = 7;
    public static final int ELT_ID_USER_DEFINED_SOUND = 12;
    public static final int ELT_ID_USER_PROMPT_INDICATOR = 19;
    public static final int ELT_ID_VARIABLE_PICTURE = 18;
    public static final int ELT_ID_WIRELESS_CTRL_MSG_PROTOCOL = 9;
    public static final int PORT_WAP_PUSH = 2948;
    public static final int PORT_WAP_WSP = 9200;
    public ConcatRef concatRef;
    public int languageShiftTable;
    public int languageTable;
    public ArrayList<MiscElt> miscEltList = new ArrayList();
    public PortAddrs portAddrs;
    public ArrayList<SpecialSmsMsg> specialSmsMsgList = new ArrayList();

    public static class ConcatRef {
        public boolean isEightBits;
        public int msgCount;
        public int refNumber;
        public int seqNumber;
    }

    public static class MiscElt {
        public byte[] data;
        public int id;
    }

    public static class PortAddrs {
        public boolean areEightBits;
        public int destPort;
        public int origPort;
    }

    public static class SpecialSmsMsg {
        public int msgCount;
        public int msgIndType;
    }

    public static SmsHeader fromByteArray(byte[] bArr) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bArr);
        SmsHeader smsHeader = new SmsHeader();
        while (byteArrayInputStream.available() > 0) {
            int read = byteArrayInputStream.read();
            int read2 = byteArrayInputStream.read();
            ConcatRef concatRef;
            PortAddrs portAddrs;
            switch (read) {
                case 0:
                    concatRef = new ConcatRef();
                    concatRef.refNumber = byteArrayInputStream.read();
                    concatRef.msgCount = byteArrayInputStream.read();
                    concatRef.seqNumber = byteArrayInputStream.read();
                    concatRef.isEightBits = true;
                    if (!(concatRef.msgCount == 0 || concatRef.seqNumber == 0 || concatRef.seqNumber > concatRef.msgCount)) {
                        smsHeader.concatRef = concatRef;
                        break;
                    }
                case 1:
                    SpecialSmsMsg specialSmsMsg = new SpecialSmsMsg();
                    specialSmsMsg.msgIndType = byteArrayInputStream.read();
                    specialSmsMsg.msgCount = byteArrayInputStream.read();
                    smsHeader.specialSmsMsgList.add(specialSmsMsg);
                    break;
                case 4:
                    portAddrs = new PortAddrs();
                    portAddrs.destPort = byteArrayInputStream.read();
                    portAddrs.origPort = byteArrayInputStream.read();
                    portAddrs.areEightBits = true;
                    smsHeader.portAddrs = portAddrs;
                    break;
                case 5:
                    portAddrs = new PortAddrs();
                    portAddrs.destPort = (byteArrayInputStream.read() << 8) | byteArrayInputStream.read();
                    portAddrs.origPort = (byteArrayInputStream.read() << 8) | byteArrayInputStream.read();
                    portAddrs.areEightBits = false;
                    smsHeader.portAddrs = portAddrs;
                    break;
                case 8:
                    concatRef = new ConcatRef();
                    concatRef.refNumber = (byteArrayInputStream.read() << 8) | byteArrayInputStream.read();
                    concatRef.msgCount = byteArrayInputStream.read();
                    concatRef.seqNumber = byteArrayInputStream.read();
                    concatRef.isEightBits = false;
                    if (!(concatRef.msgCount == 0 || concatRef.seqNumber == 0 || concatRef.seqNumber > concatRef.msgCount)) {
                        smsHeader.concatRef = concatRef;
                        break;
                    }
                case 36:
                    smsHeader.languageShiftTable = byteArrayInputStream.read();
                    break;
                case 37:
                    smsHeader.languageTable = byteArrayInputStream.read();
                    break;
                default:
                    MiscElt miscElt = new MiscElt();
                    miscElt.id = read;
                    miscElt.data = new byte[read2];
                    byteArrayInputStream.read(miscElt.data, 0, read2);
                    smsHeader.miscEltList.add(miscElt);
                    break;
            }
        }
        return smsHeader;
    }

    public static byte[] toByteArray(SmsHeader smsHeader) {
        if (smsHeader.portAddrs == null && smsHeader.concatRef == null && smsHeader.specialSmsMsgList.isEmpty() && smsHeader.miscEltList.isEmpty() && smsHeader.languageShiftTable == 0 && smsHeader.languageTable == 0) {
            return null;
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(140);
        ConcatRef concatRef = smsHeader.concatRef;
        if (concatRef != null) {
            if (concatRef.isEightBits) {
                byteArrayOutputStream.write(0);
                byteArrayOutputStream.write(3);
                byteArrayOutputStream.write(concatRef.refNumber);
            } else {
                byteArrayOutputStream.write(8);
                byteArrayOutputStream.write(4);
                byteArrayOutputStream.write(concatRef.refNumber >>> 8);
                byteArrayOutputStream.write(concatRef.refNumber & 255);
            }
            byteArrayOutputStream.write(concatRef.msgCount);
            byteArrayOutputStream.write(concatRef.seqNumber);
        }
        PortAddrs portAddrs = smsHeader.portAddrs;
        if (portAddrs != null) {
            if (portAddrs.areEightBits) {
                byteArrayOutputStream.write(4);
                byteArrayOutputStream.write(2);
                byteArrayOutputStream.write(portAddrs.destPort);
                byteArrayOutputStream.write(portAddrs.origPort);
            } else {
                byteArrayOutputStream.write(5);
                byteArrayOutputStream.write(4);
                byteArrayOutputStream.write(portAddrs.destPort >>> 8);
                byteArrayOutputStream.write(portAddrs.destPort & 255);
                byteArrayOutputStream.write(portAddrs.origPort >>> 8);
                byteArrayOutputStream.write(portAddrs.origPort & 255);
            }
        }
        if (smsHeader.languageShiftTable != 0) {
            byteArrayOutputStream.write(36);
            byteArrayOutputStream.write(1);
            byteArrayOutputStream.write(smsHeader.languageShiftTable);
        }
        if (smsHeader.languageTable != 0) {
            byteArrayOutputStream.write(37);
            byteArrayOutputStream.write(1);
            byteArrayOutputStream.write(smsHeader.languageTable);
        }
        Iterator it = smsHeader.specialSmsMsgList.iterator();
        while (it.hasNext()) {
            SpecialSmsMsg specialSmsMsg = (SpecialSmsMsg) it.next();
            byteArrayOutputStream.write(1);
            byteArrayOutputStream.write(2);
            byteArrayOutputStream.write(specialSmsMsg.msgIndType & 255);
            byteArrayOutputStream.write(specialSmsMsg.msgCount & 255);
        }
        it = smsHeader.miscEltList.iterator();
        while (it.hasNext()) {
            MiscElt miscElt = (MiscElt) it.next();
            byteArrayOutputStream.write(miscElt.id);
            byteArrayOutputStream.write(miscElt.data.length);
            byteArrayOutputStream.write(miscElt.data, 0, miscElt.data.length);
        }
        return byteArrayOutputStream.toByteArray();
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("UserDataHeader ");
        stringBuilder.append("{ ConcatRef ");
        if (this.concatRef == null) {
            stringBuilder.append("unset");
        } else {
            stringBuilder.append("{ refNumber=" + this.concatRef.refNumber);
            stringBuilder.append(", msgCount=" + this.concatRef.msgCount);
            stringBuilder.append(", seqNumber=" + this.concatRef.seqNumber);
            stringBuilder.append(", isEightBits=" + this.concatRef.isEightBits);
            stringBuilder.append(" }");
        }
        stringBuilder.append(", PortAddrs ");
        if (this.portAddrs == null) {
            stringBuilder.append("unset");
        } else {
            stringBuilder.append("{ destPort=" + this.portAddrs.destPort);
            stringBuilder.append(", origPort=" + this.portAddrs.origPort);
            stringBuilder.append(", areEightBits=" + this.portAddrs.areEightBits);
            stringBuilder.append(" }");
        }
        if (this.languageShiftTable != 0) {
            stringBuilder.append(", languageShiftTable=" + this.languageShiftTable);
        }
        if (this.languageTable != 0) {
            stringBuilder.append(", languageTable=" + this.languageTable);
        }
        Iterator it = this.specialSmsMsgList.iterator();
        while (it.hasNext()) {
            SpecialSmsMsg specialSmsMsg = (SpecialSmsMsg) it.next();
            stringBuilder.append(", SpecialSmsMsg ");
            stringBuilder.append("{ msgIndType=" + specialSmsMsg.msgIndType);
            stringBuilder.append(", msgCount=" + specialSmsMsg.msgCount);
            stringBuilder.append(" }");
        }
        it = this.miscEltList.iterator();
        while (it.hasNext()) {
            MiscElt miscElt = (MiscElt) it.next();
            stringBuilder.append(", MiscElt ");
            stringBuilder.append("{ id=" + miscElt.id);
            stringBuilder.append(", length=" + miscElt.data.length);
            stringBuilder.append(", data=" + HexDump.toHexString(miscElt.data));
            stringBuilder.append(" }");
        }
        stringBuilder.append(" }");
        return stringBuilder.toString();
    }
}
