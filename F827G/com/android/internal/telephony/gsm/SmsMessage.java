package com.android.internal.telephony.gsm;

import android.content.res.Resources;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import android.text.format.Time;
import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.Sms7BitEncodingTranslator;
import com.android.internal.telephony.SmsConstants.MessageClass;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsHeader.PortAddrs;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsMessageBase.SubmitPduBase;
import com.android.internal.telephony.uicc.IccUtils;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.PduHeaders;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import jp.co.sharp.telephony.OemGsmTelephonyManager;

public class SmsMessage extends SmsMessageBase {
    private static final int INVALID_VALIDITY_PERIOD = -1;
    static final String LOG_TAG = "SmsMessage";
    private static final int VALIDITY_PERIOD_FORMAT_ABSOLUTE = 3;
    private static final int VALIDITY_PERIOD_FORMAT_ENHANCED = 1;
    private static final int VALIDITY_PERIOD_FORMAT_NONE = 0;
    private static final int VALIDITY_PERIOD_FORMAT_RELATIVE = 2;
    private static final int VALIDITY_PERIOD_MAX = 635040;
    private static final int VALIDITY_PERIOD_MIN = 5;
    private static final boolean VDBG = false;
    private int mDataCodingScheme;
    private boolean mIsStatusReportMessage = false;
    private int mMti;
    private int mProtocolIdentifier;
    private boolean mReplyPathPresent = false;
    private int mStatus;
    private int mVoiceMailCount = 0;
    private MessageClass messageClass;

    private static class PduParser {
        int mCur = 0;
        byte[] mPdu;
        byte[] mUserData;
        SmsHeader mUserDataHeader;
        int mUserDataSeptetPadding = 0;

        PduParser(byte[] bArr) {
            this.mPdu = bArr;
        }

        /* Access modifiers changed, original: 0000 */
        public int constructUserData(boolean z, boolean z2) {
            int i;
            int i2;
            int i3 = this.mCur;
            int i4 = i3 + 1;
            int i5 = this.mPdu[i3] & 255;
            if (z) {
                try {
                    i = i4 + 1;
                    i2 = this.mPdu[i4] & 255;
                    byte[] bArr = new byte[i2];
                    System.arraycopy(this.mPdu, i, bArr, 0, i2);
                    this.mUserDataHeader = SmsHeader.fromByteArray(bArr);
                    i3 = i + i2;
                    i = (i2 + 1) * 8;
                    i4 = (i % 7 > 0 ? 1 : 0) + (i / 7);
                    this.mUserDataSeptetPadding = (i4 * 7) - i;
                    i = i4;
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage());
                }
            }
            i3 = i4;
            i = 0;
            i2 = 0;
            if (z2) {
                i4 = this.mPdu.length - i3;
            } else {
                i4 = i5 - (z ? i2 + 1 : 0);
                if (i4 < 0) {
                    i4 = 0;
                }
            }
            this.mUserData = new byte[i4];
            System.arraycopy(this.mPdu, i3, this.mUserData, 0, this.mUserData.length);
            this.mCur = i3;
            if (!z2) {
                return this.mUserData.length;
            }
            i4 = i5 - i;
            return i4 < 0 ? 0 : i4;
        }

        /* Access modifiers changed, original: 0000 */
        public GsmSmsAddress getAddress() {
            int i = (((this.mPdu[this.mCur] & 255) + 1) / 2) + 2;
            try {
                GsmSmsAddress gsmSmsAddress = new GsmSmsAddress(this.mPdu, this.mCur, i);
                this.mCur = i + this.mCur;
                return gsmSmsAddress;
            } catch (ParseException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        /* Access modifiers changed, original: 0000 */
        public int getByte() {
            byte[] bArr = this.mPdu;
            int i = this.mCur;
            this.mCur = i + 1;
            return bArr[i] & 255;
        }

        /* Access modifiers changed, original: 0000 */
        public String getSCAddress() {
            String str = null;
            int i = getByte();
            if (i != 0) {
                try {
                    str = PhoneNumberUtils.calledPartyBCDToString(this.mPdu, this.mCur, i);
                } catch (RuntimeException e) {
                    Rlog.d(SmsMessage.LOG_TAG, "invalid SC address: ", e);
                }
            }
            this.mCur += i;
            return str;
        }

        /* Access modifiers changed, original: 0000 */
        public long getSCTimestampMillis() {
            byte[] bArr = this.mPdu;
            int i = this.mCur;
            this.mCur = i + 1;
            i = IccUtils.gsmBcdByteToInt(bArr[i]);
            bArr = this.mPdu;
            int i2 = this.mCur;
            this.mCur = i2 + 1;
            i2 = IccUtils.gsmBcdByteToInt(bArr[i2]);
            bArr = this.mPdu;
            int i3 = this.mCur;
            this.mCur = i3 + 1;
            i3 = IccUtils.gsmBcdByteToInt(bArr[i3]);
            bArr = this.mPdu;
            int i4 = this.mCur;
            this.mCur = i4 + 1;
            i4 = IccUtils.gsmBcdByteToInt(bArr[i4]);
            bArr = this.mPdu;
            int i5 = this.mCur;
            this.mCur = i5 + 1;
            i5 = IccUtils.gsmBcdByteToInt(bArr[i5]);
            bArr = this.mPdu;
            int i6 = this.mCur;
            this.mCur = i6 + 1;
            i6 = IccUtils.gsmBcdByteToInt(bArr[i6]);
            bArr = this.mPdu;
            int i7 = this.mCur;
            this.mCur = i7 + 1;
            byte b = bArr[i7];
            int gsmBcdByteToInt = IccUtils.gsmBcdByteToInt((byte) (b & -9));
            if ((b & 8) != 0) {
                gsmBcdByteToInt = -gsmBcdByteToInt;
            }
            Time time = new Time("UTC");
            time.year = i >= 90 ? i + OemGsmTelephonyManager.GSM_1900 : i + 2000;
            time.month = i2 - 1;
            time.monthDay = i3;
            time.hour = i4;
            time.minute = i5;
            time.second = i6;
            return time.toMillis(true) - ((long) (((gsmBcdByteToInt * 15) * 60) * 1000));
        }

        /* Access modifiers changed, original: 0000 */
        public byte[] getUserData() {
            return this.mUserData;
        }

        /* Access modifiers changed, original: 0000 */
        public String getUserDataGSM7Bit(int i, int i2, int i3) {
            String gsm7BitPackedToString = GsmAlphabet.gsm7BitPackedToString(this.mPdu, this.mCur, i, this.mUserDataSeptetPadding, i2, i3);
            this.mCur += (i * 7) / 8;
            return gsm7BitPackedToString;
        }

        /* Access modifiers changed, original: 0000 */
        public String getUserDataGSM8bit(int i) {
            String gsm8BitUnpackedToString = GsmAlphabet.gsm8BitUnpackedToString(this.mPdu, this.mCur, i);
            this.mCur += i;
            return gsm8BitUnpackedToString;
        }

        /* Access modifiers changed, original: 0000 */
        public SmsHeader getUserDataHeader() {
            return this.mUserDataHeader;
        }

        /* Access modifiers changed, original: 0000 */
        public String getUserDataKSC5601(int i) {
            String str;
            try {
                str = new String(this.mPdu, this.mCur, i, "KSC5601");
            } catch (UnsupportedEncodingException e) {
                Throwable th = e;
                str = "";
                Rlog.e(SmsMessage.LOG_TAG, "implausible UnsupportedEncodingException", th);
            }
            this.mCur += i;
            return str;
        }

        /* Access modifiers changed, original: 0000 */
        public String getUserDataUCS2(int i) {
            String str;
            try {
                str = new String(this.mPdu, this.mCur, i, CharacterSets.MIMENAME_UTF_16);
            } catch (UnsupportedEncodingException e) {
                Throwable th = e;
                str = "";
                Rlog.e(SmsMessage.LOG_TAG, "implausible UnsupportedEncodingException", th);
            }
            this.mCur += i;
            return str;
        }

        /* Access modifiers changed, original: 0000 */
        public boolean moreDataPresent() {
            return this.mPdu.length > this.mCur;
        }
    }

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
        TextEncodingDetails countGsmSeptets = GsmAlphabet.countGsmSeptets(charSequence, z);
        if (countGsmSeptets != null) {
            return countGsmSeptets;
        }
        TextEncodingDetails textEncodingDetails = new TextEncodingDetails();
        int length = charSequence.length() * 2;
        textEncodingDetails.codeUnitCount = charSequence.length();
        if (length > 140) {
            int i = 134;
            if (!android.telephony.SmsMessage.hasEmsSupport() && length <= 1188) {
                i = 132;
            }
            textEncodingDetails.msgCount = ((i - 1) + length) / i;
            textEncodingDetails.codeUnitsRemaining = ((i * textEncodingDetails.msgCount) - length) / 2;
        } else {
            textEncodingDetails.msgCount = 1;
            textEncodingDetails.codeUnitsRemaining = (140 - length) / 2;
        }
        textEncodingDetails.codeUnitSize = 3;
        return textEncodingDetails;
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
            int length = bArr.length - 1;
            byte[] bArr2 = new byte[length];
            System.arraycopy(bArr, 1, bArr2, 0, length);
            smsMessage.parsePdu(bArr2);
            return smsMessage;
        } catch (RuntimeException e) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", e);
            return null;
        }
    }

    public static SmsMessage createFromPdu(byte[] bArr) {
        try {
            SmsMessage smsMessage = new SmsMessage();
            smsMessage.parsePdu(bArr);
            return smsMessage;
        } catch (RuntimeException e) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", e);
            return null;
        } catch (OutOfMemoryError e2) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed with out of memory: ", e2);
            return null;
        }
    }

    private static byte[] encodeUCS2(String str, byte[] bArr) throws UnsupportedEncodingException {
        byte[] bArr2;
        byte[] bytes = str.getBytes("utf-16be");
        if (bArr != null) {
            bArr2 = new byte[((bArr.length + bytes.length) + 1)];
            bArr2[0] = (byte) bArr.length;
            System.arraycopy(bArr, 0, bArr2, 1, bArr.length);
            System.arraycopy(bytes, 0, bArr2, bArr.length + 1, bytes.length);
        } else {
            bArr2 = bytes;
        }
        bytes = new byte[(bArr2.length + 1)];
        bytes[0] = (byte) (bArr2.length & 255);
        System.arraycopy(bArr2, 0, bytes, 1, bArr2.length);
        return bytes;
    }

    public static int getRelativeValidityPeriod(int i) {
        if (i >= 5 && i <= VALIDITY_PERIOD_MAX) {
            return i <= 720 ? (i / 5) - 1 : i <= 1440 ? ((i - 720) / 30) + 143 : i <= 43200 ? (i / 1440) + PduHeaders.STORE_STATUS_TEXT : i <= VALIDITY_PERIOD_MAX ? (i / 10080) + 192 : -1;
        } else {
            Rlog.e(LOG_TAG, "Invalid Validity Period" + i);
            return -1;
        }
    }

    public static SubmitPdu getSubmitPdu(String str, String str2, int i, int i2, byte[] bArr, boolean z) {
        PortAddrs portAddrs = new PortAddrs();
        portAddrs.destPort = i;
        portAddrs.origPort = i2;
        portAddrs.areEightBits = false;
        SmsHeader smsHeader = new SmsHeader();
        smsHeader.portAddrs = portAddrs;
        byte[] toByteArray = SmsHeader.toByteArray(smsHeader);
        if ((bArr.length + toByteArray.length) + 1 > 140) {
            Rlog.e(LOG_TAG, "SMS data message may only contain " + ((140 - toByteArray.length) - 1) + " bytes");
            return null;
        }
        SubmitPdu submitPdu = new SubmitPdu();
        ByteArrayOutputStream submitPduHead = getSubmitPduHead(str, str2, (byte) 65, z, submitPdu);
        submitPduHead.write(4);
        submitPduHead.write((bArr.length + toByteArray.length) + 1);
        submitPduHead.write(toByteArray.length);
        submitPduHead.write(toByteArray, 0, toByteArray.length);
        submitPduHead.write(bArr, 0, bArr.length);
        submitPdu.encodedMessage = submitPduHead.toByteArray();
        return submitPdu;
    }

    public static SubmitPdu getSubmitPdu(String str, String str2, int i, byte[] bArr, boolean z) {
        return getSubmitPdu(str, str2, i, 0, bArr, z);
    }

    public static SubmitPdu getSubmitPdu(String str, String str2, String str3, boolean z) {
        return getSubmitPdu(str, str2, str3, z, null);
    }

    public static SubmitPdu getSubmitPdu(String str, String str2, String str3, boolean z, int i) {
        return getSubmitPdu(str, str2, str3, z, null, 0, 0, 0, i);
    }

    public static SubmitPdu getSubmitPdu(String str, String str2, String str3, boolean z, byte[] bArr) {
        return getSubmitPdu(str, str2, str3, z, bArr, 0, 0, 0);
    }

    public static SubmitPdu getSubmitPdu(String str, String str2, String str3, boolean z, byte[] bArr, int i, int i2, int i3) {
        return getSubmitPdu(str, str2, str3, z, bArr, i, i2, i3, -1);
    }

    public static SubmitPdu getSubmitPdu(String str, String str2, String str3, boolean z, byte[] bArr, int i, int i2, int i3, int i4) {
        if (str3 == null || str2 == null) {
            return null;
        }
        byte[] stringToGsm7BitPackedWithHeader;
        if (i == 0) {
            TextEncodingDetails calculateLength = calculateLength(str3, false);
            i = calculateLength.codeUnitSize;
            i2 = calculateLength.languageTable;
            i3 = calculateLength.languageShiftTable;
            if (i == 1 && !(i2 == 0 && i3 == 0)) {
                SmsHeader fromByteArray;
                if (bArr != null) {
                    fromByteArray = SmsHeader.fromByteArray(bArr);
                    if (!(fromByteArray.languageTable == i2 && fromByteArray.languageShiftTable == i3)) {
                        Rlog.w(LOG_TAG, "Updating language table in SMS header: " + fromByteArray.languageTable + " -> " + i2 + ", " + fromByteArray.languageShiftTable + " -> " + i3);
                        fromByteArray.languageTable = i2;
                        fromByteArray.languageShiftTable = i3;
                        bArr = SmsHeader.toByteArray(fromByteArray);
                    }
                } else {
                    fromByteArray = new SmsHeader();
                    fromByteArray.languageTable = i2;
                    fromByteArray.languageShiftTable = i3;
                    bArr = SmsHeader.toByteArray(fromByteArray);
                }
            }
        }
        SubmitPdu submitPdu = new SubmitPdu();
        int relativeValidityPeriod = getRelativeValidityPeriod(i4);
        int i5 = relativeValidityPeriod >= 0 ? 2 : 0;
        ByteArrayOutputStream submitPduHead = getSubmitPduHead(str, str2, (byte) ((bArr != null ? 64 : 0) | 17), z, submitPdu);
        if (i == 1) {
            try {
                stringToGsm7BitPackedWithHeader = GsmAlphabet.stringToGsm7BitPackedWithHeader(str3, bArr, i2, i3);
            } catch (EncodeException e) {
                try {
                    stringToGsm7BitPackedWithHeader = encodeUCS2(str3, bArr);
                    i = 3;
                } catch (UnsupportedEncodingException e2) {
                    Rlog.e(LOG_TAG, "Implausible UnsupportedEncodingException ", e2);
                    return null;
                }
            }
        }
        try {
            stringToGsm7BitPackedWithHeader = encodeUCS2(str3, bArr);
        } catch (UnsupportedEncodingException e22) {
            Rlog.e(LOG_TAG, "Implausible UnsupportedEncodingException ", e22);
            return null;
        }
        if (i == 1) {
            if ((stringToGsm7BitPackedWithHeader[0] & 255) > 160) {
                Rlog.e(LOG_TAG, "Message too long (" + (stringToGsm7BitPackedWithHeader[0] & 255) + " septets)");
                return null;
            }
            submitPduHead.write(0);
        } else if ((stringToGsm7BitPackedWithHeader[0] & 255) > 140) {
            Rlog.e(LOG_TAG, "Message too long (" + (stringToGsm7BitPackedWithHeader[0] & 255) + " bytes)");
            return null;
        } else {
            submitPduHead.write(8);
        }
        if (i5 == 2) {
            submitPduHead.write(relativeValidityPeriod);
        } else {
            submitPduHead.write(PduHeaders.TOTALS);
        }
        submitPduHead.write(stringToGsm7BitPackedWithHeader, 0, stringToGsm7BitPackedWithHeader.length);
        submitPdu.encodedMessage = submitPduHead.toByteArray();
        return submitPdu;
    }

    private static ByteArrayOutputStream getSubmitPduHead(String str, String str2, byte b, boolean z, SubmitPdu submitPdu) {
        int b2;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(PduHeaders.RECOMMENDED_RETRIEVAL_MODE);
        if (str == null) {
            submitPdu.encodedScAddress = null;
        } else {
            submitPdu.encodedScAddress = PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(str);
        }
        if (z) {
            b2 = (byte) (b2 | 32);
        }
        byteArrayOutputStream.write(b2);
        byteArrayOutputStream.write(0);
        byte[] networkPortionToCalledPartyBCD = PhoneNumberUtils.networkPortionToCalledPartyBCD(str2);
        byteArrayOutputStream.write(((networkPortionToCalledPartyBCD.length - 1) * 2) - ((networkPortionToCalledPartyBCD[networkPortionToCalledPartyBCD.length + -1] & 240) == 240 ? 1 : 0));
        byteArrayOutputStream.write(networkPortionToCalledPartyBCD, 0, networkPortionToCalledPartyBCD.length);
        byteArrayOutputStream.write(0);
        return byteArrayOutputStream;
    }

    public static int getTPLayerLengthForPDU(String str) {
        return ((str.length() / 2) - Integer.parseInt(str.substring(0, 2), 16)) - 1;
    }

    public static SmsMessage newFromCDS(String str) {
        try {
            SmsMessage smsMessage = new SmsMessage();
            smsMessage.parsePdu(IccUtils.hexStringToBytes(str));
            return smsMessage;
        } catch (RuntimeException e) {
            Rlog.e(LOG_TAG, "CDS SMS PDU parsing failed: ", e);
            return null;
        }
    }

    public static SmsMessage newFromCMT(String[] strArr) {
        try {
            SmsMessage smsMessage = new SmsMessage();
            smsMessage.parsePdu(IccUtils.hexStringToBytes(strArr[1]));
            return smsMessage;
        } catch (RuntimeException e) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", e);
            return null;
        }
    }

    private void parsePdu(byte[] bArr) {
        this.mPdu = bArr;
        PduParser pduParser = new PduParser(bArr);
        this.mScAddress = pduParser.getSCAddress();
        if (this.mScAddress != null) {
        }
        int i = pduParser.getByte();
        this.mMti = i & 3;
        switch (this.mMti) {
            case 0:
            case 3:
                parseSmsDeliver(pduParser, i);
                return;
            case 1:
                parseSmsSubmit(pduParser, i);
                return;
            case 2:
                parseSmsStatusReport(pduParser, i);
                return;
            default:
                throw new RuntimeException("Unsupported message type");
        }
    }

    private void parseSmsDeliver(PduParser pduParser, int i) {
        boolean z = true;
        this.mReplyPathPresent = (i & 128) == 128;
        this.mOriginatingAddress = pduParser.getAddress();
        if (this.mOriginatingAddress != null) {
        }
        this.mProtocolIdentifier = pduParser.getByte();
        this.mDataCodingScheme = pduParser.getByte();
        this.mScTimeMillis = pduParser.getSCTimestampMillis();
        if ((i & 64) != 64) {
            z = false;
        }
        parseUserData(pduParser, z);
    }

    private void parseSmsStatusReport(PduParser pduParser, int i) {
        boolean z = true;
        this.mIsStatusReportMessage = true;
        this.mMessageRef = pduParser.getByte();
        this.mRecipientAddress = pduParser.getAddress();
        this.mScTimeMillis = pduParser.getSCTimestampMillis();
        pduParser.getSCTimestampMillis();
        this.mStatus = pduParser.getByte();
        if (pduParser.moreDataPresent()) {
            int i2 = pduParser.getByte();
            int i3 = i2;
            while ((i3 & 128) != 0) {
                i3 = pduParser.getByte();
            }
            if ((i2 & 120) == 0) {
                if ((i2 & 1) != 0) {
                    this.mProtocolIdentifier = pduParser.getByte();
                }
                if ((i2 & 2) != 0) {
                    this.mDataCodingScheme = pduParser.getByte();
                }
                if ((i2 & 4) != 0) {
                    if ((i & 64) != 64) {
                        z = false;
                    }
                    parseUserData(pduParser, z);
                }
            }
        }
    }

    private void parseSmsSubmit(PduParser pduParser, int i) {
        boolean z = true;
        this.mReplyPathPresent = (i & 128) == 128;
        this.mMessageRef = pduParser.getByte();
        this.mRecipientAddress = pduParser.getAddress();
        if (this.mRecipientAddress != null) {
        }
        this.mProtocolIdentifier = pduParser.getByte();
        this.mDataCodingScheme = pduParser.getByte();
        int i2 = (i >> 3) & 3;
        i2 = i2 == 0 ? 0 : 2 == i2 ? 1 : 7;
        while (i2 > 0) {
            pduParser.getByte();
            i2--;
        }
        if ((i & 64) != 64) {
            z = false;
        }
        parseUserData(pduParser, z);
    }

    /* JADX WARNING: Missing block: B:37:0x00dc, code skipped:
            if (android.content.res.Resources.getSystem().getBoolean(17957005) == false) goto L_0x00de;
     */
    private void parseUserData(com.android.internal.telephony.gsm.SmsMessage.PduParser r13, boolean r14) {
        /*
        r12 = this;
        r11 = 224; // 0xe0 float:3.14E-43 double:1.107E-321;
        r10 = 128; // 0x80 float:1.794E-43 double:6.32E-322;
        r4 = 2;
        r2 = 0;
        r1 = 1;
        r0 = r12.mDataCodingScheme;
        r0 = r0 & 128;
        if (r0 != 0) goto L_0x00fc;
    L_0x000d:
        r0 = r12.mDataCodingScheme;
        r0 = r0 & 32;
        if (r0 == 0) goto L_0x00b8;
    L_0x0013:
        r0 = r1;
    L_0x0014:
        r3 = r12.mDataCodingScheme;
        r3 = r3 & 16;
        if (r3 == 0) goto L_0x00bb;
    L_0x001a:
        r3 = r1;
    L_0x001b:
        if (r0 == 0) goto L_0x00be;
    L_0x001d:
        r0 = "SmsMessage";
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "4 - Unsupported SMS data coding scheme (compression) ";
        r4 = r4.append(r5);
        r5 = r12.mDataCodingScheme;
        r5 = r5 & 255;
        r4 = r4.append(r5);
        r4 = r4.toString();
        android.telephony.Rlog.w(r0, r4);
        r4 = r2;
    L_0x003a:
        if (r4 != r1) goto L_0x0205;
    L_0x003c:
        r0 = r1;
    L_0x003d:
        r5 = r13.constructUserData(r14, r0);
        r0 = r13.getUserData();
        r12.mUserData = r0;
        r0 = r13.getUserDataHeader();
        r12.mUserDataHeader = r0;
        if (r14 == 0) goto L_0x0242;
    L_0x004f:
        r0 = r12.mUserDataHeader;
        r0 = r0.specialSmsMsgList;
        r0 = r0.size();
        if (r0 == 0) goto L_0x0242;
    L_0x0059:
        r0 = r12.mUserDataHeader;
        r0 = r0.specialSmsMsgList;
        r6 = r0.iterator();
    L_0x0061:
        r0 = r6.hasNext();
        if (r0 == 0) goto L_0x0242;
    L_0x0067:
        r0 = r6.next();
        r0 = (com.android.internal.telephony.SmsHeader.SpecialSmsMsg) r0;
        r7 = r0.msgIndType;
        r7 = r7 & 255;
        if (r7 == 0) goto L_0x0075;
    L_0x0073:
        if (r7 != r10) goto L_0x0228;
    L_0x0075:
        r12.mIsMwi = r1;
        if (r7 != r10) goto L_0x0208;
    L_0x0079:
        r12.mMwiDontStore = r2;
    L_0x007b:
        r0 = r0.msgCount;
        r0 = r0 & 255;
        r12.mVoiceMailCount = r0;
        r0 = r12.mVoiceMailCount;
        if (r0 <= 0) goto L_0x0224;
    L_0x0085:
        r12.mMwiSense = r1;
    L_0x0087:
        r0 = "SmsMessage";
        r8 = new java.lang.StringBuilder;
        r8.<init>();
        r9 = "MWI in TP-UDH for Vmail. Msg Ind = ";
        r8 = r8.append(r9);
        r7 = r8.append(r7);
        r8 = " Dont store = ";
        r7 = r7.append(r8);
        r8 = r12.mMwiDontStore;
        r7 = r7.append(r8);
        r8 = " Vmail count = ";
        r7 = r7.append(r8);
        r8 = r12.mVoiceMailCount;
        r7 = r7.append(r8);
        r7 = r7.toString();
        android.telephony.Rlog.w(r0, r7);
        goto L_0x0061;
    L_0x00b8:
        r0 = r2;
        goto L_0x0014;
    L_0x00bb:
        r3 = r2;
        goto L_0x001b;
    L_0x00be:
        r0 = r12.mDataCodingScheme;
        r0 = r0 >> 2;
        r0 = r0 & 3;
        switch(r0) {
            case 0: goto L_0x00ca;
            case 1: goto L_0x00d1;
            case 2: goto L_0x00cd;
            case 3: goto L_0x00de;
            default: goto L_0x00c7;
        };
    L_0x00c7:
        r4 = r2;
        goto L_0x003a;
    L_0x00ca:
        r4 = r1;
        goto L_0x003a;
    L_0x00cd:
        r0 = 3;
        r4 = r0;
        goto L_0x003a;
    L_0x00d1:
        r0 = android.content.res.Resources.getSystem();
        r5 = 17957005; // 0x112008d float:2.681636E-38 double:8.8719393E-317;
        r0 = r0.getBoolean(r5);
        if (r0 != 0) goto L_0x003a;
    L_0x00de:
        r0 = "SmsMessage";
        r5 = new java.lang.StringBuilder;
        r5.<init>();
        r6 = "1 - Unsupported SMS data coding scheme ";
        r5 = r5.append(r6);
        r6 = r12.mDataCodingScheme;
        r6 = r6 & 255;
        r5 = r5.append(r6);
        r5 = r5.toString();
        android.telephony.Rlog.w(r0, r5);
        goto L_0x003a;
    L_0x00fc:
        r0 = r12.mDataCodingScheme;
        r0 = r0 & 240;
        r3 = 240; // 0xf0 float:3.36E-43 double:1.186E-321;
        if (r0 != r3) goto L_0x0111;
    L_0x0104:
        r0 = r12.mDataCodingScheme;
        r0 = r0 & 4;
        if (r0 != 0) goto L_0x010e;
    L_0x010a:
        r3 = r1;
        r4 = r1;
        goto L_0x003a;
    L_0x010e:
        r3 = r1;
        goto L_0x003a;
    L_0x0111:
        r0 = r12.mDataCodingScheme;
        r0 = r0 & 240;
        r3 = 192; // 0xc0 float:2.69E-43 double:9.5E-322;
        if (r0 == r3) goto L_0x0127;
    L_0x0119:
        r0 = r12.mDataCodingScheme;
        r0 = r0 & 240;
        r3 = 208; // 0xd0 float:2.91E-43 double:1.03E-321;
        if (r0 == r3) goto L_0x0127;
    L_0x0121:
        r0 = r12.mDataCodingScheme;
        r0 = r0 & 240;
        if (r0 != r11) goto L_0x01b4;
    L_0x0127:
        r0 = r12.mDataCodingScheme;
        r0 = r0 & 240;
        if (r0 != r11) goto L_0x0189;
    L_0x012d:
        r0 = 3;
    L_0x012e:
        r3 = r12.mDataCodingScheme;
        r3 = r3 & 8;
        r4 = 8;
        if (r3 != r4) goto L_0x018b;
    L_0x0136:
        r3 = r1;
    L_0x0137:
        r4 = r12.mDataCodingScheme;
        r4 = r4 & 3;
        if (r4 != 0) goto L_0x0192;
    L_0x013d:
        r12.mIsMwi = r1;
        r12.mMwiSense = r3;
        r4 = r12.mDataCodingScheme;
        r4 = r4 & 240;
        r5 = 192; // 0xc0 float:2.69E-43 double:9.5E-322;
        if (r4 != r5) goto L_0x018d;
    L_0x0149:
        r4 = r1;
    L_0x014a:
        r12.mMwiDontStore = r4;
        if (r3 != r1) goto L_0x018f;
    L_0x014e:
        r3 = -1;
        r12.mVoiceMailCount = r3;
    L_0x0151:
        r3 = "SmsMessage";
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "MWI in DCS for Vmail. DCS = ";
        r4 = r4.append(r5);
        r5 = r12.mDataCodingScheme;
        r5 = r5 & 255;
        r4 = r4.append(r5);
        r5 = " Dont store = ";
        r4 = r4.append(r5);
        r5 = r12.mMwiDontStore;
        r4 = r4.append(r5);
        r5 = " vmail count = ";
        r4 = r4.append(r5);
        r5 = r12.mVoiceMailCount;
        r4 = r4.append(r5);
        r4 = r4.toString();
        android.telephony.Rlog.w(r3, r4);
        r3 = r2;
        r4 = r0;
        goto L_0x003a;
    L_0x0189:
        r0 = r1;
        goto L_0x012e;
    L_0x018b:
        r3 = r2;
        goto L_0x0137;
    L_0x018d:
        r4 = r2;
        goto L_0x014a;
    L_0x018f:
        r12.mVoiceMailCount = r2;
        goto L_0x0151;
    L_0x0192:
        r12.mIsMwi = r2;
        r3 = "SmsMessage";
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "MWI in DCS for fax/email/other: ";
        r4 = r4.append(r5);
        r5 = r12.mDataCodingScheme;
        r5 = r5 & 255;
        r4 = r4.append(r5);
        r4 = r4.toString();
        android.telephony.Rlog.w(r3, r4);
        r3 = r2;
        r4 = r0;
        goto L_0x003a;
    L_0x01b4:
        r0 = r12.mDataCodingScheme;
        r0 = r0 & 192;
        if (r0 != r10) goto L_0x01e5;
    L_0x01ba:
        r0 = r12.mDataCodingScheme;
        r3 = 132; // 0x84 float:1.85E-43 double:6.5E-322;
        if (r0 != r3) goto L_0x01c5;
    L_0x01c0:
        r0 = 4;
        r3 = r2;
        r4 = r0;
        goto L_0x003a;
    L_0x01c5:
        r0 = "SmsMessage";
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "5 - Unsupported SMS data coding scheme ";
        r3 = r3.append(r4);
        r4 = r12.mDataCodingScheme;
        r4 = r4 & 255;
        r3 = r3.append(r4);
        r3 = r3.toString();
        android.telephony.Rlog.w(r0, r3);
        r3 = r2;
        r4 = r2;
        goto L_0x003a;
    L_0x01e5:
        r0 = "SmsMessage";
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "3 - Unsupported SMS data coding scheme ";
        r3 = r3.append(r4);
        r4 = r12.mDataCodingScheme;
        r4 = r4 & 255;
        r3 = r3.append(r4);
        r3 = r3.toString();
        android.telephony.Rlog.w(r0, r3);
        r3 = r2;
        r4 = r2;
        goto L_0x003a;
    L_0x0205:
        r0 = r2;
        goto L_0x003d;
    L_0x0208:
        r8 = r12.mMwiDontStore;
        if (r8 != 0) goto L_0x007b;
    L_0x020c:
        r8 = r12.mDataCodingScheme;
        r8 = r8 & 240;
        r9 = 208; // 0xd0 float:2.91E-43 double:1.03E-321;
        if (r8 == r9) goto L_0x021a;
    L_0x0214:
        r8 = r12.mDataCodingScheme;
        r8 = r8 & 240;
        if (r8 != r11) goto L_0x0220;
    L_0x021a:
        r8 = r12.mDataCodingScheme;
        r8 = r8 & 3;
        if (r8 == 0) goto L_0x007b;
    L_0x0220:
        r12.mMwiDontStore = r1;
        goto L_0x007b;
    L_0x0224:
        r12.mMwiSense = r2;
        goto L_0x0087;
    L_0x0228:
        r0 = "SmsMessage";
        r8 = new java.lang.StringBuilder;
        r8.<init>();
        r9 = "TP_UDH fax/email/extended msg/multisubscriber profile. Msg Ind = ";
        r8 = r8.append(r9);
        r7 = r8.append(r7);
        r7 = r7.toString();
        android.telephony.Rlog.w(r0, r7);
        goto L_0x0061;
    L_0x0242:
        switch(r4) {
            case 0: goto L_0x0253;
            case 1: goto L_0x026f;
            case 2: goto L_0x0257;
            case 3: goto L_0x0284;
            case 4: goto L_0x028b;
            default: goto L_0x0245;
        };
    L_0x0245:
        r0 = r12.mMessageBody;
        if (r0 == 0) goto L_0x024c;
    L_0x0249:
        r12.parseMessageBody();
    L_0x024c:
        if (r3 != 0) goto L_0x0292;
    L_0x024e:
        r0 = com.android.internal.telephony.SmsConstants.MessageClass.UNKNOWN;
        r12.messageClass = r0;
    L_0x0252:
        return;
    L_0x0253:
        r0 = 0;
        r12.mMessageBody = r0;
        goto L_0x0245;
    L_0x0257:
        r0 = android.content.res.Resources.getSystem();
        r1 = 17957005; // 0x112008d float:2.681636E-38 double:8.8719393E-317;
        r0 = r0.getBoolean(r1);
        if (r0 == 0) goto L_0x026b;
    L_0x0264:
        r0 = r13.getUserDataGSM8bit(r5);
        r12.mMessageBody = r0;
        goto L_0x0245;
    L_0x026b:
        r0 = 0;
        r12.mMessageBody = r0;
        goto L_0x0245;
    L_0x026f:
        if (r14 == 0) goto L_0x0282;
    L_0x0271:
        r0 = r12.mUserDataHeader;
        r0 = r0.languageTable;
    L_0x0275:
        if (r14 == 0) goto L_0x027b;
    L_0x0277:
        r1 = r12.mUserDataHeader;
        r2 = r1.languageShiftTable;
    L_0x027b:
        r0 = r13.getUserDataGSM7Bit(r5, r0, r2);
        r12.mMessageBody = r0;
        goto L_0x0245;
    L_0x0282:
        r0 = r2;
        goto L_0x0275;
    L_0x0284:
        r0 = r13.getUserDataUCS2(r5);
        r12.mMessageBody = r0;
        goto L_0x0245;
    L_0x028b:
        r0 = r13.getUserDataKSC5601(r5);
        r12.mMessageBody = r0;
        goto L_0x0245;
    L_0x0292:
        r0 = r12.mDataCodingScheme;
        r0 = r0 & 3;
        switch(r0) {
            case 0: goto L_0x029a;
            case 1: goto L_0x029f;
            case 2: goto L_0x02a4;
            case 3: goto L_0x02a9;
            default: goto L_0x0299;
        };
    L_0x0299:
        goto L_0x0252;
    L_0x029a:
        r0 = com.android.internal.telephony.SmsConstants.MessageClass.CLASS_0;
        r12.messageClass = r0;
        goto L_0x0252;
    L_0x029f:
        r0 = com.android.internal.telephony.SmsConstants.MessageClass.CLASS_1;
        r12.messageClass = r0;
        goto L_0x0252;
    L_0x02a4:
        r0 = com.android.internal.telephony.SmsConstants.MessageClass.CLASS_2;
        r12.messageClass = r0;
        goto L_0x0252;
    L_0x02a9:
        r0 = com.android.internal.telephony.SmsConstants.MessageClass.CLASS_3;
        r12.messageClass = r0;
        goto L_0x0252;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.gsm.SmsMessage.parseUserData(com.android.internal.telephony.gsm.SmsMessage$PduParser, boolean):void");
    }

    /* Access modifiers changed, original: 0000 */
    public int getDataCodingScheme() {
        return this.mDataCodingScheme;
    }

    public MessageClass getMessageClass() {
        return this.messageClass;
    }

    public int getMessageDCS() {
        return this.mDataCodingScheme;
    }

    public byte[] getMessageUDH() {
        return this.mUserDataHeader == null ? null : SmsHeader.toByteArray(this.mUserDataHeader);
    }

    public int getNumOfVoicemails() {
        if (!this.mIsMwi && isCphsMwiMessage()) {
            if (this.mOriginatingAddress == null || !((GsmSmsAddress) this.mOriginatingAddress).isCphsVoiceMessageSet()) {
                this.mVoiceMailCount = 0;
            } else {
                this.mVoiceMailCount = 255;
            }
            Rlog.v(LOG_TAG, "CPHS voice mail message");
        }
        return this.mVoiceMailCount;
    }

    public int getProtocolIdentifier() {
        return this.mProtocolIdentifier;
    }

    public int getStatus() {
        return this.mStatus;
    }

    public boolean isCphsMwiMessage() {
        return ((GsmSmsAddress) this.mOriginatingAddress).isCphsVoiceMessageClear() || ((GsmSmsAddress) this.mOriginatingAddress).isCphsVoiceMessageSet();
    }

    public boolean isMWIClearMessage() {
        if (this.mIsMwi && !this.mMwiSense) {
            return true;
        }
        boolean z = this.mOriginatingAddress != null && ((GsmSmsAddress) this.mOriginatingAddress).isCphsVoiceMessageClear();
        return z;
    }

    public boolean isMWISetMessage() {
        if (this.mIsMwi && this.mMwiSense) {
            return true;
        }
        boolean z = this.mOriginatingAddress != null && ((GsmSmsAddress) this.mOriginatingAddress).isCphsVoiceMessageSet();
        return z;
    }

    public boolean isMwiDontStore() {
        return (this.mIsMwi && this.mMwiDontStore) || (isCphsMwiMessage() && " ".equals(getMessageBody()));
    }

    public boolean isReplace() {
        return (this.mProtocolIdentifier & 192) == 64 && (this.mProtocolIdentifier & 63) > 0 && (this.mProtocolIdentifier & 63) < 8;
    }

    public boolean isReplyPathPresent() {
        return this.mReplyPathPresent;
    }

    public boolean isStatusReportMessage() {
        return this.mIsStatusReportMessage;
    }

    public boolean isTypeZero() {
        return this.mProtocolIdentifier == 64;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean isUsimDataDownload() {
        return this.messageClass == MessageClass.CLASS_2 && (this.mProtocolIdentifier == 127 || this.mProtocolIdentifier == 124);
    }
}
