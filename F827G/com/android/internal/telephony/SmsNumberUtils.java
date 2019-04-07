package com.android.internal.telephony;

import android.content.Context;
import android.os.Build;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class SmsNumberUtils {
    private static int[] ALL_COUNTRY_CODES = null;
    private static final int CDMA_HOME_NETWORK = 1;
    private static final int CDMA_ROAMING_NETWORK = 2;
    private static final boolean DBG = Build.IS_DEBUGGABLE;
    private static final int GSM_UMTS_NETWORK = 0;
    private static HashMap<String, ArrayList<String>> IDDS_MAPS = new HashMap();
    private static int MAX_COUNTRY_CODES_LENGTH = 0;
    private static final int MIN_COUNTRY_AREA_LOCAL_LENGTH = 10;
    private static final int NANP_CC = 1;
    private static final String NANP_IDD = "011";
    private static final int NANP_LONG_LENGTH = 11;
    private static final int NANP_MEDIUM_LENGTH = 10;
    private static final String NANP_NDD = "1";
    private static final int NANP_SHORT_LENGTH = 7;
    private static final int NP_CC_AREA_LOCAL = 104;
    private static final int NP_HOMEIDD_CC_AREA_LOCAL = 101;
    private static final int NP_INTERNATIONAL_BEGIN = 100;
    private static final int NP_LOCALIDD_CC_AREA_LOCAL = 103;
    private static final int NP_NANP_AREA_LOCAL = 2;
    private static final int NP_NANP_BEGIN = 1;
    private static final int NP_NANP_LOCAL = 1;
    private static final int NP_NANP_LOCALIDD_CC_AREA_LOCAL = 5;
    private static final int NP_NANP_NBPCD_CC_AREA_LOCAL = 4;
    private static final int NP_NANP_NBPCD_HOMEIDD_CC_AREA_LOCAL = 6;
    private static final int NP_NANP_NDD_AREA_LOCAL = 3;
    private static final int NP_NBPCD_CC_AREA_LOCAL = 102;
    private static final int NP_NBPCD_HOMEIDD_CC_AREA_LOCAL = 100;
    private static final int NP_NONE = 0;
    private static final String PLUS_SIGN = "+";
    private static final String TAG = "SmsNumberUtils";

    private static class NumberEntry {
        public String IDD;
        public int countryCode;
        public String number;

        public NumberEntry(String str) {
            this.number = str;
        }
    }

    private static int checkInternationalNumberPlan(Context context, NumberEntry numberEntry, ArrayList<String> arrayList, String str) {
        String str2 = numberEntry.number;
        String substring;
        int countryCode;
        if (str2.startsWith(PLUS_SIGN)) {
            substring = str2.substring(1);
            if (substring.startsWith(str)) {
                countryCode = getCountryCode(context, substring.substring(str.length()));
                if (countryCode > 0) {
                    numberEntry.countryCode = countryCode;
                    return 100;
                }
            }
            countryCode = getCountryCode(context, substring);
            if (countryCode > 0) {
                numberEntry.countryCode = countryCode;
                return NP_NBPCD_CC_AREA_LOCAL;
            }
        } else if (str2.startsWith(str)) {
            countryCode = getCountryCode(context, str2.substring(str.length()));
            if (countryCode > 0) {
                numberEntry.countryCode = countryCode;
                return NP_HOMEIDD_CC_AREA_LOCAL;
            }
        } else {
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                substring = (String) it.next();
                if (str2.startsWith(substring)) {
                    int countryCode2 = getCountryCode(context, str2.substring(substring.length()));
                    if (countryCode2 > 0) {
                        numberEntry.countryCode = countryCode2;
                        numberEntry.IDD = substring;
                        return NP_LOCALIDD_CC_AREA_LOCAL;
                    }
                }
            }
            if (!str2.startsWith("0")) {
                countryCode = getCountryCode(context, str2);
                if (countryCode > 0) {
                    numberEntry.countryCode = countryCode;
                    return NP_CC_AREA_LOCAL;
                }
            }
        }
        return 0;
    }

    private static int checkNANP(NumberEntry numberEntry, ArrayList<String> arrayList) {
        String str = numberEntry.number;
        if (str.length() == 7) {
            int i;
            char charAt = str.charAt(0);
            if (charAt < '2' || charAt > '9') {
                i = 0;
            } else {
                for (i = 1; i < 7; i++) {
                    if (!PhoneNumberUtils.isISODigit(str.charAt(i))) {
                        i = 0;
                        break;
                    }
                }
                i = 1;
            }
            if (i != 0) {
                return 1;
            }
        } else if (str.length() == 10) {
            if (isNANP(str)) {
                return 2;
            }
        } else if (str.length() == 11) {
            if (isNANP(str)) {
                return 3;
            }
        } else if (str.startsWith(PLUS_SIGN)) {
            String substring = str.substring(1);
            if (substring.length() == 11) {
                if (isNANP(substring)) {
                    return 4;
                }
            } else if (substring.startsWith(NANP_IDD) && substring.length() == 14 && isNANP(substring.substring(3))) {
                return 6;
            }
        } else {
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                String str2 = (String) it.next();
                if (str.startsWith(str2)) {
                    String substring2 = str.substring(str2.length());
                    if (substring2 != null && substring2.startsWith(String.valueOf(1)) && isNANP(substring2)) {
                        numberEntry.IDD = str2;
                        return 5;
                    }
                }
            }
        }
        return 0;
    }

    private static boolean compareGid1(PhoneBase phoneBase, String str) {
        boolean z = true;
        String groupIdLevel1 = phoneBase.getGroupIdLevel1();
        if (!TextUtils.isEmpty(str)) {
            int length = str.length();
            if (groupIdLevel1 == null || groupIdLevel1.length() < length || !groupIdLevel1.substring(0, length).equalsIgnoreCase(str)) {
                if (DBG) {
                    Rlog.d(TAG, " gid1 " + groupIdLevel1 + " serviceGid1 " + str);
                }
                z = false;
            }
            if (DBG) {
                Rlog.d(TAG, "compareGid1 is " + (z ? "Same" : "Different"));
            }
        } else if (DBG) {
            Rlog.d(TAG, "compareGid1 serviceGid is empty, return " + true);
        }
        return z;
    }

    public static String filterDestAddr(PhoneBase phoneBase, String str) {
        if (DBG) {
            Rlog.d(TAG, "enter filterDestAddr. destAddr=\"" + str + "\"");
        }
        if (str == null || !PhoneNumberUtils.isGlobalPhoneNumber(str)) {
            Rlog.w(TAG, "destAddr" + str + " is not a global phone number!");
            return str;
        }
        String networkOperator = TelephonyManager.getDefault().getNetworkOperator();
        String str2 = null;
        if (needToConvert(phoneBase)) {
            int networkType = getNetworkType(phoneBase);
            if (!(networkType == -1 || TextUtils.isEmpty(networkOperator))) {
                networkOperator = networkOperator.substring(0, 3);
                if (networkOperator != null && networkOperator.trim().length() > 0) {
                    str2 = formatNumber(phoneBase.getContext(), str, networkOperator, networkType);
                }
            }
        }
        if (DBG) {
            Rlog.d(TAG, "leave filterDestAddr, new destAddr=\"" + str2 + "\"");
        }
        return str2 != null ? str2 : str;
    }

    private static java.lang.String formatNumber(android.content.Context r10, java.lang.String r11, java.lang.String r12, int r13) {
        /*
        r0 = 0;
        r9 = 2;
        r8 = 1;
        if (r11 != 0) goto L_0x000d;
    L_0x0005:
        r0 = new java.lang.IllegalArgumentException;
        r1 = "number is null";
        r0.<init>(r1);
        throw r0;
    L_0x000d:
        if (r12 == 0) goto L_0x0019;
    L_0x000f:
        r1 = r12.trim();
        r1 = r1.length();
        if (r1 != 0) goto L_0x0021;
    L_0x0019:
        r0 = new java.lang.IllegalArgumentException;
        r1 = "activeMcc is null or empty!";
        r0.<init>(r1);
        throw r0;
    L_0x0021:
        r1 = android.telephony.PhoneNumberUtils.extractNetworkPortion(r11);
        if (r1 == 0) goto L_0x002d;
    L_0x0027:
        r2 = r1.length();
        if (r2 != 0) goto L_0x0035;
    L_0x002d:
        r0 = new java.lang.IllegalArgumentException;
        r1 = "Number is invalid!";
        r0.<init>(r1);
        throw r0;
    L_0x0035:
        r3 = new com.android.internal.telephony.SmsNumberUtils$NumberEntry;
        r3.<init>(r1);
        r2 = getAllIDDs(r10, r12);
        r4 = checkNANP(r3, r2);
        r5 = DBG;
        if (r5 == 0) goto L_0x0062;
    L_0x0046:
        r5 = "SmsNumberUtils";
        r6 = new java.lang.StringBuilder;
        r6.<init>();
        r7 = "NANP type: ";
        r6 = r6.append(r7);
        r7 = getNumberPlanType(r4);
        r6 = r6.append(r7);
        r6 = r6.toString();
        android.telephony.Rlog.d(r5, r6);
    L_0x0062:
        if (r4 == r8) goto L_0x0069;
    L_0x0064:
        if (r4 == r9) goto L_0x0069;
    L_0x0066:
        r5 = 3;
        if (r4 != r5) goto L_0x006b;
    L_0x0069:
        r0 = r1;
    L_0x006a:
        return r0;
    L_0x006b:
        r5 = 4;
        if (r4 != r5) goto L_0x0077;
    L_0x006e:
        if (r13 == r8) goto L_0x0072;
    L_0x0070:
        if (r13 != r9) goto L_0x0069;
    L_0x0072:
        r0 = r1.substring(r8);
        goto L_0x006a;
    L_0x0077:
        r5 = 5;
        if (r4 != r5) goto L_0x00b1;
    L_0x007a:
        if (r13 == r8) goto L_0x0069;
    L_0x007c:
        if (r13 != 0) goto L_0x00a0;
    L_0x007e:
        r2 = r3.IDD;
        if (r2 == 0) goto L_0x0088;
    L_0x0082:
        r0 = r3.IDD;
        r0 = r0.length();
    L_0x0088:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "+";
        r2 = r2.append(r3);
        r0 = r1.substring(r0);
        r0 = r2.append(r0);
        r0 = r0.toString();
        goto L_0x006a;
    L_0x00a0:
        if (r13 != r9) goto L_0x00b1;
    L_0x00a2:
        r2 = r3.IDD;
        if (r2 == 0) goto L_0x00ac;
    L_0x00a6:
        r0 = r3.IDD;
        r0 = r0.length();
    L_0x00ac:
        r0 = r1.substring(r0);
        goto L_0x006a;
    L_0x00b1:
        r4 = "011";
        r4 = checkInternationalNumberPlan(r10, r3, r2, r4);
        r2 = DBG;
        if (r2 == 0) goto L_0x00d7;
    L_0x00bb:
        r2 = "SmsNumberUtils";
        r5 = new java.lang.StringBuilder;
        r5.<init>();
        r6 = "International type: ";
        r5 = r5.append(r6);
        r6 = getNumberPlanType(r4);
        r5 = r5.append(r6);
        r5 = r5.toString();
        android.telephony.Rlog.d(r2, r5);
    L_0x00d7:
        r2 = 0;
        switch(r4) {
            case 100: goto L_0x00f8;
            case 101: goto L_0x0163;
            case 102: goto L_0x00ff;
            case 103: goto L_0x0117;
            case 104: goto L_0x013d;
            default: goto L_0x00db;
        };
    L_0x00db:
        r0 = "+";
        r0 = r1.startsWith(r0);
        if (r0 == 0) goto L_0x017e;
    L_0x00e3:
        if (r13 == r8) goto L_0x00e7;
    L_0x00e5:
        if (r13 != r9) goto L_0x017e;
    L_0x00e7:
        r0 = "+011";
        r0 = r1.startsWith(r0);
        if (r0 == 0) goto L_0x0165;
    L_0x00ef:
        r0 = r1.substring(r8);
    L_0x00f3:
        if (r0 != 0) goto L_0x006a;
    L_0x00f5:
        r0 = r1;
        goto L_0x006a;
    L_0x00f8:
        if (r13 != 0) goto L_0x017e;
    L_0x00fa:
        r0 = r1.substring(r8);
        goto L_0x00f3;
    L_0x00ff:
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r2 = "011";
        r0 = r0.append(r2);
        r2 = r1.substring(r8);
        r0 = r0.append(r2);
        r0 = r0.toString();
        goto L_0x00f3;
    L_0x0117:
        if (r13 == 0) goto L_0x011b;
    L_0x0119:
        if (r13 != r9) goto L_0x017e;
    L_0x011b:
        r2 = r3.IDD;
        if (r2 == 0) goto L_0x0125;
    L_0x011f:
        r0 = r3.IDD;
        r0 = r0.length();
    L_0x0125:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "011";
        r2 = r2.append(r3);
        r0 = r1.substring(r0);
        r0 = r2.append(r0);
        r0 = r0.toString();
        goto L_0x00f3;
    L_0x013d:
        r0 = r3.countryCode;
        r3 = inExceptionListForNpCcAreaLocal(r3);
        if (r3 != 0) goto L_0x017e;
    L_0x0145:
        r3 = r1.length();
        r4 = 11;
        if (r3 < r4) goto L_0x017e;
    L_0x014d:
        if (r0 == r8) goto L_0x017e;
    L_0x014f:
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r2 = "011";
        r0 = r0.append(r2);
        r0 = r0.append(r1);
        r0 = r0.toString();
        goto L_0x00f3;
    L_0x0163:
        r0 = r1;
        goto L_0x00f3;
    L_0x0165:
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r2 = "011";
        r0 = r0.append(r2);
        r2 = r1.substring(r8);
        r0 = r0.append(r2);
        r0 = r0.toString();
        goto L_0x00f3;
    L_0x017e:
        r0 = r2;
        goto L_0x00f3;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.SmsNumberUtils.formatNumber(android.content.Context, java.lang.String, java.lang.String, int):java.lang.String");
    }

    /* JADX WARNING: Removed duplicated region for block: B:27:0x006c  */
    private static int[] getAllCountryCodes(android.content.Context r8) {
        /*
        r6 = 0;
        r7 = 0;
        r0 = ALL_COUNTRY_CODES;
        if (r0 == 0) goto L_0x0009;
    L_0x0006:
        r0 = ALL_COUNTRY_CODES;
    L_0x0008:
        return r0;
    L_0x0009:
        r0 = r8.getContentResolver();	 Catch:{ SQLException -> 0x0059, all -> 0x0068 }
        r1 = com.android.internal.telephony.HbpcdLookup.MccLookup.CONTENT_URI;	 Catch:{ SQLException -> 0x0059, all -> 0x0068 }
        r2 = 1;
        r2 = new java.lang.String[r2];	 Catch:{ SQLException -> 0x0059, all -> 0x0068 }
        r3 = 0;
        r4 = "Country_Code";
        r2[r3] = r4;	 Catch:{ SQLException -> 0x0059, all -> 0x0068 }
        r3 = 0;
        r4 = 0;
        r5 = 0;
        r1 = r0.query(r1, r2, r3, r4, r5);	 Catch:{ SQLException -> 0x0059, all -> 0x0068 }
        r0 = r1.getCount();	 Catch:{ SQLException -> 0x0072 }
        if (r0 <= 0) goto L_0x0051;
    L_0x0024:
        r0 = r1.getCount();	 Catch:{ SQLException -> 0x0072 }
        r0 = new int[r0];	 Catch:{ SQLException -> 0x0072 }
        ALL_COUNTRY_CODES = r0;	 Catch:{ SQLException -> 0x0072 }
        r0 = r6;
    L_0x002d:
        r2 = r1.moveToNext();	 Catch:{ SQLException -> 0x0072 }
        if (r2 == 0) goto L_0x0051;
    L_0x0033:
        r2 = 0;
        r2 = r1.getInt(r2);	 Catch:{ SQLException -> 0x0072 }
        r3 = ALL_COUNTRY_CODES;	 Catch:{ SQLException -> 0x0072 }
        r3[r0] = r2;	 Catch:{ SQLException -> 0x0072 }
        r2 = java.lang.String.valueOf(r2);	 Catch:{ SQLException -> 0x0072 }
        r2 = r2.trim();	 Catch:{ SQLException -> 0x0072 }
        r2 = r2.length();	 Catch:{ SQLException -> 0x0072 }
        r3 = MAX_COUNTRY_CODES_LENGTH;	 Catch:{ SQLException -> 0x0072 }
        if (r2 <= r3) goto L_0x004e;
    L_0x004c:
        MAX_COUNTRY_CODES_LENGTH = r2;	 Catch:{ SQLException -> 0x0072 }
    L_0x004e:
        r0 = r0 + 1;
        goto L_0x002d;
    L_0x0051:
        if (r1 == 0) goto L_0x0056;
    L_0x0053:
        r1.close();
    L_0x0056:
        r0 = ALL_COUNTRY_CODES;
        goto L_0x0008;
    L_0x0059:
        r0 = move-exception;
        r1 = r7;
    L_0x005b:
        r2 = "SmsNumberUtils";
        r3 = "Can't access HbpcdLookup database";
        android.telephony.Rlog.e(r2, r3, r0);	 Catch:{ all -> 0x0070 }
        if (r1 == 0) goto L_0x0056;
    L_0x0064:
        r1.close();
        goto L_0x0056;
    L_0x0068:
        r0 = move-exception;
        r1 = r7;
    L_0x006a:
        if (r1 == 0) goto L_0x006f;
    L_0x006c:
        r1.close();
    L_0x006f:
        throw r0;
    L_0x0070:
        r0 = move-exception;
        goto L_0x006a;
    L_0x0072:
        r0 = move-exception;
        goto L_0x005b;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.SmsNumberUtils.getAllCountryCodes(android.content.Context):int[]");
    }

    /* JADX WARNING: Removed duplicated region for block: B:18:0x0064  */
    private static java.util.ArrayList<java.lang.String> getAllIDDs(android.content.Context r9, java.lang.String r10) {
        /*
        r2 = 1;
        r1 = 0;
        r6 = 0;
        r0 = IDDS_MAPS;
        r0 = r0.get(r10);
        r0 = (java.util.ArrayList) r0;
        if (r0 == 0) goto L_0x000e;
    L_0x000d:
        return r0;
    L_0x000e:
        r7 = new java.util.ArrayList;
        r7.<init>();
        if (r10 == 0) goto L_0x0095;
    L_0x0015:
        r3 = "MCC=?";
        r4 = new java.lang.String[r2];
        r4[r1] = r10;
    L_0x001b:
        r0 = r9.getContentResolver();	 Catch:{ SQLException -> 0x004e }
        r1 = com.android.internal.telephony.HbpcdLookup.MccIdd.CONTENT_URI;	 Catch:{ SQLException -> 0x004e }
        r2 = 2;
        r2 = new java.lang.String[r2];	 Catch:{ SQLException -> 0x004e }
        r5 = 0;
        r8 = "IDD";
        r2[r5] = r8;	 Catch:{ SQLException -> 0x004e }
        r5 = 1;
        r8 = "MCC";
        r2[r5] = r8;	 Catch:{ SQLException -> 0x004e }
        r5 = 0;
        r6 = r0.query(r1, r2, r3, r4, r5);	 Catch:{ SQLException -> 0x004e }
        r0 = r6.getCount();	 Catch:{ SQLException -> 0x004e }
        if (r0 <= 0) goto L_0x0088;
    L_0x0039:
        r0 = r6.moveToNext();	 Catch:{ SQLException -> 0x004e }
        if (r0 == 0) goto L_0x0088;
    L_0x003f:
        r0 = 0;
        r0 = r6.getString(r0);	 Catch:{ SQLException -> 0x004e }
        r1 = r7.contains(r0);	 Catch:{ SQLException -> 0x004e }
        if (r1 != 0) goto L_0x0039;
    L_0x004a:
        r7.add(r0);	 Catch:{ SQLException -> 0x004e }
        goto L_0x0039;
    L_0x004e:
        r0 = move-exception;
        r1 = "SmsNumberUtils";
        r2 = "Can't access HbpcdLookup database";
        android.telephony.Rlog.e(r1, r2, r0);	 Catch:{ all -> 0x008e }
        if (r6 == 0) goto L_0x005b;
    L_0x0058:
        r6.close();
    L_0x005b:
        r0 = IDDS_MAPS;
        r0.put(r10, r7);
        r0 = DBG;
        if (r0 == 0) goto L_0x0086;
    L_0x0064:
        r0 = "SmsNumberUtils";
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "MCC = ";
        r1 = r1.append(r2);
        r1 = r1.append(r10);
        r2 = ", all IDDs = ";
        r1 = r1.append(r2);
        r1 = r1.append(r7);
        r1 = r1.toString();
        android.telephony.Rlog.d(r0, r1);
    L_0x0086:
        r0 = r7;
        goto L_0x000d;
    L_0x0088:
        if (r6 == 0) goto L_0x005b;
    L_0x008a:
        r6.close();
        goto L_0x005b;
    L_0x008e:
        r0 = move-exception;
        if (r6 == 0) goto L_0x0094;
    L_0x0091:
        r6.close();
    L_0x0094:
        throw r0;
    L_0x0095:
        r4 = r6;
        r3 = r6;
        goto L_0x001b;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.SmsNumberUtils.getAllIDDs(android.content.Context, java.lang.String):java.util.ArrayList");
    }

    private static int getCountryCode(Context context, String str) {
        if (str.length() >= 10) {
            int[] allCountryCodes = getAllCountryCodes(context);
            if (allCountryCodes != null) {
                int i;
                int[] iArr = new int[MAX_COUNTRY_CODES_LENGTH];
                for (i = 0; i < MAX_COUNTRY_CODES_LENGTH; i++) {
                    iArr[i] = Integer.valueOf(str.substring(0, i + 1)).intValue();
                }
                for (int i2 : allCountryCodes) {
                    for (int i3 = 0; i3 < MAX_COUNTRY_CODES_LENGTH; i3++) {
                        if (i2 == iArr[i3]) {
                            if (DBG) {
                                Rlog.d(TAG, "Country code = " + i2);
                            }
                            return i2;
                        }
                    }
                }
            }
        }
        return -1;
    }

    private static int getNetworkType(PhoneBase phoneBase) {
        int phoneType = TelephonyManager.getDefault().getPhoneType();
        if (phoneType == 1) {
            return 0;
        }
        if (phoneType == 2) {
            return isInternationalRoaming(phoneBase) ? 2 : 1;
        } else {
            if (!DBG) {
                return -1;
            }
            Rlog.w(TAG, "warning! unknown mPhoneType value=" + phoneType);
            return -1;
        }
    }

    private static String getNumberPlanType(int i) {
        "Number Plan type (" + i + "): ";
        return i == 1 ? "NP_NANP_LOCAL" : i == 2 ? "NP_NANP_AREA_LOCAL" : i == 3 ? "NP_NANP_NDD_AREA_LOCAL" : i == 4 ? "NP_NANP_NBPCD_CC_AREA_LOCAL" : i == 5 ? "NP_NANP_LOCALIDD_CC_AREA_LOCAL" : i == 6 ? "NP_NANP_NBPCD_HOMEIDD_CC_AREA_LOCAL" : i == 100 ? "NP_NBPCD_IDD_CC_AREA_LOCAL" : i == NP_HOMEIDD_CC_AREA_LOCAL ? "NP_IDD_CC_AREA_LOCAL" : i == NP_NBPCD_CC_AREA_LOCAL ? "NP_NBPCD_CC_AREA_LOCAL" : i == NP_LOCALIDD_CC_AREA_LOCAL ? "NP_IDD_CC_AREA_LOCAL" : i == NP_CC_AREA_LOCAL ? "NP_CC_AREA_LOCAL" : "Unknown type";
    }

    private static boolean inExceptionListForNpCcAreaLocal(NumberEntry numberEntry) {
        int i = numberEntry.countryCode;
        return numberEntry.number.length() == 12 && (i == 7 || i == 20 || i == 65 || i == 90);
    }

    private static boolean isInternationalRoaming(PhoneBase phoneBase) {
        String systemProperty = phoneBase.getSystemProperty("gsm.operator.iso-country", "");
        String systemProperty2 = phoneBase.getSystemProperty("gsm.sim.operator.iso-country", "");
        boolean z = (TextUtils.isEmpty(systemProperty) || TextUtils.isEmpty(systemProperty2) || systemProperty2.equals(systemProperty)) ? false : true;
        if (z) {
            if ("us".equals(systemProperty2)) {
                return !"vi".equals(systemProperty);
            } else {
                if ("vi".equals(systemProperty2)) {
                    return !"us".equals(systemProperty);
                }
            }
        }
        return z;
    }

    private static boolean isNANP(String str) {
        if (str.length() != 10 && (str.length() != 11 || !str.startsWith(NANP_NDD))) {
            return false;
        }
        if (str.length() == 11) {
            str = str.substring(1);
        }
        return PhoneNumberUtils.isNanp(str);
    }

    private static boolean needToConvert(PhoneBase phoneBase) {
        String[] stringArray = phoneBase.getContext().getResources().getStringArray(17236036);
        if (stringArray == null || stringArray.length <= 0) {
            return false;
        }
        boolean z = false;
        for (int i = 0; i < stringArray.length; i++) {
            if (!TextUtils.isEmpty(stringArray[i])) {
                String[] split = stringArray[i].split(";");
                if (split != null && split.length > 0) {
                    if (split.length == 1) {
                        z = "true".equalsIgnoreCase(split[0]);
                    } else if (split.length == 2 && !TextUtils.isEmpty(split[1]) && compareGid1(phoneBase, split[1])) {
                        return "true".equalsIgnoreCase(split[0]);
                    }
                }
            }
        }
        return z;
    }
}
