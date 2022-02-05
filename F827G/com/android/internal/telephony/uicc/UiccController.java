package com.android.internal.telephony.uicc;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.nttdocomo.android.portablesim.service.IPortableSimAppService;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class UiccController extends Handler {
    public static final int APP_FAM_3GPP = 1;
    public static final int APP_FAM_3GPP2 = 2;
    public static final int APP_FAM_IMS = 3;
    public static final int APP_FAM_UNKNOWN = -1;
    private static final boolean DBG = true;
    private static final int EVENT_DISCONNECT = 8;
    private static final int EVENT_GET_ICC_STATUS_DONE = 2;
    private static final int EVENT_ICC_STATUS_CHANGED = 1;
    private static final int EVENT_RADIO_UNAVAILABLE = 3;
    private static final int EVENT_REFRESH = 4;
    private static final int EVENT_REFRESH_OEM = 5;
    private static final int EVENT_RESET = 7;
    private static final int EVENT_RETRY_ICC_STATUS = 11;
    private static final String LOG_TAG = "UiccController";
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
    private CommandsInterface[] mCis;
    private Context mContext;
    private boolean mOEMHookSimRefresh;
    private UiccCard[] mUiccCards = new UiccCard[TelephonyManager.getDefault().getPhoneCount()];
    private Phone mPhone = null;
    private boolean mResetFlag = false;
    private boolean mFatalSimMsgFlag = false;
    protected RegistrantList mIccChangedRegistrants = new RegistrantList();

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
        this.mOEMHookSimRefresh = false;
        log("Creating UiccController");
        this.mContext = c;
        this.mCis = ci;
        this.mOEMHookSimRefresh = this.mContext.getResources().getBoolean(17957016);
        for (int i = 0; i < this.mCis.length; i++) {
            Integer index = new Integer(i);
            this.mCis[i].registerForIccStatusChanged(this, 1, index);
            this.mCis[i].registerForAvailable(this, 1, index);
            this.mCis[i].registerForNotAvailable(this, 3, index);
            if (this.mOEMHookSimRefresh) {
                this.mCis[i].registerForSimRefreshEvent(this, 5, index);
            } else {
                this.mCis[i].registerForIccRefresh(this, 4, index);
            }
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

    public UiccCard getUiccCard() {
        return getUiccCard(0);
    }

    public UiccCard getUiccCard(int phoneId) {
        UiccCard uiccCard;
        synchronized (mLock) {
            uiccCard = isValidCardIndex(phoneId) ? this.mUiccCards[phoneId] : null;
        }
        return uiccCard;
    }

    public UiccCard[] getUiccCards() {
        UiccCard[] uiccCardArr;
        synchronized (mLock) {
            uiccCardArr = (UiccCard[]) this.mUiccCards.clone();
        }
        return uiccCardArr;
    }

    public UiccCardApplication getUiccCardApplication(int family) {
        return getUiccCardApplication(SubscriptionController.getInstance().getPhoneId(SubscriptionController.getInstance().getDefaultSubId()), family);
    }

    public IccRecords getIccRecords(int phoneId, int family) {
        IccRecords iccRecords;
        synchronized (mLock) {
            UiccCardApplication app = getUiccCardApplication(phoneId, family);
            iccRecords = app != null ? app.getIccRecords() : null;
        }
        return iccRecords;
    }

    public IccFileHandler getIccFileHandler(int phoneId, int family) {
        IccFileHandler iccFileHandler;
        synchronized (mLock) {
            UiccCardApplication app = getUiccCardApplication(phoneId, family);
            iccFileHandler = app != null ? app.getIccFileHandler() : null;
        }
        return iccFileHandler;
    }

    public static int getFamilyFromRadioTechnology(int radioTechnology) {
        if (ServiceState.isGsm(radioTechnology) || radioTechnology == 13) {
            return 1;
        }
        if (ServiceState.isCdma(radioTechnology)) {
            return 2;
        }
        return -1;
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

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        synchronized (mLock) {
            Integer index = getCiIndex(msg);
            if (index.intValue() < 0 || index.intValue() >= this.mCis.length) {
                Rlog.e(LOG_TAG, "Invalid index : " + index + " received with event " + msg.what);
                return;
            }
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
                            new PSimServiceConnection(hotswap) { // from class: com.android.internal.telephony.uicc.UiccController.2
                                @Override // com.android.internal.telephony.uicc.UiccController.PSimServiceConnection
                                protected void onFailedToConnectService() {
                                    UiccController.this.log("cannot connect to PortableSimAppService");
                                    UiccController.this.log("SystemProperties : " + ((String) this.mParam) + " calling gotoReset(false)");
                                    UiccController.this.gotoReset(false);
                                }

                                @Override // com.android.internal.telephony.uicc.UiccController.PSimServiceConnection
                                protected void onSucceededToGetState(int connState) {
                                    if ((connState & 2) != 0) {
                                        SystemProperties.set(UiccController.PROPERTY_SIM_HOTSWAP, UiccController.SIM_HOTSWAP_NONE);
                                        UiccController.this.log("switching sim and psim. avoid rebooting.");
                                        return;
                                    }
                                    UiccController.this.log("SystemProperties : " + ((String) this.mParam) + " calling gotoReset(false)");
                                    UiccController.this.gotoReset(false);
                                }

                                @Override // com.android.internal.telephony.uicc.UiccController.PSimServiceConnection
                                protected void onFailedToGetState() {
                                    UiccController.this.log("SystemProperties : " + ((String) this.mParam) + " calling gotoReset(false)");
                                    UiccController.this.gotoReset(false);
                                }
                            }.getPSimState();
                            break;
                        }
                    } else if (!TelBrand.IS_DCM) {
                        log("SystemProperties : " + hotswap + " calling gotoReset(ture)");
                        gotoReset(true);
                        break;
                    } else {
                        new PSimServiceConnection(hotswap) { // from class: com.android.internal.telephony.uicc.UiccController.1
                            @Override // com.android.internal.telephony.uicc.UiccController.PSimServiceConnection
                            protected void onFailedToConnectService() {
                                UiccController.this.log("cannot connect to PortableSimAppService");
                                UiccController.this.log("SystemProperties : " + ((String) this.mParam) + " calling gotoReset(ture)");
                                UiccController.this.gotoReset(true);
                            }

                            @Override // com.android.internal.telephony.uicc.UiccController.PSimServiceConnection
                            protected void onSucceededToGetState(int connState) {
                                if ((connState & 2) != 0) {
                                    SystemProperties.set(UiccController.PROPERTY_SIM_HOTSWAP, UiccController.SIM_HOTSWAP_NONE);
                                    UiccController.this.log("switching sim and psim. avoid rebooting.");
                                    return;
                                }
                                UiccController.this.log("SystemProperties : " + ((String) this.mParam) + " calling gotoReset(ture)");
                                UiccController.this.gotoReset(true);
                            }

                            @Override // com.android.internal.telephony.uicc.UiccController.PSimServiceConnection
                            protected void onFailedToGetState() {
                                UiccController.this.log("SystemProperties : " + ((String) this.mParam) + " calling gotoReset(ture)");
                                UiccController.this.gotoReset(true);
                            }
                        }.getPSimState();
                        break;
                    }
                    break;
                case 2:
                    log("Received EVENT_GET_ICC_STATUS_DONE");
                    onGetIccCardStatusDone((AsyncResult) msg.obj, index);
                    break;
                case 3:
                    log("EVENT_RADIO_UNAVAILABLE, dispose card");
                    if (this.mUiccCards[index.intValue()] != null) {
                        this.mUiccCards[index.intValue()].dispose();
                    }
                    this.mUiccCards[index.intValue()] = null;
                    this.mIccChangedRegistrants.notifyRegistrants(new AsyncResult((Object) null, index, (Throwable) null));
                    break;
                case 4:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    log("Sim REFRESH received");
                    if (ar.exception != null) {
                        log("Exception on refresh " + ar.exception);
                        break;
                    } else {
                        handleRefresh((IccRefreshResponse) ar.result, index.intValue());
                        break;
                    }
                case 5:
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    log("Sim REFRESH OEM received");
                    if (ar2.exception != null) {
                        log("Exception on refresh " + ar2.exception);
                        break;
                    } else {
                        handleRefresh(parseOemSimRefresh(ByteBuffer.wrap((byte[]) ar2.result)), index.intValue());
                        break;
                    }
                case 6:
                case 9:
                case 10:
                default:
                    Rlog.e(LOG_TAG, " Unknown Event " + msg.what);
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
            }
        }
    }

    private Integer getCiIndex(Message msg) {
        Integer index = new Integer(0);
        if (msg == null) {
            return index;
        }
        if (msg.obj != null && (msg.obj instanceof Integer)) {
            return (Integer) msg.obj;
        }
        if (msg.obj == null || !(msg.obj instanceof AsyncResult)) {
            return index;
        }
        AsyncResult ar = (AsyncResult) msg.obj;
        if (ar.userObj == null || !(ar.userObj instanceof Integer)) {
            return index;
        }
        return (Integer) ar.userObj;
    }

    private void handleRefresh(IccRefreshResponse refreshResponse, int index) {
        if (refreshResponse == null) {
            log("handleRefresh received without input");
            return;
        }
        if (this.mUiccCards[index] != null) {
            this.mUiccCards[index].onRefresh(refreshResponse);
        }
        this.mCis[index].getIccCardStatus(obtainMessage(2, Integer.valueOf(index)));
    }

    public static IccRefreshResponse parseOemSimRefresh(ByteBuffer payload) {
        IccRefreshResponse response = new IccRefreshResponse();
        payload.order(ByteOrder.nativeOrder());
        response.refreshResult = payload.getInt();
        response.efId = payload.getInt();
        int aidLen = payload.getInt();
        byte[] aid = new byte[44];
        payload.get(aid, 0, 44);
        response.aid = aidLen == 0 ? null : new String(aid).substring(0, aidLen);
        Rlog.d(LOG_TAG, "refresh SIM card , refresh result:" + response.refreshResult + ", ef Id:" + response.efId + ", aid:" + response.aid);
        return response;
    }

    public UiccCardApplication getUiccCardApplication(int phoneId, int family) {
        UiccCardApplication application;
        synchronized (mLock) {
            application = (!isValidCardIndex(phoneId) || this.mUiccCards[phoneId] == null) ? null : this.mUiccCards[phoneId].getApplication(family);
        }
        return application;
    }

    private synchronized void onGetIccCardStatusDone(AsyncResult ar, Integer index) {
        if (ar.exception != null) {
            Rlog.e(LOG_TAG, "Error getting ICC status. RIL_REQUEST_GET_ICC_STATUS should never return an error", ar.exception);
        } else if (!isValidCardIndex(index.intValue())) {
            Rlog.e(LOG_TAG, "onGetIccCardStatusDone: invalid index : " + index);
        } else {
            IccCardStatus status = (IccCardStatus) ar.result;
            if (this.mUiccCards[index.intValue()] != null || status.mCardState != IccCardStatus.CardState.CARDSTATE_PRESENT || status.mGsmUmtsSubscriptionAppIndex >= 0 || status.mCdmaSubscriptionAppIndex >= 0 || status.mImsSubscriptionAppIndex >= 0) {
                if (!this.mFatalSimMsgFlag && !this.mResetFlag && SystemProperties.getInt(PROPERTY_UIM_DET, -1) == 1 && status.mCardState == IccCardStatus.CardState.CARDSTATE_PRESENT) {
                    if (TelBrand.IS_DCM) {
                        new PSimServiceConnection(null) { // from class: com.android.internal.telephony.uicc.UiccController.3
                            @Override // com.android.internal.telephony.uicc.UiccController.PSimServiceConnection
                            protected void onFailedToConnectService() {
                                UiccController.this.log("cannot connect to PortableSimAppService");
                                UiccController.this.dispFatalSimMsg();
                            }

                            @Override // com.android.internal.telephony.uicc.UiccController.PSimServiceConnection
                            protected void onSucceededToGetState(int connState) {
                                if ((connState & UiccController.PSIM_SLAVE) == 0 || (connState & 255) == 0) {
                                    UiccController.this.dispFatalSimMsg();
                                } else {
                                    UiccController.this.log("psim is enabled. avoid showing uim unstable message.");
                                }
                            }

                            @Override // com.android.internal.telephony.uicc.UiccController.PSimServiceConnection
                            protected void onFailedToGetState() {
                                UiccController.this.dispFatalSimMsg();
                            }
                        }.getPSimState();
                    } else {
                        dispFatalSimMsg();
                    }
                }
                if (this.mUiccCards[index.intValue()] == null) {
                    this.mUiccCards[index.intValue()] = new UiccCard(this.mContext, this.mCis[index.intValue()], status, index.intValue());
                } else {
                    this.mUiccCards[index.intValue()].update(this.mContext, this.mCis[index.intValue()], status);
                }
                log("Notifying IccChangedRegistrants");
                this.mIccChangedRegistrants.notifyRegistrants(new AsyncResult((Object) null, index, (Throwable) null));
            } else {
                sendMessageDelayed(obtainMessage(11, index), 15000L);
                Rlog.d(LOG_TAG, "sendMessageDelayed 15000 msc");
            }
        }
    }

    private boolean isValidCardIndex(int index) {
        return index >= 0 && index < this.mUiccCards.length;
    }

    public void log(String string) {
        Rlog.d(LOG_TAG, string);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("UiccController: " + this);
        pw.println(" mContext=" + this.mContext);
        pw.println(" mInstance=" + mInstance);
        pw.println(" mIccChangedRegistrants: size=" + this.mIccChangedRegistrants.size());
        for (int i = 0; i < this.mIccChangedRegistrants.size(); i++) {
            pw.println("  mIccChangedRegistrants[" + i + "]=" + ((Registrant) this.mIccChangedRegistrants.get(i)).getHandler());
        }
        pw.println();
        pw.flush();
        pw.println(" mUiccCards: size=" + this.mUiccCards.length);
        for (int i2 = 0; i2 < this.mUiccCards.length; i2++) {
            if (this.mUiccCards[i2] == null) {
                pw.println("  mUiccCards[" + i2 + "]=null");
            } else {
                pw.println("  mUiccCards[" + i2 + "]=" + this.mUiccCards[i2]);
                this.mUiccCards[i2].dump(fd, pw, args);
            }
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
            this.mResetFlag = true;
            Resources r = Resources.getSystem();
            String title = isAdded ? r.getString(17040694) : r.getString(17040691);
            String message = isAdded ? r.getString(17040695) : r.getString(17040692);
            r.getString(17040696);
            AlertDialog dialog = new AlertDialog.Builder(this.mContext).setTitle(title).setMessage(message).create();
            dialog.getWindow().setType(2009);
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
            dialog.getWindow().addFlags(2621440);
            sendMessageDelayed(obtainMessage(7), 5000L);
        }
    }

    public void reboot() {
        log("Reboot due to SIM swap");
        ((PowerManager) this.mContext.getSystemService("power")).reboot("SIM is hotswap.");
    }

    private void resetAtNewThread() {
        new Thread() { // from class: com.android.internal.telephony.uicc.UiccController.4
            @Override // java.lang.Thread, java.lang.Runnable
            public void run() {
                UiccController.this.reboot();
            }
        }.start();
    }

    public void gotoReset(boolean isAdded) {
        if (Build.IS_DEBUGGABLE && Build.ID.startsWith("F")) {
            log("Manufacturing build. Not reboot.");
        } else if (this.mPhone != null) {
            Phone imsphone = this.mPhone.getImsPhone();
            if (this.mPhone.getState() == PhoneConstants.State.OFFHOOK) {
                log("Wait for EVENT_DISCONNECT ");
                this.mPhone.registerForDisconnect(this, 8, null);
            } else if (imsphone == null || imsphone.getState() != PhoneConstants.State.OFFHOOK) {
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

    public void dispFatalSimMsg() {
        this.mFatalSimMsgFlag = true;
        AlertDialog dialog = new AlertDialog.Builder(this.mContext).setMessage(Resources.getSystem().getString(17041205)).setPositiveButton(17039370, (DialogInterface.OnClickListener) null).create();
        dialog.getWindow().setType(2009);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        dialog.getWindow().addFlags(2621440);
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public abstract class PSimServiceConnection implements ServiceConnection {
        protected Object mParam;

        abstract void onFailedToConnectService();

        abstract void onFailedToGetState();

        abstract void onSucceededToGetState(int i);

        PSimServiceConnection(Object param) {
            UiccController.this = r2;
            this.mParam = null;
            this.mParam = param;
        }

        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                onSucceededToGetState(IPortableSimAppService.Stub.asInterface(service).getConnetionState());
            } catch (RemoteException e) {
                onFailedToGetState();
            }
        }

        @Override // android.content.ServiceConnection
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
}
