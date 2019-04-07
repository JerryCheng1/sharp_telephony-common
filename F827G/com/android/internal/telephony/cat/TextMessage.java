package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class TextMessage implements Parcelable {
    public static final Creator<TextMessage> CREATOR = new Creator<TextMessage>() {
        public TextMessage createFromParcel(Parcel parcel) {
            return new TextMessage(parcel, null);
        }

        public TextMessage[] newArray(int i) {
            return new TextMessage[i];
        }
    };
    public Duration duration;
    public Bitmap icon;
    public boolean iconSelfExplanatory;
    public boolean isHighPriority;
    public boolean responseNeeded;
    public String text;
    public String title;
    public boolean userClear;

    TextMessage() {
        this.title = "";
        this.text = null;
        this.icon = null;
        this.iconSelfExplanatory = false;
        this.isHighPriority = false;
        this.responseNeeded = true;
        this.userClear = false;
        this.duration = null;
    }

    private TextMessage(Parcel parcel) {
        boolean z = true;
        this.title = "";
        this.text = null;
        this.icon = null;
        this.iconSelfExplanatory = false;
        this.isHighPriority = false;
        this.responseNeeded = true;
        this.userClear = false;
        this.duration = null;
        this.title = parcel.readString();
        this.text = parcel.readString();
        this.icon = (Bitmap) parcel.readParcelable(null);
        this.iconSelfExplanatory = parcel.readInt() == 1;
        this.isHighPriority = parcel.readInt() == 1;
        this.responseNeeded = parcel.readInt() == 1;
        if (parcel.readInt() != 1) {
            z = false;
        }
        this.userClear = z;
        this.duration = (Duration) parcel.readParcelable(null);
    }

    /* synthetic */ TextMessage(Parcel parcel, AnonymousClass1 anonymousClass1) {
        this(parcel);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int i) {
        int i2 = 1;
        parcel.writeString(this.title);
        parcel.writeString(this.text);
        parcel.writeParcelable(this.icon, 0);
        parcel.writeInt(this.iconSelfExplanatory ? 1 : 0);
        parcel.writeInt(this.isHighPriority ? 1 : 0);
        parcel.writeInt(this.responseNeeded ? 1 : 0);
        if (!this.userClear) {
            i2 = 0;
        }
        parcel.writeInt(i2);
        parcel.writeParcelable(this.duration, 0);
    }
}
