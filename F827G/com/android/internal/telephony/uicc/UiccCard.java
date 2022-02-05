package com.android.internal.telephony.uicc;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.android.internal.telephony.cat.CatService;
import com.android.internal.telephony.cat.CatServiceFactory;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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
    private IccCardStatus.CardState mCardState;
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
    private CommandsInterface.RadioState mLastRadioState;
    private final Object mLock;
    private int mPhoneId;
    private int mPin1RetryCountSc;
    private int mPin2RetryCountSc;
    private int mPuk1RetryCountSc;
    private int mPuk2RetryCountSc;
    private UiccCardApplication[] mUiccApplications;
    private IccCardStatus.PinState mUniversalPinState;

    public UiccCard(Context c, CommandsInterface ci, IccCardStatus ics, int phoneId) {
        this.mLock = new Object();
        this.mUiccApplications = new UiccCardApplication[8];
        this.mDestroyed = false;
        this.mLastRadioState = CommandsInterface.RadioState.RADIO_UNAVAILABLE;
        this.mAbsentRegistrants = new RegistrantList();
        this.mCarrierPrivilegeRegistrants = new RegistrantList();
        this.mPin1RetryCountSc = -1;
        this.mPuk1RetryCountSc = -1;
        this.mPin2RetryCountSc = -1;
        this.mPuk2RetryCountSc = -1;
        this.mHandler = new Handler() { // from class: com.android.internal.telephony.uicc.UiccCard.2
            @Override // android.os.Handler
            public void handleMessage(Message msg) {
                if (UiccCard.this.mDestroyed) {
                    UiccCard.this.loge("Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
                    return;
                }
                switch (msg.what) {
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
                        AsyncResult ar = (AsyncResult) msg.obj;
                        if (ar.exception != null) {
                            UiccCard.this.log("Error in SIM access with exception" + ar.exception);
                        }
                        AsyncResult.forMessage((Message) ar.userObj, ar.result, ar.exception);
                        ((Message) ar.userObj).sendToTarget();
                        return;
                    case 20:
                        UiccCard.this.onCarrierPriviligesLoadedMessage();
                        return;
                    default:
                        UiccCard.this.loge("Unknown Event " + msg.what);
                        return;
                }
            }
        };
        this.mCardState = ics.mCardState;
        this.mPhoneId = phoneId;
        update(c, ci, ics);
    }

    protected UiccCard() {
        this.mLock = new Object();
        this.mUiccApplications = new UiccCardApplication[8];
        this.mDestroyed = false;
        this.mLastRadioState = CommandsInterface.RadioState.RADIO_UNAVAILABLE;
        this.mAbsentRegistrants = new RegistrantList();
        this.mCarrierPrivilegeRegistrants = new RegistrantList();
        this.mPin1RetryCountSc = -1;
        this.mPuk1RetryCountSc = -1;
        this.mPin2RetryCountSc = -1;
        this.mPuk2RetryCountSc = -1;
        this.mHandler = new Handler() { // from class: com.android.internal.telephony.uicc.UiccCard.2
            @Override // android.os.Handler
            public void handleMessage(Message msg) {
                if (UiccCard.this.mDestroyed) {
                    UiccCard.this.loge("Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
                    return;
                }
                switch (msg.what) {
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
                        AsyncResult ar = (AsyncResult) msg.obj;
                        if (ar.exception != null) {
                            UiccCard.this.log("Error in SIM access with exception" + ar.exception);
                        }
                        AsyncResult.forMessage((Message) ar.userObj, ar.result, ar.exception);
                        ((Message) ar.userObj).sendToTarget();
                        return;
                    case 20:
                        UiccCard.this.onCarrierPriviligesLoadedMessage();
                        return;
                    default:
                        UiccCard.this.loge("Unknown Event " + msg.what);
                        return;
                }
            }
        };
    }

    public void dispose() {
        synchronized (this.mLock) {
            log("Disposing card");
            if (this.mCatService != null) {
                CatServiceFactory.disposeCatService(this.mPhoneId);
            }
            UiccCardApplication[] arr$ = this.mUiccApplications;
            for (UiccCardApplication app : arr$) {
                if (app != null) {
                    app.dispose();
                }
            }
            this.mCatService = null;
            this.mUiccApplications = null;
            this.mCarrierPrivilegeRules = null;
        }
    }

    public void update(Context c, CommandsInterface ci, IccCardStatus ics) {
        synchronized (this.mLock) {
            if (this.mDestroyed) {
                loge("Updated after destroyed! Fix me!");
                return;
            }
            IccCardStatus.CardState oldState = this.mCardState;
            this.mCardState = ics.mCardState;
            this.mUniversalPinState = ics.mUniversalPinState;
            this.mGsmUmtsSubscriptionAppIndex = ics.mGsmUmtsSubscriptionAppIndex;
            this.mCdmaSubscriptionAppIndex = ics.mCdmaSubscriptionAppIndex;
            this.mImsSubscriptionAppIndex = ics.mImsSubscriptionAppIndex;
            this.mContext = c;
            this.mCi = ci;
            log(ics.mApplications.length + " applications");
            for (int i = 0; i < this.mUiccApplications.length; i++) {
                if (this.mUiccApplications[i] == null) {
                    if (i < ics.mApplications.length) {
                        this.mUiccApplications[i] = new UiccCardApplication(this, ics.mApplications[i], this.mContext, this.mCi);
                    }
                } else if (i >= ics.mApplications.length) {
                    this.mUiccApplications[i].dispose();
                    this.mUiccApplications[i] = null;
                } else {
                    this.mUiccApplications[i].update(ics.mApplications[i], this.mContext, this.mCi);
                }
            }
            createAndUpdateCatService();
            log("Before privilege rules: " + this.mCarrierPrivilegeRules + " : " + this.mCardState);
            if (this.mCarrierPrivilegeRules == null && this.mCardState == IccCardStatus.CardState.CARDSTATE_PRESENT) {
                this.mCarrierPrivilegeRules = new UiccCarrierPrivilegeRules(this, this.mHandler.obtainMessage(20));
            } else if (!(this.mCarrierPrivilegeRules == null || this.mCardState == IccCardStatus.CardState.CARDSTATE_PRESENT)) {
                this.mCarrierPrivilegeRules = null;
            }
            sanitizeApplicationIndexes();
            CommandsInterface.RadioState radioState = this.mCi.getRadioState();
            log("update: radioState=" + radioState + " mLastRadioState=" + this.mLastRadioState);
            if (radioState == CommandsInterface.RadioState.RADIO_ON && this.mLastRadioState == CommandsInterface.RadioState.RADIO_ON) {
                if (oldState != IccCardStatus.CardState.CARDSTATE_ABSENT && this.mCardState == IccCardStatus.CardState.CARDSTATE_ABSENT) {
                    log("update: notify card removed");
                    this.mAbsentRegistrants.notifyRegistrants();
                    this.mHandler.sendMessage(this.mHandler.obtainMessage(13, null));
                } else if (oldState == IccCardStatus.CardState.CARDSTATE_ABSENT && this.mCardState != IccCardStatus.CardState.CARDSTATE_ABSENT) {
                    log("update: notify card added");
                    this.mHandler.sendMessage(this.mHandler.obtainMessage(14, null));
                }
            }
            this.mLastRadioState = radioState;
        }
    }

    protected void createAndUpdateCatService() {
        if (this.mUiccApplications.length <= 0 || this.mUiccApplications[0] == null) {
            if (this.mCatService != null) {
                CatServiceFactory.disposeCatService(this.mPhoneId);
            }
            this.mCatService = null;
        } else if (this.mCatService == null) {
            this.mCatService = CatServiceFactory.makeCatService(this.mCi, this.mContext, this, this.mPhoneId);
        }
    }

    public CatService getCatService() {
        return this.mCatService;
    }

    protected void finalize() {
        log("UiccCard finalized");
    }

    private void sanitizeApplicationIndexes() {
        this.mGsmUmtsSubscriptionAppIndex = checkIndex(this.mGsmUmtsSubscriptionAppIndex, IccCardApplicationStatus.AppType.APPTYPE_SIM, IccCardApplicationStatus.AppType.APPTYPE_USIM);
        this.mCdmaSubscriptionAppIndex = checkIndex(this.mCdmaSubscriptionAppIndex, IccCardApplicationStatus.AppType.APPTYPE_RUIM, IccCardApplicationStatus.AppType.APPTYPE_CSIM);
        this.mImsSubscriptionAppIndex = checkIndex(this.mImsSubscriptionAppIndex, IccCardApplicationStatus.AppType.APPTYPE_ISIM, null);
    }

    private int checkIndex(int index, IccCardApplicationStatus.AppType expectedAppType, IccCardApplicationStatus.AppType altExpectedAppType) {
        if (this.mUiccApplications == null || index >= this.mUiccApplications.length) {
            loge("App index " + index + " is invalid since there are no applications");
            return -1;
        } else if (index < 0) {
            return -1;
        } else {
            if (this.mUiccApplications[index].getType() == expectedAppType || this.mUiccApplications[index].getType() == altExpectedAppType) {
                return index;
            }
            loge("App index " + index + " is invalid since it's not " + expectedAppType + " and not " + altExpectedAppType);
            return -1;
        }
    }

    public void registerForAbsent(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mAbsentRegistrants.add(r);
            if (this.mCardState == IccCardStatus.CardState.CARDSTATE_ABSENT) {
                r.notifyRegistrant();
            }
        }
    }

    public void unregisterForAbsent(Handler h) {
        synchronized (this.mLock) {
            this.mAbsentRegistrants.remove(h);
        }
    }

    public void registerForCarrierPrivilegeRulesLoaded(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mCarrierPrivilegeRegistrants.add(r);
            if (areCarrierPriviligeRulesLoaded()) {
                r.notifyRegistrant();
            }
        }
    }

    public void unregisterForCarrierPrivilegeRulesLoaded(Handler h) {
        synchronized (this.mLock) {
            this.mCarrierPrivilegeRegistrants.remove(h);
        }
    }

    public void onIccSwap(boolean isAdded) {
        if (this.mContext.getResources().getBoolean(17956931)) {
            log("onIccSwap: isHotSwapSupported is true, don't prompt for rebooting");
            return;
        }
        log("onIccSwap: isHotSwapSupported is false, prompt for rebooting");
        synchronized (this.mLock) {
            try {
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() { // from class: com.android.internal.telephony.uicc.UiccCard.1
                    @Override // android.content.DialogInterface.OnClickListener
                    public void onClick(DialogInterface dialog, int which) {
                        synchronized (UiccCard.this.mLock) {
                            if (which == -1) {
                                UiccCard.this.log("Reboot due to SIM swap");
                                ((PowerManager) UiccCard.this.mContext.getSystemService("power")).reboot("SIM is added.");
                            }
                        }
                    }
                };
                try {
                    Resources r = Resources.getSystem();
                    AlertDialog dialog = new AlertDialog.Builder(this.mContext).setTitle(isAdded ? r.getString(17040694) : r.getString(17040691)).setMessage(isAdded ? r.getString(17040695) : r.getString(17040692)).setPositiveButton(r.getString(17040696), listener).create();
                    dialog.getWindow().setType(2003);
                    dialog.show();
                } catch (Throwable th) {
                    th = th;
                    throw th;
                }
            } catch (Throwable th2) {
                th = th2;
            }
        }
    }

    public void onCarrierPriviligesLoadedMessage() {
        synchronized (this.mLock) {
            this.mCarrierPrivilegeRegistrants.notifyRegistrants();
        }
    }

    public boolean isApplicationOnIcc(IccCardApplicationStatus.AppType type) {
        boolean z;
        synchronized (this.mLock) {
            int i = 0;
            while (true) {
                if (i < this.mUiccApplications.length) {
                    if (this.mUiccApplications[i] != null && this.mUiccApplications[i].getType() == type) {
                        z = true;
                        break;
                    }
                    i++;
                } else {
                    z = false;
                    break;
                }
            }
        }
        return z;
    }

    public IccCardStatus.CardState getCardState() {
        IccCardStatus.CardState cardState;
        synchronized (this.mLock) {
            cardState = this.mCardState;
        }
        return cardState;
    }

    public IccCardStatus.PinState getUniversalPinState() {
        IccCardStatus.PinState pinState;
        synchronized (this.mLock) {
            pinState = this.mUniversalPinState;
        }
        return pinState;
    }

    public UiccCardApplication getApplication(int family) {
        UiccCardApplication uiccCardApplication;
        synchronized (this.mLock) {
            int index = 8;
            switch (family) {
                case 1:
                    index = this.mGsmUmtsSubscriptionAppIndex;
                    break;
                case 2:
                    index = this.mCdmaSubscriptionAppIndex;
                    break;
                case 3:
                    index = this.mImsSubscriptionAppIndex;
                    break;
            }
            if (index < 0 || index >= this.mUiccApplications.length) {
                uiccCardApplication = null;
            } else {
                uiccCardApplication = this.mUiccApplications[index];
            }
        }
        return uiccCardApplication;
    }

    public UiccCardApplication getApplicationIndex(int index) {
        UiccCardApplication uiccCardApplication;
        synchronized (this.mLock) {
            if (index >= 0) {
                if (index < this.mUiccApplications.length) {
                    uiccCardApplication = this.mUiccApplications[index];
                }
            }
            uiccCardApplication = null;
        }
        return uiccCardApplication;
    }

    public UiccCardApplication getApplicationByType(int type) {
        UiccCardApplication uiccCardApplication;
        synchronized (this.mLock) {
            int i = 0;
            while (true) {
                if (i < this.mUiccApplications.length) {
                    if (this.mUiccApplications[i] != null && this.mUiccApplications[i].getType().ordinal() == type) {
                        uiccCardApplication = this.mUiccApplications[i];
                        break;
                    }
                    i++;
                } else {
                    uiccCardApplication = null;
                    break;
                }
            }
        }
        return uiccCardApplication;
    }

    public void iccOpenLogicalChannel(String AID, Message response) {
        this.mCi.iccOpenLogicalChannel(AID, this.mHandler.obtainMessage(15, response));
    }

    public void iccCloseLogicalChannel(int channel, Message response) {
        this.mCi.iccCloseLogicalChannel(channel, this.mHandler.obtainMessage(16, response));
    }

    public void iccTransmitApduLogicalChannel(int channel, int cla, int command, int p1, int p2, int p3, String data, Message response) {
        this.mCi.iccTransmitApduLogicalChannel(channel, cla, command, p1, p2, p3, data, this.mHandler.obtainMessage(17, response));
    }

    public void iccTransmitApduBasicChannel(int cla, int command, int p1, int p2, int p3, String data, Message response) {
        this.mCi.iccTransmitApduBasicChannel(cla, command, p1, p2, p3, data, this.mHandler.obtainMessage(18, response));
    }

    public void iccExchangeSimIO(int fileID, int command, int p1, int p2, int p3, String pathID, Message response) {
        this.mCi.iccIO(command, fileID, pathID, p1, p2, p3, null, null, this.mHandler.obtainMessage(19, response));
    }

    public void getAtr(Message response) {
        this.mCi.getAtr(this.mHandler.obtainMessage(21, response));
    }

    public void sendEnvelopeWithStatus(String contents, Message response) {
        this.mCi.sendEnvelopeWithStatus(contents, response);
    }

    public int getNumApplications() {
        int count = 0;
        for (UiccCardApplication a : this.mUiccApplications) {
            if (a != null) {
                count++;
            }
        }
        return count;
    }

    public int getPhoneId() {
        return this.mPhoneId;
    }

    public boolean areCarrierPriviligeRulesLoaded() {
        return this.mCarrierPrivilegeRules != null && this.mCarrierPrivilegeRules.areCarrierPriviligeRulesLoaded();
    }

    public int getCarrierPrivilegeStatus(Signature signature, String packageName) {
        if (this.mCarrierPrivilegeRules == null) {
            return -1;
        }
        return this.mCarrierPrivilegeRules.getCarrierPrivilegeStatus(signature, packageName);
    }

    public int getCarrierPrivilegeStatus(PackageManager packageManager, String packageName) {
        if (this.mCarrierPrivilegeRules == null) {
            return -1;
        }
        return this.mCarrierPrivilegeRules.getCarrierPrivilegeStatus(packageManager, packageName);
    }

    public int getCarrierPrivilegeStatusForCurrentTransaction(PackageManager packageManager) {
        if (this.mCarrierPrivilegeRules == null) {
            return -1;
        }
        return this.mCarrierPrivilegeRules.getCarrierPrivilegeStatusForCurrentTransaction(packageManager);
    }

    public List<String> getCarrierPackageNamesForIntent(PackageManager packageManager, Intent intent) {
        if (this.mCarrierPrivilegeRules == null) {
            return null;
        }
        return this.mCarrierPrivilegeRules.getCarrierPackageNamesForIntent(packageManager, intent);
    }

    public boolean setOperatorBrandOverride(String brand) {
        log("setOperatorBrandOverride: " + brand);
        log("current iccId: " + getIccId());
        String iccId = getIccId();
        if (TextUtils.isEmpty(iccId)) {
            return false;
        }
        SharedPreferences.Editor spEditor = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
        String key = OPERATOR_BRAND_OVERRIDE_PREFIX + iccId;
        if (brand == null) {
            spEditor.remove(key).commit();
        } else {
            spEditor.putString(key, brand).commit();
        }
        return true;
    }

    public String getOperatorBrandOverride() {
        String iccId = getIccId();
        if (TextUtils.isEmpty(iccId)) {
            return null;
        }
        return PreferenceManager.getDefaultSharedPreferences(this.mContext).getString(OPERATOR_BRAND_OVERRIDE_PREFIX + iccId, null);
    }

    public String getIccId() {
        IccRecords ir;
        UiccCardApplication[] arr$ = this.mUiccApplications;
        for (UiccCardApplication app : arr$) {
            if (!(app == null || (ir = app.getIccRecords()) == null || ir.getIccId() == null)) {
                return ir.getIccId();
            }
        }
        return null;
    }

    public void onRefresh(IccRefreshResponse refreshResponse) {
        for (int i = 0; i < this.mUiccApplications.length; i++) {
            if (this.mUiccApplications[i] != null) {
                this.mUiccApplications[i].onRefresh(refreshResponse);
            }
        }
    }

    protected void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    public void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    public int getIccPinPukRetryCountSc(int isPinPuk) {
        switch (isPinPuk) {
            case 1:
                int retValue = this.mPin1RetryCountSc;
                log("Get mPin1RetryCountSc = " + this.mPin1RetryCountSc);
                return retValue;
            case 2:
                int retValue2 = this.mPuk1RetryCountSc;
                log("Get mPuk1RetryCountSc = " + this.mPuk1RetryCountSc);
                return retValue2;
            case 3:
                int retValue3 = this.mPin2RetryCountSc;
                log("Get mPin2RetryCountSc = " + this.mPin2RetryCountSc);
                return retValue3;
            case 4:
                int retValue4 = this.mPuk2RetryCountSc;
                log("Get mPuk2RetryCountSc = " + this.mPuk2RetryCountSc);
                return retValue4;
            default:
                log("Incorrect request. PIN/PUK request = " + isPinPuk);
                return -1;
        }
    }

    public void setPinPukRetryCount(int isPinPuk, int retryCount) {
        synchronized (this.mLock) {
            switch (isPinPuk) {
                case 0:
                    this.mPin1RetryCountSc = retryCount;
                    this.mPuk1RetryCountSc = retryCount;
                    this.mPin2RetryCountSc = retryCount;
                    this.mPuk2RetryCountSc = retryCount;
                    log("Initialize PIN/PUK retry count = " + retryCount);
                    break;
                case 1:
                    this.mPin1RetryCountSc = retryCount;
                    log("Set mPin1RetryCountSc = " + this.mPin1RetryCountSc);
                    break;
                case 2:
                    this.mPuk1RetryCountSc = retryCount;
                    log("Set mPuk1RetryCountSc = " + this.mPuk1RetryCountSc);
                    break;
                case 3:
                    this.mPin2RetryCountSc = retryCount;
                    log("Set mPin2RetryCountSc = " + this.mPin2RetryCountSc);
                    break;
                case 4:
                    this.mPuk2RetryCountSc = retryCount;
                    log("Set mPuk2RetryCountSc = " + this.mPuk2RetryCountSc);
                    break;
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        IccRecords ir;
        pw.println("UiccCard:");
        pw.println(" mCi=" + this.mCi);
        pw.println(" mDestroyed=" + this.mDestroyed);
        pw.println(" mLastRadioState=" + this.mLastRadioState);
        pw.println(" mCatService=" + this.mCatService);
        pw.println(" mAbsentRegistrants: size=" + this.mAbsentRegistrants.size());
        for (int i = 0; i < this.mAbsentRegistrants.size(); i++) {
            pw.println("  mAbsentRegistrants[" + i + "]=" + ((Registrant) this.mAbsentRegistrants.get(i)).getHandler());
        }
        for (int i2 = 0; i2 < this.mCarrierPrivilegeRegistrants.size(); i2++) {
            pw.println("  mCarrierPrivilegeRegistrants[" + i2 + "]=" + ((Registrant) this.mCarrierPrivilegeRegistrants.get(i2)).getHandler());
        }
        pw.println(" mCardState=" + this.mCardState);
        pw.println(" mUniversalPinState=" + this.mUniversalPinState);
        pw.println(" mGsmUmtsSubscriptionAppIndex=" + this.mGsmUmtsSubscriptionAppIndex);
        pw.println(" mCdmaSubscriptionAppIndex=" + this.mCdmaSubscriptionAppIndex);
        pw.println(" mImsSubscriptionAppIndex=" + this.mImsSubscriptionAppIndex);
        pw.println(" mImsSubscriptionAppIndex=" + this.mImsSubscriptionAppIndex);
        pw.println(" mUiccApplications: length=" + this.mUiccApplications.length);
        for (int i3 = 0; i3 < this.mUiccApplications.length; i3++) {
            if (this.mUiccApplications[i3] == null) {
                pw.println("  mUiccApplications[" + i3 + "]=" + ((Object) null));
            } else {
                pw.println("  mUiccApplications[" + i3 + "]=" + this.mUiccApplications[i3].getType() + " " + this.mUiccApplications[i3]);
            }
        }
        pw.println();
        UiccCardApplication[] arr$ = this.mUiccApplications;
        for (UiccCardApplication app : arr$) {
            if (app != null) {
                app.dump(fd, pw, args);
                pw.println();
            }
        }
        UiccCardApplication[] arr$2 = this.mUiccApplications;
        for (UiccCardApplication app2 : arr$2) {
            if (!(app2 == null || (ir = app2.getIccRecords()) == null)) {
                ir.dump(fd, pw, args);
                pw.println();
            }
        }
        if (this.mCarrierPrivilegeRules == null) {
            pw.println(" mCarrierPrivilegeRules: null");
        } else {
            pw.println(" mCarrierPrivilegeRules: " + this.mCarrierPrivilegeRules);
            this.mCarrierPrivilegeRules.dump(fd, pw, args);
        }
        pw.println(" mCarrierPrivilegeRegistrants: size=" + this.mCarrierPrivilegeRegistrants.size());
        for (int i4 = 0; i4 < this.mCarrierPrivilegeRegistrants.size(); i4++) {
            pw.println("  mCarrierPrivilegeRegistrants[" + i4 + "]=" + ((Registrant) this.mCarrierPrivilegeRegistrants.get(i4)).getHandler());
        }
        pw.flush();
    }
}
