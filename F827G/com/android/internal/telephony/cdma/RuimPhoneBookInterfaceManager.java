package com.android.internal.telephony.cdma;

import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.uicc.IccFileHandler;
import java.util.concurrent.atomic.AtomicBoolean;

public class RuimPhoneBookInterfaceManager extends IccPhoneBookInterfaceManager {
    static final String LOG_TAG = "RuimPhoneBookIM";

    public RuimPhoneBookInterfaceManager(CDMAPhone cDMAPhone) {
        super(cDMAPhone);
    }

    public void dispose() {
        super.dispose();
    }

    /* Access modifiers changed, original: protected */
    public void finalize() {
        try {
            super.finalize();
        } catch (Throwable th) {
            Rlog.e(LOG_TAG, "Error while finalizing:", th);
        }
        Rlog.d(LOG_TAG, "RuimPhoneBookInterfaceManager finalized");
    }

    public int[] getAdnRecordsSize(int i) {
        logd("getAdnRecordsSize: efid=" + i);
        synchronized (this.mLock) {
            checkThread();
            this.mRecordSize = new int[3];
            AtomicBoolean atomicBoolean = new AtomicBoolean(false);
            Message obtainMessage = this.mBaseHandler.obtainMessage(1, atomicBoolean);
            IccFileHandler iccFileHandler = this.mPhone.getIccFileHandler();
            if (iccFileHandler != null) {
                iccFileHandler.getEFLinearRecordSize(i, obtainMessage);
                waitForResult(atomicBoolean);
            }
        }
        return this.mRecordSize;
    }

    /* Access modifiers changed, original: protected */
    public void logd(String str) {
        Rlog.d(LOG_TAG, "[RuimPbInterfaceManager] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str) {
        Rlog.e(LOG_TAG, "[RuimPbInterfaceManager] " + str);
    }
}
