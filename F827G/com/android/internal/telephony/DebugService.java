package com.android.internal.telephony;

import android.telephony.Rlog;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public class DebugService {
    private static String TAG = "DebugService";

    public DebugService() {
        log("DebugService:");
    }

    private static void log(String str) {
        Rlog.d(TAG, "DebugService " + str);
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        log("dump: +");
        PhoneFactory.dump(fileDescriptor, printWriter, strArr);
        log("dump: -");
    }
}
