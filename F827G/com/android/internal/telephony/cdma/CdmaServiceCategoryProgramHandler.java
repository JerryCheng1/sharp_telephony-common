package com.android.internal.telephony.cdma;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.provider.Telephony.Sms.Intents;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.WakeLockStateMachine;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.cdma.sms.CdmaSmsAddress;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public final class CdmaServiceCategoryProgramHandler extends WakeLockStateMachine {
    final CommandsInterface mCi;
    private final BroadcastReceiver mScpResultsReceiver = new BroadcastReceiver() {
        private void sendScpResults() {
            int resultCode = getResultCode();
            if (resultCode == -1 || resultCode == 1) {
                Bundle resultExtras = getResultExtras(false);
                if (resultExtras == null) {
                    CdmaServiceCategoryProgramHandler.this.loge("SCP results error: missing extras");
                    return;
                }
                String string = resultExtras.getString("sender");
                if (string == null) {
                    CdmaServiceCategoryProgramHandler.this.loge("SCP results error: missing sender extra.");
                    return;
                }
                ArrayList parcelableArrayList = resultExtras.getParcelableArrayList("results");
                if (parcelableArrayList == null) {
                    CdmaServiceCategoryProgramHandler.this.loge("SCP results error: missing results extra.");
                    return;
                }
                BearerData bearerData = new BearerData();
                bearerData.messageType = 2;
                bearerData.messageId = SmsMessage.getNextMessageId();
                bearerData.serviceCategoryProgramResults = parcelableArrayList;
                byte[] encode = BearerData.encode(bearerData);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(100);
                DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
                try {
                    dataOutputStream.writeInt(SmsEnvelope.TELESERVICE_SCPT);
                    dataOutputStream.writeInt(0);
                    dataOutputStream.writeInt(0);
                    CdmaSmsAddress parse = CdmaSmsAddress.parse(PhoneNumberUtils.cdmaCheckAndProcessPlusCodeForSms(string));
                    dataOutputStream.write(parse.digitMode);
                    dataOutputStream.write(parse.numberMode);
                    dataOutputStream.write(parse.ton);
                    dataOutputStream.write(parse.numberPlan);
                    dataOutputStream.write(parse.numberOfDigits);
                    dataOutputStream.write(parse.origBytes, 0, parse.origBytes.length);
                    dataOutputStream.write(0);
                    dataOutputStream.write(0);
                    dataOutputStream.write(0);
                    dataOutputStream.write(encode.length);
                    dataOutputStream.write(encode, 0, encode.length);
                    CdmaServiceCategoryProgramHandler.this.mCi.sendCdmaSms(byteArrayOutputStream.toByteArray(), null);
                    try {
                        dataOutputStream.close();
                        return;
                    } catch (IOException e) {
                        return;
                    }
                } catch (IOException e2) {
                    CdmaServiceCategoryProgramHandler.this.loge("exception creating SCP results PDU", e2);
                    try {
                        dataOutputStream.close();
                        return;
                    } catch (IOException e3) {
                        return;
                    }
                } catch (Throwable th) {
                    try {
                        dataOutputStream.close();
                    } catch (IOException e4) {
                    }
                    throw th;
                }
            }
            CdmaServiceCategoryProgramHandler.this.loge("SCP results error: result code = " + resultCode);
        }

        public void onReceive(Context context, Intent intent) {
            sendScpResults();
            CdmaServiceCategoryProgramHandler.this.log("mScpResultsReceiver finished");
            CdmaServiceCategoryProgramHandler.this.sendMessage(2);
        }
    };

    CdmaServiceCategoryProgramHandler(Context context, CommandsInterface commandsInterface) {
        super("CdmaServiceCategoryProgramHandler", context, null);
        this.mContext = context;
        this.mCi = commandsInterface;
    }

    private boolean handleServiceCategoryProgramData(SmsMessage smsMessage) {
        ArrayList smsCbProgramData = smsMessage.getSmsCbProgramData();
        if (smsCbProgramData == null) {
            loge("handleServiceCategoryProgramData: program data list is null!");
            return false;
        }
        Intent intent = new Intent(Intents.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION);
        intent.putExtra("sender", smsMessage.getOriginatingAddress());
        intent.putParcelableArrayListExtra("program_data", smsCbProgramData);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
        this.mContext.sendOrderedBroadcast(intent, "android.permission.RECEIVE_SMS", 16, this.mScpResultsReceiver, getHandler(), -1, null, null);
        return true;
    }

    static CdmaServiceCategoryProgramHandler makeScpHandler(Context context, CommandsInterface commandsInterface) {
        CdmaServiceCategoryProgramHandler cdmaServiceCategoryProgramHandler = new CdmaServiceCategoryProgramHandler(context, commandsInterface);
        cdmaServiceCategoryProgramHandler.start();
        return cdmaServiceCategoryProgramHandler;
    }

    /* Access modifiers changed, original: protected */
    public boolean handleSmsMessage(Message message) {
        if (message.obj instanceof SmsMessage) {
            return handleServiceCategoryProgramData((SmsMessage) message.obj);
        }
        loge("handleMessage got object of type: " + message.obj.getClass().getName());
        return false;
    }
}
