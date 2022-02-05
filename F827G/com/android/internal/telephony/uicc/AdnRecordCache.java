package com.android.internal.telephony.uicc;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.gsm.UsimPhoneBookManager;
import java.util.ArrayList;
import java.util.Iterator;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class AdnRecordCache extends Handler implements IccConstants {
    static final int EVENT_LOAD_ALL_ADN_LIKE_DONE = 1;
    static final int EVENT_UPDATE_ADN_DONE = 2;
    private static final int USIM_EFANR_TAG = 196;
    private static final int USIM_EFEMAIL_TAG = 202;
    SparseArray<ArrayList<AdnRecord>> mAdnLikeFiles;
    SparseArray<ArrayList<Message>> mAdnLikeWaiters;
    private int mAdncountofIcc;
    private IccFileHandler mFh;
    private UiccCardApplication mUiccApplication;
    SparseArray<Message> mUserWriteResponse;
    private UsimPhoneBookManager mUsimPhoneBookManager;

    public AdnRecordCache(IccFileHandler fh) {
        this.mUiccApplication = null;
        this.mAdncountofIcc = 0;
        this.mAdnLikeFiles = new SparseArray<>();
        this.mAdnLikeWaiters = new SparseArray<>();
        this.mUserWriteResponse = new SparseArray<>();
        this.mFh = fh;
        this.mUsimPhoneBookManager = new UsimPhoneBookManager(this.mFh, this);
    }

    public AdnRecordCache(IccFileHandler fh, UiccCardApplication app) {
        this(fh);
        this.mUiccApplication = app;
    }

    public void reset() {
        this.mAdnLikeFiles.clear();
        this.mUsimPhoneBookManager.reset();
        clearWaiters();
        clearUserWriters();
    }

    private void clearWaiters() {
        int size = this.mAdnLikeWaiters.size();
        for (int i = 0; i < size; i++) {
            notifyWaiters(this.mAdnLikeWaiters.valueAt(i), new AsyncResult((Object) null, (Object) null, new RuntimeException("AdnCache reset")));
        }
        this.mAdnLikeWaiters.clear();
    }

    private void clearUserWriters() {
        int size = this.mUserWriteResponse.size();
        for (int i = 0; i < size; i++) {
            sendErrorResponse(this.mUserWriteResponse.valueAt(i), "AdnCace reset");
        }
        this.mUserWriteResponse.clear();
    }

    public ArrayList<AdnRecord> getRecordsIfLoaded(int efid) {
        return this.mAdnLikeFiles.get(efid);
    }

    public int extensionEfForEf(int efid) {
        switch (efid) {
            case IccConstants.EF_PBR /* 20272 */:
                return 0;
            case 28474:
            case IccConstants.EF_MSISDN /* 28480 */:
                return IccConstants.EF_EXT1;
            case IccConstants.EF_FDN /* 28475 */:
                return IccConstants.EF_EXT2;
            case IccConstants.EF_SDN /* 28489 */:
                return IccConstants.EF_EXT3;
            case IccConstants.EF_MBDN /* 28615 */:
                return IccConstants.EF_EXT6;
            default:
                return -1;
        }
    }

    private void sendErrorResponse(Message response, String errString) {
        if (response != null) {
            AsyncResult.forMessage(response).exception = new RuntimeException(errString);
            response.sendToTarget();
        }
    }

    public void updateAdnByIndex(int efid, AdnRecord adn, int recordIndex, String pin2, Message response) {
        int extensionEF = extensionEfForEf(efid);
        if (extensionEF < 0) {
            sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
        } else if (this.mUserWriteResponse.get(efid) != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
        } else {
            this.mUserWriteResponse.put(efid, response);
            if (TelBrand.IS_SBM) {
                new AdnRecordLoader(this.mFh, this.mUiccApplication).updateEF(adn, efid, extensionEF, recordIndex, pin2, obtainMessage(2, efid, recordIndex, adn));
            } else {
                new AdnRecordLoader(this.mFh).updateEF(adn, efid, extensionEF, recordIndex, pin2, obtainMessage(2, efid, recordIndex, adn));
            }
        }
    }

    public void updateAdnBySearch(int efid, AdnRecord oldAdn, AdnRecord newAdn, String pin2, Message response) {
        ArrayList<AdnRecord> oldAdnList;
        int extensionEF = extensionEfForEf(efid);
        if (extensionEF < 0) {
            sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
            return;
        }
        try {
            if (efid == 20272) {
                oldAdnList = this.mUsimPhoneBookManager.loadEfFilesFromUsim();
            } else {
                oldAdnList = getRecordsIfLoaded(efid);
            }
        } catch (NullPointerException e) {
            oldAdnList = null;
        }
        if (oldAdnList == null) {
            sendErrorResponse(response, "Adn list not exist for EF:" + efid);
            return;
        }
        int index = -1;
        int count = 1;
        int prePbrIndex = -2;
        int anrNum = 0;
        int emailNum = 0;
        Iterator<AdnRecord> it = oldAdnList.iterator();
        while (true) {
            if (!it.hasNext()) {
                break;
            }
            AdnRecord nextAdnRecord = it.next();
            boolean isEmailOrAnrIsFull = false;
            if (efid == 20272) {
                int pbrIndex = this.mUsimPhoneBookManager.getPbrIndexBy(count - 1);
                if (pbrIndex != prePbrIndex) {
                    anrNum = this.mUsimPhoneBookManager.getEmptyAnrNum_Pbrindex(pbrIndex);
                    emailNum = this.mUsimPhoneBookManager.getEmptyEmailNum_Pbrindex(pbrIndex);
                    prePbrIndex = pbrIndex;
                    Log.d("AdnRecordCache", "updateAdnBySearch, pbrIndex: " + pbrIndex + " anrNum:" + anrNum + " emailNum:" + emailNum);
                }
                if ((anrNum == 0 && oldAdn.getAdditionalNumbers() == null && newAdn.getAdditionalNumbers() != null) || (emailNum == 0 && oldAdn.getEmails() == null && newAdn.getEmails() != null)) {
                    isEmailOrAnrIsFull = true;
                }
            }
            if (!isEmailOrAnrIsFull && oldAdn.isEqual(nextAdnRecord)) {
                index = count;
                break;
            }
            count++;
        }
        Log.d("AdnRecordCache", "updateAdnBySearch, update oldADN:" + oldAdn.toString() + ", newAdn:" + newAdn.toString() + ",index :" + index);
        if (index == -1) {
            sendErrorResponse(response, "Adn record don't exist for " + oldAdn);
            return;
        }
        if (efid == 20272) {
            AdnRecord foundAdn = oldAdnList.get(index - 1);
            newAdn.mEfid = foundAdn.mEfid;
            newAdn.mExtRecord = foundAdn.mExtRecord;
            newAdn.mRecordNumber = foundAdn.mRecordNumber;
            oldAdn.setAdditionalNumbers(foundAdn.getAdditionalNumbers());
            oldAdn.setEmails(foundAdn.getEmails());
            newAdn.updateAnrEmailArray(oldAdn, this.mUsimPhoneBookManager.getEmailFilesCountEachAdn(), this.mUsimPhoneBookManager.getAnrFilesCountEachAdn());
        }
        if (this.mUserWriteResponse.get(efid) != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
        } else if (efid == 20272) {
            updateEmailAndAnr(efid, this.mUsimPhoneBookManager.getPBPath(efid), oldAdn, newAdn, index, pin2, response);
        } else {
            this.mUserWriteResponse.put(efid, response);
            if (TelBrand.IS_SBM) {
                new AdnRecordLoader(this.mFh, this.mUiccApplication).updateEF(newAdn, efid, extensionEF, index, pin2, obtainMessage(2, efid, index, newAdn));
            } else {
                new AdnRecordLoader(this.mFh).updateEF(newAdn, efid, extensionEF, index, pin2, obtainMessage(2, efid, index, newAdn));
            }
        }
    }

    public void requestLoadAllAdnLike(int efid, int extensionEf, String path, Message response) {
        ArrayList<AdnRecord> result;
        if (efid == 20272) {
            result = this.mUsimPhoneBookManager.loadEfFilesFromUsim();
        } else {
            result = getRecordsIfLoaded(efid);
        }
        if (result == null) {
            ArrayList<Message> waiters = this.mAdnLikeWaiters.get(efid);
            if (waiters != null) {
                waiters.add(response);
                return;
            }
            ArrayList<Message> waiters2 = new ArrayList<>();
            waiters2.add(response);
            this.mAdnLikeWaiters.put(efid, waiters2);
            if (extensionEf >= 0) {
                new AdnRecordLoader(this.mFh).loadAllFromEF(efid, extensionEf, path, obtainMessage(1, efid, 0));
            } else if (response != null) {
                AsyncResult.forMessage(response).exception = new RuntimeException("EF is not known ADN-like EF:" + efid);
                response.sendToTarget();
            }
        } else if (response != null) {
            AsyncResult.forMessage(response).result = result;
            response.sendToTarget();
        }
    }

    private void notifyWaiters(ArrayList<Message> waiters, AsyncResult ar) {
        if (waiters != null) {
            int s = waiters.size();
            for (int i = 0; i < s; i++) {
                Message waiter = waiters.get(i);
                AsyncResult.forMessage(waiter, ar.result, ar.exception);
                waiter.sendToTarget();
            }
        }
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                AsyncResult ar = (AsyncResult) msg.obj;
                int efid = msg.arg1;
                ArrayList<Message> waiters = this.mAdnLikeWaiters.get(efid);
                this.mAdnLikeWaiters.delete(efid);
                if (ar.exception == null) {
                    this.mAdnLikeFiles.put(efid, (ArrayList) ar.result);
                }
                notifyWaiters(waiters, ar);
                if (this.mAdnLikeFiles.get(28474) != null) {
                    setAdnCount(this.mAdnLikeFiles.get(28474).size());
                    return;
                }
                return;
            case 2:
                AsyncResult ar2 = (AsyncResult) msg.obj;
                int efid2 = msg.arg1;
                int index = msg.arg2;
                AdnRecord adn = (AdnRecord) ar2.userObj;
                if (ar2.exception == null) {
                    if (this.mAdnLikeFiles.get(efid2) != null) {
                        this.mAdnLikeFiles.get(efid2).set(index - 1, adn);
                    }
                    if (efid2 == 20272) {
                        this.mUsimPhoneBookManager.loadEfFilesFromUsim().set(index - 1, adn);
                    }
                }
                Message response = this.mUserWriteResponse.get(efid2);
                this.mUserWriteResponse.delete(efid2);
                if (response != null) {
                    AsyncResult.forMessage(response, (Object) null, ar2.exception);
                    response.sendToTarget();
                    return;
                }
                return;
            default:
                return;
        }
    }

    private void updateEmailAndAnr(int efid, String path, AdnRecord oldAdn, AdnRecord newAdn, int index, String pin2, Message response) {
        int extensionEF = extensionEfForEf(newAdn.mEfid);
        if (!updateUsimRecord(oldAdn, newAdn, index, USIM_EFEMAIL_TAG)) {
            sendErrorResponse(response, "update email failed");
        } else if (updateUsimRecord(oldAdn, newAdn, index, 196)) {
            this.mUserWriteResponse.put(efid, response);
            new AdnRecordLoader(this.mFh).updateEF(newAdn, newAdn.mEfid, extensionEF, path, newAdn.mRecordNumber, pin2, obtainMessage(2, efid, index, newAdn));
        } else {
            sendErrorResponse(response, "update anr failed");
        }
    }

    private boolean updateAnrEmailFile(String oldRecord, String newRecord, int index, int tag, int efidIndex) {
        boolean success;
        try {
            switch (tag) {
                case 196:
                    success = this.mUsimPhoneBookManager.updateAnrFile(index, oldRecord, newRecord, efidIndex);
                    break;
                case USIM_EFEMAIL_TAG /* 202 */:
                    success = this.mUsimPhoneBookManager.updateEmailFile(index, oldRecord, newRecord, efidIndex);
                    break;
                default:
                    return false;
            }
            return success;
        } catch (RuntimeException e) {
            Log.e("AdnRecordCache", "update usim record failed", e);
            return false;
        }
    }

    private boolean updateUsimRecord(AdnRecord oldAdn, AdnRecord newAdn, int index, int tag) {
        String[] oldRecords;
        String[] newRecords;
        boolean success = true;
        switch (tag) {
            case 196:
                oldRecords = oldAdn.getAdditionalNumbers();
                newRecords = newAdn.getAdditionalNumbers();
                break;
            case USIM_EFEMAIL_TAG /* 202 */:
                oldRecords = oldAdn.getEmails();
                newRecords = newAdn.getEmails();
                break;
            default:
                return false;
        }
        if (oldRecords == null && newRecords == null) {
            Log.e("AdnRecordCache", "Both old and new EMAIL/ANR are null");
            return true;
        }
        if (oldRecords == null && newRecords != null) {
            for (int i = 0; i < newRecords.length; i++) {
                if (!TextUtils.isEmpty(newRecords[i])) {
                    success &= updateAnrEmailFile(null, newRecords[i], index, tag, i);
                }
            }
        } else if (oldRecords == null || newRecords != null) {
            int maxLen = oldRecords.length > newRecords.length ? oldRecords.length : newRecords.length;
            int i2 = 0;
            while (i2 < maxLen) {
                String oldRecord = i2 >= oldRecords.length ? null : oldRecords[i2];
                String newRecord = i2 >= newRecords.length ? null : newRecords[i2];
                if ((!TextUtils.isEmpty(oldRecord) || !TextUtils.isEmpty(newRecord)) && (oldRecord == null || newRecord == null || !oldRecord.equals(newRecord))) {
                    success &= updateAnrEmailFile(oldRecord, newRecord, index, tag, i2);
                }
                i2++;
            }
        } else {
            for (int i3 = 0; i3 < oldRecords.length; i3++) {
                if (!TextUtils.isEmpty(oldRecords[i3])) {
                    success &= updateAnrEmailFile(oldRecords[i3], null, index, tag, i3);
                }
            }
        }
        return success;
    }

    public void updateUsimAdnByIndex(int efid, AdnRecord newAdn, int recordIndex, String pin2, Message response) {
        ArrayList<AdnRecord> oldAdnList;
        int extensionEF = extensionEfForEf(efid);
        if (extensionEF < 0) {
            sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
            return;
        }
        try {
            if (efid == 20272) {
                oldAdnList = this.mUsimPhoneBookManager.loadEfFilesFromUsim();
            } else {
                oldAdnList = getRecordsIfLoaded(efid);
            }
        } catch (NullPointerException e) {
            oldAdnList = null;
        }
        if (oldAdnList == null) {
            sendErrorResponse(response, "Adn list not exist for EF:" + efid);
            return;
        }
        if (efid == 20272) {
            AdnRecord foundAdn = oldAdnList.get(recordIndex - 1);
            newAdn.mEfid = foundAdn.mEfid;
            newAdn.mExtRecord = foundAdn.mExtRecord;
            newAdn.mRecordNumber = foundAdn.mRecordNumber;
        }
        if (this.mUserWriteResponse.get(efid) != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
        } else if (efid == 20272) {
            updateEmailAndAnr(efid, this.mUsimPhoneBookManager.getPBPath(efid), oldAdnList.get(recordIndex - 1), newAdn, recordIndex, pin2, response);
        } else {
            this.mUserWriteResponse.put(efid, response);
            new AdnRecordLoader(this.mFh).updateEF(newAdn, efid, extensionEF, recordIndex, pin2, obtainMessage(2, efid, recordIndex, newAdn));
        }
    }

    public int getAnrCount() {
        return this.mUsimPhoneBookManager.getAnrCount();
    }

    public int getEmailCount() {
        return this.mUsimPhoneBookManager.getEmailCount();
    }

    public int getSpareAnrCount() {
        return this.mUsimPhoneBookManager.getSpareAnrCount();
    }

    public int getSpareEmailCount() {
        return this.mUsimPhoneBookManager.getSpareEmailCount();
    }

    public int getAdnCount() {
        return this.mAdncountofIcc;
    }

    public void setAdnCount(int count) {
        this.mAdncountofIcc = count;
    }

    public int getUsimAdnCount() {
        return this.mUsimPhoneBookManager.getUsimAdnCount();
    }
}
