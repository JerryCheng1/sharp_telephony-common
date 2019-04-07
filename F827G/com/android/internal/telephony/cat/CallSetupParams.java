package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

class CallSetupParams extends CommandParams {
    TextMessage mCallMsg;
    TextMessage mConfirmMsg;

    CallSetupParams(CommandDetails commandDetails, TextMessage textMessage, TextMessage textMessage2) {
        super(commandDetails);
        this.mConfirmMsg = textMessage;
        this.mCallMsg = textMessage2;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean setIcon(Bitmap bitmap) {
        if (bitmap != null) {
            if (this.mConfirmMsg != null && this.mConfirmMsg.icon == null) {
                this.mConfirmMsg.icon = bitmap;
                return true;
            } else if (this.mCallMsg != null && this.mCallMsg.icon == null) {
                this.mCallMsg.icon = bitmap;
                return true;
            }
        }
        return false;
    }
}
