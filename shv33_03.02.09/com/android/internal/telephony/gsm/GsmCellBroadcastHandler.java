package com.android.internal.telephony.gsm;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Message;
import android.provider.Telephony.Carriers;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import com.android.internal.telephony.CallFailCause;
import com.android.internal.telephony.CellBroadcastHandler;
import com.android.internal.telephony.Phone;
import java.util.HashMap;
import java.util.Iterator;
import jp.co.sharp.android.internal.telephony.EtwsDuplicate;
import jp.co.sharp.android.internal.telephony.EtwsDuplicate.SerialNumberInfo;
import jp.co.sharp.android.internal.telephony.SmsDuplicate.ResultJudgeDuplicate;

public class GsmCellBroadcastHandler extends CellBroadcastHandler {
    private static final byte ALLRECEIVE_MODE = (byte) 3;
    private static final byte COMMERCIAL_MODE = (byte) 0;
    private static boolean DEBUG = false;
    private static final byte KDDITEST_MODE = (byte) 2;
    private static final byte MANUFACTURETEST_MODE = (byte) 1;
    private static boolean OUTPUT_DEBUG_LOG = false;
    protected static final String TAG = "GsmCellBroadcastHandler";
    private static final boolean VDBG = false;
    protected EtwsDuplicate mEtwsDuplicate;
    private final HashMap<SmsCbConcatInfo, byte[][]> mSmsCbPageMap = new HashMap(4);

    private static final class SmsCbConcatInfo {
        private final SmsCbHeader mHeader;
        private final SmsCbLocation mLocation;

        SmsCbConcatInfo(SmsCbHeader header, SmsCbLocation location) {
            this.mHeader = header;
            this.mLocation = location;
        }

        public int hashCode() {
            return (this.mHeader.getSerialNumber() * 31) + this.mLocation.hashCode();
        }

        public boolean equals(Object obj) {
            boolean z = false;
            if (!(obj instanceof SmsCbConcatInfo)) {
                return false;
            }
            SmsCbConcatInfo other = obj;
            if (this.mHeader.getSerialNumber() == other.mHeader.getSerialNumber()) {
                z = this.mLocation.equals(other.mLocation);
            }
            return z;
        }

        public boolean matchesLocation(String plmn, int lac, int cid) {
            return this.mLocation.isInLocationArea(plmn, lac, cid);
        }
    }

    static {
        DEBUG = true;
        OUTPUT_DEBUG_LOG = true;
        if (Build.TYPE.equals(Carriers.USER)) {
            OUTPUT_DEBUG_LOG = false;
            DEBUG = false;
        }
    }

    protected GsmCellBroadcastHandler(Context context, Phone phone) {
        super(TAG, context, phone);
        phone.mCi.setOnNewGsmBroadcastSms(getHandler(), 1, null);
    }

    /* Access modifiers changed, original: protected */
    public void onQuitting() {
        this.mPhone.mCi.unSetOnNewGsmBroadcastSms(getHandler());
        super.onQuitting();
    }

    public static GsmCellBroadcastHandler makeGsmCellBroadcastHandler(Context context, Phone phone) {
        GsmCellBroadcastHandler handler = new GsmCellBroadcastHandler(context, phone);
        handler.start();
        return handler;
    }

    /* Access modifiers changed, original: protected */
    public boolean handleSmsMessage(Message message) {
        if (message.obj instanceof AsyncResult) {
            SmsCbMessage cbMessage = handleGsmBroadcastSms((AsyncResult) message.obj);
            if (cbMessage != null) {
                handleBroadcastSms(cbMessage);
                return true;
            }
        }
        return super.handleSmsMessage(message);
    }

    private SmsCbMessage handleGsmBroadcastSms(AsyncResult ar) {
        try {
            byte[] receivedPdu = (byte[]) ar.result;
            SmsCbHeader header = new SmsCbHeader(receivedPdu);
            String plmn = TelephonyManager.from(this.mContext).getNetworkOperatorForPhone(this.mPhone.getPhoneId());
            int lac = -1;
            int cid = -1;
            CellLocation cl = this.mPhone.getCellLocation();
            if (cl instanceof GsmCellLocation) {
                GsmCellLocation cellLocation = (GsmCellLocation) cl;
                lac = cellLocation.getLac();
                cid = cellLocation.getCid();
            }
            if (OUTPUT_DEBUG_LOG) {
                Rlog.d(TAG, "messageId = " + header.getServiceCategory());
                Rlog.d(TAG, "SerialNumber:geographicalScope = " + header.getGeographicalScope());
                Rlog.d(TAG, "SerialNumber:serialNumber      = " + header.getSerialNumber());
            }
            if (!kddiJudgeDeliveryFromMessageId(header.getServiceCategory())) {
                return null;
            }
            SmsCbLocation location;
            byte[][] pdus;
            if (this.mEtwsDuplicate == null) {
                this.mEtwsDuplicate = new EtwsDuplicate(this.mContext, 1, true);
            }
            EtwsDuplicate etwsDuplicate = this.mEtwsDuplicate;
            etwsDuplicate.getClass();
            ResultJudgeDuplicate judgeInfo = this.mEtwsDuplicate.checkEtwsDuplicate(new SerialNumberInfo(header.getGeographicalScope(), header.getSerialNumber()), header.isEtwsPrimaryNotification());
            if (judgeInfo != null) {
                if (judgeInfo.mIsSame) {
                    if (OUTPUT_DEBUG_LOG) {
                        Rlog.d(TAG, "checkEtwsDuplicate is Same Data");
                    }
                    return null;
                }
                this.mEtwsDuplicate.updateEtwsDuplicate(header.getGeographicalScope(), header.getSerialNumber(), header.isEtwsPrimaryNotification());
            }
            switch (header.getGeographicalScope()) {
                case 0:
                case 3:
                    location = new SmsCbLocation(plmn, lac, cid);
                    break;
                case 2:
                    location = new SmsCbLocation(plmn, lac, -1);
                    break;
                default:
                    location = new SmsCbLocation(plmn);
                    break;
            }
            int pageCount = header.getNumberOfPages();
            if (pageCount > 1) {
                SmsCbConcatInfo concatInfo = new SmsCbConcatInfo(header, location);
                pdus = (byte[][]) this.mSmsCbPageMap.get(concatInfo);
                if (pdus == null) {
                    pdus = new byte[pageCount][];
                    this.mSmsCbPageMap.put(concatInfo, pdus);
                }
                pdus[header.getPageIndex() - 1] = receivedPdu;
                for (byte[] pdu : pdus) {
                    if (pdu == null) {
                        return null;
                    }
                }
                this.mSmsCbPageMap.remove(concatInfo);
            } else {
                pdus = new byte[][]{receivedPdu};
            }
            Iterator<SmsCbConcatInfo> iter = this.mSmsCbPageMap.keySet().iterator();
            while (iter.hasNext()) {
                if (!((SmsCbConcatInfo) iter.next()).matchesLocation(plmn, lac, cid)) {
                    iter.remove();
                }
            }
            return GsmSmsCbMessage.createSmsCbMessage(header, location, pdus);
        } catch (RuntimeException e) {
            loge("Error in decoding SMS CB pdu", e);
            return null;
        }
    }

    /* Access modifiers changed, original: protected */
    public boolean kddiJudgeDeliveryFromMessageId(int messageId) {
        int maintenanceMode = 0;
        if (DEBUG) {
            try {
                maintenanceMode = this.mContext.createPackageContext("jp.co.sharp.maintenanceMode", 2).getSharedPreferences("pref", 4).getInt("maintenanceMode", 0);
            } catch (NameNotFoundException e) {
                Rlog.e(TAG, "maintenanceMode app not found");
            }
        }
        byte[] service = new byte[]{(byte) ((messageId >>> 8) & 12)};
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "Deliver messageId = " + messageId);
            Rlog.d(TAG, "Deliver Classification = " + Integer.toHexString(service[0]));
            Rlog.d(TAG, "maintenanceMode = " + maintenanceMode);
        }
        boolean isDelivery = false;
        switch (maintenanceMode) {
            case 0:
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "Deliver classification is Commercial");
                }
                if (messageId == 4352 || messageId == SmsCbConstants.MESSAGE_ID_ETWS_TSUNAMI_WARNING || ((messageId >= SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE && messageId <= 4359) || messageId == 40963 || (messageId >= 43009 && messageId <= 43263))) {
                    isDelivery = true;
                    break;
                }
            case 1:
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "Deliver classification is ManufactureTestmode");
                }
                if (messageId != SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE) {
                    if (messageId < 43521 || messageId > 43775) {
                        if (messageId >= 0 && messageId <= CallFailCause.ERROR_UNSPECIFIED && messageId != 4352 && messageId != SmsCbConstants.MESSAGE_ID_ETWS_TSUNAMI_WARNING && ((messageId < SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE || messageId > 4359) && messageId != 40963 && ((messageId < 43009 || messageId > 43263) && (messageId < 43776 || messageId > 44031)))) {
                            isDelivery = true;
                            break;
                        }
                    }
                    isDelivery = true;
                    break;
                }
                isDelivery = true;
                break;
                break;
            case 2:
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "Deliver classification is KDDITestmode");
                }
                if (messageId < 43776 || messageId > 44031) {
                    if (messageId >= 0 && messageId <= CallFailCause.ERROR_UNSPECIFIED && messageId != 4352 && messageId != SmsCbConstants.MESSAGE_ID_ETWS_TSUNAMI_WARNING && ((messageId < SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE || messageId > 4359) && messageId != 40963 && ((messageId < 43009 || messageId > 43263) && messageId != SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE && (messageId < 43521 || messageId > 43775)))) {
                        isDelivery = true;
                        break;
                    }
                }
                isDelivery = true;
                break;
                break;
            case 3:
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "Deliver classification is AllRecieveMode");
                }
                if (messageId >= 0 && messageId <= CallFailCause.ERROR_UNSPECIFIED) {
                    isDelivery = true;
                    break;
                }
        }
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "isDelivery is " + isDelivery);
        }
        return isDelivery;
    }
}
