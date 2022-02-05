package com.android.internal.telephony.cat;

import android.content.Context;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class CatServiceFactory {
    private static CatService[] sCatServices = null;
    private static final int sSimCount = TelephonyManager.getDefault().getSimCount();
    private static final Object sInstanceLock = new Object();

    public static CatService makeCatService(CommandsInterface ci, Context context, UiccCard ic, int slotId) {
        IccFileHandler fh = null;
        if (sCatServices == null) {
            sCatServices = new CatService[sSimCount];
        }
        if (ci == null || context == null || ic == null) {
            return null;
        }
        int i = 0;
        while (true) {
            if (i < ic.getNumApplications()) {
                UiccCardApplication ca = ic.getApplicationIndex(i);
                if (ca != null && ca.getType() != IccCardApplicationStatus.AppType.APPTYPE_UNKNOWN) {
                    fh = ca.getIccFileHandler();
                    break;
                }
                i++;
            } else {
                break;
            }
        }
        synchronized (sInstanceLock) {
            if (fh == null) {
                return null;
            }
            if (sCatServices[slotId] == null) {
                sCatServices[slotId] = new CatService(ci, context, fh, slotId);
            }
            return sCatServices[slotId];
        }
    }

    public static CatService getCatService(int slotId) {
        if (sCatServices == null) {
            return null;
        }
        return sCatServices[slotId];
    }

    public static void disposeCatService(int slotId) {
        if (sCatServices != null) {
            sCatServices[slotId].dispose();
            sCatServices[slotId] = null;
        }
    }
}
