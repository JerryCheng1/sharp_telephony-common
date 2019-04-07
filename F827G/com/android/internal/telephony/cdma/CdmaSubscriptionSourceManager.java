package com.android.internal.telephony.cdma;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.provider.Settings.Global;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;
import java.util.concurrent.atomic.AtomicInteger;

public class CdmaSubscriptionSourceManager extends Handler {
    private static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 1;
    private static final int EVENT_GET_CDMA_SUBSCRIPTION_SOURCE = 2;
    private static final int EVENT_RADIO_ON = 3;
    private static final int EVENT_SUBSCRIPTION_STATUS_CHANGED = 4;
    static final String LOG_TAG = "CdmaSSM";
    public static final int PREFERRED_CDMA_SUBSCRIPTION = 1;
    private static final int SUBSCRIPTION_ACTIVATED = 1;
    public static final int SUBSCRIPTION_FROM_NV = 1;
    public static final int SUBSCRIPTION_FROM_RUIM = 0;
    public static final int SUBSCRIPTION_SOURCE_UNKNOWN = -1;
    private static CdmaSubscriptionSourceManager sInstance;
    private static int sReferenceCount = 0;
    private static final Object sReferenceCountMonitor = new Object();
    private AtomicInteger mCdmaSubscriptionSource = new AtomicInteger(1);
    private RegistrantList mCdmaSubscriptionSourceChangedRegistrants = new RegistrantList();
    private CommandsInterface mCi;
    private Context mContext;

    private CdmaSubscriptionSourceManager(Context context, CommandsInterface commandsInterface) {
        this.mContext = context;
        this.mCi = commandsInterface;
        this.mCi.registerForCdmaSubscriptionChanged(this, 1, null);
        this.mCi.registerForOn(this, 3, null);
        this.mCdmaSubscriptionSource.set(getDefault(context));
        this.mCi.registerForSubscriptionStatusChanged(this, 4, null);
    }

    public static int getDefault(Context context) {
        return Global.getInt(context.getContentResolver(), "subscription_mode", 1);
    }

    public static CdmaSubscriptionSourceManager getInstance(Context context, CommandsInterface commandsInterface, Handler handler, int i, Object obj) {
        synchronized (sReferenceCountMonitor) {
            if (sInstance == null) {
                sInstance = new CdmaSubscriptionSourceManager(context, commandsInterface);
            }
            sReferenceCount++;
        }
        sInstance.registerForCdmaSubscriptionSourceChanged(handler, i, obj);
        return sInstance;
    }

    private void handleGetCdmaSubscriptionSource(AsyncResult asyncResult) {
        if (asyncResult.exception != null || asyncResult.result == null) {
            logw("Unable to get CDMA Subscription Source, Exception: " + asyncResult.exception + ", result: " + asyncResult.result);
            return;
        }
        int i = ((int[]) asyncResult.result)[0];
        if (i != this.mCdmaSubscriptionSource.get()) {
            log("Subscription Source Changed : " + this.mCdmaSubscriptionSource + " >> " + i);
            this.mCdmaSubscriptionSource.set(i);
            this.mCdmaSubscriptionSourceChangedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
        }
    }

    private void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    private void logw(String str) {
        Rlog.w(LOG_TAG, str);
    }

    private void registerForCdmaSubscriptionSourceChanged(Handler handler, int i, Object obj) {
        this.mCdmaSubscriptionSourceChangedRegistrants.add(new Registrant(handler, i, obj));
    }

    public void dispose(Handler handler) {
        this.mCdmaSubscriptionSourceChangedRegistrants.remove(handler);
        synchronized (sReferenceCountMonitor) {
            sReferenceCount--;
            if (sReferenceCount <= 0) {
                this.mCi.unregisterForCdmaSubscriptionChanged(this);
                this.mCi.unregisterForOn(this);
                this.mCi.unregisterForSubscriptionStatusChanged(this);
                sInstance = null;
            }
        }
    }

    public int getCdmaSubscriptionSource() {
        return this.mCdmaSubscriptionSource.get();
    }

    public void handleMessage(Message message) {
        switch (message.what) {
            case 1:
            case 2:
                log("CDMA_SUBSCRIPTION_SOURCE event = " + message.what);
                handleGetCdmaSubscriptionSource((AsyncResult) message.obj);
                return;
            case 3:
                this.mCi.getCdmaSubscriptionSource(obtainMessage(2));
                return;
            case 4:
                log("EVENT_SUBSCRIPTION_STATUS_CHANGED");
                AsyncResult asyncResult = (AsyncResult) message.obj;
                if (asyncResult.exception == null) {
                    int i = ((int[]) asyncResult.result)[0];
                    log("actStatus = " + i);
                    if (i == 1) {
                        Rlog.v(LOG_TAG, "get Cdma Subscription Source");
                        this.mCi.getCdmaSubscriptionSource(obtainMessage(2));
                        return;
                    }
                    return;
                }
                logw("EVENT_SUBSCRIPTION_STATUS_CHANGED, Exception:" + asyncResult.exception);
                return;
            default:
                super.handleMessage(message);
                return;
        }
    }
}
