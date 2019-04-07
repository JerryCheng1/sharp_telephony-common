package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

class GetInputParams extends CommandParams {
    Input mInput = null;

    GetInputParams(CommandDetails commandDetails, Input input) {
        super(commandDetails);
        this.mInput = input;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean setIcon(Bitmap bitmap) {
        if (!(bitmap == null || this.mInput == null)) {
            this.mInput.icon = bitmap;
        }
        return true;
    }
}
