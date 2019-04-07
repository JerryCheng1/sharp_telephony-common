package com.android.internal.telephony.gsm;

import android.os.Handler;
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
    private ArrayList<Integer> mAdnLengthList = null;
    private Map<Integer, ArrayList<byte[]>> mAnrFileRecord;
    private Map<Integer, ArrayList<Integer>> mAnrFlags;
    private ArrayList<Integer>[] mAnrFlagsRecord;
    private boolean mAnrPresentInIap = false;
    private int mAnrTagNumberInIap = 0;
    private Map<Integer, ArrayList<byte[]>> mEmailFileRecord;
    private Map<Integer, ArrayList<Integer>> mEmailFlags;
    private ArrayList<Integer>[] mEmailFlagsRecord;
    private boolean mEmailPresentInIap = false;
    private int mEmailTagNumberInIap = 0;
    private IccFileHandler mFh;
    private Map<Integer, ArrayList<byte[]>> mIapFileRecord;
    private boolean mIapPresent = false;
    private Boolean mIsPbrPresent;
    private Object mLock = new Object();
    private PbrFile mPbrFile;
    private int mPendingExtLoads;
    private ArrayList<AdnRecord> mPhoneBookRecords;
    private Map<Integer, ArrayList<Integer>> mRecordNums;
    private boolean mRefreshCache = false;
    private boolean mSuccess = false;

    private class PbrFile {
        HashMap<Integer, ArrayList<Integer>> mAnrFileIds = new HashMap();
        HashMap<Integer, ArrayList<Integer>> mEmailFileIds = new HashMap();
        HashMap<Integer, Map<Integer, Integer>> mFileIds = new HashMap();

        PbrFile(ArrayList<byte[]> arrayList) {
            Iterator it = arrayList.iterator();
            int i = 0;
            while (it.hasNext()) {
                byte[] bArr = (byte[]) it.next();
                parseTag(new SimTlv(bArr, 0, bArr.length), i);
                i++;
            }
        }

        /* Access modifiers changed, original: 0000 */
        public void parseEf(SimTlv simTlv, Map<Integer, Integer> map, int i, ArrayList<Integer> arrayList, ArrayList<Integer> arrayList2) {
            int i2 = 0;
            do {
                int tag = simTlv.getTag();
                if (i == 168 && tag == 193) {
                    UsimPhoneBookManager.this.mIapPresent = true;
                }
                if (i == 169 && UsimPhoneBookManager.this.mIapPresent && tag == UsimPhoneBookManager.USIM_EFEMAIL_TAG) {
                    UsimPhoneBookManager.this.mEmailPresentInIap = true;
                    UsimPhoneBookManager.this.mEmailTagNumberInIap = i2;
                    UsimPhoneBookManager.this.log("parseEf: EmailPresentInIap tag = " + UsimPhoneBookManager.this.mEmailTagNumberInIap);
                }
                if (i == 169 && UsimPhoneBookManager.this.mIapPresent && tag == 196) {
                    UsimPhoneBookManager.this.mAnrPresentInIap = true;
                    UsimPhoneBookManager.this.mAnrTagNumberInIap = i2;
                    UsimPhoneBookManager.this.log("parseEf: AnrPresentInIap tag = " + UsimPhoneBookManager.this.mAnrTagNumberInIap);
                }
                switch (tag) {
                    case 192:
                    case 193:
                    case 194:
                    case 195:
                    case 196:
                    case 197:
                    case UsimPhoneBookManager.USIM_EFGRP_TAG /*198*/:
                    case UsimPhoneBookManager.USIM_EFAAS_TAG /*199*/:
                    case 200:
                    case UsimPhoneBookManager.USIM_EFUID_TAG /*201*/:
                    case UsimPhoneBookManager.USIM_EFEMAIL_TAG /*202*/:
                    case UsimPhoneBookManager.USIM_EFCCP1_TAG /*203*/:
                        byte[] data = simTlv.getData();
                        int i3 = (data[1] & 255) | ((data[0] & 255) << 8);
                        map.put(Integer.valueOf(tag), Integer.valueOf(i3));
                        if (i == 168) {
                            if (tag == 196) {
                                arrayList.add(Integer.valueOf(i3));
                            } else if (tag == UsimPhoneBookManager.USIM_EFEMAIL_TAG) {
                                arrayList2.add(Integer.valueOf(i3));
                            }
                        }
                        Rlog.d(UsimPhoneBookManager.LOG_TAG, "parseEf.put(" + tag + "," + i3 + ") parent tag:" + i);
                        break;
                }
                i2++;
            } while (simTlv.nextObject());
        }

        /* Access modifiers changed, original: 0000 */
        public void parseTag(SimTlv simTlv, int i) {
            Rlog.d(UsimPhoneBookManager.LOG_TAG, "parseTag: recNum=" + i);
            HashMap hashMap = new HashMap();
            ArrayList arrayList = new ArrayList();
            ArrayList arrayList2 = new ArrayList();
            do {
                int tag = simTlv.getTag();
                switch (tag) {
                    case 168:
                    case 169:
                    case 170:
                        byte[] data = simTlv.getData();
                        parseEf(new SimTlv(data, 0, data.length), hashMap, tag, arrayList, arrayList2);
                        break;
                }
            } while (simTlv.nextObject());
            if (arrayList.size() != 0) {
                this.mAnrFileIds.put(Integer.valueOf(i), arrayList);
                Rlog.d(UsimPhoneBookManager.LOG_TAG, "parseTag: recNum=" + i + " ANR file list:" + arrayList);
            }
            if (arrayList2.size() != 0) {
                Rlog.d(UsimPhoneBookManager.LOG_TAG, "parseTag: recNum=" + i + " EMAIL file list:" + arrayList2);
                this.mEmailFileIds.put(Integer.valueOf(i), arrayList2);
            }
            this.mFileIds.put(Integer.valueOf(i), hashMap);
        }
    }

    public UsimPhoneBookManager(IccFileHandler iccFileHandler, AdnRecordCache adnRecordCache) {
        this.mFh = iccFileHandler;
        this.mPhoneBookRecords = new ArrayList();
        this.mAdnLengthList = new ArrayList();
        this.mIapFileRecord = new HashMap();
        this.mEmailFileRecord = new HashMap();
        this.mAnrFileRecord = new HashMap();
        this.mRecordNums = new HashMap();
        this.mPbrFile = null;
        this.mAnrFlags = new HashMap();
        this.mEmailFlags = new HashMap();
        this.mIsPbrPresent = Boolean.valueOf(true);
        this.mAdnCache = adnRecordCache;
    }

    private byte[] buildAnrData(int i, int i2, String str) {
        byte[] bArr = new byte[i];
        for (int i3 = 0; i3 < i; i3++) {
            bArr[i3] = (byte) -1;
        }
        if (TextUtils.isEmpty(str)) {
            log("[buildAnrData] Empty anr record");
            return bArr;
        }
        bArr[0] = (byte) 0;
        byte[] numberToCalledPartyBCD = PhoneNumberUtils.numberToCalledPartyBCD(str);
        if (numberToCalledPartyBCD == null) {
            return null;
        }
        if (numberToCalledPartyBCD.length > 11) {
            log("[buildAnrData] wrong ANR length");
            return null;
        }
        System.arraycopy(numberToCalledPartyBCD, 0, bArr, 2, numberToCalledPartyBCD.length);
        bArr[1] = (byte) numberToCalledPartyBCD.length;
        bArr[13] = (byte) -1;
        bArr[14] = (byte) -1;
        if (i == 17) {
            bArr[16] = (byte) ((i2 - getInitIndexBy(getPbrIndexBy(i2))) + 1);
        }
        log("buildAnrData: data is" + IccUtils.bytesToHexString(bArr));
        return bArr;
    }

    private byte[] buildEmailData(int i, int i2, String str) {
        byte[] bArr = new byte[i];
        for (int i3 = 0; i3 < i; i3++) {
            bArr[i3] = (byte) -1;
        }
        if (TextUtils.isEmpty(str)) {
            log("[buildEmailData] Empty email record");
            return bArr;
        }
        byte[] stringToGsm8BitPacked = GsmAlphabet.stringToGsm8BitPacked(str);
        if (stringToGsm8BitPacked.length > (this.mEmailPresentInIap ? i - 2 : i)) {
            log("[buildEmailData] wrong email length");
            return null;
        }
        System.arraycopy(stringToGsm8BitPacked, 0, bArr, 0, stringToGsm8BitPacked.length);
        if (this.mEmailPresentInIap) {
            bArr[i - 1] = (byte) ((i2 - getInitIndexBy(getPbrIndexBy(i2))) + 1);
        }
        log("buildEmailData: data is" + IccUtils.bytesToHexString(bArr));
        return bArr;
    }

    private void createPbrFile(ArrayList<byte[]> arrayList) {
        if (arrayList == null) {
            this.mPbrFile = null;
            this.mIsPbrPresent = Boolean.valueOf(false);
            return;
        }
        this.mPbrFile = new PbrFile(arrayList);
    }

    private int getAnrRecNumber(int i, int i2, String str) {
        int pbrIndexBy = getPbrIndexBy(i);
        int initIndexBy = i - getInitIndexBy(pbrIndexBy);
        if (!hasRecordIn(this.mAnrFileRecord, pbrIndexBy)) {
            return -1;
        }
        if (!this.mAnrPresentInIap || !hasRecordIn(this.mIapFileRecord, pbrIndexBy)) {
            return initIndexBy + 1;
        }
        byte[] bArr;
        try {
            bArr = (byte[]) ((ArrayList) this.mIapFileRecord.get(Integer.valueOf(pbrIndexBy))).get(initIndexBy);
        } catch (IndexOutOfBoundsException e) {
            Rlog.e(LOG_TAG, "IndexOutOfBoundsException in getAnrRecNumber");
            bArr = null;
        }
        if (bArr == null || bArr[this.mAnrTagNumberInIap] <= (byte) 0) {
            int size = ((ArrayList) this.mAnrFileRecord.get(Integer.valueOf(pbrIndexBy))).size();
            log("getAnrRecNumber: anr record size is :" + size);
            if (TextUtils.isEmpty(str)) {
                for (int i3 = 0; i3 < size; i3++) {
                    if (TextUtils.isEmpty(readAnrRecord(i3, pbrIndexBy, 0))) {
                        log("getAnrRecNumber: Empty anr record. Anr record num is :" + (i3 + 1));
                        return i3 + 1;
                    }
                }
            }
            log("getAnrRecNumber: no anr record index found");
            return -1;
        }
        byte b = bArr[this.mAnrTagNumberInIap];
        log("getAnrRecNumber: recnum from iap is :" + b);
        return b;
    }

    private int getEfidByTag(int i, int i2, int i3) {
        this.mPbrFile.mFileIds.size();
        Map map = (Map) this.mPbrFile.mFileIds.get(Integer.valueOf(i));
        return (map != null && map.containsKey(Integer.valueOf(i2))) ? (this.mEmailPresentInIap || USIM_EFEMAIL_TAG != i2) ? (this.mAnrPresentInIap || 196 != i2) ? ((Integer) map.get(Integer.valueOf(i2))).intValue() : ((Integer) ((ArrayList) this.mPbrFile.mAnrFileIds.get(Integer.valueOf(i))).get(i3)).intValue() : ((Integer) ((ArrayList) this.mPbrFile.mEmailFileIds.get(Integer.valueOf(i))).get(i3)).intValue() : -1;
    }

    private int getEmailRecNumber(int i, int i2, String str) {
        int pbrIndexBy = getPbrIndexBy(i);
        int initIndexBy = i - getInitIndexBy(pbrIndexBy);
        log("getEmailRecNumber adnRecIndex is: " + i + ", recordIndex is :" + initIndexBy);
        if (!hasRecordIn(this.mEmailFileRecord, pbrIndexBy)) {
            log("getEmailRecNumber recordNumber is: " + -1);
            return -1;
        } else if (!this.mEmailPresentInIap || !hasRecordIn(this.mIapFileRecord, pbrIndexBy)) {
            return initIndexBy + 1;
        } else {
            byte[] bArr = null;
            try {
                bArr = (byte[]) ((ArrayList) this.mIapFileRecord.get(Integer.valueOf(pbrIndexBy))).get(initIndexBy);
            } catch (IndexOutOfBoundsException e) {
                Rlog.e(LOG_TAG, "IndexOutOfBoundsException in getEmailRecNumber");
            }
            if (bArr == null || bArr[this.mEmailTagNumberInIap] <= (byte) 0) {
                int size = ((ArrayList) this.mEmailFileRecord.get(Integer.valueOf(pbrIndexBy))).size();
                log("getEmailRecNumber recsSize is: " + size);
                if (TextUtils.isEmpty(str)) {
                    for (int i3 = 0; i3 < size; i3++) {
                        if (TextUtils.isEmpty(readEmailRecord(i3, pbrIndexBy, 0))) {
                            log("getEmailRecNumber: Got empty record.Email record num is :" + (i3 + 1));
                            return i3 + 1;
                        }
                    }
                }
                log("getEmailRecNumber: no email record index found");
                return -1;
            }
            byte b = bArr[this.mEmailTagNumberInIap];
            log(" getEmailRecNumber: record is " + IccUtils.bytesToHexString(bArr) + ", the email recordNumber is :" + b);
            return b;
        }
    }

    private int getInitIndexBy(int i) {
        int i2 = 0;
        while (i > 0) {
            i--;
            i2 = ((Integer) this.mAdnLengthList.get(i - 1)).intValue() + i2;
        }
        return i2;
    }

    private ArrayList<Integer> getValidRecordNums(int i) {
        return (ArrayList) this.mRecordNums.get(Integer.valueOf(i));
    }

    private boolean hasRecordIn(Map<Integer, ArrayList<byte[]>> map, int i) {
        if (map != null && !map.isEmpty() && map.get(Integer.valueOf(i)) != null) {
            return true;
        }
        Rlog.e(LOG_TAG, "record [" + map + "] is empty in pbrIndex" + i);
        return false;
    }

    private boolean hasValidRecords(int i) {
        return ((ArrayList) this.mRecordNums.get(Integer.valueOf(i))).size() > 0;
    }

    private void log(String str) {
        Rlog.d(LOG_TAG, str);
    }

    private void putValidRecNums(int i) {
        ArrayList arrayList = new ArrayList();
        log("pbr index is " + i + ", initAdnIndex is " + getInitIndexBy(i));
        int i2 = 0;
        while (true) {
            int i3 = i2;
            if (i3 >= ((Integer) this.mAdnLengthList.get(i)).intValue()) {
                break;
            }
            arrayList.add(Integer.valueOf(i3 + 1));
            i2 = i3 + 1;
        }
        if (arrayList.size() == 0) {
            arrayList.add(Integer.valueOf(1));
        }
        this.mRecordNums.put(Integer.valueOf(i), arrayList);
    }

    private void readAdnFileAndWait(int i) {
        Map map = (Map) this.mPbrFile.mFileIds.get(Integer.valueOf(i));
        if (map != null && !map.isEmpty()) {
            int intValue = map.containsKey(Integer.valueOf(194)) ? ((Integer) map.get(Integer.valueOf(194))).intValue() : 0;
            log("readAdnFileAndWait adn efid is : " + map.get(Integer.valueOf(192)));
            this.mAdnCache.requestLoadAllAdnLike(((Integer) map.get(Integer.valueOf(192))).intValue(), intValue, getPBPath(((Integer) map.get(Integer.valueOf(192))).intValue()), obtainMessage(2, Integer.valueOf(i)));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
            }
        }
    }

    private void readAnrFileAndWait(int i) {
        if (this.mPbrFile == null) {
            Rlog.e(LOG_TAG, "mPbrFile is NULL, exiting from readAnrFileAndWait");
            return;
        }
        Map map = (Map) this.mPbrFile.mFileIds.get(Integer.valueOf(i));
        if (map != null && !map.isEmpty() && map.containsKey(Integer.valueOf(196))) {
            if (this.mAnrPresentInIap) {
                readIapFileAndWait(((Integer) map.get(Integer.valueOf(193))).intValue(), i);
                if (hasRecordIn(this.mIapFileRecord, i)) {
                    this.mFh.loadEFLinearFixedAll(((Integer) map.get(Integer.valueOf(196))).intValue(), getPBPath(((Integer) map.get(Integer.valueOf(196))).intValue()), obtainMessage(5, Integer.valueOf(i)));
                    log("readAnrFileAndWait anr efid is : " + map.get(Integer.valueOf(196)));
                    try {
                        this.mLock.wait();
                    } catch (InterruptedException e) {
                        Rlog.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
                    }
                } else {
                    Rlog.e(LOG_TAG, "Error: IAP file is empty");
                    return;
                }
            }
            Iterator it = ((ArrayList) this.mPbrFile.mAnrFileIds.get(Integer.valueOf(i))).iterator();
            while (it.hasNext()) {
                int intValue = ((Integer) it.next()).intValue();
                this.mFh.loadEFLinearFixedPart(intValue, getPBPath(intValue), getValidRecordNums(i), obtainMessage(5, Integer.valueOf(i)));
                log("readAnrFileAndWait anr efid is : " + intValue + " recNum:" + i);
                try {
                    this.mLock.wait();
                } catch (InterruptedException e2) {
                    Rlog.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
                }
            }
            if (hasRecordIn(this.mAnrFileRecord, i)) {
                for (int i2 = 0; i2 < ((ArrayList) this.mAnrFileRecord.get(Integer.valueOf(i))).size(); i2++) {
                    this.mAnrFlagsRecord[i].add(Integer.valueOf(0));
                }
                this.mAnrFlags.put(Integer.valueOf(i), this.mAnrFlagsRecord[i]);
                updatePhoneAdnRecordWithAnr(i);
                return;
            }
            Rlog.e(LOG_TAG, "Error: Anr file is empty");
        }
    }

    private String readAnrRecord(int i, int i2, int i3) {
        if (!hasRecordIn(this.mAnrFileRecord, i2)) {
            return null;
        }
        try {
            byte[] bArr = (byte[]) ((ArrayList) this.mAnrFileRecord.get(Integer.valueOf(i2))).get(i + i3);
            int i4 = bArr[1] & 255;
            return i4 > 11 ? "" : PhoneNumberUtils.calledPartyBCDToString(bArr, 2, i4);
        } catch (IndexOutOfBoundsException e) {
            Rlog.e(LOG_TAG, "Error: Improper ICC card: No anr record for ADN, continuing");
            return null;
        }
    }

    private void readEmailFileAndWait(int i) {
        Map map = (Map) this.mPbrFile.mFileIds.get(Integer.valueOf(i));
        if (map != null && map.containsKey(Integer.valueOf(USIM_EFEMAIL_TAG))) {
            if (this.mEmailPresentInIap) {
                readIapFileAndWait(((Integer) map.get(Integer.valueOf(193))).intValue(), i);
                if (hasRecordIn(this.mIapFileRecord, i)) {
                    this.mFh.loadEFLinearFixedAll(((Integer) map.get(Integer.valueOf(USIM_EFEMAIL_TAG))).intValue(), getPBPath(((Integer) map.get(Integer.valueOf(USIM_EFEMAIL_TAG))).intValue()), obtainMessage(4, Integer.valueOf(i)));
                    log("readEmailFileAndWait email efid is : " + map.get(Integer.valueOf(USIM_EFEMAIL_TAG)));
                    try {
                        this.mLock.wait();
                    } catch (InterruptedException e) {
                        Rlog.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
                    }
                } else {
                    Rlog.e(LOG_TAG, "Error: IAP file is empty");
                    return;
                }
            }
            Iterator it = ((ArrayList) this.mPbrFile.mEmailFileIds.get(Integer.valueOf(i))).iterator();
            while (it.hasNext()) {
                int intValue = ((Integer) it.next()).intValue();
                this.mFh.loadEFLinearFixedPart(intValue, getPBPath(intValue), getValidRecordNums(i), obtainMessage(4, Integer.valueOf(i)));
                log("readEmailFileAndWait email efid is : " + intValue + " recNum:" + i);
                try {
                    this.mLock.wait();
                } catch (InterruptedException e2) {
                    Rlog.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
                }
            }
            if (hasRecordIn(this.mEmailFileRecord, i)) {
                for (int i2 = 0; i2 < ((ArrayList) this.mEmailFileRecord.get(Integer.valueOf(i))).size(); i2++) {
                    this.mEmailFlagsRecord[i].add(Integer.valueOf(0));
                }
                this.mEmailFlags.put(Integer.valueOf(i), this.mEmailFlagsRecord[i]);
                updatePhoneAdnRecordWithEmail(i);
                return;
            }
            Rlog.e(LOG_TAG, "Error: Email file is empty");
        }
    }

    private String readEmailRecord(int i, int i2, int i3) {
        if (!hasRecordIn(this.mEmailFileRecord, i2)) {
            return null;
        }
        try {
            byte[] bArr = (byte[]) ((ArrayList) this.mEmailFileRecord.get(Integer.valueOf(i2))).get(i + i3);
            return this.mEmailPresentInIap ? IccUtils.adnStringFieldToString(bArr, 0, bArr.length - 2) : IccUtils.adnStringFieldToString(bArr, 0, bArr.length);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private void readIapFileAndWait(int i, int i2) {
        log("pbrIndex is " + i2 + ",iap efid is : " + i);
        this.mFh.loadEFLinearFixedPart(i, getPBPath(i), getValidRecordNums(i2), obtainMessage(3, Integer.valueOf(i2)));
        try {
            this.mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readIapFileAndWait");
        }
    }

    private void readPbrFileAndWait() {
        this.mFh.loadEFLinearFixedAll(IccConstants.EF_PBR, obtainMessage(1));
        try {
            this.mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
        }
    }

    private void refreshCache() {
        if (this.mPbrFile != null) {
            this.mPhoneBookRecords.clear();
            int size = this.mPbrFile.mFileIds.size();
            for (int i = 0; i < size; i++) {
                readAdnFileAndWait(i);
            }
        }
    }

    private boolean updateIapFile(int i, String str, String str2, int i2) {
        int i3 = -1;
        int efidByTag = getEfidByTag(getPbrIndexBy(i - 1), 193, 0);
        this.mSuccess = false;
        if (efidByTag == -1) {
            return this.mSuccess;
        }
        int anrRecNumber;
        switch (i2) {
            case 196:
                anrRecNumber = getAnrRecNumber(i - 1, this.mPhoneBookRecords.size(), str);
                break;
            case USIM_EFEMAIL_TAG /*202*/:
                anrRecNumber = getEmailRecNumber(i - 1, this.mPhoneBookRecords.size(), str);
                break;
            default:
                anrRecNumber = -1;
                break;
        }
        if (!TextUtils.isEmpty(str2)) {
            i3 = anrRecNumber;
        }
        log("updateIapFile  efid=" + efidByTag + ", recordNumber= " + i3 + ", adnRecNum=" + i);
        synchronized (this.mLock) {
            this.mFh.getEFLinearRecordSize(efidByTag, getPBPath(efidByTag), obtainMessage(10, i, i3, Integer.valueOf(i2)));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "interrupted while trying to update by search");
            }
        }
        return this.mSuccess;
    }

    private void updatePhoneAdnRecordWithAnr(int i) {
        if (hasRecordIn(this.mAnrFileRecord, i)) {
            int intValue = ((Integer) this.mAdnLengthList.get(i)).intValue();
            if (this.mAnrPresentInIap && hasRecordIn(this.mIapFileRecord, i)) {
                int i2 = 0;
                while (i2 < intValue) {
                    try {
                        byte b = ((byte[]) ((ArrayList) this.mIapFileRecord.get(Integer.valueOf(i))).get(i2))[this.mAnrTagNumberInIap];
                        if (b > (byte) 0) {
                            String[] strArr = new String[]{readAnrRecord(b - 1, i, 0)};
                            int initIndexBy = i2 + getInitIndexBy(i);
                            AdnRecord adnRecord = (AdnRecord) this.mPhoneBookRecords.get(initIndexBy);
                            if (!(adnRecord == null || TextUtils.isEmpty(strArr[0]))) {
                                adnRecord.setAdditionalNumbers(strArr);
                                this.mPhoneBookRecords.set(initIndexBy, adnRecord);
                                ((ArrayList) this.mAnrFlags.get(Integer.valueOf(i))).set(b - 1, Integer.valueOf(1));
                            }
                        }
                        i2++;
                    } catch (IndexOutOfBoundsException e) {
                        Rlog.e(LOG_TAG, "Error: Improper ICC card: No IAP record for ADN, continuing");
                    }
                }
                log("updatePhoneAdnRecordWithAnr: no need to parse type1 ANR file");
            } else if (!this.mAnrPresentInIap) {
                parseType1AnrFile(intValue, i);
            }
        }
    }

    private void updatePhoneAdnRecordWithEmail(int i) {
        if (hasRecordIn(this.mEmailFileRecord, i)) {
            int intValue = ((Integer) this.mAdnLengthList.get(i)).intValue();
            if (this.mEmailPresentInIap && hasRecordIn(this.mIapFileRecord, i)) {
                int i2 = 0;
                while (i2 < intValue) {
                    try {
                        byte b = ((byte[]) ((ArrayList) this.mIapFileRecord.get(Integer.valueOf(i))).get(i2))[this.mEmailTagNumberInIap];
                        if (b > (byte) 0) {
                            String[] strArr = new String[]{readEmailRecord(b - 1, i, 0)};
                            int initIndexBy = i2 + getInitIndexBy(i);
                            AdnRecord adnRecord = (AdnRecord) this.mPhoneBookRecords.get(initIndexBy);
                            if (!(adnRecord == null || TextUtils.isEmpty(strArr[0]))) {
                                adnRecord.setEmails(strArr);
                                this.mPhoneBookRecords.set(initIndexBy, adnRecord);
                                ((ArrayList) this.mEmailFlags.get(Integer.valueOf(i))).set(b - 1, Integer.valueOf(1));
                            }
                        }
                        i2++;
                    } catch (IndexOutOfBoundsException e) {
                        Rlog.e(LOG_TAG, "Error: Improper ICC card: No IAP record for ADN, continuing");
                    }
                }
                log("updatePhoneAdnRecordWithEmail: no need to parse type1 EMAIL file");
                return;
            }
            int intValue2 = ((Integer) this.mAdnLengthList.get(i)).intValue();
            if (!this.mEmailPresentInIap) {
                parseType1EmailFile(intValue2, i);
            }
        }
    }

    public int getAnrCount() {
        int i = 0;
        int i2 = 0;
        while (i2 < this.mAnrFlags.size()) {
            i2++;
            i = ((ArrayList) this.mAnrFlags.get(Integer.valueOf(i2))).size() + i;
        }
        log("getAnrCount count is: " + i);
        return i;
    }

    public int getAnrFilesCountEachAdn() {
        if (this.mPbrFile == null) {
            Rlog.e(LOG_TAG, "mPbrFile is NULL, exiting from getAnrFilesCountEachAdn");
        } else {
            Map map = (Map) this.mPbrFile.mFileIds.get(Integer.valueOf(0));
            if (map != null && map.containsKey(Integer.valueOf(196))) {
                return !this.mAnrPresentInIap ? ((ArrayList) this.mPbrFile.mAnrFileIds.get(Integer.valueOf(0))).size() : 1;
            }
        }
        return 0;
    }

    public int getEmailCount() {
        int i = 0;
        int i2 = 0;
        while (i2 < this.mEmailFlags.size()) {
            i2++;
            i = ((ArrayList) this.mEmailFlags.get(Integer.valueOf(i2))).size() + i;
        }
        log("getEmailCount count is: " + i);
        return i;
    }

    public int getEmailFilesCountEachAdn() {
        if (this.mPbrFile == null) {
            Rlog.e(LOG_TAG, "mPbrFile is NULL, exiting from getEmailFilesCountEachAdn");
        } else {
            Map map = (Map) this.mPbrFile.mFileIds.get(Integer.valueOf(0));
            if (map != null && map.containsKey(Integer.valueOf(USIM_EFEMAIL_TAG))) {
                return !this.mEmailPresentInIap ? ((ArrayList) this.mPbrFile.mEmailFileIds.get(Integer.valueOf(0))).size() : 1;
            }
        }
        return 0;
    }

    public int getEmptyAnrNum_Pbrindex(int i) {
        if (!this.mAnrPresentInIap) {
            return 1;
        }
        if (!this.mAnrFlags.containsKey(Integer.valueOf(i))) {
            return 0;
        }
        int size = ((ArrayList) this.mAnrFlags.get(Integer.valueOf(i))).size();
        int i2 = 0;
        int i3 = 0;
        while (i2 < size) {
            int i4 = ((Integer) ((ArrayList) this.mAnrFlags.get(Integer.valueOf(i))).get(i2)).intValue() == 0 ? i3 + 1 : i3;
            i2++;
            i3 = i4;
        }
        return i3;
    }

    public int getEmptyEmailNum_Pbrindex(int i) {
        if (!this.mEmailPresentInIap) {
            return 1;
        }
        if (!this.mEmailFlags.containsKey(Integer.valueOf(i))) {
            return 0;
        }
        int size = ((ArrayList) this.mEmailFlags.get(Integer.valueOf(i))).size();
        int i2 = 0;
        int i3 = 0;
        while (i2 < size) {
            int i4 = ((Integer) ((ArrayList) this.mEmailFlags.get(Integer.valueOf(i))).get(i2)).intValue() == 0 ? i3 + 1 : i3;
            i2++;
            i3 = i4;
        }
        return i3;
    }

    public String getPBPath(int i) {
        return "3F007F105F3A";
    }

    public int getPbrIndexBy(int i) {
        int size = this.mAdnLengthList.size();
        int i2 = 0;
        int i3 = 0;
        while (i3 < size) {
            int intValue = ((Integer) this.mAdnLengthList.get(i3)).intValue() + i2;
            if (i < intValue) {
                return i3;
            }
            i3++;
            i2 = intValue;
        }
        return -1;
    }

    public int getSpareAnrCount() {
        int size = this.mAnrFlags.size();
        int i = 0;
        for (int i2 = 0; i2 < size; i2++) {
            int i3 = i;
            for (int i4 = 0; i4 < ((ArrayList) this.mAnrFlags.get(Integer.valueOf(i2))).size(); i4++) {
                if (((Integer) ((ArrayList) this.mAnrFlags.get(Integer.valueOf(i2))).get(i4)).intValue() == 0) {
                    i3++;
                }
            }
            i = i3;
        }
        log("getSpareAnrCount count is" + i);
        return i;
    }

    public int getSpareEmailCount() {
        int size = this.mEmailFlags.size();
        int i = 0;
        for (int i2 = 0; i2 < size; i2++) {
            int i3 = i;
            for (int i4 = 0; i4 < ((ArrayList) this.mEmailFlags.get(Integer.valueOf(i2))).size(); i4++) {
                if (((Integer) ((ArrayList) this.mEmailFlags.get(Integer.valueOf(i2))).get(i4)).intValue() == 0) {
                    i3++;
                }
            }
            i = i3;
        }
        log("getSpareEmailCount count is: " + i);
        return i;
    }

    public int getUsimAdnCount() {
        if (this.mPhoneBookRecords == null || this.mPhoneBookRecords.isEmpty()) {
            return 0;
        }
        log("getUsimAdnCount count is" + this.mPhoneBookRecords.size());
        return this.mPhoneBookRecords.size();
    }

    /* JADX WARNING: Removed duplicated region for block: B:184:0x042d A:{SYNTHETIC} */
    /* JADX WARNING: Removed duplicated region for block: B:203:0x04aa A:{SYNTHETIC} */
    public void handleMessage(android.os.Message r14) {
        /*
        r13 = this;
        r12 = 2;
        r9 = -1;
        r5 = 0;
        r11 = 1;
        r6 = 0;
        r0 = r14.what;
        switch(r0) {
            case 1: goto L_0x000b;
            case 2: goto L_0x002c;
            case 3: goto L_0x0071;
            case 4: goto L_0x00a1;
            case 5: goto L_0x011a;
            case 6: goto L_0x0193;
            case 7: goto L_0x02a0;
            case 8: goto L_0x03ad;
            case 9: goto L_0x0451;
            case 10: goto L_0x04ce;
            case 11: goto L_0x05d3;
            default: goto L_0x000a;
        };
    L_0x000a:
        return;
    L_0x000b:
        r0 = "Loading PBR done";
        r13.log(r0);
        r0 = r14.obj;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.exception;
        if (r1 != 0) goto L_0x001f;
    L_0x0018:
        r0 = r0.result;
        r0 = (java.util.ArrayList) r0;
        r13.createPbrFile(r0);
    L_0x001f:
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x0029 }
        r0.notify();	 Catch:{ all -> 0x0029 }
        monitor-exit(r1);	 Catch:{ all -> 0x0029 }
        goto L_0x000a;
    L_0x0029:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0029 }
        throw r0;
    L_0x002c:
        r0 = "Loading USIM ADN records done";
        r13.log(r0);
        r0 = r14.obj;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.userObj;
        r1 = (java.lang.Integer) r1;
        r2 = r1.intValue();
        r1 = r0.exception;
        if (r1 != 0) goto L_0x006b;
    L_0x0041:
        r3 = r13.mPhoneBookRecords;
        r1 = r0.result;
        r1 = (java.util.ArrayList) r1;
        r3.addAll(r1);
        r1 = r13.mAdnLengthList;
        r0 = r0.result;
        r0 = (java.util.ArrayList) r0;
        r0 = r0.size();
        r0 = java.lang.Integer.valueOf(r0);
        r1.add(r2, r0);
        r13.putValidRecNums(r2);
    L_0x005e:
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x0068 }
        r0.notify();	 Catch:{ all -> 0x0068 }
        monitor-exit(r1);	 Catch:{ all -> 0x0068 }
        goto L_0x000a;
    L_0x0068:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0068 }
        throw r0;
    L_0x006b:
        r0 = "can't load USIM ADN records";
        r13.log(r0);
        goto L_0x005e;
    L_0x0071:
        r0 = "Loading USIM IAP records done";
        r13.log(r0);
        r0 = r14.obj;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.userObj;
        r1 = (java.lang.Integer) r1;
        r1 = r1.intValue();
        r2 = r0.exception;
        if (r2 != 0) goto L_0x0093;
    L_0x0086:
        r2 = r13.mIapFileRecord;
        r1 = java.lang.Integer.valueOf(r1);
        r0 = r0.result;
        r0 = (java.util.ArrayList) r0;
        r2.put(r1, r0);
    L_0x0093:
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x009e }
        r0.notify();	 Catch:{ all -> 0x009e }
        monitor-exit(r1);	 Catch:{ all -> 0x009e }
        goto L_0x000a;
    L_0x009e:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x009e }
        throw r0;
    L_0x00a1:
        r0 = "Loading USIM Email records done";
        r13.log(r0);
        r0 = r14.obj;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.userObj;
        r1 = (java.lang.Integer) r1;
        r2 = r1.intValue();
        r1 = r0.exception;
        if (r1 != 0) goto L_0x00fb;
    L_0x00b6:
        r1 = r13.mPbrFile;
        if (r1 == 0) goto L_0x00fb;
    L_0x00ba:
        r1 = r13.mEmailFileRecord;
        r3 = java.lang.Integer.valueOf(r2);
        r1 = r1.get(r3);
        r1 = (java.util.ArrayList) r1;
        if (r1 != 0) goto L_0x0109;
    L_0x00c8:
        r1 = r13.mEmailFileRecord;
        r3 = java.lang.Integer.valueOf(r2);
        r0 = r0.result;
        r0 = (java.util.ArrayList) r0;
        r1.put(r3, r0);
    L_0x00d5:
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "handlemessage EVENT_EMAIL_LOAD_DONE size is: ";
        r1 = r0.append(r1);
        r0 = r13.mEmailFileRecord;
        r2 = java.lang.Integer.valueOf(r2);
        r0 = r0.get(r2);
        r0 = (java.util.ArrayList) r0;
        r0 = r0.size();
        r0 = r1.append(r0);
        r0 = r0.toString();
        r13.log(r0);
    L_0x00fb:
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x0106 }
        r0.notify();	 Catch:{ all -> 0x0106 }
        monitor-exit(r1);	 Catch:{ all -> 0x0106 }
        goto L_0x000a;
    L_0x0106:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0106 }
        throw r0;
    L_0x0109:
        r0 = r0.result;
        r0 = (java.util.ArrayList) r0;
        r1.addAll(r0);
        r0 = r13.mEmailFileRecord;
        r3 = java.lang.Integer.valueOf(r2);
        r0.put(r3, r1);
        goto L_0x00d5;
    L_0x011a:
        r0 = "Loading USIM Anr records done";
        r13.log(r0);
        r0 = r14.obj;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.userObj;
        r1 = (java.lang.Integer) r1;
        r2 = r1.intValue();
        r1 = r0.exception;
        if (r1 != 0) goto L_0x0174;
    L_0x012f:
        r1 = r13.mPbrFile;
        if (r1 == 0) goto L_0x0174;
    L_0x0133:
        r1 = r13.mAnrFileRecord;
        r3 = java.lang.Integer.valueOf(r2);
        r1 = r1.get(r3);
        r1 = (java.util.ArrayList) r1;
        if (r1 != 0) goto L_0x0182;
    L_0x0141:
        r1 = r13.mAnrFileRecord;
        r3 = java.lang.Integer.valueOf(r2);
        r0 = r0.result;
        r0 = (java.util.ArrayList) r0;
        r1.put(r3, r0);
    L_0x014e:
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "handlemessage EVENT_ANR_LOAD_DONE size is: ";
        r1 = r0.append(r1);
        r0 = r13.mAnrFileRecord;
        r2 = java.lang.Integer.valueOf(r2);
        r0 = r0.get(r2);
        r0 = (java.util.ArrayList) r0;
        r0 = r0.size();
        r0 = r1.append(r0);
        r0 = r0.toString();
        r13.log(r0);
    L_0x0174:
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x017f }
        r0.notify();	 Catch:{ all -> 0x017f }
        monitor-exit(r1);	 Catch:{ all -> 0x017f }
        goto L_0x000a;
    L_0x017f:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x017f }
        throw r0;
    L_0x0182:
        r0 = r0.result;
        r0 = (java.util.ArrayList) r0;
        r1.addAll(r0);
        r0 = r13.mAnrFileRecord;
        r3 = java.lang.Integer.valueOf(r2);
        r0.put(r3, r1);
        goto L_0x014e;
    L_0x0193:
        r0 = "Loading EF_EMAIL_RECORD_SIZE_DONE";
        r13.log(r0);
        r0 = r14.obj;
        r0 = (android.os.AsyncResult) r0;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.userObj;
        r1 = (java.lang.String) r1;
        r2 = r1;
        r2 = (java.lang.String) r2;
        r1 = r14.arg1;
        r7 = r1 + -1;
        r1 = r14.arg2;
        r3 = ",";
        r3 = r2.split(r3);
        r2 = r3.length;
        if (r2 != r11) goto L_0x01cd;
    L_0x01b4:
        r2 = r3[r6];
        r3 = "";
        r4 = r3;
    L_0x01b9:
        r3 = r0.exception;
        if (r3 == 0) goto L_0x01d6;
    L_0x01bd:
        r13.mSuccess = r6;
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x01ca }
        r0.notify();	 Catch:{ all -> 0x01ca }
        monitor-exit(r1);	 Catch:{ all -> 0x01ca }
        goto L_0x000a;
    L_0x01ca:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x01ca }
        throw r0;
    L_0x01cd:
        r2 = r3.length;
        if (r2 <= r11) goto L_0x065b;
    L_0x01d0:
        r2 = r3[r6];
        r3 = r3[r11];
        r4 = r3;
        goto L_0x01b9;
    L_0x01d6:
        r0 = r0.result;
        r0 = (int[]) r0;
        r0 = (int[]) r0;
        r3 = r13.mPhoneBookRecords;
        r3 = r3.size();
        r3 = r13.getEmailRecNumber(r7, r3, r2);
        r2 = r0.length;
        r8 = 3;
        if (r2 != r8) goto L_0x01f0;
    L_0x01ea:
        r2 = r0[r12];
        if (r3 > r2) goto L_0x01f0;
    L_0x01ee:
        if (r3 > 0) goto L_0x0200;
    L_0x01f0:
        r13.mSuccess = r6;
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x01fd }
        r0.notify();	 Catch:{ all -> 0x01fd }
        monitor-exit(r1);	 Catch:{ all -> 0x01fd }
        goto L_0x000a;
    L_0x01fd:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x01fd }
        throw r0;
    L_0x0200:
        r0 = r0[r6];
        r4 = r13.buildEmailData(r0, r7, r4);
        if (r4 != 0) goto L_0x0218;
    L_0x0208:
        r13.mSuccess = r6;
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x0215 }
        r0.notify();	 Catch:{ all -> 0x0215 }
        monitor-exit(r1);	 Catch:{ all -> 0x0215 }
        goto L_0x000a;
    L_0x0215:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0215 }
        throw r0;
    L_0x0218:
        r0 = r13.mEmailPresentInIap;
        if (r0 != 0) goto L_0x0658;
    L_0x021c:
        r0 = r13.mPbrFile;
        r0 = r0.mEmailFileIds;
        r2 = r13.getPbrIndexBy(r7);
        r2 = java.lang.Integer.valueOf(r2);
        r0 = r0.get(r2);
        r0 = (java.util.ArrayList) r0;
        r2 = java.lang.Integer.valueOf(r1);
        r2 = r0.indexOf(r2);
        if (r2 != r9) goto L_0x0250;
    L_0x0238:
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r2 = "wrong efid index:";
        r0 = r0.append(r2);
        r0 = r0.append(r1);
        r0 = r0.toString();
        r13.log(r0);
        goto L_0x000a;
    L_0x0250:
        r0 = r13.mAdnLengthList;
        r6 = r13.getPbrIndexBy(r7);
        r0 = r0.get(r6);
        r0 = (java.lang.Integer) r0;
        r0 = r0.intValue();
        r0 = r0 * r2;
        r0 = r0 + r3;
        r6 = new java.lang.StringBuilder;
        r6.<init>();
        r8 = "EMAIL index:";
        r6 = r6.append(r8);
        r2 = r6.append(r2);
        r6 = " efid:";
        r2 = r2.append(r6);
        r2 = r2.append(r1);
        r6 = " actual RecNumber:";
        r2 = r2.append(r6);
        r2 = r2.append(r0);
        r2 = r2.toString();
        r13.log(r2);
        r6 = r0;
    L_0x028d:
        r0 = r13.mFh;
        r2 = r13.getPBPath(r1);
        r8 = 8;
        r6 = r13.obtainMessage(r8, r6, r7, r4);
        r0.updateEFLinearFixed(r1, r2, r3, r4, r5, r6);
        r13.mPendingExtLoads = r11;
        goto L_0x000a;
    L_0x02a0:
        r0 = "Loading EF_ANR_RECORD_SIZE_DONE";
        r13.log(r0);
        r0 = r14.obj;
        r0 = (android.os.AsyncResult) r0;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.userObj;
        r1 = (java.lang.String) r1;
        r2 = r1;
        r2 = (java.lang.String) r2;
        r1 = r14.arg1;
        r7 = r1 + -1;
        r1 = r14.arg2;
        r3 = ",";
        r3 = r2.split(r3);
        r2 = r3.length;
        if (r2 != r11) goto L_0x02da;
    L_0x02c1:
        r2 = r3[r6];
        r3 = "";
        r4 = r3;
    L_0x02c6:
        r3 = r0.exception;
        if (r3 == 0) goto L_0x02e3;
    L_0x02ca:
        r13.mSuccess = r6;
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x02d7 }
        r0.notify();	 Catch:{ all -> 0x02d7 }
        monitor-exit(r1);	 Catch:{ all -> 0x02d7 }
        goto L_0x000a;
    L_0x02d7:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x02d7 }
        throw r0;
    L_0x02da:
        r2 = r3.length;
        if (r2 <= r11) goto L_0x0654;
    L_0x02dd:
        r2 = r3[r6];
        r3 = r3[r11];
        r4 = r3;
        goto L_0x02c6;
    L_0x02e3:
        r0 = r0.result;
        r0 = (int[]) r0;
        r0 = (int[]) r0;
        r3 = r13.mPhoneBookRecords;
        r3 = r3.size();
        r3 = r13.getAnrRecNumber(r7, r3, r2);
        r2 = r0.length;
        r8 = 3;
        if (r2 != r8) goto L_0x02fd;
    L_0x02f7:
        r2 = r0[r12];
        if (r3 > r2) goto L_0x02fd;
    L_0x02fb:
        if (r3 > 0) goto L_0x030d;
    L_0x02fd:
        r13.mSuccess = r6;
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x030a }
        r0.notify();	 Catch:{ all -> 0x030a }
        monitor-exit(r1);	 Catch:{ all -> 0x030a }
        goto L_0x000a;
    L_0x030a:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x030a }
        throw r0;
    L_0x030d:
        r0 = r0[r6];
        r4 = r13.buildAnrData(r0, r7, r4);
        if (r4 != 0) goto L_0x0325;
    L_0x0315:
        r13.mSuccess = r6;
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x0322 }
        r0.notify();	 Catch:{ all -> 0x0322 }
        monitor-exit(r1);	 Catch:{ all -> 0x0322 }
        goto L_0x000a;
    L_0x0322:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0322 }
        throw r0;
    L_0x0325:
        r0 = r13.mAnrPresentInIap;
        if (r0 != 0) goto L_0x0651;
    L_0x0329:
        r0 = r13.mPbrFile;
        r0 = r0.mAnrFileIds;
        r2 = r13.getPbrIndexBy(r7);
        r2 = java.lang.Integer.valueOf(r2);
        r0 = r0.get(r2);
        r0 = (java.util.ArrayList) r0;
        r2 = java.lang.Integer.valueOf(r1);
        r2 = r0.indexOf(r2);
        if (r2 != r9) goto L_0x035d;
    L_0x0345:
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r2 = "wrong efid index:";
        r0 = r0.append(r2);
        r0 = r0.append(r1);
        r0 = r0.toString();
        r13.log(r0);
        goto L_0x000a;
    L_0x035d:
        r0 = r13.mAdnLengthList;
        r6 = r13.getPbrIndexBy(r7);
        r0 = r0.get(r6);
        r0 = (java.lang.Integer) r0;
        r0 = r0.intValue();
        r0 = r0 * r2;
        r0 = r0 + r3;
        r6 = new java.lang.StringBuilder;
        r6.<init>();
        r8 = "ANR index:";
        r6 = r6.append(r8);
        r2 = r6.append(r2);
        r6 = " efid:";
        r2 = r2.append(r6);
        r2 = r2.append(r1);
        r6 = " actual RecNumber:";
        r2 = r2.append(r6);
        r2 = r2.append(r0);
        r2 = r2.toString();
        r13.log(r2);
        r6 = r0;
    L_0x039a:
        r0 = r13.mFh;
        r2 = r13.getPBPath(r1);
        r8 = 9;
        r6 = r13.obtainMessage(r8, r6, r7, r4);
        r0.updateEFLinearFixed(r1, r2, r3, r4, r5, r6);
        r13.mPendingExtLoads = r11;
        goto L_0x000a;
    L_0x03ad:
        r0 = "Loading UPDATE_EMAIL_RECORD_DONE";
        r13.log(r0);
        r0 = r14.obj;
        r0 = (android.os.AsyncResult) r0;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.exception;
        if (r1 == 0) goto L_0x03be;
    L_0x03bc:
        r13.mSuccess = r6;
    L_0x03be:
        r0 = r0.userObj;
        r0 = (byte[]) r0;
        r0 = (byte[]) r0;
        r3 = r14.arg1;
        r1 = r14.arg2;
        r4 = r13.getPbrIndexBy(r1);
        r1 = "EVENT_UPDATE_EMAIL_RECORD_DONE";
        r13.log(r1);
        r13.mPendingExtLoads = r6;
        r13.mSuccess = r11;
        r1 = r13.mEmailFileRecord;
        r2 = java.lang.Integer.valueOf(r4);
        r1 = r1.get(r2);
        r1 = (java.util.ArrayList) r1;
        r2 = r3 + -1;
        r1.set(r2, r0);
        r2 = r6;
    L_0x03e7:
        r1 = r0.length;
        if (r2 >= r1) goto L_0x042a;
    L_0x03ea:
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r5 = "EVENT_UPDATE_EMAIL_RECORD_DONE data = ";
        r1 = r1.append(r5);
        r5 = r0[r2];
        r1 = r1.append(r5);
        r5 = ",i is ";
        r1 = r1.append(r5);
        r1 = r1.append(r2);
        r1 = r1.toString();
        r13.log(r1);
        r1 = r0[r2];
        if (r1 == r9) goto L_0x0438;
    L_0x0410:
        r0 = "EVENT_UPDATE_EMAIL_RECORD_DONE data !=0xff";
        r13.log(r0);
        r0 = r13.mEmailFlags;
        r1 = java.lang.Integer.valueOf(r4);
        r0 = r0.get(r1);
        r0 = (java.util.ArrayList) r0;
        r1 = r3 + -1;
        r2 = java.lang.Integer.valueOf(r11);
        r0.set(r1, r2);
    L_0x042a:
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x0435 }
        r0.notify();	 Catch:{ all -> 0x0435 }
        monitor-exit(r1);	 Catch:{ all -> 0x0435 }
        goto L_0x000a;
    L_0x0435:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0435 }
        throw r0;
    L_0x0438:
        r1 = r13.mEmailFlags;
        r5 = java.lang.Integer.valueOf(r4);
        r1 = r1.get(r5);
        r1 = (java.util.ArrayList) r1;
        r5 = r3 + -1;
        r7 = java.lang.Integer.valueOf(r6);
        r1.set(r5, r7);
        r1 = r2 + 1;
        r2 = r1;
        goto L_0x03e7;
    L_0x0451:
        r0 = "Loading UPDATE_ANR_RECORD_DONE";
        r13.log(r0);
        r0 = r14.obj;
        r0 = (android.os.AsyncResult) r0;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.userObj;
        r1 = (byte[]) r1;
        r1 = (byte[]) r1;
        r3 = r14.arg1;
        r2 = r14.arg2;
        r4 = r13.getPbrIndexBy(r2);
        r0 = r0.exception;
        if (r0 == 0) goto L_0x0470;
    L_0x046e:
        r13.mSuccess = r6;
    L_0x0470:
        r0 = "EVENT_UPDATE_ANR_RECORD_DONE";
        r13.log(r0);
        r13.mPendingExtLoads = r6;
        r13.mSuccess = r11;
        r0 = r13.mAnrFileRecord;
        r2 = java.lang.Integer.valueOf(r4);
        r0 = r0.get(r2);
        r0 = (java.util.ArrayList) r0;
        r2 = r3 + -1;
        r0.set(r2, r1);
        r2 = r6;
    L_0x048b:
        r0 = r1.length;
        if (r2 >= r0) goto L_0x04a7;
    L_0x048e:
        r0 = r1[r2];
        if (r0 == r9) goto L_0x04b5;
    L_0x0492:
        r0 = r13.mAnrFlags;
        r1 = java.lang.Integer.valueOf(r4);
        r0 = r0.get(r1);
        r0 = (java.util.ArrayList) r0;
        r1 = r3 + -1;
        r2 = java.lang.Integer.valueOf(r11);
        r0.set(r1, r2);
    L_0x04a7:
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x04b2 }
        r0.notify();	 Catch:{ all -> 0x04b2 }
        monitor-exit(r1);	 Catch:{ all -> 0x04b2 }
        goto L_0x000a;
    L_0x04b2:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x04b2 }
        throw r0;
    L_0x04b5:
        r0 = r13.mAnrFlags;
        r5 = java.lang.Integer.valueOf(r4);
        r0 = r0.get(r5);
        r0 = (java.util.ArrayList) r0;
        r5 = r3 + -1;
        r7 = java.lang.Integer.valueOf(r6);
        r0.set(r5, r7);
        r0 = r2 + 1;
        r2 = r0;
        goto L_0x048b;
    L_0x04ce:
        r0 = "EVENT_EF_IAP_RECORD_SIZE_DONE";
        r13.log(r0);
        r0 = r14.obj;
        r0 = (android.os.AsyncResult) r0;
        r0 = (android.os.AsyncResult) r0;
        r7 = r14.arg2;
        r1 = r14.arg1;
        r8 = r1 + -1;
        r1 = r13.getPbrIndexBy(r8);
        r2 = 193; // 0xc1 float:2.7E-43 double:9.54E-322;
        r13.getEfidByTag(r1, r2, r6);
        r1 = r0.userObj;
        r1 = (java.lang.Integer) r1;
        r2 = r1.intValue();
        r1 = r0.exception;
        if (r1 == 0) goto L_0x0504;
    L_0x04f4:
        r13.mSuccess = r6;
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x0501 }
        r0.notify();	 Catch:{ all -> 0x0501 }
        monitor-exit(r1);	 Catch:{ all -> 0x0501 }
        goto L_0x000a;
    L_0x0501:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0501 }
        throw r0;
    L_0x0504:
        r3 = r13.getPbrIndexBy(r8);
        r1 = 193; // 0xc1 float:2.7E-43 double:9.54E-322;
        r1 = r13.getEfidByTag(r3, r1, r6);
        r0 = r0.result;
        r0 = (int[]) r0;
        r0 = (int[]) r0;
        r4 = r13.getInitIndexBy(r3);
        r9 = r8 - r4;
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r10 = "handleIAP_RECORD_SIZE_DONE adnRecIndex is: ";
        r4 = r4.append(r10);
        r4 = r4.append(r8);
        r10 = ", recordNumber is: ";
        r4 = r4.append(r10);
        r4 = r4.append(r7);
        r10 = ", recordIndex is: ";
        r4 = r4.append(r10);
        r4 = r4.append(r9);
        r4 = r4.toString();
        r13.log(r4);
        r4 = r0.length;
        r10 = 3;
        if (r4 != r10) goto L_0x0550;
    L_0x0548:
        r4 = r9 + 1;
        r0 = r0[r12];
        if (r4 > r0) goto L_0x0550;
    L_0x054e:
        if (r7 != 0) goto L_0x0560;
    L_0x0550:
        r13.mSuccess = r6;
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x055d }
        r0.notify();	 Catch:{ all -> 0x055d }
        monitor-exit(r1);	 Catch:{ all -> 0x055d }
        goto L_0x000a;
    L_0x055d:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x055d }
        throw r0;
    L_0x0560:
        r0 = r13.mIapFileRecord;
        r0 = r13.hasRecordIn(r0, r3);
        if (r0 == 0) goto L_0x000a;
    L_0x0568:
        r0 = r13.mIapFileRecord;
        r3 = java.lang.Integer.valueOf(r3);
        r0 = r0.get(r3);
        r0 = (java.util.ArrayList) r0;
        r0 = r0.get(r9);
        r0 = (byte[]) r0;
        r3 = r0.length;
        r4 = new byte[r3];
        r3 = r4.length;
        java.lang.System.arraycopy(r0, r6, r4, r6, r3);
        switch(r2) {
            case 196: goto L_0x05cd;
            case 202: goto L_0x05c7;
            default: goto L_0x0584;
        };
    L_0x0584:
        r13.mPendingExtLoads = r11;
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r2 = " IAP  efid= ";
        r0 = r0.append(r2);
        r0 = r0.append(r1);
        r2 = ", update IAP index= ";
        r0 = r0.append(r2);
        r0 = r0.append(r9);
        r2 = " with value= ";
        r0 = r0.append(r2);
        r2 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r4);
        r0 = r0.append(r2);
        r0 = r0.toString();
        r13.log(r0);
        r0 = r13.mFh;
        r2 = r13.getPBPath(r1);
        r3 = r9 + 1;
        r6 = 11;
        r6 = r13.obtainMessage(r6, r8, r7, r4);
        r0.updateEFLinearFixed(r1, r2, r3, r4, r5, r6);
        goto L_0x000a;
    L_0x05c7:
        r0 = r13.mEmailTagNumberInIap;
        r2 = (byte) r7;
        r4[r0] = r2;
        goto L_0x0584;
    L_0x05cd:
        r0 = r13.mAnrTagNumberInIap;
        r2 = (byte) r7;
        r4[r0] = r2;
        goto L_0x0584;
    L_0x05d3:
        r0 = "EVENT_UPDATE_IAP_RECORD_DONE";
        r13.log(r0);
        r0 = r14.obj;
        r0 = (android.os.AsyncResult) r0;
        r0 = (android.os.AsyncResult) r0;
        r1 = r0.exception;
        if (r1 == 0) goto L_0x05e4;
    L_0x05e2:
        r13.mSuccess = r6;
    L_0x05e4:
        r0 = r0.userObj;
        r0 = (byte[]) r0;
        r0 = (byte[]) r0;
        r1 = r14.arg1;
        r2 = r13.getPbrIndexBy(r1);
        r3 = r13.getInitIndexBy(r2);
        r3 = r1 - r3;
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "handleMessage EVENT_UPDATE_IAP_RECORD_DONE recordIndex is: ";
        r4 = r4.append(r5);
        r4 = r4.append(r3);
        r5 = ", adnRecIndex is: ";
        r4 = r4.append(r5);
        r1 = r4.append(r1);
        r1 = r1.toString();
        r13.log(r1);
        r13.mPendingExtLoads = r6;
        r13.mSuccess = r11;
        r1 = r13.mIapFileRecord;
        r2 = java.lang.Integer.valueOf(r2);
        r1 = r1.get(r2);
        r1 = (java.util.ArrayList) r1;
        r1.set(r3, r0);
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "the iap email recordNumber is :";
        r1 = r1.append(r2);
        r2 = r13.mEmailTagNumberInIap;
        r0 = r0[r2];
        r0 = r1.append(r0);
        r0 = r0.toString();
        r13.log(r0);
        r1 = r13.mLock;
        monitor-enter(r1);
        r0 = r13.mLock;	 Catch:{ all -> 0x064e }
        r0.notify();	 Catch:{ all -> 0x064e }
        monitor-exit(r1);	 Catch:{ all -> 0x064e }
        goto L_0x000a;
    L_0x064e:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x064e }
        throw r0;
    L_0x0651:
        r6 = r3;
        goto L_0x039a;
    L_0x0654:
        r2 = r5;
        r4 = r5;
        goto L_0x02c6;
    L_0x0658:
        r6 = r3;
        goto L_0x028d;
    L_0x065b:
        r2 = r5;
        r4 = r5;
        goto L_0x01b9;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.gsm.UsimPhoneBookManager.handleMessage(android.os.Message):void");
    }

    public void invalidateCache() {
        this.mRefreshCache = true;
    }

    public ArrayList<AdnRecord> loadEfFilesFromUsim() {
        synchronized (this.mLock) {
            if (!this.mPhoneBookRecords.isEmpty()) {
                if (this.mRefreshCache) {
                    this.mRefreshCache = false;
                    refreshCache();
                }
                ArrayList arrayList = this.mPhoneBookRecords;
                return arrayList;
            } else if (this.mIsPbrPresent.booleanValue()) {
                if (this.mPbrFile == null) {
                    readPbrFileAndWait();
                }
                if (this.mPbrFile == null) {
                    return null;
                }
                int i;
                int size = this.mPbrFile.mFileIds.size();
                if (this.mAnrFlagsRecord == null && this.mEmailFlagsRecord == null) {
                    this.mAnrFlagsRecord = new ArrayList[size];
                    this.mEmailFlagsRecord = new ArrayList[size];
                    for (i = 0; i < size; i++) {
                        this.mAnrFlagsRecord[i] = new ArrayList();
                        this.mEmailFlagsRecord[i] = new ArrayList();
                    }
                }
                for (i = 0; i < size; i++) {
                    readAdnFileAndWait(i);
                    readEmailFileAndWait(i);
                    readAnrFileAndWait(i);
                }
                return this.mPhoneBookRecords;
            } else {
                return null;
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void parseType1AnrFile(int i, int i2) {
        int size = ((ArrayList) this.mPbrFile.mAnrFileIds.get(Integer.valueOf(i2))).size();
        ArrayList arrayList = new ArrayList();
        int initIndexBy = getInitIndexBy(i2);
        if (hasRecordIn(this.mAnrFileRecord, i2)) {
            log("parseType1AnrFile: pbrIndex is: " + i2 + ", numRecs is: " + i + ", numAnrFiles " + size);
            for (int i3 = 0; i3 < i; i3++) {
                arrayList.clear();
                int i4 = 0;
                for (int i5 = 0; i5 < size; i5++) {
                    String readAnrRecord = readAnrRecord(i3, i2, i5 * i);
                    arrayList.add(readAnrRecord);
                    if (!TextUtils.isEmpty(readAnrRecord)) {
                        int i6 = i4 + 1;
                        ((ArrayList) this.mAnrFlags.get(Integer.valueOf(i2))).set((i5 * i) + i3, Integer.valueOf(1));
                        i4 = i6;
                    }
                }
                if (i4 != 0) {
                    AdnRecord adnRecord = (AdnRecord) this.mPhoneBookRecords.get(i3 + initIndexBy);
                    if (adnRecord != null) {
                        String[] strArr = new String[arrayList.size()];
                        System.arraycopy(arrayList.toArray(), 0, strArr, 0, arrayList.size());
                        adnRecord.setAdditionalNumbers(strArr);
                        this.mPhoneBookRecords.set(i3 + initIndexBy, adnRecord);
                    }
                }
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void parseType1EmailFile(int i, int i2) {
        int size = ((ArrayList) this.mPbrFile.mEmailFileIds.get(Integer.valueOf(i2))).size();
        ArrayList arrayList = new ArrayList();
        int initIndexBy = getInitIndexBy(i2);
        if (hasRecordIn(this.mEmailFileRecord, i2)) {
            log("parseType1EmailFile: pbrIndex is: " + i2 + ", numRecs is: " + i);
            for (int i3 = 0; i3 < i; i3++) {
                arrayList.clear();
                int i4 = 0;
                for (int i5 = 0; i5 < size; i5++) {
                    String readEmailRecord = readEmailRecord(i3, i2, i5 * i);
                    arrayList.add(readEmailRecord);
                    if (!TextUtils.isEmpty(readEmailRecord)) {
                        int i6 = i4 + 1;
                        ((ArrayList) this.mEmailFlags.get(Integer.valueOf(i2))).set((i5 * i) + i3, Integer.valueOf(1));
                        i4 = i6;
                    }
                }
                if (i4 != 0) {
                    AdnRecord adnRecord = (AdnRecord) this.mPhoneBookRecords.get(i3 + initIndexBy);
                    if (adnRecord != null) {
                        String[] strArr = new String[arrayList.size()];
                        System.arraycopy(arrayList.toArray(), 0, strArr, 0, arrayList.size());
                        adnRecord.setEmails(strArr);
                        this.mPhoneBookRecords.set(i3 + initIndexBy, adnRecord);
                    }
                }
            }
        }
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
        this.mIsPbrPresent = Boolean.valueOf(true);
        this.mRefreshCache = false;
    }

    public boolean updateAnrFile(int i, String str, String str2, int i2) {
        int pbrIndexBy = getPbrIndexBy(i - 1);
        int efidByTag = getEfidByTag(pbrIndexBy, 196, i2);
        if (str == null) {
            str = "";
        }
        if (str2 == null) {
            str2 = "";
        }
        String str3 = str + "," + str2;
        this.mSuccess = false;
        log("updateAnrFile oldAnr : " + str + ", newAnr:" + str2 + " anrs:" + str3 + ", efid" + efidByTag + ", adnRecNum: " + i);
        if (efidByTag == -1) {
            return this.mSuccess;
        }
        if (!this.mAnrPresentInIap || !TextUtils.isEmpty(str) || TextUtils.isEmpty(str2)) {
            this.mSuccess = true;
        } else if (getEmptyAnrNum_Pbrindex(pbrIndexBy) == 0) {
            log("updateAnrFile getEmptyAnrNum_Pbrindex=0, pbrIndex is " + pbrIndexBy);
            this.mSuccess = true;
            return this.mSuccess;
        } else {
            this.mSuccess = updateIapFile(i, str, str2, 196);
        }
        synchronized (this.mLock) {
            this.mFh.getEFLinearRecordSize(efidByTag, getPBPath(efidByTag), obtainMessage(7, i, efidByTag, str3));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "interrupted while trying to update by search");
            }
        }
        if (this.mAnrPresentInIap && this.mSuccess && !TextUtils.isEmpty(str) && TextUtils.isEmpty(str2)) {
            this.mSuccess = updateIapFile(i, str, str2, 196);
        }
        return this.mSuccess;
    }

    public boolean updateEmailFile(int i, String str, String str2, int i2) {
        int pbrIndexBy = getPbrIndexBy(i - 1);
        int efidByTag = getEfidByTag(pbrIndexBy, USIM_EFEMAIL_TAG, i2);
        if (str == null) {
            str = "";
        }
        if (str2 == null) {
            str2 = "";
        }
        String str3 = str + "," + str2;
        this.mSuccess = false;
        log("updateEmailFile oldEmail : " + str + " newEmail:" + str2 + " emails:" + str3 + " efid" + efidByTag + " adnRecNum: " + i);
        if (efidByTag == -1) {
            return this.mSuccess;
        }
        if (!this.mEmailPresentInIap || !TextUtils.isEmpty(str) || TextUtils.isEmpty(str2)) {
            this.mSuccess = true;
        } else if (getEmptyEmailNum_Pbrindex(pbrIndexBy) == 0) {
            log("updateEmailFile getEmptyEmailNum_Pbrindex=0, pbrIndex is " + pbrIndexBy);
            this.mSuccess = true;
            return this.mSuccess;
        } else {
            this.mSuccess = updateIapFile(i, str, str2, USIM_EFEMAIL_TAG);
        }
        if (this.mSuccess) {
            synchronized (this.mLock) {
                this.mFh.getEFLinearRecordSize(efidByTag, getPBPath(efidByTag), obtainMessage(6, i, efidByTag, str3));
                try {
                    this.mLock.wait();
                } catch (InterruptedException e) {
                    Rlog.e(LOG_TAG, "interrupted while trying to update by search");
                }
            }
        }
        if (this.mEmailPresentInIap && this.mSuccess && !TextUtils.isEmpty(str) && TextUtils.isEmpty(str2)) {
            this.mSuccess = updateIapFile(i, str, str2, USIM_EFEMAIL_TAG);
        }
        return this.mSuccess;
    }
}
