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

public class ProxyController {
    static final String LOG_TAG = "ProxyController";
    private static ProxyController sProxyController;
    private CommandsInterface[] mCi;
    private Context mContext;
    private DctController mDctController;
    private PhoneSubInfoController mPhoneSubInfoController = new PhoneSubInfoController(this.mProxyPhones);
    private PhoneProxy[] mProxyPhones;
    private UiccController mUiccController;
    private UiccPhoneBookController mUiccPhoneBookController = new UiccPhoneBookController(this.mProxyPhones);
    private UiccSmsController mUiccSmsController = new UiccSmsController(this.mProxyPhones);

    private ProxyController(Context context, PhoneProxy[] phoneProxyArr, UiccController uiccController, CommandsInterface[] commandsInterfaceArr) {
        logd("Constructor - Enter");
        this.mContext = context;
        this.mProxyPhones = phoneProxyArr;
        this.mUiccController = uiccController;
        this.mCi = commandsInterfaceArr;
        HandlerThread handlerThread = new HandlerThread("DctControllerThread");
        handlerThread.start();
        this.mDctController = DctController.makeDctController(phoneProxyArr, handlerThread.getLooper());
        logd("Constructor - Exit");
    }

    public static ProxyController getInstance() {
        return sProxyController;
    }

    public static ProxyController getInstance(Context context, PhoneProxy[] phoneProxyArr, UiccController uiccController, CommandsInterface[] commandsInterfaceArr) {
        if (sProxyController == null) {
            sProxyController = new ProxyController(context, phoneProxyArr, uiccController, commandsInterfaceArr);
        }
        return sProxyController;
    }

    private void logd(String str) {
        Rlog.d(LOG_TAG, str);
    }

    public void disableDataConnectivity(int i, Message message) {
        this.mProxyPhones[i].setInternalDataEnabled(false, message);
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        try {
            this.mDctController.dump(fileDescriptor, printWriter, strArr);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void enableDataConnectivity(int i) {
        this.mProxyPhones[i].setInternalDataEnabled(true);
    }

    public boolean isDataDisconnected(int i) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(i);
        return (phoneId < 0 || phoneId >= TelephonyManager.getDefault().getPhoneCount()) ? false : ((PhoneBase) this.mProxyPhones[phoneId].getActivePhone()).mDcTracker.isDisconnected();
    }

    public void registerForAllDataDisconnected(int i, Handler handler, int i2, Object obj) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(i);
        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            this.mProxyPhones[phoneId].registerForAllDataDisconnected(handler, i2, obj);
        }
    }

    public void unregisterForAllDataDisconnected(int i, Handler handler) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(i);
        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            this.mProxyPhones[phoneId].unregisterForAllDataDisconnected(handler);
        }
    }

    public void updateCurrentCarrierInProvider(int i) {
        this.mProxyPhones[i].updateCurrentCarrierInProvider();
    }

    public void updateDataConnectionTracker(int i) {
        this.mProxyPhones[i].updateDataConnectionTracker();
    }
}
