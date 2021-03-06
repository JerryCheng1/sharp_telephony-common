package com.android.internal.telephony;

import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.content.Context;
import android.content.res.Configuration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Telephony.BaseMmsColumns;
import android.provider.Telephony.Mms.Part;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Slog;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.google.android.mms.pdu.PduHeaders;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import libcore.icu.ICU;
import libcore.icu.TimeZoneNames;

public final class MccTable {
    static final String LOG_TAG = "MccTable";
    static ArrayList<MccEntry> sTable = new ArrayList(240);

    static class MccEntry implements Comparable<MccEntry> {
        final String mIso;
        final int mMcc;
        final int mSmallestDigitsMnc;

        MccEntry(int i, String str, int i2) {
            if (str == null) {
                throw new NullPointerException();
            }
            this.mMcc = i;
            this.mIso = str;
            this.mSmallestDigitsMnc = i2;
        }

        public int compareTo(MccEntry mccEntry) {
            return this.mMcc - mccEntry.mMcc;
        }
    }

    static {
        sTable.add(new MccEntry(202, "gr", 2));
        sTable.add(new MccEntry(204, "nl", 2));
        sTable.add(new MccEntry(206, "be", 2));
        sTable.add(new MccEntry(BerTlv.BER_PROACTIVE_COMMAND_TAG, "fr", 2));
        sTable.add(new MccEntry(CommandsInterface.GSM_SMS_FAIL_CAUSE_USIM_APP_TOOLKIT_BUSY, "mc", 2));
        sTable.add(new MccEntry(CommandsInterface.GSM_SMS_FAIL_CAUSE_USIM_DATA_DOWNLOAD_ERROR, "ad", 2));
        sTable.add(new MccEntry(BerTlv.BER_EVENT_DOWNLOAD_TAG, "es", 2));
        sTable.add(new MccEntry(216, "hu", 2));
        sTable.add(new MccEntry(218, "ba", 2));
        sTable.add(new MccEntry(219, "hr", 2));
        sTable.add(new MccEntry(220, "rs", 2));
        sTable.add(new MccEntry(222, "it", 2));
        sTable.add(new MccEntry(225, "va", 2));
        sTable.add(new MccEntry(226, "ro", 2));
        sTable.add(new MccEntry(228, "ch", 2));
        sTable.add(new MccEntry(230, "cz", 2));
        sTable.add(new MccEntry(231, "sk", 2));
        sTable.add(new MccEntry(PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_REPLY_CHARGING_FORWARDING_DENIED, "at", 2));
        sTable.add(new MccEntry(PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_ADDRESS_HIDING_NOT_SUPPORTED, "gb", 2));
        sTable.add(new MccEntry(PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_LACK_OF_PREPAID, "gb", 2));
        sTable.add(new MccEntry(238, "dk", 2));
        sTable.add(new MccEntry(240, "se", 2));
        sTable.add(new MccEntry(242, "no", 2));
        sTable.add(new MccEntry(244, "fi", 2));
        sTable.add(new MccEntry(246, "lt", 2));
        sTable.add(new MccEntry(BearerData.RELATIVE_TIME_MOBILE_INACTIVE, "lv", 2));
        sTable.add(new MccEntry(BearerData.RELATIVE_TIME_RESERVED, "ee", 2));
        sTable.add(new MccEntry(250, "ru", 2));
        sTable.add(new MccEntry(255, "ua", 2));
        sTable.add(new MccEntry(257, "by", 2));
        sTable.add(new MccEntry(259, "md", 2));
        sTable.add(new MccEntry(260, "pl", 2));
        sTable.add(new MccEntry(262, "de", 2));
        sTable.add(new MccEntry(266, "gi", 2));
        sTable.add(new MccEntry(268, "pt", 2));
        sTable.add(new MccEntry(270, "lu", 2));
        sTable.add(new MccEntry(272, "ie", 2));
        sTable.add(new MccEntry(274, "is", 2));
        sTable.add(new MccEntry(276, "al", 2));
        sTable.add(new MccEntry(278, "mt", 2));
        sTable.add(new MccEntry(280, "cy", 2));
        sTable.add(new MccEntry(282, "ge", 2));
        sTable.add(new MccEntry(283, "am", 2));
        sTable.add(new MccEntry(284, "bg", 2));
        sTable.add(new MccEntry(286, "tr", 2));
        sTable.add(new MccEntry(288, "fo", 2));
        sTable.add(new MccEntry(289, "ge", 2));
        sTable.add(new MccEntry(290, "gl", 2));
        sTable.add(new MccEntry(292, "sm", 2));
        sTable.add(new MccEntry(293, "si", 2));
        sTable.add(new MccEntry(294, "mk", 2));
        sTable.add(new MccEntry(295, "li", 2));
        sTable.add(new MccEntry(297, "me", 2));
        sTable.add(new MccEntry(302, "ca", 3));
        sTable.add(new MccEntry(308, "pm", 2));
        sTable.add(new MccEntry(310, "us", 3));
        sTable.add(new MccEntry(311, "us", 3));
        sTable.add(new MccEntry(312, "us", 3));
        sTable.add(new MccEntry(313, "us", 3));
        sTable.add(new MccEntry(314, "us", 3));
        sTable.add(new MccEntry(315, "us", 3));
        sTable.add(new MccEntry(316, "us", 3));
        sTable.add(new MccEntry(330, "pr", 2));
        sTable.add(new MccEntry(332, "vi", 2));
        sTable.add(new MccEntry(334, "mx", 3));
        sTable.add(new MccEntry(338, "jm", 3));
        sTable.add(new MccEntry(340, "gp", 2));
        sTable.add(new MccEntry(342, "bb", 3));
        sTable.add(new MccEntry(344, "ag", 3));
        sTable.add(new MccEntry(346, "ky", 3));
        sTable.add(new MccEntry(348, "vg", 3));
        sTable.add(new MccEntry(350, "bm", 2));
        sTable.add(new MccEntry(352, "gd", 2));
        sTable.add(new MccEntry(354, "ms", 2));
        sTable.add(new MccEntry(356, "kn", 2));
        sTable.add(new MccEntry(358, "lc", 2));
        sTable.add(new MccEntry(360, "vc", 2));
        sTable.add(new MccEntry(362, "ai", 2));
        sTable.add(new MccEntry(363, "aw", 2));
        sTable.add(new MccEntry(364, "bs", 2));
        sTable.add(new MccEntry(365, "ai", 3));
        sTable.add(new MccEntry(366, "dm", 2));
        sTable.add(new MccEntry(368, "cu", 2));
        sTable.add(new MccEntry(370, "do", 2));
        sTable.add(new MccEntry(372, "ht", 2));
        sTable.add(new MccEntry(374, "tt", 2));
        sTable.add(new MccEntry(376, "tc", 2));
        sTable.add(new MccEntry(400, "az", 2));
        sTable.add(new MccEntry(401, "kz", 2));
        sTable.add(new MccEntry(402, "bt", 2));
        sTable.add(new MccEntry(404, "in", 2));
        sTable.add(new MccEntry(405, "in", 2));
        sTable.add(new MccEntry(406, "in", 2));
        sTable.add(new MccEntry(410, "pk", 2));
        sTable.add(new MccEntry(412, "af", 2));
        sTable.add(new MccEntry(413, "lk", 2));
        sTable.add(new MccEntry(414, "mm", 2));
        sTable.add(new MccEntry(415, "lb", 2));
        sTable.add(new MccEntry(416, "jo", 2));
        sTable.add(new MccEntry(417, "sy", 2));
        sTable.add(new MccEntry(418, "iq", 2));
        sTable.add(new MccEntry(419, "kw", 2));
        sTable.add(new MccEntry(420, "sa", 2));
        sTable.add(new MccEntry(421, "ye", 2));
        sTable.add(new MccEntry(422, "om", 2));
        sTable.add(new MccEntry(423, "ps", 2));
        sTable.add(new MccEntry(424, "ae", 2));
        sTable.add(new MccEntry(425, "il", 2));
        sTable.add(new MccEntry(426, "bh", 2));
        sTable.add(new MccEntry(427, "qa", 2));
        sTable.add(new MccEntry(428, "mn", 2));
        sTable.add(new MccEntry(429, "np", 2));
        sTable.add(new MccEntry(430, "ae", 2));
        sTable.add(new MccEntry(431, "ae", 2));
        sTable.add(new MccEntry(432, "ir", 2));
        sTable.add(new MccEntry(434, "uz", 2));
        sTable.add(new MccEntry(436, "tj", 2));
        sTable.add(new MccEntry(437, "kg", 2));
        sTable.add(new MccEntry(438, "tm", 2));
        sTable.add(new MccEntry(440, "jp", 2));
        sTable.add(new MccEntry(441, "jp", 2));
        sTable.add(new MccEntry(450, "kr", 2));
        sTable.add(new MccEntry(452, "vn", 2));
        sTable.add(new MccEntry(454, "hk", 2));
        sTable.add(new MccEntry(455, "mo", 2));
        sTable.add(new MccEntry(456, "kh", 2));
        sTable.add(new MccEntry(457, "la", 2));
        sTable.add(new MccEntry(460, "cn", 2));
        sTable.add(new MccEntry(461, "cn", 2));
        sTable.add(new MccEntry(466, "tw", 2));
        sTable.add(new MccEntry(467, "kp", 2));
        sTable.add(new MccEntry(470, "bd", 2));
        sTable.add(new MccEntry(472, "mv", 2));
        sTable.add(new MccEntry(502, "my", 2));
        sTable.add(new MccEntry(505, "au", 2));
        sTable.add(new MccEntry(510, "id", 2));
        sTable.add(new MccEntry(514, "tl", 2));
        sTable.add(new MccEntry(515, "ph", 2));
        sTable.add(new MccEntry(520, "th", 2));
        sTable.add(new MccEntry(525, "sg", 2));
        sTable.add(new MccEntry(528, "bn", 2));
        sTable.add(new MccEntry(530, "nz", 2));
        sTable.add(new MccEntry(534, "mp", 2));
        sTable.add(new MccEntry(535, "gu", 2));
        sTable.add(new MccEntry(536, "nr", 2));
        sTable.add(new MccEntry(537, "pg", 2));
        sTable.add(new MccEntry(539, "to", 2));
        sTable.add(new MccEntry(540, "sb", 2));
        sTable.add(new MccEntry(541, "vu", 2));
        sTable.add(new MccEntry(542, "fj", 2));
        sTable.add(new MccEntry(543, "wf", 2));
        sTable.add(new MccEntry(544, "as", 2));
        sTable.add(new MccEntry(545, "ki", 2));
        sTable.add(new MccEntry(546, "nc", 2));
        sTable.add(new MccEntry(547, "pf", 2));
        sTable.add(new MccEntry(548, "ck", 2));
        sTable.add(new MccEntry(549, "ws", 2));
        sTable.add(new MccEntry(550, "fm", 2));
        sTable.add(new MccEntry(551, "mh", 2));
        sTable.add(new MccEntry(552, "pw", 2));
        sTable.add(new MccEntry(553, "tv", 2));
        sTable.add(new MccEntry(555, "nu", 2));
        sTable.add(new MccEntry(602, "eg", 2));
        sTable.add(new MccEntry(603, "dz", 2));
        sTable.add(new MccEntry(604, "ma", 2));
        sTable.add(new MccEntry(605, "tn", 2));
        sTable.add(new MccEntry(606, "ly", 2));
        sTable.add(new MccEntry(607, "gm", 2));
        sTable.add(new MccEntry(608, "sn", 2));
        sTable.add(new MccEntry(609, "mr", 2));
        sTable.add(new MccEntry(610, "ml", 2));
        sTable.add(new MccEntry(611, "gn", 2));
        sTable.add(new MccEntry(612, "ci", 2));
        sTable.add(new MccEntry(613, "bf", 2));
        sTable.add(new MccEntry(614, "ne", 2));
        sTable.add(new MccEntry(615, "tg", 2));
        sTable.add(new MccEntry(616, "bj", 2));
        sTable.add(new MccEntry(617, "mu", 2));
        sTable.add(new MccEntry(618, "lr", 2));
        sTable.add(new MccEntry(619, "sl", 2));
        sTable.add(new MccEntry(620, "gh", 2));
        sTable.add(new MccEntry(621, "ng", 2));
        sTable.add(new MccEntry(622, "td", 2));
        sTable.add(new MccEntry(623, "cf", 2));
        sTable.add(new MccEntry(624, "cm", 2));
        sTable.add(new MccEntry(625, "cv", 2));
        sTable.add(new MccEntry(626, BaseMmsColumns.STATUS, 2));
        sTable.add(new MccEntry(627, "gq", 2));
        sTable.add(new MccEntry(628, "ga", 2));
        sTable.add(new MccEntry(629, "cg", 2));
        sTable.add(new MccEntry(630, "cg", 2));
        sTable.add(new MccEntry(631, "ao", 2));
        sTable.add(new MccEntry(632, "gw", 2));
        sTable.add(new MccEntry(633, "sc", 2));
        sTable.add(new MccEntry(634, "sd", 2));
        sTable.add(new MccEntry(635, "rw", 2));
        sTable.add(new MccEntry(636, "et", 2));
        sTable.add(new MccEntry(637, "so", 2));
        sTable.add(new MccEntry(638, "dj", 2));
        sTable.add(new MccEntry(639, "ke", 2));
        sTable.add(new MccEntry(640, "tz", 2));
        sTable.add(new MccEntry(641, "ug", 2));
        sTable.add(new MccEntry(642, "bi", 2));
        sTable.add(new MccEntry(643, "mz", 2));
        sTable.add(new MccEntry(645, "zm", 2));
        sTable.add(new MccEntry(646, "mg", 2));
        sTable.add(new MccEntry(647, "re", 2));
        sTable.add(new MccEntry(648, "zw", 2));
        sTable.add(new MccEntry(649, "na", 2));
        sTable.add(new MccEntry(650, "mw", 2));
        sTable.add(new MccEntry(651, "ls", 2));
        sTable.add(new MccEntry(652, "bw", 2));
        sTable.add(new MccEntry(653, "sz", 2));
        sTable.add(new MccEntry(654, "km", 2));
        sTable.add(new MccEntry(655, "za", 2));
        sTable.add(new MccEntry(657, "er", 2));
        sTable.add(new MccEntry(658, "sh", 2));
        sTable.add(new MccEntry(659, "ss", 2));
        sTable.add(new MccEntry(702, "bz", 2));
        sTable.add(new MccEntry(704, "gt", 2));
        sTable.add(new MccEntry(706, "sv", 2));
        sTable.add(new MccEntry(708, "hn", 3));
        sTable.add(new MccEntry(710, "ni", 2));
        sTable.add(new MccEntry(712, "cr", 2));
        sTable.add(new MccEntry(714, "pa", 2));
        sTable.add(new MccEntry(716, "pe", 2));
        sTable.add(new MccEntry(722, "ar", 3));
        sTable.add(new MccEntry(724, "br", 2));
        sTable.add(new MccEntry(730, Part.CONTENT_LOCATION, 2));
        sTable.add(new MccEntry(732, "co", 3));
        sTable.add(new MccEntry(734, "ve", 2));
        sTable.add(new MccEntry(736, "bo", 2));
        sTable.add(new MccEntry(738, "gy", 2));
        sTable.add(new MccEntry(740, "ec", 2));
        sTable.add(new MccEntry(742, "gf", 2));
        sTable.add(new MccEntry(744, "py", 2));
        sTable.add(new MccEntry(746, "sr", 2));
        sTable.add(new MccEntry(748, "uy", 2));
        sTable.add(new MccEntry(750, "fk", 2));
        Collections.sort(sTable);
    }

    private static boolean canUpdateLocale(Context context) {
        return (userHasPersistedLocale() || isDeviceProvisioned(context)) ? false : true;
    }

    public static String countryCodeForMcc(int i) {
        MccEntry entryForMcc = entryForMcc(i);
        return entryForMcc == null ? "" : entryForMcc.mIso;
    }

    public static String defaultLanguageForMcc(int i) {
        MccEntry entryForMcc = entryForMcc(i);
        if (entryForMcc == null) {
            Slog.d(LOG_TAG, "defaultLanguageForMcc(" + i + "): no country for mcc");
            return null;
        }
        String language = ICU.addLikelySubtags(new Locale("und", entryForMcc.mIso)).getLanguage();
        Slog.d(LOG_TAG, "defaultLanguageForMcc(" + i + "): country " + entryForMcc.mIso + " uses " + language);
        return language;
    }

    public static String defaultTimeZoneForMcc(int i) {
        MccEntry entryForMcc = entryForMcc(i);
        if (entryForMcc != null) {
            String[] forLocale = TimeZoneNames.forLocale(new Locale("", entryForMcc.mIso));
            if (forLocale.length != 0) {
                return forLocale[0];
            }
        }
        return null;
    }

    private static MccEntry entryForMcc(int i) {
        int binarySearch = Collections.binarySearch(sTable, new MccEntry(i, "", 0));
        return binarySearch < 0 ? null : (MccEntry) sTable.get(binarySearch);
    }

    private static Locale getLocaleForLanguageCountry(Context context, String str, String str2) {
        if (str == null) {
            Slog.d(LOG_TAG, "getLocaleForLanguageCountry: skipping no language");
            return null;
        }
        if (str2 == null) {
            str2 = "";
        }
        if (isDebuggingMccOverride() || canUpdateLocale(context)) {
            Locale locale = new Locale(str, str2);
            try {
                ArrayList<String> arrayList = new ArrayList(Arrays.asList(context.getAssets().getLocales()));
                arrayList.remove("ar-XB");
                arrayList.remove("en-XA");
                Locale locale2 = null;
                for (String replace : arrayList) {
                    Locale forLanguageTag = Locale.forLanguageTag(replace.replace('_', '-'));
                    if (!(forLanguageTag == null || "und".equals(forLanguageTag.getLanguage()) || forLanguageTag.getLanguage().isEmpty() || forLanguageTag.getCountry().isEmpty() || !forLanguageTag.getLanguage().equals(locale.getLanguage()))) {
                        if (forLanguageTag.getCountry().equals(locale.getCountry())) {
                            Slog.d(LOG_TAG, "getLocaleForLanguageCountry: got perfect match: " + forLanguageTag.toLanguageTag());
                            return forLanguageTag;
                        } else if (locale2 == null) {
                            locale2 = forLanguageTag;
                        }
                    }
                }
                if (locale2 != null) {
                    Slog.d(LOG_TAG, "getLocaleForLanguageCountry: got a language-only match: " + locale2.toLanguageTag());
                    return locale2;
                }
                Slog.d(LOG_TAG, "getLocaleForLanguageCountry: no locales for language " + str);
                return null;
            } catch (Exception e) {
                Slog.d(LOG_TAG, "getLocaleForLanguageCountry: exception", e);
            }
        } else {
            Slog.d(LOG_TAG, "getLocaleForLanguageCountry: not permitted to update locale");
            return null;
        }
    }

    private static Locale getLocaleFromMcc(Context context, int i) {
        String defaultLanguageForMcc = defaultLanguageForMcc(i);
        String countryCodeForMcc = countryCodeForMcc(i);
        Slog.d(LOG_TAG, "getLocaleFromMcc to " + defaultLanguageForMcc + "_" + countryCodeForMcc + " mcc=" + i);
        return getLocaleForLanguageCountry(context, defaultLanguageForMcc, countryCodeForMcc);
    }

    private static boolean isDebuggingMccOverride() {
        return Build.IS_DEBUGGABLE && !SystemProperties.get("persist.sys.override_mcc", "").isEmpty();
    }

    private static boolean isDeviceProvisioned(Context context) {
        try {
            return Global.getInt(context.getContentResolver(), "device_provisioned") != 0;
        } catch (SettingNotFoundException e) {
            return false;
        }
    }

    public static void setSystemLocale(Context context, String str, String str2) {
        Locale localeForLanguageCountry = getLocaleForLanguageCountry(context, str, str2);
        if (localeForLanguageCountry != null) {
            Configuration configuration = new Configuration();
            configuration.setLocale(localeForLanguageCountry);
            configuration.userSetLocale = false;
            Slog.d(LOG_TAG, "setSystemLocale: updateLocale config=" + configuration);
            try {
                ActivityManagerNative.getDefault().updateConfiguration(configuration);
                return;
            } catch (RemoteException e) {
                Slog.d(LOG_TAG, "setSystemLocale exception", e);
                return;
            }
        }
        Slog.d(LOG_TAG, "setSystemLocale: no locale");
    }

    private static void setTimezoneFromMccIfNeeded(Context context, int i) {
        String str = SystemProperties.get("persist.sys.timezone");
        if (str == null || str.length() == 0) {
            String defaultTimeZoneForMcc = defaultTimeZoneForMcc(i);
            if (defaultTimeZoneForMcc != null && defaultTimeZoneForMcc.length() > 0) {
                ((AlarmManager) context.getSystemService("alarm")).setTimeZone(defaultTimeZoneForMcc);
                Slog.d(LOG_TAG, "timezone set to " + defaultTimeZoneForMcc);
            }
        }
    }

    private static void setWifiCountryCodeFromMcc(Context context, int i) {
        String countryCodeForMcc = countryCodeForMcc(i);
        Slog.d(LOG_TAG, "WIFI_COUNTRY_CODE set to " + countryCodeForMcc);
        ((WifiManager) context.getSystemService("wifi")).setCountryCode(countryCodeForMcc, true);
    }

    public static int smallestDigitsMccForMnc(int i) {
        MccEntry entryForMcc = entryForMcc(i);
        return entryForMcc == null ? 2 : entryForMcc.mSmallestDigitsMnc;
    }

    public static void updateMccMncConfiguration(Context context, String str, boolean z) {
        CharSequence str2;
        Slog.d(LOG_TAG, "updateMccMncConfiguration mccmnc='" + str2 + "' fromServiceState=" + z);
        if (Build.IS_DEBUGGABLE) {
            String str3 = SystemProperties.get("persist.sys.override_mcc");
            if (!TextUtils.isEmpty(str3)) {
                Slog.d(LOG_TAG, "updateMccMncConfiguration overriding mccmnc='" + str3 + "'");
                str2 = str3;
            }
        }
        if (!TextUtils.isEmpty(str2)) {
            Slog.d(LOG_TAG, "updateMccMncConfiguration defaultMccMnc=" + TelephonyManager.getDefault().getSimOperator());
            try {
                int parseInt = Integer.parseInt(str2.substring(0, 3));
                int parseInt2 = Integer.parseInt(str2.substring(3));
                Slog.d(LOG_TAG, "updateMccMncConfiguration: mcc=" + parseInt + ", mnc=" + parseInt2);
                Locale locale = null;
                if (parseInt != 0) {
                    setTimezoneFromMccIfNeeded(context, parseInt);
                    locale = getLocaleFromMcc(context, parseInt);
                }
                if (z) {
                    setWifiCountryCodeFromMcc(context, parseInt);
                    return;
                }
                try {
                    Configuration configuration = new Configuration();
                    if (parseInt != 0) {
                        configuration.mcc = parseInt;
                        if (parseInt2 == 0) {
                            parseInt2 = 65535;
                        }
                        configuration.mnc = parseInt2;
                        parseInt2 = 1;
                    } else {
                        parseInt2 = 0;
                    }
                    if (locale != null) {
                        configuration.setLocale(locale);
                        parseInt2 = 1;
                    }
                    if (parseInt2 != 0) {
                        Slog.d(LOG_TAG, "updateMccMncConfiguration updateConfig config=" + configuration);
                        ActivityManagerNative.getDefault().updateConfiguration(configuration);
                        return;
                    }
                    Slog.d(LOG_TAG, "updateMccMncConfiguration nothing to update");
                } catch (RemoteException e) {
                    Slog.e(LOG_TAG, "Can't update configuration", e);
                }
            } catch (NumberFormatException e2) {
                Slog.e(LOG_TAG, "Error parsing IMSI: " + str2);
            }
        } else if (z) {
            setWifiCountryCodeFromMcc(context, 0);
        }
    }

    private static boolean userHasPersistedLocale() {
        return (SystemProperties.get("persist.sys.language", "").isEmpty() && SystemProperties.get("persist.sys.country", "").isEmpty()) ? false : true;
    }
}
