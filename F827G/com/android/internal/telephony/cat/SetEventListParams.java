package com.android.internal.telephony.cat;

/* compiled from: CommandParams.java */
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
class SetEventListParams extends CommandParams {
    int[] mEventInfo;

    /* JADX INFO: Access modifiers changed from: package-private */
    public SetEventListParams(CommandDetails cmdDet, int[] eventInfo) {
        super(cmdDet);
        this.mEventInfo = eventInfo;
    }
}
