package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class Input implements Parcelable {
    public static final Parcelable.Creator<Input> CREATOR = new Parcelable.Creator<Input>() { // from class: com.android.internal.telephony.cat.Input.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public Input createFromParcel(Parcel in) {
            return new Input(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public Input[] newArray(int size) {
            return new Input[size];
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

    /* JADX INFO: Access modifiers changed from: package-private */
    public Input() {
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

    private Input(Parcel in) {
        boolean z = true;
        this.text = in.readString();
        this.defaultText = in.readString();
        this.icon = (Bitmap) in.readParcelable(null);
        this.minLen = in.readInt();
        this.maxLen = in.readInt();
        this.ucs2 = in.readInt() == 1;
        this.packed = in.readInt() == 1;
        this.digitOnly = in.readInt() == 1;
        this.echo = in.readInt() == 1;
        this.yesNo = in.readInt() == 1;
        this.helpAvailable = in.readInt() != 1 ? false : z;
        this.duration = (Duration) in.readParcelable(null);
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        int i = 1;
        dest.writeString(this.text);
        dest.writeString(this.defaultText);
        dest.writeParcelable(this.icon, 0);
        dest.writeInt(this.minLen);
        dest.writeInt(this.maxLen);
        dest.writeInt(this.ucs2 ? 1 : 0);
        dest.writeInt(this.packed ? 1 : 0);
        dest.writeInt(this.digitOnly ? 1 : 0);
        dest.writeInt(this.echo ? 1 : 0);
        dest.writeInt(this.yesNo ? 1 : 0);
        if (!this.helpAvailable) {
            i = 0;
        }
        dest.writeInt(i);
        dest.writeParcelable(this.duration, 0);
    }

    boolean setIcon(Bitmap Icon) {
        return true;
    }
}
