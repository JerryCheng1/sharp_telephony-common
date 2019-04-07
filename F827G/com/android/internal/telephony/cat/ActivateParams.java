package com.android.internal.telephony.cat;

class ActivateParams extends CommandParams {
    int mActivateTarget;

    ActivateParams(CommandDetails commandDetails, int i) {
        super(commandDetails);
        this.mActivateTarget = i;
    }
}
