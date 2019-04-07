package com.android.internal.telephony.uicc;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.GsmAlphabet;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public class AdnRecord implements Parcelable {
    static final int ADN_BCD_NUMBER_LENGTH = 0;
    static final int ADN_CAPABILITY_ID = 12;
    static final int ADN_DIALING_NUMBER_END = 11;
    static final int ADN_DIALING_NUMBER_START = 2;
    static final int ADN_EXTENSION_ID = 13;
    static final int ADN_TON_AND_NPI = 1;
    public static final Creator<AdnRecord> CREATOR = new Creator<AdnRecord>() {
        public AdnRecord createFromParcel(Parcel parcel) {
            return new AdnRecord(parcel.readInt(), parcel.readInt(), parcel.readString(), parcel.readString(), parcel.readStringArray(), parcel.readStringArray());
        }

        public AdnRecord[] newArray(int i) {
            return new AdnRecord[i];
        }
    };
    static final int EXT_RECORD_LENGTH_BYTES = 13;
    static final int EXT_RECORD_TYPE_ADDITIONAL_DATA = 2;
    static final int EXT_RECORD_TYPE_MASK = 3;
    static final int FOOTER_SIZE_BYTES = 14;
    static final String LOG_TAG = "AdnRecord";
    static final int MAX_EXT_CALLED_PARTY_LENGTH = 10;
    static final int MAX_NUMBER_SIZE_BYTES = 11;
    String[] mAdditionalNumbers;
    String mAlphaTag;
    int mEfid;
    String[] mEmails;
    int mExtRecord;
    String mNumber;
    int mRecordNumber;

    public AdnRecord(int i, int i2, String str, String str2) {
        this.mAlphaTag = null;
        this.mNumber = null;
        this.mAdditionalNumbers = null;
        this.mExtRecord = 255;
        this.mEfid = i;
        this.mRecordNumber = i2;
        this.mAlphaTag = str;
        this.mNumber = str2;
        this.mEmails = null;
        this.mAdditionalNumbers = null;
    }

    public AdnRecord(int i, int i2, String str, String str2, String[] strArr) {
        this.mAlphaTag = null;
        this.mNumber = null;
        this.mAdditionalNumbers = null;
        this.mExtRecord = 255;
        this.mEfid = i;
        this.mRecordNumber = i2;
        this.mAlphaTag = str;
        this.mNumber = str2;
        this.mEmails = strArr;
        this.mAdditionalNumbers = null;
    }

    public AdnRecord(int i, int i2, String str, String str2, String[] strArr, String[] strArr2) {
        this.mAlphaTag = null;
        this.mNumber = null;
        this.mAdditionalNumbers = null;
        this.mExtRecord = 255;
        this.mEfid = i;
        this.mRecordNumber = i2;
        this.mAlphaTag = str;
        this.mNumber = str2;
        this.mEmails = strArr;
        this.mAdditionalNumbers = strArr2;
    }

    public AdnRecord(int i, int i2, byte[] bArr) {
        this.mAlphaTag = null;
        this.mNumber = null;
        this.mAdditionalNumbers = null;
        this.mExtRecord = 255;
        this.mEfid = i;
        this.mRecordNumber = i2;
        parseRecord(bArr);
    }

    public AdnRecord(String str, String str2) {
        this(0, 0, str, str2);
    }

    public AdnRecord(String str, String str2, String[] strArr) {
        this(0, 0, str, str2, strArr);
    }

    public AdnRecord(String str, String str2, String[] strArr, String[] strArr2) {
        this(0, 0, str, str2, strArr, strArr2);
    }

    public AdnRecord(byte[] bArr) {
        this(0, 0, bArr);
    }

    private static boolean arrayCompareNullEqualsEmpty(String[] strArr, String[] strArr2) {
        if (strArr != strArr2) {
            if (strArr == null) {
                Object[] strArr3 = new String[]{""};
            }
            if (strArr2 == null) {
                Object[] strArr22 = new String[]{""};
            }
            for (CharSequence charSequence : strArr3) {
                if (!TextUtils.isEmpty(charSequence) && !Arrays.asList(strArr22).contains(charSequence)) {
                    return false;
                }
            }
            for (CharSequence charSequence2 : strArr22) {
                if (!TextUtils.isEmpty(charSequence2) && !Arrays.asList(strArr3).contains(charSequence2)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void parseRecord(byte[] bArr) {
        try {
            this.mAlphaTag = IccUtils.adnStringFieldToString(bArr, 0, bArr.length - 14);
            int length = bArr.length - 14;
            int i = bArr[length] & 255;
            if (i > 11) {
                this.mNumber = "";
                return;
            }
            this.mNumber = PhoneNumberUtils.calledPartyBCDToString(bArr, length + 1, i);
            this.mExtRecord = bArr[bArr.length - 1] & 255;
            this.mEmails = null;
            this.mAdditionalNumbers = null;
        } catch (RuntimeException e) {
            Rlog.w(LOG_TAG, "Error parsing AdnRecord", e);
            this.mNumber = "";
            this.mAlphaTag = "";
            this.mEmails = null;
            this.mAdditionalNumbers = null;
        }
    }

    private static boolean stringCompareNullEqualsEmpty(String str, String str2) {
        if (str == str2) {
            return true;
        }
        Object str22;
        if (str == null) {
            str = "";
        }
        if (str22 == null) {
            str22 = "";
        }
        return str.equals(str22);
    }

    public void appendExtRecord(byte[] bArr) {
        try {
            if (bArr.length == 13 && (bArr[0] & 3) == 2 && (bArr[1] & 255) <= 10) {
                this.mNumber += PhoneNumberUtils.calledPartyBCDFragmentToString(bArr, 2, bArr[1] & 255);
            }
        } catch (RuntimeException e) {
            Rlog.w(LOG_TAG, "Error parsing AdnRecord ext record", e);
        }
    }

    public byte[] buildAdnString(int i) {
        int i2;
        int i3 = i - 14;
        byte[] bArr = new byte[i];
        for (i2 = 0; i2 < i; i2++) {
            bArr[i2] = (byte) -1;
        }
        if (TextUtils.isEmpty(this.mNumber) && TextUtils.isEmpty(this.mAlphaTag)) {
            Rlog.w(LOG_TAG, "[buildAdnString] Empty dialing number");
        } else if (this.mNumber == null || this.mNumber.length() <= 20) {
            byte[] numberToCalledPartyBCD;
            if (!TextUtils.isEmpty(this.mNumber)) {
                numberToCalledPartyBCD = PhoneNumberUtils.numberToCalledPartyBCD(this.mNumber);
                System.arraycopy(numberToCalledPartyBCD, 0, bArr, i3 + 1, numberToCalledPartyBCD.length);
                bArr[i3 + 0] = (byte) numberToCalledPartyBCD.length;
            }
            bArr[i3 + 12] = (byte) -1;
            bArr[i3 + 13] = (byte) -1;
            if (!TextUtils.isEmpty(this.mAlphaTag)) {
                try {
                    if (GsmAlphabet.countGsmSeptetsUsingTables(this.mAlphaTag, false, 0, 0) == -1) {
                        StringBuilder stringBuilder = new StringBuilder(this.mAlphaTag.length());
                        i2 = 0;
                        for (int i4 = 0; i4 < this.mAlphaTag.length(); i4++) {
                            char charAt = this.mAlphaTag.charAt(i4);
                            if (Character.isHighSurrogate(charAt)) {
                                stringBuilder.append(' ');
                                i2 = 1;
                            } else if (Character.isLowSurrogate(charAt)) {
                                i2 = 1;
                            } else {
                                stringBuilder.append(charAt);
                            }
                        }
                        byte[] bytes = stringBuilder.toString().getBytes("UTF-16BE");
                        if (bytes.length + 1 > i3) {
                            throw new IllegalArgumentException("[UCS2] Max length of tag is " + i3);
                        }
                        bArr[0] = Byte.MIN_VALUE;
                        System.arraycopy(bytes, 0, bArr, 1, bytes.length);
                    } else {
                        numberToCalledPartyBCD = GsmAlphabet.stringToGsm8BitPacked(this.mAlphaTag);
                        if (numberToCalledPartyBCD.length > i3) {
                            throw new IllegalArgumentException("[GSM7bit] Max length of tag is " + i3);
                        }
                        System.arraycopy(numberToCalledPartyBCD, 0, bArr, 0, numberToCalledPartyBCD.length);
                        i2 = 0;
                    }
                    if (i2 != 0) {
                        this.mAlphaTag = IccUtils.adnStringFieldToString(bArr, 0, bArr.length - 14);
                        return bArr;
                    }
                } catch (UnsupportedEncodingException e) {
                    Rlog.w(LOG_TAG, "[buildAdnString]UnsupportedEncodingException:UTF-16BE");
                    return bArr;
                } catch (IllegalArgumentException e2) {
                    Rlog.w(LOG_TAG, "[buildAdnString]" + e2.getMessage());
                    return null;
                }
            }
        } else {
            Rlog.w(LOG_TAG, "[buildAdnString] Max length of dialing number is 20");
            return null;
        }
        return bArr;
    }

    public int describeContents() {
        return 0;
    }

    public String[] getAdditionalNumbers() {
        return this.mAdditionalNumbers;
    }

    public String getAlphaTag() {
        return this.mAlphaTag;
    }

    public String[] getAnrNumbers() {
        return getAdditionalNumbers();
    }

    public String[] getEmails() {
        return this.mEmails;
    }

    public String getNumber() {
        return this.mNumber;
    }

    public boolean hasExtendedRecord() {
        return (this.mExtRecord == 0 || this.mExtRecord == 255) ? false : true;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(this.mAlphaTag) && TextUtils.isEmpty(this.mNumber) && this.mEmails == null && this.mAdditionalNumbers == null;
    }

    public boolean isEqual(AdnRecord adnRecord) {
        return stringCompareNullEqualsEmpty(this.mAlphaTag, adnRecord.mAlphaTag) && stringCompareNullEqualsEmpty(this.mNumber, adnRecord.mNumber) && arrayCompareNullEqualsEmpty(this.mEmails, adnRecord.mEmails) && arrayCompareNullEqualsEmpty(this.mAdditionalNumbers, adnRecord.mAdditionalNumbers);
    }

    public void setAdditionalNumbers(String[] strArr) {
        this.mAdditionalNumbers = strArr;
    }

    public void setEmails(String[] strArr) {
        this.mEmails = strArr;
    }

    public String toString() {
        return "ADN Record 'Tag:" + this.mAlphaTag + "', Num:'" + this.mNumber + ", Emails:" + Arrays.toString(this.mEmails) + ", Anrs:" + Arrays.toString(this.mAdditionalNumbers) + "'";
    }

    public void updateAnrEmailArray(AdnRecord adnRecord, int i, int i2) {
        this.mEmails = updateAnrEmailArrayHelper(this.mEmails, adnRecord.mEmails, i);
        this.mAdditionalNumbers = updateAnrEmailArrayHelper(this.mAdditionalNumbers, adnRecord.mAdditionalNumbers, i2);
    }

    public String[] updateAnrEmailArrayHelper(String[] strArr, String[] strArr2, int i) {
        String[] strArr3;
        if (i == 0) {
            strArr3 = null;
        } else if (strArr == null || strArr2 == null) {
            return strArr;
        } else {
            int i2;
            int i3;
            String[] strArr4 = new String[i];
            for (i2 = 0; i2 < i; i2++) {
                strArr4[i2] = "";
            }
            for (i2 = 0; i2 < strArr2.length; i2++) {
                if (!TextUtils.isEmpty(strArr2[i2])) {
                    for (Object equals : strArr) {
                        if (strArr2[i2].equals(equals)) {
                            strArr4[i2] = strArr2[i2];
                            break;
                        }
                    }
                }
            }
            for (i2 = 0; i2 < strArr.length; i2++) {
                if (!Arrays.asList(strArr4).contains(strArr[i2])) {
                    for (i3 = 0; i3 < strArr4.length; i3++) {
                        if (TextUtils.isEmpty(strArr4[i3])) {
                            strArr4[i3] = strArr[i2];
                            break;
                        }
                    }
                }
            }
            strArr3 = strArr4;
        }
        return strArr3;
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.mEfid);
        parcel.writeInt(this.mRecordNumber);
        parcel.writeString(this.mAlphaTag);
        parcel.writeString(this.mNumber);
        parcel.writeStringArray(this.mEmails);
        parcel.writeStringArray(this.mAdditionalNumbers);
    }
}
