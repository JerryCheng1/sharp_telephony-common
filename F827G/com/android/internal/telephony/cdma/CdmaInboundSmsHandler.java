package com.android.internal.telephony.cdma;

import android.content.Context;
import android.content.res.Resources;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.SmsCbMessage;
import com.android.internal.telephony.CellBroadcastHandler;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.InboundSmsTracker;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SmsConstants.MessageClass;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.WspTypeDecoder;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import java.util.Arrays;

public class CdmaInboundSmsHandler extends InboundSmsHandler {
    private final boolean mCheckForDuplicatePortsInOmadmWapPush = Resources.getSystem().getBoolean(17956959);
    private byte[] mLastAcknowledgedSmsFingerprint;
    private byte[] mLastDispatchedSmsFingerprint;
    private final CdmaServiceCategoryProgramHandler mServiceCategoryProgramHandler;
    private final CdmaSMSDispatcher mSmsDispatcher;

    private CdmaInboundSmsHandler(Context context, SmsStorageMonitor smsStorageMonitor, PhoneBase phoneBase, CdmaSMSDispatcher cdmaSMSDispatcher) {
        super("CdmaInboundSmsHandler", context, smsStorageMonitor, phoneBase, CellBroadcastHandler.makeCellBroadcastHandler(context, phoneBase));
        this.mSmsDispatcher = cdmaSMSDispatcher;
        this.mServiceCategoryProgramHandler = CdmaServiceCategoryProgramHandler.makeScpHandler(context, phoneBase.mCi);
        phoneBase.mCi.setOnNewCdmaSms(getHandler(), 1, null);
    }

    private static boolean checkDuplicatePortOmadmWapPush(byte[] bArr, int i) {
        int i2 = i + 4;
        byte[] bArr2 = new byte[(bArr.length - i2)];
        System.arraycopy(bArr, i2, bArr2, 0, bArr2.length);
        WspTypeDecoder wspTypeDecoder = new WspTypeDecoder(bArr2);
        return (wspTypeDecoder.decodeUintvarInteger(2) && wspTypeDecoder.decodeContentType(wspTypeDecoder.getDecodedDataLength() + 2)) ? WspTypeDecoder.CONTENT_TYPE_B_PUSH_SYNCML_NOTI.equals(wspTypeDecoder.getValueString()) : false;
    }

    private void handleVoicemailTeleservice(SmsMessage smsMessage) {
        int i = 99;
        int numOfVoicemails = smsMessage.getNumOfVoicemails();
        log("Voicemail count=" + numOfVoicemails);
        this.mPhone.setVoiceMessageWaiting(1, numOfVoicemails);
        if (numOfVoicemails < 0) {
            i = -1;
        } else if (numOfVoicemails <= 99) {
            i = numOfVoicemails;
        }
        this.mPhone.setVoiceMessageCount(i);
        storeVoiceMailCount();
    }

    private static boolean isInEmergencyCallMode() {
        return "true".equals(SystemProperties.get("ril.cdma.inecmmode", "false"));
    }

    public static CdmaInboundSmsHandler makeInboundSmsHandler(Context context, SmsStorageMonitor smsStorageMonitor, PhoneBase phoneBase, CdmaSMSDispatcher cdmaSMSDispatcher) {
        CdmaInboundSmsHandler cdmaInboundSmsHandler = new CdmaInboundSmsHandler(context, smsStorageMonitor, phoneBase, cdmaSMSDispatcher);
        cdmaInboundSmsHandler.start();
        return cdmaInboundSmsHandler;
    }

    private int processCdmaWapPdu(byte[] bArr, int i, String str, long j) {
        int i2 = bArr[0] & 255;
        if (i2 != 0) {
            log("Received a WAP SMS which is not WDP. Discard.");
            return 1;
        }
        int i3 = bArr[1] & 255;
        int i4 = 3;
        int i5 = bArr[2] & 255;
        if (i5 >= i3) {
            loge("WDP bad segment #" + i5 + " expecting 0-" + (i3 - 1));
            return 1;
        }
        int i6;
        byte[] bArr2;
        int i7 = 0;
        int i8 = 0;
        if (i5 == 0) {
            i7 = ((bArr[3] & 255) << 8) | (bArr[4] & 255);
            i4 = 7;
            i8 = (bArr[6] & 255) | ((bArr[5] & 255) << 8);
            if (this.mCheckForDuplicatePortsInOmadmWapPush && checkDuplicatePortOmadmWapPush(bArr, 7)) {
                i4 = 11;
                i6 = i8;
                log("Received WAP PDU. Type = " + i2 + ", originator = " + str + ", src-port = " + i7 + ", dst-port = " + i6 + ", ID = " + i + ", segment# = " + i5 + '/' + i3);
                bArr2 = new byte[(bArr.length - i4)];
                System.arraycopy(bArr, i4, bArr2, 0, bArr.length - i4);
                return addTrackerToRawTableAndSendMessage(new InboundSmsTracker(bArr2, j, i6, true, str, i, i5, i3, true));
            }
        }
        i6 = i8;
        log("Received WAP PDU. Type = " + i2 + ", originator = " + str + ", src-port = " + i7 + ", dst-port = " + i6 + ", ID = " + i + ", segment# = " + i5 + '/' + i3);
        bArr2 = new byte[(bArr.length - i4)];
        System.arraycopy(bArr, i4, bArr2, 0, bArr.length - i4);
        return addTrackerToRawTableAndSendMessage(new InboundSmsTracker(bArr2, j, i6, true, str, i, i5, i3, true));
    }

    private static int resultToCause(int i) {
        switch (i) {
            case -1:
            case 1:
                return 0;
            case 3:
                return 35;
            case 4:
                return 4;
            default:
                return 96;
        }
    }

    /* Access modifiers changed, original: protected */
    public void acknowledgeLastIncomingSms(boolean z, int i, Message message) {
        if (!isInEmergencyCallMode()) {
            int resultToCause = resultToCause(i);
            this.mPhone.mCi.acknowledgeLastIncomingCdmaSms(z, resultToCause, message);
            if (resultToCause == 0) {
                this.mLastAcknowledgedSmsFingerprint = this.mLastDispatchedSmsFingerprint;
            }
            this.mLastDispatchedSmsFingerprint = null;
        }
    }

    /* Access modifiers changed, original: protected */
    public int dispatchMessageRadioSpecific(SmsMessageBase smsMessageBase) {
        int i;
        if (isInEmergencyCallMode()) {
            i = -1;
        } else {
            SmsMessage smsMessage = (SmsMessage) smsMessageBase;
            if ((1 == smsMessage.getMessageType() ? 1 : 0) != 0) {
                log("Broadcast type message");
                SmsCbMessage parseBroadcastSms = smsMessage.parseBroadcastSms();
                if (parseBroadcastSms != null) {
                    this.mCellBroadcastHandler.dispatchSmsMessage(parseBroadcastSms);
                    return 1;
                }
                loge("error trying to parse broadcast SMS");
                return 1;
            }
            this.mLastDispatchedSmsFingerprint = smsMessage.getIncomingSmsFingerprint();
            if (this.mLastAcknowledgedSmsFingerprint == null || !Arrays.equals(this.mLastDispatchedSmsFingerprint, this.mLastAcknowledgedSmsFingerprint)) {
                smsMessage.parseSms();
                int teleService = smsMessage.getTeleService();
                switch (teleService) {
                    case 4098:
                    case SmsEnvelope.TELESERVICE_WEMT /*4101*/:
                        if (smsMessage.isStatusReportMessage()) {
                            this.mSmsDispatcher.sendStatusReportMessage(smsMessage);
                            return 1;
                        }
                        break;
                    case 4099:
                    case SmsEnvelope.TELESERVICE_MWI /*262144*/:
                        handleVoicemailTeleservice(smsMessage);
                        return 1;
                    case 4100:
                    case SmsEnvelope.TELESERVICE_CT_WAP /*65002*/:
                        break;
                    case SmsEnvelope.TELESERVICE_SCPT /*4102*/:
                        this.mServiceCategoryProgramHandler.dispatchSmsMessage(smsMessage);
                        return 1;
                    default:
                        loge("unsupported teleservice 0x" + Integer.toHexString(teleService));
                        return 4;
                }
                if (!this.mStorageMonitor.isStorageAvailable() && smsMessage.getMessageClass() != MessageClass.CLASS_0) {
                    return 3;
                }
                if (4100 == teleService) {
                    return processCdmaWapPdu(smsMessage.getUserData(), smsMessage.mMessageRef, smsMessage.getOriginatingAddress(), smsMessage.getTimestampMillis());
                } else if (SmsEnvelope.TELESERVICE_CT_WAP != teleService) {
                    return dispatchNormalMessage(smsMessageBase);
                } else {
                    if (smsMessage.processCdmaCTWdpHeader(smsMessage)) {
                        return processCdmaWapPdu(smsMessage.getUserData(), smsMessage.mMessageRef, smsMessage.getOriginatingAddress(), smsMessage.getTimestampMillis());
                    }
                }
            }
            i = 1;
        }
        return i;
    }

    /* Access modifiers changed, original: protected */
    public boolean is3gpp2() {
        return true;
    }

    /* Access modifiers changed, original: protected */
    public void onQuitting() {
        this.mPhone.mCi.unSetOnNewCdmaSms(getHandler());
        this.mCellBroadcastHandler.dispose();
        log("unregistered for 3GPP2 SMS");
        super.onQuitting();
    }

    /* Access modifiers changed, original: protected */
    public void onUpdatePhoneObject(PhoneBase phoneBase) {
        super.onUpdatePhoneObject(phoneBase);
        this.mCellBroadcastHandler.updatePhoneObject(phoneBase);
    }
}
