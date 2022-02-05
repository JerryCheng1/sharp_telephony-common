package com.android.internal.telephony;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.ModemStackController;
import com.android.internal.telephony.uicc.UiccController;
import java.util.HashMap;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
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
    private ModemStackController.ModemCapabilityInfo[] mModemCapInfo;
    private int mNumOfSetPrefNwModeSuccess = 0;
    private int mNumPhones = TelephonyManager.getDefault().getPhoneCount();
    private boolean mModemRatCapabilitiesAvailable = false;
    private boolean mIsSetPrefNwModeInProgress = false;
    private int[] mPreferredStackId = new int[this.mNumPhones];
    private int[] mCurrentStackId = new int[this.mNumPhones];
    private int[] mPrefNwMode = new int[this.mNumPhones];
    private int[] mNwModeinSubIdTable = new int[this.mNumPhones];
    private HashMap<Integer, Message> mStoredResponse = new HashMap<>();

    public static ModemBindingPolicyHandler make(Context context, UiccController uiccMgr, CommandsInterface[] ci) {
        Rlog.d(LOG_TAG, "getInstance");
        if (sModemBindingPolicyHandler == null) {
            sModemBindingPolicyHandler = new ModemBindingPolicyHandler(context, uiccMgr, ci);
            return sModemBindingPolicyHandler;
        }
        throw new RuntimeException("ModemBindingPolicyHandler.make() should be called once");
    }

    public static ModemBindingPolicyHandler getInstance() {
        if (sModemBindingPolicyHandler != null) {
            return sModemBindingPolicyHandler;
        }
        throw new RuntimeException("ModemBindingPolicyHdlr.getInstance called before make()");
    }

    private ModemBindingPolicyHandler(Context context, UiccController uiccManager, CommandsInterface[] ci) {
        this.mModemCapInfo = null;
        logd("Constructor - Enter");
        this.mCi = ci;
        this.mContext = context;
        mModemStackController = ModemStackController.getInstance();
        this.mModemCapInfo = new ModemStackController.ModemCapabilityInfo[this.mNumPhones];
        mModemStackController.registerForModemRatCapsAvailable(this, 1, null);
        for (int i = 0; i < this.mCi.length; i++) {
            this.mCi[i].registerForNotAvailable(this, 4, null);
        }
        for (int i2 = 0; i2 < this.mNumPhones; i2++) {
            this.mPreferredStackId[i2] = i2;
            this.mCurrentStackId[i2] = i2;
            this.mNwModeinSubIdTable[i2] = 1;
            this.mStoredResponse.put(Integer.valueOf(i2), null);
        }
        logd("Constructor - Exit");
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                handleModemRatCapsAvailable();
                return;
            case 2:
                handleUpdateBindingDone((AsyncResult) msg.obj);
                return;
            case 3:
                handleSetPreferredNetwork(msg);
                return;
            case 4:
                logd("EVENT_RADIO_NOT_AVAILABLE");
                handleModemRatCapsUnAvailable();
                return;
            default:
                return;
        }
    }

    private void handleSetPreferredNetwork(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        int index = ((Integer) ar.userObj).intValue();
        if (ar.exception == null) {
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
        logd("Failed to set preferred network mode for slot" + index);
        this.mNumOfSetPrefNwModeSuccess = 0;
    }

    private void handleUpdateBindingDone(AsyncResult ar) {
        this.mIsSetPrefNwModeInProgress = false;
        for (int i = 0; i < this.mNumPhones; i++) {
            int errorCode = 0;
            Message resp = this.mStoredResponse.get(Integer.valueOf(i));
            if (resp != null) {
                if (ar.exception != null) {
                    errorCode = 2;
                }
                sendResponseToTarget(resp, errorCode);
                this.mStoredResponse.put(Integer.valueOf(i), null);
            }
        }
    }

    public void updatePrefNwTypeIfRequired() {
        boolean updateRequired = false;
        syncPreferredNwModeFromDB();
        SubscriptionController subCtrlr = SubscriptionController.getInstance();
        int i = 0;
        while (true) {
            if (i >= this.mNumPhones) {
                break;
            }
            int[] subIdList = subCtrlr.getSubId(i);
            if (subIdList != null && subIdList[0] > 0) {
                int subId = subIdList[0];
                if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                    this.mNwModeinSubIdTable[i] = 1;
                } else {
                    this.mNwModeinSubIdTable[i] = subCtrlr.getNwMode(subId);
                }
                if (this.mNwModeinSubIdTable[i] == -1) {
                    updateRequired = false;
                    break;
                } else if (this.mNwModeinSubIdTable[i] != this.mPrefNwMode[i]) {
                    updateRequired = true;
                }
            }
            i++;
        }
        if (updateRequired && updateStackBindingIfRequired(false) == 0) {
            for (int i2 = 0; i2 < this.mNumPhones; i2++) {
                this.mCi[i2].setPreferredNetworkType(this.mNwModeinSubIdTable[i2], obtainMessage(3, Integer.valueOf(i2)));
            }
        }
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

    private void syncCurrentStackInfo() {
        for (int i = 0; i < this.mNumPhones; i++) {
            this.mCurrentStackId[i] = mModemStackController.getCurrentStackIdForPhoneId(i);
            this.mModemCapInfo[this.mCurrentStackId[i]] = mModemStackController.getModemRatCapsForPhoneId(i);
            this.mPreferredStackId[i] = this.mCurrentStackId[i] >= 0 ? this.mCurrentStackId[i] : i;
        }
    }

    private int updateStackBindingIfRequired(boolean isBootUp) {
        boolean isUpdateStackBindingRequired = false;
        updatePreferredStackIds();
        int i = 0;
        while (true) {
            if (i >= this.mNumPhones) {
                break;
            } else if (this.mPreferredStackId[i] != this.mCurrentStackId[i]) {
                isUpdateStackBindingRequired = true;
                break;
            } else {
                i++;
            }
        }
        if (!isBootUp && !isUpdateStackBindingRequired) {
            return 0;
        }
        return mModemStackController.updateStackBinding(this.mPreferredStackId, isBootUp, Message.obtain(this, 2, null));
    }

    private void updatePreferredStackIds() {
        if (!this.mModemRatCapabilitiesAvailable) {
            loge("updatePreferredStackIds: Modem Capabilites are not Available. Return!!");
            return;
        }
        syncPreferredNwModeFromDB();
        syncCurrentStackInfo();
        for (int curPhoneId = 0; curPhoneId < this.mNumPhones; curPhoneId++) {
            if (isNwModeSupportedOnStack(this.mPrefNwMode[curPhoneId], this.mCurrentStackId[curPhoneId])) {
                logd("updatePreferredStackIds: current stack[" + this.mCurrentStackId[curPhoneId] + "]supports NwMode[" + this.mPrefNwMode[curPhoneId] + "] on phoneId[" + curPhoneId + "]");
            } else {
                for (int otherPhoneId = 0; otherPhoneId < this.mNumPhones; otherPhoneId++) {
                    if (otherPhoneId != curPhoneId && isNwModeSupportedOnStack(this.mPrefNwMode[curPhoneId], this.mCurrentStackId[otherPhoneId]) && isNwModeSupportedOnStack(this.mPrefNwMode[otherPhoneId], this.mCurrentStackId[curPhoneId])) {
                        logd("updatePreferredStackIds: Cross Binding is possible between phoneId[" + curPhoneId + "] and phoneId[" + otherPhoneId + "]");
                        this.mPreferredStackId[curPhoneId] = this.mCurrentStackId[otherPhoneId];
                        this.mPreferredStackId[otherPhoneId] = this.mCurrentStackId[curPhoneId];
                    }
                }
            }
        }
    }

    private boolean isNwModeSupportedOnStack(int nwMode, int stackId) {
        int[] numRatSupported = new int[this.mNumPhones];
        int maxNumRatSupported = 0;
        boolean isSupported = false;
        for (int i = 0; i < this.mNumPhones; i++) {
            numRatSupported[i] = getNumOfRatSupportedForNwMode(nwMode, this.mModemCapInfo[i]);
            if (maxNumRatSupported < numRatSupported[i]) {
                maxNumRatSupported = numRatSupported[i];
            }
        }
        if (numRatSupported[stackId] == maxNumRatSupported) {
            isSupported = true;
        }
        logd("nwMode:" + nwMode + ", on stack:" + stackId + " is " + (isSupported ? "Supported" : "Not Supported"));
        return isSupported;
    }

    private void syncPreferredNwModeFromDB() {
        for (int i = 0; i < this.mNumPhones; i++) {
            try {
                this.mPrefNwMode[i] = TelephonyManager.getIntAtIndex(this.mContext.getContentResolver(), "preferred_network_mode", i);
            } catch (Settings.SettingNotFoundException e) {
                loge("getPreferredNetworkMode: Could not find PREFERRED_NETWORK_MODE!!!");
                this.mPrefNwMode[i] = Phone.PREFERRED_NT_MODE;
            }
        }
    }

    public void setPreferredNetworkType(int networkType, int phoneId, Message response) {
        if (this.mIsSetPrefNwModeInProgress) {
            loge("setPreferredNetworkType: In Progress:");
            sendResponseToTarget(response, 2);
            return;
        }
        logd("setPreferredNetworkType: nwMode:" + networkType + ", on phoneId:" + phoneId);
        this.mIsSetPrefNwModeInProgress = true;
        if (updateStackBindingIfRequired(false) == 1) {
            this.mStoredResponse.put(Integer.valueOf(phoneId), response);
            return;
        }
        this.mCi[phoneId].setPreferredNetworkType(networkType, response);
        this.mIsSetPrefNwModeInProgress = false;
    }

    private void sendResponseToTarget(Message response, int responseCode) {
        if (response != null) {
            AsyncResult.forMessage(response, (Object) null, CommandException.fromRilErrno(responseCode));
            response.sendToTarget();
        }
    }

    private int getNumOfRatSupportedForNwMode(int nwMode, ModemStackController.ModemCapabilityInfo modemCaps) {
        int supportedRatMaskForNwMode = 0;
        logd("getNumOfRATsSupportedForNwMode: nwMode[" + nwMode + "] modemCaps = " + modemCaps);
        if (modemCaps == null) {
            loge("getNumOfRATsSupportedForNwMode: Modem Capabilites are null. Return!!");
            return 0;
        }
        switch (nwMode) {
            case 0:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & 101902;
                break;
            case 1:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_GSM_ONLY;
                break;
            case 2:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_WCDMA_ONLY;
                break;
            case 3:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & 101902;
                break;
            case 4:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_CDMA;
                break;
            case 5:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_CDMA_NO_EVDO;
                break;
            case 6:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_EVDO_NO_CDMA;
                break;
            case 7:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_GLOBAL;
                break;
            case 8:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_LTE_CDMA_EVDO;
                break;
            case 9:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_LTE_GSM_WCDMA;
                break;
            case 10:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_LTE_CMDA_EVDO_GSM_WCDMA;
                break;
            case 11:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_LTE_ONLY;
                break;
            case 12:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_LTE_WCDMA;
                break;
            case 13:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_ONLY;
                break;
            case 14:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_WCDMA;
                break;
            case 15:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_LTE;
                break;
            case 16:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_GSM;
                break;
            case 17:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_GSM_LTE;
                break;
            case 18:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_GSM_WCDMA;
                break;
            case 19:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_WCDMA_LTE;
                break;
            case 20:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_GSM_WCDMA_LTE;
                break;
            case 21:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_CDMA_EVDO_GSM_WCDMA;
                break;
            case 22:
                supportedRatMaskForNwMode = modemCaps.getSupportedRatBitMask() & NETWORK_MASK_TD_SCDMA_LTE_CDMA_EVDO_GSM_WCDMA;
                break;
        }
        logd("getNumOfRATsSupportedForNwMode: supportedRatMaskForNwMode:" + supportedRatMaskForNwMode);
        return getNumRatSupportedInMask(supportedRatMaskForNwMode);
    }

    private int getNumRatSupportedInMask(int mask) {
        int noOfOnes = 0;
        while (mask != 0) {
            mask &= mask - 1;
            noOfOnes++;
        }
        return noOfOnes;
    }

    private void logd(String string) {
        Rlog.d(LOG_TAG, string);
    }

    private void loge(String string) {
        Rlog.e(LOG_TAG, string);
    }
}
