package com.android.internal.telephony.dataconnection;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.PhoneBase;

public class DcRetryAlarmController {
    private static final boolean DBG = true;
    private static final String INTENT_RETRY_ALARM_TAG = "tag";
    private static final String INTENT_RETRY_ALARM_WHAT = "what";
    private String mActionRetry;
    private AlarmManager mAlarmManager;
    private DataConnection mDc;
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TextUtils.isEmpty(action)) {
                DcRetryAlarmController.this.log("onReceive: ignore empty action='" + action + "'");
            } else if (!TextUtils.equals(action, DcRetryAlarmController.this.mActionRetry)) {
                DcRetryAlarmController.this.log("onReceive: unknown action=" + action);
            } else if (!intent.hasExtra(DcRetryAlarmController.INTENT_RETRY_ALARM_WHAT)) {
                throw new RuntimeException(DcRetryAlarmController.this.mActionRetry + " has no INTENT_RETRY_ALRAM_WHAT");
            } else if (intent.hasExtra("tag")) {
                DcRetryAlarmController.this.mPartialWakeLock.acquire(1000);
                int intExtra = intent.getIntExtra(DcRetryAlarmController.INTENT_RETRY_ALARM_WHAT, Integer.MAX_VALUE);
                int intExtra2 = intent.getIntExtra("tag", Integer.MAX_VALUE);
                DcRetryAlarmController.this.log("onReceive: action=" + action + " sendMessage(what:" + DcRetryAlarmController.this.mDc.getWhatToString(intExtra) + ", tag:" + intExtra2 + ")");
                DcRetryAlarmController.this.mDc.sendMessage(DcRetryAlarmController.this.mDc.obtainMessage(intExtra, intExtra2, 0));
            } else {
                throw new RuntimeException(DcRetryAlarmController.this.mActionRetry + " has no INTENT_RETRY_ALRAM_TAG");
            }
        }
    };
    private String mLogTag = "DcRac";
    private WakeLock mPartialWakeLock;
    private PhoneBase mPhone;

    DcRetryAlarmController(PhoneBase phoneBase, DataConnection dataConnection) {
        this.mLogTag = dataConnection.getName();
        this.mPhone = phoneBase;
        this.mDc = dataConnection;
        this.mAlarmManager = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
        this.mActionRetry = this.mDc.getClass().getCanonicalName() + "." + this.mDc.getName() + ".action_retry";
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(this.mActionRetry);
        log("DcRetryAlarmController: register for intent action=" + this.mActionRetry);
        this.mPhone.getContext().registerReceiver(this.mIntentReceiver, intentFilter, null, this.mDc.getHandler());
        this.mPartialWakeLock = ((PowerManager) this.mPhone.getContext().getSystemService("power")).newWakeLock(1, this.mLogTag);
    }

    private void log(String str) {
        Rlog.d(this.mLogTag, "[dcRac] " + str);
    }

    /* Access modifiers changed, original: 0000 */
    public void dispose() {
        log("dispose");
        this.mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        this.mPhone = null;
        this.mDc = null;
        this.mAlarmManager = null;
        this.mActionRetry = null;
    }

    /* Access modifiers changed, original: 0000 */
    public int getSuggestedRetryTime(DataConnection dataConnection, AsyncResult asyncResult) {
        int i = -1;
        DataCallResponse dataCallResponse = (DataCallResponse) asyncResult.result;
        int i2 = dataCallResponse.suggestedRetryTime;
        if (i2 == Integer.MAX_VALUE) {
            log("getSuggestedRetryTime: suggestedRetryTime is MAX_INT, retry NOT needed");
        } else if (i2 >= 0) {
            log("getSuggestedRetryTime: suggestedRetryTime is >= 0 use it");
            i = i2;
        } else if (dataConnection.mRetryManager.isRetryNeeded()) {
            i = dataConnection.mRetryManager.getRetryTimer();
            if (i < 0) {
                i = 0;
            }
            log("getSuggestedRetryTime: retry is needed");
        } else {
            log("getSuggestedRetryTime: retry is NOT needed");
        }
        log("getSuggestedRetryTime: " + i + " response=" + dataCallResponse + " dc=" + dataConnection);
        return i;
    }

    public void startRetryAlarm(int i, int i2, int i3) {
        Intent intent = new Intent(this.mActionRetry);
        intent.putExtra(INTENT_RETRY_ALARM_WHAT, i);
        intent.putExtra("tag", i2);
        log("startRetryAlarm: next attempt in " + (i3 / 1000) + "s" + " what=" + i + " tag=" + i2);
        this.mAlarmManager.set(2, SystemClock.elapsedRealtime() + ((long) i3), PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728));
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(this.mLogTag).append(" [dcRac] ");
        stringBuilder.append(" mPhone=").append(this.mPhone);
        stringBuilder.append(" mDc=").append(this.mDc);
        stringBuilder.append(" mAlaramManager=").append(this.mAlarmManager);
        stringBuilder.append(" mActionRetry=").append(this.mActionRetry);
        return stringBuilder.toString();
    }
}
