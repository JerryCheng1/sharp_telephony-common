package com.android.internal.telephony;

import android.content.ContentValues;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.AdnRecordCache;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class IccPhoneBookInterfaceManager {
    protected static final boolean ALLOW_SIM_OP_IN_UI_THREAD = false;
    protected static final boolean DBG = true;
    protected static final int EVENT_GET_SIZE_DONE = 1;
    protected static final int EVENT_LOAD_DONE = 2;
    protected static final int EVENT_UPDATE_DONE = 3;
    protected AdnRecordCache mAdnCache;
    protected Handler mBaseHandler = new Handler() {
        private void notifyPending(AsyncResult asyncResult) {
            if (asyncResult.userObj != null) {
                ((AtomicBoolean) asyncResult.userObj).set(true);
            }
            IccPhoneBookInterfaceManager.this.mLock.notifyAll();
        }

        public void handleMessage(Message message) {
            boolean z = false;
            AsyncResult asyncResult;
            switch (message.what) {
                case 1:
                    asyncResult = (AsyncResult) message.obj;
                    synchronized (IccPhoneBookInterfaceManager.this.mLock) {
                        if (asyncResult.exception == null) {
                            IccPhoneBookInterfaceManager.this.mRecordSize = (int[]) asyncResult.result;
                            IccPhoneBookInterfaceManager.this.logd("GET_RECORD_SIZE Size " + IccPhoneBookInterfaceManager.this.mRecordSize[0] + " total " + IccPhoneBookInterfaceManager.this.mRecordSize[1] + " #record " + IccPhoneBookInterfaceManager.this.mRecordSize[2]);
                        }
                        notifyPending(asyncResult);
                    }
                    return;
                case 2:
                    asyncResult = (AsyncResult) message.obj;
                    synchronized (IccPhoneBookInterfaceManager.this.mLock) {
                        if (asyncResult.exception == null) {
                            IccPhoneBookInterfaceManager.this.mRecords = (List) asyncResult.result;
                        } else {
                            IccPhoneBookInterfaceManager.this.logd("Cannot load ADN records");
                            if (IccPhoneBookInterfaceManager.this.mRecords != null) {
                                IccPhoneBookInterfaceManager.this.mRecords = null;
                            }
                        }
                        notifyPending(asyncResult);
                    }
                    return;
                case 3:
                    asyncResult = (AsyncResult) message.obj;
                    synchronized (IccPhoneBookInterfaceManager.this.mLock) {
                        IccPhoneBookInterfaceManager iccPhoneBookInterfaceManager = IccPhoneBookInterfaceManager.this;
                        if (asyncResult.exception == null) {
                            z = true;
                        }
                        iccPhoneBookInterfaceManager.mSuccess = z;
                        notifyPending(asyncResult);
                    }
                    return;
                default:
                    return;
            }
        }
    };
    private UiccCardApplication mCurrentApp = null;
    private boolean mIs3gCard = false;
    protected final Object mLock = new Object();
    protected PhoneBase mPhone;
    protected int[] mRecordSize;
    protected List<AdnRecord> mRecords;
    protected boolean mSuccess;

    public IccPhoneBookInterfaceManager(PhoneBase phoneBase) {
        this.mPhone = phoneBase;
    }

    private void cleanUp() {
        if (this.mAdnCache != null) {
            this.mAdnCache.reset();
            this.mAdnCache = null;
        }
        this.mIs3gCard = false;
        this.mCurrentApp = null;
    }

    private String[] getStringArray(String str) {
        return str != null ? str.split(",") : null;
    }

    private int updateEfForIccType(int i) {
        if (i != 28474 || !this.mIs3gCard) {
            return i;
        }
        logd("Translate EF_ADN to EF_PBR");
        return IccConstants.EF_PBR;
    }

    /* Access modifiers changed, original: protected */
    public void checkThread() {
        if (this.mBaseHandler.getLooper().equals(Looper.myLooper())) {
            loge("query() called on the main UI thread!");
            throw new IllegalStateException("You cannot call query on this provder from the main UI thread.");
        }
    }

    public void dispose() {
        if (this.mRecords != null) {
            this.mRecords.clear();
        }
        cleanUp();
    }

    public int getAdnCount() {
        if (this.mAdnCache != null) {
            return this.mPhone.getCurrentUiccAppType() == AppType.APPTYPE_USIM ? this.mAdnCache.getUsimAdnCount() : this.mAdnCache.getAdnCount();
        } else {
            loge("mAdnCache is NULL when getAdnCount.");
            return 0;
        }
    }

    public List<AdnRecord> getAdnRecordsInEf(int i) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.READ_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.READ_CONTACTS permission");
        }
        int updateEfForIccType = updateEfForIccType(i);
        logd("getAdnRecordsInEF: efid=" + updateEfForIccType);
        synchronized (this.mLock) {
            checkThread();
            AtomicBoolean atomicBoolean = new AtomicBoolean(false);
            Message obtainMessage = this.mBaseHandler.obtainMessage(2, atomicBoolean);
            if (this.mAdnCache != null) {
                this.mAdnCache.requestLoadAllAdnLike(updateEfForIccType, this.mAdnCache.extensionEfForEf(updateEfForIccType), null, obtainMessage);
                waitForResult(atomicBoolean);
            } else {
                loge("Failure while trying to load from SIM due to uninitialised adncache");
            }
        }
        return this.mRecords;
    }

    public abstract int[] getAdnRecordsSize(int i);

    public int getAnrCount() {
        if (this.mAdnCache != null) {
            return this.mAdnCache.getAnrCount();
        }
        loge("mAdnCache is NULL when getAnrCount.");
        return 0;
    }

    public int getEmailCount() {
        if (this.mAdnCache != null) {
            return this.mAdnCache.getEmailCount();
        }
        loge("mAdnCache is NULL when getEmailCount.");
        return 0;
    }

    public int getSpareAnrCount() {
        if (this.mAdnCache != null) {
            return this.mAdnCache.getSpareAnrCount();
        }
        loge("mAdnCache is NULL when getSpareAnrCount.");
        return 0;
    }

    public int getSpareEmailCount() {
        if (this.mAdnCache != null) {
            return this.mAdnCache.getSpareEmailCount();
        }
        loge("mAdnCache is NULL when getSpareEmailCount.");
        return 0;
    }

    public abstract void logd(String str);

    public abstract void loge(String str);

    public void setIccCard(UiccCard uiccCard) {
        logd("Card update received: " + uiccCard);
        if (uiccCard == null) {
            logd("Card is null. Cleanup");
            cleanUp();
            return;
        }
        boolean z;
        Object obj = null;
        int numApplications = uiccCard.getNumApplications();
        this.mIs3gCard = false;
        int i = 0;
        boolean z2 = false;
        while (i < numApplications) {
            UiccCardApplication applicationIndex = uiccCard.getApplicationIndex(i);
            if (applicationIndex != null) {
                AppType type = applicationIndex.getType();
                if (type == AppType.APPTYPE_CSIM || type == AppType.APPTYPE_USIM || type == AppType.APPTYPE_ISIM) {
                    logd("Card is 3G");
                    this.mIs3gCard = true;
                }
                if (!z2) {
                    if (obj == null && type != AppType.APPTYPE_UNKNOWN) {
                        obj = applicationIndex;
                    }
                    if (this.mCurrentApp == applicationIndex) {
                        logd("Existing app found");
                        z = true;
                        if (this.mIs3gCard && r1) {
                            break;
                        }
                    }
                }
                z = z2;
                break;
            }
            z = z2;
            i++;
            z2 = z;
        }
        z = z2;
        if ((this.mCurrentApp == null || !r1) && obj != null) {
            logd("Setting currentApp: " + obj);
            this.mCurrentApp = obj;
            this.mAdnCache = this.mCurrentApp.getIccRecords().getAdnCache();
        }
    }

    public boolean updateAdnRecordsInEfByIndex(int i, String str, String str2, int i2, String str3) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.WRITE_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.WRITE_CONTACTS permission");
        }
        logd("updateAdnRecordsInEfByIndex: efid=" + i + " Index=" + i2 + " ==> " + "(" + str + "," + str2 + ")" + " pin2=" + str3);
        synchronized (this.mLock) {
            checkThread();
            this.mSuccess = false;
            AtomicBoolean atomicBoolean = new AtomicBoolean(false);
            Message obtainMessage = this.mBaseHandler.obtainMessage(3, atomicBoolean);
            AdnRecord adnRecord = new AdnRecord(str, str2);
            if (this.mAdnCache != null) {
                this.mAdnCache.updateAdnByIndex(i, adnRecord, i2, str3, obtainMessage);
                waitForResult(atomicBoolean);
            } else {
                loge("Failure while trying to update by index due to uninitialised adncache");
            }
        }
        return this.mSuccess;
    }

    public boolean updateAdnRecordsInEfBySearch(int i, String str, String str2, String str3, String str4, String str5) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.WRITE_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.WRITE_CONTACTS permission");
        }
        logd("updateAdnRecordsInEfBySearch: efid=" + i + " (" + str + "," + str2 + ")" + "==>" + " (" + str3 + "," + str4 + ")" + " pin2=" + str5);
        int updateEfForIccType = updateEfForIccType(i);
        synchronized (this.mLock) {
            checkThread();
            this.mSuccess = false;
            AtomicBoolean atomicBoolean = new AtomicBoolean(false);
            Message obtainMessage = this.mBaseHandler.obtainMessage(3, atomicBoolean);
            AdnRecord adnRecord = new AdnRecord(str, str2);
            AdnRecord adnRecord2 = new AdnRecord(str3, str4);
            if (this.mAdnCache != null) {
                this.mAdnCache.updateAdnBySearch(updateEfForIccType, adnRecord, adnRecord2, str5, obtainMessage);
                waitForResult(atomicBoolean);
            } else {
                loge("Failure while trying to update by search due to uninitialised adncache");
            }
        }
        return this.mSuccess;
    }

    public boolean updateAdnRecordsWithContentValuesInEfBySearch(int i, ContentValues contentValues, String str) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.WRITE_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.WRITE_CONTACTS permission");
        }
        String asString = contentValues.getAsString(IccProvider.STR_TAG);
        String asString2 = contentValues.getAsString(IccProvider.STR_NEW_TAG);
        String asString3 = contentValues.getAsString(IccProvider.STR_NUMBER);
        String asString4 = contentValues.getAsString(IccProvider.STR_NEW_NUMBER);
        String asString5 = contentValues.getAsString(IccProvider.STR_EMAILS);
        String asString6 = contentValues.getAsString(IccProvider.STR_NEW_EMAILS);
        String asString7 = contentValues.getAsString(IccProvider.STR_ANRS);
        String asString8 = contentValues.getAsString(IccProvider.STR_NEW_ANRS);
        String[] stringArray = TextUtils.isEmpty(asString5) ? null : getStringArray(asString5);
        String[] stringArray2 = TextUtils.isEmpty(asString6) ? null : getStringArray(asString6);
        String[] stringArray3 = TextUtils.isEmpty(asString7) ? null : getStringArray(asString7);
        String[] stringArray4 = TextUtils.isEmpty(asString8) ? null : getStringArray(asString8);
        int updateEfForIccType = updateEfForIccType(i);
        logd("updateAdnRecordsInEfBySearch: efid=" + updateEfForIccType + ", values = " + contentValues + ", pin2=" + str);
        synchronized (this.mLock) {
            checkThread();
            this.mSuccess = false;
            AtomicBoolean atomicBoolean = new AtomicBoolean(false);
            Message obtainMessage = this.mBaseHandler.obtainMessage(3, atomicBoolean);
            AdnRecord adnRecord = new AdnRecord(asString, asString3, stringArray, stringArray3);
            AdnRecord adnRecord2 = new AdnRecord(asString2, asString4, stringArray2, stringArray4);
            if (this.mAdnCache != null) {
                this.mAdnCache.updateAdnBySearch(updateEfForIccType, adnRecord, adnRecord2, str, obtainMessage);
                waitForResult(atomicBoolean);
            } else {
                loge("Failure while trying to update by search due to uninitialised adncache");
            }
        }
        return this.mSuccess;
    }

    /* Access modifiers changed, original: protected */
    public void waitForResult(AtomicBoolean atomicBoolean) {
        while (!atomicBoolean.get()) {
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                logd("interrupted while trying to update by search");
            }
        }
    }
}
