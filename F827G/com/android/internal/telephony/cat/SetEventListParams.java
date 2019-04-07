package com.android.internal.telephony.cat;

class SetEventListParams extends CommandParams {
    int[] mEventInfo;

    SetEventListParams(CommandDetails commandDetails, int[] iArr) {
        super(commandDetails);
        this.mEventInfo = iArr;
    }
}
