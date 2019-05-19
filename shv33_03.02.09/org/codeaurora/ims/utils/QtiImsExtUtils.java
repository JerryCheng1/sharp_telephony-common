package org.codeaurora.ims.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.provider.Settings.Global;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import org.codeaurora.ims.QtiCarrierConfigs;
import org.codeaurora.ims.QtiImsException;
import org.codeaurora.ims.QtiImsExtManager;

public class QtiImsExtUtils {
    public static final String ACTION_VOPS_SSAC_STATUS = "org.codeaurora.VOIP_VOPS_SSAC_STATUS";
    public static final String CARRIER_ONE_DEFAULT_MCC_MNC = "405854";
    public static final String EXTRA_SSAC = "Ssac";
    public static final String EXTRA_VOPS = "Vops";
    private static String LOG_TAG = "QtiImsExtUtils";
    public static final String PROPERTY_RADIO_ATEL_CARRIER = "persist.radio.atel.carrier";
    public static final int QTI_IMS_ASSURED_TRANSFER = 2;
    public static final int QTI_IMS_BLIND_TRANSFER = 1;
    public static final String QTI_IMS_CALL_DEFLECT_NUMBER = "ims_call_deflect_number";
    public static final int QTI_IMS_CONSULTATIVE_TRANSFER = 4;
    public static final String QTI_IMS_DEFLECT_ENABLED = "qti.ims.call_deflect";
    public static final int QTI_IMS_HO_DISABLE_ALL = 2;
    public static final int QTI_IMS_HO_ENABLED_WLAN_TO_WWAN_ONLY = 3;
    public static final int QTI_IMS_HO_ENABLED_WWAN_TO_WLAN_ONLY = 4;
    public static final int QTI_IMS_HO_ENABLE_ALL = 1;
    public static final int QTI_IMS_HO_INVALID = 0;
    public static final String QTI_IMS_INCOMING_CONF_EXTRA_KEY = "incomingConference";
    public static final int QTI_IMS_REQUEST_ERROR = 1;
    public static final int QTI_IMS_REQUEST_SUCCESS = 0;
    public static final String QTI_IMS_TRANSFER_EXTRA_KEY = "transferType";
    public static final int QTI_IMS_VOLTE_PREF_OFF = 0;
    public static final int QTI_IMS_VOLTE_PREF_ON = 1;
    public static final int QTI_IMS_VOLTE_PREF_UNKNOWN = 2;

    private QtiImsExtUtils() {
    }

    public static String getCallDeflectNumber(ContentResolver contentResolver) {
        String deflectcall = Global.getString(contentResolver, QTI_IMS_CALL_DEFLECT_NUMBER);
        if (deflectcall == null || !deflectcall.isEmpty()) {
            return deflectcall;
        }
        return null;
    }

    public static void setCallDeflectNumber(ContentResolver contentResolver, String value) {
        String deflectNum = value;
        if (value == null || value.isEmpty()) {
            deflectNum = "";
        }
        Global.putString(contentResolver, QTI_IMS_CALL_DEFLECT_NUMBER, deflectNum);
    }

    public static boolean isCallTransferEnabled(Context context) {
        return SystemProperties.getBoolean("persist.radio.ims_call_transfer", false);
    }

    public static boolean useExt(Context context) {
        return isCarrierConfigEnabled(context, QtiCarrierConfigs.USE_VIDEO_UI_EXTENSIONS);
    }

    public static boolean useCustomVideoUi(Context context) {
        return isCarrierConfigEnabled(context, QtiCarrierConfigs.USE_CUSTOM_VIDEO_UI);
    }

    public static boolean isCsRetryConfigEnabled(Context context) {
        return isCarrierConfigEnabled(context, QtiCarrierConfigs.CONFIG_CS_RETRY);
    }

    public static boolean isCarrierOneSupported() {
        return CARRIER_ONE_DEFAULT_MCC_MNC.equals(SystemProperties.get(PROPERTY_RADIO_ATEL_CARRIER));
    }

    public static boolean isCarrierConfigEnabled(Context context, String carrierConfig) {
        PersistableBundle b = getConfigForDefaultImsPhoneId(context);
        if (b != null) {
            return b.getBoolean(carrierConfig, false);
        }
        Log.e(LOG_TAG, "isCarrierConfigEnabled bundle is null");
        return false;
    }

    public static boolean allowVideoCallsInLowBattery(Context context) {
        return isCarrierConfigEnabled(context, QtiCarrierConfigs.ALLOW_VIDEO_CALL_IN_LOW_BATTERY);
    }

    public static boolean shallHidePreviewInVtConference(Context context) {
        return isCarrierConfigEnabled(context, QtiCarrierConfigs.HIDE_PREVIEW_IN_VT_CONFERENCE);
    }

    public static boolean shallRemoveModifyCallCapability(Context context) {
        return isCarrierConfigEnabled(context, QtiCarrierConfigs.REMOVE_MODIFY_CALL_CAPABILITY);
    }

    private static PersistableBundle getConfigForDefaultImsPhoneId(Context context) {
        return getConfigForPhoneId(context, getImsPhoneId());
    }

    private static PersistableBundle getConfigForPhoneId(Context context, int phoneId) {
        if (context == null) {
            Log.e(LOG_TAG, "getConfigForPhoneId context is null");
            return null;
        }
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService("carrier_config");
        if (configManager == null) {
            Log.e(LOG_TAG, "getConfigForPhoneId configManager is null");
            return null;
        } else if (phoneId == -1) {
            Log.e(LOG_TAG, "getConfigForPhoneId phoneId is invalid");
            return null;
        } else {
            int subId = getSubscriptionIdFromPhoneId(context, phoneId);
            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                return configManager.getConfigForSubId(subId);
            }
            Log.e(LOG_TAG, "getConfigForPhoneId subId is invalid");
            return null;
        }
    }

    private static int getImsPhoneId() {
        int phoneId = -1;
        try {
            return QtiImsExtManager.getInstance().getImsPhoneId();
        } catch (QtiImsException e) {
            Log.e(LOG_TAG, "getImsPhoneId failed. Exception = " + e);
            return phoneId;
        }
    }

    private static int getSubscriptionIdFromPhoneId(Context context, int phoneId) {
        SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
        if (subscriptionManager == null) {
            return -1;
        }
        SubscriptionInfo subInfo = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(phoneId);
        if (subInfo == null) {
            return -1;
        }
        return subInfo.getSubscriptionId();
    }
}
