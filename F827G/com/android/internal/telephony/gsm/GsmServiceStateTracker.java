package com.android.internal.telephony.gsm;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Telephony.CellBroadcasts;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.TimeUtils;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.ProxyController;
import com.android.internal.telephony.RestrictedState;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.cdma.CallFailCause;
import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.SpnOverride;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import jp.co.sharp.android.internal.telephony.OemTelephonyIntents;

final class GsmServiceStateTracker extends ServiceStateTracker {
    static final int CS_DISABLED = 1004;
    static final int CS_EMERGENCY_ENABLED = 1006;
    static final int CS_ENABLED = 1003;
    static final int CS_NORMAL_ENABLED = 1005;
    static final int CS_NOTIFICATION = 999;
    private static final int EVENT_ALL_DATA_DISCONNECTED = 1001;
    static final String LOG_TAG = "GsmSST";
    static final int PS_DISABLED = 1002;
    static final int PS_ENABLED = 1001;
    static final int PS_NOTIFICATION = 888;
    static final boolean VDBG = false;
    private static final String WAKELOCK_TAG = "ServiceStateTracker";
    private int locationUpdatingContext;
    private ContentObserver mAutoTimeObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean z) {
            Rlog.i("GsmServiceStateTracker", "Auto time state changed");
            GsmServiceStateTracker.this.revertToNitzTime();
        }
    };
    private ContentObserver mAutoTimeZoneObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean z) {
            Rlog.i("GsmServiceStateTracker", "Auto time zone state changed");
            GsmServiceStateTracker.this.revertToNitzTimeZone();
        }
    };
    GsmCellLocation mCellLoc;
    private int mCid = -1;
    private ContentResolver mCr;
    private String mCurPlmn = null;
    private boolean mCurShowPlmn = false;
    private boolean mCurShowSpn = false;
    private String mCurSpn = null;
    private int mDataCid = -1;
    private int mDataLAC = -1;
    private boolean mDataRoaming = false;
    private boolean mEmergencyOnly = false;
    private boolean mGotCountryCode = false;
    private boolean mGsmRoaming = false;
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (!GsmServiceStateTracker.this.mPhone.mIsTheCurrentActivePhone) {
                Rlog.e(GsmServiceStateTracker.LOG_TAG, "Received Intent " + intent + " while being destroyed. Ignoring.");
            } else if (intent.getAction().equals("android.intent.action.LOCALE_CHANGED")) {
                GsmServiceStateTracker.this.updateSpnDisplay();
            } else if (intent.getAction().equals("android.intent.action.ACTION_RADIO_OFF")) {
                GsmServiceStateTracker.this.mAlarmSwitch = false;
                GsmServiceStateTracker.this.powerOffRadioSafely(GsmServiceStateTracker.this.mPhone.mDcTracker);
            }
        }
    };
    private boolean mIsSmsMemRptSent = false;
    private int mLAC = -1;
    int mLteActiveBand = Integer.MAX_VALUE;
    int mLteBand = -1;
    private int mMaxDataCalls = 1;
    private boolean mNeedFixZoneAfterNitz = false;
    GsmCellLocation mNewCellLoc;
    private int mNewMaxDataCalls = 1;
    private int mNewReasonDataDenied = -1;
    private boolean mNitzUpdatedTime = false;
    private Notification mNotification;
    private GSMPhone mPhone;
    int mPreferredNetworkType;
    private int mReasonDataDenied = -1;
    private boolean mReportedGprsNoReg = false;
    long mSavedAtTime;
    long mSavedTime;
    String mSavedTimeZone;
    SpnOverride mSpnOverride;
    private boolean mStartedGprsRegCheck = false;
    private WakeLock mWakeLock;
    private boolean mZoneDst;
    private int mZoneOffset;
    private long mZoneTime;

    public GsmServiceStateTracker(GSMPhone gSMPhone) {
        super(gSMPhone, gSMPhone.mCi, new CellInfoGsm());
        this.mPhone = gSMPhone;
        this.mCellLoc = new GsmCellLocation();
        this.mNewCellLoc = new GsmCellLocation();
        this.mSpnOverride = new SpnOverride();
        this.mWakeLock = ((PowerManager) gSMPhone.getContext().getSystemService("power")).newWakeLock(1, WAKELOCK_TAG);
        this.mCi.registerForAvailable(this, 13, null);
        this.mCi.registerForRadioStateChanged(this, 1, null);
        this.mCi.registerForVoiceNetworkStateChanged(this, 2, null);
        this.mCi.setOnNITZTime(this, 11, null);
        this.mCi.setOnRestrictedStateChanged(this, 23, null);
        if (TelBrand.IS_SBM) {
            this.mCi.setOnSpeechCodec(this, 10003, null);
        }
        if (TelBrand.IS_SBM) {
            this.mCi.setOnOemSignalStrengthUpdate(this, 10000, null);
        }
        this.mCi.setOnLteBandInfo(this, 10002, null);
        this.mDesiredPowerState = Global.getInt(gSMPhone.getContext().getContentResolver(), "airplane_mode_on", 0) <= 0;
        this.mCr = gSMPhone.getContext().getContentResolver();
        this.mCr.registerContentObserver(Global.getUriFor("auto_time"), true, this.mAutoTimeObserver);
        this.mCr.registerContentObserver(Global.getUriFor("auto_time_zone"), true, this.mAutoTimeZoneObserver);
        setSignalStrengthDefaultValues();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.LOCALE_CHANGED");
        gSMPhone.getContext().registerReceiver(this.mIntentReceiver, intentFilter);
        intentFilter = new IntentFilter();
        Context context = gSMPhone.getContext();
        intentFilter.addAction("android.intent.action.ACTION_RADIO_OFF");
        context.registerReceiver(this.mIntentReceiver, intentFilter);
    }

    private boolean currentMccEqualsSimMcc(ServiceState serviceState) {
        try {
            return getSystemProperty("gsm.sim.operator.numeric", "").substring(0, 3).equals(serviceState.getOperatorNumeric().substring(0, 3));
        } catch (Exception e) {
            return true;
        }
    }

    private TimeZone findTimeZone(int i, boolean z, long j) {
        String[] availableIDs = TimeZone.getAvailableIDs(z ? i - 3600000 : i);
        Date date = new Date(j);
        for (String timeZone : availableIDs) {
            TimeZone timeZone2 = TimeZone.getTimeZone(timeZone);
            if (timeZone2.getOffset(j) == i && timeZone2.inDaylightTime(date) == z) {
                return timeZone2;
            }
        }
        return null;
    }

    private boolean getAutoTime() {
        try {
            return Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time") > 0;
        } catch (SettingNotFoundException e) {
            return true;
        }
    }

    private boolean getAutoTimeZone() {
        try {
            return Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time_zone") > 0;
        } catch (SettingNotFoundException e) {
            return true;
        }
    }

    private int getCidOrDataCid() {
        return this.mCid != -1 ? this.mCid : this.mDataCid != -1 ? this.mDataCid : -1;
    }

    private int getCombinedRegState() {
        int voiceRegState = this.mSS.getVoiceRegState();
        int dataRegState = this.mSS.getDataRegState();
        if (voiceRegState != 1 || dataRegState != 0) {
            return voiceRegState;
        }
        log("getCombinedRegState: return STATE_IN_SERVICE as Data is in service");
        return dataRegState;
    }

    private int getLacOrDataLac() {
        return this.mLAC != -1 ? this.mLAC : this.mDataLAC != -1 ? this.mDataLAC : -1;
    }

    private TimeZone getNitzTimeZone(int i, boolean z, long j) {
        TimeZone findTimeZone = findTimeZone(i, z, j);
        if (findTimeZone == null) {
            findTimeZone = findTimeZone(i, !z, j);
        }
        log("getNitzTimeZone returning " + (findTimeZone == null ? findTimeZone : findTimeZone.getID()));
        return findTimeZone;
    }

    private UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(this.mPhone.getPhoneId(), 1);
    }

    private boolean isGprsConsistent(int i, int i2) {
        return i2 != 0 || i == 0;
    }

    private boolean isOperatorConsideredNonRoaming(ServiceState serviceState) {
        String operatorNumeric = serviceState.getOperatorNumeric();
        String[] stringArray = this.mPhone.getContext().getResources().getStringArray(17236020);
        if (stringArray.length == 0 || operatorNumeric == null) {
            return false;
        }
        for (String startsWith : stringArray) {
            if (operatorNumeric.startsWith(startsWith)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOperatorConsideredRoaming(ServiceState serviceState) {
        String operatorNumeric = serviceState.getOperatorNumeric();
        String[] stringArray = this.mPhone.getContext().getResources().getStringArray(17236021);
        if (stringArray.length == 0 || operatorNumeric == null) {
            return false;
        }
        for (String startsWith : stringArray) {
            if (operatorNumeric.startsWith(startsWith)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameNamedOperators(ServiceState serviceState) {
        String systemProperty = getSystemProperty("gsm.sim.operator.alpha", "empty");
        String operatorAlphaLong = serviceState.getOperatorAlphaLong();
        String operatorAlphaShort = serviceState.getOperatorAlphaShort();
        boolean z = operatorAlphaLong != null && systemProperty.equals(operatorAlphaLong);
        boolean z2 = operatorAlphaShort != null && systemProperty.equals(operatorAlphaShort);
        return currentMccEqualsSimMcc(serviceState) && (z || z2);
    }

    private boolean isStorageAvailable() {
        return (!TelBrand.IS_DCM || this.mPhone.mSmsStorageMonitor == null) ? true : this.mPhone.mSmsStorageMonitor.isStorageAvailable();
    }

    private void onLteBandInfo(AsyncResult asyncResult) {
        if (asyncResult.exception == null && asyncResult.result != null) {
            int i;
            this.mLteActiveBand = ((Integer) asyncResult.result).intValue();
            switch (this.mLteActiveBand) {
                case 120:
                    i = 1;
                    break;
                case 121:
                    i = 2;
                    break;
                case 122:
                    i = 3;
                    break;
                case 123:
                    i = 4;
                    break;
                case 127:
                    i = 8;
                    break;
                case 149:
                    i = 41;
                    break;
                default:
                    i = -1;
                    break;
            }
            if (this.mLteBand != i && i != -1) {
                this.mLteBand = i;
                Intent intent = new Intent(OemTelephonyIntents.ACTION_LTE_BAND_CHANGED);
                intent.addFlags(536870912);
                intent.putExtra(OemTelephonyIntents.EXTRA_LTE_BAND, i);
                this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
            }
        }
    }

    private void onOemSignalStrengthResult(AsyncResult asyncResult) {
        SignalStrength signalStrength = this.mSignalStrength;
        if (asyncResult.exception != null || asyncResult.result == null) {
            log("onSignalStrengthResult() Exception from RIL : " + asyncResult.exception);
            this.mSignalStrength = new SignalStrength(true);
        } else {
            int i = 99;
            int i2 = -1;
            int i3 = -1;
            int i4 = 99;
            int i5 = Integer.MAX_VALUE;
            int i6 = Integer.MAX_VALUE;
            int i7 = Integer.MAX_VALUE;
            int i8 = Integer.MAX_VALUE;
            int i9 = Integer.MAX_VALUE;
            int[] iArr = (int[]) asyncResult.result;
            if (iArr.length > 15) {
                i = iArr[0] >= 0 ? iArr[0] : 99;
                i2 = iArr[1];
                i3 = iArr[2];
                i4 = iArr[8];
                i5 = iArr[9];
                i6 = iArr[10];
                i7 = iArr[11];
                i8 = iArr[12];
                i9 = iArr[14];
            }
            log("[EXTDBG] rssi=" + i + ", bitErrorRate=" + i2 + ", ecio=" + i3);
            log("[EXTDBG] lteSignalStrength=" + i4 + ", lteRsrp=" + i5 + ", lteRsrq=" + i6 + ", lteRssnr=" + i7 + ", lteCqi=" + i8 + ", lteActiveBand=" + i9);
            this.mSignalStrength = new SignalStrength(i, i2, -1, -1, -1, -1, -1, i4, i5, i6, i7, i8, -1, true, i3, i9);
            this.mSignalStrength.validateInput();
        }
        if (!this.mSignalStrength.equals(signalStrength)) {
            try {
                this.mPhone.notifySignalStrength();
            } catch (NullPointerException e) {
                log("onSignalStrengthResult() Phone already destroyed: " + e + "SignalStrength not notified");
            }
        }
    }

    private void onRestrictedStateChanged(AsyncResult asyncResult) {
        int i = 2;
        boolean z = true;
        RestrictedState restrictedState = new RestrictedState();
        log("onRestrictedStateChanged: E rs " + this.mRestrictedState);
        if (asyncResult.exception == null) {
            int i2 = ((int[]) asyncResult.result)[0];
            boolean z2 = ((i2 & 1) == 0 && (i2 & 4) == 0) ? false : true;
            restrictedState.setCsEmergencyRestricted(z2);
            if (this.mUiccApplcation != null && this.mUiccApplcation.getState() == AppState.APPSTATE_READY) {
                z2 = ((i2 & 2) == 0 && (i2 & 4) == 0) ? false : true;
                restrictedState.setCsNormalRestricted(z2);
                if ((i2 & 16) == 0) {
                    z = false;
                }
                restrictedState.setPsRestricted(z);
            }
            log("onRestrictedStateChanged: new rs " + restrictedState);
            if (!this.mRestrictedState.isPsRestricted() && restrictedState.isPsRestricted()) {
                this.mPsRestrictEnabledRegistrants.notifyRegistrants();
                setNotification(CallFailCause.CDMA_DROP);
            } else if (this.mRestrictedState.isPsRestricted() && !restrictedState.isPsRestricted()) {
                this.mPsRestrictDisabledRegistrants.notifyRegistrants();
                setNotification(1002);
            }
            int i3 = this.mRestrictedState.isCsEmergencyRestricted() ? 2 : 0;
            if (this.mRestrictedState.isCsNormalRestricted()) {
                i3++;
            }
            if (!restrictedState.isCsEmergencyRestricted()) {
                i = 0;
            }
            if (restrictedState.isCsNormalRestricted()) {
                i++;
            }
            if (i3 != i) {
                switch (i) {
                    case 0:
                        setNotification(1004);
                        SystemProperties.set("gsm.cs_normal_restricted", "false");
                        break;
                    case 1:
                        setNotification(1005);
                        SystemProperties.set("gsm.cs_normal_restricted", "true");
                        break;
                    case 2:
                        setNotification(1006);
                        SystemProperties.set("gsm.cs_normal_restricted", "false");
                        break;
                    case 3:
                        setNotification(1003);
                        SystemProperties.set("gsm.cs_normal_restricted", "true");
                        break;
                }
            }
            this.mRestrictedState = restrictedState;
        }
        log("onRestrictedStateChanged: X rs " + this.mRestrictedState);
    }

    private void onSpeechCodec(AsyncResult asyncResult) {
        if (asyncResult.exception == null && asyncResult.result != null) {
            this.mPhone.mCT.updateSpeechCodec((int[]) asyncResult.result);
        }
    }

    private void pollStateDone() {
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean("telephony.test.forceRoaming", false)) {
            this.mNewSS.setVoiceRoaming(true);
            this.mNewSS.setDataRoaming(true);
        }
        useDataRegStateForDataOnlyDevices();
        resetServiceStateInIwlanMode();
        log("Poll ServiceState done:  oldSS=[" + this.mSS + "] newSS=[" + this.mNewSS + "]" + " oldMaxDataCalls=" + this.mMaxDataCalls + " mNewMaxDataCalls=" + this.mNewMaxDataCalls + " oldReasonDataDenied=" + this.mReasonDataDenied + " mNewReasonDataDenied=" + this.mNewReasonDataDenied);
        boolean z = this.mSS.getVoiceRegState() != 0 && this.mNewSS.getVoiceRegState() == 0;
        if (!(this.mSS.getVoiceRegState() == 0 && this.mNewSS.getVoiceRegState() == 0)) {
        }
        boolean z2 = this.mSS.getDataRegState() != 0 && this.mNewSS.getDataRegState() == 0;
        Object obj = (this.mSS.getDataRegState() != 0 || this.mNewSS.getDataRegState() == 0) ? null : 1;
        Object obj2 = this.mSS.getDataRegState() != this.mNewSS.getDataRegState() ? 1 : null;
        Object obj3 = this.mSS.getVoiceRegState() != this.mNewSS.getVoiceRegState() ? 1 : null;
        Object obj4 = this.mSS.getRilVoiceRadioTechnology() != this.mNewSS.getRilVoiceRadioTechnology() ? 1 : null;
        Object obj5 = this.mSS.getRilDataRadioTechnology() != this.mNewSS.getRilDataRadioTechnology() ? 1 : null;
        Object obj6 = !this.mNewSS.equals(this.mSS) ? 1 : null;
        Object obj7 = (this.mSS.getVoiceRoaming() || !this.mNewSS.getVoiceRoaming()) ? null : 1;
        Object obj8 = (!this.mSS.getVoiceRoaming() || this.mNewSS.getVoiceRoaming()) ? null : 1;
        Object obj9 = (this.mSS.getDataRoaming() || !this.mNewSS.getDataRoaming()) ? null : 1;
        Object obj10 = (!this.mSS.getDataRoaming() || this.mNewSS.getDataRoaming()) ? null : 1;
        Object obj11 = !this.mNewCellLoc.equals(this.mCellLoc) ? 1 : null;
        Object obj12 = this.mSS.getCssIndicator() != this.mNewSS.getCssIndicator() ? 1 : null;
        if (TelBrand.IS_DCM && !this.mIsSmsMemRptSent && (z || z2)) {
            log("Called setSendSmsMemoryFull while voice:" + z + " or data:" + z2);
            if (SystemProperties.get("vold.decrypt", "0").equals("trigger_restart_min_framework")) {
                setSendSmsMemoryFull();
            } else {
                sendMessageDelayed(obtainMessage(60), 15000);
            }
        }
        if (!(obj3 == null && obj2 == null)) {
            EventLog.writeEvent(EventLogTags.GSM_SERVICE_STATE_CHANGE, new Object[]{Integer.valueOf(this.mSS.getVoiceRegState()), Integer.valueOf(this.mSS.getDataRegState()), Integer.valueOf(this.mNewSS.getVoiceRegState()), Integer.valueOf(this.mNewSS.getDataRegState())});
        }
        if (obj4 != null) {
            int i = -1;
            GsmCellLocation gsmCellLocation = this.mNewCellLoc;
            if (gsmCellLocation != null) {
                i = gsmCellLocation.getCid();
            }
            EventLog.writeEvent(EventLogTags.GSM_RAT_SWITCHED_NEW, new Object[]{Integer.valueOf(i), Integer.valueOf(this.mSS.getRilVoiceRadioTechnology()), Integer.valueOf(this.mNewSS.getRilVoiceRadioTechnology())});
            log("RAT switched " + ServiceState.rilRadioTechnologyToString(this.mSS.getRilVoiceRadioTechnology()) + " -> " + ServiceState.rilRadioTechnologyToString(this.mNewSS.getRilVoiceRadioTechnology()) + " at cell " + i);
        }
        ServiceState serviceState = this.mSS;
        this.mSS = this.mNewSS;
        this.mNewSS = serviceState;
        this.mNewSS.setStateOutOfService();
        GsmCellLocation gsmCellLocation2 = this.mCellLoc;
        this.mCellLoc = this.mNewCellLoc;
        this.mNewCellLoc = gsmCellLocation2;
        this.mReasonDataDenied = this.mNewReasonDataDenied;
        this.mMaxDataCalls = this.mNewMaxDataCalls;
        if (obj4 != null) {
            updatePhoneObject();
        }
        if (obj5 != null) {
            this.mPhone.setSystemProperty("gsm.network.type", ServiceState.rilRadioTechnologyToString(this.mSS.getRilVoiceRadioTechnology()));
            if (isIwlanFeatureAvailable() && 18 == this.mSS.getRilDataRadioTechnology()) {
                log("pollStateDone: IWLAN enabled");
            }
        }
        if (z) {
            this.mNetworkAttachedRegistrants.notifyRegistrants();
            log("pollStateDone: registering current mNitzUpdatedTime=" + this.mNitzUpdatedTime + " changing to false");
            this.mNitzUpdatedTime = false;
        }
        if (obj6 != null) {
            updateSpnDisplay();
            this.mPhone.setSystemProperty("gsm.operator.alpha", this.mSS.getOperatorAlphaLong());
            String str = SystemProperties.get("gsm.operator.numeric", "");
            String operatorNumeric = this.mSS.getOperatorNumeric();
            this.mPhone.setSystemProperty("gsm.operator.numeric", operatorNumeric);
            updateCarrierMccMncConfiguration(operatorNumeric, str, this.mPhone.getContext());
            if (operatorNumeric == null) {
                log("operatorNumeric is null");
                this.mPhone.setSystemProperty("gsm.operator.iso-country", "");
                this.mGotCountryCode = false;
                this.mNitzUpdatedTime = false;
            } else {
                String countryCodeForMcc;
                TimeZone timeZone;
                String str2 = "";
                String str3 = "";
                try {
                    str3 = operatorNumeric.substring(0, 3);
                    countryCodeForMcc = MccTable.countryCodeForMcc(Integer.parseInt(str3));
                } catch (NumberFormatException e) {
                    loge("pollStateDone: countryCodeForMcc error" + e);
                    countryCodeForMcc = str2;
                } catch (StringIndexOutOfBoundsException e2) {
                    loge("pollStateDone: countryCodeForMcc error" + e2);
                    countryCodeForMcc = str2;
                }
                this.mPhone.setSystemProperty("gsm.operator.iso-country", countryCodeForMcc);
                this.mGotCountryCode = true;
                if (!(this.mNitzUpdatedTime || str3.equals("000") || TextUtils.isEmpty(countryCodeForMcc) || !getAutoTimeZone())) {
                    boolean z3 = SystemProperties.getBoolean("telephony.test.ignore.nitz", false) && (SystemClock.uptimeMillis() & 1) == 0;
                    ArrayList timeZonesWithUniqueOffsets = TimeUtils.getTimeZonesWithUniqueOffsets(countryCodeForMcc);
                    if (timeZonesWithUniqueOffsets.size() == 1 || z3) {
                        timeZone = (TimeZone) timeZonesWithUniqueOffsets.get(0);
                        log("pollStateDone: no nitz but one TZ for iso-cc=" + countryCodeForMcc + " with zone.getID=" + timeZone.getID() + " testOneUniqueOffsetPath=" + z3);
                        setAndBroadcastNetworkSetTimeZone(timeZone.getID());
                    } else {
                        log("pollStateDone: there are " + timeZonesWithUniqueOffsets.size() + " unique offsets for iso-cc='" + countryCodeForMcc + " testOneUniqueOffsetPath=" + z3 + "', do nothing");
                    }
                }
                if (shouldFixTimeZoneNow(this.mPhone, operatorNumeric, str, this.mNeedFixZoneAfterNitz)) {
                    str2 = SystemProperties.get("persist.sys.timezone");
                    log("pollStateDone: fix time zone zoneName='" + str2 + "' mZoneOffset=" + this.mZoneOffset + " mZoneDst=" + this.mZoneDst + " iso-cc='" + countryCodeForMcc + "' iso-cc-idx=" + Arrays.binarySearch(GMT_COUNTRY_CODES, countryCodeForMcc));
                    if ("".equals(countryCodeForMcc) && this.mNeedFixZoneAfterNitz) {
                        timeZone = getNitzTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime);
                        log("pollStateDone: using NITZ TimeZone");
                    } else if (this.mZoneOffset != 0 || this.mZoneDst || str2 == null || str2.length() <= 0 || Arrays.binarySearch(GMT_COUNTRY_CODES, countryCodeForMcc) >= 0) {
                        timeZone = TimeUtils.getTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime, countryCodeForMcc);
                        log("pollStateDone: using getTimeZone(off, dst, time, iso)");
                    } else {
                        timeZone = TimeZone.getDefault();
                        if (this.mNeedFixZoneAfterNitz) {
                            long currentTimeMillis = System.currentTimeMillis();
                            long offset = (long) timeZone.getOffset(currentTimeMillis);
                            log("pollStateDone: tzOffset=" + offset + " ltod=" + TimeUtils.logTimeOfDay(currentTimeMillis));
                            if (getAutoTime()) {
                                currentTimeMillis -= offset;
                                log("pollStateDone: adj ltod=" + TimeUtils.logTimeOfDay(currentTimeMillis));
                                setAndBroadcastNetworkSetTime(currentTimeMillis);
                            } else {
                                this.mSavedTime -= offset;
                            }
                        }
                        log("pollStateDone: using default TimeZone");
                    }
                    this.mNeedFixZoneAfterNitz = false;
                    if (timeZone != null) {
                        log("pollStateDone: zone != null zone.getID=" + timeZone.getID());
                        if (getAutoTimeZone()) {
                            setAndBroadcastNetworkSetTimeZone(timeZone.getID());
                        }
                        saveNitzTimeZone(timeZone.getID());
                    } else {
                        log("pollStateDone: zone == null");
                    }
                }
            }
            this.mPhone.setSystemProperty("gsm.operator.isroaming", this.mSS.getVoiceRoaming() ? "true" : "false");
            setRoamingType(this.mSS);
            log("Broadcasting ServiceState : " + this.mSS);
            this.mPhone.notifyServiceStateChanged(this.mSS);
        }
        if (obj != null) {
            this.mDetachedRegistrants.notifyRegistrants();
        }
        if (!(obj2 == null && obj5 == null)) {
            notifyDataRegStateRilRadioTechnologyChanged();
            if (isIwlanFeatureAvailable() && 18 == this.mSS.getRilDataRadioTechnology()) {
                this.mPhone.notifyDataConnection(Phone.REASON_IWLAN_AVAILABLE);
                obj12 = null;
                this.mIwlanRatAvailable = true;
            } else {
                processIwlanToWwanTransition(this.mSS);
                obj12 = 1;
                this.mIwlanRatAvailable = false;
            }
        }
        if (obj12 != null) {
            this.mPhone.notifyDataConnection(null);
        }
        if (z2) {
            this.mAttachedRegistrants.notifyRegistrants();
        }
        if (obj7 != null) {
            this.mVoiceRoamingOnRegistrants.notifyRegistrants();
        }
        if (obj8 != null) {
            this.mVoiceRoamingOffRegistrants.notifyRegistrants();
        }
        if (obj9 != null) {
            this.mDataRoamingOnRegistrants.notifyRegistrants();
        }
        if (obj10 != null) {
            this.mDataRoamingOffRegistrants.notifyRegistrants();
        }
        if (obj11 != null) {
            this.mPhone.notifyLocationChanged();
        }
        if (isGprsConsistent(this.mSS.getDataRegState(), this.mSS.getVoiceRegState())) {
            this.mReportedGprsNoReg = false;
        } else if (!this.mStartedGprsRegCheck && !this.mReportedGprsNoReg) {
            this.mStartedGprsRegCheck = true;
            sendMessageDelayed(obtainMessage(22), (long) Global.getInt(this.mPhone.getContext().getContentResolver(), "gprs_register_check_period_ms", ServiceStateTracker.DEFAULT_GPRS_CHECK_PERIOD_MILLIS));
        }
    }

    private void queueNextSignalStrengthPoll() {
        if (!this.mDontPollSignalStrength) {
            Message obtainMessage = obtainMessage();
            obtainMessage.what = 10;
            sendMessageDelayed(obtainMessage, 20000);
        }
    }

    private boolean regCodeIsRoaming(int i) {
        return 5 == i;
    }

    private int regCodeToServiceState(int i) {
        switch (i) {
            case 0:
            case 2:
            case 3:
            case 4:
            case 10:
            case 12:
            case 13:
            case 14:
                break;
            case 1:
            case 5:
                return 0;
            default:
                loge("regCodeToServiceState: unexpected service state " + i);
                break;
        }
        return 1;
    }

    private void revertToNitzTime() {
        if (Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time", 0) != 0) {
            log("Reverting to NITZ Time: mSavedTime=" + this.mSavedTime + " mSavedAtTime=" + this.mSavedAtTime);
            if (this.mSavedTime != 0 && this.mSavedAtTime != 0) {
                setAndBroadcastNetworkSetTime(this.mSavedTime + (SystemClock.elapsedRealtime() - this.mSavedAtTime));
            }
        }
    }

    private void revertToNitzTimeZone() {
        if (Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time_zone", 0) != 0) {
            log("Reverting to NITZ TimeZone: tz='" + this.mSavedTimeZone);
            if (this.mSavedTimeZone != null) {
                setAndBroadcastNetworkSetTimeZone(this.mSavedTimeZone);
            }
        }
    }

    private void saveNitzTime(long j) {
        this.mSavedTime = j;
        this.mSavedAtTime = SystemClock.elapsedRealtime();
    }

    private void saveNitzTimeZone(String str) {
        this.mSavedTimeZone = str;
    }

    private void setAndBroadcastNetworkSetTime(long j) {
        log("setAndBroadcastNetworkSetTime: time=" + j + "ms");
        SystemClock.setCurrentTimeMillis(j);
        Intent intent = new Intent("android.intent.action.NETWORK_SET_TIME");
        intent.addFlags(536870912);
        intent.putExtra("time", j);
        this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void setAndBroadcastNetworkSetTimeZone(String str) {
        log("setAndBroadcastNetworkSetTimeZone: setTimeZone=" + str);
        ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).setTimeZone(str);
        Intent intent = new Intent("android.intent.action.NETWORK_SET_TIMEZONE");
        intent.addFlags(536870912);
        intent.putExtra("time-zone", str);
        this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        log("setAndBroadcastNetworkSetTimeZone: call alarm.setTimeZone and broadcast zoneId=" + str);
    }

    private void setNotification(int i) {
        int i2 = PS_NOTIFICATION;
        log("setNotification: create notification " + i);
        if (this.mPhone.getContext().getResources().getBoolean(17956948)) {
            Context context = this.mPhone.getContext();
            this.mNotification = new Notification();
            this.mNotification.when = System.currentTimeMillis();
            this.mNotification.flags = 16;
            this.mNotification.icon = 17301642;
            Intent intent = new Intent();
            this.mNotification.contentIntent = PendingIntent.getActivity(context, 0, intent, 268435456);
            Object obj = "";
            CharSequence text = context.getText(17039586);
            switch (i) {
                case CallFailCause.CDMA_DROP /*1001*/:
                    if (SubscriptionManager.getDefaultDataSubId() == this.mPhone.getSubId()) {
                        CharSequence obj2 = context.getText(17039587);
                        break;
                    }
                    return;
                case 1002:
                    break;
                case 1003:
                    obj2 = context.getText(17039590);
                    i2 = CS_NOTIFICATION;
                    break;
                case 1004:
                    i2 = CS_NOTIFICATION;
                    break;
                case 1005:
                    obj2 = context.getText(17039589);
                    i2 = CS_NOTIFICATION;
                    break;
                case 1006:
                    obj2 = context.getText(17039588);
                    i2 = CS_NOTIFICATION;
                    break;
                default:
                    i2 = CS_NOTIFICATION;
                    break;
            }
            log("setNotification: put notification " + text + " / " + obj2);
            this.mNotification.tickerText = text;
            this.mNotification.color = context.getResources().getColor(17170521);
            this.mNotification.setLatestEventInfo(context, text, obj2, this.mNotification.contentIntent);
            NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
            if (i == 1002 || i == 1004) {
                notificationManager.cancel(i2);
                return;
            } else {
                notificationManager.notify(i2, this.mNotification);
                return;
            }
        }
        log("Ignore all the notifications");
    }

    private void setSendSmsMemoryFull() {
        boolean z = false;
        if (TelBrand.IS_DCM) {
            String str = SystemProperties.get("vold.decrypt", "0");
            log("Get decryptState is: " + str);
            if (str.equals("trigger_restart_min_framework")) {
                log("report encrypt state to WMS");
                this.mCi.reportDecryptStatus(false, obtainMessage(61));
            } else {
                boolean isStorageAvailable = isStorageAvailable();
                StringBuilder append = new StringBuilder().append("is sms really memory full:");
                if (!isStorageAvailable) {
                    z = true;
                }
                log(append.append(z).toString());
                if (isStorageAvailable) {
                    log("report decrypt state to WMS");
                    this.mCi.reportDecryptStatus(true, obtainMessage(61));
                }
            }
            this.mIsSmsMemRptSent = true;
        }
    }

    private void setSignalStrengthDefaultValues() {
        this.mSignalStrength = new SignalStrength(true);
    }

    /* JADX WARNING: No exception handlers in catch block: Catch:{  } */
    /* JADX WARNING: Missing block: B:29:0x0108, code skipped:
            if (r10.mZoneDst != (r4 != 0)) goto L_0x010a;
     */
    private void setTimeFromNITZString(java.lang.String r11, long r12) {
        /*
        r10 = this;
        r0 = -1;
        r2 = 0;
        r1 = 1;
        r4 = android.os.SystemClock.elapsedRealtime();
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r6 = "NITZ: ";
        r3 = r3.append(r6);
        r3 = r3.append(r11);
        r6 = ",";
        r3 = r3.append(r6);
        r3 = r3.append(r12);
        r6 = " start=";
        r3 = r3.append(r6);
        r3 = r3.append(r4);
        r6 = " delay=";
        r3 = r3.append(r6);
        r4 = r4 - r12;
        r3 = r3.append(r4);
        r3 = r3.toString();
        r10.log(r3);
        r3 = "GMT";
        r3 = java.util.TimeZone.getTimeZone(r3);	 Catch:{ RuntimeException -> 0x0186 }
        r6 = java.util.Calendar.getInstance(r3);	 Catch:{ RuntimeException -> 0x0186 }
        r6.clear();	 Catch:{ RuntimeException -> 0x0186 }
        r3 = 16;
        r4 = 0;
        r6.set(r3, r4);	 Catch:{ RuntimeException -> 0x0186 }
        r3 = "[/:,+-]";
        r7 = r11.split(r3);	 Catch:{ RuntimeException -> 0x0186 }
        r3 = 1;
        r4 = 0;
        r4 = r7[r4];	 Catch:{ RuntimeException -> 0x0186 }
        r4 = java.lang.Integer.parseInt(r4);	 Catch:{ RuntimeException -> 0x0186 }
        r4 = r4 + 2000;
        r6.set(r3, r4);	 Catch:{ RuntimeException -> 0x0186 }
        r3 = 2;
        r4 = 1;
        r4 = r7[r4];	 Catch:{ RuntimeException -> 0x0186 }
        r4 = java.lang.Integer.parseInt(r4);	 Catch:{ RuntimeException -> 0x0186 }
        r4 = r4 + -1;
        r6.set(r3, r4);	 Catch:{ RuntimeException -> 0x0186 }
        r3 = 5;
        r4 = 2;
        r4 = r7[r4];	 Catch:{ RuntimeException -> 0x0186 }
        r4 = java.lang.Integer.parseInt(r4);	 Catch:{ RuntimeException -> 0x0186 }
        r6.set(r3, r4);	 Catch:{ RuntimeException -> 0x0186 }
        r3 = 10;
        r4 = 3;
        r4 = r7[r4];	 Catch:{ RuntimeException -> 0x0186 }
        r4 = java.lang.Integer.parseInt(r4);	 Catch:{ RuntimeException -> 0x0186 }
        r6.set(r3, r4);	 Catch:{ RuntimeException -> 0x0186 }
        r3 = 12;
        r4 = 4;
        r4 = r7[r4];	 Catch:{ RuntimeException -> 0x0186 }
        r4 = java.lang.Integer.parseInt(r4);	 Catch:{ RuntimeException -> 0x0186 }
        r6.set(r3, r4);	 Catch:{ RuntimeException -> 0x0186 }
        r3 = 13;
        r4 = 5;
        r4 = r7[r4];	 Catch:{ RuntimeException -> 0x0186 }
        r4 = java.lang.Integer.parseInt(r4);	 Catch:{ RuntimeException -> 0x0186 }
        r6.set(r3, r4);	 Catch:{ RuntimeException -> 0x0186 }
        r3 = 45;
        r3 = r11.indexOf(r3);	 Catch:{ RuntimeException -> 0x0186 }
        if (r3 != r0) goto L_0x0250;
    L_0x00a6:
        r5 = r1;
    L_0x00a7:
        r3 = 6;
        r3 = r7[r3];	 Catch:{ RuntimeException -> 0x0186 }
        r8 = java.lang.Integer.parseInt(r3);	 Catch:{ RuntimeException -> 0x0186 }
        r3 = r7.length;	 Catch:{ RuntimeException -> 0x0186 }
        r4 = 8;
        if (r3 < r4) goto L_0x0253;
    L_0x00b3:
        r3 = 7;
        r3 = r7[r3];	 Catch:{ RuntimeException -> 0x0186 }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ RuntimeException -> 0x0186 }
        r4 = r3;
    L_0x00bb:
        if (r5 == 0) goto L_0x00be;
    L_0x00bd:
        r0 = r1;
    L_0x00be:
        r0 = r0 * r8;
        r0 = r0 * 15;
        r0 = r0 * 60;
        r5 = r0 * 1000;
        r0 = 0;
        r3 = r7.length;	 Catch:{ RuntimeException -> 0x0186 }
        r8 = 9;
        if (r3 < r8) goto L_0x00db;
    L_0x00cb:
        r0 = 8;
        r0 = r7[r0];	 Catch:{ RuntimeException -> 0x0186 }
        r3 = 33;
        r7 = 47;
        r0 = r0.replace(r3, r7);	 Catch:{ RuntimeException -> 0x0186 }
        r0 = java.util.TimeZone.getTimeZone(r0);	 Catch:{ RuntimeException -> 0x0186 }
    L_0x00db:
        r3 = "gsm.operator.iso-country";
        r7 = "";
        r3 = r10.getSystemProperty(r3, r7);	 Catch:{ RuntimeException -> 0x0186 }
        if (r0 != 0) goto L_0x025e;
    L_0x00e5:
        r7 = r10.mGotCountryCode;	 Catch:{ RuntimeException -> 0x0186 }
        if (r7 == 0) goto L_0x025e;
    L_0x00e9:
        if (r3 == 0) goto L_0x0259;
    L_0x00eb:
        r0 = r3.length();	 Catch:{ RuntimeException -> 0x0186 }
        if (r0 <= 0) goto L_0x0259;
    L_0x00f1:
        if (r4 == 0) goto L_0x0256;
    L_0x00f3:
        r0 = r1;
    L_0x00f4:
        r8 = r6.getTimeInMillis();	 Catch:{ RuntimeException -> 0x0186 }
        r0 = android.util.TimeUtils.getTimeZone(r5, r0, r8, r3);	 Catch:{ RuntimeException -> 0x0186 }
        r3 = r0;
    L_0x00fd:
        if (r3 == 0) goto L_0x010a;
    L_0x00ff:
        r0 = r10.mZoneOffset;	 Catch:{ RuntimeException -> 0x0186 }
        if (r0 != r5) goto L_0x010a;
    L_0x0103:
        r7 = r10.mZoneDst;	 Catch:{ RuntimeException -> 0x0186 }
        if (r4 == 0) goto L_0x0150;
    L_0x0107:
        r0 = r1;
    L_0x0108:
        if (r7 == r0) goto L_0x0119;
    L_0x010a:
        r0 = 1;
        r10.mNeedFixZoneAfterNitz = r0;	 Catch:{ RuntimeException -> 0x0186 }
        r10.mZoneOffset = r5;	 Catch:{ RuntimeException -> 0x0186 }
        if (r4 == 0) goto L_0x0152;
    L_0x0111:
        r10.mZoneDst = r1;	 Catch:{ RuntimeException -> 0x0186 }
        r0 = r6.getTimeInMillis();	 Catch:{ RuntimeException -> 0x0186 }
        r10.mZoneTime = r0;	 Catch:{ RuntimeException -> 0x0186 }
    L_0x0119:
        if (r3 == 0) goto L_0x012f;
    L_0x011b:
        r0 = r10.getAutoTimeZone();	 Catch:{ RuntimeException -> 0x0186 }
        if (r0 == 0) goto L_0x0128;
    L_0x0121:
        r0 = r3.getID();	 Catch:{ RuntimeException -> 0x0186 }
        r10.setAndBroadcastNetworkSetTimeZone(r0);	 Catch:{ RuntimeException -> 0x0186 }
    L_0x0128:
        r0 = r3.getID();	 Catch:{ RuntimeException -> 0x0186 }
        r10.saveNitzTimeZone(r0);	 Catch:{ RuntimeException -> 0x0186 }
    L_0x012f:
        r0 = "gsm.ignore-nitz";
        r0 = android.os.SystemProperties.get(r0);	 Catch:{ RuntimeException -> 0x0186 }
        if (r0 == 0) goto L_0x0154;
    L_0x0137:
        r1 = "yes";
        r0 = r0.equals(r1);	 Catch:{ RuntimeException -> 0x0186 }
        if (r0 == 0) goto L_0x0154;
    L_0x013f:
        r0 = "NITZ: Not setting clock because gsm.ignore-nitz is set";
        r10.log(r0);	 Catch:{ RuntimeException -> 0x0186 }
    L_0x0144:
        return;
    L_0x0145:
        r0 = r2;
    L_0x0146:
        r8 = r6.getTimeInMillis();	 Catch:{ RuntimeException -> 0x0186 }
        r0 = r10.getNitzTimeZone(r5, r0, r8);	 Catch:{ RuntimeException -> 0x0186 }
        r3 = r0;
        goto L_0x00fd;
    L_0x0150:
        r0 = r2;
        goto L_0x0108;
    L_0x0152:
        r1 = r2;
        goto L_0x0111;
    L_0x0154:
        r0 = r10.mWakeLock;	 Catch:{ all -> 0x0249 }
        r0.acquire();	 Catch:{ all -> 0x0249 }
        r0 = r10.getAutoTime();	 Catch:{ all -> 0x0249 }
        if (r0 == 0) goto L_0x022b;
    L_0x015f:
        r0 = android.os.SystemClock.elapsedRealtime();	 Catch:{ all -> 0x0249 }
        r0 = r0 - r12;
        r2 = 0;
        r2 = (r0 > r2 ? 1 : (r0 == r2 ? 0 : -1));
        if (r2 >= 0) goto L_0x01a8;
    L_0x016a:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0249 }
        r0.<init>();	 Catch:{ all -> 0x0249 }
        r1 = "NITZ: not setting time, clock has rolled backwards since NITZ time was received, ";
        r0 = r0.append(r1);	 Catch:{ all -> 0x0249 }
        r0 = r0.append(r11);	 Catch:{ all -> 0x0249 }
        r0 = r0.toString();	 Catch:{ all -> 0x0249 }
        r10.log(r0);	 Catch:{ all -> 0x0249 }
        r0 = r10.mWakeLock;	 Catch:{ RuntimeException -> 0x0186 }
        r0.release();	 Catch:{ RuntimeException -> 0x0186 }
        goto L_0x0144;
    L_0x0186:
        r0 = move-exception;
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "NITZ: Parsing NITZ time ";
        r1 = r1.append(r2);
        r1 = r1.append(r11);
        r2 = " ex=";
        r1 = r1.append(r2);
        r0 = r1.append(r0);
        r0 = r0.toString();
        r10.loge(r0);
        goto L_0x0144;
    L_0x01a8:
        r2 = 2147483647; // 0x7fffffff float:NaN double:1.060997895E-314;
        r2 = (r0 > r2 ? 1 : (r0 == r2 ? 0 : -1));
        if (r2 <= 0) goto L_0x01d6;
    L_0x01af:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0249 }
        r2.<init>();	 Catch:{ all -> 0x0249 }
        r3 = "NITZ: not setting time, processing has taken ";
        r2 = r2.append(r3);	 Catch:{ all -> 0x0249 }
        r4 = 86400000; // 0x5265c00 float:7.82218E-36 double:4.2687272E-316;
        r0 = r0 / r4;
        r0 = r2.append(r0);	 Catch:{ all -> 0x0249 }
        r1 = " days";
        r0 = r0.append(r1);	 Catch:{ all -> 0x0249 }
        r0 = r0.toString();	 Catch:{ all -> 0x0249 }
        r10.log(r0);	 Catch:{ all -> 0x0249 }
        r0 = r10.mWakeLock;	 Catch:{ RuntimeException -> 0x0186 }
        r0.release();	 Catch:{ RuntimeException -> 0x0186 }
        goto L_0x0144;
    L_0x01d6:
        r2 = (int) r0;
        r3 = 14;
        r6.add(r3, r2);	 Catch:{ all -> 0x0249 }
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0249 }
        r2.<init>();	 Catch:{ all -> 0x0249 }
        r3 = "NITZ: Setting time of day to ";
        r2 = r2.append(r3);	 Catch:{ all -> 0x0249 }
        r3 = r6.getTime();	 Catch:{ all -> 0x0249 }
        r2 = r2.append(r3);	 Catch:{ all -> 0x0249 }
        r3 = " NITZ receive delay(ms): ";
        r2 = r2.append(r3);	 Catch:{ all -> 0x0249 }
        r0 = r2.append(r0);	 Catch:{ all -> 0x0249 }
        r1 = " gained(ms): ";
        r0 = r0.append(r1);	 Catch:{ all -> 0x0249 }
        r2 = r6.getTimeInMillis();	 Catch:{ all -> 0x0249 }
        r4 = java.lang.System.currentTimeMillis();	 Catch:{ all -> 0x0249 }
        r2 = r2 - r4;
        r0 = r0.append(r2);	 Catch:{ all -> 0x0249 }
        r1 = " from ";
        r0 = r0.append(r1);	 Catch:{ all -> 0x0249 }
        r0 = r0.append(r11);	 Catch:{ all -> 0x0249 }
        r0 = r0.toString();	 Catch:{ all -> 0x0249 }
        r10.log(r0);	 Catch:{ all -> 0x0249 }
        r0 = r6.getTimeInMillis();	 Catch:{ all -> 0x0249 }
        r10.setAndBroadcastNetworkSetTime(r0);	 Catch:{ all -> 0x0249 }
        r0 = "GsmSST";
        r1 = "NITZ: after Setting time of day";
        android.telephony.Rlog.i(r0, r1);	 Catch:{ all -> 0x0249 }
    L_0x022b:
        r0 = "gsm.nitz.time";
        r2 = r6.getTimeInMillis();	 Catch:{ all -> 0x0249 }
        r1 = java.lang.String.valueOf(r2);	 Catch:{ all -> 0x0249 }
        android.os.SystemProperties.set(r0, r1);	 Catch:{ all -> 0x0249 }
        r0 = r6.getTimeInMillis();	 Catch:{ all -> 0x0249 }
        r10.saveNitzTime(r0);	 Catch:{ all -> 0x0249 }
        r0 = 1;
        r10.mNitzUpdatedTime = r0;	 Catch:{ all -> 0x0249 }
        r0 = r10.mWakeLock;	 Catch:{ RuntimeException -> 0x0186 }
        r0.release();	 Catch:{ RuntimeException -> 0x0186 }
        goto L_0x0144;
    L_0x0249:
        r0 = move-exception;
        r1 = r10.mWakeLock;	 Catch:{ RuntimeException -> 0x0186 }
        r1.release();	 Catch:{ RuntimeException -> 0x0186 }
        throw r0;	 Catch:{ RuntimeException -> 0x0186 }
    L_0x0250:
        r5 = r2;
        goto L_0x00a7;
    L_0x0253:
        r4 = r2;
        goto L_0x00bb;
    L_0x0256:
        r0 = r2;
        goto L_0x00f4;
    L_0x0259:
        if (r4 == 0) goto L_0x0145;
    L_0x025b:
        r0 = r1;
        goto L_0x0146;
    L_0x025e:
        r3 = r0;
        goto L_0x00fd;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.gsm.GsmServiceStateTracker.setTimeFromNITZString(java.lang.String, long):void");
    }

    public void dispose() {
        checkCorrectThread();
        log("ServiceStateTracker dispose");
        this.mCi.unregisterForAvailable(this);
        this.mCi.unregisterForRadioStateChanged(this);
        this.mCi.unregisterForVoiceNetworkStateChanged(this);
        if (this.mUiccApplcation != null) {
            this.mUiccApplcation.unregisterForReady(this);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.unregisterForRecordsLoaded(this);
        }
        this.mCi.unSetOnRestrictedStateChanged(this);
        this.mCi.unSetOnNITZTime(this);
        if (TelBrand.IS_SBM) {
            this.mCi.unSetOnSpeechCodec(this);
        }
        if (TelBrand.IS_SBM) {
            this.mCi.unSetOnOemSignalStrengthUpdate(this);
        }
        this.mCi.unSetOnLteBandInfo(this);
        this.mCr.unregisterContentObserver(this.mAutoTimeObserver);
        this.mCr.unregisterContentObserver(this.mAutoTimeZoneObserver);
        this.mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        super.dispose();
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("GsmServiceStateTracker extends:");
        super.dump(fileDescriptor, printWriter, strArr);
        printWriter.println(" mPhone=" + this.mPhone);
        printWriter.println(" mSS=" + this.mSS);
        printWriter.println(" mNewSS=" + this.mNewSS);
        printWriter.println(" mCellLoc=" + this.mCellLoc);
        printWriter.println(" mNewCellLoc=" + this.mNewCellLoc);
        printWriter.println(" mPreferredNetworkType=" + this.mPreferredNetworkType);
        printWriter.println(" mMaxDataCalls=" + this.mMaxDataCalls);
        printWriter.println(" mNewMaxDataCalls=" + this.mNewMaxDataCalls);
        printWriter.println(" mReasonDataDenied=" + this.mReasonDataDenied);
        printWriter.println(" mNewReasonDataDenied=" + this.mNewReasonDataDenied);
        printWriter.println(" mGsmRoaming=" + this.mGsmRoaming);
        printWriter.println(" mDataRoaming=" + this.mDataRoaming);
        printWriter.println(" mEmergencyOnly=" + this.mEmergencyOnly);
        printWriter.println(" mNeedFixZoneAfterNitz=" + this.mNeedFixZoneAfterNitz);
        printWriter.flush();
        printWriter.println(" mZoneOffset=" + this.mZoneOffset);
        printWriter.println(" mZoneDst=" + this.mZoneDst);
        printWriter.println(" mZoneTime=" + this.mZoneTime);
        printWriter.println(" mGotCountryCode=" + this.mGotCountryCode);
        printWriter.println(" mNitzUpdatedTime=" + this.mNitzUpdatedTime);
        printWriter.println(" mSavedTimeZone=" + this.mSavedTimeZone);
        printWriter.println(" mSavedTime=" + this.mSavedTime);
        printWriter.println(" mSavedAtTime=" + this.mSavedAtTime);
        printWriter.println(" mStartedGprsRegCheck=" + this.mStartedGprsRegCheck);
        printWriter.println(" mReportedGprsNoReg=" + this.mReportedGprsNoReg);
        printWriter.println(" mNotification=" + this.mNotification);
        printWriter.println(" mWakeLock=" + this.mWakeLock);
        printWriter.println(" mCurSpn=" + this.mCurSpn);
        printWriter.println(" mCurShowSpn=" + this.mCurShowSpn);
        printWriter.println(" mCurPlmn=" + this.mCurPlmn);
        printWriter.println(" mCurShowPlmn=" + this.mCurShowPlmn);
        printWriter.flush();
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        log("finalize");
    }

    public CellLocation getCellLocation() {
        if (this.mCellLoc.getLac() < 0 || this.mCellLoc.getCid() < 0) {
            List<CellInfo> allCellInfo = getAllCellInfo();
            if (allCellInfo != null) {
                CellLocation gsmCellLocation = new GsmCellLocation();
                for (CellInfo cellInfo : allCellInfo) {
                    if (cellInfo instanceof CellInfoGsm) {
                        CellIdentityGsm cellIdentity = ((CellInfoGsm) cellInfo).getCellIdentity();
                        gsmCellLocation.setLacAndCid(cellIdentity.getLac(), cellIdentity.getCid());
                        gsmCellLocation.setPsc(cellIdentity.getPsc());
                        log("getCellLocation(): X ret GSM info=" + gsmCellLocation);
                        return gsmCellLocation;
                    } else if (cellInfo instanceof CellInfoWcdma) {
                        CellIdentityWcdma cellIdentity2 = ((CellInfoWcdma) cellInfo).getCellIdentity();
                        gsmCellLocation.setLacAndCid(cellIdentity2.getLac(), cellIdentity2.getCid());
                        gsmCellLocation.setPsc(cellIdentity2.getPsc());
                        log("getCellLocation(): X ret WCDMA info=" + gsmCellLocation);
                        return gsmCellLocation;
                    } else if ((cellInfo instanceof CellInfoLte) && (gsmCellLocation.getLac() < 0 || gsmCellLocation.getCid() < 0)) {
                        CellIdentityLte cellIdentity3 = ((CellInfoLte) cellInfo).getCellIdentity();
                        if (!(cellIdentity3.getTac() == Integer.MAX_VALUE || cellIdentity3.getCi() == Integer.MAX_VALUE)) {
                            gsmCellLocation.setLacAndCid(cellIdentity3.getTac(), cellIdentity3.getCi());
                            gsmCellLocation.setPsc(0);
                            log("getCellLocation(): possible LTE cellLocOther=" + gsmCellLocation);
                        }
                    }
                }
                log("getCellLocation(): X ret best answer cellLocOther=" + gsmCellLocation);
                return gsmCellLocation;
            }
            log("getCellLocation(): X empty mCellLoc and CellInfo mCellLoc=" + this.mCellLoc);
            return this.mCellLoc;
        }
        log("getCellLocation(): X good mCellLoc=" + this.mCellLoc);
        return this.mCellLoc;
    }

    public int getCurrentDataConnectionState() {
        return this.mSS.getDataRegState();
    }

    /* Access modifiers changed, original: protected */
    public Phone getPhone() {
        return this.mPhone;
    }

    /* JADX WARNING: Removed duplicated region for block: B:31:0x00b0  */
    /* JADX WARNING: Removed duplicated region for block: B:57:0x012f  */
    /* JADX WARNING: Removed duplicated region for block: B:151:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:66:0x015d  */
    /* JADX WARNING: Removed duplicated region for block: B:149:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:40:0x00de  */
    /* JADX WARNING: Removed duplicated region for block: B:66:0x015d  */
    /* JADX WARNING: Removed duplicated region for block: B:151:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:40:0x00de  */
    /* JADX WARNING: Removed duplicated region for block: B:149:? A:{SYNTHETIC, RETURN} */
    public void handleMessage(android.os.Message r8) {
        /*
        r7 = this;
        r3 = 3;
        r2 = -1;
        r6 = 2;
        r5 = 0;
        r4 = 1;
        r0 = r7.mPhone;
        r0 = r0.mIsTheCurrentActivePhone;
        if (r0 != 0) goto L_0x0036;
    L_0x000b:
        r0 = "GsmSST";
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "Received message ";
        r1 = r1.append(r2);
        r1 = r1.append(r8);
        r2 = "[";
        r1 = r1.append(r2);
        r2 = r8.what;
        r1 = r1.append(r2);
        r2 = "] while being destroyed. Ignoring.";
        r1 = r1.append(r2);
        r1 = r1.toString();
        android.telephony.Rlog.e(r0, r1);
    L_0x0035:
        return;
    L_0x0036:
        r0 = r8.what;
        switch(r0) {
            case 1: goto L_0x005f;
            case 2: goto L_0x0066;
            case 3: goto L_0x006a;
            case 4: goto L_0x0183;
            case 5: goto L_0x0183;
            case 6: goto L_0x0183;
            case 10: goto L_0x018e;
            case 11: goto L_0x0199;
            case 12: goto L_0x01ba;
            case 13: goto L_0x0035;
            case 14: goto L_0x0183;
            case 15: goto L_0x0085;
            case 16: goto L_0x01c9;
            case 17: goto L_0x003f;
            case 18: goto L_0x01ee;
            case 19: goto L_0x0250;
            case 20: goto L_0x021e;
            case 21: goto L_0x0233;
            case 22: goto L_0x0276;
            case 23: goto L_0x02b6;
            case 45: goto L_0x02f8;
            case 60: goto L_0x0302;
            case 61: goto L_0x0325;
            case 1001: goto L_0x02d1;
            case 10000: goto L_0x035b;
            case 10001: goto L_0x0104;
            case 10002: goto L_0x036a;
            case 10003: goto L_0x02c4;
            default: goto L_0x003b;
        };
    L_0x003b:
        super.handleMessage(r8);
        goto L_0x0035;
    L_0x003f:
        r0 = r7.mPhone;
        r0 = r0.getContext();
        r0 = r0.getResources();
        r1 = 17956953; // 0x1120059 float:2.6816214E-38 double:8.8719136E-317;
        r0 = r0.getBoolean(r1);
        if (r0 != 0) goto L_0x0058;
    L_0x0052:
        r0 = r7.mPhone;
        r1 = 0;
        r0.restoreSavedNetworkSelection(r1);
    L_0x0058:
        r7.pollState();
        r7.queueNextSignalStrengthPoll();
        goto L_0x0035;
    L_0x005f:
        r7.setPowerStateToDesired();
        r7.pollState();
        goto L_0x0035;
    L_0x0066:
        r7.pollState();
        goto L_0x0035;
    L_0x006a:
        r0 = r7.mCi;
        r0 = r0.getRadioState();
        r0 = r0.isOn();
        if (r0 == 0) goto L_0x0035;
    L_0x0076:
        r0 = r8.obj;
        r0 = (android.os.AsyncResult) r0;
        r1 = com.android.internal.telephony.TelBrand.IS_SBM;
        if (r1 != 0) goto L_0x0081;
    L_0x007e:
        r7.onSignalStrengthResult(r0, r4);
    L_0x0081:
        r7.queueNextSignalStrengthPoll();
        goto L_0x0035;
    L_0x0085:
        r0 = r8.obj;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.exception;
        if (r1 != 0) goto L_0x00d4;
    L_0x008d:
        r0 = r0.result;
        r0 = (java.lang.String[]) r0;
        r0 = (java.lang.String[]) r0;
        r1 = r0.length;
        if (r1 < r3) goto L_0x0389;
    L_0x0096:
        r1 = r0[r4];
        if (r1 == 0) goto L_0x0386;
    L_0x009a:
        r1 = 1;
        r1 = r0[r1];	 Catch:{ NumberFormatException -> 0x00e8 }
        r1 = r1.length();	 Catch:{ NumberFormatException -> 0x00e8 }
        if (r1 <= 0) goto L_0x0386;
    L_0x00a3:
        r1 = 1;
        r1 = r0[r1];	 Catch:{ NumberFormatException -> 0x00e8 }
        r3 = 16;
        r1 = java.lang.Integer.parseInt(r1, r3);	 Catch:{ NumberFormatException -> 0x00e8 }
    L_0x00ac:
        r3 = r0[r6];
        if (r3 == 0) goto L_0x0383;
    L_0x00b0:
        r3 = 2;
        r3 = r0[r3];	 Catch:{ NumberFormatException -> 0x0376 }
        r3 = r3.length();	 Catch:{ NumberFormatException -> 0x0376 }
        if (r3 <= 0) goto L_0x0383;
    L_0x00b9:
        r3 = 2;
        r0 = r0[r3];	 Catch:{ NumberFormatException -> 0x0376 }
        r3 = 16;
        r2 = java.lang.Integer.parseInt(r0, r3);	 Catch:{ NumberFormatException -> 0x0376 }
        r0 = r2;
    L_0x00c3:
        r7.mLAC = r1;
        r7.mCid = r0;
        r0 = r7.getLacOrDataLac();
        r1 = r7.getCidOrDataCid();
        r2 = r7.mCellLoc;
        r2.setLacAndCid(r0, r1);
    L_0x00d4:
        r0 = r7.locationUpdatingContext;
        r0 = r0 + -1;
        r7.locationUpdatingContext = r0;
        r0 = r7.locationUpdatingContext;
        if (r0 != 0) goto L_0x0035;
    L_0x00de:
        r0 = r7.mPhone;
        r0.notifyLocationChanged();
        r7.disableSingleLocationUpdate();
        goto L_0x0035;
    L_0x00e8:
        r0 = move-exception;
        r1 = r2;
    L_0x00ea:
        r3 = "GsmSST";
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "error parsing location: ";
        r4 = r4.append(r5);
        r0 = r4.append(r0);
        r0 = r0.toString();
        android.telephony.Rlog.w(r3, r0);
        r0 = r2;
        goto L_0x00c3;
    L_0x0104:
        r0 = r8.obj;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.exception;
        if (r1 != 0) goto L_0x0153;
    L_0x010c:
        r0 = r0.result;
        r0 = (java.lang.String[]) r0;
        r0 = (java.lang.String[]) r0;
        r1 = r0.length;
        if (r1 < r3) goto L_0x037f;
    L_0x0115:
        r1 = r0[r4];
        if (r1 == 0) goto L_0x037c;
    L_0x0119:
        r1 = 1;
        r1 = r0[r1];	 Catch:{ NumberFormatException -> 0x0167 }
        r1 = r1.length();	 Catch:{ NumberFormatException -> 0x0167 }
        if (r1 <= 0) goto L_0x037c;
    L_0x0122:
        r1 = 1;
        r1 = r0[r1];	 Catch:{ NumberFormatException -> 0x0167 }
        r3 = 16;
        r1 = java.lang.Integer.parseInt(r1, r3);	 Catch:{ NumberFormatException -> 0x0167 }
    L_0x012b:
        r3 = r0[r6];
        if (r3 == 0) goto L_0x0379;
    L_0x012f:
        r3 = 2;
        r3 = r0[r3];	 Catch:{ NumberFormatException -> 0x0373 }
        r3 = r3.length();	 Catch:{ NumberFormatException -> 0x0373 }
        if (r3 <= 0) goto L_0x0379;
    L_0x0138:
        r3 = 2;
        r0 = r0[r3];	 Catch:{ NumberFormatException -> 0x0373 }
        r3 = 16;
        r2 = java.lang.Integer.parseInt(r0, r3);	 Catch:{ NumberFormatException -> 0x0373 }
        r0 = r2;
    L_0x0142:
        r7.mDataLAC = r1;
        r7.mDataCid = r0;
        r0 = r7.getLacOrDataLac();
        r1 = r7.getCidOrDataCid();
        r2 = r7.mCellLoc;
        r2.setLacAndCid(r0, r1);
    L_0x0153:
        r0 = r7.locationUpdatingContext;
        r0 = r0 + -1;
        r7.locationUpdatingContext = r0;
        r0 = r7.locationUpdatingContext;
        if (r0 != 0) goto L_0x0035;
    L_0x015d:
        r0 = r7.mPhone;
        r0.notifyLocationChanged();
        r7.disableSingleLocationUpdate();
        goto L_0x0035;
    L_0x0167:
        r0 = move-exception;
        r1 = r2;
    L_0x0169:
        r3 = "GsmSST";
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "error parsing location: ";
        r4 = r4.append(r5);
        r0 = r4.append(r0);
        r0 = r0.toString();
        android.telephony.Rlog.w(r3, r0);
        r0 = r2;
        goto L_0x0142;
    L_0x0183:
        r0 = r8.obj;
        r0 = (android.os.AsyncResult) r0;
        r1 = r8.what;
        r7.handlePollStateResult(r1, r0);
        goto L_0x0035;
    L_0x018e:
        r0 = r7.mCi;
        r1 = r7.obtainMessage(r3);
        r0.getSignalStrength(r1);
        goto L_0x0035;
    L_0x0199:
        r0 = r8.obj;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.result;
        r1 = (java.lang.Object[]) r1;
        r1 = (java.lang.Object[]) r1;
        r1 = r1[r5];
        r1 = (java.lang.String) r1;
        r0 = r0.result;
        r0 = (java.lang.Object[]) r0;
        r0 = (java.lang.Object[]) r0;
        r0 = r0[r4];
        r0 = (java.lang.Long) r0;
        r2 = r0.longValue();
        r7.setTimeFromNITZString(r1, r2);
        goto L_0x0035;
    L_0x01ba:
        r0 = com.android.internal.telephony.TelBrand.IS_SBM;
        if (r0 != 0) goto L_0x0035;
    L_0x01be:
        r0 = r8.obj;
        r0 = (android.os.AsyncResult) r0;
        r7.mDontPollSignalStrength = r4;
        r7.onSignalStrengthResult(r0, r4);
        goto L_0x0035;
    L_0x01c9:
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "EVENT_SIM_RECORDS_LOADED: what=";
        r0 = r0.append(r1);
        r1 = r8.what;
        r0 = r0.append(r1);
        r0 = r0.toString();
        r7.log(r0);
        r0 = r7.mPhone;
        r0.notifyOtaspChanged(r3);
        r7.updatePhoneObject();
        r7.updateSpnDisplay();
        goto L_0x0035;
    L_0x01ee:
        r0 = r8.obj;
        r0 = (android.os.AsyncResult) r0;
        r0 = r0.exception;
        if (r0 != 0) goto L_0x0035;
    L_0x01f6:
        r7.locationUpdatingContext = r5;
        r0 = r7.locationUpdatingContext;
        r0 = r0 + 1;
        r7.locationUpdatingContext = r0;
        r0 = r7.mCi;
        r1 = 15;
        r2 = 0;
        r1 = r7.obtainMessage(r1, r2);
        r0.getVoiceRegistrationState(r1);
        r0 = r7.locationUpdatingContext;
        r0 = r0 + 1;
        r7.locationUpdatingContext = r0;
        r0 = r7.mCi;
        r1 = 10001; // 0x2711 float:1.4014E-41 double:4.941E-320;
        r2 = 0;
        r1 = r7.obtainMessage(r1, r2);
        r0.getDataRegistrationState(r1);
        goto L_0x0035;
    L_0x021e:
        r1 = 21;
        r0 = r8.obj;
        r0 = (android.os.AsyncResult) r0;
        r0 = r0.userObj;
        r0 = r7.obtainMessage(r1, r0);
        r1 = r7.mCi;
        r2 = r7.mPreferredNetworkType;
        r1.setPreferredNetworkType(r2, r0);
        goto L_0x0035;
    L_0x0233:
        r0 = r8.obj;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.userObj;
        if (r1 == 0) goto L_0x0035;
    L_0x023b:
        r1 = r0.userObj;
        r1 = (android.os.Message) r1;
        r1 = android.os.AsyncResult.forMessage(r1);
        r2 = r0.exception;
        r1.exception = r2;
        r0 = r0.userObj;
        r0 = (android.os.Message) r0;
        r0.sendToTarget();
        goto L_0x0035;
    L_0x0250:
        r0 = r8.obj;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.exception;
        if (r1 != 0) goto L_0x0272;
    L_0x0258:
        r1 = r0.result;
        r1 = (int[]) r1;
        r1 = (int[]) r1;
        r1 = r1[r5];
        r7.mPreferredNetworkType = r1;
    L_0x0262:
        r1 = 20;
        r0 = r0.userObj;
        r0 = r7.obtainMessage(r1, r0);
        r1 = r7.mCi;
        r2 = 7;
        r1.setPreferredNetworkType(r2, r0);
        goto L_0x0035;
    L_0x0272:
        r1 = 7;
        r7.mPreferredNetworkType = r1;
        goto L_0x0262;
    L_0x0276:
        r0 = r7.mSS;
        if (r0 == 0) goto L_0x02b2;
    L_0x027a:
        r0 = r7.mSS;
        r0 = r0.getDataRegState();
        r1 = r7.mSS;
        r1 = r1.getVoiceRegState();
        r0 = r7.isGprsConsistent(r0, r1);
        if (r0 != 0) goto L_0x02b2;
    L_0x028c:
        r0 = r7.mPhone;
        r0 = r0.getCellLocation();
        r0 = (android.telephony.gsm.GsmCellLocation) r0;
        r1 = r7.mSS;
        r1 = r1.getOperatorNumeric();
        if (r0 == 0) goto L_0x02a0;
    L_0x029c:
        r2 = r0.getCid();
    L_0x02a0:
        r0 = 50107; // 0xc3bb float:7.0215E-41 double:2.4756E-319;
        r3 = new java.lang.Object[r6];
        r3[r5] = r1;
        r1 = java.lang.Integer.valueOf(r2);
        r3[r4] = r1;
        android.util.EventLog.writeEvent(r0, r3);
        r7.mReportedGprsNoReg = r4;
    L_0x02b2:
        r7.mStartedGprsRegCheck = r5;
        goto L_0x0035;
    L_0x02b6:
        r0 = "EVENT_RESTRICTED_STATE_CHANGED";
        r7.log(r0);
        r0 = r8.obj;
        r0 = (android.os.AsyncResult) r0;
        r7.onRestrictedStateChanged(r0);
        goto L_0x0035;
    L_0x02c4:
        r0 = com.android.internal.telephony.TelBrand.IS_SBM;
        if (r0 == 0) goto L_0x0035;
    L_0x02c8:
        r0 = r8.obj;
        r0 = (android.os.AsyncResult) r0;
        r7.onSpeechCodec(r0);
        goto L_0x0035;
    L_0x02d1:
        r0 = android.telephony.SubscriptionManager.getDefaultDataSubId();
        r1 = com.android.internal.telephony.ProxyController.getInstance();
        r1.unregisterForAllDataDisconnected(r0, r7);
        monitor-enter(r7);
        r0 = r7.mPendingRadioPowerOffAfterDataOff;	 Catch:{ all -> 0x02ef }
        if (r0 == 0) goto L_0x02f2;
    L_0x02e1:
        r0 = "EVENT_ALL_DATA_DISCONNECTED, turn radio off now.";
        r7.log(r0);	 Catch:{ all -> 0x02ef }
        r7.hangupAndPowerOff();	 Catch:{ all -> 0x02ef }
        r0 = 0;
        r7.mPendingRadioPowerOffAfterDataOff = r0;	 Catch:{ all -> 0x02ef }
    L_0x02ec:
        monitor-exit(r7);	 Catch:{ all -> 0x02ef }
        goto L_0x0035;
    L_0x02ef:
        r0 = move-exception;
        monitor-exit(r7);	 Catch:{ all -> 0x02ef }
        throw r0;
    L_0x02f2:
        r0 = "EVENT_ALL_DATA_DISCONNECTED is stale";
        r7.log(r0);	 Catch:{ all -> 0x02ef }
        goto L_0x02ec;
    L_0x02f8:
        r0 = "EVENT_CHANGE_IMS_STATE:";
        r7.log(r0);
        r7.setPowerStateToDesired();
        goto L_0x0035;
    L_0x0302:
        r0 = com.android.internal.telephony.TelBrand.IS_DCM;
        if (r0 == 0) goto L_0x0035;
    L_0x0306:
        r0 = "EVENT_SEND_SMS_MEMORY_FULL";
        r7.log(r0);
        r0 = r7.mSS;
        r0 = r0.getVoiceRegState();
        if (r0 == 0) goto L_0x031b;
    L_0x0313:
        r0 = r7.mSS;
        r0 = r0.getDataRegState();
        if (r0 != 0) goto L_0x0035;
    L_0x031b:
        r0 = "Either is in service, now send EVENT_SEND_SMS_MEMORY_FULL";
        r7.log(r0);
        r7.setSendSmsMemoryFull();
        goto L_0x0035;
    L_0x0325:
        r0 = com.android.internal.telephony.TelBrand.IS_DCM;
        if (r0 == 0) goto L_0x0035;
    L_0x0329:
        r0 = "EVENT_REPORT_SMS_MEMORY_FULL";
        r7.log(r0);
        r0 = r8.obj;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.exception;
        if (r1 == 0) goto L_0x0352;
    L_0x0336:
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "handleReportSmsMemoryFullDone, exception:";
        r1 = r1.append(r2);
        r0 = r0.exception;
        r0 = r1.append(r0);
        r0 = r0.toString();
        r7.log(r0);
        r7.mIsSmsMemRptSent = r5;
        goto L_0x0035;
    L_0x0352:
        r0 = "ReportSmsMemory success!";
        r7.log(r0);
        r7.mIsSmsMemRptSent = r4;
        goto L_0x0035;
    L_0x035b:
        r0 = com.android.internal.telephony.TelBrand.IS_SBM;
        if (r0 == 0) goto L_0x0035;
    L_0x035f:
        r0 = r8.obj;
        r0 = (android.os.AsyncResult) r0;
        r7.mDontPollSignalStrength = r4;
        r7.onOemSignalStrengthResult(r0);
        goto L_0x0035;
    L_0x036a:
        r0 = r8.obj;
        r0 = (android.os.AsyncResult) r0;
        r7.onLteBandInfo(r0);
        goto L_0x0035;
    L_0x0373:
        r0 = move-exception;
        goto L_0x0169;
    L_0x0376:
        r0 = move-exception;
        goto L_0x00ea;
    L_0x0379:
        r0 = r2;
        goto L_0x0142;
    L_0x037c:
        r1 = r2;
        goto L_0x012b;
    L_0x037f:
        r0 = r2;
        r1 = r2;
        goto L_0x0142;
    L_0x0383:
        r0 = r2;
        goto L_0x00c3;
    L_0x0386:
        r1 = r2;
        goto L_0x00ac;
    L_0x0389:
        r0 = r2;
        r1 = r2;
        goto L_0x00c3;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.gsm.GsmServiceStateTracker.handleMessage(android.os.Message):void");
    }

    /* Access modifiers changed, original: protected */
    /* JADX WARNING: Unknown top exception splitter block from list: {B:178:0x03a2=Splitter:B:178:0x03a2, B:97:0x0193=Splitter:B:97:0x0193, B:84:0x012b=Splitter:B:84:0x012b, B:153:0x029b=Splitter:B:153:0x029b, B:113:0x01ec=Splitter:B:113:0x01ec} */
    /* JADX WARNING: Removed duplicated region for block: B:203:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:15:0x0051  */
    /* JADX WARNING: Removed duplicated region for block: B:96:0x015c A:{Catch:{ NumberFormatException -> 0x0207 }} */
    /* JADX WARNING: Removed duplicated region for block: B:15:0x0051  */
    /* JADX WARNING: Removed duplicated region for block: B:203:? A:{SYNTHETIC, RETURN} */
    public void handlePollStateResult(int r13, android.os.AsyncResult r14) {
        /*
        r12 = this;
        r0 = r14.userObj;
        r1 = r12.mPollingContext;
        if (r0 == r1) goto L_0x0007;
    L_0x0006:
        return;
    L_0x0007:
        r0 = r14.exception;
        if (r0 == 0) goto L_0x009c;
    L_0x000b:
        r0 = 0;
        r1 = r14.exception;
        r1 = r1 instanceof com.android.internal.telephony.CommandException;
        if (r1 == 0) goto L_0x001c;
    L_0x0012:
        r0 = r14.exception;
        r0 = (com.android.internal.telephony.CommandException) r0;
        r0 = (com.android.internal.telephony.CommandException) r0;
        r0 = r0.getCommandError();
    L_0x001c:
        r1 = com.android.internal.telephony.CommandException.Error.RADIO_NOT_AVAILABLE;
        if (r0 != r1) goto L_0x0024;
    L_0x0020:
        r12.cancelPollState();
        goto L_0x0006;
    L_0x0024:
        r1 = com.android.internal.telephony.CommandException.Error.OP_NOT_ALLOWED_BEFORE_REG_NW;
        if (r0 == r1) goto L_0x0040;
    L_0x0028:
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "RIL implementation has returned an error where it must succeed";
        r0 = r0.append(r1);
        r1 = r14.exception;
        r0 = r0.append(r1);
        r0 = r0.toString();
        r12.loge(r0);
    L_0x0040:
        r0 = r12.mPollingContext;
        r1 = 0;
        r2 = 0;
        r2 = r0[r2];
        r2 = r2 + -1;
        r0[r1] = r2;
        r0 = r12.mPollingContext;
        r1 = 0;
        r0 = r0[r1];
        if (r0 != 0) goto L_0x0006;
    L_0x0051:
        r0 = r12.mGsmRoaming;
        if (r0 != 0) goto L_0x0059;
    L_0x0055:
        r0 = r12.mDataRoaming;
        if (r0 == 0) goto L_0x03c2;
    L_0x0059:
        r0 = 1;
    L_0x005a:
        r1 = r12.mGsmRoaming;
        if (r1 == 0) goto L_0x0077;
    L_0x005e:
        r1 = r12.mNewSS;
        r1 = r12.isOperatorConsideredRoaming(r1);
        if (r1 != 0) goto L_0x0077;
    L_0x0066:
        r1 = r12.mNewSS;
        r1 = r12.isSameNamedOperators(r1);
        if (r1 != 0) goto L_0x0076;
    L_0x006e:
        r1 = r12.mNewSS;
        r1 = r12.isOperatorConsideredNonRoaming(r1);
        if (r1 == 0) goto L_0x0077;
    L_0x0076:
        r0 = 0;
    L_0x0077:
        r1 = r12.mPhone;
        r2 = r12.mNewSS;
        r2 = r2.getOperatorNumeric();
        r1 = r1.isMccMncMarkedAsNonRoaming(r2);
        if (r1 == 0) goto L_0x03c5;
    L_0x0085:
        r0 = 0;
    L_0x0086:
        r1 = r12.mNewSS;
        r1.setVoiceRoaming(r0);
        r1 = r12.mNewSS;
        r1.setDataRoaming(r0);
        r0 = r12.mNewSS;
        r1 = r12.mEmergencyOnly;
        r0.setEmergencyOnly(r1);
        r12.pollStateDone();
        goto L_0x0006;
    L_0x009c:
        switch(r13) {
            case 4: goto L_0x00a0;
            case 5: goto L_0x0224;
            case 6: goto L_0x0306;
            case 14: goto L_0x0393;
            default: goto L_0x009f;
        };
    L_0x009f:
        goto L_0x0040;
    L_0x00a0:
        r0 = r14.result;	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = (java.lang.String[]) r0;	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = (java.lang.String[]) r0;	 Catch:{ RuntimeException -> 0x01d1 }
        r1 = -1;
        r2 = -1;
        r10 = 0;
        r6 = 0;
        r3 = 0;
        r7 = 4;
        r9 = -1;
        r11 = 0;
        r8 = 0;
        r4 = 0;
        r5 = r0.length;	 Catch:{ RuntimeException -> 0x01d1 }
        if (r5 <= 0) goto L_0x03f0;
    L_0x00b3:
        r5 = 0;
        r5 = r0[r5];	 Catch:{ NumberFormatException -> 0x01ea }
        r7 = java.lang.Integer.parseInt(r5);	 Catch:{ NumberFormatException -> 0x01ea }
        r5 = r0.length;	 Catch:{ NumberFormatException -> 0x01ea }
        r10 = 3;
        if (r5 < r10) goto L_0x00fc;
    L_0x00be:
        r5 = 1;
        r5 = r0[r5];
        if (r5 == 0) goto L_0x00d5;
    L_0x00c3:
        r5 = 1;
        r5 = r0[r5];	 Catch:{ NumberFormatException -> 0x01ea }
        r5 = r5.length();	 Catch:{ NumberFormatException -> 0x01ea }
        if (r5 <= 0) goto L_0x00d5;
    L_0x00cc:
        r5 = 1;
        r5 = r0[r5];	 Catch:{ NumberFormatException -> 0x01ea }
        r10 = 16;
        r1 = java.lang.Integer.parseInt(r5, r10);	 Catch:{ NumberFormatException -> 0x01ea }
    L_0x00d5:
        r5 = 2;
        r5 = r0[r5];
        if (r5 == 0) goto L_0x00ec;
    L_0x00da:
        r5 = 2;
        r5 = r0[r5];	 Catch:{ NumberFormatException -> 0x01ea }
        r5 = r5.length();	 Catch:{ NumberFormatException -> 0x01ea }
        if (r5 <= 0) goto L_0x00ec;
    L_0x00e3:
        r5 = 2;
        r5 = r0[r5];	 Catch:{ NumberFormatException -> 0x01ea }
        r10 = 16;
        r2 = java.lang.Integer.parseInt(r5, r10);	 Catch:{ NumberFormatException -> 0x01ea }
    L_0x00ec:
        r5 = r0.length;	 Catch:{ NumberFormatException -> 0x01ea }
        r10 = 4;
        if (r5 < r10) goto L_0x00fc;
    L_0x00f0:
        r5 = 3;
        r5 = r0[r5];
        if (r5 == 0) goto L_0x00fc;
    L_0x00f5:
        r3 = 3;
        r3 = r0[r3];	 Catch:{ NumberFormatException -> 0x01ea }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x01ea }
    L_0x00fc:
        r5 = r0.length;	 Catch:{ NumberFormatException -> 0x03dc }
        r6 = 7;
        if (r5 < r6) goto L_0x010c;
    L_0x0100:
        r5 = 7;
        r5 = r0[r5];
        if (r5 == 0) goto L_0x010c;
    L_0x0105:
        r4 = 7;
        r4 = r0[r4];	 Catch:{ NumberFormatException -> 0x03dc }
        r4 = java.lang.Integer.parseInt(r4);	 Catch:{ NumberFormatException -> 0x03dc }
    L_0x010c:
        r5 = r0.length;	 Catch:{ NumberFormatException -> 0x03e1 }
        r6 = 14;
        if (r5 <= r6) goto L_0x03ed;
    L_0x0111:
        r5 = 14;
        r5 = r0[r5];
        if (r5 == 0) goto L_0x03ed;
    L_0x0117:
        r5 = 14;
        r5 = r0[r5];	 Catch:{ NumberFormatException -> 0x03e1 }
        r5 = r5.length();	 Catch:{ NumberFormatException -> 0x03e1 }
        if (r5 <= 0) goto L_0x03ed;
    L_0x0121:
        r5 = 14;
        r5 = r0[r5];	 Catch:{ NumberFormatException -> 0x03e1 }
        r6 = 16;
        r5 = java.lang.Integer.parseInt(r5, r6);	 Catch:{ NumberFormatException -> 0x03e1 }
    L_0x012b:
        r6 = r12.regCodeIsRoaming(r7);	 Catch:{ RuntimeException -> 0x01d1 }
        r12.mGsmRoaming = r6;	 Catch:{ RuntimeException -> 0x01d1 }
        r6 = r12.mNewSS;	 Catch:{ RuntimeException -> 0x01d1 }
        r8 = r12.regCodeToServiceState(r7);	 Catch:{ RuntimeException -> 0x01d1 }
        r6.setState(r8);	 Catch:{ RuntimeException -> 0x01d1 }
        r6 = r12.mNewSS;	 Catch:{ RuntimeException -> 0x01d1 }
        r6.setRilVoiceRadioTechnology(r3);	 Catch:{ RuntimeException -> 0x01d1 }
        r3 = r12.mNewSS;	 Catch:{ RuntimeException -> 0x01d1 }
        r3.setCssIndicator(r4);	 Catch:{ RuntimeException -> 0x01d1 }
        r3 = 3;
        if (r7 == r3) goto L_0x014b;
    L_0x0147:
        r3 = 13;
        if (r7 != r3) goto L_0x0193;
    L_0x014b:
        r3 = r0.length;	 Catch:{ RuntimeException -> 0x01d1 }
        r4 = 14;
        if (r3 < r4) goto L_0x0193;
    L_0x0150:
        r3 = 13;
        r0 = r0[r3];	 Catch:{ NumberFormatException -> 0x0207 }
        r0 = java.lang.Integer.parseInt(r0);	 Catch:{ NumberFormatException -> 0x0207 }
        r3 = 10;
        if (r0 != r3) goto L_0x0193;
    L_0x015c:
        r0 = new java.lang.StringBuilder;	 Catch:{ NumberFormatException -> 0x0207 }
        r0.<init>();	 Catch:{ NumberFormatException -> 0x0207 }
        r3 = " Posting Managed roaming intent sub = ";
        r0 = r0.append(r3);	 Catch:{ NumberFormatException -> 0x0207 }
        r3 = r12.mPhone;	 Catch:{ NumberFormatException -> 0x0207 }
        r3 = r3.getSubId();	 Catch:{ NumberFormatException -> 0x0207 }
        r0 = r0.append(r3);	 Catch:{ NumberFormatException -> 0x0207 }
        r0 = r0.toString();	 Catch:{ NumberFormatException -> 0x0207 }
        r12.log(r0);	 Catch:{ NumberFormatException -> 0x0207 }
        r0 = new android.content.Intent;	 Catch:{ NumberFormatException -> 0x0207 }
        r3 = "codeaurora.intent.action.ACTION_MANAGED_ROAMING_IND";
        r0.<init>(r3);	 Catch:{ NumberFormatException -> 0x0207 }
        r3 = "subscription";
        r4 = r12.mPhone;	 Catch:{ NumberFormatException -> 0x0207 }
        r4 = r4.getSubId();	 Catch:{ NumberFormatException -> 0x0207 }
        r0.putExtra(r3, r4);	 Catch:{ NumberFormatException -> 0x0207 }
        r3 = r12.mPhone;	 Catch:{ NumberFormatException -> 0x0207 }
        r3 = r3.getContext();	 Catch:{ NumberFormatException -> 0x0207 }
        r3.sendBroadcast(r0);	 Catch:{ NumberFormatException -> 0x0207 }
    L_0x0193:
        r0 = r12.mPhoneBase;	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = r0.getContext();	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = r0.getResources();	 Catch:{ RuntimeException -> 0x01d1 }
        r3 = 17956947; // 0x1120053 float:2.6816197E-38 double:8.8719106E-317;
        r0 = r0.getBoolean(r3);	 Catch:{ RuntimeException -> 0x01d1 }
        r3 = 13;
        if (r7 == r3) goto L_0x01b4;
    L_0x01a8:
        r3 = 10;
        if (r7 == r3) goto L_0x01b4;
    L_0x01ac:
        r3 = 12;
        if (r7 == r3) goto L_0x01b4;
    L_0x01b0:
        r3 = 14;
        if (r7 != r3) goto L_0x0220;
    L_0x01b4:
        if (r0 == 0) goto L_0x0220;
    L_0x01b6:
        r0 = 1;
        r12.mEmergencyOnly = r0;	 Catch:{ RuntimeException -> 0x01d1 }
    L_0x01b9:
        r12.mLAC = r1;	 Catch:{ RuntimeException -> 0x01d1 }
        r12.mCid = r2;	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = r12.getLacOrDataLac();	 Catch:{ RuntimeException -> 0x01d1 }
        r1 = r12.getCidOrDataCid();	 Catch:{ RuntimeException -> 0x01d1 }
        r2 = r12.mNewCellLoc;	 Catch:{ RuntimeException -> 0x01d1 }
        r2.setLacAndCid(r0, r1);	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = r12.mNewCellLoc;	 Catch:{ RuntimeException -> 0x01d1 }
        r0.setPsc(r5);	 Catch:{ RuntimeException -> 0x01d1 }
        goto L_0x0040;
    L_0x01d1:
        r0 = move-exception;
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "Exception while polling service state. Probably malformed RIL response.";
        r1 = r1.append(r2);
        r0 = r1.append(r0);
        r0 = r0.toString();
        r12.loge(r0);
        goto L_0x0040;
    L_0x01ea:
        r3 = move-exception;
        r5 = r3;
    L_0x01ec:
        r3 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x01d1 }
        r3.<init>();	 Catch:{ RuntimeException -> 0x01d1 }
        r4 = "error parsing RegistrationState: ";
        r3 = r3.append(r4);	 Catch:{ RuntimeException -> 0x01d1 }
        r3 = r3.append(r5);	 Catch:{ RuntimeException -> 0x01d1 }
        r3 = r3.toString();	 Catch:{ RuntimeException -> 0x01d1 }
        r12.loge(r3);	 Catch:{ RuntimeException -> 0x01d1 }
        r5 = r9;
        r3 = r6;
        r4 = r8;
        goto L_0x012b;
    L_0x0207:
        r0 = move-exception;
        r3 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x01d1 }
        r3.<init>();	 Catch:{ RuntimeException -> 0x01d1 }
        r4 = "error parsing regCode: ";
        r3 = r3.append(r4);	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = r3.append(r0);	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x01d1 }
        r12.loge(r0);	 Catch:{ RuntimeException -> 0x01d1 }
        goto L_0x0193;
    L_0x0220:
        r0 = 0;
        r12.mEmergencyOnly = r0;	 Catch:{ RuntimeException -> 0x01d1 }
        goto L_0x01b9;
    L_0x0224:
        r0 = r14.result;	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = (java.lang.String[]) r0;	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = (java.lang.String[]) r0;	 Catch:{ RuntimeException -> 0x01d1 }
        r6 = 0;
        r3 = 0;
        r1 = 0;
        r2 = -1;
        r5 = -1;
        r4 = 4;
        r7 = -1;
        r12.mNewReasonDataDenied = r7;	 Catch:{ RuntimeException -> 0x01d1 }
        r7 = 1;
        r12.mNewMaxDataCalls = r7;	 Catch:{ RuntimeException -> 0x01d1 }
        r7 = r0.length;	 Catch:{ RuntimeException -> 0x01d1 }
        if (r7 <= 0) goto L_0x03e9;
    L_0x0239:
        r6 = 0;
        r6 = r0[r6];	 Catch:{ NumberFormatException -> 0x02ec }
        r4 = java.lang.Integer.parseInt(r6);	 Catch:{ NumberFormatException -> 0x02ec }
        r6 = r0.length;	 Catch:{ NumberFormatException -> 0x02ec }
        r7 = 4;
        if (r6 < r7) goto L_0x0250;
    L_0x0244:
        r6 = 3;
        r6 = r0[r6];
        if (r6 == 0) goto L_0x0250;
    L_0x0249:
        r1 = 3;
        r1 = r0[r1];	 Catch:{ NumberFormatException -> 0x02ec }
        r1 = java.lang.Integer.parseInt(r1);	 Catch:{ NumberFormatException -> 0x02ec }
    L_0x0250:
        r3 = r0.length;	 Catch:{ NumberFormatException -> 0x03d9 }
        r6 = 5;
        if (r3 < r6) goto L_0x0260;
    L_0x0254:
        r3 = 3;
        if (r4 != r3) goto L_0x0260;
    L_0x0257:
        r3 = 4;
        r3 = r0[r3];	 Catch:{ NumberFormatException -> 0x03d9 }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x03d9 }
        r12.mNewReasonDataDenied = r3;	 Catch:{ NumberFormatException -> 0x03d9 }
    L_0x0260:
        r3 = r0.length;	 Catch:{ NumberFormatException -> 0x03d9 }
        r6 = 6;
        if (r3 < r6) goto L_0x026d;
    L_0x0264:
        r3 = 5;
        r3 = r0[r3];	 Catch:{ NumberFormatException -> 0x03d9 }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x03d9 }
        r12.mNewMaxDataCalls = r3;	 Catch:{ NumberFormatException -> 0x03d9 }
    L_0x026d:
        r3 = 1;
        r3 = r0[r3];
        if (r3 == 0) goto L_0x0284;
    L_0x0272:
        r3 = 1;
        r3 = r0[r3];	 Catch:{ NumberFormatException -> 0x03d9 }
        r3 = r3.length();	 Catch:{ NumberFormatException -> 0x03d9 }
        if (r3 <= 0) goto L_0x0284;
    L_0x027b:
        r3 = 1;
        r3 = r0[r3];	 Catch:{ NumberFormatException -> 0x03d9 }
        r6 = 16;
        r2 = java.lang.Integer.parseInt(r3, r6);	 Catch:{ NumberFormatException -> 0x03d9 }
    L_0x0284:
        r3 = 2;
        r3 = r0[r3];
        if (r3 == 0) goto L_0x03e6;
    L_0x0289:
        r3 = 2;
        r3 = r0[r3];	 Catch:{ NumberFormatException -> 0x03d9 }
        r3 = r3.length();	 Catch:{ NumberFormatException -> 0x03d9 }
        if (r3 <= 0) goto L_0x03e6;
    L_0x0292:
        r3 = 2;
        r0 = r0[r3];	 Catch:{ NumberFormatException -> 0x03d9 }
        r3 = 16;
        r0 = java.lang.Integer.parseInt(r0, r3);	 Catch:{ NumberFormatException -> 0x03d9 }
    L_0x029b:
        r3 = r12.regCodeToServiceState(r4);	 Catch:{ RuntimeException -> 0x01d1 }
        r5 = r12.mNewSS;	 Catch:{ RuntimeException -> 0x01d1 }
        r5.setDataRegState(r3);	 Catch:{ RuntimeException -> 0x01d1 }
        r5 = r12.regCodeIsRoaming(r4);	 Catch:{ RuntimeException -> 0x01d1 }
        r12.mDataRoaming = r5;	 Catch:{ RuntimeException -> 0x01d1 }
        r5 = r12.mNewSS;	 Catch:{ RuntimeException -> 0x01d1 }
        r5.setRilDataRadioTechnology(r1);	 Catch:{ RuntimeException -> 0x01d1 }
        r5 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x01d1 }
        r5.<init>();	 Catch:{ RuntimeException -> 0x01d1 }
        r6 = "handlPollStateResultMessage: GsmSST setDataRegState=";
        r5 = r5.append(r6);	 Catch:{ RuntimeException -> 0x01d1 }
        r3 = r5.append(r3);	 Catch:{ RuntimeException -> 0x01d1 }
        r5 = " regState=";
        r3 = r3.append(r5);	 Catch:{ RuntimeException -> 0x01d1 }
        r3 = r3.append(r4);	 Catch:{ RuntimeException -> 0x01d1 }
        r4 = " dataRadioTechnology=";
        r3 = r3.append(r4);	 Catch:{ RuntimeException -> 0x01d1 }
        r1 = r3.append(r1);	 Catch:{ RuntimeException -> 0x01d1 }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x01d1 }
        r12.log(r1);	 Catch:{ RuntimeException -> 0x01d1 }
        r12.mDataLAC = r2;	 Catch:{ RuntimeException -> 0x01d1 }
        r12.mDataCid = r0;	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = r12.getLacOrDataLac();	 Catch:{ RuntimeException -> 0x01d1 }
        r1 = r12.getCidOrDataCid();	 Catch:{ RuntimeException -> 0x01d1 }
        r2 = r12.mNewCellLoc;	 Catch:{ RuntimeException -> 0x01d1 }
        r2.setLacAndCid(r0, r1);	 Catch:{ RuntimeException -> 0x01d1 }
        goto L_0x0040;
    L_0x02ec:
        r0 = move-exception;
        r1 = r3;
    L_0x02ee:
        r3 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x01d1 }
        r3.<init>();	 Catch:{ RuntimeException -> 0x01d1 }
        r6 = "error parsing GprsRegistrationState: ";
        r3 = r3.append(r6);	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = r3.append(r0);	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x01d1 }
        r12.loge(r0);	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = r5;
        goto L_0x029b;
    L_0x0306:
        r0 = r14.result;	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = (java.lang.String[]) r0;	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = (java.lang.String[]) r0;	 Catch:{ RuntimeException -> 0x01d1 }
        if (r0 == 0) goto L_0x0040;
    L_0x030e:
        r1 = r0.length;	 Catch:{ RuntimeException -> 0x01d1 }
        r2 = 3;
        if (r1 < r2) goto L_0x0040;
    L_0x0312:
        r1 = r12.mUiccController;	 Catch:{ RuntimeException -> 0x01d1 }
        r2 = r12.getPhoneId();	 Catch:{ RuntimeException -> 0x01d1 }
        r1 = r1.getUiccCard(r2);	 Catch:{ RuntimeException -> 0x01d1 }
        if (r1 == 0) goto L_0x03d6;
    L_0x031e:
        r1 = r12.mUiccController;	 Catch:{ RuntimeException -> 0x01d1 }
        r2 = r12.getPhoneId();	 Catch:{ RuntimeException -> 0x01d1 }
        r1 = r1.getUiccCard(r2);	 Catch:{ RuntimeException -> 0x01d1 }
        r1 = r1.getOperatorBrandOverride();	 Catch:{ RuntimeException -> 0x01d1 }
    L_0x032c:
        if (r1 == 0) goto L_0x034e;
    L_0x032e:
        r2 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x01d1 }
        r2.<init>();	 Catch:{ RuntimeException -> 0x01d1 }
        r3 = "EVENT_POLL_STATE_OPERATOR: use brandOverride=";
        r2 = r2.append(r3);	 Catch:{ RuntimeException -> 0x01d1 }
        r2 = r2.append(r1);	 Catch:{ RuntimeException -> 0x01d1 }
        r2 = r2.toString();	 Catch:{ RuntimeException -> 0x01d1 }
        r12.log(r2);	 Catch:{ RuntimeException -> 0x01d1 }
        r2 = r12.mNewSS;	 Catch:{ RuntimeException -> 0x01d1 }
        r3 = 2;
        r0 = r0[r3];	 Catch:{ RuntimeException -> 0x01d1 }
        r2.setOperatorName(r1, r1, r0);	 Catch:{ RuntimeException -> 0x01d1 }
        goto L_0x0040;
    L_0x034e:
        r1 = r12.mSpnOverride;	 Catch:{ RuntimeException -> 0x01d1 }
        r2 = 2;
        r2 = r0[r2];	 Catch:{ RuntimeException -> 0x01d1 }
        r1 = r1.containsCarrier(r2);	 Catch:{ RuntimeException -> 0x01d1 }
        if (r1 == 0) goto L_0x038a;
    L_0x0359:
        r1 = "EVENT_POLL_STATE_OPERATOR: use spnOverride";
        r12.log(r1);	 Catch:{ RuntimeException -> 0x01d1 }
        r1 = r12.mSpnOverride;	 Catch:{ RuntimeException -> 0x01d1 }
        r2 = 2;
        r2 = r0[r2];	 Catch:{ RuntimeException -> 0x01d1 }
        r1 = r1.getSpn(r2);	 Catch:{ RuntimeException -> 0x01d1 }
    L_0x0367:
        r2 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x01d1 }
        r2.<init>();	 Catch:{ RuntimeException -> 0x01d1 }
        r3 = "EVENT_POLL_STATE_OPERATOR: ";
        r2 = r2.append(r3);	 Catch:{ RuntimeException -> 0x01d1 }
        r2 = r2.append(r1);	 Catch:{ RuntimeException -> 0x01d1 }
        r2 = r2.toString();	 Catch:{ RuntimeException -> 0x01d1 }
        r12.log(r2);	 Catch:{ RuntimeException -> 0x01d1 }
        r2 = r12.mNewSS;	 Catch:{ RuntimeException -> 0x01d1 }
        r3 = 1;
        r3 = r0[r3];	 Catch:{ RuntimeException -> 0x01d1 }
        r4 = 2;
        r0 = r0[r4];	 Catch:{ RuntimeException -> 0x01d1 }
        r2.setOperatorName(r1, r3, r0);	 Catch:{ RuntimeException -> 0x01d1 }
        goto L_0x0040;
    L_0x038a:
        r1 = "EVENT_POLL_STATE_OPERATOR: use value from ril";
        r12.log(r1);	 Catch:{ RuntimeException -> 0x01d1 }
        r1 = 0;
        r1 = r0[r1];
        goto L_0x0367;
    L_0x0393:
        r0 = r14.result;	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = (int[]) r0;	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = (int[]) r0;	 Catch:{ RuntimeException -> 0x01d1 }
        r2 = r12.mNewSS;	 Catch:{ RuntimeException -> 0x01d1 }
        r1 = 0;
        r1 = r0[r1];
        r3 = 1;
        if (r1 != r3) goto L_0x03c0;
    L_0x03a1:
        r1 = 1;
    L_0x03a2:
        r2.setIsManualSelection(r1);	 Catch:{ RuntimeException -> 0x01d1 }
        r1 = 0;
        r0 = r0[r1];
        r1 = 1;
        if (r0 != r1) goto L_0x0040;
    L_0x03ab:
        r0 = r12.mPhone;	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = r0.isManualNetSelAllowed();	 Catch:{ RuntimeException -> 0x01d1 }
        if (r0 != 0) goto L_0x0040;
    L_0x03b3:
        r0 = r12.mPhone;	 Catch:{ RuntimeException -> 0x01d1 }
        r1 = 0;
        r0.setNetworkSelectionModeAutomatic(r1);	 Catch:{ RuntimeException -> 0x01d1 }
        r0 = " Forcing Automatic Network Selection, manual selection is not allowed";
        r12.log(r0);	 Catch:{ RuntimeException -> 0x01d1 }
        goto L_0x0040;
    L_0x03c0:
        r1 = 0;
        goto L_0x03a2;
    L_0x03c2:
        r0 = 0;
        goto L_0x005a;
    L_0x03c5:
        r1 = r12.mPhone;
        r2 = r12.mNewSS;
        r2 = r2.getOperatorNumeric();
        r1 = r1.isMccMncMarkedAsRoaming(r2);
        if (r1 == 0) goto L_0x0086;
    L_0x03d3:
        r0 = 1;
        goto L_0x0086;
    L_0x03d6:
        r1 = 0;
        goto L_0x032c;
    L_0x03d9:
        r0 = move-exception;
        goto L_0x02ee;
    L_0x03dc:
        r4 = move-exception;
        r5 = r4;
        r6 = r3;
        goto L_0x01ec;
    L_0x03e1:
        r5 = move-exception;
        r6 = r3;
        r8 = r4;
        goto L_0x01ec;
    L_0x03e6:
        r0 = r5;
        goto L_0x029b;
    L_0x03e9:
        r0 = r5;
        r1 = r6;
        goto L_0x029b;
    L_0x03ed:
        r5 = r9;
        goto L_0x012b;
    L_0x03f0:
        r5 = r9;
        r3 = r10;
        r4 = r11;
        goto L_0x012b;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.gsm.GsmServiceStateTracker.handlePollStateResult(int, android.os.AsyncResult):void");
    }

    /* Access modifiers changed, original: protected */
    public void hangupAndPowerOff() {
        if (this.mPhone.isInCall()) {
            this.mPhone.mCT.mRingingCall.hangupIfAlive();
            this.mPhone.mCT.mBackgroundCall.hangupIfAlive();
            this.mPhone.mCT.mForegroundCall.hangupIfAlive();
        }
        this.mCi.setRadioPower(false, null);
    }

    public boolean isConcurrentVoiceAndDataAllowed() {
        return this.mSS.getRilVoiceRadioTechnology() != 16 || this.mSS.getCssIndicator() == 1;
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(LOG_TAG, "[GsmSST] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str) {
        Rlog.e(LOG_TAG, "[GsmSST] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            UiccCardApplication uiccCardApplication = getUiccCardApplication();
            if (this.mUiccApplcation != uiccCardApplication) {
                if (this.mUiccApplcation != null) {
                    log("Removing stale icc objects.");
                    this.mUiccApplcation.unregisterForReady(this);
                    if (this.mIccRecords != null) {
                        this.mIccRecords.unregisterForRecordsLoaded(this);
                    }
                    this.mIccRecords = null;
                    this.mUiccApplcation = null;
                }
                if (uiccCardApplication != null) {
                    log("New card found");
                    this.mUiccApplcation = uiccCardApplication;
                    this.mIccRecords = this.mUiccApplcation.getIccRecords();
                    this.mUiccApplcation.registerForReady(this, 17, null);
                    if (this.mIccRecords != null) {
                        this.mIccRecords.registerForRecordsLoaded(this, 16, null);
                    }
                }
            }
        }
    }

    public void pollState() {
        this.mPollingContext = new int[1];
        this.mPollingContext[0] = 0;
        switch (this.mCi.getRadioState()) {
            case RADIO_UNAVAILABLE:
                this.mNewSS.setStateOutOfService();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                this.mNitzUpdatedTime = false;
                pollStateDone();
                return;
            case RADIO_OFF:
                this.mNewSS.setStateOff();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                this.mNitzUpdatedTime = false;
                if (!(isIwlanFeatureAvailable() && 18 == this.mSS.getRilDataRadioTechnology())) {
                    pollStateDone();
                }
                if (!isIwlanFeatureAvailable()) {
                    return;
                }
                break;
        }
        int[] iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getOperator(obtainMessage(6, this.mPollingContext));
        iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getDataRegistrationState(obtainMessage(5, this.mPollingContext));
        iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getVoiceRegistrationState(obtainMessage(4, this.mPollingContext));
        iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getNetworkSelectionMode(obtainMessage(14, this.mPollingContext));
    }

    public void powerOffRadioSafely(DcTrackerBase dcTrackerBase) {
        synchronized (this) {
            if (!this.mPendingRadioPowerOffAfterDataOff) {
                int defaultDataSubId = SubscriptionManager.getDefaultDataSubId();
                if (!dcTrackerBase.isDisconnected() || (defaultDataSubId != this.mPhone.getSubId() && ((defaultDataSubId == this.mPhone.getSubId() || !ProxyController.getInstance().isDataDisconnected(defaultDataSubId)) && SubscriptionManager.isValidSubscriptionId(defaultDataSubId)))) {
                    if (this.mPhone.isInCall()) {
                        this.mPhone.mCT.mRingingCall.hangupIfAlive();
                        this.mPhone.mCT.mBackgroundCall.hangupIfAlive();
                        this.mPhone.mCT.mForegroundCall.hangupIfAlive();
                    }
                    dcTrackerBase.cleanUpAllConnections(Phone.REASON_RADIO_TURNED_OFF);
                    if (!(defaultDataSubId == this.mPhone.getSubId() || ProxyController.getInstance().isDataDisconnected(defaultDataSubId))) {
                        log("Data is active on DDS.  Wait for all data disconnect");
                        ProxyController.getInstance().registerForAllDataDisconnected(defaultDataSubId, this, CallFailCause.CDMA_DROP, null);
                        this.mPendingRadioPowerOffAfterDataOff = true;
                    }
                    Message obtain = Message.obtain(this);
                    obtain.what = 38;
                    int i = this.mPendingRadioPowerOffAfterDataOffTag + 1;
                    this.mPendingRadioPowerOffAfterDataOffTag = i;
                    obtain.arg1 = i;
                    if (sendMessageDelayed(obtain, 4000)) {
                        log("Wait upto 4s for data to disconnect, then turn off radio.");
                        this.mPendingRadioPowerOffAfterDataOff = true;
                    } else {
                        log("Cannot send delayed Msg, turn off radio right away.");
                        hangupAndPowerOff();
                        this.mPendingRadioPowerOffAfterDataOff = false;
                    }
                } else {
                    dcTrackerBase.cleanUpAllConnections(Phone.REASON_RADIO_TURNED_OFF);
                    log("Data disconnected, turn off radio right away.");
                    hangupAndPowerOff();
                }
            }
        }
    }

    public void setImsRegistrationState(boolean z) {
        if (this.mImsRegistrationOnOff && !z && this.mAlarmSwitch) {
            this.mImsRegistrationOnOff = z;
            ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.mRadioOffIntent);
            this.mAlarmSwitch = false;
            sendMessage(obtainMessage(45));
            return;
        }
        this.mImsRegistrationOnOff = z;
    }

    /* Access modifiers changed, original: protected */
    public void setPowerStateToDesired() {
        log("mDeviceShuttingDown = " + this.mDeviceShuttingDown);
        log("mDesiredPowerState = " + this.mDesiredPowerState);
        log("getRadioState = " + this.mCi.getRadioState());
        log("mPowerOffDelayNeed = " + this.mPowerOffDelayNeed);
        log("mAlarmSwitch = " + this.mAlarmSwitch);
        if (this.mAlarmSwitch) {
            log("mAlarmSwitch == true");
            ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.mRadioOffIntent);
            this.mAlarmSwitch = false;
        }
        if (this.mDesiredPowerState && this.mCi.getRadioState() == RadioState.RADIO_OFF) {
            if (SystemProperties.getBoolean("persist.radio.init_dun_profile", false)) {
                this.mPhone.resetDunProfiles();
                SystemProperties.set("persist.radio.init_dun_profile", "false");
            }
            this.mCi.setRadioPower(true, null);
        } else if (this.mDesiredPowerState || !this.mCi.getRadioState().isOn()) {
            if (this.mDeviceShuttingDown && this.mCi.getRadioState().isAvailable()) {
                this.mCi.requestShutdown(null);
            }
        } else if (!this.mPowerOffDelayNeed) {
            powerOffRadioSafely(this.mPhone.mDcTracker);
        } else if (!this.mImsRegistrationOnOff || this.mAlarmSwitch) {
            powerOffRadioSafely(this.mPhone.mDcTracker);
        } else {
            log("mImsRegistrationOnOff == true");
            Context context = this.mPhone.getContext();
            AlarmManager alarmManager = (AlarmManager) context.getSystemService("alarm");
            this.mRadioOffIntent = PendingIntent.getBroadcast(context, 0, new Intent("android.intent.action.ACTION_RADIO_OFF"), 0);
            this.mAlarmSwitch = true;
            log("Alarm setting");
            alarmManager.set(2, SystemClock.elapsedRealtime() + 3000, this.mRadioOffIntent);
        }
    }

    /* Access modifiers changed, original: protected */
    public void setRoamingType(ServiceState serviceState) {
        int i = serviceState.getVoiceRegState() == 0 ? 1 : 0;
        if (i != 0) {
            if (!serviceState.getVoiceRoaming()) {
                serviceState.setVoiceRoamingType(0);
            } else if (inSameCountry(serviceState.getVoiceOperatorNumeric())) {
                serviceState.setVoiceRoamingType(2);
            } else {
                serviceState.setVoiceRoamingType(3);
            }
        }
        int i2 = serviceState.getDataRegState() == 0 ? 1 : 0;
        int rilDataRadioTechnology = serviceState.getRilDataRadioTechnology();
        if (i2 == 0) {
            return;
        }
        if (!serviceState.getDataRoaming()) {
            serviceState.setDataRoamingType(0);
        } else if (!ServiceState.isGsm(rilDataRadioTechnology)) {
            serviceState.setDataRoamingType(1);
        } else if (i != 0) {
            serviceState.setDataRoamingType(serviceState.getVoiceRoamingType());
        } else {
            serviceState.setDataRoamingType(1);
        }
    }

    /* Access modifiers changed, original: protected */
    public void updateSpnDisplay() {
        boolean z;
        CharSequence charSequence;
        IccRecords iccRecords = this.mIccRecords;
        int displayRule = iccRecords != null ? iccRecords.getDisplayRule(this.mSS.getOperatorNumeric()) : 0;
        int combinedRegState = getCombinedRegState();
        String charSequence2;
        Object charSequence3;
        if (combinedRegState == 1 || combinedRegState == 2) {
            charSequence2 = this.mEmergencyOnly ? Resources.getSystem().getText(17040368).toString() : Resources.getSystem().getText(17040344).toString();
            log("updateSpnDisplay: radio is on but out of service, set plmn='" + charSequence2 + "'");
            z = true;
            charSequence3 = charSequence2;
        } else if (combinedRegState == 0) {
            String operatorAlphaLong = this.mSS.getOperatorAlphaLong();
            boolean z2 = !TextUtils.isEmpty(operatorAlphaLong) && (displayRule & 2) == 2;
            z = z2;
            charSequence3 = operatorAlphaLong;
        } else {
            charSequence2 = Resources.getSystem().getText(17040344).toString();
            log("updateSpnDisplay: radio is off w/ showPlmn=" + true + " plmn=" + charSequence2);
            z = true;
            charSequence3 = charSequence2;
        }
        CharSequence serviceProviderName = iccRecords != null ? iccRecords.getServiceProviderName() : "";
        boolean z3 = !TextUtils.isEmpty(serviceProviderName) && (displayRule & 1) == 1;
        if (this.mSS.getVoiceRegState() == 3 || (z && TextUtils.equals(serviceProviderName, charSequence3))) {
            serviceProviderName = null;
            z3 = false;
        }
        if (!(z == this.mCurShowPlmn && z3 == this.mCurShowSpn && TextUtils.equals(serviceProviderName, this.mCurSpn) && TextUtils.equals(charSequence3, this.mCurPlmn))) {
            log(String.format("updateSpnDisplay: changed sending intent rule=" + displayRule + " showPlmn='%b' plmn='%s' showSpn='%b' spn='%s'", new Object[]{Boolean.valueOf(z), charSequence3, Boolean.valueOf(z3), serviceProviderName}));
            Intent intent = new Intent("android.provider.Telephony.SPN_STRINGS_UPDATED");
            intent.addFlags(536870912);
            intent.putExtra("showSpn", z3);
            intent.putExtra("spn", serviceProviderName);
            intent.putExtra("showPlmn", z);
            intent.putExtra(CellBroadcasts.PLMN, charSequence3);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
            this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
        this.mCurShowSpn = z3;
        this.mCurShowPlmn = z;
        this.mCurSpn = serviceProviderName;
        this.mCurPlmn = charSequence3;
    }
}
