package com.android.internal.telephony;

import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.UserManager;
import android.provider.Telephony.Carriers;
import android.telephony.Rlog;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.HexDump;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IccSmsInterfaceManager {
    static final boolean DBG = true;
    private static final int EVENT_LOAD_DONE = 1;
    protected static final int EVENT_SET_BROADCAST_ACTIVATION_DONE = 3;
    protected static final int EVENT_SET_BROADCAST_CONFIG_DONE = 4;
    private static final int EVENT_UPDATE_DONE = 2;
    static final String LOG_TAG = "IccSmsInterfaceManager";
    private static final int SMS_CB_CODE_SCHEME_MAX = 255;
    private static final int SMS_CB_CODE_SCHEME_MIN = 0;
    protected final AppOpsManager mAppOps;
    private CdmaBroadcastRangeManager mCdmaBroadcastRangeManager = new CdmaBroadcastRangeManager();
    private CellBroadcastRangeManager mCellBroadcastRangeManager = new CellBroadcastRangeManager();
    protected final Context mContext;
    protected SMSDispatcher mDispatcher;
    protected Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            boolean z = true;
            AsyncResult ar;
            Object obj;
            IccSmsInterfaceManager iccSmsInterfaceManager;
            switch (msg.what) {
                case 1:
                    ar = (AsyncResult) msg.obj;
                    synchronized (IccSmsInterfaceManager.this.mLock) {
                        if (ar.exception == null) {
                            IccSmsInterfaceManager.this.mSms = IccSmsInterfaceManager.this.buildValidRawData((ArrayList) ar.result);
                            IccSmsInterfaceManager.this.markMessagesAsRead((ArrayList) ar.result);
                        } else {
                            if (Rlog.isLoggable("SMS", 3)) {
                                IccSmsInterfaceManager.this.log("Cannot load Sms records");
                            }
                            IccSmsInterfaceManager.this.mSms = null;
                        }
                        IccSmsInterfaceManager.this.mLock.notifyAll();
                    }
                    return;
                case 2:
                    ar = msg.obj;
                    obj = IccSmsInterfaceManager.this.mLock;
                    synchronized (obj) {
                        iccSmsInterfaceManager = IccSmsInterfaceManager.this;
                        if (ar.exception != null) {
                            z = false;
                        }
                        iccSmsInterfaceManager.mSuccess = z;
                        IccSmsInterfaceManager.this.mLock.notifyAll();
                        break;
                    }
                case 3:
                case 4:
                    ar = (AsyncResult) msg.obj;
                    obj = IccSmsInterfaceManager.this.mLock;
                    synchronized (obj) {
                        iccSmsInterfaceManager = IccSmsInterfaceManager.this;
                        if (ar.exception != null) {
                            z = false;
                        }
                        iccSmsInterfaceManager.mSuccess = z;
                        IccSmsInterfaceManager.this.mLock.notifyAll();
                        break;
                    }
                default:
                    return;
            }
        }
    };
    protected final Object mLock = new Object();
    protected Phone mPhone;
    private List<SmsRawData> mSms;
    protected boolean mSuccess;
    private final UserManager mUserManager;

    class CdmaBroadcastRangeManager extends IntRangeManager {
        private ArrayList<CdmaSmsBroadcastConfigInfo> mConfigList = new ArrayList();

        CdmaBroadcastRangeManager() {
        }

        /* Access modifiers changed, original: protected */
        public void startUpdate() {
            this.mConfigList.clear();
        }

        /* Access modifiers changed, original: protected */
        public void addRange(int startId, int endId, boolean selected) {
            this.mConfigList.add(new CdmaSmsBroadcastConfigInfo(startId, endId, 1, selected));
        }

        /* Access modifiers changed, original: protected */
        public boolean finishUpdate() {
            if (this.mConfigList.isEmpty()) {
                return true;
            }
            return IccSmsInterfaceManager.this.setCdmaBroadcastConfig((CdmaSmsBroadcastConfigInfo[]) this.mConfigList.toArray(new CdmaSmsBroadcastConfigInfo[this.mConfigList.size()]));
        }
    }

    class CellBroadcastRangeManager extends IntRangeManager {
        private ArrayList<SmsBroadcastConfigInfo> mConfigList = new ArrayList();

        CellBroadcastRangeManager() {
        }

        /* Access modifiers changed, original: protected */
        public void startUpdate() {
            this.mConfigList.clear();
        }

        /* Access modifiers changed, original: protected */
        public void addRange(int startId, int endId, boolean selected) {
            this.mConfigList.add(new SmsBroadcastConfigInfo(startId, endId, 0, 255, selected));
        }

        /* Access modifiers changed, original: protected */
        public boolean finishUpdate() {
            if (this.mConfigList.isEmpty()) {
                return true;
            }
            return IccSmsInterfaceManager.this.setCellBroadcastConfig((SmsBroadcastConfigInfo[]) this.mConfigList.toArray(new SmsBroadcastConfigInfo[this.mConfigList.size()]));
        }
    }

    protected IccSmsInterfaceManager(Phone phone) {
        this.mPhone = phone;
        this.mContext = phone.getContext();
        this.mAppOps = (AppOpsManager) this.mContext.getSystemService("appops");
        this.mUserManager = (UserManager) this.mContext.getSystemService(Carriers.USER);
        this.mDispatcher = new ImsSMSDispatcher(phone, phone.mSmsStorageMonitor, phone.mSmsUsageMonitor);
    }

    /* Access modifiers changed, original: protected */
    public void markMessagesAsRead(ArrayList<byte[]> messages) {
        if (messages != null) {
            IccFileHandler fh = this.mPhone.getIccFileHandler();
            if (fh == null) {
                if (Rlog.isLoggable("SMS", 3)) {
                    log("markMessagesAsRead - aborting, no icc card present.");
                }
                return;
            }
            int count = messages.size();
            for (int i = 0; i < count; i++) {
                byte[] ba = (byte[]) messages.get(i);
                if (ba[0] == (byte) 3) {
                    int n = ba.length;
                    byte[] nba = new byte[(n - 1)];
                    System.arraycopy(ba, 1, nba, 0, n - 1);
                    fh.updateEFLinearFixed(IccConstants.EF_SMS, i + 1, makeSmsRecordData(1, nba), null, null);
                    if (Rlog.isLoggable("SMS", 3)) {
                        log("SMS " + (i + 1) + " marked as read");
                    }
                }
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void updatePhoneObject(Phone phone) {
        this.mPhone = phone;
        this.mDispatcher.updatePhoneObject(phone);
    }

    /* Access modifiers changed, original: protected */
    public void enforceReceiveAndSend(String message) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.RECEIVE_SMS", message);
        this.mContext.enforceCallingOrSelfPermission("android.permission.SEND_SMS", message);
    }

    public boolean updateMessageOnIccEf(String callingPackage, int index, int status, byte[] pdu) {
        log("updateMessageOnIccEf: index=" + index + " status=" + status + " ==> " + "(" + Arrays.toString(pdu) + ")");
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
                    boolean z = this.mSuccess;
                    return z;
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
        }
        return this.mSuccess;
    }

    public boolean copyMessageToIccEf(String callingPackage, int status, byte[] pdu, byte[] smsc) {
        log("copyMessageToIccEf: status=" + status + " ==> " + "pdu=(" + Arrays.toString(pdu) + "), smsc=(" + Arrays.toString(smsc) + ")");
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
                this.mSms = null;
                List list = this.mSms;
                return list;
            }
            fh.loadEFLinearFixedAll(IccConstants.EF_SMS, this.mHandler.obtainMessage(1));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to load from the Icc");
            }
        }
        return this.mSms;
    }

    public void sendDataWithSelfPermissions(String callingPackage, String destAddr, String scAddr, int destPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        this.mPhone.getContext().enforceCallingOrSelfPermission("android.permission.SEND_SMS", "Sending SMS message");
        sendDataInternal(callingPackage, destAddr, scAddr, destPort, data, sentIntent, deliveryIntent);
    }

    public void sendData(String callingPackage, String destAddr, String scAddr, int destPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        sendDataInternal(callingPackage, destAddr, scAddr, destPort, data, sentIntent, deliveryIntent);
    }

    private void sendDataInternal(String callingPackage, String destAddr, String scAddr, int destPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendData: destAddr=" + destAddr + " scAddr=" + scAddr + " destPort=" + destPort + " data='" + HexDump.toHexString(data) + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendData(filterDestAddress(destAddr), scAddr, destPort, data, sentIntent, deliveryIntent);
        }
    }

    public void sendText(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean persistMessageForNonDefaultSmsApp) {
        this.mPhone.getContext().enforceCallingOrSelfPermission("android.permission.SEND_SMS", "Sending SMS message");
        sendTextInternal(callingPackage, destAddr, scAddr, text, sentIntent, deliveryIntent, persistMessageForNonDefaultSmsApp);
    }

    public void sendTextWithSelfPermissions(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        this.mPhone.getContext().enforceCallingOrSelfPermission("android.permission.SEND_SMS", "Sending SMS message");
        sendTextInternal(callingPackage, destAddr, scAddr, text, sentIntent, deliveryIntent, true);
    }

    private void sendTextInternal(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean persistMessageForNonDefaultSmsApp) {
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendText: destAddr=" + destAddr + " scAddr=" + scAddr + " text='" + text + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            if (!persistMessageForNonDefaultSmsApp) {
                enforceCarrierPrivilege();
            }
            this.mDispatcher.sendText(filterDestAddress(destAddr), scAddr, text, sentIntent, deliveryIntent, null, callingPackage, persistMessageForNonDefaultSmsApp, -1, false, -1);
        }
    }

    public void sendTextWithOptions(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, int priority, boolean isExpectMore, int validityPeriod) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendText: destAddr=" + destAddr + " scAddr=" + scAddr + " text='" + text + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent + "validityPeriod" + validityPeriod);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendText(destAddr, scAddr, text, sentIntent, deliveryIntent, null, callingPackage, false, priority, isExpectMore, validityPeriod);
        }
    }

    public void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        enforceCarrierPrivilege();
        if (Rlog.isLoggable("SMS", 2)) {
            log("pdu: " + pdu + "\n format=" + format + "\n receivedIntent=" + receivedIntent);
        }
        this.mDispatcher.injectSmsPdu(pdu, format, receivedIntent);
    }

    public void sendMultipartText(String callingPackage, String destAddr, String scAddr, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents, boolean persistMessageForNonDefaultSmsApp) {
        int i;
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (!persistMessageForNonDefaultSmsApp) {
            enforceCarrierPrivilege();
        }
        if (Rlog.isLoggable("SMS", 2)) {
            i = 0;
            for (String part : parts) {
                int i2 = i + 1;
                log("sendMultipartText: destAddr=" + destAddr + ", srAddr=" + scAddr + ", part[" + i + "]=" + part);
                i = i2;
            }
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            destAddr = filterDestAddress(destAddr);
            if (parts.size() <= 1 || parts.size() >= 10 || SmsMessage.hasEmsSupport()) {
                this.mDispatcher.sendMultipartText(destAddr, scAddr, (ArrayList) parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, null, callingPackage, persistMessageForNonDefaultSmsApp, -1, false, -1);
                return;
            }
            i = 0;
            while (i < parts.size()) {
                String singlePart = (String) parts.get(i);
                if (SmsMessage.shouldAppendPageNumberAsPrefix()) {
                    singlePart = String.valueOf(i + 1) + '/' + parts.size() + ' ' + singlePart;
                } else {
                    singlePart = singlePart.concat(' ' + String.valueOf(i + 1) + '/' + parts.size());
                }
                PendingIntent pendingIntent = null;
                if (sentIntents != null && sentIntents.size() > i) {
                    pendingIntent = (PendingIntent) sentIntents.get(i);
                }
                PendingIntent pendingIntent2 = null;
                if (deliveryIntents != null && deliveryIntents.size() > i) {
                    pendingIntent2 = (PendingIntent) deliveryIntents.get(i);
                }
                this.mDispatcher.sendText(destAddr, scAddr, singlePart, pendingIntent, pendingIntent2, null, callingPackage, persistMessageForNonDefaultSmsApp, -1, false, -1);
                i++;
            }
        }
    }

    public void sendMultipartTextWithOptions(String callingPackage, String destAddr, String scAddr, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents, int priority, boolean isExpectMore, int validityPeriod) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            int i = 0;
            for (String part : parts) {
                int i2 = i + 1;
                log("sendMultipartTextWithOptions: destAddr=" + destAddr + ", srAddr=" + scAddr + ", part[" + i + "]=" + part);
                i = i2;
            }
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendMultipartText(destAddr, scAddr, (ArrayList) parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, null, callingPackage, false, priority, isExpectMore, validityPeriod);
        }
    }

    public int getPremiumSmsPermission(String packageName) {
        return this.mDispatcher.getPremiumSmsPermission(packageName);
    }

    public void setPremiumSmsPermission(String packageName, int permission) {
        this.mDispatcher.setPremiumSmsPermission(packageName, permission);
    }

    /* Access modifiers changed, original: protected */
    public ArrayList<SmsRawData> buildValidRawData(ArrayList<byte[]> messages) {
        int count = messages.size();
        ArrayList<SmsRawData> ret = new ArrayList(count);
        for (int i = 0; i < count; i++) {
            if (((byte[]) messages.get(i))[0] == (byte) 0) {
                ret.add(null);
            } else {
                ret.add(new SmsRawData((byte[]) messages.get(i)));
            }
        }
        return ret;
    }

    /* Access modifiers changed, original: protected */
    public byte[] makeSmsRecordData(int status, byte[] pdu) {
        byte[] data;
        if (1 == this.mPhone.getPhoneType()) {
            data = new byte[176];
        } else {
            data = new byte[255];
        }
        data[0] = (byte) (status & 7);
        System.arraycopy(pdu, 0, data, 1, pdu.length);
        for (int j = pdu.length + 1; j < data.length; j++) {
            data[j] = (byte) -1;
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
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Enabling cell broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (this.mCellBroadcastRangeManager.enableRange(startMessageId, endMessageId, client)) {
                log("Added GSM cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
                if (!this.mCellBroadcastRangeManager.isEmpty()) {
                    z = true;
                }
                setCellBroadcastActivation(z);
                return true;
            }
            log("Failed to add GSM cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            return false;
        }
    }

    public synchronized boolean disableGsmBroadcastRange(int startMessageId, int endMessageId) {
        boolean z = false;
        synchronized (this) {
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Disabling cell broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (this.mCellBroadcastRangeManager.disableRange(startMessageId, endMessageId, client)) {
                log("Removed GSM cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
                if (!this.mCellBroadcastRangeManager.isEmpty()) {
                    z = true;
                }
                setCellBroadcastActivation(z);
                return true;
            }
            log("Failed to remove GSM cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            return false;
        }
    }

    public synchronized boolean enableCdmaBroadcastRange(int startMessageId, int endMessageId) {
        boolean z = false;
        synchronized (this) {
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Enabling cdma broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (this.mCdmaBroadcastRangeManager.enableRange(startMessageId, endMessageId, client)) {
                log("Added cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
                if (!this.mCdmaBroadcastRangeManager.isEmpty()) {
                    z = true;
                }
                setCdmaBroadcastActivation(z);
                return true;
            }
            log("Failed to add cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            return false;
        }
    }

    public synchronized boolean disableCdmaBroadcastRange(int startMessageId, int endMessageId) {
        boolean z = false;
        synchronized (this) {
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Disabling cell broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (this.mCdmaBroadcastRangeManager.disableRange(startMessageId, endMessageId, client)) {
                log("Removed cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
                if (!this.mCdmaBroadcastRangeManager.isEmpty()) {
                    z = true;
                }
                setCdmaBroadcastActivation(z);
                return true;
            }
            log("Failed to remove cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            return false;
        }
    }

    private boolean setCellBroadcastConfig(SmsBroadcastConfigInfo[] configs) {
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

    private boolean setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs) {
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

    /* Access modifiers changed, original: protected */
    public void log(String msg) {
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
            if (isFailedOrDraft(resolver, messageUri)) {
                String[] textAndAddress = loadTextAndAddress(resolver, messageUri);
                if (textAndAddress == null) {
                    Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredText: can not load text");
                    returnUnspecifiedFailure(sentIntent);
                    return;
                }
                textAndAddress[1] = filterDestAddress(textAndAddress[1]);
                this.mDispatcher.sendText(textAndAddress[1], scAddress, textAndAddress[0], sentIntent, deliveryIntent, messageUri, callingPkg, true, -1, false, -1);
                return;
            }
            Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredText: not FAILED or DRAFT message");
            returnUnspecifiedFailure(sentIntent);
        }
    }

    public void sendStoredMultipartText(String callingPkg, Uri messageUri, String scAddress, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPkg) == 0) {
            ContentResolver resolver = this.mPhone.getContext().getContentResolver();
            if (isFailedOrDraft(resolver, messageUri)) {
                String[] textAndAddress = loadTextAndAddress(resolver, messageUri);
                if (textAndAddress == null) {
                    Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredMultipartText: can not load text");
                    returnUnspecifiedFailure((List) sentIntents);
                    return;
                }
                ArrayList<String> parts = SmsManager.getDefault().divideMessage(textAndAddress[0]);
                if (parts == null || parts.size() < 1) {
                    Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredMultipartText: can not divide text");
                    returnUnspecifiedFailure((List) sentIntents);
                    return;
                }
                textAndAddress[1] = filterDestAddress(textAndAddress[1]);
                if (parts.size() <= 1 || parts.size() >= 10 || SmsMessage.hasEmsSupport()) {
                    this.mDispatcher.sendMultipartText(textAndAddress[1], scAddress, parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, messageUri, callingPkg, true, -1, false, -1);
                    return;
                }
                int i = 0;
                while (i < parts.size()) {
                    String singlePart = (String) parts.get(i);
                    if (SmsMessage.shouldAppendPageNumberAsPrefix()) {
                        singlePart = String.valueOf(i + 1) + '/' + parts.size() + ' ' + singlePart;
                    } else {
                        singlePart = singlePart.concat(' ' + String.valueOf(i + 1) + '/' + parts.size());
                    }
                    PendingIntent pendingIntent = null;
                    if (sentIntents != null && sentIntents.size() > i) {
                        pendingIntent = (PendingIntent) sentIntents.get(i);
                    }
                    PendingIntent pendingIntent2 = null;
                    if (deliveryIntents != null && deliveryIntents.size() > i) {
                        pendingIntent2 = (PendingIntent) deliveryIntents.get(i);
                    }
                    this.mDispatcher.sendText(textAndAddress[1], scAddress, singlePart, pendingIntent, pendingIntent2, messageUri, callingPkg, true, -1, false, -1);
                    i++;
                }
                return;
            }
            Log.e(LOG_TAG, "[IccSmsInterfaceManager]sendStoredMultipartText: not FAILED or DRAFT message");
            returnUnspecifiedFailure((List) sentIntents);
        }
    }

    private boolean isFailedOrDraft(ContentResolver resolver, Uri messageUri) {
        long identity = Binder.clearCallingIdentity();
        Cursor cursor = null;
        try {
            cursor = resolver.query(messageUri, new String[]{"type"}, null, null, null);
            if (cursor == null || !cursor.moveToFirst()) {
                if (cursor != null) {
                    cursor.close();
                }
                Binder.restoreCallingIdentity(identity);
                return false;
            }
            int type = cursor.getInt(0);
            boolean z = type != 3 ? type == 5 : true;
            if (cursor != null) {
                cursor.close();
            }
            Binder.restoreCallingIdentity(identity);
            return z;
        } catch (SQLiteException e) {
            Log.e(LOG_TAG, "[IccSmsInterfaceManager]isFailedOrDraft: query message type failed", e);
            if (cursor != null) {
                cursor.close();
            }
            Binder.restoreCallingIdentity(identity);
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
        String[] strArr;
        try {
            cursor = resolver.query(messageUri, new String[]{"body", "address"}, null, null, null);
            if (cursor == null || !cursor.moveToFirst()) {
                if (cursor != null) {
                    cursor.close();
                }
                Binder.restoreCallingIdentity(identity);
                return null;
            }
            strArr = new String[]{cursor.getString(0), cursor.getString(1)};
            return strArr;
        } catch (SQLiteException e) {
            strArr = LOG_TAG;
            Log.e(strArr, "[IccSmsInterfaceManager]loadText: query message text failed", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void returnUnspecifiedFailure(PendingIntent pi) {
        if (pi != null) {
            try {
                pi.send(1);
            } catch (CanceledException e) {
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
}
