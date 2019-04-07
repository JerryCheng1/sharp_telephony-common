package com.google.android.mms.util;

import com.google.android.mms.pdu.GenericPdu;

public final class PduCacheEntry {
    private final int mMessageBox;
    private final GenericPdu mPdu;
    private final long mThreadId;

    public PduCacheEntry(GenericPdu genericPdu, int i, long j) {
        this.mPdu = genericPdu;
        this.mMessageBox = i;
        this.mThreadId = j;
    }

    public int getMessageBox() {
        return this.mMessageBox;
    }

    public GenericPdu getPdu() {
        return this.mPdu;
    }

    public long getThreadId() {
        return this.mThreadId;
    }
}
