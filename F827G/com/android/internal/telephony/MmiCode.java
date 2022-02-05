package com.android.internal.telephony;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public interface MmiCode {

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum State {
        PENDING,
        CANCELLED,
        COMPLETE,
        FAILED
    }

    void cancel();

    String getDialString();

    CharSequence getMessage();

    Phone getPhone();

    State getState();

    CharSequence getUssdCode();

    boolean isCancelable();

    boolean isUssdRequest();
}
