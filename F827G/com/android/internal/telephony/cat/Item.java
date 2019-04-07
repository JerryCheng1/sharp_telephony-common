package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class Item implements Parcelable {
    public static final Creator<Item> CREATOR = new Creator<Item>() {
        public Item createFromParcel(Parcel parcel) {
            return new Item(parcel);
        }

        public Item[] newArray(int i) {
            return new Item[i];
        }
    };
    public Bitmap icon;
    public int id;
    public String text;

    public Item(int i, String str) {
        this(i, str, null);
    }

    public Item(int i, String str, Bitmap bitmap) {
        this.id = i;
        this.text = str;
        this.icon = bitmap;
    }

    public Item(Parcel parcel) {
        this.id = parcel.readInt();
        this.text = parcel.readString();
        this.icon = (Bitmap) parcel.readParcelable(null);
    }

    public int describeContents() {
        return 0;
    }

    public String toString() {
        return this.text;
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.id);
        parcel.writeString(this.text);
        parcel.writeParcelable(this.icon, i);
    }
}
