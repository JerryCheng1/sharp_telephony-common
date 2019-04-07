package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import java.util.ArrayList;
import java.util.List;

public class Menu implements Parcelable {
    public static final Creator<Menu> CREATOR = new Creator<Menu>() {
        public Menu createFromParcel(Parcel parcel) {
            return new Menu(parcel, null);
        }

        public Menu[] newArray(int i) {
            return new Menu[i];
        }
    };
    public int defaultItem;
    public boolean helpAvailable;
    public List<Item> items;
    public boolean itemsIconSelfExplanatory;
    public PresentationType presentationType;
    public boolean softKeyPreferred;
    public String title;
    public List<TextAttribute> titleAttrs;
    public Bitmap titleIcon;
    public boolean titleIconSelfExplanatory;

    public Menu() {
        this.items = new ArrayList();
        this.title = null;
        this.titleAttrs = null;
        this.defaultItem = 0;
        this.softKeyPreferred = false;
        this.helpAvailable = false;
        this.titleIconSelfExplanatory = false;
        this.itemsIconSelfExplanatory = false;
        this.titleIcon = null;
        this.presentationType = PresentationType.NAVIGATION_OPTIONS;
    }

    private Menu(Parcel parcel) {
        boolean z = true;
        this.title = parcel.readString();
        this.titleIcon = (Bitmap) parcel.readParcelable(null);
        this.items = new ArrayList();
        int readInt = parcel.readInt();
        for (int i = 0; i < readInt; i++) {
            this.items.add((Item) parcel.readParcelable(null));
        }
        this.defaultItem = parcel.readInt();
        this.softKeyPreferred = parcel.readInt() == 1;
        this.helpAvailable = parcel.readInt() == 1;
        this.titleIconSelfExplanatory = parcel.readInt() == 1;
        if (parcel.readInt() != 1) {
            z = false;
        }
        this.itemsIconSelfExplanatory = z;
        this.presentationType = PresentationType.values()[parcel.readInt()];
    }

    /* synthetic */ Menu(Parcel parcel, AnonymousClass1 anonymousClass1) {
        this(parcel);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int i) {
        int i2 = 1;
        parcel.writeString(this.title);
        parcel.writeParcelable(this.titleIcon, i);
        int size = this.items.size();
        parcel.writeInt(size);
        for (int i3 = 0; i3 < size; i3++) {
            parcel.writeParcelable((Parcelable) this.items.get(i3), i);
        }
        parcel.writeInt(this.defaultItem);
        parcel.writeInt(this.softKeyPreferred ? 1 : 0);
        parcel.writeInt(this.helpAvailable ? 1 : 0);
        parcel.writeInt(this.titleIconSelfExplanatory ? 1 : 0);
        if (!this.itemsIconSelfExplanatory) {
            i2 = 0;
        }
        parcel.writeInt(i2);
        parcel.writeInt(this.presentationType.ordinal());
    }
}
