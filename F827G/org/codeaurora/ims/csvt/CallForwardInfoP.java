package org.codeaurora.ims.csvt;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class CallForwardInfoP implements Parcelable {
    public static final Creator<CallForwardInfoP> CREATOR = new Creator<CallForwardInfoP>() {
        public CallForwardInfoP createFromParcel(Parcel parcel) {
            return new CallForwardInfoP(parcel);
        }

        public CallForwardInfoP[] newArray(int i) {
            return new CallForwardInfoP[i];
        }
    };
    public String number;
    public int reason;
    public int serviceClass;
    public int status;
    public int timeSeconds;
    public int toa;

    public CallForwardInfoP(Parcel parcel) {
        this.status = parcel.readInt();
        this.reason = parcel.readInt();
        this.toa = parcel.readInt();
        this.number = parcel.readString();
        this.timeSeconds = parcel.readInt();
        this.serviceClass = parcel.readInt();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.status);
        parcel.writeInt(this.reason);
        parcel.writeInt(this.toa);
        parcel.writeString(this.number);
        parcel.writeInt(this.timeSeconds);
        parcel.writeInt(this.serviceClass);
    }
}
