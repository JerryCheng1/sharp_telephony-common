package com.android.internal.telephony;

import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Build;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.Telephony.Carriers;
import android.provider.Telephony.CellBroadcasts;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Inbox;
import android.provider.Telephony.Sms.Intents;
import android.provider.Telephony.TextBasedSmsColumns;
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
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jp.co.sharp.android.internal.telephony.SmsDuplicate;
import jp.co.sharp.android.internal.telephony.SmsDuplicate.ResultJudgeDuplicate;

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
    private static final String[] PDU_PROJECTION = new String[]{"pdu"};
    private static final String[] PDU_SEQUENCE_PORT_PROJECTION = new String[]{"pdu", "sequence", "destination_port"};
    static final int REFERENCE_NUMBER_COLUMN = 4;
    static final String SELECT_BY_ID = "_id=?";
    static final String SELECT_BY_REFERENCE = "address=? AND reference_number=? AND count=?";
    static final int SEQUENCE_COLUMN = 1;
    private static final long TOLERANCE_TIME_MILLIS = 604800000;
    private static final boolean VDBG = false;
    private static final int WAKELOCK_TIMEOUT = 3000;
    private static final Uri sRawUri = Uri.withAppendedPath(Sms.CONTENT_URI, "raw");
    protected CellBroadcastHandler mCellBroadcastHandler;
    protected final Context mContext;
    final DefaultState mDefaultState = new DefaultState();
    final DeliveringState mDeliveringState = new DeliveringState();
    protected SmsDuplicate mDuplicate;
    final IdleState mIdleState = new IdleState();
    protected PhoneBase mPhone;
    private final ContentResolver mResolver;
    private final boolean mSmsReceiveDisabled;
    final StartupState mStartupState = new StartupState();
    protected SmsStorageMonitor mStorageMonitor;
    private UserManager mUserManager;
    final WaitingState mWaitingState = new WaitingState();
    final WakeLock mWakeLock;
    private final WapPushOverSms mWapPush;

    private final class CarrierSmsFilter extends CarrierMessagingServiceManager {
        private final int mDestPort;
        private final byte[][] mPdus;
        private final SmsBroadcastReceiver mSmsBroadcastReceiver;
        private volatile CarrierSmsFilterCallback mSmsFilterCallback;
        private final String mSmsFormat;

        CarrierSmsFilter(byte[][] bArr, int i, String str, SmsBroadcastReceiver smsBroadcastReceiver) {
            this.mPdus = bArr;
            this.mDestPort = i;
            this.mSmsFormat = str;
            this.mSmsBroadcastReceiver = smsBroadcastReceiver;
        }

        /* Access modifiers changed, original: 0000 */
        public void filterSms(String str, CarrierSmsFilterCallback carrierSmsFilterCallback) {
            this.mSmsFilterCallback = carrierSmsFilterCallback;
            if (bindToCarrierMessagingService(InboundSmsHandler.this.mContext, str)) {
                InboundSmsHandler.this.logv("bindService() for carrier messaging service succeeded");
                return;
            }
            InboundSmsHandler.this.loge("bindService() for carrier messaging service failed");
            carrierSmsFilterCallback.onFilterComplete(true);
        }

        /* Access modifiers changed, original: protected */
        public void onServiceReady(ICarrierMessagingService iCarrierMessagingService) {
            try {
                iCarrierMessagingService.filterSms(new MessagePdu(Arrays.asList(this.mPdus)), this.mSmsFormat, this.mDestPort, InboundSmsHandler.this.mPhone.getSubId(), this.mSmsFilterCallback);
            } catch (RemoteException e) {
                InboundSmsHandler.this.loge("Exception filtering the SMS: " + e);
                this.mSmsFilterCallback.onFilterComplete(true);
            }
        }
    }

    private final class CarrierSmsFilterCallback extends Stub {
        private final CarrierSmsFilter mSmsFilter;

        CarrierSmsFilterCallback(CarrierSmsFilter carrierSmsFilter) {
            this.mSmsFilter = carrierSmsFilter;
        }

        public void onDownloadMmsComplete(int i) {
            InboundSmsHandler.this.loge("Unexpected onDownloadMmsComplete call with result: " + i);
        }

        public void onFilterComplete(boolean z) {
            this.mSmsFilter.disposeConnection(InboundSmsHandler.this.mContext);
            InboundSmsHandler.this.logv("onFilterComplete: keepMessage is " + z);
            if (z) {
                InboundSmsHandler.this.dispatchSmsDeliveryIntent(this.mSmsFilter.mPdus, this.mSmsFilter.mSmsFormat, this.mSmsFilter.mDestPort, this.mSmsFilter.mSmsBroadcastReceiver);
                return;
            }
            long clearCallingIdentity = Binder.clearCallingIdentity();
            try {
                InboundSmsHandler.this.deleteFromRawTable(this.mSmsFilter.mSmsBroadcastReceiver.mDeleteWhere, this.mSmsFilter.mSmsBroadcastReceiver.mDeleteWhereArgs);
                InboundSmsHandler.this.sendMessage(3);
            } finally {
                Binder.restoreCallingIdentity(clearCallingIdentity);
            }
        }

        public void onSendMmsComplete(int i, byte[] bArr) {
            InboundSmsHandler.this.loge("Unexpected onSendMmsComplete call with result: " + i);
        }

        public void onSendMultipartSmsComplete(int i, int[] iArr) {
            InboundSmsHandler.this.loge("Unexpected onSendMultipartSmsComplete call with result: " + i);
        }

        public void onSendSmsComplete(int i, int i2) {
            InboundSmsHandler.this.loge("Unexpected onSendSmsComplete call with result: " + i);
        }
    }

    class DefaultState extends State {
        DefaultState() {
        }

        public boolean processMessage(Message message) {
            switch (message.what) {
                case 7:
                    InboundSmsHandler.this.onUpdatePhoneObject((PhoneBase) message.obj);
                    break;
                default:
                    String str = "processMessage: unhandled message type " + message.what + " currState=" + InboundSmsHandler.this.getCurrentState().getName();
                    if (!Build.IS_DEBUGGABLE) {
                        InboundSmsHandler.this.loge(str);
                        break;
                    }
                    InboundSmsHandler.this.loge("---- Dumping InboundSmsHandler ----");
                    InboundSmsHandler.this.loge("Total records=" + InboundSmsHandler.this.getLogRecCount());
                    for (int max = Math.max(InboundSmsHandler.this.getLogRecSize() - 20, 0); max < InboundSmsHandler.this.getLogRecSize(); max++) {
                        InboundSmsHandler.this.loge("Rec[%d]: %s\n" + max + InboundSmsHandler.this.getLogRec(max).toString());
                    }
                    InboundSmsHandler.this.loge("---- Dumped InboundSmsHandler ----");
                    throw new RuntimeException(str);
            }
            return true;
        }
    }

    class DeliveringState extends State {
        DeliveringState() {
        }

        public void enter() {
            InboundSmsHandler.this.log("entering Delivering state");
        }

        public void exit() {
            InboundSmsHandler.this.log("leaving Delivering state");
        }

        public boolean processMessage(Message message) {
            InboundSmsHandler.this.log("DeliveringState.processMessage:" + message.what);
            switch (message.what) {
                case 1:
                    InboundSmsHandler.this.handleNewSms((AsyncResult) message.obj);
                    InboundSmsHandler.this.sendMessage(4);
                    return true;
                case 2:
                    if (InboundSmsHandler.this.processMessagePart((InboundSmsTracker) message.obj)) {
                        InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mWaitingState);
                    }
                    return true;
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
                    InboundSmsHandler.this.handleInjectSms((AsyncResult) message.obj);
                    InboundSmsHandler.this.sendMessage(4);
                    return true;
                default:
                    return false;
            }
        }
    }

    class IdleState extends State {
        IdleState() {
        }

        public void enter() {
            InboundSmsHandler.this.log("entering Idle state");
            InboundSmsHandler.this.sendMessageDelayed(5, 3000);
        }

        public void exit() {
            InboundSmsHandler.this.mWakeLock.acquire();
            InboundSmsHandler.this.log("acquired wakelock, leaving Idle state");
        }

        public boolean processMessage(Message message) {
            InboundSmsHandler.this.log("IdleState.processMessage:" + message.what);
            InboundSmsHandler.this.log("Idle state processing message type " + message.what);
            switch (message.what) {
                case 1:
                case 2:
                case 8:
                    InboundSmsHandler.this.deferMessage(message);
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mDeliveringState);
                    return true;
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
                default:
                    return false;
            }
        }
    }

    private final class SmsBroadcastReceiver extends BroadcastReceiver {
        private long mBroadcastTimeNano = System.nanoTime();
        private final String mDeleteWhere;
        private final String[] mDeleteWhereArgs;

        SmsBroadcastReceiver(InboundSmsTracker inboundSmsTracker) {
            this.mDeleteWhere = inboundSmsTracker.getDeleteWhere();
            this.mDeleteWhereArgs = inboundSmsTracker.getDeleteWhereArgs();
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intents.SMS_DELIVER_ACTION)) {
                intent.setAction(Intents.SMS_RECEIVED_ACTION);
                intent.setComponent(null);
                InboundSmsHandler.this.dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, this, UserHandle.ALL);
            } else if (action.equals(Intents.WAP_PUSH_DELIVER_ACTION)) {
                intent.setAction(Intents.WAP_PUSH_RECEIVED_ACTION);
                intent.setComponent(null);
                InboundSmsHandler.this.dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, this, UserHandle.OWNER);
            } else {
                if (!(Intents.DATA_SMS_RECEIVED_ACTION.equals(action) || Intents.SMS_RECEIVED_ACTION.equals(action) || Intents.DATA_SMS_RECEIVED_ACTION.equals(action) || Intents.WAP_PUSH_RECEIVED_ACTION.equals(action))) {
                    InboundSmsHandler.this.loge("unexpected BroadcastReceiver action: " + action);
                }
                int resultCode = getResultCode();
                if (resultCode == -1 || resultCode == 1) {
                    InboundSmsHandler.this.log("successful broadcast, deleting from raw table.");
                } else {
                    InboundSmsHandler.this.loge("a broadcast receiver set the result code to " + resultCode + ", deleting from raw table anyway!");
                }
                InboundSmsHandler.this.deleteFromRawTable(this.mDeleteWhere, this.mDeleteWhereArgs);
                InboundSmsHandler.this.sendMessage(3);
                resultCode = (int) ((System.nanoTime() - this.mBroadcastTimeNano) / 1000000);
                if (resultCode >= 5000) {
                    InboundSmsHandler.this.loge("Slow ordered broadcast completion time: " + resultCode + " ms");
                } else {
                    InboundSmsHandler.this.log("ordered broadcast completed in: " + resultCode + " ms");
                }
            }
        }
    }

    class StartupState extends State {
        StartupState() {
        }

        public boolean processMessage(Message message) {
            InboundSmsHandler.this.log("StartupState.processMessage:" + message.what);
            switch (message.what) {
                case 1:
                case 2:
                case 8:
                    InboundSmsHandler.this.deferMessage(message);
                    return true;
                case 6:
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mIdleState);
                    return true;
                default:
                    return false;
            }
        }
    }

    class WaitingState extends State {
        WaitingState() {
        }

        public boolean processMessage(Message message) {
            InboundSmsHandler.this.log("WaitingState.processMessage:" + message.what);
            switch (message.what) {
                case 2:
                    InboundSmsHandler.this.deferMessage(message);
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

    protected InboundSmsHandler(String str, Context context, SmsStorageMonitor smsStorageMonitor, PhoneBase phoneBase, CellBroadcastHandler cellBroadcastHandler) {
        super(str);
        this.mContext = context;
        this.mStorageMonitor = smsStorageMonitor;
        this.mPhone = phoneBase;
        this.mCellBroadcastHandler = cellBroadcastHandler;
        this.mResolver = context.getContentResolver();
        this.mWapPush = new WapPushOverSms(context);
        this.mSmsReceiveDisabled = !SystemProperties.getBoolean("telephony.sms.receive", this.mContext.getResources().getBoolean(17956949));
        this.mWakeLock = ((PowerManager) this.mContext.getSystemService("power")).newWakeLock(1, str);
        this.mWakeLock.acquire();
        this.mUserManager = (UserManager) this.mContext.getSystemService(Carriers.USER);
        addState(this.mDefaultState);
        addState(this.mStartupState, this.mDefaultState);
        addState(this.mIdleState, this.mDefaultState);
        addState(this.mDeliveringState, this.mDefaultState);
        addState(this.mWaitingState, this.mDeliveringState);
        setInitialState(this.mStartupState);
        log("created InboundSmsHandler");
    }

    /* JADX WARNING: Removed duplicated region for block: B:33:0x012a  */
    private int addTrackerToRawTable(com.android.internal.telephony.InboundSmsTracker r14) {
        /*
        r13 = this;
        r8 = 0;
        r6 = 2;
        r7 = 1;
        r0 = r14.getMessageCount();
        if (r0 == r7) goto L_0x012e;
    L_0x0009:
        r0 = r14.getTimestamp();	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r2 = 604800000; // 0x240c8400 float:3.046947E-17 double:2.988109026E-315;
        r0 = r0 - r2;
        r0 = java.lang.Long.toString(r0);	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r1 = r13.mResolver;	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r2 = sRawUri;	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r3 = "date<?";
        r4 = 1;
        r4 = new java.lang.String[r4];	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r5 = 0;
        r4[r5] = r0;	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r1.delete(r2, r3, r4);	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r0 = r14.getSequenceNumber();	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r5 = r14.getAddress();	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r1 = r14.getReferenceNumber();	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r9 = java.lang.Integer.toString(r1);	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r1 = r14.getMessageCount();	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r10 = java.lang.Integer.toString(r1);	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r11 = java.lang.Integer.toString(r0);	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r0 = "address=? AND reference_number=? AND count=?";
        r1 = 3;
        r1 = new java.lang.String[r1];	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r2 = 0;
        r1[r2] = r5;	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r2 = 1;
        r1[r2] = r9;	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r2 = 2;
        r1[r2] = r10;	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r14.setDeleteWhere(r0, r1);	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r0 = r13.mResolver;	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r1 = sRawUri;	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r2 = PDU_PROJECTION;	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r3 = "address=? AND reference_number=? AND count=? AND sequence=?";
        r4 = 4;
        r4 = new java.lang.String[r4];	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r12 = 0;
        r4[r12] = r5;	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r5 = 1;
        r4[r5] = r9;	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r5 = 2;
        r4[r5] = r10;	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r5 = 3;
        r4[r5] = r11;	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r5 = 0;
        r1 = r0.query(r1, r2, r3, r4, r5);	 Catch:{ SQLException -> 0x0119, all -> 0x0126 }
        r0 = r1.moveToNext();	 Catch:{ SQLException -> 0x0155 }
        if (r0 == 0) goto L_0x00d4;
    L_0x0073:
        r0 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x0155 }
        r0.<init>();	 Catch:{ SQLException -> 0x0155 }
        r2 = "Discarding duplicate message segment, refNumber=";
        r0 = r0.append(r2);	 Catch:{ SQLException -> 0x0155 }
        r0 = r0.append(r9);	 Catch:{ SQLException -> 0x0155 }
        r2 = " seqNumber=";
        r0 = r0.append(r2);	 Catch:{ SQLException -> 0x0155 }
        r0 = r0.append(r11);	 Catch:{ SQLException -> 0x0155 }
        r0 = r0.toString();	 Catch:{ SQLException -> 0x0155 }
        r13.loge(r0);	 Catch:{ SQLException -> 0x0155 }
        r0 = 0;
        r0 = r1.getString(r0);	 Catch:{ SQLException -> 0x0155 }
        r2 = r14.getPdu();	 Catch:{ SQLException -> 0x0155 }
        r0 = com.android.internal.util.HexDump.hexStringToByteArray(r0);	 Catch:{ SQLException -> 0x0155 }
        r3 = r14.getPdu();	 Catch:{ SQLException -> 0x0155 }
        r3 = java.util.Arrays.equals(r0, r3);	 Catch:{ SQLException -> 0x0155 }
        if (r3 != 0) goto L_0x00cc;
    L_0x00aa:
        r3 = new java.lang.StringBuilder;	 Catch:{ SQLException -> 0x0155 }
        r3.<init>();	 Catch:{ SQLException -> 0x0155 }
        r4 = "Warning: dup message segment PDU of length ";
        r3 = r3.append(r4);	 Catch:{ SQLException -> 0x0155 }
        r2 = r2.length;	 Catch:{ SQLException -> 0x0155 }
        r2 = r3.append(r2);	 Catch:{ SQLException -> 0x0155 }
        r3 = " is different from existing PDU of length ";
        r2 = r2.append(r3);	 Catch:{ SQLException -> 0x0155 }
        r0 = r0.length;	 Catch:{ SQLException -> 0x0155 }
        r0 = r2.append(r0);	 Catch:{ SQLException -> 0x0155 }
        r0 = r0.toString();	 Catch:{ SQLException -> 0x0155 }
        r13.loge(r0);	 Catch:{ SQLException -> 0x0155 }
    L_0x00cc:
        r0 = 5;
        if (r1 == 0) goto L_0x00d2;
    L_0x00cf:
        r1.close();
    L_0x00d2:
        r6 = r0;
    L_0x00d3:
        return r6;
    L_0x00d4:
        r1.close();	 Catch:{ SQLException -> 0x0155 }
        if (r1 == 0) goto L_0x00dc;
    L_0x00d9:
        r1.close();
    L_0x00dc:
        r0 = r14.getContentValues();
        r1 = r13.mResolver;
        r2 = sRawUri;
        r1 = r1.insert(r2, r0);
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r2 = "URI of new row -> ";
        r0 = r0.append(r2);
        r0 = r0.append(r1);
        r0 = r0.toString();
        r13.log(r0);
        r2 = android.content.ContentUris.parseId(r1);	 Catch:{ Exception -> 0x013b }
        r0 = r14.getMessageCount();	 Catch:{ Exception -> 0x013b }
        if (r0 != r7) goto L_0x0117;
    L_0x0108:
        r0 = "_id=?";
        r4 = 1;
        r4 = new java.lang.String[r4];	 Catch:{ Exception -> 0x013b }
        r5 = 0;
        r2 = java.lang.Long.toString(r2);	 Catch:{ Exception -> 0x013b }
        r4[r5] = r2;	 Catch:{ Exception -> 0x013b }
        r14.setDeleteWhere(r0, r4);	 Catch:{ Exception -> 0x013b }
    L_0x0117:
        r6 = r7;
        goto L_0x00d3;
    L_0x0119:
        r0 = move-exception;
        r1 = r8;
    L_0x011b:
        r2 = "Can't access multipart SMS database";
        r13.loge(r2, r0);	 Catch:{ all -> 0x0153 }
        if (r1 == 0) goto L_0x0157;
    L_0x0122:
        r1.close();
        goto L_0x00d3;
    L_0x0126:
        r0 = move-exception;
        r1 = r8;
    L_0x0128:
        if (r1 == 0) goto L_0x012d;
    L_0x012a:
        r1.close();
    L_0x012d:
        throw r0;
    L_0x012e:
        r0 = r13.judSmsDuplicate(r14);
        if (r0 == 0) goto L_0x00dc;
    L_0x0134:
        r0 = "Received SMS is duplicate";
        r13.log(r0);
        r6 = 5;
        goto L_0x00d3;
    L_0x013b:
        r0 = move-exception;
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "error parsing URI for new row: ";
        r2 = r2.append(r3);
        r1 = r2.append(r1);
        r1 = r1.toString();
        r13.loge(r1, r0);
        goto L_0x00d3;
    L_0x0153:
        r0 = move-exception;
        goto L_0x0128;
    L_0x0155:
        r0 = move-exception;
        goto L_0x011b;
    L_0x0157:
        r0 = r6;
        goto L_0x00d2;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.InboundSmsHandler.addTrackerToRawTable(com.android.internal.telephony.InboundSmsTracker):int");
    }

    private static String buildMessageBodyFromPdus(SmsMessage[] smsMessageArr) {
        int i = 0;
        if (smsMessageArr.length == 1) {
            return replaceFormFeeds(smsMessageArr[0].getDisplayMessageBody());
        }
        StringBuilder stringBuilder = new StringBuilder();
        int length = smsMessageArr.length;
        while (i < length) {
            stringBuilder.append(smsMessageArr[i].getDisplayMessageBody());
            i++;
        }
        return replaceFormFeeds(stringBuilder.toString());
    }

    private byte[] getMessage_NotmtiPdu(byte[] bArr) {
        int length = bArr.length;
        int i = bArr[0] + 1;
        if (i >= length) {
            return bArr;
        }
        int i2 = i + 1;
        byte[] bArr2 = new byte[(length - i2)];
        System.arraycopy(bArr, i2, bArr2, 0, length - i2);
        return bArr2;
    }

    private List<String> getSystemAppForIntent(Intent intent) {
        ArrayList arrayList = new ArrayList();
        PackageManager packageManager = this.mContext.getPackageManager();
        for (ResolveInfo resolveInfo : packageManager.queryIntentServices(intent, 0)) {
            if (resolveInfo.serviceInfo == null) {
                loge("Can't get service information from " + resolveInfo);
            } else {
                String str = resolveInfo.serviceInfo.packageName;
                if (packageManager.checkPermission("android.permission.CARRIER_FILTER_SMS", str) == 0) {
                    arrayList.add(str);
                    log("getSystemAppForIntent: added package " + str);
                }
            }
        }
        return arrayList;
    }

    static boolean isCurrentFormat3gpp2() {
        return 2 == TelephonyManager.getDefault().getCurrentPhoneType();
    }

    private static ContentValues parseSmsMessage(SmsMessage[] smsMessageArr) {
        int i = 0;
        SmsMessage smsMessage = smsMessageArr[0];
        ContentValues contentValues = new ContentValues();
        contentValues.put("address", smsMessage.getDisplayOriginatingAddress());
        contentValues.put("body", buildMessageBodyFromPdus(smsMessageArr));
        contentValues.put("date_sent", Long.valueOf(smsMessage.getTimestampMillis()));
        contentValues.put("date", Long.valueOf(System.currentTimeMillis()));
        contentValues.put("protocol", Integer.valueOf(smsMessage.getProtocolIdentifier()));
        contentValues.put("seen", Integer.valueOf(0));
        contentValues.put("read", Integer.valueOf(0));
        String pseudoSubject = smsMessage.getPseudoSubject();
        if (!TextUtils.isEmpty(pseudoSubject)) {
            contentValues.put(TextBasedSmsColumns.SUBJECT, pseudoSubject);
        }
        if (smsMessage.isReplyPathPresent()) {
            i = 1;
        }
        contentValues.put(TextBasedSmsColumns.REPLY_PATH_PRESENT, Integer.valueOf(i));
        contentValues.put(TextBasedSmsColumns.SERVICE_CENTER, smsMessage.getServiceCenterAddress());
        return contentValues;
    }

    private static String replaceFormFeeds(String str) {
        return str == null ? "" : str.replace(12, 10);
    }

    private Uri writeInboxMessage(Intent intent) {
        Uri uri = null;
        SmsMessage[] messagesFromIntent = Intents.getMessagesFromIntent(intent);
        if (messagesFromIntent == null || messagesFromIntent.length < 1) {
            loge("Failed to parse SMS pdu");
        } else {
            int length = messagesFromIntent.length;
            int i = 0;
            while (i < length) {
                try {
                    messagesFromIntent[i].getDisplayMessageBody();
                    i++;
                } catch (NullPointerException e) {
                    loge("NPE inside SmsMessage");
                }
            }
            ContentValues parseSmsMessage = parseSmsMessage(messagesFromIntent);
            long clearCallingIdentity = Binder.clearCallingIdentity();
            try {
                uri = this.mContext.getContentResolver().insert(Inbox.CONTENT_URI, parseSmsMessage);
            } catch (Exception e2) {
                loge("Failed to persist inbox message", e2);
            } finally {
                Binder.restoreCallingIdentity(clearCallingIdentity);
            }
        }
        return uri;
    }

    public abstract void acknowledgeLastIncomingSms(boolean z, int i, Message message);

    /* Access modifiers changed, original: protected */
    public int addTrackerToRawTableAndSendMessage(InboundSmsTracker inboundSmsTracker) {
        switch (addTrackerToRawTable(inboundSmsTracker)) {
            case 1:
                sendMessage(2, inboundSmsTracker);
                return 1;
            case 5:
                return 1;
            default:
                return 2;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void deleteFromRawTable(String str, String[] strArr) {
        int delete = this.mResolver.delete(sRawUri, str, strArr);
        if (delete == 0) {
            loge("No rows were deleted from raw table!");
        } else {
            log("Deleted " + delete + " rows from raw table.");
        }
    }

    /* Access modifiers changed, original: protected */
    public void dispatchIntent(Intent intent, String str, int i, BroadcastReceiver broadcastReceiver, UserHandle userHandle) {
        intent.addFlags(134217728);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
        if (userHandle.equals(UserHandle.ALL)) {
            int[] iArr = null;
            try {
                iArr = ActivityManagerNative.getDefault().getRunningUserIds();
            } catch (RemoteException e) {
            }
            if (iArr == null) {
                iArr = new int[]{userHandle.getIdentifier()};
            }
            int[] iArr2 = iArr;
            for (int length = iArr2.length - 1; length >= 0; length--) {
                UserHandle userHandle2 = new UserHandle(iArr2[length]);
                if (iArr2[length] != 0) {
                    if (!this.mUserManager.hasUserRestriction("no_sms", userHandle2)) {
                        UserInfo userInfo = this.mUserManager.getUserInfo(iArr2[length]);
                        if (userInfo != null) {
                            if (userInfo.isManagedProfile()) {
                            }
                        }
                    }
                }
                this.mContext.sendOrderedBroadcastAsUser(intent, userHandle2, str, i, iArr2[length] == 0 ? broadcastReceiver : null, getHandler(), -1, null, null);
            }
            return;
        }
        this.mContext.sendOrderedBroadcastAsUser(intent, userHandle, str, i, broadcastReceiver, getHandler(), -1, null, null);
    }

    public int dispatchMessage(SmsMessageBase smsMessageBase) {
        if (smsMessageBase == null) {
            loge("dispatchSmsMessage: message is null");
            return 2;
        } else if (!this.mSmsReceiveDisabled) {
            return dispatchMessageRadioSpecific(smsMessageBase);
        } else {
            log("Received short message on device which doesn't support receiving SMS. Ignored.");
            return 1;
        }
    }

    public abstract int dispatchMessageRadioSpecific(SmsMessageBase smsMessageBase);

    /* Access modifiers changed, original: protected */
    public int dispatchNormalMessage(SmsMessageBase smsMessageBase) {
        InboundSmsTracker inboundSmsTracker;
        SmsHeader userDataHeader = smsMessageBase.getUserDataHeader();
        if (userDataHeader == null || userDataHeader.concatRef == null) {
            int i = -1;
            if (!(userDataHeader == null || userDataHeader.portAddrs == null)) {
                i = userDataHeader.portAddrs.destPort;
                log("destination port: " + i);
            }
            inboundSmsTracker = new InboundSmsTracker(smsMessageBase.getPdu(), smsMessageBase.getTimestampMillis(), i, is3gpp2(), false);
        } else {
            ConcatRef concatRef = userDataHeader.concatRef;
            PortAddrs portAddrs = userDataHeader.portAddrs;
            inboundSmsTracker = new InboundSmsTracker(smsMessageBase.getPdu(), smsMessageBase.getTimestampMillis(), portAddrs != null ? portAddrs.destPort : -1, is3gpp2(), smsMessageBase.getOriginatingAddress(), concatRef.refNumber, concatRef.seqNumber, concatRef.msgCount, false);
        }
        return addTrackerToRawTableAndSendMessage(inboundSmsTracker);
    }

    /* Access modifiers changed, original: 0000 */
    public void dispatchSmsDeliveryIntent(byte[][] bArr, String str, int i, BroadcastReceiver broadcastReceiver) {
        Intent intent = new Intent();
        intent.putExtra("pdus", bArr);
        intent.putExtra(CellBroadcasts.MESSAGE_FORMAT, str);
        if (i == -1) {
            intent.setAction(Intents.SMS_DELIVER_ACTION);
            ComponentName defaultSmsApplication = SmsApplication.getDefaultSmsApplication(this.mContext, true);
            if (defaultSmsApplication != null) {
                intent.setComponent(defaultSmsApplication);
                log("Delivering SMS to: " + defaultSmsApplication.getPackageName() + " " + defaultSmsApplication.getClassName());
            } else {
                intent.setComponent(null);
            }
            if (SmsManager.getDefault().getAutoPersisting()) {
                Uri writeInboxMessage = writeInboxMessage(intent);
                if (writeInboxMessage != null) {
                    intent.putExtra("uri", writeInboxMessage.toString());
                }
            }
        } else {
            intent.setAction(Intents.DATA_SMS_RECEIVED_ACTION);
            intent.setData(Uri.parse("sms://localhost:" + i));
            intent.setComponent(null);
        }
        dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, broadcastReceiver, UserHandle.OWNER);
    }

    public void dispose() {
        quit();
    }

    public PhoneBase getPhone() {
        return this.mPhone;
    }

    /* Access modifiers changed, original: 0000 */
    /* JADX WARNING: Removed duplicated region for block: B:19:? A:{SYNTHETIC, RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:8:0x000f A:{SYNTHETIC, Splitter:B:8:0x000f} */
    public void handleInjectSms(android.os.AsyncResult r5) {
        /*
        r4 = this;
        r3 = 2;
        r2 = 0;
        r0 = r5.userObj;	 Catch:{ RuntimeException -> 0x001a }
        r0 = (android.app.PendingIntent) r0;	 Catch:{ RuntimeException -> 0x001a }
        r1 = r5.result;	 Catch:{ RuntimeException -> 0x0026 }
        r1 = (android.telephony.SmsMessage) r1;	 Catch:{ RuntimeException -> 0x0026 }
        if (r1 != 0) goto L_0x0013;
    L_0x000c:
        r1 = r3;
    L_0x000d:
        if (r0 == 0) goto L_0x0012;
    L_0x000f:
        r0.send(r1);	 Catch:{ CanceledException -> 0x0024 }
    L_0x0012:
        return;
    L_0x0013:
        r1 = r1.mWrappedSmsMessage;	 Catch:{ RuntimeException -> 0x0026 }
        r1 = r4.dispatchMessage(r1);	 Catch:{ RuntimeException -> 0x0026 }
        goto L_0x000d;
    L_0x001a:
        r0 = move-exception;
        r1 = r0;
    L_0x001c:
        r0 = "Exception dispatching message";
        r4.loge(r0, r1);
        r1 = r3;
        r0 = r2;
        goto L_0x000d;
    L_0x0024:
        r0 = move-exception;
        goto L_0x0012;
    L_0x0026:
        r1 = move-exception;
        r2 = r0;
        goto L_0x001c;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.InboundSmsHandler.handleInjectSms(android.os.AsyncResult):void");
    }

    /* Access modifiers changed, original: 0000 */
    public void handleNewSms(AsyncResult asyncResult) {
        if (asyncResult.exception != null) {
            loge("Exception processing incoming SMS: " + asyncResult.exception);
            return;
        }
        int dispatchMessage;
        try {
            dispatchMessage = dispatchMessage(((SmsMessage) asyncResult.result).mWrappedSmsMessage);
        } catch (RuntimeException e) {
            loge("Exception dispatching message", e);
            dispatchMessage = 2;
        }
        SmsHeader userDataHeader = ((SmsMessage) asyncResult.result).mWrappedSmsMessage.getUserDataHeader();
        if (userDataHeader != null && userDataHeader.portAddrs != null && userDataHeader.portAddrs.destPort == SmsHeader.PORT_WAP_PUSH && dispatchMessage == 3) {
            acknowledgeLastIncomingSms(false, dispatchMessage, null);
        } else if (dispatchMessage != -1) {
            notifyAndAcknowledgeLastIncomingSms(dispatchMessage == 1, dispatchMessage, null);
        }
    }

    public abstract boolean is3gpp2();

    /* Access modifiers changed, original: protected */
    public boolean judSmsDuplicate(InboundSmsTracker inboundSmsTracker) {
        boolean z = true;
        if (inboundSmsTracker.getDestPort() == SmsHeader.PORT_WAP_PUSH) {
            return false;
        }
        try {
            if (this.mDuplicate == null) {
                this.mDuplicate = new SmsDuplicate(this.mContext, 1, true);
            }
            byte[] message_NotmtiPdu = getMessage_NotmtiPdu(inboundSmsTracker.getPdu());
            ResultJudgeDuplicate checkSmsDuplicate = this.mDuplicate.checkSmsDuplicate(inboundSmsTracker.getReferenceNumber(), message_NotmtiPdu);
            if (!(checkSmsDuplicate != null && checkSmsDuplicate.mIsSame && checkSmsDuplicate.mIsReply)) {
                z = false;
            }
            try {
                this.mDuplicate.updateSmsDuplicate(inboundSmsTracker.getReferenceNumber(), message_NotmtiPdu, this.mDuplicate.SmsAccessory(Intents.SMS_RECEIVED_ACTION, null, 0));
            } catch (NullPointerException e) {
                loge("Function failed to create SmsAccessory.");
                return z;
            }
        } catch (NullPointerException e2) {
            z = false;
            loge("Function failed to create SmsAccessory.");
            return z;
        }
        return z;
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(getName(), str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str) {
        Rlog.e(getName(), str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str, Throwable th) {
        Rlog.e(getName(), str, th);
    }

    /* Access modifiers changed, original: 0000 */
    public void notifyAndAcknowledgeLastIncomingSms(boolean z, int i, Message message) {
        if (!z) {
            Intent intent = new Intent(Intents.SMS_REJECTED_ACTION);
            intent.putExtra("result", i);
            this.mContext.sendBroadcast(intent, "android.permission.RECEIVE_SMS");
        }
        acknowledgeLastIncomingSms(z, i, message);
    }

    /* Access modifiers changed, original: protected */
    public void onQuitting() {
        this.mWapPush.dispose();
        while (this.mWakeLock.isHeld()) {
            this.mWakeLock.release();
        }
    }

    /* Access modifiers changed, original: protected */
    public void onUpdatePhoneObject(PhoneBase phoneBase) {
        this.mPhone = phoneBase;
        this.mStorageMonitor = this.mPhone.mSmsStorageMonitor;
        log("onUpdatePhoneObject: phone=" + this.mPhone.getClass().getSimpleName());
    }

    /* Access modifiers changed, original: 0000 */
    /* JADX WARNING: Removed duplicated region for block: B:45:0x00db  */
    /* JADX WARNING: Removed duplicated region for block: B:45:0x00db  */
    public boolean processMessagePart(com.android.internal.telephony.InboundSmsTracker r14) {
        /*
        r13 = this;
        r10 = r14.getMessageCount();
        r6 = r14.getDestPort();
        r0 = "";
        r1 = 1;
        if (r10 != r1) goto L_0x004f;
    L_0x000d:
        r1 = 1;
        r2 = new byte[r1][];
        r1 = 0;
        r3 = r14.getPdu();
        r2[r1] = r3;
        r3 = r6;
        r1 = r0;
    L_0x0019:
        r5 = new com.android.internal.telephony.InboundSmsHandler$SmsBroadcastReceiver;
        r5.<init>(r14);
        r0 = 2948; // 0xb84 float:4.131E-42 double:1.4565E-320;
        if (r3 != r0) goto L_0x0124;
    L_0x0022:
        r4 = new java.io.ByteArrayOutputStream;
        r4.<init>();
        r6 = r2.length;
        r0 = 0;
        r3 = r0;
    L_0x002a:
        if (r3 >= r6) goto L_0x00e9;
    L_0x002c:
        r0 = r2[r3];
        r7 = r14.is3gpp2();
        if (r7 != 0) goto L_0x0046;
    L_0x0034:
        r7 = "3gpp";
        r7 = android.telephony.SmsMessage.createFromPdu(r0, r7);
        r0 = r7.getUserData();
        r8 = "";
        if (r1 != r8) goto L_0x00df;
    L_0x0042:
        r1 = r7.getOriginatingAddress();
    L_0x0046:
        r7 = 0;
        r8 = r0.length;
        r4.write(r0, r7, r8);
        r0 = r3 + 1;
        r3 = r0;
        goto L_0x002a;
    L_0x004f:
        r9 = 0;
        r8 = 0;
        r7 = r14.getAddress();	 Catch:{ SQLException -> 0x00c9, all -> 0x00d7 }
        r0 = r14.getReferenceNumber();	 Catch:{ SQLException -> 0x00c9, all -> 0x00d7 }
        r5 = java.lang.Integer.toString(r0);	 Catch:{ SQLException -> 0x00c9, all -> 0x00d7 }
        r0 = r14.getMessageCount();	 Catch:{ SQLException -> 0x00c9, all -> 0x00d7 }
        r11 = java.lang.Integer.toString(r0);	 Catch:{ SQLException -> 0x00c9, all -> 0x00d7 }
        r0 = r13.mResolver;	 Catch:{ SQLException -> 0x00c9, all -> 0x00d7 }
        r1 = sRawUri;	 Catch:{ SQLException -> 0x00c9, all -> 0x00d7 }
        r2 = PDU_SEQUENCE_PORT_PROJECTION;	 Catch:{ SQLException -> 0x00c9, all -> 0x00d7 }
        r3 = "address=? AND reference_number=? AND count=?";
        r4 = 3;
        r4 = new java.lang.String[r4];	 Catch:{ SQLException -> 0x00c9, all -> 0x00d7 }
        r12 = 0;
        r4[r12] = r7;	 Catch:{ SQLException -> 0x00c9, all -> 0x00d7 }
        r12 = 1;
        r4[r12] = r5;	 Catch:{ SQLException -> 0x00c9, all -> 0x00d7 }
        r5 = 2;
        r4[r5] = r11;	 Catch:{ SQLException -> 0x00c9, all -> 0x00d7 }
        r5 = 0;
        r3 = r0.query(r1, r2, r3, r4, r5);	 Catch:{ SQLException -> 0x00c9, all -> 0x00d7 }
        r0 = r3.getCount();	 Catch:{ SQLException -> 0x01da, all -> 0x01d3 }
        if (r0 >= r10) goto L_0x008b;
    L_0x0084:
        if (r3 == 0) goto L_0x0089;
    L_0x0086:
        r3.close();
    L_0x0089:
        r0 = 0;
    L_0x008a:
        return r0;
    L_0x008b:
        r2 = new byte[r10][];	 Catch:{ SQLException -> 0x01da, all -> 0x01d3 }
        r0 = r6;
    L_0x008e:
        r1 = r3.moveToNext();	 Catch:{ SQLException -> 0x01da, all -> 0x01d3 }
        if (r1 == 0) goto L_0x00c0;
    L_0x0094:
        r1 = 1;
        r1 = r3.getInt(r1);	 Catch:{ SQLException -> 0x01da, all -> 0x01d3 }
        r4 = r14.getIndexOffset();	 Catch:{ SQLException -> 0x01da, all -> 0x01d3 }
        r1 = r1 - r4;
        r4 = 0;
        r4 = r3.getString(r4);	 Catch:{ SQLException -> 0x01da, all -> 0x01d3 }
        r4 = com.android.internal.util.HexDump.hexStringToByteArray(r4);	 Catch:{ SQLException -> 0x01da, all -> 0x01d3 }
        r2[r1] = r4;	 Catch:{ SQLException -> 0x01da, all -> 0x01d3 }
        if (r1 != 0) goto L_0x008e;
    L_0x00ab:
        r1 = 2;
        r1 = r3.isNull(r1);	 Catch:{ SQLException -> 0x01da, all -> 0x01d3 }
        if (r1 != 0) goto L_0x008e;
    L_0x00b2:
        r1 = 2;
        r1 = r3.getInt(r1);	 Catch:{ SQLException -> 0x01da, all -> 0x01d3 }
        r1 = com.android.internal.telephony.InboundSmsTracker.getRealDestPort(r1);	 Catch:{ SQLException -> 0x01da, all -> 0x01d3 }
        r4 = -1;
        if (r1 == r4) goto L_0x008e;
    L_0x00be:
        r0 = r1;
        goto L_0x008e;
    L_0x00c0:
        if (r3 == 0) goto L_0x01de;
    L_0x00c2:
        r3.close();
        r3 = r0;
        r1 = r7;
        goto L_0x0019;
    L_0x00c9:
        r0 = move-exception;
        r1 = r8;
    L_0x00cb:
        r2 = "Can't access multipart SMS database";
        r13.loge(r2, r0);	 Catch:{ all -> 0x01d6 }
        if (r1 == 0) goto L_0x0089;
    L_0x00d2:
        r1.close();
        r0 = 0;
        goto L_0x008a;
    L_0x00d7:
        r0 = move-exception;
        r3 = r9;
    L_0x00d9:
        if (r3 == 0) goto L_0x00de;
    L_0x00db:
        r3.close();
    L_0x00de:
        throw r0;
    L_0x00df:
        r7 = "";
        if (r1 != r7) goto L_0x0046;
    L_0x00e3:
        r1 = r14.getAddress();
        goto L_0x0046;
    L_0x00e9:
        r0 = r13.mWapPush;
        r2 = r14.getDeleteWhere();
        r0.setDeleteWhere(r2);
        r0 = r13.mWapPush;
        r2 = r14.getDeleteWhereArgs();
        r0.setDeleteWhereArgs(r2);
        r0 = r13.mWapPush;
        r2 = r4.toByteArray();
        r0 = r0.dispatchWapPdu(r2, r5, r13, r1);
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "dispatchWapPdu() returned ";
        r1 = r1.append(r2);
        r1 = r1.append(r0);
        r1 = r1.toString();
        r13.log(r1);
        r1 = -1;
        if (r0 != r1) goto L_0x0121;
    L_0x011e:
        r0 = 1;
        goto L_0x008a;
    L_0x0121:
        r0 = 0;
        goto L_0x008a;
    L_0x0124:
        r0 = 0;
        r1 = com.android.internal.telephony.uicc.UiccController.getInstance();
        r4 = r13.mPhone;
        r4 = r4.getPhoneId();
        r1 = r1.getUiccCard(r4);
        if (r1 == 0) goto L_0x017c;
    L_0x0135:
        r0 = r13.mContext;
        r0 = r0.getPackageManager();
        r4 = new android.content.Intent;
        r6 = "android.service.carrier.CarrierMessagingService";
        r4.<init>(r6);
        r0 = r1.getCarrierPackageNamesForIntent(r0, r4);
        r6 = r0;
    L_0x0147:
        r0 = new android.content.Intent;
        r1 = "android.service.carrier.CarrierMessagingService";
        r0.<init>(r1);
        r7 = r13.getSystemAppForIntent(r0);
        if (r6 == 0) goto L_0x0183;
    L_0x0154:
        r0 = r6.size();
        r1 = 1;
        if (r0 != r1) goto L_0x0183;
    L_0x015b:
        r0 = "Found carrier package.";
        r13.log(r0);
        r0 = new com.android.internal.telephony.InboundSmsHandler$CarrierSmsFilter;
        r4 = r14.getFormat();
        r1 = r13;
        r0.<init>(r2, r3, r4, r5);
        r2 = new com.android.internal.telephony.InboundSmsHandler$CarrierSmsFilterCallback;
        r2.<init>(r0);
        r1 = 0;
        r1 = r6.get(r1);
        r1 = (java.lang.String) r1;
        r0.filterSms(r1, r2);
    L_0x0179:
        r0 = 1;
        goto L_0x008a;
    L_0x017c:
        r1 = "UiccCard not initialized.";
        r13.loge(r1);
        r6 = r0;
        goto L_0x0147;
    L_0x0183:
        if (r7 == 0) goto L_0x01ab;
    L_0x0185:
        r0 = r7.size();
        r1 = 1;
        if (r0 != r1) goto L_0x01ab;
    L_0x018c:
        r0 = "Found system package.";
        r13.log(r0);
        r0 = new com.android.internal.telephony.InboundSmsHandler$CarrierSmsFilter;
        r4 = r14.getFormat();
        r1 = r13;
        r0.<init>(r2, r3, r4, r5);
        r2 = new com.android.internal.telephony.InboundSmsHandler$CarrierSmsFilterCallback;
        r2.<init>(r0);
        r1 = 0;
        r1 = r7.get(r1);
        r1 = (java.lang.String) r1;
        r0.filterSms(r1, r2);
        goto L_0x0179;
    L_0x01ab:
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "Unable to find carrier package: ";
        r0 = r0.append(r1);
        r0 = r0.append(r6);
        r1 = ", nor systemPackages: ";
        r0 = r0.append(r1);
        r0 = r0.append(r7);
        r0 = r0.toString();
        r13.logv(r0);
        r0 = r14.getFormat();
        r13.dispatchSmsDeliveryIntent(r2, r0, r3, r5);
        goto L_0x0179;
    L_0x01d3:
        r0 = move-exception;
        goto L_0x00d9;
    L_0x01d6:
        r0 = move-exception;
        r3 = r1;
        goto L_0x00d9;
    L_0x01da:
        r0 = move-exception;
        r1 = r3;
        goto L_0x00cb;
    L_0x01de:
        r3 = r0;
        r1 = r7;
        goto L_0x0019;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.InboundSmsHandler.processMessagePart(com.android.internal.telephony.InboundSmsTracker):boolean");
    }

    /* Access modifiers changed, original: protected */
    public void storeVoiceMailCount() {
        String subscriberId = this.mPhone.getSubscriberId();
        int voiceMessageCount = this.mPhone.getVoiceMessageCount();
        StringBuilder append = new StringBuilder().append("Storing Voice Mail Count = ").append(voiceMessageCount).append(" for mVmCountKey = ");
        PhoneBase phoneBase = this.mPhone;
        append = append.append(PhoneBase.VM_COUNT).append(" vmId = ");
        phoneBase = this.mPhone;
        log(append.append(PhoneBase.VM_ID).append(" subId = ").append(this.mPhone.getSubId()).append(" in preferences.").toString());
        Editor edit = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
        StringBuilder stringBuilder = new StringBuilder();
        PhoneBase phoneBase2 = this.mPhone;
        edit.putInt(stringBuilder.append(PhoneBase.VM_COUNT).append(this.mPhone.getSubId()).toString(), voiceMessageCount);
        StringBuilder stringBuilder2 = new StringBuilder();
        phoneBase = this.mPhone;
        edit.putString(stringBuilder2.append(PhoneBase.VM_ID).append(this.mPhone.getSubId()).toString(), subscriberId);
        edit.commit();
    }

    public void updatePhoneObject(PhoneBase phoneBase) {
        sendMessage(7, phoneBase);
    }
}
