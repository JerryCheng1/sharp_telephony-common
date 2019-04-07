package com.android.internal.telephony.cat;

import android.telephony.TelephonyManager;

public class CatServiceFactory {
    private static CatService[] sCatServices = null;
    private static final Object sInstanceLock = new Object();
    private static final int sSimCount = TelephonyManager.getDefault().getSimCount();

    public static void disposeCatService(int i) {
        if (sCatServices != null) {
            sCatServices[i].dispose();
            sCatServices[i] = null;
        }
    }

    public static CatService getCatService(int i) {
        return sCatServices == null ? null : sCatServices[i];
    }

    /* JADX WARNING: Missing block: B:39:?, code skipped:
            return sCatServices[r8];
     */
    public static com.android.internal.telephony.cat.CatService makeCatService(com.android.internal.telephony.CommandsInterface r5, android.content.Context r6, com.android.internal.telephony.uicc.UiccCard r7, int r8) {
        /*
        r1 = 0;
        r0 = sCatServices;
        if (r0 != 0) goto L_0x000b;
    L_0x0005:
        r0 = sSimCount;
        r0 = new com.android.internal.telephony.cat.CatService[r0];
        sCatServices = r0;
    L_0x000b:
        if (r5 == 0) goto L_0x0011;
    L_0x000d:
        if (r6 == 0) goto L_0x0011;
    L_0x000f:
        if (r7 != 0) goto L_0x0012;
    L_0x0011:
        return r1;
    L_0x0012:
        r0 = 0;
    L_0x0013:
        r2 = r7.getNumApplications();
        if (r0 >= r2) goto L_0x004d;
    L_0x0019:
        r2 = r7.getApplicationIndex(r0);
        if (r2 == 0) goto L_0x0035;
    L_0x001f:
        r3 = r2.getType();
        r4 = com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType.APPTYPE_UNKNOWN;
        if (r3 == r4) goto L_0x0035;
    L_0x0027:
        r0 = r2.getIccFileHandler();
    L_0x002b:
        r2 = sInstanceLock;
        monitor-enter(r2);
        if (r0 != 0) goto L_0x0038;
    L_0x0030:
        monitor-exit(r2);	 Catch:{ all -> 0x0032 }
        goto L_0x0011;
    L_0x0032:
        r0 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x0032 }
        throw r0;
    L_0x0035:
        r0 = r0 + 1;
        goto L_0x0013;
    L_0x0038:
        r1 = sCatServices;	 Catch:{ all -> 0x0032 }
        r1 = r1[r8];	 Catch:{ all -> 0x0032 }
        if (r1 != 0) goto L_0x0047;
    L_0x003e:
        r1 = sCatServices;	 Catch:{ all -> 0x0032 }
        r3 = new com.android.internal.telephony.cat.CatService;	 Catch:{ all -> 0x0032 }
        r3.<init>(r5, r6, r0, r8);	 Catch:{ all -> 0x0032 }
        r1[r8] = r3;	 Catch:{ all -> 0x0032 }
    L_0x0047:
        monitor-exit(r2);	 Catch:{ all -> 0x0032 }
        r0 = sCatServices;
        r1 = r0[r8];
        goto L_0x0011;
    L_0x004d:
        r0 = r1;
        goto L_0x002b;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cat.CatServiceFactory.makeCatService(com.android.internal.telephony.CommandsInterface, android.content.Context, com.android.internal.telephony.uicc.UiccCard, int):com.android.internal.telephony.cat.CatService");
    }
}
