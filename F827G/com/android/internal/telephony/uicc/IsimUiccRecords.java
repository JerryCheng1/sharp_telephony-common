package com.android.internal.telephony.uicc;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.gsm.SimTlv;
import com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public final class IsimUiccRecords extends IccRecords implements IsimRecords {
    private static final boolean DBG = true;
    private static final boolean DUMP_RECORDS = true;
    private static final int EVENT_AKA_AUTHENTICATE_DONE = 90;
    private static final int EVENT_APP_READY = 1;
    public static final String INTENT_ISIM_REFRESH = "com.android.intent.isim_refresh";
    protected static final String LOG_TAG = "IsimUiccRecords";
    private static final int TAG_ISIM_VALUE = 128;
    private String auth_rsp;
    private String mIsimDomain;
    private String mIsimImpi;
    private String[] mIsimImpu;
    private String mIsimIst;
    private String[] mIsimPcscf;
    private final Object mLock;

    private class EfIsimDomainLoaded implements IccRecordLoaded {
        private EfIsimDomainLoaded() {
        }

        public String getEfName() {
            return "EF_ISIM_DOMAIN";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            IsimUiccRecords.this.mIsimDomain = IsimUiccRecords.isimTlvToString((byte[]) asyncResult.result);
            IsimUiccRecords.this.log("EF_DOMAIN=" + IsimUiccRecords.this.mIsimDomain);
        }
    }

    private class EfIsimImpiLoaded implements IccRecordLoaded {
        private EfIsimImpiLoaded() {
        }

        public String getEfName() {
            return "EF_ISIM_IMPI";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            IsimUiccRecords.this.mIsimImpi = IsimUiccRecords.isimTlvToString((byte[]) asyncResult.result);
            IsimUiccRecords.this.log("EF_IMPI=" + IsimUiccRecords.this.mIsimImpi);
        }
    }

    private class EfIsimImpuLoaded implements IccRecordLoaded {
        private EfIsimImpuLoaded() {
        }

        public String getEfName() {
            return "EF_ISIM_IMPU";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            ArrayList arrayList = (ArrayList) asyncResult.result;
            IsimUiccRecords.this.log("EF_IMPU record count: " + arrayList.size());
            IsimUiccRecords.this.mIsimImpu = new String[arrayList.size()];
            int i = 0;
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                String access$600 = IsimUiccRecords.isimTlvToString((byte[]) it.next());
                IsimUiccRecords.this.log("EF_IMPU[" + i + "]=" + access$600);
                IsimUiccRecords.this.mIsimImpu[i] = access$600;
                i++;
            }
        }
    }

    private class EfIsimIstLoaded implements IccRecordLoaded {
        private EfIsimIstLoaded() {
        }

        public String getEfName() {
            return "EF_ISIM_IST";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            IsimUiccRecords.this.mIsimIst = IccUtils.bytesToHexString((byte[]) asyncResult.result);
            IsimUiccRecords.this.log("EF_IST=" + IsimUiccRecords.this.mIsimIst);
        }
    }

    private class EfIsimPcscfLoaded implements IccRecordLoaded {
        private EfIsimPcscfLoaded() {
        }

        public String getEfName() {
            return "EF_ISIM_PCSCF";
        }

        public void onRecordLoaded(AsyncResult asyncResult) {
            ArrayList arrayList = (ArrayList) asyncResult.result;
            IsimUiccRecords.this.log("EF_PCSCF record count: " + arrayList.size());
            IsimUiccRecords.this.mIsimPcscf = new String[arrayList.size()];
            int i = 0;
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                String access$600 = IsimUiccRecords.isimTlvToString((byte[]) it.next());
                IsimUiccRecords.this.log("EF_PCSCF[" + i + "]=" + access$600);
                IsimUiccRecords.this.mIsimPcscf[i] = access$600;
                i++;
            }
        }
    }

    public IsimUiccRecords(UiccCardApplication uiccCardApplication, Context context, CommandsInterface commandsInterface) {
        super(uiccCardApplication, context, commandsInterface);
        this.mLock = new Object();
        this.mAdnCache = new AdnRecordCache(this.mFh);
        this.mRecordsRequested = false;
        this.mRecordsToLoad = 0;
        resetRecords();
        this.mParentApp.registerForReady(this, 1, null);
        log("IsimUiccRecords X ctor this=" + this);
    }

    private static String isimTlvToString(byte[] bArr) {
        SimTlv simTlv = new SimTlv(bArr, 0, bArr.length);
        while (simTlv.getTag() != 128) {
            if (!simTlv.nextObject()) {
                Rlog.e(LOG_TAG, "[ISIM] can't find TLV tag in ISIM record, returning null");
                return null;
            }
        }
        return new String(simTlv.getData(), Charset.forName("UTF-8"));
    }

    /* Access modifiers changed, original: protected */
    public void broadcastRefresh() {
        Intent intent = new Intent(INTENT_ISIM_REFRESH);
        log("send ISim REFRESH: com.android.intent.isim_refresh");
        this.mContext.sendBroadcast(intent);
    }

    public void dispose() {
        log("Disposing " + this);
        this.mParentApp.unregisterForReady(this);
        resetRecords();
        super.dispose();
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("IsimRecords: " + this);
        printWriter.println(" extends:");
        super.dump(fileDescriptor, printWriter, strArr);
        printWriter.println(" mIsimImpi=" + this.mIsimImpi);
        printWriter.println(" mIsimDomain=" + this.mIsimDomain);
        printWriter.println(" mIsimImpu[]=" + Arrays.toString(this.mIsimImpu));
        printWriter.println(" mIsimIst" + this.mIsimIst);
        printWriter.println(" mIsimPcscf" + this.mIsimPcscf);
        printWriter.flush();
    }

    /* Access modifiers changed, original: protected */
    public void fetchIsimRecords() {
        this.mRecordsRequested = true;
        this.mFh.loadEFTransparent(IccConstants.EF_IMPI, obtainMessage(100, new EfIsimImpiLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixedAll(IccConstants.EF_IMPU, obtainMessage(100, new EfIsimImpuLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_DOMAIN, obtainMessage(100, new EfIsimDomainLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(28423, obtainMessage(100, new EfIsimIstLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixedAll(IccConstants.EF_PCSCF, obtainMessage(100, new EfIsimPcscfLoaded()));
        this.mRecordsToLoad++;
        log("fetchIsimRecords " + this.mRecordsToLoad + " requested: " + this.mRecordsRequested);
    }

    public int getDisplayRule(String str) {
        return 0;
    }

    public String getIsimChallengeResponse(String str) {
        log("getIsimChallengeResponse-nonce:" + str);
        try {
            synchronized (this.mLock) {
                this.mCi.requestIsimAuthentication(str, obtainMessage(EVENT_AKA_AUTHENTICATE_DONE));
                try {
                    this.mLock.wait();
                } catch (InterruptedException e) {
                    log("interrupted while trying to request Isim Auth");
                }
            }
            log("getIsimChallengeResponse-auth_rsp" + this.auth_rsp);
            return this.auth_rsp;
        } catch (Exception e2) {
            log("Fail while trying to request Isim Auth");
            return null;
        }
    }

    public String getIsimDomain() {
        return this.mIsimDomain;
    }

    public String getIsimImpi() {
        return this.mIsimImpi;
    }

    public String[] getIsimImpu() {
        return this.mIsimImpu != null ? (String[]) this.mIsimImpu.clone() : null;
    }

    public String getIsimIst() {
        return this.mIsimIst;
    }

    public String[] getIsimPcscf() {
        return this.mIsimPcscf != null ? (String[]) this.mIsimPcscf.clone() : null;
    }

    public int getVoiceMessageCount() {
        return 0;
    }

    /* Access modifiers changed, original: protected */
    public void handleFileUpdate(int i) {
        switch (i) {
            case IccConstants.EF_IMPI /*28418*/:
                this.mFh.loadEFTransparent(IccConstants.EF_IMPI, obtainMessage(100, new EfIsimImpiLoaded()));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_DOMAIN /*28419*/:
                this.mFh.loadEFTransparent(IccConstants.EF_DOMAIN, obtainMessage(100, new EfIsimDomainLoaded()));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_IMPU /*28420*/:
                this.mFh.loadEFLinearFixedAll(IccConstants.EF_IMPU, obtainMessage(100, new EfIsimImpuLoaded()));
                this.mRecordsToLoad++;
                return;
            case 28423:
                this.mFh.loadEFTransparent(28423, obtainMessage(100, new EfIsimIstLoaded()));
                this.mRecordsToLoad++;
                return;
            case IccConstants.EF_PCSCF /*28425*/:
                this.mFh.loadEFLinearFixedAll(IccConstants.EF_PCSCF, obtainMessage(100, new EfIsimPcscfLoaded()));
                this.mRecordsToLoad++;
                break;
        }
        fetchIsimRecords();
    }

    public void handleMessage(Message message) {
        if (this.mDestroyed.get()) {
            Rlog.e(LOG_TAG, "Received message " + message + "[" + message.what + "] while being destroyed. Ignoring.");
            return;
        }
        loge("IsimUiccRecords: handleMessage " + message + "[" + message.what + "] ");
        try {
            switch (message.what) {
                case 1:
                    onReady();
                    return;
                case EVENT_AKA_AUTHENTICATE_DONE /*90*/:
                    AsyncResult asyncResult = (AsyncResult) message.obj;
                    log("EVENT_AKA_AUTHENTICATE_DONE");
                    if (asyncResult.exception != null) {
                        log("Exception ISIM AKA: " + asyncResult.exception);
                    } else {
                        try {
                            this.auth_rsp = (String) asyncResult.result;
                            log("ISIM AKA: auth_rsp = " + this.auth_rsp);
                        } catch (Exception e) {
                            log("Failed to parse ISIM AKA contents: " + e);
                        }
                    }
                    synchronized (this.mLock) {
                        this.mLock.notifyAll();
                    }
                    return;
                default:
                    super.handleMessage(message);
                    return;
            }
        } catch (RuntimeException e2) {
            Rlog.w(LOG_TAG, "Exception parsing SIM record", e2);
        }
        Rlog.w(LOG_TAG, "Exception parsing SIM record", e2);
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(LOG_TAG, "[ISIM] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str) {
        Rlog.e(LOG_TAG, "[ISIM] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void onAllRecordsLoaded() {
        log("record load complete");
        if (isAppStateReady()) {
            this.mRecordsLoadedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
        } else {
            log("onAllRecordsLoaded: AppState is not ready; not notifying the registrants");
        }
    }

    public void onReady() {
        fetchIsimRecords();
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
            fetchIsimRecords();
        }
    }

    /* Access modifiers changed, original: protected */
    public void resetRecords() {
        this.mIsimImpi = null;
        this.mIsimDomain = null;
        this.mIsimImpu = null;
        this.mIsimIst = null;
        this.mIsimPcscf = null;
        this.auth_rsp = null;
        this.mRecordsRequested = false;
    }

    public void setVoiceMailNumber(String str, String str2, Message message) {
    }

    public void setVoiceMessageWaiting(int i, int i2) {
    }

    public String toString() {
        return "IsimUiccRecords: " + super.toString() + " mIsimImpi=" + this.mIsimImpi + " mIsimDomain=" + this.mIsimDomain + " mIsimImpu=" + this.mIsimImpu + " mIsimIst=" + this.mIsimIst + " mIsimPcscf=" + this.mIsimPcscf;
    }
}
