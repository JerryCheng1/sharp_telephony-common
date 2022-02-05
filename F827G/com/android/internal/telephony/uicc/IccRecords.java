package com.android.internal.telephony.uicc;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.TelephonyManager;
import android.util.Base64;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public abstract class IccRecords extends Handler implements IccConstants {
    protected static final boolean DBG = true;
    public static final int DEFAULT_VOICE_MESSAGE_COUNT = -2;
    private static final int EVENT_AKA_AUTHENTICATE_DONE = 90;
    protected static final int EVENT_APP_READY = 1;
    public static final int EVENT_CFI = 1;
    public static final int EVENT_GET_ICC_RECORD_DONE = 100;
    protected static final int EVENT_GET_SMS_RECORD_SIZE_DONE = 28;
    public static final int EVENT_MWI = 0;
    public static final int EVENT_REFRESH = 31;
    public static final int EVENT_REFRESH_OEM = 29;
    private static final int EVENT_RESET = 200;
    protected static final int EVENT_SET_MSISDN_DONE = 30;
    public static final int EVENT_SPN = 2;
    private static final int RESET_DELAY_TIME = 5000;
    public static final int SPN_RULE_SHOW_PLMN = 2;
    public static final int SPN_RULE_SHOW_SPN = 1;
    protected static final int UNINITIALIZED = -1;
    protected static final int UNKNOWN = 0;
    public static final int UNKNOWN_VOICE_MESSAGE_COUNT = -1;
    private IccIoResult auth_rsp;
    protected AdnRecordCache mAdnCache;
    protected CommandsInterface mCi;
    protected Context mContext;
    protected IccFileHandler mFh;
    protected String mGid1;
    protected String mIccId;
    protected String mImsi;
    private boolean mOEMHookSimRefresh;
    protected UiccCardApplication mParentApp;
    protected int mRecordsToLoad;
    protected String mSpn;
    protected AtomicBoolean mDestroyed = new AtomicBoolean(false);
    protected RegistrantList mRecordsLoadedRegistrants = new RegistrantList();
    protected RegistrantList mImsiReadyRegistrants = new RegistrantList();
    protected RegistrantList mRecordsEventsRegistrants = new RegistrantList();
    protected RegistrantList mNewSmsRegistrants = new RegistrantList();
    protected RegistrantList mNetworkSelectionModeAutomaticRegistrants = new RegistrantList();
    protected boolean mRecordsRequested = false;
    protected String mMsisdn = null;
    protected String mMsisdnTag = null;
    protected String mNewMsisdn = null;
    protected String mNewMsisdnTag = null;
    protected String mVoiceMailNum = null;
    protected String mVoiceMailTag = null;
    protected String mNewVoiceMailNum = null;
    protected String mNewVoiceMailTag = null;
    protected boolean mIsVoiceMailFixed = false;
    protected int mMncLength = -1;
    protected int mMailboxIndex = 0;
    protected int mSmsCountOnIcc = -1;
    private final Object mLock = new Object();

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public interface IccRecordLoaded {
        String getEfName();

        void onRecordLoaded(AsyncResult asyncResult);
    }

    public abstract int getDisplayRule(String str);

    public abstract int getVoiceMessageCount();

    protected abstract void handleFileUpdate(int i);

    protected abstract void log(String str);

    protected abstract void loge(String str);

    protected abstract void onAllRecordsLoaded();

    public abstract void onReady();

    protected abstract void onRecordLoaded();

    public abstract void onRefresh(boolean z, int[] iArr);

    public abstract void setVoiceMailNumber(String str, String str2, Message message);

    public abstract void setVoiceMessageWaiting(int i, int i2);

    @Override // android.os.Handler
    public String toString() {
        return "mDestroyed=" + this.mDestroyed + " mContext=" + this.mContext + " mCi=" + this.mCi + " mFh=" + this.mFh + " mParentApp=" + this.mParentApp + " recordsLoadedRegistrants=" + this.mRecordsLoadedRegistrants + " mImsiReadyRegistrants=" + this.mImsiReadyRegistrants + " mRecordsEventsRegistrants=" + this.mRecordsEventsRegistrants + " mNewSmsRegistrants=" + this.mNewSmsRegistrants + " mNetworkSelectionModeAutomaticRegistrants=" + this.mNetworkSelectionModeAutomaticRegistrants + " recordsToLoad=" + this.mRecordsToLoad + " adnCache=" + this.mAdnCache + " recordsRequested=" + this.mRecordsRequested + " iccid=" + this.mIccId + " msisdnTag=" + this.mMsisdnTag + " voiceMailNum=" + this.mVoiceMailNum + " voiceMailTag=" + this.mVoiceMailTag + " newVoiceMailNum=" + this.mNewVoiceMailNum + " newVoiceMailTag=" + this.mNewVoiceMailTag + " isVoiceMailFixed=" + this.mIsVoiceMailFixed + " mImsi=" + this.mImsi + " mncLength=" + this.mMncLength + " mailboxIndex=" + this.mMailboxIndex + " spn=" + this.mSpn;
    }

    public IccRecords(UiccCardApplication app, Context c, CommandsInterface ci) {
        this.mOEMHookSimRefresh = false;
        this.mContext = c;
        this.mCi = ci;
        this.mFh = app.getIccFileHandler();
        this.mParentApp = app;
        this.mOEMHookSimRefresh = this.mContext.getResources().getBoolean(17957016);
        if (this.mOEMHookSimRefresh) {
            this.mCi.registerForSimRefreshEvent(this, 29, null);
        } else {
            this.mCi.registerForIccRefresh(this, 31, null);
        }
    }

    public void dispose() {
        this.mDestroyed.set(true);
        if (this.mOEMHookSimRefresh) {
            this.mCi.unregisterForSimRefreshEvent(this);
        } else {
            this.mCi.unregisterForIccRefresh(this);
        }
        this.mParentApp = null;
        this.mFh = null;
        this.mCi = null;
        this.mContext = null;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void recordsRequired() {
    }

    public AdnRecordCache getAdnCache() {
        return this.mAdnCache;
    }

    public String getIccId() {
        return this.mIccId;
    }

    public void registerForRecordsLoaded(Handler h, int what, Object obj) {
        if (!this.mDestroyed.get()) {
            for (int i = this.mRecordsLoadedRegistrants.size() - 1; i >= 0; i--) {
                Handler rH = ((Registrant) this.mRecordsLoadedRegistrants.get(i)).getHandler();
                if (rH != null && rH == h) {
                    return;
                }
            }
            Registrant r = new Registrant(h, what, obj);
            this.mRecordsLoadedRegistrants.add(r);
            if (this.mRecordsToLoad == 0 && this.mRecordsRequested && isAppStateReady()) {
                r.notifyRegistrant(new AsyncResult((Object) null, (Object) null, (Throwable) null));
            } else {
                log("registerForRecordsLoaded, not notifying the registrant immediately");
            }
        }
    }

    public void unregisterForRecordsLoaded(Handler h) {
        this.mRecordsLoadedRegistrants.remove(h);
    }

    public void registerForImsiReady(Handler h, int what, Object obj) {
        if (!this.mDestroyed.get()) {
            Registrant r = new Registrant(h, what, obj);
            this.mImsiReadyRegistrants.add(r);
            if (this.mImsi == null || !isAppStateReady()) {
                log("registerForImsiReady, not notifying the registrant immediately");
            } else {
                r.notifyRegistrant(new AsyncResult((Object) null, (Object) null, (Throwable) null));
            }
        }
    }

    public void unregisterForImsiReady(Handler h) {
        this.mImsiReadyRegistrants.remove(h);
    }

    public void registerForRecordsEvents(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mRecordsEventsRegistrants.add(r);
        r.notifyResult(0);
        r.notifyResult(1);
    }

    public void unregisterForRecordsEvents(Handler h) {
        this.mRecordsEventsRegistrants.remove(h);
    }

    public void registerForNewSms(Handler h, int what, Object obj) {
        this.mNewSmsRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForNewSms(Handler h) {
        this.mNewSmsRegistrants.remove(h);
    }

    public void registerForNetworkSelectionModeAutomatic(Handler h, int what, Object obj) {
        this.mNetworkSelectionModeAutomaticRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForNetworkSelectionModeAutomatic(Handler h) {
        this.mNetworkSelectionModeAutomaticRegistrants.remove(h);
    }

    public String getIMSI() {
        return null;
    }

    public String getMccMncOnSimLock() {
        return null;
    }

    public void setImsi(String imsi) {
        this.mImsi = imsi;
        this.mImsiReadyRegistrants.notifyRegistrants();
    }

    public String getImsiOnSimLock() {
        return null;
    }

    public int getBrand() {
        return 0;
    }

    public String getNAI() {
        return null;
    }

    public String getMsisdnNumber() {
        return this.mMsisdn;
    }

    public String getGid1() {
        return null;
    }

    public void setMsisdnNumber(String alphaTag, String number, Message onComplete) {
        this.mMsisdn = number;
        this.mMsisdnTag = alphaTag;
        log("Set MSISDN: " + this.mMsisdnTag + " " + this.mMsisdn);
        new AdnRecordLoader(this.mFh).updateEF(new AdnRecord(this.mMsisdnTag, this.mMsisdn), IccConstants.EF_MSISDN, IccConstants.EF_EXT1, 1, null, obtainMessage(30, onComplete));
    }

    public String getMsisdnAlphaTag() {
        return this.mMsisdnTag;
    }

    public String getVoiceMailNumber() {
        return this.mVoiceMailNum;
    }

    public String getServiceProviderName() {
        String providerName = this.mSpn;
        UiccCardApplication parentApp = this.mParentApp;
        if (parentApp != null) {
            UiccCard card = parentApp.getUiccCard();
            if (card != null) {
                String brandOverride = card.getOperatorBrandOverride();
                if (brandOverride != null) {
                    log("getServiceProviderName: override");
                    providerName = brandOverride;
                } else {
                    log("getServiceProviderName: no brandOverride");
                }
            } else {
                log("getServiceProviderName: card is null");
            }
        } else {
            log("getServiceProviderName: mParentApp is null");
        }
        log("getServiceProviderName: providerName=" + providerName);
        return providerName;
    }

    protected void setServiceProviderName(String spn) {
        this.mSpn = spn;
    }

    public String getVoiceMailAlphaTag() {
        return this.mVoiceMailTag;
    }

    protected void onIccRefreshInit() {
        this.mAdnCache.reset();
        UiccCardApplication parentApp = this.mParentApp;
        if (parentApp != null && parentApp.getState() == IccCardApplicationStatus.AppState.APPSTATE_READY) {
            sendMessage(obtainMessage(1));
        }
    }

    public boolean getRecordsLoaded() {
        return this.mRecordsToLoad == 0 && this.mRecordsRequested;
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        try {
            switch (msg.what) {
                case EVENT_GET_SMS_RECORD_SIZE_DONE /* 28 */:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        loge("Exception in EVENT_GET_SMS_RECORD_SIZE_DONE " + ar.exception);
                        return;
                    }
                    int[] recordSize = (int[]) ar.result;
                    try {
                        this.mSmsCountOnIcc = recordSize[2];
                        log("EVENT_GET_SMS_RECORD_SIZE_DONE Size " + recordSize[0] + " total " + recordSize[1] + " record " + recordSize[2]);
                        return;
                    } catch (ArrayIndexOutOfBoundsException exc) {
                        loge("ArrayIndexOutOfBoundsException in EVENT_GET_SMS_RECORD_SIZE_DONE: " + exc.toString());
                        return;
                    }
                case EVENT_REFRESH_OEM /* 29 */:
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    log("Card REFRESH OEM occurred: ");
                    if (ar2.exception == null) {
                        handleRefreshOem((byte[]) ar2.result);
                        return;
                    } else {
                        loge("Icc refresh OEM Exception: " + ar2.exception);
                        return;
                    }
                case 31:
                    AsyncResult ar3 = (AsyncResult) msg.obj;
                    log("Card REFRESH occurred: ");
                    if (ar3.exception == null) {
                        broadcastRefresh();
                        handleRefresh((IccRefreshResponse) ar3.result);
                        return;
                    }
                    loge("Icc refresh Exception: " + ar3.exception);
                    return;
                case EVENT_AKA_AUTHENTICATE_DONE /* 90 */:
                    AsyncResult ar4 = (AsyncResult) msg.obj;
                    this.auth_rsp = null;
                    log("EVENT_AKA_AUTHENTICATE_DONE");
                    if (ar4.exception != null) {
                        loge("Exception ICC SIM AKA: " + ar4.exception);
                    } else {
                        try {
                            this.auth_rsp = (IccIoResult) ar4.result;
                            log("ICC SIM AKA: auth_rsp = " + this.auth_rsp);
                        } catch (Exception e) {
                            loge("Failed to parse ICC SIM AKA contents: " + e);
                        }
                    }
                    synchronized (this.mLock) {
                        this.mLock.notifyAll();
                    }
                    return;
                case EVENT_GET_ICC_RECORD_DONE /* 100 */:
                    AsyncResult ar5 = (AsyncResult) msg.obj;
                    IccRecordLoaded recordLoaded = (IccRecordLoaded) ar5.userObj;
                    log(recordLoaded.getEfName() + " LOADED");
                    if (ar5.exception != null) {
                        loge("Record Load Exception: " + ar5.exception);
                    } else {
                        recordLoaded.onRecordLoaded(ar5);
                    }
                    return;
                case 200:
                    resetAtNewThread();
                    return;
                default:
                    super.handleMessage(msg);
                    return;
            }
        } catch (RuntimeException exc2) {
            loge("Exception parsing SIM record: " + exc2);
        } finally {
            onRecordLoaded();
        }
    }

    protected void broadcastRefresh() {
    }

    protected void handleRefresh(IccRefreshResponse refreshResponse) {
        if (refreshResponse == null) {
            log("handleRefresh received without input");
        } else if (refreshResponse.aid == null || refreshResponse.aid.equals(this.mParentApp.getAid())) {
            switch (refreshResponse.refreshResult) {
                case 0:
                    log("handleRefresh with SIM_FILE_UPDATED");
                    handleFileUpdate(refreshResponse.efId);
                    return;
                case 1:
                    log("handleRefresh with SIM_REFRESH_INIT");
                    if (this.mAdnCache != null) {
                        this.mAdnCache.reset();
                    }
                    this.mRecordsRequested = false;
                    return;
                case 2:
                    log("handleRefresh with SIM_REFRESH_RESET");
                    if (powerOffOnSimReset()) {
                        this.mCi.setRadioPower(false, null);
                        return;
                    }
                    if (this.mAdnCache != null) {
                        this.mAdnCache.reset();
                    }
                    this.mRecordsRequested = false;
                    this.mImsi = null;
                    return;
                case 11:
                    showRebootDialog();
                    return;
                default:
                    log("handleRefresh with unknown operation");
                    return;
            }
        }
    }

    private void handleRefreshOem(byte[] data) {
        ByteBuffer payload = ByteBuffer.wrap(data);
        IccRefreshResponse response = UiccController.parseOemSimRefresh(payload);
        IccCardApplicationStatus.AppType appType = new IccCardApplicationStatus().AppTypeFromRILInt(payload.getInt());
        int slotId = payload.get();
        if (appType == IccCardApplicationStatus.AppType.APPTYPE_UNKNOWN || appType == this.mParentApp.getType()) {
            broadcastRefresh();
            handleRefresh(response);
            if (response.refreshResult == 0 || response.refreshResult == 1) {
                log("send broadcast org.codeaurora.intent.action.ACTION_SIM_REFRESH_UPDATE");
                Intent sendIntent = new Intent("org.codeaurora.intent.action.ACTION_SIM_REFRESH_UPDATE");
                if (TelephonyManager.getDefault().isMultiSimEnabled()) {
                    sendIntent.putExtra("slot", slotId);
                }
                this.mContext.sendBroadcast(sendIntent, null);
            }
        }
    }

    public boolean isCspPlmnEnabled() {
        return false;
    }

    public String getOperatorNumeric() {
        return null;
    }

    public boolean isCallForwardStatusStored() {
        return false;
    }

    public boolean getVoiceCallForwardingFlag() {
        return false;
    }

    public void setVoiceCallForwardingFlag(int line, boolean enable, String number) {
    }

    public boolean isProvisioned() {
        return true;
    }

    public IsimRecords getIsimRecords() {
        return null;
    }

    public UsimServiceTable getUsimServiceTable() {
        return null;
    }

    protected void setSystemProperty(String key, String val) {
        TelephonyManager.getDefault();
        TelephonyManager.setTelephonyProperty(this.mParentApp.getPhoneId(), key, val);
        log("[key, value]=" + key + ", " + val);
    }

    public String getIccSimChallengeResponse(int authContext, String data) {
        String str = null;
        log("getIccSimChallengeResponse:");
        try {
            synchronized (this.mLock) {
                CommandsInterface ci = this.mCi;
                UiccCardApplication parentApp = this.mParentApp;
                if (ci == null || parentApp == null) {
                    loge("getIccSimChallengeResponse: Fail, ci or parentApp is null");
                } else {
                    ci.requestIccSimAuthentication(authContext, data, parentApp.getAid(), obtainMessage(EVENT_AKA_AUTHENTICATE_DONE));
                    try {
                        this.mLock.wait();
                        log("getIccSimChallengeResponse: return auth_rsp");
                        str = Base64.encodeToString(this.auth_rsp.payload, 2);
                    } catch (InterruptedException e) {
                        loge("getIccSimChallengeResponse: Fail, interrupted while trying to request Icc Sim Auth");
                    }
                }
            }
        } catch (Exception e2) {
            loge("getIccSimChallengeResponse: Fail while trying to request Icc Sim Auth");
        }
        return str;
    }

    protected boolean requirePowerOffOnSimRefreshReset() {
        return this.mContext.getResources().getBoolean(17956985);
    }

    public int getSmsCapacityOnIcc() {
        log("getSmsCapacityOnIcc: " + this.mSmsCountOnIcc);
        return this.mSmsCountOnIcc;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("IccRecords: " + this);
        pw.println(" mDestroyed=" + this.mDestroyed);
        pw.println(" mCi=" + this.mCi);
        pw.println(" mFh=" + this.mFh);
        pw.println(" mParentApp=" + this.mParentApp);
        pw.println(" recordsLoadedRegistrants: size=" + this.mRecordsLoadedRegistrants.size());
        for (int i = 0; i < this.mRecordsLoadedRegistrants.size(); i++) {
            pw.println("  recordsLoadedRegistrants[" + i + "]=" + ((Registrant) this.mRecordsLoadedRegistrants.get(i)).getHandler());
        }
        pw.println(" mImsiReadyRegistrants: size=" + this.mImsiReadyRegistrants.size());
        for (int i2 = 0; i2 < this.mImsiReadyRegistrants.size(); i2++) {
            pw.println("  mImsiReadyRegistrants[" + i2 + "]=" + ((Registrant) this.mImsiReadyRegistrants.get(i2)).getHandler());
        }
        pw.println(" mRecordsEventsRegistrants: size=" + this.mRecordsEventsRegistrants.size());
        for (int i3 = 0; i3 < this.mRecordsEventsRegistrants.size(); i3++) {
            pw.println("  mRecordsEventsRegistrants[" + i3 + "]=" + ((Registrant) this.mRecordsEventsRegistrants.get(i3)).getHandler());
        }
        pw.println(" mNewSmsRegistrants: size=" + this.mNewSmsRegistrants.size());
        for (int i4 = 0; i4 < this.mNewSmsRegistrants.size(); i4++) {
            pw.println("  mNewSmsRegistrants[" + i4 + "]=" + ((Registrant) this.mNewSmsRegistrants.get(i4)).getHandler());
        }
        pw.println(" mNetworkSelectionModeAutomaticRegistrants: size=" + this.mNetworkSelectionModeAutomaticRegistrants.size());
        for (int i5 = 0; i5 < this.mNetworkSelectionModeAutomaticRegistrants.size(); i5++) {
            pw.println("  mNetworkSelectionModeAutomaticRegistrants[" + i5 + "]=" + ((Registrant) this.mNetworkSelectionModeAutomaticRegistrants.get(i5)).getHandler());
        }
        pw.println(" mRecordsRequested=" + this.mRecordsRequested);
        pw.println(" mRecordsToLoad=" + this.mRecordsToLoad);
        pw.println(" mRdnCache=" + this.mAdnCache);
        pw.println(" iccid=" + this.mIccId);
        pw.println(" mMsisdn=" + this.mMsisdn);
        pw.println(" mMsisdnTag=" + this.mMsisdnTag);
        pw.println(" mVoiceMailNum=" + this.mVoiceMailNum);
        pw.println(" mVoiceMailTag=" + this.mVoiceMailTag);
        pw.println(" mNewVoiceMailNum=" + this.mNewVoiceMailNum);
        pw.println(" mNewVoiceMailTag=" + this.mNewVoiceMailTag);
        pw.println(" mIsVoiceMailFixed=" + this.mIsVoiceMailFixed);
        pw.println(" mImsi=" + this.mImsi);
        pw.println(" mMncLength=" + this.mMncLength);
        pw.println(" mMailboxIndex=" + this.mMailboxIndex);
        pw.println(" mSpn=" + this.mSpn);
        pw.flush();
    }

    protected boolean powerOffOnSimReset() {
        return !this.mContext.getResources().getBoolean(17956973);
    }

    protected boolean isAppStateReady() {
        IccCardApplicationStatus.AppState appState = this.mParentApp.getState();
        log("isAppStateReady : appState = " + appState);
        return appState == IccCardApplicationStatus.AppState.APPSTATE_READY;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reboot() {
        log("Reboot due to REFRESH failed 3 times");
        ((PowerManager) this.mContext.getSystemService("power")).reboot("REFRESH failed 3 times.");
    }

    private void resetAtNewThread() {
        new Thread() { // from class: com.android.internal.telephony.uicc.IccRecords.1
            @Override // java.lang.Thread, java.lang.Runnable
            public void run() {
                IccRecords.this.reboot();
            }
        }.start();
    }

    private void showRebootDialog() {
        Resources r = Resources.getSystem();
        String title = r.getString(17041201);
        AlertDialog dialog = new AlertDialog.Builder(this.mContext).setTitle(title).setMessage(r.getString(17041202)).create();
        dialog.getWindow().setType(2009);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        dialog.getWindow().addFlags(2621440);
        sendMessageDelayed(obtainMessage(200), 5000L);
    }
}
