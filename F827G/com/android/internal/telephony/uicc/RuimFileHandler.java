package com.android.internal.telephony.uicc;

import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;

public final class RuimFileHandler extends IccFileHandler {
    static final String LOG_TAG = "RuimFH";

    public RuimFileHandler(UiccCardApplication uiccCardApplication, String str, CommandsInterface commandsInterface) {
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
            case IccConstants.EF_SMS /*28476*/:
            case 28481:
            case IccConstants.EF_CSIM_MDN /*28484*/:
            case IccConstants.EF_CSIM_MIPUPP /*28493*/:
            case IccConstants.EF_CSIM_EPRL /*28506*/:
            case IccConstants.EF_CSIM_MODEL /*28545*/:
            case IccConstants.EF_MODEL /*28560*/:
                return "3F007F25";
            default:
                return getCommonIccEFPath(i);
        }
    }

    public void loadEFImgTransparent(int i, int i2, int i3, int i4, Message message) {
        int i5 = i;
        this.mCi.iccIOForApp(192, i5, getEFPath(20256), 0, 0, 10, null, null, this.mAid, obtainMessage(10, i, 0, message));
    }

    /* Access modifiers changed, original: protected */
    public void logd(String str) {
        Rlog.d(LOG_TAG, "[RuimFileHandler] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str) {
        Rlog.e(LOG_TAG, "[RuimFileHandler] " + str);
    }
}
