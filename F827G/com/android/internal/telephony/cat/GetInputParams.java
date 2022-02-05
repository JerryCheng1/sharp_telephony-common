package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

/* compiled from: CommandParams.java */
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
class GetInputParams extends CommandParams {
    Input mInput;

    /* JADX INFO: Access modifiers changed from: package-private */
    public GetInputParams(CommandDetails cmdDet, Input input) {
        super(cmdDet);
        this.mInput = null;
        this.mInput = input;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.android.internal.telephony.cat.CommandParams
    public boolean setIcon(Bitmap icon) {
        if (icon == null || this.mInput == null) {
            return true;
        }
        this.mInput.icon = icon;
        return true;
    }
}
