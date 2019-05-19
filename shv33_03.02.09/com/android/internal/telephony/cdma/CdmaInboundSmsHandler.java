package com.android.internal.telephony.cdma;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Message;
import android.provider.Telephony.Carriers;
import android.provider.Telephony.CellBroadcasts;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CellBroadcastHandler;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.InboundSmsTracker;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SmsConstants.MessageClass;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.TelephonyComponentFactory;
import com.android.internal.telephony.WspTypeDecoder;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.util.HexDump;
import jp.co.sharp.android.internal.telephony.BcsmsDuplicate;
import jp.co.sharp.android.internal.telephony.SmsDuplicate;
import jp.co.sharp.android.internal.telephony.SmsDuplicate.ResultJudgeDuplicate;
import jp.co.sharp.android.internal.telephony.SmsDuplicate.SmsAccessory;

public class CdmaInboundSmsHandler extends InboundSmsHandler {
    private static final byte ALLRECEIVE_MODE = (byte) 3;
    private static final byte COMMERCIAL_MODE = (byte) 0;
    private static boolean DEBUG = false;
    private static final byte KDDITEST_MODE = (byte) 2;
    private static final byte MANUFACTURETEST_MODE = (byte) 1;
    private static boolean OUTPUT_DEBUG_LOG = false;
    private static final String RECEIVE_BCSMS_PERMISSION = "android.permission.RECEIVE_SMS";
    static final String RECEIVE_SMS_PERMISSION = "android.permission.RECEIVE_SMS";
    protected static final String TAG = "CdmaInboundSmsHandler";
    protected SmsDuplicate mBcsmsDuplicate;
    private final boolean mCheckForDuplicatePortsInOmadmWapPush = Resources.getSystem().getBoolean(17956970);
    private byte[] mLastAcknowledgedSmsFingerprint;
    private byte[] mLastDispatchedSmsFingerprint;
    private final CdmaServiceCategoryProgramHandler mServiceCategoryProgramHandler;
    private final CdmaSMSDispatcher mSmsDispatcher;

    static {
        OUTPUT_DEBUG_LOG = true;
        DEBUG = true;
        if (Build.TYPE.equals(Carriers.USER)) {
            OUTPUT_DEBUG_LOG = false;
            DEBUG = false;
        }
    }

    private CdmaInboundSmsHandler(Context context, SmsStorageMonitor storageMonitor, Phone phone, CdmaSMSDispatcher smsDispatcher) {
        super(TAG, context, storageMonitor, phone, CellBroadcastHandler.makeCellBroadcastHandler(context, phone));
        this.mSmsDispatcher = smsDispatcher;
        this.mServiceCategoryProgramHandler = CdmaServiceCategoryProgramHandler.makeScpHandler(context, phone.mCi);
        phone.mCi.setOnNewCdmaSms(getHandler(), 1, null);
    }

    /* Access modifiers changed, original: protected */
    public void onQuitting() {
        this.mPhone.mCi.unSetOnNewCdmaSms(getHandler());
        this.mCellBroadcastHandler.dispose();
        log("unregistered for 3GPP2 SMS");
        super.onQuitting();
    }

    public static CdmaInboundSmsHandler makeInboundSmsHandler(Context context, SmsStorageMonitor storageMonitor, Phone phone, CdmaSMSDispatcher smsDispatcher) {
        CdmaInboundSmsHandler handler = new CdmaInboundSmsHandler(context, storageMonitor, phone, smsDispatcher);
        handler.start();
        return handler;
    }

    /* Access modifiers changed, original: protected */
    public boolean is3gpp2() {
        return true;
    }

    /* Access modifiers changed, original: protected */
    public int dispatchMessageRadioSpecific(SmsMessageBase smsb) {
        boolean isBroadcastType;
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "CDMA:dispatchMessageRadioSpecific() call!");
        }
        SmsMessage sms = (SmsMessage) smsb;
        if (1 == sms.getMessageType()) {
            isBroadcastType = true;
        } else {
            isBroadcastType = false;
        }
        sms.parseSms();
        int teleService = sms.getTeleService();
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "ServiceCategory is " + sms.getServiceCategory());
        }
        byte[][] pdus = new byte[][]{sms.getPdu()};
        if (isBroadcastType) {
            SmsHeader smsHeader = sms.getUserDataHeader();
            if (smsHeader == null || smsHeader.concatRef == null) {
                bcsmsDispatchPdus(pdus, sms);
                return 1;
            } else if (kddiJudgeDeliveryFromServiceCategory(sms.getServiceCategory())) {
                dispatchNormalMessage(smsb);
                return 1;
            } else {
                Rlog.d(TAG, "This concat bcsms should not be notified ");
                return 1;
            }
        }
        switch (teleService) {
            case 4098:
            case SmsEnvelope.TELESERVICE_WEMT /*4101*/:
                if (sms.isStatusReportMessage()) {
                    this.mSmsDispatcher.sendStatusReportMessage(sms);
                    return 1;
                }
                break;
            case 4099:
            case SmsEnvelope.TELESERVICE_MWI /*262144*/:
                handleVoicemailTeleservice(sms);
                return 1;
            case 4100:
            case SmsEnvelope.TELESERVICE_CT_WAP /*65002*/:
                break;
            case SmsEnvelope.TELESERVICE_SCPT /*4102*/:
                this.mServiceCategoryProgramHandler.dispatchSmsMessage(sms);
                return 1;
            default:
                loge("unsupported teleservice 0x" + Integer.toHexString(teleService));
                return 4;
        }
        if (!this.mStorageMonitor.isStorageAvailable() && sms.getMessageClass() != MessageClass.CLASS_0) {
            return 3;
        }
        if (4100 == teleService) {
            return processCdmaWapPdu(sms.getUserData(), sms.mMessageRef, sms.getOriginatingAddress(), sms.getTimestampMillis());
        } else if (SmsEnvelope.TELESERVICE_CT_WAP != teleService) {
            return dispatchNormalMessage(smsb);
        } else {
            if (!sms.processCdmaCTWdpHeader(sms)) {
                return 1;
            }
            return processCdmaWapPdu(sms.getUserData(), sms.mMessageRef, sms.getOriginatingAddress(), sms.getTimestampMillis());
        }
    }

    /* Access modifiers changed, original: protected */
    public void acknowledgeLastIncomingSms(boolean success, int result, Message response) {
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "CDMA:acknowledgeLastIncomingSms() call!");
            Rlog.d(TAG, "success = " + success + ", result = " + result);
        }
        if (!success) {
            result = 3;
        }
        this.mPhone.mCi.acknowledgeLastIncomingCdmaSms(success, resultToCause(result), response);
    }

    /* Access modifiers changed, original: protected */
    public void onUpdatePhoneObject(Phone phone) {
        super.onUpdatePhoneObject(phone);
        this.mCellBroadcastHandler.updatePhoneObject(phone);
    }

    private static int resultToCause(int rc) {
        switch (rc) {
            case -1:
            case 1:
                return 0;
            case 3:
                return 35;
            case 4:
                return 4;
            default:
                return 39;
        }
    }

    private void handleVoicemailTeleservice(SmsMessage sms) {
        int voicemailCount = sms.getNumOfVoicemails();
        log("Voicemail count=" + voicemailCount);
        if (voicemailCount < 0) {
            voicemailCount = -1;
        } else if (voicemailCount > 99) {
            voicemailCount = 99;
        }
        this.mPhone.setVoiceMessageCount(voicemailCount);
    }

    private int processCdmaWapPdu(byte[] pdu, int referenceNumber, String address, long timestamp) {
        int msgType = pdu[0] & 255;
        if (msgType != 0) {
            log("Received a WAP SMS which is not WDP. Discard.");
            return 1;
        }
        int index = 1 + 1;
        int totalSegments = pdu[1] & 255;
        int index2 = index + 1;
        int segment = pdu[index] & 255;
        if (segment >= totalSegments) {
            loge("WDP bad segment #" + segment + " expecting 0-" + (totalSegments - 1));
            return 1;
        }
        int sourcePort = 0;
        int destinationPort = 0;
        if (segment == 0) {
            index = index2 + 1;
            index2 = index + 1;
            sourcePort = ((pdu[index2] & 255) << 8) | (pdu[index] & 255);
            index = index2 + 1;
            index2 = index + 1;
            destinationPort = ((pdu[index2] & 255) << 8) | (pdu[index] & 255);
            if (this.mCheckForDuplicatePortsInOmadmWapPush && checkDuplicatePortOmadmWapPush(pdu, index2)) {
                index2 += 4;
            }
        }
        log("Received WAP PDU. Type = " + msgType + ", originator = " + address + ", src-port = " + sourcePort + ", dst-port = " + destinationPort + ", ID = " + referenceNumber + ", segment# = " + segment + '/' + totalSegments);
        byte[] userData = new byte[(pdu.length - index2)];
        System.arraycopy(pdu, index2, userData, 0, pdu.length - index2);
        return addTrackerToRawTableAndSendMessage(TelephonyComponentFactory.getInstance().makeInboundSmsTracker(userData, timestamp, destinationPort, true, address, referenceNumber, segment, totalSegments, true, HexDump.toHexString(userData)), false);
    }

    private static boolean checkDuplicatePortOmadmWapPush(byte[] origPdu, int index) {
        index += 4;
        byte[] omaPdu = new byte[(origPdu.length - index)];
        System.arraycopy(origPdu, index, omaPdu, 0, omaPdu.length);
        WspTypeDecoder pduDecoder = new WspTypeDecoder(omaPdu);
        if (!pduDecoder.decodeUintvarInteger(2) || !pduDecoder.decodeContentType(pduDecoder.getDecodedDataLength() + 2)) {
            return false;
        }
        return WspTypeDecoder.CONTENT_TYPE_B_PUSH_SYNCML_NOTI.equals(pduDecoder.getValueString());
    }

    /* Access modifiers changed, original: 0000 */
    public void handleNewSms(AsyncResult ar) {
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "CDMA:handleNewSms() call!");
        }
        if (ar.exception != null) {
            loge("Exception processing incoming SMS: " + ar.exception);
            return;
        }
        int result;
        try {
            result = dispatchMessage(ar.result.mWrappedSmsMessage);
        } catch (RuntimeException ex) {
            loge("Exception dispatching message", ex);
            result = 2;
        }
        if (result != -1) {
            boolean handled = result == 1;
            boolean isBroadcastType = 1 == ((SmsMessage) ((SmsMessage) ar.result).mWrappedSmsMessage).getMessageType();
            if (OUTPUT_DEBUG_LOG) {
                Rlog.d(TAG, "isBroadcastType = " + isBroadcastType);
            }
            if (!isBroadcastType) {
                acknowledgeLastIncomingSms(handled, result, null);
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public boolean kddiDispatchPdus(InboundSmsTracker tracker) {
        long millis = tracker.getTimestamp();
        byte[] OriginatingAddress = tracker.getAddress().getBytes();
        byte[] MsgCenterTimeStamp = new byte[]{(byte) ((int) ((millis >>> 56) & 255)), (byte) ((int) ((millis >>> 48) & 255)), (byte) ((int) ((millis >>> 40) & 255)), (byte) ((int) ((millis >>> 32) & 255)), (byte) ((int) ((millis >>> 24) & 255)), (byte) ((int) ((millis >>> 16) & 255)), (byte) ((int) ((millis >>> 8) & 255)), (byte) ((int) ((millis >>> null) & 255))};
        byte[] messageData = new byte[(MsgCenterTimeStamp.length + OriginatingAddress.length)];
        System.arraycopy(MsgCenterTimeStamp, 0, messageData, 0, MsgCenterTimeStamp.length);
        System.arraycopy(OriginatingAddress, 0, messageData, MsgCenterTimeStamp.length, OriginatingAddress.length);
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "kddiDispatchPdus() start");
        }
        boolean IsOversea = ((TelephonyManager) this.mContext.getSystemService("phone")).isNetworkRoaming();
        if (OUTPUT_DEBUG_LOG) {
            if (IsOversea) {
                Rlog.d(TAG, "OVERSEA");
            } else {
                Rlog.d(TAG, "NOT OVERSEA");
            }
        }
        try {
            if (this.mSmsDuplicate == null) {
                this.mSmsDuplicate = new SmsDuplicate(this.mContext, 1, true);
            }
            ResultJudgeDuplicate info = this.mSmsDuplicate.checkSmsDuplicate(0, messageData);
            if (info == null || !info.mIsSame) {
                SmsAccessory accessory = this.mSmsDuplicate.SmsAccessory(Intents.SMS_RECEIVED_ACTION, "android.permission.RECEIVE_SMS", 0);
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "action = " + accessory.getAction() + " ,response = " + accessory.getResponse());
                    Rlog.d(TAG, "kddiDispatchPdus():This sms is Cmail(CDMA).");
                }
                this.mSmsDuplicate.updateSmsDuplicate(0, messageData, accessory);
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "kddiDispatchPdus() end");
                }
                return true;
            }
            if (info.mIsReply && OUTPUT_DEBUG_LOG) {
                Rlog.d(TAG, "kddiDispatchPdus():This sms is Duplicate!(CDMA).");
            }
            return false;
        } catch (NullPointerException e) {
            Rlog.e(TAG, "kddiDispatchPdus() failed to create SmsAccessory ");
        }
    }

    /* Access modifiers changed, original: protected */
    public void bcsmsDispatchPdus(byte[][] pdus, SmsMessage sms) {
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "bcsmsDispatchPdus() start");
        }
        int serviceCategory = sms.getServiceCategory();
        if (kddiJudgeDeliveryFromServiceCategory(serviceCategory)) {
            byte[] service = new byte[]{(byte) ((serviceCategory >>> 8) & 63), (byte) ((serviceCategory >>> 0) & 255)};
            byte[] messageData = new byte[sms.getUserData().length];
            System.arraycopy(sms.getUserData(), 0, messageData, 0, sms.getUserData().length);
            if (OUTPUT_DEBUG_LOG) {
                Rlog.d(TAG, "ServiceCategory = " + serviceCategory);
                Rlog.d(TAG, "MessageID       = " + sms.mMessageRef);
                Rlog.d(TAG, "UserData len    = " + messageData.length);
            }
            try {
                if (this.mBcsmsDuplicate == null) {
                    this.mBcsmsDuplicate = new BcsmsDuplicate(this.mContext, 1, true);
                }
                SmsAccessory accessory = this.mBcsmsDuplicate.SmsAccessory(Intents.SMS_RECEIVED_ACTION, "android.permission.RECEIVE_SMS", 0);
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "bcsmsDispatchPdus():set BCSMS action = " + accessory.getAction() + " ,response = " + accessory.getResponse());
                    Rlog.d(TAG, "bcsmsDispatchPdus():This sms is bcsms.");
                }
                ResultJudgeDuplicate info = this.mBcsmsDuplicate.checkSmsDuplicate(sms.mMessageRef, messageData);
                if (info == null || !info.mIsSame) {
                    this.mBcsmsDuplicate.updateSmsDuplicate(sms.mMessageRef, messageData, accessory);
                    Intent intent = new Intent(accessory.getAction());
                    intent.putExtra("pdus", pdus);
                    intent.putExtra(CellBroadcasts.MESSAGE_FORMAT, this.mSmsDispatcher.getFormat());
                    dispatchNonResult(intent, accessory.getPermission());
                    if (OUTPUT_DEBUG_LOG) {
                        Rlog.v(TAG, "bcsmsDispatchPdus() end");
                    }
                    return;
                }
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "bcsmsDispatchPdus():This bcsms is Duplicate!(CDMA).");
                }
            } catch (NullPointerException e) {
                Rlog.e(TAG, "bcsmsDispatchPdus() failed to create SmsAccessory ");
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public boolean kddiJudgeDeliveryFromServiceCategory(int serviceCategory) {
        int maintenanceMode = 0;
        if (DEBUG) {
            try {
                maintenanceMode = this.mContext.createPackageContext("jp.co.sharp.maintenanceMode", 2).getSharedPreferences("pref", 4).getInt("maintenanceMode", 0);
            } catch (NameNotFoundException e) {
                Rlog.e(TAG, "maintenanceMode app not found");
            }
        }
        byte[] service = new byte[]{(byte) ((serviceCategory >>> 12) & 12)};
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "Deliver Classification = " + Integer.toHexString(service[0]));
            Rlog.d(TAG, "maintenanceMode = " + maintenanceMode);
        }
        boolean isDelivery = false;
        switch (maintenanceMode) {
            case 0:
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "Deliver classification is Commercial");
                }
                if (serviceCategory == 1 || (serviceCategory >= 33 && serviceCategory <= 63)) {
                    isDelivery = true;
                    break;
                }
            case 1:
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "Deliver classification is ManufactureTestmode");
                }
                if (serviceCategory == 32769 || (serviceCategory >= 32801 && serviceCategory <= 32831)) {
                    isDelivery = true;
                    break;
                }
            case 2:
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "Deliver classification is KDDITestmode");
                }
                if (serviceCategory == 49153 || (serviceCategory >= 49185 && serviceCategory <= 49215)) {
                    isDelivery = true;
                    break;
                }
            case 3:
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d(TAG, "Deliver classification is AllRecieveMode");
                }
                if (serviceCategory == 1 || ((serviceCategory >= 33 && serviceCategory <= 63) || serviceCategory == 32769 || ((serviceCategory >= 32801 && serviceCategory <= 32831) || serviceCategory == 49153 || (serviceCategory >= 49185 && serviceCategory <= 49215)))) {
                    isDelivery = true;
                    break;
                }
        }
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d(TAG, "isDelivery is " + isDelivery);
        }
        return isDelivery;
    }

    /* Access modifiers changed, original: protected */
    public void dispatchIntent(Intent intent, String permission, int appOp, BroadcastReceiver resultReceiver) {
        intent.putExtra("subscription", this.mPhone.getSubId());
        this.mContext.sendOrderedBroadcast(intent, permission, appOp, resultReceiver, getHandler(), -1, null, null);
    }
}
