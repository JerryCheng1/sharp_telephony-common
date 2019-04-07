package com.android.internal.telephony.uicc;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.RegistrantList;
import android.preference.PreferenceManager;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.cat.CatService;
import com.android.internal.telephony.cat.CatServiceFactory;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccCardStatus.PinState;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

public class UiccCard {
    protected static final boolean DBG = true;
    private static final int EVENT_CARD_ADDED = 14;
    private static final int EVENT_CARD_REMOVED = 13;
    private static final int EVENT_CARRIER_PRIVILIGES_LOADED = 20;
    private static final int EVENT_CLOSE_LOGICAL_CHANNEL_DONE = 16;
    private static final int EVENT_OPEN_LOGICAL_CHANNEL_DONE = 15;
    private static final int EVENT_SIM_GET_ATR_DONE = 21;
    private static final int EVENT_SIM_IO_DONE = 19;
    private static final int EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE = 18;
    private static final int EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE = 17;
    protected static final String LOG_TAG = "UiccCard";
    private static final String OPERATOR_BRAND_OVERRIDE_PREFIX = "operator_branding_";
    protected static final int REQUEST_INITIALIZE_RETRY_COUNT = 0;
    private RegistrantList mAbsentRegistrants;
    private CardState mCardState;
    private RegistrantList mCarrierPrivilegeRegistrants;
    private UiccCarrierPrivilegeRules mCarrierPrivilegeRules;
    private CatService mCatService;
    private int mCdmaSubscriptionAppIndex;
    private CommandsInterface mCi;
    private Context mContext;
    private boolean mDestroyed;
    private int mGsmUmtsSubscriptionAppIndex;
    protected Handler mHandler;
    private int mImsSubscriptionAppIndex;
    private RadioState mLastRadioState;
    private final Object mLock;
    private int mPhoneId;
    private int mPin1RetryCountSc;
    private int mPin2RetryCountSc;
    private int mPuk1RetryCountSc;
    private int mPuk2RetryCountSc;
    private UiccCardApplication[] mUiccApplications;
    private PinState mUniversalPinState;

    protected UiccCard() {
        this.mLock = new Object();
        this.mUiccApplications = new UiccCardApplication[8];
        this.mDestroyed = false;
        this.mLastRadioState = RadioState.RADIO_UNAVAILABLE;
        this.mAbsentRegistrants = new RegistrantList();
        this.mCarrierPrivilegeRegistrants = new RegistrantList();
        this.mPin1RetryCountSc = -1;
        this.mPuk1RetryCountSc = -1;
        this.mPin2RetryCountSc = -1;
        this.mPuk2RetryCountSc = -1;
        this.mHandler = new Handler() {
            public void handleMessage(Message message) {
                if (UiccCard.this.mDestroyed) {
                    UiccCard.this.loge("Received message " + message + "[" + message.what + "] while being destroyed. Ignoring.");
                    return;
                }
                switch (message.what) {
                    case 13:
                        UiccCard.this.onIccSwap(false);
                        return;
                    case 14:
                        UiccCard.this.onIccSwap(true);
                        return;
                    case 15:
                    case 16:
                    case 17:
                    case 18:
                    case 19:
                    case 21:
                        AsyncResult asyncResult = (AsyncResult) message.obj;
                        if (asyncResult.exception != null) {
                            UiccCard.this.log("Error in SIM access with exception" + asyncResult.exception);
                        }
                        AsyncResult.forMessage((Message) asyncResult.userObj, asyncResult.result, asyncResult.exception);
                        ((Message) asyncResult.userObj).sendToTarget();
                        return;
                    case 20:
                        UiccCard.this.onCarrierPriviligesLoadedMessage();
                        return;
                    default:
                        UiccCard.this.loge("Unknown Event " + message.what);
                        return;
                }
            }
        };
    }

    public UiccCard(Context context, CommandsInterface commandsInterface, IccCardStatus iccCardStatus, int i) {
        this.mLock = new Object();
        this.mUiccApplications = new UiccCardApplication[8];
        this.mDestroyed = false;
        this.mLastRadioState = RadioState.RADIO_UNAVAILABLE;
        this.mAbsentRegistrants = new RegistrantList();
        this.mCarrierPrivilegeRegistrants = new RegistrantList();
        this.mPin1RetryCountSc = -1;
        this.mPuk1RetryCountSc = -1;
        this.mPin2RetryCountSc = -1;
        this.mPuk2RetryCountSc = -1;
        this.mHandler = /* anonymous class already generated */;
        this.mCardState = iccCardStatus.mCardState;
        this.mPhoneId = i;
        update(context, commandsInterface, iccCardStatus);
    }

    private int checkIndex(int i, AppType appType, AppType appType2) {
        if (this.mUiccApplications == null || i >= this.mUiccApplications.length) {
            loge("App index " + i + " is invalid since there are no applications");
            i = -1;
        } else if (i < 0) {
            return -1;
        } else {
            if (!(this.mUiccApplications[i].getType() == appType || this.mUiccApplications[i].getType() == appType2)) {
                loge("App index " + i + " is invalid since it's not " + appType + " and not " + appType2);
                return -1;
            }
        }
        return i;
    }

    private void loge(String str) {
        Rlog.e(LOG_TAG, str);
    }

    private void onCarrierPriviligesLoadedMessage() {
        synchronized (this.mLock) {
            this.mCarrierPrivilegeRegistrants.notifyRegistrants();
        }
    }

    private void onIccSwap(boolean z) {
        Throwable th;
        if (this.mContext.getResources().getBoolean(17956931)) {
            log("onIccSwap: isHotSwapSupported is true, don't prompt for rebooting");
            return;
        }
        log("onIccSwap: isHotSwapSupported is false, prompt for rebooting");
        synchronized (this.mLock) {
            try {
                AnonymousClass1 anonymousClass1 = new OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        synchronized (UiccCard.this.mLock) {
                            if (i == -1) {
                                UiccCard.this.log("Reboot due to SIM swap");
                                ((PowerManager) UiccCard.this.mContext.getSystemService("power")).reboot("SIM is added.");
                            }
                        }
                    }
                };
                try {
                    CharSequence string;
                    Resources system = Resources.getSystem();
                    if (z) {
                        string = system.getString(17040694);
                    } else {
                        Object string2 = system.getString(17040691);
                    }
                    AlertDialog create = new Builder(this.mContext).setTitle(string2).setMessage(z ? system.getString(17040695) : system.getString(17040692)).setPositiveButton(system.getString(17040696), anonymousClass1).create();
                    create.getWindow().setType(2003);
                    create.show();
                } catch (Throwable th2) {
                    th = th2;
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
                throw th;
            }
        }
    }

    private void sanitizeApplicationIndexes() {
        this.mGsmUmtsSubscriptionAppIndex = checkIndex(this.mGsmUmtsSubscriptionAppIndex, AppType.APPTYPE_SIM, AppType.APPTYPE_USIM);
        this.mCdmaSubscriptionAppIndex = checkIndex(this.mCdmaSubscriptionAppIndex, AppType.APPTYPE_RUIM, AppType.APPTYPE_CSIM);
        this.mImsSubscriptionAppIndex = checkIndex(this.mImsSubscriptionAppIndex, AppType.APPTYPE_ISIM, null);
    }

    public boolean areCarrierPriviligeRulesLoaded() {
        return this.mCarrierPrivilegeRules != null && this.mCarrierPrivilegeRules.areCarrierPriviligeRulesLoaded();
    }

    /* Access modifiers changed, original: protected */
    public void createAndUpdateCatService() {
        if (this.mUiccApplications.length <= 0 || this.mUiccApplications[0] == null) {
            if (this.mCatService != null) {
                CatServiceFactory.disposeCatService(this.mPhoneId);
            }
            this.mCatService = null;
        } else if (this.mCatService == null) {
            this.mCatService = CatServiceFactory.makeCatService(this.mCi, this.mContext, this, this.mPhoneId);
        }
    }

    public void dispose() {
        synchronized (this.mLock) {
            log("Disposing card");
            if (this.mCatService != null) {
                CatServiceFactory.disposeCatService(this.mPhoneId);
            }
            for (UiccCardApplication uiccCardApplication : this.mUiccApplications) {
                if (uiccCardApplication != null) {
                    uiccCardApplication.dispose();
                }
            }
            this.mCatService = null;
            this.mUiccApplications = null;
            this.mCarrierPrivilegeRules = null;
        }
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        int i;
        int i2;
        int i3 = 0;
        printWriter.println("UiccCard:");
        printWriter.println(" mCi=" + this.mCi);
        printWriter.println(" mDestroyed=" + this.mDestroyed);
        printWriter.println(" mLastRadioState=" + this.mLastRadioState);
        printWriter.println(" mCatService=" + this.mCatService);
        printWriter.println(" mAbsentRegistrants: size=" + this.mAbsentRegistrants.size());
        for (i = 0; i < this.mAbsentRegistrants.size(); i++) {
            printWriter.println("  mAbsentRegistrants[" + i + "]=" + ((Registrant) this.mAbsentRegistrants.get(i)).getHandler());
        }
        for (i = 0; i < this.mCarrierPrivilegeRegistrants.size(); i++) {
            printWriter.println("  mCarrierPrivilegeRegistrants[" + i + "]=" + ((Registrant) this.mCarrierPrivilegeRegistrants.get(i)).getHandler());
        }
        printWriter.println(" mCardState=" + this.mCardState);
        printWriter.println(" mUniversalPinState=" + this.mUniversalPinState);
        printWriter.println(" mGsmUmtsSubscriptionAppIndex=" + this.mGsmUmtsSubscriptionAppIndex);
        printWriter.println(" mCdmaSubscriptionAppIndex=" + this.mCdmaSubscriptionAppIndex);
        printWriter.println(" mImsSubscriptionAppIndex=" + this.mImsSubscriptionAppIndex);
        printWriter.println(" mImsSubscriptionAppIndex=" + this.mImsSubscriptionAppIndex);
        printWriter.println(" mUiccApplications: length=" + this.mUiccApplications.length);
        for (i2 = 0; i2 < this.mUiccApplications.length; i2++) {
            if (this.mUiccApplications[i2] == null) {
                printWriter.println("  mUiccApplications[" + i2 + "]=" + null);
            } else {
                printWriter.println("  mUiccApplications[" + i2 + "]=" + this.mUiccApplications[i2].getType() + " " + this.mUiccApplications[i2]);
            }
        }
        printWriter.println();
        for (UiccCardApplication uiccCardApplication : this.mUiccApplications) {
            if (uiccCardApplication != null) {
                uiccCardApplication.dump(fileDescriptor, printWriter, strArr);
                printWriter.println();
            }
        }
        for (UiccCardApplication uiccCardApplication2 : this.mUiccApplications) {
            if (uiccCardApplication2 != null) {
                IccRecords iccRecords = uiccCardApplication2.getIccRecords();
                if (iccRecords != null) {
                    iccRecords.dump(fileDescriptor, printWriter, strArr);
                    printWriter.println();
                }
            }
        }
        if (this.mCarrierPrivilegeRules == null) {
            printWriter.println(" mCarrierPrivilegeRules: null");
        } else {
            printWriter.println(" mCarrierPrivilegeRules: " + this.mCarrierPrivilegeRules);
            this.mCarrierPrivilegeRules.dump(fileDescriptor, printWriter, strArr);
        }
        printWriter.println(" mCarrierPrivilegeRegistrants: size=" + this.mCarrierPrivilegeRegistrants.size());
        while (i3 < this.mCarrierPrivilegeRegistrants.size()) {
            printWriter.println("  mCarrierPrivilegeRegistrants[" + i3 + "]=" + ((Registrant) this.mCarrierPrivilegeRegistrants.get(i3)).getHandler());
            i3++;
        }
        printWriter.flush();
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        log("UiccCard finalized");
    }

    /* JADX WARNING: Missing block: B:19:?, code skipped:
            return null;
     */
    public com.android.internal.telephony.uicc.UiccCardApplication getApplication(int r4) {
        /*
        r3 = this;
        r1 = r3.mLock;
        monitor-enter(r1);
        r0 = 8;
        switch(r4) {
            case 1: goto L_0x0015;
            case 2: goto L_0x0018;
            case 3: goto L_0x001b;
            default: goto L_0x0008;
        };
    L_0x0008:
        if (r0 < 0) goto L_0x001e;
    L_0x000a:
        r2 = r3.mUiccApplications;	 Catch:{ all -> 0x0021 }
        r2 = r2.length;	 Catch:{ all -> 0x0021 }
        if (r0 >= r2) goto L_0x001e;
    L_0x000f:
        r2 = r3.mUiccApplications;	 Catch:{ all -> 0x0021 }
        r0 = r2[r0];	 Catch:{ all -> 0x0021 }
        monitor-exit(r1);	 Catch:{ all -> 0x0021 }
    L_0x0014:
        return r0;
    L_0x0015:
        r0 = r3.mGsmUmtsSubscriptionAppIndex;	 Catch:{ all -> 0x0021 }
        goto L_0x0008;
    L_0x0018:
        r0 = r3.mCdmaSubscriptionAppIndex;	 Catch:{ all -> 0x0021 }
        goto L_0x0008;
    L_0x001b:
        r0 = r3.mImsSubscriptionAppIndex;	 Catch:{ all -> 0x0021 }
        goto L_0x0008;
    L_0x001e:
        monitor-exit(r1);	 Catch:{ all -> 0x0021 }
        r0 = 0;
        goto L_0x0014;
    L_0x0021:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0021 }
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.UiccCard.getApplication(int):com.android.internal.telephony.uicc.UiccCardApplication");
    }

    public UiccCardApplication getApplicationByType(int i) {
        synchronized (this.mLock) {
            int i2 = 0;
            while (i2 < this.mUiccApplications.length) {
                if (this.mUiccApplications[i2] == null || this.mUiccApplications[i2].getType().ordinal() != i) {
                    i2++;
                } else {
                    UiccCardApplication uiccCardApplication = this.mUiccApplications[i2];
                    return uiccCardApplication;
                }
            }
            return null;
        }
    }

    /* JADX WARNING: Missing block: B:14:?, code skipped:
            return null;
     */
    public com.android.internal.telephony.uicc.UiccCardApplication getApplicationIndex(int r3) {
        /*
        r2 = this;
        r1 = r2.mLock;
        monitor-enter(r1);
        if (r3 < 0) goto L_0x0010;
    L_0x0005:
        r0 = r2.mUiccApplications;	 Catch:{ all -> 0x0013 }
        r0 = r0.length;	 Catch:{ all -> 0x0013 }
        if (r3 >= r0) goto L_0x0010;
    L_0x000a:
        r0 = r2.mUiccApplications;	 Catch:{ all -> 0x0013 }
        r0 = r0[r3];	 Catch:{ all -> 0x0013 }
        monitor-exit(r1);	 Catch:{ all -> 0x0013 }
    L_0x000f:
        return r0;
    L_0x0010:
        monitor-exit(r1);	 Catch:{ all -> 0x0013 }
        r0 = 0;
        goto L_0x000f;
    L_0x0013:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0013 }
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.UiccCard.getApplicationIndex(int):com.android.internal.telephony.uicc.UiccCardApplication");
    }

    public void getAtr(Message message) {
        this.mCi.getAtr(this.mHandler.obtainMessage(21, message));
    }

    public CardState getCardState() {
        CardState cardState;
        synchronized (this.mLock) {
            cardState = this.mCardState;
        }
        return cardState;
    }

    public List<String> getCarrierPackageNamesForIntent(PackageManager packageManager, Intent intent) {
        return this.mCarrierPrivilegeRules == null ? null : this.mCarrierPrivilegeRules.getCarrierPackageNamesForIntent(packageManager, intent);
    }

    public int getCarrierPrivilegeStatus(PackageManager packageManager, String str) {
        return this.mCarrierPrivilegeRules == null ? -1 : this.mCarrierPrivilegeRules.getCarrierPrivilegeStatus(packageManager, str);
    }

    public int getCarrierPrivilegeStatus(Signature signature, String str) {
        return this.mCarrierPrivilegeRules == null ? -1 : this.mCarrierPrivilegeRules.getCarrierPrivilegeStatus(signature, str);
    }

    public int getCarrierPrivilegeStatusForCurrentTransaction(PackageManager packageManager) {
        return this.mCarrierPrivilegeRules == null ? -1 : this.mCarrierPrivilegeRules.getCarrierPrivilegeStatusForCurrentTransaction(packageManager);
    }

    public CatService getCatService() {
        return this.mCatService;
    }

    public String getIccId() {
        for (UiccCardApplication uiccCardApplication : this.mUiccApplications) {
            if (uiccCardApplication != null) {
                IccRecords iccRecords = uiccCardApplication.getIccRecords();
                if (!(iccRecords == null || iccRecords.getIccId() == null)) {
                    return iccRecords.getIccId();
                }
            }
        }
        return null;
    }

    /* Access modifiers changed, original: protected */
    public int getIccPinPukRetryCountSc(int i) {
        int i2;
        switch (i) {
            case 1:
                i2 = this.mPin1RetryCountSc;
                log("Get mPin1RetryCountSc = " + this.mPin1RetryCountSc);
                return i2;
            case 2:
                i2 = this.mPuk1RetryCountSc;
                log("Get mPuk1RetryCountSc = " + this.mPuk1RetryCountSc);
                return i2;
            case 3:
                i2 = this.mPin2RetryCountSc;
                log("Get mPin2RetryCountSc = " + this.mPin2RetryCountSc);
                return i2;
            case 4:
                i2 = this.mPuk2RetryCountSc;
                log("Get mPuk2RetryCountSc = " + this.mPuk2RetryCountSc);
                return i2;
            default:
                log("Incorrect request. PIN/PUK request = " + i);
                return -1;
        }
    }

    public int getNumApplications() {
        int i = 0;
        for (UiccCardApplication uiccCardApplication : this.mUiccApplications) {
            if (uiccCardApplication != null) {
                i++;
            }
        }
        return i;
    }

    public String getOperatorBrandOverride() {
        String iccId = getIccId();
        return TextUtils.isEmpty(iccId) ? null : PreferenceManager.getDefaultSharedPreferences(this.mContext).getString(OPERATOR_BRAND_OVERRIDE_PREFIX + iccId, null);
    }

    public int getPhoneId() {
        return this.mPhoneId;
    }

    public PinState getUniversalPinState() {
        PinState pinState;
        synchronized (this.mLock) {
            pinState = this.mUniversalPinState;
        }
        return pinState;
    }

    public void iccCloseLogicalChannel(int i, Message message) {
        this.mCi.iccCloseLogicalChannel(i, this.mHandler.obtainMessage(16, message));
    }

    public void iccExchangeSimIO(int i, int i2, int i3, int i4, int i5, String str, Message message) {
        this.mCi.iccIO(i2, i, str, i3, i4, i5, null, null, this.mHandler.obtainMessage(19, message));
    }

    public void iccOpenLogicalChannel(String str, Message message) {
        this.mCi.iccOpenLogicalChannel(str, this.mHandler.obtainMessage(15, message));
    }

    public void iccTransmitApduBasicChannel(int i, int i2, int i3, int i4, int i5, String str, Message message) {
        this.mCi.iccTransmitApduBasicChannel(i, i2, i3, i4, i5, str, this.mHandler.obtainMessage(18, message));
    }

    public void iccTransmitApduLogicalChannel(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message) {
        this.mCi.iccTransmitApduLogicalChannel(i, i2, i3, i4, i5, i6, str, this.mHandler.obtainMessage(17, message));
    }

    public boolean isApplicationOnIcc(AppType appType) {
        synchronized (this.mLock) {
            int i = 0;
            while (i < this.mUiccApplications.length) {
                if (this.mUiccApplications[i] == null || this.mUiccApplications[i].getType() != appType) {
                    i++;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    /* Access modifiers changed, original: 0000 */
    public void onRefresh(IccRefreshResponse iccRefreshResponse) {
        for (int i = 0; i < this.mUiccApplications.length; i++) {
            if (this.mUiccApplications[i] != null) {
                this.mUiccApplications[i].onRefresh(iccRefreshResponse);
            }
        }
    }

    public void registerForAbsent(Handler handler, int i, Object obj) {
        synchronized (this.mLock) {
            Registrant registrant = new Registrant(handler, i, obj);
            this.mAbsentRegistrants.add(registrant);
            if (this.mCardState == CardState.CARDSTATE_ABSENT) {
                registrant.notifyRegistrant();
            }
        }
    }

    public void registerForCarrierPrivilegeRulesLoaded(Handler handler, int i, Object obj) {
        synchronized (this.mLock) {
            Registrant registrant = new Registrant(handler, i, obj);
            this.mCarrierPrivilegeRegistrants.add(registrant);
            if (areCarrierPriviligeRulesLoaded()) {
                registrant.notifyRegistrant();
            }
        }
    }

    public void sendEnvelopeWithStatus(String str, Message message) {
        this.mCi.sendEnvelopeWithStatus(str, message);
    }

    public boolean setOperatorBrandOverride(String str) {
        log("setOperatorBrandOverride: " + str);
        log("current iccId: " + getIccId());
        String iccId = getIccId();
        if (TextUtils.isEmpty(iccId)) {
            return false;
        }
        Editor edit = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
        iccId = OPERATOR_BRAND_OVERRIDE_PREFIX + iccId;
        if (str == null) {
            edit.remove(iccId).commit();
        } else {
            edit.putString(iccId, str).commit();
        }
        return true;
    }

    /* Access modifiers changed, original: protected */
    public void setPinPukRetryCount(int r4, int r5) {
        /*
        r3 = this;
        r1 = r3.mLock;
        monitor-enter(r1);
        switch(r4) {
            case 0: goto L_0x0077;
            case 1: goto L_0x0008;
            case 2: goto L_0x0026;
            case 3: goto L_0x0041;
            case 4: goto L_0x005c;
            default: goto L_0x0006;
        };
    L_0x0006:
        monitor-exit(r1);	 Catch:{ all -> 0x0023 }
        return;
    L_0x0008:
        r3.mPin1RetryCountSc = r5;	 Catch:{ all -> 0x0023 }
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0023 }
        r0.<init>();	 Catch:{ all -> 0x0023 }
        r2 = "Set mPin1RetryCountSc = ";
        r0 = r0.append(r2);	 Catch:{ all -> 0x0023 }
        r2 = r3.mPin1RetryCountSc;	 Catch:{ all -> 0x0023 }
        r0 = r0.append(r2);	 Catch:{ all -> 0x0023 }
        r0 = r0.toString();	 Catch:{ all -> 0x0023 }
        r3.log(r0);	 Catch:{ all -> 0x0023 }
        goto L_0x0006;
    L_0x0023:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0023 }
        throw r0;
    L_0x0026:
        r3.mPuk1RetryCountSc = r5;	 Catch:{ all -> 0x0023 }
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0023 }
        r0.<init>();	 Catch:{ all -> 0x0023 }
        r2 = "Set mPuk1RetryCountSc = ";
        r0 = r0.append(r2);	 Catch:{ all -> 0x0023 }
        r2 = r3.mPuk1RetryCountSc;	 Catch:{ all -> 0x0023 }
        r0 = r0.append(r2);	 Catch:{ all -> 0x0023 }
        r0 = r0.toString();	 Catch:{ all -> 0x0023 }
        r3.log(r0);	 Catch:{ all -> 0x0023 }
        goto L_0x0006;
    L_0x0041:
        r3.mPin2RetryCountSc = r5;	 Catch:{ all -> 0x0023 }
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0023 }
        r0.<init>();	 Catch:{ all -> 0x0023 }
        r2 = "Set mPin2RetryCountSc = ";
        r0 = r0.append(r2);	 Catch:{ all -> 0x0023 }
        r2 = r3.mPin2RetryCountSc;	 Catch:{ all -> 0x0023 }
        r0 = r0.append(r2);	 Catch:{ all -> 0x0023 }
        r0 = r0.toString();	 Catch:{ all -> 0x0023 }
        r3.log(r0);	 Catch:{ all -> 0x0023 }
        goto L_0x0006;
    L_0x005c:
        r3.mPuk2RetryCountSc = r5;	 Catch:{ all -> 0x0023 }
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0023 }
        r0.<init>();	 Catch:{ all -> 0x0023 }
        r2 = "Set mPuk2RetryCountSc = ";
        r0 = r0.append(r2);	 Catch:{ all -> 0x0023 }
        r2 = r3.mPuk2RetryCountSc;	 Catch:{ all -> 0x0023 }
        r0 = r0.append(r2);	 Catch:{ all -> 0x0023 }
        r0 = r0.toString();	 Catch:{ all -> 0x0023 }
        r3.log(r0);	 Catch:{ all -> 0x0023 }
        goto L_0x0006;
    L_0x0077:
        r3.mPin1RetryCountSc = r5;	 Catch:{ all -> 0x0023 }
        r3.mPuk1RetryCountSc = r5;	 Catch:{ all -> 0x0023 }
        r3.mPin2RetryCountSc = r5;	 Catch:{ all -> 0x0023 }
        r3.mPuk2RetryCountSc = r5;	 Catch:{ all -> 0x0023 }
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0023 }
        r0.<init>();	 Catch:{ all -> 0x0023 }
        r2 = "Initialize PIN/PUK retry count = ";
        r0 = r0.append(r2);	 Catch:{ all -> 0x0023 }
        r0 = r0.append(r5);	 Catch:{ all -> 0x0023 }
        r0 = r0.toString();	 Catch:{ all -> 0x0023 }
        r3.log(r0);	 Catch:{ all -> 0x0023 }
        goto L_0x0006;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.UiccCard.setPinPukRetryCount(int, int):void");
    }

    public void unregisterForAbsent(Handler handler) {
        synchronized (this.mLock) {
            this.mAbsentRegistrants.remove(handler);
        }
    }

    public void unregisterForCarrierPrivilegeRulesLoaded(Handler handler) {
        synchronized (this.mLock) {
            this.mCarrierPrivilegeRegistrants.remove(handler);
        }
    }

    public void update(Context context, CommandsInterface commandsInterface, IccCardStatus iccCardStatus) {
        synchronized (this.mLock) {
            if (this.mDestroyed) {
                loge("Updated after destroyed! Fix me!");
                return;
            }
            CardState cardState = this.mCardState;
            this.mCardState = iccCardStatus.mCardState;
            this.mUniversalPinState = iccCardStatus.mUniversalPinState;
            this.mGsmUmtsSubscriptionAppIndex = iccCardStatus.mGsmUmtsSubscriptionAppIndex;
            this.mCdmaSubscriptionAppIndex = iccCardStatus.mCdmaSubscriptionAppIndex;
            this.mImsSubscriptionAppIndex = iccCardStatus.mImsSubscriptionAppIndex;
            this.mContext = context;
            this.mCi = commandsInterface;
            log(iccCardStatus.mApplications.length + " applications");
            for (int i = 0; i < this.mUiccApplications.length; i++) {
                if (this.mUiccApplications[i] == null) {
                    if (i < iccCardStatus.mApplications.length) {
                        this.mUiccApplications[i] = new UiccCardApplication(this, iccCardStatus.mApplications[i], this.mContext, this.mCi);
                    }
                } else if (i >= iccCardStatus.mApplications.length) {
                    this.mUiccApplications[i].dispose();
                    this.mUiccApplications[i] = null;
                } else {
                    this.mUiccApplications[i].update(iccCardStatus.mApplications[i], this.mContext, this.mCi);
                }
            }
            createAndUpdateCatService();
            log("Before privilege rules: " + this.mCarrierPrivilegeRules + " : " + this.mCardState);
            if (this.mCarrierPrivilegeRules == null && this.mCardState == CardState.CARDSTATE_PRESENT) {
                this.mCarrierPrivilegeRules = new UiccCarrierPrivilegeRules(this, this.mHandler.obtainMessage(20));
            } else if (!(this.mCarrierPrivilegeRules == null || this.mCardState == CardState.CARDSTATE_PRESENT)) {
                this.mCarrierPrivilegeRules = null;
            }
            sanitizeApplicationIndexes();
            RadioState radioState = this.mCi.getRadioState();
            log("update: radioState=" + radioState + " mLastRadioState=" + this.mLastRadioState);
            if (radioState == RadioState.RADIO_ON && this.mLastRadioState == RadioState.RADIO_ON) {
                if (cardState != CardState.CARDSTATE_ABSENT && this.mCardState == CardState.CARDSTATE_ABSENT) {
                    log("update: notify card removed");
                    this.mAbsentRegistrants.notifyRegistrants();
                    this.mHandler.sendMessage(this.mHandler.obtainMessage(13, null));
                } else if (cardState == CardState.CARDSTATE_ABSENT && this.mCardState != CardState.CARDSTATE_ABSENT) {
                    log("update: notify card added");
                    this.mHandler.sendMessage(this.mHandler.obtainMessage(14, null));
                }
            }
            this.mLastRadioState = radioState;
        }
    }
}
