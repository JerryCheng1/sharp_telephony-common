package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

/* compiled from: CommandParams */
class GetInputParams extends CommandParams {
    Input mInput = null;

    GetInputParams(CommandDetails cmdDet, Input input) {
        super(cmdDet);
        this.mInput = input;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean setIcon(Bitmap icon) {
        if (!(icon == null || this.mInput == null)) {
            this.mInput.icon = icon;
        }
        return true;
    }
}
