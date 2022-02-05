package com.google.android.mms.util;

import com.google.android.mms.pdu.GenericPdu;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class PduCacheEntry {
    private final int mMessageBox;
    private final GenericPdu mPdu;
    private final long mThreadId;

    public PduCacheEntry(GenericPdu pdu, int msgBox, long threadId) {
        this.mPdu = pdu;
        this.mMessageBox = msgBox;
        this.mThreadId = threadId;
    }

    public GenericPdu getPdu() {
        return this.mPdu;
    }

    public int getMessageBox() {
        return this.mMessageBox;
    }

    public long getThreadId() {
        return this.mThreadId;
    }
}
