package com.android.internal.telephony;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.dataconnection.DctController;
import com.android.internal.telephony.uicc.UiccController;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class ProxyController {
    static final String LOG_TAG = "ProxyController";
    private static ProxyController sProxyController;
    private CommandsInterface[] mCi;
    private Context mContext;
    private DctController mDctController;
    private PhoneSubInfoController mPhoneSubInfoController;
    private PhoneProxy[] mProxyPhones;
    private UiccController mUiccController;
    private UiccPhoneBookController mUiccPhoneBookController;
    private UiccSmsController mUiccSmsController;

    public static ProxyController getInstance(Context context, PhoneProxy[] phoneProxy, UiccController uiccController, CommandsInterface[] ci) {
        if (sProxyController == null) {
            sProxyController = new ProxyController(context, phoneProxy, uiccController, ci);
        }
        return sProxyController;
    }

    public static ProxyController getInstance() {
        return sProxyController;
    }

    private ProxyController(Context context, PhoneProxy[] phoneProxy, UiccController uiccController, CommandsInterface[] ci) {
        logd("Constructor - Enter");
        this.mContext = context;
        this.mProxyPhones = phoneProxy;
        this.mUiccController = uiccController;
        this.mCi = ci;
        HandlerThread t = new HandlerThread("DctControllerThread");
        t.start();
        this.mDctController = DctController.makeDctController(phoneProxy, t.getLooper());
        this.mUiccPhoneBookController = new UiccPhoneBookController(this.mProxyPhones);
        this.mPhoneSubInfoController = new PhoneSubInfoController(this.mProxyPhones);
        this.mUiccSmsController = new UiccSmsController(this.mProxyPhones);
        logd("Constructor - Exit");
    }

    public void updateDataConnectionTracker(int sub) {
        this.mProxyPhones[sub].updateDataConnectionTracker();
    }

    public void enableDataConnectivity(int sub) {
        this.mProxyPhones[sub].setInternalDataEnabled(true);
    }

    public void disableDataConnectivity(int sub, Message dataCleanedUpMsg) {
        this.mProxyPhones[sub].setInternalDataEnabled(false, dataCleanedUpMsg);
    }

    public void updateCurrentCarrierInProvider(int sub) {
        this.mProxyPhones[sub].updateCurrentCarrierInProvider();
    }

    public void registerForAllDataDisconnected(int subId, Handler h, int what, Object obj) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            this.mProxyPhones[phoneId].registerForAllDataDisconnected(h, what, obj);
        }
    }

    public void unregisterForAllDataDisconnected(int subId, Handler h) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            this.mProxyPhones[phoneId].unregisterForAllDataDisconnected(h);
        }
    }

    public boolean isDataDisconnected(int subId) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        if (phoneId < 0 || phoneId >= TelephonyManager.getDefault().getPhoneCount()) {
            return false;
        }
        return ((PhoneBase) this.mProxyPhones[phoneId].getActivePhone()).mDcTracker.isDisconnected();
    }

    private void logd(String string) {
        Rlog.d(LOG_TAG, string);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        try {
            this.mDctController.dump(fd, pw, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
