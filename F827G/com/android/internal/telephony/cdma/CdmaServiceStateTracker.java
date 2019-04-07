package com.android.internal.telephony.cdma;

import android.app.AlarmManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Telephony.CellBroadcasts;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.TimeUtils;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.HbpcdUtils;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data;

public class CdmaServiceStateTracker extends ServiceStateTracker {
    protected static final String DEFAULT_MNC = "00";
    protected static final String INVALID_MCC = "000";
    static final String LOG_TAG = "CdmaSST";
    private static final int MS_PER_HOUR = 3600000;
    private static final int NITZ_UPDATE_DIFF_DEFAULT = 2000;
    private static final int NITZ_UPDATE_SPACING_DEFAULT = 600000;
    private static final String UNACTIVATED_MIN2_VALUE = "000000";
    private static final String UNACTIVATED_MIN_VALUE = "1111110111";
    private static final String WAKELOCK_TAG = "ServiceStateTracker";
    private ContentObserver mAutoTimeObserver;
    private ContentObserver mAutoTimeZoneObserver;
    protected RegistrantList mCdmaForSubscriptionInfoReadyRegistrants;
    private boolean mCdmaRoaming;
    private CdmaSubscriptionSourceManager mCdmaSSM;
    CdmaCellLocation mCellLoc;
    private ContentResolver mCr;
    protected String mCurPlmn;
    private String mCurrentCarrier;
    int mCurrentOtaspMode;
    protected boolean mDataRoaming;
    private int mDefaultRoamingIndicator;
    protected boolean mGotCountryCode;
    protected HbpcdUtils mHbpcdUtils;
    protected int[] mHomeNetworkId;
    protected int[] mHomeSystemId;
    private boolean mIsEriTextLoaded;
    private boolean mIsInPrl;
    protected boolean mIsMinInfoReady;
    protected boolean mIsSubscriptionFromRuim;
    protected String mMdn;
    protected String mMin;
    protected boolean mNeedFixZone;
    CdmaCellLocation mNewCellLoc;
    private int mNitzUpdateDiff;
    private int mNitzUpdateSpacing;
    CDMAPhone mPhone;
    protected String mPrlVersion;
    private String mRegistrationDeniedReason;
    protected int mRegistrationState;
    private int mRoamingIndicator;
    long mSavedAtTime;
    long mSavedTime;
    String mSavedTimeZone;
    private WakeLock mWakeLock;
    private boolean mZoneDst;
    private int mZoneOffset;
    private long mZoneTime;

    public CdmaServiceStateTracker(CDMAPhone cDMAPhone) {
        this(cDMAPhone, new CellInfoCdma());
    }

    protected CdmaServiceStateTracker(CDMAPhone cDMAPhone, CellInfo cellInfo) {
        super(cDMAPhone, cDMAPhone.mCi, cellInfo);
        this.mCurrentOtaspMode = 0;
        this.mNitzUpdateSpacing = SystemProperties.getInt("ro.nitz_update_spacing", NITZ_UPDATE_SPACING_DEFAULT);
        this.mNitzUpdateDiff = SystemProperties.getInt("ro.nitz_update_diff", NITZ_UPDATE_DIFF_DEFAULT);
        this.mCdmaRoaming = false;
        this.mDataRoaming = false;
        this.mRoamingIndicator = 1;
        this.mDefaultRoamingIndicator = 1;
        this.mRegistrationState = -1;
        this.mCdmaForSubscriptionInfoReadyRegistrants = new RegistrantList();
        this.mNeedFixZone = false;
        this.mGotCountryCode = false;
        this.mCurPlmn = null;
        this.mHomeSystemId = null;
        this.mHomeNetworkId = null;
        this.mIsMinInfoReady = false;
        this.mIsEriTextLoaded = false;
        this.mIsSubscriptionFromRuim = false;
        this.mHbpcdUtils = null;
        this.mCurrentCarrier = null;
        this.mAutoTimeObserver = new ContentObserver(new Handler()) {
            public void onChange(boolean z) {
                CdmaServiceStateTracker.this.log("Auto time state changed");
                CdmaServiceStateTracker.this.revertToNitzTime();
            }
        };
        this.mAutoTimeZoneObserver = new ContentObserver(new Handler()) {
            public void onChange(boolean z) {
                CdmaServiceStateTracker.this.log("Auto time zone state changed");
                CdmaServiceStateTracker.this.revertToNitzTimeZone();
            }
        };
        this.mPhone = cDMAPhone;
        this.mCr = cDMAPhone.getContext().getContentResolver();
        this.mCellLoc = new CdmaCellLocation();
        this.mNewCellLoc = new CdmaCellLocation();
        this.mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(cDMAPhone.getContext(), this.mCi, this, 39, null);
        this.mIsSubscriptionFromRuim = this.mCdmaSSM.getCdmaSubscriptionSource() == 0;
        this.mWakeLock = ((PowerManager) cDMAPhone.getContext().getSystemService("power")).newWakeLock(1, WAKELOCK_TAG);
        this.mCi.registerForRadioStateChanged(this, 1, null);
        this.mCi.registerForVoiceNetworkStateChanged(this, 30, null);
        this.mCi.setOnNITZTime(this, 11, null);
        this.mCi.registerForCdmaPrlChanged(this, 40, null);
        cDMAPhone.registerForEriFileLoaded(this, 36, null);
        this.mCi.registerForCdmaOtaProvision(this, 37, null);
        this.mDesiredPowerState = Global.getInt(this.mCr, "airplane_mode_on", 0) <= 0;
        this.mCr.registerContentObserver(Global.getUriFor("auto_time"), true, this.mAutoTimeObserver);
        this.mCr.registerContentObserver(Global.getUriFor("auto_time_zone"), true, this.mAutoTimeZoneObserver);
        setSignalStrengthDefaultValues();
        this.mHbpcdUtils = new HbpcdUtils(cDMAPhone.getContext());
        cDMAPhone.notifyOtaspChanged(0);
    }

    private TimeZone findTimeZone(int i, boolean z, long j) {
        String[] availableIDs = TimeZone.getAvailableIDs(z ? i - MS_PER_HOUR : i);
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
            return Global.getInt(this.mCr, "auto_time") > 0;
        } catch (SettingNotFoundException e) {
            return true;
        }
    }

    private boolean getAutoTimeZone() {
        try {
            return Global.getInt(this.mCr, "auto_time_zone") > 0;
        } catch (SettingNotFoundException e) {
            return true;
        }
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

    private TimeZone getNitzTimeZone(int i, boolean z, long j) {
        TimeZone findTimeZone = findTimeZone(i, z, j);
        if (findTimeZone == null) {
            findTimeZone = findTimeZone(i, !z, j);
        }
        log("getNitzTimeZone returning " + (findTimeZone == null ? findTimeZone : findTimeZone.getID()));
        return findTimeZone;
    }

    private void getSubscriptionInfoAndStartPollingThreads() {
        this.mCi.getCDMASubscription(obtainMessage(34));
        pollState();
    }

    private void handleCdmaSubscriptionSource(int i) {
        log("Subscription Source : " + i);
        this.mIsSubscriptionFromRuim = i == 0;
        saveCdmaSubscriptionSource(i);
        if (this.mIsSubscriptionFromRuim) {
            registerForRuimEvents();
            return;
        }
        unregisterForRuimEvents();
        sendMessage(obtainMessage(35));
    }

    private boolean isHomeSid(int i) {
        if (this.mHomeSystemId == null) {
            return false;
        }
        for (int i2 : this.mHomeSystemId) {
            if (i == i2) {
                return true;
            }
        }
        return false;
    }

    private boolean isRoamIndForHomeSystem(String str) {
        String[] stringArray = this.mPhone.getContext().getResources().getStringArray(17236025);
        if (stringArray == null) {
            return false;
        }
        for (String equals : stringArray) {
            if (equals.equals(str)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRoamingBetweenOperators(boolean z, ServiceState serviceState) {
        String systemProperty = getSystemProperty("gsm.sim.operator.alpha", "empty");
        String voiceOperatorAlphaLong = serviceState.getVoiceOperatorAlphaLong();
        String voiceOperatorAlphaShort = serviceState.getVoiceOperatorAlphaShort();
        boolean z2 = voiceOperatorAlphaLong != null && systemProperty.equals(voiceOperatorAlphaLong);
        boolean z3 = voiceOperatorAlphaShort != null && systemProperty.equals(voiceOperatorAlphaShort);
        return (!z || z2 || z3) ? false : true;
    }

    private void queueNextSignalStrengthPoll() {
        if (!this.mDontPollSignalStrength) {
            Message obtainMessage = obtainMessage();
            obtainMessage.what = 10;
            sendMessageDelayed(obtainMessage, 20000);
        }
    }

    private void registerForRuimEvents() {
        log("registerForRuimEvents");
        if (this.mUiccApplcation != null) {
            this.mUiccApplcation.registerForReady(this, 26, null);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.registerForRecordsLoaded(this, 27, null);
        }
    }

    private void revertToNitzTime() {
        if (Global.getInt(this.mCr, "auto_time", 0) != 0) {
            log("revertToNitzTime: mSavedTime=" + this.mSavedTime + " mSavedAtTime=" + this.mSavedAtTime);
            if (this.mSavedTime != 0 && this.mSavedAtTime != 0) {
                setAndBroadcastNetworkSetTime(this.mSavedTime + (SystemClock.elapsedRealtime() - this.mSavedAtTime));
            }
        }
    }

    private void revertToNitzTimeZone() {
        if (Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time_zone", 0) != 0) {
            log("revertToNitzTimeZone: tz='" + this.mSavedTimeZone);
            if (this.mSavedTimeZone != null) {
                setAndBroadcastNetworkSetTimeZone(this.mSavedTimeZone);
            }
        }
    }

    private void saveCdmaSubscriptionSource(int i) {
        log("Storing cdma subscription source: " + i);
        Global.putInt(this.mPhone.getContext().getContentResolver(), "subscription_mode", i);
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
    }

    /* JADX WARNING: No exception handlers in catch block: Catch:{  } */
    /* JADX WARNING: Missing block: B:29:0x011a, code skipped:
            if (r18.mZoneDst != (r4 != 0)) goto L_0x011c;
     */
    private void setTimeFromNITZString(java.lang.String r19, long r20) {
        /*
        r18 = this;
        r6 = android.os.SystemClock.elapsedRealtime();
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "NITZ: ";
        r2 = r2.append(r3);
        r0 = r19;
        r2 = r2.append(r0);
        r3 = ",";
        r2 = r2.append(r3);
        r0 = r20;
        r2 = r2.append(r0);
        r3 = " start=";
        r2 = r2.append(r3);
        r2 = r2.append(r6);
        r3 = " delay=";
        r2 = r2.append(r3);
        r4 = r6 - r20;
        r2 = r2.append(r4);
        r2 = r2.toString();
        r0 = r18;
        r0.log(r2);
        r2 = "GMT";
        r2 = java.util.TimeZone.getTimeZone(r2);	 Catch:{ RuntimeException -> 0x022d }
        r5 = java.util.Calendar.getInstance(r2);	 Catch:{ RuntimeException -> 0x022d }
        r5.clear();	 Catch:{ RuntimeException -> 0x022d }
        r2 = 16;
        r3 = 0;
        r5.set(r2, r3);	 Catch:{ RuntimeException -> 0x022d }
        r2 = "[/:,+-]";
        r0 = r19;
        r8 = r0.split(r2);	 Catch:{ RuntimeException -> 0x022d }
        r2 = 1;
        r3 = 0;
        r3 = r8[r3];	 Catch:{ RuntimeException -> 0x022d }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ RuntimeException -> 0x022d }
        r3 = r3 + 2000;
        r5.set(r2, r3);	 Catch:{ RuntimeException -> 0x022d }
        r2 = 2;
        r3 = 1;
        r3 = r8[r3];	 Catch:{ RuntimeException -> 0x022d }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ RuntimeException -> 0x022d }
        r3 = r3 + -1;
        r5.set(r2, r3);	 Catch:{ RuntimeException -> 0x022d }
        r2 = 5;
        r3 = 2;
        r3 = r8[r3];	 Catch:{ RuntimeException -> 0x022d }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ RuntimeException -> 0x022d }
        r5.set(r2, r3);	 Catch:{ RuntimeException -> 0x022d }
        r2 = 10;
        r3 = 3;
        r3 = r8[r3];	 Catch:{ RuntimeException -> 0x022d }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ RuntimeException -> 0x022d }
        r5.set(r2, r3);	 Catch:{ RuntimeException -> 0x022d }
        r2 = 12;
        r3 = 4;
        r3 = r8[r3];	 Catch:{ RuntimeException -> 0x022d }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ RuntimeException -> 0x022d }
        r5.set(r2, r3);	 Catch:{ RuntimeException -> 0x022d }
        r2 = 13;
        r3 = 5;
        r3 = r8[r3];	 Catch:{ RuntimeException -> 0x022d }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ RuntimeException -> 0x022d }
        r5.set(r2, r3);	 Catch:{ RuntimeException -> 0x022d }
        r2 = 45;
        r0 = r19;
        r2 = r0.indexOf(r2);	 Catch:{ RuntimeException -> 0x022d }
        r3 = -1;
        if (r2 != r3) goto L_0x0420;
    L_0x00af:
        r2 = 1;
        r3 = r2;
    L_0x00b1:
        r2 = 6;
        r2 = r8[r2];	 Catch:{ RuntimeException -> 0x022d }
        r9 = java.lang.Integer.parseInt(r2);	 Catch:{ RuntimeException -> 0x022d }
        r2 = r8.length;	 Catch:{ RuntimeException -> 0x022d }
        r4 = 8;
        if (r2 < r4) goto L_0x0424;
    L_0x00bd:
        r2 = 7;
        r2 = r8[r2];	 Catch:{ RuntimeException -> 0x022d }
        r2 = java.lang.Integer.parseInt(r2);	 Catch:{ RuntimeException -> 0x022d }
        r4 = r2;
    L_0x00c5:
        if (r3 == 0) goto L_0x0428;
    L_0x00c7:
        r2 = 1;
    L_0x00c8:
        r2 = r2 * r9;
        r2 = r2 * 15;
        r2 = r2 * 60;
        r9 = r2 * 1000;
        r2 = 0;
        r3 = r8.length;	 Catch:{ RuntimeException -> 0x022d }
        r10 = 9;
        if (r3 < r10) goto L_0x00e5;
    L_0x00d5:
        r2 = 8;
        r2 = r8[r2];	 Catch:{ RuntimeException -> 0x022d }
        r3 = 33;
        r8 = 47;
        r2 = r2.replace(r3, r8);	 Catch:{ RuntimeException -> 0x022d }
        r2 = java.util.TimeZone.getTimeZone(r2);	 Catch:{ RuntimeException -> 0x022d }
    L_0x00e5:
        r3 = "gsm.operator.iso-country";
        r8 = "";
        r0 = r18;
        r8 = r0.getSystemProperty(r3, r8);	 Catch:{ RuntimeException -> 0x022d }
        if (r2 != 0) goto L_0x0437;
    L_0x00f1:
        r0 = r18;
        r3 = r0.mGotCountryCode;	 Catch:{ RuntimeException -> 0x022d }
        if (r3 == 0) goto L_0x0437;
    L_0x00f7:
        if (r8 == 0) goto L_0x01be;
    L_0x00f9:
        r2 = r8.length();	 Catch:{ RuntimeException -> 0x022d }
        if (r2 <= 0) goto L_0x01be;
    L_0x00ff:
        if (r4 == 0) goto L_0x042b;
    L_0x0101:
        r2 = 1;
    L_0x0102:
        r10 = r5.getTimeInMillis();	 Catch:{ RuntimeException -> 0x022d }
        r2 = android.util.TimeUtils.getTimeZone(r9, r2, r10, r8);	 Catch:{ RuntimeException -> 0x022d }
        r3 = r2;
    L_0x010b:
        if (r3 == 0) goto L_0x011c;
    L_0x010d:
        r0 = r18;
        r2 = r0.mZoneOffset;	 Catch:{ RuntimeException -> 0x022d }
        if (r2 != r9) goto L_0x011c;
    L_0x0113:
        r0 = r18;
        r10 = r0.mZoneDst;	 Catch:{ RuntimeException -> 0x022d }
        if (r4 == 0) goto L_0x0431;
    L_0x0119:
        r2 = 1;
    L_0x011a:
        if (r10 == r2) goto L_0x0134;
    L_0x011c:
        r2 = 1;
        r0 = r18;
        r0.mNeedFixZone = r2;	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r0.mZoneOffset = r9;	 Catch:{ RuntimeException -> 0x022d }
        if (r4 == 0) goto L_0x0434;
    L_0x0127:
        r2 = 1;
    L_0x0128:
        r0 = r18;
        r0.mZoneDst = r2;	 Catch:{ RuntimeException -> 0x022d }
        r10 = r5.getTimeInMillis();	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r0.mZoneTime = r10;	 Catch:{ RuntimeException -> 0x022d }
    L_0x0134:
        r2 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x022d }
        r2.<init>();	 Catch:{ RuntimeException -> 0x022d }
        r10 = "NITZ: tzOffset=";
        r2 = r2.append(r10);	 Catch:{ RuntimeException -> 0x022d }
        r2 = r2.append(r9);	 Catch:{ RuntimeException -> 0x022d }
        r9 = " dst=";
        r2 = r2.append(r9);	 Catch:{ RuntimeException -> 0x022d }
        r2 = r2.append(r4);	 Catch:{ RuntimeException -> 0x022d }
        r4 = " zone=";
        r4 = r2.append(r4);	 Catch:{ RuntimeException -> 0x022d }
        if (r3 == 0) goto L_0x01ce;
    L_0x0155:
        r2 = r3.getID();	 Catch:{ RuntimeException -> 0x022d }
    L_0x0159:
        r2 = r4.append(r2);	 Catch:{ RuntimeException -> 0x022d }
        r4 = " iso=";
        r2 = r2.append(r4);	 Catch:{ RuntimeException -> 0x022d }
        r2 = r2.append(r8);	 Catch:{ RuntimeException -> 0x022d }
        r4 = " mGotCountryCode=";
        r2 = r2.append(r4);	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r4 = r0.mGotCountryCode;	 Catch:{ RuntimeException -> 0x022d }
        r2 = r2.append(r4);	 Catch:{ RuntimeException -> 0x022d }
        r4 = " mNeedFixZone=";
        r2 = r2.append(r4);	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r4 = r0.mNeedFixZone;	 Catch:{ RuntimeException -> 0x022d }
        r2 = r2.append(r4);	 Catch:{ RuntimeException -> 0x022d }
        r2 = r2.toString();	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r0.log(r2);	 Catch:{ RuntimeException -> 0x022d }
        if (r3 == 0) goto L_0x01a6;
    L_0x018e:
        r2 = r18.getAutoTimeZone();	 Catch:{ RuntimeException -> 0x022d }
        if (r2 == 0) goto L_0x019d;
    L_0x0194:
        r2 = r3.getID();	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r0.setAndBroadcastNetworkSetTimeZone(r2);	 Catch:{ RuntimeException -> 0x022d }
    L_0x019d:
        r2 = r3.getID();	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r0.saveNitzTimeZone(r2);	 Catch:{ RuntimeException -> 0x022d }
    L_0x01a6:
        r2 = "gsm.ignore-nitz";
        r2 = android.os.SystemProperties.get(r2);	 Catch:{ RuntimeException -> 0x022d }
        if (r2 == 0) goto L_0x01d1;
    L_0x01ae:
        r3 = "yes";
        r2 = r2.equals(r3);	 Catch:{ RuntimeException -> 0x022d }
        if (r2 == 0) goto L_0x01d1;
    L_0x01b6:
        r2 = "NITZ: Not setting clock because gsm.ignore-nitz is set";
        r0 = r18;
        r0.log(r2);	 Catch:{ RuntimeException -> 0x022d }
    L_0x01bd:
        return;
    L_0x01be:
        if (r4 == 0) goto L_0x042e;
    L_0x01c0:
        r2 = 1;
    L_0x01c1:
        r10 = r5.getTimeInMillis();	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r2 = r0.getNitzTimeZone(r9, r2, r10);	 Catch:{ RuntimeException -> 0x022d }
        r3 = r2;
        goto L_0x010b;
    L_0x01ce:
        r2 = "NULL";
        goto L_0x0159;
    L_0x01d1:
        r0 = r18;
        r2 = r0.mWakeLock;	 Catch:{ all -> 0x03f0 }
        r2.acquire();	 Catch:{ all -> 0x03f0 }
        r2 = android.os.SystemClock.elapsedRealtime();	 Catch:{ all -> 0x03f0 }
        r2 = r2 - r20;
        r8 = 0;
        r4 = (r2 > r8 ? 1 : (r2 == r8 ? 0 : -1));
        if (r4 >= 0) goto L_0x0254;
    L_0x01e4:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x03f0 }
        r2.<init>();	 Catch:{ all -> 0x03f0 }
        r3 = "NITZ: not setting time, clock has rolled backwards since NITZ time was received, ";
        r2 = r2.append(r3);	 Catch:{ all -> 0x03f0 }
        r0 = r19;
        r2 = r2.append(r0);	 Catch:{ all -> 0x03f0 }
        r2 = r2.toString();	 Catch:{ all -> 0x03f0 }
        r0 = r18;
        r0.log(r2);	 Catch:{ all -> 0x03f0 }
        r2 = android.os.SystemClock.elapsedRealtime();	 Catch:{ RuntimeException -> 0x022d }
        r4 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x022d }
        r4.<init>();	 Catch:{ RuntimeException -> 0x022d }
        r5 = "NITZ: end=";
        r4 = r4.append(r5);	 Catch:{ RuntimeException -> 0x022d }
        r4 = r4.append(r2);	 Catch:{ RuntimeException -> 0x022d }
        r5 = " dur=";
        r4 = r4.append(r5);	 Catch:{ RuntimeException -> 0x022d }
        r2 = r2 - r6;
        r2 = r4.append(r2);	 Catch:{ RuntimeException -> 0x022d }
        r2 = r2.toString();	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r0.log(r2);	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r2 = r0.mWakeLock;	 Catch:{ RuntimeException -> 0x022d }
        r2.release();	 Catch:{ RuntimeException -> 0x022d }
        goto L_0x01bd;
    L_0x022d:
        r2 = move-exception;
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "NITZ: Parsing NITZ time ";
        r3 = r3.append(r4);
        r0 = r19;
        r3 = r3.append(r0);
        r4 = " ex=";
        r3 = r3.append(r4);
        r2 = r3.append(r2);
        r2 = r2.toString();
        r0 = r18;
        r0.loge(r2);
        goto L_0x01bd;
    L_0x0254:
        r8 = 2147483647; // 0x7fffffff float:NaN double:1.060997895E-314;
        r4 = (r2 > r8 ? 1 : (r2 == r8 ? 0 : -1));
        if (r4 <= 0) goto L_0x02ad;
    L_0x025b:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x03f0 }
        r4.<init>();	 Catch:{ all -> 0x03f0 }
        r5 = "NITZ: not setting time, processing has taken ";
        r4 = r4.append(r5);	 Catch:{ all -> 0x03f0 }
        r8 = 86400000; // 0x5265c00 float:7.82218E-36 double:4.2687272E-316;
        r2 = r2 / r8;
        r2 = r4.append(r2);	 Catch:{ all -> 0x03f0 }
        r3 = " days";
        r2 = r2.append(r3);	 Catch:{ all -> 0x03f0 }
        r2 = r2.toString();	 Catch:{ all -> 0x03f0 }
        r0 = r18;
        r0.log(r2);	 Catch:{ all -> 0x03f0 }
        r2 = android.os.SystemClock.elapsedRealtime();	 Catch:{ RuntimeException -> 0x022d }
        r4 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x022d }
        r4.<init>();	 Catch:{ RuntimeException -> 0x022d }
        r5 = "NITZ: end=";
        r4 = r4.append(r5);	 Catch:{ RuntimeException -> 0x022d }
        r4 = r4.append(r2);	 Catch:{ RuntimeException -> 0x022d }
        r5 = " dur=";
        r4 = r4.append(r5);	 Catch:{ RuntimeException -> 0x022d }
        r2 = r2 - r6;
        r2 = r4.append(r2);	 Catch:{ RuntimeException -> 0x022d }
        r2 = r2.toString();	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r0.log(r2);	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r2 = r0.mWakeLock;	 Catch:{ RuntimeException -> 0x022d }
        r2.release();	 Catch:{ RuntimeException -> 0x022d }
        goto L_0x01bd;
    L_0x02ad:
        r4 = (int) r2;
        r8 = 14;
        r5.add(r8, r4);	 Catch:{ all -> 0x03f0 }
        r4 = r18.getAutoTime();	 Catch:{ all -> 0x03f0 }
        if (r4 == 0) goto L_0x0344;
    L_0x02b9:
        r8 = r5.getTimeInMillis();	 Catch:{ all -> 0x03f0 }
        r10 = java.lang.System.currentTimeMillis();	 Catch:{ all -> 0x03f0 }
        r8 = r8 - r10;
        r10 = android.os.SystemClock.elapsedRealtime();	 Catch:{ all -> 0x03f0 }
        r0 = r18;
        r12 = r0.mSavedAtTime;	 Catch:{ all -> 0x03f0 }
        r10 = r10 - r12;
        r0 = r18;
        r4 = r0.mCr;	 Catch:{ all -> 0x03f0 }
        r12 = "nitz_update_spacing";
        r0 = r18;
        r13 = r0.mNitzUpdateSpacing;	 Catch:{ all -> 0x03f0 }
        r4 = android.provider.Settings.Global.getInt(r4, r12, r13);	 Catch:{ all -> 0x03f0 }
        r0 = r18;
        r12 = r0.mCr;	 Catch:{ all -> 0x03f0 }
        r13 = "nitz_update_diff";
        r0 = r18;
        r14 = r0.mNitzUpdateDiff;	 Catch:{ all -> 0x03f0 }
        r12 = android.provider.Settings.Global.getInt(r12, r13, r14);	 Catch:{ all -> 0x03f0 }
        r0 = r18;
        r14 = r0.mSavedAtTime;	 Catch:{ all -> 0x03f0 }
        r16 = 0;
        r13 = (r14 > r16 ? 1 : (r14 == r16 ? 0 : -1));
        if (r13 == 0) goto L_0x02ff;
    L_0x02f1:
        r14 = (long) r4;	 Catch:{ all -> 0x03f0 }
        r4 = (r10 > r14 ? 1 : (r10 == r14 ? 0 : -1));
        if (r4 > 0) goto L_0x02ff;
    L_0x02f6:
        r14 = java.lang.Math.abs(r8);	 Catch:{ all -> 0x03f0 }
        r12 = (long) r12;	 Catch:{ all -> 0x03f0 }
        r4 = (r14 > r12 ? 1 : (r14 == r12 ? 0 : -1));
        if (r4 <= 0) goto L_0x0398;
    L_0x02ff:
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x03f0 }
        r4.<init>();	 Catch:{ all -> 0x03f0 }
        r10 = "NITZ: Auto updating time of day to ";
        r4 = r4.append(r10);	 Catch:{ all -> 0x03f0 }
        r10 = r5.getTime();	 Catch:{ all -> 0x03f0 }
        r4 = r4.append(r10);	 Catch:{ all -> 0x03f0 }
        r10 = " NITZ receive delay=";
        r4 = r4.append(r10);	 Catch:{ all -> 0x03f0 }
        r2 = r4.append(r2);	 Catch:{ all -> 0x03f0 }
        r3 = "ms gained=";
        r2 = r2.append(r3);	 Catch:{ all -> 0x03f0 }
        r2 = r2.append(r8);	 Catch:{ all -> 0x03f0 }
        r3 = "ms from ";
        r2 = r2.append(r3);	 Catch:{ all -> 0x03f0 }
        r0 = r19;
        r2 = r2.append(r0);	 Catch:{ all -> 0x03f0 }
        r2 = r2.toString();	 Catch:{ all -> 0x03f0 }
        r0 = r18;
        r0.log(r2);	 Catch:{ all -> 0x03f0 }
        r2 = r5.getTimeInMillis();	 Catch:{ all -> 0x03f0 }
        r0 = r18;
        r0.setAndBroadcastNetworkSetTime(r2);	 Catch:{ all -> 0x03f0 }
    L_0x0344:
        r2 = "NITZ: update nitz time property";
        r0 = r18;
        r0.log(r2);	 Catch:{ all -> 0x03f0 }
        r2 = "gsm.nitz.time";
        r8 = r5.getTimeInMillis();	 Catch:{ all -> 0x03f0 }
        r3 = java.lang.String.valueOf(r8);	 Catch:{ all -> 0x03f0 }
        android.os.SystemProperties.set(r2, r3);	 Catch:{ all -> 0x03f0 }
        r2 = r5.getTimeInMillis();	 Catch:{ all -> 0x03f0 }
        r0 = r18;
        r0.mSavedTime = r2;	 Catch:{ all -> 0x03f0 }
        r2 = android.os.SystemClock.elapsedRealtime();	 Catch:{ all -> 0x03f0 }
        r0 = r18;
        r0.mSavedAtTime = r2;	 Catch:{ all -> 0x03f0 }
        r2 = android.os.SystemClock.elapsedRealtime();	 Catch:{ RuntimeException -> 0x022d }
        r4 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x022d }
        r4.<init>();	 Catch:{ RuntimeException -> 0x022d }
        r5 = "NITZ: end=";
        r4 = r4.append(r5);	 Catch:{ RuntimeException -> 0x022d }
        r4 = r4.append(r2);	 Catch:{ RuntimeException -> 0x022d }
        r5 = " dur=";
        r4 = r4.append(r5);	 Catch:{ RuntimeException -> 0x022d }
        r2 = r2 - r6;
        r2 = r4.append(r2);	 Catch:{ RuntimeException -> 0x022d }
        r2 = r2.toString();	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r0.log(r2);	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r2 = r0.mWakeLock;	 Catch:{ RuntimeException -> 0x022d }
        r2.release();	 Catch:{ RuntimeException -> 0x022d }
        goto L_0x01bd;
    L_0x0398:
        r2 = new java.lang.StringBuilder;	 Catch:{ all -> 0x03f0 }
        r2.<init>();	 Catch:{ all -> 0x03f0 }
        r3 = "NITZ: ignore, a previous update was ";
        r2 = r2.append(r3);	 Catch:{ all -> 0x03f0 }
        r2 = r2.append(r10);	 Catch:{ all -> 0x03f0 }
        r3 = "ms ago and gained=";
        r2 = r2.append(r3);	 Catch:{ all -> 0x03f0 }
        r2 = r2.append(r8);	 Catch:{ all -> 0x03f0 }
        r3 = "ms";
        r2 = r2.append(r3);	 Catch:{ all -> 0x03f0 }
        r2 = r2.toString();	 Catch:{ all -> 0x03f0 }
        r0 = r18;
        r0.log(r2);	 Catch:{ all -> 0x03f0 }
        r2 = android.os.SystemClock.elapsedRealtime();	 Catch:{ RuntimeException -> 0x022d }
        r4 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x022d }
        r4.<init>();	 Catch:{ RuntimeException -> 0x022d }
        r5 = "NITZ: end=";
        r4 = r4.append(r5);	 Catch:{ RuntimeException -> 0x022d }
        r4 = r4.append(r2);	 Catch:{ RuntimeException -> 0x022d }
        r5 = " dur=";
        r4 = r4.append(r5);	 Catch:{ RuntimeException -> 0x022d }
        r2 = r2 - r6;
        r2 = r4.append(r2);	 Catch:{ RuntimeException -> 0x022d }
        r2 = r2.toString();	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r0.log(r2);	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r2 = r0.mWakeLock;	 Catch:{ RuntimeException -> 0x022d }
        r2.release();	 Catch:{ RuntimeException -> 0x022d }
        goto L_0x01bd;
    L_0x03f0:
        r2 = move-exception;
        r4 = android.os.SystemClock.elapsedRealtime();	 Catch:{ RuntimeException -> 0x022d }
        r3 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x022d }
        r3.<init>();	 Catch:{ RuntimeException -> 0x022d }
        r8 = "NITZ: end=";
        r3 = r3.append(r8);	 Catch:{ RuntimeException -> 0x022d }
        r3 = r3.append(r4);	 Catch:{ RuntimeException -> 0x022d }
        r8 = " dur=";
        r3 = r3.append(r8);	 Catch:{ RuntimeException -> 0x022d }
        r4 = r4 - r6;
        r3 = r3.append(r4);	 Catch:{ RuntimeException -> 0x022d }
        r3 = r3.toString();	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r0.log(r3);	 Catch:{ RuntimeException -> 0x022d }
        r0 = r18;
        r3 = r0.mWakeLock;	 Catch:{ RuntimeException -> 0x022d }
        r3.release();	 Catch:{ RuntimeException -> 0x022d }
        throw r2;	 Catch:{ RuntimeException -> 0x022d }
    L_0x0420:
        r2 = 0;
        r3 = r2;
        goto L_0x00b1;
    L_0x0424:
        r2 = 0;
        r4 = r2;
        goto L_0x00c5;
    L_0x0428:
        r2 = -1;
        goto L_0x00c8;
    L_0x042b:
        r2 = 0;
        goto L_0x0102;
    L_0x042e:
        r2 = 0;
        goto L_0x01c1;
    L_0x0431:
        r2 = 0;
        goto L_0x011a;
    L_0x0434:
        r2 = 0;
        goto L_0x0128;
    L_0x0437:
        r3 = r2;
        goto L_0x010b;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cdma.CdmaServiceStateTracker.setTimeFromNITZString(java.lang.String, long):void");
    }

    private void unregisterForRuimEvents() {
        log("unregisterForRuimEvents");
        if (this.mUiccApplcation != null) {
            this.mUiccApplcation.unregisterForReady(this);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.unregisterForRecordsLoaded(this);
        }
    }

    public void dispose() {
        checkCorrectThread();
        log("ServiceStateTracker dispose");
        this.mCi.unregisterForRadioStateChanged(this);
        this.mCi.unregisterForVoiceNetworkStateChanged(this);
        this.mCi.unregisterForCdmaOtaProvision(this);
        this.mPhone.unregisterForEriFileLoaded(this);
        unregisterForRuimEvents();
        this.mCi.unSetOnNITZTime(this);
        this.mCr.unregisterContentObserver(this.mAutoTimeObserver);
        this.mCr.unregisterContentObserver(this.mAutoTimeZoneObserver);
        this.mCdmaSSM.dispose(this);
        this.mCi.unregisterForCdmaPrlChanged(this);
        super.dispose();
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("CdmaServiceStateTracker extends:");
        super.dump(fileDescriptor, printWriter, strArr);
        printWriter.flush();
        printWriter.println(" mPhone=" + this.mPhone);
        printWriter.println(" mSS=" + this.mSS);
        printWriter.println(" mNewSS=" + this.mNewSS);
        printWriter.println(" mCellLoc=" + this.mCellLoc);
        printWriter.println(" mNewCellLoc=" + this.mNewCellLoc);
        printWriter.println(" mCurrentOtaspMode=" + this.mCurrentOtaspMode);
        printWriter.println(" mRoamingIndicator=" + this.mRoamingIndicator);
        printWriter.println(" mIsInPrl=" + this.mIsInPrl);
        printWriter.println(" mDefaultRoamingIndicator=" + this.mDefaultRoamingIndicator);
        printWriter.println(" mRegistrationState=" + this.mRegistrationState);
        printWriter.println(" mNeedFixZone=" + this.mNeedFixZone);
        printWriter.flush();
        printWriter.println(" mZoneOffset=" + this.mZoneOffset);
        printWriter.println(" mZoneDst=" + this.mZoneDst);
        printWriter.println(" mZoneTime=" + this.mZoneTime);
        printWriter.println(" mGotCountryCode=" + this.mGotCountryCode);
        printWriter.println(" mSavedTimeZone=" + this.mSavedTimeZone);
        printWriter.println(" mSavedTime=" + this.mSavedTime);
        printWriter.println(" mSavedAtTime=" + this.mSavedAtTime);
        printWriter.println(" mWakeLock=" + this.mWakeLock);
        printWriter.println(" mCurPlmn=" + this.mCurPlmn);
        printWriter.println(" mMdn=" + this.mMdn);
        printWriter.println(" mHomeSystemId=" + this.mHomeSystemId);
        printWriter.println(" mHomeNetworkId=" + this.mHomeNetworkId);
        printWriter.println(" mMin=" + this.mMin);
        printWriter.println(" mPrlVersion=" + this.mPrlVersion);
        printWriter.println(" mIsMinInfoReady=" + this.mIsMinInfoReady);
        printWriter.println(" mIsEriTextLoaded=" + this.mIsEriTextLoaded);
        printWriter.println(" mIsSubscriptionFromRuim=" + this.mIsSubscriptionFromRuim);
        printWriter.println(" mCdmaSSM=" + this.mCdmaSSM);
        printWriter.println(" mRegistrationDeniedReason=" + this.mRegistrationDeniedReason);
        printWriter.println(" mCurrentCarrier=" + this.mCurrentCarrier);
        printWriter.flush();
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        log("CdmaServiceStateTracker finalized");
    }

    /* Access modifiers changed, original: protected */
    public void fixTimeZone(String str) {
        TimeZone timeZone;
        String str2 = SystemProperties.get("persist.sys.timezone");
        log("fixTimeZone zoneName='" + str2 + "' mZoneOffset=" + this.mZoneOffset + " mZoneDst=" + this.mZoneDst + " iso-cc='" + str + "' iso-cc-idx=" + Arrays.binarySearch(GMT_COUNTRY_CODES, str));
        if (this.mZoneOffset == 0 && !this.mZoneDst && str2 != null && str2.length() > 0 && Arrays.binarySearch(GMT_COUNTRY_CODES, str) < 0) {
            timeZone = TimeZone.getDefault();
            if (this.mNeedFixZone) {
                long currentTimeMillis = System.currentTimeMillis();
                long offset = (long) timeZone.getOffset(currentTimeMillis);
                log("fixTimeZone: tzOffset=" + offset + " ltod=" + TimeUtils.logTimeOfDay(currentTimeMillis));
                if (getAutoTime()) {
                    currentTimeMillis -= offset;
                    log("fixTimeZone: adj ltod=" + TimeUtils.logTimeOfDay(currentTimeMillis));
                    setAndBroadcastNetworkSetTime(currentTimeMillis);
                } else {
                    this.mSavedTime -= offset;
                    log("fixTimeZone: adj mSavedTime=" + this.mSavedTime);
                }
            }
            log("fixTimeZone: using default TimeZone");
        } else if (str.equals("")) {
            timeZone = getNitzTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime);
            log("fixTimeZone: using NITZ TimeZone");
        } else {
            timeZone = TimeUtils.getTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime, str);
            log("fixTimeZone: using getTimeZone(off, dst, time, iso)");
        }
        this.mNeedFixZone = false;
        if (timeZone != null) {
            log("fixTimeZone: zone != null zone.getID=" + timeZone.getID());
            if (getAutoTimeZone()) {
                setAndBroadcastNetworkSetTimeZone(timeZone.getID());
            } else {
                log("fixTimeZone: skip changing zone as getAutoTimeZone was false");
            }
            saveNitzTimeZone(timeZone.getID());
            return;
        }
        log("fixTimeZone: zone == null, do nothing for zone");
    }

    /* Access modifiers changed, original: protected */
    public String fixUnknownMcc(String str, int i) {
        int i2 = 1;
        if (i <= 0) {
            return str;
        }
        int rawOffset;
        boolean z;
        if (this.mSavedTimeZone != null) {
            rawOffset = TimeZone.getTimeZone(this.mSavedTimeZone).getRawOffset() / MS_PER_HOUR;
            z = true;
        } else {
            TimeZone nitzTimeZone = getNitzTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime);
            if (nitzTimeZone != null) {
                rawOffset = nitzTimeZone.getRawOffset() / MS_PER_HOUR;
                z = false;
            } else {
                z = false;
                rawOffset = 0;
            }
        }
        HbpcdUtils hbpcdUtils = this.mHbpcdUtils;
        if (!this.mZoneDst) {
            i2 = 0;
        }
        int mcc = hbpcdUtils.getMcc(i, rawOffset, i2, z);
        return mcc > 0 ? Integer.toString(mcc) + DEFAULT_MNC : str;
    }

    public String getCdmaMin() {
        return this.mMin;
    }

    public int getCurrentDataConnectionState() {
        return this.mSS.getDataRegState();
    }

    /* Access modifiers changed, original: protected */
    public String getHomeOperatorNumeric() {
        return SystemProperties.get("gsm.sim.operator.numeric", SystemProperties.get(CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC, ""));
    }

    /* Access modifiers changed, original: 0000 */
    public String getImsi() {
        String systemProperty = getSystemProperty("gsm.sim.operator.numeric", "");
        return (TextUtils.isEmpty(systemProperty) || getCdmaMin() == null) ? null : systemProperty + getCdmaMin();
    }

    public String getMdnNumber() {
        return this.mMdn;
    }

    /* Access modifiers changed, original: 0000 */
    public int getOtasp() {
        int i = 2;
        if (!(this.mIsSubscriptionFromRuim && this.mMin == null)) {
            if (this.mMin == null || this.mMin.length() < 6) {
                log("getOtasp: bad mMin='" + this.mMin + "'");
                i = 1;
            } else if (!(this.mMin.equals(UNACTIVATED_MIN_VALUE) || this.mMin.substring(0, 6).equals("000000") || SystemProperties.getBoolean("test_cdma_setup", false))) {
                i = 3;
            }
            log("getOtasp: state=" + i);
        }
        return i;
    }

    /* Access modifiers changed, original: protected */
    public Phone getPhone() {
        return this.mPhone;
    }

    public String getPrlVersion() {
        return this.mPrlVersion;
    }

    /* Access modifiers changed, original: protected */
    public UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(this.mPhone.getPhoneId(), 2);
    }

    public void handleMessage(Message message) {
        int parseInt;
        Object e;
        int i;
        int i2;
        int parseInt2;
        int i3 = -1;
        if (this.mPhone.mIsTheCurrentActivePhone) {
            AsyncResult asyncResult;
            String[] strArr;
            switch (message.what) {
                case 1:
                    if (this.mCi.getRadioState() == RadioState.RADIO_ON) {
                        handleCdmaSubscriptionSource(this.mCdmaSSM.getCdmaSubscriptionSource());
                        queueNextSignalStrengthPoll();
                    }
                    setPowerStateToDesired();
                    pollState();
                    return;
                case 3:
                    if (this.mCi.getRadioState().isOn()) {
                        onSignalStrengthResult((AsyncResult) message.obj, false);
                        queueNextSignalStrengthPoll();
                        return;
                    }
                    return;
                case 5:
                case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /*24*/:
                case 25:
                    handlePollStateResult(message.what, (AsyncResult) message.obj);
                    return;
                case 10:
                    this.mCi.getSignalStrength(obtainMessage(3));
                    return;
                case 11:
                    asyncResult = (AsyncResult) message.obj;
                    setTimeFromNITZString((String) ((Object[]) asyncResult.result)[0], ((Long) ((Object[]) asyncResult.result)[1]).longValue());
                    return;
                case 12:
                    asyncResult = (AsyncResult) message.obj;
                    this.mDontPollSignalStrength = true;
                    onSignalStrengthResult(asyncResult, false);
                    return;
                case 14:
                    log("EVENT_POLL_STATE_NETWORK_SELECTION_MODE");
                    asyncResult = (AsyncResult) message.obj;
                    if (asyncResult.exception != null || asyncResult.result == null) {
                        log("Unable to getNetworkSelectionMode");
                        return;
                    } else if (((int[]) asyncResult.result)[0] == 1) {
                        this.mPhone.setNetworkSelectionModeAutomatic(null);
                        return;
                    } else {
                        return;
                    }
                case 18:
                    if (((AsyncResult) message.obj).exception == null) {
                        this.mCi.getVoiceRegistrationState(obtainMessage(31, null));
                        return;
                    }
                    return;
                case 26:
                    this.mCi.setPreferredNetworkType(PhoneFactory.calculatePreferredNetworkType(this.mPhone.getContext(), this.mPhone.getPhoneId()), null);
                    log("Receive EVENT_RUIM_READY");
                    pollState();
                    if (!this.mPhone.getContext().getResources().getBoolean(17956953)) {
                        this.mCi.getNetworkSelectionMode(obtainMessage(14));
                    }
                    this.mPhone.prepareEri();
                    return;
                case OEM_RIL_RDE_Data.RDE_NV_OTKSL_I /*27*/:
                    log("EVENT_RUIM_RECORDS_LOADED: what=" + message.what);
                    updatePhoneObject();
                    RuimRecords ruimRecords = (RuimRecords) this.mIccRecords;
                    if (ruimRecords != null && ruimRecords.isProvisioned()) {
                        this.mMdn = ruimRecords.getMdn();
                        this.mMin = ruimRecords.getMin();
                        parseSidNid(ruimRecords.getSid(), ruimRecords.getNid());
                        this.mPrlVersion = ruimRecords.getPrlVersion();
                        this.mIsMinInfoReady = true;
                        updateOtaspState();
                    }
                    getSubscriptionInfoAndStartPollingThreads();
                    return;
                case 30:
                    pollState();
                    return;
                case 31:
                    asyncResult = (AsyncResult) message.obj;
                    if (asyncResult.exception == null) {
                        int i4;
                        strArr = (String[]) asyncResult.result;
                        if (strArr.length > 9) {
                            if (strArr[4] != null) {
                                try {
                                    parseInt = Integer.parseInt(strArr[4]);
                                } catch (NumberFormatException e2) {
                                    e = e2;
                                    parseInt = i3;
                                    i = Integer.MAX_VALUE;
                                    i2 = i3;
                                    i4 = Integer.MAX_VALUE;
                                }
                            } else {
                                parseInt = i3;
                            }
                            if (strArr[5] != null) {
                                try {
                                    i4 = Integer.parseInt(strArr[5]);
                                } catch (NumberFormatException e3) {
                                    e = e3;
                                    i = Integer.MAX_VALUE;
                                    i2 = i3;
                                    i4 = Integer.MAX_VALUE;
                                }
                            } else {
                                i4 = Integer.MAX_VALUE;
                            }
                            if (strArr[6] != null) {
                                try {
                                    i = Integer.parseInt(strArr[6]);
                                } catch (NumberFormatException e4) {
                                    e = e4;
                                    i = Integer.MAX_VALUE;
                                    i2 = i3;
                                }
                            } else {
                                i = Integer.MAX_VALUE;
                            }
                            if (i4 == 0 && r3 == 0) {
                                i = Integer.MAX_VALUE;
                                i4 = Integer.MAX_VALUE;
                            }
                            if (strArr[8] != null) {
                                try {
                                    i2 = Integer.parseInt(strArr[8]);
                                } catch (NumberFormatException e5) {
                                    e = e5;
                                    i2 = i3;
                                }
                            } else {
                                i2 = i3;
                            }
                            if (strArr[9] != null) {
                                try {
                                    parseInt2 = Integer.parseInt(strArr[9]);
                                } catch (NumberFormatException e6) {
                                    e = e6;
                                    loge("error parsing cell location data: " + e);
                                    parseInt2 = i3;
                                    this.mCellLoc.setCellLocationData(parseInt, i4, i, i2, parseInt2);
                                    this.mPhone.notifyLocationChanged();
                                    disableSingleLocationUpdate();
                                    return;
                                }
                            }
                            parseInt2 = i3;
                        } else {
                            parseInt2 = i3;
                            parseInt = i3;
                            i = Integer.MAX_VALUE;
                            i2 = i3;
                            i4 = Integer.MAX_VALUE;
                        }
                        this.mCellLoc.setCellLocationData(parseInt, i4, i, i2, parseInt2);
                        this.mPhone.notifyLocationChanged();
                    }
                    disableSingleLocationUpdate();
                    return;
                case 34:
                    asyncResult = (AsyncResult) message.obj;
                    if (asyncResult.exception == null) {
                        strArr = (String[]) asyncResult.result;
                        if (strArr == null || strArr.length < 5) {
                            log("GET_CDMA_SUBSCRIPTION: error parsing cdmaSubscription params num=" + strArr.length);
                            return;
                        }
                        if (strArr[0] != null) {
                            this.mMdn = strArr[0];
                        }
                        parseSidNid(strArr[1], strArr[2]);
                        if (strArr[3] != null) {
                            this.mMin = strArr[3];
                        }
                        if (strArr[4] != null) {
                            this.mPrlVersion = strArr[4];
                        }
                        log("GET_CDMA_SUBSCRIPTION: MDN=" + this.mMdn);
                        this.mIsMinInfoReady = true;
                        updateOtaspState();
                        if (this.mIsSubscriptionFromRuim || this.mIccRecords == null) {
                            log("GET_CDMA_SUBSCRIPTION either mIccRecords is null  or NV type device - not setting Imsi in mIccRecords");
                            return;
                        }
                        log("GET_CDMA_SUBSCRIPTION set imsi in mIccRecords");
                        this.mIccRecords.setImsi(getImsi());
                        return;
                    }
                    return;
                case 35:
                    updatePhoneObject();
                    this.mCi.getNetworkSelectionMode(obtainMessage(14));
                    getSubscriptionInfoAndStartPollingThreads();
                    return;
                case 36:
                    log("[CdmaServiceStateTracker] ERI file has been loaded, repolling.");
                    pollState();
                    return;
                case 37:
                    asyncResult = (AsyncResult) message.obj;
                    if (asyncResult.exception == null) {
                        int i5 = ((int[]) asyncResult.result)[0];
                        if (i5 == 8 || i5 == 10) {
                            log("EVENT_OTA_PROVISION_STATUS_CHANGE: Complete, Reload MDN");
                            this.mCi.getCDMASubscription(obtainMessage(34));
                            return;
                        }
                        return;
                    }
                    return;
                case 39:
                    handleCdmaSubscriptionSource(this.mCdmaSSM.getCdmaSubscriptionSource());
                    return;
                case 40:
                    asyncResult = (AsyncResult) message.obj;
                    if (asyncResult.exception == null) {
                        this.mPrlVersion = Integer.toString(((int[]) asyncResult.result)[0]);
                        return;
                    }
                    return;
                case OEM_RIL_RDE_Data.RDE_NV_CDMA_SO73_ENABLED_I /*45*/:
                    log("EVENT_CHANGE_IMS_STATE");
                    setPowerStateToDesired();
                    return;
                default:
                    super.handleMessage(message);
                    return;
            }
        }
        loge("Received message " + message + "[" + message.what + "]" + " while being destroyed. Ignoring.");
    }

    /* Access modifiers changed, original: protected */
    public void handlePollStateResult(int i, AsyncResult asyncResult) {
        boolean z = false;
        if (asyncResult.userObj == this.mPollingContext) {
            if (asyncResult.exception != null) {
                Error error = null;
                if (asyncResult.exception instanceof CommandException) {
                    error = ((CommandException) asyncResult.exception).getCommandError();
                }
                if (error == Error.RADIO_NOT_AVAILABLE) {
                    cancelPollState();
                    return;
                } else if (error != Error.OP_NOT_ALLOWED_BEFORE_REG_NW) {
                    loge("handlePollStateResult: RIL returned an error where it must succeed" + asyncResult.exception);
                }
            } else {
                try {
                    handlePollStateResultMessage(i, asyncResult);
                } catch (RuntimeException e) {
                    loge("handlePollStateResult: Exception while polling service state. Probably malformed RIL response." + e);
                }
            }
            int[] iArr = this.mPollingContext;
            iArr[0] = iArr[0] - 1;
            if (this.mPollingContext[0] == 0) {
                int i2;
                boolean z2 = !isSidsAllZeros() && isHomeSid(this.mNewSS.getSystemId());
                boolean z3 = (this.mCdmaRoaming || this.mDataRoaming) && !isRoamIndForHomeSystem(String.valueOf(this.mRoamingIndicator));
                this.mCdmaRoaming = z3;
                if (this.mIsSubscriptionFromRuim) {
                    this.mNewSS.setVoiceRoaming(isRoamingBetweenOperators(this.mNewSS.getVoiceRoaming(), this.mNewSS));
                }
                if (this.mNewSS.getVoiceRegState() == 0) {
                    i2 = 1;
                } else {
                    z3 = false;
                }
                int rilDataRadioTechnology = this.mNewSS.getRilDataRadioTechnology();
                if (i2 != 0 && ServiceState.isCdma(rilDataRadioTechnology)) {
                    this.mNewSS.setDataRoaming(this.mNewSS.getVoiceRoaming());
                }
                this.mNewSS.setCdmaDefaultRoamingIndicator(this.mDefaultRoamingIndicator);
                this.mNewSS.setCdmaRoamingIndicator(this.mRoamingIndicator);
                if (!TextUtils.isEmpty(this.mPrlVersion)) {
                    z = true;
                }
                if (!z || this.mNewSS.getRilVoiceRadioTechnology() == 0) {
                    log("Turn off roaming indicator if !isPrlLoaded or voice RAT is unknown");
                    this.mNewSS.setCdmaRoamingIndicator(1);
                } else if (!isSidsAllZeros()) {
                    if (!z2 && !this.mIsInPrl) {
                        this.mNewSS.setCdmaRoamingIndicator(this.mDefaultRoamingIndicator);
                    } else if (!z2 || this.mIsInPrl) {
                        if (!z2 && this.mIsInPrl) {
                            this.mNewSS.setCdmaRoamingIndicator(this.mRoamingIndicator);
                        } else if (this.mRoamingIndicator <= 2) {
                            this.mNewSS.setCdmaRoamingIndicator(1);
                        } else {
                            this.mNewSS.setCdmaRoamingIndicator(this.mRoamingIndicator);
                        }
                    } else if (isRatLte(this.mNewSS.getRilVoiceRadioTechnology())) {
                        log("Turn off roaming indicator as voice is LTE");
                        this.mNewSS.setCdmaRoamingIndicator(1);
                    } else {
                        this.mNewSS.setCdmaRoamingIndicator(2);
                    }
                }
                int cdmaRoamingIndicator = this.mNewSS.getCdmaRoamingIndicator();
                this.mNewSS.setCdmaEriIconIndex(this.mPhone.mEriManager.getCdmaEriIconIndex(cdmaRoamingIndicator, this.mDefaultRoamingIndicator));
                this.mNewSS.setCdmaEriIconMode(this.mPhone.mEriManager.getCdmaEriIconMode(cdmaRoamingIndicator, this.mDefaultRoamingIndicator));
                log("Set CDMA Roaming Indicator to: " + this.mNewSS.getCdmaRoamingIndicator() + ". voiceRoaming = " + this.mNewSS.getVoiceRoaming() + ". dataRoaming = " + this.mNewSS.getDataRoaming() + ", isPrlLoaded = " + z + ". namMatch = " + z2 + " , mIsInPrl = " + this.mIsInPrl + ", mRoamingIndicator = " + this.mRoamingIndicator + ", mDefaultRoamingIndicator= " + this.mDefaultRoamingIndicator);
                pollStateDone();
            }
        }
    }

    /* Access modifiers changed, original: protected */
    /* JADX WARNING: Removed duplicated region for block: B:111:0x0275  */
    /* JADX WARNING: Removed duplicated region for block: B:98:0x01e6  */
    /* JADX WARNING: Removed duplicated region for block: B:112:0x0278  */
    /* JADX WARNING: Removed duplicated region for block: B:101:0x01f8  */
    /* JADX WARNING: Removed duplicated region for block: B:174:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:104:0x0205  */
    /* JADX WARNING: Removed duplicated region for block: B:93:0x01a2  */
    /* JADX WARNING: Removed duplicated region for block: B:98:0x01e6  */
    /* JADX WARNING: Removed duplicated region for block: B:111:0x0275  */
    /* JADX WARNING: Removed duplicated region for block: B:101:0x01f8  */
    /* JADX WARNING: Removed duplicated region for block: B:112:0x0278  */
    /* JADX WARNING: Removed duplicated region for block: B:104:0x0205  */
    /* JADX WARNING: Removed duplicated region for block: B:174:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:93:0x01a2  */
    /* JADX WARNING: Removed duplicated region for block: B:111:0x0275  */
    /* JADX WARNING: Removed duplicated region for block: B:98:0x01e6  */
    /* JADX WARNING: Removed duplicated region for block: B:112:0x0278  */
    /* JADX WARNING: Removed duplicated region for block: B:101:0x01f8  */
    /* JADX WARNING: Removed duplicated region for block: B:174:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:104:0x0205  */
    /* JADX WARNING: Removed duplicated region for block: B:93:0x01a2  */
    /* JADX WARNING: Removed duplicated region for block: B:98:0x01e6  */
    /* JADX WARNING: Removed duplicated region for block: B:111:0x0275  */
    /* JADX WARNING: Removed duplicated region for block: B:101:0x01f8  */
    /* JADX WARNING: Removed duplicated region for block: B:112:0x0278  */
    /* JADX WARNING: Removed duplicated region for block: B:104:0x0205  */
    /* JADX WARNING: Removed duplicated region for block: B:174:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:93:0x01a2  */
    /* JADX WARNING: Removed duplicated region for block: B:111:0x0275  */
    /* JADX WARNING: Removed duplicated region for block: B:98:0x01e6  */
    /* JADX WARNING: Removed duplicated region for block: B:112:0x0278  */
    /* JADX WARNING: Removed duplicated region for block: B:101:0x01f8  */
    /* JADX WARNING: Removed duplicated region for block: B:174:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:104:0x0205  */
    /* JADX WARNING: Removed duplicated region for block: B:93:0x01a2  */
    /* JADX WARNING: Removed duplicated region for block: B:98:0x01e6  */
    /* JADX WARNING: Removed duplicated region for block: B:111:0x0275  */
    /* JADX WARNING: Removed duplicated region for block: B:101:0x01f8  */
    /* JADX WARNING: Removed duplicated region for block: B:112:0x0278  */
    /* JADX WARNING: Removed duplicated region for block: B:104:0x0205  */
    /* JADX WARNING: Removed duplicated region for block: B:174:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:93:0x01a2  */
    /* JADX WARNING: Removed duplicated region for block: B:111:0x0275  */
    /* JADX WARNING: Removed duplicated region for block: B:98:0x01e6  */
    /* JADX WARNING: Removed duplicated region for block: B:112:0x0278  */
    /* JADX WARNING: Removed duplicated region for block: B:101:0x01f8  */
    /* JADX WARNING: Removed duplicated region for block: B:174:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:104:0x0205  */
    /* JADX WARNING: Removed duplicated region for block: B:93:0x01a2  */
    /* JADX WARNING: Removed duplicated region for block: B:98:0x01e6  */
    /* JADX WARNING: Removed duplicated region for block: B:111:0x0275  */
    /* JADX WARNING: Removed duplicated region for block: B:101:0x01f8  */
    /* JADX WARNING: Removed duplicated region for block: B:112:0x0278  */
    /* JADX WARNING: Removed duplicated region for block: B:104:0x0205  */
    /* JADX WARNING: Removed duplicated region for block: B:174:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:93:0x01a2  */
    /* JADX WARNING: Removed duplicated region for block: B:111:0x0275  */
    /* JADX WARNING: Removed duplicated region for block: B:98:0x01e6  */
    /* JADX WARNING: Removed duplicated region for block: B:112:0x0278  */
    /* JADX WARNING: Removed duplicated region for block: B:101:0x01f8  */
    /* JADX WARNING: Removed duplicated region for block: B:174:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:104:0x0205  */
    public void handlePollStateResultMessage(int r24, android.os.AsyncResult r25) {
        /*
        r23 = this;
        switch(r24) {
            case 5: goto L_0x000b;
            case 24: goto L_0x00bc;
            case 25: goto L_0x028b;
            default: goto L_0x0003;
        };
    L_0x0003:
        r1 = "handlePollStateResultMessage: RIL response handle in wrong phone! Expected CDMA RIL request and get GSM RIL request.";
        r0 = r23;
        r0.loge(r1);
    L_0x000a:
        return;
    L_0x000b:
        r0 = r25;
        r1 = r0.result;
        r1 = (java.lang.String[]) r1;
        r1 = (java.lang.String[]) r1;
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "handlePollStateResultMessage: EVENT_POLL_STATE_GPRS states.length=";
        r2 = r2.append(r3);
        r3 = r1.length;
        r2 = r2.append(r3);
        r3 = " states=";
        r2 = r2.append(r3);
        r2 = r2.append(r1);
        r2 = r2.toString();
        r0 = r23;
        r0.log(r2);
        r3 = 4;
        r2 = 0;
        r4 = r1.length;
        if (r4 <= 0) goto L_0x03ed;
    L_0x003b:
        r4 = 0;
        r4 = r1[r4];	 Catch:{ NumberFormatException -> 0x00a1 }
        r3 = java.lang.Integer.parseInt(r4);	 Catch:{ NumberFormatException -> 0x00a1 }
        r4 = r1.length;	 Catch:{ NumberFormatException -> 0x00a1 }
        r5 = 4;
        if (r4 < r5) goto L_0x03ed;
    L_0x0046:
        r4 = 3;
        r4 = r1[r4];
        if (r4 == 0) goto L_0x03ed;
    L_0x004b:
        r4 = 3;
        r1 = r1[r4];	 Catch:{ NumberFormatException -> 0x00a1 }
        r1 = java.lang.Integer.parseInt(r1);	 Catch:{ NumberFormatException -> 0x00a1 }
    L_0x0052:
        r0 = r23;
        r2 = r0.regCodeToServiceState(r3);
        r0 = r23;
        r4 = r0.mNewSS;
        r4.setDataRegState(r2);
        r0 = r23;
        r4 = r0.mNewSS;
        r4.setRilDataRadioTechnology(r1);
        r0 = r23;
        r4 = r0.mNewSS;
        r0 = r23;
        r5 = r0.regCodeIsRoaming(r3);
        r4.setDataRoaming(r5);
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "handlPollStateResultMessage: cdma setDataRegState=";
        r4 = r4.append(r5);
        r2 = r4.append(r2);
        r4 = " regState=";
        r2 = r2.append(r4);
        r2 = r2.append(r3);
        r3 = " dataRadioTechnology=";
        r2 = r2.append(r3);
        r1 = r2.append(r1);
        r1 = r1.toString();
        r0 = r23;
        r0.log(r1);
        goto L_0x000a;
    L_0x00a1:
        r1 = move-exception;
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "handlePollStateResultMessage: error parsing GprsRegistrationState: ";
        r4 = r4.append(r5);
        r1 = r4.append(r1);
        r1 = r1.toString();
        r0 = r23;
        r0.loge(r1);
        r1 = r2;
        goto L_0x0052;
    L_0x00bc:
        r0 = r25;
        r1 = r0.result;
        r1 = (java.lang.String[]) r1;
        r1 = (java.lang.String[]) r1;
        r2 = 4;
        r3 = -1;
        r4 = -1;
        r5 = 2147483647; // 0x7fffffff float:NaN double:1.060997895E-314;
        r17 = 2147483647; // 0x7fffffff float:NaN double:1.060997895E-314;
        r10 = 0;
        r6 = 0;
        r18 = 0;
        r11 = 0;
        r19 = 0;
        r12 = 0;
        r21 = 1;
        r13 = 1;
        r22 = 0;
        r14 = 0;
        r20 = 1;
        r15 = 1;
        r16 = 0;
        r7 = r1.length;
        r8 = 14;
        if (r7 < r8) goto L_0x0252;
    L_0x00e5:
        r7 = 0;
        r7 = r1[r7];
        if (r7 == 0) goto L_0x03ea;
    L_0x00ea:
        r7 = 0;
        r7 = r1[r7];	 Catch:{ NumberFormatException -> 0x0223 }
        r2 = java.lang.Integer.parseInt(r7);	 Catch:{ NumberFormatException -> 0x0223 }
        r7 = r2;
    L_0x00f2:
        r2 = 3;
        r2 = r1[r2];
        if (r2 == 0) goto L_0x03e7;
    L_0x00f7:
        r2 = 3;
        r2 = r1[r2];	 Catch:{ NumberFormatException -> 0x033a }
        r2 = java.lang.Integer.parseInt(r2);	 Catch:{ NumberFormatException -> 0x033a }
        r8 = r2;
    L_0x00ff:
        r2 = 4;
        r2 = r1[r2];
        if (r2 == 0) goto L_0x03e4;
    L_0x0104:
        r2 = 4;
        r2 = r1[r2];	 Catch:{ NumberFormatException -> 0x0349 }
        r2 = java.lang.Integer.parseInt(r2);	 Catch:{ NumberFormatException -> 0x0349 }
    L_0x010b:
        r3 = 5;
        r3 = r1[r3];
        if (r3 == 0) goto L_0x03e1;
    L_0x0110:
        r3 = 5;
        r3 = r1[r3];	 Catch:{ NumberFormatException -> 0x0357 }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x0357 }
    L_0x0117:
        r4 = 6;
        r4 = r1[r4];
        if (r4 == 0) goto L_0x03dd;
    L_0x011c:
        r4 = 6;
        r4 = r1[r4];	 Catch:{ NumberFormatException -> 0x0365 }
        r4 = java.lang.Integer.parseInt(r4);	 Catch:{ NumberFormatException -> 0x0365 }
    L_0x0123:
        if (r3 != 0) goto L_0x03c3;
    L_0x0125:
        if (r4 != 0) goto L_0x03c3;
    L_0x0127:
        r5 = 2147483647; // 0x7fffffff float:NaN double:1.060997895E-314;
        r3 = 2147483647; // 0x7fffffff float:NaN double:1.060997895E-314;
        r4 = r3;
        r9 = r5;
    L_0x012f:
        r3 = 7;
        r3 = r1[r3];
        if (r3 == 0) goto L_0x03da;
    L_0x0134:
        r3 = 7;
        r3 = r1[r3];	 Catch:{ NumberFormatException -> 0x0373 }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x0373 }
        r10 = r3;
    L_0x013c:
        r3 = 8;
        r3 = r1[r3];
        if (r3 == 0) goto L_0x03d7;
    L_0x0142:
        r3 = 8;
        r3 = r1[r3];	 Catch:{ NumberFormatException -> 0x0373 }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x0373 }
        r5 = r3;
    L_0x014b:
        r3 = 9;
        r3 = r1[r3];
        if (r3 == 0) goto L_0x03d4;
    L_0x0151:
        r3 = 9;
        r3 = r1[r3];	 Catch:{ NumberFormatException -> 0x0382 }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x0382 }
        r6 = r3;
    L_0x015a:
        r3 = 10;
        r3 = r1[r3];
        if (r3 == 0) goto L_0x03d1;
    L_0x0160:
        r3 = 10;
        r3 = r1[r3];	 Catch:{ NumberFormatException -> 0x0393 }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x0393 }
        r11 = r3;
    L_0x0169:
        r3 = 11;
        r3 = r1[r3];
        if (r3 == 0) goto L_0x03ce;
    L_0x016f:
        r3 = 11;
        r3 = r1[r3];	 Catch:{ NumberFormatException -> 0x03a2 }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x03a2 }
        r12 = r3;
    L_0x0178:
        r3 = 12;
        r3 = r1[r3];
        if (r3 == 0) goto L_0x03cb;
    L_0x017e:
        r3 = 12;
        r3 = r1[r3];	 Catch:{ NumberFormatException -> 0x03af }
        r3 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x03af }
        r13 = r3;
    L_0x0187:
        r3 = 13;
        r3 = r1[r3];
        if (r3 == 0) goto L_0x03c6;
    L_0x018d:
        r3 = 13;
        r3 = r1[r3];	 Catch:{ NumberFormatException -> 0x03ba }
        r14 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x03ba }
        r3 = r9;
    L_0x0196:
        r0 = r23;
        r0.mRegistrationState = r7;
        r0 = r23;
        r9 = r0.regCodeIsRoaming(r7);
        if (r9 == 0) goto L_0x0272;
    L_0x01a2:
        r9 = 10;
        r1 = r1[r9];
        r0 = r23;
        r1 = r0.isRoamIndForHomeSystem(r1);
        if (r1 != 0) goto L_0x0272;
    L_0x01ae:
        r1 = 1;
    L_0x01af:
        r0 = r23;
        r0.mCdmaRoaming = r1;
        r0 = r23;
        r1 = r0.mNewSS;
        r0 = r23;
        r9 = r0.mCdmaRoaming;
        r1.setVoiceRoaming(r9);
        r0 = r23;
        r1 = r0.mNewSS;
        r0 = r23;
        r7 = r0.regCodeToServiceState(r7);
        r1.setState(r7);
        r0 = r23;
        r1 = r0.mNewSS;
        r1.setRilVoiceRadioTechnology(r8);
        r0 = r23;
        r1 = r0.mNewSS;
        r1.setCssIndicator(r10);
        r0 = r23;
        r1 = r0.mNewSS;
        r1.setSystemAndNetworkId(r5, r6);
        r0 = r23;
        r0.mRoamingIndicator = r11;
        if (r12 != 0) goto L_0x0275;
    L_0x01e6:
        r1 = 0;
    L_0x01e7:
        r0 = r23;
        r0.mIsInPrl = r1;
        r0 = r23;
        r0.mDefaultRoamingIndicator = r13;
        r0 = r23;
        r1 = r0.mNewCellLoc;
        r1.setCellLocationData(r2, r3, r4, r5, r6);
        if (r14 != 0) goto L_0x0278;
    L_0x01f8:
        r1 = "General";
        r0 = r23;
        r0.mRegistrationDeniedReason = r1;
    L_0x01fe:
        r0 = r23;
        r1 = r0.mRegistrationState;
        r2 = 3;
        if (r1 != r2) goto L_0x000a;
    L_0x0205:
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "Registration denied, ";
        r1 = r1.append(r2);
        r0 = r23;
        r2 = r0.mRegistrationDeniedReason;
        r1 = r1.append(r2);
        r1 = r1.toString();
        r0 = r23;
        r0.log(r1);
        goto L_0x000a;
    L_0x0223:
        r14 = move-exception;
        r15 = r4;
        r6 = r19;
        r13 = r20;
        r8 = r3;
        r7 = r2;
        r11 = r21;
        r12 = r22;
        r9 = r5;
    L_0x0230:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "EVENT_POLL_STATE_REGISTRATION_CDMA: error parsing: ";
        r2 = r2.append(r3);
        r2 = r2.append(r14);
        r2 = r2.toString();
        r0 = r23;
        r0.loge(r2);
        r14 = r16;
        r2 = r15;
        r4 = r17;
        r5 = r18;
        r3 = r9;
        goto L_0x0196;
    L_0x0252:
        r2 = new java.lang.RuntimeException;
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "Warning! Wrong number of parameters returned from RIL_REQUEST_REGISTRATION_STATE: expected 14 or more strings and got ";
        r3 = r3.append(r4);
        r1 = r1.length;
        r1 = r3.append(r1);
        r3 = " strings";
        r1 = r1.append(r3);
        r1 = r1.toString();
        r2.<init>(r1);
        throw r2;
    L_0x0272:
        r1 = 0;
        goto L_0x01af;
    L_0x0275:
        r1 = 1;
        goto L_0x01e7;
    L_0x0278:
        r1 = 1;
        if (r14 != r1) goto L_0x0283;
    L_0x027b:
        r1 = "Authentication Failure";
        r0 = r23;
        r0.mRegistrationDeniedReason = r1;
        goto L_0x01fe;
    L_0x0283:
        r1 = "";
        r0 = r23;
        r0.mRegistrationDeniedReason = r1;
        goto L_0x01fe;
    L_0x028b:
        r0 = r25;
        r1 = r0.result;
        r1 = (java.lang.String[]) r1;
        r1 = (java.lang.String[]) r1;
        if (r1 == 0) goto L_0x0331;
    L_0x0295:
        r2 = r1.length;
        r3 = 3;
        if (r2 < r3) goto L_0x0331;
    L_0x0299:
        r2 = 2;
        r2 = r1[r2];
        if (r2 == 0) goto L_0x02b3;
    L_0x029e:
        r2 = 2;
        r2 = r1[r2];
        r2 = r2.length();
        r3 = 5;
        if (r2 < r3) goto L_0x02b3;
    L_0x02a8:
        r2 = "00000";
        r3 = 2;
        r3 = r1[r3];
        r2 = r2.equals(r3);
        if (r2 == 0) goto L_0x02d9;
    L_0x02b3:
        r2 = 2;
        r3 = "ro.cdma.home.operator.numeric";
        r4 = "00000";
        r3 = android.os.SystemProperties.get(r3, r4);
        r1[r2] = r3;
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "RIL_REQUEST_OPERATOR.response[2], the numeric,  is bad. Using SystemProperties 'ro.cdma.home.operator.numeric'= ";
        r2 = r2.append(r3);
        r3 = 2;
        r3 = r1[r3];
        r2 = r2.append(r3);
        r2 = r2.toString();
        r0 = r23;
        r0.log(r2);
    L_0x02d9:
        r0 = r23;
        r2 = r0.mIsSubscriptionFromRuim;
        if (r2 != 0) goto L_0x02f1;
    L_0x02df:
        r0 = r23;
        r2 = r0.mNewSS;
        r3 = 0;
        r3 = r1[r3];
        r4 = 1;
        r4 = r1[r4];
        r5 = 2;
        r1 = r1[r5];
        r2.setOperatorName(r3, r4, r1);
        goto L_0x000a;
    L_0x02f1:
        r0 = r23;
        r2 = r0.mUiccController;
        r3 = r23.getPhoneId();
        r2 = r2.getUiccCard(r3);
        if (r2 == 0) goto L_0x031d;
    L_0x02ff:
        r0 = r23;
        r2 = r0.mUiccController;
        r3 = r23.getPhoneId();
        r2 = r2.getUiccCard(r3);
        r2 = r2.getOperatorBrandOverride();
    L_0x030f:
        if (r2 == 0) goto L_0x031f;
    L_0x0311:
        r0 = r23;
        r3 = r0.mNewSS;
        r4 = 2;
        r1 = r1[r4];
        r3.setOperatorName(r2, r2, r1);
        goto L_0x000a;
    L_0x031d:
        r2 = 0;
        goto L_0x030f;
    L_0x031f:
        r0 = r23;
        r2 = r0.mNewSS;
        r3 = 0;
        r3 = r1[r3];
        r4 = 1;
        r4 = r1[r4];
        r5 = 2;
        r1 = r1[r5];
        r2.setOperatorName(r3, r4, r1);
        goto L_0x000a;
    L_0x0331:
        r1 = "EVENT_POLL_STATE_OPERATOR_CDMA: error parsing opNames";
        r0 = r23;
        r0.log(r1);
        goto L_0x000a;
    L_0x033a:
        r2 = move-exception;
        r14 = r2;
        r15 = r4;
        r6 = r19;
        r13 = r20;
        r8 = r3;
        r11 = r21;
        r12 = r22;
        r9 = r5;
        goto L_0x0230;
    L_0x0349:
        r2 = move-exception;
        r14 = r2;
        r15 = r4;
        r6 = r19;
        r13 = r20;
        r11 = r21;
        r12 = r22;
        r9 = r5;
        goto L_0x0230;
    L_0x0357:
        r3 = move-exception;
        r14 = r3;
        r15 = r2;
        r6 = r19;
        r13 = r20;
        r11 = r21;
        r12 = r22;
        r9 = r5;
        goto L_0x0230;
    L_0x0365:
        r4 = move-exception;
        r14 = r4;
        r15 = r2;
        r6 = r19;
        r13 = r20;
        r11 = r21;
        r12 = r22;
        r9 = r3;
        goto L_0x0230;
    L_0x0373:
        r3 = move-exception;
        r14 = r3;
        r15 = r2;
        r17 = r4;
        r6 = r19;
        r13 = r20;
        r11 = r21;
        r12 = r22;
        goto L_0x0230;
    L_0x0382:
        r3 = move-exception;
        r14 = r3;
        r15 = r2;
        r17 = r4;
        r18 = r5;
        r6 = r19;
        r13 = r20;
        r11 = r21;
        r12 = r22;
        goto L_0x0230;
    L_0x0393:
        r3 = move-exception;
        r14 = r3;
        r15 = r2;
        r17 = r4;
        r18 = r5;
        r13 = r20;
        r11 = r21;
        r12 = r22;
        goto L_0x0230;
    L_0x03a2:
        r3 = move-exception;
        r14 = r3;
        r15 = r2;
        r17 = r4;
        r18 = r5;
        r13 = r20;
        r12 = r22;
        goto L_0x0230;
    L_0x03af:
        r3 = move-exception;
        r14 = r3;
        r15 = r2;
        r17 = r4;
        r18 = r5;
        r13 = r20;
        goto L_0x0230;
    L_0x03ba:
        r3 = move-exception;
        r14 = r3;
        r15 = r2;
        r17 = r4;
        r18 = r5;
        goto L_0x0230;
    L_0x03c3:
        r9 = r3;
        goto L_0x012f;
    L_0x03c6:
        r14 = r16;
        r3 = r9;
        goto L_0x0196;
    L_0x03cb:
        r13 = r15;
        goto L_0x0187;
    L_0x03ce:
        r12 = r14;
        goto L_0x0178;
    L_0x03d1:
        r11 = r13;
        goto L_0x0169;
    L_0x03d4:
        r6 = r12;
        goto L_0x015a;
    L_0x03d7:
        r5 = r11;
        goto L_0x014b;
    L_0x03da:
        r10 = r6;
        goto L_0x013c;
    L_0x03dd:
        r4 = r17;
        goto L_0x0123;
    L_0x03e1:
        r3 = r5;
        goto L_0x0117;
    L_0x03e4:
        r2 = r4;
        goto L_0x010b;
    L_0x03e7:
        r8 = r3;
        goto L_0x00ff;
    L_0x03ea:
        r7 = r2;
        goto L_0x00f2;
    L_0x03ed:
        r1 = r2;
        goto L_0x0052;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cdma.CdmaServiceStateTracker.handlePollStateResultMessage(int, android.os.AsyncResult):void");
    }

    /* Access modifiers changed, original: protected */
    public void hangupAndPowerOff() {
        this.mPhone.mCT.mRingingCall.hangupIfAlive();
        this.mPhone.mCT.mBackgroundCall.hangupIfAlive();
        this.mPhone.mCT.mForegroundCall.hangupIfAlive();
        this.mCi.setRadioPower(false, null);
    }

    public boolean isConcurrentVoiceAndDataAllowed() {
        return false;
    }

    /* Access modifiers changed, original: protected */
    public boolean isInvalidOperatorNumeric(String str) {
        return str == null || str.length() < 5 || str.startsWith(INVALID_MCC);
    }

    public boolean isMinInfoReady() {
        return this.mIsMinInfoReady;
    }

    /* Access modifiers changed, original: protected */
    public boolean isSidsAllZeros() {
        if (this.mHomeSystemId != null) {
            for (int i : this.mHomeSystemId) {
                if (i != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(LOG_TAG, "[CdmaSST] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str) {
        Rlog.e(LOG_TAG, "[CdmaSST] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            UiccCardApplication uiccCardApplication = getUiccCardApplication();
            if (this.mUiccApplcation != uiccCardApplication) {
                log("Removing stale icc objects.");
                unregisterForRuimEvents();
                this.mIccRecords = null;
                this.mUiccApplcation = null;
                if (uiccCardApplication != null) {
                    log("New card found");
                    this.mUiccApplcation = uiccCardApplication;
                    this.mIccRecords = this.mUiccApplcation.getIccRecords();
                    if (this.mIsSubscriptionFromRuim) {
                        registerForRuimEvents();
                    }
                }
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void parseSidNid(String str, String str2) {
        int i = 0;
        if (str != null) {
            String[] split = str.split(",");
            this.mHomeSystemId = new int[split.length];
            for (int i2 = 0; i2 < split.length; i2++) {
                try {
                    this.mHomeSystemId[i2] = Integer.parseInt(split[i2]);
                } catch (NumberFormatException e) {
                    loge("error parsing system id: " + e);
                }
            }
        }
        log("CDMA_SUBSCRIPTION: SID=" + str);
        if (str2 != null) {
            String[] split2 = str2.split(",");
            this.mHomeNetworkId = new int[split2.length];
            while (i < split2.length) {
                try {
                    this.mHomeNetworkId[i] = Integer.parseInt(split2[i]);
                } catch (NumberFormatException e2) {
                    loge("CDMA_SUBSCRIPTION: error parsing network id: " + e2);
                }
                i++;
            }
        }
        log("CDMA_SUBSCRIPTION: NID=" + str2);
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
                pollStateDone();
                return;
            case RADIO_OFF:
                this.mNewSS.setStateOff();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
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
        this.mCi.getOperator(obtainMessage(25, this.mPollingContext));
        iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getVoiceRegistrationState(obtainMessage(24, this.mPollingContext));
        iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getDataRegistrationState(obtainMessage(5, this.mPollingContext));
    }

    /* Access modifiers changed, original: protected */
    public void pollStateDone() {
        if (this.mPhone.isMccMncMarkedAsNonRoaming(this.mNewSS.getOperatorNumeric()) || this.mPhone.isSidMarkedAsNonRoaming(this.mNewSS.getSystemId())) {
            log("pollStateDone: override - marked as non-roaming.");
            this.mNewSS.setRoaming(false);
            this.mNewSS.setCdmaEriIconIndex(1);
        } else if (this.mPhone.isMccMncMarkedAsRoaming(this.mNewSS.getOperatorNumeric()) || this.mPhone.isSidMarkedAsRoaming(this.mNewSS.getSystemId())) {
            log("pollStateDone: override - marked as roaming.");
            this.mNewSS.setRoaming(true);
            this.mNewSS.setCdmaEriIconIndex(0);
            this.mNewSS.setCdmaEriIconMode(0);
        }
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean("telephony.test.forceRoaming", false)) {
            this.mNewSS.setVoiceRoaming(true);
            this.mNewSS.setDataRoaming(true);
        }
        useDataRegStateForDataOnlyDevices();
        resetServiceStateInIwlanMode();
        log("pollStateDone: cdma oldSS=[" + this.mSS + "] newSS=[" + this.mNewSS + "]");
        Object obj = (this.mSS.getVoiceRegState() == 0 || this.mNewSS.getVoiceRegState() != 0) ? null : 1;
        if (!(this.mSS.getVoiceRegState() == 0 && this.mNewSS.getVoiceRegState() == 0)) {
        }
        Object obj2 = (this.mSS.getDataRegState() == 0 || this.mNewSS.getDataRegState() != 0) ? null : 1;
        Object obj3 = (this.mSS.getDataRegState() != 0 || this.mNewSS.getDataRegState() == 0) ? null : 1;
        Object obj4 = this.mSS.getDataRegState() != this.mNewSS.getDataRegState() ? 1 : null;
        Object obj5 = this.mSS.getRilVoiceRadioTechnology() != this.mNewSS.getRilVoiceRadioTechnology() ? 1 : null;
        Object obj6 = this.mSS.getRilDataRadioTechnology() != this.mNewSS.getRilDataRadioTechnology() ? 1 : null;
        Object obj7 = !this.mNewSS.equals(this.mSS) ? 1 : null;
        Object obj8 = (this.mSS.getVoiceRoaming() || !this.mNewSS.getVoiceRoaming()) ? null : 1;
        Object obj9 = (!this.mSS.getVoiceRoaming() || this.mNewSS.getVoiceRoaming()) ? null : 1;
        Object obj10 = (this.mSS.getDataRoaming() || !this.mNewSS.getDataRoaming()) ? null : 1;
        Object obj11 = (!this.mSS.getDataRoaming() || this.mNewSS.getDataRoaming()) ? null : 1;
        Object obj12 = !this.mNewCellLoc.equals(this.mCellLoc) ? 1 : null;
        TelephonyManager telephonyManager = (TelephonyManager) this.mPhone.getContext().getSystemService("phone");
        if (!(this.mSS.getVoiceRegState() == this.mNewSS.getVoiceRegState() && this.mSS.getDataRegState() == this.mNewSS.getDataRegState())) {
            EventLog.writeEvent(EventLogTags.CDMA_SERVICE_STATE_CHANGE, new Object[]{Integer.valueOf(this.mSS.getVoiceRegState()), Integer.valueOf(this.mSS.getDataRegState()), Integer.valueOf(this.mNewSS.getVoiceRegState()), Integer.valueOf(this.mNewSS.getDataRegState())});
        }
        ServiceState serviceState = this.mSS;
        this.mSS = this.mNewSS;
        this.mNewSS = serviceState;
        this.mNewSS.setStateOutOfService();
        CdmaCellLocation cdmaCellLocation = this.mCellLoc;
        this.mCellLoc = this.mNewCellLoc;
        this.mNewCellLoc = cdmaCellLocation;
        if (obj5 != null) {
            updatePhoneObject();
        }
        if (obj6 != null) {
            telephonyManager.setDataNetworkTypeForPhone(this.mPhone.getPhoneId(), this.mSS.getRilDataRadioTechnology());
            if (isIwlanFeatureAvailable() && 18 == this.mSS.getRilDataRadioTechnology()) {
                log("pollStateDone: IWLAN enabled");
            }
        }
        if (obj != null) {
            this.mNetworkAttachedRegistrants.notifyRegistrants();
        }
        if (obj7 != null) {
            if (this.mCi.getRadioState().isOn() && !this.mIsSubscriptionFromRuim) {
                this.mSS.setOperatorAlphaLong(this.mSS.getVoiceRegState() == 0 ? this.mPhone.getCdmaEriText() : this.mPhone.getContext().getText(17039620).toString());
            }
            this.mPhone.setSystemProperty("gsm.operator.alpha", this.mSS.getOperatorAlphaLong());
            String str = SystemProperties.get("gsm.operator.numeric", "");
            String operatorNumeric = this.mSS.getOperatorNumeric();
            if (isInvalidOperatorNumeric(operatorNumeric)) {
                operatorNumeric = fixUnknownMcc(operatorNumeric, this.mSS.getSystemId());
            }
            this.mPhone.setSystemProperty("gsm.operator.numeric", operatorNumeric);
            updateCarrierMccMncConfiguration(operatorNumeric, str, this.mPhone.getContext());
            if (isInvalidOperatorNumeric(operatorNumeric)) {
                log("operatorNumeric " + operatorNumeric + "is invalid");
                this.mPhone.setSystemProperty("gsm.operator.iso-country", "");
                this.mGotCountryCode = false;
            } else {
                String str2 = "";
                operatorNumeric.substring(0, 3);
                try {
                    str2 = MccTable.countryCodeForMcc(Integer.parseInt(operatorNumeric.substring(0, 3)));
                } catch (NumberFormatException e) {
                    loge("pollStateDone: countryCodeForMcc error" + e);
                } catch (StringIndexOutOfBoundsException e2) {
                    loge("pollStateDone: countryCodeForMcc error" + e2);
                }
                this.mPhone.setSystemProperty("gsm.operator.iso-country", str2);
                this.mGotCountryCode = true;
                setOperatorIdd(operatorNumeric);
                if (shouldFixTimeZoneNow(this.mPhone, operatorNumeric, str, this.mNeedFixZone)) {
                    fixTimeZone(str2);
                }
            }
            CDMAPhone cDMAPhone = this.mPhone;
            operatorNumeric = (this.mSS.getVoiceRoaming() || this.mSS.getDataRoaming()) ? "true" : "false";
            cDMAPhone.setSystemProperty("gsm.operator.isroaming", operatorNumeric);
            updateSpnDisplay();
            setRoamingType(this.mSS);
            log("Broadcasting ServiceState : " + this.mSS);
            this.mPhone.notifyServiceStateChanged(this.mSS);
        }
        if (obj3 != null) {
            this.mDetachedRegistrants.notifyRegistrants();
        }
        if (!(obj4 == null && obj6 == null)) {
            notifyDataRegStateRilRadioTechnologyChanged();
            if (isIwlanFeatureAvailable() && 18 == this.mSS.getRilDataRadioTechnology()) {
                this.mPhone.notifyDataConnection(Phone.REASON_IWLAN_AVAILABLE);
                this.mIwlanRatAvailable = true;
            } else {
                processIwlanToWwanTransition(this.mSS);
                this.mPhone.notifyDataConnection(null);
                this.mIwlanRatAvailable = false;
            }
        }
        if (obj2 != null) {
            this.mAttachedRegistrants.notifyRegistrants();
        }
        if (obj8 != null) {
            this.mVoiceRoamingOnRegistrants.notifyRegistrants();
        }
        if (obj9 != null) {
            this.mVoiceRoamingOffRegistrants.notifyRegistrants();
        }
        if (obj10 != null) {
            this.mDataRoamingOnRegistrants.notifyRegistrants();
        }
        if (obj11 != null) {
            this.mDataRoamingOffRegistrants.notifyRegistrants();
        }
        if (obj12 != null) {
            this.mPhone.notifyLocationChanged();
        }
    }

    /* Access modifiers changed, original: protected */
    public int radioTechnologyToDataServiceState(int i) {
        switch (i) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                break;
            case 6:
            case 7:
            case 8:
            case 12:
            case 13:
                return 0;
            default:
                loge("radioTechnologyToDataServiceState: Wrong radioTechnology code.");
                break;
        }
        return 1;
    }

    /* Access modifiers changed, original: protected */
    public boolean regCodeIsRoaming(int i) {
        return 5 == i;
    }

    /* Access modifiers changed, original: protected */
    public int regCodeToServiceState(int i) {
        switch (i) {
            case 0:
            case 2:
            case 3:
            case 4:
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

    public void registerForSubscriptionInfoReady(Handler handler, int i, Object obj) {
        Registrant registrant = new Registrant(handler, i, obj);
        this.mCdmaForSubscriptionInfoReadyRegistrants.add(registrant);
        if (isMinInfoReady()) {
            registrant.notifyRegistrant();
        }
    }

    public void setImsRegistrationState(boolean z) {
        log("ImsRegistrationState - registered : " + z);
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
    public void setOperatorIdd(String str) {
        String iddByMcc = this.mHbpcdUtils.getIddByMcc(Integer.parseInt(str.substring(0, 3)));
        if (iddByMcc == null || iddByMcc.isEmpty()) {
            this.mPhone.setSystemProperty("gsm.operator.idpstring", "+");
        } else {
            this.mPhone.setSystemProperty("gsm.operator.idpstring", iddByMcc);
        }
    }

    /* Access modifiers changed, original: protected */
    public void setPowerStateToDesired() {
        if (this.mDesiredPowerState && this.mCi.getRadioState() == RadioState.RADIO_OFF) {
            this.mCi.setRadioPower(true, null);
        } else if (!this.mDesiredPowerState && this.mCi.getRadioState().isOn()) {
            powerOffRadioSafely(this.mPhone.mDcTracker);
        } else if (this.mDeviceShuttingDown && this.mCi.getRadioState().isAvailable()) {
            this.mCi.requestShutdown(null);
        }
    }

    /* Access modifiers changed, original: protected */
    public void setRoamingType(ServiceState serviceState) {
        int i = serviceState.getVoiceRegState() == 0 ? 1 : 0;
        if (i != 0) {
            if (serviceState.getVoiceRoaming()) {
                int[] intArray = this.mPhone.getContext().getResources().getIntArray(17236037);
                if (intArray != null && intArray.length > 0) {
                    serviceState.setVoiceRoamingType(2);
                    int cdmaRoamingIndicator = serviceState.getCdmaRoamingIndicator();
                    for (int i2 : intArray) {
                        if (cdmaRoamingIndicator == i2) {
                            serviceState.setVoiceRoamingType(3);
                            break;
                        }
                    }
                } else if (inSameCountry(serviceState.getVoiceOperatorNumeric())) {
                    serviceState.setVoiceRoamingType(2);
                } else {
                    serviceState.setVoiceRoamingType(3);
                }
            } else {
                serviceState.setVoiceRoamingType(0);
            }
        }
        int i3 = serviceState.getDataRegState() == 0 ? 1 : 0;
        int rilDataRadioTechnology = serviceState.getRilDataRadioTechnology();
        if (i3 == 0) {
            return;
        }
        if (!serviceState.getDataRoaming()) {
            serviceState.setDataRoamingType(0);
        } else if (ServiceState.isCdma(rilDataRadioTechnology)) {
            if (i != 0) {
                serviceState.setDataRoamingType(serviceState.getVoiceRoamingType());
            } else {
                serviceState.setDataRoamingType(1);
            }
        } else if (inSameCountry(serviceState.getDataOperatorNumeric())) {
            serviceState.setDataRoamingType(2);
        } else {
            serviceState.setDataRoamingType(3);
        }
    }

    /* Access modifiers changed, original: protected */
    public void setSignalStrengthDefaultValues() {
        this.mSignalStrength = new SignalStrength(false);
    }

    public void unregisterForSubscriptionInfoReady(Handler handler) {
        this.mCdmaForSubscriptionInfoReadyRegistrants.remove(handler);
    }

    /* Access modifiers changed, original: protected */
    public void updateOtaspState() {
        int otasp = getOtasp();
        int i = this.mCurrentOtaspMode;
        this.mCurrentOtaspMode = otasp;
        if (this.mCdmaForSubscriptionInfoReadyRegistrants != null) {
            log("CDMA_SUBSCRIPTION: call notifyRegistrants()");
            this.mCdmaForSubscriptionInfoReadyRegistrants.notifyRegistrants();
        }
        if (i != this.mCurrentOtaspMode) {
            log("CDMA_SUBSCRIPTION: call notifyOtaspChanged old otaspMode=" + i + " new otaspMode=" + this.mCurrentOtaspMode);
            this.mPhone.notifyOtaspChanged(this.mCurrentOtaspMode);
        }
    }

    /* Access modifiers changed, original: protected */
    public void updateSpnDisplay() {
        CharSequence operatorAlphaLong = this.mSS.getOperatorAlphaLong();
        if (getCombinedRegState() == 1) {
            operatorAlphaLong = Resources.getSystem().getText(17040344).toString();
            log("updateSpnDisplay: radio is on but out of service, set plmn='" + operatorAlphaLong + "'");
        }
        if (!TextUtils.equals(operatorAlphaLong, this.mCurPlmn)) {
            log(String.format("updateSpnDisplay: changed sending intent showPlmn='%b' plmn='%s'", new Object[]{Boolean.valueOf(operatorAlphaLong != null), operatorAlphaLong}));
            Intent intent = new Intent("android.provider.Telephony.SPN_STRINGS_UPDATED");
            intent.addFlags(536870912);
            intent.putExtra("showSpn", false);
            intent.putExtra("spn", "");
            intent.putExtra("showPlmn", r1);
            intent.putExtra(CellBroadcasts.PLMN, operatorAlphaLong);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
            this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
        this.mCurPlmn = operatorAlphaLong;
    }
}
