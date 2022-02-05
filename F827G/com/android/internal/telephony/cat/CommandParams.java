package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import com.android.internal.telephony.cat.AppInterface;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
class CommandParams {
    CommandDetails mCmdDet;
    boolean mLoadIconFailed = false;

    /* JADX INFO: Access modifiers changed from: package-private */
    public CommandParams(CommandDetails cmdDet) {
        this.mCmdDet = cmdDet;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public AppInterface.CommandType getCommandType() {
        return AppInterface.CommandType.fromInt(this.mCmdDet.typeOfCommand);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean setIcon(Bitmap icon) {
        return true;
    }

    public String toString() {
        return this.mCmdDet.toString();
    }
}
