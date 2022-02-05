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
import com.android.internal.telephony.dataconnection.ApnProfileOmh;
import com.google.android.mms.pdu.CharacterSets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class CdmaApnProfileTracker extends Handler {
    private static final int EVENT_GET_DATA_CALL_PROFILE_DONE = 1;
    private static final int EVENT_LOAD_PROFILES = 2;
    private static final int EVENT_READ_MODEM_PROFILES = 0;
    protected ApnSetting mActiveApn;
    private CdmaSubscriptionSourceManager mCdmaSsm;
    private CDMAPhone mPhone;
    private static final String[] mSupportedApnTypes = {"default", "mms", "supl", "dun", "hipri", "fota", "ims", "cbs"};
    private static final String[] mDefaultApnTypes = {"default", "mms", "supl", "hipri", "fota", "ims", "cbs"};
    protected final String LOG_TAG = "CDMA";
    private ArrayList<ApnSetting> mApnProfilesList = new ArrayList<>();
    private int mOmhReadProfileContext = 0;
    private int mOmhReadProfileCount = 0;
    ArrayList<ApnSetting> mTempOmhApnProfilesList = new ArrayList<>();
    private RegistrantList mModemApnProfileRegistrants = new RegistrantList();
    HashMap<String, Integer> mOmhServicePriorityMap = new HashMap<>();

    /* JADX INFO: Access modifiers changed from: package-private */
    public CdmaApnProfileTracker(CDMAPhone phone) {
        this.mPhone = phone;
        this.mCdmaSsm = CdmaSubscriptionSourceManager.getInstance(phone.getContext(), phone.mCi, this, 2, null);
        sendMessage(obtainMessage(2));
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void loadProfiles() {
        log("loadProfiles...");
        this.mApnProfilesList.clear();
        readApnProfilesFromModem();
    }

    private String[] parseTypes(String types) {
        return (types == null || types.equals("")) ? new String[]{CharacterSets.MIMENAME_ANY_CHARSET} : types.split(",");
    }

    protected void finalize() {
        Log.d("CDMA", "CdmaApnProfileTracker finalized");
    }

    public void registerForModemProfileReady(Handler h, int what, Object obj) {
        this.mModemApnProfileRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForModemProfileReady(Handler h) {
        this.mModemApnProfileRegistrants.remove(h);
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        if (!this.mPhone.mIsTheCurrentActivePhone) {
            Log.d("CDMA", "Ignore CDMA msgs since CDMA phone is inactive");
            return;
        }
        switch (msg.what) {
            case 0:
                onReadApnProfilesFromModem();
                return;
            case 1:
                onGetDataCallProfileDone((AsyncResult) msg.obj, msg.arg1);
                return;
            case 2:
                loadProfiles();
                return;
            default:
                return;
        }
    }

    private void readApnProfilesFromModem() {
        sendMessage(obtainMessage(0));
    }

    private void onReadApnProfilesFromModem() {
        log("OMH: onReadApnProfilesFromModem()");
        this.mOmhReadProfileContext++;
        this.mOmhReadProfileCount = 0;
        this.mTempOmhApnProfilesList.clear();
        this.mOmhServicePriorityMap.clear();
        ApnProfileOmh.ApnProfileTypeModem[] arr$ = ApnProfileOmh.ApnProfileTypeModem.values();
        for (ApnProfileOmh.ApnProfileTypeModem p : arr$) {
            log("OMH: Reading profiles for:" + p.getid());
            this.mOmhReadProfileCount++;
            this.mPhone.mCi.getDataCallProfile(p.getid(), obtainMessage(1, this.mOmhReadProfileContext, 0, p));
        }
    }

    private void onGetDataCallProfileDone(AsyncResult ar, int context) {
        if (context == this.mOmhReadProfileContext) {
            if (ar.exception != null) {
                log("OMH: Exception in onGetDataCallProfileDone:" + ar.exception);
                this.mOmhReadProfileCount--;
                return;
            }
            ArrayList<ApnSetting> dataProfileListModem = (ArrayList) ar.result;
            ApnProfileOmh.ApnProfileTypeModem modemProfile = (ApnProfileOmh.ApnProfileTypeModem) ar.userObj;
            this.mOmhReadProfileCount--;
            if (dataProfileListModem != null && dataProfileListModem.size() > 0) {
                String serviceType = modemProfile.getDataServiceType();
                log("OMH: # profiles returned from modem:" + dataProfileListModem.size() + " for " + serviceType);
                this.mOmhServicePriorityMap.put(serviceType, Integer.valueOf(omhListGetArbitratedPriority(dataProfileListModem, serviceType)));
                Iterator i$ = dataProfileListModem.iterator();
                while (i$.hasNext()) {
                    ApnSetting apn = i$.next();
                    ((ApnProfileOmh) apn).setApnProfileTypeModem(modemProfile);
                    ApnProfileOmh omhDuplicateDp = getDuplicateProfile(apn);
                    if (omhDuplicateDp == null) {
                        this.mTempOmhApnProfilesList.add(apn);
                        ((ApnProfileOmh) apn).addServiceType(ApnProfileOmh.ApnProfileTypeModem.getApnProfileTypeModem(serviceType));
                    } else {
                        log("OMH: Duplicate Profile " + omhDuplicateDp);
                        omhDuplicateDp.addServiceType(ApnProfileOmh.ApnProfileTypeModem.getApnProfileTypeModem(serviceType));
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

    private ApnProfileOmh getDuplicateProfile(ApnSetting apn) {
        Iterator i$ = this.mTempOmhApnProfilesList.iterator();
        while (i$.hasNext()) {
            ApnSetting dataProfile = i$.next();
            if (((ApnProfileOmh) apn).getProfileId() == ((ApnProfileOmh) dataProfile).getProfileId()) {
                return (ApnProfileOmh) dataProfile;
            }
        }
        return null;
    }

    public ApnSetting getApnProfile(String serviceType) {
        log("getApnProfile: serviceType=" + serviceType);
        ApnSetting profile = null;
        Iterator i$ = this.mApnProfilesList.iterator();
        while (true) {
            if (!i$.hasNext()) {
                break;
            }
            ApnSetting apn = i$.next();
            if (apn.canHandleType(serviceType)) {
                profile = apn;
                break;
            }
        }
        log("getApnProfile: return profile=" + profile);
        return profile;
    }

    public ArrayList<ApnSetting> getOmhApnProfilesList() {
        log("getOmhApnProfilesList:" + this.mApnProfilesList);
        return this.mApnProfilesList;
    }

    /* JADX INFO: Multiple debug info for r3v1 int: [D('i$' java.util.Iterator), D('i$' int)] */
    private void addServiceTypeToUnSpecified() {
        String[] arr$ = mSupportedApnTypes;
        for (String apntype : arr$) {
            if (!this.mOmhServicePriorityMap.containsKey(apntype)) {
                Iterator i$ = this.mTempOmhApnProfilesList.iterator();
                while (true) {
                    if (i$.hasNext()) {
                        ApnSetting apn = i$.next();
                        if (((ApnProfileOmh) apn).getApnProfileTypeModem() == ApnProfileOmh.ApnProfileTypeModem.PROFILE_TYPE_UNSPECIFIED) {
                            ((ApnProfileOmh) apn).addServiceType(ApnProfileOmh.ApnProfileTypeModem.getApnProfileTypeModem(apntype));
                            log("OMH: Service Type added to UNSPECIFIED is : " + ApnProfileOmh.ApnProfileTypeModem.getApnProfileTypeModem(apntype));
                            break;
                        }
                    }
                }
            }
        }
    }

    private int omhListGetArbitratedPriority(ArrayList<ApnSetting> dataProfileListModem, String serviceType) {
        ApnSetting profile = null;
        Iterator i$ = dataProfileListModem.iterator();
        while (i$.hasNext()) {
            ApnSetting apn = i$.next();
            if (!((ApnProfileOmh) apn).isValidPriority()) {
                log("[OMH] Invalid priority... skipping");
            } else if (profile == null) {
                profile = apn;
            } else if (serviceType == "supl") {
                if (((ApnProfileOmh) apn).isPriorityLower(((ApnProfileOmh) profile).getPriority())) {
                    profile = apn;
                }
            } else if (((ApnProfileOmh) apn).isPriorityHigher(((ApnProfileOmh) profile).getPriority())) {
                profile = apn;
            }
        }
        return ((ApnProfileOmh) profile).getPriority();
    }

    public void clearActiveApnProfile() {
        this.mActiveApn = null;
    }

    public boolean isApnTypeActive(String type) {
        return this.mActiveApn != null && this.mActiveApn.canHandleType(type);
    }

    protected boolean isApnTypeAvailable(String type) {
        for (String s : mSupportedApnTypes) {
            if (TextUtils.equals(type, s)) {
                return true;
            }
        }
        return false;
    }

    protected void log(String s) {
        Log.d("CDMA", "[CdmaApnProfileTracker] " + s);
    }

    protected void loge(String s) {
        Log.e("CDMA", "[CdmaApnProfileTracker] " + s);
    }
}
