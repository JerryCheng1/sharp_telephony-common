package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class Duration implements Parcelable {
    public static final Creator<Duration> CREATOR = new Creator<Duration>() {
        public Duration createFromParcel(Parcel parcel) {
            return new Duration(parcel, null);
        }

        public Duration[] newArray(int i) {
            return new Duration[i];
        }
    };
    public int timeInterval;
    public TimeUnit timeUnit;

    public enum TimeUnit {
        MINUTE(0),
        SECOND(1),
        TENTH_SECOND(2);
        
        private int mValue;

        private TimeUnit(int i) {
            this.mValue = i;
        }

        public int value() {
            return this.mValue;
        }
    }

    public Duration(int i, TimeUnit timeUnit) {
        this.timeInterval = i;
        this.timeUnit = timeUnit;
    }

    private Duration(Parcel parcel) {
        this.timeInterval = parcel.readInt();
        this.timeUnit = TimeUnit.values()[parcel.readInt()];
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.timeInterval);
        parcel.writeInt(this.timeUnit.ordinal());
    }
}
