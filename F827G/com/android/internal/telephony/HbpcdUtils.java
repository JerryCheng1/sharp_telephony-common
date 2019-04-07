package com.android.internal.telephony;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import com.android.internal.telephony.HbpcdLookup.ArbitraryMccSidMatch;
import com.android.internal.telephony.HbpcdLookup.MccIdd;
import com.android.internal.telephony.HbpcdLookup.MccLookup;
import com.android.internal.telephony.HbpcdLookup.MccSidConflicts;
import com.android.internal.telephony.HbpcdLookup.MccSidRange;

public final class HbpcdUtils {
    private static final boolean DBG = false;
    private static final String LOG_TAG = "HbpcdUtils";
    private ContentResolver resolver = null;

    public HbpcdUtils(Context context) {
        this.resolver = context.getContentResolver();
    }

    public String getIddByMcc(int i) {
        String str = "";
        ContentResolver contentResolver = this.resolver;
        Uri uri = MccIdd.CONTENT_URI;
        String str2 = "MCC=" + i;
        Cursor query = contentResolver.query(uri, new String[]{MccIdd.IDD}, str2, null, null);
        if (query == null) {
            return str;
        }
        String string;
        if (query.getCount() > 0) {
            query.moveToFirst();
            string = query.getString(0);
        } else {
            string = str;
        }
        query.close();
        return string;
    }

    public int getMcc(int i, int i2, int i3, boolean z) {
        int i4;
        Cursor query = this.resolver.query(ArbitraryMccSidMatch.CONTENT_URI, new String[]{"MCC"}, "SID=" + i, null, null);
        if (query != null) {
            if (query.getCount() == 1) {
                query.moveToFirst();
                i4 = query.getInt(0);
                query.close();
                return i4;
            }
            query.close();
        }
        query = this.resolver.query(MccSidConflicts.CONTENT_URI, new String[]{"MCC"}, "SID_Conflict=" + i + " and (((" + MccLookup.GMT_OFFSET_LOW + "<=" + i2 + ") and (" + i2 + "<=" + MccLookup.GMT_OFFSET_HIGH + ") and (" + "0=" + i3 + ")) or ((" + MccLookup.GMT_DST_LOW + "<=" + i2 + ") and (" + i2 + "<=" + MccLookup.GMT_DST_HIGH + ") and (" + "1=" + i3 + ")))", null, null);
        if (query != null) {
            i4 = query.getCount();
            if (i4 > 0) {
                if (i4 > 1) {
                    Log.w(LOG_TAG, "something wrong, get more results for 1 conflict SID: " + query);
                }
                query.moveToFirst();
                i4 = query.getInt(0);
                query.close();
                return !z ? 0 : i4;
            }
        }
        query = this.resolver.query(MccSidRange.CONTENT_URI, new String[]{"MCC"}, "SID_Range_Low<=" + i + " and " + MccSidRange.RANGE_HIGH + ">=" + i, null, null);
        if (query != null) {
            if (query.getCount() > 0) {
                query.moveToFirst();
                i4 = query.getInt(0);
                query.close();
                return i4;
            }
            query.close();
        }
        return 0;
    }
}
