package com.android.internal.telephony.uicc;

import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;

public final class CsimFileHandler extends IccFileHandler implements IccConstants {
    static final String LOG_TAG = "CsimFH";

    public CsimFileHandler(UiccCardApplication uiccCardApplication, String str, CommandsInterface commandsInterface) {
        super(uiccCardApplication, str, commandsInterface);
    }

    /* Access modifiers changed, original: protected */
    public String getEFPath(int i) {
        switch (i) {
            case 20256:
            case IccConstants.EF_CSIM_MSPL /*20257*/:
                return "3F007F105F3C";
            case IccConstants.EF_CSIM_IMSIM /*28450*/:
            case IccConstants.EF_CSIM_CDMAHOME /*28456*/:
            case IccConstants.EF_CSIM_PRL /*28464*/:
            case IccConstants.EF_RUIM_ID /*28465*/:
            case IccConstants.EF_CST /*28466*/:
            case 28474:
            case IccConstants.EF_FDN /*28475*/:
            case IccConstants.EF_SMS /*28476*/:
            case IccConstants.EF_MSISDN /*28480*/:
            case 28481:
            case IccConstants.EF_CSIM_MDN /*28484*/:
            case IccConstants.EF_CSIM_MIPUPP /*28493*/:
            case IccConstants.EF_CSIM_EPRL /*28506*/:
            case IccConstants.EF_CSIM_MODEL /*28545*/:
            case IccConstants.EF_MODEL /*28560*/:
                return "3F007FFF";
            default:
                String commonIccEFPath = getCommonIccEFPath(i);
                return commonIccEFPath == null ? "3F007F105F3A" : commonIccEFPath;
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
