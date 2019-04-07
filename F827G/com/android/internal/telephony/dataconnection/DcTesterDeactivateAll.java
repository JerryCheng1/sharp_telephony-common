package com.android.internal.telephony.dataconnection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.telephony.Rlog;
import com.android.internal.telephony.PhoneBase;
import java.util.Iterator;

public class DcTesterDeactivateAll {
    private static final boolean DBG = true;
    private static final String LOG_TAG = "DcTesterDeacativeAll";
    public static String sActionDcTesterDeactivateAll = "com.android.internal.telephony.dataconnection.action_deactivate_all";
    private DcController mDcc;
    private PhoneBase mPhone;
    protected BroadcastReceiver sIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            DcTesterDeactivateAll.log("sIntentReceiver.onReceive: action=" + action);
            if (action.equals(DcTesterDeactivateAll.sActionDcTesterDeactivateAll) || action.equals(DcTesterDeactivateAll.this.mPhone.getActionDetached())) {
                DcTesterDeactivateAll.log("Send DEACTIVATE to all Dcc's");
                if (DcTesterDeactivateAll.this.mDcc != null) {
                    Iterator it = DcTesterDeactivateAll.this.mDcc.mDcListAll.iterator();
                    while (it.hasNext()) {
                        ((DataConnection) it.next()).tearDownNow();
                    }
                    return;
                }
                DcTesterDeactivateAll.log("onReceive: mDcc is null, ignoring");
                return;
            }
            DcTesterDeactivateAll.log("onReceive: unknown action=" + action);
        }
    };

    DcTesterDeactivateAll(PhoneBase phoneBase, DcController dcController, Handler handler) {
        this.mPhone = phoneBase;
        this.mDcc = dcController;
        if (Build.IS_DEBUGGABLE) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(sActionDcTesterDeactivateAll);
            log("register for intent action=" + sActionDcTesterDeactivateAll);
            intentFilter.addAction(this.mPhone.getActionDetached());
            log("register for intent action=" + this.mPhone.getActionDetached());
            phoneBase.getContext().registerReceiver(this.sIntentReceiver, intentFilter, null, handler);
        }
    }

    private static void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    /* Access modifiers changed, original: 0000 */
    public void dispose() {
        if (Build.IS_DEBUGGABLE) {
            this.mPhone.getContext().unregisterReceiver(this.sIntentReceiver);
        }
    }
}
