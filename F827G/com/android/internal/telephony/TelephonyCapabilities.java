package com.android.internal.telephony;

import android.telephony.Rlog;

public class TelephonyCapabilities {
    private static final String LOG_TAG = "TelephonyCapabilities";

    private TelephonyCapabilities() {
    }

    public static boolean canDistinguishDialingAndConnected(int i) {
        return i == 1;
    }

    public static int getDeviceIdLabel(Phone phone) {
        if (phone.getPhoneType() == 1) {
            return 17039563;
        }
        if (phone.getPhoneType() == 2) {
            return 17039564;
        }
        Rlog.w(LOG_TAG, "getDeviceIdLabel: no known label for phone " + phone.getPhoneName());
        return 0;
    }

    public static boolean supportsAdn(int i) {
        return i == 1 || i == 2;
    }

    public static boolean supportsAnswerAndHold(Phone phone) {
        boolean z = true;
        if (!TelBrand.IS_KDDI) {
            return phone.getPhoneType() == 1 || phone.getPhoneType() == 3;
        } else {
            if (phone.getPhoneType() != 1) {
                z = false;
            }
            return z;
        }
    }

    public static boolean supportsConferenceCallManagement(Phone phone) {
        return phone.getPhoneType() == 1 || phone.getPhoneType() == 3;
    }

    public static boolean supportsEcm(Phone phone) {
        Rlog.d(LOG_TAG, "supportsEcm: Phone type = " + phone.getPhoneType() + " Ims Phone = " + phone.getImsPhone());
        return !TelBrand.IS_KDDI ? phone.getPhoneType() == 2 || phone.getImsPhone() != null : false;
    }

    public static boolean supportsHoldAndUnhold(Phone phone) {
        return phone.getPhoneType() == 1 || phone.getPhoneType() == 3 || phone.getPhoneType() == 5;
    }

    public static boolean supportsNetworkSelection(Phone phone) {
        return phone.getPhoneType() == 1;
    }

    public static boolean supportsOtasp(Phone phone) {
        return phone.getPhoneType() == 2;
    }

    public static boolean supportsVoiceMessageCount(Phone phone) {
        return (TelBrand.IS_KDDI || phone.getVoiceMessageCount() == -1) ? false : true;
    }
}
