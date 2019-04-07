package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Telephony.Sms.Intents;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionManager;

public class CellBroadcastHandler extends WakeLockStateMachine {
    private CellBroadcastHandler(Context context, PhoneBase phoneBase) {
        this("CellBroadcastHandler", context, phoneBase);
    }

    protected CellBroadcastHandler(String str, Context context, PhoneBase phoneBase) {
        super(str, context, phoneBase);
    }

    public static CellBroadcastHandler makeCellBroadcastHandler(Context context, PhoneBase phoneBase) {
        CellBroadcastHandler cellBroadcastHandler = new CellBroadcastHandler(context, phoneBase);
        cellBroadcastHandler.start();
        return cellBroadcastHandler;
    }

    /* Access modifiers changed, original: protected */
    public void handleBroadcastSms(SmsCbMessage smsCbMessage) {
        Intent intent;
        String str;
        int i;
        if (smsCbMessage.isEmergencyMessage()) {
            log("Dispatching emergency SMS CB, SmsCbMessage is: " + smsCbMessage);
            intent = new Intent(Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION);
            str = "android.permission.RECEIVE_EMERGENCY_BROADCAST";
            i = 17;
        } else {
            log("Dispatching SMS CB, SmsCbMessage is: " + smsCbMessage);
            intent = new Intent(Intents.SMS_CB_RECEIVED_ACTION);
            str = "android.permission.RECEIVE_SMS";
            i = 16;
        }
        intent.putExtra("message", smsCbMessage);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
        this.mContext.sendOrderedBroadcastAsUser(intent, UserHandle.ALL, str, i, this.mReceiver, getHandler(), -1, null, null);
    }

    /* Access modifiers changed, original: protected */
    public boolean handleSmsMessage(Message message) {
        if (message.obj instanceof SmsCbMessage) {
            handleBroadcastSms((SmsCbMessage) message.obj);
            return true;
        }
        loge("handleMessage got object of type: " + message.obj.getClass().getName());
        return false;
    }
}
