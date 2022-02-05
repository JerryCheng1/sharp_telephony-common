package com.android.internal.telephony.uicc;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class UiccCardApplication {
    public static final int AUTH_CONTEXT_EAP_AKA = 129;
    public static final int AUTH_CONTEXT_EAP_SIM = 128;
    public static final int AUTH_CONTEXT_UNDEFINED = -1;
    private static final boolean DBG = true;
    private static final int DEFAULT_RETRY_COUNT_PIN = 3;
    private static final int DEFAULT_RETRY_COUNT_PUK = 10;
    private static final int EVENT_CHANGE_FACILITY_FDN_DONE = 5;
    private static final int EVENT_CHANGE_FACILITY_LOCK_DONE = 7;
    private static final int EVENT_CHANGE_PIN1_DONE = 2;
    private static final int EVENT_CHANGE_PIN2_DONE = 3;
    private static final int EVENT_CLOSE_CHANNEL_DONE_SHARP = 22;
    private static final int EVENT_EXCHANGE_APDU_DONE_SHARP = 20;
    private static final int EVENT_OPEN_CHANNEL_DONE_SHARP = 21;
    private static final int EVENT_PIN1_DONE_SHARP = 15;
    private static final int EVENT_PIN2_DONE_SHARP = 17;
    private static final int EVENT_PUK1_DONE_SHARP = 16;
    private static final int EVENT_PUK2_DONE_SHARP = 18;
    private static final int EVENT_QUERY_FACILITY_FDN_DONE = 4;
    private static final int EVENT_QUERY_FACILITY_LOCK_DONE = 6;
    private static final int EVENT_RADIO_UNAVAILABLE = 9;
    private static final int EVENT_SIM_IO_DONE_SHARP = 23;
    private static final String LOG_TAG = "UiccCardApplication";
    private String mAid;
    private String mAppLabel;
    private IccCardApplicationStatus.AppState mAppState;
    private IccCardApplicationStatus.AppType mAppType;
    private int mAuthContext;
    private CommandsInterface mCi;
    private Context mContext;
    private boolean mDesiredPinLocked;
    private boolean mDestroyed;
    private boolean mIccFdnEnabled;
    private IccFileHandler mIccFh;
    private boolean mIccLockEnabled;
    private IccRecords mIccRecords;
    private IccCardApplicationStatus.PersoSubState mPersoSubState;
    private boolean mPin1Replaced;
    private IccCardStatus.PinState mPin1State;
    private IccCardStatus.PinState mPin2State;
    private UiccCard mUiccCard;
    private final Object mLock = new Object();
    private boolean mDesiredFdnEnabled = false;
    private boolean mIccFdnAvailable = true;
    private RegistrantList mReadyRegistrants = new RegistrantList();
    private RegistrantList mPinLockedRegistrants = new RegistrantList();
    private RegistrantList mPersoLockedRegistrants = new RegistrantList();
    private Handler mHandler = new Handler() { // from class: com.android.internal.telephony.uicc.UiccCardApplication.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            if (UiccCardApplication.this.mDestroyed) {
                UiccCardApplication.this.loge("Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
                return;
            }
            switch (msg.what) {
                case 2:
                case 3:
                case 15:
                case 16:
                case 17:
                case 18:
                    int attemptsRemaining = -1;
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar.result != null) {
                        attemptsRemaining = UiccCardApplication.this.parsePinPukErrorResult(ar);
                    }
                    UiccCardApplication.this.parsePinPukResultSC(ar, msg.what);
                    Message response = (Message) ar.userObj;
                    AsyncResult.forMessage(response).exception = ar.exception;
                    response.arg1 = attemptsRemaining;
                    response.sendToTarget();
                    return;
                case 4:
                    UiccCardApplication.this.onQueryFdnEnabled((AsyncResult) msg.obj);
                    return;
                case 5:
                    UiccCardApplication.this.onChangeFdnDone((AsyncResult) msg.obj);
                    return;
                case 6:
                    UiccCardApplication.this.onQueryFacilityLock((AsyncResult) msg.obj);
                    return;
                case 7:
                    UiccCardApplication.this.onChangeFacilityLock((AsyncResult) msg.obj);
                    return;
                case 8:
                case 10:
                case 11:
                case 12:
                case 13:
                case 14:
                case 19:
                default:
                    UiccCardApplication.this.loge("Unknown Event " + msg.what);
                    return;
                case 9:
                    UiccCardApplication.this.log("handleMessage (EVENT_RADIO_UNAVAILABLE)");
                    UiccCardApplication.this.mAppState = IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN;
                    return;
                case 20:
                case 21:
                case 22:
                case 23:
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    if (ar2.exception != null) {
                        UiccCardApplication.this.log("Error in SIM access with exception" + ar2.exception);
                    }
                    AsyncResult.forMessage((Message) ar2.userObj, ar2.result, ar2.exception);
                    ((Message) ar2.userObj).sendToTarget();
                    return;
            }
        }
    };

    public UiccCardApplication(UiccCard uiccCard, IccCardApplicationStatus as, Context c, CommandsInterface ci) {
        boolean z = true;
        log("Creating UiccApp: " + as);
        this.mUiccCard = uiccCard;
        this.mAppState = as.app_state;
        this.mAppType = as.app_type;
        this.mAuthContext = getAuthContext(this.mAppType);
        this.mPersoSubState = as.perso_substate;
        this.mAid = as.aid;
        this.mAppLabel = as.app_label;
        this.mPin1Replaced = as.pin1_replaced == 0 ? false : z;
        this.mPin1State = as.pin1;
        this.mPin2State = as.pin2;
        this.mContext = c;
        this.mCi = ci;
        this.mIccFh = createIccFileHandler(as.app_type);
        this.mIccRecords = createIccRecords(as.app_type, this.mContext, this.mCi);
        if (this.mAppState == IccCardApplicationStatus.AppState.APPSTATE_READY) {
            queryFdn();
            queryPin1State();
        }
        this.mCi.registerForNotAvailable(this.mHandler, 9, null);
    }

    public void update(IccCardApplicationStatus as, Context c, CommandsInterface ci) {
        synchronized (this.mLock) {
            if (this.mDestroyed) {
                loge("Application updated after destroyed! Fix me!");
                return;
            }
            log(this.mAppType + " update. New " + as);
            this.mContext = c;
            this.mCi = ci;
            IccCardApplicationStatus.AppType oldAppType = this.mAppType;
            IccCardApplicationStatus.AppState oldAppState = this.mAppState;
            IccCardApplicationStatus.PersoSubState oldPersoSubState = this.mPersoSubState;
            this.mAppType = as.app_type;
            this.mAuthContext = getAuthContext(this.mAppType);
            this.mAppState = as.app_state;
            this.mPersoSubState = as.perso_substate;
            this.mAid = as.aid;
            this.mAppLabel = as.app_label;
            this.mPin1Replaced = as.pin1_replaced != 0;
            if (!TelBrand.IS_DCM) {
                this.mPin1State = as.pin1;
            }
            this.mPin2State = as.pin2;
            if (this.mAppType != oldAppType) {
                if (this.mIccFh != null) {
                    this.mIccFh.dispose();
                }
                if (this.mIccRecords != null) {
                    this.mIccRecords.dispose();
                }
                this.mIccFh = createIccFileHandler(as.app_type);
                this.mIccRecords = createIccRecords(as.app_type, c, ci);
            }
            if (this.mPersoSubState != oldPersoSubState && isPersoLocked()) {
                notifyPersoLockedRegistrantsIfNeeded(null);
            }
            if (TelBrand.IS_DCM) {
                if (!(as.app_state == oldAppState && this.mPin1State == as.pin1)) {
                    log(oldAppType + " changed state: " + oldAppState + " -> " + as.app_state + " Pin1 state: " + this.mPin1State + " -> " + as.pin1);
                    this.mPin1State = as.pin1;
                    if (this.mAppState == IccCardApplicationStatus.AppState.APPSTATE_READY) {
                        queryFdn();
                        queryPin1State();
                    }
                    notifyPinLockedRegistrantsIfNeeded(null);
                    notifyReadyRegistrantsIfNeeded(null);
                }
            } else if (this.mAppState != oldAppState) {
                log(oldAppType + " changed state: " + oldAppState + " -> " + this.mAppState);
                if (this.mAppState == IccCardApplicationStatus.AppState.APPSTATE_READY) {
                    queryFdn();
                    queryPin1State();
                }
                notifyPinLockedRegistrantsIfNeeded(null);
                notifyReadyRegistrantsIfNeeded(null);
            }
        }
    }

    public void dispose() {
        synchronized (this.mLock) {
            log(this.mAppType + " being Disposed");
            this.mDestroyed = true;
            if (this.mIccRecords != null) {
                this.mIccRecords.dispose();
            }
            if (this.mIccFh != null) {
                this.mIccFh.dispose();
            }
            this.mIccRecords = null;
            this.mIccFh = null;
            this.mCi.unregisterForNotAvailable(this.mHandler);
        }
    }

    private IccRecords createIccRecords(IccCardApplicationStatus.AppType type, Context c, CommandsInterface ci) {
        if (type == IccCardApplicationStatus.AppType.APPTYPE_USIM || type == IccCardApplicationStatus.AppType.APPTYPE_SIM) {
            return new SIMRecords(this, c, ci);
        }
        if (type == IccCardApplicationStatus.AppType.APPTYPE_RUIM || type == IccCardApplicationStatus.AppType.APPTYPE_CSIM) {
            return new RuimRecords(this, c, ci);
        }
        if (type == IccCardApplicationStatus.AppType.APPTYPE_ISIM) {
            return new IsimUiccRecords(this, c, ci);
        }
        return null;
    }

    private IccFileHandler createIccFileHandler(IccCardApplicationStatus.AppType type) {
        switch (type) {
            case APPTYPE_SIM:
                return new SIMFileHandler(this, this.mAid, this.mCi);
            case APPTYPE_RUIM:
                return new RuimFileHandler(this, this.mAid, this.mCi);
            case APPTYPE_USIM:
                return new UsimFileHandler(this, this.mAid, this.mCi);
            case APPTYPE_CSIM:
                return new CsimFileHandler(this, this.mAid, this.mCi);
            case APPTYPE_ISIM:
                return new IsimFileHandler(this, this.mAid, this.mCi);
            default:
                return null;
        }
    }

    public void queryFdn() {
        this.mCi.queryFacilityLockForApp(CommandsInterface.CB_FACILITY_BA_FD, "", 7, this.mAid, this.mHandler.obtainMessage(4));
    }

    public void onQueryFdnEnabled(AsyncResult ar) {
        boolean z = false;
        synchronized (this.mLock) {
            if (ar.exception != null) {
                log("Error in querying facility lock:" + ar.exception);
                return;
            }
            int[] result = (int[]) ar.result;
            if (result.length != 0) {
                if (result[0] == 2) {
                    this.mIccFdnEnabled = false;
                    this.mIccFdnAvailable = false;
                } else {
                    if (result[0] == 1) {
                        z = true;
                    }
                    this.mIccFdnEnabled = z;
                    this.mIccFdnAvailable = true;
                }
                log("Query facility FDN : FDN service available: " + this.mIccFdnAvailable + " enabled: " + this.mIccFdnEnabled);
            } else {
                loge("Bogus facility lock response");
            }
        }
    }

    public void onChangeFdnDone(AsyncResult ar) {
        synchronized (this.mLock) {
            int attemptsRemaining = -1;
            if (ar.exception == null) {
                this.mIccFdnEnabled = this.mDesiredFdnEnabled;
                log("EVENT_CHANGE_FACILITY_FDN_DONE: mIccFdnEnabled=" + this.mIccFdnEnabled);
            } else {
                attemptsRemaining = parsePinPukErrorResult(ar);
                loge("Error change facility fdn with exception " + ar.exception);
            }
            parsePinPukResultSC(ar, 5);
            Message response = (Message) ar.userObj;
            response.arg1 = attemptsRemaining;
            AsyncResult.forMessage(response).exception = ar.exception;
            response.sendToTarget();
        }
    }

    private void queryPin1State() {
        this.mCi.queryFacilityLockForApp(CommandsInterface.CB_FACILITY_BA_SIM, "", 7, this.mAid, this.mHandler.obtainMessage(6));
    }

    public void onQueryFacilityLock(AsyncResult ar) {
        synchronized (this.mLock) {
            if (ar.exception != null) {
                log("Error in querying facility lock:" + ar.exception);
                return;
            }
            int[] ints = (int[]) ar.result;
            if (ints.length != 0) {
                log("Query facility lock : " + ints[0]);
                this.mIccLockEnabled = ints[0] != 0;
                if (this.mIccLockEnabled) {
                    this.mPinLockedRegistrants.notifyRegistrants();
                }
                switch (this.mPin1State) {
                    case PINSTATE_DISABLED:
                        if (this.mIccLockEnabled) {
                            loge("QUERY_FACILITY_LOCK:enabled GET_SIM_STATUS.Pin1:disabled. Fixme");
                            break;
                        }
                        break;
                    case PINSTATE_ENABLED_NOT_VERIFIED:
                    case PINSTATE_ENABLED_VERIFIED:
                    case PINSTATE_ENABLED_BLOCKED:
                    case PINSTATE_ENABLED_PERM_BLOCKED:
                        if (!this.mIccLockEnabled) {
                            loge("QUERY_FACILITY_LOCK:disabled GET_SIM_STATUS.Pin1:enabled. Fixme");
                        }
                    default:
                        log("Ignoring: pin1state=" + this.mPin1State);
                        break;
                }
            } else {
                loge("Bogus facility lock response");
            }
        }
    }

    public void onChangeFacilityLock(AsyncResult ar) {
        synchronized (this.mLock) {
            int attemptsRemaining = -1;
            if (ar.exception == null) {
                this.mIccLockEnabled = this.mDesiredPinLocked;
                log("EVENT_CHANGE_FACILITY_LOCK_DONE: mIccLockEnabled= " + this.mIccLockEnabled);
            } else {
                attemptsRemaining = parsePinPukErrorResult(ar);
                loge("Error change facility lock with exception " + ar.exception);
            }
            parsePinPukResultSC(ar, 7);
            Message response = (Message) ar.userObj;
            AsyncResult.forMessage(response).exception = ar.exception;
            response.arg1 = attemptsRemaining;
            response.sendToTarget();
        }
    }

    public int parsePinPukErrorResult(AsyncResult ar) {
        int[] result = (int[]) ar.result;
        if (result == null) {
            return -1;
        }
        int attemptsRemaining = -1;
        if (result.length > 0) {
            attemptsRemaining = result[0];
        }
        log("parsePinPukErrorResult: attemptsRemaining=" + attemptsRemaining);
        return attemptsRemaining;
    }

    public void onRefresh(IccRefreshResponse refreshResponse) {
        if (refreshResponse == null) {
            loge("onRefresh received without input");
        } else if (refreshResponse.aid == null || refreshResponse.aid.equals(this.mAid)) {
            log("refresh for app " + refreshResponse.aid);
            switch (refreshResponse.refreshResult) {
                case 1:
                case 2:
                    log("onRefresh: Setting app state to unknown");
                    this.mAppState = IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN;
                    return;
                default:
                    return;
            }
        }
    }

    public void registerForReady(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            for (int i = this.mReadyRegistrants.size() - 1; i >= 0; i--) {
                Handler rH = ((Registrant) this.mReadyRegistrants.get(i)).getHandler();
                if (rH != null && rH == h) {
                    return;
                }
            }
            Registrant r = new Registrant(h, what, obj);
            this.mReadyRegistrants.add(r);
            notifyReadyRegistrantsIfNeeded(r);
        }
    }

    public void unregisterForReady(Handler h) {
        synchronized (this.mLock) {
            this.mReadyRegistrants.remove(h);
        }
    }

    public void registerForLocked(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mPinLockedRegistrants.add(r);
            notifyPinLockedRegistrantsIfNeeded(r);
        }
    }

    public void unregisterForLocked(Handler h) {
        synchronized (this.mLock) {
            this.mPinLockedRegistrants.remove(h);
        }
    }

    public void registerForPersoLocked(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mPersoLockedRegistrants.add(r);
            notifyPersoLockedRegistrantsIfNeeded(r);
        }
    }

    public void unregisterForPersoLocked(Handler h) {
        synchronized (this.mLock) {
            this.mPersoLockedRegistrants.remove(h);
        }
    }

    private void notifyReadyRegistrantsIfNeeded(Registrant r) {
        if (this.mDestroyed || this.mAppState != IccCardApplicationStatus.AppState.APPSTATE_READY) {
            return;
        }
        if (this.mPin1State == IccCardStatus.PinState.PINSTATE_ENABLED_NOT_VERIFIED || this.mPin1State == IccCardStatus.PinState.PINSTATE_ENABLED_BLOCKED || this.mPin1State == IccCardStatus.PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
            loge("Sanity check failed! APPSTATE is ready while PIN1 is not verified!!!");
        } else if (r == null) {
            log("Notifying registrants: READY");
            this.mReadyRegistrants.notifyRegistrants();
        } else {
            log("Notifying 1 registrant: READY");
            r.notifyRegistrant(new AsyncResult((Object) null, (Object) null, (Throwable) null));
        }
    }

    private void notifyPinLockedRegistrantsIfNeeded(Registrant r) {
        if (!this.mDestroyed) {
            if (this.mAppState != IccCardApplicationStatus.AppState.APPSTATE_PIN && this.mAppState != IccCardApplicationStatus.AppState.APPSTATE_PUK) {
                return;
            }
            if (this.mPin1State == IccCardStatus.PinState.PINSTATE_ENABLED_VERIFIED || this.mPin1State == IccCardStatus.PinState.PINSTATE_DISABLED) {
                loge("Sanity check failed! APPSTATE is locked while PIN1 is not!!!");
            } else if (r == null) {
                log("Notifying registrants: LOCKED");
                this.mPinLockedRegistrants.notifyRegistrants();
            } else {
                log("Notifying 1 registrant: LOCKED");
                r.notifyRegistrant(new AsyncResult((Object) null, (Object) null, (Throwable) null));
            }
        }
    }

    private void notifyPersoLockedRegistrantsIfNeeded(Registrant r) {
        if (!this.mDestroyed && this.mAppState == IccCardApplicationStatus.AppState.APPSTATE_SUBSCRIPTION_PERSO && isPersoLocked()) {
            AsyncResult ar = new AsyncResult((Object) null, Integer.valueOf(this.mPersoSubState.ordinal()), (Throwable) null);
            if (r == null) {
                log("Notifying registrants: PERSO_LOCKED");
                this.mPersoLockedRegistrants.notifyRegistrants(ar);
                return;
            }
            log("Notifying 1 registrant: PERSO_LOCKED");
            r.notifyRegistrant(ar);
        }
    }

    public IccCardApplicationStatus.AppState getState() {
        IccCardApplicationStatus.AppState appState;
        synchronized (this.mLock) {
            appState = this.mAppState;
        }
        return appState;
    }

    public IccCardApplicationStatus.AppType getType() {
        IccCardApplicationStatus.AppType appType;
        synchronized (this.mLock) {
            appType = this.mAppType;
        }
        return appType;
    }

    public int getAuthContext() {
        int i;
        synchronized (this.mLock) {
            i = this.mAuthContext;
        }
        return i;
    }

    private static int getAuthContext(IccCardApplicationStatus.AppType appType) {
        switch (appType) {
            case APPTYPE_SIM:
                return 128;
            case APPTYPE_RUIM:
            default:
                return -1;
            case APPTYPE_USIM:
                return 129;
        }
    }

    public IccCardApplicationStatus.PersoSubState getPersoSubState() {
        IccCardApplicationStatus.PersoSubState persoSubState;
        synchronized (this.mLock) {
            persoSubState = this.mPersoSubState;
        }
        return persoSubState;
    }

    public String getAid() {
        String str;
        synchronized (this.mLock) {
            str = this.mAid;
        }
        return str;
    }

    public String getAppLabel() {
        return this.mAppLabel;
    }

    public IccCardStatus.PinState getPin1State() {
        IccCardStatus.PinState universalPinState;
        synchronized (this.mLock) {
            universalPinState = this.mPin1Replaced ? this.mUiccCard.getUniversalPinState() : this.mPin1State;
        }
        return universalPinState;
    }

    public IccFileHandler getIccFileHandler() {
        IccFileHandler iccFileHandler;
        synchronized (this.mLock) {
            iccFileHandler = this.mIccFh;
        }
        return iccFileHandler;
    }

    public IccRecords getIccRecords() {
        IccRecords iccRecords;
        synchronized (this.mLock) {
            iccRecords = this.mIccRecords;
        }
        return iccRecords;
    }

    public boolean isPersoLocked() {
        boolean z;
        synchronized (this.mLock) {
            switch (this.mPersoSubState) {
                case PERSOSUBSTATE_UNKNOWN:
                case PERSOSUBSTATE_IN_PROGRESS:
                case PERSOSUBSTATE_READY:
                    z = false;
                    break;
                default:
                    z = true;
                    break;
            }
        }
        return z;
    }

    public void supplyPin(String pin, Message onComplete) {
        synchronized (this.mLock) {
            this.mCi.supplyIccPinForApp(pin, this.mAid, this.mHandler.obtainMessage(15, onComplete));
        }
    }

    public void supplyPuk(String puk, String newPin, Message onComplete) {
        synchronized (this.mLock) {
            this.mCi.supplyIccPukForApp(puk, newPin, this.mAid, this.mHandler.obtainMessage(16, onComplete));
        }
    }

    public void supplyPin2(String pin2, Message onComplete) {
        synchronized (this.mLock) {
            this.mCi.supplyIccPin2ForApp(pin2, this.mAid, this.mHandler.obtainMessage(17, onComplete));
        }
    }

    public void supplyPuk2(String puk2, String newPin2, Message onComplete) {
        synchronized (this.mLock) {
            this.mCi.supplyIccPuk2ForApp(puk2, newPin2, this.mAid, this.mHandler.obtainMessage(18, onComplete));
        }
    }

    public void supplyDepersonalization(String pin, String type, Message onComplete) {
        synchronized (this.mLock) {
            log("Network Despersonalization: pin = **** , type = " + type);
            this.mCi.supplyDepersonalization(pin, type, onComplete);
        }
    }

    public boolean getIccLockEnabled() {
        return this.mIccLockEnabled;
    }

    public boolean getIccFdnEnabled() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mIccFdnEnabled;
        }
        return z;
    }

    public boolean getIccFdnAvailable() {
        boolean z;
        synchronized (this.mLock) {
            z = (this.mAppState != IccCardApplicationStatus.AppState.APPSTATE_SUBSCRIPTION_PERSO || !isPersoLocked()) ? this.mIccFdnAvailable : false;
        }
        return z;
    }

    public void setIccLockEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (this.mLock) {
            this.mDesiredPinLocked = enabled;
            this.mCi.setFacilityLockForApp(CommandsInterface.CB_FACILITY_BA_SIM, enabled, password, 7, this.mAid, this.mHandler.obtainMessage(7, onComplete));
        }
    }

    public void setIccFdnEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (this.mLock) {
            this.mDesiredFdnEnabled = enabled;
            this.mCi.setFacilityLockForApp(CommandsInterface.CB_FACILITY_BA_FD, enabled, password, 15, this.mAid, this.mHandler.obtainMessage(5, onComplete));
        }
    }

    public void changeIccLockPassword(String oldPassword, String newPassword, Message onComplete) {
        synchronized (this.mLock) {
            log("changeIccLockPassword");
            this.mCi.changeIccPinForApp(oldPassword, newPassword, this.mAid, this.mHandler.obtainMessage(2, onComplete));
        }
    }

    public void changeIccFdnPassword(String oldPassword, String newPassword, Message onComplete) {
        synchronized (this.mLock) {
            log("changeIccFdnPassword");
            this.mCi.changeIccPin2ForApp(oldPassword, newPassword, this.mAid, this.mHandler.obtainMessage(3, onComplete));
        }
    }

    public boolean getIccPin2Blocked() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mPin2State == IccCardStatus.PinState.PINSTATE_ENABLED_BLOCKED;
        }
        return z;
    }

    public boolean getIccPuk2Blocked() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mPin2State == IccCardStatus.PinState.PINSTATE_ENABLED_PERM_BLOCKED;
        }
        return z;
    }

    public int getPhoneId() {
        return this.mUiccCard.getPhoneId();
    }

    public UiccCard getUiccCard() {
        return this.mUiccCard;
    }

    public void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    public void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("UiccCardApplication: " + this);
        pw.println(" mUiccCard=" + this.mUiccCard);
        pw.println(" mAppState=" + this.mAppState);
        pw.println(" mAppType=" + this.mAppType);
        pw.println(" mPersoSubState=" + this.mPersoSubState);
        pw.println(" mAid=" + this.mAid);
        pw.println(" mAppLabel=" + this.mAppLabel);
        pw.println(" mPin1Replaced=" + this.mPin1Replaced);
        pw.println(" mPin1State=" + this.mPin1State);
        pw.println(" mPin2State=" + this.mPin2State);
        pw.println(" mIccFdnEnabled=" + this.mIccFdnEnabled);
        pw.println(" mDesiredFdnEnabled=" + this.mDesiredFdnEnabled);
        pw.println(" mIccLockEnabled=" + this.mIccLockEnabled);
        pw.println(" mDesiredPinLocked=" + this.mDesiredPinLocked);
        pw.println(" mCi=" + this.mCi);
        pw.println(" mIccRecords=" + this.mIccRecords);
        pw.println(" mIccFh=" + this.mIccFh);
        pw.println(" mDestroyed=" + this.mDestroyed);
        pw.println(" mReadyRegistrants: size=" + this.mReadyRegistrants.size());
        for (int i = 0; i < this.mReadyRegistrants.size(); i++) {
            pw.println("  mReadyRegistrants[" + i + "]=" + ((Registrant) this.mReadyRegistrants.get(i)).getHandler());
        }
        pw.println(" mPinLockedRegistrants: size=" + this.mPinLockedRegistrants.size());
        for (int i2 = 0; i2 < this.mPinLockedRegistrants.size(); i2++) {
            pw.println("  mPinLockedRegistrants[" + i2 + "]=" + ((Registrant) this.mPinLockedRegistrants.get(i2)).getHandler());
        }
        pw.println(" mPersoLockedRegistrants: size=" + this.mPersoLockedRegistrants.size());
        for (int i3 = 0; i3 < this.mPersoLockedRegistrants.size(); i3++) {
            pw.println("  mPersoLockedRegistrants[" + i3 + "]=" + ((Registrant) this.mPersoLockedRegistrants.get(i3)).getHandler());
        }
        pw.flush();
    }

    public void parsePinPukResultSC(AsyncResult ar, int isEventPinPuk) {
        int[] intArray = (int[]) ar.result;
        int requestTarget = -1;
        if (ar.exception == null || !(intArray == null || intArray.length == 0)) {
            switch (isEventPinPuk) {
                case 2:
                case 7:
                case 15:
                    requestTarget = 1;
                    break;
                case 3:
                case 5:
                case 17:
                    requestTarget = 3;
                    break;
                case 16:
                    requestTarget = 2;
                    break;
                case 18:
                    requestTarget = 4;
                    break;
            }
            if (ar.exception != null) {
                this.mUiccCard.setPinPukRetryCount(requestTarget, intArray[0]);
                return;
            }
            switch (requestTarget) {
                case 1:
                    this.mUiccCard.setPinPukRetryCount(1, 3);
                    return;
                case 2:
                    this.mUiccCard.setPinPukRetryCount(1, 3);
                    this.mUiccCard.setPinPukRetryCount(2, 10);
                    return;
                case 3:
                    this.mUiccCard.setPinPukRetryCount(3, 3);
                    return;
                case 4:
                    this.mUiccCard.setPinPukRetryCount(3, 3);
                    this.mUiccCard.setPinPukRetryCount(4, 10);
                    return;
                default:
                    return;
            }
        } else {
            loge("Get retry count result is null or blank.");
        }
    }

    public int clearPin2RetryCount() {
        int Pin2RetryCount;
        synchronized (this.mLock) {
            Pin2RetryCount = this.mUiccCard.getIccPinPukRetryCountSc(3);
            if (Pin2RetryCount >= 0) {
                this.mUiccCard.setPinPukRetryCount(3, 3);
                Pin2RetryCount = this.mUiccCard.getIccPinPukRetryCountSc(3);
                log("Set mPin2RetryCountSc=" + Pin2RetryCount);
            }
        }
        return Pin2RetryCount;
    }

    public int decrementPin2RetryCount() {
        int Pin2RetryCount;
        synchronized (this.mLock) {
            Pin2RetryCount = this.mUiccCard.getIccPinPukRetryCountSc(3);
            if (Pin2RetryCount > 0) {
                this.mUiccCard.setPinPukRetryCount(3, Pin2RetryCount - 1);
                Pin2RetryCount = this.mUiccCard.getIccPinPukRetryCountSc(3);
                log("Set mPin2RetryCountSc=" + Pin2RetryCount);
            }
        }
        return Pin2RetryCount;
    }

    public void exchangeAPDU(int cla, int command, int channel, int p1, int p2, int p3, String data, Message onComplete) {
        this.mCi.iccExchangeAPDU(cla, command, channel, p1, p2, p3, data, this.mHandler.obtainMessage(20, onComplete));
    }

    public void openLogicalChannel(String AID, Message onComplete) {
        if (TextUtils.isEmpty(AID)) {
            AID = "";
        }
        this.mCi.iccOpenChannel(AID, this.mHandler.obtainMessage(21, onComplete));
    }

    public void closeLogicalChannel(int channel, Message onComplete) {
        this.mCi.iccCloseChannel(channel, this.mHandler.obtainMessage(22, onComplete));
    }

    public void exchangeSimIO(int fileID, int command, int p1, int p2, int p3, String pathID, Message onComplete) {
        this.mCi.iccIO(command, fileID, pathID, p1, p2, p3, null, null, this.mHandler.obtainMessage(23, onComplete));
    }
}
