package jp.co.sharp.telephony;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_CDMA_BC;
import jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_CDMA_Errno;
import jp.co.sharp.telephony.OemCdmaTelephonyManager.OemCdmaDataConverter;

public class OemWcdmaTelephonyManager {
    private static final int CDMA_START = 33554432;
    private static boolean DEBUG = false;
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
    private static OemWcdmaTelephonyManager mInstance = null;
    private OemCdmaTelephonyManager mCdmaTelMgr = OemCdmaTelephonyManager.getInstance();
    private Phone mPhone = PhoneFactory.getDefaultPhone();

    private OemWcdmaTelephonyManager() {
    }

    public static OemWcdmaTelephonyManager getInstance() {
        synchronized (OemWcdmaTelephonyManager.class) {
            try {
                if (mInstance == null) {
                    mInstance = new OemWcdmaTelephonyManager();
                }
                OemWcdmaTelephonyManager oemWcdmaTelephonyManager = mInstance;
                return oemWcdmaTelephonyManager;
            } finally {
                Object obj = OemWcdmaTelephonyManager.class;
            }
        }
    }

    private Message obtainMessage(int i) {
        return this.mCdmaTelMgr.getMsgHandler().obtainMessage(i);
    }

    private Message obtainMessage(int i, Object obj) {
        return this.mCdmaTelMgr.getMsgHandler().obtainMessage(i, obj);
    }

    public OEM_RIL_CDMA_Errno getWcdmaEnablement(Handler handler, int i) {
        if (DEBUG) {
            Log.d(TAG, "getWcdmaEnablement() index:" + i);
        }
        int i2 = -1;
        switch (i) {
            case 1:
                i2 = OEM_RIL_REQUEST_WCDMA_GET_B1;
                break;
            case 8:
                i2 = OEM_RIL_REQUEST_WCDMA_GET_B8;
                break;
        }
        Message obtainMessage = obtainMessage(i2);
        this.mCdmaTelMgr.invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(i2), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setWcdmaEnablement(int i, String str, Handler handler, int i2) {
        int i3 = -1;
        switch (i2) {
            case 1:
                i3 = OEM_RIL_REQUEST_WCDMA_SET_B1;
                break;
            case 8:
                i3 = OEM_RIL_REQUEST_WCDMA_SET_B8;
                break;
        }
        Message obtainMessage = obtainMessage(i3);
        OEM_RIL_CDMA_BC oem_ril_cdma_bc = new OEM_RIL_CDMA_BC();
        oem_ril_cdma_bc.status = i;
        this.mCdmaTelMgr.invokeOemRilRequestRaw(OemCdmaDataConverter.BCToByteArr(oem_ril_cdma_bc, i3, str), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }
}
