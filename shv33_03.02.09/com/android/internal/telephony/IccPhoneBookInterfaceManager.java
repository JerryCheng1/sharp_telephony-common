package com.android.internal.telephony;

import android.content.ContentValues;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.AdnRecordCache;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.SimPhoneBookAdnRecordCache;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class IccPhoneBookInterfaceManager {
    protected static final boolean ALLOW_SIM_OP_IN_UI_THREAD = false;
    protected static final boolean DBG = true;
    protected static final int EVENT_GET_SIZE_DONE = 1;
    protected static final int EVENT_LOAD_DONE = 2;
    protected static final int EVENT_UPDATE_DONE = 3;
    static final String LOG_TAG = "IccPhoneBookIM";
    protected AdnRecordCache mAdnCache;
    protected Handler mBaseHandler = new Handler() {
        public void handleMessage(Message msg) {
            boolean z = true;
            AsyncResult ar;
            Object obj;
            switch (msg.what) {
                case 1:
                    ar = msg.obj;
                    obj = IccPhoneBookInterfaceManager.this.mLock;
                    synchronized (obj) {
                        if (ar.exception == null) {
                            IccPhoneBookInterfaceManager.this.mRecordSize = (int[]) ar.result;
                            IccPhoneBookInterfaceManager.this.logd("GET_RECORD_SIZE Size " + IccPhoneBookInterfaceManager.this.mRecordSize[0] + " total " + IccPhoneBookInterfaceManager.this.mRecordSize[1] + " #record " + IccPhoneBookInterfaceManager.this.mRecordSize[2]);
                        }
                        notifyPending(ar);
                        break;
                    }
                case 2:
                    ar = (AsyncResult) msg.obj;
                    obj = IccPhoneBookInterfaceManager.this.mLock;
                    synchronized (obj) {
                        if (ar.exception == null) {
                            IccPhoneBookInterfaceManager.this.logd("Load ADN records done");
                            IccPhoneBookInterfaceManager.this.mRecords = (List) ar.result;
                        } else {
                            IccPhoneBookInterfaceManager.this.logd("Cannot load ADN records");
                            IccPhoneBookInterfaceManager.this.mRecords = null;
                        }
                        notifyPending(ar);
                        break;
                    }
                case 3:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        IccPhoneBookInterfaceManager.this.logd("exception of EVENT_UPDATE_DONE is" + ar.exception);
                    }
                    synchronized (IccPhoneBookInterfaceManager.this.mLock) {
                        IccPhoneBookInterfaceManager iccPhoneBookInterfaceManager = IccPhoneBookInterfaceManager.this;
                        if (ar.exception != null) {
                            z = IccPhoneBookInterfaceManager.ALLOW_SIM_OP_IN_UI_THREAD;
                        }
                        iccPhoneBookInterfaceManager.mSuccess = z;
                        notifyPending(ar);
                    }
                    return;
                default:
                    return;
            }
        }

        private void notifyPending(AsyncResult ar) {
            if (ar.userObj != null) {
                ar.userObj.set(true);
            }
            IccPhoneBookInterfaceManager.this.mLock.notifyAll();
        }
    };
    private UiccCardApplication mCurrentApp = null;
    private boolean mIs3gCard = ALLOW_SIM_OP_IN_UI_THREAD;
    protected final Object mLock = new Object();
    protected Phone mPhone;
    protected int[] mRecordSize;
    protected List<AdnRecord> mRecords;
    protected SimPhoneBookAdnRecordCache mSimPbAdnCache;
    protected boolean mSuccess;

    public IccPhoneBookInterfaceManager(Phone phone) {
        this.mPhone = phone;
        IccRecords r = phone.getIccRecords();
        if (r != null) {
            this.mAdnCache = r.getAdnCache();
        }
        if (isSimPhoneBookEnabled() && this.mSimPbAdnCache == null) {
            this.mSimPbAdnCache = new SimPhoneBookAdnRecordCache(phone.getContext(), phone.getPhoneId(), phone.mCi);
        }
    }

    public void dispose() {
        if (this.mRecords != null) {
            this.mRecords.clear();
        }
    }

    public void updateIccRecords(IccRecords iccRecords) {
        if (iccRecords != null) {
            this.mAdnCache = iccRecords.getAdnCache();
        } else {
            this.mAdnCache = null;
        }
    }

    /* Access modifiers changed, original: protected */
    public void logd(String msg) {
        Rlog.d(LOG_TAG, "[IccPbInterfaceManager] " + msg);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String msg) {
        Rlog.e(LOG_TAG, "[IccPbInterfaceManager] " + msg);
    }

    public boolean updateAdnRecordsInEfBySearch(int efid, String oldTag, String oldPhoneNumber, String newTag, String newPhoneNumber, String pin2) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.WRITE_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.WRITE_CONTACTS permission");
        }
        logd("updateAdnRecordsInEfBySearch: efid=0x" + Integer.toHexString(efid).toUpperCase() + " (" + oldTag + "," + oldPhoneNumber + ")" + "==>" + " (" + newTag + "," + newPhoneNumber + ")" + " pin2=" + pin2);
        efid = updateEfForIccType(efid);
        synchronized (this.mLock) {
            checkThread();
            this.mSuccess = ALLOW_SIM_OP_IN_UI_THREAD;
            AtomicBoolean status = new AtomicBoolean(ALLOW_SIM_OP_IN_UI_THREAD);
            Message response = this.mBaseHandler.obtainMessage(3, status);
            AdnRecord oldAdn = new AdnRecord(oldTag, oldPhoneNumber);
            AdnRecord newAdn = new AdnRecord(newTag, newPhoneNumber);
            if (this.mAdnCache != null) {
                this.mAdnCache.updateAdnBySearch(efid, oldAdn, newAdn, pin2, response);
                waitForResult(status);
            } else {
                loge("Failure while trying to update by search due to uninitialised adncache");
            }
        }
        return this.mSuccess;
    }

    public boolean updateAdnRecordsWithContentValuesInEfBySearch(int efid, ContentValues values, String pin2) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.WRITE_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.WRITE_CONTACTS permission");
        }
        String oldTag = values.getAsString("tag");
        String newTag = values.getAsString("newTag");
        String oldPhoneNumber = values.getAsString("number");
        String newPhoneNumber = values.getAsString("newNumber");
        String oldEmail = values.getAsString("emails");
        String newEmail = values.getAsString("newEmails");
        String oldAnr = values.getAsString("anrs");
        String newAnr = values.getAsString("newAnrs");
        String[] oldEmailArray = TextUtils.isEmpty(oldEmail) ? null : getStringArray(oldEmail);
        String[] newEmailArray = TextUtils.isEmpty(newEmail) ? null : getStringArray(newEmail);
        String[] oldAnrArray = TextUtils.isEmpty(oldAnr) ? null : getAnrStringArray(oldAnr);
        String[] newAnrArray = TextUtils.isEmpty(newAnr) ? null : getAnrStringArray(newAnr);
        efid = updateEfForIccType(efid);
        logd("updateAdnRecordsWithContentValuesInEfBySearch: efid=" + efid + ", values = " + values + ", pin2=" + pin2);
        synchronized (this.mLock) {
            checkThread();
            this.mSuccess = ALLOW_SIM_OP_IN_UI_THREAD;
            AtomicBoolean atomicBoolean = new AtomicBoolean(ALLOW_SIM_OP_IN_UI_THREAD);
            Message response = this.mBaseHandler.obtainMessage(3, atomicBoolean);
            AdnRecord oldAdn = new AdnRecord(oldTag, oldPhoneNumber, oldEmailArray, oldAnrArray);
            AdnRecord newAdn = new AdnRecord(newTag, newPhoneNumber, newEmailArray, newAnrArray);
            if (isSimPhoneBookEnabled() && (efid == 20272 || efid == 28474)) {
                if (this.mSimPbAdnCache != null) {
                    this.mSimPbAdnCache.updateSimPbAdnBySearch(oldAdn, newAdn, response);
                    waitForResult(atomicBoolean);
                } else {
                    loge("Failure while trying to update by search due to uninit sim pb adncache");
                }
            } else if (this.mAdnCache != null) {
                this.mAdnCache.updateAdnBySearch(efid, oldAdn, newAdn, pin2, response);
                waitForResult(atomicBoolean);
            } else {
                loge("Failure while trying to update by search due to uninitialised adncache");
            }
        }
        return this.mSuccess;
    }

    public boolean updateAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber, int index, String pin2) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.WRITE_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.WRITE_CONTACTS permission");
        }
        logd("updateAdnRecordsInEfByIndex: efid=0x" + Integer.toHexString(efid).toUpperCase() + " Index=" + index + " ==> " + "(" + newTag + "," + newPhoneNumber + ")" + " pin2=" + pin2);
        synchronized (this.mLock) {
            checkThread();
            this.mSuccess = ALLOW_SIM_OP_IN_UI_THREAD;
            AtomicBoolean status = new AtomicBoolean(ALLOW_SIM_OP_IN_UI_THREAD);
            Message response = this.mBaseHandler.obtainMessage(3, status);
            AdnRecord newAdn = new AdnRecord(newTag, newPhoneNumber);
            if (this.mAdnCache != null) {
                this.mAdnCache.updateAdnByIndex(efid, newAdn, index, pin2, response);
                waitForResult(status);
            } else {
                loge("Failure while trying to update by index due to uninitialised adncache");
            }
        }
        return this.mSuccess;
    }

    public int[] getAdnRecordsSize(int efid) {
        logd("getAdnRecordsSize: efid=" + efid);
        synchronized (this.mLock) {
            checkThread();
            this.mRecordSize = new int[3];
            AtomicBoolean status = new AtomicBoolean(ALLOW_SIM_OP_IN_UI_THREAD);
            Message response = this.mBaseHandler.obtainMessage(1, status);
            IccFileHandler fh = this.mPhone.getIccFileHandler();
            if (fh != null) {
                fh.getEFLinearRecordSize(efid, response);
                waitForResult(status);
            }
        }
        return this.mRecordSize;
    }

    public List<AdnRecord> getAdnRecordsInEf(int efid) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.READ_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.READ_CONTACTS permission");
        }
        efid = updateEfForIccType(efid);
        logd("getAdnRecordsInEF: efid=0x" + Integer.toHexString(efid).toUpperCase());
        synchronized (this.mLock) {
            checkThread();
            AtomicBoolean status = new AtomicBoolean(ALLOW_SIM_OP_IN_UI_THREAD);
            Message response = this.mBaseHandler.obtainMessage(2, status);
            if (isSimPhoneBookEnabled() && (efid == IccConstants.EF_PBR || efid == 28474)) {
                if (this.mSimPbAdnCache != null) {
                    this.mSimPbAdnCache.requestLoadAllAdnLike(response);
                    waitForResult(status);
                } else {
                    loge("Failure while trying to load from SIM due to uninit  sim pb adncache");
                }
            } else if (this.mAdnCache != null) {
                this.mAdnCache.requestLoadAllAdnLike(efid, this.mAdnCache.extensionEfForEf(efid), response);
                waitForResult(status);
            } else {
                loge("Failure while trying to load from SIM due to uninitialised adncache");
            }
        }
        return this.mRecords;
    }

    private boolean isSimPhoneBookEnabled() {
        if (this.mPhone.getContext().getResources().getBoolean(17957054)) {
            return true;
        }
        return ALLOW_SIM_OP_IN_UI_THREAD;
    }

    /* Access modifiers changed, original: protected */
    public void checkThread() {
        if (this.mBaseHandler.getLooper().equals(Looper.myLooper())) {
            loge("query() called on the main UI thread!");
            throw new IllegalStateException("You cannot call query on this provder from the main UI thread.");
        }
    }

    /* Access modifiers changed, original: protected */
    public void waitForResult(AtomicBoolean status) {
        while (!status.get()) {
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                logd("interrupted while trying to update by search");
            }
        }
    }

    private int updateEfForIccType(int efid) {
        if (efid == 28474 && this.mPhone.getCurrentUiccAppType() == AppType.APPTYPE_USIM) {
            return IccConstants.EF_PBR;
        }
        return efid;
    }

    private String[] getStringArray(String str) {
        if (str != null) {
            return str.split(",");
        }
        return null;
    }

    private String[] getAnrStringArray(String str) {
        if (str != null) {
            return str.split(":");
        }
        return null;
    }

    public int[] getAdnRecordsCapacity() {
        int[] capacity = new int[6];
        if (isSimPhoneBookEnabled()) {
            if (this.mSimPbAdnCache != null) {
                capacity[0] = this.mSimPbAdnCache.getAdnCount();
                capacity[1] = this.mSimPbAdnCache.getUsedAdnCount();
                capacity[2] = this.mSimPbAdnCache.getEmailCount();
                capacity[3] = this.mSimPbAdnCache.getUsedEmailCount();
                capacity[4] = this.mSimPbAdnCache.getAnrCount();
                capacity[5] = this.mSimPbAdnCache.getUsedAnrCount();
            } else {
                loge("mAdnCache is NULL when getAdnRecordsCapacity.");
            }
        }
        logd("getAdnRecordsCapacity: max adn=" + capacity[0] + ", used adn=" + capacity[1] + ", max email=" + capacity[2] + ", used email=" + capacity[3] + ", max anr=" + capacity[4] + ", used anr=" + capacity[5]);
        return capacity;
    }
}
