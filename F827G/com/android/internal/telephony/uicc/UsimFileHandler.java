package com.android.internal.telephony.uicc;

import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class UsimFileHandler extends IccFileHandler implements IccConstants {
    static final String LOG_TAG = "UsimFH";

    public UsimFileHandler(UiccCardApplication app, String aid, CommandsInterface ci) {
        super(app, aid, ci);
    }

    @Override // com.android.internal.telephony.uicc.IccFileHandler
    protected String getEFPath(int efid) {
        switch (efid) {
            case IccConstants.EF_LOCK /* 12272 */:
                return IccConstants.MF_SIM;
            case IccConstants.EF_PBR /* 20272 */:
                return "3F007F105F3A";
            case IccConstants.EF_LI /* 28421 */:
            case 28423:
            case IccConstants.EF_VOICE_MAIL_INDICATOR_CPHS /* 28433 */:
            case IccConstants.EF_CFF_CPHS /* 28435 */:
            case IccConstants.EF_SPN_CPHS /* 28436 */:
            case IccConstants.EF_CSP_CPHS /* 28437 */:
            case IccConstants.EF_INFO_CPHS /* 28438 */:
            case IccConstants.EF_MAILBOX_CPHS /* 28439 */:
            case IccConstants.EF_SPN_SHORT_CPHS /* 28440 */:
            case IccConstants.EF_SST /* 28472 */:
            case IccConstants.EF_FDN /* 28475 */:
            case IccConstants.EF_SMS /* 28476 */:
            case IccConstants.EF_GID1 /* 28478 */:
            case IccConstants.EF_MSISDN /* 28480 */:
            case IccConstants.EF_SPN /* 28486 */:
            case IccConstants.EF_EXT2 /* 28491 */:
            case IccConstants.EF_EXT5 /* 28494 */:
            case IccConstants.EF_PLMNWACT /* 28512 */:
            case IccConstants.EF_AD /* 28589 */:
            case IccConstants.EF_PNN /* 28613 */:
            case IccConstants.EF_OPL /* 28614 */:
            case IccConstants.EF_MBDN /* 28615 */:
            case IccConstants.EF_EXT6 /* 28616 */:
            case IccConstants.EF_MBI /* 28617 */:
            case IccConstants.EF_MWIS /* 28618 */:
            case IccConstants.EF_CFIS /* 28619 */:
            case IccConstants.EF_SPDI /* 28621 */:
                return "3F007FFF";
            default:
                String path = getCommonIccEFPath(efid);
                if (path == null) {
                    return "3F007F105F3A";
                }
                return path;
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
