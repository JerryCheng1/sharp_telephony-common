package com.android.internal.telephony.dataconnection;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.google.android.mms.pdu.CharacterSets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public final class CdmaApnProfileTracker extends Handler {
    private static final int EVENT_GET_DATA_CALL_PROFILE_DONE = 1;
    private static final int EVENT_LOAD_PROFILES = 2;
    private static final int EVENT_READ_MODEM_PROFILES = 0;
    private static final String[] mDefaultApnTypes = new String[]{"default", "mms", "supl", "hipri", "fota", "ims", "cbs"};
    private static final String[] mSupportedApnTypes = new String[]{"default", "mms", "supl", "dun", "hipri", "fota", "ims", "cbs"};
    protected final String LOG_TAG = "CDMA";
    protected ApnSetting mActiveApn;
    private ArrayList<ApnSetting> mApnProfilesList = new ArrayList();
    private CdmaSubscriptionSourceManager mCdmaSsm;
    private RegistrantList mModemApnProfileRegistrants = new RegistrantList();
    private int mOmhReadProfileContext = 0;
    private int mOmhReadProfileCount = 0;
    HashMap<String, Integer> mOmhServicePriorityMap;
    private CDMAPhone mPhone;
    ArrayList<ApnSetting> mTempOmhApnProfilesList = new ArrayList();

    CdmaApnProfileTracker(CDMAPhone cDMAPhone) {
        this.mPhone = cDMAPhone;
        this.mCdmaSsm = CdmaSubscriptionSourceManager.getInstance(cDMAPhone.getContext(), cDMAPhone.mCi, this, 2, null);
        this.mOmhServicePriorityMap = new HashMap();
        sendMessage(obtainMessage(2));
    }

    private void addServiceTypeToUnSpecified() {
        for (Object obj : mSupportedApnTypes) {
            if (!this.mOmhServicePriorityMap.containsKey(obj)) {
                Iterator it = this.mTempOmhApnProfilesList.iterator();
                while (it.hasNext()) {
                    ApnSetting apnSetting = (ApnSetting) it.next();
                    if (((ApnProfileOmh) apnSetting).getApnProfileTypeModem() == ApnProfileTypeModem.PROFILE_TYPE_UNSPECIFIED) {
                        ((ApnProfileOmh) apnSetting).addServiceType(ApnProfileTypeModem.getApnProfileTypeModem(obj));
                        log("OMH: Service Type added to UNSPECIFIED is : " + ApnProfileTypeModem.getApnProfileTypeModem(obj));
                        break;
                    }
                }
            }
        }
    }

    private ApnProfileOmh getDuplicateProfile(ApnSetting apnSetting) {
        Iterator it = this.mTempOmhApnProfilesList.iterator();
        while (it.hasNext()) {
            ApnSetting apnSetting2 = (ApnSetting) it.next();
            if (((ApnProfileOmh) apnSetting).getProfileId() == ((ApnProfileOmh) apnSetting2).getProfileId()) {
                return (ApnProfileOmh) apnSetting2;
            }
        }
        return null;
    }

    private int omhListGetArbitratedPriority(ArrayList<ApnSetting> arrayList, String str) {
        ApnSetting apnSetting = null;
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            ApnSetting apnSetting2 = (ApnSetting) it.next();
            if (!((ApnProfileOmh) apnSetting2).isValidPriority()) {
                log("[OMH] Invalid priority... skipping");
            } else if (apnSetting == null) {
                apnSetting = apnSetting2;
            } else if (str == "supl") {
                if (((ApnProfileOmh) apnSetting2).isPriorityLower(((ApnProfileOmh) apnSetting).getPriority())) {
                    apnSetting = apnSetting2;
                }
            } else if (((ApnProfileOmh) apnSetting2).isPriorityHigher(((ApnProfileOmh) apnSetting).getPriority())) {
                apnSetting = apnSetting2;
            }
        }
        return ((ApnProfileOmh) apnSetting).getPriority();
    }

    private void onGetDataCallProfileDone(AsyncResult asyncResult, int i) {
        if (i == this.mOmhReadProfileContext) {
            if (asyncResult.exception != null) {
                log("OMH: Exception in onGetDataCallProfileDone:" + asyncResult.exception);
                this.mOmhReadProfileCount--;
                return;
            }
            ArrayList arrayList = (ArrayList) asyncResult.result;
            ApnProfileTypeModem apnProfileTypeModem = (ApnProfileTypeModem) asyncResult.userObj;
            this.mOmhReadProfileCount--;
            if (arrayList != null && arrayList.size() > 0) {
                String dataServiceType = apnProfileTypeModem.getDataServiceType();
                log("OMH: # profiles returned from modem:" + arrayList.size() + " for " + dataServiceType);
                this.mOmhServicePriorityMap.put(dataServiceType, Integer.valueOf(omhListGetArbitratedPriority(arrayList, dataServiceType)));
                Iterator it = arrayList.iterator();
                while (it.hasNext()) {
                    ApnSetting apnSetting = (ApnSetting) it.next();
                    ((ApnProfileOmh) apnSetting).setApnProfileTypeModem(apnProfileTypeModem);
                    ApnProfileOmh duplicateProfile = getDuplicateProfile(apnSetting);
                    if (duplicateProfile == null) {
                        this.mTempOmhApnProfilesList.add(apnSetting);
                        ((ApnProfileOmh) apnSetting).addServiceType(ApnProfileTypeModem.getApnProfileTypeModem(dataServiceType));
                    } else {
                        log("OMH: Duplicate Profile " + duplicateProfile);
                        duplicateProfile.addServiceType(ApnProfileTypeModem.getApnProfileTypeModem(dataServiceType));
                    }
                }
            }
            if (this.mOmhReadProfileCount == 0) {
                log("OMH: Modem omh profile read complete.");
                addServiceTypeToUnSpecified();
                this.mApnProfilesList.addAll(this.mTempOmhApnProfilesList);
                this.mModemApnProfileRegistrants.notifyRegistrants();
            }
        }
    }

    private void onReadApnProfilesFromModem() {
        log("OMH: onReadApnProfilesFromModem()");
        this.mOmhReadProfileContext++;
        this.mOmhReadProfileCount = 0;
        this.mTempOmhApnProfilesList.clear();
        this.mOmhServicePriorityMap.clear();
        for (ApnProfileTypeModem apnProfileTypeModem : ApnProfileTypeModem.values()) {
            log("OMH: Reading profiles for:" + apnProfileTypeModem.getid());
            this.mOmhReadProfileCount++;
            this.mPhone.mCi.getDataCallProfile(apnProfileTypeModem.getid(), obtainMessage(1, this.mOmhReadProfileContext, 0, apnProfileTypeModem));
        }
    }

    private String[] parseTypes(String str) {
        if (str != null && !str.equals("")) {
            return str.split(",");
        }
        return new String[]{CharacterSets.MIMENAME_ANY_CHARSET};
    }

    private void readApnProfilesFromModem() {
        sendMessage(obtainMessage(0));
    }

    public void clearActiveApnProfile() {
        this.mActiveApn = null;
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        Log.d("CDMA", "CdmaApnProfileTracker finalized");
    }

    public ApnSetting getApnProfile(String str) {
        Object obj;
        log("getApnProfile: serviceType=" + str);
        Iterator it = this.mApnProfilesList.iterator();
        while (it.hasNext()) {
            obj = (ApnSetting) it.next();
            if (obj.canHandleType(str)) {
                break;
            }
        }
        obj = null;
        log("getApnProfile: return profile=" + obj);
        return obj;
    }

    public ArrayList<ApnSetting> getOmhApnProfilesList() {
        log("getOmhApnProfilesList:" + this.mApnProfilesList);
        return this.mApnProfilesList;
    }

    public void handleMessage(Message message) {
        if (this.mPhone.mIsTheCurrentActivePhone) {
            switch (message.what) {
                case 0:
                    onReadApnProfilesFromModem();
                    return;
                case 1:
                    onGetDataCallProfileDone((AsyncResult) message.obj, message.arg1);
                    return;
                case 2:
                    loadProfiles();
                    return;
                default:
                    return;
            }
        }
        Log.d("CDMA", "Ignore CDMA msgs since CDMA phone is inactive");
    }

    public boolean isApnTypeActive(String str) {
        return this.mActiveApn != null && this.mActiveApn.canHandleType(str);
    }

    /* Access modifiers changed, original: protected */
    public boolean isApnTypeAvailable(String str) {
        for (CharSequence equals : mSupportedApnTypes) {
            if (TextUtils.equals(str, equals)) {
                return true;
            }
        }
        return false;
    }

    /* Access modifiers changed, original: 0000 */
    public void loadProfiles() {
        log("loadProfiles...");
        this.mApnProfilesList.clear();
        readApnProfilesFromModem();
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Log.d("CDMA", "[CdmaApnProfileTracker] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str) {
        Log.e("CDMA", "[CdmaApnProfileTracker] " + str);
    }

    public void registerForModemProfileReady(Handler handler, int i, Object obj) {
        this.mModemApnProfileRegistrants.add(new Registrant(handler, i, obj));
    }

    public void unregisterForModemProfileReady(Handler handler) {
        this.mModemApnProfileRegistrants.remove(handler);
    }
}
