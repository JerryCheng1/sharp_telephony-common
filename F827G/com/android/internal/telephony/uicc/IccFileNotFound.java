package com.android.internal.telephony.uicc;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class IccFileNotFound extends IccException {
    /* JADX INFO: Access modifiers changed from: package-private */
    public IccFileNotFound() {
    }

    IccFileNotFound(String s) {
        super(s);
    }

    IccFileNotFound(int ef) {
        super("ICC EF Not Found 0x" + Integer.toHexString(ef));
    }
}
