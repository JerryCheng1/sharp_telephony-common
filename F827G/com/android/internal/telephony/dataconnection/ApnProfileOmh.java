package com.android.internal.telephony.dataconnection;

import android.text.TextUtils;
import com.android.internal.telephony.dataconnection.ApnSetting;
import java.util.ArrayList;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class ApnProfileOmh extends ApnSetting {
    private static final int DATA_PROFILE_OMH_PRIORITY_HIGHEST = 0;
    private static final int DATA_PROFILE_OMH_PRIORITY_LOWEST = 255;
    private ApnProfileTypeModem mApnProfileModem;
    private int mPriority;
    private int mServiceTypeMasks = 0;

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    enum ApnProfileTypeModem {
        PROFILE_TYPE_UNSPECIFIED(1, "default"),
        PROFILE_TYPE_MMS(2, "mms"),
        PROFILE_TYPE_LBS(32, "supl"),
        PROFILE_TYPE_TETHERED(64, "dun");
        
        int id;
        String serviceType;

        ApnProfileTypeModem(int i, String serviceType) {
            this.id = i;
            this.serviceType = serviceType;
        }

        public int getid() {
            return this.id;
        }

        public String getDataServiceType() {
            return this.serviceType;
        }

        public static ApnProfileTypeModem getApnProfileTypeModem(String serviceType) {
            if (TextUtils.equals(serviceType, "default")) {
                return PROFILE_TYPE_UNSPECIFIED;
            }
            if (TextUtils.equals(serviceType, "mms")) {
                return PROFILE_TYPE_MMS;
            }
            if (TextUtils.equals(serviceType, "supl")) {
                return PROFILE_TYPE_LBS;
            }
            if (TextUtils.equals(serviceType, "dun")) {
                return PROFILE_TYPE_TETHERED;
            }
            return PROFILE_TYPE_UNSPECIFIED;
        }
    }

    public ApnProfileOmh(int profileId, int priority) {
        super(0, "", null, "", null, null, null, null, null, null, null, 3, new String[0], "IP", "IP", true, 0, profileId, false, 0, 0, 0, 0, "", "");
        this.mPriority = 0;
        this.mPriority = priority;
    }

    @Override // com.android.internal.telephony.dataconnection.ApnSetting
    public boolean canHandleType(String serviceType) {
        return (this.mServiceTypeMasks & ApnProfileTypeModem.getApnProfileTypeModem(serviceType).getid()) != 0;
    }

    @Override // com.android.internal.telephony.dataconnection.ApnSetting
    public ApnSetting.ApnProfileType getApnProfileType() {
        return ApnSetting.ApnProfileType.PROFILE_TYPE_OMH;
    }

    @Override // com.android.internal.telephony.dataconnection.ApnSetting
    public String toShortString() {
        return "ApnProfile OMH";
    }

    @Override // com.android.internal.telephony.dataconnection.ApnSetting
    public String toHash() {
        return toString();
    }

    @Override // com.android.internal.telephony.dataconnection.ApnSetting
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString()).append(this.profileId).append(", ").append(this.mPriority);
        sb.append("]");
        return sb.toString();
    }

    public void setApnProfileTypeModem(ApnProfileTypeModem modemProfile) {
        this.mApnProfileModem = modemProfile;
    }

    public ApnProfileTypeModem getApnProfileTypeModem() {
        return this.mApnProfileModem;
    }

    public void setPriority(int priority) {
        this.mPriority = priority;
    }

    public boolean isPriorityHigher(int priority) {
        return isValidPriority(priority) && this.mPriority < priority;
    }

    public boolean isPriorityLower(int priority) {
        return isValidPriority(priority) && this.mPriority > priority;
    }

    public boolean isValidPriority() {
        return isValidPriority(this.mPriority);
    }

    private boolean isValidPriority(int priority) {
        return priority >= 0 && priority <= 255;
    }

    @Override // com.android.internal.telephony.dataconnection.ApnSetting
    public int getProfileId() {
        return this.profileId;
    }

    public int getPriority() {
        return this.mPriority;
    }

    public void addServiceType(ApnProfileTypeModem modemProfile) {
        this.mServiceTypeMasks |= modemProfile.getid();
        ArrayList<String> serviceTypes = new ArrayList<>();
        ApnProfileTypeModem[] arr$ = ApnProfileTypeModem.values();
        for (ApnProfileTypeModem apt : arr$) {
            if ((this.mServiceTypeMasks & apt.getid()) != 0) {
                serviceTypes.add(apt.getDataServiceType());
            }
        }
        this.types = (String[]) serviceTypes.toArray(new String[0]);
    }
}
