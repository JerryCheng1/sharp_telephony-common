package com.android.internal.telephony;

import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.Rlog;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TelephonyDevController extends Handler {
    private static final boolean DBG = true;
    private static final int EVENT_HARDWARE_CONFIG_CHANGED = 1;
    private static final String LOG_TAG = "TDC";
    private static final Object mLock = new Object();
    private static ArrayList<HardwareConfig> mModems = new ArrayList();
    private static ArrayList<HardwareConfig> mSims = new ArrayList();
    private static Message sRilHardwareConfig;
    private static TelephonyDevController sTelephonyDevController;

    private TelephonyDevController() {
        initFromResource();
        mModems.trimToSize();
        mSims.trimToSize();
    }

    public static TelephonyDevController create() {
        TelephonyDevController telephonyDevController;
        synchronized (mLock) {
            if (sTelephonyDevController != null) {
                throw new RuntimeException("TelephonyDevController already created!?!");
            }
            sTelephonyDevController = new TelephonyDevController();
            telephonyDevController = sTelephonyDevController;
        }
        return telephonyDevController;
    }

    public static TelephonyDevController getInstance() {
        TelephonyDevController telephonyDevController;
        synchronized (mLock) {
            if (sTelephonyDevController == null) {
                throw new RuntimeException("TelephonyDevController not yet created!?!");
            }
            telephonyDevController = sTelephonyDevController;
        }
        return telephonyDevController;
    }

    public static int getModemCount() {
        int size;
        synchronized (mLock) {
            size = mModems.size();
            logd("getModemCount: " + size);
        }
        return size;
    }

    private static void handleGetHardwareConfigChanged(AsyncResult asyncResult) {
        if (asyncResult.exception != null || asyncResult.result == null) {
            loge("handleGetHardwareConfigChanged - returned an error.");
            return;
        }
        List list = (List) asyncResult.result;
        int i = 0;
        while (true) {
            int i2 = i;
            if (i2 < list.size()) {
                HardwareConfig hardwareConfig = (HardwareConfig) list.get(i2);
                if (hardwareConfig != null) {
                    if (hardwareConfig.type == 0) {
                        updateOrInsert(hardwareConfig, mModems);
                    } else if (hardwareConfig.type == 1) {
                        updateOrInsert(hardwareConfig, mSims);
                    }
                }
                i = i2 + 1;
            } else {
                return;
            }
        }
    }

    private void initFromResource() {
        String[] stringArray = Resources.getSystem().getStringArray(17236024);
        if (stringArray != null) {
            for (String hardwareConfig : stringArray) {
                HardwareConfig hardwareConfig2 = new HardwareConfig(hardwareConfig);
                if (hardwareConfig2 != null) {
                    if (hardwareConfig2.type == 0) {
                        updateOrInsert(hardwareConfig2, mModems);
                    } else if (hardwareConfig2.type == 1) {
                        updateOrInsert(hardwareConfig2, mSims);
                    }
                }
            }
        }
    }

    private static void logd(String str) {
        Rlog.d(LOG_TAG, str);
    }

    private static void loge(String str) {
        Rlog.e(LOG_TAG, str);
    }

    public static void registerRIL(CommandsInterface commandsInterface) {
        commandsInterface.getHardwareConfig(sRilHardwareConfig);
        if (sRilHardwareConfig != null) {
            AsyncResult asyncResult = (AsyncResult) sRilHardwareConfig.obj;
            if (asyncResult.exception == null) {
                handleGetHardwareConfigChanged(asyncResult);
            }
        }
        commandsInterface.registerForHardwareConfigChanged(sTelephonyDevController, 1, null);
    }

    public static void unregisterRIL(CommandsInterface commandsInterface) {
        commandsInterface.unregisterForHardwareConfigChanged(sTelephonyDevController);
    }

    private static void updateOrInsert(HardwareConfig hardwareConfig, ArrayList<HardwareConfig> arrayList) {
        synchronized (mLock) {
            int size = arrayList.size();
            for (int i = 0; i < size; i++) {
                HardwareConfig hardwareConfig2 = (HardwareConfig) arrayList.get(i);
                if (hardwareConfig2.uuid.compareTo(hardwareConfig.uuid) == 0) {
                    logd("updateOrInsert: removing: " + hardwareConfig2);
                    arrayList.remove(i);
                }
            }
            logd("updateOrInsert: inserting: " + hardwareConfig);
            arrayList.add(hardwareConfig);
        }
    }

    public ArrayList<HardwareConfig> getAllModems() {
        ArrayList arrayList;
        synchronized (mLock) {
            arrayList = new ArrayList();
            if (mModems.isEmpty()) {
                logd("getAllModems: empty list.");
            } else {
                Iterator it = mModems.iterator();
                while (it.hasNext()) {
                    arrayList.add((HardwareConfig) it.next());
                }
            }
        }
        return arrayList;
    }

    public ArrayList<HardwareConfig> getAllSims() {
        ArrayList arrayList;
        synchronized (mLock) {
            arrayList = new ArrayList();
            if (mSims.isEmpty()) {
                logd("getAllSims: empty list.");
            } else {
                Iterator it = mSims.iterator();
                while (it.hasNext()) {
                    arrayList.add((HardwareConfig) it.next());
                }
            }
        }
        return arrayList;
    }

    public ArrayList<HardwareConfig> getAllSimsForModem(int i) {
        synchronized (mLock) {
            if (mSims.isEmpty()) {
                loge("getAllSimsForModem: no registered sim device?!?");
                return null;
            } else if (i > getModemCount()) {
                loge("getAllSimsForModem: out-of-bounds access for modem device " + i + " max: " + getModemCount());
                return null;
            } else {
                logd("getAllSimsForModem " + i);
                ArrayList<HardwareConfig> arrayList = new ArrayList();
                HardwareConfig modem = getModem(i);
                Iterator it = mSims.iterator();
                while (it.hasNext()) {
                    HardwareConfig hardwareConfig = (HardwareConfig) it.next();
                    if (hardwareConfig.modemUuid.equals(modem.uuid)) {
                        arrayList.add(hardwareConfig);
                    }
                }
                return arrayList;
            }
        }
    }

    public HardwareConfig getModem(int i) {
        HardwareConfig hardwareConfig = null;
        synchronized (mLock) {
            if (mModems.isEmpty()) {
                loge("getModem: no registered modem device?!?");
            } else if (i > getModemCount()) {
                loge("getModem: out-of-bounds access for modem device " + i + " max: " + getModemCount());
            } else {
                logd("getModem: " + i);
                hardwareConfig = (HardwareConfig) mModems.get(i);
            }
        }
        return hardwareConfig;
    }

    public HardwareConfig getModemForSim(int i) {
        synchronized (mLock) {
            if (mModems.isEmpty() || mSims.isEmpty()) {
                loge("getModemForSim: no registered modem/sim device?!?");
                return null;
            } else if (i > getSimCount()) {
                loge("getModemForSim: out-of-bounds access for sim device " + i + " max: " + getSimCount());
                return null;
            } else {
                logd("getModemForSim " + i);
                HardwareConfig sim = getSim(i);
                Iterator it = mModems.iterator();
                while (it.hasNext()) {
                    HardwareConfig hardwareConfig = (HardwareConfig) it.next();
                    if (hardwareConfig.uuid.equals(sim.modemUuid)) {
                        return hardwareConfig;
                    }
                }
                return null;
            }
        }
    }

    public HardwareConfig getSim(int i) {
        HardwareConfig hardwareConfig = null;
        synchronized (mLock) {
            if (mSims.isEmpty()) {
                loge("getSim: no registered sim device?!?");
            } else if (i > getSimCount()) {
                loge("getSim: out-of-bounds access for sim device " + i + " max: " + getSimCount());
            } else {
                logd("getSim: " + i);
                hardwareConfig = (HardwareConfig) mSims.get(i);
            }
        }
        return hardwareConfig;
    }

    public int getSimCount() {
        int size;
        synchronized (mLock) {
            size = mSims.size();
            logd("getSimCount: " + size);
        }
        return size;
    }

    public void handleMessage(Message message) {
        switch (message.what) {
            case 1:
                logd("handleMessage: received EVENT_HARDWARE_CONFIG_CHANGED");
                handleGetHardwareConfigChanged((AsyncResult) message.obj);
                return;
            default:
                loge("handleMessage: Unknown Event " + message.what);
                return;
        }
    }
}
