package com.android.internal.telephony.uicc;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class IccRefreshResponse {
    public static final int REFRESH_RESULT_FILE_UPDATE = 0;
    public static final int REFRESH_RESULT_INIT = 1;
    public static final int REFRESH_RESULT_REBOOT = 11;
    public static final int REFRESH_RESULT_RESET = 2;
    public String aid;
    public int efId;
    public int refreshResult;

    public String toString() {
        return "{" + this.refreshResult + ", " + this.aid + ", " + this.efId + "}";
    }
}
