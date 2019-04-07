package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class SmsCbMessage implements Parcelable {
    public static final Creator<SmsCbMessage> CREATOR = new Creator<SmsCbMessage>() {
        public SmsCbMessage createFromParcel(Parcel parcel) {
            return new SmsCbMessage(parcel);
        }

        public SmsCbMessage[] newArray(int i) {
            return new SmsCbMessage[i];
        }
    };
    public static final int GEOGRAPHICAL_SCOPE_CELL_WIDE = 3;
    public static final int GEOGRAPHICAL_SCOPE_CELL_WIDE_IMMEDIATE = 0;
    public static final int GEOGRAPHICAL_SCOPE_LA_WIDE = 2;
    public static final int GEOGRAPHICAL_SCOPE_PLMN_WIDE = 1;
    protected static final String LOG_TAG = "SMSCB";
    public static final int MESSAGE_FORMAT_3GPP = 1;
    public static final int MESSAGE_FORMAT_3GPP2 = 2;
    public static final int MESSAGE_PRIORITY_EMERGENCY = 3;
    public static final int MESSAGE_PRIORITY_INTERACTIVE = 1;
    public static final int MESSAGE_PRIORITY_NORMAL = 0;
    public static final int MESSAGE_PRIORITY_URGENT = 2;
    private final String mBody;
    private final SmsCbCmasInfo mCmasWarningInfo;
    private final SmsCbEtwsInfo mEtwsWarningInfo;
    private final int mGeographicalScope;
    private final String mLanguage;
    private final SmsCbLocation mLocation;
    private final int mMessageFormat;
    private final int mPriority;
    private final int mSerialNumber;
    private final int mServiceCategory;

    public SmsCbMessage(int i, int i2, int i3, SmsCbLocation smsCbLocation, int i4, String str, String str2, int i5, SmsCbEtwsInfo smsCbEtwsInfo, SmsCbCmasInfo smsCbCmasInfo) {
        this.mMessageFormat = i;
        this.mGeographicalScope = i2;
        this.mSerialNumber = i3;
        this.mLocation = smsCbLocation;
        this.mServiceCategory = i4;
        this.mLanguage = str;
        this.mBody = str2;
        this.mPriority = i5;
        this.mEtwsWarningInfo = smsCbEtwsInfo;
        this.mCmasWarningInfo = smsCbCmasInfo;
    }

    public SmsCbMessage(Parcel parcel) {
        this.mMessageFormat = parcel.readInt();
        this.mGeographicalScope = parcel.readInt();
        this.mSerialNumber = parcel.readInt();
        this.mLocation = new SmsCbLocation(parcel);
        this.mServiceCategory = parcel.readInt();
        this.mLanguage = parcel.readString();
        this.mBody = parcel.readString();
        this.mPriority = parcel.readInt();
        switch (parcel.readInt()) {
            case 67:
                this.mEtwsWarningInfo = null;
                this.mCmasWarningInfo = new SmsCbCmasInfo(parcel);
                return;
            case 69:
                this.mEtwsWarningInfo = new SmsCbEtwsInfo(parcel);
                this.mCmasWarningInfo = null;
                return;
            default:
                this.mEtwsWarningInfo = null;
                this.mCmasWarningInfo = null;
                return;
        }
    }

    public int describeContents() {
        return 0;
    }

    public SmsCbCmasInfo getCmasWarningInfo() {
        return this.mCmasWarningInfo;
    }

    public SmsCbEtwsInfo getEtwsWarningInfo() {
        return this.mEtwsWarningInfo;
    }

    public int getGeographicalScope() {
        return this.mGeographicalScope;
    }

    public String getLanguageCode() {
        return this.mLanguage;
    }

    public SmsCbLocation getLocation() {
        return this.mLocation;
    }

    public String getMessageBody() {
        return this.mBody;
    }

    public int getMessageFormat() {
        return this.mMessageFormat;
    }

    public int getMessagePriority() {
        return this.mPriority;
    }

    public int getSerialNumber() {
        return this.mSerialNumber;
    }

    public int getServiceCategory() {
        return this.mServiceCategory;
    }

    public boolean isCmasMessage() {
        return this.mCmasWarningInfo != null;
    }

    public boolean isEmergencyMessage() {
        return this.mPriority == 3;
    }

    public boolean isEtwsMessage() {
        return this.mEtwsWarningInfo != null;
    }

    public String toString() {
        return "SmsCbMessage{geographicalScope=" + this.mGeographicalScope + ", serialNumber=" + this.mSerialNumber + ", location=" + this.mLocation + ", serviceCategory=" + this.mServiceCategory + ", language=" + this.mLanguage + ", body=" + this.mBody + ", priority=" + this.mPriority + (this.mEtwsWarningInfo != null ? ", " + this.mEtwsWarningInfo.toString() : "") + (this.mCmasWarningInfo != null ? ", " + this.mCmasWarningInfo.toString() : "") + '}';
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.mMessageFormat);
        parcel.writeInt(this.mGeographicalScope);
        parcel.writeInt(this.mSerialNumber);
        this.mLocation.writeToParcel(parcel, i);
        parcel.writeInt(this.mServiceCategory);
        parcel.writeString(this.mLanguage);
        parcel.writeString(this.mBody);
        parcel.writeInt(this.mPriority);
        if (this.mEtwsWarningInfo != null) {
            parcel.writeInt(69);
            this.mEtwsWarningInfo.writeToParcel(parcel, i);
        } else if (this.mCmasWarningInfo != null) {
            parcel.writeInt(67);
            this.mCmasWarningInfo.writeToParcel(parcel, i);
        } else {
            parcel.writeInt(48);
        }
    }
}
