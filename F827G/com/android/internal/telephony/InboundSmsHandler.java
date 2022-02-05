package com.android.internal.telephony;

import android.app.ActivityManagerNative;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Build;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.service.carrier.ICarrierMessagingCallback;
import android.service.carrier.ICarrierMessagingService;
import android.service.carrier.MessagePdu;
import android.telephony.CarrierMessagingServiceManager;
import android.telephony.Rlog;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.HexDump;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jp.co.sharp.android.internal.telephony.SmsDuplicate;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public abstract class InboundSmsHandler extends StateMachine {
    static final int ADDRESS_COLUMN = 6;
    static final int COUNT_COLUMN = 5;
    static final int DATE_COLUMN = 3;
    protected static final boolean DBG = true;
    static final int DESTINATION_PORT_COLUMN = 2;
    static final int EVENT_BROADCAST_COMPLETE = 3;
    static final int EVENT_BROADCAST_SMS = 2;
    public static final int EVENT_INJECT_SMS = 8;
    public static final int EVENT_NEW_SMS = 1;
    static final int EVENT_RELEASE_WAKELOCK = 5;
    static final int EVENT_RETURN_TO_IDLE = 4;
    static final int EVENT_START_ACCEPTING_SMS = 6;
    static final int EVENT_UPDATE_PHONE_OBJECT = 7;
    static final int ID_COLUMN = 7;
    static final int PDU_COLUMN = 0;
    static final int REFERENCE_NUMBER_COLUMN = 4;
    static final String SELECT_BY_ID = "_id=?";
    static final String SELECT_BY_REFERENCE = "address=? AND reference_number=? AND count=?";
    static final int SEQUENCE_COLUMN = 1;
    private static final long TOLERANCE_TIME_MILLIS = 604800000;
    private static final boolean VDBG = false;
    private static final int WAKELOCK_TIMEOUT = 3000;
    protected CellBroadcastHandler mCellBroadcastHandler;
    protected final Context mContext;
    protected SmsDuplicate mDuplicate;
    protected PhoneBase mPhone;
    private final ContentResolver mResolver;
    private final boolean mSmsReceiveDisabled;
    protected SmsStorageMonitor mStorageMonitor;
    private UserManager mUserManager;
    final PowerManager.WakeLock mWakeLock;
    private final WapPushOverSms mWapPush;
    private static final String[] PDU_PROJECTION = {"pdu"};
    private static final String[] PDU_SEQUENCE_PORT_PROJECTION = {"pdu", "sequence", "destination_port"};
    private static final Uri sRawUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "raw");
    final DefaultState mDefaultState = new DefaultState();
    final StartupState mStartupState = new StartupState();
    final IdleState mIdleState = new IdleState();
    final DeliveringState mDeliveringState = new DeliveringState();
    final WaitingState mWaitingState = new WaitingState();

    protected abstract void acknowledgeLastIncomingSms(boolean z, int i, Message message);

    protected abstract int dispatchMessageRadioSpecific(SmsMessageBase smsMessageBase);

    protected abstract boolean is3gpp2();

    public InboundSmsHandler(String name, Context context, SmsStorageMonitor storageMonitor, PhoneBase phone, CellBroadcastHandler cellBroadcastHandler) {
        super(name);
        boolean z;
        this.mContext = context;
        this.mStorageMonitor = storageMonitor;
        this.mPhone = phone;
        this.mCellBroadcastHandler = cellBroadcastHandler;
        this.mResolver = context.getContentResolver();
        this.mWapPush = new WapPushOverSms(context);
        if (!SystemProperties.getBoolean("telephony.sms.receive", this.mContext.getResources().getBoolean(17956949))) {
            z = true;
        } else {
            z = false;
        }
        this.mSmsReceiveDisabled = z;
        this.mWakeLock = ((PowerManager) this.mContext.getSystemService("power")).newWakeLock(1, name);
        this.mWakeLock.acquire();
        this.mUserManager = (UserManager) this.mContext.getSystemService(Telephony.Carriers.USER);
        addState(this.mDefaultState);
        addState(this.mStartupState, this.mDefaultState);
        addState(this.mIdleState, this.mDefaultState);
        addState(this.mDeliveringState, this.mDefaultState);
        addState(this.mWaitingState, this.mDeliveringState);
        setInitialState(this.mStartupState);
        log("created InboundSmsHandler");
    }

    public void dispose() {
        quit();
    }

    public void updatePhoneObject(PhoneBase phone) {
        sendMessage(7, phone);
    }

    public void onQuitting() {
        this.mWapPush.dispose();
        while (this.mWakeLock.isHeld()) {
            this.mWakeLock.release();
        }
    }

    public PhoneBase getPhone() {
        return this.mPhone;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DefaultState extends State {
        DefaultState() {
            InboundSmsHandler.this = r1;
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 7:
                    InboundSmsHandler.this.onUpdatePhoneObject((PhoneBase) msg.obj);
                    return true;
                default:
                    String errorText = "processMessage: unhandled message type " + msg.what + " currState=" + InboundSmsHandler.this.getCurrentState().getName();
                    if (Build.IS_DEBUGGABLE) {
                        InboundSmsHandler.this.loge("---- Dumping InboundSmsHandler ----");
                        InboundSmsHandler.this.loge("Total records=" + InboundSmsHandler.this.getLogRecCount());
                        for (int i = Math.max(InboundSmsHandler.this.getLogRecSize() - 20, 0); i < InboundSmsHandler.this.getLogRecSize(); i++) {
                            InboundSmsHandler.this.loge("Rec[%d]: %s\n" + i + InboundSmsHandler.this.getLogRec(i).toString());
                        }
                        InboundSmsHandler.this.loge("---- Dumped InboundSmsHandler ----");
                        throw new RuntimeException(errorText);
                    }
                    InboundSmsHandler.this.loge(errorText);
                    return true;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class StartupState extends State {
        StartupState() {
            InboundSmsHandler.this = r1;
        }

        public boolean processMessage(Message msg) {
            InboundSmsHandler.this.log("StartupState.processMessage:" + msg.what);
            switch (msg.what) {
                case 1:
                case 2:
                case 8:
                    InboundSmsHandler.this.deferMessage(msg);
                    return true;
                case 3:
                case 4:
                case 5:
                case 7:
                default:
                    return false;
                case 6:
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mIdleState);
                    return true;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class IdleState extends State {
        IdleState() {
            InboundSmsHandler.this = r1;
        }

        public void enter() {
            InboundSmsHandler.this.log("entering Idle state");
            InboundSmsHandler.this.sendMessageDelayed(5, 3000L);
        }

        public void exit() {
            InboundSmsHandler.this.mWakeLock.acquire();
            InboundSmsHandler.this.log("acquired wakelock, leaving Idle state");
        }

        public boolean processMessage(Message msg) {
            InboundSmsHandler.this.log("IdleState.processMessage:" + msg.what);
            InboundSmsHandler.this.log("Idle state processing message type " + msg.what);
            switch (msg.what) {
                case 1:
                case 2:
                case 8:
                    InboundSmsHandler.this.deferMessage(msg);
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mDeliveringState);
                    return true;
                case 3:
                case 6:
                case 7:
                default:
                    return false;
                case 4:
                    return true;
                case 5:
                    InboundSmsHandler.this.mWakeLock.release();
                    if (InboundSmsHandler.this.mWakeLock.isHeld()) {
                        InboundSmsHandler.this.log("mWakeLock is still held after release");
                        return true;
                    }
                    InboundSmsHandler.this.log("mWakeLock released");
                    return true;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class DeliveringState extends State {
        DeliveringState() {
            InboundSmsHandler.this = r1;
        }

        public void enter() {
            InboundSmsHandler.this.log("entering Delivering state");
        }

        public void exit() {
            InboundSmsHandler.this.log("leaving Delivering state");
        }

        public boolean processMessage(Message msg) {
            InboundSmsHandler.this.log("DeliveringState.processMessage:" + msg.what);
            switch (msg.what) {
                case 1:
                    InboundSmsHandler.this.handleNewSms((AsyncResult) msg.obj);
                    InboundSmsHandler.this.sendMessage(4);
                    return true;
                case 2:
                    if (InboundSmsHandler.this.processMessagePart((InboundSmsTracker) msg.obj)) {
                        InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mWaitingState);
                    }
                    return true;
                case 3:
                case 6:
                case 7:
                default:
                    return false;
                case 4:
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mIdleState);
                    return true;
                case 5:
                    InboundSmsHandler.this.mWakeLock.release();
                    if (!InboundSmsHandler.this.mWakeLock.isHeld()) {
                        InboundSmsHandler.this.loge("mWakeLock released while delivering/broadcasting!");
                    }
                    return true;
                case 8:
                    InboundSmsHandler.this.handleInjectSms((AsyncResult) msg.obj);
                    InboundSmsHandler.this.sendMessage(4);
                    return true;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class WaitingState extends State {
        WaitingState() {
            InboundSmsHandler.this = r1;
        }

        public boolean processMessage(Message msg) {
            InboundSmsHandler.this.log("WaitingState.processMessage:" + msg.what);
            switch (msg.what) {
                case 2:
                    InboundSmsHandler.this.deferMessage(msg);
                    return true;
                case 3:
                    InboundSmsHandler.this.sendMessage(4);
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mDeliveringState);
                    return true;
                case 4:
                    return true;
                default:
                    return false;
            }
        }
    }

    void handleNewSms(AsyncResult ar) {
        int result;
        boolean handled = true;
        if (ar.exception != null) {
            loge("Exception processing incoming SMS: " + ar.exception);
            return;
        }
        try {
            result = dispatchMessage(((SmsMessage) ar.result).mWrappedSmsMessage);
        } catch (RuntimeException ex) {
            loge("Exception dispatching message", ex);
            result = 2;
        }
        SmsHeader smsHeader = ((SmsMessage) ar.result).mWrappedSmsMessage.getUserDataHeader();
        if (smsHeader != null && smsHeader.portAddrs != null && smsHeader.portAddrs.destPort == 2948 && result == 3) {
            acknowledgeLastIncomingSms(false, result, null);
        } else if (result != -1) {
            if (result != 1) {
                handled = false;
            }
            notifyAndAcknowledgeLastIncomingSms(handled, result, null);
        }
    }

    void handleInjectSms(AsyncResult ar) {
        int result;
        PendingIntent receivedIntent = null;
        try {
            receivedIntent = (PendingIntent) ar.userObj;
            SmsMessage sms = (SmsMessage) ar.result;
            if (sms == null) {
                result = 2;
            } else {
                result = dispatchMessage(sms.mWrappedSmsMessage);
            }
        } catch (RuntimeException ex) {
            loge("Exception dispatching message", ex);
            result = 2;
        }
        if (receivedIntent != null) {
            try {
                receivedIntent.send(result);
            } catch (PendingIntent.CanceledException e) {
            }
        }
    }

    public int dispatchMessage(SmsMessageBase smsb) {
        if (smsb == null) {
            loge("dispatchSmsMessage: message is null");
            return 2;
        } else if (!this.mSmsReceiveDisabled) {
            return dispatchMessageRadioSpecific(smsb);
        } else {
            log("Received short message on device which doesn't support receiving SMS. Ignored.");
            return 1;
        }
    }

    public void onUpdatePhoneObject(PhoneBase phone) {
        this.mPhone = phone;
        this.mStorageMonitor = this.mPhone.mSmsStorageMonitor;
        log("onUpdatePhoneObject: phone=" + this.mPhone.getClass().getSimpleName());
    }

    void notifyAndAcknowledgeLastIncomingSms(boolean success, int result, Message response) {
        if (!success) {
            Intent intent = new Intent(Telephony.Sms.Intents.SMS_REJECTED_ACTION);
            intent.putExtra("result", result);
            this.mContext.sendBroadcast(intent, "android.permission.RECEIVE_SMS");
        }
        acknowledgeLastIncomingSms(success, result, response);
    }

    protected int dispatchNormalMessage(SmsMessageBase sms) {
        InboundSmsTracker tracker;
        SmsHeader smsHeader = sms.getUserDataHeader();
        if (smsHeader == null || smsHeader.concatRef == null) {
            int destPort = -1;
            if (!(smsHeader == null || smsHeader.portAddrs == null)) {
                destPort = smsHeader.portAddrs.destPort;
                log("destination port: " + destPort);
            }
            tracker = new InboundSmsTracker(sms.getPdu(), sms.getTimestampMillis(), destPort, is3gpp2(), false);
        } else {
            SmsHeader.ConcatRef concatRef = smsHeader.concatRef;
            SmsHeader.PortAddrs portAddrs = smsHeader.portAddrs;
            tracker = new InboundSmsTracker(sms.getPdu(), sms.getTimestampMillis(), portAddrs != null ? portAddrs.destPort : -1, is3gpp2(), sms.getOriginatingAddress(), concatRef.refNumber, concatRef.seqNumber, concatRef.msgCount, false);
        }
        return addTrackerToRawTableAndSendMessage(tracker);
    }

    protected int addTrackerToRawTableAndSendMessage(InboundSmsTracker tracker) {
        switch (addTrackerToRawTable(tracker)) {
            case 1:
                sendMessage(2, tracker);
                return 1;
            case 5:
                return 1;
            default:
                return 2;
        }
    }

    boolean processMessagePart(InboundSmsTracker tracker) {
        byte[][] pdus;
        int messageCount = tracker.getMessageCount();
        int destPort = tracker.getDestPort();
        String address = "";
        if (messageCount == 1) {
            pdus = new byte[][]{tracker.getPdu()};
        } else {
            Cursor cursor = null;
            try {
                try {
                    address = tracker.getAddress();
                    cursor = this.mResolver.query(sRawUri, PDU_SEQUENCE_PORT_PROJECTION, SELECT_BY_REFERENCE, new String[]{address, Integer.toString(tracker.getReferenceNumber()), Integer.toString(tracker.getMessageCount())}, null);
                    if (cursor.getCount() >= messageCount) {
                        pdus = new byte[messageCount];
                        while (cursor.moveToNext()) {
                            int index = cursor.getInt(1) - tracker.getIndexOffset();
                            pdus[index] = HexDump.hexStringToByteArray(cursor.getString(0));
                            if (index == 0 && !cursor.isNull(2)) {
                                int port = InboundSmsTracker.getRealDestPort(cursor.getInt(2));
                                if (port != -1) {
                                    destPort = port;
                                }
                            }
                        }
                        if (cursor != null) {
                            cursor.close();
                        }
                    } else if (cursor == null) {
                        return false;
                    } else {
                        cursor.close();
                        return false;
                    }
                } catch (SQLException e) {
                    loge("Can't access multipart SMS database", e);
                    if (cursor == null) {
                        return false;
                    }
                    cursor.close();
                    return false;
                }
            } catch (Throwable th) {
                if (cursor != null) {
                    cursor.close();
                }
                throw th;
            }
        }
        SmsBroadcastReceiver resultReceiver = new SmsBroadcastReceiver(tracker);
        if (destPort == 2948) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (byte[] pdu : pdus) {
                if (!tracker.is3gpp2()) {
                    SmsMessage msg = SmsMessage.createFromPdu(pdu, SmsMessage.FORMAT_3GPP);
                    pdu = msg.getUserData();
                    if (address == "") {
                        address = msg.getOriginatingAddress();
                    } else if (address == "") {
                        address = tracker.getAddress();
                    }
                }
                output.write(pdu, 0, pdu.length);
            }
            this.mWapPush.setDeleteWhere(tracker.getDeleteWhere());
            this.mWapPush.setDeleteWhereArgs(tracker.getDeleteWhereArgs());
            int result = this.mWapPush.dispatchWapPdu(output.toByteArray(), resultReceiver, this, address);
            log("dispatchWapPdu() returned " + result);
            return result == -1;
        }
        List<String> carrierPackages = null;
        UiccCard card = UiccController.getInstance().getUiccCard(this.mPhone.getPhoneId());
        if (card != null) {
            carrierPackages = card.getCarrierPackageNamesForIntent(this.mContext.getPackageManager(), new Intent("android.service.carrier.CarrierMessagingService"));
        } else {
            loge("UiccCard not initialized.");
        }
        List<String> systemPackages = getSystemAppForIntent(new Intent("android.service.carrier.CarrierMessagingService"));
        if (carrierPackages != null && carrierPackages.size() == 1) {
            log("Found carrier package.");
            CarrierSmsFilter smsFilter = new CarrierSmsFilter(pdus, destPort, tracker.getFormat(), resultReceiver);
            smsFilter.filterSms(carrierPackages.get(0), new CarrierSmsFilterCallback(smsFilter));
        } else if (systemPackages == null || systemPackages.size() != 1) {
            logv("Unable to find carrier package: " + carrierPackages + ", nor systemPackages: " + systemPackages);
            dispatchSmsDeliveryIntent(pdus, tracker.getFormat(), destPort, resultReceiver);
        } else {
            log("Found system package.");
            CarrierSmsFilter smsFilter2 = new CarrierSmsFilter(pdus, destPort, tracker.getFormat(), resultReceiver);
            smsFilter2.filterSms(systemPackages.get(0), new CarrierSmsFilterCallback(smsFilter2));
        }
        return true;
    }

    private List<String> getSystemAppForIntent(Intent intent) {
        List<String> packages = new ArrayList<>();
        PackageManager packageManager = this.mContext.getPackageManager();
        for (ResolveInfo info : packageManager.queryIntentServices(intent, 0)) {
            if (info.serviceInfo == null) {
                loge("Can't get service information from " + info);
            } else {
                String packageName = info.serviceInfo.packageName;
                if (packageManager.checkPermission("android.permission.CARRIER_FILTER_SMS", packageName) == 0) {
                    packages.add(packageName);
                    log("getSystemAppForIntent: added package " + packageName);
                }
            }
        }
        return packages;
    }

    public void dispatchIntent(Intent intent, String permission, int appOp, BroadcastReceiver resultReceiver, UserHandle user) {
        UserInfo info;
        intent.addFlags(134217728);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
        if (user.equals(UserHandle.ALL)) {
            int[] users = null;
            try {
                users = ActivityManagerNative.getDefault().getRunningUserIds();
            } catch (RemoteException e) {
            }
            if (users == null) {
                users = new int[]{user.getIdentifier()};
            }
            for (int i = users.length - 1; i >= 0; i--) {
                UserHandle targetUser = new UserHandle(users[i]);
                if (users[i] == 0 || (!this.mUserManager.hasUserRestriction("no_sms", targetUser) && (info = this.mUserManager.getUserInfo(users[i])) != null && !info.isManagedProfile())) {
                    this.mContext.sendOrderedBroadcastAsUser(intent, targetUser, permission, appOp, users[i] == 0 ? resultReceiver : null, getHandler(), -1, null, null);
                }
            }
            return;
        }
        this.mContext.sendOrderedBroadcastAsUser(intent, user, permission, appOp, resultReceiver, getHandler(), -1, null, null);
    }

    public void deleteFromRawTable(String deleteWhere, String[] deleteWhereArgs) {
        int rows = this.mResolver.delete(sRawUri, deleteWhere, deleteWhereArgs);
        if (rows == 0) {
            loge("No rows were deleted from raw table!");
        } else {
            log("Deleted " + rows + " rows from raw table.");
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    void dispatchSmsDeliveryIntent(byte[][] pdus, String format, int destPort, BroadcastReceiver resultReceiver) {
        Uri uri;
        Intent intent = new Intent();
        intent.putExtra("pdus", (Serializable) pdus);
        intent.putExtra(Telephony.CellBroadcasts.MESSAGE_FORMAT, format);
        if (destPort == -1) {
            intent.setAction(Telephony.Sms.Intents.SMS_DELIVER_ACTION);
            ComponentName componentName = SmsApplication.getDefaultSmsApplication(this.mContext, true);
            if (componentName != null) {
                intent.setComponent(componentName);
                log("Delivering SMS to: " + componentName.getPackageName() + " " + componentName.getClassName());
            } else {
                intent.setComponent(null);
            }
            if (SmsManager.getDefault().getAutoPersisting() && (uri = writeInboxMessage(intent)) != null) {
                intent.putExtra("uri", uri.toString());
            }
        } else {
            intent.setAction(Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION);
            intent.setData(Uri.parse("sms://localhost:" + destPort));
            intent.setComponent(null);
        }
        dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, resultReceiver, UserHandle.OWNER);
    }

    private int addTrackerToRawTable(InboundSmsTracker tracker) {
        Cursor cursor;
        if (tracker.getMessageCount() != 1) {
            try {
                cursor = null;
                try {
                    this.mResolver.delete(sRawUri, "date<?", new String[]{Long.toString(tracker.getTimestamp() - TOLERANCE_TIME_MILLIS)});
                    int sequence = tracker.getSequenceNumber();
                    String address = tracker.getAddress();
                    String refNumber = Integer.toString(tracker.getReferenceNumber());
                    String count = Integer.toString(tracker.getMessageCount());
                    String seqNumber = Integer.toString(sequence);
                    tracker.setDeleteWhere(SELECT_BY_REFERENCE, new String[]{address, refNumber, count});
                    Cursor cursor2 = this.mResolver.query(sRawUri, PDU_PROJECTION, "address=? AND reference_number=? AND count=? AND sequence=?", new String[]{address, refNumber, count, seqNumber}, null);
                    if (cursor2.moveToNext()) {
                        loge("Discarding duplicate message segment, refNumber=" + refNumber + " seqNumber=" + seqNumber);
                        String oldPduString = cursor2.getString(0);
                        byte[] pdu = tracker.getPdu();
                        byte[] oldPdu = HexDump.hexStringToByteArray(oldPduString);
                        if (!Arrays.equals(oldPdu, tracker.getPdu())) {
                            loge("Warning: dup message segment PDU of length " + pdu.length + " is different from existing PDU of length " + oldPdu.length);
                        }
                        if (cursor2 == null) {
                            return 5;
                        }
                        cursor2.close();
                        return 5;
                    }
                    cursor2.close();
                    if (cursor2 != null) {
                        cursor2.close();
                    }
                } catch (SQLException e) {
                    loge("Can't access multipart SMS database", e);
                    if (0 == 0) {
                        return 2;
                    }
                    cursor.close();
                    return 2;
                }
            } catch (Throwable th) {
                if (0 != 0) {
                    cursor.close();
                }
                throw th;
            }
        } else if (judSmsDuplicate(tracker)) {
            log("Received SMS is duplicate");
            return 5;
        }
        Uri newUri = this.mResolver.insert(sRawUri, tracker.getContentValues());
        log("URI of new row -> " + newUri);
        try {
            long rowId = ContentUris.parseId(newUri);
            if (tracker.getMessageCount() == 1) {
                tracker.setDeleteWhere(SELECT_BY_ID, new String[]{Long.toString(rowId)});
            }
            return 1;
        } catch (Exception e2) {
            loge("error parsing URI for new row: " + newUri, e2);
            return 2;
        }
    }

    public static boolean isCurrentFormat3gpp2() {
        return 2 == TelephonyManager.getDefault().getCurrentPhoneType();
    }

    protected void storeVoiceMailCount() {
        String imsi = this.mPhone.getSubscriberId();
        int mwi = this.mPhone.getVoiceMessageCount();
        StringBuilder append = new StringBuilder().append("Storing Voice Mail Count = ").append(mwi).append(" for mVmCountKey = ");
        PhoneBase phoneBase = this.mPhone;
        StringBuilder append2 = append.append(PhoneBase.VM_COUNT).append(" vmId = ");
        PhoneBase phoneBase2 = this.mPhone;
        log(append2.append(PhoneBase.VM_ID).append(" subId = ").append(this.mPhone.getSubId()).append(" in preferences.").toString());
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
        StringBuilder sb = new StringBuilder();
        PhoneBase phoneBase3 = this.mPhone;
        editor.putInt(sb.append(PhoneBase.VM_COUNT).append(this.mPhone.getSubId()).toString(), mwi);
        StringBuilder sb2 = new StringBuilder();
        PhoneBase phoneBase4 = this.mPhone;
        editor.putString(sb2.append(PhoneBase.VM_ID).append(this.mPhone.getSubId()).toString(), imsi);
        editor.commit();
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public final class SmsBroadcastReceiver extends BroadcastReceiver {
        private long mBroadcastTimeNano = System.nanoTime();
        private final String mDeleteWhere;
        private final String[] mDeleteWhereArgs;

        SmsBroadcastReceiver(InboundSmsTracker tracker) {
            InboundSmsHandler.this = r3;
            this.mDeleteWhere = tracker.getDeleteWhere();
            this.mDeleteWhereArgs = tracker.getDeleteWhereArgs();
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Telephony.Sms.Intents.SMS_DELIVER_ACTION)) {
                intent.setAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
                intent.setComponent(null);
                InboundSmsHandler.this.dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, this, UserHandle.ALL);
            } else if (action.equals(Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION)) {
                intent.setAction(Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION);
                intent.setComponent(null);
                InboundSmsHandler.this.dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, this, UserHandle.OWNER);
            } else {
                if (!Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION.equals(action) && !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action) && !Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION.equals(action) && !Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION.equals(action)) {
                    InboundSmsHandler.this.loge("unexpected BroadcastReceiver action: " + action);
                }
                int rc = getResultCode();
                if (rc == -1 || rc == 1) {
                    InboundSmsHandler.this.log("successful broadcast, deleting from raw table.");
                } else {
                    InboundSmsHandler.this.loge("a broadcast receiver set the result code to " + rc + ", deleting from raw table anyway!");
                }
                InboundSmsHandler.this.deleteFromRawTable(this.mDeleteWhere, this.mDeleteWhereArgs);
                InboundSmsHandler.this.sendMessage(3);
                int durationMillis = (int) ((System.nanoTime() - this.mBroadcastTimeNano) / 1000000);
                if (durationMillis >= 5000) {
                    InboundSmsHandler.this.loge("Slow ordered broadcast completion time: " + durationMillis + " ms");
                } else {
                    InboundSmsHandler.this.log("ordered broadcast completed in: " + durationMillis + " ms");
                }
            }
        }
    }

    protected boolean judSmsDuplicate(InboundSmsTracker tracker) {
        boolean isDuplicate = false;
        if (tracker.getDestPort() == 2948) {
            return false;
        }
        try {
            if (this.mDuplicate == null) {
                this.mDuplicate = new SmsDuplicate(this.mContext, 1, true);
            }
            byte[] pdu = getMessage_NotmtiPdu(tracker.getPdu());
            SmsDuplicate.ResultJudgeDuplicate info = this.mDuplicate.checkSmsDuplicate(tracker.getReferenceNumber(), pdu);
            if (info != null && info.mIsSame && info.mIsReply) {
                isDuplicate = true;
            }
            this.mDuplicate.updateSmsDuplicate(tracker.getReferenceNumber(), pdu, this.mDuplicate.SmsAccessory(Telephony.Sms.Intents.SMS_RECEIVED_ACTION, null, 0));
        } catch (NullPointerException e) {
            loge("Function failed to create SmsAccessory.");
        }
        return isDuplicate;
    }

    private byte[] getMessage_NotmtiPdu(byte[] pdus) {
        int maxlen = pdus.length;
        int i = 0 + 1;
        int cur = pdus[0] + 1;
        if (cur >= maxlen) {
            return pdus;
        }
        int cur2 = cur + 1;
        byte[] pdu = new byte[maxlen - cur2];
        System.arraycopy(pdus, cur2, pdu, 0, maxlen - cur2);
        return pdu;
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public final class CarrierSmsFilter extends CarrierMessagingServiceManager {
        private final int mDestPort;
        private final byte[][] mPdus;
        private final SmsBroadcastReceiver mSmsBroadcastReceiver;
        private volatile CarrierSmsFilterCallback mSmsFilterCallback;
        private final String mSmsFormat;

        CarrierSmsFilter(byte[][] pdus, int destPort, String smsFormat, SmsBroadcastReceiver smsBroadcastReceiver) {
            InboundSmsHandler.this = r1;
            this.mPdus = pdus;
            this.mDestPort = destPort;
            this.mSmsFormat = smsFormat;
            this.mSmsBroadcastReceiver = smsBroadcastReceiver;
        }

        void filterSms(String carrierPackageName, CarrierSmsFilterCallback smsFilterCallback) {
            this.mSmsFilterCallback = smsFilterCallback;
            if (!bindToCarrierMessagingService(InboundSmsHandler.this.mContext, carrierPackageName)) {
                InboundSmsHandler.this.loge("bindService() for carrier messaging service failed");
                smsFilterCallback.onFilterComplete(true);
                return;
            }
            InboundSmsHandler.this.logv("bindService() for carrier messaging service succeeded");
        }

        @Override // android.telephony.CarrierMessagingServiceManager
        protected void onServiceReady(ICarrierMessagingService carrierMessagingService) {
            try {
                carrierMessagingService.filterSms(new MessagePdu(Arrays.asList(this.mPdus)), this.mSmsFormat, this.mDestPort, InboundSmsHandler.this.mPhone.getSubId(), this.mSmsFilterCallback);
            } catch (RemoteException e) {
                InboundSmsHandler.this.loge("Exception filtering the SMS: " + e);
                this.mSmsFilterCallback.onFilterComplete(true);
            }
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public final class CarrierSmsFilterCallback extends ICarrierMessagingCallback.Stub {
        private final CarrierSmsFilter mSmsFilter;

        CarrierSmsFilterCallback(CarrierSmsFilter smsFilter) {
            InboundSmsHandler.this = r1;
            this.mSmsFilter = smsFilter;
        }

        /* JADX WARN: Finally extract failed */
        public void onFilterComplete(boolean keepMessage) {
            this.mSmsFilter.disposeConnection(InboundSmsHandler.this.mContext);
            InboundSmsHandler.this.logv("onFilterComplete: keepMessage is " + keepMessage);
            if (keepMessage) {
                InboundSmsHandler.this.dispatchSmsDeliveryIntent(this.mSmsFilter.mPdus, this.mSmsFilter.mSmsFormat, this.mSmsFilter.mDestPort, this.mSmsFilter.mSmsBroadcastReceiver);
                return;
            }
            long token = Binder.clearCallingIdentity();
            try {
                InboundSmsHandler.this.deleteFromRawTable(this.mSmsFilter.mSmsBroadcastReceiver.mDeleteWhere, this.mSmsFilter.mSmsBroadcastReceiver.mDeleteWhereArgs);
                Binder.restoreCallingIdentity(token);
                InboundSmsHandler.this.sendMessage(3);
            } catch (Throwable th) {
                Binder.restoreCallingIdentity(token);
                throw th;
            }
        }

        public void onSendSmsComplete(int result, int messageRef) {
            InboundSmsHandler.this.loge("Unexpected onSendSmsComplete call with result: " + result);
        }

        public void onSendMultipartSmsComplete(int result, int[] messageRefs) {
            InboundSmsHandler.this.loge("Unexpected onSendMultipartSmsComplete call with result: " + result);
        }

        public void onSendMmsComplete(int result, byte[] sendConfPdu) {
            InboundSmsHandler.this.loge("Unexpected onSendMmsComplete call with result: " + result);
        }

        public void onDownloadMmsComplete(int result) {
            InboundSmsHandler.this.loge("Unexpected onDownloadMmsComplete call with result: " + result);
        }
    }

    protected void log(String s) {
        Rlog.d(getName(), s);
    }

    protected void loge(String s) {
        Rlog.e(getName(), s);
    }

    protected void loge(String s, Throwable e) {
        Rlog.e(getName(), s, e);
    }

    private Uri writeInboxMessage(Intent intent) {
        Uri uri = null;
        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length < 1) {
            loge("Failed to parse SMS pdu");
        } else {
            for (SmsMessage sms : messages) {
                try {
                    sms.getDisplayMessageBody();
                } catch (NullPointerException e) {
                    loge("NPE inside SmsMessage");
                }
            }
            ContentValues values = parseSmsMessage(messages);
            long identity = Binder.clearCallingIdentity();
            try {
                uri = this.mContext.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI, values);
            } catch (Exception e2) {
                loge("Failed to persist inbox message", e2);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
        return uri;
    }

    private static ContentValues parseSmsMessage(SmsMessage[] msgs) {
        int i = 0;
        SmsMessage sms = msgs[0];
        ContentValues values = new ContentValues();
        values.put("address", sms.getDisplayOriginatingAddress());
        values.put("body", buildMessageBodyFromPdus(msgs));
        values.put("date_sent", Long.valueOf(sms.getTimestampMillis()));
        values.put("date", Long.valueOf(System.currentTimeMillis()));
        values.put("protocol", Integer.valueOf(sms.getProtocolIdentifier()));
        values.put("seen", (Integer) 0);
        values.put("read", (Integer) 0);
        String subject = sms.getPseudoSubject();
        if (!TextUtils.isEmpty(subject)) {
            values.put(Telephony.TextBasedSmsColumns.SUBJECT, subject);
        }
        if (sms.isReplyPathPresent()) {
            i = 1;
        }
        values.put(Telephony.TextBasedSmsColumns.REPLY_PATH_PRESENT, Integer.valueOf(i));
        values.put(Telephony.TextBasedSmsColumns.SERVICE_CENTER, sms.getServiceCenterAddress());
        return values;
    }

    private static String buildMessageBodyFromPdus(SmsMessage[] msgs) {
        if (msgs.length == 1) {
            return replaceFormFeeds(msgs[0].getDisplayMessageBody());
        }
        StringBuilder body = new StringBuilder();
        for (SmsMessage msg : msgs) {
            body.append(msg.getDisplayMessageBody());
        }
        return replaceFormFeeds(body.toString());
    }

    private static String replaceFormFeeds(String s) {
        return s == null ? "" : s.replace('\f', '\n');
    }
}
