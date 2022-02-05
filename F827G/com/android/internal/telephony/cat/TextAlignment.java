package com.android.internal.telephony.cat;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public enum TextAlignment {
    LEFT(0),
    CENTER(1),
    RIGHT(2),
    DEFAULT(3);
    
    private int mValue;

    TextAlignment(int value) {
        this.mValue = value;
    }

    public static TextAlignment fromInt(int value) {
        TextAlignment[] arr$ = values();
        for (TextAlignment e : arr$) {
            if (e.mValue == value) {
                return e;
            }
        }
        return null;
    }
}
