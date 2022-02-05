package com.android.internal.telephony;

import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.IPhoneSubInfo;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class PhoneSubInfoController extends IPhoneSubInfo.Stub {
    private static final String TAG = "PhoneSubInfoController";
    private Phone[] mPhone;

    /* JADX WARN: Multi-variable type inference failed */
    public PhoneSubInfoController(Phone[] phone) {
        this.mPhone = phone;
        if (ServiceManager.getService("iphonesubinfo") == null) {
            ServiceManager.addService("iphonesubinfo", this);
        }
    }

    public String getDeviceId() {
        return getDeviceIdForPhone(0);
    }

    public String getDeviceIdForPhone(int phoneId) {
        Phone phone = getPhone(phoneId);
        if (phone != null) {
            return phone.getDeviceId();
        }
        Rlog.e(TAG, "getDeviceIdForPhone phone " + phoneId + " is null");
        return null;
    }

    public String getNaiForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getNai();
        }
        Rlog.e(TAG, "getNai phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getImeiForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getImei();
        }
        Rlog.e(TAG, "getDeviceId phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getDeviceSvn() {
        return getDeviceSvnUsingSubId(getFirstPhoneSubId());
    }

    public String getDeviceSvnUsingSubId(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getDeviceSvn();
        }
        Rlog.e(TAG, "getDeviceSvn phoneSubInfoProxy is null");
        return null;
    }

    public String getSubscriberId() {
        return getSubscriberIdForSubscriber(getDefaultSubscription());
    }

    public String getSubscriberIdForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getSubscriberId();
        }
        Rlog.e(TAG, "getSubscriberId phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getIccSerialNumber() {
        return getIccSerialNumberForSubscriber(getDefaultSubscription());
    }

    public String getIccSerialNumberForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getIccSerialNumber();
        }
        Rlog.e(TAG, "getIccSerialNumber phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getLine1Number() {
        return getLine1NumberForSubscriber(getDefaultSubscription());
    }

    public String getLine1NumberForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getLine1Number();
        }
        Rlog.e(TAG, "getLine1Number phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getLine1AlphaTag() {
        return getLine1AlphaTagForSubscriber(getDefaultSubscription());
    }

    public String getLine1AlphaTagForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getLine1AlphaTag();
        }
        Rlog.e(TAG, "getLine1AlphaTag phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getMsisdn() {
        return getMsisdnForSubscriber(getDefaultSubscription());
    }

    public String getMsisdnForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getMsisdn();
        }
        Rlog.e(TAG, "getMsisdn phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getVoiceMailNumber() {
        return getVoiceMailNumberForSubscriber(getDefaultVoiceSubId());
    }

    public String getVoiceMailNumberForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getVoiceMailNumber();
        }
        Rlog.e(TAG, "getVoiceMailNumber phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getCompleteVoiceMailNumber() {
        return getCompleteVoiceMailNumberForSubscriber(getDefaultVoiceSubId());
    }

    public String getCompleteVoiceMailNumberForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getCompleteVoiceMailNumber();
        }
        Rlog.e(TAG, "getCompleteVoiceMailNumber phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getVoiceMailAlphaTag() {
        return getVoiceMailAlphaTagForSubscriber(getDefaultVoiceSubId());
    }

    public String getVoiceMailAlphaTagForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getVoiceMailAlphaTag();
        }
        Rlog.e(TAG, "getVoiceMailAlphaTag phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    private PhoneSubInfoProxy getPhoneSubInfoProxy(int subId) {
        try {
            return getPhone(SubscriptionManager.getPhoneId(subId)).getPhoneSubInfoProxy();
        } catch (NullPointerException e) {
            Rlog.e(TAG, "Exception is :" + e.toString() + " For subId :" + subId);
            e.printStackTrace();
            return null;
        }
    }

    private PhoneProxy getPhone(int phoneId) {
        if (phoneId < 0 || phoneId >= TelephonyManager.getDefault().getPhoneCount()) {
            phoneId = 0;
        }
        return (PhoneProxy) this.mPhone[phoneId];
    }

    private int getDefaultSubscription() {
        return SubscriptionController.getInstance().getDefaultSubId();
    }

    private int getDefaultVoiceSubId() {
        return SubscriptionController.getInstance().getDefaultVoiceSubId();
    }

    private int getFirstPhoneSubId() {
        return SubscriptionController.getInstance().getSubId(0)[0];
    }

    public String getIsimImpi() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimImpi();
    }

    public String getIsimDomain() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimDomain();
    }

    public String[] getIsimImpu() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimImpu();
    }

    public String getIsimIst() throws RemoteException {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimIst();
    }

    public String[] getIsimPcscf() throws RemoteException {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimPcscf();
    }

    public String getIsimChallengeResponse(String nonce) throws RemoteException {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimChallengeResponse(nonce);
    }

    public String getIccSimChallengeResponse(int subId, int appType, String data) throws RemoteException {
        return getPhoneSubInfoProxy(subId).getIccSimChallengeResponse(subId, appType, data);
    }

    public String getGroupIdLevel1() {
        return getGroupIdLevel1ForSubscriber(getDefaultSubscription());
    }

    public String getMccMncOnSimLock() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getMccMncOnSimLock();
    }

    public String getImsiOnSimLock() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getImsiOnSimLock();
    }

    public int getBrand() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getBrand();
    }

    public String getGroupIdLevel1ForSubscriber(int subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getGroupIdLevel1();
        }
        Rlog.e(TAG, "getGroupIdLevel1 phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }
}
