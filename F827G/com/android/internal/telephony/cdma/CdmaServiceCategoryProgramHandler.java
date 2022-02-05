package com.android.internal.telephony.cdma;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.telephony.cdma.CdmaSmsCbProgramData;
import android.telephony.cdma.CdmaSmsCbProgramResults;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.WakeLockStateMachine;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.cdma.sms.CdmaSmsAddress;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class CdmaServiceCategoryProgramHandler extends WakeLockStateMachine {
    final CommandsInterface mCi;
    private final BroadcastReceiver mScpResultsReceiver = new BroadcastReceiver() { // from class: com.android.internal.telephony.cdma.CdmaServiceCategoryProgramHandler.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            sendScpResults();
            CdmaServiceCategoryProgramHandler.this.log("mScpResultsReceiver finished");
            CdmaServiceCategoryProgramHandler.this.sendMessage(2);
        }

        private void sendScpResults() {
            DataOutputStream dos;
            int resultCode = getResultCode();
            if (resultCode == -1 || resultCode == 1) {
                Bundle extras = getResultExtras(false);
                if (extras == null) {
                    CdmaServiceCategoryProgramHandler.this.loge("SCP results error: missing extras");
                    return;
                }
                String sender = extras.getString("sender");
                if (sender == null) {
                    CdmaServiceCategoryProgramHandler.this.loge("SCP results error: missing sender extra.");
                    return;
                }
                ArrayList<CdmaSmsCbProgramResults> results = extras.getParcelableArrayList("results");
                if (results == null) {
                    CdmaServiceCategoryProgramHandler.this.loge("SCP results error: missing results extra.");
                    return;
                }
                try {
                    BearerData bData = new BearerData();
                    bData.messageType = 2;
                    bData.messageId = SmsMessage.getNextMessageId();
                    bData.serviceCategoryProgramResults = results;
                    byte[] encodedBearerData = BearerData.encode(bData);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream(100);
                    dos = new DataOutputStream(baos);
                    try {
                        dos.writeInt(SmsEnvelope.TELESERVICE_SCPT);
                        dos.writeInt(0);
                        dos.writeInt(0);
                        CdmaSmsAddress destAddr = CdmaSmsAddress.parse(PhoneNumberUtils.cdmaCheckAndProcessPlusCodeForSms(sender));
                        dos.write(destAddr.digitMode);
                        dos.write(destAddr.numberMode);
                        dos.write(destAddr.ton);
                        dos.write(destAddr.numberPlan);
                        dos.write(destAddr.numberOfDigits);
                        dos.write(destAddr.origBytes, 0, destAddr.origBytes.length);
                        dos.write(0);
                        dos.write(0);
                        dos.write(0);
                        dos.write(encodedBearerData.length);
                        dos.write(encodedBearerData, 0, encodedBearerData.length);
                        CdmaServiceCategoryProgramHandler.this.mCi.sendCdmaSms(baos.toByteArray(), null);
                    } catch (IOException e) {
                        CdmaServiceCategoryProgramHandler.this.loge("exception creating SCP results PDU", e);
                        try {
                            dos.close();
                        } catch (IOException e2) {
                        }
                    }
                } finally {
                    try {
                        dos.close();
                    } catch (IOException e3) {
                    }
                }
            } else {
                CdmaServiceCategoryProgramHandler.this.loge("SCP results error: result code = " + resultCode);
            }
        }
    };

    CdmaServiceCategoryProgramHandler(Context context, CommandsInterface commandsInterface) {
        super("CdmaServiceCategoryProgramHandler", context, null);
        this.mContext = context;
        this.mCi = commandsInterface;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static CdmaServiceCategoryProgramHandler makeScpHandler(Context context, CommandsInterface commandsInterface) {
        CdmaServiceCategoryProgramHandler handler = new CdmaServiceCategoryProgramHandler(context, commandsInterface);
        handler.start();
        return handler;
    }

    @Override // com.android.internal.telephony.WakeLockStateMachine
    protected boolean handleSmsMessage(Message message) {
        if (message.obj instanceof SmsMessage) {
            return handleServiceCategoryProgramData((SmsMessage) message.obj);
        }
        loge("handleMessage got object of type: " + message.obj.getClass().getName());
        return false;
    }

    private boolean handleServiceCategoryProgramData(SmsMessage sms) {
        ArrayList<CdmaSmsCbProgramData> programDataList = sms.getSmsCbProgramData();
        if (programDataList == null) {
            loge("handleServiceCategoryProgramData: program data list is null!");
            return false;
        }
        Intent intent = new Intent(Telephony.Sms.Intents.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION);
        intent.putExtra("sender", sms.getOriginatingAddress());
        intent.putParcelableArrayListExtra("program_data", programDataList);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
        this.mContext.sendOrderedBroadcast(intent, "android.permission.RECEIVE_SMS", 16, this.mScpResultsReceiver, getHandler(), -1, (String) null, (Bundle) null);
        return true;
    }
}
