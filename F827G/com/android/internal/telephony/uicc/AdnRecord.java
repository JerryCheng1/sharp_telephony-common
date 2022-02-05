package com.android.internal.telephony.uicc;

import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.GsmAlphabet;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class AdnRecord implements Parcelable {
    static final int ADN_BCD_NUMBER_LENGTH = 0;
    static final int ADN_CAPABILITY_ID = 12;
    static final int ADN_DIALING_NUMBER_END = 11;
    static final int ADN_DIALING_NUMBER_START = 2;
    static final int ADN_EXTENSION_ID = 13;
    static final int ADN_TON_AND_NPI = 1;
    public static final Parcelable.Creator<AdnRecord> CREATOR = new Parcelable.Creator<AdnRecord>() { // from class: com.android.internal.telephony.uicc.AdnRecord.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public AdnRecord createFromParcel(Parcel source) {
            return new AdnRecord(source.readInt(), source.readInt(), source.readString(), source.readString(), source.readStringArray(), source.readStringArray());
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public AdnRecord[] newArray(int size) {
            return new AdnRecord[size];
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

    public AdnRecord(byte[] record) {
        this(0, 0, record);
    }

    public AdnRecord(int efid, int recordNumber, byte[] record) {
        this.mAlphaTag = null;
        this.mNumber = null;
        this.mAdditionalNumbers = null;
        this.mExtRecord = 255;
        this.mEfid = efid;
        this.mRecordNumber = recordNumber;
        parseRecord(record);
    }

    public AdnRecord(String alphaTag, String number) {
        this(0, 0, alphaTag, number);
    }

    public AdnRecord(String alphaTag, String number, String[] emails) {
        this(0, 0, alphaTag, number, emails);
    }

    public AdnRecord(String alphaTag, String number, String[] emails, String[] additionalNumbers) {
        this(0, 0, alphaTag, number, emails, additionalNumbers);
    }

    public AdnRecord(int efid, int recordNumber, String alphaTag, String number, String[] emails) {
        this.mAlphaTag = null;
        this.mNumber = null;
        this.mAdditionalNumbers = null;
        this.mExtRecord = 255;
        this.mEfid = efid;
        this.mRecordNumber = recordNumber;
        this.mAlphaTag = alphaTag;
        this.mNumber = number;
        this.mEmails = emails;
        this.mAdditionalNumbers = null;
    }

    public AdnRecord(int efid, int recordNumber, String alphaTag, String number, String[] emails, String[] additionalNumbers) {
        this.mAlphaTag = null;
        this.mNumber = null;
        this.mAdditionalNumbers = null;
        this.mExtRecord = 255;
        this.mEfid = efid;
        this.mRecordNumber = recordNumber;
        this.mAlphaTag = alphaTag;
        this.mNumber = number;
        this.mEmails = emails;
        this.mAdditionalNumbers = additionalNumbers;
    }

    public AdnRecord(int efid, int recordNumber, String alphaTag, String number) {
        this.mAlphaTag = null;
        this.mNumber = null;
        this.mAdditionalNumbers = null;
        this.mExtRecord = 255;
        this.mEfid = efid;
        this.mRecordNumber = recordNumber;
        this.mAlphaTag = alphaTag;
        this.mNumber = number;
        this.mEmails = null;
        this.mAdditionalNumbers = null;
    }

    public String getAlphaTag() {
        return this.mAlphaTag;
    }

    public String getNumber() {
        return this.mNumber;
    }

    public String[] getEmails() {
        return this.mEmails;
    }

    public void setEmails(String[] emails) {
        this.mEmails = emails;
    }

    public String[] getAdditionalNumbers() {
        return this.mAdditionalNumbers;
    }

    public void setAdditionalNumbers(String[] additionalNumbers) {
        this.mAdditionalNumbers = additionalNumbers;
    }

    public String toString() {
        return "ADN Record 'Tag:" + this.mAlphaTag + "', Num:'" + this.mNumber + ", Emails:" + Arrays.toString(this.mEmails) + ", Anrs:" + Arrays.toString(this.mAdditionalNumbers) + "'";
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(this.mAlphaTag) && TextUtils.isEmpty(this.mNumber) && this.mEmails == null && this.mAdditionalNumbers == null;
    }

    public boolean hasExtendedRecord() {
        return (this.mExtRecord == 0 || this.mExtRecord == 255) ? false : true;
    }

    private static boolean stringCompareNullEqualsEmpty(String s1, String s2) {
        if (s1 == s2) {
            return true;
        }
        if (s1 == null) {
            s1 = "";
        }
        if (s2 == null) {
            s2 = "";
        }
        return s1.equals(s2);
    }

    private static boolean arrayCompareNullEqualsEmpty(String[] s1, String[] s2) {
        if (s1 == s2) {
            return true;
        }
        if (s1 == null) {
            s1 = new String[]{""};
        }
        if (s2 == null) {
            s2 = new String[]{""};
        }
        for (String str : s1) {
            if (!TextUtils.isEmpty(str) && !Arrays.asList(s2).contains(str)) {
                return false;
            }
        }
        for (String str2 : s2) {
            if (!TextUtils.isEmpty(str2) && !Arrays.asList(s1).contains(str2)) {
                return false;
            }
        }
        return true;
    }

    public boolean isEqual(AdnRecord adn) {
        return stringCompareNullEqualsEmpty(this.mAlphaTag, adn.mAlphaTag) && stringCompareNullEqualsEmpty(this.mNumber, adn.mNumber) && arrayCompareNullEqualsEmpty(this.mEmails, adn.mEmails) && arrayCompareNullEqualsEmpty(this.mAdditionalNumbers, adn.mAdditionalNumbers);
    }

    public String[] updateAnrEmailArrayHelper(String[] dest, String[] src, int fileCount) {
        if (fileCount == 0) {
            return null;
        }
        if (dest == null || src == null) {
            return dest;
        }
        String[] ref = new String[fileCount];
        for (int i = 0; i < fileCount; i++) {
            ref[i] = "";
        }
        for (int i2 = 0; i2 < src.length; i2++) {
            if (!TextUtils.isEmpty(src[i2])) {
                int j = 0;
                while (true) {
                    if (j >= dest.length) {
                        break;
                    } else if (src[i2].equals(dest[j])) {
                        ref[i2] = src[i2];
                        break;
                    } else {
                        j++;
                    }
                }
            }
        }
        for (int i3 = 0; i3 < dest.length; i3++) {
            if (!Arrays.asList(ref).contains(dest[i3])) {
                int j2 = 0;
                while (true) {
                    if (j2 >= ref.length) {
                        break;
                    } else if (TextUtils.isEmpty(ref[j2])) {
                        ref[j2] = dest[i3];
                        break;
                    } else {
                        j2++;
                    }
                }
            }
        }
        return ref;
    }

    public void updateAnrEmailArray(AdnRecord adn, int emailFileNum, int anrFileNum) {
        this.mEmails = updateAnrEmailArrayHelper(this.mEmails, adn.mEmails, emailFileNum);
        this.mAdditionalNumbers = updateAnrEmailArrayHelper(this.mAdditionalNumbers, adn.mAdditionalNumbers, anrFileNum);
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mEfid);
        dest.writeInt(this.mRecordNumber);
        dest.writeString(this.mAlphaTag);
        dest.writeString(this.mNumber);
        dest.writeStringArray(this.mEmails);
        dest.writeStringArray(this.mAdditionalNumbers);
    }

    public byte[] buildAdnString(int recordSize) {
        int footerOffset = recordSize - 14;
        byte[] adnString = new byte[recordSize];
        for (int i = 0; i < recordSize; i++) {
            adnString[i] = -1;
        }
        if (TextUtils.isEmpty(this.mNumber) && TextUtils.isEmpty(this.mAlphaTag)) {
            Rlog.w(LOG_TAG, "[buildAdnString] Empty dialing number");
            return adnString;
        } else if (this.mNumber == null || this.mNumber.length() <= 20) {
            if (!TextUtils.isEmpty(this.mNumber)) {
                byte[] bcdNumber = PhoneNumberUtils.numberToCalledPartyBCD(this.mNumber);
                System.arraycopy(bcdNumber, 0, adnString, footerOffset + 1, bcdNumber.length);
                adnString[footerOffset + 0] = (byte) bcdNumber.length;
            }
            adnString[footerOffset + 12] = -1;
            adnString[footerOffset + 13] = -1;
            if (TextUtils.isEmpty(this.mAlphaTag)) {
                return adnString;
            }
            boolean isChange = false;
            try {
                if (GsmAlphabet.countGsmSeptetsUsingTables(this.mAlphaTag, false, 0, 0) == -1) {
                    StringBuilder usc2String = new StringBuilder(this.mAlphaTag.length());
                    for (int i2 = 0; i2 < this.mAlphaTag.length(); i2++) {
                        char ch = this.mAlphaTag.charAt(i2);
                        if (Character.isHighSurrogate(ch)) {
                            usc2String.append(' ');
                            isChange = true;
                        } else if (Character.isLowSurrogate(ch)) {
                            isChange = true;
                        } else {
                            usc2String.append(ch);
                        }
                    }
                    byte[] byteTag = usc2String.toString().getBytes("UTF-16BE");
                    if (byteTag.length + 1 > footerOffset) {
                        throw new IllegalArgumentException("[UCS2] Max length of tag is " + footerOffset);
                    }
                    adnString[0] = Byte.MIN_VALUE;
                    System.arraycopy(byteTag, 0, adnString, 1, byteTag.length);
                } else {
                    byte[] byteTag2 = GsmAlphabet.stringToGsm8BitPacked(this.mAlphaTag);
                    if (byteTag2.length > footerOffset) {
                        throw new IllegalArgumentException("[GSM7bit] Max length of tag is " + footerOffset);
                    }
                    System.arraycopy(byteTag2, 0, adnString, 0, byteTag2.length);
                }
                if (!isChange) {
                    return adnString;
                }
                this.mAlphaTag = IccUtils.adnStringFieldToString(adnString, 0, adnString.length - 14);
                return adnString;
            } catch (UnsupportedEncodingException e) {
                Rlog.w(LOG_TAG, "[buildAdnString]UnsupportedEncodingException:UTF-16BE");
                return adnString;
            } catch (IllegalArgumentException e2) {
                Rlog.w(LOG_TAG, "[buildAdnString]" + e2.getMessage());
                return null;
            }
        } else {
            Rlog.w(LOG_TAG, "[buildAdnString] Max length of dialing number is 20");
            return null;
        }
    }

    public void appendExtRecord(byte[] extRecord) {
        try {
            if (extRecord.length == 13 && (extRecord[0] & 3) == 2 && (extRecord[1] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) <= 10) {
                this.mNumber += PhoneNumberUtils.calledPartyBCDFragmentToString(extRecord, 2, extRecord[1] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT);
            }
        } catch (RuntimeException ex) {
            Rlog.w(LOG_TAG, "Error parsing AdnRecord ext record", ex);
        }
    }

    private void parseRecord(byte[] record) {
        try {
            this.mAlphaTag = IccUtils.adnStringFieldToString(record, 0, record.length - 14);
            int footerOffset = record.length - 14;
            int numberLength = record[footerOffset] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
            if (numberLength > 11) {
                this.mNumber = "";
            } else {
                this.mNumber = PhoneNumberUtils.calledPartyBCDToString(record, footerOffset + 1, numberLength);
                this.mExtRecord = record[record.length - 1] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
                this.mEmails = null;
                this.mAdditionalNumbers = null;
            }
        } catch (RuntimeException ex) {
            Rlog.w(LOG_TAG, "Error parsing AdnRecord", ex);
            this.mNumber = "";
            this.mAlphaTag = "";
            this.mEmails = null;
            this.mAdditionalNumbers = null;
        }
    }

    public String[] getAnrNumbers() {
        return getAdditionalNumbers();
    }
}
