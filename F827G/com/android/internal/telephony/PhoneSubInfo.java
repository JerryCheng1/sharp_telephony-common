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

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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

    public void dispose() {
    }

    protected void finalize() {
        try {
            super.finalize();
        } catch (Throwable throwable) {
            loge("Error while finalizing:", throwable);
        }
        log("PhoneSubInfo finalized");
    }

    public String getDeviceId() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getDeviceId();
    }

    public String getImei() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getImei();
    }

    public String getNai() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getNai();
    }

    public String getDeviceSvn() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getDeviceSvn();
    }

    public String getSubscriberId() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getSubscriberId();
    }

    public String getMccMncOnSimLock() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getMccMncOnSimLock();
    }

    public String getImsiOnSimLock() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getImsiOnSimLock();
    }

    public int getBrand() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getBrand();
    }

    public String getGroupIdLevel1() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getGroupIdLevel1();
    }

    public String getIccSerialNumber() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getIccSerialNumber();
    }

    public String getLine1Number() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getLine1Number();
    }

    public String getLine1AlphaTag() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getLine1AlphaTag();
    }

    public String getMsisdn() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getMsisdn();
    }

    public String getVoiceMailNumber() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return PhoneNumberUtils.extractNetworkPortion(this.mPhone.getVoiceMailNumber());
    }

    public String getCompleteVoiceMailNumber() {
        this.mContext.enforceCallingOrSelfPermission(CALL_PRIVILEGED, "Requires CALL_PRIVILEGED");
        return this.mPhone.getVoiceMailNumber();
    }

    public String getVoiceMailAlphaTag() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getVoiceMailAlphaTag();
    }

    public String getIsimImpi() {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        IsimRecords isim = this.mPhone.getIsimRecords();
        if (isim != null) {
            return isim.getIsimImpi();
        }
        return null;
    }

    public String getIsimDomain() {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        IsimRecords isim = this.mPhone.getIsimRecords();
        if (isim != null) {
            return isim.getIsimDomain();
        }
        return null;
    }

    public String[] getIsimImpu() {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        IsimRecords isim = this.mPhone.getIsimRecords();
        if (isim != null) {
            return isim.getIsimImpu();
        }
        return null;
    }

    public String getIsimIst() {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        IsimRecords isim = this.mPhone.getIsimRecords();
        if (isim != null) {
            return isim.getIsimIst();
        }
        return null;
    }

    public String[] getIsimPcscf() {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        IsimRecords isim = this.mPhone.getIsimRecords();
        if (isim != null) {
            return isim.getIsimPcscf();
        }
        return null;
    }

    public String getIsimChallengeResponse(String nonce) {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        IsimRecords isim = this.mPhone.getIsimRecords();
        if (isim != null) {
            return isim.getIsimChallengeResponse(nonce);
        }
        return null;
    }

    public String getIccSimChallengeResponse(int subId, int appType, String data) {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        UiccCard uiccCard = this.mPhone.getUiccCard();
        if (uiccCard == null) {
            Rlog.e(LOG_TAG, "getIccSimChallengeResponse() UiccCard is null");
            return null;
        }
        UiccCardApplication uiccApp = uiccCard.getApplicationByType(appType);
        if (uiccApp == null) {
            Rlog.e(LOG_TAG, "getIccSimChallengeResponse() no app with specified type -- " + appType);
            return null;
        }
        Rlog.e(LOG_TAG, "getIccSimChallengeResponse() found app " + uiccApp.getAid() + "specified type -- " + appType);
        int authContext = uiccApp.getAuthContext();
        if (data.length() < 32) {
            Rlog.e(LOG_TAG, "data is too small to use EAP_AKA, using EAP_SIM instead");
            authContext = 128;
        }
        if (authContext != -1) {
            return uiccApp.getIccRecords().getIccSimChallengeResponse(authContext, data);
        }
        Rlog.e(LOG_TAG, "getIccSimChallengeResponse() authContext undefined for app type " + appType);
        return null;
    }

    private void log(String s) {
        Rlog.d(LOG_TAG, s);
    }

    private void loge(String s, Throwable e) {
        Rlog.e(LOG_TAG, s, e);
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.DUMP") != 0) {
            pw.println("Permission Denial: can't dump PhoneSubInfo from from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        pw.println("Phone Subscriber Info:");
        pw.println("  Phone Type = " + this.mPhone.getPhoneName());
        pw.println("  Device ID = " + this.mPhone.getDeviceId());
    }
}
