package com.android.internal.telephony.cdma.sms;

import android.content.res.Resources;
import android.telephony.Rlog;
import android.telephony.SmsCbCmasInfo;
import android.telephony.cdma.CdmaSmsCbProgramData;
import android.telephony.cdma.CdmaSmsCbProgramResults;
import android.text.format.Time;
import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.gsm.SmsMessage;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.util.BitwiseInputStream;
import com.android.internal.util.BitwiseInputStream.AccessException;
import com.android.internal.util.BitwiseOutputStream;
import com.google.android.mms.pdu.PduPart;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TimeZone;
import jp.co.sharp.telephony.OemGsmTelephonyManager;

public final class BearerData {
    public static final int ALERT_DEFAULT = 0;
    public static final int ALERT_HIGH_PRIO = 3;
    public static final int ALERT_LOW_PRIO = 1;
    public static final int ALERT_MEDIUM_PRIO = 2;
    public static final int DISPLAY_MODE_DEFAULT = 1;
    public static final int DISPLAY_MODE_IMMEDIATE = 0;
    public static final int DISPLAY_MODE_USER = 2;
    public static final int ERROR_NONE = 0;
    public static final int ERROR_PERMANENT = 3;
    public static final int ERROR_TEMPORARY = 2;
    public static final int ERROR_UNDEFINED = 255;
    public static final int LANGUAGE_CHINESE = 6;
    public static final int LANGUAGE_ENGLISH = 1;
    public static final int LANGUAGE_FRENCH = 2;
    public static final int LANGUAGE_HEBREW = 7;
    public static final int LANGUAGE_JAPANESE = 4;
    public static final int LANGUAGE_KOREAN = 5;
    public static final int LANGUAGE_SPANISH = 3;
    public static final int LANGUAGE_UNKNOWN = 0;
    private static final String LOG_TAG = "BearerData";
    public static final int MESSAGE_TYPE_CANCELLATION = 3;
    public static final int MESSAGE_TYPE_DELIVER = 1;
    public static final int MESSAGE_TYPE_DELIVERY_ACK = 4;
    public static final int MESSAGE_TYPE_DELIVER_REPORT = 7;
    public static final int MESSAGE_TYPE_READ_ACK = 6;
    public static final int MESSAGE_TYPE_SUBMIT = 2;
    public static final int MESSAGE_TYPE_SUBMIT_REPORT = 8;
    public static final int MESSAGE_TYPE_USER_ACK = 5;
    public static final int PRIORITY_EMERGENCY = 3;
    public static final int PRIORITY_INTERACTIVE = 1;
    public static final int PRIORITY_NORMAL = 0;
    public static final int PRIORITY_URGENT = 2;
    public static final int PRIVACY_CONFIDENTIAL = 2;
    public static final int PRIVACY_NOT_RESTRICTED = 0;
    public static final int PRIVACY_RESTRICTED = 1;
    public static final int PRIVACY_SECRET = 3;
    public static final int RELATIVE_TIME_DAYS_LIMIT = 196;
    public static final int RELATIVE_TIME_HOURS_LIMIT = 167;
    public static final int RELATIVE_TIME_INDEFINITE = 245;
    public static final int RELATIVE_TIME_MINS_LIMIT = 143;
    public static final int RELATIVE_TIME_MOBILE_INACTIVE = 247;
    public static final int RELATIVE_TIME_NOW = 246;
    public static final int RELATIVE_TIME_RESERVED = 248;
    public static final int RELATIVE_TIME_WEEKS_LIMIT = 244;
    public static final int STATUS_ACCEPTED = 0;
    public static final int STATUS_BLOCKED_DESTINATION = 7;
    public static final int STATUS_CANCELLED = 3;
    public static final int STATUS_CANCEL_FAILED = 6;
    public static final int STATUS_DELIVERED = 2;
    public static final int STATUS_DEPOSITED_TO_INTERNET = 1;
    public static final int STATUS_DUPLICATE_MESSAGE = 9;
    public static final int STATUS_INVALID_DESTINATION = 10;
    public static final int STATUS_MESSAGE_EXPIRED = 13;
    public static final int STATUS_NETWORK_CONGESTION = 4;
    public static final int STATUS_NETWORK_ERROR = 5;
    public static final int STATUS_TEXT_TOO_LONG = 8;
    public static final int STATUS_UNDEFINED = 255;
    public static final int STATUS_UNKNOWN_ERROR = 31;
    private static final byte SUBPARAM_ALERT_ON_MESSAGE_DELIVERY = (byte) 12;
    private static final byte SUBPARAM_CALLBACK_NUMBER = (byte) 14;
    private static final byte SUBPARAM_DEFERRED_DELIVERY_TIME_ABSOLUTE = (byte) 6;
    private static final byte SUBPARAM_DEFERRED_DELIVERY_TIME_RELATIVE = (byte) 7;
    private static final byte SUBPARAM_ID_LAST_DEFINED = (byte) 23;
    private static final byte SUBPARAM_LANGUAGE_INDICATOR = (byte) 13;
    private static final byte SUBPARAM_MESSAGE_CENTER_TIME_STAMP = (byte) 3;
    private static final byte SUBPARAM_MESSAGE_DEPOSIT_INDEX = (byte) 17;
    private static final byte SUBPARAM_MESSAGE_DISPLAY_MODE = (byte) 15;
    private static final byte SUBPARAM_MESSAGE_IDENTIFIER = (byte) 0;
    private static final byte SUBPARAM_MESSAGE_STATUS = (byte) 20;
    private static final byte SUBPARAM_NUMBER_OF_MESSAGES = (byte) 11;
    private static final byte SUBPARAM_PRIORITY_INDICATOR = (byte) 8;
    private static final byte SUBPARAM_PRIVACY_INDICATOR = (byte) 9;
    private static final byte SUBPARAM_REPLY_OPTION = (byte) 10;
    private static final byte SUBPARAM_SERVICE_CATEGORY_PROGRAM_DATA = (byte) 18;
    private static final byte SUBPARAM_SERVICE_CATEGORY_PROGRAM_RESULTS = (byte) 19;
    private static final byte SUBPARAM_USER_DATA = (byte) 1;
    private static final byte SUBPARAM_USER_RESPONSE_CODE = (byte) 2;
    private static final byte SUBPARAM_VALIDITY_PERIOD_ABSOLUTE = (byte) 4;
    private static final byte SUBPARAM_VALIDITY_PERIOD_RELATIVE = (byte) 5;
    public int alert = 0;
    public boolean alertIndicatorSet = false;
    public CdmaSmsAddress callbackNumber;
    public SmsCbCmasInfo cmasWarningInfo;
    public TimeStamp deferredDeliveryTimeAbsolute;
    public int deferredDeliveryTimeRelative;
    public boolean deferredDeliveryTimeRelativeSet;
    public boolean deliveryAckReq;
    public int depositIndex;
    public int displayMode = 1;
    public boolean displayModeSet = false;
    public int errorClass = 255;
    public boolean hasUserDataHeader;
    public int language = 0;
    public boolean languageIndicatorSet = false;
    public int messageId;
    public int messageStatus = 255;
    public boolean messageStatusSet = false;
    public int messageType;
    public TimeStamp msgCenterTimeStamp;
    public int numberOfMessages;
    public int priority = 0;
    public boolean priorityIndicatorSet = false;
    public int privacy = 0;
    public boolean privacyIndicatorSet = false;
    public boolean readAckReq;
    public boolean reportReq;
    public ArrayList<CdmaSmsCbProgramData> serviceCategoryProgramData;
    public ArrayList<CdmaSmsCbProgramResults> serviceCategoryProgramResults;
    public boolean userAckReq;
    public UserData userData;
    public int userResponseCode;
    public boolean userResponseCodeSet = false;
    public TimeStamp validityPeriodAbsolute;
    public int validityPeriodRelative;
    public boolean validityPeriodRelativeSet;

    private static class CodingException extends Exception {
        public CodingException(String str) {
            super(str);
        }
    }

    private static class Gsm7bitCodingResult {
        byte[] data;
        int septets;

        private Gsm7bitCodingResult() {
        }
    }

    public static class TimeStamp extends Time {
        public TimeStamp() {
            super(TimeZone.getDefault().getID());
        }

        public static TimeStamp fromByteArray(byte[] bArr) {
            TimeStamp timeStamp = new TimeStamp();
            int cdmaBcdByteToInt = IccUtils.cdmaBcdByteToInt(bArr[0]);
            if (cdmaBcdByteToInt > 99 || cdmaBcdByteToInt < 0) {
                return null;
            }
            timeStamp.year = cdmaBcdByteToInt >= 96 ? cdmaBcdByteToInt + OemGsmTelephonyManager.GSM_1900 : cdmaBcdByteToInt + 2000;
            cdmaBcdByteToInt = IccUtils.cdmaBcdByteToInt(bArr[1]);
            if (cdmaBcdByteToInt < 1 || cdmaBcdByteToInt > 12) {
                return null;
            }
            timeStamp.month = cdmaBcdByteToInt - 1;
            cdmaBcdByteToInt = IccUtils.cdmaBcdByteToInt(bArr[2]);
            if (cdmaBcdByteToInt < 1 || cdmaBcdByteToInt > 31) {
                return null;
            }
            timeStamp.monthDay = cdmaBcdByteToInt;
            cdmaBcdByteToInt = IccUtils.cdmaBcdByteToInt(bArr[3]);
            if (cdmaBcdByteToInt < 0 || cdmaBcdByteToInt > 23) {
                return null;
            }
            timeStamp.hour = cdmaBcdByteToInt;
            cdmaBcdByteToInt = IccUtils.cdmaBcdByteToInt(bArr[4]);
            if (cdmaBcdByteToInt < 0 || cdmaBcdByteToInt > 59) {
                return null;
            }
            timeStamp.minute = cdmaBcdByteToInt;
            cdmaBcdByteToInt = IccUtils.cdmaBcdByteToInt(bArr[5]);
            if (cdmaBcdByteToInt < 0 || cdmaBcdByteToInt > 59) {
                return null;
            }
            timeStamp.second = cdmaBcdByteToInt;
            return timeStamp;
        }

        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("TimeStamp ");
            stringBuilder.append("{ year=" + this.year);
            stringBuilder.append(", month=" + this.month);
            stringBuilder.append(", day=" + this.monthDay);
            stringBuilder.append(", hour=" + this.hour);
            stringBuilder.append(", minute=" + this.minute);
            stringBuilder.append(", second=" + this.second);
            stringBuilder.append(" }");
            return stringBuilder.toString();
        }
    }

    public static TextEncodingDetails calcTextEncodingDetails(CharSequence charSequence, boolean z) {
        int countAsciiSeptets = countAsciiSeptets(charSequence, z);
        TextEncodingDetails calculateLength;
        if (countAsciiSeptets == -1 || countAsciiSeptets > 160) {
            calculateLength = SmsMessage.calculateLength(charSequence, z);
            if (calculateLength.msgCount != 1 || calculateLength.codeUnitSize != 1) {
                return calculateLength;
            }
            calculateLength.codeUnitCount = charSequence.length();
            int i = calculateLength.codeUnitCount * 2;
            if (i > 140) {
                countAsciiSeptets = 134;
                if (!android.telephony.SmsMessage.hasEmsSupport() && i <= 1188) {
                    countAsciiSeptets = 132;
                }
                calculateLength.msgCount = ((countAsciiSeptets - 1) + i) / countAsciiSeptets;
                calculateLength.codeUnitsRemaining = ((countAsciiSeptets * calculateLength.msgCount) - i) / 2;
            } else {
                calculateLength.msgCount = 1;
                calculateLength.codeUnitsRemaining = (140 - i) / 2;
            }
            calculateLength.codeUnitSize = 3;
            return calculateLength;
        }
        calculateLength = new TextEncodingDetails();
        calculateLength.msgCount = 1;
        calculateLength.codeUnitCount = countAsciiSeptets;
        calculateLength.codeUnitsRemaining = 160 - countAsciiSeptets;
        calculateLength.codeUnitSize = 1;
        return calculateLength;
    }

    private static int countAsciiSeptets(CharSequence charSequence, boolean z) {
        int length = charSequence.length();
        if (z) {
            return length;
        }
        for (int i = 0; i < length; i++) {
            if (UserData.charToAscii.get(charSequence.charAt(i), -1) == -1) {
                return -1;
            }
        }
        return length;
    }

    public static BearerData decode(byte[] bArr) {
        return decode(bArr, 0);
    }

    public static BearerData decode(byte[] bArr, int i) {
        try {
            BitwiseInputStream bitwiseInputStream = new BitwiseInputStream(bArr);
            BearerData bearerData = new BearerData();
            int i2 = 0;
            while (bitwiseInputStream.available() > 0) {
                int read = bitwiseInputStream.read(8);
                int i3 = 1 << read;
                if ((i2 & i3) == 0 || read < 0 || read > 23) {
                    boolean decodeMessageId;
                    switch (read) {
                        case 0:
                            decodeMessageId = decodeMessageId(bearerData, bitwiseInputStream);
                            break;
                        case 1:
                            decodeMessageId = decodeUserData(bearerData, bitwiseInputStream);
                            break;
                        case 2:
                            decodeMessageId = decodeUserResponseCode(bearerData, bitwiseInputStream);
                            break;
                        case 3:
                            decodeMessageId = decodeMsgCenterTimeStamp(bearerData, bitwiseInputStream);
                            break;
                        case 4:
                            decodeMessageId = decodeValidityAbs(bearerData, bitwiseInputStream);
                            break;
                        case 5:
                            decodeMessageId = decodeValidityRel(bearerData, bitwiseInputStream);
                            break;
                        case 6:
                            decodeMessageId = decodeDeferredDeliveryAbs(bearerData, bitwiseInputStream);
                            break;
                        case 7:
                            decodeMessageId = decodeDeferredDeliveryRel(bearerData, bitwiseInputStream);
                            break;
                        case 8:
                            decodeMessageId = decodePriorityIndicator(bearerData, bitwiseInputStream);
                            break;
                        case 9:
                            decodeMessageId = decodePrivacyIndicator(bearerData, bitwiseInputStream);
                            break;
                        case 10:
                            decodeMessageId = decodeReplyOption(bearerData, bitwiseInputStream);
                            break;
                        case 11:
                            decodeMessageId = decodeMsgCount(bearerData, bitwiseInputStream);
                            break;
                        case 12:
                            decodeMessageId = decodeMsgDeliveryAlert(bearerData, bitwiseInputStream);
                            break;
                        case 13:
                            decodeMessageId = decodeLanguageIndicator(bearerData, bitwiseInputStream);
                            break;
                        case 14:
                            decodeMessageId = decodeCallbackNumber(bearerData, bitwiseInputStream);
                            break;
                        case 15:
                            decodeMessageId = decodeDisplayMode(bearerData, bitwiseInputStream);
                            break;
                        case 17:
                            decodeMessageId = decodeDepositIndex(bearerData, bitwiseInputStream);
                            break;
                        case 18:
                            decodeMessageId = decodeServiceCategoryProgramData(bearerData, bitwiseInputStream);
                            break;
                        case 20:
                            decodeMessageId = decodeMsgStatus(bearerData, bitwiseInputStream);
                            break;
                        default:
                            decodeMessageId = decodeReserved(bearerData, bitwiseInputStream, read);
                            break;
                    }
                    if (decodeMessageId && read >= 0 && read <= 23) {
                        i2 |= i3;
                    }
                } else {
                    throw new CodingException("illegal duplicate subparameter (" + read + ")");
                }
            }
            if ((i2 & 1) == 0) {
                throw new CodingException("missing MESSAGE_IDENTIFIER subparam");
            } else if (bearerData.userData == null) {
                return bearerData;
            } else {
                if (isCmasAlertCategory(i)) {
                    decodeCmasUserData(bearerData, i);
                    return bearerData;
                } else if (bearerData.userData.msgEncoding == 1) {
                    if (((i2 ^ 1) ^ 2) != 0) {
                        Rlog.e(LOG_TAG, "IS-91 must occur without extra subparams (" + i2 + ")");
                    }
                    decodeIs91(bearerData);
                    return bearerData;
                } else {
                    decodeUserDataPayload(bearerData.userData, bearerData.hasUserDataHeader);
                    return bearerData;
                }
            }
        } catch (AccessException e) {
            Rlog.e(LOG_TAG, "BearerData decode failed: " + e);
            return null;
        } catch (CodingException e2) {
            Rlog.e(LOG_TAG, "BearerData decode failed: " + e2);
            return null;
        }
    }

    private static String decode7bitAscii(byte[] bArr, int i, int i2) throws CodingException {
        int i3 = i * 8;
        try {
            int i4 = (i3 + 6) / 7;
            int i5 = i2 - i4;
            StringBuffer stringBuffer = new StringBuffer(i5);
            BitwiseInputStream bitwiseInputStream = new BitwiseInputStream(bArr);
            int i6 = (i4 * 7) + (i5 * 7);
            if (bitwiseInputStream.available() < i6) {
                throw new CodingException("insufficient data (wanted " + i6 + " bits, but only have " + bitwiseInputStream.available() + ")");
            }
            bitwiseInputStream.skip(i3 + ((i4 * 7) - i3));
            for (i3 = 0; i3 < i5; i3++) {
                i4 = bitwiseInputStream.read(7);
                if (i4 >= 32 && i4 <= UserData.ASCII_MAP_MAX_INDEX) {
                    stringBuffer.append(UserData.ASCII_MAP[i4 - 32]);
                } else if (i4 == 10) {
                    stringBuffer.append(10);
                } else if (i4 == 13) {
                    stringBuffer.append(13);
                } else {
                    stringBuffer.append(' ');
                }
            }
            return stringBuffer.toString();
        } catch (AccessException e) {
            throw new CodingException("7bit ASCII decode failed: " + e);
        }
    }

    private static String decode7bitGsm(byte[] bArr, int i, int i2) throws CodingException {
        int i3 = i * 8;
        int i4 = (i3 + 6) / 7;
        String gsm7BitPackedToString = GsmAlphabet.gsm7BitPackedToString(bArr, i, i2 - i4, (i4 * 7) - i3, 0, 0);
        if (gsm7BitPackedToString != null) {
            return gsm7BitPackedToString;
        }
        throw new CodingException("7bit GSM decoding failed");
    }

    private static boolean decodeCallbackNumber(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException, CodingException {
        int i = 4;
        int read = bitwiseInputStream.read(8) * 8;
        if (read < 8) {
            bitwiseInputStream.skip(read);
            return false;
        }
        int i2;
        CdmaSmsAddress cdmaSmsAddress = new CdmaSmsAddress();
        cdmaSmsAddress.digitMode = bitwiseInputStream.read(1);
        if (cdmaSmsAddress.digitMode == 1) {
            cdmaSmsAddress.ton = bitwiseInputStream.read(3);
            cdmaSmsAddress.numberPlan = bitwiseInputStream.read(4);
            i2 = (byte) 8;
            i = 8;
        } else {
            i2 = 1;
        }
        cdmaSmsAddress.numberOfDigits = bitwiseInputStream.read(8);
        i2 = read - ((byte) (i2 + 8));
        i *= cdmaSmsAddress.numberOfDigits;
        int i3 = i2 - i;
        if (i2 < i) {
            throw new CodingException("CALLBACK_NUMBER subparam encoding size error (remainingBits + " + i2 + ", dataBits + " + i + ", paddingBits + " + i3 + ")");
        }
        cdmaSmsAddress.origBytes = bitwiseInputStream.readByteArray(i);
        bitwiseInputStream.skip(i3);
        decodeSmsAddress(cdmaSmsAddress);
        bearerData.callbackNumber = cdmaSmsAddress;
        return true;
    }

    private static String decodeCharset(byte[] bArr, int i, int i2, int i3, String str) throws CodingException {
        if (i2 < 0 || (i2 * i3) + i > bArr.length) {
            int length = ((bArr.length - i) - (i % i3)) / i3;
            if (length < 0) {
                throw new CodingException(str + " decode failed: offset out of range");
            }
            Rlog.e(LOG_TAG, str + " decode error: offset = " + i + " numFields = " + i2 + " data.length = " + bArr.length + " maxNumFields = " + length);
            i2 = length;
        }
        try {
            return new String(bArr, i, i2 * i3, str);
        } catch (UnsupportedEncodingException e) {
            throw new CodingException(str + " decode failed: " + e);
        }
    }

    private static void decodeCmasUserData(BearerData bearerData, int i) throws AccessException, CodingException {
        BitwiseInputStream bitwiseInputStream = new BitwiseInputStream(bearerData.userData.payload);
        if (bitwiseInputStream.available() < 8) {
            throw new CodingException("emergency CB with no CMAE_protocol_version");
        }
        int read = bitwiseInputStream.read(8);
        if (read != 0) {
            throw new CodingException("unsupported CMAE_protocol_version " + read);
        }
        int serviceCategoryToCmasMessageClass = serviceCategoryToCmasMessageClass(i);
        int i2 = -1;
        int i3 = -1;
        int i4 = -1;
        int i5 = -1;
        int i6 = -1;
        while (bitwiseInputStream.available() >= 16) {
            read = bitwiseInputStream.read(8);
            int read2 = bitwiseInputStream.read(8);
            switch (read) {
                case 0:
                    UserData userData = new UserData();
                    userData.msgEncoding = bitwiseInputStream.read(5);
                    userData.msgEncodingSet = true;
                    userData.msgType = 0;
                    switch (userData.msgEncoding) {
                        case 0:
                        case 8:
                            read = read2 - 1;
                            break;
                        case 2:
                        case 3:
                        case 9:
                            read = ((read2 * 8) - 5) / 7;
                            break;
                        case 4:
                            read = (read2 - 1) / 2;
                            break;
                        default:
                            read = 0;
                            break;
                    }
                    userData.numFields = read;
                    userData.payload = bitwiseInputStream.readByteArray((read2 * 8) - 5);
                    decodeUserDataPayload(userData, false);
                    bearerData.userData = userData;
                    break;
                case 1:
                    i2 = bitwiseInputStream.read(8);
                    i3 = bitwiseInputStream.read(8);
                    i4 = bitwiseInputStream.read(4);
                    i5 = bitwiseInputStream.read(4);
                    i6 = bitwiseInputStream.read(4);
                    bitwiseInputStream.skip((read2 * 8) - 28);
                    break;
                default:
                    Rlog.w(LOG_TAG, "skipping unsupported CMAS record type " + read);
                    bitwiseInputStream.skip(read2 * 8);
                    break;
            }
        }
        bearerData.cmasWarningInfo = new SmsCbCmasInfo(serviceCategoryToCmasMessageClass, i2, i3, i4, i5, i6);
    }

    private static boolean decodeDeferredDeliveryAbs(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        boolean z = false;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 48) {
            read -= 48;
            z = true;
            bearerData.deferredDeliveryTimeAbsolute = TimeStamp.fromByteArray(bitwiseInputStream.readByteArray(48));
        }
        boolean z2 = z;
        if (!z2 || read > 0) {
            Rlog.d(LOG_TAG, "DEFERRED_DELIVERY_TIME_ABSOLUTE decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + read + ")");
        }
        bitwiseInputStream.skip(read);
        return z2;
    }

    private static boolean decodeDeferredDeliveryRel(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        boolean z = false;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 8) {
            read -= 8;
            z = true;
            bearerData.validityPeriodRelative = bitwiseInputStream.read(8);
        }
        boolean z2 = z;
        if (!z2 || read > 0) {
            Rlog.d(LOG_TAG, "DEFERRED_DELIVERY_TIME_RELATIVE decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + read + ")");
        }
        bitwiseInputStream.skip(read);
        bearerData.validityPeriodRelativeSet = z2;
        return z2;
    }

    private static boolean decodeDepositIndex(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        boolean z = false;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 16) {
            read -= 16;
            z = true;
            bearerData.depositIndex = (bitwiseInputStream.read(8) << 8) | bitwiseInputStream.read(8);
        }
        boolean z2 = z;
        if (!z2 || read > 0) {
            Rlog.d(LOG_TAG, "MESSAGE_DEPOSIT_INDEX decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + read + ")");
        }
        bitwiseInputStream.skip(read);
        return z2;
    }

    private static boolean decodeDisplayMode(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        boolean z = false;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 8) {
            read -= 8;
            z = true;
            bearerData.displayMode = bitwiseInputStream.read(2);
            bitwiseInputStream.skip(6);
        }
        boolean z2 = z;
        if (!z2 || read > 0) {
            Rlog.d(LOG_TAG, "DISPLAY_MODE decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + read + ")");
        }
        bitwiseInputStream.skip(read);
        bearerData.displayModeSet = z2;
        return z2;
    }

    private static String decodeDtmfSmsAddress(byte[] bArr, int i) throws CodingException {
        StringBuffer stringBuffer = new StringBuffer(i);
        for (int i2 = 0; i2 < i; i2++) {
            int i3 = (bArr[i2 / 2] >>> (4 - ((i2 % 2) * 4))) & 15;
            if (i3 >= 1 && i3 <= 9) {
                stringBuffer.append(Integer.toString(i3, 10));
            } else if (i3 == 10) {
                stringBuffer.append('0');
            } else if (i3 == 11) {
                stringBuffer.append('*');
            } else if (i3 == 12) {
                stringBuffer.append('#');
            } else {
                throw new CodingException("invalid SMS address DTMF code (" + i3 + ")");
            }
        }
        return stringBuffer.toString();
    }

    private static String decodeGsmDcs(byte[] bArr, int i, int i2, int i3) throws CodingException {
        switch ((i3 >> 2) & 3) {
            case 0:
                return decode7bitGsm(bArr, i, i2);
            case 1:
                return decodeUtf8(bArr, i, i2);
            case 2:
                return decodeUtf16(bArr, i, i2);
            default:
                throw new CodingException("unsupported user msgType encoding (" + i3 + ")");
        }
    }

    private static void decodeIs91(BearerData bearerData) throws AccessException, CodingException {
        switch (bearerData.userData.msgType) {
            case 130:
                decodeIs91VoicemailStatus(bearerData);
                return;
            case 131:
            case 133:
                decodeIs91ShortMessage(bearerData);
                return;
            case 132:
                decodeIs91Cli(bearerData);
                return;
            default:
                throw new CodingException("unsupported IS-91 message type (" + bearerData.userData.msgType + ")");
        }
    }

    private static void decodeIs91Cli(BearerData bearerData) throws CodingException {
        int available = new BitwiseInputStream(bearerData.userData.payload).available() / 4;
        int i = bearerData.userData.numFields;
        if (available > 14 || available < 3 || available < i) {
            throw new CodingException("IS-91 voicemail status decoding failed");
        }
        CdmaSmsAddress cdmaSmsAddress = new CdmaSmsAddress();
        cdmaSmsAddress.digitMode = 0;
        cdmaSmsAddress.origBytes = bearerData.userData.payload;
        cdmaSmsAddress.numberOfDigits = (byte) i;
        decodeSmsAddress(cdmaSmsAddress);
        bearerData.callbackNumber = cdmaSmsAddress;
    }

    private static void decodeIs91ShortMessage(BearerData bearerData) throws AccessException, CodingException {
        BitwiseInputStream bitwiseInputStream = new BitwiseInputStream(bearerData.userData.payload);
        int available = bitwiseInputStream.available() / 6;
        int i = bearerData.userData.numFields;
        if (i > 14 || available < i) {
            throw new CodingException("IS-91 short message decoding failed");
        }
        StringBuffer stringBuffer = new StringBuffer(available);
        for (available = 0; available < i; available++) {
            stringBuffer.append(UserData.ASCII_MAP[bitwiseInputStream.read(6)]);
        }
        bearerData.userData.payloadStr = stringBuffer.toString();
    }

    private static void decodeIs91VoicemailStatus(BearerData bearerData) throws AccessException, CodingException {
        BitwiseInputStream bitwiseInputStream = new BitwiseInputStream(bearerData.userData.payload);
        int available = bitwiseInputStream.available() / 6;
        int i = bearerData.userData.numFields;
        if (available > 14 || available < 3 || available < i) {
            throw new CodingException("IS-91 voicemail status decoding failed");
        }
        try {
            StringBuffer stringBuffer = new StringBuffer(available);
            while (bitwiseInputStream.available() >= 6) {
                stringBuffer.append(UserData.ASCII_MAP[bitwiseInputStream.read(6)]);
            }
            String stringBuffer2 = stringBuffer.toString();
            bearerData.numberOfMessages = Integer.parseInt(stringBuffer2.substring(0, 2));
            char charAt = stringBuffer2.charAt(2);
            if (charAt == ' ') {
                bearerData.priority = 0;
            } else if (charAt == '!') {
                bearerData.priority = 2;
            } else {
                throw new CodingException("IS-91 voicemail status decoding failed: illegal priority setting (" + charAt + ")");
            }
            bearerData.priorityIndicatorSet = true;
            bearerData.userData.payloadStr = stringBuffer2.substring(3, i - 3);
        } catch (NumberFormatException e) {
            throw new CodingException("IS-91 voicemail status decoding failed: " + e);
        } catch (IndexOutOfBoundsException e2) {
            throw new CodingException("IS-91 voicemail status decoding failed: " + e2);
        }
    }

    private static boolean decodeLanguageIndicator(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        boolean z = false;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 8) {
            read -= 8;
            z = true;
            bearerData.language = bitwiseInputStream.read(8);
        }
        boolean z2 = z;
        if (!z2 || read > 0) {
            Rlog.d(LOG_TAG, "LANGUAGE_INDICATOR decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + read + ")");
        }
        bitwiseInputStream.skip(read);
        bearerData.languageIndicatorSet = z2;
        return z2;
    }

    private static String decodeLatin(byte[] bArr, int i, int i2) throws CodingException {
        return decodeCharset(bArr, i, i2, 1, "ISO-8859-1");
    }

    private static boolean decodeMessageId(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        boolean z = false;
        boolean z2 = true;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 24) {
            read -= 24;
            bearerData.messageType = bitwiseInputStream.read(4);
            bearerData.messageId = bitwiseInputStream.read(8) << 8;
            bearerData.messageId |= bitwiseInputStream.read(8);
            if (bitwiseInputStream.read(1) == 1) {
                z = true;
            }
            bearerData.hasUserDataHeader = z;
            bitwiseInputStream.skip(3);
        } else {
            z2 = false;
        }
        if (!z2 || read > 0) {
            Rlog.d(LOG_TAG, "MESSAGE_IDENTIFIER decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + read + ")");
        }
        bitwiseInputStream.skip(read);
        return z2;
    }

    private static boolean decodeMsgCenterTimeStamp(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        boolean z = false;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 48) {
            read -= 48;
            z = true;
            bearerData.msgCenterTimeStamp = TimeStamp.fromByteArray(bitwiseInputStream.readByteArray(48));
        }
        boolean z2 = z;
        if (!z2 || read > 0) {
            Rlog.d(LOG_TAG, "MESSAGE_CENTER_TIME_STAMP decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + read + ")");
        }
        bitwiseInputStream.skip(read);
        return z2;
    }

    private static boolean decodeMsgCount(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        boolean z = false;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 8) {
            read -= 8;
            z = true;
            bearerData.numberOfMessages = IccUtils.cdmaBcdByteToInt((byte) bitwiseInputStream.read(8));
        }
        boolean z2 = z;
        if (!z2 || read > 0) {
            Rlog.d(LOG_TAG, "NUMBER_OF_MESSAGES decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + read + ")");
        }
        bitwiseInputStream.skip(read);
        return z2;
    }

    private static boolean decodeMsgDeliveryAlert(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        boolean z = false;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 8) {
            read -= 8;
            z = true;
            bearerData.alert = bitwiseInputStream.read(2);
            bitwiseInputStream.skip(6);
        }
        boolean z2 = z;
        if (!z2 || read > 0) {
            Rlog.d(LOG_TAG, "ALERT_ON_MESSAGE_DELIVERY decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + read + ")");
        }
        bitwiseInputStream.skip(read);
        bearerData.alertIndicatorSet = z2;
        return z2;
    }

    private static boolean decodeMsgStatus(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        boolean z = false;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 8) {
            read -= 8;
            z = true;
            bearerData.errorClass = bitwiseInputStream.read(2);
            bearerData.messageStatus = bitwiseInputStream.read(6);
        }
        boolean z2 = z;
        if (!z2 || read > 0) {
            Rlog.d(LOG_TAG, "MESSAGE_STATUS decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + read + ")");
        }
        bitwiseInputStream.skip(read);
        bearerData.messageStatusSet = z2;
        return z2;
    }

    private static boolean decodePriorityIndicator(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        boolean z = false;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 8) {
            read -= 8;
            z = true;
            bearerData.priority = bitwiseInputStream.read(2);
            bitwiseInputStream.skip(6);
        }
        boolean z2 = z;
        if (!z2 || read > 0) {
            Rlog.d(LOG_TAG, "PRIORITY_INDICATOR decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + read + ")");
        }
        bitwiseInputStream.skip(read);
        bearerData.priorityIndicatorSet = z2;
        return z2;
    }

    private static boolean decodePrivacyIndicator(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        boolean z = false;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 8) {
            read -= 8;
            z = true;
            bearerData.privacy = bitwiseInputStream.read(2);
            bitwiseInputStream.skip(6);
        }
        boolean z2 = z;
        if (!z2 || read > 0) {
            Rlog.d(LOG_TAG, "PRIVACY_INDICATOR decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + read + ")");
        }
        bitwiseInputStream.skip(read);
        bearerData.privacyIndicatorSet = z2;
        return z2;
    }

    private static boolean decodeReplyOption(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        int i;
        boolean z = false;
        boolean z2 = true;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 8) {
            i = read - 8;
            bearerData.userAckReq = bitwiseInputStream.read(1) == 1;
            bearerData.deliveryAckReq = bitwiseInputStream.read(1) == 1;
            bearerData.readAckReq = bitwiseInputStream.read(1) == 1;
            if (bitwiseInputStream.read(1) == 1) {
                z = true;
            }
            bearerData.reportReq = z;
            bitwiseInputStream.skip(4);
        } else {
            z2 = false;
            i = read;
        }
        if (!z2 || i > 0) {
            Rlog.d(LOG_TAG, "REPLY_OPTION decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + i + ")");
        }
        bitwiseInputStream.skip(i);
        return z2;
    }

    private static boolean decodeReserved(BearerData bearerData, BitwiseInputStream bitwiseInputStream, int i) throws AccessException, CodingException {
        boolean z = false;
        int read = bitwiseInputStream.read(8);
        int i2 = read * 8;
        if (i2 <= bitwiseInputStream.available()) {
            z = true;
            bitwiseInputStream.skip(i2);
        }
        Rlog.d(LOG_TAG, "RESERVED bearer data subparameter " + i + " decode " + (z ? "succeeded" : "failed") + " (param bits = " + i2 + ")");
        if (z) {
            return z;
        }
        throw new CodingException("RESERVED bearer data subparameter " + i + " had invalid SUBPARAM_LEN " + read);
    }

    private static boolean decodeServiceCategoryProgramData(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException, CodingException {
        if (bitwiseInputStream.available() < 13) {
            throw new CodingException("SERVICE_CATEGORY_PROGRAM_DATA decode failed: only " + bitwiseInputStream.available() + " bits available");
        }
        int read = bitwiseInputStream.read(8);
        int read2 = bitwiseInputStream.read(5);
        int i = (read * 8) - 5;
        if (bitwiseInputStream.available() < i) {
            throw new CodingException("SERVICE_CATEGORY_PROGRAM_DATA decode failed: only " + bitwiseInputStream.available() + " bits available (" + i + " bits expected)");
        }
        ArrayList arrayList = new ArrayList();
        boolean z = false;
        while (i >= 48) {
            int read3 = bitwiseInputStream.read(4);
            int read4 = bitwiseInputStream.read(8);
            int read5 = bitwiseInputStream.read(8);
            int read6 = bitwiseInputStream.read(8);
            int read7 = bitwiseInputStream.read(8);
            int read8 = bitwiseInputStream.read(4);
            read = bitwiseInputStream.read(8);
            i -= 48;
            int bitsForNumFields = getBitsForNumFields(read2, read);
            if (i < bitsForNumFields) {
                throw new CodingException("category name is " + bitsForNumFields + " bits in length," + " but there are only " + i + " bits available");
            }
            UserData userData = new UserData();
            userData.msgEncoding = read2;
            userData.msgEncodingSet = true;
            userData.numFields = read;
            userData.payload = bitwiseInputStream.readByteArray(bitsForNumFields);
            bitsForNumFields = i - bitsForNumFields;
            decodeUserDataPayload(userData, false);
            arrayList.add(new CdmaSmsCbProgramData(read3, (read4 << 8) | read5, read6, read7, read8, userData.payloadStr));
            z = true;
            i = bitsForNumFields;
        }
        if (!z || i > 0) {
            Rlog.d(LOG_TAG, "SERVICE_CATEGORY_PROGRAM_DATA decode " + (z ? "succeeded" : "failed") + " (extra bits = " + i + ')');
        }
        bitwiseInputStream.skip(i);
        bearerData.serviceCategoryProgramData = arrayList;
        return z;
    }

    private static String decodeShiftJis(byte[] bArr, int i, int i2) throws CodingException {
        return decodeCharset(bArr, i, i2, 1, "Shift_JIS");
    }

    private static void decodeSmsAddress(CdmaSmsAddress cdmaSmsAddress) throws CodingException {
        if (cdmaSmsAddress.digitMode == 1) {
            try {
                cdmaSmsAddress.address = new String(cdmaSmsAddress.origBytes, 0, cdmaSmsAddress.origBytes.length, "US-ASCII");
                return;
            } catch (UnsupportedEncodingException e) {
                throw new CodingException("invalid SMS address ASCII code");
            }
        }
        cdmaSmsAddress.address = decodeDtmfSmsAddress(cdmaSmsAddress.origBytes, cdmaSmsAddress.numberOfDigits);
    }

    private static boolean decodeUserData(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        int i = 5;
        int read = bitwiseInputStream.read(8);
        bearerData.userData = new UserData();
        bearerData.userData.msgEncoding = bitwiseInputStream.read(5);
        bearerData.userData.msgEncodingSet = true;
        bearerData.userData.msgType = 0;
        if (bearerData.userData.msgEncoding == 1 || bearerData.userData.msgEncoding == 10) {
            bearerData.userData.msgType = bitwiseInputStream.read(8);
            i = 13;
        }
        bearerData.userData.numFields = bitwiseInputStream.read(8);
        bearerData.userData.payload = bitwiseInputStream.readByteArray((read * 8) - (i + 8));
        return true;
    }

    private static void decodeUserDataPayload(UserData userData, boolean z) throws CodingException {
        int i;
        if (z) {
            int i2 = userData.payload[0] & 255;
            i = (i2 + 1) + 0;
            byte[] bArr = new byte[i2];
            System.arraycopy(userData.payload, 1, bArr, 0, i2);
            userData.userDataHeader = SmsHeader.fromByteArray(bArr);
        } else {
            i = 0;
        }
        switch (userData.msgEncoding) {
            case 0:
                boolean z2 = Resources.getSystem().getBoolean(17956956);
                byte[] bArr2 = new byte[userData.numFields];
                System.arraycopy(userData.payload, 0, bArr2, 0, userData.numFields < userData.payload.length ? userData.numFields : userData.payload.length);
                userData.payload = bArr2;
                if (z2) {
                    userData.payloadStr = decodeUtf8(userData.payload, i, userData.numFields);
                    return;
                } else {
                    userData.payloadStr = decodeLatin(userData.payload, i, userData.numFields);
                    return;
                }
            case 2:
            case 3:
                userData.payloadStr = decode7bitAscii(userData.payload, i, userData.numFields);
                return;
            case 4:
                userData.payloadStr = decodeUtf16(userData.payload, i, userData.numFields);
                return;
            case 5:
                userData.payloadStr = decodeShiftJis(userData.payload, i, userData.numFields);
                return;
            case 8:
                userData.payloadStr = decodeLatin(userData.payload, i, userData.numFields);
                return;
            case 9:
                userData.payloadStr = decode7bitGsm(userData.payload, i, userData.numFields);
                return;
            case 10:
                userData.payloadStr = decodeGsmDcs(userData.payload, i, userData.numFields, userData.msgType);
                return;
            default:
                throw new CodingException("unsupported user data encoding (" + userData.msgEncoding + ")");
        }
    }

    private static boolean decodeUserResponseCode(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        boolean z = false;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 8) {
            read -= 8;
            z = true;
            bearerData.userResponseCode = bitwiseInputStream.read(8);
        }
        boolean z2 = z;
        if (!z2 || read > 0) {
            Rlog.d(LOG_TAG, "USER_RESPONSE_CODE decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + read + ")");
        }
        bitwiseInputStream.skip(read);
        bearerData.userResponseCodeSet = z2;
        return z2;
    }

    private static String decodeUtf16(byte[] bArr, int i, int i2) throws CodingException {
        return decodeCharset(bArr, i, i2 - (((i % 2) + i) / 2), 2, "utf-16be");
    }

    private static String decodeUtf8(byte[] bArr, int i, int i2) throws CodingException {
        return decodeCharset(bArr, i, i2, 1, "UTF-8");
    }

    private static boolean decodeValidityAbs(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        boolean z = false;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 48) {
            read -= 48;
            z = true;
            bearerData.validityPeriodAbsolute = TimeStamp.fromByteArray(bitwiseInputStream.readByteArray(48));
        }
        boolean z2 = z;
        if (!z2 || read > 0) {
            Rlog.d(LOG_TAG, "VALIDITY_PERIOD_ABSOLUTE decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + read + ")");
        }
        bitwiseInputStream.skip(read);
        return z2;
    }

    private static boolean decodeValidityRel(BearerData bearerData, BitwiseInputStream bitwiseInputStream) throws AccessException {
        boolean z = false;
        int read = bitwiseInputStream.read(8) * 8;
        if (read >= 8) {
            read -= 8;
            z = true;
            bearerData.deferredDeliveryTimeRelative = bitwiseInputStream.read(8);
        }
        boolean z2 = z;
        if (!z2 || read > 0) {
            Rlog.d(LOG_TAG, "VALIDITY_PERIOD_RELATIVE decode " + (z2 ? "succeeded" : "failed") + " (extra bits = " + read + ")");
        }
        bitwiseInputStream.skip(read);
        bearerData.deferredDeliveryTimeRelativeSet = z2;
        return z2;
    }

    public static byte[] encode(BearerData bearerData) {
        boolean z = true;
        if (bearerData.userData == null || bearerData.userData.userDataHeader == null) {
            z = false;
        }
        bearerData.hasUserDataHeader = z;
        try {
            BitwiseOutputStream bitwiseOutputStream = new BitwiseOutputStream(PduPart.P_CONTENT_TRANSFER_ENCODING);
            bitwiseOutputStream.write(8, 0);
            encodeMessageId(bearerData, bitwiseOutputStream);
            if (bearerData.userData != null) {
                bitwiseOutputStream.write(8, 1);
                encodeUserData(bearerData, bitwiseOutputStream);
            }
            if (bearerData.callbackNumber != null) {
                bitwiseOutputStream.write(8, 14);
                encodeCallbackNumber(bearerData, bitwiseOutputStream);
            }
            if (bearerData.userAckReq || bearerData.deliveryAckReq || bearerData.readAckReq || bearerData.reportReq) {
                bitwiseOutputStream.write(8, 10);
                encodeReplyOption(bearerData, bitwiseOutputStream);
            }
            if (bearerData.numberOfMessages != 0) {
                bitwiseOutputStream.write(8, 11);
                encodeMsgCount(bearerData, bitwiseOutputStream);
            }
            if (bearerData.validityPeriodRelativeSet) {
                bitwiseOutputStream.write(8, 5);
                encodeValidityPeriodRel(bearerData, bitwiseOutputStream);
            }
            if (bearerData.privacyIndicatorSet) {
                bitwiseOutputStream.write(8, 9);
                encodePrivacyIndicator(bearerData, bitwiseOutputStream);
            }
            if (bearerData.languageIndicatorSet) {
                bitwiseOutputStream.write(8, 13);
                encodeLanguageIndicator(bearerData, bitwiseOutputStream);
            }
            if (bearerData.displayModeSet) {
                bitwiseOutputStream.write(8, 15);
                encodeDisplayMode(bearerData, bitwiseOutputStream);
            }
            if (bearerData.priorityIndicatorSet) {
                bitwiseOutputStream.write(8, 8);
                encodePriorityIndicator(bearerData, bitwiseOutputStream);
            }
            if (bearerData.alertIndicatorSet) {
                bitwiseOutputStream.write(8, 12);
                encodeMsgDeliveryAlert(bearerData, bitwiseOutputStream);
            }
            if (bearerData.messageStatusSet) {
                bitwiseOutputStream.write(8, 20);
                encodeMsgStatus(bearerData, bitwiseOutputStream);
            }
            if (bearerData.serviceCategoryProgramResults != null) {
                bitwiseOutputStream.write(8, 19);
                encodeScpResults(bearerData, bitwiseOutputStream);
            }
            return bitwiseOutputStream.toByteArray();
        } catch (BitwiseOutputStream.AccessException e) {
            Rlog.e(LOG_TAG, "BearerData encode failed: " + e);
            return null;
        } catch (CodingException e2) {
            Rlog.e(LOG_TAG, "BearerData encode failed: " + e2);
            return null;
        }
    }

    private static void encode16bitEms(UserData userData, byte[] bArr) throws CodingException {
        byte[] encodeUtf16 = encodeUtf16(userData.payloadStr);
        int length = bArr.length + 1;
        int i = (length + 1) / 2;
        int length2 = encodeUtf16.length / 2;
        userData.msgEncoding = 4;
        userData.msgEncodingSet = true;
        userData.numFields = i + length2;
        userData.payload = new byte[(userData.numFields * 2)];
        userData.payload[0] = (byte) bArr.length;
        System.arraycopy(bArr, 0, userData.payload, 1, bArr.length);
        System.arraycopy(encodeUtf16, 0, userData.payload, length, encodeUtf16.length);
    }

    private static byte[] encode7bitAscii(String str, boolean z) throws CodingException {
        try {
            BitwiseOutputStream bitwiseOutputStream = new BitwiseOutputStream(str.length());
            int length = str.length();
            for (int i = 0; i < length; i++) {
                int i2 = UserData.charToAscii.get(str.charAt(i), -1);
                if (i2 != -1) {
                    bitwiseOutputStream.write(7, i2);
                } else if (z) {
                    bitwiseOutputStream.write(7, 32);
                } else {
                    throw new CodingException("cannot ASCII encode (" + str.charAt(i) + ")");
                }
            }
            return bitwiseOutputStream.toByteArray();
        } catch (BitwiseOutputStream.AccessException e) {
            throw new CodingException("7bit ASCII encode failed: " + e);
        }
    }

    private static void encode7bitAsciiEms(UserData userData, byte[] bArr, boolean z) throws CodingException {
        int i = 1;
        int i2 = 0;
        try {
            Rlog.d(LOG_TAG, "encode7bitAsciiEms");
            int length = bArr.length + 1;
            int i3 = ((length * 8) + 6) / 7;
            int i4 = (i3 * 7) - (length * 8);
            String str = userData.payloadStr;
            int length2 = str.length();
            if (i4 <= 0) {
                i = 0;
            }
            BitwiseOutputStream bitwiseOutputStream = new BitwiseOutputStream(i + length2);
            bitwiseOutputStream.write(i4, 0);
            while (i2 < length2) {
                i = UserData.charToAscii.get(str.charAt(i2), -1);
                if (i != -1) {
                    bitwiseOutputStream.write(7, i);
                } else if (z) {
                    bitwiseOutputStream.write(7, 32);
                } else {
                    throw new CodingException("cannot ASCII encode (" + str.charAt(i2) + ")");
                }
                i2++;
            }
            byte[] toByteArray = bitwiseOutputStream.toByteArray();
            userData.msgEncoding = 2;
            userData.msgEncodingSet = true;
            userData.numFields = userData.payloadStr.length() + i3;
            userData.payload = new byte[(toByteArray.length + length)];
            userData.payload[0] = (byte) bArr.length;
            System.arraycopy(bArr, 0, userData.payload, 1, bArr.length);
            System.arraycopy(toByteArray, 0, userData.payload, length, toByteArray.length);
        } catch (BitwiseOutputStream.AccessException e) {
            throw new CodingException("7bit ASCII encode failed: " + e);
        }
    }

    private static void encode7bitEms(UserData userData, byte[] bArr, boolean z) throws CodingException {
        Gsm7bitCodingResult encode7bitGsm = encode7bitGsm(userData.payloadStr, (((bArr.length + 1) * 8) + 6) / 7, z);
        userData.msgEncoding = 9;
        userData.msgEncodingSet = true;
        userData.numFields = encode7bitGsm.septets;
        userData.payload = encode7bitGsm.data;
        userData.payload[0] = (byte) bArr.length;
        System.arraycopy(bArr, 0, userData.payload, 1, bArr.length);
    }

    private static Gsm7bitCodingResult encode7bitGsm(String str, int i, boolean z) throws CodingException {
        boolean z2 = true;
        if (z) {
            z2 = false;
        }
        try {
            byte[] stringToGsm7BitPacked = GsmAlphabet.stringToGsm7BitPacked(str, i, z2, 0, 0);
            Gsm7bitCodingResult gsm7bitCodingResult = new Gsm7bitCodingResult();
            gsm7bitCodingResult.data = new byte[(stringToGsm7BitPacked.length - 1)];
            System.arraycopy(stringToGsm7BitPacked, 1, gsm7bitCodingResult.data, 0, stringToGsm7BitPacked.length - 1);
            gsm7bitCodingResult.septets = stringToGsm7BitPacked[0] & 255;
            return gsm7bitCodingResult;
        } catch (EncodeException e) {
            throw new CodingException("7bit GSM encode failed: " + e);
        }
    }

    private static void encodeCallbackNumber(BearerData bearerData, BitwiseOutputStream bitwiseOutputStream) throws BitwiseOutputStream.AccessException, CodingException {
        int i;
        CdmaSmsAddress cdmaSmsAddress = bearerData.callbackNumber;
        encodeCdmaSmsAddress(cdmaSmsAddress);
        int i2 = 9;
        if (cdmaSmsAddress.digitMode == 1) {
            i2 = 16;
            i = cdmaSmsAddress.numberOfDigits * 8;
        } else {
            i = cdmaSmsAddress.numberOfDigits * 4;
        }
        int i3 = i2 + i;
        i2 = (i3 % 8 > 0 ? 1 : 0) + (i3 / 8);
        i3 = (i2 * 8) - i3;
        bitwiseOutputStream.write(8, i2);
        bitwiseOutputStream.write(1, cdmaSmsAddress.digitMode);
        if (cdmaSmsAddress.digitMode == 1) {
            bitwiseOutputStream.write(3, cdmaSmsAddress.ton);
            bitwiseOutputStream.write(4, cdmaSmsAddress.numberPlan);
        }
        bitwiseOutputStream.write(8, cdmaSmsAddress.numberOfDigits);
        bitwiseOutputStream.writeByteArray(i, cdmaSmsAddress.origBytes);
        if (i3 > 0) {
            bitwiseOutputStream.write(i3, 0);
        }
    }

    private static void encodeCdmaSmsAddress(CdmaSmsAddress cdmaSmsAddress) throws CodingException {
        if (cdmaSmsAddress.digitMode == 1) {
            try {
                cdmaSmsAddress.origBytes = cdmaSmsAddress.address.getBytes("US-ASCII");
                return;
            } catch (UnsupportedEncodingException e) {
                throw new CodingException("invalid SMS address, cannot convert to ASCII");
            }
        }
        cdmaSmsAddress.origBytes = encodeDtmfSmsAddress(cdmaSmsAddress.address);
    }

    private static void encodeDisplayMode(BearerData bearerData, BitwiseOutputStream bitwiseOutputStream) throws BitwiseOutputStream.AccessException {
        bitwiseOutputStream.write(8, 1);
        bitwiseOutputStream.write(2, bearerData.displayMode);
        bitwiseOutputStream.skip(6);
    }

    private static byte[] encodeDtmfSmsAddress(String str) {
        int i = 0;
        int length = str.length();
        int i2 = length * 4;
        byte[] bArr = new byte[((i2 % 8 > 0 ? 1 : 0) + (i2 / 8))];
        while (i < length) {
            char charAt = str.charAt(i);
            if (charAt >= '1' && charAt <= '9') {
                i2 = charAt - 48;
            } else if (charAt == '0') {
                i2 = 10;
            } else if (charAt == '*') {
                i2 = 11;
            } else if (charAt != '#') {
                return null;
            } else {
                i2 = 12;
            }
            int i3 = i / 2;
            bArr[i3] = (byte) ((i2 << (4 - ((i % 2) * 4))) | bArr[i3]);
            i++;
        }
        return bArr;
    }

    private static void encodeEmsUserDataPayload(UserData userData) throws CodingException {
        byte[] toByteArray = SmsHeader.toByteArray(userData.userDataHeader);
        if (!userData.msgEncodingSet) {
            try {
                encode7bitEms(userData, toByteArray, false);
            } catch (CodingException e) {
                encode16bitEms(userData, toByteArray);
            }
        } else if (userData.msgEncoding == 9) {
            encode7bitEms(userData, toByteArray, true);
        } else if (userData.msgEncoding == 4) {
            encode16bitEms(userData, toByteArray);
        } else if (userData.msgEncoding == 2) {
            encode7bitAsciiEms(userData, toByteArray, true);
        } else {
            throw new CodingException("unsupported EMS user data encoding (" + userData.msgEncoding + ")");
        }
    }

    private static void encodeLanguageIndicator(BearerData bearerData, BitwiseOutputStream bitwiseOutputStream) throws BitwiseOutputStream.AccessException {
        bitwiseOutputStream.write(8, 1);
        bitwiseOutputStream.write(8, bearerData.language);
    }

    private static void encodeMessageId(BearerData bearerData, BitwiseOutputStream bitwiseOutputStream) throws BitwiseOutputStream.AccessException {
        bitwiseOutputStream.write(8, 3);
        bitwiseOutputStream.write(4, bearerData.messageType);
        bitwiseOutputStream.write(8, bearerData.messageId >> 8);
        bitwiseOutputStream.write(8, bearerData.messageId);
        bitwiseOutputStream.write(1, bearerData.hasUserDataHeader ? 1 : 0);
        bitwiseOutputStream.skip(3);
    }

    private static void encodeMsgCount(BearerData bearerData, BitwiseOutputStream bitwiseOutputStream) throws BitwiseOutputStream.AccessException {
        bitwiseOutputStream.write(8, 1);
        bitwiseOutputStream.write(8, bearerData.numberOfMessages);
    }

    private static void encodeMsgDeliveryAlert(BearerData bearerData, BitwiseOutputStream bitwiseOutputStream) throws BitwiseOutputStream.AccessException {
        bitwiseOutputStream.write(8, 1);
        bitwiseOutputStream.write(2, bearerData.alert);
        bitwiseOutputStream.skip(6);
    }

    private static void encodeMsgStatus(BearerData bearerData, BitwiseOutputStream bitwiseOutputStream) throws BitwiseOutputStream.AccessException {
        bitwiseOutputStream.write(8, 1);
        bitwiseOutputStream.write(2, bearerData.errorClass);
        bitwiseOutputStream.write(6, bearerData.messageStatus);
    }

    private static void encodePriorityIndicator(BearerData bearerData, BitwiseOutputStream bitwiseOutputStream) throws BitwiseOutputStream.AccessException {
        bitwiseOutputStream.write(8, 1);
        bitwiseOutputStream.write(2, bearerData.priority);
        bitwiseOutputStream.skip(6);
    }

    private static void encodePrivacyIndicator(BearerData bearerData, BitwiseOutputStream bitwiseOutputStream) throws BitwiseOutputStream.AccessException {
        bitwiseOutputStream.write(8, 1);
        bitwiseOutputStream.write(2, bearerData.privacy);
        bitwiseOutputStream.skip(6);
    }

    private static void encodeReplyOption(BearerData bearerData, BitwiseOutputStream bitwiseOutputStream) throws BitwiseOutputStream.AccessException {
        bitwiseOutputStream.write(8, 1);
        bitwiseOutputStream.write(1, bearerData.userAckReq ? 1 : 0);
        bitwiseOutputStream.write(1, bearerData.deliveryAckReq ? 1 : 0);
        bitwiseOutputStream.write(1, bearerData.readAckReq ? 1 : 0);
        bitwiseOutputStream.write(1, bearerData.reportReq ? 1 : 0);
        bitwiseOutputStream.write(4, 0);
    }

    private static void encodeScpResults(BearerData bearerData, BitwiseOutputStream bitwiseOutputStream) throws BitwiseOutputStream.AccessException {
        ArrayList arrayList = bearerData.serviceCategoryProgramResults;
        bitwiseOutputStream.write(8, arrayList.size() * 4);
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            CdmaSmsCbProgramResults cdmaSmsCbProgramResults = (CdmaSmsCbProgramResults) it.next();
            int category = cdmaSmsCbProgramResults.getCategory();
            bitwiseOutputStream.write(8, category >> 8);
            bitwiseOutputStream.write(8, category);
            bitwiseOutputStream.write(8, cdmaSmsCbProgramResults.getLanguage());
            bitwiseOutputStream.write(4, cdmaSmsCbProgramResults.getCategoryResult());
            bitwiseOutputStream.skip(4);
        }
    }

    private static byte[] encodeShiftJis(String str) throws CodingException {
        try {
            return str.getBytes("Shift_JIS");
        } catch (UnsupportedEncodingException e) {
            throw new CodingException("Shift-JIS encode failed: " + e);
        }
    }

    private static void encodeUserData(BearerData bearerData, BitwiseOutputStream bitwiseOutputStream) throws BitwiseOutputStream.AccessException, CodingException {
        encodeUserDataPayload(bearerData.userData);
        bearerData.hasUserDataHeader = bearerData.userData.userDataHeader != null;
        if (bearerData.userData.payload.length > 140) {
            throw new CodingException("encoded user data too large (" + bearerData.userData.payload.length + " > " + 140 + " bytes)");
        }
        int length = (bearerData.userData.payload.length * 8) - bearerData.userData.paddingBits;
        int i = length + 13;
        if (bearerData.userData.msgEncoding == 1 || bearerData.userData.msgEncoding == 10) {
            i += 8;
        }
        int i2 = (i % 8 > 0 ? 1 : 0) + (i / 8);
        i = (i2 * 8) - i;
        bitwiseOutputStream.write(8, i2);
        bitwiseOutputStream.write(5, bearerData.userData.msgEncoding);
        if (bearerData.userData.msgEncoding == 1 || bearerData.userData.msgEncoding == 10) {
            bitwiseOutputStream.write(8, bearerData.userData.msgType);
        }
        bitwiseOutputStream.write(8, bearerData.userData.numFields);
        bitwiseOutputStream.writeByteArray(length, bearerData.userData.payload);
        if (i > 0) {
            bitwiseOutputStream.write(i, 0);
        }
    }

    private static void encodeUserDataPayload(UserData userData) throws CodingException {
        if (userData.payloadStr == null && userData.msgEncoding != 0) {
            Rlog.e(LOG_TAG, "user data with null payloadStr");
            userData.payloadStr = "";
        }
        if (userData.userDataHeader != null) {
            encodeEmsUserDataPayload(userData);
        } else if (!userData.msgEncodingSet) {
            try {
                userData.payload = encode7bitAscii(userData.payloadStr, false);
                userData.msgEncoding = 2;
            } catch (CodingException e) {
                userData.payload = encodeUtf16(userData.payloadStr);
                userData.msgEncoding = 4;
            }
            userData.numFields = userData.payloadStr.length();
            userData.msgEncodingSet = true;
        } else if (userData.msgEncoding != 0) {
            if (userData.payloadStr == null) {
                Rlog.e(LOG_TAG, "non-octet user data with null payloadStr");
                userData.payloadStr = "";
            }
            if (userData.msgEncoding == 9) {
                Gsm7bitCodingResult encode7bitGsm = encode7bitGsm(userData.payloadStr, 0, true);
                userData.payload = encode7bitGsm.data;
                userData.numFields = encode7bitGsm.septets;
            } else if (userData.msgEncoding == 2) {
                userData.payload = encode7bitAscii(userData.payloadStr, true);
                userData.numFields = userData.payloadStr.length();
            } else if (userData.msgEncoding == 4) {
                userData.payload = encodeUtf16(userData.payloadStr);
                userData.numFields = userData.payloadStr.length();
            } else if (userData.msgEncoding == 5) {
                userData.payload = encodeShiftJis(userData.payloadStr);
                userData.numFields = userData.payload.length;
            } else {
                throw new CodingException("unsupported user data encoding (" + userData.msgEncoding + ")");
            }
        } else if (userData.payload == null) {
            Rlog.e(LOG_TAG, "user data with octet encoding but null payload");
            userData.payload = new byte[0];
            userData.numFields = 0;
        } else {
            userData.numFields = userData.payload.length;
        }
    }

    private static byte[] encodeUtf16(String str) throws CodingException {
        try {
            return str.getBytes("utf-16be");
        } catch (UnsupportedEncodingException e) {
            throw new CodingException("UTF-16 encode failed: " + e);
        }
    }

    private static void encodeValidityPeriodRel(BearerData bearerData, BitwiseOutputStream bitwiseOutputStream) throws BitwiseOutputStream.AccessException {
        bitwiseOutputStream.write(8, 1);
        bitwiseOutputStream.write(8, bearerData.validityPeriodRelative);
    }

    private static int getBitsForNumFields(int i, int i2) throws CodingException {
        switch (i) {
            case 0:
            case 5:
            case 6:
            case 7:
            case 8:
                return i2 * 8;
            case 2:
            case 3:
            case 9:
                return i2 * 7;
            case 4:
                return i2 * 16;
            default:
                throw new CodingException("unsupported message encoding (" + i + ')');
        }
    }

    private static String getLanguageCodeForValue(int i) {
        switch (i) {
            case 1:
                return "en";
            case 2:
                return "fr";
            case 3:
                return "es";
            case 4:
                return "ja";
            case 5:
                return "ko";
            case 6:
                return "zh";
            case 7:
                return "he";
            default:
                return null;
        }
    }

    private static boolean isCmasAlertCategory(int i) {
        return i >= 4096 && i <= SmsEnvelope.SERVICE_CATEGORY_CMAS_LAST_RESERVED_VALUE;
    }

    private static int serviceCategoryToCmasMessageClass(int i) {
        switch (i) {
            case 4096:
                return 0;
            case SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT /*4097*/:
                return 1;
            case 4098:
                return 2;
            case 4099:
                return 3;
            case 4100:
                return 4;
            default:
                return -1;
        }
    }

    public String getLanguage() {
        return getLanguageCodeForValue(this.language);
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("BearerData ");
        stringBuilder.append("{ messageType=" + this.messageType);
        stringBuilder.append(", messageId=" + this.messageId);
        stringBuilder.append(", priority=" + (this.priorityIndicatorSet ? Integer.valueOf(this.priority) : "unset"));
        stringBuilder.append(", privacy=" + (this.privacyIndicatorSet ? Integer.valueOf(this.privacy) : "unset"));
        stringBuilder.append(", alert=" + (this.alertIndicatorSet ? Integer.valueOf(this.alert) : "unset"));
        stringBuilder.append(", displayMode=" + (this.displayModeSet ? Integer.valueOf(this.displayMode) : "unset"));
        stringBuilder.append(", language=" + (this.languageIndicatorSet ? Integer.valueOf(this.language) : "unset"));
        stringBuilder.append(", errorClass=" + (this.messageStatusSet ? Integer.valueOf(this.errorClass) : "unset"));
        stringBuilder.append(", msgStatus=" + (this.messageStatusSet ? Integer.valueOf(this.messageStatus) : "unset"));
        stringBuilder.append(", msgCenterTimeStamp=" + (this.msgCenterTimeStamp != null ? this.msgCenterTimeStamp : "unset"));
        stringBuilder.append(", validityPeriodAbsolute=" + (this.validityPeriodAbsolute != null ? this.validityPeriodAbsolute : "unset"));
        stringBuilder.append(", validityPeriodRelative=" + (this.validityPeriodRelativeSet ? Integer.valueOf(this.validityPeriodRelative) : "unset"));
        stringBuilder.append(", deferredDeliveryTimeAbsolute=" + (this.deferredDeliveryTimeAbsolute != null ? this.deferredDeliveryTimeAbsolute : "unset"));
        stringBuilder.append(", deferredDeliveryTimeRelative=" + (this.deferredDeliveryTimeRelativeSet ? Integer.valueOf(this.deferredDeliveryTimeRelative) : "unset"));
        stringBuilder.append(", userAckReq=" + this.userAckReq);
        stringBuilder.append(", deliveryAckReq=" + this.deliveryAckReq);
        stringBuilder.append(", readAckReq=" + this.readAckReq);
        stringBuilder.append(", reportReq=" + this.reportReq);
        stringBuilder.append(", numberOfMessages=" + this.numberOfMessages);
        stringBuilder.append(", callbackNumber=" + this.callbackNumber);
        stringBuilder.append(", depositIndex=" + this.depositIndex);
        stringBuilder.append(", hasUserDataHeader=" + this.hasUserDataHeader);
        stringBuilder.append(", userData=" + this.userData);
        stringBuilder.append(" }");
        return stringBuilder.toString();
    }
}
