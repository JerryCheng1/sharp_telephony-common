package com.android.internal.telephony.cat;

import java.io.ByteArrayOutputStream;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
abstract class ResponseData {
    public abstract void format(ByteArrayOutputStream byteArrayOutputStream);

    public static void writeLength(ByteArrayOutputStream buf, int length) {
        if (length > 127) {
            buf.write(129);
        }
        buf.write(length);
    }
}
