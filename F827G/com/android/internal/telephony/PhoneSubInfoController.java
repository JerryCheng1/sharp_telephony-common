package com.android.internal.telephony;

import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.IPhoneSubInfo.Stub;

public class PhoneSubInfoController extends Stub {
    private static final String TAG = "PhoneSubInfoController";
    private Phone[] mPhone;

    public PhoneSubInfoController(Phone[] phoneArr) {
        this.mPhone = phoneArr;
        if (ServiceManager.getService("iphonesubinfo") == null) {
            ServiceManager.addService("iphonesubinfo", this);
        }
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

    private PhoneProxy getPhone(int i) {
        if (i < 0 || i >= TelephonyManager.getDefault().getPhoneCount()) {
            i = 0;
        }
        return (PhoneProxy) this.mPhone[i];
    }

    private PhoneSubInfoProxy getPhoneSubInfoProxy(int i) {
        try {
            return getPhone(SubscriptionManager.getPhoneId(i)).getPhoneSubInfoProxy();
        } catch (NullPointerException e) {
            Rlog.e(TAG, "Exception is :" + e.toString() + " For subId :" + i);
            e.printStackTrace();
            return null;
        }
    }

    public int getBrand() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getBrand();
    }

    public String getCompleteVoiceMailNumber() {
        return getCompleteVoiceMailNumberForSubscriber(getDefaultVoiceSubId());
    }

    public String getCompleteVoiceMailNumberForSubscriber(int i) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(i);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getCompleteVoiceMailNumber();
        }
        Rlog.e(TAG, "getCompleteVoiceMailNumber phoneSubInfoProxy is null for Subscription:" + i);
        return null;
    }

    public String getDeviceId() {
        return getDeviceIdForPhone(0);
    }

    public String getDeviceIdForPhone(int i) {
        PhoneProxy phone = getPhone(i);
        if (phone != null) {
            return phone.getDeviceId();
        }
        Rlog.e(TAG, "getDeviceIdForPhone phone " + i + " is null");
        return null;
    }

    public String getDeviceSvn() {
        return getDeviceSvnUsingSubId(getFirstPhoneSubId());
    }

    public String getDeviceSvnUsingSubId(int i) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(i);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getDeviceSvn();
        }
        Rlog.e(TAG, "getDeviceSvn phoneSubInfoProxy is null");
        return null;
    }

    public String getGroupIdLevel1() {
        return getGroupIdLevel1ForSubscriber(getDefaultSubscription());
    }

    public String getGroupIdLevel1ForSubscriber(int i) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(i);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getGroupIdLevel1();
        }
        Rlog.e(TAG, "getGroupIdLevel1 phoneSubInfoProxy is null for Subscription:" + i);
        return null;
    }

    public String getIccSerialNumber() {
        return getIccSerialNumberForSubscriber(getDefaultSubscription());
    }

    public String getIccSerialNumberForSubscriber(int i) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(i);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getIccSerialNumber();
        }
        Rlog.e(TAG, "getIccSerialNumber phoneSubInfoProxy is null for Subscription:" + i);
        return null;
    }

    public String getIccSimChallengeResponse(int i, int i2, String str) throws RemoteException {
        return getPhoneSubInfoProxy(i).getIccSimChallengeResponse(i, i2, str);
    }

    public String getImeiForSubscriber(int i) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(i);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getImei();
        }
        Rlog.e(TAG, "getDeviceId phoneSubInfoProxy is null for Subscription:" + i);
        return null;
    }

    public String getImsiOnSimLock() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getImsiOnSimLock();
    }

    public String getIsimChallengeResponse(String str) throws RemoteException {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimChallengeResponse(str);
    }

    public String getIsimDomain() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimDomain();
    }

    public String getIsimImpi() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimImpi();
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

    public String getLine1AlphaTag() {
        return getLine1AlphaTagForSubscriber(getDefaultSubscription());
    }

    public String getLine1AlphaTagForSubscriber(int i) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(i);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getLine1AlphaTag();
        }
        Rlog.e(TAG, "getLine1AlphaTag phoneSubInfoProxy is null for Subscription:" + i);
        return null;
    }

    public String getLine1Number() {
        return getLine1NumberForSubscriber(getDefaultSubscription());
    }

    public String getLine1NumberForSubscriber(int i) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(i);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getLine1Number();
        }
        Rlog.e(TAG, "getLine1Number phoneSubInfoProxy is null for Subscription:" + i);
        return null;
    }

    public String getMccMncOnSimLock() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getMccMncOnSimLock();
    }

    public String getMsisdn() {
        return getMsisdnForSubscriber(getDefaultSubscription());
    }

    public String getMsisdnForSubscriber(int i) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(i);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getMsisdn();
        }
        Rlog.e(TAG, "getMsisdn phoneSubInfoProxy is null for Subscription:" + i);
        return null;
    }

    public String getNaiForSubscriber(int i) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(i);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getNai();
        }
        Rlog.e(TAG, "getNai phoneSubInfoProxy is null for Subscription:" + i);
        return null;
    }

    public String getSubscriberId() {
        return getSubscriberIdForSubscriber(getDefaultSubscription());
    }

    public String getSubscriberIdForSubscriber(int i) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(i);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getSubscriberId();
        }
        Rlog.e(TAG, "getSubscriberId phoneSubInfoProxy is null for Subscription:" + i);
        return null;
    }

    public String getVoiceMailAlphaTag() {
        return getVoiceMailAlphaTagForSubscriber(getDefaultVoiceSubId());
    }

    public String getVoiceMailAlphaTagForSubscriber(int i) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(i);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getVoiceMailAlphaTag();
        }
        Rlog.e(TAG, "getVoiceMailAlphaTag phoneSubInfoProxy is null for Subscription:" + i);
        return null;
    }

    public String getVoiceMailNumber() {
        return getVoiceMailNumberForSubscriber(getDefaultVoiceSubId());
    }

    public String getVoiceMailNumberForSubscriber(int i) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(i);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getVoiceMailNumber();
        }
        Rlog.e(TAG, "getVoiceMailNumber phoneSubInfoProxy is null for Subscription:" + i);
        return null;
    }
}
