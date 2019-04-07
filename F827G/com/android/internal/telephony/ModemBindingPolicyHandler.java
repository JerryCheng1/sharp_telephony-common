package com.android.internal.telephony;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.ModemStackController.ModemCapabilityInfo;
import com.android.internal.telephony.uicc.UiccController;
import java.util.HashMap;

public class ModemBindingPolicyHandler extends Handler {
    private static final int EVENT_MODEM_RAT_CAPS_AVAILABLE = 1;
    private static final int EVENT_RADIO_NOT_AVAILABLE = 4;
    private static final int EVENT_SET_NW_MODE_DONE = 3;
    private static final int EVENT_UPDATE_BINDING_DONE = 2;
    private static final int FAILURE = 0;
    static final String LOG_TAG = "ModemBindingPolicyHandler";
    private static final int NETWORK_MASK_CDMA = 12784;
    private static final int NETWORK_MASK_CDMA_NO_EVDO = 112;
    private static final int NETWORK_MASK_EVDO_NO_CDMA = 12672;
    private static final int NETWORK_MASK_GLOBAL = 114686;
    private static final int NETWORK_MASK_GSM_ONLY = 65542;
    private static final int NETWORK_MASK_GSM_UMTS = 101902;
    private static final int NETWORK_MASK_LTE_CDMA_EVDO = 29168;
    private static final int NETWORK_MASK_LTE_CMDA_EVDO_GSM_WCDMA = 131070;
    private static final int NETWORK_MASK_LTE_GSM_WCDMA = 118286;
    private static final int NETWORK_MASK_LTE_ONLY = 16384;
    private static final int NETWORK_MASK_LTE_WCDMA = 52744;
    private static final int NETWORK_MASK_TD_SCDMA_CDMA_EVDO_GSM_WCDMA = 245758;
    private static final int NETWORK_MASK_TD_SCDMA_GSM = 196614;
    private static final int NETWORK_MASK_TD_SCDMA_GSM_LTE = 212998;
    private static final int NETWORK_MASK_TD_SCDMA_GSM_WCDMA = 232974;
    private static final int NETWORK_MASK_TD_SCDMA_GSM_WCDMA_LTE = 249358;
    private static final int NETWORK_MASK_TD_SCDMA_LTE = 147456;
    private static final int NETWORK_MASK_TD_SCDMA_LTE_CDMA_EVDO_GSM_WCDMA = 262142;
    private static final int NETWORK_MASK_TD_SCDMA_ONLY = 131072;
    private static final int NETWORK_MASK_TD_SCDMA_WCDMA = 167432;
    private static final int NETWORK_MASK_TD_SCDMA_WCDMA_LTE = 183816;
    private static final int NETWORK_MASK_WCDMA_ONLY = 36360;
    private static final int NETWORK_MASK_WCDMA_PREF = 101902;
    private static final int SUCCESS = 1;
    private static ModemStackController mModemStackController;
    private static ModemBindingPolicyHandler sModemBindingPolicyHandler;
    private CommandsInterface[] mCi;
    private Context mContext;
    private int[] mCurrentStackId = new int[this.mNumPhones];
    private boolean mIsSetPrefNwModeInProgress = false;
    private ModemCapabilityInfo[] mModemCapInfo = null;
    private boolean mModemRatCapabilitiesAvailable = false;
    private int mNumOfSetPrefNwModeSuccess = 0;
    private int mNumPhones = TelephonyManager.getDefault().getPhoneCount();
    private int[] mNwModeinSubIdTable = new int[this.mNumPhones];
    private int[] mPrefNwMode = new int[this.mNumPhones];
    private int[] mPreferredStackId = new int[this.mNumPhones];
    private HashMap<Integer, Message> mStoredResponse = new HashMap();

    private ModemBindingPolicyHandler(Context context, UiccController uiccController, CommandsInterface[] commandsInterfaceArr) {
        int i = 0;
        logd("Constructor - Enter");
        this.mCi = commandsInterfaceArr;
        this.mContext = context;
        mModemStackController = ModemStackController.getInstance();
        this.mModemCapInfo = new ModemCapabilityInfo[this.mNumPhones];
        mModemStackController.registerForModemRatCapsAvailable(this, 1, null);
        for (CommandsInterface registerForNotAvailable : this.mCi) {
            registerForNotAvailable.registerForNotAvailable(this, 4, null);
        }
        while (i < this.mNumPhones) {
            this.mPreferredStackId[i] = i;
            this.mCurrentStackId[i] = i;
            this.mNwModeinSubIdTable[i] = 1;
            this.mStoredResponse.put(Integer.valueOf(i), null);
            i++;
        }
        logd("Constructor - Exit");
    }

    public static ModemBindingPolicyHandler getInstance() {
        if (sModemBindingPolicyHandler != null) {
            return sModemBindingPolicyHandler;
        }
        throw new RuntimeException("ModemBindingPolicyHdlr.getInstance called before make()");
    }

    private int getNumOfRatSupportedForNwMode(int i, ModemCapabilityInfo modemCapabilityInfo) {
        int i2 = 0;
        logd("getNumOfRATsSupportedForNwMode: nwMode[" + i + "] modemCaps = " + modemCapabilityInfo);
        if (modemCapabilityInfo == null) {
            loge("getNumOfRATsSupportedForNwMode: Modem Capabilites are null. Return!!");
            return 0;
        }
        switch (i) {
            case 0:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & 101902;
                break;
            case 1:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_GSM_ONLY;
                break;
            case 2:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_WCDMA_ONLY;
                break;
            case 3:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & 101902;
                break;
            case 4:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_CDMA;
                break;
            case 5:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_CDMA_NO_EVDO;
                break;
            case 6:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_EVDO_NO_CDMA;
                break;
            case 7:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_GLOBAL;
                break;
            case 8:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_LTE_CDMA_EVDO;
                break;
            case 9:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_LTE_GSM_WCDMA;
                break;
            case 10:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_LTE_CMDA_EVDO_GSM_WCDMA;
                break;
            case 11:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_LTE_ONLY;
                break;
            case 12:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_LTE_WCDMA;
                break;
            case 13:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_ONLY;
                break;
            case 14:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_WCDMA;
                break;
            case 15:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_LTE;
                break;
            case 16:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_GSM;
                break;
            case 17:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_GSM_LTE;
                break;
            case 18:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_GSM_WCDMA;
                break;
            case 19:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_WCDMA_LTE;
                break;
            case 20:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_GSM_WCDMA_LTE;
                break;
            case 21:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_CDMA_EVDO_GSM_WCDMA;
                break;
            case 22:
                i2 = modemCapabilityInfo.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_LTE_CDMA_EVDO_GSM_WCDMA;
                break;
        }
        logd("getNumOfRATsSupportedForNwMode: supportedRatMaskForNwMode:" + i2);
        return getNumRatSupportedInMask(i2);
    }

    private int getNumRatSupportedInMask(int i) {
        int i2 = 0;
        while (i != 0) {
            i &= i - 1;
            i2++;
        }
        return i2;
    }

    private void handleModemRatCapsAvailable() {
        this.mModemRatCapabilitiesAvailable = true;
        if (1 == updateStackBindingIfRequired(true)) {
            this.mIsSetPrefNwModeInProgress = true;
        }
    }

    private void handleModemRatCapsUnAvailable() {
        if (this.mModemRatCapabilitiesAvailable) {
            this.mModemRatCapabilitiesAvailable = false;
        }
    }

    private void handleSetPreferredNetwork(Message message) {
        AsyncResult asyncResult = (AsyncResult) message.obj;
        int intValue = ((Integer) asyncResult.userObj).intValue();
        if (asyncResult.exception == null) {
            this.mNumOfSetPrefNwModeSuccess++;
            if (this.mNumOfSetPrefNwModeSuccess == this.mNumPhones) {
                for (int i = 0; i < this.mNumPhones; i++) {
                    logd("Updating network mode in DB for slot[" + i + "] with " + this.mNwModeinSubIdTable[i]);
                    TelephonyManager.putIntAtIndex(this.mContext.getContentResolver(), "preferred_network_mode", i, this.mNwModeinSubIdTable[i]);
                }
                this.mNumOfSetPrefNwModeSuccess = 0;
                return;
            }
            return;
        }
        logd("Failed to set preferred network mode for slot" + intValue);
        this.mNumOfSetPrefNwModeSuccess = 0;
    }

    private void handleUpdateBindingDone(AsyncResult asyncResult) {
        this.mIsSetPrefNwModeInProgress = false;
        for (int i = 0; i < this.mNumPhones; i++) {
            Message message = (Message) this.mStoredResponse.get(Integer.valueOf(i));
            if (message != null) {
                sendResponseToTarget(message, asyncResult.exception != null ? 2 : 0);
                this.mStoredResponse.put(Integer.valueOf(i), null);
            }
        }
    }

    private boolean isNwModeSupportedOnStack(int i, int i2) {
        boolean z = false;
        int[] iArr = new int[this.mNumPhones];
        boolean z2 = false;
        for (int i3 = 0; i3 < this.mNumPhones; i3++) {
            iArr[i3] = getNumOfRatSupportedForNwMode(i, this.mModemCapInfo[i3]);
            if (z2 < iArr[i3]) {
                z2 = iArr[i3];
            }
        }
        if (iArr[i2] == z2) {
            z = true;
        }
        logd("nwMode:" + i + ", on stack:" + i2 + " is " + (z ? "Supported" : "Not Supported"));
        return z;
    }

    private void logd(String str) {
        Rlog.d(LOG_TAG, str);
    }

    private void loge(String str) {
        Rlog.e(LOG_TAG, str);
    }

    public static ModemBindingPolicyHandler make(Context context, UiccController uiccController, CommandsInterface[] commandsInterfaceArr) {
        Rlog.d(LOG_TAG, "getInstance");
        if (sModemBindingPolicyHandler == null) {
            sModemBindingPolicyHandler = new ModemBindingPolicyHandler(context, uiccController, commandsInterfaceArr);
            return sModemBindingPolicyHandler;
        }
        throw new RuntimeException("ModemBindingPolicyHandler.make() should be called once");
    }

    private void sendResponseToTarget(Message message, int i) {
        if (message != null) {
            AsyncResult.forMessage(message, null, CommandException.fromRilErrno(i));
            message.sendToTarget();
        }
    }

    private void syncCurrentStackInfo() {
        int i = 0;
        while (i < this.mNumPhones) {
            this.mCurrentStackId[i] = mModemStackController.getCurrentStackIdForPhoneId(i);
            this.mModemCapInfo[this.mCurrentStackId[i]] = mModemStackController.getModemRatCapsForPhoneId(i);
            this.mPreferredStackId[i] = this.mCurrentStackId[i] >= 0 ? this.mCurrentStackId[i] : i;
            i++;
        }
    }

    private void syncPreferredNwModeFromDB() {
        for (int i = 0; i < this.mNumPhones; i++) {
            try {
                this.mPrefNwMode[i] = TelephonyManager.getIntAtIndex(this.mContext.getContentResolver(), "preferred_network_mode", i);
            } catch (SettingNotFoundException e) {
                loge("getPreferredNetworkMode: Could not find PREFERRED_NETWORK_MODE!!!");
                this.mPrefNwMode[i] = Phone.PREFERRED_NT_MODE;
            }
        }
    }

    private void updatePreferredStackIds() {
        if (this.mModemRatCapabilitiesAvailable) {
            syncPreferredNwModeFromDB();
            syncCurrentStackInfo();
            int i = 0;
            while (i < this.mNumPhones) {
                if (isNwModeSupportedOnStack(this.mPrefNwMode[i], this.mCurrentStackId[i])) {
                    logd("updatePreferredStackIds: current stack[" + this.mCurrentStackId[i] + "]supports NwMode[" + this.mPrefNwMode[i] + "] on phoneId[" + i + "]");
                } else {
                    int i2 = 0;
                    while (i2 < this.mNumPhones) {
                        if (i2 != i && isNwModeSupportedOnStack(this.mPrefNwMode[i], this.mCurrentStackId[i2]) && isNwModeSupportedOnStack(this.mPrefNwMode[i2], this.mCurrentStackId[i])) {
                            logd("updatePreferredStackIds: Cross Binding is possible between phoneId[" + i + "] and phoneId[" + i2 + "]");
                            this.mPreferredStackId[i] = this.mCurrentStackId[i2];
                            this.mPreferredStackId[i2] = this.mCurrentStackId[i];
                        }
                        i2++;
                    }
                }
                i++;
            }
            return;
        }
        loge("updatePreferredStackIds: Modem Capabilites are not Available. Return!!");
    }

    private int updateStackBindingIfRequired(boolean z) {
        int i;
        updatePreferredStackIds();
        for (i = 0; i < this.mNumPhones; i++) {
            if (this.mPreferredStackId[i] != this.mCurrentStackId[i]) {
                i = 1;
                break;
            }
        }
        i = 0;
        if (!z && r0 == 0) {
            return 0;
        }
        return mModemStackController.updateStackBinding(this.mPreferredStackId, z, Message.obtain(this, 2, null));
    }

    public void handleMessage(Message message) {
        switch (message.what) {
            case 1:
                handleModemRatCapsAvailable();
                return;
            case 2:
                handleUpdateBindingDone((AsyncResult) message.obj);
                return;
            case 3:
                handleSetPreferredNetwork(message);
                return;
            case 4:
                logd("EVENT_RADIO_NOT_AVAILABLE");
                handleModemRatCapsUnAvailable();
                return;
            default:
                return;
        }
    }

    public void setPreferredNetworkType(int i, int i2, Message message) {
        if (this.mIsSetPrefNwModeInProgress) {
            loge("setPreferredNetworkType: In Progress:");
            sendResponseToTarget(message, 2);
            return;
        }
        logd("setPreferredNetworkType: nwMode:" + i + ", on phoneId:" + i2);
        this.mIsSetPrefNwModeInProgress = true;
        if (updateStackBindingIfRequired(false) == 1) {
            this.mStoredResponse.put(Integer.valueOf(i2), message);
            return;
        }
        this.mCi[i2].setPreferredNetworkType(i, message);
        this.mIsSetPrefNwModeInProgress = false;
    }

    public void updatePrefNwTypeIfRequired() {
        int i = 0;
        syncPreferredNwModeFromDB();
        SubscriptionController instance = SubscriptionController.getInstance();
        int i2 = 0;
        for (int i3 = 0; i3 < this.mNumPhones; i3++) {
            int[] subId = instance.getSubId(i3);
            if (subId != null && subId[0] > 0) {
                int i4 = subId[0];
                if (SubscriptionManager.isValidSubscriptionId(i4)) {
                    this.mNwModeinSubIdTable[i3] = instance.getNwMode(i4);
                } else {
                    this.mNwModeinSubIdTable[i3] = 1;
                }
                if (this.mNwModeinSubIdTable[i3] == -1) {
                    i2 = 0;
                    break;
                } else if (this.mNwModeinSubIdTable[i3] != this.mPrefNwMode[i3]) {
                    i2 = 1;
                }
            }
        }
        if (i2 != 0 && updateStackBindingIfRequired(false) == 0) {
            while (i < this.mNumPhones) {
                this.mCi[i].setPreferredNetworkType(this.mNwModeinSubIdTable[i], obtainMessage(3, Integer.valueOf(i)));
                i++;
            }
        }
    }
}
