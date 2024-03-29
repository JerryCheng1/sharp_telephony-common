package com.android.internal.telephony;

import android.telephony.Rlog;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class DebugService {
    private static String TAG = "DebugService";

    public DebugService() {
        log("DebugService:");
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        log("dump: +");
        PhoneFactory.dump(fd, pw, args);
        log("dump: -");
    }

    private static void log(String s) {
        Rlog.d(TAG, "DebugService " + s);
    }
}
