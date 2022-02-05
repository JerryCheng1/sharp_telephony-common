package com.android.internal.telephony;

import android.os.RemoteException;
import com.android.internal.telephony.IPhoneSubInfo;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class PhoneSubInfoProxy extends IPhoneSubInfo.Stub {
    private PhoneSubInfo mPhoneSubInfo;

    public PhoneSubInfoProxy(PhoneSubInfo phoneSubInfo) {
        this.mPhoneSubInfo = phoneSubInfo;
    }

    public void setmPhoneSubInfo(PhoneSubInfo phoneSubInfo) {
        this.mPhoneSubInfo = phoneSubInfo;
    }

    public String getDeviceId() {
        return this.mPhoneSubInfo.getDeviceId();
    }

    public String getImei() {
        return this.mPhoneSubInfo.getImei();
    }

    public String getNai() {
        return this.mPhoneSubInfo.getNai();
    }

    public String getDeviceSvn() {
        return this.mPhoneSubInfo.getDeviceSvn();
    }

    public String getSubscriberId() {
        return this.mPhoneSubInfo.getSubscriberId();
    }

    public String getMccMncOnSimLock() {
        return this.mPhoneSubInfo.getMccMncOnSimLock();
    }

    public String getImsiOnSimLock() {
        return this.mPhoneSubInfo.getImsiOnSimLock();
    }

    public int getBrand() {
        return this.mPhoneSubInfo.getBrand();
    }

    public String getGroupIdLevel1() {
        return this.mPhoneSubInfo.getGroupIdLevel1();
    }

    public String getIccSerialNumber() {
        return this.mPhoneSubInfo.getIccSerialNumber();
    }

    public String getLine1Number() {
        return this.mPhoneSubInfo.getLine1Number();
    }

    public String getLine1AlphaTag() {
        return this.mPhoneSubInfo.getLine1AlphaTag();
    }

    public String getMsisdn() {
        return this.mPhoneSubInfo.getMsisdn();
    }

    public String getVoiceMailNumber() {
        return this.mPhoneSubInfo.getVoiceMailNumber();
    }

    public String getCompleteVoiceMailNumber() {
        return this.mPhoneSubInfo.getCompleteVoiceMailNumber();
    }

    public String getVoiceMailAlphaTag() {
        return this.mPhoneSubInfo.getVoiceMailAlphaTag();
    }

    public String getIsimImpi() {
        return this.mPhoneSubInfo.getIsimImpi();
    }

    public String getIsimDomain() {
        return this.mPhoneSubInfo.getIsimDomain();
    }

    public String[] getIsimImpu() {
        return this.mPhoneSubInfo.getIsimImpu();
    }

    public String getDeviceIdForPhone(int phoneId) throws RemoteException {
        return null;
    }

    public String getImeiForSubscriber(int subId) throws RemoteException {
        return null;
    }

    public String getDeviceSvnUsingSubId(int subId) throws RemoteException {
        return null;
    }

    public String getNaiForSubscriber(int subId) throws RemoteException {
        return null;
    }

    public String getSubscriberIdForSubscriber(int subId) throws RemoteException {
        return null;
    }

    public String getGroupIdLevel1ForSubscriber(int subId) throws RemoteException {
        return null;
    }

    public String getIccSerialNumberForSubscriber(int subId) throws RemoteException {
        return null;
    }

    public String getLine1NumberForSubscriber(int subId) throws RemoteException {
        return null;
    }

    public String getLine1AlphaTagForSubscriber(int subId) throws RemoteException {
        return null;
    }

    public String getMsisdnForSubscriber(int subId) throws RemoteException {
        return null;
    }

    public String getVoiceMailNumberForSubscriber(int subId) throws RemoteException {
        return null;
    }

    public String getCompleteVoiceMailNumberForSubscriber(int subId) throws RemoteException {
        return null;
    }

    public String getVoiceMailAlphaTagForSubscriber(int subId) throws RemoteException {
        return null;
    }

    public String getIsimIst() {
        return this.mPhoneSubInfo.getIsimIst();
    }

    public String[] getIsimPcscf() {
        return this.mPhoneSubInfo.getIsimPcscf();
    }

    public String getIsimChallengeResponse(String nonce) {
        return this.mPhoneSubInfo.getIsimChallengeResponse(nonce);
    }

    public String getIccSimChallengeResponse(int subId, int appType, String data) {
        return this.mPhoneSubInfo.getIccSimChallengeResponse(subId, appType, data);
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        this.mPhoneSubInfo.dump(fd, pw, args);
    }
}
