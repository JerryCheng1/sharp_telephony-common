package jp.co.sharp.telephony;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class OemWcdmaTelephonyManager {
    private static final int CDMA_START = 33554432;
    public static final int OEM_RIL_REQUEST_WCDMA_GET_B1 = 33554704;
    public static final int OEM_RIL_REQUEST_WCDMA_GET_B11 = 33554716;
    public static final int OEM_RIL_REQUEST_WCDMA_GET_B2 = 33554706;
    public static final int OEM_RIL_REQUEST_WCDMA_GET_B4 = 33554708;
    public static final int OEM_RIL_REQUEST_WCDMA_GET_B5 = 33554710;
    public static final int OEM_RIL_REQUEST_WCDMA_GET_B8 = 33554712;
    public static final int OEM_RIL_REQUEST_WCDMA_GET_B9 = 33554714;
    public static final int OEM_RIL_REQUEST_WCDMA_SET_B1 = 33554705;
    public static final int OEM_RIL_REQUEST_WCDMA_SET_B11 = 33554717;
    public static final int OEM_RIL_REQUEST_WCDMA_SET_B2 = 33554707;
    public static final int OEM_RIL_REQUEST_WCDMA_SET_B4 = 33554709;
    public static final int OEM_RIL_REQUEST_WCDMA_SET_B5 = 33554711;
    public static final int OEM_RIL_REQUEST_WCDMA_SET_B8 = 33554713;
    public static final int OEM_RIL_REQUEST_WCDMA_SET_B9 = 33554715;
    private static final String TAG = "OemWcdmaTelephonyManager";
    public static final int WCDMA_B1 = 1;
    public static final int WCDMA_B8 = 8;
    private OemCdmaTelephonyManager mCdmaTelMgr = OemCdmaTelephonyManager.getInstance();
    private Phone mPhone = PhoneFactory.getDefaultPhone();
    private static boolean DEBUG = false;
    private static OemWcdmaTelephonyManager mInstance = null;

    private OemWcdmaTelephonyManager() {
    }

    public static synchronized OemWcdmaTelephonyManager getInstance() {
        OemWcdmaTelephonyManager oemWcdmaTelephonyManager;
        synchronized (OemWcdmaTelephonyManager.class) {
            if (mInstance == null) {
                mInstance = new OemWcdmaTelephonyManager();
            }
            oemWcdmaTelephonyManager = mInstance;
        }
        return oemWcdmaTelephonyManager;
    }

    private Message obtainMessage(int what) {
        return this.mCdmaTelMgr.getMsgHandler().obtainMessage(what);
    }

    private Message obtainMessage(int what, Object obj) {
        return this.mCdmaTelMgr.getMsgHandler().obtainMessage(what, obj);
    }

    public OemCdmaTelephonyManager.OEM_RIL_CDMA_Errno getWcdmaEnablement(Handler msgH, int index) {
        if (DEBUG) {
            Log.d(TAG, "getWcdmaEnablement() index:" + index);
        }
        int msgid = -1;
        switch (index) {
            case 1:
                msgid = OEM_RIL_REQUEST_WCDMA_GET_B1;
                break;
            case 8:
                msgid = OEM_RIL_REQUEST_WCDMA_GET_B8;
                break;
        }
        Message msg = obtainMessage(msgid);
        this.mCdmaTelMgr.invokeOemRilRequestRaw(OemCdmaTelephonyManager.OemCdmaDataConverter.writeHookHeader(msgid), msg, msgH);
        return OemCdmaTelephonyManager.OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OemCdmaTelephonyManager.OEM_RIL_CDMA_Errno setWcdmaEnablement(int status, String spcLockCode, Handler msgH, int index) {
        int msgid = -1;
        switch (index) {
            case 1:
                msgid = OEM_RIL_REQUEST_WCDMA_SET_B1;
                break;
            case 8:
                msgid = OEM_RIL_REQUEST_WCDMA_SET_B8;
                break;
        }
        Message msg = obtainMessage(msgid);
        OemCdmaTelephonyManager.OEM_RIL_CDMA_BC bc = new OemCdmaTelephonyManager.OEM_RIL_CDMA_BC();
        bc.status = status;
        this.mCdmaTelMgr.invokeOemRilRequestRaw(OemCdmaTelephonyManager.OemCdmaDataConverter.BCToByteArr(bc, msgid, spcLockCode), msg, msgH);
        return OemCdmaTelephonyManager.OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }
}
