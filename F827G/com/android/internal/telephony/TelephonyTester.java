package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.telephony.Rlog;
import com.android.ims.ImsCall;
import com.android.ims.ImsConferenceState;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneCall;
import com.android.internal.telephony.test.TestConferenceEventPackageParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class TelephonyTester {
    private static final String ACTION_TEST_CONFERENCE_EVENT_PACKAGE = "com.android.internal.telephony.TestConferenceEventPackage";
    private static final boolean DBG = true;
    private static final String EXTRA_FILENAME = "filename";
    private static final String LOG_TAG = "TelephonyTester";
    protected BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            TelephonyTester.log("sIntentReceiver.onReceive: action=" + action);
            if (action.equals(TelephonyTester.this.mPhone.getActionDetached())) {
                TelephonyTester.log("simulate detaching");
                TelephonyTester.this.mPhone.getServiceStateTracker().mDetachedRegistrants.notifyRegistrants();
            } else if (action.equals(TelephonyTester.this.mPhone.getActionAttached())) {
                TelephonyTester.log("simulate attaching");
                TelephonyTester.this.mPhone.getServiceStateTracker().mAttachedRegistrants.notifyRegistrants();
            } else if (action.equals(TelephonyTester.ACTION_TEST_CONFERENCE_EVENT_PACKAGE)) {
                TelephonyTester.log("inject simulated conference event package");
                TelephonyTester.this.handleTestConferenceEventPackage(context, intent.getStringExtra(TelephonyTester.EXTRA_FILENAME));
            } else {
                TelephonyTester.log("onReceive: unknown action=" + action);
            }
        }
    };
    private PhoneBase mPhone;

    TelephonyTester(PhoneBase phoneBase) {
        this.mPhone = phoneBase;
        if (Build.IS_DEBUGGABLE) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(this.mPhone.getActionDetached());
            log("register for intent action=" + this.mPhone.getActionDetached());
            intentFilter.addAction(this.mPhone.getActionAttached());
            log("register for intent action=" + this.mPhone.getActionAttached());
            if (this.mPhone.getPhoneType() == 5) {
                log("register for intent action=com.android.internal.telephony.TestConferenceEventPackage");
                intentFilter.addAction(ACTION_TEST_CONFERENCE_EVENT_PACKAGE);
            }
            phoneBase.getContext().registerReceiver(this.mIntentReceiver, intentFilter, null, this.mPhone.getHandler());
        }
    }

    private void handleTestConferenceEventPackage(Context context, String str) {
        ImsPhone imsPhone = (ImsPhone) this.mPhone;
        if (imsPhone != null) {
            ImsPhoneCall foregroundCall = imsPhone.getForegroundCall();
            if (foregroundCall != null) {
                ImsCall imsCall = foregroundCall.getImsCall();
                if (imsCall != null) {
                    File file = new File(context.getFilesDir(), str);
                    try {
                        ImsConferenceState parse = new TestConferenceEventPackageParser(new FileInputStream(file)).parse();
                        if (parse != null) {
                            imsCall.conferenceStateUpdated(parse);
                        }
                    } catch (FileNotFoundException e) {
                        log("Test conference event package file not found: " + file.getAbsolutePath());
                    }
                }
            }
        }
    }

    private static void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    /* Access modifiers changed, original: 0000 */
    public void dispose() {
        if (Build.IS_DEBUGGABLE) {
            this.mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        }
    }
}
