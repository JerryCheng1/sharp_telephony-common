package com.android.internal.telephony.dataconnection;

import android.text.TextUtils;
import com.android.internal.telephony.dataconnection.ApnSetting.ApnProfileType;
import java.util.ArrayList;

public class ApnProfileOmh extends ApnSetting {
    private static final int DATA_PROFILE_OMH_PRIORITY_HIGHEST = 0;
    private static final int DATA_PROFILE_OMH_PRIORITY_LOWEST = 255;
    private ApnProfileTypeModem mApnProfileModem;
    private int mPriority = 0;
    private int mServiceTypeMasks = 0;

    enum ApnProfileTypeModem {
        PROFILE_TYPE_UNSPECIFIED(1, "default"),
        PROFILE_TYPE_MMS(2, "mms"),
        PROFILE_TYPE_LBS(32, "supl"),
        PROFILE_TYPE_TETHERED(64, "dun");
        
        int id;
        String serviceType;

        private ApnProfileTypeModem(int i, String str) {
            this.id = i;
            this.serviceType = str;
        }

        public static ApnProfileTypeModem getApnProfileTypeModem(String str) {
            return TextUtils.equals(str, "default") ? PROFILE_TYPE_UNSPECIFIED : TextUtils.equals(str, "mms") ? PROFILE_TYPE_MMS : TextUtils.equals(str, "supl") ? PROFILE_TYPE_LBS : TextUtils.equals(str, "dun") ? PROFILE_TYPE_TETHERED : PROFILE_TYPE_UNSPECIFIED;
        }

        public String getDataServiceType() {
            return this.serviceType;
        }

        public int getid() {
            return this.id;
        }
    }

    public ApnProfileOmh(int i, int i2) {
        super(0, "", null, "", null, null, null, null, null, null, null, 3, new String[0], "IP", "IP", true, 0, i, false, 0, 0, 0, 0, "", "");
        this.mPriority = i2;
    }

    private boolean isValidPriority(int i) {
        return i >= 0 && i <= 255;
    }

    public void addServiceType(ApnProfileTypeModem apnProfileTypeModem) {
        this.mServiceTypeMasks |= apnProfileTypeModem.getid();
        ArrayList arrayList = new ArrayList();
        for (ApnProfileTypeModem apnProfileTypeModem2 : ApnProfileTypeModem.values()) {
            if ((this.mServiceTypeMasks & apnProfileTypeModem2.getid()) != 0) {
                arrayList.add(apnProfileTypeModem2.getDataServiceType());
            }
        }
        this.types = (String[]) arrayList.toArray(new String[0]);
    }

    public boolean canHandleType(String str) {
        return (this.mServiceTypeMasks & ApnProfileTypeModem.getApnProfileTypeModem(str).getid()) != 0;
    }

    public ApnProfileType getApnProfileType() {
        return ApnProfileType.PROFILE_TYPE_OMH;
    }

    public ApnProfileTypeModem getApnProfileTypeModem() {
        return this.mApnProfileModem;
    }

    public int getPriority() {
        return this.mPriority;
    }

    public int getProfileId() {
        return this.profileId;
    }

    public boolean isPriorityHigher(int i) {
        return isValidPriority(i) && this.mPriority < i;
    }

    public boolean isPriorityLower(int i) {
        return isValidPriority(i) && this.mPriority > i;
    }

    public boolean isValidPriority() {
        return isValidPriority(this.mPriority);
    }

    public void setApnProfileTypeModem(ApnProfileTypeModem apnProfileTypeModem) {
        this.mApnProfileModem = apnProfileTypeModem;
    }

    public void setPriority(int i) {
        this.mPriority = i;
    }

    public String toHash() {
        return toString();
    }

    public String toShortString() {
        return "ApnProfile OMH";
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(super.toString()).append(this.profileId).append(", ").append(this.mPriority);
        stringBuilder.append("]");
        return stringBuilder.toString();
    }
}
