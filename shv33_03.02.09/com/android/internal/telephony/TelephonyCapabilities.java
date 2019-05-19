package com.android.internal.telephony;

import android.telephony.Rlog;

public class TelephonyCapabilities {
    private static final String LOG_TAG = "TelephonyCapabilities";

    private TelephonyCapabilities() {
    }

    public static boolean supportsEcm(Phone phone) {
        boolean z = true;
        Rlog.d(LOG_TAG, "supportsEcm: Phone type = " + phone.getPhoneType() + " Ims Phone = " + phone.getImsPhone());
        if (TelBrand.IS_KDDI) {
            return false;
        }
        if (phone.getPhoneType() != 2 && phone.getImsPhone() == null) {
            z = false;
        }
        return z;
    }

    public static boolean supportsOtasp(Phone phone) {
        return phone.getPhoneType() == 2;
    }

    public static boolean supportsVoiceMessageCount(Phone phone) {
        boolean z = false;
        if (TelBrand.IS_KDDI) {
            return false;
        }
        if (phone.getVoiceMessageCount() != -1) {
            z = true;
        }
        return z;
    }

    public static boolean supportsNetworkSelection(Phone phone) {
        return phone.getPhoneType() == 1;
    }

    public static int getDeviceIdLabel(Phone phone) {
        if (phone.getPhoneType() == 1) {
            return 17039549;
        }
        if (phone.getPhoneType() == 2) {
            return 17039550;
        }
        Rlog.w(LOG_TAG, "getDeviceIdLabel: no known label for phone " + phone.getPhoneName());
        return 0;
    }

    public static boolean supportsConferenceCallManagement(Phone phone) {
        if (phone.getPhoneType() == 1 || phone.getPhoneType() == 3) {
            return true;
        }
        return false;
    }

    public static boolean supportsHoldAndUnhold(Phone phone) {
        if (phone.getPhoneType() == 1 || phone.getPhoneType() == 3 || phone.getPhoneType() == 5) {
            return true;
        }
        return false;
    }

    public static boolean supportsAnswerAndHold(Phone phone) {
        boolean z = true;
        if (TelBrand.IS_KDDI) {
            if (phone.getPhoneType() != 1) {
                z = false;
            }
            return z;
        }
        if (!(phone.getPhoneType() == 1 || phone.getPhoneType() == 3)) {
            z = false;
        }
        return z;
    }

    public static boolean supportsAdn(int phoneType) {
        return phoneType == 1;
    }

    public static boolean canDistinguishDialingAndConnected(int phoneType) {
        return phoneType == 1;
    }
}
