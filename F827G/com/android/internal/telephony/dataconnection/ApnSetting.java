package com.android.internal.telephony.dataconnection;

import android.text.TextUtils;
import com.google.android.mms.pdu.CharacterSets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ApnSetting {
    static final String V2_FORMAT_REGEX = "^\\[ApnSettingV2\\]\\s*";
    static final String V3_FORMAT_REGEX = "^\\[ApnSettingV3\\]\\s*";
    public final String apn;
    public final int authType;
    public final int bearer;
    public final String carrier;
    public final boolean carrierEnabled;
    public final int id;
    public final int maxConns;
    public final int maxConnsTime;
    public final String mmsPort;
    public final String mmsProxy;
    public final String mmsc;
    public final boolean modemCognitive;
    public final int mtu;
    public final String mvnoMatchData;
    public final String mvnoType;
    public final String numeric;
    public String[] oemDnses = null;
    public final String password;
    public final String port;
    public int profileId;
    public final String protocol;
    public final String proxy;
    public final String roamingProtocol;
    public String[] types;
    public final String user;
    public final int waitTime;

    public enum ApnProfileType {
        PROFILE_TYPE_APN(0),
        PROFILE_TYPE_CDMA(1),
        PROFILE_TYPE_OMH(2);
        
        int id;

        private ApnProfileType(int i) {
            this.id = i;
        }

        public int getid() {
            return this.id;
        }
    }

    public ApnSetting(int i, String str, String str2, String str3, String str4, String str5, String str6, String str7, String str8, String str9, String str10, int i2, String[] strArr, String str11, String str12, boolean z, int i3, int i4, boolean z2, int i5, int i6, int i7, int i8, String str13, String str14) {
        this.id = i;
        this.numeric = str;
        this.carrier = str2;
        this.apn = str3;
        this.proxy = str4;
        this.port = str5;
        this.mmsc = str6;
        this.mmsProxy = str7;
        this.mmsPort = str8;
        this.user = str9;
        this.password = str10;
        this.authType = i2;
        this.types = new String[strArr.length];
        for (int i9 = 0; i9 < strArr.length; i9++) {
            this.types[i9] = strArr[i9].toLowerCase(Locale.ROOT);
        }
        this.protocol = str11;
        this.roamingProtocol = str12;
        this.carrierEnabled = z;
        this.bearer = i3;
        this.profileId = i4;
        this.modemCognitive = z2;
        this.maxConns = i5;
        this.waitTime = i6;
        this.maxConnsTime = i7;
        this.mtu = i8;
        this.mvnoType = str13;
        this.mvnoMatchData = str14;
    }

    public static List<ApnSetting> arrayFromString(String str) {
        ArrayList arrayList = new ArrayList();
        if (!TextUtils.isEmpty(str)) {
            for (String fromString : str.split("\\s*;\\s*")) {
                ApnSetting fromString2 = fromString(fromString);
                if (fromString2 != null) {
                    arrayList.add(fromString2);
                }
            }
        }
        return arrayList;
    }

    /* JADX WARNING: Removed duplicated region for block: B:64:0x0168  */
    /* JADX WARNING: Removed duplicated region for block: B:44:0x011d  */
    /* JADX WARNING: Removed duplicated region for block: B:63:0x0158  */
    /* JADX WARNING: Removed duplicated region for block: B:49:0x012a  */
    /* JADX WARNING: Removed duplicated region for block: B:44:0x011d  */
    /* JADX WARNING: Removed duplicated region for block: B:64:0x0168  */
    /* JADX WARNING: Removed duplicated region for block: B:49:0x012a  */
    /* JADX WARNING: Removed duplicated region for block: B:63:0x0158  */
    /* JADX WARNING: Removed duplicated region for block: B:64:0x0168  */
    /* JADX WARNING: Removed duplicated region for block: B:44:0x011d  */
    /* JADX WARNING: Removed duplicated region for block: B:63:0x0158  */
    /* JADX WARNING: Removed duplicated region for block: B:49:0x012a  */
    /* JADX WARNING: Removed duplicated region for block: B:44:0x011d  */
    /* JADX WARNING: Removed duplicated region for block: B:64:0x0168  */
    /* JADX WARNING: Removed duplicated region for block: B:49:0x012a  */
    /* JADX WARNING: Removed duplicated region for block: B:63:0x0158  */
    public static com.android.internal.telephony.dataconnection.ApnSetting fromString(java.lang.String r28) {
        /*
        if (r28 != 0) goto L_0x0004;
    L_0x0002:
        r1 = 0;
    L_0x0003:
        return r1;
    L_0x0004:
        r1 = "^\\[ApnSettingV3\\]\\s*.*";
        r0 = r28;
        r1 = r0.matches(r1);
        if (r1 == 0) goto L_0x0028;
    L_0x000e:
        r1 = 3;
        r2 = "^\\[ApnSettingV3\\]\\s*";
        r3 = "";
        r0 = r28;
        r28 = r0.replaceFirst(r2, r3);
    L_0x0019:
        r2 = "\\s*,\\s*";
        r0 = r28;
        r12 = r0.split(r2);
        r2 = r12.length;
        r3 = 14;
        if (r2 >= r3) goto L_0x0040;
    L_0x0026:
        r1 = 0;
        goto L_0x0003;
    L_0x0028:
        r1 = "^\\[ApnSettingV2\\]\\s*.*";
        r0 = r28;
        r1 = r0.matches(r1);
        if (r1 == 0) goto L_0x003e;
    L_0x0032:
        r1 = 2;
        r2 = "^\\[ApnSettingV2\\]\\s*";
        r3 = "";
        r0 = r28;
        r28 = r0.replaceFirst(r2, r3);
        goto L_0x0019;
    L_0x003e:
        r1 = 1;
        goto L_0x0019;
    L_0x0040:
        r2 = 12;
        r2 = r12[r2];	 Catch:{ NumberFormatException -> 0x00bb }
        r13 = java.lang.Integer.parseInt(r2);	 Catch:{ NumberFormatException -> 0x00bb }
    L_0x0048:
        r2 = 0;
        r7 = 0;
        r11 = 0;
        r19 = 0;
        r3 = 0;
        r20 = 0;
        r6 = 0;
        r10 = 0;
        r21 = 0;
        r5 = 0;
        r9 = 0;
        r22 = 0;
        r4 = 0;
        r23 = 0;
        r8 = 0;
        r24 = 0;
        r25 = "";
        r26 = "";
        r14 = 1;
        if (r1 != r14) goto L_0x00be;
    L_0x0065:
        r1 = r12.length;
        r1 = r1 + -13;
        r14 = new java.lang.String[r1];
        r1 = 13;
        r2 = 0;
        r3 = r12.length;
        r3 = r3 + -13;
        java.lang.System.arraycopy(r12, r1, r14, r2, r3);
        r15 = "IP";
        r16 = "IP";
        r17 = 1;
        r1 = 0;
        r18 = r1;
    L_0x007c:
        r1 = new com.android.internal.telephony.dataconnection.ApnSetting;
        r2 = -1;
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = 10;
        r4 = r12[r4];
        r3 = r3.append(r4);
        r4 = 11;
        r4 = r12[r4];
        r3 = r3.append(r4);
        r3 = r3.toString();
        r4 = 0;
        r4 = r12[r4];
        r5 = 1;
        r5 = r12[r5];
        r6 = 2;
        r6 = r12[r6];
        r7 = 3;
        r7 = r12[r7];
        r8 = 7;
        r8 = r12[r8];
        r9 = 8;
        r9 = r12[r9];
        r10 = 9;
        r10 = r12[r10];
        r11 = 4;
        r11 = r12[r11];
        r27 = 5;
        r12 = r12[r27];
        r1.<init>(r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, r12, r13, r14, r15, r16, r17, r18, r19, r20, r21, r22, r23, r24, r25, r26);
        goto L_0x0003;
    L_0x00bb:
        r2 = move-exception;
        r13 = 0;
        goto L_0x0048;
    L_0x00be:
        r1 = r12.length;
        r14 = 18;
        if (r1 >= r14) goto L_0x00c6;
    L_0x00c3:
        r1 = 0;
        goto L_0x0003;
    L_0x00c6:
        r1 = 13;
        r1 = r12[r1];
        r14 = "\\s*\\|\\s*";
        r14 = r1.split(r14);
        r1 = 14;
        r15 = r12[r1];
        r1 = 15;
        r16 = r12[r1];
        r1 = 16;
        r1 = r12[r1];
        r17 = java.lang.Boolean.parseBoolean(r1);
        r1 = 17;
        r1 = r12[r1];	 Catch:{ NumberFormatException -> 0x014b }
        r1 = java.lang.Integer.parseInt(r1);	 Catch:{ NumberFormatException -> 0x014b }
    L_0x00e8:
        r2 = r12.length;
        r18 = 22;
        r0 = r18;
        if (r2 <= r0) goto L_0x016a;
    L_0x00ef:
        r2 = 19;
        r2 = r12[r2];
        r2 = java.lang.Boolean.parseBoolean(r2);
        r3 = 18;
        r3 = r12[r3];	 Catch:{ NumberFormatException -> 0x0145 }
        r7 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x0145 }
        r3 = 20;
        r3 = r12[r3];	 Catch:{ NumberFormatException -> 0x014e }
        r6 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x014e }
        r3 = 21;
        r3 = r12[r3];	 Catch:{ NumberFormatException -> 0x0152 }
        r5 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x0152 }
        r3 = 22;
        r3 = r12[r3];	 Catch:{ NumberFormatException -> 0x0155 }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x0155 }
        r4 = r3;
    L_0x0118:
        r3 = r12.length;
        r9 = 23;
        if (r3 <= r9) goto L_0x0168;
    L_0x011d:
        r3 = 23;
        r3 = r12[r3];	 Catch:{ NumberFormatException -> 0x0142 }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x0142 }
    L_0x0125:
        r8 = r12.length;
        r9 = 25;
        if (r8 <= r9) goto L_0x0158;
    L_0x012a:
        r8 = 24;
        r25 = r12[r8];
        r8 = 25;
        r26 = r12[r8];
        r20 = r2;
        r18 = r1;
        r19 = r7;
        r21 = r6;
        r22 = r5;
        r23 = r4;
        r24 = r3;
        goto L_0x007c;
    L_0x0142:
        r3 = move-exception;
        r3 = r8;
        goto L_0x0125;
    L_0x0145:
        r3 = move-exception;
        r3 = r9;
        r6 = r10;
        r7 = r11;
    L_0x0149:
        r5 = r3;
        goto L_0x0118;
    L_0x014b:
        r1 = move-exception;
        r1 = r2;
        goto L_0x00e8;
    L_0x014e:
        r3 = move-exception;
        r3 = r9;
        r6 = r10;
        goto L_0x0149;
    L_0x0152:
        r3 = move-exception;
        r3 = r9;
        goto L_0x0149;
    L_0x0155:
        r3 = move-exception;
        r3 = r5;
        goto L_0x0149;
    L_0x0158:
        r20 = r2;
        r18 = r1;
        r19 = r7;
        r21 = r6;
        r22 = r5;
        r23 = r4;
        r24 = r3;
        goto L_0x007c;
    L_0x0168:
        r3 = r8;
        goto L_0x0125;
    L_0x016a:
        r2 = r3;
        goto L_0x0118;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.dataconnection.ApnSetting.fromString(java.lang.String):com.android.internal.telephony.dataconnection.ApnSetting");
    }

    public boolean canHandleType(String str) {
        if (!this.carrierEnabled) {
            return false;
        }
        for (String str2 : this.types) {
            if (str2.equalsIgnoreCase(str) || str2.equalsIgnoreCase(CharacterSets.MIMENAME_ANY_CHARSET) || (str2.equalsIgnoreCase("default") && str.equalsIgnoreCase("hipri"))) {
                return true;
            }
        }
        return false;
    }

    public boolean equals(Object obj) {
        return !(obj instanceof ApnSetting) ? false : toString().equals(obj.toString());
    }

    public ApnProfileType getApnProfileType() {
        return ApnProfileType.PROFILE_TYPE_APN;
    }

    public int getProfileId() {
        return this.profileId;
    }

    public boolean hasMvnoParams() {
        return (TextUtils.isEmpty(this.mvnoType) || TextUtils.isEmpty(this.mvnoMatchData)) ? false : true;
    }

    public String toHash() {
        return toString();
    }

    public String toShortString() {
        return "ApnSetting";
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[ApnSettingV3] ").append(this.carrier).append(", ").append(this.id).append(", ").append(this.numeric).append(", ").append(this.apn).append(", ").append(this.proxy).append(", ").append(this.mmsc).append(", ").append(this.mmsProxy).append(", ").append(this.mmsPort).append(", ").append(this.port).append(", ").append(this.authType).append(", ");
        for (int i = 0; i < this.types.length; i++) {
            stringBuilder.append(this.types[i]);
            if (i < this.types.length - 1) {
                stringBuilder.append(" | ");
            }
        }
        stringBuilder.append(", ").append(this.protocol);
        stringBuilder.append(", ").append(this.roamingProtocol);
        stringBuilder.append(", ").append(this.carrierEnabled);
        stringBuilder.append(", ").append(this.bearer);
        stringBuilder.append(", ").append(this.profileId);
        stringBuilder.append(", ").append(this.modemCognitive);
        stringBuilder.append(", ").append(this.maxConns);
        stringBuilder.append(", ").append(this.waitTime);
        stringBuilder.append(", ").append(this.maxConnsTime);
        stringBuilder.append(", ").append(this.mtu);
        stringBuilder.append(", ").append(this.mvnoType);
        stringBuilder.append(", ").append(this.mvnoMatchData);
        return stringBuilder.toString();
    }
}
