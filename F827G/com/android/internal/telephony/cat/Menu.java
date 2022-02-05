package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class Menu implements Parcelable {
    public static final Parcelable.Creator<Menu> CREATOR = new Parcelable.Creator<Menu>() { // from class: com.android.internal.telephony.cat.Menu.1
        @Override // android.os.Parcelable.Creator
        public Menu createFromParcel(Parcel in) {
            return new Menu(in);
        }

        @Override // android.os.Parcelable.Creator
        public Menu[] newArray(int size) {
            return new Menu[size];
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

    private Menu(Parcel in) {
        boolean z = true;
        this.title = in.readString();
        this.titleIcon = (Bitmap) in.readParcelable(null);
        this.items = new ArrayList();
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            this.items.add((Item) in.readParcelable(null));
        }
        this.defaultItem = in.readInt();
        this.softKeyPreferred = in.readInt() == 1;
        this.helpAvailable = in.readInt() == 1;
        this.titleIconSelfExplanatory = in.readInt() == 1;
        this.itemsIconSelfExplanatory = in.readInt() != 1 ? false : z;
        this.presentationType = PresentationType.values()[in.readInt()];
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        int i = 1;
        dest.writeString(this.title);
        dest.writeParcelable(this.titleIcon, flags);
        int size = this.items.size();
        dest.writeInt(size);
        for (int i2 = 0; i2 < size; i2++) {
            dest.writeParcelable(this.items.get(i2), flags);
        }
        dest.writeInt(this.defaultItem);
        dest.writeInt(this.softKeyPreferred ? 1 : 0);
        dest.writeInt(this.helpAvailable ? 1 : 0);
        dest.writeInt(this.titleIconSelfExplanatory ? 1 : 0);
        if (!this.itemsIconSelfExplanatory) {
            i = 0;
        }
        dest.writeInt(i);
        dest.writeInt(this.presentationType.ordinal());
    }
}
