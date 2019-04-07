package com.android.internal.telephony.uicc;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.TelBrand;
import java.util.ArrayList;

public class AdnRecordLoader extends Handler {
    static final int EVENT_ADN_LOAD_ALL_DONE = 3;
    static final int EVENT_ADN_LOAD_DONE = 1;
    static final int EVENT_EF_LINEAR_RECORD_SIZE_DONE = 4;
    static final int EVENT_EXT_RECORD_LOAD_DONE = 2;
    static final int EVENT_UPDATE_RECORD_DONE = 5;
    static final String LOG_TAG = "AdnRecordLoader";
    static final boolean VDBG = false;
    ArrayList<AdnRecord> mAdns;
    int mEf;
    int mExtensionEF;
    private IccFileHandler mFh;
    String mPath;
    int mPendingExtLoads;
    String mPin2;
    int mRecordNumber;
    Object mResult;
    private UiccCardApplication mUiccApplication;
    Message mUserResponse;

    AdnRecordLoader(IccFileHandler iccFileHandler) {
        super(Looper.getMainLooper());
        this.mUiccApplication = null;
        this.mFh = iccFileHandler;
    }

    AdnRecordLoader(IccFileHandler iccFileHandler, UiccCardApplication uiccCardApplication) {
        this(iccFileHandler);
        this.mUiccApplication = uiccCardApplication;
    }

    private String getEFPath(int i) {
        return i == 28474 ? "3F007F10" : null;
    }

    private boolean isPin2Verify(int i) {
        switch (i) {
            case IccConstants.EF_FDN /*28475*/:
                return true;
            default:
                return false;
        }
    }

    public void handleMessage(Message message) {
        try {
            AsyncResult asyncResult;
            byte[] bArr;
            switch (message.what) {
                case 1:
                    asyncResult = (AsyncResult) message.obj;
                    bArr = (byte[]) asyncResult.result;
                    if (asyncResult.exception == null) {
                        AdnRecord adnRecord = new AdnRecord(this.mEf, this.mRecordNumber, bArr);
                        this.mResult = adnRecord;
                        if (adnRecord.hasExtendedRecord()) {
                            this.mPendingExtLoads = 1;
                            if (this.mPath == null) {
                                this.mFh.loadEFLinearFixed(this.mExtensionEF, adnRecord.mExtRecord, obtainMessage(2, adnRecord));
                                break;
                            } else {
                                this.mFh.loadEFLinearFixed(this.mExtensionEF, this.mPath, adnRecord.mExtRecord, obtainMessage(2, adnRecord));
                                break;
                            }
                        }
                    }
                    throw new RuntimeException("load failed", asyncResult.exception);
                    break;
                case 2:
                    asyncResult = (AsyncResult) message.obj;
                    bArr = (byte[]) asyncResult.result;
                    AdnRecord adnRecord2 = (AdnRecord) asyncResult.userObj;
                    if (asyncResult.exception == null) {
                        Rlog.d(LOG_TAG, "ADN extension EF: 0x" + Integer.toHexString(this.mExtensionEF) + ":" + adnRecord2.mExtRecord + "\n" + IccUtils.bytesToHexString(bArr));
                        adnRecord2.appendExtRecord(bArr);
                        this.mPendingExtLoads--;
                        break;
                    }
                    throw new RuntimeException("load failed", asyncResult.exception);
                case 3:
                    asyncResult = (AsyncResult) message.obj;
                    ArrayList arrayList = (ArrayList) asyncResult.result;
                    if (asyncResult.exception == null) {
                        this.mAdns = new ArrayList(arrayList.size());
                        this.mResult = this.mAdns;
                        this.mPendingExtLoads = 0;
                        int size = arrayList.size();
                        for (int i = 0; i < size; i++) {
                            AdnRecord adnRecord3 = new AdnRecord(this.mEf, i + 1, (byte[]) arrayList.get(i));
                            this.mAdns.add(adnRecord3);
                            if (adnRecord3.hasExtendedRecord() && this.mExtensionEF != 0) {
                                this.mPendingExtLoads++;
                                if (this.mPath != null) {
                                    this.mFh.loadEFLinearFixed(this.mExtensionEF, this.mPath, adnRecord3.mExtRecord, obtainMessage(2, adnRecord3));
                                } else {
                                    this.mFh.loadEFLinearFixed(this.mExtensionEF, adnRecord3.mExtRecord, obtainMessage(2, adnRecord3));
                                }
                            }
                        }
                        break;
                    }
                    throw new RuntimeException("load failed", asyncResult.exception);
                case 4:
                    asyncResult = (AsyncResult) message.obj;
                    AdnRecord adnRecord4 = (AdnRecord) asyncResult.userObj;
                    if (asyncResult.exception == null) {
                        int[] iArr = (int[]) asyncResult.result;
                        if (iArr.length == 3 && this.mRecordNumber <= iArr[2]) {
                            byte[] buildAdnString = adnRecord4.buildAdnString(iArr[0]);
                            if (buildAdnString != null) {
                                if (this.mPath != null) {
                                    this.mFh.updateEFLinearFixed(this.mEf, this.mPath, this.mRecordNumber, buildAdnString, this.mPin2, obtainMessage(5));
                                } else {
                                    this.mFh.updateEFLinearFixed(this.mEf, this.mRecordNumber, buildAdnString, this.mPin2, obtainMessage(5));
                                }
                                this.mPendingExtLoads = 1;
                                break;
                            }
                            throw new RuntimeException("wrong ADN format", asyncResult.exception);
                        }
                        throw new RuntimeException("get wrong EF record size format", asyncResult.exception);
                    }
                    throw new RuntimeException("get EF record size failed", asyncResult.exception);
                case 5:
                    asyncResult = (AsyncResult) message.obj;
                    if (TelBrand.IS_SBM && isPin2Verify(this.mEf) && this.mUiccApplication != null) {
                        if (asyncResult.exception == null) {
                            this.mUiccApplication.clearPin2RetryCount();
                        } else if (asyncResult.exception instanceof CommandException) {
                            Error commandError = ((CommandException) asyncResult.exception).getCommandError();
                            if (commandError == Error.PASSWORD_INCORRECT || commandError == Error.SIM_PUK2) {
                                this.mUiccApplication.decrementPin2RetryCount();
                            }
                        }
                    }
                    if (asyncResult.exception == null) {
                        this.mPendingExtLoads = 0;
                        this.mResult = null;
                        break;
                    }
                    throw new RuntimeException("update EF adn record failed", asyncResult.exception);
            }
            if (this.mUserResponse != null && this.mPendingExtLoads == 0) {
                AsyncResult.forMessage(this.mUserResponse).result = this.mResult;
                this.mUserResponse.sendToTarget();
                this.mUserResponse = null;
            }
        } catch (RuntimeException e) {
            if (this.mUserResponse != null) {
                AsyncResult.forMessage(this.mUserResponse).exception = e;
                this.mUserResponse.sendToTarget();
                this.mUserResponse = null;
            }
        }
    }

    public void loadAllFromEF(int i, int i2, String str, Message message) {
        this.mEf = i;
        this.mExtensionEF = i2;
        this.mPath = str;
        this.mUserResponse = message;
        if (i == 28474) {
            this.mPath = getEFPath(i);
        }
        if (this.mPath != null) {
            this.mFh.loadEFLinearFixedAll(i, this.mPath, obtainMessage(3));
        } else {
            this.mFh.loadEFLinearFixedAll(i, obtainMessage(3));
        }
    }

    public void loadFromEF(int i, int i2, int i3, Message message) {
        this.mEf = i;
        this.mExtensionEF = i2;
        this.mRecordNumber = i3;
        this.mUserResponse = message;
        if (i == 28474) {
            this.mPath = getEFPath(i);
            this.mFh.loadEFLinearFixed(i, getEFPath(i), i3, obtainMessage(1));
            return;
        }
        this.mPath = null;
        this.mFh.loadEFLinearFixed(i, i3, obtainMessage(1));
    }

    public void updateEF(AdnRecord adnRecord, int i, int i2, int i3, String str, Message message) {
        String str2 = null;
        if (i == 28474) {
            str2 = getEFPath(i);
        }
        updateEF(adnRecord, i, i2, str2, i3, str, message);
    }

    public void updateEF(AdnRecord adnRecord, int i, int i2, String str, int i3, String str2, Message message) {
        this.mEf = i;
        this.mExtensionEF = i2;
        this.mPath = str;
        this.mRecordNumber = i3;
        this.mUserResponse = message;
        this.mPin2 = str2;
        if (str != null) {
            this.mFh.getEFLinearRecordSize(i, str, obtainMessage(4, adnRecord));
        } else {
            this.mFh.getEFLinearRecordSize(i, obtainMessage(4, adnRecord));
        }
    }
}
