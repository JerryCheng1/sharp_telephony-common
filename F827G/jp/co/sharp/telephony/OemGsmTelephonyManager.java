package jp.co.sharp.telephony;

import android.os.Handler;
import android.os.Message;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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

    public static synchronized OemGsmTelephonyManager getInstance() {
        OemGsmTelephonyManager oemGsmTelephonyManager;
        synchronized (OemGsmTelephonyManager.class) {
            if (mInstance == null) {
                mInstance = new OemGsmTelephonyManager();
            }
            oemGsmTelephonyManager = mInstance;
        }
        return oemGsmTelephonyManager;
    }

    private Message obtainMessage(int what) {
        return this.mCdmaTelMgr.getMsgHandler().obtainMessage(what);
    }

    private Message obtainMessage(int what, Object obj) {
        return this.mCdmaTelMgr.getMsgHandler().obtainMessage(what, obj);
    }

    public OemCdmaTelephonyManager.OEM_RIL_CDMA_Errno getGsmEnablement(Handler msgH, int index) {
        int msgid = -1;
        switch (index) {
            case GSM_900 /* 900 */:
                msgid = OEM_RIL_REQUEST_GSM_GET_900;
                break;
            case GSM_1800 /* 1800 */:
                msgid = OEM_RIL_REQUEST_GSM_GET_1800;
                break;
            case GSM_1900 /* 1900 */:
                msgid = OEM_RIL_REQUEST_GSM_GET_1900;
                break;
        }
        Message msg = obtainMessage(msgid);
        this.mCdmaTelMgr.invokeOemRilRequestRaw(OemCdmaTelephonyManager.OemCdmaDataConverter.writeHookHeader(msgid), msg, msgH);
        return OemCdmaTelephonyManager.OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }

    public OemCdmaTelephonyManager.OEM_RIL_CDMA_Errno setGsmEnablement(int status, String spcLockCode, Handler msgH, int index) {
        int msgid = -1;
        switch (index) {
            case GSM_900 /* 900 */:
                msgid = OEM_RIL_REQUEST_GSM_SET_900;
                break;
            case GSM_1800 /* 1800 */:
                msgid = OEM_RIL_REQUEST_GSM_SET_1800;
                break;
            case GSM_1900 /* 1900 */:
                msgid = OEM_RIL_REQUEST_GSM_SET_1900;
                break;
        }
        Message msg = obtainMessage(msgid);
        OemCdmaTelephonyManager.OEM_RIL_CDMA_BC bc = new OemCdmaTelephonyManager.OEM_RIL_CDMA_BC();
        bc.status = status;
        this.mCdmaTelMgr.invokeOemRilRequestRaw(OemCdmaTelephonyManager.OemCdmaDataConverter.BCToByteArr(bc, msgid, spcLockCode), msg, msgH);
        return OemCdmaTelephonyManager.OEM_RIL_CDMA_Errno.OEM_RIL_CDMA_SUCCESS;
    }
}
