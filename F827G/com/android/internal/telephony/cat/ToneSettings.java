package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class ToneSettings implements Parcelable {
    public static final Creator<ToneSettings> CREATOR = new Creator<ToneSettings>() {
        public ToneSettings createFromParcel(Parcel parcel) {
            return new ToneSettings(parcel, null);
        }

        public ToneSettings[] newArray(int i) {
            return new ToneSettings[i];
        }
    };
    public Duration duration;
    public Tone tone;
    public boolean vibrate;

    private ToneSettings(Parcel parcel) {
        this.duration = (Duration) parcel.readParcelable(null);
        this.tone = (Tone) parcel.readParcelable(null);
        this.vibrate = parcel.readInt() == 1;
    }

    /* synthetic */ ToneSettings(Parcel parcel, AnonymousClass1 anonymousClass1) {
        this(parcel);
    }

    public ToneSettings(Duration duration, Tone tone, boolean z) {
        this.duration = duration;
        this.tone = tone;
        this.vibrate = z;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int i) {
        int i2 = 0;
        parcel.writeParcelable(this.duration, 0);
        parcel.writeParcelable(this.tone, 0);
        if (this.vibrate) {
            i2 = 1;
        }
        parcel.writeInt(i2);
    }
}
