package com.android.internal.telephony.cat;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public enum FontSize {
    NORMAL(0),
    LARGE(1),
    SMALL(2);
    
    private int mValue;

    FontSize(int value) {
        this.mValue = value;
    }

    public static FontSize fromInt(int value) {
        FontSize[] arr$ = values();
        for (FontSize e : arr$) {
            if (e.mValue == value) {
                return e;
            }
        }
        return null;
    }
}
