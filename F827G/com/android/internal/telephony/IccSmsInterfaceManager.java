package com.android.internal.telephony;

import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserManager;
import android.provider.Telephony;
import android.telephony.Rlog;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import com.android.internal.telephony.CommandException;
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

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class IccSmsInterfaceManager {
    static final boolean DBG = true;
    private static final int DEFAULT_CBS_RETRY_COUNT = 6;
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
    protected final AppOpsManager mAppOps;
    protected final Context mContext;
    protected SMSDispatcher mDispatcher;
    protected PhoneBase mPhone;
    private List<SmsRawData> mSms;
    protected boolean mSuccess;
    private final UserManager mUserManager;
    private static int mCbsRetryCount = SystemProperties.getInt("persist.radio.cbs_retry_count", 6);
    private static final int DEFAULT_CBS_RETRY_DELAY = 500;
    private static int mCbsRetryDelay = SystemProperties.getInt("persist.radio.cbs_retry_delay", (int) DEFAULT_CBS_RETRY_DELAY);
    protected final Object mLock = new Object();
    private final Object mCbsRetryLock = new Object();
    private String mSmscAddress = null;
    private boolean mSmscSuccess = false;
    private final Object mGetSmscLock = new Object();
    private final Object mSetSmscLock = new Object();
    private CellBroadcastRangeManager mCellBroadcastRangeManager = new CellBroadcastRangeManager();
    private CdmaBroadcastRangeManager mCdmaBroadcastRangeManager = new CdmaBroadcastRangeManager();
    private boolean mIsRetry = false;
    private boolean mRetryOut = false;
    protected Handler mHandler = new Handler() { // from class: com.android.internal.telephony.IccSmsInterfaceManager.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            boolean z = true;
            switch (msg.what) {
                case 1:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    synchronized (IccSmsInterfaceManager.this.mLock) {
                        if (ar.exception == null) {
                            IccSmsInterfaceManager.this.mSms = IccSmsInterfaceManager.this.buildValidRawData((ArrayList) ar.result);
                            IccSmsInterfaceManager.this.markMessagesAsRead((ArrayList) ar.result);
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
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    synchronized (IccSmsInterfaceManager.this.mLock) {
                        IccSmsInterfaceManager iccSmsInterfaceManager = IccSmsInterfaceManager.this;
                        if (ar2.exception != null) {
                            z = false;
                        }
                        iccSmsInterfaceManager.mSuccess = z;
                        IccSmsInterfaceManager.this.mLock.notifyAll();
                    }
                    return;
                case 3:
                case 4:
                    AsyncResult ar3 = (AsyncResult) msg.obj;
                    synchronized (IccSmsInterfaceManager.this.mLock) {
                        IccSmsInterfaceManager iccSmsInterfaceManager2 = IccSmsInterfaceManager.this;
                        if (ar3.exception != null) {
                            z = false;
                        }
                        iccSmsInterfaceManager2.mSuccess = z;
                        if (TelBrand.IS_DCM) {
                            IccSmsInterfaceManager.this.mIsRetry = false;
                            if (!IccSmsInterfaceManager.this.mSuccess && ((CommandException) ar3.exception).getCommandError() == CommandException.Error.CBS_REQUEST_FAIL_RETRY) {
                                IccSmsInterfaceManager.this.mIsRetry = true;
                            }
                        }
                        IccSmsInterfaceManager.this.mLock.notifyAll();
                    }
                    return;
                case 5:
                    AsyncResult ar4 = (AsyncResult) msg.obj;
                    synchronized (IccSmsInterfaceManager.this.mGetSmscLock) {
                        if (ar4.exception == null) {
                            IccSmsInterfaceManager.this.mSmscAddress = (String) ar4.result;
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
                    AsyncResult ar5 = (AsyncResult) msg.obj;
                    synchronized (IccSmsInterfaceManager.this.mSetSmscLock) {
                        IccSmsInterfaceManager iccSmsInterfaceManager3 = IccSmsInterfaceManager.this;
                        if (ar5.exception != null) {
                            z = false;
                        }
                        iccSmsInterfaceManager3.mSmscSuccess = z;
                        IccSmsInterfaceManager.this.mSetSmscLock.notifyAll();
                    }
                    return;
                case 7:
                case 8:
                case 9:
                default:
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
            }
        }
    };

    /* JADX INFO: Access modifiers changed from: protected */
    public IccSmsInterfaceManager(PhoneBase phone) {
        this.mPhone = phone;
        this.mContext = phone.getContext();
        this.mAppOps = (AppOpsManager) this.mContext.getSystemService("appops");
        this.mUserManager = (UserManager) this.mContext.getSystemService(Telephony.Carriers.USER);
        this.mDispatcher = new ImsSMSDispatcher(phone, phone.mSmsStorageMonitor, phone.mSmsUsageMonitor);
    }

    protected void markMessagesAsRead(ArrayList<byte[]> messages) {
        if (messages != null) {
            IccFileHandler fh = this.mPhone.getIccFileHandler();
            if (fh != null) {
                int count = messages.size();
                for (int i = 0; i < count; i++) {
                    byte[] ba = messages.get(i);
                    if (ba[0] == 3) {
                        int n = ba.length;
                        byte[] nba = new byte[n - 1];
                        System.arraycopy(ba, 1, nba, 0, n - 1);
                        fh.updateEFLinearFixed(IccConstants.EF_SMS, i + 1, makeSmsRecordData(1, nba), null, null);
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

    /* JADX INFO: Access modifiers changed from: protected */
    public void updatePhoneObject(PhoneBase phone) {
        this.mPhone = phone;
        this.mDispatcher.updatePhoneObject(phone);
    }

    protected void enforceReceiveAndSend(String message) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.RECEIVE_SMS", message);
        this.mContext.enforceCallingOrSelfPermission("android.permission.SEND_SMS", message);
    }

    public boolean updateMessageOnIccEf(String callingPackage, int index, int status, byte[] pdu) {
        log("updateMessageOnIccEf: index=" + index + " status=" + status + " ==> (" + Arrays.toString(pdu) + ")");
        enforceReceiveAndSend("Updating message on Icc");
        if (this.mAppOps.noteOp(22, Binder.getCallingUid(), callingPackage) != 0) {
            return false;
        }
        synchronized (this.mLock) {
            this.mSuccess = false;
            Message response = this.mHandler.obtainMessage(2);
            if (status != 0) {
                IccFileHandler fh = this.mPhone.getIccFileHandler();
                if (fh == null) {
                    response.recycle();
                    return this.mSuccess;
                }
                fh.updateEFLinearFixed(IccConstants.EF_SMS, index, makeSmsRecordData(status, pdu), null, response);
            } else if (1 == this.mPhone.getPhoneType()) {
                this.mPhone.mCi.deleteSmsOnSim(index, response);
            } else {
                this.mPhone.mCi.deleteSmsOnRuim(index, response);
            }
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to update by index");
            }
            return this.mSuccess;
        }
    }

    public boolean copyMessageToIccEf(String callingPackage, int status, byte[] pdu, byte[] smsc) {
        log("copyMessageToIccEf: status=" + status + " ==> pdu=(" + Arrays.toString(pdu) + "), smsc=(" + Arrays.toString(smsc) + ")");
        enforceReceiveAndSend("Copying message to Icc");
        if (this.mAppOps.noteOp(22, Binder.getCallingUid(), callingPackage) != 0) {
            return false;
        }
        synchronized (this.mLock) {
            this.mSuccess = false;
            Message response = this.mHandler.obtainMessage(2);
            if (1 == this.mPhone.getPhoneType()) {
                this.mPhone.mCi.writeSmsToSim(status, IccUtils.bytesToHexString(smsc), IccUtils.bytesToHexString(pdu), response);
            } else {
                this.mPhone.mCi.writeSmsToRuim(status, IccUtils.bytesToHexString(pdu), response);
            }
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to update by index");
            }
        }
        return this.mSuccess;
    }

    public List<SmsRawData> getAllMessagesFromIccEf(String callingPackage) {
        log("getAllMessagesFromEF");
        this.mContext.enforceCallingOrSelfPermission("android.permission.RECEIVE_SMS", "Reading messages from Icc");
        if (this.mAppOps.noteOp(21, Binder.getCallingUid(), callingPackage) != 0) {
            return new ArrayList();
        }
        synchronized (this.mLock) {
            IccFileHandler fh = this.mPhone.getIccFileHandler();
            if (fh == null) {
                Rlog.e(LOG_TAG, "Cannot load Sms records. No icc card?");
                if (this.mSms != null) {
                    this.mSms.clear();
                    return this.mSms;
                }
            }
            fh.loadEFLinearFixedAll(IccConstants.EF_SMS, this.mHandler.obtainMessage(1));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to load from the Icc");
            }
            return this.mSms;
        }
    }

    public void sendData(String callingPackage, String destAddr, String scAddr, int destPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendData: destAddr=" + destAddr + " scAddr=" + scAddr + " destPort=" + destPort + " data='" + HexDump.toHexString(data) + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendData(filterDestAddress(destAddr), scAddr, destPort, 0, data, sentIntent, deliveryIntent);
        }
    }

    public void sendDataWithOrigPort(String callingPackage, String destAddr, String scAddr, int destPort, int origPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendDataWithOrigPort: destAddr=" + destAddr + " scAddr=" + scAddr + " destPort=" + destPort + "origPort=" + origPort + " data='" + HexDump.toHexString(data) + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendData(filterDestAddress(destAddr), scAddr, destPort, origPort, data, sentIntent, deliveryIntent);
        }
    }

    public void sendText(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendText: destAddr=" + destAddr + " scAddr=" + scAddr + " text='" + text + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendText(filterDestAddress(destAddr), scAddr, text, sentIntent, deliveryIntent, null, callingPackage, -1, false, -1);
        }
    }

    public void sendTextWithOptions(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, int priority, boolean isExpectMore, int validityPeriod) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendText: destAddr=" + destAddr + " scAddr=" + scAddr + " text='" + text + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent + "validityPeriod" + validityPeriod);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendText(destAddr, scAddr, text, sentIntent, deliveryIntent, null, callingPackage, priority, isExpectMore, validityPeriod);
        }
    }

    public void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        enforceCarrierPrivilege();
        if (Rlog.isLoggable("SMS", 2)) {
            log("pdu: " + pdu + "\n format=" + format + "\n receivedIntent=" + receivedIntent);
        }
        this.mDispatcher.injectSmsPdu(pdu, format, receivedIntent);
    }

    public void sendMultipartText(String callingPackage, String destAddr, String scAddr, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents) {
        String singlePart;
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            int i = 0;
            Iterator i$ = parts.iterator();
            while (i$.hasNext()) {
                log("sendMultipartText: destAddr=" + destAddr + ", srAddr=" + scAddr + ", part[" + i + "]=" + i$.next());
                i++;
            }
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            String destAddr2 = filterDestAddress(destAddr);
            if (parts.size() <= 1 || parts.size() >= 10 || SmsMessage.hasEmsSupport()) {
                this.mDispatcher.sendMultipartText(destAddr2, scAddr, (ArrayList) parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, null, callingPackage, -1, false, -1);
                return;
            }
            for (int i2 = 0; i2 < parts.size(); i2++) {
                String singlePart2 = parts.get(i2);
                if (SmsMessage.shouldAppendPageNumberAsPrefix()) {
                    singlePart = String.valueOf(i2 + 1) + '/' + parts.size() + ' ' + singlePart2;
                } else {
                    singlePart = singlePart2.concat(' ' + String.valueOf(i2 + 1) + '/' + parts.size());
                }
                PendingIntent singleSentIntent = null;
                if (sentIntents != null && sentIntents.size() > i2) {
                    singleSentIntent = sentIntents.get(i2);
                }
                PendingIntent singleDeliveryIntent = null;
                if (deliveryIntents != null && deliveryIntents.size() > i2) {
                    singleDeliveryIntent = deliveryIntents.get(i2);
                }
                this.mDispatcher.sendText(destAddr2, scAddr, singlePart, singleSentIntent, singleDeliveryIntent, null, callingPackage, -1, false, -1);
            }
        }
    }

    public void sendMultipartTextWithOptions(String callingPackage, String destAddr, String scAddr, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents, int priority, boolean isExpectMore, int validityPeriod) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            int i = 0;
            Iterator i$ = parts.iterator();
            while (i$.hasNext()) {
                log("sendMultipartTextWithOptions: destAddr=" + destAddr + ", srAddr=" + scAddr + ", part[" + i + "]=" + i$.next());
                i++;
            }
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendMultipartText(destAddr, scAddr, (ArrayList) parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, null, callingPackage, priority, isExpectMore, validityPeriod);
        }
    }

    public int getPremiumSmsPermission(String packageName) {
        return this.mDispatcher.getPremiumSmsPermission(packageName);
    }

    public void setPremiumSmsPermission(String packageName, int permission) {
        this.mDispatcher.setPremiumSmsPermission(packageName, permission);
    }

    protected ArrayList<SmsRawData> buildValidRawData(ArrayList<byte[]> messages) {
        int count = messages.size();
        ArrayList<SmsRawData> ret = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (messages.get(i)[0] == 0) {
                ret.add(null);
            } else {
                ret.add(new SmsRawData(messages.get(i)));
            }
        }
        return ret;
    }

    protected byte[] makeSmsRecordData(int status, byte[] pdu) {
        byte[] data;
        if (1 == this.mPhone.getPhoneType()) {
            data = new byte[176];
        } else {
            data = new byte[255];
        }
        data[0] = (byte) (status & 7);
        System.arraycopy(pdu, 0, data, 1, pdu.length);
        for (int j = pdu.length + 1; j < data.length; j++) {
            data[j] = -1;
        }
        return data;
    }

    public boolean enableCellBroadcast(int messageIdentifier, int ranType) {
        return enableCellBroadcastRange(messageIdentifier, messageIdentifier, ranType);
    }

    public boolean disableCellBroadcast(int messageIdentifier, int ranType) {
        return disableCellBroadcastRange(messageIdentifier, messageIdentifier, ranType);
    }

    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId, int ranType) {
        if (ranType == 0) {
            return enableGsmBroadcastRange(startMessageId, endMessageId);
        }
        if (ranType == 1) {
            return enableCdmaBroadcastRange(startMessageId, endMessageId);
        }
        throw new IllegalArgumentException("Not a supportted RAN Type");
    }

    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId, int ranType) {
        if (ranType == 0) {
            return disableGsmBroadcastRange(startMessageId, endMessageId);
        }
        if (ranType == 1) {
            return disableCdmaBroadcastRange(startMessageId, endMessageId);
        }
        throw new IllegalArgumentException("Not a supportted RAN Type");
    }

    public synchronized boolean enableGsmBroadcastRange(int startMessageId, int endMessageId) {
        boolean z = false;
        synchronized (this) {
            log("enableGsmBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Enabling cell broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (!this.mCellBroadcastRangeManager.enableRange(startMessageId, endMessageId, client)) {
                log("Failed to add GSM cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            } else {
                log("Added GSM cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
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
            }
        }
        return z;
    }

    public synchronized boolean disableGsmBroadcastRange(int startMessageId, int endMessageId) {
        boolean z = false;
        synchronized (this) {
            log("disableGsmBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Disabling cell broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (!this.mCellBroadcastRangeManager.disableRange(startMessageId, endMessageId, client)) {
                log("Failed to remove GSM cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            } else {
                log("Removed GSM cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
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
            }
        }
        return z;
    }

    public synchronized boolean enableCdmaBroadcastRange(int startMessageId, int endMessageId) {
        boolean z = false;
        synchronized (this) {
            log("enableCdmaBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Enabling cdma broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (!this.mCdmaBroadcastRangeManager.enableRange(startMessageId, endMessageId, client)) {
                log("Failed to add cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            } else {
                log("Added cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
                if (!this.mCdmaBroadcastRangeManager.isEmpty()) {
                    z = true;
                }
                setCdmaBroadcastActivation(z);
                z = true;
            }
        }
        return z;
    }

    public synchronized boolean disableCdmaBroadcastRange(int startMessageId, int endMessageId) {
        boolean z = false;
        synchronized (this) {
            log("disableCdmaBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Disabling cell broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (!this.mCdmaBroadcastRangeManager.disableRange(startMessageId, endMessageId, client)) {
                log("Failed to remove cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            } else {
                log("Removed cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
                if (!this.mCdmaBroadcastRangeManager.isEmpty()) {
                    z = true;
                }
                setCdmaBroadcastActivation(z);
                z = true;
            }
        }
        return z;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class CellBroadcastRangeManager extends IntRangeManager {
        private ArrayList<SmsBroadcastConfigInfo> mConfigList = new ArrayList<>();

        CellBroadcastRangeManager() {
        }

        @Override // com.android.internal.telephony.IntRangeManager
        protected void startUpdate() {
            this.mConfigList.clear();
        }

        @Override // com.android.internal.telephony.IntRangeManager
        protected void addRange(int startId, int endId, boolean selected) {
            this.mConfigList.add(new SmsBroadcastConfigInfo(startId, endId, 0, 255, selected));
        }

        @Override // com.android.internal.telephony.IntRangeManager
        protected boolean finishUpdate() {
            if (this.mConfigList.isEmpty()) {
                return true;
            }
            SmsBroadcastConfigInfo[] configs = (SmsBroadcastConfigInfo[]) this.mConfigList.toArray(new SmsBroadcastConfigInfo[this.mConfigList.size()]);
            return TelBrand.IS_DCM ? IccSmsInterfaceManager.this.setCellBroadcastConfigEx(configs) : IccSmsInterfaceManager.this.setCellBroadcastConfig(configs);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class CdmaBroadcastRangeManager extends IntRangeManager {
        private ArrayList<CdmaSmsBroadcastConfigInfo> mConfigList = new ArrayList<>();

        CdmaBroadcastRangeManager() {
        }

        @Override // com.android.internal.telephony.IntRangeManager
        protected void startUpdate() {
            this.mConfigList.clear();
        }

        @Override // com.android.internal.telephony.IntRangeManager
        protected void addRange(int startId, int endId, boolean selected) {
            this.mConfigList.add(new CdmaSmsBroadcastConfigInfo(startId, endId, 1, selected));
        }

        @Override // com.android.internal.telephony.IntRangeManager
        protected boolean finishUpdate() {
            if (this.mConfigList.isEmpty()) {
                return true;
            }
            return IccSmsInterfaceManager.this.setCdmaBroadcastConfig((CdmaSmsBroadcastConfigInfo[]) this.mConfigList.toArray(new CdmaSmsBroadcastConfigInfo[this.mConfigList.size()]));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean setCellBroadcastConfig(SmsBroadcastConfigInfo[] configs) {
        log("Calling setGsmBroadcastConfig with " + configs.length + " configurations");
        synchronized (this.mLock) {
            Message response = this.mHandler.obtainMessage(4);
            this.mSuccess = false;
            this.mPhone.mCi.setGsmBroadcastConfig(configs, response);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cell broadcast config");
            }
        }
        return this.mSuccess;
    }

    private boolean setCellBroadcastActivation(boolean activate) {
        log("Calling setCellBroadcastActivation(" + activate + ')');
        synchronized (this.mLock) {
            Message response = this.mHandler.obtainMessage(3);
            this.mSuccess = false;
            this.mPhone.mCi.setGsmBroadcastActivation(activate, response);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cell broadcast activation");
            }
        }
        return this.mSuccess;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs) {
        log("Calling setCdmaBroadcastConfig with " + configs.length + " configurations");
        synchronized (this.mLock) {
            Message response = this.mHandler.obtainMessage(4);
            this.mSuccess = false;
            this.mPhone.mCi.setCdmaBroadcastConfig(configs, response);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cdma broadcast config");
            }
        }
        return this.mSuccess;
    }

    private boolean setCdmaBroadcastActivation(boolean activate) {
        log("Calling setCdmaBroadcastActivation(" + activate + ")");
        synchronized (this.mLock) {
            Message response = this.mHandler.obtainMessage(3);
            this.mSuccess = false;
            this.mPhone.mCi.setCdmaBroadcastActivation(activate, response);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cdma broadcast activation");
            }
        }
        return this.mSuccess;
    }

    protected void log(String msg) {
        Log.d(LOG_TAG, "[IccSmsInterfaceManager] " + msg);
    }

    public boolean isImsSmsSupported() {
        return this.mDispatcher.isIms();
    }

    public String getImsSmsFormat() {
        return this.mDispatcher.getImsSmsFormat();
    }

    public void sendStoredText(String callingPkg, Uri messageUri, String scAddress, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendStoredText: scAddr=" + scAddress + " messageUri=" + messageUri + " sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPkg) == 0) {
            ContentResolver resolver = this.mPhone.getContext().getContentResolver();
            if (!isFailedOrDraft(resolver, messageUri)) {
                Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredText: not FAILED or DRAFT message");
                returnUnspecifiedFailure(sentIntent);
                return;
            }
            String[] textAndAddress = loadTextAndAddress(resolver, messageUri);
            if (textAndAddress == null) {
                Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredText: can not load text");
                returnUnspecifiedFailure(sentIntent);
                return;
            }
            textAndAddress[1] = filterDestAddress(textAndAddress[1]);
            this.mDispatcher.sendText(textAndAddress[1], scAddress, textAndAddress[0], sentIntent, deliveryIntent, messageUri, callingPkg, -1, false, -1);
        }
    }

    public void sendStoredMultipartText(String callingPkg, Uri messageUri, String scAddress, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents) {
        String singlePart;
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPkg) == 0) {
            ContentResolver resolver = this.mPhone.getContext().getContentResolver();
            if (!isFailedOrDraft(resolver, messageUri)) {
                Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredMultipartText: not FAILED or DRAFT message");
                returnUnspecifiedFailure(sentIntents);
                return;
            }
            String[] textAndAddress = loadTextAndAddress(resolver, messageUri);
            if (textAndAddress == null) {
                Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredMultipartText: can not load text");
                returnUnspecifiedFailure(sentIntents);
                return;
            }
            ArrayList<String> parts = SmsManager.getDefault().divideMessage(textAndAddress[0]);
            if (parts == null || parts.size() < 1) {
                Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredMultipartText: can not divide text");
                returnUnspecifiedFailure(sentIntents);
                return;
            }
            textAndAddress[1] = filterDestAddress(textAndAddress[1]);
            if (parts.size() <= 1 || parts.size() >= 10 || SmsMessage.hasEmsSupport()) {
                this.mDispatcher.sendMultipartText(textAndAddress[1], scAddress, parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, messageUri, callingPkg, -1, false, -1);
                return;
            }
            for (int i = 0; i < parts.size(); i++) {
                String singlePart2 = parts.get(i);
                if (SmsMessage.shouldAppendPageNumberAsPrefix()) {
                    singlePart = String.valueOf(i + 1) + '/' + parts.size() + ' ' + singlePart2;
                } else {
                    singlePart = singlePart2.concat(' ' + String.valueOf(i + 1) + '/' + parts.size());
                }
                PendingIntent singleSentIntent = null;
                if (sentIntents != null && sentIntents.size() > i) {
                    singleSentIntent = sentIntents.get(i);
                }
                PendingIntent singleDeliveryIntent = null;
                if (deliveryIntents != null && deliveryIntents.size() > i) {
                    singleDeliveryIntent = deliveryIntents.get(i);
                }
                this.mDispatcher.sendText(textAndAddress[1], scAddress, singlePart, singleSentIntent, singleDeliveryIntent, messageUri, callingPkg, -1, false, -1);
            }
        }
    }

    private boolean isFailedOrDraft(ContentResolver resolver, Uri messageUri) {
        Cursor cursor;
        long identity;
        boolean z;
        try {
            identity = Binder.clearCallingIdentity();
            cursor = null;
            try {
                cursor = resolver.query(messageUri, new String[]{"type"}, null, null, null);
            } catch (SQLiteException e) {
                Log.e(LOG_TAG, "[IccSmsInterfaceManager]isFailedOrDraft: query message type failed", e);
                if (cursor != null) {
                    cursor.close();
                }
                Binder.restoreCallingIdentity(identity);
            }
            if (cursor == null || !cursor.moveToFirst()) {
                if (cursor != null) {
                    cursor.close();
                }
                Binder.restoreCallingIdentity(identity);
                return false;
            }
            int type = cursor.getInt(0);
            if (type == 3 || type == 5) {
                z = true;
            } else {
                z = false;
            }
            if (cursor != null) {
                cursor.close();
            }
            Binder.restoreCallingIdentity(identity);
            return z;
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
            Binder.restoreCallingIdentity(identity);
            throw th;
        }
    }

    private String[] loadTextAndAddress(ContentResolver resolver, Uri messageUri) {
        long identity = Binder.clearCallingIdentity();
        Cursor cursor = null;
        try {
            try {
                cursor = resolver.query(messageUri, new String[]{"body", "address"}, null, null, null);
            } catch (SQLiteException e) {
                Log.e(LOG_TAG, "[IccSmsInterfaceManager]loadText: query message text failed", e);
                if (cursor != null) {
                    cursor.close();
                }
                Binder.restoreCallingIdentity(identity);
            }
            if (cursor == null || !cursor.moveToFirst()) {
                if (cursor != null) {
                    cursor.close();
                }
                Binder.restoreCallingIdentity(identity);
                return null;
            }
            String[] strArr = {cursor.getString(0), cursor.getString(1)};
            if (cursor != null) {
                cursor.close();
            }
            Binder.restoreCallingIdentity(identity);
            return strArr;
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
            Binder.restoreCallingIdentity(identity);
            throw th;
        }
    }

    private void returnUnspecifiedFailure(PendingIntent pi) {
        if (pi != null) {
            try {
                pi.send(1);
            } catch (PendingIntent.CanceledException e) {
            }
        }
    }

    private void returnUnspecifiedFailure(List<PendingIntent> pis) {
        if (pis != null) {
            for (PendingIntent pi : pis) {
                returnUnspecifiedFailure(pi);
            }
        }
    }

    private void enforceCarrierPrivilege() {
        UiccController controller = UiccController.getInstance();
        if (controller == null || controller.getUiccCard(this.mPhone.getPhoneId()) == null) {
            throw new SecurityException("No Carrier Privilege: No UICC");
        } else if (controller.getUiccCard(this.mPhone.getPhoneId()).getCarrierPrivilegeStatusForCurrentTransaction(this.mContext.getPackageManager()) != 1) {
            throw new SecurityException("No Carrier Privilege.");
        }
    }

    private String filterDestAddress(String destAddr) {
        String result = SmsNumberUtils.filterDestAddr(this.mPhone, destAddr);
        return result != null ? result : destAddr;
    }

    public int getSmsCapacityOnIcc() {
        int numberOnIcc = -1;
        IccRecords ir = this.mPhone.getIccRecords();
        if (ir != null) {
            numberOnIcc = ir.getSmsCapacityOnIcc();
        } else {
            log("getSmsCapacityOnIcc - aborting, no icc card present.");
        }
        log("getSmsCapacityOnIcc().numberOnIcc = " + numberOnIcc);
        return numberOnIcc;
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

    public boolean setSmscAddressToIcc(String scAddress) {
        synchronized (this.mSetSmscLock) {
            Message response = this.mHandler.obtainMessage(6);
            this.mSmscSuccess = false;
            this.mPhone.setSmscAddress(scAddress, response);
            try {
                this.mSetSmscLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set SMSC address");
            }
        }
        return this.mSmscSuccess;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean setCellBroadcastConfigEx(SmsBroadcastConfigInfo[] configs) {
        boolean cellBroadcastConfig;
        boolean isRetry;
        if (!TelBrand.IS_DCM) {
            return false;
        }
        log("Calling setGsmBroadcastConfigEx with " + configs.length + " configurations");
        synchronized (this.mCbsRetryLock) {
            int retryCount = 0;
            do {
                cellBroadcastConfig = setCellBroadcastConfig(configs);
                isRetry = this.mIsRetry;
                if (isRetry) {
                    if (this.mRetryOut || retryCount >= mCbsRetryCount) {
                        isRetry = false;
                        this.mRetryOut = true;
                        continue;
                    } else {
                        this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(10), mCbsRetryDelay);
                        retryCount++;
                        log("Scheduling next attempt on setGsmBroadcastConfig for " + mCbsRetryDelay + "ms, Retry count = " + retryCount);
                        try {
                            this.mCbsRetryLock.wait();
                            continue;
                        } catch (InterruptedException e) {
                            log("interrupted while trying to set cell broadcast config Ex");
                            continue;
                        }
                    }
                }
            } while (isRetry);
        }
        return cellBroadcastConfig;
    }

    private boolean setCellBroadcastActivationEx(boolean activate) {
        boolean cellBroadcastActivation;
        boolean isRetry;
        if (!TelBrand.IS_DCM) {
            return false;
        }
        log("Calling setCellBroadcastActivationEx(" + activate + ")");
        synchronized (this.mCbsRetryLock) {
            int retryCount = 0;
            do {
                cellBroadcastActivation = setCellBroadcastActivation(activate);
                isRetry = this.mIsRetry;
                if (isRetry) {
                    if (this.mRetryOut || retryCount >= mCbsRetryCount) {
                        isRetry = false;
                        this.mRetryOut = true;
                        continue;
                    } else {
                        this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(10), mCbsRetryDelay);
                        retryCount++;
                        log("Scheduling next attempt on setCellBroadcastActivation for " + mCbsRetryDelay + "ms, Retry count = " + retryCount);
                        try {
                            this.mCbsRetryLock.wait();
                            continue;
                        } catch (InterruptedException e) {
                            log("interrupted while trying to set cell broadcast config Ex");
                            continue;
                        }
                    }
                }
            } while (isRetry);
        }
        return cellBroadcastActivation;
    }
}
