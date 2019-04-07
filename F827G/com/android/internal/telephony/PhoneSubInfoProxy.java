package com.android.internal.telephony;

import android.os.RemoteException;
import com.android.internal.telephony.IPhoneSubInfo.Stub;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public class PhoneSubInfoProxy extends Stub {
    private PhoneSubInfo mPhoneSubInfo;

    public PhoneSubInfoProxy(PhoneSubInfo phoneSubInfo) {
        this.mPhoneSubInfo = phoneSubInfo;
    }

    /* Access modifiers changed, original: protected */
    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        this.mPhoneSubInfo.dump(fileDescriptor, printWriter, strArr);
    }

    public int getBrand() {
        return this.mPhoneSubInfo.getBrand();
    }

    public String getCompleteVoiceMailNumber() {
        return this.mPhoneSubInfo.getCompleteVoiceMailNumber();
    }

    public String getCompleteVoiceMailNumberForSubscriber(int i) throws RemoteException {
        return null;
    }

    public String getDeviceId() {
        return this.mPhoneSubInfo.getDeviceId();
    }

    public String getDeviceIdForPhone(int i) throws RemoteException {
        return null;
    }

    public String getDeviceSvn() {
        return this.mPhoneSubInfo.getDeviceSvn();
    }

    public String getDeviceSvnUsingSubId(int i) throws RemoteException {
        return null;
    }

    public String getGroupIdLevel1() {
        return this.mPhoneSubInfo.getGroupIdLevel1();
    }

    public String getGroupIdLevel1ForSubscriber(int i) throws RemoteException {
        return null;
    }

    public String getIccSerialNumber() {
        return this.mPhoneSubInfo.getIccSerialNumber();
    }

    public String getIccSerialNumberForSubscriber(int i) throws RemoteException {
        return null;
    }

    public String getIccSimChallengeResponse(int i, int i2, String str) {
        return this.mPhoneSubInfo.getIccSimChallengeResponse(i, i2, str);
    }

    public String getImei() {
        return this.mPhoneSubInfo.getImei();
    }

    public String getImeiForSubscriber(int i) throws RemoteException {
        return null;
    }

    public String getImsiOnSimLock() {
        return this.mPhoneSubInfo.getImsiOnSimLock();
    }

    public String getIsimChallengeResponse(String str) {
        return this.mPhoneSubInfo.getIsimChallengeResponse(str);
    }

    public String getIsimDomain() {
        return this.mPhoneSubInfo.getIsimDomain();
    }

    public String getIsimImpi() {
        return this.mPhoneSubInfo.getIsimImpi();
    }

    public String[] getIsimImpu() {
        return this.mPhoneSubInfo.getIsimImpu();
    }

    public String getIsimIst() {
        return this.mPhoneSubInfo.getIsimIst();
    }

    public String[] getIsimPcscf() {
        return this.mPhoneSubInfo.getIsimPcscf();
    }

    public String getLine1AlphaTag() {
        return this.mPhoneSubInfo.getLine1AlphaTag();
    }

    public String getLine1AlphaTagForSubscriber(int i) throws RemoteException {
        return null;
    }

    public String getLine1Number() {
        return this.mPhoneSubInfo.getLine1Number();
    }

    public String getLine1NumberForSubscriber(int i) throws RemoteException {
        return null;
    }

    public String getMccMncOnSimLock() {
        return this.mPhoneSubInfo.getMccMncOnSimLock();
    }

    public String getMsisdn() {
        return this.mPhoneSubInfo.getMsisdn();
    }

    public String getMsisdnForSubscriber(int i) throws RemoteException {
        return null;
    }

    public String getNai() {
        return this.mPhoneSubInfo.getNai();
    }

    public String getNaiForSubscriber(int i) throws RemoteException {
        return null;
    }

    public String getSubscriberId() {
        return this.mPhoneSubInfo.getSubscriberId();
    }

    public String getSubscriberIdForSubscriber(int i) throws RemoteException {
        return null;
    }

    public String getVoiceMailAlphaTag() {
        return this.mPhoneSubInfo.getVoiceMailAlphaTag();
    }

    public String getVoiceMailAlphaTagForSubscriber(int i) throws RemoteException {
        return null;
    }

    public String getVoiceMailNumber() {
        return this.mPhoneSubInfo.getVoiceMailNumber();
    }

    public String getVoiceMailNumberForSubscriber(int i) throws RemoteException {
        return null;
    }

    public void setmPhoneSubInfo(PhoneSubInfo phoneSubInfo) {
        this.mPhoneSubInfo = phoneSubInfo;
    }
}
