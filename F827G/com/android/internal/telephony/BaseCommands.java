package com.android.internal.telephony;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.dataconnection.DataProfile;

public abstract class BaseCommands implements CommandsInterface {
    protected RegistrantList mAvailRegistrants = new RegistrantList();
    protected RegistrantList mCallStateRegistrants = new RegistrantList();
    protected RegistrantList mCallWaitingInfoRegistrants = new RegistrantList();
    protected Registrant mCatCallSetUpRegistrant;
    protected Registrant mCatCcAlphaRegistrant;
    protected Registrant mCatEventRegistrant;
    protected Registrant mCatProCmdRegistrant;
    protected Registrant mCatSessionEndRegistrant;
    protected RegistrantList mCdmaPrlChangedRegistrants = new RegistrantList();
    protected Registrant mCdmaSmsRegistrant;
    protected int mCdmaSubscription;
    protected RegistrantList mCdmaSubscriptionChangedRegistrants = new RegistrantList();
    protected Context mContext;
    protected RegistrantList mDataNetworkStateRegistrants = new RegistrantList();
    protected RegistrantList mDisplayInfoRegistrants = new RegistrantList();
    protected Registrant mEmergencyCallbackModeRegistrant;
    protected RegistrantList mExitEmergencyCallbackModeRegistrants = new RegistrantList();
    protected Registrant mGsmBroadcastSmsRegistrant;
    protected Registrant mGsmSmsRegistrant;
    protected RegistrantList mHardwareConfigChangeRegistrants = new RegistrantList();
    protected RegistrantList mIccRefreshRegistrants = new RegistrantList();
    protected Registrant mIccSmsFullRegistrant;
    protected RegistrantList mIccStatusChangedRegistrants = new RegistrantList();
    protected RegistrantList mImsNetworkStateChangedRegistrants = new RegistrantList();
    protected RegistrantList mLineControlInfoRegistrants = new RegistrantList();
    protected Registrant mLteBandInfoRegistrant;
    protected RegistrantList mModemCapRegistrants = new RegistrantList();
    protected Registrant mNITZTimeRegistrant;
    protected RegistrantList mNotAvailRegistrants = new RegistrantList();
    protected RegistrantList mNumberInfoRegistrants = new RegistrantList();
    protected Registrant mOemSignalStrengthRegistrant;
    protected RegistrantList mOffOrNotAvailRegistrants = new RegistrantList();
    protected RegistrantList mOnRegistrants = new RegistrantList();
    protected RegistrantList mOtaProvisionRegistrants = new RegistrantList();
    protected int mPhoneType;
    protected int mPreferredNetworkType;
    protected RegistrantList mRadioStateChangedRegistrants = new RegistrantList();
    protected RegistrantList mRedirNumInfoRegistrants = new RegistrantList();
    protected RegistrantList mResendIncallMuteRegistrants = new RegistrantList();
    protected Registrant mRestrictedStateRegistrant;
    protected RegistrantList mRilCellInfoListRegistrants = new RegistrantList();
    protected RegistrantList mRilConnectedRegistrants = new RegistrantList();
    protected int mRilVersion = -1;
    protected Registrant mRingRegistrant;
    protected RegistrantList mRingbackToneRegistrants = new RegistrantList();
    protected RegistrantList mSignalInfoRegistrants = new RegistrantList();
    protected Registrant mSignalStrengthRegistrant;
    protected RegistrantList mSimRefreshRegistrants = new RegistrantList();
    protected Registrant mSmsOnSimRegistrant;
    protected Registrant mSmsStatusRegistrant;
    protected Registrant mSpeechCodecRegistrant;
    protected RegistrantList mSrvccStateRegistrants = new RegistrantList();
    protected Registrant mSsRegistrant;
    protected Registrant mSsnRegistrant;
    protected RadioState mState = RadioState.RADIO_UNAVAILABLE;
    protected Object mStateMonitor = new Object();
    protected RegistrantList mSubscriptionStatusRegistrants = new RegistrantList();
    protected RegistrantList mT53AudCntrlInfoRegistrants = new RegistrantList();
    protected RegistrantList mT53ClirInfoRegistrants = new RegistrantList();
    protected Registrant mUSSDRegistrant;
    protected Registrant mUnsolOemHookRawRegistrant;
    protected RegistrantList mVoiceNetworkStateRegistrants = new RegistrantList();
    protected RegistrantList mVoicePrivacyOffRegistrants = new RegistrantList();
    protected RegistrantList mVoicePrivacyOnRegistrants = new RegistrantList();
    protected RegistrantList mVoiceRadioTechChangedRegistrants = new RegistrantList();
    protected RegistrantList mWwanIwlanCoexistenceRegistrants = new RegistrantList();

    public BaseCommands(Context context) {
        this.mContext = context;
    }

    public void getAtr(Message message) {
    }

    public void getBandPref(Message message) {
    }

    public void getDataCallProfile(int i, Message message) {
    }

    public int getLteOnCdmaMode() {
        return TelephonyManager.getLteOnCdmaModeStatic();
    }

    public void getModemCapability(Message message) {
    }

    public void getPreferredNetworkTypeWithOptimizeSetting(Message message) {
    }

    public RadioState getRadioState() {
        return this.mState;
    }

    public int getRilVersion() {
        return this.mRilVersion;
    }

    public void getSimLock(Message message) {
    }

    public void iccCloseChannel(int i, Message message) {
    }

    public void iccCloseLogicalChannel(int i, Message message) {
    }

    public void iccExchangeAPDU(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message) {
    }

    public void iccOpenChannel(String str, Message message) {
    }

    public void iccOpenLogicalChannel(String str, Message message) {
    }

    public void iccTransmitApduBasicChannel(int i, int i2, int i3, int i4, int i5, String str, Message message) {
    }

    public void iccTransmitApduLogicalChannel(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message) {
    }

    public boolean isRunning() {
        return false;
    }

    /* Access modifiers changed, original: protected */
    public void onRadioAvailable() {
    }

    public void registerFoT53ClirlInfo(Handler handler, int i, Object obj) {
        this.mT53ClirInfoRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForAvailable(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        synchronized (this.mStateMonitor) {
            this.mAvailRegistrants.add(registrant);
            if (this.mState.isAvailable()) {
                registrant.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    public void registerForCallStateChanged(Handler handler, int i, Object obj) {
        this.mCallStateRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForCallWaitingInfo(Handler handler, int i, Object obj) {
        this.mCallWaitingInfoRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForCdmaOtaProvision(Handler handler, int i, Object obj) {
        this.mOtaProvisionRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForCdmaPrlChanged(Handler handler, int i, Object obj) {
        this.mCdmaPrlChangedRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForCdmaSubscriptionChanged(Handler handler, int i, Object obj) {
        this.mCdmaSubscriptionChangedRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForCellInfoList(Handler handler, int i, Object obj) {
        this.mRilCellInfoListRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForDataNetworkStateChanged(Handler handler, int i, Object obj) {
        this.mDataNetworkStateRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForDisplayInfo(Handler handler, int i, Object obj) {
        this.mDisplayInfoRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForExitEmergencyCallbackMode(Handler handler, int i, Object obj) {
        this.mExitEmergencyCallbackModeRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForHardwareConfigChanged(Handler handler, int i, Object obj) {
        this.mHardwareConfigChangeRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForIccRefresh(Handler handler, int i, Object obj) {
        this.mIccRefreshRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForIccStatusChanged(Handler handler, int i, Object obj) {
        this.mIccStatusChangedRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForImsNetworkStateChanged(Handler handler, int i, Object obj) {
        this.mImsNetworkStateChangedRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForInCallVoicePrivacyOff(Handler handler, int i, Object obj) {
        this.mVoicePrivacyOffRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForInCallVoicePrivacyOn(Handler handler, int i, Object obj) {
        this.mVoicePrivacyOnRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForLineControlInfo(Handler handler, int i, Object obj) {
        this.mLineControlInfoRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForModemCapEvent(Handler handler, int i, Object obj) {
        this.mModemCapRegistrants.addUnique(handler, i, obj);
    }

    public void registerForNotAvailable(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        synchronized (this.mStateMonitor) {
            this.mNotAvailRegistrants.add(registrant);
            if (!this.mState.isAvailable()) {
                registrant.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    public void registerForNumberInfo(Handler handler, int i, Object obj) {
        this.mNumberInfoRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForOffOrNotAvailable(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        synchronized (this.mStateMonitor) {
            this.mOffOrNotAvailRegistrants.add(registrant);
            if (this.mState == RadioState.RADIO_OFF || !this.mState.isAvailable()) {
                registrant.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    public void registerForOn(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        synchronized (this.mStateMonitor) {
            this.mOnRegistrants.add(registrant);
            if (this.mState.isOn()) {
                registrant.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    public void registerForRadioStateChanged(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        synchronized (this.mStateMonitor) {
            this.mRadioStateChangedRegistrants.add(registrant);
            registrant.notifyRegistrant();
        }
    }

    public void registerForRedirectedNumberInfo(Handler handler, int i, Object obj) {
        this.mRedirNumInfoRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForResendIncallMute(Handler handler, int i, Object obj) {
        this.mResendIncallMuteRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForRilConnected(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        this.mRilConnectedRegistrants.add(registrant);
        if (this.mRilVersion != -1) {
            registrant.notifyRegistrant(new AsyncResult(null, new Integer(this.mRilVersion), null));
        }
    }

    public void registerForRingbackTone(Handler handler, int i, Object obj) {
        this.mRingbackToneRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForSignalInfo(Handler handler, int i, Object obj) {
        this.mSignalInfoRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForSimRefreshEvent(Handler handler, int i, Object obj) {
        this.mSimRefreshRegistrants.addUnique(handler, i, obj);
    }

    public void registerForSrvccStateChanged(Handler handler, int i, Object obj) {
        this.mSrvccStateRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForSubscriptionStatusChanged(Handler handler, int i, Object obj) {
        this.mSubscriptionStatusRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForT53AudioControlInfo(Handler handler, int i, Object obj) {
        this.mT53AudCntrlInfoRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForVoiceNetworkStateChanged(Handler handler, int i, Object obj) {
        this.mVoiceNetworkStateRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForVoiceRadioTechChanged(Handler handler, int i, Object obj) {
        this.mVoiceRadioTechChangedRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForWwanIwlanCoexistence(Handler handler, int i, Object obj) {
        this.mWwanIwlanCoexistenceRegistrants.addUnique(handler, i, obj);
    }

    public void reportDecryptStatus(boolean z, Message message) {
    }

    public void requestShutdown(Message message) {
    }

    public void sendSMSExpectMore(String str, String str2, Message message) {
    }

    public void setBandPref(long j, int i, Message message) {
    }

    public void setDataAllowed(boolean z, Message message) {
    }

    public void setDataProfile(DataProfile[] dataProfileArr, Message message) {
    }

    public void setEmergencyCallbackMode(Handler handler, int i, Object obj) {
        this.mEmergencyCallbackModeRegistrant = new Registrant(handler, i, obj);
    }

    public void setLimitationByChameleon(boolean z, Message message) {
    }

    public void setLocalCallHold(int i) {
    }

    public void setModemSettingsByChameleon(int i, Message message) {
    }

    public void setOnCallRing(Handler handler, int i, Object obj) {
        this.mRingRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnCatCallSetUp(Handler handler, int i, Object obj) {
        this.mCatCallSetUpRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnCatCcAlphaNotify(Handler handler, int i, Object obj) {
        this.mCatCcAlphaRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnCatEvent(Handler handler, int i, Object obj) {
        this.mCatEventRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnCatProactiveCmd(Handler handler, int i, Object obj) {
        this.mCatProCmdRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnCatSessionEnd(Handler handler, int i, Object obj) {
        this.mCatSessionEndRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnIccRefresh(Handler handler, int i, Object obj) {
        registerForIccRefresh(handler, i, obj);
    }

    public void setOnIccSmsFull(Handler handler, int i, Object obj) {
        this.mIccSmsFullRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnLteBandInfo(Handler handler, int i, Object obj) {
        this.mLteBandInfoRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnNITZTime(Handler handler, int i, Object obj) {
        this.mNITZTimeRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnNewCdmaSms(Handler handler, int i, Object obj) {
        this.mCdmaSmsRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnNewGsmBroadcastSms(Handler handler, int i, Object obj) {
        this.mGsmBroadcastSmsRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnNewGsmSms(Handler handler, int i, Object obj) {
        this.mGsmSmsRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnOemSignalStrengthUpdate(Handler handler, int i, Object obj) {
        this.mOemSignalStrengthRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnRestrictedStateChanged(Handler handler, int i, Object obj) {
        this.mRestrictedStateRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnSignalStrengthUpdate(Handler handler, int i, Object obj) {
        this.mSignalStrengthRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnSmsOnSim(Handler handler, int i, Object obj) {
        this.mSmsOnSimRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnSmsStatus(Handler handler, int i, Object obj) {
        this.mSmsStatusRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnSpeechCodec(Handler handler, int i, Object obj) {
        this.mSpeechCodecRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnSs(Handler handler, int i, Object obj) {
        this.mSsRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnSuppServiceNotification(Handler handler, int i, Object obj) {
        this.mSsnRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnUSSD(Handler handler, int i, Object obj) {
        this.mUSSDRegistrant = new Registrant(handler, i, obj);
    }

    public void setOnUnsolOemHookRaw(Handler handler, int i, Object obj) {
        this.mUnsolOemHookRawRegistrant = new Registrant(handler, i, obj);
    }

    /* Access modifiers changed, original: protected */
    /* JADX WARNING: Missing block: B:36:?, code skipped:
            return;
     */
    public void setRadioState(com.android.internal.telephony.CommandsInterface.RadioState r4) {
        /*
        r3 = this;
        r1 = r3.mStateMonitor;
        monitor-enter(r1);
        r0 = r3.mState;	 Catch:{ all -> 0x0071 }
        r3.mState = r4;	 Catch:{ all -> 0x0071 }
        r2 = r3.mState;	 Catch:{ all -> 0x0071 }
        if (r0 != r2) goto L_0x000d;
    L_0x000b:
        monitor-exit(r1);	 Catch:{ all -> 0x0071 }
    L_0x000c:
        return;
    L_0x000d:
        r2 = r3.mRadioStateChangedRegistrants;	 Catch:{ all -> 0x0071 }
        r2.notifyRegistrants();	 Catch:{ all -> 0x0071 }
        r2 = r3.mState;	 Catch:{ all -> 0x0071 }
        r2 = r2.isAvailable();	 Catch:{ all -> 0x0071 }
        if (r2 == 0) goto L_0x0028;
    L_0x001a:
        r2 = r0.isAvailable();	 Catch:{ all -> 0x0071 }
        if (r2 != 0) goto L_0x0028;
    L_0x0020:
        r2 = r3.mAvailRegistrants;	 Catch:{ all -> 0x0071 }
        r2.notifyRegistrants();	 Catch:{ all -> 0x0071 }
        r3.onRadioAvailable();	 Catch:{ all -> 0x0071 }
    L_0x0028:
        r2 = r3.mState;	 Catch:{ all -> 0x0071 }
        r2 = r2.isAvailable();	 Catch:{ all -> 0x0071 }
        if (r2 != 0) goto L_0x003b;
    L_0x0030:
        r2 = r0.isAvailable();	 Catch:{ all -> 0x0071 }
        if (r2 == 0) goto L_0x003b;
    L_0x0036:
        r2 = r3.mNotAvailRegistrants;	 Catch:{ all -> 0x0071 }
        r2.notifyRegistrants();	 Catch:{ all -> 0x0071 }
    L_0x003b:
        r2 = r3.mState;	 Catch:{ all -> 0x0071 }
        r2 = r2.isOn();	 Catch:{ all -> 0x0071 }
        if (r2 == 0) goto L_0x004e;
    L_0x0043:
        r2 = r0.isOn();	 Catch:{ all -> 0x0071 }
        if (r2 != 0) goto L_0x004e;
    L_0x0049:
        r2 = r3.mOnRegistrants;	 Catch:{ all -> 0x0071 }
        r2.notifyRegistrants();	 Catch:{ all -> 0x0071 }
    L_0x004e:
        r2 = r3.mState;	 Catch:{ all -> 0x0071 }
        r2 = r2.isOn();	 Catch:{ all -> 0x0071 }
        if (r2 == 0) goto L_0x005e;
    L_0x0056:
        r2 = r3.mState;	 Catch:{ all -> 0x0071 }
        r2 = r2.isAvailable();	 Catch:{ all -> 0x0071 }
        if (r2 != 0) goto L_0x006f;
    L_0x005e:
        r2 = r0.isOn();	 Catch:{ all -> 0x0071 }
        if (r2 == 0) goto L_0x006f;
    L_0x0064:
        r0 = r0.isAvailable();	 Catch:{ all -> 0x0071 }
        if (r0 == 0) goto L_0x006f;
    L_0x006a:
        r0 = r3.mOffOrNotAvailRegistrants;	 Catch:{ all -> 0x0071 }
        r0.notifyRegistrants();	 Catch:{ all -> 0x0071 }
    L_0x006f:
        monitor-exit(r1);	 Catch:{ all -> 0x0071 }
        goto L_0x000c;
    L_0x0071:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0071 }
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.BaseCommands.setRadioState(com.android.internal.telephony.CommandsInterface$RadioState):void");
    }

    public void setRatModeOptimizeSetting(boolean z, Message message) {
    }

    public void setUiccSubscription(int i, int i2, int i3, int i4, Message message) {
    }

    public void testingEmergencyCall() {
    }

    public void unSetOnCallRing(Handler handler) {
        if (this.mRingRegistrant != null && this.mRingRegistrant.getHandler() == handler) {
            this.mRingRegistrant.clear();
            this.mRingRegistrant = null;
        }
    }

    public void unSetOnCatCallSetUp(Handler handler) {
        if (this.mCatCallSetUpRegistrant != null && this.mCatCallSetUpRegistrant.getHandler() == handler) {
            this.mCatCallSetUpRegistrant.clear();
            this.mCatCallSetUpRegistrant = null;
        }
    }

    public void unSetOnCatCcAlphaNotify(Handler handler) {
        this.mCatCcAlphaRegistrant.clear();
    }

    public void unSetOnCatEvent(Handler handler) {
        if (this.mCatEventRegistrant != null && this.mCatEventRegistrant.getHandler() == handler) {
            this.mCatEventRegistrant.clear();
            this.mCatEventRegistrant = null;
        }
    }

    public void unSetOnCatProactiveCmd(Handler handler) {
        if (this.mCatProCmdRegistrant != null && this.mCatProCmdRegistrant.getHandler() == handler) {
            this.mCatProCmdRegistrant.clear();
            this.mCatProCmdRegistrant = null;
        }
    }

    public void unSetOnCatSessionEnd(Handler handler) {
        if (this.mCatSessionEndRegistrant != null && this.mCatSessionEndRegistrant.getHandler() == handler) {
            this.mCatSessionEndRegistrant.clear();
            this.mCatSessionEndRegistrant = null;
        }
    }

    public void unSetOnIccSmsFull(Handler handler) {
        if (this.mIccSmsFullRegistrant != null && this.mIccSmsFullRegistrant.getHandler() == handler) {
            this.mIccSmsFullRegistrant.clear();
            this.mIccSmsFullRegistrant = null;
        }
    }

    public void unSetOnLteBandInfo(Handler handler) {
        if (this.mLteBandInfoRegistrant != null) {
            this.mLteBandInfoRegistrant.clear();
        }
    }

    public void unSetOnNITZTime(Handler handler) {
        if (this.mNITZTimeRegistrant != null && this.mNITZTimeRegistrant.getHandler() == handler) {
            this.mNITZTimeRegistrant.clear();
            this.mNITZTimeRegistrant = null;
        }
    }

    public void unSetOnNewCdmaSms(Handler handler) {
        if (this.mCdmaSmsRegistrant != null && this.mCdmaSmsRegistrant.getHandler() == handler) {
            this.mCdmaSmsRegistrant.clear();
            this.mCdmaSmsRegistrant = null;
        }
    }

    public void unSetOnNewGsmBroadcastSms(Handler handler) {
        if (this.mGsmBroadcastSmsRegistrant != null && this.mGsmBroadcastSmsRegistrant.getHandler() == handler) {
            this.mGsmBroadcastSmsRegistrant.clear();
            this.mGsmBroadcastSmsRegistrant = null;
        }
    }

    public void unSetOnNewGsmSms(Handler handler) {
        if (this.mGsmSmsRegistrant != null && this.mGsmSmsRegistrant.getHandler() == handler) {
            this.mGsmSmsRegistrant.clear();
            this.mGsmSmsRegistrant = null;
        }
    }

    public void unSetOnOemSignalStrengthUpdate(Handler handler) {
        if (this.mOemSignalStrengthRegistrant != null) {
            this.mOemSignalStrengthRegistrant.clear();
        }
    }

    public void unSetOnRestrictedStateChanged(Handler handler) {
        if (this.mRestrictedStateRegistrant != null && this.mRestrictedStateRegistrant.getHandler() != handler) {
            this.mRestrictedStateRegistrant.clear();
            this.mRestrictedStateRegistrant = null;
        }
    }

    public void unSetOnSignalStrengthUpdate(Handler handler) {
        if (this.mSignalStrengthRegistrant != null && this.mSignalStrengthRegistrant.getHandler() == handler) {
            this.mSignalStrengthRegistrant.clear();
            this.mSignalStrengthRegistrant = null;
        }
    }

    public void unSetOnSmsOnSim(Handler handler) {
        if (this.mSmsOnSimRegistrant != null && this.mSmsOnSimRegistrant.getHandler() == handler) {
            this.mSmsOnSimRegistrant.clear();
            this.mSmsOnSimRegistrant = null;
        }
    }

    public void unSetOnSmsStatus(Handler handler) {
        if (this.mSmsStatusRegistrant != null && this.mSmsStatusRegistrant.getHandler() == handler) {
            this.mSmsStatusRegistrant.clear();
            this.mSmsStatusRegistrant = null;
        }
    }

    public void unSetOnSpeechCodec(Handler handler) {
        if (this.mSpeechCodecRegistrant != null) {
            this.mSpeechCodecRegistrant.clear();
        }
    }

    public void unSetOnSs(Handler handler) {
        this.mSsRegistrant.clear();
    }

    public void unSetOnSuppServiceNotification(Handler handler) {
        if (this.mSsnRegistrant != null && this.mSsnRegistrant.getHandler() == handler) {
            this.mSsnRegistrant.clear();
            this.mSsnRegistrant = null;
        }
    }

    public void unSetOnUSSD(Handler handler) {
        if (this.mUSSDRegistrant != null && this.mUSSDRegistrant.getHandler() == handler) {
            this.mUSSDRegistrant.clear();
            this.mUSSDRegistrant = null;
        }
    }

    public void unSetOnUnsolOemHookRaw(Handler handler) {
        if (this.mUnsolOemHookRawRegistrant != null && this.mUnsolOemHookRawRegistrant.getHandler() == handler) {
            this.mUnsolOemHookRawRegistrant.clear();
            this.mUnsolOemHookRawRegistrant = null;
        }
    }

    public void unregisterForAvailable(Handler handler) {
        synchronized (this.mStateMonitor) {
            this.mAvailRegistrants.remove(handler);
        }
    }

    public void unregisterForCallStateChanged(Handler handler) {
        this.mCallStateRegistrants.remove(handler);
    }

    public void unregisterForCallWaitingInfo(Handler handler) {
        this.mCallWaitingInfoRegistrants.remove(handler);
    }

    public void unregisterForCdmaOtaProvision(Handler handler) {
        this.mOtaProvisionRegistrants.remove(handler);
    }

    public void unregisterForCdmaPrlChanged(Handler handler) {
        this.mCdmaPrlChangedRegistrants.remove(handler);
    }

    public void unregisterForCdmaSubscriptionChanged(Handler handler) {
        this.mCdmaSubscriptionChangedRegistrants.remove(handler);
    }

    public void unregisterForCellInfoList(Handler handler) {
        this.mRilCellInfoListRegistrants.remove(handler);
    }

    public void unregisterForDataNetworkStateChanged(Handler handler) {
        this.mDataNetworkStateRegistrants.remove(handler);
    }

    public void unregisterForDisplayInfo(Handler handler) {
        this.mDisplayInfoRegistrants.remove(handler);
    }

    public void unregisterForExitEmergencyCallbackMode(Handler handler) {
        this.mExitEmergencyCallbackModeRegistrants.remove(handler);
    }

    public void unregisterForHardwareConfigChanged(Handler handler) {
        this.mHardwareConfigChangeRegistrants.remove(handler);
    }

    public void unregisterForIccRefresh(Handler handler) {
        this.mIccRefreshRegistrants.remove(handler);
    }

    public void unregisterForIccStatusChanged(Handler handler) {
        this.mIccStatusChangedRegistrants.remove(handler);
    }

    public void unregisterForImsNetworkStateChanged(Handler handler) {
        this.mImsNetworkStateChangedRegistrants.remove(handler);
    }

    public void unregisterForInCallVoicePrivacyOff(Handler handler) {
        this.mVoicePrivacyOffRegistrants.remove(handler);
    }

    public void unregisterForInCallVoicePrivacyOn(Handler handler) {
        this.mVoicePrivacyOnRegistrants.remove(handler);
    }

    public void unregisterForLineControlInfo(Handler handler) {
        this.mLineControlInfoRegistrants.remove(handler);
    }

    public void unregisterForModemCapEvent(Handler handler) {
        this.mModemCapRegistrants.remove(handler);
    }

    public void unregisterForNotAvailable(Handler handler) {
        synchronized (this.mStateMonitor) {
            this.mNotAvailRegistrants.remove(handler);
        }
    }

    public void unregisterForNumberInfo(Handler handler) {
        this.mNumberInfoRegistrants.remove(handler);
    }

    public void unregisterForOffOrNotAvailable(Handler handler) {
        synchronized (this.mStateMonitor) {
            this.mOffOrNotAvailRegistrants.remove(handler);
        }
    }

    public void unregisterForOn(Handler handler) {
        synchronized (this.mStateMonitor) {
            this.mOnRegistrants.remove(handler);
        }
    }

    public void unregisterForRadioStateChanged(Handler handler) {
        synchronized (this.mStateMonitor) {
            this.mRadioStateChangedRegistrants.remove(handler);
        }
    }

    public void unregisterForRedirectedNumberInfo(Handler handler) {
        this.mRedirNumInfoRegistrants.remove(handler);
    }

    public void unregisterForResendIncallMute(Handler handler) {
        this.mResendIncallMuteRegistrants.remove(handler);
    }

    public void unregisterForRilConnected(Handler handler) {
        this.mRilConnectedRegistrants.remove(handler);
    }

    public void unregisterForRingbackTone(Handler handler) {
        this.mRingbackToneRegistrants.remove(handler);
    }

    public void unregisterForSignalInfo(Handler handler) {
        this.mSignalInfoRegistrants.remove(handler);
    }

    public void unregisterForSimRefreshEvent(Handler handler) {
        this.mSimRefreshRegistrants.remove(handler);
    }

    public void unregisterForSrvccStateChanged(Handler handler) {
        this.mSrvccStateRegistrants.remove(handler);
    }

    public void unregisterForSubscriptionStatusChanged(Handler handler) {
        this.mSubscriptionStatusRegistrants.remove(handler);
    }

    public void unregisterForT53AudioControlInfo(Handler handler) {
        this.mT53AudCntrlInfoRegistrants.remove(handler);
    }

    public void unregisterForT53ClirInfo(Handler handler) {
        this.mT53ClirInfoRegistrants.remove(handler);
    }

    public void unregisterForVoiceNetworkStateChanged(Handler handler) {
        this.mVoiceNetworkStateRegistrants.remove(handler);
    }

    public void unregisterForVoiceRadioTechChanged(Handler handler) {
        this.mVoiceRadioTechChangedRegistrants.remove(handler);
    }

    public void unregisterForWwanIwlanCoexistence(Handler handler) {
        this.mWwanIwlanCoexistenceRegistrants.remove(handler);
    }

    public void unsetOnIccRefresh(Handler handler) {
        unregisterForIccRefresh(handler);
    }

    public void updateOemDataSettings(boolean z, boolean z2, boolean z3, Message message) {
    }

    public void updateStackBinding(int i, int i2, Message message) {
    }
}
