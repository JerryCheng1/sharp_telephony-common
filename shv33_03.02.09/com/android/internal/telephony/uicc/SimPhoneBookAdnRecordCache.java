package com.android.internal.telephony.uicc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.util.SparseArray;
import com.android.internal.telephony.CommandsInterface;
import java.util.ArrayList;
import java.util.Iterator;

public final class SimPhoneBookAdnRecordCache extends Handler {
    static final String ACTION_ADN_INIT_DONE = "android.intent.action.ACTION_ADN_INIT_DONE";
    private static final boolean DBG = true;
    static final int EVENT_INIT_ADN_DONE = 1;
    static final int EVENT_LOAD_ADN_RECORD_DONE = 3;
    static final int EVENT_LOAD_ALL_ADN_LIKE_DONE = 4;
    static final int EVENT_QUERY_ADN_RECORD_DONE = 2;
    static final int EVENT_SIM_REFRESH = 6;
    static final int EVENT_UPDATE_ADN_RECORD_DONE = 5;
    private static final String LOG_TAG = "SimPhoneBookAdnRecordCache";
    SparseArray<int[]> extRecList = new SparseArray();
    private int mAddNumCount = 0;
    private int mAdnCount = 0;
    ArrayList<Message> mAdnLoadingWaiters = new ArrayList();
    Message mAdnUpdatingWaiter = null;
    protected final CommandsInterface mCi;
    protected Context mContext;
    private int mEmailCount = 0;
    private Object mLock = new Object();
    protected int mPhoneId;
    private int mRecCount = 0;
    private boolean mRefreshAdnCache = false;
    private ArrayList<AdnRecord> mSimPbRecords;
    private int mValidAddNumCount = 0;
    private int mValidAdnCount = 0;
    private int mValidEmailCount = 0;
    private final BroadcastReceiver sReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.intent.action.SIM_STATE_CHANGED")) {
                int phoneId = intent.getIntExtra("phone", -1);
                String simStatus = intent.getStringExtra("ss");
                if ("ABSENT".equals(simStatus) && SimPhoneBookAdnRecordCache.this.mPhoneId == phoneId) {
                    SimPhoneBookAdnRecordCache.this.log("ACTION_SIM_STATE_CHANGED intent received simStatus: " + simStatus + "phoneId: " + phoneId);
                    SimPhoneBookAdnRecordCache.this.invalidateAdnCache();
                }
            }
        }
    };

    public SimPhoneBookAdnRecordCache(Context context, int phoneId, CommandsInterface ci) {
        this.mCi = ci;
        this.mSimPbRecords = new ArrayList();
        this.mPhoneId = phoneId;
        this.mContext = context;
        this.mCi.registerForAdnInitDone(this, 1, null);
        this.mCi.registerForIccRefresh(this, 6, null);
        context.registerReceiver(this.sReceiver, new IntentFilter("android.intent.action.SIM_STATE_CHANGED"));
    }

    public void reset() {
        this.mAdnLoadingWaiters.clear();
        clearUpdatingWriter();
        this.mSimPbRecords.clear();
        this.mRecCount = 0;
        this.mRefreshAdnCache = false;
    }

    private void clearUpdatingWriter() {
        sendErrorResponse(this.mAdnUpdatingWaiter, "SimPhoneBookAdnRecordCache reset");
        this.mAdnUpdatingWaiter = null;
    }

    private void sendErrorResponse(Message response, String errString) {
        if (response != null) {
            AsyncResult.forMessage(response).exception = new RuntimeException(errString);
            response.sendToTarget();
        }
    }

    private void notifyAndClearWaiters() {
        if (this.mAdnLoadingWaiters != null) {
            int s = this.mAdnLoadingWaiters.size();
            for (int i = 0; i < s; i++) {
                Message response = (Message) this.mAdnLoadingWaiters.get(i);
                if (response != null) {
                    AsyncResult.forMessage(response).result = this.mSimPbRecords;
                    response.sendToTarget();
                }
            }
            this.mAdnLoadingWaiters.clear();
        }
    }

    public void queryAdnRecord() {
        this.mRecCount = 0;
        this.mAdnCount = 0;
        this.mValidAdnCount = 0;
        this.mEmailCount = 0;
        this.mAddNumCount = 0;
        log("start to queryAdnRecord");
        this.mCi.getAdnRecord(obtainMessage(2));
        this.mCi.registerForAdnRecordsInfo(this, 3, null);
        try {
            this.mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in queryAdnRecord");
        }
        this.mCi.unregisterForAdnRecordsInfo(this);
    }

    /* JADX WARNING: Missing block: B:12:0x0025, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void requestLoadAllAdnLike(Message response) {
        if (this.mAdnLoadingWaiters != null) {
            this.mAdnLoadingWaiters.add(response);
        }
        synchronized (this.mLock) {
            if (this.mSimPbRecords.isEmpty()) {
                queryAdnRecord();
                return;
            }
            log("ADN cache has already filled in");
            if (this.mRefreshAdnCache) {
                this.mRefreshAdnCache = false;
                refreshAdnCache();
            } else {
                notifyAndClearWaiters();
            }
        }
    }

    public void updateSimPbAdnBySearch(AdnRecord oldAdn, AdnRecord newAdn, Message response) {
        ArrayList<AdnRecord> oldAdnList = this.mSimPbRecords;
        synchronized (this.mLock) {
            if (this.mSimPbRecords.isEmpty()) {
                queryAdnRecord();
            } else {
                log("ADN cache has already filled in");
                if (this.mRefreshAdnCache) {
                    this.mRefreshAdnCache = false;
                    refreshAdnCache();
                }
            }
        }
        if (oldAdnList == null) {
            sendErrorResponse(response, "Sim PhoneBook Adn list not exist");
            return;
        }
        int index = -1;
        int count = 1;
        if (!oldAdn.isEmpty() || newAdn.isEmpty()) {
            Iterator<AdnRecord> it = oldAdnList.iterator();
            while (it.hasNext()) {
                if (oldAdn.isEqual((AdnRecord) it.next())) {
                    index = count;
                    break;
                }
                count++;
            }
        } else {
            index = 0;
        }
        if (index == -1) {
            sendErrorResponse(response, "Sim PhoneBook Adn record don't exist for " + oldAdn);
        } else if (index == 0 && this.mValidAdnCount == this.mAdnCount) {
            sendErrorResponse(response, "Sim PhoneBook Adn record is full");
        } else {
            int recordIndex = index == 0 ? 0 : ((AdnRecord) oldAdnList.get(index - 1)).getRecordNumber();
            SimPhoneBookAdnRecord updateAdn = new SimPhoneBookAdnRecord();
            updateAdn.mRecordIndex = recordIndex;
            updateAdn.mAlphaTag = newAdn.getAlphaTag();
            updateAdn.mNumber = newAdn.getNumber();
            if (newAdn.getEmails() != null) {
                updateAdn.mEmails = newAdn.getEmails();
                updateAdn.mEmailCount = updateAdn.mEmails.length;
            }
            if (newAdn.getAdditionalNumbers() != null) {
                updateAdn.mAdNumbers = newAdn.getAdditionalNumbers();
                updateAdn.mAdNumCount = updateAdn.mAdNumbers.length;
            }
            if (this.mAdnUpdatingWaiter != null) {
                sendErrorResponse(response, "Have pending update for Sim PhoneBook Adn");
                return;
            }
            this.mAdnUpdatingWaiter = response;
            this.mCi.updateAdnRecord(updateAdn, obtainMessage(5, index, 0, newAdn));
        }
    }

    public void handleMessage(Message msg) {
        AsyncResult ar = msg.obj;
        switch (msg.what) {
            case 1:
                ar = msg.obj;
                if (ar.exception == null) {
                    invalidateAdnCache();
                    Intent intent = new Intent(ACTION_ADN_INIT_DONE);
                    intent.addFlags(536870912);
                    SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhoneId);
                    log("broadcast intent ACTION_ADN_INIT_DONE for mPhoneId=" + this.mPhoneId);
                    this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
                    return;
                }
                log("Init ADN done Exception: " + ar.exception);
                return;
            case 2:
                log("Querying ADN record done");
                if (ar.exception != null) {
                    synchronized (this.mLock) {
                        this.mLock.notify();
                    }
                    for (Message response : this.mAdnLoadingWaiters) {
                        sendErrorResponse(response, "Query adn record failed" + ar.exception);
                    }
                    this.mAdnLoadingWaiters.clear();
                    return;
                }
                this.mAdnCount = ((int[]) ar.result)[0];
                this.mValidAdnCount = ((int[]) ar.result)[1];
                this.mEmailCount = ((int[]) ar.result)[2];
                this.mValidEmailCount = ((int[]) ar.result)[3];
                this.mAddNumCount = ((int[]) ar.result)[4];
                this.mValidAddNumCount = ((int[]) ar.result)[5];
                log("Max ADN count is: " + this.mAdnCount + ", Valid ADN count is: " + this.mValidAdnCount + ", Email count is: " + this.mEmailCount + ", Valid Email count is: " + this.mValidEmailCount + ", Add number count is: " + this.mAddNumCount + ", Valid Add number count is: " + this.mValidAddNumCount);
                if (this.mValidAdnCount == 0 || this.mRecCount == this.mValidAdnCount) {
                    sendMessage(obtainMessage(4));
                    return;
                }
                return;
            case 3:
                log("Loading ADN record done");
                if (ar.exception == null) {
                    SimPhoneBookAdnRecord[] AdnRecordsGroup = ar.result;
                    for (int i = 0; i < AdnRecordsGroup.length; i++) {
                        if (AdnRecordsGroup[i] != null) {
                            this.mSimPbRecords.add(new AdnRecord(0, AdnRecordsGroup[i].getRecordIndex(), AdnRecordsGroup[i].getAlphaTag(), AdnRecordsGroup[i].getNumber(), AdnRecordsGroup[i].getEmails(), AdnRecordsGroup[i].getAdNumbers()));
                            this.mRecCount++;
                        }
                    }
                    if (this.mRecCount == this.mValidAdnCount) {
                        sendMessage(obtainMessage(4));
                        return;
                    }
                    return;
                }
                return;
            case 4:
                log("Loading all ADN records done");
                synchronized (this.mLock) {
                    this.mLock.notify();
                }
                notifyAndClearWaiters();
                return;
            case 5:
                log("Update ADN record done");
                Throwable e = null;
                if (ar.exception == null) {
                    int index = msg.arg1;
                    AdnRecord adn = ar.userObj;
                    int recordIndex = ((int[]) ar.result)[0];
                    int adnRecordIndex;
                    if (index == 0) {
                        log("Record number for added ADN is " + recordIndex);
                        adn.setRecordNumber(recordIndex);
                        this.mSimPbRecords.add(adn);
                        this.mValidAdnCount++;
                    } else if (adn.isEmpty()) {
                        adnRecordIndex = ((AdnRecord) this.mSimPbRecords.get(index - 1)).getRecordNumber();
                        log("Record number for deleted ADN is " + adnRecordIndex);
                        if (recordIndex == adnRecordIndex) {
                            this.mSimPbRecords.remove(index - 1);
                            this.mValidAdnCount--;
                        } else {
                            e = new RuntimeException("The index for deleted ADN record did not match");
                        }
                    } else {
                        adnRecordIndex = ((AdnRecord) this.mSimPbRecords.get(index - 1)).getRecordNumber();
                        log("Record number for changed ADN is " + adnRecordIndex);
                        if (recordIndex == adnRecordIndex) {
                            adn.setRecordNumber(recordIndex);
                            this.mSimPbRecords.set(index - 1, adn);
                        } else {
                            e = new RuntimeException("The index for changed ADN record did not match");
                        }
                    }
                } else {
                    e = new RuntimeException("Update adn record failed", ar.exception);
                }
                if (this.mAdnUpdatingWaiter != null) {
                    AsyncResult.forMessage(this.mAdnUpdatingWaiter, null, e);
                    this.mAdnUpdatingWaiter.sendToTarget();
                    this.mAdnUpdatingWaiter = null;
                    return;
                }
                return;
            case 6:
                ar = msg.obj;
                log("SIM REFRESH occurred");
                if (ar.exception == null) {
                    IccRefreshResponse refreshRsp = ar.result;
                    if (refreshRsp == null) {
                        log("IccRefreshResponse received is null");
                        return;
                    } else if (refreshRsp.refreshResult == 0 || refreshRsp.refreshResult == 1) {
                        invalidateAdnCache();
                        return;
                    } else {
                        return;
                    }
                }
                log("SIM refresh Exception: " + ar.exception);
                return;
            default:
                return;
        }
    }

    public int getAdnCount() {
        return this.mAdnCount;
    }

    public int getUsedAdnCount() {
        return this.mValidAdnCount;
    }

    public int getEmailCount() {
        return this.mEmailCount;
    }

    public int getUsedEmailCount() {
        return this.mValidEmailCount;
    }

    public int getAnrCount() {
        return this.mAddNumCount;
    }

    public int getUsedAnrCount() {
        return this.mValidAddNumCount;
    }

    private void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    public void invalidateAdnCache() {
        log("invalidateAdnCache");
        this.mRefreshAdnCache = true;
    }

    private void refreshAdnCache() {
        log("refreshAdnCache");
        this.mSimPbRecords.clear();
        queryAdnRecord();
    }
}
