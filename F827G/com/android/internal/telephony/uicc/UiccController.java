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
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.nttdocomo.android.portablesim.service.IPortableSimAppService.Stub;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
    private boolean mFatalSimMsgFlag = false;
    protected RegistrantList mIccChangedRegistrants = new RegistrantList();
    private boolean mOEMHookSimRefresh = false;
    private Phone mPhone = null;
    private boolean mResetFlag = false;
    private UiccCard[] mUiccCards = new UiccCard[TelephonyManager.getDefault().getPhoneCount()];

    private abstract class PSimServiceConnection implements ServiceConnection {
        protected Object mParam = null;

        PSimServiceConnection(Object obj) {
            this.mParam = obj;
        }

        public void getPSimState() {
            Intent intent = new Intent();
            intent.setClassName("com.nttdocomo.android.portablesim", "com.nttdocomo.android.portablesim.service.PortableSimAppService");
            if (!UiccController.this.mContext.bindService(intent, this, 1)) {
                onFailedToConnectService();
            }
        }

        public abstract void onFailedToConnectService();

        public abstract void onFailedToGetState();

        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            try {
                onSucceededToGetState(Stub.asInterface(iBinder).getConnetionState());
            } catch (RemoteException e) {
                onFailedToGetState();
            }
        }

        public void onServiceDisconnected(ComponentName componentName) {
        }

        public abstract void onSucceededToGetState(int i);
    }

    private UiccController(Context context, CommandsInterface[] commandsInterfaceArr) {
        int i = 0;
        log("Creating UiccController");
        this.mContext = context;
        this.mCis = commandsInterfaceArr;
        this.mOEMHookSimRefresh = this.mContext.getResources().getBoolean(17957016);
        while (i < this.mCis.length) {
            Integer num = new Integer(i);
            this.mCis[i].registerForIccStatusChanged(this, 1, num);
            this.mCis[i].registerForAvailable(this, 1, num);
            this.mCis[i].registerForNotAvailable(this, 3, num);
            if (this.mOEMHookSimRefresh) {
                this.mCis[i].registerForSimRefreshEvent(this, 5, num);
            } else {
                this.mCis[i].registerForIccRefresh(this, 4, num);
            }
            i++;
        }
    }

    private void dispFatalSimMsg() {
        this.mFatalSimMsgFlag = true;
        AlertDialog create = new Builder(this.mContext).setMessage(Resources.getSystem().getString(17041205)).setPositiveButton(17039370, null).create();
        create.getWindow().setType(2009);
        create.setCanceledOnTouchOutside(false);
        create.show();
        create.getWindow().addFlags(2621440);
    }

    private Integer getCiIndex(Message message) {
        Integer num = new Integer(0);
        if (message != null) {
            if (message.obj != null && (message.obj instanceof Integer)) {
                return (Integer) message.obj;
            }
            if (message.obj != null && (message.obj instanceof AsyncResult)) {
                AsyncResult asyncResult = (AsyncResult) message.obj;
                if (asyncResult.userObj != null && (asyncResult.userObj instanceof Integer)) {
                    return (Integer) asyncResult.userObj;
                }
            }
        }
        return num;
    }

    public static int getFamilyFromRadioTechnology(int i) {
        return (ServiceState.isGsm(i) || i == 13) ? 1 : ServiceState.isCdma(i) ? 2 : -1;
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

    private void gotoReset(boolean z) {
        if (Build.IS_DEBUGGABLE && Build.ID.startsWith("F")) {
            log("Manufacturing build. Not reboot.");
        } else if (this.mPhone != null) {
            Phone imsPhone = this.mPhone.getImsPhone();
            if (this.mPhone.getState() == State.OFFHOOK) {
                log("Wait for EVENT_DISCONNECT ");
                this.mPhone.registerForDisconnect(this, 8, null);
            } else if (imsPhone == null || imsPhone.getState() != State.OFFHOOK) {
                log("PhoneConstants.State OFFHOOK onIccSwap()");
                onIccSwap(z);
            } else {
                log("Wait for EVENT_DISCONNECT ");
                imsPhone.registerForDisconnect(this, 8, null);
            }
        } else {
            Rlog.e(LOG_TAG, "mPhone NULL calling onIccSwap ");
            onIccSwap(z);
        }
    }

    private void handleRefresh(IccRefreshResponse iccRefreshResponse, int i) {
        if (iccRefreshResponse == null) {
            log("handleRefresh received without input");
            return;
        }
        if (this.mUiccCards[i] != null) {
            this.mUiccCards[i].onRefresh(iccRefreshResponse);
        }
        this.mCis[i].getIccCardStatus(obtainMessage(2, Integer.valueOf(i)));
    }

    private boolean isValidCardIndex(int i) {
        return i >= 0 && i < this.mUiccCards.length;
    }

    private void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    public static UiccController make(Context context, CommandsInterface[] commandsInterfaceArr) {
        UiccController uiccController;
        synchronized (mLock) {
            if (mInstance != null) {
                throw new RuntimeException("MSimUiccController.make() should only be called once");
            }
            mInstance = new UiccController(context, commandsInterfaceArr);
            uiccController = mInstance;
        }
        return uiccController;
    }

    private void onGetIccCardStatusDone(AsyncResult asyncResult, Integer num) {
        synchronized (this) {
            if (asyncResult.exception != null) {
                Rlog.e(LOG_TAG, "Error getting ICC status. RIL_REQUEST_GET_ICC_STATUS should never return an error", asyncResult.exception);
            } else if (isValidCardIndex(num.intValue())) {
                IccCardStatus iccCardStatus = (IccCardStatus) asyncResult.result;
                if (this.mUiccCards[num.intValue()] != null || iccCardStatus.mCardState != CardState.CARDSTATE_PRESENT || iccCardStatus.mGsmUmtsSubscriptionAppIndex >= 0 || iccCardStatus.mCdmaSubscriptionAppIndex >= 0 || iccCardStatus.mImsSubscriptionAppIndex >= 0) {
                    if (!this.mFatalSimMsgFlag && !this.mResetFlag && SystemProperties.getInt(PROPERTY_UIM_DET, -1) == 1 && iccCardStatus.mCardState == CardState.CARDSTATE_PRESENT) {
                        if (TelBrand.IS_DCM) {
                            new PSimServiceConnection(null) {
                                /* Access modifiers changed, original: protected */
                                public void onFailedToConnectService() {
                                    UiccController.this.log("cannot connect to PortableSimAppService");
                                    UiccController.this.dispFatalSimMsg();
                                }

                                /* Access modifiers changed, original: protected */
                                public void onFailedToGetState() {
                                    UiccController.this.dispFatalSimMsg();
                                }

                                /* Access modifiers changed, original: protected */
                                public void onSucceededToGetState(int i) {
                                    if ((i & UiccController.PSIM_SLAVE) == 0 || (i & 255) == 0) {
                                        UiccController.this.dispFatalSimMsg();
                                    } else {
                                        UiccController.this.log("psim is enabled. avoid showing uim unstable message.");
                                    }
                                }
                            }.getPSimState();
                        } else {
                            dispFatalSimMsg();
                        }
                    }
                    if (this.mUiccCards[num.intValue()] == null) {
                        this.mUiccCards[num.intValue()] = new UiccCard(this.mContext, this.mCis[num.intValue()], iccCardStatus, num.intValue());
                    } else {
                        this.mUiccCards[num.intValue()].update(this.mContext, this.mCis[num.intValue()], iccCardStatus);
                    }
                    log("Notifying IccChangedRegistrants");
                    this.mIccChangedRegistrants.notifyRegistrants(new AsyncResult(null, num, null));
                } else {
                    sendMessageDelayed(obtainMessage(11, num), 15000);
                    Rlog.d(LOG_TAG, "sendMessageDelayed 15000 msc");
                }
            } else {
                Rlog.e(LOG_TAG, "onGetIccCardStatusDone: invalid index : " + num);
            }
        }
    }

    private void onIccSwap(boolean z) {
        String str = SystemProperties.get("sys.shutdown.requested", "");
        if (str != null && str.length() > 0) {
            log("shutdown was started");
        } else if (this.mResetFlag) {
            log("onIccSwap is executed");
        } else {
            CharSequence string;
            this.mResetFlag = true;
            Resources system = Resources.getSystem();
            if (z) {
                string = system.getString(17040694);
            } else {
                Object string2 = system.getString(17040691);
            }
            CharSequence string3 = z ? system.getString(17040695) : system.getString(17040692);
            system.getString(17040696);
            AlertDialog create = new Builder(this.mContext).setTitle(string2).setMessage(string3).create();
            create.getWindow().setType(2009);
            create.setCanceledOnTouchOutside(false);
            create.show();
            create.getWindow().addFlags(2621440);
            sendMessageDelayed(obtainMessage(7), 5000);
        }
    }

    public static IccRefreshResponse parseOemSimRefresh(ByteBuffer byteBuffer) {
        IccRefreshResponse iccRefreshResponse = new IccRefreshResponse();
        byteBuffer.order(ByteOrder.nativeOrder());
        iccRefreshResponse.refreshResult = byteBuffer.getInt();
        iccRefreshResponse.efId = byteBuffer.getInt();
        int i = byteBuffer.getInt();
        byte[] bArr = new byte[44];
        byteBuffer.get(bArr, 0, 44);
        iccRefreshResponse.aid = i == 0 ? null : new String(bArr).substring(0, i);
        Rlog.d(LOG_TAG, "refresh SIM card , refresh result:" + iccRefreshResponse.refreshResult + ", ef Id:" + iccRefreshResponse.efId + ", aid:" + iccRefreshResponse.aid);
        return iccRefreshResponse;
    }

    private void reboot() {
        log("Reboot due to SIM swap");
        ((PowerManager) this.mContext.getSystemService("power")).reboot("SIM is hotswap.");
    }

    private void resetAtNewThread() {
        new Thread() {
            public void run() {
                UiccController.this.reboot();
            }
        }.start();
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        int i = 0;
        printWriter.println("UiccController: " + this);
        printWriter.println(" mContext=" + this.mContext);
        printWriter.println(" mInstance=" + mInstance);
        printWriter.println(" mIccChangedRegistrants: size=" + this.mIccChangedRegistrants.size());
        for (int i2 = 0; i2 < this.mIccChangedRegistrants.size(); i2++) {
            printWriter.println("  mIccChangedRegistrants[" + i2 + "]=" + ((Registrant) this.mIccChangedRegistrants.get(i2)).getHandler());
        }
        printWriter.println();
        printWriter.flush();
        printWriter.println(" mUiccCards: size=" + this.mUiccCards.length);
        while (i < this.mUiccCards.length) {
            if (this.mUiccCards[i] == null) {
                printWriter.println("  mUiccCards[" + i + "]=null");
            } else {
                printWriter.println("  mUiccCards[" + i + "]=" + this.mUiccCards[i]);
                this.mUiccCards[i].dump(fileDescriptor, printWriter, strArr);
            }
            i++;
        }
    }

    public IccFileHandler getIccFileHandler(int i, int i2) {
        synchronized (mLock) {
            UiccCardApplication uiccCardApplication = getUiccCardApplication(i, i2);
            if (uiccCardApplication != null) {
                IccFileHandler iccFileHandler = uiccCardApplication.getIccFileHandler();
                return iccFileHandler;
            }
            return null;
        }
    }

    public IccRecords getIccRecords(int i, int i2) {
        synchronized (mLock) {
            UiccCardApplication uiccCardApplication = getUiccCardApplication(i, i2);
            if (uiccCardApplication != null) {
                IccRecords iccRecords = uiccCardApplication.getIccRecords();
                return iccRecords;
            }
            return null;
        }
    }

    public Phone getPhone() {
        Phone phone;
        synchronized (mLock) {
            phone = this.mPhone;
        }
        return phone;
    }

    public UiccCard getUiccCard() {
        return getUiccCard(0);
    }

    public UiccCard getUiccCard(int i) {
        synchronized (mLock) {
            if (isValidCardIndex(i)) {
                UiccCard uiccCard = this.mUiccCards[i];
                return uiccCard;
            }
            return null;
        }
    }

    public UiccCardApplication getUiccCardApplication(int i) {
        return getUiccCardApplication(SubscriptionController.getInstance().getPhoneId(SubscriptionController.getInstance().getDefaultSubId()), i);
    }

    /* JADX WARNING: Missing block: B:15:?, code skipped:
            return null;
     */
    public com.android.internal.telephony.uicc.UiccCardApplication getUiccCardApplication(int r3, int r4) {
        /*
        r2 = this;
        r1 = mLock;
        monitor-enter(r1);
        r0 = r2.isValidCardIndex(r3);	 Catch:{ all -> 0x001c }
        if (r0 == 0) goto L_0x0019;
    L_0x0009:
        r0 = r2.mUiccCards;	 Catch:{ all -> 0x001c }
        r0 = r0[r3];	 Catch:{ all -> 0x001c }
        if (r0 == 0) goto L_0x0019;
    L_0x000f:
        r0 = r2.mUiccCards;	 Catch:{ all -> 0x001c }
        r0 = r0[r3];	 Catch:{ all -> 0x001c }
        r0 = r0.getApplication(r4);	 Catch:{ all -> 0x001c }
        monitor-exit(r1);	 Catch:{ all -> 0x001c }
    L_0x0018:
        return r0;
    L_0x0019:
        monitor-exit(r1);	 Catch:{ all -> 0x001c }
        r0 = 0;
        goto L_0x0018;
    L_0x001c:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x001c }
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.UiccController.getUiccCardApplication(int, int):com.android.internal.telephony.uicc.UiccCardApplication");
    }

    public UiccCard[] getUiccCards() {
        UiccCard[] uiccCardArr;
        synchronized (mLock) {
            uiccCardArr = (UiccCard[]) this.mUiccCards.clone();
        }
        return uiccCardArr;
    }

    /* JADX WARNING: Missing block: B:52:?, code skipped:
            return;
     */
    public void handleMessage(android.os.Message r7) {
        /*
        r6 = this;
        r1 = mLock;
        monitor-enter(r1);
        r2 = r6.getCiIndex(r7);	 Catch:{ all -> 0x005d }
        r0 = r2.intValue();	 Catch:{ all -> 0x005d }
        if (r0 < 0) goto L_0x0016;
    L_0x000d:
        r0 = r2.intValue();	 Catch:{ all -> 0x005d }
        r3 = r6.mCis;	 Catch:{ all -> 0x005d }
        r3 = r3.length;	 Catch:{ all -> 0x005d }
        if (r0 < r3) goto L_0x003c;
    L_0x0016:
        r0 = "UiccController";
        r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x005d }
        r3.<init>();	 Catch:{ all -> 0x005d }
        r4 = "Invalid index : ";
        r3 = r3.append(r4);	 Catch:{ all -> 0x005d }
        r2 = r3.append(r2);	 Catch:{ all -> 0x005d }
        r3 = " received with event ";
        r2 = r2.append(r3);	 Catch:{ all -> 0x005d }
        r3 = r7.what;	 Catch:{ all -> 0x005d }
        r2 = r2.append(r3);	 Catch:{ all -> 0x005d }
        r2 = r2.toString();	 Catch:{ all -> 0x005d }
        android.telephony.Rlog.e(r0, r2);	 Catch:{ all -> 0x005d }
        monitor-exit(r1);	 Catch:{ all -> 0x005d }
    L_0x003b:
        return;
    L_0x003c:
        r0 = r7.what;	 Catch:{ all -> 0x005d }
        switch(r0) {
            case 1: goto L_0x0060;
            case 2: goto L_0x00f2;
            case 3: goto L_0x0134;
            case 4: goto L_0x0165;
            case 5: goto L_0x0199;
            case 6: goto L_0x0041;
            case 7: goto L_0x01e2;
            case 8: goto L_0x01d7;
            case 9: goto L_0x0041;
            case 10: goto L_0x0041;
            case 11: goto L_0x0100;
            default: goto L_0x0041;
        };	 Catch:{ all -> 0x005d }
    L_0x0041:
        r0 = "UiccController";
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x005d }
        r2.<init>();	 Catch:{ all -> 0x005d }
        r3 = " Unknown Event ";
        r2 = r2.append(r3);	 Catch:{ all -> 0x005d }
        r3 = r7.what;	 Catch:{ all -> 0x005d }
        r2 = r2.append(r3);	 Catch:{ all -> 0x005d }
        r2 = r2.toString();	 Catch:{ all -> 0x005d }
        android.telephony.Rlog.e(r0, r2);	 Catch:{ all -> 0x005d }
    L_0x005b:
        monitor-exit(r1);	 Catch:{ all -> 0x005d }
        goto L_0x003b;
    L_0x005d:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x005d }
        throw r0;
    L_0x0060:
        r0 = "Received EVENT_ICC_STATUS_CHANGED, calling getIccCardStatus";
        r6.log(r0);	 Catch:{ all -> 0x005d }
        r0 = r6.mCis;	 Catch:{ all -> 0x005d }
        r3 = r2.intValue();	 Catch:{ all -> 0x005d }
        r0 = r0[r3];	 Catch:{ all -> 0x005d }
        r3 = 2;
        r2 = r6.obtainMessage(r3, r2);	 Catch:{ all -> 0x005d }
        r0.getIccCardStatus(r2);	 Catch:{ all -> 0x005d }
        r0 = "ril.uim.hotswap";
        r0 = android.os.SystemProperties.get(r0);	 Catch:{ all -> 0x005d }
        r2 = "ADDED";
        r2 = r2.equals(r0);	 Catch:{ all -> 0x005d }
        if (r2 == 0) goto L_0x00b1;
    L_0x0083:
        r2 = com.android.internal.telephony.TelBrand.IS_DCM;	 Catch:{ all -> 0x005d }
        if (r2 == 0) goto L_0x0090;
    L_0x0087:
        r2 = new com.android.internal.telephony.uicc.UiccController$1;	 Catch:{ all -> 0x005d }
        r2.<init>(r0);	 Catch:{ all -> 0x005d }
        r2.getPSimState();	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x0090:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x005d }
        r2.<init>();	 Catch:{ all -> 0x005d }
        r3 = "SystemProperties : ";
        r2 = r2.append(r3);	 Catch:{ all -> 0x005d }
        r0 = r2.append(r0);	 Catch:{ all -> 0x005d }
        r2 = " calling gotoReset(ture)";
        r0 = r0.append(r2);	 Catch:{ all -> 0x005d }
        r0 = r0.toString();	 Catch:{ all -> 0x005d }
        r6.log(r0);	 Catch:{ all -> 0x005d }
        r0 = 1;
        r6.gotoReset(r0);	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x00b1:
        r2 = "REMOVED";
        r2 = r2.equals(r0);	 Catch:{ all -> 0x005d }
        if (r2 == 0) goto L_0x00e8;
    L_0x00b9:
        r2 = com.android.internal.telephony.TelBrand.IS_DCM;	 Catch:{ all -> 0x005d }
        if (r2 == 0) goto L_0x00c6;
    L_0x00bd:
        r2 = new com.android.internal.telephony.uicc.UiccController$2;	 Catch:{ all -> 0x005d }
        r2.<init>(r0);	 Catch:{ all -> 0x005d }
        r2.getPSimState();	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x00c6:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x005d }
        r2.<init>();	 Catch:{ all -> 0x005d }
        r3 = "SystemProperties : ";
        r2 = r2.append(r3);	 Catch:{ all -> 0x005d }
        r0 = r2.append(r0);	 Catch:{ all -> 0x005d }
        r2 = " calling gotoReset(false)";
        r0 = r0.append(r2);	 Catch:{ all -> 0x005d }
        r0 = r0.toString();	 Catch:{ all -> 0x005d }
        r6.log(r0);	 Catch:{ all -> 0x005d }
        r0 = 0;
        r6.gotoReset(r0);	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x00e8:
        r2 = "NONE";
        r0 = r2.equals(r0);	 Catch:{ all -> 0x005d }
        if (r0 == 0) goto L_0x005b;
    L_0x00f0:
        goto L_0x005b;
    L_0x00f2:
        r0 = "Received EVENT_GET_ICC_STATUS_DONE";
        r6.log(r0);	 Catch:{ all -> 0x005d }
        r0 = r7.obj;	 Catch:{ all -> 0x005d }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ all -> 0x005d }
        r6.onGetIccCardStatusDone(r0, r2);	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x0100:
        r0 = r6.mUiccCards;	 Catch:{ all -> 0x005d }
        r3 = r2.intValue();	 Catch:{ all -> 0x005d }
        r0 = r0[r3];	 Catch:{ all -> 0x005d }
        if (r0 != 0) goto L_0x005b;
    L_0x010a:
        r0 = "UiccController";
        r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x005d }
        r3.<init>();	 Catch:{ all -> 0x005d }
        r4 = "Retry getIccCardStatus mUiccCards[index]=";
        r3 = r3.append(r4);	 Catch:{ all -> 0x005d }
        r4 = r6.mUiccCards;	 Catch:{ all -> 0x005d }
        r5 = r2.intValue();	 Catch:{ all -> 0x005d }
        r4 = r4[r5];	 Catch:{ all -> 0x005d }
        r3 = r3.append(r4);	 Catch:{ all -> 0x005d }
        r3 = r3.toString();	 Catch:{ all -> 0x005d }
        android.telephony.Rlog.d(r0, r3);	 Catch:{ all -> 0x005d }
        r0 = 1;
        r0 = r6.obtainMessage(r0, r2);	 Catch:{ all -> 0x005d }
        r6.sendMessage(r0);	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x0134:
        r0 = "EVENT_RADIO_UNAVAILABLE, dispose card";
        r6.log(r0);	 Catch:{ all -> 0x005d }
        r0 = r6.mUiccCards;	 Catch:{ all -> 0x005d }
        r3 = r2.intValue();	 Catch:{ all -> 0x005d }
        r0 = r0[r3];	 Catch:{ all -> 0x005d }
        if (r0 == 0) goto L_0x014e;
    L_0x0143:
        r0 = r6.mUiccCards;	 Catch:{ all -> 0x005d }
        r3 = r2.intValue();	 Catch:{ all -> 0x005d }
        r0 = r0[r3];	 Catch:{ all -> 0x005d }
        r0.dispose();	 Catch:{ all -> 0x005d }
    L_0x014e:
        r0 = r6.mUiccCards;	 Catch:{ all -> 0x005d }
        r3 = r2.intValue();	 Catch:{ all -> 0x005d }
        r4 = 0;
        r0[r3] = r4;	 Catch:{ all -> 0x005d }
        r0 = r6.mIccChangedRegistrants;	 Catch:{ all -> 0x005d }
        r3 = new android.os.AsyncResult;	 Catch:{ all -> 0x005d }
        r4 = 0;
        r5 = 0;
        r3.<init>(r4, r2, r5);	 Catch:{ all -> 0x005d }
        r0.notifyRegistrants(r3);	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x0165:
        r0 = r7.obj;	 Catch:{ all -> 0x005d }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ all -> 0x005d }
        r3 = "Sim REFRESH received";
        r6.log(r3);	 Catch:{ all -> 0x005d }
        r3 = r0.exception;	 Catch:{ all -> 0x005d }
        if (r3 != 0) goto L_0x017f;
    L_0x0172:
        r0 = r0.result;	 Catch:{ all -> 0x005d }
        r0 = (com.android.internal.telephony.uicc.IccRefreshResponse) r0;	 Catch:{ all -> 0x005d }
        r2 = r2.intValue();	 Catch:{ all -> 0x005d }
        r6.handleRefresh(r0, r2);	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x017f:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x005d }
        r2.<init>();	 Catch:{ all -> 0x005d }
        r3 = "Exception on refresh ";
        r2 = r2.append(r3);	 Catch:{ all -> 0x005d }
        r0 = r0.exception;	 Catch:{ all -> 0x005d }
        r0 = r2.append(r0);	 Catch:{ all -> 0x005d }
        r0 = r0.toString();	 Catch:{ all -> 0x005d }
        r6.log(r0);	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x0199:
        r0 = r7.obj;	 Catch:{ all -> 0x005d }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ all -> 0x005d }
        r3 = "Sim REFRESH OEM received";
        r6.log(r3);	 Catch:{ all -> 0x005d }
        r3 = r0.exception;	 Catch:{ all -> 0x005d }
        if (r3 != 0) goto L_0x01bd;
    L_0x01a6:
        r0 = r0.result;	 Catch:{ all -> 0x005d }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x005d }
        r0 = (byte[]) r0;	 Catch:{ all -> 0x005d }
        r0 = java.nio.ByteBuffer.wrap(r0);	 Catch:{ all -> 0x005d }
        r0 = parseOemSimRefresh(r0);	 Catch:{ all -> 0x005d }
        r2 = r2.intValue();	 Catch:{ all -> 0x005d }
        r6.handleRefresh(r0, r2);	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x01bd:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x005d }
        r2.<init>();	 Catch:{ all -> 0x005d }
        r3 = "Exception on refresh ";
        r2 = r2.append(r3);	 Catch:{ all -> 0x005d }
        r0 = r0.exception;	 Catch:{ all -> 0x005d }
        r0 = r2.append(r0);	 Catch:{ all -> 0x005d }
        r0 = r0.toString();	 Catch:{ all -> 0x005d }
        r6.log(r0);	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x01d7:
        r0 = "Received EVENT_DISCONNECT calling gotoReset(false) ";
        r6.log(r0);	 Catch:{ all -> 0x005d }
        r0 = 0;
        r6.gotoReset(r0);	 Catch:{ all -> 0x005d }
        goto L_0x005b;
    L_0x01e2:
        r6.resetAtNewThread();	 Catch:{ all -> 0x005d }
        goto L_0x005b;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.UiccController.handleMessage(android.os.Message):void");
    }

    public void registerForIccChanged(Handler handler, int i, Object obj) {
        synchronized (mLock) {
            Registrant registrant = new Registrant(handler, i, obj);
            this.mIccChangedRegistrants.add(registrant);
            registrant.notifyRegistrant();
        }
    }

    public void setPhone(Phone phone) {
        synchronized (mLock) {
            this.mPhone = phone;
        }
    }

    public void unregisterForIccChanged(Handler handler) {
        synchronized (mLock) {
            this.mIccChangedRegistrants.remove(handler);
        }
    }
}
