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
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

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
    private static final String[] MCCMNC_CODES_HAVING_3DIGITS_MNC = new String[]{"302370", "302720", "310260", "405025", "405026", "405027", "405028", "405029", "405030", "405031", "405032", "405033", "405034", "405035", "405036", "405037", "405038", "405039", "405040", "405041", "405042", "405043", "405044", "405045", "405046", "405047", "405750", "405751", "405752", "405753", "405754", "405755", "405756", "405799", "405800", "405801", "405802", "405803", "405804", "405805", "405806", "405807", "405808", "405809", "405810", "405811", "405812", "405813", "405814", "405815", "405816", "405817", "405818", "405819", "405820", "405821", "405822", "405823", "405824", "405825", "405826", "405827", "405828", "405829", "405830", "405831", "405832", "405833", "405834", "405835", "405836", "405837", "405838", "405839", "405840", "405841", "405842", "405843", "405844", "405845", "405846", "405847", "405848", "405849", "405850", "405851", "405852", "405853", "405875", "405876", "405877", "405878", "405879", "405880", "405881", "405882", "405883", "405884", "405885", "405886", "405908", "405909", "405910", "405911", "405912", "405913", "405914", "405915", "405916", "405917", "405918", "405919", "405920", "405921", "405922", "405923", "405924", "405925", "405926", "405927", "405928", "405929", "405930", "405931", "405932", "502142", "502143", "502145", "502146", "502147", "502148"};
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
    private byte[] mCphsInfo = null;
    boolean mCspPlmnEnabled = true;
    byte[] mEfCPHS_MWI = null;
    byte[] mEfCff = null;
    byte[] mEfCfis = null;
    byte[] mEfLi = null;
    byte[] mEfMWIS = null;
    byte[] mEfPl = null;
    protected final boolean mIsSmartPhone = true;
    String mPnnHomeName = null;
    protected String mSimLockImsi = null;
    protected int mSimLockMccMncSize = -1;
    protected boolean mSimRefresh = false;
    protected String mSimlockGid1 = null;
    ArrayList<String> mSpdiNetworks = null;
    int mSpnDisplayCondition;
    SpnOverride mSpnOverride;
    private GetSpnFsmState mSpnState;
    UsimServiceTable mUsimServiceTable;
    VoiceMailConstants mVmConfig;

    private class EfPlLoaded implements IccRecordLoaded {
        private EfPlLoaded() {
        }

        public String getEfName() {
            return "EF_PL";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            SIMRecords.this.mEfPl = (byte[]) asyncResult.result;
            SIMRecords.this.log("EF_PL=" + IccUtils.bytesToHexString(SIMRecords.this.mEfPl));
        }
    }

    private class EfUsimLiLoaded implements IccRecordLoaded {
        private EfUsimLiLoaded() {
        }

        public String getEfName() {
            return "EF_LI";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            SIMRecords.this.mEfLi = (byte[]) asyncResult.result;
            SIMRecords.this.log("EF_LI=" + IccUtils.bytesToHexString(SIMRecords.this.mEfLi));
        }
    }

    private enum GetSpnFsmState {
        IDLE,
        INIT,
        READ_SPN_3GPP,
        READ_SPN_CPHS,
        READ_SPN_SHORT_CPHS
    }

    public SIMRecords(UiccCardApplication uiccCardApplication, Context context, CommandsInterface commandsInterface) {
        super(uiccCardApplication, context, commandsInterface);
        if (TelBrand.IS_SBM) {
            this.mAdnCache = new AdnRecordCache(this.mFh, uiccCardApplication);
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

    private int dispatchGsmMessage(SmsMessage smsMessage) {
        this.mNewSmsRegistrants.notifyResult(smsMessage);
        return 0;
    }

    private String findBestLanguage(byte[] bArr) {
        String str;
        String[] locales = this.mContext.getAssets().getLocales();
        if (bArr == null || locales == null) {
            str = null;
        } else {
            for (int i = 0; i + 1 < bArr.length; i += 2) {
                try {
                    String str2 = new String(bArr, i, 2, "ISO-8859-1");
                    log("languages from sim = " + str2);
                    int i2 = 0;
                    while (i2 < locales.length) {
                        if (locales[i2] != null) {
                            if (locales[i2].length() >= 2 && locales[i2].substring(0, 2).equalsIgnoreCase(str2)) {
                                str = str2;
                            }
                        }
                        i2++;
                    }
                    continue;
                } catch (UnsupportedEncodingException e) {
                    log("Failed to parse USIM language records" + e);
                }
            }
            return null;
        }
        return str;
    }

    private int getExtFromEf(int i) {
        switch (i) {
            case IccConstants.EF_MSISDN /*28480*/:
                return this.mParentApp.getType() == AppType.APPTYPE_USIM ? IccConstants.EF_EXT5 : IccConstants.EF_EXT1;
            default:
                return IccConstants.EF_EXT1;
        }
    }

    private void getSpnFsm(boolean z, AsyncResult asyncResult) {
        if (z) {
            if (this.mSpnState == GetSpnFsmState.READ_SPN_3GPP || this.mSpnState == GetSpnFsmState.READ_SPN_CPHS || this.mSpnState == GetSpnFsmState.READ_SPN_SHORT_CPHS || this.mSpnState == GetSpnFsmState.INIT) {
                this.mSpnState = GetSpnFsmState.INIT;
                return;
            }
            this.mSpnState = GetSpnFsmState.INIT;
        }
        byte[] bArr;
        switch (this.mSpnState) {
            case INIT:
                setServiceProviderName(null);
                this.mFh.loadEFTransparent(IccConstants.EF_SPN, obtainMessage(12));
                this.mRecordsToLoad++;
                this.mSpnState = GetSpnFsmState.READ_SPN_3GPP;
                return;
            case READ_SPN_3GPP:
                if (asyncResult == null || asyncResult.exception != null) {
                    this.mFh.loadEFTransparent(IccConstants.EF_SPN_CPHS, obtainMessage(12));
                    this.mRecordsToLoad++;
                    this.mSpnState = GetSpnFsmState.READ_SPN_CPHS;
                    this.mSpnDisplayCondition = -1;
                    return;
                }
                bArr = (byte[]) asyncResult.result;
                this.mSpnDisplayCondition = bArr[0] & 255;
                setServiceProviderName(IccUtils.adnStringFieldToString(bArr, 1, bArr.length - 1));
                log("Load EF_SPN: " + getServiceProviderName() + " spnDisplayCondition: " + this.mSpnDisplayCondition);
                setSystemProperty("gsm.sim.operator.alpha", getServiceProviderName());
                this.mSpnState = GetSpnFsmState.IDLE;
                return;
            case READ_SPN_CPHS:
                if (asyncResult == null || asyncResult.exception != null) {
                    this.mFh.loadEFTransparent(IccConstants.EF_SPN_SHORT_CPHS, obtainMessage(12));
                    this.mRecordsToLoad++;
                    this.mSpnState = GetSpnFsmState.READ_SPN_SHORT_CPHS;
                    return;
                }
                bArr = (byte[]) asyncResult.result;
                setServiceProviderName(IccUtils.adnStringFieldToString(bArr, 0, bArr.length));
                log("Load EF_SPN_CPHS: " + getServiceProviderName());
                setSystemProperty("gsm.sim.operator.alpha", getServiceProviderName());
                this.mSpnState = GetSpnFsmState.IDLE;
                return;
            case READ_SPN_SHORT_CPHS:
                if (asyncResult == null || asyncResult.exception != null) {
                    log("No SPN loaded in either CHPS or 3GPP");
                } else {
                    bArr = (byte[]) asyncResult.result;
                    setServiceProviderName(IccUtils.adnStringFieldToString(bArr, 0, bArr.length));
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

    private void handleEfCspData(byte[] bArr) {
        int length = bArr.length / 2;
        this.mCspPlmnEnabled = true;
        for (int i = 0; i < length; i++) {
            if (bArr[i * 2] == (byte) -64) {
                log("[CSP] found ValueAddedServicesGroup, value " + bArr[(i * 2) + 1]);
                if ((bArr[(i * 2) + 1] & 128) == 128) {
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

    private void handleSms(byte[] bArr) {
        if (bArr[0] != (byte) 0) {
            Rlog.d("ENF", "status : " + bArr[0]);
        }
        if (bArr[0] == (byte) 3) {
            int length = bArr.length;
            byte[] bArr2 = new byte[(length - 1)];
            System.arraycopy(bArr, 1, bArr2, 0, length - 1);
            dispatchGsmMessage(SmsMessage.createFromPdu(bArr2, SmsMessage.FORMAT_3GPP));
        }
    }

    private void handleSmses(ArrayList<byte[]> arrayList) {
        int size = arrayList.size();
        for (int i = 0; i < size; i++) {
            byte[] bArr = (byte[]) arrayList.get(i);
            if (bArr[0] != (byte) 0) {
                Rlog.i("ENF", "status " + i + ": " + bArr[0]);
            }
            if (bArr[0] == (byte) 3) {
                int length = bArr.length;
                byte[] bArr2 = new byte[(length - 1)];
                System.arraycopy(bArr, 1, bArr2, 0, length - 1);
                dispatchGsmMessage(SmsMessage.createFromPdu(bArr2, SmsMessage.FORMAT_3GPP));
                bArr[0] = (byte) 1;
            }
        }
    }

    private boolean isBootstrapIMSI(String str) {
        return isInRange(str, NW_SUBSET_BOOTSTRAP_SP_MIN, NW_SUBSET_BOOTSTRAP_SP_MAX);
    }

    private boolean isCphsMailboxEnabled() {
        return this.mCphsInfo != null && (this.mCphsInfo[1] & 48) == 48;
    }

    private boolean isInRange(String str, String str2, String str3) {
        if (str.length() >= str2.length() && str.length() >= str3.length()) {
            return Integer.parseInt(str2) <= Integer.parseInt(str.substring(0, str2.length())) && Integer.parseInt(str.substring(0, str3.length())) <= Integer.parseInt(str3);
        } else {
            loge("isInRange() : IMSI is too short");
            return false;
        }
    }

    private boolean isOnMatchingPlmn(String str) {
        if (str != null) {
            if (str.equals(getOperatorNumeric())) {
                return true;
            }
            if (this.mSpdiNetworks != null) {
                Iterator it = this.mSpdiNetworks.iterator();
                while (it.hasNext()) {
                    if (str.equals((String) it.next())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isTestIMSI(String str) {
        return isInRange(str, NW_SUBSET_TEST_MIN, NW_SUBSET_TEST_MAX);
    }

    private void loadEfLiAndEfPl() {
        if (!Resources.getSystem().getBoolean(17957041)) {
            log("Not using EF LI/EF PL");
        } else if (this.mParentApp.getType() == AppType.APPTYPE_USIM) {
            this.mRecordsRequested = true;
            this.mFh.loadEFTransparent(IccConstants.EF_LI, obtainMessage(100, new EfUsimLiLoaded()));
            this.mRecordsToLoad++;
            this.mFh.loadEFTransparent(IccConstants.EF_PL, obtainMessage(100, new EfPlLoaded()));
            this.mRecordsToLoad++;
        }
    }

    private void onLocked() {
        log("only fetch EF_LI and EF_PL in lock state");
        loadEfLiAndEfPl();
    }

    private void parseEfSpdi(byte[] bArr) {
        byte[] data;
        SimTlv simTlv = new SimTlv(bArr, 0, bArr.length);
        while (simTlv.isValidObject()) {
            if (simTlv.getTag() == 163) {
                simTlv = new SimTlv(simTlv.getData(), 0, simTlv.getData().length);
            }
            if (simTlv.getTag() == 128) {
                data = simTlv.getData();
                break;
            }
            simTlv.nextObject();
        }
        data = null;
        if (data != null) {
            this.mSpdiNetworks = new ArrayList(data.length / 3);
            for (int i = 0; i + 2 < data.length; i += 3) {
                String bcdToString = IccUtils.bcdToString(data, i, 3);
                if (bcdToString.length() >= 5) {
                    log("EF_SPDI network: " + bcdToString);
                    this.mSpdiNetworks.add(bcdToString);
                }
            }
        }
    }

    private void setLocaleFromUsim() {
        String findBestLanguage = findBestLanguage(this.mEfLi);
        String findBestLanguage2 = findBestLanguage == null ? findBestLanguage(this.mEfPl) : findBestLanguage;
        if (findBestLanguage2 != null) {
            String imsi = getIMSI();
            findBestLanguage = null;
            if (imsi != null) {
                findBestLanguage = MccTable.countryCodeForMcc(Integer.parseInt(imsi.substring(0, 3)));
            }
            log("Setting locale to " + findBestLanguage2 + "_" + findBestLanguage);
            MccTable.setSystemLocale(this.mContext, findBestLanguage2, findBestLanguage);
            return;
        }
        log("No suitable USIM selected locale");
    }

    private void setSpnFromConfig(String str) {
        if (this.mSpnOverride.containsCarrier(str)) {
            setServiceProviderName(this.mSpnOverride.getSpn(str));
            setSystemProperty("gsm.sim.operator.alpha", getServiceProviderName());
        }
    }

    private void setVoiceMailByCountry(String str) {
        if (this.mVmConfig.containsCarrier(str)) {
            this.mIsVoiceMailFixed = true;
            this.mVoiceMailNum = this.mVmConfig.getVoiceMailNumber(str);
            this.mVoiceMailTag = this.mVmConfig.getVoiceMailTag(str);
        }
    }

    private boolean validEfCfis(byte[] bArr) {
        return bArr != null && bArr[0] >= (byte) 1 && bArr[0] <= (byte) 4;
    }

    public void dispose() {
        log("Disposing SIMRecords this=" + this);
        this.mParentApp.unregisterForReady(this);
        this.mParentApp.unregisterForLocked(this);
        resetRecords();
        super.dispose();
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("SIMRecords: " + this);
        printWriter.println(" extends:");
        super.dump(fileDescriptor, printWriter, strArr);
        printWriter.println(" mVmConfig=" + this.mVmConfig);
        printWriter.println(" mSpnOverride=" + this.mSpnOverride);
        printWriter.println(" mCallForwardingEnabled=" + this.mCallForwardingEnabled);
        printWriter.println(" mSpnState=" + this.mSpnState);
        printWriter.println(" mCphsInfo=" + this.mCphsInfo);
        printWriter.println(" mCspPlmnEnabled=" + this.mCspPlmnEnabled);
        printWriter.println(" mEfMWIS[]=" + Arrays.toString(this.mEfMWIS));
        printWriter.println(" mEfCPHS_MWI[]=" + Arrays.toString(this.mEfCPHS_MWI));
        printWriter.println(" mEfCff[]=" + Arrays.toString(this.mEfCff));
        printWriter.println(" mEfCfis[]=" + Arrays.toString(this.mEfCfis));
        printWriter.println(" mSpnDisplayCondition=" + this.mSpnDisplayCondition);
        printWriter.println(" mSpdiNetworks[]=" + this.mSpdiNetworks);
        printWriter.println(" mPnnHomeName=" + this.mPnnHomeName);
        printWriter.println(" mUsimServiceTable=" + this.mUsimServiceTable);
        printWriter.println(" mGid1=" + this.mGid1);
        printWriter.flush();
    }

    /* Access modifiers changed, original: protected */
    public void fetchSimRecords() {
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

    /* Access modifiers changed, original: protected */
    public void finalize() {
        log("finalized");
    }

    public int getBrand() {
        int i;
        String imsiOnSimLock = getImsiOnSimLock();
        Object substring = imsiOnSimLock != null ? imsiOnSimLock.substring(0, 7) : null;
        Object substring2 = this.mSimlockGid1 != null ? this.mSimlockGid1.substring(0, 2) : SP_CODE_DEFAULT;
        if (imsiOnSimLock == null) {
            i = 0;
        } else if (isBootstrapIMSI(imsiOnSimLock)) {
            i = 2;
        } else if (NW_SUBSET_SOFTBANK.equals(substring)) {
            i = 2;
        } else if (!NW_SUBSET_SOFTBANK_GROUP.equals(substring)) {
            i = isTestIMSI(imsiOnSimLock) ? 5 : 1;
        } else if (this.mSimlockGid1 == null) {
            log("getBrand : Brand is " + 0 + " (waiting GID1)");
            return 0;
        } else {
            i = SP_CODE_EMOBILE.equals(substring2) ? 3 : 1;
        }
        log("getBrand : Brand is " + i);
        return i;
    }

    public int getDisplayRule(String str) {
        int i = 2;
        if (this.mContext != null && this.mContext.getResources().getBoolean(17957045)) {
            return 1;
        }
        if (!((this.mParentApp != null && this.mParentApp.getUiccCard() != null && this.mParentApp.getUiccCard().getOperatorBrandOverride() != null) || TextUtils.isEmpty(getServiceProviderName()) || this.mSpnDisplayCondition == -1)) {
            if (isOnMatchingPlmn(str)) {
                i = (this.mSpnDisplayCondition & 1) == 1 ? 3 : 1;
            } else if ((this.mSpnDisplayCondition & 2) == 0) {
                i = 3;
            }
        }
        return i;
    }

    public String getGid1() {
        return this.mGid1;
    }

    public String getIMSI() {
        return this.mImsi;
    }

    public String getImsiOnSimLock() {
        if (this.mSimLockImsi == null) {
            log("getImsiOnSimLock : mSimLockImsi is null");
        }
        return this.mSimLockImsi;
    }

    public String getMccMncOnSimLock() {
        if (this.mSimLockImsi == null) {
            loge("getSimLockMccMnc : EF_IMSI is not loaded");
            return null;
        }
        int i = this.mSimLockMccMncSize;
        if (i == -1 || i == 0) {
            log("getSimLockMccMnc : EF_AD is invalid. treating MCC/MNC length as " + 5);
            i = 5;
        }
        return this.mSimLockImsi.substring(0, i);
    }

    public String getMsisdnAlphaTag() {
        return this.mMsisdnTag;
    }

    public String getMsisdnNumber() {
        return this.mMsisdn;
    }

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

    public UsimServiceTable getUsimServiceTable() {
        return this.mUsimServiceTable;
    }

    public boolean getVoiceCallForwardingFlag() {
        return this.mCallForwardingEnabled;
    }

    public String getVoiceMailAlphaTag() {
        return this.mVoiceMailTag;
    }

    public String getVoiceMailNumber() {
        return this.mVoiceMailNum;
    }

    public int getVoiceMessageCount() {
        int i = -1;
        int i2 = 0;
        int i3;
        if (this.mEfMWIS != null) {
            if ((this.mEfMWIS[0] & 1) != 0) {
                i2 = 1;
            }
            i3 = this.mEfMWIS[1] & 255;
            i2 = (i2 == 0 || i3 != 0) ? i3 : -1;
            log(" VoiceMessageCount from SIM MWIS = " + i2);
        } else if (this.mEfCPHS_MWI != null) {
            i3 = this.mEfCPHS_MWI[0] & 15;
            if (i3 != 10) {
                i = i3 == 5 ? 0 : -2;
            }
            log(" VoiceMessageCount from SIM CPHS = " + i);
            return i;
        } else {
            i2 = -2;
        }
        return i2;
    }

    /* Access modifiers changed, original: protected */
    public void handleFileUpdate(int i) {
        switch (i) {
            case IccConstants.EF_LOCK /*12272*/:
                if (TelBrand.IS_DCM) {
                    log("SIM Refresh for EF_LOCK");
                    Intent intent = new Intent("jp.co.sharp.android.telephony.action.SIM_REMOTE_NFCLOCK_CHANGED");
                    intent.addFlags(536870912);
                    log("Broadcasting intent ACTION_SIM_REMOTE_NFCLOCK_CHANGED");
                    ActivityManagerNative.broadcastStickyIntent(intent, "android.permission.READ_PHONE_STATE", -1);
                    return;
                }
                return;
            case IccConstants.EF_CFF_CPHS /*28435*/:
                this.mRecordsToLoad++;
                log("SIM Refresh called for EF_CFF_CPHS");
                this.mFh.loadEFTransparent(IccConstants.EF_CFF_CPHS, obtainMessage(24));
                return;
            case IccConstants.EF_CSP_CPHS /*28437*/:
                this.mRecordsToLoad++;
                log("[CSP] SIM Refresh for EF_CSP_CPHS");
                this.mFh.loadEFTransparent(IccConstants.EF_CSP_CPHS, obtainMessage(33));
                return;
            case IccConstants.EF_MAILBOX_CPHS /*28439*/:
                this.mRecordsToLoad++;
                new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, obtainMessage(11));
                return;
            case 28474:
                log("SIM Refresh for EF_ADN");
                this.mAdnCache.reset();
                return;
            case IccConstants.EF_FDN /*28475*/:
                log("SIM Refresh called for EF_FDN");
                this.mParentApp.queryFdn();
                break;
            case IccConstants.EF_MSISDN /*28480*/:
                break;
            case IccConstants.EF_MBDN /*28615*/:
                this.mRecordsToLoad++;
                new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MBDN, IccConstants.EF_EXT6, this.mMailboxIndex, obtainMessage(6));
                return;
            case IccConstants.EF_CFIS /*28619*/:
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

    /* JADX WARNING: Unknown top exception splitter block from list: {B:198:0x05c0=Splitter:B:198:0x05c0, B:328:0x0981=Splitter:B:328:0x0981, B:509:0x0e71=Splitter:B:509:0x0e71, B:434:0x0c13=Splitter:B:434:0x0c13, B:134:0x03a4=Splitter:B:134:0x03a4, B:284:0x0824=Splitter:B:284:0x0824, B:18:0x005b=Splitter:B:18:0x005b, B:304:0x08ec=Splitter:B:304:0x08ec, B:126:0x035d=Splitter:B:126:0x035d, B:63:0x01af=Splitter:B:63:0x01af, B:241:0x06ff=Splitter:B:241:0x06ff, B:367:0x0a9f=Splitter:B:367:0x0a9f} */
    /* JADX WARNING: Removed duplicated region for block: B:115:0x0325 A:{Catch:{ all -> 0x0a09, RuntimeException -> 0x00be }} */
    /* JADX WARNING: Removed duplicated region for block: B:114:0x030e A:{Catch:{ all -> 0x0a09, RuntimeException -> 0x00be }} */
    /* JADX WARNING: Removed duplicated region for block: B:28:0x006f  */
    /* JADX WARNING: Exception block dominator not found, dom blocks: [B:33:0x009d, B:171:0x051d] */
    /* JADX WARNING: Missing block: B:19:?, code skipped:
            logw("Exception parsing SIM record", r0);
     */
    /* JADX WARNING: Missing block: B:20:0x0060, code skipped:
            if (r2 != false) goto L_0x0062;
     */
    /* JADX WARNING: Missing block: B:21:0x0062, code skipped:
            onRecordLoaded();
     */
    /* JADX WARNING: Missing block: B:28:0x006f, code skipped:
            onRecordLoaded();
     */
    /* JADX WARNING: Missing block: B:37:0x00be, code skipped:
            r0 = e;
     */
    /* JADX WARNING: Missing block: B:72:0x01f6, code skipped:
            r0 = th;
     */
    /* JADX WARNING: Missing block: B:354:0x0a23, code skipped:
            r1 = r9.mImsi.substring(0, 6);
            log("mccmncCode=" + r1);
            r3 = MCCMNC_CODES_HAVING_3DIGITS_MNC;
            r4 = r3.length;
     */
    /* JADX WARNING: Missing block: B:355:0x0a44, code skipped:
            if (r7 < r4) goto L_0x0a46;
     */
    /* JADX WARNING: Missing block: B:357:0x0a4c, code skipped:
            if (r3[r7].equals(r1) != false) goto L_0x0a4e;
     */
    /* JADX WARNING: Missing block: B:358:0x0a4e, code skipped:
            r9.mMncLength = 3;
            log("setting6 mMncLength=" + r9.mMncLength);
     */
    /* JADX WARNING: Missing block: B:364:0x0a73, code skipped:
            if (r9.mImsi != null) goto L_0x0a75;
     */
    /* JADX WARNING: Missing block: B:366:?, code skipped:
            r9.mMncLength = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(java.lang.Integer.parseInt(r9.mImsi.substring(0, 3)));
            log("setting7 mMncLength=" + r9.mMncLength);
     */
    /* JADX WARNING: Missing block: B:372:0x0aa7, code skipped:
            log("update mccmnc=" + r9.mImsi.substring(0, r9.mMncLength + 3));
            com.android.internal.telephony.MccTable.updateMccMncConfiguration(r9.mContext, r9.mImsi.substring(0, r9.mMncLength + 3), false);
     */
    /* JADX WARNING: Missing block: B:374:0x0adb, code skipped:
            if (r9.mMncLength == 0) goto L_0x0ae1;
     */
    /* JADX WARNING: Missing block: B:377:0x0ae1, code skipped:
            r9.mSimLockMccMncSize = 0;
     */
    /* JADX WARNING: Missing block: B:380:0x0ae6, code skipped:
            r9.mMncLength = 0;
            loge("Corrupt IMSI! setting8 mMncLength=" + r9.mMncLength);
     */
    /* JADX WARNING: Missing block: B:381:0x0b02, code skipped:
            r9.mMncLength = 0;
            log("MNC length not present in EF_AD setting9 mMncLength=" + r9.mMncLength);
     */
    /* JADX WARNING: Missing block: B:382:0x0b1e, code skipped:
            r9.mSimLockMccMncSize = r9.mMncLength + 3;
     */
    /* JADX WARNING: Missing block: B:537:0x0fc3, code skipped:
            r7 = r7 + 1;
     */
    /* JADX WARNING: Missing block: B:565:?, code skipped:
            return;
     */
    /* JADX WARNING: Missing block: B:566:?, code skipped:
            return;
     */
    public void handleMessage(android.os.Message r10) {
        /*
        r9 = this;
        r3 = 3;
        r6 = 6;
        r8 = -1;
        r2 = 1;
        r7 = 0;
        r0 = r9.mDestroyed;
        r0 = r0.get();
        if (r0 == 0) goto L_0x003c;
    L_0x000d:
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "Received message ";
        r0 = r0.append(r1);
        r0 = r0.append(r10);
        r1 = "[";
        r0 = r0.append(r1);
        r1 = r10.what;
        r0 = r0.append(r1);
        r1 = "] ";
        r0 = r0.append(r1);
        r1 = " while being destroyed. Ignoring.";
        r0 = r0.append(r1);
        r0 = r0.toString();
        r9.loge(r0);
    L_0x003b:
        return;
    L_0x003c:
        r0 = r10.what;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        switch(r0) {
            case 1: goto L_0x0054;
            case 3: goto L_0x009d;
            case 4: goto L_0x04ed;
            case 5: goto L_0x02c7;
            case 6: goto L_0x033c;
            case 7: goto L_0x0452;
            case 8: goto L_0x04a7;
            case 9: goto L_0x051d;
            case 10: goto L_0x03e4;
            case 11: goto L_0x033c;
            case 12: goto L_0x0bce;
            case 13: goto L_0x0c28;
            case 14: goto L_0x0c3b;
            case 15: goto L_0x0c4d;
            case 17: goto L_0x0ce3;
            case 18: goto L_0x0c86;
            case 19: goto L_0x0c97;
            case 20: goto L_0x0d40;
            case 22: goto L_0x0cb4;
            case 24: goto L_0x0bd8;
            case 25: goto L_0x0df2;
            case 26: goto L_0x0d12;
            case 30: goto L_0x0423;
            case 32: goto L_0x0e3a;
            case 33: goto L_0x0eb3;
            case 34: goto L_0x0efa;
            case 35: goto L_0x0066;
            case 101: goto L_0x0073;
            case 102: goto L_0x021d;
            case 103: goto L_0x0b25;
            case 104: goto L_0x0f4e;
            default: goto L_0x0041;
        };	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
    L_0x0041:
        r0 = 31;
        r1 = r10.what;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r0 != r1) goto L_0x004a;
    L_0x0047:
        r0 = 1;
        r9.mSimRefresh = r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
    L_0x004a:
        super.handleMessage(r10);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
    L_0x004e:
        if (r2 == 0) goto L_0x003b;
    L_0x0050:
        r9.onRecordLoaded();
        goto L_0x003b;
    L_0x0054:
        r9.onReady();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x0059:
        r0 = move-exception;
        r2 = r7;
    L_0x005b:
        r1 = "Exception parsing SIM record";
        r9.logw(r1, r0);	 Catch:{ all -> 0x01f6 }
        if (r2 == 0) goto L_0x003b;
    L_0x0062:
        r9.onRecordLoaded();
        goto L_0x003b;
    L_0x0066:
        r9.onLocked();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x006b:
        r0 = move-exception;
        r2 = r7;
    L_0x006d:
        if (r2 == 0) goto L_0x0072;
    L_0x006f:
        r9.onRecordLoaded();
    L_0x0072:
        throw r0;
    L_0x0073:
        r0 = r9.mFh;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = 28423; // 0x6f07 float:3.9829E-41 double:1.4043E-319;
        r2 = 0;
        r3 = 102; // 0x66 float:1.43E-43 double:5.04E-322;
        r3 = r9.obtainMessage(r3);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0.loadEFTransparent(r1, r2, r3);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r9.mFh;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = 28589; // 0x6fad float:4.0062E-41 double:1.4125E-319;
        r2 = 103; // 0x67 float:1.44E-43 double:5.1E-322;
        r2 = r9.obtainMessage(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0.loadEFTransparent(r1, r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r9.mFh;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = 28478; // 0x6f3e float:3.9906E-41 double:1.407E-319;
        r2 = 104; // 0x68 float:1.46E-43 double:5.14E-322;
        r2 = r9.obtainMessage(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0.loadEFTransparent(r1, r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x009d:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 == 0) goto L_0x00c0;
    L_0x00a5:
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r1.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "Exception querying IMSI, Exception:";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r1.append(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.loge(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x00be:
        r0 = move-exception;
        goto L_0x005b;
    L_0x00c0:
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (java.lang.String) r1;	 Catch:{ RuntimeException -> 0x00be }
        r9.mImsi = r1;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (java.lang.String) r0;	 Catch:{ RuntimeException -> 0x00be }
        r9.mSimLockImsi = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0100;
    L_0x00d0:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.length();	 Catch:{ RuntimeException -> 0x00be }
        if (r0 < r6) goto L_0x00e2;
    L_0x00d8:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.length();	 Catch:{ RuntimeException -> 0x00be }
        r1 = 15;
        if (r0 <= r1) goto L_0x0100;
    L_0x00e2:
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "invalid IMSI ";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.loge(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = 0;
        r9.mImsi = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = 0;
        r9.mSimLockImsi = r0;	 Catch:{ RuntimeException -> 0x00be }
    L_0x0100:
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "IMSI: mMncLength=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "IMSI: ";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        r4 = 6;
        r1 = r1.substring(r3, r4);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = "xxxxxxx";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0145;
    L_0x0140:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r1 = 2;
        if (r0 != r1) goto L_0x0181;
    L_0x0145:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0181;
    L_0x0149:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.length();	 Catch:{ RuntimeException -> 0x00be }
        if (r0 < r6) goto L_0x0181;
    L_0x0151:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r1 = 0;
        r3 = 6;
        r0 = r0.substring(r1, r3);	 Catch:{ RuntimeException -> 0x00be }
        r1 = MCCMNC_CODES_HAVING_3DIGITS_MNC;	 Catch:{ RuntimeException -> 0x00be }
        r3 = r1.length;	 Catch:{ RuntimeException -> 0x00be }
    L_0x015c:
        if (r7 >= r3) goto L_0x0181;
    L_0x015e:
        r4 = r1[r7];	 Catch:{ RuntimeException -> 0x00be }
        r4 = r4.equals(r0);	 Catch:{ RuntimeException -> 0x00be }
        if (r4 == 0) goto L_0x0f9b;
    L_0x0166:
        r0 = 3;
        r9.mMncLength = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "IMSI: setting1 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
    L_0x0181:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != 0) goto L_0x01af;
    L_0x0185:
        r0 = r9.mImsi;	 Catch:{ NumberFormatException -> 0x01f9 }
        r1 = 0;
        r3 = 3;
        r0 = r0.substring(r1, r3);	 Catch:{ NumberFormatException -> 0x01f9 }
        r0 = java.lang.Integer.parseInt(r0);	 Catch:{ NumberFormatException -> 0x01f9 }
        r0 = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(r0);	 Catch:{ NumberFormatException -> 0x01f9 }
        r9.mMncLength = r0;	 Catch:{ NumberFormatException -> 0x01f9 }
        r0 = new java.lang.StringBuilder;	 Catch:{ NumberFormatException -> 0x01f9 }
        r0.<init>();	 Catch:{ NumberFormatException -> 0x01f9 }
        r1 = "setting2 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ NumberFormatException -> 0x01f9 }
        r1 = r9.mMncLength;	 Catch:{ NumberFormatException -> 0x01f9 }
        r0 = r0.append(r1);	 Catch:{ NumberFormatException -> 0x01f9 }
        r0 = r0.toString();	 Catch:{ NumberFormatException -> 0x01f9 }
        r9.log(r0);	 Catch:{ NumberFormatException -> 0x01f9 }
    L_0x01af:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x01e9;
    L_0x01b3:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == r8) goto L_0x01e9;
    L_0x01b7:
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "update mccmnc=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        r4 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r4 = r4 + 3;
        r1 = r1.substring(r3, r4);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r9.mContext;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        r4 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r4 = r4 + 3;
        r1 = r1.substring(r3, r4);	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r0, r1, r3);	 Catch:{ RuntimeException -> 0x00be }
    L_0x01e9:
        r0 = r9.isAppStateReady();	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0216;
    L_0x01ef:
        r0 = r9.mImsiReadyRegistrants;	 Catch:{ RuntimeException -> 0x00be }
        r0.notifyRegistrants();	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x01f6:
        r0 = move-exception;
        goto L_0x006d;
    L_0x01f9:
        r0 = move-exception;
        r0 = 0;
        r9.mMncLength = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "Corrupt IMSI! setting3 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.loge(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x01af;
    L_0x0216:
        r0 = "EVENT_GET_IMSI_DONE:App state is not ready; not notifying the registrants";
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x021d:
        r0 = "EVENT_GET_IMSI_SUBSCRIPTION_PERSO_DONE";
        r9.log(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r3 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r3 == 0) goto L_0x0f9f;
    L_0x0230:
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1.<init>();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = "Exception querying IMSI on SIM Lock, Exception:";
        r1 = r1.append(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r1.append(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.loge(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x024b:
        r0 = 8;
    L_0x024d:
        r3 = "";
        r4 = r2;
    L_0x0250:
        r2 = r0 + 1;
        if (r4 >= r2) goto L_0x028c;
    L_0x0254:
        r2 = r1[r4];
        r2 = r2 & 15;
        r5 = 15;
        if (r2 == r5) goto L_0x0fce;
    L_0x025c:
        r5 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r5.<init>();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r3 = r5.append(r3);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r3.append(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r2.toString();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
    L_0x026d:
        r3 = r1[r4];
        r3 = r3 >>> 4;
        r3 = r3 & 15;
        r5 = 15;
        if (r3 == r5) goto L_0x0288;
    L_0x0277:
        r5 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r5.<init>();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r5.append(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r2.append(r3);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r2.toString();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
    L_0x0288:
        r4 = r4 + 1;
        r3 = r2;
        goto L_0x0250;
    L_0x028c:
        r0 = 1;
        r0 = r3.substring(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.mSimLockImsi = r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
    L_0x0293:
        r0 = r9.mSimLockImsi;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r0 == 0) goto L_0x0fcb;
    L_0x0297:
        r0 = r9.mSimLockImsi;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.length();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r0 < r6) goto L_0x02a9;
    L_0x029f:
        r0 = r9.mSimLockImsi;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.length();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = 15;
        if (r0 <= r1) goto L_0x0fcb;
    L_0x02a9:
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0.<init>();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = "invalid IMSI(SIM Lock) ";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r9.mSimLockImsi;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.loge(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = 0;
        r9.mSimLockImsi = r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x02c7:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != 0) goto L_0x0fd4;
    L_0x02d5:
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "EF_MBI: ";
        r0 = r0.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r3 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = 0;
        r0 = r1[r0];	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0 & 255;
        r9.mMailboxIndex = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r9.mMailboxIndex;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0fd4;
    L_0x02fa:
        r0 = r9.mMailboxIndex;	 Catch:{ RuntimeException -> 0x00be }
        r1 = 255; // 0xff float:3.57E-43 double:1.26E-321;
        if (r0 == r1) goto L_0x0fd4;
    L_0x0300:
        r0 = "Got valid mailbox number for MBDN";
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r2;
    L_0x0306:
        r1 = r9.mRecordsToLoad;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1 + 1;
        r9.mRecordsToLoad = r1;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0325;
    L_0x030e:
        r0 = new com.android.internal.telephony.uicc.AdnRecordLoader;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mFh;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = 28615; // 0x6fc7 float:4.0098E-41 double:1.41377E-319;
        r3 = 28616; // 0x6fc8 float:4.01E-41 double:1.4138E-319;
        r4 = r9.mMailboxIndex;	 Catch:{ RuntimeException -> 0x00be }
        r5 = 6;
        r5 = r9.obtainMessage(r5);	 Catch:{ RuntimeException -> 0x00be }
        r0.loadFromEF(r1, r3, r4, r5);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0325:
        r0 = new com.android.internal.telephony.uicc.AdnRecordLoader;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mFh;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = 28439; // 0x6f17 float:3.9852E-41 double:1.40507E-319;
        r3 = 28490; // 0x6f4a float:3.9923E-41 double:1.4076E-319;
        r4 = 1;
        r5 = 11;
        r5 = r9.obtainMessage(r5);	 Catch:{ RuntimeException -> 0x00be }
        r0.loadFromEF(r1, r3, r4, r5);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x033c:
        r0 = 0;
        r9.mVoiceMailNum = r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = 0;
        r9.mVoiceMailTag = r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 == 0) goto L_0x0389;
    L_0x034a:
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "Invalid or missing EF";
        r1 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r10.what;	 Catch:{ RuntimeException -> 0x00be }
        r3 = 11;
        if (r0 != r3) goto L_0x0fab;
    L_0x035b:
        r0 = "[MAILBOX]";
    L_0x035d:
        r0 = r1.append(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r10.what;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != r6) goto L_0x004e;
    L_0x036c:
        r0 = r9.mRecordsToLoad;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0 + 1;
        r9.mRecordsToLoad = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new com.android.internal.telephony.uicc.AdnRecordLoader;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mFh;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = 28439; // 0x6f17 float:3.9852E-41 double:1.40507E-319;
        r3 = 28490; // 0x6f4a float:3.9923E-41 double:1.4076E-319;
        r4 = 1;
        r5 = 11;
        r5 = r9.obtainMessage(r5);	 Catch:{ RuntimeException -> 0x00be }
        r0.loadFromEF(r1, r3, r4, r5);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0389:
        r0 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (com.android.internal.telephony.uicc.AdnRecord) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r1.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "VM: ";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r3 = r1.append(r0);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r10.what;	 Catch:{ RuntimeException -> 0x00be }
        r4 = 11;
        if (r1 != r4) goto L_0x0faf;
    L_0x03a2:
        r1 = " EF[MAILBOX]";
    L_0x03a4:
        r1 = r3.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.isEmpty();	 Catch:{ RuntimeException -> 0x00be }
        if (r1 == 0) goto L_0x03d6;
    L_0x03b5:
        r1 = r10.what;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 != r6) goto L_0x03d6;
    L_0x03b9:
        r0 = r9.mRecordsToLoad;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0 + 1;
        r9.mRecordsToLoad = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new com.android.internal.telephony.uicc.AdnRecordLoader;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mFh;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = 28439; // 0x6f17 float:3.9852E-41 double:1.40507E-319;
        r3 = 28490; // 0x6f4a float:3.9923E-41 double:1.4076E-319;
        r4 = 1;
        r5 = 11;
        r5 = r9.obtainMessage(r5);	 Catch:{ RuntimeException -> 0x00be }
        r0.loadFromEF(r1, r3, r4, r5);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x03d6:
        r1 = r0.getNumber();	 Catch:{ RuntimeException -> 0x00be }
        r9.mVoiceMailNum = r1;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.getAlphaTag();	 Catch:{ RuntimeException -> 0x00be }
        r9.mVoiceMailTag = r0;	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x03e4:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 == 0) goto L_0x03f3;
    L_0x03ec:
        r0 = "Invalid or missing EF[MSISDN]";
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x03f3:
        r0 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (com.android.internal.telephony.uicc.AdnRecord) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.getNumber();	 Catch:{ RuntimeException -> 0x00be }
        r9.mMsisdn = r1;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.getAlphaTag();	 Catch:{ RuntimeException -> 0x00be }
        r9.mMsisdnTag = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = "MSISDN: xxxxxxx";
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r9.mSimRefresh;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x004e;
    L_0x040c:
        r0 = 0;
        r9.mSimRefresh = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new android.content.Intent;	 Catch:{ RuntimeException -> 0x00be }
        r1 = "jp.co.sharp.android.telephony.action.REFRESHED_MSISDN_LOADED";
        r0.<init>(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = "Broadcasting intent ACTION_REFRESHED_MSISDN_LOADED";
        r9.log(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = "android.permission.READ_PHONE_STATE";
        r3 = -1;
        android.app.ActivityManagerNative.broadcastStickyIntent(r0, r1, r3);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0423:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r1 != 0) goto L_0x0438;
    L_0x042b:
        r1 = r9.mNewMsisdn;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.mMsisdn = r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r9.mNewMsisdnTag;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.mMsisdnTag = r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = "Success to update EF[MSISDN]";
        r9.log(r1);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
    L_0x0438:
        r1 = r0.userObj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r1 == 0) goto L_0x0fcb;
    L_0x043c:
        r1 = r0.userObj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = (android.os.Message) r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = android.os.AsyncResult.forMessage(r1);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1.exception = r2;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.userObj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = (android.os.Message) r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0.sendToTarget();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x0452:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r3 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r3.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r4 = "EF_MWIS : ";
        r3 = r3.append(r4);	 Catch:{ RuntimeException -> 0x00be }
        r4 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r1);	 Catch:{ RuntimeException -> 0x00be }
        r3 = r3.append(r4);	 Catch:{ RuntimeException -> 0x00be }
        r3 = r3.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r3);	 Catch:{ RuntimeException -> 0x00be }
        r3 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r3 == 0) goto L_0x0494;
    L_0x047a:
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r1.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "EVENT_GET_MWIS_DONE exception = ";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r1.append(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0494:
        r0 = r1[r7];
        r0 = r0 & 255;
        r3 = 255; // 0xff float:3.57E-43 double:1.26E-321;
        if (r0 != r3) goto L_0x04a3;
    L_0x049c:
        r0 = "SIMRecords: Uninitialized record MWIS";
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x04a3:
        r9.mEfMWIS = r1;	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x04a7:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r3 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r3.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r4 = "EF_CPHS_MWI: ";
        r3 = r3.append(r4);	 Catch:{ RuntimeException -> 0x00be }
        r4 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r1);	 Catch:{ RuntimeException -> 0x00be }
        r3 = r3.append(r4);	 Catch:{ RuntimeException -> 0x00be }
        r3 = r3.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r3);	 Catch:{ RuntimeException -> 0x00be }
        r3 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r3 == 0) goto L_0x04e9;
    L_0x04cf:
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r1.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "EVENT_GET_VOICE_MAIL_INDICATOR_CPHS_DONE exception = ";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r1.append(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x04e9:
        r9.mEfCPHS_MWI = r1;	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x04ed:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != 0) goto L_0x004e;
    L_0x04fb:
        r0 = 0;
        r3 = r1.length;	 Catch:{ RuntimeException -> 0x00be }
        r0 = com.android.internal.telephony.uicc.IccUtils.bcdToString(r1, r0, r3);	 Catch:{ RuntimeException -> 0x00be }
        r9.mIccId = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "iccid: ";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mIccId;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x051d:
        r0 = r10.obj;	 Catch:{ all -> 0x0a09 }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ all -> 0x0a09 }
        r1 = r0.result;	 Catch:{ all -> 0x0a09 }
        r1 = (byte[]) r1;	 Catch:{ all -> 0x0a09 }
        r1 = (byte[]) r1;	 Catch:{ all -> 0x0a09 }
        r0 = r0.exception;	 Catch:{ all -> 0x0a09 }
        if (r0 == 0) goto L_0x0648;
    L_0x052b:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == r8) goto L_0x0538;
    L_0x052f:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0538;
    L_0x0533:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r1 = 2;
        if (r0 != r1) goto L_0x058a;
    L_0x0538:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x058a;
    L_0x053c:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.length();	 Catch:{ RuntimeException -> 0x00be }
        if (r0 < r6) goto L_0x058a;
    L_0x0544:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r1 = 0;
        r3 = 6;
        r0 = r0.substring(r1, r3);	 Catch:{ RuntimeException -> 0x00be }
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r1.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "mccmncCode=";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.append(r0);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = MCCMNC_CODES_HAVING_3DIGITS_MNC;	 Catch:{ RuntimeException -> 0x00be }
        r3 = r1.length;	 Catch:{ RuntimeException -> 0x00be }
    L_0x0565:
        if (r7 >= r3) goto L_0x058a;
    L_0x0567:
        r4 = r1[r7];	 Catch:{ RuntimeException -> 0x00be }
        r4 = r4.equals(r0);	 Catch:{ RuntimeException -> 0x00be }
        if (r4 == 0) goto L_0x0fb3;
    L_0x056f:
        r0 = 3;
        r9.mMncLength = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "setting6 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
    L_0x058a:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0592;
    L_0x058e:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != r8) goto L_0x05c0;
    L_0x0592:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0624;
    L_0x0596:
        r0 = r9.mImsi;	 Catch:{ NumberFormatException -> 0x0607 }
        r1 = 0;
        r3 = 3;
        r0 = r0.substring(r1, r3);	 Catch:{ NumberFormatException -> 0x0607 }
        r0 = java.lang.Integer.parseInt(r0);	 Catch:{ NumberFormatException -> 0x0607 }
        r0 = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(r0);	 Catch:{ NumberFormatException -> 0x0607 }
        r9.mMncLength = r0;	 Catch:{ NumberFormatException -> 0x0607 }
        r0 = new java.lang.StringBuilder;	 Catch:{ NumberFormatException -> 0x0607 }
        r0.<init>();	 Catch:{ NumberFormatException -> 0x0607 }
        r1 = "setting7 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ NumberFormatException -> 0x0607 }
        r1 = r9.mMncLength;	 Catch:{ NumberFormatException -> 0x0607 }
        r0 = r0.append(r1);	 Catch:{ NumberFormatException -> 0x0607 }
        r0 = r0.toString();	 Catch:{ NumberFormatException -> 0x0607 }
        r9.log(r0);	 Catch:{ NumberFormatException -> 0x0607 }
    L_0x05c0:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x05fa;
    L_0x05c4:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x05fa;
    L_0x05c8:
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "update mccmnc=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        r4 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r4 = r4 + 3;
        r1 = r1.substring(r3, r4);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r9.mContext;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        r4 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r4 = r4 + 3;
        r1 = r1.substring(r3, r4);	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r0, r1, r3);	 Catch:{ RuntimeException -> 0x00be }
    L_0x05fa:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0602;
    L_0x05fe:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != r8) goto L_0x0640;
    L_0x0602:
        r0 = 0;
        r9.mSimLockMccMncSize = r0;	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0607:
        r0 = move-exception;
        r0 = 0;
        r9.mMncLength = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "Corrupt IMSI! setting8 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.loge(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x05c0;
    L_0x0624:
        r0 = 0;
        r9.mMncLength = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "MNC length not present in EF_AD setting9 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x05c0;
    L_0x0640:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0 + 3;
        r9.mSimLockMccMncSize = r0;	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0648:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0a09 }
        r0.<init>();	 Catch:{ all -> 0x0a09 }
        r4 = "EF_AD: ";
        r0 = r0.append(r4);	 Catch:{ all -> 0x0a09 }
        r4 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r1);	 Catch:{ all -> 0x0a09 }
        r0 = r0.append(r4);	 Catch:{ all -> 0x0a09 }
        r0 = r0.toString();	 Catch:{ all -> 0x0a09 }
        r9.log(r0);	 Catch:{ all -> 0x0a09 }
        r0 = r1.length;	 Catch:{ all -> 0x0a09 }
        if (r0 >= r3) goto L_0x0787;
    L_0x0665:
        r0 = "Corrupt AD data on SIM";
        r9.log(r0);	 Catch:{ all -> 0x0a09 }
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == r8) goto L_0x0677;
    L_0x066e:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0677;
    L_0x0672:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r1 = 2;
        if (r0 != r1) goto L_0x06c9;
    L_0x0677:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x06c9;
    L_0x067b:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.length();	 Catch:{ RuntimeException -> 0x00be }
        if (r0 < r6) goto L_0x06c9;
    L_0x0683:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r1 = 0;
        r3 = 6;
        r0 = r0.substring(r1, r3);	 Catch:{ RuntimeException -> 0x00be }
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r1.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "mccmncCode=";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.append(r0);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = MCCMNC_CODES_HAVING_3DIGITS_MNC;	 Catch:{ RuntimeException -> 0x00be }
        r3 = r1.length;	 Catch:{ RuntimeException -> 0x00be }
    L_0x06a4:
        if (r7 >= r3) goto L_0x06c9;
    L_0x06a6:
        r4 = r1[r7];	 Catch:{ RuntimeException -> 0x00be }
        r4 = r4.equals(r0);	 Catch:{ RuntimeException -> 0x00be }
        if (r4 == 0) goto L_0x0fb7;
    L_0x06ae:
        r0 = 3;
        r9.mMncLength = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "setting6 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
    L_0x06c9:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x06d1;
    L_0x06cd:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != r8) goto L_0x06ff;
    L_0x06d1:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0763;
    L_0x06d5:
        r0 = r9.mImsi;	 Catch:{ NumberFormatException -> 0x0746 }
        r1 = 0;
        r3 = 3;
        r0 = r0.substring(r1, r3);	 Catch:{ NumberFormatException -> 0x0746 }
        r0 = java.lang.Integer.parseInt(r0);	 Catch:{ NumberFormatException -> 0x0746 }
        r0 = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(r0);	 Catch:{ NumberFormatException -> 0x0746 }
        r9.mMncLength = r0;	 Catch:{ NumberFormatException -> 0x0746 }
        r0 = new java.lang.StringBuilder;	 Catch:{ NumberFormatException -> 0x0746 }
        r0.<init>();	 Catch:{ NumberFormatException -> 0x0746 }
        r1 = "setting7 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ NumberFormatException -> 0x0746 }
        r1 = r9.mMncLength;	 Catch:{ NumberFormatException -> 0x0746 }
        r0 = r0.append(r1);	 Catch:{ NumberFormatException -> 0x0746 }
        r0 = r0.toString();	 Catch:{ NumberFormatException -> 0x0746 }
        r9.log(r0);	 Catch:{ NumberFormatException -> 0x0746 }
    L_0x06ff:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0739;
    L_0x0703:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0739;
    L_0x0707:
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "update mccmnc=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        r4 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r4 = r4 + 3;
        r1 = r1.substring(r3, r4);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r9.mContext;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        r4 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r4 = r4 + 3;
        r1 = r1.substring(r3, r4);	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r0, r1, r3);	 Catch:{ RuntimeException -> 0x00be }
    L_0x0739:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0741;
    L_0x073d:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != r8) goto L_0x077f;
    L_0x0741:
        r0 = 0;
        r9.mSimLockMccMncSize = r0;	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0746:
        r0 = move-exception;
        r0 = 0;
        r9.mMncLength = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "Corrupt IMSI! setting8 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.loge(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x06ff;
    L_0x0763:
        r0 = 0;
        r9.mMncLength = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "MNC length not present in EF_AD setting9 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x06ff;
    L_0x077f:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0 + 3;
        r9.mSimLockMccMncSize = r0;	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0787:
        r0 = r1.length;	 Catch:{ all -> 0x0a09 }
        if (r0 != r3) goto L_0x08ac;
    L_0x078a:
        r0 = "MNC length not present in EF_AD";
        r9.log(r0);	 Catch:{ all -> 0x0a09 }
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == r8) goto L_0x079c;
    L_0x0793:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x079c;
    L_0x0797:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r1 = 2;
        if (r0 != r1) goto L_0x07ee;
    L_0x079c:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x07ee;
    L_0x07a0:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.length();	 Catch:{ RuntimeException -> 0x00be }
        if (r0 < r6) goto L_0x07ee;
    L_0x07a8:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r1 = 0;
        r3 = 6;
        r0 = r0.substring(r1, r3);	 Catch:{ RuntimeException -> 0x00be }
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r1.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "mccmncCode=";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.append(r0);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = MCCMNC_CODES_HAVING_3DIGITS_MNC;	 Catch:{ RuntimeException -> 0x00be }
        r3 = r1.length;	 Catch:{ RuntimeException -> 0x00be }
    L_0x07c9:
        if (r7 >= r3) goto L_0x07ee;
    L_0x07cb:
        r4 = r1[r7];	 Catch:{ RuntimeException -> 0x00be }
        r4 = r4.equals(r0);	 Catch:{ RuntimeException -> 0x00be }
        if (r4 == 0) goto L_0x0fbb;
    L_0x07d3:
        r0 = 3;
        r9.mMncLength = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "setting6 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
    L_0x07ee:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x07f6;
    L_0x07f2:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != r8) goto L_0x0824;
    L_0x07f6:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0888;
    L_0x07fa:
        r0 = r9.mImsi;	 Catch:{ NumberFormatException -> 0x086b }
        r1 = 0;
        r3 = 3;
        r0 = r0.substring(r1, r3);	 Catch:{ NumberFormatException -> 0x086b }
        r0 = java.lang.Integer.parseInt(r0);	 Catch:{ NumberFormatException -> 0x086b }
        r0 = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(r0);	 Catch:{ NumberFormatException -> 0x086b }
        r9.mMncLength = r0;	 Catch:{ NumberFormatException -> 0x086b }
        r0 = new java.lang.StringBuilder;	 Catch:{ NumberFormatException -> 0x086b }
        r0.<init>();	 Catch:{ NumberFormatException -> 0x086b }
        r1 = "setting7 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ NumberFormatException -> 0x086b }
        r1 = r9.mMncLength;	 Catch:{ NumberFormatException -> 0x086b }
        r0 = r0.append(r1);	 Catch:{ NumberFormatException -> 0x086b }
        r0 = r0.toString();	 Catch:{ NumberFormatException -> 0x086b }
        r9.log(r0);	 Catch:{ NumberFormatException -> 0x086b }
    L_0x0824:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x085e;
    L_0x0828:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x085e;
    L_0x082c:
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "update mccmnc=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        r4 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r4 = r4 + 3;
        r1 = r1.substring(r3, r4);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r9.mContext;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        r4 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r4 = r4 + 3;
        r1 = r1.substring(r3, r4);	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r0, r1, r3);	 Catch:{ RuntimeException -> 0x00be }
    L_0x085e:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0866;
    L_0x0862:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != r8) goto L_0x08a4;
    L_0x0866:
        r0 = 0;
        r9.mSimLockMccMncSize = r0;	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x086b:
        r0 = move-exception;
        r0 = 0;
        r9.mMncLength = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "Corrupt IMSI! setting8 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.loge(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x0824;
    L_0x0888:
        r0 = 0;
        r9.mMncLength = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "MNC length not present in EF_AD setting9 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x0824;
    L_0x08a4:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0 + 3;
        r9.mSimLockMccMncSize = r0;	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x08ac:
        r0 = 3;
        r0 = r1[r0];	 Catch:{ all -> 0x0a09 }
        r0 = r0 & 15;
        r9.mMncLength = r0;	 Catch:{ all -> 0x0a09 }
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0a09 }
        r0.<init>();	 Catch:{ all -> 0x0a09 }
        r1 = "setting4 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ all -> 0x0a09 }
        r1 = r9.mMncLength;	 Catch:{ all -> 0x0a09 }
        r0 = r0.append(r1);	 Catch:{ all -> 0x0a09 }
        r0 = r0.toString();	 Catch:{ all -> 0x0a09 }
        r9.log(r0);	 Catch:{ all -> 0x0a09 }
        r0 = r9.mMncLength;	 Catch:{ all -> 0x0a09 }
        r1 = 15;
        if (r0 != r1) goto L_0x08ec;
    L_0x08d1:
        r0 = 0;
        r9.mMncLength = r0;	 Catch:{ all -> 0x0a09 }
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0a09 }
        r0.<init>();	 Catch:{ all -> 0x0a09 }
        r1 = "setting5 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ all -> 0x0a09 }
        r1 = r9.mMncLength;	 Catch:{ all -> 0x0a09 }
        r0 = r0.append(r1);	 Catch:{ all -> 0x0a09 }
        r0 = r0.toString();	 Catch:{ all -> 0x0a09 }
        r9.log(r0);	 Catch:{ all -> 0x0a09 }
    L_0x08ec:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == r8) goto L_0x08f9;
    L_0x08f0:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x08f9;
    L_0x08f4:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r1 = 2;
        if (r0 != r1) goto L_0x094b;
    L_0x08f9:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x094b;
    L_0x08fd:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.length();	 Catch:{ RuntimeException -> 0x00be }
        if (r0 < r6) goto L_0x094b;
    L_0x0905:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r1 = 0;
        r3 = 6;
        r0 = r0.substring(r1, r3);	 Catch:{ RuntimeException -> 0x00be }
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r1.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "mccmncCode=";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.append(r0);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = MCCMNC_CODES_HAVING_3DIGITS_MNC;	 Catch:{ RuntimeException -> 0x00be }
        r3 = r1.length;	 Catch:{ RuntimeException -> 0x00be }
    L_0x0926:
        if (r7 >= r3) goto L_0x094b;
    L_0x0928:
        r4 = r1[r7];	 Catch:{ RuntimeException -> 0x00be }
        r4 = r4.equals(r0);	 Catch:{ RuntimeException -> 0x00be }
        if (r4 == 0) goto L_0x0fbf;
    L_0x0930:
        r0 = 3;
        r9.mMncLength = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "setting6 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
    L_0x094b:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0953;
    L_0x094f:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != r8) goto L_0x0981;
    L_0x0953:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x09e5;
    L_0x0957:
        r0 = r9.mImsi;	 Catch:{ NumberFormatException -> 0x09c8 }
        r1 = 0;
        r3 = 3;
        r0 = r0.substring(r1, r3);	 Catch:{ NumberFormatException -> 0x09c8 }
        r0 = java.lang.Integer.parseInt(r0);	 Catch:{ NumberFormatException -> 0x09c8 }
        r0 = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(r0);	 Catch:{ NumberFormatException -> 0x09c8 }
        r9.mMncLength = r0;	 Catch:{ NumberFormatException -> 0x09c8 }
        r0 = new java.lang.StringBuilder;	 Catch:{ NumberFormatException -> 0x09c8 }
        r0.<init>();	 Catch:{ NumberFormatException -> 0x09c8 }
        r1 = "setting7 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ NumberFormatException -> 0x09c8 }
        r1 = r9.mMncLength;	 Catch:{ NumberFormatException -> 0x09c8 }
        r0 = r0.append(r1);	 Catch:{ NumberFormatException -> 0x09c8 }
        r0 = r0.toString();	 Catch:{ NumberFormatException -> 0x09c8 }
        r9.log(r0);	 Catch:{ NumberFormatException -> 0x09c8 }
    L_0x0981:
        r0 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x09bb;
    L_0x0985:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x09bb;
    L_0x0989:
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "update mccmnc=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        r4 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r4 = r4 + 3;
        r1 = r1.substring(r3, r4);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r9.mContext;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        r4 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r4 = r4 + 3;
        r1 = r1.substring(r3, r4);	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r0, r1, r3);	 Catch:{ RuntimeException -> 0x00be }
    L_0x09bb:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x09c3;
    L_0x09bf:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != r8) goto L_0x0a01;
    L_0x09c3:
        r0 = 0;
        r9.mSimLockMccMncSize = r0;	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x09c8:
        r0 = move-exception;
        r0 = 0;
        r9.mMncLength = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "Corrupt IMSI! setting8 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.loge(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x0981;
    L_0x09e5:
        r0 = 0;
        r9.mMncLength = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "MNC length not present in EF_AD setting9 mMncLength=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x0981;
    L_0x0a01:
        r0 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0 + 3;
        r9.mSimLockMccMncSize = r0;	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0a09:
        r0 = move-exception;
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 == r8) goto L_0x0a17;
    L_0x0a0e:
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 == 0) goto L_0x0a17;
    L_0x0a12:
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r3 = 2;
        if (r1 != r3) goto L_0x0a69;
    L_0x0a17:
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 == 0) goto L_0x0a69;
    L_0x0a1b:
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.length();	 Catch:{ RuntimeException -> 0x00be }
        if (r1 < r6) goto L_0x0a69;
    L_0x0a23:
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        r4 = 6;
        r1 = r1.substring(r3, r4);	 Catch:{ RuntimeException -> 0x00be }
        r3 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r3.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r4 = "mccmncCode=";
        r3 = r3.append(r4);	 Catch:{ RuntimeException -> 0x00be }
        r3 = r3.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r3 = r3.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r3);	 Catch:{ RuntimeException -> 0x00be }
        r3 = MCCMNC_CODES_HAVING_3DIGITS_MNC;	 Catch:{ RuntimeException -> 0x00be }
        r4 = r3.length;	 Catch:{ RuntimeException -> 0x00be }
    L_0x0a44:
        if (r7 >= r4) goto L_0x0a69;
    L_0x0a46:
        r5 = r3[r7];	 Catch:{ RuntimeException -> 0x00be }
        r5 = r5.equals(r1);	 Catch:{ RuntimeException -> 0x00be }
        if (r5 == 0) goto L_0x0fc3;
    L_0x0a4e:
        r1 = 3;
        r9.mMncLength = r1;	 Catch:{ RuntimeException -> 0x00be }
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r1.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "setting6 mMncLength=";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r3 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r1);	 Catch:{ RuntimeException -> 0x00be }
    L_0x0a69:
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 == 0) goto L_0x0a71;
    L_0x0a6d:
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 != r8) goto L_0x0a9f;
    L_0x0a71:
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 == 0) goto L_0x0b02;
    L_0x0a75:
        r1 = r9.mImsi;	 Catch:{ NumberFormatException -> 0x0ae5 }
        r3 = 0;
        r4 = 3;
        r1 = r1.substring(r3, r4);	 Catch:{ NumberFormatException -> 0x0ae5 }
        r1 = java.lang.Integer.parseInt(r1);	 Catch:{ NumberFormatException -> 0x0ae5 }
        r1 = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(r1);	 Catch:{ NumberFormatException -> 0x0ae5 }
        r9.mMncLength = r1;	 Catch:{ NumberFormatException -> 0x0ae5 }
        r1 = new java.lang.StringBuilder;	 Catch:{ NumberFormatException -> 0x0ae5 }
        r1.<init>();	 Catch:{ NumberFormatException -> 0x0ae5 }
        r3 = "setting7 mMncLength=";
        r1 = r1.append(r3);	 Catch:{ NumberFormatException -> 0x0ae5 }
        r3 = r9.mMncLength;	 Catch:{ NumberFormatException -> 0x0ae5 }
        r1 = r1.append(r3);	 Catch:{ NumberFormatException -> 0x0ae5 }
        r1 = r1.toString();	 Catch:{ NumberFormatException -> 0x0ae5 }
        r9.log(r1);	 Catch:{ NumberFormatException -> 0x0ae5 }
    L_0x0a9f:
        r1 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 == 0) goto L_0x0ad9;
    L_0x0aa3:
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 == 0) goto L_0x0ad9;
    L_0x0aa7:
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r1.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "update mccmnc=";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r3 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r4 = 0;
        r5 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r5 = r5 + 3;
        r3 = r3.substring(r4, r5);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mContext;	 Catch:{ RuntimeException -> 0x00be }
        r3 = r9.mImsi;	 Catch:{ RuntimeException -> 0x00be }
        r4 = 0;
        r5 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r5 = r5 + 3;
        r3 = r3.substring(r4, r5);	 Catch:{ RuntimeException -> 0x00be }
        r4 = 0;
        com.android.internal.telephony.MccTable.updateMccMncConfiguration(r1, r3, r4);	 Catch:{ RuntimeException -> 0x00be }
    L_0x0ad9:
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 == 0) goto L_0x0ae1;
    L_0x0add:
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 != r8) goto L_0x0b1e;
    L_0x0ae1:
        r1 = 0;
        r9.mSimLockMccMncSize = r1;	 Catch:{ RuntimeException -> 0x00be }
    L_0x0ae4:
        throw r0;	 Catch:{ RuntimeException -> 0x00be }
    L_0x0ae5:
        r1 = move-exception;
        r1 = 0;
        r9.mMncLength = r1;	 Catch:{ RuntimeException -> 0x00be }
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r1.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "Corrupt IMSI! setting8 mMncLength=";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r3 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.loge(r1);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x0a9f;
    L_0x0b02:
        r1 = 0;
        r9.mMncLength = r1;	 Catch:{ RuntimeException -> 0x00be }
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r1.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "MNC length not present in EF_AD setting9 mMncLength=";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r3 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r1);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x0a9f;
    L_0x0b1e:
        r1 = r9.mMncLength;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1 + 3;
        r9.mSimLockMccMncSize = r1;	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x0ae4;
    L_0x0b25:
        r0 = "EVENT_GET_AD_SUBSCRIPTION_PERSO_DONE";
        r9.log(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r0 != 0) goto L_0x0b3e;
    L_0x0b38:
        if (r1 == 0) goto L_0x0b3e;
    L_0x0b3a:
        r0 = r1.length;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = 4;
        if (r0 >= r2) goto L_0x0b49;
    L_0x0b3e:
        r0 = 0;
        r9.mSimLockMccMncSize = r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = "invalid EF_AD";
        r9.loge(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x0b49:
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0.<init>();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = "EF_AD: ";
        r0 = r0.append(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r1);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.append(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r1[r3];
        r0 = r0 & 15;
        r1 = 15;
        if (r0 != r1) goto L_0x0b6c;
    L_0x0b6b:
        r0 = r7;
    L_0x0b6c:
        if (r0 == r8) goto L_0x0b73;
    L_0x0b6e:
        if (r0 == 0) goto L_0x0b73;
    L_0x0b70:
        r1 = 2;
        if (r0 != r1) goto L_0x0b96;
    L_0x0b73:
        r1 = r9.mSimLockImsi;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r1 == 0) goto L_0x0b96;
    L_0x0b77:
        r1 = r9.mSimLockImsi;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r1.length();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r1 < r6) goto L_0x0b96;
    L_0x0b7f:
        r1 = r9.mSimLockImsi;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = 0;
        r4 = 6;
        r2 = r1.substring(r2, r4);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r4 = MCCMNC_CODES_HAVING_3DIGITS_MNC;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r5 = r4.length;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r7;
    L_0x0b8b:
        if (r1 >= r5) goto L_0x0b96;
    L_0x0b8d:
        r6 = r4[r1];	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r6 = r6.equals(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r6 == 0) goto L_0x0fc7;
    L_0x0b95:
        r0 = r3;
    L_0x0b96:
        if (r0 == 0) goto L_0x0b9a;
    L_0x0b98:
        if (r0 != r8) goto L_0x0bae;
    L_0x0b9a:
        r0 = r9.mSimLockImsi;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r0 == 0) goto L_0x0bc0;
    L_0x0b9e:
        r0 = r9.mSimLockImsi;	 Catch:{ NumberFormatException -> 0x0bb8 }
        r1 = 0;
        r2 = 3;
        r0 = r0.substring(r1, r2);	 Catch:{ NumberFormatException -> 0x0bb8 }
        r0 = java.lang.Integer.parseInt(r0);	 Catch:{ NumberFormatException -> 0x0bb8 }
        r0 = com.android.internal.telephony.MccTable.smallestDigitsMccForMnc(r0);	 Catch:{ NumberFormatException -> 0x0bb8 }
    L_0x0bae:
        if (r0 == 0) goto L_0x0bb2;
    L_0x0bb0:
        if (r0 != r8) goto L_0x0bc7;
    L_0x0bb2:
        r0 = 0;
        r9.mSimLockMccMncSize = r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x0bb8:
        r0 = move-exception;
        r0 = "Corrupt IMSI!";
        r9.loge(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r7;
        goto L_0x0bae;
    L_0x0bc0:
        r0 = "MNC length not present in EF_AD";
        r9.log(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r7;
        goto L_0x0bae;
    L_0x0bc7:
        r0 = r0 + 3;
        r9.mSimLockMccMncSize = r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x0bce:
        r1 = 0;
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r9.getSpnFsm(r1, r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0bd8:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != 0) goto L_0x004e;
    L_0x0be6:
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "EF_CFF_CPHS: ";
        r0 = r0.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r3 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        r9.mEfCff = r1;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r9.mEfCfis;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r9.validEfCfis(r0);	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != 0) goto L_0x0c21;
    L_0x0c0a:
        r0 = r1[r7];
        r0 = r0 & 15;
        r1 = 10;
        if (r0 != r1) goto L_0x0c13;
    L_0x0c12:
        r7 = r2;
    L_0x0c13:
        r9.mCallForwardingEnabled = r7;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r9.mRecordsEventsRegistrants;	 Catch:{ RuntimeException -> 0x00be }
        r1 = 1;
        r1 = java.lang.Integer.valueOf(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0.notifyResult(r1);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0c21:
        r0 = "EVENT_GET_CFF_DONE: EF_CFIS is valid, ignoring EF_CFF_CPHS";
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0c28:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != 0) goto L_0x004e;
    L_0x0c36:
        r9.parseEfSpdi(r1);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0c3b:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r1 == 0) goto L_0x0fcb;
    L_0x0c43:
        r1 = "update failed. ";
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.logw(r1, r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x0c4d:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != 0) goto L_0x004e;
    L_0x0c5b:
        r0 = new com.android.internal.telephony.gsm.SimTlv;	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        r4 = r1.length;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>(r1, r3, r4);	 Catch:{ RuntimeException -> 0x00be }
    L_0x0c62:
        r1 = r0.isValidObject();	 Catch:{ RuntimeException -> 0x00be }
        if (r1 == 0) goto L_0x004e;
    L_0x0c68:
        r1 = r0.getTag();	 Catch:{ RuntimeException -> 0x00be }
        r3 = 67;
        if (r1 != r3) goto L_0x0c82;
    L_0x0c70:
        r1 = r0.getData();	 Catch:{ RuntimeException -> 0x00be }
        r3 = 0;
        r0 = r0.getData();	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.length;	 Catch:{ RuntimeException -> 0x00be }
        r0 = com.android.internal.telephony.uicc.IccUtils.networkNameToString(r1, r3, r0);	 Catch:{ RuntimeException -> 0x00be }
        r9.mPnnHomeName = r0;	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0c82:
        r0.nextObject();	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x0c62;
    L_0x0c86:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 != 0) goto L_0x004e;
    L_0x0c8e:
        r0 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (java.util.ArrayList) r0;	 Catch:{ RuntimeException -> 0x00be }
        r9.handleSmses(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0c97:
        r0 = "ENF";
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1.<init>();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = "marked read: sms ";
        r1 = r1.append(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r10.arg1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r1.append(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        android.telephony.Rlog.i(r0, r1);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x0cb4:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r1 != 0) goto L_0x0cc8;
    L_0x0cbc:
        r0 = r0.result;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.handleSms(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x0cc8:
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1.<init>();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = "Error on GET_SMS with exp ";
        r1 = r1.append(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r1.append(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.loge(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x0ce3:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != 0) goto L_0x004e;
    L_0x0cf1:
        r0 = new com.android.internal.telephony.uicc.UsimServiceTable;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>(r1);	 Catch:{ RuntimeException -> 0x00be }
        r9.mUsimServiceTable = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "SST: ";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mUsimServiceTable;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0d12:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 != 0) goto L_0x004e;
    L_0x0d1a:
        r0 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x00be }
        r9.mCphsInfo = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "iCPHS: ";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mCphsInfo;	 Catch:{ RuntimeException -> 0x00be }
        r1 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0d40:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1.<init>();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = "EVENT_SET_MBDN_DONE ex:";
        r1 = r1.append(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r1.append(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.log(r1);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r1 != 0) goto L_0x0d68;
    L_0x0d60:
        r1 = r9.mNewVoiceMailNum;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.mVoiceMailNum = r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r9.mNewVoiceMailTag;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.mVoiceMailTag = r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
    L_0x0d68:
        r1 = r9.isCphsMailboxEnabled();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r1 == 0) goto L_0x0db5;
    L_0x0d6e:
        r1 = new com.android.internal.telephony.uicc.AdnRecord;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r9.mVoiceMailTag;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r3 = r9.mVoiceMailNum;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1.<init>(r2, r3);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r0.userObj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = (android.os.Message) r2;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r3 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r3 != 0) goto L_0x0fd1;
    L_0x0d7f:
        r3 = r0.userObj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r3 == 0) goto L_0x0fd1;
    L_0x0d83:
        r2 = r0.userObj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = (android.os.Message) r2;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = android.os.AsyncResult.forMessage(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r3 = 0;
        r2.exception = r3;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.userObj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = (android.os.Message) r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0.sendToTarget();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = "Callback with MBDN successful.";
        r9.log(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = 0;
        r6 = r2;
    L_0x0d9c:
        r0 = new com.android.internal.telephony.uicc.AdnRecordLoader;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r9.mFh;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0.<init>(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = 28439; // 0x6f17 float:3.9852E-41 double:1.40507E-319;
        r3 = 28490; // 0x6f4a float:3.9923E-41 double:1.4076E-319;
        r4 = 1;
        r5 = 0;
        r8 = 25;
        r6 = r9.obtainMessage(r8, r6);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0.updateEF(r1, r2, r3, r4, r5, r6);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x0db5:
        r1 = r0.userObj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r1 == 0) goto L_0x0fcb;
    L_0x0db9:
        r1 = android.content.res.Resources.getSystem();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r2 == 0) goto L_0x0de5;
    L_0x0dc1:
        r2 = 17957007; // 0x112008f float:2.6816366E-38 double:8.8719403E-317;
        r1 = r1.getBoolean(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r1 == 0) goto L_0x0de5;
    L_0x0dca:
        r1 = r0.userObj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = (android.os.Message) r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = android.os.AsyncResult.forMessage(r1);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = new com.android.internal.telephony.uicc.IccVmNotSupportedException;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r3 = "Update SIM voice mailbox error";
        r2.<init>(r3);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1.exception = r2;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
    L_0x0ddb:
        r0 = r0.userObj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = (android.os.Message) r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0.sendToTarget();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x0de5:
        r1 = r0.userObj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = (android.os.Message) r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = android.os.AsyncResult.forMessage(r1);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1.exception = r2;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        goto L_0x0ddb;
    L_0x0df2:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r1 != 0) goto L_0x0e21;
    L_0x0dfa:
        r1 = r9.mNewVoiceMailNum;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.mVoiceMailNum = r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r9.mNewVoiceMailTag;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.mVoiceMailTag = r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
    L_0x0e02:
        r1 = r0.userObj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r1 == 0) goto L_0x0fcb;
    L_0x0e06:
        r1 = "Callback with CPHS MB successful.";
        r9.log(r1);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r0.userObj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = (android.os.Message) r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = android.os.AsyncResult.forMessage(r1);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1.exception = r2;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.userObj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = (android.os.Message) r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0.sendToTarget();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x0e21:
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1.<init>();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = "Set CPHS MailBox with exception: ";
        r1 = r1.append(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r1.append(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.log(r1);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        goto L_0x0e02;
    L_0x0e3a:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r0 != 0) goto L_0x004e;
    L_0x0e48:
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "EF_CFIS: ";
        r0 = r0.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r3 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r9.validEfCfis(r1);	 Catch:{ RuntimeException -> 0x00be }
        if (r0 == 0) goto L_0x0e97;
    L_0x0e68:
        r9.mEfCfis = r1;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r1[r2];
        r0 = r0 & 1;
        if (r0 == 0) goto L_0x0e71;
    L_0x0e70:
        r7 = r2;
    L_0x0e71:
        r9.mCallForwardingEnabled = r7;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "EF_CFIS: callForwardingEnabled=";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mCallForwardingEnabled;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r9.mRecordsEventsRegistrants;	 Catch:{ RuntimeException -> 0x00be }
        r1 = 1;
        r1 = java.lang.Integer.valueOf(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0.notifyResult(r1);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0e97:
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "EF_CFIS: invalid data=";
        r0 = r0.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r1 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0eb3:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r1 == 0) goto L_0x0ed5;
    L_0x0ebb:
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r1.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "Exception in fetching EF_CSP data ";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r1.append(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.loge(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0ed5:
        r0 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r1.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "EF_CSP: ";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r3 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r0);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r1.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r1);	 Catch:{ RuntimeException -> 0x00be }
        r9.handleEfCspData(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0efa:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x00be }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x00be }
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x00be }
        r3 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        if (r3 == 0) goto L_0x0f28;
    L_0x0f08:
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r1.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r3 = "Exception in get GID1 ";
        r1 = r1.append(r3);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r1.append(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.loge(r0);	 Catch:{ RuntimeException -> 0x00be }
        r0 = 0;
        r9.mGid1 = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = 0;
        r9.mSimlockGid1 = r0;	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0f28:
        r0 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r1);	 Catch:{ RuntimeException -> 0x00be }
        r9.mGid1 = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r1);	 Catch:{ RuntimeException -> 0x00be }
        r9.mSimlockGid1 = r0;	 Catch:{ RuntimeException -> 0x00be }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x00be }
        r0.<init>();	 Catch:{ RuntimeException -> 0x00be }
        r1 = "GID1: ";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r1 = r9.mGid1;	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x00be }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x00be }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x00be }
        goto L_0x004e;
    L_0x0f4e:
        r0 = r10.obj;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = (android.os.AsyncResult) r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r0.result;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = (byte[]) r1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        if (r2 == 0) goto L_0x0f7a;
    L_0x0f5c:
        r1 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1.<init>();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = "Exception in get GID1 ";
        r1 = r1.append(r2);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.exception;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r1.append(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.loge(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = 0;
        r9.mSimlockGid1 = r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x0f7a:
        r0 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r1);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.mSimlockGid1 = r0;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = new java.lang.StringBuilder;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0.<init>();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = "GID1: ";
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r1 = r9.mSimlockGid1;	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.append(r1);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r0 = r0.toString();	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r9.log(r0);	 Catch:{ RuntimeException -> 0x0059, all -> 0x006b }
        r2 = r7;
        goto L_0x004e;
    L_0x0f9b:
        r7 = r7 + 1;
        goto L_0x015c;
    L_0x0f9f:
        if (r1 == 0) goto L_0x0293;
    L_0x0fa1:
        r0 = r1[r7];
        r3 = 8;
        if (r0 >= r3) goto L_0x024b;
    L_0x0fa7:
        r0 = r1[r7];
        goto L_0x024d;
    L_0x0fab:
        r0 = "[MBDN]";
        goto L_0x035d;
    L_0x0faf:
        r1 = " EF[MBDN]";
        goto L_0x03a4;
    L_0x0fb3:
        r7 = r7 + 1;
        goto L_0x0565;
    L_0x0fb7:
        r7 = r7 + 1;
        goto L_0x06a4;
    L_0x0fbb:
        r7 = r7 + 1;
        goto L_0x07c9;
    L_0x0fbf:
        r7 = r7 + 1;
        goto L_0x0926;
    L_0x0fc3:
        r7 = r7 + 1;
        goto L_0x0a44;
    L_0x0fc7:
        r1 = r1 + 1;
        goto L_0x0b8b;
    L_0x0fcb:
        r2 = r7;
        goto L_0x004e;
    L_0x0fce:
        r2 = r3;
        goto L_0x026d;
    L_0x0fd1:
        r6 = r2;
        goto L_0x0d9c;
    L_0x0fd4:
        r0 = r7;
        goto L_0x0306;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.uicc.SIMRecords.handleMessage(android.os.Message):void");
    }

    public void handleSmsOnIcc(AsyncResult asyncResult) {
        int[] iArr = (int[]) asyncResult.result;
        if (asyncResult.exception == null && iArr.length == 1) {
            log("READ EF_SMS RECORD index= " + iArr[0]);
            this.mFh.loadEFLinearFixed(IccConstants.EF_SMS, iArr[0], obtainMessage(22));
            return;
        }
        loge(" Error on SMS_ON_SIM with exp " + asyncResult.exception + " length " + iArr.length);
    }

    public boolean isCallForwardStatusStored() {
        return (this.mEfCfis == null && this.mEfCff == null) ? false : true;
    }

    public boolean isCspPlmnEnabled() {
        return this.mCspPlmnEnabled;
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(LOG_TAG, "[SIMRecords] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str) {
        Rlog.e(LOG_TAG, "[SIMRecords] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void logv(String str) {
        Rlog.v(LOG_TAG, "[SIMRecords] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void logw(String str, Throwable th) {
        Rlog.w(LOG_TAG, "[SIMRecords] " + str, th);
    }

    /* Access modifiers changed, original: protected */
    public void onAllRecordsLoaded() {
        log("record load complete");
        setLocaleFromUsim();
        if (this.mParentApp.getState() == AppState.APPSTATE_PIN || this.mParentApp.getState() == AppState.APPSTATE_PUK) {
            this.mRecordsRequested = false;
            return;
        }
        String operatorNumeric = getOperatorNumeric();
        if (TextUtils.isEmpty(operatorNumeric)) {
            log("onAllRecordsLoaded empty 'gsm.sim.operator.numeric' skipping");
        } else {
            log("onAllRecordsLoaded set 'gsm.sim.operator.numeric' to operator='" + operatorNumeric + "'");
            log("update icc_operator_numeric=" + operatorNumeric);
            setSystemProperty("gsm.sim.operator.numeric", operatorNumeric);
            SubscriptionController instance = SubscriptionController.getInstance();
            instance.setMccMnc(operatorNumeric, instance.getDefaultSmsSubId());
        }
        if (TextUtils.isEmpty(this.mImsi)) {
            log("onAllRecordsLoaded empty imsi skipping setting mcc");
        } else {
            log("onAllRecordsLoaded set mcc imsi = xxxxxx");
            setSystemProperty("gsm.sim.operator.iso-country", MccTable.countryCodeForMcc(Integer.parseInt(this.mImsi.substring(0, 3))));
        }
        setVoiceMailByCountry(operatorNumeric);
        setSpnFromConfig(operatorNumeric);
        if (isAppStateReady()) {
            this.mRecordsLoadedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
        } else {
            log("onAllRecordsLoaded: AppState is not ready; not notifying the registrants");
        }
    }

    public void onReady() {
        fetchSimRecords();
    }

    /* Access modifiers changed, original: protected */
    public void onRecordLoaded() {
        this.mRecordsToLoad--;
        log("onRecordLoaded " + this.mRecordsToLoad + " requested: " + this.mRecordsRequested);
        if (this.mRecordsToLoad == 0 && this.mRecordsRequested) {
            onAllRecordsLoaded();
        } else if (this.mRecordsToLoad < 0) {
            loge("recordsToLoad <0, programmer error suspected");
            this.mRecordsToLoad = 0;
        }
    }

    public void onRefresh(boolean z, int[] iArr) {
        if (z) {
            fetchSimRecords();
        }
    }

    /* Access modifiers changed, original: protected */
    public void resetRecords() {
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
        log("update icc_operator_numeric=" + null);
        setSystemProperty("gsm.sim.operator.numeric", null);
        setSystemProperty("gsm.sim.operator.alpha", null);
        setSystemProperty("gsm.sim.operator.iso-country", null);
        this.mRecordsRequested = false;
    }

    public void setMsisdnNumber(String str, String str2, Message message) {
        this.mNewMsisdn = str2;
        this.mNewMsisdnTag = str;
        log("Set MSISDN: " + this.mNewMsisdnTag + " " + "xxxxxxx");
        new AdnRecordLoader(this.mFh).updateEF(new AdnRecord(this.mNewMsisdnTag, this.mNewMsisdn), IccConstants.EF_MSISDN, getExtFromEf(IccConstants.EF_MSISDN), 1, null, obtainMessage(30, message));
    }

    public void setVoiceCallForwardingFlag(int i, boolean z, String str) {
        if (i == 1) {
            this.mCallForwardingEnabled = z;
            this.mRecordsEventsRegistrants.notifyResult(Integer.valueOf(1));
            try {
                if (validEfCfis(this.mEfCfis)) {
                    byte[] bArr;
                    if (z) {
                        bArr = this.mEfCfis;
                        bArr[1] = (byte) (bArr[1] | 1);
                    } else {
                        bArr = this.mEfCfis;
                        bArr[1] = (byte) (bArr[1] & 254);
                    }
                    log("setVoiceCallForwardingFlag: enable=" + z + " mEfCfis=" + IccUtils.bytesToHexString(this.mEfCfis));
                    if (z && !TextUtils.isEmpty(str)) {
                        log("EF_CFIS: updating cf number, " + str);
                        bArr = PhoneNumberUtils.numberToCalledPartyBCD(str);
                        System.arraycopy(bArr, 0, this.mEfCfis, 3, bArr.length);
                        this.mEfCfis[2] = (byte) bArr.length;
                        this.mEfCfis[14] = (byte) -1;
                        this.mEfCfis[15] = (byte) -1;
                    }
                    this.mFh.updateEFLinearFixed(IccConstants.EF_CFIS, 1, this.mEfCfis, null, obtainMessage(14, Integer.valueOf(IccConstants.EF_CFIS)));
                } else {
                    log("setVoiceCallForwardingFlag: ignoring enable=" + z + " invalid mEfCfis=" + IccUtils.bytesToHexString(this.mEfCfis));
                }
                if (this.mEfCff != null) {
                    if (z) {
                        this.mEfCff[0] = (byte) ((this.mEfCff[0] & 240) | 10);
                    } else {
                        this.mEfCff[0] = (byte) ((this.mEfCff[0] & 240) | 5);
                    }
                    this.mFh.updateEFTransparent(IccConstants.EF_CFF_CPHS, this.mEfCff, obtainMessage(14, Integer.valueOf(IccConstants.EF_CFF_CPHS)));
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                logw("Error saving call forwarding flag to SIM. Probably malformed SIM record", e);
            }
        }
    }

    public void setVoiceMailNumber(String str, String str2, Message message) {
        if (this.mIsVoiceMailFixed) {
            AsyncResult.forMessage(message).exception = new IccVmFixedException("Voicemail number is fixed by operator");
            message.sendToTarget();
            return;
        }
        this.mNewVoiceMailNum = str2;
        this.mNewVoiceMailTag = str;
        AdnRecord adnRecord = new AdnRecord(this.mNewVoiceMailTag, this.mNewVoiceMailNum);
        if (this.mMailboxIndex != 0 && this.mMailboxIndex != 255) {
            new AdnRecordLoader(this.mFh).updateEF(adnRecord, IccConstants.EF_MBDN, IccConstants.EF_EXT6, this.mMailboxIndex, null, obtainMessage(20, message));
        } else if (isCphsMailboxEnabled()) {
            new AdnRecordLoader(this.mFh).updateEF(adnRecord, IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, null, obtainMessage(25, message));
        } else {
            AsyncResult.forMessage(message).exception = new IccVmNotSupportedException("Update SIM voice mailbox error");
            message.sendToTarget();
        }
    }

    public void setVoiceMessageWaiting(int i, int i2) {
        int i3 = 1;
        if (i == 1) {
            try {
                if (this.mEfMWIS != null) {
                    byte[] bArr = this.mEfMWIS;
                    byte b = this.mEfMWIS[0];
                    if (i2 == 0) {
                        i3 = 0;
                    }
                    bArr[0] = (byte) (i3 | (b & 254));
                    if (i2 < 0) {
                        this.mEfMWIS[1] = (byte) 0;
                    } else {
                        this.mEfMWIS[1] = (byte) i2;
                    }
                    this.mFh.updateEFLinearFixed(IccConstants.EF_MWIS, 1, this.mEfMWIS, null, obtainMessage(14, IccConstants.EF_MWIS, 0));
                }
                if (this.mEfCPHS_MWI != null) {
                    this.mEfCPHS_MWI[0] = (byte) ((i2 == 0 ? 5 : 10) | (this.mEfCPHS_MWI[0] & 240));
                    this.mFh.updateEFTransparent(IccConstants.EF_VOICE_MAIL_INDICATOR_CPHS, this.mEfCPHS_MWI, obtainMessage(14, Integer.valueOf(IccConstants.EF_VOICE_MAIL_INDICATOR_CPHS)));
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                logw("Error saving voice mail state to SIM. Probably malformed SIM record", e);
            }
        }
    }

    public String toString() {
        return "SimRecords: " + super.toString() + " mVmConfig" + this.mVmConfig + " mSpnOverride=" + "mSpnOverride" + " callForwardingEnabled=" + this.mCallForwardingEnabled + " spnState=" + this.mSpnState + " mCphsInfo=" + this.mCphsInfo + " mCspPlmnEnabled=" + this.mCspPlmnEnabled + " efMWIS=" + this.mEfMWIS + " efCPHS_MWI=" + this.mEfCPHS_MWI + " mEfCff=" + this.mEfCff + " mEfCfis=" + this.mEfCfis + " getOperatorNumeric=" + getOperatorNumeric();
    }
}
