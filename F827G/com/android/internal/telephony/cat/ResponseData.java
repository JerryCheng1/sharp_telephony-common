package com.android.internal.telephony.cat;

import java.io.ByteArrayOutputStream;

abstract class ResponseData {
    ResponseData() {
    }

    public static void writeLength(ByteArrayOutputStream byteArrayOutputStream, int i) {
        if (i > 127) {
            byteArrayOutputStream.write(129);
        }
        byteArrayOutputStream.write(i);
    }

    public abstract void format(ByteArrayOutputStream byteArrayOutputStream);
}
