package com.android.internal.telephony.uicc;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
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
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

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
    protected AtomicBoolean mDestroyed = new AtomicBoolean(false);
    protected IccFileHandler mFh;
    protected String mGid1;
    protected String mIccId;
    protected String mImsi;
    protected RegistrantList mImsiReadyRegistrants = new RegistrantList();
    protected boolean mIsVoiceMailFixed = false;
    private final Object mLock = new Object();
    protected int mMailboxIndex = 0;
    protected int mMncLength = -1;
    protected String mMsisdn = null;
    protected String mMsisdnTag = null;
    protected RegistrantList mNetworkSelectionModeAutomaticRegistrants = new RegistrantList();
    protected String mNewMsisdn = null;
    protected String mNewMsisdnTag = null;
    protected RegistrantList mNewSmsRegistrants = new RegistrantList();
    protected String mNewVoiceMailNum = null;
    protected String mNewVoiceMailTag = null;
    private boolean mOEMHookSimRefresh = false;
    protected UiccCardApplication mParentApp;
    protected RegistrantList mRecordsEventsRegistrants = new RegistrantList();
    protected RegistrantList mRecordsLoadedRegistrants = new RegistrantList();
    protected boolean mRecordsRequested = false;
    protected int mRecordsToLoad;
    protected int mSmsCountOnIcc = -1;
    protected String mSpn;
    protected String mVoiceMailNum = null;
    protected String mVoiceMailTag = null;

    public interface IccRecordLoaded {
        String getEfName();

        void onRecordLoaded(AsyncResult asyncResult);
    }

    public IccRecords(UiccCardApplication uiccCardApplication, Context context, CommandsInterface commandsInterface) {
        this.mContext = context;
        this.mCi = commandsInterface;
        this.mFh = uiccCardApplication.getIccFileHandler();
        this.mParentApp = uiccCardApplication;
        this.mOEMHookSimRefresh = this.mContext.getResources().getBoolean(17957016);
        if (this.mOEMHookSimRefresh) {
            this.mCi.registerForSimRefreshEvent(this, 29, null);
        } else {
            this.mCi.registerForIccRefresh(this, 31, null);
        }
    }

    private void handleRefreshOem(byte[] bArr) {
        ByteBuffer wrap = ByteBuffer.wrap(bArr);
        IccRefreshResponse parseOemSimRefresh = UiccController.parseOemSimRefresh(wrap);
        AppType AppTypeFromRILInt = new IccCardApplicationStatus().AppTypeFromRILInt(wrap.getInt());
        byte b = wrap.get();
        if (AppTypeFromRILInt == AppType.APPTYPE_UNKNOWN || AppTypeFromRILInt == this.mParentApp.getType()) {
            broadcastRefresh();
            handleRefresh(parseOemSimRefresh);
            if (parseOemSimRefresh.refreshResult == 0 || parseOemSimRefresh.refreshResult == 1) {
                log("send broadcast org.codeaurora.intent.action.ACTION_SIM_REFRESH_UPDATE");
                Intent intent = new Intent("org.codeaurora.intent.action.ACTION_SIM_REFRESH_UPDATE");
                if (TelephonyManager.getDefault().isMultiSimEnabled()) {
                    intent.putExtra("slot", b);
                }
                this.mContext.sendBroadcast(intent, null);
            }
        }
    }

    private void reboot() {
        log("Reboot due to REFRESH failed 3 times");
        ((PowerManager) this.mContext.getSystemService("power")).reboot("REFRESH failed 3 times.");
    }

    private void resetAtNewThread() {
        new Thread() {
            public void run() {
                IccRecords.this.reboot();
            }
        }.start();
    }

    private void showRebootDialog() {
        Resources system = Resources.getSystem();
        String string = system.getString(17041201);
        AlertDialog create = new Builder(this.mContext).setTitle(string).setMessage(system.getString(17041202)).create();
        create.getWindow().setType(2009);
        create.setCanceledOnTouchOutside(false);
        create.show();
        create.getWindow().addFlags(2621440);
        sendMessageDelayed(obtainMessage(200), 5000);
    }

    /* Access modifiers changed, original: protected */
    public void broadcastRefresh() {
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

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        int i;
        int i2 = 0;
        printWriter.println("IccRecords: " + this);
        printWriter.println(" mDestroyed=" + this.mDestroyed);
        printWriter.println(" mCi=" + this.mCi);
        printWriter.println(" mFh=" + this.mFh);
        printWriter.println(" mParentApp=" + this.mParentApp);
        printWriter.println(" recordsLoadedRegistrants: size=" + this.mRecordsLoadedRegistrants.size());
        for (i = 0; i < this.mRecordsLoadedRegistrants.size(); i++) {
            printWriter.println("  recordsLoadedRegistrants[" + i + "]=" + ((Registrant) this.mRecordsLoadedRegistrants.get(i)).getHandler());
        }
        printWriter.println(" mImsiReadyRegistrants: size=" + this.mImsiReadyRegistrants.size());
        for (i = 0; i < this.mImsiReadyRegistrants.size(); i++) {
            printWriter.println("  mImsiReadyRegistrants[" + i + "]=" + ((Registrant) this.mImsiReadyRegistrants.get(i)).getHandler());
        }
        printWriter.println(" mRecordsEventsRegistrants: size=" + this.mRecordsEventsRegistrants.size());
        for (i = 0; i < this.mRecordsEventsRegistrants.size(); i++) {
            printWriter.println("  mRecordsEventsRegistrants[" + i + "]=" + ((Registrant) this.mRecordsEventsRegistrants.get(i)).getHandler());
        }
        printWriter.println(" mNewSmsRegistrants: size=" + this.mNewSmsRegistrants.size());
        for (i = 0; i < this.mNewSmsRegistrants.size(); i++) {
            printWriter.println("  mNewSmsRegistrants[" + i + "]=" + ((Registrant) this.mNewSmsRegistrants.get(i)).getHandler());
        }
        printWriter.println(" mNetworkSelectionModeAutomaticRegistrants: size=" + this.mNetworkSelectionModeAutomaticRegistrants.size());
        while (i2 < this.mNetworkSelectionModeAutomaticRegistrants.size()) {
            printWriter.println("  mNetworkSelectionModeAutomaticRegistrants[" + i2 + "]=" + ((Registrant) this.mNetworkSelectionModeAutomaticRegistrants.get(i2)).getHandler());
            i2++;
        }
        printWriter.println(" mRecordsRequested=" + this.mRecordsRequested);
        printWriter.println(" mRecordsToLoad=" + this.mRecordsToLoad);
        printWriter.println(" mRdnCache=" + this.mAdnCache);
        printWriter.println(" iccid=" + this.mIccId);
        printWriter.println(" mMsisdn=" + this.mMsisdn);
        printWriter.println(" mMsisdnTag=" + this.mMsisdnTag);
        printWriter.println(" mVoiceMailNum=" + this.mVoiceMailNum);
        printWriter.println(" mVoiceMailTag=" + this.mVoiceMailTag);
        printWriter.println(" mNewVoiceMailNum=" + this.mNewVoiceMailNum);
        printWriter.println(" mNewVoiceMailTag=" + this.mNewVoiceMailTag);
        printWriter.println(" mIsVoiceMailFixed=" + this.mIsVoiceMailFixed);
        printWriter.println(" mImsi=" + this.mImsi);
        printWriter.println(" mMncLength=" + this.mMncLength);
        printWriter.println(" mMailboxIndex=" + this.mMailboxIndex);
        printWriter.println(" mSpn=" + this.mSpn);
        printWriter.flush();
    }

    public AdnRecordCache getAdnCache() {
        return this.mAdnCache;
    }

    public int getBrand() {
        return 0;
    }

    public abstract int getDisplayRule(String str);

    public String getGid1() {
        return null;
    }

    public String getIMSI() {
        return null;
    }

    public String getIccId() {
        return this.mIccId;
    }

    public String getIccSimChallengeResponse(int i, String str) {
        log("getIccSimChallengeResponse:");
        try {
            synchronized (this.mLock) {
                CommandsInterface commandsInterface = this.mCi;
                UiccCardApplication uiccCardApplication = this.mParentApp;
                if (commandsInterface == null || uiccCardApplication == null) {
                    loge("getIccSimChallengeResponse: Fail, ci or parentApp is null");
                    return null;
                }
                commandsInterface.requestIccSimAuthentication(i, str, uiccCardApplication.getAid(), obtainMessage(EVENT_AKA_AUTHENTICATE_DONE));
                try {
                    this.mLock.wait();
                    log("getIccSimChallengeResponse: return auth_rsp");
                    return Base64.encodeToString(this.auth_rsp.payload, 2);
                } catch (InterruptedException e) {
                    loge("getIccSimChallengeResponse: Fail, interrupted while trying to request Icc Sim Auth");
                    return null;
                }
            }
        } catch (Exception e2) {
            loge("getIccSimChallengeResponse: Fail while trying to request Icc Sim Auth");
            return null;
        }
    }

    public String getImsiOnSimLock() {
        return null;
    }

    public IsimRecords getIsimRecords() {
        return null;
    }

    public String getMccMncOnSimLock() {
        return null;
    }

    public String getMsisdnAlphaTag() {
        return this.mMsisdnTag;
    }

    public String getMsisdnNumber() {
        return this.mMsisdn;
    }

    public String getNAI() {
        return null;
    }

    public String getOperatorNumeric() {
        return null;
    }

    public boolean getRecordsLoaded() {
        return this.mRecordsToLoad == 0 && this.mRecordsRequested;
    }

    public String getServiceProviderName() {
        String operatorBrandOverride;
        String str = this.mSpn;
        UiccCardApplication uiccCardApplication = this.mParentApp;
        if (uiccCardApplication != null) {
            UiccCard uiccCard = uiccCardApplication.getUiccCard();
            if (uiccCard != null) {
                operatorBrandOverride = uiccCard.getOperatorBrandOverride();
                if (operatorBrandOverride != null) {
                    log("getServiceProviderName: override");
                } else {
                    log("getServiceProviderName: no brandOverride");
                    operatorBrandOverride = str;
                }
            } else {
                log("getServiceProviderName: card is null");
                operatorBrandOverride = str;
            }
        } else {
            log("getServiceProviderName: mParentApp is null");
            operatorBrandOverride = str;
        }
        log("getServiceProviderName: providerName=" + operatorBrandOverride);
        return operatorBrandOverride;
    }

    public int getSmsCapacityOnIcc() {
        log("getSmsCapacityOnIcc: " + this.mSmsCountOnIcc);
        return this.mSmsCountOnIcc;
    }

    public UsimServiceTable getUsimServiceTable() {
        return null;
    }

    public boolean getVoiceCallForwardingFlag() {
        return false;
    }

    public String getVoiceMailAlphaTag() {
        return this.mVoiceMailTag;
    }

    public String getVoiceMailNumber() {
        return this.mVoiceMailNum;
    }

    public abstract int getVoiceMessageCount();

    public abstract void handleFileUpdate(int i);

    public void handleMessage(Message message) {
        AsyncResult asyncResult;
        switch (message.what) {
            case EVENT_GET_SMS_RECORD_SIZE_DONE /*28*/:
                asyncResult = (AsyncResult) message.obj;
                if (asyncResult.exception != null) {
                    loge("Exception in EVENT_GET_SMS_RECORD_SIZE_DONE " + asyncResult.exception);
                    return;
                }
                int[] iArr = (int[]) asyncResult.result;
                try {
                    this.mSmsCountOnIcc = iArr[2];
                    log("EVENT_GET_SMS_RECORD_SIZE_DONE Size " + iArr[0] + " total " + iArr[1] + " record " + iArr[2]);
                    return;
                } catch (ArrayIndexOutOfBoundsException e) {
                    loge("ArrayIndexOutOfBoundsException in EVENT_GET_SMS_RECORD_SIZE_DONE: " + e.toString());
                    return;
                }
            case EVENT_REFRESH_OEM /*29*/:
                asyncResult = (AsyncResult) message.obj;
                log("Card REFRESH OEM occurred: ");
                if (asyncResult.exception == null) {
                    handleRefreshOem((byte[]) asyncResult.result);
                    return;
                } else {
                    loge("Icc refresh OEM Exception: " + asyncResult.exception);
                    return;
                }
            case 31:
                asyncResult = (AsyncResult) message.obj;
                log("Card REFRESH occurred: ");
                if (asyncResult.exception == null) {
                    broadcastRefresh();
                    handleRefresh((IccRefreshResponse) asyncResult.result);
                    return;
                }
                loge("Icc refresh Exception: " + asyncResult.exception);
                return;
            case EVENT_AKA_AUTHENTICATE_DONE /*90*/:
                asyncResult = (AsyncResult) message.obj;
                this.auth_rsp = null;
                log("EVENT_AKA_AUTHENTICATE_DONE");
                if (asyncResult.exception != null) {
                    loge("Exception ICC SIM AKA: " + asyncResult.exception);
                } else {
                    try {
                        this.auth_rsp = (IccIoResult) asyncResult.result;
                        log("ICC SIM AKA: auth_rsp = " + this.auth_rsp);
                    } catch (Exception e2) {
                        loge("Failed to parse ICC SIM AKA contents: " + e2);
                    }
                }
                synchronized (this.mLock) {
                    try {
                        this.mLock.notifyAll();
                    } catch (Throwable th) {
                        throw th;
                    }
                }
                return;
            case EVENT_GET_ICC_RECORD_DONE /*100*/:
                try {
                    asyncResult = (AsyncResult) message.obj;
                    IccRecordLoaded iccRecordLoaded = (IccRecordLoaded) asyncResult.userObj;
                    log(iccRecordLoaded.getEfName() + " LOADED");
                    if (asyncResult.exception != null) {
                        loge("Record Load Exception: " + asyncResult.exception);
                    } else {
                        iccRecordLoaded.onRecordLoaded(asyncResult);
                    }
                    onRecordLoaded();
                    return;
                } catch (RuntimeException e3) {
                    loge("Exception parsing SIM record: " + e3);
                    onRecordLoaded();
                    return;
                } catch (Throwable th2) {
                    onRecordLoaded();
                    throw th2;
                }
            case 200:
                resetAtNewThread();
                return;
            default:
                super.handleMessage(message);
                return;
        }
    }

    /* Access modifiers changed, original: protected */
    public void handleRefresh(IccRefreshResponse iccRefreshResponse) {
        if (iccRefreshResponse == null) {
            log("handleRefresh received without input");
        } else if (iccRefreshResponse.aid == null || iccRefreshResponse.aid.equals(this.mParentApp.getAid())) {
            switch (iccRefreshResponse.refreshResult) {
                case 0:
                    log("handleRefresh with SIM_FILE_UPDATED");
                    handleFileUpdate(iccRefreshResponse.efId);
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

    /* Access modifiers changed, original: protected */
    public boolean isAppStateReady() {
        AppState state = this.mParentApp.getState();
        log("isAppStateReady : appState = " + state);
        return state == AppState.APPSTATE_READY;
    }

    public boolean isCallForwardStatusStored() {
        return false;
    }

    public boolean isCspPlmnEnabled() {
        return false;
    }

    public boolean isProvisioned() {
        return true;
    }

    public abstract void log(String str);

    public abstract void loge(String str);

    public abstract void onAllRecordsLoaded();

    /* Access modifiers changed, original: protected */
    public void onIccRefreshInit() {
        this.mAdnCache.reset();
        UiccCardApplication uiccCardApplication = this.mParentApp;
        if (uiccCardApplication != null && uiccCardApplication.getState() == AppState.APPSTATE_READY) {
            sendMessage(obtainMessage(1));
        }
    }

    public abstract void onReady();

    public abstract void onRecordLoaded();

    public abstract void onRefresh(boolean z, int[] iArr);

    /* Access modifiers changed, original: protected */
    public boolean powerOffOnSimReset() {
        return !this.mContext.getResources().getBoolean(17956973);
    }

    /* Access modifiers changed, original: 0000 */
    public void recordsRequired() {
    }

    public void registerForImsiReady(Handler handler, int i, Object obj) {
        if (!this.mDestroyed.get()) {
            Registrant registrant = new Registrant(handler, i, obj);
            this.mImsiReadyRegistrants.add(registrant);
            if (this.mImsi == null || !isAppStateReady()) {
                log("registerForImsiReady, not notifying the registrant immediately");
            } else {
                registrant.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    public void registerForNetworkSelectionModeAutomatic(Handler handler, int i, Object obj) {
        this.mNetworkSelectionModeAutomaticRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForNewSms(Handler handler, int i, Object obj) {
        this.mNewSmsRegistrants.add(new Registrant(handler, i, obj));
    }

    public void registerForRecordsEvents(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        this.mRecordsEventsRegistrants.add(registrant);
        registrant.notifyResult(Integer.valueOf(0));
        registrant.notifyResult(Integer.valueOf(1));
    }

    public void registerForRecordsLoaded(Handler handler, int i, Object obj) {
        if (!this.mDestroyed.get()) {
            int size = this.mRecordsLoadedRegistrants.size() - 1;
            while (size >= 0) {
                Handler handler2 = ((Registrant) this.mRecordsLoadedRegistrants.get(size)).getHandler();
                if (handler2 == null || handler2 != handler) {
                    size--;
                } else {
                    return;
                }
            }
            Registrant registrant = new Registrant(handler, i, obj);
            this.mRecordsLoadedRegistrants.add(registrant);
            if (this.mRecordsToLoad == 0 && this.mRecordsRequested && isAppStateReady()) {
                registrant.notifyRegistrant(new AsyncResult(null, null, null));
            } else {
                log("registerForRecordsLoaded, not notifying the registrant immediately");
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public boolean requirePowerOffOnSimRefreshReset() {
        return this.mContext.getResources().getBoolean(17956985);
    }

    public void setImsi(String str) {
        this.mImsi = str;
        this.mImsiReadyRegistrants.notifyRegistrants();
    }

    public void setMsisdnNumber(String str, String str2, Message message) {
        this.mMsisdn = str2;
        this.mMsisdnTag = str;
        log("Set MSISDN: " + this.mMsisdnTag + " " + this.mMsisdn);
        new AdnRecordLoader(this.mFh).updateEF(new AdnRecord(this.mMsisdnTag, this.mMsisdn), IccConstants.EF_MSISDN, IccConstants.EF_EXT1, 1, null, obtainMessage(30, message));
    }

    /* Access modifiers changed, original: protected */
    public void setServiceProviderName(String str) {
        this.mSpn = str;
    }

    /* Access modifiers changed, original: protected */
    public void setSystemProperty(String str, String str2) {
        TelephonyManager.getDefault();
        TelephonyManager.setTelephonyProperty(this.mParentApp.getPhoneId(), str, str2);
        log("[key, value]=" + str + ", " + str2);
    }

    public void setVoiceCallForwardingFlag(int i, boolean z, String str) {
    }

    public abstract void setVoiceMailNumber(String str, String str2, Message message);

    public abstract void setVoiceMessageWaiting(int i, int i2);

    public String toString() {
        return "mDestroyed=" + this.mDestroyed + " mContext=" + this.mContext + " mCi=" + this.mCi + " mFh=" + this.mFh + " mParentApp=" + this.mParentApp + " recordsLoadedRegistrants=" + this.mRecordsLoadedRegistrants + " mImsiReadyRegistrants=" + this.mImsiReadyRegistrants + " mRecordsEventsRegistrants=" + this.mRecordsEventsRegistrants + " mNewSmsRegistrants=" + this.mNewSmsRegistrants + " mNetworkSelectionModeAutomaticRegistrants=" + this.mNetworkSelectionModeAutomaticRegistrants + " recordsToLoad=" + this.mRecordsToLoad + " adnCache=" + this.mAdnCache + " recordsRequested=" + this.mRecordsRequested + " iccid=" + this.mIccId + " msisdnTag=" + this.mMsisdnTag + " voiceMailNum=" + this.mVoiceMailNum + " voiceMailTag=" + this.mVoiceMailTag + " newVoiceMailNum=" + this.mNewVoiceMailNum + " newVoiceMailTag=" + this.mNewVoiceMailTag + " isVoiceMailFixed=" + this.mIsVoiceMailFixed + " mImsi=" + this.mImsi + " mncLength=" + this.mMncLength + " mailboxIndex=" + this.mMailboxIndex + " spn=" + this.mSpn;
    }

    public void unregisterForImsiReady(Handler handler) {
        this.mImsiReadyRegistrants.remove(handler);
    }

    public void unregisterForNetworkSelectionModeAutomatic(Handler handler) {
        this.mNetworkSelectionModeAutomaticRegistrants.remove(handler);
    }

    public void unregisterForNewSms(Handler handler) {
        this.mNewSmsRegistrants.remove(handler);
    }

    public void unregisterForRecordsEvents(Handler handler) {
        this.mRecordsEventsRegistrants.remove(handler);
    }

    public void unregisterForRecordsLoaded(Handler handler) {
        this.mRecordsLoadedRegistrants.remove(handler);
    }
}
