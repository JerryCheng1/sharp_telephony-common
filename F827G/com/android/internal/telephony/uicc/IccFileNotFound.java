package com.android.internal.telephony.uicc;

public class IccFileNotFound extends IccException {
    IccFileNotFound() {
    }

    IccFileNotFound(int i) {
        super("ICC EF Not Found 0x" + Integer.toHexString(i));
    }

    IccFileNotFound(String str) {
        super(str);
    }
}
