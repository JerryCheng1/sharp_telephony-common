package com.android.internal.telephony.dataconnection;

import android.content.Intent;
import android.telephony.Rlog;

class DcFailBringUp {
    static final String ACTION_FAIL_BRINGUP = "action_fail_bringup";
    static final String COUNTER = "counter";
    private static final boolean DBG = true;
    static final int DEFAULT_COUNTER = 1;
    static final DcFailCause DEFAULT_FAIL_CAUSE = DcFailCause.ERROR_UNSPECIFIED;
    static final int DEFAULT_SUGGESTED_RETRY_TIME = -1;
    static final String FAIL_CAUSE = "fail_cause";
    static final String INTENT_BASE = DataConnection.class.getPackage().getName();
    private static final String LOG_TAG = "DcFailBringUp";
    static final String SUGGESTED_RETRY_TIME = "suggested_retry_time";
    int mCounter;
    DcFailCause mFailCause;
    int mSuggestedRetryTime;

    DcFailBringUp() {
    }

    private static void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    /* Access modifiers changed, original: 0000 */
    public void saveParameters(int i, int i2, int i3) {
        this.mCounter = i;
        this.mFailCause = DcFailCause.fromInt(i2);
        this.mSuggestedRetryTime = i3;
    }

    /* Access modifiers changed, original: 0000 */
    public void saveParameters(Intent intent, String str) {
        log(str + ".saveParameters: action=" + intent.getAction());
        this.mCounter = intent.getIntExtra(COUNTER, 1);
        this.mFailCause = DcFailCause.fromInt(intent.getIntExtra(FAIL_CAUSE, DEFAULT_FAIL_CAUSE.getErrorCode()));
        this.mSuggestedRetryTime = intent.getIntExtra(SUGGESTED_RETRY_TIME, -1);
        log(str + ".saveParameters: " + this);
    }

    public String toString() {
        return "{mCounter=" + this.mCounter + " mFailCause=" + this.mFailCause + " mSuggestedRetryTime=" + this.mSuggestedRetryTime + "}";
    }
}
