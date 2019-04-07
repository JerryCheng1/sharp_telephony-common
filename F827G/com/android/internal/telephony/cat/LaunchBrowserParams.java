package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

class LaunchBrowserParams extends CommandParams {
    TextMessage mConfirmMsg;
    LaunchBrowserMode mMode;
    String mUrl;

    LaunchBrowserParams(CommandDetails commandDetails, TextMessage textMessage, String str, LaunchBrowserMode launchBrowserMode) {
        super(commandDetails);
        this.mConfirmMsg = textMessage;
        this.mMode = launchBrowserMode;
        this.mUrl = str;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean setIcon(Bitmap bitmap) {
        if (bitmap == null || this.mConfirmMsg == null) {
            return false;
        }
        this.mConfirmMsg.icon = bitmap;
        return true;
    }
}
