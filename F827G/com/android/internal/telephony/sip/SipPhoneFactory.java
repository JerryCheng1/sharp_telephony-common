package com.android.internal.telephony.sip;

import android.content.Context;
import android.net.sip.SipProfile.Builder;
import android.telephony.Rlog;
import com.android.internal.telephony.PhoneNotifier;
import java.text.ParseException;

public class SipPhoneFactory {
    public static SipPhone makePhone(String str, Context context, PhoneNotifier phoneNotifier) {
        try {
            return new SipPhone(context, phoneNotifier, new Builder(str).build());
        } catch (ParseException e) {
            Rlog.w("SipPhoneFactory", "makePhone", e);
            return null;
        }
    }
}
