package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class SmsCbLocation implements Parcelable {
    public static final Creator<SmsCbLocation> CREATOR = new Creator<SmsCbLocation>() {
        public SmsCbLocation createFromParcel(Parcel parcel) {
            return new SmsCbLocation(parcel);
        }

        public SmsCbLocation[] newArray(int i) {
            return new SmsCbLocation[i];
        }
    };
    private final int mCid;
    private final int mLac;
    private final String mPlmn;

    public SmsCbLocation() {
        this.mPlmn = "";
        this.mLac = -1;
        this.mCid = -1;
    }

    public SmsCbLocation(Parcel parcel) {
        this.mPlmn = parcel.readString();
        this.mLac = parcel.readInt();
        this.mCid = parcel.readInt();
    }

    public SmsCbLocation(String str) {
        this.mPlmn = str;
        this.mLac = -1;
        this.mCid = -1;
    }

    public SmsCbLocation(String str, int i, int i2) {
        this.mPlmn = str;
        this.mLac = i;
        this.mCid = i2;
    }

    public int describeContents() {
        return 0;
    }

    public boolean equals(Object obj) {
        if (obj != this) {
            if (obj == null || !(obj instanceof SmsCbLocation)) {
                return false;
            }
            SmsCbLocation smsCbLocation = (SmsCbLocation) obj;
            if (!(this.mPlmn.equals(smsCbLocation.mPlmn) && this.mLac == smsCbLocation.mLac && this.mCid == smsCbLocation.mCid)) {
                return false;
            }
        }
        return true;
    }

    public int getCid() {
        return this.mCid;
    }

    public int getLac() {
        return this.mLac;
    }

    public String getPlmn() {
        return this.mPlmn;
    }

    public int hashCode() {
        return (((this.mPlmn.hashCode() * 31) + this.mLac) * 31) + this.mCid;
    }

    public boolean isInLocationArea(SmsCbLocation smsCbLocation) {
        return ((this.mCid == -1 || this.mCid == smsCbLocation.mCid) && (this.mLac == -1 || this.mLac == smsCbLocation.mLac)) ? this.mPlmn.equals(smsCbLocation.mPlmn) : false;
    }

    public boolean isInLocationArea(String str, int i, int i2) {
        return this.mPlmn.equals(str) && ((this.mLac == -1 || this.mLac == i) && (this.mCid == -1 || this.mCid == i2));
    }

    public String toString() {
        return '[' + this.mPlmn + ',' + this.mLac + ',' + this.mCid + ']';
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.mPlmn);
        parcel.writeInt(this.mLac);
        parcel.writeInt(this.mCid);
    }
}
