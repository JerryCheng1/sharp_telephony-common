package com.android.internal.telephony;

import android.app.ActivityManagerNative;
import android.app.BroadcastOptions;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IDeviceIdleController;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.provider.Telephony.Carriers;
import android.provider.Telephony.CellBroadcasts;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Inbox;
import android.provider.Telephony.Sms.Intents;
import android.provider.Telephony.TextBasedSmsColumns;
import android.provider.Telephony.ThreadsColumns;
import android.service.carrier.ICarrierMessagingCallback.Stub;
import android.service.carrier.ICarrierMessagingService;
import android.service.carrier.MessagePdu;
import android.telephony.CarrierMessagingServiceManager;
import android.telephony.Rlog;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.internal.telephony.SmsHeader.ConcatRef;
import com.android.internal.telephony.SmsHeader.PortAddrs;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.HexDump;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jp.co.sharp.android.internal.telephony.SmsDuplicate;

public abstract class InboundSmsHandler extends StateMachine {
    public static final int ADDRESS_COLUMN = 6;
    public static final int COUNT_COLUMN = 5;
    public static final int DATE_COLUMN = 3;
    protected static final boolean DBG = true;
    public static final int DESTINATION_PORT_COLUMN = 2;
    private static final int EVENT_BROADCAST_COMPLETE = 3;
    public static final int EVENT_BROADCAST_SMS = 2;
    public static final int EVENT_INJECT_SMS = 8;
    public static final int EVENT_NEW_SMS = 1;
    private static final int EVENT_RELEASE_WAKELOCK = 5;
    private static final int EVENT_RETURN_TO_IDLE = 4;
    public static final int EVENT_START_ACCEPTING_SMS = 6;
    private static final int EVENT_UPDATE_PHONE_OBJECT = 7;
    static final int EVENT_WAKE_LOCK_TIMEOUT = 9;
    public static final int ID_COLUMN = 7;
    public static final int MESSAGE_BODY_COLUMN = 8;
    private static final int NOTIFICATION_ID_NEW_MESSAGE = 1;
    private static final String NOTIFICATION_TAG = "InboundSmsHandler";
    private static boolean OUTPUT_DEBUG_LOG = false;
    public static final int PDU_COLUMN = 0;
    private static final String[] PDU_PROJECTION = new String[]{"pdu"};
    private static final String[] PDU_SEQUENCE_PORT_PROJECTION = new String[]{"pdu", "sequence", "destination_port"};
    public static final int REFERENCE_NUMBER_COLUMN = 4;
    public static final String SELECT_BY_ID = "_id=?";
    public static final String SELECT_BY_REFERENCE = "address=? AND reference_number=? AND count=? AND deleted=0";
    public static final int SEQUENCE_COLUMN = 1;
    protected static final String TAG = "InboundSmsHandler";
    private static final boolean VDBG = false;
    private static final int WAKELOCK_TIMEOUT = 3000;
    protected static final Uri sRawUri = Uri.withAppendedPath(Sms.CONTENT_URI, "raw");
    protected static final Uri sRawUriPermanentDelete = Uri.withAppendedPath(Sms.CONTENT_URI, "raw/permanentDelete");
    private final int DELETE_PERMANENTLY = 1;
    private final int MARK_DELETED = 2;
    protected CellBroadcastHandler mCellBroadcastHandler;
    protected final Context mContext;
    private final DefaultState mDefaultState = new DefaultState(this, null);
    private final DeliveringState mDeliveringState = new DeliveringState(this, null);
    IDeviceIdleController mDeviceIdleController;
    private final IdleState mIdleState = new IdleState(this, null);
    protected Phone mPhone;
    private final ContentResolver mResolver;
    protected SmsDuplicate mSmsDuplicate;
    private final boolean mSmsReceiveDisabled;
    private final StartupState mStartupState = new StartupState(this, null);
    protected SmsStorageMonitor mStorageMonitor;
    private UserManager mUserManager;
    private final WaitingState mWaitingState = new WaitingState(this, null);
    private final WakeLock mWakeLock;
    int mWakeLockCount = 0;
    private final WapPushOverSms mWapPush;

    private final class CarrierSmsFilter extends CarrierMessagingServiceManager {
        private final int mDestPort;
        private final byte[][] mPdus;
        private final SmsBroadcastReceiver mSmsBroadcastReceiver;
        private volatile CarrierSmsFilterCallback mSmsFilterCallback;
        private final String mSmsFormat;

        CarrierSmsFilter(byte[][] pdus, int destPort, String smsFormat, SmsBroadcastReceiver smsBroadcastReceiver) {
            this.mPdus = pdus;
            this.mDestPort = destPort;
            this.mSmsFormat = smsFormat;
            this.mSmsBroadcastReceiver = smsBroadcastReceiver;
        }

        /* Access modifiers changed, original: 0000 */
        public void filterSms(String carrierPackageName, CarrierSmsFilterCallback smsFilterCallback) {
            this.mSmsFilterCallback = smsFilterCallback;
            if (bindToCarrierMessagingService(InboundSmsHandler.this.mContext, carrierPackageName)) {
                InboundSmsHandler.this.logv("bindService() for carrier messaging service succeeded");
                return;
            }
            InboundSmsHandler.this.loge("bindService() for carrier messaging service failed");
            smsFilterCallback.onFilterComplete(0);
        }

        /* Access modifiers changed, original: protected */
        public void onServiceReady(ICarrierMessagingService carrierMessagingService) {
            try {
                carrierMessagingService.filterSms(new MessagePdu(Arrays.asList(this.mPdus)), this.mSmsFormat, this.mDestPort, InboundSmsHandler.this.mPhone.getSubId(), this.mSmsFilterCallback);
            } catch (RemoteException e) {
                InboundSmsHandler.this.loge("Exception filtering the SMS: " + e);
                this.mSmsFilterCallback.onFilterComplete(0);
            }
        }
    }

    private final class CarrierSmsFilterCallback extends Stub {
        private final CarrierSmsFilter mSmsFilter;
        private final boolean mUserUnlocked;

        CarrierSmsFilterCallback(CarrierSmsFilter smsFilter, boolean userUnlocked) {
            this.mSmsFilter = smsFilter;
            this.mUserUnlocked = userUnlocked;
        }

        public void onFilterComplete(int result) {
            this.mSmsFilter.disposeConnection(InboundSmsHandler.this.mContext);
            InboundSmsHandler.this.logv("onFilterComplete: result is " + result);
            if ((result & 1) != 0) {
                long token = Binder.clearCallingIdentity();
                try {
                    InboundSmsHandler.this.deleteFromRawTable(this.mSmsFilter.mSmsBroadcastReceiver.mDeleteWhere, this.mSmsFilter.mSmsBroadcastReceiver.mDeleteWhereArgs, 2);
                    InboundSmsHandler.this.sendMessage(3);
                } finally {
                    Binder.restoreCallingIdentity(token);
                }
            } else if (this.mUserUnlocked) {
                InboundSmsHandler.this.dispatchSmsDeliveryIntent(this.mSmsFilter.mPdus, this.mSmsFilter.mSmsFormat, this.mSmsFilter.mDestPort, this.mSmsFilter.mSmsBroadcastReceiver);
            } else {
                if (!InboundSmsHandler.this.isSkipNotifyFlagSet(result)) {
                    InboundSmsHandler.this.showNewMessageNotification();
                }
                InboundSmsHandler.this.sendMessage(3);
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

    private class DefaultState extends State {
        /* synthetic */ DefaultState(InboundSmsHandler this$0, DefaultState defaultState) {
            this();
        }

        private DefaultState() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 7:
                    InboundSmsHandler.this.onUpdatePhoneObject((Phone) msg.obj);
                    break;
                case 9:
                    InboundSmsHandler.this.loge("Release wakelock as it timed out");
                    InboundSmsHandler.this.releaseWakeLock();
                    break;
                default:
                    String errorText = "processMessage: unhandled message type " + msg.what + " currState=" + InboundSmsHandler.this.getCurrentState().getName();
                    if (!Build.IS_DEBUGGABLE) {
                        InboundSmsHandler.this.loge(errorText);
                        break;
                    }
                    InboundSmsHandler.this.loge("---- Dumping InboundSmsHandler ----");
                    InboundSmsHandler.this.loge("Total records=" + InboundSmsHandler.this.getLogRecCount());
                    for (int i = Math.max(InboundSmsHandler.this.getLogRecSize() - 20, 0); i < InboundSmsHandler.this.getLogRecSize(); i++) {
                        InboundSmsHandler.this.loge("Rec[%d]: %s\n" + i + InboundSmsHandler.this.getLogRec(i).toString());
                    }
                    InboundSmsHandler.this.loge("---- Dumped InboundSmsHandler ----");
                    throw new RuntimeException(errorText);
            }
            return true;
        }
    }

    private class DeliveringState extends State {
        /* synthetic */ DeliveringState(InboundSmsHandler this$0, DeliveringState deliveringState) {
            this();
        }

        private DeliveringState() {
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
                    if (InboundSmsHandler.OUTPUT_DEBUG_LOG) {
                        Rlog.d("InboundSmsHandler", "New SMS Message Received");
                    }
                    InboundSmsHandler.this.handleNewSms((AsyncResult) msg.obj);
                    InboundSmsHandler.this.sendMessage(4);
                    return true;
                case 2:
                    if (InboundSmsHandler.OUTPUT_DEBUG_LOG) {
                        Rlog.d("InboundSmsHandler", "New Broadcast SMS Message Received");
                    }
                    if (InboundSmsHandler.this.processMessagePart(msg.obj)) {
                        InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mWaitingState);
                    } else {
                        InboundSmsHandler.this.log("No broadcast sent on processing EVENT_BROADCAST_SMS in Delivering state. Return to Idle state");
                        InboundSmsHandler.this.sendMessage(4);
                    }
                    return true;
                case 4:
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mIdleState);
                    return true;
                case 5:
                    InboundSmsHandler.this.decrementWakeLock();
                    if (!InboundSmsHandler.this.mWakeLock.isHeld()) {
                        InboundSmsHandler.this.loge("mWakeLock released while delivering/broadcasting! wakelockcount = " + InboundSmsHandler.this.mWakeLockCount);
                    }
                    return true;
                case 8:
                    InboundSmsHandler.this.handleInjectSms((AsyncResult) msg.obj);
                    InboundSmsHandler.this.sendMessage(4);
                    return true;
                default:
                    return InboundSmsHandler.VDBG;
            }
        }
    }

    private class IdleState extends State {
        /* synthetic */ IdleState(InboundSmsHandler this$0, IdleState idleState) {
            this();
        }

        private IdleState() {
        }

        public void enter() {
            InboundSmsHandler.this.log("entering Idle state");
            InboundSmsHandler.this.sendMessage(5);
        }

        public void exit() {
            InboundSmsHandler.this.acquireWakeLock();
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
                case 4:
                    return true;
                case 5:
                    InboundSmsHandler.this.decrementWakeLock();
                    if (InboundSmsHandler.this.mWakeLock.isHeld()) {
                        InboundSmsHandler.this.log("mWakeLock is still held after release wakelockcount = " + InboundSmsHandler.this.mWakeLockCount);
                    } else {
                        InboundSmsHandler.this.log("mWakeLock released wakelockcount = " + InboundSmsHandler.this.mWakeLockCount);
                    }
                    return true;
                default:
                    return InboundSmsHandler.VDBG;
            }
        }
    }

    private final class SmsBroadcastReceiver extends BroadcastReceiver {
        private long mBroadcastTimeNano = System.nanoTime();
        private final String mDeleteWhere;
        private final String[] mDeleteWhereArgs;

        SmsBroadcastReceiver(InboundSmsTracker tracker) {
            this.mDeleteWhere = tracker.getDeleteWhere();
            this.mDeleteWhereArgs = tracker.getDeleteWhereArgs();
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intents.SMS_DELIVER_ACTION)) {
                intent.setAction(Intents.SMS_RECEIVED_ACTION);
                intent.setComponent(null);
                Intent intent2 = intent;
                InboundSmsHandler.this.dispatchIntent(intent2, "android.permission.RECEIVE_SMS", 16, InboundSmsHandler.this.handleSmsWhitelisting(null), this, UserHandle.ALL);
            } else if (action.equals(Intents.WAP_PUSH_DELIVER_ACTION)) {
                intent.setAction(Intents.WAP_PUSH_RECEIVED_ACTION);
                intent.setComponent(null);
                Bundle options = null;
                try {
                    long duration = InboundSmsHandler.this.mDeviceIdleController.addPowerSaveTempWhitelistAppForMms(InboundSmsHandler.this.mContext.getPackageName(), 0, "mms-broadcast");
                    BroadcastOptions bopts = BroadcastOptions.makeBasic();
                    bopts.setTemporaryAppWhitelistDuration(duration);
                    options = bopts.toBundle();
                } catch (RemoteException e) {
                }
                String mimeType = intent.getType();
                InboundSmsHandler.this.dispatchIntent(intent, WapPushOverSms.getPermissionForType(mimeType), WapPushOverSms.getAppOpsPermissionForIntent(mimeType), options, this, UserHandle.SYSTEM);
            } else {
                if (!(Intents.DATA_SMS_RECEIVED_ACTION.equals(action) || Intents.SMS_RECEIVED_ACTION.equals(action) || Intents.DATA_SMS_RECEIVED_ACTION.equals(action) || Intents.WAP_PUSH_RECEIVED_ACTION.equals(action))) {
                    InboundSmsHandler.this.loge("unexpected BroadcastReceiver action: " + action);
                }
                int rc = getResultCode();
                if (rc == -1 || rc == 1) {
                    InboundSmsHandler.this.log("successful broadcast, deleting from raw table.");
                } else {
                    InboundSmsHandler.this.loge("a broadcast receiver set the result code to " + rc + ", deleting from raw table anyway!");
                }
                InboundSmsHandler.this.deleteFromRawTable(this.mDeleteWhere, this.mDeleteWhereArgs, 2);
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

    private class StartupState extends State {
        /* synthetic */ StartupState(InboundSmsHandler this$0, StartupState startupState) {
            this();
        }

        private StartupState() {
        }

        public boolean processMessage(Message msg) {
            InboundSmsHandler.this.log("StartupState.processMessage:" + msg.what);
            switch (msg.what) {
                case 1:
                case 2:
                case 8:
                    InboundSmsHandler.this.deferMessage(msg);
                    return true;
                case 6:
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mIdleState);
                    return true;
                default:
                    return InboundSmsHandler.VDBG;
            }
        }
    }

    private class WaitingState extends State {
        /* synthetic */ WaitingState(InboundSmsHandler this$0, WaitingState waitingState) {
            this();
        }

        private WaitingState() {
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
                    return InboundSmsHandler.VDBG;
            }
        }
    }

    public abstract void acknowledgeLastIncomingSms(boolean z, int i, Message message);

    public abstract int dispatchMessageRadioSpecific(SmsMessageBase smsMessageBase);

    public abstract boolean is3gpp2();

    static {
        OUTPUT_DEBUG_LOG = true;
        if (Build.TYPE.equals(Carriers.USER)) {
            OUTPUT_DEBUG_LOG = VDBG;
        }
    }

    protected InboundSmsHandler(String name, Context context, SmsStorageMonitor storageMonitor, Phone phone, CellBroadcastHandler cellBroadcastHandler) {
        super(name);
        this.mContext = context;
        this.mStorageMonitor = storageMonitor;
        this.mPhone = phone;
        this.mCellBroadcastHandler = cellBroadcastHandler;
        this.mResolver = context.getContentResolver();
        this.mWapPush = new WapPushOverSms(context);
        this.mSmsReceiveDisabled = TelephonyManager.from(this.mContext).getSmsReceiveCapableForPhone(this.mPhone.getPhoneId(), this.mContext.getResources().getBoolean(17956960)) ? VDBG : true;
        this.mWakeLock = ((PowerManager) this.mContext.getSystemService("power")).newWakeLock(1, name);
        this.mWakeLock.setReferenceCounted(VDBG);
        acquireWakeLock();
        this.mUserManager = (UserManager) this.mContext.getSystemService(Carriers.USER);
        this.mDeviceIdleController = TelephonyComponentFactory.getInstance().getIDeviceIdleController();
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

    public void updatePhoneObject(Phone phone) {
        sendMessage(7, phone);
    }

    /* Access modifiers changed, original: protected */
    public void onQuitting() {
        this.mWapPush.dispose();
        while (this.mWakeLock.isHeld()) {
            this.mWakeLock.release();
        }
    }

    public Phone getPhone() {
        return this.mPhone;
    }

    /* Access modifiers changed, original: 0000 */
    public void acquireWakeLock() {
        synchronized (this.mWakeLock) {
            this.mWakeLock.acquire();
            this.mWakeLockCount++;
            removeMessages(9);
            sendMessageDelayed(9, 3000);
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void decrementWakeLock() {
        synchronized (this.mWakeLock) {
            this.mWakeLockCount--;
            if (this.mWakeLockCount == 0) {
                releaseWakeLock();
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void releaseWakeLock() {
        synchronized (this.mWakeLock) {
            this.mWakeLockCount = 0;
            this.mWakeLock.release();
            removeMessages(9);
        }
    }

    private void handleNewSms(AsyncResult ar) {
        boolean handled = true;
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d("InboundSmsHandler", "InboundSmsHandler:handleNewSms() call!");
        }
        if (ar.exception != null) {
            loge("Exception processing incoming SMS: " + ar.exception);
            return;
        }
        int result;
        try {
            result = dispatchMessage(ar.result.mWrappedSmsMessage);
        } catch (RuntimeException ex) {
            loge("Exception dispatching message", ex);
            result = 2;
        }
        if (result != -1) {
            if (result != 1) {
                handled = VDBG;
            }
            notifyAndAcknowledgeLastIncomingSms(handled, result, null);
        }
    }

    private void handleInjectSms(AsyncResult ar) {
        int result;
        PendingIntent pendingIntent = null;
        try {
            pendingIntent = (PendingIntent) ar.userObj;
            SmsMessage sms = ar.result;
            if (sms == null) {
                result = 2;
            } else {
                result = dispatchMessage(sms.mWrappedSmsMessage);
            }
        } catch (RuntimeException ex) {
            loge("Exception dispatching message", ex);
            result = 2;
        }
        if (pendingIntent != null) {
            try {
                pendingIntent.send(result);
            } catch (CanceledException e) {
            }
        }
    }

    public int dispatchMessage(SmsMessageBase smsb) {
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d("InboundSmsHandler", "dispatchMessage start");
        }
        if (smsb == null) {
            loge("dispatchSmsMessage: message is null");
            return 2;
        } else if (this.mSmsReceiveDisabled) {
            log("Received short message on device which doesn't support receiving SMS. Ignored.");
            return 1;
        } else {
            boolean onlyCore = VDBG;
            try {
                onlyCore = IPackageManager.Stub.asInterface(ServiceManager.getService(Intents.EXTRA_PACKAGE_NAME)).isOnlyCoreApps();
            } catch (RemoteException e) {
            }
            if (!onlyCore) {
                return dispatchMessageRadioSpecific(smsb);
            }
            log("Received a short message in encrypted state. Rejecting.");
            return 2;
        }
    }

    /* Access modifiers changed, original: protected */
    public void onUpdatePhoneObject(Phone phone) {
        this.mPhone = phone;
        this.mStorageMonitor = this.mPhone.mSmsStorageMonitor;
        log("onUpdatePhoneObject: phone=" + this.mPhone.getClass().getSimpleName());
    }

    private void notifyAndAcknowledgeLastIncomingSms(boolean success, int result, Message response) {
        if (!success) {
            Intent intent = new Intent(Intents.SMS_REJECTED_ACTION);
            intent.putExtra("result", result);
            this.mContext.sendBroadcast(intent, "android.permission.RECEIVE_SMS");
        }
        acknowledgeLastIncomingSms(success, result, response);
    }

    /* Access modifiers changed, original: protected */
    public int dispatchNormalMessage(SmsMessageBase sms) {
        InboundSmsTracker tracker;
        SmsHeader smsHeader = sms.getUserDataHeader();
        if (smsHeader == null || smsHeader.concatRef == null) {
            int destPort = -1;
            if (!(smsHeader == null || smsHeader.portAddrs == null)) {
                destPort = smsHeader.portAddrs.destPort;
                log("destination port: " + destPort);
                if (destPort == SmsHeader.PORT_WAP_PUSH) {
                    if (OUTPUT_DEBUG_LOG) {
                        Rlog.d("InboundSmsHandler", "this SMS is PORT_WAP_PUSH!");
                        Rlog.d("InboundSmsHandler", "Output getUserData  Rx: " + IccUtils.bytesToHexString(sms.getUserData()));
                    }
                    tracker = TelephonyComponentFactory.getInstance().makeInboundSmsTracker(sms.getUserData(), sms.getTimestampMillis(), destPort, is3gpp2(), VDBG, sms.getDisplayOriginatingAddress(), sms.getMessageBody());
                    return addTrackerToRawTableAndSendMessage(tracker, tracker.getDestPort() == -1 ? true : VDBG);
                } else if (OUTPUT_DEBUG_LOG) {
                    Rlog.d("InboundSmsHandler", "this SMS is dispatchPortAddressedPdus!");
                }
            }
            tracker = TelephonyComponentFactory.getInstance().makeInboundSmsTracker(sms.getPdu(), sms.getTimestampMillis(), destPort, is3gpp2(), VDBG, sms.getDisplayOriginatingAddress(), sms.getMessageBody());
        } else {
            ConcatRef concatRef = smsHeader.concatRef;
            PortAddrs portAddrs = smsHeader.portAddrs;
            tracker = TelephonyComponentFactory.getInstance().makeInboundSmsTracker(sms.getPdu(), sms.getTimestampMillis(), portAddrs != null ? portAddrs.destPort : -1, is3gpp2(), sms.getDisplayOriginatingAddress(), concatRef.refNumber, concatRef.seqNumber, concatRef.msgCount, VDBG, sms.getMessageBody());
        }
        return addTrackerToRawTableAndSendMessage(tracker, tracker.getDestPort() == -1 ? true : VDBG);
    }

    /* Access modifiers changed, original: protected */
    public int addTrackerToRawTableAndSendMessage(InboundSmsTracker tracker, boolean deDup) {
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d("InboundSmsHandler", "addTrackerToRawTableAndSendMessage Start!");
        }
        switch (addTrackerToRawTable(tracker, deDup)) {
            case 1:
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d("InboundSmsHandler", "addTrackerToRawTableAndSendMessage case is RESULT_SMS_HANDLED!");
                }
                sendMessage(2, tracker);
                return 1;
            case 5:
                return 1;
            default:
                return 2;
        }
    }

    private boolean processMessagePart(InboundSmsTracker tracker) {
        byte[][] pdus;
        int messageCount = tracker.getMessageCount();
        int destPort = tracker.getDestPort();
        if (messageCount == 1) {
            pdus = new byte[][]{tracker.getPdu()};
        } else {
            Cursor cursor = null;
            try {
                String address = tracker.getAddress();
                String refNumber = Integer.toString(tracker.getReferenceNumber());
                String count = Integer.toString(tracker.getMessageCount());
                cursor = this.mResolver.query(sRawUri, PDU_SEQUENCE_PORT_PROJECTION, SELECT_BY_REFERENCE, new String[]{address, refNumber, count}, null);
                if (cursor.getCount() < messageCount) {
                    if (cursor != null) {
                        cursor.close();
                    }
                    return VDBG;
                }
                pdus = new byte[messageCount][];
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
            } catch (SQLException e) {
                loge("Can't access multipart SMS database", e);
                if (cursor != null) {
                    cursor.close();
                }
                return VDBG;
            } catch (Throwable th) {
                if (cursor != null) {
                    cursor.close();
                }
                throw th;
            }
        }
        List<byte[]> pduList = Arrays.asList(pdus);
        if (pduList.size() == 0 || pduList.contains(null)) {
            loge("processMessagePart: returning false due to " + (pduList.size() == 0 ? "pduList.size() == 0" : "pduList.contains(null)"));
            return VDBG;
        } else if (!this.mUserManager.isUserUnlocked()) {
            return processMessagePartWithUserLocked(tracker, pdus, destPort);
        } else {
            SmsBroadcastReceiver resultReceiver = new SmsBroadcastReceiver(tracker);
            if (destPort == SmsHeader.PORT_WAP_PUSH) {
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d("InboundSmsHandler", "this SMS is PORT_WAP_PUSH!");
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                int i = 0;
                int length = pdus.length;
                while (i < length) {
                    byte[] pdu = pdus[i];
                    try {
                        output.write(pdu, 0, pdu.length);
                        i++;
                    } catch (NullPointerException ex) {
                        Rlog.d("InboundSmsHandler", "NullPointerException:ex= " + ex);
                        return VDBG;
                    }
                }
                int result = this.mWapPush.dispatchWapPdu(output.toByteArray(), resultReceiver, this);
                log("dispatchWapPdu() returned " + result);
                if (result == -1) {
                    return true;
                }
                deleteFromRawTable(tracker.getDeleteWhere(), tracker.getDeleteWhereArgs(), 2);
                return VDBG;
            } else if (BlockChecker.isBlocked(this.mContext, tracker.getAddress())) {
                deleteFromRawTable(tracker.getDeleteWhere(), tracker.getDeleteWhereArgs(), 1);
                return VDBG;
            } else {
                if (!filterSmsWithCarrierOrSystemApp(pdus, destPort, tracker, resultReceiver, true)) {
                    dispatchSmsDeliveryIntent(pdus, tracker.getFormat(), destPort, resultReceiver);
                }
                return true;
            }
        }
    }

    private boolean processMessagePartWithUserLocked(InboundSmsTracker tracker, byte[][] pdus, int destPort) {
        log("Credential-encrypted storage not available. Port: " + destPort);
        if (destPort == SmsHeader.PORT_WAP_PUSH && this.mWapPush.isWapPushForMms(pdus[0], this)) {
            showNewMessageNotification();
            return VDBG;
        } else if (destPort != -1) {
            return VDBG;
        } else {
            if (filterSmsWithCarrierOrSystemApp(pdus, destPort, tracker, null, VDBG)) {
                return true;
            }
            showNewMessageNotification();
            return VDBG;
        }
    }

    private void showNewMessageNotification() {
        if (StorageManager.isFileEncryptedNativeOrEmulated()) {
            log("Show new message notification.");
            ((NotificationManager) this.mContext.getSystemService(ThreadsColumns.NOTIFICATION)).notify("InboundSmsHandler", 1, new Builder(this.mContext).setSmallIcon(17301646).setAutoCancel(true).setVisibility(1).setDefaults(-1).setContentTitle(this.mContext.getString(17040899)).setContentText(this.mContext.getString(17040900)).setContentIntent(PendingIntent.getActivity(this.mContext, 1, Intent.makeMainSelectorActivity("android.intent.action.MAIN", "android.intent.category.APP_MESSAGING"), 0)).build());
        }
    }

    static void cancelNewMessageNotification(Context context) {
        ((NotificationManager) context.getSystemService(ThreadsColumns.NOTIFICATION)).cancel("InboundSmsHandler", 1);
    }

    private boolean filterSmsWithCarrierOrSystemApp(byte[][] pdus, int destPort, InboundSmsTracker tracker, SmsBroadcastReceiver resultReceiver, boolean userUnlocked) {
        List carrierPackages = null;
        UiccCard card = UiccController.getInstance().getUiccCard(this.mPhone.getPhoneId());
        if (card != null) {
            carrierPackages = card.getCarrierPackageNamesForIntent(this.mContext.getPackageManager(), new Intent("android.service.carrier.CarrierMessagingService"));
        } else {
            loge("UiccCard not initialized.");
        }
        List<String> systemPackages = getSystemAppForIntent(new Intent("android.service.carrier.CarrierMessagingService"));
        CarrierSmsFilter smsFilter;
        if (carrierPackages != null && carrierPackages.size() == 1) {
            log("Found carrier package.");
            smsFilter = new CarrierSmsFilter(pdus, destPort, tracker.getFormat(), resultReceiver);
            smsFilter.filterSms((String) carrierPackages.get(0), new CarrierSmsFilterCallback(smsFilter, userUnlocked));
            return true;
        } else if (systemPackages == null || systemPackages.size() != 1) {
            logv("Unable to find carrier package: " + carrierPackages + ", nor systemPackages: " + systemPackages);
            return VDBG;
        } else {
            log("Found system package.");
            smsFilter = new CarrierSmsFilter(pdus, destPort, tracker.getFormat(), resultReceiver);
            smsFilter.filterSms((String) systemPackages.get(0), new CarrierSmsFilterCallback(smsFilter, userUnlocked));
            return true;
        }
    }

    private List<String> getSystemAppForIntent(Intent intent) {
        List<String> packages = new ArrayList();
        PackageManager packageManager = this.mContext.getPackageManager();
        String carrierFilterSmsPerm = "android.permission.CARRIER_FILTER_SMS";
        for (ResolveInfo info : packageManager.queryIntentServices(intent, 0)) {
            if (info.serviceInfo == null) {
                loge("Can't get service information from " + info);
            } else {
                String packageName = info.serviceInfo.packageName;
                if (packageManager.checkPermission(carrierFilterSmsPerm, packageName) == 0) {
                    packages.add(packageName);
                    log("getSystemAppForIntent: added package " + packageName);
                }
            }
        }
        return packages;
    }

    public void dispatchIntent(Intent intent, String permission, int appOp, Bundle opts, BroadcastReceiver resultReceiver, UserHandle user) {
        intent.addFlags(134217728);
        String action = intent.getAction();
        if (Intents.SMS_DELIVER_ACTION.equals(action) || Intents.SMS_RECEIVED_ACTION.equals(action) || Intents.WAP_PUSH_DELIVER_ACTION.equals(action) || Intents.WAP_PUSH_RECEIVED_ACTION.equals(action)) {
            intent.addFlags(268435456);
        }
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
                if (users[i] != 0) {
                    if (!this.mUserManager.hasUserRestriction("no_sms", targetUser)) {
                        UserInfo info = this.mUserManager.getUserInfo(users[i]);
                        if (info != null) {
                            if (info.isManagedProfile()) {
                            }
                        }
                    }
                }
                this.mContext.sendOrderedBroadcastAsUser(intent, targetUser, permission, appOp, opts, users[i] == 0 ? resultReceiver : null, getHandler(), -1, null, null);
            }
            return;
        }
        this.mContext.sendOrderedBroadcastAsUser(intent, user, permission, appOp, opts, resultReceiver, getHandler(), -1, null, null);
    }

    private void deleteFromRawTable(String deleteWhere, String[] deleteWhereArgs, int deleteType) {
        int rows = this.mResolver.delete(deleteType == 1 ? sRawUriPermanentDelete : sRawUri, deleteWhere, deleteWhereArgs);
        if (rows == 0) {
            loge("No rows were deleted from raw table!");
        } else {
            log("Deleted " + rows + " rows from raw table.");
        }
    }

    private Bundle handleSmsWhitelisting(ComponentName target) {
        String pkgName;
        String reason;
        if (target != null) {
            pkgName = target.getPackageName();
            reason = "sms-app";
        } else {
            pkgName = this.mContext.getPackageName();
            reason = "sms-broadcast";
        }
        try {
            long duration = this.mDeviceIdleController.addPowerSaveTempWhitelistAppForSms(pkgName, 0, reason);
            BroadcastOptions bopts = BroadcastOptions.makeBasic();
            bopts.setTemporaryAppWhitelistDuration(duration);
            return bopts.toBundle();
        } catch (RemoteException e) {
            return null;
        }
    }

    private void dispatchSmsDeliveryIntent(byte[][] pdus, String format, int destPort, BroadcastReceiver resultReceiver) {
        Intent intent = new Intent();
        intent.putExtra("pdus", pdus);
        intent.putExtra(CellBroadcasts.MESSAGE_FORMAT, format);
        if (destPort == -1) {
            intent.setAction(Intents.SMS_DELIVER_ACTION);
            ComponentName componentName = SmsApplication.getDefaultSmsApplication(this.mContext, true);
            if (componentName != null) {
                intent.setComponent(componentName);
                log("Delivering SMS to: " + componentName.getPackageName() + " " + componentName.getClassName());
            } else {
                intent.setComponent(null);
            }
            if (SmsManager.getDefault().getAutoPersisting()) {
                Uri uri = writeInboxMessage(intent);
                if (uri != null) {
                    intent.putExtra("uri", uri.toString());
                }
            }
        } else {
            intent.setAction(Intents.DATA_SMS_RECEIVED_ACTION);
            intent.setData(Uri.parse("sms://localhost:" + destPort));
            intent.setComponent(null);
        }
        dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, handleSmsWhitelisting(intent.getComponent()), resultReceiver, UserHandle.SYSTEM);
    }

    private int addTrackerToRawTable(InboundSmsTracker tracker, boolean deDup) {
        if (OUTPUT_DEBUG_LOG) {
            Rlog.d("InboundSmsHandler", "addTrackerToRawTable start");
        }
        String address = tracker.getAddress();
        String refNumber = Integer.toString(tracker.getReferenceNumber());
        String count = Integer.toString(tracker.getMessageCount());
        if (!deDup) {
            logd("Skipped message de-duping logic");
        } else if (tracker.getMessageCount() != 1) {
            Cursor cursor = null;
            try {
                String seqNumber = Integer.toString(tracker.getSequenceNumber());
                String date = Long.toString(tracker.getTimestamp());
                String messageBody = tracker.getMessageBody();
                if (OUTPUT_DEBUG_LOG) {
                    Rlog.d("InboundSmsHandler", "address         = " + address);
                    Rlog.d("InboundSmsHandler", "referenceNumber = " + refNumber);
                    Rlog.d("InboundSmsHandler", "count/seqNumber = " + count + "/" + seqNumber);
                }
                cursor = this.mResolver.query(sRawUri, PDU_PROJECTION, "address=? AND reference_number=? AND count=? AND sequence=? AND date=? AND message_body=?", new String[]{address, refNumber, count, seqNumber, date, messageBody}, null);
                if (cursor.moveToNext()) {
                    loge("Discarding duplicate message segment, refNumber=" + refNumber + " seqNumber=" + seqNumber + " count=" + count);
                    String oldPduString = cursor.getString(0);
                    byte[] pdu = tracker.getPdu();
                    byte[] oldPdu = HexDump.hexStringToByteArray(oldPduString);
                    if (!Arrays.equals(oldPdu, tracker.getPdu())) {
                        loge("Warning: dup message segment PDU of length " + pdu.length + " is different from existing PDU of length " + oldPdu.length);
                    }
                    if (cursor != null) {
                        cursor.close();
                    }
                    return 5;
                } else if (cursor != null) {
                    cursor.close();
                }
            } catch (SQLException e) {
                loge("Can't access SMS database", e);
                if (cursor != null) {
                    cursor.close();
                }
                return 2;
            } catch (Throwable th) {
                if (cursor != null) {
                    cursor.close();
                }
                throw th;
            }
        } else if (!(tracker.getDestPort() == SmsHeader.PORT_WAP_PUSH || kddiDispatchPdus(tracker))) {
            loge("Discarding duplicate message segment");
            return 5;
        }
        Uri newUri = this.mResolver.insert(sRawUri, tracker.getContentValues());
        log("URI of new row -> " + newUri);
        try {
            long rowId = ContentUris.parseId(newUri);
            if (tracker.getMessageCount() == 1) {
                tracker.setDeleteWhere(SELECT_BY_ID, new String[]{Long.toString(rowId)});
            } else {
                InboundSmsTracker inboundSmsTracker = tracker;
                inboundSmsTracker.setDeleteWhere(SELECT_BY_REFERENCE, new String[]{address, refNumber, count});
            }
            return 1;
        } catch (Exception e2) {
            loge("error parsing URI for new row: " + newUri, e2);
            return 2;
        }
    }

    static boolean isCurrentFormat3gpp2() {
        return 2 == TelephonyManager.getDefault().getCurrentPhoneType() ? true : VDBG;
    }

    private boolean isSkipNotifyFlagSet(int callbackResult) {
        return (callbackResult & 2) > 0 ? true : VDBG;
    }

    /* Access modifiers changed, original: protected */
    public void log(String s) {
        Rlog.d(getName(), s);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String s) {
        Rlog.e(getName(), s);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String s, Throwable e) {
        Rlog.e(getName(), s, e);
    }

    private Uri writeInboxMessage(Intent intent) {
        SmsMessage[] messages = Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length < 1) {
            loge("Failed to parse SMS pdu");
            return null;
        }
        int i = 0;
        int length = messages.length;
        while (i < length) {
            try {
                messages[i].getDisplayMessageBody();
                i++;
            } catch (NullPointerException e) {
                loge("NPE inside SmsMessage");
                return null;
            }
        }
        ContentValues values = parseSmsMessage(messages);
        long identity = Binder.clearCallingIdentity();
        Uri insert;
        try {
            insert = this.mContext.getContentResolver().insert(Inbox.CONTENT_URI, values);
            return insert;
        } catch (Exception e2) {
            insert = "Failed to persist inbox message";
            loge(insert, e2);
            return null;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
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
        values.put("seen", Integer.valueOf(0));
        values.put("read", Integer.valueOf(0));
        String subject = sms.getPseudoSubject();
        if (!TextUtils.isEmpty(subject)) {
            values.put(TextBasedSmsColumns.SUBJECT, subject);
        }
        String str = TextBasedSmsColumns.REPLY_PATH_PRESENT;
        if (sms.isReplyPathPresent()) {
            i = 1;
        }
        values.put(str, Integer.valueOf(i));
        values.put(TextBasedSmsColumns.SERVICE_CENTER, sms.getServiceCenterAddress());
        return values;
    }

    private static String buildMessageBodyFromPdus(SmsMessage[] msgs) {
        int i = 0;
        if (msgs.length == 1) {
            return replaceFormFeeds(msgs[0].getDisplayMessageBody());
        }
        StringBuilder body = new StringBuilder();
        int length = msgs.length;
        while (i < length) {
            body.append(msgs[i].getDisplayMessageBody());
            i++;
        }
        return replaceFormFeeds(body.toString());
    }

    private static String replaceFormFeeds(String s) {
        return s == null ? "" : s.replace(12, 10);
    }

    public WakeLock getWakeLock() {
        return this.mWakeLock;
    }

    public int getWakeLockTimeout() {
        return WAKELOCK_TIMEOUT;
    }

    public int getWakelockCount() {
        return this.mWakeLockCount;
    }

    /* Access modifiers changed, original: protected */
    public boolean kddiDispatchPdus(InboundSmsTracker tracker) {
        return true;
    }

    /* Access modifiers changed, original: protected */
    public void dispatchNonResult(Intent intent, String permission) {
        this.mWakeLock.acquire(3000);
        this.mContext.sendOrderedBroadcast(intent, permission);
    }
}
