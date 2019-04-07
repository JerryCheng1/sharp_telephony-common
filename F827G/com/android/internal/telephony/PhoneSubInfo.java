package com.android.internal.telephony;

import android.content.Context;
import android.os.Binder;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public class PhoneSubInfo {
    private static final String CALL_PRIVILEGED = "android.permission.CALL_PRIVILEGED";
    private static final boolean DBG = true;
    static final String LOG_TAG = "PhoneSubInfo";
    private static final String READ_PHONE_STATE = "android.permission.READ_PHONE_STATE";
    private static final String READ_PRIVILEGED_PHONE_STATE = "android.permission.READ_PRIVILEGED_PHONE_STATE";
    private static final boolean VDBG = false;
    private Context mContext;
    private Phone mPhone;

    public PhoneSubInfo(Phone phone) {
        this.mPhone = phone;
        this.mContext = phone.getContext();
    }

    private void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    private void loge(String str, Throwable th) {
        Rlog.e(LOG_TAG, str, th);
    }

    public void dispose() {
    }

    /* Access modifiers changed, original: protected */
    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.DUMP") != 0) {
            printWriter.println("Permission Denial: can't dump PhoneSubInfo from from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        printWriter.println("Phone Subscriber Info:");
        printWriter.println("  Phone Type = " + this.mPhone.getPhoneName());
        printWriter.println("  Device ID = " + this.mPhone.getDeviceId());
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        try {
            super.finalize();
        } catch (Throwable th) {
            loge("Error while finalizing:", th);
        }
        log("PhoneSubInfo finalized");
    }

    public int getBrand() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getBrand();
    }

    public String getCompleteVoiceMailNumber() {
        this.mContext.enforceCallingOrSelfPermission(CALL_PRIVILEGED, "Requires CALL_PRIVILEGED");
        return this.mPhone.getVoiceMailNumber();
    }

    public String getDeviceId() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getDeviceId();
    }

    public String getDeviceSvn() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getDeviceSvn();
    }

    public String getGroupIdLevel1() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getGroupIdLevel1();
    }

    public String getIccSerialNumber() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getIccSerialNumber();
    }

    public String getIccSimChallengeResponse(int i, int i2, String str) {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        UiccCard uiccCard = this.mPhone.getUiccCard();
        if (uiccCard == null) {
            Rlog.e(LOG_TAG, "getIccSimChallengeResponse() UiccCard is null");
            return null;
        }
        UiccCardApplication applicationByType = uiccCard.getApplicationByType(i2);
        if (applicationByType == null) {
            Rlog.e(LOG_TAG, "getIccSimChallengeResponse() no app with specified type -- " + i2);
            return null;
        }
        Rlog.e(LOG_TAG, "getIccSimChallengeResponse() found app " + applicationByType.getAid() + "specified type -- " + i2);
        int authContext = applicationByType.getAuthContext();
        if (str.length() < 32) {
            Rlog.e(LOG_TAG, "data is too small to use EAP_AKA, using EAP_SIM instead");
            authContext = 128;
        }
        if (authContext != -1) {
            return applicationByType.getIccRecords().getIccSimChallengeResponse(authContext, str);
        }
        Rlog.e(LOG_TAG, "getIccSimChallengeResponse() authContext undefined for app type " + i2);
        return null;
    }

    public String getImei() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getImei();
    }

    public String getImsiOnSimLock() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getImsiOnSimLock();
    }

    public String getIsimChallengeResponse(String str) {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        IsimRecords isimRecords = this.mPhone.getIsimRecords();
        return isimRecords != null ? isimRecords.getIsimChallengeResponse(str) : null;
    }

    public String getIsimDomain() {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        IsimRecords isimRecords = this.mPhone.getIsimRecords();
        return isimRecords != null ? isimRecords.getIsimDomain() : null;
    }

    public String getIsimImpi() {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        IsimRecords isimRecords = this.mPhone.getIsimRecords();
        return isimRecords != null ? isimRecords.getIsimImpi() : null;
    }

    public String[] getIsimImpu() {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        IsimRecords isimRecords = this.mPhone.getIsimRecords();
        return isimRecords != null ? isimRecords.getIsimImpu() : null;
    }

    public String getIsimIst() {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        IsimRecords isimRecords = this.mPhone.getIsimRecords();
        return isimRecords != null ? isimRecords.getIsimIst() : null;
    }

    public String[] getIsimPcscf() {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        IsimRecords isimRecords = this.mPhone.getIsimRecords();
        return isimRecords != null ? isimRecords.getIsimPcscf() : null;
    }

    public String getLine1AlphaTag() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getLine1AlphaTag();
    }

    public String getLine1Number() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getLine1Number();
    }

    public String getMccMncOnSimLock() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getMccMncOnSimLock();
    }

    public String getMsisdn() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getMsisdn();
    }

    public String getNai() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getNai();
    }

    public String getSubscriberId() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getSubscriberId();
    }

    public String getVoiceMailAlphaTag() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getVoiceMailAlphaTag();
    }

    public String getVoiceMailNumber() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return PhoneNumberUtils.extractNetworkPortion(this.mPhone.getVoiceMailNumber());
    }
}
