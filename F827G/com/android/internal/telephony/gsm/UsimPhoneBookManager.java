package com.android.internal.telephony.gsm;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.AdnRecordCache;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class UsimPhoneBookManager extends Handler implements IccConstants {
    private static final int ANR_ADDITIONAL_NUMBER_END_ID = 12;
    private static final int ANR_ADDITIONAL_NUMBER_START_ID = 3;
    private static final int ANR_ADN_RECORD_IDENTIFIER_ID = 16;
    private static final int ANR_ADN_SFI_ID = 15;
    private static final int ANR_BCD_NUMBER_LENGTH = 1;
    private static final int ANR_CAPABILITY_ID = 13;
    private static final int ANR_DESCRIPTION_ID = 0;
    private static final int ANR_EXTENSION_ID = 14;
    private static final int ANR_TON_NPI_ID = 2;
    private static final boolean DBG = true;
    private static final int EVENT_ANR_LOAD_DONE = 5;
    private static final int EVENT_EF_ANR_RECORD_SIZE_DONE = 7;
    private static final int EVENT_EF_EMAIL_RECORD_SIZE_DONE = 6;
    private static final int EVENT_EF_IAP_RECORD_SIZE_DONE = 10;
    private static final int EVENT_EMAIL_LOAD_DONE = 4;
    private static final int EVENT_IAP_LOAD_DONE = 3;
    private static final int EVENT_PBR_LOAD_DONE = 1;
    private static final int EVENT_UPDATE_ANR_RECORD_DONE = 9;
    private static final int EVENT_UPDATE_EMAIL_RECORD_DONE = 8;
    private static final int EVENT_UPDATE_IAP_RECORD_DONE = 11;
    private static final int EVENT_USIM_ADN_LOAD_DONE = 2;
    private static final String LOG_TAG = "UsimPhoneBookManager";
    private static final int MAX_NUMBER_SIZE_BYTES = 11;
    private static final int USIM_EFAAS_TAG = 199;
    private static final int USIM_EFADN_TAG = 192;
    private static final int USIM_EFANR_TAG = 196;
    private static final int USIM_EFCCP1_TAG = 203;
    private static final int USIM_EFEMAIL_TAG = 202;
    private static final int USIM_EFEXT1_TAG = 194;
    private static final int USIM_EFGRP_TAG = 198;
    private static final int USIM_EFGSD_TAG = 200;
    private static final int USIM_EFIAP_TAG = 193;
    private static final int USIM_EFPBC_TAG = 197;
    private static final int USIM_EFSNE_TAG = 195;
    private static final int USIM_EFUID_TAG = 201;
    private static final int USIM_TYPE1_TAG = 168;
    private static final int USIM_TYPE2_TAG = 169;
    private static final int USIM_TYPE3_TAG = 170;
    private AdnRecordCache mAdnCache;
    private ArrayList<Integer> mAdnLengthList;
    private ArrayList<Integer>[] mAnrFlagsRecord;
    private ArrayList<Integer>[] mEmailFlagsRecord;
    private IccFileHandler mFh;
    private int mPendingExtLoads;
    private Object mLock = new Object();
    private boolean mEmailPresentInIap = false;
    private int mEmailTagNumberInIap = 0;
    private boolean mAnrPresentInIap = false;
    private int mAnrTagNumberInIap = 0;
    private boolean mIapPresent = false;
    private boolean mSuccess = false;
    private boolean mRefreshCache = false;
    private ArrayList<AdnRecord> mPhoneBookRecords = new ArrayList<>();
    private Map<Integer, ArrayList<byte[]>> mIapFileRecord = new HashMap();
    private Map<Integer, ArrayList<byte[]>> mEmailFileRecord = new HashMap();
    private Map<Integer, ArrayList<byte[]>> mAnrFileRecord = new HashMap();
    private Map<Integer, ArrayList<Integer>> mRecordNums = new HashMap();
    private PbrFile mPbrFile = null;
    private Map<Integer, ArrayList<Integer>> mAnrFlags = new HashMap();
    private Map<Integer, ArrayList<Integer>> mEmailFlags = new HashMap();
    private Boolean mIsPbrPresent = true;

    public UsimPhoneBookManager(IccFileHandler fh, AdnRecordCache cache) {
        this.mAdnLengthList = null;
        this.mFh = fh;
        this.mAdnLengthList = new ArrayList<>();
        this.mAdnCache = cache;
    }

    public void reset() {
        if (!(this.mAnrFlagsRecord == null || this.mEmailFlagsRecord == null || this.mPbrFile == null)) {
            for (int i = 0; i < this.mPbrFile.mFileIds.size(); i++) {
                this.mAnrFlagsRecord[i].clear();
                this.mEmailFlagsRecord[i].clear();
            }
        }
        this.mAnrFlags.clear();
        this.mEmailFlags.clear();
        this.mPhoneBookRecords.clear();
        this.mIapFileRecord.clear();
        this.mEmailFileRecord.clear();
        this.mAnrFileRecord.clear();
        this.mRecordNums.clear();
        this.mPbrFile = null;
        this.mAdnLengthList.clear();
        this.mIsPbrPresent = true;
        this.mRefreshCache = false;
    }

    public ArrayList<AdnRecord> loadEfFilesFromUsim() {
        synchronized (this.mLock) {
            if (!this.mPhoneBookRecords.isEmpty()) {
                if (this.mRefreshCache) {
                    this.mRefreshCache = false;
                    refreshCache();
                }
                return this.mPhoneBookRecords;
            } else if (!this.mIsPbrPresent.booleanValue()) {
                return null;
            } else {
                if (this.mPbrFile == null) {
                    readPbrFileAndWait();
                }
                if (this.mPbrFile == null) {
                    return null;
                }
                int numRecs = this.mPbrFile.mFileIds.size();
                if (this.mAnrFlagsRecord == null && this.mEmailFlagsRecord == null) {
                    this.mAnrFlagsRecord = new ArrayList[numRecs];
                    this.mEmailFlagsRecord = new ArrayList[numRecs];
                    for (int i = 0; i < numRecs; i++) {
                        this.mAnrFlagsRecord[i] = new ArrayList<>();
                        this.mEmailFlagsRecord[i] = new ArrayList<>();
                    }
                }
                for (int i2 = 0; i2 < numRecs; i2++) {
                    readAdnFileAndWait(i2);
                    readEmailFileAndWait(i2);
                    readAnrFileAndWait(i2);
                }
                return this.mPhoneBookRecords;
            }
        }
    }

    private void refreshCache() {
        if (this.mPbrFile != null) {
            this.mPhoneBookRecords.clear();
            int numRecs = this.mPbrFile.mFileIds.size();
            for (int i = 0; i < numRecs; i++) {
                readAdnFileAndWait(i);
            }
        }
    }

    public void invalidateCache() {
        this.mRefreshCache = true;
    }

    private void readPbrFileAndWait() {
        this.mFh.loadEFLinearFixedAll(IccConstants.EF_PBR, obtainMessage(1));
        try {
            this.mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
        }
    }

    private void readEmailFileAndWait(int recNum) {
        Map<Integer, Integer> fileIds = this.mPbrFile.mFileIds.get(Integer.valueOf(recNum));
        if (fileIds != null && fileIds.containsKey(Integer.valueOf((int) USIM_EFEMAIL_TAG))) {
            if (this.mEmailPresentInIap) {
                readIapFileAndWait(fileIds.get(193).intValue(), recNum);
                if (!hasRecordIn(this.mIapFileRecord, recNum)) {
                    Rlog.e(LOG_TAG, "Error: IAP file is empty");
                    return;
                }
                this.mFh.loadEFLinearFixedAll(fileIds.get(Integer.valueOf((int) USIM_EFEMAIL_TAG)).intValue(), getPBPath(fileIds.get(Integer.valueOf((int) USIM_EFEMAIL_TAG)).intValue()), obtainMessage(4, Integer.valueOf(recNum)));
                log("readEmailFileAndWait email efid is : " + fileIds.get(Integer.valueOf((int) USIM_EFEMAIL_TAG)));
                try {
                    this.mLock.wait();
                } catch (InterruptedException e) {
                    Rlog.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
                }
            } else {
                Iterator i$ = this.mPbrFile.mEmailFileIds.get(Integer.valueOf(recNum)).iterator();
                while (i$.hasNext()) {
                    int efid = i$.next().intValue();
                    this.mFh.loadEFLinearFixedPart(efid, getPBPath(efid), getValidRecordNums(recNum), obtainMessage(4, Integer.valueOf(recNum)));
                    log("readEmailFileAndWait email efid is : " + efid + " recNum:" + recNum);
                    try {
                        this.mLock.wait();
                    } catch (InterruptedException e2) {
                        Rlog.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
                    }
                }
            }
            if (!hasRecordIn(this.mEmailFileRecord, recNum)) {
                Rlog.e(LOG_TAG, "Error: Email file is empty");
                return;
            }
            for (int m = 0; m < this.mEmailFileRecord.get(Integer.valueOf(recNum)).size(); m++) {
                this.mEmailFlagsRecord[recNum].add(0);
            }
            this.mEmailFlags.put(Integer.valueOf(recNum), this.mEmailFlagsRecord[recNum]);
            updatePhoneAdnRecordWithEmail(recNum);
        }
    }

    private void readAnrFileAndWait(int recNum) {
        if (this.mPbrFile == null) {
            Rlog.e(LOG_TAG, "mPbrFile is NULL, exiting from readAnrFileAndWait");
            return;
        }
        Map<Integer, Integer> fileIds = this.mPbrFile.mFileIds.get(Integer.valueOf(recNum));
        if (!(fileIds == null || fileIds.isEmpty() || !fileIds.containsKey(196))) {
            if (this.mAnrPresentInIap) {
                readIapFileAndWait(fileIds.get(193).intValue(), recNum);
                if (!hasRecordIn(this.mIapFileRecord, recNum)) {
                    Rlog.e(LOG_TAG, "Error: IAP file is empty");
                    return;
                }
                this.mFh.loadEFLinearFixedAll(fileIds.get(196).intValue(), getPBPath(fileIds.get(196).intValue()), obtainMessage(5, Integer.valueOf(recNum)));
                log("readAnrFileAndWait anr efid is : " + fileIds.get(196));
                try {
                    this.mLock.wait();
                } catch (InterruptedException e) {
                    Rlog.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
                }
            } else {
                Iterator i$ = this.mPbrFile.mAnrFileIds.get(Integer.valueOf(recNum)).iterator();
                while (i$.hasNext()) {
                    int efid = i$.next().intValue();
                    this.mFh.loadEFLinearFixedPart(efid, getPBPath(efid), getValidRecordNums(recNum), obtainMessage(5, Integer.valueOf(recNum)));
                    log("readAnrFileAndWait anr efid is : " + efid + " recNum:" + recNum);
                    try {
                        this.mLock.wait();
                    } catch (InterruptedException e2) {
                        Rlog.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
                    }
                }
            }
            if (!hasRecordIn(this.mAnrFileRecord, recNum)) {
                Rlog.e(LOG_TAG, "Error: Anr file is empty");
                return;
            }
            for (int m = 0; m < this.mAnrFileRecord.get(Integer.valueOf(recNum)).size(); m++) {
                this.mAnrFlagsRecord[recNum].add(0);
            }
            this.mAnrFlags.put(Integer.valueOf(recNum), this.mAnrFlagsRecord[recNum]);
            updatePhoneAdnRecordWithAnr(recNum);
        }
    }

    private void readIapFileAndWait(int efid, int recNum) {
        log("pbrIndex is " + recNum + ",iap efid is : " + efid);
        this.mFh.loadEFLinearFixedPart(efid, getPBPath(efid), getValidRecordNums(recNum), obtainMessage(3, Integer.valueOf(recNum)));
        try {
            this.mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readIapFileAndWait");
        }
    }

    public boolean updateEmailFile(int adnRecNum, String oldEmail, String newEmail, int efidIndex) {
        int pbrIndex = getPbrIndexBy(adnRecNum - 1);
        int efid = getEfidByTag(pbrIndex, USIM_EFEMAIL_TAG, efidIndex);
        if (oldEmail == null) {
            oldEmail = "";
        }
        if (newEmail == null) {
            newEmail = "";
        }
        String emails = oldEmail + "," + newEmail;
        this.mSuccess = false;
        log("updateEmailFile oldEmail : " + oldEmail + " newEmail:" + newEmail + " emails:" + emails + " efid" + efid + " adnRecNum: " + adnRecNum);
        if (efid == -1) {
            return this.mSuccess;
        }
        if (!this.mEmailPresentInIap || !TextUtils.isEmpty(oldEmail) || TextUtils.isEmpty(newEmail)) {
            this.mSuccess = true;
        } else if (getEmptyEmailNum_Pbrindex(pbrIndex) == 0) {
            log("updateEmailFile getEmptyEmailNum_Pbrindex=0, pbrIndex is " + pbrIndex);
            this.mSuccess = true;
            return this.mSuccess;
        } else {
            this.mSuccess = updateIapFile(adnRecNum, oldEmail, newEmail, USIM_EFEMAIL_TAG);
        }
        if (this.mSuccess) {
            synchronized (this.mLock) {
                this.mFh.getEFLinearRecordSize(efid, getPBPath(efid), obtainMessage(6, adnRecNum, efid, emails));
                try {
                    this.mLock.wait();
                } catch (InterruptedException e) {
                    Rlog.e(LOG_TAG, "interrupted while trying to update by search");
                }
            }
        }
        if (this.mEmailPresentInIap && this.mSuccess && !TextUtils.isEmpty(oldEmail) && TextUtils.isEmpty(newEmail)) {
            this.mSuccess = updateIapFile(adnRecNum, oldEmail, newEmail, USIM_EFEMAIL_TAG);
        }
        return this.mSuccess;
    }

    public boolean updateAnrFile(int adnRecNum, String oldAnr, String newAnr, int efidIndex) {
        int pbrIndex = getPbrIndexBy(adnRecNum - 1);
        int efid = getEfidByTag(pbrIndex, 196, efidIndex);
        if (oldAnr == null) {
            oldAnr = "";
        }
        if (newAnr == null) {
            newAnr = "";
        }
        String anrs = oldAnr + "," + newAnr;
        this.mSuccess = false;
        log("updateAnrFile oldAnr : " + oldAnr + ", newAnr:" + newAnr + " anrs:" + anrs + ", efid" + efid + ", adnRecNum: " + adnRecNum);
        if (efid == -1) {
            return this.mSuccess;
        }
        if (!this.mAnrPresentInIap || !TextUtils.isEmpty(oldAnr) || TextUtils.isEmpty(newAnr)) {
            this.mSuccess = true;
        } else if (getEmptyAnrNum_Pbrindex(pbrIndex) == 0) {
            log("updateAnrFile getEmptyAnrNum_Pbrindex=0, pbrIndex is " + pbrIndex);
            this.mSuccess = true;
            return this.mSuccess;
        } else {
            this.mSuccess = updateIapFile(adnRecNum, oldAnr, newAnr, 196);
        }
        synchronized (this.mLock) {
            this.mFh.getEFLinearRecordSize(efid, getPBPath(efid), obtainMessage(7, adnRecNum, efid, anrs));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "interrupted while trying to update by search");
            }
        }
        if (this.mAnrPresentInIap && this.mSuccess && !TextUtils.isEmpty(oldAnr) && TextUtils.isEmpty(newAnr)) {
            this.mSuccess = updateIapFile(adnRecNum, oldAnr, newAnr, 196);
        }
        return this.mSuccess;
    }

    private boolean updateIapFile(int adnRecNum, String oldValue, String newValue, int tag) {
        int efid = getEfidByTag(getPbrIndexBy(adnRecNum - 1), 193, 0);
        this.mSuccess = false;
        int recordNumber = -1;
        if (efid == -1) {
            return this.mSuccess;
        }
        switch (tag) {
            case 196:
                recordNumber = getAnrRecNumber(adnRecNum - 1, this.mPhoneBookRecords.size(), oldValue);
                break;
            case USIM_EFEMAIL_TAG /* 202 */:
                recordNumber = getEmailRecNumber(adnRecNum - 1, this.mPhoneBookRecords.size(), oldValue);
                break;
        }
        if (TextUtils.isEmpty(newValue)) {
            recordNumber = -1;
        }
        log("updateIapFile  efid=" + efid + ", recordNumber= " + recordNumber + ", adnRecNum=" + adnRecNum);
        synchronized (this.mLock) {
            this.mFh.getEFLinearRecordSize(efid, getPBPath(efid), obtainMessage(10, adnRecNum, recordNumber, Integer.valueOf(tag)));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "interrupted while trying to update by search");
            }
        }
        return this.mSuccess;
    }

    private int getEfidByTag(int recNum, int tag, int efidIndex) {
        int efid;
        this.mPbrFile.mFileIds.size();
        Map<Integer, Integer> fileIds = this.mPbrFile.mFileIds.get(Integer.valueOf(recNum));
        if (fileIds == null || !fileIds.containsKey(Integer.valueOf(tag))) {
            return -1;
        }
        if (!this.mEmailPresentInIap && USIM_EFEMAIL_TAG == tag) {
            efid = this.mPbrFile.mEmailFileIds.get(Integer.valueOf(recNum)).get(efidIndex).intValue();
        } else if (this.mAnrPresentInIap || 196 != tag) {
            efid = fileIds.get(Integer.valueOf(tag)).intValue();
        } else {
            efid = this.mPbrFile.mAnrFileIds.get(Integer.valueOf(recNum)).get(efidIndex).intValue();
        }
        return efid;
    }

    public int getPbrIndexBy(int adnIndex) {
        int len = this.mAdnLengthList.size();
        int size = 0;
        for (int i = 0; i < len; i++) {
            size += this.mAdnLengthList.get(i).intValue();
            if (adnIndex < size) {
                return i;
            }
        }
        return -1;
    }

    private int getInitIndexBy(int pbrIndex) {
        int index = 0;
        while (pbrIndex > 0) {
            index += this.mAdnLengthList.get(pbrIndex - 1).intValue();
            pbrIndex--;
        }
        return index;
    }

    private boolean hasRecordIn(Map<Integer, ArrayList<byte[]>> record, int pbrIndex) {
        if (record != null && !record.isEmpty() && record.get(Integer.valueOf(pbrIndex)) != null) {
            return true;
        }
        Rlog.e(LOG_TAG, "record [" + record + "] is empty in pbrIndex" + pbrIndex);
        return false;
    }

    private void updatePhoneAdnRecordWithEmail(int pbrIndex) {
        if (hasRecordIn(this.mEmailFileRecord, pbrIndex)) {
            int numAdnRecs = this.mAdnLengthList.get(pbrIndex).intValue();
            if (!this.mEmailPresentInIap || !hasRecordIn(this.mIapFileRecord, pbrIndex)) {
                int len = this.mAdnLengthList.get(pbrIndex).intValue();
                if (!this.mEmailPresentInIap) {
                    parseType1EmailFile(len, pbrIndex);
                    return;
                }
                return;
            }
            for (int i = 0; i < numAdnRecs; i++) {
                try {
                    byte b = this.mIapFileRecord.get(Integer.valueOf(pbrIndex)).get(i)[this.mEmailTagNumberInIap];
                    if (b > 0) {
                        String[] emails = {readEmailRecord(b - 1, pbrIndex, 0)};
                        int adnRecIndex = i + getInitIndexBy(pbrIndex);
                        AdnRecord rec = this.mPhoneBookRecords.get(adnRecIndex);
                        if (rec != null && !TextUtils.isEmpty(emails[0])) {
                            rec.setEmails(emails);
                            this.mPhoneBookRecords.set(adnRecIndex, rec);
                            this.mEmailFlags.get(Integer.valueOf(pbrIndex)).set(b - 1, 1);
                        }
                    }
                } catch (IndexOutOfBoundsException e) {
                    Rlog.e(LOG_TAG, "Error: Improper ICC card: No IAP record for ADN, continuing");
                }
            }
            log("updatePhoneAdnRecordWithEmail: no need to parse type1 EMAIL file");
        }
    }

    private void updatePhoneAdnRecordWithAnr(int pbrIndex) {
        if (hasRecordIn(this.mAnrFileRecord, pbrIndex)) {
            int numAdnRecs = this.mAdnLengthList.get(pbrIndex).intValue();
            if (this.mAnrPresentInIap && hasRecordIn(this.mIapFileRecord, pbrIndex)) {
                for (int i = 0; i < numAdnRecs; i++) {
                    try {
                        byte b = this.mIapFileRecord.get(Integer.valueOf(pbrIndex)).get(i)[this.mAnrTagNumberInIap];
                        if (b > 0) {
                            String[] anrs = {readAnrRecord(b - 1, pbrIndex, 0)};
                            int adnRecIndex = i + getInitIndexBy(pbrIndex);
                            AdnRecord rec = this.mPhoneBookRecords.get(adnRecIndex);
                            if (rec != null && !TextUtils.isEmpty(anrs[0])) {
                                rec.setAdditionalNumbers(anrs);
                                this.mPhoneBookRecords.set(adnRecIndex, rec);
                                this.mAnrFlags.get(Integer.valueOf(pbrIndex)).set(b - 1, 1);
                            }
                        }
                    } catch (IndexOutOfBoundsException e) {
                        Rlog.e(LOG_TAG, "Error: Improper ICC card: No IAP record for ADN, continuing");
                    }
                }
                log("updatePhoneAdnRecordWithAnr: no need to parse type1 ANR file");
            } else if (!this.mAnrPresentInIap) {
                parseType1AnrFile(numAdnRecs, pbrIndex);
            }
        }
    }

    void parseType1EmailFile(int numRecs, int pbrIndex) {
        AdnRecord rec;
        int numEmailFiles = this.mPbrFile.mEmailFileIds.get(Integer.valueOf(pbrIndex)).size();
        ArrayList<String> emailList = new ArrayList<>();
        int adnInitIndex = getInitIndexBy(pbrIndex);
        if (hasRecordIn(this.mEmailFileRecord, pbrIndex)) {
            log("parseType1EmailFile: pbrIndex is: " + pbrIndex + ", numRecs is: " + numRecs);
            for (int i = 0; i < numRecs; i++) {
                int count = 0;
                emailList.clear();
                for (int j = 0; j < numEmailFiles; j++) {
                    String email = readEmailRecord(i, pbrIndex, j * numRecs);
                    emailList.add(email);
                    if (!TextUtils.isEmpty(email)) {
                        count++;
                        this.mEmailFlags.get(Integer.valueOf(pbrIndex)).set((j * numRecs) + i, 1);
                    }
                }
                if (!(count == 0 || (rec = this.mPhoneBookRecords.get(i + adnInitIndex)) == null)) {
                    String[] emails = new String[emailList.size()];
                    System.arraycopy(emailList.toArray(), 0, emails, 0, emailList.size());
                    rec.setEmails(emails);
                    this.mPhoneBookRecords.set(i + adnInitIndex, rec);
                }
            }
        }
    }

    void parseType1AnrFile(int numRecs, int pbrIndex) {
        AdnRecord rec;
        int numAnrFiles = this.mPbrFile.mAnrFileIds.get(Integer.valueOf(pbrIndex)).size();
        ArrayList<String> anrList = new ArrayList<>();
        int adnInitIndex = getInitIndexBy(pbrIndex);
        if (hasRecordIn(this.mAnrFileRecord, pbrIndex)) {
            log("parseType1AnrFile: pbrIndex is: " + pbrIndex + ", numRecs is: " + numRecs + ", numAnrFiles " + numAnrFiles);
            for (int i = 0; i < numRecs; i++) {
                int count = 0;
                anrList.clear();
                for (int j = 0; j < numAnrFiles; j++) {
                    String anr = readAnrRecord(i, pbrIndex, j * numRecs);
                    anrList.add(anr);
                    if (!TextUtils.isEmpty(anr)) {
                        count++;
                        this.mAnrFlags.get(Integer.valueOf(pbrIndex)).set((j * numRecs) + i, 1);
                    }
                }
                if (!(count == 0 || (rec = this.mPhoneBookRecords.get(i + adnInitIndex)) == null)) {
                    String[] anrs = new String[anrList.size()];
                    System.arraycopy(anrList.toArray(), 0, anrs, 0, anrList.size());
                    rec.setAdditionalNumbers(anrs);
                    this.mPhoneBookRecords.set(i + adnInitIndex, rec);
                }
            }
        }
    }

    private String readEmailRecord(int recNum, int pbrIndex, int offSet) {
        if (!hasRecordIn(this.mEmailFileRecord, pbrIndex)) {
            return null;
        }
        try {
            byte[] emailRec = this.mEmailFileRecord.get(Integer.valueOf(pbrIndex)).get(recNum + offSet);
            if (this.mEmailPresentInIap) {
                return IccUtils.adnStringFieldToString(emailRec, 0, emailRec.length - 2);
            }
            return IccUtils.adnStringFieldToString(emailRec, 0, emailRec.length);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private String readAnrRecord(int recNum, int pbrIndex, int offSet) {
        if (!hasRecordIn(this.mAnrFileRecord, pbrIndex)) {
            return null;
        }
        try {
            byte[] anrRec = this.mAnrFileRecord.get(Integer.valueOf(pbrIndex)).get(recNum + offSet);
            int numberLength = anrRec[1] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT;
            if (numberLength > 11) {
                return "";
            }
            return PhoneNumberUtils.calledPartyBCDToString(anrRec, 2, numberLength);
        } catch (IndexOutOfBoundsException e) {
            Rlog.e(LOG_TAG, "Error: Improper ICC card: No anr record for ADN, continuing");
            return null;
        }
    }

    private void readAdnFileAndWait(int recNum) {
        Map<Integer, Integer> fileIds = this.mPbrFile.mFileIds.get(Integer.valueOf(recNum));
        if (fileIds != null && !fileIds.isEmpty()) {
            int extEf = 0;
            if (fileIds.containsKey(194)) {
                extEf = fileIds.get(194).intValue();
            }
            log("readAdnFileAndWait adn efid is : " + fileIds.get(192));
            this.mAdnCache.requestLoadAllAdnLike(fileIds.get(192).intValue(), extEf, getPBPath(fileIds.get(192).intValue()), obtainMessage(2, Integer.valueOf(recNum)));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
            }
        }
    }

    private int getEmailRecNumber(int adnRecIndex, int numRecs, String oldEmail) {
        int pbrIndex = getPbrIndexBy(adnRecIndex);
        int recordIndex = adnRecIndex - getInitIndexBy(pbrIndex);
        log("getEmailRecNumber adnRecIndex is: " + adnRecIndex + ", recordIndex is :" + recordIndex);
        if (!hasRecordIn(this.mEmailFileRecord, pbrIndex)) {
            log("getEmailRecNumber recordNumber is: -1");
            return -1;
        } else if (!this.mEmailPresentInIap || !hasRecordIn(this.mIapFileRecord, pbrIndex)) {
            return recordIndex + 1;
        } else {
            byte[] record = null;
            try {
                record = this.mIapFileRecord.get(Integer.valueOf(pbrIndex)).get(recordIndex);
            } catch (IndexOutOfBoundsException e) {
                Rlog.e(LOG_TAG, "IndexOutOfBoundsException in getEmailRecNumber");
            }
            if (record == null || record[this.mEmailTagNumberInIap] <= 0) {
                int recsSize = this.mEmailFileRecord.get(Integer.valueOf(pbrIndex)).size();
                log("getEmailRecNumber recsSize is: " + recsSize);
                if (TextUtils.isEmpty(oldEmail)) {
                    for (int i = 0; i < recsSize; i++) {
                        if (TextUtils.isEmpty(readEmailRecord(i, pbrIndex, 0))) {
                            log("getEmailRecNumber: Got empty record.Email record num is :" + (i + 1));
                            return i + 1;
                        }
                    }
                }
                log("getEmailRecNumber: no email record index found");
                return -1;
            }
            byte b = record[this.mEmailTagNumberInIap];
            log(" getEmailRecNumber: record is " + IccUtils.bytesToHexString(record) + ", the email recordNumber is :" + ((int) b));
            return b;
        }
    }

    private int getAnrRecNumber(int adnRecIndex, int numRecs, String oldAnr) {
        int pbrIndex = getPbrIndexBy(adnRecIndex);
        int recordIndex = adnRecIndex - getInitIndexBy(pbrIndex);
        if (!hasRecordIn(this.mAnrFileRecord, pbrIndex)) {
            return -1;
        }
        if (!this.mAnrPresentInIap || !hasRecordIn(this.mIapFileRecord, pbrIndex)) {
            return recordIndex + 1;
        }
        byte[] record = null;
        try {
            record = this.mIapFileRecord.get(Integer.valueOf(pbrIndex)).get(recordIndex);
        } catch (IndexOutOfBoundsException e) {
            Rlog.e(LOG_TAG, "IndexOutOfBoundsException in getAnrRecNumber");
        }
        if (record == null || record[this.mAnrTagNumberInIap] <= 0) {
            int recsSize = this.mAnrFileRecord.get(Integer.valueOf(pbrIndex)).size();
            log("getAnrRecNumber: anr record size is :" + recsSize);
            if (TextUtils.isEmpty(oldAnr)) {
                for (int i = 0; i < recsSize; i++) {
                    if (TextUtils.isEmpty(readAnrRecord(i, pbrIndex, 0))) {
                        log("getAnrRecNumber: Empty anr record. Anr record num is :" + (i + 1));
                        return i + 1;
                    }
                }
            }
            log("getAnrRecNumber: no anr record index found");
            return -1;
        }
        byte b = record[this.mAnrTagNumberInIap];
        log("getAnrRecNumber: recnum from iap is :" + ((int) b));
        return b;
    }

    private byte[] buildEmailData(int length, int adnRecIndex, String email) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = -1;
        }
        if (TextUtils.isEmpty(email)) {
            log("[buildEmailData] Empty email record");
            return data;
        }
        byte[] byteEmail = GsmAlphabet.stringToGsm8BitPacked(email);
        if (byteEmail.length > (this.mEmailPresentInIap ? length - 2 : length)) {
            log("[buildEmailData] wrong email length");
            return null;
        }
        System.arraycopy(byteEmail, 0, data, 0, byteEmail.length);
        if (this.mEmailPresentInIap) {
            data[length - 1] = (byte) ((adnRecIndex - getInitIndexBy(getPbrIndexBy(adnRecIndex))) + 1);
        }
        log("buildEmailData: data is" + IccUtils.bytesToHexString(data));
        return data;
    }

    private byte[] buildAnrData(int length, int adnRecIndex, String anr) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = -1;
        }
        if (TextUtils.isEmpty(anr)) {
            log("[buildAnrData] Empty anr record");
            return data;
        }
        data[0] = 0;
        byte[] byteAnr = PhoneNumberUtils.numberToCalledPartyBCD(anr);
        if (byteAnr == null) {
            return null;
        }
        if (byteAnr.length > 11) {
            log("[buildAnrData] wrong ANR length");
            return null;
        }
        System.arraycopy(byteAnr, 0, data, 2, byteAnr.length);
        data[1] = (byte) byteAnr.length;
        data[13] = -1;
        data[14] = -1;
        if (length == 17) {
            data[16] = (byte) ((adnRecIndex - getInitIndexBy(getPbrIndexBy(adnRecIndex))) + 1);
        }
        log("buildAnrData: data is" + IccUtils.bytesToHexString(data));
        return data;
    }

    private void createPbrFile(ArrayList<byte[]> records) {
        if (records == null) {
            this.mPbrFile = null;
            this.mIsPbrPresent = false;
            return;
        }
        this.mPbrFile = new PbrFile(records);
    }

    private void putValidRecNums(int pbrIndex) {
        ArrayList<Integer> recordNums = new ArrayList<>();
        log("pbr index is " + pbrIndex + ", initAdnIndex is " + getInitIndexBy(pbrIndex));
        for (int i = 0; i < this.mAdnLengthList.get(pbrIndex).intValue(); i++) {
            recordNums.add(Integer.valueOf(i + 1));
        }
        if (recordNums.size() == 0) {
            recordNums.add(1);
        }
        this.mRecordNums.put(Integer.valueOf(pbrIndex), recordNums);
    }

    private ArrayList<Integer> getValidRecordNums(int pbrIndex) {
        return this.mRecordNums.get(Integer.valueOf(pbrIndex));
    }

    private boolean hasValidRecords(int pbrIndex) {
        return this.mRecordNums.get(Integer.valueOf(pbrIndex)).size() > 0;
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        String oldAnr = null;
        String newAnr = null;
        String oldEmail = null;
        String newEmail = null;
        switch (msg.what) {
            case 1:
                log("Loading PBR done");
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    createPbrFile((ArrayList) ar.result);
                }
                synchronized (this.mLock) {
                    this.mLock.notify();
                }
                return;
            case 2:
                log("Loading USIM ADN records done");
                AsyncResult ar2 = (AsyncResult) msg.obj;
                int pbrIndex = ((Integer) ar2.userObj).intValue();
                if (ar2.exception == null) {
                    this.mPhoneBookRecords.addAll((ArrayList) ar2.result);
                    this.mAdnLengthList.add(pbrIndex, Integer.valueOf(((ArrayList) ar2.result).size()));
                    putValidRecNums(pbrIndex);
                } else {
                    log("can't load USIM ADN records");
                }
                synchronized (this.mLock) {
                    this.mLock.notify();
                }
                return;
            case 3:
                log("Loading USIM IAP records done");
                AsyncResult ar3 = (AsyncResult) msg.obj;
                int pbrIndex2 = ((Integer) ar3.userObj).intValue();
                if (ar3.exception == null) {
                    this.mIapFileRecord.put(Integer.valueOf(pbrIndex2), (ArrayList) ar3.result);
                }
                synchronized (this.mLock) {
                    this.mLock.notify();
                }
                return;
            case 4:
                log("Loading USIM Email records done");
                AsyncResult ar4 = (AsyncResult) msg.obj;
                int pbrIndex3 = ((Integer) ar4.userObj).intValue();
                if (ar4.exception == null && this.mPbrFile != null) {
                    ArrayList<byte[]> tmpList = this.mEmailFileRecord.get(Integer.valueOf(pbrIndex3));
                    if (tmpList == null) {
                        this.mEmailFileRecord.put(Integer.valueOf(pbrIndex3), (ArrayList) ar4.result);
                    } else {
                        tmpList.addAll((ArrayList) ar4.result);
                        this.mEmailFileRecord.put(Integer.valueOf(pbrIndex3), tmpList);
                    }
                    log("handlemessage EVENT_EMAIL_LOAD_DONE size is: " + this.mEmailFileRecord.get(Integer.valueOf(pbrIndex3)).size());
                }
                synchronized (this.mLock) {
                    this.mLock.notify();
                }
                return;
            case 5:
                log("Loading USIM Anr records done");
                AsyncResult ar5 = (AsyncResult) msg.obj;
                int pbrIndex4 = ((Integer) ar5.userObj).intValue();
                if (ar5.exception == null && this.mPbrFile != null) {
                    ArrayList<byte[]> tmp = this.mAnrFileRecord.get(Integer.valueOf(pbrIndex4));
                    if (tmp == null) {
                        this.mAnrFileRecord.put(Integer.valueOf(pbrIndex4), (ArrayList) ar5.result);
                    } else {
                        tmp.addAll((ArrayList) ar5.result);
                        this.mAnrFileRecord.put(Integer.valueOf(pbrIndex4), tmp);
                    }
                    log("handlemessage EVENT_ANR_LOAD_DONE size is: " + this.mAnrFileRecord.get(Integer.valueOf(pbrIndex4)).size());
                }
                synchronized (this.mLock) {
                    this.mLock.notify();
                }
                return;
            case 6:
                log("Loading EF_EMAIL_RECORD_SIZE_DONE");
                AsyncResult ar6 = (AsyncResult) msg.obj;
                String emails = (String) ar6.userObj;
                int adnRecIndex = msg.arg1 - 1;
                int efid = msg.arg2;
                String[] email = emails.split(",");
                if (email.length == 1) {
                    oldEmail = email[0];
                    newEmail = "";
                } else if (email.length > 1) {
                    oldEmail = email[0];
                    newEmail = email[1];
                }
                if (ar6.exception != null) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                    }
                    return;
                }
                int[] recordSize = (int[]) ar6.result;
                int recordNumber = getEmailRecNumber(adnRecIndex, this.mPhoneBookRecords.size(), oldEmail);
                if (recordSize.length != 3 || recordNumber > recordSize[2] || recordNumber <= 0) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                    }
                    return;
                }
                byte[] data = buildEmailData(recordSize[0], adnRecIndex, newEmail);
                if (data == null) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                    }
                    return;
                }
                int actualRecNumber = recordNumber;
                if (!this.mEmailPresentInIap) {
                    int efidIndex = this.mPbrFile.mEmailFileIds.get(Integer.valueOf(getPbrIndexBy(adnRecIndex))).indexOf(Integer.valueOf(efid));
                    if (efidIndex == -1) {
                        log("wrong efid index:" + efid);
                        return;
                    } else {
                        actualRecNumber = recordNumber + (this.mAdnLengthList.get(getPbrIndexBy(adnRecIndex)).intValue() * efidIndex);
                        log("EMAIL index:" + efidIndex + " efid:" + efid + " actual RecNumber:" + actualRecNumber);
                    }
                }
                this.mFh.updateEFLinearFixed(efid, getPBPath(efid), recordNumber, data, null, obtainMessage(8, actualRecNumber, adnRecIndex, data));
                this.mPendingExtLoads = 1;
                return;
            case 7:
                log("Loading EF_ANR_RECORD_SIZE_DONE");
                AsyncResult ar7 = (AsyncResult) msg.obj;
                String anrs = (String) ar7.userObj;
                int adnRecIndex2 = msg.arg1 - 1;
                int efid2 = msg.arg2;
                String[] anr = anrs.split(",");
                if (anr.length == 1) {
                    oldAnr = anr[0];
                    newAnr = "";
                } else if (anr.length > 1) {
                    oldAnr = anr[0];
                    newAnr = anr[1];
                }
                if (ar7.exception != null) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                    }
                    return;
                }
                int[] recordSize2 = (int[]) ar7.result;
                int recordNumber2 = getAnrRecNumber(adnRecIndex2, this.mPhoneBookRecords.size(), oldAnr);
                if (recordSize2.length != 3 || recordNumber2 > recordSize2[2] || recordNumber2 <= 0) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                    }
                    return;
                }
                byte[] data2 = buildAnrData(recordSize2[0], adnRecIndex2, newAnr);
                if (data2 == null) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                    }
                    return;
                }
                int actualRecNumber2 = recordNumber2;
                if (!this.mAnrPresentInIap) {
                    int efidIndex2 = this.mPbrFile.mAnrFileIds.get(Integer.valueOf(getPbrIndexBy(adnRecIndex2))).indexOf(Integer.valueOf(efid2));
                    if (efidIndex2 == -1) {
                        log("wrong efid index:" + efid2);
                        return;
                    } else {
                        actualRecNumber2 = recordNumber2 + (this.mAdnLengthList.get(getPbrIndexBy(adnRecIndex2)).intValue() * efidIndex2);
                        log("ANR index:" + efidIndex2 + " efid:" + efid2 + " actual RecNumber:" + actualRecNumber2);
                    }
                }
                this.mFh.updateEFLinearFixed(efid2, getPBPath(efid2), recordNumber2, data2, null, obtainMessage(9, actualRecNumber2, adnRecIndex2, data2));
                this.mPendingExtLoads = 1;
                return;
            case 8:
                log("Loading UPDATE_EMAIL_RECORD_DONE");
                AsyncResult ar8 = (AsyncResult) msg.obj;
                if (ar8.exception != null) {
                    this.mSuccess = false;
                }
                byte[] data3 = (byte[]) ar8.userObj;
                int recordNumber3 = msg.arg1;
                int pbrIndex5 = getPbrIndexBy(msg.arg2);
                log("EVENT_UPDATE_EMAIL_RECORD_DONE");
                this.mPendingExtLoads = 0;
                this.mSuccess = true;
                this.mEmailFileRecord.get(Integer.valueOf(pbrIndex5)).set(recordNumber3 - 1, data3);
                int i = 0;
                while (true) {
                    if (i < data3.length) {
                        log("EVENT_UPDATE_EMAIL_RECORD_DONE data = " + ((int) data3[i]) + ",i is " + i);
                        if (data3[i] != -1) {
                            log("EVENT_UPDATE_EMAIL_RECORD_DONE data !=0xff");
                            this.mEmailFlags.get(Integer.valueOf(pbrIndex5)).set(recordNumber3 - 1, 1);
                        } else {
                            this.mEmailFlags.get(Integer.valueOf(pbrIndex5)).set(recordNumber3 - 1, 0);
                            i++;
                        }
                    }
                }
                synchronized (this.mLock) {
                    this.mLock.notify();
                }
                return;
            case 9:
                log("Loading UPDATE_ANR_RECORD_DONE");
                AsyncResult ar9 = (AsyncResult) msg.obj;
                byte[] data4 = (byte[]) ar9.userObj;
                int recordNumber4 = msg.arg1;
                int pbrIndex6 = getPbrIndexBy(msg.arg2);
                if (ar9.exception != null) {
                    this.mSuccess = false;
                }
                log("EVENT_UPDATE_ANR_RECORD_DONE");
                this.mPendingExtLoads = 0;
                this.mSuccess = true;
                this.mAnrFileRecord.get(Integer.valueOf(pbrIndex6)).set(recordNumber4 - 1, data4);
                int i2 = 0;
                while (true) {
                    if (i2 < data4.length) {
                        if (data4[i2] != -1) {
                            this.mAnrFlags.get(Integer.valueOf(pbrIndex6)).set(recordNumber4 - 1, 1);
                        } else {
                            this.mAnrFlags.get(Integer.valueOf(pbrIndex6)).set(recordNumber4 - 1, 0);
                            i2++;
                        }
                    }
                }
                synchronized (this.mLock) {
                    this.mLock.notify();
                }
                return;
            case 10:
                log("EVENT_EF_IAP_RECORD_SIZE_DONE");
                AsyncResult ar10 = (AsyncResult) msg.obj;
                int recordNumber5 = msg.arg2;
                int adnRecIndex3 = msg.arg1 - 1;
                getEfidByTag(getPbrIndexBy(adnRecIndex3), 193, 0);
                int tag = ((Integer) ar10.userObj).intValue();
                if (ar10.exception != null) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                    }
                    return;
                }
                int pbrIndex7 = getPbrIndexBy(adnRecIndex3);
                int efid3 = getEfidByTag(pbrIndex7, 193, 0);
                int[] recordSize3 = (int[]) ar10.result;
                int recordIndex = adnRecIndex3 - getInitIndexBy(pbrIndex7);
                log("handleIAP_RECORD_SIZE_DONE adnRecIndex is: " + adnRecIndex3 + ", recordNumber is: " + recordNumber5 + ", recordIndex is: " + recordIndex);
                if (recordSize3.length != 3 || recordIndex + 1 > recordSize3[2] || recordNumber5 == 0) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                    }
                    return;
                } else if (hasRecordIn(this.mIapFileRecord, pbrIndex7)) {
                    byte[] data5 = this.mIapFileRecord.get(Integer.valueOf(pbrIndex7)).get(recordIndex);
                    byte[] record_data = new byte[data5.length];
                    System.arraycopy(data5, 0, record_data, 0, record_data.length);
                    switch (tag) {
                        case 196:
                            record_data[this.mAnrTagNumberInIap] = (byte) recordNumber5;
                            break;
                        case USIM_EFEMAIL_TAG /* 202 */:
                            record_data[this.mEmailTagNumberInIap] = (byte) recordNumber5;
                            break;
                    }
                    this.mPendingExtLoads = 1;
                    log(" IAP  efid= " + efid3 + ", update IAP index= " + recordIndex + " with value= " + IccUtils.bytesToHexString(record_data));
                    this.mFh.updateEFLinearFixed(efid3, getPBPath(efid3), recordIndex + 1, record_data, null, obtainMessage(11, adnRecIndex3, recordNumber5, record_data));
                    return;
                } else {
                    return;
                }
            case 11:
                log("EVENT_UPDATE_IAP_RECORD_DONE");
                AsyncResult ar11 = (AsyncResult) msg.obj;
                if (ar11.exception != null) {
                    this.mSuccess = false;
                }
                byte[] data6 = (byte[]) ar11.userObj;
                int adnRecIndex4 = msg.arg1;
                int pbrIndex8 = getPbrIndexBy(adnRecIndex4);
                int recordIndex2 = adnRecIndex4 - getInitIndexBy(pbrIndex8);
                log("handleMessage EVENT_UPDATE_IAP_RECORD_DONE recordIndex is: " + recordIndex2 + ", adnRecIndex is: " + adnRecIndex4);
                this.mPendingExtLoads = 0;
                this.mSuccess = true;
                this.mIapFileRecord.get(Integer.valueOf(pbrIndex8)).set(recordIndex2, data6);
                log("the iap email recordNumber is :" + ((int) data6[this.mEmailTagNumberInIap]));
                synchronized (this.mLock) {
                    this.mLock.notify();
                }
                return;
            default:
                return;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class PbrFile {
        HashMap<Integer, Map<Integer, Integer>> mFileIds = new HashMap<>();
        HashMap<Integer, ArrayList<Integer>> mAnrFileIds = new HashMap<>();
        HashMap<Integer, ArrayList<Integer>> mEmailFileIds = new HashMap<>();

        PbrFile(ArrayList<byte[]> records) {
            int recNum = 0;
            Iterator i$ = records.iterator();
            while (i$.hasNext()) {
                byte[] record = i$.next();
                parseTag(new SimTlv(record, 0, record.length), recNum);
                recNum++;
            }
        }

        void parseTag(SimTlv tlv, int recNum) {
            Rlog.d(UsimPhoneBookManager.LOG_TAG, "parseTag: recNum=" + recNum);
            Map<Integer, Integer> val = new HashMap<>();
            ArrayList<Integer> anrList = new ArrayList<>();
            ArrayList<Integer> emailList = new ArrayList<>();
            do {
                int tag = tlv.getTag();
                switch (tag) {
                    case 168:
                    case 169:
                    case 170:
                        byte[] data = tlv.getData();
                        parseEf(new SimTlv(data, 0, data.length), val, tag, anrList, emailList);
                        break;
                }
            } while (tlv.nextObject());
            if (anrList.size() != 0) {
                this.mAnrFileIds.put(Integer.valueOf(recNum), anrList);
                Rlog.d(UsimPhoneBookManager.LOG_TAG, "parseTag: recNum=" + recNum + " ANR file list:" + anrList);
            }
            if (emailList.size() != 0) {
                Rlog.d(UsimPhoneBookManager.LOG_TAG, "parseTag: recNum=" + recNum + " EMAIL file list:" + emailList);
                this.mEmailFileIds.put(Integer.valueOf(recNum), emailList);
            }
            this.mFileIds.put(Integer.valueOf(recNum), val);
        }

        void parseEf(SimTlv tlv, Map<Integer, Integer> val, int parentTag, ArrayList<Integer> anrList, ArrayList<Integer> emailList) {
            int tagNumberWithinParentTag = 0;
            do {
                int tag = tlv.getTag();
                if (parentTag == 168 && tag == 193) {
                    UsimPhoneBookManager.this.mIapPresent = true;
                }
                if (parentTag == 169 && UsimPhoneBookManager.this.mIapPresent && tag == UsimPhoneBookManager.USIM_EFEMAIL_TAG) {
                    UsimPhoneBookManager.this.mEmailPresentInIap = true;
                    UsimPhoneBookManager.this.mEmailTagNumberInIap = tagNumberWithinParentTag;
                    UsimPhoneBookManager.this.log("parseEf: EmailPresentInIap tag = " + UsimPhoneBookManager.this.mEmailTagNumberInIap);
                }
                if (parentTag == 169 && UsimPhoneBookManager.this.mIapPresent && tag == 196) {
                    UsimPhoneBookManager.this.mAnrPresentInIap = true;
                    UsimPhoneBookManager.this.mAnrTagNumberInIap = tagNumberWithinParentTag;
                    UsimPhoneBookManager.this.log("parseEf: AnrPresentInIap tag = " + UsimPhoneBookManager.this.mAnrTagNumberInIap);
                }
                switch (tag) {
                    case 192:
                    case 193:
                    case 194:
                    case 195:
                    case 196:
                    case 197:
                    case UsimPhoneBookManager.USIM_EFGRP_TAG /* 198 */:
                    case UsimPhoneBookManager.USIM_EFAAS_TAG /* 199 */:
                    case 200:
                    case UsimPhoneBookManager.USIM_EFUID_TAG /* 201 */:
                    case UsimPhoneBookManager.USIM_EFEMAIL_TAG /* 202 */:
                    case UsimPhoneBookManager.USIM_EFCCP1_TAG /* 203 */:
                        byte[] data = tlv.getData();
                        int efid = ((data[0] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT) << 8) | (data[1] & OemCdmaTelephonyManager.OEM_RIL_CDMA_RESET_TO_FACTORY.RESET_DEFAULT);
                        val.put(Integer.valueOf(tag), Integer.valueOf(efid));
                        if (parentTag == 168) {
                            if (tag == 196) {
                                anrList.add(Integer.valueOf(efid));
                            } else if (tag == UsimPhoneBookManager.USIM_EFEMAIL_TAG) {
                                emailList.add(Integer.valueOf(efid));
                            }
                        }
                        Rlog.d(UsimPhoneBookManager.LOG_TAG, "parseEf.put(" + tag + "," + efid + ") parent tag:" + parentTag);
                        break;
                }
                tagNumberWithinParentTag++;
            } while (tlv.nextObject());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    public int getAnrCount() {
        int count = 0;
        int pbrIndex = this.mAnrFlags.size();
        for (int j = 0; j < pbrIndex; j++) {
            count += this.mAnrFlags.get(Integer.valueOf(j)).size();
        }
        log("getAnrCount count is: " + count);
        return count;
    }

    public int getEmailCount() {
        int count = 0;
        int pbrIndex = this.mEmailFlags.size();
        for (int j = 0; j < pbrIndex; j++) {
            count += this.mEmailFlags.get(Integer.valueOf(j)).size();
        }
        log("getEmailCount count is: " + count);
        return count;
    }

    public int getSpareAnrCount() {
        int count = 0;
        int pbrIndex = this.mAnrFlags.size();
        for (int j = 0; j < pbrIndex; j++) {
            for (int i = 0; i < this.mAnrFlags.get(Integer.valueOf(j)).size(); i++) {
                if (this.mAnrFlags.get(Integer.valueOf(j)).get(i).intValue() == 0) {
                    count++;
                }
            }
        }
        log("getSpareAnrCount count is" + count);
        return count;
    }

    public int getSpareEmailCount() {
        int count = 0;
        int pbrIndex = this.mEmailFlags.size();
        for (int j = 0; j < pbrIndex; j++) {
            for (int i = 0; i < this.mEmailFlags.get(Integer.valueOf(j)).size(); i++) {
                if (this.mEmailFlags.get(Integer.valueOf(j)).get(i).intValue() == 0) {
                    count++;
                }
            }
        }
        log("getSpareEmailCount count is: " + count);
        return count;
    }

    public int getUsimAdnCount() {
        if (this.mPhoneBookRecords == null || this.mPhoneBookRecords.isEmpty()) {
            return 0;
        }
        log("getUsimAdnCount count is" + this.mPhoneBookRecords.size());
        return this.mPhoneBookRecords.size();
    }

    public int getEmptyEmailNum_Pbrindex(int pbrindex) {
        int count = 0;
        if (!this.mEmailPresentInIap) {
            return 1;
        }
        if (this.mEmailFlags.containsKey(Integer.valueOf(pbrindex))) {
            int size = this.mEmailFlags.get(Integer.valueOf(pbrindex)).size();
            for (int i = 0; i < size; i++) {
                if (this.mEmailFlags.get(Integer.valueOf(pbrindex)).get(i).intValue() == 0) {
                    count++;
                }
            }
        }
        return count;
    }

    public int getEmptyAnrNum_Pbrindex(int pbrindex) {
        int count = 0;
        if (!this.mAnrPresentInIap) {
            return 1;
        }
        if (this.mAnrFlags.containsKey(Integer.valueOf(pbrindex))) {
            int size = this.mAnrFlags.get(Integer.valueOf(pbrindex)).size();
            for (int i = 0; i < size; i++) {
                if (this.mAnrFlags.get(Integer.valueOf(pbrindex)).get(i).intValue() == 0) {
                    count++;
                }
            }
        }
        return count;
    }

    public int getEmailFilesCountEachAdn() {
        if (this.mPbrFile == null) {
            Rlog.e(LOG_TAG, "mPbrFile is NULL, exiting from getEmailFilesCountEachAdn");
            return 0;
        }
        Map<Integer, Integer> fileIds = this.mPbrFile.mFileIds.get(0);
        if (fileIds == null || !fileIds.containsKey(Integer.valueOf((int) USIM_EFEMAIL_TAG))) {
            return 0;
        }
        if (!this.mEmailPresentInIap) {
            return this.mPbrFile.mEmailFileIds.get(0).size();
        }
        return 1;
    }

    public int getAnrFilesCountEachAdn() {
        if (this.mPbrFile == null) {
            Rlog.e(LOG_TAG, "mPbrFile is NULL, exiting from getAnrFilesCountEachAdn");
            return 0;
        }
        Map<Integer, Integer> fileIds = this.mPbrFile.mFileIds.get(0);
        if (fileIds == null || !fileIds.containsKey(196)) {
            return 0;
        }
        if (!this.mAnrPresentInIap) {
            return this.mPbrFile.mAnrFileIds.get(0).size();
        }
        return 1;
    }

    public String getPBPath(int efid) {
        return "3F007F105F3A";
    }
}
