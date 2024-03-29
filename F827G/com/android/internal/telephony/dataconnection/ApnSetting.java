package com.android.internal.telephony.dataconnection;

import android.text.TextUtils;
import com.google.android.mms.pdu.CharacterSets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum ApnProfileType {
        PROFILE_TYPE_APN(0),
        PROFILE_TYPE_CDMA(1),
        PROFILE_TYPE_OMH(2);
        
        int id;

        ApnProfileType(int i) {
            this.id = i;
        }

        public int getid() {
            return this.id;
        }
    }

    public ApnSetting(int id, String numeric, String carrier, String apn, String proxy, String port, String mmsc, String mmsProxy, String mmsPort, String user, String password, int authType, String[] types, String protocol, String roamingProtocol, boolean carrierEnabled, int bearer, int profileId, boolean modemCognitive, int maxConns, int waitTime, int maxConnsTime, int mtu, String mvnoType, String mvnoMatchData) {
        this.id = id;
        this.numeric = numeric;
        this.carrier = carrier;
        this.apn = apn;
        this.proxy = proxy;
        this.port = port;
        this.mmsc = mmsc;
        this.mmsProxy = mmsProxy;
        this.mmsPort = mmsPort;
        this.user = user;
        this.password = password;
        this.authType = authType;
        this.types = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            this.types[i] = types[i].toLowerCase(Locale.ROOT);
        }
        this.protocol = protocol;
        this.roamingProtocol = roamingProtocol;
        this.carrierEnabled = carrierEnabled;
        this.bearer = bearer;
        this.profileId = profileId;
        this.modemCognitive = modemCognitive;
        this.maxConns = maxConns;
        this.waitTime = waitTime;
        this.maxConnsTime = maxConnsTime;
        this.mtu = mtu;
        this.mvnoType = mvnoType;
        this.mvnoMatchData = mvnoMatchData;
    }

    public static ApnSetting fromString(String data) {
        int version;
        int authType;
        String[] typeArray;
        String protocol;
        String roamingProtocol;
        boolean carrierEnabled;
        if (data == null) {
            return null;
        }
        if (data.matches("^\\[ApnSettingV3\\]\\s*.*")) {
            version = 3;
            data = data.replaceFirst(V3_FORMAT_REGEX, "");
        } else if (data.matches("^\\[ApnSettingV2\\]\\s*.*")) {
            version = 2;
            data = data.replaceFirst(V2_FORMAT_REGEX, "");
        } else {
            version = 1;
        }
        String[] a = data.split("\\s*,\\s*");
        if (a.length < 14) {
            return null;
        }
        try {
            authType = Integer.parseInt(a[12]);
        } catch (NumberFormatException e) {
            authType = 0;
        }
        int bearer = 0;
        int profileId = 0;
        boolean modemCognitive = false;
        int maxConns = 0;
        int waitTime = 0;
        int maxConnsTime = 0;
        int mtu = 0;
        String mvnoType = "";
        String mvnoMatchData = "";
        if (version == 1) {
            typeArray = new String[a.length - 13];
            System.arraycopy(a, 13, typeArray, 0, a.length - 13);
            protocol = "IP";
            roamingProtocol = "IP";
            carrierEnabled = true;
            bearer = 0;
        } else if (a.length < 18) {
            return null;
        } else {
            typeArray = a[13].split("\\s*\\|\\s*");
            protocol = a[14];
            roamingProtocol = a[15];
            carrierEnabled = Boolean.parseBoolean(a[16]);
            try {
                bearer = Integer.parseInt(a[17]);
            } catch (NumberFormatException e2) {
            }
            if (a.length > 22) {
                modemCognitive = Boolean.parseBoolean(a[19]);
                try {
                    profileId = Integer.parseInt(a[18]);
                    maxConns = Integer.parseInt(a[20]);
                    waitTime = Integer.parseInt(a[21]);
                    maxConnsTime = Integer.parseInt(a[22]);
                } catch (NumberFormatException e3) {
                }
            }
            if (a.length > 23) {
                try {
                    mtu = Integer.parseInt(a[23]);
                } catch (NumberFormatException e4) {
                }
            }
            if (a.length > 25) {
                mvnoType = a[24];
                mvnoMatchData = a[25];
            }
        }
        return new ApnSetting(-1, a[10] + a[11], a[0], a[1], a[2], a[3], a[7], a[8], a[9], a[4], a[5], authType, typeArray, protocol, roamingProtocol, carrierEnabled, bearer, profileId, modemCognitive, maxConns, waitTime, maxConnsTime, mtu, mvnoType, mvnoMatchData);
    }

    public static List<ApnSetting> arrayFromString(String data) {
        List<ApnSetting> retVal = new ArrayList<>();
        if (!TextUtils.isEmpty(data)) {
            for (String apnString : data.split("\\s*;\\s*")) {
                ApnSetting apn = fromString(apnString);
                if (apn != null) {
                    retVal.add(apn);
                }
            }
        }
        return retVal;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ApnSettingV3] ").append(this.carrier).append(", ").append(this.id).append(", ").append(this.numeric).append(", ").append(this.apn).append(", ").append(this.proxy).append(", ").append(this.mmsc).append(", ").append(this.mmsProxy).append(", ").append(this.mmsPort).append(", ").append(this.port).append(", ").append(this.authType).append(", ");
        for (int i = 0; i < this.types.length; i++) {
            sb.append(this.types[i]);
            if (i < this.types.length - 1) {
                sb.append(" | ");
            }
        }
        sb.append(", ").append(this.protocol);
        sb.append(", ").append(this.roamingProtocol);
        sb.append(", ").append(this.carrierEnabled);
        sb.append(", ").append(this.bearer);
        sb.append(", ").append(this.profileId);
        sb.append(", ").append(this.modemCognitive);
        sb.append(", ").append(this.maxConns);
        sb.append(", ").append(this.waitTime);
        sb.append(", ").append(this.maxConnsTime);
        sb.append(", ").append(this.mtu);
        sb.append(", ").append(this.mvnoType);
        sb.append(", ").append(this.mvnoMatchData);
        return sb.toString();
    }

    public boolean hasMvnoParams() {
        return !TextUtils.isEmpty(this.mvnoType) && !TextUtils.isEmpty(this.mvnoMatchData);
    }

    public boolean canHandleType(String type) {
        if (!this.carrierEnabled) {
            return false;
        }
        String[] arr$ = this.types;
        for (String t : arr$) {
            if (t.equalsIgnoreCase(type) || t.equalsIgnoreCase(CharacterSets.MIMENAME_ANY_CHARSET) || (t.equalsIgnoreCase("default") && type.equalsIgnoreCase("hipri"))) {
                return true;
            }
        }
        return false;
    }

    public boolean equals(Object o) {
        if (!(o instanceof ApnSetting)) {
            return false;
        }
        return toString().equals(o.toString());
    }

    public ApnProfileType getApnProfileType() {
        return ApnProfileType.PROFILE_TYPE_APN;
    }

    public int getProfileId() {
        return this.profileId;
    }

    public String toShortString() {
        return "ApnSetting";
    }

    public String toHash() {
        return toString();
    }
}
