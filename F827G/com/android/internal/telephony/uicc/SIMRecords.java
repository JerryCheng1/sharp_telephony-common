package com.android.internal.telephony.uicc;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.gsm.SimTlv;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccRecords;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class SIMRecords extends IccRecords {
    static final int CFF_LINE1_MASK = 15;
    static final int CFF_LINE1_RESET = 240;
    static final int CFF_UNCONDITIONAL_ACTIVE = 10;
    static final int CFF_UNCONDITIONAL_DEACTIVE = 5;
    private static final int CFIS_ADN_CAPABILITY_ID_OFFSET = 14;
    private static final int CFIS_ADN_EXTENSION_ID_OFFSET = 15;
    private static final int CFIS_BCD_NUMBER_LENGTH_OFFSET = 2;
    private static final int CFIS_TON_NPI_OFFSET = 3;
    private static final int CPHS_SST_MBN_ENABLED = 48;
    private static final int CPHS_SST_MBN_MASK = 48;
    private static final boolean CRASH_RIL = false;
    private static final int EVENT_APP_LOCKED = 35;
    protected static final int EVENT_GET_AD_DONE = 9;
    private static final int EVENT_GET_AD_SUBSCRIPTION_PERSO_DONE = 103;
    private static final int EVENT_GET_ALL_SMS_DONE = 18;
    private static final int EVENT_GET_CFF_DONE = 24;
    private static final int EVENT_GET_CFIS_DONE = 32;
    private static final int EVENT_GET_CPHS_MAILBOX_DONE = 11;
    private static final int EVENT_GET_CSP_CPHS_DONE = 33;
    private static final int EVENT_GET_GID1_DONE = 34;
    private static final int EVENT_GET_GID1_PERSO_DONE = 104;
    private static final int EVENT_GET_ICCID_DONE = 4;
    private static final int EVENT_GET_IMSI_DONE = 3;
    private static final int EVENT_GET_IMSI_SUBSCRIPTION_PERSO_DONE = 102;
    private static final int EVENT_GET_INFO_CPHS_DONE = 26;
    private static final int EVENT_GET_MBDN_DONE = 6;
    private static final int EVENT_GET_MBI_DONE = 5;
    protected static final int EVENT_GET_MSISDN_DONE = 10;
    private static final int EVENT_GET_MWIS_DONE = 7;
    private static final int EVENT_GET_PNN_DONE = 15;
    private static final int EVENT_GET_SMS_DONE = 22;
    private static final int EVENT_GET_SPDI_DONE = 13;
    private static final int EVENT_GET_SPN_DONE = 12;
    protected static final int EVENT_GET_SST_DONE = 17;
    private static final int EVENT_GET_VOICE_MAIL_INDICATOR_CPHS_DONE = 8;
    private static final int EVENT_MARK_SMS_READ_DONE = 19;
    private static final int EVENT_SET_CPHS_MAILBOX_DONE = 25;
    private static final int EVENT_SET_MBDN_DONE = 20;
    private static final int EVENT_SUBSCRIPTION_PERSO = 101;
    private static final int EVENT_UPDATE_DONE = 14;
    protected static final String LOG_TAG = "SIMRecords";
    private static final String[] MCCMNC_CODES_HAVING_3DIGITS_MNC = {"302370", "302720", "310260", "405025", "405026", "405027", "405028", "405029", "405030", "405031", "405032", "405033", "405034", "405035", "405036", "405037", "405038", "405039", "405040", "405041", "405042", "405043", "405044", "405045", "405046", "405047", "405750", "405751", "405752", "405753", "405754", "405755", "405756", "405799", "405800", "405801", "405802", "405803", "405804", "405805", "405806", "405807", "405808", "405809", "405810", "405811", "405812", "405813", "405814", "405815", "405816", "405817", "405818", "405819", "405820", "405821", "405822", "405823", "405824", "405825", "405826", "405827", "405828", "405829", "405830", "405831", "405832", "405833", "405834", "405835", "405836", "405837", "405838", "405839", "405840", "405841", "405842", "405843", "405844", "405845", "405846", "405847", "405848", "405849", "405850", "405851", "405852", "405853", "405875", "405876", "405877", "405878", "405879", "405880", "405881", "405882", "405883", "405884", "405885", "405886", "405908", "405909", "405910", "405911", "405912", "405913", "405914", "405915", "405916", "405917", "405918", "405919", "405920", "405921", "405922", "405923", "405924", "405925", "405926", "405927", "405928", "405929", "405930", "405931", "405932", "502142", "502143", "502145", "502146", "502147", "502148"};
    private static final int MCC_SIZE = 3;
    private static final int MNC_SIZE_MIN = 2;
    private static final String NW_SUBSET_BOOTSTRAP_DD_MAX = "4402049";
    private static final String NW_SUBSET_BOOTSTRAP_DD_MIN = "4402000";
    private static final String NW_SUBSET_BOOTSTRAP_SP_MAX = "4402029";
    private static final String NW_SUBSET_BOOTSTRAP_SP_MIN = "4402020";
    private static final String NW_SUBSET_SOFTBANK = "4402082";
    private static final String NW_SUBSET_SOFTBANK_GROUP = "4402091";
    private static final String NW_SUBSET_TEST_MAX = "012";
    private static final String NW_SUBSET_TEST_MIN = "001";
    private static final String SP_CODE_DEFAULT = "FF";
    private static final String SP_CODE_EMOBILE = "16";
    static final int TAG_FULL_NETWORK_NAME = 67;
    static final int TAG_SHORT_NETWORK_NAME = 69;
    static final int TAG_SPDI = 163;
    static final int TAG_SPDI_PLMN_LIST = 128;
    private boolean mCallForwardingEnabled;
    int mSpnDisplayCondition;
    SpnOverride mSpnOverride;
    private GetSpnFsmState mSpnState;
    UsimServiceTable mUsimServiceTable;
    VoiceMailConstants mVmConfig;
    private byte[] mCphsInfo = null;
    boolean mCspPlmnEnabled = true;
    byte[] mEfMWIS = null;
    byte[] mEfCPHS_MWI = null;
    byte[] mEfCff = null;
    byte[] mEfCfis = null;
    byte[] mEfLi = null;
    byte[] mEfPl = null;
    ArrayList<String> mSpdiNetworks = null;
    String mPnnHomeName = null;
    protected String mSimLockImsi = null;
    protected int mSimLockMccMncSize = -1;
    protected final boolean mIsSmartPhone = true;
    protected String mSimlockGid1 = null;
    protected boolean mSimRefresh = false;

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum GetSpnFsmState {
        IDLE,
        INIT,
        READ_SPN_3GPP,
        READ_SPN_CPHS,
        READ_SPN_SHORT_CPHS
    }

    @Override // com.android.internal.telephony.uicc.IccRecords, android.os.Handler
    public String toString() {
        return "SimRecords: " + super.toString() + " mVmConfig" + this.mVmConfig + " mSpnOverride=mSpnOverride callForwardingEnabled=" + this.mCallForwardingEnabled + " spnState=" + this.mSpnState + " mCphsInfo=" + this.mCphsInfo + " mCspPlmnEnabled=" + this.mCspPlmnEnabled + " efMWIS=" + this.mEfMWIS + " efCPHS_MWI=" + this.mEfCPHS_MWI + " mEfCff=" + this.mEfCff + " mEfCfis=" + this.mEfCfis + " getOperatorNumeric=" + getOperatorNumeric();
    }

    public SIMRecords(UiccCardApplication app, Context c, CommandsInterface ci) {
        super(app, c, ci);
        if (TelBrand.IS_SBM) {
            this.mAdnCache = new AdnRecordCache(this.mFh, app);
        } else {
            this.mAdnCache = new AdnRecordCache(this.mFh);
        }
        this.mVmConfig = new VoiceMailConstants();
        this.mSpnOverride = new SpnOverride();
        this.mRecordsRequested = false;
        this.mRecordsToLoad = 0;
        resetRecords();
        this.mParentApp.registerForReady(this, 1, null);
        this.mParentApp.registerForLocked(this, 35, null);
        this.mParentApp.registerForPersoLocked(this, EVENT_SUBSCRIPTION_PERSO, null);
        log("SIMRecords X ctor this=" + this);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void dispose() {
        log("Disposing SIMRecords this=" + this);
        this.mParentApp.unregisterForReady(this);
        this.mParentApp.unregisterForLocked(this);
        resetRecords();
        super.dispose();
    }

    protected void finalize() {
        log("finalized");
    }

    protected void resetRecords() {
        this.mImsi = null;
        this.mSimLockImsi = null;
        this.mSimLockMccMncSize = -1;
        this.mSimlockGid1 = null;
        this.mMsisdn = null;
        this.mVoiceMailNum = null;
        this.mMncLength = -1;
        log("setting0 mMncLength" + this.mMncLength);
        this.mIccId = null;
        this.mSpnDisplayCondition = -1;
        this.mEfMWIS = null;
        this.mEfCPHS_MWI = null;
        this.mSpdiNetworks = null;
        this.mPnnHomeName = null;
        this.mGid1 = null;
        this.mAdnCache.reset();
        log("SIMRecords: onRadioOffOrNotAvailable set 'gsm.sim.operator.numeric' to operator=null");
        log("update icc_operator_numeric=" + ((Object) null));
        setSystemProperty("gsm.sim.operator.numeric", null);
        setSystemProperty("gsm.sim.operator.alpha", null);
        setSystemProperty("gsm.sim.operator.iso-country", null);
        this.mRecordsRequested = false;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getIMSI() {
        return this.mImsi;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getMccMncOnSimLock() {
        if (this.mSimLockImsi == null) {
            loge("getSimLockMccMnc : EF_IMSI is not loaded");
            return null;
        }
        int size = this.mSimLockMccMncSize;
        if (size == -1 || size == 0) {
            size = 5;
            log("getSimLockMccMnc : EF_AD is invalid. treating MCC/MNC length as 5");
        }
        return this.mSimLockImsi.substring(0, size);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getImsiOnSimLock() {
        if (this.mSimLockImsi == null) {
            log("getImsiOnSimLock : mSimLockImsi is null");
        }
        return this.mSimLockImsi;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public int getBrand() {
        String subsetName;
        String spCode;
        int brand;
        String imsi = getImsiOnSimLock();
        if (imsi != null) {
            subsetName = imsi.substring(0, 7);
        } else {
            subsetName = null;
        }
        if (this.mSimlockGid1 != null) {
            spCode = this.mSimlockGid1.substring(0, 2);
        } else {
            spCode = SP_CODE_DEFAULT;
        }
        if (imsi == null) {
            brand = 0;
        } else if (isBootstrapIMSI(imsi)) {
            brand = 2;
        } else if (NW_SUBSET_SOFTBANK.equals(subsetName)) {
            brand = 2;
        } else if (NW_SUBSET_SOFTBANK_GROUP.equals(subsetName)) {
            if (this.mSimlockGid1 == null) {
                log("getBrand : Brand is 0 (waiting GID1)");
                return 0;
            } else if (SP_CODE_EMOBILE.equals(spCode)) {
                brand = 3;
            } else {
                brand = 1;
            }
        } else if (isTestIMSI(imsi)) {
            brand = 5;
        } else {
            brand = 1;
        }
        log("getBrand : Brand is " + brand);
        return brand;
    }

    private boolean isBootstrapIMSI(String imsi) {
        return isInRange(imsi, NW_SUBSET_BOOTSTRAP_SP_MIN, NW_SUBSET_BOOTSTRAP_SP_MAX);
    }

    private boolean isTestIMSI(String imsi) {
        return isInRange(imsi, NW_SUBSET_TEST_MIN, NW_SUBSET_TEST_MAX);
    }

    private boolean isInRange(String imsi, String min, String max) {
        if (imsi.length() < min.length() || imsi.length() < max.length()) {
            loge("isInRange() : IMSI is too short");
            return false;
        } else if (Integer.parseInt(min) > Integer.parseInt(imsi.substring(0, min.length())) || Integer.parseInt(imsi.substring(0, max.length())) > Integer.parseInt(max)) {
            return false;
        } else {
            return true;
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getMsisdnNumber() {
        return this.mMsisdn;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getGid1() {
        return this.mGid1;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public UsimServiceTable getUsimServiceTable() {
        return this.mUsimServiceTable;
    }

    private int getExtFromEf(int ef) {
        switch (ef) {
            case IccConstants.EF_MSISDN /* 28480 */:
                if (this.mParentApp.getType() == IccCardApplicationStatus.AppType.APPTYPE_USIM) {
                    return IccConstants.EF_EXT5;
                }
                return IccConstants.EF_EXT1;
            default:
                return IccConstants.EF_EXT1;
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void setMsisdnNumber(String alphaTag, String number, Message onComplete) {
        this.mNewMsisdn = number;
        this.mNewMsisdnTag = alphaTag;
        log("Set MSISDN: " + this.mNewMsisdnTag + " xxxxxxx");
        new AdnRecordLoader(this.mFh).updateEF(new AdnRecord(this.mNewMsisdnTag, this.mNewMsisdn), IccConstants.EF_MSISDN, getExtFromEf(IccConstants.EF_MSISDN), 1, null, obtainMessage(30, onComplete));
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getMsisdnAlphaTag() {
        return this.mMsisdnTag;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getVoiceMailNumber() {
        return this.mVoiceMailNum;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void setVoiceMailNumber(String alphaTag, String voiceNumber, Message onComplete) {
        if (this.mIsVoiceMailFixed) {
            AsyncResult.forMessage(onComplete).exception = new IccVmFixedException("Voicemail number is fixed by operator");
            onComplete.sendToTarget();
            return;
        }
        this.mNewVoiceMailNum = voiceNumber;
        this.mNewVoiceMailTag = alphaTag;
        AdnRecord adn = new AdnRecord(this.mNewVoiceMailTag, this.mNewVoiceMailNum);
        if (this.mMailboxIndex != 0 && this.mMailboxIndex != 255) {
            new AdnRecordLoader(this.mFh).updateEF(adn, IccConstants.EF_MBDN, IccConstants.EF_EXT6, this.mMailboxIndex, null, obtainMessage(20, onComplete));
        } else if (isCphsMailboxEnabled()) {
            new AdnRecordLoader(this.mFh).updateEF(adn, IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, null, obtainMessage(25, onComplete));
        } else {
            AsyncResult.forMessage(onComplete).exception = new IccVmNotSupportedException("Update SIM voice mailbox error");
            onComplete.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getVoiceMailAlphaTag() {
        return this.mVoiceMailTag;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        int i = 0;
        if (line == 1) {
            try {
                if (this.mEfMWIS != null) {
                    byte[] bArr = this.mEfMWIS;
                    int i2 = this.mEfMWIS[0] & 254;
                    if (countWaiting != 0) {
                        i = 1;
                    }
                    bArr[0] = (byte) (i | i2);
                    if (countWaiting < 0) {
                        this.mEfMWIS[1] = 0;
                    } else {
                        this.mEfMWIS[1] = (byte) countWaiting;
                    }
                    this.mFh.updateEFLinearFixed(IccConstants.EF_MWIS, 1, this.mEfMWIS, null, obtainMessage(14, IccConstants.EF_MWIS, 0));
                }
                if (this.mEfCPHS_MWI != null) {
                    this.mEfCPHS_MWI[0] = (byte) ((countWaiting == 0 ? 5 : 10) | (this.mEfCPHS_MWI[0] & 240));
                    this.mFh.updateEFTransparent(IccConstants.EF_VOICE_MAIL_INDICATOR_CPHS, this.mEfCPHS_MWI, obtainMessage(14, Integer.valueOf((int) IccConstants.EF_VOICE_MAIL_INDICATOR_CPHS)));
                }
            } catch (ArrayIndexOutOfBoundsException ex) {
                logw("Error saving voice mail state to SIM. Probably malformed SIM record", ex);
            }
        }
    }

    private boolean validEfCfis(byte[] data) {
        return data != null && data[0] >= 1 && data[0] <= 4;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public int getVoiceMessageCount() {
        int countVoiceMessages = -2;
        if (this.mEfMWIS != null) {
            boolean voiceMailWaiting = (this.mEfMWIS[0] & 1) != 0;
            countVoiceMessages = this.mEfMWIS[1] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
            if (voiceMailWaiting && countVoiceMessages == 0) {
                countVoiceMessages = -1;
            }
            log(" VoiceMessageCount from SIM MWIS = " + countVoiceMessages);
        } else if (this.mEfCPHS_MWI != null) {
            int indicator = this.mEfCPHS_MWI[0] & 15;
            if (indicator == 10) {
                countVoiceMessages = -1;
            } else if (indicator == 5) {
                countVoiceMessages = 0;
            }
            log(" VoiceMessageCount from SIM CPHS = " + countVoiceMessages);
        }
        return countVoiceMessages;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public boolean isCallForwardStatusStored() {
        return (this.mEfCfis == null && this.mEfCff == null) ? false : true;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public boolean getVoiceCallForwardingFlag() {
        return this.mCallForwardingEnabled;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void setVoiceCallForwardingFlag(int line, boolean enable, String dialNumber) {
        if (line == 1) {
            this.mCallForwardingEnabled = enable;
            this.mRecordsEventsRegistrants.notifyResult(1);
            try {
                if (validEfCfis(this.mEfCfis)) {
                    if (enable) {
                        byte[] bArr = this.mEfCfis;
                        bArr[1] = (byte) (bArr[1] | 1);
                    } else {
                        byte[] bArr2 = this.mEfCfis;
                        bArr2[1] = (byte) (bArr2[1] & 254);
                    }
                    log("setVoiceCallForwardingFlag: enable=" + enable + " mEfCfis=" + IccUtils.bytesToHexString(this.mEfCfis));
                    if (enable && !TextUtils.isEmpty(dialNumber)) {
                        log("EF_CFIS: updating cf number, " + dialNumber);
                        byte[] bcdNumber = PhoneNumberUtils.numberToCalledPartyBCD(dialNumber);
                        System.arraycopy(bcdNumber, 0, this.mEfCfis, 3, bcdNumber.length);
                        this.mEfCfis[2] = (byte) bcdNumber.length;
                        this.mEfCfis[14] = -1;
                        this.mEfCfis[15] = -1;
                    }
                    this.mFh.updateEFLinearFixed(IccConstants.EF_CFIS, 1, this.mEfCfis, null, obtainMessage(14, Integer.valueOf((int) IccConstants.EF_CFIS)));
                } else {
                    log("setVoiceCallForwardingFlag: ignoring enable=" + enable + " invalid mEfCfis=" + IccUtils.bytesToHexString(this.mEfCfis));
                }
                if (this.mEfCff != null) {
                    if (enable) {
                        this.mEfCff[0] = (byte) ((this.mEfCff[0] & 240) | 10);
                    } else {
                        this.mEfCff[0] = (byte) ((this.mEfCff[0] & 240) | 5);
                    }
                    this.mFh.updateEFTransparent(IccConstants.EF_CFF_CPHS, this.mEfCff, obtainMessage(14, Integer.valueOf((int) IccConstants.EF_CFF_CPHS)));
                }
            } catch (ArrayIndexOutOfBoundsException ex) {
                logw("Error saving call forwarding flag to SIM. Probably malformed SIM record", ex);
            }
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void onRefresh(boolean fileChanged, int[] fileList) {
        if (fileChanged) {
            fetchSimRecords();
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getOperatorNumeric() {
        if (this.mImsi == null) {
            log("getOperatorNumeric: IMSI == null");
            return null;
        } else if (this.mMncLength != -1 && this.mMncLength != 0) {
            return this.mImsi.substring(0, this.mMncLength + 3);
        } else {
            log("getSIMOperatorNumeric: bad mncLength");
            return null;
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords, android.os.Handler
    public void handleMessage(Message msg) {
        boolean isRecordLoadResponse = false;
        try {
            if (this.mDestroyed.get()) {
                loge("Received message " + msg + "[" + msg.what + "]  while being destroyed. Ignoring.");
                return;
            }
            try {
                switch (msg.what) {
                    case 1:
                        onReady();
                        break;
                    case 3:
                        isRecordLoadResponse = true;
                        AsyncResult ar = (AsyncResult) msg.obj;
                        if (ar.exception != null) {
                            loge("Exception querying IMSI, Exception:" + ar.exception);
                            break;
                        } else {
                            this.mImsi = (String) ar.result;
                            this.mSimLockImsi = (String) ar.result;
                            if (this.mImsi != null && (this.mImsi.length() < 6 || this.mImsi.length() > 15)) {
                                loge("invalid IMSI " + this.mImsi);
                                this.mImsi = null;
                                this.mSimLockImsi = null;
                            }
                            log("IMSI: mMncLength=" + this.mMncLength);
                            log("IMSI: " + this.mImsi.substring(0, 6) + "xxxxxxx");
                            if ((this.mMncLength == 0 || this.mMncLength == 2) && this.mImsi != null && this.mImsi.length() >= 6) {
                                String mccmncCode = this.mImsi.substring(0, 6);
                                String[] arr$ = MCCMNC_CODES_HAVING_3DIGITS_MNC;
                                int len$ = arr$.length;
                                int i$ = 0;
                                while (true) {
                                    if (i$ < len$) {
                                        if (arr$[i$].equals(mccmncCode)) {
                                            this.mMncLength = 3;
                                            log("IMSI: setting1 mMncLength=" + this.mMncLength);
                                        } else {
                                            i$++;
                                        }
                                    }
                                }
                            }
                            if (this.mMncLength == 0) {
                                try {
                                    this.mMncLength = MccTable.smallestDigitsMccForMnc(Integer.parseInt(this.mImsi.substring(0, 3)));
                                    log("setting2 mMncLength=" + this.mMncLength);
                                } catch (NumberFormatException e) {
                                    this.mMncLength = 0;
                                    loge("Corrupt IMSI! setting3 mMncLength=" + this.mMncLength);
                                }
                            }
                            if (!(this.mMncLength == 0 || this.mMncLength == -1)) {
                                log("update mccmnc=" + this.mImsi.substring(0, this.mMncLength + 3));
                                MccTable.updateMccMncConfiguration(this.mContext, this.mImsi.substring(0, this.mMncLength + 3), false);
                            }
                            if (isAppStateReady()) {
                                this.mImsiReadyRegistrants.notifyRegistrants();
                                break;
                            } else {
                                log("EVENT_GET_IMSI_DONE:App state is not ready; not notifying the registrants");
                                break;
                            }
                        }
                        break;
                    case 4:
                        isRecordLoadResponse = true;
                        AsyncResult ar2 = (AsyncResult) msg.obj;
                        byte[] data = (byte[]) ar2.result;
                        if (ar2.exception == null) {
                            this.mIccId = IccUtils.bcdToString(data, 0, data.length);
                            log("iccid: " + this.mIccId);
                            break;
                        }
                        break;
                    case 5:
                        isRecordLoadResponse = true;
                        AsyncResult ar3 = (AsyncResult) msg.obj;
                        byte[] data2 = (byte[]) ar3.result;
                        boolean isValidMbdn = false;
                        if (ar3.exception == null) {
                            log("EF_MBI: " + IccUtils.bytesToHexString(data2));
                            this.mMailboxIndex = data2[0] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
                            if (!(this.mMailboxIndex == 0 || this.mMailboxIndex == 255)) {
                                log("Got valid mailbox number for MBDN");
                                isValidMbdn = true;
                            }
                        }
                        this.mRecordsToLoad++;
                        if (isValidMbdn) {
                            new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MBDN, IccConstants.EF_EXT6, this.mMailboxIndex, obtainMessage(6));
                            break;
                        } else {
                            new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, obtainMessage(11));
                            break;
                        }
                    case 6:
                    case 11:
                        this.mVoiceMailNum = null;
                        this.mVoiceMailTag = null;
                        isRecordLoadResponse = true;
                        AsyncResult ar4 = (AsyncResult) msg.obj;
                        if (ar4.exception != null) {
                            log("Invalid or missing EF" + (msg.what == 11 ? "[MAILBOX]" : "[MBDN]"));
                            if (msg.what == 6) {
                                this.mRecordsToLoad++;
                                new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, obtainMessage(11));
                                break;
                            }
                        } else {
                            AdnRecord adn = (AdnRecord) ar4.result;
                            log("VM: " + adn + (msg.what == 11 ? " EF[MAILBOX]" : " EF[MBDN]"));
                            if (!adn.isEmpty() || msg.what != 6) {
                                this.mVoiceMailNum = adn.getNumber();
                                this.mVoiceMailTag = adn.getAlphaTag();
                                break;
                            } else {
                                this.mRecordsToLoad++;
                                new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, obtainMessage(11));
                                break;
                            }
                        }
                        break;
                    case 7:
                        isRecordLoadResponse = true;
                        AsyncResult ar5 = (AsyncResult) msg.obj;
                        byte[] data3 = (byte[]) ar5.result;
                        log("EF_MWIS : " + IccUtils.bytesToHexString(data3));
                        if (ar5.exception == null) {
                            if ((data3[0] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) == 255) {
                                log("SIMRecords: Uninitialized record MWIS");
                                break;
                            } else {
                                this.mEfMWIS = data3;
                                break;
                            }
                        } else {
                            log("EVENT_GET_MWIS_DONE exception = " + ar5.exception);
                            break;
                        }
                    case 8:
                        isRecordLoadResponse = true;
                        AsyncResult ar6 = (AsyncResult) msg.obj;
                        byte[] data4 = (byte[]) ar6.result;
                        log("EF_CPHS_MWI: " + IccUtils.bytesToHexString(data4));
                        if (ar6.exception != null) {
                            log("EVENT_GET_VOICE_MAIL_INDICATOR_CPHS_DONE exception = " + ar6.exception);
                            break;
                        } else {
                            this.mEfCPHS_MWI = data4;
                            break;
                        }
                    case 9:
                        isRecordLoadResponse = true;
                        try {
                            AsyncResult ar7 = (AsyncResult) msg.obj;
                            byte[] data5 = (byte[]) ar7.result;
                            if (ar7.exception != null) {
                                if ((this.mMncLength == -1 || this.mMncLength == 0 || this.mMncLength == 2) && this.mImsi != null && this.mImsi.length() >= 6) {
                                    String mccmncCode2 = this.mImsi.substring(0, 6);
                                    log("mccmncCode=" + mccmncCode2);
                                    String[] arr$2 = MCCMNC_CODES_HAVING_3DIGITS_MNC;
                                    int len$2 = arr$2.length;
                                    int i$2 = 0;
                                    while (true) {
                                        if (i$2 < len$2) {
                                            if (arr$2[i$2].equals(mccmncCode2)) {
                                                this.mMncLength = 3;
                                                log("setting6 mMncLength=" + this.mMncLength);
                                            } else {
                                                i$2++;
                                            }
                                        }
                                    }
                                }
                                if (this.mMncLength == 0 || this.mMncLength == -1) {
                                    if (this.mImsi != null) {
                                        try {
                                            this.mMncLength = MccTable.smallestDigitsMccForMnc(Integer.parseInt(this.mImsi.substring(0, 3)));
                                            log("setting7 mMncLength=" + this.mMncLength);
                                        } catch (NumberFormatException e2) {
                                            this.mMncLength = 0;
                                            loge("Corrupt IMSI! setting8 mMncLength=" + this.mMncLength);
                                        }
                                    } else {
                                        this.mMncLength = 0;
                                        log("MNC length not present in EF_AD setting9 mMncLength=" + this.mMncLength);
                                    }
                                }
                                if (!(this.mImsi == null || this.mMncLength == 0)) {
                                    log("update mccmnc=" + this.mImsi.substring(0, this.mMncLength + 3));
                                    MccTable.updateMccMncConfiguration(this.mContext, this.mImsi.substring(0, this.mMncLength + 3), false);
                                }
                                if (this.mMncLength != 0 && this.mMncLength != -1) {
                                    this.mSimLockMccMncSize = this.mMncLength + 3;
                                    break;
                                } else {
                                    this.mSimLockMccMncSize = 0;
                                    break;
                                }
                            } else {
                                log("EF_AD: " + IccUtils.bytesToHexString(data5));
                                if (data5.length >= 3) {
                                    if (data5.length == 3) {
                                        log("MNC length not present in EF_AD");
                                        if ((this.mMncLength == -1 || this.mMncLength == 0 || this.mMncLength == 2) && this.mImsi != null && this.mImsi.length() >= 6) {
                                            String mccmncCode3 = this.mImsi.substring(0, 6);
                                            log("mccmncCode=" + mccmncCode3);
                                            String[] arr$3 = MCCMNC_CODES_HAVING_3DIGITS_MNC;
                                            int len$3 = arr$3.length;
                                            int i$3 = 0;
                                            while (true) {
                                                if (i$3 < len$3) {
                                                    if (arr$3[i$3].equals(mccmncCode3)) {
                                                        this.mMncLength = 3;
                                                        log("setting6 mMncLength=" + this.mMncLength);
                                                    } else {
                                                        i$3++;
                                                    }
                                                }
                                            }
                                        }
                                        if (this.mMncLength == 0 || this.mMncLength == -1) {
                                            if (this.mImsi != null) {
                                                try {
                                                    this.mMncLength = MccTable.smallestDigitsMccForMnc(Integer.parseInt(this.mImsi.substring(0, 3)));
                                                    log("setting7 mMncLength=" + this.mMncLength);
                                                } catch (NumberFormatException e3) {
                                                    this.mMncLength = 0;
                                                    loge("Corrupt IMSI! setting8 mMncLength=" + this.mMncLength);
                                                }
                                            } else {
                                                this.mMncLength = 0;
                                                log("MNC length not present in EF_AD setting9 mMncLength=" + this.mMncLength);
                                            }
                                        }
                                        if (!(this.mImsi == null || this.mMncLength == 0)) {
                                            log("update mccmnc=" + this.mImsi.substring(0, this.mMncLength + 3));
                                            MccTable.updateMccMncConfiguration(this.mContext, this.mImsi.substring(0, this.mMncLength + 3), false);
                                        }
                                        if (this.mMncLength != 0 && this.mMncLength != -1) {
                                            this.mSimLockMccMncSize = this.mMncLength + 3;
                                            break;
                                        } else {
                                            this.mSimLockMccMncSize = 0;
                                            break;
                                        }
                                    } else {
                                        this.mMncLength = data5[3] & 15;
                                        log("setting4 mMncLength=" + this.mMncLength);
                                        if (this.mMncLength == 15) {
                                            this.mMncLength = 0;
                                            log("setting5 mMncLength=" + this.mMncLength);
                                        }
                                        if ((this.mMncLength == -1 || this.mMncLength == 0 || this.mMncLength == 2) && this.mImsi != null && this.mImsi.length() >= 6) {
                                            String mccmncCode4 = this.mImsi.substring(0, 6);
                                            log("mccmncCode=" + mccmncCode4);
                                            String[] arr$4 = MCCMNC_CODES_HAVING_3DIGITS_MNC;
                                            int len$4 = arr$4.length;
                                            int i$4 = 0;
                                            while (true) {
                                                if (i$4 < len$4) {
                                                    if (arr$4[i$4].equals(mccmncCode4)) {
                                                        this.mMncLength = 3;
                                                        log("setting6 mMncLength=" + this.mMncLength);
                                                    } else {
                                                        i$4++;
                                                    }
                                                }
                                            }
                                        }
                                        if (this.mMncLength == 0 || this.mMncLength == -1) {
                                            if (this.mImsi != null) {
                                                try {
                                                    this.mMncLength = MccTable.smallestDigitsMccForMnc(Integer.parseInt(this.mImsi.substring(0, 3)));
                                                    log("setting7 mMncLength=" + this.mMncLength);
                                                } catch (NumberFormatException e4) {
                                                    this.mMncLength = 0;
                                                    loge("Corrupt IMSI! setting8 mMncLength=" + this.mMncLength);
                                                }
                                            } else {
                                                this.mMncLength = 0;
                                                log("MNC length not present in EF_AD setting9 mMncLength=" + this.mMncLength);
                                            }
                                        }
                                        if (!(this.mImsi == null || this.mMncLength == 0)) {
                                            log("update mccmnc=" + this.mImsi.substring(0, this.mMncLength + 3));
                                            MccTable.updateMccMncConfiguration(this.mContext, this.mImsi.substring(0, this.mMncLength + 3), false);
                                        }
                                        if (this.mMncLength != 0 && this.mMncLength != -1) {
                                            this.mSimLockMccMncSize = this.mMncLength + 3;
                                            break;
                                        } else {
                                            this.mSimLockMccMncSize = 0;
                                            break;
                                        }
                                    }
                                } else {
                                    log("Corrupt AD data on SIM");
                                    if ((this.mMncLength == -1 || this.mMncLength == 0 || this.mMncLength == 2) && this.mImsi != null && this.mImsi.length() >= 6) {
                                        String mccmncCode5 = this.mImsi.substring(0, 6);
                                        log("mccmncCode=" + mccmncCode5);
                                        String[] arr$5 = MCCMNC_CODES_HAVING_3DIGITS_MNC;
                                        int len$5 = arr$5.length;
                                        int i$5 = 0;
                                        while (true) {
                                            if (i$5 < len$5) {
                                                if (arr$5[i$5].equals(mccmncCode5)) {
                                                    this.mMncLength = 3;
                                                    log("setting6 mMncLength=" + this.mMncLength);
                                                } else {
                                                    i$5++;
                                                }
                                            }
                                        }
                                    }
                                    if (this.mMncLength == 0 || this.mMncLength == -1) {
                                        if (this.mImsi != null) {
                                            try {
                                                this.mMncLength = MccTable.smallestDigitsMccForMnc(Integer.parseInt(this.mImsi.substring(0, 3)));
                                                log("setting7 mMncLength=" + this.mMncLength);
                                            } catch (NumberFormatException e5) {
                                                this.mMncLength = 0;
                                                loge("Corrupt IMSI! setting8 mMncLength=" + this.mMncLength);
                                            }
                                        } else {
                                            this.mMncLength = 0;
                                            log("MNC length not present in EF_AD setting9 mMncLength=" + this.mMncLength);
                                        }
                                    }
                                    if (!(this.mImsi == null || this.mMncLength == 0)) {
                                        log("update mccmnc=" + this.mImsi.substring(0, this.mMncLength + 3));
                                        MccTable.updateMccMncConfiguration(this.mContext, this.mImsi.substring(0, this.mMncLength + 3), false);
                                    }
                                    if (this.mMncLength != 0 && this.mMncLength != -1) {
                                        this.mSimLockMccMncSize = this.mMncLength + 3;
                                        break;
                                    } else {
                                        this.mSimLockMccMncSize = 0;
                                        break;
                                    }
                                }
                            }
                        } catch (Throwable th) {
                            if ((this.mMncLength == -1 || this.mMncLength == 0 || this.mMncLength == 2) && this.mImsi != null && this.mImsi.length() >= 6) {
                                String mccmncCode6 = this.mImsi.substring(0, 6);
                                log("mccmncCode=" + mccmncCode6);
                                String[] arr$6 = MCCMNC_CODES_HAVING_3DIGITS_MNC;
                                int len$6 = arr$6.length;
                                int i$6 = 0;
                                while (true) {
                                    if (i$6 < len$6) {
                                        if (arr$6[i$6].equals(mccmncCode6)) {
                                            this.mMncLength = 3;
                                            log("setting6 mMncLength=" + this.mMncLength);
                                        } else {
                                            i$6++;
                                        }
                                    }
                                }
                            }
                            if (this.mMncLength == 0 || this.mMncLength == -1) {
                                if (this.mImsi != null) {
                                    try {
                                        this.mMncLength = MccTable.smallestDigitsMccForMnc(Integer.parseInt(this.mImsi.substring(0, 3)));
                                        log("setting7 mMncLength=" + this.mMncLength);
                                    } catch (NumberFormatException e6) {
                                        this.mMncLength = 0;
                                        loge("Corrupt IMSI! setting8 mMncLength=" + this.mMncLength);
                                    }
                                } else {
                                    this.mMncLength = 0;
                                    log("MNC length not present in EF_AD setting9 mMncLength=" + this.mMncLength);
                                }
                            }
                            if (!(this.mImsi == null || this.mMncLength == 0)) {
                                log("update mccmnc=" + this.mImsi.substring(0, this.mMncLength + 3));
                                MccTable.updateMccMncConfiguration(this.mContext, this.mImsi.substring(0, this.mMncLength + 3), false);
                            }
                            if (this.mMncLength == 0 || this.mMncLength == -1) {
                                this.mSimLockMccMncSize = 0;
                            } else {
                                this.mSimLockMccMncSize = this.mMncLength + 3;
                            }
                            throw th;
                        }
                        break;
                    case 10:
                        isRecordLoadResponse = true;
                        AsyncResult ar8 = (AsyncResult) msg.obj;
                        if (ar8.exception != null) {
                            log("Invalid or missing EF[MSISDN]");
                            break;
                        } else {
                            AdnRecord adn2 = (AdnRecord) ar8.result;
                            this.mMsisdn = adn2.getNumber();
                            this.mMsisdnTag = adn2.getAlphaTag();
                            log("MSISDN: xxxxxxx");
                            if (this.mSimRefresh) {
                                this.mSimRefresh = false;
                                Intent intent = new Intent("jp.co.sharp.android.telephony.action.REFRESHED_MSISDN_LOADED");
                                log("Broadcasting intent ACTION_REFRESHED_MSISDN_LOADED");
                                ActivityManagerNative.broadcastStickyIntent(intent, "android.permission.READ_PHONE_STATE", -1);
                                break;
                            }
                        }
                        break;
                    case 12:
                        isRecordLoadResponse = true;
                        getSpnFsm(false, (AsyncResult) msg.obj);
                        break;
                    case 13:
                        isRecordLoadResponse = true;
                        AsyncResult ar9 = (AsyncResult) msg.obj;
                        byte[] data6 = (byte[]) ar9.result;
                        if (ar9.exception == null) {
                            parseEfSpdi(data6);
                            break;
                        }
                        break;
                    case 14:
                        AsyncResult ar10 = (AsyncResult) msg.obj;
                        if (ar10.exception != null) {
                            logw("update failed. ", ar10.exception);
                            break;
                        }
                        break;
                    case 15:
                        isRecordLoadResponse = true;
                        AsyncResult ar11 = (AsyncResult) msg.obj;
                        byte[] data7 = (byte[]) ar11.result;
                        if (ar11.exception == null) {
                            SimTlv tlv = new SimTlv(data7, 0, data7.length);
                            while (true) {
                                if (!tlv.isValidObject()) {
                                    break;
                                } else if (tlv.getTag() == TAG_FULL_NETWORK_NAME) {
                                    this.mPnnHomeName = IccUtils.networkNameToString(tlv.getData(), 0, tlv.getData().length);
                                    break;
                                } else {
                                    tlv.nextObject();
                                }
                            }
                        }
                        break;
                    case 17:
                        isRecordLoadResponse = true;
                        AsyncResult ar12 = (AsyncResult) msg.obj;
                        byte[] data8 = (byte[]) ar12.result;
                        if (ar12.exception == null) {
                            this.mUsimServiceTable = new UsimServiceTable(data8);
                            log("SST: " + this.mUsimServiceTable);
                            break;
                        }
                        break;
                    case 18:
                        isRecordLoadResponse = true;
                        AsyncResult ar13 = (AsyncResult) msg.obj;
                        if (ar13.exception == null) {
                            handleSmses((ArrayList) ar13.result);
                            break;
                        }
                        break;
                    case 19:
                        Rlog.i("ENF", "marked read: sms " + msg.arg1);
                        break;
                    case 20:
                        isRecordLoadResponse = false;
                        AsyncResult ar14 = (AsyncResult) msg.obj;
                        log("EVENT_SET_MBDN_DONE ex:" + ar14.exception);
                        if (ar14.exception == null) {
                            this.mVoiceMailNum = this.mNewVoiceMailNum;
                            this.mVoiceMailTag = this.mNewVoiceMailTag;
                        }
                        if (!isCphsMailboxEnabled()) {
                            if (ar14.userObj != null) {
                                Resources resource = Resources.getSystem();
                                if (ar14.exception == null || !resource.getBoolean(17957007)) {
                                    AsyncResult.forMessage((Message) ar14.userObj).exception = ar14.exception;
                                } else {
                                    AsyncResult.forMessage((Message) ar14.userObj).exception = new IccVmNotSupportedException("Update SIM voice mailbox error");
                                }
                                ((Message) ar14.userObj).sendToTarget();
                                break;
                            }
                        } else {
                            AdnRecord adn3 = new AdnRecord(this.mVoiceMailTag, this.mVoiceMailNum);
                            Message onCphsCompleted = (Message) ar14.userObj;
                            if (ar14.exception == null && ar14.userObj != null) {
                                AsyncResult.forMessage((Message) ar14.userObj).exception = null;
                                ((Message) ar14.userObj).sendToTarget();
                                log("Callback with MBDN successful.");
                                onCphsCompleted = null;
                            }
                            new AdnRecordLoader(this.mFh).updateEF(adn3, IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, null, obtainMessage(25, onCphsCompleted));
                            break;
                        }
                        break;
                    case 22:
                        isRecordLoadResponse = false;
                        AsyncResult ar15 = (AsyncResult) msg.obj;
                        if (ar15.exception == null) {
                            handleSms((byte[]) ar15.result);
                            break;
                        } else {
                            loge("Error on GET_SMS with exp " + ar15.exception);
                            break;
                        }
                    case 24:
                        isRecordLoadResponse = true;
                        AsyncResult ar16 = (AsyncResult) msg.obj;
                        byte[] data9 = (byte[]) ar16.result;
                        if (ar16.exception == null) {
                            log("EF_CFF_CPHS: " + IccUtils.bytesToHexString(data9));
                            this.mEfCff = data9;
                            if (!validEfCfis(this.mEfCfis)) {
                                this.mCallForwardingEnabled = (data9[0] & 15) == 10;
                                this.mRecordsEventsRegistrants.notifyResult(1);
                                break;
                            } else {
                                log("EVENT_GET_CFF_DONE: EF_CFIS is valid, ignoring EF_CFF_CPHS");
                                break;
                            }
                        }
                        break;
                    case 25:
                        isRecordLoadResponse = false;
                        AsyncResult ar17 = (AsyncResult) msg.obj;
                        if (ar17.exception == null) {
                            this.mVoiceMailNum = this.mNewVoiceMailNum;
                            this.mVoiceMailTag = this.mNewVoiceMailTag;
                        } else {
                            log("Set CPHS MailBox with exception: " + ar17.exception);
                        }
                        if (ar17.userObj != null) {
                            log("Callback with CPHS MB successful.");
                            AsyncResult.forMessage((Message) ar17.userObj).exception = ar17.exception;
                            ((Message) ar17.userObj).sendToTarget();
                            break;
                        }
                        break;
                    case 26:
                        isRecordLoadResponse = true;
                        AsyncResult ar18 = (AsyncResult) msg.obj;
                        if (ar18.exception == null) {
                            this.mCphsInfo = (byte[]) ar18.result;
                            log("iCPHS: " + IccUtils.bytesToHexString(this.mCphsInfo));
                            break;
                        }
                        break;
                    case 30:
                        isRecordLoadResponse = false;
                        AsyncResult ar19 = (AsyncResult) msg.obj;
                        if (ar19.exception == null) {
                            this.mMsisdn = this.mNewMsisdn;
                            this.mMsisdnTag = this.mNewMsisdnTag;
                            log("Success to update EF[MSISDN]");
                        }
                        if (ar19.userObj != null) {
                            AsyncResult.forMessage((Message) ar19.userObj).exception = ar19.exception;
                            ((Message) ar19.userObj).sendToTarget();
                            break;
                        }
                        break;
                    case 32:
                        isRecordLoadResponse = true;
                        AsyncResult ar20 = (AsyncResult) msg.obj;
                        byte[] data10 = (byte[]) ar20.result;
                        if (ar20.exception == null) {
                            log("EF_CFIS: " + IccUtils.bytesToHexString(data10));
                            if (validEfCfis(data10)) {
                                this.mEfCfis = data10;
                                this.mCallForwardingEnabled = (data10[1] & 1) != 0;
                                log("EF_CFIS: callForwardingEnabled=" + this.mCallForwardingEnabled);
                                this.mRecordsEventsRegistrants.notifyResult(1);
                                break;
                            } else {
                                log("EF_CFIS: invalid data=" + IccUtils.bytesToHexString(data10));
                                break;
                            }
                        }
                        break;
                    case 33:
                        isRecordLoadResponse = true;
                        AsyncResult ar21 = (AsyncResult) msg.obj;
                        if (ar21.exception != null) {
                            loge("Exception in fetching EF_CSP data " + ar21.exception);
                            break;
                        } else {
                            byte[] data11 = (byte[]) ar21.result;
                            log("EF_CSP: " + IccUtils.bytesToHexString(data11));
                            handleEfCspData(data11);
                            break;
                        }
                    case 34:
                        isRecordLoadResponse = true;
                        AsyncResult ar22 = (AsyncResult) msg.obj;
                        byte[] data12 = (byte[]) ar22.result;
                        if (ar22.exception != null) {
                            loge("Exception in get GID1 " + ar22.exception);
                            this.mGid1 = null;
                            this.mSimlockGid1 = null;
                            break;
                        } else {
                            this.mGid1 = IccUtils.bytesToHexString(data12);
                            this.mSimlockGid1 = IccUtils.bytesToHexString(data12);
                            log("GID1: " + this.mGid1);
                            break;
                        }
                    case 35:
                        onLocked();
                        break;
                    case EVENT_SUBSCRIPTION_PERSO /* 101 */:
                        this.mFh.loadEFTransparent(28423, 0, obtainMessage(EVENT_GET_IMSI_SUBSCRIPTION_PERSO_DONE));
                        this.mFh.loadEFTransparent(IccConstants.EF_AD, obtainMessage(EVENT_GET_AD_SUBSCRIPTION_PERSO_DONE));
                        this.mFh.loadEFTransparent(IccConstants.EF_GID1, obtainMessage(EVENT_GET_GID1_PERSO_DONE));
                        break;
                    case EVENT_GET_IMSI_SUBSCRIPTION_PERSO_DONE /* 102 */:
                        log("EVENT_GET_IMSI_SUBSCRIPTION_PERSO_DONE");
                        AsyncResult ar23 = (AsyncResult) msg.obj;
                        byte[] data13 = (byte[]) ar23.result;
                        if (ar23.exception != null) {
                            loge("Exception querying IMSI on SIM Lock, Exception:" + ar23.exception);
                            break;
                        } else {
                            if (data13 != null) {
                                byte b = data13[0] < 8 ? data13[0] : (byte) 8;
                                String tmpImsi = "";
                                for (int i = 1; i < b + 1; i++) {
                                    int digit = data13[i] & 15;
                                    if (digit != 15) {
                                        tmpImsi = tmpImsi + digit;
                                    }
                                    int digit2 = (data13[i] >>> 4) & 15;
                                    if (digit2 != 15) {
                                        tmpImsi = tmpImsi + digit2;
                                    }
                                }
                                this.mSimLockImsi = tmpImsi.substring(1);
                            }
                            if (this.mSimLockImsi != null && (this.mSimLockImsi.length() < 6 || this.mSimLockImsi.length() > 15)) {
                                loge("invalid IMSI(SIM Lock) " + this.mSimLockImsi);
                                this.mSimLockImsi = null;
                                break;
                            }
                        }
                        break;
                    case EVENT_GET_AD_SUBSCRIPTION_PERSO_DONE /* 103 */:
                        log("EVENT_GET_AD_SUBSCRIPTION_PERSO_DONE");
                        AsyncResult ar24 = (AsyncResult) msg.obj;
                        byte[] data14 = (byte[]) ar24.result;
                        if (ar24.exception != null || data14 == null || data14.length < 4) {
                            this.mSimLockMccMncSize = 0;
                            loge("invalid EF_AD");
                            break;
                        } else {
                            log("EF_AD: " + IccUtils.bytesToHexString(data14));
                            int mncSize = data14[3] & 15;
                            if (mncSize == 15) {
                                mncSize = 0;
                            }
                            if ((mncSize == -1 || mncSize == 0 || mncSize == 2) && this.mSimLockImsi != null && this.mSimLockImsi.length() >= 6) {
                                String mccmncCode7 = this.mSimLockImsi.substring(0, 6);
                                String[] arr$7 = MCCMNC_CODES_HAVING_3DIGITS_MNC;
                                int len$7 = arr$7.length;
                                int i$7 = 0;
                                while (true) {
                                    if (i$7 < len$7) {
                                        if (arr$7[i$7].equals(mccmncCode7)) {
                                            mncSize = 3;
                                        } else {
                                            i$7++;
                                        }
                                    }
                                }
                            }
                            if (mncSize == 0 || mncSize == -1) {
                                if (this.mSimLockImsi != null) {
                                    try {
                                        mncSize = MccTable.smallestDigitsMccForMnc(Integer.parseInt(this.mSimLockImsi.substring(0, 3)));
                                    } catch (NumberFormatException e7) {
                                        mncSize = 0;
                                        loge("Corrupt IMSI!");
                                    }
                                } else {
                                    mncSize = 0;
                                    log("MNC length not present in EF_AD");
                                }
                            }
                            if (mncSize != 0 && mncSize != -1) {
                                this.mSimLockMccMncSize = mncSize + 3;
                                break;
                            } else {
                                this.mSimLockMccMncSize = 0;
                                break;
                            }
                        }
                        break;
                    case EVENT_GET_GID1_PERSO_DONE /* 104 */:
                        AsyncResult ar25 = (AsyncResult) msg.obj;
                        byte[] data15 = (byte[]) ar25.result;
                        if (ar25.exception != null) {
                            loge("Exception in get GID1 " + ar25.exception);
                            this.mSimlockGid1 = null;
                            break;
                        } else {
                            this.mSimlockGid1 = IccUtils.bytesToHexString(data15);
                            log("GID1: " + this.mSimlockGid1);
                            break;
                        }
                    default:
                        if (31 == msg.what) {
                            this.mSimRefresh = true;
                        }
                        super.handleMessage(msg);
                        break;
                }
                if (isRecordLoadResponse) {
                    onRecordLoaded();
                }
            } catch (RuntimeException exc) {
                logw("Exception parsing SIM record", exc);
                if (0 != 0) {
                    onRecordLoaded();
                }
            }
        } catch (Throwable th2) {
            if (0 != 0) {
                onRecordLoaded();
            }
            throw th2;
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class EfPlLoaded implements IccRecords.IccRecordLoaded {
        private EfPlLoaded() {
            SIMRecords.this = r1;
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_PL";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            SIMRecords.this.mEfPl = (byte[]) ar.result;
            SIMRecords.this.log("EF_PL=" + IccUtils.bytesToHexString(SIMRecords.this.mEfPl));
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class EfUsimLiLoaded implements IccRecords.IccRecordLoaded {
        private EfUsimLiLoaded() {
            SIMRecords.this = r1;
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_LI";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            SIMRecords.this.mEfLi = (byte[]) ar.result;
            SIMRecords.this.log("EF_LI=" + IccUtils.bytesToHexString(SIMRecords.this.mEfLi));
        }
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void handleFileUpdate(int efid) {
        switch (efid) {
            case IccConstants.EF_LOCK /* 12272 */:
                if (TelBrand.IS_DCM) {
                    log("SIM Refresh for EF_LOCK");
                    Intent intent = new Intent("jp.co.sharp.android.telephony.action.SIM_REMOTE_NFCLOCK_CHANGED");
                    intent.addFlags(536870912);
                    log("Broadcasting intent ACTION_SIM_REMOTE_NFCLOCK_CHANGED");
                    ActivityManagerNative.broadcastStickyIntent(intent, "android.permission.READ_PHONE_STATE", -1);
                    return;
                }
                return;
            case IccConstants.EF_CFF_CPHS /* 28435 */:
                this.mRecordsToLoad++;
                log("SIM Refresh called for EF_CFF_CPHS");
                this.mFh.loadEFTransparent(IccConstants.EF_CFF_CPHS, obtainMessage(24));
                return;
            case IccConstants.EF_CSP_CPHS /* 28437 */:
                this.mRecordsToLoad++;
                log("[CSP] SIM Refresh for EF_CSP_CPHS");
                this.mFh.loadEFTransparent(IccConstants.EF_CSP_CPHS, obtainMessage(33));
                return;
            case IccConstants.EF_MAILBOX_CPHS /* 28439 */:
                this.mRecordsToLoad++;
                new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, obtainMessage(11));
                return;
            case 28474:
                log("SIM Refresh for EF_ADN");
                this.mAdnCache.reset();
                return;
            case IccConstants.EF_FDN /* 28475 */:
                log("SIM Refresh called for EF_FDN");
                this.mParentApp.queryFdn();
                break;
            case IccConstants.EF_MSISDN /* 28480 */:
                break;
            case IccConstants.EF_MBDN /* 28615 */:
                this.mRecordsToLoad++;
                new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MBDN, IccConstants.EF_EXT6, this.mMailboxIndex, obtainMessage(6));
                return;
            case IccConstants.EF_CFIS /* 28619 */:
                this.mRecordsToLoad++;
                log("SIM Refresh called for EF_CFIS");
                this.mFh.loadEFLinearFixed(IccConstants.EF_CFIS, 1, obtainMessage(32));
                return;
            default:
                this.mAdnCache.reset();
                fetchSimRecords();
                return;
        }
        this.mRecordsToLoad++;
        log("SIM Refresh called for EF_MSISDN");
        new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MSISDN, getExtFromEf(IccConstants.EF_MSISDN), 1, obtainMessage(10));
    }

    private int dispatchGsmMessage(SmsMessage message) {
        this.mNewSmsRegistrants.notifyResult(message);
        return 0;
    }

    public void handleSmsOnIcc(AsyncResult ar) {
        int[] index = (int[]) ar.result;
        if (ar.exception == null && index.length == 1) {
            log("READ EF_SMS RECORD index= " + index[0]);
            this.mFh.loadEFLinearFixed(IccConstants.EF_SMS, index[0], obtainMessage(22));
            return;
        }
        loge(" Error on SMS_ON_SIM with exp " + ar.exception + " length " + index.length);
    }

    private void handleSms(byte[] ba) {
        if (ba[0] != 0) {
            Rlog.d("ENF", "status : " + ((int) ba[0]));
        }
        if (ba[0] == 3) {
            int n = ba.length;
            byte[] pdu = new byte[n - 1];
            System.arraycopy(ba, 1, pdu, 0, n - 1);
            dispatchGsmMessage(SmsMessage.createFromPdu(pdu, SmsMessage.FORMAT_3GPP));
        }
    }

    private void handleSmses(ArrayList<byte[]> messages) {
        int count = messages.size();
        for (int i = 0; i < count; i++) {
            byte[] ba = messages.get(i);
            if (ba[0] != 0) {
                Rlog.i("ENF", "status " + i + ": " + ((int) ba[0]));
            }
            if (ba[0] == 3) {
                int n = ba.length;
                byte[] pdu = new byte[n - 1];
                System.arraycopy(ba, 1, pdu, 0, n - 1);
                dispatchGsmMessage(SmsMessage.createFromPdu(pdu, SmsMessage.FORMAT_3GPP));
                ba[0] = 1;
            }
        }
    }

    private String findBestLanguage(byte[] languages) {
        String[] locales = this.mContext.getAssets().getLocales();
        if (languages == null || locales == null) {
            return null;
        }
        for (int i = 0; i + 1 < languages.length; i += 2) {
            try {
                String lang = new String(languages, i, 2, "ISO-8859-1");
                log("languages from sim = " + lang);
                for (int j = 0; j < locales.length; j++) {
                    if (locales[j] != null && locales[j].length() >= 2 && locales[j].substring(0, 2).equalsIgnoreCase(lang)) {
                        return lang;
                    }
                }
            } catch (UnsupportedEncodingException e) {
                log("Failed to parse USIM language records" + e);
            }
            if (0 != 0) {
                break;
            }
        }
        return null;
    }

    private void setLocaleFromUsim() {
        String prefLang = findBestLanguage(this.mEfLi);
        if (prefLang == null) {
            prefLang = findBestLanguage(this.mEfPl);
        }
        if (prefLang != null) {
            String imsi = getIMSI();
            String country = null;
            if (imsi != null) {
                country = MccTable.countryCodeForMcc(Integer.parseInt(imsi.substring(0, 3)));
            }
            log("Setting locale to " + prefLang + "_" + country);
            MccTable.setSystemLocale(this.mContext, prefLang, country);
            return;
        }
        log("No suitable USIM selected locale");
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void onRecordLoaded() {
        this.mRecordsToLoad--;
        log("onRecordLoaded " + this.mRecordsToLoad + " requested: " + this.mRecordsRequested);
        if (this.mRecordsToLoad == 0 && this.mRecordsRequested) {
            onAllRecordsLoaded();
        } else if (this.mRecordsToLoad < 0) {
            loge("recordsToLoad <0, programmer error suspected");
            this.mRecordsToLoad = 0;
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void onAllRecordsLoaded() {
        log("record load complete");
        setLocaleFromUsim();
        if (this.mParentApp.getState() == IccCardApplicationStatus.AppState.APPSTATE_PIN || this.mParentApp.getState() == IccCardApplicationStatus.AppState.APPSTATE_PUK) {
            this.mRecordsRequested = false;
            return;
        }
        String operator = getOperatorNumeric();
        if (!TextUtils.isEmpty(operator)) {
            log("onAllRecordsLoaded set 'gsm.sim.operator.numeric' to operator='" + operator + "'");
            log("update icc_operator_numeric=" + operator);
            setSystemProperty("gsm.sim.operator.numeric", operator);
            SubscriptionController subController = SubscriptionController.getInstance();
            subController.setMccMnc(operator, subController.getDefaultSmsSubId());
        } else {
            log("onAllRecordsLoaded empty 'gsm.sim.operator.numeric' skipping");
        }
        if (!TextUtils.isEmpty(this.mImsi)) {
            log("onAllRecordsLoaded set mcc imsi = xxxxxx");
            setSystemProperty("gsm.sim.operator.iso-country", MccTable.countryCodeForMcc(Integer.parseInt(this.mImsi.substring(0, 3))));
        } else {
            log("onAllRecordsLoaded empty imsi skipping setting mcc");
        }
        setVoiceMailByCountry(operator);
        setSpnFromConfig(operator);
        if (isAppStateReady()) {
            this.mRecordsLoadedRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Object) null, (Throwable) null));
        } else {
            log("onAllRecordsLoaded: AppState is not ready; not notifying the registrants");
        }
    }

    private void setSpnFromConfig(String carrier) {
        if (this.mSpnOverride.containsCarrier(carrier)) {
            setServiceProviderName(this.mSpnOverride.getSpn(carrier));
            setSystemProperty("gsm.sim.operator.alpha", getServiceProviderName());
        }
    }

    private void setVoiceMailByCountry(String spn) {
        if (this.mVmConfig.containsCarrier(spn)) {
            this.mIsVoiceMailFixed = true;
            this.mVoiceMailNum = this.mVmConfig.getVoiceMailNumber(spn);
            this.mVoiceMailTag = this.mVmConfig.getVoiceMailTag(spn);
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void onReady() {
        fetchSimRecords();
    }

    private void onLocked() {
        log("only fetch EF_LI and EF_PL in lock state");
        loadEfLiAndEfPl();
    }

    private void loadEfLiAndEfPl() {
        if (!Resources.getSystem().getBoolean(17957041)) {
            log("Not using EF LI/EF PL");
        } else if (this.mParentApp.getType() == IccCardApplicationStatus.AppType.APPTYPE_USIM) {
            this.mRecordsRequested = true;
            this.mFh.loadEFTransparent(IccConstants.EF_LI, obtainMessage(100, new EfUsimLiLoaded()));
            this.mRecordsToLoad++;
            this.mFh.loadEFTransparent(IccConstants.EF_PL, obtainMessage(100, new EfPlLoaded()));
            this.mRecordsToLoad++;
        }
    }

    protected void fetchSimRecords() {
        this.mRecordsRequested = true;
        log("fetchSimRecords " + this.mRecordsToLoad);
        this.mCi.getIMSIForApp(this.mParentApp.getAid(), obtainMessage(3));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_ICCID, obtainMessage(4));
        this.mRecordsToLoad++;
        new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MSISDN, getExtFromEf(IccConstants.EF_MSISDN), 1, obtainMessage(10));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixed(IccConstants.EF_MBI, 1, obtainMessage(5));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_AD, obtainMessage(9));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixed(IccConstants.EF_MWIS, 1, obtainMessage(7));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_VOICE_MAIL_INDICATOR_CPHS, obtainMessage(8));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixed(IccConstants.EF_CFIS, 1, obtainMessage(32));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_CFF_CPHS, obtainMessage(24));
        this.mRecordsToLoad++;
        getSpnFsm(true, null);
        this.mFh.loadEFTransparent(IccConstants.EF_SPDI, obtainMessage(13));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixed(IccConstants.EF_PNN, 1, obtainMessage(15));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_SST, obtainMessage(17));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_INFO_CPHS, obtainMessage(26));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_CSP_CPHS, obtainMessage(33));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_GID1, obtainMessage(34));
        this.mRecordsToLoad++;
        loadEfLiAndEfPl();
        this.mFh.getEFLinearRecordSize(IccConstants.EF_SMS, obtainMessage(28));
        log("fetchSimRecords " + this.mRecordsToLoad + " requested: " + this.mRecordsRequested);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public int getDisplayRule(String plmn) {
        int rule;
        if (this.mContext != null && this.mContext.getResources().getBoolean(17957045)) {
            return 1;
        }
        if (this.mParentApp != null && this.mParentApp.getUiccCard() != null && this.mParentApp.getUiccCard().getOperatorBrandOverride() != null) {
            rule = 2;
        } else if (TextUtils.isEmpty(getServiceProviderName()) || this.mSpnDisplayCondition == -1) {
            rule = 2;
        } else if (isOnMatchingPlmn(plmn)) {
            rule = 1;
            if ((this.mSpnDisplayCondition & 1) == 1) {
                rule = 1 | 2;
            }
        } else {
            rule = 2;
            if ((this.mSpnDisplayCondition & 2) == 0) {
                rule = 2 | 1;
            }
        }
        return rule;
    }

    private boolean isOnMatchingPlmn(String plmn) {
        if (plmn == null) {
            return false;
        }
        if (plmn.equals(getOperatorNumeric())) {
            return true;
        }
        if (this.mSpdiNetworks == null) {
            return false;
        }
        Iterator i$ = this.mSpdiNetworks.iterator();
        while (i$.hasNext()) {
            if (plmn.equals(i$.next())) {
                return true;
            }
        }
        return false;
    }

    private void getSpnFsm(boolean start, AsyncResult ar) {
        if (start) {
            if (this.mSpnState == GetSpnFsmState.READ_SPN_3GPP || this.mSpnState == GetSpnFsmState.READ_SPN_CPHS || this.mSpnState == GetSpnFsmState.READ_SPN_SHORT_CPHS || this.mSpnState == GetSpnFsmState.INIT) {
                this.mSpnState = GetSpnFsmState.INIT;
                return;
            }
            this.mSpnState = GetSpnFsmState.INIT;
        }
        switch (this.mSpnState) {
            case INIT:
                setServiceProviderName(null);
                this.mFh.loadEFTransparent(IccConstants.EF_SPN, obtainMessage(12));
                this.mRecordsToLoad++;
                this.mSpnState = GetSpnFsmState.READ_SPN_3GPP;
                return;
            case READ_SPN_3GPP:
                if (ar == null || ar.exception != null) {
                    this.mFh.loadEFTransparent(IccConstants.EF_SPN_CPHS, obtainMessage(12));
                    this.mRecordsToLoad++;
                    this.mSpnState = GetSpnFsmState.READ_SPN_CPHS;
                    this.mSpnDisplayCondition = -1;
                    return;
                }
                byte[] data = (byte[]) ar.result;
                this.mSpnDisplayCondition = data[0] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
                setServiceProviderName(IccUtils.adnStringFieldToString(data, 1, data.length - 1));
                log("Load EF_SPN: " + getServiceProviderName() + " spnDisplayCondition: " + this.mSpnDisplayCondition);
                setSystemProperty("gsm.sim.operator.alpha", getServiceProviderName());
                this.mSpnState = GetSpnFsmState.IDLE;
                return;
            case READ_SPN_CPHS:
                if (ar == null || ar.exception != null) {
                    this.mFh.loadEFTransparent(IccConstants.EF_SPN_SHORT_CPHS, obtainMessage(12));
                    this.mRecordsToLoad++;
                    this.mSpnState = GetSpnFsmState.READ_SPN_SHORT_CPHS;
                    return;
                }
                byte[] data2 = (byte[]) ar.result;
                setServiceProviderName(IccUtils.adnStringFieldToString(data2, 0, data2.length));
                log("Load EF_SPN_CPHS: " + getServiceProviderName());
                setSystemProperty("gsm.sim.operator.alpha", getServiceProviderName());
                this.mSpnState = GetSpnFsmState.IDLE;
                return;
            case READ_SPN_SHORT_CPHS:
                if (ar == null || ar.exception != null) {
                    log("No SPN loaded in either CHPS or 3GPP");
                } else {
                    byte[] data3 = (byte[]) ar.result;
                    setServiceProviderName(IccUtils.adnStringFieldToString(data3, 0, data3.length));
                    log("Load EF_SPN_SHORT_CPHS: " + getServiceProviderName());
                    setSystemProperty("gsm.sim.operator.alpha", getServiceProviderName());
                }
                this.mSpnState = GetSpnFsmState.IDLE;
                return;
            default:
                this.mSpnState = GetSpnFsmState.IDLE;
                return;
        }
    }

    private void parseEfSpdi(byte[] data) {
        SimTlv tlv = new SimTlv(data, 0, data.length);
        byte[] plmnEntries = null;
        while (true) {
            if (!tlv.isValidObject()) {
                break;
            }
            if (tlv.getTag() == 163) {
                tlv = new SimTlv(tlv.getData(), 0, tlv.getData().length);
            }
            if (tlv.getTag() == 128) {
                plmnEntries = tlv.getData();
                break;
            }
            tlv.nextObject();
        }
        if (plmnEntries != null) {
            this.mSpdiNetworks = new ArrayList<>(plmnEntries.length / 3);
            for (int i = 0; i + 2 < plmnEntries.length; i += 3) {
                String plmnCode = IccUtils.bcdToString(plmnEntries, i, 3);
                if (plmnCode.length() >= 5) {
                    log("EF_SPDI network: " + plmnCode);
                    this.mSpdiNetworks.add(plmnCode);
                }
            }
        }
    }

    private boolean isCphsMailboxEnabled() {
        boolean z = true;
        if (this.mCphsInfo == null) {
            return false;
        }
        if ((this.mCphsInfo[1] & 48) != 48) {
            z = false;
        }
        return z;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[SIMRecords] " + s);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[SIMRecords] " + s);
    }

    protected void logw(String s, Throwable tr) {
        Rlog.w(LOG_TAG, "[SIMRecords] " + s, tr);
    }

    protected void logv(String s) {
        Rlog.v(LOG_TAG, "[SIMRecords] " + s);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public boolean isCspPlmnEnabled() {
        return this.mCspPlmnEnabled;
    }

    private void handleEfCspData(byte[] data) {
        int usedCspGroups = data.length / 2;
        this.mCspPlmnEnabled = true;
        for (int i = 0; i < usedCspGroups; i++) {
            if (data[i * 2] == -64) {
                log("[CSP] found ValueAddedServicesGroup, value " + ((int) data[(i * 2) + 1]));
                if ((data[(i * 2) + 1] & 128) == 128) {
                    this.mCspPlmnEnabled = true;
                    return;
                }
                this.mCspPlmnEnabled = false;
                log("[CSP] Set Automatic Network Selection");
                this.mNetworkSelectionModeAutomaticRegistrants.notifyRegistrants();
                return;
            }
        }
        log("[CSP] Value Added Service Group (0xC0), not found!");
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("SIMRecords: " + this);
        pw.println(" extends:");
        super.dump(fd, pw, args);
        pw.println(" mVmConfig=" + this.mVmConfig);
        pw.println(" mSpnOverride=" + this.mSpnOverride);
        pw.println(" mCallForwardingEnabled=" + this.mCallForwardingEnabled);
        pw.println(" mSpnState=" + this.mSpnState);
        pw.println(" mCphsInfo=" + this.mCphsInfo);
        pw.println(" mCspPlmnEnabled=" + this.mCspPlmnEnabled);
        pw.println(" mEfMWIS[]=" + Arrays.toString(this.mEfMWIS));
        pw.println(" mEfCPHS_MWI[]=" + Arrays.toString(this.mEfCPHS_MWI));
        pw.println(" mEfCff[]=" + Arrays.toString(this.mEfCff));
        pw.println(" mEfCfis[]=" + Arrays.toString(this.mEfCfis));
        pw.println(" mSpnDisplayCondition=" + this.mSpnDisplayCondition);
        pw.println(" mSpdiNetworks[]=" + this.mSpdiNetworks);
        pw.println(" mPnnHomeName=" + this.mPnnHomeName);
        pw.println(" mUsimServiceTable=" + this.mUsimServiceTable);
        pw.println(" mGid1=" + this.mGid1);
        pw.flush();
    }
}
