package com.android.internal.telephony.cat;

/* compiled from: CommandParams.java */
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
class ActivateParams extends CommandParams {
    int mActivateTarget;

    ActivateParams(CommandDetails cmdDet, int target) {
        super(cmdDet);
        this.mActivateTarget = target;
    }
}
