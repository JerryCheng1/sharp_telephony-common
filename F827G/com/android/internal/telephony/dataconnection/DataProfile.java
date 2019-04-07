package com.android.internal.telephony.dataconnection;

import android.os.Parcel;
import android.telephony.ServiceState;

public class DataProfile {
    static final int TYPE_3GPP = 1;
    static final int TYPE_3GPP2 = 2;
    static final int TYPE_COMMON = 0;
    public final String apn;
    public final int authType;
    public final boolean enabled;
    public final int maxConns;
    public final int maxConnsTime;
    public final String password;
    public final int profileId;
    public final String protocol;
    public final int type;
    public final String user;
    public final int waitTime;

    DataProfile(int i, String str, String str2, int i2, String str3, String str4, int i3, int i4, int i5, int i6, boolean z) {
        this.profileId = i;
        this.apn = str;
        this.protocol = str2;
        this.authType = i2;
        this.user = str3;
        this.password = str4;
        this.type = i3;
        this.maxConnsTime = i4;
        this.maxConns = i5;
        this.waitTime = i6;
        this.enabled = z;
    }

    DataProfile(ApnSetting apnSetting, boolean z) {
        int i = apnSetting.profileId;
        String str = apnSetting.apn;
        String str2 = z ? apnSetting.protocol : apnSetting.roamingProtocol;
        int i2 = apnSetting.authType;
        String str3 = apnSetting.user;
        String str4 = apnSetting.password;
        int i3 = apnSetting.bearer == 0 ? 0 : ServiceState.isCdma(apnSetting.bearer) ? 2 : 1;
        this(i, str, str2, i2, str3, str4, i3, apnSetting.maxConnsTime, apnSetting.maxConns, apnSetting.waitTime, apnSetting.carrierEnabled);
    }

    public static Parcel toParcel(Parcel parcel, DataProfile[] dataProfileArr) {
        if (parcel == null) {
            return null;
        }
        parcel.writeInt(dataProfileArr.length);
        for (int i = 0; i < dataProfileArr.length; i++) {
            parcel.writeInt(dataProfileArr[i].profileId);
            parcel.writeString(dataProfileArr[i].apn);
            parcel.writeString(dataProfileArr[i].protocol);
            parcel.writeInt(dataProfileArr[i].authType);
            parcel.writeString(dataProfileArr[i].user);
            parcel.writeString(dataProfileArr[i].password);
            parcel.writeInt(dataProfileArr[i].type);
            parcel.writeInt(dataProfileArr[i].maxConnsTime);
            parcel.writeInt(dataProfileArr[i].maxConns);
            parcel.writeInt(dataProfileArr[i].waitTime);
            parcel.writeInt(dataProfileArr[i].enabled ? 1 : 0);
        }
        return parcel;
    }

    public boolean equals(Object obj) {
        return !(obj instanceof DataProfile) ? false : toString().equals(obj.toString());
    }

    public String toString() {
        return "DataProfile " + this.profileId + "/" + this.apn + "/" + this.protocol + "/" + this.authType + "/" + this.user + "/" + this.password + "/" + this.type + "/" + this.maxConnsTime + "/" + this.maxConns + "/" + this.waitTime + "/" + this.enabled;
    }
}
