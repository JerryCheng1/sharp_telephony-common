package com.android.internal.telephony.cdma;

public class CdmaSmsBroadcastConfigInfo {
    private int mFromServiceCategory;
    private int mLanguage;
    private boolean mSelected;
    private int mToServiceCategory;

    public CdmaSmsBroadcastConfigInfo(int i, int i2, int i3, boolean z) {
        this.mFromServiceCategory = i;
        this.mToServiceCategory = i2;
        this.mLanguage = i3;
        this.mSelected = z;
    }

    public int getFromServiceCategory() {
        return this.mFromServiceCategory;
    }

    public int getLanguage() {
        return this.mLanguage;
    }

    public int getToServiceCategory() {
        return this.mToServiceCategory;
    }

    public boolean isSelected() {
        return this.mSelected;
    }

    public String toString() {
        return "CdmaSmsBroadcastConfigInfo: Id [" + this.mFromServiceCategory + ", " + this.mToServiceCategory + "] " + (isSelected() ? "ENABLED" : "DISABLED");
    }
}
