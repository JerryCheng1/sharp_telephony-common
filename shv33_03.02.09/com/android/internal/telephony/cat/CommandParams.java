package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import com.android.internal.telephony.cat.AppInterface.CommandType;

class CommandParams {
    CommandDetails mCmdDet;
    boolean mLoadIconFailed = false;

    CommandParams(CommandDetails cmdDet) {
        this.mCmdDet = cmdDet;
    }

    /* Access modifiers changed, original: 0000 */
    public CommandType getCommandType() {
        return CommandType.fromInt(this.mCmdDet.typeOfCommand);
    }

    /* Access modifiers changed, original: 0000 */
    public boolean setIcon(Bitmap icon) {
        return true;
    }

    public String toString() {
        return this.mCmdDet.toString();
    }
}
