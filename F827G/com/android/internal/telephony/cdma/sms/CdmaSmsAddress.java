package com.android.internal.telephony.cdma.sms;

import android.util.SparseBooleanArray;
import com.android.internal.telephony.SmsAddress;
import com.android.internal.util.HexDump;

public class CdmaSmsAddress extends SmsAddress {
    public static final int DIGIT_MODE_4BIT_DTMF = 0;
    public static final int DIGIT_MODE_8BIT_CHAR = 1;
    public static final int NUMBERING_PLAN_ISDN_TELEPHONY = 1;
    public static final int NUMBERING_PLAN_UNKNOWN = 0;
    public static final int NUMBER_MODE_DATA_NETWORK = 1;
    public static final int NUMBER_MODE_NOT_DATA_NETWORK = 0;
    public static final int SMS_ADDRESS_MAX = 36;
    public static final int SMS_SUBADDRESS_MAX = 36;
    public static final int TON_ABBREVIATED = 6;
    public static final int TON_ALPHANUMERIC = 5;
    public static final int TON_INTERNATIONAL_OR_IP = 1;
    public static final int TON_NATIONAL_OR_EMAIL = 2;
    public static final int TON_NETWORK = 3;
    public static final int TON_RESERVED = 7;
    public static final int TON_SUBSCRIBER = 4;
    public static final int TON_UNKNOWN = 0;
    private static final SparseBooleanArray numericCharDialableMap = new SparseBooleanArray(numericCharsDialable.length + numericCharsSugar.length);
    private static final char[] numericCharsDialable = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '#'};
    private static final char[] numericCharsSugar = new char[]{'(', ')', ' ', '-', '+', '.', '/', '\\'};
    public int digitMode;
    public int numberMode;
    public int numberOfDigits;
    public int numberPlan;

    static {
        for (char put : numericCharsDialable) {
            numericCharDialableMap.put(put, true);
        }
        for (char put2 : numericCharsSugar) {
            numericCharDialableMap.put(put2, false);
        }
    }

    private static String filterNumericSugar(String str) {
        StringBuilder stringBuilder = new StringBuilder();
        int length = str.length();
        for (int i = 0; i < length; i++) {
            char charAt = str.charAt(i);
            int indexOfKey = numericCharDialableMap.indexOfKey(charAt);
            if (indexOfKey < 0) {
                return null;
            }
            if (numericCharDialableMap.valueAt(indexOfKey)) {
                stringBuilder.append(charAt);
            }
        }
        return stringBuilder.toString();
    }

    private static String filterWhitespace(String str) {
        StringBuilder stringBuilder = new StringBuilder();
        int length = str.length();
        for (int i = 0; i < length; i++) {
            char charAt = str.charAt(i);
            if (!(charAt == ' ' || charAt == 13 || charAt == 10 || charAt == 9)) {
                stringBuilder.append(charAt);
            }
        }
        return stringBuilder.toString();
    }

    public static CdmaSmsAddress parse(String str) {
        byte[] parseToDtmf;
        CdmaSmsAddress cdmaSmsAddress = new CdmaSmsAddress();
        cdmaSmsAddress.address = str;
        cdmaSmsAddress.digitMode = 0;
        cdmaSmsAddress.ton = 0;
        cdmaSmsAddress.numberMode = 0;
        cdmaSmsAddress.numberPlan = 0;
        if (str.indexOf(43) != -1) {
            cdmaSmsAddress.digitMode = 1;
            cdmaSmsAddress.ton = 1;
            cdmaSmsAddress.numberMode = 0;
            cdmaSmsAddress.numberPlan = 1;
        }
        if (str.indexOf(64) != -1) {
            cdmaSmsAddress.digitMode = 1;
            cdmaSmsAddress.ton = 2;
            cdmaSmsAddress.numberMode = 1;
        }
        String filterNumericSugar = filterNumericSugar(str);
        if (cdmaSmsAddress.digitMode == 0) {
            parseToDtmf = filterNumericSugar != null ? parseToDtmf(filterNumericSugar) : null;
            if (parseToDtmf == null) {
                cdmaSmsAddress.digitMode = 1;
            }
        } else {
            parseToDtmf = null;
        }
        if (cdmaSmsAddress.digitMode == 1) {
            parseToDtmf = UserData.stringToAscii(filterWhitespace(filterNumericSugar));
            if (parseToDtmf == null) {
                return null;
            }
        }
        cdmaSmsAddress.origBytes = parseToDtmf;
        cdmaSmsAddress.numberOfDigits = parseToDtmf.length;
        return cdmaSmsAddress;
    }

    private static byte[] parseToDtmf(String str) {
        int length = str.length();
        byte[] bArr = new byte[length];
        for (int i = 0; i < length; i++) {
            int i2;
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
            bArr[i] = (byte) i2;
        }
        return bArr;
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("CdmaSmsAddress ");
        stringBuilder.append("{ digitMode=" + this.digitMode);
        stringBuilder.append(", numberMode=" + this.numberMode);
        stringBuilder.append(", numberPlan=" + this.numberPlan);
        stringBuilder.append(", numberOfDigits=" + this.numberOfDigits);
        stringBuilder.append(", ton=" + this.ton);
        stringBuilder.append(", address=\"" + this.address + "\"");
        stringBuilder.append(", origBytes=" + HexDump.toHexString(this.origBytes));
        stringBuilder.append(" }");
        return stringBuilder.toString();
    }
}
