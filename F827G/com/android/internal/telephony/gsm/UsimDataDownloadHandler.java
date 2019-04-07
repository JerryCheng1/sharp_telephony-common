package com.android.internal.telephony.gsm;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.cat.ComprehensionTlvTag;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UsimServiceTable;
import com.android.internal.telephony.uicc.UsimServiceTable.UsimService;

public class UsimDataDownloadHandler extends Handler {
    private static final int BER_SMS_PP_DOWNLOAD_TAG = 209;
    private static final int DEV_ID_NETWORK = 131;
    private static final int DEV_ID_UICC = 129;
    private static final int EVENT_SEND_ENVELOPE_RESPONSE = 2;
    private static final int EVENT_START_DATA_DOWNLOAD = 1;
    private static final int EVENT_WRITE_SMS_COMPLETE = 3;
    private static final String TAG = "UsimDataDownloadHandler";
    private final CommandsInterface mCi;

    public UsimDataDownloadHandler(CommandsInterface commandsInterface) {
        this.mCi = commandsInterface;
    }

    private void acknowledgeSmsWithError(int i) {
        this.mCi.acknowledgeLastIncomingGsmSms(false, i, null);
    }

    private static int getEnvelopeBodyLength(int i, int i2) {
        int i3 = (i2 > 127 ? 2 : 1) + (i2 + 5);
        return i != 0 ? (i3 + 2) + i : i3;
    }

    private void handleDataDownload(SmsMessage smsMessage) {
        int i;
        int dataCodingScheme = smsMessage.getDataCodingScheme();
        int protocolIdentifier = smsMessage.getProtocolIdentifier();
        byte[] pdu = smsMessage.getPdu();
        int i2 = pdu[0] & 255;
        int i3 = i2 + 1;
        int length = pdu.length - i3;
        int envelopeBodyLength = getEnvelopeBodyLength(i2, length);
        byte[] bArr = new byte[((envelopeBodyLength > 127 ? 2 : 1) + (envelopeBodyLength + 1))];
        bArr[0] = (byte) -47;
        if (envelopeBodyLength > 127) {
            i = 2;
            bArr[1] = (byte) -127;
        } else {
            i = 1;
        }
        int i4 = i + 1;
        bArr[i] = (byte) envelopeBodyLength;
        i = i4 + 1;
        bArr[i4] = (byte) (ComprehensionTlvTag.DEVICE_IDENTITIES.value() | 128);
        i4 = i + 1;
        bArr[i] = (byte) 2;
        envelopeBodyLength = i4 + 1;
        bArr[i4] = (byte) -125;
        i = envelopeBodyLength + 1;
        bArr[envelopeBodyLength] = (byte) -127;
        if (i2 != 0) {
            i4 = i + 1;
            bArr[i] = (byte) ComprehensionTlvTag.ADDRESS.value();
            i = i4 + 1;
            bArr[i4] = (byte) i2;
            System.arraycopy(pdu, 1, bArr, i, i2);
            i += i2;
        }
        i4 = i + 1;
        bArr[i] = (byte) (ComprehensionTlvTag.SMS_TPDU.value() | 128);
        if (length > 127) {
            i = i4 + 1;
            bArr[i4] = (byte) -127;
        } else {
            i = i4;
        }
        i4 = i + 1;
        bArr[i] = (byte) length;
        System.arraycopy(pdu, i3, bArr, i4, length);
        if (i4 + length != bArr.length) {
            Rlog.e(TAG, "startDataDownload() calculated incorrect envelope length, aborting.");
            acknowledgeSmsWithError(255);
            return;
        }
        this.mCi.sendEnvelopeWithStatus(IccUtils.bytesToHexString(bArr), obtainMessage(2, new int[]{dataCodingScheme, protocolIdentifier}));
    }

    private static boolean is7bitDcs(int i) {
        return (i & 140) == 0 || (i & 244) == 240;
    }

    private void sendSmsAckForEnvelopeResponse(IccIoResult iccIoResult, int i, int i2) {
        boolean z;
        int i3 = iccIoResult.sw1;
        int i4 = iccIoResult.sw2;
        if ((i3 == 144 && i4 == 0) || i3 == 145) {
            Rlog.d(TAG, "USIM data download succeeded: " + iccIoResult.toString());
            z = true;
        } else if (i3 == 147 && i4 == 0) {
            Rlog.e(TAG, "USIM data download failed: Toolkit busy");
            acknowledgeSmsWithError(CommandsInterface.GSM_SMS_FAIL_CAUSE_USIM_APP_TOOLKIT_BUSY);
            return;
        } else if (i3 == 98 || i3 == 99) {
            Rlog.e(TAG, "USIM data download failed: " + iccIoResult.toString());
            z = false;
        } else {
            Rlog.e(TAG, "Unexpected SW1/SW2 response from UICC: " + iccIoResult.toString());
            z = false;
        }
        byte[] bArr = iccIoResult.payload;
        if (bArr != null && bArr.length != 0) {
            byte[] bArr2;
            int i5;
            if (z) {
                bArr2 = new byte[(bArr.length + 5)];
                bArr2[0] = (byte) 0;
                bArr2[1] = (byte) 7;
                i5 = 2;
            } else {
                bArr2 = new byte[(bArr.length + 6)];
                bArr2[0] = (byte) 0;
                bArr2[1] = (byte) -43;
                bArr2[2] = (byte) 7;
                i5 = 3;
            }
            int i6 = i5 + 1;
            bArr2[i5] = (byte) i2;
            i5 = i6 + 1;
            bArr2[i6] = (byte) i;
            if (is7bitDcs(i)) {
                bArr2[i5] = (byte) ((bArr.length * 8) / 7);
                i5++;
            } else {
                bArr2[i5] = (byte) bArr.length;
                i5++;
            }
            System.arraycopy(bArr, 0, bArr2, i5, bArr.length);
            this.mCi.acknowledgeIncomingGsmSmsWithPdu(z, IccUtils.bytesToHexString(bArr2), null);
        } else if (z) {
            this.mCi.acknowledgeLastIncomingGsmSms(true, 0, null);
        } else {
            acknowledgeSmsWithError(CommandsInterface.GSM_SMS_FAIL_CAUSE_USIM_DATA_DOWNLOAD_ERROR);
        }
    }

    public void handleMessage(Message message) {
        AsyncResult asyncResult;
        switch (message.what) {
            case 1:
                handleDataDownload((SmsMessage) message.obj);
                return;
            case 2:
                asyncResult = (AsyncResult) message.obj;
                if (asyncResult.exception != null) {
                    Rlog.e(TAG, "UICC Send Envelope failure, exception: " + asyncResult.exception);
                    acknowledgeSmsWithError(CommandsInterface.GSM_SMS_FAIL_CAUSE_USIM_DATA_DOWNLOAD_ERROR);
                    return;
                }
                int[] iArr = (int[]) asyncResult.userObj;
                sendSmsAckForEnvelopeResponse((IccIoResult) asyncResult.result, iArr[0], iArr[1]);
                return;
            case 3:
                asyncResult = (AsyncResult) message.obj;
                if (asyncResult.exception == null) {
                    Rlog.d(TAG, "Successfully wrote SMS-PP message to UICC");
                    this.mCi.acknowledgeLastIncomingGsmSms(true, 0, null);
                    return;
                }
                Rlog.d(TAG, "Failed to write SMS-PP message to UICC", asyncResult.exception);
                this.mCi.acknowledgeLastIncomingGsmSms(false, 255, null);
                return;
            default:
                Rlog.e(TAG, "Ignoring unexpected message, what=" + message.what);
                return;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public int handleUsimDataDownload(UsimServiceTable usimServiceTable, SmsMessage smsMessage) {
        if (usimServiceTable == null || !usimServiceTable.isAvailable(UsimService.DATA_DL_VIA_SMS_PP)) {
            Rlog.d(TAG, "DATA_DL_VIA_SMS_PP service not available, storing message to UICC.");
            this.mCi.writeSmsToSim(3, IccUtils.bytesToHexString(PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(smsMessage.getServiceCenterAddress())), IccUtils.bytesToHexString(smsMessage.getPdu()), obtainMessage(3));
            return -1;
        }
        Rlog.d(TAG, "Received SMS-PP data download, sending to UICC.");
        return startDataDownload(smsMessage);
    }

    public int startDataDownload(SmsMessage smsMessage) {
        if (sendMessage(obtainMessage(1, smsMessage))) {
            return -1;
        }
        Rlog.e(TAG, "startDataDownload failed to send message to start data download.");
        return 2;
    }
}
