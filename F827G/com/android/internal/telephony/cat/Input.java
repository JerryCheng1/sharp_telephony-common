package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class Input implements Parcelable {
    public static final Creator<Input> CREATOR = new Creator<Input>() {
        public Input createFromParcel(Parcel parcel) {
            return new Input(parcel, null);
        }

        public Input[] newArray(int i) {
            return new Input[i];
        }
    };
    public String defaultText;
    public boolean digitOnly;
    public Duration duration;
    public boolean echo;
    public boolean helpAvailable;
    public Bitmap icon;
    public int maxLen;
    public int minLen;
    public boolean packed;
    public String text;
    public boolean ucs2;
    public boolean yesNo;

    Input() {
        this.text = "";
        this.defaultText = null;
        this.icon = null;
        this.minLen = 0;
        this.maxLen = 1;
        this.ucs2 = false;
        this.packed = false;
        this.digitOnly = false;
        this.echo = false;
        this.yesNo = false;
        this.helpAvailable = false;
        this.duration = null;
    }

    private Input(Parcel parcel) {
        boolean z = true;
        this.text = parcel.readString();
        this.defaultText = parcel.readString();
        this.icon = (Bitmap) parcel.readParcelable(null);
        this.minLen = parcel.readInt();
        this.maxLen = parcel.readInt();
        this.ucs2 = parcel.readInt() == 1;
        this.packed = parcel.readInt() == 1;
        this.digitOnly = parcel.readInt() == 1;
        this.echo = parcel.readInt() == 1;
        this.yesNo = parcel.readInt() == 1;
        if (parcel.readInt() != 1) {
            z = false;
        }
        this.helpAvailable = z;
        this.duration = (Duration) parcel.readParcelable(null);
    }

    /* synthetic */ Input(Parcel parcel, AnonymousClass1 anonymousClass1) {
        this(parcel);
    }

    public int describeContents() {
        return 0;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean setIcon(Bitmap bitmap) {
        return true;
    }

    public void writeToParcel(Parcel parcel, int i) {
        int i2 = 1;
        parcel.writeString(this.text);
        parcel.writeString(this.defaultText);
        parcel.writeParcelable(this.icon, 0);
        parcel.writeInt(this.minLen);
        parcel.writeInt(this.maxLen);
        parcel.writeInt(this.ucs2 ? 1 : 0);
        parcel.writeInt(this.packed ? 1 : 0);
        parcel.writeInt(this.digitOnly ? 1 : 0);
        parcel.writeInt(this.echo ? 1 : 0);
        parcel.writeInt(this.yesNo ? 1 : 0);
        if (!this.helpAvailable) {
            i2 = 0;
        }
        parcel.writeInt(i2);
        parcel.writeParcelable(this.duration, 0);
    }
}
