package com.android.internal.telephony.uicc;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.TelephonyEventLog;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.nttdocomo.android.portablesim.service.IPortableSimAppService.Stub;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.LinkedList;

public class UiccController extends Handler {
    public static final int APP_FAM_3GPP = 1;
    public static final int APP_FAM_3GPP2 = 2;
    public static final int APP_FAM_IMS = 3;
    private static final boolean DBG = true;
    private static final String DECRYPT_STATE = "trigger_restart_framework";
    private static final int EVENT_DISCONNECT = 8;
    private static final int EVENT_GET_ICC_STATUS_DONE = 2;
    private static final int EVENT_ICC_STATUS_CHANGED = 1;
    private static final int EVENT_RADIO_UNAVAILABLE = 3;
    private static final int EVENT_RESET = 7;
    private static final int EVENT_RETRY_ICC_STATUS = 11;
    private static final int EVENT_SIM_REFRESH = 4;
    private static final String LOG_TAG = "UiccController";
    private static final int MAX_PROACTIVE_COMMANDS_TO_LOG = 20;
    private static final int POLL_PERIOD_MILLIS_15 = 15000;
    private static final String PROPERTY_SIM_HOTSWAP = "ril.uim.hotswap";
    private static final String PROPERTY_UIM_DET = "ril.uim.det";
    private static final int PSIM_CONNECTION_STATE = 255;
    private static final int PSIM_SLAVE = 512;
    private static final int PSIM_SWITCHING = 2;
    private static final int RESET_DELAY_TIME = 5000;
    private static final String SIM_HOTSWAP_ADDED = "ADDED";
    private static final String SIM_HOTSWAP_NONE = "NONE";
    private static final String SIM_HOTSWAP_REMOVED = "REMOVED";
    private static final int UIM_DET_ABSENT = 1;
    private static UiccController mInstance;
    private static final Object mLock = new Object();
    private LinkedList<String> mCardLogs = new LinkedList();
    private CommandsInterface[] mCis;
    private Context mContext;
    private boolean mFatalSimMsgFlag = false;
    protected RegistrantList mIccChangedRegistrants = new RegistrantList();
    private Phone mPhone = null;
    private boolean mResetFlag = false;
    private UiccCard[] mUiccCards = new UiccCard[TelephonyManager.getDefault().getPhoneCount()];

    private abstract class PSimServiceConnection implements ServiceConnection {
        protected Object mParam = null;

        public abstract void onFailedToConnectService();

        public abstract void onFailedToGetState();

        public abstract void onSucceededToGetState(int i);

        PSimServiceConnection(Object param) {
            this.mParam = param;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                onSucceededToGetState(Stub.asInterface(service).getConnetionState());
            } catch (RemoteException e) {
                onFailedToGetState();
            }
        }

        public void onServiceDisconnected(ComponentName name) {
        }

        public void getPSimState() {
            Intent intent = new Intent();
            intent.setClassName("com.nttdocomo.android.portablesim", "com.nttdocomo.android.portablesim.service.PortableSimAppService");
            if (!UiccController.this.mContext.bindService(intent, this, 1)) {
                onFailedToConnectService();
            }
        }
    }

    public static UiccController make(Context c, CommandsInterface[] ci) {
        UiccController uiccController;
        synchronized (mLock) {
            if (mInstance != null) {
                throw new RuntimeException("MSimUiccController.make() should only be called once");
            }
            mInstance = new UiccController(c, ci);
            uiccController = mInstance;
        }
        return uiccController;
    }

    private UiccController(Context c, CommandsInterface[] ci) {
        log("Creating UiccController");
        this.mContext = c;
        this.mCis = ci;
        for (int i = 0; i < this.mCis.length; i++) {
            Integer index = new Integer(i);
            this.mCis[i].registerForIccStatusChanged(this, 1, index);
            this.mCis[i].registerForAvailable(this, 1, index);
            this.mCis[i].registerForNotAvailable(this, 3, index);
            this.mCis[i].registerForIccRefresh(this, 4, index);
        }
    }

    public static UiccController getInstance() {
        UiccController uiccController;
        synchronized (mLock) {
            if (mInstance == null) {
                throw new RuntimeException("UiccController.getInstance can't be called before make()");
            }
            uiccController = mInstance;
        }
        return uiccController;
    }

    public UiccCard getUiccCard(int phoneId) {
        synchronized (mLock) {
            if (isValidCardIndex(phoneId)) {
                UiccCard uiccCard = this.mUiccCards[phoneId];
                return uiccCard;
            }
            return null;
        }
    }

    public UiccCard[] getUiccCards() {
        UiccCard[] uiccCardArr;
        synchronized (mLock) {
            uiccCardArr = (UiccCard[]) this.mUiccCards.clone();
        }
        return uiccCardArr;
    }

    public IccRecords getIccRecords(int phoneId, int family) {
        synchronized (mLock) {
            UiccCardApplication app = getUiccCardApplication(phoneId, family);
            if (app != null) {
                IccRecords iccRecords = app.getIccRecords();
                return iccRecords;
            }
            return null;
        }
    }

    public IccFileHandler getIccFileHandler(int phoneId, int family) {
        synchronized (mLock) {
            UiccCardApplication app = getUiccCardApplication(phoneId, family);
            if (app != null) {
                IccFileHandler iccFileHandler = app.getIccFileHandler();
                return iccFileHandler;
            }
            return null;
        }
    }

    public void registerForIccChanged(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mIccChangedRegistrants.add(r);
            r.notifyRegistrant();
        }
    }

    public void unregisterForIccChanged(Handler h) {
        synchronized (mLock) {
            this.mIccChangedRegistrants.remove(h);
        }
    }

    /* JADX WARNING: Missing block: B:16:0x0062, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void handleMessage(Message msg) {
        synchronized (mLock) {
            Integer index = getCiIndex(msg);
            if (index.intValue() >= 0 && index.intValue() < this.mCis.length) {
                switch (msg.what) {
                    case 1:
                        log("Received EVENT_ICC_STATUS_CHANGED, calling getIccCardStatus");
                        this.mCis[index.intValue()].getIccCardStatus(obtainMessage(2, index));
                        String hotswap = SystemProperties.get(PROPERTY_SIM_HOTSWAP);
                        if (!SIM_HOTSWAP_ADDED.equals(hotswap)) {
                            if (!SIM_HOTSWAP_REMOVED.equals(hotswap)) {
                                if (SIM_HOTSWAP_NONE.equals(hotswap)) {
                                    break;
                                }
                            } else if (!TelBrand.IS_DCM) {
                                log("SystemProperties : " + hotswap + " calling gotoReset(false)");
                                gotoReset(false);
                                break;
                            } else {
                                new PSimServiceConnection(this, hotswap) {
                                    /* Access modifiers changed, original: protected */
                                    public void onFailedToConnectService() {
                                        this.log("cannot connect to PortableSimAppService");
                                        this.log("SystemProperties : " + ((String) this.mParam) + " calling gotoReset(false)");
                                        this.gotoReset(false);
                                    }

                                    /* Access modifiers changed, original: protected */
                                    public void onSucceededToGetState(int connState) {
                                        if ((connState & 2) != 0) {
                                            SystemProperties.set(UiccController.PROPERTY_SIM_HOTSWAP, UiccController.SIM_HOTSWAP_NONE);
                                            this.log("switching sim and psim. avoid rebooting.");
                                            return;
                                        }
                                        this.log("SystemProperties : " + ((String) this.mParam) + " calling gotoReset(false)");
                                        this.gotoReset(false);
                                    }

                                    /* Access modifiers changed, original: protected */
                                    public void onFailedToGetState() {
                                        this.log("SystemProperties : " + ((String) this.mParam) + " calling gotoReset(false)");
                                        this.gotoReset(false);
                                    }
                                }.getPSimState();
                                break;
                            }
                        } else if (!TelBrand.IS_DCM) {
                            log("SystemProperties : " + hotswap + " calling gotoReset(ture)");
                            gotoReset(true);
                            break;
                        } else {
                            new PSimServiceConnection(this, hotswap) {
                                /* Access modifiers changed, original: protected */
                                public void onFailedToConnectService() {
                                    this.log("cannot connect to PortableSimAppService");
                                    this.log("SystemProperties : " + ((String) this.mParam) + " calling gotoReset(ture)");
                                    this.gotoReset(true);
                                }

                                /* Access modifiers changed, original: protected */
                                public void onSucceededToGetState(int connState) {
                                    if ((connState & 2) != 0) {
                                        SystemProperties.set(UiccController.PROPERTY_SIM_HOTSWAP, UiccController.SIM_HOTSWAP_NONE);
                                        this.log("switching sim and psim. avoid rebooting.");
                                        return;
                                    }
                                    this.log("SystemProperties : " + ((String) this.mParam) + " calling gotoReset(ture)");
                                    this.gotoReset(true);
                                }

                                /* Access modifiers changed, original: protected */
                                public void onFailedToGetState() {
                                    this.log("SystemProperties : " + ((String) this.mParam) + " calling gotoReset(ture)");
                                    this.gotoReset(true);
                                }
                            }.getPSimState();
                            break;
                        }
                        break;
                    case 2:
                        log("Received EVENT_GET_ICC_STATUS_DONE");
                        onGetIccCardStatusDone(msg.obj, index);
                        break;
                    case 3:
                        log("EVENT_RADIO_UNAVAILABLE, dispose card");
                        if (this.mUiccCards[index.intValue()] != null) {
                            this.mUiccCards[index.intValue()].dispose();
                        }
                        this.mUiccCards[index.intValue()] = null;
                        this.mIccChangedRegistrants.notifyRegistrants(new AsyncResult(null, index, null));
                        break;
                    case 4:
                        log("Received EVENT_SIM_REFRESH");
                        onSimRefresh(msg.obj, index);
                        break;
                    case 7:
                        resetAtNewThread();
                        break;
                    case 8:
                        log("Received EVENT_DISCONNECT calling gotoReset(false) ");
                        gotoReset(false);
                        break;
                    case 11:
                        if (this.mUiccCards[index.intValue()] == null) {
                            Rlog.d(LOG_TAG, "Retry getIccCardStatus mUiccCards[index]=" + this.mUiccCards[index.intValue()]);
                            sendMessage(obtainMessage(1, index));
                            break;
                        }
                        break;
                    default:
                        Rlog.e(LOG_TAG, " Unknown Event " + msg.what);
                        break;
                }
            }
            Rlog.e(LOG_TAG, "Invalid index : " + index + " received with event " + msg.what);
        }
    }

    private Integer getCiIndex(Message msg) {
        Integer index = new Integer(0);
        if (msg == null) {
            return index;
        }
        if (msg.obj != null && (msg.obj instanceof Integer)) {
            return msg.obj;
        }
        if (msg.obj == null || !(msg.obj instanceof AsyncResult)) {
            return index;
        }
        AsyncResult ar = msg.obj;
        if (ar.userObj == null || !(ar.userObj instanceof Integer)) {
            return index;
        }
        return ar.userObj;
    }

    /* JADX WARNING: Missing block: B:11:0x001b, code skipped:
            return null;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public UiccCardApplication getUiccCardApplication(int phoneId, int family) {
        synchronized (mLock) {
            if (!isValidCardIndex(phoneId) || this.mUiccCards[phoneId] == null) {
            } else {
                UiccCardApplication application = this.mUiccCards[phoneId].getApplication(family);
                return application;
            }
        }
    }

    private synchronized void onGetIccCardStatusDone(AsyncResult ar, Integer index) {
        if (ar.exception != null) {
            Rlog.e(LOG_TAG, "Error getting ICC status. RIL_REQUEST_GET_ICC_STATUS should never return an error", ar.exception);
        } else if (isValidCardIndex(index.intValue())) {
            IccCardStatus status = ar.result;
            if (this.mUiccCards[index.intValue()] != null || status.mCardState != CardState.CARDSTATE_PRESENT || status.mGsmUmtsSubscriptionAppIndex >= 0 || status.mCdmaSubscriptionAppIndex >= 0 || status.mImsSubscriptionAppIndex >= 0) {
                if (!(this.mFatalSimMsgFlag || this.mResetFlag)) {
                    int det = SystemProperties.getInt(PROPERTY_UIM_DET, -1);
                    String hotswap = SystemProperties.get(PROPERTY_SIM_HOTSWAP);
                    if (det == 1 && status.mCardState == CardState.CARDSTATE_PRESENT && !SIM_HOTSWAP_ADDED.equals(hotswap)) {
                        if (!SIM_HOTSWAP_REMOVED.equals(hotswap)) {
                            if (TelBrand.IS_DCM) {
                                new PSimServiceConnection(this, null) {
                                    /* Access modifiers changed, original: protected */
                                    public void onFailedToConnectService() {
                                        this.log("cannot connect to PortableSimAppService");
                                        this.dispFatalSimMsg();
                                    }

                                    /* Access modifiers changed, original: protected */
                                    public void onSucceededToGetState(int connState) {
                                        if ((connState & UiccController.PSIM_SLAVE) == 0 || (connState & 255) == 0) {
                                            this.dispFatalSimMsg();
                                        } else {
                                            this.log("psim is enabled. avoid showing uim unstable message.");
                                        }
                                    }

                                    /* Access modifiers changed, original: protected */
                                    public void onFailedToGetState() {
                                        this.dispFatalSimMsg();
                                    }
                                }.getPSimState();
                            } else {
                                dispFatalSimMsg();
                            }
                        }
                    }
                }
                if (this.mUiccCards[index.intValue()] == null) {
                    this.mUiccCards[index.intValue()] = new UiccCard(this.mContext, this.mCis[index.intValue()], status, index.intValue());
                } else {
                    this.mUiccCards[index.intValue()].update(this.mContext, this.mCis[index.intValue()], status);
                }
                log("Notifying IccChangedRegistrants");
                this.mIccChangedRegistrants.notifyRegistrants(new AsyncResult(null, index, null));
                return;
            }
            sendMessageDelayed(obtainMessage(11, index), 15000);
            Rlog.d(LOG_TAG, "sendMessageDelayed 15000 msc");
        } else {
            Rlog.e(LOG_TAG, "onGetIccCardStatusDone: invalid index : " + index);
        }
    }

    private void onSimRefresh(AsyncResult ar, Integer index) {
        if (ar.exception != null) {
            Rlog.e(LOG_TAG, "Sim REFRESH with exception: " + ar.exception);
        } else if (isValidCardIndex(index.intValue())) {
            IccRefreshResponse resp = ar.result;
            Rlog.d(LOG_TAG, "onSimRefresh: " + resp);
            if (resp == null) {
                Rlog.e(LOG_TAG, "onSimRefresh: received without input");
            } else if (this.mUiccCards[index.intValue()] == null) {
                Rlog.e(LOG_TAG, "onSimRefresh: refresh on null card : " + index);
            } else {
                Rlog.d(LOG_TAG, "Handling refresh: " + resp);
                switch (resp.refreshResult) {
                    case 1:
                    case 2:
                        if (this.mUiccCards[index.intValue()].resetAppWithAid(resp.aid) && resp.refreshResult == 2 && this.mContext.getResources().getBoolean(17956995)) {
                            this.mCis[index.intValue()].setRadioPower(false, null);
                        }
                        this.mCis[index.intValue()].getIccCardStatus(obtainMessage(2));
                        return;
                    default:
                        return;
                }
            }
        } else {
            Rlog.e(LOG_TAG, "onSimRefresh: invalid index : " + index);
        }
    }

    private boolean isValidCardIndex(int index) {
        return index >= 0 && index < this.mUiccCards.length;
    }

    private void log(String string) {
        Rlog.d(LOG_TAG, string);
    }

    public void addCardLog(String data) {
        Time t = new Time();
        t.setToNow();
        this.mCardLogs.addLast(t.format("%m-%d %H:%M:%S") + " " + data);
        if (this.mCardLogs.size() > 20) {
            this.mCardLogs.removeFirst();
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        int i;
        pw.println("UiccController: " + this);
        pw.println(" mContext=" + this.mContext);
        pw.println(" mInstance=" + mInstance);
        pw.println(" mIccChangedRegistrants: size=" + this.mIccChangedRegistrants.size());
        for (i = 0; i < this.mIccChangedRegistrants.size(); i++) {
            pw.println("  mIccChangedRegistrants[" + i + "]=" + ((Registrant) this.mIccChangedRegistrants.get(i)).getHandler());
        }
        pw.println();
        pw.flush();
        pw.println(" mUiccCards: size=" + this.mUiccCards.length);
        for (i = 0; i < this.mUiccCards.length; i++) {
            if (this.mUiccCards[i] == null) {
                pw.println("  mUiccCards[" + i + "]=null");
            } else {
                pw.println("  mUiccCards[" + i + "]=" + this.mUiccCards[i]);
                this.mUiccCards[i].dump(fd, pw, args);
            }
        }
        pw.println("mCardLogs: ");
        for (i = 0; i < this.mCardLogs.size(); i++) {
            pw.println("  " + ((String) this.mCardLogs.get(i)));
        }
    }

    public void setPhone(Phone phone) {
        synchronized (mLock) {
            this.mPhone = phone;
        }
    }

    public Phone getPhone() {
        Phone phone;
        synchronized (mLock) {
            phone = this.mPhone;
        }
        return phone;
    }

    private void onIccSwap(boolean isAdded) {
        String shutdownAction = SystemProperties.get("sys.shutdown.requested", "");
        if (shutdownAction != null && shutdownAction.length() > 0) {
            log("shutdown was started");
        } else if (this.mResetFlag) {
            log("onIccSwap is executed");
        } else {
            String title;
            String message;
            this.mResetFlag = true;
            Resources r = Resources.getSystem();
            if (isAdded) {
                title = r.getString(17040399);
            } else {
                title = r.getString(17040396);
            }
            if (isAdded) {
                message = r.getString(17040400);
            } else {
                message = r.getString(17040397);
            }
            String buttonTxt = r.getString(17040401);
            WakeLock wakeLock = ((PowerManager) this.mContext.getSystemService("power")).newWakeLock(268435462, LOG_TAG);
            wakeLock.acquire();
            AlertDialog dialog = new Builder(this.mContext).setTitle(title).setMessage(message).create();
            dialog.getWindow().setType(TelephonyEventLog.TAG_IMS_CALL_MERGE);
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
            dialog.getWindow().addFlags(2621440);
            sendMessageDelayed(obtainMessage(7), 5000);
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private void reboot() {
        log("Reboot due to SIM swap");
        ((PowerManager) this.mContext.getSystemService("power")).reboot("SIM1");
    }

    private void resetAtNewThread() {
        new Thread() {
            public void run() {
                UiccController.this.reboot();
            }
        }.start();
    }

    private void gotoReset(boolean isAdded) {
        if (Build.IS_DEBUGGABLE && Build.ID.startsWith("F")) {
            log("Manufacturing build. Not reboot.");
            return;
        }
        if (this.mPhone != null) {
            Phone imsphone = this.mPhone.getImsPhone();
            if (this.mPhone.getState() == State.OFFHOOK) {
                log("Wait for EVENT_DISCONNECT ");
                this.mPhone.registerForDisconnect(this, 8, null);
            } else if (imsphone == null || imsphone.getState() != State.OFFHOOK) {
                log("PhoneConstants.State OFFHOOK onIccSwap()");
                onIccSwap(isAdded);
            } else {
                log("Wait for EVENT_DISCONNECT ");
                imsphone.registerForDisconnect(this, 8, null);
            }
        } else {
            Rlog.e(LOG_TAG, "mPhone NULL calling onIccSwap ");
            onIccSwap(isAdded);
        }
    }

    private void dispFatalSimMsg() {
        this.mFatalSimMsgFlag = true;
        AlertDialog dialog = new Builder(this.mContext).setMessage(Resources.getSystem().getString(17040925)).setPositiveButton(17039370, null).create();
        dialog.getWindow().setType(TelephonyEventLog.TAG_IMS_CALL_MERGE);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        dialog.getWindow().addFlags(2621440);
    }
}
