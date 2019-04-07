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

    AdnRecordCache(IccFileHandler iccFileHandler) {
        this.mUiccApplication = null;
        this.mAdncountofIcc = 0;
        this.mAdnLikeFiles = new SparseArray();
        this.mAdnLikeWaiters = new SparseArray();
        this.mUserWriteResponse = new SparseArray();
        this.mFh = iccFileHandler;
        this.mUsimPhoneBookManager = new UsimPhoneBookManager(this.mFh, this);
    }

    AdnRecordCache(IccFileHandler iccFileHandler, UiccCardApplication uiccCardApplication) {
        this(iccFileHandler);
        this.mUiccApplication = uiccCardApplication;
    }

    private void clearUserWriters() {
        int size = this.mUserWriteResponse.size();
        for (int i = 0; i < size; i++) {
            sendErrorResponse((Message) this.mUserWriteResponse.valueAt(i), "AdnCace reset");
        }
        this.mUserWriteResponse.clear();
    }

    private void clearWaiters() {
        int size = this.mAdnLikeWaiters.size();
        for (int i = 0; i < size; i++) {
            notifyWaiters((ArrayList) this.mAdnLikeWaiters.valueAt(i), new AsyncResult(null, null, new RuntimeException("AdnCache reset")));
        }
        this.mAdnLikeWaiters.clear();
    }

    private void notifyWaiters(ArrayList<Message> arrayList, AsyncResult asyncResult) {
        if (arrayList != null) {
            int size = arrayList.size();
            for (int i = 0; i < size; i++) {
                Message message = (Message) arrayList.get(i);
                AsyncResult.forMessage(message, asyncResult.result, asyncResult.exception);
                message.sendToTarget();
            }
        }
    }

    private void sendErrorResponse(Message message, String str) {
        if (message != null) {
            AsyncResult.forMessage(message).exception = new RuntimeException(str);
            message.sendToTarget();
        }
    }

    private boolean updateAnrEmailFile(String str, String str2, int i, int i2, int i3) {
        switch (i2) {
            case 196:
                return this.mUsimPhoneBookManager.updateAnrFile(i, str, str2, i3);
            case USIM_EFEMAIL_TAG /*202*/:
                try {
                    return this.mUsimPhoneBookManager.updateEmailFile(i, str, str2, i3);
                } catch (RuntimeException e) {
                    Log.e("AdnRecordCache", "update usim record failed", e);
                    return false;
                }
            default:
                return false;
        }
    }

    private void updateEmailAndAnr(int i, String str, AdnRecord adnRecord, AdnRecord adnRecord2, int i2, String str2, Message message) {
        int extensionEfForEf = extensionEfForEf(adnRecord2.mEfid);
        if (!updateUsimRecord(adnRecord, adnRecord2, i2, USIM_EFEMAIL_TAG)) {
            sendErrorResponse(message, "update email failed");
        } else if (updateUsimRecord(adnRecord, adnRecord2, i2, 196)) {
            this.mUserWriteResponse.put(i, message);
            new AdnRecordLoader(this.mFh).updateEF(adnRecord2, adnRecord2.mEfid, extensionEfForEf, str, adnRecord2.mRecordNumber, str2, obtainMessage(2, i, i2, adnRecord2));
        } else {
            sendErrorResponse(message, "update anr failed");
        }
    }

    private boolean updateUsimRecord(AdnRecord adnRecord, AdnRecord adnRecord2, int i, int i2) {
        String[] additionalNumbers;
        String[] additionalNumbers2;
        boolean z = true;
        int i3 = 0;
        switch (i2) {
            case 196:
                additionalNumbers = adnRecord.getAdditionalNumbers();
                additionalNumbers2 = adnRecord2.getAdditionalNumbers();
                break;
            case USIM_EFEMAIL_TAG /*202*/:
                additionalNumbers = adnRecord.getEmails();
                additionalNumbers2 = adnRecord2.getEmails();
                break;
            default:
                return false;
        }
        if (additionalNumbers == null && additionalNumbers2 == null) {
            Log.e("AdnRecordCache", "Both old and new EMAIL/ANR are null");
            return true;
        }
        boolean z2;
        int i4;
        if (additionalNumbers == null && additionalNumbers2 != null) {
            while (true) {
                z2 = z;
                if (i3 < additionalNumbers2.length) {
                    z = !TextUtils.isEmpty(additionalNumbers2[i3]) ? updateAnrEmailFile(null, additionalNumbers2[i3], i, i2, i3) & z2 : z2;
                    i3++;
                }
            }
        } else if (additionalNumbers == null || additionalNumbers2 != null) {
            int length = additionalNumbers.length > additionalNumbers2.length ? additionalNumbers.length : additionalNumbers2.length;
            i4 = 0;
            while (i4 < length) {
                CharSequence charSequence = i4 >= additionalNumbers.length ? null : additionalNumbers[i4];
                CharSequence charSequence2 = i4 >= additionalNumbers2.length ? null : additionalNumbers2[i4];
                if (!(TextUtils.isEmpty(charSequence) && TextUtils.isEmpty(charSequence2)) && (charSequence == null || charSequence2 == null || !charSequence.equals(charSequence2))) {
                    z &= updateAnrEmailFile(charSequence, charSequence2, i, i2, i4);
                }
                i4++;
            }
            z2 = z;
        } else {
            for (i4 = 0; i4 < additionalNumbers.length; i4++) {
                if (!TextUtils.isEmpty(additionalNumbers[i4])) {
                    z &= updateAnrEmailFile(additionalNumbers[i4], null, i, i2, i4);
                }
            }
            z2 = z;
        }
        return z2;
    }

    public int extensionEfForEf(int i) {
        switch (i) {
            case IccConstants.EF_PBR /*20272*/:
                return 0;
            case 28474:
            case IccConstants.EF_MSISDN /*28480*/:
                return IccConstants.EF_EXT1;
            case IccConstants.EF_FDN /*28475*/:
                return IccConstants.EF_EXT2;
            case IccConstants.EF_SDN /*28489*/:
                return IccConstants.EF_EXT3;
            case IccConstants.EF_MBDN /*28615*/:
                return IccConstants.EF_EXT6;
            default:
                return -1;
        }
    }

    public int getAdnCount() {
        return this.mAdncountofIcc;
    }

    public int getAnrCount() {
        return this.mUsimPhoneBookManager.getAnrCount();
    }

    public int getEmailCount() {
        return this.mUsimPhoneBookManager.getEmailCount();
    }

    public ArrayList<AdnRecord> getRecordsIfLoaded(int i) {
        return (ArrayList) this.mAdnLikeFiles.get(i);
    }

    public int getSpareAnrCount() {
        return this.mUsimPhoneBookManager.getSpareAnrCount();
    }

    public int getSpareEmailCount() {
        return this.mUsimPhoneBookManager.getSpareEmailCount();
    }

    public int getUsimAdnCount() {
        return this.mUsimPhoneBookManager.getUsimAdnCount();
    }

    public void handleMessage(Message message) {
        AsyncResult asyncResult;
        int i;
        switch (message.what) {
            case 1:
                asyncResult = (AsyncResult) message.obj;
                i = message.arg1;
                ArrayList arrayList = (ArrayList) this.mAdnLikeWaiters.get(i);
                this.mAdnLikeWaiters.delete(i);
                if (asyncResult.exception == null) {
                    this.mAdnLikeFiles.put(i, (ArrayList) asyncResult.result);
                }
                notifyWaiters(arrayList, asyncResult);
                if (this.mAdnLikeFiles.get(28474) != null) {
                    setAdnCount(((ArrayList) this.mAdnLikeFiles.get(28474)).size());
                    return;
                }
                return;
            case 2:
                asyncResult = (AsyncResult) message.obj;
                i = message.arg1;
                int i2 = message.arg2;
                AdnRecord adnRecord = (AdnRecord) asyncResult.userObj;
                if (asyncResult.exception == null) {
                    if (this.mAdnLikeFiles.get(i) != null) {
                        ((ArrayList) this.mAdnLikeFiles.get(i)).set(i2 - 1, adnRecord);
                    }
                    if (i == IccConstants.EF_PBR) {
                        this.mUsimPhoneBookManager.loadEfFilesFromUsim().set(i2 - 1, adnRecord);
                    }
                }
                Message message2 = (Message) this.mUserWriteResponse.get(i);
                this.mUserWriteResponse.delete(i);
                if (message2 != null) {
                    AsyncResult.forMessage(message2, null, asyncResult.exception);
                    message2.sendToTarget();
                    return;
                }
                return;
            default:
                return;
        }
    }

    public void requestLoadAllAdnLike(int i, int i2, String str, Message message) {
        Object loadEfFilesFromUsim = i == IccConstants.EF_PBR ? this.mUsimPhoneBookManager.loadEfFilesFromUsim() : getRecordsIfLoaded(i);
        if (loadEfFilesFromUsim == null) {
            ArrayList arrayList = (ArrayList) this.mAdnLikeWaiters.get(i);
            if (arrayList != null) {
                arrayList.add(message);
                return;
            }
            arrayList = new ArrayList();
            arrayList.add(message);
            this.mAdnLikeWaiters.put(i, arrayList);
            if (i2 >= 0) {
                new AdnRecordLoader(this.mFh).loadAllFromEF(i, i2, str, obtainMessage(1, i, 0));
            } else if (message != null) {
                AsyncResult.forMessage(message).exception = new RuntimeException("EF is not known ADN-like EF:" + i);
                message.sendToTarget();
            }
        } else if (message != null) {
            AsyncResult.forMessage(message).result = loadEfFilesFromUsim;
            message.sendToTarget();
        }
    }

    public void reset() {
        this.mAdnLikeFiles.clear();
        this.mUsimPhoneBookManager.reset();
        clearWaiters();
        clearUserWriters();
    }

    public void setAdnCount(int i) {
        this.mAdncountofIcc = i;
    }

    public void updateAdnByIndex(int i, AdnRecord adnRecord, int i2, String str, Message message) {
        int extensionEfForEf = extensionEfForEf(i);
        if (extensionEfForEf < 0) {
            sendErrorResponse(message, "EF is not known ADN-like EF:" + i);
        } else if (((Message) this.mUserWriteResponse.get(i)) != null) {
            sendErrorResponse(message, "Have pending update for EF:" + i);
        } else {
            this.mUserWriteResponse.put(i, message);
            if (TelBrand.IS_SBM) {
                new AdnRecordLoader(this.mFh, this.mUiccApplication).updateEF(adnRecord, i, extensionEfForEf, i2, str, obtainMessage(2, i, i2, adnRecord));
                return;
            }
            new AdnRecordLoader(this.mFh).updateEF(adnRecord, i, extensionEfForEf, i2, str, obtainMessage(2, i, i2, adnRecord));
        }
    }

    public void updateAdnBySearch(int i, AdnRecord adnRecord, AdnRecord adnRecord2, String str, Message message) {
        int extensionEfForEf = extensionEfForEf(i);
        if (extensionEfForEf < 0) {
            sendErrorResponse(message, "EF is not known ADN-like EF:" + i);
            return;
        }
        ArrayList loadEfFilesFromUsim;
        if (i == 20272) {
            try {
                loadEfFilesFromUsim = this.mUsimPhoneBookManager.loadEfFilesFromUsim();
            } catch (NullPointerException e) {
                loadEfFilesFromUsim = null;
            }
        } else {
            loadEfFilesFromUsim = getRecordsIfLoaded(i);
        }
        if (loadEfFilesFromUsim == null) {
            sendErrorResponse(message, "Adn list not exist for EF:" + i);
            return;
        }
        AdnRecord adnRecord3;
        int i2 = 1;
        int i3 = -2;
        int i4 = 0;
        Iterator it = loadEfFilesFromUsim.iterator();
        int i5 = 0;
        while (it.hasNext()) {
            adnRecord3 = (AdnRecord) it.next();
            Object obj = null;
            if (i == 20272) {
                int pbrIndexBy = this.mUsimPhoneBookManager.getPbrIndexBy(i2 - 1);
                if (pbrIndexBy != i3) {
                    i5 = this.mUsimPhoneBookManager.getEmptyAnrNum_Pbrindex(pbrIndexBy);
                    i4 = this.mUsimPhoneBookManager.getEmptyEmailNum_Pbrindex(pbrIndexBy);
                    Log.d("AdnRecordCache", "updateAdnBySearch, pbrIndex: " + pbrIndexBy + " anrNum:" + i5 + " emailNum:" + i4);
                    i3 = pbrIndexBy;
                }
                if ((i5 == 0 && adnRecord.getAdditionalNumbers() == null && adnRecord2.getAdditionalNumbers() != null) || (i4 == 0 && adnRecord.getEmails() == null && adnRecord2.getEmails() != null)) {
                    obj = 1;
                }
            }
            if (obj == null && adnRecord.isEqual(adnRecord3)) {
                break;
            }
            i2++;
        }
        i2 = -1;
        Log.d("AdnRecordCache", "updateAdnBySearch, update oldADN:" + adnRecord.toString() + ", newAdn:" + adnRecord2.toString() + ",index :" + i2);
        if (i2 == -1) {
            sendErrorResponse(message, "Adn record don't exist for " + adnRecord);
            return;
        }
        if (i == 20272) {
            adnRecord3 = (AdnRecord) loadEfFilesFromUsim.get(i2 - 1);
            adnRecord2.mEfid = adnRecord3.mEfid;
            adnRecord2.mExtRecord = adnRecord3.mExtRecord;
            adnRecord2.mRecordNumber = adnRecord3.mRecordNumber;
            adnRecord.setAdditionalNumbers(adnRecord3.getAdditionalNumbers());
            adnRecord.setEmails(adnRecord3.getEmails());
            adnRecord2.updateAnrEmailArray(adnRecord, this.mUsimPhoneBookManager.getEmailFilesCountEachAdn(), this.mUsimPhoneBookManager.getAnrFilesCountEachAdn());
        }
        if (((Message) this.mUserWriteResponse.get(i)) != null) {
            sendErrorResponse(message, "Have pending update for EF:" + i);
        } else if (i == 20272) {
            updateEmailAndAnr(i, this.mUsimPhoneBookManager.getPBPath(i), adnRecord, adnRecord2, i2, str, message);
        } else {
            this.mUserWriteResponse.put(i, message);
            if (TelBrand.IS_SBM) {
                new AdnRecordLoader(this.mFh, this.mUiccApplication).updateEF(adnRecord2, i, extensionEfForEf, i2, str, obtainMessage(2, i, i2, adnRecord2));
                return;
            }
            new AdnRecordLoader(this.mFh).updateEF(adnRecord2, i, extensionEfForEf, i2, str, obtainMessage(2, i, i2, adnRecord2));
        }
    }

    public void updateUsimAdnByIndex(int i, AdnRecord adnRecord, int i2, String str, Message message) {
        int extensionEfForEf = extensionEfForEf(i);
        if (extensionEfForEf < 0) {
            sendErrorResponse(message, "EF is not known ADN-like EF:" + i);
            return;
        }
        ArrayList loadEfFilesFromUsim;
        if (i == IccConstants.EF_PBR) {
            try {
                loadEfFilesFromUsim = this.mUsimPhoneBookManager.loadEfFilesFromUsim();
            } catch (NullPointerException e) {
                loadEfFilesFromUsim = null;
            }
        } else {
            loadEfFilesFromUsim = getRecordsIfLoaded(i);
        }
        if (loadEfFilesFromUsim == null) {
            sendErrorResponse(message, "Adn list not exist for EF:" + i);
            return;
        }
        if (i == IccConstants.EF_PBR) {
            AdnRecord adnRecord2 = (AdnRecord) loadEfFilesFromUsim.get(i2 - 1);
            adnRecord.mEfid = adnRecord2.mEfid;
            adnRecord.mExtRecord = adnRecord2.mExtRecord;
            adnRecord.mRecordNumber = adnRecord2.mRecordNumber;
        }
        if (((Message) this.mUserWriteResponse.get(i)) != null) {
            sendErrorResponse(message, "Have pending update for EF:" + i);
        } else if (i == IccConstants.EF_PBR) {
            updateEmailAndAnr(i, this.mUsimPhoneBookManager.getPBPath(i), (AdnRecord) loadEfFilesFromUsim.get(i2 - 1), adnRecord, i2, str, message);
        } else {
            this.mUserWriteResponse.put(i, message);
            new AdnRecordLoader(this.mFh).updateEF(adnRecord, i, extensionEfForEf, i2, str, obtainMessage(2, i, i2, adnRecord));
        }
    }
}
