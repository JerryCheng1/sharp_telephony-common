package com.android.internal.telephony;

import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserManager;
import android.provider.Telephony.Carriers;
import android.telephony.Rlog;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.HexDump;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class IccSmsInterfaceManager {
    static final boolean DBG = true;
    private static final int DEFAULT_CBS_RETRY_COUNT = 6;
    private static final int DEFAULT_CBS_RETRY_DELAY = 500;
    private static final int EVENT_BROADCAST_REQUEST_RETRY = 10;
    protected static final int EVENT_GET_SMSC_ADDRESS_DONE = 5;
    private static final int EVENT_LOAD_DONE = 1;
    protected static final int EVENT_SET_BROADCAST_ACTIVATION_DONE = 3;
    protected static final int EVENT_SET_BROADCAST_CONFIG_DONE = 4;
    protected static final int EVENT_SET_SMSC_ADDRESS_DONE = 6;
    private static final int EVENT_UPDATE_DONE = 2;
    static final String LOG_TAG = "IccSmsInterfaceManager";
    private static final int SMS_CB_CODE_SCHEME_MAX = 255;
    private static final int SMS_CB_CODE_SCHEME_MIN = 0;
    private static int mCbsRetryCount = SystemProperties.getInt("persist.radio.cbs_retry_count", 6);
    private static int mCbsRetryDelay = SystemProperties.getInt("persist.radio.cbs_retry_delay", DEFAULT_CBS_RETRY_DELAY);
    protected final AppOpsManager mAppOps;
    private final Object mCbsRetryLock = new Object();
    private CdmaBroadcastRangeManager mCdmaBroadcastRangeManager = new CdmaBroadcastRangeManager();
    private CellBroadcastRangeManager mCellBroadcastRangeManager = new CellBroadcastRangeManager();
    protected final Context mContext;
    protected SMSDispatcher mDispatcher;
    private final Object mGetSmscLock = new Object();
    protected Handler mHandler = new Handler() {
        public void handleMessage(Message message) {
            boolean z = true;
            boolean z2 = false;
            AsyncResult asyncResult;
            IccSmsInterfaceManager iccSmsInterfaceManager;
            switch (message.what) {
                case 1:
                    asyncResult = (AsyncResult) message.obj;
                    synchronized (IccSmsInterfaceManager.this.mLock) {
                        if (asyncResult.exception == null) {
                            IccSmsInterfaceManager.this.mSms = IccSmsInterfaceManager.this.buildValidRawData((ArrayList) asyncResult.result);
                            IccSmsInterfaceManager.this.markMessagesAsRead((ArrayList) asyncResult.result);
                        } else {
                            if (Rlog.isLoggable("SMS", 3)) {
                                IccSmsInterfaceManager.this.log("Cannot load Sms records");
                            }
                            if (IccSmsInterfaceManager.this.mSms != null) {
                                IccSmsInterfaceManager.this.mSms.clear();
                            }
                        }
                        IccSmsInterfaceManager.this.mLock.notifyAll();
                    }
                    return;
                case 2:
                    asyncResult = (AsyncResult) message.obj;
                    synchronized (IccSmsInterfaceManager.this.mLock) {
                        iccSmsInterfaceManager = IccSmsInterfaceManager.this;
                        if (asyncResult.exception == null) {
                            z2 = true;
                        }
                        iccSmsInterfaceManager.mSuccess = z2;
                        IccSmsInterfaceManager.this.mLock.notifyAll();
                    }
                    return;
                case 3:
                case 4:
                    asyncResult = (AsyncResult) message.obj;
                    synchronized (IccSmsInterfaceManager.this.mLock) {
                        iccSmsInterfaceManager = IccSmsInterfaceManager.this;
                        if (asyncResult.exception != null) {
                            z = false;
                        }
                        iccSmsInterfaceManager.mSuccess = z;
                        if (TelBrand.IS_DCM) {
                            IccSmsInterfaceManager.this.mIsRetry = false;
                            if (!IccSmsInterfaceManager.this.mSuccess && ((CommandException) asyncResult.exception).getCommandError() == Error.CBS_REQUEST_FAIL_RETRY) {
                                IccSmsInterfaceManager.this.mIsRetry = true;
                            }
                        }
                        IccSmsInterfaceManager.this.mLock.notifyAll();
                    }
                    return;
                case 5:
                    asyncResult = (AsyncResult) message.obj;
                    synchronized (IccSmsInterfaceManager.this.mGetSmscLock) {
                        if (asyncResult.exception == null) {
                            IccSmsInterfaceManager.this.mSmscAddress = (String) asyncResult.result;
                        } else {
                            if (Rlog.isLoggable("SMS", 3)) {
                                IccSmsInterfaceManager.this.log("Load Smsc failed");
                            }
                            IccSmsInterfaceManager.this.mSmscAddress = null;
                        }
                        IccSmsInterfaceManager.this.mGetSmscLock.notifyAll();
                    }
                    return;
                case 6:
                    asyncResult = (AsyncResult) message.obj;
                    synchronized (IccSmsInterfaceManager.this.mSetSmscLock) {
                        IccSmsInterfaceManager.this.mSmscSuccess = asyncResult.exception == null;
                        IccSmsInterfaceManager.this.mSetSmscLock.notifyAll();
                    }
                    return;
                case 10:
                    if (TelBrand.IS_DCM) {
                        IccSmsInterfaceManager.this.log("Received EVENT_BROADCAST_REQUEST_RETRY msg.");
                        synchronized (IccSmsInterfaceManager.this.mCbsRetryLock) {
                            IccSmsInterfaceManager.this.mCbsRetryLock.notifyAll();
                        }
                        return;
                    }
                    return;
                default:
                    return;
            }
        }
    };
    private boolean mIsRetry = false;
    protected final Object mLock = new Object();
    protected PhoneBase mPhone;
    private boolean mRetryOut = false;
    private final Object mSetSmscLock = new Object();
    private List<SmsRawData> mSms;
    private String mSmscAddress = null;
    private boolean mSmscSuccess = false;
    protected boolean mSuccess;
    private final UserManager mUserManager;

    class CdmaBroadcastRangeManager extends IntRangeManager {
        private ArrayList<CdmaSmsBroadcastConfigInfo> mConfigList = new ArrayList();

        CdmaBroadcastRangeManager() {
        }

        /* Access modifiers changed, original: protected */
        public void addRange(int i, int i2, boolean z) {
            this.mConfigList.add(new CdmaSmsBroadcastConfigInfo(i, i2, 1, z));
        }

        /* Access modifiers changed, original: protected */
        public boolean finishUpdate() {
            if (this.mConfigList.isEmpty()) {
                return true;
            }
            return IccSmsInterfaceManager.this.setCdmaBroadcastConfig((CdmaSmsBroadcastConfigInfo[]) this.mConfigList.toArray(new CdmaSmsBroadcastConfigInfo[this.mConfigList.size()]));
        }

        /* Access modifiers changed, original: protected */
        public void startUpdate() {
            this.mConfigList.clear();
        }
    }

    class CellBroadcastRangeManager extends IntRangeManager {
        private ArrayList<SmsBroadcastConfigInfo> mConfigList = new ArrayList();

        CellBroadcastRangeManager() {
        }

        /* Access modifiers changed, original: protected */
        public void addRange(int i, int i2, boolean z) {
            this.mConfigList.add(new SmsBroadcastConfigInfo(i, i2, 0, 255, z));
        }

        /* Access modifiers changed, original: protected */
        public boolean finishUpdate() {
            if (this.mConfigList.isEmpty()) {
                return true;
            }
            SmsBroadcastConfigInfo[] smsBroadcastConfigInfoArr = (SmsBroadcastConfigInfo[]) this.mConfigList.toArray(new SmsBroadcastConfigInfo[this.mConfigList.size()]);
            return TelBrand.IS_DCM ? IccSmsInterfaceManager.this.setCellBroadcastConfigEx(smsBroadcastConfigInfoArr) : IccSmsInterfaceManager.this.setCellBroadcastConfig(smsBroadcastConfigInfoArr);
        }

        /* Access modifiers changed, original: protected */
        public void startUpdate() {
            this.mConfigList.clear();
        }
    }

    protected IccSmsInterfaceManager(PhoneBase phoneBase) {
        this.mPhone = phoneBase;
        this.mContext = phoneBase.getContext();
        this.mAppOps = (AppOpsManager) this.mContext.getSystemService("appops");
        this.mUserManager = (UserManager) this.mContext.getSystemService(Carriers.USER);
        this.mDispatcher = new ImsSMSDispatcher(phoneBase, phoneBase.mSmsStorageMonitor, phoneBase.mSmsUsageMonitor);
    }

    private void enforceCarrierPrivilege() {
        UiccController instance = UiccController.getInstance();
        if (instance == null || instance.getUiccCard(this.mPhone.getPhoneId()) == null) {
            throw new SecurityException("No Carrier Privilege: No UICC");
        } else if (instance.getUiccCard(this.mPhone.getPhoneId()).getCarrierPrivilegeStatusForCurrentTransaction(this.mContext.getPackageManager()) != 1) {
            throw new SecurityException("No Carrier Privilege.");
        }
    }

    private String filterDestAddress(String str) {
        String filterDestAddr = SmsNumberUtils.filterDestAddr(this.mPhone, str);
        return filterDestAddr != null ? filterDestAddr : str;
    }

    /* JADX WARNING: Removed duplicated region for block: B:31:0x0057  */
    private boolean isFailedOrDraft(android.content.ContentResolver r13, android.net.Uri r14) {
        /*
        r12 = this;
        r8 = 1;
        r6 = 0;
        r7 = 0;
        r10 = android.os.Binder.clearCallingIdentity();
        r0 = 1;
        r2 = new java.lang.String[r0];	 Catch:{ SQLiteException -> 0x0041, all -> 0x0053 }
        r0 = 0;
        r1 = "type";
        r2[r0] = r1;	 Catch:{ SQLiteException -> 0x0041, all -> 0x0053 }
        r3 = 0;
        r4 = 0;
        r5 = 0;
        r0 = r13;
        r1 = r14;
        r1 = r0.query(r1, r2, r3, r4, r5);	 Catch:{ SQLiteException -> 0x0041, all -> 0x0053 }
        if (r1 == 0) goto L_0x0037;
    L_0x001a:
        r0 = r1.moveToFirst();	 Catch:{ SQLiteException -> 0x0060 }
        if (r0 == 0) goto L_0x0037;
    L_0x0020:
        r0 = 0;
        r0 = r1.getInt(r0);	 Catch:{ SQLiteException -> 0x0060 }
        r2 = 3;
        if (r0 == r2) goto L_0x002b;
    L_0x0028:
        r2 = 5;
        if (r0 != r2) goto L_0x0035;
    L_0x002b:
        r0 = r8;
    L_0x002c:
        if (r1 == 0) goto L_0x0031;
    L_0x002e:
        r1.close();
    L_0x0031:
        android.os.Binder.restoreCallingIdentity(r10);
    L_0x0034:
        return r0;
    L_0x0035:
        r0 = r6;
        goto L_0x002c;
    L_0x0037:
        if (r1 == 0) goto L_0x003c;
    L_0x0039:
        r1.close();
    L_0x003c:
        android.os.Binder.restoreCallingIdentity(r10);
    L_0x003f:
        r0 = r6;
        goto L_0x0034;
    L_0x0041:
        r0 = move-exception;
        r1 = r7;
    L_0x0043:
        r2 = "IccSmsInterfaceManager";
        r3 = "[IccSmsInterfaceManager]isFailedOrDraft: query message type failed";
        android.util.Log.e(r2, r3, r0);	 Catch:{ all -> 0x005e }
        if (r1 == 0) goto L_0x004f;
    L_0x004c:
        r1.close();
    L_0x004f:
        android.os.Binder.restoreCallingIdentity(r10);
        goto L_0x003f;
    L_0x0053:
        r0 = move-exception;
        r1 = r7;
    L_0x0055:
        if (r1 == 0) goto L_0x005a;
    L_0x0057:
        r1.close();
    L_0x005a:
        android.os.Binder.restoreCallingIdentity(r10);
        throw r0;
    L_0x005e:
        r0 = move-exception;
        goto L_0x0055;
    L_0x0060:
        r0 = move-exception;
        goto L_0x0043;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.IccSmsInterfaceManager.isFailedOrDraft(android.content.ContentResolver, android.net.Uri):boolean");
    }

    /* JADX WARNING: Removed duplicated region for block: B:25:0x005f  */
    private java.lang.String[] loadTextAndAddress(android.content.ContentResolver r13, android.net.Uri r14) {
        /*
        r12 = this;
        r11 = 2;
        r10 = 1;
        r7 = 0;
        r6 = 0;
        r8 = android.os.Binder.clearCallingIdentity();
        r0 = 2;
        r2 = new java.lang.String[r0];	 Catch:{ SQLiteException -> 0x0049, all -> 0x005b }
        r0 = 0;
        r1 = "body";
        r2[r0] = r1;	 Catch:{ SQLiteException -> 0x0049, all -> 0x005b }
        r0 = 1;
        r1 = "address";
        r2[r0] = r1;	 Catch:{ SQLiteException -> 0x0049, all -> 0x005b }
        r3 = 0;
        r4 = 0;
        r5 = 0;
        r0 = r13;
        r1 = r14;
        r1 = r0.query(r1, r2, r3, r4, r5);	 Catch:{ SQLiteException -> 0x0049, all -> 0x005b }
        if (r1 == 0) goto L_0x003f;
    L_0x0020:
        r0 = r1.moveToFirst();	 Catch:{ SQLiteException -> 0x0068 }
        if (r0 == 0) goto L_0x003f;
    L_0x0026:
        r0 = 0;
        r2 = r1.getString(r0);	 Catch:{ SQLiteException -> 0x0068 }
        r0 = 1;
        r3 = r1.getString(r0);	 Catch:{ SQLiteException -> 0x0068 }
        if (r1 == 0) goto L_0x0035;
    L_0x0032:
        r1.close();
    L_0x0035:
        android.os.Binder.restoreCallingIdentity(r8);
        r0 = new java.lang.String[r11];
        r0[r7] = r2;
        r0[r10] = r3;
    L_0x003e:
        return r0;
    L_0x003f:
        if (r1 == 0) goto L_0x0044;
    L_0x0041:
        r1.close();
    L_0x0044:
        android.os.Binder.restoreCallingIdentity(r8);
    L_0x0047:
        r0 = r6;
        goto L_0x003e;
    L_0x0049:
        r0 = move-exception;
        r1 = r6;
    L_0x004b:
        r2 = "IccSmsInterfaceManager";
        r3 = "[IccSmsInterfaceManager]loadText: query message text failed";
        android.util.Log.e(r2, r3, r0);	 Catch:{ all -> 0x0066 }
        if (r1 == 0) goto L_0x0057;
    L_0x0054:
        r1.close();
    L_0x0057:
        android.os.Binder.restoreCallingIdentity(r8);
        goto L_0x0047;
    L_0x005b:
        r0 = move-exception;
        r1 = r6;
    L_0x005d:
        if (r1 == 0) goto L_0x0062;
    L_0x005f:
        r1.close();
    L_0x0062:
        android.os.Binder.restoreCallingIdentity(r8);
        throw r0;
    L_0x0066:
        r0 = move-exception;
        goto L_0x005d;
    L_0x0068:
        r0 = move-exception;
        goto L_0x004b;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.IccSmsInterfaceManager.loadTextAndAddress(android.content.ContentResolver, android.net.Uri):java.lang.String[]");
    }

    private void returnUnspecifiedFailure(PendingIntent pendingIntent) {
        if (pendingIntent != null) {
            try {
                pendingIntent.send(1);
            } catch (CanceledException e) {
            }
        }
    }

    private void returnUnspecifiedFailure(List<PendingIntent> list) {
        if (list != null) {
            for (PendingIntent returnUnspecifiedFailure : list) {
                returnUnspecifiedFailure(returnUnspecifiedFailure);
            }
        }
    }

    private boolean setCdmaBroadcastActivation(boolean z) {
        log("Calling setCdmaBroadcastActivation(" + z + ")");
        synchronized (this.mLock) {
            Message obtainMessage = this.mHandler.obtainMessage(3);
            this.mSuccess = false;
            this.mPhone.mCi.setCdmaBroadcastActivation(z, obtainMessage);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cdma broadcast activation");
            }
        }
        return this.mSuccess;
    }

    private boolean setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] cdmaSmsBroadcastConfigInfoArr) {
        log("Calling setCdmaBroadcastConfig with " + cdmaSmsBroadcastConfigInfoArr.length + " configurations");
        synchronized (this.mLock) {
            Message obtainMessage = this.mHandler.obtainMessage(4);
            this.mSuccess = false;
            this.mPhone.mCi.setCdmaBroadcastConfig(cdmaSmsBroadcastConfigInfoArr, obtainMessage);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cdma broadcast config");
            }
        }
        return this.mSuccess;
    }

    private boolean setCellBroadcastActivation(boolean z) {
        log("Calling setCellBroadcastActivation(" + z + ')');
        synchronized (this.mLock) {
            Message obtainMessage = this.mHandler.obtainMessage(3);
            this.mSuccess = false;
            this.mPhone.mCi.setGsmBroadcastActivation(z, obtainMessage);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cell broadcast activation");
            }
        }
        return this.mSuccess;
    }

    private boolean setCellBroadcastActivationEx(boolean z) {
        if (!TelBrand.IS_DCM) {
            return false;
        }
        boolean cellBroadcastActivation;
        log("Calling setCellBroadcastActivationEx(" + z + ")");
        synchronized (this.mCbsRetryLock) {
            int i = 0;
            boolean z2;
            do {
                cellBroadcastActivation = setCellBroadcastActivation(z);
                z2 = this.mIsRetry;
                if (z2) {
                    if (this.mRetryOut || i >= mCbsRetryCount) {
                        this.mRetryOut = true;
                        z2 = false;
                        continue;
                    } else {
                        this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(10), (long) mCbsRetryDelay);
                        i++;
                        log("Scheduling next attempt on setCellBroadcastActivation for " + mCbsRetryDelay + "ms, Retry count = " + i);
                        try {
                            this.mCbsRetryLock.wait();
                            continue;
                        } catch (InterruptedException e) {
                            log("interrupted while trying to set cell broadcast config Ex");
                            continue;
                        }
                    }
                }
            } while (z2);
        }
        return cellBroadcastActivation;
    }

    private boolean setCellBroadcastConfig(SmsBroadcastConfigInfo[] smsBroadcastConfigInfoArr) {
        log("Calling setGsmBroadcastConfig with " + smsBroadcastConfigInfoArr.length + " configurations");
        synchronized (this.mLock) {
            Message obtainMessage = this.mHandler.obtainMessage(4);
            this.mSuccess = false;
            this.mPhone.mCi.setGsmBroadcastConfig(smsBroadcastConfigInfoArr, obtainMessage);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cell broadcast config");
            }
        }
        return this.mSuccess;
    }

    private boolean setCellBroadcastConfigEx(SmsBroadcastConfigInfo[] smsBroadcastConfigInfoArr) {
        if (!TelBrand.IS_DCM) {
            return false;
        }
        boolean cellBroadcastConfig;
        log("Calling setGsmBroadcastConfigEx with " + smsBroadcastConfigInfoArr.length + " configurations");
        synchronized (this.mCbsRetryLock) {
            int i = 0;
            boolean z;
            do {
                cellBroadcastConfig = setCellBroadcastConfig(smsBroadcastConfigInfoArr);
                z = this.mIsRetry;
                if (z) {
                    if (this.mRetryOut || i >= mCbsRetryCount) {
                        this.mRetryOut = true;
                        z = false;
                        continue;
                    } else {
                        this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(10), (long) mCbsRetryDelay);
                        i++;
                        log("Scheduling next attempt on setGsmBroadcastConfig for " + mCbsRetryDelay + "ms, Retry count = " + i);
                        try {
                            this.mCbsRetryLock.wait();
                            continue;
                        } catch (InterruptedException e) {
                            log("interrupted while trying to set cell broadcast config Ex");
                            continue;
                        }
                    }
                }
            } while (z);
        }
        return cellBroadcastConfig;
    }

    /* Access modifiers changed, original: protected */
    public ArrayList<SmsRawData> buildValidRawData(ArrayList<byte[]> arrayList) {
        int size = arrayList.size();
        ArrayList arrayList2 = new ArrayList(size);
        for (int i = 0; i < size; i++) {
            if (((byte[]) arrayList.get(i))[0] == (byte) 0) {
                arrayList2.add(null);
            } else {
                arrayList2.add(new SmsRawData((byte[]) arrayList.get(i)));
            }
        }
        return arrayList2;
    }

    public boolean copyMessageToIccEf(String str, int i, byte[] bArr, byte[] bArr2) {
        log("copyMessageToIccEf: status=" + i + " ==> " + "pdu=(" + Arrays.toString(bArr) + "), smsc=(" + Arrays.toString(bArr2) + ")");
        enforceReceiveAndSend("Copying message to Icc");
        if (this.mAppOps.noteOp(22, Binder.getCallingUid(), str) != 0) {
            return false;
        }
        synchronized (this.mLock) {
            this.mSuccess = false;
            Message obtainMessage = this.mHandler.obtainMessage(2);
            if (1 == this.mPhone.getPhoneType()) {
                this.mPhone.mCi.writeSmsToSim(i, IccUtils.bytesToHexString(bArr2), IccUtils.bytesToHexString(bArr), obtainMessage);
            } else {
                this.mPhone.mCi.writeSmsToRuim(i, IccUtils.bytesToHexString(bArr), obtainMessage);
            }
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to update by index");
            }
        }
        return this.mSuccess;
    }

    public boolean disableCdmaBroadcastRange(int i, int i2) {
        boolean z = false;
        synchronized (this) {
            log("disableCdmaBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Disabling cell broadcast SMS");
            String nameForUid = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (this.mCdmaBroadcastRangeManager.disableRange(i, i2, nameForUid)) {
                log("Removed cdma broadcast subscription for MID range " + i + " to " + i2 + " from client " + nameForUid);
                if (!this.mCdmaBroadcastRangeManager.isEmpty()) {
                    z = true;
                }
                setCdmaBroadcastActivation(z);
                z = true;
            } else {
                log("Failed to remove cdma broadcast subscription for MID range " + i + " to " + i2 + " from client " + nameForUid);
            }
        }
        return z;
    }

    public boolean disableCellBroadcast(int i, int i2) {
        return disableCellBroadcastRange(i, i, i2);
    }

    public boolean disableCellBroadcastRange(int i, int i2, int i3) {
        if (i3 == 0) {
            return disableGsmBroadcastRange(i, i2);
        }
        if (i3 == 1) {
            return disableCdmaBroadcastRange(i, i2);
        }
        throw new IllegalArgumentException("Not a supportted RAN Type");
    }

    public boolean disableGsmBroadcastRange(int i, int i2) {
        boolean z = false;
        synchronized (this) {
            log("disableGsmBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Disabling cell broadcast SMS");
            String nameForUid = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (this.mCellBroadcastRangeManager.disableRange(i, i2, nameForUid)) {
                log("Removed GSM cell broadcast subscription for MID range " + i + " to " + i2 + " from client " + nameForUid);
                if (TelBrand.IS_DCM) {
                    if (!this.mCellBroadcastRangeManager.isEmpty()) {
                        z = true;
                    }
                    setCellBroadcastActivationEx(z);
                } else {
                    if (!this.mCellBroadcastRangeManager.isEmpty()) {
                        z = true;
                    }
                    setCellBroadcastActivation(z);
                }
                z = true;
            } else {
                log("Failed to remove GSM cell broadcast subscription for MID range " + i + " to " + i2 + " from client " + nameForUid);
            }
        }
        return z;
    }

    public boolean enableCdmaBroadcastRange(int i, int i2) {
        boolean z = false;
        synchronized (this) {
            log("enableCdmaBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Enabling cdma broadcast SMS");
            String nameForUid = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (this.mCdmaBroadcastRangeManager.enableRange(i, i2, nameForUid)) {
                log("Added cdma broadcast subscription for MID range " + i + " to " + i2 + " from client " + nameForUid);
                if (!this.mCdmaBroadcastRangeManager.isEmpty()) {
                    z = true;
                }
                setCdmaBroadcastActivation(z);
                z = true;
            } else {
                log("Failed to add cdma broadcast subscription for MID range " + i + " to " + i2 + " from client " + nameForUid);
            }
        }
        return z;
    }

    public boolean enableCellBroadcast(int i, int i2) {
        return enableCellBroadcastRange(i, i, i2);
    }

    public boolean enableCellBroadcastRange(int i, int i2, int i3) {
        if (i3 == 0) {
            return enableGsmBroadcastRange(i, i2);
        }
        if (i3 == 1) {
            return enableCdmaBroadcastRange(i, i2);
        }
        throw new IllegalArgumentException("Not a supportted RAN Type");
    }

    public boolean enableGsmBroadcastRange(int i, int i2) {
        boolean z = false;
        synchronized (this) {
            log("enableGsmBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Enabling cell broadcast SMS");
            String nameForUid = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (this.mCellBroadcastRangeManager.enableRange(i, i2, nameForUid)) {
                log("Added GSM cell broadcast subscription for MID range " + i + " to " + i2 + " from client " + nameForUid);
                if (TelBrand.IS_DCM) {
                    if (!this.mCellBroadcastRangeManager.isEmpty()) {
                        z = true;
                    }
                    setCellBroadcastActivationEx(z);
                } else {
                    if (!this.mCellBroadcastRangeManager.isEmpty()) {
                        z = true;
                    }
                    setCellBroadcastActivation(z);
                }
                z = true;
            } else {
                log("Failed to add GSM cell broadcast subscription for MID range " + i + " to " + i2 + " from client " + nameForUid);
            }
        }
        return z;
    }

    /* Access modifiers changed, original: protected */
    public void enforceReceiveAndSend(String str) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.RECEIVE_SMS", str);
        this.mContext.enforceCallingOrSelfPermission("android.permission.SEND_SMS", str);
    }

    public List<SmsRawData> getAllMessagesFromIccEf(String str) {
        log("getAllMessagesFromEF");
        this.mContext.enforceCallingOrSelfPermission("android.permission.RECEIVE_SMS", "Reading messages from Icc");
        if (this.mAppOps.noteOp(21, Binder.getCallingUid(), str) != 0) {
            return new ArrayList();
        }
        synchronized (this.mLock) {
            IccFileHandler iccFileHandler = this.mPhone.getIccFileHandler();
            if (iccFileHandler == null) {
                Rlog.e(LOG_TAG, "Cannot load Sms records. No icc card?");
                if (this.mSms != null) {
                    this.mSms.clear();
                    List list = this.mSms;
                    return list;
                }
            }
            iccFileHandler.loadEFLinearFixedAll(IccConstants.EF_SMS, this.mHandler.obtainMessage(1));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to load from the Icc");
            }
            return this.mSms;
        }
    }

    public String getImsSmsFormat() {
        return this.mDispatcher.getImsSmsFormat();
    }

    public int getPremiumSmsPermission(String str) {
        return this.mDispatcher.getPremiumSmsPermission(str);
    }

    public int getSmsCapacityOnIcc() {
        int i = -1;
        IccRecords iccRecords = this.mPhone.getIccRecords();
        if (iccRecords != null) {
            i = iccRecords.getSmsCapacityOnIcc();
        } else {
            log("getSmsCapacityOnIcc - aborting, no icc card present.");
        }
        log("getSmsCapacityOnIcc().numberOnIcc = " + i);
        return i;
    }

    public String getSmscAddressFromIcc() {
        String str;
        synchronized (this.mGetSmscLock) {
            this.mPhone.getSmscAddress(this.mHandler.obtainMessage(5));
            try {
                this.mGetSmscLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to get SMSC address");
            }
            str = this.mSmscAddress;
        }
        return str;
    }

    public void injectSmsPdu(byte[] bArr, String str, PendingIntent pendingIntent) {
        enforceCarrierPrivilege();
        if (Rlog.isLoggable("SMS", 2)) {
            log("pdu: " + bArr + "\n format=" + str + "\n receivedIntent=" + pendingIntent);
        }
        this.mDispatcher.injectSmsPdu(bArr, str, pendingIntent);
    }

    public boolean isImsSmsSupported() {
        return this.mDispatcher.isIms();
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Log.d(LOG_TAG, "[IccSmsInterfaceManager] " + str);
    }

    /* Access modifiers changed, original: protected */
    public byte[] makeSmsRecordData(int i, byte[] bArr) {
        byte[] bArr2 = 1 == this.mPhone.getPhoneType() ? new byte[176] : new byte[255];
        bArr2[0] = (byte) (i & 7);
        System.arraycopy(bArr, 0, bArr2, 1, bArr.length);
        int length = bArr.length;
        while (true) {
            length++;
            if (length >= bArr2.length) {
                return bArr2;
            }
            bArr2[length] = (byte) -1;
        }
    }

    /* Access modifiers changed, original: protected */
    public void markMessagesAsRead(ArrayList<byte[]> arrayList) {
        if (arrayList != null) {
            IccFileHandler iccFileHandler = this.mPhone.getIccFileHandler();
            if (iccFileHandler != null) {
                int size = arrayList.size();
                for (int i = 0; i < size; i++) {
                    byte[] bArr = (byte[]) arrayList.get(i);
                    if (bArr[0] == (byte) 3) {
                        int length = bArr.length;
                        byte[] bArr2 = new byte[(length - 1)];
                        System.arraycopy(bArr, 1, bArr2, 0, length - 1);
                        iccFileHandler.updateEFLinearFixed(IccConstants.EF_SMS, i + 1, makeSmsRecordData(1, bArr2), null, null);
                        if (Rlog.isLoggable("SMS", 3)) {
                            log("SMS " + (i + 1) + " marked as read");
                        }
                    }
                }
            } else if (Rlog.isLoggable("SMS", 3)) {
                log("markMessagesAsRead - aborting, no icc card present.");
            }
        }
    }

    public void sendData(String str, String str2, String str3, int i, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendData: destAddr=" + str2 + " scAddr=" + str3 + " destPort=" + i + " data='" + HexDump.toHexString(bArr) + "' sentIntent=" + pendingIntent + " deliveryIntent=" + pendingIntent2);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), str) == 0) {
            this.mDispatcher.sendData(filterDestAddress(str2), str3, i, 0, bArr, pendingIntent, pendingIntent2);
        }
    }

    public void sendDataWithOrigPort(String str, String str2, String str3, int i, int i2, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendDataWithOrigPort: destAddr=" + str2 + " scAddr=" + str3 + " destPort=" + i + "origPort=" + i2 + " data='" + HexDump.toHexString(bArr) + "' sentIntent=" + pendingIntent + " deliveryIntent=" + pendingIntent2);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), str) == 0) {
            this.mDispatcher.sendData(filterDestAddress(str2), str3, i, i2, bArr, pendingIntent, pendingIntent2);
        }
    }

    public void sendMultipartText(String str, String str2, String str3, List<String> list, List<PendingIntent> list2, List<PendingIntent> list3) {
        int i;
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            i = 0;
            Iterator it = list.iterator();
            while (true) {
                int i2 = i;
                if (!it.hasNext()) {
                    break;
                }
                log("sendMultipartText: destAddr=" + str2 + ", srAddr=" + str3 + ", part[" + i2 + "]=" + ((String) it.next()));
                i = i2 + 1;
            }
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), str) == 0) {
            String filterDestAddress = filterDestAddress(str2);
            if (list.size() <= 1 || list.size() >= 10 || SmsMessage.hasEmsSupport()) {
                this.mDispatcher.sendMultipartText(filterDestAddress, str3, (ArrayList) list, (ArrayList) list2, (ArrayList) list3, null, str, -1, false, -1);
                return;
            }
            i = 0;
            while (true) {
                int i3 = i;
                if (i3 < list.size()) {
                    String str4 = (String) list.get(i3);
                    String concat = SmsMessage.shouldAppendPageNumberAsPrefix() ? String.valueOf(i3 + 1) + '/' + list.size() + ' ' + str4 : str4.concat(' ' + String.valueOf(i3 + 1) + '/' + list.size());
                    PendingIntent pendingIntent = null;
                    if (list2 != null && list2.size() > i3) {
                        pendingIntent = (PendingIntent) list2.get(i3);
                    }
                    PendingIntent pendingIntent2 = null;
                    if (list3 != null && list3.size() > i3) {
                        pendingIntent2 = (PendingIntent) list3.get(i3);
                    }
                    this.mDispatcher.sendText(filterDestAddress, str3, concat, pendingIntent, pendingIntent2, null, str, -1, false, -1);
                    i = i3 + 1;
                } else {
                    return;
                }
            }
        }
    }

    public void sendMultipartTextWithOptions(String str, String str2, String str3, List<String> list, List<PendingIntent> list2, List<PendingIntent> list3, int i, boolean z, int i2) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            int i3 = 0;
            Iterator it = list.iterator();
            while (true) {
                int i4 = i3;
                if (!it.hasNext()) {
                    break;
                }
                log("sendMultipartTextWithOptions: destAddr=" + str2 + ", srAddr=" + str3 + ", part[" + i4 + "]=" + ((String) it.next()));
                i3 = i4 + 1;
            }
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), str) == 0) {
            this.mDispatcher.sendMultipartText(str2, str3, (ArrayList) list, (ArrayList) list2, (ArrayList) list3, null, str, i, z, i2);
        }
    }

    public void sendStoredMultipartText(String str, Uri uri, String str2, List<PendingIntent> list, List<PendingIntent> list2) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), str) == 0) {
            ContentResolver contentResolver = this.mPhone.getContext().getContentResolver();
            if (isFailedOrDraft(contentResolver, uri)) {
                String[] loadTextAndAddress = loadTextAndAddress(contentResolver, uri);
                if (loadTextAndAddress == null) {
                    Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredMultipartText: can not load text");
                    returnUnspecifiedFailure((List) list);
                    return;
                }
                ArrayList divideMessage = SmsManager.getDefault().divideMessage(loadTextAndAddress[0]);
                if (divideMessage == null || divideMessage.size() < 1) {
                    Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredMultipartText: can not divide text");
                    returnUnspecifiedFailure((List) list);
                    return;
                }
                loadTextAndAddress[1] = filterDestAddress(loadTextAndAddress[1]);
                if (divideMessage.size() <= 1 || divideMessage.size() >= 10 || SmsMessage.hasEmsSupport()) {
                    this.mDispatcher.sendMultipartText(loadTextAndAddress[1], str2, divideMessage, (ArrayList) list, (ArrayList) list2, uri, str, -1, false, -1);
                    return;
                }
                int i = 0;
                while (true) {
                    int i2 = i;
                    if (i2 < divideMessage.size()) {
                        String str3 = (String) divideMessage.get(i2);
                        String concat = SmsMessage.shouldAppendPageNumberAsPrefix() ? String.valueOf(i2 + 1) + '/' + divideMessage.size() + ' ' + str3 : str3.concat(' ' + String.valueOf(i2 + 1) + '/' + divideMessage.size());
                        PendingIntent pendingIntent = null;
                        if (list != null && list.size() > i2) {
                            pendingIntent = (PendingIntent) list.get(i2);
                        }
                        PendingIntent pendingIntent2 = null;
                        if (list2 != null && list2.size() > i2) {
                            pendingIntent2 = (PendingIntent) list2.get(i2);
                        }
                        this.mDispatcher.sendText(loadTextAndAddress[1], str2, concat, pendingIntent, pendingIntent2, uri, str, -1, false, -1);
                        i = i2 + 1;
                    } else {
                        return;
                    }
                }
            }
            Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredMultipartText: not FAILED or DRAFT message");
            returnUnspecifiedFailure((List) list);
        }
    }

    public void sendStoredText(String str, Uri uri, String str2, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendStoredText: scAddr=" + str2 + " messageUri=" + uri + " sentIntent=" + pendingIntent + " deliveryIntent=" + pendingIntent2);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), str) == 0) {
            ContentResolver contentResolver = this.mPhone.getContext().getContentResolver();
            if (isFailedOrDraft(contentResolver, uri)) {
                String[] loadTextAndAddress = loadTextAndAddress(contentResolver, uri);
                if (loadTextAndAddress == null) {
                    Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredText: can not load text");
                    returnUnspecifiedFailure(pendingIntent);
                    return;
                }
                loadTextAndAddress[1] = filterDestAddress(loadTextAndAddress[1]);
                this.mDispatcher.sendText(loadTextAndAddress[1], str2, loadTextAndAddress[0], pendingIntent, pendingIntent2, uri, str, -1, false, -1);
                return;
            }
            Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredText: not FAILED or DRAFT message");
            returnUnspecifiedFailure(pendingIntent);
        }
    }

    public void sendText(String str, String str2, String str3, String str4, PendingIntent pendingIntent, PendingIntent pendingIntent2) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendText: destAddr=" + str2 + " scAddr=" + str3 + " text='" + str4 + "' sentIntent=" + pendingIntent + " deliveryIntent=" + pendingIntent2);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), str) == 0) {
            this.mDispatcher.sendText(filterDestAddress(str2), str3, str4, pendingIntent, pendingIntent2, null, str, -1, false, -1);
        }
    }

    public void sendTextWithOptions(String str, String str2, String str3, String str4, PendingIntent pendingIntent, PendingIntent pendingIntent2, int i, boolean z, int i2) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendText: destAddr=" + str2 + " scAddr=" + str3 + " text='" + str4 + "' sentIntent=" + pendingIntent + " deliveryIntent=" + pendingIntent2 + "validityPeriod" + i2);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), str) == 0) {
            this.mDispatcher.sendText(str2, str3, str4, pendingIntent, pendingIntent2, null, str, i, z, i2);
        }
    }

    public void setPremiumSmsPermission(String str, int i) {
        this.mDispatcher.setPremiumSmsPermission(str, i);
    }

    public boolean setSmscAddressToIcc(String str) {
        synchronized (this.mSetSmscLock) {
            Message obtainMessage = this.mHandler.obtainMessage(6);
            this.mSmscSuccess = false;
            this.mPhone.setSmscAddress(str, obtainMessage);
            try {
                this.mSetSmscLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set SMSC address");
            }
        }
        return this.mSmscSuccess;
    }

    public boolean updateMessageOnIccEf(String str, int i, int i2, byte[] bArr) {
        log("updateMessageOnIccEf: index=" + i + " status=" + i2 + " ==> " + "(" + Arrays.toString(bArr) + ")");
        enforceReceiveAndSend("Updating message on Icc");
        if (this.mAppOps.noteOp(22, Binder.getCallingUid(), str) != 0) {
            return false;
        }
        synchronized (this.mLock) {
            this.mSuccess = false;
            Message obtainMessage = this.mHandler.obtainMessage(2);
            if (i2 != 0) {
                IccFileHandler iccFileHandler = this.mPhone.getIccFileHandler();
                if (iccFileHandler == null) {
                    obtainMessage.recycle();
                    boolean z = this.mSuccess;
                    return z;
                }
                iccFileHandler.updateEFLinearFixed(IccConstants.EF_SMS, i, makeSmsRecordData(i2, bArr), null, obtainMessage);
            } else if (1 == this.mPhone.getPhoneType()) {
                this.mPhone.mCi.deleteSmsOnSim(i, obtainMessage);
            } else {
                this.mPhone.mCi.deleteSmsOnRuim(i, obtainMessage);
            }
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to update by index");
            }
            return this.mSuccess;
        }
    }

    /* Access modifiers changed, original: protected */
    public void updatePhoneObject(PhoneBase phoneBase) {
        this.mPhone = phoneBase;
        this.mDispatcher.updatePhoneObject(phoneBase);
    }
}
