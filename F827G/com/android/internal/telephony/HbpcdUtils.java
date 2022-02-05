package com.android.internal.telephony;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import com.android.internal.telephony.HbpcdLookup;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class HbpcdUtils {
    private static final boolean DBG = false;
    private static final String LOG_TAG = "HbpcdUtils";
    private ContentResolver resolver;

    public HbpcdUtils(Context context) {
        this.resolver = null;
        this.resolver = context.getContentResolver();
    }

    public int getMcc(int sid, int tz, int DSTflag, boolean isNitzTimeZone) {
        int c3Counter;
        Cursor c2 = this.resolver.query(HbpcdLookup.ArbitraryMccSidMatch.CONTENT_URI, new String[]{"MCC"}, "SID=" + sid, null, null);
        if (c2 != null) {
            if (c2.getCount() == 1) {
                c2.moveToFirst();
                int tmpMcc = c2.getInt(0);
                c2.close();
                return tmpMcc;
            }
            c2.close();
        }
        Cursor c3 = this.resolver.query(HbpcdLookup.MccSidConflicts.CONTENT_URI, new String[]{"MCC"}, "SID_Conflict=" + sid + " and (((" + HbpcdLookup.MccLookup.GMT_OFFSET_LOW + "<=" + tz + ") and (" + tz + "<=" + HbpcdLookup.MccLookup.GMT_OFFSET_HIGH + ") and (0=" + DSTflag + ")) or ((" + HbpcdLookup.MccLookup.GMT_DST_LOW + "<=" + tz + ") and (" + tz + "<=" + HbpcdLookup.MccLookup.GMT_DST_HIGH + ") and (1=" + DSTflag + ")))", null, null);
        if (c3 == null || (c3Counter = c3.getCount()) <= 0) {
            Cursor c5 = this.resolver.query(HbpcdLookup.MccSidRange.CONTENT_URI, new String[]{"MCC"}, "SID_Range_Low<=" + sid + " and " + HbpcdLookup.MccSidRange.RANGE_HIGH + ">=" + sid, null, null);
            if (c5 != null) {
                if (c5.getCount() > 0) {
                    c5.moveToFirst();
                    int tmpMcc2 = c5.getInt(0);
                    c5.close();
                    return tmpMcc2;
                }
                c5.close();
            }
            return 0;
        }
        if (c3Counter > 1) {
            Log.w(LOG_TAG, "something wrong, get more results for 1 conflict SID: " + c3);
        }
        c3.moveToFirst();
        int tmpMcc3 = c3.getInt(0);
        c3.close();
        if (isNitzTimeZone) {
            return tmpMcc3;
        }
        return 0;
    }

    public String getIddByMcc(int mcc) {
        String idd = "";
        Cursor c = null;
        Cursor cur = this.resolver.query(HbpcdLookup.MccIdd.CONTENT_URI, new String[]{HbpcdLookup.MccIdd.IDD}, "MCC=" + mcc, null, null);
        if (cur != null) {
            if (cur.getCount() > 0) {
                cur.moveToFirst();
                idd = cur.getString(0);
            }
            cur.close();
        }
        if (0 != 0) {
            c.close();
        }
        return idd;
    }
}
