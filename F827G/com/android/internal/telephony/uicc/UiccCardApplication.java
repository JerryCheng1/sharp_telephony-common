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
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.PersoSubState;
import com.android.internal.telephony.uicc.IccCardStatus.PinState;
import java.io.FileDescriptor;
import java.io.PrintWriter;

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
    private AppState mAppState;
    private AppType mAppType;
    private int mAuthContext;
    private CommandsInterface mCi;
    private Context mContext;
    private boolean mDesiredFdnEnabled = false;
    private boolean mDesiredPinLocked;
    private boolean mDestroyed;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message message) {
            if (UiccCardApplication.this.mDestroyed) {
                UiccCardApplication.this.loge("Received message " + message + "[" + message.what + "] while being destroyed. Ignoring.");
                return;
            }
            AsyncResult asyncResult;
            switch (message.what) {
                case 2:
                case 3:
                case 15:
                case 16:
                case 17:
                case 18:
                    asyncResult = (AsyncResult) message.obj;
                    int access$200 = asyncResult.result != null ? UiccCardApplication.this.parsePinPukErrorResult(asyncResult) : -1;
                    UiccCardApplication.this.parsePinPukResultSC(asyncResult, message.what);
                    Message message2 = (Message) asyncResult.userObj;
                    AsyncResult.forMessage(message2).exception = asyncResult.exception;
                    message2.arg1 = access$200;
                    message2.sendToTarget();
                    return;
                case 4:
                    UiccCardApplication.this.onQueryFdnEnabled((AsyncResult) message.obj);
                    return;
                case 5:
                    UiccCardApplication.this.onChangeFdnDone((AsyncResult) message.obj);
                    return;
                case 6:
                    UiccCardApplication.this.onQueryFacilityLock((AsyncResult) message.obj);
                    return;
                case 7:
                    UiccCardApplication.this.onChangeFacilityLock((AsyncResult) message.obj);
                    return;
                case 9:
                    UiccCardApplication.this.log("handleMessage (EVENT_RADIO_UNAVAILABLE)");
                    UiccCardApplication.this.mAppState = AppState.APPSTATE_UNKNOWN;
                    return;
                case 20:
                case 21:
                case 22:
                case 23:
                    asyncResult = (AsyncResult) message.obj;
                    if (asyncResult.exception != null) {
                        UiccCardApplication.this.log("Error in SIM access with exception" + asyncResult.exception);
                    }
                    AsyncResult.forMessage((Message) asyncResult.userObj, asyncResult.result, asyncResult.exception);
                    ((Message) asyncResult.userObj).sendToTarget();
                    return;
                default:
                    UiccCardApplication.this.loge("Unknown Event " + message.what);
                    return;
            }
        }
    };
    private boolean mIccFdnAvailable = true;
    private boolean mIccFdnEnabled;
    private IccFileHandler mIccFh;
    private boolean mIccLockEnabled;
    private IccRecords mIccRecords;
    private final Object mLock = new Object();
    private RegistrantList mPersoLockedRegistrants = new RegistrantList();
    private PersoSubState mPersoSubState;
    private boolean mPin1Replaced;
    private PinState mPin1State;
    private PinState mPin2State;
    private RegistrantList mPinLockedRegistrants = new RegistrantList();
    private RegistrantList mReadyRegistrants = new RegistrantList();
    private UiccCard mUiccCard;

    UiccCardApplication(UiccCard uiccCard, IccCardApplicationStatus iccCardApplicationStatus, Context context, CommandsInterface commandsInterface) {
        boolean z = false;
        log("Creating UiccApp: " + iccCardApplicationStatus);
        this.mUiccCard = uiccCard;
        this.mAppState = iccCardApplicationStatus.app_state;
        this.mAppType = iccCardApplicationStatus.app_type;
        this.mAuthContext = getAuthContext(this.mAppType);
        this.mPersoSubState = iccCardApplicationStatus.perso_substate;
        this.mAid = iccCardApplicationStatus.aid;
        this.mAppLabel = iccCardApplicationStatus.app_label;
        if (iccCardApplicationStatus.pin1_replaced != 0) {
            z = true;
        }
        this.mPin1Replaced = z;
        this.mPin1State = iccCardApplicationStatus.pin1;
        this.mPin2State = iccCardApplicationStatus.pin2;
        this.mContext = context;
        this.mCi = commandsInterface;
        this.mIccFh = createIccFileHandler(iccCardApplicationStatus.app_type);
        this.mIccRecords = createIccRecords(iccCardApplicationStatus.app_type, this.mContext, this.mCi);
        if (this.mAppState == AppState.APPSTATE_READY) {
            queryFdn();
            queryPin1State();
        }
        this.mCi.registerForNotAvailable(this.mHandler, 9, null);
    }

    private IccFileHandler createIccFileHandler(AppType appType) {
        switch (appType) {
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

    private IccRecords createIccRecords(AppType appType, Context context, CommandsInterface commandsInterface) {
        return (appType == AppType.APPTYPE_USIM || appType == AppType.APPTYPE_SIM) ? new SIMRecords(this, context, commandsInterface) : (appType == AppType.APPTYPE_RUIM || appType == AppType.APPTYPE_CSIM) ? new RuimRecords(this, context, commandsInterface) : appType == AppType.APPTYPE_ISIM ? new IsimUiccRecords(this, context, commandsInterface) : null;
    }

    private static int getAuthContext(AppType appType) {
        switch (appType) {
            case APPTYPE_SIM:
                return 128;
            case APPTYPE_USIM:
                return 129;
            default:
                return -1;
        }
    }

    private void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    private void loge(String str) {
        Rlog.e(LOG_TAG, str);
    }

    private void notifyPersoLockedRegistrantsIfNeeded(Registrant registrant) {
        if (!this.mDestroyed && this.mAppState == AppState.APPSTATE_SUBSCRIPTION_PERSO && isPersoLocked()) {
            AsyncResult asyncResult = new AsyncResult(null, Integer.valueOf(this.mPersoSubState.ordinal()), null);
            if (registrant == null) {
                log("Notifying registrants: PERSO_LOCKED");
                this.mPersoLockedRegistrants.notifyRegistrants(asyncResult);
                return;
            }
            log("Notifying 1 registrant: PERSO_LOCKED");
            registrant.notifyRegistrant(asyncResult);
        }
    }

    private void notifyPinLockedRegistrantsIfNeeded(Registrant registrant) {
        if (!this.mDestroyed) {
            if (this.mAppState != AppState.APPSTATE_PIN && this.mAppState != AppState.APPSTATE_PUK) {
                return;
            }
            if (this.mPin1State == PinState.PINSTATE_ENABLED_VERIFIED || this.mPin1State == PinState.PINSTATE_DISABLED) {
                loge("Sanity check failed! APPSTATE is locked while PIN1 is not!!!");
            } else if (registrant == null) {
                log("Notifying registrants: LOCKED");
                this.mPinLockedRegistrants.notifyRegistrants();
            } else {
                log("Notifying 1 registrant: LOCKED");
                registrant.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    private void notifyReadyRegistrantsIfNeeded(Registrant registrant) {
        if (this.mDestroyed || this.mAppState != AppState.APPSTATE_READY) {
            return;
        }
        if (this.mPin1State == PinState.PINSTATE_ENABLED_NOT_VERIFIED || this.mPin1State == PinState.PINSTATE_ENABLED_BLOCKED || this.mPin1State == PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
            loge("Sanity check failed! APPSTATE is ready while PIN1 is not verified!!!");
        } else if (registrant == null) {
            log("Notifying registrants: READY");
            this.mReadyRegistrants.notifyRegistrants();
        } else {
            log("Notifying 1 registrant: READY");
            registrant.notifyRegistrant(new AsyncResult(null, null, null));
        }
    }

    private void onChangeFacilityLock(AsyncResult asyncResult) {
        synchronized (this.mLock) {
            int i;
            if (asyncResult.exception == null) {
                this.mIccLockEnabled = this.mDesiredPinLocked;
                log("EVENT_CHANGE_FACILITY_LOCK_DONE: mIccLockEnabled= " + this.mIccLockEnabled);
                i = -1;
            } else {
                int parsePinPukErrorResult = parsePinPukErrorResult(asyncResult);
                loge("Error change facility lock with exception " + asyncResult.exception);
                i = parsePinPukErrorResult;
            }
            parsePinPukResultSC(asyncResult, 7);
            Message message = (Message) asyncResult.userObj;
            AsyncResult.forMessage(message).exception = asyncResult.exception;
            message.arg1 = i;
            message.sendToTarget();
        }
    }

    private void onChangeFdnDone(AsyncResult asyncResult) {
        synchronized (this.mLock) {
            int i;
            if (asyncResult.exception == null) {
                this.mIccFdnEnabled = this.mDesiredFdnEnabled;
                log("EVENT_CHANGE_FACILITY_FDN_DONE: mIccFdnEnabled=" + this.mIccFdnEnabled);
                i = -1;
            } else {
                int parsePinPukErrorResult = parsePinPukErrorResult(asyncResult);
                loge("Error change facility fdn with exception " + asyncResult.exception);
                i = parsePinPukErrorResult;
            }
            parsePinPukResultSC(asyncResult, 5);
            Message message = (Message) asyncResult.userObj;
            message.arg1 = i;
            AsyncResult.forMessage(message).exception = asyncResult.exception;
            message.sendToTarget();
        }
    }

    /* JADX WARNING: Missing block: B:35:?, code skipped:
            return;
     */
    private void onQueryFacilityLock(android.os.AsyncResult r6) {
        /*
        r5 = this;
        r1 = 0;
        r2 = r5.mLock;
        monitor-enter(r2);
        r0 = r6.exception;	 Catch:{ all -> 0x007b }
        if (r0 == 0) goto L_0x0022;
    L_0x0008:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x007b }
        r0.<init>();	 Catch:{ all -> 0x007b }
        r1 = "Error in querying facility lock:";
        r0 = r0.append(r1);	 Catch:{ all -> 0x007b }
        r1 = r6.exception;	 Catch:{ all -> 0x007b }
        r0 = r0.append(r1);	 Catch:{ all -> 0x007b }
        r0 = r0.toString();	 Catch:{ all -> 0x007b }
        r5.log(r0);	 Catch:{ all -> 0x007b }
        monitor-exit(r2);	 Catch:{ all -> 0x007b }
    L_0x0021:
        return;
    L_0x0022:
        r0 = r6.result;	 Catch:{ all -> 0x007b }
        r0 = (int[]) r0;	 Catch:{ all -> 0x007b }
        r0 = (int[]) r0;	 Catch:{ all -> 0x007b }
        r3 = r0.length;	 Catch:{ all -> 0x007b }
        if (r3 == 0) goto L_0x0094;
    L_0x002b:
        r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x007b }
        r3.<init>();	 Catch:{ all -> 0x007b }
        r4 = "Query facility lock : ";
        r3 = r3.append(r4);	 Catch:{ all -> 0x007b }
        r4 = 0;
        r4 = r0[r4];	 Catch:{ all -> 0x007b }
        r3 = r3.append(r4);	 Catch:{ all -> 0x007b }
        r3 = r3.toString();	 Catch:{ all -> 0x007b }
        r5.log(r3);	 Catch:{ all -> 0x007b }
        r0 = r0[r1];
        if (r0 == 0) goto L_0x007e;
    L_0x0048:
        r0 = 1;
    L_0x0049:
        r5.mIccLockEnabled = r0;	 Catch:{ all -> 0x007b }
        r0 = r5.mIccLockEnabled;	 Catch:{ all -> 0x007b }
        if (r0 == 0) goto L_0x0054;
    L_0x004f:
        r0 = r5.mPinLockedRegistrants;	 Catch:{ all -> 0x007b }
        r0.notifyRegistrants();	 Catch:{ all -> 0x007b }
    L_0x0054:
        r0 = com.android.internal.telephony.uicc.UiccCardApplication.AnonymousClass2.$SwitchMap$com$android$internal$telephony$uicc$IccCardStatus$PinState;	 Catch:{ all -> 0x007b }
        r1 = r5.mPin1State;	 Catch:{ all -> 0x007b }
        r1 = r1.ordinal();	 Catch:{ all -> 0x007b }
        r0 = r0[r1];	 Catch:{ all -> 0x007b }
        switch(r0) {
            case 1: goto L_0x0080;
            case 2: goto L_0x008a;
            case 3: goto L_0x008a;
            case 4: goto L_0x008a;
            case 5: goto L_0x008a;
            default: goto L_0x0061;
        };	 Catch:{ all -> 0x007b }
    L_0x0061:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x007b }
        r0.<init>();	 Catch:{ all -> 0x007b }
        r1 = "Ignoring: pin1state=";
        r0 = r0.append(r1);	 Catch:{ all -> 0x007b }
        r1 = r5.mPin1State;	 Catch:{ all -> 0x007b }
        r0 = r0.append(r1);	 Catch:{ all -> 0x007b }
        r0 = r0.toString();	 Catch:{ all -> 0x007b }
        r5.log(r0);	 Catch:{ all -> 0x007b }
    L_0x0079:
        monitor-exit(r2);	 Catch:{ all -> 0x007b }
        goto L_0x0021;
    L_0x007b:
        r0 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x007b }
        throw r0;
    L_0x007e:
        r0 = r1;
        goto L_0x0049;
    L_0x0080:
        r0 = r5.mIccLockEnabled;	 Catch:{ all -> 0x007b }
        if (r0 == 0) goto L_0x0079;
    L_0x0084:
        r0 = "QUERY_FACILITY_LOCK:enabled GET_SIM_STATUS.Pin1:disabled. Fixme";
        r5.loge(r0);	 Catch:{ all -> 0x007b }
        goto L_0x0079;
    L_0x008a:
        r0 = r5.mIccLockEnabled;	 Catch:{ all -> 0x007b }
        if (r0 != 0) goto L_0x0061;
    L_0x008e:
        r0 = "QUERY_FACILITY_LOCK:disabled GET_SIM_STATUS.Pin1:enabled. Fixme";
        r5.loge(r0);	 Catch:{ all -> 0x007b }
        goto L_0x0061;
    L_0x0094:
        r0 = "Bogus facility lock response";
        r5.loge(r0);	 Catch:{ all -> 0x007b }
        goto L_0x0079;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.UiccCardApplication.onQueryFacilityLock(android.os.AsyncResult):void");
    }

    /* JADX WARNING: Missing block: B:28:?, code skipped:
            return;
     */
    private void onQueryFdnEnabled(android.os.AsyncResult r7) {
        /*
        r6 = this;
        r1 = 1;
        r2 = 0;
        r3 = r6.mLock;
        monitor-enter(r3);
        r0 = r7.exception;	 Catch:{ all -> 0x005d }
        if (r0 == 0) goto L_0x0023;
    L_0x0009:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x005d }
        r0.<init>();	 Catch:{ all -> 0x005d }
        r1 = "Error in querying facility lock:";
        r0 = r0.append(r1);	 Catch:{ all -> 0x005d }
        r1 = r7.exception;	 Catch:{ all -> 0x005d }
        r0 = r0.append(r1);	 Catch:{ all -> 0x005d }
        r0 = r0.toString();	 Catch:{ all -> 0x005d }
        r6.log(r0);	 Catch:{ all -> 0x005d }
        monitor-exit(r3);	 Catch:{ all -> 0x005d }
    L_0x0022:
        return;
    L_0x0023:
        r0 = r7.result;	 Catch:{ all -> 0x005d }
        r0 = (int[]) r0;	 Catch:{ all -> 0x005d }
        r0 = (int[]) r0;	 Catch:{ all -> 0x005d }
        r4 = r0.length;	 Catch:{ all -> 0x005d }
        if (r4 == 0) goto L_0x006b;
    L_0x002c:
        r4 = r0[r2];
        r5 = 2;
        if (r4 != r5) goto L_0x0060;
    L_0x0031:
        r0 = 0;
        r6.mIccFdnEnabled = r0;	 Catch:{ all -> 0x005d }
        r0 = 0;
        r6.mIccFdnAvailable = r0;	 Catch:{ all -> 0x005d }
    L_0x0037:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x005d }
        r0.<init>();	 Catch:{ all -> 0x005d }
        r1 = "Query facility FDN : FDN service available: ";
        r0 = r0.append(r1);	 Catch:{ all -> 0x005d }
        r1 = r6.mIccFdnAvailable;	 Catch:{ all -> 0x005d }
        r0 = r0.append(r1);	 Catch:{ all -> 0x005d }
        r1 = " enabled: ";
        r0 = r0.append(r1);	 Catch:{ all -> 0x005d }
        r1 = r6.mIccFdnEnabled;	 Catch:{ all -> 0x005d }
        r0 = r0.append(r1);	 Catch:{ all -> 0x005d }
        r0 = r0.toString();	 Catch:{ all -> 0x005d }
        r6.log(r0);	 Catch:{ all -> 0x005d }
    L_0x005b:
        monitor-exit(r3);	 Catch:{ all -> 0x005d }
        goto L_0x0022;
    L_0x005d:
        r0 = move-exception;
        monitor-exit(r3);	 Catch:{ all -> 0x005d }
        throw r0;
    L_0x0060:
        r0 = r0[r2];
        if (r0 != r1) goto L_0x0071;
    L_0x0064:
        r0 = r1;
    L_0x0065:
        r6.mIccFdnEnabled = r0;	 Catch:{ all -> 0x005d }
        r0 = 1;
        r6.mIccFdnAvailable = r0;	 Catch:{ all -> 0x005d }
        goto L_0x0037;
    L_0x006b:
        r0 = "Bogus facility lock response";
        r6.loge(r0);	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x0071:
        r0 = r2;
        goto L_0x0065;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.UiccCardApplication.onQueryFdnEnabled(android.os.AsyncResult):void");
    }

    private int parsePinPukErrorResult(AsyncResult asyncResult) {
        int[] iArr = (int[]) asyncResult.result;
        if (iArr == null) {
            return -1;
        }
        int i = iArr.length > 0 ? iArr[0] : -1;
        log("parsePinPukErrorResult: attemptsRemaining=" + i);
        return i;
    }

    private void parsePinPukResultSC(AsyncResult asyncResult, int i) {
        int[] iArr = (int[]) asyncResult.result;
        int i2 = -1;
        if (asyncResult.exception == null || !(iArr == null || iArr.length == 0)) {
            switch (i) {
                case 2:
                case 7:
                case 15:
                    i2 = 1;
                    break;
                case 3:
                case 5:
                case 17:
                    i2 = 3;
                    break;
                case 16:
                    i2 = 2;
                    break;
                case 18:
                    i2 = 4;
                    break;
            }
            if (asyncResult.exception != null) {
                this.mUiccCard.setPinPukRetryCount(i2, iArr[0]);
                return;
            }
            switch (i2) {
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
        }
        loge("Get retry count result is null or blank.");
    }

    private void queryPin1State() {
        this.mCi.queryFacilityLockForApp(CommandsInterface.CB_FACILITY_BA_SIM, "", 7, this.mAid, this.mHandler.obtainMessage(6));
    }

    public void changeIccFdnPassword(String str, String str2, Message message) {
        synchronized (this.mLock) {
            log("changeIccFdnPassword");
            this.mCi.changeIccPin2ForApp(str, str2, this.mAid, this.mHandler.obtainMessage(3, message));
        }
    }

    public void changeIccLockPassword(String str, String str2, Message message) {
        synchronized (this.mLock) {
            log("changeIccLockPassword");
            this.mCi.changeIccPinForApp(str, str2, this.mAid, this.mHandler.obtainMessage(2, message));
        }
    }

    public int clearPin2RetryCount() {
        int iccPinPukRetryCountSc;
        synchronized (this.mLock) {
            iccPinPukRetryCountSc = this.mUiccCard.getIccPinPukRetryCountSc(3);
            if (iccPinPukRetryCountSc >= 0) {
                this.mUiccCard.setPinPukRetryCount(3, 3);
                iccPinPukRetryCountSc = this.mUiccCard.getIccPinPukRetryCountSc(3);
                log("Set mPin2RetryCountSc=" + iccPinPukRetryCountSc);
            }
        }
        return iccPinPukRetryCountSc;
    }

    public void closeLogicalChannel(int i, Message message) {
        this.mCi.iccCloseChannel(i, this.mHandler.obtainMessage(22, message));
    }

    public int decrementPin2RetryCount() {
        int iccPinPukRetryCountSc;
        synchronized (this.mLock) {
            iccPinPukRetryCountSc = this.mUiccCard.getIccPinPukRetryCountSc(3);
            if (iccPinPukRetryCountSc > 0) {
                this.mUiccCard.setPinPukRetryCount(3, iccPinPukRetryCountSc - 1);
                iccPinPukRetryCountSc = this.mUiccCard.getIccPinPukRetryCountSc(3);
                log("Set mPin2RetryCountSc=" + iccPinPukRetryCountSc);
            }
        }
        return iccPinPukRetryCountSc;
    }

    /* Access modifiers changed, original: 0000 */
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

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        int i;
        int i2 = 0;
        printWriter.println("UiccCardApplication: " + this);
        printWriter.println(" mUiccCard=" + this.mUiccCard);
        printWriter.println(" mAppState=" + this.mAppState);
        printWriter.println(" mAppType=" + this.mAppType);
        printWriter.println(" mPersoSubState=" + this.mPersoSubState);
        printWriter.println(" mAid=" + this.mAid);
        printWriter.println(" mAppLabel=" + this.mAppLabel);
        printWriter.println(" mPin1Replaced=" + this.mPin1Replaced);
        printWriter.println(" mPin1State=" + this.mPin1State);
        printWriter.println(" mPin2State=" + this.mPin2State);
        printWriter.println(" mIccFdnEnabled=" + this.mIccFdnEnabled);
        printWriter.println(" mDesiredFdnEnabled=" + this.mDesiredFdnEnabled);
        printWriter.println(" mIccLockEnabled=" + this.mIccLockEnabled);
        printWriter.println(" mDesiredPinLocked=" + this.mDesiredPinLocked);
        printWriter.println(" mCi=" + this.mCi);
        printWriter.println(" mIccRecords=" + this.mIccRecords);
        printWriter.println(" mIccFh=" + this.mIccFh);
        printWriter.println(" mDestroyed=" + this.mDestroyed);
        printWriter.println(" mReadyRegistrants: size=" + this.mReadyRegistrants.size());
        for (i = 0; i < this.mReadyRegistrants.size(); i++) {
            printWriter.println("  mReadyRegistrants[" + i + "]=" + ((Registrant) this.mReadyRegistrants.get(i)).getHandler());
        }
        printWriter.println(" mPinLockedRegistrants: size=" + this.mPinLockedRegistrants.size());
        for (i = 0; i < this.mPinLockedRegistrants.size(); i++) {
            printWriter.println("  mPinLockedRegistrants[" + i + "]=" + ((Registrant) this.mPinLockedRegistrants.get(i)).getHandler());
        }
        printWriter.println(" mPersoLockedRegistrants: size=" + this.mPersoLockedRegistrants.size());
        while (i2 < this.mPersoLockedRegistrants.size()) {
            printWriter.println("  mPersoLockedRegistrants[" + i2 + "]=" + ((Registrant) this.mPersoLockedRegistrants.get(i2)).getHandler());
            i2++;
        }
        printWriter.flush();
    }

    public void exchangeAPDU(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message) {
        this.mCi.iccExchangeAPDU(i, i2, i3, i4, i5, i6, str, this.mHandler.obtainMessage(20, message));
    }

    public void exchangeSimIO(int i, int i2, int i3, int i4, int i5, String str, Message message) {
        this.mCi.iccIO(i2, i, str, i3, i4, i5, null, null, this.mHandler.obtainMessage(23, message));
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

    public int getAuthContext() {
        int i;
        synchronized (this.mLock) {
            i = this.mAuthContext;
        }
        return i;
    }

    public boolean getIccFdnAvailable() {
        synchronized (this.mLock) {
            if (this.mAppState == AppState.APPSTATE_SUBSCRIPTION_PERSO && isPersoLocked()) {
                return false;
            }
            boolean z = this.mIccFdnAvailable;
            return z;
        }
    }

    public boolean getIccFdnEnabled() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mIccFdnEnabled;
        }
        return z;
    }

    public IccFileHandler getIccFileHandler() {
        IccFileHandler iccFileHandler;
        synchronized (this.mLock) {
            iccFileHandler = this.mIccFh;
        }
        return iccFileHandler;
    }

    public boolean getIccLockEnabled() {
        return this.mIccLockEnabled;
    }

    public boolean getIccPin2Blocked() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mPin2State == PinState.PINSTATE_ENABLED_BLOCKED;
        }
        return z;
    }

    public boolean getIccPuk2Blocked() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mPin2State == PinState.PINSTATE_ENABLED_PERM_BLOCKED;
        }
        return z;
    }

    public IccRecords getIccRecords() {
        IccRecords iccRecords;
        synchronized (this.mLock) {
            iccRecords = this.mIccRecords;
        }
        return iccRecords;
    }

    public PersoSubState getPersoSubState() {
        PersoSubState persoSubState;
        synchronized (this.mLock) {
            persoSubState = this.mPersoSubState;
        }
        return persoSubState;
    }

    public int getPhoneId() {
        return this.mUiccCard.getPhoneId();
    }

    public PinState getPin1State() {
        PinState universalPinState;
        synchronized (this.mLock) {
            if (this.mPin1Replaced) {
                universalPinState = this.mUiccCard.getUniversalPinState();
            } else {
                universalPinState = this.mPin1State;
            }
        }
        return universalPinState;
    }

    public AppState getState() {
        AppState appState;
        synchronized (this.mLock) {
            appState = this.mAppState;
        }
        return appState;
    }

    public AppType getType() {
        AppType appType;
        synchronized (this.mLock) {
            appType = this.mAppType;
        }
        return appType;
    }

    /* Access modifiers changed, original: protected */
    public UiccCard getUiccCard() {
        return this.mUiccCard;
    }

    public boolean isPersoLocked() {
        synchronized (this.mLock) {
            switch (this.mPersoSubState) {
                case PERSOSUBSTATE_UNKNOWN:
                case PERSOSUBSTATE_IN_PROGRESS:
                case PERSOSUBSTATE_READY:
                    return false;
                default:
                    return true;
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void onRefresh(IccRefreshResponse iccRefreshResponse) {
        if (iccRefreshResponse == null) {
            loge("onRefresh received without input");
        } else if (iccRefreshResponse.aid == null || iccRefreshResponse.aid.equals(this.mAid)) {
            log("refresh for app " + iccRefreshResponse.aid);
            switch (iccRefreshResponse.refreshResult) {
                case 1:
                case 2:
                    log("onRefresh: Setting app state to unknown");
                    this.mAppState = AppState.APPSTATE_UNKNOWN;
                    return;
                default:
                    return;
            }
        }
    }

    public void openLogicalChannel(String str, Message message) {
        if (TextUtils.isEmpty(str)) {
            str = "";
        }
        this.mCi.iccOpenChannel(str, this.mHandler.obtainMessage(21, message));
    }

    /* Access modifiers changed, original: 0000 */
    public void queryFdn() {
        this.mCi.queryFacilityLockForApp(CommandsInterface.CB_FACILITY_BA_FD, "", 7, this.mAid, this.mHandler.obtainMessage(4));
    }

    public void registerForLocked(Handler handler, int i, Object obj) {
        synchronized (this.mLock) {
            Registrant registrant = new Registrant(handler, i, obj);
            this.mPinLockedRegistrants.add(registrant);
            notifyPinLockedRegistrantsIfNeeded(registrant);
        }
    }

    public void registerForPersoLocked(Handler handler, int i, Object obj) {
        synchronized (this.mLock) {
            Registrant registrant = new Registrant(handler, i, obj);
            this.mPersoLockedRegistrants.add(registrant);
            notifyPersoLockedRegistrantsIfNeeded(registrant);
        }
    }

    public void registerForReady(Handler handler, int i, Object obj) {
        synchronized (this.mLock) {
            int size = this.mReadyRegistrants.size() - 1;
            while (size >= 0) {
                Handler handler2 = ((Registrant) this.mReadyRegistrants.get(size)).getHandler();
                if (handler2 == null || handler2 != handler) {
                    size--;
                } else {
                    return;
                }
            }
            Registrant registrant = new Registrant(handler, i, obj);
            this.mReadyRegistrants.add(registrant);
            notifyReadyRegistrantsIfNeeded(registrant);
        }
    }

    public void setIccFdnEnabled(boolean z, String str, Message message) {
        synchronized (this.mLock) {
            this.mDesiredFdnEnabled = z;
            this.mCi.setFacilityLockForApp(CommandsInterface.CB_FACILITY_BA_FD, z, str, 15, this.mAid, this.mHandler.obtainMessage(5, message));
        }
    }

    public void setIccLockEnabled(boolean z, String str, Message message) {
        synchronized (this.mLock) {
            this.mDesiredPinLocked = z;
            this.mCi.setFacilityLockForApp(CommandsInterface.CB_FACILITY_BA_SIM, z, str, 7, this.mAid, this.mHandler.obtainMessage(7, message));
        }
    }

    public void supplyDepersonalization(String str, String str2, Message message) {
        synchronized (this.mLock) {
            log("Network Despersonalization: pin = **** , type = " + str2);
            this.mCi.supplyDepersonalization(str, str2, message);
        }
    }

    public void supplyPin(String str, Message message) {
        synchronized (this.mLock) {
            this.mCi.supplyIccPinForApp(str, this.mAid, this.mHandler.obtainMessage(15, message));
        }
    }

    public void supplyPin2(String str, Message message) {
        synchronized (this.mLock) {
            this.mCi.supplyIccPin2ForApp(str, this.mAid, this.mHandler.obtainMessage(17, message));
        }
    }

    public void supplyPuk(String str, String str2, Message message) {
        synchronized (this.mLock) {
            this.mCi.supplyIccPukForApp(str, str2, this.mAid, this.mHandler.obtainMessage(16, message));
        }
    }

    public void supplyPuk2(String str, String str2, Message message) {
        synchronized (this.mLock) {
            this.mCi.supplyIccPuk2ForApp(str, str2, this.mAid, this.mHandler.obtainMessage(18, message));
        }
    }

    public void unregisterForLocked(Handler handler) {
        synchronized (this.mLock) {
            this.mPinLockedRegistrants.remove(handler);
        }
    }

    public void unregisterForPersoLocked(Handler handler) {
        synchronized (this.mLock) {
            this.mPersoLockedRegistrants.remove(handler);
        }
    }

    public void unregisterForReady(Handler handler) {
        synchronized (this.mLock) {
            this.mReadyRegistrants.remove(handler);
        }
    }

    /* Access modifiers changed, original: 0000 */
    /* JADX WARNING: Missing block: B:51:?, code skipped:
            return;
     */
    public void update(com.android.internal.telephony.uicc.IccCardApplicationStatus r6, android.content.Context r7, com.android.internal.telephony.CommandsInterface r8) {
        /*
        r5 = this;
        r1 = r5.mLock;
        monitor-enter(r1);
        r0 = r5.mDestroyed;	 Catch:{ all -> 0x00fe }
        if (r0 == 0) goto L_0x000e;
    L_0x0007:
        r0 = "Application updated after destroyed! Fix me!";
        r5.loge(r0);	 Catch:{ all -> 0x00fe }
        monitor-exit(r1);	 Catch:{ all -> 0x00fe }
    L_0x000d:
        return;
    L_0x000e:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00fe }
        r0.<init>();	 Catch:{ all -> 0x00fe }
        r2 = r5.mAppType;	 Catch:{ all -> 0x00fe }
        r0 = r0.append(r2);	 Catch:{ all -> 0x00fe }
        r2 = " update. New ";
        r0 = r0.append(r2);	 Catch:{ all -> 0x00fe }
        r0 = r0.append(r6);	 Catch:{ all -> 0x00fe }
        r0 = r0.toString();	 Catch:{ all -> 0x00fe }
        r5.log(r0);	 Catch:{ all -> 0x00fe }
        r5.mContext = r7;	 Catch:{ all -> 0x00fe }
        r5.mCi = r8;	 Catch:{ all -> 0x00fe }
        r2 = r5.mAppType;	 Catch:{ all -> 0x00fe }
        r3 = r5.mAppState;	 Catch:{ all -> 0x00fe }
        r4 = r5.mPersoSubState;	 Catch:{ all -> 0x00fe }
        r0 = r6.app_type;	 Catch:{ all -> 0x00fe }
        r5.mAppType = r0;	 Catch:{ all -> 0x00fe }
        r0 = r5.mAppType;	 Catch:{ all -> 0x00fe }
        r0 = getAuthContext(r0);	 Catch:{ all -> 0x00fe }
        r5.mAuthContext = r0;	 Catch:{ all -> 0x00fe }
        r0 = r6.app_state;	 Catch:{ all -> 0x00fe }
        r5.mAppState = r0;	 Catch:{ all -> 0x00fe }
        r0 = r6.perso_substate;	 Catch:{ all -> 0x00fe }
        r5.mPersoSubState = r0;	 Catch:{ all -> 0x00fe }
        r0 = r6.aid;	 Catch:{ all -> 0x00fe }
        r5.mAid = r0;	 Catch:{ all -> 0x00fe }
        r0 = r6.app_label;	 Catch:{ all -> 0x00fe }
        r5.mAppLabel = r0;	 Catch:{ all -> 0x00fe }
        r0 = r6.pin1_replaced;	 Catch:{ all -> 0x00fe }
        if (r0 == 0) goto L_0x0101;
    L_0x0054:
        r0 = 1;
    L_0x0055:
        r5.mPin1Replaced = r0;	 Catch:{ all -> 0x00fe }
        r0 = com.android.internal.telephony.TelBrand.IS_DCM;	 Catch:{ all -> 0x00fe }
        if (r0 != 0) goto L_0x005f;
    L_0x005b:
        r0 = r6.pin1;	 Catch:{ all -> 0x00fe }
        r5.mPin1State = r0;	 Catch:{ all -> 0x00fe }
    L_0x005f:
        r0 = r6.pin2;	 Catch:{ all -> 0x00fe }
        r5.mPin2State = r0;	 Catch:{ all -> 0x00fe }
        r0 = r5.mAppType;	 Catch:{ all -> 0x00fe }
        if (r0 == r2) goto L_0x0089;
    L_0x0067:
        r0 = r5.mIccFh;	 Catch:{ all -> 0x00fe }
        if (r0 == 0) goto L_0x0070;
    L_0x006b:
        r0 = r5.mIccFh;	 Catch:{ all -> 0x00fe }
        r0.dispose();	 Catch:{ all -> 0x00fe }
    L_0x0070:
        r0 = r5.mIccRecords;	 Catch:{ all -> 0x00fe }
        if (r0 == 0) goto L_0x0079;
    L_0x0074:
        r0 = r5.mIccRecords;	 Catch:{ all -> 0x00fe }
        r0.dispose();	 Catch:{ all -> 0x00fe }
    L_0x0079:
        r0 = r6.app_type;	 Catch:{ all -> 0x00fe }
        r0 = r5.createIccFileHandler(r0);	 Catch:{ all -> 0x00fe }
        r5.mIccFh = r0;	 Catch:{ all -> 0x00fe }
        r0 = r6.app_type;	 Catch:{ all -> 0x00fe }
        r0 = r5.createIccRecords(r0, r7, r8);	 Catch:{ all -> 0x00fe }
        r5.mIccRecords = r0;	 Catch:{ all -> 0x00fe }
    L_0x0089:
        r0 = r5.mPersoSubState;	 Catch:{ all -> 0x00fe }
        if (r0 == r4) goto L_0x0097;
    L_0x008d:
        r0 = r5.isPersoLocked();	 Catch:{ all -> 0x00fe }
        if (r0 == 0) goto L_0x0097;
    L_0x0093:
        r0 = 0;
        r5.notifyPersoLockedRegistrantsIfNeeded(r0);	 Catch:{ all -> 0x00fe }
    L_0x0097:
        r0 = com.android.internal.telephony.TelBrand.IS_DCM;	 Catch:{ all -> 0x00fe }
        if (r0 == 0) goto L_0x0104;
    L_0x009b:
        r0 = r6.app_state;	 Catch:{ all -> 0x00fe }
        if (r0 != r3) goto L_0x00a5;
    L_0x009f:
        r0 = r5.mPin1State;	 Catch:{ all -> 0x00fe }
        r4 = r6.pin1;	 Catch:{ all -> 0x00fe }
        if (r0 == r4) goto L_0x00fb;
    L_0x00a5:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00fe }
        r0.<init>();	 Catch:{ all -> 0x00fe }
        r0 = r0.append(r2);	 Catch:{ all -> 0x00fe }
        r2 = " changed state: ";
        r0 = r0.append(r2);	 Catch:{ all -> 0x00fe }
        r0 = r0.append(r3);	 Catch:{ all -> 0x00fe }
        r2 = " -> ";
        r0 = r0.append(r2);	 Catch:{ all -> 0x00fe }
        r2 = r6.app_state;	 Catch:{ all -> 0x00fe }
        r0 = r0.append(r2);	 Catch:{ all -> 0x00fe }
        r2 = " Pin1 state: ";
        r0 = r0.append(r2);	 Catch:{ all -> 0x00fe }
        r2 = r5.mPin1State;	 Catch:{ all -> 0x00fe }
        r0 = r0.append(r2);	 Catch:{ all -> 0x00fe }
        r2 = " -> ";
        r0 = r0.append(r2);	 Catch:{ all -> 0x00fe }
        r2 = r6.pin1;	 Catch:{ all -> 0x00fe }
        r0 = r0.append(r2);	 Catch:{ all -> 0x00fe }
        r0 = r0.toString();	 Catch:{ all -> 0x00fe }
        r5.log(r0);	 Catch:{ all -> 0x00fe }
        r0 = r6.pin1;	 Catch:{ all -> 0x00fe }
        r5.mPin1State = r0;	 Catch:{ all -> 0x00fe }
        r0 = r5.mAppState;	 Catch:{ all -> 0x00fe }
        r2 = com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState.APPSTATE_READY;	 Catch:{ all -> 0x00fe }
        if (r0 != r2) goto L_0x00f3;
    L_0x00ed:
        r5.queryFdn();	 Catch:{ all -> 0x00fe }
        r5.queryPin1State();	 Catch:{ all -> 0x00fe }
    L_0x00f3:
        r0 = 0;
        r5.notifyPinLockedRegistrantsIfNeeded(r0);	 Catch:{ all -> 0x00fe }
        r0 = 0;
        r5.notifyReadyRegistrantsIfNeeded(r0);	 Catch:{ all -> 0x00fe }
    L_0x00fb:
        monitor-exit(r1);	 Catch:{ all -> 0x00fe }
        goto L_0x000d;
    L_0x00fe:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x00fe }
        throw r0;
    L_0x0101:
        r0 = 0;
        goto L_0x0055;
    L_0x0104:
        r0 = r5.mAppState;	 Catch:{ all -> 0x00fe }
        if (r0 == r3) goto L_0x00fb;
    L_0x0108:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00fe }
        r0.<init>();	 Catch:{ all -> 0x00fe }
        r0 = r0.append(r2);	 Catch:{ all -> 0x00fe }
        r2 = " changed state: ";
        r0 = r0.append(r2);	 Catch:{ all -> 0x00fe }
        r0 = r0.append(r3);	 Catch:{ all -> 0x00fe }
        r2 = " -> ";
        r0 = r0.append(r2);	 Catch:{ all -> 0x00fe }
        r2 = r5.mAppState;	 Catch:{ all -> 0x00fe }
        r0 = r0.append(r2);	 Catch:{ all -> 0x00fe }
        r0 = r0.toString();	 Catch:{ all -> 0x00fe }
        r5.log(r0);	 Catch:{ all -> 0x00fe }
        r0 = r5.mAppState;	 Catch:{ all -> 0x00fe }
        r2 = com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState.APPSTATE_READY;	 Catch:{ all -> 0x00fe }
        if (r0 != r2) goto L_0x013a;
    L_0x0134:
        r5.queryFdn();	 Catch:{ all -> 0x00fe }
        r5.queryPin1State();	 Catch:{ all -> 0x00fe }
    L_0x013a:
        r0 = 0;
        r5.notifyPinLockedRegistrantsIfNeeded(r0);	 Catch:{ all -> 0x00fe }
        r0 = 0;
        r5.notifyReadyRegistrantsIfNeeded(r0);	 Catch:{ all -> 0x00fe }
        goto L_0x00fb;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.UiccCardApplication.update(com.android.internal.telephony.uicc.IccCardApplicationStatus, android.content.Context, com.android.internal.telephony.CommandsInterface):void");
    }
}
