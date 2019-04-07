package com.android.internal.telephony.uicc;

import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;

public final class IsimFileHandler extends IccFileHandler implements IccConstants {
    static final String LOG_TAG = "IsimFH";

    public IsimFileHandler(UiccCardApplication uiccCardApplication, String str, CommandsInterface commandsInterface) {
        super(uiccCardApplication, str, commandsInterface);
    }

    /* Access modifiers changed, original: protected */
    public String getEFPath(int i) {
        switch (i) {
            case IccConstants.EF_IMPI /*28418*/:
            case IccConstants.EF_DOMAIN /*28419*/:
            case IccConstants.EF_IMPU /*28420*/:
            case 28423:
            case IccConstants.EF_PCSCF /*28425*/:
                return "3F007FFF";
            default:
                return getCommonIccEFPath(i);
        }
    }

    /* Access modifiers changed, original: protected */
    public void logd(String str) {
        Rlog.d(LOG_TAG, str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str) {
        Rlog.e(LOG_TAG, str);
    }
}
