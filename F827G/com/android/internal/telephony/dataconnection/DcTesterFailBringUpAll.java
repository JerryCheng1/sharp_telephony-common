package com.android.internal.telephony.dataconnection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.telephony.Rlog;
import com.android.internal.telephony.PhoneBase;

public class DcTesterFailBringUpAll {
    private static final boolean DBG = true;
    private static final String LOG_TAG = "DcTesterFailBrinupAll";
    private String mActionFailBringUp = (DcFailBringUp.INTENT_BASE + "." + "action_fail_bringup");
    private DcFailBringUp mFailBringUp = new DcFailBringUp();
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            DcTesterFailBringUpAll.this.log("sIntentReceiver.onReceive: action=" + action);
            if (action.equals(DcTesterFailBringUpAll.this.mActionFailBringUp)) {
                DcTesterFailBringUpAll.this.mFailBringUp.saveParameters(intent, "sFailBringUp");
            } else if (action.equals(DcTesterFailBringUpAll.this.mPhone.getActionDetached())) {
                DcTesterFailBringUpAll.this.log("simulate detaching");
                DcTesterFailBringUpAll.this.mFailBringUp.saveParameters(Integer.MAX_VALUE, DcFailCause.LOST_CONNECTION.getErrorCode(), -1);
            } else if (action.equals(DcTesterFailBringUpAll.this.mPhone.getActionAttached())) {
                DcTesterFailBringUpAll.this.log("simulate attaching");
                DcTesterFailBringUpAll.this.mFailBringUp.saveParameters(0, DcFailCause.NONE.getErrorCode(), -1);
            } else {
                DcTesterFailBringUpAll.this.log("onReceive: unknown action=" + action);
            }
        }
    };
    private PhoneBase mPhone;

    DcTesterFailBringUpAll(PhoneBase phoneBase, Handler handler) {
        this.mPhone = phoneBase;
        if (Build.IS_DEBUGGABLE) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(this.mActionFailBringUp);
            log("register for intent action=" + this.mActionFailBringUp);
            intentFilter.addAction(this.mPhone.getActionDetached());
            log("register for intent action=" + this.mPhone.getActionDetached());
            intentFilter.addAction(this.mPhone.getActionAttached());
            log("register for intent action=" + this.mPhone.getActionAttached());
            phoneBase.getContext().registerReceiver(this.mIntentReceiver, intentFilter, null, handler);
        }
    }

    private void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    /* Access modifiers changed, original: 0000 */
    public void dispose() {
        if (Build.IS_DEBUGGABLE) {
            this.mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public DcFailBringUp getDcFailBringUp() {
        return this.mFailBringUp;
    }
}
