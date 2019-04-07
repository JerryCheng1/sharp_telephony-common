package com.android.internal.telephony.cdma;

import android.content.res.Resources;
import android.os.Parcel;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.cdma.CdmaSmsCbProgramData;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.Sms7BitEncodingTranslator;
import com.android.internal.telephony.SmsConstants.MessageClass;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsHeader.PortAddrs;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsMessageBase.SubmitPduBase;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.cdma.sms.CdmaSmsAddress;
import com.android.internal.telephony.cdma.sms.CdmaSmsSubaddress;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.cdma.sms.UserData;
import com.android.internal.util.BitwiseInputStream;
import com.android.internal.util.BitwiseInputStream.AccessException;
import com.android.internal.util.HexDump;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class SmsMessage extends SmsMessageBase {
    private static final byte BEARER_DATA = (byte) 8;
    private static final byte BEARER_REPLY_OPTION = (byte) 6;
    private static final byte CAUSE_CODES = (byte) 7;
    private static final byte DESTINATION_ADDRESS = (byte) 4;
    private static final byte DESTINATION_SUB_ADDRESS = (byte) 5;
    private static final String LOGGABLE_TAG = "CDMA:SMS";
    static final String LOG_TAG = "SmsMessage";
    private static final byte ORIGINATING_ADDRESS = (byte) 2;
    private static final byte ORIGINATING_SUB_ADDRESS = (byte) 3;
    private static final int PRIORITY_EMERGENCY = 3;
    private static final int PRIORITY_INTERACTIVE = 1;
    private static final int PRIORITY_NORMAL = 0;
    private static final int PRIORITY_URGENT = 2;
    private static final int RETURN_ACK = 1;
    private static final int RETURN_NO_ACK = 0;
    private static final byte SERVICE_CATEGORY = (byte) 1;
    private static final byte TELESERVICE_IDENTIFIER = (byte) 0;
    private static final boolean VDBG = false;
    private BearerData mBearerData;
    private SmsEnvelope mEnvelope;
    private int status;

    public static class SubmitPdu extends SubmitPduBase {
    }

    public static TextEncodingDetails calculateLength(CharSequence charSequence, boolean z) {
        CharSequence charSequence2 = null;
        if (Resources.getSystem().getBoolean(17957010)) {
            charSequence2 = Sms7BitEncodingTranslator.translate(charSequence);
        }
        if (!TextUtils.isEmpty(charSequence2)) {
            charSequence = charSequence2;
        }
        return BearerData.calcTextEncodingDetails(charSequence, z);
    }

    private byte convertDtmfToAscii(byte b) {
        switch (b) {
            case (byte) 0:
                return (byte) 68;
            case (byte) 1:
                return (byte) 49;
            case (byte) 2:
                return (byte) 50;
            case (byte) 3:
                return (byte) 51;
            case (byte) 4:
                return (byte) 52;
            case (byte) 5:
                return (byte) 53;
            case (byte) 6:
                return (byte) 54;
            case (byte) 7:
                return (byte) 55;
            case (byte) 8:
                return (byte) 56;
            case (byte) 9:
                return (byte) 57;
            case (byte) 10:
                return (byte) 48;
            case (byte) 11:
                return (byte) 42;
            case (byte) 12:
                return (byte) 35;
            case (byte) 13:
                return (byte) 65;
            case (byte) 14:
                return (byte) 66;
            case (byte) 15:
                return (byte) 67;
            default:
                return (byte) 32;
        }
    }

    public static SmsMessage createFromEfRecord(int i, byte[] bArr) {
        try {
            SmsMessage smsMessage = new SmsMessage();
            smsMessage.mIndexOnIcc = i;
            if ((bArr[0] & 1) == 0) {
                Rlog.w(LOG_TAG, "SMS parsing failed: Trying to parse a free record");
                return null;
            }
            smsMessage.mStatusOnIcc = bArr[0] & 7;
            int i2 = bArr[1] & 255;
            byte[] bArr2 = new byte[i2];
            System.arraycopy(bArr, 2, bArr2, 0, i2);
            smsMessage.parsePduFromEfRecord(bArr2);
            return smsMessage;
        } catch (RuntimeException e) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", e);
            return null;
        }
    }

    public static SmsMessage createFromPdu(byte[] bArr) {
        SmsMessage smsMessage = new SmsMessage();
        try {
            smsMessage.parsePdu(bArr);
            return smsMessage;
        } catch (RuntimeException e) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", e);
            return null;
        } catch (OutOfMemoryError e2) {
            Log.e(LOG_TAG, "SMS PDU parsing failed with out of memory: ", e2);
            return null;
        }
    }

    private void createPdu() {
        SmsEnvelope smsEnvelope = this.mEnvelope;
        CdmaSmsAddress cdmaSmsAddress = smsEnvelope.origAddress;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(100);
        DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(byteArrayOutputStream));
        try {
            dataOutputStream.writeInt(smsEnvelope.messageType);
            dataOutputStream.writeInt(smsEnvelope.teleService);
            dataOutputStream.writeInt(smsEnvelope.serviceCategory);
            dataOutputStream.writeByte(cdmaSmsAddress.digitMode);
            dataOutputStream.writeByte(cdmaSmsAddress.numberMode);
            dataOutputStream.writeByte(cdmaSmsAddress.ton);
            dataOutputStream.writeByte(cdmaSmsAddress.numberPlan);
            dataOutputStream.writeByte(cdmaSmsAddress.numberOfDigits);
            dataOutputStream.write(cdmaSmsAddress.origBytes, 0, cdmaSmsAddress.origBytes.length);
            dataOutputStream.writeInt(smsEnvelope.bearerReply);
            dataOutputStream.writeByte(smsEnvelope.replySeqNo);
            dataOutputStream.writeByte(smsEnvelope.errorClass);
            dataOutputStream.writeByte(smsEnvelope.causeCode);
            dataOutputStream.writeInt(smsEnvelope.bearerData.length);
            dataOutputStream.write(smsEnvelope.bearerData, 0, smsEnvelope.bearerData.length);
            dataOutputStream.close();
            this.mPdu = byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            Rlog.e(LOG_TAG, "createPdu: conversion from object to byte array failed: " + e);
        }
    }

    static int getNextMessageId() {
        int i;
        synchronized (SmsMessage.class) {
            try {
                i = SystemProperties.getInt("persist.radio.cdma.msgid", 1);
                String num = Integer.toString((i % 65535) + 1);
                SystemProperties.set("persist.radio.cdma.msgid", num);
                if (Rlog.isLoggable(LOGGABLE_TAG, 2)) {
                    Rlog.d(LOG_TAG, "next persist.radio.cdma.msgid = " + num);
                    Rlog.d(LOG_TAG, "readback gets " + SystemProperties.get("persist.radio.cdma.msgid"));
                }
            } catch (Throwable th) {
                Class cls = SmsMessage.class;
            }
        }
        return i;
    }

    public static SubmitPdu getSubmitPdu(String str, UserData userData, boolean z) {
        return privateGetSubmitPdu(str, z, userData);
    }

    public static SubmitPdu getSubmitPdu(String str, UserData userData, boolean z, int i) {
        return privateGetSubmitPdu(str, z, userData, i);
    }

    public static SubmitPdu getSubmitPdu(String str, String str2, int i, int i2, byte[] bArr, boolean z) {
        PortAddrs portAddrs = new PortAddrs();
        portAddrs.destPort = i;
        portAddrs.origPort = i2;
        portAddrs.areEightBits = false;
        SmsHeader smsHeader = new SmsHeader();
        smsHeader.portAddrs = portAddrs;
        UserData userData = new UserData();
        userData.userDataHeader = smsHeader;
        userData.msgEncoding = 0;
        userData.msgEncodingSet = true;
        userData.payload = bArr;
        return privateGetSubmitPdu(str2, z, userData);
    }

    public static SubmitPdu getSubmitPdu(String str, String str2, int i, byte[] bArr, boolean z) {
        return getSubmitPdu(str, str2, i, 0, bArr, z);
    }

    public static SubmitPdu getSubmitPdu(String str, String str2, String str3, boolean z, SmsHeader smsHeader) {
        return getSubmitPdu(str, str2, str3, z, smsHeader, -1);
    }

    public static SubmitPdu getSubmitPdu(String str, String str2, String str3, boolean z, SmsHeader smsHeader, int i) {
        if (str3 == null || str2 == null) {
            return null;
        }
        UserData userData = new UserData();
        userData.payloadStr = str3;
        userData.userDataHeader = smsHeader;
        return privateGetSubmitPdu(str2, z, userData, i);
    }

    public static int getTPLayerLengthForPDU(String str) {
        Rlog.w(LOG_TAG, "getTPLayerLengthForPDU: is not supported in CDMA mode.");
        return 0;
    }

    public static SmsMessage newFromParcel(Parcel parcel) {
        int i = 0;
        SmsMessage smsMessage = new SmsMessage();
        SmsEnvelope smsEnvelope = new SmsEnvelope();
        CdmaSmsAddress cdmaSmsAddress = new CdmaSmsAddress();
        CdmaSmsSubaddress cdmaSmsSubaddress = new CdmaSmsSubaddress();
        smsEnvelope.teleService = parcel.readInt();
        if (parcel.readByte() != (byte) 0) {
            smsEnvelope.messageType = 1;
        } else if (smsEnvelope.teleService == 0) {
            smsEnvelope.messageType = 2;
        } else {
            smsEnvelope.messageType = 0;
        }
        smsEnvelope.serviceCategory = parcel.readInt();
        int readInt = parcel.readInt();
        cdmaSmsAddress.digitMode = (byte) (readInt & 255);
        cdmaSmsAddress.numberMode = (byte) (parcel.readInt() & 255);
        cdmaSmsAddress.ton = parcel.readInt();
        cdmaSmsAddress.numberPlan = (byte) (parcel.readInt() & 255);
        byte readByte = parcel.readByte();
        cdmaSmsAddress.numberOfDigits = readByte;
        byte[] bArr = new byte[readByte];
        for (byte b = (byte) 0; b < readByte; b++) {
            bArr[b] = parcel.readByte();
            if (readInt == 0) {
                bArr[b] = smsMessage.convertDtmfToAscii(bArr[b]);
            }
        }
        cdmaSmsAddress.origBytes = bArr;
        cdmaSmsSubaddress.type = parcel.readInt();
        cdmaSmsSubaddress.odd = parcel.readByte();
        int readByte2 = parcel.readByte();
        if (readByte2 < 0) {
            readByte2 = 0;
        }
        byte[] bArr2 = new byte[readByte2];
        for (readInt = 0; readInt < readByte2; readInt++) {
            bArr2[readInt] = parcel.readByte();
        }
        cdmaSmsSubaddress.origBytes = bArr2;
        readByte2 = parcel.readInt();
        if (readByte2 < 0) {
            readByte2 = 0;
        }
        byte[] bArr3 = new byte[readByte2];
        while (i < readByte2) {
            bArr3[i] = parcel.readByte();
            i++;
        }
        smsEnvelope.bearerData = bArr3;
        smsEnvelope.origAddress = cdmaSmsAddress;
        smsEnvelope.origSubaddress = cdmaSmsSubaddress;
        smsMessage.mOriginatingAddress = cdmaSmsAddress;
        smsMessage.mEnvelope = smsEnvelope;
        smsMessage.createPdu();
        return smsMessage;
    }

    private void parsePdu(byte[] bArr) {
        DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(bArr));
        SmsEnvelope smsEnvelope = new SmsEnvelope();
        CdmaSmsAddress cdmaSmsAddress = new CdmaSmsAddress();
        try {
            smsEnvelope.messageType = dataInputStream.readInt();
            smsEnvelope.teleService = dataInputStream.readInt();
            smsEnvelope.serviceCategory = dataInputStream.readInt();
            cdmaSmsAddress.digitMode = dataInputStream.readByte();
            cdmaSmsAddress.numberMode = dataInputStream.readByte();
            cdmaSmsAddress.ton = dataInputStream.readByte();
            cdmaSmsAddress.numberPlan = dataInputStream.readByte();
            int readUnsignedByte = dataInputStream.readUnsignedByte();
            cdmaSmsAddress.numberOfDigits = readUnsignedByte;
            if (readUnsignedByte > bArr.length) {
                throw new RuntimeException("createFromPdu: Invalid pdu, addr.numberOfDigits " + readUnsignedByte + " > pdu len " + bArr.length);
            }
            cdmaSmsAddress.origBytes = new byte[readUnsignedByte];
            dataInputStream.read(cdmaSmsAddress.origBytes, 0, readUnsignedByte);
            smsEnvelope.bearerReply = dataInputStream.readInt();
            smsEnvelope.replySeqNo = dataInputStream.readByte();
            smsEnvelope.errorClass = dataInputStream.readByte();
            smsEnvelope.causeCode = dataInputStream.readByte();
            readUnsignedByte = dataInputStream.readInt();
            if (readUnsignedByte > bArr.length) {
                throw new RuntimeException("createFromPdu: Invalid pdu, bearerDataLength " + readUnsignedByte + " > pdu len " + bArr.length);
            }
            smsEnvelope.bearerData = new byte[readUnsignedByte];
            dataInputStream.read(smsEnvelope.bearerData, 0, readUnsignedByte);
            dataInputStream.close();
            this.mOriginatingAddress = cdmaSmsAddress;
            smsEnvelope.origAddress = cdmaSmsAddress;
            this.mEnvelope = smsEnvelope;
            this.mPdu = bArr;
            parseSms();
        } catch (IOException e) {
            throw new RuntimeException("createFromPdu: conversion from byte array to object failed: " + e, e);
        } catch (Exception e2) {
            Rlog.e(LOG_TAG, "createFromPdu: conversion from byte array to object failed: " + e2);
        }
    }

    private void parsePduFromEfRecord(byte[] bArr) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bArr);
        DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);
        SmsEnvelope smsEnvelope = new SmsEnvelope();
        CdmaSmsAddress cdmaSmsAddress = new CdmaSmsAddress();
        CdmaSmsSubaddress cdmaSmsSubaddress = new CdmaSmsSubaddress();
        try {
            smsEnvelope.messageType = dataInputStream.readByte();
            while (dataInputStream.available() > 0) {
                byte readByte = dataInputStream.readByte();
                int readUnsignedByte = dataInputStream.readUnsignedByte();
                byte[] bArr2 = new byte[readUnsignedByte];
                switch (readByte) {
                    case (byte) 0:
                        smsEnvelope.teleService = dataInputStream.readUnsignedShort();
                        Rlog.i(LOG_TAG, "teleservice = " + smsEnvelope.teleService);
                        break;
                    case (byte) 1:
                        smsEnvelope.serviceCategory = dataInputStream.readUnsignedShort();
                        break;
                    case (byte) 2:
                    case (byte) 4:
                        dataInputStream.read(bArr2, 0, readUnsignedByte);
                        BitwiseInputStream bitwiseInputStream = new BitwiseInputStream(bArr2);
                        cdmaSmsAddress.digitMode = bitwiseInputStream.read(1);
                        cdmaSmsAddress.numberMode = bitwiseInputStream.read(1);
                        if (cdmaSmsAddress.digitMode == 1) {
                            readUnsignedByte = bitwiseInputStream.read(3);
                            cdmaSmsAddress.ton = readUnsignedByte;
                            if (cdmaSmsAddress.numberMode == 0) {
                                cdmaSmsAddress.numberPlan = bitwiseInputStream.read(4);
                            }
                        } else {
                            readUnsignedByte = 0;
                        }
                        cdmaSmsAddress.numberOfDigits = bitwiseInputStream.read(8);
                        bArr2 = new byte[cdmaSmsAddress.numberOfDigits];
                        if (cdmaSmsAddress.digitMode == 0) {
                            for (readUnsignedByte = 0; readUnsignedByte < cdmaSmsAddress.numberOfDigits; readUnsignedByte++) {
                                bArr2[readUnsignedByte] = convertDtmfToAscii((byte) (bitwiseInputStream.read(4) & 15));
                            }
                        } else if (cdmaSmsAddress.digitMode != 1) {
                            Rlog.e(LOG_TAG, "Incorrect Digit mode");
                        } else if (cdmaSmsAddress.numberMode == 0) {
                            for (readUnsignedByte = 0; readUnsignedByte < cdmaSmsAddress.numberOfDigits; readUnsignedByte++) {
                                bArr2[readUnsignedByte] = (byte) (bitwiseInputStream.read(8) & 255);
                            }
                        } else if (cdmaSmsAddress.numberMode != 1) {
                            Rlog.e(LOG_TAG, "Originating Addr is of incorrect type");
                        } else if (readUnsignedByte == 2) {
                            Rlog.e(LOG_TAG, "TODO: Originating Addr is email id");
                        } else {
                            Rlog.e(LOG_TAG, "TODO: Originating Addr is data network address");
                        }
                        cdmaSmsAddress.origBytes = bArr2;
                        Rlog.i(LOG_TAG, "Originating Addr=" + cdmaSmsAddress.toString());
                        if (readByte != DESTINATION_ADDRESS) {
                            break;
                        }
                        smsEnvelope.destAddress = cdmaSmsAddress;
                        this.mRecipientAddress = cdmaSmsAddress;
                        break;
                    case (byte) 3:
                    case (byte) 5:
                        dataInputStream.read(bArr2, 0, readUnsignedByte);
                        BitwiseInputStream bitwiseInputStream2 = new BitwiseInputStream(bArr2);
                        cdmaSmsSubaddress.type = bitwiseInputStream2.read(3);
                        cdmaSmsSubaddress.odd = bitwiseInputStream2.readByteArray(1)[0];
                        int read = bitwiseInputStream2.read(8);
                        byte[] bArr3 = new byte[read];
                        for (readUnsignedByte = 0; readUnsignedByte < read; readUnsignedByte++) {
                            bArr3[readUnsignedByte] = convertDtmfToAscii((byte) (bitwiseInputStream2.read(4) & 255));
                        }
                        cdmaSmsSubaddress.origBytes = bArr3;
                        break;
                    case (byte) 6:
                        dataInputStream.read(bArr2, 0, readUnsignedByte);
                        smsEnvelope.bearerReply = new BitwiseInputStream(bArr2).read(6);
                        break;
                    case (byte) 7:
                        dataInputStream.read(bArr2, 0, readUnsignedByte);
                        BitwiseInputStream bitwiseInputStream3 = new BitwiseInputStream(bArr2);
                        smsEnvelope.replySeqNo = bitwiseInputStream3.readByteArray(6)[0];
                        smsEnvelope.errorClass = bitwiseInputStream3.readByteArray(2)[0];
                        if (smsEnvelope.errorClass == (byte) 0) {
                            break;
                        }
                        smsEnvelope.causeCode = bitwiseInputStream3.readByteArray(8)[0];
                        break;
                    case (byte) 8:
                        dataInputStream.read(bArr2, 0, readUnsignedByte);
                        smsEnvelope.bearerData = bArr2;
                        break;
                    default:
                        throw new Exception("unsupported parameterId (" + readByte + ")");
                }
            }
            byteArrayInputStream.close();
            dataInputStream.close();
        } catch (Exception e) {
            Rlog.e(LOG_TAG, "parsePduFromEfRecord: conversion from pdu to SmsMessage failed" + e);
        }
        this.mOriginatingAddress = cdmaSmsAddress;
        smsEnvelope.origAddress = cdmaSmsAddress;
        smsEnvelope.origSubaddress = cdmaSmsSubaddress;
        this.mEnvelope = smsEnvelope;
        this.mPdu = bArr;
        parseSms();
    }

    private static SubmitPdu privateGetSubmitPdu(String str, boolean z, UserData userData) {
        return privateGetSubmitPdu(str, z, userData, -1);
    }

    private static SubmitPdu privateGetSubmitPdu(String str, boolean z, UserData userData, int i) {
        CdmaSmsAddress parse = CdmaSmsAddress.parse(PhoneNumberUtils.cdmaCheckAndProcessPlusCodeForSms(str));
        if (parse == null) {
            return null;
        }
        BearerData bearerData = new BearerData();
        bearerData.messageType = 2;
        bearerData.messageId = getNextMessageId();
        bearerData.deliveryAckReq = z;
        bearerData.userAckReq = false;
        bearerData.readAckReq = false;
        bearerData.reportReq = false;
        if (i >= 0 && i <= 3) {
            bearerData.priorityIndicatorSet = true;
            bearerData.priority = i;
        }
        bearerData.userData = userData;
        byte[] encode = BearerData.encode(bearerData);
        if (Rlog.isLoggable(LOGGABLE_TAG, 2)) {
            Rlog.d(LOG_TAG, "MO (encoded) BearerData = " + bearerData);
            Rlog.d(LOG_TAG, "MO raw BearerData = '" + HexDump.toHexString(encode) + "'");
        }
        if (encode == null) {
            return null;
        }
        int i2 = bearerData.hasUserDataHeader ? SmsEnvelope.TELESERVICE_WEMT : 4098;
        Resources system = Resources.getSystem();
        if (system != null) {
            boolean z2 = system.getBoolean(17957011);
            if (z2) {
                Rlog.d(LOG_TAG, "ascii7bitForLongMsg = " + z2);
                i2 = 4098;
            }
        }
        SmsEnvelope smsEnvelope = new SmsEnvelope();
        smsEnvelope.messageType = 0;
        smsEnvelope.teleService = i2;
        smsEnvelope.destAddress = parse;
        smsEnvelope.bearerReply = 1;
        smsEnvelope.bearerData = encode;
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(100);
            DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
            dataOutputStream.writeInt(smsEnvelope.teleService);
            dataOutputStream.writeInt(0);
            dataOutputStream.writeInt(0);
            dataOutputStream.write(parse.digitMode);
            dataOutputStream.write(parse.numberMode);
            dataOutputStream.write(parse.ton);
            dataOutputStream.write(parse.numberPlan);
            dataOutputStream.write(parse.numberOfDigits);
            dataOutputStream.write(parse.origBytes, 0, parse.origBytes.length);
            dataOutputStream.write(0);
            dataOutputStream.write(0);
            dataOutputStream.write(0);
            dataOutputStream.write(encode.length);
            dataOutputStream.write(encode, 0, encode.length);
            dataOutputStream.close();
            SubmitPdu submitPdu = new SubmitPdu();
            submitPdu.encodedMessage = byteArrayOutputStream.toByteArray();
            submitPdu.encodedScAddress = null;
            return submitPdu;
        } catch (IOException e) {
            Rlog.e(LOG_TAG, "creating SubmitPdu failed: " + e);
            return null;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public byte[] getIncomingSmsFingerprint() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byteArrayOutputStream.write(this.mEnvelope.serviceCategory);
        byteArrayOutputStream.write(this.mEnvelope.teleService);
        byteArrayOutputStream.write(this.mEnvelope.origAddress.origBytes, 0, this.mEnvelope.origAddress.origBytes.length);
        byteArrayOutputStream.write(this.mEnvelope.bearerData, 0, this.mEnvelope.bearerData.length);
        byteArrayOutputStream.write(this.mEnvelope.origSubaddress.origBytes, 0, this.mEnvelope.origSubaddress.origBytes.length);
        return byteArrayOutputStream.toByteArray();
    }

    public MessageClass getMessageClass() {
        return this.mBearerData.displayMode == 0 ? MessageClass.CLASS_0 : MessageClass.UNKNOWN;
    }

    /* Access modifiers changed, original: 0000 */
    public int getMessageType() {
        return this.mEnvelope.serviceCategory != 0 ? 1 : 0;
    }

    /* Access modifiers changed, original: 0000 */
    public int getNumOfVoicemails() {
        return this.mBearerData.numberOfMessages;
    }

    public int getProtocolIdentifier() {
        Rlog.w(LOG_TAG, "getProtocolIdentifier: is not supported in CDMA mode.");
        return 0;
    }

    public ArrayList<CdmaSmsCbProgramData> getSmsCbProgramData() {
        return this.mBearerData.serviceCategoryProgramData;
    }

    public int getStatus() {
        return this.status << 16;
    }

    /* Access modifiers changed, original: 0000 */
    public int getTeleService() {
        return this.mEnvelope.teleService;
    }

    public boolean isCphsMwiMessage() {
        Rlog.w(LOG_TAG, "isCphsMwiMessage: is not supported in CDMA mode.");
        return false;
    }

    public boolean isMWIClearMessage() {
        return this.mBearerData != null && this.mBearerData.numberOfMessages == 0;
    }

    public boolean isMWISetMessage() {
        return this.mBearerData != null && this.mBearerData.numberOfMessages > 0;
    }

    public boolean isMwiDontStore() {
        return this.mBearerData != null && this.mBearerData.numberOfMessages > 0 && this.mBearerData.userData == null;
    }

    public boolean isReplace() {
        Rlog.w(LOG_TAG, "isReplace: is not supported in CDMA mode.");
        return false;
    }

    public boolean isReplyPathPresent() {
        Rlog.w(LOG_TAG, "isReplyPathPresent: is not supported in CDMA mode.");
        return false;
    }

    public boolean isStatusReportMessage() {
        return this.mBearerData.messageType == 4;
    }

    /* Access modifiers changed, original: 0000 */
    public SmsCbMessage parseBroadcastSms() {
        BearerData decode = BearerData.decode(this.mEnvelope.bearerData, this.mEnvelope.serviceCategory);
        if (decode == null) {
            Rlog.w(LOG_TAG, "BearerData.decode() returned null");
            return null;
        }
        if (Rlog.isLoggable(LOGGABLE_TAG, 2)) {
            Rlog.d(LOG_TAG, "MT raw BearerData = " + HexDump.toHexString(this.mEnvelope.bearerData));
        }
        return new SmsCbMessage(2, 1, decode.messageId, new SmsCbLocation(SystemProperties.get("gsm.operator.numeric")), this.mEnvelope.serviceCategory, decode.getLanguage(), decode.userData.payloadStr, decode.priority, null, decode.cmasWarningInfo);
    }

    /* Access modifiers changed, original: protected */
    public void parseSms() {
        if (this.mEnvelope.teleService == SmsEnvelope.TELESERVICE_MWI) {
            this.mBearerData = new BearerData();
            if (this.mEnvelope.bearerData != null) {
                this.mBearerData.numberOfMessages = this.mEnvelope.bearerData[0] & 255;
                return;
            }
            return;
        }
        this.mBearerData = BearerData.decode(this.mEnvelope.bearerData);
        if (Rlog.isLoggable(LOGGABLE_TAG, 2)) {
            Rlog.d(LOG_TAG, "MT raw BearerData = '" + HexDump.toHexString(this.mEnvelope.bearerData) + "'");
            Rlog.d(LOG_TAG, "MT (decoded) BearerData = " + this.mBearerData);
        }
        this.mMessageRef = this.mBearerData.messageId;
        if (this.mBearerData.userData != null) {
            this.mUserData = this.mBearerData.userData.payload;
            this.mUserDataHeader = this.mBearerData.userData.userDataHeader;
            this.mMessageBody = this.mBearerData.userData.payloadStr;
        }
        if (this.mOriginatingAddress != null) {
            this.mOriginatingAddress.address = new String(this.mOriginatingAddress.origBytes);
            if (this.mOriginatingAddress.ton == 1 && this.mOriginatingAddress.address.charAt(0) != '+') {
                this.mOriginatingAddress.address = "+" + this.mOriginatingAddress.address;
            }
        }
        if (this.mBearerData.msgCenterTimeStamp != null) {
            this.mScTimeMillis = this.mBearerData.msgCenterTimeStamp.toMillis(true);
        }
        if (this.mBearerData.messageType == 4) {
            if (this.mBearerData.messageStatusSet) {
                this.status = this.mBearerData.errorClass << 8;
                this.status |= this.mBearerData.messageStatus;
            } else {
                Rlog.d(LOG_TAG, "DELIVERY_ACK message without msgStatus (" + (this.mUserData == null ? "also missing" : "does have") + " userData).");
                this.status = 0;
            }
        } else if (!(this.mBearerData.messageType == 1 || this.mBearerData.messageType == 2)) {
            throw new RuntimeException("Unsupported message type: " + this.mBearerData.messageType);
        }
        if (this.mMessageBody != null) {
            parseMessageBody();
        } else {
            if (this.mUserData != null) {
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public boolean processCdmaCTWdpHeader(SmsMessage smsMessage) {
        boolean z = true;
        try {
            BitwiseInputStream bitwiseInputStream = new BitwiseInputStream(smsMessage.getUserData());
            if (bitwiseInputStream.read(8) != 0) {
                Rlog.e(LOG_TAG, "Invalid WDP SubparameterId");
                return false;
            } else if (bitwiseInputStream.read(8) != 3) {
                Rlog.e(LOG_TAG, "Invalid WDP subparameter length");
                return false;
            } else {
                smsMessage.mBearerData.messageType = bitwiseInputStream.read(4);
                int read = bitwiseInputStream.read(8) | (bitwiseInputStream.read(8) << 8);
                smsMessage.mBearerData.hasUserDataHeader = bitwiseInputStream.read(1) == 1;
                if (smsMessage.mBearerData.hasUserDataHeader) {
                    Rlog.e(LOG_TAG, "Invalid WDP UserData header value");
                    return false;
                }
                bitwiseInputStream.skip(3);
                smsMessage.mBearerData.messageId = read;
                smsMessage.mMessageRef = read;
                bitwiseInputStream.read(8);
                int read2 = bitwiseInputStream.read(8);
                smsMessage.mBearerData.userData.msgEncoding = bitwiseInputStream.read(5);
                if (smsMessage.mBearerData.userData.msgEncoding != 0) {
                    Rlog.e(LOG_TAG, "Invalid WDP encoding");
                    return false;
                }
                smsMessage.mBearerData.userData.numFields = bitwiseInputStream.read(8);
                read = (read2 * 8) - 13;
                read2 = smsMessage.mBearerData.userData.numFields * 8;
                if (read2 >= read) {
                    read2 = read;
                }
                smsMessage.mBearerData.userData.payload = bitwiseInputStream.readByteArray(read2);
                smsMessage.mUserData = smsMessage.mBearerData.userData.payload;
                return z;
            }
        } catch (AccessException e) {
            Rlog.e(LOG_TAG, "CT WDP Header decode failed: " + e);
            z = false;
        }
    }
}
