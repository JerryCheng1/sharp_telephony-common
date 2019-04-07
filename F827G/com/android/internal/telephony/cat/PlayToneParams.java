package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

class PlayToneParams extends CommandParams {
    ToneSettings mSettings;
    TextMessage mTextMsg;

    PlayToneParams(CommandDetails commandDetails, TextMessage textMessage, Tone tone, Duration duration, boolean z) {
        super(commandDetails);
        this.mTextMsg = textMessage;
        this.mSettings = new ToneSettings(duration, tone, z);
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
