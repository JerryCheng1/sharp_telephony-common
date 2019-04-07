package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

class BIPClientParams extends CommandParams {
    boolean mHasAlphaId;
    TextMessage mTextMsg;

    BIPClientParams(CommandDetails commandDetails, TextMessage textMessage, boolean z) {
        super(commandDetails);
        this.mTextMsg = textMessage;
        this.mHasAlphaId = z;
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
