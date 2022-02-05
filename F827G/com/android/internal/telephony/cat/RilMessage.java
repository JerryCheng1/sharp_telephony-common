package com.android.internal.telephony.cat;

/* JADX INFO: Access modifiers changed from: package-private */
/* compiled from: CatService.java */
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class RilMessage {
    Object mData;
    int mId;
    ResultCode mResCode;

    /* JADX INFO: Access modifiers changed from: package-private */
    public RilMessage(int msgId, String rawData) {
        this.mId = msgId;
        this.mData = rawData;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public RilMessage(RilMessage other) {
        this.mId = other.mId;
        this.mData = other.mData;
        this.mResCode = other.mResCode;
    }
}
