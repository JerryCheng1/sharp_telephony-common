package com.android.internal.telephony.sip;

import android.content.Context;
import android.net.sip.SipProfile;
import android.telephony.Rlog;
import com.android.internal.telephony.PhoneNotifier;
import java.text.ParseException;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class SipPhoneFactory {
    public static SipPhone makePhone(String sipUri, Context context, PhoneNotifier phoneNotifier) {
        try {
            return new SipPhone(context, phoneNotifier, new SipProfile.Builder(sipUri).build());
        } catch (ParseException e) {
            Rlog.w("SipPhoneFactory", "makePhone", e);
            return null;
        }
    }
}
