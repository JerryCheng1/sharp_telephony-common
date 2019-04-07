package com.android.internal.telephony.cat;

class RilMessage {
    Object mData;
    int mId;
    ResultCode mResCode;

    RilMessage(int i, String str) {
        this.mId = i;
        this.mData = str;
    }

    RilMessage(RilMessage rilMessage) {
        this.mId = rilMessage.mId;
        this.mData = rilMessage.mData;
        this.mResCode = rilMessage.mResCode;
    }
}
