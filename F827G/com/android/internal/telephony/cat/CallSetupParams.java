package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

/* JADX INFO: Access modifiers changed from: package-private */
/* compiled from: CommandParams.java */
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class CallSetupParams extends CommandParams {
    TextMessage mCallMsg;
    TextMessage mConfirmMsg;

    /* JADX INFO: Access modifiers changed from: package-private */
    public CallSetupParams(CommandDetails cmdDet, TextMessage confirmMsg, TextMessage callMsg) {
        super(cmdDet);
        this.mConfirmMsg = confirmMsg;
        this.mCallMsg = callMsg;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.android.internal.telephony.cat.CommandParams
    public boolean setIcon(Bitmap icon) {
        if (icon == null) {
            return false;
        }
        if (this.mConfirmMsg != null && this.mConfirmMsg.icon == null) {
            this.mConfirmMsg.icon = icon;
            return true;
        } else if (this.mCallMsg == null || this.mCallMsg.icon != null) {
            return false;
        } else {
            this.mCallMsg.icon = icon;
            return true;
        }
    }
}
