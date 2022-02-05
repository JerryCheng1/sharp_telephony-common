package com.android.internal.telephony.uicc;

import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class IsimFileHandler extends IccFileHandler implements IccConstants {
    static final String LOG_TAG = "IsimFH";

    public IsimFileHandler(UiccCardApplication app, String aid, CommandsInterface ci) {
        super(app, aid, ci);
    }

    @Override // com.android.internal.telephony.uicc.IccFileHandler
    protected String getEFPath(int efid) {
        switch (efid) {
            case IccConstants.EF_IMPI /* 28418 */:
            case IccConstants.EF_DOMAIN /* 28419 */:
            case IccConstants.EF_IMPU /* 28420 */:
            case 28423:
            case IccConstants.EF_PCSCF /* 28425 */:
                return "3F007FFF";
            case IccConstants.EF_LI /* 28421 */:
            case 28422:
            case 28424:
            default:
                return getCommonIccEFPath(efid);
        }
    }

    @Override // com.android.internal.telephony.uicc.IccFileHandler
    protected void logd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    @Override // com.android.internal.telephony.uicc.IccFileHandler
    protected void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }
}
