package com.android.internal.telephony.uicc;

import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;

public final class IsimFileHandler extends IccFileHandler implements IccConstants {
    static final String LOG_TAG = "IsimFH";

    public IsimFileHandler(UiccCardApplication app, String aid, CommandsInterface ci) {
        super(app, aid, ci);
    }

    /* Access modifiers changed, original: protected */
    public String getEFPath(int efid) {
        switch (efid) {
            case IccConstants.EF_IMPI /*28418*/:
            case IccConstants.EF_DOMAIN /*28419*/:
            case IccConstants.EF_IMPU /*28420*/:
            case 28423:
            case IccConstants.EF_PCSCF /*28425*/:
                return "3F007FFF";
            default:
                return getCommonIccEFPath(efid);
        }
    }

    /* Access modifiers changed, original: protected */
    public void logd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }
}
