package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

class DisplayTextParams extends CommandParams {
    TextMessage mTextMsg;

    DisplayTextParams(CommandDetails commandDetails, TextMessage textMessage) {
        super(commandDetails);
        this.mTextMsg = textMessage;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean setIcon(Bitmap bitmap) {
        if (bitmap == null || this.mTextMsg == null) {
            return false;
        }
        this.mTextMsg.icon = bitmap;
        return true;
    }
}
