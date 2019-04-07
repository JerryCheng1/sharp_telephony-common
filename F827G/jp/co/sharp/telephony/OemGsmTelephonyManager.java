package jp.co.sharp.telephony;

import android.os.Handler;
import android.os.Message;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_CDMA_BC;
import jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_CDMA_Errno;
import jp.co.sharp.telephony.OemCdmaTelephonyManager.OemCdmaDataConverter;

public class OemGsmTelephonyManager {
    private static final int CDMA_START = 33554432;
    public static final int GSM_1800 = 1800;
    public static final int GSM_1900 = 1900;
    public static final int GSM_900 = 900;
    public static final int OEM_RIL_REQUEST_GSM_GET_1800 = 33554676;
    public static final int OEM_RIL_REQUEST_GSM_GET_1900 = 33554678;
    public static final int OEM_RIL_REQUEST_GSM_GET_850 = 33554672;
    public static final int OEM_RIL_REQUEST_GSM_GET_900 = 33554674;
    public static final int OEM_RIL_REQUEST_GSM_SET_1800 = 33554677;
    public static final int OEM_RIL_REQUEST_GSM_SET_1900 = 33554679;
    public static final int OEM_RIL_REQUEST_GSM_SET_850 = 33554673;
    public static final int OEM_RIL_REQUEST_GSM_SET_900 = 33554675;
    private static final String TAG = "OemGsmTelephonyManager";
    private static OemGsmTelephonyManager mInstance = null;
    private OemCdmaTelephonyManager mCdmaTelMgr = OemCdmaTelephonyManager.getInstance();
    private Phone mPhone = PhoneFactory.getDefaultPhone();

    private OemGsmTelephonyManager() {
    }

    public static OemGsmTelephonyManager getInstance() {
        synchronized (OemGsmTelephonyManager.class) {
            try {
                if (mInstance == null) {
                    mInstance = new OemGsmTelephonyManager();
                }
                OemGsmTelephonyManager oemGsmTelephonyManager = mInstance;
                return oemGsmTelephonyManager;
            } finally {
                Object obj = OemGsmTelephonyManager.class;
            }
        }
    }

    private Message obtainMessage(int i) {
        return this.mCdmaTelMgr.getMsgHandler().obtainMessage(i);
    }

    private Message obtainMessage(int i, Object obj) {
        return this.mCdmaTelMgr.getMsgHandler().obtainMessage(i, obj);
    }

    public OEM_RIL_CDMA_Errno getGsmEnablement(Handler handler, int i) {
        int i2 = -1;
        switch (i) {
            case GSM_900 /*900*/:
                i2 = OEM_RIL_REQUEST_GSM_GET_900;
                break;
            case GSM_1800 /*1800*/:
                i2 = OEM_RIL_REQUEST_GSM_GET_1800;
                break;
            case GSM_1900 /*1900*/:
                i2 = OEM_RIL_REQUEST_GSM_GET_1900;
                break;
        }
        Message obtainMessage = obtainMessage(i2);
        this.mCdmaTelMgr.invokeOemRilRequestRaw(OemCdmaDataConverter.writeHookHeader(i2), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OEM_RIL_CDMA_Errno setGsmEnablement(int i, String str, Handler handler, int i2) {
        int i3 = -1;
        switch (i2) {
            case GSM_900 /*900*/:
                i3 = OEM_RIL_REQUEST_GSM_SET_900;
                break;
            case GSM_1800 /*1800*/:
                i3 = OEM_RIL_REQUEST_GSM_SET_1800;
                break;
            case GSM_1900 /*1900*/:
                i3 = OEM_RIL_REQUEST_GSM_SET_1900;
                break;
        }
        Message obtainMessage = obtainMessage(i3);
        OEM_RIL_CDMA_BC oem_ril_cdma_bc = new OEM_RIL_CDMA_BC();
        oem_ril_cdma_bc.status = i;
        this.mCdmaTelMgr.invokeOemRilRequestRaw(OemCdmaDataConverter.BCToByteArr(oem_ril_cdma_bc, i3, str), obtainMessage, handler);
        return OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }
}
